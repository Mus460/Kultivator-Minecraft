package dev.kultivator.client.sekte.mandiri;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.disciple.DiscipleScreen;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Rank;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Jalan Hidup Murid", the hub card of section MURID (DESIGN 39.7.4): one jade scroll over the whole hall with four seals
 * along its top:
 * <ul>
 * <li><b>Murid</b>: every disciple (loaded, travelling, or last seen), with its path seal, name, what it does or where it
 * is ("Tutup Diri hari 2/3", "on a shopping trip, home in about 0.3 days"), realm, priority-1 goal with a thin progress
 * bar and purse; red flags for wounded, gold for ready to break through, violet for tempted; filters for travelling,
 * wounded, ready and tempted; a click opens the disciple scroll on its Jalan Hidup tab when the body is near, else its
 * diary book (the "short card", read from the last mirror).</li>
 * <li><b>Kabar</b>: the hall's last 30 events, newest first, important ones in seal red.</li>
 * <li><b>Kebijakan Aula</b>: the hall policy editor (mode, caution, pocket money, permissions, notices, errand money,
 * rank approval, waiting days, careful breakthroughs, feud stance).</li>
 * <li><b>Kas</b>: pocket money paid in the last seven days, the hall's contribution pool and the money book (40 lines).</li>
 * </ul>
 * Data from {@code mandiri_papan} ({@code PapanMandiri.bangun}), asked for when the scroll opens and after every press.
 */
public final class MandiriBoardScreen extends Screen {

	private static final String L = JalanHidupTab.L;
	private static final int MAX_W = 470;
	private static final int MAX_H = 280;
	private static final int ROW_H = 20;
	private static final long UNROLL_MILLIS = 250L;

	private static final int F_PERGI = 1;
	private static final int F_LUKA = 2;
	private static final int F_SIAP = 4;
	private static final int F_TERGODA = 8;
	private static final int F_TUTUP = 16;
	private static final int F_PERINTAH = 32;
	private static final int F_DIMUAT = 64;
	private static final int F_JAUH = 128;

	private static @Nullable CompoundTag terbaru;
	private static long versi;

	private final Sentuh sentuh = new Sentuh();
	private long openedAt;
	private int tampilan;
	private int saring;
	private float gulir;
	private float gulirMaks;
	private int x0;
	private int x1;
	private int top;
	private int bottom;

	private MandiriBoardScreen() {
		super(Component.translatable(L + "papan.judul"));
	}

	/** Opens the board (from the hub card) and asks for its data. */
	static void buka() {
		MandiriClient.aksi("papan", "", "", 0);
		HubNav.openChild(new MandiriBoardScreen(), HubNav.MURID);
	}

	static void terima(CompoundTag data) {
		terbaru = data;
		versi++;
		Minecraft minecraft = Minecraft.getInstance();
		if (data.getBooleanOr("buka", false) && minecraft.player != null && !(minecraft.gui.screen() instanceof MandiriBoardScreen)) {
			// the chat [Lihat] link or the command: open the board over the game
			minecraft.gui.setScreen(new MandiriBoardScreen());
		}
	}

	static void bersih() {
		terbaru = null;
		versi++;
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			UiSound.open();
		}
		int w = Math.min(MAX_W, width - 16);
		int h = Math.min(MAX_H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		top = (height - h) / 2;
		bottom = top + h;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		sentuh.mulai();
		float open = Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
		int mid = (top + bottom) / 2;
		int half = Math.round((bottom - top) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, top, bottom);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		int cx0 = x0 + ScrollFrame.SIDE + 8;
		int cx1 = x1 - ScrollFrame.SIDE - 8;
		int cy = top + ScrollFrame.END + 5;
		Ikon.hiasan(g, Ikon.SEGEL, cx0, cy - 2, 16, 16, 0xFFFFFFFF);
		Brush.fit(g, font, Brush.bold(title), cx0 + 19.0F, cy + 1.0F, 150.0F, Palette.SEAL_DARK, 1.0F, 0.6F, false);
		CompoundTag data = terbaru;
		if (data != null) {
			Component day = Component.translatable(L + "papan.hari", data.getLongOr("hari", 0L) + 1, data.getIntOr("jam", 0));
			Brush.fit(g, font, day, cx0 + 19.0F, cy + 11.0F, 150.0F, Palette.INK_SOFT, 0.55F, 0.4F, false);
		}
		drawSegel(g, mouseX, mouseY, cx0 + 175.0F, cy, cx1 - 16.0F);
		// the "?" to the encyclopedia topic of DESIGN 39 (Kitab Panduan > Ensiklopedia)
		boolean helpOver = sentuh.di(mouseX, mouseY, cx1 - 13.0F, cy, cx1, cy + 15.0F);
		Brush.disc(g, cx1 - 6.5F, cy + 7.5F, 6.5F, helpOver ? Palette.GOLD : Palette.argb(0xC0, Palette.GOLD_DARK));
		Brush.fitCentered(g, font, Brush.bold(Component.literal("?")), cx1 - 6.5F, cy + 3.5F, 10.0F, 0xFFFFF4E0, 0.9F, 0.6F, false);
		sentuh.tambah(cx1 - 13.0F, cy, cx1, cy + 15.0F, () -> HubNav.help("mandiri"),
				() -> List.of(Component.translatable("kultivator.screen.help.mandiri.title").withStyle(ChatFormatting.GOLD)));
		Brush.rect(g, cx0, cy + 20, cx1, cy + 20.6F, Palette.argb(0xB0, Palette.GOLD));
		int listTop = cy + 24;
		int listBottom = bottom - ScrollFrame.END - 4;
		Component empty = null;
		if (data == null) {
			empty = Component.translatable(L + "papan.memuat");
		} else if (!data.getBooleanOr("on", false)) {
			empty = Component.translatable(L + "tab.mati");
		} else if (!data.getBooleanOr("hall", false)) {
			empty = Component.translatable(L + "papan.tanpa_aula");
		}
		if (empty != null) {
			Brush.wrap(g, font, empty, cx0 + 4.0F, listTop + 8.0F, cx1 - cx0 - 8.0F, Palette.INK_SOFT, 0.8F, false);
		} else {
			switch (tampilan) {
				case 1 -> drawKabar(g, data, cx0, listTop, cx1, listBottom);
				case 2 -> drawKebijakan(g, data, cx0, listTop, cx1, listBottom, mouseX, mouseY);
				case 3 -> drawKas(g, data, cx0, listTop, cx1, listBottom);
				default -> drawMurid(g, data, cx0, listTop, cx1, listBottom, mouseX, mouseY);
			}
		}
		List<Component> tip = sentuh.tip(mouseX, mouseY);
		if (tip != null) {
			g.setTooltipForNextFrame(font, tip, java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	private void drawSegel(GuiGraphicsExtractor g, int mouseX, int mouseY, float sx, float sy, float limit) {
		String[] names = {"murid", "kabar", "kebijakan", "kas"};
		float sw = (limit - sx) / 4.0F;
		for (int i = 0; i < 4; i++) {
			int index = i;
			float a0 = sx + i * sw;
			float a1 = a0 + sw - 3.0F;
			boolean on = tampilan == i;
			boolean over = sentuh.di(mouseX, mouseY, a0, sy, a1, sy + 15);
			Brush.rect(g, a0, sy, a1, sy + 15.0F, on ? Palette.argb(0xE0, Palette.SEAL) : over ? Palette.argb(0x60, Palette.SEAL)
					: Palette.argb(0x30, Palette.PAPER_DEEP));
			Brush.frame(g, Math.round(a0), Math.round(sy), Math.round(a1), Math.round(sy + 15.0F), on ? Palette.SEAL_DARK : Palette.GOLD_DARK);
			Brush.fitCentered(g, font, Component.translatable(L + "papan.tampil." + names[i]), (a0 + a1) / 2.0F, sy + 4.0F, sw - 8.0F,
					on ? 0xFFFFF4E0 : Palette.INK, 0.7F, 0.45F, false);
			sentuh.tambah(a0, sy, a1, sy + 15.0F, () -> {
				if (tampilan != index) {
					tampilan = index;
					gulir = 0.0F;
					UiSound.tab();
				}
			}, () -> List.of(Component.translatable(L + "papan.tampil." + names[index] + ".tip")));
		}
	}

	// ================================================================== disciples

	private void drawMurid(GuiGraphicsExtractor g, CompoundTag data, int cx0, int listTop, int cx1, int listBottom, int mouseX, int mouseY) {
		// filters
		String[] filters = {"semua", "pergi", "luka", "siap", "tergoda"};
		float fx = cx0;
		ListTag rows = data.getListOrEmpty("rows");
		for (int i = 0; i < filters.length; i++) {
			int index = i;
			int count = 0;
			for (int r = 0; r < rows.size(); r++) {
				if (lolos(rows.getCompoundOrEmpty(r).getIntOr("f", 0), i)) {
					count++;
				}
			}
			Component label = Component.translatable(L + "papan.saring." + filters[i], count);
			float fw = Math.min(90.0F, font.width(label) * 0.62F + 12.0F);
			boolean on = saring == i;
			boolean over = sentuh.di(mouseX, mouseY, fx, listTop, fx + fw, listTop + 11);
			Brush.rect(g, fx, listTop, fx + fw, listTop + 11.0F, on ? Palette.argb(0xD0, Palette.JADE) : over ? Palette.argb(0x50, Palette.JADE)
					: Palette.argb(0x30, Palette.PAPER_DEEP));
			Brush.frame(g, Math.round(fx), listTop, Math.round(fx + fw), listTop + 11, on ? Palette.JADE_DARK : Palette.argb(0x90, Palette.GOLD_DARK));
			Brush.fitCentered(g, font, label, fx + fw / 2.0F, listTop + 2.0F, fw - 4.0F, on ? 0xFFFFFFFF : Palette.INK, 0.62F, 0.4F, false);
			sentuh.tambah(fx, listTop, fx + fw, listTop + 11.0F, () -> {
				saring = index;
				gulir = 0.0F;
			}, null);
			fx += fw + 3.0F;
		}
		// header
		int hy = listTop + 15;
		float colName = cx0 + 22.0F;
		float colAct = cx0 + (cx1 - cx0) * 0.30F;
		float colRealm = cx0 + (cx1 - cx0) * 0.58F;
		float colGoal = cx0 + (cx1 - cx0) * 0.72F;
		float colPurse = cx0 + (cx1 - cx0) * 0.89F;
		int head = Palette.SEAL_DARK;
		Brush.fit(g, font, Component.translatable(L + "papan.kol.nama"), colName, hy, colAct - colName - 4, head, 0.6F, 0.4F, false);
		Brush.fit(g, font, Component.translatable(L + "papan.kol.kegiatan"), colAct, hy, colRealm - colAct - 4, head, 0.6F, 0.4F, false);
		Brush.fit(g, font, Component.translatable(L + "papan.kol.ranah"), colRealm, hy, colGoal - colRealm - 4, head, 0.6F, 0.4F, false);
		Brush.fit(g, font, Component.translatable(L + "papan.kol.cita"), colGoal, hy, colPurse - colGoal - 4, head, 0.6F, 0.4F, false);
		Brush.fit(g, font, Component.translatable(L + "papan.kol.dompet"), colPurse, hy, cx1 - colPurse, head, 0.6F, 0.4F, false);
		Brush.rect(g, cx0, hy + 8, cx1, hy + 8.5F, Palette.argb(0x80, Palette.GOLD_DARK));
		int bodyTop = hy + 10;
		List<CompoundTag> shown = new ArrayList<>();
		for (int r = 0; r < rows.size(); r++) {
			CompoundTag row = rows.getCompoundOrEmpty(r);
			if (lolos(row.getIntOr("f", 0), saring)) {
				shown.add(row);
			}
		}
		shown.sort((p, q) -> Integer.compare(urut(q), urut(p)));
		if (shown.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(rows.isEmpty() ? L + "papan.kosong" : L + "papan.saring_kosong"), cx0 + 4.0F, bodyTop + 6.0F,
					cx1 - cx0 - 8.0F, Palette.INK_SOFT, 0.7F, false);
			return;
		}
		gulirMaks = Math.max(0.0F, shown.size() * ROW_H - (listBottom - bodyTop));
		gulir = Math.min(gulir, gulirMaks);
		g.enableScissor(cx0 - 2, bodyTop, cx1 + 2, listBottom);
		float ry = bodyTop - gulir;
		for (CompoundTag row : shown) {
			if (ry + ROW_H >= bodyTop && ry < listBottom) {
				drawBaris(g, row, cx0, ry, cx1, colName, colAct, colRealm, colGoal, colPurse, mouseX, mouseY, bodyTop, listBottom);
			}
			ry += ROW_H;
		}
		g.disableScissor();
		if (gulirMaks > 0.0F) {
			float railH = listBottom - bodyTop;
			float thumbH = Math.max(10.0F, railH * railH / (railH + gulirMaks));
			float ty = bodyTop + (railH - thumbH) * (gulir / gulirMaks);
			Brush.rect(g, cx1 + 3.0F, bodyTop, cx1 + 4.5F, listBottom, Palette.argb(0x40, Palette.INK_FAINT));
			Brush.rect(g, cx1 + 3.0F, ty, cx1 + 4.5F, ty + thumbH, Palette.argb(0xC0, Palette.GOLD_DARK));
		}
	}

	/** Troubled and travelling first, then by realm. */
	private static int urut(CompoundTag row) {
		int f = row.getIntOr("f", 0);
		int score = row.getIntOr("r", 0);
		if ((f & F_TERGODA) != 0) {
			score += 4000;
		}
		if ((f & F_LUKA) != 0) {
			score += 3000;
		}
		if ((f & F_SIAP) != 0) {
			score += 2000;
		}
		if ((f & F_PERGI) != 0) {
			score += 1000;
		}
		return score;
	}

	private static boolean lolos(int f, int filter) {
		return switch (filter) {
			case 1 -> (f & F_PERGI) != 0;
			case 2 -> (f & F_LUKA) != 0;
			case 3 -> (f & F_SIAP) != 0;
			case 4 -> (f & F_TERGODA) != 0;
			default -> true;
		};
	}

	private void drawBaris(GuiGraphicsExtractor g, CompoundTag row, int cx0, float ry, int cx1, float colName, float colAct, float colRealm,
			float colGoal, float colPurse, int mouseX, int mouseY, int clipTop, int clipBottom) {
		int f = row.getIntOr("f", 0);
		boolean over = mouseY >= clipTop && mouseY < clipBottom && sentuh.di(mouseX, mouseY, cx0, ry, cx1, ry + ROW_H - 1);
		if (over) {
			g.fill(cx0 - 1, Math.round(ry), cx1 + 1, Math.round(ry + ROW_H - 1), Palette.argb(0x40, Palette.PAPER_DEEP));
		}
		int stripe = (f & F_TERGODA) != 0 ? JalanHidup.IBLIS.warna() : (f & F_LUKA) != 0 ? Palette.DANGER : (f & F_SIAP) != 0 ? Palette.GOLD
				: (f & F_PERGI) != 0 ? Palette.QI : Palette.JADE_DULL;
		g.fill(cx0 - 1, Math.round(ry + 1), cx0 + 1, Math.round(ry + ROW_H - 2), Palette.opaque(stripe));
		JalanHidup jalan = JalanHidup.byId(row.getStringOr("j", ""));
		if (jalan != null) {
			Ikon.jalan(g, jalan, cx0 + 3, Math.round(ry + 1), 17, (f & F_DIMUAT) != 0 || (f & F_PERGI) != 0 ? 0xFF : 0xA0);
		}
		Component name = row.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		boolean last = (f & (F_DIMUAT | F_PERGI)) == 0;
		Brush.fit(g, font, last ? name.copy().withStyle(ChatFormatting.ITALIC) : Brush.bold(name), colName, ry + 2.0F, colAct - colName - 4.0F,
				Palette.INK, 0.72F, 0.45F, false);
		Component path = jalan == null ? Component.translatable(L + "tab.jalan_belum") : Component.translatable(jalan.kunciNama());
		Brush.fit(g, font, path, colName, ry + 11.0F, colAct - colName - 4.0F, jalan == null ? Palette.INK_FAINT : Palette.onPaper(jalan.warna()), 0.52F,
				0.36F, false);
		// activity / place
		Component act = kegiatan(row);
		Kegiatan k = Kegiatan.byId(row.getStringOr("k", ""));
		float ax = colAct;
		if ((f & F_PERGI) == 0 && k != null && k != Kegiatan.KOSONG) {
			Ikon.kegiatan(g, k, Math.round(colAct), Math.round(ry + 2.0F), 9, 0xFF);
			ax += 11.0F;
		}
		Brush.fit(g, font, act, ax, ry + 3.0F, colRealm - ax - 4.0F, Palette.INK, 0.6F, 0.38F, false);
		Component flags = bendera(row);
		Brush.fit(g, font, flags, colAct, ry + 12.0F, colRealm - colAct - 4.0F, Palette.SEAL_DARK, 0.5F, 0.34F, false);
		// realm
		int step = row.getIntOr("r", 0);
		Component realm = Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, step))).displayName();
		Brush.fit(g, font, realm, colRealm, ry + 6.0F, colGoal - colRealm - 4.0F, Palette.INK_SOFT, 0.56F, 0.36F, false);
		// goal
		JenisCita cita = JenisCita.byId(row.getStringOr("c", ""));
		if (cita != null) {
			Brush.fit(g, font, Component.translatable(cita.kunciNama()), colGoal, ry + 3.0F, colPurse - colGoal - 4.0F, Palette.INK, 0.56F, 0.36F, false);
			int prog = Math.max(0, Math.min(100, row.getIntOr("cp", 0)));
			float gx1 = colPurse - 6.0F;
			Brush.rect(g, colGoal, ry + 12.0F, gx1, ry + 14.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
			Brush.rect(g, colGoal, ry + 12.0F, colGoal + (gx1 - colGoal) * prog / 100.0F, ry + 14.0F, Palette.JADE);
		} else {
			Brush.fit(g, font, Component.literal("-"), colGoal, ry + 6.0F, 20.0F, Palette.INK_FAINT, 0.56F, 0.36F, false);
		}
		Brush.fit(g, font, NilaiBatu.text(row.getLongOr("d", 0L)), colPurse, ry + 6.0F, cx1 - colPurse, Palette.GOLD_DARK, 0.56F, 0.36F, false);
		String soul = row.getStringOr("soul", "");
		int eid = row.getIntOr("eid", -1);
		sentuh.tambah(cx0, Math.max(clipTop, ry), cx1, Math.min(clipBottom, ry + ROW_H - 1), () -> bukaMurid(soul, eid), () -> {
			List<Component> lines = new ArrayList<>();
			lines.add(name.copy().withStyle(ChatFormatting.GOLD));
			lines.add(act);
			if (last) {
				lines.add(Component.translatable(L + "papan.terakhir_dilihat").withStyle(ChatFormatting.GRAY));
			}
			lines.add(Component.translatable(eid >= 0 ? L + "papan.klik_buka" : L + "papan.klik_buku").withStyle(ChatFormatting.DARK_GRAY));
			return lines;
		});
	}

	/** What a row does or where it is. */
	private static Component kegiatan(CompoundTag row) {
		int f = row.getIntOr("f", 0);
		if ((f & F_PERGI) != 0) {
			String jj = row.getStringOr("jj", "");
			long ret = row.getLongOr("ret", 0L);
			Component kind = !jj.isEmpty() ? Component.translatable("kultivator.sekte.mandiri.inti.jenis_jalan." + jj)
					: Component.translatable(alasanPergi(row.getStringOr("ar", "")));
			if (ret > 0L) {
				return Component.translatable(L + "papan.pergi_pulang", kind, String.format(Locale.ROOT, "%.1f", ret / 24000.0F).replace('.', ','));
			}
			return Component.translatable(L + "papan.pergi", kind);
		}
		if (row.contains("th")) {
			return Component.translatable(L + "papan.tutup", row.getIntOr("th", 1), row.getIntOr("tt", 1));
		}
		Kegiatan k = Kegiatan.byId(row.getStringOr("k", ""));
		Component base = k == null || k == Kegiatan.KOSONG ? Component.translatable(L + "tab.sedang_kosong") : Component.translatable(k.kunciNama());
		if ((f & F_DIMUAT) == 0) {
			return Component.translatable(L + "papan.terakhir", base);
		}
		if ((f & F_JAUH) != 0) {
			return Component.translatable(L + "papan.jauh", base);
		}
		return base;
	}

	private static String alasanPergi(String reason) {
		String key = L + "papan.alasan." + (reason == null || reason.isEmpty() ? "other" : reason);
		return Language.getInstance().has(key) ? key : L + "papan.alasan.other";
	}

	private static Component bendera(CompoundTag row) {
		int f = row.getIntOr("f", 0);
		var out = Component.empty();
		if ((f & F_LUKA) != 0) {
			JenisLuka luka = JenisLuka.byId(row.getStringOr("l", ""));
			out.append(Component.translatable(luka == JenisLuka.TIDAK ? JenisLuka.TERLUKA.kunciNama() : luka.kunciNama()).withColor(Palette.DANGER))
					.append(Component.literal(" "));
		}
		if ((f & F_SIAP) != 0) {
			out.append(Component.translatable(L + "papan.siap").withColor(Palette.GOLD_DARK)).append(Component.literal(" "));
		}
		if ((f & F_TERGODA) != 0) {
			out.append(Component.translatable(L + "papan.tergoda").withColor(JalanHidup.IBLIS.warna())).append(Component.literal(" "));
		}
		if ((f & F_PERINTAH) != 0) {
			out.append(Component.translatable(L + "papan.perintah").withColor(Palette.INK_SOFT));
		}
		return out;
	}

	private void bukaMurid(String soul, int eid) {
		if (eid >= 0) {
			DiscipleScreen.bukaTab(Glyphs.JALAN);
			Keys.send(new Payloads.Action(Actions.DISCIPLE_OPEN, eid, ""));
		} else {
			BukuHarianScreen.buka(soul, this);
		}
	}

	// ================================================================== news

	private void drawKabar(GuiGraphicsExtractor g, CompoundTag data, int cx0, int listTop, int cx1, int listBottom) {
		ListTag news = data.getListOrEmpty("kabar");
		if (news.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(L + "papan.kabar_kosong"), cx0 + 4.0F, listTop + 6.0F, cx1 - cx0 - 8.0F, Palette.INK_SOFT, 0.7F,
					false);
			return;
		}
		g.enableScissor(cx0 - 2, listTop, cx1 + 2, listBottom);
		float cy = listTop + 2.0F - gulir;
		for (int i = 0; i < news.size(); i++) {
			CompoundTag n = news.getCompoundOrEmpty(i);
			boolean important = n.getBooleanOr("p", false);
			Component when = Component.translatable(L + "papan.kabar_waktu", n.getLongOr("h", 0L) + 1, String.format("%02d", n.getIntOr("j", 0)));
			Brush.fit(g, font, when, cx0, cy, 70.0F, Palette.INK_FAINT, 0.55F, 0.38F, false);
			if (important) {
				Brush.disc(g, cx0 + 74.0F, cy + 3.0F, 2.0F, Palette.SEAL);
			}
			Component text = n.read("t", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			cy += Math.max(9.0F, Brush.wrap(g, font, text, cx0 + 80.0F, cy, cx1 - cx0 - 82.0F, important ? Palette.SEAL_DARK : Palette.INK, 0.62F, false))
					+ 3.0F;
			Brush.rect(g, cx0 + 80.0F, cy - 1.5F, cx1, cy - 1.0F, Palette.argb(0x28, Palette.INK_SOFT));
		}
		g.disableScissor();
		gulirMaks = Math.max(0.0F, cy + gulir - listBottom + 4.0F);
	}

	// ================================================================== hall policy

	private void drawKebijakan(GuiGraphicsExtractor g, CompoundTag data, int cx0, int listTop, int cx1, int listBottom, int mouseX, int mouseY) {
		boolean own = data.getBooleanOr("own", false);
		Kebijakan kb = Kebijakan.fromTag(data.getCompoundOrEmpty("kb"), Kebijakan.bawaan(ModeMandiri.SEIMBANG, 2, 3));
		float half = (cx1 - cx0) / 2.0F;
		float lx = cx0;
		float lw = half - 8.0F;
		float cy = listTop + 2.0F;
		cy = baris(g, lx, cy, lw, L + "tab.kb.mode");
		for (int i = 0; i < ModeMandiri.values().length; i++) {
			ModeMandiri m = ModeMandiri.values()[i];
			chip(g, mouseX, mouseY, lx + i * lw / 3.0F, cy, lw / 3.0F - 3.0F, Component.translatable(m.kunciNama()), kb.mode() == m,
					own ? () -> MandiriClient.aksi("aula_mode", "", m.id(), 0) : null, L + "mode.desc." + m.id());
		}
		cy += 15.0F;
		cy = baris(g, lx, cy, lw, L + "tab.kb.risiko");
		for (int i = 0; i < Kehatian.values().length; i++) {
			Kehatian k = Kehatian.values()[i];
			chip(g, mouseX, mouseY, lx + i * lw / 4.0F, cy, lw / 4.0F - 3.0F, Component.translatable(k.kunciNama()), kb.risiko() == k,
					own ? () -> MandiriClient.aksi("aula_risiko", "", k.id(), 0) : null, L + "kehatian.desc." + k.id());
		}
		cy += 15.0F;
		cy = angka(g, mouseX, mouseY, lx, cy, lw, Component.translatable(L + "papan.kb.saku", kb.uangSakuPersen()), own, kb.uangSakuPersen(), 0, 200, 25,
				n -> MandiriClient.aksi("aula_saku", "", "", n), L + "papan.kb.saku.tip");
		cy = angka(g, mouseX, mouseY, lx, cy, lw, Component.translatable(L + "papan.kb.titipan", NilaiBatu.text(kb.titipanNbMinggu())), own,
				kb.titipanNbMinggu(), 0, 5000, 100, n -> MandiriClient.aksi("aula_titipan", "", "", n), L + "papan.kb.titipan.tip");
		cy = angka(g, mouseX, mouseY, lx, cy, lw, Component.translatable(L + "papan.kb.tunggu_tembus", kb.tungguTerobosanHari()), own,
				kb.tungguTerobosanHari(), 0, 30, 1, n -> MandiriClient.aksi("aula_tunggu", "", "terobosan", n), L + "papan.kb.tunggu_tembus.tip");
		cy = angka(g, mouseX, mouseY, lx, cy, lw, Component.translatable(L + "papan.kb.tunggu_dao", kb.tungguDaoHari()), own, kb.tungguDaoHari(), 0, 30,
				1, n -> MandiriClient.aksi("aula_tunggu", "", "dao", n), L + "papan.kb.tunggu_dao.tip");
		cy += 2.0F;
		cy = baris(g, lx, cy, lw, L + "papan.kb.jenjang");
		Kebijakan.Jenjang[] ranks = Kebijakan.Jenjang.values();
		for (int i = 0; i < ranks.length; i++) {
			Kebijakan.Jenjang j = ranks[i];
			String id = j.name().toLowerCase(Locale.ROOT);
			chip(g, mouseX, mouseY, lx + i * lw / 3.0F, cy, lw / 3.0F - 3.0F, Component.translatable(L + "papan.kb.jenjang." + id), kb.jenjang() == j,
					own ? () -> MandiriClient.aksi("aula_jenjang", "", j.name(), 0) : null, L + "papan.kb.jenjang." + id + ".tip");
		}
		cy += 15.0F;
		cy = baris(g, lx, cy, lw, L + "papan.kb.seteru");
		Kebijakan.SikapSeteru[] feud = Kebijakan.SikapSeteru.values();
		for (int i = 0; i < feud.length; i++) {
			Kebijakan.SikapSeteru s = feud[i];
			String id = s.name().toLowerCase(Locale.ROOT);
			chip(g, mouseX, mouseY, lx + i * lw / 3.0F, cy, lw / 3.0F - 3.0F, Component.translatable(L + "papan.kb.seteru." + id), kb.seteru() == s,
					own ? () -> MandiriClient.aksi("aula_seteru", "", s.name(), 0) : null, L + "papan.kb.seteru." + id + ".tip");
		}
		cy += 15.0F;
		boolean careful = kb.hatiHatiTerobosan();
		centang(g, mouseX, mouseY, lx, cy, lw, Component.translatable(L + "papan.kb.hati"), careful,
				own ? () -> MandiriClient.aksi("aula_hati", "", "", careful ? 0 : 1) : null, L + "papan.kb.hati.tip");

		// right half: permissions and notices
		float rx = cx0 + half + 4.0F;
		float rw = half - 4.0F;
		float ry = listTop + 2.0F;
		ry = baris(g, rx, ry, rw, L + "tab.kb.izin");
		IzinKegiatan[] groups = IzinKegiatan.values();
		for (int i = 0; i < groups.length; i++) {
			IzinKegiatan group = groups[i];
			boolean on = group.boleh(kb.izin());
			float gx = rx + (i % 2) * rw / 2.0F;
			float gy = ry + (i / 2) * 10.0F;
			int mask = on ? kb.izin() & ~group.bit() : kb.izin() | group.bit();
			centang(g, mouseX, mouseY, gx, gy, rw / 2.0F - 2.0F, Component.translatable(group.kunciNama()), on,
					own ? () -> MandiriClient.aksi("aula_izin", "", "", mask) : null, L + "izin.desc." + group.id());
		}
		ry += ((groups.length + 1) / 2) * 10.0F + 4.0F;
		ry = baris(g, rx, ry, rw, L + "papan.kb.beritahu");
		Kebijakan.Pemberitahuan[] notices = Kebijakan.Pemberitahuan.values();
		for (int i = 0; i < notices.length; i++) {
			Kebijakan.Pemberitahuan n = notices[i];
			boolean on = kb.diberitahu(n);
			float gx = rx + (i % 2) * rw / 2.0F;
			float gy = ry + (i / 2) * 10.0F;
			if (gy > listBottom - 10) {
				break;
			}
			int mask = on ? kb.beritahu() & ~n.bit() : kb.beritahu() | n.bit();
			String id = n.name().toLowerCase(Locale.ROOT);
			centang(g, mouseX, mouseY, gx, gy, rw / 2.0F - 2.0F, Component.translatable(L + "beritahu." + id), on,
					own ? () -> MandiriClient.aksi("aula_beritahu", "", "", mask) : null, L + "beritahu." + id + ".tip");
		}
	}

	private float baris(GuiGraphicsExtractor g, float x, float y, float w, String key) {
		Brush.fit(g, font, Brush.bold(Component.translatable(key)), x, y, w, Palette.SEAL_DARK, 0.62F, 0.42F, false);
		return y + 9.0F;
	}

	private void chip(GuiGraphicsExtractor g, int mouseX, int mouseY, float cx, float cy, float cw, Component label, boolean on,
			@Nullable Runnable aksi, String tipKey) {
		boolean over = aksi != null && sentuh.di(mouseX, mouseY, cx, cy, cx + cw, cy + 11);
		Brush.rect(g, cx, cy, cx + cw, cy + 11.0F, on ? Palette.argb(0xE0, Palette.SEAL) : over ? Palette.argb(0x70, Palette.TALISMAN)
				: Palette.argb(0x50, Palette.TALISMAN_LOW));
		Brush.frame(g, Math.round(cx), Math.round(cy), Math.round(cx + cw), Math.round(cy + 11.0F), on ? Palette.SEAL_DARK : Palette.TALISMAN_EDGE);
		Brush.fitCentered(g, font, label, cx + cw / 2.0F, cy + 2.0F, cw - 4.0F, on ? 0xFFFFF4E0 : Palette.INK, 0.6F, 0.38F, false);
		sentuh.tambah(cx, cy, cx + cw, cy + 11.0F, on ? null : aksi, () -> List.of(label.copy().withStyle(ChatFormatting.GOLD),
				Component.translatable(tipKey)));
	}

	private void centang(GuiGraphicsExtractor g, int mouseX, int mouseY, float gx, float gy, float gw, Component label, boolean on,
			@Nullable Runnable aksi, String tipKey) {
		boolean over = aksi != null && sentuh.di(mouseX, mouseY, gx, gy, gx + gw, gy + 9);
		Brush.rect(g, gx + 1.0F, gy + 1.0F, gx + 8.0F, gy + 8.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
		Brush.frame(g, Math.round(gx + 1.0F), Math.round(gy + 1.0F), Math.round(gx + 8.0F), Math.round(gy + 8.0F), over ? Palette.GOLD : Palette.INK_SOFT);
		if (on) {
			Brush.line(g, gx + 2.0F, gy + 4.5F, gx + 4.0F, gy + 7.0F, 1.2F, Palette.JADE_DARK);
			Brush.line(g, gx + 4.0F, gy + 7.0F, gx + 7.5F, gy + 2.0F, 1.2F, Palette.JADE_DARK);
		}
		Brush.fit(g, font, label, gx + 10.0F, gy + 1.5F, gw - 11.0F, on ? Palette.INK : Palette.INK_FAINT, 0.54F, 0.36F, false);
		sentuh.tambah(gx, gy, gx + gw, gy + 9.0F, aksi, () -> List.of(label.copy().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY),
				Component.translatable(tipKey)));
	}

	private float angka(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, Component label, boolean own, int value, int min,
			int max, int step, java.util.function.IntConsumer send, String tipKey) {
		Brush.fit(g, font, label, x, y + 1.5F, w - 32.0F, Palette.INK, 0.58F, 0.38F, false);
		sentuh.tambah(x, y, x + w - 32.0F, y + 10.0F, null, () -> List.of(label.copy().withStyle(ChatFormatting.GOLD), Component.translatable(tipKey)));
		chip(g, mouseX, mouseY, x + w - 30.0F, y, 13.0F, Component.literal("-"), false, own && value > min ? () -> send.accept(Math.max(min, value - step))
				: null, tipKey);
		chip(g, mouseX, mouseY, x + w - 15.0F, y, 13.0F, Component.literal("+"), false, own && value < max ? () -> send.accept(Math.min(max, value + step))
				: null, tipKey);
		return y + 12.0F;
	}

	// ================================================================== money book

	private void drawKas(GuiGraphicsExtractor g, CompoundTag data, int cx0, int listTop, int cx1, int listBottom) {
		Ikon.hiasan(g, Ikon.KANTONG, cx0, listTop, 18, 18, 0xFFFFFFFF);
		Brush.fit(g, font, Component.translatable(L + "papan.kas.saku7", NilaiBatu.text(data.getLongOr("saku7", 0L))), cx0 + 22.0F, listTop + 1.0F,
				cx1 - cx0 - 22.0F, Palette.INK, 0.66F, 0.42F, false);
		Brush.fit(g, font, Component.translatable(L + "papan.kas.pool", data.getIntOr("pool", 0)), cx0 + 22.0F, listTop + 10.0F, cx1 - cx0 - 22.0F,
				Palette.INK_SOFT, 0.58F, 0.38F, false);
		ListTag book = data.getListOrEmpty("kas");
		int bodyTop = listTop + 22;
		Brush.rect(g, cx0, bodyTop - 2, cx1, bodyTop - 1.5F, Palette.argb(0x80, Palette.GOLD_DARK));
		if (book.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(L + "papan.kas.kosong"), cx0 + 4.0F, bodyTop + 4.0F, cx1 - cx0 - 8.0F, Palette.INK_SOFT, 0.7F, false);
			return;
		}
		g.enableScissor(cx0 - 2, bodyTop, cx1 + 2, listBottom);
		float cy = bodyTop + 2.0F - gulir;
		for (int i = 0; i < book.size(); i++) {
			CompoundTag b = book.getCompoundOrEmpty(i);
			long amount = b.getLongOr("j", 0L);
			Brush.fit(g, font, Component.translatable(L + "papan.kabar_hari", b.getLongOr("h", 0L) + 1), cx0, cy, 50.0F, Palette.INK_FAINT, 0.55F, 0.38F,
					false);
			Component who = b.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Brush.fit(g, font, who, cx0 + 52.0F, cy, (cx1 - cx0) * 0.32F, Palette.INK, 0.58F, 0.38F, false);
			String sebab = b.getStringOr("s", "");
			String key = L + "kas.sebab." + (sebab.indexOf(':') > 0 ? sebab.substring(0, sebab.indexOf(':')) : sebab);
			Component why = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(sebab);
			Brush.fit(g, font, why, cx0 + 56.0F + (cx1 - cx0) * 0.32F, cy, (cx1 - cx0) * 0.36F, Palette.INK_SOFT, 0.56F, 0.38F, false);
			Component sum = Component.literal(amount > 0 ? "+" : amount < 0 ? "-" : "").append(NilaiBatu.text(Math.abs(amount)));
			Brush.fit(g, font, sum, cx1 - 60.0F, cy, 60.0F, amount >= 0 ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.58F, 0.38F, false);
			cy += 9.0F;
		}
		g.disableScissor();
		gulirMaks = Math.max(0.0F, cy + gulir - listBottom + 4.0F);
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && sentuh.klik(event.x(), event.y())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (gulirMaks <= 0.0F) {
			return false;
		}
		gulir = Math.max(0.0F, Math.min(gulirMaks, gulir - (float) scrollY * 20.0F));
		return true;
	}

	@Override
	public void onClose() {
		UiSound.close();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Refreshes the open board now and then (every 5 s; the data is small). */
	@Override
	public void tick() {
		if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.tickCount % 100 == 0) {
			MandiriClient.aksi("papan", "", "", 0);
		}
	}
}
