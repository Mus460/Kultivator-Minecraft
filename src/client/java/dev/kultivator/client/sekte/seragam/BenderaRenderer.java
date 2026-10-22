package dev.kultivator.client.sekte.seragam;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The sect banners in the world (DESIGN 36.2.8): on every flagpole of the hall (module S's gate and formation poles,
 * module T's arena poles) a lacquered arm, a gilt spear-tip finial and a tall silk streamer (幡) with the hall's emblem
 * hang from the pole's gilt cap; NPC sects' standards and the test banner also get a drawn pole and stone foot. The
 * cloth is a vertex grid whose rows ripple in the wind (a travelling sine wave growing toward the hem, a slow sway and
 * gusts; stronger in rain and thunder, read from the level, never changed) with red silk tassels at the hem's corners.
 * <p>
 * Weak-laptop budget (DESIGN 36.3.9): at most {@code maxBanners} (server config, ≤ 16) per hall and {@link #MAX_FRAME}
 * per frame; within {@code animRange} blocks the cloth waves (Minimal: 2 rows, no wave; Normal: 8 rows × 2 columns;
 * Maximal: 12 × 3), up to twice that it hangs still as one quad, beyond it nothing is drawn; behind the camera nothing
 * is drawn; never in Iris's shadow pass. Vanilla {@code entityCutout} (two-sided) render types on the hall's dynamic
 * texture and {@code bendera_hiasan.png}; the world's own light at the banner. No blocks, no entities.
 */
public final class BenderaRenderer {

	static final Identifier FITTINGS = Kultivator.id("textures/sekte/seragam/bendera_hiasan.png");
	private static final int MAX_FRAME = 24;
	private static final float INV = 1.0F / 64.0F;

	private static final Map<Identifier, SeragamQuads> CLOTH = new HashMap<>();
	private static final SeragamQuads FITTING = new SeragamQuads(RenderTypes.entityCutout(FITTINGS));
	private static final Vector3f FORWARD = new Vector3f();
	/** The cloth grid of the banner being drawn: x, y, z, u, v per vertex (at most 13 rows × 4 columns). */
	private static final float[] GRID = new float[13 * 4 * 5];

	/** The {@code COLLECT_SUBMITS} listener (with {@link LambangFormasiRenderer} in the same pass). */
	static void collect(LevelRenderContext context) {
		if (!LambangCache.anyGeometry() || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			for (SeragamQuads quads : CLOTH.values()) {
				quads.reset();
			}
			FITTING.reset();
			camera.orientation.transform(0.0F, 0.0F, -1.0F, FORWARD);
			String dim = level.dimension().identifier().toString();
			double time = (level.getGameTime() + partial) / 20.0D;
			float wind = 0.11F + (level.isRaining() ? 0.07F : 0.0F) + (level.isThundering() ? 0.08F : 0.0F);
			int drawn = 0;
			for (LambangCache.Entry entry : LambangCache.live()) {
				if (entry.banners.isEmpty() || !dim.equals(entry.dim)) {
					continue;
				}
				Identifier texture = LambangAtlas.cloth(entry);
				if (texture == null) {
					continue;
				}
				SeragamQuads cloth = CLOTH.computeIfAbsent(texture, id -> new SeragamQuads(RenderTypes.entityCutout(id)));
				int perHall = 0;
				for (SeragamPayloads.Banner banner : entry.banners) {
					if (perHall >= entry.maxBanners || drawn >= MAX_FRAME) {
						break;
					}
					if (banner(level, camera.pos, banner, entry.animRange, cloth, time, wind)) {
						perHall++;
						drawn++;
					}
				}
			}
			LambangFormasiRenderer.collect(level, camera.pos, time);
			PoseStack pose = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (SeragamQuads quads : CLOTH.values()) {
				quads.submit(collector, pose);
			}
			FITTING.submit(collector, pose);
			LambangFormasiRenderer.submit(collector, pose);
		} catch (RuntimeException exception) {
			SeragamClient.warn("banner", exception);
		}
	}

	/** One banner; false when it was culled. */
	private static boolean banner(ClientLevel level, Vec3 cam, SeragamPayloads.Banner banner, int animRange, SeragamQuads cloth, double time, float wind) {
		BlockPos pos = banner.pos();
		boolean standard = banner.kind() == SeragamPayloads.BANNER_STANDARD;
		double capX = pos.getX() + 0.5D;
		double capY = standard ? pos.getY() + 6.0D : pos.getY() + 1.0D;
		double capZ = pos.getZ() + 0.5D;
		double dx = capX - cam.x;
		double dy = capY - 1.5D - cam.y;
		double dz = capZ - cam.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist > animRange * 2.0D) {
			return false;
		}
		if (dist > 8.0D && (dx * FORWARD.x() + dy * FORWARD.y() + dz * FORWARD.z()) / dist < -0.35D) {
			return false;
		}
		EffectQuality.Level quality = EffectQuality.level();
		boolean animated = dist <= animRange && quality != EffectQuality.Level.MINIMAL;
		int rows = !animated ? (dist <= animRange ? 2 : 1) : (quality == EffectQuality.Level.MAXIMAL ? 12 : 8);
		int cols = !animated ? 1 : (quality == EffectQuality.Level.MAXIMAL ? 3 : 2);

		double angle = banner.yaw() / 256.0D * Math.PI * 2.0D;
		float ddx = (float) -Math.sin(angle);
		float ddz = (float) Math.cos(angle);
		float nx = ddz;
		float nz = -ddx;
		int light = light(level, BlockPos.containing(capX + ddx, capY - 2.0D, capZ + ddz));
		float seed = (pos.getX() * 0.37F + pos.getZ() * 0.71F) % 6.283F;

		float cx = (float) (capX - cam.x);
		float cy = (float) (capY - cam.y);
		float cz = (float) (capZ - cam.z);
		boolean formation = banner.kind() == SeragamPayloads.BANNER_FORMATION;
		float width = formation ? 0.9F : 1.0F;
		float height = formation ? 2.2F : (standard ? 2.4F : 2.6F);

		if (standard) {
			standardPole(cx, (float) (pos.getY() - cam.y), cz, light);
		}
		// the arm: a thin lacquered beam from the cap toward the inside, gilt at both ends
		float armY = cy - 0.12F;
		float ax0 = cx + ddx * 0.06F;
		float az0 = cz + ddz * 0.06F;
		float len = width + 0.3F;
		beam(ax0, armY, az0, ddx, ddz, len, 0.065F, light);
		// the finial: two crossed gilt spear-tip quads above the cap
		crossed(FITTING, cx, cy, cz, ddx, ddz, 0.19F, 0.78F, 0.0F, 0.0F, 16 * INV, 32 * INV, light);

		// the cloth: hanging from the arm, rows from the arm down to the hem
		float topY = armY - 0.06F;
		float sx = ax0 + ddx * 0.22F;
		float sz = az0 + ddz * 0.22F;
		for (int j = 0; j <= rows; j++) {
			float t = j / (float) rows;
			for (int i = 0; i <= cols; i++) {
				float s = i / (float) cols;
				float off = 0.0F;
				float along = 0.0F;
				float lift = 0.0F;
				if (animated) {
					double phase = Math.PI * 2.0D * (0.85D * t - time * 0.55D) + s * 1.6D + seed;
					float gust = (float) (0.5D + 0.5D * Math.sin(time * 0.37D + seed));
					float amp = (wind + 0.05F * gust) * (float) Math.pow(t, 1.25D);
					off = amp * (float) Math.sin(phase);
					along = 0.08F * t * t * (float) Math.sin(time * 0.6D + seed) * (0.6F + gust);
					lift = Math.abs(off) * 0.18F * t;
				} else {
					off = 0.04F * t * t;
				}
				float x = sx + ddx * (s * width + along) + nx * off;
				float y = topY - t * height + lift;
				float z = sz + ddz * (s * width + along) + nz * off;
				int o = (j * (cols + 1) + i) * 5;
				GRID[o] = x;
				GRID[o + 1] = y;
				GRID[o + 2] = z;
				GRID[o + 3] = s * 0.5F;
				GRID[o + 4] = t;
			}
		}
		for (int j = 0; j < rows; j++) {
			for (int i = 0; i < cols; i++) {
				int a = (j * (cols + 1) + i) * 5;
				int b = ((j + 1) * (cols + 1) + i) * 5;
				int c = ((j + 1) * (cols + 1) + i + 1) * 5;
				int d = (j * (cols + 1) + i + 1) * 5;
				// the normal of the quad (cross of its diagonals), so the folds catch the light
				float ux = GRID[c] - GRID[a];
				float uy = GRID[c + 1] - GRID[a + 1];
				float uz = GRID[c + 2] - GRID[a + 2];
				float vx = GRID[d] - GRID[b];
				float vy = GRID[d + 1] - GRID[b + 1];
				float vz = GRID[d + 2] - GRID[b + 2];
				float qx = uy * vz - uz * vy;
				float qy = uz * vx - ux * vz;
				float qz = ux * vy - uy * vx;
				float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
				if (ql < 1.0E-5F) {
					qx = nx;
					qy = 0.0F;
					qz = nz;
				} else {
					qx /= ql;
					qy /= ql;
					qz /= ql;
				}
				for (int v : new int[]{a, b, c, d}) {
					cloth.vertex(GRID[v], GRID[v + 1], GRID[v + 2], GRID[v + 3], GRID[v + 4], -1, light, qx, qy, qz);
				}
			}
		}
		// silk tassels from the hem's two corners (and the arm's outer end)
		int left = rows * (cols + 1) * 5;
		int right = (rows * (cols + 1) + cols) * 5;
		crossed(FITTING, GRID[left], GRID[left + 1], GRID[left + 2], ddx, ddz, 0.09F, -0.46F, 16 * INV, 0.0F, 32 * INV, 32 * INV, light);
		crossed(FITTING, GRID[right], GRID[right + 1], GRID[right + 2], ddx, ddz, 0.09F, -0.46F, 16 * INV, 0.0F, 32 * INV, 32 * INV, light);
		crossed(FITTING, ax0 + ddx * len, armY - 0.02F, az0 + ddz * len, ddx, ddz, 0.07F, -0.36F, 16 * INV, 0.0F, 32 * INV, 32 * INV, light);
		return true;
	}

	/** A free-standing standard's stone foot and lacquered pole (NPC sects, the test banner). */
	private static void standardPole(float cx, float groundY, float cz, int light) {
		box(cx, groundY, cz, 0.32F, 0.5F, 40 * INV, 8 * INV, 56 * INV, 24 * INV, light);
		box(cx, groundY + 0.5F, cz, 0.09F, 5.5F, 32 * INV, 8 * INV, 40 * INV, 40 * INV, light);
	}

	/** A vertical square prism of half width {@code r} from {@code y0} up {@code h}: four sides and the top. */
	private static void box(float cx, float y0, float cz, float r, float h, float u0, float v0, float u1, float v1, int light) {
		float y1 = y0 + h;
		float[][] corners = {{cx - r, cz - r}, {cx + r, cz - r}, {cx + r, cz + r}, {cx - r, cz + r}};
		float[][] normals = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int k = 0; k < 4; k++) {
			float[] p = corners[k];
			float[] q = corners[(k + 1) % 4];
			FITTING.quad(new float[]{p[0], y1, p[1]}, new float[]{p[0], y0, p[1]}, new float[]{q[0], y0, q[1]}, new float[]{q[0], y1, q[1]},
					u0, v0, u1, v1, -1, light, normals[k][0], 0.0F, normals[k][1]);
		}
		FITTING.quad(new float[]{cx - r, y1, cz - r}, new float[]{cx - r, y1, cz + r}, new float[]{cx + r, y1, cz + r}, new float[]{cx + r, y1, cz - r},
				u0, v0, u1, v0 + (u1 - u0), -1, light, 0.0F, 1.0F, 0.0F);
	}

	/** A horizontal beam along (dx, dz) of length {@code len} and half size {@code r}: top, bottom and both sides. */
	private static void beam(float x0, float y, float z0, float dx, float dz, float len, float r, int light) {
		float nx = dz;
		float nz = -dx;
		float x1 = x0 + dx * len;
		float z1 = z0 + dz * len;
		float u0 = 32 * INV;
		float u1 = 64 * INV;
		float v0 = 0.0F;
		float v1 = 8 * INV;
		float[] a = {x0 + nx * r, y + r, z0 + nz * r};
		float[] b = {x0 - nx * r, y + r, z0 - nz * r};
		float[] c = {x1 - nx * r, y + r, z1 - nz * r};
		float[] d = {x1 + nx * r, y + r, z1 + nz * r};
		FITTING.quad(a, b, c, d, u0, v0, u1, v1, -1, light, 0.0F, 1.0F, 0.0F);
		float[] e = {x0 + nx * r, y - r, z0 + nz * r};
		float[] f = {x0 - nx * r, y - r, z0 - nz * r};
		float[] g = {x1 - nx * r, y - r, z1 - nz * r};
		float[] h = {x1 + nx * r, y - r, z1 + nz * r};
		FITTING.quad(f, e, h, g, u0, v0, u1, v1, -1, light, 0.0F, -1.0F, 0.0F);
		FITTING.quad(a, e, h, d, u0, v0, u1, v1, -1, light, nx, 0.0F, nz);
		FITTING.quad(c, g, f, b, u0, v0, u1, v1, -1, light, -nx, 0.0F, -nz);
	}

	/**
	 * Two crossed vertical quads centred on (x, y, z), half width {@code r}, reaching {@code h} up (or down when
	 * negative), one along (dx, dz), one across it.
	 */
	private static void crossed(SeragamQuads quads, float x, float y, float z, float dx, float dz, float r, float h, float u0, float v0, float u1,
			float v1, int light) {
		float top = h >= 0.0F ? y + h : y;
		float bottom = h >= 0.0F ? y : y + h;
		float[][] axes = {{dx, dz}, {dz, -dx}};
		for (float[] axis : axes) {
			float[] a = {x - axis[0] * r, top, z - axis[1] * r};
			float[] b = {x - axis[0] * r, bottom, z - axis[1] * r};
			float[] c = {x + axis[0] * r, bottom, z + axis[1] * r};
			float[] d = {x + axis[0] * r, top, z + axis[1] * r};
			quads.quad(a, b, c, d, u0, v0, u1, v1, -1, light, -axis[1], 0.0F, axis[0]);
		}
	}

	static int light(ClientLevel level, BlockPos pos) {
		int block = level.getBrightness(LightLayer.BLOCK, pos);
		int sky = level.getBrightness(LightLayer.SKY, pos);
		return LightCoordsUtil.pack(block, sky);
	}

	static void clear() {
		CLOTH.clear();
	}

	private BenderaRenderer() {
	}
}
