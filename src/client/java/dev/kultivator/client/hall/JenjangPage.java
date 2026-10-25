package dev.kultivator.client.hall;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sekte › Anggota › Pangkat, source Aulaku (DESIGN 20.3.6, 43.5): a six-storey pagoda, Murid Tercatat at the bottom and the
 * Tetua Agung at the top, with the rank name and a seal (face) for every disciple on its floor. A seal (click, or Tab +
 * Enter) opens its slip: realm, rank, Poin Kontribusi, technique slots, the checklist for the next rank and the talismans
 * "Naikkan", "Turunkan" (confirmed with the seal, 43.7), Pelindung Sekte and Anugerah Ketua (pool PK to the disciple).
 * From 320 px the slip stands right of the pagoda; narrower, the slip replaces the pagoda (back control / Esc returns).
 * Sect mates visiting the hall see the pagoda read-only. Text 1.0 (43.0.5).
 */
public class JenjangPage extends Page {

	private static final int FLOORS = 6;
	private static final int SEAL = 12;
	/** Seal pitch on a floor: each seal's click box is 16 wide (43.0.6). */
	private static final int STEP = SEAL + 4;
	private static final String K = "kultivator.gui43.m06.";

	private String selected = "";
	/** Narrow layout: the slip is shown instead of the pagoda. */
	private boolean slipOpen;

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.jenjang");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.jenjang.short");
	}

	@Override
	public int glyph() {
		return Glyphs.TECHNIQUES;
	}

	@Override
	public String helpTopic() {
		return "jenjang";
	}

	private static CompoundTag info() {
		return HallClient.info();
	}

	private static boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	private static ListTag disciples() {
		return info().getListOrEmpty("disciples");
	}

	private static @Nullable CompoundTag entry(String soul) {
		for (Tag tag : disciples()) {
			if (tag instanceof CompoundTag entry && entry.getStringOr("soul", "").equals(soul)) {
				return entry;
			}
		}
		return null;
	}

	private @Nullable CompoundTag selectedEntry() {
		return selected.isEmpty() ? null : entry(selected);
	}

	private static boolean present(@Nullable CompoundTag entry) {
		return entry != null && !entry.getBooleanOr("away", false) && !entry.getBooleanOr("unloaded", false);
	}

	private boolean narrow() {
		return tata() == Tata.SEMPIT;
	}

	@Override
	public boolean rinci() {
		return narrow() && slipOpen && !readonly();
	}

	@Override
	public boolean mundur() {
		if (rinci()) {
			slipOpen = false;
			UiSound.close();
			return true;
		}
		return false;
	}

	@Override
	protected void build() {
		// immediate page: everything is drawn in lukis()
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	/** Keeps a valid selection: the chosen disciple, else the first present one, else the first. */
	private void pick() {
		if (selectedEntry() != null) {
			return;
		}
		selected = "";
		for (Tag tag : disciples()) {
			if (tag instanceof CompoundTag entry && (selected.isEmpty() || present(entry) && !present(entry(selected)))) {
				selected = entry.getStringOr("soul", "");
			}
		}
	}

	@Override
	protected void lukis(Lukis l) {
		if (HallClient.version() == 0L) {
			l.tengah(Component.translatable("kultivator.gui43.konteks.memuat"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8, l.gaya.teksSamar);
			return;
		}
		boolean ro = readonly();
		pick();
		if (narrow()) {
			if (rinci()) {
				slip(l, x, y, x + w, y + h);
			} else {
				int top = y;
				if (ro) {
					top += Math.round(l.bungkus(Component.translatable("kultivator.hall.ui.ranks_readonly"), x + 2, y, w - 4, l.gaya.teksLemah, 20)) + 3;
				}
				pagoda(l, x, top, x + w, y + h, ro);
			}
			return;
		}
		int pagodaW = Math.max(150, Math.min(220, Math.round(w * 0.5F)));
		pagoda(l, x, y, x + pagodaW, y + h, ro);
		int sx = x + pagodaW + 6;
		if (ro) {
			l.sumur(sx, y, x + w, y + h);
			l.bungkus(Component.translatable("kultivator.hall.ui.ranks_readonly"), sx + 5, y + 6, x + w - sx - 10, l.gaya.teksLemah, h - 12);
			return;
		}
		slip(l, sx, y, x + w, y + h);
	}

	// ================================================================== the pagoda

	/** Six floors narrowing upwards: roof, rank name (with seats), the seals, and the overflow count. */
	private void pagoda(Lukis l, int x0, int y0, int x1, int y1, boolean ro) {
		int floorH = Math.max(SEAL + 6, Math.min(30, (y1 - y0 - 10) / FLOORS));
		int[] counts = new int[FLOORS];
		for (Tag tag : disciples()) {
			if (tag instanceof CompoundTag entry) {
				counts[floor(entry)]++;
			}
		}
		// the spire above the top floor
		int topY = y1 - FLOORS * floorH;
		float cx = (x0 + x1) / 2.0F;
		Brush.rect(l.g, cx - 0.8F, topY - 7.0F, cx + 0.8F, topY, Palette.GOLD_DARK);
		Brush.disc(l.g, cx, topY - 7.5F, 1.8F, Palette.GOLD);
		int[] placed = new int[FLOORS];
		for (int floor = 0; floor < FLOORS; floor++) {
			int top = y1 - (floor + 1) * floorH;
			int fx0 = x0 + 6 + floor * 3;
			int fx1 = x1 - 6 - floor * 3;
			int trim = HallUi.TRIM[floor];
			// body: parchment tinted with the floor's trim colour, gold frame, two pillars; roof with upturned eaves
			l.g.fill(fx0, top + 3, fx1, top + floorH, Palette.mix(Palette.PAPER_TOP, Palette.opaque(trim), 0.18F));
			Brush.frame(l.g, fx0, top + 3, fx1, top + floorH, Palette.argb(0xC0, Palette.GOLD_DARK));
			l.g.fill(fx0 + 1, top + 4, fx0 + 2, top + floorH - 1, Palette.argb(0x80, Palette.ROD_LIGHT));
			l.g.fill(fx1 - 2, top + 4, fx1 - 1, top + floorH - 1, Palette.argb(0x80, Palette.ROD_LIGHT));
			Brush.rect(l.g, fx0 - 4.0F, top + 1.0F, fx1 + 4.0F, top + 3.5F, Palette.JADE_DARK);
			Brush.rect(l.g, fx0 - 2.0F, top, fx1 + 2.0F, top + 1.2F, Palette.JADE);
			Brush.rect(l.g, fx0 - 5.5F, top - 0.5F, fx0 - 3.5F, top + 2.0F, Palette.JADE_DARK);
			Brush.rect(l.g, fx1 + 3.5F, top - 0.5F, fx1 + 5.5F, top + 2.0F, Palette.JADE_DARK);
			// the rank name on the left, the seals on the right
			MutableComponent label = Component.translatable("kultivator.hall.rank." + HallUi.RANK_ID[floor]);
			if (floor == 4) {
				label = Component.translatable("kultivator.hall.ui.seats", label, counts[floor], info().getIntOr("elders_max", 1));
			} else if (floor == 5) {
				label = Component.translatable("kultivator.hall.ui.seats", label, counts[floor], 1);
			}
			int labelW = Math.min(l.font.width(Brush.bold(label)) + 2, (fx1 - fx0) * 2 / 5);
			int ty = top + 3 + (floorH - 3 - 9) / 2 + 1;
			l.teks(Brush.bold(label), fx0 + 4, ty, labelW, Palette.onPaper(trim));
			int sx0 = fx0 + 6 + labelW;
			int room = Math.max(0, (fx1 - 4 - sx0) / STEP);
			if (counts[floor] == 0) {
				l.samar(Component.translatable("kultivator.hall.ui.floor_empty"), sx0, ty, fx1 - sx0 - 4);
			} else if (counts[floor] > room) {
				Component more = Component.literal("+" + (counts[floor] - Math.max(0, room - 1)));
				l.kanan(more, fx1 - 4, ty, l.gaya.teksLemah);
				room = Math.max(0, room - 1);
			}
			int sy = top + 3 + (floorH - 3 - SEAL) / 2;
			if (ro) {
				continue;
			}
			for (Tag tag : disciples()) {
				if (!(tag instanceof CompoundTag entry) || floor(entry) != floor) {
					continue;
				}
				int index = placed[floor]++;
				if (index >= room) {
					continue;
				}
				seal(l, entry, sx0 + 2 + index * STEP, sy);
			}
		}
		if (!ro && disciples().isEmpty()) {
			l.samar(Component.translatable("kultivator.hall.ui.ranks_none"), x0 + 6, y0 + 1, x1 - x0 - 12);
		}
	}

	private static int floor(CompoundTag entry) {
		return Math.max(0, Math.min(FLOORS - 1, entry.getIntOr("rank", 1)));
	}

	/** A disciple's face framed in its rank's trim colour; the chosen one wears a gold ring, absent ones are greyed. */
	private void seal(Lukis l, CompoundTag e, int sx, int sy) {
		String soul = e.getStringOr("soul", "");
		boolean chosen = soul.equals(selected);
		if (chosen) {
			l.g.fill(sx - 2, sy - 2, sx + SEAL + 2, sy + SEAL + 2, Palette.GOLD_LIGHT);
		}
		l.g.fill(sx - 1, sy - 1, sx + SEAL + 1, sy + SEAL + 1, Palette.opaque(HallUi.TRIM[floor(e)]));
		HallUi.face(l.g, e.getIntOr("skin", 0), e.getBooleanOr("slim", false), sx, sy, SEAL);
		if (!present(e)) {
			l.g.fill(sx, sy, sx + SEAL, sy + SEAL, 0x90707A75);
		}
		if (e.getBooleanOr("protector", false)) {
			l.g.fill(sx + SEAL - 3, sy, sx + SEAL, sy + 3, Palette.SEAL);
		}
		String kunci = "seal:" + soul;
		l.zona(kunci, sx - 2, sy - 1, sx + SEAL + 2, sy + SEAL + 1, () -> {
			selected = soul;
			slipOpen = true;
			UiSound.press();
		}, true, null, true);
		l.fokusCincin(kunci, sx - 2, sy - 1, sx + SEAL + 2, sy + SEAL + 1);
		if (l.di(sx - 2, sy - 1, sx + SEAL + 2, sy + SEAL + 1)) {
			l.g.fill(sx, sy, sx + SEAL, sy + SEAL, 0x30FFFFFF);
			l.tip(sealTip(e));
		}
	}

	private static List<Component> sealTip(CompoundTag e) {
		List<Component> lines = new ArrayList<>();
		lines.add(HallUi.name(e, "name"));
		if (!e.getBooleanOr("away", false)) {
			lines.add(HallUi.realmLine(e));
		}
		lines.add(HallUi.rankName(e.getIntOr("rank", 1)));
		if (present(e)) {
			lines.add(Component.translatable("kultivator.hall.ui.pk", e.getIntOr("pk", 0)).withStyle(ChatFormatting.AQUA));
		} else {
			lines.add(Component.translatable(e.getBooleanOr("away", false) ? "kultivator.hall.ui.seal_away" : "kultivator.hall.ui.seal_unloaded")
					.withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	// ================================================================== the slip

	/** The selected disciple's slip: who it is, its numbers, the checklist for the next floor, then the talismans. */
	private void slip(Lukis l, int x0, int y0, int x1, int y1) {
		CompoundTag e = selectedEntry();
		if (e == null) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.hall.ui.ranks_none"), null, null, x0, y0, x1, y1);
			return;
		}
		int bh = narrow() ? 12 : 14;
		int buttonsY = y1 - bh * 3 - 6;
		l.sumur(x0, y0, x1, buttonsY - 3);
		int tx = x0 + 4;
		int tw = x1 - x0 - 8;
		int yMax = buttonsY - 4;
		int y = y0 + 3;
		y += l.kepala(HallUi.name(e, "name"), tx, y, x1 - 4);
		int rank = e.getIntOr("rank", 0);
		if (e.getBooleanOr("away", false)) {
			y = baris(l, HallUi.rankName(rank), tx, y, tw, yMax, l.gaya.teks);
			Component reason = Component.translatableWithFallback("kultivator.hall.away." + e.getStringOr("reason", "other"), e.getStringOr("reason", "?"));
			l.bungkus(Component.translatable("kultivator.hall.ui.ranks_away", reason), tx, y + 2, tw, l.gaya.teksLemah, yMax - y - 2);
		} else {
			y = baris(l, HallUi.realmLine(e), tx, y, tw, yMax, l.gaya.teks);
			if (e.getBooleanOr("unloaded", false)) {
				y = baris(l, HallUi.rankName(rank), tx, y, tw, yMax, l.gaya.teks);
				l.bungkus(Component.translatable("kultivator.hall.ui.ranks_unloaded"), tx, y + 2, tw, l.gaya.teksLemah, yMax - y - 2);
			} else {
				details(l, e, rank, tx, y, tw, yMax);
			}
		}
		buttons(l, e, x0, buttonsY, x1, bh);
	}

	private int baris(Lukis l, Component text, int x0, int y, int w, int yMax, int warna) {
		if (y + 9 > yMax) {
			return y;
		}
		l.teks(text, x0, y, w, warna);
		return y + 10;
	}

	private void details(Lukis l, CompoundTag e, int rank, int tx, int y, int tw, int yMax) {
		y = baris(l, Component.translatable("kultivator.hall.ui.rank_pk", HallUi.rankName(rank), e.getIntOr("pk", 0)), tx, y, tw, yMax, l.gaya.teks);
		int slots = e.getIntOr("slots", -1);
		y = baris(l, slots < 0 ? Component.translatable("kultivator.hall.ui.slots_all", e.getIntOr("learned", 0))
				: Component.translatable("kultivator.hall.ui.slots", e.getIntOr("learned", 0), slots), tx, y, tw, yMax, l.gaya.teksLemah);
		List<Component> marks = new ArrayList<>();
		if (e.getIntOr("stars", 0) > 0) {
			marks.add(Component.translatable("kultivator.spine6.stars", e.getIntOr("stars", 0)).withColor(Palette.GOLD_DARK));
		}
		if (e.getBooleanOr("protector", false)) {
			marks.add(Component.translatable("kultivator.hall.ui.mark.protector").withColor(Palette.SEAL_DARK));
		}
		if (e.getBooleanOr("heir", false)) {
			marks.add(Component.translatable("kultivator.hall.ui.mark.heir").withColor(Palette.JADE_DARK));
		}
		if (!marks.isEmpty()) {
			MutableComponent line = Component.empty();
			for (int i = 0; i < marks.size(); i++) {
				if (i > 0) {
					line.append(Component.literal(" · "));
				}
				line.append(marks.get(i));
			}
			y = baris(l, line, tx, y, tw, yMax, l.gaya.teks);
		}
		y += 2;
		if (rank >= FLOORS - 1) {
			l.bungkus(Component.translatable("kultivator.hall.ui.rank_top"), tx, y, tw, Palette.GOLD_DARK, yMax - y);
			return;
		}
		y = baris(l, Component.translatable("kultivator.hall.ui.next_rank", HallUi.rankName(rank + 1)), tx, y, tw, yMax, Palette.SEAL_DARK);
		ListTag reqs = e.getListOrEmpty("reqs");
		for (int i = 0; i < reqs.size(); i++) {
			if (!(reqs.get(i) instanceof CompoundTag req)) {
				continue;
			}
			if (y + 19 > yMax && i < reqs.size() - 1) {
				// no room for all: one line "+N lagi" with every requirement in its tooltip
				Component more = Component.translatable(K + "jenjang.lagi", reqs.size() - i);
				l.samar(more, tx, y, tw);
				if (l.di(tx, y, tx + tw, y + 9)) {
					l.tip(reqLines(reqs));
				}
				return;
			}
			if (y + 9 > yMax) {
				return;
			}
			boolean ok = req.getBooleanOr("ok", false);
			HallUi.mark(l.g, tx, y + 2.0F, ok);
			l.teks(HallUi.name(req, "text"), tx + 8, y, tw - 8, ok ? l.gaya.teks : l.gaya.teksLemah);
			y += 10;
		}
	}

	private static List<Component> reqLines(ListTag reqs) {
		List<Component> lines = new ArrayList<>();
		for (Tag tag : reqs) {
			if (tag instanceof CompoundTag req) {
				boolean ok = req.getBooleanOr("ok", false);
				lines.add(Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED).append(HallUi.name(req, "text")));
			}
		}
		return lines;
	}

	/** Naikkan · Turunkan / Pelindung Sekte / Anugerah Ketua +10 +50 +100 +500. */
	private void buttons(Lukis l, CompoundTag e, int x0, int by, int x1, int bh) {
		int w = x1 - x0;
		int half = (w - 2) / 2;
		int rank = e.getIntOr("rank", 0);
		boolean here = present(e);
		String soul = selected;
		Component name = HallUi.name(e, "name");
		int cost = e.getIntOr("cost", 0);
		Component promote = cost > 0 ? Component.translatable("kultivator.hall.ui.promote_cost", cost) : Component.translatable("kultivator.hall.ui.promote");
		Component why = here ? null : Component.translatable(e.getBooleanOr("away", false) ? "kultivator.hall.ui.seal_away" : "kultivator.hall.ui.seal_unloaded");
		if (TalismanButton.gambar(l, "naik", x0, by, half, bh, promote, here && rank < FLOORS - 1, why != null ? why
				: Component.translatable("kultivator.hall.ui.rank_top"), () -> HallClient.send(Halls.OP_PROMOTE, soul), ready(e), false)) {
			l.tip(promoteTip(e));
		}
		if (TalismanButton.gambar(l, "turun", x0 + half + 2, by, w - half - 2, bh, Component.translatable("kultivator.hall.ui.demote"), here && rank > 0,
				why, () -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "jenjang.turun", name, HallUi.rankName(Math.max(0,
						rank - 1))), () -> HallClient.send(Halls.OP_DEMOTE, soul)))) {
			l.tip(List.of(Component.translatable("kultivator.hall.ui.demote"), Component.translatable("kultivator.hall.ui.demote.tip")));
		}
		int py = by + bh + 2;
		boolean protector = e.getBooleanOr("protector", false);
		boolean canProtect = here && (protector || e.getBooleanOr("can_protect", false));
		Component protectWhy = why != null ? why : HallUi.name(e, "protect_why");
		if (TalismanButton.gambar(l, "pelindung", x0, py, w, bh, Component.translatable(protector ? "kultivator.hall.ui.protector_off"
				: "kultivator.hall.ui.protector_on"), canProtect, protectWhy, () -> HallClient.send(Halls.OP_PROTECTOR, soul))) {
			l.tip(protectorTip(e));
		}
		int gy = py + bh + 2;
		int[] grants = info().getIntArray("grants").orElse(new int[]{10, 50, 100, 500});
		int count = Math.max(1, grants.length);
		int gw = (w - (count - 1) * 2) / count;
		int pool = info().getIntOr("pool", 0);
		for (int i = 0; i < grants.length; i++) {
			int amount = grants[i];
			if (TalismanButton.gambar(l, "anugerah" + i, x0 + i * (gw + 2), gy, gw, bh, Component.literal("+" + amount), here && pool >= amount,
					why != null ? why : Component.translatable(K + "jenjang.kolam", pool), () -> HallClient.send(Halls.OP_GRANT, soul + ":" + amount))) {
				l.tip(List.of(Component.translatable("kultivator.hall.ui.grant", amount), Component.translatable("kultivator.hall.ui.grant.tip", pool),
						Component.translatable("kultivator.hall.ui.grant.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
		}
	}

	/** Every requirement holds except the Latihan Khusus, which the promotion itself starts. */
	private static boolean ready(@Nullable CompoundTag entry) {
		if (!present(entry) || entry.getIntOr("rank", 0) >= FLOORS - 1) {
			return false;
		}
		for (Tag tag : entry.getListOrEmpty("reqs")) {
			if (tag instanceof CompoundTag line && !line.getBooleanOr("ok", false)) {
				return false;
			}
		}
		return true;
	}

	private static List<Component> promoteTip(CompoundTag e) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.promote"));
		int rank = e.getIntOr("rank", 0);
		if (rank < FLOORS - 1) {
			lines.add(Component.translatable("kultivator.hall.ui.next_rank", HallUi.rankName(rank + 1)));
		}
		lines.add(Component.translatable("kultivator.hall.ui.promote.tip").withStyle(ChatFormatting.GRAY));
		if (rank == 1) {
			lines.add(Component.translatable("kultivator.hall.ui.promote.trial").withStyle(ChatFormatting.DARK_AQUA));
			lines.add(Component.translatable("kultivator.hall.ui.promote.trial.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
		lines.add(Component.translatable("kultivator.hall.ui.ranks.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	private static List<Component> protectorTip(CompoundTag e) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.protector"));
		lines.add(Component.translatable("kultivator.hall.ui.protector.tip", info().getIntOr("protectors_max", 1)).withStyle(ChatFormatting.GRAY));
		if (!e.getBooleanOr("protector", false) && !e.getBooleanOr("can_protect", false)) {
			lines.add(HallUi.name(e, "protect_why").copy().withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable("kultivator.hall.ui.protector.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}
}
