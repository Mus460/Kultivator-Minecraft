package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.AnjingKotaEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:anjing_kota} (DESIGN 42.7.5, 42.12.4): the town dog in four coats (yellow village dog, black
 * "four-eyed" dog with tan brows, white piebald, and the big shaggy guard dog drawn 1.3 times larger with its fur layer,
 * neck ruff and drop ears; textures {@code hewan/anjing_<coat>.png}), a red rope collar with a bell, hinged jaw and a tail
 * curled over the back.
 * <ul>
 * <li>{@link AnjingKotaEntity#KEADAAN_DIAM}: trot from the speed, a lazy tail sway, the head following the look;</li>
 * <li>{@link AnjingKotaEntity#KEADAAN_DUDUK}: sitting on its haunches, the tail sweeping the ground;</li>
 * <li>{@link AnjingKotaEntity#KEADAAN_GONGGONG}: barking, the jaw opening 20 degrees on two quick barks then a pause,
 * the head jerking with each bark, ears pricked;</li>
 * <li>{@link AnjingKotaEntity#KEADAAN_TIDUR}: lying with the chin on the stretched front paws, eyes shut, slow breath;</li>
 * <li>{@link AnjingKotaEntity#KEADAAN_KIBAS}: the happy wag, tail +-35 degrees fast, the rump wiggling, tongue out.</li>
 * </ul>
 */
public class AnjingRenderer extends HewanRenderer<AnjingKotaEntity, HewanRenderer.State> {

	static final String[] CORAK = {"kuning", "hitam", "belang", "penjaga"};

	public AnjingRenderer(EntityRendererProvider.Context context) {
		super(context, HewanRenderClient.ANJING, AnjingRenderer::pose, 0.4F, 1.0F, HewanRenderClient.tekstur("anjing", CORAK));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected float skalaVarian(int varian) {
		return varian == AnjingKotaEntity.VARIAN_PENJAGA ? AnjingKotaEntity.SKALA_PENJAGA : 1.0F;
	}

	static void pose(KotaModel<State> m, State s) {
		ModelPart badan = m.p("badan");
		ModelPart kepala = m.p("kepala");
		ModelPart rahang = m.p("rahang");
		ModelPart e1 = m.p("ekor_1");
		ModelPart dk = m.p("kaki_dk");
		ModelPart dkn = m.p("kaki_dkn");
		ModelPart bk = m.p("kaki_bk");
		ModelPart bkn = m.p("kaki_bkn");
		ModelPart tk = m.p("telinga_k");
		ModelPart tkn = m.p("telinga_kn");
		boolean penjaga = s.varian == AnjingKotaEntity.VARIAN_PENJAGA;
		boolean tidur = s.keadaan == AnjingKotaEntity.KEADAAN_TIDUR;
		m.p("bulu_lebat").visible = penjaga;
		m.p("kelopak").visible = tidur || (s.kedip && !s.jauh);
		if (penjaga) {
			// the mastiff's drop ears
			tk.zRot = -2.15F;
			tkn.zRot = 2.15F;
			tk.y += 0.6F;
			tkn.y += 0.6F;
		}
		if (s.jauh) {
			return;
		}
		float t = s.umur;
		if (!penjaga) {
			tk.zRot += kedut(s, 0.0F) * 0.3F;
			tkn.zRot -= kedut(s, 23.0F) * 0.3F;
		}
		switch (s.keadaan) {
			case AnjingKotaEntity.KEADAAN_DUDUK -> {
				duduk(badan, kepala, dk, dkn, bk, bkn);
				e1.xRot = -0.2F;
				e1.yRot = Mth.sin(s.waktu * 0.15F + s.fase) * 0.5F;
				tengok(kepala, s, 1.0F);
				rahang.xRot = 0.08F + 0.04F * Mth.sin(s.waktu * 0.5F);   // panting
			}
			case AnjingKotaEntity.KEADAAN_GONGGONG -> {
				float u = t % 22.0F;
				float buka = (u < 4.0F ? Mth.sin(u / 4.0F * Mth.PI) : 0.0F) + (u > 6.0F && u < 10.0F ? Mth.sin((u - 6.0F) / 4.0F * Mth.PI) : 0.0F);
				rahang.xRot = 0.35F * buka;
				kepala.xRot -= 0.18F * buka;
				kepala.z -= 0.4F * buka;
				badan.y -= 0.25F * buka;
				if (!penjaga) {
					tk.zRot -= 0.1F;
					tkn.zRot += 0.1F;
				}
				dk.xRot = -0.12F;
				dkn.xRot = -0.12F;
				e1.yRot = kibas(s, 0.15F, 0.3F);
				tengok(kepala, s, 0.6F);
			}
			case AnjingKotaEntity.KEADAAN_TIDUR -> {
				badan.y += 4.2F;
				for (ModelPart k : new ModelPart[] {bk, bkn}) {
					k.y += 3.6F;
					k.xRot = -1.45F;
				}
				dk.y += 4.0F;
				dkn.y += 4.0F;
				dk.xRot = -1.5F;
				dkn.xRot = -1.5F;
				kepala.xRot = 0.35F;
				kepala.y += 1.6F;
				kepala.yRot = 0.15F;
				e1.xRot = -0.6F;
				e1.yRot = 1.2F;
				m.p("ekor_2").xRot = 0.2F;
				m.p("ekor_3").xRot = 0.1F;
				float napas = 1.0F + 0.03F * Mth.sin(s.waktu * 0.09F + s.fase);
				badan.xScale = napas;
				badan.yScale = napas;
			}
			case AnjingKotaEntity.KEADAAN_KIBAS -> {
				derap(dk, dkn, bk, bkn, s, 0.8F, 1.0F);
				e1.yRot = Mth.sin(s.waktu * 1.6F) * rad(35.0F);
				m.p("ekor_2").zRot = Mth.sin(s.waktu * 1.6F - 0.6F) * 0.25F;
				badan.yRot = Mth.sin(s.waktu * 1.6F) * 0.06F;
				rahang.xRot = 0.18F;
				kepala.zRot = Mth.sin(s.waktu * 0.4F) * 0.08F;
				tengok(kepala, s, 1.0F);
			}
			default -> {
				float laju = Math.min(1.0F, s.walkAnimationSpeed);
				derap(dk, dkn, bk, bkn, s, 0.8F, 1.0F);
				badan.y -= Math.abs(Mth.sin(s.walkAnimationPos * 0.8F)) * 0.35F * laju;
				e1.yRot = kibas(s, 0.25F, 0.12F + 0.25F * laju);
				rahang.xRot = 0.1F * laju;
				tengok(kepala, s, 1.0F);
			}
		}
	}

	static void duduk(ModelPart badan, ModelPart kepala, ModelPart dk, ModelPart dkn, ModelPart bk, ModelPart bkn) {
		badan.xRot = -0.55F;
		badan.y += 2.0F;
		kepala.xRot = 0.5F;
		dk.y -= 0.3F;
		dkn.y -= 0.3F;
		dk.xRot = -0.1F;
		dkn.xRot = -0.1F;
		bk.y += 3.4F;
		bkn.y += 3.4F;
		bk.z -= 1.0F;
		bkn.z -= 1.0F;
		bk.xRot = -1.4F;
		bkn.xRot = -1.4F;
	}
}
