package dev.kultivator.client.anim.model;

import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Everything a prop needs (polish plan C3): the model {@code id}, where ({@code pos}) and how big ({@code scale}), its
 * Minecraft yaw / pitch / roll in degrees (the model's front looks that way; pitch + tilts the front down), its life in
 * {@code ticks}, its {@code anims}, an entity to {@code follow} (-1 none) at {@code followOffset} (blocks; turned with
 * the entity's body yaw when {@code followYaw}), whether it is lit full bright ({@code glow}), the caster it belongs to
 * (ABORT ends it) and its budget priority.
 */
public record ModelRequest(String id, Vec3 pos, float scale, float yaw, float pitch, float roll, int ticks, List<AnimStep> anims,
		int follow, Vec3 followOffset, boolean followYaw, boolean glow, int casterId, FxLayer.Priority priority) {

	/** A still prop with one animation over its whole life. */
	public static ModelRequest simple(String id, Vec3 pos, float scale, float yaw, int ticks, Anim anim, float a, float b, int casterId) {
		List<AnimStep> steps = anim == Anim.NONE ? List.of() : List.of(AnimStep.of(anim, 0, ticks, a, b));
		return new ModelRequest(id, pos, scale, yaw, 0.0F, 0.0F, ticks, steps, -1, Vec3.ZERO, false, true, casterId, FxLayer.Priority.CORE);
	}
}
