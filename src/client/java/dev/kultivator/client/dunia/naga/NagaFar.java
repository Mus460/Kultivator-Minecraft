package dev.kultivator.client.dunia.naga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.SegmentChain;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.SkyProjection;
import dev.kultivator.dunia.naga.NagaKind;
import dev.kultivator.dunia.naga.NagaLiarEntity;
import dev.kultivator.dunia.naga.NagaPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The far level of detail of a wild Jiao (DESIGN 34.5.11 "LOD: ... beyond render distance a sky-projected silhouette"):
 * where the entity is not tracked (a short render distance) or lies beyond the fog, the server's once-a-second
 * {@link NagaPayloads.Far} keeps a ghost of its flight here, a body chain pushed every client tick from the extrapolated
 * head, and every frame draws it sky-projected (DESIGN 23.10.3, {@link SkyProjection}: exact angular shape at a draw
 * radius inside the fog, only above 15-20 degrees of elevation so it never paints over hills). The look is a dark
 * undulating ribbon against the day sky with a faint glowing spine and two eye glints that brighten at night: at most
 * 42 translucent emissive quads per Jiao (10 / 20 body points by quality), from module B's share of the 7b frame budget,
 * never in a shader pack's shadow pass, no post chain (rule V2; Sodium and Iris safe). Nothing is sent back.
 */
final class NagaFar {

	private static final Identifier BODY_TEX = Kultivator.id("textures/dunia/naga/far_body.png");
	private static final Identifier GLOW_TEX = Kultivator.id("textures/dunia/naga/far_glow.png");
	private static final RenderType BODY = RenderTypes.entityTranslucentEmissive(BODY_TEX);
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(GLOW_TEX);

	/** Body points of the far chain and their spacing (about the real Jiao's 24 blocks). */
	private static final int POINTS = 20;
	private static final float SPACING = 1.25F;
	/** An entry with no update for this long fades out, and is dropped after {@link #DROP_TICKS}. */
	private static final int STALE_TICKS = 30;
	private static final int DROP_TICKS = 50;
	private static final int FADE_IN = 20;
	private static final int MAX_ENTRIES = 4;
	/** The silhouette's day colour and how much it pales at night (the glow takes over). */
	private static final int SHADOW_RGB = 0x1A2030;

	private static final class Ghost {
		final int id;
		NagaKind kind = NagaKind.AIR;
		int flags;
		Vec3 server = Vec3.ZERO;
		Vec3 velocity = Vec3.ZERO;
		long updated;
		long born;
		Vec3 head;
		final SegmentChain chain = new SegmentChain(POINTS, SPACING);
		float yaw;
		float pitch;
		boolean pushed;

		Ghost(int id) {
			this.id = id;
		}
	}

	private static final Map<Integer, Ghost> GHOSTS = new HashMap<>();
	private static long ticks;
	private static ClientLevel lastLevel;
	private static boolean warned;

	// ---- the frame (camera-relative geometry, written once per sink through a reused writer)
	private static final Sink SHADOW = new Sink(BODY);
	private static final Sink CORE = new Sink(BODY);
	private static final Sink EYES = new Sink(GLOW);
	private static final float[] OUT = new float[3];
	private static final float[] PX = new float[POINTS + 1];
	private static final float[] PY = new float[POINTS + 1];
	private static final float[] PZ = new float[POINTS + 1];
	private static final float[] PK = new float[POINTS + 1];
	private static final float[] PA = new float[POINTS + 1];

	static void onFar(NagaPayloads.Far far) {
		Ghost ghost = GHOSTS.get(far.entityId());
		if (ghost == null) {
			if (GHOSTS.size() >= MAX_ENTRIES) {
				return;
			}
			ghost = new Ghost(far.entityId());
			ghost.born = ticks;
			GHOSTS.put(far.entityId(), ghost);
		}
		ghost.kind = NagaKind.byOrdinal(far.kind());
		ghost.flags = far.flags();
		ghost.server = new Vec3(far.x(), far.y(), far.z());
		ghost.velocity = new Vec3(far.vx(), far.vy(), far.vz());
		ghost.updated = ticks;
		if (ghost.head == null || ghost.head.distanceToSqr(ghost.server) > 48.0D * 48.0D) {
			ghost.head = ghost.server;
			ghost.chain.reset();
			ghost.pushed = false;
		}
	}

	static void clear() {
		GHOSTS.clear();
	}

	/** End of every client tick: the ghosts fly on between updates. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			GHOSTS.clear();
		}
		if (level == null || minecraft.isPaused() || GHOSTS.isEmpty()) {
			return;
		}
		ticks++;
		Iterator<Ghost> iterator = GHOSTS.values().iterator();
		while (iterator.hasNext()) {
			Ghost ghost = iterator.next();
			long age = ticks - ghost.updated;
			if (age > DROP_TICKS) {
				iterator.remove();
				continue;
			}
			// where the server's last word says it is now (extrapolated a little), approached smoothly
			Vec3 target = ghost.server.add(ghost.velocity.scale(Math.min(age, 25L)));
			Vec3 before = ghost.head;
			ghost.head = before.add(target.subtract(before).scale(0.22D));
			Vec3 move = ghost.head.subtract(before);
			if (move.lengthSqr() > 1.0E-4D) {
				double horizontal = Math.sqrt(move.x * move.x + move.z * move.z);
				ghost.yaw = (float) (Mth.atan2(move.z, move.x) * Mth.RAD_TO_DEG) - 90.0F;
				ghost.pitch = (float) -(Mth.atan2(move.y, horizontal) * Mth.RAD_TO_DEG);
			}
			ghost.chain.push(ghost.head, ghost.yaw, ghost.pitch);
			ghost.pushed = true;
		}
	}

	/** Fabric {@code LevelRenderEvents.COLLECT_SUBMITS}. */
	static void collect(LevelRenderContext context) {
		if (GHOSTS.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD
				|| dev.kultivator.client.epic.kosmik.Kosmik3D.covering()) {
			return;
		}
		long start = System.nanoTime();
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			float fog = camera.fogData.renderDistanceStart;
			if (!(fog > 8.0F)) {
				fog = minecraft.options.getEffectiveRenderDistance() * 16.0F * 0.9F;
			}
			float reach = 0.85F * fog;
			float radius = SkyProjection.drawRadius(fog);
			float night = Mth.clamp(minecraft.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partial)
					* 2.0F, 0.0F, 1.0F);
			float rain = level.getRainLevel(partial);
			SHADOW.reset();
			CORE.reset();
			EYES.reset();
			for (Ghost ghost : GHOSTS.values()) {
				draw(level, ghost, camera.pos, partial, reach, radius, night, rain);
			}
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			SHADOW.submit(collector, poseStack);
			CORE.submit(collector, poseStack);
			EYES.submit(collector, poseStack);
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Far Jiao silhouette skipped a frame", exception);
			}
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	private static void draw(ClientLevel level, Ghost ghost, Vec3 cam, float partial, float reach, float radius, float night, float rain) {
		if (!ghost.pushed) {
			return;
		}
		Vec3 headAt = ghost.chain.pose(0, partial).pos();
		double distance = headAt.distanceTo(cam);
		// the real entity is drawn within the fog: the ghost only covers what the renderer cannot show
		Entity real = level.getEntity(ghost.id);
		if (real instanceof NagaLiarEntity && real.position().distanceTo(cam) < reach || distance < Math.min(reach, 96.0F) * 0.8F) {
			return;
		}
		long age = ticks - ghost.updated;
		float fade = Mth.clamp((ticks - ghost.born + partial) / FADE_IN, 0.0F, 1.0F)
				* (age <= STALE_TICKS ? 1.0F : Mth.clamp(1.0F - (age - STALE_TICKS) / (float) (DROP_TICKS - STALE_TICKS), 0.0F, 1.0F))
				* (1.0F - 0.6F * rain);
		if (fade <= 0.01F) {
			return;
		}
		int n = EffectQuality.minimal() ? POINTS / 2 : POINTS;
		int quads = (n - 1) * 2 + 1 + 2;
		if (!NagaVisuals.takeQuads(quads)) {
			return;
		}
		float time = (ticks + partial);
		boolean lemah = (ghost.flags & NagaLiarEntity.FLAG_LEMAH) != 0;
		float sway = lemah ? 0.35F : 1.0F;
		// far away the body reads thicker (the eye's minimum), up to 1.7× at 600 blocks
		float thicken = 1.0F + Mth.clamp((float) (distance - 160.0D) / 600.0F, 0.0F, 0.7F);
		// ---- the body points: the chain, a lateral undulation on top, projected onto the sky sphere
		float visible = 0.0F;
		for (int i = 0; i < n; i++) {
			int index = n <= 1 ? 0 : Math.round(i * (POINTS - 1) / (float) (n - 1));
			SegmentChain.Pose pose = ghost.chain.pose(index, partial);
			double yaw = pose.yaw() * Mth.DEG_TO_RAD;
			double wave = Mth.sin(time * 0.18F - index * 0.55F) * 1.1F * sway * (0.3F + index / (float) POINTS);
			// sideways of the body (horizontal), so the snake shape shows from below too
			double sx = -Math.cos(yaw);
			double sz = -Math.sin(yaw);
			double wx = pose.pos().x + sx * wave - cam.x;
			double wy = pose.pos().y + Mth.sin(time * 0.11F - index * 0.4F) * 0.35F * sway - cam.y;
			double wz = pose.pos().z + sz * wave - cam.z;
			float length = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
			PA[i] = SkyProjection.project((float) wx, (float) wy, (float) wz, radius, OUT);
			PX[i] = OUT[0];
			PY[i] = OUT[1];
			PZ[i] = OUT[2];
			PK[i] = radius / Math.max(1.0F, length);
			visible = Math.max(visible, PA[i]);
		}
		if (visible <= 0.01F) {
			return;
		}
		// the snout: one point ahead of the head along the first segment
		float fx = PX[0] - PX[1];
		float fy = PY[0] - PY[1];
		float fz = PZ[0] - PZ[1];
		PX[n] = PX[0] + fx * 1.1F;
		PY[n] = PY[0] + fy * 1.1F;
		PZ[n] = PZ[0] + fz * 1.1F;
		PK[n] = PK[0];
		PA[n] = PA[0];
		int glow = ghost.kind.glow;
		float shadowAlpha = 0.8F * (1.0F - 0.55F * night) * fade;
		float coreAlpha = (0.16F + 0.55F * night) * fade;
		for (int i = 0; i + 1 < n; i++) {
			float t0 = i / (float) (n - 1);
			float t1 = (i + 1) / (float) (n - 1);
			float w0 = halfWidth(t0) * PK[i] * thicken;
			float w1 = halfWidth(t1) * PK[i + 1] * thicken;
			ribbon(SHADOW, i, i + 1, w0, w1, t0 * 3.0F, t1 * 3.0F, SHADOW_RGB, shadowAlpha);
			ribbon(CORE, i, i + 1, w0 * 0.32F, w1 * 0.32F, t0 * 3.0F, t1 * 3.0F, glow, coreAlpha);
		}
		// the head: a broad quad from the neck point to the snout
		ribbon(SHADOW, 0, n, halfWidth(0.0F) * 1.35F * PK[0] * thicken, halfWidth(0.0F) * 0.55F * PK[0] * thicken, 0.0F, 0.4F, SHADOW_RGB,
				shadowAlpha);
		// two eye glints, brighter at night and when it is angry
		boolean angry = (ghost.flags & NagaLiarEntity.FLAG_ANGRY) != 0;
		float eyeAlpha = (0.35F + 0.6F * night) * (angry ? 1.0F : 0.75F) * fade * PA[0];
		float eye = 0.5F * PK[0] * thicken;
		float ex = PX[0] + fx * 0.55F;
		float ey = PY[0] + fy * 0.55F;
		float ez = PZ[0] + fz * 0.55F;
		float[] side = side(0, n);
		float spread = halfWidth(0.0F) * 0.55F * PK[0] * thicken;
		dot(ex + side[0] * spread, ey + side[1] * spread, ez + side[2] * spread, eye, angry ? 0xFF5040 : glow, eyeAlpha);
		dot(ex - side[0] * spread, ey - side[1] * spread, ez - side[2] * spread, eye, angry ? 0xFF5040 : glow, eyeAlpha);
	}

	/** Half the body width at {@code t} (0 neck, 1 tail): the real Jiao's 2.0 → 0.85 blocks, the tail tapering to a point. */
	private static float halfWidth(float t) {
		float w = Mth.lerp(t, 1.05F, 0.45F);
		if (t > 0.85F) {
			w *= 1.0F - (t - 0.85F) / 0.15F * 0.8F;
		}
		return w;
	}

	/** The unit vector across the ribbon between points a and b: perpendicular to the segment and to the view ray. */
	private static float[] side(int a, int b) {
		float tx = PX[b] - PX[a];
		float ty = PY[b] - PY[a];
		float tz = PZ[b] - PZ[a];
		float vx = (PX[a] + PX[b]) * 0.5F;
		float vy = (PY[a] + PY[b]) * 0.5F;
		float vz = (PZ[a] + PZ[b]) * 0.5F;
		float cx = ty * vz - tz * vy;
		float cy = tz * vx - tx * vz;
		float cz = tx * vy - ty * vx;
		float length = Mth.sqrt(cx * cx + cy * cy + cz * cz);
		if (length < 1.0E-5F) {
			return new float[]{0.0F, 1.0F, 0.0F};
		}
		return new float[]{cx / length, cy / length, cz / length};
	}

	private static void ribbon(Sink sink, int a, int b, float wa, float wb, float ua, float ub, int rgb, float alpha) {
		float[] s = side(a, b);
		int ca = color(alpha * PA[a], rgb);
		int cb = color(alpha * PA[b], rgb);
		if ((ca >>> 24) == 0 && (cb >>> 24) == 0) {
			return;
		}
		sink.quad(PX[a] - s[0] * wa, PY[a] - s[1] * wa, PZ[a] - s[2] * wa,
				PX[a] + s[0] * wa, PY[a] + s[1] * wa, PZ[a] + s[2] * wa,
				PX[b] + s[0] * wb, PY[b] + s[1] * wb, PZ[b] + s[2] * wb,
				PX[b] - s[0] * wb, PY[b] - s[1] * wb, PZ[b] - s[2] * wb,
				ua, 0.0F, ub, 1.0F, ca, cb);
	}

	/** A round glint facing the camera (the camera sits at the origin of the camera-relative frame). */
	private static void dot(float x, float y, float z, float half, int rgb, float alpha) {
		int c = color(alpha, rgb);
		if ((c >>> 24) == 0) {
			return;
		}
		float length = Mth.sqrt(x * x + y * y + z * z);
		if (length < 1.0E-4F) {
			return;
		}
		float nx = x / length;
		float ny = y / length;
		float nz = z / length;
		// any vector not parallel to the view ray, then the basis across it
		float ux = Math.abs(ny) > 0.9F ? 1.0F : 0.0F;
		float uy = Math.abs(ny) > 0.9F ? 0.0F : 1.0F;
		float rx = uy * nz;
		float ry = -ux * nz;
		float rz = ux * ny - uy * nx;
		float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
		rx /= rl;
		ry /= rl;
		rz /= rl;
		float qx = ny * rz - nz * ry;
		float qy = nz * rx - nx * rz;
		float qz = nx * ry - ny * rx;
		EYES.quad(x - rx * half - qx * half, y - ry * half - qy * half, z - rz * half - qz * half,
				x + rx * half - qx * half, y + ry * half - qy * half, z + rz * half - qz * half,
				x + rx * half + qx * half, y + ry * half + qy * half, z + rz * half + qz * half,
				x - rx * half + qx * half, y - ry * half + qy * half, z - rz * half + qz * half,
				0.0F, 0.0F, 1.0F, 1.0F, c, c);
	}

	private static int color(float alpha, int rgb) {
		int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
		return a << 24 | rgb & 0xFFFFFF;
	}

	/** Camera-relative quads of one render type for one frame, submitted once through a reused writer. */
	private static final class Sink {
		private static final int STRIDE = 5;
		private final RenderType type;
		private float[] data = new float[STRIDE * 4 * 64];
		private int[] colors = new int[4 * 64];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type) {
			this.type = type;
		}

		void reset() {
			count = 0;
		}

		private void vertex(float x, float y, float z, float u, float v, int argb) {
			if (count >= colors.length) {
				int size = colors.length * 2;
				float[] more = new float[size * STRIDE];
				System.arraycopy(data, 0, more, 0, data.length);
				data = more;
				int[] moreColors = new int[size];
				System.arraycopy(colors, 0, moreColors, 0, colors.length);
				colors = moreColors;
			}
			int o = count * STRIDE;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = u;
			data[o + 4] = v;
			colors[count] = argb;
			count++;
		}

		void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
				float u0, float v0, float u1, float v1, int ca, int cb) {
			vertex(x0, y0, z0, u0, v0, ca);
			vertex(x1, y1, z1, u0, v1, ca);
			vertex(x2, y2, z2, u1, v1, cb);
			vertex(x3, y3, z3, u1, v0, cb);
		}

		void submit(SubmitNodeCollector collector, PoseStack poseStack) {
			if (count >= 4) {
				collector.submitCustomGeometry(poseStack, type, writer);
			}
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int vertices = count / 4 * 4;
			for (int i = 0; i < vertices; i++) {
				int o = i * STRIDE;
				buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
						.setColor(colors[i])
						.setUv(data[o + 3], data[o + 4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, 0.0F, 1.0F, 0.0F);
			}
		}
	}

	private NagaFar() {
	}
}
