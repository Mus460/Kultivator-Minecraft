package dev.kultivator.client.sectevent;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.turnamen.TurnamenClient;
import dev.kultivator.data.Realm;
import dev.kultivator.sekte.turnamen.TurnamenPayloads;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kegiatan › Arena of the hall owner (DESIGN 20.3.12-20.3.15, 36.2.3, 43.5; G40: the old "Turnamen" screen is merged
 * here). Section chips: Turnamen (the schedule, who enters, the bracket while it runs, the all-time Daftar Kuat, module
 * T's season board of Elo ratings, the roll of champions, the inter-sect record and a waiting NPC sect's challenge with
 * Terima / Tolak, which stay on the page: R12), Alam Hampa (the Void God Realm's queue and best-floor board), Menara (the
 * Blazing Sky tower's rooms and trainees) and Perang (the Hundred Dynasties War's youths and bracket).
 */
public class ArenaPage extends SectEventPage {

	private static final String[] SEKSI = {"kultivator.sectevent.ui.tab.tourney", "kultivator.sectevent.ui.tab.void",
			"kultivator.sectevent.ui.tab.tower", "kultivator.sectevent.ui.tab.war"};
	private static final int TURNAMEN = 0;
	private static final int HAMPA = 1;
	private static final int MENARA = 2;
	private static final long TANYA_MS = 3000L;

	private int seksi;
	private int floor = 1;
	private long tanya = Long.MIN_VALUE / 2;
	/** The souls the server lists as entering the tournament (the picker mirrors it; a tick toggles on the server). */
	private Set<String> masuk = new HashSet<>();
	private List<PemilihOrang.Orang> peserta = List.of();
	private final PemilihOrang entrants = new PemilihOrang(99).onUbah(this::ubahPeserta);
	private final PemilihOrang hampa = new PemilihOrang(0);
	private final PemilihOrang menara = new PemilihOrang(0);
	private final DaftarGulir teks = new DaftarGulir();
	private final BracketView bracket = new BracketView();

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.arena");
	}

	@Override
	public int glyph() {
		return Glyphs.TREASURE;
	}

	@Override
	public String helpTopic() {
		return seksi == HAMPA || seksi == MENARA ? "aula" : "turnamen";
	}

	private static CompoundTag tourney() {
		return sekte().getCompoundOrEmpty("tourney");
	}

	private static CompoundTag war() {
		return sekte().getCompoundOrEmpty("war");
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		tanya = Util.getMillis();
		TurnamenClient.ask(TurnamenPayloads.Minta.ASK);
		super.onShow(fokus);
	}

	@Override
	public void tick() {
		// module T's board while its section is shown (every 3 s, as the old card did)
		if (seksi == TURNAMEN && Util.getMillis() - tanya > TANYA_MS) {
			tanya = Util.getMillis();
			TurnamenClient.ask(TurnamenPayloads.Minta.ASK);
		}
	}

	// ================================================================== drawing

	@Override
	protected void isi(Lukis l) {
		int top = seksi(l, SEKSI, seksi, i -> seksi = i);
		int colW = Math.max(120, Math.round(w * 0.55F));
		int left1 = x + colW - 2;
		int rx = x + colW + 2;
		int bottom = y + h;
		switch (seksi) {
			case TURNAMEN -> turnamen(l, top, left1, rx, bottom);
			case HAMPA -> hampa(l, top, left1, rx, bottom);
			case MENARA -> menara(l, top, left1, rx, bottom);
			default -> perang(l, top, left1, rx, bottom);
		}
	}

	// ---------------------------------------------------------------- Turnamen (+ module T)

	private void turnamen(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag tourney = tourney();
		CompoundTag t = TurnamenClient.data();
		CompoundTag live = t == null ? new CompoundTag() : t.getCompoundOrEmpty("live");
		int y0 = tantangan(l, t, top);
		CompoundTag bagan = tourney.contains("bracket") ? tourney.getCompoundOrEmpty("bracket")
				: live.getBooleanOr("running", false) ? live.getCompoundOrEmpty("bracket") : null;
		if (bagan != null) {
			Component name = Component.translatable(tourney.getBooleanOr("elite", false) ? "kultivator.sectevent.tourney.elite"
					: "kultivator.sectevent.tourney.name");
			y0 = kepala(l, name, x, y0, x + w);
			if (live.contains("now_a")) {
				Component a = live.read("now_a", ComponentSerialization.CODEC).orElse(Component.empty());
				Component b = live.read("now_b", ComponentSerialization.CODEC).orElse(Component.empty());
				l.teks(Component.translatable("kultivator.sekte.turnamen.ui.sekarang", a, b), x + 2, y0, w - 4, Palette.SEAL_DARK);
				y0 += 11;
			}
			bracket.gambar(l, "bagan", bagan, x, y0, x + w, bottom);
			return;
		}
		int ly = kepala(l, Component.translatable("kultivator.sectevent.tourney.name"), x, y0, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.tourney.entrants"), rx, y0, x + w);
		masuk = new HashSet<>();
		for (CompoundTag e : SectEventClient.roster()) {
			if (e.getIntOr("rank", 1) >= 1 && !e.getBooleanOr("excluded", false)) {
				masuk.add(e.getStringOr("soul", ""));
			}
		}
		peserta = orang(e -> e.getIntOr("rank", 1) >= 1, e -> alasanBaca(), e -> sectRank(e.getIntOr("rank", 1)));
		entrants.terpilih().clear();
		entrants.terpilih().addAll(masuk);
		entrants.gambar(l, "peserta", peserta, rx, ry, x + w, bottom);
		long day = sekte().getLongOr("day", 0L);
		List<Garis> list = new ArrayList<>();
		long next = tourney.getLongOr("next", 0L);
		list.add(next <= day ? Garis.teks(Component.translatable("kultivator.sectevent.ui.tourney.due"), Palette.SEAL_DARK)
				: Garis.teks(Component.translatable("kultivator.sectevent.ui.tourney.next", next, next - day), Palette.INK_SOFT));
		if (tourney.getBooleanOr("elite_next", false)) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tourney.elite_next"), Palette.onPaper(0x9F6BFF)));
		}
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tourney.held", tourney.getIntOr("count", 0)), Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tourney.rules"), Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tourney.source"), Palette.INK_FAINT));
		list.add(Garis.judul(Component.translatable("kultivator.sectevent.ui.ranking")));
		slips(list, tourney.getListOrEmpty("ranking"), "points");
		musim(list, t);
		gulungan(l, "turnamen.teks", teks, list, x, ly, left1, bottom - 2 * BTN_H - 5);
		boolean manual = day >= tourney.getLongOr("manual_ready", Long.MIN_VALUE);
		Component startWhy = manual ? alasanBaca() : Component.translatable("kultivator.sectevent.ui.tourney.start.tip", 3);
		boolean over = TalismanButton.gambar(l, "mulai", x, bottom - 2 * BTN_H - 2, left1 - x, BTN_H,
				Component.translatable("kultivator.sectevent.ui.tourney.start"), startWhy == null, startWhy, () -> act(0, "tourney_start"));
		if (over && startWhy == null) {
			l.tip(Component.translatable("kultivator.sectevent.ui.tourney.start.tip", 3));
		}
		Component skipWhy = day >= next ? alasanBaca() : Component.translatable("kultivator.sectevent.ui.tourney.next", next, next - day);
		TalismanButton.gambar(l, "lewati", x, bottom - BTN_H, left1 - x, BTN_H, Component.translatable("kultivator.sectevent.ui.tourney.skip"),
				skipWhy == null, skipWhy, () -> act(0, "tourney_skip"));
	}

	/** A tick in the entrants list toggles that disciple's place on the server (the list mirrors the server). */
	private void ubahPeserta() {
		for (PemilihOrang.Orang o : peserta) {
			boolean now = entrants.terpilih().contains(o.id());
			if (now != masuk.contains(o.id())) {
				CompoundTag e = entri(o.id());
				if (e != null) {
					act(e.getIntOr("id", 0), "exclude");
				}
			}
		}
	}

	/** A waiting NPC sect's challenge (module T) as an urgent strip with Terima / Tolak; returns the y under it. */
	private int tantangan(Lukis l, @Nullable CompoundTag t, int top) {
		if (t == null || !t.contains("pending")) {
			return top;
		}
		Component sect = t.read("pending", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		int bw = Math.min(70, TalismanButton.lebar(font, Component.translatable("kultivator.sekte.turnamen.antar.terima"), 50, 70));
		l.g.fill(x, top, x + w, top + BTN_H + 2, Palette.argb(0x40 + Math.round(0x30 * l.denyut()), Palette.SEAL));
		l.teks(Brush.bold(Component.translatable("kultivator.sekte.turnamen.ui.tantangan", sect)), x + 3, top + 4, w - 2 * bw - 12, Palette.SEAL_DARK);
		Component why = alasanBaca();
		TalismanButton.gambar(l, "antar.terima", x + w - 2 * bw - 3, top + 1, bw, BTN_H, Component.translatable("kultivator.sekte.turnamen.antar.terima"),
				why == null, why, () -> TurnamenClient.ask(TurnamenPayloads.Minta.ACCEPT), true, false);
		TalismanButton.gambar(l, "antar.tolak", x + w - bw - 1, top + 1, bw, BTN_H, Component.translatable("kultivator.sekte.turnamen.antar.tolak"),
				why == null, why, () -> TurnamenClient.ask(TurnamenPayloads.Minta.REFUSE));
		return top + BTN_H + 5;
	}

	/** Module T's lines: the season board (Elo, wins, losses), the roll of champions, the tribune and the inter-sect record. */
	private static void musim(List<Garis> list, @Nullable CompoundTag t) {
		if (t == null) {
			list.add(Garis.teks(Component.translatable(TurnamenClient.aktif() ? "kultivator.sekte.turnamen.ui.memuat" : "kultivator.sekte.turnamen.ui.mati"),
					Palette.INK_FAINT));
			return;
		}
		int season = t.getIntOr("season", 0);
		list.add(Garis.judul(season > 0 ? Component.translatable("kultivator.sekte.turnamen.ui.judul_musim", season)
				: Component.translatable("kultivator.sekte.turnamen.ui.papan")));
		ListTag board = t.getListOrEmpty("board");
		if (board.isEmpty()) {
			list.add(Garis.teks(Component.translatable("kultivator.sekte.turnamen.papan.kosong"), Palette.INK_FAINT));
		}
		int rank = 0;
		for (Tag tag : board) {
			if (tag instanceof CompoundTag entry) {
				rank++;
				Component name = entry.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				Component line = Component.literal(rank + ". ").append(name).append("  ").append(Component.translatable("kultivator.sekte.turnamen.ui.elo_baris",
						entry.getIntOr("e", 1000), entry.getIntOr("w", 0), entry.getIntOr("l", 0)));
				list.add(Garis.bilah(line, rank == 1 ? Palette.GOLD_DARK : Palette.INK));
			}
		}
		ListTag roll = t.getListOrEmpty("roll");
		if (!roll.isEmpty()) {
			list.add(Garis.judul(Component.translatable("kultivator.sekte.turnamen.papan.juara")));
			int i = 0;
			for (Tag tag : roll) {
				if (tag instanceof CompoundTag entry) {
					Component name = entry.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
					list.add(Garis.bilah(Component.translatable("kultivator.sekte.turnamen.papan.juara_baris", entry.getIntOr("s", 0),
							Component.translatable("kultivator.sekte.turnamen.jenis." + entry.getStringOr("k", "biasa")), name), i++ == 0 ? Palette.GOLD_DARK
							: Palette.INK));
				}
			}
		}
		list.add(Garis.teks(Component.translatable("kultivator.sekte.turnamen.papan.antar", t.getIntOr("antar_w", 0), t.getIntOr("antar_l", 0)),
				Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable(t.getBooleanOr("tribun", false) ? "kultivator.sekte.turnamen.ui.tribun_ada"
				: "kultivator.sekte.turnamen.ui.tribun_belum"), Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable("kultivator.sekte.turnamen.sumber"), Palette.INK_FAINT));
	}

	/** The top ten on bamboo slips ({@code key}: points or best floor). */
	private static void slips(List<Garis> list, ListTag ranking, String key) {
		int index = 0;
		for (Tag tag : ranking) {
			if (tag instanceof CompoundTag entry && index < 10) {
				index++;
				Component text = Component.translatable(key.equals("points") ? "kultivator.sectevent.ui.ranking.line" : "kultivator.sectevent.ui.void.line",
						index, SectEventClient.name(entry, "name"), entry.getIntOr(key, 0), entry.getIntOr("firsts", 0));
				list.add(Garis.bilah(text, index == 1 ? Palette.SEAL_DARK : Palette.INK));
			}
		}
		if (index == 0) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.ranking.empty"), Palette.INK_FAINT));
		}
	}

	// ---------------------------------------------------------------- Alam Hampa

	private static boolean dekat(CompoundTag e) {
		return e.getBooleanOr("near", false) && e.getBooleanOr("free", false);
	}

	private void hampa(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag info = sekte().getCompoundOrEmpty("void");
		int ly = kepala(l, Component.translatable("kultivator.sectevent.ui.void"), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.void.pick"), rx, top, x + w);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> {
			if (!dekat(e)) {
				return halangan(e) != null ? halangan(e) : Component.translatable("kultivator.sectevent.ui.far");
			}
			if (e.getIntOr("rank", 1) < 2) {
				return Component.translatable("kultivator.sectevent.ui.not_allowed");
			}
			return e.getBooleanOr("void_today", false) ? Component.translatable("kultivator.gui43.m09.hampa_hari_ini") : null;
		}, e -> Component.translatable("kultivator.sectevent.ui.void.best_short", e.getIntOr("void_best", 0)));
		hampa.gambar(l, "hampa", semua, rx, ry, x + w, bottom);
		CompoundTag chosen = satu(hampa);
		List<Garis> list = new ArrayList<>();
		if (info.contains("climber")) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.void.climbing", SectEventClient.name(info, "climber"), info.getIntOr("floor", 1),
					info.getIntOr("cleared", 0)), Palette.onPaper(0x9F6BFF)));
		}
		ListTag queue = info.getListOrEmpty("queue");
		if (!queue.isEmpty()) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.void.queue", queue.size()), Palette.INK_SOFT));
		}
		if (chosen != null) {
			int next = chosen.getIntOr("void_best", 0) + 1;
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.void.next", next, floorName(next)), Palette.JADE_DARK));
		}
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.void.desc"), Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.void.source"), Palette.INK_FAINT));
		list.add(Garis.judul(Component.translatable("kultivator.sectevent.ui.void.board")));
		slips(list, info.getListOrEmpty("board"), "best");
		gulungan(l, "hampa.teks", teks, list, x, ly, left1, bottom - BTN_H - 3);
		Component why = chosen == null ? Component.translatable("kultivator.gui43.m09.pilih_murid") : alasanBaca();
		TalismanButton.gambar(l, "hampa.masuk", x, bottom - BTN_H, left1 - x, BTN_H, Component.translatable("kultivator.sectevent.ui.void.enter"),
				why == null, why, () -> {
					CompoundTag c = satu(hampa);
					if (c != null) {
						act(c.getIntOr("id", 0), "void");
					}
				});
	}

	/** The client copy of the floor names (DESIGN 20.3.14). */
	private static Component floorName(int floor) {
		String key;
		if (floor <= 9) {
			key = "start";
		} else if (floor <= 19) {
			key = "stairs";
		} else if (floor <= 29) {
			key = "clouds";
		} else if (floor <= 39) {
			key = "palace";
		} else if (floor <= 49) {
			key = "peak";
		} else if (floor == 50) {
			key = "border";
		} else if (floor <= 59) {
			key = "beyond";
		} else if (floor == 60) {
			key = "devil";
		} else {
			return Component.translatable("kultivator.sectevent.void.floor.bottomless", floor - 60);
		}
		return Component.translatable("kultivator.sectevent.void.floor." + key);
	}

	// ---------------------------------------------------------------- Menara

	private List<CompoundTag> rooms() {
		List<CompoundTag> list = new ArrayList<>();
		for (Tag tag : sekte().getCompoundOrEmpty("tower").getListOrEmpty("rooms")) {
			if (tag instanceof CompoundTag room) {
				list.add(room);
			}
		}
		return list;
	}

	private void menara(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag info = sekte().getCompoundOrEmpty("tower");
		int ly = kepala(l, Component.translatable("kultivator.sectevent.ui.tower"), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.tower.pick"), rx, top, x + w);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> e.getBooleanOr("near", false) ? null : Component.translatable("kultivator.sectevent.ui.far"),
				e -> e.getBooleanOr("tower", false) ? Component.translatable("kultivator.sectevent.ui.tower.training")
						: Realm.byOrdinal(e.getIntOr("realm", 0)).displayName());
		menara.gambar(l, "menara", semua, rx, ry, x + w, bottom);
		int rooms = rooms().size();
		floor = Math.max(1, Math.min(Math.max(1, rooms), floor));
		List<Garis> list = new ArrayList<>();
		list.add(rooms == 0 ? Garis.teks(Component.translatable("kultivator.sectevent.ui.tower.no_rooms"), Palette.DANGER)
				: Garis.teks(Component.translatable("kultivator.sectevent.ui.tower.rooms", rooms), Palette.INK_SOFT));
		if (info.getBooleanOr("free", false)) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tower.free_day"), Palette.JADE_DARK));
		}
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tower.desc"), Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.tower.source"), Palette.INK_FAINT));
		ListTag trainees = info.getListOrEmpty("trainees");
		if (!trainees.isEmpty()) {
			list.add(Garis.judul(Component.translatable("kultivator.sectevent.ui.tower.trainees")));
			for (Tag tag : trainees) {
				if (tag instanceof CompoundTag entry) {
					boolean out = entry.getBooleanOr("out", false);
					list.add(Garis.bilah(Component.translatable(out ? "kultivator.sectevent.ui.tower.out" : "kultivator.sectevent.ui.tower.line",
							SectEventClient.name(entry, "name"), entry.getIntOr("floor", 1)), out ? Palette.SEAL_DARK : Palette.INK));
				}
			}
		}
		int floorsY = bottom - 2 * BTN_H - 3;
		gulungan(l, "menara.teks", teks, list, x, ly, left1, floorsY - 3);
		int buttons = Math.max(1, Math.min(7, rooms));
		int each = Math.max(TalismanButton.MIN_W, (left1 - x - (buttons - 1) * 2) / buttons);
		for (int i = 1; i <= Math.min(7, rooms); i++) {
			int n = i;
			boolean over = TalismanButton.gambar(l, "lantai" + n, x + (n - 1) * (each + 2), floorsY, each, BTN_H, Component.literal(String.valueOf(n)), true,
					null, () -> floor = n, false, floor == n);
			if (over) {
				l.tip(Component.translatable("kultivator.sectevent.ui.tower.floor", n, String.format(Locale.ROOT, "%.1f", 1.2D + 0.2D * n), n));
			}
		}
		CompoundTag chosen = satu(menara);
		int half = (left1 - x - 2) / 2;
		Component trainWhy = rooms == 0 ? Component.translatable("kultivator.sectevent.ui.tower.no_rooms")
				: chosen == null ? Component.translatable("kultivator.gui43.m09.pilih_murid")
				: !chosen.getBooleanOr("tower_ok", true) ? Component.translatable("kultivator.sectevent.tower.rank") : alasanBaca();
		TalismanButton.gambar(l, "latih", x, bottom - BTN_H, half, BTN_H, Component.translatable("kultivator.sectevent.ui.tower.train", floor),
				trainWhy == null, trainWhy, () -> {
					CompoundTag c = satu(menara);
					if (c != null) {
						act(c.getIntOr("id", 0), "tower:" + floor);
					}
				});
		Component stopWhy = chosen == null || !chosen.getBooleanOr("tower", false) ? Component.translatable("kultivator.gui43.m09.tidak_berlatih")
				: alasanBaca();
		TalismanButton.gambar(l, "henti", x + half + 2, bottom - BTN_H, half, BTN_H, Component.translatable("kultivator.sectevent.ui.tower.stop"),
				stopWhy == null, stopWhy, () -> {
					CompoundTag c = satu(menara);
					if (c != null) {
						act(c.getIntOr("id", 0), "tower_stop");
					}
				});
	}

	// ---------------------------------------------------------------- Perang

	private void perang(Lukis l, int top, int left1, int rx, int bottom) {
		CompoundTag war = war();
		if (war.contains("bracket")) {
			int y0 = kepala(l, Component.translatable("kultivator.sectevent.war.title"), x, top, x + w);
			bracket.gambar(l, "bagan.perang", war.getCompoundOrEmpty("bracket"), x, y0, x + w, bottom);
			return;
		}
		int ly = kepala(l, Component.translatable("kultivator.sectevent.war.title"), x, top, left1);
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.war.youths"), rx, top, x + w);
		boolean today = war.getBooleanOr("today", false);
		int state = war.getIntOr("state", 0);
		List<Garis> list = new ArrayList<>();
		if (today && state == 0) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.war.waiting", war.getListOrEmpty("youths").size()), Palette.SEAL_DARK));
		} else if (state == 2) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.war.done"), Palette.JADE_DARK));
		} else if (!today) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.war.not_today"), Palette.INK_SOFT));
		}
		if (war.contains("champion")) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.war.champion", SectEventClient.name(war, "champion")), Palette.GOLD_DARK));
		}
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.war.desc"), Palette.INK_SOFT));
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.war.source"), Palette.INK_FAINT));
		gulungan(l, "perang.teks", teks, list, x, ly, left1, bottom - BTN_H - 3);
		List<Garis> youths = new ArrayList<>();
		if (today) {
			for (Tag tag : war.getListOrEmpty("youths")) {
				if (tag instanceof CompoundTag youth) {
					youths.add(Garis.bilah(Component.translatable("kultivator.sectevent.ui.war.youth", SectEventClient.name(youth, "name"),
							youth.getIntOr("tekad", 0)), Palette.INK));
				}
			}
		}
		if (youths.isEmpty()) {
			l.samar(Component.translatable("kultivator.gui43.m09.tanpa_pemuda"), rx + 2, ry + 2, x + w - rx - 4);
		} else {
			gulungan(l, "perang.pemuda", pemuda, youths, rx, ry, x + w, bottom);
		}
		Component why = !today || state != 0 ? Component.translatable("kultivator.sectevent.ui.war.not_today") : alasanBaca();
		TalismanButton.gambar(l, "perang.mulai", x, bottom - BTN_H, left1 - x, BTN_H, Component.translatable("kultivator.sectevent.ui.war.start"),
				why == null, why, () -> act(0, "war_start"));
	}

	private final DaftarGulir pemuda = new DaftarGulir();
}
