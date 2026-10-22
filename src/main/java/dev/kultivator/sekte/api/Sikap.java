package dev.kultivator.sekte.api;

/**
 * The stance between the player's sect and an NPC sect (DESIGN 36.2.9, module D), derived from the standing -100..100:
 * war below -70, hostile below -30, cold below -10, neutral below 20, friendly below 60 and above (allied only with a
 * pact).
 */
public enum Sikap {
	PERANG("perang", -70),
	BERMUSUHAN("bermusuhan", -30),
	DINGIN("dingin", -10),
	NETRAL("netral", 20),
	RAMAH("ramah", Integer.MAX_VALUE),
	SEKUTU("sekutu", Integer.MAX_VALUE);

	private final String id;
	/** Standings below this belong to this stance (checked in order). */
	private final int below;

	Sikap(String id, int below) {
		this.id = id;
		this.below = below;
	}

	public String id() {
		return id;
	}

	public String key() {
		return "kultivator.sekte.sikap." + id;
	}

	/** The stance of a standing; a {@code pact} with a standing of 60 or more is an alliance. */
	public static Sikap of(int standing, boolean pact) {
		if (pact && standing >= 60) {
			return SEKUTU;
		}
		for (Sikap sikap : values()) {
			if (standing < sikap.below) {
				return sikap;
			}
		}
		return RAMAH;
	}
}
