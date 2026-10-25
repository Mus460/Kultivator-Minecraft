package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.PanggungKelilingEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:panggung_keliling} (DESIGN 42.12.4, 42.7.1 "Rombongan keliling"): the travelling troupe's stage, 5 x 3
 * blocks: six planks on trestles with a pleated red skirt and side steps, four red poles with beams and a carved gold
 * valance, a painted backdrop per troupe ({@code latar_0..3}: opera mountains and sun, acrobats' red-gold rays, musicians'
 * plum branch, storyteller's dragon), gathered side curtains, a red cloth gable roof with a scalloped gold valance and
 * tassels, two big lanterns, and two pennant poles whose banners carry the troupe's glyph pair ({@code panji()} % 4:
 * 福寿, 春丰, 囍福, 寿春). Props per troupe ({@code alat_0..3}): one table two chairs, stacked benches with a hoop and a
 * spinning plate, a drum on its stand and a gong, the storyteller's table with teapot, fan and clapper block.
 * <p>
 * Assembly over {@link PanggungKelilingEntity#RAKIT_TICK} ticks with progress {@code nilai()}: floor (0-35 %), poles and
 * backdrop (35-65 %), roof unrolling (65-90 %), pennants, lanterns and props (90-100 %); reversed while
 * {@link PanggungKelilingEntity#KEADAAN_DIBONGKAR}; nothing is drawn while {@link PanggungKelilingEntity#KEADAAN_TERBONGKAR}
 * (bundled on the cart). Within 64 blocks the pennants flutter in three linked segments, the roof valance, lanterns,
 * hoop and gong swing; the lanterns glow at night. Textures {@code panggung.png} (+ {@code _pancar}).
 */
public class PanggungRenderer extends TemaBendaRenderer<PanggungKelilingEntity, PanggungRenderer.State, KotaModel<PanggungRenderer.State>> {

	public static class State extends TemaBendaRenderer.State {
		public float rakit = 1.0F;
		public int panji;
	}

	public PanggungRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.PANGGUNG), PanggungRenderer::pose),
				KotaRenderClient.tekstur("panggung"), KotaRenderClient.tekstur("panggung_pancar"), 2.2F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PanggungKelilingEntity entity) {
		return 1.5D;
	}

	@Override
	protected double batas() {
		return 160.0D;
	}

	@Override
	public void extractRenderState(PanggungKelilingEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.panji = Math.floorMod(entity.panji(), 4);
		state.rakit = switch (state.keadaan) {
			case PanggungKelilingEntity.KEADAAN_TERBONGKAR -> 0.0F;
			case PanggungKelilingEntity.KEADAAN_BERDIRI -> 1.0F;
			default -> Mth.clamp(state.nilai, 0.0F, 1.0F);
		};
		if (state.keadaan == PanggungKelilingEntity.KEADAAN_TERBONGKAR) {
			state.alpha = 0.0F;
		}
		state.pancar = state.rakit > 0.9F ? KotaRenderClient.malam(entity.level()) * KotaRenderClient.kedip(state.waktu, state.fase) : 0.0F;
	}

	static void pose(KotaModel<State> m, State s) {
		float p = s.rakit;
		m.p("lantai").yScale = Math.max(0.02F, KotaModel.maju(p, 0.0F, 0.35F));
		ModelPart tiang = m.p("tiang");
		tiang.visible = p > 0.35F;
		tiang.yScale = Math.max(0.02F, KotaModel.maju(p, 0.35F, 0.3F));
		ModelPart atap = m.p("atap");
		atap.visible = p > 0.65F;
		atap.zScale = Math.max(0.02F, KotaModel.maju(p, 0.65F, 0.25F));
		boolean akhir = p > 0.9F;
		int v = Mth.clamp(s.varian, 0, PanggungKelilingEntity.VARIAN_JUMLAH - 1);
		m.satu("latar_", 4, v);
		m.satu("alat_", 4, akhir ? v : -1);
		m.p("lentera_k").visible = akhir;
		m.p("lentera_kn").visible = akhir;
		for (String side : new String[] {"k", "kn"}) {
			ModelPart pole = m.p("panji_" + side);
			pole.visible = akhir;
			pole.yScale = Math.max(0.05F, KotaModel.maju(p, 0.9F, 0.1F));
			m.satu("panji_" + side + "_", 4, s.panji);
		}
		if (s.jauh || !akhir) {
			return;
		}
		for (int k = 0; k < 2; k++) {
			String side = k == 0 ? "k" : "kn";
			float dir = k == 0 ? 1.0F : -1.0F;
			ModelPart seg0 = m.p("panji_" + side + "_" + s.panji);
			float w = s.waktu * 0.21F + s.fase + k * 1.4F;
			seg0.yRot = dir * (Mth.sin(w) * 0.12F);
			for (int j = 1; j < 3; j++) {
				ModelPart seg = m.p("panji_" + side + "_" + s.panji + "_" + j);
				seg.yRot = dir * Mth.sin(w - j * 0.9F) * (0.22F + 0.08F * j);
			}
			ModelPart lamp = m.p(k == 0 ? "lentera_k" : "lentera_kn");
			lamp.zRot = KotaModel.ayun(s.waktu, s.fase + k * 2.0F, 0.06F, 0.06F);
			lamp.xRot = KotaModel.ayun(s.waktu, s.fase + k + 1.0F, 0.04F, 0.05F);
		}
		float r = Mth.sin(s.waktu * 0.16F + s.fase) * 0.1F;
		m.p("rumbai_atap_d").xRot += r;
		m.p("rumbai_atap_b").xRot -= r;
		m.p("atap_d").xRot += Mth.sin(s.waktu * 0.11F + s.fase) * 0.012F;
		if (v == PanggungKelilingEntity.VARIAN_AKROBAT) {
			m.p("simpai").zRot = Mth.sin(s.waktu * 0.05F) * 0.05F;
		}
		if (v == PanggungKelilingEntity.VARIAN_PEMUSIK) {
			m.p("gong").xRot = Mth.sin(s.waktu * 0.09F + s.fase) * 0.06F;
		}
	}
}
