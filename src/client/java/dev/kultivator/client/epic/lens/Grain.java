package dev.kultivator.client.epic.lens;

import dev.kultivator.Kultivator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Film grain (DESIGN 30.2.7 GRAIN): four frames of our 64-px noise tiled over the screen at α 0.06, the frame and its
 * offset stepped by the seed every tick, plus Kenney's {@code scratch} at α 0.04 for one tick in 40. HUD blits only.
 */
final class Grain {

	private static final Identifier[] FRAMES = {
			Kultivator.id("textures/misc/film/grain_0.png"), Kultivator.id("textures/misc/film/grain_1.png"),
			Kultivator.id("textures/misc/film/grain_2.png"), Kultivator.id("textures/misc/film/grain_3.png")};
	private static final Identifier SCRATCH = Kultivator.id("textures/misc/film/scratch.png");
	private static final int TILE = 64;
	private static final int ALPHA = 15;

	static void draw(GuiGraphicsExtractor g, int width, int height, long step) {
		long mix = step * 0x9E3779B97F4A7C15L;
		Identifier frame = FRAMES[(int) ((mix >>> 60) & 3)];
		int ox = (int) ((mix >>> 20) & 63);
		int oy = (int) ((mix >>> 30) & 63);
		int color = (ALPHA << 24) | 0xFFFFFF;
		for (int y = -oy; y < height; y += TILE) {
			for (int x = -ox; x < width; x += TILE) {
				g.blit(RenderPipelines.GUI_TEXTURED, frame, x, y, 0.0F, 0.0F, TILE, TILE, TILE, TILE, TILE, TILE, color);
			}
		}
		if (step % 40 == 0) {
			int sx = (int) ((mix >>> 10) & 0x3FF) % Math.max(1, width - 64);
			g.blit(RenderPipelines.GUI_TEXTURED, SCRATCH, sx, 0, 0.0F, 0.0F, 48, height, 128, 128, 128, 128, (10 << 24) | 0xFFFFFF);
		}
	}

	private Grain() {
	}
}
