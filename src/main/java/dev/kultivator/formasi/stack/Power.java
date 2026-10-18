package dev.kultivator.formasi.stack;

import dev.kultivator.formasi.Formasi;
import dev.kultivator.formasi.FormasiB;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.keeper.Affinity;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;

/**
 * Power P and the effective radius (DESIGN 24.2.9):
 * {@code P = min(P_CAP, 1 + Q + K + F + A)}; support families cap at {@code P_SUPPORT_CAP} and ignore A. A cracked core
 * (L6 "Retak") works at P × 0.5.
 */
public final class Power {

	/** Stone quality Q by best grade: amethyst or Low 0, Mid 0.05, High 0.10, Supreme 0.15 (RMJ ch. 131). */
	public static final double[] STONE_Q = {0.0D, 0.05D, 0.10D, 0.15D};
	/** The L6 amplifier on its target. */
	public static final double FLAG_A = 0.25D;
	public static final int FLAG_RADIUS = 2;
	public static final int GRAND_RADIUS = 2;
	public static final double CRACKED = 0.5D;

	public static double q(int best) {
		return best < 0 ? 0.0D : STONE_Q[Math.min(STONE_Q.length - 1, best)];
	}

	public static double k(CoreRecord record) {
		return Math.min(Stacking.K_CAP, record.keeperK);
	}

	public static double f(CoreRecord record) {
		return record.keeperAffinity ? Affinity.BONUS : 0.0D;
	}

	/** The amplifier term A (≤ A_CAP): L6 on its target plus every registered {@link PowerBonus}. */
	public static double a(CoreRecord record, CoreView view) {
		double a = record.flaggedBy >= 0 ? FLAG_A : 0.0D;
		for (PowerBonus bonus : Formasi.powerBonuses()) {
			try {
				double value = bonus.bonus(view);
				if (Double.isFinite(value) && value > 0.0D) {
					a += value;
				}
			} catch (RuntimeException exception) {
				// a broken bonus adds nothing
			}
		}
		return Math.min(Stacking.A_CAP, a);
	}

	/** P of a record (its view is passed to the power bonuses). */
	public static double of(FormationSpec spec, CoreRecord record, CoreView view) {
		double base = 1.0D + q(record.best) + k(record) + f(record);
		double p = spec.family().support() ? Math.min(Stacking.P_SUPPORT_CAP, base) : Math.min(Stacking.P_CAP, base + a(record, view));
		return record.cracked ? p * CRACKED : p;
	}

	/**
	 * The effective radius: the card radius at the hall level (plus the {@code plus_l5}/{@code plus_l9} steps and the
	 * {@code keeper_radius} of a Murid Dalam+ keeper), then +2 for L6 and +2 for A1 (at most +4), capped at RADIUS_CAP.
	 * {@code hallRadius} caps specs with {@code territory_cap} (L1); TERRITORY specs report the hall radius.
	 */
	public static double radius(FormationSpec spec, CoreRecord record, int hallLevel, double hallRadius) {
		double r = spec.radiusAt(hallLevel);
		if (hallLevel >= 5) {
			r += spec.params().get("plus_l5");
		}
		if (hallLevel >= 9) {
			r += spec.params().get("plus_l9");
		}
		if (record.keeperDalam) {
			r += spec.params().get("keeper_radius");
		}
		int bonus = 0;
		if (record.flaggedBy >= 0) {
			bonus += FLAG_RADIUS;
		}
		if (spec.family() != Family.GABUNGAN && spec.grade() != Grade.ABADI && FormasiB.grandComplete(record.owner)) {
			bonus += GRAND_RADIUS;
		}
		r += Math.min(Stacking.RADIUS_BONUS_CAP, bonus);
		if (spec.params().get("territory_cap") > 0.0D && hallRadius > 0.0D) {
			r = Math.min(r, hallRadius);
		}
		return Math.max(1.0D, Math.min(Stacking.RADIUS_CAP, r));
	}

	private Power() {
	}
}
