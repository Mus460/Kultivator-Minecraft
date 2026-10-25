package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.TendaPasarEntity;
import dev.kultivator.dunia.kota42.ekonomi.TendaLapakEntity;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The two market tents of DESIGN 42.12.4 on one mesh ({@code KotaMesh.tenda} + the dyed strips {@code tendaWarna}):
 * <ul>
 * <li>{@link Pasar} {@code kultivator:tenda_pasar} (42.7.6, 42.9.6): a counter under the canopy with the festival goods of
 * its variant: hanging red lanterns that glow at night, bamboo steamers and candied haws, pinwheels that spin in the wind,
 * red paper-cuts on a line, a ring-toss game;</li>
 * <li>{@link Lapak} {@code kultivator:tenda_lapak} (42.6.2): no counter, the straw mat and a low bench with the market
 * goods of its variant: vegetables, fruit, pottery, medicine (herb bundles swing from the eave), cloth.</li>
 * </ul>
 * The canopy is a gable of eight strips a side; every other strip and both scalloped valances are drawn a second time from
 * {@code tendaWarna} tinted with the synced stripe colour ({@code warna()}), the rest stays undyed cloth. Assembly
 * ({@code KEADAAN_DIPASANG} / {@code KEADAAN_DIBONGKAR}, progress {@code nilai()}): the poles grow from the ground, the
 * canopy unrolls from the ridge, then the counter and goods appear (reversed when struck). Within 64 blocks the cloth
 * ripples and the valances, lanterns and herbs sway. Textures {@code tenda.png} (+ {@code _pancar}),
 * {@code tenda_warna.png}.
 */
public abstract class TendaRenderer<T extends TemaMekanismeEntity> extends TemaBendaRenderer<T, TendaRenderer.State, KotaModel<TendaRenderer.State>> {

	private static final Identifier TEX = KotaRenderClient.tekstur("tenda");
	private static final Identifier GLOW = KotaRenderClient.tekstur("tenda_pancar");
	private static final Identifier TEX_WARNA = KotaRenderClient.tekstur("tenda_warna");
	private static final RenderType TYPE_WARNA = RenderTypes.entityCutout(TEX_WARNA);

	public static class State extends TemaBendaRenderer.State {
		/** Assembly 0 (struck) .. 1 (standing). */
		public float rakit = 1.0F;
		public int warna = 0xC0262D;
		public boolean lapak;
	}

	private final KotaModel<State> warnaModel;

	protected TendaRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.TENDA), TendaRenderer::pose), TEX, GLOW, 1.0F);
		this.warnaModel = new KotaModel<>(context.bakeLayer(KotaRenderClient.TENDA_WARNA), TendaRenderer::poseAtap);
	}

	protected abstract int warna(T entity);

	protected abstract boolean lapak();

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(T entity) {
		return 1.0D;
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.lapak = lapak();
		state.warna = warna(entity) & 0xFFFFFF;
		// KEADAAN_BERDIRI = 0 in both tent classes; 1 / 2 = going up / coming down with nilai the progress
		state.rakit = state.keadaan == 0 ? 1.0F : Mth.clamp(state.nilai, 0.0F, 1.0F);
		boolean lentera = !state.lapak && state.varian == TendaPasarEntity.VARIAN_LENTERA;
		state.pancar = lentera && state.rakit > 0.8F
				? KotaRenderClient.malam(entity.level()) * KotaRenderClient.kedip(state.waktu, state.fase) : 0.0F;
	}

	@Override
	protected void submitExtra(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.rakit > 0.45F) {
			collector.submitModel(warnaModel, state, poseStack, TYPE_WARNA, state.lightCoords, OverlayTexture.NO_OVERLAY,
					ARGB.opaque(state.warna), null, state.outlineColor);
		}
	}

	static void pose(KotaModel<State> m, State s) {
		float p = s.rakit;
		ModelPart tiang = m.p("tiang");
		tiang.yScale = Math.max(0.02F, KotaModel.maju(p, 0.0F, 0.45F));
		poseAtap(m, s);
		boolean barang = p > 0.8F;
		float naik = KotaModel.maju(p, 0.8F, 0.2F);
		m.p("meja").visible = barang && !s.lapak;
		m.p("tikar").visible = p > 0.45F && s.lapak;
		m.p("bangku_lapak").visible = barang && s.lapak;
		m.p("peti").visible = barang;
		m.p("keranjang").visible = barang;
		int v = Mth.clamp(s.varian, 0, 4);
		for (int i = 0; i < 5; i++) {
			ModelPart pasar = m.p("pasar_" + i);
			ModelPart lapak = m.p("lapak_" + i);
			pasar.visible = barang && !s.lapak && i == v;
			lapak.visible = barang && s.lapak && i == v;
			pasar.yScale = lapak.yScale = Math.max(0.05F, naik);
		}
		if (s.jauh) {
			return;
		}
		for (int i = 0; i < 4; i++) {
			ModelPart l = m.p("lentera_" + i);
			l.zRot = KotaModel.ayun(s.waktu, s.fase + i * 1.3F, 0.07F, 0.08F);
			l.xRot = KotaModel.ayun(s.waktu, s.fase + i * 0.7F + 2.0F, 0.04F, 0.06F);
			ModelPart h = m.p("herba_" + i);
			h.zRot = KotaModel.ayun(s.waktu, s.fase + i * 1.1F, 0.05F, 0.07F);
		}
		for (int i = 0; i < 5; i++) {
			// pinwheels on their sticks (local x): spin with a gusty wind
			m.p("kincir_" + i).xRot = s.waktu * (0.25F + 0.1F * Mth.sin(s.waktu * 0.02F + i)) + i;
		}
	}

	/** The canopy pose shared by both meshes: unroll from the ridge, ripple, valance swing. */
	static void poseAtap(KotaModel<State> m, State s) {
		ModelPart atap = m.p("atap");
		float buka = KotaModel.maju(s.rakit, 0.45F, 0.35F);
		atap.visible = s.rakit > 0.45F;
		atap.zScale = Math.max(0.02F, buka);
		if (s.jauh || buka < 1.0F) {
			return;
		}
		float r = Mth.sin(s.waktu * 0.13F + s.fase) * 0.025F + Mth.sin(s.waktu * 0.31F + s.fase * 2.0F) * 0.01F;
		m.p("atap_d").xRot += r;
		m.p("atap_b").xRot -= r * 0.8F;
		if (m.ada("rumbai_d")) {
			m.p("rumbai_d").xRot += Mth.sin(s.waktu * 0.17F + s.fase) * 0.12F;
			m.p("rumbai_b").xRot -= Mth.sin(s.waktu * 0.15F + s.fase + 1.0F) * 0.12F;
		}
	}

	/** {@code kultivator:tenda_pasar}. */
	public static class Pasar extends TendaRenderer<TendaPasarEntity> {

		public Pasar(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected int warna(TendaPasarEntity entity) {
			return entity.warna();
		}

		@Override
		protected boolean lapak() {
			return false;
		}
	}

	/** {@code kultivator:tenda_lapak}. */
	public static class Lapak extends TendaRenderer<TendaLapakEntity> {

		public Lapak(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		protected int warna(TendaLapakEntity entity) {
			return entity.warna();
		}

		@Override
		protected boolean lapak() {
			return true;
		}
	}
}
