package dev.kultivator.client.sectevent;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one bracket of Kegiatan › Arena (DESIGN 20.3.12, 20.3.13, 36.2.3; the sect tournament, the Hundred Dynasties War and
 * module T's live view share it): one heading per round (Babak 1 … Semifinal, Final), each bout a card of two bamboo
 * slips laid in a grid that scrolls, so names stay at text 1.0 at any GUI scale. The winner's slip is bright with a red
 * seal, the loser's faded, the bout being fought pulses; visitors (north-bank rivals, rival champions) are written in
 * soft ink; the bout for third place closes the list. Bouts not drawn yet read "?".
 */
final class BracketView {

	/** Kinds as in {@code Bracket}: 0 the master's disciple, 1 rival, 2 champion, 3 youth. */
	private static final int RIVAL = 1;
	private static final int CHAMPION = 2;
	private static final String K = "kultivator.gui43.m09.";

	private record Sel(@Nullable Component judul, @Nullable CompoundTag laga) {
	}

	private final DaftarGulir daftar = new DaftarGulir();

	void gambar(Lukis l, String kunci, CompoundTag bracket, int x0, int y0, int x1, int y1) {
		ListTag rounds = bracket.getListOrEmpty("rounds");
		int first = rounds.isEmpty() || !(rounds.get(0) instanceof CompoundTag r0) ? 1 : Math.max(1, r0.getListOrEmpty("m").size());
		int size = Math.max(2, bracket.getIntOr("size", first * 2));
		int total = Math.max(1, Integer.numberOfTrailingZeros(Integer.highestOneBit(size)));
		List<Sel> sel = new ArrayList<>();
		for (int r = 0; r < total; r++) {
			int left = total - r;
			sel.add(new Sel(left == 1 ? Component.translatable(K + "final") : left == 2 ? Component.translatable(K + "semifinal")
					: Component.translatable(K + "babak", r + 1), null));
			ListTag played = r < rounds.size() && rounds.get(r) instanceof CompoundTag wrapper ? wrapper.getListOrEmpty("m") : new ListTag();
			int matches = Math.max(1, (size / 2) >> r);
			for (int m = 0; m < Math.max(matches, played.size()); m++) {
				sel.add(new Sel(null, m < played.size() && played.get(m) instanceof CompoundTag tag ? tag : new CompoundTag()));
			}
		}
		if (bracket.get("bronze") instanceof CompoundTag bronze) {
			sel.add(new Sel(Component.translatable("kultivator.sectevent.ui.bronze"), null));
			sel.add(new Sel(null, bronze));
		}
		daftar.judul(i -> i < sel.size() && sel.get(i).judul() != null, 13);
		daftar.gambarKisi(l, kunci, x0, y0, x1, y1, sel.size(), 130, 24, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Sel s = sel.get(i);
			if (s.judul() != null) {
				lk.kepala(s.judul(), rx0, ry0, rx1);
			} else if (s.laga() != null) {
				laga(lk, s.laga(), rx0, ry0 + 1, rx1, ry1 - 1);
			}
		}, null, null);
	}

	private static void laga(Lukis l, CompoundTag match, int x0, int y0, int x1, int y1) {
		int mid = (y0 + y1) / 2;
		if (!match.contains("a") && !match.contains("b")) {
			l.g.fill(x0, y0, x1, y1, Palette.argb(0x40, Palette.BAMBOO_ALT));
			Brush.frame(l.g, x0, y0, x1, y1, Palette.argb(0x60, Palette.BAMBOO_KNOT));
			l.tengah(Component.literal("?"), (x0 + x1) / 2.0F, mid - 4.0F, x1 - x0 - 4, Palette.INK_FAINT);
			return;
		}
		int winner = match.getIntOr("w", -1);
		if (match.getBooleanOr("now", false)) {
			Brush.frame(l.g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.argb(Math.round(90 + 120 * l.denyut()), Palette.SEAL_GLOW));
		}
		half(l, match, "a", match.getIntOr("ak", 0), winner == 0, winner == 1, x0, y0, x1, mid);
		half(l, match, "b", match.getIntOr("bk", 0), winner == 1, winner == 0, x0, mid, x1, y1);
	}

	private static void half(Lukis l, CompoundTag match, String key, int kind, boolean won, boolean lost, int x0, int y0, int x1, int y1) {
		l.g.fill(x0, y0, x1, y1 - 1, won ? Palette.BAMBOO : lost ? Palette.argb(0x70, Palette.BAMBOO_ALT) : Palette.BAMBOO_ALT);
		l.g.fill(x0, y0, x0 + 2, y1 - 1, Palette.BAMBOO_KNOT);
		if (won) {
			l.g.fill(x1 - 5, y0 + 1, x1 - 1, y1 - 2, Palette.SEAL);
		}
		Component name = SectEventClient.name(match, key);
		int color = lost ? Palette.INK_FAINT : kind == RIVAL || kind == CHAMPION ? Palette.INK_SOFT : Palette.INK;
		l.teks(won ? Brush.bold(name) : name, x0 + 4, y0 + (y1 - y0 - 9) / 2.0F + 0.5F, x1 - x0 - (won ? 11 : 6), color);
	}
}
