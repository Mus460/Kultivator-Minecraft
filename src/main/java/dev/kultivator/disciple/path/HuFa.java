package dev.kultivator.disciple.path;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hu Fa, the master guarding a disciple's breakthrough, calamity or soul-lamp trial (DESIGN 20.3.18). Sumber: <i>Battle
 * Through the Heavens</i>: Yao Lao protects and guides Xiao Yan through refining the Green Lotus Core Flame and the Fallen
 * Heart Flame (护法).
 * <p>
 * The master qualifies while within 8 blocks and meditating, or standing still while sneaking: strikes x0.8, quality
 * +0.1, Foundation chance +10%. Tahan Petir lets the master take the next strike himself ({@code 0.5 x the strike's
 * share x his max health}, never below 1 health), at most 3 per trial for 15% of his max qi each. Dao Protector waves
 * come every 2 strikes of a disciple's tribulation; each protector the master kills softens the next strike (x0.92, at
 * least x0.6) and adds +0.05 quality (at most +0.2). Other disciples may fight the protectors but never reduce strikes.
 * A guarded success gives +10 bond. This class also follows every disciple breakthrough for the Foundation types and
 * the heir's hexes.
 */
public final class HuFa {

	public static final double RANGE = 8.0D;
	public static final double STRIKE_FACTOR = 0.8D;
	public static final double QUALITY_BONUS = 0.1D;
	public static final double FOUNDATION_BONUS = 0.10D;
	public static final double KILL_STRIKE = 0.92D;
	public static final double KILL_STRIKE_MIN = 0.6D;
	public static final double KILL_QUALITY = 0.05D;
	public static final double KILL_QUALITY_MAX = 0.2D;
	public static final int TAHAN_MAX = 3;
	public static final double TAHAN_QI = 0.15D;
	public static final double TAHAN_SHARE = 0.5D;
	/** Standing still this long (ticks) while sneaking counts as guarding. */
	public static final int STILL_TICKS = 20;
	/** Protector waves come after every second strike of a disciple's tribulation. */
	public static final int WAVE_EVERY = 2;

	/** One guarded trial. */
	static final class Guard {
		final DiscipleEntity entity;
		final @Nullable UUID owner;
		final @Nullable DiscipleBreakthrough.Kind kind;
		final Rank target;
		final Realm fromRealm;
		final boolean calamity;
		boolean guardedEver;
		boolean masterMeditatedNear;
		boolean tahanPending;
		int killsPending;
		int qualityKills;
		int tahanUsed;
		int ticks;

		Guard(DiscipleEntity entity, @Nullable DiscipleBreakthrough.Kind kind, Rank target, boolean calamity) {
			this.entity = entity;
			this.owner = entity.ownerId();
			this.kind = kind;
			this.target = target;
			this.fromRealm = entity.data().realm();
			this.calamity = calamity;
		}

		boolean hasStrikes() {
			return calamity || kind == DiscipleBreakthrough.Kind.TRIBULATION;
		}
	}

	private static final Map<DiscipleEntity, Guard> GUARDS = new IdentityHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Integer> STILL = new HashMap<>();

	static void register() {
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onStart(DiscipleEntity entity, DiscipleBreakthrough.Kind kind, Rank target) {
				if (entity.role() == DiscipleEntity.Role.DISCIPLE) {
					begin(entity, kind, target, false);
				}
			}

			@Override
			public void onStrike(DiscipleEntity entity, int index, int count) {
				onBreakthroughStrike(entity, index, count);
			}

			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				Guard guard = GUARDS.get(entity);
				if (success && kind == DiscipleBreakthrough.Kind.GATHERING) {
					FoundationTypes.roll(entity, guard != null && guard.masterMeditatedNear);
				}
				if (success && guard != null && guard.fromRealm != entity.data().realm()) {
					Heir.onNewRealm(entity);
				}
				end(entity, success);
			}

			@Override
			public void onCancel(DiscipleEntity entity, DiscipleBreakthrough.Kind kind) {
				end(entity, false);
			}
		});
		DiscipleHooks.addStrikeFactor(HuFa::strikeFactor);
		DiscipleHooks.addStrikeSkip(HuFa::strikeSkip);
		DiscipleHooks.addQualityBonus(HuFa::qualityBonus);
		DiscipleHooks.addFoundationChance(HuFa::foundationChance);
	}

	// ================================================================== who guards

	/**
	 * True while the disciple's master qualifies as its guardian right now: online in its level within 8 blocks, and
	 * meditating or standing still while sneaking. Module C may ask this for the flame absorption.
	 */
	public static boolean active(DiscipleEntity entity) {
		return qualifies(entity.owner(), entity);
	}

	static boolean qualifies(@Nullable ServerPlayer master, DiscipleEntity entity) {
		if (master == null || master.isSpectator() || !DisciplePaths.near(master, entity, RANGE)) {
			return false;
		}
		return Cultivators.get(master).meditating() || master.isShiftKeyDown() && STILL.getOrDefault(master.getUUID(), 0) >= STILL_TICKS;
	}

	/** True while a guarded trial of the disciple runs and its master guards it now (synced flag). */
	static boolean guarded(DiscipleEntity entity) {
		return GUARDS.containsKey(entity) && active(entity);
	}

	/** True while any breakthrough, calamity or lamp trial of the disciple is followed here. */
	static boolean inTrial(DiscipleEntity entity) {
		return GUARDS.containsKey(entity);
	}

	static @Nullable Guard guard(DiscipleEntity entity) {
		return GUARDS.get(entity);
	}

	static void begin(DiscipleEntity entity, @Nullable DiscipleBreakthrough.Kind kind, Rank target, boolean calamity) {
		GUARDS.put(entity, new Guard(entity, kind, target, calamity));
	}

	/** The trial ended: protectors go, and a guarded success brings bond and the guardian's advancement. */
	static void end(DiscipleEntity entity, boolean success) {
		Guard guard = GUARDS.remove(entity);
		if (guard == null) {
			return;
		}
		DaoProtectors.clearFor(entity);
		if (!success || !guard.guardedEver) {
			return;
		}
		Bond.add(entity.data(), Bond.GUARD);
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "hufa.guarded", DisciplePaths.name(entity), Bond.GUARD)
					.withColor(DisciplePaths.JADE));
			Advancements.grant(master, "disciple_guardian");
		}
	}

	// ================================================================== tick

	static void tick(MinecraftServer server) {
		if (GUARDS.isEmpty()) {
			if (!STILL.isEmpty()) {
				STILL.clear();
				LAST_POS.clear();
			}
			return;
		}
		if (server.getTickCount() % 5 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Vec3 pos = player.position();
				Vec3 last = LAST_POS.put(player.getUUID(), pos);
				boolean still = last != null && last.distanceToSqr(pos) < 0.0025D;
				STILL.put(player.getUUID(), still ? STILL.getOrDefault(player.getUUID(), 0) + 5 : 0);
			}
		}
		for (Guard guard : List.copyOf(GUARDS.values())) {
			DiscipleEntity entity = guard.entity;
			if (entity.isRemoved() || !entity.isAlive()) {
				GUARDS.remove(entity);
				DaoProtectors.clearFor(entity);
				continue;
			}
			guard.ticks++;
			if (guard.ticks % 10 != 0) {
				continue;
			}
			ServerPlayer master = entity.owner();
			boolean active = qualifies(master, entity);
			if (!active) {
				continue;
			}
			guard.guardedEver = true;
			if (guard.kind == DiscipleBreakthrough.Kind.GATHERING && Cultivators.get(master).meditating()) {
				guard.masterMeditatedNear = true;
			}
			if (guard.ticks % 40 == 0 && entity.level() instanceof ServerLevel level) {
				Fx.send(level, master.position().add(0.0D, 0.05D, 0.0D), entity.position().add(0.0D, 1.0D, 0.0D), FxType.STAR,
						PathFxIds.HUFA_SEAL, 0, (float) RANGE);
				if (!guard.hasStrikes()) {
					Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "hufa.active", DisciplePaths.name(entity))
							.withColor(DisciplePaths.JADE));
				}
			}
		}
	}

	/** Dao Protector waves for a disciple's tribulation: after every second strike, {@code 1 + target realm / 3} monsters. */
	private static void onBreakthroughStrike(DiscipleEntity entity, int index, int count) {
		Guard guard = GUARDS.get(entity);
		if (guard == null || guard.kind != DiscipleBreakthrough.Kind.TRIBULATION || index % WAVE_EVERY != 0 || index >= count
				|| !KultivatorConfig.get().daoProtectorEvent || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer master = entity.owner();
		int arrived = DaoProtectors.spawnWaveFor(level, entity, master, guard.target, 1 + guard.target.realm().ordinal() / 3, 1.0D);
		if (arrived > 0 && master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "hufa.wave", arrived, DisciplePaths.name(entity))
					.withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** A Dao Protector died: when the master killed one of a guarded disciple's wave, the next strike softens. */
	static void onDeath(LivingEntity victim, DamageSource source) {
		if (GUARDS.isEmpty() || !DaoProtectors.isProtector(victim) || !(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		for (Guard guard : GUARDS.values()) {
			if (killer.getUUID().equals(guard.owner) && DaoProtectors.isWaveOf(victim, guard.entity)) {
				guard.killsPending++;
				guard.qualityKills = Math.min((int) Math.round(KILL_QUALITY_MAX / KILL_QUALITY), guard.qualityKills + 1);
				Msg.actionBar(killer, Component.translatable(DisciplePaths.PREFIX + "hufa.kill", DisciplePaths.name(guard.entity))
						.withColor(DisciplePaths.GOLD));
				return;
			}
		}
	}

	// ================================================================== hooks

	private static double strikeFactor(DiscipleEntity entity, Rank target, int index) {
		double factor = 1.0D;
		if (active(entity)) {
			factor *= STRIKE_FACTOR;
		}
		if (FoundationTypes.softensStrikes(entity.data())) {
			factor *= FoundationTypes.LANGIT_STRIKES;
		}
		Guard guard = GUARDS.get(entity);
		if (guard != null && guard.killsPending > 0) {
			factor *= Math.max(KILL_STRIKE_MIN, Math.pow(KILL_STRIKE, guard.killsPending));
			guard.killsPending = 0;
		}
		return factor;
	}

	private static boolean strikeSkip(DiscipleEntity entity, Rank target, int index) {
		Guard guard = GUARDS.get(entity);
		if (guard == null || !guard.tahanPending) {
			return false;
		}
		guard.tahanPending = false;
		return redirect(entity, guard);
	}

	private static double qualityBonus(DiscipleEntity entity, Rank target) {
		Guard guard = GUARDS.get(entity);
		double bonus = guard != null && guard.guardedEver || active(entity) ? QUALITY_BONUS : 0.0D;
		if (guard != null) {
			bonus += Math.min(KILL_QUALITY_MAX, KILL_QUALITY * guard.qualityKills);
		}
		return bonus;
	}

	private static double foundationChance(DiscipleEntity entity) {
		Guard guard = GUARDS.get(entity);
		return guard != null && guard.guardedEver || active(entity) ? FOUNDATION_BONUS : 0.0D;
	}

	// ================================================================== Tahan Petir

	/** The master takes this strike: a tribulation hit on him that never leaves less than 1 health. False when he cannot. */
	private static boolean redirect(DiscipleEntity entity, Guard guard) {
		ServerPlayer master = entity.owner();
		if (master == null || master.isSpectator() || !DisciplePaths.near(master, entity, Disciples.ACTION_RANGE)
				|| !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		DiscipleData data = entity.data();
		double share = guard.calamity ? Calamity.currentShare(entity) : DiscipleBreakthrough.strikeShare(data);
		float amount = (float) (TAHAN_SHARE * share * master.getMaxHealth());
		float dealt = Math.min(amount, master.getHealth() + master.getAbsorptionAmount() - 1.0F);
		if (dealt > 0.0F) {
			master.hurtServer(level, Tribulation.damageSource(level), dealt);
		}
		if (master.isAlive() && master.getHealth() < 1.0F) {
			master.setHealth(1.0F);
		}
		Fx.send(level, master.position().add(0.0D, 1.2D, 0.0D), entity.position().add(0.0D, 1.2D, 0.0D), FxType.STAR, PathFxIds.TAHAN_ARC, 0, 1.0F);
		DisciplePaths.sound(level, master.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.2F);
		DisciplePaths.sound(level, master.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.8F, 1.0F);
		Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "hufa.tahan_taken", DisciplePaths.name(entity))
				.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** The Tahan Petir talisman (and {@code /kultivasi murid tahan}). True when the next strike is now the master's. */
	static boolean requestTahan(ServerPlayer master, DiscipleEntity entity) {
		Guard guard = GUARDS.get(entity);
		Component name = DisciplePaths.name(entity);
		String why = null;
		if (guard == null || !guard.hasStrikes()) {
			why = "why.tahan_none";
		} else if (guard.tahanPending) {
			why = "why.tahan_ready";
		} else if (guard.tahanUsed >= TAHAN_MAX) {
			why = "why.tahan_max";
		} else if (!DisciplePaths.near(master, entity, RANGE)) {
			why = "why.tahan_far";
		}
		if (why == null) {
			double cost = Qi.max(Cultivators.get(master)) * TAHAN_QI;
			if (!Qi.trySpend(master, cost)) {
				why = "why.tahan_qi";
			}
		}
		if (why != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + why, name).withColor(DisciplePaths.REFUSE));
			return false;
		}
		guard.tahanPending = true;
		guard.tahanUsed++;
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "hufa.tahan_ready", name, guard.tahanUsed, TAHAN_MAX)
				.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** {@code /kultivasi murid tahan}: the nearest own disciple within 16 blocks whose trial has strikes. */
	static int tahanCommand(ServerPlayer player) {
		DiscipleEntity best = null;
		double bestSq = Disciples.ACTION_RANGE * Disciples.ACTION_RANGE;
		for (Guard guard : GUARDS.values()) {
			DiscipleEntity entity = guard.entity;
			if (!entity.isOwnedBy(player) || entity.level() != player.level() || !guard.hasStrikes()) {
				continue;
			}
			double distanceSq = entity.distanceToSqr(player);
			if (distanceSq <= bestSq) {
				bestSq = distanceSq;
				best = entity;
			}
		}
		if (best == null) {
			Msg.chat(player, Component.translatable(DisciplePaths.PREFIX + "why.tahan_none_near").withColor(DisciplePaths.REFUSE));
			return 0;
		}
		return requestTahan(player, best) ? 1 : 0;
	}

	/** Tahan Petir uses left in the running trial (-1 without a trial that has strikes). */
	static int tahanLeft(DiscipleEntity entity) {
		Guard guard = GUARDS.get(entity);
		return guard == null || !guard.hasStrikes() ? -1 : TAHAN_MAX - guard.tahanUsed;
	}

	static boolean tahanPending(DiscipleEntity entity) {
		Guard guard = GUARDS.get(entity);
		return guard != null && guard.tahanPending;
	}

	static void clear() {
		GUARDS.clear();
		LAST_POS.clear();
		STILL.clear();
	}

	private HuFa() {
	}
}
