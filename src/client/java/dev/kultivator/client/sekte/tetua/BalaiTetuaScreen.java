package dev.kultivator.client.sekte.tetua;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Realm;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.tetua.TetuaPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Balai Tetua (DESIGN 36.2.6 "UI") as pages of Sekte › Anggota (DESIGN 43.5, 43.12.1 "H"), source Aulaku:
 * <ul>
 * <li><b>tetua</b> ({@code penerus} false): the six offices, each with its seal, its holder (or vacant), the holder's realm,
 * deputy mark, the bonus it gives its domain, its affairs and whether it sits at its desk now; "Angkat" opens the people
 * picker of disciples who may serve (Inti and up, the eligibility shown per disciple), "Copot" relieves after the seal
 * (43.7). The office hours sit under the list; below them, as a stacked heading in the same scroll (43.0.4), the last
 * morning report in full with "Tampilkan kartu".</li>
 * <li><b>penerus</b> ({@code penerus} true): the heir and the ceremony checklist, the Succession Ceremony (heavy seal),
 * the generation, the Founding Patriarch and the lineage.</li>
 * </ul>
 * One scroll per page (wheel, ↑↓ PgUp PgDn Home End), text 1.0. Every button sends E's {@code TetuaPayloads.Act}; the
 * server checks everything again and answers with a fresh view ({@link TetuaClient#view()}).
 */
public final class BalaiTetuaScreen extends Page {

	private static final String K = "kultivator.gui43.m06.";
	private static final int ROW = 24;
	private static final String[] OFFICE_SEALS = {"宗", "律", "經", "庫", "使", "藥"};
	private static final int[] OFFICE_COLOURS = {0xFFD54A, 0xC8463C, 0x4A8CFF, 0xE0A84A, 0x3FC48A, 0x6CFF8A};

	private final boolean penerus;
	private final PemilihOrang pemilih = new PemilihOrang(0);
	/** The office whose picker is open, or null. */
	private @Nullable Jabatan picking;
	private float scroll;
	private int contentH;
	private long seenVersion = -1L;

	public BalaiTetuaScreen(boolean penerus) {
		this.penerus = penerus;
		pemilih.kolom(List.of(Component.translatable(K + "tetua.kolom.pangkat"), Component.translatable(K + "tetua.kolom.ranah")));
		pemilih.onBuka(this::angkat);
	}

	@Override
	public Component title() {
		return Component.translatable(penerus ? "kultivator.sekte.tetua.ui.tab.penerus" : "kultivator.sekte.tetua.ui.title");
	}

	@Override
	public int glyph() {
		return Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return "sekte_hidup";
	}

	private static CompoundTag view() {
		return TetuaClient.view();
	}

	// ================================================================== life

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		TetuaClient.minta();
	}

	@Override
	public void onHide() {
		picking = null;
		pemilih.tutup();
	}

	@Override
	public void tick() {
		long v = TetuaClient.versi();
		if (v != seenVersion) {
			seenVersion = v;
			if (picking != null && view().getListOrEmpty("candidates").isEmpty()) {
				// nothing left to choose from (the server answered an appointment): back to the offices
				picking = null;
				pemilih.tutup();
			}
		}
	}

	@Override
	protected void build() {
		// immediate page: everything is drawn in lukis()
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	public boolean mundur() {
		if (pemilih.tutup()) {
			picking = null;
			return true;
		}
		return false;
	}

	// ================================================================== drawing

	@Override
	protected void lukis(Lukis l) {
		CompoundTag view = view();
		if (view.isEmpty()) {
			l.tengah(Component.translatable("kultivator.gui43.konteks.memuat"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8, l.gaya.teksSamar);
			return;
		}
		Component hall = view.read("hall", ComponentSerialization.CODEC).orElse(Component.empty());
		LaporanCard.seal(l.g, l.font, x + 8, y + 7, 14, "老");
		l.teks(Brush.bold(Component.translatable("kultivator.sekte.tetua.ui.title.hall", hall)), x + 18, y + 3, w - 20, l.gaya.teks);
		int top = y + 16;
		int max = Math.max(0, contentH - (y + h - top));
		scroll = Math.max(0.0F, Math.min(max, scroll));
		int off = Math.round(scroll);
		l.klip(x, top, x + w, y + h);
		int bottom = penerus ? heir(l, view, top - off) : offices(l, view, top - off);
		l.lepasKlip();
		contentH = bottom + off - top;
		if (max > 0) {
			int rail = x + w - 2;
			l.g.fill(rail, top, rail + 2, y + h, Palette.argb(0x40, 0x000000));
			int len = Math.max(10, (y + h - top) * (y + h - top) / Math.max(1, contentH));
			int pos = Math.round((y + h - top - len) * (scroll / max));
			l.g.fill(rail, top + pos, rail + 2, top + pos + len, Palette.JADE);
		}
		if (picking != null) {
			pemilih.gambarSembul(l, "pilih_tetua", Component.translatable("kultivator.sekte.tetua.ui.pick.title", Component.translatable(picking.key())),
					orang(picking));
			if (!pemilih.terbuka()) {
				picking = null;
			}
		}
	}

	/** The offices, the hours, then the morning report; returns the y under the content. */
	private int offices(Lukis l, CompoundTag view, int y0) {
		int y = y0;
		int x1 = x + w - 4;
		boolean narrow = tata() == Tata.SEMPIT;
		int bh = narrow ? 12 : 14;
		y += l.kepala(Component.translatable("kultivator.sekte.tetua.ui.tab.jabatan"), x, y, x1);
		ListTag offices = view.getListOrEmpty("offices");
		int row = 0;
		for (int i = 0; i < offices.size() && i < Jabatan.values().length; i++) {
			if (!(offices.get(i) instanceof CompoundTag line)) {
				continue;
			}
			Jabatan office = Jabatan.byId(line.getStringOr("id", ""));
			if (office == null) {
				continue;
			}
			if (row++ % 2 == 0) {
				l.g.fill(x, y, x1, y + ROW, Palette.argb(0x18, Palette.INK));
			}
			int rgb = OFFICE_COLOURS[office.ordinal()];
			LaporanCard.seal(l.g, l.font, x + 10, y + ROW / 2.0F, 15, OFFICE_SEALS[office.ordinal()]);
			boolean filled = !line.getStringOr("soul", "").isEmpty();
			Component angkat = Component.translatable("kultivator.sekte.tetua.ui.appoint");
			Component copot = Component.translatable("kultivator.sekte.tetua.ui.dismiss");
			int aw = TalismanButton.lebar(l.font, angkat, 40, 70);
			int cw = TalismanButton.lebar(l.font, copot, 40, 70);
			int by = y + (ROW - bh) / 2;
			int bx = x1 - aw;
			if (TalismanButton.gambar(l, "angkat:" + office.id(), bx, by, aw, bh, angkat, true, null, () -> {
				picking = office;
				pemilih.bukaSembul();
				UiSound.press();
			})) {
				l.tip(List.of(Component.translatable("kultivator.sekte.tetua.ui.appoint.tip", Component.translatable(office.key()))));
			}
			int textR = bx - 4;
			if (filled) {
				Component name = line.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				TalismanButton.gambar(l, "copot:" + office.id(), bx - cw - 3, by, cw, bh, copot, true, null, () -> konfirmasi(
						SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "tetua.copot", name, Component.translatable(office.key())),
						() -> send(TetuaPayloads.OP_DISMISS, office.id())));
				textR = bx - cw - 7;
			}
			int tx = x + 22;
			int tw = textR - tx;
			l.teks(Brush.bold(Component.translatable(office.key())), tx, y + 2, tw, Palette.argb(255, darken(rgb)));
			if (!filled) {
				l.samar(Component.translatable("kultivator.sekte.tetua.ui.vacant"), tx, y + 13, tw);
			} else {
				Component name = line.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				Component who = line.getBooleanOr("wakil", false) ? Component.translatable("kultivator.sekte.tetua.ui.deputy", name) : name;
				String status = !line.getBooleanOr("loaded", false) ? "away" : line.getBooleanOr("desk", false) ? "desk" : "idle";
				Component detail = Component.translatable("kultivator.sekte.tetua.ui.detail", Realm.byOrdinal(line.getIntOr("realm", 0)).displayName(),
						factorText(line.getDoubleOr("factor", 1.0D)), line.getIntOr("urusan", 0),
						Component.translatable("kultivator.sekte.tetua.ui.status." + status));
				l.teks(Component.empty().append(who).append(Component.literal(" · ").withStyle(ChatFormatting.GRAY)).append(detail), tx, y + 13, tw,
						l.gaya.teksLemah);
			}
			y += ROW;
		}
		Component hours = Component.translatable("kultivator.sekte.tetua.ui.hours", pad(view.getIntOr("from", 6)), pad(view.getIntOr("to", 18)),
				pad(view.getIntOr("hour", 0)), Component.translatable(view.getBooleanOr("open", false) ? "kultivator.sekte.tetua.ui.hours.open"
						: "kultivator.sekte.tetua.ui.hours.closed"));
		l.samar(hours, x + 2, y + 2, x1 - x - 2);
		y += 16;
		// the morning report, stacked under the offices (was its own tab)
		y += l.kepala(Component.translatable("kultivator.sekte.tetua.ui.tab.laporan"), x, y, x1);
		CompoundTag report = view.getCompoundOrEmpty("report");
		Component show = Component.translatable("kultivator.sekte.tetua.ui.report.show");
		int sw = TalismanButton.lebar(l.font, show, 60, 140);
		if (TalismanButton.gambar(l, "laporan", x1 - sw, y, sw, bh, show, true, null, () -> {
			send(TetuaPayloads.OP_REPORT, "");
			HubNav.tutupSemua(); // the report card is a HUD card: show it over the world
		})) {
			l.tip(List.of(Component.translatable("kultivator.sekte.tetua.ui.report.show.tip")));
		}
		if (report.isEmpty()) {
			y += Math.round(l.bungkus(Component.translatable("kultivator.sekte.tetua.ui.report.none"), x + 2, y + 1, x1 - sw - x - 8, l.gaya.teksLemah));
			return Math.max(y, y + bh) + 4;
		}
		Component by = report.read("by", ComponentSerialization.CODEC).orElse(Component.empty());
		l.teks(Component.translatable("kultivator.sekte.tetua.card.day", report.getLongOr("day", 0L), by), x + 2, y + 2, x1 - sw - x - 8,
				Palette.SEAL_DARK);
		y += bh + 4;
		List<LaporanCard.Row> rows = LaporanCard.rowsOf(report);
		if (rows.isEmpty()) {
			l.samar(Component.translatable("kultivator.sekte.tetua.card.quiet"), x + 4, y, x1 - x - 4);
			y += 10;
		}
		for (LaporanCard.Row row2 : rows) {
			if (row2.tab()) {
				int tw = Math.min(x1 - x - 8, l.font.width(row2.text()) + 8);
				Brush.chamfer(l.g, x + 4, y, x + 4 + tw, y + 10, 2, Palette.argb(220, row2.rgb()));
				l.teks(row2.text(), x + 8, y + 1, tw - 6, 0xFFFFFFFF);
				y += 12;
			} else {
				Brush.dot(l.g, x + 8, y + 4, 1.2F, Palette.argb(200, row2.rgb()));
				l.teks(row2.text(), x + 12, y, x1 - x - 14, l.gaya.teks);
				y += 10;
			}
		}
		Component foot = Component.translatable("kultivator.sekte.tetua.card.foot", report.getIntOr("hadir", 0), report.getIntOr("gelisah", 0),
				report.getIntOr("jabatan", 0), report.getIntOr("kebun", 0), report.getIntOr("tambang", 0));
		l.garis(x + 4, x1 - 4, y + 2);
		l.samar(foot, x + 4, y + 5, x1 - x - 8);
		return y + 16;
	}

	/** The heir, the checklist and the ceremony; then the generation, the founder and the lineage. Returns the y under it. */
	private int heir(Lukis l, CompoundTag view, int y0) {
		int x1 = x + w - 4;
		boolean dua = tata() != Tata.SEMPIT;
		int half = dua ? (x1 - x - 8) / 2 : x1 - x;
		int bh = dua ? 14 : 12;
		int y = y0;
		y += l.kepala(Component.translatable("kultivator.sekte.tetua.ui.heir"), x, y, x + half);
		CompoundTag heir = view.getCompoundOrEmpty("heir");
		if (heir.getStringOr("soul", "").isEmpty()) {
			y += Math.round(l.bungkus(Component.translatable("kultivator.sekte.tetua.ui.heir.none"), x + 2, y, half - 2, l.gaya.teksLemah)) + 2;
		} else {
			Component name = heir.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			l.teks(Brush.bold(name), x + 2, y, half - 2, Palette.SEAL_DARK);
			y += 10;
			l.teks(heir.getBooleanOr("loaded", false) ? Component.translatable("kultivator.sekte.tetua.ui.heir.line", Realm.byOrdinal(heir.getIntOr(
					"realm", 0)).displayName(), heir.getIntOr("stars", 0), heir.getIntOr("hexes", 0)) : Component.translatable(
					"kultivator.sekte.tetua.ui.heir.away"), x + 2, y, half - 2, l.gaya.teksLemah);
			y += 12;
		}
		for (Tag tag : view.getListOrEmpty("reqs")) {
			if (!(tag instanceof CompoundTag r)) {
				continue;
			}
			boolean ok = r.getBooleanOr("ok", false);
			Component text = r.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
			l.teks(Component.literal(ok ? "✔" : "✘"), x + 2, y, 9, ok ? Palette.JADE : Palette.DANGER);
			l.teks(text, x + 12, y, half - 12, ok ? l.gaya.teks : l.gaya.teksLemah);
			y += 10;
		}
		int running = view.getIntOr("running", -1);
		if (running >= 0) {
			l.teks(Component.translatable("kultivator.sekte.tetua.ui.throne.left", running), x + 2, y + 1, half - 2, Palette.SEAL);
			y += 11;
		}
		boolean ready = view.getBooleanOr("ready", false);
		Component throne = Component.translatable(running >= 0 ? "kultivator.sekte.tetua.ui.throne.running" : "kultivator.sekte.tetua.ui.throne");
		int tw = Math.min(half, TalismanButton.lebar(l.font, throne, 80, 200));
		if (TalismanButton.gambar(l, "takhta", x + 2, y + 2, tw, bh, throne, ready && running < 0, Component.translatable(running >= 0
				? "kultivator.sekte.tetua.ui.throne.running" : K + "penerus.belum"), () -> konfirmasi(SegelKonfirmasi.Tingkat.BERAT,
						Component.translatable(K + "penerus.takhta"), () -> {
							send(TetuaPayloads.OP_THRONE, "");
							HubNav.tutupSemua(); // the ceremony is a scene in the world
						}), ready && running < 0, false)) {
			l.tip(List.of(Component.translatable("kultivator.sekte.tetua.ui.throne"),
					Component.translatable("kultivator.sekte.tetua.ui.throne.tip").withStyle(ChatFormatting.GRAY)));
		}
		y += bh + 6;
		// the generation, the founder and the lineage: right column when wide, below when narrow
		int rx = dua ? x + half + 8 : x;
		int ry = dua ? y0 : y + 2;
		int rw = dua ? x1 - rx : x1 - x;
		ry += l.kepala(Component.translatable("kultivator.sekte.tetua.ui.lineage"), rx, ry, rx + rw);
		int gen = view.getIntOr("gen", 0);
		Component owner = view.read("owner", ComponentSerialization.CODEC).orElse(Component.empty());
		if (gen >= 2) {
			Component ketua = view.read("ketua", ComponentSerialization.CODEC).orElse(Component.translatable("kultivator.sekte.tetua.none"));
			l.teks(Component.translatable("kultivator.sekte.tetua.ui.ketua", gen, ketua), rx + 2, ry, rw - 2, Palette.argb(255, 0x8A671B));
			ry += 10;
		}
		if (view.getBooleanOr("founder", false)) {
			l.teks(Component.translatable("kultivator.sekte.tetua.title.founder", owner), rx + 2, ry, rw - 2, Palette.SEAL_DARK);
			ry += 11;
		} else {
			ry += Math.round(l.bungkus(Component.translatable("kultivator.sekte.tetua.ui.founder.hint"), rx + 2, ry, rw - 2, l.gaya.teksSamar)) + 2;
		}
		for (Tag tag : view.getListOrEmpty("lineage")) {
			if (!(tag instanceof CompoundTag line)) {
				continue;
			}
			Component name = line.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component text = Component.translatable(line.getBooleanOr("founder", false) ? "kultivator.sekte.tetua.ui.lineage.founder"
					: "kultivator.sekte.tetua.ui.lineage.line", line.getIntOr("gen", 0), name, line.getLongOr("day", 0L));
			Brush.dot(l.g, rx + 3, ry + 4, 1.4F, Palette.argb(220, Palette.GOLD_DARK));
			l.teks(text, rx + 8, ry, rw - 8, l.gaya.teks);
			ry += 10;
		}
		l.samar(Component.translatable("kultivator.sekte.tetua.ui.age", view.getIntOr("age", 0)), rx + 2, ry + 2, rw - 2);
		ry += 14;
		return Math.max(y, ry);
	}

	// ================================================================== the picker

	/** The disciples who may serve {@code office}, for the people picker (the eligibility as tag or lock). */
	private static List<PemilihOrang.Orang> orang(Jabatan office) {
		List<PemilihOrang.Orang> list = new ArrayList<>();
		for (Tag tag : view().getListOrEmpty("candidates")) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			int eligible = eligible(c.getIntOr("rank", 0), office);
			Component name = c.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Jabatan held = Jabatan.byId(c.getStringOr("office", ""));
			Component tanda = eligible == 1 ? Component.translatable(K + "tetua.wakil") : held != null ? Component.translatable(held.key()) : null;
			Component kunci = eligible == 0 ? Component.translatable("kultivator.sekte.tetua.ui.pick.no") : null;
			list.add(new PemilihOrang.Orang(c.getStringOr("soul", ""), name, OFFICE_COLOURS[office.ordinal()], -1,
					List.of(SectRanks.rankName(c.getIntOr("rank", 0)), Realm.byOrdinal(c.getIntOr("realm", 0)).displayName()), tanda, kunci, null));
		}
		return list;
	}

	/** Enter / second click on a disciple in the picker: appoint it, back to the offices. */
	private void angkat(PemilihOrang.Orang o) {
		Jabatan office = picking;
		if (office == null || o.kunci() != null) {
			UiSound.denied();
			return;
		}
		send(TetuaPayloads.OP_APPOINT, office.id() + ":" + o.id());
		picking = null;
		pemilih.tutup();
	}

	/** 2 full, 1 deputy, 0 none (mirrors the server's rule for the picker's hints; the server decides). */
	private static int eligible(int rank, Jabatan office) {
		if (office == Jabatan.TETUA_AGUNG) {
			return rank >= SectRanks.TETUA_AGUNG ? 2 : rank == SectRanks.TETUA ? 1 : 0;
		}
		return rank >= SectRanks.TETUA ? 2 : rank == SectRanks.INTI ? 1 : 0;
	}

	private static void send(String op, String arg) {
		UiSound.press();
		TetuaClient.send(op, arg);
	}

	private static int darken(int rgb) {
		int r = (rgb >> 16 & 255) * 55 / 100;
		int gr = (rgb >> 8 & 255) * 55 / 100;
		int b = (rgb & 255) * 55 / 100;
		return r << 16 | gr << 8 | b;
	}

	private static String pad(int hour) {
		return (hour < 10 ? "0" : "") + hour + ":00";
	}

	private static String factorText(double factor) {
		return "×" + String.format(java.util.Locale.ROOT, "%.2f", factor);
	}

	// ================================================================== input: the page scroll

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (picking != null && pemilih.terbuka()) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (inside(mouseX, mouseY)) {
			scroll -= (float) scrollY * 18.0F;
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (picking != null) {
			return false;
		}
		int page = Math.max(20, h - 30);
		switch (event.key()) {
			case InputConstants.KEY_UP -> scroll -= 18.0F;
			case InputConstants.KEY_DOWN -> scroll += 18.0F;
			case InputConstants.KEY_PAGEUP -> scroll -= page;
			case InputConstants.KEY_PAGEDOWN -> scroll += page;
			case InputConstants.KEY_HOME -> scroll = 0.0F;
			case InputConstants.KEY_END -> scroll = contentH;
			default -> {
				return false;
			}
		}
		return true;
	}
}
