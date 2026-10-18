package dev.kultivator.formasi.stack;

/**
 * The combined, capped formation field at a position for one member (DESIGN 24.5). {@code healAmp} −1 means no
 * Regeneration; every factor is 1 and every bonus 0 in {@link #NONE}.
 */
public record FormationField(double gather, double unite, double stackCap, double drain, int healAmp, double qiRegen, double deviation,
		double tribFactor, double refineChance, int refineSteps, double refineTime, double inscribe, double quality, int layers,
		int concealGrade, int illusionGrade) {

	/** No formation here. */
	public static final FormationField NONE = new FormationField(1.0D, 1.0D, Stacking.FORMATION_STACK_CAP, 1.0D, -1, 1.0D, 1.0D, 1.0D,
			0.0D, 0, 1.0D, 0.0D, 0.0D, 0, 0, 0);

	/** True when nothing here differs from {@link #NONE}. */
	public boolean empty() {
		return this.equals(NONE);
	}

	/** The sect gathering term G = min(GATHER_SECT_CAP, gather × unite). */
	public double sectGathering() {
		return Math.min(Stacking.GATHER_SECT_CAP, gather * unite);
	}
}
