package dev.kultivator.duel.blade;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static dev.kultivator.duel.blade.DuelPoseTable.LAX;
import static dev.kultivator.duel.blade.DuelPoseTable.LAY;
import static dev.kultivator.duel.blade.DuelPoseTable.LAZ;
import static dev.kultivator.duel.blade.DuelPoseTable.LEAN;
import static dev.kultivator.duel.blade.DuelPoseTable.RAX;
import static dev.kultivator.duel.blade.DuelPoseTable.RAY;
import static dev.kultivator.duel.blade.DuelPoseTable.RAZ;
import static dev.kultivator.duel.blade.DuelPoseTable.TWIST;
import static dev.kultivator.duel.blade.DuelPoseTable.WRIST;

/**
 * Forward kinematics of the held blade (DESIGN 35.5.3, contract C35.A2): pure {@code org.joml}, no client class, safe on
 * the server every tick. It repeats the 26.3 render chain exactly (verified in mcsrc):
 * <ol>
 * <li>{@code LivingEntityRenderer.submit}: {@code scale(entityScale)}, {@code setupRotations} (our lift first, then
 * {@code rotateDegrees(YP, 180 − bodyRot)}, then our tilt {@code rotate(XP, −tilt)}), {@code scale(−1, −1, 1)}, the
 * renderer's {@code scale} (players, disciples and rogues: 0.9375), {@code translate(0, −1.501, 0)};</li>
 * <li>{@code PlayerModel.translateToHand}: {@code root} (identity) and the arm part, slim arms 0.5 px inward; the arm's
 * pivot is where {@code PoseApplier.bendBody} put it (the shoulder moved by LEAN and TWIST) and its rotation is
 * {@code rotationZYX(zRot, yRot, xRot)} followed by the wrist roll about the arm's own long axis (y);</li>
 * <li>{@code ItemInHandLayer}: {@code rotateDegrees(XP, −90)}, {@code rotateDegrees(YP, 180)},
 * {@code translate(±1, 2, −10)/16};</li>
 * <li>the {@code handheld} third-person display ({@code ItemTransform.apply}): {@code translate(0, 4, 0.5)/16},
 * {@code rotationXYZ(0, −90°, 55°)} (left hand: y and z negated), {@code scale(0.85)}, then the item-centred point.</li>
 * </ol>
 * The disciple's 3D sword replaces 3-4 with {@code DiscipleHeldItemLayer}'s {@code translate(−1, 10, −1)/16},
 * {@code rotateDegrees(XP, 90)}, {@code scale(0.8)} and {@code FormModels}' {@code rotateDegrees(ZP, 180)}.
 * <p>
 * The wrist roll is not a vanilla channel: the client folds it into the arm's Euler angles ({@link #foldWrist}) so the
 * drawn arm and item carry it, and this class applies the same rotation directly, so both agree to the float.
 */
public final class BladeKinematics {

	/** The renderer scale of every PlayerModel renderer (players, disciples, rogues). */
	public static final float PLAYER_SCALE = 0.9375F;
	/** {@code LivingEntityRenderer}'s model offset. */
	public static final float MODEL_OFFSET_Y = -1.501F;
	private static final float SHOULDER_X = 5.0F;
	private static final float SHOULDER_Y = 2.0F;
	private static final float HIP_Y = 12.0F;
	private static final float DEG = (float) (Math.PI / 180.0D);

	/**
	 * The model root in the entity's facing frame (feet at the origin, after the yaw turn and the tilt; +y up, the entity
	 * faces −z, its right side is +x).
	 */
	public static Matrix4f modelRoot(float modelScale, Matrix4f out) {
		return out.identity().scale(-1.0F, -1.0F, 1.0F).scale(modelScale).translate(0.0F, MODEL_OFFSET_Y, 0.0F);
	}

	/**
	 * The arm's pivot in model pixels, moved with the body bend of {@code PoseApplier.bendBody} (lean forward about the
	 * hips, twist about the spine) and the slim arms' half pixel of {@code PlayerModel.translateToHand}.
	 */
	public static Vector3f shoulder(float lean, float twist, boolean left, boolean slim, Vector3f out) {
		float sa = (float) Math.sin(lean), ca = (float) Math.cos(lean);
		float st = (float) Math.sin(twist), ct = (float) Math.cos(twist);
		float nx = -HIP_Y * sa * st;
		float ny = HIP_Y - HIP_Y * ca;
		float nz = -HIP_Y * sa * ct;
		float s = left ? SHOULDER_X : -SHOULDER_X;
		float px = nx + s * ct + SHOULDER_Y * sa * st;
		float py = ny + SHOULDER_Y * ca;
		float pz = nz - s * st + SHOULDER_Y * sa * ct;
		if (slim) {
			px += left ? -0.5F : 0.5F;
		}
		return out.set(px, py, pz);
	}

	/**
	 * The hand frame of the right (or left) arm in the entity's facing frame for the duel channels {@code ch}
	 * ({@link DuelPoseTable#CHANNELS} values, radians, the wrist not yet folded). {@code modelScale} is the renderer
	 * scale ({@link #PLAYER_SCALE}).
	 */
	public static Matrix4f hand(float[] ch, boolean slim, boolean left, float modelScale, Matrix4f out) {
		modelRoot(modelScale, out);
		Vector3f pivot = shoulder(value(ch, LEAN), value(ch, TWIST), left, slim, new Vector3f());
		out.translate(pivot.x / 16.0F, pivot.y / 16.0F, pivot.z / 16.0F);
		float x = value(ch, left ? LAX : RAX);
		float y = value(ch, left ? LAY : RAY);
		float z = value(ch, left ? LAZ : RAZ);
		out.rotate(new Quaternionf().rotationZYX(z, y, x));
		float wrist = value(ch, WRIST);
		if (wrist != 0.0F) {
			// the wrist belongs to the main hand only: the off hand of a one-handed pose does not roll
			out.rotateY(wrist);
		}
		return out;
	}

	/** {@link #hand(float[], boolean, boolean, float, Matrix4f)} with the player renderer scale. */
	public static Matrix4f hand(float[] ch, boolean slim, boolean left, Matrix4f out) {
		return hand(ch, slim, left, PLAYER_SCALE, out);
	}

	/** Applies {@code ItemInHandLayer} and the handheld third-person display to a hand frame (in place). */
	public static Matrix4f item(boolean left, Matrix4f handFrame) {
		handFrame.rotateX(-90.0F * DEG);
		handFrame.rotateY(180.0F * DEG);
		handFrame.translate((left ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
		handFrame.translate(0.0F, 4.0F / 16.0F, 0.5F / 16.0F);
		float side = left ? -1.0F : 1.0F;
		handFrame.rotate(new Quaternionf().rotationXYZ(0.0F, side * -90.0F * DEG, side * 55.0F * DEG));
		handFrame.scale(0.85F);
		return handFrame;
	}

	/** Applies the disciple's 3D sword transform ({@code DiscipleHeldItemLayer.swordTransform} + the 180° roll). */
	public static Matrix4f sword3d(Matrix4f handFrame) {
		handFrame.translate(-1.0F / 16.0F, 10.0F / 16.0F, -1.0F / 16.0F);
		handFrame.rotateX(90.0F * DEG);
		handFrame.scale(0.8F);
		handFrame.rotateZ(180.0F * DEG);
		return handFrame;
	}

	/** Moves a hand frame onto the item of {@code shape} (nothing for the fist, whose shape is in the arm frame). */
	public static Matrix4f toItem(BladeShape shape, boolean left, Matrix4f handFrame) {
		return switch (shape.hold()) {
			case ITEM -> item(left, handFrame);
			case SWORD_3D -> sword3d(handFrame);
			case ARM -> handFrame;
		};
	}

	/**
	 * The blade's base and point in the frame of {@code itemFrame} (a hand frame already moved by {@link #toItem}).
	 * Writes into {@code grip} and {@code tip}.
	 */
	public static void blade(BladeShape shape, boolean left, Matrix4f itemFrame, Vector3f grip, Vector3f tip) {
		grip.set(shape.grip());
		tip.set(shape.tip());
		if (shape.hold() == BladeShape.Hold.ARM && left) {
			grip.x = -grip.x;
			tip.x = -tip.x;
		}
		itemFrame.transformPosition(grip);
		itemFrame.transformPosition(tip);
	}

	// ------------------------------------------------------------------------------------------ the wrist on the model

	/**
	 * Folds the wrist roll into the main arm's Euler angles (in place): the arm's {@code rotationZYX(z, y, x)} followed
	 * by a turn of {@code ch[WRIST]} about its long axis becomes one {@code rotationZYX(z', y', x')}, the solution
	 * closest to the unrolled angles (so blending from the vanilla arm never takes the long way round). {@code left}
	 * names the arm that holds the blade. The WRIST channel itself is left as it is.
	 */
	public static void foldWrist(float[] ch, boolean left) {
		float wrist = value(ch, WRIST);
		if (wrist == 0.0F) {
			return;
		}
		int cx = left ? LAX : RAX;
		int cy = left ? LAY : RAY;
		int cz = left ? LAZ : RAZ;
		float x = value(ch, cx), y = value(ch, cy), z = value(ch, cz);
		float[] r = rotZyx(z, y, x);
		float[] ry = rotY(wrist);
		float[] m = mul(r, ry);
		// m = Rz Ry Rx: m20 = -sin y, m21 = cos y sin x, m22 = cos y cos x, m10 = cos y sin z, m00 = cos y cos z
		float sy = clamp(-m[6], -1.0F, 1.0F);
		float y1 = (float) Math.asin(sy);
		float x1;
		float z1;
		if (Math.abs(sy) > 0.9999F) {
			// gimbal lock (y = ±90°): only x ∓ z is defined; keep the old z and put the rest into x
			z1 = z;
			x1 = sy > 0 ? (float) Math.atan2(m[1], m[4]) + z : (float) Math.atan2(-m[1], m[4]) - z;
		} else {
			x1 = (float) Math.atan2(m[7], m[8]);
			z1 = (float) Math.atan2(m[3], m[0]);
		}
		// the other Euler solution of the same rotation
		float x2 = x1 + (float) Math.PI;
		float y2 = (float) Math.PI - y1;
		float z2 = z1 + (float) Math.PI;
		x1 = near(x1, x);
		y1 = near(y1, y);
		z1 = near(z1, z);
		x2 = near(x2, x);
		y2 = near(y2, y);
		z2 = near(z2, z);
		float d1 = Math.abs(x1 - x) + Math.abs(y1 - y) + Math.abs(z1 - z);
		float d2 = Math.abs(x2 - x) + Math.abs(y2 - y) + Math.abs(z2 - z);
		boolean second = d2 < d1;
		ch[cx] = second ? x2 : x1;
		ch[cy] = second ? y2 : y1;
		ch[cz] = second ? z2 : z1;
	}

	/**
	 * The arm angles {@code (xRot, yRot)} (zRot 0) that point an arm from its pivot {@code shoulder} towards
	 * {@code target}, both in model pixels (y down): used to bring the off hand to the hilt of a two-handed grip.
	 * Writes {@code {x, y}} into {@code out}.
	 */
	public static void aim(Vector3f shoulder, Vector3f target, float[] out) {
		float dx = target.x - shoulder.x;
		float dy = target.y - shoulder.y;
		float dz = target.z - shoulder.z;
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-4F) {
			out[0] = 0.0F;
			out[1] = 0.0F;
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		// Ry(y) Rx(x) (0, 1, 0) = (sin x sin y, cos x, sin x cos y), with x <= 0 (the arm raised forward)
		float x = -(float) Math.acos(clamp(dy, -1.0F, 1.0F));
		float sx = (float) Math.sin(x);
		float y = Math.abs(sx) < 1.0E-4F ? 0.0F : (float) Math.atan2(dx / sx, dz / sx);
		out[0] = x;
		out[1] = y;
	}

	// ------------------------------------------------------------------------------------------ small 3x3 helpers

	/** Row-major 3x3 of {@code Rz(z) Ry(y) Rx(x)}. */
	private static float[] rotZyx(float z, float y, float x) {
		return mul(mul(rotZ(z), rotY(y)), rotX(x));
	}

	private static float[] rotX(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {1, 0, 0, 0, c, -s, 0, s, c};
	}

	private static float[] rotY(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
	}

	private static float[] rotZ(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
	}

	private static float[] mul(float[] a, float[] b) {
		float[] m = new float[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				m[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
			}
		}
		return m;
	}

	/** {@code angle} moved by whole turns to lie within π of {@code reference}. */
	private static float near(float angle, float reference) {
		float twoPi = (float) (Math.PI * 2.0D);
		float d = angle - reference;
		d -= twoPi * Math.round(d / twoPi);
		return reference + d;
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	private static float value(float[] ch, int c) {
		if (c >= ch.length) {
			return 0.0F;
		}
		float v = ch[c];
		return Float.isNaN(v) ? 0.0F : v;
	}

	private BladeKinematics() {
	}
}
