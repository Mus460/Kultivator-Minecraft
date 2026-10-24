package dev.kultivator.client.sosok.kulit;

import dev.kultivator.Kultivator;
import dev.kultivator.sosok.Sosok;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The ready fallback atlases of the Sosok composer (DESIGN 40.3.9, 40.6.1; contract C40.K1; module 40K):
 * {@code textures/sosok/cadangan/<l|p>_<usia 0-3>_<kelas>.png}, 48 real composed looks (two sexes, four ages, six
 * classes) written by {@code tools/gen_sosok_cadangan.py}. A body shows one while its own atlas waits for the frame
 * budget, beyond the silhouette distance, and always in the tournament crowd; DESIGN 38 impostors draw them too (same
 * region A layout as every Sosok atlas, so a far body looks like its near self). Never a vanilla skin, never a plain box.
 */
public final class KulitCadangan {

	/** The classes, in {@code SosokRender.KELAS} order. */
	public static final String[] KELAS = {"sipil", "kultivator", "penjaga", "rahib", "bangsawan", "iblis"};

	private static final Identifier[][][] ID = new Identifier[2][4][KELAS.length];
	private static final boolean[][][] CEK = new boolean[2][4][KELAS.length];

	/**
	 * The fallback atlas of a sex ({@code Sosok.LAKI/PEREMPUAN}), age (0-3) and class; an unknown class reads as
	 * {@code sipil}. Null only when the texture is missing from the resources (checked once per atlas).
	 */
	public static @Nullable Identifier untuk(int kelamin, int usia, String kelas) {
		int k = kelamin == Sosok.PEREMPUAN ? 1 : 0;
		int u = Math.max(0, Math.min(3, usia));
		int c = 0;
		for (int i = 0; i < KELAS.length; i++) {
			if (KELAS[i].equals(kelas)) {
				c = i;
				break;
			}
		}
		if (!CEK[k][u][c]) {
			CEK[k][u][c] = true;
			Identifier id = Kultivator.id("textures/sosok/cadangan/" + (k == 1 ? "p" : "l") + "_" + u + "_" + KELAS[c] + ".png");
			if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) {
				ID[k][u][c] = id;
			} else {
				Kultivator.LOGGER.warn("[Sosok] fallback atlas {} missing", id);
			}
		}
		return ID[k][u][c];
	}

	private KulitCadangan() {
	}
}
