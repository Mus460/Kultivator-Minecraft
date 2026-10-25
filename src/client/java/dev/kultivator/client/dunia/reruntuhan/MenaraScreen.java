package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.menara.PenjagaLantaiEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dunia › Alam › Menara (DESIGN 34.6 T, 43.5, 43.12.1: the old "Menara Seratus" card screen is now this sub of the Alam
 * page): an ink painting of the nine-tier pagoda's hundred floors as ten bands, lit up to the player's best floor (each
 * band's guardian in its tooltip), and beside it one scroll with the best floor and its bar, the open gates, Luka Menara,
 * the floor pattern, the week's ranking with the best of all time, and the module's other records (ancestral ruins,
 * Pahala from the prisons, the seals broken). The numbers come from the server while the sub is shown
 * ({@code RNet.Action "menara_info"} → {@code op: "info"}, {@link #onInfo}); until then it says it is loading. Text at 1.0
 * (43.0.5); the pagoda narrows on a small box instead of shrinking text.
 */
public final class MenaraScreen {

	private static final String K = "kultivator.reruntuhan.menara.";
	private static final long WAIT_MILLIS = 4000L;

	private static @Nullable CompoundTag data;
	private static long askedAt;

	/** One line of the scroll: a heading, a wrapped text line, or the progress bar ({@code bar}). */
	private record Row(@Nullable Component judul, @Nullable FormattedCharSequence teks, int warna, boolean bar) {
	}

	private final List<Row> rows = new ArrayList<>();
	private final DaftarGulir daftar = new DaftarGulir().judul(i -> i < rows.size() && rows.get(i).judul() != null, 13);

	/** Asks the server for the tower's numbers (the sub came on screen; a quiet no-op while not connected). */
	public static void request() {
		askedAt = Util.getMillis();
		if (ClientPlayNetworking.canSend(RNet.Action.TYPE)) {
			ClientPlayNetworking.send(new RNet.Action("menara_info", 0, 0));
		}
	}

	/** The server's answer ({@code op: "info"}). */
	static void onInfo(CompoundTag tag) {
		data = tag.copy();
	}

	/** Forgets the numbers (disconnect). */
	static void reset() {
		data = null;
	}

	/** Draws the sub into the box. */
	public void gambar(Lukis l, int x0, int y0, int x1, int y1) {
		CompoundTag d = data;
		if (d == null) {
			boolean late = Util.getMillis() - askedAt > WAIT_MILLIS;
			Component line = late ? Component.translatable("kultivator.gui43.m13.menara.diam")
					: Component.translatable(K + "ui.loading").append(".".repeat((int) ((Util.getMillis() - askedAt) / 300L % 4L)));
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, line, null, null, x0, y0, x1, y1);
			return;
		}
		int best = Mth.clamp(d.getIntOr("best", 0), 0, 100);
		int unlocked = Mth.clamp(d.getIntOr("unlocked", 1), 1, 10);
		int pagodaW = Mth.clamp((x1 - x0) * 2 / 5, 70, 150);
		pagoda(l, best, unlocked, x0, y0 + 2, x0 + pagodaW, y1);
		int lx = x0 + pagodaW + 8;
		isi(l, d, best, unlocked, x1 - lx - 8);
		daftar.gambar(l, "menara", lx, y0, x1, y1, rows.size(), 10, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Row r = rows.get(i);
			if (r.judul() != null) {
				lk.kepala(r.judul(), rx0, ry0, rx1);
			} else if (r.bar()) {
				lk.g.fill(rx0, ry0 + 2, rx1 - 2, ry0 + 6, Palette.argb(0x50, Palette.INK_FAINT));
				lk.g.fill(rx0, ry0 + 2, rx0 + (rx1 - 2 - rx0) * best / 100, ry0 + 6, Palette.GOLD);
				for (int b = 1; b < 10; b++) {
					int tx = rx0 + (rx1 - 2 - rx0) * b / 10;
					lk.g.fill(tx, ry0 + 2, tx + 1, ry0 + 6, Palette.argb(0x80, Palette.PAPER_TOP));
				}
			} else if (r.teks() != null) {
				Brush.text(lk.g, lk.font, r.teks(), rx0, ry0, r.warna(), 1.0F, false);
			}
		}, null, null);
	}

	/** Builds the scroll's rows for {@code lebar}. */
	private void isi(Lukis l, CompoundTag d, int best, int unlocked, int lebar) {
		rows.clear();
		teks(l, Component.translatable(K + "ui.best", best), Palette.INK, lebar);
		rows.add(new Row(null, null, 0, true));
		teks(l, Component.translatable(K + "ui.gates", unlocked, unlocked * 10 - 9), Palette.INK_SOFT, lebar);
		int luka = d.getIntOr("luka", 0);
		if (luka > 0) {
			teks(l, Component.translatable(K + "ui.luka", (luka + 59) / 60), Palette.DANGER, lebar);
		}
		if (d.getBooleanOr("penakluk", false)) {
			teks(l, Component.translatable(K + "ui.title_conqueror").withStyle(ChatFormatting.BOLD), Palette.GOLD_DARK, lebar);
		}
		if (!d.getBooleanOr("tower", true)) {
			teks(l, Component.translatable(K + "closed"), Palette.LOCKED, lebar);
		}
		rows.add(new Row(Component.translatable(K + "ui.pattern"), null, 0, false));
		teks(l, Component.translatable(K + "ui.pattern.text"), Palette.INK_SOFT, lebar);
		rows.add(new Row(Component.translatable(K + "ui.week"), null, 0, false));
		int rank = 0;
		for (Tag t : d.getListOrEmpty("weekly")) {
			if (t instanceof StringTag entry) {
				String value = entry.value();
				int bar = value.lastIndexOf('|');
				String name = bar < 0 ? value : value.substring(0, bar);
				String floor = bar < 0 ? "?" : value.substring(bar + 1);
				rank++;
				teks(l, Component.translatable(K + "stele.row", rank, name, floor), rank == 1 ? Palette.GOLD_DARK : Palette.INK, lebar);
			}
		}
		if (rank == 0) {
			teks(l, Component.translatable(K + "stele.empty"), Palette.INK_FAINT, lebar);
		}
		String bestName = d.getStringOr("best_name", "");
		if (!bestName.isEmpty()) {
			teks(l, Component.translatable(K + "stele.best", bestName, d.getIntOr("best_floor", 0)), Palette.STORED, lebar);
		}
		rows.add(new Row(Component.translatable(K + "ui.records"), null, 0, false));
		int ruins = d.getIntOr("ruins", 0);
		int ruinBest = Mth.clamp(d.getIntOr("ruin_best", 0), 0, 3);
		teks(l, ruins == 0 ? Component.translatable(K + "ui.ruins_none")
				: Component.translatable(K + "ui.ruins", ruins, Component.translatable("kultivator.reruntuhan.gua.tier." + Math.max(1, ruinBest))),
				Palette.INK, lebar);
		teks(l, Component.translatable(K + "ui.pahala", d.getIntOr("pahala", 0)), Palette.INK, lebar);
		int broken = d.getIntOr("prisons_broken", 0);
		teks(l, Component.translatable(K + "ui.prisons", d.getIntOr("prisons", 0), broken), broken > 0 ? Palette.DANGER : Palette.INK, lebar);
		teks(l, Component.translatable(K + "source").withStyle(ChatFormatting.ITALIC), Palette.INK_FAINT, lebar);
	}

	private void teks(Lukis l, Component text, int warna, int lebar) {
		for (FormattedCharSequence line : l.font.split(text, Math.max(20, lebar))) {
			rows.add(new Row(null, line, warna, false));
		}
	}

	/** The ten bands as the tiers of a pagoda, the widest at the bottom; lit up to the best floor; a tier's tooltip names its band. */
	private static void pagoda(Lukis l, int best, int unlocked, int left, int top0, int right, int bottom0) {
		float mid = (left + right) / 2.0F;
		float top = top0 + 8.0F;
		float bottom = bottom0 - 5.0F;
		float tier = (bottom - top) / 10.0F;
		float widest = (right - left) / 2.0F - 4.0F;
		float pulse = l.denyut();
		Brush.rect(l.g, mid - 0.8F, top - 6.0F, mid + 0.8F, top + 2.0F, Palette.GOLD);
		Brush.disc(l.g, mid, top - 6.0F, 2.0F, Palette.GOLD_LIGHT);
		for (int b = 10; b >= 1; b--) {
			int i = 10 - b;
			float y = top + i * tier;
			float half = widest * (0.3F + 0.07F * b);
			boolean cleared = best >= b * 10;
			boolean open = b <= unlocked;
			int rgb = LobbyState.bandRgb(b);
			int roof = cleared ? Palette.opaque(rgb) : open ? Palette.mix(Palette.opaque(rgb), Palette.INK_FAINT, 0.55F) : Palette.LOCKED;
			int wall = cleared ? Palette.ROD_LIGHT : open ? Palette.argb(0xC0, Palette.ROD_LIGHT) : Palette.LOCKED_PAPER;
			Brush.rect(l.g, mid - half * 0.62F, y + tier * 0.45F, mid + half * 0.62F, y + tier - 1.0F, wall);
			Brush.rect(l.g, mid - half, y + tier * 0.3F, mid + half, y + tier * 0.45F, roof);
			Brush.line(l.g, mid - half, y + tier * 0.3F, mid - half - 3.0F, y + tier * 0.12F, 1.2F, roof);
			Brush.line(l.g, mid + half, y + tier * 0.3F, mid + half + 3.0F, y + tier * 0.12F, 1.2F, roof);
			Brush.rect(l.g, mid - half * 0.7F, y + tier * 0.18F, mid + half * 0.7F, y + tier * 0.3F, Palette.mix(roof, 0xFF000000, 0.2F));
			if (b == unlocked && !cleared) {
				Brush.halo(l.g, mid, y + tier * 0.6F, half * 0.5F, 4.0F, rgb, 0.35F + 0.35F * pulse);
			}
			if (tier >= 11.0F) {
				Brush.centered(l.g, l.font, Component.literal(LobbyState.NUMERALS[b]), mid, y + tier * 0.45F, cleared ? 0xFF1B1008 : Palette.INK, 1.0F, false);
			}
			if (l.di(Math.round(mid - half), Math.round(y), Math.round(mid + half), Math.round(y + tier))) {
				Component guardian = Component.translatable("entity.kultivator.penjaga_lantai." + (b == 10 ? "bayangan_diri" : PenjagaLantaiEntity.IDS[b - 1]));
				l.tip(List.of(Component.translatable(K + "ui.band", LobbyState.NUMERALS[b], guardian),
						Component.translatable(cleared ? "kultivator.gui43.m13.menara.tingkat.lulus" : open ? "kultivator.gui43.m13.menara.tingkat.buka"
								: "kultivator.gui43.m13.menara.tingkat.tutup", b * 10 - 9, b * 10)));
			}
		}
		Brush.rect(l.g, left + 4.0F, bottom, right - 4.0F, bottom + 3.0F, Palette.ROD_DARK);
	}
}
