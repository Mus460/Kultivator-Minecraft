package dev.kultivator.kehancuran;

/**
 * Sub effects ({@code a}) of the {@code FxType.KEHANCURAN} packets, shared by the server and
 * {@code client.kehancuran.KehancuranFx}. Never renumber.
 */
public final class KehancuranFxIds {

	/**
	 * The server's caps for the warning screen (sent to one player before {@code OpenScreen.KEHANCURAN_CONFIRM}):
	 * pos = (max radius, max blocks per cast, drops %), target = (hall safe radius, player safe radius, undo minutes),
	 * b = warning seconds, f = blocks per tick.
	 */
	public static final int CONFIG = 0;
	/** The red sky over the dimension: pos = cast centre, b = seconds left (0 stops it). */
	public static final int RED_SKY = 1;
	/** The shape preview: pos = centre or trench start, target = trench end (or the centre), b = Shapes.Kind ordinal, f = radius. */
	public static final int PREVIEW = 2;
	/** Dust of one tick's removed blocks: pos = their middle, b = count, f = spread. */
	public static final int DUST = 3;
	/** The restore shimmer: pos = the middle of one tick's restored blocks, b = count, f = spread. */
	public static final int SHIMMER = 4;
	/** The cast lands: pos = centre, f = radius. */
	public static final int IMPACT = 5;

	private KehancuranFxIds() {
	}
}
