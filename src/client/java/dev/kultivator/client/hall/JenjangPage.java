package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Jenjang tablet (DESIGN 20.3.6): a six-storey pagoda, Murid Tercatat at the bottom and the Tetua Agung at the top,
 * with a seal (face) for every disciple on its floor. Clicking a seal opens its slip on the right: realm, rank, Poin
 * Kontribusi, technique slots, the checklist for the next rank and the talismans "Naikkan", "Turunkan", Pelindung Sekte
 * and Anugerah Ketua (pool PK to the disciple). Sect mates see the hall read-only and get no list.
 */
public class JenjangPage extends HallPage {

	private static final int FLOORS = 6;
	private static final int SEAL = 12;
	private static final int BUTTON_H = 12;
	private static final long CONFIRM_MILLIS = 3000L;

	private final List<RankSeal> seals = new ArrayList<>();
	private String builtKey = "";
	private boolean builtReadonly;
	private String selected = "";
	private String demoteSoul = "";
	private long demoteUntil;
	private int pagodaW;
	private int slipX;
	private int slipW;
	private int buttonsY;

	public JenjangPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.jenjang");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.jenjang.short");
	}

	@Override
	public int icon() {
		return Glyphs.TECHNIQUES;
	}

	private boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	private ListTag disciples() {
		return info().getListOrEmpty("disciples");
	}

	private @Nullable CompoundTag entry(String soul) {
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

	/** Which seals exist where: rebuilt when a disciple joins, leaves or changes floor. */
	private String key() {
		StringBuilder key = new StringBuilder(readonly() ? "r" : "o");
		for (Tag tag : disciples()) {
			if (tag instanceof CompoundTag entry) {
				key.append(entry.getStringOr("soul", "")).append(':').append(entry.getIntOr("rank", 0))
						.append(entry.getBooleanOr("away", false) ? 'a' : entry.getBooleanOr("unloaded", false) ? 'u' : 'p').append(';');
			}
		}
		return key.toString();
	}

	@Override
	public boolean needsRebuild() {
		return !key().equals(builtKey);
	}

	private int floorH() {
		return Math.max(SEAL + 11, Math.min(32, (h - 2) / FLOORS));
	}

	private int floorTop(int floor) {
		return y + h - (floor + 1) * floorH();
	}

	private int floorX0(int floor) {
		return x + 2 + floor * 5;
	}

	private int floorX1(int floor) {
		return x + pagodaW - 2 - floor * 5;
	}

	@Override
	protected void build() {
		builtKey = key();
		builtReadonly = readonly();
		seals.clear();
		pagodaW = Math.max(118, Math.min(170, Math.round(w * 0.52F)));
		slipX = x + pagodaW + 4;
		slipW = x + w - slipX;
		buttonsY = y + h - BUTTON_H * 3 - 6;
		if (builtReadonly) {
			return;
		}
		if (selectedEntry() == null) {
			selected = "";
			for (Tag tag : disciples()) {
				if (tag instanceof CompoundTag entry && (selected.isEmpty() || present(entry) && !present(entry(selected)))) {
					selected = entry.getStringOr("soul", "");
				}
			}
		}
		// The seals, floor by floor.
		int[] placed = new int[FLOORS];
		for (Tag tag : disciples()) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			int floor = Math.max(0, Math.min(FLOORS - 1, entry.getIntOr("rank", 1)));
			int room = Math.max(1, (floorX1(floor) - floorX0(floor) - 6) / (SEAL + 2));
			int index = placed[floor]++;
			if (index >= room) {
				continue;
			}
			int sx = floorX0(floor) + 4 + index * (SEAL + 2);
			int sy = floorTop(floor) + floorH() - SEAL - 3;
			String soul = entry.getStringOr("soul", "");
			RankSeal seal = new RankSeal(sx, sy, soul);
			seal.onPress(() -> {
				selected = soul;
				demoteSoul = "";
			});
			seals.add(add(seal));
		}

		int half = (slipW - 6) / 2;
		TalismanButton promote = new TalismanButton(slipX + 2, buttonsY, half, BUTTON_H, this::promoteLabel);
		promote.enabledWhen(() -> {
			CompoundTag e = selectedEntry();
			return present(e) && e.getIntOr("rank", 0) < FLOORS - 1;
		});
		promote.urgentWhen(() -> ready(selectedEntry()));
		promote.onPress(() -> HallClient.send(Halls.OP_PROMOTE, selected));
		promote.tooltip(this::promoteTip);
		add(promote);

		TalismanButton demote = new TalismanButton(slipX + 4 + half, buttonsY, slipW - 6 - half, BUTTON_H,
				() -> Component.translatable(demoteArmed() ? "kultivator.hall.ui.demote_sure" : "kultivator.hall.ui.demote"));
		demote.enabledWhen(() -> {
			CompoundTag e = selectedEntry();
			return present(e) && e.getIntOr("rank", 0) > 0;
		});
		demote.urgentWhen(this::demoteArmed);
		demote.onPress(() -> {
			if (demoteArmed()) {
				demoteSoul = "";
				HallClient.send(Halls.OP_DEMOTE, selected);
			} else {
				demoteSoul = selected;
				demoteUntil = Util.getMillis() + CONFIRM_MILLIS;
			}
		});
		demote.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.demote"), Component.translatable("kultivator.hall.ui.demote.tip")));
		add(demote);

		TalismanButton protector = new TalismanButton(slipX + 2, buttonsY + BUTTON_H + 2, slipW - 4, BUTTON_H, () -> {
			CompoundTag e = selectedEntry();
			return Component.translatable(e != null && e.getBooleanOr("protector", false) ? "kultivator.hall.ui.protector_off"
					: "kultivator.hall.ui.protector_on");
		});
		protector.enabledWhen(() -> {
			CompoundTag e = selectedEntry();
			return present(e) && (e.getBooleanOr("protector", false) || e.getBooleanOr("can_protect", false));
		});
		protector.tooltip(this::protectorTip);
		protector.onPress(() -> HallClient.send(Halls.OP_PROTECTOR, selected));
		add(protector);

		int[] grants = info().getIntArray("grants").orElse(new int[]{10, 50, 100, 500});
		int count = Math.max(1, grants.length);
		int gw = (slipW - 4 - (count - 1) * 2) / count;
		for (int i = 0; i < grants.length; i++) {
			int amount = grants[i];
			TalismanButton grant = new TalismanButton(slipX + 2 + i * (gw + 2), buttonsY + (BUTTON_H + 2) * 2, gw, BUTTON_H,
					() -> Component.literal("+" + amount));
			grant.enabledWhen(() -> present(selectedEntry()) && info().getIntOr("pool", 0) >= amount);
			grant.onPress(() -> HallClient.send(Halls.OP_GRANT, selected + ":" + amount));
			grant.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.grant", amount),
					Component.translatable("kultivator.hall.ui.grant.tip", info().getIntOr("pool", 0)),
					Component.translatable("kultivator.hall.ui.grant.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			add(grant);
		}
	}

	private boolean demoteArmed() {
		return !demoteSoul.isEmpty() && demoteSoul.equals(selected) && Util.getMillis() < demoteUntil;
	}

	/** Every requirement holds except the Latihan Khusus, which the promotion itself starts. */
	private static boolean ready(@Nullable CompoundTag entry) {
		if (!present(entry) || entry.getIntOr("rank", 0) >= FLOORS - 1) {
			return false;
		}
		ListTag reqs = entry.getListOrEmpty("reqs");
		for (Tag tag : reqs) {
			if (tag instanceof CompoundTag line && !line.getBooleanOr("ok", false)) {
				return false;
			}
		}
		return true;
	}

	private Component promoteLabel() {
		CompoundTag e = selectedEntry();
		int cost = e == null ? 0 : e.getIntOr("cost", 0);
		return cost > 0 ? Component.translatable("kultivator.hall.ui.promote_cost", cost) : Component.translatable("kultivator.hall.ui.promote");
	}

	private List<Component> promoteTip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.promote"));
		CompoundTag e = selectedEntry();
		if (e == null) {
			return lines;
		}
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

	private List<Component> protectorTip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.protector"));
		lines.add(Component.translatable("kultivator.hall.ui.protector.tip", info().getIntOr("protectors_max", 1)).withStyle(ChatFormatting.GRAY));
		CompoundTag e = selectedEntry();
		if (e != null && !e.getBooleanOr("protector", false) && !e.getBooleanOr("can_protect", false)) {
			lines.add(HallUi.name(e, "protect_why").copy().withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable("kultivator.hall.ui.protector.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		drawPagoda(g);
		if (builtReadonly) {
			panel(g, slipX, y, x + w, y + h);
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.ranks_readonly"), slipX + 5.0F, y + 6.0F, slipW - 10.0F, Palette.INK_SOFT, 0.65F,
					false);
			return;
		}
		drawSlip(g);
	}

	/** The pagoda: six floors narrowing upwards, each with its roof, its rank name (and seats) and the overflow count. */
	private void drawPagoda(GuiGraphicsExtractor g) {
		int floorH = floorH();
		int[] counts = new int[FLOORS];
		for (Tag tag : disciples()) {
			if (tag instanceof CompoundTag entry) {
				counts[Math.max(0, Math.min(FLOORS - 1, entry.getIntOr("rank", 1)))]++;
			}
		}
		// The spire above the top floor.
		int topY = floorTop(FLOORS - 1);
		float cx = x + pagodaW / 2.0F;
		Brush.rect(g, cx - 0.8F, topY - 7.0F, cx + 0.8F, topY, Palette.GOLD_DARK);
		Brush.disc(g, cx, topY - 7.5F, 1.8F, Palette.GOLD);
		for (int floor = 0; floor < FLOORS; floor++) {
			int top = floorTop(floor);
			int x0 = floorX0(floor);
			int x1 = floorX1(floor);
			int trim = HallUi.TRIM[floor];
			// Body: parchment tinted with the floor's trim colour, gold frame, two pillars.
			g.fill(x0, top + 3, x1, top + floorH, Palette.mix(Palette.PAPER_TOP, Palette.opaque(trim), 0.18F));
			Brush.frame(g, x0, top + 3, x1, top + floorH, Palette.argb(0xC0, Palette.GOLD_DARK));
			g.fill(x0 + 1, top + 4, x0 + 2, top + floorH - 1, Palette.argb(0x80, Palette.ROD_LIGHT));
			g.fill(x1 - 2, top + 4, x1 - 1, top + floorH - 1, Palette.argb(0x80, Palette.ROD_LIGHT));
			// Roof with upturned eaves.
			Brush.rect(g, x0 - 4.0F, top + 1.0F, x1 + 4.0F, top + 3.5F, Palette.JADE_DARK);
			Brush.rect(g, x0 - 2.0F, top, x1 + 2.0F, top + 1.2F, Palette.JADE);
			Brush.rect(g, x0 - 5.5F, top - 0.5F, x0 - 3.5F, top + 2.0F, Palette.JADE_DARK);
			Brush.rect(g, x1 + 3.5F, top - 0.5F, x1 + 5.5F, top + 2.0F, Palette.JADE_DARK);
			// The rank name in the trim colour, with the seats of the elder floors.
			MutableComponent label = Component.translatable("kultivator.hall.rank." + HallUi.RANK_ID[floor]);
			if (floor == 4) {
				label = Component.translatable("kultivator.hall.ui.seats", label, counts[floor], info().getIntOr("elders_max", 1));
			} else if (floor == 5) {
				label = Component.translatable("kultivator.hall.ui.seats", label, counts[floor], 1);
			}
			Brush.fit(g, font, Brush.bold(label), x0 + 4.0F, top + 4.5F, x1 - x0 - 8.0F, Palette.onPaper(trim), 0.5F, 0.35F, false);
			int room = Math.max(1, (x1 - x0 - 6) / (SEAL + 2));
			if (counts[floor] > room) {
				Brush.fit(g, font, Component.literal("+" + (counts[floor] - room)), x1 - 14.0F, top + floorH - 9.0F, 12.0F, Palette.INK_SOFT, 0.55F,
						0.4F, false);
			} else if (counts[floor] == 0) {
				Brush.fit(g, font, Component.translatable("kultivator.hall.ui.floor_empty"), x0 + 5.0F, top + floorH - 9.0F, x1 - x0 - 10.0F,
						Palette.INK_FAINT, 0.45F, 0.35F, false);
			}
		}
	}

	/** The selected disciple's slip: who it is, its numbers and the checklist for the next floor. */
	private void drawSlip(GuiGraphicsExtractor g) {
		panel(g, slipX, y, x + w, buttonsY - 3);
		float tx = slipX + 4.0F;
		float tw = slipW - 8.0F;
		float cursor = y + 3.0F;
		CompoundTag e = selectedEntry();
		if (e == null) {
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.ranks_none"), tx, cursor + 2.0F, tw, Palette.INK_SOFT, 0.62F, false);
			return;
		}
		heading(g, HallUi.name(e, "name"), tx - 1.0F, cursor, tw, 0.7F);
		cursor += 11.0F;
		int rank = e.getIntOr("rank", 0);
		if (e.getBooleanOr("away", false)) {
			cursor += HallUi.line(g, font, HallUi.rankName(rank), tx, cursor, tw, Palette.INK, 0.6F);
			Component reason = Component.translatableWithFallback("kultivator.hall.away." + e.getStringOr("reason", "other"), e.getStringOr("reason", "?"));
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.ranks_away", reason), tx, cursor + 2.0F, tw, Palette.INK_SOFT, 0.55F, false);
			return;
		}
		cursor += HallUi.line(g, font, HallUi.realmLine(e), tx, cursor, tw, Palette.INK, 0.6F);
		if (e.getBooleanOr("unloaded", false)) {
			cursor += HallUi.line(g, font, HallUi.rankName(rank), tx, cursor, tw, Palette.INK, 0.6F);
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.ranks_unloaded"), tx, cursor + 2.0F, tw, Palette.INK_SOFT, 0.55F, false);
			return;
		}
		MutableComponent rankLine = Component.translatable("kultivator.hall.ui.rank_pk", HallUi.rankName(rank), e.getIntOr("pk", 0));
		cursor += HallUi.line(g, font, rankLine, tx, cursor, tw, Palette.INK, 0.58F);
		int slots = e.getIntOr("slots", -1);
		Component slotLine = slots < 0 ? Component.translatable("kultivator.hall.ui.slots_all", e.getIntOr("learned", 0))
				: Component.translatable("kultivator.hall.ui.slots", e.getIntOr("learned", 0), slots);
		cursor += HallUi.line(g, font, slotLine, tx, cursor, tw, Palette.INK_SOFT, 0.55F);
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
			cursor += HallUi.line(g, font, line, tx, cursor, tw, Palette.INK, 0.55F);
		}
		cursor += 2.0F;
		if (rank >= FLOORS - 1) {
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.rank_top"), tx, cursor, tw, Palette.GOLD_DARK, 0.58F, false);
			return;
		}
		cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.next_rank", HallUi.rankName(rank + 1)), tx, cursor, tw,
				Palette.SEAL_DARK, 0.58F) + 1.0F;
		for (Tag tag : e.getListOrEmpty("reqs")) {
			if (!(tag instanceof CompoundTag req) || cursor > buttonsY - 10) {
				continue;
			}
			boolean ok = req.getBooleanOr("ok", false);
			HallUi.mark(g, tx, cursor + 0.5F, ok);
			cursor += HallUi.line(g, font, HallUi.name(req, "text"), tx + 7.0F, cursor, tw - 7.0F, ok ? Palette.INK : Palette.INK_SOFT, 0.52F) + 0.5F;
		}
	}

	// ================================================================== a disciple's seal on its floor

	/** A disciple's face framed in its rank's trim colour; the chosen one wears a gold ring, absent ones are greyed. */
	private final class RankSeal extends Widget {

		private final String soul;

		RankSeal(int x, int y, String soul) {
			super(x, y, SEAL, SEAL);
			this.soul = soul;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			CompoundTag e = entry(soul);
			if (e == null) {
				return;
			}
			int trim = HallUi.TRIM[Math.max(0, Math.min(FLOORS - 1, e.getIntOr("rank", 1)))];
			boolean chosen = soul.equals(selected);
			if (chosen) {
				g.fill(x - 2, y - 2, x + w + 2, y + h + 2, Palette.GOLD_LIGHT);
			}
			g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Palette.opaque(trim));
			HallUi.face(g, e.getIntOr("skin", 0), e.getBooleanOr("slim", false), x, y, SEAL);
			if (!present(e)) {
				g.fill(x, y, x + w, y + h, 0x90707A75);
			}
			if (e.getBooleanOr("protector", false)) {
				g.fill(x + w - 3, y, x + w, y + 3, Palette.SEAL);
			}
			if (over) {
				g.fill(x, y, x + w, y + h, 0x30FFFFFF);
			}
		}

		@Override
		public List<Component> tooltipLines() {
			CompoundTag e = entry(soul);
			if (e == null) {
				return List.of();
			}
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
	}
}
