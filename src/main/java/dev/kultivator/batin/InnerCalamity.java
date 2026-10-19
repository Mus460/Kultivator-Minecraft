package dev.kultivator.batin;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.musuh.RogueTargets;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bencana Bumi, the earthly calamities of the inner world (DESIGN 26.6.4): every {@code innerWorldCalamityDays} outside
 * days ("ten inner years") the next calamity is due; its type cycles Hujan Api Meteor → Banjir Besar → Bayangan Petir, each
 * cycle one strength higher. When the owner is online, active, not busy and stands on open ground it comes live for
 * {@link #DURATION} ticks around the player (entities only: meteors, flood spirits, the lightning shadow); otherwise it waits
 * up to one outside day and then resolves as data with {@link #surviveChance}. Losses touch only the inner world's data:
 * never inventory items, never blocks, never fewer than two usable plots. Peaceful: always the survive result.
 * <p>
 * Sumber: Reverend Insanity ch. 409 "Managing the Blessed Land" (a calamity every ten years; the 2nd a meteor fire rain that
 * ruined the east, the 4th a flood over the northern grassland, the 5th a human-shaped blue lightning shadow that avoids Dang
 * Hun Mountain, where its soul would be shocked to ash). The defences, timings and numbers are game design.
 */
public final class InnerCalamity {

	public static final String TAG = "kultivator.batin_calamity";
	public static final int METEOR = 0;
	public static final int FLOOD = 1;
	public static final int LIGHTNING = 2;
	public static final String[] TYPE_IDS = {"meteor", "banjir", "petir"};
	public static final int DURATION = 1800;
	public static final double LEAVE_RANGE = 48.0D;
	public static final int RING_SAMPLES = 16;
	public static final double RING_RADIUS = 10.0D;
	public static final double RING_NEED = 0.70D;
	public static final long ACTIVE_MILLIS = 60000L;
	public static final int METEOR_MAX_ALIVE = 16;
	public static final int METEOR_TOTAL_CAP = 40;
	public static final double METEOR_SPREAD = 12.0D;
	public static final double METEOR_BURST_RADIUS = 3.0D;
	public static final float METEOR_BURST_SHARE = 0.10F;
	public static final int METEOR_FIRE_TICKS = 100;
	public static final int METEOR_SPAWN_WINDOW = 1400;
	public static final int FLOOD_CHANNEL_TICKS = 1200;
	public static final double FLOOD_RADIUS = 4.0D;
	public static final double FLOOD_INTERRUPT_SHARE = 0.15D;
	public static final int FLOOD_INTERRUPT_TICKS = 40;
	public static final int FLOOD_WAVE_SIZE = 2;
	public static final int FLOOD_WAVES_CAP = 8;
	public static final int FOES_MAX = 6;
	public static final double DANG_HUN_DISTANCE = 8.0D;
	public static final double DANG_HUN_RADIUS = 5.0D;
	public static final float DANG_HUN_FACTOR = 2.0F;
	public static final double SHADOW_PER_N = 0.15D;
	public static final int WIN_PEARLS_PER_N = 5;
	public static final double WIN_FOXES = 0.10D;
	public static final double LIGHTNING_FOXES = 0.20D;
	public static final int LIGHTNING_PEARLS = 10;
	public static final double DATA_LOSS = 0.5D;

	/** One live defence around its owner (memory only; a restart ends it and the calamity is evaluated again later). */
	static final class Defence {
		final UUID owner;
		final int type;
		final int n;
		final Vec3 center;
		final ResourceKey<Level> dim;
		final long start;
		final long end;
		final ServerBossEvent bar;
		// meteor
		int total;
		int spawned;
		int landed;
		int destroyed;
		long nextMeteor;
		int interval;
		final List<BatinMeteorEntity> meteors = new ArrayList<>();
		// flood
		int channel;
		long interruptedUntil;
		int waves;
		int wavesSpawned;
		long nextWave;
		final List<Mob> foes = new ArrayList<>();
		// lightning
		@Nullable Mob shadow;
		Vec3 ring = Vec3.ZERO;

		Defence(UUID owner, int type, int n, Vec3 center, ResourceKey<Level> dim, long start, ServerBossEvent bar) {
			this.owner = owner;
			this.type = type;
			this.n = n;
			this.center = center;
			this.dim = dim;
			this.start = start;
			this.end = start + DURATION;
			this.bar = bar;
		}
	}

	private static final Map<UUID, Defence> DEFENCES = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(InnerCalamity::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Defence defence : DEFENCES.values()) {
				defence.bar.removeAllPlayers();
			}
			DEFENCES.clear();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			Defence defence = DEFENCES.get(player.getUUID());
			if (defence != null) {
				finish(player.level().getServer(), defence, player, false);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (victim instanceof ServerPlayer player) {
				Defence defence = DEFENCES.get(player.getUUID());
				if (defence != null) {
					finish(player.level().getServer(), defence, player, false);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			Defence defence = DEFENCES.get(player.getUUID());
			if (defence != null && defence.type == FLOOD && taken > player.getMaxHealth() * FLOOD_INTERRUPT_SHARE) {
				defence.interruptedUntil = player.level().getGameTime() + FLOOD_INTERRUPT_TICKS;
				Msg.actionBar(player, Component.translatable("kultivator.batin.cal.flood.interrupt").withStyle(ChatFormatting.RED));
			}
		});
		// Inside the Gunung Dang Hun ring the lightning shadow takes double damage.
		DamageHooks.register(DamageHooks.Stage.DR, (entity, source, amount) -> {
			if (DEFENCES.isEmpty() || !entity.entityTags().contains(TAG)) {
				return amount;
			}
			for (Defence defence : DEFENCES.values()) {
				if (defence.shadow == entity && entity.position().distanceToSqr(defence.ring) <= DANG_HUN_RADIUS * DANG_HUN_RADIUS) {
					return amount * DANG_HUN_FACTOR;
				}
			}
			return amount;
		});
	}

	public static boolean active(UUID owner) {
		return DEFENCES.containsKey(owner);
	}

	// ================================================================== schedule

	/** {@code P(survive)} of the data resolution: 0.30 + 0.10 per fortify step + 0.05 awake rockmen + 0.05 per grade, 0.30-0.90. */
	public static double surviveChance(CompoundTag batin) {
		int fortify = batin.getCompoundOrEmpty("cal").getIntOr("fortify", 0);
		boolean rockmen = batin.getCompoundOrEmpty("rock").getBooleanOr("awake", false);
		return Mth.clamp(0.30D + 0.10D * fortify + (rockmen ? 0.05D : 0.0D) + 0.05D * InnerWorld.grade(batin), 0.30D, 0.90D);
	}

	/** The type (0-2) of calamity number {@code n} (1-based). */
	public static int typeOf(int n) {
		return Math.floorMod(Math.max(1, n) - 1, 3);
	}

	/** The periodic pass (after the day catch-up): the warning, the live start or the data resolution. */
	static void check(ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		CultivationData data = Cultivators.get(player);
		if (!config.innerWorld || !InnerWorld.born(data) || active(player.getUUID())) {
			return;
		}
		int every = config.innerWorldCalamityDays;
		if (every <= 0) {
			return;
		}
		CompoundTag batin = InnerWorld.root(data);
		CompoundTag cal = InnerWorld.section(batin, "cal");
		long day = InnerWorld.day(player);
		long due = cal.getLongOr("due", -1L);
		if (due < 0L) {
			cal.putLong("due", day + every);
			Batin.dirty(player);
			return;
		}
		int n = Math.max(1, cal.getIntOr("n", 1));
		if (day >= due - 1L && day < due && !cal.getBooleanOr("warned", false)) {
			cal.putBoolean("warned", true);
			Msg.chat(player, LandSpirit.say(batin, Component.translatable("kultivator.batin.cal.warn", n,
					Component.translatable("kultivator.batin.cal.type." + TYPE_IDS[typeOf(n)]))));
			Batin.dirty(player);
		}
		if (day < due) {
			return;
		}
		if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
			resolveData(player, true);
			return;
		}
		if (canGoLive(player)) {
			if (startLive(player, typeOf(n), n)) {
				return;
			}
		}
		if (day >= due + 1L) {
			resolveData(player, false);
		}
	}

	/** Online, active (input in 60 s), not busy, survival, and ≥ 70% of a ring around the player is open ground. */
	static boolean canGoLive(ServerPlayer player) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || Batin.busy(player)
				|| Util.getMillis() - player.getLastActionTime() > ACTIVE_MILLIS) {
			return false;
		}
		ServerLevel level = player.level();
		int ok = 0;
		for (int i = 0; i < RING_SAMPLES; i++) {
			double angle = Math.PI * 2.0D * i / RING_SAMPLES;
			Vec3 point = player.position().add(Math.cos(angle) * RING_RADIUS, 0.0D, Math.sin(angle) * RING_RADIUS);
			if (level.isLoaded(BlockPos.containing(point)) && Targeting.safeSpot(level, player, point) != null) {
				ok++;
			}
		}
		return ok >= Math.ceil(RING_SAMPLES * RING_NEED);
	}

	// ================================================================== live

	/** Starts a live defence of {@code type} (strength {@code n}) around the player; false when it could not start. */
	static boolean startLive(ServerPlayer player, int type, int n) {
		if (active(player.getUUID())) {
			return false;
		}
		ServerLevel level = player.level();
		CompoundTag batin = InnerWorld.root(Cultivators.get(player));
		Component typeName = Component.translatable("kultivator.batin.cal.type." + TYPE_IDS[type]);
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.batin.cal.bar", n, typeName),
				type == METEOR ? BossEvent.BossBarColor.RED : type == FLOOD ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		Defence defence = new Defence(player.getUUID(), type, n, player.position(), level.dimension(), level.getGameTime(), bar);
		int realm = effectiveRealm(player);
		switch (type) {
			case METEOR -> {
				defence.total = Math.min(METEOR_TOTAL_CAP, 12 + 4 * n);
				defence.interval = Math.max(10, METEOR_SPAWN_WINDOW / defence.total);
				defence.nextMeteor = defence.start + 20L;
			}
			case FLOOD -> {
				defence.waves = Math.min(FLOOD_WAVES_CAP, 2 + n);
				defence.interval = Math.max(100, (DURATION - 200) / defence.waves);
				defence.nextWave = defence.start + 40L;
			}
			default -> {
				RandomSource random = level.getRandom();
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 spawnAt = defence.center.add(Math.cos(angle) * 10.0D, 0.0D, Math.sin(angle) * 10.0D);
				Mob shadow = RogueCultivators.spawn(level, spawnAt, 4, realm, RogueCultivators.VARIANT_ENERGY, RogueCultivators.ROLE_QUEST, null);
				if (shadow == null) {
					bar.removeAllPlayers();
					return false;
				}
				AttributeInstance health = shadow.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.addOrReplacePermanentModifier(new AttributeModifier(dev.kultivator.Kultivator.id("batin_calamity"),
							SHADOW_PER_N * Math.min(20, n), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
					shadow.setHealth(shadow.getMaxHealth());
				}
				shadow.addTag(TechniquePower.TAG_ABADI_FOE);
				shadow.addTag(TAG);
				shadow.setTarget(player);
				defence.shadow = shadow;
				double ringAngle = angle + Math.PI;
				Vec3 ringAt = defence.center.add(Math.cos(ringAngle) * DANG_HUN_DISTANCE, 0.0D, Math.sin(ringAngle) * DANG_HUN_DISTANCE);
				Vec3 spot = Targeting.safeSpot(level, player, ringAt);
				defence.ring = spot != null ? spot : ringAt;
			}
		}
		bar.addPlayer(player);
		DEFENCES.put(player.getUUID(), defence);
		EpicHooks.extra(level, EpicIds.EXTRA_INNER_CALAMITY, player, null, defence.center, type, n);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0F, 0.6F);
		Msg.chat(player, LandSpirit.say(batin, Component.translatable("kultivator.batin.cal.start." + TYPE_IDS[type], n)));
		Msg.chat(player, Component.translatable("kultivator.batin.cal.goal." + TYPE_IDS[type]).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** R_eff = 8 + floor(stars / 9) (DESIGN 26.2.2). */
	static int effectiveRealm(ServerPlayer player) {
		return 8 + TechniquePower.playerStars(Cultivators.get(player)) / 9;
	}

	private static void tick(MinecraftServer server) {
		if (DEFENCES.isEmpty()) {
			return;
		}
		for (Defence defence : List.copyOf(DEFENCES.values())) {
			try {
				tickDefence(server, defence);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: inner calamity tick failed", exception);
				finish(server, defence, server.getPlayerList().getPlayer(defence.owner), false);
			}
		}
	}

	private static void tickDefence(MinecraftServer server, Defence defence) {
		ServerPlayer player = server.getPlayerList().getPlayer(defence.owner);
		ServerLevel level = server.getLevel(defence.dim);
		if (player == null || level == null) {
			finish(server, defence, player, false);
			return;
		}
		if (player.level() != level || player.position().distanceToSqr(defence.center) > LEAVE_RANGE * LEAVE_RANGE) {
			Msg.chat(player, Component.translatable("kultivator.batin.cal.left").withStyle(ChatFormatting.RED));
			finish(server, defence, player, false);
			return;
		}
		long now = level.getGameTime();
		boolean second = (now - defence.start) % 20L == 0L;
		switch (defence.type) {
			case METEOR -> {
				for (Iterator<BatinMeteorEntity> it = defence.meteors.iterator(); it.hasNext(); ) {
					BatinMeteorEntity meteor = it.next();
					if (meteor.landed()) {
						defence.landed++;
						it.remove();
					} else if (meteor.isRemoved() || !meteor.isAlive()) {
						defence.destroyed++;
						it.remove();
					}
				}
				if (defence.spawned < defence.total && now >= defence.nextMeteor && defence.meteors.size() < METEOR_MAX_ALIVE) {
					spawnMeteor(level, player, defence);
					defence.nextMeteor = now + defence.interval;
				}
				defence.bar.setProgress(Mth.clamp(1.0F - (defence.landed + defence.destroyed) / (float) Math.max(1, defence.total), 0.0F, 1.0F));
				if (defence.spawned >= defence.total && defence.meteors.isEmpty()) {
					finish(server, defence, player, defence.landed * 4 < defence.total);
					return;
				}
			}
			case FLOOD -> {
				defence.foes.removeIf(mob -> mob.isRemoved() || !mob.isAlive());
				if (defence.wavesSpawned < defence.waves && now >= defence.nextWave) {
					spawnWave(level, player, defence);
					defence.wavesSpawned++;
					defence.nextWave = now + defence.interval;
				}
				double dx = player.getX() - defence.center.x;
				double dz = player.getZ() - defence.center.z;
				if (dx * dx + dz * dz <= FLOOD_RADIUS * FLOOD_RADIUS && Math.abs(player.getY() - defence.center.y) <= 3.0D && now >= defence.interruptedUntil) {
					defence.channel++;
				}
				defence.bar.setProgress(Mth.clamp(defence.channel / (float) FLOOD_CHANNEL_TICKS, 0.0F, 1.0F));
				if (second) {
					int progress = Math.round(100.0F * defence.channel / FLOOD_CHANNEL_TICKS);
					Fx.send(level, defence.center, FxType.BATIN, BatinFxIds.TANGGUL, progress, (float) FLOOD_RADIUS);
					Msg.actionBar(player, Component.translatable("kultivator.batin.cal.flood.progress", progress).withStyle(ChatFormatting.AQUA));
				}
				if (defence.channel >= FLOOD_CHANNEL_TICKS) {
					finish(server, defence, player, true);
					return;
				}
			}
			default -> {
				Mob shadow = defence.shadow;
				if (shadow == null) {
					finish(server, defence, player, false);
					return;
				}
				if (!shadow.isAlive()) {
					boolean slain = shadow.isDeadOrDying() || shadow.getHealth() <= 0.0F;
					finish(server, defence, player, slain);
					return;
				}
				defence.bar.setProgress(Mth.clamp(shadow.getHealth() / Math.max(1.0F, shadow.getMaxHealth()), 0.0F, 1.0F));
				if (second) {
					Fx.send(level, defence.ring, FxType.BATIN, BatinFxIds.DANG_HUN, 0, (float) DANG_HUN_RADIUS);
					Fx.send(level, shadow.position(), FxType.BATIN, BatinFxIds.BOLT, 0, 1.0F);
					if (shadow.position().distanceToSqr(defence.ring) <= DANG_HUN_RADIUS * DANG_HUN_RADIUS) {
						shadow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
					}
				}
			}
		}
		if (now >= defence.end) {
			// Time is up: the flood was not held back, the shadow still walks, unspawned meteors would have landed.
			if (defence.type == METEOR) {
				defence.landed += defence.total - defence.landed - defence.destroyed;
			}
			finish(server, defence, player, defence.type == METEOR && defence.landed * 4 < defence.total);
		}
	}

	private static void spawnMeteor(ServerLevel level, ServerPlayer player, Defence defence) {
		BatinMeteorEntity meteor = BatinItems.METEOR.create(level, EntitySpawnReason.EVENT);
		defence.spawned++;
		if (meteor == null) {
			defence.destroyed++;
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = random.nextDouble() * METEOR_SPREAD;
		Vec3 candidate = defence.center.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
		// Every third meteor aims at the player's feet, so standing still is never safe.
		if (defence.spawned % 3 == 0) {
			candidate = player.position();
		}
		Vec3 spot = level.isLoaded(BlockPos.containing(candidate)) ? Targeting.safeSpot(level, meteor, candidate) : null;
		Vec3 landing = spot != null ? spot : defence.center;
		double health = BatinMeteorEntity.BASE_HEALTH * (1.0D + 0.35D * effectiveRealm(player));
		meteor.setup(landing, health, player.getUUID());
		if (!level.addFreshEntity(meteor)) {
			defence.destroyed++;
			return;
		}
		defence.meteors.add(meteor);
		Fx.send(level, landing, FxType.BATIN, BatinFxIds.METEOR_MARK, 0, BatinMeteorEntity.FALL_TICKS / 20.0F);
	}

	/** A meteor reached the ground: a 3-block burst of 10% max HP through {@code mobAttack}, entity fire; no block changes. */
	static void meteorLanded(ServerLevel level, BatinMeteorEntity meteor) {
		Vec3 at = meteor.position();
		AABB box = new AABB(at, at).inflate(METEOR_BURST_RADIUS);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != meteor && entity.isAlive())) {
			if (target.position().distanceToSqr(at) > METEOR_BURST_RADIUS * METEOR_BURST_RADIUS || !RogueTargets.mayHit(meteor, target)) {
				continue;
			}
			target.hurtServer(level, meteor.damageSources().mobAttack(meteor), target.getMaxHealth() * METEOR_BURST_SHARE);
			target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), METEOR_FIRE_TICKS));
		}
		Fx.send(level, at, FxType.BATIN, BatinFxIds.METEOR_BURST, 0, (float) METEOR_BURST_RADIUS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.6F, 0.6F);
	}

	private static void spawnWave(ServerLevel level, ServerPlayer player, Defence defence) {
		RandomSource random = level.getRandom();
		int realm = effectiveRealm(player);
		for (int i = 0; i < FLOOD_WAVE_SIZE && defence.foes.size() < FOES_MAX; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 at = defence.center.add(Math.cos(angle) * 12.0D, 0.0D, Math.sin(angle) * 12.0D);
			Mob foe = RogueCultivators.spawn(level, at, 2, realm, RogueCultivators.VARIANT_ENERGY, RogueCultivators.ROLE_QUEST, null);
			if (foe == null) {
				continue;
			}
			foe.addTag(TechniquePower.TAG_ABADI_FOE);
			foe.addTag(TAG);
			foe.setTarget(player);
			defence.foes.add(foe);
		}
		level.playSound(null, defence.center.x, defence.center.y, defence.center.z, SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 1.4F, 0.5F);
	}

	/** Ends a live defence: removes what is left, then the win or the loss (the full live loss). */
	private static void finish(@Nullable MinecraftServer server, Defence defence, @Nullable ServerPlayer player, boolean won) {
		if (DEFENCES.remove(defence.owner) == null) {
			return;
		}
		defence.bar.removeAllPlayers();
		for (BatinMeteorEntity meteor : defence.meteors) {
			if (!meteor.isRemoved()) {
				meteor.discard();
			}
		}
		for (Mob foe : defence.foes) {
			vanish(foe);
		}
		if (defence.shadow != null && defence.shadow.isAlive()) {
			vanish(defence.shadow);
		}
		if (player == null) {
			// Offline at the end: the calamity is judged again as data on the next pass.
			return;
		}
		CompoundTag batin = InnerWorld.root(Cultivators.get(player));
		if (won) {
			win(player, batin, defence.n, defence.type);
			if (defence.type == METEOR && defence.landed > 0) {
				int ruined = ruinPlots(player, batin, defence.landed / 4, InnerWorld.STATE_BURNED, true);
				if (ruined > 0) {
					Msg.chat(player, Component.translatable("kultivator.batin.cal.loss.meteor", ruined).withStyle(ChatFormatting.YELLOW));
				}
			}
		} else {
			double share = defence.type == FLOOD ? 1.0D - Math.min(1.0D, defence.channel / (double) FLOOD_CHANNEL_TICKS) : 1.0D;
			int landed = defence.type == METEOR ? Math.max(defence.landed, defence.total - defence.destroyed) : 0;
			lose(player, batin, defence.type, defence.n, 1.0D, landed, share);
		}
		next(player, batin);
	}

	private static void vanish(Mob mob) {
		if (mob.isRemoved()) {
			return;
		}
		if (mob instanceof RogueCultivatorEntity rogue) {
			rogue.vanish(false);
		} else {
			mob.discard();
		}
	}

	// ================================================================== outcomes

	/** The data resolution: survive with {@link #surviveChance} (always when {@code kind}), else half of the live loss. */
	static void resolveData(ServerPlayer player, boolean kind) {
		CultivationData data = Cultivators.get(player);
		if (!InnerWorld.born(data)) {
			return;
		}
		CompoundTag batin = InnerWorld.root(data);
		CompoundTag cal = InnerWorld.section(batin, "cal");
		int n = Math.max(1, cal.getIntOr("n", 1));
		int type = typeOf(n);
		boolean survive = kind || player.getRandom().nextDouble() < surviveChance(batin);
		EpicHooks.extra(player.level(), EpicIds.EXTRA_INNER_CALAMITY, player, null, player.position(), type, n);
		Msg.chat(player, LandSpirit.say(batin, Component.translatable("kultivator.batin.cal.data", n,
				Component.translatable("kultivator.batin.cal.type." + TYPE_IDS[type]))));
		if (survive) {
			win(player, batin, n, type);
		} else {
			int total = Math.min(METEOR_TOTAL_CAP, 12 + 4 * n);
			lose(player, batin, type, n, DATA_LOSS, total, 1.0D);
		}
		next(player, batin);
	}

	/** The win: 5 × n pearls, foxes +10%, a land-spirit line, {@code batin_calamity}. */
	private static void win(ServerPlayer player, CompoundTag batin, int n, int type) {
		int pearls = Math.min(InnerWorld.PEARL_CAP, batin.getIntOr("pearls", 0) + WIN_PEARLS_PER_N * n);
		int foxes = Math.min(InnerWorld.foxCap(batin), (int) Math.round(batin.getIntOr("foxes", 0) * (1.0D + WIN_FOXES)));
		batin.putInt("pearls", pearls);
		batin.putInt("foxes", foxes);
		LandSpirit.log(batin, InnerWorld.day(player), "cal_win", n, type);
		Advancements.grant(player, "batin_calamity");
		Fx.send(player.level(), player.position(), FxType.BATIN, BatinFxIds.CALAMITY_WIN, type, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F);
		Msg.chat(player, LandSpirit.say(batin, Component.translatable("kultivator.batin.cal.win", n, WIN_PEARLS_PER_N * n)));
	}

	/**
	 * A loss at {@code factor} (1 live, 0.5 data): meteors burn {@code landed / 4} plots, the flood soaks {@code share} of the
	 * plots (herb age −30%), the lightning shadow takes 20% of the foxes, 10 pearls and scorches one plot.
	 */
	private static void lose(ServerPlayer player, CompoundTag batin, int type, int n, double factor, int landed, double share) {
		ListTag plots = InnerWorld.list(batin, "plots");
		Component line;
		switch (type) {
			case METEOR -> {
				int ruined = ruinPlots(player, batin, (int) Math.floor(landed / 4.0D * factor), InnerWorld.STATE_BURNED, true);
				line = Component.translatable("kultivator.batin.cal.loss.meteor", ruined);
			}
			case FLOOD -> {
				int soaked = floodPlots(player, plots, (int) Math.round(plots.size() * share * factor));
				line = Component.translatable("kultivator.batin.cal.loss.banjir", soaked);
			}
			default -> {
				int foxes = batin.getIntOr("foxes", 0);
				int lostFoxes = (int) Math.round(foxes * LIGHTNING_FOXES * factor);
				int lostPearls = (int) Math.round(LIGHTNING_PEARLS * factor);
				batin.putInt("foxes", Math.max(0, foxes - lostFoxes));
				batin.putInt("pearls", Math.max(0, batin.getIntOr("pearls", 0) - lostPearls));
				int scorched = factor >= 1.0D ? ruinPlots(player, batin, 1, InnerWorld.STATE_SCORCHED, true) : 0;
				line = Component.translatable("kultivator.batin.cal.loss.petir", lostFoxes, lostPearls, scorched);
			}
		}
		LandSpirit.log(batin, InnerWorld.day(player), "cal_loss", n, type);
		Msg.chat(player, LandSpirit.say(batin, Component.translatable("kultivator.batin.cal.lost", n)));
		Msg.chat(player, line.copy().withStyle(ChatFormatting.YELLOW));
	}

	/** Ruins up to {@code count} plots (herbs destroyed, unusable for 3 days), never below two usable plots. */
	private static int ruinPlots(ServerPlayer player, CompoundTag batin, int count, int state, boolean preferHerbs) {
		ListTag plots = InnerWorld.list(batin, "plots");
		long until = InnerWorld.day(player) + InnerWorld.RUIN_DAYS;
		RandomSource random = player.getRandom();
		int ruined = 0;
		for (int pass = 0; pass < 2 && ruined < count; pass++) {
			List<Integer> candidates = new ArrayList<>();
			for (int i = 0; i < plots.size(); i++) {
				CompoundTag plot = plots.getCompoundOrEmpty(i);
				int s = plot.getIntOr("s", InnerWorld.STATE_OK);
				boolean usable = s != InnerWorld.STATE_BURNED && s != InnerWorld.STATE_SCORCHED;
				boolean herb = !plot.getStringOr("k", "").isEmpty();
				if (usable && (pass == 1 || !preferHerbs || herb)) {
					candidates.add(i);
				}
			}
			while (ruined < count && !candidates.isEmpty() && InnerWorld.usablePlots(plots) > InnerWorld.MIN_USABLE) {
				int index = candidates.remove(random.nextInt(candidates.size()));
				CompoundTag ruinedPlot = new CompoundTag();
				ruinedPlot.putInt("s", state);
				ruinedPlot.putLong("u", until);
				plots.set(index, ruinedPlot);
				ruined++;
			}
		}
		return ruined;
	}

	/** Soaks up to {@code count} plots: herb age −30% (never below 1), marked Terendam for a day. */
	private static int floodPlots(ServerPlayer player, ListTag plots, int count) {
		long until = InnerWorld.day(player) + 1L;
		int soaked = 0;
		for (int i = 0; i < plots.size() && soaked < count; i++) {
			CompoundTag plot = plots.getCompoundOrEmpty(i);
			int s = plot.getIntOr("s", InnerWorld.STATE_OK);
			if (s == InnerWorld.STATE_BURNED || s == InnerWorld.STATE_SCORCHED) {
				continue;
			}
			if (!plot.getStringOr("k", "").isEmpty()) {
				int age = plot.getIntOr("a", 1);
				plot.putInt("a", Math.max(1, (int) Math.floor(age * 0.7D)));
				plot.putInt("p", Math.min(plot.getIntOr("p", age), plot.getIntOr("a", 1)));
			}
			plot.putInt("s", InnerWorld.STATE_FLOODED);
			plot.putLong("u", until);
			soaked++;
		}
		return soaked;
	}

	/** The next calamity: n + 1, due after {@code innerWorldCalamityDays}, Perkokoh used up. */
	private static void next(ServerPlayer player, CompoundTag batin) {
		CompoundTag cal = InnerWorld.section(batin, "cal");
		int every = KultivatorConfig.get().innerWorldCalamityDays;
		cal.putInt("n", Math.max(1, cal.getIntOr("n", 1)) + 1);
		cal.putLong("due", every <= 0 ? -1L : InnerWorld.day(player) + every);
		cal.putInt("fortify", 0);
		cal.putBoolean("warned", false);
		Cultivators.dirty(player);
		Batin.dirty(player);
	}

	// ================================================================== tests and reads

	/** {@code uji batin bencana <type> [n]}: a live defence now around the player (no conditions but not twice). */
	static boolean forceLive(ServerPlayer player, int type, int n) {
		return startLive(player, Math.max(0, Math.min(2, type)), Math.max(1, n));
	}

	/** Remaining ticks of the live defence, or -1. */
	static int ticksLeft(ServerPlayer player) {
		Defence defence = DEFENCES.get(player.getUUID());
		return defence == null ? -1 : (int) Math.max(0L, defence.end - player.level().getGameTime());
	}

	/** The live defence's type, or -1. */
	static int liveType(UUID owner) {
		Defence defence = DEFENCES.get(owner);
		return defence == null ? -1 : defence.type;
	}

	private InnerCalamity() {
	}
}
