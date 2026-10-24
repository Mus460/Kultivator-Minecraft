package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The terrain verdict of one themed-town candidate (DESIGN 38.T.2.2). Pure function of the world seed and noise: a 9 x 9
 * grid of samples 36 blocks apart in a +-144 box around the candidate centre, each with {@code WORLD_SURFACE_WG} and
 * {@code OCEAN_FLOOR_WG} ({@link PutusanTanah.Sumber}; water where the surface is above the floor), plus the biome at every
 * district centre. Worldgen ({@code PutusanTanah.Sumber.of(context)}) and the server ({@code Sumber.of(overworld)}) give the
 * same answer, so the nine or ten district starts of one town are born together or not at all.
 * <p>
 * Rules per theme (land spread and medians use land samples only, so a river bed never counts as relief):
 * <ul>
 * <li><b>pelabuhan</b>: one compass side whose three outer sample rows (27) are >= 80 % water with a sea floor at
 * {@code sea-20 .. sea-4}; the other six rows >= 85 % land, land median {@code sea+1 .. sea+14}, spread <= 14; land
 * district centres in {@code #kultivator:kota_tema/pelabuhan/darat}, sea centres in {@code .../pelabuhan/laut}.
 * The sea side becomes grid north ({@link Hasil#putar}).</li>
 * <li><b>oasis</b>: >= 7 of 9 district centres in {@code #kultivator:kota_tema/oasis}; <= 2 water samples; spread <= 16;
 * median {@code sea+2 .. sea+40}.</li>
 * <li><b>air</b>: all 9 centres in {@code #kultivator:kota_tema/air}; spread <= 6; median {@code sea+1 .. sea+6};
 * <= 12 water samples (a small river may cross, a lake may not).</li>
 * <li><b>gunung</b>: no water; one compass side toward which the three district rows (27 samples each) rise monotonically,
 * every step +10 .. +30 and +30 .. +60 in all; spread inside one row <= 18; centres in {@code #kultivator:kota_tema/gunung}
 * (the top row may also be {@code .../gunung/puncak}). The uphill side becomes grid north.</li>
 * <li><b>iblis</b>: >= 3000 from the world centre; >= 6 of 9 centres in {@code #kultivator:kota_tema/iblis}; <= 3 water
 * samples; spread <= 22; median {@code sea+1 .. sea+48}.</li>
 * </ul>
 * Results are cached per (seed, theme, base candidate) with at most {@link #CACHE_MAX} entries (cleared when full).
 */
public final class PutusanTema {

	/** Samples per side (odd). */
	public static final int SISI = 9;
	/** Distance between samples in blocks. */
	public static final int LANGKAH = 36;
	public static final int CACHE_MAX = 320;

	public static final TagKey<Biome> PELABUHAN_DARAT = tag("kota_tema/pelabuhan/darat");
	public static final TagKey<Biome> PELABUHAN_LAUT = tag("kota_tema/pelabuhan/laut");
	public static final TagKey<Biome> OASIS = tag("kota_tema/oasis");
	public static final TagKey<Biome> AIR = tag("kota_tema/air");
	public static final TagKey<Biome> GUNUNG = tag("kota_tema/gunung");
	public static final TagKey<Biome> GUNUNG_PUNCAK = tag("kota_tema/gunung/puncak");
	public static final TagKey<Biome> IBLIS = tag("kota_tema/iblis");

	/** Compass sides in quarter-turn order: index = the {@code Rotation} that turns grid north onto that side. */
	private static final String[] ARAH = {"utara", "timur", "selatan", "barat"};

	/**
	 * One verdict.
	 *
	 * @param layak  suitable terrain
	 * @param dasarY street floor height (lot floor at {@code dasarY - 1})
	 * @param putar  the terrain rotation 0..3 (pelabuhan: sea side, gunung: uphill side), or -1 when any rotation fits
	 * @param alasan short reason for {@code uji tema rencana} ("air laut 61 %", "selisih 19 > 14", "bioma 5/9", "ok")
	 * @param rinci  extra numbers for {@code uji tema putusan} (median, spread, row medians, water count)
	 */
	public record Hasil(boolean layak, int dasarY, int putar, String alasan, String rinci) {

		static Hasil tidak(String alasan, String rinci) {
			return new Hasil(false, 0, -1, alasan, rinci);
		}
	}

	/** The raw samples of one candidate (also printed by {@code uji tema putusan}). */
	public record Sampel(int pusatX, int pusatZ, int[] tanah, boolean[] air, int laut) {

		int i(int ix, int iz) {
			return ix * SISI + iz;
		}

		public int tanah(int ix, int iz) {
			return tanah[i(ix, iz)];
		}

		public boolean air(int ix, int iz) {
			return air[i(ix, iz)];
		}
	}

	private static final Map<Long, Hasil> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	/** The cached verdict of base candidate {@code (tema, index)} centred at {@code (x, z)}. */
	public static Hasil of(PutusanTanah.Sumber sumber, Tema tema, int index, int x, int z) {
		long key = sumber.seed() * 0x9E3779B97F4A7C15L ^ ((long) tema.ordinal() << 56) ^ ((long) index << 20) ^ (x * 31L + z);
		Hasil known = CACHE.get(key);
		if (known != null) {
			return known;
		}
		Hasil hasil;
		try {
			hasil = hitung(sumber, tema, x, z);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Themed town terrain verdict failed for {} #{}", tema.id(), index, exception);
			hasil = Hasil.tidak("galat", "");
		}
		if (CACHE.size() >= CACHE_MAX) {
			CACHE.clear();
		}
		CACHE.put(key, hasil);
		return hasil;
	}

	/** Forgets every cached verdict (config reload, {@code uji tema rencana segar}). */
	public static void lupakan() {
		CACHE.clear();
	}

	/** The 81 samples around {@code (x, z)} (not cached; {@code uji tema putusan} and {@link #hitung}). */
	public static Sampel sampel(PutusanTanah.Sumber sumber, int x, int z) {
		int n = SISI * SISI;
		int[] tanah = new int[n];
		boolean[] air = new boolean[n];
		int half = SISI / 2;
		for (int ix = 0; ix < SISI; ix++) {
			for (int iz = 0; iz < SISI; iz++) {
				int bx = x + (ix - half) * LANGKAH;
				int bz = z + (iz - half) * LANGKAH;
				int surface = sumber.surface(bx, bz);
				int floor = sumber.floor(bx, bz);
				tanah[ix * SISI + iz] = floor;
				air[ix * SISI + iz] = surface > floor;
			}
		}
		return new Sampel(x, z, tanah, air, sumber.generator().getSeaLevel());
	}

	/** Computes the verdict without the cache. */
	public static Hasil hitung(PutusanTanah.Sumber sumber, Tema tema, int x, int z) {
		Sampel s = sampel(sumber, x, z);
		return switch (tema) {
			case PELABUHAN -> pelabuhan(sumber, tema, s);
			case OASIS -> oasis(sumber, tema, s);
			case AIR -> air(sumber, tema, s);
			case GUNUNG -> gunung(sumber, tema, s);
			case IBLIS -> iblis(sumber, tema, s);
		};
	}

	// ------------------------------------------------------------------------------------------------ per theme

	private static Hasil pelabuhan(PutusanTanah.Sumber sumber, Tema tema, Sampel s) {
		int sea = s.laut();
		StringBuilder rinci = new StringBuilder();
		String terbaik = "tanpa laut";
		for (int arah = 0; arah < 4; arah++) {
			int lautOk = 0;
			int darat = 0;
			int darN = 0;
			int[] landHeights = new int[SISI * SISI];
			for (int ix = 0; ix < SISI; ix++) {
				for (int iz = 0; iz < SISI; iz++) {
					boolean luar = barisLuar(arah, ix, iz);
					if (luar) {
						int floor = s.tanah(ix, iz);
						if (s.air(ix, iz) && floor >= sea - 20 && floor <= sea - 4) {
							lautOk++;
						}
					} else {
						darN++;
						if (!s.air(ix, iz)) {
							landHeights[darat++] = s.tanah(ix, iz);
						}
					}
				}
			}
			rinci.append(ARAH[arah]).append(": laut ").append(lautOk).append("/27 darat ").append(darat).append('/').append(darN).append("; ");
			if (lautOk < 22) {
				terbaik = pilihAlasan(terbaik, "laut " + ARAH[arah] + " " + (lautOk * 100 / 27) + "%");
				continue;
			}
			if (darat * 100 < 85 * darN) {
				terbaik = pilihAlasan(terbaik, "darat " + (darat * 100 / darN) + "%");
				continue;
			}
			int[] h = Arrays.copyOf(landHeights, darat);
			Arrays.sort(h);
			int median = h[h.length / 2];
			int spread = h[h.length - 1] - h[0];
			if (median < sea + 1 || median > sea + 14) {
				terbaik = pilihAlasan(terbaik, "tinggi darat " + (median - sea));
				continue;
			}
			if (spread > 14) {
				terbaik = pilihAlasan(terbaik, "selisih " + spread + " > 14");
				continue;
			}
			// biomes: sea row (grid z = -1) toward this side, the land rows elsewhere
			Rotation r = Rotation.values()[arah];
			boolean biomOk = true;
			for (Tema.Distrik d : tema.distrik()) {
				int[] o = TataLetakStructure.rotate(r, d.x() * Tema.SEL_DISTRIK, d.z() * Tema.SEL_DISTRIK);
				int y = d.laut() ? sea - 1 : median;
				if (!sumber.biome(s.pusatX() + o[0], y, s.pusatZ() + o[1]).is(d.laut() ? PELABUHAN_LAUT : PELABUHAN_DARAT)) {
					biomOk = false;
					break;
				}
			}
			if (!biomOk) {
				terbaik = pilihAlasan(terbaik, "bioma " + ARAH[arah]);
				continue;
			}
			int dasar = Math.clamp(median, sea + 1, sea + 8);
			return new Hasil(true, dasar, arah, "ok laut " + ARAH[arah], rinci + "median " + (median - sea) + " selisih " + spread);
		}
		return Hasil.tidak(terbaik, rinci.toString());
	}

	private static Hasil oasis(PutusanTanah.Sumber sumber, Tema tema, Sampel s) {
		Umum u = umum(s);
		int sea = s.laut();
		String rinci = u.rinci(sea);
		if (u.air > 2) {
			return Hasil.tidak("air " + u.air + " > 2", rinci);
		}
		if (u.spread > 16) {
			return Hasil.tidak("selisih " + u.spread + " > 16", rinci);
		}
		if (u.median < sea + 2 || u.median > sea + 40) {
			return Hasil.tidak("tinggi " + (u.median - sea), rinci);
		}
		int ok = bioma(sumber, tema, s, u.median, OASIS, null);
		if (ok < 7) {
			return Hasil.tidak("bioma " + ok + "/9", rinci);
		}
		return new Hasil(true, u.median, -1, "ok", rinci);
	}

	private static Hasil air(PutusanTanah.Sumber sumber, Tema tema, Sampel s) {
		Umum u = umum(s);
		int sea = s.laut();
		String rinci = u.rinci(sea);
		if (u.air > 12) {
			return Hasil.tidak("air " + u.air + " > 12", rinci);
		}
		if (u.spread > 6) {
			return Hasil.tidak("selisih " + u.spread + " > 6", rinci);
		}
		if (u.median < sea + 1 || u.median > sea + 6) {
			return Hasil.tidak("tinggi " + (u.median - sea), rinci);
		}
		int ok = bioma(sumber, tema, s, u.median, AIR, null);
		if (ok < 9) {
			return Hasil.tidak("bioma " + ok + "/9", rinci);
		}
		return new Hasil(true, Math.clamp(u.median, sea + 1, sea + 4), -1, "ok", rinci);
	}

	private static Hasil gunung(PutusanTanah.Sumber sumber, Tema tema, Sampel s) {
		int sea = s.laut();
		int water = 0;
		for (boolean w : s.air()) {
			if (w) {
				water++;
			}
		}
		if (water > 0) {
			return Hasil.tidak("air " + water + " > 0", "");
		}
		StringBuilder rinci = new StringBuilder();
		String terbaik = "tidak menanjak";
		for (int arah = 0; arah < 4; arah++) {
			// row 0 = the top (toward the side), row 2 = the bottom
			int[] med = new int[3];
			int[] spr = new int[3];
			for (int row = 0; row < 3; row++) {
				int[] h = new int[27];
				int n = 0;
				for (int ix = 0; ix < SISI; ix++) {
					for (int iz = 0; iz < SISI; iz++) {
						if (barisKe(arah, ix, iz) / 3 == row) {
							h[n++] = s.tanah(ix, iz);
						}
					}
				}
				Arrays.sort(h, 0, n);
				med[row] = h[n / 2];
				spr[row] = h[n - 1] - h[0];
			}
			int naik1 = med[1] - med[2];
			int naik2 = med[0] - med[1];
			int total = med[0] - med[2];
			rinci.append(ARAH[arah]).append(": ").append(med[2] - sea).append('/').append(med[1] - sea).append('/').append(med[0] - sea)
					.append(" selisih ").append(spr[2]).append('/').append(spr[1]).append('/').append(spr[0]).append("; ");
			if (naik1 < 10 || naik1 > 30 || naik2 < 10 || naik2 > 30 || total < 30 || total > 60) {
				if (total > 0) {
					terbaik = pilihAlasan(terbaik, "naik " + naik1 + "+" + naik2 + " " + ARAH[arah]);
				}
				continue;
			}
			if (spr[0] > 18 || spr[1] > 18 || spr[2] > 18) {
				terbaik = pilihAlasan(terbaik, "selisih baris " + Math.max(spr[0], Math.max(spr[1], spr[2])) + " > 18");
				continue;
			}
			Rotation r = Rotation.values()[arah];
			boolean biomOk = true;
			for (Tema.Distrik d : tema.distrik()) {
				int[] o = TataLetakStructure.rotate(r, d.x() * Tema.SEL_DISTRIK, d.z() * Tema.SEL_DISTRIK);
				int y = med[d.z() + 1];
				var biome = sumber.biome(s.pusatX() + o[0], y, s.pusatZ() + o[1]);
				if (!biome.is(GUNUNG) && !(d.z() == -1 && biome.is(GUNUNG_PUNCAK))) {
					biomOk = false;
					break;
				}
			}
			if (!biomOk) {
				terbaik = pilihAlasan(terbaik, "bioma " + ARAH[arah]);
				continue;
			}
			if (med[2] < sea + 1) {
				terbaik = pilihAlasan(terbaik, "kaki terlalu rendah");
				continue;
			}
			return new Hasil(true, med[2], arah, "ok naik " + ARAH[arah], rinci.toString());
		}
		return Hasil.tidak(terbaik, rinci.toString());
	}

	private static Hasil iblis(PutusanTanah.Sumber sumber, Tema tema, Sampel s) {
		if (Math.hypot(s.pusatX(), s.pusatZ()) < 3000.0D) {
			return Hasil.tidak("terlalu dekat pusat", "");
		}
		Umum u = umum(s);
		int sea = s.laut();
		String rinci = u.rinci(sea);
		if (u.air > 3) {
			return Hasil.tidak("air " + u.air + " > 3", rinci);
		}
		if (u.spread > 22) {
			return Hasil.tidak("selisih " + u.spread + " > 22", rinci);
		}
		if (u.median < sea + 1 || u.median > sea + 48) {
			return Hasil.tidak("tinggi " + (u.median - sea), rinci);
		}
		int ok = bioma(sumber, tema, s, u.median, IBLIS, null);
		if (ok < 6) {
			return Hasil.tidak("bioma " + ok + "/9", rinci);
		}
		return new Hasil(true, u.median, -1, "ok", rinci);
	}

	// ------------------------------------------------------------------------------------------------ helpers

	private record Umum(int air, int median, int spread) {

		String rinci(int sea) {
			return "air " + air + " median " + (median - sea) + " selisih " + spread;
		}
	}

	private static Umum umum(Sampel s) {
		int water = 0;
		int[] h = new int[SISI * SISI];
		int n = 0;
		for (int i = 0; i < SISI * SISI; i++) {
			if (s.air()[i]) {
				water++;
			} else {
				h[n++] = s.tanah()[i];
			}
		}
		if (n == 0) {
			return new Umum(water, s.laut() - 10, 999);
		}
		Arrays.sort(h, 0, n);
		return new Umum(water, h[n / 2], h[n - 1] - h[0]);
	}

	/** How many of the nine core district centres (rotation 0, no oasis caravan field) are in {@code tag} (or {@code alt}). */
	private static int bioma(PutusanTanah.Sumber sumber, Tema tema, Sampel s, int y, TagKey<Biome> tag, @Nullable TagKey<Biome> alt) {
		int ok = 0;
		for (Tema.Distrik d : tema.distrik()) {
			if (Math.abs(d.z()) > 1) {
				continue;
			}
			var biome = sumber.biome(s.pusatX() + d.x() * Tema.SEL_DISTRIK, y, s.pusatZ() + d.z() * Tema.SEL_DISTRIK);
			if (biome.is(tag) || alt != null && biome.is(alt)) {
				ok++;
			}
		}
		return ok;
	}

	/** True when sample (ix, iz) is in the three outer rows toward side {@code arah} (0 north, 1 east, 2 south, 3 west). */
	static boolean barisLuar(int arah, int ix, int iz) {
		return barisKe(arah, ix, iz) < 3;
	}

	/** The sample row index counted from side {@code arah} inwards (0 = the outermost row on that side). */
	static int barisKe(int arah, int ix, int iz) {
		return switch (arah) {
			case 0 -> iz;
			case 1 -> SISI - 1 - ix;
			case 2 -> SISI - 1 - iz;
			default -> ix;
		};
	}

	private static String pilihAlasan(String lama, String baru) {
		return lama.startsWith("tanpa") || lama.startsWith("tidak") ? baru : lama;
	}

	/** The compass side name of a rotation ({@code utara}, {@code timur}, {@code selatan}, {@code barat}). */
	public static String arah(int putar) {
		return putar < 0 ? "-" : ARAH[putar & 3];
	}

	private static TagKey<Biome> tag(String path) {
		return TagKey.create(Registries.BIOME, Kultivator.id(path));
	}

	/** Debug summary of the verdict rules ({@code uji tema putusan} header). */
	public static Map<String, String> aturan() {
		Map<String, String> out = new LinkedHashMap<>();
		out.put("pelabuhan", "laut >=80% 3 baris, darat >=85%, median +1..+14, selisih <=14");
		out.put("oasis", "bioma >=7/9, air <=2, selisih <=16, median +2..+40");
		out.put("air", "bioma 9/9, air <=12, selisih <=6, median +1..+6");
		out.put("gunung", "naik 10..30 per baris, total 30..60, selisih baris <=18, tanpa air");
		out.put("iblis", ">=3000 dari pusat, bioma >=6/9, air <=3, selisih <=22");
		return out;
	}

	private PutusanTema() {
	}
}
