package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.DaftarSekte;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Saring;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.TombolKembali;
import dev.kultivator.client.sekte.diplomasi.DiplomasiClient;
import dev.kultivator.client.sekte.diplomasi.DiplomasiScreen;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.sekte.mandiri.seteru.SeteruClient;
import dev.kultivator.client.sekte.mandiri.seteru.SeteruScreen;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.TabLentera;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Direktori;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code gabung_direktori} (DESIGN 41.10.1) as the one sect list of the mod: Sekte › Luar › Sekte Dunia (43.5, G36). It
 * merges what four screens showed apart: the directory (every sect in live strength order; unknown ones as "?" with a hint),
 * the hall's diplomacy (stance, war, pact, vassal), the hall's feuds and the named disciples of other sects (Seteru), and
 * the strength board with its 7-day trend (old tab Peringkat). Old tabs Lurus / Netral / Iblis are filter chips
 * ({@link Saring}, 43.2.3), Peringkat is a sort order, Sekteku lives in Sekte › Ikhtisar (G45) except its Papan Jasa,
 * a stacked heading in your own sect's detail here.
 * <p>
 * The list is {@link DaftarSekte}; the chosen sect's detail is beside it (or in its place in the narrow layout, with
 * "‹ Kembali"): its card, strength and trend, the best door's requirements with Lamar / Kartu Sekte / Tunjuk di peta, the
 * diplomacy block of {@link DiplomasiScreen#blokDiplomasi} (hall owners) and its disciples' cards
 * ({@link SeteruScreen#kartuTokoh}), stacked headings in one scroll (43.0.4). The page is drawn with the single kit
 * {@code screen/ui} (its own focus ring, not the old sheet engine) whether it shows under Sekte › Luar or under Di Sini
 * (a recruiter, a command): one GUI. Text is 1.0.
 */
public final class LayarDirektori extends LayarGabungDasar {

	private static final String M = "kultivator.gui43.m11.";
	private static final int TOMBOL_H = 14;
	/** A sect asked for by {@code LuarPage} before the sheet was there (or for the next sheet). */
	private static String tuju = "";

	private final DaftarGulir daftar = new DaftarGulir();
	private final GulirIsi gulir = new GulirIsi();
	private final Saring saring;
	private List<Lembar> tampil = List.of();
	private String pilihId = "";
	private boolean rinciSempit;
	private long diploDiminta = Long.MIN_VALUE / 2;

	public LayarDirektori(int npcId, CompoundTag lembar) {
		super(Direktori.LAYAR, npcId, lembar, t("direktori.judul"), TempatWarna.Gaya.KERTAS);
		saring = new Saring(() -> daftar.mulaiUlang(Util.getMillis()));
		saring.jenis(List.of(new Saring.Pilihan(Direktori.TAB_LURUS, t("tab.lurus")), new Saring.Pilihan(Direktori.TAB_NETRAL, t("tab.netral")),
				new Saring.Pilihan(Direktori.TAB_IBLIS, t("tab.iblis"))));
		saring.urut(List.of(new Saring.Pilihan("peringkat", t("direktori.urut.peringkat")), new Saring.Pilihan("jarak", t("direktori.urut.jarak")),
				new Saring.Pilihan("kelas", t("direktori.urut.kelas")), new Saring.Pilihan("sikap", Component.translatable(M + "urut.sikap"))));
	}

	/** Selects sect {@code id} now, or as soon as the directory sheet holds it (Sekte › Luar opened on a sect). */
	public static void tuju(String id) {
		tuju = id == null ? "" : id;
	}

	// ================================================================== sheet

	/** One tab: the old category tabs are filter chips of the one list (43.2.3); no sub bar. */
	@Override
	protected List<TabLentera.Tab> tabs() {
		return List.of(tabBaru(Direktori.TAB_SEMUA, "tab.semua", IkonTempat.PETA));
	}

	@Override
	protected TabLentera.Tab tabBaru(String id, String labelKunci, int ikon) {
		return new TabLentera.Tab(id, t(labelKunci), ikon, null);
	}

	@Override
	protected void setelahLembar(boolean buka) {
		String pilih = lembar.s(Direktori.PILIH);
		if (buka && !pilih.isEmpty()) {
			pilihId = pilih;
			rinciSempit = true;
		}
		// the old tab of a command (lurus/netral/iblis) becomes the filter chip
		String diminta = lembar.s(LembarKunci.TAB);
		if (buka && (Direktori.TAB_LURUS.equals(diminta) || Direktori.TAB_NETRAL.equals(diminta) || Direktori.TAB_IBLIS.equals(diminta))) {
			saring.setJenis(diminta);
		}
	}

	@Override
	protected void isiGabung(dev.kultivator.client.tempat.ui.kit.Lukis l, int x0, int y0, int x1, int y1) {
		// not used: this page draws with the single kit in lukis()
	}

	@Override
	public String helpTopic() {
		return "gabung_sekte";
	}

	@Override
	public boolean lebar() {
		return false;
	}

	@Override
	public boolean rinci() {
		return rinciSempit && tata() == Tata.SEMPIT || super.rinci();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokusTag) {
		super.onShow(fokusTag);
		mintaDiplomasi();
	}

	@Override
	public void fokus(CompoundTag fokus) {
		String s = fokus.getStringOr("sekte", "");
		if (!s.isEmpty()) {
			tuju(s);
		}
	}

	/** The hall's diplomacy sheet for the stances (fresh each time the list shows; R16). */
	private void mintaDiplomasi() {
		long now = Util.getMillis();
		if (now - diploDiminta > 2000L) {
			diploDiminta = now;
			DiplomasiClient.minta(pilihId.isEmpty() ? null : pilihId);
		}
	}

	// ================================================================== the merged list

	private static @Nullable String jenis(Lembar e) {
		if (!e.b(Direktori.DIKENAL)) {
			return null;
		}
		return switch (e.i(GabungLembar.ALIRAN, -1)) {
			case 0 -> Direktori.TAB_LURUS;
			case 1 -> Direktori.TAB_NETRAL;
			case 2 -> Direktori.TAB_IBLIS;
			default -> null;
		};
	}

	/** The standing of sect {@code id}: the diplomacy sheet's, else the directory's, else none. */
	private static int standing(Lembar e, @Nullable CompoundTag diplo) {
		CompoundTag s = DiplomasiScreen.cari(diplo, e.id());
		if (s != null) {
			return s.getIntOr("standing", 0);
		}
		return e.b(Direktori.DIKENAL) ? e.i(GabungLembar.SIKAP, 0) : DaftarSekte.TANPA_SIKAP;
	}

	private List<Lembar> saringDaftar(@Nullable CompoundTag diplo) {
		List<Lembar> out = new ArrayList<>();
		for (Lembar e : lembar.daftar(LembarKunci.DAFTAR)) {
			if (saring.cocok(e.teks(LembarKunci.NAMA), jenis(e))) {
				out.add(e);
			}
		}
		Comparator<Lembar> c = switch (saring.urutAktif()) {
			case "jarak" -> Comparator.comparingInt(e -> {
				int j = e.i(LembarKunci.JARAK, -1);
				return j < 0 ? Integer.MAX_VALUE : j;
			});
			case "kelas" -> Comparator.comparingInt((Lembar e) -> -e.i(GabungLembar.TINGKAT, 0)).thenComparingInt(e -> e.i(Direktori.PERINGKAT, 99));
			case "sikap" -> Comparator.comparingInt((Lembar e) -> {
				int s = standing(e, diplo);
				return s == DaftarSekte.TANPA_SIKAP ? Integer.MAX_VALUE : s;
			}).thenComparingInt(e -> e.i(Direktori.PERINGKAT, 99));
			default -> Comparator.comparingInt(e -> e.i(Direktori.PERINGKAT, 99));
		};
		out.sort(c);
		return out;
	}

	/** Sects the hall has a live feud with (Seteru sheet). */
	private static Set<String> seteru() {
		Set<String> out = new HashSet<>();
		CompoundTag d = SeteruClient.data();
		if (d == null) {
			return out;
		}
		ListTag list = d.getListOrEmpty("seteru");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag f = list.getCompoundOrEmpty(i);
			String status = f.getStringOr("status", "");
			if (SeteruIds.AKTIF.equals(status) || SeteruIds.TERLARANG.equals(status)) {
				out.add(f.getStringOr("sekte", ""));
			}
		}
		return out;
	}

	/** One {@link DaftarSekte} row of a directory entry, with the diplomacy and feud marks. */
	private DaftarSekte.Sekte baris(Lembar e, @Nullable CompoundTag diplo, Set<String> seteru) {
		int rank = e.i(Direktori.PERINGKAT, 0);
		if (!e.b(Direktori.DIKENAL)) {
			return DaftarSekte.Sekte.asing(e.id(), e.teks(LembarKunci.NAMA), e.teksAtau(Direktori.PETUNJUK), rank);
		}
		List<Integer> ikon = new ArrayList<>(3);
		if (e.b(Direktori.ANGGOTA)) {
			ikon.add(IkonGabung.LENCANA);
		}
		if (e.b(Direktori.CALON)) {
			ikon.add(IkonGabung.PINTU + 1);
		}
		if (e.b(GabungLembar.LANGIT)) {
			ikon.add(IkonGabung.LANGIT);
		}
		if (e.b(GabungLembar.MEROSOT)) {
			ikon.add(IkonGabung.MEROSOT);
		}
		CompoundTag d = DiplomasiScreen.cari(diplo, e.id());
		MutableComponent tanda = Component.empty();
		if (d != null && d.getBooleanOr("perang", false)) {
			tanda.append(Component.literal("戰").withColor(Palette.DANGER & 0xFFFFFF));
		} else if (d != null && d.getIntOr("pakta", 0) == 2) {
			tanda.append(Component.literal("盟").withColor(Palette.GOLD_DARK & 0xFFFFFF));
		} else if (d != null && d.getBooleanOr("bawahan", false)) {
			tanda.append(Component.literal("屬").withColor(Palette.JADE_DARK & 0xFFFFFF));
		}
		if (seteru.contains(e.id())) {
			tanda.append(Component.literal("仇").withColor(Palette.SEAL & 0xFFFFFF));
		}
		int sikap = standing(e, diplo);
		Component sikapNama = d != null ? DiplomasiScreen.namaSikap(DiplomasiScreen.sikap(d)) : e.teksAtau(GabungLembar.SIKAP_NAMA);
		Component bawah = KelasSekte.byNomor(e.i(GabungLembar.KELAS, 1)).nama().copy().append(" · ")
				.append(Corak.byId(e.s(GabungLembar.CORAK), Corak.PEDANG).nama());
		int jarak = e.i(LembarKunci.JARAK, -1);
		if (jarak >= 0) {
			bawah = bawah.copy().append(" · ").append(t("jarak", Brush.angka(jarak), t("arah." + Math.max(0, e.i(Direktori.ARAH, 0)))));
		}
		return new DaftarSekte.Sekte(e.id(), e.teks(LembarKunci.NAMA), e.i(GabungLembar.LAMBANG, 0), e.i(GabungLembar.WARNA, 0xB0B0B0),
				e.i(GabungLembar.TINGKAT, 1), 9, sikap, sikapNama, Math.max(0, Math.min(2, e.i(GabungLembar.ALIRAN, 0))), rank, bawah,
				tanda.getString().isEmpty() ? null : tanda, true, ikon);
	}

	private int indeksPilih() {
		for (int i = 0; i < tampil.size(); i++) {
			if (tampil.get(i).id().equals(pilihId)) {
				return i;
			}
		}
		return -1;
	}

	// ================================================================== drawing (single kit)

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		// applies the first sheet; the content is drawn in lukis() with the page's focus ring
		tabSekarang();
	}

	@Override
	protected void lukis(Lukis l) {
		int x0 = x + 2;
		int x1 = x + w - 2;
		int y0 = y + 2;
		int y1 = y + h - 2;
		if (lembar.ada(LembarKunci.KUNCI_LAYAR)) {
			Lembar k = lembar.sub(LembarKunci.KUNCI_LAYAR);
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, k.teks(LembarKunci.ALASAN), null, null, x0, y0, x1, y1);
			return;
		}
		CompoundTag diplo = DiplomasiClient.data();
		Set<String> seteru = seteru();
		int y = y0;
		Component sub = lembar.teksAtau(LembarKunci.SUBJUDUL);
		if (sub != null) {
			l.samar(sub, x0, y, x1 - x0);
			y += 12;
		}
		List<Lembar> semua = lembar.daftar(LembarKunci.DAFTAR);
		if (semua.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, lembar.sub(LembarKunci.KOSONG), t("direktori.kosong"), x0, y, x1, y1, this::kirim);
			return;
		}
		tampil = saringDaftar(diplo);
		if (!tuju.isEmpty()) {
			for (Lembar e : semua) {
				if (e.id().equals(tuju)) {
					pilihId = tuju;
					rinciSempit = true;
					saring.reset();
					tampil = saringDaftar(diplo);
					gulir.atas();
					tuju = "";
					break;
				}
			}
		}
		int at = indeksPilih();
		if (at < 0 && !tampil.isEmpty()) {
			at = 0;
			pilihId = tampil.get(0).id();
		}
		boolean sempit = tata() == Tata.SEMPIT;
		if (sempit && rinciSempit && at >= 0) {
			rinci(l, tampil.get(at), diplo, x0, y, x1, y1);
			return;
		}
		int lx1 = sempit ? x1 : x0 + Math.round((x1 - x0) * 0.46F);
		y += saring.gambar(l, "saring", x0, y, lx1) + 3;
		if (tampil.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, t("direktori.tidak_ketemu"), null, null, x0, y, lx1, y1);
		} else {
			if (daftar.pilihan() != at) {
				daftar.setPilih(at);
			}
			List<DaftarSekte.Sekte> rows = new ArrayList<>(tampil.size());
			for (Lembar e : tampil) {
				rows.add(baris(e, diplo, seteru));
			}
			DaftarSekte.gambar(l, "daftar", daftar, rows, x0, y, lx1, y1, () -> rinciSempit = true, i -> {
				if (i >= 0 && i < tampil.size()) {
					pilihId = tampil.get(i).id();
					gulir.atas();
					mintaDiplomasi();
				}
			});
		}
		if (!sempit && at >= 0) {
			int ry0 = sub != null ? y0 + 12 : y0;
			rinci(l, tampil.get(at), diplo, lx1 + 6, ry0, x1, y1);
		}
	}

	/** The stacked detail of one sect. */
	private void rinci(Lukis l, Lembar e, @Nullable CompoundTag diplo, int x0, int y0, int x1, int y1) {
		int top = y0;
		if (tata() == Tata.SEMPIT) {
			TombolKembali.gambar(l, "kembali", x0, y0, null, () -> rinciSempit = false);
			top += 16;
		}
		l.sumur(x0, top, x1, y1);
		int cx0 = x0 + 4;
		int cx1 = x1 - 9;
		int y = gulir.mulai(l, x0 + 1, top + 1, x1 - 6, y1 - 1) + 3;
		Lembar rank = peringkat(e.id());
		if (!e.b(Direktori.DIKENAL)) {
			y = asing(l, e, rank, cx0, y, cx1);
		} else {
			y = kartu(l, e, rank, diplo, cx0, y, cx1);
			y += 4;
			int h = DiplomasiScreen.blokDiplomasi(l, e.id(), cx0, y, cx1);
			y += h > 0 ? h + 4 : 0;
			if (e.b(Direktori.ANGGOTA) && lembar.ada(Direktori.SEKTEKU)) {
				y = papanJasa(l, lembar.sub(Direktori.SEKTEKU), cx0, y, cx1);
			}
			y = murid(l, e.id(), cx0, y, cx1);
		}
		gulir.selesai(l, y + 4);
	}

	private @Nullable Lembar peringkat(String id) {
		for (Lembar r : lembar.daftar(Direktori.PERINGKAT)) {
			if (r.id().equals(id)) {
				return r;
			}
		}
		return null;
	}

	/** Strength and 7-day trend (old tab Peringkat); returns the height used. */
	private int kekuatan(Lukis l, @Nullable Lembar rank, int x0, int y, int x1) {
		if (rank == null) {
			return 0;
		}
		int tren = rank.i(Direktori.TREN, 0);
		Component panah = tren > 0 ? Component.literal("▲").withColor(Palette.JADE_DARK & 0xFFFFFF)
				: tren < 0 ? Component.literal("▼").withColor(Palette.SEAL & 0xFFFFFF) : Component.literal("–");
		Component teks = Component.translatable(M + "kekuatan", Brush.angka(rank.i(Direktori.KEKUATAN, 0)), rank.i(Direktori.PERINGKAT, 0)).append(" ")
				.append(panah);
		l.teks(teks, x0, y, x1 - x0, l.gaya.teksLemah);
		if (l.di(x0, y, x1, y + 10)) {
			l.tip(t("direktori.tren." + (tren > 0 ? "naik" : tren < 0 ? "turun" : "tetap")));
		}
		return 11;
	}

	/** The "?" card of an unknown sect; returns the y under it. */
	private int asing(Lukis l, Lembar e, @Nullable Lembar rank, int x0, int y, int x1) {
		IkonGabung.gambar(l.g, IkonGabung.TANYA, x0, y, 24);
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), x0 + 28, y + 3, x1 - x0 - 28, l.gaya.teks);
		l.samar(t("direktori.jumlah", lembar.i(LembarKunci.JUMLAH, 0), lembar.i(Direktori.DIKENAL_JUMLAH, 0)), x0 + 28, y + 14, x1 - x0 - 28);
		y += 28;
		y += kekuatan(l, rank, x0, y, x1);
		IkonGabung.gambar(l.g, IkonGabung.KABAR, x0, y, 12);
		y += (int) l.bungkus(e.teks(Direktori.PETUNJUK), x0 + 15, y + 1, x1 - x0 - 15, l.gaya.teks, 45) + 4;
		return y;
	}

	/** The card of a known sect, its door and the buttons; returns the y under it. */
	private int kartu(Lukis l, Lembar e, @Nullable Lembar rank, @Nullable CompoundTag diplo, int x0, int y, int x1) {
		int r = 13;
		IkonGabung.lambang(l.g, l.font, e.i(GabungLembar.LAMBANG, 0), x0 + r, y + r, r);
		int tx = x0 + r * 2 + 5;
		l.teks(Brush.bold(e.teks(LembarKunci.NAMA)), tx, y + 1, x1 - tx, l.gaya.teks);
		int kelas = e.i(GabungLembar.KELAS, 1);
		IkonGabung.gambar(l.g, IkonGabung.kelas(kelas), tx, y + 11, 10);
		l.teks(t("direktori.kelas", KelasSekte.byNomor(kelas).nama(), e.teks(Direktori.GELAR_KELAS), e.i(GabungLembar.TINGKAT, 1)), tx + 12, y + 12,
				x1 - tx - 12, l.gaya.teksLemah);
		y += Math.max(r * 2, 22) + 3;
		int aliran = Math.max(0, Math.min(2, e.i(GabungLembar.ALIRAN, 0)));
		IkonGabung.gambar(l.g, IkonGabung.ALIRAN + aliran, x0, y, 10);
		l.teks(t("direktori.aliran_corak", t("aliran." + aliran), Corak.byId(e.s(GabungLembar.CORAK), Corak.PEDANG).nama()), x0 + 12, y + 1, x1 - x0 - 12,
				l.gaya.teks);
		y += 11;
		l.teks(t("direktori.ketua", e.teks(Direktori.KETUA), namaRanah(e.i(Direktori.RANAH_KETUA, 0))), x0, y, x1 - x0, l.gaya.teks);
		y += 11;
		if (DiplomasiScreen.cari(diplo, e.id()) == null) {
			// without the hall's diplomacy sheet the directory's stance stands alone (the diplomacy block has the gauge)
			int sikap = e.i(GabungLembar.SIKAP, 0);
			Component s = t("direktori.sikap", e.teks(GabungLembar.SIKAP_NAMA), Brush.bertanda(sikap));
			l.teks(s, x0, y, x1 - x0 - 44, l.gaya.teks);
			Bilah.gambar(l, x1 - 40, y + 3, x1, (sikap + 100) / 200.0F, DaftarSekte.warnaSikap(sikap), new float[] {0.5F});
			y += 11;
		}
		y += kekuatan(l, rank, x0, y, x1);
		int jarak = e.i(LembarKunci.JARAK, -1);
		if (jarak >= 0) {
			IkonGabung.gambar(l.g, IkonGabung.ARAH, x0, y, 9);
			l.teks(t("direktori.jarak", Brush.angka(jarak), t("arah." + Math.max(0, e.i(Direktori.ARAH, 0)))), x0 + 11, y, x1 - x0 - 11, l.gaya.teks);
			y += 11;
		}
		if (e.ada(GabungLembar.HARI_UJIAN) && !e.b(Direktori.ANGGOTA)) {
			IkonGabung.gambar(l.g, IkonGabung.PINTU, x0, y, 9);
			l.teks(t("direktori.hari_ujian", hariKe(e.l(GabungLembar.HARI_UJIAN, -1L), hariIni())), x0 + 11, y, x1 - x0 - 11, l.gaya.teks);
			y += 11;
		}
		// the best door's requirements
		List<Lembar> syarat = e.daftar(LembarKunci.SYARAT);
		if (!syarat.isEmpty()) {
			y += 2;
			Pintu pintu = Pintu.byId(e.s(GabungLembar.PINTU), Pintu.GERBANG);
			y += l.kepala(t("direktori.syarat", pintu.nama()), x0, y, x1);
			for (Lembar s : syarat) {
				y += syarat(l, s, x0, y, x1);
			}
		}
		// Lamar / Kartu Sekte and Tunjuk di peta
		y += 3;
		int half = (x1 - x0 - 3) / 2;
		if (e.b(Direktori.ANGGOTA)) {
			TalismanButton.gambar(l, "kartu", x0, y, half, TOMBOL_H, t("direktori.kartu"), true, null, () -> kirim(Direktori.OP_KARTU));
		} else {
			Component lamar = t(e.b(Direktori.CALON) ? "direktori.lamaran_saya" : "direktori.lamar");
			String op = LembarKunci.op(Direktori.OP_LAMAR, e.id());
			TalismanButton.gambar(l, "lamar", x0, y, half, TOMBOL_H, lamar, true, null, () -> kirim(op));
		}
		boolean adaPos = e.ada(LembarKunci.X);
		String tandai = LembarKunci.op(LembarKunci.OP_TANDAI, e.i(LembarKunci.X, 0), e.i(LembarKunci.Z, 0));
		TalismanButton.gambar(l, "tandai", x0 + half + 3, y, half, TOMBOL_H, t("tunjuk_peta"), adaPos, adaPos ? null : t("tanpa_lokasi"), () -> kirim(tandai));
		return y + TOMBOL_H + 2;
	}

	/** A requirement row: tick or cross, the line, then what the player has and the advice. Returns the height used. */
	private int syarat(Lukis l, Lembar s, int x0, int y, int x1) {
		boolean ok = s.b(LembarKunci.OK);
		Glyphs.Atlas.ikon(l.g, ok ? Glyphs.Atlas.CENTANG : Glyphs.Atlas.SILANG, x0, y, 9, 0xFFFFFFFF);
		l.teks(s.teks(LembarKunci.TEKS), x0 + 12, y, x1 - x0 - 12, ok ? l.gaya.teks : l.gaya.teksLemah);
		Component punya = s.teksAtau(GabungLembar.PUNYA);
		Component saran = ok ? null : s.teksAtau(GabungLembar.SARAN);
		if (punya == null && saran == null) {
			return 11;
		}
		MutableComponent cap = Component.empty();
		if (punya != null) {
			cap.append(t("punya", punya));
		}
		if (saran != null) {
			if (punya != null) {
				cap.append("  ");
			}
			cap.append(t("saran_panah", saran));
		}
		return 11 + (int) l.bungkus(cap, x0 + 12, y + 10, x1 - x0 - 12, saran != null ? Palette.WARN : l.gaya.teksSamar, 18) + 1;
	}

	/**
	 * This month's Papan Jasa of your own sect (old tab Sekteku, 41:1036): players and roster seniors by PK, your row marked,
	 * the top three in gold (they earn the quota). Returns the y under it.
	 */
	private int papanJasa(Lukis l, Lembar s, int x0, int y, int x1) {
		List<Lembar> rows = s.daftar(Direktori.PAPAN_JASA);
		if (rows.isEmpty()) {
			return y;
		}
		y += l.kepala(t("direktori.papan_jasa"), x0, y, x1);
		l.samar(t("direktori.pk_total", Brush.angka(s.l(GabungLembar.PK_TOTAL, 0L))), x0, y, x1 - x0);
		y += 12;
		for (int i = 0; i < rows.size(); i++) {
			Lembar r = rows.get(i);
			boolean anda = r.b(LembarKunci.ANDA);
			if (anda) {
				l.g.fill(x0 - 1, y - 1, x1 + 1, y + 10, Palette.argb(0x30, Palette.JADE_DARK & 0xFFFFFF));
			}
			int warna = i < 3 ? Palette.GOLD_DARK : anda ? Palette.JADE_DARK : l.gaya.teks;
			Component pk = Component.literal(Brush.angka(r.l(GabungLembar.PK_TOTAL, 0L)) + " PK");
			int pw = l.font.width(pk);
			// the server sends the top 10 plus your own row when it is lower down (its place is on the Kartu)
			String no = i < 10 ? "#" + (i + 1) + " " : "… ";
			l.teks(Component.literal(no).append(r.teks(LembarKunci.NAMA)), x0, y, x1 - x0 - pw - 4, warna);
			l.teks(pk, x1 - pw, y, pw, warna);
			y += 11;
		}
		return y + 4;
	}

	/** The named disciples of the sect (Seteru), two cards a row when there is room; returns the y under them. */
	private int murid(Lukis l, String id, int x0, int y, int x1) {
		if (!SeteruClient.tersedia()) {
			return y;
		}
		y += l.kepala(Component.translatable(M + "murid_sekte"), x0, y, x1);
		List<CompoundTag> roster = SeteruClient.rosterUntuk(id);
		if (roster.isEmpty()) {
			Component teks = SeteruClient.rosterAda(id) ? Component.translatable("kultivator.sekte.mandiri.seteru.layar.roster_kosong")
					: Component.translatable(M + "memuat");
			l.samar(teks, x0, y, x1 - x0);
			return y + 12;
		}
		int kolom = x1 - x0 >= 230 ? 2 : 1;
		int cw = (x1 - x0 - (kolom - 1) * 4) / kolom;
		int n = Math.min(10, roster.size());
		for (int i = 0; i < n; i++) {
			int cx = x0 + (i % kolom) * (cw + 4);
			int cy = y + (i / kolom) * (SeteruScreen.TINGGI_KARTU + 3);
			SeteruScreen.kartuTokoh(l, roster.get(i), cx, cy, cx + cw);
		}
		return y + ((n + kolom - 1) / kolom) * (SeteruScreen.TINGGI_KARTU + 3);
	}

	// ================================================================== input (the page's focus ring, not the old sheet engine)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return fokusRing.klik(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return fokusRing.lepas(event.x(), event.y());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return fokusRing.seret(event.x(), event.y(), dx);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return fokusRing.gulir(mouseX, mouseY, scrollY) || gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return fokusRing.tombol(event) || gulir.tombol(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return fokusRing.ketik(event);
	}

	@Override
	public boolean fokusTab(boolean back) {
		fokusRing.geser(back ? -1 : 1);
		return true;
	}

	@Override
	public boolean mauTombol() {
		return fokusRing.mauTombol();
	}

	@Override
	public boolean angka(int n) {
		return fokusRing.angka(n);
	}

	@Override
	public boolean mundur() {
		if (rinciSempit && tata() == Tata.SEMPIT) {
			rinciSempit = false;
			return true;
		}
		return super.mundur();
	}
}
