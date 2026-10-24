package dev.kultivator.client.disciple.power;

import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.client.sosok.gerak.GerakData;
import dev.kultivator.client.sosok.gerak.GerakKatalog;
import dev.kultivator.client.sosok.gerak.Klip;
import dev.kultivator.client.sosok.gerak.Sampler;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sosok.GerakId;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * The looping duty poses of the Tugas Sekte (DESIGN 20.3.9, drawn from {@code syncedActivity()}), now clips of the
 * shared Gerak catalog (DESIGN 40.5.10, 40.7.2): carrying two buckets on a pole over the shoulder (clip 5, variant
 * {@code V_JALAN_PIKUL_DIAM}), tending herbs bent over with a sickle and a basket (44 {@code V_MEMANEN_SABIT}; the old
 * bend of the shoulders is the clip's LEAN, so the head and shoulders follow the hips), stirring a wok with a ladle (23)
 * and scattering feed from a basket on the arm (51 {@code V_LEMPAR_PAKAN_LOOP}); the props stay
 * {@code DiscipleHeldItemLayer}'s. Sekte yang Hidup's activities 13-24 keep their registered function poses
 * ({@code SektePoses}); Sekte Langit's 25-27 are laid on at every return by {@code LangitDutyPosesMixin} (this method
 * keeps its signature and always returns normally). An autonomous activity with a Gerak clip (DESIGN 39, 40-60, see
 * {@code GerakKatalog.petakanAktivitas}) is played by the Gerak controller itself as layer 5; only a body without Gerak
 * data gets it here, under the same gate.
 * <p>
 * Applied after the normal animation to the disciple's body model and its robe, only while it stands still and calm:
 * never while it walks, fights or plays a cast pose (polish plan 2.6 [224]); it blends in and out over 6 ticks.
 */
public final class DutyPoses {

	private static final float BLEND_TICKS = 6.0F;

	/** Per disciple: whether the duty pose was on and when that changed (entity age). */
	private static final Map<Integer, float[]> BLENDS = new HashMap<>();

	public static void apply(PlayerModel model, AvatarRenderState avatar) {
		if (!(avatar instanceof DiscipleRenderState state)) {
			return;
		}
		int act = state.activity;
		boolean lama = legacy(act);
		boolean gerak = !lama && act >= 28 && GerakData.dari(state) == null && GerakKatalog.klipAktivitas(act) != 0;
		if (!lama && !gerak) {
			return;
		}
		Klip klipGerak = gerak ? GerakKatalog.klip(GerakKatalog.klipAktivitas(act)) : null;
		boolean duduk = klipGerak != null && klipGerak.punya(Klip.DUDUK | Klip.BERSILA);
		if (state.seated > 0.5F && !duduk) {
			return;
		}
		boolean diam = state.walkAnimationSpeed <= 0.1F || klipGerak != null && klipGerak.punya(Klip.JALAN_OK);
		boolean on = !state.castActive && (state.flags & DiscipleLook.FIGHTING) == 0 && diam;
		float s = blend(state.id, on, state.ageInTicks);
		if (s <= 0.0F) {
			return;
		}
		float t = state.ageInTicks;
		switch (act) {
			// the carrying pole over the right shoulder, the right hand steadying it, the left swinging a little
			case DiscipleEntity.ACTIVITY_WATER -> Sampler.terapkan(model, state, GerakId.JALAN_PIKUL, GerakId.V_JALAN_PIKUL_DIAM, s, t);
			// bent over the herb beds, cutting with the sickle in the right hand, the basket in the left
			case DiscipleEntity.ACTIVITY_GARDEN -> Sampler.terapkan(model, state, GerakId.MEMANEN, GerakId.V_MEMANEN_SABIT, s, t);
			// stirring the spirit rice: the ladle circles, the left hand holds the wok's handle
			case DiscipleEntity.ACTIVITY_KITCHEN -> Sampler.terapkan(model, state, GerakId.MASAK, 0, s, t);
			// scattering feed from the basket on the left arm
			case DiscipleEntity.ACTIVITY_FARM -> Sampler.terapkan(model, state, GerakId.LEMPAR_PAKAN, GerakId.V_LEMPAR_PAKAN_LOOP, s, t);
			default -> {
				if (lama) {
					// Sekte yang Hidup's activities 13-24 (DESIGN 36.3.3): the owner module's registered pose
					SektePoses.apply(model, state, s, t);
				} else {
					// an autonomous activity's Gerak clip on a body the controller does not drive
					Sampler.terapkan(model, state, GerakKatalog.klipAktivitas(act), GerakKatalog.varianAktivitas(act), s, t);
				}
			}
		}
	}

	/** The four base duties and the registered Sekte yang Hidup poses (their props are DiscipleHeldItemLayer's). */
	private static boolean legacy(int act) {
		return act >= DiscipleEntity.ACTIVITY_WATER && act <= DiscipleEntity.ACTIVITY_FARM || SektePoses.handles(act);
	}

	/** True when the duty prop should be drawn (a base duty or Sekte pose is mostly on). */
	public static boolean showsProp(DiscipleRenderState state) {
		if (!legacy(state.activity)) {
			return false;
		}
		float[] b = BLENDS.get(state.id);
		if (b == null) {
			return false;
		}
		return value(b, state.ageInTicks) > 0.5F;
	}

	private static float blend(int id, boolean on, float now) {
		float[] b = BLENDS.get(id);
		if (b == null) {
			b = new float[] {on ? 1.0F : 0.0F, now - BLEND_TICKS};
			BLENDS.put(id, b);
		} else if ((b[0] > 0.5F) != on) {
			float current = value(b, now);
			b[0] = on ? 1.0F : 0.0F;
			b[1] = now - (on ? current : 1.0F - current) * BLEND_TICKS;
		} else if (now < b[1]) {
			b[1] = now - BLEND_TICKS;
		}
		if (BLENDS.size() > 256) {
			BLENDS.clear();
		}
		return value(b, now);
	}

	private static float value(float[] b, float now) {
		float t = Mth.clamp((now - b[1]) / BLEND_TICKS, 0.0F, 1.0F);
		float v = b[0] > 0.5F ? t : 1.0F - t;
		return v * v * (3.0F - 2.0F * v);
	}

	/** The disciple's body model: the player model plus the duty poses, then the cast pose again on top. */
	public static final class Model extends PlayerModel {

		public Model(net.minecraft.client.model.geom.ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			// super already ran the cast pose (the PlayerModel mixin's TAIL); the duty pose stays off while one plays
			super.setupAnim(state);
			apply(this, state);
		}
	}

	private DutyPoses() {
	}
}
