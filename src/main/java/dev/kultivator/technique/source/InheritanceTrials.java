package dev.kultivator.technique.source;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.registry.ModItems;
import dev.kultivator.sect.Sparring;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ujian Warisan, the inheritance trial of a {@code giok_warisan} (DESIGN 22.8.5; component {@code kultivator:theme} = a
 * Dao id or {@code universal}). From Transformasi Dewa, not deviating and not breaking through, using the jade starts a
 * 10 s omen (tribulation clouds, no weather), then 90 s and three waves of guards through
 * {@code DaoProtectors.spawnWaveFor}: every guard carries {@code kultivator.trial_guard} (the trial-mob rule, never the
 * boss tag); the last wave brings the final guardian with 4× health, {@code kultivator.trial_guard} and
 * {@code kultivator.boss}. Win: choose 1 of 3 arts of the theme rolled by realm (DT-DU Bumi; IA Langit, from T7 70%
 * Langit / 30% Abadi; TI 60% Abadi / 40% Langit), advancement {@code inheritance}. Loss (below 20% health, 24 blocks from
 * the start, a dimension change, logout or the timeout): the jade comes back with a 1-day cooldown. Never lethal: a
 * killing blow ends the trial instead (DeathGuard stage DUEL; unblockable damage still kills).
 * <p>
 * Sources: <i>Renegade Immortal</i> (Tu Si's legacy), <i>Perfect World</i> (bone-text precious arts), <i>A Record of a
 * Mortal's Journey</i> (jade slips). The trial itself is game design.
 */
public final class InheritanceTrials {

	public static final String UNIVERSAL = "universal";
	/** Every theme a jade can carry: the eight Daos and {@code universal}. */
	public static final List<String> THEMES = List.of("sword", "fire", "ice", "lightning", "body", "life", "shadow", "space", UNIVERSAL);
	public static final Realm MIN_REALM = Realm.DEITY_TRANSFORMATION;
	public static final int OMEN_TICKS = 200;
	public static final int FIGHT_TICKS = 1800;
	public static final int WAVES = 3;
	/** The next wave comes when the last one is beaten, or after this many ticks. */
	public static final int WAVE_GAP = 500;
	public static final float LOSS_HEALTH = 0.20F;
	public static final double LEASH = 24.0D;
	public static final long COOLDOWN_TICKS = 24000L;
	public static final double FINAL_HEALTH = 4.0D;
	public static final int CHOICES = 3;
	/** Offered choices stay valid this long. */
	public static final long CHOICE_TICKS = 6000L;
	/** IA layer index (0-8) from which Abadi arts may be rolled (Tahap 7). */
	public static final int ABADI_FROM_LAYER = 6;
	public static final double IA_ABADI_SHARE = 0.30D;
	public static final double TI_ABADI_SHARE = 0.60D;
	/** Comprehension when the theme has nothing left to give. */
	public static final double CONSOLATION_COMPREHENSION = 5.0D;
	public static final String TAG_TRIAL = "kultivator.inheritance";
	/** Player data: {@code extra.teknik.warisan {cd, won [themes], first [boss ids]}}. */
	public static final String KEY = "warisan";
	private static final String PREFIX = "kultivator.sumber.trial.";

	/** {@code kultivator:theme}: the theme of an inheritance jade (saved and synced). */
	public static final DataComponentType<String> THEME = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("theme"), DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());

	public static final Item GIOK_WARISAN = ModItems.register("giok_warisan", JadeItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	private enum Phase { OMEN, FIGHT }

	/** One running trial (session only). */
	private static final class Trial {
		final UUID player;
		final String theme;
		final ResourceKey<Level> dimension;
		final Vec3 start;
		final long begun;
		final boolean returnJade;
		Phase phase = Phase.OMEN;
		long fightStart;
		long nextWave;
		int wave;
		final Set<UUID> guards = new HashSet<>();

		Trial(UUID player, String theme, ResourceKey<Level> dimension, Vec3 start, long begun, boolean returnJade) {
			this.player = player;
			this.theme = theme;
			this.dimension = dimension;
			this.start = start;
			this.begun = begun;
			this.returnJade = returnJade;
		}
	}

	private record Offer(String theme, List<String> ids, long expires) {
	}

	private static final Map<UUID, Trial> ACTIVE = new HashMap<>();
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(InheritanceTrials::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			if (ACTIVE.containsKey(player.getUUID())) {
				lose(player, "left");
			}
			OFFERS.remove(player.getUUID());
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (ACTIVE.containsKey(player.getUUID())) {
				lose(player, "fled");
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			OFFERS.clear();
		});
		DeathGuard.register(DeathGuard.Stage.DUEL, InheritanceTrials::preventDeath);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			for (String root : new String[]{"cultivate", "kultivasi"}) {
				dispatcher.register(Commands.literal(root).then(Commands.literal("warisan")
						.then(Commands.literal("pilih").then(Commands.argument("n", IntegerArgumentType.integer(1, CHOICES))
								.executes(InheritanceTrials::choose)))));
			}
		});
	}

	// ================================================================== the jade

	/** An inheritance jade of {@code theme} (universal when the theme is unknown). */
	public static ItemStack jade(String theme) {
		ItemStack stack = new ItemStack(GIOK_WARISAN);
		stack.set(THEME, THEMES.contains(theme) ? theme : UNIVERSAL);
		return stack;
	}

	/** The theme of a jade (universal by default). */
	public static String themeOf(ItemStack stack) {
		String theme = stack.get(THEME);
		return theme != null && THEMES.contains(theme) ? theme : UNIVERSAL;
	}

	/** The theme's name: the Dao's, or "Universal". */
	public static Component themeName(String theme) {
		Dao dao = Dao.byId(theme);
		return dao == null ? Component.translatable(PREFIX + "theme.universal").withColor(0xFFD54A) : DaoPaths.colored(dao);
	}

	// ================================================================== starting

	/** Starts the trial of {@code theme} (a Dao id or {@code universal}); false when refused (the reason is told). */
	public static boolean start(ServerPlayer player, String theme) {
		return start(player, theme, false);
	}

	/** True while the player's trial runs. */
	public static boolean active(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	/** Why the player cannot begin a trial of {@code theme} now (null when they can). Pure apart from the game time. */
	public static @Nullable Component blocker(ServerPlayer player, String theme) {
		CultivationData data = Cultivators.get(player);
		if (!THEMES.contains(theme)) {
			return Component.translatable(PREFIX + "why.theme");
		}
		if (ACTIVE.containsKey(player.getUUID())) {
			return Component.translatable(PREFIX + "why.running");
		}
		if (!data.realm().atLeast(MIN_REALM)) {
			return Component.translatable(PREFIX + "why.realm", MIN_REALM.displayName().copy().withColor(MIN_REALM.color()));
		}
		if (data.deviating()) {
			return Component.translatable(PREFIX + "why.deviating");
		}
		if (data.breakingThrough()) {
			return Component.translatable(PREFIX + "why.breakthrough");
		}
		if (DuelRules.inDuel(player) || Sparring.inDuel(player)) {
			return Component.translatable(PREFIX + "why.duel");
		}
		long left = cooldownLeft(player);
		if (left > 0L) {
			return Component.translatable(PREFIX + "why.cooldown", Msg.number(Math.ceil(left / 1200.0D)));
		}
		Dao dao = Dao.byId(theme);
		if (dao != null && data.dao() != dao) {
			return Component.translatable(PREFIX + "why.dao", themeName(theme));
		}
		return null;
	}

	/** Ticks left of the player's 1-day cooldown after a lost trial. */
	public static long cooldownLeft(ServerPlayer player) {
		long until = section(Cultivators.get(player)).getLongOr("cd", 0L);
		return Math.max(0L, until - player.level().getGameTime());
	}

	private static boolean start(ServerPlayer player, String theme, boolean returnJade) {
		Component why = blocker(player, theme);
		if (why != null) {
			Msg.chat(player, Component.translatable(PREFIX + "refuse", why).withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerLevel level = player.level();
		Trial trial = new Trial(player.getUUID(), theme, level.dimension(), player.position(), level.getGameTime(), returnJade);
		ACTIVE.put(player.getUUID(), trial);
		Vec3 at = player.position();
		Fx.send(level, at, at.add(0.0D, 24.0D, 0.0D), FxType.TRIBULATION_OMEN, Tribulation.variant(level), OMEN_TICKS, 0.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.6F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		Msg.title(player, Component.translatable(PREFIX + "title").withStyle(ChatFormatting.GOLD),
				Component.translatable(PREFIX + "subtitle", themeName(theme)), 10, 60, 20);
		Msg.chat(player, Component.translatable(PREFIX + "omen", themeName(theme), OMEN_TICKS / 20, FIGHT_TICKS / 20, (int) LEASH)
				.withStyle(ChatFormatting.GOLD));
		return true;
	}

	// ================================================================== running

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		for (Trial trial : List.copyOf(ACTIVE.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(trial.player);
			if (player == null) {
				ACTIVE.remove(trial.player);
				continue;
			}
			try {
				tick(player, trial);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: inheritance trial of {} failed", player.getName().getString(), exception);
				lose(player, "fled");
			}
		}
	}

	private static void tick(ServerPlayer player, Trial trial) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive() || level.dimension() != trial.dimension) {
			lose(player, "fled");
			return;
		}
		if (trial.phase == Phase.OMEN) {
			if (now - trial.begun >= OMEN_TICKS) {
				trial.phase = Phase.FIGHT;
				trial.fightStart = now;
				if (!nextWave(player, trial)) {
					// Peaceful difficulty or no free ground: nothing can be fought, nothing is lost.
					cancel(player, trial);
				}
			}
			return;
		}
		if ((now - trial.fightStart) % 10L != 0L) {
			return;
		}
		if (player.getHealth() < player.getMaxHealth() * LOSS_HEALTH) {
			lose(player, "fallen");
			return;
		}
		if (player.position().distanceToSqr(trial.start) > LEASH * LEASH) {
			lose(player, "fled");
			return;
		}
		if (now - trial.fightStart >= FIGHT_TICKS) {
			lose(player, "timeout");
			return;
		}
		int alive = living(level, trial);
		if (trial.wave < WAVES && (alive == 0 || now >= trial.nextWave)) {
			nextWave(player, trial);
			alive = living(level, trial);
		}
		if (trial.wave >= WAVES && alive == 0) {
			win(player, trial);
			return;
		}
		if ((now - trial.fightStart) % 20L == 0L) {
			long left = Math.max(0L, FIGHT_TICKS - (now - trial.fightStart)) / 20L;
			Msg.actionBar(player, Component.translatable(PREFIX + "bar", trial.wave, WAVES, left, alive).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Counts the trial's living guards (forgets the dead ones). */
	private static int living(ServerLevel level, Trial trial) {
		trial.guards.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive());
		return trial.guards.size();
	}

	/** Raises the next wave (the last one brings the final guardian). False when nothing could arrive. */
	private static boolean nextWave(ServerPlayer player, Trial trial) {
		ServerLevel level = player.level();
		CultivationData data = Cultivators.get(player);
		Rank rank = data.rank();
		int size = 2 + rank.realm().ordinal() / 3;
		trial.wave++;
		trial.nextWave = level.getGameTime() + WAVE_GAP;
		int arrived = DaoProtectors.spawnWaveFor(level, player, null, rank, size, 1.0D);
		tagNew(player, trial, false);
		if (trial.wave >= WAVES) {
			arrived += DaoProtectors.spawnWaveFor(level, player, null, rank, 1, FINAL_HEALTH);
			tagNew(player, trial, true);
			Msg.chat(player, Component.translatable(PREFIX + "guardian").withStyle(ChatFormatting.RED));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0F, 0.6F);
		} else {
			Msg.chat(player, Component.translatable(PREFIX + "wave", trial.wave, WAVES, arrived).withStyle(ChatFormatting.DARK_RED));
		}
		return arrived > 0;
	}

	/** Gives the protectors that just arrived for the player the trial tags (and the boss tag for the final guardian). */
	private static void tagNew(ServerPlayer player, Trial trial, boolean guardian) {
		ServerLevel level = player.level();
		List<Mob> fresh = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LEASH),
				mob -> DaoProtectors.isWaveOf(mob, player) && !mob.entityTags().contains(TAG_TRIAL));
		for (Mob mob : fresh) {
			mob.addTag(TAG_TRIAL);
			mob.addTag(TechniquePower.TAG_TRIAL_GUARD);
			if (guardian) {
				mob.addTag(TechniquePower.TAG_BOSS);
				mob.setCustomName(Component.translatable(PREFIX + "guardian_name").withStyle(ChatFormatting.GOLD));
				mob.setCustomNameVisible(true);
			}
			trial.guards.add(mob.getUUID());
		}
	}

	// ================================================================== endings

	/** The trial could not begin (no guard arrived): no cooldown, the jade comes back. */
	private static void cancel(ServerPlayer player, Trial trial) {
		ACTIVE.remove(player.getUUID());
		DaoProtectors.clear(player);
		returnJade(player, trial);
		Msg.chat(player, Component.translatable(PREFIX + "cancelled").withStyle(ChatFormatting.GRAY));
	}

	private static void lose(ServerPlayer player, String reason) {
		Trial trial = ACTIVE.remove(player.getUUID());
		if (trial == null) {
			return;
		}
		DaoProtectors.clear(player);
		CultivationData data = Cultivators.get(player);
		section(data).putLong("cd", player.level().getGameTime() + COOLDOWN_TICKS);
		Cultivators.dirty(player);
		returnJade(player, trial);
		ServerLevel level = player.level();
		Fx.send(level, trial.start, trial.start.add(0.0D, 24.0D, 0.0D), FxType.TRIBULATION_END, Tribulation.variant(level), 0, 0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.4F);
		Msg.chat(player, Component.translatable(PREFIX + "lost." + reason).withStyle(ChatFormatting.RED));
		Msg.chat(player, Component.translatable(PREFIX + "lost_after", COOLDOWN_TICKS / 24000L).withStyle(ChatFormatting.GRAY));
	}

	private static void returnJade(ServerPlayer player, Trial trial) {
		if (!trial.returnJade) {
			return;
		}
		ItemStack jade = jade(trial.theme);
		if (!player.getInventory().add(jade) && !jade.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), jade);
			item.setNoPickUpDelay();
			item.addTag(dev.kultivator.util.NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	private static void win(ServerPlayer player, Trial trial) {
		ACTIVE.remove(player.getUUID());
		DaoProtectors.clear(player);
		ServerLevel level = player.level();
		CultivationData data = Cultivators.get(player);
		CompoundTag mine = section(data);
		ListTag won = mine.getListOrEmpty("won").copy();
		boolean seen = false;
		for (Tag tag : won) {
			seen |= trial.theme.equals(tag.asString().orElse(""));
		}
		if (!seen) {
			won.add(StringTag.valueOf(trial.theme));
			mine.put("won", won);
		}
		Cultivators.dirty(player);
		Fx.send(level, trial.start, trial.start.add(0.0D, 24.0D, 0.0D), FxType.TRIBULATION_END, Tribulation.variant(level), 1, 0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		Msg.title(player, Component.translatable(PREFIX + "won_title").withStyle(ChatFormatting.GOLD), themeName(trial.theme), 10, 60, 20);
		Advancements.grant(player, "inheritance");
		List<String> ids = roll(data, trial.theme, player.getRandom());
		if (ids.isEmpty()) {
			Dao dao = Dao.byId(trial.theme);
			Dao target = dao != null ? dao : data.dao();
			Msg.chat(player, Component.translatable(PREFIX + "nothing_left", themeName(trial.theme)).withStyle(ChatFormatting.YELLOW));
			if (target != null) {
				double gained = Comprehension.grant(player, target, CONSOLATION_COMPREHENSION);
				Msg.chat(player, Component.translatable("kultivator.dao.book.insight_gain", Comprehension.format(gained), DaoPaths.colored(target),
						Comprehension.format(data.comprehension(target))).withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		OFFERS.put(player.getUUID(), new Offer(trial.theme, ids, level.getGameTime() + CHOICE_TICKS));
		Msg.chat(player, Component.translatable(PREFIX + "choose", themeName(trial.theme), CHOICE_TICKS / 1200L).withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < ids.size(); i++) {
			TechniqueSpec spec = TechniqueRegistry.byId(ids.get(i));
			if (spec == null) {
				continue;
			}
			String command = "/kultivasi warisan pilih " + (i + 1);
			MutableComponent hover = Component.empty().append(LearnRules.name(spec)).append("\n").append(LearnRules.gradeLine(spec)).append("\n")
					.append(spec.description().copy().withStyle(ChatFormatting.GRAY)).append("\n")
					.append(spec.source().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			MutableComponent line = Component.translatable(PREFIX + "option", i + 1, LearnRules.name(spec), LearnRules.gradeLine(spec))
					.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(hover)));
			Msg.chat(player, line);
		}
	}

	/** {@code /kultivasi warisan pilih <n>}: learns the chosen art of the last offer. */
	private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int n = IntegerArgumentType.getInteger(ctx, "n");
		Offer offer = OFFERS.get(player.getUUID());
		if (offer == null || player.level().getGameTime() > offer.expires()) {
			OFFERS.remove(player.getUUID());
			Msg.chat(player, Component.translatable(PREFIX + "no_offer").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (n < 1 || n > offer.ids().size()) {
			return 0;
		}
		TechniqueSpec spec = TechniqueRegistry.byId(offer.ids().get(n - 1));
		if (spec == null) {
			return 0;
		}
		OFFERS.remove(player.getUUID());
		if (Cultivators.get(player).knows(spec.id())) {
			Mastery.add(player, spec.id(), JadeSlips.MASTERY_DUPLICATE, Mastery.Source.SLIP);
			Msg.chat(player, Component.translatable("kultivator.sumber.slip.known", LearnRules.name(spec), JadeSlips.MASTERY_DUPLICATE)
					.withStyle(ChatFormatting.YELLOW));
			return 1;
		}
		LearnRules.learn(player, spec, "inheritance");
		return 1;
	}

	/**
	 * Rolls up to 3 different arts of the theme for the player (DESIGN 22.8.5): the grade by realm, the arts among the
	 * theme's specs (its Dao's, the universal ones, or any spec with an INH route of that theme) that a jade slip may hold,
	 * that are new to the player and fit their Dao. The realm gate is waived (the cast still needs it); a grade without
	 * candidates falls back to the other rolled grade, then to lower grades.
	 */
	public static List<String> roll(CultivationData data, String theme, RandomSource random) {
		List<String> picked = new ArrayList<>();
		for (int i = 0; i < CHOICES; i++) {
			List<TechniqueGrade> order = gradeOrder(data, random);
			for (TechniqueGrade grade : order) {
				List<TechniqueSpec> pool = candidates(data, theme, grade, picked);
				if (!pool.isEmpty()) {
					picked.add(pool.get(random.nextInt(pool.size())).id());
					break;
				}
			}
		}
		return picked;
	}

	/** The rolled grade first, then the other grade of the realm's table, then lower grades. */
	private static List<TechniqueGrade> gradeOrder(CultivationData data, RandomSource random) {
		Rank rank = data.rank();
		List<TechniqueGrade> order = new ArrayList<>();
		if (rank.realm() == Realm.TRUE_IMMORTAL) {
			boolean abadi = random.nextDouble() < TI_ABADI_SHARE;
			order.add(abadi ? TechniqueGrade.ABADI : TechniqueGrade.TIAN);
			order.add(abadi ? TechniqueGrade.TIAN : TechniqueGrade.ABADI);
		} else if (rank.realm() == Realm.IMMORTAL_ASCENSION) {
			boolean abadi = rank.layer() >= ABADI_FROM_LAYER && random.nextDouble() < IA_ABADI_SHARE;
			order.add(abadi ? TechniqueGrade.ABADI : TechniqueGrade.TIAN);
			if (rank.layer() >= ABADI_FROM_LAYER) {
				order.add(abadi ? TechniqueGrade.TIAN : TechniqueGrade.ABADI);
			}
		} else {
			order.add(TechniqueGrade.DI);
		}
		for (TechniqueGrade lower : new TechniqueGrade[]{TechniqueGrade.TIAN, TechniqueGrade.DI, TechniqueGrade.XUAN, TechniqueGrade.HUANG}) {
			if (!order.contains(lower) && lower.tier() < order.get(0).tier()) {
				order.add(lower);
			}
		}
		return order;
	}

	private static List<TechniqueSpec> candidates(CultivationData data, String theme, TechniqueGrade grade, List<String> picked) {
		Dao dao = Dao.byId(theme);
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.grade() != grade || !spec.slipAllowed() || !spec.playerVisible() || data.knows(spec.id()) || picked.contains(spec.id())
					|| !LearnRules.daoFits(data, spec)) {
				continue;
			}
			boolean themed = dao == null ? spec.dao() == null : spec.dao() == dao;
			for (Route route : spec.routes()) {
				themed |= route.kind() == Route.Kind.INH && theme.equals(route.key());
			}
			if (themed) {
				list.add(spec);
			}
		}
		return list;
	}

	// ================================================================== never lethal

	private static boolean preventDeath(ServerPlayer player, DamageSource source, float amount) {
		if (!ACTIVE.containsKey(player.getUUID()) || source.is(DamageHooks.UNBLOCKABLE)) {
			return false;
		}
		player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.1F));
		lose(player, "fallen");
		return true;
	}

	// ================================================================== data

	/** The player's {@code extra.teknik.warisan} compound (created when missing). */
	static CompoundTag section(CultivationData data) {
		CompoundTag teknik = data.extra().getCompound(Mastery.EXTRA).orElse(null);
		if (teknik == null) {
			teknik = new CompoundTag();
			data.extra().put(Mastery.EXTRA, teknik);
		}
		CompoundTag mine = teknik.getCompound(KEY).orElse(null);
		if (mine == null) {
			mine = new CompoundTag();
			teknik.put(KEY, mine);
		}
		return mine;
	}

	// ================================================================== the item

	/** Giok Warisan: right-click begins the trial of its theme; the jade is kept by the trial and comes back on a loss. */
	public static class JadeItem extends Item {

		public JadeItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			String theme = themeOf(stack);
			if (!start(serverPlayer, theme, true)) {
				return InteractionResult.PASS;
			}
			stack.shrink(1);
			return InteractionResult.SUCCESS;
		}

		@Override
		public Component getName(ItemStack stack) {
			return Component.translatable("item.kultivator.giok_warisan.named", themeName(themeOf(stack)));
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable(PREFIX + "tooltip.theme", themeName(themeOf(stack))).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable(PREFIX + "tooltip.use", MIN_REALM.displayName(), FIGHT_TICKS / 20).withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable(PREFIX + "tooltip.rule", (int) LEASH).withStyle(ChatFormatting.DARK_GRAY));
			builder.accept(Component.translatable(PREFIX + "tooltip.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private InheritanceTrials() {
	}
}
