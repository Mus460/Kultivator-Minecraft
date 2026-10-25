package dev.kultivator.sekte.gabung.dunia;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Gabung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The titik (named anchors) of DESIGN 41 (41.11.2, contract C41.W1, owner 41W) and the one ladder every 41 module uses to
 * find a place of a sect: the compound titik ({@code KompleksApi.spot}, once 41W-G added the name to
 * {@code KompleksAnchors.TITIK}) -&gt; a nearer compound titik that stands in for it (an altar in the main hall, a pillar
 * on the training ground) -&gt; the sect's mountain gate -&gt; its planned gate column. The town fallback (Kantor Sekte,
 * 41.4.6 "tanpa tempat") is {@code GabungApi.titik}'s last step.
 * <p>
 * Names: compound titik are the plain names below ({@code kompleks/<name>} anchors); gate titik carry the prefix
 * {@link #GERBANG} ({@code sektenpc/<name>}, placed by {@code tools/gen_sekte_gerbang.py}).
 * <p>
 * Gate titik: the mountain gate's anchors belong to module {@code sektenpc} (36 D's handler, one per module), so 41 never
 * registers a second handler; {@link #pindaiGerbang} (every 10 s from {@code DuniaGabung}) reads the loaded
 * {@code sektenpc/*} anchors, gives each to the sect whose planned gate is nearest (within {@link #MILIK_GERBANG}) and
 * remembers its position in {@code kultivator:gabung_tokoh}, so the answer stays when the gate is unloaded.
 */
public final class TitikGabung {

	// ---------------------------------------------------------------------------------------------------------------
	// Compound titik of 41.11.2 (lot in brackets)

	/** Paviliun Jasa keeper [paviliun_jasa]. */
	public static final String JASA = "jasa";
	/** Exchange table of the pavilion [paviliun_jasa]. */
	public static final String JASA_MEJA = "jasa_meja";
	/** Sect mission board on the outer wall ({@code PapanHidupEntity}) [paviliun_jasa]. */
	public static final String PAPAN_MISI = "papan_misi";
	/** Order counter of the pavilion [paviliun_jasa]. */
	public static final String PESANAN = "pesanan";
	/** 3 x 3 oath dais before the throne [aula_utama]; Iblis: the {@code altar_iblis}. */
	public static final String ALTAR_SUMPAH = "altar_sumpah";
	/** Side table of the Discipline Elder [aula_utama]. */
	public static final String HUKUM = "hukum";
	/** Corak test spot [lapangan_latihan]. */
	public static final String UJIAN_CORAK = "ujian_corak";
	/** Prefix of the five Qi pillars {@code pilar_0..4} in a ring of 9 blocks [lapangan_latihan]. */
	public static final String PILAR = "pilar_";
	public static final int PILAR_JUMLAH = 5;
	/** Beast pen in a corner [lapangan_latihan]. */
	public static final String KANDANG = "kandang";
	/** Bronze Men corridor along the long side [lapangan_latihan]. */
	public static final String LORONG = "lorong";
	/** Well and water barrel of the "Pikul Air" duty [kebun_obat]. */
	public static final String KERJA_SUMUR = "kerja:sumur";
	public static final String KERJA_TONG = "kerja:tong";
	/** Prefix of the six small caves {@code kediaman_0..5} [gua_meditasi]. */
	public static final String KEDIAMAN = "kediaman_";
	public static final int KEDIAMAN_JUMLAH = 6;
	/** The cave kept as Gua Renungan (confinement) [gua_meditasi]. */
	public static final String GUA_RENUNGAN = "gua_renungan";
	/** Prefix of the four vein caves {@code kediaman_dalam_0..3} [asrama_dalam]. */
	public static final String KEDIAMAN_DALAM = "kediaman_dalam_";
	public static final int KEDIAMAN_DALAM_JUMLAH = 4;
	/** Prefix of the two private peak pavilions {@code kediaman_puncak_0..1} [aula_puncak]. */
	public static final String KEDIAMAN_PUNCAK = "kediaman_puncak_";
	public static final int KEDIAMAN_PUNCAK_JUMLAH = 2;
	/** First and last step of the Tangga Ujian [tangga]. */
	public static final String UJIAN_BAWAH = "ujian_bawah";
	public static final String UJIAN_ATAS = "ujian_atas";
	/** Blood pool test spot of an Iblis compound [kolam_darah]. */
	public static final String UJIAN_DARAH = "ujian_darah";

	// Existing compound titik (38.C.3.3) used by 41
	public static final String GERBANG_KOMPLEKS = "gerbang";
	public static final String AULA = "aula";
	public static final String ARENA = "arena";
	public static final String PASAR = "pasar";
	public static final String DALAM = "dalam";
	public static final String PUNCAK = "puncak";
	public static final String TETUA = "tetua";
	public static final String DERMAGA_AWAN = "dermaga_awan";

	// ---------------------------------------------------------------------------------------------------------------
	// Gate titik (sektenpc/*) of 41.11.2

	/** Prefix of gate titik names. */
	public static final String GERBANG = "sektenpc/";
	public static final String GERBANG_UJIAN_BAWAH = GERBANG + "ujian_bawah";
	public static final String GERBANG_UJIAN_ATAS = GERBANG + "ujian_atas";
	/** Registration desk in the gate hall. */
	public static final String GERBANG_LOKET = GERBANG + "loket";
	/** Prefix of the two tower rooms {@code sektenpc/bilik_0..1} (spare dwellings). */
	public static final String GERBANG_BILIK = GERBANG + "bilik_";
	/** Oath altar of a sect without a compound. */
	public static final String GERBANG_ALTAR = GERBANG + "altar";
	/** Existing gate titik (36 D). */
	public static final String GERBANG_KETUA = GERBANG + "ketua";
	public static final String GERBANG_MURID = GERBANG + "murid";

	/** Every new compound titik name of 41 (prefixes expanded), the list 41W-G adds to {@code KompleksAnchors.TITIK}. */
	public static final List<String> TITIK_BARU;
	/** Every gate titik of 41 (bilik prefix expanded) that {@link #pindaiGerbang} remembers. */
	public static final List<String> TITIK_GERBANG = List.of(GERBANG_UJIAN_BAWAH, GERBANG_UJIAN_ATAS, GERBANG_LOKET, GERBANG_BILIK + 0,
			GERBANG_BILIK + 1, GERBANG_ALTAR, GERBANG_KETUA, GERBANG_MURID);
	/** How far from a planned gate column a {@code sektenpc/*} anchor may lie and still belong to that sect (36 D: 256). */
	public static final double MILIK_GERBANG = 256.0D;

	static {
		List<String> all = new ArrayList<>(List.of(JASA, JASA_MEJA, PAPAN_MISI, PESANAN, ALTAR_SUMPAH, HUKUM, UJIAN_CORAK,
				KANDANG, LORONG, KERJA_SUMUR, KERJA_TONG, GUA_RENUNGAN, UJIAN_BAWAH, UJIAN_ATAS, UJIAN_DARAH));
		for (int i = 0; i < PILAR_JUMLAH; i++) {
			all.add(PILAR + i);
		}
		for (int i = 0; i < KEDIAMAN_JUMLAH; i++) {
			all.add(KEDIAMAN + i);
		}
		for (int i = 0; i < KEDIAMAN_DALAM_JUMLAH; i++) {
			all.add(KEDIAMAN_DALAM + i);
		}
		for (int i = 0; i < KEDIAMAN_PUNCAK_JUMLAH; i++) {
			all.add(KEDIAMAN_PUNCAK + i);
		}
		TITIK_BARU = Collections.unmodifiableList(all);
	}

	/**
	 * The compound titik that stands in for {@code nama} when the compound lacks it (an older lot, a small band without the
	 * district): pavilion, board, altar and discipline desk fall back to the main hall, test spots to the training ground,
	 * vein caves to the inner district, peak pavilions to the peak. Null when there is no stand-in.
	 */
	public static @Nullable String pengganti(String nama) {
		if (nama == null) {
			return null;
		}
		if (nama.startsWith(KEDIAMAN_DALAM)) {
			return DALAM;
		}
		if (nama.startsWith(KEDIAMAN_PUNCAK)) {
			return PUNCAK;
		}
		if (nama.startsWith(PILAR)) {
			return ARENA;
		}
		if (nama.startsWith(KEDIAMAN)) {
			return DALAM;
		}
		return switch (nama) {
			case JASA, JASA_MEJA, PAPAN_MISI, PESANAN, ALTAR_SUMPAH, HUKUM -> AULA;
			case UJIAN_CORAK, KANDANG, LORONG, UJIAN_DARAH -> ARENA;
			case UJIAN_BAWAH, KERJA_SUMUR, KERJA_TONG -> GERBANG_KOMPLEKS;
			case UJIAN_ATAS, GUA_RENUNGAN, TETUA -> AULA;
			default -> null;
		};
	}

	/**
	 * Where titik {@code nama} of {@code sekte} is: the compound anchor, its stand-in, the remembered gate titik that stands
	 * in for it ({@link #penggantiGerbang}), the mountain gate, the planned gate column (y 0). Gate titik ({@link #GERBANG}
	 * prefix) try their remembered anchor first. Null for an unknown sect or before the server runs.
	 */
	public static @Nullable BlockPos titik(String sekte, String nama) {
		if (sekte == null || nama == null) {
			return null;
		}
		BlockPos spot = kompleks(sekte, nama);
		if (spot != null) {
			return spot;
		}
		MinecraftServer server = Gabung.server();
		if (server != null) {
			String gateName = nama.startsWith(GERBANG) ? nama : penggantiGerbang(nama);
			BlockPos remembered = gateName == null ? null : TokohSekte.titikGerbang(server, sekte, gateName);
			if (remembered != null) {
				return remembered;
			}
		}
		BlockPos gate = SekteApi.diplomasi().gate(sekte);
		return gate != null ? gate : SekteApi.diplomasi().plannedGate(sekte);
	}

	/**
	 * The gate titik that serves compound titik {@code nama} when the sect has no compound (41.4.6 "tanpa tempat"): tests
	 * on the gate stair, the desk for pavilion, board and admissions, the altar for the oath, the tower rooms for dwellings,
	 * the master's throne for the summit. Null when the plain gate is the best answer.
	 */
	public static @Nullable String penggantiGerbang(String nama) {
		if (nama == null) {
			return null;
		}
		if (nama.startsWith(KEDIAMAN_DALAM) || nama.startsWith(KEDIAMAN_PUNCAK)) {
			return GERBANG_BILIK + 1;
		}
		if (nama.startsWith(KEDIAMAN)) {
			return GERBANG_BILIK + 0;
		}
		if (nama.startsWith(PILAR)) {
			return GERBANG_UJIAN_ATAS;
		}
		return switch (nama) {
			case UJIAN_BAWAH, KERJA_SUMUR, KERJA_TONG -> GERBANG_UJIAN_BAWAH;
			case UJIAN_ATAS, UJIAN_CORAK, LORONG, KANDANG, UJIAN_DARAH, ARENA -> GERBANG_UJIAN_ATAS;
			case JASA, JASA_MEJA, PAPAN_MISI, PESANAN, HUKUM, AULA, TETUA -> GERBANG_LOKET;
			case ALTAR_SUMPAH, GUA_RENUNGAN -> GERBANG_ALTAR;
			case PUNCAK, DALAM -> GERBANG_KETUA;
			default -> null;
		};
	}

	/**
	 * Reads the loaded {@code sektenpc/*} anchors once and remembers every 41 gate titik under the sect whose planned gate is
	 * nearest (within {@link #MILIK_GERBANG}). Returns how many positions were new or moved.
	 */
	public static int pindaiGerbang(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		DiplomasiService d = SekteApi.diplomasi();
		List<String> sects = d.sects();
		if (sects.isEmpty()) {
			return 0;
		}
		int changed = 0;
		for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(GERBANG)) {
			String kind = anchor.kind();
			if (!TITIK_GERBANG.contains(kind)) {
				continue;
			}
			String best = null;
			double bestD = MILIK_GERBANG * MILIK_GERBANG;
			for (String sekte : sects) {
				BlockPos col = d.plannedGate(sekte);
				if (col == null) {
					continue;
				}
				double dx = anchor.getX() - col.getX();
				double dz = anchor.getZ() - col.getZ();
				double dist = dx * dx + dz * dz;
				if (dist < bestD) {
					bestD = dist;
					best = sekte;
				}
			}
			if (best != null && TokohSekte.simpanTitikGerbang(server, best, kind, anchor.blockPosition())) {
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Every titik of 41 for {@code sekte} with its answer now, for {@code uji gabung titik}: name -&gt; position (null =
	 * not known yet), the compound ones first (new and old), then the gate ones.
	 */
	public static Map<String, BlockPos> semua(String sekte) {
		Map<String, BlockPos> out = new LinkedHashMap<>();
		MinecraftServer server = Gabung.server();
		for (String nama : List.of(GERBANG_KOMPLEKS, AULA, ARENA, PASAR, DALAM, PUNCAK, TETUA, DERMAGA_AWAN)) {
			out.put(nama, KompleksApi.spot(sekte, nama));
		}
		for (String nama : TITIK_BARU) {
			out.put(nama, KompleksApi.spot(sekte, nama));
		}
		for (String nama : TITIK_GERBANG) {
			out.put(nama, server == null ? null : TokohSekte.titikGerbang(server, sekte, nama));
		}
		return out;
	}

	/**
	 * Only the compound steps of {@link #titik}: the anchor or its stand-in, null otherwise (and for gate titik). This is
	 * the form to hand to {@code GabungApi.addPenyediaTitik}, so {@code GabungApi.titik} keeps its own gate and town steps.
	 */
	public static @Nullable BlockPos kompleks(String sekte, String nama) {
		if (sekte == null || nama == null || nama.startsWith(GERBANG)) {
			return null;
		}
		BlockPos spot = KompleksApi.spot(sekte, nama);
		if (spot != null) {
			return spot;
		}
		String stand = pengganti(nama);
		return stand == null ? null : KompleksApi.spot(sekte, stand);
	}

	private TitikGabung() {
	}
}
