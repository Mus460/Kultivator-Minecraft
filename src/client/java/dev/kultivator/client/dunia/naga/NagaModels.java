package dev.kultivator.client.dunia.naga;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * The cuboid models of module B ({@code kultivator:fx/naga/<id>}, written by {@code tools/gen_dunia_naga_models.py}):
 * Jiao heads, jaws, body segments, claws, tails, whiskers, horns, manes and wings, the dragon pearl, fox tails and wolf
 * halos. Stacks are made lazily on first use (item components are not bound while renderers are created), resolved
 * render states are kept per model id until the next resource reload ({@link #clear}); one resolved state is submitted
 * many times per frame (the submit copies the pose and the quads list), so a 20-segment body costs no model lookups.
 * Every model's (8, 8, 8) lands on the pose origin (the item transform's centring), its front is -z, its up +y.
 */
public final class NagaModels {

	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static final Map<String, ItemStackRenderState> STATES = new HashMap<>();

	/** The stack showing {@code kultivator:fx/naga/<id>}. */
	public static ItemStack stack(String id) {
		return STACKS.computeIfAbsent(id, key -> {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Kultivator.id("fx/naga/" + key));
			return stack;
		});
	}

	/** The resolved render state of model {@code id} (resolved once, reused). */
	public static ItemStackRenderState state(String id) {
		ItemStackRenderState state = STATES.get(id);
		if (state == null) {
			state = new ItemStackRenderState();
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getItemModelResolver().updateForTopItem(state, stack(id), ItemDisplayContext.FIXED, minecraft.level, null, 0);
			STATES.put(id, state);
		}
		return state;
	}

	/** Draws model {@code id} at the pose origin (world-oriented pose: +y up). */
	public static void submit(String id, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		ItemStackRenderState state = state(id);
		if (!state.isEmpty()) {
			state.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		}
	}

	/**
	 * Draws model {@code id} at a model part's pivot (the pose is in part space, y down): the model's +y lands on the
	 * part's up, as the phase-27 form pieces do.
	 */
	public static void submitOnPart(String id, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		poseStack.pushPose();
		poseStack.rotate(Axis.ZP.rotationDegrees(180.0F));
		submit(id, poseStack, collector, light);
		poseStack.popPose();
	}

	/** Forgets the resolved states (resource reload, leaving a world). */
	public static void clear() {
		STATES.clear();
	}

	private NagaModels() {
	}
}
