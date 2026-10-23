package dev.kultivator.client.sekte.langit.gerak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.sekte.langit.gerak.FeriAwanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws {@code kultivator:feri_awan}, the Cloud Ferry (DESIGN 37.5.5): a small skiff in jade lacquer with gold gunwales,
 * an upswept bow carved with a cloud curl and a gilded prow, a cinnabar stern post flying a white silk pennant, pale deck
 * boards with two brocade benches, four cinnabar pillars under a double azure-tiled canopy with a gold ridge and curled
 * eaves, three paper lanterns, the whole sitting on a bank of cloud puffs that swirl slowly round the keel and trail
 * behind it in flight, with a faint levitation ring turning under it. Every part is geometry of the entity (atlas
 * {@code textures/sekte/langit/gerak/feri_awan.png} from {@code tools/gen_sekte_langit_gerak_tex.py}); no block exists.
 * <p>
 * Animation: a slow bob at the stops, a lean into turns and a nose-up climb, the pennant flutters harder with speed, the
 * puffs stream back while it flies. LOD and budgets (weak laptops): beyond 64 blocks only the hull, canopy and six puffs;
 * {@code EffectQuality} Minimal 8 puffs, no ring, no trail, Normal 12 puffs + 4 trail, Maximal 16 + 8. Light layers (glows,
 * ring) are layered emissive quads, skipped in a shader pack's shadow pass (Iris); no post chain. Client render thread
 * only.
 */
public class FeriAwanRenderer extends EntityRenderer<FeriAwanEntity, FeriAwanRenderer.State> {

	static final Identifier ATLAS = Kultivator.id("textures/sekte/langit/gerak/feri_awan.png");
	static final Identifier PUFF = Kultivator.id("textures/sekte/langit/gerak/awan_puff.png");
	static final Identifier GLOW = Kultivator.id("textures/sekte/langit/gerak/cahaya.png");
	static final Identifier RING = Kultivator.id("textures/sekte/langit/gerak/cincin.png");

	private static final float CELL = 0.25F;
	private static final float FAR = 64.0F;
	private static final int[] PUFFS = {8, 12, 16};
	private static final int[] TRAIL = {0, 4, 8};

	/** The ferry as one frame sees it. */
	public static class State extends EntityRenderState {
		public float yaw;
		public float roll;
		public float pitch;
		public float bob;
		public float time;
		public float speed;
		public byte phase;
		public int color = 0x3FAE84;
		public double distance;
		public int quality;
		public float camX;
		public float camY;
		public float camZ;
	}

	private final GerakQuads solid = new GerakQuads();
	private final GerakQuads lamps = new GerakQuads();
	private final GerakQuads clouds = new GerakQuads();
	private final GerakQuads glows = new GerakQuads();
	private final GerakQuads ring = new GerakQuads();
	private RenderType solidType;
	private RenderType lampType;
	private RenderType cloudType;
	private RenderType glowType;
	private RenderType ringType;

	public FeriAwanRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	private void types() {
		if (solidType == null) {
			solidType = RenderTypes.entityCutout(ATLAS);
			lampType = RenderTypes.entityCutout(ATLAS);
			cloudType = RenderTypes.entityTranslucent(PUFF);
			glowType = RenderTypes.entityTranslucentEmissive(GLOW);
			ringType = RenderTypes.entityTranslucentEmissive(RING);
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(FeriAwanEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		if (entity.distanceToSqr(camX, camY, camZ) > 192.0D * 192.0D) {
			return false;
		}
		AABB box = getBoundingBoxForCulling(entity, partialTicks);
		return box.hasNaN() || culler.isVisible(box);
	}

	@Override
	protected AABB getBoundingBoxForCulling(FeriAwanEntity entity, float partialTicks) {
		double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		// the canopy, the bow and the cloud trail reach past the 3 × 1.2 hit box
		return new AABB(x - 5.0D, y - 1.0D, z - 5.5D, x + 5.0D, y + 3.5D, z + 5.5D);
	}

	@Override
	public void extractRenderState(FeriAwanEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
		float climb = (float) (entity.getY() - entity.yOld);
		state.speed = entity.speed();
		state.phase = entity.phase();
		state.color = entity.color();
		state.time = state.ageInTicks;
		state.roll = Mth.clamp(-turn * 2.5F, -10.0F, 10.0F);
		state.pitch = Mth.clamp(-climb * 14.0F, -8.0F, 8.0F);
		float calm = state.speed > 0.05F ? 0.35F : 1.0F;
		state.bob = 0.07F * calm * Mth.sin(state.time * 0.09F) + 0.02F * Mth.sin(state.time * 0.23F);
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		state.camX = (float) (camera.x - state.x);
		state.camY = (float) (camera.y - state.y);
		state.camZ = (float) (camera.z - state.z);
		state.distance = Math.sqrt(state.camX * state.camX + state.camY * state.camY + state.camZ * state.camZ);
		state.quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		types();
		boolean shadow = ShaderCompat.shadowPass();
		boolean far = state.distance > FAR;
		int light = state.lightCoords;
		int full = LightCoordsUtil.FULL_BRIGHT;
		poseStack.pushPose();
		poseStack.translate(0.0F, state.bob, 0.0F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.rotate(Axis.XP.rotationDegrees(state.pitch));
		poseStack.rotate(Axis.ZP.rotationDegrees(state.roll));
		// the camera in the hull's frame (yaw only: pitch and roll stay small)
		double r = Math.toRadians(state.yaw);
		float cos = (float) Math.cos(r);
		float sin = (float) Math.sin(r);
		float lx = state.camX * cos + state.camZ * sin;
		float lz = -state.camX * sin + state.camZ * cos;
		float ly = state.camY - state.bob;

		hull(state, far, light);
		if (!far) {
			pennant(state, light);
		}
		solid.submit(collector, poseStack, solidType);
		lanterns(full);
		lamps.submit(collector, poseStack, lampType);
		clouds(state, far, light, lx, ly, lz);
		clouds.submit(collector, poseStack, cloudType);
		if (!shadow) {
			if (!far) {
				lanternGlows(state, full, lx, ly, lz);
				glows.submit(collector, poseStack, glowType);
			}
			if (!far && state.quality > 0) {
				int alpha = (int) (110 + 50 * Mth.sin(state.time * 0.07F));
				ring.flatTurned(0.0F, -0.08F, 0.0F, 1.9F, state.time * 0.02F, alpha << 24 | 0xCFE8FF, full);
				ring.flatTurned(0.0F, -0.1F, 0.0F, 1.2F, -state.time * 0.035F, (alpha / 2) << 24 | 0xE6F2FF, full);
				ring.submit(collector, poseStack, ringType);
			}
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== the hull

	/** Atlas cell (cx, cy) → its uv rectangle. */
	private void box(float x0, float y0, float z0, float x1, float y1, float z1, int cx, int cy, int light) {
		solid.box(x0, y0, z0, x1, y1, z1, cx * CELL, cy * CELL, (cx + 1) * CELL, (cy + 1) * CELL, 0xFFFFFFFF, light);
	}

	private void hull(State state, boolean far, int light) {
		// keel and bottom (dark wood)
		box(-0.7F, 0.05F, -1.6F, 0.7F, 0.3F, 1.6F, 3, 1, light);
		box(-1.05F, 0.25F, -1.85F, 1.05F, 0.5F, 1.85F, 3, 1, light);
		// jade sides with the gold rim, the deck
		box(-1.25F, 0.35F, -1.85F, -1.05F, 0.9F, 1.85F, 0, 0, light);
		box(1.05F, 0.35F, -1.85F, 1.25F, 0.9F, 1.85F, 0, 0, light);
		box(-1.05F, 0.5F, -1.85F, 1.05F, 0.6F, 1.85F, 1, 0, light);
		// bow: carved cloud-curl panel, the upswept prow and its gilded tip
		box(-1.05F, 0.35F, 1.85F, 1.05F, 0.95F, 2.15F, 0, 2, light);
		box(-0.55F, 0.75F, 2.15F, 0.55F, 1.15F, 2.4F, 0, 2, light);
		box(-0.2F, 1.05F, 2.35F, 0.2F, 1.55F, 2.6F, 2, 0, light);
		// stern: panel, cinnabar post with a gold cap
		box(-1.05F, 0.35F, -2.15F, 1.05F, 1.0F, -1.85F, 0, 2, light);
		box(-0.25F, 0.9F, -2.35F, 0.25F, 1.9F, -2.05F, 0, 1, light);
		box(-0.32F, 1.9F, -2.42F, 0.32F, 2.0F, -1.98F, 2, 0, light);
		// canopy roof (always; it is the silhouette)
		box(-1.3F, 2.25F, -1.6F, 1.3F, 2.38F, 1.6F, 3, 0, light);
		box(-0.9F, 2.38F, -1.25F, 0.9F, 2.52F, 1.25F, 3, 0, light);
		box(-0.08F, 2.52F, -1.4F, 0.08F, 2.68F, 1.4F, 2, 0, light);
		// pillars
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				float x = sx * 0.98F;
				float z = sz * 1.3F;
				box(x - 0.07F, 0.6F, z - 0.07F, x + 0.07F, 2.25F, z + 0.07F, 0, 1, light);
			}
		}
		if (far) {
			return;
		}
		// gold gunwales
		box(-1.3F, 0.9F, -1.9F, -1.0F, 0.98F, 1.9F, 2, 0, light);
		box(1.0F, 0.9F, -1.9F, 1.3F, 0.98F, 1.9F, 2, 0, light);
		box(-1.05F, 0.95F, 2.1F, 1.05F, 1.02F, 2.2F, 2, 0, light);
		// brocade benches along both sides
		box(-0.95F, 0.6F, -1.05F, -0.3F, 0.72F, 1.15F, 1, 2, light);
		box(0.3F, 0.6F, -1.05F, 0.95F, 0.72F, 1.15F, 1, 2, light);
		// curled eaves at the four corners and the ridge ends
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				float x = sx * 1.3F;
				float z = sz * 1.6F;
				box(x - 0.09F, 2.3F, z - 0.09F, x + 0.09F, 2.58F, z + 0.09F, 2, 0, light);
				box(x - 0.06F + sx * 0.06F, 2.52F, z - 0.06F + sz * 0.06F, x + 0.06F + sx * 0.06F, 2.66F, z + 0.06F + sz * 0.06F, 2, 0, light);
			}
		}
		box(-0.12F, 2.6F, 1.38F, 0.12F, 2.82F, 1.52F, 2, 0, light);
		box(-0.12F, 2.6F, -1.52F, 0.12F, 2.82F, -1.38F, 2, 0, light);
		// lantern cords and the stern arm
		box(-1.0F, 2.02F, 1.29F, -0.96F, 2.25F, 1.33F, 2, 0, light);
		box(0.96F, 2.02F, 1.29F, 1.0F, 2.25F, 1.33F, 2, 0, light);
		box(-0.03F, 1.78F, -2.05F, 0.03F, 1.84F, -1.6F, 2, 0, light);
	}

	/** The paper lanterns (full bright atlas cells). */
	private void lanterns(int full) {
		float[][] at = {{-0.98F, 1.72F, 1.31F}, {0.98F, 1.72F, 1.31F}, {0.0F, 1.46F, -1.62F}};
		for (float[] p : at) {
			lamps.box(p[0] - 0.13F, p[1], p[2] - 0.13F, p[0] + 0.13F, p[1] + 0.32F, p[2] + 0.13F, CELL, CELL, 2 * CELL, 2 * CELL, 0xFFFFFFFF, full);
		}
	}

	private void lanternGlows(State state, int full, float lx, float ly, float lz) {
		float flicker = 0.85F + 0.15F * Mth.sin(state.time * 0.6F) * Mth.sin(state.time * 0.23F + 1.3F);
		int alpha = (int) (170 * flicker);
		int color = alpha << 24 | 0xFFD89A;
		glows.billboard(-0.98F, 1.88F, 1.31F, 0.5F, color, full, lx, ly, lz);
		glows.billboard(0.98F, 1.88F, 1.31F, 0.5F, color, full, lx, ly, lz);
		glows.billboard(0.0F, 1.62F, -1.62F, 0.45F, color, full, lx, ly, lz);
	}

	/** The silk pennant from the stern post: six segments, fluttering harder with speed. */
	private void pennant(State state, int light) {
		int segments = 6;
		float length = 1.5F;
		float height = 0.32F;
		float top = 1.95F;
		float u0 = 2 * CELL;
		float u1 = 3 * CELL;
		float v0 = CELL;
		float v1 = 2 * CELL;
		float speed = Math.min(1.0F, state.speed / 0.6F);
		float freq = 0.25F + 0.35F * speed;
		float amp = 0.18F - 0.08F * speed;
		float sag = 0.25F * (1.0F - speed);
		float px = 0.0F;
		float pz = -2.2F;
		float prevX = px;
		float prevZ = pz;
		float prevY = top;
		for (int i = 1; i <= segments; i++) {
			float t = i / (float) segments;
			float x = px + amp * t * Mth.sin(state.time * freq - t * 4.0F);
			float z = pz - length * t;
			float y = top - sag * t * t;
			float ua = Mth.lerp((i - 1) / (float) segments, u0, u1);
			float ub = Mth.lerp(t, u0, u1);
			float h = height * (1.0F - 0.45F * t);
			float hp = height * (1.0F - 0.45F * (i - 1) / (float) segments);
			solid.vertex(prevX, prevY, prevZ, ua, v0, 0xFFFFFFFF, light, 1.0F, 0.0F, 0.0F);
			solid.vertex(prevX, prevY - hp, prevZ, ua, v1, 0xFFFFFFFF, light, 1.0F, 0.0F, 0.0F);
			solid.vertex(x, y - h, z, ub, v1, 0xFFFFFFFF, light, 1.0F, 0.0F, 0.0F);
			solid.vertex(x, y, z, ub, v0, 0xFFFFFFFF, light, 1.0F, 0.0F, 0.0F);
			prevX = x;
			prevY = y;
			prevZ = z;
		}
	}

	// ================================================================== the clouds

	private void clouds(State state, boolean far, int light, float lx, float ly, float lz) {
		int count = far ? 6 : PUFFS[state.quality];
		float t = state.time;
		float moving = Math.min(1.0F, state.speed / 0.6F);
		int base = 0xFFFFFF;
		for (int i = 0; i < count; i++) {
			float a = i / (float) count * Mth.TWO_PI + t * 0.012F;
			float wobble = Mth.sin(t * 0.05F + i * 1.9F);
			float x = Mth.cos(a) * (1.35F + 0.12F * wobble);
			float z = Mth.sin(a) * (2.25F + 0.15F * wobble) - 0.3F * moving;
			float y = 0.12F + 0.07F * Mth.sin(t * 0.07F + i);
			float half = 0.55F + 0.18F * Mth.sin(i * 1.7F) + 0.12F * moving;
			int alpha = (int) (215 + 30 * wobble);
			clouds.billboard(x, y, z, half, alpha << 24 | base, light, lx, ly, lz);
		}
		// the belly under the keel
		clouds.billboard(0.0F, -0.12F, 0.9F, 0.9F, 0xE6FFFFFF, light, lx, ly, lz);
		clouds.billboard(0.0F, -0.15F, -0.6F, 1.0F, 0xE6F4F8FF, light, lx, ly, lz);
		if (far) {
			return;
		}
		clouds.billboard(0.0F, -0.1F, 0.15F, 0.8F, 0xDDFFFFFF, light, lx, ly, lz);
		// the trail behind it in flight
		int trail = Math.round(TRAIL[state.quality] * moving);
		for (int i = 0; i < trail; i++) {
			float k = (i + 1) / (float) (trail + 1);
			float drift = (t * 0.6F + i * 7.0F) % 10.0F / 10.0F;
			float z = -2.4F - 2.2F * k - 0.6F * drift;
			float x = 0.5F * Mth.sin(i * 2.3F + t * 0.04F) * k;
			float y = 0.2F + 0.25F * k;
			int alpha = (int) (180 * (1.0F - k) * (1.0F - 0.5F * drift));
			if (alpha < 16) {
				continue;
			}
			clouds.billboard(x, y, z, 0.45F + 0.5F * k, alpha << 24 | base, light, lx, ly, lz);
		}
	}
}
