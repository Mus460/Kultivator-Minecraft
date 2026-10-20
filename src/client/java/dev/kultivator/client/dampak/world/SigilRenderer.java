package dev.kultivator.client.dampak.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * All Phase 10 world quads (DESIGN 28.1.4, 28.5, 28.8.3, 28.9.2): ground sigils, the light grid, shock rings, molten
 * decals and the star-impact pillars, drawn from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} (registered after
 * {@code EpicRenderer}), never in Iris's shadow pass. Quads take from the shared 7b frame budget
 * ({@link EpicBudget#takeQuads}); when no 7b scene draws this frame the budget's frame is started here. Ground quads sit
 * 0.04 b above the client heightmap (no polygon offset). Only vanilla render types: {@code entityTranslucentEmissive}
 * for soft layers and decals, {@code lightning()} for thin glow lines, the beacon beam for pillars. Idle cost: one
 * boolean.
 */
public final class SigilRenderer {

	public static final Identifier GROUND_GLOW = Kultivator.id("textures/misc/dampak/ground_glow.png");
	public static final Identifier CRACK_DECAL = Kultivator.id("textures/misc/dampak/crack_decal.png");
	/** Ground quads sit this far above the surface. */
	public static final float LIFT = 0.04F;

	private static final Frame FRAME = new Frame();
	private static int lastQuads;

	/** The {@code COLLECT_SUBMITS} listener. */
	static void collect(LevelRenderContext context) {
		if (!DampakWorld.anyGeometry()) {
			return;
		}
		if (ShaderCompat.shadowPass()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		long start = System.nanoTime();
		try {
			if (!EpicDirector.anyGeometry()) {
				// EpicRenderer did not start a 7b frame: start the shared counters here
				EpicBudget.beginFrame();
			}
			CameraRenderState camera = context.levelState().cameraRenderState;
			FRAME.setup(level, camera, context.levelState().worldPartialTicks, context.poseStack(), context.submitNodeCollector());
			if (DampakWorldBudget.geometry()) {
				MoltenGlow.render(FRAME);
				TerrainLift.render(FRAME);
				GroundSigil.render(FRAME);
				ShockRing.render(FRAME);
			}
			lastQuads = FRAME.quads();
			FRAME.flush();
		} catch (RuntimeException exception) {
			DampakWorld.warn("render", exception);
			FRAME.clear();
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	/** Quads written in the last frame (stats). */
	static int lastQuads() {
		return lastQuads;
	}

	/** The per-frame drawing state. Positions are camera-relative floats. */
	static final class Frame {
		static final RenderType GROUND_TYPE = RenderTypes.entityTranslucentEmissive(GROUND_GLOW);
		static final RenderType CRACK_TYPE = RenderTypes.entityTranslucentEmissive(CRACK_DECAL);

		ClientLevel level;
		double camX;
		double camY;
		double camZ;
		float partial;
		/** Presentation time (ticks, σ-scaled) of this frame. */
		double clock;
		PoseStack poseStack;
		SubmitNodeCollector collector;
		final Vector3f right = new Vector3f();
		final Vector3f up = new Vector3f();
		final Vector3f forward = new Vector3f();

		final Sink ground = new Sink(GROUND_TYPE, true);
		final Sink crack = new Sink(CRACK_TYPE, true);
		final Sink ring = new Sink(EpicRenderTypes.RING, true);
		final Sink rune = new Sink(EpicRenderTypes.RUNE, true);
		final Sink glow = new Sink(EpicRenderTypes.GLOW, true);
		final Sink lightning = new Sink(EpicRenderTypes.LIGHTNING, false);

		private int pillarCount;
		private final double[] pillarPos = new double[16 * 3];
		private final int[] pillarHeight = new int[16];
		private final int[] pillarColor = new int[16];
		private final float[] pillarGlow = new float[16];

		void setup(ClientLevel level, CameraRenderState camera, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
			this.level = level;
			Vec3 pos = camera.pos;
			camX = pos.x;
			camY = pos.y;
			camZ = pos.z;
			partial = partialTicks;
			clock = DampakWorld.clock(partialTicks);
			this.poseStack = poseStack;
			this.collector = collector;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
			camera.orientation.transform(0.0F, 0.0F, -1.0F, forward);
			clear();
		}

		void clear() {
			ground.reset();
			crack.reset();
			ring.reset();
			rune.reset();
			glow.reset();
			lightning.reset();
			pillarCount = 0;
		}

		int quads() {
			return (ground.vertices() + crack.vertices() + ring.vertices() + rune.vertices() + glow.vertices() + lightning.vertices()) / 4;
		}

		float rx(double x) {
			return (float) (x - camX);
		}

		float ry(double y) {
			return (float) (y - camY);
		}

		float rz(double z) {
			return (float) (z - camZ);
		}

		double dist2(double x, double y, double z) {
			double dx = x - camX;
			double dy = y - camY;
			double dz = z - camZ;
			return dx * dx + dy * dy + dz * dz;
		}

		/** The surface height (world y) of a column plus {@link #LIFT}, or {@code fallback}. */
		double groundY(double x, double z, double fallback) {
			return DampakDisplays.ground(level, x, z, fallback) + LIFT;
		}

		/**
		 * A flat strip on the ground between two camera-relative points: a horizontal quad {@code width} wide with
		 * colours at each end; v runs across the strip (the glow-strip texture's bright centre line).
		 */
		void groundStrip(Sink sink, float ax, float ay, float az, float bx, float by, float bz, float width, int ca, int cb, float u0, float u1) {
			float dx = bx - ax;
			float dz = bz - az;
			float length = (float) Math.sqrt(dx * dx + dz * dz);
			float sx;
			float sz;
			if (length < 1.0E-4F) {
				sx = 1.0F;
				sz = 0.0F;
			} else {
				sx = -dz / length;
				sz = dx / length;
			}
			float h = width * 0.5F;
			sink.vertex(ax - sx * h, ay, az - sz * h, u0, 0.0F, ca, 0.0F, 1.0F, 0.0F);
			sink.vertex(ax + sx * h, ay, az + sz * h, u0, 1.0F, ca, 0.0F, 1.0F, 0.0F);
			sink.vertex(bx + sx * h, by, bz + sz * h, u1, 1.0F, cb, 0.0F, 1.0F, 0.0F);
			sink.vertex(bx - sx * h, by, bz - sz * h, u1, 0.0F, cb, 0.0F, 1.0F, 0.0F);
		}

		/** A horizontal textured square of half size {@code half}, turned by {@code angle} radians, normal up. */
		void flat(Sink sink, float x, float y, float z, float half, float angle, int color) {
			float c = (float) Math.cos(angle) * half;
			float s = (float) Math.sin(angle) * half;
			sink.vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}

		/** A horizontal axis-aligned quad with its own uv window (decal cells). */
		void cell(Sink sink, float x0, float z0, float x1, float z1, float y, float u0, float v0, float u1, float v1, int color) {
			sink.vertex(x0, y, z0, u0, v0, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x0, y, z1, u0, v1, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x1, y, z1, u1, v1, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x1, y, z0, u1, v0, color, 0.0F, 1.0F, 0.0F);
		}

		/** A camera-facing square of half size {@code half} (node glows). */
		void billboard(Sink sink, float x, float y, float z, float half, int color) {
			float rxh = right.x * half;
			float ryh = right.y * half;
			float rzh = right.z * half;
			float uxh = up.x * half;
			float uyh = up.y * half;
			float uzh = up.z * half;
			sink.vertex(x - rxh - uxh, y - ryh - uyh, z - rzh - uzh, 0.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x + rxh - uxh, y + ryh - uyh, z + rzh - uzh, 1.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x + rxh + uxh, y + ryh + uyh, z + rzh + uzh, 1.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x - rxh + uxh, y - ryh + uyh, z - rzh + uzh, 0.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
		}

		/** Queues a beacon-beam pillar (world position; drawn after the soft layers). */
		boolean pillar(double x, double y, double z, int height, int rgb, float glowRadius) {
			if (pillarCount >= pillarHeight.length || height <= 0 || !EpicBudget.takePillar()) {
				return false;
			}
			int i = pillarCount++;
			pillarPos[i * 3] = x;
			pillarPos[i * 3 + 1] = y;
			pillarPos[i * 3 + 2] = z;
			pillarHeight[i] = height;
			pillarColor[i] = 0xFF000000 | (rgb & 0xFFFFFF);
			pillarGlow[i] = glowRadius;
			return true;
		}

		/** Submits every sink once: decals, soft layers, pillars, then the additive thin lines last. */
		void flush() {
			ground.submit(collector, poseStack);
			crack.submit(collector, poseStack);
			ring.submit(collector, poseStack);
			rune.submit(collector, poseStack);
			glow.submit(collector, poseStack);
			for (int i = 0; i < pillarCount; i++) {
				poseStack.pushPose();
				double x = pillarPos[i * 3] - camX;
				double y = pillarPos[i * 3 + 1] - camY;
				double z = pillarPos[i * 3 + 2] - camZ;
				// submitBeaconBeam adds translate(0.5, 0, 0.5) itself
				poseStack.translate(x - 0.5D, y, z - 0.5D);
				BeaconRenderer.submitBeaconBeam(poseStack, collector, EpicRenderTypes.BEACON_BEAM, 1.0F, (float) clock, 0, pillarHeight[i],
						pillarColor[i], 0.05F, Math.max(0.06F, pillarGlow[i]));
				poseStack.popPose();
			}
			lightning.submit(collector.order(1), poseStack);
		}
	}

	/** Vertices of one render type for this frame, submitted once through a reused writer. */
	static final class Sink {
		private static final int STRIDE = 8;
		private final RenderType type;
		private final boolean entity;
		private float[] data = new float[STRIDE * 512];
		private int[] colors = new int[512];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type, boolean entity) {
			this.type = type;
			this.entity = entity;
		}

		void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
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
			data[o + 5] = nx;
			data[o + 6] = ny;
			data[o + 7] = nz;
			colors[count] = argb;
			count++;
		}

		int vertices() {
			return count;
		}

		void reset() {
			count = 0;
		}

		void submit(OrderedSubmitNodeCollector collector, PoseStack poseStack) {
			if (count < 4) {
				return;
			}
			collector.submitCustomGeometry(poseStack, type, writer);
		}

		private void write(PoseStack.Pose pose, VertexConsumer buffer) {
			int quads = count / 4 * 4;
			for (int i = 0; i < quads; i++) {
				int o = i * STRIDE;
				if (entity) {
					buffer.addVertex(pose, data[o], data[o + 1], data[o + 2])
							.setColor(colors[i])
							.setUv(data[o + 3], data[o + 4])
							.setOverlay(OverlayTexture.NO_OVERLAY)
							.setLight(LightCoordsUtil.FULL_BRIGHT)
							.setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
				} else {
					buffer.addVertex(pose, data[o], data[o + 1], data[o + 2]).setColor(colors[i]);
				}
			}
		}
	}

	private SigilRenderer() {
	}
}
