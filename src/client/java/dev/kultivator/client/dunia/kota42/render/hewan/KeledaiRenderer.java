package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.KeledaiKotaEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:keledai_kota} (DESIGN 42.7.5, 42.12.4): the mill and market donkey, the horse build drawn at
 * {@link #SKALA} with long ears, a short upright mane, the dark dorsal stripe and shoulder cross, a pale muzzle and belly,
 * a rope tail with a tuft, and a pack saddle with two sacks while loaded ({@code nilai() > 0.5}). Two coats, textures
 * {@code hewan/keledai_<abu|coklat>.png}.
 * <ul>
 * <li>{@link KeledaiKotaEntity#KEADAAN_DIAM}: walk from the speed, resting life (ears turning independently, tail);</li>
 * <li>{@link KeledaiKotaEntity#KEADAAN_KUNYAH}: chewing, the lower jaw grinding sideways;</li>
 * <li>{@link KeledaiKotaEntity#KEADAAN_DENGUS}: a snort, the head tossed and shaken;</li>
 * <li>{@link KeledaiKotaEntity#KEADAAN_HENTAK}: the stubborn stamp;</li>
 * <li>{@link KeledaiKotaEntity#KEADAAN_MERINGKIK}: the bray: head and neck stretched up, jaw wide open in the
 * hee-haw rhythm, ears laid back.</li>
 * </ul>
 */
public class KeledaiRenderer extends HewanRenderer<KeledaiKotaEntity, KudaRenderer.State> {

	static final String[] CORAK = {"abu", "coklat"};
	public static final float SKALA = 0.72F;

	public KeledaiRenderer(EntityRendererProvider.Context context) {
		super(context, HewanRenderClient.KELEDAI, KeledaiRenderer::pose, 0.6F, SKALA, HewanRenderClient.tekstur("keledai", CORAK));
	}

	@Override
	public KudaRenderer.State createRenderState() {
		return new KudaRenderer.State();
	}

	@Override
	public void extractRenderState(KeledaiKotaEntity entity, KudaRenderer.State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.beban = entity.nilai() > 0.5F;
	}

	static void pose(KotaModel<KudaRenderer.State> m, KudaRenderer.State s) {
		m.p("beban").visible = s.beban;
		m.p("kelopak").visible = s.kedip && !s.jauh;
		ModelPart tk = m.p("telinga_k");
		ModelPart tkn = m.p("telinga_kn");
		if (s.jauh) {
			return;
		}
		ModelPart leher = m.p("leher");
		ModelPart kepala = m.p("kepala");
		ModelPart rahang = m.p("rahang");
		float t = s.umur;
		KudaRenderer.langkah(m, s);
		KudaRenderer.idle(m, s);
		// donkey ears turn on their own, slowly, forward and back
		tk.xRot += Mth.sin(s.waktu * 0.05F + s.fase) * 0.3F;
		tkn.xRot += Mth.sin(s.waktu * 0.043F + s.fase + 1.7F) * 0.3F;
		switch (s.keadaan) {
			case KeledaiKotaEntity.KEADAAN_KUNYAH -> {
				rahang.yRot = Mth.sin(t * 0.5F) * 0.12F;
				rahang.xRot = 0.05F + Math.abs(Mth.sin(t * 0.5F)) * 0.06F;
				leher.xRot += 0.2F;
			}
			case KeledaiKotaEntity.KEADAAN_DENGUS -> {
				float p = denyut(t, 40.0F, 10.0F);
				kepala.xRot -= 0.35F * p;
				kepala.yRot = Mth.sin(t * 1.4F) * 0.18F * p;
				leher.xRot -= 0.15F * p;
			}
			case KeledaiKotaEntity.KEADAAN_HENTAK -> {
				KudaRenderer.hentak(m, t);
				tk.xRot += 0.6F;
				tkn.xRot += 0.6F;
			}
			case KeledaiKotaEntity.KEADAAN_MERINGKIK -> {
				float naik = sinus(maju(t, 0.0F, 10.0F)) * (1.0F - sinus(maju(t, 50.0F, 10.0F)));
				leher.xRot -= 0.55F * naik;
				kepala.xRot -= 0.6F * naik;
				// hee (open) - haw (wider), four times
				float u = t % 12.0F;
				float buka = u < 5.0F ? 0.25F : 0.5F;
				rahang.xRot = buka * naik * (t < 50.0F ? 1.0F : 0.0F) + 0.05F * Mth.sin(t * 1.9F) * naik;
				tk.xRot += 0.8F * naik;
				tkn.xRot += 0.8F * naik;
			}
			default -> tengok(leher, s, 0.4F);
		}
	}
}
