package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.KerjaPeran;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukGen;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Work, apprenticeship and changing jobs (DESIGN 42.4.7). A <b>job</b> is a generated 38 slot ({@code PendudukGen}) of a
 * working role; one slot one holder. Residents without a slot of their own (grown children, newcomers, apprentices, the
 * unemployed) live in extra slots ({@link PendudukTambahan}) and are <b>unemployed</b> unless a player employs them.
 * <p>
 * On Hari Warga ({@link #harian}): a child of 12 chooses its path (the parents' trade 55 %, the zhuazhou object 30 %, chance
 * 15 %; a rooted child who grasped the wooden sword becomes a sect hopeful); 13-15 it becomes the apprentice of a holder of
 * that trade (bond {@code GURU_MURID}, skill starts at 10, works beside the master: {@link #magang}); a grown child leaves its
 * child slot; skill grows +4 a year at work (habit factors) and +6 as an apprentice; a skilled-poor, unhappy worker changes
 * jobs (5 % a year); and every vacant slot is filled in the design's order: (1) the trade's apprentice here, (2) the previous
 * holder's grown unemployed child, (3) the unemployed adult whose skill and temperament fit best ({@link #cocok}), (4) a
 * mover or a newcomer ({@link Migrasi}). Children's and elders' slots are filled from the children and elders in extra slots.
 */
public final class Pekerjaan {

	/** Zhuazhou objects in the order of the ceremony mat (42.7.7): 1 brush .. 8 coin; {@code Warga.zhuazhou} holds the index. */
	public static final String[] ZHUAZHOU = {"", "kuas", "sempoa", "pedang_kayu", "cap", "kitab", "palu", "tasbih", "koin"};
	/** Path a zhuazhou object points to ({@code "sekte"} is decided by the root, see {@link #pilihJalur}). */
	private static final NpcRole.Kelompok[] ZHUAZHOU_KE = {null, NpcRole.Kelompok.CENDEKIA, NpcRole.Kelompok.PEDAGANG,
			NpcRole.Kelompok.PENJAGA, NpcRole.Kelompok.PEJABAT, NpcRole.Kelompok.CENDEKIA, NpcRole.Kelompok.PEKERJA,
			NpcRole.Kelompok.RAHIB, NpcRole.Kelompok.PEDAGANG};
	/** Path value of a sect hopeful. */
	public static final String JALUR_SEKTE = "sekte";

	/** Record keys ({@link ArsipWarga.Catatan#warga}). */
	static final String K_JALUR = "jalur";
	static final String K_GURU = "guru";
	static final String K_MAJIKAN = "majikan";
	static final String K_TUGAS = "tugas";
	static final String K_NGANGGUR = "nganggur";
	/** Place stat keys ({@code WargaData.Situs.stat}): centre, kind, strength, beds seen, vacant trades. */
	static final String S_X = "a2x";
	static final String S_Z = "a2z";
	static final String S_JENIS = "a2j";
	static final String S_KUAT = "a2k";
	static final String S_KASUR = "a2kasur";
	static final String S_LOWONGAN = "a2lowongan";

	/** Most apprentices one master takes. */
	private static final int MURID_MAKS = 2;

	/** Generated slots of places that are not loaded now (deterministic from site, kind, strength and seed). */
	private static final Map<Long, List<Penduduk>> SLOT = new HashMap<>();

	// ================================================================== readers

	/** True for a working role (not a child, an elder or a fixed life). */
	public static boolean peranKerja(NpcRole peran) {
		return peran.kelompok != NpcRole.Kelompok.ANAK && peran.kelompok != NpcRole.Kelompok.TETUA && peran.kelompok != NpcRole.Kelompok.KHUSUS;
	}

	/** True while {@code w} holds a job: a generated working slot, or employment by a player ({@link #pekerjakan}). */
	public static boolean bekerja(Warga w) {
		if (w == null || w.status != Warga.Status.HIDUP) {
			return false;
		}
		if (w.slot != Warga.TANPA_SLOT && !PendudukTambahan.tambahan(w.slot) && peranKerja(w.peranNpc())) {
			return true;
		}
		CompoundTag t = catatan(w);
		return t != null && t.contains(K_MAJIKAN);
	}

	/** The master of an apprentice, or null. */
	public static @Nullable Warga guru(WargaData data, Warga w) {
		CompoundTag t = catatan(w);
		return t == null || !t.contains(K_GURU) ? null : data.byId(t.getIntOr(K_GURU, 0));
	}

	/** The path a child chose at 12 ({@code NpcRole.Kelompok.id()} or {@link #JALUR_SEKTE}), "" before. */
	public static String jalur(Warga w) {
		CompoundTag t = catatan(w);
		return t == null ? "" : t.getStringOr(K_JALUR, "");
	}

	/** World days {@code w} has been out of work (0 when working or not of working age). */
	public static long hariNganggur(Warga w, long hari) {
		CompoundTag t = catatan(w);
		return t == null || !t.contains(K_NGANGGUR) ? 0L : Math.max(0L, hari - t.getLongOr(K_NGANGGUR, hari));
	}

	/**
	 * How well a temperament ({@code Tokoh.WATAK}: ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik) fits a trade
	 * group, -1..2 (42.4.7 {@code KerjaPeran} fit): merchants like the greedy, sly and friendly, scholars the wise and
	 * honest, guards the honest and proud, workers anyone.
	 */
	public static int cocok(int watak, NpcRole.Kelompok kelompok) {
		return switch (kelompok) {
			case PEDAGANG -> watak == 2 || watak == 7 ? 2 : watak == 0 ? 1 : watak == 4 ? -1 : 0;
			case CENDEKIA -> watak == 3 ? 2 : watak == 6 ? 1 : watak == 7 ? -1 : 0;
			case PENJAGA -> watak == 6 || watak == 5 ? 2 : watak == 4 ? -1 : 0;
			case PEJABAT -> watak == 5 || watak == 3 ? 1 : watak == 4 ? -1 : 0;
			case RAHIB -> watak == 3 || watak == 0 ? 1 : watak == 2 ? -1 : 0;
			default -> 0;
		};
	}

	// ================================================================== employment by players (42C)

	/** A player employs {@code w} ({@code tugas}: "toko", "gudang"...); the resident leaves a generated job for it. */
	public static void pekerjakan(MinecraftServer server, long situs, Warga w, UUID majikan, String tugas) {
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		CompoundTag t = c.warga(w.id);
		t.putString(K_MAJIKAN, majikan.toString());
		t.putString(K_TUGAS, tugas == null ? "" : tugas);
		t.remove(K_NGANGGUR);
		c.ubah();
	}

	/** Ends a player's employment of {@code w}. */
	public static void lepas(MinecraftServer server, Warga w) {
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		CompoundTag t = c.peekWarga(w.id);
		if (t != null && t.contains(K_MAJIKAN)) {
			t.remove(K_MAJIKAN);
			t.remove(K_TUGAS);
			c.ubah();
		}
	}

	/** The player employing {@code w}, or null. */
	public static @Nullable UUID majikan(Warga w) {
		CompoundTag t = catatan(w);
		if (t == null || !t.contains(K_MAJIKAN)) {
			return null;
		}
		try {
			return UUID.fromString(t.getStringOr(K_MAJIKAN, ""));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A business went bankrupt (42.6.4): its owner gives up the shop slot and becomes a worker when one is free. */
	public static void bangkrut(MinecraftServer server, long situs, Warga pemilik) {
		if (pemilik.slot != Warga.TANPA_SLOT && !PendudukTambahan.tambahan(pemilik.slot)) {
			keluar(server, situs, pemilik, "bangkrut");
			ArsipWarga.catatan(server).warga(pemilik.id).putString(K_JALUR, NpcRole.Kelompok.PEKERJA.id());
			ArsipWarga.catatan(server).ubah();
		}
	}

	// ================================================================== the apprentice beside its master

	/**
	 * The apprentice's activity during work hours (8-17, not on the rest day): beside its master's work titik, playing the
	 * master's work clip with the master's props. Null outside those hours or without a master at work here.
	 */
	public static @Nullable Kegiatan magang(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam) {
		if (jam < 8 || jam >= 17) {
			return null;
		}
		CompoundTag t = catatan(w);
		if (t == null || !t.contains(K_GURU)) {
			return null;
		}
		Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
		if (s.hariPekan() == 6) {
			return null;
		}
		Warga guru = WargaApi.byId(t.getIntOr(K_GURU, 0));
		if (guru == null || guru.status != Warga.Status.HIDUP || guru.slot == Warga.TANPA_SLOT) {
			return null;
		}
		Penduduk gp = huni.orang(guru.slot);
		Titik titik = gp == null ? null : huni.kerja(gp);
		if (titik == null) {
			return null;
		}
		Direction sisi = titik.hadap().getAxis().isHorizontal() ? titik.hadap().getClockWise() : Direction.EAST;
		BlockPos at = titik.pos().relative(sisi, 1 + Math.floorMod(w.id, 2));
		Kegiatan kerja = KerjaPeran.untuk(gp.peran(), gp.benih()).kegiatan();
		// one hour in three the apprentice only watches and copies the master's stroke (a beat late, eyes on him: TIRU)
		boolean tiru = Math.floorMod(jam + w.id, 3) == 0;
		return new Kegiatan("magang", Kegiatan.XZ + at.getX() + "," + at.getZ(), tiru ? KotaGerakId.TIRU : kerja.klip(), tiru ? 0 : kerja.varian(),
				kerja.propKanan(), kerja.propKiri(), kerja.propPunggung(), 0, false);
	}

	// ================================================================== Hari Warga

	/** Hari Warga step (after {@link Suasana#harian}): paths, apprentices, skill, job changes and filling vacant slots. */
	public static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaConfig.warga().aktif) {
			return;
		}
		TempatHuni huni = Pemanggil.huni(s.id);
		if (huni != null) {
			catatTempat(huni, s);
		}
		List<Penduduk> slot = slot(server, s);
		WargaData data = WargaData.get(server);
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		RandomSource random = RandomSource.create(s.id * 0x2545F4914F6CDD1DL ^ hari * 0x9E3779B97F4A7C15L ^ 0x4B45524AL);
		int tahun = KotaConfig.warga().hariPerTahun;
		Map<Integer, Penduduk> slotById = new HashMap<>();
		if (slot != null) {
			for (Penduduk p : slot) {
				slotById.put(p.id(), p);
			}
		}
		for (Warga w : List.copyOf(s.warga)) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			int umur = WargaApi.umur(w);
			boolean ultah = umur != Kebiasaan.umurPada(w, hari - 1L, tahun);
			CompoundTag t = c.warga(w.id);
			if (ultah && umur == 12 && !t.contains(K_JALUR)) {
				t.putString(K_JALUR, pilihJalur(data, w, random));
			}
			if (umur >= 13 && umur <= 15 && !t.contains(K_GURU)) {
				Warga guru = cariGuru(s, w, t.getStringOr(K_JALUR, ""));
				if (guru != null) {
					t.putInt(K_GURU, guru.id);
					w.ahli = Math.max(w.ahli, 10);
					Hubungan.ikat(w, guru, JenisHubungan.GURU_MURID, 30, hari);
				}
			}
			// a grown child leaves the child's slot (the extra slots take it until a job is free)
			Penduduk own = w.slot == Warga.TANPA_SLOT ? null : slotById.get(w.slot);
			if (own != null && own.peran().kelompok == NpcRole.Kelompok.ANAK && umur >= 16) {
				keluar(server, s.id, w, "dewasa");
			}
			if (ultah) {
				if (bekerja(w)) {
					w.ahli = Mth.clamp(w.ahli + (int) Math.round(4.0D * Kebiasaan.faktorAhli(w)), 0, 100);
				} else if (t.contains(K_GURU)) {
					w.ahli = Mth.clamp(w.ahli + (int) Math.round(6.0D * Kebiasaan.faktorAhli(w)), 0, 100);
				}
				// a poor craftsman unhappy for a year looks for other work (5 % a year)
				if (bekerja(w) && w.ahli < 30 && Suasana.hariMurung(server, w, "s20", hari) >= tahun && random.nextDouble() < 0.05D) {
					keluar(server, s.id, w, "ganti");
				}
			}
			if (umur >= 16 && umur < 65 && !bekerja(w)) {
				if (!t.contains(K_NGANGGUR)) {
					t.putLong(K_NGANGGUR, hari);
				}
			} else {
				t.remove(K_NGANGGUR);
			}
			if (umur >= 16 && t.contains(K_GURU) && bekerja(w)) {
				t.remove(K_GURU);
			}
		}
		if (slot != null) {
			isi(server, s, slot, hari);
		}
		c.ubah();
		data.ubah();
	}

	/** The path of a child at 12 (42.4.7): the parents' trade 55 %, the zhuazhou object 30 %, chance 15 %. */
	static String pilihJalur(WargaData data, Warga w, RandomSource random) {
		double r = random.nextDouble();
		if (r < 0.55D) {
			Warga ortu = data.byId(w.pria() ? w.ayah : w.ibu);
			if (ortu == null) {
				ortu = data.byId(w.pria() ? w.ibu : w.ayah);
			}
			if (ortu != null && peranKerja(ortu.peranNpc())) {
				return ortu.peranNpc().kelompok.id();
			}
		} else if (r < 0.85D && w.zhuazhou > 0 && w.zhuazhou < ZHUAZHOU.length) {
			if (w.zhuazhou == 3 && w.akar >= 2) {
				return JALUR_SEKTE;
			}
			return ZHUAZHOU_KE[w.zhuazhou].id();
		}
		NpcRole.Kelompok[] all = {NpcRole.Kelompok.PEKERJA, NpcRole.Kelompok.PEKERJA, NpcRole.Kelompok.PEDAGANG, NpcRole.Kelompok.CENDEKIA,
				NpcRole.Kelompok.PENJAGA, NpcRole.Kelompok.RAHIB};
		return w.akar >= 3 && random.nextBoolean() ? JALUR_SEKTE : all[random.nextInt(all.length)].id();
	}

	/** A holder of the path's trade here with fewer than {@value #MURID_MAKS} apprentices, the most skilled first. */
	private static @Nullable Warga cariGuru(WargaData.Situs s, Warga murid, String jalur) {
		if (jalur.isEmpty() || jalur.equals(JALUR_SEKTE)) {
			return null;
		}
		Map<Integer, Integer> murid0 = new HashMap<>();
		for (Warga x : s.warga) {
			CompoundTag t = catatan(x);
			if (t != null && t.contains(K_GURU)) {
				murid0.merge(t.getIntOr(K_GURU, 0), 1, Integer::sum);
			}
		}
		Warga best = null;
		for (Warga x : s.warga) {
			if (x == murid || x.status != Warga.Status.HIDUP || !bekerja(x) || PendudukTambahan.tambahan(x.slot)
					|| !x.peranNpc().kelompok.id().equals(jalur) || murid0.getOrDefault(x.id, 0) >= MURID_MAKS) {
				continue;
			}
			if (best == null || x.ahli > best.ahli || x.ahli == best.ahli && (x.id == murid.ayah || x.id == murid.ibu)) {
				best = x;
			}
		}
		return best;
	}

	/** {@code w} gives up its generated slot ({@code sebab}: dewasa, ganti, bangkrut, pindah); the extra slots take it. */
	static void keluar(MinecraftServer server, long situs, Warga w, String sebab) {
		String lama = w.peran;
		w.slot = Warga.TANPA_SLOT;
		if (!sebab.equals("pindah")) {
			w.peran = NpcRole.WARGA.id();
		}
		if (!lama.equals(w.peran)) {
			KotaBus.KERJA_BERUBAH.invoker().onKerjaBerubah(server, situs, w, lama, w.peran);
		}
	}

	/** Fills every vacant generated slot of the place (design order), then records the trades still open. */
	private static void isi(MinecraftServer server, WargaData.Situs s, List<Penduduk> slot, long hari) {
		Map<Integer, Warga> pemegang = new HashMap<>();
		List<Warga> lepas = new ArrayList<>();
		Map<Integer, Integer> umur = new HashMap<>();
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			umur.put(w.id, WargaApi.umur(w));
			if (w.slot != Warga.TANPA_SLOT && !PendudukTambahan.tambahan(w.slot)) {
				pemegang.put(w.slot, w);
			} else if (umur.get(w.id) >= 3) {
				lepas.add(w);
			}
		}
		List<String> lowongan = new ArrayList<>();
		for (Penduduk p : slot) {
			if (p.tamu() || pemegang.containsKey(p.id())) {
				continue;
			}
			NpcRole.Kelompok k = p.peran().kelompok;
			Warga pilih;
			if (k == NpcRole.Kelompok.ANAK) {
				pilih = terbaik(lepas, x -> umur.get(x.id) >= 3 && umur.get(x.id) <= 12 ? 100 - umur.get(x.id) : Integer.MIN_VALUE);
			} else if (k == NpcRole.Kelompok.TETUA) {
				pilih = terbaik(lepas, x -> umur.get(x.id) >= 60 && majikan(x) == null ? umur.get(x.id) : Integer.MIN_VALUE);
			} else if (k == NpcRole.Kelompok.KHUSUS) {
				continue;
			} else {
				pilih = calonKerja(s, p, lepas, umur);
			}
			if (pilih == null) {
				if (peranKerja(p.peran())) {
					lowongan.add(p.peran().id());
				}
				continue;
			}
			lepas.remove(pilih);
			pemegang.put(p.id(), pilih);
			String lama = pilih.peran;
			pilih.slot = p.id();
			pilih.peran = p.peran().id();
			CompoundTag t = ArsipWarga.catatan(server).warga(pilih.id);
			t.remove(K_GURU);
			t.remove(K_NGANGGUR);
			if (!lama.equals(pilih.peran)) {
				KotaBus.KERJA_BERUBAH.invoker().onKerjaBerubah(server, s.id, pilih, lama, pilih.peran);
			}
		}
		s.stat.putString(S_LOWONGAN, String.join(",", lowongan));
	}

	/** The candidate for a working slot: (1) the trade's apprentice, (2) the previous holder's child, (3) the best fit. */
	private static @Nullable Warga calonKerja(WargaData.Situs s, Penduduk p, List<Warga> lepas, Map<Integer, Integer> umur) {
		NpcRole peran = p.peran();
		Warga murid = terbaik(lepas, x -> {
			CompoundTag t = catatan(x);
			Warga g = t == null || !t.contains(K_GURU) ? null : WargaApi.byId(t.getIntOr(K_GURU, 0));
			return g != null && g.peranNpc() == peran && umur.get(x.id) >= 14 ? 1000 + x.ahli : Integer.MIN_VALUE;
		});
		if (murid != null) {
			return murid;
		}
		ArsipWarga.Entri lama = ArsipWarga.terakhirDiSlot(s, p.id());
		if (lama != null && lama.anak().length > 0) {
			int[] anak = lama.anak();
			Warga anakLama = terbaik(lepas, x -> {
				for (int a : anak) {
					if (a == x.id && umur.get(x.id) >= 16 && majikan(x) == null) {
						return 500 + x.ahli;
					}
				}
				return Integer.MIN_VALUE;
			});
			if (anakLama != null) {
				return anakLama;
			}
		}
		return terbaik(lepas, x -> {
			int u = umur.get(x.id);
			if (u < 16 || u >= 65 || majikan(x) != null || jalur(x).equals(JALUR_SEKTE) && u < 18) {
				return Integer.MIN_VALUE;
			}
			int skor = x.ahli + cocok(x.watak, peran.kelompok) * 15;
			if (jalur(x).equals(peran.kelompok.id())) {
				skor += 20;
			}
			return skor;
		});
	}

	private interface Skor {
		int nilai(Warga w);
	}

	private static @Nullable Warga terbaik(List<Warga> list, Skor skor) {
		Warga best = null;
		int bestV = Integer.MIN_VALUE;
		for (Warga w : list) {
			int v = skor.nilai(w);
			if (v != Integer.MIN_VALUE && (best == null || v > bestV || v == bestV && w.id < best.id)) {
				best = w;
				bestV = v;
			}
		}
		return best;
	}

	// ================================================================== place facts

	/** Caches the facts of an active place other modules need while it is not loaded (centre, kind, beds). */
	public static void catatTempat(TempatHuni huni, WargaData.Situs s) {
		BlockPos c = huni.kotak.getCenter();
		s.stat.putInt(S_X, c.getX());
		s.stat.putInt(S_Z, c.getZ());
		s.stat.putString(S_JENIS, huni.jenis);
		s.stat.putInt(S_KUAT, huni.kekuatan);
		s.stat.putInt(S_KASUR, Math.max(s.stat.getIntOr(S_KASUR, 0), huni.titik("kasur").size()));
		SLOT.remove(s.id);
	}

	/** The generated slots of a place: from the live place, else regenerated from the cached kind (null when unknown). */
	public static @Nullable List<Penduduk> slot(MinecraftServer server, WargaData.Situs s) {
		TempatHuni huni = Pemanggil.huni(s.id);
		if (huni != null) {
			List<Penduduk> all = huni.penduduk();
			return all.subList(0, Math.min(all.size(), huni.jumlahAsli()));
		}
		List<Penduduk> cached = SLOT.get(s.id);
		if (cached != null) {
			return cached;
		}
		String jenis = s.stat.getStringOr(S_JENIS, "");
		if (jenis.isEmpty()) {
			return null;
		}
		List<Penduduk> gen = PendudukGen.untuk(s.id, jenis, s.stat.getIntOr(S_KUAT, 1), server.overworld().getSeed());
		SLOT.put(s.id, gen);
		return gen;
	}

	/** The trades a place has open (vacant working slots after the last Hari Warga). */
	public static List<String> lowongan(WargaData.Situs s) {
		String v = s.stat.getStringOr(S_LOWONGAN, "");
		return v.isEmpty() ? List.of() : List.of(v.split(","));
	}

	/** Lang key of a child's path ({@code kultivator.kota42.kerja.jalur.<id>}, {@code belum} before 12). */
	public static String kunciJalur(String jalur) {
		return "kultivator.kota42.kerja.jalur." + (jalur.isEmpty() ? "belum" : jalur.toLowerCase(Locale.ROOT));
	}

	static @Nullable CompoundTag catatan(Warga w) {
		ArsipWarga.Catatan c = ArsipWarga.catatan();
		return c == null ? null : c.peekWarga(w.id);
	}

	/** Forgets the regenerated slot cache (server stop). */
	public static void lupakan() {
		SLOT.clear();
	}

	private Pekerjaan() {
	}
}
