package dev.kultivator.data;

/** Where cultivation XP came from; decides which multipliers apply (see Progression.addXp). */
public enum XpSource {
	COMBAT,
	MINING,
	MEDITATION,
	PILL,
	ENLIGHTENMENT,
	TRIAL,
	TRIBULATION,
	BOOK,
	SECT,
	/** Commands and other exact amounts: no multipliers. */
	EXACT
}
