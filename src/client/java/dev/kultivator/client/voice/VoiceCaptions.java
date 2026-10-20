package dev.kultivator.client.voice;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.voice.VoicePayload;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Voice captions at the bottom centre, above the hotbar: the speaker's name (small, in its colour), the line in the
 * player's language and the original Japanese line under it, on a soft dark band that fades in and out. At most three
 * at once (newest at the bottom); the voice of Heaven is gold with a thin glowing rule. F1 and "Teks suara: Mati" hide it.
 */
public final class VoiceCaptions implements HudElement {

	private static final int MAX = 3;
	private static final long FADE_IN = 250L;
	private static final long FADE_OUT = 700L;
	private static final int ROW = 34;

	private record Line(int speakerId, Component speaker, int color, Component text, @Nullable Component ja, int kind, long start, long length) {
	}

	private static final Deque<Line> LINES = new ArrayDeque<>();

	/** Shows a caption; the same speaker's previous caption (by entity id) is replaced. */
	static void push(int speakerId, Component speaker, int color, Component text, @Nullable Component ja, int kind) {
		long now = Util.getMillis();
		if (speakerId >= 0) {
			LINES.removeIf(line -> line.speakerId() == speakerId);
		}
		int chars = text.getString().length();
		long length = Math.max(3000L, Math.min(9000L, 2200L + 70L * chars)) + (kind == VoicePayload.KIND_HEAVEN ? 1500L : 0L);
		while (LINES.size() >= MAX) {
			LINES.pollFirst();
		}
		LINES.addLast(new Line(speakerId, speaker, color, text, ja, kind, now, length));
	}

	static void clear() {
		LINES.clear();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		if (LINES.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		long now = Util.getMillis();
		for (Iterator<Line> it = LINES.iterator(); it.hasNext(); ) {
			Line line = it.next();
			if (now - line.start() > line.length()) {
				it.remove();
			}
		}
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || !VoiceSettings.text() || LINES.isEmpty()) {
			return;
		}
		Font font = minecraft.font;
		int width = g.guiWidth();
		int height = g.guiHeight();
		float cx = width / 2.0F;
		float bottom = height - 74.0F;
		List<Line> shown = new ArrayList<>(LINES);
		for (int i = shown.size() - 1, row = 0; i >= 0; i--, row++) {
			Line line = shown.get(i);
			long age = now - line.start();
			float fade = Math.min(1.0F, age / (float) FADE_IN) * Math.min(1.0F, (line.length() - age) / (float) FADE_OUT);
			if (fade <= 0.04F) {
				continue;
			}
			float top = bottom - ROW * (row + 1);
			draw(g, font, line, cx, top, width, fade);
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, Line line, float cx, float top, int screenWidth, float fade) {
		boolean heaven = line.kind() == VoicePayload.KIND_HEAVEN;
		float textW = font.width(line.text());
		float scale = Math.min(1.0F, screenWidth * 0.8F / Math.max(1.0F, textW));
		float jaW = line.ja() == null ? 0.0F : font.width(line.ja()) * 0.75F;
		float nameW = font.width(line.speaker()) * 0.7F;
		float half = Math.min(screenWidth * 0.45F, Math.max(textW * scale, Math.max(jaW, nameW)) / 2.0F + 10.0F);
		float bandTop = top + 1.0F;
		float bandBottom = top + (line.ja() == null ? 23.0F : 31.0F);
		// the soft band: darker in the middle, fading out to the sides
		Brush.rect(g, cx - half, bandTop, cx + half, bandBottom, Palette.argb(Math.round(0x78 * fade), 0x05070C));
		Brush.rect(g, cx - half - 6.0F, bandTop + 1.0F, cx - half, bandBottom - 1.0F, Palette.argb(Math.round(0x40 * fade), 0x05070C));
		Brush.rect(g, cx + half, bandTop + 1.0F, cx + half + 6.0F, bandBottom - 1.0F, Palette.argb(Math.round(0x40 * fade), 0x05070C));
		int accent = line.color() & 0xFFFFFF;
		Brush.rect(g, cx - half * 0.6F, bandTop, cx + half * 0.6F, bandTop + 0.5F, Palette.argb(Math.round((heaven ? 0xE0 : 0x90) * fade), accent));
		if (heaven) {
			Brush.rect(g, cx - half * 0.35F, bandBottom - 0.5F, cx + half * 0.35F, bandBottom, Palette.argb(Math.round(0x90 * fade), accent));
		}
		int alpha = Math.max(0x10, Math.round(0xFF * fade));
		Brush.centered(g, font, line.speaker(), cx, top + 3.0F, Palette.argb(alpha, accent), 0.7F, true);
		int textColor = heaven ? 0xFFF4D0 : line.kind() == VoicePayload.KIND_DEMON ? 0xFFC8CC : 0xFFFFFF;
		Brush.centered(g, font, line.text(), cx, top + 11.0F, Palette.argb(alpha, textColor), scale, true);
		if (line.ja() != null) {
			Brush.centered(g, font, line.ja(), cx, top + 22.0F, Palette.argb(Math.max(0x10, Math.round(0xC0 * fade)), 0xB8B8C0), 0.75F, false);
		}
	}
}
