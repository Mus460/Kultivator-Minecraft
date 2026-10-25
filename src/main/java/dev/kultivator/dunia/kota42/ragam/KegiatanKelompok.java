package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukData;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Doing things together (DESIGN 42.4A.6): families walking out in the evening (18-19, 20 % of households a day), friends
 * meeting at the tea house (pairs of SAHABAT free at the same time), children 2-5 playing tag, lovers walking at dusk,
 * elders gathering over chess, workmates lunching together, market-day shopping parties and the rest day's lantern party.
 * Groups are drawn once per place per day ({@code situs ^ hari}) among residents the round leaves free in that window, and
 * run in <b>place time</b> so every member sets off at the same minute and arrives together; their goal
 * {@code @kelompok:<day>:<group>:<turn>} is placed by {@link #pilih} (registered as a {@link TempatHuni.Penyeimbang}
 * answering only that prefix): one shared anchor titik, the members in a formation about 1.2 blocks apart round it. A
 * group counts as one unit for 42S's balancer ({@link #ukuran}). Walking side by side: {@link #mitra} (for 42A's
 * {@code WargaApi.mitraJalan}).
 */
public final class KegiatanKelompok {

	/** Goal prefix of a group: {@code @kelompok:<day>:<group>:<turn>}. */
	public static final String TUJUAN = "@kelompok:";

	private static final Set<KumpulanKegiatan.Blok> BEBAS = EnumSet.of(KumpulanKegiatan.Blok.BEBAS);
	private static final Set<KumpulanKegiatan.Blok> SANTAI = EnumSet.of(KumpulanKegiatan.Blok.BEBAS, KumpulanKegiatan.Blok.MAKAN,
			KumpulanKegiatan.Blok.MALAM);
	private static final Set<KumpulanKegiatan.Blok> MAKAN = EnumSet.of(KumpulanKegiatan.Blok.MAKAN);

	/**
	 * The eight kinds.
	 *
	 * @param dari    window start, minutes after 06:00 (place time)
	 * @param lama    window length (minutes)
	 * @param blok    the blocks the round must be in for a member
	 * @param tujuan  the anchor: a titik kind or {@code @jalan}, {@code @depan_lapak}, {@code @kedai}
	 * @param kunci   activity key (existing 38 keys: the built-in chats, faces and interludes of TubuhPenduduk follow them)
	 */
	public enum Jenis {
		KELUARGA_SORE(720, 60, SANTAI, "@jalan", "jalan_jalan", GerakId.BICARA_TANGAN, GerakId.JALAN_SANTAI, 0, 0),
		SAHABAT_TEH(420, 75, BEBAS, "@kedai", "minum_teh", GerakId.MINUM_TEH, GerakId.JALAN_SANTAI, PropId.CANGKIR, PropId.LEPEK),
		ANAK_KEJAR(480, 60, BEBAS, "main", "main", GerakId.MAIN_KEJAR, GerakId.LARI, 0, 0),
		KEKASIH_SENJA(750, 60, SANTAI, "pandang", "jalan_jalan", KotaGerakId.TATAP_BULAN, GerakId.JALAN_SANTAI, 0, 0),
		TETUA_CATUR(450, 120, BEBAS, "catur", "catur", GerakId.CATUR, GerakId.JALAN_TUA, PropId.PAPAN_CATUR, 0),
		REKAN_MAKAN(360, 60, MAKAN, "@kedai", "makan", GerakId.MAKAN, GerakId.JALAN_SANTAI, PropId.SUMPIT, PropId.MANGKUK),
		ROMBONGAN_PASAR(180, 90, BEBAS, "@depan_lapak", "pasar", GerakId.TAWAR, GerakId.JALAN_SANTAI, 0, PropId.KERANJANG),
		LENTERA_LIBUR(810, 60, SANTAI, "jalan_lentera", "festival", GerakId.MAIN_LENTERA, GerakId.JALAN_SANTAI, PropId.LENTERA_TANGAN, 0);

		public final int dari;
		public final int lama;
		public final Set<KumpulanKegiatan.Blok> blok;
		public final String tujuan;
		public final String kunci;
		public final int klip;
		public final int jalan;
		public final int kanan;
		public final int kiri;

		Jenis(int dari, int lama, Set<KumpulanKegiatan.Blok> blok, String tujuan, String kunci, int klip, int jalan, int kanan, int kiri) {
			this.dari = dari;
			this.lama = lama;
			this.blok = blok;
			this.tujuan = tujuan;
			this.kunci = kunci;
			this.klip = klip;
			this.jalan = jalan;
			this.kanan = kanan;
			this.kiri = kiri;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** {@code kultivator.kota.ragam2.kelompok.<id>}. */
		public String kunciLang() {
			return "kultivator.kota.ragam2.kelompok." + id();
		}
	}

	/** One group of one day: {@code anggota} resident slots (index 0 leads), window in place minutes after 06:00. */
	public record Grup(int id, Jenis jenis, int[] anggota, int mulai, int akhir) {

		public int indeks(int slot) {
			for (int i = 0; i < anggota.length; i++) {
				if (anggota[i] == slot) {
					return i;
				}
			}
			return -1;
		}
	}

	private record Hari(long hari, List<Grup> grup, Map<Integer, Grup> perSlot) {
	}

	private static final Map<Long, Hari> CACHE = new HashMap<>();

	// ================================================================== reading

	/** The groups of a place today (drawn on first use). */
	public static List<Grup> grup(TempatHuni huni, long hari) {
		return hariIni(huni, hari).grup();
	}

	/** The group resident {@code slot} belongs to on day {@code hari}, or null. */
	public static @Nullable Grup grupDari(TempatHuni huni, int slot, long hari) {
		return hariIni(huni, hari).perSlot().get(slot);
	}

	/** Groups formed in place {@code situs} on day {@code hari} (0 when not drawn). */
	public static int jumlah(long situs, long hari) {
		Hari h = CACHE.get(situs);
		return h == null || h.hari() != hari ? 0 : h.grup().size();
	}

	/**
	 * How many people resident {@code slot} stands for in the balancer right now (DESIGN 42.4A.6: a group counts as one unit
	 * with its members' capacity): the group's size for its leader while the group runs, 0 for the other members, 1 for
	 * anyone not in a running group.
	 */
	public static int ukuran(TempatHuni huni, int slot, long waktu) {
		long hari = Math.floorDiv(waktu, 24000L);
		Grup g = grupDari(huni, slot, hari);
		int m = RencanaHarian.menit(waktu);
		if (g == null || m < g.mulai() || m >= g.akhir()) {
			return 1;
		}
		return g.indeks(slot) == 0 ? g.anggota().length : 0;
	}

	// ================================================================== the overrider part

	/** The group activity of {@code p} now (place minute {@code m}), or null when it is in no running group. */
	static @Nullable Kegiatan timpa(TempatHuni huni, Penduduk p, long hari, int m, KumpulanKegiatan.Blok blok) {
		Grup g = grupDari(huni, p.id(), hari);
		if (g == null || m < g.mulai() || m >= g.akhir() || !g.jenis().blok.contains(blok)) {
			return null;
		}
		Jenis j = g.jenis();
		if ((huni.cuaca().hujan() || huni.cuaca().badai()) && (j == Jenis.KELUARGA_SORE || j == Jenis.KEKASIH_SENJA
				|| j == Jenis.ANAK_KEJAR || j == Jenis.LENTERA_LIBUR)) {
			return null;
		}
		int i = g.indeks(p.id());
		int klip = j.klip;
		if (j == Jenis.TETUA_CATUR && i >= 2) {
			klip = KotaGerakId.TONTON_CATUR;
		} else if (j == Jenis.KEKASIH_SENJA && (m - g.mulai()) < 30) {
			klip = GerakId.BICARA_TANGAN;
		}
		int turn = j.tujuan.equals("@jalan") || j.tujuan.equals("@depan_lapak") ? (m - g.mulai()) / 20 : 0;
		return new Kegiatan(j.kunci, TUJUAN + hari + ":" + g.id() + ":" + turn, klip, 0, j.kanan, j.kiri, 0, j.jalan, false);
	}

	// ================================================================== drawing the day's groups

	private static Hari hariIni(TempatHuni huni, long hari) {
		Hari h = CACHE.get(huni.situsId);
		if (h == null || h.hari() != hari) {
			List<Grup> list = KotaConfig.warga().sebaranAktif ? susun(huni, hari) : List.of();
			Map<Integer, Grup> per = new HashMap<>();
			for (Grup g : list) {
				for (int s : g.anggota()) {
					per.put(s, g);
				}
			}
			h = new Hari(hari, list, per);
			CACHE.put(huni.situsId, h);
		}
		return h;
	}

	/** Drops the drawn groups of a place (residents changed). */
	public static void lupakan(long situs) {
		CACHE.remove(situs);
	}

	private static List<Grup> susun(TempatHuni huni, long hari) {
		SplittableRandom r = new SplittableRandom(huni.situsId * 0x9E3779B97F4A7C15L ^ hari * 0xC2B2AE3D27D4EB4FL);
		List<Penduduk> orang = new ArrayList<>();
		for (Penduduk p : huni.penduduk()) {
			if (!p.tamu() && !p.peran().stationary && p.peran().kelompok != NpcRole.Kelompok.KHUSUS && p.peran() != NpcRole.TAWANAN
					&& huni.keadaanDari(p.id()) == PendudukData.SEHAT) {
				orang.add(p);
			}
		}
		Set<Integer> dipakai = new HashSet<>();
		List<Grup> out = new ArrayList<>();
		int pekan = IramaMingguan.pekan(hari);
		Map<Integer, Warga> warga = new HashMap<>();
		Map<Integer, Integer> slotWarga = new HashMap<>();
		for (Penduduk p : orang) {
			Warga w = WargaApi.warga(huni.situsId, p.id());
			if (w != null) {
				warga.put(p.id(), w);
				slotWarga.put(w.id, p.id());
			}
		}
		// lovers first (their walk is the rarest), then friends, elders, children, families, workmates, market, lanterns
		pasangan(huni, hari, r, orang, warga, slotWarga, dipakai, out);
		sahabat(huni, hari, r, orang, warga, slotWarga, dipakai, out);
		kumpul(huni, hari, r, orang, dipakai, out, Jenis.TETUA_CATUR, p -> p.peran().kelompok == NpcRole.Kelompok.TETUA, 3, 5, 2);
		kumpul(huni, hari, r, orang, dipakai, out, Jenis.ANAK_KEJAR, p -> p.peran().kelompok == NpcRole.Kelompok.ANAK, 2, 5, 3);
		keluarga(huni, hari, r, orang, dipakai, out);
		rekan(huni, hari, r, orang, dipakai, out);
		if (pekan == IramaMingguan.PASAR_BESAR) {
			kumpul(huni, hari, r, orang, dipakai, out, Jenis.ROMBONGAN_PASAR, p -> dewasa(p), 3, 5, 3);
		}
		if (pekan == IramaMingguan.ISTIRAHAT) {
			kumpul(huni, hari, r, orang, dipakai, out, Jenis.LENTERA_LIBUR,
					p -> p.peran().kelompok == NpcRole.Kelompok.ANAK || p.peran().kelompok == NpcRole.Kelompok.PEKERJA, 3, 6, 2);
		}
		return List.copyOf(out);
	}

	private static boolean dewasa(Penduduk p) {
		return p.peran().kelompok != NpcRole.Kelompok.ANAK && p.peran().kelompok != NpcRole.Kelompok.PENJAGA;
	}

	/** True when the round leaves {@code p} in one of the kind's blocks at the window's start and middle. */
	private static boolean bebas(TempatHuni huni, Penduduk p, long hari, Jenis j, int mulai) {
		for (int m : new int[]{mulai, mulai + j.lama / 2}) {
			long waktu = hari * 24000L + (long) m * 24000L / 1440L;
			Kegiatan round = Jadwal.kegiatan(p, huni.jenis, waktu, Jadwal.Cuaca.CERAH, null);
			KumpulanKegiatan.Blok b = RencanaHarian.blok(round, Jadwal.saat(p, huni.jenis, waktu).jam());
			if (b == null || !j.blok.contains(b)) {
				return false;
			}
		}
		return true;
	}

	/** A window start for kind {@code j}: its own start shifted by up to half an hour, per group. */
	private static int mulai(Jenis j, SplittableRandom r) {
		return j.dari + r.nextInt(4) * 10;
	}

	private static void tambah(List<Grup> out, Set<Integer> dipakai, Jenis j, List<Integer> anggota, int mulai) {
		int[] a = anggota.stream().mapToInt(Integer::intValue).toArray();
		for (int s : a) {
			dipakai.add(s);
		}
		out.add(new Grup(out.size(), j, a, mulai, Math.min(1440, mulai + j.lama)));
	}

	private static void pasangan(TempatHuni huni, long hari, SplittableRandom r, List<Penduduk> orang, Map<Integer, Warga> warga,
			Map<Integer, Integer> slotWarga, Set<Integer> dipakai, List<Grup> out) {
		for (Penduduk p : orang) {
			Warga w = warga.get(p.id());
			if (w == null || dipakai.contains(p.id())) {
				continue;
			}
			for (long bond : w.hubungan) {
				JenisHubungan jenis = JenisHubungan.jenis(bond);
				Integer lain = slotWarga.get(JenisHubungan.idLain(bond));
				// lovers every day they are both free, married couples now and then (K9: only ever a man and a woman)
				boolean ikut = jenis == JenisHubungan.KEKASIH || jenis == JenisHubungan.PASANGAN && r.nextInt(4) == 0;
				if (!ikut || lain == null || dipakai.contains(lain) || lain == p.id()) {
					continue;
				}
				Penduduk q = huni.orang(lain);
				int m = mulai(Jenis.KEKASIH_SENJA, r);
				if (q != null && bebas(huni, p, hari, Jenis.KEKASIH_SENJA, m) && bebas(huni, q, hari, Jenis.KEKASIH_SENJA, m)) {
					tambah(out, dipakai, Jenis.KEKASIH_SENJA, List.of(p.id(), lain), m);
					break;
				}
			}
		}
	}

	private static void sahabat(TempatHuni huni, long hari, SplittableRandom r, List<Penduduk> orang, Map<Integer, Warga> warga,
			Map<Integer, Integer> slotWarga, Set<Integer> dipakai, List<Grup> out) {
		for (Penduduk p : orang) {
			Warga w = warga.get(p.id());
			if (w == null || dipakai.contains(p.id()) || r.nextInt(3) != 0) {
				continue;
			}
			List<Integer> grup = new ArrayList<>(List.of(p.id()));
			int m = mulai(Jenis.SAHABAT_TEH, r);
			if (!bebas(huni, p, hari, Jenis.SAHABAT_TEH, m)) {
				continue;
			}
			for (long bond : w.hubungan) {
				Integer lain = slotWarga.get(JenisHubungan.idLain(bond));
				if (JenisHubungan.jenis(bond) != JenisHubungan.SAHABAT || lain == null || dipakai.contains(lain) || grup.contains(lain)) {
					continue;
				}
				Penduduk q = huni.orang(lain);
				if (q != null && bebas(huni, q, hari, Jenis.SAHABAT_TEH, m)) {
					grup.add(lain);
					if (grup.size() >= 3) {
						break;
					}
				}
			}
			if (grup.size() >= 2) {
				tambah(out, dipakai, Jenis.SAHABAT_TEH, grup, m);
			}
		}
	}

	/** Gathers groups of {@code min..max} free residents matching {@code siapa}; at most {@code maksGrup} groups. */
	private static void kumpul(TempatHuni huni, long hari, SplittableRandom r, List<Penduduk> orang, Set<Integer> dipakai, List<Grup> out,
			Jenis j, java.util.function.Predicate<Penduduk> siapa, int min, int max, int maksGrup) {
		List<Penduduk> calon = new ArrayList<>();
		for (Penduduk p : orang) {
			if (!dipakai.contains(p.id()) && siapa.test(p)) {
				calon.add(p);
			}
		}
		for (int i = calon.size() - 1; i > 0; i--) {
			int k = r.nextInt(i + 1);
			Penduduk t = calon.get(i);
			calon.set(i, calon.get(k));
			calon.set(k, t);
		}
		int dibuat = 0;
		int i = 0;
		while (dibuat < maksGrup && i < calon.size()) {
			int ukuran = min + r.nextInt(max - min + 1);
			int m = mulai(j, r);
			List<Integer> grup = new ArrayList<>();
			while (i < calon.size() && grup.size() < ukuran) {
				Penduduk p = calon.get(i++);
				if (bebas(huni, p, hari, j, m)) {
					grup.add(p.id());
				}
			}
			if (grup.size() >= min) {
				tambah(out, dipakai, j, grup, m);
				dibuat++;
			}
		}
	}

	/** Households of two or more (one roof) walking out together: 20 % of them a day. */
	private static void keluarga(TempatHuni huni, long hari, SplittableRandom r, List<Penduduk> orang, Set<Integer> dipakai, List<Grup> out) {
		Map<Integer, List<Penduduk>> rumah = new LinkedHashMap<>();
		for (Penduduk p : orang) {
			if (p.kelompok() > 0 && !dipakai.contains(p.id())) {
				rumah.computeIfAbsent(p.kelompok(), k -> new ArrayList<>()).add(p);
			}
		}
		for (List<Penduduk> isi : rumah.values()) {
			if (isi.size() < 2 || r.nextDouble() >= 0.2D) {
				continue;
			}
			int m = mulai(Jenis.KELUARGA_SORE, r);
			List<Integer> grup = new ArrayList<>();
			for (Penduduk p : isi) {
				if (grup.size() < 6 && bebas(huni, p, hari, Jenis.KELUARGA_SORE, m)) {
					grup.add(p.id());
				}
			}
			if (grup.size() >= 2) {
				tambah(out, dipakai, Jenis.KELUARGA_SORE, grup, m);
			}
		}
	}

	/** Workers of one role (2-4) taking their midday meal together, a third of the roles a day. */
	private static void rekan(TempatHuni huni, long hari, SplittableRandom r, List<Penduduk> orang, Set<Integer> dipakai, List<Grup> out) {
		Map<NpcRole, List<Penduduk>> per = new LinkedHashMap<>();
		for (Penduduk p : orang) {
			if (p.peran().kelompok == NpcRole.Kelompok.PEKERJA && !dipakai.contains(p.id())) {
				per.computeIfAbsent(p.peran(), k -> new ArrayList<>()).add(p);
			}
		}
		for (List<Penduduk> isi : per.values()) {
			if (isi.size() < 2 || r.nextInt(3) != 0) {
				continue;
			}
			int m = mulai(Jenis.REKAN_MAKAN, r);
			int cap = 2 + r.nextInt(3);
			List<Integer> grup = new ArrayList<>();
			for (Penduduk p : isi) {
				if (grup.size() < cap && bebas(huni, p, hari, Jenis.REKAN_MAKAN, m)) {
					grup.add(p.id());
				}
			}
			if (grup.size() >= 2) {
				tambah(out, dipakai, Jenis.REKAN_MAKAN, grup, m);
			}
		}
	}

	// ================================================================== the goal

	/** Formation offsets (sideways, backwards) of members 1.. round the anchor, about 1-1.4 blocks apart. */
	private static final int[][] FORMASI = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {1, 1}, {-1, 1}, {2, 0}, {-2, 0}};

	/** {@link TempatHuni.Penyeimbang} for {@code @kelompok:} goals (null for any other goal). */
	public static TempatHuni.@Nullable Tujuan pilih(TempatHuni huni, ServerLevel level, Penduduk p, Kegiatan k, int jam, boolean kunciUntuk) {
		if (!k.tujuan().startsWith(TUJUAN)) {
			return null;
		}
		String[] part = k.tujuan().substring(TUJUAN.length()).split(":");
		if (part.length < 3) {
			return null;
		}
		long hari;
		int gid;
		int turn;
		try {
			hari = Long.parseLong(part[0]);
			gid = Integer.parseInt(part[1]);
			turn = Integer.parseInt(part[2]);
		} catch (NumberFormatException e) {
			return null;
		}
		List<Grup> list = grup(huni, hari);
		if (gid < 0 || gid >= list.size()) {
			return null;
		}
		Grup g = list.get(gid);
		int i = Math.max(0, g.indeks(p.id()));
		List<Titik> anchors = jangkar(huni, g.jenis().tujuan);
		if (anchors.isEmpty()) {
			return null;
		}
		Titik a = anchors.get((int) Math.floorMod(huni.situsId ^ hari * 31L + gid * 17L + turn * 7L, (long) anchors.size()));
		Direction face = a.hadap();
		boolean depan = g.jenis().tujuan.equals("@depan_lapak") || g.jenis().tujuan.equals("@jalan");
		BlockPos base = depan ? a.pos().relative(face, 2) : a.pos();
		Direction side = face.getClockWise();
		Direction back = depan ? face : face.getOpposite();
		if (i == 0 && !depan && (!kunciUntuk || !huni.terkunci(base, p.id())) && !huni.dipesan(base, p.id(), level.getGameTime())) {
			// the leader takes the anchor itself (the chess table, the bench, the tea seat) and books it for the window
			huni.pesan(base, p.id(), level.getGameTime() + 1200L, level.getGameTime());
			return new TempatHuni.Tujuan(base, face, a.jenis(), true);
		}
		for (int n = 0; n < FORMASI.length; n++) {
			int[] o = FORMASI[(i + n) % FORMASI.length];
			if (o[0] == 0 && o[1] == 0 && !depan) {
				continue;
			}
			BlockPos at = Belokan.berdiri(level, base.relative(side, o[0]).relative(back, o[1]));
			if (at != null && !huni.terkunci(at, p.id())) {
				Direction look = depan ? face.getOpposite() : Direction.fromYRot(Math.toDegrees(Math.atan2(-(a.pos().getX() - at.getX()),
						a.pos().getZ() - at.getZ())));
				return new TempatHuni.Tujuan(at, look, a.jenis(), false);
			}
		}
		return Belokan.sekitar(level, huni, p, base, a.jenis());
	}

	/** The anchor titik of a group goal. */
	private static List<Titik> jangkar(TempatHuni huni, String tujuan) {
		List<Titik> out = new ArrayList<>();
		switch (tujuan) {
			case "@jalan" -> {
				for (String type : List.of("jalan", "obrol", "pandang", "main")) {
					out.addAll(huni.titik(type));
					if (!out.isEmpty()) {
						break;
					}
				}
			}
			case "@depan_lapak" -> {
				out.addAll(huni.titik("lapak"));
				out.addAll(huni.titik("kerja:kios"));
			}
			case "@kedai" -> {
				out.addAll(huni.titik("meja_makan"));
				if (out.isEmpty()) {
					out.addAll(huni.titik("kursi"));
				}
			}
			default -> {
				out.addAll(huni.titik(tujuan));
				if (out.isEmpty()) {
					out.addAll(huni.titik(tujuan.equals("catur") ? "kursi" : "obrol"));
				}
			}
		}
		return out;
	}

	// ================================================================== walking together

	/**
	 * The member of the same running group walking with {@code npc} (nearest body of a fellow member within 6 blocks), or
	 * null. For 42A's {@code WargaApi.mitraJalan} lookup ({@code TubuhPenduduk.setMitraJalan} has one owner).
	 */
	public static @Nullable KultivatorNpc mitra(KultivatorNpc npc) {
		if (!(npc.level() instanceof ServerLevel level)) {
			return null;
		}
		Penduduk p = HidupApi.pendudukDari(npc);
		TempatHuni huni = p == null ? null : Pemanggil.huni(npc.situsPenduduk());
		if (huni == null) {
			return null;
		}
		long waktu = level.getServer().overworld().getOverworldClockTime();
		Grup g = grupDari(huni, p.id(), Math.floorDiv(waktu, 24000L));
		int m = RencanaHarian.menit(waktu);
		if (g == null || m < g.mulai() || m >= g.akhir()) {
			return null;
		}
		KultivatorNpc best = null;
		double bestD = 36.0D;
		for (KultivatorNpc other : level.getEntitiesOfClass(KultivatorNpc.class, npc.getBoundingBox().inflate(6.0D),
				e -> e != npc && e.isAlive() && e.situsPenduduk() == npc.situsPenduduk())) {
			Penduduk q = HidupApi.pendudukDari(other);
			if (q != null && q.id() != p.id() && g.indeks(q.id()) >= 0 && other.distanceToSqr(npc) < bestD) {
				bestD = other.distanceToSqr(npc);
				best = other;
			}
		}
		return best;
	}

	private KegiatanKelompok() {
	}
}
