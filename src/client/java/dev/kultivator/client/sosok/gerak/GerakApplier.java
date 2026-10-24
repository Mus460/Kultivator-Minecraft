package dev.kultivator.client.sosok.gerak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sosok.SosokDetail;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * Puts Gerak's own channels on a {@link PlayerModel} (DESIGN 40.5.1-40.5.2): called by the PlayerModel mixin after
 * {@code PoseApplier} for every PlayerModel of a body (skin, outfit cubes, old robe, gear), so nothing is left behind.
 * The 18 PoseLibrary channels already went through the snapshot; here come the head roll (HZ), the hip sway (BZ, the
 * body rolls about the hips, shoulders and head ride along, the feet shift against it), the shoulder raise (BAHU), the
 * walk profile's arm and limp multipliers, the additive offsets (gaze, breath, glances), and while the meditation seat
 * holds the body the seated clip's arms and head (PoseApplier skips the snapshot then). Does nothing for a render
 * state without {@link GerakData} (players, mirror shadows, heart demons).
 * <p>
 * {@link #angkat} (bob, float, spin) and {@link #putarTidur} (lying down) are for the renderers' {@code setupRotations}.
 */
public final class GerakApplier {

	private static final float SHOULDER_X = 5.0F;
	private static final float SHOULDER_Y = 2.0F;
	private static final float HIP_Y = 12.0F;
	private static final float MODEL_SCALE = 0.9375F;

	/** From the PlayerModel mixin, after {@code PoseApplier.apply}. */
	public static void apply(PlayerModel model, AvatarRenderState state) {
		GerakData d = GerakData.dari(state);
		if (d == null) {
			return;
		}
		boolean meditasi = MeditationPose.strength(state) > 0.5F;
		if (meditasi) {
			duduk(model, d);
		}
		boolean walking = state.walkAnimationSpeed > 0.01F && !meditasi && !state.isPassenger;
		if (walking) {
			ayun(model, state, d);
		}
		if (d.langkah != null && !meditasi && !state.isPassenger && d.tidurBobot < 0.5F && state.swimAmount <= 0.0F) {
			// the shared polish (players use the same code): only what the walk profile does not already do
			boolean lengan = !d.lenganDipose && !d.aktivitas;
			Langkah.terapkan(model, state, d.langkah, SosokDetail.jalanKuat(), false, lengan, lengan, lengan);
		}
		float[] t = d.tambah;
		model.head.xRot += t[Klip.HX];
		model.head.yRot += t[Klip.HY];
		model.rightArm.xRot += t[Klip.RAX];
		model.leftArm.xRot += t[Klip.LAX];
		model.rightArm.zRot += t[Klip.RAZ];
		model.leftArm.zRot += t[Klip.LAZ];
		if (t[Klip.TWIST] != 0.0F && !meditasi) {
			putarBadan(model, t[Klip.TWIST]);
		}
		if (d.hz != 0.0F) {
			model.head.zRot += d.hz;
		}
		if (d.bz != 0.0F && !meditasi) {
			goyangPinggul(model, d.bz);
		}
		if (d.bahu != 0.0F) {
			model.rightArm.y -= d.bahu;
			model.leftArm.y -= d.bahu;
		}
	}

	/** The seated clip's arms and head while sitting on the meditation seat. */
	private static void duduk(PlayerModel model, GerakData d) {
		float[] v = d.duduk;
		float[] w = d.dudukW;
		lerp(model.rightArm, w[Klip.RAX], v[Klip.RAX], w[Klip.RAY], v[Klip.RAY], w[Klip.RAZ], v[Klip.RAZ]);
		lerp(model.leftArm, w[Klip.LAX], v[Klip.LAX], w[Klip.LAY], v[Klip.LAY], w[Klip.LAZ], v[Klip.LAZ]);
		model.head.xRot += v[Klip.HX] * w[Klip.HX];
		model.head.yRot += v[Klip.HY] * w[Klip.HY];
		if (w[Klip.LEAN] > 0.0F) {
			float lean = v[Klip.LEAN] * w[Klip.LEAN];
			bendBody(model, lean, model.body.yRot, w[Klip.RAX] <= 0.0F, false, w[Klip.LAX] <= 0.0F, false);
		}
	}

	private static void lerp(ModelPart p, float wx, float x, float wy, float y, float wz, float z) {
		if (wx > 0.0F) {
			p.xRot = Mth.lerp(wx, p.xRot, x);
		}
		if (wy > 0.0F) {
			p.yRot = Mth.lerp(wy, p.yRot, y);
		}
		if (wz > 0.0F) {
			p.zRot = Mth.lerp(wz, p.zRot, z);
		}
	}

	/** The walk profile's arm swing (relative to the legs) and the limp, on top of vanilla's swing formula. */
	private static void ayun(PlayerModel model, AvatarRenderState state, GerakData d) {
		float pos = state.walkAnimationPos * 0.6662F;
		float speed = state.walkAnimationSpeed / Math.max(0.0001F, state.speedValue);
		if (d.ayunLengan != 1.0F && !d.lenganDipose) {
			float k = d.ayunLengan - 1.0F;
			model.rightArm.xRot += k * Mth.cos(pos + Mth.PI) * speed;
			model.leftArm.xRot += k * Mth.cos(pos) * speed;
		}
		if (d.ayunKakiKanan != 1.0F) {
			model.rightLeg.xRot *= d.ayunKakiKanan;
		}
		if (d.profil == PENARI) {
			// the dancer glides on the toes: the backward kick of each leg is cut short (40.5.6)
			if (model.rightLeg.xRot > 0.0F) {
				model.rightLeg.xRot *= 0.35F;
			}
			if (model.leftLeg.xRot > 0.0F) {
				model.leftLeg.xRot *= 0.35F;
			}
		}
	}

	private static final int PENARI = ProfilJalan.PENARI.ordinal();

	/** The hip sway: the body rolls about the hip line, head and shoulders ride along, the legs shift the other way. */
	static void goyangPinggul(PlayerModel model, float a) {
		float sa = Mth.sin(a);
		float turun = HIP_Y * (1.0F - Mth.cos(a));
		float geser = HIP_Y * sa;
		model.body.zRot += a;
		model.body.x += geser;
		model.body.y += turun;
		model.head.x += geser;
		model.head.y += turun;
		model.head.zRot -= a * 0.5F;
		for (int side = -1; side <= 1; side += 2) {
			ModelPart arm = side < 0 ? model.rightArm : model.leftArm;
			float s = side * SHOULDER_X;
			arm.x += geser + s * (Mth.cos(a) - 1.0F) - SHOULDER_Y * sa;
			arm.y += turun + s * sa;
			arm.zRot += a;
		}
		float kaki = -a * 6.0F;
		model.rightLeg.x += kaki;
		model.leftLeg.x += kaki;
	}

	/** A small extra body twist (the gaze past 40 degrees): arms turn with the torso. */
	static void putarBadan(PlayerModel model, float twist) {
		model.body.yRot += twist;
		model.rightArm.yRot += twist;
		model.leftArm.yRot += twist;
		float c = Mth.cos(twist);
		float s = Mth.sin(twist);
		putarPivot(model.rightArm, c, s);
		putarPivot(model.leftArm, c, s);
	}

	private static void putarPivot(ModelPart arm, float c, float s) {
		float x = arm.x;
		float z = arm.z;
		arm.x = x * c + z * s;
		arm.z = -x * s + z * c;
	}

	/**
	 * Lays sampled channels {@code v} (24, NaN = not driven) over the model with weight {@code s}: arms and legs to the
	 * absolute angles (legs only when {@code jalan} and {@code meditasi} are false), head channels and the hip sway and
	 * shoulder raise add, lean and twist bend the body about the hips. Used for the duty and activity poses
	 * ({@code Sampler.terapkan} from {@code DutyPoses}).
	 */
	static void letakkan(PlayerModel model, float[] v, float s, boolean jalan, boolean meditasi) {
		boolean rx = !Float.isNaN(v[Klip.RAX]);
		boolean ry = !Float.isNaN(v[Klip.RAY]);
		boolean lx = !Float.isNaN(v[Klip.LAX]);
		boolean ly = !Float.isNaN(v[Klip.LAY]);
		bagian(model.rightArm, s, v[Klip.RAX], v[Klip.RAY], v[Klip.RAZ]);
		bagian(model.leftArm, s, v[Klip.LAX], v[Klip.LAY], v[Klip.LAZ]);
		if (!jalan && !meditasi) {
			bagian(model.rightLeg, s, v[Klip.RLX], v[Klip.RLY], v[Klip.RLZ]);
			bagian(model.leftLeg, s, v[Klip.LLX], v[Klip.LLY], v[Klip.LLZ]);
		}
		if (!Float.isNaN(v[Klip.HX])) {
			model.head.xRot += v[Klip.HX] * s;
		}
		if (!Float.isNaN(v[Klip.HY])) {
			model.head.yRot += v[Klip.HY] * s;
		}
		if (!Float.isNaN(v[Klip.HZ])) {
			model.head.zRot += v[Klip.HZ] * s;
		}
		boolean lean = !Float.isNaN(v[Klip.LEAN]);
		boolean twist = !Float.isNaN(v[Klip.TWIST]);
		if ((lean || twist) && !meditasi) {
			float l = lean ? Mth.lerp(s, model.body.xRot, v[Klip.LEAN]) : model.body.xRot;
			float t = twist ? Mth.lerp(s, model.body.yRot, v[Klip.TWIST]) : model.body.yRot;
			if (jalan) {
				l *= 0.5F;
			}
			bendBody(model, l, t, !rx, !ry, !lx, !ly);
		}
		if (!Float.isNaN(v[Klip.BZ]) && !meditasi) {
			goyangPinggul(model, v[Klip.BZ] * s);
		}
		if (!Float.isNaN(v[Klip.BAHU])) {
			model.rightArm.y -= v[Klip.BAHU] * s;
			model.leftArm.y -= v[Klip.BAHU] * s;
		}
	}

	private static void bagian(ModelPart p, float s, float x, float y, float z) {
		if (!Float.isNaN(x)) {
			p.xRot = Mth.lerp(s, p.xRot, x);
		}
		if (!Float.isNaN(y)) {
			p.yRot = Mth.lerp(s, p.yRot, y);
		}
		if (!Float.isNaN(z)) {
			p.zRot = Mth.lerp(s, p.zRot, z);
		}
	}

	/**
	 * Turns the body by {@code lean} (forward, about the hips) and {@code twist} and moves the head and shoulder pivots
	 * with it; arms not driven follow the torso (same geometry as {@code PoseApplier}'s private bend, which this module
	 * may not edit).
	 */
	static void bendBody(PlayerModel model, float lean, float twist, boolean rightXFollows, boolean rightYFollows,
			boolean leftXFollows, boolean leftYFollows) {
		float leanDelta = lean - model.body.xRot;
		float twistDelta = twist - model.body.yRot;
		float sa = Mth.sin(lean), ca = Mth.cos(lean);
		float st = Mth.sin(twist), ct = Mth.cos(twist);
		float nx = -HIP_Y * sa * st;
		float ny = HIP_Y - HIP_Y * ca;
		float nz = -HIP_Y * sa * ct;
		model.body.xRot = lean;
		model.body.yRot = twist;
		model.body.x = nx;
		model.body.y = ny;
		model.body.z = nz;
		model.head.x = nx;
		model.head.y = ny;
		model.head.z = nz;
		for (int side = -1; side <= 1; side += 2) {
			float s = side * SHOULDER_X;
			ModelPart arm = side < 0 ? model.rightArm : model.leftArm;
			arm.x = nx + s * ct + SHOULDER_Y * sa * st;
			arm.y = ny + SHOULDER_Y * ca;
			arm.z = nz - s * st + SHOULDER_Y * sa * ct;
		}
		if (rightXFollows) {
			model.rightArm.xRot += leanDelta;
		}
		if (leftXFollows) {
			model.leftArm.xRot += leanDelta;
		}
		if (rightYFollows) {
			model.rightArm.yRot += twistDelta;
		}
		if (leftYFollows) {
			model.leftArm.yRot += twistDelta;
		}
	}

	// ================================================================== whole-body moves (renderers' setupRotations)

	/**
	 * At the start of {@code setupRotations} (after {@code MeditationPose.lower} and {@code PoseApplier.lift}, before
	 * super): the bob of the walk and the breath, the float of immortals and ghosts, the spin of the dance.
	 */
	public static void angkat(AvatarRenderState state, PoseStack poseStack) {
		GerakData d = GerakData.dari(state);
		if (d == null) {
			return;
		}
		float naik = d.bob / 16.0F * MODEL_SCALE + d.melayang;
		if (naik != 0.0F && Float.isFinite(naik)) {
			poseStack.translate(0.0F, Mth.clamp(naik, -0.5F, 0.6F), 0.0F);
		}
		if (d.spin != 0.0F && Float.isFinite(d.spin)) {
			poseStack.rotateDegrees(Axis.YP, -d.spin);
		}
	}

	/**
	 * How far {@code setupRotations} raised the whole body this frame, in model blocks (the meditation seat's lowering,
	 * the snapshot's {@code LIFT} as {@code PoseApplier.lift} reads it, and {@link #angkat}'s bob and float, divided by
	 * the player model scale). Props that stand on the ground ({@code DEPAN_KAKI}) move back down by it, so a squatting
	 * child's top or a seated monk's wooden fish stays on the floor instead of sinking with the hips.
	 */
	public static float naikModel(AvatarRenderState state) {
		float naik = 0.0F;
		float med = MeditationPose.strength(state);
		if (med > 0.0F) {
			naik -= MeditationPose.LOWER * med;
		}
		dev.kultivator.client.anim.CastPoses.Snapshot pose = dev.kultivator.client.anim.PoseRenderData.pose(state);
		if (pose != null && !(med > 0.5F && pose.pose() != dev.kultivator.technique.spec.dsl.Pose.MEDITATE_FLOAT
				&& pose.pose() != dev.kultivator.technique.spec.dsl.Pose.FLAME_CUP)) {
			float lift = pose.body(Klip.LIFT);
			if (Float.isFinite(lift)) {
				naik += Mth.clamp(lift, -0.8F, 1.2F);
			}
		}
		GerakData d = GerakData.dari(state);
		if (d != null) {
			float a = d.bob / 16.0F * MODEL_SCALE + d.melayang;
			if (Float.isFinite(a)) {
				naik += Mth.clamp(a, -0.5F, 0.6F);
			}
		}
		return naik / MODEL_SCALE;
	}

	/** True when the body lies down this frame (clip 13 or another lying clip at more than half weight). */
	public static boolean tidur(AvatarRenderState state) {
		GerakData d = GerakData.dari(state);
		return d != null && d.tidur;
	}

	/**
	 * At the end of {@code setupRotations} (the facing frame, feet at the origin): lays the body on its back, the head
	 * behind the feet (the bed point of DESIGN 38 puts the feet at the foot of the bed), on its side for variant 1. The
	 * entity never calls {@code startSleeping} (that would change the bed block).
	 */
	public static void putarTidur(AvatarRenderState state, PoseStack poseStack) {
		GerakData d = GerakData.dari(state);
		if (d == null || d.tidurBobot <= 0.0F) {
			return;
		}
		float w = d.tidurBobot;
		poseStack.translate(0.0F, 0.18F * w, 0.0F);
		poseStack.rotateDegrees(Axis.XP, 90.0F * w);
		if (d.klip == dev.kultivator.sosok.GerakId.TIDUR && d.varian == dev.kultivator.sosok.GerakId.V_TIDUR_MIRING) {
			poseStack.rotateDegrees(Axis.YP, 90.0F * w);
		}
	}

	private GerakApplier() {
	}
}
