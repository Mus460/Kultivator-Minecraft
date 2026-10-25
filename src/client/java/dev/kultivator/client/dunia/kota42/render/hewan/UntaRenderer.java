package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.UntaTambatEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:unta_tambat} (DESIGN 42.7.5, 42.12.4): the tethered Bactrian camel of the oasis town and the desert
 * post stations in two coats (sand, dark brown; textures {@code hewan/unta_<pasir|coklat_tua>.png}): two humps with shaggy
 * crowns, an S-curved neck with a throat mane, a split lip and a side-chewing jaw, a kilim saddle cloth with tassels, a rope
 * halter with a bell.
 * <ul>
 * <li>{@link UntaTambatEntity#KEADAAN_DIAM}: the pace (both legs of one side together) from the speed, with the body
 * roll; resting: the neck swaying slowly, ears and tail;</li>
 * <li>{@link UntaTambatEntity#KEADAAN_KUNYAH}: chewing the cud, the jaw grinding sideways;</li>
 * <li>{@link UntaTambatEntity#KEADAAN_BERLUTUT}: kneeling down: the front legs fold first (20 ticks), then the hind legs
 * (20), the body sinking onto its chest pad; lying it chews slowly;</li>
 * <li>{@link UntaTambatEntity#KEADAAN_DENGUS}: a snort, the head tossed up, ears back.</li>
 * </ul>
 * Standing up again is instant when the state leaves {@code BERLUTUT} (there is no synced get-up state).
 */
public class UntaRenderer extends HewanRenderer<UntaTambatEntity, HewanRenderer.State> {

	static final String[] CORAK = {"pasir", "coklat_tua"};
	/** The body sinks this many model pixels when fully kneeling (belly on the ground). */
	static final float TURUN = 12.0F;

	public UntaRenderer(EntityRendererProvider.Context context) {
		super(context, HewanRenderClient.UNTA, UntaRenderer::pose, 0.9F, 1.0F, HewanRenderClient.tekstur("unta", CORAK));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	static void pose(KotaModel<State> m, State s) {
		boolean lutut = s.keadaan == UntaTambatEntity.KEADAAN_BERLUTUT;
		m.p("kelopak").visible = s.kedip && !s.jauh;
		ModelPart badan = m.p("badan");
		ModelPart l1 = m.p("leher_1");
		ModelPart l2 = m.p("leher_2");
		ModelPart kepala = m.p("kepala");
		ModelPart rahang = m.p("rahang");
		float t = s.umur;
		if (lutut) {
			float depan = s.jauh ? 1.0F : sinus(maju(t, 0.0F, 20.0F));
			float belakang = s.jauh ? 1.0F : sinus(maju(t, 20.0F, 20.0F));
			berlutut(m, depan, belakang);
			if (!s.jauh) {
				rahang.yRot = Mth.sin(t * 0.25F) * 0.08F;
			}
			return;
		}
		if (s.jauh) {
			return;
		}
		// the pace: both legs of one side swing together, with a roll of the body
		float laju = Math.min(1.0F, s.walkAnimationSpeed);
		float pos = s.walkAnimationPos * 0.55F;
		float a = Mth.cos(pos) * 0.5F * laju;
		m.p("kaki_dk").xRot += a;
		m.p("kaki_bk").xRot += a;
		m.p("kaki_dkn").xRot -= a;
		m.p("kaki_bkn").xRot -= a;
		m.p("betis_dk").xRot += Math.max(0.0F, -Mth.sin(pos)) * 0.6F * laju;
		m.p("betis_dkn").xRot += Math.max(0.0F, Mth.sin(pos)) * 0.6F * laju;
		badan.zRot = Mth.sin(pos) * 0.04F * laju;
		l2.xRot += Mth.sin(pos * 2.0F) * 0.06F * laju;
		// resting life: neck sway, ears, tail
		l1.yRot = Mth.sin(s.waktu * 0.03F + s.fase) * 0.08F;
		m.p("ekor").zRot = kibas(s, 0.12F, 0.09F);
		m.p("telinga_k").zRot -= kedut(s, 0.0F) * 0.3F;
		m.p("telinga_kn").zRot += kedut(s, 37.0F) * 0.3F;
		switch (s.keadaan) {
			case UntaTambatEntity.KEADAAN_KUNYAH -> {
				rahang.yRot = Mth.sin(t * 0.45F) * 0.13F;
				rahang.xRot = 0.04F + Math.abs(Mth.sin(t * 0.45F)) * 0.05F;
			}
			case UntaTambatEntity.KEADAAN_DENGUS -> {
				float p = denyut(t, 36.0F, 12.0F);
				kepala.xRot -= 0.4F * p;
				l2.xRot -= 0.12F * p;
				m.p("telinga_k").xRot += 0.5F * p;
				m.p("telinga_kn").xRot += 0.5F * p;
				rahang.xRot = 0.12F * p;
			}
			default -> tengok(kepala, s, 0.6F);
		}
	}

	/**
	 * Kneeling: {@code depan}/{@code belakang} 0..1 fold the front and hind legs (forearm forward on the ground, cannon folded
	 * back under it; hind thigh back, shin folded forward) and sink that end of the body.
	 */
	static void berlutut(KotaModel<State> m, float depan, float belakang) {
		ModelPart badan = m.p("badan");
		badan.y += TURUN * 0.5F * (depan + belakang);
		badan.xRot = 0.32F * (depan - belakang);
		for (String sisi : new String[] {"_dk", "_dkn"}) {
			ModelPart paha = m.p("kaki" + sisi);
			paha.y += TURUN * depan;
			paha.xRot = -1.5F * depan;
			m.p("betis" + sisi).xRot = 2.95F * depan;
		}
		for (String sisi : new String[] {"_bk", "_bkn"}) {
			ModelPart paha = m.p("kaki" + sisi);
			paha.y += TURUN * belakang;
			paha.xRot = 1.5F * belakang;
			m.p("betis" + sisi).xRot = -2.95F * belakang;
		}
		// the head stays carried up while the body sinks
		m.p("leher_1").xRot -= 0.25F * Math.max(depan, belakang);
	}
}
