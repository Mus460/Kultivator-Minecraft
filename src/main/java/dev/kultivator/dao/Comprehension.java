package dev.kultivator.dao;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dao comprehension (DESIGN 7). Every gain goes through {@link #add}: x1.5 when the spiritual root resonates with the
 * Dao, clamped to 0..100, and crossing a level (10 Minor, 30 Major, 60 Perfect, 90 Domain) is announced with chat,
 * action bar, effect, sound and an advancement.
 * <p>
 * The chosen Dao also deepens through its "actions", each at most once per 2 s per player: sword kills, killing
 * burning monsters, killing in cold biomes, killing during thunderstorms, monster hits taken, healing (sampled once per
 * second), feeding animals or bone meal on plants, kills from the shadows (sneaking, night, darkness), ender pearls and
 * time spent in the End.
 */
public final class Comprehension {

	/** Gain factor when the root has the Dao's element. */
	public static final double AFFINITY_FACTOR = 1.5D;
	/** Per successful cast of a technique of the chosen Dao. */
	public static final double TECHNIQUE_GAIN = 0.15D;
	/** Comprehension where each level starts (index = level). */
	public static final double[] LEVEL_THRESHOLDS = {0.0D, 10.0D, 30.0D, 60.0D, 90.0D};

	private static final String[] LEVEL_ADVANCEMENTS = {"", "comprehension_minor", "comprehension_major", "comprehension_perfect",
			"dao_domain_level"};

	private static final double KILL_GAIN = 0.05D;
	private static final double THUNDER_KILL_GAIN = 0.08D;
	private static final double BODY_HIT_GAIN = 0.02D;
	private static final double LIFE_GAIN = 0.03D;
	private static final double PEARL_GAIN = 0.05D;
	private static final double END_MINUTE_GAIN = 0.02D;
	/** Each action source counts at most once per this many ticks per player. */
	private static final int ACTION_INTERVAL = 40;
	/** Seconds in the End per Space insight. */
	private static final int END_SECONDS = 60;
	/** At or below this light level a kill counts as "from the shadows". */
	private static final int SHADOW_LIGHT = 7;

	/** Rate-limited Dao actions. */
	private enum Source {
		SWORD_KILL,
		FIRE_KILL,
		ICE_KILL,
		THUNDER_KILL,
		BODY_HIT,
		LIFE,
		SHADOW_KILL,
		SPACE_PEARL
	}

	/** Player -> server tick of the last counted action per {@link Source}. */
	private static final Map<UUID, long[]> LAST_ACTION = new HashMap<>();
	/** Player -> seconds spent in the End not yet turned into insight. */
	private static final Map<UUID, Integer> END_SECONDS_SPENT = new HashMap<>();
	/** Life Dao player -> health at the last once-per-second sample (healing is a Life action). */
	private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();
	/** Health regained between two samples that counts as healing. */
	private static final float HEAL_STEP = 1.0F;

	public static void init() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(Comprehension::onKill);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Comprehension::onDamaged);
		UseEntityCallback.EVENT.register(Comprehension::onUseEntity);
		UseBlockCallback.EVENT.register(Comprehension::onUseBlock);
		UseItemCallback.EVENT.register(Comprehension::onUseItem);
		ServerTickEvents.END_SERVER_TICK.register(Comprehension::tickServer);
		ServerPlayerEvents.LEAVE.register(player -> {
			LAST_ACTION.remove(player.getUUID());
			END_SECONDS_SPENT.remove(player.getUUID());
			LAST_HEALTH.remove(player.getUUID());
		});
		// A respawn's full health is no healing.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LAST_HEALTH.remove(newPlayer.getUUID()));
	}

	// ================================================================== gains

	/** Adds comprehension to {@code dao} (x1.5 with a matching root element), with level-up messages. */
	public static void add(ServerPlayer player, Dao dao, double amount) {
		grant(player, dao, amount);
	}

	/** Same as {@link #add}, returning the comprehension really gained (after affinity and the 100 cap). */
	public static double grant(ServerPlayer player, Dao dao, double amount) {
		if (dao == null || !(amount > 0.0D) || !Double.isFinite(amount)) {
			return 0.0D;
		}
		CultivationData data = Cultivators.get(player);
		double before = data.comprehension(dao);
		data.addComprehension(dao, gainFor(data, dao, amount));
		double after = data.comprehension(dao);
		Cultivators.dirty(player);
		announceLevels(player, dao, before, after);
		refreshTechniques(player, dao, before, after);
		return after - before;
	}

	/** Sets the comprehension of {@code dao} (debug); levels crossed upwards are announced like real gains. */
	public static void set(ServerPlayer player, Dao dao, double value) {
		CultivationData data = Cultivators.get(player);
		double before = data.comprehension(dao);
		data.setComprehension(dao, value);
		double after = data.comprehension(dao);
		Cultivators.dirty(player);
		announceLevels(player, dao, before, after);
		refreshTechniques(player, dao, before, after);
	}

	// ================================================================== phase 6c techniques (DESIGN 22.2.6, 22.8)

	/** Technique comprehension a player may gain per Dao per in-game day (22.2.6). */
	public static final double TECHNIQUE_DAILY_CAP = 3.0D;
	/** {@code extra.teknik.cday}: {@code {day, <dao id>: gained}} (22.18.4). */
	private static final String DAILY = "cday";

	/**
	 * Comprehension from techniques, at most {@link #TECHNIQUE_DAILY_CAP} per Dao per in-game day (stored in
	 * {@code extra.teknik.cday}). Returns what was really gained.
	 */
	public static double grantCapped(ServerPlayer player, Dao dao, double amount) {
		if (dao == null || !(amount > 0.0D) || !Double.isFinite(amount)) {
			return 0.0D;
		}
		CultivationData data = Cultivators.get(player);
		net.minecraft.nbt.CompoundTag teknik = data.extra().getCompound("teknik").orElse(null);
		if (teknik == null) {
			teknik = new net.minecraft.nbt.CompoundTag();
			data.extra().put("teknik", teknik);
		}
		net.minecraft.nbt.CompoundTag daily = teknik.getCompoundOrEmpty(DAILY);
		long day = dev.kultivator.technique.spec.run.Mastery.day(player.level());
		if (daily.getLongOr("day", -1L) != day) {
			daily = new net.minecraft.nbt.CompoundTag();
			daily.putLong("day", day);
		}
		double used = daily.getDoubleOr(dao.id(), 0.0D);
		double allowed = Math.min(amount, TECHNIQUE_DAILY_CAP - used);
		if (!(allowed > 0.0D)) {
			return 0.0D;
		}
		daily.putDouble(dao.id(), used + allowed);
		teknik.put(DAILY, daily);
		return grant(player, dao, allowed);
	}

	/**
	 * A spec cast hit a hostile for the first time (22.2.6): +0.15 x the grade factor (1 / 1.2 / 1.5 / 2 / 2.5 / 3) to the
	 * chosen Dao when the spec is of that Dao (universal arts never count), within the daily cap.
	 */
	public static void onTechniqueHit(ServerPlayer player, dev.kultivator.data.TechniqueSpec spec) {
		Dao chosen = Cultivators.get(player).dao();
		if (chosen == null || spec.dao() != chosen) {
			return;
		}
		grantCapped(player, chosen, TECHNIQUE_GAIN * spec.grade().comprehensionFactor());
	}

	/** A COMP route threshold of a {@code dao} spec was crossed: the automatic unlocks run at once (22.8). */
	private static void refreshTechniques(ServerPlayer player, Dao dao, double before, double after) {
		if (!(after > before)) {
			return;
		}
		boolean gate3 = before < dev.kultivator.technique.spec.TechniquePower.MASTERY_GATE_3 && after >= dev.kultivator.technique.spec.TechniquePower.MASTERY_GATE_3;
		boolean gate4 = before < dev.kultivator.technique.spec.TechniquePower.MASTERY_GATE_4 && after >= dev.kultivator.technique.spec.TechniquePower.MASTERY_GATE_4;
		if (gate3 || gate4) {
			// mastery levels 3 / 4 open here with points already banked: the toast, then the unlocks they gate (22.6)
			dev.kultivator.technique.spec.run.Mastery.gateCrossed(player, dao, before, after);
			dev.kultivator.technique.spec.run.Unlocks.refresh(player);
			return;
		}
		for (dev.kultivator.data.TechniqueSpec spec : dev.kultivator.data.TechniqueRegistry.all()) {
			if (spec.dao() != null && spec.dao() != dao) {
				continue;
			}
			for (dev.kultivator.technique.spec.dsl.Route route : spec.routes()) {
				if (route.kind() == dev.kultivator.technique.spec.dsl.Route.Kind.COMP && before < route.n() && after + 1.0E-6D >= route.n()) {
					dev.kultivator.technique.spec.run.Unlocks.refresh(player);
					return;
				}
			}
			if (spec.minComprehension() > 0.0D && before < spec.minComprehension() && after + 1.0E-6D >= spec.minComprehension()) {
				dev.kultivator.technique.spec.run.Unlocks.refresh(player);
				return;
			}
		}
	}

	/** {@code amount} after the root affinity factor. */
	public static double gainFor(CultivationData data, Dao dao, double amount) {
		return hasAffinity(data, dao) ? amount * AFFINITY_FACTOR : amount;
	}

	/** True when the player's root resonates with {@code dao} (x1.5 comprehension). */
	public static boolean hasAffinity(CultivationData data, Dao dao) {
		return data.rootTested() && dao.hasAffinity(data.rootElements());
	}

	/** After a successful cast: only the chosen Dao's own techniques deepen it (universal ones never count). */
	public static void onTechniqueUsed(ServerPlayer player, Technique technique) {
		Dao chosen = Cultivators.get(player).dao();
		if (chosen == null) {
			return;
		}
		if (technique.dao() == chosen) {
			add(player, chosen, TECHNIQUE_GAIN);
		}
	}

	/** Comprehension where the level after {@code level} starts, or -1 at the top. */
	public static double nextThreshold(int level) {
		return level + 1 < LEVEL_THRESHOLDS.length ? LEVEL_THRESHOLDS[level + 1] : -1.0D;
	}

	/** "12.5" / "30" (one decimal at most, no locale surprises). */
	public static String format(double value) {
		double rounded = Math.floor(value * 10.0D + 1.0E-6D) / 10.0D;
		if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-6D) {
			return Long.toString(Math.round(rounded));
		}
		return String.format(Locale.ROOT, "%.1f", rounded);
	}

	/** Grants the advancements of every level crossed and announces the highest one. */
	private static void announceLevels(ServerPlayer player, Dao dao, double before, double after) {
		int oldLevel = Dao.comprehensionLevel(before);
		int newLevel = Dao.comprehensionLevel(after);
		if (newLevel <= oldLevel) {
			return;
		}
		for (int level = oldLevel + 1; level <= newLevel; level++) {
			Advancements.grant(player, LEVEL_ADVANCEMENTS[level]);
		}
		CultivationData data = Cultivators.get(player);
		Component daoName = dao.displayName().copy().withColor(dao.color());
		Component levelName = Dao.comprehensionName(after).copy().withColor(dao.color()).withStyle(ChatFormatting.BOLD);
		Msg.chat(player, Component.translatable("kultivator.dao.level.chat", daoName, levelName, format(after)).withStyle(ChatFormatting.YELLOW));
		if (data.dao() == dao) {
			Msg.chat(player, Component.translatable("kultivator.dao.level.power", format(1.0D + after / 100.0D)).withStyle(ChatFormatting.GRAY));
		}
		if (newLevel >= 4) {
			Msg.chat(player, Component.translatable("kultivator.dao.level.domain", daoName).withStyle(ChatFormatting.GOLD));
		}
		Msg.actionBar(player, Component.translatable("kultivator.dao.level.bar", daoName, levelName));

		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.DAO_CHOSEN, dao.ordinal(), newLevel, 0.0F);
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		float pitch = 0.8F + newLevel * 0.15F;
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, pitch);
		level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, pitch);
		level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.2F);
		if (newLevel >= 3) {
			level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.3F);
		}
		if (newLevel >= 4) {
			level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	// ================================================================== Dao actions

	/** Counts an action of the chosen Dao, at most once per {@link #ACTION_INTERVAL} per source. */
	private static void action(ServerPlayer player, Source source, Dao dao, double amount) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		long[] last = LAST_ACTION.computeIfAbsent(player.getUUID(), id -> {
			long[] fresh = new long[Source.values().length];
			Arrays.fill(fresh, Long.MIN_VALUE / 2L);
			return fresh;
		});
		if (now - last[source.ordinal()] < ACTION_INTERVAL) {
			return;
		}
		last[source.ordinal()] = now;
		add(player, dao, amount);
	}

	/** The chosen Dao of a real, non-spectating player, else null. */
	private static @Nullable Dao chosenDao(@Nullable Entity entity) {
		if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer || player.isSpectator()) {
			return null;
		}
		return Cultivators.get(player).dao();
	}

	/** A monster, or any mob that was after the player. */
	private static boolean isFoe(@Nullable Entity entity, ServerPlayer player) {
		if (!(entity instanceof LivingEntity) || entity instanceof Player) {
			return false;
		}
		return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == player;
	}

	private static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		Dao dao = chosenDao(killer);
		if (dao == null || !(killer instanceof ServerPlayer player) || !isFoe(killed, player)) {
			return;
		}
		switch (dao) {
			case SWORD -> {
				if (player.getMainHandItem().is(ItemTags.SWORDS)) {
					action(player, Source.SWORD_KILL, dao, KILL_GAIN);
				}
			}
			case FIRE -> {
				if (killed.isOnFire()) {
					action(player, Source.FIRE_KILL, dao, KILL_GAIN);
				}
			}
			case ICE -> {
				BlockPos pos = killed.blockPosition();
				if (level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) {
					action(player, Source.ICE_KILL, dao, KILL_GAIN);
				}
			}
			case LIGHTNING -> {
				if (level.isThundering()) {
					action(player, Source.THUNDER_KILL, dao, THUNDER_KILL_GAIN);
				}
			}
			case SHADOW -> {
				if (player.isShiftKeyDown() || level.isDarkOutside()
						|| level.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) <= SHADOW_LIGHT) {
					action(player, Source.SHADOW_KILL, dao, KILL_GAIN);
				}
			}
			default -> {
			}
		}
	}

	/** Body Dao: every monster hit that really hurt. */
	private static void onDamaged(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || !(damageTaken > 0.0F) || chosenDao(entity) != Dao.BODY || !(entity instanceof ServerPlayer player)) {
			return;
		}
		if (isFoe(source.getEntity(), player)) {
			action(player, Source.BODY_HIT, Dao.BODY, BODY_HIT_GAIN);
		}
	}

	/** Life Dao: feeding an animal food it accepts (breeding or growing a baby). Never cancels anything. */
	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
			@Nullable EntityHitResult hitResult) {
		if (level.isClientSide() || chosenDao(player) != Dao.LIFE || !(player instanceof ServerPlayer serverPlayer)
				|| !(entity instanceof Animal animal) || !animal.isAlive()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.isEmpty() && animal.isFood(stack) && (animal.isBaby() || animal.getAge() == 0 && animal.canFallInLove())) {
			action(serverPlayer, Source.LIFE, Dao.LIFE, LIFE_GAIN);
		}
		return InteractionResult.PASS;
	}

	/** Life Dao: bone meal on a plant that can grow. Read-only check; vanilla's bone meal does the rest. */
	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || chosenDao(player) != Dao.LIFE || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!player.getItemInHand(hand).is(Items.BONE_MEAL)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)) {
			action(serverPlayer, Source.LIFE, Dao.LIFE, LIFE_GAIN);
		}
		return InteractionResult.PASS;
	}

	/** Space Dao: throwing an ender pearl (the throw itself is vanilla and never cancelled). */
	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || chosenDao(player) != Dao.SPACE || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Items.ENDER_PEARL) && !player.getCooldowns().isOnCooldown(stack)) {
			action(serverPlayer, Source.SPACE_PEARL, Dao.SPACE, PEARL_GAIN);
		}
		return InteractionResult.PASS;
	}

	/** Once per second: Life Dao healing (health regained since the last sample) and Space Dao time in the End. */
	private static void tickServer(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sampleHealing(player);
			if (chosenDao(player) != Dao.SPACE || !player.isAlive() || player.level().dimension() != Level.END) {
				continue;
			}
			int seconds = END_SECONDS_SPENT.getOrDefault(player.getUUID(), 0) + 1;
			if (seconds >= END_SECONDS) {
				seconds = 0;
				add(player, Dao.SPACE, END_MINUTE_GAIN);
			}
			END_SECONDS_SPENT.put(player.getUUID(), seconds);
		}
	}

	/** Life Dao: health regained since the last sample counts as healing (rate-limited like every action). */
	private static void sampleHealing(ServerPlayer player) {
		if (chosenDao(player) != Dao.LIFE || !player.isAlive()) {
			LAST_HEALTH.remove(player.getUUID());
			return;
		}
		float hp = player.getHealth();
		Float prev = LAST_HEALTH.put(player.getUUID(), hp);
		if (prev != null && hp - prev >= HEAL_STEP) {
			action(player, Source.LIFE, Dao.LIFE, LIFE_GAIN);
		}
	}

	/** "Sword Dao 34.5% (Major Comprehension)" in the Dao colour. */
	public static MutableComponent describe(CultivationData data, Dao dao) {
		double value = data.comprehension(dao);
		return Component.translatable("kultivator.dao.info.value", dao.displayName(), format(value), Dao.comprehensionName(value))
				.withColor(dao.color());
	}

	private Comprehension() {
	}
}
