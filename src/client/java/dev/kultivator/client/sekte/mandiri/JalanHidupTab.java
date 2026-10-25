package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.client.disciple.MuridPage;
import dev.kultivator.client.disciple.MuridSub;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * "Jalan Hidup" of a disciple (DESIGN 39.7.3) in Murid › Murid (43.5): one instance shows one part. Jalan Hidup stacks
 * Ikhtisar over Kebijakan, Dompet is the purse and bag; the diary (with Tegur / Puji) is the sub Buku Harian
 * ({@link BukuHarianScreen}). The parts:
 * <ol>
 * <li><b>Ikhtisar</b>: the path (icon, name, the "why" from its score: traits, talent, Dao, root, memories, habit, the
 * master's push), the temptation gauge when it rises, {@code [Dorong ke jalan...]} (a row of the nine paths) and
 * {@code [Tebus]} at 60+; up to three goal cards (target, progress, origin, days, {@code [Dukung]} / {@code [Batalkan]});
 * the seven drives; the 24-hour strip of today over yesterday's ({@link PitaJadwal}, click a bar to forbid that kind of
 * activity), what runs now and the next shopping plan.</li>
 * <li><b>Dompet &amp; Tas</b>: the purse against its cap, the pocket money (-/+ in 25 % steps), {@code [Beri uang]} (the
 * stones in the master's hand), the nine bag slots (click = take to the master), the shopping list, the three strongest
 * ties and the named characters it knows.</li>
 * <li><b>Kebijakan</b>: this disciple's mode, caution and permissions over the hall's ({@code [Ikut aula]} drops the
 * overlay), and {@code [Bebaskan]} to end an order pause.</li>
 * </ol>
 * Light values come with the disciple info (part {@code sekte_mandiri}, {@code InfoMandiri}); the diary page is asked for
 * when the tab opens and after every press (the server answers with fresh details and page). Every press sends
 * {@code mandiri_aksi}; the server checks it again.
 */
public class JalanHidupTab extends MuridSub {

	static final String L = "kultivator.sekte.mandiri.cerita.";
	private static final String SUMBER = L + "sumber";
	/** The parts one instance shows (DESIGN 43.5: Ikhtisar and Kebijakan stacked as Jalan Hidup, Tas as Dompet; the diary is Buku Harian). */
	public static final int HAL_IKHTISAR = 0;
	public static final int HAL_TAS = 2;
	public static final int HAL_KEBIJAKAN = 3;
	private static final String[] NAMA_HAL = {"ikhtisar", "harian", "tas", "kebijakan"};
	private static final int BTN_H = 14;
	/** Small in-place buttons and chips: at least 12 high (43.0.6). */
	private static final float MINI_H = 12.0F;

	private final Sentuh sentuh = new Sentuh();
	private final int halaman;
	private boolean pilihDorong;
	private long dimintaPada;
	private String dimintaSoul = "";
	private long versiDilihat = -1L;
	private PitaJadwal.Kotak pita;

	private TalismanButton tDorong;
	private TalismanButton tTebus;
	private TalismanButton tBeri;
	private TalismanButton tIkut;
	private TalismanButton tBebas;

	public JalanHidupTab(MuridPage murid, int halaman) {
		super(murid);
		this.halaman = halaman == HAL_TAS || halaman == HAL_KEBIJAKAN ? halaman : HAL_IKHTISAR;
	}

	@Override
	public String helpTopic() {
		return "mandiri";
	}

	/** Stacked under Jalan Hidup (or alone as Dompet): room for the goal cards, the day strip and the lines at 1.0. */
	@Override
	public int tinggiMin() {
		return halaman == HAL_IKHTISAR ? 236 : halaman == HAL_TAS ? 166 : 186;
	}

	@Override
	public Component title() {
		return Component.translatable(L + "tab.hal." + NAMA_HAL[halaman]);
	}

	@Override
	public Component shortName() {
		return Component.translatable(L + "tab.short");
	}

	// ================================================================== data

	private CompoundTag bagian() {
		return info().getCompoundOrEmpty(MandiriIds.INFO);
	}

	private String soul() {
		return bagian().getStringOr("soul", "");
	}

	private boolean milik() {
		return bagian().getBooleanOr("own", false);
	}

	private Halaman page() {
		return Halaman.dari(MandiriClient.halaman(soul()));
	}

	private void minta() {
		String soul = soul();
		if (soul.isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		if (!soul.equals(dimintaSoul) || now - dimintaPada > 4000L) {
			dimintaSoul = soul;
			dimintaPada = now;
			MandiriClient.mintaHalaman(soul);
		}
	}

	private void tekan(String op, String arg, int n) {
		MandiriClient.aksi(op, soul(), arg, n);
	}

	// ================================================================== controls

	@Override
	protected void build() {
		int right = x + w;
		int top = y + 15;
		tDorong = add(new TalismanButton(right - 150, top, 82, BTN_H, () -> Component.translatable(L + "tab.dorong")));
		tDorong.onPress(() -> pilihDorong = !pilihDorong);
		tDorong.enabledWhen(this::milik);
		tDorong.tooltip(() -> tip(L + "tab.dorong", L + "tab.dorong.tip"));
		tTebus = add(new TalismanButton(right - 64, top, 64, BTN_H, () -> Component.translatable(L + "tab.tebus")));
		tTebus.urgentWhen(() -> true);
		tTebus.onPress(() -> tekan("tebus", "", 0));
		tTebus.tooltip(() -> tip(L + "tab.tebus", L + "tab.tebus.tip"));

		tBeri = add(new TalismanButton(x, y + h - BTN_H, 92, BTN_H, () -> Component.translatable(L + "tab.beri_uang")));
		tBeri.onPress(() -> tekan("beri_uang", "", 0));
		tBeri.enabledWhen(this::milik);
		tBeri.tooltip(() -> tip(L + "tab.beri_uang", L + "tab.beri_uang.tip"));

		tIkut = add(new TalismanButton(x, y + h - BTN_H, 96, BTN_H, () -> Component.translatable(L + "tab.ikut_aula")));
		tIkut.onPress(() -> tekan("ikut_aula", "", 0));
		tIkut.enabledWhen(() -> milik() && bagian().getBooleanOr("tp", false));
		tIkut.tooltip(() -> tip(L + "tab.ikut_aula", L + "tab.ikut_aula.tip"));
		tBebas = add(new TalismanButton(x + 100, y + h - BTN_H, 96, BTN_H, () -> Component.translatable(L + "tab.bebaskan")));
		tBebas.onPress(() -> tekan("bebaskan", "", 0));
		tBebas.enabledWhen(() -> milik() && bagian().getIntOr("bb", 0) != 0);
		tBebas.tooltip(() -> tip(L + "tab.bebaskan", L + "tab.bebaskan.tip"));
	}

	@Override
	protected boolean visible(Widget widget) {
		if (!bagian().getBooleanOr("on", false) || soul().isEmpty()) {
			return false;
		}
		if (widget == tDorong) {
			return halaman == HAL_IKHTISAR;
		}
		if (widget == tTebus) {
			return halaman == HAL_IKHTISAR && milik() && bagian().getIntOr("i", 0) >= 60;
		}
		if (widget == tBeri) {
			return halaman == HAL_TAS;
		}
		if (widget == tIkut || widget == tBebas) {
			return halaman == HAL_KEBIJAKAN;
		}
		return true;
	}

	private static List<Component> tip(String head, String body) {
		return List.of(Component.translatable(head).withStyle(ChatFormatting.GOLD), Component.translatable(body),
				Component.translatable(SUMBER).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		// The path picker is drawn over the page buttons: it takes the click before any widget under it.
		if (pilihDorong && button == 0) {
			if (!sentuh.klik(mouseX, mouseY)) {
				pilihDorong = false;
			}
			return true;
		}
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (button != 0) {
			return false;
		}
		if (sentuh.klik(mouseX, mouseY)) {
			return true;
		}
		if (pilihDorong) {
			pilihDorong = false;
			return true;
		}
		return false;
	}

	/** Esc closes the path picker first (43.4.2: one step back). */
	@Override
	public boolean mundur() {
		if (pilihDorong) {
			pilihDorong = false;
			return true;
		}
		return false;
	}

	// ================================================================== drawing

	/** The click areas drawn this frame join the Tab ring: Enter runs them, a gold ring shows the focus (43.0.7). */
	@Override
	protected void lukis(Lukis l) {
		sentuh.zonakan(l, "jh");
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		sentuh.mulai();
		CompoundTag info = bagian();
		Component kosong = null;
		if (info.isEmpty()) {
			kosong = Component.translatable(L + "tab.memuat");
		} else if (!info.getBooleanOr("on", false)) {
			kosong = Component.translatable(L + "tab.mati");
		} else if (soul().isEmpty()) {
			kosong = Component.translatable(L + "tab.bukan_murid");
		}
		heading(g, title(), x + 2.0F, y, Math.min(140.0F, w * 0.4F), 1.0F);
		if (kosong != null) {
			Brush.wrap(g, font, kosong, x + 4.0F, y + 18.0F, w - 8.0F, Palette.INK_SOFT, 1.0F, false);
			hiasanKosong(g);
			return;
		}
		long v = MandiriClient.versi();
		if (v != versiDilihat) {
			versiDilihat = v;
		}
		minta();
		switch (halaman) {
			case HAL_TAS -> drawTas(g, mouseX, mouseY);
			case HAL_KEBIJAKAN -> drawKebijakan(g, mouseX, mouseY);
			default -> drawIkhtisar(g, mouseX, mouseY);
		}
		if (pilihDorong && halaman == HAL_IKHTISAR) {
			// last in drawBehind (over the cards; the talismans stay above it) so its seals join the Tab ring
			drawPilihDorong(g, mouseX, mouseY);
		}
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		List<Component> tip = sentuh.tip(mouseX, mouseY);
		if (tip != null) {
			tooltip(tip);
		}
	}

	/** Four faint path seals on an empty page. */
	private void hiasanKosong(GuiGraphicsExtractor g) {
		JalanHidup[] show = {JalanHidup.PEDANG, JalanHidup.ALKEMIS, JalanHidup.PERTAPA, JalanHidup.PETUALANG};
		for (int i = 0; i < show.length; i++) {
			int cx = Math.round(x + w * (0.2F + 0.2F * i)) - 12;
			Ikon.jalan(g, show[i], cx, Math.round(y + h * 0.55F), 24, 0x50);
		}
	}

	// ------------------------------------------------------------------ page 1: overview

	private void drawIkhtisar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag info = bagian();
		Halaman p = page();
		JalanHidup jalan = JalanHidup.byId(info.getStringOr("j", ""));
		int iblis = info.getIntOr("i", 0);
		float top = y + 15.0F;
		float rightLimit = x + w - (milik() ? (iblis >= 60 ? 154.0F : 90.0F) : 4.0F);
		// the path seal
		int rgb = jalan == null ? Palette.INK_FAINT : jalan.warna();
		float pulse = iblis >= 60 ? 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 260.0D) : 0.0F;
		if (pulse > 0.0F) {
			Brush.halo(g, x + 15.0F, top + 13.0F, 13.0F, 4.0F, JalanHidup.IBLIS.warna(), 0.25F + 0.3F * pulse);
		}
		Brush.disc(g, x + 15.0F, top + 13.0F, 14.0F, Palette.argb(0xC0, Palette.darken(Palette.opaque(rgb), 0.45F)));
		if (jalan != null) {
			Ikon.jalan(g, jalan, Math.round(x + 3.0F), Math.round(top + 1.0F), 24, 0xFF);
		} else {
			Brush.fitCentered(g, font, Component.literal("?"), x + 15.0F, top + 8.0F, 14.0F, 0xFFFFFFFF, 1.2F, 1.0F, true);
		}
		sentuh.tambah(x, top, x + 30, top + 27, null, () -> jalan == null ? List.of(Component.translatable(L + "tab.jalan_belum"))
				: List.of(Component.translatable(jalan.kunciNama()).withColor(jalan.warna()), Component.translatable(jalan.kunciDesc()),
						Component.translatable(SUMBER).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		float tx = x + 33.0F;
		Component name = jalan == null ? Component.translatable(L + "tab.jalan_belum") : Brush.bold(Component.translatable(jalan.kunciNama()));
		Brush.fit(g, font, name, tx, top + 1.0F, rightLimit - tx, jalan == null ? Palette.INK_SOFT : Palette.onPaper(rgb), 1.0F, 1.0F, false);
		Brush.fit(g, font, alasan(info.getListOrEmpty("al")), tx, top + 11.0F, rightLimit - tx, Palette.INK_SOFT, 1.0F, 1.0F, false);
		if (iblis > 0) {
			float gx0 = tx;
			float gx1 = Math.min(x + w - 50.0F, tx + 110.0F);
			float gy = top + 24.0F;
			Brush.rect(g, gx0, gy, gx1, gy + 3.0F, Palette.argb(0x50, Palette.PAPER_SHADE));
			Brush.rect(g, gx0, gy, gx0 + (gx1 - gx0) * Math.min(100, iblis) / 100.0F, gy + 3.0F, Palette.opaque(JalanHidup.IBLIS.warna()));
			Brush.rect(g, gx0 + (gx1 - gx0) * 0.6F - 0.5F, gy - 1.0F, gx0 + (gx1 - gx0) * 0.6F + 0.5F, gy + 4.0F, Palette.INK);
			Brush.fit(g, font, Component.translatable(L + "tab.godaan", iblis), gx1 + 3.0F, gy - 3.0F, x + w - gx1 - 3.0F,
					Palette.onPaper(JalanHidup.IBLIS.warna()), 1.0F, 1.0F, false);
			sentuh.tambah(gx0, gy - 2, gx1, gy + 5, null, () -> List.of(Component.translatable(L + "tab.godaan", iblis)
					.withColor(JalanHidup.IBLIS.warna()), Component.translatable(L + "tab.godaan.tip")));
		}

		float bodyTop = top + 32.0F;
		float bottomBlock = 52.0F;
		float colW = Math.max(120.0F, w * 0.56F);
		drawCita(g, info, p, x, bodyTop, colW - 6.0F, y + h - bottomBlock - 4.0F - bodyTop, mouseX, mouseY);
		drawDorongan(g, info, x + colW, bodyTop, w - colW, mouseX, mouseY);
		drawHariIni(g, info, p, y + h - bottomBlock, mouseX, mouseY);
	}

	/** "Bakat Pedang + berani + 3 kenangan turnamen": the reason codes of the path score. */
	private static Component alasan(ListTag codes) {
		if (codes.isEmpty()) {
			return Component.translatable(L + "tab.alasan_kosong");
		}
		var out = Component.empty();
		for (int i = 0; i < codes.size(); i++) {
			String c = codes.getStringOr(i, "");
			Component part;
			if (c.startsWith("sifat.")) {
				Sifat s = Sifat.byId(c.substring(6));
				part = s == null ? Component.literal(c.substring(6)) : Component.translatable(s.key());
			} else if (c.startsWith("bakat.")) {
				JalanHidup j = JalanHidup.byId(c.substring(6));
				part = Component.translatable(L + "alasan.bakat", j == null ? Component.literal("?") : Component.translatable(j.kunciNama()));
			} else if (c.startsWith("dao.")) {
				Dao d = Dao.byId(c.substring(4));
				part = Component.translatable(L + "alasan.dao", d == null ? Component.literal(c.substring(4)) : d.displayName());
			} else {
				String key = L + "alasan." + c;
				part = Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(c);
			}
			if (i > 0) {
				out.append(Component.literal(" + "));
			}
			out.append(part);
		}
		return out;
	}

	private void drawCita(GuiGraphicsExtractor g, CompoundTag info, Halaman p, float x0, float y0, float width, float height, int mouseX,
			int mouseY) {
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.cita")), x0 + 2.0F, y0, width, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		float cy = y0 + 11.0F;
		List<Halaman.Cita> list = new ArrayList<>();
		if (p != null && !p.cita.isEmpty()) {
			list.addAll(p.cita);
		} else {
			ListTag c = info.getListOrEmpty("c");
			for (int i = 0; i < c.size(); i++) {
				CompoundTag t = c.getCompoundOrEmpty(i);
				list.add(new Halaman.Cita(t.getStringOr("j", ""), t.getByteOr("p", (byte) 0), t.getByteOr("r", (byte) 3), "", -1L, Component.empty()));
			}
			list.sort((a, b) -> Integer.compare(a.prio(), b.prio()));
		}
		if (list.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(L + "tab.cita_kosong"), x0 + 4.0F, cy + 2.0F, width - 6.0F, Palette.INK_FAINT, 1.0F, false);
			return;
		}
		int cards = Math.max(1, Math.min(3, (int) ((height - 11.0F) / 32.0F)));
		for (int i = 0; i < Math.min(cards, list.size()); i++) {
			Halaman.Cita c = list.get(i);
			JenisCita jenis = JenisCita.byId(c.jenis());
			float cx1 = x0 + width;
			panel(g, Math.round(x0), Math.round(cy), Math.round(cx1), Math.round(cy + 30.0F));
			// priority seal
			int seal = c.prio() == 1 ? Palette.SEAL : c.prio() == 2 ? Palette.GOLD_DARK : Palette.INK_FAINT;
			Brush.disc(g, x0 + 7.0F, cy + 8.0F, 5.5F, seal);
			Brush.fitCentered(g, font, Component.literal(Integer.toString(c.prio())), x0 + 7.5F, cy + 4.0F, 9.0F, 0xFFFFFFFF, 1.0F, 1.0F, false);
			Component judul = jenis == null ? Component.literal(c.jenis()) : Component.translatable(jenis.kunciNama());
			Component baris = c.target().getString().isEmpty() ? judul : judul.copy().append(Component.literal(": ")).append(c.target());
			float bw = width - 18.0F - (milik() ? 56.0F : 0.0F);
			Brush.fit(g, font, Brush.bold(baris), x0 + 14.0F, cy + 3.0F, bw, Palette.INK, 1.0F, 1.0F, false);
			// progress
			float px0 = x0 + 14.0F;
			float px1 = x0 + 14.0F + bw;
			float py = cy + 14.0F;
			Brush.rect(g, px0, py, px1, py + 3.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
			Brush.rect(g, px0, py, px0 + (px1 - px0) * Math.max(0, Math.min(100, c.prog())) / 100.0F, py + 3.0F, Palette.JADE);
			Brush.rect(g, px0, py, px0 + (px1 - px0) * Math.max(0, Math.min(100, c.prog())) / 100.0F, py + 1.0F, Palette.argb(0x80, Palette.JADE_LIGHT));
			Component asal = c.hari() < 0 ? Component.translatable(L + "tab.cita_prog", c.prog())
					: Component.translatable(L + "tab.cita_asal", c.prog(), asal(c.asal()), c.hari());
			Brush.fit(g, font, asal, px0, cy + 19.5F, bw, Palette.INK_SOFT, 1.0F, 1.0F, false);
			if (milik() && jenis != null) {
				float bx = cx1 - 54.0F;
				miniTombol(g, mouseX, mouseY, bx, cy + 2.5F, 52.0F, Component.translatable(L + "tab.dukung"), c.prio() == 1 ? null
						: () -> tekan("cita_dukung", jenis.id(), 0), L + "tab.dukung.tip");
				if (jenis.bisaDibatalkan()) {
					miniTombol(g, mouseX, mouseY, bx, cy + 16.0F, 52.0F, Component.translatable(L + "tab.batalkan"),
							() -> tekan("cita_batal", jenis.id(), 0), L + "tab.batalkan.tip");
				}
			}
			sentuh.tambah(x0, cy, x0 + 14.0F + bw, cy + 30.0F, null, () -> jenis == null ? null
					: List.of(Component.translatable(jenis.kunciNama()).withStyle(ChatFormatting.GOLD),
							Component.translatable(L + "cita.desc." + jenis.id()),
							Component.translatable(SUMBER).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			cy += 32.0F;
		}
	}

	/** The origin of a goal ("sejak kalah final musim 3") from its origin code. */
	static Component asal(String code) {
		if (code == null || code.isEmpty()) {
			return Component.translatable(L + "asal.lain");
		}
		if (code.startsWith("jalan.")) {
			JalanHidup j = JalanHidup.byId(code.substring(6));
			return Component.translatable(L + "asal.jalan", j == null ? Component.literal("?") : Component.translatable(j.kunciNama()));
		}
		String base = code.indexOf(':') > 0 ? code.substring(0, code.indexOf(':')) : code;
		String key = L + "asal." + base.replace('.', '_');
		if (!Language.getInstance().has(key)) {
			return Component.translatable(L + "asal.lain");
		}
		String rest = code.indexOf(':') > 0 ? code.substring(code.indexOf(':') + 1) : "";
		return Component.translatable(key, HarianTeks.arg(rest));
	}

	/** A small paper button drawn in place (cards, bag, chips); {@code aksi} null = disabled. */
	private void miniTombol(GuiGraphicsExtractor g, int mouseX, int mouseY, float bx, float by, float bw, Component label, Runnable aksi,
			String tipKey) {
		float by1 = by + MINI_H;
		boolean over = aksi != null && sentuh.di(mouseX, mouseY, bx, by, bx + bw, by1);
		int top = aksi == null ? Palette.grey(Palette.TALISMAN, 0.7F) : over ? Palette.lighten(Palette.TALISMAN, 0.15F) : Palette.TALISMAN;
		g.fillGradient(Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by1), top, Palette.TALISMAN_LOW);
		Brush.frame(g, Math.round(bx), Math.round(by), Math.round(bx + bw), Math.round(by1), Palette.TALISMAN_EDGE);
		Brush.rect(g, bx + 1.0F, by + 1.0F, bx + 3.0F, by1 - 1.0F, aksi == null ? Palette.LOCKED : Palette.SEAL);
		Brush.fitCentered(g, font, label, bx + bw / 2.0F + 1.0F, by + 2.0F, bw - 6.0F, aksi == null ? Palette.INK_FAINT : Palette.INK, 1.0F, 1.0F,
				false);
		sentuh.tambah(bx, by, bx + bw, by1, aksi, () -> List.of(label.copy().withStyle(ChatFormatting.GOLD), Component.translatable(tipKey)));
	}

	private void drawDorongan(GuiGraphicsExtractor g, CompoundTag info, float x0, float y0, float width, int mouseX, int mouseY) {
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.dorongan")), x0, y0, width, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		int[] d = info.getIntArray("d").orElse(new int[Dorong.JUMLAH]);
		float ry = y0 + 11.0F;
		for (Dorong dorong : Dorong.values()) {
			int value = dorong.ordinal() < d.length ? Math.max(0, Math.min(100, d[dorong.ordinal()])) : 0;
			Ikon.dorong(g, dorong, Math.round(x0), Math.round(ry), 8, 0xFF);
			Brush.fit(g, font, Component.translatable(dorong.kunciNama()), x0 + 10.0F, ry, width * 0.5F - 10.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
			float bx0 = x0 + width * 0.5F + 2.0F;
			float bx1 = x0 + width - 2.0F;
			Brush.rect(g, bx0, ry + 2.5F, bx1, ry + 6.5F, Palette.argb(0x50, Palette.PAPER_SHADE));
			int color = Palette.opaque(dorong.warna());
			Brush.rect(g, bx0, ry + 2.5F, bx0 + (bx1 - bx0) * value / 100.0F, ry + 6.5F, color);
			if (value >= 80) {
				Brush.rect(g, bx0, ry + 2.5F, bx0 + (bx1 - bx0) * value / 100.0F, ry + 3.5F, Palette.argb(0x90, 0xFFFFFF));
			}
			int val = value;
			sentuh.tambah(x0, ry, x0 + width, ry + 9.0F, null, () -> List.of(Component.translatable(dorong.kunciNama()).withColor(dorong.warna())
					.append(Component.literal("  " + val)), Component.translatable(L + "dorong.desc." + dorong.id())));
			ry += 10.5F;
		}
	}

	private void drawHariIni(GuiGraphicsExtractor g, CompoundTag info, Halaman p, float y0, int mouseX, int mouseY) {
		Brush.rect(g, x, y0 - 2.0F, x + w, y0 - 1.5F, Palette.argb(0x90, Palette.GOLD));
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.hari_ini")), x + 2.0F, y0, 60.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		Kegiatan now = Kegiatan.byId(info.getStringOr("k", ""));
		int sisa = info.getIntOr("ks", 0);
		Component doing;
		JenisLuka luka = JenisLuka.byId(info.getStringOr("lk", ""));
		if (info.getBooleanOr("tt", false)) {
			doing = Component.translatable(L + "tab.sedang_tutup");
		} else if (now == null || now == Kegiatan.KOSONG) {
			doing = Component.translatable(L + "tab.sedang_kosong");
		} else if (sisa > 0) {
			doing = Component.translatable(L + "tab.sedang_sisa", Component.translatable(now.kunciNama()), jamTeks(sisa));
		} else {
			doing = Component.translatable(L + "tab.sedang", Component.translatable(now.kunciNama()));
		}
		if (luka != null && luka != JenisLuka.TIDAK) {
			doing = doing.copy().append(Component.literal("  ")).append(Component.translatable(luka.kunciNama()).withStyle(ChatFormatting.RED));
		}
		if (now != null && now != Kegiatan.KOSONG) {
			Ikon.kegiatan(g, now, Math.round(x + 64.0F), Math.round(y0 - 1.0F), 9, 0xFF);
		}
		Brush.fit(g, font, doing, x + 75.0F, y0, w - 77.0F, Palette.INK, 1.0F, 1.0F, false);
		int[] hari = p != null ? p.riwayat : new int[24];
		int[] kemarin = p != null ? p.kemarin : new int[24];
		int jam = p != null ? p.jam : -1;
		pita = PitaJadwal.gambar(g, font, x + 2.0F, y0 + 12.0F, x + w - 2.0F, 9.0F, 4.0F, hari, kemarin, jam);
		int hr = PitaJadwal.jamDi(pita, mouseX, mouseY);
		if (hr >= 0) {
			boolean today = hr < 100;
			int[] strip = today ? hari : kemarin;
			int hour = today ? hr : hr - 100;
			Kegiatan k = Kegiatan.byOrdinal(strip[hour]);
			int[] span = PitaJadwal.rentang(strip, hour);
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable(k.kunciNama()).withColor(Ikon.warna(k)));
			lines.add(Component.translatable(today ? L + "tab.pita.jam" : L + "tab.pita.jam_kemarin", String.format("%02d:00", span[0]),
					String.format("%02d:00", span[1] % 24)));
			if (today && milik() && k != Kegiatan.KOSONG) {
				lines.add(Component.translatable(L + "tab.pita.larang", Component.translatable(k.izin().kunciNama())).withStyle(ChatFormatting.RED));
			}
			tooltip(lines);
		}
		if (milik()) {
			sentuh.tambah(pita.x0(), pita.y0() - 2, pita.x1(), pita.y1() + 2, () -> {
				int at = PitaJadwal.jamDi(pita, lastMouseX, lastMouseY);
				if (at >= 0 && at < 24) {
					Kegiatan k = Kegiatan.byOrdinal(hari[at]);
					if (k != Kegiatan.KOSONG) {
						tekan("larang", k.izin().id(), 0);
					}
				}
			}, null);
		}
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		// the next plan
		Component rencana = rencana(p);
		Brush.fit(g, font, rencana, x + 2.0F, y0 + 41.0F, w - 4.0F, Palette.JADE_DARK, 1.0F, 1.0F, false);
	}

	private double lastMouseX;
	private double lastMouseY;

	/** "Rencana: 2x Pil Fondasi (<= 72 NB) di kota" from the shopping list, else the priority-1 goal. */
	private static Component rencana(Halaman p) {
		if (p == null) {
			return Component.translatable(L + "tab.rencana_memuat");
		}
		if (!p.belanja.isEmpty()) {
			Halaman.Belanja b = p.belanja.get(0);
			Component item = namaItem(b.item());
			return Component.translatable(L + "tab.rencana_belanja", b.n(), item, NilaiBatu.text(Math.max(0L, b.maksHarga())));
		}
		if (!p.cita.isEmpty()) {
			JenisCita j = JenisCita.byId(p.cita.get(0).jenis());
			return Component.translatable(L + "tab.rencana_cita", j == null ? Component.literal("?") : Component.translatable(j.kunciNama()));
		}
		return Component.translatable(L + "tab.rencana_kosong");
	}

	static Component namaItem(String id) {
		Identifier rl = Identifier.tryParse(id == null ? "" : id);
		Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
		return item == null ? Component.literal(id == null ? "?" : id) : Component.translatable(item.getDescriptionId());
	}

	/** Ticks as "0,4 jam" (1000 ticks = 1 in-game hour). */
	static Component jamTeks(int ticks) {
		float hours = ticks / (float) Kegiatan.JAM;
		if (hours >= 24.0F) {
			return Component.translatable(L + "waktu_hari", String.format(java.util.Locale.ROOT, "%.1f", hours / 24.0F).replace('.', ','));
		}
		return Component.translatable(L + "waktu_jam", String.format(java.util.Locale.ROOT, "%.1f", hours).replace('.', ','));
	}

	private void drawPilihDorong(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<JalanHidup> paths = new ArrayList<>();
		for (JalanHidup j : JalanHidup.values()) {
			if (j.pilihanAwal()) {
				paths.add(j);
			}
		}
		float cell = 22.0F;
		float pw = paths.size() * cell + 8.0F;
		float px0 = Math.max(x, x + w - pw);
		float py0 = y + 31.0F;
		g.fill(Math.round(px0) + 2, Math.round(py0) + 2, Math.round(px0 + pw) + 2, Math.round(py0 + 34.0F) + 2, 0x50000000);
		g.fillGradient(Math.round(px0), Math.round(py0), Math.round(px0 + pw), Math.round(py0 + 34.0F), Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		Brush.frame(g, Math.round(px0), Math.round(py0), Math.round(px0 + pw), Math.round(py0 + 34.0F), Palette.GOLD_DARK);
		JalanHidup current = JalanHidup.byId(bagian().getStringOr("j", ""));
		for (int i = 0; i < paths.size(); i++) {
			JalanHidup j = paths.get(i);
			float cx = px0 + 4.0F + i * cell;
			boolean over = sentuh.di(mouseX, mouseY, cx, py0 + 3, cx + cell - 2, py0 + 31);
			if (over) {
				Brush.rect(g, cx, py0 + 3.0F, cx + cell - 2.0F, py0 + 31.0F, Palette.argb(0x50, j.warna()));
			}
			if (j == current) {
				Brush.frame(g, Math.round(cx), Math.round(py0 + 3.0F), Math.round(cx + cell - 2.0F), Math.round(py0 + 31.0F), Palette.SEAL);
			}
			Ikon.jalan(g, j, Math.round(cx + 1.0F), Math.round(py0 + 5.0F), 18, 0xFF);
			sentuh.tambah(cx, py0 + 3.0F, cx + cell - 2.0F, py0 + 31.0F, () -> {
				pilihDorong = false;
				tekan("dorong", j.id(), 0);
			}, () -> List.of(Component.translatable(j.kunciNama()).withColor(j.warna()), Component.translatable(j.kunciDesc()),
					Component.translatable(j == current ? L + "tab.dorong.sama" : L + "tab.dorong.pilih").withStyle(ChatFormatting.GRAY)));
		}
	}

	// ------------------------------------------------------------------ page 3: purse and bag

	private void drawTas(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag info = bagian();
		Halaman p = page();
		float top = y + 17.0F;
		float colW = Math.max(130.0F, w * 0.46F);
		// the purse
		long dp = p != null ? p.dompet : info.getLongOr("dp", 0L);
		long dm = p != null ? Math.max(1L, p.dompetMaks) : Math.max(1L, dp);
		Ikon.hiasan(g, Ikon.KANTONG, Math.round(x), Math.round(top), 18, 18, 0xFFFFFFFF);
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.dompet")), x + 21.0F, top, colW - 22.0F, Palette.SEAL_DARK, 1.0F, 1.0F,
				false);
		Brush.fit(g, font, NilaiBatu.text(dp).copy().append(Component.literal(" / ")).append(NilaiBatu.text(dm)), x + 21.0F, top + 10.0F,
				colW - 22.0F, Palette.INK, 1.0F, 1.0F, false);
		float bx0 = x + 2.0F;
		float bx1 = x + colW - 6.0F;
		float by = top + 22.0F;
		Brush.rect(g, bx0, by, bx1, by + 4.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
		Brush.rect(g, bx0, by, bx0 + (bx1 - bx0) * Math.min(1.0F, dp / (float) dm), by + 4.0F, Palette.GOLD);
		sentuh.tambah(bx0, by - 1, bx1, by + 5, null, () -> List.of(Component.translatable(L + "tab.dompet").withStyle(ChatFormatting.GOLD),
				Component.translatable(L + "tab.dompet.tip")));
		// pocket money
		int us = p != null ? p.sakuPersen : info.getIntOr("us", 100);
		float sy = by + 9.0F;
		Component saku = p != null ? Component.translatable(L + "tab.saku", us, NilaiBatu.text(p.sakuHarian))
				: Component.translatable(L + "tab.saku_pendek", us);
		Brush.fit(g, font, saku, x + 2.0F, sy + 2.0F, colW - 44.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
		if (milik()) {
			miniTombol(g, mouseX, mouseY, x + colW - 42.0F, sy, 17.0F, Component.literal("-"), us <= 0 ? null : () -> tekan("saku", "", Math.max(0, us - 25)),
					L + "tab.saku.tip");
			miniTombol(g, mouseX, mouseY, x + colW - 23.0F, sy, 17.0F, Component.literal("+"), us >= 200 ? null
					: () -> tekan("saku", "", Math.min(200, us + 25)), L + "tab.saku.tip");
		}
		// the bag
		float gy = sy + 16.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.tas")), x + 2.0F, gy, colW, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		gy += 11.0F;
		List<ItemStack> tas = p != null ? p.tas : List.of();
		for (int i = 0; i < MandiriIds.TAS_SLOT; i++) {
			float sx = x + 2.0F + (i % 3) * 21.0F;
			float syy = gy + (i / 3) * 21.0F;
			boolean over = sentuh.di(mouseX, mouseY, sx, syy, sx + 19, syy + 19);
			g.fill(Math.round(sx), Math.round(syy), Math.round(sx) + 19, Math.round(syy) + 19, over ? Palette.argb(0x70, Palette.PAPER_DEEP)
					: Palette.argb(0x45, Palette.PAPER_DEEP));
			Brush.frame(g, Math.round(sx), Math.round(syy), Math.round(sx) + 19, Math.round(syy) + 19, Palette.argb(0xB0, Palette.GOLD_DARK));
			if (i < tas.size()) {
				ItemStack stack = tas.get(i);
				g.item(stack, Math.round(sx) + 2, Math.round(syy) + 2);
				g.itemDecorations(font, stack, Math.round(sx) + 2, Math.round(syy) + 2);
				int slot = i;
				sentuh.tambah(sx, syy, sx + 19, syy + 19, milik() ? () -> tekan("ambil", "", slot) : null, () -> List.of(stack.getHoverName(),
						Component.translatable(milik() ? L + "tab.tas.ambil" : L + "tab.tas.lihat").withStyle(ChatFormatting.GRAY)));
			}
		}
		if (p == null) {
			Brush.fit(g, font, Component.translatable(L + "tab.memuat"), x + 70.0F, gy + 2.0F, colW - 72.0F, Palette.INK_FAINT, 1.0F, 1.0F, false);
		}
		// right column: plan, ties, characters
		float rx = x + colW;
		float rw = w - colW;
		float ry = top;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.belanja")), rx, ry, rw, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		ry += 11.0F;
		if (p == null || p.belanja.isEmpty()) {
			Brush.fit(g, font, Component.translatable(L + "tab.belanja_kosong"), rx + 2.0F, ry, rw - 2.0F, Palette.INK_FAINT, 1.0F, 1.0F, false);
			ry += 10.0F;
		} else {
			for (int i = 0; i < Math.min(4, p.belanja.size()); i++) {
				Halaman.Belanja b = p.belanja.get(i);
				Component line = Component.literal(b.n() + "× ").append(namaItem(b.item())).append(Component.literal("  ≤ "))
						.append(NilaiBatu.text(Math.max(0L, b.maksHarga())));
				Brush.fit(g, font, line, rx + 2.0F, ry, rw - 2.0F, b.prio() == 1 ? Palette.SEAL_DARK : Palette.INK, 1.0F, 1.0F, false);
				String why = b.alasan();
				float lineY = ry;
				sentuh.tambah(rx, lineY, rx + rw, lineY + 9, null, () -> List.of(namaItem(b.item()).copy().withStyle(ChatFormatting.GOLD),
						Language.getInstance().has(why) ? Component.translatable(why) : Component.translatable(L + "tab.belanja.prio", b.prio())));
				ry += 10.0F;
			}
		}
		ry += 3.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.ikatan")), rx, ry, rw, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		ry += 11.0F;
		if (p == null || p.ikatan.isEmpty()) {
			Brush.fit(g, font, Component.translatable(L + "tab.ikatan_kosong"), rx + 2.0F, ry, rw - 2.0F, Palette.INK_FAINT, 1.0F, 1.0F, false);
			ry += 10.0F;
		} else {
			for (Halaman.Ikatan t : p.ikatan) {
				Ikatan kind = Ikatan.byId(t.jenis());
				Component line = t.nama().copy().append(Component.literal("  ")).append(Component.translatable(kind.key()).withStyle(ChatFormatting.ITALIC))
						.append(Component.literal("  " + (t.afinitas() > 0 ? "+" : "") + t.afinitas()));
				Brush.fit(g, font, line, rx + 2.0F, ry, rw - 2.0F, t.afinitas() >= 0 ? Palette.JADE_DARK : Palette.SEAL_DARK, 1.0F, 1.0F, false);
				ry += 10.0F;
			}
		}
		ry += 3.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.tokoh")), rx, ry, rw, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		ry += 11.0F;
		if (p == null || p.tokoh.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(L + "tab.tokoh_kosong"), rx + 2.0F, ry, rw - 2.0F, Palette.INK_FAINT, 1.0F, false);
		} else {
			for (Halaman.Tokoh t : p.tokoh) {
				if (ry > y + h - 10.0F) {
					break;
				}
				Component peran = Component.translatable("kultivator.sekte.mandiri.inti.tokoh." + t.peran().toLowerCase(java.util.Locale.ROOT));
				Component line = Component.literal(t.nama()).append(Component.literal(" · ")).append(peran.copy().withStyle(ChatFormatting.ITALIC));
				Brush.fit(g, font, line, rx + 2.0F, ry, rw - 30.0F, Palette.INK, 1.0F, 1.0F, false);
				int s = t.sikap();
				Brush.fit(g, font, Component.literal((s > 0 ? "+" : "") + s), x + w - 26.0F, ry, 24.0F, s >= 0 ? Palette.JADE_DARK : Palette.SEAL, 1.0F,
						1.0F, false);
				float lineY = ry;
				sentuh.tambah(rx, lineY, x + w, lineY + 9, null, () -> List.of(Component.literal(t.nama()).withStyle(ChatFormatting.GOLD),
						peran, Component.translatable(L + "tab.tokoh.jumpa", t.jumpa(), s),
						Component.translatable(watakKey(t.watak())).withStyle(ChatFormatting.GRAY)));
				ry += 10.0F;
			}
		}
	}

	private static String watakKey(String watak) {
		String key = L + "watak." + watak;
		return watak != null && !watak.isEmpty() && Language.getInstance().has(key) ? key : L + "watak.biasa";
	}

	// ------------------------------------------------------------------ page 4: policy

	private void drawKebijakan(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag info = bagian();
		Halaman p = page();
		Kebijakan kb = p != null ? p.kebijakan : null;
		ModeMandiri mode = ModeMandiri.byId(info.getStringOr("m", ""), ModeMandiri.SEIMBANG);
		Kehatian risiko = Kehatian.byId(info.getStringOr("r", ""), Kehatian.WAJAR);
		float cy = y + 17.0F;
		boolean tp = info.getBooleanOr("tp", false);
		Component note = Component.translatable(tp ? L + "tab.kb.tumpang" : L + "tab.kb.ikut");
		Brush.fit(g, font, note, x + 2.0F, cy, w - 4.0F, tp ? Palette.SEAL_DARK : Palette.JADE_DARK, 1.0F, 1.0F, false);
		cy += 12.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.kb.mode")), x + 2.0F, cy + 2.0F, 70.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		float chipX = x + 74.0F;
		float chipW = (w - 76.0F) / 3.0F;
		for (int i = 0; i < ModeMandiri.values().length; i++) {
			ModeMandiri m = ModeMandiri.values()[i];
			chip(g, mouseX, mouseY, chipX + i * chipW, cy, chipW - 3.0F, Component.translatable(m.kunciNama()), m == mode,
					() -> tekan("mode", m.id(), 0), L + "mode.desc." + m.id());
		}
		cy += 15.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.kb.risiko")), x + 2.0F, cy + 2.0F, 70.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		chipW = (w - 76.0F) / 4.0F;
		for (int i = 0; i < Kehatian.values().length; i++) {
			Kehatian k = Kehatian.values()[i];
			chip(g, mouseX, mouseY, chipX + i * chipW, cy, chipW - 3.0F, Component.translatable(k.kunciNama()), k == risiko,
					() -> tekan("risiko", k.id(), 0), L + "kehatian.desc." + k.id());
		}
		cy += 16.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(L + "tab.kb.izin")), x + 2.0F, cy, w - 4.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		cy += 11.0F;
		int izin = kb != null ? kb.izin() : IzinKegiatan.BAWAAN;
		IzinKegiatan[] groups = IzinKegiatan.values();
		int cols = w >= 300 ? 4 : 3;
		float cw = w / (float) cols;
		for (int i = 0; i < groups.length; i++) {
			IzinKegiatan group = groups[i];
			float gx = x + (i % cols) * cw;
			float gy = cy + (i / cols) * 13.0F;
			if (gy > y + h - BTN_H - 14) {
				break;
			}
			boolean on = group.boleh(izin);
			boolean over = sentuh.di(mouseX, mouseY, gx, gy, gx + cw - 2, gy + MINI_H);
			Brush.rect(g, gx + 1.0F, gy + 2.0F, gx + 8.0F, gy + 9.0F, Palette.argb(0x60, Palette.PAPER_SHADE));
			Brush.frame(g, Math.round(gx + 1.0F), Math.round(gy + 2.0F), Math.round(gx + 8.0F), Math.round(gy + 9.0F), over ? Palette.GOLD : Palette.INK_SOFT);
			if (on) {
				Brush.line(g, gx + 2.0F, gy + 5.5F, gx + 4.0F, gy + 8.0F, 1.2F, Palette.JADE_DARK);
				Brush.line(g, gx + 4.0F, gy + 8.0F, gx + 7.5F, gy + 3.0F, 1.2F, Palette.JADE_DARK);
			} else {
				Brush.line(g, gx + 2.5F, gy + 3.5F, gx + 6.5F, gy + 7.5F, 1.0F, Palette.SEAL);
				Brush.line(g, gx + 6.5F, gy + 3.5F, gx + 2.5F, gy + 7.5F, 1.0F, Palette.SEAL);
			}
			Brush.fit(g, font, Component.translatable(group.kunciNama()), gx + 10.0F, gy + 2.0F, cw - 13.0F, on ? Palette.INK : Palette.INK_FAINT, 1.0F, 1.0F,
					false);
			sentuh.tambah(gx, gy, gx + cw - 2, gy + MINI_H, milik() ? () -> tekan(on ? "larang" : "izinkan", group.id(), 0) : null,
					() -> List.of(Component.translatable(group.kunciNama()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED),
							Component.translatable(L + "izin.desc." + group.id()),
							Component.translatable(on ? L + "tab.kb.klik_larang" : L + "tab.kb.klik_izinkan").withStyle(ChatFormatting.GRAY)));
		}
		int bb = info.getIntOr("bb", 0);
		if (bb != 0) {
			Component pause = bb < 0 ? Component.translatable(L + "tab.kb.perintah_tetap") : Component.translatable(L + "tab.kb.perintah", jamTeks(bb));
			Brush.fit(g, font, pause, x + 200.0F, y + h - BTN_H + 3.0F, w - 202.0F, Palette.WARN, 1.0F, 1.0F, false);
		}
	}

	/** One option chip (stamped red when chosen). */
	private void chip(GuiGraphicsExtractor g, int mouseX, int mouseY, float cx, float cy, float cw, Component label, boolean on, Runnable aksi,
			String tipKey) {
		boolean over = sentuh.di(mouseX, mouseY, cx, cy, cx + cw, cy + MINI_H);
		int fill = on ? Palette.argb(0xE0, Palette.SEAL) : over ? Palette.argb(0x70, Palette.TALISMAN) : Palette.argb(0x50, Palette.TALISMAN_LOW);
		Brush.rect(g, cx, cy, cx + cw, cy + MINI_H, fill);
		Brush.frame(g, Math.round(cx), Math.round(cy), Math.round(cx + cw), Math.round(cy + MINI_H), on ? Palette.SEAL_DARK : Palette.TALISMAN_EDGE);
		Brush.fitCentered(g, font, label, cx + cw / 2.0F, cy + 2.0F, cw - 4.0F, on ? 0xFFFFF4E0 : Palette.INK, 1.0F, 1.0F, false);
		sentuh.tambah(cx, cy, cx + cw, cy + MINI_H, milik() && !on ? aksi : null, () -> List.of(label.copy().withStyle(ChatFormatting.GOLD),
				Component.translatable(tipKey)));
	}
}
