package dev.kultivator.sosok;

import dev.kultivator.dunia.kota.NpcRole;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Who a body is (DESIGN 40.2.2, 40.4): the sex and age policy of every look key, and the Sosok it gives a disciple or an
 * NPC. The server never reads {@code assets/}, so the policy columns K and U of the 40.4 tables live here in Java; 40K's
 * {@code tools/cek_sosok.py} compares them with {@code assets/kultivator/sosok/resep.json} by regex (one {@code baris(...)}
 * call per row, key first, so keep that form).
 * <p>
 * <b>NPCs</b> ({@link #untukNpc}): everything comes from the role and the look seed, so every existing spawner is right
 * without being edited. Sex: {@code L} = men only, {@code P} = women only, {@code C<p>} = women with chance {@code p} %,
 * read from look bits {@code (look >>> 24) & 0x7F} (0-127) {@code < p × 1.28}. Age: a fixed group or a mix read from
 * bits {@code (look >>> 2) & 0x7F} against the cumulative weights (out of 128). Build and seed come from
 * {@code h = Mth.murmurHash3Mixer(look)} (the fastutil {@code HashCommon.murmurHash3} mixer): build {@code h & 7}, seed
 * {@code (h >>> 6) & 0x3FFFFFF}. A look key without its own row falls back to the role's schedule group
 * ({@code kelompok.<GROUP>}), then to {@code kelompok.SIPIL}.
 * <p>
 * <b>Disciples</b> ({@link #untukMurid}): sex {@code nextDouble() < rasioPerempuan}, age youth 82 % / child 10 % /
 * adult 8 % ({@link SosokConfig}), never old; any build; a random 26-bit seed.
 */
public final class SosokGen {

	/**
	 * The policy of one look key: the women's share in percent (0 = men only, 100 = women only) and the four age weights
	 * (child, youth, adult, old) out of 128.
	 */
	public record Kebijakan(String kunci, int persenPerempuan, int anak, int muda, int dewasa, int tua) {

		/** True when the key allows only one sex. */
		public boolean kelaminTetap() {
			return persenPerempuan <= 0 || persenPerempuan >= 100;
		}

		/** The sex of a fixed policy, or -1 for a mixed one. */
		public int kelaminWajib() {
			return persenPerempuan <= 0 ? Sosok.LAKI : persenPerempuan >= 100 ? Sosok.PEREMPUAN : -1;
		}

		/** The age group whose weight is largest (the gallery's "default age"). */
		public int usiaUtama() {
			int best = Sosok.MUDA;
			int bestW = -1;
			int[] w = {anak, muda, dewasa, tua};
			for (int i = 0; i < 4; i++) {
				if (w[i] > bestW) {
					bestW = w[i];
					best = i;
				}
			}
			return best;
		}

		/** True when age group {@code usia} can occur. */
		public boolean bolehUsia(int usia) {
			return switch (usia) {
				case Sosok.ANAK -> anak > 0;
				case Sosok.MUDA -> muda > 0;
				case Sosok.DEWASA -> dewasa > 0;
				default -> tua > 0;
			};
		}

		/** The policy in the table's own notation ("C25 D70T30"), for the gallery and {@code cek_sosok.py}. */
		public String teks() {
			String k = persenPerempuan <= 0 ? "L" : persenPerempuan >= 100 ? "P" : "C" + persenPerempuan;
			StringBuilder u = new StringBuilder();
			char[] huruf = {'A', 'M', 'D', 'T'};
			int[] w = {anak, muda, dewasa, tua};
			for (int i = 0; i < 4; i++) {
				if (w[i] > 0) {
					u.append(huruf[i]).append(Math.round(w[i] * 100.0F / 128.0F));
				}
			}
			return k + " " + u;
		}
	}

	/** Every row, in the order of the 40.4 tables. */
	private static final Map<String, Kebijakan> BARIS = new LinkedHashMap<>();
	/** The NPC look keys of 40.4.1 A-D (the role gallery pages, ±125 looks). */
	private static final List<String> RUPA_NPC = new ArrayList<>();

	/** The disciple rows of 40.4.2 besides ranks 0-5 (keys for {@code RupaKunci} and 40K's recipe rows). */
	public static final String MURID_CALON = "murid.calon";
	public static final String MURID_SAINGAN = "murid.saingan";
	public static final String MURID_BAYANGAN = "murid.bayangan";
	public static final String MURID_KLON = "murid.klon";
	/** The last fallback of every key without its own row. */
	public static final String KELOMPOK_SIPIL = "kelompok.SIPIL";

	static {
		// ---------------------------------------------------------------- 40.4.1 A: the old towns (module K)
		npc("juru_lelang", 25, "D");
		npc("juru_lelang_abadi", 25, "D");
		npc("pemilik_teh", 55, "D70T30");
		npc("makelar_gelap", 30, "D");
		npc("pedagang_pil", 40, "D");
		npc("pedagang_abadi", 40, "D");
		npc("pandai_pusaka", 10, "D");
		npc("penjaga", 15, "M30D70");
		npc("warga", 50, "A15M20D45T20");
		npc("pengembara", 35, "M40D60");
		npc("saingan", 30, "M60D40");
		npc("abadi", 50, "M30D70");
		npc("penceramah", 30, "T");
		npc("utusan", 0, "D");
		npc("penjaga_kebun", 70, "M50D50");
		// ---------------------------------------------------------------- B: capital, guilds, villages, clans, compounds, caves
		npc("pangeran_kota", 0, "D");
		npc("menteri", 20, "D60T40");
		npc("pengawal_istana", 20, "MD");
		npc("kapten_penjaga", 20, "D");
		npc("penjaga_gerbang", 15, "MD");
		npc("hakim", 15, "DT");
		npc("bankir", 35, "D");
		npc("bandar_batu", 25, "D");
		npc("pemotong_batu", 20, "MD");
		npc("pedagang_binatang", 30, "D");
		npc("pedagang_senjata", 30, "D");
		npc("empu", 10, "DT");
		npc("alkemis", 45, "DT");
		npc("ketua_gilda", 30, "DT");
		npc("petugas_gilda", 50, "MD");
		npc("penguji", 30, "D");
		npc("penjaga_formasi", 40, "D");
		npc("nakhoda", 20, "D");
		npc("kuli", 10, "MD");
		npc("pemilik_penginapan", 50, "D");
		npc("koki", 35, "D");
		npc("pelayan", 65, "M");
		npc("pustakawan", 40, "DT");
		npc("penceramah_fana", 30, "T");
		npc("rahib", 0, "MDT");
		npc("tabib", 50, "DT");
		npc("tukang", 10, "MD");
		npc("penjaga_teka", 60, "MD");
		npc("juara_sekte", 40, "MD");
		npc("kepala_desa", 0, "T");
		npc("petani", 40, "MDT");
		npc("nelayan", 25, "DT");
		npc("pandai_desa", 10, "D");
		npc("pedagang_keliling", 35, "D");
		npc("anak", 50, "A");
		npc("tetua_desa", 50, "T");
		npc("patriark_klan", 0, "T");
		npc("tuan_muda", 20, "M");
		npc("tetua_klan", 30, "T");
		npc("murid_klan", 40, "M");
		npc("murid_sekte_luar", 45, "M");
		npc("murid_sekte_dalam", 45, "MD");
		npc("tetua_sekte", 35, "DT");
		npc("ketua_sekte", 35, "DT");
		npc("pelayan_sekte", 55, "A20M80");
		// 42 town roles (42V-B looks): sex and age match their robes
		npc("pencopet", 25, "M50D50");
		npc("penjaga_malam", 10, "D50T50");
		npc("mak_comblang", 100, "D40T60");
		npc("pendongeng", 0, "T");
		npc("pengamen", 40, "MD");
		npc("akrobat", 45, "M");
		npc("penjual_obat", 20, "DT");
		npc("guru_sekolah", 30, "DT");
		npc("pengurus_duka", 0, "T");
		npc("penjaga_gudang", 40, "DT");
		npc("pialang", 25, "D");
		npc("penari", 85, "M");
		npc("pemusik", 20, "MD");
		baris("topeng_kertas", 35, "MD");
		npc("pertapa", 20, "T");
		npc("sisa_jiwa", 40, "DT");
		npc("tetua_iblis", 30, "DT");
		npc("tawanan", 45, "MD");
		npc("pengemis", 40, "A30T70");
		npc("pendeta_tao", 30, "DT");
		// ---------------------------------------------------------------- C: themed cities (38.T)
		npc("syahbandar", 20, "D");
		npc("petugas_bea", 30, "MD");
		npc("pelaut", 15, "MD");
		npc("nakhoda_laut", 20, "DT");
		npc("penjaga_suar", 30, "DT");
		npc("tukang_kapal", 10, "D");
		npc("penjual_ikan", 60, "D");
		npc("pedagang_seberang", 25, "D");
		npc("pendeta_laut", 50, "DT");
		npc("tuan_benteng", 0, "D");
		npc("kafilah_bashi", 20, "D");
		npc("penuntun_unta", 20, "MD");
		npc("pedagang_bazar", 45, "D");
		npc("pendeta_air", 55, "DT");
		npc("pelukis_gua", 40, "MD");
		npc("penari_gurun", 100, "M");
		npc("pengawal_gurun", 20, "MD");
		npc("nyonya_sutra", 100, "D");
		npc("penenun_sutra", 80, "MD");
		npc("pemelihara_ulat", 70, "MD");
		npc("ahli_teh", 50, "DT");
		npc("pemetik_teh", 75, "MD");
		npc("pendayung", 30, "MD");
		npc("sastrawan", 35, "MD");
		npc("kepala_biara", 0, "T");
		npc("biksu_gunung", 15, "MD");
		npc("penjaga_lift", 20, "D");
		npc("kuli_gunung", 10, "MD");
		npc("pemetik_herba", 45, "MD");
		npc("peziarah", 50, "DT");
		npc("penguasa_iblis", 30, "D");
		npc("penegak_iblis", 25, "D");
		npc("jagal_arena", 10, "D");
		npc("gladiator_iblis", 30, "MD");
		npc("penjaga_kitab_terlarang", 40, "T");
		npc("pembunuh_bayaran", 40, "MD");
		npc("warga_iblis", 50, "A10M30D45T15");
		npc("pemburu_lurus", 35, "MD");
		// ---------------------------------------------------------------- D: Dimension D, Rogue Cultivators, envoy, spectators
		npc("d.meng_po", 100, "T");
		npc("d.hakim", 0, "DT");
		npc("d.raja_yama", 0, "D");
		npc("d.arwah", 50, "AMDT");
		npc("d.penjaga_laut", 25, "MD");
		npc("d.pertapa_laut", 0, "T");
		npc("d.dewa_penglai", 40, "D");
		npc("d.nelayan", 20, "DT");
		npc("d.pedagang", 40, "D");
		for (int sekte = 0; sekte < 8; sekte++) {
			npc("liar." + sekte, 30, "MD");
		}
		npc("utusan_langit", 0, "D");
		npc("penonton.utusan", 40, "MD");
		npc("penonton.warga", 50, "A15D60T25");
		// ---------------------------------------------------------------- 40.4.2 disciples by rank (NPC sect members use these too)
		baris("murid.0", 50, "A10M90");
		baris("murid.1", 50, "M");
		baris("murid.2", 50, "M70D30");
		baris("murid.3", 50, "M50D50");
		baris("murid.4", 40, "D60T40");
		baris("murid.5", 35, "D50T50");
		baris(MURID_CALON, 50, "A20M80");
		baris(MURID_SAINGAN, 30, "M60D40");
		baris(MURID_BAYANGAN, 50, "M");
		baris(MURID_KLON, 50, "M");
		// ---------------------------------------------------------------- 40.4.3 life paths (any sex; the gallery shows youths)
		for (int bit = 1; bit <= SosokJalan.MAKS; bit++) {
			String kunci = SosokJalan.kunciRupa(bit);
			if (kunci != null) {
				baris(kunci, 50, "M");
			}
		}
		// ---------------------------------------------------------------- schedule-group fallbacks (38.N.3) and the last one
		baris("kelompok.PEKERJA", 30, "MD");
		baris("kelompok.PEDAGANG", 40, "D");
		baris("kelompok.PENJAGA", 15, "MD");
		baris("kelompok.ANAK", 50, "A");
		baris("kelompok.TETUA", 40, "T");
		baris("kelompok.KULTIVATOR", 35, "MD");
		baris("kelompok.CENDEKIA", 40, "DT");
		baris("kelompok.RAHIB", 0, "MDT");
		baris("kelompok.PEJABAT", 20, "DT");
		baris("kelompok.KHUSUS", 40, "MD");
		baris(KELOMPOK_SIPIL, 50, "A10M25D45T20");
	}

	// ================================================================== lookups

	/** The row of exactly {@code kunci}, or null. */
	public static @Nullable Kebijakan baris(@Nullable String kunci) {
		return kunci == null ? null : BARIS.get(kunci);
	}

	/**
	 * The policy of a look key with the fallbacks: the key's own row, {@code liar.*} and {@code d.hakim.*} style
	 * sub-keys by their prefix row, the role's schedule group row (when {@code peran} is given), then
	 * {@code kelompok.SIPIL}.
	 */
	public static Kebijakan kebijakan(@Nullable String kunci, @Nullable NpcRole peran) {
		Kebijakan k = baris(kunci);
		if (k == null && kunci != null) {
			int dot = kunci.lastIndexOf('.');
			while (k == null && dot > 0) {
				kunci = kunci.substring(0, dot);
				k = baris(kunci);
				dot = kunci.lastIndexOf('.');
			}
		}
		if (k == null && peran != null) {
			k = baris(peran.id());
			if (k == null) {
				k = baris(peran.rupaKelompok());
			}
		}
		return k != null ? k : BARIS.get(KELOMPOK_SIPIL);
	}

	/** The policy of a role (its own row, else its schedule group, else {@code kelompok.SIPIL}). */
	public static Kebijakan kebijakan(NpcRole peran) {
		return kebijakan(peran.id(), peran);
	}

	/** Every row key, in table order (the gallery and {@code cek_sosok.py}). */
	public static List<String> semuaKunci() {
		return Collections.unmodifiableList(new ArrayList<>(BARIS.keySet()));
	}

	/** The NPC look keys of 40.4.1 A-D in table order (the role gallery, ±125 looks). */
	public static List<String> rupaNpc() {
		return Collections.unmodifiableList(RUPA_NPC);
	}

	// ================================================================== NPCs

	/** The NPC's sex from its role and look (the names on its plate use this, {@code KotaNames.plate}). */
	public static int kelamin(NpcRole peran, int look) {
		return kelamin(kebijakan(peran), look);
	}

	/** The sex a policy gives a look seed. */
	public static int kelamin(Kebijakan k, int look) {
		int wajib = k.kelaminWajib();
		if (wajib >= 0) {
			return wajib;
		}
		int bits = look >>> 24 & 0x7F;
		return bits < k.persenPerempuan() * 1.28F ? Sosok.PEREMPUAN : Sosok.LAKI;
	}

	/** The age group a policy gives a look seed. */
	public static int usia(Kebijakan k, int look) {
		int bits = look >>> 2 & 0x7F;
		int c = k.anak();
		if (bits < c) {
			return Sosok.ANAK;
		}
		c += k.muda();
		if (bits < c) {
			return Sosok.MUDA;
		}
		c += k.dewasa();
		if (bits < c) {
			return Sosok.DEWASA;
		}
		return k.tua() > 0 ? Sosok.TUA : k.dewasa() > 0 ? Sosok.DEWASA : k.muda() > 0 ? Sosok.MUDA : Sosok.ANAK;
	}

	/** The whole Sosok of an NPC of {@code peran} with look seed {@code look} (40.2.2). */
	public static Sosok untukNpc(NpcRole peran, int look) {
		return dariKebijakan(kebijakan(peran), look);
	}

	/**
	 * The Sosok of a body dressed by look key {@code kunci} ({@code SosokApi.setRupa}: an NPC sect member
	 * {@code murid.2|sekte=npc:s3}, a sect master {@code ketua_sekte}); {@code peran} is the fallback for keys without a
	 * row.
	 */
	public static Sosok untukRupa(String kunci, @Nullable NpcRole peran, int look) {
		return dariKebijakan(kebijakan(kunci, peran), look);
	}

	/** The Sosok a policy gives a look seed: sex and age by the policy, build and seed by the murmur mixer. */
	public static Sosok dariKebijakan(Kebijakan k, int look) {
		int h = Mth.murmurHash3Mixer(look);
		return new Sosok(kelamin(k, look), usia(k, look), h & 7, h >>> 6 & Sosok.BENIH_MASK);
	}

	// ================================================================== disciples

	/** A new disciple's Sosok (40.2.2): sex by {@code rasioPerempuan}, mostly youths, never old. */
	public static Sosok untukMurid(RandomSource random) {
		int kelamin = random.nextDouble() < SosokConfig.get().rasioPerempuan ? Sosok.PEREMPUAN : Sosok.LAKI;
		return untukMurid(random, kelamin);
	}

	/** A new disciple's Sosok of a given sex (the name was picked first, or a command asked for it). */
	public static Sosok untukMurid(RandomSource random, int kelamin) {
		SosokConfig.Settings s = SosokConfig.get();
		double roll = random.nextDouble();
		int usia = roll < s.anakSaatRekrut ? Sosok.ANAK : roll < s.anakSaatRekrut + s.dewasaSaatRekrut ? Sosok.DEWASA : Sosok.MUDA;
		return new Sosok(kelamin, usia, random.nextInt(Sosok.POSTUR_MAKS + 1), random.nextInt(Sosok.BENIH_MASK + 1));
	}

	/**
	 * A deterministic Sosok for a body made without one by code that only knows the old look fields (a rival, an NPC
	 * sect member, a Sekte Langit guest made through {@code Disciples.spawnUnowned}): the sex of its disciple name when it
	 * carries one ({@code kultivator.disciples.name.<n>}), else the old slim flag; age by the recruit shares; build and
	 * seed hashed from name, skin and slim, so the same person respawns with the same face.
	 */
	public static Sosok dariTubuhLama(@Nullable Component nama, int skin, boolean slim) {
		String teks = nama == null ? "" : nama.getContents() instanceof TranslatableContents t ? t.getKey() : nama.getString();
		long h = Sosok.aduk(teks.hashCode() * 0x9E3779B97F4A7C15L ^ (long) skin << 32 ^ (slim ? 0x5DEECE66DL : 0L));
		int dariNama = kelaminNama(nama);
		int kelamin = dariNama >= 0 ? dariNama : slim ? Sosok.PEREMPUAN : Sosok.LAKI;
		SosokConfig.Settings s = SosokConfig.get();
		double roll = (h >>> 11 & 0xFFFFF) / (double) 0x100000;
		int usia = roll < s.anakSaatRekrut * 0.5D ? Sosok.ANAK : roll < s.anakSaatRekrut * 0.5D + s.dewasaSaatRekrut * 2.0D
				? Sosok.DEWASA : Sosok.MUDA;
		return new Sosok(kelamin, usia, (int) (h & 7), (int) (h >>> 36) & Sosok.BENIH_MASK);
	}

	/** The sex of a disciple name component ({@link dev.kultivator.disciple.DiscipleNames}), or -1 when it is none. */
	public static int kelaminNama(@Nullable Component nama) {
		if (nama != null && nama.getContents() instanceof TranslatableContents t) {
			return dev.kultivator.disciple.DiscipleNames.kelaminKunci(t.getKey());
		}
		return -1;
	}

	// ================================================================== table helpers

	/** An NPC row of 40.4.1 A-D (also listed for the role gallery). */
	private static void npc(String kunci, int persenPerempuan, String usia) {
		baris(kunci, persenPerempuan, usia);
		RUPA_NPC.add(kunci);
	}

	/**
	 * One row: {@code persenPerempuan} 0 (L) .. 100 (P), age spec in the table's notation: letters A/M/D/T each followed
	 * by an optional percentage; letters without a number share what is left equally ("MD" = 50/50, "D70T30").
	 */
	private static void baris(String kunci, int persenPerempuan, String usia) {
		int[] persen = new int[4];
		boolean[] ada = new boolean[4];
		int tanpaAngka = 0;
		int dipakai = 0;
		String spec = usia.toUpperCase(Locale.ROOT);
		for (int i = 0; i < spec.length(); ) {
			int idx = "AMDT".indexOf(spec.charAt(i));
			if (idx < 0) {
				throw new IllegalArgumentException("Sosok age spec " + usia + " of " + kunci);
			}
			int j = i + 1;
			while (j < spec.length() && Character.isDigit(spec.charAt(j))) {
				j++;
			}
			ada[idx] = true;
			if (j > i + 1) {
				persen[idx] = Integer.parseInt(spec.substring(i + 1, j));
				dipakai += persen[idx];
			} else {
				persen[idx] = -1;
				tanpaAngka++;
			}
			i = j;
		}
		if (tanpaAngka > 0) {
			int sisa = Math.max(0, 100 - dipakai);
			int bagian = sisa / tanpaAngka;
			int lebih = sisa - bagian * tanpaAngka;
			for (int i = 0; i < 4; i++) {
				if (ada[i] && persen[i] < 0) {
					persen[i] = bagian + (lebih-- > 0 ? 1 : 0);
				}
			}
		}
		// percent -> weights out of 128, the last present group takes the rounding rest
		int[] w = new int[4];
		int total = 0;
		int last = -1;
		for (int i = 0; i < 4; i++) {
			if (ada[i]) {
				w[i] = Math.round(persen[i] * 1.28F);
				total += w[i];
				last = i;
			}
		}
		if (last < 0) {
			throw new IllegalArgumentException("Sosok age spec " + usia + " of " + kunci);
		}
		w[last] += 128 - total;
		BARIS.put(kunci, new Kebijakan(kunci, Mth.clamp(persenPerempuan, 0, 100), w[0], w[1], w[2], w[3]));
	}

	private SosokGen() {
	}
}
