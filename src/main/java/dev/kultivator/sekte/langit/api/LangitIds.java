package dev.kultivator.sekte.langit.api;

/**
 * Shared numbers of Sekte Langit (DESIGN 37.9.1): the advancement folder, the transient effect ops of
 * {@link LangitPayloads.Fx}, the disciple activity ids 25-27 (DESIGN 36 uses 13-24) and the client sightings of
 * {@link LangitPayloads.Saw}. Never renumber (the ops cross the network, the activity ids are read by poses).
 */
public final class LangitIds {

	/** Advancement folder: {@code kultivator:sekte_langit/<name>} ({@code util.Advancements.grant(player, ADV + name)}). */
	public static final String ADV = "sekte_langit/";

	/** Tether light pillar (ground stele to the throne) while a sky site is tethered. */
	public static final int FX_PILAR = 0;
	/** Cloud puff where a disciple arrives on the island. */
	public static final int FX_ARRIVE = 1;
	/** The Cloud Net catches a falling member. */
	public static final int FX_TANGKAP = 2;
	/** Cloud Transfer formation trip. */
	public static final int FX_PINDAH = 3;
	/** Cloud ferry departs or arrives. */
	public static final int FX_FERI = 4;
	/** The Ascension ceremony: nine coloured rings rise from the throne. */
	public static final int FX_NAIK = 5;
	/** A cloud pirate ship breaks through the cloud sea. */
	public static final int FX_BAJAK = 6;
	/** A tribulation seen from above (a vortex in the cloud sea). */
	public static final int FX_SENGSARA_BAWAH = 7;

	/** Flying sword patrol (37M). */
	public static final int ACT_TERBANG_PEDANG = 25;
	/** Rim guard walk (37G). */
	public static final int ACT_JAGA_TEPI = 26;
	/** Watching the cloud sea at dawn or dusk (37G). */
	public static final int ACT_MEMANDANG = 27;

	/** {@link LangitPayloads.Saw}: the Buddha's Light halo was seen. */
	public static final int SAW_HALO = 0;

	/** Site flags of {@link LangitPayloads.SkyHall#flags()}. */
	public static final int FLAG_TETHERED = 1;
	public static final int FLAG_GRAND_FORMATION = 1 << 1;
	public static final int FLAG_NET = 1 << 2;
	public static final int FLAG_GATE_CLOSED = 1 << 3;
	public static final int FLAG_FESTIVAL = 1 << 4;

	private LangitIds() {
	}
}
