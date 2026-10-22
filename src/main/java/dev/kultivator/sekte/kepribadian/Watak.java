package dev.kultivator.sekte.kepribadian;

import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Watak, the twelve traits of DESIGN 36.2.1 (six axes of two): the roll at recruitment (or once, lazily, for a disciple
 * that lived before P), the rare change by a heart trial or a betrayal, and the trait effects through the existing
 * {@code DiscipleHooks} (all multiplied with the old factors, bounded, scaled by {@code kepribadian.faktorWatak}).
 * <p>
 * Sumber: Han Li's caution and diligence (<i>A Record of a Mortal's Journey</i>), Bai Xiaochun's fear and cunning
 * (<i>A Will Eternal</i>), Xiao Yan's pride and his loyalty to Yao Lao (<i>Battle Through the Heavens</i>), Wang Lin's
 * grudges (<i>Renegade Immortal</i>).
 */
public final class Watak {

	/** Chance of a third trait; a special candidate (Calon Istimewa) has twice the chance. */
	public static final double THIRD = 0.15D;
	public static final double THIRD_SPECIAL = 0.30D;
	/** The traits a heart trial may turn into their opposite. */
	private static final List<Sifat> FLAWS = List.of(Sifat.MALAS, Sifat.SOMBONG, Sifat.LICIK, Sifat.PENAKUT, Sifat.PENDENDAM);
	/** Days between two trait changes by the stair trial (G). */
	public static final int TRIAL_DAYS = 30;

	// ================================================================== the roll

	/**
	 * Rolls 2 traits of 2 axes (sometimes 3) for {@code data}. Biases: the stair's heart verdict 3 weighs Rajin and Berani
	 * x3, verdict 1 weighs Malas and Penakut x2, Tekad 70+ weighs Setia x2 (DESIGN 36.2.1).
	 */
	static List<Sifat> roll(DiscipleData data, RandomSource random) {
		double[] weights = new double[Sifat.values().length];
		java.util.Arrays.fill(weights, 1.0D);
		int verdict = SekteApi.tangga().resolve(data);
		if (verdict >= 3) {
			weights[Sifat.RAJIN.ordinal()] *= 3.0D;
			weights[Sifat.BERANI.ordinal()] *= 3.0D;
		} else if (verdict == 1) {
			weights[Sifat.MALAS.ordinal()] *= 2.0D;
			weights[Sifat.PENAKUT.ordinal()] *= 2.0D;
		}
		if (Talents.tekad(data) >= 70) {
			weights[Sifat.SETIA.ordinal()] *= 2.0D;
		}
		double third = Talents.special(data) != 0 ? THIRD_SPECIAL : THIRD;
		int count = random.nextDouble() < third ? 3 : 2;
		List<Sifat> traits = new ArrayList<>(count);
		boolean[] axes = new boolean[Sifat.AXES];
		for (int n = 0; n < count; n++) {
			double total = 0.0D;
			for (Sifat sifat : Sifat.values()) {
				if (!axes[sifat.axis()]) {
					total += weights[sifat.ordinal()];
				}
			}
			double pick = random.nextDouble() * total;
			Sifat chosen = null;
			for (Sifat sifat : Sifat.values()) {
				if (axes[sifat.axis()]) {
					continue;
				}
				pick -= weights[sifat.ordinal()];
				chosen = sifat;
				if (pick <= 0.0D) {
					break;
				}
			}
			if (chosen == null) {
				break;
			}
			axes[chosen.axis()] = true;
			traits.add(chosen);
		}
		return traits;
	}

	/**
	 * The stair trial again (module G): when the disciple's newest {@code tangga.*} memory is younger than P's last trait
	 * change, its heart verdict is 3 and it holds a flaw, that flaw turns into its opposite (once per {@link #TRIAL_DAYS}).
	 * Returns the flaw that changed, or null.
	 */
	static Sifat trialShift(DiscipleData data, long today) {
		long changed = Hati.getLong(data, "ujian", Long.MIN_VALUE / 4);
		if (today - changed < TRIAL_DAYS || SekteApi.tangga().resolve(data) < 3) {
			return null;
		}
		long newest = Long.MIN_VALUE;
		for (Kenangan k : Ingatan.ofKind(data, "tangga.")) {
			newest = Math.max(newest, k.day());
		}
		if (newest == Long.MIN_VALUE || newest <= changed) {
			return null;
		}
		for (Sifat flaw : FLAWS) {
			if (Hati.has(data, flaw) && Hati.swap(data, flaw, flaw.opposite())) {
				Hati.putLong(data, "ujian", today);
				return flaw;
			}
		}
		// No flaw to mend: the trial still counts (no second look for 30 days).
		Hati.putLong(data, "ujian", today);
		return null;
	}

	// ================================================================== effects (DiscipleHooks)

	static void registerHooks() {
		DiscipleHooks.addXpFactor((entity, data, source) -> {
			if (!on() || source != XpSource.MEDITATION) {
				return 1.0D;
			}
			double f = 1.0D;
			if (Hati.has(data, Sifat.RAJIN)) {
				f *= KepribadianConfig.scaled(1.08D);
			}
			if (Hati.has(data, Sifat.MALAS)) {
				f *= KepribadianConfig.scaled(0.90D);
			}
			return f;
		});
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			if (!on() || entity.role() != DiscipleEntity.Role.DISCIPLE || !Hati.has(entity.data(), Sifat.SOMBONG)) {
				return 1.0D;
			}
			// Proud in a formal bout only (sword duels, tournament rounds, sparring).
			boolean bout = DuelSessions.byEntity(entity) != null || DuelRules.inDuel(entity)
					|| entity.activity() == DiscipleEntity.ACTIVITY_DUELLIST;
			return bout ? KepribadianConfig.scaled(1.05D) : 1.0D;
		});
		DiscipleHooks.addTeachCostFactor((entity, master, technique) ->
				on() && Hati.has(entity.data(), Sifat.RENDAH_HATI) ? KepribadianConfig.scaled(0.9D) : 1.0D);
		DiscipleHooks.addRetreatFraction(entity -> {
			if (!on() || entity.role() != DiscipleEntity.Role.DISCIPLE || !Hati.has(entity.data(), Sifat.PENAKUT)) {
				return 0.0D;
			}
			return 0.25D + 0.10D * KepribadianConfig.get().faktorWatak;
		});
		DiscipleHooks.addFoundationChance(entity ->
				on() && entity.role() == DiscipleEntity.Role.DISCIPLE && Hati.has(entity.data(), Sifat.AMBISIUS)
						? 0.03D * KepribadianConfig.get().faktorWatak : 0.0D);
	}

	/**
	 * P's work multiplier for other modules (W, A, X): 1 +0.15 Rajin, -0.20 Malas (scaled), plus (contentment - 60) / 200,
	 * halved while defiant; kept in 0.6-1.3.
	 */
	static double workFactor(DiscipleData data) {
		if (!on() || !Hati.known(data)) {
			return 1.0D;
		}
		double f = 1.0D;
		double scale = KepribadianConfig.get().faktorWatak;
		if (Hati.has(data, Sifat.RAJIN)) {
			f += 0.15D * scale;
		}
		if (Hati.has(data, Sifat.MALAS)) {
			f -= 0.20D * scale;
		}
		f += (Hati.contentmentExact(data) - 60.0D) / 200.0D;
		if (Kesetiaan.defiant(data)) {
			f *= 0.5D;
		}
		return Math.max(0.6D, Math.min(1.3D, f));
	}

	private static boolean on() {
		return KepribadianConfig.get().aktif;
	}

	private Watak() {
	}
}
