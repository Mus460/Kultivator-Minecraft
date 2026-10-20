package dev.kultivator.abadi;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.alam.Alam;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kesengsaraan Kenaikan Alam, the ascension tribulation (DESIGN 26.3.2-26.3.3). Gate: Dewa Abadi Puncak, ★9 or more, the
 * DESIGN 17 ascension, not busy, not hurt for 10 s, not Peaceful; offering 9 Esensi Energi Makam + 1 Supreme spirit stone,
 * taken at the start and given back on a failure (balance change); a failure waits one in-game day. The ritual: a 10 s omen, nine strikes through
 * {@code Tribulation.strike} (a would-kill strike is a failure, never a death), envoy waves after strikes 3 and 6
 * ({@code Purba.spawnEnvoyWave}), then the black hole (RMJ ch. 2451): 15 s inside an 8-block circle against an outward drag.
 * Success opens Alam Abadi; failure gives "Luka Kenaikan" (Qi Deviation 12,000 ticks). DeathGuard {@code LANGIT} turns any
 * lethal blow during the ritual into a failure, so totems and cheat-death charges are never spent. No block, no
 * {@code LightningBolt}: every bolt and the vortex are client visuals.
 */
final class Ascend {

	public static final int OFFER_ESSENCE = 9;
	public static final String ESSENCE_ID = "tomb_essence";
	public static final int OMEN_TICKS = 200;
	public static final int STRIKES = 9;
	public static final int STRIKE_GAP = 60;
	public static final double STRIKE_BASE = 0.13D;
	public static final double STRIKE_STEP = 0.01D;
	public static final int WAVE_TIMEOUT = 900;
	public static final int VORTEX_TICKS = 300;
	public static final double VORTEX_RADIUS = 8.0D;
	public static final double VORTEX_HEIGHT = 14.0D;
	public static final double DRAG = 0.08D;
	public static final long RETRY_TICKS = 24000L;
	public static final int LUKA_TICKS = Deviation.MAX_TICKS;
	public static final int PASS_DELAY = 60;
	public static final int GIFT_STONES = 16;

	private enum Phase { OMEN, STRIKES, WAVE, VORTEX, PASSING }

	private static final class Ritual {
		final UUID id;
		final boolean free;
		final @Nullable Boolean forced;
		final ResourceKey<Level> dim;
		final Vec3 start;
		final ServerBossEvent bar;
		Phase phase = Phase.OMEN;
		long t;
		long nextStrike;
		int strikes;
		long waveUntil;
		volatile boolean waveDone;
		int vortexT;
		long passAt;
		boolean over;
		/** What {@link #takeOffering} took: refunded on a failure (balance change). */
		int essencePaid;
		boolean stonePaid;

		Ritual(ServerPlayer player, boolean free, @Nullable Boolean forced) {
			this.id = player.getUUID();
			this.free = free;
			this.forced = forced;
			this.dim = player.level().dimension();
			this.start = player.position();
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.abadi.ritual.bar"), BossEvent.BossBarColor.YELLOW,
					BossEvent.BossBarOverlay.NOTCHED_10);
		}
	}

	private static final Map<UUID, Ritual> RITUALS = new HashMap<>();

	static void init() {
		DeathGuard.register(DeathGuard.Stage.LANGIT, Ascend::preventDeath);
		ServerPlayerEvents.LEAVE.register(player -> {
			Ritual ritual = RITUALS.get(player.getUUID());
			if (ritual != null) {
				fail(player, ritual, "kultivator.abadi.ritual.fail.left");
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Ritual ritual : RITUALS.values()) {
				ritual.bar.removeAllPlayers();
			}
			RITUALS.clear();
		});
	}

	static boolean active(ServerPlayer player) {
		return RITUALS.containsKey(player.getUUID());
	}

	// ================================================================== gate (26.3.2)

	/** The Esensi Energi Makam item (by registry id), or AIR when it is missing. */
	static Item essenceItem() {
		return BuiltInRegistries.ITEM.getValue(Kultivator.id(ESSENCE_ID));
	}

	static ItemStack essenceStack(int count) {
		Item item = essenceItem();
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
	}

	private static int essenceHave(ServerPlayer player) {
		Item item = essenceItem();
		return item == Items.AIR ? 0 : player.getInventory().countItem(item);
	}

	private static int supremeHave(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (SpiritStones.isStone(stack) && SpiritStones.grade(stack) == SpiritStones.SUPREME) {
				n += stack.getCount();
			}
		}
		return n;
	}

	static long cooldownLeft(ServerPlayer player) {
		return Math.max(0L, Abadi.extra(player).getLongOr("asc_cd", 0L) - Abadi.now(player.level().getServer()));
	}

	/** Why the ritual cannot start now, or null ({@code free}: only the "already running" and dimension checks). */
	static @Nullable Component blocker(ServerPlayer player, boolean free) {
		if (!KultivatorConfig.get().immortalRealm) {
			return Component.translatable("kultivator.abadi.why.disabled");
		}
		if (Abadi.immortalLevel(player.level().getServer()) == null) {
			return Component.translatable("kultivator.abadi.why.no_dimension");
		}
		if (active(player)) {
			return Component.translatable("kultivator.abadi.why.running");
		}
		if (Alam.isRealmLevel(player.level()) || Alam.inRun(player)) {
			return Component.translatable("kultivator.abadi.why.busy");
		}
		if (free) {
			return null;
		}
		if (Abadi.ascended(player)) {
			return Component.translatable("kultivator.abadi.why.already");
		}
		if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return Component.translatable("kultivator.abadi.why.peaceful");
		}
		CultivationData data = Cultivators.get(player);
		if (!data.rank().isMax()) {
			return Component.translatable("kultivator.abadi.why.peak");
		}
		if (TechniquePower.playerStars(data) < ImmortalPerks.GATE_I_STARS) {
			return Component.translatable("kultivator.abadi.why.stars", ImmortalPerks.GATE_I_STARS);
		}
		if (!Ascension.isAscended(player) || Ascension.isActive(player)) {
			return Component.translatable("kultivator.abadi.why.ascension");
		}
		long left = cooldownLeft(player);
		if (left > 0L) {
			return Component.translatable("kultivator.abadi.why.cooldown", Msg.number(Math.ceil(left / 1200.0D)));
		}
		Component busy = Abadi.busyReason(player, false);
		if (busy != null) {
			return busy;
		}
		if (Abadi.recentlyHurt(player)) {
			return Component.translatable("kultivator.abadi.why.hurt");
		}
		if (!player.isCreative() && (essenceHave(player) < OFFER_ESSENCE || supremeHave(player) < 1)) {
			return Component.translatable("kultivator.abadi.why.offering", essenceHave(player), OFFER_ESSENCE, supremeHave(player));
		}
		return null;
	}

	/** Takes the offering and notes on {@code ritual} what was taken, so a failure can give it back. */
	private static void takeOffering(ServerPlayer player, Ritual ritual) {
		if (player.isCreative()) {
			return;
		}
		Item essence = essenceItem();
		int needEssence = OFFER_ESSENCE;
		boolean stoneTaken = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (needEssence > 0 && essence != Items.AIR && stack.is(essence)) {
				int take = Math.min(needEssence, stack.getCount());
				stack.shrink(take);
				needEssence -= take;
			} else if (!stoneTaken && SpiritStones.isStone(stack) && SpiritStones.grade(stack) == SpiritStones.SUPREME) {
				stack.shrink(1);
				stoneTaken = true;
			}
		}
		ritual.essencePaid = OFFER_ESSENCE - needEssence;
		ritual.stonePaid = stoneTaken;
	}

	/**
	 * Balance change (flow audit): a failed ritual gives the whole offering back (the 9 Esensi Energi Makam and the Supreme
	 * spirit stone); the Ascension Wound and the one-day wait stay. Into the inventory (overflow to the sect storehouse or
	 * the ground, as {@code Abadi.give}); a player who is no longer alive gets it as items on the ground.
	 */
	private static void refundOffering(ServerPlayer player, Ritual ritual) {
		int essence = ritual.essencePaid;
		boolean stone = ritual.stonePaid;
		ritual.essencePaid = 0;
		ritual.stonePaid = false;
		if (essence <= 0 && !stone) {
			return;
		}
		List<ItemStack> stacks = new java.util.ArrayList<>();
		if (essence > 0) {
			stacks.add(essenceStack(essence));
		}
		if (stone) {
			stacks.add(SpiritStones.stack(SpiritStones.SUPREME, 1));
		}
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			if (player.isAlive()) {
				Abadi.give(player, stack);
			} else {
				net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(),
						player.getY() + 0.5D, player.getZ(), stack);
				item.addTag(dev.kultivator.util.NoGrief.TAG);
				player.level().addFreshEntity(item);
			}
		}
		Msg.chat(player, Component.translatable("kultivator.abadi.ritual.refund", essence, stone ? 1 : 0).withStyle(ChatFormatting.GOLD));
	}

	// ================================================================== the ritual (26.3.3)

	/** Starts the ritual ({@code free}: no gate, offering or cooldown; {@code forced}: the test's forced result). */
	static boolean start(ServerPlayer player, boolean free, @Nullable Boolean forced) {
		Component why = blocker(player, free);
		if (why != null) {
			Msg.chat(player, Component.translatable("kultivator.abadi.ritual.refuse", why).withStyle(ChatFormatting.GRAY));
			return false;
		}
		Ritual ritual = new Ritual(player, free, forced);
		if (!free) {
			takeOffering(player, ritual);
		}
		RITUALS.put(player.getUUID(), ritual);
		ritual.bar.addPlayer(player);
		ritual.bar.setProgress(0.0F);
		ServerLevel level = player.level();
		Vec3 at = player.position();
		EpicHooks.extra(level, EpicIds.EXTRA_ASCEND_OMEN, player, player, at, 0, 0);
		Fx.send(level, at, at.add(0.0D, VORTEX_HEIGHT, 0.0D), FxType.ABADI, AbadiFxIds.OMEN, OMEN_TICKS / 20, 0.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.4F);
		Msg.chat(player, Component.translatable("kultivator.abadi.ritual.omen").withStyle(ChatFormatting.GOLD));
		Msg.title(player, Component.translatable("kultivator.abadi.ritual.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.abadi.ritual.subtitle").withStyle(ChatFormatting.YELLOW), 10, 60, 20);
		return true;
	}

	static void tick(MinecraftServer server, long ticks) {
		if (RITUALS.isEmpty()) {
			return;
		}
		for (Ritual ritual : List.copyOf(RITUALS.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(ritual.id);
			if (player == null) {
				RITUALS.remove(ritual.id);
				ritual.bar.removeAllPlayers();
				continue;
			}
			if (ritual.over) {
				continue;
			}
			if (!player.isAlive() || player.level().dimension() != ritual.dim) {
				fail(player, ritual, "kultivator.abadi.ritual.fail.left");
				continue;
			}
			ritual.t++;
			step(player, ritual);
		}
	}

	private static void step(ServerPlayer player, Ritual ritual) {
		ServerLevel level = player.level();
		Vec3 sky = ritual.start.add(0.0D, VORTEX_HEIGHT, 0.0D);
		switch (ritual.phase) {
			case OMEN -> {
				ritual.bar.setProgress(Mth.clamp(0.1F * ritual.t / OMEN_TICKS, 0.0F, 1.0F));
				if (ritual.t % 40L == 0L) {
					Fx.send(level, ritual.start, sky, FxType.ABADI, AbadiFxIds.OMEN, (int) Math.max(1L, (OMEN_TICKS - ritual.t) / 20L), 0.0F);
				}
				if (ritual.t >= OMEN_TICKS) {
					ritual.phase = Phase.STRIKES;
					ritual.nextStrike = ritual.t;
				}
			}
			case STRIKES -> {
				if (ritual.t < ritual.nextStrike) {
					return;
				}
				int number = ritual.strikes + 1;
				Vec3 at = player.position();
				Fx.send(level, at, at.add(0.0D, 40.0D, 0.0D), FxType.ABADI, AbadiFxIds.STRIKE, number, number >= STRIKES ? 2.0F : 1.0F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.5F, 0.6F + 0.04F * number);
				boolean wouldKill = Tribulation.strike(player, STRIKE_BASE + STRIKE_STEP * number);
				ritual.strikes = number;
				ritual.bar.setProgress(Mth.clamp(0.1F + 0.6F * number / STRIKES, 0.0F, 1.0F));
				Msg.actionBar(player, Component.translatable("kultivator.abadi.ritual.strike", number, STRIKES).withStyle(ChatFormatting.YELLOW));
				if (Boolean.FALSE.equals(ritual.forced) || (wouldKill && !Boolean.TRUE.equals(ritual.forced))) {
					fail(player, ritual, "kultivator.abadi.ritual.fail.strike");
					return;
				}
				if (number == 3 || number == 6) {
					ritual.phase = Phase.WAVE;
					ritual.waveDone = false;
					ritual.waveUntil = ritual.t + WAVE_TIMEOUT;
					Msg.chat(player, Component.translatable("kultivator.abadi.ritual.wave").withStyle(ChatFormatting.RED));
					dev.kultivator.purba.Purba.spawnEnvoyWave(player, 2 + number / 3, 0, cleared -> ritual.waveDone = true);
				} else if (number >= STRIKES) {
					ritual.phase = Phase.VORTEX;
					ritual.vortexT = 0;
					Fx.send(level, ritual.start, sky, FxType.ABADI, AbadiFxIds.VORTEX, VORTEX_TICKS, (float) VORTEX_RADIUS);
					level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.2F, 0.5F);
					Msg.chat(player, Component.translatable("kultivator.abadi.ritual.vortex", (int) VORTEX_RADIUS, VORTEX_TICKS / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
				} else {
					ritual.nextStrike = ritual.t + STRIKE_GAP;
				}
			}
			case WAVE -> {
				if (ritual.waveDone || ritual.t >= ritual.waveUntil) {
					ritual.phase = Phase.STRIKES;
					ritual.nextStrike = ritual.t + STRIKE_GAP;
				}
			}
			case VORTEX -> {
				ritual.vortexT++;
				ritual.bar.setProgress(Mth.clamp(0.7F + 0.3F * ritual.vortexT / VORTEX_TICKS, 0.0F, 1.0F));
				if (ritual.vortexT % 40 == 0) {
					Fx.send(level, ritual.start, sky, FxType.ABADI, AbadiFxIds.VORTEX, VORTEX_TICKS - ritual.vortexT, (float) VORTEX_RADIUS);
				}
				double dx = player.getX() - ritual.start.x;
				double dz = player.getZ() - ritual.start.z;
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance > VORTEX_RADIUS && !Boolean.TRUE.equals(ritual.forced)) {
					fail(player, ritual, "kultivator.abadi.ritual.fail.circle");
					return;
				}
				// The black hole's pull throws the cultivator outward (velocity only, never a block).
				double nx = distance < 0.05D ? Math.cos(ritual.t * 0.3D) : dx / distance;
				double nz = distance < 0.05D ? Math.sin(ritual.t * 0.3D) : dz / distance;
				if (!player.isCreative() && !player.isSpectator()) {
					player.setDeltaMovement(player.getDeltaMovement().add(nx * DRAG, 0.0D, nz * DRAG));
					player.connection.send(new ClientboundSetEntityMotionPacket(player));
				}
				if (ritual.vortexT % 20 == 0) {
					Msg.actionBar(player, Component.translatable("kultivator.abadi.ritual.hold", (VORTEX_TICKS - ritual.vortexT) / 20,
							Math.round(distance * 10.0D) / 10.0D, (int) VORTEX_RADIUS).withStyle(ChatFormatting.LIGHT_PURPLE));
				}
				if (ritual.vortexT >= VORTEX_TICKS) {
					pass(player, ritual);
				}
			}
			case PASSING -> {
				if (ritual.t >= ritual.passAt) {
					finish(player, ritual);
				}
			}
		}
	}

	private static void pass(ServerPlayer player, Ritual ritual) {
		ServerLevel level = player.level();
		ritual.phase = Phase.PASSING;
		ritual.passAt = ritual.t + PASS_DELAY;
		ritual.bar.setProgress(1.0F);
		Vec3 at = player.position();
		Fx.send(level, at, ritual.start.add(0.0D, VORTEX_HEIGHT, 0.0D), FxType.ABADI, AbadiFxIds.STOP, 1, 0.0F);
		Fx.send(level, at, at, FxType.ABADI, AbadiFxIds.PASS, 0, 0.0F);
		// 23.1: the camera shot only after the danger is over.
		EpicHooks.extra(level, EpicIds.EXTRA_ASCEND_PASS, player, player, at, 0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		CompoundTag extra = Abadi.extra(player);
		extra.putBoolean("ascended", true);
		extra.remove("asc_cd");
		saveReturn(player);
		Cultivators.dirty(player);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PASS_DELAY + 40, 4, false, false, true));
		Msg.title(player, Component.translatable("kultivator.abadi.ritual.pass_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.abadi.ritual.pass_subtitle").withStyle(ChatFormatting.YELLOW), 10, 60, 20);
	}

	private static void finish(ServerPlayer player, Ritual ritual) {
		ritual.over = true;
		RITUALS.remove(ritual.id);
		ritual.bar.removeAllPlayers();
		arrive(player, true);
	}

	/**
	 * Teleports the player (with up to 3 FOLLOW disciples of Murid Inti+) to the arrival island; {@code ascension}: the
	 * first-arrival title card, the gifts (once per player), the ascend listeners and the advancement.
	 */
	static void arrive(ServerPlayer player, boolean ascension) {
		MinecraftServer server = player.level().getServer();
		ServerLevel target = Abadi.immortalLevel(server);
		if (target == null) {
			Msg.chat(player, Component.translatable("kultivator.abadi.why.no_dimension").withStyle(ChatFormatting.RED));
			return;
		}
		BlockPos island = ArrivalIsland.arrival(server);
		Vec3 pos = new Vec3(island.getX() + 0.5D, island.getY() + 0.1D, island.getZ() + 0.5D);
		ServerPlayer moved = Crossing.travel(player, target, pos, player.getYRot(), player.getXRot(), java.util.List.of());
		if (moved == null) {
			return;
		}
		if (!ascension) {
			return;
		}
		CompoundTag extra = Abadi.extra(moved);
		Msg.title(moved, Component.translatable("kultivator.abadi.arrive.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
				Component.translatable("kultivator.abadi.arrive.subtitle").withStyle(ChatFormatting.WHITE), 20, 80, 30);
		if (!extra.getBooleanOr("gifted", false)) {
			extra.putBoolean("gifted", true);
			Abadi.give(moved, new ItemStack(AbadiItems.REALM_CROSSING_TALISMAN));
			Abadi.giveStones(moved, GIFT_STONES);
			Msg.chat(moved, Component.translatable("kultivator.abadi.arrive.gifts", GIFT_STONES).withStyle(ChatFormatting.GOLD));
			Cultivators.dirty(moved);
		}
		Advancements.grant(moved, "immortal_realm");
		Abadi.notifyAscended(moved);
		Abadi.sync(moved);
	}

	/** Saves where the player stands now as {@code extra.abadi.ret} (the {@code ReturnPoint} layout). */
	static void saveReturn(ServerPlayer player) {
		if (Abadi.isImmortalLevel(player.level())) {
			return;
		}
		CompoundTag ret = new CompoundTag();
		ret.putString("dim", player.level().dimension().identifier().toString());
		ret.putDouble("x", player.getX());
		ret.putDouble("y", player.getY());
		ret.putDouble("z", player.getZ());
		ret.putFloat("yRot", player.getYRot());
		ret.putFloat("xRot", player.getXRot());
		Abadi.extra(player).put("ret", ret);
		Cultivators.dirty(player);
	}

	/**
	 * Ends the ritual as a failure: Luka Kenaikan (Qi Deviation 12,000 ticks), the cooldown starts, and (balance change)
	 * the offering comes back.
	 */
	private static void fail(ServerPlayer player, Ritual ritual, String reasonKey) {
		if (ritual.over) {
			return;
		}
		ritual.over = true;
		RITUALS.remove(ritual.id);
		ritual.bar.removeAllPlayers();
		Fx.send(player.level(), player.position(), ritual.start.add(0.0D, VORTEX_HEIGHT, 0.0D), FxType.ABADI, AbadiFxIds.STOP, 0, 0.0F);
		CultivationData data = Cultivators.get(player);
		if (!player.isCreative() && !player.isSpectator()) {
			// Luka Kenaikan: set directly, the DESIGN 17 deviation immunity of an ascended True Immortal does not cover it.
			data.setDeviationTicks(Math.max(data.deviationTicks(), LUKA_TICKS));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 0, false, false, true));
			Cultivators.refreshVisual(player);
			Cultivators.syncNow(player);
		}
		if (!ritual.free) {
			Abadi.extra(player).putLong("asc_cd", Abadi.now(player.level().getServer()) + RETRY_TICKS);
		}
		Cultivators.dirty(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.6F);
		Msg.chat(player, Component.translatable("kultivator.abadi.ritual.fail", Component.translatable(reasonKey), LUKA_TICKS / 1200)
				.withStyle(ChatFormatting.RED));
		refundOffering(player, ritual);
	}

	/** DeathGuard {@code LANGIT}: a lethal blow during the ritual leaves 1 HP and fails it (never the passing phase). */
	private static boolean preventDeath(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
		Ritual ritual = RITUALS.get(player.getUUID());
		if (ritual == null || ritual.over) {
			return false;
		}
		player.setHealth(1.0F);
		player.setRemainingFireTicks(0);
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		if (ritual.phase != Phase.PASSING) {
			Abadi.schedule(1, () -> {
				Ritual current = RITUALS.get(player.getUUID());
				if (current == ritual) {
					fail(player, ritual, "kultivator.abadi.ritual.fail.strike");
				}
			});
		}
		return true;
	}

	// ================================================================== the Alam tablet

	/** The Alam tablet's data: the ascension card (gate, offering) or the Alam Abadi status. */
	static CompoundTag pageTag(ServerPlayer player) {
		CompoundTag page = new CompoundTag();
		MinecraftServer server = player.level().getServer();
		CultivationData data = Cultivators.get(player);
		boolean ascended = Abadi.ascended(player);
		page.putBoolean("ascended", ascended);
		page.putBoolean("enabled", KultivatorConfig.get().immortalRealm);
		page.putBoolean("in_realm", Abadi.isImmortalLevel(player.level()));
		page.putBoolean("ritual", active(player));
		page.putInt("conv", OriginStones.conv(player));
		page.putInt("stones", Abadi.stonesIn(player));
		page.putInt("mined", OriginStones.minedToday(player));
		page.putInt("mined_cap", OriginStones.MINE_DAY_CAP);
		page.putInt("conv_left", OriginStones.convertsLeft(player));
		page.putLong("cross_cd", Crossing.cooldownLeft(player));
		page.putBoolean("talisman", player.getInventory().countItem(AbadiItems.REALM_CROSSING_TALISMAN) > 0);
		page.putLong("asc_cd", cooldownLeft(player));
		page.putInt("discount", Math.min(OriginStones.STAR_STONES_MAX, Abadi.stonesIn(player)));
		ListTag gate = new ListTag();
		gate.add(line("kultivator.abadi.gate.peak", data.rank().isMax(), 0, 0));
		int stars = TechniquePower.playerStars(data);
		gate.add(line("kultivator.abadi.gate.stars", stars >= ImmortalPerks.GATE_I_STARS, stars, ImmortalPerks.GATE_I_STARS));
		gate.add(line("kultivator.abadi.gate.ascension", Ascension.isAscended(player), 0, 0));
		gate.add(line("kultivator.abadi.gate.essence", essenceHave(player) >= OFFER_ESSENCE, essenceHave(player), OFFER_ESSENCE));
		gate.add(line("kultivator.abadi.gate.stone", supremeHave(player) >= 1, supremeHave(player), 1));
		gate.add(line("kultivator.abadi.gate.cooldown", cooldownLeft(player) <= 0L, (int) Math.ceil(cooldownLeft(player) / 1200.0D), 0));
		page.put("gate", gate);
		if (!ascended) {
			Component why = blocker(player, false);
			page.putBoolean("ready", why == null);
			if (why != null) {
				page.store("why", ComponentSerialization.CODEC, why);
			}
		}
		page.putBoolean("fallback", ArrivalIsland.usesFallback(server));
		return page;
	}

	private static CompoundTag line(String key, boolean ok, int have, int need) {
		CompoundTag tag = new CompoundTag();
		tag.putString("key", key);
		tag.putBoolean("ok", ok);
		tag.putInt("have", have);
		tag.putInt("need", need);
		return tag;
	}

	private Ascend() {
	}
}
