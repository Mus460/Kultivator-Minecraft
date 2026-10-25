package dev.kultivator.client.sekte.kebun;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.wilayah.GulirIsi;
import dev.kultivator.sekte.kebun.Kebun;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sekte › Wilayah › Kebun &amp; Tambang (DESIGN 36.2.10 "UI", 43.5, 43.12.1: was the board screen of the Kebun Obat &amp;
 * Tambang Roh). One scroll with two stacked headings (43.0.4) instead of the old two page tabs:
 * <ul>
 * <li><b>Kebun</b>: the spirit beds as a grid of cards (the herb, its stage, a growth bar in the herb's own colour, the
 * replant and gardener marks); the chosen bed's panel (sow one of the nine seeds from the Lumbung or the pack, harvest,
 * uproot, replant on or off); the gardener slots (one per two beds) with each gardener's streak; "Panen semua";</li>
 * <li><b>Tambang</b>: the shaft drawn as nine strata with the depth reached and the cave-in risk, the shift slots with
 * streak, today's and the total haul, the yield table of the level and the cost of going deeper;</li>
 * <li>the board's log.</li>
 * </ul>
 * Every button sends one {@code KebunPayloads.Action}; the server answers with a fresh {@code Open} (kept in
 * {@link #terima}), so the page never guesses. Choosing a worker opens the kit's people picker as a pop-up (R19: Esc, × or a
 * click outside cancel; a click inside never closes it by accident) with the disciples near the stele, their roots
 * (Kayu/Air for the garden, Tanah/Logam for the mine: ×1.1), rest and mood. Seals are ≥ 16×12 (R9), text 1.0.
 */
public class KebunScreen extends Page {

	private static final String K = "kultivator.sekte.kebun.ui.";
	private static final String M = "kultivator.gui43.m10.kebun.";
	private static final String[] STAGE_GLYPH = {"·", "芽", "苗", "蕾", "華"};
	private static final int SEL_H = 44;
	private static final int CHIP_H = 26;
	private static final long DIMINTA_MS = 3000L;

	/** The last state from the server (shared: the page may be rebuilt) and the page it named. */
	private static CompoundTag data = new CompoundTag();
	private static int halamanTerakhir = Kebun.PAGE_GARDEN;
	private static boolean keTambang;
	private static long dimintaAt = Long.MIN_VALUE / 2;
	private static @Nullable KebunScreen tampil;

	private int selected = -1;
	/** The worker picker while choosing: "rawat" or "gali" and the slot (1-based). */
	private String picking = "rawat";
	private int pickSlot;
	private List<PemilihOrang.Orang> calon = List.of();
	private final PemilihOrang pemilih = new PemilihOrang(1);
	private final GulirIsi gulir = new GulirIsi();
	private final Map<String, ItemStack> icons = new HashMap<>();

	public KebunScreen() {
		pemilih.onUbah(this::dipilih);
		pemilih.kolom(List.of(Component.translatable(M + "akar")));
	}

	// ================================================================== data

	/** A fresh state from the server ({@code KebunPayloads.Open}). */
	static void terima(int page, CompoundTag d) {
		data = d == null ? new CompoundTag() : d.copy();
		halamanTerakhir = page == Kebun.PAGE_MINE ? Kebun.PAGE_MINE : Kebun.PAGE_GARDEN;
		// opened from the world (the mine's mouth, a click on soil, /kultivasi perguruan kebun tambang): show the part it names
		keTambang |= halamanTerakhir == Kebun.PAGE_MINE && !dimintaBaru();
		KebunScreen shown = tampil;
		if (shown != null && shown.selected >= data.getIntOr("plots", 0)) {
			shown.selected = -1;
		}
	}

	/** True while this page is on screen. */
	static boolean ditampilkan() {
		return tampil != null;
	}

	/** True when the page asked the server in the last 3 s (its answer must not reopen the Gulungan). */
	static boolean dimintaBaru() {
		return Util.getMillis() - dimintaAt <= DIMINTA_MS;
	}

	/** A button of the page (the server checks the owner and everything else). */
	private static void kirim(String action, int a, String b) {
		dimintaAt = Util.getMillis();
		KebunClient.send(action, a, b);
	}

	// ================================================================== identity and life

	@Override
	public Component title() {
		return Component.translatable(K + "judul");
	}

	@Override
	public int glyph() {
		return Glyphs.ALKIMIA;
	}

	@Override
	public String helpTopic() {
		return "kebun_tambang";
	}

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		tampil = this;
		kirim("buka", halamanTerakhir, "");
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		if (tampil == this) {
			tampil = null;
		}
		pemilih.tutup();
	}

	@Override
	public boolean mundur() {
		return pemilih.tutup();
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	// ================================================================== drawing

	@Override
	protected void lukis(Lukis l) {
		if (data.isEmpty()) {
			// P5: until the first answer to "buka" (no made-up "no plots yet")
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.gui43.konteks.memuat"), null, null, x, y, x + w, y + h);
			return;
		}
		long now = Util.getMillis();
		int x0 = x + 2;
		int x1 = x + w - 7;
		int top = gulir.mulai(l, x, y, x + w - 5, y + h);
		int cy = top;
		cy = kebun(l, x0, cy, x1, now);
		cy += 6;
		int tambangTop = cy;
		cy = tambang(l, x0, cy, x1, now);
		cy += 6;
		cy = catatan(l, x0, cy, x1);
		gulir.selesai(l, cy);
		if (keTambang) {
			keTambang = false;
			gulir.ke(tambangTop - top);
		}
		calon = calon();
		pemilih.gambarSembul(l, "pekerja", Component.translatable(K + ("gali".equals(picking) ? "pilih_penambang" : "pilih_perawat"), pickSlot), calon);
	}

	// ------------------------------------------------------------------ the garden

	private int kebun(Lukis l, int x0, int cy, int x1, long now) {
		cy += l.kepala(Component.translatable(K + "tab_kebun"), x0, cy, x1);
		if (!data.getBooleanOr("garden_on", true)) {
			return cy + Math.round(l.bungkus(Component.translatable(K + "kebun_mati"), x0, cy, x1 - x0, l.gaya.teksSamar));
		}
		int plots = data.getIntOr("plots", 0);
		if (plots <= 0) {
			return cy + Math.round(l.bungkus(Component.translatable(K + "belum_petak"), x0, cy, x1 - x0, l.gaya.teksLemah));
		}
		ListTag beds = data.getListOrEmpty("beds");
		l.teks(Brush.bold(Component.translatable(K + "petak_judul", plots, data.getIntOr("hall", 0))), x0, cy, x1 - x0, Palette.JADE_DARK);
		cy += 11;
		int lebar = x1 - x0 + 4;
		int cols = Math.max(1, Math.min(plots, lebar / 58));
		int cellW = Math.min(76, lebar / cols);
		for (int i = 0; i < plots; i++) {
			CompoundTag bed = i < beds.size() && beds.get(i) instanceof CompoundTag b ? b : new CompoundTag();
			int cx = x0 + (i % cols) * cellW;
			int ry = cy + (i / cols) * SEL_H;
			bedCard(l, i, bed, cx, ry, cx + cellW - 4, ry + SEL_H - 4, now);
		}
		cy += ((plots + cols - 1) / cols) * SEL_H + 2;
		cy = bedPanel(l, beds, x0, cy, x1);
		cy = gardeners(l, x0, cy + 4, x1);
		return harvestAll(l, beds, x0, cy + 4, x1);
	}

	private void bedCard(Lukis l, int index, CompoundTag bed, int ax, int ay, int bx, int by, long now) {
		String herb = bed.getStringOr("herb", "");
		int stage = Math.max(0, Math.min(4, bed.getIntOr("stage", 0)));
		int rgb = bed.getIntOr("color", 0x8CFFB0);
		boolean ripe = stage >= 4;
		boolean hover = l.di(ax, ay, bx, by);
		boolean chosen = selected == index;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 260.0D + index);
		// the card: tilled earth under a paper label
		if (ripe) {
			Brush.rect(l.g, ax - 1.0F, ay - 1.0F, bx + 1.0F, by + 1.0F, Palette.argb((int) (0x40 + 0x60 * pulse), rgb));
		}
		Brush.chamferGradient(l.g, ax, ay, bx, by, 3, 0xFF6B5136, 0xFF4A3622);
		if (chosen || hover) {
			Brush.frame(l.g, ax, ay, bx, by, chosen ? Palette.GOLD : Palette.JADE_LIGHT);
		}
		float midX = (ax + bx) / 2.0F;
		l.teks(Component.literal(String.valueOf(index + 1)), ax + 3, ay + 2, 12, 0xFFE8D8B8);
		if (herb.isEmpty()) {
			l.tengah(Component.literal("+"), midX, ay + 12, bx - ax - 4, 0xFFB8A27E);
			l.tengah(Component.translatable(K + "kosong"), midX, by - 11, bx - ax - 4, 0xFFD8C8A8);
		} else {
			if (stage >= 3) {
				Brush.halo(l.g, midX, ay + 15.0F, 5.0F, 6.0F, rgb & 0xFFFFFF, ripe ? 0.6F + 0.4F * pulse : 0.35F);
				l.g.item(icon(bed.getStringOr("item", "")), Math.round(midX) - 8, ay + 6);
			} else {
				l.tengah(Component.literal(STAGE_GLYPH[stage]), midX, ay + 10, bx - ax - 4, Palette.argb(0xFF, rgb));
			}
			// the growth bar in the herb's colour
			float prog = ripe ? 1.0F : Math.max(0.0F, Math.min(1.0F, bed.getFloatOr("prog", 0.0F)));
			int barY = by - 14;
			Brush.rect(l.g, ax + 4.0F, barY, bx - 4.0F, barY + 2.0F, 0xFF2E2216);
			Brush.rect(l.g, ax + 4.0F, barY, ax + 4.0F + (bx - ax - 8.0F) * prog, barY + 2.0F,
					Palette.argb(0xFF, ripe ? Palette.mix(rgb | 0xFF000000, 0xFFFFFFFF, 0.3F * pulse) : rgb));
			Component state = ripe ? Component.translatable(K + "siap_panen") : Component.translatable("kultivator.sekte.kebun.stage." + stage);
			l.tengah(state, midX, by - 11, bx - ax - 4, ripe ? Palette.GOLD_LIGHT : 0xFFE8D8B8);
			if (bed.getBooleanOr("auto", false)) {
				l.teks(Component.literal("↻"), bx - 9, ay + 2, 8, Palette.JADE_PALE);
			}
			if (bed.getBooleanOr("tended", false)) {
				l.teks(Component.literal("✿"), bx - 9, ay + 12, 8, Palette.JADE_LIGHT);
			}
			if (hover) {
				List<Component> tip = new ArrayList<>();
				tip.add(Brush.bold(herbName(bed.getStringOr("item", ""))));
				tip.add(Component.translatable(K + "tip_tahap", Component.translatable("kultivator.sekte.kebun.stage." + stage), Math.round(prog * 100.0F)));
				if (!ripe) {
					tip.add(Component.translatable(K + "tip_sisa", timeLeft(bed.getLongOr("left", 0L))));
				}
				tip.add(Component.translatable(K + "tip_umur", bed.getIntOr("age", 0)));
				tip.add(Component.translatable(bed.getBooleanOr("tended", false) ? K + "tip_dirawat" : K + "tip_tak_dirawat"));
				l.tip(tip);
			}
		}
		l.zona("petak:" + index, ax, ay, bx, by, () -> {
			if (ripe && selected == index) {
				kirim("panen", index, "");
			}
			selected = index;
		}, true, null, true);
	}

	private int bedPanel(Lukis l, ListTag beds, int left, int top, int right) {
		int y = top;
		int room = right - left;
		if (selected < 0 || selected >= beds.size() || !(beds.get(selected) instanceof CompoundTag bed)) {
			y += Math.round(l.bungkus(Brush.bold(Component.translatable(K + "pilih_petak")), left, y, room, Palette.JADE_DARK)) + 1;
			return y + Math.round(l.bungkus(Component.translatable(K + "pilih_petak_tip"), left, y, room, l.gaya.teksLemah)) + 2;
		}
		l.sumur(left, y, right, y + 2);
		y += 5;
		String herb = bed.getStringOr("herb", "");
		int stage = bed.getIntOr("stage", 0);
		l.teks(Brush.bold(Component.translatable(K + "petak_n", selected + 1)), left, y, room, Palette.JADE_DARK);
		y += 11;
		if (herb.isEmpty()) {
			y += Math.round(l.bungkus(Component.translatable(K + "pilih_benih"), left, y, room, l.gaya.teksLemah)) + 2;
			return seeds(l, left, y, right);
		}
		int rgb = bed.getIntOr("color", 0x8CFFB0);
		l.g.item(icon(bed.getStringOr("item", "")), left, y);
		l.teks(Brush.bold(herbName(bed.getStringOr("item", ""))), left + 20, y, room - 20, Palette.argb(0xFF, Palette.mix(rgb | 0xFF000000, Palette.INK, 0.55F)));
		l.teks(Component.translatable(K + "tip_umur", bed.getIntOr("age", 0)), left + 20, y + 9, room - 20, l.gaya.teksSamar);
		y += 20;
		boolean ripe = stage >= 4;
		y += Math.round(l.bungkus(ripe ? Component.translatable(K + "siap_panen").withStyle(ChatFormatting.BOLD)
				: Component.translatable(K + "tip_sisa", timeLeft(bed.getLongOr("left", 0L))), left, y, room, ripe ? Palette.GOLD_DARK : l.gaya.teks)) + 1;
		boolean tended = bed.getBooleanOr("tended", false);
		y += Math.round(l.bungkus(Component.translatable(tended ? K + "tip_dirawat" : K + "tip_tak_dirawat"), left, y, room,
				tended ? Palette.JADE_DARK : Palette.WARN)) + 3;
		int index = selected;
		if (ripe) {
			TalismanButton.gambar(l, "panen", left, y, room, 14, Component.translatable(K + "panen"), true, null, () -> kirim("panen", index, ""), true, false);
			y += 17;
		}
		boolean auto = bed.getBooleanOr("auto", false);
		int bw = (room - 6) / 2;
		TalismanButton.gambar(l, "ulang", left, y, bw, 14, Component.translatable(auto ? K + "ulang_on" : K + "ulang_off"), true, null,
				() -> kirim("ulang", index, ""), false, auto);
		Component nama = herbName(bed.getStringOr("item", ""));
		TalismanButton.gambar(l, "cabut", left + bw + 6, y, room - bw - 6, 14, Component.translatable(K + "cabut"), true, null,
				() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable(M + "cabut_yakin", nama, index + 1), () -> kirim("cabut", index, "")));
		y += 17;
		return y + Math.round(l.bungkus(Component.translatable(K + "ulang_tip"), left, y, room, l.gaya.teksSamar)) + 2;
	}

	/** "Panen semua" and the garden's numbers. */
	private int harvestAll(Lukis l, ListTag beds, int left, int y, int right) {
		int ripe = 0;
		for (Tag tag : beds) {
			if (tag instanceof CompoundTag b && b.getIntOr("stage", 0) >= 4) {
				ripe++;
			}
		}
		if (ripe > 0) {
			TalismanButton.gambar(l, "panen_semua", left, y, right - left, 14, Component.translatable(K + "panen_semua", ripe), true, null,
					() -> kirim("panen", -1, ""), true, false);
			y += 17;
		}
		CompoundTag stat = data.getCompoundOrEmpty("stat");
		return y + Math.round(l.bungkus(Component.translatable(K + "statistik_kebun", stat.getIntOr("panen", 0), stat.getIntOr("herba", 0)), left, y,
				right - left, l.gaya.teksSamar));
	}

	/** The nine seeds: icon, name, days to ripen, how many in the Lumbung and in the pack; a click sows. */
	private int seeds(Lukis l, int left, int top, int right) {
		int y = top;
		int index = selected;
		for (Tag tag : data.getListOrEmpty("seeds")) {
			if (!(tag instanceof CompoundTag seed)) {
				continue;
			}
			int store = seed.getIntOr("store", 0);
			int hand = seed.getIntOr("hand", 0);
			boolean have = store + hand > 0;
			int rowB = y + 18;
			boolean hover = have && l.di(left, y, right, rowB);
			l.kartu(left, y, right, rowB - 1, hover ? 1.0F : 0.0F, false);
			l.g.item(icon(seed.getStringOr("item", "")), left + 1, y + 1);
			int rgb = seed.getIntOr("color", 0x8CFFB0);
			int ink = have ? Palette.mix(rgb | 0xFF000000, Palette.INK, 0.6F) : l.gaya.teksSamar;
			Component stok = Component.translatable(K + "benih_stok", store, hand);
			Component hari = Component.translatable(K + "benih_hari", 1 + seed.getIntOr("mutu", 0));
			int stokW = font.width(stok);
			int hariW = font.width(hari);
			l.kanan(stok, right - 3, y + 5, have ? Palette.JADE_DARK : l.gaya.teksSamar);
			l.kanan(hari, right - 9 - stokW, y + 5, l.gaya.teksLemah);
			l.teks(herbName(seed.getStringOr("item", "")), left + 20, y + 5, right - left - 34 - stokW - hariW, ink);
			String id = seed.getStringOr("id", "");
			l.zona("benih:" + id, left, y, right, rowB - 1, () -> {
				kirim("tanam", index, id);
			}, have, have ? null : Component.translatable(M + "benih_habis"), true);
			y += 19;
		}
		return y;
	}

	/** The gardener slots: one per two beds. */
	private int gardeners(Lukis l, int left, int top, int right) {
		int slots = data.getIntOr("tslots", 0);
		if (slots <= 0) {
			return top;
		}
		l.teks(Brush.bold(Component.translatable(K + "perawat", data.getIntOr("rest", 5))), left, top, right - left, Palette.JADE_DARK);
		Map<Integer, CompoundTag> bySlot = workers(data.getListOrEmpty("tenders"));
		int y = top + 11;
		int cols = Math.max(1, Math.min(3, Math.min(slots, (right - left) / 130)));
		int w = (right - left + 4) / cols;
		for (int s = 1; s <= slots; s++) {
			int ax = left + ((s - 1) % cols) * w;
			int ay = y + ((s - 1) / cols) * (CHIP_H + 3);
			workerChip(l, "rawat", s, bySlot.get(s), Component.translatable(K + "slot_petak", 2 * s - 1, Math.min(2 * s, data.getIntOr("plots", 0))),
					ax, ay, ax + w - 4, ay + CHIP_H);
		}
		return y + ((slots + cols - 1) / cols) * (CHIP_H + 3);
	}

	/** One worker slot: the name, streak and two seals (release, a day off), or "+ pilih" when empty. */
	private void workerChip(Lukis l, String kind, int slot, @Nullable CompoundTag worker, Component label, int ax, int ay, int bx, int by) {
		boolean hover = l.di(ax, ay, bx, by);
		if (worker == null) {
			l.kartu(ax, ay, bx, by, hover ? 1.0F : 0.0F, false);
			l.teks(label, ax + 4, ay + 3, bx - ax - 8, l.gaya.teksSamar);
			l.teks(Component.translatable(K + "pilih_murid"), ax + 4, ay + 14, bx - ax - 8, Palette.JADE_DARK);
			l.zona("slot:" + kind + slot, ax, ay, bx, by, () -> {
				picking = kind;
				pickSlot = slot;
				pemilih.terpilih().clear();
				pemilih.bukaSembul();
			}, true, null, true);
			return;
		}
		l.kartu(ax, ay, bx, by, 0.0F, false);
		Component name = worker.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		int streak = worker.getIntOr("streak", 0);
		int rest = data.getIntOr("rest", 5);
		boolean tired = streak >= rest;
		boolean here = worker.getBooleanOr("here", true);
		int seals = 2 * 18;
		l.teks(Brush.bold(name), ax + 4, ay + 3, bx - ax - 8 - seals, here ? l.gaya.teks : l.gaya.teksSamar);
		Component sub = "gali".equals(kind)
				? Component.translatable(K + "penambang_baris", streak, worker.getIntOr("today", 0), worker.getIntOr("total", 0))
				: Component.translatable(K + "perawat_baris", streak);
		l.teks(sub, ax + 4, ay + 14, bx - ax - 8 - seals, tired ? Palette.DANGER : l.gaya.teksLemah);
		// two seals: 離 release, 休 a day off (16×12, R9)
		int sx = bx - seals;
		if (TalismanButton.gambar(l, kind + "_lepas" + slot, sx, ay + 7, 16, 12, Component.literal("離"), true, null,
				() -> kirim(kind + "_lepas", slot, ""))) {
			l.tip(Component.translatable(K + "lepas"));
		}
		if (TalismanButton.gambar(l, kind + "_libur" + slot, sx + 18, ay + 7, 16, 12, Component.literal("休"), true, null,
				() -> kirim(kind + "_libur", slot, ""), tired, false)) {
			l.tip(Component.translatable(K + "libur", rest));
		}
	}

	// ------------------------------------------------------------------ the mine

	private int tambang(Lukis l, int x0, int cy, int x1, long now) {
		cy += l.kepala(Component.translatable(K + "tab_tambang"), x0, cy, x1);
		CompoundTag mine = data.getCompoundOrEmpty("mine");
		if (!data.getBooleanOr("mine_on", true)) {
			return cy + Math.round(l.bungkus(Component.translatable(K + "tambang_mati"), x0, cy, x1 - x0, l.gaya.teksSamar));
		}
		if (!mine.getBooleanOr("built", false)) {
			return cy + Math.round(l.bungkus(Component.translatable(K + "belum_tambang"), x0, cy, x1 - x0, l.gaya.teksLemah));
		}
		int level = Math.max(1, mine.getIntOr("level", 1));
		int shaftW = 82;
		int shaftBottom = shaft(l, x0, cy, x0 + shaftW, level, mine.getIntOr("danger", 0), now);
		int mid = x0 + shaftW + 8;
		// the shifts
		int slots = mine.getIntOr("slots", 2);
		l.teks(Brush.bold(Component.translatable(K + "giliran", slots, data.getIntOr("rest", 5))), mid, cy, x1 - mid, Palette.JADE_DARK);
		Map<Integer, CompoundTag> bySlot = workers(mine.getListOrEmpty("miners"));
		int y = cy + 11;
		int cols = x1 - mid >= 250 ? 2 : 1;
		int w = (x1 - mid + 4) / cols;
		for (int s = 1; s <= slots; s++) {
			int ax = mid + ((s - 1) % cols) * w;
			int ay = y + ((s - 1) / cols) * (CHIP_H + 3);
			workerChip(l, "gali", s, bySlot.get(s), Component.translatable(s == 1 ? K + "slot_mulut" : K + "slot_dalam", s), ax, ay, ax + w - 4,
					ay + CHIP_H);
		}
		y += ((slots + cols - 1) / cols) * (CHIP_H + 3) + 2;
		CompoundTag stat = data.getCompoundOrEmpty("stat");
		y += Math.round(l.bungkus(Component.translatable(K + "statistik_tambang", stat.getIntOr("gali", 0), stat.getIntOr("inti", 0)), mid, y, x1 - mid,
				l.gaya.teksLemah)) + 1;
		y += Math.round(l.bungkus(Component.translatable(K + "giliran_tip"), mid, y, x1 - mid, l.gaya.teksSamar)) + 2;
		return table(l, mine, level, x0, Math.max(y, shaftBottom) + 4, x1);
	}

	/** The shaft: nine strata from the moss at the mouth down to the five-coloured deep, the depth reached lit. Returns its bottom. */
	private int shaft(Lukis l, int ax, int ay, int bx, int level, int danger, long now) {
		int[] strata = {0xFF7C6A4A, 0xFF6F604A, 0xFF625A50, 0xFF55524F, 0xFF4A4A52, 0xFF403F4E, 0xFF3A3450, 0xFF35304E, 0xFF2E2846};
		l.teks(Brush.bold(Component.translatable(K + "kedalaman", level)), ax, ay, bx - ax, Palette.JADE_DARK);
		int top = ay + 15;
		int h = 11;
		// the hill and the mouth on top
		Brush.rect(l.g, ax, top - 3.0F, bx, top, 0xFF4F7A3A);
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
		for (int i = 0; i < 9; i++) {
			int sy = top + i * h;
			Brush.rect(l.g, ax, sy, bx, sy + h, strata[i]);
			// a vein or two in each stratum, brighter the deeper
			int vein = i >= 6 ? 0xFFE8C060 : i >= 3 ? 0xFFC9A04A : 0xFFB08850;
			Brush.rect(l.g, ax + 6.0F + (i * 13) % 40, sy + h * 0.4F, ax + 14.0F + (i * 13) % 40, sy + h * 0.4F + 1.0F, Palette.argb(0xB0, vein));
			if (i >= 6) {
				int[] five = {0xFF5AAAFF, 0xFFE8403A, 0xFFFFCE48, 0xFFF4F4EC, 0xFF4FB08E};
				Brush.dot(l.g, ax + 50.0F + (i * 7) % 20, sy + h * 0.6F, 1.2F, five[i % 5]);
			}
			int n = i + 1;
			l.kanan(Component.literal(String.valueOf(n)), bx - 2, sy + 1, n <= level ? 0xFFF4E8C8 : 0x90F4E8C8);
			if (n >= 5) {
				Brush.rect(l.g, ax, sy, ax + 2.0F, sy + h, Palette.argb(0x90, Palette.DANGER));
			}
		}
		// the shaft cut down to the depth reached, with the lamp at its foot
		float shaftX = ax + (bx - ax) * 0.42F;
		float foot = top + level * h;
		Brush.rect(l.g, shaftX - 3.0F, top - 3.0F, shaftX + 3.0F, foot, 0xFF1A1410);
		Brush.rect(l.g, shaftX - 4.0F, top - 3.0F, shaftX - 3.0F, foot, 0xFF8A6A3A);
		Brush.rect(l.g, shaftX + 3.0F, top - 3.0F, shaftX + 4.0F, foot, 0xFF8A6A3A);
		Brush.halo(l.g, shaftX, foot - 3.0F, 2.0F, 6.0F, 0xFFC447, 0.5F + 0.4F * pulse);
		Brush.dot(l.g, shaftX, foot - 3.0F, 1.6F, 0xFFFFE6A8);
		int bottom = top + 9 * h + 2;
		Component risk = danger > 0 ? Component.translatable(K + "bahaya", danger) : Component.translatable(K + "aman");
		bottom += Math.round(l.bungkus(risk, ax, bottom, bx - ax, danger > 0 ? Palette.DANGER : Palette.JADE_DARK));
		return bottom;
	}

	/** The yield table of the level and the cost of the next. */
	private int table(Lukis l, CompoundTag mine, int level, int left, int top, int right) {
		int y = top;
		int room = right - left;
		y += Math.round(l.bungkus(Brush.bold(Component.translatable(K + "tabel", level)), left, y, room, Palette.JADE_DARK)) + 1;
		for (Tag tag : mine.getListOrEmpty("table")) {
			if (!(tag instanceof CompoundTag row)) {
				continue;
			}
			ItemStack icon = icon(row.getStringOr("item", ""));
			l.g.item(icon, left, y);
			Component name = icon.isEmpty() ? Component.literal("?") : icon.getHoverName();
			int pct = row.getIntOr("pct", 0);
			Component persen = Component.literal(pct + "%");
			l.kanan(persen, right - 2, y + 4, pct <= 3 ? Palette.GOLD_DARK : l.gaya.teksLemah);
			l.teks(Component.translatable(K + "tabel_baris", row.getIntOr("count", 1), name), left + 20, y + 4, room - 26 - font.width(persen), l.gaya.teks);
			y += 18;
		}
		y += 3;
		if (!mine.contains("cost")) {
			return y + Math.round(l.bungkus(Component.translatable(K + "terdalam").withStyle(ChatFormatting.BOLD), left, y, room, Palette.GOLD_DARK));
		}
		CompoundTag cost = mine.getCompoundOrEmpty("cost");
		y += Math.round(l.bungkus(Brush.bold(Component.translatable(K + "perdalam_judul", level + 1)), left, y, room, Palette.JADE_DARK)) + 1;
		boolean okPk = cost.getBooleanOr("ok_pk", false);
		boolean okWood = cost.getLongOr("have_kayu", 0L) >= cost.getIntOr("kayu", 0);
		boolean okStone = cost.getLongOr("have_batu", 0L) >= cost.getIntOr("batu", 0);
		y += Math.round(l.bungkus(Component.translatable(K + "biaya_pk", cost.getIntOr("pk", 0)), left + 4, y, room - 4,
				okPk ? Palette.JADE_DARK : Palette.DANGER)) + 1;
		y += Math.round(l.bungkus(Component.translatable(K + "biaya_kayu", cost.getIntOr("kayu", 0), cost.getLongOr("have_kayu", 0L)), left + 4, y,
				room - 4, okWood ? Palette.JADE_DARK : Palette.DANGER)) + 1;
		y += Math.round(l.bungkus(Component.translatable(K + "biaya_batu", cost.getIntOr("batu", 0), cost.getLongOr("have_batu", 0L)), left + 4, y,
				room - 4, okStone ? Palette.JADE_DARK : Palette.DANGER)) + 3;
		boolean ok = okPk && okWood && okStone;
		int tujuan = level + 1;
		TalismanButton.gambar(l, "perdalam", left, y, Math.min(room, 180), 14, Component.translatable(K + "perdalam"), ok,
				ok ? null : Component.translatable(M + "biaya_kurang"),
				() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable(M + "perdalam_yakin", tujuan), () -> kirim("dalam", 0, "")));
		return y + 17;
	}

	// ------------------------------------------------------------------ the log

	private int catatan(Lukis l, int x0, int cy, int x1) {
		ListTag log = data.getListOrEmpty("log");
		if (!log.isEmpty()) {
			cy += l.kepala(Component.translatable(M + "catatan"), x0, cy, x1);
			for (int i = 0; i < Math.min(8, log.size()); i++) {
				if (!(log.get(i) instanceof CompoundTag entry)) {
					continue;
				}
				Component text = entry.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
				Component line = Component.translatable(K + "log_baris", entry.getLongOr("day", 0L), text);
				cy += Math.round(l.bungkus(line, x0, cy, x1 - x0, i == 0 ? l.gaya.teksLemah : l.gaya.teksSamar)) + 1;
			}
			cy += 3;
		}
		Component source = Component.translatable(halamanTerakhir == Kebun.PAGE_MINE ? "kultivator.sekte.kebun.sumber.tambang"
				: "kultivator.sekte.kebun.sumber.kebun").withStyle(ChatFormatting.ITALIC);
		return cy + Math.round(l.bungkus(source, x0, cy, x1 - x0, l.gaya.teksSamar)) + 2;
	}

	// ================================================================== the worker picker

	/** The disciples near the stele as people of the picker (blocked ones greyed with the reason). */
	private List<PemilihOrang.Orang> calon() {
		boolean mine = "gali".equals(picking);
		int rest = data.getIntOr("rest", 5);
		List<PemilihOrang.Orang> out = new ArrayList<>();
		for (Tag tag : data.getListOrEmpty("cands")) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			Component name = c.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			boolean root = mine ? c.getBooleanOr("stone", false) : c.getBooleanOr("green", false);
			boolean defiant = c.getBooleanOr("defiant", false);
			boolean resting = c.getBooleanOr("resting", false);
			boolean busy = c.getBooleanOr("busy", false);
			String job = c.getStringOr("job", "");
			Component kunci = defiant ? Component.translatable(K + "c_membangkang") : resting ? Component.translatable(K + "c_libur")
					: busy ? Component.translatable(K + "c_terobosan") : null;
			Component note = !job.isEmpty() ? Component.translatable(K + "c_kerja." + ("tambang".equals(job) || "kebun".equals(job) || "tugas".equals(job)
					? job : "lain")) : Component.translatable(K + "c_luang");
			int streak = c.getIntOr("streak", 0);
			if (streak > 0 && kunci == null) {
				note = Component.translatable(K + "c_beruntun", note, streak);
				if (streak >= rest) {
					note = note.copy().withColor(Palette.WARN & 0xFFFFFF);
				}
			}
			// the root column: ×1.1 for Kayu/Air in the garden, Tanah/Logam in the mine
			List<Component> kolom = List.of(root ? Component.literal(mine ? "⛏ ×1.1" : "✿ ×1.1").withStyle(ChatFormatting.BOLD) : Component.empty());
			out.add(new PemilihOrang.Orang(c.getStringOr("soul", ""), root ? Brush.bold(name) : name, root ? Palette.JADE : Palette.LOCKED, -1, kolom,
					kunci != null ? kunci : note, kunci, null));
		}
		return out;
	}

	/** A tick in the picker assigns that disciple at once and closes it. */
	private void dipilih() {
		List<PemilihOrang.Orang> pilih = pemilih.pilihan(calon);
		if (pilih.isEmpty()) {
			return;
		}
		kirim(picking, pickSlot, pilih.get(0).id());
		pemilih.terpilih().clear();
		pemilih.tutup();
	}

	// ================================================================== helpers

	private static Map<Integer, CompoundTag> workers(ListTag list) {
		Map<Integer, CompoundTag> out = new HashMap<>();
		for (Tag tag : list) {
			if (tag instanceof CompoundTag w) {
				out.put(w.getIntOr("slot", 0), w);
			}
		}
		return out;
	}

	/** The item of a registry id for an icon (made once per id while the page lives). */
	private ItemStack icon(String id) {
		return icons.computeIfAbsent(id, key -> {
			Identifier identifier = key.isEmpty() ? null : Identifier.tryParse(key);
			Item item = identifier == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(identifier);
			return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		});
	}

	private Component herbName(String itemId) {
		ItemStack stack = icon(itemId);
		return stack.isEmpty() ? Component.literal("?") : Component.translatable(stack.getItem().getDescriptionId());
	}

	/** Ticks of growth left as world days, or hours under a day. */
	private static Component timeLeft(long ticks) {
		if (ticks >= 24000L) {
			return Component.translatable(K + "hari", String.format(java.util.Locale.ROOT, "%.1f", ticks / 24000.0D));
		}
		return Component.translatable(K + "jam", Math.max(1L, (ticks + 999L) / 1000L));
	}

	// ================================================================== input

	@Override
	protected boolean clickedElsewhere(double mouseX, double mouseY) {
		if (!pemilih.terbuka()) {
			selected = -1;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		return !pemilih.terbuka() && gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		return !pemilih.terbuka() && gulir.tombol(event);
	}
}
