package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * The cuboid creature parts of module R ({@code kultivator:dunia/reruntuhan/<part>}, written by
 * {@code tools/gen_dunia_reruntuhan_textures.py}): the three demon kings and the nine floor guardians. Stacks are made
 * lazily on first use (never in a static initializer or a renderer constructor, rule C1); each part's render state is
 * resolved once and reused until a resource reload or leaving the world ({@link #clear}). A part's (8, 8, 8) is its
 * joint and lands on the pose origin, its front is -z, its up +y; one model unit is 1/16 block before the rig's scale.
 */
final class RModels {

	private static final Map<String, ItemStack> STACKS = new HashMap<>();
	private static final Map<String, ItemStackRenderState> STATES = new HashMap<>();

	private static ItemStack stack(String id) {
		return STACKS.computeIfAbsent(id, key -> {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, Kultivator.id("dunia/reruntuhan/" + key));
			return stack;
		});
	}

	private static ItemStackRenderState state(String id) {
		ItemStackRenderState state = STATES.get(id);
		if (state == null) {
			state = new ItemStackRenderState();
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getItemModelResolver().updateForTopItem(state, stack(id), ItemDisplayContext.FIXED, minecraft.level, null, 0);
			STATES.put(id, state);
		}
		return state;
	}

	/** Draws part {@code id} at the pose origin. */
	static void submit(String id, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
		ItemStackRenderState state = state(id);
		if (!state.isEmpty()) {
			state.submit(poseStack, collector, light, overlay, 0);
		}
	}

	/** Forgets the resolved states (resource reload, leaving a world). */
	static void clear() {
		STATES.clear();
	}

	private RModels() {
	}
}
