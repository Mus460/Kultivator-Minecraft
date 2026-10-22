package dev.kultivator.sekte.api;

/** What an NPC sect did (DESIGN 36.2.9, {@code SekteBus.RIVAL_SECT_ACTION}, fired only by module D). */
public enum AksiSekte {
	/** The sect grew one tier. */
	TUMBUH("tumbuh"),
	/** An envoy arrived at the hall gate (module E receives it). */
	UTUSAN("utusan"),
	/** A gift or tribute was sent. */
	HADIAH("hadiah"),
	/** A challenge to a friendly tournament or a disciple duel (module T hosts it). */
	TANTANGAN("tantangan"),
	/** A raid on the hall territory (live or settled as data, like {@code musuh.SectRaids}). */
	SERANGAN("serangan"),
	/** War declared. */
	PERANG("perang"),
	/** Peace offered or concluded. */
	DAMAI("damai"),
	/** An alliance pact concluded. */
	SEKUTU("sekutu"),
	/** An alliance broken. */
	PUTUS("putus"),
	/** A mission team of the player was ambushed (module X resolves it). */
	SERGAP_MISI("sergap_misi"),
	/** The sect tried to lure a disciple away (module P decides). */
	MEREKRUT("merekrut"),
	/** Two NPC sects fought each other (news only). */
	PERANG_NPC("perang_npc");

	private final String id;

	AksiSekte(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String key() {
		return "kultivator.sekte.aksi." + id;
	}
}
