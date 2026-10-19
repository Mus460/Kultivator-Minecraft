package dev.kultivator.client.anim.model;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One animation of a prop: {@code kind} from tick {@code start} (after the prop appeared) for {@code duration} ticks, with
 * the kind's parameters {@code a}, {@code b} and target point {@code to} (see {@link Anim}).
 */
public record AnimStep(Anim kind, int start, int duration, float a, float b, @Nullable Vec3 to) {

	public static AnimStep of(Anim kind, int start, int duration, float a, float b) {
		return new AnimStep(kind, start, duration, a, b, null);
	}

	public static AnimStep to(Anim kind, int start, int duration, Vec3 to) {
		return new AnimStep(kind, start, duration, 0.0F, 1.0F, to);
	}

	public int end() {
		return start + Math.max(1, duration);
	}
}
