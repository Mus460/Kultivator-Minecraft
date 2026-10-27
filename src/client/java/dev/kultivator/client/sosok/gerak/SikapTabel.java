package dev.kultivator.client.sosok.gerak;

import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokJalan;

import java.util.HashMap;
import java.util.Map;

/**
 * The "Sikap · Jalan" and idle "Prop" columns of the look tables (DESIGN 40.4.1 A-D, 40.4.2, 40.4.3) and the trait,
 * sex and age rules of 40.5.6 / 40.5.11: which idle stance (108-121), which walk profile and which prop in the hand a
 * body shows when nothing else drives it. Keys are the look keys of 40.4 ({@code NpcRole.id()}, {@code d.*},
 * {@code liar.*}, {@code murid.<rank>}, {@code calon}, {@code saingan_murid}, {@code ketua_sekte}...); unknown keys
 * fall back by sex and age.
 */
final class SikapTabel {

	/** "santai": 113 for men, 114 for women. */
	private static final int SANTAI = -1;
	/** "santai / tangan_depan": 113 for men, 121 for women. */
	private static final int SANTAI_DEPAN = -2;
	/** "tangan_belakang (L) / tangan_depan (P)". */
	private static final int BELAKANG_DEPAN = -3;
	/** "gagang / tangan_depan" (inner disciples of NPC sects). */
	private static final int GAGANG_DEPAN = -4;

	private record Baris(int sikap, int varian, int prop, int peluangProp, ProfilJalan profil, boolean menurutKelamin) {
	}

	private static final Map<String, Baris> TABEL = new HashMap<>();

	private static void b(String kunci, int sikap, int prop, ProfilJalan profil) {
		TABEL.put(kunci, new Baris(sikap, 0, prop, 100, profil, false));
	}

	private static void b(String kunci, int sikap, int varian, int prop, int peluang, ProfilJalan profil, boolean kelamin) {
		TABEL.put(kunci, new Baris(sikap, varian, prop, peluang, profil, kelamin));
	}

	private static final int BELAKANG = GerakId.SIKAP_TANGAN_BELAKANG;
	private static final int LIPAT = GerakId.SIKAP_LIPAT_TANGAN;
	private static final int LENGAN = GerakId.SIKAP_TANGAN_LENGAN;
	private static final int TOMBAK = GerakId.SIKAP_TOMBAK;
	private static final int GAGANG = GerakId.SIKAP_GAGANG;
	private static final int TUA = GerakId.SIKAP_TUA;
	private static final int ANAK = GerakId.SIKAP_ANAK;
	private static final int KACAK = GerakId.SIKAP_BERKACAK;
	private static final int TUNDUK = GerakId.SIKAP_TUNDUK;
	private static final int ANGKUH = GerakId.SIKAP_ANGKUH;
	private static final int DOA = GerakId.SIKAP_DOA;
	private static final int DEPAN = GerakId.SIKAP_TANGAN_DEPAN;

	static {
		// A. the old town roles
		b("juru_lelang", BELAKANG, PropId.KIPAS_LIPAT, ProfilJalan.BANGSAWAN);
		b("juru_lelang_abadi", BELAKANG, PropId.KIPAS_LIPAT, ProfilJalan.BANGSAWAN);
		b("pemilik_teh", LENGAN, PropId.TEKO, ProfilJalan.SANTAI);
		b("makelar_gelap", LIPAT, PropId.KANTONG_UANG, ProfilJalan.IBLIS);
		b("pedagang_pil", LENGAN, PropId.LABU_PIL, ProfilJalan.PEDAGANG);
		b("pedagang_abadi", LENGAN, PropId.SEMPOA, ProfilJalan.PEDAGANG);
		b("pandai_pusaka", KACAK, PropId.PALU, ProfilJalan.GAGAH);
		b("penjaga", TOMBAK, PropId.TOMBAK, ProfilJalan.MILITER);
		b("warga", SANTAI, 0, 0, 100, ProfilJalan.SANTAI, false);
		b("pengembara", GAGANG, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("saingan", GAGANG, 0, ProfilJalan.GAGAH);
		b("abadi", LENGAN, GerakId.V_TANGAN_LENGAN_ABADI, 0, 100, ProfilJalan.BANGSAWAN, false);
		b("penceramah", BELAKANG, PropId.KEBUT, ProfilJalan.TUA);
		b("utusan", BELAKANG, PropId.PAPAN_TITAH, ProfilJalan.MILITER);
		b("penjaga_kebun", DEPAN, PropId.KERANJANG, ProfilJalan.ANGGUN);
		// B. capital, guilds, villages, clans, compounds, caves
		b("pangeran_kota", BELAKANG, 0, ProfilJalan.BANGSAWAN);
		b("menteri", LENGAN, PropId.PAPAN_CATATAN, ProfilJalan.BANGSAWAN);
		b("pengawal_istana", TOMBAK, PropId.TOMBAK, ProfilJalan.MILITER);
		b("kapten_penjaga", KACAK, 0, ProfilJalan.MILITER);
		b("penjaga_gerbang", TOMBAK, PropId.TOMBAK, ProfilJalan.MILITER);
		b("hakim", BELAKANG, PropId.PALU_KAYU, ProfilJalan.BANGSAWAN);
		b("bankir", LENGAN, PropId.SEMPOA, ProfilJalan.PEDAGANG);
		b("bandar_batu", KACAK, PropId.PAHAT, ProfilJalan.GAGAH);
		b("pemotong_batu", SANTAI, PropId.PAHAT, ProfilJalan.SANTAI);
		b("pedagang_binatang", KACAK, PropId.TALI_KEKANG, ProfilJalan.GAGAH);
		b("pedagang_senjata", LIPAT, 0, ProfilJalan.GAGAH);
		b("empu", KACAK, PropId.PALU, ProfilJalan.GAGAH);
		b("alkemis", LENGAN, PropId.LABU_PIL, ProfilJalan.SANTAI);
		b("ketua_gilda", BELAKANG, 0, ProfilJalan.BANGSAWAN);
		b("petugas_gilda", DEPAN, PropId.KUAS, ProfilJalan.PEDAGANG);
		b("penguji", LIPAT, 0, ProfilJalan.MILITER);
		b("penjaga_formasi", LENGAN, PropId.PIRINGAN_FORMASI, ProfilJalan.SANTAI);
		b("nakhoda", KACAK, PropId.TEROPONG, ProfilJalan.GAGAH);
		b("kuli", GerakId.SIKAP_SANTAI_L, 0, ProfilJalan.KULI);
		b("pemilik_penginapan", DEPAN, PropId.KUNCI_KAMAR, ProfilJalan.SANTAI);
		b("koki", KACAK, PropId.SENDOK_SAYUR, ProfilJalan.SANTAI);
		b("pelayan", DEPAN, PropId.NAMPAN, ProfilJalan.TERGESA);
		b("pustakawan", LENGAN, PropId.KITAB, ProfilJalan.SANTAI);
		b("penceramah_fana", BELAKANG, PropId.KIPAS_LIPAT, ProfilJalan.TUA);
		b("rahib", DOA, PropId.DUPA, ProfilJalan.RAHIB);
		b("tabib", DEPAN, PropId.KOTAK_OBAT, ProfilJalan.SANTAI);
		b("tukang", KACAK, PropId.PALU, ProfilJalan.SANTAI);
		b("penjaga_teka", DEPAN, PropId.LENTERA_TANGAN, ProfilJalan.SANTAI);
		b("juara_sekte", GAGANG, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("kepala_desa", TUA, GerakId.V_TUA_TONGKAT, PropId.TONGKAT, 100, ProfilJalan.TUA_TONGKAT, false);
		b("petani", SANTAI, PropId.CANGKUL, ProfilJalan.SANTAI);
		b("nelayan", SANTAI, PropId.JORAN, ProfilJalan.SANTAI);
		b("pandai_desa", KACAK, PropId.PALU, ProfilJalan.GAGAH);
		b("pedagang_keliling", DEPAN, 0, ProfilJalan.PEDAGANG);
		b("anak", ANAK, 0, ProfilJalan.ANAK_LOMPAT);
		b("tetua_desa", TUA, GerakId.V_TUA_TONGKAT, PropId.TONGKAT, 100, ProfilJalan.TUA_TONGKAT, false);
		b("patriark_klan", BELAKANG, 0, ProfilJalan.BANGSAWAN);
		b("tuan_muda", ANGKUH, PropId.KIPAS_LIPAT, ProfilJalan.BANGSAWAN);
		b("tetua_klan", BELAKANG, 0, PropId.TONGKAT, 30, ProfilJalan.TUA, false);
		b("murid_klan", SANTAI, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("murid_sekte_luar", SANTAI, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("murid_sekte_dalam", GAGANG_DEPAN, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("tetua_sekte", BELAKANG, 0, PropId.KEBUT, 40, ProfilJalan.BANGSAWAN, false);
		b("ketua_sekte", BELAKANG, 0, ProfilJalan.BANGSAWAN);
		b("pelayan_sekte", DEPAN, PropId.SAPU, ProfilJalan.TERGESA);
		b("pertapa", DOA, 0, ProfilJalan.TUA);
		b("sisa_jiwa", 0, 0, ProfilJalan.ARWAH);
		b("tetua_iblis", LIPAT, 0, ProfilJalan.IBLIS);
		b("tawanan", GerakId.BERLUTUT_RANTAI, PropId.RANTAI_TANGAN, ProfilJalan.TERGESA);
		b("pengemis", TUNDUK, PropId.MANGKUK, ProfilJalan.TUA_TONGKAT);
		b("pendeta_tao", LENGAN, PropId.KEBUT, ProfilJalan.RAHIB);
		// C. themed cities (38.T)
		b("syahbandar", BELAKANG, PropId.PAPAN_CATATAN, ProfilJalan.BANGSAWAN);
		b("petugas_bea", DEPAN, PropId.KUAS, ProfilJalan.PEDAGANG);
		b("pelaut", KACAK, PropId.TALI, ProfilJalan.GAGAH);
		b("nakhoda_laut", KACAK, PropId.TEROPONG, ProfilJalan.GAGAH);
		b("penjaga_suar", SANTAI, PropId.LENTERA_TANGAN, ProfilJalan.TUA);
		b("tukang_kapal", KACAK, PropId.PALU_DEMPUL, ProfilJalan.SANTAI);
		b("penjual_ikan", DEPAN, PropId.IKAN_TALI, ProfilJalan.PEDAGANG);
		b("pedagang_seberang", LENGAN, PropId.SEMPOA, ProfilJalan.PEDAGANG);
		b("pendeta_laut", DOA, PropId.KEBUT, ProfilJalan.RAHIB);
		b("tuan_benteng", BELAKANG, 0, ProfilJalan.BANGSAWAN);
		b("kafilah_bashi", KACAK, PropId.CAMBUK_KAFILAH, ProfilJalan.GAGAH);
		b("penuntun_unta", SANTAI, PropId.TALI, ProfilJalan.SANTAI);
		b("pedagang_bazar", DEPAN, PropId.KANTONG_UANG, ProfilJalan.PEDAGANG);
		b("pendeta_air", DOA, PropId.KENDI, ProfilJalan.RAHIB);
		b("pelukis_gua", SANTAI, PropId.KUAS_BESAR, ProfilJalan.SANTAI);
		b("penari_gurun", DEPAN, 0, ProfilJalan.PENARI);
		b("pengawal_gurun", TOMBAK, PropId.TOMBAK, ProfilJalan.MILITER);
		b("nyonya_sutra", ANGKUH, PropId.KIPAS_SUTRA, ProfilJalan.ANGGUN);
		b("penenun_sutra", DEPAN, 0, ProfilJalan.ANGGUN);
		b("pemelihara_ulat", SANTAI, PropId.KERANJANG, ProfilJalan.SANTAI);
		b("ahli_teh", LENGAN, PropId.TEKO, ProfilJalan.SANTAI);
		b("pemetik_teh", SANTAI, PropId.KERANJANG_TEH, ProfilJalan.SANTAI);
		b("pendayung", SANTAI, PropId.DAYUNG_YULOH, ProfilJalan.GAGAH);
		b("sastrawan", BELAKANG, PropId.KIPAS_LIPAT, ProfilJalan.SANTAI);
		b("kepala_biara", DOA, PropId.TONGKAT_CINCIN, ProfilJalan.RAHIB);
		b("biksu_gunung", DOA, PropId.TONGKAT_BAMBU, ProfilJalan.RAHIB);
		b("penjaga_lift", KACAK, 0, ProfilJalan.SANTAI);
		b("kuli_gunung", SANTAI, 0, ProfilJalan.KULI);
		b("pemetik_herba", SANTAI, PropId.SABIT_HERBA, ProfilJalan.SANTAI);
		b("peziarah", DOA, PropId.TAS_DUPA, ProfilJalan.RAHIB);
		b("penguasa_iblis", ANGKUH, 0, ProfilJalan.IBLIS);
		b("penegak_iblis", LIPAT, 0, ProfilJalan.MILITER);
		b("jagal_arena", KACAK, 0, ProfilJalan.GAGAH);
		b("gladiator_iblis", KACAK, 0, ProfilJalan.GAGAH);
		b("penjaga_kitab_terlarang", LENGAN, PropId.KITAB, ProfilJalan.TUA);
		b("pembunuh_bayaran", LIPAT, PropId.PISAU_GANDA, ProfilJalan.IBLIS);
		b("warga_iblis", SANTAI, 0, ProfilJalan.SANTAI);
		b("pemburu_lurus", GAGANG, 0, 0, 100, ProfilJalan.GAGAH, true);
		// D. dimension people, rogue cultivators, the heavenly envoy
		b("d.meng_po", DEPAN, 0, ProfilJalan.TUA_TONGKAT);
		b("d.hakim", BELAKANG, 0, ProfilJalan.BANGSAWAN);
		b("d.raja_yama", ANGKUH, 0, ProfilJalan.BANGSAWAN);
		b("d.arwah", 0, 0, ProfilJalan.ARWAH);
		b("d.penjaga_laut", TOMBAK, PropId.TOMBAK, ProfilJalan.MILITER);
		b("d.pertapa_laut", LENGAN, 0, ProfilJalan.TUA);
		b("d.dewa_penglai", LENGAN, 0, ProfilJalan.BANGSAWAN);
		b("d.nelayan", SANTAI, PropId.JORAN, ProfilJalan.SANTAI);
		b("d.pedagang", LENGAN, 0, ProfilJalan.PEDAGANG);
		b("liar", LIPAT, 0, ProfilJalan.IBLIS);
		b("utusan_langit", BELAKANG, 0, ProfilJalan.MILITER);
		// disciples by sect rank (40.4.2)
		b("murid.0", TUNDUK, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("murid.1", SANTAI, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("murid.2", SANTAI_DEPAN, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("murid.3", BELAKANG_DEPAN, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("murid.4", BELAKANG, 0, 0, 100, ProfilJalan.BANGSAWAN, false);
		b("murid.5", BELAKANG, 0, 0, 100, ProfilJalan.BANGSAWAN, false);
		b("calon", TUNDUK, 0, 0, 100, ProfilJalan.GAGAH, true);
		b("saingan_murid", LIPAT, 0, 0, 100, ProfilJalan.GAGAH, true);
	}

	/** Idle stance per life path bit (40.4.3), 0 = keep the rank's. */
	private static final int[] SIKAP_JALAN = new int[SosokJalan.MAKS + 1];
	/** Walk profile per life path, null = keep. */
	private static final ProfilJalan[] PROFIL_JALAN = new ProfilJalan[SosokJalan.MAKS + 1];
	/** Hand prop at rest per life path. */
	private static final int[] PROP_JALAN = new int[SosokJalan.MAKS + 1];
	/** Attached props per life path (40.4.3 "prop pakai"). */
	private static final Klip.PropPasang[][] PASANG_JALAN = new Klip.PropPasang[SosokJalan.MAKS + 1][];

	static {
		SIKAP_JALAN[SosokJalan.PEDANG] = GAGANG;
		SIKAP_JALAN[SosokJalan.ALKEMIS] = LENGAN;
		SIKAP_JALAN[SosokJalan.FORMASI] = LENGAN;
		SIKAP_JALAN[SosokJalan.PERTAPA] = DOA;
		SIKAP_JALAN[SosokJalan.PETUALANG] = KACAK;
		SIKAP_JALAN[SosokJalan.PEMBURU] = KACAK;
		SIKAP_JALAN[SosokJalan.PEDAGANG] = LENGAN;
		SIKAP_JALAN[SosokJalan.CENDEKIA] = BELAKANG;
		SIKAP_JALAN[SosokJalan.PENJAGA] = TOMBAK;
		SIKAP_JALAN[SosokJalan.IBLIS] = LIPAT;
		SIKAP_JALAN[SosokJalan.TEMPA] = KACAK;
		SIKAP_JALAN[SosokJalan.TABIB] = DEPAN;
		SIKAP_JALAN[SosokJalan.PETANI_ROH] = SANTAI;
		SIKAP_JALAN[SosokJalan.MUSISI] = LENGAN;
		SIKAP_JALAN[SosokJalan.BANGSAWAN] = ANGKUH;
		PROFIL_JALAN[SosokJalan.PENJAGA] = ProfilJalan.MILITER;
		PROFIL_JALAN[SosokJalan.IBLIS] = ProfilJalan.IBLIS;
		PROFIL_JALAN[SosokJalan.PEDAGANG] = ProfilJalan.PEDAGANG;
		PROFIL_JALAN[SosokJalan.PETUALANG] = ProfilJalan.SANTAI;
		PROFIL_JALAN[SosokJalan.BANGSAWAN] = ProfilJalan.BANGSAWAN;
		PROFIL_JALAN[SosokJalan.PERTAPA] = ProfilJalan.RAHIB;
		PROP_JALAN[SosokJalan.PERTAPA] = PropId.TASBIH_TANGAN;
		PROP_JALAN[SosokJalan.PENJAGA] = PropId.TOMBAK;
		PROP_JALAN[SosokJalan.TABIB] = PropId.KOTAK_OBAT;
		PROP_JALAN[SosokJalan.MUSISI] = PropId.SERULING;
		PASANG_JALAN[SosokJalan.ALKEMIS] = new Klip.PropPasang[] {
				new Klip.PropPasang(PropId.BOTOL_PIL, Klip.Titik.PINGGANG_KANAN, 0, 0, 0, 0, 0, 0, 0.8F)};
		PASANG_JALAN[SosokJalan.FORMASI] = new Klip.PropPasang[] {
				new Klip.PropPasang(PropId.BENDERA_FORMASI, Klip.Titik.PUNGGUNG, -2.5F, 0, 0, 0, 0, -12, 0.8F),
				new Klip.PropPasang(PropId.BENDERA_FORMASI, Klip.Titik.PUNGGUNG, 0, -1, 0.4F, 0, 0, 0, 0.8F),
				new Klip.PropPasang(PropId.BENDERA_FORMASI, Klip.Titik.PUNGGUNG, 2.5F, 0, 0, 0, 0, 12, 0.8F)};
		PASANG_JALAN[SosokJalan.PEMBURU] = new Klip.PropPasang[] {
				new Klip.PropPasang(PropId.TROFI_TARING, Klip.Titik.BAHU_KIRI, 0, 0, 0, 0, 0, 0, 1.0F)};
		PASANG_JALAN[SosokJalan.PEDAGANG] = new Klip.PropPasang[] {
				// tucked flat into the back of the belt
				new Klip.PropPasang(PropId.SEMPOA, Klip.Titik.PUNGGUNG, 0, 6.5F, 0.3F, 0, 0, 0, 0.6F)};
		PASANG_JALAN[SosokJalan.CENDEKIA] = new Klip.PropPasang[] {
				new Klip.PropPasang(PropId.GULUNGAN, Klip.Titik.PINGGANG_KIRI, 0, 0, 0, 0, 0, 0, 0.8F)};
	}

	/** Attached props of disciples of rank Inti (3) and up: the Qiankun pouch at the hip. */
	private static final Klip.PropPasang[] PASANG_INTI = {
			new Klip.PropPasang(PropId.KANTONG_QIANKUN, Klip.Titik.PINGGANG_KIRI, 0, 0, 0, 0, 0, 0, 0.8F)};

	/** The chosen stance, its variant, the idle hand prop, the walk profile and attached props. */
	static final class Hasil {
		int sikap;
		int varian;
		int prop;
		ProfilJalan profil = ProfilJalan.GAGAH;
		Klip.PropPasang[] pasang;
		/** Extra shoulder raise of the timid and the lazy idle lean (40.5.11 trait rules). */
		float bahu;
		float leanTambah;
		float hxTambah;
	}

	/**
	 * Chooses for a body. {@code kunci} the look key (may be null), {@code pangkat} the disciple's sect rank (-1 for
	 * others), {@code pemimpin} for group leaders, {@code murid} for disciples (traits may override their stance).
	 */
	static void pilih(String kunci, Sosok sosok, Gaya gaya, int pangkat, boolean pemimpin, boolean murid, int benih, Hasil h) {
		boolean p = sosok.perempuan();
		kunci = kunci == null ? null : dev.kultivator.sosok.RupaKunci.tanpaGaya(kunci);
		Baris baris = kunci == null ? null : TABEL.get(kunci);
		if (baris == null && kunci != null && kunci.startsWith("liar")) {
			baris = TABEL.get("liar");
		}
		if (baris == null && kunci != null && kunci.startsWith("murid.")) {
			baris = TABEL.get("murid.1");
		}
		int sikap;
		int varian = 0;
		int prop = 0;
		ProfilJalan profil;
		if (baris != null) {
			sikap = baris.sikap;
			varian = baris.varian;
			prop = baris.peluangProp >= 100 || Math.floorMod(benih >>> 5, 100) < baris.peluangProp ? baris.prop : 0;
			profil = baris.menurutKelamin && p ? ProfilJalan.ANGGUN : baris.profil;
		} else {
			sikap = SANTAI;
			profil = p ? ProfilJalan.ANGGUN : ProfilJalan.GAGAH;
		}
		if ("saingan".equals(kunci) && pemimpin) {
			sikap = LIPAT;
		}
		if ("anak".equals(kunci)) {
			int r = Math.floorMod(benih >>> 9, 10);
			prop = r == 0 ? PropId.LAYANG_LAYANG : r == 1 ? PropId.GASING : 0;
		}
		sikap = switch (sikap) {
			case SANTAI -> p ? GerakId.SIKAP_SANTAI_P : GerakId.SIKAP_SANTAI_L;
			case SANTAI_DEPAN -> p ? DEPAN : GerakId.SIKAP_SANTAI_L;
			case BELAKANG_DEPAN -> p ? DEPAN : BELAKANG;
			case GAGANG_DEPAN -> p ? DEPAN : GAGANG;
			default -> sikap;
		};
		Klip.PropPasang[] pasang = null;
		h.bahu = 0.0F;
		h.leanTambah = 0.0F;
		h.hxTambah = 0.0F;
		if (murid) {
			int jalan = gaya.jalan();
			if (jalan > 0 && jalan <= SosokJalan.MAKS) {
				if (SIKAP_JALAN[jalan] != 0) {
					sikap = SIKAP_JALAN[jalan] == SANTAI ? (p ? GerakId.SIKAP_SANTAI_P : GerakId.SIKAP_SANTAI_L) : SIKAP_JALAN[jalan];
				}
				if (PROFIL_JALAN[jalan] != null) {
					profil = PROFIL_JALAN[jalan];
				}
				if (PROP_JALAN[jalan] != 0) {
					prop = PROP_JALAN[jalan];
				}
				pasang = PASANG_JALAN[jalan];
			}
			if (pasang == null && pangkat >= 3) {
				pasang = PASANG_INTI;
			}
			// traits override the stance where the tables do not lock it (40.5.11)
			if (gaya.punya(Sifat.SOMBONG)) {
				sikap = ANGKUH;
			} else if (gaya.punya(Sifat.RENDAH_HATI) && pangkat < 4) {
				sikap = TUNDUK;
			} else if (gaya.punya(Sifat.BERANI) && jalan == 0) {
				sikap = KACAK;
			} else if (gaya.punya(Sifat.PENAKUT)) {
				sikap = DEPAN;
				h.bahu = 0.6F;
			} else if (gaya.punya(Sifat.MALAS)) {
				sikap = p ? GerakId.SIKAP_SANTAI_P : GerakId.SIKAP_SANTAI_L;
				h.leanTambah = 6.0F * net.minecraft.util.Mth.DEG_TO_RAD;
				h.hxTambah = 8.0F * net.minecraft.util.Mth.DEG_TO_RAD;
			} else if (gaya.punya(Sifat.TENANG) && jalan == 0) {
				sikap = LENGAN;
			} else if ((gaya.punya(Sifat.AMBISIUS) || gaya.punya(Sifat.LICIK)) && jalan == 0) {
				sikap = LIPAT;
			}
			if (pangkat == 5 && gaya.punya(Sifat.SOMBONG)) {
				sikap = ANGKUH;
			}
		}
		// age overrides (40.5.6): children stand like children, elders stoop
		if (sosok.anak() && !"tawanan".equals(kunci)) {
			sikap = ANAK;
			varian = 0;
			profil = ProfilJalan.ANAK_LOMPAT;
		} else if (sosok.tua()) {
			if (sikap == GerakId.SIKAP_SANTAI_L || sikap == GerakId.SIKAP_SANTAI_P || sikap == DEPAN) {
				sikap = TUA;
			}
			if (profil == ProfilJalan.GAGAH || profil == ProfilJalan.ANGGUN || profil == ProfilJalan.SANTAI) {
				profil = prop == PropId.TONGKAT ? ProfilJalan.TUA_TONGKAT : ProfilJalan.TUA;
			}
		}
		h.sikap = sikap;
		h.varian = varian;
		h.prop = prop;
		h.profil = profil;
		h.pasang = pasang;
	}

	private SikapTabel() {
	}
}
