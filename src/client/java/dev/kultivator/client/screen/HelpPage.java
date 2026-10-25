package dev.kultivator.client.screen;

import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TabBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Panduan › Ensiklopedia (DESIGN 33.3, 33.6 R2, 43.5, G17): short paragraphs on the path in one scroll at text 1.0, a
 * search field ({@code /}) over titles and texts, and a letter index (a mini {@link TabBar}: one chip per first letter of
 * the topic titles; a click jumps to the next topic with that letter). {@link #focus} scrolls to one topic and lights its
 * heading for a second: every "?" seal of the Gulungan lands here. Key tokens in the texts show the live keys
 * ({@link KeyHints#fill}).
 */
final class HelpPage extends Page {

	/**
	 * Topics in reading order. The phase-6 topics (Sect Hall onwards, DESIGN 20.4.1) and the phase-7 topics (DESIGN 19.6:
	 * the Jalan tablet first, for new players) are written by their own modules; a topic whose text is not in the
	 * language files yet is skipped. Phase 8 adds the topics of DESIGN 25.0 (the Jalan Dunia glue writes {@code desa} and
	 * {@code jalan_dunia}); 33 adds {@code titah} (the Titah Langit screen's "?"); the integration audit (M5) adds the duel,
	 * the Living Sect, the settings groups and the glossary ({@code ensiklopedia_sambung.*}).
	 */
	private static final String[] TOPICS = {"gulungan", "di_sini", "jalan", "meditation", "xp", "bottleneck", "breakthrough", "persiapan", "tribulation", "protectors",
			"pills", "techniques", "roots", "dao", "alchemy", "heart_demon", "beasts", "formations", "sects", "ascension", "visuals",
			"aula", "bangun", "calon", "jenjang", "kontribusi", "tugas", "misi", "turnamen", "alam_hampa", "bakat", "pusaka", "bintang", "ikatan", "bimbingan",
			"kehancuran", "teknik", "penguasaan", "kitab_teknik", "kehancuran_diri",
			"harian", "batu_roh", "tanaman_roh", "inti_binatang", "alkimia_lanjut", "relik", "urat_roh", "jimat", "anugerah", "elit", "unsur",
			"kejenuhan", "hu_fa_tribulasi", "momen", "formasi", "formasi_pola",
			// Phase 8 (DESIGN 25.0): each module writes its own topic text; jalan_dunia is the glue's.
			"alam_rahasia", "kultivator_liar", "serbuan", "tempa", "pusaka_diri", "roh_senjata", "cincin", "desa", "jalan_dunia",
			// Phase 9 (DESIGN 26.1): each module writes its own topic text; jalan_abadi is the glue's (P9-batin).
			"alam_abadi", "batu_asal", "binatang_purba", "bencana_bintang", "murka_langit", "dunia_batin", "jurus_ciptaan", "lelang", "jalan_abadi",
			// DESIGN 33 (UI/UX Terpadu): the Titah Langit menu.
			"titah",
			// Phase 34 (DESIGN 34.6): each module writes its own topic text; dunia_luas is module T's overview.
			"dunia_luas", "kota", "pengembara", "saingan", "kota_abadi", "gua_leluhur", "penjara_iblis", "menara_seratus",
			"peristiwa_langit", "harta_lahir", "cuaca_roh", "naga_liar", "evolusi_binatang", "dunia_bawah", "laut_tanpa_batas",
			"kapal_terbang", "peta_dunia",
			// Audit M5 (S11, R25, S31/S32): the Sword Duel (35), the Living Sect (36) and its bigger parts, the 37 slot (skipped
			// until its text exists), the Pengaturan groups' own topics, and one glossary of the words that name two things.
			"duel_pedang", "sekte_hidup", "turnamen_sekte", "diplomasi_sekte", "kebun_tambang", "sekte_langit", "mandiri", "suara", "hud", "tombol",
			// DESIGN 41 (Gabung Sekte): texts in sekte_gabung_ui.
			"gabung_sekte", "kelas_sekte", "corak_sekte",
			// DESIGN 43 (P8, R26): the Gulungan itself, and one topic for every page whose "?" had no text yet: the disciple
			// page, the sect money places, the 38/41 sheets (topic = sheet key, or an alias below) and the themed sheets.
			"murid", "sekte_kas", "sekte_paviliun",
			"gabung_lamaran", "gabung_putusan", "gabung_shifu", "gabung_kartu", "gabung_pangkat", "gabung_paviliun", "gabung_papan",
			"gabung_kediaman", "gabung_politik",
			"dialog", "toko_senjata", "gilda_misi", "bank", "buku_kas", "tagihan", "peta_kota", "teleport", "pasar_binatang", "judi_batu",
			"layanan_kota", "sidang", "acara", "lapak_rekrut", "jurnal", "gilda", "kafilah",
			"tema_kapal", "tema_galangan", "tema_mural", "tema_sutra", "tema_teh", "tema_bait", "tema_herba", "tema_ziarah", "tema_lift",
			"tema_pasar_gelap", "tema_kitab_terlarang", "tema_kontrak", "tema_taruhan", "tema_tawanan", "tema_dokumen", "tema_racik",
			"tema_irama", "tema_tawar", "tema_dagang",
			"glosarium"};
	/** Sheet keys whose page shares one topic with a sibling sheet (same class or same function). */
	private static final Map<String, String> ALIAS = Map.of("dermaga", "teleport", "penginapan", "layanan_kota", "rumah_makan",
			"layanan_kota", "jasa", "layanan_kota", "yamen", "sidang", "gabung_hukum", "sidang", "gilda_alkemis", "gilda", "gilda_tempa", "gilda",
			"tema_kafilah", "kafilah", "gabung_direktori", "gabung_sekte");
	private static final String M = "kultivator.gui43.m17.";
	private static final long HIGHLIGHT_MILLIS = 1000L;
	private static final int CARI_H = 14;
	private static final int INDEKS_H = 13;

	/** The topic a "?" seal asked for before this page was laid out (applied on the next frame). */
	private static @Nullable String pendingFocus;

	private final JalanPage.Gulir gulir = new JalanPage.Gulir();
	private final TabBar indeks = new TabBar();
	private final List<String> shownTopics = new ArrayList<>();
	/** Topics by first letter of their title (the index chips). */
	private final TreeMap<String, List<String>> byLetter = new TreeMap<>();
	private final Map<String, Integer> next = new HashMap<>();
	/** First line of each topic in the scroll (filled when the lines are built). */
	private final Map<String, Integer> topicLine = new HashMap<>();
	private @Nullable InkField cari;
	private String filter = "";
	private boolean kotor = true;
	private int lebarIsi = -1;
	private @Nullable String highlighted;
	private long highlightUntil;

	HelpPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.screen.tab.help");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.screen.tab.help.short");
	}

	@Override
	public int glyph() {
		return Glyphs.HELP;
	}

	@Override
	public String helpTopic() {
		return "gulungan";
	}

	/** Scrolls to {@code topic} and lights its heading for a second (C33.N3; called by the hub for the "?" seals). */
	void focus(String topic) {
		pendingFocus = ALIAS.getOrDefault(topic, topic);
		if (!filter.isEmpty()) {
			// the asked topic may be filtered away: show everything again
			filter = "";
			if (cari != null) {
				cari.setText("");
			}
			kotor = true;
		}
	}

	@Override
	protected void build() {
		shownTopics.clear();
		byLetter.clear();
		Language language = Language.getInstance();
		for (String topic : TOPICS) {
			if (language.has("kultivator.screen.help." + topic)) {
				shownTopics.add(topic);
				String title = Component.translatable("kultivator.screen.help." + topic + ".title").getString().strip();
				String letter = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
				byLetter.computeIfAbsent(letter, c -> new ArrayList<>()).add(topic);
			}
		}
		List<TabBar.Tab> tabs = new ArrayList<>();
		for (String letter : byLetter.keySet()) {
			tabs.add(TabBar.Tab.of(letter, Component.literal(letter)));
		}
		indeks.setTabs(tabs);
		cari = add(new InkField(x, y, w, CARI_H, 40, null).cari(Component.translatable(M + "ensiklopedia.cari"), text -> {
			filter = text;
			kotor = true;
			gulir.keAtas();
		}));
		cari.setText(filter);
		kotor = true;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!mauTombol() && gulir.tombol(event, fokusRing.kunci(), true)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void lukis(Lukis l) {
		int iy = y + CARI_H + 3;
		if (filter.isEmpty()) {
			// "" active: every chip stays clickable, so a second click on a letter goes to its next topic
			indeks.gambar(l, "ensiklopedia.indeks", x, iy, x + w, iy + INDEKS_H, false, TabBar.Rupa.SUB, true, "", this::huruf);
		} else {
			l.samar(Component.translatable(M + "ensiklopedia.hasil", topicLine.size()), x + 2, iy + 2, w - 4);
		}
		int top = iy + INDEKS_H + 3;
		l.g.fill(x + 2, top - 2, x + w - 2, top - 1, Palette.argb(0x90, Palette.GOLD));
		int lebar = w - JalanPage.Gulir.RUANG_BATANG;
		if (kotor || lebar != lebarIsi) {
			kotor = false;
			lebarIsi = lebar;
			isi(l, lebar);
		}
		String topic = pendingFocus;
		if (topic != null) {
			pendingFocus = null;
			Integer line = topicLine.get(topic);
			if (line != null) {
				gulir.ke(line);
				highlighted = topic;
				highlightUntil = Util.getMillis() + HIGHLIGHT_MILLIS;
			}
		}
		gulir.gambar(l, "ensiklopedia", x, top, x + w, y + h);
	}

	/** Builds the lines: per shown topic matching the search, its heading (lit while focused) and its paragraph. */
	private void isi(Lukis l, int lebar) {
		gulir.kosongkan();
		topicLine.clear();
		String needle = filter.strip().toLowerCase(Locale.ROOT);
		for (String topic : shownTopics) {
			Component title = Component.translatable("kultivator.screen.help." + topic + ".title");
			Component body = KeyHints.fill(Component.translatable("kultivator.screen.help." + topic));
			if (!needle.isEmpty() && !title.getString().toLowerCase(Locale.ROOT).contains(needle)
					&& !body.getString().toLowerCase(Locale.ROOT).contains(needle)) {
				continue;
			}
			topicLine.put(topic, gulir.jumlah());
			gulir.kepala(title);
			gulir.bungkusUlang(gulir.jumlah() - 1, base -> (lk, i, a, b, c, d, sorot, pilih) -> {
				long now = Util.getMillis();
				if (topic.equals(highlighted) && now < highlightUntil) {
					float fade = (highlightUntil - now) / (float) HIGHLIGHT_MILLIS;
					lk.g.fill(a, b - 1, c, d + 1, Palette.argb(Math.round(0x70 * fade), Palette.GOLD_LIGHT));
				}
				base.gambar(lk, i, a, b, c, d, sorot, pilih);
			});
			gulir.bungkus(l.font, body, 3, lebar, Palette.INK);
			gulir.garis((lk, i, a, b, c, d, sorot, pilih) -> {
			});
		}
		if (topicLine.isEmpty()) {
			gulir.bungkus(l.font, Component.translatable(M + "ensiklopedia.kosong"), 2, lebar, Palette.INK_SOFT);
		} else if (needle.isEmpty()) {
			gulir.bungkus(l.font, Component.translatable("kultivator.screen.help.commands"), 3, lebar, Palette.INK_SOFT);
		}
	}

	/** A letter chip: the next topic whose title starts with it (cycling on repeated clicks). */
	private void huruf(String letter) {
		List<String> topics = byLetter.get(letter);
		if (topics == null || topics.isEmpty()) {
			return;
		}
		int n = next.merge(letter, 1, Integer::sum) - 1;
		pendingFocus = topics.get(Math.floorMod(n, topics.size()));
	}
}
