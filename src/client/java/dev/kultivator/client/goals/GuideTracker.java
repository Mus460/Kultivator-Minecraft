package dev.kultivator.client.goals;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.guide.GuideBook;
import dev.kultivator.client.guide.GuideClient;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.goals.Guide;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.List;

/**
 * The guide tracker (DESIGN 19.3.9): a jade slip on the right edge, vertically centred (clear of the sect-build panel
 * top left, the codex toasts and effect icons top right, boss bars and the red sky top centre and the Qi HUD bottom
 * left), about 120 GUI px wide and scaled down on small screens. It shows the chapter seal, the next step with one or two
 * "how" lines (with the live key names) and the progress. It shrinks to a 12 px seal 20 s after joining and opens again
 * for 8 s on a change or while the Akses Cepat key (or the scroll key, if bound) is held (DESIGN 33.6 R12); a finished step rings an amethyst chime with a gold flash.
 * Hidden by the tracker coin in Pengaturan › HUD.
 * <p>
 * What it follows (integration audit T2), first match wins: Jalan Abadi (chapters 11-15) once the player is Dewa Abadi
 * Puncak; Jalan Dunia (chapters 6-10) once Langkah Pertama chapters 1-4 are done and the player is Foundation+; Langkah
 * Pertama chapters 1-5 (the sect steps only once a hall of your own stands, {@link Guide#hermitSkip}); and otherwise the
 * next step of the Kitab Panduan's current chapter, so it never vanishes for the long Immortal Ascension years. The seal
 * says which book it follows ("Langkah" or "Kitab") and the same chapter number as the title.
 */
public final class GuideTracker implements HudElement {

	public static final int WIDTH = 120;
	private static final long IDLE_MILLIS = 20000L;
	private static final long PEEK_MILLIS = 8000L;
	private static final long FLASH_MILLIS = 1200L;
	/** Langkah Pertama chapters 1-4 (steps 0-15), the gate of Jalan Dunia. */
	private static final int FIRST_FOUR = (1 << (4 * Guide.PER_CHAPTER)) - 1;

	/** What the tracker follows. */
	private static final int NONE = -1;
	private static final int LANGKAH = 0;
	private static final int WORLD = 1;
	private static final int ABADI = 2;
	private static final int KITAB = 3;

	private static int lastMode = NONE;
	private static int lastValue = -1;
	private static boolean started;
	private static long lastChange;
	private static long peekUntil;
	private static long flashUntil;
	private static final Eased open = new Eased(9.0F);
	private long lastFrame;

	/** Every client tick: notices finished steps and the peek key. */
	static void tick(Minecraft minecraft) {
		if (minecraft.player == null || !ClientCultivation.received()) {
			started = false;
			return;
		}
		long now = Util.getMillis();
		CultivationData data = ClientCultivation.data();
		int mode = mode(data);
		int value = value(mode, data);
		if (!started) {
			started = true;
			lastMode = mode;
			lastValue = value;
			lastChange = now;
		} else if (mode != lastMode || value != lastValue) {
			boolean progress = mode == lastMode ? (mode == KITAB ? value > lastValue : (value & ~lastValue) != 0) : lastMode != NONE;
			if (progress) {
				flashUntil = now + FLASH_MILLIS;
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.9F));
			}
			lastMode = mode;
			lastValue = value;
			// A change opens the slip for 8 s (the first 20 s after joining it stays open anyway).
			peekUntil = Math.max(peekUntil, now + PEEK_MILLIS);
		}
		if (KeyHints.peekHeld()) {
			peekUntil = now + PEEK_MILLIS;
		}
	}

	static void reset() {
		started = false;
		lastMode = NONE;
		lastValue = -1;
		peekUntil = 0L;
		flashUntil = 0L;
	}

	/** The step bits of a Langkah mode, or the Kitab's done count. */
	private static int value(int mode, CultivationData data) {
		return switch (mode) {
			case LANGKAH -> Guide.bits(data);
			case WORLD -> Guide.bits2(data);
			case ABADI -> Guide.bits3(data);
			case KITAB -> GuideClient.doneCount();
			default -> 0;
		};
	}

	/** What the tracker follows right now (see the class comment), or {@link #NONE}. */
	private static int mode(CultivationData data) {
		if (Guide.hidden(data)) {
			return NONE;
		}
		if (abadi(data)) {
			return ABADI;
		}
		if (world(data)) {
			return WORLD;
		}
		int bits = Guide.bits(data);
		if (Guide.currentStep(bits, Guide.hermitSkip(bits)) >= 0) {
			return LANGKAH;
		}
		return GuideClient.nextStep() != null ? KITAB : NONE;
	}

	/** True when the tracker is shown at all (not hidden, something left to follow, or the HUD demo). */
	public static boolean visible(CultivationData data) {
		return HudDemo.active() || mode(data) != NONE;
	}

	/** True when the tracker follows chapters 11-15 "Jalan Abadi": the player is Dewa Abadi Puncak and steps are left. */
	public static boolean abadi(CultivationData data) {
		return data.rank().isMax() && Guide.bits3(data) != Guide.ALL;
	}

	/** True when the tracker follows chapters 6-10: Langkah chapters 1-4 done, Foundation+, steps left. */
	public static boolean world(CultivationData data) {
		return (Guide.bits(data) & FIRST_FOUR) == FIRST_FOUR && data.rank().realm().atLeast(Realm.FOUNDATION) && Guide.bits2(data) != Guide.ALL;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long now = Util.getMillis();
		float seconds = lastFrame == 0L ? 0.0F : Math.max(0.0F, Math.min(0.25F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()
				|| !EffectQuality.hudVisible() || !ClientCultivation.received() || minecraft.gui.screen() instanceof ChatScreen) {
			return;
		}
		CultivationData data = ClientCultivation.data();
		boolean demo = HudDemo.active();
		int mode = demo ? LANGKAH : mode(data);
		if (mode == NONE) {
			return;
		}
		boolean expanded = demo || now - lastChange < IDLE_MILLIS || now < peekUntil;
		float t = open.to(expanded ? 1.0F : 0.0F, seconds);
		float flash = now < flashUntil ? (flashUntil - now) / (float) FLASH_MILLIS : 0.0F;
		Font font = minecraft.font;
		int width = g.guiWidth();
		int height = g.guiHeight();
		float scale = Math.max(0.65F, Math.min(1.0F, (width - 180.0F) / 300.0F));
		View view = mode == KITAB ? kitabView() : langkahView(mode, data, demo);
		if (view == null) {
			return;
		}
		if (t < 0.05F) {
			drawSeal(g, font, width - 9.0F, height / 2.0F, view, flash);
			return;
		}
		g.pose().pushMatrix();
		float panelW = WIDTH * scale;
		float x0 = width - 4.0F - panelW * t;
		g.pose().translate(x0, height / 2.0F);
		g.pose().scale(scale, scale);
		drawPanel(g, font, data, view, flash, t);
		g.pose().popMatrix();
	}

	/**
	 * What one frame shows: the book's label ("Langkah" or "Kitab"), the chapter number written on the seal and in the
	 * title (always the same number), the chapter's name, the step and its "how" line, the chapter's progress, and for a
	 * Langkah mode its step bits and current step (-1 for the Kitab).
	 */
	private record View(boolean kitab, int number, Component chapterName, Component title, Component how, int doneIn, int size, int bits,
			int step) {
	}

	/** A Langkah Pertama / Jalan Dunia / Jalan Abadi frame (the demo shows chapter 1 with seven steps done). */
	private static View langkahView(int mode, CultivationData data, boolean demo) {
		int bits = demo ? 0x7F : value(mode, data);
		int step = mode == ABADI ? Guide.currentStepAbadi(bits) : mode == LANGKAH && !demo ? Guide.currentStep(bits, Guide.hermitSkip(bits))
				: Guide.currentStep(bits);
		int chapter = step >= 0 ? step / Guide.PER_CHAPTER : Math.min(Guide.CHAPTERS - 1, Guide.currentChapter(bits));
		int offset = mode == ABADI ? Guide.ABADI_FIRST : mode == WORLD ? Guide.CHAPTERS : 0;
		int number = chapter + offset + 1;
		Guide.Step[] steps = mode == ABADI ? Guide.STEPS3 : mode == WORLD ? Guide.STEPS2 : Guide.STEPS;
		Component title = step < 0 ? Component.translatable("kultivator.goals.tracker.all_done")
				: Component.translatable("kultivator.goals.step." + steps[step].id());
		Component how = step < 0 ? Component.empty()
				: offset > 0 ? KeyHints.fill(Component.translatable("kultivator.goals.step." + steps[step].id() + ".how")) : how(step, data);
		return new View(false, number, Component.translatable("kultivator.goals.chapter." + number), title, how, Guide.doneIn(bits, chapter),
				Guide.PER_CHAPTER, bits, step);
	}

	/** A Kitab Panduan frame: the next step of its current chapter, the first line of its text as the "how". */
	private static View kitabView() {
		GuideBook.Step step = GuideClient.nextStep();
		if (step == null) {
			return null;
		}
		int position = GuideClient.current();
		GuideBook.Chapter chapter = GuideBook.chapter(position);
		Language language = Language.getInstance();
		Component title = language.has(step.titleKey()) ? Component.translatable(step.titleKey()) : Component.literal(step.id());
		String body = language.has(step.bodyKey()) ? language.getOrDefault(step.bodyKey()) : "";
		int cut = body.indexOf('\n');
		Component how = KeyHints.fill(Component.literal(cut >= 0 ? body.substring(0, cut) : body));
		return new View(true, position + 1, chapter.title(), title, how, GuideClient.doneIn(position), chapter.steps().size(), 0, -1);
	}

	/** The collapsed form: a 12 px red seal with the chapter number, a gold ring of the chapter's progress, the book's label. */
	private static void drawSeal(GuiGraphicsExtractor g, Font font, float cx, float cy, View view, float flash) {
		if (flash > 0.0F) {
			Brush.halo(g, cx, cy, 6.0F, 4.0F, 0xFFE07A, flash);
		}
		Brush.disc(g, cx, cy, 7.5F, 0xA0101824);
		Brush.arc(g, cx, cy, 6.2F, 7.4F, 0.0F, 1.0F, 0xFF3A2D12);
		Brush.arc(g, cx, cy, 6.2F, 7.4F, 0.0F, view.size() <= 0 ? 0.0F : Math.min(1.0F, view.doneIn() / (float) view.size()), Palette.GOLD);
		Brush.rect(g, cx - 4.5F, cy - 4.5F, cx + 4.5F, cy + 4.5F, view.kitab() ? Palette.JADE_DARK : Palette.SEAL);
		Brush.centered(g, font, Component.literal(String.valueOf(view.number())), cx + 0.3F, cy - 3.0F, 0xFFF1D57E, 0.75F, false);
		// the book's label under the seal: "Langkah" or "Kitab"
		Component label = Component.translatable(view.kitab() ? "kultivator.goals.tracker.label_kitab" : "kultivator.goals.tracker.label_langkah");
		float labelW = font.width(label) * 0.45F;
		Brush.text(g, font, label, Math.min(cx - labelW / 2.0F, g.guiWidth() - 1.0F - labelW), cy + 8.5F, 0xFFF1D57E, 0.45F, true);
	}

	private static void drawPanel(GuiGraphicsExtractor g, Font font, CultivationData data, View view, float flash, float t) {
		int w = WIDTH;
		float inner = w - 12.0F;
		Component chapterTitle = Component.translatable(view.kitab() ? "kultivator.goals.tracker.chapter_kitab" : "kultivator.goals.tracker.chapter_langkah",
				view.number(), view.chapterName());
		List<FormattedCharSequence> titleRows = font.split(Brush.bold(view.title()), (int) (inner / 0.7F));
		List<FormattedCharSequence> howRows = font.split(view.how(), (int) (inner / 0.6F));
		int titleCount = Math.min(2, titleRows.size());
		int howCount = Math.min(2, howRows.size());
		boolean reward = GoalsClient.cfg("guide", true) && rewardWaiting(data, Guide.bits(data));
		// The Kitab Panduan's current chapter (once the guide book has synced), as one small line at the bottom.
		boolean book = !view.kitab() && GuideClient.synced() && !GuideClient.allDone();
		boolean beads = !view.kitab();
		float h = 16.0F + titleCount * 6.5F + howCount * 5.6F + (beads ? 10.0F : 4.0F) + (reward ? 6.0F : 0.0F) + (book ? 6.0F : 0.0F);
		float top = -h / 2.0F;
		int alpha = Math.round(255 * Math.min(1.0F, t * 1.4F));

		// The slip: dark jade with a gold edge, two cords, a red seal knot at the corner.
		g.fill(1, Math.round(top) + 1, w + 1, Math.round(top + h) + 1, Palette.argb(Math.round(0x50 * t), 0x000000));
		g.fill(0, Math.round(top), w, Math.round(top + h), Palette.argb(Math.round(0xE6 * t), 0x112A23));
		int edge = flash > 0.0F ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, flash) : Palette.GOLD;
		Brush.frame(g, 0, Math.round(top), w, Math.round(top + h), Palette.fade(edge, t));
		Brush.frame(g, 1, Math.round(top) + 1, w - 1, Math.round(top + h) - 1, Palette.argb(Math.round(0x90 * t), Palette.JADE_DARK));
		if (flash > 0.0F) {
			g.fill(1, Math.round(top) + 1, w - 1, Math.round(top + h) - 1, Palette.argb(Math.round(70 * flash), 0xFFE07A));
		}

		// Chapter seal and title: the seal carries the same number as the title (integration audit T2).
		float sx = 4.0F;
		float sy = top + 3.0F;
		Brush.rect(g, sx, sy, sx + 10.0F, sy + 10.0F, Palette.fade(view.kitab() ? Palette.JADE_DARK : Palette.SEAL, t));
		Brush.frame(g, Math.round(sx) + 1, Math.round(sy) + 1, Math.round(sx) + 9, Math.round(sy) + 9, Palette.argb(Math.round(0xA0 * t), Palette.TALISMAN));
		Brush.centered(g, font, Component.literal(String.valueOf(view.number())), sx + 5.3F, sy + 1.6F, Palette.fade(0xFFF1D57E, t), 0.8F, false);
		Component count = Component.literal(view.doneIn() + "/" + view.size());
		float countW = font.width(count) * 0.6F;
		Brush.fit(g, font, chapterTitle, sx + 13.0F, sy + 2.0F, w - 20.0F - countW - sx, Palette.argb(alpha, Palette.GOLD_LIGHT), 0.65F, 0.45F, false);
		Brush.text(g, font, count, w - 4.0F - countW, sy + 2.5F, Palette.argb(alpha, Palette.JADE_LIGHT), 0.6F, false);

		float y = top + 16.0F;
		for (int i = 0; i < titleCount; i++) {
			Brush.text(g, font, titleRows.get(i), 6.0F, y, Palette.argb(alpha, 0xFFFFFF), 0.7F, false);
			y += 6.5F;
		}
		for (int i = 0; i < howCount; i++) {
			Brush.text(g, font, howRows.get(i), 6.0F, y, Palette.argb(alpha, Palette.JADE_PALE), 0.6F, false);
			y += 5.6F;
		}
		y += 2.0F;
		if (beads) {
			// Twenty beads, four per chapter, the current one pulsing.
			int bits = view.bits();
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
			float beadStep = (w - 30.0F) / Guide.STEP_COUNT;
			for (int i = 0; i < Guide.STEP_COUNT; i++) {
				float bx = 6.0F + i * beadStep + (i / Guide.PER_CHAPTER) * 1.5F;
				boolean done = (bits & (1 << i)) != 0;
				int color = done ? Palette.GOLD : i == view.step() ? Palette.mix(Palette.JADE_DULL, Palette.GOLD_LIGHT, pulse) : Palette.argb(0x90, 0x3A4A44);
				Brush.rect(g, bx, y, bx + beadStep - 1.0F, y + 2.5F, Palette.fade(color, t));
			}
			Brush.text(g, font, Component.literal(Integer.bitCount(bits) + "/" + Guide.STEP_COUNT), w - 19.0F, y - 1.0F,
					Palette.argb(alpha, Palette.JADE_LIGHT), 0.55F, false);
		}
		if (reward) {
			y += 5.0F;
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
			Component line = Component.translatable("kultivator.goals.tracker.reward", KeyHints.hubKey());
			Brush.fit(g, font, line, 6.0F, y, w - 12.0F, Palette.argb(alpha, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse)), 0.6F, 0.45F, false);
		}
		if (book) {
			y += 5.0F;
			Component line = Component.translatable("kultivator.guide_client.tracker", GuideClient.current() + 1);
			Brush.fit(g, font, line, 6.0F, y, w - 12.0F, Palette.argb(alpha, Palette.JADE_LIGHT), 0.55F, 0.4F, false);
		}
	}

	/** A finished chapter whose reward was not taken yet. */
	public static boolean rewardWaiting(CultivationData data, int bits) {
		int claimed = Guide.claimed(data);
		for (int chapter = 0; chapter < Guide.CHAPTERS; chapter++) {
			if (Guide.chapterDone(bits, chapter) && (claimed & (1 << chapter)) == 0) {
				return true;
			}
		}
		// Chapters 6-10 keep their claimed bits at 5-9, chapters 11-15 at 10-14.
		for (int chapter = Guide.CHAPTERS; chapter < Guide.ALL_CHAPTERS; chapter++) {
			if (Guide.chapterDoneAny(data, chapter) && (claimed & (1 << chapter)) == 0) {
				return true;
			}
		}
		return false;
	}

	/** The "how" line of a step, with the live key names and live numbers. */
	public static Component how(int step, CultivationData data) {
		String id = Guide.STEPS[Math.max(0, Math.min(Guide.STEP_COUNT - 1, step))].id();
		String key = "kultivator.goals.step." + id + ".how";
		Rank rank = data.rank();
		return switch (id) {
			case "root", "awaken", "dao" -> Component.translatable(key, KeyHints.hubKey());
			case "meditate" -> Component.translatable(key, KeyHints.name(Keys.MEDITATE));
			case "bar" -> Component.translatable(key, Msg.number(rank.realm().isMortal() ? data.xp() : rank.xpRequired()),
					Msg.number(rank.xpRequired()), KeyHints.name(Keys.MEDITATE));
			case "technique" -> Component.translatable(key, KeyHints.name(Keys.PREVIOUS), KeyHints.name(Keys.NEXT), KeyHints.name(Keys.CAST));
			case "sword" -> Component.translatable(key, KeyHints.name(Keys.FLYING_SWORD));
			case "codex" -> Component.translatable(key, KeyHints.name(Keys.CODEX), Math.min(Guide.SEALS_NEEDED, Guide.seals(data)),
					Guide.SEALS_NEEDED);
			default -> KeyHints.fill(Component.translatable(key));
		};
	}
}
