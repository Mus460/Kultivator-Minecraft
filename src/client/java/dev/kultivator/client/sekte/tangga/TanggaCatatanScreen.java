package dev.kultivator.client.sekte.tangga;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.mandiri.MandiriBoardScreen;
import dev.kultivator.sekte.tangga.TanggaPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Murid › Daftar › tangga (DESIGN 43.5; was the "Catatan Tangga" screen, R23 of the integration audit): one row per loaded
 * disciple with the steps of its last climb on the Tangga Seribu, its best and the heart verdict; a gold row reached the
 * peak; the Ujian Hati hint under the list. A click (or Enter on the chosen row) opens the disciple on Murid › Murid › hati
 * (R18; the rows carry only the name, matched against the hall board). A panel drawn by the Daftar page; the data comes
 * from module G's {@code Catatan} payload (the records of {@code /kultivasi perguruan tangga}), asked for when the sub shows.
 */
public final class TanggaCatatanScreen {

	private static final String G = "kultivator.sekte.tangga.";
	private static final int ROW_H = 12;
	private static final int PEAK = 0xF2C14E;

	private static @Nullable CompoundTag latest;

	private final DaftarGulir daftar = new DaftarGulir();

	static void onRecords(TanggaPayloads.Catatan payload) {
		latest = payload.data();
	}

	/** Forgets the records (disconnect). */
	static void bersih() {
		latest = null;
	}

	/** True when module G runs (the sub is hidden otherwise). */
	public static boolean ada() {
		return TanggaClient.aktif();
	}

	/** Asks the server for the records (the sub came on screen). */
	public static void minta() {
		if (ClientPlayNetworking.canSend(TanggaPayloads.Minta.TYPE)) {
			ClientPlayNetworking.send(new TanggaPayloads.Minta());
		}
	}

	/** Draws the panel into the box; {@code kunci} the focus key of its list. */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1) {
		CompoundTag data = latest;
		if (data == null) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable("kultivator.sekte_sambung.tangga.memuat"), null, null, x0, y0,
					x1, y1);
			return;
		}
		Component heading = Component.translatable(G + "list.title",
				Component.translatable(data.getBooleanOr("stair", false) ? G + "list.stair" : G + "list.no_stair"));
		int top = y0 + l.kepala(heading, x0, y0, x1);
		Component hint = Component.translatable(G + "list.hint", data.getIntOr("pk", 0), data.getIntOr("hari", 0));
		int hintH = Math.min(18, Math.round(Brush.wrapHeight(l.font, hint, x1 - x0, 1.0F)));
		int bottom = y1 - hintH - 2;
		l.bungkus(hint, x0, bottom + 2, x1 - x0, l.gaya.teksSamar, hintH);
		ListTag rows = data.getListOrEmpty("rows");
		if (rows.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable(G + "list.empty"), null, null, x0, top, x1, bottom);
			return;
		}
		daftar.gambar(l, kunci, x0, top, x1, bottom, rows.size(), ROW_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			CompoundTag row = rows.getCompoundOrEmpty(i);
			String nama = row.getStringOr("name", "?");
			int steps = row.getIntOr("steps", -1);
			boolean peak = row.getBooleanOr("peak", false);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
			if (peak) {
				lk.g.fill(rx0, ry0, rx0 + 3, ry1, Palette.opaque(PEAK));
			}
			Component name = Component.literal(nama);
			Component line = steps < 0 ? Component.translatable(G + "list.none", name)
					: Component.translatable(G + "list.line", name, steps, row.getIntOr("best", steps),
							Component.translatable(G + "hati." + Math.max(0, Math.min(3, row.getIntOr("heart", 0)))));
			lk.teks(line, rx0 + 6, ry0 + 1, rx1 - rx0 - 8, peak ? Palette.SEAL_DARK : steps < 0 ? lk.gaya.teksLemah : lk.gaya.teks);
			lk.zona(kunci + "#b" + i, rx0, ry0, rx1, ry1, () -> {
				UiSound.press();
				buka(nama);
			}, true, null, false);
			if (sorot) {
				lk.tip(List.of(line, Component.translatable("kultivator.gui43.m04.klik_murid").withStyle(ChatFormatting.DARK_GRAY)));
			}
		}, () -> {
			int p = daftar.pilihan();
			if (p >= 0 && p < rows.size()) {
				buka(rows.getCompoundOrEmpty(p).getStringOr("name", ""));
			}
		}, null);
	}

	private static void buka(String nama) {
		MandiriBoardScreen.bukaMuridNama(nama, "hati");
	}
}
