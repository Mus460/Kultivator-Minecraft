package dev.kultivator.sekte.api;

/**
 * The ways a master can mistreat a disciple (DESIGN 36.2.1). Whichever module sees it fires
 * {@code SekteBus.DISCIPLE_MISTREATED}; module P turns it into lost contentment, a memory and, in the end, defiance or
 * departure. {@link #severity()} is the base contentment loss before P's trait factors.
 */
public enum Perlakuan {
	/** The master struck the disciple (P detects it from the hurt hook). */
	DIPUKUL("dipukul", 8),
	/** A formal punishment by the master or the Discipline elder (E). */
	DIHUKUM("dihukum", 5),
	/** No teaching, pill, word or shared meditation for {@code neglectDays} in-game days (P). */
	DIABAIKAN("diabaikan", 3),
	/** Duty, garden or mine shifts without a rest day (W). */
	DIPAKSA_KERJA("dipaksa_kerja", 2),
	/** A promised reward withheld or the disciple's share taken (T, X). */
	DIRAMPAS("dirampas", 6),
	/** Left to fall: the master watched it drop below 20% health in a fight, or abandoned it on a mission (P, X). */
	DIKORBANKAN("dikorbankan", 12),
	/** Shamed in public: demoted, or mocked after a lost tournament bout (T, E). */
	DIPERMALUKAN("dipermalukan", 6),
	/** A junior favoured over it: promoted first, or named heir over a senior with a higher bond (E). */
	TIDAK_ADIL("tidak_adil", 4);

	private final String id;
	private final int severity;

	Perlakuan(String id, int severity) {
		this.id = id;
		this.severity = severity;
	}

	public String id() {
		return id;
	}

	public int severity() {
		return severity;
	}

	public String key() {
		return "kultivator.sekte.perlakuan." + id;
	}
}
