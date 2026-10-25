package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.AyamKotaEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:ayam_kota} (DESIGN 42.7.5, 42.12.4): the yard chicken. The rooster ({@link AyamKotaEntity#VARIAN_JAGO})
 * has the tall serrated comb, wattle, golden hackles and the black-green sickle tail; the hen the small comb and short
 * tail; the chick ({@link AyamKotaEntity#VARIAN_ANAK}) is the yellow down look at half size with a head half again as big
 * and no comb, wattle or tail. Textures {@code hewan/ayam_<jago|betina|anak>.png}.
 * <ul>
 * <li>{@link AyamKotaEntity#KEADAAN_DIAM}: walking legs with the chicken's head bob (the head stays, the body catches up),
 * jerky head turns while idle;</li>
 * <li>{@link AyamKotaEntity#KEADAAN_PATUK}: pecking, the head down 50 degrees for 6 ticks of every 16;</li>
 * <li>{@link AyamKotaEntity#KEADAAN_KEPAK}: a wing flap with a small hop;</li>
 * <li>{@link AyamKotaEntity#KEADAAN_KOKOK}: the crow: chest out, neck stretched up and back, the head trembling while it
 * calls (40 ticks);</li>
 * <li>{@link AyamKotaEntity#KEADAAN_KABUR}: running away, body leaning forward, wings flapping wildly.</li>
 * </ul>
 */
public class AyamRenderer extends HewanRenderer<AyamKotaEntity, HewanRenderer.State> {

	static final String[] JENIS = {"jago", "betina", "anak"};

	public AyamRenderer(EntityRendererProvider.Context context) {
		super(context, HewanRenderClient.AYAM, AyamRenderer::pose, 0.25F, 1.0F, HewanRenderClient.tekstur("ayam", JENIS));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected float skalaVarian(int varian) {
		return varian == AyamKotaEntity.VARIAN_ANAK ? AyamKotaEntity.SKALA_ANAK : 1.0F;
	}

	static void pose(KotaModel<State> m, State s) {
		boolean jago = s.varian == AyamKotaEntity.VARIAN_JAGO;
		boolean anak = s.varian == AyamKotaEntity.VARIAN_ANAK;
		m.p("jengger").visible = jago;
		m.p("ekor_jago").visible = jago;
		m.p("bulu_leher").visible = jago;
		m.p("jengger_kecil").visible = !jago && !anak;
		m.p("ekor_betina").visible = !jago && !anak;
		m.p("pial").visible = !anak;
		ModelPart badan = m.p("badan");
		ModelPart kepala = m.p("kepala");
		ModelPart sk = m.p("sayap_k");
		ModelPart skn = m.p("sayap_kn");
		ModelPart kk = m.p("kaki_k");
		ModelPart kkn = m.p("kaki_kn");
		if (anak) {
			kepala.xScale = 1.45F;
			kepala.yScale = 1.45F;
			kepala.zScale = 1.45F;
			kepala.y += 0.8F;
		}
		if (s.jauh) {
			return;
		}
		float t = s.umur;
		float laju = Math.min(1.0F, s.walkAnimationSpeed);
		float langkah = s.walkAnimationPos * 1.3F;
		kk.xRot = Mth.cos(langkah) * 1.2F * laju;
		kkn.xRot = -Mth.cos(langkah) * 1.2F * laju;
		// the head holds still while the body walks under it: bob forward and back with the steps
		kepala.z += Mth.sin(langkah * 2.0F) * 0.6F * laju;
		switch (s.keadaan) {
			case AyamKotaEntity.KEADAAN_PATUK -> {
				float p = denyut(t, 16.0F, 6.0F);
				kepala.xRot = rad(50.0F) * p;
				kepala.y += 0.8F * p;
				badan.xRot = 0.2F * p;
			}
			case AyamKotaEntity.KEADAAN_KEPAK -> {
				float f = Math.abs(Mth.sin(t * 1.3F));
				sk.zRot = 0.2F + 1.1F * f;
				skn.zRot = -0.2F - 1.1F * f;
				badan.y -= 0.8F * Math.abs(Mth.sin(t * 0.65F));
				kepala.xRot = -0.2F;
			}
			case AyamKotaEntity.KEADAAN_KOKOK -> {
				float p = sinus(maju(t, 0.0F, 8.0F)) * (1.0F - sinus(maju(t, 30.0F, 10.0F)));
				badan.xRot = -0.3F * p;
				kepala.xRot = -0.55F * p + (t > 8.0F && t < 30.0F ? Mth.sin(t * 2.2F) * 0.05F : 0.0F);
				kepala.y -= 0.8F * p;
				sk.zRot = 0.25F * p;
				skn.zRot = -0.25F * p;
			}
			case AyamKotaEntity.KEADAAN_KABUR -> {
				float f = Math.abs(Mth.sin(s.waktu * 1.5F));
				sk.zRot = 0.3F + 1.0F * f;
				skn.zRot = -0.3F - 1.0F * f;
				badan.xRot = 0.3F;
				kepala.xRot = -0.25F;
				kk.xRot = Mth.cos(s.waktu * 1.4F) * 1.3F;
				kkn.xRot = -Mth.cos(s.waktu * 1.4F) * 1.3F;
				badan.y -= Math.abs(Mth.sin(s.waktu * 1.4F)) * 0.5F;
			}
			default -> {
				// jerky head turns: a new angle every 12 ticks, snapped quickly
				int slot = Mth.floor((s.waktu + s.fase * 10.0F) / 12.0F);
				float u = ((s.waktu + s.fase * 10.0F) % 12.0F) / 2.0F;
				float lama = sudut(slot - 1);
				float baru = sudut(slot);
				kepala.yRot += campur(lama, baru, Math.min(1.0F, u)) * (1.0F - laju);
				tengok(kepala, s, 0.5F);
				sk.zRot = 0.05F * laju;
				skn.zRot = -0.05F * laju;
			}
		}
	}

	/** A stable pseudo-random head angle for a 12-tick slot. */
	static float sudut(int slot) {
		int h = slot * 0x9E3779B1;
		h ^= h >>> 15;
		return ((h & 1023) / 1023.0F - 0.5F) * 1.2F;
	}
}
