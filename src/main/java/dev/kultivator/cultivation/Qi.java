package dev.kultivator.cultivation;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * The qi pool: maximum, regeneration, spending (DESIGN section 2). {@link #max} and {@link #regenPerSecond} are pure
 * (only read {@link CultivationData}), so the client HUD uses them on its snapshot too.
 */
public final class Qi {

	/** Anugerah Kenaikan L4 Lautan Qi: max qi ×1.15 (DESIGN 19.3.11). */
	public static final double QI_SEA_FACTOR = 1.15D;
	/** Anugerah Kenaikan L6 Urat Abadi: qi regeneration ×1.25. */
	public static final double MERIDIAN_REGEN_FACTOR = 1.25D;
	/** Each player star: +1% max qi. */
	public static final double MAX_QI_PER_STAR = 0.01D;

	/** Qi value of the last snapshot we asked for, per player, so slow regeneration does not sync every second. */
	private static final Map<UUID, Double> LAST_SENT = new HashMap<>();

	public static void init() {
		ServerPlayerEvents.LEAVE.register(player -> LAST_SENT.remove(player.getUUID()));
	}

	private static final java.util.List<ToDoubleFunction<ServerPlayer>> REGEN_FACTORS = new java.util.ArrayList<>();
	/** Server regeneration factors (phase 7c formations) never multiply the regeneration by more than this. */
	public static final double REGEN_FACTOR_CAP = 2.0D;

	/** Phase 7c (DESIGN 24.13 item 8): multiplies the player's qi regeneration on the server (product of all, 0 to the cap). */
	public static void addRegenFactor(ToDoubleFunction<ServerPlayer> provider) {
		REGEN_FACTORS.add(provider);
	}

	/** The product of the server regeneration factors for {@code player} (1 without providers). */
	public static double regenFactor(ServerPlayer player) {
		if (REGEN_FACTORS.isEmpty()) {
			return 1.0D;
		}
		double product = 1.0D;
		for (ToDoubleFunction<ServerPlayer> provider : REGEN_FACTORS) {
			double value = provider.applyAsDouble(player);
			if (Double.isFinite(value) && value >= 0.0D) {
				product *= value;
			}
		}
		return Math.min(REGEN_FACTOR_CAP, product);
	}

	/** Called every server tick for every player; regenerates once per second and clamps to the maximum. */
	public static void tick(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 20 != 0 || player.isDeadOrDying()) {
			return;
		}
		double max = max(data);
		double before = data.qi();
		double after;
		if (!Double.isFinite(before)) {
			after = 0.0D;
		} else if (before > max) {
			after = max;
		} else {
			after = Math.min(max, before + regenPerSecond(data) * regenFactor(player));
		}
		if (after == before) {
			return;
		}
		data.setQi(after);
		boolean reachedMax = after >= max && before < max;
		boolean dropped = !(after > before);
		Double last = LAST_SENT.get(player.getUUID());
		if (reachedMax || dropped || last == null || Math.abs(after - last) >= 0.5D) {
			markSent(player, after);
		}
	}

	/**
	 * Max qi: {@code (100 + realm x 120 + stage x 30 + layer x 60) x quality factor x root factor}, where the root factor
	 * is {@code 0.85 + 0.15 x grade multiplier} once the root is tested (1 before); x1.15 with the Qi Sea boon and +1% per
	 * player star (DESIGN 19.3.11). Pure: also used on the client.
	 */
	public static double max(CultivationData data) {
		Rank rank = data.rank();
		double base = 100.0D + rank.realm().ordinal() * 120.0D + rank.stage() * 30.0D + rank.layer() * 60.0D;
		double root = data.rootTested() ? 0.85D + 0.15D * data.rootGrade().multiplier() : 1.0D;
		double sea = ImmortalPerks.boonActive(data, 4) ? QI_SEA_FACTOR : 1.0D;
		double stars = 1.0D + MAX_QI_PER_STAR * ImmortalPerks.stars(data);
		return base * data.qualityFactor() * root * sea * stars;
	}

	/**
	 * Qi regenerated per second right now: 0.4% of max × (1 + 0.1 × caster grade tier) (Dewa Abadi ×1.4, DESIGN 22.5.4),
	 * ×1.25 with the Immortal Meridians boon (DESIGN 19.3.11), x6 while meditating, x1.5 at a spiritual site, nothing
	 * during a Qi Deviation. Pure: also used on the client.
	 */
	public static double regenPerSecond(CultivationData data) {
		if (data.deviating()) {
			return 0.0D;
		}
		double regen = max(data) * 0.004D * dev.kultivator.technique.spec.TechniquePower.qiRegenFactor(data);
		if (ImmortalPerks.boonActive(data, 6)) {
			regen *= MERIDIAN_REGEN_FACTOR;
		}
		if (data.meditating()) {
			regen *= 6.0D;
		}
		if (data.siteMultiplier() > 1.01D) {
			regen *= 1.5D;
		}
		return regen;
	}

	/** 0..1 how full the qi pool is. Pure. */
	public static double fraction(CultivationData data) {
		double max = max(data);
		return max <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, data.qi() / max));
	}

	/** Spends {@code amount} if the player has it (nothing to spend counts as success). */
	public static boolean trySpend(ServerPlayer player, double amount) {
		if (Double.isNaN(amount)) {
			return false;
		}
		if (amount <= 0.0D) {
			return true;
		}
		CultivationData data = Cultivators.get(player);
		if (data.qi() + 1.0E-6D < amount) {
			return false;
		}
		data.setQi(data.qi() - amount);
		markSent(player, data.qi());
		return true;
	}

	/** Adds qi up to the maximum. */
	public static void restore(ServerPlayer player, double amount) {
		if (!(amount > 0.0D)) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		double max = max(data);
		double after = Double.isFinite(amount) ? Math.min(max, data.qi() + amount) : max;
		if (after > data.qi()) {
			data.setQi(after);
			markSent(player, after);
		}
	}

	/** Fills the pool to the maximum. */
	public static void fill(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		data.setQi(max(data));
		markSent(player, data.qi());
	}

	/** Cuts qi down to the maximum after the maximum dropped (realm lost, quality changed...). */
	public static void clamp(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		double max = max(data);
		if (data.qi() > max || !Double.isFinite(data.qi())) {
			data.setQi(Double.isFinite(data.qi()) ? max : 0.0D);
			markSent(player, data.qi());
		}
	}

	private static void markSent(ServerPlayer player, double value) {
		LAST_SENT.put(player.getUUID(), value);
		Cultivators.dirty(player);
	}

	private Qi() {
	}
}
