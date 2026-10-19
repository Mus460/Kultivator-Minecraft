package dev.kultivator.batin;

/** Sub-ids ({@code a}) of {@code FxType.BATIN} (DESIGN 26.14 item 3), owned by P9-batin; drawn by {@code client.batin.BatinFx}. */
public final class BatinFxIds {

	/** The inner world was born: jade and gold rings rising around the player. */
	public static final int BORN = 0;
	/** A meteor's landing point: a red ground ring ({@code f} = seconds until it lands). */
	public static final int METEOR_MARK = 1;
	/** A meteor landed: a burst of fire motes and embers (entity fire only, no block). */
	public static final int METEOR_BURST = 2;
	/** The Gunung Dang Hun glyph ring ({@code f} = radius), sent every second while the lightning shadow is alive. */
	public static final int DANG_HUN = 3;
	/** A lightning visual from the sky to the position (never a LightningBolt). */
	public static final int BOLT = 4;
	/** The Tanggul Qi circle ({@code f} = radius, {@code b} = progress 0-100). */
	public static final int TANGGUL = 5;
	/** An earthly calamity was weathered: green motes and a gold ring. */
	public static final int CALAMITY_WIN = 6;
	/** Pencerahan Ciptaan channel motes ({@code b} = progress 0-100, {@code tx} packs the two Dao colours). */
	public static final int RITUAL = 7;
	/** A self-created art was born: a rune ring in the two Dao colours. */
	public static final int ART_BORN = 8;
	/**
	 * A self-created art's form at the impact point: {@code b} = form | (Dao A + 1) &lt;&lt; 4 | (Dao B + 1) &lt;&lt; 8; target =
	 * the caster's eye; {@code f} = radius or length. Drawn by {@code client.batin.CiptaanFx}.
	 */
	public static final int OWN_ART = 9;
	/** A plot was harvested or planted (personal): a few green sparkles at the player. */
	public static final int PLOT = 10;

	private BatinFxIds() {
	}
}
