package dev.kultivator.client.sosok.kulit;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kultivator.Kultivator;
import dev.kultivator.client.sosok.SosokDetail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The atlas cache of the Sosok composer (DESIGN 40.3.9, 40.6.1, contract C40.K1; module 40K): one
 * {@code DynamicTexture} {@code kultivator:sosok/<16 hex>} per distinct look ({@link SosokRupa#kunci}), least recently
 * used first out when the cache holds more than the detail level allows (Tinggi 96, Sedang 64, Rendah 32 atlases,
 * 128 KiB each for GPU + RAM). Bodies with the same look share one atlas (the JELMAAN body of a disciple in its
 * travelling outfit uses the disciple's travelling atlas).
 * <p>
 * {@link #atlas} composes on the spot when the look is new; the caller (40R {@code SosokRender}) asks only within the
 * per-frame budget and shows the class fallback atlas ({@link KulitCadangan}) meanwhile. Every texture is released when
 * it leaves the cache, when the world closes and on {@code /sosok muat_ulang} ({@link #lepasSemua}, which also forgets
 * the recipe and the part sheets). Client thread only.
 */
public final class KulitCache {

	private record Entri(Identifier id, DynamicTexture tekstur) {
	}

	private static final LinkedHashMap<Long, Entri> CACHE = new LinkedHashMap<>(128, 0.75F, true);
	private static long susunJumlah;
	private static long susunNanoTotal;
	private static long susunNanoMaks;
	private static boolean rusak;

	/**
	 * The atlas of {@code rupa}: from the cache, else composed and registered now. Null when composing failed (the
	 * caller keeps the fallback; a failure turns composing off until {@link #lepasSemua}).
	 */
	public static @Nullable Identifier atlas(SosokRupa rupa) {
		Entri e = CACHE.get(rupa.kunci());
		if (e != null) {
			return e.id();
		}
		if (rusak) {
			return null;
		}
		long t0 = System.nanoTime();
		NativeImage img;
		try {
			img = KulitPenyusun.susun(rupa);
		} catch (RuntimeException ex) {
			rusak = true;
			Kultivator.LOGGER.error("[Sosok] composing atlas {} failed; fallback atlases until reload", Long.toHexString(rupa.kunci()), ex);
			return null;
		}
		Identifier id = Kultivator.id("sosok/" + String.format(Locale.ROOT, "%016x", rupa.kunci()));
		DynamicTexture tex = new DynamicTexture(() -> "kultivator sosok " + id.getPath(), img);
		Minecraft.getInstance().getTextureManager().register(id, tex);
		CACHE.put(rupa.kunci(), new Entri(id, tex));
		long dt = System.nanoTime() - t0;
		susunJumlah++;
		susunNanoTotal += dt;
		susunNanoMaks = Math.max(susunNanoMaks, dt);
		buang(SosokDetail.tingkat().atlasMaks);
		return id;
	}

	/** True when the atlas of {@code rupa} is ready (no composing). */
	public static boolean ada(SosokRupa rupa) {
		return CACHE.containsKey(rupa.kunci());
	}

	/** Releases the least recently used atlases above {@code maks}. */
	private static void buang(int maks) {
		Iterator<Map.Entry<Long, Entri>> it = CACHE.entrySet().iterator();
		while (CACHE.size() > maks && it.hasNext()) {
			Entri e = it.next().getValue();
			it.remove();
			Minecraft.getInstance().getTextureManager().release(e.id());
		}
	}

	/** Releases every atlas and forgets the recipe and the part sheets (world closed, {@code /sosok muat_ulang}). */
	public static void lepasSemua() {
		Minecraft mc = Minecraft.getInstance();
		for (Entri e : CACHE.values()) {
			mc.getTextureManager().release(e.id());
		}
		CACHE.clear();
		rusak = false;
		susunJumlah = 0;
		susunNanoTotal = 0;
		susunNanoMaks = 0;
		BagianCache.lepas();
		Resep.lepas();
	}

	/** Atlases alive now. */
	public static int jumlah() {
		return CACHE.size();
	}

	/** Atlases composed since the last {@link #lepasSemua}. */
	public static long jumlahSusun() {
		return susunJumlah;
	}

	/** Mean composing time in milliseconds (0 before the first). */
	public static float rataSusunMs() {
		return susunJumlah == 0 ? 0.0F : susunNanoTotal / (float) susunJumlah / 1_000_000.0F;
	}

	/** Longest composing time in milliseconds. */
	public static float maksSusunMs() {
		return susunNanoMaks / 1_000_000.0F;
	}

	/** Approximate memory of the cache in MiB (64 KiB on the GPU + 64 KiB kept by the DynamicTexture). */
	public static float mib() {
		return CACHE.size() * 128.0F / 1024.0F;
	}

	private KulitCache() {
	}
}
