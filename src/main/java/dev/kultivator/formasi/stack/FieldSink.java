package dev.kultivator.formasi.stack;

/** What a running core contributes to the stacking field at a position (DESIGN 24.5); {@code Stacking} combines and caps. */
public interface FieldSink {

	/** Sect gathering factor (K2, B5). */
	void gather(double g);

	/** Uniting factor (M5). */
	void unite(double m);

	/** Meditation vein drain factor. */
	void drain(double f);

	/** Healing: Regeneration amplifier for players, qi regeneration factor, deviation timer factor. */
	void heal(int amp, double qiRegen, double deviation);

	/** Tribulation strike factor. */
	void tribulation(double f);

	/** Refining: chance bonus, quality steps, time factor, inscription bonus. */
	void refine(double chance, int steps, double time, double inscribe);

	/** Breakthrough quality contribution. */
	void quality(double q);

	/** Raises the formation stack cap (A1). */
	void stackCap(double cap);

	/** Protection layers (L1). */
	void layers(int n);

	/** Concealment by effective grade. */
	void concealed(int grade);

	/** Illusion by effective grade. */
	void illusion(int grade);
}
