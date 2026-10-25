package dev.kultivator.client.sekte.kepribadian;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.mandiri.MandiriBoardScreen;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.kepribadian.KepribadianPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Murid › Daftar › suasana (DESIGN 43.5; was the "Suasana Hati Murid" screen of DESIGN 36.2.1): every loaded disciple's
 * heart, the most troubled first: its state stripe, name, trait seals, a loyalty bar with the leaving thresholds marked and
 * the state with the countdown of one preparing to leave; under a heading, the ones that left the sect and how. A click (or
 * Enter on the chosen row) opens the disciple on Murid › Murid › hati (R18). A panel drawn by the Daftar page
 * ({@link MandiriBoardScreen}); the list comes from module P's {@code Daftar} payload, asked for when the sub shows.
 */
public final class SuasanaScreen {

	private static final String P = "kultivator.sekte.kepribadian.";
	private static final String M = "kultivator.gui43.m04.";
	private static final int ROW_H = 22;
	private static final String[] KEADAAN = {"setia", "gelisah", "membangkang", "akan_pergi"};

	private static @Nullable KepribadianPayloads.Daftar latest;

	private final DaftarGulir daftar = new DaftarGulir();

	static void onList(KepribadianPayloads.Daftar list) {
		latest = list;
	}

	/** Forgets the list (disconnect). */
	static void bersih() {
		latest = null;
	}

	/** Asks the server for the list (the sub came on screen). */
	public static void minta() {
		KepribadianPayloads.Aksi ask = new KepribadianPayloads.Aksi(-1, KepribadianPayloads.A_LIST, "");
		if (ClientPlayNetworking.canSend(ask.type())) {
			ClientPlayNetworking.send(ask);
		}
	}

	/** Draws the panel into the box; {@code kunci} the focus key of its list. */
	public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1) {
		KepribadianPayloads.Daftar list = latest;
		if (list == null) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable(P + "suasana.memuat"), null, null, x0, y0, x1, y1);
			return;
		}
		List<KepribadianPayloads.Baris> rows = list.rows();
		List<KepribadianPayloads.Pergi> gone = list.gone();
		if (rows.isEmpty() && gone.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable(P + "suasana.kosong"), null, null, x0, y0, x1, y1);
			return;
		}
		int nRows = rows.size();
		int count = nRows + (gone.isEmpty() ? 0 : 1 + gone.size());
		daftar.judul(i -> i == nRows && !gone.isEmpty(), 13);
		daftar.gambar(l, kunci, x0, y0, x1, y1, count, ROW_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			if (i < nRows) {
				baris(lk, kunci, i, rows.get(i), rx0, ry0, rx1, ry1, sorot, pilih);
			} else if (i == nRows) {
				lk.kepala(Component.translatable(P + "suasana.yangpergi", gone.size()), rx0, ry0, rx1);
			} else {
				pergi(lk, gone.get(i - nRows - 1), list.today(), rx0, ry0, rx1, ry1);
			}
		}, () -> {
			int p = daftar.pilihan();
			if (p >= 0 && p < nRows) {
				buka(rows.get(p));
			}
		}, null);
	}

	private void baris(Lukis l, String kunci, int i, KepribadianPayloads.Baris row, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih) {
		int state = Math.max(0, Math.min(3, row.state()));
		int rgb = HatiTab.STATE_RGB[state];
		l.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
		l.g.fill(rx0, ry0, rx0 + 3, ry1, Palette.opaque(rgb));
		int tx = rx0 + 6;
		Component keadaan = Component.translatable("kultivator.sekte.keadaan." + KEADAAN[state]);
		Component label = row.daysLeft() >= 0 ? Component.translatable(P + "suasana.sisa", keadaan, row.daysLeft()) : keadaan;
		int lw = Math.min(l.font.width(label), Math.round((rx1 - tx) * 0.45F));
		l.teks(Brush.bold(row.name()), tx, ry0 + 2, rx1 - tx - lw - 6, l.gaya.teks);
		l.teks(label, rx1 - 2 - lw, ry0 + 2, lw, Palette.onPaper(rgb));
		// the trait seals, then the loyalty bar with the thresholds 30 (unrest) and 50 (calm)
		int ix = tx;
		List<Component> traitNames = new ArrayList<>();
		for (String id : row.traits().isEmpty() ? new String[0] : row.traits().split(",")) {
			Sifat sifat = Sifat.byId(id);
			if (sifat == null) {
				continue;
			}
			int icon = sifat.ordinal();
			l.g.blit(RenderPipelines.GUI_TEXTURED, HatiTab.SIFAT_ATLAS, ix, ry0 + 10, (icon % 4) * 16.0F, (icon / 4) * 16.0F, 11, 11, 16, 16, 64, 48,
					0xFFFFFFFF);
			traitNames.add(Component.translatable(sifat.key()).withColor(HatiTab.AXIS_RGB[sifat.axis()]));
			ix += 12;
		}
		int barX = Math.max(ix + 4, tx + 40);
		int barX1 = rx1 - 4;
		if (barX1 - barX >= 20) {
			Brush.rect(l.g, barX, ry0 + 14, barX1, ry0 + 18, Palette.argb(0x50, Palette.PAPER_DEEP));
			Brush.rect(l.g, barX, ry0 + 14, barX + (barX1 - barX) * Math.max(0, Math.min(100, row.loyalty())) / 100.0F, ry0 + 18, Palette.opaque(rgb));
			for (int mark : new int[]{30, 50}) {
				float mx = barX + (barX1 - barX) * mark / 100.0F;
				Brush.rect(l.g, mx - 0.5F, ry0 + 13, mx + 0.5F, ry0 + 19, Palette.INK_SOFT);
			}
		}
		// one click opens the disciple (the row zone of the list only selects)
		l.zona(kunci + "#b" + i, rx0, ry0, rx1, ry1, () -> {
			UiSound.press();
			buka(row);
		}, true, null, false);
		if (sorot) {
			List<Component> lines = new ArrayList<>();
			lines.add(row.name().copy().withStyle(ChatFormatting.GOLD));
			lines.addAll(traitNames);
			lines.add(Component.translatable(P + "suasana.angka", row.loyalty(), row.contentment()).withStyle(ChatFormatting.GRAY));
			if ((row.flags() & KepribadianPayloads.F_SEALED) != 0) {
				lines.add(Component.translatable(P + "tab.terikat").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			if ((row.flags() & KepribadianPayloads.F_PENDING) != 0) {
				lines.add(Component.translatable(P + "suasana.pending").withStyle(ChatFormatting.RED));
			}
			lines.add(Component.translatable(P + "suasana.petunjuk").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			lines.add(Component.translatable(M + "klik_murid").withStyle(ChatFormatting.DARK_GRAY));
			l.tip(lines);
		}
	}

	private static void pergi(Lukis l, KepribadianPayloads.Pergi gone, long today, int rx0, int ry0, int rx1, int ry1) {
		l.kartu(rx0, ry0, rx1, ry1, 0.0F, false);
		l.teks(gone.name(), rx0 + 6, ry0 + 2, rx1 - rx0 - 8, l.gaya.teksLemah);
		Component how = Component.translatable("kultivator.sekte.kepergian." + gone.how())
				.append(Component.translatable(P + "cmd.hari_lalu", Math.max(0L, today - gone.day())));
		l.samar(how, rx0 + 6, ry0 + 11, rx1 - rx0 - 8);
	}

	private static void buka(KepribadianPayloads.Baris row) {
		MandiriBoardScreen.bukaMurid("", row.entityId(), false, "hati");
	}
}
