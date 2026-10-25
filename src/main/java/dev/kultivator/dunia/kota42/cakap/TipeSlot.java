package dev.kultivator.dunia.kota42.cakap;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * The typed slots of the grammar (DESIGN 42.5A.8): how a value (a person, a number, a day, a word) becomes an utterance
 * node. Every visible word is a lexicon key of {@code kota_hidup_cakap_lex} chosen through {@link Dialek#kunci}, so the
 * same value reads "Tuan" in the capital, "Bung" at the harbour and "Nak" in a village, and the client shows it in its own
 * language. Numbers above twenty and names are literal leaves. Pure: no world access, no randomness.
 * <p>
 * Values ({@link Realisasi.Masukan#nilai}, looked up by slot name, then by the slot's {@code dari}): {@link Orang} (a
 * person), {@link Ujaran.Simpul} (a ready node, e.g. an item or place name), {@link Number} (count, NB amount, world day,
 * blocks) or {@link String} (a lexicon word, or an id: weather, season, direction, bond, role, item, tic).
 */
public enum TipeSlot {
	SEBUTAN_PENDENGAR, NAMA_WARGA, GANTI, BARANG, TEMPAT, ARAH, JARAK, ANGKA, HARGA, WAKTU_LALU, WAKTU_DEPAN, CUACA, MUSIM, KATA_EMOSI,
	SERU, TIK, PEKERJAAN, HUBUNGAN;

	/** Address words that may carry the listener's name ("Tuan Han", "Kakak Mei"). */
	private static final Set<String> BERNAMA = Set.of("tuan", "nona", "kakak", "adik", "senior", "rekan_daois", "paman", "bibi", "kakek",
			"nenek", "bapak", "ibu", "tuan_abadi");

	/**
	 * A person as a slot value.
	 *
	 * @param nama    name node (literal or key), null when unknown
	 * @param kelamin {@code l}/{@code p}
	 * @param tahap   {@code anak remaja dewasa tua}
	 * @param pemain  a player (players are addressed as adults)
	 */
	public record Orang(@Nullable Ujaran.Simpul nama, String kelamin, String tahap, boolean pemain) {
	}

	/** Lower-case id as written in grammar files. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The type of an id (alias {@code sebutan}, {@code nama}), null when unknown. */
	public static @Nullable TipeSlot dariId(String id) {
		String key = switch (id) {
			case "sebutan" -> "sebutan_pendengar";
			case "nama" -> "nama_warga";
			default -> id;
		};
		for (TipeSlot t : values()) {
			if (t.id().equals(key)) {
				return t;
			}
		}
		return null;
	}

	/**
	 * The node of this slot.
	 *
	 * @param v    the looked-up value, null when the context gave none (defaults: the listener, the speaker's feeling, the
	 *             weather and season of the moment, the speaker's own tic)
	 * @param dari the slot's source path ({@code pembicara}/{@code pendengar} matter for {@link #GANTI})
	 */
	public Ujaran.Simpul isi(@Nullable Object v, String dari, Realisasi.Masukan m, Dialek.Tabel d) {
		if (v instanceof Ujaran.Simpul s && this != SEBUTAN_PENDENGAR) {
			return s;
		}
		return switch (this) {
			case SEBUTAN_PENDENGAR -> v instanceof Orang o ? sebutan(o, m, d) : v instanceof String w ? lex(d, w, m, "")
					: v instanceof Ujaran.Simpul s ? s : m.pendengar() != null ? sebutan(m.pendengar(), m, d) : Ujaran.Simpul.KOSONG;
			case NAMA_WARGA -> v instanceof Orang o && o.nama() != null ? o.nama() : v instanceof String w ? lex(d, w, m, "") : Ujaran.Simpul.KOSONG;
			case GANTI -> ganti(v, dari, m, d);
			case BARANG -> v instanceof String id ? Ujaran.Simpul.kunci(barang(id)) : Ujaran.Simpul.KOSONG;
			case TEMPAT -> v instanceof String w ? lex(d, w, m, "") : Ujaran.Simpul.KOSONG;
			case ARAH -> v instanceof String w ? Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "arah." + w) : Ujaran.Simpul.KOSONG;
			case JARAK -> v instanceof Number n ? jarak(n.longValue(), m, d) : v instanceof String w ? lex(d, w, m, "") : Ujaran.Simpul.KOSONG;
			case ANGKA -> v instanceof Number n ? angka(n.longValue()) : Ujaran.Simpul.KOSONG;
			case HARGA -> v instanceof Number n ? Ujaran.Simpul.kunci("kultivator.kota.nb", literal(Long.toString(n.longValue())))
					: Ujaran.Simpul.KOSONG;
			case WAKTU_LALU -> v instanceof Number n ? lalu(m.hari() - n.longValue(), m, d) : v instanceof String w ? lex(d, w, m, "")
					: Ujaran.Simpul.KOSONG;
			case WAKTU_DEPAN -> v instanceof Number n ? depan(n.longValue() - m.hari(), m, d) : v instanceof String w ? lex(d, w, m, "")
					: Ujaran.Simpul.KOSONG;
			case CUACA -> kata("cuaca.", v instanceof String w ? w : m.cuaca());
			case MUSIM -> kata("musim.", v instanceof String w ? w : m.musim());
			case KATA_EMOSI -> emosi(v instanceof String w ? w : m.label(), m, d);
			case SERU -> lex(d, v instanceof String w ? w : Dialek.seru(d, m.niat().id(), m.label()), m, "");
			case TIK -> tik(v, m);
			case PEKERJAAN -> v instanceof String id ? Ujaran.Simpul.kunci("kultivator.kota.peran." + id)
					: "pembicara".equals(dari) && !m.peran().isEmpty() ? Ujaran.Simpul.kunci("kultivator.kota.peran." + m.peran()) : Ujaran.Simpul.KOSONG;
			case HUBUNGAN -> v instanceof String id ? Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "hub." + id) : Ujaran.Simpul.KOSONG;
		};
	}

	// ------------------------------------------------------------------ the types

	/**
	 * How the speaker addresses a listener (42.5A.8): {@code kuno} by realm gap (Leluhur, Tuan/Nona Abadi, Senior, Rekan
	 * Daois, Anak muda), {@code kasar} "Kawan" (the dialect makes it "Kau"/"Bung"), {@code sopan} Tuan/Nona (Bapak/Ibu to
	 * the old), {@code biasa} by the two life stages (children say Kakek/Paman/Kakak, the old say Anak muda, adults say
	 * Bapak/Adik/Kawan/Kakak). With the name known: "Tuan Han".
	 */
	static Ujaran.Simpul sebutan(Orang o, Realisasi.Masukan m, Dialek.Tabel d) {
		boolean l = !"p".equals(o.kelamin());
		String lt = o.pemain() ? "dewasa" : o.tahap();
		String w;
		switch (m.register()) {
			case "kuno" -> {
				int r = m.ranahSelisih();
				w = r >= 6 ? "leluhur" : r >= 4 ? "tuan_abadi" : r >= 1 ? "senior" : r <= -2 ? "anak_muda" : "rekan_daois";
			}
			case "kasar" -> w = "kawan";
			case "sopan" -> w = lt.equals("tua") ? (l ? "bapak" : "ibu") : (l ? "tuan" : "nona");
			default -> {
				String sp = m.tahap();
				if (sp.equals("anak") || sp.equals("remaja")) {
					w = lt.equals("tua") ? (l ? "kakek" : "nenek") : lt.equals("dewasa") ? (l ? "paman" : "bibi") : "kakak";
				} else if (sp.equals("tua")) {
					w = lt.equals("tua") ? "kawan" : "anak_muda";
				} else {
					w = lt.equals("tua") ? (l ? "bapak" : "ibu") : lt.equals("anak") ? "adik"
							: m.kesan().equals("akrab") || m.kesan().equals("sahabat") ? "kawan" : "kakak";
				}
			}
		}
		if (m.tahuNama() && o.nama() != null && BERNAMA.contains(w)) {
			return Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "sebutan_nama", Ujaran.Simpul.kunci(Dialek.kunciBernama(d, w, m.dialek(), o.kelamin())),
					o.nama());
		}
		return Ujaran.Simpul.kunci(Dialek.kunci(d, w, m.dialek(), o.kelamin()));
	}

	private static Ujaran.Simpul ganti(@Nullable Object v, String dari, Realisasi.Masukan m, Dialek.Tabel d) {
		if (dari.equals("pembicara")) {
			return lex(d, "aku", m, m.kelamin());
		}
		if (dari.equals("pendengar")) {
			return lex(d, "kamu", m, "");
		}
		String kelamin = v instanceof Orang o ? o.kelamin() : "";
		return Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "dia" + (kelamin.isEmpty() ? "" : "." + kelamin));
	}

	/** "minecraft:rice" -> {@code item.minecraft.rice} (callers with a stack pass its name node instead). */
	private static String barang(String id) {
		int c = id.indexOf(':');
		return c < 0 ? "item.minecraft." + id : "item." + id.substring(0, c) + "." + id.substring(c + 1).replace('/', '.');
	}

	private static Ujaran.Simpul jarak(long blok, Realisasi.Masukan m, Dialek.Tabel d) {
		if (blok <= 48L) {
			return lex(d, "dekat", m, "");
		}
		if (blok <= 160L) {
			return Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "tidak_jauh");
		}
		return blok <= 600L ? lex(d, "jauh", m, "") : Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "jauh_sekali");
	}

	/** Number words up to twenty, then digits. */
	static Ujaran.Simpul angka(long n) {
		return n >= 0L && n <= 20L ? Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "angka." + n) : literal(Long.toString(n));
	}

	private static Ujaran.Simpul lalu(long hari, Realisasi.Masukan m, Dialek.Tabel d) {
		if (hari <= 0L) {
			return lex(d, "tadi", m, "");
		}
		if (hari == 1L) {
			return lex(d, "kemarin", m, "");
		}
		if (hari == 2L) {
			return lex(d, "kemarin_lusa", m, "");
		}
		if (hari < 7L) {
			return Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "hari_lalu", angka(hari));
		}
		if (hari < 14L) {
			return lex(d, "minggu_lalu", m, "");
		}
		return hari < 60L ? Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "minggu_lalu_n", angka(hari / 7L)) : lex(d, "lama_sekali", m, "");
	}

	private static Ujaran.Simpul depan(long hari, Realisasi.Masukan m, Dialek.Tabel d) {
		if (hari <= 0L) {
			return lex(d, "nanti", m, "");
		}
		if (hari == 1L) {
			return lex(d, "besok", m, "");
		}
		return hari == 2L ? lex(d, "lusa", m, "") : Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "hari_lagi", angka(hari));
	}

	private static Ujaran.Simpul kata(String awalan, @Nullable String id) {
		return id == null || id.isEmpty() ? Ujaran.Simpul.KOSONG : Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + awalan + id);
	}

	/** "agak cemas" / "sedih bukan main": the intensity wrapper around the dialect's feeling word ("netral" stays bare). */
	private static Ujaran.Simpul emosi(String label, Realisasi.Masukan m, Dialek.Tabel d) {
		if (label.isEmpty()) {
			return Ujaran.Simpul.KOSONG;
		}
		Ujaran.Simpul kata = lex(d, label, m, "");
		String i = m.intensitas();
		return label.equals("netral") || i.isEmpty() ? kata : Ujaran.Simpul.kunci(CakapData.KUNCI_LEX + "emosi." + i, kata);
	}

	private static Ujaran.Simpul tik(@Nullable Object v, Realisasi.Masukan m) {
		if (v instanceof String id) {
			return Ujaran.Simpul.kunci(CakapData.KUNCI_TIK + id);
		}
		for (String id : m.tik()) {
			if (!id.equals("gagap")) {
				return Ujaran.Simpul.kunci(CakapData.KUNCI_TIK + id);
			}
		}
		return Ujaran.Simpul.KOSONG;
	}

	static Ujaran.Simpul lex(Dialek.Tabel d, String kata, Realisasi.Masukan m, String kelamin) {
		return Ujaran.Simpul.kunci(Dialek.kunci(d, kata, m.dialek(), kelamin));
	}

	static Ujaran.Simpul literal(String teks) {
		return Ujaran.Simpul.teks(Component.literal(teks));
	}
}
