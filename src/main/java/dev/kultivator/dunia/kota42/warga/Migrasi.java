package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.sosok.GerakId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moving between towns (DESIGN 42.4.8). Once a Fana Year, on the resident's birthday, these triggers are weighed (first
 * one that fires): a crowded house sends a young single adult away (30 %, to a known place with free beds); two years out
 * of work (40 %, to a place with an open job of its trade); a year of mood below -40 (20 %, to the most prosperous place
 * within 3000 blocks, else far away); a frightened town, fear meter >= 60 (10 %); an elder follows a child who moved
 * (15 %). A lost raid makes a quarter of the families leave for relatives or the nearest place; a marriage across towns
 * moves the bride (or the groom who marries in when he has no work and she has). A married mover takes its spouse and
 * young children.
 * <p>
 * The family packs on its last evening and waves goodbye ({@link #pamit}), leaves at the next day change (status
 * {@code JALAN} for {@code ceil(distance / 1200)} days, bodies on the road by {@link PerjalananWarga}), and arrives as a new
 * household with its memories (id kept), where 42.4.7 finds it a house slot and work. Gossip is born at both ends. With no
 * known place a far-off destination archives the mover ({@code pindah_jauh}). Open jobs nobody fills bring newcomers from
 * beyond the map through 42A's generator ({@link #setPembuatPendatang}).
 */
public final class Migrasi {

	/** 42A's newcomer generator (adds the new resident to the place and returns it; null = none). */
	@FunctionalInterface
	public interface PembuatPendatang {
		@Nullable Warga buat(MinecraftServer server, long situs, NpcRole peran, long hari);
	}

	/** Record keys ({@link ArsipWarga.Catatan#warga}). */
	static final String K_KE = "pdKe";
	static final String K_HARI = "pdHari";
	static final String K_SEBAB = "pdSebab";
	static final String K_ROMBONGAN = "pdRombongan";
	static final String K_ASAL = "asal";
	static final String K_ASAL_PERAN = "asalPeran";
	static final String K_ASAL_HARI = "asalHari";
	/** Place record key: world day of a lost raid not yet answered. */
	static final String T_KALAH = "kalahHari";

	/** Causes ({@code kultivator.kota42.pindah.sebab.<id>}). */
	public static final String SESAK = "sesak";
	public static final String NGANGGUR = "nganggur";
	public static final String MURUNG = "murung";
	public static final String TAKUT = "takut";
	public static final String IKUT_ANAK = "ikut_anak";
	public static final String SERBUAN = "serbuan";
	public static final String NIKAH = "nikah";

	/** Blocks travelled per world day. */
	public static final int JARAK_HARI = 1200;
	/** Farthest a sad resident looks for a better town. */
	public static final int JARAK_MURUNG = 3000;

	private static volatile @Nullable PembuatPendatang pembuat;
	private static boolean ready;
	/** Crowded households per place for one day: situs -> {day, set of household ids}. */
	private static final Map<Long, Object[]> SESAK_CACHE = new HashMap<>();

	/** Registers the KotaBus listeners. From {@code ArsipWarga.init}; safe twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		KotaBus.NIKAH.register(Migrasi::nikah);
		KotaBus.SERBUAN.register((server, situs, mulai, menang) -> {
			if (!mulai && !menang) {
				ArsipWarga.catatan(server).tempat(situs).putLong(T_KALAH, KotaHidup.hari(server));
				ArsipWarga.catatan(server).ubah();
			}
		});
	}

	/** 42A registers {@code WargaGen}'s newcomer here. */
	public static void setPembuatPendatang(@Nullable PembuatPendatang baru) {
		pembuat = baru;
	}

	// ================================================================== readers

	/** The place {@code w} moved from ({@code 0} = born here or from beyond the map). */
	public static long asal(Warga w) {
		CompoundTag t = Pekerjaan.catatan(w);
		return t == null ? 0L : t.getLongOr(K_ASAL, 0L);
	}

	/** The role {@code w} had in its old town ("" = none): "Dulu jual bakpao di <asal>". */
	public static String asalPeran(Warga w) {
		CompoundTag t = Pekerjaan.catatan(w);
		return t == null ? "" : t.getStringOr(K_ASAL_PERAN, "");
	}

	/** The cause of a planned or running move ("" = none). */
	public static String sebab(Warga w) {
		CompoundTag t = Pekerjaan.catatan(w);
		return t == null ? "" : t.getStringOr(K_SEBAB, "");
	}

	/** The world day {@code w} will leave (-1 = no move planned). */
	public static long hariBerangkat(Warga w) {
		CompoundTag t = Pekerjaan.catatan(w);
		return t == null ? -1L : t.getLongOr(K_HARI, -1L);
	}

	/**
	 * True when the household of {@code w} has more members than beds + 1 (42.4.11 "rumah sesak" -5, 42.4.8 trigger): beds
	 * are those of the members holding generated slots (an extra slot shares a bed).
	 */
	public static boolean sesak(MinecraftServer server, long situs, Warga w) {
		if (w.rumahTangga == 0) {
			return false;
		}
		long hari = KotaHidup.hari(server);
		Object[] c = SESAK_CACHE.get(situs);
		if (c == null || (long) c[0] != hari) {
			c = new Object[] {hari, hitungSesak(server, situs)};
			SESAK_CACHE.put(situs, c);
		}
		@SuppressWarnings("unchecked")
		Set<Integer> set = (Set<Integer>) c[1];
		return set.contains(w.rumahTangga);
	}

	private static Set<Integer> hitungSesak(MinecraftServer server, long situs) {
		WargaData.Situs s = WargaData.get(server).peek(situs);
		List<Penduduk> slot = s == null ? null : Pekerjaan.slot(server, s);
		Set<Integer> out = new HashSet<>();
		if (s == null || slot == null) {
			return out;
		}
		Map<Integer, Penduduk> byId = new HashMap<>();
		for (Penduduk p : slot) {
			byId.put(p.id(), p);
		}
		Map<Integer, Integer> anggota = new HashMap<>();
		Map<Integer, Set<Integer>> kasur = new HashMap<>();
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP || w.rumahTangga == 0) {
				continue;
			}
			anggota.merge(w.rumahTangga, 1, Integer::sum);
			Penduduk p = w.slot == Warga.TANPA_SLOT ? null : byId.get(w.slot);
			if (p != null && p.kasur() != Penduduk.TANPA) {
				kasur.computeIfAbsent(w.rumahTangga, k -> new HashSet<>()).add(p.kasur());
			}
		}
		anggota.forEach((rt, n) -> {
			if (n > kasur.getOrDefault(rt, Set.of()).size() + 1) {
				out.add(rt);
			}
		});
		return out;
	}

	// ================================================================== the last evening

	/** The movers' last evening: packing at home 16-19, then waving goodbye 19-20 (the day before they leave). */
	public static @Nullable Kegiatan pamit(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam) {
		CompoundTag t = Pekerjaan.catatan(w);
		if (t == null || !t.contains(K_HARI) || jam < 16 || jam >= 20) {
			return null;
		}
		long hari = Math.floorDiv(waktu, 24000L);
		if (t.getLongOr(K_HARI, -1L) != hari + 1L) {
			return null;
		}
		if (jam < 19) {
			return new Kegiatan("pindah_berkemas", Kegiatan.RUMAH, GerakId.BERKEMAS, 0, 0, 0, KotaGerakId.PROP_BUNTELAN, 0, false);
		}
		return new Kegiatan("pindah_pamit", Kegiatan.RUMAH, KotaGerakId.PAMIT_LAMBAI, 0, 0, 0, KotaGerakId.PROP_BUNTELAN, 0, false);
	}

	// ================================================================== Hari Warga

	/** Hari Warga step (after {@link Pekerjaan#harian}): arrivals, departures, this year's decisions and newcomers. */
	public static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaConfig.warga().aktif) {
			return;
		}
		WargaData data = WargaData.get(server);
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		Map<Integer, Integer> rumahTanggaBaru = new HashMap<>();
		for (Warga w : List.copyOf(s.warga)) {
			if (w.status == Warga.Status.JALAN && hari >= w.tiba) {
				tiba(server, data, c, s, w, hari, rumahTanggaBaru);
			}
		}
		for (Warga w : List.copyOf(s.warga)) {
			CompoundTag t = c.peekWarga(w.id);
			if (w.status == Warga.Status.HIDUP && t != null && t.contains(K_HARI) && t.getLongOr(K_HARI, Long.MAX_VALUE) <= hari) {
				berangkat(server, data, c, s, w, hari);
			}
		}
		SESAK_CACHE.remove(s.id);
		if (!KotaConfig.warga().migrasi) {
			c.ubah();
			return;
		}
		RandomSource random = RandomSource.create(s.id * 0x27BB2EE687B0B0FDL ^ hari * 0x9E3779B97F4A7C15L ^ 0x50494EL);
		int tahun = KotaConfig.warga().hariPerTahun;
		for (Warga w : List.copyOf(s.warga)) {
			if (w.status != Warga.Status.HIDUP || hariBerangkat(w) >= 0L || !w.tokoh.isEmpty()) {
				continue;
			}
			int umur = WargaApi.umur(w);
			if (umur < 18 || umur == Kebiasaan.umurPada(w, hari - 1L, tahun)) {
				continue;
			}
			putuskan(server, data, s, w, umur, hari, tahun, random);
		}
		long kalah = c.tempat(s.id).getLongOr(T_KALAH, -1L);
		if (kalah >= 0L) {
			c.tempat(s.id).remove(T_KALAH);
			serbuan(server, data, s, hari, random);
		}
		pendatang(server, s, hari, random);
		c.ubah();
		data.ubah();
	}

	/** One resident's yearly weighing of the triggers (first that fires). */
	private static void putuskan(MinecraftServer server, WargaData data, WargaData.Situs s, Warga w, int umur, long hari, int tahun,
			RandomSource random) {
		if (umur <= 30 && w.pasangan == Warga.TANPA && sesak(server, s.id, w) && random.nextDouble() < 0.30D) {
			WargaData.Situs ke = terbaik(server, data, s, x -> kasurBebas(server, x) > 0 ? kasurBebas(server, x) * 2.0D - jarak(s, x) / 500.0D : Double.NaN);
			if (ke != null) {
				rencana(server, data, s, List.of(w), ke.id, SESAK, hari);
				return;
			}
		}
		if (Pekerjaan.hariNganggur(w, hari) >= 2L * tahun && random.nextDouble() < 0.40D) {
			String jalur = Pekerjaan.jalur(w);
			WargaData.Situs ke = terbaik(server, data, s, x -> {
				List<String> buka = Pekerjaan.lowongan(x);
				if (buka.isEmpty()) {
					return Double.NaN;
				}
				double skor = 10.0D - jarak(s, x) / 400.0D;
				for (String peran : buka) {
					NpcRole r = NpcRole.byId(peran);
					if (r != null && r.kelompok.id().equals(jalur)) {
						skor += 5.0D;
						break;
					}
				}
				return skor;
			});
			if (ke != null) {
				rencana(server, data, s, inti(data, s, w), ke.id, NGANGGUR, hari);
				return;
			}
		}
		if (Suasana.hariMurung(server, w, "s40", hari) >= tahun && random.nextDouble() < 0.20D) {
			WargaData.Situs ke = terbaik(server, data, s, x -> jarak(s, x) <= JARAK_MURUNG ? Kemakmuran.nilai(x.id) : Double.NaN);
			rencana(server, data, s, inti(data, s, w), ke == null ? 0L : ke.id, MURUNG, hari);
			return;
		}
		if (PerbaikanApi.takut(s.id) >= 60 && random.nextDouble() < 0.10D) {
			WargaData.Situs ke = terbaik(server, data, s, x -> -jarak(s, x));
			rencana(server, data, s, inti(data, s, w), ke == null ? 0L : ke.id, TAKUT, hari);
			return;
		}
		if (umur >= 60 && random.nextDouble() < 0.15D) {
			for (int a : w.anak) {
				Warga anak = data.byId(a);
				long tempatAnak = anak == null ? 0L : data.situsDari(a);
				if (anak != null && anak.status == Warga.Status.HIDUP && tempatAnak != 0L && tempatAnak != s.id) {
					List<Warga> pergi = new ArrayList<>();
					pergi.add(w);
					Warga pasangan = data.byId(w.pasangan);
					if (pasangan != null && pasangan.status == Warga.Status.HIDUP && data.situsDari(pasangan.id) == s.id) {
						pergi.add(pasangan);
					}
					rencana(server, data, s, pergi, tempatAnak, IKUT_ANAK, hari);
					return;
				}
			}
		}
	}

	/** A lost raid: a quarter of the households leave, for a relative's town first, else the nearest place. */
	private static void serbuan(MinecraftServer server, WargaData data, WargaData.Situs s, long hari, RandomSource random) {
		Map<Integer, List<Warga>> rumah = new HashMap<>();
		for (Warga w : s.warga) {
			if (w.status == Warga.Status.HIDUP && w.rumahTangga != 0 && hariBerangkat(w) < 0L && w.tokoh.isEmpty()) {
				rumah.computeIfAbsent(w.rumahTangga, k -> new ArrayList<>()).add(w);
			}
		}
		WargaData.Situs dekat = terbaik(server, data, s, x -> -jarak(s, x));
		for (List<Warga> rt : rumah.values()) {
			if (random.nextDouble() >= 0.25D) {
				continue;
			}
			long ke = kerabat(data, s, rt);
			if (ke == 0L && dekat != null) {
				ke = dekat.id;
			}
			rencana(server, data, s, rt, ke, SERBUAN, hari);
		}
	}

	/** The town of a relative (parent, child or sibling) living elsewhere, or 0. */
	private static long kerabat(WargaData data, WargaData.Situs s, List<Warga> rt) {
		for (Warga w : rt) {
			List<Integer> lain = new ArrayList<>();
			lain.add(w.ayah);
			lain.add(w.ibu);
			for (int a : w.anak) {
				lain.add(a);
			}
			Warga ayah = data.byId(w.ayah);
			if (ayah != null) {
				for (int a : ayah.anak) {
					lain.add(a);
				}
			}
			for (int id : lain) {
				Warga k = id == Warga.TANPA ? null : data.byId(id);
				long tempat = k == null ? 0L : data.situsDari(id);
				if (k != null && k.status == Warga.Status.HIDUP && tempat != 0L && tempat != s.id && data.peek(tempat) != null) {
					return tempat;
				}
			}
		}
		return 0L;
	}

	/** A marriage across towns: the bride moves, or the groom without work who marries into a working bride's house. */
	private static void nikah(MinecraftServer server, long situs, Warga pria, Warga wanita) {
		if (!KotaConfig.warga().aktif || !KotaConfig.warga().migrasi) {
			return;
		}
		WargaData data = WargaData.get(server);
		long sp = data.situsDari(pria.id);
		long sw = data.situsDari(wanita.id);
		if (sp == 0L || sw == 0L || sp == sw) {
			return;
		}
		boolean ruzhui = !Pekerjaan.bekerja(pria) && Pekerjaan.bekerja(wanita);
		Warga mover = ruzhui ? pria : wanita;
		WargaData.Situs dari = data.peek(ruzhui ? sp : sw);
		if (dari != null) {
			rencana(server, data, dari, List.of(mover), ruzhui ? sw : sp, NIKAH, KotaHidup.hari(server));
		}
	}

	/** A married resident moves with its spouse and the children under 16 living here. */
	private static List<Warga> inti(WargaData data, WargaData.Situs s, Warga w) {
		List<Warga> out = new ArrayList<>();
		out.add(w);
		Warga pasangan = data.byId(w.pasangan);
		if (pasangan != null && pasangan.status == Warga.Status.HIDUP && data.situsDari(pasangan.id) == s.id && pasangan.tokoh.isEmpty()) {
			out.add(pasangan);
		}
		for (int a : w.anak) {
			Warga anak = data.byId(a);
			if (anak != null && anak.status == Warga.Status.HIDUP && data.situsDari(a) == s.id && WargaApi.umur(anak) < 16) {
				out.add(anak);
			}
		}
		return out;
	}

	/** Plans the move of {@code pergi} (first = leader) to {@code ke} (0 = beyond the map), leaving at the next day change. */
	static void rencana(MinecraftServer server, WargaData data, WargaData.Situs s, List<Warga> pergi, long ke, String sebab, long hari) {
		if (pergi.isEmpty()) {
			return;
		}
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		int pemimpin = pergi.getFirst().id;
		for (Warga w : pergi) {
			CompoundTag t = c.warga(w.id);
			t.putLong(K_KE, ke);
			t.putLong(K_HARI, hari + 1L);
			t.putString(K_SEBAB, sebab);
			t.putInt(K_ROMBONGAN, pemimpin);
		}
		c.ubah();
	}

	/** Cancels a planned move (a quest kept the family, a wedding moved elsewhere). */
	public static void batal(MinecraftServer server, Warga w) {
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		CompoundTag t = c.peekWarga(w.id);
		if (t != null && t.contains(K_HARI)) {
			t.remove(K_KE);
			t.remove(K_HARI);
			t.remove(K_SEBAB);
			t.remove(K_ROMBONGAN);
			c.ubah();
		}
	}

	private static void berangkat(MinecraftServer server, WargaData data, ArsipWarga.Catatan c, WargaData.Situs s, Warga w, long hari) {
		CompoundTag t = c.warga(w.id);
		long ke = t.getLongOr(K_KE, 0L);
		boolean pemimpin = t.getIntOr(K_ROMBONGAN, w.id) == w.id;
		WargaData.Situs tujuan = ke == 0L ? null : data.peek(ke);
		ServerLevel level = server.overworld();
		Pekerjaan.lepas(server, w);
		if (tujuan == null) {
			if (pemimpin) {
				GosipWarga.lahir(level, s.id, "PINDAH", Fakta.Rujuk.warga(w.id));
			}
			ArsipWarga.arsipkan(data, s.id, w, ArsipWarga.SEBAB_PINDAH_JAUH, hari, -1);
			KotaBus.PINDAH.invoker().onPindah(server, w, s.id, 0L);
			data.buang(w.id);
			c.lupakan(w.id);
			return;
		}
		t.putString(K_ASAL_PERAN, w.peran);
		Pekerjaan.keluar(server, s.id, w, "pindah");
		w.status = Warga.Status.JALAN;
		w.hariStatus = hari;
		w.tujuanSitus = ke;
		w.tiba = hari + Math.max(1L, (long) Math.ceil(jarak(s, tujuan) / JARAK_HARI));
		t.putLong(K_ASAL, s.id);
		t.putLong(K_ASAL_HARI, hari);
		t.remove(K_HARI);
		t.remove(K_KE);
		if (pemimpin) {
			GosipWarga.lahir(level, s.id, "PINDAH", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.tempat(ke));
		}
		KotaBus.PINDAH.invoker().onPindah(server, w, s.id, ke);
	}

	private static void tiba(MinecraftServer server, WargaData data, ArsipWarga.Catatan c, WargaData.Situs asal, Warga w, long hari,
			Map<Integer, Integer> rumahTanggaBaru) {
		long ke = w.tujuanSitus;
		WargaData.Situs tujuan = data.situs(ke);
		CompoundTag t = c.warga(w.id);
		int pemimpin = t.getIntOr(K_ROMBONGAN, w.id);
		w.rumahTangga = rumahTanggaBaru.computeIfAbsent(pemimpin, k -> tujuan.rumahTanggaBaru());
		w.rumah = Penduduk.TANPA;
		w.slot = Warga.TANPA_SLOT;
		w.status = Warga.Status.HIDUP;
		w.hariStatus = hari;
		w.datang = hari;
		w.tujuanSitus = 0L;
		w.tiba = 0L;
		t.remove(K_SEBAB);
		t.remove(K_ROMBONGAN);
		data.tambah(ke, w);
		if (pemimpin == w.id) {
			GosipWarga.lahir(server.overworld(), ke, "PINDAH", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.tempat(asal.id));
		}
		KotaBus.PINDAH.invoker().onPindah(server, w, asal.id, ke);
	}

	/** Open jobs nobody here could fill bring a newcomer from beyond the map (8 % a day, one a day). */
	private static void pendatang(MinecraftServer server, WargaData.Situs s, long hari, RandomSource random) {
		PembuatPendatang p = pembuat;
		List<String> buka = Pekerjaan.lowongan(s);
		if (p == null || buka.isEmpty() || random.nextDouble() >= 0.08D) {
			return;
		}
		NpcRole peran = NpcRole.byId(buka.get(random.nextInt(buka.size())));
		if (peran != null) {
			p.buat(server, s.id, peran, hari);
		}
	}

	// ================================================================== places

	private interface Nilai {
		double dari(WargaData.Situs s);
	}

	/** The best other known place with a position by {@code nilai} (NaN = unfit), or null. */
	private static @Nullable WargaData.Situs terbaik(MinecraftServer server, WargaData data, WargaData.Situs asal, Nilai nilai) {
		WargaData.Situs best = null;
		double bestV = Double.NEGATIVE_INFINITY;
		for (WargaData.Situs x : data.semuaSitus()) {
			if (x.id == asal.id || !x.stat.contains(Pekerjaan.S_X) || !x.bangkit()) {
				continue;
			}
			double v = nilai.dari(x);
			if (!Double.isNaN(v) && (best == null || v > bestV)) {
				best = x;
				bestV = v;
			}
		}
		return best;
	}

	/** Horizontal distance between two known places (huge when one has no recorded centre). */
	public static double jarak(WargaData.Situs a, WargaData.Situs b) {
		if (!a.stat.contains(Pekerjaan.S_X) || !b.stat.contains(Pekerjaan.S_X)) {
			return 1.0E9D;
		}
		double dx = a.stat.getIntOr(Pekerjaan.S_X, 0) - b.stat.getIntOr(Pekerjaan.S_X, 0);
		double dz = a.stat.getIntOr(Pekerjaan.S_Z, 0) - b.stat.getIntOr(Pekerjaan.S_Z, 0);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Free beds of a place: the beds (or generated slots) seen there minus the living residents of three and older. */
	public static int kasurBebas(MinecraftServer server, WargaData.Situs s) {
		List<Penduduk> slot = Pekerjaan.slot(server, s);
		int kapasitas = Math.max(s.stat.getIntOr(Pekerjaan.S_KASUR, 0), slot == null ? 0 : slot.size());
		int penghuni = 0;
		for (Warga w : s.warga) {
			if (w.status == Warga.Status.HIDUP && WargaApi.umur(w) >= 3) {
				penghuni++;
			}
		}
		return kapasitas - penghuni;
	}

	/** The traveller's line when greeted on the road ("We are moving to Kota Air, sir. There is work there."). */
	public static Component ucapan(Warga w) {
		String sebab = sebab(w);
		Component ke = w.tujuanSitus == 0L ? Component.translatable("kultivator.kota42.pindah.negeri_jauh") : KotaNames.town(w.tujuanSitus);
		return Component.translatable("kultivator.kota42.pindah.ucap." + (sebab.isEmpty() ? "umum" : sebab), ke);
	}

	/** Forgets the caches (server stop). */
	public static void lupakan() {
		SESAK_CACHE.clear();
	}

	private Migrasi() {
	}
}
