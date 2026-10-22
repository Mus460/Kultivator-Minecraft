package dev.kultivator.client.dunia.kapal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalStyle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The drawing of a flying hull, shared by the ships ({@link KapalRenderer}) and the sky pirates' raider
 * ({@link KapalBajakRenderer}): the hull runs from the display list ({@link KapalModel}) as vanilla block models in the
 * ship's livery ({@link KapalLivery}), turned by the yaw and swaying with turns and climbs; battened sails that belly with
 * the speed and ripple; the swallow-tailed banner and the masthead pennants; the standing rigging (thin rope strips facing
 * the camera); the qi oars that row the clouds; the levitation formation turning under the keel; lantern halos and, at
 * night, the cabin windows; the stern engine's glow and ring; and the cloud wake behind it. No block is placed: the hull
 * is geometry of the entity only.
 * <p>
 * LOD (V3): up to 64 b every run (tiled within 24 b on Normal and 40 b on Maximal, within a block budget; Minimal draws
 * the hull runs only, stretched); 64-160 b the hull runs stretched; beyond 160 b a silhouette of a few boxes and the
 * sails. Rigging within 64 b (Minimal: 24 b), oars within 64 b on Normal and Maximal, the formation and pennants within
 * 160 b (formation not on Minimal); every light effect follows {@code EffectQuality} and is skipped in a shader pack's
 * shadow pass (V2: layered emissive quads, no post chain). Client render thread only.
 */
final class KapalDraw {

	static final Identifier SAIL = Kultivator.id("textures/dunia/kapal/sail.png");
	static final Identifier SAIL_BAJAK = Kultivator.id("textures/dunia/kapal/sail_bajak.png");
	static final Identifier BANNER = Kultivator.id("textures/dunia/kapal/banner.png");
	static final Identifier GLOW = Kultivator.id("textures/dunia/kapal/glow.png");
	static final Identifier RING = Kultivator.id("textures/dunia/kapal/ring.png");
	static final Identifier CLOUD = Kultivator.id("textures/dunia/kapal/cloud.png");
	static final Identifier ROPE = Kultivator.id("textures/dunia/kapal/rope.png");
	static final Identifier OAR = Kultivator.id("textures/dunia/kapal/oar.png");
	static final Identifier FORMATION = Kultivator.id("textures/dunia/kapal/formation.png");

	static final double FULL = 64.0D;
	static final double SILHOUETTE = 160.0D;
	private static final int[] TILE_BUDGET = {0, 900, 2200};
	private static final float[] TILE_RANGE = {0.0F, 24.0F, 40.0F};

	private final BlockModelResolver blocks;
	private final Map<BlockState, BlockModelRenderState> models = new IdentityHashMap<>();
	private final QuadBuf cloth = new QuadBuf();
	private final QuadBuf banner = new QuadBuf();
	private final QuadBuf glow = new QuadBuf();
	private final QuadBuf ring = new QuadBuf();
	private final QuadBuf wake = new QuadBuf();
	private final QuadBuf rope = new QuadBuf();
	private final QuadBuf oar = new QuadBuf();
	private final QuadBuf disc = new QuadBuf();
	private RenderType sailType;
	private RenderType bajakSailType;
	private RenderType bannerType;
	private RenderType glowType;
	private RenderType ringType;
	private RenderType wakeType;
	private RenderType ropeType;
	private RenderType oarType;
	private RenderType discType;

	KapalDraw(BlockModelResolver blocks) {
		this.blocks = blocks;
	}

	private void types() {
		if (sailType == null) {
			sailType = RenderTypes.entityCutout(SAIL);
			bajakSailType = RenderTypes.entityCutout(SAIL_BAJAK);
			bannerType = RenderTypes.entityCutout(BANNER);
			glowType = RenderTypes.entityTranslucentEmissive(GLOW);
			ringType = RenderTypes.entityTranslucentEmissive(RING);
			wakeType = RenderTypes.entityTranslucent(CLOUD);
			ropeType = RenderTypes.entityCutout(ROPE);
			oarType = RenderTypes.entityTranslucentEmissive(OAR);
			discType = RenderTypes.entityTranslucentEmissive(FORMATION);
		}
	}

	/** Everything of one hull (the pose stack at the entity's position). */
	void submit(KapalRenderer.State state, PoseStack poseStack, SubmitNodeCollector collector) {
		types();
		KapalModel.Hull hull = state.model != null ? state.model : KapalModel.of(state.kind);
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
		// the camera in the hull's local frame (billboards and ropes face it)
		double r = Math.toRadians(state.yaw);
		float cos = (float) Math.cos(r);
		float sin = (float) Math.sin(r);
		float lx = state.camX * cos + state.camZ * sin;
		float lz = -state.camX * sin + state.camZ * cos;
		float ly = state.camY - state.bob;
		sails(state, hull, poseStack, collector, light);
		if (!shadow && state.distance <= FULL && (state.quality > 0 || state.distance <= 24.0D)) {
			rigging(hull, poseStack, collector, light, lx, ly, lz);
		}
		if (!shadow && state.distance <= SILHOUETTE) {
			extras(state, hull, poseStack, collector, lx, ly, lz);
		}
		poseStack.popPose();
		if (!shadow && state.wakeCount > 0) {
			wake(state, poseStack, collector);
		}
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

	private void hull(KapalRenderer.State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		boolean near = state.distance <= FULL;
		boolean details = near && state.quality > 0;
		int tileBudget = state.distance <= TILE_RANGE[state.quality] ? TILE_BUDGET[state.quality] : 0;
		List<KapalModel.Run> runs = hull.runs;
		int limit = details ? runs.size() : Math.min(runs.size(), hull.core);
		KapalStyle style = state.style;
		for (int i = 0; i < limit; i++) {
			KapalModel.Run run = runs.get(i);
			BlockModelRenderState model = model(KapalLivery.styled(run.state(), style));
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
	private void silhouette(KapalRenderer.State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		BlockModelRenderState planks = model(state.raider ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
				: KapalLivery.styled(Blocks.DARK_OAK_PLANKS.defaultBlockState(), state.style));
		BlockModelRenderState deck = model(state.raider ? Blocks.CRIMSON_PLANKS.defaultBlockState()
				: KapalLivery.styled(Blocks.SPRUCE_PLANKS.defaultBlockState(), state.style));
		BlockModelRenderState mast = model(state.raider ? Blocks.CRIMSON_STEM.defaultBlockState()
				: KapalLivery.styled(Blocks.DARK_OAK_LOG.defaultBlockState(), state.style));
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

	// ================================================================== cloth: sails, the banner, the pennants

	private void sails(KapalRenderer.State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		boolean broken = state.shipState == KapalRohEntity.STATE_BROKEN;
		float speed = Math.abs(state.speed);
		float belly = broken ? 0.05F : Mth.clamp(0.12F + speed * 2.4F, 0.12F, 0.9F);
		int cols = state.distance <= FULL ? (state.quality == 0 ? 3 : state.quality == 1 ? 5 : 8) : 2;
		int rows = state.distance <= FULL ? (state.quality == 0 ? 2 : 4) : 1;
		int tint = state.raider ? 0xF0E4DC : mix(state.style.sail(), state.color, 0.22F);
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
		cloth.submit(collector, poseStack, state.raider ? bajakSailType : sailType);
		if (state.distance <= SILHOUETTE) {
			banner.clear();
			bannerCloth(state, hull, light);
			pennants(state, hull, light);
			banner.submit(collector, poseStack, bannerType);
		}
	}

	/** A point of a sail: across u (x0 → x1), down v (y1 → y0), bellied forward (+z) and rippling. */
	private static float[] sailPoint(KapalModel.Sail sail, KapalRenderer.State state, float belly, float u, float v, float x0, float x1, float y0,
			float y1) {
		float x = Mth.lerp(u, x0, x1);
		float y = Mth.lerp(v, y1, y0);
		float shape = Mth.sin(u * Mth.PI) * (0.35F + 0.65F * Mth.sin(v * Mth.PI));
		float ripple = 0.06F * Mth.sin(state.time * 0.35F + u * 7.0F + v * 3.0F) * (0.4F + 0.6F * u);
		if (state.raider) {
			// tattered cloth flogs harder
			ripple *= 1.8F;
		}
		float z = sail.z() + belly * shape + ripple;
		return new float[]{x, y, z};
	}

	/** The banner hangs from its pole and flaps aft, more with speed. */
	private void bannerCloth(KapalRenderer.State state, KapalModel.Hull hull, int light) {
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

	private static float bannerSway(KapalRenderer.State state, float v, float speed) {
		return (0.06F + 0.2F * speed) * v * Mth.sin(state.time * (0.2F + 0.25F * speed) + v * 4.0F);
	}

	/**
	 * The long masthead pennants: a tapering streamer flying aft from the pole top, snapping in the wind (the wind of the
	 * ship's own speed plus a little high-altitude breeze; a docked ship's streamers droop).
	 */
	private void pennants(KapalRenderer.State state, KapalModel.Hull hull, int light) {
		if (hull.pennants.isEmpty()) {
			return;
		}
		int argb = 0xFF000000 | mix(state.color, 0xFFFFFF, state.raider ? 0.0F : 0.12F);
		int segs = state.quality == 0 || state.distance > FULL ? 2 : 6;
		float wind = state.docked ? 0.15F : Math.min(1.0F, 0.3F + Math.abs(state.speed) * 2.5F);
		for (float[] p : hull.pennants) {
			float px = p[0];
			float py = p[1];
			float pz = p[2];
			float len = p[3];
			for (int i = 0; i < segs; i++) {
				float v0 = i / (float) segs;
				float v1 = (i + 1) / (float) segs;
				float[] a = pennantPoint(state, px, py, pz, len, v0, wind);
				float[] c = pennantPoint(state, px, py, pz, len, v1, wind);
				float h0 = 0.34F * (1.0F - 0.75F * v0);
				float h1 = 0.34F * (1.0F - 0.75F * v1);
				banner.vertex(a[0], a[1] + h0, a[2], 0.0F, v0, argb, light, 1.0F, 0.0F, 0.0F);
				banner.vertex(c[0], c[1] + h1, c[2], 0.0F, v1, argb, light, 1.0F, 0.0F, 0.0F);
				banner.vertex(c[0], c[1] - h1, c[2], 1.0F, v1, argb, light, 1.0F, 0.0F, 0.0F);
				banner.vertex(a[0], a[1] - h0, a[2], 1.0F, v0, argb, light, 1.0F, 0.0F, 0.0F);
			}
		}
	}

	private static float[] pennantPoint(KapalRenderer.State state, float px, float py, float pz, float len, float v, float wind) {
		float flutter = (0.08F + 0.22F * wind) * v * Mth.sin(state.time * (0.3F + 0.5F * wind) - v * 6.0F + px);
		float droop = v * len * 0.7F * (1.0F - wind);
		float reach = v * len * (0.3F + 0.7F * wind);
		return new float[]{px + flutter, py - 0.2F - droop, pz - reach};
	}

	// ================================================================== rigging

	/** The standing rigging: hemp strips from mastheads to the rails, the yards and the stays. */
	private void rigging(KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, int light, float lx, float ly, float lz) {
		if (hull.rigging.isEmpty()) {
			return;
		}
		rope.clear();
		int argb = 0xFFFFFFFF;
		for (KapalModel.Line line : hull.rigging) {
			float dx = line.x1() - line.x0();
			float dy = line.y1() - line.y0();
			float dz = line.z1() - line.z0();
			float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			rope.ribbon(line.x0(), line.y0(), line.z0(), line.x1(), line.y1(), line.z1(), 0.035F, 0.0F, length * 2.0F, argb, light, lx, ly, lz);
		}
		rope.submit(collector, poseStack, ropeType);
	}

	// ================================================================== light: halos, windows, oars, the formation, the engine

	private void extras(KapalRenderer.State state, KapalModel.Hull hull, PoseStack poseStack, SubmitNodeCollector collector, float lx, float ly,
			float lz) {
		int full = LightCoordsUtil.FULL_BRIGHT;
		glow.clear();
		ring.clear();
		oar.clear();
		disc.clear();
		boolean broken = state.shipState == KapalRohEntity.STATE_BROKEN;
		boolean running = !state.docked && !broken;
		int qi = mixToWhite(state.color, 0.35F);
		if (state.quality > 0 || state.distance <= 32.0D) {
			for (KapalModel.Light lantern : hull.lights) {
				if (lantern.window() && !state.night) {
					continue;
				}
				int rgb = lantern.window() ? lantern.rgb() : styledLantern(state, lantern.rgb());
				float flicker = 0.85F + 0.15F * Mth.sin(state.time * 0.5F + lantern.x() * 3.1F + lantern.z());
				float a = (state.quality == 2 ? 0.55F : 0.4F) * flicker;
				if (broken && ((int) (state.time * 0.7F + lantern.x() * 5.0F)) % 7 == 0) {
					// a broken hull's lanterns gutter
					a *= 0.3F;
				}
				glow.billboard(lantern.x(), lantern.y(), lantern.z(), 0.9F * lantern.size(), argb(a, rgb), full, lx, ly, lz);
				if (state.quality == 2) {
					glow.billboard(lantern.x(), lantern.y(), lantern.z(), 2.2F * lantern.size(), argb(0.12F * flicker, rgb), full, lx, ly, lz);
				}
			}
		}
		float power = running ? Math.min(1.0F, 0.35F + Math.abs(state.speed) * 2.0F) : 0.18F;
		for (float[] engine : hull.engines) {
			float pulse = 0.8F + 0.2F * Mth.sin(state.time * 0.6F + engine[0]);
			float ex = engine[0];
			float ey = engine[1] + 0.5F;
			float ez = engine[2] - 0.6F;
			glow.billboard(ex, ey, ez, 0.8F + 0.6F * power, argb(0.7F * power * pulse, state.raider ? 0xFF8A6A : 0xBFF4FF), full, lx, ly, lz);
			glow.billboard(ex, ey, ez, 1.6F + 1.2F * power, argb(0.25F * power * pulse, qi), full, lx, ly, lz);
			if (running && state.quality > 0) {
				// the engine ring, facing aft, turning
				float spin = state.time * 0.15F;
				float half = 0.9F + 0.3F * power;
				float c = Mth.cos(spin) * half;
				float s = Mth.sin(spin) * half;
				int col = argb(0.55F * power, state.raider ? 0xFFB09A : 0xCFF6FF);
				ring.vertex(ex - c - s, ey - c + s, ez - 0.02F, 0.0F, 0.0F, col, full, 0.0F, 0.0F, -1.0F);
				ring.vertex(ex + c - s, ey - s - c, ez - 0.02F, 1.0F, 0.0F, col, full, 0.0F, 0.0F, -1.0F);
				ring.vertex(ex + c + s, ey + c - s, ez - 0.02F, 1.0F, 1.0F, col, full, 0.0F, 0.0F, -1.0F);
				ring.vertex(ex - c + s, ey + s + c, ez - 0.02F, 0.0F, 1.0F, col, full, 0.0F, 0.0F, -1.0F);
			}
		}
		if (state.distance <= FULL && state.quality > 0 && !hull.oars.isEmpty() && running) {
			oars(state, hull, qi, full, lx, ly, lz);
		}
		if (state.quality > 0 && hull.formation[1] > 0.0F && !broken) {
			formation(state, hull, qi, full);
		}
		if (state.leaping) {
			// a cloud leap gathers: the whole hull glows faintly
			glow.billboard(0.0F, hull.deck, 0.0F, hull.length * 0.6F, argb(0.18F, 0xEAF6FF), full, lx, ly, lz);
		}
		disc.submit(collector, poseStack, discType);
		oar.submit(collector, poseStack, oarType);
		glow.submit(collector, poseStack, glowType);
		ring.submit(collector, poseStack, ringType);
	}

	/** A livery may tint the lantern glows (Giok Laut's soul lanterns burn blue). */
	private static int styledLantern(KapalRenderer.State state, int rgb) {
		return rgb == 0xFFC66A ? state.style.lantern() : rgb;
	}

	/**
	 * The qi oars: blades of light from rowlocks on the beam, all pulling together. The stroke quickens with the speed; on
	 * the power stroke the blade dips into the clouds and sweeps aft, on the recovery it lifts and swings forward.
	 */
	private void oars(KapalRenderer.State state, KapalModel.Hull hull, int qi, int full, float lx, float ly, float lz) {
		float rate = Math.min(0.42F, 0.07F + Math.abs(state.speed) * 1.1F);
		float phase = state.time * rate;
		float sweep = Mth.sin(phase) * 0.5F;
		float dip = 0.5F + 0.22F * Mth.cos(phase);
		float power = Math.min(1.0F, 0.45F + Math.abs(state.speed) * 2.0F);
		int col = argb(0.7F * power, mixToWhite(qi, 0.3F));
		float cosDip = Mth.cos(dip);
		float sinDip = Mth.sin(dip);
		for (KapalModel.Oar o : hull.oars) {
			float bx = o.x() + o.side() * 0.45F;
			float by = o.y();
			float bz = o.z();
			float tx = bx + o.side() * cosDip * Mth.cos(sweep) * o.length();
			float ty = by - sinDip * o.length();
			float tz = bz - cosDip * Mth.sin(sweep) * o.length();
			oar.ribbon(bx, by, bz, tx, ty, tz, 0.3F, 0.0F, 1.0F, col, full, lx, ly, lz);
			if (state.quality == 2) {
				glow.billboard(tx, ty, tz, 0.6F, argb(0.3F * power, qi), full, lx, ly, lz);
			}
		}
	}

	/** The levitation formation under the keel: a rune disc turning, brighter with speed and in a cloud leap. */
	private void formation(KapalRenderer.State state, KapalModel.Hull hull, int qi, int full) {
		float radius = hull.formation[1];
		float y = hull.formation[0];
		float power;
		if (state.leaping) {
			power = 1.0F;
		} else if (state.docked) {
			power = 0.22F;
		} else {
			power = Math.min(1.0F, 0.45F + Math.abs(state.speed) * 2.2F);
		}
		float pulse = 0.88F + 0.12F * Mth.sin(state.time * 0.12F);
		float angle = state.time * (0.008F + Math.abs(state.speed) * 0.06F) + (state.leaping ? state.time * 0.05F : 0.0F);
		disc.flatTurned(0.0F, y, 0.0F, radius, angle, argb(0.42F * power * pulse, state.raider ? 0xFF6A50 : qi), full);
		if (state.quality == 2 || state.leaping) {
			// a counter-turning inner ring and a faint bloom below it (layered quads, no post chain)
			ring.flatTurned(0.0F, y - 0.05F, 0.0F, radius * 0.58F, -angle * 1.7F, argb(0.35F * power, mixToWhite(qi, 0.4F)), full);
			glow.flatTurned(0.0F, y - 0.1F, 0.0F, radius * 1.25F, 0.0F, argb(0.1F * power, qi), full);
		}
	}

	// ================================================================== the cloud wake (world anchored)

	private void wake(KapalRenderer.State state, PoseStack poseStack, SubmitNodeCollector collector) {
		wake.clear();
		int light = LightCoordsUtil.pack(15, 15);
		int cap = state.quality == 0 ? 12 : state.quality == 1 ? 30 : 60;
		int n = Math.min(cap, state.wakeCount);
		int step = Math.max(1, state.wakeCount / Math.max(1, n));
		float scale = state.raider ? 1.0F : 1.0F + state.kind.ordinal() * 0.8F;
		int tint = state.raider ? 0x8C8A94 : 0xF4F8FF;
		for (int i = 0; i < state.wakeCount; i += step) {
			int o = i * 4;
			float age = state.wake[o + 3];
			float size = scale * (0.9F + 2.4F * age);
			float alpha = 0.55F * (1.0F - age) * Math.min(1.0F, age * 6.0F);
			wake.billboard(state.wake[o], state.wake[o + 1], state.wake[o + 2], size, argb(alpha, tint), light, state.camX, state.camY,
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
		return mix(rgb, 0xFFFFFF, t);
	}

	/** {@code a} toward {@code b} by {@code t} (0..1), per channel. */
	static int mix(int a, int b, float t) {
		int r = (a >> 16) & 0xFF;
		int g = (a >> 8) & 0xFF;
		int bl = a & 0xFF;
		r = Math.round(r + (((b >> 16) & 0xFF) - r) * t);
		g = Math.round(g + (((b >> 8) & 0xFF) - g) * t);
		bl = Math.round(bl + ((b & 0xFF) - bl) * t);
		return r << 16 | g << 8 | bl;
	}
}
