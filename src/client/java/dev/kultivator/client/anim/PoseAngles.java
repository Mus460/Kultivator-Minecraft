package dev.kultivator.client.anim;

/**
 * One sampled cast pose at full weight (polish plan C3), in player-model space: radians, y points down, the front is -z.
 * {@code headX}/{@code headY} are offsets added to the look; {@code bodyX} is the forward lean and {@code bodyY} the
 * twist; arms and legs are absolute angles; {@code lift} is in blocks (+ up). Channels a pose does not drive are 0, so a
 * giant figure or an epic avatar can copy the values straight onto its parts.
 */
public record PoseAngles(float headX, float headY, float bodyX, float bodyY,
		float rArmX, float rArmY, float rArmZ, float lArmX, float lArmY, float lArmZ,
		float rLegX, float rLegZ, float lLegX, float lLegZ, float lift) {

	public static final PoseAngles REST = new PoseAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			0.0F, 0.0F);
}
