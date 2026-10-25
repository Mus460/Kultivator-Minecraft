package dev.kultivator.client.dunia.kota42.render.hewan;

import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.client.dunia.tempat.tema.model.TemaBendaRenderer;
import dev.kultivator.dunia.kota42.acara.BarongsaiEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * {@code kultivator:barongsai} (DESIGN 42.12.2, 42.12.4, 42.7.1, 42.7.6): the southern lion dance. The generated mesh is
 * the whole costume with its two dancers' legs: a lacquered head with gold cloud scrolls, forehead mirror, horn, fur brows
 * and trim, round eyes with two-frame eyelids (closed while {@link BarongsaiEntity#kedip()} is 1, while asleep and on a
 * client blink), a hinged jaw with teeth, tongue and beard; a five-segment scaled cloth body with fur fringes and spine
 * ridge rippling behind the head; a fur tail; four trousered legs with fur cuffs and clawed boots. Three looks
 * ({@link BarongsaiEntity#VARIAN_MERAH_EMAS}, {@link BarongsaiEntity#VARIAN_HIJAU}, {@link BarongsaiEntity#VARIAN_HITAM}),
 * textures {@code hewan/barongsai_<look>.png}.
 * <p>
 * The jaw opens by {@code max(nilai(), movement)} (the head snaps it on the drum beat). Movements of 42.12.4, each a pure
 * function of the ticks since it began:
 * <ul>
 * <li>{@link BarongsaiEntity#KEADAAN_JALAN}: the dance walk, one step every {@link BarongsaiEntity#LANGKAH_TICK} ticks:
 * the head bobs and looks left and right on each step, the jaw snaps on the beat, the back dancer follows a quarter step
 * later, the cloth ripples;</li>
 * <li>{@code MENYAPA}: three deep bows, mouth open at the bottom of each;</li>
 * <li>{@code MENGENDUS}: head low, sweeping side to side with quick sniffs, both dancers crouched;</li>
 * <li>{@code MENGGARUK}: the head tilted, a front leg raised scratching behind the ear, ears flapping;</li>
 * <li>{@code TIDUR}: the dancers kneel, the head rests on the ground, eyes shut, the cloth breathing;</li>
 * <li>{@code BANGUN}: getting up over 40 ticks, blinking, then the fur shake;</li>
 * <li>{@code MELOMPAT}: rearing: the front dancer lifted, front legs up, head high, mouth wide;</li>
 * <li>{@code MAKAN_SELADA}: cai qing: reaching up for the lettuce with its red packet, chewing it (the lettuce shrinks),
 * then spitting the leaves to the crowd;</li>
 * <li>{@code MENGGELENG}: shaking the head hard, ears and beard swinging.</li>
 * </ul>
 * Beyond 64 blocks the small motions (ripple, blink, ears) stop.
 */
public class BarongsaiRenderer extends TemaBendaRenderer<BarongsaiEntity, BarongsaiRenderer.State, KotaModel<BarongsaiRenderer.State>> {

	static final String[] WARNA = {"merah_emas", "hijau", "hitam"};
	static final float LANGKAH = BarongsaiEntity.LANGKAH_TICK;

	/** The lion state: the synced eyelid frame. */
	public static class State extends TemaBendaRenderer.State {
		public int kedip;
	}

	public BarongsaiRenderer(EntityRendererProvider.Context context) {
		super(context, new KotaModel<>(context.bakeLayer(HewanRenderClient.BARONGSAI), BarongsaiRenderer::pose),
				HewanRenderClient.tekstur("barongsai", WARNA), null, 0.9F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BarongsaiEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kedip = entity.kedip();
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, WARNA.length - 1);
	}

	@Override
	protected double reach(BarongsaiEntity entity) {
		return 1.4D;
	}

	static void pose(KotaModel<State> m, State s) {
		ModelPart root = m.root();
		ModelPart kepala = m.p("kepala");
		ModelPart rahang = m.p("rahang");
		ModelPart dk = m.p("kaki_dk");
		ModelPart dkn = m.p("kaki_dkn");
		ModelPart bk = m.p("kaki_bk");
		ModelPart bkn = m.p("kaki_bkn");
		ModelPart bdk = m.p("betis_dk");
		ModelPart bdkn = m.p("betis_dkn");
		ModelPart bbk = m.p("betis_bk");
		ModelPart bbkn = m.p("betis_bkn");
		float t = s.umur;
		boolean tidur = s.keadaan == BarongsaiEntity.KEADAAN_TIDUR;
		boolean kedipKlien = !s.jauh && ((int) (s.waktu + s.fase * 20.0F)) % 83 < 3;
		float rahangGerak = 0.0F;
		boolean tutup = s.kedip != 0 || tidur || kedipKlien;
		m.p("selada").visible = false;
		for (int i = 0; i < 4; i++) {
			m.p("daun_" + i).visible = false;
		}
		switch (s.keadaan) {
			case BarongsaiEntity.KEADAAN_MENYAPA -> {
				float b = t < 60.0F ? Math.abs(Mth.sin(t * Mth.PI / 20.0F)) : 0.0F;
				kepala.xRot = 0.55F * b;
				kepala.y += 2.5F * b;
				kepala.z -= 1.0F * b;
				jongkok(dk, bdk, 0.5F * b);
				jongkok(dkn, bdkn, 0.5F * b);
				m.p("kain_0").xRot = 0.2F * b;
				rahangGerak = b > 0.8F ? (b - 0.8F) * 4.0F : 0.0F;
			}
			case BarongsaiEntity.KEADAAN_MENGENDUS -> {
				kepala.xRot = 0.45F + Mth.sin(t * 1.7F) * 0.04F * (1.0F + Mth.sin(t * 0.3F));
				kepala.y += 3.0F;
				kepala.yRot = Mth.sin(t * 0.08F) * 0.6F;
				jongkok(dk, bdk, 0.7F);
				jongkok(dkn, bdkn, 0.7F);
				jongkok(bk, bbk, 0.4F);
				jongkok(bkn, bbkn, 0.4F);
				m.p("kain_0").xRot = 0.25F;
				rahangGerak = 0.1F + 0.08F * Mth.sin(t * 0.9F);
			}
			case BarongsaiEntity.KEADAAN_MENGGARUK -> {
				kepala.zRot = 0.35F;
				kepala.yRot = -0.3F;
				kepala.y += 1.0F;
				dk.xRot = -1.1F + Mth.sin(t * 1.2F) * 0.25F;
				bdk.xRot = 0.9F + Mth.sin(t * 1.2F + 0.5F) * 0.3F;
				dk.zRot = -0.25F;
				jongkok(dkn, bdkn, 0.25F);
				m.p("telinga_k").zRot += Mth.sin(t * 1.4F) * 0.4F;
				rahangGerak = 0.15F;
			}
			case BarongsaiEntity.KEADAAN_TIDUR -> tidur(m, 1.0F, s);
			case BarongsaiEntity.KEADAAN_BANGUN -> {
				float p = 1.0F - sinus(maju(t, 0.0F, 30.0F));
				tidur(m, p, s);
				float goyang = redam(t - 24.0F, 20.0F, 1.7F);
				kepala.zRot += goyang * 0.45F;
				kepala.yRot += goyang * 0.25F;
				m.p("kain_1").zRot = -goyang * 0.12F;
				m.p("kain_3").zRot = goyang * 0.12F;
				tutup = p > 0.6F || (t > 32.0F && t < 35.0F) || (t > 38.0F && t < 41.0F);
				rahangGerak = goyang > 0.0F ? Math.abs(goyang) * 0.4F : 0.0F;
			}
			case BarongsaiEntity.KEADAAN_MELOMPAT -> {
				float r = sinus(maju(t, 0.0F, 12.0F)) * (1.0F - sinus(maju(t, 28.0F, 12.0F)));
				kepala.y -= 9.0F * r;
				kepala.xRot = -0.5F * r;
				dk.y -= 6.0F * r;
				dkn.y -= 6.0F * r;
				dk.xRot = -1.0F * r;
				dkn.xRot = -0.8F * r;
				bdk.xRot = 1.2F * r;
				bdkn.xRot = 1.3F * r;
				jongkok(bk, bbk, 0.45F * r);
				jongkok(bkn, bbkn, 0.45F * r);
				m.p("kain_0").xRot = -0.55F * r;
				m.p("kain_1").xRot = 0.15F * r;
				m.p("kain_2").xRot = 0.15F * r;
				rahangGerak = 0.9F * r;
			}
			case BarongsaiEntity.KEADAAN_MAKAN_SELADA -> {
				float naik = sinus(maju(t, 0.0F, 20.0F)) * (1.0F - sinus(maju(t, 50.0F, 10.0F)));
				kepala.y -= 5.0F * naik;
				kepala.xRot = -0.4F * naik;
				jongkok(bk, bbk, 0.3F * naik);
				jongkok(bkn, bbkn, 0.3F * naik);
				ModelPart sel = m.p("selada");
				sel.visible = t < 52.0F;
				float habis = 1.0F - maju(t, 30.0F, 22.0F);
				sel.xScale = habis;
				sel.yScale = habis;
				sel.zScale = habis;
				if (t >= 20.0F && t < 52.0F) {
					rahangGerak = Math.abs(Mth.sin(t * 0.6F)) * 0.8F;
					kepala.zRot = Mth.sin(t * 0.3F) * 0.12F;
				}
				if (t >= 60.0F && t < 92.0F) {
					// spit the leaves to the crowd
					float u = t - 60.0F;
					kepala.z -= 2.0F * redam(u, 12.0F, 0.3F);
					rahangGerak = 0.7F * (1.0F - maju(u, 4.0F, 8.0F));
					for (int i = 0; i < 4; i++) {
						ModelPart d = m.p("daun_" + i);
						d.visible = true;
						float a = (i - 1.5F) * 0.5F;
						d.x += Mth.sin(a) * u * 0.35F;
						d.z -= Mth.cos(a) * u * 0.5F;
						d.y += -u * 0.35F + u * u * 0.025F;
						d.xRot = u * (0.3F + i * 0.07F);
						d.zRot = u * 0.2F * (i % 2 == 0 ? 1.0F : -1.0F);
					}
				}
			}
			case BarongsaiEntity.KEADAAN_MENGGELENG -> {
				float env = maju(t, 0.0F, 5.0F) * (1.0F - maju(t, 35.0F, 10.0F));
				kepala.yRot = Mth.sin(t * 0.9F) * 0.6F * env;
				kepala.zRot = Mth.sin(t * 0.9F + 0.6F) * 0.12F * env;
				m.p("janggut").zRot = -Mth.sin(t * 0.9F - 0.5F) * 0.3F * env;
				m.p("telinga_k").zRot += Mth.sin(t * 0.9F) * 0.5F * env;
				m.p("telinga_kn").zRot += Mth.sin(t * 0.9F) * 0.5F * env;
				rahangGerak = 0.2F * env;
			}
			default -> {
				// the dance walk: a step every LANGKAH ticks, the back dancer a quarter step behind
				float ph = (t % LANGKAH) / LANGKAH * Mth.TWO_PI;
				float ph2 = ph - Mth.HALF_PI;
				kepala.y -= Math.abs(Mth.sin(ph)) * 1.6F;
				kepala.yRot = Mth.sin(ph) * 0.35F;
				kepala.xRot = -0.08F + Mth.cos(ph * 2.0F) * 0.06F;
				dk.xRot = Mth.sin(ph) * 0.5F;
				dkn.xRot = -Mth.sin(ph) * 0.5F;
				bdk.xRot = Math.max(0.0F, Mth.cos(ph)) * 0.7F;
				bdkn.xRot = Math.max(0.0F, -Mth.cos(ph)) * 0.7F;
				bk.xRot = Mth.sin(ph2) * 0.45F;
				bkn.xRot = -Mth.sin(ph2) * 0.45F;
				bbk.xRot = Math.max(0.0F, Mth.cos(ph2)) * 0.6F;
				bbkn.xRot = Math.max(0.0F, -Mth.cos(ph2)) * 0.6F;
				root.y -= Math.abs(Mth.sin(ph)) * 0.4F;
				float pukul = Mth.sin(ph * 2.0F);
				rahangGerak = pukul > 0.0F ? pukul * pukul * pukul * pukul * 0.6F : 0.0F;
			}
		}
		// jaw: the synced drum snap or the movement's, whichever is wider
		float buka = Math.max(Mth.clamp(s.nilai, 0.0F, 1.0F), Mth.clamp(rahangGerak, 0.0F, 1.0F));
		rahang.xRot += buka * 0.62F;
		m.p("janggut").xRot -= buka * 0.3F;
		m.p("kelopak_k").visible = tutup;
		m.p("kelopak_kn").visible = tutup;
		if (s.jauh) {
			return;
		}
		// ripple of the cloth body behind the head, ears and brows alive
		for (int i = 0; i < 5; i++) {
			ModelPart k = m.p("kain_" + i);
			k.yRot += Mth.sin(s.waktu * 0.12F - i * 0.6F + s.fase) * 0.06F;
			k.xRot += Mth.sin(s.waktu * 0.15F - i * 0.7F + s.fase) * 0.03F;
		}
		m.p("ekor").yRot += Mth.sin(s.waktu * 0.2F + s.fase) * 0.3F;
		m.p("telinga_k").zRot += Mth.sin(s.waktu * 0.3F + s.fase) * 0.12F;
		m.p("telinga_kn").zRot -= Mth.sin(s.waktu * 0.3F + s.fase + 1.0F) * 0.12F;
		m.p("alis_k").xRot += Mth.sin(s.waktu * 0.25F) * 0.05F;
		m.p("alis_kn").xRot += Mth.sin(s.waktu * 0.25F + 0.4F) * 0.05F;
	}

	/** A dancer's knee bend: thigh forward, shin back by {@code f} (0 straight, 1 deep crouch); the hip sinks. */
	static void jongkok(ModelPart paha, ModelPart betis, float f) {
		paha.xRot -= 0.75F * f;
		betis.xRot += 1.5F * f;
		paha.y += 2.2F * f;
	}

	/** Asleep by {@code p} (0 standing .. 1 lying): dancers kneel, the head on the ground, cloth breathing. */
	static void tidur(KotaModel<State> m, float p, State s) {
		ModelPart root = m.root();
		root.y += 6.5F * p;
		for (String sisi : new String[] {"_dk", "_dkn", "_bk", "_bkn"}) {
			m.p("kaki" + sisi).xRot -= 1.25F * p;
			m.p("betis" + sisi).xRot += 2.3F * p;
		}
		ModelPart kepala = m.p("kepala");
		kepala.xRot += 0.35F * p;
		kepala.y += 3.0F * p;
		m.p("kain_0").xRot += 0.2F * p;
		if (!s.jauh) {
			float napas = 1.0F + 0.04F * p * Mth.sin(s.waktu * 0.08F + s.fase);
			for (int i = 0; i < 5; i++) {
				m.p("kain_" + i).yScale = napas;
			}
		}
	}
}
