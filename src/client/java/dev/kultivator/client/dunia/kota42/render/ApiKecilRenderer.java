package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.kota42.acara.ApiKecilEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:api_kecil} (DESIGN 42.12.4, 42.7.4): a small street fire, never a block. What burns is the variant
 * ({@code bahan_0..2}): a charred straw heap with loose straws, an overturned oil jar in its dark pool, three crossed logs
 * with glowing ends; three embers pulse under it. The flames are the {@code apiNyala} mesh: eight frames of six crossed
 * cards (three large, three small turned 30 degrees), shown two frames a tick so the tongues lick and split, drawn
 * emissive only (the vanilla {@code eyes} type: full bright, no post chain) and tinted per fuel (straw pale gold, oil
 * deep orange, wood orange). The synced size {@code nilai()} scales the flames: it grows while
 * {@link ApiKecilEntity#KEADAAN_MENJALAR} and shrinks while doused ({@link ApiKecilEntity#KEADAAN_PADAM}); at 0 only the
 * smoking fuel stays. Not drawn in a shadow pass; beyond 64 blocks the frame freezes. Smoke is 42V-F's. Textures
 * {@code api_bahan.png} (+ {@code _pancar}), {@code api_nyala.png}.
 */
public class ApiKecilRenderer extends TemaBendaRenderer<ApiKecilEntity, ApiKecilRenderer.State, KotaModel<ApiKecilRenderer.State>> {

	private static final RenderType NYALA = RenderTypes.eyes(KotaRenderClient.tekstur("api_nyala"));
	private static final int[] WARNA = {0xFFF0C0, 0xFF9A50, 0xFFC070};

	public static class State extends TemaBendaRenderer.State {
		public float besar;
		public int bingkai;
	}

	private final KotaModel<State> nyala;

	public ApiKecilRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.API_BAHAN), ApiKecilRenderer::pose),
				KotaRenderClient.tekstur("api_bahan"), KotaRenderClient.tekstur("api_bahan_pancar"), 0.5F);
		this.nyala = new KotaModel<>(context.bakeLayer(KotaRenderClient.API_NYALA), ApiKecilRenderer::poseNyala);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ApiKecilEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.besar = Mth.clamp(state.nilai, 0.0F, 1.0F);
		state.bingkai = state.jauh ? 0 : Math.floorMod((int) (state.waktu * 2.0F) + state.acak, 8);
		state.pancar = 0.35F + 0.65F * state.besar * (0.8F + 0.2F * Mth.sin(state.waktu * 0.4F + state.fase));
	}

	@Override
	protected void submitExtra(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.besar <= 0.01F || ShaderCompat.shadowPass()) {
			return;
		}
		int warna = WARNA[Mth.clamp(state.varian, 0, WARNA.length - 1)];
		int alpha = EffectQuality.minimal() ? 200 : 235;
		collector.order(2).submitModel(nyala, state, poseStack, NYALA, state.lightCoords, OverlayTexture.NO_OVERLAY, ARGB.color(alpha, warna), null,
				state.outlineColor);
	}

	static void pose(KotaModel<State> m, State s) {
		m.satu("bahan_", ApiKecilEntity.VARIAN_JUMLAH, Mth.clamp(s.varian, 0, ApiKecilEntity.VARIAN_JUMLAH - 1));
	}

	static void poseNyala(KotaModel<State> m, State s) {
		for (int i = 0; i < 8; i++) {
			ModelPart f = m.p("nyala_" + i);
			f.visible = i == s.bingkai;
			if (i == s.bingkai) {
				float g = 0.25F + 0.75F * s.besar;
				float flick = s.jauh ? 1.0F : 1.0F + 0.06F * Mth.sin(s.waktu * 1.3F + s.fase);
				f.xScale = g * (s.varian == ApiKecilEntity.VARIAN_KAYU ? 1.1F : 1.0F);
				f.zScale = f.xScale;
				f.yScale = g * flick * (s.varian == ApiKecilEntity.VARIAN_MINYAK ? 1.2F : 1.0F);
				f.yRot = s.fase;
			}
		}
	}
}
