package dev.kultivator.client.goals;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.goals.Guide;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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
 * for 8 s on a change or while the info key is pressed; a finished step rings an amethyst chime with a gold flash.
 * Hidden by the "Sembunyikan panduan" coin and after the last step.
 */
public final class GuideTracker implements HudElement {

	public static final int WIDTH = 120;
	private static final long IDLE_MILLIS = 20000L;
	private static final long PEEK_MILLIS = 8000L;
	private static final long FLASH_MILLIS = 1200L;

	private static int lastBits = -1;
	private static long lastChange;
	private static long peekUntil;
	private static long flashUntil;
	private static final Eased open = new Eased(9.0F);
	private long lastFrame;

	/** Every client tick: notices finished steps and the info key. */
	static void tick(Minecraft minecraft) {
		if (minecraft.player == null || !ClientCultivation.received()) {
			lastBits = -1;
			return;
		}
		long now = Util.getMillis();
		int bits = Guide.bits(ClientCultivation.data());
		if (lastBits < 0) {
			lastBits = bits;
			lastChange = now;
		} else if (bits != lastBits) {
			if ((bits & ~lastBits) != 0) {
				flashUntil = now + FLASH_MILLIS;
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.9F));
			}
			lastBits = bits;
			// A change opens the slip for 8 s (the first 20 s after joining it stays open anyway).
			peekUntil = Math.max(peekUntil, now + PEEK_MILLIS);
		}
		if (Keys.INFO.isDown()) {
			peekUntil = now + PEEK_MILLIS;
		}
	}

	static void reset() {
		lastBits = -1;
		peekUntil = 0L;
		flashUntil = 0L;
	}

	/** True when the tracker is shown at all (not hidden, steps left, or the HUD demo). */
	public static boolean visible(CultivationData data) {
		return HudDemo.active() || !Guide.hidden(data) && Guide.bits(data) != Guide.ALL;
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
		if (!visible(data)) {
			return;
		}
		boolean demo = HudDemo.active();
		boolean expanded = demo || now - lastChange < IDLE_MILLIS || now < peekUntil;
		float t = open.to(expanded ? 1.0F : 0.0F, seconds);
		int bits = demo ? 0x7F : Guide.bits(data);
		int chapter = Math.min(Guide.CHAPTERS - 1, Guide.currentChapter(bits));
		int step = Guide.currentStep(bits);
		float flash = now < flashUntil ? (flashUntil - now) / (float) FLASH_MILLIS : 0.0F;
		Font font = minecraft.font;
		int width = g.guiWidth();
		int height = g.guiHeight();
		float scale = Math.max(0.65F, Math.min(1.0F, (width - 180.0F) / 300.0F));
		if (t < 0.05F) {
			drawSeal(g, font, width - 9.0F, height / 2.0F, chapter, bits, flash);
			return;
		}
		g.pose().pushMatrix();
		float panelW = WIDTH * scale;
		float x0 = width - 4.0F - panelW * t;
		g.pose().translate(x0, height / 2.0F);
		g.pose().scale(scale, scale);
		drawPanel(g, font, data, bits, chapter, step, flash, t);
		g.pose().popMatrix();
	}

	/** The collapsed form: a 12 px red seal with the chapter number and a gold ring of the chapter's progress. */
	private static void drawSeal(GuiGraphicsExtractor g, Font font, float cx, float cy, int chapter, int bits, float flash) {
		if (flash > 0.0F) {
			Brush.halo(g, cx, cy, 6.0F, 4.0F, 0xFFE07A, flash);
		}
		Brush.disc(g, cx, cy, 7.5F, 0xA0101824);
		Brush.arc(g, cx, cy, 6.2F, 7.4F, 0.0F, 1.0F, 0xFF3A2D12);
		Brush.arc(g, cx, cy, 6.2F, 7.4F, 0.0F, Guide.doneIn(bits, chapter) / (float) Guide.PER_CHAPTER, Palette.GOLD);
		Brush.rect(g, cx - 4.5F, cy - 4.5F, cx + 4.5F, cy + 4.5F, Palette.SEAL);
		Brush.centered(g, font, Component.literal(String.valueOf(chapter + 1)), cx + 0.3F, cy - 3.0F, 0xFFF1D57E, 0.75F, false);
	}

	private static void drawPanel(GuiGraphicsExtractor g, Font font, CultivationData data, int bits, int chapter, int step, float flash, float t) {
		int w = WIDTH;
		float inner = w - 12.0F;
		Component chapterTitle = Component.translatable("kultivator.goals.tracker.chapter", chapter + 1,
				Component.translatable("kultivator.goals.chapter." + (chapter + 1)));
		Component title = step < 0 ? Component.translatable("kultivator.goals.tracker.all_done")
				: Component.translatable("kultivator.goals.step." + Guide.STEPS[step].id());
		Component how = step < 0 ? Component.empty() : how(step, data);
		List<FormattedCharSequence> titleRows = font.split(Brush.bold(title), (int) (inner / 0.7F));
		List<FormattedCharSequence> howRows = font.split(how, (int) (inner / 0.6F));
		int titleCount = Math.min(2, titleRows.size());
		int howCount = Math.min(2, howRows.size());
		boolean reward = GoalsClient.cfg("guide", true) && rewardWaiting(data, bits);
		float h = 16.0F + titleCount * 6.5F + howCount * 5.6F + 10.0F + (reward ? 6.0F : 0.0F);
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

		// Chapter seal and title.
		float sx = 4.0F;
		float sy = top + 3.0F;
		Brush.rect(g, sx, sy, sx + 10.0F, sy + 10.0F, Palette.fade(Palette.SEAL, t));
		Brush.frame(g, Math.round(sx) + 1, Math.round(sy) + 1, Math.round(sx) + 9, Math.round(sy) + 9, Palette.argb(Math.round(0xA0 * t), Palette.TALISMAN));
		Brush.centered(g, font, Component.literal(String.valueOf(chapter + 1)), sx + 5.3F, sy + 1.6F, Palette.fade(0xFFF1D57E, t), 0.8F, false);
		Component count = Component.literal(Guide.doneIn(bits, chapter) + "/" + Guide.PER_CHAPTER);
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
		// Twenty beads, four per chapter, the current one pulsing.
		float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 220.0D);
		float beadStep = (w - 30.0F) / Guide.STEP_COUNT;
		for (int i = 0; i < Guide.STEP_COUNT; i++) {
			float bx = 6.0F + i * beadStep + (i / Guide.PER_CHAPTER) * 1.5F;
			boolean done = (bits & (1 << i)) != 0;
			int color = done ? Palette.GOLD : i == step ? Palette.mix(Palette.JADE_DULL, Palette.GOLD_LIGHT, pulse) : Palette.argb(0x90, 0x3A4A44);
			Brush.rect(g, bx, y, bx + beadStep - 1.0F, y + 2.5F, Palette.fade(color, t));
		}
		Brush.text(g, font, Component.literal(Integer.bitCount(bits) + "/" + Guide.STEP_COUNT), w - 19.0F, y - 1.0F,
				Palette.argb(alpha, Palette.JADE_LIGHT), 0.55F, false);
		if (reward) {
			y += 5.0F;
			Component line = Component.translatable("kultivator.goals.tracker.reward", Keys.INFO.getTranslatedKeyMessage());
			Brush.fit(g, font, line, 6.0F, y, w - 12.0F, Palette.argb(alpha, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse)), 0.6F, 0.45F, false);
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
		return false;
	}

	/** The "how" line of a step, with the live key names and live numbers. */
	public static Component how(int step, CultivationData data) {
		String id = Guide.STEPS[Math.max(0, Math.min(Guide.STEP_COUNT - 1, step))].id();
		String key = "kultivator.goals.step." + id + ".how";
		Rank rank = data.rank();
		return switch (id) {
			case "root", "awaken", "dao" -> Component.translatable(key, Keys.INFO.getTranslatedKeyMessage());
			case "meditate" -> Component.translatable(key, Keys.MEDITATE.getTranslatedKeyMessage());
			case "bar" -> Component.translatable(key, Msg.number(rank.realm().isMortal() ? data.xp() : rank.xpRequired()),
					Msg.number(rank.xpRequired()), Keys.MEDITATE.getTranslatedKeyMessage());
			case "technique" -> Component.translatable(key, Keys.PREVIOUS.getTranslatedKeyMessage(), Keys.NEXT.getTranslatedKeyMessage(),
					Keys.CAST.getTranslatedKeyMessage());
			case "sword" -> Component.translatable(key, Keys.FLYING_SWORD.getTranslatedKeyMessage());
			case "codex" -> Component.translatable(key, Keys.CODEX.getTranslatedKeyMessage(), Math.min(Guide.SEALS_NEEDED, Guide.seals(data)),
					Guide.SEALS_NEEDED);
			default -> Component.translatable(key);
		};
	}
}
