package dev.kultivator.client.epic.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.ShaderCompat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * All 7b geometry, drawn from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} (DESIGN 23.10): never in Iris's shadow pass
 * (the event does not fire there; {@code isRenderingShadowPass} is still checked), never culled by a small hitbox. Every
 * element of every live scene that the director granted geometry writes its quads into one reused sink per render type,
 * and each non-empty sink is submitted once per frame: soft layers first, then pillars (vanilla beacon beam), then
 * figures, then bolts last. Positions are camera-relative. Idle cost: one boolean.
 */
public final class EpicRenderer {

	private static final Frame FRAME = new Frame();

	/** The {@code COLLECT_SUBMITS} listener. */
	public static void collect(LevelRenderContext context) {
		if (!EpicDirector.anyGeometry()) {
			return;
		}
		if (ShaderCompat.shadowPass()) {
			return;
		}
		long start = System.nanoTime();
		try {
			EpicBudget.beginFrame();
			CameraRenderState camera = context.levelState().cameraRenderState;
			FRAME.setup(camera, context.levelState().worldPartialTicks, context.poseStack(), context.submitNodeCollector());
			List<Scene> scenes = EpicDirector.scenesByPriority();
			for (int i = 0; i < scenes.size(); i++) {
				Scene scene = scenes.get(i);
				List<Element> elements = scene.elements();
				for (int j = 0; j < elements.size(); j++) {
					Element element = elements.get(j);
					if (element.alive() && element.geometry() && element.granted()) {
						element.render(FRAME, element.progressAge(FRAME.partial));
					}
				}
			}
			FRAME.flush();
		} catch (RuntimeException exception) {
			EpicDirector.warn("render", exception);
			FRAME.clear();
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	/** The per-frame drawing state handed to every element. */
	public static final class Frame {
		public double camX;
		public double camY;
		public double camZ;
		public float partial;
		/** {@link EpicClock} presentation time at this frame. */
		public double clock;
		public float fogStart;
		/** True-position geometry reaches this far (0.85 × fog start). */
		public float trueReach;
		/** The sky-projection radius. */
		public float drawRadius;
		public PoseStack poseStack;
		public SubmitNodeCollector collector;
		/** Camera right and up (world axes), for billboards. */
		public final Vector3f right = new Vector3f();
		public final Vector3f up = new Vector3f();
		public final Vector3f forward = new Vector3f();
		public final float[] tmp = new float[3];
		public final float[] tmp2 = new float[3];

		public final Sink lightning = new Sink(EpicRenderTypes.LIGHTNING, false);
		public final Sink glowBolt = new Sink(EpicRenderTypes.GLOW, true);
		public final Sink swirl = new Sink(EpicRenderTypes.SWIRL, true);
		public final Sink glow = new Sink(EpicRenderTypes.GLOW, true);
		public final Sink rays = new Sink(EpicRenderTypes.RAYS, true);
		public final Sink ring = new Sink(EpicRenderTypes.RING, true);
		public final Sink rune = new Sink(EpicRenderTypes.RUNE, true);

		private final List<GiantFigureRenderer> figures = new ArrayList<>();
		private int pillarCount;
		private final double[] pillarPos = new double[16 * 3];
		private final int[] pillarHeight = new int[16];
		private final int[] pillarColor = new int[16];
		private final float[] pillarGlow = new float[16];
		private final float[] pillarAnim = new float[16];

		void setup(CameraRenderState camera, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
			Vec3 pos = camera.pos;
			camX = pos.x;
			camY = pos.y;
			camZ = pos.z;
			partial = partialTicks;
			clock = EpicClock.time(partialTicks);
			float fog = camera.fogData.renderDistanceStart;
			if (!(fog > 8.0F)) {
				fog = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F * 0.9F;
			}
			fogStart = fog;
			trueReach = 0.85F * fog;
			drawRadius = SkyProjection.drawRadius(fog);
			this.poseStack = poseStack;
			this.collector = collector;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
			camera.orientation.transform(0.0F, 0.0F, -1.0F, forward);
			clear();
		}

		void clear() {
			lightning.reset();
			glowBolt.reset();
			swirl.reset();
			glow.reset();
			rays.reset();
			ring.reset();
			rune.reset();
			figures.clear();
			pillarCount = 0;
		}

		/** The sink for bolts and thin glow (the additive lightning type, or the emissive strip under a pack). */
		public Sink bolts() {
			return EpicRenderTypes.BOLT_EMISSIVE_UNDER_PACK && ShaderCompat.packOn() ? glowBolt : lightning;
		}

		/** World x/y/z to camera-relative floats. */
		public float rx(double x) {
			return (float) (x - camX);
		}

		public float ry(double y) {
			return (float) (y - camY);
		}

		public float rz(double z) {
			return (float) (z - camZ);
		}

		/** Squared distance from the camera to a world point. */
		public double dist2(double x, double y, double z) {
			double dx = x - camX;
			double dy = y - camY;
			double dz = z - camZ;
			return dx * dx + dy * dy + dz * dz;
		}

		/** Queues a beacon-beam pillar (drawn after the soft layers). {@code yFrac} smooths a descent. */
		public boolean pillar(double x, double y, double z, int height, int rgb, float glowRadius, float anim) {
			if (pillarCount >= pillarHeight.length || height <= 0) {
				return false;
			}
			int i = pillarCount++;
			pillarPos[i * 3] = x;
			pillarPos[i * 3 + 1] = y;
			pillarPos[i * 3 + 2] = z;
			pillarHeight[i] = height;
			pillarColor[i] = 0xFF000000 | (rgb & 0xFFFFFF);
			pillarGlow[i] = glowRadius;
			pillarAnim[i] = anim;
			return true;
		}

		/** Queues a giant figure (drawn after the pillars). */
		public void figure(GiantFigureRenderer figure) {
			figures.add(figure);
		}

		/** A camera-facing quad between two camera-relative points (a strip segment). */
		public void segment(Sink sink, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb, int ca, int cb,
							float u0, float u1) {
			float dx = bx - ax;
			float dy = by - ay;
			float dz = bz - az;
			// side = normalize(dir × (A − camera)); the camera is the origin here.
			float sx = dy * az - dz * ay;
			float sy = dz * ax - dx * az;
			float sz = dx * ay - dy * ax;
			float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (length < 1.0E-5F) {
				sx = right.x;
				sy = right.y;
				sz = right.z;
			} else {
				sx /= length;
				sy /= length;
				sz /= length;
			}
			float ha = wa * 0.5F;
			float hb = wb * 0.5F;
			sink.vertex(ax - sx * ha, ay - sy * ha, az - sz * ha, u0, 0.0F, ca, forward.x, forward.y, forward.z);
			sink.vertex(bx - sx * hb, by - sy * hb, bz - sz * hb, u1, 0.0F, cb, forward.x, forward.y, forward.z);
			sink.vertex(bx + sx * hb, by + sy * hb, bz + sz * hb, u1, 1.0F, cb, forward.x, forward.y, forward.z);
			sink.vertex(ax + sx * ha, ay + sy * ha, az + sz * ha, u0, 1.0F, ca, forward.x, forward.y, forward.z);
		}

		/** A camera-facing square of half size {@code half} at a camera-relative point. */
		public void billboard(Sink sink, float x, float y, float z, float half, int color) {
			float rxh = right.x * half;
			float ryh = right.y * half;
			float rzh = right.z * half;
			float uxh = up.x * half;
			float uyh = up.y * half;
			float uzh = up.z * half;
			sink.vertex(x - rxh - uxh, y - ryh - uyh, z - rzh - uzh, 0.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x + rxh - uxh, y + ryh - uyh, z + rzh - uzh, 1.0F, 1.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x + rxh + uxh, y + ryh + uyh, z + rzh + uzh, 1.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
			sink.vertex(x - rxh + uxh, y - ryh + uyh, z - rzh + uzh, 0.0F, 0.0F, color, -forward.x, -forward.y, -forward.z);
		}

		/** A horizontal textured square (rune circles), turned by {@code angle} radians, normal up. */
		public void flat(Sink sink, float x, float y, float z, float half, float angle, int color) {
			float c = (float) Math.cos(angle) * half;
			float s = (float) Math.sin(angle) * half;
			sink.vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			sink.vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}

		/** Submits every sink once, in order (soft layers, pillars, figures, bolts). */
		void flush() {
			swirl.submit(collector);
			rays.submit(collector);
			ring.submit(collector);
			rune.submit(collector);
			glow.submit(collector);
			for (int i = 0; i < pillarCount; i++) {
				poseStack.pushPose();
				double x = pillarPos[i * 3] - camX;
				double y = pillarPos[i * 3 + 1] - camY;
				double z = pillarPos[i * 3 + 2] - camZ;
				// submitBeaconBeam adds translate(0.5, 0, 0.5) itself; the int start is 0 and the fraction moves the pose.
				poseStack.translate(x - 0.5D, y, z - 0.5D);
				BeaconRenderer.submitBeaconBeam(poseStack, collector, EpicRenderTypes.BEACON_BEAM, 1.0F, pillarAnim[i], 0, pillarHeight[i],
						pillarColor[i], 0.05F, Math.max(0.06F, pillarGlow[i]));
				poseStack.popPose();
			}
			for (int i = 0; i < figures.size(); i++) {
				figures.get(i).submit(this, i);
			}
			glowBolt.submit(collector.order(1));
			lightning.submit(collector.order(1));
		}
	}

	/** Vertices of one render type for this frame; submitted once through a reused writer (no per-frame lambda). */
	public static final class Sink {
		private static final int STRIDE = 8;
		private final RenderType type;
		private final boolean entity;
		private float[] data = new float[STRIDE * 1024];
		private int[] colors = new int[1024];
		private int count;
		private final SubmitNodeCollector.CustomGeometryRenderer writer = this::write;

		Sink(RenderType type, boolean entity) {
			this.type = type;
			this.entity = entity;
		}

		public void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
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

		/** Vertices written so far (a quad is 4). */
		public int vertices() {
			return count;
		}

		void reset() {
			count = 0;
		}

		void submit(net.minecraft.client.renderer.OrderedSubmitNodeCollector collector) {
			if (count < 4) {
				return;
			}
			collector.submitCustomGeometry(FRAME.poseStack, type, writer);
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

	private EpicRenderer() {
	}
}
