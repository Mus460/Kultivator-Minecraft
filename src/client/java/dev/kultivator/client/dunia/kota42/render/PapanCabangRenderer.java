package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.sekte.PapanCabangBlock;
import dev.kultivator.sekte.api.Lambang;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The block entity renderer of {@code kultivator:papan_cabang} (DESIGN 42.12.3, 42.8.4 "Cabang sekte di kota"): the sect
 * emblem painted on both faces of the branch board, composed straight from the greyscale masks of the 36.2.8 emblem atlas
 * ({@code textures/sekte/seragam/lambang_atlas.png}: shape fill tinted with the field colour, the symbol with the charge
 * colour, lifted when it would vanish on the field, and the rim with the border colour) from the packed
 * {@link Lambang} the board keeps ({@link PapanCabangBlock.Isi#lambang()}), so no hall data is needed; under it the sect
 * name in the board's ink colour. A blank board (no owner or no emblem) shows nothing. Drawn within 48 blocks. Client only.
 */
public class PapanCabangRenderer implements BlockEntityRenderer<PapanCabangBlock.Isi, PapanCabangRenderer.State> {

	private static final RenderType ATLAS = RenderTypes.entityTranslucent(Kultivator.id("textures/sekte/seragam/lambang_atlas.png"));
	private static final float ATLAS_W = 512.0F;
	private static final float ATLAS_H = 384.0F;
	private static final float CELL = 64.0F;

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public @Nullable Lambang lambang;
		public int charge;
		public @Nullable FormattedCharSequence nama;
		public float lebar;
		public int tinta = 0xE8C060;
	}

	private final Font font;

	public PapanCabangRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public int getViewDistance() {
		return 48;
	}

	@Override
	public void extractRenderState(PapanCabangBlock.Isi papan, State state, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(papan, state, partialTicks, camera, breakProgress);
		state.facing = papan.getBlockState().getValue(PapanCabangBlock.FACING);
		boolean kosong = papan.pemilik() == null || papan.lambang() < 0;
		state.lambang = kosong ? null : Lambang.unpack(papan.lambang());
		if (state.lambang != null) {
			int field = state.lambang.fieldRgb();
			int charge = state.lambang.chargeRgb();
			if (jarak(field, charge) < 60) {
				charge = terang(field) > 140 ? ARGB.scaleRGB(charge, 0.55F) : ARGB.srgbLerp(0.6F, charge, 0xFFFFFF);
			}
			state.charge = charge & 0xFFFFFF;
		}
		String nama = papan.namaSekte();
		state.nama = kosong || nama.isBlank() ? null : Component.literal(nama).getVisualOrderText();
		state.lebar = state.nama == null ? 0.0F : font.width(state.nama);
		state.tinta = papan.warna() & 0xFFFFFF;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Lambang l = state.lambang;
		if (l == null) {
			return;
		}
		int light = state.lightCoords;
		float shapeU = l.shape() * CELL;
		float symU = (l.symbol() % 8) * CELL;
		float symV = 128.0F + (l.symbol() / 8) * CELL;
		int field = ARGB.opaque(l.fieldRgb());
		int charge = ARGB.opaque(state.charge);
		int border = ARGB.opaque(l.borderRgb());
		for (int side = 0; side < 2; side++) {
			poseStack.pushPose();
			// the board: block y 3..14, faces 0.5 px off the centre plane; the emblem 7 px square, its centre at y 9.6
			poseStack.translate(0.5F, 9.6F / 16.0F, 0.5F);
			poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot() + side * 180.0F);
			poseStack.translate(0.0F, 0.0F, 0.5F / 16.0F + 0.002F);
			collector.submitCustomGeometry(poseStack, ATLAS, (pose, buffer) -> {
				kotak(pose, buffer, shapeU, 0.0F, field, 0.0F, light);
				kotak(pose, buffer, symU, symV, charge, 0.0008F, light);
				kotak(pose, buffer, shapeU, CELL, border, 0.0016F, light);
			});
			if (state.nama != null) {
				poseStack.translate(0.0F, -4.9F / 16.0F, 0.001F);
				float s = Math.min(10.0F / 16.0F / Math.max(1.0F, state.lebar), 1.5F / 16.0F / 9.0F);
				poseStack.scale(s, -s, s);
				collector.submitText(poseStack, -state.lebar / 2.0F, -4.5F, state.nama, false, Font.DisplayMode.POLYGON_OFFSET, light,
						ARGB.opaque(state.tinta), 0, 0);
			}
			poseStack.popPose();
		}
	}

	/** One emblem layer: a 7 x 7 px quad facing +z showing the 64 x 64 atlas cell at (u, v), tinted. */
	private static void kotak(PoseStack.Pose pose, VertexConsumer buffer, float u, float v, int color, float z, int light) {
		float h = 3.5F / 16.0F;
		float u0 = u / ATLAS_W;
		float u1 = (u + CELL) / ATLAS_W;
		float v0 = v / ATLAS_H;
		float v1 = (v + CELL) / ATLAS_H;
		vertex(pose, buffer, -h, h, z, u0, v0, color, light);
		vertex(pose, buffer, -h, -h, z, u0, v1, color, light);
		vertex(pose, buffer, h, -h, z, u1, v1, color, light);
		vertex(pose, buffer, h, h, z, u1, v0, color, light);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color, int light) {
		buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	private static int jarak(int a, int b) {
		return Math.abs(ARGB.red(a) - ARGB.red(b)) + Math.abs(ARGB.green(a) - ARGB.green(b)) + Math.abs(ARGB.blue(a) - ARGB.blue(b));
	}

	private static int terang(int rgb) {
		return (ARGB.red(rgb) * 30 + ARGB.green(rgb) * 59 + ARGB.blue(rgb) * 11) / 100;
	}
}
