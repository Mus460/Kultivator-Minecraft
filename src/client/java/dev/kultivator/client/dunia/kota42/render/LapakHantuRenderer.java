package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.ekonomi.LapakHantuEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:lapak_hantu} (DESIGN 42.12.4, 42.6.7 "Pasar Hantu"): a worn straw mat with two old lacquered chests
 * (one lid thrown open over a glint of coins), a bent bamboo pole hanging the green paper lantern, three incense sticks
 * smouldering in a bronze burner, a green candle stub and the masked seller's stool; on the mat the goods of the variant
 * ({@code barang_0..3}: talismans and jade, glazed jars, scrolls, a sword with a bronze mirror and a ding). The lantern,
 * candle flame and incense tips glow all night (the market only opens at the fifth watch) and flicker; the lantern sways
 * on its hook. At dawn ({@link LapakHantuEntity#KEADAAN_MEMUDAR}) the whole stall fades with the synced opacity
 * {@code nilai()} and the lantern dims with it. The thin mist is 42V-F's. Textures {@code lapak_hantu.png} (+ {@code _pancar}).
 */
public class LapakHantuRenderer extends TemaBendaRenderer<LapakHantuEntity, TemaBendaRenderer.State, KotaModel<TemaBendaRenderer.State>> {

	public LapakHantuRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.LAPAK_HANTU), LapakHantuRenderer::pose),
				KotaRenderClient.tekstur("lapak_hantu"), KotaRenderClient.tekstur("lapak_hantu_pancar"), 0.7F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(LapakHantuEntity entity) {
		return 0.6D;
	}

	@Override
	public void extractRenderState(LapakHantuEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.alpha = state.keadaan == LapakHantuEntity.KEADAAN_MEMUDAR ? Mth.clamp(state.nilai, 0.0F, 1.0F) : 1.0F;
		state.pancar = state.alpha * KotaRenderClient.kedip(state.waktu, state.fase) * (0.85F + 0.15F * Mth.sin(state.waktu * 0.05F + state.fase));
	}

	static void pose(KotaModel<State> m, State s) {
		m.satu("barang_", LapakHantuEntity.VARIAN_JUMLAH, Mth.clamp(s.varian, 0, LapakHantuEntity.VARIAN_JUMLAH - 1));
		if (s.jauh) {
			return;
		}
		ModelPart l = m.p("lentera");
		l.zRot = KotaModel.ayun(s.waktu, s.fase, 0.09F, 0.05F);
		l.xRot = KotaModel.ayun(s.waktu, s.fase + 1.9F, 0.06F, 0.04F);
		// the bamboo arm gives a little under the swinging lantern
		m.p("lengan").xRot += Mth.sin(s.waktu * 0.05F + s.fase) * 0.015F;
	}
}
