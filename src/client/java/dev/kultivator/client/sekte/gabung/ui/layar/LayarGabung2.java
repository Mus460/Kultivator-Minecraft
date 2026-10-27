package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Client registration of the 41U-2 screens (DESIGN 41.10.4-41.10.5, 41.10.7-41.10.9): Paviliun Jasa, Papan Misi Sekte,
 * Gua Kediaman, Politik Sekte, the Balai Hukum hearing, the elders' Shifu offers and the verdict card. Called once from
 * {@code GabungUiClient.init} (41U-1). Also the two small helpers these screens share: their lang prefix and a PK price
 * with the bamboo-tally icon (PK is never NB, 41.0 butir 4).
 */
public final class LayarGabung2 {

	/** Lang prefix of the 41U-2 texts (fragment {@code sekte_gabung_ui2}). */
	public static final String K = "kultivator.gabung.ui2.";

	private static final int IKON = 10;

	private static boolean terdaftar;

	/** Registers every 41U-2 screen with the sheet router (38 sheet, drawn with the single kit). Idempotent; client thread. */
	public static void daftar() {
		if (terdaftar) {
			return;
		}
		terdaftar = true;
		UiTempatClient.daftar(GabungLembar.Paviliun.LAYAR, LayarPaviliun::new);
		UiTempatClient.daftar(GabungLembar.Papan.LAYAR, LayarPapan::new);
		UiTempatClient.daftar(GabungLembar.Kediaman.LAYAR, LayarKediaman::new);
		UiTempatClient.daftar(GabungLembar.Politik.LAYAR, LayarPolitik::new);
		UiTempatClient.daftar(GabungLembar.Shifu.LAYAR, LayarShifu::new);
		UiTempatClient.daftar(GabungLembar.Putusan.LAYAR, LayarPutusan::new);
	}

	/** A 41U-2 text. */
	public static MutableComponent u(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	/**
	 * A PK price "380" with the tally icon at (x, y) (or right-aligned at x); red when {@code punya} (PK on hand, -1 = do not
	 * compare) is short, with the shortfall as tooltip. Returns the width drawn.
	 */
	public static int pk(Lukis l, long harga, long punya, float x, float y, boolean kanan) {
		return pk(l, harga, punya, x, y, kanan, l.gaya.teks);
	}

	/** {@link #pk} in ink {@code warna} (inside a light well use {@code gaya.teksSumur()}). */
	public static int pk(Lukis l, long harga, long punya, float x, float y, boolean kanan, int warna) {
		boolean kurang = punya >= 0L && harga > punya;
		Component angka = Component.literal(Brush.angka(harga));
		int lebar = IKON + 2 + l.font.width(angka);
		int x0 = Math.round(kanan ? x - lebar : x);
		int yi = Math.round(y - 1.0F);
		Glyphs.Atlas.ikon(l.g, LembarKunci.Ikon.PK, x0, yi, IKON, 0xFFFFFFFF);
		Brush.text(l.g, l.font, angka, x0 + IKON + 2, y, kurang ? Palette.BAHAYA : warna, 1.0F, false);
		if (l.di(x0, yi, x0 + lebar, yi + IKON)) {
			l.tip(kurang ? List.of(u("pk.tip", Brush.angka(harga)), u("pk.kurang", Brush.angka(harga - punya)))
					: List.of(u("pk.tip", Brush.angka(harga))));
		}
		return lebar;
	}

	private LayarGabung2() {
	}
}
