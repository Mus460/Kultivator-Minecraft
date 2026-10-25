package dev.kultivator.client.sectevent;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuHasil;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.misi.MisiClient;
import dev.kultivator.client.sekte.misi.MisiTab;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kegiatan › Misi of the hall owner (DESIGN 20.3.10, 20.3.11, 36.2.7, 43.5). Section chips: Harian (the day's board, a
 * team of 1-3 and its success chance), Tim (teams away with their time left and "Panggil Pulang"), Pencari Bakat (the
 * recruiting envoy; envoys to NPC sects go from Sekte › Luar as story missions), Terlarang (the Blood-Colored Forbidden
 * Ground and its last report), Bahtera (the Primordial Ark and the six Kunpeng bone pieces), Cerita (module X's story
 * board: a stance and a team of 1-4 with the server's stage chances) and Kabar (the running story missions with their
 * stage beads, "Tarik Pulang" with or without the wounded, and the reports). A report (from "Baca", or the Gulungan
 * Laporan item: R14) opens here as a detail ({@link KartuHasil}); Esc / the back control returns to the list.
 */
public class MissionPage extends SectEventPage {

	private static final String[] SEKSI = {"kultivator.sekte_sambung.misi.tab.harian", "kultivator.sectevent.ui.tab.teams",
			"kultivator.sekte_sambung.misi.tab.bakat", "kultivator.sectevent.ui.tab.forbidden", "kultivator.sectevent.ui.tab.ark",
			"kultivator.sekte_sambung.misi.tab.cerita", "kultivator.sekte_sambung.misi.tab.kabar"};
	private static final int HARIAN = 0;
	private static final int TIM = 1;
	private static final int BAKAT = 2;
	private static final int TERLARANG = 3;
	private static final int BAHTERA = 4;
	private static final int CERITA = 5;
	private static final int KABAR = 6;
	private static final String[] TYPES = {"patrol", "herbs", "caravan", "hunt", "secret_realm"};
	private static final long[] DURATION = {12000L, 12000L, 24000L, 24000L, 48000L};
	private static final int CARD_H = 22;

	/** A report that arrived and waits to be shown (the Laporan payload). */
	private static @Nullable CompoundTag laporanBaru;

	private int seksi;
	private int offer = -1;
	private int storyOffer = -1;
	private String stance = MisiTab.STANCES[0];
	private String previewKey = "";
	private @Nullable CompoundTag laporan;
	private final PemilihOrang party = new PemilihOrang(3);
	private final PemilihOrang envoy = new PemilihOrang(0);
	private final PemilihOrang ark = new PemilihOrang(0);
	private final PemilihOrang story = new PemilihOrang(4);
	private PemilihOrang forbidden = new PemilihOrang(3);
	private final DaftarGulir papan = new DaftarGulir();
	private final DaftarGulir tim = new DaftarGulir();
	private final DaftarGulir teks = new DaftarGulir();
	private final DaftarGulir cerita = new DaftarGulir();
	private final DaftarGulir kabar = new DaftarGulir();
	private final DaftarGulir riwayat = new DaftarGulir();
	private final KartuHasil kartu = new KartuHasil();

	/**
	 * A report arrived ({@code MisiPayloads.Laporan}: "Baca" or the Gulungan Laporan item): it opens as the detail of
	 * Kegiatan › Misi, in the Gulungan when it is open, else as soon as no other screen is open (R14).
	 */
	public static void laporan(CompoundTag report) {
		laporanBaru = report == null ? new CompoundTag() : report.copy();
		HubNav.antre(Tujuan.of(HubNav.SEKTE, Urutan.ID_KEGIATAN).sub(KegiatanPage.MISI));
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.mission");
	}

	@Override
	public int glyph() {
		return Glyphs.PATH;
	}

	@Override
	public String helpTopic() {
		return "misi";
	}

	@Override
	public boolean rinci() {
		return laporan != null;
	}

	@Override
	public boolean mundur() {
		if (laporan != null) {
			laporan = null;
			UiSound.press();
			return true;
		}
		return false;
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		if (seksi >= CERITA) {
			MisiClient.request(true);
		}
		super.onShow(fokus);
	}

	private static boolean siap(CompoundTag e) {
		return halangan(e) == null;
	}

	// ================================================================== drawing

	@Override
	protected void isi(Lukis l) {
		if (laporanBaru != null) {
			laporan = laporanBaru;
			laporanBaru = null;
			kartu.mulaiUlang(l.kini);
			seksi = KABAR;
		}
		if (laporan != null) {
			kartu.gambar(l, "laporan", -1, MisiTab.reportTitle(laporan), MisiTab.reportSub(laporan), MisiTab.reportGrade(laporan),
					MisiTab.reportTeam(laporan), MisiTab.reportBody(laporan), x, y, x + w, y + h);
			return;
		}
		int top = seksi(l, SEKSI, seksi, i -> {
			seksi = i;
			if (i >= CERITA) {
				MisiClient.request(true);
			}
		});
		int colW = Math.max(120, Math.round(w * 0.55F));
		int left1 = x + colW - 2;
		int rx = x + colW + 2;
		int bottom = y + h;
		switch (seksi) {
			case HARIAN -> harian(l, top, left1, rx, bottom);
			case TIM -> tim(l, top, bottom);
			case BAKAT -> bakat(l, top, left1, rx, bottom);
			case TERLARANG -> terlarang(l, top, left1, rx, bottom);
			case BAHTERA -> bahtera(l, top, left1, rx, bottom);
			case CERITA -> cerita(l, top, left1, rx, bottom);
			default -> kabar(l, top, bottom);
		}
	}

	// ---------------------------------------------------------------- Harian

	private void harian(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag data = sekte();
		int ly = kepala(l, Component.translatable("kultivator.sectevent.ui.board", data.getIntOr("strongest", 1)), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.party"), rx, top, x + w);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> {
			Component why = halangan(e);
			return why != null ? why : e.getIntOr("rank", 1) < 1 ? Component.translatable("kultivator.sectevent.ui.not_allowed") : null;
		}, e -> sectRank(e.getIntOr("rank", 1)));
		party.gambar(l, "regu", semua, rx, ry, x + w, bottom);
		ListTag board = data.getListOrEmpty("board");
		int listBottom = bottom - BTN_H - 14;
		if (board.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable("kultivator.sectevent.ui.board_empty"), null, null, x, ly,
					left1, listBottom);
		} else {
			papan.gambar(l, "papan", x, ly, left1, listBottom, board.size(), CARD_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				CompoundTag tag = board.get(i) instanceof CompoundTag t ? t : new CompoundTag();
				offerRow(lk, tag, rx0, ry0, rx1, ry1, sorot, i == offer);
			}, () -> pilihOffer(board, papan.pilihan()), i -> pilihOffer(board, i));
		}
		CompoundTag current = currentOffer();
		List<CompoundTag> regu = dipilih(party, semua);
		Component line;
		if (current == null) {
			line = Component.translatable("kultivator.sectevent.ui.pick_offer");
		} else if (regu.isEmpty()) {
			line = Component.translatable("kultivator.sectevent.ui.pick_party");
		} else {
			line = Component.translatable("kultivator.sectevent.ui.chance", percent(chance(regu, current.getIntOr("tier", 1))));
		}
		l.teks(line, x + 2, listBottom + 2, left1 - x - 4, Palette.JADE_DARK);
		Component why = current == null ? Component.translatable("kultivator.sectevent.ui.pick_offer")
				: regu.isEmpty() ? Component.translatable("kultivator.sectevent.ui.pick_party") : alasanBaca();
		TalismanButton.gambar(l, "kirim", x, bottom - BTN_H, left1 - x, BTN_H, Component.translatable("kultivator.sectevent.ui.send"), why == null, why,
				() -> act(0, "mission:" + offer + ":" + ids(party, semua)));
	}

	private void pilihOffer(ListTag board, int index) {
		if (index >= 0 && index < board.size() && board.get(index) instanceof CompoundTag tag && !tag.getBooleanOr("taken", false)) {
			offer = offer == index ? -1 : index;
			UiSound.toggle(offer == index);
		}
	}

	/** One offer of the daily board; its description is the tooltip. */
	private void offerRow(Lukis l, CompoundTag tag, int x0, int y0, int x1, int y1, boolean over, boolean chosen) {
		int type = Math.max(0, Math.min(TYPES.length - 1, tag.getIntOr("type", 0)));
		boolean taken = tag.getBooleanOr("taken", false);
		l.kartu(x0, y0, x1, y1, over && !taken ? 1.0F : 0.0F, chosen && !taken);
		l.g.fill(x0 + 3, y0 + 3, x0 + 7, y0 + 7, taken ? Palette.LOCKED : Palette.SEAL);
		Component name = Brush.bold(Component.translatable("kultivator.sectevent.mission." + TYPES[type]));
		l.teks(name, x0 + 10, y0 + 2, x1 - x0 - 12, taken ? Palette.INK_FAINT : l.gaya.teks);
		Component detail = taken ? Component.translatable("kultivator.sectevent.ui.taken")
				: Component.translatable("kultivator.sectevent.ui.offer_line", tag.getIntOr("tier", 1),
				String.format(Locale.ROOT, "%.1f", DURATION[type] / 24000.0D), Component.translatable("kultivator.sectevent.mission." + TYPES[type] + ".need"));
		l.teks(detail, x0 + 10, y0 + 11, x1 - x0 - 12, l.gaya.teksLemah);
		if (over && !taken) {
			l.tip(List.of(name, Component.translatable("kultivator.sectevent.mission." + TYPES[type] + ".desc"),
					Component.translatable("kultivator.sectevent.mission.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
	}

	private @Nullable CompoundTag currentOffer() {
		ListTag board = sekte().getListOrEmpty("board");
		if (offer < 0 || offer >= board.size() || !(board.get(offer) instanceof CompoundTag tag) || tag.getBooleanOr("taken", false)) {
			return null;
		}
		return tag;
	}

	/** The success chance of DESIGN 20.3.10 from the roster's numbers (the server rolls with the same formula). */
	private static double chance(List<CompoundTag> team, int tier) {
		double best = 0.0D;
		double sum = 0.0D;
		int stars = 0;
		boolean core = false;
		for (CompoundTag entry : team) {
			double m = entry.getIntOr("realm", 0) + entry.getIntOr("stage", 0) / 4.0D + entry.getIntOr("layer", 0) / 9.0D
					+ entry.getIntOr("stars", 0) / 9.0D;
			sum += m;
			if (m > best) {
				best = m;
				stars = entry.getIntOr("stars", 0);
			}
			core |= entry.getIntOr("rank", 1) >= 3;
		}
		double party = best + 0.5D * (sum - best);
		double difficulty = tier + (tier == 9 ? stars / 9.0D : 0.0D);
		return Math.max(0.10D, Math.min(0.95D, 0.45D + 0.12D * (party - (difficulty - 1.0D)) + (core ? 0.05D : 0.0D)));
	}

	// ---------------------------------------------------------------- Tim

	private void tim(Lukis l, int top, int bottom) {
		int ly = kepala(l, Component.translatable("kultivator.sectevent.ui.teams"), x, top, x + w);
		List<CompoundTag> teams = new ArrayList<>();
		for (Tag tag : sekte().getListOrEmpty("teams")) {
			if (tag instanceof CompoundTag team) {
				teams.add(team);
			}
		}
		if (teams.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.sectevent.ui.teams_none"), null, null, x, ly, x + w,
					bottom);
			return;
		}
		long now = now();
		tim.gambar(l, "tim", x, ly, x + w, bottom, teams.size(), 32, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			CompoundTag team = teams.get(i);
			lk.kartu(rx0, ry0, rx1, ry1, 0.0F, false);
			int bw = Math.min(90, TalismanButton.lebar(lk.font, Component.translatable("kultivator.sectevent.ui.recall"), 60, 90));
			int tx1 = rx1 - bw - 6;
			String kind = team.getStringOr("kind", "");
			Component what = switch (kind) {
				case "expedition" -> Component.translatable("kultivator.sectevent.ui.team.expedition",
						Component.translatable("kultivator.sectevent.mission." + TYPES[Math.max(0, Math.min(TYPES.length - 1, team.getIntOr("type", 0)))]),
						team.getIntOr("tier", 1));
				case "envoy" -> Component.translatable("kultivator.sectevent.ui.team.envoy");
				case "forbidden" -> Component.translatable("kultivator.sectevent.ui.team.forbidden");
				default -> Component.translatable("kultivator.sectevent.ui.team.ark");
			};
			lk.teks(Brush.bold(what), rx0 + 4, ry0 + 2, tx1 - rx0 - 4, lk.gaya.teks);
			Component names = Component.empty();
			boolean firstName = true;
			for (Tag each : team.getListOrEmpty("names")) {
				if (each instanceof CompoundTag entry) {
					names = Component.empty().append(names).append(firstName ? Component.empty() : Component.literal(", ")).append(SectEventClient.name(entry, "name"));
					firstName = false;
				}
			}
			long left = team.getLongOr("return_at", 0L) - now;
			Component state = team.getBooleanOr("resolved", false) || left <= 0L ? Component.translatable("kultivator.sectevent.ui.team.returning")
					: Component.translatable("kultivator.sectevent.ui.team.left", String.format(Locale.ROOT, "%.1f", left / 24000.0D), clock(left));
			lk.teks(names, rx0 + 4, ry0 + 11, tx1 - rx0 - 4, lk.gaya.teksLemah);
			lk.teks(state, rx0 + 4, ry0 + 20, tx1 - rx0 - 4, Palette.JADE_DARK);
			int id = team.getIntOr("id", 0);
			boolean open = !team.getBooleanOr("resolved", false);
			boolean over = TalismanButton.gambar(lk, "pulang" + id, rx1 - bw - 3, ry0 + (ry1 - ry0 - BTN_H) / 2, bw, BTN_H,
					Component.translatable("kultivator.sectevent.ui.recall"), open && !readonly(), open ? alasanBaca()
							: Component.translatable("kultivator.sectevent.ui.team.returning"), () -> act(0, "recall:" + id));
			if (over && open) {
				lk.tip(Component.translatable("kultivator.sectevent.ui.recall.tip"));
			}
		}, null, null);
	}

	// ---------------------------------------------------------------- Pencari Bakat

	private boolean envoyReady() {
		return sekte().getLongOr("day", 0L) >= sekte().getLongOr("envoy_ready", Long.MIN_VALUE);
	}

	private void bakat(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag data = sekte();
		int ly = kepala(l, Component.translatable("kultivator.sekte_sambung.bakat.judul"), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sekte_sambung.bakat.pilih"), rx, top, x + w);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> {
			Component why = halangan(e);
			return why != null ? why : e.getBooleanOr("envoy_ok", false) ? null : Component.translatable("kultivator.sectevent.ui.not_allowed");
		}, e -> sectRank(e.getIntOr("rank", 1)));
		envoy.gambar(l, "utusan", semua, rx, ry, x + w, bottom);
		List<Garis> list = new ArrayList<>();
		list.add(Garis.teks(Component.translatable("kultivator.sekte_sambung.bakat.desc"), Palette.INK_SOFT));
		long ready = data.getLongOr("envoy_ready", Long.MIN_VALUE);
		list.add(envoyReady() ? Garis.teks(Component.translatable("kultivator.sekte_sambung.bakat.siap"), Palette.JADE_DARK)
				: Garis.teks(Component.translatable("kultivator.sekte_sambung.bakat.tunggu_ui", ready), Palette.DANGER));
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.envoy.source"), Palette.INK_FAINT));
		gulungan(l, "bakat", teks, list, x, ly, left1, bottom - BTN_H - 3);
		int half = (left1 - x - 2) / 2;
		CompoundTag chosen = satu(envoy);
		Component why = !envoyReady() ? Component.translatable("kultivator.sekte_sambung.bakat.tunggu_ui", ready)
				: chosen == null ? Component.translatable("kultivator.gui43.m09.pilih_murid") : alasanBaca();
		TalismanButton.gambar(l, "utus", x, bottom - BTN_H, half, BTN_H, Component.translatable("kultivator.sekte_sambung.bakat.kirim"), why == null, why,
				() -> {
					CompoundTag c = satu(envoy);
					if (c != null) {
						act(c.getIntOr("id", 0), "envoy");
					}
				});
		Component resetWhy = envoyReady() ? Component.translatable("kultivator.sekte_sambung.bakat.siap") : alasanBaca();
		boolean over = TalismanButton.gambar(l, "percepat", x + half + 2, bottom - BTN_H, half, BTN_H,
				Component.translatable("kultivator.sectevent.ui.envoy.reset", 300), resetWhy == null, resetWhy, () -> act(0, "envoy_reset"));
		if (over && resetWhy == null) {
			l.tip(Component.translatable("kultivator.sekte_sambung.bakat.percepat.tip"));
		}
	}

	// ---------------------------------------------------------------- Terlarang

	private void terlarang(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag info = sekte().getCompoundOrEmpty("forbidden");
		int slots = Math.max(1, info.getIntOr("slots", 3));
		int ly = kepala(l, Component.translatable("kultivator.sectevent.ui.forbidden"), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.forbidden.pick", slots), rx, top, x + w);
		if (forbidden.terpilih().size() > slots || forbiddenSlots != slots) {
			// the number of places changed: a picker for the new count keeps the first ticks
			PemilihOrang next = new PemilihOrang(slots);
			for (String id : forbidden.terpilih()) {
				if (next.terpilih().size() < slots) {
					next.terpilih().add(id);
				}
			}
			forbidden = next;
			forbiddenSlots = slots;
		}
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> {
			Component why = halangan(e);
			return why != null ? why : e.getBooleanOr("qr", false) ? null : Component.translatable("kultivator.sectevent.ui.forbidden.sealed");
		}, e -> e.getBooleanOr("qr", false) ? Component.literal(percent(e.getDoubleOr("fg", 0.0D))) : null);
		forbidden.gambar(l, "terlarang", semua, rx, ry, x + w, bottom);
		List<CompoundTag> chosen = dipilih(forbidden, semua);
		List<Garis> list = new ArrayList<>();
		boolean open = info.getBooleanOr("open", false);
		if (open) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.forbidden.open", clock(info.getLongOr("until", 0L) - now())), Palette.DANGER));
		} else {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.forbidden.closed", info.getLongOr("next", 0L)), Palette.INK_SOFT));
		}
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.forbidden.desc"), Palette.INK_SOFT));
		boolean company = chosen.size() > 1;
		for (CompoundTag entry : chosen) {
			double c = Math.min(0.85D, entry.getDoubleOr("fg", 0.0D) + (company ? 0.10D : 0.0D));
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.forbidden.chance", SectEventClient.name(entry, "name"), percent(c)),
					Palette.JADE_DARK));
		}
		ListTag report = info.getListOrEmpty("report");
		if (!report.isEmpty()) {
			list.add(Garis.judul(Component.translatable("kultivator.sectevent.ui.forbidden.report")));
			for (Tag each : report) {
				if (each instanceof CompoundTag line) {
					boolean ok = line.getBooleanOr("ok", false);
					list.add(Garis.bilah(Component.translatable(ok ? "kultivator.sectevent.ui.forbidden.slip_ok" : line.getBooleanOr("tablet", false)
							? "kultivator.sectevent.ui.forbidden.slip_tablet" : "kultivator.sectevent.ui.forbidden.slip_hurt",
							SectEventClient.name(line, "name"), line.getIntOr("herbs", 0)), ok ? Palette.JADE_DARK : Palette.SEAL_DARK));
				}
			}
		}
		gulungan(l, "terlarang.teks", teks, list, x, ly, left1, bottom - BTN_H - 3);
		Component why = !open ? Component.translatable("kultivator.gui43.m09.terlarang_tutup")
				: chosen.isEmpty() ? Component.translatable("kultivator.gui43.m09.pilih_murid") : alasanBaca();
		TalismanButton.gambar(l, "masuk", x, bottom - BTN_H, left1 - x, BTN_H, Component.translatable("kultivator.sectevent.ui.forbidden.enter"),
				why == null, why, () -> act(0, "forbidden:" + ids(forbidden, semua)));
	}

	private int forbiddenSlots = 3;

	// ---------------------------------------------------------------- Bahtera

	private void bahtera(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag data = sekte();
		CompoundTag info = data.getCompoundOrEmpty("ark");
		int ly = kepala(l, Component.translatable("kultivator.sectevent.ui.ark"), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.ark.pick"), rx, top, x + w);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> {
			Component why = halangan(e);
			return why != null ? why : e.getBooleanOr("ark_ok", false) || e.getIntOr("rank", 1) >= 3 ? null
					: Component.translatable("kultivator.sectevent.ui.not_allowed");
		}, e -> e.getBooleanOr("ark_ok", false) ? Component.translatable("kultivator.sectevent.ui.ark.may") : sectRank(e.getIntOr("rank", 1)));
		ark.gambar(l, "bahtera", semua, rx, ry, x + w, bottom);
		List<Garis> list = new ArrayList<>();
		if (info.getBooleanOr("open", false)) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.ark.open", clock(info.getLongOr("until", 0L) - now())), Palette.onPaper(0x9F6BFF)));
		} else {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.ark.closed"), Palette.INK_SOFT));
		}
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.ark.desc"), Palette.INK_SOFT));
		int bonesY = bottom - BTN_H - 3 - 26;
		gulungan(l, "bahtera.teks", teks, list, x, ly, left1, bonesY - 2);
		// the six bone pieces
		int mask = data.getIntOr("kunpeng", 0);
		l.teks(Component.translatable("kultivator.sectevent.ui.kunpeng", Integer.bitCount(mask & 0x3F)), x + 2, bonesY, left1 - x - 4, Palette.SEAL_DARK);
		int size = 14;
		for (int i = 0; i < 6; i++) {
			int bx = x + 2 + i * (size + 4);
			int by = bonesY + 10;
			boolean have = (mask & 1 << i) != 0;
			l.g.fill(bx, by, bx + size, by + size, have ? Palette.argb(0xFF, 0xF2EAD0) : Palette.argb(0x50, Palette.LOCKED));
			Brush.frame(l.g, bx, by, bx + size, by + size, have ? Palette.GOLD : Palette.argb(0x80, Palette.INK_FAINT));
			int bone = have ? Palette.INK_SOFT : Palette.argb(0x80, Palette.INK_FAINT);
			Brush.line(l.g, bx + size * 0.25F, by + size * 0.75F, bx + size * 0.75F, by + size * 0.25F, 1.4F, bone);
			Brush.disc(l.g, bx + size * 0.22F, by + size * 0.78F, size * 0.12F, bone);
			Brush.disc(l.g, bx + size * 0.78F, by + size * 0.22F, size * 0.12F, bone);
			if (l.di(bx, by, bx + size, by + size)) {
				l.tip(Component.translatable("kultivator.gui43.m09.tulang", i + 1, Component.translatable(have ? "kultivator.gui43.m09.ada"
						: "kultivator.gui43.m09.belum")));
			}
		}
		CompoundTag chosen = satu(ark);
		int half = (left1 - x - 2) / 2;
		Component naikWhy = chosen == null ? Component.translatable("kultivator.gui43.m09.pilih_murid")
				: !chosen.getBooleanOr("ark_ok", false) ? Component.translatable("kultivator.sectevent.ui.not_allowed") : alasanBaca();
		TalismanButton.gambar(l, "naik", x, bottom - BTN_H, half, BTN_H, Component.translatable("kultivator.sectevent.ui.ark.board"), naikWhy == null,
				naikWhy, () -> {
					CompoundTag c = satu(ark);
					if (c != null) {
						act(c.getIntOr("id", 0), "ark");
					}
				});
		Component satuWhy = chosen == null ? Component.translatable("kultivator.gui43.m09.pilih_murid")
				: chosen.getIntOr("rank", 1) < 3 || mask != 0x3F ? Component.translatable("kultivator.sectevent.ui.kunpeng.tip") : alasanBaca();
		boolean over = TalismanButton.gambar(l, "satukan", x + half + 2, bottom - BTN_H, half, BTN_H,
				Component.translatable("kultivator.sectevent.ui.kunpeng.unite"), satuWhy == null, satuWhy, () -> {
					CompoundTag c = satu(ark);
					if (c != null) {
						act(c.getIntOr("id", 0), "kunpeng");
					}
				});
		if (over && satuWhy == null) {
			l.tip(List.of(Component.translatable("kultivator.sectevent.ui.kunpeng.tip"),
					Component.translatable("kultivator.sectevent.item.kunpeng_bone_fragment.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
	}

	// ---------------------------------------------------------------- Misi Cerita (module X)

	private void cerita(Lukis l, int top, int left1, int rx, int bottom) {
		MisiClient.request(false);
		int teamMax = Math.max(1, Math.min(4, MisiClient.state().getIntOr("tim", 4)));
		int ly = kepala(l, Component.translatable("kultivator.sekte.misi.ui.papan", MisiClient.state().getIntOr("selesai", 0)), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sekte.misi.ui.tim", teamMax), rx, top, x + w);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> {
			Component why = halangan(e);
			return why != null ? why : e.getIntOr("rank", 1) < 1 ? Component.translatable("kultivator.sectevent.ui.not_allowed") : null;
		}, e -> sectRank(e.getIntOr("rank", 1)));
		if (story.terpilih().size() > teamMax) {
			story.terpilih().retainAll(new ArrayList<>(story.terpilih()).subList(0, teamMax));
		}
		story.gambar(l, "cerita.tim", semua, rx, ry, x + w, bottom);
		int stanceY = bottom - 2 * BTN_H - 3;
		int previewY = stanceY - 11;
		List<CompoundTag> offers = MisiTab.offers();
		if (!MisiTab.on()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable("kultivator.sekte.misi.ui.mati"), null, null, x, ly, left1, previewY);
		} else if (offers.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.PAPAN, Component.translatable("kultivator.sekte.misi.ui.papan_kosong"), null, null, x, ly, left1,
					previewY);
		} else {
			cerita.gambar(l, "cerita", x, ly, left1, previewY - 2, offers.size(), MisiTab.CARD_H,
					(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> MisiTab.offerRow(lk, offers.get(i), rx0, ry0, rx1, ry1, sorot, i == storyOffer),
					() -> pilihCerita(cerita.pilihan()), this::pilihCerita);
		}
		String souls = storySouls(semua);
		String key = storyOffer + "|" + stance + "|" + souls;
		if (!key.equals(previewKey)) {
			previewKey = key;
			if (storyOffer >= 0 && !souls.isEmpty()) {
				MisiClient.send("cek", storyOffer, stance + "|" + souls);
			}
		}
		MisiTab.preview(l, x + 2, previewY, left1 - x - 4, storyOffer, stance, souls, !souls.isEmpty());
		int third = (left1 - x - 4) / 3;
		for (int i = 0; i < MisiTab.STANCES.length; i++) {
			String id = MisiTab.STANCES[i];
			boolean over = TalismanButton.gambar(l, "sikap." + id, x + i * (third + 2), stanceY, third, BTN_H,
					Component.translatable("kultivator.sekte.misi.sikap." + id), true, null, () -> {
						stance = id;
						UiSound.toggle(true);
					}, false, stance.equals(id));
			if (over) {
				l.tip(Component.translatable("kultivator.sekte.misi.sikap." + id + ".desc"));
			}
		}
		Component why = storyBlock(semua);
		boolean over = TalismanButton.gambar(l, "cerita.kirim", x, bottom - BTN_H, left1 - x, BTN_H, Component.translatable("kultivator.sekte.misi.ui.kirim"),
				why == null, why, () -> {
					if (readonly()) {
						UiSound.denied();
						return;
					}
					MisiClient.send("kirim", storyOffer, stance + "|" + storySouls(semua));
					storyOffer = -1;
					story.terpilih().clear();
				});
		if (over && why == null) {
			l.tip(Component.translatable("kultivator.sekte.misi.ui.kirim.tip"));
		}
	}

	private void pilihCerita(int index) {
		CompoundTag tag = MisiTab.offer(index);
		if (tag != null && !tag.getBooleanOr("taken", false)) {
			storyOffer = storyOffer == index ? -1 : index;
			UiSound.toggle(storyOffer == index);
		}
	}

	/** Why the story mission cannot be sent now (null = it can). */
	private @Nullable Component storyBlock(List<PemilihOrang.Orang> semua) {
		CompoundTag offer = MisiTab.offer(storyOffer);
		if (offer == null || offer.getBooleanOr("taken", false)) {
			return Component.translatable("kultivator.sekte.misi.ui.pilih_misi");
		}
		if (MisiTab.full()) {
			return Component.translatable("kultivator.sekte.misi.ui.penuh", MisiClient.state().getIntOr("maks", 2));
		}
		if (story.pilihan(semua).isEmpty()) {
			return Component.translatable("kultivator.sekte.misi.ui.pilih_tim");
		}
		CompoundTag preview = MisiClient.state().getCompoundOrEmpty("pratinjau");
		if (preview.getIntOr("offer", -1) == storyOffer && (!preview.getStringOr("blocker", "").isEmpty() || !preview.getBooleanOr("rank_ok", true))) {
			return Component.translatable("kultivator.sekte.misi.ui.jenjang_kurang");
		}
		return alasanBaca();
	}

	private String storySouls(List<PemilihOrang.Orang> semua) {
		StringBuilder souls = new StringBuilder();
		for (PemilihOrang.Orang o : story.pilihan(semua)) {
			souls.append(souls.isEmpty() ? "" : ",").append(o.id());
		}
		return souls.toString();
	}

	// ---------------------------------------------------------------- Kabar Misi (module X)

	private void kabar(Lukis l, int top, int bottom) {
		MisiClient.request(false);
		List<CompoundTag> missions = MisiTab.missions();
		List<CompoundTag> history = MisiTab.history();
		int ly = kepala(l, Component.translatable("kultivator.sekte.misi.ui.kabar", missions.size(), Math.max(1, MisiClient.state().getIntOr("maks", 2))),
				x, top, x + w);
		int split = history.isEmpty() ? bottom - 30 : ly + Math.min(Math.max(1, missions.size()) * MisiTab.MISSION_H, (bottom - ly) * 55 / 100);
		if (missions.isEmpty()) {
			l.samar(Component.translatable("kultivator.sekte.misi.ui.tidak_ada"), x + 2, ly + 2, w - 4);
		} else {
			int bw = Math.min(90, TalismanButton.lebar(font, Component.translatable("kultivator.sekte.misi.ui.tinggal"), 60, 90));
			kabar.gambar(l, "kabar", x, ly, x + w, split, missions.size(), MisiTab.MISSION_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				CompoundTag mission = missions.get(i);
				MisiTab.missionRow(lk, mission, rx0, ry0, rx1, rx1 - bw - 6);
				int id = mission.getIntOr("id", 0);
				boolean open = !mission.getBooleanOr("finished", false) && !mission.getBooleanOr("ditarik", false);
				boolean hurt = MisiTab.hurt(mission);
				Component why = open ? alasanBaca() : Component.translatable("kultivator.sekte.misi.ui.pulang_jalan");
				boolean over = TalismanButton.gambar(lk, "tarik" + id, rx1 - bw - 3, ry0 + 1, bw, BTN_H, Component.translatable("kultivator.sekte.misi.ui.tarik"),
						why == null, why, () -> MisiClient.send("tarik", id, ""));
				if (over && why == null) {
					lk.tip(Component.translatable(hurt ? "kultivator.sekte.misi.ui.tarik.tip_luka" : "kultivator.sekte.misi.ui.tarik.tip"));
				}
				if (open && hurt) {
					boolean overT = TalismanButton.gambar(lk, "tinggal" + id, rx1 - bw - 3, ry0 + 2 + BTN_H, bw, BTN_H,
							Component.translatable("kultivator.sekte.misi.ui.tinggal"), why == null, why, () -> MisiClient.send("tinggal", id, ""), true, false);
					if (overT && why == null) {
						lk.tip(Component.translatable("kultivator.sekte.misi.ui.tinggal.tip").withStyle(ChatFormatting.RED));
					}
				}
			}, null, null);
		}
		int hy = kepala(l, Component.translatable("kultivator.sekte.misi.ui.laporan"), x, split + 2, x + w);
		if (history.isEmpty()) {
			l.samar(Component.translatable("kultivator.sekte.misi.ui.riwayat_kosong"), x + 2, hy + 2, w - 4);
			return;
		}
		int bw = Math.min(70, TalismanButton.lebar(font, Component.translatable("kultivator.sekte.misi.ui.baca"), 40, 70));
		riwayat.gambar(l, "riwayat", x, hy, x + w, bottom, history.size(), MisiTab.ROW_H, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
			CompoundTag row = history.get(i);
			MisiTab.historyRow(lk, row, i, rx0, ry0, rx1, ry1, rx1 - bw - 4);
			int index = row.getIntOr("i", 0);
			TalismanButton.gambar(lk, "baca" + i, rx1 - bw - 1, ry0 + 1, bw, ry1 - ry0 - 2, Component.translatable("kultivator.sekte.misi.ui.baca"), true,
					null, () -> MisiClient.send("baca", index, ""));
		}, () -> {
			int i = riwayat.pilihan();
			if (i >= 0 && i < history.size()) {
				MisiClient.send("baca", history.get(i).getIntOr("i", 0), "");
			}
		}, null);
	}
}
