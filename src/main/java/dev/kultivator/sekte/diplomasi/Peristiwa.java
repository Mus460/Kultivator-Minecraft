package dev.kultivator.sekte.diplomasi;

import java.util.Locale;

/**
 * The events an NPC sect brings to a hall (DESIGN 36.2.9 event table), also the names of {@code uji perguruan diplomasi
 * aksi <sekte> <peristiwa>}. Each one fires its {@code AksiSekte} on the bus when it happens.
 */
public enum Peristiwa {
	/** An envoy with greetings. */
	UTUSAN,
	/** A gift of PK. */
	HADIAH,
	/** A challenge to an inter-sect tournament (module T). */
	TANTANG,
	/** A raid on the existing raid schedule (or a border skirmish below hall level 3). */
	SERANGAN,
	/** A mission team ambushed (module X). */
	SERGAP,
	/** Disciples lured away (module P). */
	MEREKRUT,
	/** War declared. */
	PERANG,
	/** A non-aggression pact proposed. */
	PAKTA,
	/** An alliance proposed. */
	SEKUTU,
	/** Peace offered during a war. */
	DAMAI,
	/** A strong sect demands tribute. */
	TUNTUT,
	/** The sect master's duel letter during a war. */
	KETUA,
	/** The sect grows one tier now (test). */
	TUMBUH,
	/** A feud with another NPC sect starts now (test). */
	PERANG_NPC;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Peristiwa byId(String id) {
		for (Peristiwa p : values()) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}
}
