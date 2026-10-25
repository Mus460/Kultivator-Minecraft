package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.KucingKotaEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:kucing_kota} (DESIGN 42.7.5, 42.12.4): the town cat in six coats (orange tabby, black, white, grey
 * tabby, tuxedo, calico; textures {@code hewan/kucing_<coat>.png}), slit-pupil eyes with eyelids, whiskers and a pink
 * tongue.
 * <ul>
 * <li>{@link KucingKotaEntity#KEADAAN_DIAM}: walk from the speed (opposite legs together), tail raised while walking and
 * swaying on a sine, the head following the look;</li>
 * <li>{@link KucingKotaEntity#KEADAAN_DUDUK}: sitting, hind legs folded under, the tail wrapped round the front paws with
 * a flicking tip;</li>
 * <li>{@link KucingKotaEntity#KEADAAN_TIDUR}: curled on the ground, head turned along the flank, the tail laid round the
 * nose, eyes shut, slow breathing;</li>
 * <li>{@link KucingKotaEntity#KEADAAN_MENJILAT}: sitting, one front paw raised and licked (tongue in and out);</li>
 * <li>{@link KucingKotaEntity#KEADAAN_MEREGANG}: the stretch, chest down 20 degrees, front legs reaching, rump and tail up
 * (one shot, 50 ticks);</li>
 * <li>{@link KucingKotaEntity#KEADAAN_LOMPAT}: the two-block jump: crouch, spring with legs stretched, tuck to land.</li>
 * </ul>
 * Ears twitch now and then; blinking is client-only.
 */
public class KucingRenderer extends HewanRenderer<KucingKotaEntity, HewanRenderer.State> {

	static final String[] CORAK = {"oranye", "hitam", "putih", "belang_abu", "hitam_putih", "kaliko"};

	public KucingRenderer(EntityRendererProvider.Context context) {
		super(context, HewanRenderClient.KUCING, KucingRenderer::pose, 0.3F, 1.0F, HewanRenderClient.tekstur("kucing", CORAK));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	static void pose(KotaModel<State> m, State s) {
		ModelPart badan = m.p("badan");
		ModelPart kepala = m.p("kepala");
		ModelPart e1 = m.p("ekor_1");
		ModelPart e2 = m.p("ekor_2");
		ModelPart dk = m.p("kaki_dk");
		ModelPart dkn = m.p("kaki_dkn");
		ModelPart bk = m.p("kaki_bk");
		ModelPart bkn = m.p("kaki_bkn");
		boolean tidur = s.keadaan == KucingKotaEntity.KEADAAN_TIDUR;
		m.p("kelopak").visible = tidur || (s.kedip && !s.jauh);
		m.p("lidah").visible = false;
		if (s.jauh) {
			return;
		}
		float t = s.umur;
		// ears twitch, whiskers quiver
		m.p("telinga_k").zRot += kedut(s, 0.0F) * 0.35F;
		m.p("telinga_kn").zRot -= kedut(s, 29.0F) * 0.35F;
		m.p("kumis_k").yRot += Mth.sin(s.waktu * 0.3F + s.fase) * 0.05F;
		m.p("kumis_kn").yRot -= Mth.sin(s.waktu * 0.3F + s.fase) * 0.05F;
		switch (s.keadaan) {
			case KucingKotaEntity.KEADAAN_DUDUK -> {
				duduk(badan, kepala, dk, dkn, bk, bkn);
				ekorMelingkar(e1, e2, s);
				tengok(kepala, s, 1.0F);
			}
			case KucingKotaEntity.KEADAAN_TIDUR -> {
				badan.y += 3.6F;
				badan.yRot = 0.35F;
				for (ModelPart k : new ModelPart[] {dk, dkn, bk, bkn}) {
					k.y += 3.1F;
					k.xRot = -1.45F;
				}
				dk.z -= 0.6F;
				dkn.z -= 0.6F;
				kepala.yRot = 1.15F;
				kepala.xRot = 0.35F;
				kepala.y += 1.2F;
				m.p("telinga_k").zRot += 0.35F;
				m.p("telinga_kn").zRot -= 0.35F;
				e1.xRot = -0.25F;
				e1.yRot = 1.35F;
				e2.xRot = 0.0F;
				e2.yRot = 1.2F + Mth.sin(s.waktu * 0.05F + s.fase) * 0.08F;
				float napas = 1.0F + 0.035F * Mth.sin(s.waktu * 0.1F + s.fase);
				badan.yScale = napas;
				badan.xScale = napas;
			}
			case KucingKotaEntity.KEADAAN_MENJILAT -> {
				duduk(badan, kepala, dk, dkn, bk, bkn);
				ekorMelingkar(e1, e2, s);
				dk.xRot = -1.25F + Mth.sin(t * 0.25F) * 0.08F;
				dk.y -= 0.6F;
				kepala.xRot += 0.65F + Mth.sin(t * 0.8F) * 0.12F;
				kepala.yRot = -0.3F;
				m.p("lidah").visible = (t % 10.0F) < 6.0F;
			}
			case KucingKotaEntity.KEADAAN_MEREGANG -> {
				float p = sinus(maju(t, 0.0F, 12.0F)) * (1.0F - sinus(maju(t, 36.0F, 14.0F)));
				badan.xRot = 0.35F * p;
				badan.y += 0.6F * p;
				dk.xRot = -1.05F * p;
				dkn.xRot = -1.05F * p;
				dk.y += 1.2F * p;
				dkn.y += 1.2F * p;
				dk.z -= 0.8F * p;
				dkn.z -= 0.8F * p;
				bk.xRot = 0.25F * p;
				bkn.xRot = 0.25F * p;
				kepala.xRot -= 0.25F * p;
				e1.xRot += 1.1F * p;
				e1.yRot = kibas(s, 0.1F, 0.1F);
				m.p("lidah").visible = p > 0.7F && t > 14.0F && t < 30.0F;
			}
			case KucingKotaEntity.KEADAAN_LOMPAT -> {
				float jongkok = sinus(maju(t, 0.0F, 6.0F)) * (1.0F - maju(t, 6.0F, 2.0F));
				float lompat = maju(t, 6.0F, 3.0F) * (1.0F - maju(t, 14.0F, 4.0F));
				float darat = maju(t, 14.0F, 4.0F) * (1.0F - maju(t, 20.0F, 6.0F));
				badan.y += 1.4F * jongkok + 0.8F * darat;
				badan.xRot = -0.35F * lompat + 0.15F * jongkok;
				dk.xRot = -1.0F * lompat + 0.3F * jongkok;
				dkn.xRot = -1.0F * lompat + 0.3F * jongkok;
				bk.xRot = 1.0F * lompat - 0.6F * jongkok;
				bkn.xRot = 1.0F * lompat - 0.6F * jongkok;
				bk.y += 1.0F * jongkok;
				bkn.y += 1.0F * jongkok;
				e1.xRot += 0.6F * lompat;
				kepala.xRot += 0.2F * lompat - 0.15F * jongkok;
			}
			default -> {
				float laju = Math.min(1.0F, s.walkAnimationSpeed);
				derap(dk, dkn, bk, bkn, s, 0.9F, 1.1F);
				badan.y += Math.abs(Mth.sin(s.walkAnimationPos * 0.9F)) * -0.3F * laju;
				e1.xRot += 0.9F * laju + 0.15F;
				e1.yRot = kibas(s, 0.35F, 0.12F);
				e2.yRot = Mth.sin(s.waktu * 0.12F + s.fase - 0.9F) * 0.45F;
				e2.xRot -= 0.4F * laju;
				tengok(kepala, s, 1.0F);
			}
		}
	}

	/** Sitting: front up, rear down, hind legs folded forward under the body, the head levelled. */
	static void duduk(ModelPart badan, ModelPart kepala, ModelPart dk, ModelPart dkn, ModelPart bk, ModelPart bkn) {
		badan.xRot = -0.5F;
		badan.y += 1.6F;
		kepala.xRot = 0.5F;
		dk.xRot = -0.08F;
		dkn.xRot = -0.08F;
		dk.y -= 0.4F;
		dkn.y -= 0.4F;
		bk.y += 3.0F;
		bkn.y += 3.0F;
		bk.z -= 0.6F;
		bkn.z -= 0.6F;
		bk.xRot = -1.45F;
		bkn.xRot = -1.45F;
	}

	/** The tail laid along the ground round the paws, its tip flicking. */
	static void ekorMelingkar(ModelPart e1, ModelPart e2, State s) {
		e1.xRot = -0.95F;
		e1.yRot = 1.5F;
		e2.xRot = 0.35F;
		e2.yRot = 1.3F + Mth.sin(s.waktu * 0.2F + s.fase) * 0.25F;
	}
}
