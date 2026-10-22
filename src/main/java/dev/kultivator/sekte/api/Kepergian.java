package dev.kultivator.sekte.api;

/** How a disciple left the sect (DESIGN 36.2.1, {@code SekteBus.DISCIPLE_LEFT}, fired only by module P). */
public enum Kepergian {
	/** Slipped out of the gate at night with a bundle. */
	MINGGAT("minggat"),
	/** Defied the master in the open (a challenge duel at the stele), then left, maybe with a sworn sibling. */
	MEMBERONTAK("memberontak"),
	/** Defected to an NPC sect (module D takes the disciple in as that sect's member). */
	BERKHIANAT("berkhianat"),
	/** Expelled by the master. */
	DIUSIR("diusir"),
	/** Left in peace to wander (an ambitious disciple that outgrew the sect); may come back one day. */
	MENGEMBARA("mengembara");

	private final String id;

	Kepergian(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String key() {
		return "kultivator.sekte.kepergian." + id;
	}
}
