package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.dunia.kota42.ekonomi.PapanNamaTokoBlock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity renderer of {@code kultivator:papan_nama_toko} (DESIGN 42.12.3, 42.6.2): the shop name
 * ({@link PapanNamaTokoBlock.Isi#nama()}, at most {@link PapanNamaTokoBlock.Isi#NAMA_MAKS} characters) lettered in gold
 * with a dark outline on both faces of the hanging black board, scaled down to fit its 13 x 5 px field, front side toward
 * {@link PapanNamaTokoBlock#FACING}. An unnamed sign stays blank. Drawn within 32 blocks. Client only.
 */
public class PapanNamaTokoRenderer implements BlockEntityRenderer<PapanNamaTokoBlock.Isi, PapanNamaTokoRenderer.State> {

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public @Nullable FormattedCharSequence nama;
		public float lebar;
	}

	private final Font font;

	public PapanNamaTokoRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public int getViewDistance() {
		return 32;
	}

	@Override
	public void extractRenderState(PapanNamaTokoBlock.Isi papan, State state, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(papan, state, partialTicks, camera, breakProgress);
		state.facing = papan.getBlockState().getValue(PapanNamaTokoBlock.FACING);
		String nama = papan.nama();
		if (nama == null || nama.isBlank()) {
			state.nama = null;
			return;
		}
		state.nama = Component.literal(nama).getVisualOrderText();
		state.lebar = font.width(state.nama);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.nama == null) {
			return;
		}
		// the board: 14 x 8 px at block y 2..10, 1 px thick around the centre plane
		float s = Math.min(13.0F / 16.0F / Math.max(1.0F, state.lebar), 5.0F / 16.0F / 9.0F);
		for (int side = 0; side < 2; side++) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 6.0F / 16.0F, 0.5F);
			poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + side * 180.0F);
			poseStack.translate(0.0F, 0.0F, 0.5F / 16.0F + 0.003F);
			poseStack.scale(s, -s, s);
			collector.submitText(poseStack, -state.lebar / 2.0F, -4.5F, state.nama, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords,
					0xFFE8C060, 0, 0xFF3A2410);
			poseStack.popPose();
		}
	}
}
