package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Saring;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.kepribadian.SuasanaScreen;
import dev.kultivator.client.sekte.tangga.TanggaCatatanScreen;
import dev.kultivator.data.Rank;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Murid › Daftar (DESIGN 43.5; was the "Jalan Hidup Murid" board of DESIGN 39.7.4, a card of section MURID): every disciple
 * of the hall as one page of the Gulungan Utama, with three subs:
 * <ul>
 * <li><b>ringkas</b>: every disciple (loaded, travelling, or last seen) with its path seal, name, what it does or where it
 * is, realm, priority-1 goal with a thin bar and purse; stripes for tempted, wounded, ready, travelling; a {@link Saring}
 * row (search + kind chips) instead of the old filter buttons. One click (or Enter) opens the disciple on Murid › Murid ›
 * jalan_hidup when the body is in reach, else its diary (R18);</li>
 * <li><b>suasana</b>: the hearts of the loaded disciples ({@link SuasanaScreen}, was its own screen, R15);</li>
 * <li><b>tangga</b>: the Tangga Seribu records ({@link TanggaCatatanScreen}), shown only while module G runs.</li>
 * </ul>
 * The board's other three views live in their homes (43.11): Kabar in Sekte › Ikhtisar › catatan ({@link #kabar}), Kebijakan
 * Aula in Sekte › Ikhtisar › identitas, Kas Mandiri in Sekte › Kas & Toko › kas (they read {@link #papan()}).
 * <p>
 * Data from {@code mandiri_papan} ({@code PapanMandiri.bangun}), asked for when the page shows and every 5 s while it shows
 * (43.0.12); the server answers every policy press with a fresh board. The board's rows are also the soul/name → body map
 * behind {@link #bukaMurid} for the other lists of section Murid.
 */
public final class MandiriBoardScreen extends Page {

	static final String L = "kultivator.sekte.mandiri.cerita.";
	private static final String M = "kultivator.gui43.m04.";
	private static final String S = "kultivator.gui43.sub.daftar.";
	private static final int ROW_H = 22;
	private static final long REFRESH_MILLIS = 5000L;

	public static final String SUB_RINGKAS = "ringkas";
	public static final String SUB_SUASANA = "suasana";
	public static final String SUB_TANGGA = "tangga";

	static final int F_PERGI = 1;
	static final int F_LUKA = 2;
	static final int F_SIAP = 4;
	static final int F_TERGODA = 8;
	static final int F_TUTUP = 16;
	static final int F_PERINTAH = 32;
	static final int F_DIMUAT = 64;
	static final int F_JAUH = 128;

	private static final String[] SARING = {"pergi", "luka", "siap", "tergoda"};
	private static final int[] SARING_BIT = {F_PERGI, F_LUKA, F_SIAP, F_TERGODA};

	private static @Nullable CompoundTag terbaru;
	private static long versi;
	private static long dimintaAt;

	private String sub = SUB_RINGKAS;
	private long askedAt;
	private long jenisVersi = -1L;
	private final DaftarGulir daftar = new DaftarGulir();
	private final Saring saring = new Saring(() -> { });
	private final SuasanaScreen suasana = new SuasanaScreen();
	private final TanggaCatatanScreen tangga = new TanggaCatatanScreen();

	public MandiriBoardScreen() {
		super();
	}

	// ================================================================== data (public for the other pages)

	static void terima(CompoundTag data) {
		terbaru = data;
		versi++;
		if (data.getBooleanOr("buka", false)) {
			// the chat [Lihat] link or the command
			HubNav.buka(Tujuan.of(HubNav.MURID, Urutan.ID_DAFTAR).sub(SUB_RINGKAS));
		}
	}

	static void bersih() {
		terbaru = null;
		versi++;
	}

	/** The last board ({@code PapanMandiri.bangun}: rows, kabar, kb, kas, saku7, pool, hari, jam, on, hall, own), or null. */
	public static @Nullable CompoundTag papan() {
		return terbaru;
	}

	/** Asks the server for the board (at most once a second). */
	public static void minta() {
		long now = Util.getMillis();
		if (now - dimintaAt >= 1000L) {
			dimintaAt = now;
			MandiriClient.aksi("papan", "", "", 0);
		}
	}

	/** Why a board view has nothing to show (loading, module off, no hall), or null when {@code data} can be shown. */
	public static @Nullable Component alasanKosong(@Nullable CompoundTag data) {
		if (data == null) {
			return Component.translatable(L + "papan.memuat");
		}
		if (!data.getBooleanOr("on", false)) {
			return Component.translatable(L + "tab.mati");
		}
		if (!data.getBooleanOr("hall", false)) {
			return Component.translatable(L + "papan.tanpa_aula");
		}
		return null;
	}

	/** The hall's news as chronicle entries (oldest first, kind "murid"; important ones in seal red) for {@code Kronik}. */
	public static List<Kronik.Entri> kabar(@Nullable CompoundTag data) {
		List<Kronik.Entri> out = new ArrayList<>();
		if (data == null) {
			return out;
		}
		ListTag news = data.getListOrEmpty("kabar");
		// the server sends the newest first
		for (int i = news.size() - 1; i >= 0; i--) {
			CompoundTag n = news.getCompoundOrEmpty(i);
			boolean important = n.getBooleanOr("p", false);
			Component text = n.read("t", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component line = Component.literal(String.format(Locale.ROOT, "%02d:00 ", n.getIntOr("j", 0))).withColor(Palette.INK_FAINT).append(text);
			out.add(new Kronik.Entri(line, n.getLongOr("h", 0L) + 1L, "murid", important ? Palette.SEAL : Palette.JADE));
		}
		return out;
	}

	/**
	 * Opens a disciple of the hall on Murid › Murid › {@code sub}: through the server (as a click on the body would) when the
	 * body is loaded and in reach, else on its diary (Murid › Murid › buku_harian, focus {@code {"jiwa": soul}}).
	 */
	public static void bukaMurid(String soul, int eid, boolean jauh, String sub) {
		if (eid >= 0 && !jauh) {
			HubNav.ingatSub(Urutan.ID_MURID, sub);
			Keys.send(new Payloads.Action(Actions.DISCIPLE_OPEN, eid, ""));
			return;
		}
		CompoundTag fokus = new CompoundTag();
		if (soul != null && !soul.isEmpty()) {
			MandiriClient.mintaHalaman(soul);
			fokus.putString("jiwa", soul);
		}
		if (eid >= 0) {
			fokus.putInt("murid", eid);
		}
		HubNav.buka(Tujuan.of(HubNav.MURID, Urutan.ID_MURID).sub("buku_harian").fokus(fokus));
	}

	/** {@link #bukaMurid(String, int, boolean, String)} for a soul id, its body looked up in the board. */
	public static void bukaMurid(String soul, String sub) {
		CompoundTag row = baris(r -> soul != null && soul.equals(r.getStringOr("soul", "")));
		if (row != null) {
			bukaMurid(soul, row.getIntOr("eid", -1), (row.getIntOr("f", 0) & F_JAUH) != 0, sub);
		} else {
			bukaMurid(soul, -1, true, sub);
		}
	}

	private static @Nullable CompoundTag baris(java.util.function.Predicate<CompoundTag> cocok) {
		CompoundTag data = terbaru;
		if (data == null) {
			return null;
		}
		ListTag rows = data.getListOrEmpty("rows");
		for (int i = 0; i < rows.size(); i++) {
			CompoundTag row = rows.getCompoundOrEmpty(i);
			if (cocok.test(row)) {
				return row;
			}
		}
		return null;
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable(M + "daftar.judul");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.gui43.hal.murid.daftar");
	}

	@Override
	public int glyph() {
		return Glyphs.JALAN;
	}

	@Override
	public String helpTopic() {
		// Suasana and Tangga are parts of "Sekte yang Hidup"; the list itself is "Jalan Hidup" (mandiri)
		return SUB_SUASANA.equals(sub) || SUB_TANGGA.equals(sub) ? "sekte_hidup" : "mandiri";
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>();
		subs.add(new Sub(SUB_RINGKAS, Component.translatable(S + SUB_RINGKAS)));
		subs.add(new Sub(SUB_SUASANA, Component.translatable(S + SUB_SUASANA)));
		if (TanggaCatatanScreen.ada()) {
			subs.add(new Sub(SUB_TANGGA, Component.translatable(S + SUB_TANGGA)));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		sub = id;
		if (SUB_SUASANA.equals(id)) {
			SuasanaScreen.minta();
		} else if (SUB_TANGGA.equals(id)) {
			TanggaCatatanScreen.minta();
		}
	}

	// ================================================================== life

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		askedAt = Util.getMillis();
		minta();
		super.onShow(fokus);
	}

	@Override
	public void tick() {
		long now = Util.getMillis();
		if (now - askedAt > REFRESH_MILLIS) {
			askedAt = now;
			minta();
		}
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		int x1 = x + w;
		int y1 = y + h;
		switch (sub) {
			case SUB_SUASANA -> suasana.gambar(l, "suasana", x, y, x1, y1);
			case SUB_TANGGA -> tangga.gambar(l, "tangga", x, y, x1, y1);
			default -> {
				CompoundTag data = terbaru;
				Component empty = alasanKosong(data);
				if (empty != null) {
					KeadaanKosong.gambar(l, data == null ? KeadaanKosong.Gambar.LENTERA : KeadaanKosong.Gambar.GULUNGAN, empty, null, null, x, y, x1, y1);
					return;
				}
				ringkas(l, data, x1, y1);
			}
		}
	}

	// ================================================================== ringkas

	private void ringkas(Lukis l, CompoundTag data, int x1, int y1) {
		ListTag rows = data.getListOrEmpty("rows");
		if (jenisVersi != versi) {
			jenisVersi = versi;
			List<Saring.Pilihan> jenis = new ArrayList<>();
			for (int i = 0; i < SARING.length; i++) {
				int count = 0;
				for (int r = 0; r < rows.size(); r++) {
					if ((rows.getCompoundOrEmpty(r).getIntOr("f", 0) & SARING_BIT[i]) != 0) {
						count++;
					}
				}
				jenis.add(new Saring.Pilihan(SARING[i], Component.translatable(L + "papan.saring." + SARING[i], count)));
			}
			saring.jenis(jenis);
		}
		int top = y + saring.gambar(l, "saring", x, y, x1) + 2;
		Component day = Component.translatable(L + "papan.hari", data.getLongOr("hari", 0L) + 1, data.getIntOr("jam", 0));
		l.samar(day, x, top, w);
		top += 11;
		List<CompoundTag> shown = new ArrayList<>();
		for (int r = 0; r < rows.size(); r++) {
			CompoundTag row = rows.getCompoundOrEmpty(r);
			Component name = row.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			if (saring.kolom().cocok(name) && lolos(row.getIntOr("f", 0), saring.jenisAktif())) {
				shown.add(row);
			}
		}
		shown.sort((p, q) -> Integer.compare(urut(q), urut(p)));
		if (shown.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable(rows.isEmpty() ? L + "papan.kosong" : L + "papan.saring_kosong"),
					null, null, x, top, x1, y1);
			return;
		}
		boolean sempit = tata() == Tata.SEMPIT;
		if (!sempit) {
			kolom(l, x + 24, top, x1);
			top += 11;
		}
		daftar.gambar(l, "daftar", x, top, x1, y1, shown.size(), ROW_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) ->
				barisRingkas(lk, i, shown.get(i), rx0, ry0, rx1, ry1, sorot, pilih, sempit), () -> {
			int p = daftar.pilihan();
			if (p >= 0 && p < shown.size()) {
				bukaBaris(shown.get(p));
			}
		}, null);
	}

	/** The column titles of the medium and wide layouts. */
	private void kolom(Lukis l, int tx, int y0, int x1) {
		int cw = x1 - tx;
		int[] at = {tx, tx + cw * 30 / 100, tx + cw * 62 / 100, tx + cw * 78 / 100, x1};
		String[] keys = {"nama", "kegiatan", "ranah", "cita"};
		for (int i = 0; i < keys.length; i++) {
			l.teks(Component.translatable(L + "papan.kol." + keys[i]), at[i], y0, at[i + 1] - at[i] - 4, Palette.SEAL_DARK);
		}
		Brush.rect(l.g, x, y0 + 9, x1, y0 + 9.6F, Palette.argb(0x80, Palette.GOLD_DARK));
	}

	private void barisRingkas(Lukis l, int i, CompoundTag row, int rx0, int ry0, int rx1, int ry1, boolean sorot, boolean pilih, boolean sempit) {
		GuiGraphicsExtractor g = l.g;
		int f = row.getIntOr("f", 0);
		l.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, pilih);
		int stripe = (f & F_TERGODA) != 0 ? JalanHidup.IBLIS.warna() : (f & F_LUKA) != 0 ? Palette.DANGER : (f & F_SIAP) != 0 ? Palette.GOLD
				: (f & F_PERGI) != 0 ? Palette.QI : Palette.JADE_DULL;
		g.fill(rx0, ry0 + 1, rx0 + 2, ry1 - 1, Palette.opaque(stripe));
		JalanHidup jalan = JalanHidup.byId(row.getStringOr("j", ""));
		if (jalan != null) {
			Ikon.jalan(g, jalan, rx0 + 4, ry0 + 2, 17, (f & F_DIMUAT) != 0 || (f & F_PERGI) != 0 ? 0xFF : 0xA0);
		}
		Component name = row.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		boolean last = (f & (F_DIMUAT | F_PERGI)) == 0;
		Component shownName = last ? name.copy().withStyle(ChatFormatting.ITALIC) : Brush.bold(name);
		Component act = kegiatan(row);
		Kegiatan k = Kegiatan.byId(row.getStringOr("k", ""));
		boolean actIcon = (f & F_PERGI) == 0 && k != null && k != Kegiatan.KOSONG;
		Component flags = bendera(row);
		boolean adaFlags = !flags.getString().isEmpty();
		int step = row.getIntOr("r", 0);
		Component realm = Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, step))).displayName();
		JenisCita cita = JenisCita.byId(row.getStringOr("c", ""));
		int prog = Math.max(0, Math.min(100, row.getIntOr("cp", 0)));
		Component purse = NilaiBatu.text(row.getLongOr("d", 0L));
		int tx = rx0 + 24;
		int line1 = ry0 + 2;
		int line2 = ry0 + 12;
		if (sempit) {
			int rw = Math.min(l.font.width(realm), Math.round((rx1 - tx) * 0.38F));
			l.teks(shownName, tx, line1, rx1 - tx - rw - 6, l.gaya.teks);
			l.teks(realm, rx1 - 2 - rw, line1, rw, l.gaya.teksLemah);
			int ax = tx;
			if (actIcon) {
				Ikon.kegiatan(g, k, tx, line2, 9, 0xFF);
				ax += 11;
			}
			int fw = adaFlags ? Math.min(l.font.width(flags), Math.round((rx1 - tx) * 0.40F)) : 0;
			l.teks(act, ax, line2, rx1 - ax - fw - 6, l.gaya.teksLemah);
			if (fw > 0) {
				l.teks(flags, rx1 - 2 - fw, line2, fw, Palette.SEAL_DARK);
			}
		} else {
			int cw = rx1 - tx;
			int colAct = tx + cw * 30 / 100;
			int colRealm = tx + cw * 62 / 100;
			int colGoal = tx + cw * 78 / 100;
			l.teks(shownName, tx, line1, colAct - tx - 4, l.gaya.teks);
			Component path = jalan == null ? Component.translatable(L + "tab.jalan_belum") : Component.translatable(jalan.kunciNama());
			l.teks(path, tx, line2, colAct - tx - 4, jalan == null ? Palette.INK_FAINT : Palette.onPaper(jalan.warna()));
			int ax = colAct;
			if (actIcon) {
				Ikon.kegiatan(g, k, colAct, line1, 9, 0xFF);
				ax += 11;
			}
			l.teks(act, ax, line1, colRealm - ax - 4, l.gaya.teks);
			if (adaFlags) {
				l.teks(flags, colAct, line2, colRealm - colAct - 4, Palette.SEAL_DARK);
			}
			l.teks(realm, colRealm, line1, colGoal - colRealm - 4, l.gaya.teksLemah);
			l.teks(purse, colRealm, line2, colGoal - colRealm - 4, Palette.GOLD_DARK);
			if (cita != null) {
				l.teks(Component.translatable(cita.kunciNama()), colGoal, line1, rx1 - colGoal - 2, l.gaya.teks);
				int gx1 = rx1 - 4;
				Brush.rect(g, colGoal, line2 + 3, gx1, line2 + 5, Palette.argb(0x60, Palette.PAPER_SHADE));
				Brush.rect(g, colGoal, line2 + 3, colGoal + (gx1 - colGoal) * prog / 100.0F, line2 + 5, Palette.JADE);
			} else {
				l.samar(Component.literal("-"), colGoal, line1, 20);
			}
		}
		// one click opens the disciple (the list's own row zone only selects)
		l.zona("daftar#b" + i, rx0, ry0, rx1, ry1, () -> {
			UiSound.press();
			bukaBaris(row);
		}, true, null, false);
		if (sorot) {
			int eid = row.getIntOr("eid", -1);
			List<Component> lines = new ArrayList<>();
			lines.add(name.copy().withStyle(ChatFormatting.GOLD));
			lines.add(act);
			if (adaFlags) {
				lines.add(flags);
			}
			if (cita != null) {
				lines.add(Component.translatable(cita.kunciNama()).append(" " + prog + "%").withStyle(ChatFormatting.GRAY));
			}
			lines.add(Component.translatable(L + "papan.kol.dompet").append(": ").append(purse).withStyle(ChatFormatting.GRAY));
			if (last) {
				lines.add(Component.translatable(L + "papan.terakhir_dilihat").withStyle(ChatFormatting.GRAY));
			}
			boolean dekat = eid >= 0 && (f & F_JAUH) == 0;
			lines.add(Component.translatable(dekat ? L + "papan.klik_buka" : L + "papan.klik_buku").withStyle(ChatFormatting.DARK_GRAY));
			l.tip(lines);
		}
	}

	private static void bukaBaris(CompoundTag row) {
		bukaMurid(row.getStringOr("soul", ""), row.getIntOr("eid", -1), (row.getIntOr("f", 0) & F_JAUH) != 0, "jalan_hidup");
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

	/** The kind chip {@code jenis} ({@link Saring#SEMUA} or one of {@link #SARING}) against the row flags. */
	private static boolean lolos(int f, String jenis) {
		for (int i = 0; i < SARING.length; i++) {
			if (SARING[i].equals(jenis)) {
				return (f & SARING_BIT[i]) != 0;
			}
		}
		return true;
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
}
