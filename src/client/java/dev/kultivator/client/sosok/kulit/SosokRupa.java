package dev.kultivator.client.sosok.kulit;

import org.jetbrains.annotations.Nullable;

/**
 * A resolved look (DESIGN 40.3.9, contract C40.K1): every part choice of one body and its 16 slot colours, made by
 * {@link RupaResolver#dari} and painted by {@link KulitPenyusun}. Immutable; {@link #kunci} is the atlas cache key, a
 * FNV-1a 64 over every field that changes a pixel ({@code sosok_rupa.kunci} in Python gives the same number for the
 * same look; the sect emblem stamp is added only when a uniform emblem is painted, which the Python previews never do).
 *
 * @param kelamin     {@code Sosok.LAKI} / {@code Sosok.PEREMPUAN}
 * @param usia        {@code Sosok.ANAK..TUA}
 * @param ramping     slim (3 px) arms
 * @param benih       the body's seed (the cloth noise uses its low 16 bits)
 * @param palet       16 RGB slot colours (40.3.3)
 * @param lapisan     the painted layers in 40.3.9 order
 * @param kubusRambut bit i = hair cube {@code BusanaKatalog.RAMBUT[i]} is shown
 * @param kubusBusana bit i = garment cube {@code BusanaKatalog.BUSANA[i]} is shown
 * @param varian      model variants ({@code RupaResolver.V_*})
 * @param bertopi     a covering hat: hair volume under it is hidden and the hat-variant hair sheet is painted
 * @param kelas       fallback atlas class (sipil, kultivator, penjaga, rahib, bangsawan, iblis)
 * @param busana      garment style id ({@code HANFU_L} ...)
 * @param rambut      hair style id ({@code L1} ... {@code A2})
 * @param mata        eye shape id
 * @param prop        idle hand prop ({@code PropId}, 0 none)
 * @param sikap       idle stance clip ({@code GerakId} 108-121, 0 = the walk profile's default)
 * @param jalan       walk profile id ({@code ProfilJalan}, "" default)
 * @param lambang     the sect emblem to stamp on the back/chest, or null
 */
public record SosokRupa(int kelamin, int usia, boolean ramping, int benih, int[] palet, Lapisan[] lapisan, int kubusRambut, long kubusBusana,
						int varian, boolean bertopi, String kelas, String busana, String rambut, String mata, int prop, int sikap, String jalan,
						@Nullable LambangSalin.Stempel lambang, long kunci) {

	/** A layer kind: an ordinary rectangle, or a face part whose source column follows the eye/mouth state. */
	public static final int BIASA = 0;
	public static final int MATA = 1;
	public static final int ALIS = 2;
	public static final int MULUT = 3;
	static final String[] NAMA_JENIS = {"biasa", "mata", "alis", "mulut"};

	/**
	 * One painted layer: sheet {@code textures/sosok/bagian/<lembar>.png}, source rectangle and destination. For
	 * {@link #MATA}/{@link #ALIS}/{@link #MULUT} the sheet is 128 x 8 with state {@code i} at x {@code 8i} and only
	 * {@code dx, dy} are meaningful.
	 */
	public record Lapisan(int jenis, String lembar, int sx, int sy, int w, int h, int dx, int dy) {

		static Lapisan biasa(String lembar, int sx, int sy, int w, int h, int dx, int dy) {
			return new Lapisan(BIASA, lembar, sx, sy, w, h, dx, dy);
		}

		static Lapisan wajah(int jenis, String lembar, int dx, int dy) {
			return new Lapisan(jenis, lembar, 0, 0, 8, 8, dx, dy);
		}
	}

	/** True when hair cube {@code i} of {@code BusanaKatalog.RAMBUT} is shown. */
	public boolean rambutAda(int i) {
		return (kubusRambut >>> i & 1) != 0;
	}

	/** True when garment cube {@code i} of {@code BusanaKatalog.BUSANA} is shown. */
	public boolean busanaAda(int i) {
		return (kubusBusana >>> i & 1L) != 0L;
	}

	public boolean punya(int varianBit) {
		return (varian & varianBit) != 0;
	}

	/** The cache key of these fields (same order as {@code sosok_rupa.kunci}); the stamp is appended when present. */
	static long hitungKunci(int kelamin, int usia, boolean ramping, int benih, int[] palet, Lapisan[] lapisan, int kubusRambut,
			long kubusBusana, @Nullable LambangSalin.Stempel lambang) {
		long h = SplitMix64.FNV64_AWAL;
		h = SplitMix64.fnv64(h, kelamin);
		h = SplitMix64.fnv64(h, usia);
		h = SplitMix64.fnv64(h, ramping ? 1 : 0);
		h = SplitMix64.fnv64(h, benih & 0xFFFF);
		for (int c : palet) {
			h = SplitMix64.fnv64(h, c);
		}
		for (Lapisan l : lapisan) {
			h = SplitMix64.fnv64(h, SplitMix64.fnv32(NAMA_JENIS[l.jenis()] + ":" + l.lembar()));
			if (l.jenis() == BIASA) {
				h = SplitMix64.fnv64(h, l.sx());
				h = SplitMix64.fnv64(h, l.sy());
				h = SplitMix64.fnv64(h, l.w());
				h = SplitMix64.fnv64(h, l.h());
			}
			h = SplitMix64.fnv64(h, l.dx());
			h = SplitMix64.fnv64(h, l.dy());
		}
		h = SplitMix64.fnv64(h, kubusRambut);
		h = SplitMix64.fnv64(h, (int) kubusBusana);
		h = SplitMix64.fnv64(h, (int) (kubusBusana >>> 32));
		if (lambang != null) {
			h = SplitMix64.fnv64(h, lambang.tekstur().hashCode());
			h = SplitMix64.fnv64(h, lambang.stamp());
			h = SplitMix64.fnv64(h, (lambang.punggung() ? 1 : 0) | (lambang.dada() ? 2 : 0));
		}
		return h;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SosokRupa r && r.kunci == kunci;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(kunci);
	}
}
