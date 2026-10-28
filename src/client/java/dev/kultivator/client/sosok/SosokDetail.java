package dev.kultivator.client.sosok;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The detail level, the level of detail per body and the per-frame budgets of Sosok (DESIGN 40.6.1, 40.6.2; contract
 * C40.R2). Read by the renderers (40R), the skin composer (40K) and the animation controller (40G).
 * <p>
 * Settings ({@code config/kultivator-client.json} through {@link EffectQuality#get}/{@link EffectQuality#put}, the
 * {@code AuraSettings} pattern): {@code sosok_detail} = {@code otomatis} (default: Maksimal effects give Tinggi, Normal
 * Sedang, Minimal Rendah) / {@code tinggi} / {@code sedang} / {@code rendah}; {@code sosok_wajah} = true (expressions and
 * blinking); {@code sosok_jalan} = {@code cantik} (the player walk of {@code Langkah}) / {@code vanilla}; {@code sosok_jalan_kuat}
 * = 100 (percent, 50-150, the walk polish of players and NPCs). Minimal effects always force Rendah.
 * <p>
 * The LOD of a body comes from its camera distance with a 2-block hysteresis per entity, so it never flickers on a
 * border. Two counted budgets need "the N nearest": the bodies with full Gerak per frame and the bodies with springs. A
 * frame cannot sort before it draws, so every body reports its distance while it is extracted and the N-th smallest
 * distance of the previous frame becomes this frame's threshold (one sort of at most a few hundred floats per frame).
 * Client thread only.
 */
public final class SosokDetail {

	/** The detail level in effect. Every number is a row of the 40.6.1 table. */
	public enum Tingkat {
		TINGGI(96, 2, 2.0F, 20, 40, 72, 24, 12),
		SEDANG(64, 1, 1.5F, 16, 32, 56, 16, 8),
		RENDAH(32, 1, 1.0F, 12, 24, 40, 10, 4);

		/** Sosok atlases kept in the LRU cache. */
		public final int atlasMaks;
		/** Atlases that may be composed in one frame. */
		public final int susunPerFrame;
		/** Time budget of the composing in one frame (milliseconds). */
		public final float susunMs;
		/** Distances (blocks) of the PENUH, KURANG and SILUET levels; farther is JAUH. */
		public final int penuh;
		public final int kurang;
		public final int siluet;
		/** Bodies with full Gerak per frame (the nearest). */
		public final int gerakPenuhMaks;
		/** Bodies with springs per frame (the nearest of the PENUH ones). */
		public final int pegasMaks;

		Tingkat(int atlasMaks, int susunPerFrame, float susunMs, int penuh, int kurang, int siluet, int gerakPenuhMaks, int pegasMaks) {
			this.atlasMaks = atlasMaks;
			this.susunPerFrame = susunPerFrame;
			this.susunMs = susunMs;
			this.penuh = penuh;
			this.kurang = kurang;
			this.siluet = siluet;
			this.gerakPenuhMaks = gerakPenuhMaks;
			this.pegasMaks = pegasMaks;
		}

		/** Lang id ({@code kultivator.sosok.setelan.detail.<id>}). */
		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The stored choice: Otomatis follows the effect quality. */
	public enum Setelan {
		OTOMATIS("otomatis"), TINGGI("tinggi"), SEDANG("sedang"), RENDAH("rendah");

		private final String id;

		Setelan(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static Setelan byId(@Nullable String id) {
			for (Setelan s : values()) {
				if (s.id.equals(id)) {
					return s;
				}
			}
			return OTOMATIS;
		}
	}

	/**
	 * Level of detail of one body (40.6.1): PENUH every Gerak layer, springs, face, props and all cubes; KURANG clips
	 * sampled every second frame, big cubes only, no springs, face, idle gestures or eye gaze; SILUET the atlas body only
	 * (vanilla walk, frozen idle pose, no busana, face or prop layer); JAUH the class fallback atlas, no layers, never asks
	 * for a unique atlas.
	 */
	public enum Lod {
		PENUH, KURANG, SILUET, JAUH
	}

	/** Queue cap of bodies waiting for their atlas (the rest use the fallback atlas until their turn). */
	public static final int ANTREAN_MAKS = 64;
	/** Hysteresis of the LOD borders (blocks). */
	private static final float HISTERESIS = 2.0F;
	/** How long the {@code /sosok lod} labels and the {@code /sosok bingkai} boxes stay on (milliseconds). */
	public static final long TAMPIL_UJI_MS = 30_000L;

	private static final String DETAIL_KEY = "sosok_detail";
	private static final String WAJAH_KEY = "sosok_wajah";

	private static boolean loaded;
	private static Setelan setelan = Setelan.OTOMATIS;
	private static boolean wajah = true;
	private static final String JALAN_KEY = "sosok_jalan";
	private static final String JALAN_KUAT_KEY = "sosok_jalan_kuat";
	private static final int KUAT_MIN = 50;
	private static final int KUAT_MAKS = 150;
	private static final int KUAT_LANGKAH = 25;
	private static boolean jalanCantik = true;
	private static int jalanKuat = 100;

	/** Last LOD per entity id (byte = ordinal), for the hysteresis. */
	private static final Int2ByteOpenHashMap TERAKHIR = new Int2ByteOpenHashMap();

	// ---- per-frame bookkeeping (render thread == client thread)
	private static long frameKey = Long.MIN_VALUE;
	private static long frameNo;
	private static float[] jarak = new float[256];
	private static int jarakN;
	/** Squared-distance thresholds of this frame (from the previous frame's distances). */
	private static double ambangGerakSq = Double.MAX_VALUE;
	private static double ambangPegasSq = Double.MAX_VALUE;
	private static final int[] HITUNG = new int[Lod.values().length];
	private static final int[] HITUNG_LALU = new int[Lod.values().length];
	private static int gerakPenuhLalu;
	private static int gerakPenuh;
	private static int pegasLalu;
	private static int pegas;

	// ---- composing statistics (5-second window) and the queue
	private static final long JENDELA_NS = 5_000_000_000L;
	private static final int CATATAN = 512;
	private static final long[] SUSUN_KAPAN = new long[CATATAN];
	private static final long[] SUSUN_LAMA = new long[CATATAN];
	private static int susunIdx;
	private static int susunFrame;
	private static long susunFrameNs;
	private static int antrean;
	private static int antreanLalu;
	private static boolean penuhMenunggu;
	private static boolean penuhMenungguLalu;

	private static long lodSampai;
	private static long bingkaiSampai;

	// ================================================================ settings

	/** The level in effect: Minimal effects force Rendah; Otomatis maps Maksimal/Normal/Minimal to Tinggi/Sedang/Rendah. */
	public static Tingkat tingkat() {
		load();
		if (EffectQuality.minimal()) {
			return Tingkat.RENDAH;
		}
		return switch (setelan) {
			case TINGGI -> Tingkat.TINGGI;
			case SEDANG -> Tingkat.SEDANG;
			case RENDAH -> Tingkat.RENDAH;
			case OTOMATIS -> EffectQuality.maximal() ? Tingkat.TINGGI : Tingkat.SEDANG;
		};
	}

	public static Setelan setelan() {
		load();
		return setelan;
	}

	public static void setSetelan(Setelan value) {
		load();
		setelan = value;
		EffectQuality.put(DETAIL_KEY, new JsonPrimitive(value.id()));
	}

	/** Otomatis → Tinggi → Sedang → Rendah → Otomatis. */
	public static void putarSetelan() {
		setSetelan(Setelan.values()[(setelan().ordinal() + 1) % Setelan.values().length]);
	}

	/** "Ekspresi wajah": blinking, moods and talking mouths (off = a still face). */
	public static boolean wajah() {
		load();
		return wajah;
	}

	public static void setWajah(boolean on) {
		load();
		wajah = on;
		EffectQuality.put(WAJAH_KEY, new JsonPrimitive(on));
	}

	/** "Gaya jalan pemain": Cantik (the walk and run of {@code Langkah}) or Vanilla (plain vanilla walking for players). */
	public static boolean jalanPemain() {
		load();
		return jalanCantik;
	}

	/** Cantik ↔ Vanilla. */
	public static void putarJalan() {
		load();
		jalanCantik = !jalanCantik;
		EffectQuality.put(JALAN_KEY, new JsonPrimitive(jalanCantik ? "cantik" : "vanilla"));
	}

	/** "Kekuatan gerak jalan" in percent (50-150, steps of 25), for players and NPCs alike. */
	public static int jalanKuatPersen() {
		load();
		return jalanKuat;
	}

	/** The same as a multiplier (1 = as designed). */
	public static float jalanKuat() {
		return jalanKuatPersen() / 100.0F;
	}

	/** 50 → 75 → 100 → 125 → 150 → 50. */
	public static void putarJalanKuat() {
		load();
		jalanKuat = jalanKuat >= KUAT_MAKS ? KUAT_MIN : jalanKuat + KUAT_LANGKAH;
		EffectQuality.put(JALAN_KUAT_KEY, new JsonPrimitive(jalanKuat));
	}

	// ================================================================ level of detail

	/** The LOD stored in a render state by {@link SosokRender#ekstrak}; JAUH for a state Sosok did not touch. */
	public static Lod lod(EntityRenderState state) {
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		return k == null ? Lod.JAUH : k.lod;
	}

	/** True when the body in {@code state} gets springs this frame (PENUH and among the nearest). */
	public static boolean pegas(EntityRenderState state) {
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		return k != null && k.pegas;
	}

	/** True when the face may move (setting on, PENUH, not the shadow pass). */
	public static boolean wajahHidup(EntityRenderState state) {
		return wajah() && lod(state) == Lod.PENUH && !dev.kultivator.client.epic.ShaderCompat.shadowPass();
	}

	/**
	 * The LOD of entity {@code id} at {@code distSq} (squared blocks from the camera) with the 2-block hysteresis: a body
	 * only moves to a nearer level once it is 2 blocks inside that border, and to a farther one 2 blocks outside.
	 */
	static Lod hitung(int id, double distSq, Tingkat t) {
		float d = (float) Math.sqrt(distSq);
		byte last = TERAKHIR.getOrDefault(id, (byte) -1);
		Lod lod;
		if (last < 0) {
			lod = d <= t.penuh ? Lod.PENUH : d <= t.kurang ? Lod.KURANG : d <= t.siluet ? Lod.SILUET : Lod.JAUH;
		} else {
			lod = Lod.values()[last];
			// move outward while beyond the current border + hysteresis, inward while inside the nearer border - hysteresis
			while (lod != Lod.JAUH && d > batas(lod, t) + HISTERESIS) {
				lod = Lod.values()[lod.ordinal() + 1];
			}
			while (lod != Lod.PENUH && d < batas(Lod.values()[lod.ordinal() - 1], t) - HISTERESIS) {
				lod = Lod.values()[lod.ordinal() - 1];
			}
		}
		if (TERAKHIR.size() > 2048) {
			TERAKHIR.clear();
		}
		TERAKHIR.put(id, (byte) lod.ordinal());
		return lod;
	}

	private static float batas(Lod lod, Tingkat t) {
		return switch (lod) {
			case PENUH -> t.penuh;
			case KURANG -> t.kurang;
			case SILUET -> t.siluet;
			case JAUH -> Float.MAX_VALUE;
		};
	}

	// ================================================================ per-frame budgets

	/** Number of the frame being rendered (the atlas cache never evicts what this frame asked for). */
	public static long nomorFrame() {
		return frameNo;
	}

	/** Starts a frame's counting once per rendered frame (the first body extracted in a frame does it). */
	static void mulaiFrame() {
		long key = Minecraft.getInstance().getFrameTimeNs();
		if (key == frameKey) {
			return;
		}
		frameKey = key;
		frameNo++;
		Tingkat t = tingkat();
		ambangGerakSq = ambang(t.gerakPenuhMaks);
		ambangPegasSq = ambang(t.pegasMaks);
		jarakN = 0;
		System.arraycopy(HITUNG, 0, HITUNG_LALU, 0, HITUNG.length);
		Arrays.fill(HITUNG, 0);
		gerakPenuhLalu = gerakPenuh;
		gerakPenuh = 0;
		pegasLalu = pegas;
		pegas = 0;
		antreanLalu = antrean;
		antrean = 0;
		penuhMenungguLalu = penuhMenunggu;
		penuhMenunggu = false;
		susunFrame = 0;
		susunFrameNs = 0L;
	}

	/** The squared distance of the n-th nearest PENUH body of the previous frame (MAX when fewer were drawn). */
	private static double ambang(int n) {
		if (jarakN <= n) {
			return Double.MAX_VALUE;
		}
		float[] copy = Arrays.copyOf(jarak, jarakN);
		Arrays.sort(copy);
		return copy[n - 1];
	}

	/**
	 * Counts one extracted body and returns its effective LOD: a PENUH body beyond this frame's "nearest N" threshold
	 * drops to KURANG (full Gerak per frame is capped, 40.6.1).
	 */
	static Lod catat(Lod lod, double distSq) {
		if (lod == Lod.PENUH) {
			if (jarakN == jarak.length) {
				jarak = Arrays.copyOf(jarak, jarak.length * 2);
			}
			if (jarakN < 4096) {
				jarak[jarakN++] = (float) distSq;
			}
			if (distSq > ambangGerakSq) {
				lod = Lod.KURANG;
			} else {
				gerakPenuh++;
			}
		}
		HITUNG[lod.ordinal()]++;
		return lod;
	}

	/** True when a PENUH body at {@code distSq} also gets springs (the nearest of 12/8/4). */
	static boolean bolehPegas(double distSq) {
		if (distSq <= ambangPegasSq && pegas < tingkat().pegasMaks) {
			pegas++;
			return true;
		}
		return false;
	}

	/**
	 * True when one more body may ask for a not-yet-composed atlas this frame: under the per-frame count and time budget
	 * and the queue cap; bodies outside PENUH wait while a PENUH body waited last frame (nearest first).
	 */
	static boolean bolehSusun(Lod lod) {
		Tingkat t = tingkat();
		if (lod != Lod.PENUH && penuhMenungguLalu) {
			return false;
		}
		return susunFrame < t.susunPerFrame && susunFrameNs < (long) (t.susunMs * 1_000_000.0F);
	}

	/** Records one atlas request that could compose (its time counts toward the frame budget and the 5-s statistics). */
	static void catatSusun(long nanos) {
		susunFrame++;
		susunFrameNs += nanos;
		long now = Util.getNanos();
		SUSUN_KAPAN[susunIdx] = now;
		SUSUN_LAMA[susunIdx] = nanos;
		susunIdx = (susunIdx + 1) % CATATAN;
	}

	/** A body that wanted its own atlas got the fallback this frame (it is queued). */
	static void catatMenunggu(Lod lod) {
		if (antrean < ANTREAN_MAKS) {
			antrean++;
		}
		if (lod == Lod.PENUH) {
			penuhMenunggu = true;
		}
	}

	// ================================================================ statistics for /sosok info

	/** Bodies per LOD in the last complete frame. */
	public static int jumlahLod(Lod lod) {
		return HITUNG_LALU[lod.ordinal()];
	}

	public static int jumlahGerakPenuh() {
		return gerakPenuhLalu;
	}

	public static int jumlahPegas() {
		return pegasLalu;
	}

	public static int antrean() {
		return antreanLalu;
	}

	/** {average, max, count} of the composing requests in the last 5 seconds (milliseconds, milliseconds, n). */
	public static float[] statistikSusun() {
		long now = Util.getNanos();
		long sum = 0L;
		long max = 0L;
		int n = 0;
		for (int i = 0; i < CATATAN; i++) {
			if (SUSUN_KAPAN[i] != 0L && now - SUSUN_KAPAN[i] <= JENDELA_NS) {
				sum += SUSUN_LAMA[i];
				max = Math.max(max, SUSUN_LAMA[i]);
				n++;
			}
		}
		return new float[] {n == 0 ? 0.0F : sum / (float) n / 1_000_000.0F, max / 1_000_000.0F, n};
	}

	// ================================================================ test displays

	/** Turns the LOD labels above every body on for 30 s. */
	public static void nyalakanLabelLod() {
		lodSampai = Util.getMillis() + TAMPIL_UJI_MS;
	}

	public static boolean labelLod() {
		return Util.getMillis() < lodSampai;
	}

	/** Turns the cube outline boxes on for 30 s (40K's busana layer and 40R's body outline read it). */
	public static void nyalakanBingkai() {
		bingkaiSampai = Util.getMillis() + TAMPIL_UJI_MS;
	}

	public static boolean bingkai() {
		return Util.getMillis() < bingkaiSampai;
	}

	// ================================================================ housekeeping

	/** Forgets the per-entity hysteresis and the counters (world closed). */
	static void bersihkan() {
		TERAKHIR.clear();
		jarakN = 0;
		ambangGerakSq = Double.MAX_VALUE;
		ambangPegasSq = Double.MAX_VALUE;
		Arrays.fill(HITUNG, 0);
		Arrays.fill(HITUNG_LALU, 0);
		Arrays.fill(SUSUN_KAPAN, 0L);
		antrean = 0;
		antreanLalu = 0;
		penuhMenunggu = false;
		penuhMenungguLalu = false;
	}

	/** Drops the hysteresis of entities no longer drawn (every 200 ticks, with {@link SosokRender#rapikan}). */
	static void lupakan(int id) {
		TERAKHIR.remove(id);
	}

	/** Forgets the cache (the next read takes the file's values again). */
	static void reload() {
		loaded = false;
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		setelan = Setelan.byId(string(EffectQuality.get(DETAIL_KEY)));
		wajah = bool(EffectQuality.get(WAJAH_KEY), true);
		jalanCantik = !"vanilla".equals(string(EffectQuality.get(JALAN_KEY)));
		int kuat = 100;
		try {
			JsonElement e = EffectQuality.get(JALAN_KUAT_KEY);
			kuat = e == null || e.isJsonNull() ? 100 : e.getAsInt();
		} catch (RuntimeException exception) {
			kuat = 100;
		}
		jalanKuat = Math.max(KUAT_MIN, Math.min(KUAT_MAKS, kuat / KUAT_LANGKAH * KUAT_LANGKAH));
	}

	private static @Nullable String string(@Nullable JsonElement element) {
		try {
			return element == null || element.isJsonNull() ? null : element.getAsString();
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static boolean bool(@Nullable JsonElement element, boolean fallback) {
		try {
			return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private SosokDetail() {
	}
}
