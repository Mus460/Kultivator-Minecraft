package dev.kultivator.client.sosok.kulit;

import dev.kultivator.client.sekte.seragam.LambangCache;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.client.sosok.busana.BusanaKatalog;
import dev.kultivator.client.sosok.busana.SosokGear;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sekte.seragam.SeragamData;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokJalan;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The look resolver of the Sosok composer (DESIGN 40.3.4-40.4, contract C40.K1; module 40K): Sosok + Gaya + what the
 * renderer knows ({@link SosokRender.RupaMasukan}) -> a {@link SosokRupa} with every part choice and its 16 slot
 * colours. A line-by-line port of {@code tools/sosok_rupa.py resolve}; any change there must be made here too (the
 * scratch harness compares both on hundreds of seeds, {@code /sosok dump benih} vs {@code gen_sosok_pratinjau.py
 * --benih}).
 * <p>
 * One {@link SplitMix64} per draw group, in the fixed group order of 40.3.10, so adding a draw in one group never moves
 * another. Inputs beyond the Python ones: the sect uniform (colours, cut, emblem stamp) and the covering hat, both
 * taken from the render state's sources ({@code LambangCache}, {@link SosokGear}).
 */
public final class RupaResolver {

	public static final int V_KEPANG_DEPAN = 1;
	public static final int V_BAHU_KIRI = 1 << 1;
	public static final int V_SELENDANG_MELAYANG = 1 << 2;
	public static final int V_ROK_LEBAR = 1 << 3;
	public static final int V_SANGGUL_KECIL = 1 << 4;
	public static final int V_JAMBUL_SATU = 1 << 5;
	public static final int V_PUDAR = 1 << 6;
	public static final int V_LENGAN_TELANJANG = 1 << 7;

	private static final String AGE_LETTER = "AMDT";
	private static final String[] SHAPES = {"lonjong", "persegi", "hati", "bulat"};
	private static final Set<String> LASHES = Set.of("phoenix", "almond", "bulat", "lembut");
	private static final List<String> HIASAN_TIER = List.of("kain", "guan_kayu", "guan_perak", "guan_giok", "guan_emas");
	private static final Set<String> HIDE_UNDER_HAT = Set.of("sanggul", "sanggul_tinggi", "guan_rendah", "guan_tinggi", "mahkota_teratai",
			"tusuk_guan", "tusuk_buyao", "manik_buyao", "bunga_rambut", "jambul_anak", "cepol", "pita_rambut");
	private static final String[] MARK_ORDER = {"bekas_luka", "bekas_cakar", "tahi_lalat", "coreng_jelaga", "huadian", "tinta_jari", "tanda_dao"};

	private static final List<SplitMix64.Berbobot<String>> DEF_MATA_L = w("phoenix", 30, "almond", 35, "sipit", 10, "tajam", 15, "lembut", 10);
	private static final List<SplitMix64.Berbobot<String>> DEF_MATA_P = w("phoenix", 30, "almond", 30, "bulat", 20, "lembut", 20);
	private static final List<SplitMix64.Berbobot<String>> DEF_MATA_P_MUDA = w("phoenix", 20, "almond", 25, "bulat", 35, "lembut", 20);
	private static final List<SplitMix64.Berbobot<String>> MATA_TUA = w("tua", 1);
	private static final List<SplitMix64.Berbobot<String>> DEF_ALIS_L = w("pedang", 45, "tebal", 25, "tipis", 20, "berkerut", 10);
	private static final List<SplitMix64.Berbobot<String>> DEF_ALIS_P = w("daun_willow", 70, "tipis", 30);
	private static final List<SplitMix64.Berbobot<String>> DEF_MULUT_L = w("datar", 40, "tegas", 30, "senyum_tipis", 30);
	private static final List<SplitMix64.Berbobot<String>> DEF_MULUT_P = w("penuh", 45, "senyum_tipis", 30, "kecil", 25);
	private static final List<SplitMix64.Berbobot<String>> DEF_BENTUK_L = w("lonjong", 30, "persegi", 35, "hati", 10, "bulat", 25);
	private static final List<SplitMix64.Berbobot<String>> DEF_BENTUK_P = w("lonjong", 35, "persegi", 5, "hati", 35, "bulat", 25);
	private static final List<SplitMix64.Berbobot<String>> DEF_RW_D = w("kumis_tipis", 15, "jenggot_kambing", 12, "jenggot_penuh", 13,
			"jenggot_panjang", 5, "tidak", 55);
	private static final List<SplitMix64.Berbobot<String>> DEF_RW_T = w("kumis_tipis", 10, "jenggot_kambing", 15, "jenggot_penuh", 20,
			"jenggot_panjang", 25, "tidak", 55);
	private static final String[][][] DEF_RAMBUT = {
			{{"A1", "A2", "L8"}, {"L1", "L2", "L3", "L4", "L7"}, {"L1", "L2", "L4", "L7"}, {"L6"}},
			{{"A1", "P2"}, {"P2", "P3", "P4", "P5", "P6"}, {"P1", "P3", "P4", "P5", "P6"}, {"P7"}}};

	private static @Nullable Map<String, NpcRole> peranById;

	/**
	 * The look of a body (C40.K1). Returns null when the recipe cannot be read (bodies then keep the class fallback
	 * atlas). Cheap enough to run on every change of the inputs, not every frame (40R caches it per body).
	 */
	public static @Nullable SosokRupa dari(Sosok sosok, Gaya gaya, SosokRender.RupaMasukan m) {
		Resep r = Resep.get();
		if (r == null) {
			return null;
		}
		RupaKunci rk = RupaKunci.parse(m.kunciRupa());
		String kunci = rk.kunci();
		String cadangan = cadangan(kunci);
		int dao = rk.dao() >= 0 ? rk.dao() : m.dao();
		int pangkatMasuk = rk.pangkat() >= 0 ? rk.pangkat() : -1;
		Resep.Baris row0 = kunci.isEmpty() ? null : r.peran.get("peran." + kunci);
		Resep.Baris row = row0 != null ? row0 : (cadangan != null ? r.peran.get("peran." + cadangan) : null);
		if (row == null) {
			row = r.peran.get("peran.kelompok.SIPIL");
		}
		if (row == null) {
			return null;
		}
		if (pangkatMasuk < 0 && row.pangkat < 0 && m.sectRank() >= 0 && row.murid) {
			pangkatMasuk = m.sectRank();
		}
		@Nullable LambangCache.Entry seragamEntry = m.seragam();
		if (seragamEntry != null && (seragamEntry.data.flags() & SeragamData.NO_ROBE) != 0) {
			seragamEntry = null;
		}
		int pangkatSeragam = Math.max(0, Math.min(5, pangkatMasuk >= 0 ? pangkatMasuk : Math.max(0, row.pangkat)));
		int[] seragam = null;
		if (seragamEntry != null) {
			int robe = seragamEntry.data.robeRgb(pangkatSeragam);
			int trim = seragamEntry.data.trimRgb(pangkatSeragam);
			int fallbackRobe = dao >= 0 && dao < r.dao.size() ? r.dao.get(dao).kain() : 0x3A5A8C;
			seragam = new int[] {robe < 0 ? fallbackRobe : robe, trim < 0 ? 0xE8E4D8 : trim, seragamEntry.data.emblem().fieldRgb(),
					seragamEntry.data.cutOf(pangkatSeragam)};
		}
		Masukan in = new Masukan(kunci, rk.sekte(), m.realm(), dao, pangkatMasuk, m.physique(), seragam,
				SosokGear.menutup(m.kotaGearKind(), m.headwearKind()), gaya.jalan(), gaya.luka(), gaya.busanaKhusus());
		SosokRupa rupa = resolve(r, row, sosok.kelamin(), sosok.usia(), sosok.lenganRamping(), sosok.benih(), in);
		if (seragamEntry != null && rupa != null) {
			int pangkat = in.pangkat >= 0 ? in.pangkat : row.pangkat;
			LambangSalin.Stempel stempel = LambangSalin.untuk(seragamEntry, pangkat, m.realm());
			if (stempel != null) {
				rupa = new SosokRupa(rupa.kelamin(), rupa.usia(), rupa.ramping(), rupa.benih(), rupa.palet(), rupa.lapisan(), rupa.kubusRambut(),
						rupa.kubusBusana(), rupa.varian(), rupa.bertopi(), rupa.kelas(), rupa.busana(), rupa.rambut(), rupa.mata(), rupa.prop(),
						rupa.sikap(), rupa.jalan(), stempel, SosokRupa.hitungKunci(rupa.kelamin(), rupa.usia(), rupa.ramping(), rupa.benih(),
						rupa.palet(), rupa.lapisan(), rupa.kubusRambut(), rupa.kubusBusana(), stempel));
			}
		}
		return rupa;
	}

	/** The schedule-group fallback row of an NPC role key ({@code kelompok.<GROUP>}), or null. */
	private static @Nullable String cadangan(String kunci) {
		if (peranById == null) {
			Map<String, NpcRole> map = new HashMap<>();
			for (NpcRole role : NpcRole.values()) {
				map.put(role.id(), role);
			}
			peranById = map;
		}
		NpcRole role = peranById.get(kunci.toLowerCase(Locale.ROOT));
		return role == null ? null : "kelompok." + role.kelompok.name();
	}

	/** The inputs of {@code sosok_rupa.masukan}; {@code seragam} = {robe, trim, embroidery, cut} or null. */
	record Masukan(String kunci, @Nullable String sekte, int realm, int dao, int pangkat, int fisik, int[] seragam, boolean topiMenutup,
				   int jalan, int luka, int busanaKhusus) {
	}

	// ================================================================ the port of sosok_rupa.resolve

	static @Nullable SosokRupa resolve(Resep R, Resep.Baris row, int kel, int usia, boolean ramping, int benih, Masukan m) {
		boolean fem = kel == Sosok.PEREMPUAN;
		String jalanId = SosokJalan.id(m.jalan);
		Resep.Jalan jrow = jalanId == null ? null : R.jalan.get("jalan." + jalanId);
		int realm = m.realm;
		int dao = m.dao;
		int fisik = m.fisik;
		boolean daoAda = dao >= 0 && dao < 8 && dao < R.dao.size();
		int pangkat = m.pangkat >= 0 ? m.pangkat : row.pangkat;
		int[] seragam = row.murid || row.kain.contains("sekte") || row.kain.contains("sekte_pudar") ? m.seragam : null;
		String W = ramping ? "ramping" : "lebar";
		String S = fem ? "p" : "l";
		int bk = m.busanaKhusus;
		String tema = row.tema;

		// ------------------------------------------------------------ skin
		SplitMix64 rk = SplitMix64.untuk(benih, "kulit");
		List<SplitMix64.Berbobot<Integer>> skinW = new ArrayList<>();
		for (int i = 0; i < R.kulit.size(); i++) {
			int wgt = R.kulit.get(i).bobot();
			if (tema.equals("oasis")) {
				wgt = i >= 3 && i <= 6 ? wgt * 2 : wgt;
			} else if (tema.equals("gunung")) {
				wgt = i <= 2 ? wgt * 3 / 2 : wgt;
			}
			skinW.add(new SplitMix64.Berbobot<>(i, wgt));
		}
		Integer skinPick = rk.pick(skinW);
		int skinId = skinPick == null ? 1 : skinPick;
		int gelap = Math.max(row.kulitGelap, Math.max(tema.equals("pelabuhan") ? 40 : 0, jrow != null ? jrow.kulitGelap : 0));
		if (gelap > 0 && rk.chance(gelap)) {
			skinId = Math.min(6, skinId + 1);
		}
		if (row.kulit.equals("arwah")) {
			skinId = 7;
		}
		int skin = R.kulit.get(Math.min(skinId, R.kulit.size() - 1)).rgb();
		if (fisik == 4) {
			skin = Palet.skala(skin, 104, 102, 94);
		}

		// ------------------------------------------------------------ hair colour
		SplitMix64 rh = SplitMix64.untuk(benih, "rambut_warna");
		List<SplitMix64.Berbobot<String>> hw = new ArrayList<>();
		for (Map.Entry<String, Resep.Warna> e : R.rambutWarna.entrySet()) {
			hw.add(new SplitMix64.Berbobot<>(e.getKey(), e.getValue().bobot() * (tema.equals("oasis") && e.getKey().equals("cokelat_tua") ? 2 : 1)));
		}
		String hairName = rh.pick(hw);
		if (usia == Sosok.TUA) {
			hairName = rh.chance(40) ? "abu" : "putih";
		}
		String forced = jrow != null && !jrow.rambutWarna.isEmpty() ? jrow.rambutWarna : "";
		if (forced.isEmpty()) {
			forced = row.rambutWarna;
		}
		if (row.rambutMerah != 0 && rh.chance(row.rambutMerah)) {
			forced = forced.isEmpty() ? "merah_tua" : forced;
		}
		if (dao == 1 && realm >= 4) {
			forced = forced.isEmpty() ? "merah_tua" : forced;
		}
		if (dao == 2 && realm >= 4 && forced.isEmpty()) {
			forced = "perak";
		}
		boolean kilapEmas = false;
		if (forced.equals("hitam_biru_emas")) {
			forced = "hitam_biru";
			kilapEmas = true;
		}
		if (!forced.isEmpty()) {
			hairName = forced;
		}
		Resep.Warna hairRow = hairName == null ? null : R.rambutWarna.get(hairName);
		int hair = hairRow != null ? hairRow.rgb() : 0x1B191E;

		// ------------------------------------------------------------ iris
		SplitMix64 ri = SplitMix64.untuk(benih, "iris");
		List<SplitMix64.Berbobot<Integer>> irisW = new ArrayList<>();
		for (Resep.Warna i : R.iris) {
			irisW.add(new SplitMix64.Berbobot<>(i.rgb(), i.bobot()));
		}
		Integer irisPick = ri.pick(irisW);
		int iris = irisPick == null ? 0x3B2418 : irisPick;
		int pupil = 0x15121A;
		int sclera = usia == Sosok.ANAK ? 0xF4F1EA : (usia == Sosok.TUA ? 0xE2DACB : 0xEDE8E0);
		int lipsF = 0xB85A5E;
		if (daoAda && R.dao.get(dao).iris() >= 0 && realm >= R.dao.get(dao).irisRanah()) {
			iris = R.dao.get(dao).iris();
		}
		if (realm >= 8) {
			iris = 0xF2C94C;
		}
		if (fisik == 1) {
			iris = 0x6A8A3A;
			lipsF = 0x6A3A7A;
		} else if (fisik == 2) {
			iris = 0xC8CCD8;
		} else if (fisik == 3) {
			iris = 0xA0702A;
		} else if (fisik == 4) {
			iris = 0xD8A83A;
		} else if (fisik == 5 && realm >= 3) {
			iris = 0x3A1A4A;
		} else if (fisik == 7) {
			iris = 0xC9A24A;
			pupil = 0x2A0E05;
		} else if (fisik == 9) {
			iris = 0xA8A8A8;
		}
		if (!row.iris.isEmpty()) {
			iris = Palet.hex(row.iris);
		}
		if (jrow != null && !jrow.iris.isEmpty()) {
			iris = Palet.hex(jrow.iris);
		}

		// ------------------------------------------------------------ face parts
		SplitMix64 rb = SplitMix64.untuk(benih, "bentuk");
		String bentuk = usia == Sosok.ANAK ? "bulat" : orDefault(rb.pick(!row.bentuk.isEmpty() ? row.bentuk : (fem ? DEF_BENTUK_P : DEF_BENTUK_L)), "lonjong");
		SplitMix64 rm = SplitMix64.untuk(benih, "mata");
		String mata;
		if (fisik == 7) {
			mata = "ganda";
		} else if (usia == Sosok.ANAK) {
			mata = "anak";
		} else {
			List<SplitMix64.Berbobot<String>> lst = jrow != null && !jrow.mata.isEmpty() ? jrow.mata : row.mata;
			if (lst.isEmpty()) {
				lst = usia == Sosok.TUA ? MATA_TUA : (fem ? (usia == Sosok.MUDA ? DEF_MATA_P_MUDA : DEF_MATA_P) : DEF_MATA_L);
			}
			mata = orDefault(rm.pick(lst), "almond");
			if (mata.equals("anak")) {
				mata = "almond";
			}
		}
		SplitMix64 ra = SplitMix64.untuk(benih, "alis");
		List<SplitMix64.Berbobot<String>> alisLst = jrow != null && fem && !jrow.alisP.isEmpty() ? jrow.alisP : (fem ? row.alisP : row.alisL);
		if (alisLst.isEmpty()) {
			alisLst = fem ? DEF_ALIS_P : DEF_ALIS_L;
		}
		String alis = orDefault(ra.pick(alisLst), "tipis");
		if (mata.equals("tajam") && (alis.equals("daun_willow") || alis.equals("tipis")) && !fem) {
			alis = "berkerut";
		}
		SplitMix64 rmu = SplitMix64.untuk(benih, "mulut");
		String mulut;
		if (usia == Sosok.ANAK) {
			mulut = "kecil";
		} else if (usia == Sosok.TUA && row.mulut.isEmpty()) {
			mulut = "tua";
		} else {
			mulut = orDefault(rmu.pick(!row.mulut.isEmpty() ? row.mulut : (fem ? DEF_MULUT_P : DEF_MULUT_L)), "datar");
		}

		// facial hair (men, adult or old)
		String rwName = null;
		SplitMix64 rr = SplitMix64.untuk(benih, "rambut_wajah");
		if (!fem && usia >= Sosok.DEWASA) {
			List<SplitMix64.Berbobot<String>> items;
			if (!row.rw.isEmpty()) {
				items = new ArrayList<>();
				int sum = 0;
				for (Map.Entry<String, Integer> e : row.rw.entrySet()) {
					items.add(new SplitMix64.Berbobot<>(e.getKey(), e.getValue()));
					sum += e.getValue();
				}
				if (100 - sum > 0) {
					items.add(new SplitMix64.Berbobot<>("tidak", 100 - sum));
				}
			} else {
				items = usia == Sosok.TUA ? DEF_RW_T : DEF_RW_D;
			}
			rwName = rr.pick(items);
			if ("tidak".equals(rwName)) {
				rwName = null;
			}
		}

		// marks
		SplitMix64 rt = SplitMix64.untuk(benih, "tanda");
		List<String> marks = new ArrayList<>();
		Map<String, Integer> tanda = new HashMap<>(row.tanda);
		if (fem) {
			tanda.putAll(row.tandaP);
		}
		if (jrow != null) {
			tanda.putAll(jrow.tanda);
			if (fem) {
				tanda.putAll(jrow.tandaP);
			}
		}
		for (String name : MARK_ORDER) {
			int pct = tanda.getOrDefault(name, name.equals("tahi_lalat") ? 8 : 0);
			if (pct > 0 && rt.chance(pct)) {
				marks.add(name);
			}
		}
		if ((row.murid || row.tandaRanah) && realm >= 4 && !marks.contains("tanda_dao")) {
			marks.add("tanda_dao");
		}
		if (usia == Sosok.ANAK) {
			marks.removeIf(k -> !k.equals("tahi_lalat"));
		}

		// ------------------------------------------------------------ hair style
		SplitMix64 rg = SplitMix64.untuk(benih, "gaya_rambut");
		char letter = AGE_LETTER.charAt(Math.max(0, Math.min(3, usia)));
		List<String> opts = jrow != null ? cocokRambut(R, fem ? jrow.rambutP : jrow.rambutL, letter, fem) : new ArrayList<>();
		if (opts.isEmpty()) {
			opts = cocokRambut(R, fem ? row.rambutP : row.rambutL, letter, fem);
		}
		if (opts.isEmpty()) {
			opts = List.of(DEF_RAMBUT[fem ? 1 : 0][Math.max(0, Math.min(3, usia))]);
		}
		if (bk == Gaya.BUSANA_TIDUR && (usia == Sosok.MUDA || usia == Sosok.DEWASA)) {
			opts = List.of(fem ? "P6" : "L4");
		}
		List<SplitMix64.Berbobot<String>> hairOpts = new ArrayList<>();
		for (String h : opts) {
			hairOpts.add(new SplitMix64.Berbobot<>(h, 1));
		}
		String rambut = orDefault(rg.pick(hairOpts), fem ? "P3" : "L1");
		Resep.GayaRambut gr = R.gayaRambut.get(rambut);
		if (gr == null) {
			rambut = fem ? "P3" : "L1";
			gr = R.gayaRambut.get(rambut);
		}

		// ------------------------------------------------------------ garment style
		SplitMix64 rs = SplitMix64.untuk(benih, "gaya");
		String busana = rs.pick(fem ? row.gayaP : row.gayaL);
		if (busana == null) {
			busana = fem ? "HANFU_P" : "HANFU_L";
		}
		boolean rokPendekP = busana.equals("HANFU_P_PENDEK");
		if (rokPendekP) {
			busana = "HANFU_P";
		}
		if (usia == Sosok.ANAK) {
			if (!row.gayaAnak.isEmpty()) {
				busana = orDefault(rs.pick(row.gayaAnak), "ANAK");
			} else if (!busana.equals("COMPANG") && !busana.equals("KAFAN") && !busana.equals("ANAK")) {
				busana = "ANAK";
			}
		}
		if (seragam != null) {
			int cut = seragam[3];
			busana = cut == SeragamData.CUT_PANJANG ? (fem ? "HANFU_P" : "HANFU_L") : (cut == SeragamData.CUT_PENDEK ? "PENDEK" : "TEMPUR");
		} else if (jrow != null && !jrow.gayaTanpaSeragam.isEmpty() && row.murid) {
			busana = jrow.gayaTanpaSeragam;
		}
		if (jrow != null && !jrow.gayaIntiEmas.isEmpty() && realm >= 3 && seragam == null) {
			busana = jrow.gayaIntiEmas;
		}
		if (row.taoDao && seragam == null && (dao == 6 || dao == 7)) {
			busana = "TAO";
		}
		if (row.murid && realm >= 7 && seragam == null) {
			busana = "ABADI";
		}
		Resep.GayaBusana gb = R.gayaBusana.get(busana);
		if (gb == null) {
			busana = fem ? "HANFU_P" : "HANFU_L";
			gb = R.gayaBusana.get(busana);
			if (gb == null) {
				return null;
			}
		}

		// ------------------------------------------------------------ colours
		SplitMix64 rc = SplitMix64.untuk(benih, "kain");
		String clanKey = m.sekte != null && !m.sekte.isEmpty() ? m.sekte : m.kunci;
		int klan = R.klan.isEmpty() ? 0x3A5A8C : R.klan.get(Integer.remainderUnsigned(SplitMix64.fnv32(clanKey), R.klan.size()));
		List<String> toks = row.kain.size() >= 4 ? row.kain : List.of("-", "-", "-", "-");
		Kain kainOf = new Kain(R, dao, daoAda, realm, pangkat, seragam, klan, rc);
		int[] defaultKain = new int[4];
		for (int i = 0; i < 4; i++) {
			String t = i < gb.kain().size() ? gb.kain().get(i) : "-";
			Integer c = t.startsWith("#") ? Integer.valueOf(Palet.hex(t)) : R.bernama(t);
			defaultKain[i] = c != null ? c : 0x808080;
		}
		int kain1 = kainOf.of(toks.get(0), 0, defaultKain[0]);
		int kain2 = kainOf.of(toks.get(1), 1, defaultKain[1]);
		int tepi = kainOf.of(toks.get(2), 2, Palet.skala(kain1, 70, 70, 70));
		int logam = kainOf.of(toks.get(3), 3, R.logam.getOrDefault("perunggu", 0x8C6A3A));
		if (row.murid && seragam == null && toks.get(2).equals("pangkat")) {
			tepi = R.tepiPangkat.get(Math.max(0, Math.min(5, pangkat)));
		}
		if (jrow != null && !jrow.kainTepi.isEmpty()) {
			tepi = Palet.hex(jrow.kainTepi);
		}
		if (jrow != null && !jrow.kain2.isEmpty()) {
			Integer c = R.bernama(jrow.kain2);
			kain2 = c != null ? c : kain2;
		}
		if (row.murid && realm >= 3) {
			logam = R.logam.getOrDefault("emas_kusam", 0xB8963E);
		}
		int bright = rc.below(13) - 6;
		int tint = rc.below(9) - 4;
		if (seragam == null && !toks.get(0).equals("sekte") && !toks.get(0).equals("sekte_pudar")) {
			kain1 = Palet.skala(kain1, 100 + bright + tint, 100 + bright, 100 + bright - tint);
		}
		int sulam = tepi;
		if (daoAda) {
			sulam = R.dao.get(dao).sulam();
		}
		if (seragam != null) {
			sulam = seragam[2];
		}
		if (bk == Gaya.BUSANA_BERKABUNG) {
			kain1 = 0xE6E2D6;
			kain2 = 0xEDE9DE;
			tepi = 0xCFC9B8;
		}
		int kilap = Palet.tambah(Palet.skala(hair, 125, 125, 125), 5, 8, 15);
		if (kilapEmas) {
			kilap = 0xD8C080;
		} else if (realm >= 6 && daoAda) {
			kilap = Palet.campurWarna(kilap, R.dao.get(dao).sulam(), 40);
		}
		int alisRgb = usia == Sosok.TUA ? hair : Palet.skala(hair, 80, 80, 80);
		if (alis.equals("putih_panjang")) {
			alisRgb = 0xE6E3DC;
		}
		int bibir = fem ? lipsF : Palet.skala(skin, 92, 75, 74);
		if (fem && fisik == 1) {
			bibir = 0x6A3A7A;
		}
		int[] palet = {skin, Palet.skala(skin, 100, 86, 84), hair, kilap, sclera, iris, pupil, alisRgb, bibir, kain1, kain2, tepi, logam, 0x5FA88C,
				sulam, 0x5A3A22};

		// ------------------------------------------------------------ extras
		List<String> tambahan = new ArrayList<>(row.tambahan);
		SplitMix64 rx = SplitMix64.untuk(benih, "tambahan");
		for (Map.Entry<String, Integer> e : row.tambahanAcak.entrySet()) {
			if (rx.chance(e.getValue())) {
				tambahan.add(e.getKey());
			}
		}
		if (jrow != null) {
			tambahan.addAll(jrow.tambahan);
		}
		switch (bk) {
			case Gaya.BUSANA_BEPERGIAN -> tambahan.add("bepergian");
			case Gaya.BUSANA_PESTA -> tambahan.add("pesta");
			case Gaya.BUSANA_KERJA -> {
				tambahan.add("kerja");
				tambahan.add("celemek");
			}
			case Gaya.BUSANA_ZIRAH -> tambahan.add("zirah");
			case Gaya.BUSANA_PENYAMARAN -> tambahan.add("penyamaran");
			default -> {
			}
		}

		// ------------------------------------------------------------ cubes
		List<String> hairCubes = new ArrayList<>(gr == null ? List.of() : gr.kubus());
		List<String> bodyCubes = new ArrayList<>();
		for (String c : gb.kubus()) {
			bodyCubes.addAll(R.kelompokKubus.getOrDefault(c, List.of(c)));
		}
		if (rokPendekP) {
			bodyCubes.replaceAll(c -> c.replace("rok_panjang", "rok_pendek"));
		}
		int varian = 0;
		if (gb.lebar()) {
			varian |= V_ROK_LEBAR;
		}
		if (gr != null && gr.kecil()) {
			varian |= V_SANGGUL_KECIL;
		}
		if (gr != null && gr.satu()) {
			varian |= V_JAMBUL_SATU;
		}
		if (rambut.equals("P5") && (tema.equals("oasis") || ((benih >> 3) & 1) != 0)) {
			varian |= V_KEPANG_DEPAN;
		}
		List<String> sheetsExtra = new ArrayList<>();
		String headCloth = !row.kepala.isEmpty() ? row.kepala : gb.kepala();
		Set<String> hide = new HashSet<>();
		int tepiNaik = 0;
		for (String t : tambahan) {
			if (t.equals("sabuk_kubus")) {
				bodyCubes.add("sabuk");
				continue;
			}
			if (t.equals("kerah_tinggi_l")) {
				if (!fem) {
					bodyCubes.add("kerah_tinggi");
				}
				continue;
			}
			Resep.Tambahan spec = R.tambahan.get(t);
			if (spec == null) {
				continue;
			}
			bodyCubes.addAll(spec.kubus());
			if (!spec.lembar().isEmpty()) {
				sheetsExtra.add(spec.lembar());
			}
			if (!spec.kepala().isEmpty()) {
				headCloth = spec.kepala();
			}
			hide.addAll(spec.sembunyi());
			tepiNaik += spec.tepiNaik();
			if (spec.varian().equals("bahu_kiri")) {
				varian |= V_BAHU_KIRI;
			}
		}
		bodyCubes.addAll(row.kubus);
		if (fem) {
			bodyCubes.addAll(row.kubusP);
		}
		if (jrow != null) {
			bodyCubes.addAll(jrow.kubus);
			if (fem) {
				bodyCubes.addAll(jrow.kubusP);
			}
			tepiNaik += jrow.tepiNaik;
		}
		if (row.selendangP && fem) {
			bodyCubes.add("selendang_1");
			bodyCubes.add("selendang_2");
		}
		if (row.murid && realm >= 3 && !bodyCubes.contains("liontin_giok")) {
			bodyCubes.add("liontin_giok");
		}
		if (row.murid && realm >= 7) {
			bodyCubes.add("selendang_1");
			bodyCubes.add("selendang_2");
			bodyCubes.add("selendang_punggung");
		}
		if (row.melayang || row.murid && realm >= 7) {
			varian |= V_SELENDANG_MELAYANG;
		}
		if (row.pudar) {
			varian |= V_PUDAR;
		}
		if (row.lenganTelanjang) {
			varian |= V_LENGAN_TELANJANG;
		}
		if (row.murid && pangkat >= 0) {
			bodyCubes.add("lencana_giok");
		}
		if (bk == Gaya.BUSANA_TIDUR) {
			bodyCubes.clear();
		}
		if ("jenggot_kambing".equals(rwName) || "jenggot_penuh".equals(rwName)) {
			hairCubes.add("jenggot_pendek");
		} else if ("jenggot_panjang".equals(rwName)) {
			hairCubes.add("jenggot_panjang");
		} else if ("kumis_naga".equals(rwName)) {
			hairCubes.add("kumis_naga");
		}
		if (alis.equals("putih_panjang")) {
			hairCubes.add("alis_panjang");
		}
		List<String> extraHair = new ArrayList<>(row.kubus);
		if (jrow != null) {
			extraHair.addAll(jrow.kubus);
		}
		for (String c : extraHair) {
			if (BusanaKatalog.rambut(c) >= 0) {
				hairCubes.add(c);
			}
		}

		// ornament (hiasan)
		String hiasan = fem ? row.hiasanP : row.hiasanL;
		if (hiasan.isEmpty()) {
			hiasan = rambut.equals("L2") ? "guan_kayu" : (rambut.equals("L1") || rambut.equals("L6") || rambut.equals("L7") ? "kain" : "");
		}
		if (realm >= 5 && HIASAN_TIER.contains(hiasan) && !hiasan.equals(HIASAN_TIER.get(HIASAN_TIER.size() - 1))) {
			hiasan = HIASAN_TIER.get(HIASAN_TIER.indexOf(hiasan) + 1);
		}
		if (row.murid && realm >= 7 && pangkat == 5 && !fem) {
			hiasan = "mahkota";
		}
		Map<String, String> ornament = new HashMap<>();
		boolean hasBun = hairCubes.contains("sanggul");
		if (hiasan.startsWith("guan") || hiasan.equals("kain") || hiasan.equals("kain_tepi")) {
			String mat = hiasan.equals("kain") || hiasan.equals("kain_tepi") ? "kain" : bagian1(hiasan);
			if (hasBun) {
				hairCubes.removeIf(c -> c.equals("guan_rendah") || c.equals("guan_tinggi"));
				if (mat.equals("kain") || rambut.equals("L1") || rambut.equals("L6") || rambut.equals("L7") || rambut.equals("P3")) {
					hairCubes.add("guan_rendah");
					ornament.put("guan_rendah", "rambut/kubus/guan_rendah_" + mat);
				} else {
					hairCubes.add("guan_tinggi");
					ornament.put("guan_tinggi", "rambut/kubus/guan_tinggi_" + mat);
				}
			}
		} else if (hiasan.equals("mahkota") && hasBun) {
			hairCubes.removeIf(c -> c.equals("guan_rendah") || c.equals("guan_tinggi"));
			hairCubes.add("mahkota_teratai");
		} else if (hiasan.startsWith("tusuk")) {
			String mat = bagian1(hiasan);
			if (hasBun || hairCubes.contains("sanggul_rendah")) {
				hairCubes.add("tusuk_guan");
				ornament.put("tusuk_guan", "rambut/kubus/tusuk_guan_" + mat);
			}
		} else if (hiasan.startsWith("buyao")) {
			String mat = hiasan.contains("perak") ? "perak" : "emas";
			if (hairCubes.contains("sanggul_tinggi")) {
				ornament.put("tusuk_buyao", "rambut/kubus/tusuk_buyao_" + mat);
				ornament.put("manik_buyao", "rambut/kubus/manik_buyao_" + mat);
			}
			if (hiasan.endsWith("bunga")) {
				hairCubes.add("bunga_rambut");
				ornament.put("bunga_rambut", "rambut/kubus/bunga_rambut_plum");
			}
		} else if (hiasan.startsWith("bunga")) {
			hairCubes.add("bunga_rambut");
			ornament.put("bunga_rambut", "rambut/kubus/bunga_rambut_" + bagian1(hiasan));
		}
		if (hairCubes.contains("guan_tinggi")) {
			ornament.putIfAbsent("guan_tinggi", "rambut/kubus/guan_tinggi_kayu");
		}
		if (hairCubes.contains("guan_rendah")) {
			ornament.putIfAbsent("guan_rendah", "rambut/kubus/guan_rendah_kain");
		}
		if (hairCubes.contains("tusuk_guan")) {
			ornament.putIfAbsent("tusuk_guan", "rambut/kubus/tusuk_guan_kayu");
		}
		if (hairCubes.contains("tusuk_buyao") && !ornament.containsKey("tusuk_buyao")) {
			ornament.put("tusuk_buyao", "rambut/kubus/tusuk_buyao_perak");
			ornament.put("manik_buyao", "rambut/kubus/manik_buyao_perak");
		}
		if (hairCubes.contains("bunga_rambut")) {
			ornament.putIfAbsent("bunga_rambut", "rambut/kubus/bunga_rambut_plum");
		}

		boolean bertopi = m.topiMenutup || headCloth.equals("kerudung") || headCloth.equals("tudung") || bodyCubes.contains("caping_bambu");
		if (bertopi) {
			boolean keepRibbon = hairCubes.contains("ekor_kuda_1");
			hairCubes.removeIf(c -> HIDE_UNDER_HAT.contains(c) && !(c.equals("pita_rambut") && keepRibbon));
		}
		bodyCubes.removeIf(hide::contains);

		// ------------------------------------------------------------ layers (40.3.9 order)
		List<SosokRupa.Lapisan> L = new ArrayList<>();
		full(L, "kulit/dasar_" + S + "_" + usia + "_" + W);
		full(L, "wajah/bentuk_" + S + "_" + indexOf(SHAPES, bentuk));
		if (usia != Sosok.ANAK) {
			face8(L, "wajah/hidung_" + (usia == Sosok.TUA ? "tua" : S));
		}
		for (String mk : new String[] {"bekas_luka", "tahi_lalat", "coreng_jelaga"}) {
			if (marks.contains(mk)) {
				face8(L, "wajah/tanda_" + mk);
			}
		}
		if (marks.contains("bekas_cakar")) {
			full(L, "wajah/tanda_bekas_cakar");
		}
		if (marks.contains("huadian") && fem) {
			face8(L, "wajah/tanda_huadian" + (row.kelas.equals("bangsawan") ? "_emas" : ""));
		}
		if (marks.contains("tanda_dao")) {
			face8(L, "wajah/tanda_tanda_dao");
		}
		L.add(SosokRupa.Lapisan.wajah(SosokRupa.ALIS, "wajah/alis_" + alis, 8, 8));
		L.add(SosokRupa.Lapisan.wajah(SosokRupa.MATA, "wajah/mata_" + mata + (fem && LASHES.contains(mata) ? "_p" : ""), 8, 8));
		L.add(SosokRupa.Lapisan.wajah(SosokRupa.MULUT, "wajah/mulut_" + mulut, 8, 8));
		if (rwName != null) {
			full(L, "wajah/rambut_" + rwName);
		}
		if (fisik == 8) {
			full(L, "wajah/tanda_nadi_sesat");
		}
		full(L, "busana/dalam_" + S + "_" + W);
		if (bk != Gaya.BUSANA_TIDUR) {
			full(L, "busana/" + busana + "_" + W);
			int level = pangkat < 0 ? 0 : (pangkat <= 1 ? 0 : (pangkat <= 3 ? 1 : 2));
			level = Math.min(2, level + tepiNaik);
			if (level > 0) {
				full(L, "busana/" + busana + "_tepi" + level + "_" + W);
			}
			String buzi = !row.buzi.isEmpty() ? row.buzi : gb.buzi();
			if ((busana.equals("PEJABAT") || busana.equals("HAKIM_BAWAH")) && !buzi.isEmpty() && bk != Gaya.BUSANA_BERKABUNG) {
				full(L, "busana/buzi_" + buzi);
			}
		}
		if (fisik == 6) {
			full(L, "wajah/tanda_tulang_tertinggi");
		}
		if (marks.contains("tinta_jari")) {
			full(L, "wajah/tanda_tinta_jari_" + W);
		}
		for (String sx : sheetsExtra) {
			full(L, "busana/tambahan_" + sx + "_" + W);
		}
		if (bk == Gaya.BUSANA_TIDUR) {
			full(L, "busana/tambahan_tidur_" + W);
		}
		int luka = m.luka != 0 ? m.luka : row.luka;
		if (luka == 2) {
			full(L, "luka/perban_lengan_" + W);
		} else if (luka == 1) {
			full(L, "luka/perban_kaki");
		}
		String kaki = !row.kaki.isEmpty() ? row.kaki : gb.kaki();
		if (bk == Gaya.BUSANA_TIDUR) {
			kaki = "telanjang";
		}
		if (!kaki.equals("telanjang")) {
			full(L, "busana/kaki_" + kaki);
		}
		full(L, "rambut/" + rambut + "_dasar");
		if (!rambut.equals("L5") && !rambut.equals("A1")) {
			full(L, "rambut/" + rambut + "_" + (bertopi ? "bertopi" : "topi"));
		}
		if (row.buddha && rambut.equals("L5")) {
			full(L, "rambut/titik_tahbisan");
		}
		if (bk == Gaya.BUSANA_BERKABUNG) {
			headCloth = "ikat_putih";
		}
		if (!headCloth.isEmpty()) {
			full(L, "busana/kepala_" + headCloth);
		}
		Set<String> hairSet = new HashSet<>(hairCubes);
		Set<String> bodySet = new HashSet<>(bodyCubes);
		int kubusRambut = 0;
		long kubusBusana = 0L;
		for (int i = 0; i < BusanaKatalog.RAMBUT.size(); i++) {
			BusanaKatalog.Kubus k = BusanaKatalog.RAMBUT.get(i);
			if (hairSet.contains(k.id())) {
				kubusRambut |= 1 << i;
				L.add(SosokRupa.Lapisan.biasa(ornament.getOrDefault(k.id(), "rambut/kubus/" + k.id()), 0, 0, k.jejakW(), k.jejakH(), k.u(), k.v()));
			}
		}
		for (int i = 0; i < BusanaKatalog.BUSANA.size(); i++) {
			BusanaKatalog.Kubus k = BusanaKatalog.BUSANA.get(i);
			if (bodySet.contains(k.id())) {
				kubusBusana |= 1L << i;
				String sheet = "busana/kubus/" + k.id();
				if (k.id().equals("lencana_giok")) {
					sheet += "_" + R.lencanaPangkat.get(Math.max(0, Math.min(5, pangkat)));
				}
				L.add(SosokRupa.Lapisan.biasa(sheet, 0, 0, k.jejakW(), k.jejakH(), k.u(), k.v()));
			}
		}

		int sikap = fem ? row.sikapP : row.sikapL;
		if (jrow != null && jrow.sikap != 0) {
			sikap = jrow.sikap;
		}
		SplitMix64 rp = SplitMix64.untuk(benih, "prop");
		int prop = row.prop;
		for (Map.Entry<String, Integer> e : row.propAcak.entrySet()) {
			if (rp.chance(e.getValue())) {
				prop = Integer.parseInt(e.getKey());
				break;
			}
		}
		if (jrow != null && jrow.prop != 0) {
			prop = jrow.prop;
		}
		SosokRupa.Lapisan[] lapisan = L.toArray(new SosokRupa.Lapisan[0]);
		long kunci = SosokRupa.hitungKunci(kel, usia, ramping, benih, palet, lapisan, kubusRambut, kubusBusana, null);
		return new SosokRupa(kel, usia, ramping, benih, palet, lapisan, kubusRambut, kubusBusana, varian, bertopi, row.kelas, busana, rambut, mata,
				prop, sikap, row.jalan, null, kunci);
	}

	/** Hair styles of a list that fit the age letter and sex (DESIGN 40.3.5 K/U columns). */
	private static List<String> cocokRambut(Resep R, List<String> list, char letter, boolean fem) {
		List<String> out = new ArrayList<>();
		for (String h : list) {
			Resep.GayaRambut g = R.gayaRambut.get(h);
			if (g != null && g.u().indexOf(letter) >= 0 && g.k().indexOf(fem ? 'P' : 'L') >= 0) {
				out.add(h);
			}
		}
		return out;
	}

	/** The cloth token resolver ({@code kain_of} in Python); keeps the draw order of the "kain" generator. */
	private record Kain(Resep R, int dao, boolean daoAda, int realm, int pangkat, int[] seragam, int klan, SplitMix64 rc) {

		int of(String tok, int slot, int fallback) {
			switch (tok) {
				case "-" -> {
					return fallback;
				}
				case "dao", "dao_kain", "dao_gelap" -> {
					int c;
					if (daoAda) {
						c = R.dao.get(dao).kain();
					} else if (realm == 0) {
						c = R.warna.getOrDefault("kasar", 0x8A7A60);
					} else if (realm > 0) {
						c = Palet.skala(R.ranah.get(Math.min(realm, R.ranah.size() - 1)), 60, 60, 60);
					} else {
						c = klan;
					}
					return tok.equals("dao_gelap") ? Palet.skala(c, 80, 80, 80) : c;
				}
				case "sekte", "sekte_pudar" -> {
					int c;
					if (seragam != null) {
						c = slot != 2 ? seragam[0] : seragam[1];
					} else if (daoAda) {
						c = slot != 2 ? R.dao.get(dao).kain() : R.dao.get(dao).sulam();
					} else {
						c = klan;
						if (slot == 2) {
							c = Palet.tambah(c, 60, 60, 60);
						}
					}
					return tok.equals("sekte_pudar") ? Palet.skala(c, 70, 70, 70) : c;
				}
				case "klan", "kelompok" -> {
					return klan;
				}
				case "cerah" -> {
					return R.cerah.get(rc.below(R.cerah.size()));
				}
				case "pangkat" -> {
					return R.tepiPangkat.get(Math.max(0, Math.min(5, pangkat)));
				}
				default -> {
					Integer c = R.bernama(tok);
					return c != null ? c : fallback;
				}
			}
		}
	}

	private static void full(List<SosokRupa.Lapisan> L, String sheet) {
		L.add(SosokRupa.Lapisan.biasa(sheet, 0, 0, 64, 64, 0, 0));
	}

	private static void face8(List<SosokRupa.Lapisan> L, String sheet) {
		L.add(SosokRupa.Lapisan.biasa(sheet, 0, 0, 8, 8, 8, 8));
	}

	private static String bagian1(String s) {
		int i = s.indexOf('_');
		if (i < 0) {
			return s;
		}
		int j = s.indexOf('_', i + 1);
		return j < 0 ? s.substring(i + 1) : s.substring(i + 1, j);
	}

	private static int indexOf(String[] a, String v) {
		for (int i = 0; i < a.length; i++) {
			if (a[i].equals(v)) {
				return i;
			}
		}
		return 0;
	}

	private static String orDefault(@Nullable String v, String d) {
		return v != null ? v : d;
	}

	private static <T> List<SplitMix64.Berbobot<T>> w(Object... pairs) {
		List<SplitMix64.Berbobot<T>> out = new ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2) {
			@SuppressWarnings("unchecked")
			T v = (T) pairs[i];
			out.add(new SplitMix64.Berbobot<>(v, (Integer) pairs[i + 1]));
		}
		return List.copyOf(out);
	}

	private RupaResolver() {
	}
}
