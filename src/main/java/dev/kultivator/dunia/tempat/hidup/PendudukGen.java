package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.kompleks.RencanaKompleks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The residents of a place (DESIGN 38.N.1 {@code PendudukGen.untuk}, numbers of 38.N.7), generated deterministically from
 * {@code (situsId, world seed)} the first time the place is loaded: the same place always holds the same people with the
 * same roles, looks, voices, households and work spots. Only changes are saved ({@link PendudukData}). A role mix per
 * kind of place: fixed service posts first (one prince, sixteen palace guards, the inn's cook), then the remaining
 * places drawn by weight (townsfolk, porters, children, elders). Children and elders join a working household, so a
 * house holds a family; market-day guests ({@link Penduduk#TAMU}) come to villages and trading posts once a week.
 *
 * <p>Kinds without residents here: caves and sites (38GU drives its hermits, ghosts and captives), road markers, bandit
 * camps, crossings, the Immortal City and the sky palaces (their own spawners).
 */
public final class PendudukGen {

	/** A role mix: fixed counts, then weights for the rest up to {@link #jumlah}. */
	private record Campuran(int jumlah, Map<NpcRole, Integer> tetap, Map<NpcRole, Integer> bobot, int tamu) {
	}

	private static final class Pembangun {
		private final Map<NpcRole, Integer> tetap = new EnumMap<>(NpcRole.class);
		private final Map<NpcRole, Integer> bobot = new EnumMap<>(NpcRole.class);
		private int tamu;

		Pembangun tetap(NpcRole role, int n) {
			tetap.merge(role, n, Integer::sum);
			return this;
		}

		Pembangun bobot(NpcRole role, int w) {
			bobot.merge(role, w, Integer::sum);
			return this;
		}

		Pembangun tamu(int n) {
			tamu = n;
			return this;
		}

		Campuran jumlah(int n) {
			return new Campuran(n, Map.copyOf(tetap), Map.copyOf(bobot), tamu);
		}
	}

	private static Pembangun mix() {
		return new Pembangun();
	}

	private static final Map<String, Campuran> CAMPURAN = new HashMap<>();

	static {
		// ---------------------------------------------------------------- the capital (650 residents, 38.N.7)
		CAMPURAN.put("kota/kota_besar", mix()
				.tetap(NpcRole.PANGERAN_KOTA, 1).tetap(NpcRole.MENTERI, 6).tetap(NpcRole.PENGAWAL_ISTANA, 16).tetap(NpcRole.KAPTEN_PENJAGA, 3)
				.tetap(NpcRole.PENJAGA_GERBANG, 12).tetap(NpcRole.PENJAGA, 20).tetap(NpcRole.HAKIM, 1).tetap(NpcRole.BANKIR, 2)
				.tetap(NpcRole.BANDAR_BATU, 2).tetap(NpcRole.PEMOTONG_BATU, 4).tetap(NpcRole.PEDAGANG_BINATANG, 3)
				.tetap(NpcRole.PEDAGANG_SENJATA, 4).tetap(NpcRole.EMPU, 4).tetap(NpcRole.ALKEMIS, 4).tetap(NpcRole.KETUA_GILDA, 3)
				.tetap(NpcRole.PETUGAS_GILDA, 4).tetap(NpcRole.PENGUJI, 2).tetap(NpcRole.PENJAGA_FORMASI, 2).tetap(NpcRole.NAKHODA, 3)
				.tetap(NpcRole.PEMILIK_PENGINAPAN, 4).tetap(NpcRole.KOKI, 10).tetap(NpcRole.PUSTAKAWAN, 3).tetap(NpcRole.PENCERAMAH_FANA, 2)
				.tetap(NpcRole.RAHIB, 8).tetap(NpcRole.TABIB, 4).tetap(NpcRole.TUKANG, 8).tetap(NpcRole.PENJAGA_TEKA, 2)
				.tetap(NpcRole.JUARA_SEKTE, 4).tetap(NpcRole.PEDAGANG_PIL, 4).tetap(NpcRole.PEMILIK_TEH, 2)
				.bobot(NpcRole.WARGA, 30).bobot(NpcRole.KULI, 12).bobot(NpcRole.PELAYAN, 10).bobot(NpcRole.ANAK, 18)
				.bobot(NpcRole.TETUA_DESA, 10).bobot(NpcRole.PEDAGANG_KELILING, 8).bobot(NpcRole.PENGEMIS, 2).bobot(NpcRole.PETANI, 4)
				.jumlah(650));
		// ---------------------------------------------------------------- old cultivator towns given life (38.C.7)
		Campuran kota = mix().tetap(NpcRole.TABIB, 1).tetap(NpcRole.KOKI, 2).tetap(NpcRole.PEMILIK_PENGINAPAN, 1).tetap(NpcRole.TUKANG, 2)
				.tetap(NpcRole.RAHIB, 1).bobot(NpcRole.WARGA, 10).bobot(NpcRole.KULI, 3).bobot(NpcRole.ANAK, 5).bobot(NpcRole.TETUA_DESA, 3)
				.bobot(NpcRole.PELAYAN, 2).bobot(NpcRole.PEDAGANG_KELILING, 2).bobot(NpcRole.PETANI, 2).jumlah(40);
		CAMPURAN.put("kota/kota", kota);
		CAMPURAN.put("kota/kota_kultivator", kota);
		// ---------------------------------------------------------------- villages (6 / 18 / 36) and clan villages (30)
		Pembangun desa = mix().tetap(NpcRole.KEPALA_DESA, 1).tetap(NpcRole.PANDAI_DESA, 1).bobot(NpcRole.PETANI, 8).bobot(NpcRole.NELAYAN, 3)
				.bobot(NpcRole.ANAK, 5).bobot(NpcRole.TETUA_DESA, 3).bobot(NpcRole.PEDAGANG_KELILING, 1).bobot(NpcRole.TUKANG, 1);
		CAMPURAN.put("kampung/dusun", mix().tetap(NpcRole.KEPALA_DESA, 1).bobot(NpcRole.PETANI, 4).bobot(NpcRole.ANAK, 2)
				.bobot(NpcRole.TETUA_DESA, 1).bobot(NpcRole.NELAYAN, 1).jumlah(6));
		CAMPURAN.put("kampung/desa", desa.jumlah(18));
		CAMPURAN.put("kampung/desa_besar", mix().tetap(NpcRole.KEPALA_DESA, 1).tetap(NpcRole.PANDAI_DESA, 2).tetap(NpcRole.TABIB, 1)
				.tetap(NpcRole.RAHIB, 1).tetap(NpcRole.KOKI, 1).bobot(NpcRole.PETANI, 10).bobot(NpcRole.NELAYAN, 4).bobot(NpcRole.ANAK, 6)
				.bobot(NpcRole.TETUA_DESA, 4).bobot(NpcRole.PEDAGANG_KELILING, 2).bobot(NpcRole.TUKANG, 2).bobot(NpcRole.KULI, 1)
				.tamu(3).jumlah(36));
		CAMPURAN.put("kampung/klan", mix().tetap(NpcRole.PATRIARK_KLAN, 1).tetap(NpcRole.TETUA_KLAN, 3).tetap(NpcRole.TUAN_MUDA, 2)
				.tetap(NpcRole.KOKI, 1).tetap(NpcRole.PENJAGA, 2).bobot(NpcRole.MURID_KLAN, 8).bobot(NpcRole.PELAYAN, 3).bobot(NpcRole.PETANI, 3)
				.bobot(NpcRole.ANAK, 3).bobot(NpcRole.TETUA_DESA, 1).jumlah(30));
		// ---------------------------------------------------------------- road posts (4-8)
		CAMPURAN.put("pos/singgah", mix().tetap(NpcRole.PEMILIK_PENGINAPAN, 1).tetap(NpcRole.KOKI, 1).tetap(NpcRole.PELAYAN, 1)
				.tetap(NpcRole.KULI, 1).jumlah(4));
		CAMPURAN.put("pos/singgah_besar", mix().tetap(NpcRole.PEMILIK_PENGINAPAN, 1).tetap(NpcRole.KOKI, 1).tetap(NpcRole.PELAYAN, 2)
				.tetap(NpcRole.KULI, 1).tetap(NpcRole.PENJAGA, 2).tetap(NpcRole.TUKANG, 1).jumlah(8));
		CAMPURAN.put("pos/dagang", mix().tetap(NpcRole.PEMILIK_PENGINAPAN, 1).tetap(NpcRole.PEDAGANG_KELILING, 2).tetap(NpcRole.KULI, 2)
				.tetap(NpcRole.PENJAGA, 1).tamu(3).jumlah(6));
		CAMPURAN.put("pos/kuil_jalan", mix().tetap(NpcRole.RAHIB, 1).jumlah(1));
		// ---------------------------------------------------------------- themed cities (38.T.3.3-38.T.7.3)
		CAMPURAN.put("kota_tema/pelabuhan", mix().tetap(NpcRole.SYAHBANDAR, 1).tetap(NpcRole.PETUGAS_BEA, 3).tetap(NpcRole.NAKHODA_LAUT, 3)
				.tetap(NpcRole.PENJAGA_SUAR, 1).tetap(NpcRole.TUKANG_KAPAL, 5).tetap(NpcRole.PENJUAL_IKAN, 6).tetap(NpcRole.PEDAGANG_SEBERANG, 5)
				.tetap(NpcRole.PENDETA_LAUT, 2).tetap(NpcRole.PENJAGA, 8).bobot(NpcRole.PELAUT, 14).bobot(NpcRole.KULI, 8)
				.bobot(NpcRole.NELAYAN, 6).bobot(NpcRole.ANAK, 6).bobot(NpcRole.TETUA_DESA, 4).bobot(NpcRole.WARGA, 8).bobot(NpcRole.KOKI, 3)
				.jumlah(140));
		CAMPURAN.put("kota_tema/oasis", mix().tetap(NpcRole.TUAN_BENTENG, 1).tetap(NpcRole.KAFILAH_BASHI, 3).tetap(NpcRole.PENDETA_AIR, 2)
				.tetap(NpcRole.PELUKIS_GUA, 3).tetap(NpcRole.PENARI_GURUN, 4).tetap(NpcRole.PENGAWAL_GURUN, 10).bobot(NpcRole.PENUNTUN_UNTA, 8)
				.bobot(NpcRole.PEDAGANG_BAZAR, 12).bobot(NpcRole.WARGA, 8).bobot(NpcRole.ANAK, 6).bobot(NpcRole.TETUA_DESA, 4)
				.bobot(NpcRole.KOKI, 3).bobot(NpcRole.KULI, 4).jumlah(130));
		CAMPURAN.put("kota_tema/air", mix().tetap(NpcRole.NYONYA_SUTRA, 2).tetap(NpcRole.AHLI_TEH, 2).tetap(NpcRole.SASTRAWAN, 4)
				.tetap(NpcRole.PENJAGA, 6).bobot(NpcRole.PENENUN_SUTRA, 10).bobot(NpcRole.PEMELIHARA_ULAT, 6).bobot(NpcRole.PEMETIK_TEH, 10)
				.bobot(NpcRole.PENDAYUNG, 10).bobot(NpcRole.WARGA, 8).bobot(NpcRole.ANAK, 6).bobot(NpcRole.TETUA_DESA, 4).bobot(NpcRole.KOKI, 3)
				.bobot(NpcRole.NELAYAN, 4).jumlah(130));
		CAMPURAN.put("kota_tema/gunung", mix().tetap(NpcRole.KEPALA_BIARA, 1).tetap(NpcRole.PENJAGA_LIFT, 3).tetap(NpcRole.TABIB, 1)
				.bobot(NpcRole.BIKSU_GUNUNG, 16).bobot(NpcRole.KULI_GUNUNG, 10).bobot(NpcRole.PEMETIK_HERBA, 8).bobot(NpcRole.PEZIARAH, 10)
				.bobot(NpcRole.WARGA, 6).bobot(NpcRole.ANAK, 4).bobot(NpcRole.TETUA_DESA, 4).bobot(NpcRole.KOKI, 2).jumlah(110));
		CAMPURAN.put("kota_tema/iblis", mix().tetap(NpcRole.PENGUASA_IBLIS, 1).tetap(NpcRole.PENJAGA_KITAB_TERLARANG, 1)
				.tetap(NpcRole.JAGAL_ARENA, 2).tetap(NpcRole.MAKELAR_GELAP, 3).tetap(NpcRole.PENEGAK_IBLIS, 10).bobot(NpcRole.GLADIATOR_IBLIS, 8)
				.bobot(NpcRole.PEMBUNUH_BAYARAN, 4).bobot(NpcRole.WARGA_IBLIS, 30).bobot(NpcRole.PENGEMIS, 4).jumlah(140));
	}

	/** True when places of kind {@code jenis} have generated residents (NPC sect compounds included). */
	public static boolean dihuni(String jenis) {
		return jenis.equals(KOMPLEKS) || campuran(jenis) != null;
	}

	/** Site kind of the NPC sect compounds (38.C.3, {@code RencanaKompleks.JENIS}). */
	static final String KOMPLEKS = "sekte/kompleks";

	private static Campuran campuran(String jenis) {
		Campuran c = CAMPURAN.get(jenis);
		if (c != null) {
			return c;
		}
		if (jenis.startsWith("kampung/")) {
			return CAMPURAN.get("kampung/desa");
		}
		return null;
	}

	/**
	 * The size of an NPC sect compound: its gate band (38.C.3.3: small, middle and great compounds hold 40 / 80 / 120
	 * residents, 38.N.7), from the compound plan of 38DS; the place strength only when the plan does not know the site.
	 */
	private static int ukuranKompleks(long situsId, int kekuatan) {
		try {
			RencanaKompleks.Kompleks k = KompleksApi.kompleksSitus(situsId);
			if (k != null) {
				return switch (k.entry().band()) {
					case 0 -> 40;
					case 1 -> 80;
					default -> 120;
				};
			}
		} catch (RuntimeException e) {
			// the plan is unavailable (no server yet): fall back to the strength
		}
		return kekuatan <= 2 ? 40 : kekuatan == 3 ? 80 : 120;
	}

	/**
	 * An NPC sect compound of {@code n} residents: the elders and the summit's champions, the kitchen, the servants
	 * sweeping the courts, the healer, the alchemist and the smith of the sect, the gate guards, and the outer and inner
	 * disciples (two outer for each inner). The sect's uniform is put on by 38DS ({@code KompleksAnchors}).
	 */
	private static Campuran kompleks(int n) {
		return mix().tetap(NpcRole.TETUA_SEKTE, Math.max(2, n / 20)).tetap(NpcRole.KOKI, Math.max(2, n / 40)).tetap(NpcRole.PELAYAN_SEKTE, n / 10)
				.tetap(NpcRole.TABIB, 1).tetap(NpcRole.ALKEMIS, Math.max(1, n / 60)).tetap(NpcRole.EMPU, 1).tetap(NpcRole.PENJAGA, Math.max(2, n / 30))
				.tetap(NpcRole.PUSTAKAWAN, n >= 80 ? 1 : 0).bobot(NpcRole.MURID_SEKTE_LUAR, 6).bobot(NpcRole.MURID_SEKTE_DALAM, 3).jumlah(n);
	}

	/**
	 * The residents of place {@code situsId} of kind {@code jenis} and strength {@code kekuatan} in a world with seed
	 * {@code seed}; empty for kinds without residents.
	 */
	public static List<Penduduk> untuk(long situsId, String jenis, int kekuatan, long seed) {
		Campuran mix = jenis.equals(KOMPLEKS) ? kompleks(ukuranKompleks(situsId, kekuatan)) : campuran(jenis);
		if (mix == null) {
			return List.of();
		}
		long base = mixSeed(seed ^ situsId * 0x9E3779B97F4A7C15L ^ jenis.hashCode());
		List<NpcRole> roles = new ArrayList<>(mix.jumlah() + mix.tamu());
		for (NpcRole role : NpcRole.values()) {
			Integer n = mix.tetap().get(role);
			for (int i = 0; n != null && i < n && roles.size() < mix.jumlah(); i++) {
				roles.add(role);
			}
		}
		int total = 0;
		List<NpcRole> pool = new ArrayList<>();
		for (NpcRole role : NpcRole.values()) {
			Integer w = mix.bobot().get(role);
			if (w != null && w > 0) {
				pool.add(role);
				total += w;
			}
		}
		long r = base;
		while (roles.size() < mix.jumlah() && total > 0) {
			r = mixSeed(r + 0x632BE59BD9B4E019L);
			int pick = (int) Math.floorMod(r, (long) total);
			for (NpcRole role : pool) {
				pick -= mix.bobot().get(role);
				if (pick < 0) {
					roles.add(role);
					break;
				}
			}
		}
		// households: every working adult founds or joins one; children and elders join the working households in turn
		List<Penduduk> out = new ArrayList<>(roles.size() + mix.tamu());
		Map<String, Integer> kerjaSlot = new HashMap<>();
		int rumah = 0;
		int dewasa = 0;
		int[] household = new int[roles.size()];
		for (int i = 0; i < roles.size(); i++) {
			NpcRole role = roles.get(i);
			boolean ikut = role.kelompok == NpcRole.Kelompok.ANAK || role.kelompok == NpcRole.Kelompok.TETUA && role != NpcRole.KEPALA_DESA;
			if (ikut && dewasa > 0) {
				household[i] = 1 + (int) Math.floorMod(mixSeed(base + i), (long) dewasa);
			} else {
				// two working adults share a house half the time (a couple), otherwise a new house
				boolean pasangan = dewasa > 0 && (mixSeed(base ^ i * 31L) & 1L) == 0L && (dewasa & 1) == 1;
				if (!pasangan) {
					rumah++;
				}
				household[i] = rumah;
				dewasa = rumah;
			}
		}
		for (int i = 0; i < roles.size(); i++) {
			out.add(buat(i, roles.get(i), household[i], base, kerjaSlot));
		}
		for (int g = 0; g < mix.tamu(); g++) {
			int id = roles.size() + g;
			Penduduk p = buat(id, NpcRole.PEDAGANG_KELILING, 0, base, kerjaSlot);
			out.add(new Penduduk(p.id(), p.peran(), p.look(), p.suara(), Penduduk.TANPA, Penduduk.TANPA, p.kerja(), Penduduk.TAMU, p.benih()));
		}
		return List.copyOf(out);
	}

	private static Penduduk buat(int id, NpcRole role, int household, long base, Map<String, Integer> kerjaSlot) {
		long benih = mixSeed(base + id * 0xD1B54A32D192ED03L);
		int look = (int) (benih ^ benih >>> 32);
		int suara = (int) Math.floorMod(benih >>> 40, 6L);
		String titik = KerjaPeran.untuk(role, benih).titik();
		int kerja = kerjaSlot.merge(titik, 1, Integer::sum) - 1;
		return new Penduduk(id, role, look, suara, household, id, kerja, household, benih);
	}

	/** How many residents share work titik type {@code titik} in this list (the slot modulus of {@link TempatHuni}). */
	public static Map<String, Integer> hitungKerja(List<Penduduk> residents) {
		Map<String, Integer> out = new HashMap<>();
		for (Penduduk p : residents) {
			out.merge(KerjaPeran.untuk(p.peran(), p.benih()).titik(), 1, Integer::sum);
		}
		return out;
	}

	/** SplitMix64 finaliser (stable across Java versions; never {@code Random}). */
	static long mixSeed(long z) {
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		return z ^ z >>> 31;
	}

	private PendudukGen() {
	}
}
