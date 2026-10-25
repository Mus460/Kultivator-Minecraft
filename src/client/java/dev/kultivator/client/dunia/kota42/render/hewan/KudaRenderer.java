package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.dunia.kota42.hewan.KudaTambatEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:kuda_tambat} (DESIGN 42.7.5, 42.12.4, 42.7.7): the tethered riding horse in four coats (bay with
 * black points, black, dapple grey, white; textures {@code hewan/kuda_<coat>.png}), saddle with blanket and stirrups,
 * bridle with bit rings, an eight-card mane and a crossed hair-card tail; with {@link KudaTambatEntity#hias()} it wears the
 * wedding finery (red cloth with a gold border, ribbons, a red silk flower on the brow) for the groom's ride.
 * <ul>
 * <li>{@link KudaTambatEntity#KEADAAN_DIAM}: a four-beat walk from the speed (knees and fetlocks flex on the forward
 * swing), otherwise resting: weight shifts, ear flicks, a slow tail sway, the head following the look;</li>
 * <li>{@link KudaTambatEntity#KEADAAN_KIBAS_EKOR}: swishing flies with the tail, skin twitch;</li>
 * <li>{@link KudaTambatEntity#KEADAAN_ANGGUK}: nodding the head (and when the caravan arrives, a whinny with the head up);</li>
 * <li>{@link KudaTambatEntity#KEADAAN_MAKAN}: neck down into the trough, chewing;</li>
 * <li>{@link KudaTambatEntity#KEADAAN_HENTAK}: stamping a front hoof twice, with a head toss.</li>
 * </ul>
 */
public class KudaRenderer extends HewanRenderer<KudaTambatEntity, KudaRenderer.State> {

	static final String[] CORAK = {"coklat", "hitam", "abu_belang", "putih"};

	/** The horse state: the finery flag and (for the donkey) the pack load. */
	public static class State extends HewanRenderer.State {
		public boolean hias;
		public boolean beban;
	}

	public KudaRenderer(EntityRendererProvider.Context context) {
		super(context, HewanRenderClient.KUDA, KudaRenderer::pose, 0.8F, 1.0F, HewanRenderClient.tekstur("kuda", CORAK));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(KudaTambatEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.hias = entity.hias();
	}

	static void pose(KotaModel<State> m, State s) {
		m.p("hias").visible = s.hias;
		m.p("bunga").visible = s.hias;
		m.p("kelopak").visible = s.kedip && !s.jauh;
		if (s.jauh) {
			return;
		}
		ModelPart leher = m.p("leher");
		ModelPart kepala = m.p("kepala");
		ModelPart ekor = m.p("ekor");
		float t = s.umur;
		langkah(m, s);
		idle(m, s);
		switch (s.keadaan) {
			case KudaTambatEntity.KEADAAN_KIBAS_EKOR -> {
				ekor.yRot = Mth.sin(t * 0.55F) * 0.65F;
				ekor.xRot += 0.25F + 0.1F * Mth.sin(t * 1.1F);
				m.p("ekor_rambut").zRot = Mth.sin(t * 0.55F - 0.8F) * 0.4F;
				m.p("badan").zRot = denyut(t, 30.0F, 4.0F) * 0.02F;   // skin twitch
			}
			case KudaTambatEntity.KEADAAN_ANGGUK -> {
				float n = Mth.sin(t * 0.45F) * (1.0F - maju(t, 40.0F, 10.0F));
				leher.xRot += 0.18F * n;
				kepala.xRot += 0.22F * n;
				m.p("telinga_k").xRot -= 0.25F;
				m.p("telinga_kn").xRot -= 0.25F;
			}
			case KudaTambatEntity.KEADAAN_MAKAN -> {
				float turun = sinus(maju(t, 0.0F, 15.0F));
				leher.xRot += 1.05F * turun;
				kepala.xRot += 0.35F * turun;
				m.p("rahang").xRot = Math.abs(Mth.sin(t * 0.45F)) * 0.14F * turun;
			}
			case KudaTambatEntity.KEADAAN_HENTAK -> hentak(m, t);
			default -> tengok(leher, s, 0.4F);
		}
	}

	/** The walk: legs swing in a four-beat walk, the knee/hock below flexes on the forward swing. */
	static void langkah(KotaModel<? extends HewanRenderer.State> m, HewanRenderer.State s) {
		float laju = Math.min(1.0F, s.walkAnimationSpeed);
		if (laju < 0.01F) {
			return;
		}
		float pos = s.walkAnimationPos * 0.6F;
		String[] kaki = {"_dk", "_bkn", "_dkn", "_bk"};
		for (int i = 0; i < 4; i++) {
			float ph = pos + i * Mth.HALF_PI;
			m.p("kaki" + kaki[i]).xRot += Mth.cos(ph) * 0.55F * laju;
			m.p("betis" + kaki[i]).xRot += Math.max(0.0F, -Mth.sin(ph)) * 0.7F * laju * (i % 2 == 0 ? 1.0F : -1.0F);
		}
		m.p("leher").xRot += Mth.sin(pos * 2.0F) * 0.05F * laju;
		m.p("badan").y += Mth.sin(pos * 2.0F) * 0.3F * laju;
	}

	/** Resting life: tail sway, ear flicks, breathing. */
	static void idle(KotaModel<? extends HewanRenderer.State> m, HewanRenderer.State s) {
		m.p("ekor").yRot += kibas(s, 0.12F, 0.07F);
		m.p("telinga_k").zRot -= kedut(s, 0.0F) * 0.4F;
		m.p("telinga_kn").zRot += kedut(s, 41.0F) * 0.4F;
		ModelPart badan = m.p("badan");
		float napas = 1.0F + 0.012F * Mth.sin(s.waktu * 0.08F + s.fase);
		badan.xScale = napas;
		badan.yScale = napas;
	}

	/** Stamping the right front hoof twice with a toss of the head. */
	static void hentak(KotaModel<? extends HewanRenderer.State> m, float t) {
		float p = t < 40.0F ? Math.max(0.0F, Mth.sin(t * 0.32F)) : 0.0F;
		m.p("kaki_dkn").xRot -= 0.55F * p;
		m.p("betis_dkn").xRot += 1.05F * p;
		m.p("kepala").xRot -= 0.25F * p;
		m.p("leher").xRot -= 0.12F * p;
	}
}
