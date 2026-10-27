package dev.kultivator.sect.event;

import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.DamageEstimate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Latihan Khusus, the promotion trial from Outer to Inner disciple (DESIGN 20.3.15), started by module A: the disciple
 * holds the stele (activity 5) for 60 s against three Dao Protector waves (sizes 2, 3, 4 at 0, 20 and 40 s; Foundation
 * monsters with 1.6x health, raised for the disciple). It passes when it never drops below 25% health and the master
 * deals no damage to the trial monsters; a hit that would take it lower is stopped at that line and fails the trial, so
 * nobody dies. Failing vanishes the monsters; the next try waits one in-game day. The result goes to
 * {@code SectRanks.onPromotionTrialResult}.
 * <p>
 * Sumber: I Shall Seal the Heavens ch. 27-31 (a special promotion training admits a single Outer disciple into the Inner
 * Sect); Battle Through the Heavens (the Inner Academy selection).
 */
public final class PromotionTrial {

	public static final int DURATION = 1200;
	public static final int[] WAVE_TICKS = {0, 400, 800};
	public static final int[] WAVE_SIZE = {2, 3, 4};
	public static final double HEALTH_FACTOR = 1.6D;
	public static final float MIN_HEALTH = 0.25F;
	public static final double STELE_RANGE = 6.0D;
	public static final double LEAVE_RANGE = 24.0D;
	public static final long RETRY_TICKS = 24000L;

	private static final class Trial {
		final UUID owner;
		final DiscipleEntity disciple;
		final ServerLevel level;
		final Vec3 center;
		int age;
		int wave;
		boolean ended;

		Trial(UUID owner, DiscipleEntity disciple, ServerLevel level, Vec3 center) {
			this.owner = owner;
			this.disciple = disciple;
			this.level = level;
			this.center = center;
		}
	}

	/** Owner -> the running trial (one per hall). */
	private static final Map<UUID, Trial> TRIALS = new HashMap<>();

	public static boolean running(UUID owner) {
		return TRIALS.containsKey(owner);
	}

	/** {@code SectEvents.startPromotionTrial}: false (with a chat reason) when it cannot start now. */
	static boolean start(DiscipleEntity disciple, ServerPlayer master) {
		UUID owner = master.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !EventUtil.hallLoaded(hall) || !disciple.isOwnedBy(master)) {
			EventUtil.chat(master, Component.translatable("kultivator.sectevent.no_hall").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (TRIALS.containsKey(owner) || DuelArena.busy(owner) || Tournament.running(owner) || HundredDynasties.running(owner)) {
			EventUtil.chat(master, Component.translatable("kultivator.sectevent.arena.busy").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!EventUtil.free(disciple) || !EventUtil.nearHall(disciple, hall, STELE_RANGE)) {
			EventUtil.chat(master, Component.translatable("kultivator.sectevent.trial.at_stele", disciple.getDisplayName(), (int) STELE_RANGE)
					.withStyle(ChatFormatting.YELLOW));
			return false;
		}
		CompoundTag extra = EventUtil.extra(disciple.data());
		long retry = extra.getLongOr("trial_retry", 0L);
		if (retry > EventUtil.gameTime()) {
			EventUtil.chat(master, Component.translatable("kultivator.sectevent.trial.retry", disciple.getDisplayName(),
					Expeditions.days(retry - EventUtil.gameTime())).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ServerLevel level = EventUtil.hallLevel(hall);
		Vec3 center = EventUtil.center(hall);
		Duties.release(owner, disciple.soulId(), null);
		BlazingTower.stop(owner, disciple.soulId(), false);
		BlockPos spot = disciple.blockPosition();
		disciple.setActivity(DiscipleEntity.ACTIVITY_FORMATION_GUARD, spot);
		// it stands the trial with its spear facing the hall's heart, where the waves come from
		disciple.setActivityFocus(BlockPos.containing(center));
		extra.putLong("trial_spot", spot.asLong());
		Trial trial = new Trial(owner, disciple, level, center);
		TRIALS.put(owner, trial);
		EventUtil.chat(master, Component.translatable("kultivator.sectevent.trial.start", disciple.getDisplayName(), DURATION / 20)
				.withStyle(ChatFormatting.GOLD));
		EventUtil.chat(master, Component.translatable("kultivator.sectevent.trial.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.2F, 0.7F);
		return true;
	}

	static void tick(MinecraftServer server) {
		if (TRIALS.isEmpty()) {
			return;
		}
		for (Trial trial : new ArrayList<>(TRIALS.values())) {
			if (trial.ended) {
				continue;
			}
			DiscipleEntity disciple = trial.disciple;
			if (disciple.isRemoved() || !disciple.isAlive() || disciple.level() != trial.level
					|| disciple.distanceToSqr(trial.center) > LEAVE_RANGE * LEAVE_RANGE || EventUtil.player(trial.owner) == null
					|| disciple.activity() != DiscipleEntity.ACTIVITY_FORMATION_GUARD) {
				finish(trial, false, "kultivator.sectevent.trial.left");
				continue;
			}
			if (trial.wave < WAVE_TICKS.length && trial.age >= WAVE_TICKS[trial.wave]) {
				int size = WAVE_SIZE[trial.wave];
				ServerPlayer master = EventUtil.player(trial.owner);
				DaoProtectors.spawnWaveFor(trial.level, disciple, master, new Rank(Realm.FOUNDATION, 0, 0), size, HEALTH_FACTOR);
				EventUtil.fx(trial.level, disciple.position(), SectEventFxIds.TRIAL_WAVE, trial.wave + 1, 6.0F);
				trial.level.playSound(null, disciple.getX(), disciple.getY(), disciple.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON,
						SoundSource.HOSTILE, 0.8F, 1.0F);
				if (master != null) {
					dev.kultivator.util.Msg.actionBar(master, Component.translatable("kultivator.sectevent.trial.wave", trial.wave + 1,
							WAVE_TICKS.length).withStyle(ChatFormatting.GOLD));
				}
				trial.wave++;
			}
			trial.age++;
			if (trial.age >= DURATION) {
				finish(trial, true, null);
			}
		}
	}

	/**
	 * ALLOW_DAMAGE: a hit that would take the trial disciple below 25% stops at that line and fails the trial (the
	 * disciple never dies in its trial).
	 */
	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (TRIALS.isEmpty() || !(victim instanceof DiscipleEntity disciple)) {
			return true;
		}
		Trial trial = trialOf(disciple);
		if (trial == null || trial.ended || !(amount > 0.0F)) {
			return true;
		}
		float line = disciple.getMaxHealth() * MIN_HEALTH;
		float after = disciple.getHealth() + disciple.getAbsorptionAmount() - DamageEstimate.upperBound(disciple, source, amount);
		if (after >= line) {
			return true;
		}
		if (disciple.getHealth() > line) {
			disciple.setHealth(line);
		}
		finish(trial, false, "kultivator.sectevent.trial.fell");
		return false;
	}

	/** AFTER_DAMAGE: the master hurting a trial monster ends the trial ("Guru membantu"). */
	static void afterDamage(LivingEntity victim, DamageSource source, float taken) {
		if (TRIALS.isEmpty() || !(taken > 0.0F) || !DaoProtectors.isProtector(victim)) {
			return;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof ServerPlayer player)) {
			return;
		}
		Trial trial = TRIALS.get(player.getUUID());
		if (trial != null && !trial.ended && DaoProtectors.isWaveOf(victim, trial.disciple)) {
			finish(trial, false, "kultivator.sectevent.trial.helped");
		}
	}

	private static @Nullable Trial trialOf(DiscipleEntity disciple) {
		UUID owner = disciple.ownerId();
		Trial trial = owner == null ? null : TRIALS.get(owner);
		return trial != null && trial.disciple == disciple ? trial : null;
	}

	private static void finish(Trial trial, boolean passed, @Nullable String reason) {
		if (trial.ended) {
			return;
		}
		trial.ended = true;
		TRIALS.remove(trial.owner, trial);
		DiscipleEntity disciple = trial.disciple;
		DaoProtectors.clearFor(disciple);
		CompoundTag extra = EventUtil.extra(disciple.data());
		extra.remove("trial_spot");
		if (!disciple.isRemoved() && disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_GUARD) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		ServerPlayer master = EventUtil.player(trial.owner);
		Vec3 at = disciple.position();
		EventUtil.fx(trial.level, at, SectEventFxIds.TRIAL_END, passed ? 1 : 0, 0.0F);
		if (passed) {
			trial.level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
			EventUtil.chat(master, Component.translatable("kultivator.sectevent.trial.passed", disciple.getDisplayName()).withStyle(ChatFormatting.GOLD));
		} else {
			extra.putLong("trial_retry", EventUtil.gameTime() + RETRY_TICKS);
			trial.level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.6F, 0.5F);
			EventUtil.chat(master, Component.translatable(reason == null ? "kultivator.sectevent.trial.failed" : reason, disciple.getDisplayName())
					.withStyle(ChatFormatting.RED));
		}
		if (disciple.isAlive() && !disciple.isRemoved()) {
			SectRanks.onPromotionTrialResult(disciple, passed);
		}
	}

	/** After a restart the formation-guard spot of an unfinished trial is cleared (activity 5 is saved). */
	static void cleanup(DiscipleEntity disciple) {
		CompoundTag extra = disciple.data().extra().getCompoundOrEmpty(Duties.EXTRA);
		if (!extra.contains("trial_spot") || trialOf(disciple) != null) {
			return;
		}
		BlockPos spot = BlockPos.of(extra.getLongOr("trial_spot", 0L));
		EventUtil.extra(disciple.data()).remove("trial_spot");
		if (disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_GUARD && spot.equals(disciple.activitySpot())) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	static void clear() {
		TRIALS.clear();
	}

	private PromotionTrial() {
	}
}
