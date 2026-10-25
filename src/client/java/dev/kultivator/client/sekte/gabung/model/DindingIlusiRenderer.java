package dev.kultivator.client.sekte.gabung.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.sekte.gabung.masuk.DindingIlusiEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * {@code kultivator:dinding_ilusi} (DESIGN 41.11.3): a cell of the Stone Sentinel Maze of "Labirin Batu". The collider
 * itself is invisible; the client draws what the test wants seen, as quads on {@code textures/entity/gabung/dinding_ilusi.png}
 * (128 x 96, {@code tools/gen_gabung_assets.py}):
 * <ul>
 * <li>a wall: a column of grey-white formation mist 1 x 3 x 1 in two layers whose windows drift slowly through the
 * mist tile (the inner layer against the outer), thicker at the foot, thinning at the top;</li>
 * <li>a revealed wall ({@link DindingIlusiEntity#FLAG_TERUNGKAP}): its trigram ({@link DindingIlusiEntity#trigram()},
 * Qian Dui Li Zhen Xun Kan Gen Kun) glowing gold on all four faces at eye height;</li>
 * <li>a formation eye ({@link DindingIlusiEntity#FLAG_MATA}): an eight-spoked circle on the ground, pale and breathing
 * until stepped on, gold once stepped on in order ({@link DindingIlusiEntity#FLAG_TERINJAK}), a red cross flashing on a
 * wrong step ({@link DindingIlusiEntity#FLAG_SALAH}); a revealed eye also shows its order 1-3;</li>
 * <li>the end of the test: everything thins away over {@link DindingIlusiEntity#PUDAR_TICKS}.</li>
 * </ul>
 * Mist uses {@code entityTranslucent} (lit), glyphs and the eye the vanilla {@code eyes} type (emissive): no post chain.
 * Nothing beyond 48 blocks; Minimal: one still mist layer.
 */
public class DindingIlusiRenderer extends EntityRenderer<DindingIlusiEntity, DindingIlusiRenderer.State> {

	private static final Identifier TEXTURE = Kultivator.id("textures/entity/gabung/dinding_ilusi.png");
	private static final RenderType KABUT = RenderTypes.entityTranslucent(TEXTURE);
	private static final RenderType CAHAYA = RenderTypes.eyes(TEXTURE);
	private static final double JARAK_SQ = 48.0D * 48.0D;
	private static final float W = 128.0F;
	private static final float H = 96.0F;

	public static class State extends EntityRenderState {
		float waktu;
		int trigram = -1;
		int flags;
		int urutan;
		float pudar = 1.0F;
		boolean rinci;
		/** A small per-cell phase so neighbouring walls do not breathe in step. */
		float fase;
	}

	public DindingIlusiRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DindingIlusiEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.waktu = entity.level().getGameTime() + partialTicks;
		state.trigram = entity.trigram();
		state.flags = entity.flags();
		state.urutan = entity.urutan();
		state.rinci = !EffectQuality.minimal();
		state.fase = (Mth.floor(entity.getX()) * 31 + Mth.floor(entity.getZ()) * 17) % 64 * 0.1F;
		long sejak = entity.pudarSejak();
		state.pudar = sejak <= 0L ? 1.0F
				: Mth.clamp(1.0F - (entity.level().getGameTime() - sejak + partialTicks) / DindingIlusiEntity.PUDAR_TICKS, 0.0F, 1.0F);
	}

	@Override
	protected AABB getBoundingBoxForCulling(DindingIlusiEntity entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(0.1D);
	}

	private static boolean has(State s, int flag) {
		return (s.flags & flag) != 0;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.distanceToCameraSq > JARAK_SQ || state.pudar <= 0.0F) {
			return;
		}
		int light = state.lightCoords;
		if (has(state, DindingIlusiEntity.FLAG_MATA)) {
			collector.submitCustomGeometry(poseStack, CAHAYA, (pose, buffer) -> mata(state, pose, buffer));
		} else {
			collector.submitCustomGeometry(poseStack, KABUT, (pose, buffer) -> kabut(state, pose, buffer, light));
			if (has(state, DindingIlusiEntity.FLAG_TERUNGKAP) && state.trigram >= 0) {
				collector.order(1).submitCustomGeometry(poseStack, CAHAYA, (pose, buffer) -> trigram(state, pose, buffer));
			}
		}
		super.submit(state, poseStack, collector, camera);
	}

	// ================================================================== mist

	private static void kabut(State s, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		float t = s.rinci ? s.waktu * 0.01F + s.fase : 0.0F;
		lapis(s, pose, buffer, light, 0.5F, 150, t, 0.0F);
		if (s.rinci) {
			lapis(s, pose, buffer, light, 0.4F, 120, -t * 1.3F, 0.5F);
		}
	}

	/** One mist shell of half-width {@code r}: four sides in three stacked bands (thick foot, thin top) and a lid. */
	private static void lapis(State s, PoseStack.Pose pose, VertexConsumer buffer, int light, float r, int alpha, float drift, float offset) {
		// a 32 x 32 window sliding inside the 64 x 64 mist tile
		float u0 = (0.5F + 0.5F * Mth.sin(drift + offset)) * 32.0F / W;
		float v0 = (0.5F + 0.5F * Mth.cos(drift * 0.7F + offset)) * 32.0F / H;
		float du = 32.0F / W;
		float dv = 32.0F / H;
		for (int band = 0; band < 3; band++) {
			float y0 = band;
			float y1 = band + 1.0F;
			float a = s.pudar * alpha * (band == 0 ? 1.0F : band == 1 ? 0.85F : 0.6F);
			float breathe = s.rinci ? 0.9F + 0.1F * Mth.sin(s.waktu * 0.05F + s.fase + band) : 1.0F;
			int c = ARGB.color(Mth.clamp(Math.round(a * breathe), 0, 255), 0xE8ECF4);
			float va = v0 + dv * band / 3.0F;
			float vb = v0 + dv * (band + 1) / 3.0F;
			quad(buffer, pose, -r, y1, -r, r, y1, -r, r, y0, -r, -r, y0, -r, u0, va, u0 + du, vb, c, light, 0, 0, -1);
			quad(buffer, pose, r, y1, r, -r, y1, r, -r, y0, r, r, y0, r, u0, va, u0 + du, vb, c, light, 0, 0, 1);
			quad(buffer, pose, -r, y1, r, -r, y1, -r, -r, y0, -r, -r, y0, r, u0, va, u0 + du, vb, c, light, -1, 0, 0);
			quad(buffer, pose, r, y1, -r, r, y1, r, r, y0, r, r, y0, -r, u0, va, u0 + du, vb, c, light, 1, 0, 0);
		}
		int top = ARGB.color(Mth.clamp(Math.round(s.pudar * alpha * 0.45F), 0, 255), 0xF0F4FA);
		quad(buffer, pose, -r, 3.0F, -r, -r, 3.0F, r, r, 3.0F, r, r, 3.0F, -r, u0, v0, u0 + du, v0 + dv, top, light, 0, 1, 0);
	}

	// ================================================================== trigram and eye

	private static void trigram(State s, PoseStack.Pose pose, VertexConsumer buffer) {
		float u0 = s.trigram * 16.0F / W;
		float u1 = (s.trigram + 1) * 16.0F / W;
		float v0 = 64.0F / H;
		float v1 = 80.0F / H;
		float glow = s.rinci ? 0.8F + 0.2F * Mth.sin(s.waktu * 0.15F + s.fase) : 1.0F;
		int c = ARGB.color(Mth.clamp(Math.round(255.0F * s.pudar * glow), 0, 255), 0xFFFFFF);
		float r = 0.505F;
		float h = 0.4F;
		float y = 1.6F;
		int full = 0xF000F0;
		duaSisi(buffer, pose, -h, y + h, -r, h, y + h, -r, h, y - h, -r, -h, y - h, -r, u0, v0, u1, v1, c, full, 0, 0, -1);
		duaSisi(buffer, pose, h, y + h, r, -h, y + h, r, -h, y - h, r, h, y - h, r, u0, v0, u1, v1, c, full, 0, 0, 1);
		duaSisi(buffer, pose, -r, y + h, h, -r, y + h, -h, -r, y - h, -h, -r, y - h, h, u0, v0, u1, v1, c, full, -1, 0, 0);
		duaSisi(buffer, pose, r, y + h, -h, r, y + h, h, r, y - h, h, r, y - h, -h, u0, v0, u1, v1, c, full, 1, 0, 0);
	}

	private static void mata(State s, PoseStack.Pose pose, VertexConsumer buffer) {
		boolean terinjak = has(s, DindingIlusiEntity.FLAG_TERINJAK);
		boolean salah = has(s, DindingIlusiEntity.FLAG_SALAH);
		float napas = s.rinci ? 0.55F + 0.25F * Mth.sin(s.waktu * 0.12F + s.fase) : 0.7F;
		int rgb = terinjak ? 0xFFE6A0 : 0xB8D8FF;
		int alpha = Mth.clamp(Math.round(255.0F * s.pudar * (terinjak ? 1.0F : napas)), 0, 255);
		float r = 0.48F;
		float y = 0.02F;
		int full = 0xF000F0;
		// the spokes turn slowly; a stepped eye turns faster
		float a = s.rinci ? s.waktu * (terinjak ? 0.04F : 0.01F) : 0.0F;
		float cx = Mth.cos(a) * r * Mth.SQRT_OF_TWO;
		float sz = Mth.sin(a) * r * Mth.SQRT_OF_TWO;
		duaSisi(buffer, pose, -cx, y, -sz, sz, y, -cx, cx, y, sz, -sz, y, cx, 64.0F / W, 0.0F, 96.0F / W, 32.0F / H, ARGB.color(alpha, rgb), full, 0, 1, 0);
		if (salah) {
			int merah = ARGB.color(Mth.clamp(Math.round(255.0F * s.pudar * (0.6F + 0.4F * Mth.sin(s.waktu * 0.8F))), 0, 255), 0xFFFFFF);
			duaSisi(buffer, pose, -r, y + 0.01F, -r, r, y + 0.01F, -r, r, y + 0.01F, r, -r, y + 0.01F, r, 96.0F / W, 0.0F, 1.0F, 32.0F / H, merah,
					full, 0, 1, 0);
		}
		if (has(s, DindingIlusiEntity.FLAG_TERUNGKAP) && s.urutan >= 1 && s.urutan <= 3) {
			float d = 0.18F;
			float u0 = (64.0F + (s.urutan - 1) * 16.0F) / W;
			float u1 = u0 + 16.0F / W;
			duaSisi(buffer, pose, -d, y + 0.02F, -d, d, y + 0.02F, -d, d, y + 0.02F, d, -d, y + 0.02F, d, u0, 32.0F / H, u1, 48.0F / H,
					ARGB.color(alpha, 0xFFFFFF), full, 0, 1, 0);
		}
	}

	// ================================================================== quads

	private static void quad(VertexConsumer b, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2,
			float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1, int argb, int light, float nx, float ny,
			float nz) {
		b.addVertex(pose, x0, y0, z0).setColor(argb).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		b.addVertex(pose, x1, y1, z1).setColor(argb).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		b.addVertex(pose, x2, y2, z2).setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		b.addVertex(pose, x3, y3, z3).setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** A quad seen from both sides (the eyes type culls back faces). */
	private static void duaSisi(VertexConsumer b, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2,
			float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1, int argb, int light, float nx, float ny,
			float nz) {
		quad(b, pose, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, argb, light, nx, ny, nz);
		quad(b, pose, x1, y1, z1, x0, y0, z0, x3, y3, z3, x2, y2, z2, u1, v0, u0, v1, argb, light, -nx, -ny, -nz);
	}
}
