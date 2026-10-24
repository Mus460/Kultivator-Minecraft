package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.tempat.model.PancarLayer;
import dev.kultivator.dunia.tempat.tema.bersama.TemaAnimasi;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * The base of the three themed-town creatures (DESIGN 38.T.9.2: {@code unta_kafilah}, {@code kalajengking_raja},
 * {@code kera_gunung}, module 38TV-M): a vanilla {@link MobRenderer} (hurt tint, death fall, shadow, name tags) that adds
 * the synced {@link TemaAnimasi} state to the render state, the 38.T.9 LOD (small animations frozen beyond
 * {@link #jauhKecil()} blocks, nothing drawn beyond {@link #batas()}; both a quarter shorter on Minimal effects), a
 * model scale, one texture per variant and an optional emissive pass through the 38MD {@link PancarLayer} (vanilla
 * {@code eyes} render type, no post chain: Sodium and Iris safe).
 */
public abstract class TemaMakhlukRenderer<T extends Mob & TemaAnimasi, S extends TemaMakhlukRenderer.State, M extends EntityModel<S>>
		extends MobRenderer<T, S, M> {

	/** The common creature state. */
	public static class State extends LivingEntityRenderState {
		public int varian;
		public int keadaan;
		/** Ticks since {@link #keadaan} began, with the partial tick. */
		public float umur;
		public float nilai;
		/** Far away: small animations (chewing, ear flicks, tail) are frozen. */
		public boolean jauh;
		/** A stable per-entity phase 0..2pi. */
		public float fase;
		/** Strength of the emissive pass 0..1. */
		public float pancar;
	}

	private final Identifier[] textures;
	private final float skala;

	protected TemaMakhlukRenderer(EntityRendererProvider.Context context, M model, float shadow, float skala, Identifier[] textures,
			@Nullable Identifier glow) {
		super(context, model, shadow);
		this.textures = textures;
		this.skala = skala;
		if (glow != null) {
			this.addLayer(new PancarLayer<>(this, glow, s -> s.pancar));
		}
	}

	protected double batas() {
		return 128.0D;
	}

	protected double jauhKecil() {
		return 64.0D;
	}

	/** Which texture of the constructor's list this state uses (default: the variant, clamped). */
	protected int tekstur(S state) {
		return Mth.clamp(state.varian, 0, textures.length - 1);
	}

	@Override
	public Identifier getTextureLocation(S state) {
		return textures[tekstur(state)];
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
		state.varian = entity.varian();
		state.keadaan = entity.keadaan();
		state.umur = entity.umurKeadaan(now) + partialTicks;
		state.nilai = entity.nilai();
		int h = entity.getId() * 0x9E3779B1;
		state.fase = ((h >>> 22) & 1023) / 1024.0F * Mth.TWO_PI;
		double small = jauhKecil() * (EffectQuality.minimal() ? 0.75D : 1.0D);
		state.jauh = state.distanceToCameraSq > small * small;
		state.pancar = 0.0F;
		TemaModelStat.digambar(state.jauh);
	}

	@Override
	protected void scale(S state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
		if (skala != 1.0F) {
			poseStack.scale(skala, skala, skala);
		}
	}

	// ------------------------------------------------------------------ easing helpers (same as TemaBendaRenderer)

	protected static float maju(float t, float a, float len) {
		return Mth.clamp((t - a) / len, 0.0F, 1.0F);
	}

	protected static float sinus(float t) {
		return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
	}

	protected static float lewat(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = t - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	protected static float rad(float degrees) {
		return degrees * Mth.DEG_TO_RAD;
	}
}
