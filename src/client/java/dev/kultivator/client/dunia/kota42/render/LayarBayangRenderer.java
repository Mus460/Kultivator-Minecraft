package dev.kultivator.client.dunia.kota42.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.screen.SettingsRows;
import dev.kultivator.dunia.kota42.acara.LayarBayangEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:layar_bayang} (DESIGN 42.12.4, 42.9.6 "Teater Bayang"): the shadow-puppet screen: a red lacquered
 * frame 32 x 24 x 2 on feet with a carved gold crest and two small corner lanterns, the 30 x 22 paper cloth, and behind
 * it the puppeteer's table with an oil lamp (the puppeteer himself is an NPC on clip 413 {@code DALANG}).
 * <p>
 * While {@link LayarBayangEntity#KEADAAN_PENTAS} the cloth glows warm with the synced lamp brightness {@code nilai()}
 * and a candle flicker, and the puppets of the script ({@link LayarBayangEntity#naskah()}, atlas
 * {@code wayang_<naskah>.png}: six coloured leather figures with a jointed arm each) play on it: a 1200-tick show of four
 * acts (enter from the wings, meet, bow / duel / talk / travel together / chase, leave), derived from the synced start
 * so every viewer sees the same frame, with walking bob, arm swings, bows and leaps; figures fade in and out at the
 * wings. {@link LayarBayangEntity#KEADAAN_JEDA} freezes the figures mid-act ("Malam Wayang": a stolen puppet);
 * {@link LayarBayangEntity#KEADAAN_GELAP} is an unlit screen with no puppets. The figures are drawn on both sides of
 * the cloth (the audience sees the shadows, the puppeteer the puppets), not in a shadow pass. Textures
 * {@code layar.png} (+ {@code _pancar}), {@code wayang_kera_sakti / ular_putih / hua_mulan / hakim_bao.png}.
 */
public class LayarBayangRenderer extends TemaBendaRenderer<LayarBayangEntity, LayarBayangRenderer.State, KotaModel<LayarBayangRenderer.State>> {

	/** One show (four acts of {@link #BABAK} ticks), looped. */
	static final int PENTAS = 1200;
	static final int BABAK = 300;
	private static final RenderType[] WAYANG = new RenderType[LayarBayangEntity.NASKAH_JUMLAH];

	static {
		for (int i = 0; i < WAYANG.length; i++) {
			WAYANG[i] = RenderTypes.entityTranslucent(KotaRenderClient.tekstur("wayang_" + LayarBayangEntity.NASKAH_ID[i]));
		}
	}

	/**
	 * The acts per script: {figure a, figure b, mode} with the atlas cells 0..5 and modes {@link #SAPA} (meet and bow),
	 * {@link #TARUNG} (duel), {@link #CAKAP} (talk), {@link #JALAN} (travel together), {@link #KEJAR} (chase).
	 */
	static final int SAPA = 0;
	static final int TARUNG = 1;
	static final int CAKAP = 2;
	static final int JALAN = 3;
	static final int KEJAR = 4;
	static final int[][][] BABAK_NASKAH = {
			// Kera Sakti: the monk leads the white horse west; Wukong against the demon; Pigsy and Sandy bicker; master and disciple bow
			{{1, 5, JALAN}, {0, 4, TARUNG}, {2, 3, CAKAP}, {0, 1, SAPA}},
			// Ular Putih: Bai Suzhen meets Xu Xian under the umbrella; the sisters travel; Fahai fights Xiaoqing; Fahai chases Bai Suzhen
			{{0, 2, SAPA}, {0, 1, JALAN}, {3, 1, TARUNG}, {3, 0, KEJAR}},
			// Hua Mulan: at the loom with her father; she rides out; duel with the enemy chief; she bows to the general
			{{5, 1, CAKAP}, {0, 2, JALAN}, {0, 4, TARUNG}, {0, 3, SAPA}},
			// Hakim Bao: Qin Xianglian petitions the judge; Zhan Zhao chases Chen Shimei; the judge confronts him; two officials bow
			{{3, 0, CAKAP}, {1, 2, KEJAR}, {0, 2, CAKAP}, {0, 4, SAPA}},
	};

	public static class State extends TemaBendaRenderer.State {
		public int naskah;
		public float waktuPentas;
		public float terang;
	}

	public LayarBayangRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(KotaRenderClient.LAYAR), (m, s) -> {
		}), KotaRenderClient.tekstur("layar"), KotaRenderClient.tekstur("layar_pancar"), 0.9F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(LayarBayangEntity entity) {
		return 0.8D;
	}

	@Override
	public void extractRenderState(LayarBayangEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.naskah = Math.floorMod(entity.naskah(), LayarBayangEntity.NASKAH_JUMLAH);
		boolean lit = state.keadaan != LayarBayangEntity.KEADAAN_GELAP;
		state.terang = lit ? Mth.clamp(state.nilai <= 0.0F ? 1.0F : state.nilai, 0.0F, 1.0F) : 0.0F;
		state.pancar = state.terang * KotaRenderClient.kedip(state.waktu, state.fase);
		if (state.keadaan == LayarBayangEntity.KEADAAN_PENTAS) {
			state.waktuPentas = state.umur % PENTAS;
		} else if (state.keadaan == LayarBayangEntity.KEADAAN_JEDA) {
			// frozen: mid-act of the act the stop fell in (the synced start of JEDA has no script time, so the duel act)
			state.waktuPentas = BABAK + 150.0F;
		}
	}

	@Override
	protected void submitExtra(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (state.terang <= 0.01F || state.jauh || ShaderCompat.shadowPass() || !SettingsRows.kota42("kota42.wayang")) {
			return;
		}
		float[] q = susun(state);
		int color = ARGB.color(Math.round(235 * Math.min(1.0F, 0.5F + state.terang * 0.5F)), 0xFFE6CC);
		collector.order(2).submitCustomGeometry(poseStack, WAYANG[state.naskah], (pose, buffer) -> tulis(pose, buffer, q, color));
	}

	// ------------------------------------------------------------------ choreography

	/** Floats per quad: 4 corners x (x, y) in model px, u0, v0, u1, v1, z (model px), alpha 0..1. */
	private static final int STRIDE = 14;

	/** All quads of the current frame: body and arm per figure, each on the front (z -) and back (z +) of the cloth. */
	static float[] susun(State s) {
		int act = Mth.clamp((int) (s.waktuPentas / BABAK), 0, 3);
		float t = s.waktuPentas - act * BABAK;
		int[] b = BABAK_NASKAH[s.naskah][act];
		float[] out = new float[2 * 2 * 2 * STRIDE];
		Figur fa = new Figur();
		Figur fb = new Figur();
		gerak(b[2], t, fa, fb);
		int n = 0;
		for (int side = 0; side < 2; side++) {
			float z = side == 0 ? -0.09F : 0.09F;
			n = figur(out, n, b[0], fa, z);
			n = figur(out, n, b[1], fb, z);
		}
		return out;
	}

	/** One figure's frame pose: centre x (cloth px, -15..15), lift (px up), facing, arm angle and bow tilt (radians), alpha. */
	static final class Figur {
		float x;
		float naik;
		boolean kanan = true;
		float lengan;
		float tunduk;
		float alpha = 1.0F;
	}

	static void gerak(int mode, float t, Figur a, Figur b) {
		float masuk = KotaModel.maju(t, 0.0F, 90.0F);
		float keluar = KotaModel.maju(t, 220.0F, 80.0F);
		float jalanBob = Math.abs(Mth.sin(t * 0.45F)) * 0.7F;
		switch (mode) {
			case JALAN, KEJAR -> {
				float span = mode == KEJAR ? 26.0F : 24.0F;
				float lead = -12.0F + span * (t / BABAK);
				a.x = mode == KEJAR ? lead - 5.0F - 1.5F * Mth.sin(t * 0.07F) : lead;
				b.x = mode == KEJAR ? lead + 1.5F : lead - 5.5F;
				a.kanan = b.kanan = true;
				float bob = mode == KEJAR ? Math.abs(Mth.sin(t * 0.8F)) * 1.2F : jalanBob;
				a.naik = bob;
				b.naik = mode == KEJAR ? Math.abs(Mth.sin(t * 0.8F + 1.2F)) * 1.2F : jalanBob;
				a.lengan = mode == KEJAR ? 0.6F + Mth.sin(t * 0.5F) * 0.5F : Mth.sin(t * 0.45F) * 0.3F;
				b.lengan = Mth.sin(t * 0.45F + 1.5F) * 0.3F;
			}
			default -> {
				float e = masuk - keluar;
				float ease = e * e * (3.0F - 2.0F * e);
				a.x = Mth.lerp(ease, -12.0F, -3.2F);
				b.x = Mth.lerp(ease, 12.0F, 3.2F);
				boolean pergi = t > 220.0F;
				a.kanan = !pergi;
				b.kanan = pergi;
				boolean berjalan = t < 90.0F || pergi;
				a.naik = berjalan ? jalanBob : 0.0F;
				b.naik = berjalan ? Math.abs(Mth.sin(t * 0.45F + 1.0F)) * 0.7F : 0.0F;
				a.lengan = berjalan ? Mth.sin(t * 0.45F) * 0.25F : 0.0F;
				b.lengan = berjalan ? Mth.sin(t * 0.45F + 1.6F) * 0.25F : 0.0F;
				if (!berjalan) {
					float u = t - 90.0F;
					switch (mode) {
						case SAPA -> {
							float bow = Math.max(0.0F, Mth.sin(u / 130.0F * Mth.TWO_PI));
							a.tunduk = bow * 0.35F;
							b.tunduk = Math.max(0.0F, Mth.sin(u / 130.0F * Mth.TWO_PI - 0.6F)) * 0.35F;
							a.lengan = b.lengan = 0.5F * bow;
						}
						case TARUNG -> {
							a.x += Mth.sin(u * 0.15F) * 1.6F + 1.0F;
							b.x -= Mth.sin(u * 0.15F + 0.4F) * 1.6F + 1.0F;
							a.lengan = Mth.sin(u * 0.35F) * 1.2F;
							b.lengan = Mth.sin(u * 0.35F + 2.0F) * 1.2F;
							a.naik = Math.max(0.0F, Mth.sin(u * 0.12F)) * 3.0F;
							b.naik = Math.max(0.0F, Mth.sin(u * 0.12F + 2.4F)) * 3.0F;
						}
						default -> {
							boolean giliran = ((int) (u / 25.0F)) % 2 == 0;
							a.lengan = giliran ? 0.4F + Mth.sin(u * 0.3F) * 0.3F : 0.05F;
							b.lengan = giliran ? 0.05F : 0.4F + Mth.sin(u * 0.3F) * 0.3F;
							a.tunduk = giliran ? 0.05F : 0.0F;
							b.tunduk = giliran ? 0.0F : 0.05F;
						}
					}
				}
			}
		}
		a.alpha = sayap(a.x);
		b.alpha = sayap(b.x);
	}

	/** Fade near the wings: figures enter and leave through the screen's edges. */
	static float sayap(float x) {
		return Mth.clamp((10.5F - Math.abs(x)) / 2.5F, 0.0F, 1.0F);
	}

	/**
	 * Writes a figure's body (10.5 x 12 px, bowing about the feet) and arm (10.5 x 3 px from the shoulder, body texel 9, 9)
	 * as two quads; a figure facing left is mirrored by its corner order, so the atlas stays one-sided.
	 */
	static int figur(float[] out, int n, int cell, Figur f, float z) {
		float cu = (cell % 3) * 21.0F / 64.0F;
		float cv = (cell / 3) * 32.0F / 64.0F;
		float x = Mth.clamp(f.x, -10.5F, 10.5F);
		float feet = 15.0F - f.naik;
		float dir = f.kanan ? 1.0F : -1.0F;
		float tilt = f.tunduk * dir;
		float c = Mth.cos(tilt);
		float sn = Mth.sin(tilt);
		float[] bx = {-5.25F, 5.25F, 5.25F, -5.25F};
		float[] by = {-12.0F, -12.0F, 0.0F, 0.0F};
		int o = n++ * STRIDE;
		for (int i = 0; i < 4; i++) {
			float px = bx[i] * dir;
			out[o + i * 2] = x + px * c - by[i] * sn;
			out[o + i * 2 + 1] = feet + px * sn + by[i] * c;
		}
		uv(out, o, cu, cv, cu + 21.0F / 64.0F, cv + 24.0F / 64.0F, z, f.alpha);
		float sx = -0.75F * dir;
		float sy = -7.5F;
		float shX = x + sx * c - sy * sn;
		float shY = feet + sx * sn + sy * c;
		float phi = tilt - f.lengan * dir;
		float ca = Mth.cos(phi);
		float sa = Mth.sin(phi);
		float[] ax = {0.0F, 10.5F, 10.5F, 0.0F};
		float[] ay = {-1.25F, -1.25F, 1.75F, 1.75F};
		o = n++ * STRIDE;
		for (int i = 0; i < 4; i++) {
			float lx = ax[i] * dir;
			out[o + i * 2] = shX + lx * ca - ay[i] * sa;
			out[o + i * 2 + 1] = shY + lx * sa + ay[i] * ca;
		}
		uv(out, o, cu, cv + 25.0F / 64.0F, cu + 21.0F / 64.0F, cv + 31.0F / 64.0F, z * 1.1F, f.alpha);
		return n;
	}

	private static void uv(float[] out, int o, float u0, float v0, float u1, float v1, float z, float alpha) {
		out[o + 8] = u0;
		out[o + 9] = v0;
		out[o + 10] = u1;
		out[o + 11] = v1;
		out[o + 12] = z;
		out[o + 13] = alpha;
	}

	static void tulis(PoseStack.Pose pose, VertexConsumer buffer, float[] q, int color) {
		for (int k = 0; k + STRIDE <= q.length; k += STRIDE) {
			float alpha = q[k + 13];
			if (alpha <= 0.01F) {
				continue;
			}
			int col = ARGB.color(Math.round(ARGB.alpha(color) * alpha), color & 0xFFFFFF);
			float u0 = q[k + 8];
			float v0 = q[k + 9];
			float u1 = q[k + 10];
			float v1 = q[k + 11];
			float z = q[k + 12] / 16.0F;
			// corners: top-left, top-right, bottom-right, bottom-left of the unmirrored figure
			float[] us = {u0, u1, u1, u0};
			float[] vs = {v0, v0, v1, v1};
			for (int i = 0; i < 4; i++) {
				buffer.addVertex(pose, q[k + i * 2] / 16.0F, q[k + i * 2 + 1] / 16.0F, z)
						.setColor(col)
						.setUv(us[i], vs[i])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT)
						.setNormal(pose, 0.0F, 0.0F, -1.0F);
			}
		}
	}
}
