package dev.kultivator.client.sosok.busana;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sosok.SosokRender;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;

/**
 * Draws the animated face of Sosok bodies (DESIGN 40.3.1, 40.5.9; module 40K): blinking, glancing, talking and the
 * expressions, as one eye plane and one mouth plane over the head front, textured from the face strips of the body's
 * own atlas. Only near bodies (LOD PENUH, setting "Ekspresi wajah" on, outside the Iris shadow pass - 40R's gate), only
 * when the face is not at rest (rest = the skin's painted face).
 */
public class WajahLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	private final WajahModel model;

	public WajahLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.model = new WajahModel(context.bakeLayer(SosokLapisan.WAJAH));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		if (k == null || !k.atlasSendiri() || !WajahModel.adaWajah(state)) {
			return;
		}
		boolean tembus = k.tembus;
		int warna = tembus ? ARGB.color(150, 0xFFFFFF) : -1;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		collector.order(2).submitModel(model, state, poseStack, tembus ? RenderTypes.entityTranslucent(k.atlas) : RenderTypes.entityCutout(k.atlas),
				lightCoords, overlay, warna, null, state.outlineColor);
	}
}
