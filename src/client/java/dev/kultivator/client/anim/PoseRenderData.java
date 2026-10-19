package dev.kultivator.client.anim;

import dev.kultivator.technique.spec.dsl.Pose;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The cast pose and form of an entity, carried in its render state from the extract step to the model setup and the
 * layers (players: {@code AvatarRendererMixin}; disciples: {@code DiscipleRenderer.extractRenderState}).
 */
public final class PoseRenderData {

	/** Stored when no pose plays (the data map never sees a null). */
	public static final CastPoses.Snapshot NONE = new CastPoses.Snapshot(Pose.NONE, new float[PoseLibrary.CHANNELS],
			new float[PoseLibrary.CHANNELS], null, null, 1.0F, false);
	public static final ClientForms.View NO_FORM = new ClientForms.View("", 0, 0.0F, java.util.List.of());

	public static final RenderStateDataKey<CastPoses.Snapshot> POSE = RenderStateDataKey.create(() -> "kultivator:cast_pose");
	public static final RenderStateDataKey<ClientForms.View> FORM = RenderStateDataKey.create(() -> "kultivator:form");

	/** Evaluates the pose and form of {@code entity} for this frame and stores them in {@code state}. */
	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		float age = entity.tickCount + partialTicks;
		CastPoses.Snapshot snapshot = CastPoses.snapshot(entity, age);
		// render states are made fresh every frame: only what is there gets written (the readers default to none)
		FabricRenderState data = (FabricRenderState) state;
		if (snapshot != null || data.getData(POSE) != null) {
			data.setData(POSE, snapshot == null ? NONE : snapshot);
		}
		ClientForms.View form = ClientForms.view(entity, age);
		if (form != null || data.getData(FORM) != null) {
			data.setData(FORM, form == null ? NO_FORM : form);
		}
	}

	/** The pose stored in a render state, or null (none plays, or another renderer's state). */
	public static @Nullable CastPoses.Snapshot pose(EntityRenderState state) {
		CastPoses.Snapshot snapshot = ((FabricRenderState) state).getDataOrDefault(POSE, NONE);
		return snapshot == null || snapshot == NONE ? null : snapshot;
	}

	/** The form stored in a render state (never null). */
	public static ClientForms.View form(EntityRenderState state) {
		ClientForms.View view = ((FabricRenderState) state).getDataOrDefault(FORM, NO_FORM);
		return view == null ? NO_FORM : view;
	}

	private PoseRenderData() {
	}
}
