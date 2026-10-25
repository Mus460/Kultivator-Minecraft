package dev.kultivator.client.sekte.diplomasi;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
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
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.sekte.wilayah.GulirIsi;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.diplomasi.DiplomasiPayloads;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Diplomasi Sekte (DESIGN 36.2.9) as pages of Sekte › Luar (43.5, 43.12.1: one class, three uses):
 * <ul>
 * <li>{@link Mode#PERANG}: the hall's wars and hostile sects (war facts, score, Tawarkan Damai / Tuntut Upeti) with the chip
 * Sumber Aulaku / Perguruanku; Perguruanku is tab Perang of the NPC sect's politics sheet ({@code gabung_politik});</li>
 * <li>{@link Mode#UTUSAN}: the envoys' open offers with Terima / Tolak;</li>
 * <li>{@link #blokDiplomasi}: the diplomacy block of one sect in the detail of Sekte Dunia (standing gauge, strength, facts,
 * the eleven actions, the sect's chronicle).</li>
 * </ul>
 * Heavy actions ask through {@code SegelKonfirmasi} (R7): breaking a pact, demanding tribute and a tournament challenge
 * hold 1.5 s, declaring war is heavy. Data come from {@link DiplomasiClient}; a page asks for a fresh sheet each time it is
 * shown and reads "memuat…" until it arrives (R16). Text is 1.0, the layout follows the content box (R8: no shrinking).
 */
public final class DiplomasiScreen extends Page {

	/** Which use of the class this page is. */
	public enum Mode { PERANG, UTUSAN }

	private static final String KEY = "kultivator.sekte.diplomasi.";
	private static final String M = "kultivator.gui43.m11.";
	/** Stance ids of the sheet ({@code Relasi.Sikap}), war to sworn ally, and their colours. */
	public static final String[] STANCES = {"perang", "bermusuhan", "dingin", "netral", "ramah", "sekutu"};
	public static final int[] STANCE_RGB = {0x8B1A1A, 0xC0392B, 0xE5821C, 0x7A8C83, 0x2E8963, 0xD4A637};
	/** Action indices of {@link DiplomasiPayloads#ACTIONS}. */
	private static final int A_PUTUS = 6;
	private static final int A_TUNTUT = 7;
	private static final int A_TANTANG = 8;
	private static final int A_PERANG = 9;
	private static final int A_DAMAI = 10;
	/** Offer kinds of {@link DiplomasiPayloads#OFFER_KINDS}. */
	private static final int O_TANTANG = 3;
	private static final int O_DAMAI = 4;
	private static final int O_TUNTUT = 5;
	private static final int O_KETUA = 6;
	private static final int BARIS = 22;
	private static final int TOMBOL_H = 14;

	/** 0 Aulaku, 1 Perguruanku (session memory of the chip). */
	private static int sumber;

	private final Mode mode;
	private final DaftarGulir daftar = new DaftarGulir();
	private final GulirIsi gulir = new GulirIsi();
	private long versiDiminta = Long.MIN_VALUE;
	private @Nullable String pilih;
	private boolean rinciSempit;
	private boolean ditampilkan;
	private @Nullable LembarHub.Halaman politik;
	private String politikLaid = "";
	private int modeTampil = -1;

	public DiplomasiScreen(Mode mode) {
		this.mode = mode;
	}

	// ================================================================== sheet access (also for Sekte Dunia)

	/** The sects of a diplomacy sheet. */
	public static List<CompoundTag> sekte(@Nullable CompoundTag sheet) {
		if (sheet == null) {
			return List.of();
		}
		ListTag list = sheet.getListOrEmpty("sects");
		List<CompoundTag> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getCompoundOrEmpty(i));
		}
		return out;
	}

	/** The sect {@code id} of a diplomacy sheet, or null. */
	public static @Nullable CompoundTag cari(@Nullable CompoundTag sheet, @Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (CompoundTag s : sekte(sheet)) {
			if (id.equals(s.getStringOr("id", ""))) {
				return s;
			}
		}
		return null;
	}

	/** The stance index (0 war .. 5 ally) of a sheet sect. */
	public static int sikap(CompoundTag s) {
		String id = s.getStringOr("sikap", "netral");
		for (int i = 0; i < STANCES.length; i++) {
			if (STANCES[i].equals(id)) {
				return i;
			}
		}
		return 3;
	}

	/** The stance name of index {@code i}, in its colour. */
	public static Component namaSikap(int i) {
		int k = Mth.clamp(i, 0, STANCES.length - 1);
		return Component.translatable("kultivator.sekte.sikap." + STANCES[k]).withColor(STANCE_RGB[k]);
	}

	private static Component comp(CompoundTag tag, String key, Component fallback) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(fallback);
	}

	private static Component nama(CompoundTag s) {
		return comp(s, "name", Component.literal(s.getStringOr("id", "?")));
	}

	// ================================================================== sources (Perang)

	private static boolean aula() {
		CompoundTag d = DiplomasiClient.data();
		return d != null ? d.getBooleanOr("hall", false) : LuarPage.punyaAula();
	}

	private static boolean perguruan() {
		return GabungClient.lencanaSendiri() != null;
	}

	/** A world opener of the politics war tab: show the Perguruanku source. */
	static void sumberPerguruan() {
		sumber = 1;
	}

	/** 0 Aulaku, 1 Perguruanku: the chip when both, else the one that exists. */
	private int modeSumber() {
		if (mode != Mode.PERANG) {
			return 0;
		}
		boolean a = aula();
		boolean p = perguruan();
		return a && p ? sumber : a ? 0 : p ? 1 : 0;
	}

	/** The politics sheet on its war tab (Perguruanku), mounted on first use without a registration. */
	private LembarHub.Halaman politik() {
		if (politik == null) {
			politik = LembarHub.dari(new HubPages.Entri(HubNav.SEKTE, Urutan.ID_LUAR, Urutan.LUAR, () -> null, GabungLembar.Politik.LAYAR,
					Set.of(GabungLembar.Politik.TAB_PERANG)));
		}
		return politik;
	}

	/** The page shown in place of this one (Perguruanku), or null. */
	private @Nullable Page anak() {
		return modeSumber() == 1 ? politik() : null;
	}

	/** Keeps the child page laid into this box and shown/hidden with the chip. */
	private void sesuaikanAnak() {
		int m = modeSumber();
		if (m != modeTampil) {
			if (ditampilkan && modeTampil == 1 && politik != null) {
				politik.onHide();
			}
			modeTampil = m;
			if (ditampilkan && m == 1) {
				politik().onShow(null);
			}
		}
		Page p = anak();
		if (p == null || w <= 0) {
			return;
		}
		if (screen != null) {
			p.pasang(screen);
		}
		String box = x + "," + y + "," + w + "," + h;
		if (!box.equals(politikLaid)) {
			politikLaid = box;
			p.layout(x, y, w, h);
		}
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.sub." + Urutan.ID_LUAR + "." + (mode == Mode.PERANG ? LuarPage.SUB_PERANG : LuarPage.SUB_UTUSAN));
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		Page p = anak();
		return p != null ? p.helpTopic() : "diplomasi_sekte";
	}

	@Override
	public @Nullable Sumber sumber() {
		if (mode != Mode.PERANG || !aula() || !perguruan()) {
			return null;
		}
		return new Sumber(List.of(Component.translatable(M + "sumber.aula"), Component.translatable(M + "sumber.perguruan")), () -> sumber, i -> {
			sumber = Mth.clamp(i, 0, 1);
			sesuaikanAnak();
		});
	}

	@Override
	public boolean rinci() {
		Page p = anak();
		return p != null ? p.rinci() : rinciSempit && tata() == Tata.SEMPIT;
	}

	@Override
	public Palette.Gaya gaya() {
		Page p = anak();
		return p != null ? p.gaya() : Palette.Gaya.KERTAS;
	}

	@Override
	public boolean lebar() {
		Page p = anak();
		return p != null && p.lebar();
	}

	// ================================================================== life

	@Override
	protected void build() {
		politikLaid = "";
		sesuaikanAnak();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		ditampilkan = true;
		modeTampil = -1;
		sesuaikanAnak();
		// R16: always a fresh sheet; the page reads "memuat…" until it is here
		versiDiminta = DiplomasiClient.versi();
		DiplomasiClient.minta(pilih);
		daftar.mulaiUlang(net.minecraft.util.Util.getMillis());
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		ditampilkan = false;
		if (modeTampil == 1 && politik != null) {
			politik.onHide();
		}
		modeTampil = -1;
	}

	@Override
	public void fokus(CompoundTag fokus) {
		String s = fokus.getStringOr("sekte", "");
		if (!s.isEmpty()) {
			pilih = s;
			rinciSempit = true;
		}
	}

	@Override
	public void tick() {
		sesuaikanAnak();
		Page p = anak();
		if (p != null) {
			p.tick();
		}
	}

	private boolean memuat() {
		return DiplomasiClient.versi() <= versiDiminta && DiplomasiClient.tersedia();
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		Page p = anak();
		if (p != null) {
			p.render(g, mouseX, mouseY, seconds);
		}
	}

	@Override
	protected void lukis(Lukis l) {
		if (anak() != null) {
			return;
		}
		int x0 = x + 2;
		int x1 = x + w - 2;
		int y0 = y + 2;
		int y1 = y + h - 2;
		CompoundTag sheet = DiplomasiClient.data();
		if (!DiplomasiClient.tersedia()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(KEY + "layar.tanpa_server"), null, null, x0, y0, x1, y1);
			return;
		}
		if (sheet == null || memuat()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable(M + "memuat"), null, null, x0, y0, x1, y1);
			return;
		}
		if (mode == Mode.PERANG && !sheet.getBooleanOr("hall", false)) {
			Component teks = Component.translatable(perguruan() ? M + "perang.tanpa_aula_sekte" : KEY + "layar.tanpa_aula");
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, teks, null, null, x0, y0, x1, y1);
			return;
		}
		int y = y0;
		y += kepalaAula(l, sheet, x0, y, x1);
		if (mode == Mode.UTUSAN) {
			utusan(l, sheet, x0, y, x1, y1);
		} else {
			perang(l, sheet, x0, y, x1, y1);
		}
	}

	/** The hall line over both modes; returns the height used. */
	private int kepalaAula(Lukis l, CompoundTag sheet, int x0, int y, int x1) {
		if (!sheet.getBooleanOr("hall", false)) {
			l.teks(Component.translatable(KEY + "layar.tanpa_aula"), x0, y, x1 - x0, Palette.DANGER);
			return 12;
		}
		l.samar(Component.translatable(KEY + "layar.aula", sheet.getIntOr("hall_level", 0), Brush.angka(sheet.getIntOr("kekuatan", 0)),
				Brush.angka(sheet.getIntOr("pool", 0))), x0, y, x1 - x0);
		l.garis(x0, x1, y + 11);
		return 15;
	}

	// ------------------------------------------------------------------ Utusan

	private void utusan(Lukis l, CompoundTag sheet, int x0, int y0, int x1, int y1) {
		ListTag list = sheet.getListOrEmpty("offers");
		List<CompoundTag> offers = new ArrayList<>(list.size());
		for (int i = list.size() - 1; i >= 0; i--) {
			offers.add(list.getCompoundOrEmpty(i));
		}
		if (offers.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable(M + "utusan.kosong"), null, null, x0, y0, x1, y1);
			return;
		}
		int fokusOffer = sheet.getIntOr("offer", 0);
		Fokus f = l.fokusRing();
		daftar.gambar(l, "tawaran", x0, y0, x1, y1, offers.size(), 26, (lk, i, rx0, ry0, rx1, ry1, sorot, dipilih) -> {
			CompoundTag o = offers.get(i);
			int id = o.getIntOr("id", 0);
			lk.kartu(rx0, ry0, rx1, ry1, sorot ? 1.0F : 0.0F, dipilih || id == fokusOffer);
			CompoundTag s = cari(sheet, o.getStringOr("sect", ""));
			int kind = Mth.clamp(o.getIntOr("kind", 0), 0, DiplomasiPayloads.OFFER_KINDS.length - 1);
			String jenis = DiplomasiPayloads.OFFER_KINDS[kind];
			Component terima = Component.translatable(KEY + "layar.terima");
			Component tolak = Component.translatable(KEY + "layar.tolak");
			int tw = TalismanButton.lebar(lk.font, terima, 40, 70);
			int kw = TalismanButton.lebar(lk.font, tolak, 36, 64);
			int bx = rx1 - 3 - kw - 3 - tw;
			int tx = rx0 + 22;
			if (s != null) {
				IkonGabung.lambang(lk.g, lk.font, s.getIntOr("lambang", Lambang.DEFAULT.pack()), rx0 + 11.0F, ry0 + 12.0F, 8.0F);
				lk.teks(Brush.bold(nama(s)), tx, ry0 + 3, bx - tx - 4, lk.gaya.teks);
			}
			long menit = Math.max(1L, (o.getLongOr("left", 0L) + 1199L) / 1200L);
			Component bawah = Component.translatable(KEY + "tawaran." + jenis).append(" · ").append(Component.translatable(KEY + "layar.sisa", menit));
			lk.teks(bawah, tx, ry0 + 14, bx - tx - 4, kind == O_TUNTUT || kind == O_KETUA ? Palette.DANGER : lk.gaya.teksLemah);
			if (lk.di(rx0, ry0, bx, ry1)) {
				lk.tip(List.of(Brush.bold(Component.translatable(KEY + "tawaran." + jenis)), Component.translatable(KEY + "tawaran." + jenis + ".tip")));
			}
			int by = ry0 + (ry1 - ry0 - TOMBOL_H) / 2;
			Component sekteNama = s == null ? Component.literal("?") : nama(s);
			TalismanButton.gambar(lk, "terima" + id, bx, by, tw, TOMBOL_H, terima, true, null, () -> {
				Runnable kirim = () -> DiplomasiClient.request(DiplomasiPayloads.OP_ANSWER, "", id, 1);
				if (kind == O_TANTANG || kind == O_TUNTUT) {
					f.konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(M + "yakin.terima." + jenis, sekteNama), kirim);
				} else {
					kirim.run();
				}
			});
			TalismanButton.gambar(lk, "tolak" + id, rx1 - 3 - kw, by, kw, TOMBOL_H, tolak, true, null, () -> {
				Runnable kirim = () -> DiplomasiClient.request(DiplomasiPayloads.OP_ANSWER, "", id, 0);
				if (kind == O_DAMAI || kind == O_TUNTUT) {
					f.konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(M + "yakin.tolak." + jenis, sekteNama), kirim);
				} else {
					kirim.run();
				}
			});
		}, () -> {
			int i = daftar.pilihan();
			if (i >= 0 && i < offers.size()) {
				LuarPage.bukaSekte(offers.get(i).getStringOr("sect", ""));
			}
		}, null);
	}

	// ------------------------------------------------------------------ Perang (Aulaku)

	/** Sects at war first, then the hostile ones. */
	private static List<CompoundTag> musuh(CompoundTag sheet) {
		List<CompoundTag> perang = new ArrayList<>();
		List<CompoundTag> benci = new ArrayList<>();
		for (CompoundTag s : sekte(sheet)) {
			if (s.getBooleanOr("perang", false)) {
				perang.add(s);
			} else if (sikap(s) <= 1) {
				benci.add(s);
			}
		}
		perang.addAll(benci);
		return perang;
	}

	private void perang(Lukis l, CompoundTag sheet, int x0, int y0, int x1, int y1) {
		int y = y0;
		CompoundTag raider = cari(sheet, sheet.getStringOr("penyerbu", ""));
		if (raider != null) {
			l.g.fill(x0, y, x1, y + 12, Palette.argb(0x50, Palette.SEAL));
			l.teks(Component.translatable(M + "perang.diserbu", nama(raider)), x0 + 3, y + 2, x1 - x0 - 6, Palette.SEAL_DARK);
			y += 15;
		}
		if (!sheet.getBooleanOr("perang_aktif", true)) {
			l.samar(Component.translatable(M + "perang.mati"), x0, y, x1 - x0);
			y += 12;
		}
		List<CompoundTag> list = musuh(sheet);
		if (list.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable(M + "perang.kosong"), Component.translatable(M + "ke_sekte_dunia"),
					() -> tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_LUAR).sub(LuarPage.SUB_SEKTE_DUNIA)), x0, y, x1, y1);
			return;
		}
		int at = indeks(list, pilih);
		if (at < 0) {
			at = 0;
			pilih = list.get(0).getStringOr("id", "");
		}
		if (daftar.pilihan() != at) {
			daftar.setPilih(at);
		}
		boolean sempit = tata() == Tata.SEMPIT;
		if (sempit && rinciSempit) {
			rinciPerang(l, sheet, list.get(at), x0, y, x1, y1);
			return;
		}
		int lx1 = sempit ? x1 : x0 + Math.round((x1 - x0) * 0.45F);
		daftar.gambar(l, "perang", x0, y, lx1, y1, list.size(), BARIS, (lk, i, rx0, ry0, rx1, ry1, sorot, dipilih) -> barisPerang(lk, list.get(i), rx0,
				ry0, rx1, ry1, sorot, dipilih), () -> rinciSempit = true, i -> {
			if (i >= 0 && i < list.size()) {
				pilih = list.get(i).getStringOr("id", "");
				gulir.atas();
			}
		});
		if (!sempit) {
			rinciPerang(l, sheet, list.get(at), lx1 + 6, y, x1, y1);
		}
	}

	private static int indeks(List<CompoundTag> list, @Nullable String id) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getStringOr("id", "").equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static void barisPerang(Lukis l, CompoundTag s, int x0, int y0, int x1, int y1, boolean sorot, boolean dipilih) {
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, dipilih);
		int st = sikap(s);
		l.g.fill(x0 + 1, y0 + 2, x0 + 3, y1 - 2, Palette.opaque(STANCE_RGB[st]));
		IkonGabung.lambang(l.g, l.font, s.getIntOr("lambang", Lambang.DEFAULT.pack()), x0 + 13.0F, y0 + 11.0F, 8.0F);
		boolean perang = s.getBooleanOr("perang", false);
		int kanan = x1 - 4;
		if (perang) {
			Component tanda = Component.literal("戰");
			kanan -= l.font.width(tanda);
			l.teks(tanda, kanan, y0 + 2, 12, Palette.DANGER);
			kanan -= 3;
		}
		l.teks(Brush.bold(nama(s)), x0 + 24, y0 + 2, kanan - x0 - 26, l.gaya.teks);
		Component bawah = perang ? Component.translatable(M + "perang.ringkas", s.getLongOr("perang_hari", 0L), s.getIntOr("menang", 0),
				s.getIntOr("kalah", 0)) : namaSikap(st);
		l.teks(bawah, x0 + 24, y0 + 12, x1 - x0 - 28, perang ? Palette.SEAL_DARK : l.gaya.teksSamar);
	}

	private void rinciPerang(Lukis l, CompoundTag sheet, CompoundTag s, int x0, int y0, int x1, int y1) {
		int top = y0;
		if (tata() == Tata.SEMPIT) {
			TombolKembali.gambar(l, "kembali", x0, y0, null, () -> rinciSempit = false);
			top += 16;
		}
		l.sumur(x0, top, x1, y1);
		int cx0 = x0 + 4;
		int cx1 = x1 - 8;
		int y = gulir.mulai(l, x0 + 1, top + 1, x1 - 6, y1 - 1) + 3;
		String id = s.getStringOr("id", "");
		IkonGabung.lambang(l.g, l.font, s.getIntOr("lambang", Lambang.DEFAULT.pack()), cx0 + 12.0F, y + 12.0F, 11.0F);
		l.teks(Brush.bold(nama(s)), cx0 + 28, y + 2, cx1 - cx0 - 28, l.gaya.teks);
		l.teks(comp(s, "master", Component.empty()), cx0 + 28, y + 13, cx1 - cx0 - 28, l.gaya.teksLemah);
		y += 28;
		y += gauge(l, s, cx0, y, cx1);
		if (s.getBooleanOr("perang", false)) {
			y += (int) l.bungkus(Component.translatable(KEY + "layar.perang", s.getLongOr("perang_hari", 0L), s.getIntOr("menang", 0), s.getIntOr("kalah", 0), 3),
					cx0, y, cx1 - cx0, Palette.SEAL_DARK, 27) + 2;
			// three raids repelled win the war: the score as a bar
			y += Bilah.baris(l, Component.translatable(M + "perang.menang"), Math.min(3, s.getIntOr("menang", 0)), 3, cx0, y, cx1, null, Palette.JADE);
			y += Bilah.baris(l, Component.translatable(M + "perang.kalah"), Math.min(3, s.getIntOr("kalah", 0)), 3, cx0, y, cx1, null, Palette.SEAL) + 3;
		}
		y += kekuatan(l, sheet, s, cx0, y, cx1);
		// the war actions of this sect (the rest of the diplomacy is in Sekte Dunia)
		y += 3;
		int[] aksi = s.getBooleanOr("perang", false) ? new int[] {A_DAMAI, A_TUNTUT} : new int[] {A_PERANG, A_TUNTUT};
		y += tombolAksi(l, sheet, s, aksi, cx0, y, cx1);
		Component lihat = Component.translatable(M + "lihat_sekte");
		int lw = TalismanButton.lebar(l.font, lihat, 60, cx1 - cx0);
		TalismanButton.gambar(l, "lihat", cx0, y, lw, TOMBOL_H, lihat, true, null, () -> LuarPage.bukaSekte(id));
		y += TOMBOL_H + 4;
		gulir.selesai(l, y);
	}

	// ================================================================== the diplomacy block of one sect (Sekte Dunia)

	/**
	 * Draws the diplomacy of sect {@code id} of the last sheet into a stacked detail from {@code y}: heading, standing gauge,
	 * strength, facts, the eleven actions and the chronicle. Returns the height used (0 when the module does not run).
	 */
	public static int blokDiplomasi(Lukis l, String id, int x0, int y, int x1) {
		CompoundTag sheet = DiplomasiClient.data();
		if (!DiplomasiClient.tersedia()) {
			return 0;
		}
		int top = y;
		y += l.kepala(Component.translatable(M + "diplomasi"), x0, y, x1);
		CompoundTag s = cari(sheet, id);
		if (sheet == null || s == null) {
			l.samar(Component.translatable(sheet == null ? M + "memuat" : M + "diplomasi.tanpa"), x0, y, x1 - x0);
			return y + 12 - top;
		}
		y += gauge(l, s, x0, y, x1);
		y += kekuatan(l, sheet, s, x0, y, x1);
		for (Component fakta : fakta(s)) {
			y += (int) l.bungkus(fakta, x0, y, x1 - x0, l.gaya.teksLemah, 27) + 1;
		}
		y += 3;
		if (!sheet.getBooleanOr("hall", false)) {
			y += (int) l.bungkus(Component.translatable(KEY + "layar.tanpa_aula"), x0, y, x1 - x0, Palette.DANGER, 18) + 3;
		} else {
			int[] semua = new int[DiplomasiPayloads.ACTIONS.length];
			for (int a = 0; a < semua.length; a++) {
				semua[a] = a;
			}
			y += tombolAksi(l, sheet, s, semua, x0, y, x1);
		}
		y += 2;
		y += l.kepala(Component.translatable(KEY + "layar.kronik"), x0, y, x1);
		ListTag kronik = s.getListOrEmpty("kronik");
		if (kronik.isEmpty()) {
			l.samar(Component.translatable(KEY + "layar.kronik_kosong"), x0, y, x1 - x0);
			y += 11;
		}
		int shown = 0;
		for (int i = kronik.size() - 1; i >= 0 && shown < 6; i--, shown++) {
			CompoundTag line = kronik.getCompoundOrEmpty(i);
			Component text = Component.translatable(KEY + "layar.hari", line.getLongOr("day", 0L)).append(" ").append(comp(line, "text", Component.empty()));
			y += (int) l.bungkus(text, x0, y, x1 - x0, l.gaya.teks, 27) + 1;
		}
		return y - top;
	}

	/** The fact lines of a sect (war, vassal, forced tribute, gate, its enemy and band, defectors). */
	private static List<Component> fakta(CompoundTag s) {
		List<Component> facts = new ArrayList<>();
		if (s.getBooleanOr("perang", false)) {
			facts.add(Component.translatable(KEY + "layar.perang", s.getLongOr("perang_hari", 0L), s.getIntOr("menang", 0), s.getIntOr("kalah", 0), 3)
					.withColor(Palette.SEAL_DARK & 0xFFFFFF));
		}
		if (s.getBooleanOr("bawahan", false)) {
			facts.add(Component.translatable(KEY + "layar.bawahan").withColor(Palette.JADE_DARK & 0xFFFFFF));
		}
		if (s.getIntOr("upeti_paksa", 0) > 0) {
			facts.add(Component.translatable(KEY + "layar.upeti_paksa", s.getIntOr("upeti_paksa", 0)).withColor(Palette.SEAL_DARK & 0xFFFFFF));
		}
		if (s.contains("gx")) {
			facts.add(Component.translatable(KEY + (s.getBooleanOr("gerbang", false) ? "layar.gerbang" : "layar.gerbang_belum"), s.getIntOr("gx", 0),
					s.getIntOr("gz", 0)));
		} else {
			facts.add(Component.translatable(KEY + "layar.gerbang_rahasia"));
		}
		if (s.contains("musuh")) {
			facts.add(Component.translatable(KEY + "layar.musuh", comp(s, "musuh", Component.empty())));
		}
		if (s.contains("klan")) {
			facts.add(Component.translatable(KEY + "layar.klan", comp(s, "klan", Component.empty())));
		}
		if (s.getIntOr("pembelot", 0) > 0) {
			facts.add(Component.translatable(KEY + "layar.pembelot", s.getIntOr("pembelot", 0)));
		}
		return facts;
	}

	/** The standing gauge from war to alliance with its six zones, the marker and the pact badge; returns the height (22). */
	private static int gauge(Lukis l, CompoundTag s, int x0, int y, int x1) {
		int stance = sikap(s);
		int standing = s.getIntOr("standing", 0);
		Component label = Component.translatable(KEY + "layar.standing", Brush.bertanda(standing), namaSikap(stance));
		int kanan = x1;
		int pakta = s.getIntOr("pakta", 0);
		if (pakta > 0) {
			Component badge = Component.translatable(KEY + (pakta == 2 ? "layar.pakta_sekutu" : "layar.pakta_damai"));
			int bw = l.font.width(badge) + 6;
			Brush.chamfer(l.g, x1 - bw, y, x1, y + 10, 2, pakta == 2 ? Palette.GOLD : Palette.JADE);
			l.teks(badge, x1 - bw + 3, y + 1, bw - 4, pakta == 2 ? Palette.INK : 0xFFFFFFFF);
			kanan -= bw + 4;
		}
		l.teks(label, x0, y + 1, kanan - x0, l.gaya.teks);
		int by = y + 12;
		// zones: war < -70, hostile < -30, cold < -10, neutral < 20, friendly < 60, ally from 60 (with a pact)
		int[] edges = {-100, -70, -30, -10, 20, 60, 100};
		for (int i = 0; i < 6; i++) {
			int a = x0 + Math.round((x1 - x0) * (edges[i] + 100) / 200.0F);
			int b = x0 + Math.round((x1 - x0) * (edges[i + 1] + 100) / 200.0F);
			l.g.fillGradient(a, by, b, by + 6, Palette.argb(0xD0, Palette.lighten(Palette.opaque(STANCE_RGB[i]), 0.15F)), Palette.argb(0xD0, STANCE_RGB[i]));
		}
		Brush.frame(l.g, x0 - 1, by - 1, x1 + 1, by + 7, Palette.argb(0xA0, Palette.INK));
		float at = x0 + (x1 - x0) * (Mth.clamp(standing, -100, 100) + 100) / 200.0F;
		Brush.rect(l.g, at - 1.5F, by - 2, at + 1.5F, by + 8, Palette.mix(Palette.GOLD_LIGHT, 0xFFFFFFFF, l.denyut()));
		if (l.di(x0, y, x1, by + 8)) {
			l.tip(Component.translatable(KEY + "layar.tip.standing"));
		}
		return 22;
	}

	/** Strength against the hall's and the growth toward the next tier; returns the height used. */
	private static int kekuatan(Lukis l, CompoundTag sheet, CompoundTag s, int x0, int y, int x1) {
		int theirs = s.getIntOr("strength", 0);
		int ours = sheet.getIntOr("kekuatan", 0);
		Component teks = Component.translatable(KEY + "layar.kekuatan", Brush.angka(ours), Brush.angka(theirs));
		l.teks(teks, x0, y, x1 - x0, l.gaya.teksLemah);
		float share = theirs + ours <= 0 ? 0.5F : ours / (float) (theirs + ours);
		l.g.fill(x0, y + 11, x1, y + 15, Palette.opaque(0x8B1A1A));
		Bilah.gambar(l, x0, y + 11, x1, share, Palette.JADE, new float[] {0.5F});
		int tier = s.getIntOr("tier", 1);
		float grow = tier >= 9 ? 1.0F : Mth.clamp(s.getIntOr("sumber", 0) / (float) Math.max(1, s.getIntOr("next", 1)), 0.0F, 1.0F);
		int h = 18;
		h += Bilah.baris(l, Component.translatable(KEY + "layar.tingkat", tier), grow, 1.0F, x0, y + h, x1,
				Component.literal(Brush.persen(grow)), Palette.GOLD);
		if (l.di(x0, y + 18, x1, y + 28)) {
			l.tip(Component.translatable(KEY + "layar.tip.tumbuh", s.getIntOr("sumber", 0), s.getIntOr("next", 0)));
		}
		return h + 3;
	}

	/** A grid of action talismans {@code aksi} of sect {@code s}; returns the height used. */
	private static int tombolAksi(Lukis l, CompoundTag sheet, CompoundTag s, int[] aksi, int x0, int y, int x1) {
		ListTag why = s.getListOrEmpty("why");
		String id = s.getStringOr("id", "");
		Component namaSekte = nama(s);
		boolean aula = sheet.getBooleanOr("hall", false);
		int kolom = Math.max(1, Math.min(4, (x1 - x0 + 3) / 74));
		int bw = (x1 - x0 - (kolom - 1) * 3) / kolom;
		Fokus f = l.fokusRing();
		for (int i = 0; i < aksi.length; i++) {
			int a = aksi[i];
			int bx = x0 + (i % kolom) * (bw + 3);
			int by = y + (i / kolom) * (TOMBOL_H + 3);
			CompoundTag w = why.getCompoundOrEmpty(a);
			String action = DiplomasiPayloads.ACTIONS[a];
			Component label = Component.translatable(KEY + "aksi." + action);
			Component alasan = !aula ? Component.translatable(KEY + "layar.tanpa_aula") : w.contains("t") ? comp(w, "t", Component.empty()) : null;
			boolean over = TalismanButton.gambar(l, "aksi:" + id + ":" + a, bx, by, bw, TOMBOL_H, label, alasan == null, alasan, () -> {
				Runnable kirim = () -> {
					if (DiplomasiClient.request(DiplomasiPayloads.OP_ACTION, id, a, 0)) {
						UiSound.press();
					}
				};
				SegelKonfirmasi.Tingkat t = a == A_PERANG ? SegelKonfirmasi.Tingkat.BERAT
						: a == A_PUTUS || a == A_TUNTUT || a == A_TANTANG ? SegelKonfirmasi.Tingkat.SEDANG : null;
				if (t == null) {
					kirim.run();
				} else {
					f.konfirmasi(t, Component.translatable(M + "yakin." + action, namaSekte), kirim);
				}
			});
			if (over) {
				List<Component> tip = new ArrayList<>();
				tip.add(Brush.bold(label));
				tip.add(Component.translatable(KEY + "aksi." + action + ".tip"));
				if (a == 4) {
					tip.add(Component.translatable(KEY + "layar.peluang", s.getIntOr("peluang_pakta", 0)).withColor(Palette.JADE_DARK & 0xFFFFFF));
				} else if (a == 5) {
					tip.add(Component.translatable(KEY + "layar.peluang", s.getIntOr("peluang_sekutu", 0)).withColor(Palette.JADE_DARK & 0xFFFFFF));
				}
				if (alasan != null) {
					tip.add(alasan.copy().withColor(Palette.DANGER & 0xFFFFFF));
				}
				l.tip(tip);
			}
		}
		int baris = (aksi.length + kolom - 1) / kolom;
		return baris * (TOMBOL_H + 3) + 2;
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Page p = anak();
		return p != null ? p.mouseClicked(event, doubleClick) : super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Page p = anak();
		return p != null ? p.mouseReleased(event) : super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		Page p = anak();
		return p != null ? p.mouseDragged(event, dx, dy) : super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Page p = anak();
		if (p != null) {
			return p.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Page p = anak();
		if (p != null) {
			return p.keyPressed(event);
		}
		return super.keyPressed(event) || gulir.tombol(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		Page p = anak();
		return p != null ? p.keyReleased(event) : super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		Page p = anak();
		return p != null ? p.charTyped(event) : super.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		Page p = anak();
		return p != null ? p.mauTombol() : super.mauTombol();
	}

	@Override
	public boolean modal() {
		Page p = anak();
		return p != null ? p.modal() : super.modal();
	}

	@Override
	public boolean mundur() {
		Page p = anak();
		if (p != null) {
			return p.mundur();
		}
		if (rinciSempit && tata() == Tata.SEMPIT) {
			rinciSempit = false;
			return true;
		}
		return false;
	}

	@Override
	public boolean fokusTab(boolean back) {
		Page p = anak();
		return p != null ? p.fokusTab(back) : super.fokusTab(back);
	}
}
