package dev.kultivator.client.tempat.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.dunia.tempat.model.PapanHidupRenderer;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.TeksTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.PapanHidupEntity;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * The renderer of {@code kultivator:papan_hidup} (DESIGN 38.O.2 model by 38MD, 38.U.4 content by 38UI): the 38MD board
 * with its six pinned sheets, and on every sheet a kit paper panel ({@code panel_kertas}) with the sheet's writing,
 * following the sheet's flutter and the paste-in of the newest one exactly (same pivot, same angle, same scale).
 *
 * <ul>
 * <li><b>kabar</b> (rumours): a vermilion title fitted to the sheet (shrunk, then two lines), an ink rule, the body
 * wrapped and cut with an ellipsis;</li>
 * <li><b>misi</b> (guild missions): the same with the guild's round seal in the lower corner;</li>
 * <li><b>buronan</b> (wanted): sheet 0 is the poster: "DICARI" in red, the wanted face (the player's own skin when
 * they are in the tab list, else a sepia sketch), the name and the bounty; the other sheets are rumours.</li>
 * </ul>
 * Text only for boards granted a {@link TeksDunia#izin} slot (≤ 24 blocks, ≤ 12 boards), body lines only within
 * {@link TeksDunia#jarakRinci()}; the sheet data is copied into the state only then. Client code only.
 */
public class PapanHidupTeksRenderer extends PapanHidupRenderer<PapanHidupEntity> {

	/** Sheet positions on the board face, mirrored from the 38MD model: x, y (model px, top-left), width, height. */
	private static final float[][] LEMBAR = {
			{-14.0F, -12.0F, 7.0F, 9.0F}, {-6.0F, -13.0F, 6.0F, 8.0F}, {1.0F, -12.5F, 7.0F, 9.0F}, {9.0F, -12.0F, 5.0F, 7.0F},
			{-12.0F, -2.5F, 8.0F, 6.0F}, {3.0F, -3.0F, 8.0F, 6.5F}};
	/** The board part's offset in the model (px). */
	private static final float PAPAN_Y = 8.0F;
	/** The sheet pivot's z (px) and the depths of the paper panel and the ink in front of the sheet face (-0.1). */
	private static final float LEMBAR_Z = -1.15F;
	private static final float Z_PANEL = -0.16F;
	private static final float Z_TINTA = -0.22F;

	private static final int TINTA = Palette.INK;
	private static final int TINTA_LEMAH = 0xFF4A3A2A;
	private static final int JUDUL = 0xFF7A1E14;
	private static final int MERAH = 0xFFA01A12;
	private static final int GARIS = 0x907A5A3A;

	/** The board state plus the writing of its sheets. */
	public static final class Isi extends PapanHidupRenderer.State {
		public int id;
		public boolean teks;
		public boolean rinci;
		public String jenis = PapanHidupEntity.JENIS_KABAR;
		public final Component[] judul = new Component[PapanHidupEntity.MAKS_LEMBAR];
		public final Component[] baris = new Component[PapanHidupEntity.MAKS_LEMBAR];
		public String wajah = "";
		public long hadiah;
	}

	public PapanHidupTeksRenderer(EntityRendererProvider.Context context) {
		super(context, PapanHidupEntity::jumlahLembar, PapanHidupEntity::sejakBerubah);
	}

	@Override
	public State createRenderState() {
		return new Isi();
	}

	@Override
	public void extractRenderState(PapanHidupEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (!(state instanceof Isi isi)) {
			return;
		}
		isi.id = entity.getId();
		double r = TeksDunia.jarak();
		isi.teks = state.sheets > 0 && state.distanceToCameraSq <= r * r;
		isi.rinci = isi.teks && TeksDunia.rinci(state.distanceToCameraSq);
		if (!isi.teks) {
			return;
		}
		isi.jenis = entity.jenis();
		for (int i = 0; i < PapanHidupEntity.MAKS_LEMBAR; i++) {
			boolean ada = i < state.sheets;
			isi.judul[i] = ada ? entity.judul(i) : null;
			isi.baris[i] = ada && isi.rinci ? entity.baris(i) : null;
		}
		isi.wajah = entity.wajah();
		isi.hadiah = entity.hadiah();
	}

	@Override
	protected void submitIsi(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		if (!(state instanceof Isi isi) || !isi.teks || !TeksDunia.izin(isi.id, state.distanceToCameraSq)) {
			return;
		}
		int light = state.lightCoords;
		boolean buronan = PapanHidupEntity.JENIS_BURONAN.equals(isi.jenis);
		boolean misi = PapanHidupEntity.JENIS_MISI.equals(isi.jenis);
		for (int i = 0; i < state.sheets && i < LEMBAR.length; i++) {
			float[] s = LEMBAR[i];
			float w = s[2];
			float h = s[3];
			poseStack.pushPose();
			// the sheet's own transform (ModelPart.translateAndRotate of sheet_i under the board part)
			poseStack.translate((s[0] + w / 2.0F) / 16.0F, (PAPAN_Y + s[1]) / 16.0F, LEMBAR_Z / 16.0F);
			if (state.near) {
				float xRot = (Mth.sin(state.ageInTicks * 0.21F + i * 1.7F) * 0.5F + 0.5F) * -0.12F;
				poseStack.rotate(new Quaternionf().rotationX(xRot));
			}
			if (i == state.sheets - 1 && state.paste < 1.0F) {
				float k = Math.max(0.01F, state.paste);
				poseStack.scale(k, k, 1.0F);
			}
			poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
			// model px now: origin at the sheet's top centre, y down, the viewer toward -z
			TeksDunia.panel(poseStack, collector, TempatWarna.Gaya.KERTAS, -w / 2.0F - 0.06F, -0.06F, w / 2.0F + 0.06F, h + 0.06F, Z_PANEL, -1.0F,
					Math.min(w, h) * 0.16F, light, -1);
			poseStack.translate(0.0F, 0.0F, Z_TINTA - Z_PANEL);
			if (buronan && i == 0) {
				poster(isi, poseStack, collector, w, h, light);
			} else {
				lembar(isi, i, poseStack, collector, w, h, misi, light);
			}
			poseStack.popPose();
		}
	}

	/** A rumour or mission sheet: fitted title, rule, wrapped body (near only), a seal on missions. */
	private static void lembar(Isi isi, int i, PoseStack poseStack, SubmitNodeCollector collector, float w, float h, boolean misi, int light) {
		Component judul = isi.judul[i];
		if (judul == null) {
			return;
		}
		float m = 0.55F;
		float lebar = w - 2.0F * m;
		float y = m;
		y = judul(poseStack, collector, judul, lebar, y, JUDUL, light);
		if (judul.getString().isEmpty()) {
			y = m;
		} else {
			TeksDunia.garis(poseStack, collector, -lebar / 2.0F, lebar / 2.0F, y + 0.12F, 0.08F, GARIS, light);
			y += 0.35F;
		}
		float bawah = h - m - (misi ? 1.6F : 0.0F);
		Component baris = isi.baris[i];
		if (isi.rinci && baris != null && !baris.getString().isEmpty()) {
			float tinggi = 0.7F;
			int fontLebar = Math.round(lebar / tinggi * TeksDunia.BARIS);
			int muat = (int) Math.floor((bawah - y) / (tinggi * 1.05F));
			List<FormattedCharSequence> lines = TeksDunia.bungkus(baris, fontLebar, muat);
			for (FormattedCharSequence line : lines) {
				TeksDunia.baris(poseStack, collector, line, -lebar / 2.0F, y, tinggi, TeksDunia.Rata.KIRI, -1.0F, TINTA, light);
				y += tinggi * 1.05F;
			}
		}
		if (misi) {
			float u = Math.min(2.0F, w * 0.3F);
			TeksDunia.segel(poseStack, collector, TeksDunia.Segel.BULAT, w / 2.0F - m - u + 0.2F, h - m - u + 0.2F, u, 0.02F, -1.0F, light);
		}
	}

	/** The wanted poster on sheet 0: DICARI, the face, the name, the bounty. */
	private static void poster(Isi isi, PoseStack poseStack, SubmitNodeCollector collector, float w, float h, int light) {
		float m = 0.5F;
		float lebar = w - 2.0F * m;
		float y = m;
		Component dicari = TeksTempat.k("papan.dicari");
		float tinggi = Math.min(1.05F, lebar / Math.max(1, TeksDunia.lebar(dicari)) * TeksDunia.BARIS);
		TeksDunia.baris(poseStack, collector, dicari, 0.0F, y, tinggi, TeksDunia.Rata.TENGAH, -1.0F, MERAH, light);
		y += tinggi + 0.2F;
		float wajah = Math.min(lebar * 0.62F, h * 0.4F);
		String nama = isi.wajah;
		if (nama.isEmpty() && isi.judul[0] != null) {
			nama = isi.judul[0].getString();
		}
		// a dark ink frame around the face, then the face itself
		TeksDunia.garis(poseStack, collector, -wajah / 2.0F - 0.12F, wajah / 2.0F + 0.12F, y - 0.12F, wajah + 0.24F, 0xFF3A2A1A, light);
		TeksDunia.wajah(poseStack, collector, nama, -wajah / 2.0F, y, wajah, -0.07F, -1.0F, light);
		y += wajah + 0.25F;
		Component judul = isi.judul[0] != null && !isi.judul[0].getString().isEmpty() ? isi.judul[0] : Component.literal(nama);
		y = judul(poseStack, collector, judul, lebar, y, TINTA, light);
		if (isi.hadiah > 0L) {
			Component hadiah = TeksTempat.k("papan.hadiah", TeksTempat.angka(isi.hadiah));
			float th = Math.min(0.8F, lebar / Math.max(1, TeksDunia.lebar(hadiah)) * TeksDunia.BARIS);
			TeksDunia.baris(poseStack, collector, hadiah, 0.0F, y + 0.1F, th, TeksDunia.Rata.TENGAH, -1.0F, MERAH, light);
			y += th + 0.2F;
		}
		Component baris = isi.baris[0];
		if (isi.rinci && baris != null && !baris.getString().isEmpty() && y < h - m - 0.6F) {
			float t = 0.55F;
			int fontLebar = Math.round(lebar / t * TeksDunia.BARIS);
			int muat = (int) Math.floor((h - m - y) / (t * 1.05F));
			for (FormattedCharSequence line : TeksDunia.bungkus(baris, fontLebar, muat)) {
				TeksDunia.baris(poseStack, collector, line, 0.0F, y, t, TeksDunia.Rata.TENGAH, -1.0F, TINTA_LEMAH, light);
				y += t * 1.05F;
			}
		}
	}

	/**
	 * A title centred at the top {@code y}: one line shrunk to fit (down to 0.75 px), else two wrapped lines at 0.8 px;
	 * returns the y below it.
	 */
	private static float judul(PoseStack poseStack, SubmitNodeCollector collector, Component judul, float lebar, float y, int warna, int light) {
		if (judul.getString().isEmpty()) {
			return y;
		}
		float penuh = 1.15F;
		int w = Math.max(1, TeksDunia.lebar(judul));
		float pas = lebar / w * TeksDunia.BARIS;
		if (pas >= 0.75F) {
			float t = Math.min(penuh, pas);
			TeksDunia.baris(poseStack, collector, judul, 0.0F, y, t, TeksDunia.Rata.TENGAH, -1.0F, warna, light);
			return y + t;
		}
		float t = 0.8F;
		List<FormattedCharSequence> lines = TeksDunia.bungkus(judul, Math.round(lebar / t * TeksDunia.BARIS), 2);
		for (FormattedCharSequence line : lines) {
			TeksDunia.baris(poseStack, collector, line, 0.0F, y, t, TeksDunia.Rata.TENGAH, -1.0F, warna, light);
			y += t;
		}
		return y;
	}
}
