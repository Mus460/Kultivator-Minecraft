package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.sosok.GerakId;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Realisation of the typed grammar (DESIGN 42.5A.8): from a meaning (intent, topic, the speaker's register, dialect,
 * feeling and tics, the listener, the fact's values) to an {@link Ujaran} tree of lang keys.
 * <ol>
 * <li>Candidates: every (rule, frame) of the intent (rules of the asked topic first, else topic-less rules, else all) whose
 *     condition holds and whose register/dialect filter allows it; weight {@code (1 + 2 x matched keys) x bobot x
 *     anti-repeat}; one weighted draw.</li>
 * <li>Slots in argument order: an optional slot draws its chance; a fragment slot draws a frame of that rule (depth
 *     &lt;= {@link TataBahasa#KEDALAMAN}); a typed slot becomes a node through {@link TipeSlot}; a lexicon slot is a
 *     dialect word.</li>
 * <li>Variant key: the authored suffix whose features are all active (register, dialect, speaker gender, number), the
 *     most specific first, register before dialect before gender before number.</li>
 * <li>Tic ({@link Tik#sisip}) and the gesture/voice of the rule.</li>
 * </ol>
 * Pure and deterministic: randomness only through {@link #pecahan} ({@code nextLong()} of the given
 * {@link SplittableRandom}); iteration in file order. {@code tools/cek_cakap.py} mirrors this class line for line to write
 * and check the golden files ({@link #ujiGolden}).
 */
public final class Realisasi {

	/** Exclamation voices with a male and a female clip ({@code kota42.seru.<x>_l/_p}). */
	private static final Set<String> SERU_KELAMIN = Set.of("ya", "eh", "hmm", "oh", "tawa", "aduh", "terima_kasih", "maaf");
	/** Exclamation voices with one clip under a suffixed key. */
	private static final Map<String, String> SERU_TETAP = Map.of("bisik", "bisik_p", "takut", "takut_p", "setuju", "setuju_l");
	/** Gesture names used by grammar files that are not catalog names. */
	private static final Map<String, String> GESTUR_ALIAS = Map.of("angguk", "angguk_dengar", "doa", "sikap_doa", "berkacak",
			"sikap_berkacak", "geleng", "menepuk_jidat");
	private static final Pattern FORMAT = Pattern.compile("%(\\d+)\\$s|%%");

	/**
	 * Everything one realisation needs (built by 42K from a {@code Topik.Situasi}, or from a golden file).
	 *
	 * @param niat         intent
	 * @param topik        topic id, null for any
	 * @param register     {@code sopan biasa kasar kuno}
	 * @param dialek       dialect id
	 * @param kelamin      speaker gender {@code l}/{@code p}
	 * @param tahap        speaker life stage {@code anak remaja dewasa tua}
	 * @param peran        speaker role id ({@code NpcRole}), "" when none
	 * @param pembicara    speaker warga id (anti-repeat)
	 * @param label        speaker emotion label ({@code emosi.json} octant or {@code netral})
	 * @param intensitas   intensity word ({@code agak sangat bukan_main}), "" for none
	 * @param arousal      PAD arousal now (tic chance)
	 * @param pendengar    the listener, null for nobody
	 * @param kesan        impression bucket of the listener ({@code musuh asing kenal akrab sahabat}), ""
	 * @param ranahSelisih listener realm minus speaker realm
	 * @param tahuNama     the speaker knows the listener's name
	 * @param nilai        slot values by slot name (without {@code $}) or by source path ({@code fakta.objek}); insertion order
	 * @param hari         world day (relative days)
	 * @param cuaca        weather id of the moment, "" when unknown
	 * @param musim        season id, ""
	 * @param tik          the speaker's own tics ({@link Tik#milik})
	 * @param tikTerakhir  the speaker's previous tic
	 * @param tikPeluang   tic chance (config {@code kotaBicara.tikPeluang}); negative = the data file's
	 * @param gestur       default gesture name when the rule names none (intent or emotion default), null for none
	 * @param cocok        condition matcher of the speaker now (-1 fails, else matched keys)
	 * @param dengar       the listener's anti-repeat ring, null when nobody hears (no memory, no penalty)
	 * @param now          game time
	 */
	public record Masukan(Niat niat, @Nullable String topik, String register, String dialek, String kelamin, String tahap, String peran, int pembicara,
			String label, String intensitas, double arousal, @Nullable TipeSlot.Orang pendengar, String kesan, int ranahSelisih, boolean tahuNama,
			Map<String, Object> nilai, long hari, String cuaca, String musim, List<String> tik, @Nullable String tikTerakhir, double tikPeluang,
			@Nullable String gestur, ToIntFunction<JsonObject> cocok, AntiUlang.@Nullable Cincin dengar, long now) {
		public Masukan {
			nilai = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(nilai));
			tik = List.copyOf(tik);
		}
	}

	/**
	 * A realisation.
	 *
	 * @param ujaran  the utterance
	 * @param aturan  rule id of the root frame, null when nothing was said
	 * @param gestur  gesture name played with it (also resolved to {@link Ujaran#gestur}), null for none
	 * @param tik     tic added, null for none (store it as the speaker's {@code tikTerakhir})
	 * @param lanjut  follow-up topics of the rule
	 * @param dipakai frame keys used (already recorded in {@link Masukan#dengar})
	 */
	public record Keluaran(Ujaran ujaran, @Nullable String aturan, @Nullable String gestur, @Nullable String tik, List<String> lanjut,
			List<String> dipakai) {
		public static final Keluaran KOSONG = new Keluaran(Ujaran.KOSONG, null, null, null, List.of(), List.of());

		public boolean kosong() {
			return aturan == null;
		}
	}

	/** Realises an intent with the loaded grammar. */
	public static Keluaran ujar(Masukan m, SplittableRandom r) {
		return ujar(TataBahasa.tabel(), m, r);
	}

	public static Keluaran ujar(TataBahasa.Tabel t, Masukan m, SplittableRandom r) {
		List<TataBahasa.Aturan> semua = t.untuk(m.niat.id());
		List<TataBahasa.Aturan> rules = semua;
		if (m.topik != null) {
			rules = saring(semua, m.topik);
			if (rules.isEmpty()) {
				rules = saring(semua, null);
			}
			if (rules.isEmpty()) {
				rules = semua;
			}
		}
		List<TataBahasa.Aturan> pa = new ArrayList<>();
		List<TataBahasa.Bingkai> pb = new ArrayList<>();
		List<Double> pw = new ArrayList<>();
		for (TataBahasa.Aturan a : rules) {
			int s = m.cocok.applyAsInt(a.syarat());
			if (s < 0) {
				continue;
			}
			for (TataBahasa.Bingkai b : a.bingkai()) {
				if (b.boleh(m.register, m.dialek) && bisa(a, b, m)) {
					pa.add(a);
					pb.add(b);
					pw.add((1.0D + 2.0D * s) * b.bobot() * faktor(m, b.kunci()));
				}
			}
		}
		if (pw.isEmpty()) {
			return Keluaran.KOSONG;
		}
		int i = pilih(pw, r);
		TataBahasa.Aturan a = pa.get(i);
		List<String> dipakai = new ArrayList<>();
		Ujaran.Simpul akar = bingkai(t, a, pb.get(i), 1, m, r, dipakai);
		String tikId = null;
		if (!m.tik.isEmpty()) {
			Tik.Data d = Tik.sisip(t.tik(), m.tik, m.tikPeluang, m.arousal, m.tikTerakhir, m.cocok, r);
			if (d != null) {
				tikId = d.id();
				akar = bungkus(d, akar, r);
			}
		}
		String gestur = a.gestur() != null ? a.gestur() : m.gestur;
		Ujaran u = new Ujaran(akar, m.niat, gesturId(gestur), seruKunci(a.seru(), m.kelamin), -1);
		if (m.dengar != null) {
			for (String k : dipakai) {
				m.dengar.catat(k, m.pembicara, m.now);
			}
		}
		return new Keluaran(u, a.id(), gestur, tikId, a.lanjut(), List.copyOf(dipakai));
	}

	/** Expands one rule by id (a fragment, or one intent rule chosen by the caller); empty node when it cannot be said. */
	public static Ujaran.Simpul fragmen(TataBahasa.Tabel t, String aturan, Masukan m, SplittableRandom r) {
		return fragmen(t, aturan, 1, m, r, new ArrayList<>());
	}

	private static List<TataBahasa.Aturan> saring(List<TataBahasa.Aturan> semua, @Nullable String topik) {
		List<TataBahasa.Aturan> out = new ArrayList<>();
		for (TataBahasa.Aturan a : semua) {
			if (topik == null ? a.topik() == null : topik.equals(a.topik())) {
				out.add(a);
			}
		}
		return out;
	}

	private static Ujaran.Simpul fragmen(TataBahasa.Tabel t, String id, int depth, Masukan m, SplittableRandom r, List<String> dipakai) {
		TataBahasa.Aturan a = t.aturan().get(id);
		if (a == null || m.cocok.applyAsInt(a.syarat()) < 0) {
			return Ujaran.Simpul.KOSONG;
		}
		List<TataBahasa.Bingkai> pb = new ArrayList<>();
		List<Double> pw = new ArrayList<>();
		for (TataBahasa.Bingkai b : a.bingkai()) {
			if (b.boleh(m.register, m.dialek) && bisa(a, b, m)) {
				pb.add(b);
				pw.add(b.bobot() * faktor(m, b.kunci()));
			}
		}
		if (pw.isEmpty()) {
			return Ujaran.Simpul.KOSONG;
		}
		return bingkai(t, a, pb.get(pilih(pw, r)), depth, m, r, dipakai);
	}

	private static Ujaran.Simpul bingkai(TataBahasa.Tabel t, TataBahasa.Aturan a, TataBahasa.Bingkai b, int depth, Masukan m, SplittableRandom r,
			List<String> dipakai) {
		dipakai.add(b.kunci());
		boolean jamak = false;
		List<Ujaran.Simpul> args = new ArrayList<>(b.slot().size());
		for (String nama : b.slot()) {
			TataBahasa.Slot s = a.slot().get(nama);
			if (s == null) {
				args.add(Ujaran.Simpul.KOSONG);
				continue;
			}
			if (s.opsional() < 1.0D && pecahan(r) >= s.opsional()) {
				args.add(Ujaran.Simpul.KOSONG);
				continue;
			}
			if (s.aturan() != null) {
				args.add(depth >= TataBahasa.KEDALAMAN ? Ujaran.Simpul.KOSONG : fragmen(t, s.aturan(), depth + 1, m, r, dipakai));
			} else if (s.lex() != null) {
				args.add(TipeSlot.lex(t.dialek(), s.lex(), m, ""));
			} else if (s.tipe() != null) {
				args.add(s.tipe().isi(nilai(m, nama, s.dari()), s.dari(), m, t.dialek()));
				if (s.fitur().contains("jamak") && m.nilai.get(polos(nama) + ".jumlah") instanceof Number n && n.longValue() > 1L) {
					jamak = true;
				}
			} else {
				args.add(Ujaran.Simpul.KOSONG);
			}
		}
		return new Ujaran.Simpul(varian(b, m, jamak), args, null);
	}

	/**
	 * False when a frame needs a value the context lacks: a non-optional typed slot of a value-only type (name, item, place,
	 * direction, distance, number, price, days, bond) without a value, an address without a listener, or the speaker's job
	 * without a role. Such frames are never said instead of leaving a hole in the sentence.
	 */
	static boolean bisa(TataBahasa.Aturan a, TataBahasa.Bingkai b, Masukan m) {
		for (String nama : b.slot()) {
			TataBahasa.Slot s = a.slot().get(nama);
			if (s == null || s.tipe() == null || s.opsional() < 1.0D || nilai(m, nama, s.dari()) != null) {
				continue;
			}
			boolean ada = switch (s.tipe()) {
				case NAMA_WARGA, BARANG, TEMPAT, ARAH, JARAK, ANGKA, HARGA, WAKTU_LALU, WAKTU_DEPAN, HUBUNGAN -> false;
				case SEBUTAN_PENDENGAR -> m.pendengar != null;
				case PEKERJAAN -> s.dari().equals("pembicara") && !m.peran.isEmpty();
				default -> true;
			};
			if (!ada) {
				return false;
			}
		}
		return true;
	}

	private static @Nullable Object nilai(Masukan m, String nama, String dari) {
		String key = polos(nama);
		if (m.nilai.containsKey(key)) {
			return m.nilai.get(key);
		}
		if (!dari.isEmpty() && m.nilai.containsKey(dari)) {
			return m.nilai.get(dari);
		}
		return dari.equals("pendengar") ? m.pendengar : null;
	}

	private static String polos(String nama) {
		return nama.startsWith("$") ? nama.substring(1) : nama;
	}

	/** The variant key: all features active, most features first, then the highest-priority feature, then authored order. */
	static String varian(TataBahasa.Bingkai b, Masukan m, boolean jamak) {
		if (b.varian().isEmpty()) {
			return b.kunci();
		}
		List<String> aktif = List.of(m.register, m.dialek, m.kelamin, jamak ? "j" : "s");
		String best = null;
		int bestN = 0;
		int bestP = Integer.MAX_VALUE;
		for (String v : b.varian()) {
			String[] parts = v.split("\\.");
			int p = Integer.MAX_VALUE;
			boolean ok = true;
			for (String part : parts) {
				int idx = aktif.indexOf(part);
				if (idx < 0) {
					ok = false;
					break;
				}
				p = Math.min(p, idx);
			}
			if (ok && (parts.length > bestN || parts.length == bestN && p < bestP)) {
				best = v;
				bestN = parts.length;
				bestP = p;
			}
		}
		return best == null ? b.kunci() : b.kunci() + "." + best;
	}

	private static Ujaran.Simpul bungkus(Tik.Data d, Ujaran.Simpul akar, SplittableRandom r) {
		String b = CakapData.KUNCI + "bungkus.";
		return switch (d.posisi()) {
			case "akhir" -> Ujaran.Simpul.kunci(b + "akhir", akar, Ujaran.Simpul.kunci(CakapData.KUNCI_TIK + d.id()));
			case "sisip" -> Ujaran.Simpul.kunci(b + "sisip", akar, Ujaran.Simpul.kunci(CakapData.KUNCI_TIK + d.id()));
			case "gagap" -> Ujaran.Simpul.kunci(b + "gagap", Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "gagap." + (int) (pecahan(r) * Tik.GAGAP)), akar);
			default -> Ujaran.Simpul.kunci(b + "awal", Ujaran.Simpul.kunci(CakapData.KUNCI_TIK + d.id()), akar);
		};
	}

	private static double faktor(Masukan m, String kunci) {
		return m.dengar == null ? 1.0D : m.dengar.faktor(kunci, m.pembicara, m.now);
	}

	/** Weighted index: {@code u = pecahan x total}, subtract weights in order until below zero. */
	static int pilih(List<Double> w, SplittableRandom r) {
		double total = 0.0D;
		for (double x : w) {
			total += x;
		}
		double u = pecahan(r) * total;
		for (int i = 0; i < w.size(); i++) {
			u -= w.get(i);
			if (u < 0.0D) {
				return i;
			}
		}
		return w.size() - 1;
	}

	/** A double in [0, 1) from one {@code nextLong()} (53 high bits), the only draw the engine makes. */
	public static double pecahan(SplittableRandom r) {
		return (r.nextLong() >>> 11) * 0x1.0p-53;
	}

	/** The clip id of a gesture name ({@code GerakId} names, then {@code KotaGerakId} names, then aliases), 0 for none. */
	public static int gesturId(@Nullable String nama) {
		if (nama == null || nama.isEmpty()) {
			return 0;
		}
		String n = GESTUR_ALIAS.getOrDefault(nama, nama);
		int id = GerakId.id(n);
		return id != 0 ? id : KotaGerakId.klipId(n);
	}

	/** The voice key of a rule's {@code seru} for a speaker gender, null for none. */
	public static @Nullable String seruKunci(@Nullable String seru, String kelamin) {
		if (seru == null || seru.isEmpty()) {
			return null;
		}
		if (SERU_KELAMIN.contains(seru)) {
			return CakapData.SERU + seru + ("p".equals(kelamin) ? "_p" : "_l");
		}
		return CakapData.SERU + SERU_TETAP.getOrDefault(seru, seru);
	}

	/**
	 * Renders a node the way the client does with a language map ({@code %n$s} positional, {@code %%}; a missing key shows
	 * the key; an argument index out of range renders empty). Used for golden files and server-side logs.
	 */
	public static String rangkai(Ujaran.Simpul s, Map<String, String> lang) {
		if (s.teks() != null) {
			return s.teks().getString();
		}
		if (s.kunci().isEmpty()) {
			return "";
		}
		String fmt = lang.getOrDefault(s.kunci(), s.kunci());
		List<String> args = new ArrayList<>(s.arg().size());
		for (Ujaran.Simpul a : s.arg()) {
			args.add(rangkai(a, lang));
		}
		Matcher mt = FORMAT.matcher(fmt);
		StringBuilder out = new StringBuilder();
		while (mt.find()) {
			String rep;
			if (mt.group(1) == null) {
				rep = "%";
			} else {
				int idx = Integer.parseInt(mt.group(1)) - 1;
				rep = idx >= 0 && idx < args.size() ? args.get(idx) : "";
			}
			mt.appendReplacement(out, Matcher.quoteReplacement(rep));
		}
		mt.appendTail(out);
		return out.toString();
	}

	// ================================================================== golden files (42.5A.12)

	/**
	 * Runs a realisation golden file ({@code "jenis": "realisasi"}): every step merges its {@code ubah} over the file's
	 * {@code masukan}, seeds {@code Cakap.benih(benih_dunia, pembicara, 0, hari, step, "cakap")}, runs {@link #ujar} with one
	 * shared listener ring (so later steps feel the anti-repeat memory) at {@code now + 200 x step}, and compares the node,
	 * rule, gesture, voice, tic and both texts.
	 *
	 * @param tulis rewrite the expectations in {@code g} instead of comparing
	 * @return differences (empty = pass)
	 */
	public static List<String> ujiGolden(JsonObject g, TataBahasa.Tabel t, Map<String, String> id, Map<String, String> en, boolean tulis) {
		List<String> beda = new ArrayList<>();
		String nama = TataBahasa.teks(g, "id", "?");
		JsonObject dasar = TataBahasa.objek(g, "masukan");
		long benihDunia = g.has("benih_dunia") ? g.get("benih_dunia").getAsLong() : 0L;
		AntiUlang.Cincin dengar = AntiUlang.Cincin.muat(new long[0], AntiUlang.PEMAIN);
		for (JsonElement e : dasar.has("dengar") ? dasar.getAsJsonArray("dengar") : new JsonArray()) {
			JsonArray x = e.getAsJsonArray();
			dengar.catat(x.get(0).getAsString(), x.get(1).getAsInt(), x.get(2).getAsLong());
		}
		List<JsonObject> langkah = TataBahasa.objekList(g, "langkah");
		for (int i = 0; i < langkah.size(); i++) {
			JsonObject step = langkah.get(i);
			JsonObject mj = dasar.deepCopy();
			for (Map.Entry<String, JsonElement> o : TataBahasa.objek(step, "ubah").entrySet()) {
				mj.add(o.getKey(), o.getValue());
			}
			long now = (mj.has("now") ? mj.get("now").getAsLong() : 0L) + 200L * i;
			Masukan m = dariJson(mj, dengar, now);
			SplittableRandom r = new SplittableRandom(Cakap.benih(benihDunia, m.pembicara, 0, m.hari, i, "cakap"));
			Keluaran k = ujar(t, m, r);
			JsonObject hasil = new JsonObject();
			hasil.add("harap", k.ujaran.akar().golden());
			hasil.add("aturan", teks(k.aturan));
			hasil.add("gestur", teks(k.gestur));
			hasil.add("seru", teks(k.ujaran.seru()));
			hasil.add("tik", teks(k.tik));
			hasil.addProperty("id_id", rangkai(k.ujaran.akar(), id));
			hasil.addProperty("en_us", rangkai(k.ujaran.akar(), en));
			for (Map.Entry<String, JsonElement> o : hasil.entrySet()) {
				if (tulis) {
					step.add(o.getKey(), o.getValue());
				} else if (!o.getValue().equals(step.get(o.getKey()))) {
					beda.add(nama + " step " + i + " " + o.getKey() + ": expected " + step.get(o.getKey()) + ", got " + o.getValue());
				}
			}
		}
		return beda;
	}

	private static JsonElement teks(@Nullable String s) {
		return s == null ? JsonNull.INSTANCE : new JsonPrimitive(s);
	}

	/** Builds a {@link Masukan} from the golden {@code masukan} object (matcher over its {@code situasi}). */
	public static Masukan dariJson(JsonObject o, AntiUlang.@Nullable Cincin dengar, long now) {
		JsonObject situasi = TataBahasa.objek(o, "situasi").deepCopy();
		String register = TataBahasa.teks(o, "register", "biasa");
		String dialek = TataBahasa.teks(o, "dialek", "kampung");
		String kelamin = TataBahasa.teks(o, "kelamin", "l");
		String tahap = TataBahasa.teks(o, "tahap", "dewasa");
		String label = TataBahasa.teks(o, "label", "netral");
		String kesan = TataBahasa.teks(o, "kesan", "");
		String peran = TataBahasa.teks(o, "peran", "");
		int ranah = TataBahasa.angka(o, "ranah_selisih", 0);
		boolean tahu = TataBahasa.benar(o, "tahu_nama", false);
		bawaan(situasi, "register", new JsonPrimitive(register));
		bawaan(situasi, "dialek", new JsonPrimitive(dialek));
		bawaan(situasi, "kelamin", new JsonPrimitive(kelamin));
		bawaan(situasi, "tahap", new JsonPrimitive(tahap));
		bawaan(situasi, "label_emosi", new JsonPrimitive(label));
		bawaan(situasi, "kesan", new JsonPrimitive(kesan));
		bawaan(situasi, "peran", new JsonPrimitive(peran));
		bawaan(situasi, "ranah_selisih", new JsonPrimitive(ranah));
		bawaan(situasi, "tahu_nama", new JsonPrimitive(tahu));
		Map<String, Object> nilai = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : TataBahasa.objek(o, "nilai").entrySet()) {
			nilai.put(e.getKey(), nilaiJson(e.getValue()));
		}
		JsonObject p = o.has("pendengar") && o.get("pendengar").isJsonObject() ? o.getAsJsonObject("pendengar") : null;
		String topik = TataBahasa.teksAtauNull(o, "topik");
		return new Masukan(new Niat(TataBahasa.teks(o, "niat", "SAPA")), topik, register, dialek, kelamin, tahap, peran,
				TataBahasa.angka(o, "pembicara", 0), label, TataBahasa.teks(o, "intensitas", ""), TataBahasa.dbl(o, "arousal", 0.0D),
				p == null ? null : orang(p), kesan, ranah, tahu, nilai, o.has("hari") ? o.get("hari").getAsLong() : 0L, TataBahasa.teks(o, "cuaca", ""),
				TataBahasa.teks(o, "musim", ""), TataBahasa.teksList(o, "tik"), TataBahasa.teksAtauNull(o, "tik_terakhir"),
				TataBahasa.dbl(o, "tik_peluang", -1.0D), TataBahasa.teksAtauNull(o, "gestur"), s -> cocokJson(s, situasi), dengar, now);
	}

	private static void bawaan(JsonObject o, String key, JsonElement v) {
		if (!o.has(key)) {
			o.add(key, v);
		}
	}

	private static TipeSlot.Orang orang(JsonObject p) {
		return new TipeSlot.Orang(p.has("nama") ? Ujaran.Simpul.dariGolden(namaJson(p.get("nama"))) : null, TataBahasa.teks(p, "kelamin", "l"),
				TataBahasa.teks(p, "tahap", "dewasa"), TataBahasa.benar(p, "pemain", false));
	}

	private static JsonElement namaJson(JsonElement e) {
		if (e.isJsonPrimitive()) {
			JsonObject t = new JsonObject();
			t.add("t", e);
			return t;
		}
		return e;
	}

	/** Golden value: number -> Long, string -> String, {"orang": {...}} -> Orang, {"k"/"t"} -> node. */
	private static Object nilaiJson(JsonElement e) {
		if (e.isJsonPrimitive()) {
			return e.getAsJsonPrimitive().isNumber() ? (Object) e.getAsLong() : e.getAsString();
		}
		if (e.isJsonObject() && e.getAsJsonObject().has("orang")) {
			return orang(e.getAsJsonObject().getAsJsonObject("orang"));
		}
		return Ujaran.Simpul.dariGolden(e);
	}

	/** The condition matcher over a plain JSON situation (same keys and rules as {@code Topik.cocok}). */
	public static int cocokJson(@Nullable JsonObject syarat, JsonObject s) {
		if (syarat == null || syarat.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (Map.Entry<String, JsonElement> e : syarat.entrySet()) {
			String key = e.getKey();
			if (key.startsWith("_")) {
				continue;
			}
			JsonElement v = e.getValue();
			Boolean ok = switch (key) {
				case "peran", "kelompok", "tahap", "kelamin", "watak", "jam", "cuaca", "musim", "wajah", "label_emosi", "kesan", "tempat", "dialek",
						"register" -> ada(v, satu(s, key));
				case "kebiasaan", "acara", "kenangan", "hubungan" -> adaSalah(v, banyak(s, key));
				case "sifat" -> sifat(v, TataBahasa.objek(s, "sifat"));
				case "tahu_nama" -> v.isJsonPrimitive() && v.getAsBoolean() == TataBahasa.benar(s, "tahu_nama", false);
				case "fakta" -> s.has("fakta") && ada(v, satu(s, "fakta"));
				case "ranah_selisih" -> v.isJsonPrimitive() && banding(v.getAsString(), TataBahasa.angka(s, "ranah_selisih", 0));
				default -> null;
			};
			if (ok == null) {
				continue;
			}
			if (!ok) {
				return -1;
			}
			n++;
		}
		return n;
	}

	private static String satu(JsonObject s, String key) {
		List<String> l = TataBahasa.teksList(s, key);
		return l.isEmpty() ? "" : l.get(0);
	}

	private static List<String> banyak(JsonObject s, String key) {
		return TataBahasa.teksList(s, key);
	}

	private static boolean ada(JsonElement v, String value) {
		if (v.isJsonArray()) {
			for (JsonElement e : v.getAsJsonArray()) {
				if (e.isJsonPrimitive() && e.getAsString().equalsIgnoreCase(value)) {
					return true;
				}
			}
			return v.getAsJsonArray().isEmpty();
		}
		return v.isJsonPrimitive() && v.getAsString().equalsIgnoreCase(value);
	}

	private static boolean adaSalah(JsonElement v, List<String> values) {
		if (v.isJsonArray() && v.getAsJsonArray().isEmpty()) {
			return true;
		}
		for (String value : values) {
			if (ada(v, value)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sifat(JsonElement v, JsonObject s) {
		if (!v.isJsonObject()) {
			return true;
		}
		for (Map.Entry<String, JsonElement> e : v.getAsJsonObject().entrySet()) {
			if (!e.getValue().isJsonPrimitive() || !banding(e.getValue().getAsString(), TataBahasa.angka(s, e.getKey(), 0))) {
				return false;
			}
		}
		return true;
	}

	/** {@code ">60"}, {@code "<-40"}, {@code ">=3"}, {@code "<=0"}, {@code "=2"} or a bare number (as {@code Topik.banding}). */
	static boolean banding(String expr, double value) {
		String t = expr.trim();
		try {
			if (t.startsWith(">=")) {
				return value >= Double.parseDouble(t.substring(2));
			}
			if (t.startsWith("<=")) {
				return value <= Double.parseDouble(t.substring(2));
			}
			if (t.startsWith(">")) {
				return value > Double.parseDouble(t.substring(1));
			}
			if (t.startsWith("<")) {
				return value < Double.parseDouble(t.substring(1));
			}
			return value == Double.parseDouble(t.startsWith("=") ? t.substring(1) : t);
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private Realisasi() {
	}
}
