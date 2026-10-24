package dev.kultivator.client.sosok.busana;

import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.client.sosok.gerak.GerakData;
import dev.kultivator.client.sosok.gerak.Pegas;
import dev.kultivator.client.sosok.kulit.RupaResolver;
import dev.kultivator.client.sosok.kulit.SosokRupa;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * The hair and garment cubes of a Sosok body (DESIGN 40.3.6-40.3.7, 40.5.8; module 40K): one {@code PlayerModel} on
 * the 128 x 128 atlas whose skeleton carries every cube of {@link BusanaKatalog}, so cast, duel, meditation, duty and
 * Gerak poses move them with the body through {@code PlayerModelMixin}. Per frame ({@link #setupAnim}, read from the
 * render state at draw time) it
 * <ol>
 *   <li>shows exactly the cubes of the body's look ({@code SosokRupa.kubusRambut/kubusBusana}, variants) - everything
 *   else is {@code visible = false} and costs nothing; at LOD KURANG only the big cubes, in the Iris shadow pass only
 *   skirts, long hair and hats (Tinggi) or skirts (Sedang);</li>
 *   <li>opens the skirt panels with the legs (40G's smoothed values from {@code GerakData}, or the kinematic rule of
 *   40.5.8 when Gerak does not drive the body), so no leg goes through a long robe; seated cross-legged the skirt
 *   spreads on the floor;</li>
 *   <li>adds 40G's spring angles to hair, ponytail, braids, ribbons, buyao beads, beards, sleeves' pockets, the outer
 *   robe, the floating sashes and the hanging jade (chains of {@code Pegas.Rantai}).</li>
 * </ol>
 * Only {@code visible}, rotations and scales of parts change (Sodium's fast cube path supports them).
 */
public class SosokBusanaModel extends PlayerModel {

	private static final int NR = BusanaKatalog.RAMBUT.size();
	private static final int NB = BusanaKatalog.BUSANA.size();

	private final ModelPart[] rambut = new ModelPart[NR];
	private final ModelPart[] rambutKiri = new ModelPart[NR];
	private final ModelPart[] busana = new ModelPart[NB];
	private final ModelPart[] busanaKiri = new ModelPart[NB];
	private final boolean[] besarR = new boolean[NR];
	private final boolean[] besarB = new boolean[NB];
	private final int[] bayanganR = new int[NR];
	private final int[] bayanganB = new int[NB];
	private final ModelPart[] manik = new ModelPart[3];
	private final ModelPart[] rumbai = new ModelPart[3];
	private final @Nullable ModelPart pitaEkor;
	private final @Nullable ModelPart pitaCepol;
	private final @Nullable ModelPart pitaCepolKiri;
	private final @Nullable ModelPart tusukRendah;
	private final @Nullable ModelPart kepang1Depan;
	private final @Nullable ModelPart kepang1DepanKiri;
	private final @Nullable ModelPart kepang2Depan;
	private final @Nullable ModelPart kepang2DepanKiri;
	private final @Nullable ModelPart jambulSatu;
	private final @Nullable ModelPart rokZirahB;
	private final @Nullable ModelPart pitaDada;
	private final @Nullable ModelPart pitaDadaKiri;

	private final int iSanggul = BusanaKatalog.rambut("sanggul");
	private final int iSanggulRendah = BusanaKatalog.rambut("sanggul_rendah");
	private final int iCepol = BusanaKatalog.rambut("cepol");
	private final int iEkor1 = BusanaKatalog.rambut("ekor_kuda_1");
	private final int iEkor2 = BusanaKatalog.rambut("ekor_kuda_2");
	private final int iGuanRendah = BusanaKatalog.rambut("guan_rendah");
	private final int iTusukGuan = BusanaKatalog.rambut("tusuk_guan");
	private final int iPitaRambut = BusanaKatalog.rambut("pita_rambut");
	private final int iManik = BusanaKatalog.rambut("manik_buyao");
	private final int iKepang1 = BusanaKatalog.rambut("kepang_1");
	private final int iKepang2 = BusanaKatalog.rambut("kepang_2");
	private final int iJambul = BusanaKatalog.rambut("jambul_anak");
	private final int iRambutAtas = BusanaKatalog.rambut("rambut_atas");
	private final int iSabuk = BusanaKatalog.busana("sabuk");
	private final int iPitaSabuk = BusanaKatalog.busana("pita_sabuk");
	private final int iRokZirah = BusanaKatalog.busana("rok_zirah");
	private final int iRumbai = BusanaKatalog.busana("rumbai_iblis");
	private final int iBahuZirah = BusanaKatalog.busana("bahu_zirah");
	private final int iCelemek = BusanaKatalog.busana("celemek");
	private final int iKantongLengan = BusanaKatalog.busana("lengan_lebar_kantong");

	/** Garment cube index -> skirt role: 0 none, 1 front, 2 back, 3 side. */
	private final int[] rok = new int[NB];
	/** Garment cube index -> wide-skirt scaling (front and back panels of the long and short skirts). */
	private final boolean[] rokLebar = new boolean[NB];
	/** Cube index -> spring chain ordinal (-1 none). */
	private final int[] pegasR = new int[NR];
	private final int[] pegasB = new int[NB];
	/** Reused per frame (no allocation on the hot path; the model is drawn on the render thread only). */
	private final boolean[] onR = new boolean[NR];

	public SosokBusanaModel(ModelPart root, boolean ramping) {
		super(root, ramping);
		Function<String, ModelPart> cari = root.createPartLookup();
		for (int i = 0; i < NR; i++) {
			BusanaKatalog.Kubus k = BusanaKatalog.RAMBUT.get(i);
			rambut[i] = cari.apply(k.id());
			rambutKiri[i] = cari.apply(k.id() + "_kiri");
			besarR[i] = BusanaKatalog.besar(k.id());
			bayanganR[i] = BusanaKatalog.bayangan(k.id());
			pegasR[i] = rantai(k);
		}
		for (int i = 0; i < NB; i++) {
			BusanaKatalog.Kubus k = BusanaKatalog.BUSANA.get(i);
			busana[i] = cari.apply(k.id());
			busanaKiri[i] = cari.apply(k.id() + "_kiri");
			besarB[i] = BusanaKatalog.besar(k.id());
			bayanganB[i] = BusanaKatalog.bayangan(k.id());
			pegasB[i] = rantai(k);
			String id = k.id();
			rok[i] = id.endsWith("_depan") && id.startsWith("rok_") ? 1 : id.endsWith("_belakang") && id.startsWith("rok_") ? 2
					: id.endsWith("_sisi") ? 3 : 0;
			rokLebar[i] = rok[i] == 1 || rok[i] == 2;
		}
		for (int j = 0; j < 3; j++) {
			manik[j] = cari.apply("manik_buyao_" + j);
			rumbai[j] = cari.apply("rumbai_iblis_" + j);
		}
		pitaEkor = cari.apply("pita_rambut_ekor");
		pitaCepol = cari.apply("pita_rambut_cepol");
		pitaCepolKiri = cari.apply("pita_rambut_cepol_kiri");
		tusukRendah = cari.apply("tusuk_guan_rendah");
		kepang1Depan = cari.apply("kepang_1_depan");
		kepang1DepanKiri = cari.apply("kepang_1_depan_kiri");
		kepang2Depan = cari.apply("kepang_2_depan");
		kepang2DepanKiri = cari.apply("kepang_2_depan_kiri");
		jambulSatu = cari.apply("jambul_anak_satu");
		rokZirahB = cari.apply("rok_zirah_b");
		pitaDada = cari.apply("pita_sabuk_dada");
		pitaDadaKiri = cari.apply("pita_sabuk_dada_kiri");
	}

	private static int rantai(BusanaKatalog.Kubus k) {
		if (k.pegas() == null) {
			return -1;
		}
		return switch (k.pegas()) {
			case "RAMBUT" -> Pegas.Rantai.RAMBUT.ordinal();
			case "EKOR" -> k.id().endsWith("_2") ? Pegas.Rantai.EKOR_2.ordinal() : Pegas.Rantai.EKOR_1.ordinal();
			case "KEPANG" -> k.id().endsWith("_2") ? Pegas.Rantai.KEPANG_2.ordinal() : Pegas.Rantai.KEPANG_1.ordinal();
			case "PITA" -> Pegas.Rantai.PITA.ordinal();
			case "BUYAO" -> Pegas.Rantai.BUYAO.ordinal();
			case "JENGGOT" -> Pegas.Rantai.JENGGOT.ordinal();
			case "KUMIS", "ALIS" -> Pegas.Rantai.KUMIS.ordinal();
			case "PONI" -> Pegas.Rantai.PONI.ordinal();
			case "LENGAN" -> Pegas.Rantai.LENGAN_KANAN.ordinal();
			case "JUBAH" -> Pegas.Rantai.JUBAH.ordinal();
			case "SELENDANG" -> Pegas.Rantai.SELENDANG.ordinal();
			case "RUMBAI" -> Pegas.Rantai.RUMBAI.ordinal();
			default -> -1;
		};
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		SosokRupa rupa = k != null && k.rupa() instanceof SosokRupa r ? r : null;
		if (rupa == null || k.lod == SosokDetail.Lod.SILUET || k.lod == SosokDetail.Lod.JAUH) {
			semuaMati();
			return;
		}
		boolean penuh = k.lod == SosokDetail.Lod.PENUH;
		int bayanganMaks = 3;
		if (ShaderCompat.shadowPass()) {
			SosokDetail.Tingkat t = SosokDetail.tingkat();
			bayanganMaks = t == SosokDetail.Tingkat.TINGGI ? 2 : (t == SosokDetail.Tingkat.SEDANG ? 1 : 0);
		}
		tampilRambut(rupa, penuh, bayanganMaks);
		tampilBusana(rupa, penuh, bayanganMaks);
		GerakData gd = GerakData.dari(state);
		rokPanel(rupa, gd);
		if (gd != null && penuh) {
			pegas(gd);
		} else {
			gantungLengan();
		}
	}

	private boolean boleh(boolean besar, int bayangan, boolean penuh, int bayanganMaks) {
		return (penuh || besar) && bayangan <= bayanganMaks;
	}

	private void tampilRambut(SosokRupa rupa, boolean penuh, int bayanganMaks) {
		boolean[] on = this.onR;
		for (int i = 0; i < NR; i++) {
			on[i] = rupa.rambutAda(i) && boleh(besarR[i], bayanganR[i], penuh, bayanganMaks);
			lihat(rambut[i], on[i]);
			lihat(rambutKiri[i], on[i]);
		}
		if (rupa.punya(RupaResolver.V_SANGGUL_KECIL) && rambut[iSanggul] != null) {
			rambut[iSanggul].xScale = 0.85F;
			rambut[iSanggul].yScale = 0.85F;
			rambut[iSanggul].zScale = 0.85F;
		}
		for (ModelPart m : manik) {
			lihat(m, on[iManik]);
		}
		// braids: down the back, or two over the shoulders
		boolean depan = rupa.punya(RupaResolver.V_KEPANG_DEPAN);
		lihat(rambut[iKepang1], on[iKepang1] && !depan);
		lihat(kepang1Depan, on[iKepang1] && depan);
		lihat(kepang1DepanKiri, on[iKepang1] && depan);
		lihat(kepang2Depan, on[iKepang2] && depan);
		lihat(kepang2DepanKiri, on[iKepang2] && depan);
		// the kids' tufts: two, or one in front
		boolean satu = rupa.punya(RupaResolver.V_JAMBUL_SATU);
		lihat(rambut[iJambul], on[iJambul] && !satu);
		lihat(rambutKiri[iJambul], on[iJambul] && !satu);
		lihat(jambulSatu, on[iJambul] && satu);
		// hair ribbons: on the bun, on each twin bun, or round the ponytail
		boolean pita = on[iPitaRambut];
		boolean diSanggul = pita && rupa.rambutAda(iSanggul);
		boolean diCepol = pita && !diSanggul && rupa.rambutAda(iCepol);
		boolean diEkor = pita && !diSanggul && !diCepol && rupa.rambutAda(iEkor1);
		lihat(rambut[iPitaRambut], diSanggul);
		lihat(rambutKiri[iPitaRambut], diSanggul);
		lihat(pitaCepol, diCepol);
		lihat(pitaCepolKiri, diCepol);
		lihat(pitaEkor, diEkor);
		// the hairpin through the guan, or through the old woman's low bun
		boolean rendah = on[iTusukGuan] && !rupa.rambutAda(iGuanRendah) && rupa.rambutAda(iSanggulRendah);
		lihat(rambut[iTusukGuan], on[iTusukGuan] && !rendah);
		lihat(tusukRendah, rendah);
		// the ponytail stays still at LOD KURANG (no springs), as 40.6.1 says
		if (!penuh && rambut[iEkor2] != null) {
			rambut[iEkor2].xRot = 0.0F;
		}
	}

	private void tampilBusana(SosokRupa rupa, boolean penuh, int bayanganMaks) {
		boolean lebar = rupa.punya(RupaResolver.V_ROK_LEBAR);
		boolean sabuk = rupa.busanaAda(iSabuk);
		for (int i = 0; i < NB; i++) {
			boolean on = rupa.busanaAda(i) && boleh(besarB[i], bayanganB[i], penuh, bayanganMaks);
			lihat(busana[i], on);
			lihat(busanaKiri[i], on);
			if (i == iRokZirah) {
				lihat(rokZirahB, on);
			} else if (i == iRumbai) {
				for (ModelPart m : rumbai) {
					lihat(m, on);
				}
			} else if (i == iPitaSabuk) {
				lihat(busana[i], on && sabuk);
				lihat(busanaKiri[i], on && sabuk);
				lihat(pitaDada, on && !sabuk);
				lihat(pitaDadaKiri, on && !sabuk);
			} else if (i == iBahuZirah) {
				lihat(busana[i], on && !rupa.punya(RupaResolver.V_BAHU_KIRI));
			}
			if (on && lebar && rokLebar[i] && busana[i] != null) {
				busana[i].xScale = 1.06F;
			}
		}
	}

	/**
	 * The skirt panels (40.5.8): 40G's smoothed angles and floor spread when Gerak drives the body, else the kinematic
	 * rule from this frame's legs. The armour skirt is 1.6 x stiffer, the apron opens with half the front angle.
	 */
	private void rokPanel(SosokRupa rupa, @Nullable GerakData gd) {
		float depan;
		float belakang;
		float kanan;
		float kiri;
		float skalaDepan = 1.0F;
		float skalaBelakang = 1.0F;
		if (gd != null) {
			depan = gd.rokDepanX;
			belakang = gd.rokBelakangX;
			kanan = gd.rokKananZ;
			kiri = gd.rokKiriZ;
			skalaDepan = gd.rokDepanSkalaY;
			skalaBelakang = gd.rokBelakangSkalaY;
		} else {
			float maju = Math.min(0.0F, Math.min(rightLeg.xRot, leftLeg.xRot));
			float mundur = Math.max(0.0F, Math.max(rightLeg.xRot, leftLeg.xRot));
			depan = maju * 0.85F - body.xRot * 0.9F;
			belakang = mundur * 0.85F - body.xRot * 0.9F;
			kanan = Math.max(0.0F, rightLeg.zRot) * 0.9F;
			kiri = -Math.max(0.0F, -leftLeg.zRot) * 0.9F;
		}
		for (int i = 0; i < NB; i++) {
			ModelPart p = busana[i];
			if (p == null || !p.visible && (busanaKiri[i] == null || !busanaKiri[i].visible)) {
				continue;
			}
			switch (rok[i]) {
				case 1 -> {
					p.xRot += depan;
					p.yScale *= skalaDepan;
				}
				case 2 -> {
					p.xRot += belakang;
					p.yScale *= skalaBelakang;
				}
				case 3 -> {
					// the table panel hangs at +x (the wearer's left), its mirror at -x (the wearer's right)
					p.zRot += kiri;
					if (busanaKiri[i] != null) {
						busanaKiri[i].zRot += kanan;
					}
				}
				default -> {
				}
			}
		}
		if (iRokZirah >= 0 && busana[iRokZirah] != null && busana[iRokZirah].visible) {
			busana[iRokZirah].xRot += depan * 0.6F;
			if (rokZirahB != null) {
				rokZirahB.xRot += belakang * 0.6F;
			}
		}
		if (iCelemek >= 0 && busana[iCelemek] != null && busana[iCelemek].visible) {
			busana[iCelemek].xRot += depan * 0.5F;
		}
	}

	/** Adds the spring angles of 40G (40.5.8) to every cube on a chain. */
	private void pegas(GerakData gd) {
		float[] px = gd.pegasX;
		float[] pz = gd.pegasZ;
		for (int i = 0; i < NR; i++) {
			int c = pegasR[i];
			if (c < 0) {
				continue;
			}
			float kaku = i == iRambutAtas ? 0.6F : 1.0F;
			putar(rambut[i], px[c] * kaku, pz[c] * kaku);
			putar(rambutKiri[i], px[c] * kaku, pz[c] * kaku);
			if (i == iKepang1) {
				putar(kepang1Depan, px[c] * 0.6F, pz[c]);
				putar(kepang1DepanKiri, px[c] * 0.6F, pz[c]);
			} else if (i == iKepang2) {
				putar(kepang2Depan, px[c] * 0.6F, pz[c]);
				putar(kepang2DepanKiri, px[c] * 0.6F, pz[c]);
			} else if (i == iPitaRambut) {
				putar(pitaCepol, px[c], pz[c]);
				putar(pitaCepolKiri, px[c], pz[c]);
				putar(pitaEkor, px[c], pz[c]);
			}
		}
		int buyao = Pegas.Rantai.BUYAO.ordinal();
		for (int j = 0; j < 3; j++) {
			// the three beads swing a little apart (phase 0.15 between them)
			float f = 1.0F - 0.15F * j;
			putar(manik[j], px[buyao] * f, pz[buyao] * (j == 1 ? -f : f));
		}
		int lenganKiri = Pegas.Rantai.LENGAN_KIRI.ordinal();
		for (int i = 0; i < NB; i++) {
			int c = pegasB[i];
			if (c < 0) {
				continue;
			}
			putar(busana[i], px[c], pz[c]);
			if (i == iKantongLengan) {
				putar(busanaKiri[i], px[lenganKiri], pz[lenganKiri]);
			} else {
				putar(busanaKiri[i], px[c], pz[c]);
			}
			if (i == iPitaSabuk) {
				putar(pitaDada, px[c], pz[c]);
				putar(pitaDadaKiri, px[c], pz[c]);
			} else if (i == iRumbai) {
				for (ModelPart m : rumbai) {
					putar(m, px[c], pz[c]);
				}
			}
		}
	}

	/** Without springs the wide sleeves' pockets still hang down in the world when the arm rises. */
	private void gantungLengan() {
		ModelPart kanan = iKantongLengan >= 0 ? busana[iKantongLengan] : null;
		ModelPart kiri = iKantongLengan >= 0 ? busanaKiri[iKantongLengan] : null;
		if (kanan != null && kanan.visible) {
			kanan.xRot += -rightArm.xRot * 0.85F;
			kanan.zRot += -rightArm.zRot * 0.6F;
		}
		if (kiri != null && kiri.visible) {
			kiri.xRot += -leftArm.xRot * 0.85F;
			kiri.zRot += -leftArm.zRot * 0.6F;
		}
	}

	private static void putar(@Nullable ModelPart p, float x, float z) {
		if (p != null && p.visible) {
			p.xRot += x;
			p.zRot += z;
		}
	}

	private static void lihat(@Nullable ModelPart p, boolean on) {
		if (p != null) {
			p.visible = on;
		}
	}

	/** Hides every cube (no look, or a far body). */
	private void semuaMati() {
		for (int i = 0; i < NR; i++) {
			lihat(rambut[i], false);
			lihat(rambutKiri[i], false);
		}
		for (int i = 0; i < NB; i++) {
			lihat(busana[i], false);
			lihat(busanaKiri[i], false);
		}
		for (ModelPart m : manik) {
			lihat(m, false);
		}
		for (ModelPart m : rumbai) {
			lihat(m, false);
		}
		lihat(pitaEkor, false);
		lihat(pitaCepol, false);
		lihat(pitaCepolKiri, false);
		lihat(tusukRendah, false);
		lihat(kepang1Depan, false);
		lihat(kepang1DepanKiri, false);
		lihat(kepang2Depan, false);
		lihat(kepang2DepanKiri, false);
		lihat(jambulSatu, false);
		lihat(rokZirahB, false);
		lihat(pitaDada, false);
		lihat(pitaDadaKiri, false);
	}
}
