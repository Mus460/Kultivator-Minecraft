package dev.kultivator.client.dunia.kapal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws {@code kultivator:kapal_roh} (DESIGN 34.5.15): the hull from its display list ({@link KapalModel}) with the
 * vanilla block models (each run one block model, stretched, or tiled block by block up close), turned by the ship's
 * yaw and swaying with its turns and climbs; battened sails that belly with the speed and ripple, a swallow-tailed
 * banner in the sect colour, lantern halos, the stern's qi engine glow and a wake of cloud puffs behind it. No block is
 * placed: the hull is geometry of this entity only.
 * <p>
 * LOD (V3): up to 64 b every run (tiled within 24 b on Normal and 40 b on Maximal, within a block budget; Minimal draws
 * the hull runs only, stretched); 64-160 b the hull runs stretched; beyond 160 b a silhouette of a few boxes and the
 * sails. Halos, wake and engine glow follow {@code EffectQuality} and are skipped in a shader pack's shadow pass (V2:
 * layered emissive quads, no post chain).
 */
public class KapalRenderer extends EntityRenderer<KapalRohEntity, KapalRenderer.State> {

	public static final Identifier SAIL = Kultivator.id("textures/dunia/kapal/sail.png");
	public static final Identifier BANNER = Kultivator.id("textures/dunia/kapal/banner.png");
	public static final Identifier GLOW = Kultivator.id("textures/dunia/kapal/glow.png");
	public static final Identifier RING = Kultivator.id("textures/dunia/kapal/ring.png");
	public static final Identifier CLOUD = Kultivator.id("textures/dunia/kapal/cloud.png");

	private static final double FULL = 64.0D;
	private static final double SILHOUETTE = 160.0D;
	private static final int[] TILE_BUDGET = {0, 900, 2200};
	private static final float[] TILE_RANGE = {0.0F, 24.0F, 40.0F};

	private final BlockModelResolver blocks;
	private final Map<BlockState, BlockModelRenderState> models = new IdentityHashMap<>();
	private final QuadBuf cloth = new QuadBuf();
	private final QuadBuf banner = new QuadBuf();
	private final QuadBuf glow = new QuadBuf();
	private final QuadBuf ring = new QuadBuf();
	private final QuadBuf wake = new QuadBuf();
	private RenderType sailType;
	private RenderType bannerType;
	private RenderType glowType;
	private RenderType ringType;
	private RenderType wakeType;

	/** The render state: the hull, its pose and the extras' strength. */
	public static class State extends EntityRenderState {
		public KapalClass kind = KapalClass.PERAHU;
		public float yaw;
		public float roll;
		public float pitch;
		public float bob;
		public byte shipState;
		public boolean docked;
		public float speed;
		public int color = KapalRohEntity.DEFAULT_COLOR;
		public float hull = 1.0F;
		public double distance;
		public int quality;
		public float time;
		public boolean leaping;
		public float camX;
		public float camY;
		public float camZ;
		/** Wake puffs relative to the entity: x, y, z, age 0..1 per puff. */
		public float[] wake = new float[0];
		public int wakeCount;
	}

	public KapalRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.blocks = context.getBlockModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(KapalRohEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		double r = entity.kind().radius() + 6.0D;
		if (entity.distanceToSqr(camX, camY, camZ) > (256.0D + r) * (256.0D + r)) {
			return false;
		}
		AABB box = getBoundingBoxForCulling(entity, partialTicks);
		return box.hasNaN() || culler.isVisible(box);
	}

	@Override
	protected AABB getBoundingBoxForCulling(KapalRohEntity entity, float partialTicks) {
		KapalClass kind = entity.kind();
		double r = kind.radius() + 2.0D;
		double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		return new AABB(x - r, y - 2.0D, z - r, x + r, y + kind.height() + 3.0D, z + r);
	}

	@Override
	public void extractRenderState(KapalRohEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kind = entity.kind();
		state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		float turn = Mth.wrapDegrees(entity.getYRot() - entity.yRotO);
		float climb = (float) (entity.getY() - entity.yOld);
		state.roll = Mth.clamp(-turn * 2.6F, -9.0F, 9.0F);
		state.pitch = Mth.clamp(-climb * 18.0F, -6.0F, 6.0F);
		state.shipState = entity.state();
		state.docked = entity.docked();
		state.speed = entity.speed();
		state.color = entity.color();
		state.hull = entity.kind().hull() <= 0 ? 1.0F : Mth.clamp(entity.hull() / (float) entity.kind().hull(), 0.0F, 1.0F);
		state.time = state.ageInTicks;
		state.bob = state.docked ? 0.05F * Mth.sin(state.time * 0.08F) : 0.12F * Mth.sin(state.time * 0.11F) * Math.min(1.0F,
				0.3F + Math.abs(state.speed) * 2.0F);
		if (state.shipState == KapalRohEntity.STATE_BROKEN) {
			state.roll = 7.0F;
			state.pitch = -3.0F;
			state.bob = 0.0F;
		}
		state.leaping = state.shipState == KapalRohEntity.STATE_LEAP;
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		state.camX = (float) (camera.x - state.x);
		state.camY = (float) (camera.y - state.y);
		state.camZ = (float) (camera.z - state.z);
		state.distance = Math.sqrt(state.camX * state.camX + state.camY * state.camY + state.camZ * state.camZ);
		state.quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
		KapalFx.Trail trail = KapalFx.trail(entity.getId());
		int count = trail == null ? 0 : trail.count();
		if (state.wake.length < count * 4) {
			state.wake = new float[count * 4];
		}
		state.wakeCount = 0;
		if (trail != null) {
			float now = entity.tickCount + partialTicks;
			for (int i = 0; i < count; i++) {
				double[] p = trail.at(i);
				float age = (float) ((now - p[3]) / KapalFx.WAKE_LIFE);
				if (age < 0.0F || age > 1.0F) {
					continue;
				}
				int o = state.wakeCount * 4;
				state.wake[o] = (float) (p[0] - state.x);
				state.wake[o + 1] = (float) (p[1] - state.y);
				state.wake[o + 2] = (float) (p[2] - state.z);
				state.wake[o + 3] = age;
				state.wakeCount++;
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		KapalModel.Hull hull = KapalModel.of(state.kind);
		boolean shadow = ShaderCompat.shadowPass();
		poseStack.pushPose();
		poseStack.translate(0.0F, state.bob, 0.0F);
		poseStack.rotate(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.rotate(Axis.XP.rotationDegrees(state.pitch));
		poseStack.rotate(Axis.ZP.rotationDegrees(state.roll));
		int light = state.lightCoords;
		if (state.distance > SILHOUETTE) {
			silhouette(state, hull, poseStack, collector, light);
		} else {
			hull(state, hull, poseStack, collector, light);
		}
		sails(state, hull, poseStack, collector, light);
		if (!shadow && state.distance <= SILHOUETTE) {
			extras(state, hull, poseStack, collector);
		}
		poseStack.popPose();
		if (!shadow && state.wakeCount > 0) {
			wake(state, poseStack, collector);
		}
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== the hull

	private BlockModelRenderState model(BlockState state) {
		BlockModelRenderState model = models.get(state);
		if (model == null) {
			model = new BlockModelRenderState();
			blocks.update(model, state, DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
			models.put(state, model);
		}
		return model;
	}

	private void hull(State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		boolean near = state.distance <= FULL;
		boolean details = near && state.quality > 0;
		int tileBudget = state.distance <= TILE_RANGE[state.quality] ? TILE_BUDGET[state.quality] : 0;
		List<KapalModel.Run> runs = hull.runs;
		int limit = details ? runs.size() : Math.min(runs.size(), hull.core);
		for (int i = 0; i < limit; i++) {
			KapalModel.Run run = runs.get(i);
			BlockModelRenderState model = model(run.state());
			if (model.isEmpty()) {
				continue;
			}
			poseStack.pushPose();
			poseStack.translate(run.x(), run.y(), run.z());
			if (run.n() > 1 && tileBudget >= run.n()) {
				tileBudget -= run.n();
				for (int k = 0; k < run.n(); k++) {
					if (k > 0) {
						poseStack.translate(run.alongZ() ? 0.0F : 1.0F, 0.0F, run.alongZ() ? 1.0F : 0.0F);
					}
					model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
				}
			} else {
				if (run.n() > 1) {
					poseStack.scale(run.alongZ() ? 1.0F : run.n(), 1.0F, run.alongZ() ? run.n() : 1.0F);
				}
				model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			}
			poseStack.popPose();
		}
	}

	/** Far away: three tapered hull boxes, the deck and the masts. */
	private void silhouette(State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		BlockModelRenderState planks = model(Blocks.DARK_OAK_PLANKS.defaultBlockState());
		BlockModelRenderState deck = model(Blocks.SPRUCE_PLANKS.defaultBlockState());
		BlockModelRenderState mast = model(Blocks.DARK_OAK_LOG.defaultBlockState());
		float w = hull.width - 2.0F;
		float l = hull.length;
		float d = hull.deck;
		box(planks, poseStack, collector, light, -w * 0.5F + 1.0F, 1.0F, -l * 0.3F, w - 2.0F, d - 1.0F, l * 0.6F);
		box(planks, poseStack, collector, light, -w * 0.5F, d - 2.0F, -l * 0.42F, w, 1.0F, l * 0.84F);
		box(deck, poseStack, collector, light, -w * 0.5F + 0.5F, d - 1.0F, -l * 0.45F, w - 1.0F, 1.0F, l * 0.9F);
		for (KapalModel.Sail sail : hull.sails) {
			box(mast, poseStack, collector, light, -0.5F, d, sail.z() + 0.6F - 0.5F, 1.0F, sail.y1() - d + 1.0F, 1.0F);
		}
	}

	private static void box(BlockModelRenderState model, PoseStack poseStack, SubmitNodeCollector collector, int light, float x, float y, float z,
			float sx, float sy, float sz) {
		if (model.isEmpty() || sx <= 0.0F || sy <= 0.0F || sz <= 0.0F) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.scale(sx, sy, sz);
		model.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	// ================================================================== cloth: sails and the banner

	private void sails(State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		if (sailType == null) {
			sailType = RenderTypes.entityCutout(SAIL);
			bannerType = RenderTypes.entityCutout(BANNER);
		}
		boolean broken = state.shipState == KapalRohEntity.STATE_BROKEN;
		float speed = Math.abs(state.speed);
		float belly = broken ? 0.05F : Mth.clamp(0.12F + speed * 2.4F, 0.12F, 0.9F);
		int cols = state.distance <= FULL ? (state.quality == 0 ? 3 : state.quality == 1 ? 5 : 8) : 2;
		int rows = state.distance <= FULL ? (state.quality == 0 ? 2 : 4) : 1;
		int tint = mixToWhite(state.color, 0.78F);
		int argb = 0xFF000000 | tint;
		cloth.clear();
		for (KapalModel.Sail sail : hull.sails) {
			float x0 = sail.x0();
			float x1 = sail.x1();
			float y0 = sail.y0();
			float y1 = sail.y1();
			for (int r = 0; r < rows; r++) {
				float v0 = r / (float) rows;
				float v1 = (r + 1) / (float) rows;
				for (int c = 0; c < cols; c++) {
					float u0 = c / (float) cols;
					float u1 = (c + 1) / (float) cols;
					float[] a = sailPoint(sail, state, belly, u0, v0, x0, x1, y0, y1);
					float[] b = sailPoint(sail, state, belly, u1, v0, x0, x1, y0, y1);
					float[] cc = sailPoint(sail, state, belly, u1, v1, x0, x1, y0, y1);
					float[] d = sailPoint(sail, state, belly, u0, v1, x0, x1, y0, y1);
					// top edge at y1 (v = 0), the bottom batten at y0 (v = 1)
					cloth.vertex(a[0], a[1], a[2], u0, v0, argb, light, 0.0F, 0.0F, 1.0F);
					cloth.vertex(d[0], d[1], d[2], u0, v1, argb, light, 0.0F, 0.0F, 1.0F);
					cloth.vertex(cc[0], cc[1], cc[2], u1, v1, argb, light, 0.0F, 0.0F, 1.0F);
					cloth.vertex(b[0], b[1], b[2], u1, v0, argb, light, 0.0F, 0.0F, 1.0F);
				}
			}
		}
		cloth.submit(collector, poseStack, sailType);
		if (state.distance <= SILHOUETTE) {
			bannerCloth(state, hull, light);
			banner.submit(collector, poseStack, bannerType);
		}
	}

	/** A point of a sail: across u (x0 → x1), down v (y1 → y0), bellied forward (+z) and rippling. */
	private static float[] sailPoint(KapalModel.Sail sail, State state, float belly, float u, float v, float x0, float x1, float y0, float y1) {
		float x = Mth.lerp(u, x0, x1);
		float y = Mth.lerp(v, y1, y0);
		float shape = Mth.sin(u * Mth.PI) * (0.35F + 0.65F * Mth.sin(v * Mth.PI));
		float ripple = 0.06F * Mth.sin(state.time * 0.35F + u * 7.0F + v * 3.0F) * (0.4F + 0.6F * u);
		float z = sail.z() + belly * shape + ripple;
		return new float[]{x, y, z};
	}

	/** The banner hangs from its pole and flaps aft, more with speed. */
	private void bannerCloth(State state, KapalModel.Hull hull, int light) {
		banner.clear();
		float[] b = hull.banner;
		float px = b[0];
		float top = b[1];
		float pz = b[2];
		float w = b[3];
		float h = b[4];
		int argb = 0xFF000000 | (state.color & 0xFFFFFF);
		int segs = state.quality == 0 ? 3 : 6;
		float speed = Math.min(1.0F, Math.abs(state.speed) * 2.5F);
		for (int i = 0; i < segs; i++) {
			float v0 = i / (float) segs;
			float v1 = (i + 1) / (float) segs;
			float s0 = bannerSway(state, v0, speed);
			float s1 = bannerSway(state, v1, speed);
			float tilt0 = -v0 * h * 0.35F * speed;
			float tilt1 = -v1 * h * 0.35F * speed;
			float y0 = top - v0 * h * (1.0F - 0.3F * speed);
			float y1 = top - v1 * h * (1.0F - 0.3F * speed);
			// the cloth spans z (pole at the front edge, flying aft)
			banner.vertex(px + s0, y0, pz + tilt0, 0.0F, v0, argb, light, 1.0F, 0.0F, 0.0F);
			banner.vertex(px + s1, y1, pz + tilt1, 0.0F, v1, argb, light, 1.0F, 0.0F, 0.0F);
			banner.vertex(px + s1, y1, pz + tilt1 - w, 1.0F, v1, argb, light, 1.0F, 0.0F, 0.0F);
			banner.vertex(px + s0, y0, pz + tilt0 - w, 1.0F, v0, argb, light, 1.0F, 0.0F, 0.0F);
		}
	}

	private static float bannerSway(State state, float v, float speed) {
		return (0.06F + 0.2F * speed) * v * Mth.sin(state.time * (0.2F + 0.25F * speed) + v * 4.0F);
	}

	// ================================================================== light: lantern halos, the engine

	private void extras(State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector) {
		if (glowType == null) {
			glowType = RenderTypes.entityTranslucentEmissive(GLOW);
			ringType = RenderTypes.entityTranslucentEmissive(RING);
		}
		// the camera in the hull's local frame (the halos face it)
		double r = Math.toRadians(state.yaw);
		float cos = (float) Math.cos(r);
		float sin = (float) Math.sin(r);
		float lx = state.camX * cos + state.camZ * sin;
		float lz = -state.camX * sin + state.camZ * cos;
		float ly = state.camY - state.bob;
		int full = LightCoordsUtil.FULL_BRIGHT;
		glow.clear();
		ring.clear();
		if (state.quality > 0 || state.distance <= 32.0D) {
			for (KapalModel.Light lantern : hull.lights) {
				float flicker = 0.85F + 0.15F * Mth.sin(state.time * 0.5F + lantern.x() * 3.1F + lantern.z());
				float a = (state.quality == 2 ? 0.55F : 0.4F) * flicker;
				glow.billboard(lantern.x(), lantern.y(), lantern.z(), 0.9F * lantern.size(), argb(a, lantern.rgb()), full, lx, ly, lz);
				if (state.quality == 2) {
					glow.billboard(lantern.x(), lantern.y(), lantern.z(), 2.2F * lantern.size(), argb(0.12F * flicker, lantern.rgb()),
							full, lx, ly, lz);
				}
			}
		}
		boolean running = !state.docked && state.shipState != KapalRohEntity.STATE_BROKEN;
		float power = running ? Math.min(1.0F, 0.35F + Math.abs(state.speed) * 2.0F) : 0.18F;
		int qi = mixToWhite(state.color, 0.35F);
		for (float[] engine : hull.engines) {
			float pulse = 0.8F + 0.2F * Mth.sin(state.time * 0.6F + engine[0]);
			float ex = engine[0];
			float ey = engine[1] + 0.5F;
			float ez = engine[2] - 0.6F;
			glow.billboard(ex, ey, ez, 0.8F + 0.6F * power, argb(0.7F * power * pulse, 0xBFF4FF), full, lx, ly, lz);
			glow.billboard(ex, ey, ez, 1.6F + 1.2F * power, argb(0.25F * power * pulse, qi), full, lx, ly, lz);
			if (running && state.quality > 0) {
				// the engine ring, facing aft, turning
				float spin = state.time * 0.15F;
				float half = 0.9F + 0.3F * power;
				float c = Mth.cos(spin) * half;
				float s = Mth.sin(spin) * half;
				int col = argb(0.55F * power, 0xCFF6FF);
				ring.vertex(ex - c - s, ey - c + s, ez - 0.02F, 0.0F, 0.0F, col, full, 0.0F, 0.0F, -1.0F);
				ring.vertex(ex + c - s, ey - s - c, ez - 0.02F, 1.0F, 0.0F, col, full, 0.0F, 0.0F, -1.0F);
				ring.vertex(ex + c + s, ey + c - s, ez - 0.02F, 1.0F, 1.0F, col, full, 0.0F, 0.0F, -1.0F);
				ring.vertex(ex - c + s, ey + s + c, ez - 0.02F, 0.0F, 1.0F, col, full, 0.0F, 0.0F, -1.0F);
			}
		}
		if (state.leaping) {
			// a cloud leap gathers: the whole hull glows faintly
			glow.billboard(0.0F, hull.deck, 0.0F, hull.length * 0.6F, argb(0.18F, 0xEAF6FF), full, lx, ly, lz);
		}
		glow.submit(collector, poseStack, glowType);
		ring.submit(collector, poseStack, ringType);
	}

	// ================================================================== the cloud wake (world anchored)

	private void wake(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (wakeType == null) {
			wakeType = RenderTypes.entityTranslucent(CLOUD);
		}
		wake.clear();
		int light = LightCoordsUtil.pack(15, 15);
		int cap = state.quality == 0 ? 12 : state.quality == 1 ? 30 : 60;
		int n = Math.min(cap, state.wakeCount);
		int step = Math.max(1, state.wakeCount / Math.max(1, n));
		float scale = 1.0F + state.kind.ordinal() * 0.8F;
		for (int i = 0; i < state.wakeCount; i += step) {
			int o = i * 4;
			float age = state.wake[o + 3];
			float size = scale * (0.9F + 2.4F * age);
			float alpha = 0.55F * (1.0F - age) * Math.min(1.0F, age * 6.0F);
			wake.billboard(state.wake[o], state.wake[o + 1], state.wake[o + 2], size, argb(alpha, 0xF4F8FF), light, state.camX, state.camY,
					state.camZ);
		}
		wake.submit(collector, poseStack, wakeType);
	}

	// ================================================================== colour helpers

	static int argb(float alpha, int rgb) {
		int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
		return a << 24 | (rgb & 0xFFFFFF);
	}

	static int mixToWhite(int rgb, float t) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return r << 16 | g << 8 | b;
	}
}
