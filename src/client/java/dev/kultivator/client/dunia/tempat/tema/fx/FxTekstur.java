package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.Kultivator;
import net.minecraft.resources.Identifier;

/**
 * The textures of the themed-town effects, all made by {@code tools/gen_tempat_tema_fx.py} under
 * {@code textures/dunia/tempat/tema/} (layout documented in the generator), and the cells of the 4 × 4 atlas
 * {@code fx.png} (64 px each). Cell uv are returned as {u0, v0, u1, v1} with a half-texel inset so filtering never
 * bleeds the neighbour cell.
 */
public final class FxTekstur {

	public static final Identifier FX = Kultivator.id("textures/dunia/tempat/tema/fx.png");
	public static final Identifier AWAN = Kultivator.id("textures/dunia/tempat/tema/awan.png");
	public static final Identifier KABUT = Kultivator.id("textures/dunia/tempat/tema/kabut.png");
	public static final Identifier PASIR = Kultivator.id("textures/dunia/tempat/tema/pasir.png");
	public static final Identifier DINDING_PASIR = Kultivator.id("textures/dunia/tempat/tema/dinding_pasir.png");
	public static final Identifier OMBAK = Kultivator.id("textures/dunia/tempat/tema/ombak.png");
	public static final Identifier PENDAYUNG = Kultivator.id("textures/dunia/tempat/tema/pendayung.png");
	/**
	 * The mural atlas (4 × 8 cells of 128 × 64): cell {@code 2 * i + f} = story {@code i} (0..11) frame {@code f};
	 * 24-27 the player's mural by icon; 28-31 the buried murals of Reruntuhan Gurun. Also the picture slot ("gambar") of
	 * the {@code tema_mural} screen: slot = story index.
	 */
	public static final Identifier MURAL = Kultivator.id("textures/dunia/tempat/tema/mural.png");

	/** fx.png cells, column + 4 × row. */
	public static final int GLOW = 0;
	public static final int CINCIN = 1;
	public static final int SINAR = 2;
	public static final int KILAU = 3;
	public static final int GORES = 4;
	public static final int DEBU = 5;
	public static final int TERATAI = 6;
	public static final int API = 7;
	public static final int ATAS = 8;
	public static final int TALI = 9;
	public static final int RIAK = 10;
	public static final int SILUET = 11;
	public static final int PETASAN = 12;
	public static final int KABUT_PUFF = 13;
	public static final int PANTUL = 14;
	public static final int PELANGI = 15;

	private static final float[][] SEL = new float[16][];
	private static final float INSET = 0.5F / 256.0F;

	static {
		for (int i = 0; i < 16; i++) {
			float u0 = (i % 4) * 0.25F;
			float v0 = (i / 4) * 0.25F;
			SEL[i] = new float[] {u0 + INSET, v0 + INSET, u0 + 0.25F - INSET, v0 + 0.25F - INSET};
		}
	}

	/** {u0, v0, u1, v1} of fx.png cell {@code sel} (shared array: read only). */
	static float[] sel(int sel) {
		return SEL[sel];
	}

	/** {u0, v0, u1, v1} of mural cell {@code idx} (0..31). */
	static float[] mural(int idx, float[] out) {
		int i = Math.floorMod(idx, 32);
		float u0 = (i % 4) * 0.25F;
		float v0 = (i / 4) * 0.125F;
		out[0] = u0 + 0.5F / 512.0F;
		out[1] = v0 + 0.5F / 512.0F;
		out[2] = u0 + 0.25F - 0.5F / 512.0F;
		out[3] = v0 + 0.125F - 0.5F / 512.0F;
		return out;
	}

	private FxTekstur() {
	}
}
