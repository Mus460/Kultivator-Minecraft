package dev.kultivator.client.sosok.kulit;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kultivator.Kultivator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The encoded part sheets of the Sosok composer (DESIGN 40.3.2, 40.3.9; module 40K):
 * {@code textures/sosok/bagian/<group>/<id>.png} and their fixed-colour partners {@code <id>_tetap.png}, read once on
 * first use ({@code NativeImage.read}) and kept as ARGB {@code int[]} (about 1.2 MiB for every sheet; loaded lazily,
 * one sheet at a time). A missing or unreadable sheet logs one line and is skipped by the composer, the atlas is still
 * made. Client thread only.
 */
public final class BagianCache {

	/** One sheet: size, ARGB pixels (A coverage, R tone, G slot x 16, B flags) and the fixed colours or null. */
	public record Lembar(int w, int h, int[] px, int[] tetap) {
	}

	private static final String AKAR = "textures/sosok/bagian/";
	private static final Map<String, Lembar> CACHE = new HashMap<>();
	private static final Set<String> HILANG = new HashSet<>();

	/** The sheet {@code textures/sosok/bagian/<rel>.png}, or null when missing (logged once). */
	public static @Nullable Lembar get(String rel) {
		Lembar l = CACHE.get(rel);
		if (l != null || HILANG.contains(rel)) {
			return l;
		}
		int[] px = null;
		int w = 0;
		int h = 0;
		try (NativeImage img = baca(rel)) {
			if (img != null) {
				w = img.getWidth();
				h = img.getHeight();
				px = img.getPixels();
			}
		} catch (Exception e) {
			Kultivator.LOGGER.warn("[Sosok] part sheet {} unreadable: {}", rel, e.toString());
		}
		if (px == null) {
			HILANG.add(rel);
			Kultivator.LOGGER.warn("[Sosok] part sheet {} missing, skipped", rel);
			return null;
		}
		int[] tetap = null;
		try (NativeImage img = baca(rel + "_tetap")) {
			if (img != null && img.getWidth() == w && img.getHeight() == h) {
				tetap = img.getPixels();
			}
		} catch (Exception e) {
			Kultivator.LOGGER.warn("[Sosok] fixed-colour sheet {}_tetap unreadable: {}", rel, e.toString());
		}
		l = new Lembar(w, h, px, tetap);
		CACHE.put(rel, l);
		return l;
	}

	private static @Nullable NativeImage baca(String rel) throws java.io.IOException {
		Identifier id = Kultivator.id(AKAR + rel + ".png");
		Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) {
			return null;
		}
		try (InputStream in = res.get().open()) {
			return NativeImage.read(in);
		}
	}

	/** Puts a sheet read outside the game (the scratch parity harness of 40K; never called by the mod). */
	static void isiUntukUji(String rel, @Nullable Lembar l) {
		if (l == null) {
			HILANG.add(rel);
		} else {
			CACHE.put(rel, l);
		}
	}

	/** Sheets held now. */
	public static int jumlah() {
		return CACHE.size();
	}

	/** Forgets every sheet (read again on next use: {@code /sosok muat_ulang}, world closed). */
	public static void lepas() {
		CACHE.clear();
		HILANG.clear();
	}

	private BagianCache() {
	}
}
