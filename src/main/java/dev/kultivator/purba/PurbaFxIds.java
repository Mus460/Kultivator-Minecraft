package dev.kultivator.purba;

/**
 * Sub-ids ({@code a}) of {@code FxType.PURBA} (DESIGN 26.14 item 3), owned by P9-purba. Every visual is drawn by the client
 * ({@code client.purba.PurbaFx}) with particles and the 7b sky director; nothing here is a block, a {@code LightningBolt}
 * or a weather or time change.
 */
public final class PurbaFxIds {

	/** A closing telegraph ring on the ground: pos the centre, b rgb, f radius, target.x the ticks it lasts. */
	public static final int RING = 0;
	/** A telegraphed line: pos the start, target the end, b rgb, f the ticks it lasts. */
	public static final int LINE = 1;
	/** A skill lands: a burst and a ring (pos, b rgb, f radius). */
	public static final int HIT = 2;
	/** Gelombang Tiga Ribu Li, the Kun's wave (pos, f radius). */
	public static final int WAVE = 3;
	/** Sayap Bagai Awan, the Peng's wing shadow (pos the ground centre, f radius). */
	public static final int WING_SHADOW = 4;
	/** Zhulong's gaze beam for one second (pos the head, target the far end, f the cone half-angle in degrees). */
	public static final int GAZE = 5;
	/** Hembusan Musim Dingin (pos the head, target the centre, f radius). */
	public static final int BREATH_COLD = 6;
	/** Seruan Musim Panas (pos the head, target the centre, f radius). */
	public static final int BREATH_HOT = 7;
	/** Taotie's inhale (pos the mouth, target the far end of the cone). */
	public static final int INHALE = 8;
	/** The Celah opens: a glint on the beast (pos, f seconds). */
	public static final int CELAH = 9;
	/** The candle flares before a strike from the dark (pos the side the strike comes from). */
	public static final int CANDLE = 10;
	/** Zhulong closes its eyes: the client darkens the sky (b 1 on, 0 off; pos the beast). */
	public static final int NIGHT = 11;
	/** The Heavens gaze: white-gold sky grade (b 1 on, 0 off; pos the player). */
	public static final int WRATH_SKY = 12;
	/** One heavenly strike of a star calamity, drawn by the client (pos the player, b the calamity k). */
	public static final int STRIKE = 13;
	/** An envoy or the Panglima descends in a column of light (pos, b 0 envoy / 1 Panglima). */
	public static final int DESCEND = 14;
	/** The whirlwind of Naik Sembilan Puluh Ribu Li (pos the centre, f radius). */
	public static final int WHIRLWIND = 15;
	/** A primordial phase change: a shock ring (pos, b kind, f radius). */
	public static final int PHASE = 16;
	/** A Pelahap is devoured by Taotie or a player is spat out (pos). */
	public static final int DEVOUR = 17;
	/** The omen: the silhouette crosses the far sky (pos the summon point, b kind). */
	public static final int OMEN = 18;

	/** Telegraph lengths in ticks (shared by the server timing and the client rings). */
	public static final int WAVE_TICKS = 40;
	public static final int POUNCE_TICKS = 30;
	public static final int SPEAR_TICKS = 20;
	public static final int SWORD_TICKS = 30;
	public static final int SEAL_TICKS = 40;
	public static final int BREATH_TICKS = 40;
	public static final int CANDLE_TICKS = 20;

	private PurbaFxIds() {
	}
}
