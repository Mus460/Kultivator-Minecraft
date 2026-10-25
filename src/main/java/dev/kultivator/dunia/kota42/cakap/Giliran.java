package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.KodeWajah;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.sosok.GerakId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Turn taking (DESIGN 42.5A.6) and the seam between meaning and words (42.5A.0 butir 2). The engine decides <i>what</i>
 * is said (intent, topic, fact, register, emotion: a {@link Permintaan}); a {@link Perangkai} turns that into an
 * {@link Ujaran}. The grammar of 42K-T ({@code Realisasi}, typed rules of {@code tata/*.json}) registers itself with
 * {@link #daftar}; while none is registered or no rule matches, a small built-in realiser answers from the fallback keys
 * {@code kultivator.kota42.cakap.cadangan.<niat>.<n>} of fragment {@code kota_hidup_cakap_mesin}, so a conversation is
 * never mute and never free text.
 * <p>
 * Also here: how many utterances an NPC says in a turn (1-3 by X), the back-channel of a listener, and the gesture /
 * face / short-voice defaults of an utterance.
 */
public final class Giliran {

	/**
	 * Everything a realiser needs for one utterance.
	 *
	 * @param niat      the speech act
	 * @param topik     topic id, null for topic-less acts
	 * @param situasi   the speaker's snapshot (register, dialect, emotion label, sex, role, place...)
	 * @param fakta     fact in focus, null for none
	 * @param isian     extra slot values by slot name without {@code $} (insertion order kept)
	 * @param pendengar listening player, null for NPC-NPC talk
	 * @param lawan     the other townsperson in NPC-NPC talk, null otherwise
	 * @param acak      the seeded generator of this turn (42.5A.0)
	 */
	public record Permintaan(Niat niat, @Nullable String topik, Topik.Situasi situasi, @Nullable Fakta fakta, Map<String, Object> isian,
			@Nullable ServerPlayer pendengar, @Nullable Warga lawan, SplittableRandom acak) {
		public Permintaan {
			isian = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(isian));
		}
	}

	/** Turns meaning into words (42K-T {@code Realisasi}). Must be deterministic in its inputs. */
	@FunctionalInterface
	public interface Perangkai {
		/** The utterance, or null when no rule fits (the fallback realiser answers). */
		@Nullable Ujaran rangkai(Permintaan permintaan);
	}

	private static volatile @Nullable Perangkai perangkai;

	/** Registers the grammar realiser (one owner, 42K-T); null clears it. */
	public static void daftar(@Nullable Perangkai baru) {
		perangkai = baru;
	}

	/** True while a grammar realiser is registered. */
	public static boolean adaTataBahasa() {
		return perangkai != null;
	}

	/** One utterance for a request: the grammar first, the fallback keys second. Never null. */
	public static Ujaran ujaran(Permintaan p) {
		Perangkai r = perangkai;
		if (r != null) {
			try {
				Ujaran u = r.rangkai(p);
				if (u != null && !u.kosong()) {
					return lengkapi(u, p);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Conversation grammar failed on {} / {}", p.niat(), p.topik(), exception);
			}
		}
		return cadangan(p);
	}

	/** Fills gesture, face and short voice of a grammar utterance that left them open. */
	private static Ujaran lengkapi(Ujaran u, Permintaan p) {
		int gestur = u.gestur() != 0 ? u.gestur() : gestur(p);
		String seru = u.seru() != null ? u.seru() : seru(p, null);
		int wajah = u.wajah() >= 0 ? u.wajah() : wajah(p.situasi());
		return gestur == u.gestur() && wajah == u.wajah() && java.util.Objects.equals(seru, u.seru()) ? u
				: new Ujaran(u.akar(), u.niat(), gestur, seru, wajah);
	}

	// ================================================================== turn shape

	/** Utterances in one NPC turn: 1-3, the talkative say more (42.5A.6: X). */
	public static int panjang(Kepribadian.Sifat s, SplittableRandom r) {
		double extra = (s.x() + 100) / 200.0D;
		int n = 1;
		if (r.nextDouble() < extra * 0.7D) {
			n++;
		}
		if (n == 2 && r.nextDouble() < extra * 0.35D) {
			n++;
		}
		return n;
	}

	/** True when a listener answers a long utterance with a back-channel ({@code hmm}, {@code oh ya?}; 30 %). */
	public static boolean sambungRasa(SplittableRandom r) {
		return r.nextDouble() < 0.3D;
	}

	// ================================================================== gesture, face, voice

	/** Clip id of a gesture name ({@code GerakId}, then {@code KotaGerakId}, then the short aliases of the corpus). */
	public static int gestur(@Nullable String nama) {
		if (nama == null || nama.isEmpty()) {
			return 0;
		}
		String key = nama.toLowerCase(Locale.ROOT);
		String alias = switch (key) {
			case "angguk" -> "angguk_dengar";
			case "geleng" -> "menepuk_jidat";
			case "tangan_dada", "doa" -> "sikap_doa";
			case "malu", "sedih", "tunduk" -> "sikap_tunduk";
			case "marah" -> "marah_hentak";
			case "takut" -> "kaget";
			case "angkat_bahu", "ragu" -> "sikap_lipat_tangan";
			case "tawa" -> "tertawa";
			default -> key;
		};
		int id = GerakId.id(alias);
		return id != 0 ? id : KotaGerakId.klipId(alias);
	}

	/** Default gesture of a request: the intent's ({@code niat.json}), else the emotion label's, else talking hands. */
	static int gestur(Permintaan p) {
		Niat.Info info = p.niat().info();
		int id = info == null ? 0 : gestur(info.gestur());
		if (id == 0) {
			id = gestur(Emosi.label(p.situasi().pad()).gestur());
		}
		return id != 0 ? id : GerakId.BICARA_TANGAN;
	}

	/** The face code index of a snapshot (its {@code wajah} id). */
	static int wajah(Topik.Situasi s) {
		return KodeWajah.byId(s.wajah()).ordinal();
	}

	/**
	 * The short voice key ({@code kota42.seru.<x>_l|_p}, or ungendered) of a request: the rule's {@code seru}, else the
	 * intent's default; null when off ({@code kotaBicara.suaraSeru}) or none.
	 */
	public static @Nullable String seru(Permintaan p, @Nullable String dariAturan) {
		if (!KotaConfig.bicara().suaraSeru) {
			return null;
		}
		String base = dariAturan;
		if (base == null) {
			Niat.Info info = p.niat().info();
			base = info == null ? null : info.seru();
		}
		return base == null || base.isEmpty() ? null : seruKunci(base, p.situasi());
	}

	/** Gendered / special voice key of a base name (42.5A.8 table). */
	public static String seruKunci(String base, Topik.Situasi s) {
		return switch (base) {
			case "cih", "wah_anak", "tetua_hmm", "tetua_marah", "mabuk", "bisik_p", "takut_p", "setuju_l" -> CakapData.SERU + base;
			case "bisik" -> CakapData.SERU + "bisik_p";
			case "takut" -> CakapData.SERU + "takut_p";
			case "setuju" -> CakapData.SERU + "setuju_l";
			default -> CakapData.SERU + base + (s.kelamin().equals("p") ? "_p" : "_l");
		};
	}

	// ================================================================== fallback realiser

	/** Fallback keys per intent and their variant count ({@code kultivator.kota42.cakap.cadangan.<niat>.<0..n-1>}). */
	static final Map<String, Integer> CADANGAN = cadanganPeta();

	private static Map<String, Integer> cadanganPeta() {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("sapa", 6);
		m.put("basa_basi", 6);
		m.put("beri_kabar", 4);
		m.put("tanya", 4);
		m.put("reaksi", 6);
		m.put("terima_kasih", 4);
		m.put("tolak", 4);
		m.put("tutup", 6);
		m.put("potong", 4);
		m.put("kembali", 3);
		m.put("kilas_balik", 4);
		m.put("sambung_rasa", 6);
		m.put("koreksi", 3);
		m.put("umpan_gosip", 4);
		m.put("puji", 3);
		m.put("hina", 3);
		m.put("minta", 3);
		m.put("tawar", 3);
		m.put("janji", 3);
		m.put("pamit", 3);
		m.put("canda", 4);
		m.put("keluh", 4);
		m.put("hibur", 4);
		m.put("nasihat", 3);
		m.put("tuduh", 3);
		m.put("bantah", 3);
		m.put("rayu", 3);
		m.put("malu", 3);
		m.put("tawa", 3);
		m.put("sepakat", 3);
		return java.util.Collections.unmodifiableMap(m);
	}

	/** Prefix of the fallback keys. */
	public static final String KUNCI_CADANGAN = "kultivator.kota42.cakap.cadangan.";

	/**
	 * The built-in realiser: one fallback line of the intent (or the nearest family of intents), argument 1 = the address
	 * form, argument 2 = the fact's subject (or the topic word). Deterministic through the request's generator.
	 */
	static Ujaran cadangan(Permintaan p) {
		String niat = keluarga(p.niat().kunci());
		int n = CADANGAN.getOrDefault(niat, 0);
		if (n == 0) {
			niat = "basa_basi";
			n = CADANGAN.get(niat);
		}
		int pick = p.acak().nextInt(n);
		Ujaran.Simpul sebut = Ujaran.Simpul.kunci("kultivator.kota42.cakap.sebutan." + sebutan(p.situasi()));
		Ujaran.Simpul hal = hal(p);
		Ujaran.Simpul akar = Ujaran.Simpul.kunci(KUNCI_CADANGAN + niat + "." + pick, sebut, hal);
		return new Ujaran(akar, p.niat(), gestur(p), seru(p, null), wajah(p.situasi()));
	}

	/** Fallback family of the intents that have no lines of their own (niat.json ids, lower case). */
	private static final Map<String, String> KELUARGA = keluargaPeta();

	private static Map<String, String> keluargaPeta() {
		Map<String, String> m = new LinkedHashMap<>();
		String[][] rows = {
				{"perkenalan", "basa_basi"}, {"maaf", "reaksi"}, {"tanya_balik", "tanya"}, {"tawaran", "tawar"}, {"permintaan_npc", "minta"},
				{"kecewa", "reaksi"}, {"selamat", "reaksi"}, {"belasungkawa", "hibur"}, {"peringatan", "potong"}, {"saran", "nasihat"},
				{"cerita", "beri_kabar"}, {"pamer", "basa_basi"}, {"khawatir", "keluh"}, {"bisik", "umpan_gosip"}, {"takut", "reaksi"},
				{"marah", "hina"}, {"sindir", "hina"}, {"kaget", "reaksi"}, {"ragu", "reaksi"}, {"komentar", "reaksi"}, {"eskalasi", "tuduh"},
				{"lerai", "tolak"}, {"pergi_marah", "tutup"}, {"puji_rayu", "rayu"}, {"malu_malu", "malu"}, {"canda_rayu", "rayu"},
				{"janji_temu", "sepakat"}, {"tawar_balik", "tawar"}, {"batal", "tolak"}, {"peraga", "nasihat"}, {"tanya_murid", "tanya"},
				{"puji_murid", "puji"}, {"tegur", "nasihat"}, {"minta_tolong", "minta"}, {"olok", "canda"}};
		for (String[] r : rows) {
			m.put(r[0], r[1]);
		}
		return java.util.Collections.unmodifiableMap(m);
	}

	/** The fallback family of an intent id (lower case): its own lines, the table above, else small talk. */
	static String keluarga(String niat) {
		if (CADANGAN.containsKey(niat)) {
			return niat;
		}
		return KELUARGA.getOrDefault(niat, "basa_basi");
	}

	/** Address form id of the fallback lines: register first, then the listener's age. */
	static String sebutan(Topik.Situasi s) {
		return switch (s.register()) {
			case "kuno" -> "senior";
			case "kasar" -> "kau";
			case "sopan" -> "tuan";
			default -> s.tahap().equals("anak") ? "kakak" : "kawan";
		};
	}

	/** The thing talked about: the fact's subject name, the topic word, or "itu". */
	static Ujaran.Simpul hal(Permintaan p) {
		Fakta f = p.fakta();
		if (f != null) {
			Ujaran.Simpul s = rujuk(f.subjek(), p);
			if (s != null) {
				return s;
			}
		}
		String topik = p.topik();
		return Ujaran.Simpul.kunci("kultivator.kota42.cakap.hal." + (topik == null || topik.isEmpty() ? "itu" : topikKata(topik)));
	}

	/** Topic words the fallback lines know (others read as "that matter"). */
	static final java.util.Set<String> HAL = java.util.Set.of("harga", "cuaca", "kafilah", "begal", "duka", "keluarga", "kerja", "sekte",
			"acara", "kota", "gosip", "pasar", "panen", "kesehatan", "cinta", "anak", "perjalanan", "makanan", "musim", "bahaya", "itu");

	private static String topikKata(String topik) {
		return HAL.contains(topik) ? topik : "itu";
	}

	/** A name node for a reference (warga name, player name, place, item, literal word), null when unknown. */
	static @Nullable Ujaran.Simpul rujuk(Fakta.Rujuk r, Permintaan p) {
		switch (r.jenis()) {
			case WARGA -> {
				Warga w = WargaApi.byId((int) r.id());
				return w == null ? null : Ujaran.Simpul.teks(WargaApi.nama(w));
			}
			case PEMAIN -> {
				ServerPlayer pl = p.pendengar();
				if (pl != null && p.situasi().tahuNama()) {
					return Ujaran.Simpul.teks(pl.getName());
				}
				return Ujaran.Simpul.kunci("kultivator.kota42.cakap.hal.pendekar");
			}
			case BARANG -> {
				net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(r.kunci());
				var item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				return item == null ? null : Ujaran.Simpul.teks(Component.translatable(item.getDescriptionId()));
			}
			case KATA -> {
				return Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + r.kunci());
			}
			default -> {
				return null;
			}
		}
	}

	/** A list of utterances as components (for logs and tests). */
	public static List<Component> komponen(List<Ujaran> list) {
		List<Component> out = new ArrayList<>(list.size());
		for (Ujaran u : list) {
			out.add(u.komponen());
		}
		return out;
	}

	private Giliran() {
	}
}
