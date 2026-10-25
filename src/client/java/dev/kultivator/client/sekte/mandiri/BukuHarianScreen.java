package dev.kultivator.client.sekte.mandiri;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.disciple.MuridPage;
import dev.kultivator.client.disciple.MuridSub;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Buku Harian, the full diary of one disciple (DESIGN 39.8.1/39.8.2), now the sub Murid › Murid › Buku Harian on the wide
 * paper (43.5; was its own screen, and the short diary page of the Jalan Hidup tab, G42): an open stitched book with the
 * binding in the middle, a red ribbon bookmark, the disciple's name and path seal on the first page, then every kept entry
 * (up to 40, newest first; composed in the reader's language by {@link HarianTeks}) flowing from page to page, or, on the
 * second ribbon, the travel scrolls of its last journeys with their numbered stages. Under the book the master's
 * {@code [Tegur]} / {@code [Puji]} (once a day). The arrows, the wheel, A/D or PgUp/PgDn turn a spread (←/→ stay the
 * disciple picker's). Works for disciples whose body is not loaded (the server answers from its mirror; the page then
 * shows only this sub).
 */
public final class BukuHarianScreen extends MuridSub {

	private static final String L = JalanHidupTab.L;
	private static final float SKALA = 1.0F;
	private static final int BTN_H = 14;

	private int spread;
	private boolean gulungan;
	private long versiDilihat = -1L;
	private String soulDilihat = "";
	private final List<List<Blok>> halaman = new ArrayList<>();
	private final Sentuh sentuh = new Sentuh();
	private int bx0;
	private int bx1;
	private int by0;
	private int by1;

	/** One laid-out block (an entry or a scroll) and the page width it was measured for. */
	private record Blok(@Nullable HarianTeks.Tersusun entri, @Nullable Halaman.Gulungan gulungan, float tinggi) {
	}

	public BukuHarianScreen(MuridPage murid) {
		super(murid);
	}

	/** The disciple's name from its last diary page, or null when none arrived. */
	public static @Nullable Component nama(String soul) {
		Halaman p = Halaman.dari(MandiriClient.halaman(soul));
		return p == null ? null : p.nama;
	}

	@Override
	public Component title() {
		return Component.translatable(L + "buku.judul");
	}

	@Override
	public String helpTopic() {
		return "mandiri";
	}

	private String soul() {
		return murid.soul();
	}

	/** Tegur / Puji: the master's own disciple (unknown for a diary-only disciple: the server checks). */
	private boolean milik() {
		CompoundTag mandiri = info().getCompoundOrEmpty(MandiriIds.INFO);
		return mandiri.isEmpty() || mandiri.getBooleanOr("own", false);
	}

	// ================================================================== layout

	@Override
	protected void build() {
		bx0 = x;
		bx1 = x + w;
		by0 = y + 6;
		by1 = y + h - BTN_H - 3;
		versiDilihat = -1L;
		TalismanButton tegur = add(new TalismanButton(x, y + h - BTN_H, 70, BTN_H, () -> Component.translatable(L + "tab.tegur")));
		tegur.onPress(() -> MandiriClient.aksi("tegur", soul(), "", 0));
		tegur.enabledWhen(this::milik);
		tegur.tooltip(() -> tip(L + "tab.tegur", L + "tab.tegur.tip"));
		TalismanButton puji = add(new TalismanButton(x + 74, y + h - BTN_H, 70, BTN_H, () -> Component.translatable(L + "tab.puji")));
		puji.onPress(() -> MandiriClient.aksi("puji", soul(), "", 0));
		puji.enabledWhen(this::milik);
		puji.tooltip(() -> tip(L + "tab.puji", L + "tab.puji.tip"));
	}

	private static List<Component> tip(String head, String body) {
		return List.of(Component.translatable(head).withStyle(ChatFormatting.GOLD), Component.translatable(body),
				Component.translatable(L + "sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		if (!soul().isEmpty()) {
			MandiriClient.mintaHalaman(soul());
		}
	}

	private float lebarHalaman() {
		return (bx1 - bx0) / 2.0F - 26.0F;
	}

	private float tinggiHalaman() {
		return by1 - by0 - 34.0F;
	}

	private void susunHalaman(@Nullable Halaman p) {
		halaman.clear();
		if (p == null) {
			return;
		}
		float pw = lebarHalaman();
		float ph = tinggiHalaman();
		List<Blok> blocks = new ArrayList<>();
		if (gulungan) {
			for (Halaman.Gulungan s : p.gulungan) {
				float hgt = 20.0F;
				for (Component line : s.baris()) {
					hgt += Brush.wrapHeight(font, line, pw - 10.0F, SKALA) + 2.0F;
				}
				blocks.add(new Blok(null, s, hgt + 6.0F));
			}
		} else {
			for (HarianTeks.Tersusun e : p.entri) {
				blocks.add(new Blok(e, null, 12.0F + Brush.wrapHeight(font, e.isi(), pw - 16.0F, SKALA) + 7.0F));
			}
		}
		List<Blok> current = new ArrayList<>();
		// the first page keeps room for the title plate
		float used = 44.0F;
		for (Blok b : blocks) {
			if (!current.isEmpty() && used + b.tinggi() > ph) {
				halaman.add(current);
				current = new ArrayList<>();
				used = 0.0F;
			}
			current.add(b);
			used += b.tinggi();
		}
		halaman.add(current);
		spread = Math.max(0, Math.min(spread, (halaman.size() - 1) / 2));
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		sentuh.mulai();
		String soul = soul();
		Halaman p = soul.isEmpty() ? null : Halaman.dari(MandiriClient.halaman(soul));
		long v = MandiriClient.versi();
		if (v != versiDilihat || !soul.equals(soulDilihat)) {
			versiDilihat = v;
			soulDilihat = soul;
			susunHalaman(p);
		}
		int mid = (bx0 + bx1) / 2;
		// cover, pages, binding
		g.fill(bx0 - 2, by0 - 2, bx1 + 2, by1 + 2, Palette.ROD_DARK);
		g.fill(bx0 - 1, by0 - 1, bx1 + 1, by1 + 1, Palette.ROD);
		Ikon.kertas(g, bx0, by0, bx1, by1, 0xFFFFFFFF);
		g.fillGradient(mid - 10, by0, mid, by1, 0x00000000, 0x40000000);
		g.fillGradient(mid, by0, mid + 10, by1, 0x40000000, 0x00000000);
		g.fill(mid - 1, by0, mid + 1, by1, Palette.argb(0x90, Palette.ROD_DARK));
		for (int sy = by0 + 12; sy < by1 - 8; sy += 18) {
			g.fill(mid - 2, sy, mid + 2, sy + 2, Palette.argb(0xB0, Palette.CORD));
		}
		g.nextStratum();
		// ribbons: diary / travel scrolls
		ribbon(g, mouseX, mouseY, bx1 - 70, false);
		ribbon(g, mouseX, mouseY, bx1 - 44, true);
		int left = bx0 + 14;
		int right = mid + 12;
		int top = by0 + 12;
		if (p == null) {
			Brush.wrap(g, font, Component.translatable(L + "tab.memuat"), left, top + 20, lebarHalaman(), Palette.INK_SOFT, 1.0F, false);
			return;
		}
		int first = spread * 2;
		if (first == 0) {
			plat(g, p, left, top);
		}
		drawPage(g, first, left, first == 0 ? top + 44 : top, mouseX, mouseY);
		drawPage(g, first + 1, right, top, mouseX, mouseY);
		// folios and arrows
		int pages = Math.max(1, halaman.size());
		Brush.fitCentered(g, font, Component.literal(Integer.toString(first + 1)), (bx0 + mid) / 2.0F, by1 - 12, 24, Palette.INK_FAINT, 1.0F, 1.0F, false);
		if (first + 1 < pages) {
			Brush.fitCentered(g, font, Component.literal(Integer.toString(first + 2)), (mid + bx1) / 2.0F, by1 - 12, 24, Palette.INK_FAINT, 1.0F, 1.0F,
					false);
		}
		panah(g, mouseX, mouseY, bx0 + 1, spread > 0, -1);
		panah(g, mouseX, mouseY, bx1 - 15, first + 2 < pages, 1);
	}

	/** The ribbons and page arrows join the Tab ring (Enter switches diary / scrolls, turns a spread; 43.0.7). */
	@Override
	protected void lukis(Lukis l) {
		sentuh.zonakan(l, "bh");
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		List<Component> tip = sentuh.tip(mouseX, mouseY);
		if (tip != null) {
			tooltip(tip);
		}
	}

	/** The title plate on the first page: name, path seal, how many entries and scrolls. */
	private void plat(GuiGraphicsExtractor g, Halaman p, int x, int y) {
		float pw = lebarHalaman();
		Ikon.hiasan(g, Ikon.SUDUT, x - 6, y - 6, 12, 12, 0xC0FFFFFF);
		JalanHidup jalan = p.jalan;
		if (jalan != null) {
			Brush.disc(g, x + 13.0F, y + 13.0F, 13.0F, Palette.argb(0xB0, Palette.darken(Palette.opaque(jalan.warna()), 0.45F)));
			Ikon.jalan(g, jalan, x + 2, y + 2, 22, 0xFF);
		} else {
			Ikon.hiasan(g, Ikon.BUKU, x + 3, y + 3, 20, 20, 0xFFFFFFFF);
		}
		Brush.fit(g, font, Brush.bold(p.nama), x + 30.0F, y + 1.0F, pw - 30.0F, Palette.INK, 1.0F, 1.0F, false);
		Component sub = Component.translatable(L + "buku.sub", jalan == null ? Component.translatable(L + "tab.jalan_belum")
				: Component.translatable(jalan.kunciNama()), p.entri.size(), p.gulungan.size());
		Brush.fit(g, font, sub, x + 30.0F, y + 12.0F, pw - 30.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
		if (!p.muat) {
			Brush.fit(g, font, Component.translatable(L + "buku.cermin"), x + 30.0F, y + 23.0F, pw - 30.0F, Palette.WARN, 1.0F, 1.0F, false);
		}
		Brush.rect(g, x, y + 33.0F, x + pw, y + 33.6F, Palette.argb(0xB0, Palette.GOLD));
		Brush.rect(g, x, y + 35.0F, x + pw, y + 35.3F, Palette.argb(0x70, Palette.GOLD));
	}

	private void drawPage(GuiGraphicsExtractor g, int index, int x, int y, int mouseX, int mouseY) {
		if (index >= halaman.size()) {
			if (index == 0) {
				Component empty = Component.translatable(gulungan ? L + "tab.gulungan_kosong" : L + "tab.harian_kosong");
				Brush.wrap(g, font, empty, x, y + 4, lebarHalaman(), Palette.INK_SOFT, 1.0F, false);
			}
			return;
		}
		float pw = lebarHalaman();
		float cy = y;
		for (Blok b : halaman.get(index)) {
			if (b.entri() != null) {
				HarianTeks.Tersusun e = b.entri();
				Ikon.suasana(g, e.suasana(), x, Math.round(cy), 11, 0xFF);
				Brush.fit(g, font, Brush.bold(e.kepala()), x + 14.0F, cy + 1.5F, pw - 14.0F, Palette.onPaper(Ikon.warna(e.suasana())), 1.0F, 1.0F, false);
				if (e.penting()) {
					Ikon.hiasan(g, Ikon.PITA, Math.round(x + pw - 6.0F), Math.round(cy - 3.0F), 7, 22, 0xFFFFFFFF);
				}
				float hy = cy;
				sentuh.tambah(x, hy, x + 12, hy + 12, null, () -> List.of(Component.translatable(L + "suasana."
						+ e.suasana().name().toLowerCase(java.util.Locale.ROOT)).withColor(Ikon.warna(e.suasana()))));
				Brush.wrap(g, font, e.isi(), x + 14.0F, cy + 12.0F, pw - 16.0F, Palette.INK, SKALA, false);
				cy += b.tinggi();
				Brush.rect(g, x + 14.0F, cy - 4.0F, x + pw - 10.0F, cy - 3.6F, Palette.argb(0x30, Palette.INK_SOFT));
			} else if (b.gulungan() != null) {
				Halaman.Gulungan s = b.gulungan();
				Ikon.hiasan(g, Ikon.GULUNGAN, x, Math.round(cy), 14, 14, 0xFFFFFFFF);
				Component jenis = Component.translatable("kultivator.sekte.mandiri.inti.jenis_jalan." + s.jenis());
				Brush.fit(g, font, Brush.bold(Component.translatable(L + "gulungan.judul", jenis, s.hari() + 1)), x + 17.0F, cy, pw - 17.0F,
						s.sukses() ? Palette.JADE_DARK : Palette.SEAL_DARK, 1.0F, 1.0F, false);
				if (!s.tujuan().isEmpty()) {
					Object dest = HarianTeks.arg(s.tujuan());
					Brush.fit(g, font, Component.translatable(L + "gulungan.tujuan", dest instanceof Component c ? c : Component.literal(String.valueOf(dest))),
							x + 17.0F, cy + 10.0F, pw - 17.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
				}
				float ly = cy + 20.0F;
				int n = 1;
				for (Component line : s.baris()) {
					Component numbered = Component.literal(n++ + ". ").withColor(Palette.GOLD_DARK).append(line.copy().withColor(Palette.INK));
					ly += Brush.wrap(g, font, numbered, x + 4.0F, ly, pw - 10.0F, Palette.INK, SKALA, false) + 2.0F;
				}
				cy += b.tinggi();
			}
		}
	}

	private void ribbon(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, boolean scroll) {
		boolean on = gulungan == scroll;
		boolean over = sentuh.di(mouseX, mouseY, x, by0 - 6, x + 22, by0 + (on ? 34 : 24));
		int len = on ? 34 : over ? 28 : 24;
		int color = scroll ? 0xFF2E6E8E : Palette.SEAL;
		g.fill(x + 3, by0 - 6, x + 19, by0 + len, Palette.darken(color, 0.25F));
		g.fill(x + 4, by0 - 6, x + 18, by0 + len - 1, color);
		g.fill(x + 4, by0 + len - 4, x + 11, by0 + len, Palette.darken(color, 0.25F));
		g.fill(x + 11, by0 + len - 6, x + 18, by0 + len - 1, Palette.darken(color, 0.1F));
		Ikon.hiasan(g, scroll ? Ikon.GULUNGAN : Ikon.BUKU, x + 5, by0 - 3, 12, 12, 0xFFFFFFFF);
		sentuh.tambah(x, by0 - 6, x + 22, by0 + len, () -> {
			if (gulungan != scroll) {
				gulungan = scroll;
				spread = 0;
				versiDilihat = -1L;
			}
		}, () -> List.of(Component.translatable(scroll ? L + "buku.pita_gulungan" : L + "buku.pita_harian").withStyle(ChatFormatting.GOLD)));
	}

	private void panah(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, boolean on, int dir) {
		int y = (by0 + by1) / 2 - 8;
		boolean over = on && sentuh.di(mouseX, mouseY, x, y, x + 14, y + 16);
		int color = !on ? Palette.argb(0x50, Palette.INK_FAINT) : over ? Palette.SEAL : Palette.INK_SOFT;
		for (int i = 0; i < 6; i++) {
			int len = 7 - i;
			int cx = dir < 0 ? x + 4 + i : x + 10 - i;
			g.fill(cx, y + 8 - len, cx + 1, y + 8 + len, color);
		}
		if (on) {
			// at least 14 x 16: the kit's smallest click box (43.0.6)
			sentuh.tambah(x, y, x + 14, y + 16, () -> balik(dir), null);
		}
	}

	private void balik(int dir) {
		int max = Math.max(0, (halaman.size() - 1) / 2);
		int next = Math.max(0, Math.min(max, spread + dir));
		if (next != spread) {
			spread = next;
			UiSound.tab();
		}
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
		if (scrollY != 0.0D && mouseY < by1) {
			balik(scrollY < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_A || key == InputConstants.KEY_PAGEUP) {
			balik(-1);
			return true;
		}
		if (key == InputConstants.KEY_D || key == InputConstants.KEY_PAGEDOWN) {
			balik(1);
			return true;
		}
		if (key == InputConstants.KEY_HOME || key == InputConstants.KEY_END) {
			balik(key == InputConstants.KEY_HOME ? -spread : halaman.size());
			return true;
		}
		return false;
	}
}
