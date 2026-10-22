package dev.kultivator.duel.blade;

import dev.kultivator.duel.clash.WeaponClass;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The blade of a fighter in world space at a moment of a pose (DESIGN 35.5.3, contract C35.A2): pose keys from
 * {@link DuelPoseTable}, the arm and item chain from {@link BladeKinematics}, the blade from {@link BladeShape}. Pure
 * math with no client class: the server calls it for its arc tests ({@code HitTest.sweep}) and contact anchors
 * ({@code Exchange}); the client calls it for blades it cannot see ({@code BladeTracker}'s fallback). Thread-safe (scratch
 * per thread), allocation-light: well under the 2 µs per sample the design allows.
 * <p>
 * <b>Normalised time</b> {@code t01}: 0 is the start of the pose, {@link #IMPACT_T01} (0.5) the impact (the moment of
 * contact of a cut: its strike key), 1 the end of the follow-through. The first half covers the windup and strike, the
 * second half the follow-through; {@link #t01(DuelPose, float, int)} converts ticks of a real swing.
 * <p>
 * <b>Reach.</b> The drawn blade of a vanilla-sized sword reaches about 1.2-1.3 blocks ahead of the feet at contact.
 * Rules that want the weapon class's longer reach (3 blocks for a sword) test the {@link #extend extended} segment, which
 * continues the same blade line; sparks and anchors use the real one.
 */
public final class BladeArcs {

	/** {@code t01} of the impact (the strike key of a cut). */
	public static final float IMPACT_T01 = 0.5F;

	private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

	private static final class Scratch {
		final float[] channels = new float[DuelPoseTable.CHANNELS];
		final Matrix4f matrix = new Matrix4f();
		final Matrix4f world = new Matrix4f();
		final Vector3f grip = new Vector3f();
		final Vector3f tip = new Vector3f();
	}

	/**
	 * The blade at {@code t01} of {@code pose} (contract C35.A2).
	 *
	 * @param feet       the entity's position (feet, world)
	 * @param bodyYaw    the body yaw in degrees ({@code yBodyRot}; the model turns with the body, not the head)
	 * @param scale      the entity's scale attribute (1 for adults)
	 * @param slim       slim arms (the player's model type; disciples and rogues by their skin)
	 * @param leftHanded main arm LEFT: the pose is mirrored and the blade is in the left hand
	 */
	public static BladeSegment sample(DuelPose pose, WeaponClass weapon, float t01, Vec3 feet, float bodyYaw, float scale, boolean slim,
			boolean leftHanded) {
		return sampleTicks(pose, weapon, ticks(pose, t01, -1), -1, feet, bodyYaw, scale, slim, leftHanded);
	}

	/**
	 * The blade {@code ticks} after the start of a swing whose impact comes {@code windup} ticks after its start (the
	 * server's real windup; {@code <= 0} for the pose's canonical one). The same sampler the client draws with.
	 */
	public static BladeSegment sampleTicks(DuelPose pose, WeaponClass weapon, float ticks, int windup, Vec3 feet, float bodyYaw, float scale,
			boolean slim, boolean leftHanded) {
		Scratch s = SCRATCH.get();
		DuelPoseTable.sample(pose, ticks, windup, s.channels);
		return fromChannels(s.channels, weapon, feet, bodyYaw, scale, slim, leftHanded);
	}

	/**
	 * The blade for raw duel channels ({@link DuelPoseTable#CHANNELS}, right-handed as authored; mirrored here for
	 * left-handed fighters). Lift and tilt are applied as {@code PoseApplier} does.
	 */
	public static BladeSegment fromChannels(float[] channels, WeaponClass weapon, Vec3 feet, float bodyYaw, float scale, boolean slim,
			boolean leftHanded) {
		Scratch s = SCRATCH.get();
		float[] ch = s.channels;
		if (ch != channels) {
			System.arraycopy(channels, 0, ch, 0, Math.min(channels.length, ch.length));
		}
		if (leftHanded) {
			DuelPoseTable.mirror(ch);
		}
		BladeShape shape = BladeShape.of(weapon);
		Matrix4f hand = BladeKinematics.hand(ch, slim, leftHanded, BladeKinematics.PLAYER_SCALE, s.matrix);
		BladeKinematics.toItem(shape, leftHanded, hand);
		BladeKinematics.blade(shape, leftHanded, hand, s.grip, s.tip);
		Matrix4f world = facing(ch, feet, bodyYaw, scale, s.world);
		world.transformPosition(s.grip);
		world.transformPosition(s.tip);
		return new BladeSegment(new Vec3(s.grip.x, s.grip.y, s.grip.z), new Vec3(s.tip.x, s.tip.y, s.tip.z));
	}

	/**
	 * The entity's facing frame in the world: feet, scale, the pose's lift, the body yaw ({@code 180 − bodyYaw}, as
	 * {@code LivingEntityRenderer.setupRotations}) and the pose's forward tilt. Relative to the feet when {@code feet}
	 * is the zero vector.
	 */
	public static Matrix4f facing(float[] ch, Vec3 feet, float bodyYaw, float scale, Matrix4f out) {
		float lift = clamp(value(ch, DuelPoseTable.LIFT), -0.8F, 1.2F);
		float tilt = clamp(value(ch, DuelPoseTable.TILT), -1.0F, 1.0F);
		out.identity().translate((float) feet.x, (float) feet.y, (float) feet.z);
		out.scale(scale);
		out.translate(0.0F, lift, 0.0F);
		out.rotateY((float) Math.toRadians(180.0F - bodyYaw));
		if (tilt != 0.0F) {
			out.rotateX(-tilt);
		}
		return out;
	}

	/** Converts ticks since the start of a swing to normalised time ({@code windup <= 0}: the pose's canonical one). */
	public static float t01(DuelPose pose, float ticks, int windup) {
		int impact = windup > 0 ? windup : DuelPoseTable.impactTick(pose);
		int follow = Math.max(1, DuelPoseTable.followTicks(pose));
		if (ticks <= impact) {
			return impact <= 0 ? IMPACT_T01 : Math.max(0.0F, ticks / impact) * IMPACT_T01;
		}
		return Math.min(1.0F, IMPACT_T01 + (ticks - impact) / follow * (1.0F - IMPACT_T01));
	}

	/** Normalised time back to ticks since the start. */
	public static float ticks(DuelPose pose, float t01, int windup) {
		int impact = windup > 0 ? windup : DuelPoseTable.impactTick(pose);
		int follow = Math.max(1, DuelPoseTable.followTicks(pose));
		float t = clamp(t01, 0.0F, 1.0F);
		if (t <= IMPACT_T01) {
			return t / IMPACT_T01 * impact;
		}
		return impact + (t - IMPACT_T01) / (1.0F - IMPACT_T01) * follow;
	}

	/**
	 * The blade continued along its own line until its point is {@code reach} blocks from {@code from} (the eyes or the
	 * shoulder); unchanged when it already reaches that far. For rule tests that use the weapon class's reach.
	 */
	public static BladeSegment extend(BladeSegment blade, Vec3 from, double reach) {
		Vec3 axis = blade.axis();
		double len = axis.length();
		if (len < 1.0E-6D) {
			return blade;
		}
		Vec3 dir = axis.scale(1.0D / len);
		// solve |tip + dir * k - from| = reach for k >= 0
		Vec3 rel = blade.tip().subtract(from);
		double b = rel.dot(dir);
		double c = rel.dot(rel) - reach * reach;
		if (c >= 0.0D) {
			return blade;
		}
		double k = -b + Math.sqrt(Math.max(0.0D, b * b - c));
		return new BladeSegment(blade.grip(), blade.tip().add(dir.scale(Math.max(0.0D, k))));
	}

	private static float value(float[] ch, int c) {
		float v = c < ch.length ? ch[c] : 0.0F;
		return Float.isNaN(v) ? 0.0F : v;
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	private BladeArcs() {
	}
}
