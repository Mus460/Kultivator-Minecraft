package dev.kultivator.client.sekte.tetua;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Realm;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.tetua.TetuaPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Balai Tetua (DESIGN 36.2.6 "UI", hub card SEKTE "Balai Tetua"): the hall of elders on the jade scroll of the other
 * Kultivator screens. Three pages under talisman tabs:
 * <ul>
 * <li><b>Jabatan</b>: the six offices, each with its seal, its holder (or vacant), the holder's realm, deputy mark, the
 * bonus it gives its domain, its affairs and whether it sits at its desk now; "Angkat" opens the picker of disciples who
 * may serve (Inti and up, the eligibility shown per disciple), "Copot" relieves after a confirming second click. The
 * office hours and the hour now sit at the foot.</li>
 * <li><b>Laporan</b>: the last morning report in full, grouped like the card.</li>
 * <li><b>Penerus</b>: the heir and the ceremony checklist, the button of the Succession Ceremony (a confirming second
 * click), the generation, the Founding Patriarch and the lineage.</li>
 * </ul>
 * Every button sends E's {@code TetuaPayloads.Act}; the server checks everything again and answers with a fresh view.
 */
public final class BalaiTetuaScreen extends Screen {

	private static final int MAX_W = 312;
	private static final int MAX_H = 232;
	private static final long UNROLL_MILLIS = 220L;
	private static final int PAGE_OFFICES = 0;
	private static final int PAGE_REPORT = 1;
	private static final int PAGE_HEIR = 2;

	private CompoundTag view;
	private int page;
	/** The office whose picker is open, or null. */
	private @Nullable Jabatan picking;
	private @Nullable Jabatan confirmDismiss;
	private boolean confirmThrone;
	private int scroll;

	private final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;
	private long openedAt;
	private long lastFrame;
	private int left;
	private int right;
	private int top;
	private int bottom;
	private int x0;
	private int x1;
	private int y0;
	private int y1;
	private static int lastPage;

	public BalaiTetuaScreen(CompoundTag view) {
		super(Component.translatable("kultivator.sekte.tetua.ui.title"));
		this.view = view;
		this.page = lastPage;
	}

	/** A fresh view from the server (after an action or a refresh). */
	void update(CompoundTag fresh) {
		this.view = fresh;
		this.confirmDismiss = null;
		this.confirmThrone = false;
		rebuild();
	}

	// ================================================================== layout

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		int w = Math.min(MAX_W, width - 12);
		int h = Math.min(MAX_H, height - 8);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2 + 4;
		y1 = y0 + h - 8;
		left = x0 + ScrollFrame.SIDE + 8;
		right = x1 - ScrollFrame.SIDE - 8;
		top = y0 + ScrollFrame.END + 4;
		bottom = y1 - ScrollFrame.END - 4;
		rebuild();
	}

	private void rebuild() {
		widgets.clear();
		pressed = null;
		int tabW = Math.min(84, (right - left - 8) / 3);
		int tx = (left + right) / 2 - (tabW * 3 + 8) / 2;
		String[] tabs = {"jabatan", "laporan", "penerus"};
		for (int i = 0; i < 3; i++) {
			int index = i;
			widgets.add(new TalismanButton(tx + i * (tabW + 4), top + 12, tabW, 13, () -> Component.translatable("kultivator.sekte.tetua.ui.tab." + tabs[index]))
					.stampedWhen(() -> page == index)
					.onPress(() -> {
						page = index;
						lastPage = index;
						picking = null;
						scroll = 0;
						UiSound.tab();
						rebuild();
					}));
		}
		switch (page) {
			case PAGE_OFFICES -> buildOffices();
			case PAGE_REPORT -> buildReport();
			default -> buildHeir();
		}
		widgets.add(new TalismanButton(right - 58, bottom - 13, 58, 13, () -> Component.translatable("kultivator.sekte.tetua.ui.close"))
				.onPress(this::onClose));
	}

	private int contentTop() {
		return top + 30;
	}

	private void buildOffices() {
		ListTag offices = view.getListOrEmpty("offices");
		if (picking != null) {
			Jabatan office = picking;
			int y = contentTop() + 14;
			int i = 0;
			for (Tag tag : view.getListOrEmpty("candidates")) {
				if (!(tag instanceof CompoundTag c) || y > bottom - 34) {
					continue;
				}
				String soul = c.getStringOr("soul", "");
				int eligible = eligible(c.getIntOr("rank", 0), office);
				Component name = c.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				int rowY = y;
				widgets.add(new TalismanButton(left + 4, rowY, (right - left) / 2 - 8, 12, () -> name)
						.enabledWhen(() -> eligible > 0)
						.onPress(() -> send(TetuaPayloads.OP_APPOINT, office.id() + ":" + soul))
						.tooltip(() -> List.of(name, Component.translatable(eligible == 2 ? "kultivator.sekte.tetua.ui.pick.full"
								: eligible == 1 ? "kultivator.sekte.tetua.ui.pick.deputy" : "kultivator.sekte.tetua.ui.pick.no").withStyle(ChatFormatting.GRAY))));
				y += 14;
				i++;
			}
			widgets.add(new TalismanButton(left + 4, bottom - 13, 70, 13, () -> Component.translatable("kultivator.sekte.tetua.ui.cancel"))
					.onPress(() -> {
						picking = null;
						rebuild();
					}));
			return;
		}
		int rowH = Math.max(20, Math.min(26, (bottom - 18 - contentTop()) / 6));
		int y = contentTop();
		for (int i = 0; i < offices.size() && i < Jabatan.values().length; i++) {
			if (!(offices.get(i) instanceof CompoundTag line)) {
				continue;
			}
			Jabatan office = Jabatan.byId(line.getStringOr("id", ""));
			if (office == null) {
				continue;
			}
			boolean filled = !line.getStringOr("soul", "").isEmpty();
			int bx = right - 54;
			int by = y + (rowH - 12) / 2;
			widgets.add(new TalismanButton(bx, by, 52, 12, () -> Component.translatable("kultivator.sekte.tetua.ui.appoint"))
					.onPress(() -> {
						picking = office;
						UiSound.press();
						rebuild();
					})
					.tooltip(() -> List.of(Component.translatable("kultivator.sekte.tetua.ui.appoint.tip", Component.translatable(office.key())))));
			if (filled) {
				widgets.add(new TalismanButton(bx - 50, by, 46, 12, () -> Component.translatable(confirmDismiss == office
						? "kultivator.sekte.tetua.ui.dismiss.sure" : "kultivator.sekte.tetua.ui.dismiss"))
						.urgentWhen(() -> confirmDismiss == office)
						.onPress(() -> {
							if (confirmDismiss == office) {
								send(TetuaPayloads.OP_DISMISS, office.id());
							} else {
								confirmDismiss = office;
								UiSound.ask();
							}
						}));
			}
			y += rowH;
		}
	}

	private void buildReport() {
		widgets.add(new TalismanButton(left + 4, bottom - 13, 90, 13, () -> Component.translatable("kultivator.sekte.tetua.ui.report.show"))
				.onPress(() -> {
					send(TetuaPayloads.OP_REPORT, "");
					onClose();
				})
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.tetua.ui.report.show.tip"))));
	}

	private void buildHeir() {
		boolean running = view.getIntOr("running", -1) >= 0;
		boolean ready = view.getBooleanOr("ready", false);
		int bw = Math.min(170, right - left - 70);
		widgets.add(new TalismanButton(left + 4, bottom - 13, bw, 13, () -> Component.translatable(running ? "kultivator.sekte.tetua.ui.throne.running"
				: confirmThrone ? "kultivator.sekte.tetua.ui.throne.sure" : "kultivator.sekte.tetua.ui.throne"))
				.urgentWhen(() -> ready && !running)
				.enabledWhen(() -> ready && !running)
				.onPress(() -> {
					if (confirmThrone) {
						send(TetuaPayloads.OP_THRONE, "");
						onClose();
					} else {
						confirmThrone = true;
						UiSound.ask();
					}
				})
				.tooltip(() -> List.of(Component.translatable("kultivator.sekte.tetua.ui.throne"),
						Component.translatable("kultivator.sekte.tetua.ui.throne.tip").withStyle(ChatFormatting.GRAY))));
	}

	/** 2 full, 1 deputy, 0 none (mirrors the server's rule for the picker's hints; the server decides). */
	private static int eligible(int rank, Jabatan office) {
		if (office == Jabatan.TETUA_AGUNG) {
			return rank >= SectRanks.TETUA_AGUNG ? 2 : rank == SectRanks.TETUA ? 1 : 0;
		}
		return rank >= SectRanks.TETUA ? 2 : rank == SectRanks.INTI ? 1 : 0;
	}

	private void send(String op, String arg) {
		UiSound.press();
		TetuaClient.send(op, arg);
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		float open = Eased.out(Math.min(1.0F, (now - openedAt) / (float) UNROLL_MILLIS));
		int mid = (y0 + y1) / 2;
		int half = Math.round((y1 - y0) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, y0, y1);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		Component hall = view.read("hall", ComponentSerialization.CODEC).orElse(Component.empty());
		Brush.fitCentered(g, font, Brush.bold(Component.translatable("kultivator.sekte.tetua.ui.title.hall", hall)), (left + right) / 2.0F, top,
				right - left - 40, Palette.INK, 0.95F, 0.6F, false);
		LaporanCard.seal(g, font, left + 8, top + 5, 14, "老");
		switch (page) {
			case PAGE_OFFICES -> drawOffices(g, mouseX, mouseY);
			case PAGE_REPORT -> drawReport(g);
			default -> drawHeir(g);
		}
		List<Component> tooltip = null;
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && !widget.tooltipLines().isEmpty()) {
				tooltip = widget.tooltipLines();
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		if (tooltip != null) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	private void drawOffices(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (picking != null) {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.pick.title", Component.translatable(picking.key())), left + 4,
					contentTop(), right - left - 8, Palette.SEAL_DARK, 0.8F, 0.5F, false);
			ListTag candidates = view.getListOrEmpty("candidates");
			if (candidates.isEmpty()) {
				Brush.wrap(g, font, Component.translatable("kultivator.sekte.tetua.ui.pick.none"), left + 4, contentTop() + 16, right - left - 8,
						Palette.INK_SOFT, 0.65F, false);
				return;
			}
			float y = contentTop() + 14;
			float cx = (left + right) / 2.0F;
			for (Tag tag : candidates) {
				if (!(tag instanceof CompoundTag c) || y > bottom - 34) {
					continue;
				}
				Component info = Component.translatable("kultivator.sekte.tetua.ui.pick.line", SectRanks.rankName(c.getIntOr("rank", 0)),
						Realm.byOrdinal(c.getIntOr("realm", 0)).displayName());
				Brush.fit(g, font, info, cx, y + 2, right - cx - 4, Palette.INK_SOFT, 0.6F, 0.42F, false);
				String held = c.getStringOr("office", "");
				Jabatan heldOffice = Jabatan.byId(held);
				if (heldOffice != null) {
					Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.pick.holds", Component.translatable(heldOffice.key())), cx, y + 8,
							right - cx - 4, Palette.JADE_DARK, 0.5F, 0.4F, false);
				}
				y += 14;
			}
			return;
		}
		ListTag offices = view.getListOrEmpty("offices");
		int rowH = Math.max(20, Math.min(26, (bottom - 18 - contentTop()) / 6));
		int y = contentTop();
		for (int i = 0; i < offices.size() && i < Jabatan.values().length; i++) {
			if (!(offices.get(i) instanceof CompoundTag line)) {
				continue;
			}
			Jabatan office = Jabatan.byId(line.getStringOr("id", ""));
			if (office == null) {
				continue;
			}
			int rgb = OFFICE_COLOURS[office.ordinal()];
			if (i % 2 == 0) {
				g.fill(left, y, right, y + rowH, Palette.argb(0x18, Palette.INK));
			}
			LaporanCard.seal(g, font, left + 10, y + rowH / 2.0F, 15, OFFICE_SEALS[office.ordinal()]);
			float tx = left + 22;
			float tw = right - 112 - tx;
			Brush.fit(g, font, Brush.bold(Component.translatable(office.key())), tx, y + 2, tw, Palette.argb(255, darken(rgb)), 0.7F, 0.5F, false);
			String soul = line.getStringOr("soul", "");
			if (soul.isEmpty()) {
				Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.vacant"), tx, y + 11, tw, Palette.INK_FAINT, 0.62F, 0.45F, false);
			} else {
				Component name = line.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				boolean deputy = line.getBooleanOr("wakil", false);
				Component who = deputy ? Component.translatable("kultivator.sekte.tetua.ui.deputy", name) : name;
				Brush.fit(g, font, who, tx, y + 10, tw * 0.55F, Palette.INK, 0.66F, 0.45F, false);
				String status = !line.getBooleanOr("loaded", false) ? "away" : line.getBooleanOr("desk", false) ? "desk" : "idle";
				Component detail = Component.translatable("kultivator.sekte.tetua.ui.detail", Realm.byOrdinal(line.getIntOr("realm", 0)).displayName(),
						factorText(line.getDoubleOr("factor", 1.0D)), line.getIntOr("urusan", 0),
						Component.translatable("kultivator.sekte.tetua.ui.status." + status));
				Brush.fit(g, font, detail, tx + tw * 0.55F + 2, y + 11, tw * 0.45F - 2, Palette.INK_SOFT, 0.5F, 0.38F, false);
			}
			y += rowH;
		}
		Component hours = Component.translatable("kultivator.sekte.tetua.ui.hours", pad(view.getIntOr("from", 6)), pad(view.getIntOr("to", 18)),
				pad(view.getIntOr("hour", 0)), Component.translatable(view.getBooleanOr("open", false) ? "kultivator.sekte.tetua.ui.hours.open"
						: "kultivator.sekte.tetua.ui.hours.closed"));
		Brush.fit(g, font, hours, left + 4, bottom - 10, right - left - 70, Palette.INK_SOFT, 0.58F, 0.42F, false);
	}

	private void drawReport(GuiGraphicsExtractor g) {
		CompoundTag report = view.getCompoundOrEmpty("report");
		if (report.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.tetua.ui.report.none"), left + 6, contentTop() + 4, right - left - 12,
					Palette.INK_SOFT, 0.7F, false);
			return;
		}
		g.enableScissor(left, contentTop(), right, bottom - 16);
		LaporanCard.draw(g, font, report, LaporanCard.rowsOf(report), left + 6, contentTop() + 2, right - left - 12, bottom - 18 + 24, 1.0F);
		g.disableScissor();
	}

	private void drawHeir(GuiGraphicsExtractor g) {
		float y = contentTop();
		float w = right - left - 8;
		float half = w / 2.0F - 4;
		// left column: the heir and the checklist
		CompoundTag heir = view.getCompoundOrEmpty("heir");
		y = heading(g, Component.translatable("kultivator.sekte.tetua.ui.heir"), left + 4, y, half);
		if (heir.getStringOr("soul", "").isEmpty()) {
			y += Brush.wrap(g, font, Component.translatable("kultivator.sekte.tetua.ui.heir.none"), left + 4, y, half, Palette.INK_SOFT, 0.6F, false) + 2;
		} else {
			Component name = heir.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Brush.fit(g, font, Brush.bold(name), left + 4, y, half, Palette.SEAL_DARK, 0.78F, 0.5F, false);
			y += 9;
			if (heir.getBooleanOr("loaded", false)) {
				Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.heir.line", Realm.byOrdinal(heir.getIntOr("realm", 0)).displayName(),
						heir.getIntOr("stars", 0), heir.getIntOr("hexes", 0)), left + 4, y, half, Palette.INK_SOFT, 0.58F, 0.42F, false);
			} else {
				Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.heir.away"), left + 4, y, half, Palette.INK_FAINT, 0.58F, 0.42F, false);
			}
			y += 9;
		}
		y += 3;
		for (Tag tag : view.getListOrEmpty("reqs")) {
			if (!(tag instanceof CompoundTag r) || y > bottom - 26) {
				continue;
			}
			boolean ok = r.getBooleanOr("ok", false);
			Component text = r.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
			Brush.fit(g, font, Component.literal(ok ? "✔" : "✘"), left + 4, y, 8, ok ? Palette.JADE : Palette.DANGER, 0.7F, 0.5F, false);
			Brush.fit(g, font, text, left + 13, y, half - 9, ok ? Palette.INK : Palette.INK_SOFT, 0.58F, 0.4F, false);
			y += 9;
		}
		int running = view.getIntOr("running", -1);
		if (running >= 0) {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.throne.left", running), left + 4, y + 2, half, Palette.SEAL, 0.62F, 0.45F,
					false);
		}
		// right column: the generation, the founder and the lineage
		float rx = left + 8 + half + 4;
		float ry = contentTop();
		ry = heading(g, Component.translatable("kultivator.sekte.tetua.ui.lineage"), rx, ry, half);
		int gen = view.getIntOr("gen", 0);
		Component owner = view.read("owner", ComponentSerialization.CODEC).orElse(Component.empty());
		if (gen >= 2) {
			Component ketua = view.read("ketua", ComponentSerialization.CODEC).orElse(Component.translatable("kultivator.sekte.tetua.none"));
			Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.ketua", gen, ketua), rx, ry, half, Palette.argb(255, 0x8A671B), 0.66F, 0.45F,
					false);
			ry += 9;
		}
		if (view.getBooleanOr("founder", false)) {
			Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.title.founder", owner), rx, ry, half, Palette.SEAL_DARK, 0.62F, 0.45F, false);
			ry += 10;
		} else {
			ry += Brush.wrap(g, font, Component.translatable("kultivator.sekte.tetua.ui.founder.hint"), rx, ry, half, Palette.INK_FAINT, 0.55F, false) + 2;
		}
		for (Tag tag : view.getListOrEmpty("lineage")) {
			if (!(tag instanceof CompoundTag line) || ry > bottom - 26) {
				continue;
			}
			Component name = line.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			Component text = Component.translatable(line.getBooleanOr("founder", false) ? "kultivator.sekte.tetua.ui.lineage.founder"
					: "kultivator.sekte.tetua.ui.lineage.line", line.getIntOr("gen", 0), name, line.getLongOr("day", 0L));
			Brush.dot(g, rx + 2, ry + 3.5F, 1.4F, Palette.argb(220, Palette.GOLD_DARK));
			Brush.fit(g, font, text, rx + 6, ry, half - 6, Palette.INK, 0.58F, 0.42F, false);
			ry += 9;
		}
		Brush.fit(g, font, Component.translatable("kultivator.sekte.tetua.ui.age", view.getIntOr("age", 0)), rx, bottom - 26, half, Palette.INK_FAINT,
				0.55F, 0.4F, false);
	}

	private float heading(GuiGraphicsExtractor g, Component text, float x, float y, float w) {
		Brush.fit(g, font, Brush.bold(text), x, y, w, Palette.SEAL_DARK, 0.75F, 0.5F, false);
		Brush.rect(g, x, y + 9.0F, x + w, y + 9.6F, Palette.argb(0xB0, Palette.GOLD));
		return y + 12.0F;
	}

	private static final String[] OFFICE_SEALS = {"宗", "律", "經", "庫", "使", "藥"};
	private static final int[] OFFICE_COLOURS = {0xFFD54A, 0xC8463C, 0x4A8CFF, 0xE0A84A, 0x3FC48A, 0x6CFF8A};

	private static int darken(int rgb) {
		int r = (rgb >> 16 & 255) * 55 / 100;
		int gr = (rgb >> 8 & 255) * 55 / 100;
		int b = (rgb & 255) * 55 / 100;
		return r << 16 | gr << 8 | b;
	}

	private static String pad(int hour) {
		return (hour < 10 ? "0" : "") + hour + ":00";
	}

	private static String factorText(double factor) {
		return "×" + String.format(java.util.Locale.ROOT, "%.2f", factor);
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (Widget widget : widgets) {
				if (widget.contains(event.x(), event.y())) {
					widget.press();
					pressed = widget;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Widget widget = pressed;
		if (widget != null) {
			pressed = null;
			widget.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		if (picking != null) {
			picking = null;
			rebuild();
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		UiSound.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
