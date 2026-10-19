package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.technique.spec.run.Unlocks;
import dev.kultivator.KultivatorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The bamboo detail slip of the Kitab Teknik (DESIGN 22.12.4): the large glyph and both names; chips for the Dao, the
 * grade, the type, the realm gate and Gerbang Dewa; the Sumber line ("Terinspirasi" lines in grey italic); the
 * description and lore; the live numbers from {@link TechniquePower} (damage, area, duration, count, qi and cooldown,
 * boss cap, the crater while Mode Kehancuran is on, "Di arena: kekuatan lama"); the mastery bar with today's points;
 * the acquisition checklist; and the talismans Pilih, Pasang ke Cincin (a slot picker) and Uji (op + creative).
 * It scrolls with the wheel above its talismans.
 */
public final class DetailSlip {

	private static final int BUTTON_H = 15;
	private static final String P = "kultivator.codex.detail.";

	private int x;
	private int y;
	private int w;
	private int h;
	private @Nullable TechniqueSpec spec;
	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentHeight;
	private final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;
	private boolean picker;
	private int pickerTop;
	private final Font font = Minecraft.getInstance().font;

	public void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		widgets.clear();
		picker = false;
		int by = y + h - BUTTON_H - 3;
		boolean tester = tester();
		int count = tester ? 3 : 2;
		int gap = 3;
		int bw = (w - 8 - gap * (count - 1)) / count;
		TalismanButton pick = new TalismanButton(x + 4, by, bw, BUTTON_H, () -> Component.translatable(P + "select"));
		pick.enabledWhen(() -> spec != null && SpecInfo.castable(data(), spec) && Keys.connected());
		pick.stampedWhen(() -> spec != null && spec.id().equals(data().selectedId()));
		pick.onPress(() -> {
			if (spec != null) {
				Keys.send(new Payloads.Action(Actions.SELECT_TECHNIQUE, 0, spec.id()));
			}
		});
		pick.tooltip(() -> List.of(Component.translatable(P + "select"), Component.translatable(P + "select.tip").withStyle(ChatFormatting.GRAY)));
		widgets.add(pick);
		TalismanButton assign = new TalismanButton(x + 4 + bw + gap, by, bw, BUTTON_H, () -> Component.translatable(P + "assign"));
		assign.enabledWhen(() -> spec != null && SpecInfo.castable(data(), spec) && Keys.connected());
		assign.urgentWhen(() -> picker);
		assign.onPress(() -> picker = !picker);
		assign.tooltip(() -> List.of(Component.translatable(P + "assign"), Component.translatable(P + "assign.tip").withStyle(ChatFormatting.GRAY)));
		widgets.add(assign);
		if (tester) {
			TalismanButton test = new TalismanButton(x + 4 + (bw + gap) * 2, by, bw, BUTTON_H, () -> Component.translatable(P + "test"));
			test.enabledWhen(() -> spec != null && Keys.connected());
			test.onPress(() -> {
				if (spec != null) {
					Keys.send(new Payloads.Action(Actions.TECHNIQUE, 0, "test:" + spec.id()));
				}
			});
			test.tooltip(() -> List.of(Component.translatable(P + "test"), Component.translatable(P + "test.tip").withStyle(ChatFormatting.GRAY)));
			widgets.add(test);
		}
		pickerTop = by - 36;
	}

	/** Op and in creative: the "Uji" talisman is offered (the server checks again). */
	private static boolean tester() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.player.isCreative() && minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public void setSpec(@Nullable TechniqueSpec value) {
		if (value != spec) {
			spec = value;
			scroll = 0.0F;
			picker = false;
			if (value != null && data().knows(value.id())) {
				CodexFilters.markSeen(value);
			}
		}
	}

	public @Nullable TechniqueSpec spec() {
		return spec;
	}

	private static CultivationData data() {
		return ClientCultivation.data();
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	// ================================================================== drawing

	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds, Consumer<List<Component>> tooltip) {
		bamboo(g);
		if (spec == null) {
			Brush.wrap(g, font, Component.translatable(P + "empty"), x + 8.0F, y + 10.0F, w - 16.0F, Palette.INK_SOFT, 0.7F, false);
			return;
		}
		int bodyBottom = y + h - BUTTON_H - 6;
		float maxScroll = Math.max(0.0F, contentHeight - (bodyBottom - y - 4));
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		g.enableScissor(x + 1, y + 2, x + w - 1, bodyBottom);
		float top = y + 5.0F - offset;
		float cursor = body(g, top);
		contentHeight = cursor - top + 6.0F;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = y + 4.0F + (bodyBottom - y - 8.0F) * (offset / maxScroll);
			g.fill(x + w - 3, y + 3, x + w - 2, bodyBottom - 1, Palette.CORD);
			Brush.disc(g, x + w - 2.5F, bead, 2.0F, Palette.JADE);
		}
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY)) {
				List<Component> tip = widget.tooltipLines();
				if (!tip.isEmpty()) {
					tooltip.accept(tip);
				}
			}
		}
		if (picker) {
			drawPicker(g, mouseX, mouseY, tooltip);
		}
	}

	/** Bamboo strips with knots and two cords. */
	private void bamboo(GuiGraphicsExtractor g) {
		g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
		int strip = 7;
		for (int sx = x; sx < x + w; sx += strip) {
			int right = Math.min(x + w, sx + strip);
			g.fillGradient(sx, y, right, y + h, ((sx - x) / strip) % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT, Palette.mix(Palette.BAMBOO_ALT,
					Palette.BAMBOO_KNOT, 0.15F));
			g.fill(right - 1, y, right, y + h, Palette.argb(0x50, Palette.BAMBOO_KNOT));
			int knot = y + 8 + ((sx - x) / strip) * 5 % 17;
			g.fill(sx, knot, right - 1, knot + 1, Palette.argb(0x70, Palette.BAMBOO_KNOT));
		}
		g.fill(x, y + 3, x + w, y + 4, Palette.CORD);
		g.fill(x, y + h - 4, x + w, y + h - 3, Palette.CORD);
		Brush.frame(g, x, y, x + w, y + h, Palette.argb(0xC0, Palette.GOLD_DARK));
	}

	/** The body; returns the y after the last line. */
	private float body(GuiGraphicsExtractor g, float top) {
		TechniqueSpec s = spec;
		CultivationData data = data();
		float tx = x + 7.0F;
		float room = w - 14.0F;
		float cursor = top;
		int ink = SpecInfo.ink(s);

		// 1. Glyph, names.
		float glyph = 20.0F;
		g.fill(Math.round(tx), Math.round(cursor), Math.round(tx + glyph + 4), Math.round(cursor + glyph + 4), Palette.argb(0x60, 0xFFF4E6B8));
		Brush.frame(g, Math.round(tx), Math.round(cursor), Math.round(tx + glyph + 4), Math.round(cursor + glyph + 4), SpecInfo.ribbon(s.grade()));
		Glyphs.spec(g, s, tx + glyph / 2.0F + 2.0F, cursor + glyph / 2.0F + 2.0F, glyph * 0.8F, ink);
		float nameX = tx + glyph + 8.0F;
		float nameRoom = room - glyph - 8.0F;
		float used = Brush.wrap(g, font, Brush.bold(CodexNames.name(s)), nameX, cursor + 1.0F, nameRoom, ink, 0.8F, false);
		Brush.fit(g, font, Component.literal(SearchIndex.otherName(s)), nameX, cursor + 2.0F + used, nameRoom, Palette.INK_FAINT, 0.6F, 0.45F, false);
		cursor += Math.max(glyph + 6.0F, used + 10.0F);

		// 2. Chips.
		List<Component> chips = new ArrayList<>();
		chips.add(SpecInfo.daoName(s));
		chips.add(SpecInfo.gradeName(s));
		chips.add(s.type().displayName());
		chips.add(SpecInfo.realmName(s));
		if (s.gate() != TechniqueSpec.Gate.NONE) {
			chips.add(Component.translatable(s.gate() == TechniqueSpec.Gate.DEWA_II ? "kultivator.codex.gate2" : "kultivator.codex.gate1"));
		}
		cursor = chips(g, chips, tx, cursor, room);

		// 3. Sumber.
		cursor += Brush.wrap(g, font, SpecInfo.sourceLine(s), tx, cursor, room, SpecInfo.inspired(s) ? Palette.INK_FAINT : 0xFF8A5A1E, 0.62F, false) + 3.0F;

		// 4. Description and lore.
		cursor += Brush.wrap(g, font, s.description(), tx, cursor, room, Palette.INK, 0.62F, false) + 2.0F;
		if (Language.getInstance().has(s.loreKey())) {
			cursor += Brush.wrap(g, font, Component.translatable(s.loreKey()).withStyle(ChatFormatting.ITALIC), tx, cursor, room, Palette.INK_SOFT, 0.6F,
					false) + 2.0F;
		}
		cursor = rule(g, tx, cursor, room);

		// 5. Live numbers.
		SpecInfo.Numbers numbers = SpecInfo.numbers(data, s);
		List<Component> lines = new ArrayList<>();
		if (numbers.coef() > 0.0D) {
			MutableComponent damage = Component.translatable(P + "damage", SpecInfo.decimal(numbers.coef(), 2), SpecInfo.compact(numbers.damage()));
			if (numbers.repeats() > 1) {
				damage.append(Component.translatable(P + "repeats", numbers.repeats()));
			}
			lines.add(damage.withColor(0xB3281D));
			// Lima Unsur (DESIGN 19.3.12, the element line of P7-jalan): the monster elements this art overcomes.
			Component elements = elementLine(data, s);
			if (elements != null) {
				lines.add(elements);
			}
		}
		if (!Double.isNaN(numbers.area())) {
			lines.add(Component.translatable(P + "area", Component.translatable(numbers.areaKey()), SpecInfo.decimal(numbers.areaBase(), 1),
					SpecInfo.decimal(numbers.area(), 1)));
		}
		if (numbers.duration() > 0) {
			lines.add(Component.translatable(P + "duration", SpecInfo.seconds(numbers.duration())));
		}
		if (numbers.count() > 0) {
			lines.add(Component.translatable(P + "count", numbers.count()));
		}
		Component qi = s.qiPerSecond() > 0.0D ? Component.translatable(P + "qi_per_second", SpecInfo.decimal(s.qiPerSecond(), 1))
				: Component.literal(SpecInfo.number(numbers.qi()));
		lines.add(Component.translatable(P + "costs", qi, SpecInfo.seconds(numbers.cooldown())).withColor(0x2A6FA8));
		lines.add(Component.translatable(P + "boss", Math.round(numbers.bossShare() * 100.0D)));
		if (TeknikSession.breakerOn() && s.hasDestruction()) {
			lines.add(crater(data, s).copy().withColor(0xB3281D));
		}
		for (Component line : lines) {
			cursor += Brush.wrap(g, font, line, tx, cursor, room, Palette.INK, 0.6F, false) + 0.5F;
		}
		cursor += Brush.wrap(g, font, Component.translatable(P + "arena").withStyle(ChatFormatting.ITALIC), tx, cursor, room, Palette.INK_FAINT, 0.55F,
				false) + 2.0F;
		cursor = rule(g, tx, cursor, room);

		// 6. Mastery.
		cursor = mastery(g, data, s, tx, cursor, room);
		cursor = rule(g, tx, cursor, room);

		// 7. Acquisition checklist.
		Brush.fit(g, font, Brush.bold(Component.translatable(P + "how")), tx, cursor, room, Palette.SEAL_DARK, 0.65F, 0.45F, false);
		cursor += 8.0F;
		for (Unlocks.Line line : Checklist.lines(data, s)) {
			Component mark = Component.literal(line.met() ? "✓ " : "✗ ").withColor(line.met() ? 0x2E8963 : 0xB3281D);
			MutableComponent text = Component.empty().append(mark).append(line.text());
			if (line.goal() > 0 && !line.text().getString().contains("/")) {
				text.append(Component.literal(" (" + Math.min(line.progress(), line.goal()) + "/" + line.goal() + ")").withColor(0x7A8C83));
			}
			cursor += Brush.wrap(g, font, text, tx, cursor, room, line.met() ? Palette.INK_SOFT : Palette.INK, 0.58F, false) + 0.5F;
		}
		return cursor;
	}

	/**
	 * "Unsur Logam · unggul melawan monster Kayu" (DESIGN 19.3.12): the art's element (its Dao's, else the caster's own
	 * from {@code ElementRelations.techniqueElement}) and {@code ElementRelations.strongAgainst}; Space is ×1.05 against
	 * everything. Null without an element.
	 */
	private static @Nullable Component elementLine(CultivationData data, TechniqueSpec s) {
		dev.kultivator.data.Element element = s.dao() != null ? s.dao().affinity() : dev.kultivator.elite.ElementRelations.techniqueElement(data);
		if (element == null) {
			return null;
		}
		List<dev.kultivator.data.Element> strong = dev.kultivator.elite.ElementRelations.strongAgainst(element);
		if (strong.isEmpty()) {
			return element == dev.kultivator.data.Element.SPACE
					? Component.translatable("kultivator.goals.codex.space", element.displayName()).withColor(0x2E6B4F) : null;
		}
		MutableComponent names = Component.empty();
		for (int i = 0; i < strong.size(); i++) {
			if (i > 0) {
				names.append(Component.literal(", "));
			}
			names.append(strong.get(i).displayName());
		}
		return Component.translatable("kultivator.goals.codex.strong", element.displayName(), names).withColor(0x2E6B4F);
	}

	/** "Kawah: BOWL r 6 · ≤ 512 blok" for the first carving shape at this rank (22.11.3). */
	private Component crater(CultivationData data, TechniqueSpec s) {
		DShape shape = s.destruction().get(0);
		double k = TechniquePower.kScale(data);
		double area = TechniquePower.masteryArea(SpecInfo.mastery(data, s));
		int maxRadius = KultivatorConfig.get().kehancuranMaxRadius;
		int r = (int) Math.max(1, Math.min(maxRadius, Math.round(shape.a() * k * area)));
		int cap = TechniquePower.blockCap(data);
		String kind = shape.kind().name();
		if (k <= 0.0D) {
			return Component.translatable(P + "crater_none");
		}
		return Component.translatable(P + "crater", kind, r, SpecInfo.number(cap));
	}

	private float mastery(GuiGraphicsExtractor g, CultivationData data, TechniqueSpec s, float tx, float cursor, float room) {
		int level = SpecInfo.mastery(data, s);
		int points = Mastery.points(data, s.id());
		int next = level >= 4 ? s.grade().masteryThreshold(4) : s.grade().masteryThreshold(level + 1);
		int previous = s.grade().masteryThreshold(level);
		Component title = Component.translatable(P + "mastery", SpecInfo.masteryName(level));
		Brush.fit(g, font, Brush.bold(title), tx, cursor, room * 0.6F, Palette.SEAL_DARK, 0.65F, 0.45F, false);
		Component pts = Component.literal(SpecInfo.number(points) + " / " + SpecInfo.number(next));
		float ptsScale = 0.55F;
		Brush.text(g, font, pts, tx + room - font.width(pts) * ptsScale, cursor + 1.0F, Palette.INK_SOFT, ptsScale, false);
		cursor += 8.0F;
		float fill = level >= 4 ? 1.0F : Math.max(0.0F, Math.min(1.0F, (points - previous) / (float) Math.max(1, next - previous)));
		Brush.rect(g, tx, cursor, tx + room, cursor + 3.0F, Palette.argb(0x70, Palette.BAMBOO_KNOT));
		Brush.rect(g, tx, cursor, tx + room * fill, cursor + 3.0F, Palette.GOLD);
		for (int i = 1; i <= 4; i++) {
			float bx = tx + room * i / 4.0F - 0.5F;
			Brush.rect(g, bx, cursor - 1.0F, bx + 1.0F, cursor + 4.0F, i <= level ? Palette.GOLD_DARK : Palette.INK_FAINT);
		}
		cursor += 6.0F;
		List<Component> notes = new ArrayList<>();
		if (!s.perfection().isEmpty()) {
			notes.add(Component.translatable(P + "level3", join(s.perfection())));
		}
		if (!s.peak().isEmpty()) {
			notes.add(Component.translatable(P + "level4", join(s.peak())));
		}
		notes.add(Component.translatable(P + "gates", Checklist.masteryGate(s, 3), Checklist.masteryGate(s, 4)));
		int[] today = today(data, s);
		if (today != null) {
			notes.add(Component.translatable(P + "today", Math.max(0, 200 - today[0]), Math.max(0, 20 - today[1])));
		}
		for (Component note : notes) {
			cursor += Brush.wrap(g, font, note, tx, cursor, room, Palette.INK_SOFT, 0.56F, false) + 0.5F;
		}
		return cursor + 1.0F;
	}

	/** Today's [hit and kill points, points of casts that hit nothing] from {@code extra.teknik.mday}, or null. */
	private static int[] today(CultivationData data, TechniqueSpec s) {
		CompoundTag mday = data.extra().getCompoundOrEmpty(Mastery.EXTRA).getCompoundOrEmpty("mday");
		if (!(mday.get(s.id()) instanceof ListTag list) || list.size() < 3) {
			return new int[]{0, 0};
		}
		long day = list.get(0).asLong().orElse(-1L);
		if (day != TeknikSession.today()) {
			return new int[]{0, 0};
		}
		return new int[]{list.get(1).asInt().orElse(0), list.get(2).asInt().orElse(0)};
	}

	private static Component join(List<Bonus> bonuses) {
		MutableComponent joined = Component.empty();
		for (int i = 0; i < bonuses.size(); i++) {
			if (i > 0) {
				joined.append(Component.literal(", "));
			}
			joined.append(Checklist.bonus(bonuses.get(i)));
		}
		return joined;
	}

	private float chips(GuiGraphicsExtractor g, List<Component> chips, float tx, float cursor, float room) {
		float cx = tx;
		float scale = 0.52F;
		float height = 8.0F;
		for (Component chip : chips) {
			float width = font.width(chip) * scale + 6.0F;
			if (cx + width > tx + room && cx > tx) {
				cx = tx;
				cursor += height + 2.0F;
			}
			Brush.rect(g, cx, cursor, cx + width, cursor + height, Palette.argb(0x90, 0xFFF4E6B8));
			Brush.frame(g, Math.round(cx), Math.round(cursor), Math.round(cx + width), Math.round(cursor + height), Palette.argb(0xC0, Palette.GOLD_DARK));
			Brush.text(g, font, chip, cx + 3.0F, cursor + 1.6F, Palette.INK, scale, false);
			cx += width + 2.0F;
		}
		return cursor + height + 4.0F;
	}

	private float rule(GuiGraphicsExtractor g, float tx, float cursor, float room) {
		Brush.rect(g, tx, cursor + 1.0F, tx + room, cursor + 1.6F, Palette.argb(0x90, Palette.GOLD_DARK));
		return cursor + 4.0F;
	}

	// ================================================================== the slot picker

	private void drawPicker(GuiGraphicsExtractor g, int mouseX, int mouseY, Consumer<List<Component>> tooltip) {
		int px0 = x + 3;
		int px1 = x + w - 3;
		g.fill(px0, pickerTop, px1, pickerTop + 34, 0xF2112A23);
		Brush.frame(g, px0, pickerTop, px1, pickerTop + 34, Palette.GOLD);
		Brush.fit(g, font, Component.translatable(P + "picker"), px0 + 3.0F, pickerTop + 2.0F, px1 - px0 - 6.0F, Palette.GOLD_LIGHT, 0.55F, 0.4F, false);
		String[] slots = TeknikSession.slots();
		int cell = Math.min(14, (px1 - px0 - 8) / 6);
		for (int i = 0; i < 12; i++) {
			int cx = px0 + 4 + (i % 6) * (cell + 1);
			int cy = pickerTop + 10 + (i / 6) * (cell + 1);
			boolean over = mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + cell;
			g.fill(cx, cy, cx + cell, cy + cell, over ? 0xFFF7DFA0 : Palette.TALISMAN);
			Brush.frame(g, cx, cy, cx + cell, cy + cell, i < 4 ? Palette.GOLD_DARK : Palette.TALISMAN_EDGE);
			TechniqueSpec there = slots[i].isEmpty() ? null : TechniqueRegistry.byId(slots[i]);
			if (there != null) {
				Glyphs.spec(g, there, cx + cell / 2.0F, cy + cell / 2.0F, cell * 0.6F, SpecInfo.ink(there));
			}
			Brush.text(g, font, Component.literal(String.valueOf(i + 1)), cx + 1.0F, cy + 1.0F, i < 4 ? Palette.SEAL : Palette.INK_SOFT, 0.45F, false);
			if (over) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.translatable(P + "picker.slot", i + 1));
				tip.add(there == null ? Component.translatable(P + "picker.empty").withStyle(ChatFormatting.GRAY) : there.displayName());
				tooltip.accept(tip);
			}
		}
	}

	private int pickerSlot(double mouseX, double mouseY) {
		int px0 = x + 3;
		int px1 = x + w - 3;
		int cell = Math.min(14, (px1 - px0 - 8) / 6);
		for (int i = 0; i < 12; i++) {
			int cx = px0 + 4 + (i % 6) * (cell + 1);
			int cy = pickerTop + 10 + (i / 6) * (cell + 1);
			if (mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + cell) {
				return i;
			}
		}
		return -1;
	}

	// ================================================================== input

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (picker) {
			int slot = pickerSlot(mouseX, mouseY);
			if (slot >= 0 && spec != null) {
				UiSound.press();
				Keys.send(new Payloads.Action(Actions.TECHNIQUE, 0, "slot:" + slot + ":" + spec.id()));
				picker = false;
				return true;
			}
			if (mouseY >= pickerTop && mouseY < pickerTop + 34 && contains(mouseX, mouseY)) {
				return true;
			}
		}
		for (Widget widget : widgets) {
			if (widget.contains(mouseX, mouseY)) {
				widget.press();
				pressed = widget;
				return true;
			}
		}
		if (picker) {
			picker = false;
			return contains(mouseX, mouseY);
		}
		return contains(mouseX, mouseY);
	}

	public boolean mouseReleased(double mouseX, double mouseY) {
		if (pressed == null) {
			return false;
		}
		Widget widget = pressed;
		pressed = null;
		widget.release(mouseX, mouseY);
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		scroll -= (float) amount * 14.0F;
		return true;
	}

	/** Escape closes the slot picker first. */
	public boolean escape() {
		if (picker) {
			picker = false;
			return true;
		}
		return false;
	}
}
