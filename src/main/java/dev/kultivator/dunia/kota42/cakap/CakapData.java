package dev.kultivator.dunia.kota42.cakap;

/**
 * The frozen data formats of the conversation engine (DESIGN 42.5A.2-42.5A.9, plus golden files of 42.5A.10/12). Binding
 * for 42K (loader {@code CakapReload} + engine), 42K-T (grammar, dialects, tics, generator {@code tools/gen_kota_hidup_cakap.py},
 * checker {@code tools/cek_cakap.py}) and 42K-C (corpus). Frozen in 42-G0 by 42K0; a change needs every owner above.
 * One small valid example of each file sits under {@code data/kultivator/tempat/percakapan/}.
 *
 * <h2>Common rules</h2>
 * <ul>
 * <li>Folder {@code data/<ns>/tempat/percakapan/} ({@link #FOLDER}); loaded on every data (re)load like {@code CeritaReload}
 *     (Fabric {@code ResourceLoader}, parse on the preparation executor, swap on the server thread); a broken file is logged
 *     and skipped. Files of several namespaces merge; the same id later in path order replaces the earlier one.</li>
 * <li>UTF-8 JSON objects. Keys starting with {@code _} (e.g. {@code "_catatan"}) are comments: the loader ignores them,
 *     {@code cek_cakap.py} allows them.</li>
 * <li>Ids: lower_snake ASCII, except niat and predicate ids (UPPER_SNAKE). Order inside arrays is meaningful and kept
 *     (determinism, 42.5A.12): loaders use {@code LinkedHashMap}/lists, never iteration order of a {@code HashMap}.</li>
 * <li>No text in data except ids: every visible word is a lang key in a {@code kota_hidup_cakap*} fragment (Indonesian
 *     primary, identical keys in {@code .id_id} and {@code .en_us}). No network, no model, no generated free text.</li>
 * <li>Six traits are written {@code H E X A C O} (HEXACO, -100..100); PAD axes {@code p a d} (-1..1); {@code S = -E}.</li>
 * <li>A {@code syarat} object (shared by topics, rules, tics, chats) may hold any of: {@code peran[]} ({@code NpcRole} ids,
 *     lower case), {@code kelompok[]} ({@code NpcRole.Kelompok} ids), {@code tahap[]} ({@code anak remaja dewasa tua}),
 *     {@code kelamin} ({@code l}/{@code p}), {@code watak[]} ({@code Tokoh.WATAK}), {@code kebiasaan[]} (42.4.12 ids),
 *     {@code sifat} ({@code {"E": ">60", "A": "<-40"}}: comparison on a trait), {@code jam[]} ({@code subuh pagi siang sore
 *     malam larut}), {@code cuaca[]} ({@code cerah hujan badai salju kabut_qi gerhana meteor}), {@code musim[]} ({@code semi
 *     panas gugur dingin}), {@code acara[]} ({@code kota_suasana} event ids, 42.13.2), {@code wajah[]} (face codes
 *     {@code senang netral sedih takut marah mabuk sakit}), {@code label_emosi[]} (octant labels of {@code emosi.json} plus
 *     {@code netral}), {@code kesan[]} ({@code musuh asing kenal akrab sahabat}), {@code tahu_nama} (bool),
 *     {@code kenangan[]} (42.4.10 ids, upper case), {@code hubungan[]} ({@code JenisHubungan} ids, lower case),
 *     {@code tempat[]} (place kinds / titik ids), {@code dialek[]}, {@code register[]}, {@code fakta} (one predicate id that
 *     must be the rule's fact), {@code ranah_selisih} ({@code ">=3"}: player realm minus speaker realm). All present keys must
 *     hold (AND); values inside one array are OR.</li>
 * </ul>
 *
 * <h2>{@code kepribadian.json} (42.5A.2)</h2>
 * <pre>
 * {"simpangan": 20, "warisan": 25,
 *  "watak":     {"ramah": {"H": 20, "E": 10, "X": 40, "A": 60, "C": 0, "O": 10}, ...},   // all 8 Tokoh.WATAK
 *  "kebiasaan": {"pemabuk": {"C": -20, "X": 15}, ...}}                                  // shifts, missing trait = 0
 * </pre>
 * Generation: watak mean + habit shifts + seeded normal(0, simpangan), clamped; children: parents' mean +- warisan.
 *
 * <h2>{@code emosi.json} (42.5A.3)</h2>
 * <pre>
 * {"temperamen": {"p": {"X": 0.21, "A": 0.59, "S": 0.19}, "a": {"O": 0.15, "A": 0.30, "S": -0.57},
 *                 "d": {"O": 0.25, "C": 0.17, "X": 0.60, "A": -0.32}, "bagi": 100, "jepit": 0.6, "suasana_bagi": 250},
 *  "paruh": {"jam": 2.0, "C": 200, "E": -300},          // t_half = jam * (1 + C/200 - E/300) world hours
 *  "simpan_hari": 2,                                    // keep PAD state only for warga pushed in the last 2 days
 *  "peristiwa": [{"id": "dipuji", "p": 0.30, "a": 0.10, "d": 0.10, "d_bila_rendah": null, "berarah": true,
 *                 "efek": {"C": -30}, "fakta": "INGKAR"}, ...],
 *  "label":     [{"id": "gembira", "p": 1, "a": 1, "d": 1, "wajah": "senang", "gestur": "tertawa"}, ...],   // 8 octants
 *  "intensitas":[{"min": 0.0, "kunci": "agak"}, {"min": 0.35, "kunci": "sangat"}, {"min": 0.7, "kunci": "bukan_main"}]}
 * </pre>
 * {@code peristiwa}: optional {@code d_bila_rendah} = d used when the current D &lt; 0 ("dihina"); {@code berarah} = emotion
 * points at the cause; {@code efek} = temporary trait shift while the push lasts; {@code fakta} = predicate recorded about the
 * cause. Paired outcomes are separate ids ({@code hadiah_disukai}/{@code hadiah_dibenci}). Entries override
 * {@link Penilaian#daftar} registrations with the same id. {@code intensitas[].kunci} -> {@code kultivator.cakap.lex.<kunci>}.
 *
 * <h2>{@code predikat.json} (42.5A.4)</h2>
 * <pre>
 * {"predikat": [{"id": "HARGA_NAIK", "golongan": "kota", "subjek": "tempat", "objek": "barang", "nilai": "persen",
 *                "valensi": -1, "kuat": 1.0, "topik": "harga", "keluarga": false, "rahasia": false}, ...]}
 * </pre>
 * {@code golongan}: {@code pemain warga kota dunia opini}; {@code subjek}/{@code objek}: a {@link Fakta.Jenis} id or
 * {@code null}; {@code nilai}: {@code tiada angka persen hari opini}; {@code valensi}: default -3..3; {@code kuat}: extra S
 * multiplier; {@code topik}: topic it feeds; {@code keluarga}: never forgotten when it involves family; {@code rahasia}: needs
 * relLawan &gt;= 0.7 to be told. 44 predicates (42.5A.4 list + {@code COPET_TERTANGKAP}).
 *
 * <h2>{@code topik.json} (42.5A.5)</h2>
 * <pre>
 * {"bobot": {"sal": 0.35, "relLawan": 0.20, "cocokEmosi": 0.15, "konteks": 0.10, "minat": 0.10, "kebutuhan": 0.10,
 *            "sudahDibahas": -0.30, "lanjut": 0.25, "tau_dasar": 0.25, "tau_O": 400},
 *  "topik": [{"id": "harga", "golongan": ["sosial"], "valensi": -1, "syarat": {...}, "minat": ["PEDAGANG"],
 *             "lanjut": ["kafilah", "begal"], "tabu": ["duka_di_pesta"], "niat": ["BERI_KABAR", "TANYA"]}, ...]}
 * </pre>
 * {@code golongan} (trait multipliers of 42.5A.5): {@code sosial} (X +0.1), {@code konflik} (A -0.2), {@code khawatir}
 * (E +0.2), {@code gosip} (H -0.3), {@code tanya} (O +0.2). {@code minat}: kelompok/peran/kebiasaan ids that score
 * {@code minat} = 1. {@code tabu} ids: {@code duka_di_pesta gosip_saat_kerja rahasia_ke_asing jelek_pejabat_takut}.
 * {@code niat}: intents allowed on this topic (player options and NPC acts).
 *
 * <h2>{@code niat.json} (42.5A.1, 42.5A.7)</h2>
 * <pre>
 * {"niat": [{"id": "BERI_KABAR", "pelaku": "npc", "topik": true, "tahap": ["TOPIK", "LANJUTAN"], "jawab": ["REAKSI"],
 *            "seru": null, "gestur": "bicara_tangan"}, ...]}
 * </pre>
 * {@code pelaku}: {@code npc pemain keduanya}; {@code topik}: needs a topic; {@code tahap}: {@link SesiCakap.Tahap} names where
 * it may occur; {@code jawab}: adjacency pairs (intents expected next); {@code seru}/{@code gestur}: defaults when the rule
 * names none. Player options show {@code kultivator.cakap.niat.<id lower>}. 62 niat in total ({@link Niat} constants are a
 * subset).
 *
 * <h2>{@code nada.json} (42.5A.7)</h2>
 * <pre>
 * {"ulang": 0.7,                                         // factor per repeat of the same tone to the same warga per day
 *  "nada": [{"id": "akrab", "syarat": {"kesan_min": 20}, "b0": -0.5,
 *            "w": {"kesan": 0.04, "X": 0.01, "A": 0.01, "sebaya": 0.5},
 *            "berhasil": {"kesan": 4, "rahasia": true, "harga": -0.03},
 *            "gagal": {"kesan": -3, "isyarat": "sok_akrab"}}, ...]}            // all 6 Nada
 * </pre>
 * Chance {@code p = 1 / (1 + e^-(b0 + sum w_i x_i))}. Factor names ({@code x_i}): the six traits of the listener, {@code P A D}
 * (current, x100), {@code kesan}, {@code nama_baik}, {@code sebaya} (0/1), {@code ranah} (player minus listener realm),
 * {@code infami}, {@code saksi} (bodies in view), {@code serakah}/{@code miskin} (0/1). {@code syarat} keys: {@code kesan_min},
 * {@code dewasa}, {@code lajang_atau_H_maks}, {@code nb_min}. Effect keys: {@code kesan}, {@code kesabaran}, {@code harga}
 * (personal price factor delta), {@code rahasia}, {@code info}, {@code emosi} (event id of {@code emosi.json}), {@code fakta}
 * (predicate recorded about the player), {@code gosip} (bool), {@code hukum} (crime points in the capital), {@code infami},
 * {@code isyarat} (mood hint id -> {@code kultivator.cakap.isyarat.<id>}).
 *
 * <h2>{@code tata/<kelompok>.json} (42.5A.8; written by {@code gen_kota_hidup_cakap.py})</h2>
 * <pre>
 * {"kelompok": "kabar",
 *  "isi": [{"aturan": "kabar.harga_naik", "niat": "BERI_KABAR", "topik": "harga",
 *           "syarat": {"fakta": "HARGA_NAIK", "label_emosi": ["jemu", "sedih", "cemas", "netral"]},
 *           "bingkai": [{"kunci": "harga_naik.0", "slot": ["$barang", "$waktu_lalu", "$keluh"], "bobot": 3},
 *                       {"kunci": "harga_naik.1", "slot": ["$barang", "$sebab"], "bobot": 2, "register": ["biasa", "kasar"]}],
 *           "slot": {"$barang": {"tipe": "barang", "dari": "fakta.objek", "fitur": ["jamak"]},
 *                    "$sebab": {"aturan": "sebab.harga", "opsional": 0.5}},
 *           "lanjut": ["kafilah", "begal"], "gestur": "geleng", "seru": "aduh"}, ...]}
 * </pre>
 * A rule without {@code niat} is a fragment, reachable only through a slot {@code "aturan"}. A frame's lang key is
 * {@code kultivator.cakap.<kunci>}; its string has exactly {@code slot.length} positional arguments {@code %1$s..%n$s}.
 * Feature lookup {@code <kunci>.<f1>.<f2>} -> {@code <kunci>.<f1>} -> {@code <kunci>}; features: {@code l/p} (gender),
 * {@code s/j} (number), register {@code sopan biasa kasar kuno}, a dialect id. A slot is either {@code {"tipe": ...}} (typed
 * slot: {@code sebutan_pendengar nama_warga ganti barang tempat arah jarak angka harga waktu_lalu waktu_depan cuaca musim
 * kata_emosi seru tik pekerjaan hubungan}; {@code dari}: {@code fakta.subjek fakta.objek fakta.nilai fakta.hariKejadian
 * pendengar pembicara lawan konteks.<name>}; {@code fitur[]}) or {@code {"aturan": "<rule id>"}}; {@code opsional} = chance
 * the slot is filled (otherwise it realises as empty). Expansion depth &lt;= 4. {@code seru} = short voice suffix: the key
 * {@code kota42.seru.<seru>_l|_p} (by speaker gender) or {@code kota42.seru.<seru>} when ungendered.
 *
 * <h2>{@code dialek.json} (42.5A.8)</h2>
 * <pre>
 * {"bawaan": "kampung",
 *  "tempat": {"kota_besar": "ibukota", "tema_pelabuhan": "pelabuhan", "tema_oasis": "oasis", "tema_air": "air",
 *             "tema_gunung": "gunung", "tema_iblis": "iblis", "kampung": "kampung"},
 *  "dialek": [{"id": "pelabuhan", "sebutan": "bung", "register_condong": "biasa", "kata": ["bung", "ombak", ...]}, ...]}
 * </pre>
 * {@code tempat}: place kind -> dialect. Each listed word {@code w} has the lexicon key {@code kultivator.cakap.lex.<w>.<dialek>}
 * (fragment {@code kota_hidup_cakap_lex}); a grammar terminal {@code kultivator.cakap.lex.<w>} is replaced by the dialect
 * entry when present. 7 dialects x 60 words.
 *
 * <h2>{@code tik.json} (42.5A.8)</h2>
 * <pre>
 * {"peluang": 0.25, "pengali_A": 1.5, "maks_per_warga": 2,
 *  "tik": [{"id": "hik", "posisi": "awal", "bobot": 3, "syarat": {"kebiasaan": ["pemabuk"], "jam": ["malam", "larut"]}},
 *          {"id": "gagap", "posisi": "gagap", "bobot": 1, "syarat": {"sifat": {"E": ">60"}}}, ...]}
 * </pre>
 * {@code posisi}: {@code awal akhir sisip gagap} ({@code gagap} repeats the first syllable, no lang key). Text key
 * {@code kultivator.cakap.tik.<id>}. Never twice in a row. 36 tics.
 *
 * <h2>{@code obrolan/<jenis>.json} (42.5A.9; one file per {@link Cakap.JenisObrolan})</h2>
 * <pre>
 * {"jenis": "gosip", "syarat": {"hubungan_min": "kenal", "fakta_menonjol": true},
 *  "mulai": {"dasar": 0.3, "X": 300, "hubungan": 400}, "jeda_tick": [40, 80],
 *  "langkah": [{"id": "sapa",   "oleh": "A", "niat": "SAPA",            "lanjut": ["umpan"]},
 *              {"id": "umpan",  "oleh": "A", "niat": "UMPAN_GOSIP",     "lanjut": ["kabar"]},
 *              {"id": "kabar",  "oleh": "A", "niat": "BERI_KABAR", "fakta": "menonjol", "bumbu": true,
 *               "lanjut": ["kaget", "ragu", "koreksi"]},
 *              {"id": "koreksi","oleh": "B", "niat": "KOREKSI", "syarat": {"saksi": true}, "lanjut": ["komentar"]}, ...],
 *  "efek": {"fakta_pindah": true, "kesan_subjek": 3}}
 * </pre>
 * {@code oleh}: {@code A} (starter) / {@code B}; {@code lanjut}: next step ids, chosen by their own {@code syarat} (extra keys:
 * {@code saksi}, {@code A_maks}, {@code D_min}, {@code P_maks}, {@code ronde_maks}) then seeded weights; a step without
 * {@code lanjut} ends the chat. Effect keys: {@code fakta_pindah}, {@code kesan_subjek}, {@code hubungan} ([min, max]),
 * {@code emosi} (event id), {@code perkelahian} (bool), {@code permintaan} (bool), {@code ahli} (int),
 * {@code catatan} (overheard fact becomes a player note). Text per step comes from grammar rules with the step's niat.
 *
 * <h2>{@code golden/<id>.json} (42.5A.10/12; written by {@code uji kota_hidup cakap golden tulis})</h2>
 * <pre>
 * {"id": "contoh_bibi_wang", "benih_dunia": 42, "hari": 120,
 *  "warga": {"id": 7, "watak": "penakut", "kebiasaan": ["gosip_sumur"], "sifat": {...}, "pad": [-0.1, 0.2, -0.3],
 *            "dialek": "ibukota", "fakta": [ {Fakta golden form} ], "janji": [...]},
 *  "pemain": {"nama": "Han", "kesan": 34, "ranah": 2},
 *  "langkah": [{"pilih": {"niat": "MINTA", "nada": "sopan"},
 *               "harap": [{"k": "kultivator.cakap...", "a": [...]}], "id_id": "...", "en_us": "..."}, ...]}
 * </pre>
 * The test rebuilds the state, runs the engine with the fixed seed and compares keys/arguments plus both realisations.
 */
public final class CakapData {

	/** Data folder below {@code data/<ns>/}. */
	public static final String FOLDER = "tempat/percakapan";
	/** Grammar folder ({@code tata/<kelompok>.json}). */
	public static final String TATA = FOLDER + "/tata";
	/** NPC-NPC chat scripts ({@code obrolan/<jenis>.json}). */
	public static final String OBROLAN = FOLDER + "/obrolan";
	/** Golden files. */
	public static final String GOLDEN = FOLDER + "/golden";
	/** Single files below {@link #FOLDER}, without {@code .json}. */
	public static final String KEPRIBADIAN = "kepribadian", EMOSI = "emosi", PREDIKAT = "predikat", TOPIK = "topik",
			NIAT = "niat", NADA = "nada", DIALEK = "dialek", TIK = "tik";

	/** Prefix of every grammar terminal (fragment {@code kota_hidup_cakap}). */
	public static final String KUNCI = "kultivator.cakap.";
	/** Prefix of lexicon words and dialect replacements (fragment {@code kota_hidup_cakap_lex}). */
	public static final String KUNCI_LEX = KUNCI + "lex.";
	/** Prefix of tic texts. */
	public static final String KUNCI_TIK = KUNCI + "tik.";
	/** Prefix of short voice keys (42.5A.8 table; sound events added by F-END). */
	public static final String SERU = "kota42.seru.";

	private CakapData() {
	}
}
