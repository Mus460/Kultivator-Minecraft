package dev.kultivator.technique.spec.run;

/**
 * Options of one cast (DESIGN 22.2.6). Only {@code /uji} uses anything other than {@link #DEFAULT}: {@code bypassGates}
 * skips realm, Dao, gate and known checks; {@code free} skips qi and cooldowns; {@code forceVisual} skips the target
 * precheck; {@code showcase} marks the showcase (TechFx flag 64, no mastery); {@code noKehancuran} never carves.
 */
public record CastOptions(boolean bypassGates, boolean free, boolean forceVisual, boolean showcase, boolean noKehancuran) {

	public static final CastOptions DEFAULT = new CastOptions(false, false, false, false, false);
	/** {@code uji teknik <id>}: bypass gates, free, forced visual (carves only with {@code uji teknik kehancuran nyala}). */
	public static final CastOptions TEST = new CastOptions(true, true, true, false, false);
	/** {@code uji teknik pamer}. */
	public static final CastOptions SHOWCASE = new CastOptions(true, true, true, true, false);

	/** Test casts never give mastery, feats or comprehension (22.6). */
	public boolean test() {
		return bypassGates || free || forceVisual || showcase;
	}

	public CastOptions withNoKehancuran() {
		return new CastOptions(bypassGates, free, forceVisual, showcase, true);
	}
}
