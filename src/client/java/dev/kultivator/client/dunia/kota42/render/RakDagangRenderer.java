package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.dunia.kota42.ekonomi.RakDagangBlock;
import dev.kultivator.dunia.kota42.ekonomi.RakDagangBlockEntity;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity renderer of {@code kultivator:rak_dagang} (DESIGN 42.12.3, 42.6.2): each of the three shelves (bottom,
 * middle, top: {@link RakDagangBlock#RAK_Y}) shows its stock item on the shelf, one copy for a few, two for a handful,
 * three for a full stack, turned to the customer side ({@link RakDagangBlock#FACING}), and its asking price on the red lip
 * of the shelf ("12 NB" in the short form of the 38.U kit, cream on a dark plate; nothing for a display-only slot). Items
 * use the vanilla shelf display context; within 16 blocks the price labels are drawn, the items to the block entity view
 * distance. Client only.
 */
public class RakDagangRenderer implements BlockEntityRenderer<RakDagangBlockEntity, RakDagangRenderer.State> {

	/** Labels are only readable close up. */
	static final double LABEL_SQ = 16.0D * 16.0D;
	/** The shelf centre, local z (front = +z after the facing turn). */
	static final float RAK_Z = -0.22F;

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public final ItemStackRenderState[][] barang = new ItemStackRenderState[RakDagangBlockEntity.SLOT][];
		public final @Nullable FormattedCharSequence[] harga = new FormattedCharSequence[RakDagangBlockEntity.SLOT];
		public boolean dekat;
	}

	private final ItemModelResolver items;
	private final Font font;

	public RakDagangRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RakDagangBlockEntity rak, State state, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(rak, state, partialTicks, camera, breakProgress);
		state.facing = rak.getBlockState().getValue(RakDagangBlock.FACING);
		state.dekat = Vec3.atCenterOf(rak.getBlockPos()).distanceToSqr(camera) < LABEL_SQ;
		int seed = HashCommon.long2int(rak.getBlockPos().asLong());
		for (int slot = 0; slot < RakDagangBlockEntity.SLOT; slot++) {
			ItemStack stack = rak.barang(slot);
			if (stack.isEmpty()) {
				state.barang[slot] = null;
				state.harga[slot] = null;
				continue;
			}
			int copies = stack.getCount() >= 32 ? 3 : stack.getCount() >= 8 ? 2 : 1;
			ItemStackRenderState[] shown = new ItemStackRenderState[copies];
			for (int c = 0; c < copies; c++) {
				ItemStackRenderState s = new ItemStackRenderState();
				items.updateForTopItem(s, stack, ItemDisplayContext.ON_SHELF, rak.getLevel(), null, seed + slot * 7 + c);
				shown[c] = s;
			}
			state.barang[slot] = shown;
			int price = rak.harga(slot);
			state.harga[slot] = price > 0 ? Component.translatable("kultivator.tempat_ui.nb", TeksTempat.singkat(price)).getVisualOrderText() : null;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		for (int slot = 0; slot < RakDagangBlockEntity.SLOT; slot++) {
			ItemStackRenderState[] shown = state.barang[slot];
			if (shown == null) {
				continue;
			}
			float y = RakDagangBlock.RAK_Y[slot] / 16.0F;
			for (int c = 0; c < shown.length; c++) {
				float x = shown.length == 1 ? 0.0F : (c - (shown.length - 1) / 2.0F) * (shown.length == 2 ? 0.28F : 0.26F);
				poseStack.pushPose();
				poseStack.translate(0.5F, y, 0.5F);
				poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
				poseStack.translate(x, 0.0F, RAK_Z + (c % 2) * 0.04F);
				poseStack.scale(0.3F, 0.3F, 0.3F);
				AABB box = shown[c].getModelBoundingBox();
				poseStack.translate(0.0D, -box.minY, 0.0D);
				shown[c].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
			FormattedCharSequence label = state.harga[slot];
			if (label != null && state.dekat) {
				poseStack.pushPose();
				poseStack.translate(0.5F, y + 1.4F / 16.0F, 0.5F);
				poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
				poseStack.translate(0.0F, 0.0F, 0.004F);
				float s = 0.0055F;
				poseStack.scale(s, -s, s);
				float w = font.width(label);
				collector.submitText(poseStack, -w / 2.0F, -4.0F, label, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, 0xFFF4E8C8,
						0xC0201010, 0);
				poseStack.popPose();
			}
		}
	}
}
