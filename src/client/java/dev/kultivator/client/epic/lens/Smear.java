package dev.kultivator.client.epic.lens;

import dev.kultivator.Kultivator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The whip-pan smear (DESIGN 30.2.7 SMEAR): 4-10 stretched Kenney {@code trace} strokes ({@code smear.png}) across the
 * screen along the pan direction, α ∝ the camera's angular speed (0.6-0.9 at a whip's peak). Fakes motion blur without a
 * post chain. The stroke rows come from the seed, so a replay looks the same.
 */
final class Smear {

	private static final Identifier STROKE = Kultivator.id("textures/misc/film/smear.png");

	static void draw(GuiGraphicsExtractor g, int width, int height, float level, float yawRate, float pitchRate, int strokes, long seed, int rgb) {
		boolean vertical = Math.abs(pitchRate) > Math.abs(yawRate) * 1.2F;
		float direction = vertical ? Math.signum(pitchRate) : Math.signum(yawRate);
		long mix = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
		for (int i = 0; i < strokes; i++) {
			mix = mix * 6364136223846793005L + 1442695040888963407L;
			float row = ((mix >>> 33) & 0xFFFF) / 65535.0F;
			float lengthK = 0.45F + (((mix >>> 17) & 0xFF) / 255.0F) * 0.55F;
			float shift = (((mix >>> 9) & 0xFF) / 255.0F - 0.5F) * 0.4F;
			float alpha = level * (0.35F + 0.65F * (((mix >>> 25) & 0xFF) / 255.0F));
			int color = (Math.round(Math.min(0.9F, alpha) * 255.0F) << 24) | (i % 3 == 0 ? (rgb & 0xFFFFFF) : 0xFFFFFF);
			if (!vertical) {
				int w = Math.round(width * lengthK);
				int h = Math.max(2, Math.round(height * (0.02F + 0.03F * ((i * 37) % 5) / 4.0F)));
				int x = Math.round(width * (0.5F + shift) - w * 0.5F);
				int y = Math.round(row * height);
				float u = direction >= 0.0F ? 0.0F : 128.0F;
				g.blit(RenderPipelines.GUI_TEXTURED, STROKE, x, y, u, 0.0F, w, h, direction >= 0.0F ? 128 : -128, 32, 128, 32, color);
			} else {
				int h = Math.round(height * lengthK);
				int w = Math.max(2, Math.round(width * (0.012F + 0.018F * ((i * 37) % 5) / 4.0F)));
				int x = Math.round(row * width);
				int y = Math.round(height * (0.5F + shift) - h * 0.5F);
				g.pose().pushMatrix();
				g.pose().translate(x + w * 0.5F, y + h * 0.5F);
				g.pose().rotate((float) (Math.PI * 0.5D));
				g.blit(RenderPipelines.GUI_TEXTURED, STROKE, -h / 2, -w / 2, 0.0F, 0.0F, h, w, 128, 32, 128, 32, color);
				g.pose().popMatrix();
			}
		}
	}

	private Smear() {
	}
}
