package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Fokus;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.TombolKembali;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.diplomasi.DiplomasiScreen;
import dev.kultivator.client.sekte.diplomasi.LuarPage;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perseteruan (DESIGN 39.F.4, 39.F.9) as the page Sekte › Luar › Seteru (43.5; R22: it was a hub card of section Murid).
 * One list with stacked headings (43.0.4): the hall's feuds (their sect's seal, our disciple × theirs, the cause icon, a heat
 * bar or the status), the friends from other sects, the patrol on the hall's road. The chosen entry's detail beside it
 * (or in its place in the narrow layout, with "‹ Kembali"): for a feud the bamboo ladder T0-T7 with the rungs reached burnt
 * red and the cap locked, the heat gauge, their members as cards ({@link #kartuTokoh}), what waits, the history, and the
 * master's eight actions (Restui / Pisahkan for forbidden lovers), each confirmed with a 1.5 s hold (R7). The named
 * disciples of every other sect are in Sekte Dunia (G36). Text is 1.0 and the layout follows the box (R8); the page asks
 * for a fresh sheet each time it is shown (R16).
 */
public final class SeteruScreen extends Page {

	private static final Identifier IKON = Kultivator.id("textures/sekte/mandiri/seteru/ikon.png");
	private static final String M = "kultivator.gui43.m11.";
	private static final int TOMBOL_H = 14;
	public static final int TINGGI_KARTU = 42;

	/** One row of the list: a heading, a feud, a friend or the patrol. */
	private record Baris(int jenis, @Nullable Component judul, CompoundTag data) {
	}

	private static final int JUDUL = 0;
	private static final int SETERU = 1;
	private static final int SAHABAT = 2;
	private static final int PATROLI = 3;

	private final DaftarGulir daftar = new DaftarGulir();
	private final GulirIsi gulir = new GulirIsi();
	private long versiDiminta = Long.MIN_VALUE;
	private String pilih = "";
	private boolean rinciSempit;
	private List<Baris> baris = List.of();

	public SeteruScreen() {
	}

	// ================================================================== sheet access

	private static List<CompoundTag> list(CompoundTag tag, String key) {
		ListTag l = tag.getListOrEmpty(key);
		List<CompoundTag> out = new ArrayList<>(l.size());
		for (int i = 0; i < l.size(); i++) {
			out.add(l.getCompoundOrEmpty(i));
		}
		return out;
	}

	private static Component comp(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.empty());
	}

	private static boolean aktif(CompoundTag f) {
		String s = f.getStringOr("status", "");
		return SeteruIds.AKTIF.equals(s) || SeteruIds.TERLARANG.equals(s);
	}

	/** The rows of the list: feuds (active first, as the server sends them), friends, the patrol, each under a heading. */
	private static List<Baris> susun(CompoundTag sheet) {
		List<Baris> out = new ArrayList<>();
		out.add(new Baris(JUDUL, Component.translatable(SeteruIds.k("layar.tab.seteru")), new CompoundTag()));
		for (CompoundTag f : list(sheet, "seteru")) {
			out.add(new Baris(SETERU, null, f));
		}
		List<CompoundTag> sahabat = list(sheet, "sahabat");
		if (!sahabat.isEmpty()) { // P5: no empty heading
			out.add(new Baris(JUDUL, Component.translatable(SeteruIds.k("layar.sahabat")), new CompoundTag()));
			for (CompoundTag fr : sahabat) {
				out.add(new Baris(SAHABAT, null, fr));
			}
		}
		CompoundTag patrol = sheet.getCompoundOrEmpty("patroli");
		if (!patrol.isEmpty()) {
			out.add(new Baris(JUDUL, Component.translatable(SeteruIds.k("layar.patroli")), new CompoundTag()));
			out.add(new Baris(PATROLI, null, patrol));
		}
		return out;
	}

	private static String kunci(Baris b, int i) {
		return switch (b.jenis()) {
			case SETERU -> "s:" + b.data().getStringOr("id", "");
			case SAHABAT -> "f:" + i;
			case PATROLI -> "p";
			default -> "h:" + i;
		};
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable(SeteruIds.k("layar.judul"));
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return "sekte_hidup";
	}

	@Override
	public boolean rinci() {
		return rinciSempit && tata() == Tata.SEMPIT;
	}

	// ================================================================== life

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		// R16: a fresh sheet each time; "memuat…" until it is here
		versiDiminta = SeteruClient.versi();
		SeteruClient.minta(SeteruIds.OP_SEGAR, "", 0, "");
		daftar.mulaiUlang(Util.getMillis());
		super.onShow(fokus);
	}

	@Override
	public void fokus(CompoundTag fokus) {
		String id = fokus.getStringOr("seteru", "");
		if (!id.isEmpty()) {
			pilih = "s:" + id;
			rinciSempit = true;
		}
	}

	private boolean memuat() {
		return SeteruClient.versi() <= versiDiminta && SeteruClient.tersedia();
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		int x0 = x + 2;
		int x1 = x + w - 2;
		int y0 = y + 2;
		int y1 = y + h - 2;
		CompoundTag sheet = SeteruClient.data();
		if (!SeteruClient.tersedia()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(SeteruIds.k("layar.tanpa_server")), null, null, x0, y0, x1, y1);
			return;
		}
		if (sheet == null || memuat()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(M + "memuat"), null, null, x0, y0, x1, y1);
			return;
		}
		int y = y0;
		y += info(l, sheet, x0, y, x1);
		baris = susun(sheet);
		int at = pilihan();
		if (at < 0) {
			// P5: nothing but headings: one empty state at every layout (43.18)
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(SeteruIds.k("layar.kosong")), null, null, x0, y, x1, y1);
			return;
		}
		boolean sempit = tata() == Tata.SEMPIT;
		if (sempit && rinciSempit) {
			rinciBaris(l, sheet, baris.get(at), x0, y, x1, y1);
			return;
		}
		int lx1 = sempit ? x1 : x0 + Math.round((x1 - x0) * 0.42F);
		if (at >= 0 && daftar.pilihan() != at) {
			daftar.setPilih(at);
		}
		List<Baris> rows = baris;
		daftar.judul(i -> i < rows.size() && rows.get(i).jenis() == JUDUL, 13);
		daftar.gambar(l, "seteru", x0, y, lx1, y1, rows.size(), 24, (lk, i, rx0, ry0, rx1, ry1, sorot, dipilih) -> gambarBaris(lk, rows.get(i), rx0, ry0,
				rx1, ry1, sorot, dipilih), () -> rinciSempit = true, i -> {
			if (i >= 0 && i < rows.size()) {
				pilih = kunci(rows.get(i), i);
				gulir.atas();
			}
		});
		if (!sempit) {
			rinciBaris(l, sheet, baris.get(at), lx1 + 6, y, x1, y1);
		}
	}

	/** The selected row (the first feud, else the first entry, when nothing is chosen yet); -1 = only headings. */
	private int pilihan() {
		int pertama = -1;
		for (int i = 0; i < baris.size(); i++) {
			Baris b = baris.get(i);
			if (b.jenis() == JUDUL) {
				continue;
			}
			if (kunci(b, i).equals(pilih)) {
				return i;
			}
			if (pertama < 0) {
				pertama = i;
			}
		}
		if (pertama >= 0) {
			pilih = kunci(baris.get(pertama), pertama);
		}
		return pertama;
	}

	/** The hall's line: active feuds of the cap, intensity, policy. Returns the height used. */
	private static int info(Lukis l, CompoundTag sheet, int x0, int y, int x1) {
		int aktif = 0;
		for (CompoundTag f : list(sheet, "seteru")) {
			if (aktif(f)) {
				aktif++;
			}
		}
		Component info = Component.translatable(SeteruIds.k("layar.info"), aktif, sheet.getIntOr("maks", 3),
				Component.translatable(SeteruIds.k("intensitas." + sheet.getStringOr("intensitas", "SEDANG").toLowerCase(Locale.ROOT))),
				Component.translatable(SeteruIds.k("kebijakan." + sheet.getStringOr("kebijakan", "TANYA").toLowerCase(Locale.ROOT))));
		l.samar(info, x0, y, x1 - x0);
		if (l.di(x0, y, x1, y + 10)) {
			l.tip(List.of(Component.translatable(SeteruIds.k("layar.sub")), Component.translatable(SeteruIds.k("layar.tubuh"), sheet.getIntOr("tubuh", 0))));
		}
		l.garis(x0, x1, y + 11);
		return 15;
	}

	private void gambarBaris(Lukis l, Baris b, int x0, int y0, int x1, int y1, boolean sorot, boolean dipilih) {
		if (b.jenis() == JUDUL) {
			l.kepala(b.judul(), x0, y0, x1);
			return;
		}
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, dipilih);
		CompoundTag d = b.data();
		switch (b.jenis()) {
			case SETERU -> {
				boolean live = aktif(d);
				lambang(l, d.getIntOr("lambang", Lambang.DEFAULT.pack()), x0 + 11, y0 + 11, 8.0F, live ? 1.0F : 0.5F);
				List<CompoundTag> souls = list(d, "jiwa");
				List<CompoundTag> them = list(d, "tokoh");
				Component ours = souls.isEmpty() ? Component.literal("?") : comp(souls.get(0), "nama");
				Component theirs = them.isEmpty() ? Component.literal("?") : comp(them.get(0), "nama");
				int tx = x0 + 22;
				ikon(l.g, SeteruIds.sebabIndex(d.getStringOr("sebab", "")), x1 - 12, y0 + 2, 10, live ? 1.0F : 0.5F);
				l.teks(Component.empty().append(ours).append(" × ").append(theirs), tx, y0 + 2, x1 - tx - 14, live ? l.gaya.teks : l.gaya.teksSamar);
				String status = d.getStringOr("status", "");
				if (live && !SeteruIds.TERLARANG.equals(status)) {
					int heat = d.getIntOr("panas", 0);
					Component tingkat = Component.translatable(SeteruIds.k("tingkat.pendek." + Math.max(0, d.getIntOr("tingkat", 0))));
					int tw = Math.min(70, l.font.width(tingkat));
					l.teks(tingkat, x1 - 4 - tw, y0 + 12, tw, Palette.SEAL_DARK);
					Bilah.gambar(l, tx, y0 + 15, x1 - 8 - tw, heat / 100.0F, panasWarna(heat), null);
				} else {
					l.teks(Component.translatable(SeteruIds.k("status." + status)), tx, y0 + 12, x1 - tx - 4, statusWarna(status));
				}
			}
			case SAHABAT -> {
				ikon(l.g, 31, x0 + 4, y0 + 6, 11, 1.0F);
				l.teks(Component.translatable(SeteruIds.k("layar.sahabat_baris"), comp(d, "murid"), comp(d, "tokoh")), x0 + 20, y0 + 2, x1 - x0 - 24,
						l.gaya.teks);
				l.teks(comp(d, "sekte").copy().append(" · ").append(Component.translatable(SeteruIds.k("layar.sikap"), d.getIntOr("sikap", 0))), x0 + 20,
						y0 + 12, x1 - x0 - 24, l.gaya.teksSamar);
			}
			default -> {
				ikon(l.g, 26, x0 + 4, y0 + 4, 14, 1.0F);
				l.teks(comp(d, "sekte"), x0 + 22, y0 + 2, x1 - x0 - 26, l.gaya.teks);
				l.teks(comp(d, "kota"), x0 + 22, y0 + 12, x1 - x0 - 26, l.gaya.teksSamar);
			}
		}
	}

	private void rinciBaris(Lukis l, CompoundTag sheet, Baris b, int x0, int y0, int x1, int y1) {
		int top = y0;
		if (tata() == Tata.SEMPIT) {
			TombolKembali.gambar(l, "kembali", x0, y0, null, () -> rinciSempit = false);
			top += 16;
		}
		l.sumur(x0, top, x1, y1);
		int cx0 = x0 + 4;
		int cx1 = x1 - 9;
		int y = gulir.mulai(l, x0 + 1, top + 1, x1 - 6, y1 - 1) + 3;
		y = switch (b.jenis()) {
			case SETERU -> rinciSeteru(l, sheet, b.data(), cx0, y, cx1);
			case SAHABAT -> rinciSahabat(l, b.data(), cx0, y, cx1);
			default -> rinciPatroli(l, sheet, b.data(), cx0, y, cx1);
		};
		gulir.selesai(l, y + 4);
	}

	private int rinciSeteru(Lukis l, CompoundTag sheet, CompoundTag f, int x0, int y, int x1) {
		String status = f.getStringOr("status", "");
		boolean lover = SeteruIds.TERLARANG.equals(status);
		List<CompoundTag> souls = list(f, "jiwa");
		List<CompoundTag> them = list(f, "tokoh");
		// title: ours, theirs and their sect, the cause
		lambang(l, f.getIntOr("lambang", Lambang.DEFAULT.pack()), x0 + 12, y + 12, 11.0F, 1.0F);
		MutableComponent names = Component.empty();
		for (int i = 0; i < souls.size(); i++) {
			if (i > 0) {
				names.append(", ");
			}
			names.append(comp(souls.get(i), "nama"));
		}
		int tx = x0 + 28;
		l.teks(Brush.bold(names), tx, y + 1, x1 - tx, l.gaya.teks);
		Component other = Component.translatable(SeteruIds.k(lover ? "layar.kekasih_dari" : "layar.melawan"),
				them.isEmpty() ? Component.literal("?") : comp(them.get(0), "nama"), comp(f, "namaSekte"));
		l.teks(other, tx, y + 12, x1 - tx, Palette.SEAL_DARK);
		y += 26;
		int sikap = DiplomasiScreen.sikap(f);
		Component cause = Component.translatable(SeteruIds.k("layar.sebab"), Component.translatable(SeteruIds.k("sebab." + f.getStringOr("sebab", ""))),
				Component.translatable(SeteruIds.k("salah." + f.getStringOr("salah", "-").replace("-", "tidak"))), DiplomasiScreen.namaSikap(sikap),
				f.getIntOr("standing", 0));
		ikon(l.g, SeteruIds.sebabIndex(f.getStringOr("sebab", "")), x0, y, 9, 1.0F);
		y += (int) l.bungkus(cause, x0 + 12, y, x1 - x0 - 12, l.gaya.teksLemah, 27) + 4;
		String sekte = f.getStringOr("sekte", "");
		if (!sekte.isEmpty()) {
			Component lihat = Component.translatable(M + "lihat_sekte");
			int lw = TalismanButton.lebar(l.font, lihat, 60, x1 - x0);
			TalismanButton.gambar(l, "lihat:" + sekte, x0, y, lw, TOMBOL_H, lihat, true, null, () -> LuarPage.bukaSekte(sekte));
			y += TOMBOL_H + 4;
		}
		// the ladder and the heat
		if (!lover) {
			y += tangga(l, f, x0, y, x1);
		} else {
			y += (int) l.bungkus(Component.translatable(SeteruIds.k("layar.kekasih_info")), x0, y, x1 - x0, l.gaya.teksLemah, 45) + 4;
		}
		// their members
		if (!them.isEmpty()) {
			int kolom = x1 - x0 >= 230 ? 2 : 1;
			int cw = (x1 - x0 - (kolom - 1) * 4) / kolom;
			for (int i = 0; i < them.size() && i < 4; i++) {
				int cx = x0 + (i % kolom) * (cw + 4);
				int cy = y + (i / kolom) * (TINGGI_KARTU + 3);
				kartuTokoh(l, them.get(i), cx, cy, cx + cw);
			}
			y += ((Math.min(4, them.size()) + kolom - 1) / kolom) * (TINGGI_KARTU + 3) + 2;
		}
		// what waits
		for (Component wait : menanti(sheet, f)) {
			y += (int) l.bungkus(wait, x0, y, x1 - x0, Palette.WARN, 18) + 1;
		}
		y += 3;
		// the master's actions
		y += aksi(l, f, lover, x0, y, x1) + 3;
		// the history
		y += l.kepala(Component.translatable(SeteruIds.k("layar.riwayat")), x0, y, x1);
		List<CompoundTag> history = list(f, "riwayat");
		int shown = 0;
		for (int i = history.size() - 1; i >= 0 && shown < 8; i--, shown++) {
			CompoundTag line = history.get(i);
			Component text = Component.translatable(SeteruIds.k("layar.hari"), line.getLongOr("hari", 0L)).append(" ").append(baris(line));
			y += (int) l.bungkus(text, x0, y, x1 - x0, shown == 0 ? l.gaya.teks : l.gaya.teksLemah, 27) + 1;
		}
		return y;
	}

	/** The bamboo ladder T0-T7 with burnt rungs, the cap lock, the flame on the heat, and the gauge below; returns the height. */
	private static int tangga(Lukis l, CompoundTag f, int x0, int y, int x1) {
		int heat = f.getIntOr("panas", 0);
		int reached = f.getIntOr("tingkat", -1);
		int cap = f.getIntOr("batas", 6);
		float span = x1 - x0 - 16;
		l.g.fill(x0 + 6, y + 4, x1 - 6, y + 6, Palette.BAMBOO_KNOT);
		l.g.fill(x0 + 6, y + 18, x1 - 6, y + 20, Palette.BAMBOO_KNOT);
		l.g.fill(x0 + 6, y + 4, x1 - 6, y + 5, Palette.BAMBOO);
		l.g.fill(x0 + 6, y + 18, x1 - 6, y + 19, Palette.BAMBOO);
		for (int t = 0; t <= 7; t++) {
			float rx = x0 + 8 + span * SeteruIds.AMBANG[t] / 100.0F;
			boolean burnt = t <= reached;
			boolean locked = t > cap;
			int col = locked ? 0xFF4A4038 : burnt ? Palette.mix(0xFFE07040, 0xFF8B1A1A, t / 7.0F) : Palette.BAMBOO_ALT;
			int ix = Math.round(rx);
			l.g.fill(ix - 1, y + 4, ix + 2, y + 20, col);
			if (locked) {
				ikon(l.g, 30, ix - 4, y + 8, 8, 0.9F);
			}
			if (l.di(ix - 5, y, ix + 6, y + 22)) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal("T" + t + " · ").append(Component.translatable(SeteruIds.k("tingkat." + t))).withColor(Palette.GOLD_DARK & 0xFFFFFF));
				tip.add(Component.translatable(SeteruIds.k("tingkat." + t + ".desc")));
				if (locked) {
					tip.add(Component.translatable(SeteruIds.k("layar.terkunci")).withColor(Palette.INK_FAINT & 0xFFFFFF));
				}
				l.tip(tip);
			}
		}
		float hx = x0 + 8 + span * Mth.clamp(heat, 0, 100) / 100.0F;
		float flicker = 1.0F + 0.12F * Mth.sin(Util.getMillis() / 90.0F);
		Brush.halo(l.g, hx, y + 12, 7.0F * flicker, 4.0F, 0xFF6A2A, 0.6F);
		ikon(l.g, 29, Math.round(hx) - 5, y + 6, 11, 1.0F);
		int gy = y + 24;
		Bilah.gambar(l, x0 + 8, gy, x1 - 8, heat / 100.0F, panasWarna(heat), null);
		Component panas = Component.translatable(SeteruIds.k("layar.panas"), heat);
		l.teks(panas, x0 + 8, gy + 6, x1 - x0 - 16, l.gaya.teksLemah);
		if (f.getBooleanOr("beku", false)) {
			int pw = l.font.width(panas) + 8;
			l.teks(Component.translatable(SeteruIds.k("layar.beku")), x0 + 8 + pw, gy + 6, x1 - x0 - 16 - pw, Palette.JADE_DARK);
		}
		return 42;
	}

	/**
	 * A member card of another sect (also used by Sekte Dunia): name and sex, role and realm, traits and Dao, the attitude
	 * bar from -100 to 100, grudge and wound marks; the details in the tooltip. Height {@link #TINGGI_KARTU}.
	 */
	public static void kartuTokoh(Lukis l, CompoundTag t, int x0, int y0, int x1) {
		int y1 = y0 + TINGGI_KARTU;
		l.kartu(x0, y0, x1, y1, 0.0F, false);
		boolean woman = t.getBooleanOr("perempuan", false);
		l.teks(Component.literal(woman ? "♀" : "♂"), x0 + 3, y0 + 2, 8, woman ? 0xFFC0406A : 0xFF3A6AA8);
		int mark = x1 - 3;
		if (t.getBooleanOr("dendam", false)) {
			mark -= 10;
			ikon(l.g, 11, mark, y0 + 2, 9, 1.0F);
		}
		if (t.getBooleanOr("luka", false)) {
			mark -= 10;
			ikon(l.g, 28, mark, y0 + 2, 9, 1.0F);
		}
		if (t.getBooleanOr("seteru", false)) {
			mark -= 10;
			ikon(l.g, 9, mark, y0 + 2, 9, 1.0F);
		}
		l.teks(Brush.bold(comp(t, "nama")), x0 + 12, y0 + 2, mark - x0 - 14, l.gaya.teks);
		Component role = Component.translatable(t.getStringOr("peran", "")).append(" · ")
				.append(Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, t.getIntOr("langkah", 2)))).displayName());
		l.teks(role, x0 + 3, y0 + 12, x1 - x0 - 6, l.gaya.teksLemah);
		MutableComponent traits = Component.empty();
		ListTag sifat = t.getListOrEmpty("sifat");
		for (int i = 0; i < sifat.size(); i++) {
			if (i > 0) {
				traits.append(", ");
			}
			traits.append(Component.translatable("kultivator.sekte.sifat." + sifat.getStringOr(i, "")));
		}
		Dao dao = Dao.byId(t.getStringOr("dao", ""));
		if (dao != null) {
			traits.append(" · ").append(dao.displayName().copy().withColor(dao.color()));
		}
		l.teks(traits, x0 + 3, y0 + 22, x1 - x0 - 6, l.gaya.teksSamar);
		int sikap = Mth.clamp(t.getIntOr("sikap", 0), -100, 100);
		int bx0 = x0 + 3;
		int bx1 = x1 - 3;
		int mid = (bx0 + bx1) / 2;
		int by = y0 + 35;
		l.g.fill(bx0, by, bx1, by + 3, Palette.argb(0x40, 0x000000));
		l.g.fill(mid, by - 1, mid + 1, by + 4, Palette.argb(0x90, l.gaya.teks));
		int end = mid + Math.round((bx1 - mid) * sikap / 100.0F);
		l.g.fill(Math.min(mid, end), by, Math.max(mid, end), by + 3, sikap >= 0 ? Palette.JADE : Palette.SEAL);
		if (l.di(x0, y0, x1, y1)) {
			List<Component> tip = new ArrayList<>();
			tip.add(comp(t, "nama").copy().withColor(Palette.GOLD_DARK & 0xFFFFFF));
			tip.add(Component.translatable(SeteruIds.k("layar.sikap"), sikap));
			tip.add(Component.translatable(SeteruIds.k("layar.tokoh.tip"), t.getIntOr("jumpa", 0),
					Component.translatable(SeteruIds.k("senjata." + t.getIntOr("senjata", 0)))));
			if (t.getBooleanOr("dendam", false)) {
				tip.add(Component.translatable(SeteruIds.k("layar.tokoh.dendam")).withColor(Palette.DANGER & 0xFFFFFF));
			}
			l.tip(tip);
		}
		if (!SeteruIds.TOKOH_AKTIF.equals(t.getStringOr("status", SeteruIds.TOKOH_AKTIF))) {
			l.g.fill(x0, y0, x1, y1, 0x70000000);
			l.tengah(Component.translatable(SeteruIds.k("status_tokoh." + t.getStringOr("status", "").toLowerCase(Locale.ROOT))), (x0 + x1) / 2.0F, y0 + 16,
					x1 - x0 - 6, 0xFFE0C080);
		}
	}

	/** The things waiting: a duel or brawl, the ambush, the vow, the elder away, the patrol. */
	private static List<Component> menanti(CompoundTag sheet, CompoundTag f) {
		List<Component> out = new ArrayList<>();
		String scene = f.getStringOr("adegan", "");
		if (!scene.isEmpty()) {
			long hours = Math.max(0L, f.getLongOr("adeganSisa", 0L) / 1000L);
			out.add(Component.translatable(SeteruIds.k("layar.menanti." + scene), hours,
					Component.translatable(SeteruIds.k(f.getBooleanOr("dukung", false) ? "layar.didukung" : "layar.tanpa_dukungan"))));
		}
		if (f.getBooleanOr("sergap", false)) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.sergap")));
		}
		if (f.getLongOr("sumpah", 0L) > 0L) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.sumpah"), f.getLongOr("sumpah", 0L)));
		}
		if (f.getBooleanOr("tetua", false)) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.tetua")));
		}
		CompoundTag patrol = sheet.getCompoundOrEmpty("patroli");
		if (!patrol.isEmpty() && patrol.getStringOr("seteru", "").equals(f.getStringOr("id", ""))) {
			out.add(Component.translatable(SeteruIds.k("layar.menanti.patroli"), comp(patrol, "kota")));
		}
		return out;
	}

	/** The eight actions (four or two per row), or Restui / Pisahkan for lovers; each asks with a 1.5 s hold (R7). Returns the height. */
	private int aksi(Lukis l, CompoundTag f, boolean lover, int x0, int y, int x1) {
		ListTag reasons = f.getListOrEmpty("aksi");
		String id = f.getStringOr("id", "");
		String sekte = f.getStringOr("sekte", "");
		int[] ids = lover ? new int[] {SeteruIds.AKSI_RESTUI, SeteruIds.AKSI_PISAHKAN}
				: new int[] {SeteruIds.AKSI_DAMAIKAN, SeteruIds.AKSI_UTUS_TETUA, SeteruIds.AKSI_HUKUM, SeteruIds.AKSI_TUNTUT, SeteruIds.AKSI_DUKUNG,
						SeteruIds.AKSI_TANTANG_TETUA, SeteruIds.AKSI_LARANG, SeteruIds.AKSI_BIARKAN};
		int cols = lover ? 2 : Math.max(1, Math.min(4, (x1 - x0 + 3) / 70));
		int bw = (x1 - x0 - (cols - 1) * 3) / cols;
		Fokus fokus = l.fokusRing();
		for (int i = 0; i < ids.length; i++) {
			int a = ids[i];
			String name = a == SeteruIds.AKSI_RESTUI ? "restui" : a == SeteruIds.AKSI_PISAHKAN ? "pisahkan" : SeteruIds.AKSI[a];
			int bx = x0 + (i % cols) * (bw + 3);
			int by = y + (i / cols) * (TOMBOL_H + 3);
			String why = a < reasons.size() ? reasons.getStringOr(a, "") : "";
			Component alasan = why.isEmpty() ? null : Component.translatable(why);
			Component label = Component.translatable(SeteruIds.k("aksi." + name));
			boolean over = TalismanButton.gambar(l, "aksi:" + id + ":" + a, bx, by, bw, TOMBOL_H, label, alasan == null, alasan, () -> fokus.konfirmasi(
					SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(M + "yakin.seteru", label, Component.translatable(SeteruIds.k("aksi." + name + ".desc"))),
					() -> {
						UiSound.press();
						SeteruClient.minta(SeteruIds.OP_AKSI, id, a, sekte);
					}));
			if (over) {
				List<Component> tip = new ArrayList<>();
				tip.add(Brush.bold(label));
				tip.add(Component.translatable(SeteruIds.k("aksi." + name + ".desc")));
				if (alasan != null) {
					tip.add(alasan.copy().withColor(Palette.DANGER & 0xFFFFFF));
				}
				l.tip(tip);
			}
		}
		return ((ids.length + cols - 1) / cols) * (TOMBOL_H + 3);
	}

	private static int rinciSahabat(Lukis l, CompoundTag fr, int x0, int y, int x1) {
		y += l.kepala(Component.translatable(SeteruIds.k("layar.sahabat")), x0, y, x1);
		ikon(l.g, 31, x0, y, 14, 1.0F);
		l.teks(Brush.bold(Component.translatable(SeteruIds.k("layar.sahabat_baris"), comp(fr, "murid"), comp(fr, "tokoh"))), x0 + 18, y + 2, x1 - x0 - 18,
				l.gaya.teks);
		y += 16;
		l.teks(comp(fr, "sekte"), x0, y, x1 - x0, l.gaya.teksLemah);
		y += 11;
		int sikap = Mth.clamp(fr.getIntOr("sikap", 0), -100, 100);
		y += Bilah.baris(l, Component.translatable(SeteruIds.k("layar.sikap"), sikap), (sikap + 100) / 2.0F, 100.0F, x0, y, x1, Component.literal(Brush.bertanda(sikap)),
				sikap >= 0 ? Palette.JADE : Palette.SEAL);
		return y + 4;
	}

	private static int rinciPatroli(Lukis l, CompoundTag sheet, CompoundTag patrol, int x0, int y, int x1) {
		y += l.kepala(Component.translatable(SeteruIds.k("layar.patroli")), x0, y, x1);
		BlockPos pa = BlockPos.of(patrol.getLongOr("a", 0L));
		BlockPos pb = BlockPos.of(patrol.getLongOr("b", 0L));
		ikon(l.g, 26, x0, y, 16, 1.0F);
		y += (int) l.bungkus(Component.translatable(SeteruIds.k("layar.patroli_info"), comp(patrol, "sekte"), pa.getX(), pa.getZ(), pb.getX(), pb.getZ(),
				comp(patrol, "kota")), x0 + 20, y, x1 - x0 - 20, l.gaya.teks, 72) + 4;
		long usir = patrol.getLongOr("usir", 0L) - sheet.getLongOr("hari", 0L);
		if (usir > 0L) {
			l.teks(Component.translatable(SeteruIds.k("layar.patroli_usir"), usir), x0, y, x1 - x0, Palette.JADE_DARK);
			y += 11;
		}
		return y;
	}

	// ================================================================== drawing helpers

	private static int panasWarna(int heat) {
		return Palette.mix(0xFFE8C060, 0xFFB01810, Mth.clamp(heat / 100.0F, 0.0F, 1.0F));
	}

	private static int statusWarna(String status) {
		return switch (status) {
			case SeteruIds.DAMAI -> Palette.JADE_DARK;
			case SeteruIds.SAINGAN -> Palette.GOLD_DARK;
			case SeteruIds.DENDAM -> Palette.DANGER;
			case SeteruIds.TERLARANG, SeteruIds.KAWIN_LARI -> 0xFFC0406A;
			default -> Palette.INK_FAINT;
		};
	}

	/** One 16 x 16 cell of the icon atlas (8 columns x 4 rows) drawn at {@code size}. */
	static void ikon(GuiGraphicsExtractor g, int cell, int x, int y, int size, float alpha) {
		int c = Math.floorMod(cell, 32);
		g.blit(RenderPipelines.GUI_TEXTURED, IKON, x, y, (c % 8) * 16.0F, (c / 8) * 16.0F, size, size, 16, 16, 128, 64,
				Palette.argb(Math.round(alpha * 255.0F), 0xFFFFFF));
	}

	/** The sect's emblem (the kit's emblem painter), dimmed by {@code alpha}. */
	private static void lambang(Lukis l, int packed, int cx, int cy, float r, float alpha) {
		IkonGabung.lambang(l.g, l.font, packed, cx, cy, r);
		if (alpha < 1.0F) {
			Brush.disc(l.g, cx, cy, r + 0.5F, Palette.argb(Math.round((1.0F - alpha) * 160), Palette.PAPER_TOP & 0xFFFFFF));
		}
	}

	/** A history line: its key and stored arguments (names, nested sect names, towns, numbers). */
	private static Component baris(CompoundTag line) {
		ListTag args = line.getListOrEmpty("arg");
		Object[] parts = new Object[args.size()];
		for (int i = 0; i < parts.length; i++) {
			parts[i] = arg(args.getStringOr(i, ""));
		}
		return Component.translatable(SeteruIds.k(line.getStringOr("kunci", "")), parts);
	}

	static Object arg(String a) {
		if (a == null || a.isEmpty()) {
			return "";
		}
		try {
			if (a.startsWith("n:")) {
				return a.substring(2);
			}
			if (a.startsWith("@kota_nama:")) {
				return KotaNames.town(Long.parseLong(a.substring(11)));
			}
			if (a.indexOf('|') > 0 && a.startsWith("kultivator.")) {
				String[] part = a.split("\\|");
				Object[] inner = new Object[part.length - 1];
				for (int i = 1; i < part.length; i++) {
					inner[i - 1] = arg(part[i]);
				}
				return Component.translatable(part[0], inner);
			}
			if (a.indexOf(' ') < 0 && a.indexOf('.') > 0 && (a.startsWith("kultivator.") || a.startsWith("entity."))) {
				return Component.translatable(a);
			}
		} catch (RuntimeException exception) {
			return a;
		}
		return a;
	}

	// ================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event) || gulir.tombol(event);
	}

	@Override
	public boolean mundur() {
		if (rinciSempit && tata() == Tata.SEMPIT) {
			rinciSempit = false;
			return true;
		}
		return false;
	}
}
