package dev.kultivator.dunia.kota42.cakap;

/**
 * A speech act (DESIGN 42.5A.1, 42.5A.7, 42.5A.8). The full list (62 niat) is data: {@code percakapan/niat.json} (format in
 * {@link CakapData}); this record is the id plus the constants code refers to. Ids are UPPER_SNAKE ASCII, the same string
 * in {@code niat.json}, in grammar rules ({@code "niat"}) and in golden files.
 * <p>
 * The nine player intents of 42.5A.7 are {@link #TANYA}, {@link #BERI_KABAR}, {@link #MINTA}, {@link #TAWAR}, {@link #JANJI},
 * {@link #TOLAK}, {@link #PUJI}, {@link #HINA}, {@link #PAMIT}; the rest are NPC acts (openers, back-channel, cut-ins,
 * callbacks, closers, the steps of the eight NPC-NPC chats of 42.5A.9).
 */
public record Niat(String id) {

	// ---- player intents (42.5A.7)
	public static final Niat TANYA = new Niat("TANYA");
	public static final Niat BERI_KABAR = new Niat("BERI_KABAR");
	public static final Niat MINTA = new Niat("MINTA");
	public static final Niat TAWAR = new Niat("TAWAR");
	public static final Niat JANJI = new Niat("JANJI");
	public static final Niat TOLAK = new Niat("TOLAK");
	public static final Niat PUJI = new Niat("PUJI");
	public static final Niat HINA = new Niat("HINA");
	public static final Niat PAMIT = new Niat("PAMIT");

	// ---- NPC acts code calls by name (42.5A.6, 42.5A.11)
	public static final Niat SAPA = new Niat("SAPA");
	public static final Niat BASA_BASI = new Niat("BASA_BASI");
	public static final Niat KILAS_BALIK = new Niat("KILAS_BALIK");
	public static final Niat SAMBUNG_RASA = new Niat("SAMBUNG_RASA");
	public static final Niat POTONG = new Niat("POTONG");
	public static final Niat KEMBALI = new Niat("KEMBALI");
	public static final Niat REAKSI = new Niat("REAKSI");
	public static final Niat TERIMA_KASIH = new Niat("TERIMA_KASIH");
	public static final Niat KOREKSI = new Niat("KOREKSI");
	public static final Niat TUTUP = new Niat("TUTUP");

	public static final Niat UMPAN_GOSIP = new Niat("UMPAN_GOSIP");

	public Niat {
		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("empty niat");
		}
	}

	/**
	 * One row of {@code niat.json}.
	 *
	 * @param niat   the intent
	 * @param pelaku {@code npc pemain keduanya}
	 * @param topik  needs a topic
	 * @param tahap  conversation states where it may occur
	 * @param jawab  expected answers (adjacency pairs)
	 * @param seru   default short voice suffix, null for none
	 * @param gestur default gesture name, null for none
	 */
	public record Info(Niat niat, String pelaku, boolean topik, java.util.List<SesiCakap.Tahap> tahap, java.util.List<Niat> jawab,
			@org.jetbrains.annotations.Nullable String seru, @org.jetbrains.annotations.Nullable String gestur) {

		public boolean olehPemain() {
			return !pelaku.equals("npc");
		}

		public boolean olehNpc() {
			return !pelaku.equals("pemain");
		}

		static java.util.Map<String, Info> parse(com.google.gson.JsonObject json) {
			java.util.Map<String, Info> out = new java.util.LinkedHashMap<>();
			for (com.google.gson.JsonObject o : CakapReload.objekList(json, "niat")) {
				String id = CakapReload.teks(o, "id", "");
				if (id.isEmpty()) {
					continue;
				}
				java.util.List<SesiCakap.Tahap> tahap = new java.util.ArrayList<>();
				for (String t : CakapReload.teksList(o, "tahap")) {
					try {
						tahap.add(SesiCakap.Tahap.valueOf(t));
					} catch (IllegalArgumentException ignored) {
						// unknown state: cek_cakap.py reports it
					}
				}
				java.util.List<Niat> jawab = new java.util.ArrayList<>();
				for (String j : CakapReload.teksList(o, "jawab")) {
					jawab.add(new Niat(j));
				}
				out.put(id, new Info(new Niat(id), CakapReload.teks(o, "pelaku", "npc"), CakapReload.benar(o, "topik", false),
						java.util.List.copyOf(tahap), java.util.List.copyOf(jawab), CakapReload.teksAtauNull(o, "seru"),
						CakapReload.teksAtauNull(o, "gestur")));
			}
			return out;
		}
	}

	/** The data row of this intent, or null when {@code niat.json} does not list it. */
	public @org.jetbrains.annotations.Nullable Info info() {
		return CakapReload.niat().get(id);
	}

	/** Lower-case form used in lang keys ({@code kultivator.cakap.niat.<id>} labels the player's option). */
	public String kunci() {
		return id.toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public String toString() {
		return id;
	}
}
