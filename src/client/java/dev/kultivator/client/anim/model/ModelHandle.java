package dev.kultivator.client.anim.model;

import net.minecraft.world.phys.Vec3;

/**
 * A live prop made by {@link ModelFx#show} (polish plan C3). Changes are eased over {@code lerpTicks}; {@link #end} fades
 * it out by shrinking over {@code fadeTicks}. Calls on an ended handle do nothing.
 */
public interface ModelHandle {

	void moveTo(Vec3 pos, int lerpTicks);

	/** Minecraft degrees, like {@link ModelRequest}. */
	void rotateTo(float yaw, float pitch, float roll, int lerpTicks);

	void scaleTo(float scale, int lerpTicks);

	/** Adds an animation; its {@code start} counts from now. */
	void play(AnimStep step);

	void follow(int entityId, Vec3 offset, boolean yaw);

	/** Shows another model on the same prop (the prison hand closing, a lotus opening into another shape). */
	void swap(String modelId);

	void end(int fadeTicks);

	boolean alive();
}
