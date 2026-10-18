package dev.kultivator.formasi.fuel;

import dev.kultivator.cultivation.SpiritVeins;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Upkeep of a running core (DESIGN 24.2.7):
 * {@code centi/tick = upkeep × 100 / 72000 × vein × keeper × standby × flag}. A per-core carry keeps the fraction, so
 * there is no drift. Only running cores in entity-ticking chunks pay; virtual cores never pay.
 */
public final class Upkeep {

	/** Vein factors (<i>Terinspirasi dari</i> RMJ ch. 391): Great, Rich, Normal, Thin. */
	public static final double VEIN_GREAT = 0.70D;
	public static final double VEIN_RICH = 0.85D;
	public static final double VEIN_NORMAL = 1.00D;
	public static final double VEIN_THIN = 1.25D;
	/** Siaga: the family has nothing to do. */
	public static final double STANDBY = 0.25D;
	/** The target of a Formasi Panji Larangan pays a little more. */
	public static final double FLAG_TARGET = 1.10D;
	/** Ticks in one hour. */
	public static final int TICKS_PER_HOUR = 72_000;
	/** A core whose Daya lasts less than this many hours draws from the Kas (when allowed). */
	public static final int DRAW_BELOW_HOURS = 2;
	/** How many hours one auto-draw takes. */
	public static final int DRAW_HOURS = 12;

	/** The vein factor at the eye. */
	public static double vein(ServerLevel level, BlockPos eye) {
		return switch (SpiritVeins.grade(level, eye)) {
			case SpiritVeins.GREAT -> VEIN_GREAT;
			case SpiritVeins.RICH -> VEIN_RICH;
			case SpiritVeins.THIN -> VEIN_THIN;
			default -> VEIN_NORMAL;
		};
	}

	/** The core Daya cap in centi-Daya: 48 h of the spec's upkeep. */
	public static long capCenti(FormationSpec spec) {
		return Math.round(spec.upkeep() * Grade.CAP_HOURS * 100.0D);
	}

	/** Centi-Daya of {@code hours} of plain upkeep. */
	public static long hoursCenti(FormationSpec spec, double hours) {
		return Math.round(spec.upkeep() * hours * 100.0D);
	}

	/** Centi-Daya per tick with every factor applied. */
	public static double centiPerTick(FormationSpec spec, double vein, double keeper, boolean standby, boolean flagged) {
		return spec.upkeep() * 100.0D / TICKS_PER_HOUR * vein * keeper * (standby ? STANDBY : 1.0D) * (flagged ? FLAG_TARGET : 1.0D);
	}

	/** Hours a core lasts at its current rate (infinite at rate 0). */
	public static double hoursLeft(long dayaCenti, double centiPerTick) {
		if (centiPerTick <= 0.0D) {
			return Double.POSITIVE_INFINITY;
		}
		return dayaCenti / centiPerTick / TICKS_PER_HOUR;
	}

	private Upkeep() {
	}
}
