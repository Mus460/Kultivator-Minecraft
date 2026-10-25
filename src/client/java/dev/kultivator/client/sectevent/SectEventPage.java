package dev.kultivator.client.sectevent;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.network.Actions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Shared helper of the Kegiatan subs Tugas, Misi and Arena (DESIGN 43.6: a {@link Page} subclass, not a second base): the
 * sect-activity details ({@link SectEventClient#data()}), the read-only rule of a sect mate's hall, the row of section
 * chips (43.0.4: the fourth level is a chip row, never a tab bar), a scrolling text pane at 1.0 ({@link #gulungan}) and
 * the roster as people for the kit's {@link PemilihOrang} (replaces {@code RosterView}). Pages draw immediately in
 * {@link #isi}; {@link KegiatanPage} mounts them as its subs.
 */
abstract class SectEventPage extends Page {

	protected static final int CHIP_H = 14;
	protected static final int BTN_H = 14;
	protected static final int GAP = 4;
	/** Kinds of a {@link Garis}: wrapped text, a stacked heading, a bamboo slip (one line). */
	protected static final int TEKS = 0;
	protected static final int JUDUL = 1;
	protected static final int BILAH = 2;
	private static final String K = "kultivator.gui43.m09.";

	/** One line of a {@link #gulungan} pane. */
	protected record Garis(Component teks, int warna, int jenis) {

		static Garis teks(Component teks, int warna) {
			return new Garis(teks, warna, TEKS);
		}

		static Garis judul(Component teks) {
			return new Garis(teks, 0, JUDUL);
		}

		static Garis bilah(Component teks, int warna) {
			return new Garis(teks, warna, BILAH);
		}
	}

	private record Baris(@Nullable Garis garis, @Nullable FormattedCharSequence teks, int warna, boolean terang) {
	}

	private final TabBar seksi = new TabBar();

	@Override
	protected void build() {
		// immediate page: everything is drawn in lukis()
	}

	/** The sect-activity details (never null). */
	protected static CompoundTag sekte() {
		return SectEventClient.data();
	}

	protected static boolean adaAula() {
		return sekte().getBooleanOr("hall", false);
	}

	/** True when this is a sect mate's hall, shown read-only (actions are refused by the server too). */
	protected static boolean readonly() {
		return sekte().getBooleanOr("readonly", false);
	}

	/** Why a write control is off in a sect mate's hall (null in one's own). */
	protected static @Nullable Component alasanBaca() {
		return readonly() ? Component.translatable(K + "baca_saja") : null;
	}

	/** Sends a sect-event action ({@code Actions.SECT_EVENT}); nothing from a sect mate's read-only scroll. */
	protected void act(int entityId, String text) {
		if (readonly()) {
			UiSound.denied();
			pesan(Component.translatable(K + "baca_saja"), Palette.DANGER);
			return;
		}
		send(Actions.SECT_EVENT, entityId, text);
	}

	// ================================================================== drawing

	@Override
	protected final void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected final void lukis(Lukis l) {
		if (!adaAula()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.sectevent.ui.no_hall"), null, null, x, y, x + w,
					y + h);
			return;
		}
		isi(l);
	}

	/** The page's content (only with a hall). */
	protected abstract void isi(Lukis l);

	/** The row of section chips at the top ({@code keys} lang keys; ids "0".."n"). Returns the y under it. */
	protected int seksi(Lukis l, String[] keys, int aktif, IntConsumer pilih) {
		List<TabBar.Tab> tabs = new ArrayList<>(keys.length);
		for (int i = 0; i < keys.length; i++) {
			tabs.add(TabBar.Tab.of(String.valueOf(i), Component.translatable(keys[i])));
		}
		seksi.setTabs(tabs);
		seksi.gambar(l, "seksi", x, y, x + w, y + CHIP_H, false, TabBar.Rupa.SUB, true, String.valueOf(aktif), id -> pilih.accept(Integer.parseInt(id)));
		return y + CHIP_H + 3;
	}

	/** A scrolling pane of text lines, headings and bamboo slips at 1.0 (never overflows at GUI scale 4). */
	protected void gulungan(Lukis l, String kunci, DaftarGulir daftar, List<Garis> isi, int x0, int y0, int x1, int y1) {
		if (x1 - x0 < 24 || y1 - y0 < 10) {
			return;
		}
		int lebar = x1 - x0 - 10;
		List<Baris> rows = new ArrayList<>();
		int slip = 0;
		for (Garis g : isi) {
			switch (g.jenis()) {
				case JUDUL -> rows.add(new Baris(g, null, 0, false));
				case BILAH -> rows.add(new Baris(g, null, g.warna(), slip++ % 2 == 0));
				default -> {
					for (FormattedCharSequence line : font.split(g.teks(), Math.max(20, lebar))) {
						rows.add(new Baris(null, line, g.warna(), false));
					}
				}
			}
		}
		daftar.judul(i -> i < rows.size() && rows.get(i).garis() != null && rows.get(i).garis().jenis() == JUDUL, 13);
		daftar.gambar(l, kunci, x0, y0, x1, y1, rows.size(), 10, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			Baris b = rows.get(i);
			if (b.garis() != null && b.garis().jenis() == JUDUL) {
				lk.kepala(b.garis().teks(), rx0, ry0, rx1);
			} else if (b.garis() != null) {
				lk.g.fill(rx0, ry0, rx1, ry1, b.terang() ? Palette.BAMBOO : Palette.BAMBOO_ALT);
				lk.g.fill(rx0, ry0, rx0 + 2, ry1, Palette.BAMBOO_KNOT);
				lk.g.fill(rx1 - 2, ry0, rx1, ry1, Palette.BAMBOO_KNOT);
				lk.teks(b.garis().teks(), rx0 + 4, ry0 + 1, rx1 - rx0 - 8, b.warna());
			} else if (b.teks() != null) {
				Brush.text(lk.g, lk.font, b.teks(), rx0 + 1, ry0, b.warna(), 1.0F, false);
			}
		}, null, null);
	}

	/** A heading over a column (seal, title, gold rule). Returns the y under it. */
	protected int kepala(Lukis l, Component judul, int x0, int y0, int x1) {
		return y0 + l.kepala(judul, x0, y0, x1);
	}

	// ================================================================== roster

	/**
	 * The master's disciples as people of the picker: {@code tampil} filters, {@code kunci} gives the reason a person
	 * cannot be chosen now (null = may), {@code tanda} the tag on the right. Id = soul. On a narrow page (the picker column
	 * under 180 px, GUI scale 4) the tag is left out so names keep their room; the page shows the chosen one's details.
	 */
	protected List<PemilihOrang.Orang> orang(Predicate<CompoundTag> tampil, Function<CompoundTag, Component> kunci,
			Function<CompoundTag, Component> tanda) {
		return orang(tampil, kunci, tanda, w >= 400);
	}

	/** {@link #orang(Predicate, Function, Function)} with the tag forced on or off (short tags such as a duty's). */
	protected static List<PemilihOrang.Orang> orang(Predicate<CompoundTag> tampil, Function<CompoundTag, Component> kunci,
			Function<CompoundTag, Component> tanda, boolean tag) {
		List<PemilihOrang.Orang> list = new ArrayList<>();
		for (CompoundTag e : SectEventClient.roster()) {
			if (!tampil.test(e)) {
				continue;
			}
			list.add(new PemilihOrang.Orang(e.getStringOr("soul", ""), SectEventClient.name(e, "name"), e.getIntOr("color", 0xCFE8FF), -1,
					List.of(rankName(e)), tag ? tanda.apply(e) : null, kunci.apply(e), null));
		}
		return list;
	}

	/** The roster entry of soul {@code id}, or null. */
	protected static @Nullable CompoundTag entri(String id) {
		for (CompoundTag e : SectEventClient.roster()) {
			if (e.getStringOr("soul", "").equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** The ticked roster entries of {@code pemilih} that are still there and allowed, in list order. */
	protected static List<CompoundTag> dipilih(PemilihOrang pemilih, List<PemilihOrang.Orang> semua) {
		List<CompoundTag> out = new ArrayList<>();
		for (PemilihOrang.Orang o : pemilih.pilihan(semua)) {
			CompoundTag e = entri(o.id());
			if (e != null) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * The chosen person of a single-choice picker (list mode, {@code new PemilihOrang(0)}: one click chooses, the
	 * highlighted row), or null when the list is empty or that person cannot go now.
	 */
	protected static @Nullable CompoundTag satu(PemilihOrang pemilih) {
		PemilihOrang.Orang o = pemilih.sorotan();
		return o == null || o.kunci() != null ? null : entri(o.id());
	}

	/** Entity ids of the ticked entries as "12,34". */
	protected static String ids(PemilihOrang pemilih, List<PemilihOrang.Orang> semua) {
		StringBuilder b = new StringBuilder();
		for (CompoundTag e : dipilih(pemilih, semua)) {
			b.append(b.isEmpty() ? "" : ",").append(e.getIntOr("id", 0));
		}
		return b.toString();
	}

	/** Busy guarding a formation or in the Void body (a mission cannot take them). */
	protected static boolean sibuk(CompoundTag e) {
		int act = e.getIntOr("act", 0);
		return act == DiscipleEntity.ACTIVITY_FORMATION_GUARD || act >= DiscipleEntity.ACTIVITY_VOID_BODY;
	}

	/** The usual reason a disciple cannot go now (far, away, busy, hurt), or null. */
	protected static @Nullable Component halangan(CompoundTag e) {
		if (!e.getBooleanOr("near", false)) {
			return Component.translatable("kultivator.sectevent.ui.far");
		}
		if (e.getBooleanOr("injured", false)) {
			return Component.translatable("kultivator.sectevent.ui.injured");
		}
		if (!e.getBooleanOr("free", false) || sibuk(e)) {
			int act = e.getIntOr("act", 0);
			if (act == DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
				return Component.translatable("kultivator.spine7c.keeper_duty");
			}
			if (act == DiscipleEntity.ACTIVITY_FORGE_ASSIST) {
				return Component.translatable("kultivator.spine8.forge_duty");
			}
			return Component.translatable(K + "sibuk");
		}
		return null;
	}

	// ================================================================== small helpers

	/** Current game time of the client level (close to the server's). */
	protected static long now() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0L : minecraft.level.getGameTime();
	}

	/** "1:05:20" or "4:12" for a number of ticks. */
	protected static String clock(long ticks) {
		long seconds = Math.max(0L, ticks / 20L);
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long rest = seconds % 60L;
		return hours > 0L ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
				: String.format(Locale.ROOT, "%d:%02d", minutes, rest);
	}

	/** "Golden Core, Late" for a roster entry. */
	protected static Component rankName(CompoundTag entry) {
		Rank rank = new Rank(Realm.byOrdinal(entry.getIntOr("realm", 0)), entry.getIntOr("layer", 0), entry.getIntOr("stage", 0));
		return rank.displayName().withColor(Palette.onPaper(rank.realm().color()));
	}

	/** The sect rank's name (0 Tercatat .. 5 Tetua Agung). */
	protected static Component sectRank(int rank) {
		return Component.translatable("kultivator.sectevent.ui.rank." + Math.max(0, Math.min(5, rank)));
	}

	protected static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}
}
