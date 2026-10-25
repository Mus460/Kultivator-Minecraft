package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.UsunganPetiEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:usungan_peti} (DESIGN 42.12.4, 42.7.7 funeral): a traditional coffin, higher and wider at the head,
 * lacquered black ({@link UsunganPetiEntity#VARIAN_HITAM}, white paper flowers along the lid) or red for the "red joy"
 * funeral of an elder past eighty ({@link UsunganPetiEntity#VARIAN_MERAH}, a red silk flower ball with ribbons), gold
 * cloud bands along the sides, a 寿 roundel on the head and a 福 on the foot, bound with rope to three cross bars on two
 * 56 px poles with gold end caps.
 * <ul>
 * <li>{@link UsunganPetiEntity#KEADAAN_DIAM}: lying at the house on two trestles (the whole bier rests lower);</li>
 * <li>{@link UsunganPetiEntity#KEADAAN_DIUSUNG}: carried at shoulder height by eight bearers on clip 400
 * ({@code KotaGerakId.PIKUL_USUNGAN}): sways +-1 degree and bobs once a step, in time with the clip (30 ticks a step
 * pair, phase from the synced start);</li>
 * <li>{@link UsunganPetiEntity#KEADAAN_TENGGELAM}: poles taken away, lowered 1.6 blocks into the grave over
 * {@link UsunganPetiEntity#TENGGELAM_TICK} ticks (progress {@code nilai()}).</li>
 * </ul>
 * Textures {@code usungan_hitam.png}, {@code usungan_merah.png}.
 */
public class UsunganPetiRenderer extends TemaBendaRenderer<UsunganPetiEntity, TemaBendaRenderer.State, KotaModel<TemaBendaRenderer.State>> {

	/** Ticks of one step pair of the bearers (clip 400's loop). */
	static final float LANGKAH = 30.0F;
	/** The DIAM pose rests this many model pixels lower (poles on the trestles). */
	static final float TURUN_DIAM = 14.0F;

	public UsunganPetiRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.USUNGAN), UsunganPetiRenderer::pose),
				new Identifier[] {KotaRenderClient.tekstur("usungan_hitam"), KotaRenderClient.tekstur("usungan_merah")}, null, 0.9F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(UsunganPetiEntity entity) {
		return 1.4D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == UsunganPetiEntity.VARIAN_MERAH ? 1 : 0;
	}

	static void pose(KotaModel<State> m, State s) {
		boolean merah = s.varian == UsunganPetiEntity.VARIAN_MERAH;
		m.p("bunga_merah").visible = merah;
		m.p("bunga_putih").visible = !merah;
		ModelPart root = m.root();
		boolean diam = s.keadaan == UsunganPetiEntity.KEADAAN_DIAM;
		boolean tenggelam = s.keadaan == UsunganPetiEntity.KEADAAN_TENGGELAM;
		m.p("kuda_kuda").visible = diam;
		m.p("galah_k").visible = !tenggelam;
		m.p("galah_kn").visible = !tenggelam;
		if (diam) {
			root.y += TURUN_DIAM;
		} else if (tenggelam) {
			float t = Mth.clamp(Math.max(s.nilai, s.umur / UsunganPetiEntity.TENGGELAM_TICK), 0.0F, 1.0F);
			// lowered on ropes: from the shoulder height down past the ground, slow at both ends
			root.y += TURUN_DIAM + sinus(t) * 25.6F;
		} else if (s.keadaan == UsunganPetiEntity.KEADAAN_DIUSUNG && !s.jauh) {
			float fase = s.umur / LANGKAH * Mth.TWO_PI;
			root.zRot = Mth.sin(fase) * Mth.DEG_TO_RAD;
			root.y += Math.abs(Mth.sin(fase)) * 0.6F;
			root.xRot = Mth.sin(fase * 0.5F + 0.6F) * 0.3F * Mth.DEG_TO_RAD;
		}
	}
}
