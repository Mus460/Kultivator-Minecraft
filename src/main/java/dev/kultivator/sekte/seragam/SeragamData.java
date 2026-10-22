package dev.kultivator.sekte.seragam;

import dev.kultivator.sekte.api.Lambang;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

/**
 * A hall's whole design (DESIGN 36.2.8) as both sides see it: the packed {@link Lambang}, the motto, and per sect rank
 * 0-5 (Tercatat, Murid Luar, Murid Dalam, Murid Inti, Tetua, Tetua Agung) the robe body colour, the trim colour (palette
 * indices of {@link Lambang#PALETTE}, -1 = keep the old look: the Dao tint, the {@code SectRanks.TRIM} colour) and the cut
 * ({@link #CUT_PANJANG}, {@link #CUT_PENDEK}, {@link #CUT_TEMPUR}); {@code generation} counts the sect masters since the
 * founder (module E's succession), {@code flags} {@link #DESIGNED}. Immutable; the arrays are copied in and out. Pure
 * data, safe on both sides.
 */
public record SeragamData(int lambang, String motto, byte[] robe, byte[] trim, byte[] cut, int generation, int flags) {

	public static final int RANKS = 6;
	public static final int CUT_PANJANG = 0;
	public static final int CUT_PENDEK = 1;
	public static final int CUT_TEMPUR = 2;
	public static final int CUTS = 3;
	public static final int MOTTO_MAX = 32;
	/** The master saved a design at least once (the robe embroidery switches to the hall's emblem). */
	public static final int DESIGNED = 1;
	/** Set by the server's sync when {@code seragam.jubah} is off: clients keep the old robes (never saved). */
	public static final int NO_ROBE = 2;

	/** A hall nobody designed yet: the default emblem, no motto, the old robe colours, long robes. */
	public static final SeragamData DEFAULT = new SeragamData(Lambang.DEFAULT.pack(), "", none(), none(), new byte[RANKS], 1, 0);

	public SeragamData {
		motto = cleanMotto(motto);
		robe = fit(robe, -1, Lambang.PALETTE.length - 1);
		trim = fit(trim, -1, Lambang.PALETTE.length - 1);
		cut = fit(cut, 0, CUTS - 1);
		generation = Math.max(1, Math.min(99, generation));
	}

	private static byte[] none() {
		byte[] b = new byte[RANKS];
		Arrays.fill(b, (byte) -1);
		return b;
	}

	private static byte[] fit(byte[] in, int lo, int hi) {
		byte[] out = new byte[RANKS];
		Arrays.fill(out, (byte) lo);
		if (in != null) {
			for (int i = 0; i < Math.min(RANKS, in.length); i++) {
				out[i] = (byte) Math.max(lo, Math.min(hi, in[i]));
			}
		}
		return out;
	}

	/** Strips formatting codes and control characters and cuts the motto to {@link #MOTTO_MAX} characters. */
	public static String cleanMotto(String motto) {
		if (motto == null || motto.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(Math.min(motto.length(), MOTTO_MAX));
		for (int i = 0; i < motto.length() && sb.length() < MOTTO_MAX; i++) {
			char c = motto.charAt(i);
			if (c == '§') {
				i++;
				continue;
			}
			if (Character.isISOControl(c)) {
				continue;
			}
			sb.append(c);
		}
		return sb.toString().strip();
	}

	public Lambang emblem() {
		return Lambang.unpack(lambang);
	}

	public boolean designed() {
		return (flags & DESIGNED) != 0;
	}

	/** Palette index of rank {@code rank}'s robe body, or -1. */
	public int robeIndex(int rank) {
		return robe[clampRank(rank)];
	}

	public int trimIndex(int rank) {
		return trim[clampRank(rank)];
	}

	public int cutOf(int rank) {
		return cut[clampRank(rank)];
	}

	/** RGB of rank {@code rank}'s robe body, or -1 (keep the Dao tint). */
	public int robeRgb(int rank) {
		int i = robeIndex(rank);
		return i < 0 ? -1 : Lambang.PALETTE[i];
	}

	/** RGB of rank {@code rank}'s trims, or -1 (keep the rank colour). */
	public int trimRgb(int rank) {
		int i = trimIndex(rank);
		return i < 0 ? -1 : Lambang.PALETTE[i];
	}

	public static int clampRank(int rank) {
		return Math.max(0, Math.min(RANKS - 1, rank));
	}

	@Override
	public byte[] robe() {
		return robe.clone();
	}

	@Override
	public byte[] trim() {
		return trim.clone();
	}

	@Override
	public byte[] cut() {
		return cut.clone();
	}

	public SeragamData withEmblem(Lambang emblem) {
		return new SeragamData(emblem.pack(), motto, robe, trim, cut, generation, flags);
	}

	public SeragamData withMotto(String text) {
		return new SeragamData(lambang, text, robe, trim, cut, generation, flags);
	}

	public SeragamData withRank(int rank, int robeIndex, int trimIndex) {
		byte[] r = robe.clone();
		byte[] t = trim.clone();
		r[clampRank(rank)] = (byte) robeIndex;
		t[clampRank(rank)] = (byte) trimIndex;
		return new SeragamData(lambang, motto, r, t, cut, generation, flags);
	}

	public SeragamData withCut(int rank, int value) {
		byte[] c = cut.clone();
		if (rank < 0) {
			Arrays.fill(c, (byte) value);
		} else {
			c[clampRank(rank)] = (byte) value;
		}
		return new SeragamData(lambang, motto, robe, trim, c, generation, flags);
	}

	public SeragamData withGeneration(int value) {
		return new SeragamData(lambang, motto, robe, trim, cut, value, flags);
	}

	public SeragamData withFlags(int value) {
		return new SeragamData(lambang, motto, robe, trim, cut, generation, value);
	}

	/** Same design (emblem, motto, colours, cuts), ignoring generation and flags. */
	public boolean sameDesign(SeragamData other) {
		return other != null && lambang == other.lambang && motto.equals(other.motto) && Arrays.equals(robe, other.robe)
				&& Arrays.equals(trim, other.trim) && Arrays.equals(cut, other.cut);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SeragamData d && sameDesign(d) && generation == d.generation && flags == d.flags;
	}

	@Override
	public int hashCode() {
		return ((lambang * 31 + motto.hashCode()) * 31 + Arrays.hashCode(robe)) * 31 + Arrays.hashCode(trim) * 7 + Arrays.hashCode(cut)
				+ generation * 131 + flags;
	}

	// ================================================================== wire (both payloads)

	public void write(FriendlyByteBuf buf) {
		buf.writeInt(lambang);
		buf.writeUtf(motto, MOTTO_MAX * 4);
		buf.writeBytes(robe);
		buf.writeBytes(trim);
		buf.writeBytes(cut);
		buf.writeByte(generation);
		buf.writeByte(flags);
	}

	public static SeragamData read(FriendlyByteBuf buf) {
		int lambang = buf.readInt();
		String motto = buf.readUtf(MOTTO_MAX * 4);
		byte[] robe = new byte[RANKS];
		byte[] trim = new byte[RANKS];
		byte[] cut = new byte[RANKS];
		buf.readBytes(robe);
		buf.readBytes(trim);
		buf.readBytes(cut);
		int generation = buf.readUnsignedByte();
		int flags = buf.readUnsignedByte();
		return new SeragamData(lambang, motto, robe, trim, cut, generation, flags);
	}
}
