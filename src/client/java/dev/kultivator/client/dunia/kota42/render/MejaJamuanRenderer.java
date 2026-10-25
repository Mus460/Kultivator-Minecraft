package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.MejaJamuanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:meja_jamuan} (DESIGN 42.12.4, 42.7.7): the round banquet table: a 16-sided top under a cloth with four
 * hanging flaps, a pedestal and cross foot, eight blue-and-white porcelain stools, a lacquered turntable with a soup
 * tureen, and six plates whose dishes ({@code hidangan_0..5}: whole fish, chicken, dumplings, greens, braised pork,
 * noodles) show by the synced mask {@link MejaJamuanEntity#hidangan()}; as the meal goes on ({@code nilai()}) the wine cups
 * appear seat by seat and bones pile up past 60 %. The cloth is the variant: red with gold 囍 flaps for a wedding
 * ({@link MejaJamuanEntity#VARIAN_PERNIKAHAN}), white with 奠 for a funeral meal ({@link MejaJamuanEntity#VARIAN_DUKA}),
 * plain tan with 福 for a feast ({@link MejaJamuanEntity#VARIAN_PESTA}). Textures {@code jamuan_merah.png},
 * {@code jamuan_putih.png}, {@code jamuan_polos.png}.
 */
public class MejaJamuanRenderer extends TemaBendaRenderer<MejaJamuanEntity, MejaJamuanRenderer.State, KotaModel<MejaJamuanRenderer.State>> {

	public static class State extends TemaBendaRenderer.State {
		public int hidangan = MejaJamuanEntity.SEMUA_PIRING;
	}

	public MejaJamuanRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.JAMUAN), MejaJamuanRenderer::pose),
				new Identifier[] {KotaRenderClient.tekstur("jamuan_merah"), KotaRenderClient.tekstur("jamuan_putih"),
						KotaRenderClient.tekstur("jamuan_polos")}, null, 1.1F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(MejaJamuanEntity entity) {
		return 0.6D;
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, MejaJamuanEntity.VARIAN_JUMLAH - 1);
	}

	@Override
	public void extractRenderState(MejaJamuanEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.hidangan = entity.hidangan();
	}

	static void pose(KotaModel<State> m, State s) {
		for (int i = 0; i < MejaJamuanEntity.PIRING; i++) {
			m.p("hidangan_" + i).visible = (s.hidangan & (1 << i)) != 0;
		}
		float makan = Mth.clamp(s.nilai, 0.0F, 1.0F);
		for (int i = 0; i < 8; i++) {
			m.p("cawan_" + i).visible = makan > 0.05F + i * 0.06F;
		}
		m.p("tulang").visible = makan > 0.6F;
	}
}
