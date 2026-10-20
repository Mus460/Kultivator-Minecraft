package dev.kultivator.client.epic.lens;

import dev.kultivator.Kultivator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Light leaks (DESIGN 30.2.7 LIGHT_LEAK): a warm or Dao-tinted gradient ({@code leak_0..2.png}, ours) sliding in from a
 * screen edge over 10-20 ticks, α ≤ 0.25, then fading. HUD blits only.
 */
final class LightLeak {

	private static final Identifier[] LEAKS = {
			Kultivator.id("textures/misc/film/leak_0.png"), Kultivator.id("textures/misc/film/leak_1.png"),
			Kultivator.id("textures/misc/film/leak_2.png")};
	private static final float MAX_ALPHA = 0.25F;

	private static int rgb;
	private static int side;
	private static int life;
	private static int age = -1;

	static void start(int tint, int edge, int ticks) {
		rgb = tint & 0xFFFFFF;
		side = edge & 3;
		life = Math.max(10, Math.min(20, ticks));
		age = 0;
	}

	static void tick() {
		if (age >= 0 && ++age > life + 10) {
			age = -1;
		}
	}

	static boolean active() {
		return age >= 0;
	}

	static void clear() {
		age = -1;
	}

	static void draw(GuiGraphicsExtractor g, int width, int height, float effect) {
		float t = age / (float) life;
		float fade = t < 0.5F ? t * 2.0F : t <= 1.0F ? 1.0F : Math.max(0.0F, 1.0F - (age - life) / 10.0F);
		float alpha = fade * MAX_ALPHA * Math.max(0.4F, effect);
		if (alpha <= 0.01F) {
			return;
		}
		float slide = Math.min(1.0F, t);
		int w = Math.round(width * 0.7F);
		int color = (Math.round(alpha * 255.0F) << 24) | rgb;
		Identifier leak = LEAKS[side % LEAKS.length];
		if (side % 2 == 0) {
			int x = Math.round(-w * 0.55F + w * 0.45F * slide);
			g.blit(RenderPipelines.GUI_TEXTURED, leak, x, 0, 0.0F, 0.0F, w, height, 128, 128, 128, 128, color);
		} else {
			// from the right edge: the texture's bright side is its left, so u runs backwards (source width -128)
			int x = Math.round(width - w * (0.15F + 0.45F * slide));
			g.blit(RenderPipelines.GUI_TEXTURED, leak, x, 0, 128.0F, 0.0F, w, height, -128, 128, 128, 128, color);
		}
	}

	private LightLeak() {
	}
}
