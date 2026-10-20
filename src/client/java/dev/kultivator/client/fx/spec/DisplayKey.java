package dev.kultivator.client.fx.spec;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The one way client code poses a client-only Display entity (technique props, phantom displays, glyphs). Vanilla
 * {@code Display} (26.3) behaves like this on the client:
 * <ul>
 * <li>every data setter only notifies the entity when the value changes, except
 * {@code setTransformationInterpolationDelay}, which is a forced set: each call re-arms the interpolation start at the
 * display's next tick;</li>
 * <li>at that tick a changed transformation starts a new segment from {@code previous.get(lastProgress)} to the new
 * target, where {@code lastProgress} is the progress of the last rendered frame;</li>
 * <li>a re-armed start with an <em>unchanged</em> transformation keeps the old segment and restarts its progress at 0,
 * so the last motion is replayed: a prop that has stopped moving jumps back and forth (a stamp squash, the last step of
 * a fall or a grow) every time it is re-keyed.</li>
 * </ul>
 * So a {@link DisplayKey} remembers the frame it last sent and re-arms the interpolation only when the frame changed,
 * sets the duration only when it differs, and places the first frame with duration 0 (no zoom from the default scale 1).
 * Callers key once per display per client tick (END_CLIENT_TICK), targeting the frame of the next tick with duration 1,
 * and move the entity with {@link #move} (plain {@code setPos}: the renderer lerps old to new position, like any entity),
 * so position and transformation stay in step.
 */
public final class DisplayKey {

	private final Display display;
	private @Nullable Vector3f lastTranslation;
	private @Nullable Quaternionf lastRotation;
	private @Nullable Vector3f lastScale;

	public DisplayKey(Display display) {
		this.display = display;
	}

	public Display display() {
		return display;
	}

	/** The first frame: shown as is from the display's first rendered frame, without interpolation. */
	public void snap(Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
		send(translation, rotation, scale, 0);
	}

	/** The frame reached {@code duration} ticks after the display's next tick (call at most once per display per tick). */
	public void key(Vector3fc translation, Quaternionfc rotation, Vector3fc scale, int duration) {
		send(translation, rotation, scale, Math.max(0, duration));
	}

	/** Moves the entity (the renderer lerps from the previous tick's position). */
	public void move(Vec3 at) {
		move(display, at);
	}

	/** Turns the entity about y (degrees; FIXED billboards lerp it like the entity they ride). */
	public void turn(float yRot) {
		if (Float.isFinite(yRot) && yRot != display.getYRot()) {
			display.setYRot(yRot);
		}
	}

	private void send(Vector3fc translation, Quaternionfc rotation, Vector3fc scale, int duration) {
		if (display.isRemoved() || !finite(translation) || !finite(scale) || !Float.isFinite(rotation.x()) || !Float.isFinite(rotation.y())
				|| !Float.isFinite(rotation.z()) || !Float.isFinite(rotation.w())) {
			return;
		}
		boolean unchanged = lastTranslation != null && lastRotation != null && lastScale != null && same(lastTranslation, translation)
				&& same(lastScale, scale) && bits(lastRotation.x()) == bits(rotation.x()) && bits(lastRotation.y()) == bits(rotation.y())
				&& bits(lastRotation.z()) == bits(rotation.z()) && bits(lastRotation.w()) == bits(rotation.w());
		if (unchanged && display.getTransformationInterpolationDuration() == duration) {
			// unchanged: re-arming would replay the last segment
			return;
		}
		// fresh copies: the entity data keeps the very vectors it is given
		lastTranslation = new Vector3f(translation);
		lastRotation = new Quaternionf(rotation);
		lastScale = new Vector3f(scale);
		if (display.getTransformationInterpolationDuration() != duration) {
			display.setTransformationInterpolationDuration(duration);
		}
		if (unchanged) {
			return;
		}
		display.setTransformation(new Transformation(new Vector3f(translation), new Quaternionf(rotation), new Vector3f(scale), null));
		// a forced set: re-arms the interpolation start at the next tick, together with the new target
		display.setTransformationInterpolationDelay(0);
	}

	// ================================================================== static displays

	/** A display posed once (no animation): its only frame, shown without interpolation. */
	public static void place(Display display, Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
		new DisplayKey(display).snap(translation, rotation, scale);
	}

	/** Moves a display entity when {@code at} is finite and differs from where it stands. */
	public static void move(Display display, Vec3 at) {
		if (display.isRemoved() || !Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) {
			return;
		}
		if (at.x != display.getX() || at.y != display.getY() || at.z != display.getZ()) {
			display.setPos(at.x, at.y, at.z);
		}
	}

	/** The equality the entity data uses (JOML equals: the float bits). */
	private static boolean same(Vector3fc a, Vector3fc b) {
		return bits(a.x()) == bits(b.x()) && bits(a.y()) == bits(b.y()) && bits(a.z()) == bits(b.z());
	}

	private static int bits(float f) {
		return Float.floatToIntBits(f);
	}

	private static boolean finite(Vector3fc v) {
		return Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z());
	}
}
