package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.BarangTercecerEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:barang_tercecer} (DESIGN 42.12.4, 42.7.4 "gerobak terguling"): goods spilled on the street, one of five
 * ({@code jenis_0..4}): a cabbage with a leek, a jar rolled on its side with its lid and spilled rice, a tied sack with
 * scattered beans, oranges with a peach and a pear, a half unrolled bolt of cloth. Each lies turned by the synced roll
 * {@code nilai()} (a fraction of a full turn) so a spill never looks stamped. Picked up
 * ({@link BarangTercecerEntity#KEADAAN_DIPUNGUT}) it lifts, tilts and shrinks away over
 * {@link BarangTercecerEntity#PUNGUT_TICK} ticks. Texture {@code barang.png}.
 */
public class BarangTercecerRenderer extends TemaBendaRenderer<BarangTercecerEntity, TemaBendaRenderer.State, KotaModel<TemaBendaRenderer.State>> {

	public BarangTercecerRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.BARANG), BarangTercecerRenderer::pose),
				KotaRenderClient.tekstur("barang"), null, 0.25F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double batas() {
		return 48.0D;
	}

	static void pose(KotaModel<State> m, State s) {
		int v = Mth.clamp(s.varian, 0, BarangTercecerEntity.VARIAN_JUMLAH - 1);
		m.satu("jenis_", BarangTercecerEntity.VARIAN_JUMLAH, v);
		ModelPart root = m.root();
		root.yRot = s.nilai * Mth.TWO_PI;
		if (s.keadaan == BarangTercecerEntity.KEADAAN_DIPUNGUT) {
			float t = KotaModel.maju(s.umur, 0.0F, BarangTercecerEntity.PUNGUT_TICK);
			root.y -= t * 9.0F;
			root.zRot = t * 0.6F;
			float k = 1.0F - t * t;
			root.xScale = root.yScale = root.zScale = Math.max(0.05F, k);
		}
	}
}
