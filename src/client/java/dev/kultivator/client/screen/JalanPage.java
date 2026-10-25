package dev.kultivator.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.goals.GoalsClient;
import dev.kultivator.client.goals.GuideTracker;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.goals.DailyChores;
import dev.kultivator.goals.GoalCounters;
import dev.kultivator.goals.Guide;
import dev.kultivator.goals.RealmTrials;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Diri › Jalan (DESIGN 19.3.9, 25.8, 26.9, 43.5): sub <b>Ujian</b> (the open Foundation Trial set with progress bars, the
 * optional fourth row and its bonus) and sub <b>Harian</b> (today's chores, the streak and when the next set comes). The
 * Langkah chapters moved to Panduan › Kitab › Jalan Kultivasi; they are drawn by {@link Langkah}, the same class mounted
 * there (43.12.2: one class, two mounts, no tab twice). Everything is read from the synced {@code extra.goals}; the server
 * owns every rule. Text at 1.0 in one {@link Gulir} scroll (43.0.5).
 */
public class JalanPage extends Page {

	public static final String SUB_UJIAN = "ujian";
	public static final String SUB_HARIAN = "harian";
	private static final String P = "kultivator.goals.page.";
	private static final String M = "kultivator.gui43.m17.";
	private static final int[] TRIAL_COLORS = {0xFF2E8963, 0xFF8A671B, 0xFF6E160F, 0xFF3A5E9A};
	private static final int OK = 0xFF2E8963;
	/** The countdown of Harian changes with time: rebuild the lines at most once a second. */
	private static final long SEGAR_MS = 1000L;

	private String sub = SUB_UJIAN;
	private final Gulir gulir = new Gulir();
	private boolean kotor = true;
	private int lebarIsi = -1;
	private long dibangun;

	JalanPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.diri.jalan");
	}

	@Override
	public int glyph() {
		return Glyphs.JALAN;
	}

	@Override
	public String helpTopic() {
		return SUB_HARIAN.equals(sub) ? "harian" : "jalan";
	}

	@Override
	public List<Sub> subs() {
		return List.of(new Sub(SUB_UJIAN, Component.translatable(P + "section.trials")), new Sub(SUB_HARIAN, Component.translatable(P + "section.daily")));
	}

	@Override
	public void showSub(String id) {
		String next = SUB_HARIAN.equals(id) ? SUB_HARIAN : SUB_UJIAN;
		if (!next.equals(sub)) {
			sub = next;
			gulir.keAtas();
		}
		kotor = true;
	}

	@Override
	protected void build() {
		kotor = true;
	}

	@Override
	public void onData() {
		kotor = true;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!mauTombol() && gulir.tombol(event, fokusRing.kunci(), true)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void lukis(Lukis l) {
		int lebar = w - Gulir.RUANG_BATANG;
		if (kotor || lebar != lebarIsi || SUB_HARIAN.equals(sub) && l.kini - dibangun > SEGAR_MS) {
			kotor = false;
			lebarIsi = lebar;
			dibangun = l.kini;
			gulir.kosongkan();
			if (SUB_HARIAN.equals(sub)) {
				harian(data(), lebar);
			} else {
				ujian(data(), lebar);
			}
		}
		gulir.gambar(l, "jalan", x, y, x + w, y + h);
	}

	// ================================================================== Ujian

	private void ujian(CultivationData data, int lebar) {
		CompoundTag trial = RealmTrials.view(data);
		String key = trial.getStringOr("target", "");
		RealmTrials.Trial[] set = key.isEmpty() ? null : RealmTrials.full(key);
		if (set == null) {
			gulir.kepala(Component.translatable(P + "trials.title_none"));
			gulir.bungkus(font, Component.translatable(P + "trials.none"), 2, lebar, Palette.INK_SOFT);
			gulir.sumber(font, Component.translatable(P + "trials.source"), lebar);
			return;
		}
		gulir.kepala(Component.translatable(P + "trials.title", RealmTrials.targetName(key)));
		int[] prog = trial.getIntArray("prog").orElse(new int[0]);
		int done = trial.getIntOr("done", 0);
		for (int i = 0; i < set.length; i++) {
			RealmTrials.Trial each = set[i];
			boolean ok = (done & (1 << i)) != 0;
			int have = i * 2 < prog.length ? prog[i * 2] : 0;
			int goal = Math.max(1, each.goal());
			String amount = each.kind().minutes() ? have / 60 + "/" + goal / 60 : have + "/" + goal;
			gulir.kemajuan(Component.literal(ok ? "✓" : String.valueOf(i + 1)), ok ? OK : TRIAL_COLORS[i % TRIAL_COLORS.length], RealmTrials.describe(each),
					Component.literal(amount), Math.min(1.0F, have / (float) goal), ok);
		}
		Rank target = RealmTrials.rankOf(key);
		double bonus = target == null ? 0.0D : RealmTrials.bonus(data, target);
		gulir.bungkus(font, Component.translatable(P + "trials.bonus", String.format(Locale.ROOT, "%+.2f", bonus)), 2, lebar,
				bonus > 0.0D ? Palette.GOLD_DARK : Palette.INK_SOFT);
		if (RealmTrials.isFull(done)) {
			gulir.bungkus(font, Brush.bold(Component.translatable(P + "trials.full")), 2, lebar, Palette.SEAL_DARK);
		}
		gulir.bungkus(font, Component.translatable(set.length > 3 ? "kultivator.jalan8.page.trials.rule4" : P + "trials.rule",
				String.format(Locale.ROOT, "%.2f", RealmTrials.QUALITY_PER_TRIAL), String.format(Locale.ROOT, "%.2f", RealmTrials.FULL_BONUS),
				String.format(Locale.ROOT, "%.1f", RealmTrials.FULL_STRIKE_FACTOR)), 2, lebar, Palette.INK_FAINT);
		gulir.sumber(font, Component.translatable(P + "trials.source"), lebar);
	}

	// ================================================================== Harian

	private void harian(CultivationData data, int lebar) {
		CompoundTag daily = DailyChores.view(data);
		int streak = daily.getIntOr("streak", 0);
		gulir.kepala(Component.translatable(P + "daily.title", streak, Math.round((DailyChores.streakFactor(streak) - 1.0D) * 100.0D)));
		if (!GoalsClient.cfg("daily", true)) {
			gulir.bungkus(font, Component.translatable(P + "daily.off"), 2, lebar, Palette.INK_SOFT);
			gulir.sumber(font, Component.translatable(P + "daily.source"), lebar);
			return;
		}
		ListTag chores = daily.getListOrEmpty("chores");
		int done = daily.getIntOr("done", 0);
		if (chores.isEmpty()) {
			gulir.bungkus(font, Component.translatable(P + "daily.wait"), 2, lebar, Palette.INK_SOFT);
		}
		for (int i = 0; i < chores.size(); i++) {
			CompoundTag entry = chores.getCompoundOrEmpty(i);
			DailyChores.Chore chore = DailyChores.Chore.byId(entry.getStringOr("id", ""));
			if (chore == null) {
				continue;
			}
			boolean ok = (done & (1 << i)) != 0;
			int need = Math.max(1, entry.getIntOr("need", 1));
			int have = Math.min(need, entry.getIntOr("have", 0));
			String amount = chore.seconds() ? have / 60 + "/" + need / 60 : have + "/" + need;
			gulir.kemajuan(Component.literal(ok ? "✓" : "○"), ok ? OK : Palette.SEAL, DailyChores.name(chore, need), Component.literal(amount),
					have / (float) need, ok);
		}
		gulir.bungkus(font, Component.translatable(P + "daily.reward", Math.round(DailyChores.REWARD_SHARE * 100.0D)), 2, lebar, Palette.GOLD_DARK);
		if (daily.getBooleanOr("full", false)) {
			gulir.bungkus(font, Brush.bold(Component.translatable(P + "daily.all_done")), 2, lebar, Palette.SEAL_DARK);
		}
		// When the next set may come: a new day and 18,000 ticks played since the last refresh.
		long dayLeft = DailyChores.DAY_TICKS - Math.floorMod(ClientCultivation.serverGameTime(), DailyChores.DAY_TICKS);
		int played = GoalCounters.value(data, GoalCounters.PLAYED) - daily.getIntOr("last", 0);
		int playLeft = Math.max(0, DailyChores.MIN_PLAYED - played);
		Component next = playLeft > 0 ? Component.translatable(P + "daily.next_play", minutes(dayLeft), minutes(playLeft))
				: Component.translatable(P + "daily.next", minutes(dayLeft));
		gulir.bungkus(font, next, 2, lebar, Palette.INK_FAINT);
		gulir.sumber(font, Component.translatable(P + "daily.source"), lebar);
	}

	private static Component minutes(long ticks) {
		long minutes = Math.max(1L, (ticks + 1199L) / 1200L);
		return Component.translatable(P + "minutes", minutes);
	}

	// ================================================================== Gulir: one scroll of text lines

	/**
	 * One scroll of variable content on the kit's {@link DaftarGulir} (43.7, G12): text lines of {@link #GARIS} px (list
	 * headings, never selected) and rows of {@link #BARIS} px (selectable with ↑↓; Enter runs the row's action). Text is
	 * wrapped once when the content is built (the page rebuilds it when the width or the data changes) and drawn at 1.0.
	 * Shared by Diri › Jalan, Panduan › Kitab and Panduan › Ensiklopedia.
	 */
	public static final class Gulir {

		public static final int GARIS = 10;
		public static final int BARIS = 15;
		/** Width the scroll bar takes from the right of the box (wrap text at {@code w - RUANG_BATANG}). */
		public static final int RUANG_BATANG = 8;

		private final DaftarGulir daftar = new DaftarGulir();
		private final List<DaftarGulir.Baris> isi = new ArrayList<>();
		private final List<Runnable> aksi = new ArrayList<>();
		private final BitSet garis = new BitSet();
		private int tunda = -1;
		private String kunci = "";
		private int tinggi = 100;

		public Gulir() {
			daftar.judul(garis::get, GARIS);
		}

		public void kosongkan() {
			isi.clear();
			aksi.clear();
			garis.clear();
		}

		/** Back to the top with the entry cascade (new sub, new chapter). */
		public void keAtas() {
			daftar.mulaiUlang(Util.getMillis());
			tunda = -1;
		}

		/** The number of entries so far (the index the next entry gets). */
		public int jumlah() {
			return isi.size();
		}

		/** Scrolls entry {@code i} to the top on the next frame. */
		public void ke(int i) {
			tunda = i;
		}

		/** A text line (10 px). */
		public void garis(DaftarGulir.Baris baris) {
			garis.set(isi.size());
			isi.add(baris);
			aksi.add(null);
		}

		/** A row (15 px); {@code jalan} runs on Enter while the row is selected (null: none). */
		public void baris(DaftarGulir.Baris baris, @Nullable Runnable jalan) {
			isi.add(baris);
			aksi.add(jalan);
		}

		/** Wraps entry {@code i}'s painter (a card edge, a mark on a first line): {@code f} gets the old painter. */
		public void bungkusUlang(int i, UnaryOperator<DaftarGulir.Baris> f) {
			if (i >= 0 && i < isi.size()) {
				isi.set(i, f.apply(isi.get(i)));
			}
		}

		/** A line cut to the width ("…" and tooltip when longer). */
		public void teks(Component t, int indent, int warna) {
			garis((l, i, x0, y0, x1, y1, sorot, pilih) -> l.teks(t, x0 + indent, y0, x1 - x0 - indent, warna));
		}

		/** {@code t} wrapped into lines {@code lebar} wide (minus {@code indent}). Returns the index of the first line. */
		public int bungkus(Font font, Component t, int indent, int lebar, int warna) {
			int first = isi.size();
			for (FormattedCharSequence row : font.split(t, Math.max(10, lebar - indent))) {
				garis((l, i, x0, y0, x1, y1, sorot, pilih) -> Brush.text(l.g, l.font, row, x0 + indent, y0, warna, 1.0F, false));
			}
			return first;
		}

		/** The source note at the end: faint italics. */
		public void sumber(Font font, Component t, int lebar) {
			bungkus(font, t.copy().withStyle(ChatFormatting.ITALIC), 2, lebar, Palette.INK_FAINT);
		}

		/** A heading line: a small seal, the title in the paper's accent and a gold rule under it. */
		public void kepala(Component t) {
			garis((l, i, x0, y0, x1, y1, sorot, pilih) -> {
				Brush.rect(l.g, x0, y0 + 2.0F, x0 + 4.0F, y0 + 6.0F, Palette.SEAL);
				l.teks(Brush.bold(t), x0 + 6, y0, x1 - x0 - 6, l.gaya.aksen);
				l.g.fill(x0, y1, x1, y1 + 1, Palette.argb(0xB0, Palette.GOLD));
			});
		}

		/** A progress row: a mark, the name (cut), the amount on the right and a bar under them. */
		public void kemajuan(Component tanda, int warnaTanda, Component nama, Component jumlah, float bagian, boolean selesai) {
			baris((l, i, x0, y0, x1, y1, sorot, pilih) -> {
				l.teks(tanda, x0 + 2, y0, 10, warnaTanda);
				int jw = l.font.width(jumlah);
				l.teks(nama, x0 + 13, y0, x1 - x0 - 13 - jw - 6, selesai ? l.gaya.teksLemah : l.gaya.teks);
				l.kanan(jumlah, x1 - 2, y0, selesai ? OK : l.gaya.teksLemah);
				Bilah.gambar(l, x0 + 13, y0 + 10, x1 - 2, bagian, selesai ? Palette.JADE : 0, null);
			}, null);
		}

		/** A talisman row: one click (or Enter while selected) runs {@code jalan}; disabled with {@code alasan}. */
		public void tombol(String kunci, Component label, boolean aktif, @Nullable Component alasan, Runnable jalan) {
			baris((l, i, x0, y0, x1, y1, sorot, pilih) -> {
				int bw = TalismanButton.lebar(l.font, label, 60, x1 - x0 - 4);
				TalismanButton.gambar(l, kunci, x0 + 2, y0 + 1, bw, 13, label, aktif, alasan, jalan);
			}, () -> {
				if (aktif) {
					jalan.run();
				}
			});
		}

		/**
		 * ↑↓ PgUp PgDn Home End scroll the text (actions are reached with Tab like every talisman) while this scroll has the
		 * focus ({@code fokus} = the page's focus key), or with nothing focused when {@code tanpaFokus}. True when used.
		 */
		public boolean tombol(KeyEvent event, @Nullable String fokus, boolean tanpaFokus) {
			if (fokus == null ? !tanpaFokus : !fokus.equals(kunci)) {
				return false;
			}
			float halaman = Math.max(1.0F, (tinggi - GARIS) / 18.0F);
			float langkah = switch (event.key()) {
				case InputConstants.KEY_UP -> GARIS / 18.0F;
				case InputConstants.KEY_DOWN -> -GARIS / 18.0F;
				case InputConstants.KEY_PAGEUP -> halaman;
				case InputConstants.KEY_PAGEDOWN -> -halaman;
				case InputConstants.KEY_HOME -> 100000.0F;
				case InputConstants.KEY_END -> -100000.0F;
				default -> 0.0F;
			};
			if (langkah == 0.0F) {
				return false;
			}
			daftar.roda(langkah);
			return true;
		}

		public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1) {
			this.kunci = kunci;
			this.tinggi = y1 - y0;
			daftar.gambar(l, kunci, x0, y0, x1, y1, isi.size(), BARIS, (lk, i, a, b, c, d, sorot, pilih) -> {
				if (pilih && aksi.get(i) != null) {
					lk.kartu(a, b, c, d, 0.0F, true);
				}
				isi.get(i).gambar(lk, i, a, b, c, d, sorot, pilih);
			}, this::pakai, null);
			if (tunda >= 0 && tunda < isi.size()) {
				// Two jumps after the first layout: to the end, then back up to the entry, so it lands at the top.
				daftar.setPilih(isi.size() - 1);
				daftar.setPilih(tunda);
				tunda = -1;
			}
		}

		private void pakai() {
			int p = daftar.pilihan();
			Runnable r = p >= 0 && p < aksi.size() ? aksi.get(p) : null;
			if (r != null) {
				r.run();
			}
		}
	}

	// ================================================================== Langkah (mounted by Panduan › Kitab)

	/**
	 * The Langkah chapters (DESIGN 19.3.9, 25.8.2, 26.9) as drawn in Panduan › Kitab › Jalan Kultivasi: the fifteen chapters
	 * in one list grouped by book (Langkah Pertama, Jalan Dunia, Jalan Abadi; no book coin), and the chosen chapter's four
	 * steps with their live "how" lines, the reward and the claim talisman. Narrow box: the list, a chapter opens as the
	 * detail (back with Esc or the header's back control); wider: list left, detail right.
	 */
	public static final class Langkah {

		private static final int BUKU = Guide.ALL_CHAPTERS / Guide.CHAPTERS;
		private static final int PER_BUKU = Guide.CHAPTERS + 1;
		private static final int ROW = 22;

		private final DaftarGulir bab = new DaftarGulir().judul(i -> i % PER_BUKU == 0, 12);
		private final Gulir langkah = new Gulir();
		private int chapter = -1;
		private boolean rinci;
		private boolean kotor = true;
		private int lebarIsi = -1;
		/** Bring the chosen chapter into view after the next layout of the list. */
		private boolean tampakkan;
		/** The layout of the last frame: one column (list or detail) below 320 px. */
		private boolean sempit = true;

		/** True while the narrow layout shows a chapter's detail (the host draws its back control). */
		public boolean rinci() {
			return sempit && rinci;
		}

		/** Esc: back from the detail to the list; false when already there (or when list and detail show side by side). */
		public boolean mundur() {
			if (!sempit || !rinci) {
				return false;
			}
			rinci = false;
			return true;
		}

		/** Scroll keys for the steps (see {@link Gulir#tombol}); with nothing focused only while the detail is alone. */
		public boolean tombol(KeyEvent event, @Nullable String fokus) {
			return langkah.tombol(event, fokus, sempit && rinci);
		}

		/** New player data: rebuild the step lines. */
		public void onData() {
			kotor = true;
		}

		/** Shown again: start at the chapter being worked on, as the detail (fewest clicks). */
		public void onShow() {
			pilih(Math.min(Guide.ALL_CHAPTERS - 1, Guide.currentChapterAny(data())));
			rinci = true;
		}

		private void pilih(int c) {
			int next = Math.max(0, Math.min(Guide.ALL_CHAPTERS - 1, c));
			if (next != chapter) {
				chapter = next;
				langkah.keAtas();
				kotor = true;
			}
			bab.setPilih(entri(next));
			tampakkan = true;
		}

		private static int entri(int chapter) {
			return chapter / Guide.CHAPTERS * PER_BUKU + chapter % Guide.CHAPTERS + 1;
		}

		private static int babDari(int entri) {
			return entri / PER_BUKU * Guide.CHAPTERS + entri % PER_BUKU - 1;
		}

		public void gambar(Lukis l, int x0, int y0, int x1, int y1) {
			if (chapter < 0) {
				onShow();
			}
			sempit = x1 - x0 < 320;
			if (sempit && rinci) {
				rinci(l, x0, y0, x1, y1);
				return;
			}
			if (sempit) {
				daftar(l, x0, y0, x1, y1, true);
				return;
			}
			int listW = Math.max(110, Math.min(150, Math.round((x1 - x0) * 0.36F)));
			daftar(l, x0, y0, x0 + listW, y1, false);
			l.g.fill(x0 + listW + 3, y0, x0 + listW + 4, y1, Palette.argb(0x90, Palette.GOLD));
			rinci(l, x0 + listW + 8, y0, x1, y1);
		}

		private void daftar(Lukis l, int x0, int y0, int x1, int y1, boolean sempit) {
			CultivationData data = data();
			int current = Guide.currentChapterAny(data);
			bab.gambar(l, "langkah.bab", x0, y0, x1, y1, BUKU * PER_BUKU, ROW, (lk, i, a, b, c, d, sorot, terpilih) -> {
				if (i % PER_BUKU == 0) {
					String key = i / PER_BUKU == 0 ? M + "buku.1" : i / PER_BUKU == 1 ? M + "buku.2" : M + "buku.3";
					lk.teks(Brush.bold(Component.translatable(key)), a + 2, b + 2, c - a - 4, lk.gaya.aksen);
					lk.g.fill(a, d - 1, c, d, Palette.argb(0x90, Palette.GOLD));
					return;
				}
				int index = babDari(i);
				baris(lk, data, index, a, b, c, d, sorot, index == chapter, index == current);
				lk.zona("langkah.bab#p" + i, a, b, c, d, () -> {
					pilih(index);
					rinci = sempit;
				}, true, null, false);
			}, () -> {
				pilih(babDari(bab.pilihan()));
				rinci = sempit;
			}, sempit ? null : e -> pilih(babDari(e)));
			if (tampakkan) {
				tampakkan = false;
				bab.setPilih(entri(chapter));
			}
		}

		/** One chapter: the seal with its number, the name, n/4 (✓ once claimed) and a bar. */
		private static void baris(Lukis l, CultivationData data, int index, int x0, int y0, int x1, int y1, boolean sorot, boolean chosen,
				boolean current) {
			boolean done = Guide.chapterDoneAny(data, index);
			boolean claimed = (Guide.claimed(data) & (1 << index)) != 0;
			l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, chosen);
			if (current) {
				Brush.frame(l.g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, l.denyut()));
			}
			int seal = 13;
			int sx = x0 + 4;
			int sy = y0 + (y1 - y0 - seal) / 2;
			l.g.fill(sx, sy, sx + seal, sy + seal, done ? Palette.SEAL : Palette.grey(Palette.SEAL, 0.6F));
			Component number = Component.literal(String.valueOf(index + 1));
			Brush.text(l.g, l.font, number, sx + (seal - l.font.width(number)) / 2.0F + 0.5F, sy + 3.0F, 0xFFF1D57E, 1.0F, false);
			Component count = Component.literal((claimed ? "✓ " : "") + Guide.doneInAny(data, index) + "/" + Guide.PER_CHAPTER);
			int tx = sx + seal + 4;
			int cw = l.font.width(count);
			l.teks(Component.translatable("kultivator.goals.chapter." + (index + 1)), tx, y0 + 2, x1 - tx - cw - 6, l.gaya.teks);
			l.kanan(count, x1 - 3, y0 + 2, done ? OK : l.gaya.teksLemah);
			Bilah.gambar(l, tx, y0 + 13, x1 - 4, Guide.doneInAny(data, index) / (float) Guide.PER_CHAPTER, done ? Palette.JADE : 0, null);
		}

		/** The chosen chapter: heading and total, the steps in one scroll, the reward and the claim talisman. */
		private void rinci(Lukis l, int x0, int y0, int x1, int y1) {
			CultivationData data = data();
			Component chapterName = Component.translatable(P + "chapter", chapter + 1, Component.translatable("kultivator.goals.chapter." + (chapter + 1)));
			Component total = Component.translatable(P + "total", Guide.totalDone(data), Guide.ALL_STEPS);
			int totalW = l.font.width(total);
			Brush.rect(l.g, x0, y0 + 3.0F, x0 + 4.0F, y0 + 7.0F, Palette.SEAL);
			l.teks(Brush.bold(chapterName), x0 + 6, y0 + 1, x1 - x0 - totalW - 12, l.gaya.aksen);
			l.kanan(total, x1, y0 + 1, l.gaya.teksSamar);
			l.g.fill(x0, y0 + 11, x1, y0 + 12, Palette.argb(0xB0, Palette.GOLD));
			// the claim talisman and the reward along the bottom
			boolean claimed = (Guide.claimed(data) & (1 << chapter)) != 0;
			boolean on = GoalsClient.cfg("guide", true);
			boolean ready = Guide.chapterDoneAny(data, chapter);
			Component label = Component.translatable(claimed ? P + "claimed" : P + "claim");
			int bw = TalismanButton.lebar(l.font, label, 70, Math.max(70, (x1 - x0) / 2));
			int by = y1 - 14;
			Component why = claimed ? Component.translatable(P + "claimed") : !on ? Component.translatable(P + "claim.off")
					: !ready ? Component.translatable(M + "langkah.belum") : null;
			boolean aktif = why == null && Keys.connected();
			int c = chapter;
			TalismanButton.gambar(l, "langkah.ambil", x1 - bw, by, bw, 14, label, aktif, why, () -> send(Actions.JALAN, 0, "claim:" + (c + 1)), aktif,
					false);
			Component reward = Component.translatable(P + "reward", Component.translatable("kultivator.goals.reward." + (chapter + 1)));
			l.teks(reward, x0, by + 3, x1 - x0 - bw - 6, Palette.GOLD_DARK);
			if (l.di(x1 - bw, by, x1, by + 14)) {
				l.tip(List.of(label, Component.translatable("kultivator.goals.reward." + (chapter + 1)).withStyle(ChatFormatting.GOLD),
						Component.translatable(on ? P + "claim.tip" : P + "claim.off").withStyle(ChatFormatting.GRAY)));
			}
			int lebar = x1 - x0 - Gulir.RUANG_BATANG;
			if (kotor || lebar != lebarIsi) {
				kotor = false;
				lebarIsi = lebar;
				isi(data, l.font, lebar);
			}
			langkah.gambar(l, "langkah.isi", x0, y0 + 15, x1, by - 3);
		}

		private void isi(CultivationData data, Font font, int lebar) {
			langkah.kosongkan();
			if (chapter >= Guide.CHAPTERS && chapter < Guide.ABADI_FIRST) {
				// Jalan Dunia: the realm the chapter is meant for, and the cosmetic title once chapter 10 was claimed.
				langkah.bungkus(font, Component.translatable("kultivator.jalan8.page.realm", Guide.CHAPTER_REALM[chapter - Guide.CHAPTERS].displayName()), 0,
						lebar, Palette.INK_FAINT);
				if (chapter == Guide.WORLD_CHAPTER && Guide.worldTitle(data)) {
					langkah.bungkus(font, Brush.bold(Component.translatable("kultivator.jalan8.page.title")), 0, lebar, Palette.SEAL_DARK);
				}
			} else if (chapter >= Guide.ABADI_FIRST) {
				// Jalan Abadi: meant for Dewa Abadi Puncak, and the title "Leluhur Abadi" once chapter 15 was claimed.
				langkah.bungkus(font, Component.translatable("kultivator.jalan9.page.realm"), 0, lebar, Palette.INK_FAINT);
				if (chapter == Guide.ALL_CHAPTERS - 1 && Guide.abadiTitle(data)) {
					langkah.bungkus(font, Brush.bold(Component.translatable("kultivator.jalan9.page.title")), 0, lebar, Palette.SEAL_DARK);
				}
			}
			int current = Guide.currentStepAny(data);
			for (int s = 0; s < Guide.PER_CHAPTER; s++) {
				int step = chapter * Guide.PER_CHAPTER + s;
				boolean done = Guide.stepDone(data, step);
				boolean now = step == current;
				Component name = Component.translatable("kultivator.goals.step." + Guide.step(step).id());
				int first = langkah.bungkus(font, done ? name : Brush.bold(name), 11, lebar, done ? Palette.INK_SOFT : Palette.INK);
				// the mark on the first line of the step: ✓ done, a pulsing ◆ for the step being worked on, · otherwise
				langkah.bungkusUlang(first, text -> (l, i, a, b, c, d, sorot, pilih) -> {
					int mark = done ? OK : now ? Palette.mix(Palette.GOLD_DARK, Palette.SEAL, l.denyut()) : Palette.INK_FAINT;
					Brush.text(l.g, l.font, Component.literal(done ? "✓" : now ? "◆" : "·"), a + 1, b, mark, 1.0F, false);
					text.gambar(l, i, a, b, c, d, sorot, pilih);
				});
				if (!done) {
					Component how = step < Guide.STEP_COUNT ? GuideTracker.how(step, data)
							: KeyHints.fill(Component.translatable("kultivator.goals.step." + Guide.step(step).id() + ".how"));
					langkah.bungkus(font, how, 11, lebar, Palette.INK_SOFT);
				}
			}
			// The tracker's on/off coin lives only in Pengaturan › HUD (DESIGN 33.6 R5); this talisman leads there.
			langkah.tombol("langkah.pelacak", Component.translatable(Guide.hidden(data) ? "kultivator.uxpages.jalan.tracker_off"
					: "kultivator.uxpages.jalan.tracker"), true, null, () -> HubNav.openSettings(HubNav.GROUP_HUD));
		}
	}
}
