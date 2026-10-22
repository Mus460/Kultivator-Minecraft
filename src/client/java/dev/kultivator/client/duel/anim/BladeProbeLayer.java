package dev.kultivator.client.duel.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.disciple.DiscipleHeldItemLayer;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.duel.blade.BladeKinematics;
import dev.kultivator.duel.blade.BladeShape;
import dev.kultivator.duel.clash.WeaponClass;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A render layer that draws nothing (DESIGN 35.5.4): on every renderer whose model is a {@link PlayerModel} (players,
 * disciples, rogue cultivators, the PlayerModel NPCs of Dunia) it repeats exactly what the held-item layer does with
 * the pose stack ({@code translateToHand}, then vanilla {@code ItemInHandLayer}'s turn and offset with the
 * {@code handheld} third-person display, or the disciple's 3D-sword branch through
 * {@link DiscipleHeldItemLayer#swordTransform}) and records the base and the point of the blade in world space into
 * {@link BladeTracker}. It runs after {@code setupAnim} and every mixin and animation library that touched the arms, so
 * the blade recorded is the blade on screen.
 * <p>
 * Records only armed entities (or any fighter of a duel) within {@link BladeTracker#RANGE} blocks of the camera, never in
 * the Iris shadow pass and never for invisible ones. Cost per recorded entity: one matrix copy and two transforms.
 */
public final class BladeProbeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	/** Entities whose last recorded blade was the disciples' 3D sword (the off hand aims at its handle). */
	private static final IntSet SWORD_3D = new IntOpenHashSet();

	private final Matrix4f matrix = new Matrix4f();
	private final Vector3f grip = new Vector3f();
	private final Vector3f tip = new Vector3f();

	public BladeProbeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	/**
	 * Adds the probe to a renderer when its model is a PlayerModel (from the layer registration callback of
	 * {@code AnimClient}); other renderers are left alone.
	 */
	@SuppressWarnings("unchecked")
	public static @org.jetbrains.annotations.Nullable BladeProbeLayer forRenderer(LivingEntityRenderer<?, ?, ?> renderer) {
		if (!(renderer.getModel() instanceof PlayerModel)) {
			return null;
		}
		// a PlayerModel renderer always works on AvatarRenderState (PlayerModel's setupAnim demands it)
		return new BladeProbeLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (RenderLayerParent<?, ?>) renderer);
	}

	/** True when the last blade of {@code entityId} was the 3D natal / qi sword of a disciple. */
	static boolean drewSword3d(int entityId) {
		return SWORD_3D.contains(entityId);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || ShaderCompat.shadowPass()) {
			return;
		}
		Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double dx = state.x - cam.x;
		double dy = state.y - cam.y;
		double dz = state.z - cam.z;
		if (dx * dx + dy * dy + dz * dz > BladeTracker.RANGE * BladeTracker.RANGE) {
			return;
		}
		boolean sword3d = state instanceof DiscipleRenderState disciple && disciple.heldSword && !disciple.rightHandItemState.isEmpty();
		HumanoidArm arm = sword3d ? HumanoidArm.RIGHT : state.mainArm;
		ItemStack held = arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		WeaponClass weapon = sword3d ? WeaponClass.PEDANG : WeaponClass.of(held);
		boolean emptyHand = arm == HumanoidArm.RIGHT ? state.rightHandItemState.isEmpty() : state.leftHandItemState.isEmpty();
		if (weapon == WeaponClass.TANGAN_KOSONG && DuelClient.fighter(state.id) == null) {
			SWORD_3D.remove(state.id);
			return;
		}
		BladeShape shape = sword3d ? BladeShape.SWORD_3D : weapon == WeaponClass.TANGAN_KOSONG || emptyHand ? BladeShape.FIST : BladeShape.of(weapon);
		boolean left = arm == HumanoidArm.LEFT;
		poseStack.pushPose();
		this.getParentModel().translateToHand(state, arm, poseStack);
		if (sword3d) {
			DiscipleHeldItemLayer.swordTransform(poseStack);
		}
		matrix.set(poseStack.last().pose());
		poseStack.popPose();
		if (sword3d) {
			// FormModels.submitState turns the 3D model upside down before drawing it
			matrix.rotateZ((float) Math.PI);
		} else if (shape.hold() == BladeShape.Hold.ITEM) {
			BladeKinematics.item(left, matrix);
		}
		BladeKinematics.blade(shape, left, matrix, grip, tip);
		BladeTracker.record(state.id, new Vec3(grip.x + cam.x, grip.y + cam.y, grip.z + cam.z), new Vec3(tip.x + cam.x, tip.y + cam.y, tip.z + cam.z),
				shape.hold() == BladeShape.Hold.ARM);
		if (sword3d) {
			SWORD_3D.add(state.id);
		} else {
			SWORD_3D.remove(state.id);
		}
	}

	static void clear() {
		SWORD_3D.clear();
	}
}
