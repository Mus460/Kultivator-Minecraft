package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.kota42.sekte.BatuUjiEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:batu_uji} (DESIGN 42.12.4, 42.8.1 "Kantor Rekrutmen"): the root-testing stone, 8 x 12 x 8 on a stepped
 * plinth, the five element glyphs carved into it (金 front, 木 right, 水 back, 火 left, 土 on top where the applicant lays
 * both palms, clip 409 {@code UJI_AKAR}). When it answers ({@link BatuUjiEntity#KEADAAN_MENYALA}) the glyphs of the synced
 * element mask ({@link BatuUjiEntity#unsur()}) light up in the root colour ({@link BatuUjiEntity#warna()}: grey, green,
 * blue, purple, gold) rising over 1.5 s from the synced start, a rune ring kindles on the plinth, and the light breathes
 * with the strength {@code nilai()}; {@link BatuUjiEntity#KEADAAN_PADAM} fades it out over a second. The light is the
 * {@code batuUjiGlif} mesh drawn emissive ({@code eyes}), never in a shadow pass. Textures {@code batu_uji.png},
 * {@code batu_uji_glif.png}.
 */
public class BatuUjiRenderer extends TemaBendaRenderer<BatuUjiEntity, BatuUjiRenderer.State, KotaModel<BatuUjiRenderer.State>> {

	private static final RenderType GLIF = RenderTypes.eyes(KotaRenderClient.tekstur("batu_uji_glif"));
	/** Ticks the glyphs take to light up / go out. */
	static final float NAIK = 30.0F;
	static final float TURUN = 20.0F;

	public static class State extends TemaBendaRenderer.State {
		public int unsur;
		public int warna = 0x9A9A9A;
		public float cahaya;
	}

	private final KotaModel<State> glif;

	public BatuUjiRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.BATU_UJI), (m, s) -> {
		}), KotaRenderClient.tekstur("batu_uji"), null, 0.45F);
		this.glif = new KotaModel<>(context.bakeLayer(KotaRenderClient.BATU_UJI_GLIF), BatuUjiRenderer::poseGlif);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BatuUjiEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.unsur = entity.unsur() & BatuUjiEntity.UNSUR_SEMUA;
		state.warna = entity.warna() & 0xFFFFFF;
		float kuat = state.nilai > 0.0F ? Mth.clamp(state.nilai, 0.0F, 1.0F) : 1.0F;
		state.cahaya = switch (state.keadaan) {
			case BatuUjiEntity.KEADAAN_MENYALA -> KotaModel.maju(state.umur, 0.0F, NAIK) * kuat
					* (state.jauh ? 1.0F : 0.82F + 0.18F * Mth.sin(state.waktu * 0.18F + state.fase));
			case BatuUjiEntity.KEADAAN_PADAM -> (1.0F - KotaModel.maju(state.umur, 0.0F, TURUN)) * kuat;
			default -> 0.0F;
		};
	}

	@Override
	protected void submitExtra(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.cahaya <= 0.02F || ShaderCompat.shadowPass()) {
			return;
		}
		int alpha = Math.round(255.0F * Math.min(1.0F, state.cahaya) * (EffectQuality.minimal() ? 0.8F : 1.0F));
		collector.order(1).submitModel(glif, state, poseStack, GLIF, state.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.color(alpha, state.warna), null,
				state.outlineColor);
	}

	static void poseGlif(KotaModel<State> m, State s) {
		for (int i = 0; i < 5; i++) {
			m.p("glif_" + i).visible = (s.unsur & (1 << i)) != 0;
		}
		m.p("cincin").visible = s.cahaya > 0.3F;
	}
}
