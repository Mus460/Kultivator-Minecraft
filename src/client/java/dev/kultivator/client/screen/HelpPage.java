package dev.kultivator.client.screen;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Panduan › Ensiklopedia (DESIGN 33.3, 33.6 R2, C33.P1): short paragraphs on the path, written on the scroll, under a
 * topic index of small jade coins (one per first letter of the topic titles; a click jumps to the next topic with that
 * letter, the tooltip lists them). {@link #focus} scrolls to one topic and lights its heading for a second (the "?" seals
 * of the other screens land here). The key list moved to Pengaturan › Kontrol. Key tokens in the texts show the live
 * keys ({@link KeyHints#fill}). Scrolls with the wheel.
 */
final class HelpPage extends Page {

	/**
	 * Topics in reading order. The phase-6 topics (Sect Hall onwards, DESIGN 20.4.1) and the phase-7 topics (DESIGN 19.6:
	 * the Jalan tablet first, for new players) are written by their own modules; a topic whose text is not in the
	 * language files yet is skipped. Phase 8 adds the topics of DESIGN 25.0 (the Jalan Dunia glue writes {@code desa} and
	 * {@code jalan_dunia}); 33 adds {@code titah} (the Titah Langit screen's "?"); the integration audit (M5) adds the duel,
	 * the Living Sect, the settings groups and the glossary ({@code ensiklopedia_sambung.*}).
	 */
	private static final String[] TOPICS = {"jalan", "meditation", "xp", "bottleneck", "breakthrough", "persiapan", "tribulation", "protectors",
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
			"glosarium"};
	private static final long HIGHLIGHT_MILLIS = 1000L;
	private static final int COIN = 11;
	private static final int COIN_GAP = 2;

	/** The topic a "?" seal asked for before this page was laid out (applied on the next frame). */
	private static @Nullable String pendingFocus;

	private final Eased scrollShown = new Eased(14.0F);
	private final List<String> shownTopics = new ArrayList<>();
	/** Top of each shown topic's heading, relative to the top of the text (measured once per layout). */
	private final Map<String, Float> topicTop = new HashMap<>();
	private final Map<String, Float> topicHeight = new HashMap<>();
	private float scroll;
	private float contentH;
	private float measuredW = -1.0F;
	private int textTop;
	private @Nullable String highlighted;
	private long highlightUntil;

	HelpPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.help");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.help.short");
	}

	@Override
	public String helpTopic() {
		return "jalan";
	}

	/** Scrolls to {@code topic} and lights its heading for a second (C33.N3; called by the hub for the "?" seals). */
	void focus(String topic) {
		pendingFocus = topic;
		applyFocus();
	}

	private float scale() {
		return w >= 300 ? 0.85F : 0.72F;
	}

	@Override
	protected void build() {
		shownTopics.clear();
		Language language = Language.getInstance();
		for (String topic : TOPICS) {
			if (language.has("kultivator.screen.help." + topic)) {
				shownTopics.add(topic);
			}
		}
		// The index: one jade coin per first letter, wrapped into rows across the top of the box.
		TreeMap<Character, List<String>> byLetter = new TreeMap<>();
		for (String topic : shownTopics) {
			String title = Component.translatable("kultivator.screen.help." + topic + ".title").getString().strip();
			char letter = title.isEmpty() ? '?' : Character.toUpperCase(title.charAt(0));
			byLetter.computeIfAbsent(letter, c -> new ArrayList<>()).add(topic);
		}
		int perRow = Math.max(1, (w - 4 + COIN_GAP) / (COIN + COIN_GAP));
		int index = 0;
		for (Map.Entry<Character, List<String>> entry : byLetter.entrySet()) {
			int cx = x + 2 + (index % perRow) * (COIN + COIN_GAP);
			int cy = y + (index / perRow) * (COIN + COIN_GAP);
			add(new LetterCoin(cx, cy, entry.getKey(), entry.getValue()));
			index++;
		}
		int rows = Math.max(1, (index + perRow - 1) / perRow);
		textTop = y + rows * (COIN + COIN_GAP) + 3;
		measuredW = -1.0F;
	}

	/** Measures every topic's height for the current width (the box only draws the ones in view). */
	private void measure() {
		if (measuredW == w) {
			return;
		}
		measuredW = w;
		topicTop.clear();
		topicHeight.clear();
		float scale = scale();
		float textW = w - 14.0F;
		float cursor = 1.0F;
		for (String topic : shownTopics) {
			float height = 12.0F + Brush.wrapHeight(font, body(topic), textW - 3.0F, scale) + 6.0F;
			topicTop.put(topic, cursor);
			topicHeight.put(topic, height);
			cursor += height;
		}
		cursor += Brush.wrapHeight(font, Component.translatable("kultivator.screen.help.commands"), textW - 3.0F, scale * 0.9F) + 12.0F;
		contentH = cursor;
		applyFocus();
	}

	private static Component body(String topic) {
		return KeyHints.fill(Component.translatable("kultivator.screen.help." + topic));
	}

	private void applyFocus() {
		String topic = pendingFocus;
		if (topic == null || measuredW != w) {
			return;
		}
		pendingFocus = null;
		Float top = topicTop.get(topic);
		if (top == null) {
			return;
		}
		scroll = Math.max(0.0F, top - 1.0F);
		highlighted = topic;
		highlightUntil = Util.getMillis() + HIGHLIGHT_MILLIS;
	}

	private float viewH() {
		return y + h - textTop;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		measure();
		float view = viewH();
		float maxScroll = Math.max(0.0F, contentH - view);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		float scale = scale();
		float textW = w - 14.0F;
		long now = Util.getMillis();
		// a thin gold rule under the index
		Brush.rect(g, x + 2.0F, textTop - 2.0F, x + w - 2.0F, textTop - 1.4F, Palette.argb(0x90, Palette.GOLD));
		g.enableScissor(x, textTop, x + w, y + h);
		for (String topic : shownTopics) {
			float top = textTop + topicTop.get(topic) - offset;
			float height = topicHeight.get(topic);
			if (top + height < textTop || top > y + h) {
				continue;
			}
			if (topic.equals(highlighted) && now < highlightUntil) {
				float fade = (highlightUntil - now) / (float) HIGHLIGHT_MILLIS;
				g.fill(x + 1, Math.round(top) - 1, x + w - 3, Math.round(top) + 11, Palette.argb(Math.round(0x70 * fade), Palette.GOLD_LIGHT));
			}
			heading(g, Component.translatable("kultivator.screen.help." + topic + ".title"), x + 2.0F, top, textW, 0.85F);
			Brush.wrap(g, font, body(topic), x + 5.0F, top + 12.0F, textW - 3.0F, Palette.INK, scale, false);
		}
		float commandsTop = textTop + contentH - offset - Brush.wrapHeight(font, Component.translatable("kultivator.screen.help.commands"), textW - 3.0F,
				scale * 0.9F) - 8.0F;
		if (commandsTop < y + h) {
			Brush.wrap(g, font, Component.translatable("kultivator.screen.help.commands"), x + 5.0F, commandsTop, textW - 3.0F, Palette.INK_SOFT,
					scale * 0.9F, false);
		}
		g.disableScissor();

		if (maxScroll > 0.0F) {
			float bead = textTop + 3.0F + (view - 6.0F) * (offset / maxScroll);
			g.fill(x + w - 2, textTop, x + w - 1, y + h, Palette.CORD);
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (inside(mouseX, mouseY)) {
			scroll -= (float) amount * 16.0F;
			return true;
		}
		return false;
	}

	/** One letter of the index: a click jumps to the next topic whose title starts with it. */
	private final class LetterCoin extends Widget {

		private final char letter;
		private final List<String> topics;
		private int next;

		LetterCoin(int x, int y, char letter, List<String> topics) {
			super(x, y, COIN, COIN);
			this.letter = letter;
			this.topics = topics;
			tooltip(this::lines);
		}

		private List<Component> lines() {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("kultivator.uxpages.help.index", String.valueOf(letter)));
			int shown = 0;
			for (String topic : topics) {
				if (shown >= 8) {
					lines.add(Component.translatable("kultivator.uxpages.help.index.more", topics.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
					break;
				}
				lines.add(Component.literal("· ").append(Component.translatable("kultivator.screen.help." + topic + ".title")).withStyle(ChatFormatting.GRAY));
				shown++;
			}
			return lines;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			float glow = hover.get();
			boolean lit = highlighted != null && topics.contains(highlighted) && Util.getMillis() < highlightUntil;
			float cy = pressOffset(over);
			int face = Palette.mix(Palette.mix(Palette.JADE, Palette.JADE_LIGHT, 0.2F), Palette.JADE_LIGHT, glow * 0.6F);
			g.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x40000000);
			Brush.chamferGradient(g, x, Math.round(y + cy), x + w, Math.round(y + h + cy), 2, face, Palette.JADE_DARK);
			Brush.frame(g, x, Math.round(y + cy), x + w, Math.round(y + h + cy), lit ? Palette.GOLD_LIGHT : Palette.argb(0xA0, Palette.GOLD_DARK));
			Component text = Brush.bold(Component.literal(String.valueOf(letter).toUpperCase(Locale.ROOT)));
			Brush.centered(g, font, text, x + w / 2.0F + 0.4F, y + cy + (h - 9.0F * 0.75F) / 2.0F + 0.5F, Palette.TALISMAN, 0.75F, false);
		}

		@Override
		protected void activate() {
			UiSound.tab();
			if (topics.isEmpty()) {
				return;
			}
			String topic = topics.get(Math.floorMod(next, topics.size()));
			next++;
			pendingFocus = topic;
			applyFocus();
		}
	}
}
