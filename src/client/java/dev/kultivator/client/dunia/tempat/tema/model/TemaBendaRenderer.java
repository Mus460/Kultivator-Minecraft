package dev.kultivator.client.dunia.tempat.tema.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The base of every themed-town mechanism, boat and gate renderer (DESIGN 38.T.9, module 38TV-M), in the manner of the
 * 38MD {@code BendaRenderer}: one cuboid {@link EntityModel}, turned to the entity's yaw and drawn in model space exactly
 * like a mob (y down, 24 = the entity's feet), with an optional emissive pass through the vanilla {@code eyes} render type
 * (full bright, no post chain: Sodium and Iris safe). It adds the synced {@link TemaAnimasi} state, so every animation is
 * a pure function of {@code (keadaan, umur, nilai)} without per-tick packets, texture variants and a fade.
 * <p>
 * <b>Texture rule.</b> The theme textures ({@code textures/entity/tempat/tema/*.png}, tools/gen_tempat_tema_textures.py)
 * are stacks of horizontal bands of height {@code B}; each band is one material that repeats over the whole width
 * (entity textures sample with REPEAT), so a box may run past the right edge, but its depth + height must stay within
 * one band. Long pieces (masts, poles, ropes) are therefore built along x and turned into place. Decal bands (a ship's
 * eye, a gate's relief, a seal's script) are painted at the exact uv of their plate.
 * <p>
 * <b>LOD (38.T.9).</b> Beyond {@link #jauhKecil()} blocks (64) the small animations stop ({@link State#jauh}); beyond
 * {@link #batas()} (128, ships at sea 192) nothing is drawn. On Minimal effects both distances shrink by a quarter and the
 * glow pass is only drawn within 24 blocks.
 */
public abstract class TemaBendaRenderer<T extends Entity & TemaAnimasi, S extends TemaBendaRenderer.State, M extends EntityModel<S>>
		extends EntityRenderer<T, S> {

	/** The common themed state: yaw, synced animation values, LOD flag, glow and fade. */
	public static class State extends EntityRenderState {
		public float yaw;
		public int varian;
		public int keadaan;
		/** Ticks since {@link #keadaan} began, with the partial tick. */
		public float umur;
		public float nilai;
		/** Far away: small animations (ripples, flutter, sway) are frozen. */
		public boolean jauh;
		/** Game time with the partial tick (town-wide synchronous motion, e.g. the lighthouse lens). */
		public float waktu;
		/** A stable per-entity phase 0..2pi so neighbours never move in lockstep. */
		public float fase;
		/** A stable per-entity number 0..1023 (crate counts, small choices). */
		public int acak;
		/** Strength of the emissive pass 0..1. */
		public float pancar;
		/** Opacity 0..1; below 1 the model is drawn translucent (a departing ship). */
		public float alpha = 1.0F;
	}

	protected final M model;
	private final RenderType[] solid;
	private final RenderType[] fade;
	private final @Nullable RenderType[] glow;

	/**
	 * @param textures one texture per look (index from {@link #tekstur}); all share the model's uv layout
	 * @param glowTextures the matching glow textures, or null for none (a null entry = no glow for that look)
	 */
	protected TemaBendaRenderer(EntityRendererProvider.Context context, M model, Identifier[] textures, @Nullable Identifier[] glowTextures,
			float shadow) {
		super(context);
		this.model = model;
		this.solid = new RenderType[textures.length];
		this.fade = new RenderType[textures.length];
		this.glow = glowTextures == null ? null : new RenderType[glowTextures.length];
		for (int i = 0; i < textures.length; i++) {
			solid[i] = RenderTypes.entityCutout(textures[i]);
			fade[i] = RenderTypes.entityTranslucent(textures[i]);
		}
		if (glowTextures != null) {
			for (int i = 0; i < glowTextures.length; i++) {
				glow[i] = glowTextures[i] == null ? null : RenderTypes.eyes(glowTextures[i]);
			}
		}
		this.shadowRadius = shadow;
	}

	protected TemaBendaRenderer(EntityRendererProvider.Context context, M model, Identifier texture, @Nullable Identifier glowTexture, float shadow) {
		this(context, model, new Identifier[] {texture}, glowTexture == null ? null : new Identifier[] {glowTexture}, shadow);
	}

	/** A cube list on band {@code band} of a texture with bands {@code tinggi} pixels high. */
	protected static CubeListBuilder pita(int band, int tinggi) {
		return CubeListBuilder.create().texOffs(0, band * tinggi);
	}

	/** The same with a horizontal offset {@code u} (breaks the repeat of identical boxes). */
	protected static CubeListBuilder pita(int band, int tinggi, int u) {
		return CubeListBuilder.create().texOffs(u, band * tinggi);
	}

	/** Not drawn beyond this many blocks. */
	protected double batas() {
		return 128.0D;
	}

	/** Small animations stop beyond this many blocks. */
	protected double jauhKecil() {
		return 64.0D;
	}

	/** How far the model reaches beyond the entity's box (blocks), for frustum culling of wide models. */
	protected double reach(T entity) {
		return 0.5D;
	}

	/** Which texture of the constructor's list this state uses (default: the first). */
	protected int tekstur(S state) {
		return 0;
	}

	@Override
	protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(reach(entity));
	}

	@Override
	public boolean shouldRender(T entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		double max = batas() * (EffectQuality.minimal() ? 0.75D : 1.0D);
		if (entity.distanceToSqr(camX, camY, camZ) > max * max) {
			return false;
		}
		return super.shouldRender(entity, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	public void extractRenderState(T entity, S state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		long now = entity.level().getGameTime();
		state.yaw = entity.getYRot(partialTicks);
		state.varian = entity.varian();
		state.keadaan = entity.keadaan();
		state.umur = entity.umurKeadaan(now) + partialTicks;
		state.nilai = entity.nilai();
		state.waktu = (float) (now % 240000L) + partialTicks;
		int h = entity.getId() * 0x9E3779B1;
		state.acak = (h >>> 22) & 1023;
		state.fase = state.acak / 1024.0F * Mth.TWO_PI;
		double small = jauhKecil() * (EffectQuality.minimal() ? 0.75D : 1.0D);
		// distanceToCameraSq is filled by EntityRenderer.extractRenderState (extractNameTags)
		state.jauh = state.distanceToCameraSq > small * small;
		state.pancar = 0.0F;
		state.alpha = 1.0F;
		TemaModelStat.digambar(state.jauh);
	}

	@Override
	public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible && state.alpha > 0.01F) {
			int look = Mth.clamp(tekstur(state), 0, solid.length - 1);
			poseStack.pushPose();
			poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			if (state.alpha >= 0.99F) {
				collector.submitModel(model, state, poseStack, solid[look], state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			} else {
				int a = Math.round(255.0F * state.alpha);
				collector.submitModel(model, state, poseStack, fade[look], state.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.color(a, 0xFFFFFF), null,
						state.outlineColor);
			}
			RenderType g = glow == null ? null : glow[Math.min(look, glow.length - 1)];
			double glowFar = EffectQuality.minimal() ? 24.0D : 96.0D;
			if (g != null && state.pancar > 0.02F && state.distanceToCameraSq < glowFar * glowFar) {
				int alpha = Math.round(255.0F * Math.min(1.0F, state.pancar) * Math.min(1.0F, state.alpha));
				collector.order(1).submitModel(model, state, poseStack, g, state.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.color(alpha, 0xFFFFFF), null,
						state.outlineColor);
				TemaModelStat.bercahaya();
			}
			submitExtra(state, poseStack, collector);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** Extra geometry in model space (after the model, same transform); none by default. */
	protected void submitExtra(S state, PoseStack poseStack, SubmitNodeCollector collector) {
	}

	// ------------------------------------------------------------------ easing helpers for subclasses

	/** Clamped progress of {@code t} over {@code [a, a + len]}. */
	protected static float maju(float t, float a, float len) {
		return Mth.clamp((t - a) / len, 0.0F, 1.0F);
	}

	/** easeOutCubic. */
	protected static float keluar(float t) {
		float u = 1.0F - t;
		return 1.0F - u * u * u;
	}

	/** easeInCubic. */
	protected static float masuk(float t) {
		return t * t * t;
	}

	/** easeInOutSine. */
	protected static float sinus(float t) {
		return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
	}

	/** easeOutBack(1.7). */
	protected static float lewat(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = t - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	/** A damped wobble: amplitude 1 at t = 0 decaying to 0 over {@code len} ticks, {@code freq} radians per tick. */
	protected static float redam(float t, float len, float freq) {
		if (t >= len || t < 0.0F) {
			return 0.0F;
		}
		float k = 1.0F - t / len;
		return k * k * Mth.sin(t * freq);
	}

	protected static float rad(float degrees) {
		return degrees * Mth.DEG_TO_RAD;
	}
}
