package dev.kultivator.client.sekte.tangga;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.sekte.tangga.JalurTangga;
import dev.kultivator.sekte.tangga.TanggaPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The illusory Thousand-Step Stair (DESIGN 36.2.4), drawn from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} with
 * vanilla render types only ({@code entityTranslucentEmissive}: two-sided, no depth write, full bright), never in Iris's
 * shadow pass, never a post chain: the "jade stair of light" is layered translucent quads with glow textures.
 * <ul>
 * <li>The treads and risers of {@link JalurTangga}'s path: 120 / 400 / 1000 drawn steps for EffectQuality Minimal /
 * Normal / Maksimal (or the server's forced {@code lod}); halved past 64 blocks and again past 112; nothing past 160.
 * A wave of light runs up the steps; the colour turns from jade to gold toward the summit; while forming the stair
 * unfurls from the projector with a bright growing tip.</li>
 * <li>Glowing side rails (Normal and up), mist puffs under the stair (24 Normal, 48 Maksimal, none Minimal), the
 * projector's turning trigram ring, the gate of light at the summit (a camera-facing card), a golden beacon pillar while
 * someone stands on the peak.</li>
 * <li>The heart demon's face for two seconds in front of a climber kneeling under an illusion (steps 300/600/900).</li>
 * <li>The step number over each climber's head (at most six, within 48 blocks), with the result after the reading.</li>
 * </ul>
 * Every quad is taken from the shared 7b frame budget ({@code EpicBudget.takeQuads}); when the stair does not fit it
 * halves its steps until it does, then draws only the ring. Idle cost: one boolean.
 */
final class TanggaIlusiRenderer {

	static final Identifier STEP = Kultivator.id("textures/sekte/tangga/step.png");
	static final Identifier RISER = Kultivator.id("textures/sekte/tangga/riser.png");
	static final Identifier MIST = Kultivator.id("textures/sekte/tangga/mist.png");
	static final Identifier PEAK = Kultivator.id("textures/sekte/tangga/peak.png");
	static final Identifier FACE = Kultivator.id("textures/sekte/tangga/face.png");
	static final Identifier RING = Kultivator.id("textures/sekte/tangga/ring.png");

	static final int JADE = 0x5FE3C0;
	static final int JADE_DEEP = 0x2FB89A;
	static final int GOLD = 0xFFD27A;
	static final int DEMON = 0xB060FF;
	static final double DRAW_RANGE = 160.0D;
	static final double LABEL_RANGE = 48.0D;
	private static final int[] LOD_STEPS = {120, 400, 1000};
	private static final int[] MIST_PUFFS = {0, 24, 48};

	private static final RenderType STEP_TYPE = RenderTypes.entityTranslucentEmissive(STEP);
	private static final RenderType RISER_TYPE = RenderTypes.entityTranslucentEmissive(RISER);
	private static final RenderType MIST_TYPE = RenderTypes.entityTranslucentEmissive(MIST);
	private static final RenderType PEAK_TYPE = RenderTypes.entityTranslucentEmissive(PEAK);
	private static final RenderType FACE_TYPE = RenderTypes.entityTranslucentEmissive(FACE);
	private static final RenderType RING_TYPE = RenderTypes.entityTranslucentEmissive(RING);

	private static final TanggaSink TREADS = new TanggaSink(STEP_TYPE, true);
	private static final TanggaSink RISERS = new TanggaSink(RISER_TYPE, true);
	private static final TanggaSink MISTS = new TanggaSink(MIST_TYPE, true);
	private static final TanggaSink GATES = new TanggaSink(PEAK_TYPE, true);
	private static final TanggaSink FACES = new TanggaSink(FACE_TYPE, true);
	private static final TanggaSink RINGS = new TanggaSink(RING_TYPE, true);
	private static final TanggaSink RAILS = new TanggaSink(EpicRenderTypes.GLOW, true);

	private static double camX;
	private static double camY;
	private static double camZ;
	private static final Vector3f RIGHT = new Vector3f();
	private static final Vector3f UP = new Vector3f();
	private static final Vector3f FORWARD = new Vector3f();
	private static final double[] P = new double[3];
	private static final double[] Q = new double[3];
	private static final double[] H = new double[2];
	private static int pillars;
	private static final double[] PILLAR_AT = new double[3 * 4];

	/** The {@code COLLECT_SUBMITS} listener. */
	static void collect(LevelRenderContext context) {
		if (!TanggaIlusi.any() || ShaderCompat.shadowPass()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		try {
			if (!EpicDirector.anyGeometry()) {
				EpicBudget.beginFrame();
			}
			CameraRenderState camera = context.levelState().cameraRenderState;
			Vec3 pos = camera.pos;
			camX = pos.x;
			camY = pos.y;
			camZ = pos.z;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, RIGHT);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, UP);
			camera.orientation.transform(0.0F, 0.0F, -1.0F, FORWARD);
			float partial = context.levelState().worldPartialTicks;
			double now = TanggaIlusi.clock() + partial;
			resetSinks();
			pillars = 0;
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (TanggaIlusi.Stair stair : TanggaIlusi.stairs()) {
				if (stair.frame != null) {
					draw(stair, now, level, partial, poseStack, collector, camera);
				}
			}
			MISTS.submit(collector, poseStack);
			RINGS.submit(collector, poseStack);
			RISERS.submit(collector, poseStack);
			TREADS.submit(collector, poseStack);
			RAILS.submit(collector, poseStack);
			GATES.submit(collector, poseStack);
			FACES.submit(collector, poseStack);
			for (int i = 0; i < pillars; i++) {
				poseStack.pushPose();
				// submitBeaconBeam adds translate(0.5, 0, 0.5) itself
				poseStack.translate(PILLAR_AT[i * 3] - camX - 0.5D, PILLAR_AT[i * 3 + 1] - camY, PILLAR_AT[i * 3 + 2] - camZ - 0.5D);
				BeaconRenderer.submitBeaconBeam(poseStack, collector, EpicRenderTypes.BEACON_BEAM, 1.0F, (float) now, 0, 48, 0xFF000000 | GOLD, 0.06F,
						0.22F);
				poseStack.popPose();
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Tangga stair render failed", exception);
			resetSinks();
		}
	}

	private static void resetSinks() {
		TREADS.reset();
		RISERS.reset();
		MISTS.reset();
		GATES.reset();
		FACES.reset();
		RINGS.reset();
		RAILS.reset();
	}

	private static void draw(TanggaIlusi.Stair stair, double now, ClientLevel level, float partial, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		JalurTangga.Frame f = stair.frame;
		float vis = smooth(stair.visible);
		if (vis <= 0.01F) {
			return;
		}
		double dx = camX - f.cx();
		double dy = camY - (f.oy() + JalurTangga.HEIGHT * 0.5D);
		double dz = camZ - f.cz();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist > DRAW_RANGE) {
			return;
		}
		int quality = stair.lod >= 0 ? Math.min(2, stair.lod) : EffectQuality.level().ordinal();
		int steps = LOD_STEPS[Math.max(0, Math.min(2, quality))];
		if (dist > 64.0D) {
			steps = Math.max(60, steps / 2);
		}
		if (dist > 112.0D) {
			steps = Math.max(60, steps / 2);
		}
		boolean rails = quality >= 1;
		int mist = dist > 96.0D ? MIST_PUFFS[quality] / 2 : MIST_PUFFS[quality];
		int extras = 12 + stair.climbers.size() * 2;
		while (!EpicBudget.takeQuads(steps * 2 + (rails ? steps / 2 : 0) + mist + extras)) {
			if (steps <= 60) {
				// Nothing else fits this frame: the ring alone marks where the stair stands.
				if (EpicBudget.takeQuads(1)) {
					ring(f, now, vis);
				}
				return;
			}
			steps /= 2;
			mist /= 2;
		}
		ring(f, now, vis);
		float reach = stair.reach();
		boolean forming = stair.phase == TanggaPayloads.PHASE_FORMING && reach < JalurTangga.STEPS;
		double wave = forming ? reach : now * 9.0D % 1300.0D - 150.0D;
		float half = (float) (JalurTangga.WIDTH * 0.5D);
		double stepLen = (double) JalurTangga.STEPS / steps;
		for (int i = 0; i < steps; i++) {
			double s0 = i * stepLen;
			if (s0 >= reach) {
				break;
			}
			double s1 = Math.min(reach, (i + 1) * stepLen);
			float y0 = (float) (f.oy() + JalurTangga.rise(s0) - camY);
			float y1 = (float) (f.oy() + JalurTangga.rise(s1) - camY);
			JalurTangga.point(f, s0, -half, P);
			float ix0 = (float) (P[0] - camX);
			float iz0 = (float) (P[2] - camZ);
			JalurTangga.point(f, s0, half, P);
			float ox0 = (float) (P[0] - camX);
			float oz0 = (float) (P[2] - camZ);
			JalurTangga.point(f, s1, -half, P);
			float ix1 = (float) (P[0] - camX);
			float iz1 = (float) (P[2] - camZ);
			JalurTangga.point(f, s1, half, P);
			float ox1 = (float) (P[0] - camX);
			float oz1 = (float) (P[2] - camZ);
			double w = Math.exp(-Math.pow((s0 - wave) / 70.0D, 2.0D));
			int rgb = mix(JADE, GOLD, smooth((float) ((s0 / JalurTangga.STEPS - 0.72D) / 0.28D)));
			rgb = mix(rgb, 0xFFFFFF, (float) (0.45D * w));
			int treadA = alpha((0.55F + 0.35F * (float) w) * vis);
			int riserA = alpha((0.38F + 0.3F * (float) w) * vis);
			int tread = treadA << 24 | rgb;
			int riser = riserA << 24 | mix(rgb, JADE_DEEP, 0.35F);
			float v1 = (float) ((s1 - s0) / stepLen);
			TREADS.vertex(ix0, y1, iz0, 0.0F, 0.0F, tread, 0.0F, 1.0F, 0.0F);
			TREADS.vertex(ox0, y1, oz0, 1.0F, 0.0F, tread, 0.0F, 1.0F, 0.0F);
			TREADS.vertex(ox1, y1, oz1, 1.0F, v1, tread, 0.0F, 1.0F, 0.0F);
			TREADS.vertex(ix1, y1, iz1, 0.0F, v1, tread, 0.0F, 1.0F, 0.0F);
			JalurTangga.heading(f, s0, H);
			float nx = (float) -H[0];
			float nz = (float) -H[1];
			RISERS.vertex(ix0, y0, iz0, 0.0F, 1.0F, riser, nx, 0.0F, nz);
			RISERS.vertex(ox0, y0, oz0, 1.0F, 1.0F, riser, nx, 0.0F, nz);
			RISERS.vertex(ox0, y1, oz0, 1.0F, 0.0F, riser, nx, 0.0F, nz);
			RISERS.vertex(ix0, y1, iz0, 0.0F, 0.0F, riser, nx, 0.0F, nz);
		}
		if (rails) {
			rails(f, reach, steps, vis, wave);
		}
		if (mist > 0) {
			mist(f, reach, mist, vis, now);
		}
		if (forming) {
			// the growing tip of the projection
			JalurTangga.point(f, reach, 0.0D, P);
			billboard(GATES, P[0], P[1] + 0.4D, P[2], 1.1F, alpha(0.9F * vis) << 24 | 0xFFFFFF, 0.0F, 0.0F, 1.0F, 1.0F);
		}
		peak(stair, f, now, vis, reach);
		faces(stair, f, level, partial, now, vis);
		if (dist <= LABEL_RANGE + JalurTangga.RADIUS_START * 2.0D) {
			labels(stair, level, partial, now, vis, poseStack, collector, camera);
		}
	}

	/** The projector's trigram ring, turning, on the ground at the projector. */
	private static void ring(JalurTangga.Frame f, double now, float vis) {
		float x = (float) (f.ox() - camX);
		float y = (float) (f.oy() + 0.06D - camY);
		float z = (float) (f.oz() - camZ);
		float half = 2.6F;
		double angle = now * 0.02D;
		float c = (float) Math.cos(angle) * half;
		float s = (float) Math.sin(angle) * half;
		int color = alpha(0.85F * vis) << 24 | JADE;
		RINGS.vertex(x - c + s, y, z - s - c, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		RINGS.vertex(x - c - s, y, z - s + c, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		RINGS.vertex(x + c - s, y, z + s + c, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
		RINGS.vertex(x + c + s, y, z + s - c, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
	}

	/** Glowing rails along both edges, one segment per four drawn steps, camera-facing strips. */
	private static void rails(JalurTangga.Frame f, float reach, int steps, float vis, double wave) {
		double seg = 4.0D * JalurTangga.STEPS / steps;
		double half = JalurTangga.WIDTH * 0.5D;
		for (double s0 = 0.0D; s0 < reach; s0 += seg) {
			double s1 = Math.min(reach, s0 + seg);
			double w = Math.exp(-Math.pow((s0 - wave) / 70.0D, 2.0D));
			int rgb = mix(JADE, GOLD, smooth((float) ((s0 / JalurTangga.STEPS - 0.72D) / 0.28D)));
			int color = alpha((0.6F + 0.4F * (float) w) * vis) << 24 | mix(rgb, 0xFFFFFF, 0.3F);
			for (double side : new double[]{-half, half}) {
				JalurTangga.point(f, s0, side, P);
				JalurTangga.point(f, s1, side, Q);
				beam(RAILS, P[0], P[1] + 0.12D, P[2], Q[0], Q[1] + 0.12D, Q[2], 0.16F, color, color);
			}
		}
	}

	/** Mist under the stair: soft flat puffs below the treads, turning slowly. */
	private static void mist(JalurTangga.Frame f, float reach, int count, float vis, double now) {
		for (int i = 0; i < count; i++) {
			double s = (i + 0.5D) * JalurTangga.STEPS / count;
			if (s > reach) {
				break;
			}
			JalurTangga.point(f, s, ((i * 37) % 7 - 3) * 0.4D, P);
			float x = (float) (P[0] - camX);
			float y = (float) (P[1] - 1.1D - (i % 3) * 0.35D - camY);
			float z = (float) (P[2] - camZ);
			float half = 2.2F + (i % 4) * 0.45F;
			double angle = now * 0.01D + i * 1.7D;
			float c = (float) Math.cos(angle) * half;
			float sn = (float) Math.sin(angle) * half;
			int color = alpha(0.3F * vis) << 24 | 0xDDF5EE;
			MISTS.vertex(x - c + sn, y, z - sn - c, 0.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
			MISTS.vertex(x - c - sn, y, z - sn + c, 0.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			MISTS.vertex(x + c - sn, y, z + sn + c, 1.0F, 1.0F, color, 0.0F, 1.0F, 0.0F);
			MISTS.vertex(x + c + sn, y, z + sn - c, 1.0F, 0.0F, color, 0.0F, 1.0F, 0.0F);
		}
	}

	/** The gate of light at the summit, brighter (and a beacon pillar) while someone stands on it. */
	private static void peak(TanggaIlusi.Stair stair, JalurTangga.Frame f, double now, float vis, float reach) {
		if (reach < JalurTangga.STEPS * 0.98F) {
			return;
		}
		boolean summit = stair.summit();
		JalurTangga.peak(f, P);
		float pulse = (float) (0.5D + 0.5D * Math.sin(now * 0.12D));
		float a = (summit ? 0.95F : 0.55F + 0.15F * pulse) * vis;
		billboard(GATES, P[0], P[1] + 3.2D, P[2], summit ? 3.4F : 3.0F, alpha(a) << 24 | GOLD, 0.0F, 0.0F, 1.0F, 1.0F);
		if (summit && pillars < 4 && EpicBudget.takePillar()) {
			PILLAR_AT[pillars * 3] = P[0];
			PILLAR_AT[pillars * 3 + 1] = P[1];
			PILLAR_AT[pillars * 3 + 2] = P[2];
			pillars++;
		}
	}

	/** The heart demon's face before a kneeling climber (fades in and out over the kneel). */
	private static void faces(TanggaIlusi.Stair stair, JalurTangga.Frame f, ClientLevel level, float partial, double now, float vis) {
		for (TanggaIlusi.Climber c : stair.climbers.values()) {
			if (c.state != TanggaPayloads.C_KNEEL) {
				continue;
			}
			double t = (now - c.stateSince) / 40.0D;
			if (t < 0.0D || t > 1.0D) {
				continue;
			}
			Entity entity = level.getEntity(c.entityId);
			if (entity == null) {
				continue;
			}
			Vec3 at = entity.getPosition(partial);
			JalurTangga.heading(f, c.shownStep(now), H);
			float a = (float) Math.sin(Math.PI * t) * 0.85F * vis;
			float wobble = (float) Math.sin(now * 0.7D) * 0.06F;
			billboard(FACES, at.x + H[0] * 1.7D, at.y + 1.7D + wobble, at.z + H[1] * 1.7D, 1.15F + (float) t * 0.3F, alpha(a) << 24 | DEMON, 0.0F, 0.0F,
					1.0F, 1.0F);
		}
	}

	/** Step numbers over the climbers (the result after the reading). */
	private static void labels(TanggaIlusi.Stair stair, ClientLevel level, float partial, double now, float vis, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera) {
		if (vis < 0.2F || stair.has(TanggaPayloads.FLAG_PREVIEW)) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		for (TanggaIlusi.Climber c : stair.climbers.values()) {
			Entity entity = level.getEntity(c.entityId);
			if (entity == null) {
				continue;
			}
			Vec3 at = entity.getPosition(partial);
			double dx = at.x - camX;
			double dy = at.y - camY;
			double dz = at.z - camZ;
			if (dx * dx + dy * dy + dz * dz > LABEL_RANGE * LABEL_RANGE) {
				continue;
			}
			int shown = Math.round(c.shownStep(now));
			Component text;
			int color;
			if (c.limit >= 0) {
				text = Component.translatable("kultivator.sekte.tangga.label.result", c.limit, Component.translatable("kultivator.sekte.tangga.hati_short." + c.verdict));
				color = c.limit >= JalurTangga.STEPS ? GOLD : 0xC8FFF0;
			} else if (c.state == TanggaPayloads.C_PEAK) {
				text = Component.translatable("kultivator.sekte.tangga.label.peak");
				color = GOLD;
			} else if (c.state == TanggaPayloads.C_GAVE_UP) {
				text = Component.translatable("kultivator.sekte.tangga.label.gave_up", shown);
				color = 0xA8B4B0;
			} else {
				text = Component.literal(Integer.toString(shown));
				color = c.state == TanggaPayloads.C_KNEEL ? 0xD8B0FF : 0x9FF5DC;
			}
			FormattedCharSequence seq = text.getVisualOrderText();
			float width = font.width(seq);
			float scale = 0.025F;
			poseStack.pushPose();
			poseStack.translate(dx, dy + entity.getBbHeight() + 0.55D, dz);
			poseStack.rotate(camera.orientation);
			poseStack.scale(scale, -scale, scale);
			collector.submitText(poseStack, -width / 2.0F, 0.0F, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT,
					alpha(vis) << 24 | color, 0x40000000, 0);
			poseStack.popPose();
		}
	}

	// ================================================================== quad helpers

	/** A camera-facing square of half size {@code half} with its own uv window. */
	private static void billboard(TanggaSink sink, double wx, double wy, double wz, float half, int color, float u0, float v0, float u1, float v1) {
		float x = (float) (wx - camX);
		float y = (float) (wy - camY);
		float z = (float) (wz - camZ);
		float rx = RIGHT.x * half;
		float ry = RIGHT.y * half;
		float rz = RIGHT.z * half;
		float ux = UP.x * half;
		float uy = UP.y * half;
		float uz = UP.z * half;
		sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, u0, v1, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, u1, v1, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, u1, v0, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, u0, v0, color, -FORWARD.x, -FORWARD.y, -FORWARD.z);
	}

	/** A strip between two world points that turns about its axis to face the camera ({@code width} wide, v across). */
	private static void beam(TanggaSink sink, double ax, double ay, double az, double bx, double by, double bz, float width, int ca, int cb) {
		float x0 = (float) (ax - camX);
		float y0 = (float) (ay - camY);
		float z0 = (float) (az - camZ);
		float x1 = (float) (bx - camX);
		float y1 = (float) (by - camY);
		float z1 = (float) (bz - camZ);
		float dx = x1 - x0;
		float dy = y1 - y0;
		float dz = z1 - z0;
		float mx = (x0 + x1) * 0.5F;
		float my = (y0 + y1) * 0.5F;
		float mz = (z0 + z1) * 0.5F;
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (length < 1.0E-5F) {
			sx = RIGHT.x;
			sy = RIGHT.y;
			sz = RIGHT.z;
			length = 1.0F;
		}
		float h = width * 0.5F / length;
		sx *= h;
		sy *= h;
		sz *= h;
		sink.vertex(x0 - sx, y0 - sy, z0 - sz, 0.0F, 0.0F, ca, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x0 + sx, y0 + sy, z0 + sz, 0.0F, 1.0F, ca, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x1 + sx, y1 + sy, z1 + sz, 1.0F, 1.0F, cb, -FORWARD.x, -FORWARD.y, -FORWARD.z);
		sink.vertex(x1 - sx, y1 - sy, z1 - sz, 1.0F, 0.0F, cb, -FORWARD.x, -FORWARD.y, -FORWARD.z);
	}

	static int alpha(float a) {
		return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F);
	}

	static float smooth(float t) {
		float v = Math.max(0.0F, Math.min(1.0F, t));
		return v * v * (3.0F - 2.0F * v);
	}

	static int mix(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return (r << 16) | (g << 8) | bl;
	}

	private TanggaIlusiRenderer() {
	}
}
