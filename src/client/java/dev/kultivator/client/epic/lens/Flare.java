package dev.kultivator.client.epic.lens;

import dev.kultivator.Kultivator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Lens flare and the anamorphic streak (DESIGN 30.2.7 FLARE, ANAMORPHIC): the shot's light point projected to the
 * screen with the camera's own axes and FOV; a hot core (Kenney {@code flare}), 3 or 6 ghosts (Kenney {@code circle} and
 * {@code light}) on the line through the screen centre, sized and dimmed by the distance from the centre; at Maximal a
 * horizontal streak ({@code anamorphic.png}, ours) through the point, α ≤ 0.35. It fades when the point is off screen or
 * occluded (one {@code level.clip} from the camera every 4 ticks). HUD blits only.
 */
final class Flare {

	private static final Identifier CORE = Kultivator.id("textures/misc/film/flare_core.png");
	private static final Identifier GHOST = Kultivator.id("textures/misc/film/flare_ghost.png");
	private static final Identifier LIGHT = Kultivator.id("textures/misc/film/flare_light.png");
	private static final Identifier STREAK = Kultivator.id("textures/misc/film/anamorphic.png");
	private static final float[] GHOST_AT = {0.45F, 0.75F, 1.15F, 1.4F, 1.7F, 2.05F};
	private static final int[] GHOST_SIZE = {22, 10, 34, 14, 46, 18};

	private static boolean occluded;
	private static float visible;

	static void reset() {
		occluded = false;
		visible = 0.0F;
	}

	/** Once per 4 ticks: is the light point hidden behind blocks? */
	static void occlusion(Vec3 point) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		Vec3 from = minecraft.gameRenderer.mainCamera().position();
		if (from.distanceToSqr(point) > 160.0D * 160.0D) {
			Vec3 dir = point.subtract(from).normalize();
			point = from.add(dir.scale(160.0D));
		}
		HitResult hit = minecraft.level.clip(new ClipContext(from, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player));
		occluded = hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(point) > 4.0D;
	}

	static void draw(GuiGraphicsExtractor g, int width, int height, Vec3 point, int rgb, int ghosts, boolean streak, float effect) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vec3 rel = point.subtract(camera.position());
		Vector3fc forward = camera.forwardVector();
		Vector3fc up = camera.upVector();
		Vector3fc left = camera.leftVector();
		double z = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		float target = 0.0F;
		float sx = 0.0F;
		float sy = 0.0F;
		if (z > 0.5D) {
			double x = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
			double y = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
			double tan = Math.tan(Math.toRadians(camera.getFov()) * 0.5D);
			float half = height * 0.5F;
			sx = (float) (width * 0.5F + x / z / tan * half);
			sy = (float) (half - y / z / tan * half);
			boolean onScreen = sx > -width * 0.1F && sx < width * 1.1F && sy > -height * 0.1F && sy < height * 1.1F;
			target = onScreen && !occluded ? 1.0F : 0.0F;
		}
		visible += (target - visible) * 0.25F;
		if (visible < 0.02F) {
			return;
		}
		float a = visible * Math.max(0.4F, effect);
		float cx = width * 0.5F;
		float cy = height * 0.5F;
		float edge = (float) Math.min(1.0D, Math.hypot(sx - cx, sy - cy) / Math.hypot(cx, cy));
		int core = Math.round(height * 0.22F);
		blit(g, CORE, sx, sy, core, a * 0.85F, rgb);
		blit(g, LIGHT, sx, sy, core * 2, a * 0.35F, rgb);
		if (streak) {
			int w = Math.round(width * 0.95F);
			int h = Math.max(6, Math.round(height * 0.035F));
			int color = (Math.round(Math.min(0.35F, a * 0.35F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, STREAK, Math.round(sx - w * 0.5F), Math.round(sy - h * 0.5F), 0.0F, 0.0F, w, h, 256, 16, 256, 16, color);
		}
		for (int i = 0; i < Math.min(ghosts, GHOST_AT.length); i++) {
			float k = GHOST_AT[i];
			float gx = sx + (cx - sx) * k;
			float gy = sy + (cy - sy) * k;
			float size = GHOST_SIZE[i] * (height / 240.0F) * (0.6F + 0.8F * edge);
			float alpha = a * (0.10F + 0.12F * edge) * (i % 2 == 0 ? 1.0F : 0.7F);
			blit(g, i % 3 == 1 ? LIGHT : GHOST, gx, gy, Math.round(size), alpha, i % 2 == 0 ? rgb : 0xBFD8FF);
		}
	}

	private static void blit(GuiGraphicsExtractor g, Identifier texture, float x, float y, int size, float alpha, int rgb) {
		if (size <= 1 || alpha <= 0.005F) {
			return;
		}
		int color = (Math.round(Math.min(1.0F, alpha) * 255.0F) << 24) | (rgb & 0xFFFFFF);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, Math.round(x - size * 0.5F), Math.round(y - size * 0.5F), 0.0F, 0.0F, size, size, 64, 64, 64, 64,
				color);
	}

	private Flare() {
	}
}
