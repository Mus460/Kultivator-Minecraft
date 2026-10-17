package dev.kultivator.trial;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sparring;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageEstimate;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Heart demon trials (DESIGN sections 10 and 16). A dark copy of the cultivator ({@link HeartDemonEntity}) appears:
 * <ul>
 *   <li>as the trial before the breakthroughs into Nascent Soul, Dao Union and True Immortal (registered with
 *   {@link Breakthrough#setTrialProvider}): 120 s, won = the tribulation omen starts, lost = the breakthrough fails;</li>
 *   <li>when the heart demon pressure (failed breakthroughs, pill toxicity, many kills) is above 100: a 2% chance per
 *   minute, warned 10 s before and appearing once the cultivator stands on firm ground; 120 s, won = 25% of the bar as
 *   XP, lost = a Qi Deviation (severity 0.6) and pressure -30.</li>
 * </ul>
 * A cultivator below half health is brought up to half when the demon appears. Health falling below 20% of the max
 * (absorption included) loses the trial: the demon's blows and harm without an attacker (a fall or lava after its
 * knockback) that could do it are cancelled and lose the trial on the spot, anything else is checked every tick. Winning
 * always gives +0.1 on the next breakthrough's quality. Dying, logging out or changing dimension also loses it. The
 * demon is cleaned up in every case.
 */
public final class HeartDemons {

	/** Sub-compound of {@link CultivationData#extra()} owned by this module. */
	private static final String EXTRA_KEY = "trial";
	/** A pressure trial was abandoned (logout / death): the loss is applied when the cultivator is back on their feet. */
	private static final String PENDING_LOSS = "pending_loss";

	public static final int BREAKTHROUGH_TICKS = 20 * 120;
	public static final int PRESSURE_TICKS = 20 * 120;
	private static final int WARNING_TICKS = 200;
	/** Health below this share of the max loses the trial (the demon may never push its owner below it). */
	private static final float LOSE_FRACTION = 0.2F;
	/** A cultivator below this share of their max health is healed up to it when the demon appears. */
	private static final float START_HEALTH = 0.5F;
	/** Kills add pressure: a monster 0.15, anything else 1.0, at most this much per minute. */
	private static final double KILL_PRESSURE_MONSTER = 0.15D;
	private static final double KILL_PRESSURE_OTHER = 1.0D;
	private static final double KILL_PRESSURE_PER_MINUTE = 6.0D;
	private static final double LEASH_DISTANCE = 24.0D;
	private static final double PRESSURE_THRESHOLD = 100.0D;
	private static final double PRESSURE_CHANCE_PER_MINUTE = 0.02D;
	private static final double TOXIC_THRESHOLD = 80.0D;
	private static final int TAUNT_COUNT = 12;
	/** A demon that vanished without being defeated (chunk unload, other mods) is brought back at most this often. */
	private static final int MAX_RESPAWNS = 3;

	private enum Kind { BREAKTHROUGH, PRESSURE }

	private enum Outcome { WON, OVERWHELMED, TIMEOUT, FLED, DIED, LEFT, CANCELLED }

	private static final class Trial {
		final UUID owner;
		final Kind kind;
		final @Nullable Rank target;
		final ResourceKey<Level> dimension;
		final int duration;
		final ServerBossEvent bar;
		@Nullable HeartDemonEntity demon;
		int ticks;
		int nextTaunt = 60;
		int lastTaunt = -1;
		int respawns;
		/** Decided inside the damage event; applied on the next tick (never inside another entity's hurt call). */
		@Nullable Outcome pending;
		/** Where the owner last stood on the ground (put back there if the demon throws them into the void). */
		Vec3 lastGround;

		Trial(UUID owner, Kind kind, @Nullable Rank target, ResourceKey<Level> dimension, int duration, ServerBossEvent bar, Vec3 start) {
			this.owner = owner;
			this.kind = kind;
			this.target = target;
			this.dimension = dimension;
			this.duration = duration;
			this.bar = bar;
			this.lastGround = start;
		}
	}

	/** Owner UUID -> the running trial. */
	private static final Map<UUID, Trial> TRIALS = new HashMap<>();
	/** Owner UUID -> ticks until the announced pressure demon appears. */
	private static final Map<UUID, Integer> WARNINGS = new HashMap<>();
	/** Player UUID -> pressure their kills added this minute. */
	private static final Map<UUID, Double> KILL_PRESSURE = new HashMap<>();

	public static void init() {
		TrialEntities.init();
		Breakthrough.setTrialProvider(new Provider());
		// Someone else's heart demon is not a target for anyone's techniques.
		Targeting.addExclusion((caster, entity) -> entity instanceof HeartDemonEntity demon && !demon.isOwner(caster));
		// Never saved (noSave), and refused anyway should one ever come back from disk.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !(entity instanceof HeartDemonEntity));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HeartDemons::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HeartDemons::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onOwnerDeath(player);
			}
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(HeartDemons::onKill);
		ServerPlayerEvents.LEAVE.register(HeartDemons::onLeave);
		// Players leaving because the server stops settle their trials in onLeave first; this only drops what is left.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Trial trial : List.copyOf(TRIALS.values())) {
				removeQuietly(trial);
			}
			TRIALS.clear();
			WARNINGS.clear();
			KILL_PRESSURE.clear();
		});
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		Trial trial = TRIALS.get(player.getUUID());
		if (trial != null) {
			tickTrial(player, data, trial);
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying()) {
			return;
		}
		applyPendingLoss(player, data);
		tickWarning(player, data);
		tickPressure(player, data);
	}

	/** True while the player faces a heart demon (breakthrough or pressure trial). */
	public static boolean inTrial(ServerPlayer player) {
		return TRIALS.containsKey(player.getUUID());
	}

	/** True while the player's heart demon trial is the one before a breakthrough. */
	public static boolean inBreakthroughTrial(ServerPlayer player) {
		Trial trial = TRIALS.get(player.getUUID());
		return trial != null && trial.kind == Kind.BREAKTHROUGH;
	}

	/** True when {@code demon} is the demon of a running trial (anything else removes itself). */
	public static boolean isTracked(HeartDemonEntity demon) {
		UUID owner = demon.getOwnerUUID();
		Trial trial = owner == null ? null : TRIALS.get(owner);
		return trial != null && trial.demon == demon;
	}

	/** Ends the player's trial without a result (breakthrough trials cancel the breakthrough, no penalty). */
	public static boolean dispel(ServerPlayer player) {
		WARNINGS.remove(player.getUUID());
		Trial trial = TRIALS.get(player.getUUID());
		if (trial == null) {
			return false;
		}
		finish(player, Cultivators.get(player), trial, Outcome.CANCELLED);
		return true;
	}

	/** {@code debug heartdemon summon|pressure|clear} (each with an optional player). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		debug.then(Commands.literal("heartdemon")
				.then(Commands.literal("summon")
						.executes(ctx -> summonCommand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> summonCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("pressure")
						.executes(ctx -> showPressure(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 200.0D))
								.executes(ctx -> setPressure(ctx.getSource(), ctx.getSource().getPlayerOrException(),
										DoubleArgumentType.getDouble(ctx, "value")))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> setPressure(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
												DoubleArgumentType.getDouble(ctx, "value"))))))
				.then(Commands.literal("clear")
						.executes(ctx -> clearCommand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> clearCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
	}

	// ================================================================== breakthrough trial

	/** The trial before the breakthroughs into Nascent Soul, Dao Union and True Immortal, once per realm. */
	private static final class Provider implements Breakthrough.TrialProvider {
		@Override
		public boolean needsTrial(ServerPlayer player, Rank target) {
			Realm realm = target.realm();
			return KultivatorConfig.get().heartDemonTrials
					&& (realm == Realm.NASCENT_SOUL || realm == Realm.DAO_UNION || realm == Realm.TRUE_IMMORTAL)
					&& target.layer() == 0
					&& !Cultivators.get(player).trialPassed(realm);
		}

		@Override
		public void startTrial(ServerPlayer player, Rank target) {
			CultivationData data = Cultivators.get(player);
			WARNINGS.remove(player.getUUID());
			Trial old = TRIALS.remove(player.getUUID());
			if (old != null) {
				// A pressure demon still around: it melts into the greater trial.
				removeQuietly(old);
			}
			// Qualified: this class's own startTrial(player, target) would hide the outer overload.
			if (!HeartDemons.startTrial(player, data, Kind.BREAKTHROUGH, target)) {
				Kultivator.LOGGER.warn("Could not summon the heart demon of {}; the trial is skipped", player.getName().getString());
				Breakthrough.onTrialFinished(player, true);
			}
		}
	}

	// ================================================================== pressure

	private static void tickPressure(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 1200 == 600) {
			KILL_PRESSURE.remove(player.getUUID());
			if (data.toxicity() > TOXIC_THRESHOLD) {
				data.setHeartDemonPressure(data.heartDemonPressure() + 2.0D);
				Cultivators.dirty(player);
			}
			if (data.heartDemonPressure() > PRESSURE_THRESHOLD && !WARNINGS.containsKey(player.getUUID()) && canFace(player, data)
					&& player.getRandom().nextDouble() < PRESSURE_CHANCE_PER_MINUTE) {
				warn(player);
			}
		}
		if (player.tickCount % 6000 == 3000 && data.heartDemonPressure() > 0.0D) {
			data.setHeartDemonPressure(data.heartDemonPressure() - 1.0D);
			Cultivators.dirty(player);
		}
	}

	/**
	 * A pressure demon may come: not busy, not in a duel (countdown included), not in Creative/Spectator, alive, and the
	 * feature is on.
	 */
	private static boolean canFace(ServerPlayer player, CultivationData data) {
		return KultivatorConfig.get().heartDemonTrials && player.isAlive() && !player.isDeadOrDying() && !player.isSpectator()
				&& !player.isCreative() && !Breakthrough.isBusy(player) && !data.breakingThrough() && !Sparring.inDuel(player)
				&& !TRIALS.containsKey(player.getUUID()) && !hasPendingLoss(data);
	}

	/** On firm ground: not riding, swimming, gliding or on the Flying Sword (the demon would appear in the air or water). */
	private static boolean standing(ServerPlayer player, CultivationData data) {
		return player.onGround() && !player.isPassenger() && !player.isInWater() && !player.isFallFlying() && !data.flyingSword();
	}

	/** Kills weigh on the heart: +0.15 pressure for a monster, +1 for anything else, at most +6 per minute. */
	private static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (!(killer instanceof ServerPlayer player) || player instanceof FakePlayer || killed == player
				|| killed instanceof HeartDemonEntity) {
			return;
		}
		double amount = killed instanceof Enemy ? KILL_PRESSURE_MONSTER : KILL_PRESSURE_OTHER;
		double minute = KILL_PRESSURE.getOrDefault(player.getUUID(), 0.0D);
		if (minute >= KILL_PRESSURE_PER_MINUTE) {
			return;
		}
		KILL_PRESSURE.put(player.getUUID(), minute + amount);
		CultivationData data = Cultivators.get(player);
		data.setHeartDemonPressure(data.heartDemonPressure() + amount);
		Cultivators.dirty(player);
	}

	private static void warn(ServerPlayer player) {
		WARNINGS.put(player.getUUID(), WARNING_TICKS);
		Msg.chat(player, Component.translatable("kultivator.trial.warning").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		Fx.sendTo(player, player.position(), player.position(), FxType.HEART_DEMON_APPEAR, TrialFxIds.WARNING, player.getId(), WARNING_TICKS);
		heartbeat(player, 1.0F);
	}

	private static void tickWarning(ServerPlayer player, CultivationData data) {
		Integer left = WARNINGS.get(player.getUUID());
		if (left == null) {
			return;
		}
		int now = left - 1;
		if (now == 120 || now == 60 || now == 20) {
			heartbeat(player, 1.0F + (120 - now) / 120.0F * 0.4F);
		}
		if (now > 0) {
			WARNINGS.put(player.getUUID(), now);
			return;
		}
		if (!canFace(player, data)) {
			// Busy now (sparring, a breakthrough, Creative...): the warning fades.
			WARNINGS.remove(player.getUUID());
			return;
		}
		if (!standing(player, data)) {
			// Riding, swimming or flying: it waits until the cultivator lands, checking every second.
			WARNINGS.put(player.getUUID(), 20);
			return;
		}
		WARNINGS.remove(player.getUUID());
		startTrial(player, data, Kind.PRESSURE, null);
	}

	private static void heartbeat(ServerPlayer player, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
				1.0F, pitch);
	}

	// ================================================================== the trial

	/** Summons the demon and starts the trial. False when the demon could not appear. */
	private static boolean startTrial(ServerPlayer player, CultivationData data, Kind kind, @Nullable Rank target) {
		if (data.meditating()) {
			Meditation.stop(player);
		}
		HeartDemonEntity demon = spawnDemon(player, 1.0F);
		if (demon == null) {
			return false;
		}
		if (player.getHealth() < player.getMaxHealth() * START_HEALTH) {
			// The fights that filled the bar must not lose the trial on the first touch (below 20% loses).
			player.setHealth(player.getMaxHealth() * START_HEALTH);
		}
		int duration = kind == Kind.BREAKTHROUGH ? BREAKTHROUGH_TICKS : PRESSURE_TICKS;
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), barName(player, duration), BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		bar.setDarkenScreen(true);
		bar.setProgress(1.0F);
		bar.addPlayer(player);
		Trial trial = new Trial(player.getUUID(), kind, target, player.level().dimension(), duration, bar, player.position());
		trial.demon = demon;
		TRIALS.put(player.getUUID(), trial);

		ServerLevel level = player.level();
		Vec3 pos = demon.position();
		Fx.send(level, pos, pos.add(0.0D, 6.0D, 0.0D), FxType.HEART_DEMON_APPEAR, TrialFxIds.APPEAR, demon.getId(), 0.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6F, 0.5F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8F, 1.4F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.5F);

		boolean breakthrough = kind == Kind.BREAKTHROUGH;
		Msg.title(player, Component.translatable("kultivator.trial.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.translatable(breakthrough ? "kultivator.trial.subtitle.breakthrough" : "kultivator.trial.subtitle.pressure")
						.withStyle(ChatFormatting.DARK_PURPLE), 10, 60, 20);
		Msg.chat(player, Component.translatable(breakthrough ? "kultivator.trial.start.breakthrough" : "kultivator.trial.start.pressure",
				Component.literal(String.valueOf(duration / 20)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.LIGHT_PURPLE));
		Advancements.grant(player, "heart_demon_faced");
		return true;
	}

	/** Creates the owner's demon 4-6 blocks away on a safe spot (right next to them when there is none) and adds it. */
	private static @Nullable HeartDemonEntity spawnDemon(ServerPlayer player, float healthFraction) {
		ServerLevel level = player.level();
		HeartDemonEntity demon = TrialEntities.HEART_DEMON.create(level, EntitySpawnReason.EVENT);
		if (demon == null) {
			return null;
		}
		demon.setup(player, healthFraction);
		Vec3 spot = findSpot(level, player, demon);
		double dx = player.getX() - spot.x;
		double dz = player.getZ() - spot.z;
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
		demon.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		demon.setYHeadRot(yaw);
		demon.setYBodyRot(yaw);
		if (!level.addFreshEntity(demon)) {
			return null;
		}
		demon.setTarget(player);
		return demon;
	}

	/** Read-only search for a free standing spot 4-6 blocks from the player. */
	private static Vec3 findSpot(ServerLevel level, ServerPlayer player, HeartDemonEntity demon) {
		Vec3 center = player.position();
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = 4.0D + player.getRandom().nextDouble() * 2.0D;
			Vec3 candidate = center.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
			if (!level.isLoaded(BlockPos.containing(candidate))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, demon, candidate);
			if (spot != null && Math.abs(spot.y - center.y) <= 3.0D) {
				return spot;
			}
		}
		Vec3 beside = Targeting.safeSpot(level, demon, center.add(player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize()));
		return beside != null ? beside : center;
	}

	private static void tickTrial(ServerPlayer player, CultivationData data, Trial trial) {
		trial.ticks++;
		HeartDemonEntity demon = trial.demon;
		if (demon != null && demon.killedByOwner()) {
			finish(player, data, trial, Outcome.WON);
			return;
		}
		if (trial.pending != null) {
			finish(player, data, trial, trial.pending);
			return;
		}
		if (!player.isAlive() || player.isDeadOrDying()) {
			// The death event decides.
			return;
		}
		if (trial.kind == Kind.BREAKTHROUGH && !Breakthrough.isBusy(player)) {
			// The breakthrough was called off elsewhere (command, its own timeout).
			finish(player, data, trial, Outcome.CANCELLED);
			return;
		}
		if (trial.kind == Kind.PRESSURE && (Breakthrough.isBusy(player) || data.breakingThrough())) {
			// A breakthrough started anyway (only debug or test paths can): the pressure demon gives way to it.
			finish(player, data, trial, Outcome.CANCELLED);
			return;
		}
		if (!player.level().dimension().equals(trial.dimension)) {
			finish(player, data, trial, Outcome.FLED);
			return;
		}
		if (player.getY() < player.level().getMinY()) {
			// Thrown off an edge by the demon's dash or blow: caught long before the void hurts (it starts 64 blocks below
			// the world), put back where they last stood, and the heart gives way (never lethal).
			ServerLevel level = player.level();
			Vec3 spot = Targeting.safeSpot(level, player, trial.lastGround);
			Vec3 to = spot != null ? spot : trial.lastGround;
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			player.teleportTo(level, to.x, to.y, to.z, Relative.ROTATION, 0.0F, 0.0F, false);
			player.resetFallDistance();
			finish(player, data, trial, Outcome.OVERWHELMED);
			return;
		}
		if (player.onGround()) {
			trial.lastGround = player.position();
		}
		if (player.getHealth() + player.getAbsorptionAmount() < player.getMaxHealth() * LOSE_FRACTION) {
			// Below 20% from anything (other mobs, fire, hunger...): the heart gives way.
			finish(player, data, trial, Outcome.OVERWHELMED);
			return;
		}
		if (demon == null || demon.isRemoved() || demon.isDeadOrDying()) {
			if (demon != null && demon.isDeadOrDying()) {
				// Killed by something that is not its owner (only /kill can): no result.
				finish(player, data, trial, Outcome.CANCELLED);
				return;
			}
			float fraction = demon == null ? 1.0F : demon.getHealth() / Math.max(1.0F, demon.getMaxHealth());
			HeartDemonEntity again = trial.respawns < MAX_RESPAWNS ? spawnDemon(player, fraction) : null;
			if (again == null) {
				finish(player, data, trial, Outcome.CANCELLED);
				return;
			}
			trial.respawns++;
			trial.demon = again;
			demon = again;
			Fx.send(player.level(), again.position(), again.position().add(0.0D, 6.0D, 0.0D), FxType.HEART_DEMON_APPEAR, TrialFxIds.APPEAR,
					again.getId(), 0.0F);
		}
		if (trial.ticks >= trial.duration) {
			finish(player, data, trial, Outcome.TIMEOUT);
			return;
		}
		if (trial.ticks % 10 == 0 && (demon.distanceToSqr(player) > LEASH_DISTANCE * LEASH_DISTANCE
				|| demon.getY() < player.level().getMinY() + 1 || demon.isInLava())) {
			demon.blinkTo(player);
		}
		if (trial.ticks % 5 == 0) {
			trial.bar.setProgress(Mth.clamp(demon.getHealth() / Math.max(1.0F, demon.getMaxHealth()), 0.0F, 1.0F));
		}
		if (trial.ticks % 20 == 0) {
			trial.bar.setName(barName(player, trial.duration - trial.ticks));
		}
		if (trial.ticks >= trial.nextTaunt) {
			taunt(player, data, trial, demon);
			trial.nextTaunt = trial.ticks + 260 + player.getRandom().nextInt(81);
		}
	}

	private static Component barName(ServerPlayer player, int ticksLeft) {
		return Component.translatable("kultivator.trial.bossbar", player.getName(), Math.max(0, (ticksLeft + 19) / 20));
	}

	/** A taunt line in chat (translated); some name the player, their failed breakthroughs or the realm. */
	private static void taunt(ServerPlayer player, CultivationData data, Trial trial, HeartDemonEntity demon) {
		List<Integer> choices = new ArrayList<>();
		for (int i = 0; i < TAUNT_COUNT; i++) {
			if (i != trial.lastTaunt && (i != 2 || data.failedBreakthroughs() > 0)) {
				choices.add(i);
			}
		}
		int index = choices.get(player.getRandom().nextInt(choices.size()));
		trial.lastTaunt = index;
		String key = "kultivator.trial.taunt." + index;
		Realm realm = trial.target != null ? trial.target.realm() : data.realm();
		Component line = switch (index) {
			case 0, 6, 10 -> Component.translatable(key, player.getName());
			case 2 -> Component.translatable(key, data.failedBreakthroughs());
			case 3 -> Component.translatable(key, realm.displayName().copy().withColor(realm.color()));
			default -> Component.translatable(key);
		};
		player.sendSystemMessage(Component.translatable("kultivator.trial.taunt", line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		player.level().playSound(null, demon.getX(), demon.getY(), demon.getZ(), SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 0.7F, 0.5F);
	}

	// ================================================================== results

	private static void finish(ServerPlayer player, CultivationData data, Trial trial, Outcome outcome) {
		TRIALS.remove(trial.owner);
		trial.bar.removeAllPlayers();
		HeartDemonEntity demon = trial.demon;
		trial.demon = null;
		ServerLevel fxLevel = demon != null && demon.level() instanceof ServerLevel demonLevel ? demonLevel : player.level();
		Vec3 at = demon != null ? demon.position().add(0.0D, 1.0D, 0.0D) : player.position().add(0.0D, 1.0D, 0.0D);
		Vec3 heart = player.position().add(0.0D, 1.1D, 0.0D);
		if (demon != null && !demon.isRemoved()) {
			// Discarding drops nothing and gives no experience.
			demon.discard();
		}
		switch (outcome) {
			case WON -> won(player, data, trial, fxLevel, at, heart);
			case OVERWHELMED, TIMEOUT, FLED -> lost(player, data, trial, outcome, fxLevel, at, heart);
			case DIED -> {
				Fx.send(fxLevel, at, at, FxType.HEART_DEMON_DEFEAT, TrialFxIds.DEFEAT_DISSOLVED, player.getId(), 0.0F);
				Advancements.grant(player, "heart_demon_lost");
				if (trial.kind == Kind.PRESSURE) {
					// Breakthrough trials: Breakthrough records the failure for after the respawn itself.
					setPendingLoss(data, true);
					Msg.chat(player, Component.translatable("kultivator.trial.died").withStyle(ChatFormatting.DARK_RED));
				}
			}
			case LEFT -> {
				Fx.send(fxLevel, at, at, FxType.HEART_DEMON_DEFEAT, TrialFxIds.DEFEAT_DISSOLVED, player.getId(), 0.0F);
				if (trial.kind == Kind.PRESSURE) {
					setPendingLoss(data, true);
				}
			}
			case CANCELLED -> {
				Fx.send(fxLevel, at, at, FxType.HEART_DEMON_DEFEAT, TrialFxIds.DEFEAT_DISSOLVED, player.getId(), 0.0F);
				fxLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 0.8F);
				Msg.chat(player, Component.translatable("kultivator.trial.cleared").withStyle(ChatFormatting.GRAY));
				if (trial.kind == Kind.BREAKTHROUGH && Breakthrough.isBusy(player)) {
					Breakthrough.cancel(player);
				}
			}
		}
		Cultivators.dirty(player);
	}

	/** "Hati Dao Teguh": the shadow shatters into the owner. */
	private static void won(ServerPlayer player, CultivationData data, Trial trial, ServerLevel fxLevel, Vec3 at, Vec3 heart) {
		Fx.send(fxLevel, at, heart, FxType.HEART_DEMON_DEFEAT, TrialFxIds.DEFEAT_SHATTERED, player.getId(), 0.0F);
		fxLevel.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
		fxLevel.playSound(null, heart.x, heart.y, heart.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
		fxLevel.playSound(null, heart.x, heart.y, heart.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.9F);

		data.addHeartDemonDefeated();
		data.setHeartDemonPressure(0.0D);
		// "Hati Dao Teguh": +0.1 on the next breakthrough's quality score (the setter keeps it within its cap).
		data.setNextBreakthroughBonus(data.nextBreakthroughBonus() + 0.1D);
		boolean breakthrough = trial.kind == Kind.BREAKTHROUGH;
		double gained;
		if (breakthrough) {
			// A little qi, and the wounds close before the tribulation omen starts.
			gained = Progression.grantXp(player, data.rank().xpRequired() * 0.1D, XpSource.TRIAL);
			player.setHealth(player.getMaxHealth());
			player.clearFire();
		} else {
			gained = Progression.grantXp(player, data.rank().xpRequired() * 0.25D, XpSource.TRIAL);
		}
		Msg.title(player, Component.translatable("kultivator.trial.won.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.trial.won.subtitle").withStyle(ChatFormatting.YELLOW), 10, 60, 20);
		Msg.chat(player, Component.translatable(breakthrough ? "kultivator.trial.won.breakthrough" : "kultivator.trial.won.pressure",
				Component.literal(Msg.number(gained)).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GOLD));
		Advancements.grant(player, "heart_demon_defeated");
		if (!breakthrough) {
			Advancements.grant(player, "inner_demon_quelled");
		}
		if (data.heartDemonsDefeated() >= 3) {
			Advancements.grant(player, "still_heart");
		}
		Cultivators.dirty(player);
		if (breakthrough) {
			// Breakthrough records the passed trial and starts the tribulation omen.
			Breakthrough.onTrialFinished(player, true);
		}
	}

	private static void lost(ServerPlayer player, CultivationData data, Trial trial, Outcome outcome, ServerLevel fxLevel, Vec3 at,
							 Vec3 heart) {
		if (outcome == Outcome.OVERWHELMED) {
			// A knockback that threw them off a ledge must not finish what the trial already decided.
			player.resetFallDistance();
		}
		Fx.send(fxLevel, at, heart, FxType.HEART_DEMON_DEFEAT, TrialFxIds.DEFEAT_RETURNED, player.getId(), 0.0F);
		fxLevel.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.6F, 0.5F);
		player.level().playSound(null, heart.x, heart.y, heart.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.5F);
		String key = switch (outcome) {
			case OVERWHELMED -> "kultivator.trial.lost.overwhelmed";
			case TIMEOUT -> "kultivator.trial.lost.timeout";
			default -> "kultivator.trial.lost.fled";
		};
		Msg.chat(player, Component.translatable(key).withStyle(ChatFormatting.DARK_RED));
		Advancements.grant(player, "heart_demon_lost");
		if (trial.kind == Kind.BREAKTHROUGH) {
			// Breakthrough applies the failure (deviation or Hardcore realm loss, +25 pressure).
			Breakthrough.onTrialFinished(player, false);
		} else {
			punishPressure(player, data);
		}
	}

	/** The pressure demon won: a Qi Deviation (severity 0.6, never lethal) and some of the pressure is released. */
	private static void punishPressure(ServerPlayer player, CultivationData data) {
		Msg.chat(player, Component.translatable("kultivator.trial.lost.pressure").withStyle(ChatFormatting.DARK_RED));
		data.setHeartDemonPressure(data.heartDemonPressure() - 30.0D);
		Cultivators.dirty(player);
		Deviation.apply(player, 0.6D);
	}

	/** Removes a trial's demon and boss bar without any result or message. */
	private static void removeQuietly(Trial trial) {
		trial.bar.removeAllPlayers();
		if (trial.demon != null && !trial.demon.isRemoved()) {
			trial.demon.discard();
		}
		trial.demon = null;
	}

	// ================================================================== events

	/**
	 * The demon's blow, or harm without an attacker (a fall, lava or the void's edge after its knockback), that could
	 * leave its owner below 20% of their max health is cancelled, and the trial is lost at that moment (resolved on the
	 * next tick). The damage is estimated after armour, Resistance and Protection ({@link DamageEstimate}, an upper
	 * bound); a demon's blow on a raised shield goes through unless it could kill, and {@link #afterDamage} judges the
	 * health it really left. The void's own damage is not held back here; instead tickTrial catches an owner who falls
	 * below the world, puts them back on the ground and ends the trial as lost.
	 */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || TRIALS.isEmpty()) {
			return true;
		}
		Trial trial = TRIALS.get(player.getUUID());
		if (trial == null) {
			return true;
		}
		boolean fromDemon = trial.demon != null && source.getEntity() == trial.demon;
		boolean environmental = source.getEntity() == null && source.getDirectEntity() == null
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!fromDemon && !environmental) {
			return true;
		}
		if (trial.pending != null) {
			return false;
		}
		if (!(amount > 0.0F)) {
			return true;
		}
		float left = player.getHealth() + player.getAbsorptionAmount() - DamageEstimate.upperBound(player, source, amount);
		boolean shielded = fromDemon && player.isBlocking();
		if (left <= 0.0F || !shielded && left < player.getMaxHealth() * LOSE_FRACTION) {
			trial.pending = Outcome.OVERWHELMED;
			return false;
		}
		return true;
	}

	/** A demon's blow taken on a shield that still left the owner below 20%: the trial is lost. */
	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(victim instanceof ServerPlayer player) || TRIALS.isEmpty()) {
			return;
		}
		Trial trial = TRIALS.get(player.getUUID());
		if (trial != null && trial.pending == null && trial.demon != null && source.getEntity() == trial.demon
				&& player.getHealth() + player.getAbsorptionAmount() < player.getMaxHealth() * LOSE_FRACTION) {
			trial.pending = Outcome.OVERWHELMED;
		}
	}

	private static void onOwnerDeath(ServerPlayer player) {
		WARNINGS.remove(player.getUUID());
		Trial trial = TRIALS.get(player.getUUID());
		if (trial != null) {
			finish(player, Cultivators.get(player), trial, Outcome.DIED);
		}
	}

	/** Leaving loses the trial: Breakthrough records a breakthrough trial itself, a pressure trial is settled here. */
	private static void onLeave(ServerPlayer player) {
		WARNINGS.remove(player.getUUID());
		KILL_PRESSURE.remove(player.getUUID());
		Trial trial = TRIALS.get(player.getUUID());
		if (trial != null) {
			finish(player, Cultivators.get(player), trial, Outcome.LEFT);
		}
	}

	// ================================================================== pending loss

	private static boolean hasPendingLoss(CultivationData data) {
		return data.extra().getCompoundOrEmpty(EXTRA_KEY).getBooleanOr(PENDING_LOSS, false);
	}

	private static void setPendingLoss(CultivationData data, boolean pending) {
		CompoundTag mine = data.extra().getCompoundOrEmpty(EXTRA_KEY);
		if (pending) {
			mine.putBoolean(PENDING_LOSS, true);
		} else {
			mine.remove(PENDING_LOSS);
		}
		data.extra().put(EXTRA_KEY, mine);
	}

	/** An abandoned pressure trial catches up once the cultivator is back (2 s after joining or respawning). */
	private static void applyPendingLoss(ServerPlayer player, CultivationData data) {
		if (player.tickCount < 40 || !hasPendingLoss(data)) {
			return;
		}
		setPendingLoss(data, false);
		Msg.chat(player, Component.translatable("kultivator.trial.lost.pending").withStyle(ChatFormatting.DARK_RED));
		Advancements.grant(player, "heart_demon_lost");
		punishPressure(player, data);
	}

	// ================================================================== debug commands

	private static int summonCommand(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator() || TRIALS.containsKey(player.getUUID())
				|| Breakthrough.isBusy(player) || data.breakingThrough()) {
			source.sendFailure(Component.translatable("kultivator.trial.debug.busy", player.getDisplayName()));
			return 0;
		}
		WARNINGS.remove(player.getUUID());
		if (!startTrial(player, data, Kind.PRESSURE, null)) {
			source.sendFailure(Component.translatable("kultivator.trial.debug.failed"));
			return 0;
		}
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.trial.debug.summoned", player.getDisplayName())), false);
		if (player.isCreative()) {
			source.sendSuccess(() -> Component.translatable("kultivator.trial.debug.creative").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int showPressure(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.trial.debug.pressure_show", player.getDisplayName(),
				Msg.number(data.heartDemonPressure()), data.heartDemonsDefeated())), false);
		return 1;
	}

	private static int setPressure(CommandSourceStack source, ServerPlayer player, double value) {
		CultivationData data = Cultivators.get(player);
		data.setHeartDemonPressure(value);
		Cultivators.syncNow(player);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.trial.debug.pressure_set", player.getDisplayName(),
				Msg.number(data.heartDemonPressure()))), false);
		return 1;
	}

	private static int clearCommand(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean warned = WARNINGS.containsKey(player.getUUID());
		boolean pending = hasPendingLoss(data);
		setPendingLoss(data, false);
		boolean dispelled = dispel(player);
		if (!dispelled && !warned && !pending) {
			source.sendFailure(Component.translatable("kultivator.trial.debug.nothing", player.getDisplayName()));
			return 0;
		}
		Cultivators.syncNow(player);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.trial.debug.cleared", player.getDisplayName())), false);
		return 1;
	}

	private HeartDemons() {
	}
}
