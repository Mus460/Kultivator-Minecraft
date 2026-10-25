package dev.kultivator.client.dunia.kota42.render;

import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.tumbuh.TendaPengungsiEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:tenda_pengungsi} (DESIGN 42.12.4, 42.9.5): the refugee tent of a household whose house was really
 * destroyed: an A-frame of patched canvas (grey, oiled brown or indigo by variant) on crossed poles with a ridge pole,
 * a triangular back wall, guy ropes to pegs, a bedroll with a quilt, a cloth bundle, a pot on three stones, and a small
 * paper lantern hanging at the entrance. By day ({@link TendaPengungsiEntity#KEADAAN_SIANG}) both front flaps are folded
 * back and tied over the sides; at night ({@link TendaPengungsiEntity#KEADAAN_MALAM}) they swing shut over 1 s (they hinge
 * on the slanted front edges) and the lantern glows with the synced brightness {@code nilai()}, flickering, and sways a
 * little. Textures {@code pengungsi_abu.png}, {@code pengungsi_coklat.png}, {@code pengungsi_biru.png} (+ {@code _pancar}).
 */
public class TendaPengungsiRenderer extends TemaBendaRenderer<TendaPengungsiEntity, TemaBendaRenderer.State, KotaModel<TemaBendaRenderer.State>> {

	/** Flap swing when tied open (radians, about the slanted edge). */
	static final float TERBUKA = 150.0F * Mth.DEG_TO_RAD;
	/** Ticks the flaps take to open or close. */
	static final float AYUN = 20.0F;

	public TendaPengungsiRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.PENGUNGSI), TendaPengungsiRenderer::pose),
				new Identifier[] {KotaRenderClient.tekstur("pengungsi_abu"), KotaRenderClient.tekstur("pengungsi_coklat"),
						KotaRenderClient.tekstur("pengungsi_biru")},
				new Identifier[] {KotaRenderClient.tekstur("pengungsi_abu_pancar"), KotaRenderClient.tekstur("pengungsi_coklat_pancar"),
						KotaRenderClient.tekstur("pengungsi_biru_pancar")}, 0.9F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(TendaPengungsiEntity entity) {
		return 0.5D;
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, TendaPengungsiEntity.VARIAN_JUMLAH - 1);
	}

	@Override
	public void extractRenderState(TendaPengungsiEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		boolean malam = state.keadaan == TendaPengungsiEntity.KEADAAN_MALAM;
		float kuat = state.nilai > 0.0F ? Mth.clamp(state.nilai, 0.0F, 1.0F) : 0.8F;
		state.pancar = malam ? kuat * KotaModel.maju(state.umur, 0.0F, AYUN) * KotaRenderClient.kedip(state.waktu, state.fase) : 0.0F;
	}

	static void pose(KotaModel<State> m, State s) {
		boolean malam = s.keadaan == TendaPengungsiEntity.KEADAAN_MALAM;
		float t = s.jauh ? 1.0F : KotaModel.maju(s.umur, 0.0F, AYUN);
		float e = t * t * (3.0F - 2.0F * t);
		float buka = malam ? 1.0F - e : e;
		m.p("engsel_k").yRot = -TERBUKA * buka;
		m.p("engsel_kn").yRot = TERBUKA * buka;
		if (!s.jauh) {
			ModelPart l = m.p("lentera");
			l.zRot = KotaModel.ayun(s.waktu, s.fase, 0.05F, 0.05F);
			l.xRot = KotaModel.ayun(s.waktu, s.fase + 1.3F, 0.03F, 0.04F);
		}
	}
}
