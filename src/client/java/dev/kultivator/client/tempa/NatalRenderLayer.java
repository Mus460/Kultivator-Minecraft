package dev.kultivator.client.tempa;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.data.Element;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.NatalTreasureSelf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The player's natal treasure (DESIGN 25.5.6), drawn from the synced {@code NatalTreasureSelf.LOOK} on every avatar: a Pedang
 * rides on the back, crossed with the cultivator's back sword; the other forms slowly orbit at chest height. The stack is the
 * real artifact model (form, refinement tint, element) built once per look value. Render layers never run for the
 * first-person view.
 */
public class NatalRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	private static final float CENTER_Y = 6.0F / 16.0F;
	private static final float CENTER_Z = 4.6F / 16.0F;
	private static final float TILT = -20.0F;
	private static final float ORBIT_RADIUS = 0.9F;
	private static final int[] TINT_REFINE = {0, 3, 6, 9, 12};

	private static final Map<Integer, ItemStack> STACKS = new HashMap<>();

	private final ItemModelResolver itemModelResolver;
	private final ItemStackRenderState item = new ItemStackRenderState();

	public NatalRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.itemModelResolver = context.getItemModelResolver();
	}

	/** The look value of the player drawn with this state (0 none). */
	static int lookOf(AvatarRenderState state) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return 0;
		}
		Entity entity = level.getEntity(state.id);
		if (!(entity instanceof Player player) || player.isRemoved()) {
			return 0;
		}
		Integer look = player.getAttached(NatalTreasureSelf.LOOK);
		return look == null ? 0 : look;
	}

	/** The artifact stack of a look value (cached). */
	static ItemStack stackOf(int look) {
		return STACKS.computeIfAbsent(look, value -> {
			CompoundTag tag = new CompoundTag();
			int form = Math.max(0, Math.min(3, (value & 0xF) - 1));
			tag.putString("form", ArtifactItem.FORMS.get(form));
			tag.putInt("grade", value >> 4 & 0xF);
			tag.putInt("refine", TINT_REFINE[Math.min(4, value >> 8 & 0xF)]);
			int element = (value >> 16 & 0xFF) - 1;
			tag.putString("element", element >= 0 && element < Element.values().length ? Element.values()[element].id() : "");
			CompoundTag spirit = new CompoundTag();
			spirit.putInt("stage", value >> 12 & 0xF);
			tag.put("spirit", spirit);
			tag.putString("id", "00000000-0000-0000-0000-000000000000");
			return ArtifactItem.fromData(tag);
		});
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		int look = lookOf(state);
		if (look == 0) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		Entity entity = level == null ? null : level.getEntity(state.id);
		if (!(entity instanceof Player player)) {
			return;
		}
		ItemStack stack = stackOf(look);
		this.itemModelResolver.updateForLiving(this.item, stack, ItemDisplayContext.FIXED, player);
		if (this.item.isEmpty()) {
			return;
		}
		PlayerModel model = this.getParentModel();
		int form = Math.max(0, Math.min(3, (look & 0xF) - 1));
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		if (form == 0) {
			model.body.translateAndRotate(poseStack);
			poseStack.translate(0.0F, CENTER_Y, CENTER_Z);
			poseStack.rotateDegrees(Axis.ZP, TILT);
			poseStack.rotateDegrees(Axis.YP, 180.0F);
			poseStack.scale(0.85F, 0.85F, 0.85F);
		} else {
			float angle = state.ageInTicks * 2.5F;
			poseStack.rotateDegrees(Axis.YP, angle);
			poseStack.translate(ORBIT_RADIUS, 0.2F + (float) Math.sin(state.ageInTicks * 0.07F) * 0.07F, 0.0F);
			poseStack.rotateDegrees(Axis.YP, 90.0F);
			float scale = form == 3 ? 0.5F : 0.65F;
			poseStack.scale(scale, scale, scale);
		}
		this.item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
