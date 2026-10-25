package dev.kultivator.client.dunia.kota42.render.hewan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.client.dunia.tempat.tema.model.TemaMakhlukRenderer;
import dev.kultivator.dunia.kota42.hewan.HewanKotaEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The base of the six town animal renderers of 42V-H (DESIGN 42.7.5, 42.12.4): a {@link TemaMakhlukRenderer} (vanilla
 * mob renderer with hurt tint, shadow and name tags, plus the synced {@code TemaAnimasi} state) over a generated
 * {@code model/hewan/HewanMesh} mesh posed by one {@link KotaModel.Gerak} function per animal, one texture per variant.
 * <p>
 * LOD of 42.13.3: within {@link #PENUH} blocks every frame is animated; from there to 64 blocks the animation clock
 * steps in whole ticks; beyond 64 ({@code jauh}) the pose functions keep the base pose (only the variant's parts are
 * toggled). Blinking is client-only ({@link State#kedip}). A variant may change the body size ({@link #skalaVarian}):
 * the guard dog, the chick; the entity box follows on the server ({@code HewanKotaEntity#skala}). Client only.
 */
public abstract class HewanRenderer<T extends HewanKotaEntity, S extends HewanRenderer.State> extends TemaMakhlukRenderer<T, S, KotaModel<S>> {

	/** Full animation within this many blocks. */
	public static final double PENUH = 32.0D;

	/** The animal state: the creature state plus the animation clock and the client blink. */
	public static class State extends TemaMakhlukRenderer.State {
		/** Animation clock in ticks (age with the partial tick; whole ticks beyond {@link #PENUH}). */
		public float waktu;
		/** Eyes shut this frame (a client blink every ~5 s, phase per entity). */
		public boolean kedip;
		/** Model scale of the variant. */
		public float skalaVarian = 1.0F;
	}

	private final float skalaModel;

	protected HewanRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, KotaModel.Gerak<S> gerak, float shadow, float skalaModel,
			Identifier[] textures) {
		super(context, new KotaModel<>(context.bakeLayer(layer), gerak), shadow, 1.0F, textures, null);
		this.skalaModel = skalaModel;
	}

	/** Model scale of a variant (1 = the mesh as generated). */
	protected float skalaVarian(int varian) {
		return 1.0F;
	}

	@Override
	public void extractRenderState(T entity, S state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float age = state.ageInTicks;
		if (state.distanceToCameraSq > PENUH * PENUH) {
			// shortcut: 32-64 blocks steps the clock in whole ticks (about every 2nd-3rd frame at 60 fps) rather than a true
			// every-2nd-frame update; upgrade to a per-entity frame counter if the stepping ever shows
			age = Mth.floor(age);
			state.umur = Mth.floor(state.umur);
		}
		state.waktu = age;
		state.kedip = ((int) (age + state.fase * 16.0F)) % 97 < 3;
		state.skalaVarian = skalaVarian(state.varian);
	}

	@Override
	protected void scale(S state, PoseStack poseStack) {
		float f = skalaModel * state.skalaVarian;
		if (f != 1.0F) {
			poseStack.scale(f, f, f);
		}
	}

	// ------------------------------------------------------------------ shared motion helpers

	/** Looks with the head part: yaw and pitch of the render state, scaled by {@code bagian} (0..1). */
	static void tengok(ModelPart kepala, State s, float bagian) {
		kepala.yRot += s.yRot * Mth.DEG_TO_RAD * bagian;
		kepala.xRot += s.xRot * Mth.DEG_TO_RAD * bagian;
	}

	/** A trot of four legs (diagonal pairs together) from the walk animation; returns the swing phase used. */
	static float derap(ModelPart dk, ModelPart dkn, ModelPart bk, ModelPart bkn, State s, float frekuensi, float amp) {
		float laju = Math.min(1.0F, s.walkAnimationSpeed);
		float a = Mth.cos(s.walkAnimationPos * frekuensi) * amp * laju;
		dk.xRot += a;
		bkn.xRot += a;
		dkn.xRot -= a;
		bk.xRot -= a;
		return s.walkAnimationPos * frekuensi;
	}

	/** A tail sway: {@code amp} radians of yaw at {@code kecepatan} radians per tick, phase per entity. */
	static float kibas(State s, float amp, float kecepatan) {
		return Mth.sin(s.waktu * kecepatan + s.fase) * amp;
	}

	/** An occasional ear flick 0..1 (a short twitch every few seconds, phase per entity and side). */
	static float kedut(State s, float geser) {
		float t = (s.waktu + s.fase * 31.0F + geser) % 73.0F;
		return t < 6.0F ? Mth.sin(t / 6.0F * Mth.PI) : 0.0F;
	}

	/** A pulse train: 0..1..0 over {@code naik} ticks at the start of every {@code periode} ticks of {@code t}. */
	static float denyut(float t, float periode, float naik) {
		float u = t % periode;
		return u < naik ? Mth.sin(u / naik * Mth.PI) : 0.0F;
	}

	/** Linear blend of {@code a} towards {@code b}. */
	static float campur(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
