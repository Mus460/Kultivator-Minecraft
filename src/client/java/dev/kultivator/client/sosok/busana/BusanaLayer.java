package dev.kultivator.client.sosok.busana;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.client.sosok.kulit.SosokRupa;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;

/**
 * Draws the hair and garment cubes of Sosok bodies (DESIGN 40.3.6-40.3.7, module 40K) with the body's own atlas, so
 * body, face, hair and robes share one texture and one vanilla render type per body ({@code entityCutout}; see-through
 * bodies {@code entityTranslucent}) - Iris entity shaders and Sodium batching stay simple. 40R wraps it in its LOD and
 * shadow-pass gate ({@code SosokJembatan.Gerbang}); here a body without its own atlas yet (class fallback showing) or
 * without a look draws no cubes, because the fallback atlas holds another look's cube textures.
 */
public class BusanaLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	private final SosokBusanaModel lebar;
	private final SosokBusanaModel ramping;

	public BusanaLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.lebar = new SosokBusanaModel(context.bakeLayer(SosokLapisan.BUSANA), false);
		this.ramping = new SosokBusanaModel(context.bakeLayer(SosokLapisan.BUSANA_RAMPING), true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		if (k == null || !k.atlasSendiri() || !(k.rupa() instanceof SosokRupa rupa)) {
			return;
		}
		if (k.lod != SosokDetail.Lod.PENUH && k.lod != SosokDetail.Lod.KURANG) {
			return;
		}
		if (rupa.kubusRambut() == 0 && rupa.kubusBusana() == 0L) {
			return;
		}
		SosokBusanaModel model = rupa.ramping() ? ramping : lebar;
		boolean tembus = k.tembus || rupa.punya(dev.kultivator.client.sosok.kulit.RupaResolver.V_PUDAR);
		RenderType type = tembus ? RenderTypes.entityTranslucent(k.atlas) : RenderTypes.entityCutout(k.atlas);
		int warna = tembus ? ARGB.color(150, 0xFFFFFF) : -1;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		collector.order(1).submitModel(model, state, poseStack, type, lightCoords, overlay, warna, null, state.outlineColor);
	}
}
