package dev.kultivator.client.sekte.hubungan;

import dev.kultivator.client.disciple.MuridPage;
import dev.kultivator.client.disciple.MuridSub;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.hubungan.HubunganPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.List;

/**
 * Hubungan, the ties page of the disciple scroll (DESIGN 36.2.2, contract C36.R3; added after P's "Hati" with
 * Murid › Murid › Hubungan, DESIGN 43.5). Left: every disciple this one knows, strongest tie first, each with its kind's
 * seal, colour and a small affinity bar. Right: the chosen tie: its kind and what it does, the affinity scale from
 * grudge to Dao companions with its thresholds, how often they met, their last shared moments, and the master's talismans
 * for it (swear the oath, bless, forbid, call the honour duel). Reads {@code sekte_hubungan} of the disciple info; the
 * talismans send {@code HubunganPayloads.Aksi} (the server checks everything again).
 */
public class HubunganTab extends MuridSub {

	private static final String K = "kultivator.sekte.hubungan.";
	private static final int ROW_H = 18;
	private static final int BUTTON_H = 16;

	/** One tie as the info sent it. */
	private record Tie(String soul, Component name, Ikatan kind, int affinity, int meetings, int bouts, boolean near, boolean banned, int act,
					   List<CompoundTag> history) {
	}

	private final List<Tie> ties = new ArrayList<>();
	private int selected;
	private int scroll;
	private int listW;
	private int listRows;
	private TalismanButton first;
	private TalismanButton second;

	public HubunganTab(MuridPage murid) {
		super(murid);
	}

	@Override
	public String helpTopic() {
		// the ties between disciples are part (2) of "Sekte yang Hidup"; "ikatan" is the master's bond (Murid › Jalan)
		return "sekte_hidup";
	}

	/** Its own scroll when the box is shorter (GUI scale 4): list, detail lines at 1.0 and the talismans. */
	@Override
	public int tinggiMin() {
		return 164;
	}

	@Override
	public Component title() {
		return Component.translatable(K + "tab");
	}

	@Override
	public Component shortName() {
		return Component.translatable(K + "tab.short");
	}

	private CompoundTag hub() {
		return info().getCompoundOrEmpty("sekte_hubungan");
	}

	@Override
	public void onView() {
		String keep = selected >= 0 && selected < ties.size() ? ties.get(selected).soul() : null;
		ties.clear();
		ListTag list = hub().getListOrEmpty("ties");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			List<CompoundTag> history = new ArrayList<>();
			ListTag h = t.getListOrEmpty("h");
			for (int j = 0; j < h.size(); j++) {
				history.add(h.getCompoundOrEmpty(j));
			}
			ties.add(new Tie(t.getStringOr("s", ""), t.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?")),
					Ikatan.byId(t.getStringOr("k", "none")), t.getIntOr("a", 0), t.getIntOr("m", 0), t.getIntOr("t", 0),
					t.getBooleanOr("near", false), t.getBooleanOr("ban", false), t.getIntOr("act", 0), history));
		}
		selected = 0;
		if (keep != null) {
			for (int i = 0; i < ties.size(); i++) {
				if (ties.get(i).soul().equals(keep)) {
					selected = i;
				}
			}
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, ties.size() - Math.max(1, listRows))));
	}

	private Tie chosen() {
		return selected >= 0 && selected < ties.size() ? ties.get(selected) : null;
	}

	// ================================================================== the talismans

	/** The two talismans show the first two actions open for the chosen tie. */
	private int[] actions() {
		Tie tie = chosen();
		if (tie == null || !hub().getBooleanOr("own", false)) {
			return new int[0];
		}
		List<Integer> list = new ArrayList<>();
		for (int bit : new int[]{1, 2, 8, 4}) {
			if ((tie.act() & bit) != 0) {
				list.add(bit);
			}
		}
		return list.stream().mapToInt(Integer::intValue).toArray();
	}

	private static String op(int bit) {
		return switch (bit) {
			case 1 -> "sumpah";
			case 2 -> "restui";
			case 4 -> "larang";
			default -> "duel";
		};
	}

	@Override
	protected void build() {
		listW = Math.max(100, Math.min(170, Math.round(w * 0.44F)));
		listRows = Math.max(1, (h - 16) / ROW_H);
		int rightX = x + listW + 6;
		int rightW = x + w - rightX;
		int half = (rightW - 4) / 2;
		int top = y + h - BUTTON_H;
		first = add(new TalismanButton(rightX, top, half, BUTTON_H, () -> label(0)));
		second = add(new TalismanButton(rightX + half + 4, top, rightW - half - 4, BUTTON_H, () -> label(1)));
		for (int i = 0; i < 2; i++) {
			int index = i;
			TalismanButton button = i == 0 ? first : second;
			button.urgentWhen(() -> {
				Tie tie = chosen();
				return tie != null && (tie.act() & 16) != 0;
			});
			button.enabledWhen(() -> {
				Tie tie = chosen();
				return tie != null && (tie.act() & 32) == 0 && actions().length > index;
			});
			button.onPress(() -> press(index));
			button.tooltip(() -> tip(index));
		}
	}

	private Component label(int index) {
		int[] acts = actions();
		return index < acts.length ? Component.translatable(K + "tombol." + op(acts[index])) : Component.empty();
	}

	private List<Component> tip(int index) {
		int[] acts = actions();
		if (index >= acts.length) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(K + "tombol." + op(acts[index])).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable(K + "tombol." + op(acts[index]) + ".tip"));
		Tie tie = chosen();
		if (tie != null && (tie.act() & 32) != 0) {
			lines.add(Component.translatable(K + "tab.jauh").withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable(K + "sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	private void press(int index) {
		int[] acts = actions();
		Tie tie = chosen();
		String self = hub().getStringOr("self", "");
		if (index >= acts.length || tie == null || self.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new HubunganPayloads.Aksi(op(acts[index]), self, tie.soul(), 0));
	}

	@Override
	protected boolean visible(Widget widget) {
		int n = actions().length;
		if (widget == first) {
			return n >= 1;
		}
		if (widget == second) {
			return n >= 2;
		}
		return true;
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (button != 0 || mouseX < x || mouseX >= x + listW) {
			return false;
		}
		int row = (int) ((mouseY - (y + 14)) / ROW_H);
		if (mouseY < y + 14 || row < 0 || row >= listRows) {
			return false;
		}
		int index = scroll + row;
		if (index < ties.size()) {
			selected = index;
			dev.kultivator.client.screen.ui.UiSound.press();
			return true;
		}
		return false;
	}

	/** ↑↓ PgUp PgDn Home End walk the ties (←/→ stay the disciple picker). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (ties.isEmpty()) {
			return false;
		}
		int page = Math.max(1, listRows - 1);
		int next = event.isUp() ? selected - 1 : event.isDown() ? selected + 1
				: event.key() == InputConstants.KEY_PAGEUP ? selected - page : event.key() == InputConstants.KEY_PAGEDOWN ? selected + page
				: event.key() == InputConstants.KEY_HOME ? 0 : event.key() == InputConstants.KEY_END ? ties.size() - 1 : Integer.MIN_VALUE;
		if (next == Integer.MIN_VALUE) {
			return false;
		}
		next = Math.max(0, Math.min(ties.size() - 1, next));
		if (next != selected) {
			selected = next;
			dev.kultivator.client.screen.ui.UiSound.tab();
		}
		scroll = Math.max(0, Math.min(Math.max(0, ties.size() - listRows), Math.max(Math.min(scroll, selected), selected - listRows + 1)));
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double amount) {
		if (mouseX < x || mouseX >= x + listW || ties.size() <= listRows) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, amount);
		}
		scroll = Math.max(0, Math.min(ties.size() - listRows, scroll - (int) Math.signum(amount)));
		return true;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = hub();
		heading(g, title(), x + 2.0F, y, listW - 4.0F, 1.0F);
		if (mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + 10) {
			tooltip(List.of(title().copy().withStyle(ChatFormatting.GOLD), Component.translatable(K + "tab.tip"),
					Component.translatable(K + "sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		Component empty = null;
		if (data.isEmpty()) {
			// Module R off on the server: it never sends the info, so "loading" would never end.
			empty = Component.translatable(ClientPlayNetworking.canSend(HubunganPayloads.Aksi.TYPE) ? K + "tab.memuat" : K + "tab.mati");
		} else if (!data.getBooleanOr("on", false)) {
			empty = Component.translatable(K + "tab.mati");
		} else if (!data.getBooleanOr("hall", false)) {
			empty = Component.translatable(K + "tab.tanpa_aula");
		} else if (ties.isEmpty()) {
			empty = Component.translatable(K + "tab.kosong");
		}
		if (empty != null) {
			Brush.wrap(g, font, empty, x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 1.0F, false);
			drawSeals(g, seconds);
			return;
		}
		int balance = data.getIntOr("bal", 0);
		Component mood = Component.translatable(K + "tab.suasana", (balance > 0 ? "+" : "") + balance);
		int moodColor = balance > 5 ? Palette.JADE_DARK : balance < -5 ? Palette.SEAL_DARK : Palette.INK_SOFT;
		Brush.fit(g, font, mood, x + listW + 6.0F, y + 1.0F, w - listW - 8.0F, moodColor, 1.0F, 1.0F, false);
		if (mouseX >= x + listW + 6 && mouseY >= y && mouseY < y + 9) {
			tooltip(List.of(mood, Component.translatable(K + "tab.suasana.tip")));
		}
		drawList(g, mouseX, mouseY);
		Tie tie = chosen();
		if (tie != null) {
			drawDetail(g, tie, mouseX, mouseY, seconds);
		}
	}

	/** A few faint seals in the empty page so it does not look unfinished. */
	private void drawSeals(GuiGraphicsExtractor g, float seconds) {
		String[] glyphs = {"友", "义", "侣"};
		for (int i = 0; i < glyphs.length; i++) {
			float cx = x + w * (0.3F + 0.2F * i);
			float cy = y + h * 0.62F;
			Brush.disc(g, cx, cy, 9.0F, Palette.argb(0x30, Palette.SEAL));
			Brush.fitCentered(g, font, Component.literal(glyphs[i]), cx, cy - 4.5F, 14.0F, Palette.argb(0x70, Palette.SEAL_DARK), 1.0F, 0.6F, false);
		}
	}

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int top = y + 14;
		for (int row = 0; row < listRows; row++) {
			int index = scroll + row;
			if (index >= ties.size()) {
				break;
			}
			Tie tie = ties.get(index);
			int ry = top + row * ROW_H;
			int rgb = HubunganColors.of(tie.kind());
			boolean over = mouseX >= x && mouseX < x + listW && mouseY >= ry && mouseY < ry + ROW_H - 1;
			if (index == selected) {
				g.fill(x, ry, x + listW, ry + ROW_H - 1, Palette.argb(0x50, Palette.PAPER_DEEP));
				Brush.frame(g, x, ry, x + listW, ry + ROW_H - 1, Palette.argb(0xD0, Palette.GOLD));
			} else if (over) {
				g.fill(x, ry, x + listW, ry + ROW_H - 1, Palette.argb(0x28, Palette.PAPER_DEEP));
			}
			g.fill(x + 1, ry + 1, x + 4, ry + ROW_H - 2, Palette.opaque(rgb));
			// the kind's seal
			float sx = x + 11.0F;
			float sy = ry + 8.0F;
			PelukisIkatan.segelJenis(g, font, sx, sy, 6.0F, tie.kind());
			float nx = x + 18.0F;
			float nw = listW - 22.0F;
			Component name = tie.near() ? tie.name() : tie.name().copy().withStyle(ChatFormatting.ITALIC);
			Brush.fit(g, font, name, nx, ry + 1.0F, nw * 0.62F, Palette.INK, 1.0F, 1.0F, false);
			Component kind = Component.translatable(tie.kind().key());
			float kw = Math.min(nw * 0.38F, font.width(kind));
			Brush.fit(g, font, kind, x + listW - 3.0F - kw, ry + 1.0F, kw, Palette.onPaper(rgb), 1.0F, 1.0F, false);
			// the affinity bar, zero in the middle
			float bx0 = nx;
			float bx1 = x + listW - 4.0F;
			PelukisIkatan.bilahAfinitas(g, bx0, bx1, ry + ROW_H - 5.0F, tie.affinity());
		}
		if (ties.size() > listRows) {
			// a slim scroll rail
			int railTop = top;
			int railH = listRows * ROW_H;
			float frac = listRows / (float) ties.size();
			float pos = scroll / (float) Math.max(1, ties.size() - listRows);
			float thumbH = Math.max(8.0F, railH * frac);
			float ty = railTop + (railH - thumbH) * pos;
			Brush.rect(g, x + listW + 1.0F, railTop, x + listW + 2.5F, railTop + railH, Palette.argb(0x40, Palette.INK_FAINT));
			Brush.rect(g, x + listW + 1.0F, ty, x + listW + 2.5F, ty + thumbH, Palette.argb(0xC0, Palette.GOLD_DARK));
		}
	}

	private void drawDetail(GuiGraphicsExtractor g, Tie tie, int mouseX, int mouseY, float seconds) {
		float x0 = x + listW + 6.0F;
		float width = x + w - x0 - 1.0F;
		float cy = y + 13.0F;
		int rgb = HubunganColors.of(tie.kind());
		panel(g, (int) x0 - 2, (int) cy - 1, (int) (x0 + width) + 1, (int) cy + 26);
		// the big seal of the kind, softly pulsing for the close ties
		float pulse = tie.kind().positive() ? 0.5F + 0.5F * (float) Math.sin(net.minecraft.util.Util.getMillis() / 500.0D) : 0.0F;
		float sx = x0 + 11.0F;
		float sy = cy + 12.0F;
		if (pulse > 0.0F) {
			Brush.halo(g, sx, sy, 10.0F, 3.0F, rgb, 0.25F + 0.2F * pulse);
		}
		Brush.disc(g, sx, sy, 10.0F, Palette.darken(Palette.opaque(rgb), 0.3F));
		Brush.disc(g, sx, sy, 8.6F, Palette.opaque(rgb));
		Brush.fitCentered(g, font, Brush.bold(Component.literal(HubunganColors.seal(tie.kind()))), sx, sy - 5.0F, 16.0F, 0xFFFFFFFF, 1.2F, 0.8F, true);
		float tx = sx + 14.0F;
		float tw = x0 + width - tx - 2.0F;
		Brush.fit(g, font, Brush.bold(tie.name()), tx, cy + 2.0F, tw, Palette.INK, 1.0F, 1.0F, false);
		Component kind = Component.translatable(tie.kind().key()).append(Component.literal("  " + (tie.affinity() > 0 ? "+" : "") + tie.affinity()));
		Brush.fit(g, font, kind, tx, cy + 13.0F, tw, Palette.onPaper(rgb), 1.0F, 1.0F, false);
		if (mouseX >= x0 && mouseX < x0 + width && mouseY >= cy && mouseY < cy + 26) {
			tooltip(List.of(Component.translatable(tie.kind().key()).withColor(rgb), Component.translatable(K + "jenis." + tie.kind().id() + ".desc"),
					Component.translatable(K + "sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		cy += 31.0F;
		cy = drawScale(g, tie, x0, cy, width, mouseX, mouseY);
		Component met = Component.translatable(K + "tab.bertemu", tie.meetings(), tie.bouts());
		Brush.fit(g, font, met, x0, cy, width, Palette.INK_SOFT, 1.0F, 1.0F, false);
		cy += 10.0F;
		float noteY = y + h - BUTTON_H - 11.0F;
		g.enableScissor((int) x0, (int) cy, (int) (x0 + width) + 1, (int) noteY);
		cy += Brush.wrap(g, font, Component.translatable(K + "efek." + tie.kind().id()), x0, cy, width, Palette.JADE_DARK, 1.0F, false) + 3.0F;
		g.disableScissor();
		if (!tie.history().isEmpty() && cy < noteY - 20) {
			Brush.fit(g, font, Brush.bold(Component.translatable(K + "tab.kenangan")), x0, cy, width, Palette.SEAL_DARK, 1.0F, 1.0F, false);
			cy += 10.0F;
			for (CompoundTag event : tie.history()) {
				if (cy > noteY - 10) {
					break;
				}
				int value = event.getIntOr("v", 0);
				String sign = value > 0 ? "+" + value : Integer.toString(value);
				Component line = Component.literal(sign + "  ").withColor(value >= 0 ? Palette.JADE_DARK : Palette.SEAL)
						.append(Component.translatable(reasonKey(event.getStringOr("r", ""))).withColor(Palette.INK_SOFT));
				Brush.fit(g, font, line, x0 + 2.0F, cy, width - 2.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
				cy += 10.0F;
			}
		}
		Component note = null;
		if (tie.banned() && tie.kind() != Ikatan.PASANGAN_DAO) {
			note = Component.translatable(K + "tab.dilarang");
		}
		if ((tie.act() & 16) != 0) {
			note = Component.translatable(K + "tab.menunggu");
		} else if ((tie.act() & 32) != 0 && actions().length > 0) {
			note = Component.translatable(K + "tab.jauh");
		}
		if (note != null) {
			Brush.fit(g, font, note, x0, noteY, width, Palette.WARN, 1.0F, 1.0F, false);
		}
	}

	/** The affinity scale from -100 to 100 with the thresholds of the kinds and a pointer at the value. */
	private float drawScale(GuiGraphicsExtractor g, Tie tie, float x0, float cy, float width, int mouseX, int mouseY) {
		float bx0 = x0 + 2.0F;
		float bx1 = x0 + width - 2.0F;
		float by = cy + 2.0F;
		PelukisIkatan.skala(g, x0, x0 + width, cy, tie.affinity());
		if (mouseX >= bx0 && mouseX < bx1 && mouseY >= by - 3 && mouseY < by + 8) {
			tooltip(List.of(Component.translatable(K + "tab.afinitas", tie.affinity()).withStyle(ChatFormatting.GOLD),
					Component.translatable(K + "tab.skala.tip")));
		}
		return cy + 12.0F;
	}

	private static String reasonKey(String reason) {
		String r = reason == null ? "" : reason;
		String s = r.startsWith("hubungan.") ? r.substring(9) : r.replace('.', '_');
		String key = K + "sebab." + s;
		return Language.getInstance().has(key) ? key : K + "sebab.lain";
	}
}
