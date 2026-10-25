package dev.kultivator.client.disciple;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orders: stacked talismans (Follow, Stay &amp; guard, Meditate, Assist, Guard the Hall) with the active order stamped, and on the
 * right the meaning of the order under the mouse (or the active one) with a small ink picture of it.
 */
final class OrderTab extends DiscipleTab {

	private static final int BUTTON_H = 20;
	private static final int GAP = 6;

	private final List<TalismanButton> buttons = new ArrayList<>();
	private int columnW;

	OrderTab(DiscipleScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.discipleui.tab.orders");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.discipleui.tab.orders.short");
	}

	@Override
	protected void build() {
		buttons.clear();
		columnW = Math.max(96, Math.min(150, Math.round(w * 0.44F)));
		int top = y + 16;
		int gap = Math.max(3, Math.min(GAP, (h - 16 - DiscipleView.MODES.length * BUTTON_H) / DiscipleView.MODES.length));
		for (int i = 0; i < DiscipleView.MODES.length; i++) {
			int index = i;
			String mode = DiscipleView.MODES[i];
			TalismanButton button = new TalismanButton(x + 2, top + i * (BUTTON_H + gap), columnW - 4, BUTTON_H,
					() -> Component.translatable("kultivator.discipleui.mode." + mode));
			button.stampedWhen(() -> view().modeIndex() == index);
			button.onPress(() -> {
				if (view().modeIndex() != index) {
					screen.send(Actions.DISCIPLE_MODE, mode);
				}
			});
			button.tooltip(() -> List.of(Component.translatable("kultivator.discipleui.mode." + mode),
					Component.translatable("kultivator.discipleui.mode." + mode + ".desc")));
			buttons.add(button);
			add(button);
		}
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		heading(g, Component.translatable("kultivator.discipleui.orders.heading"), x + 2.0F, y, columnW - 4.0F, 0.8F);
		int shown = view().modeIndex();
		for (int i = 0; i < buttons.size(); i++) {
			if (buttons.get(i).contains(mouseX, mouseY)) {
				shown = i;
			}
		}
		String mode = DiscipleView.MODES[shown];
		int x0 = x + columnW + 4;
		int x1 = x + w;
		int y0 = y;
		int y1 = y + h;
		panel(g, x0, y0, x1, y1);
		float room = x1 - x0 - 10.0F;
		float cx = (x0 + x1) / 2.0F;
		float picture = Math.max(18.0F, Math.min(40.0F, (y1 - y0) * 0.25F));
		drawPicture(g, shown, cx, y0 + 6.0F + picture / 2.0F, picture);
		float ty = y0 + 10.0F + picture;
		Component name = Brush.bold(Component.translatable("kultivator.discipleui.mode." + mode));
		Brush.fitCentered(g, font, name, cx, ty, room, Palette.SEAL_DARK, 0.95F, 0.6F, false);
		ty += 11.0F;
		Brush.rect(g, x0 + 8.0F, ty, x1 - 8.0F, ty + 0.6F, Palette.argb(0x90, Palette.GOLD));
		ty += 4.0F;
		// The active order along the bottom, the command hint above it when there is room, the meaning in between.
		float bottomLine = y1 - 11.0F;
		Component current = Component.translatable("kultivator.discipleui.orders.current",
				Component.translatable("kultivator.discipleui.mode." + DiscipleView.MODES[view().modeIndex()]));
		Brush.fit(g, font, current, x0 + 5.0F, bottomLine, room, Palette.JADE_DARK, 0.7F, 0.45F, false);
		Component description = Component.translatable("kultivator.discipleui.mode." + mode + ".desc");
		Component hint = Component.translatable("kultivator.discipleui.orders.hint");
		float descH = Brush.wrapHeight(font, description, room, 0.7F);
		float hintH = Brush.wrapHeight(font, hint, room, 0.6F);
		float hintTop = bottomLine - 3.0F - hintH;
		if (hintTop > ty + descH + 3.0F) {
			Brush.wrap(g, font, hint, x0 + 5.0F, hintTop, room, Palette.INK_FAINT, 0.6F, false);
		}
		g.enableScissor(x0 + 1, Math.round(ty), x1 - 1, Math.round(bottomLine - 2.0F));
		Brush.wrap(g, font, description, x0 + 5.0F, ty, room, Palette.INK, 0.7F, false);
		g.disableScissor();
	}

	/**
	 * Small ink pictures: footprints (follow), a banner on a post (stay), a seated figure (meditate), crossed blades
	 * (assist), the hall's roof (guard the hall).
	 */
	private static void drawPicture(GuiGraphicsExtractor g, int mode, float cx, float cy, float size) {
		float s = size / 2.0F;
		int ink = Palette.INK_SOFT;
		Brush.ring(g, cx, cy, s, s + 0.8F, Palette.argb(0x90, Palette.GOLD));
		switch (mode) {
			case 0 -> {
				for (int i = 0; i < 3; i++) {
					float fx = cx - s * 0.45F + i * s * 0.45F;
					float fy = cy + (i % 2 == 0 ? s * 0.25F : -s * 0.15F);
					Brush.disc(g, fx, fy, s * 0.13F, ink);
					Brush.disc(g, fx + s * 0.05F, fy - s * 0.22F, s * 0.07F, ink);
				}
			}
			case 1 -> {
				Brush.line(g, cx - s * 0.2F, cy + s * 0.6F, cx - s * 0.2F, cy - s * 0.6F, 1.2F, ink);
				Brush.rect(g, cx - s * 0.2F, cy - s * 0.6F, cx + s * 0.45F, cy - s * 0.15F, Palette.SEAL);
				Brush.line(g, cx - s * 0.5F, cy + s * 0.6F, cx + s * 0.1F, cy + s * 0.6F, 1.2F, ink);
			}
			case 2 -> {
				Brush.disc(g, cx, cy - s * 0.42F, s * 0.17F, ink);
				Brush.line(g, cx, cy - s * 0.25F, cx, cy + s * 0.2F, 1.4F, ink);
				Brush.line(g, cx - s * 0.55F, cy + s * 0.42F, cx + s * 0.55F, cy + s * 0.42F, 1.4F, ink);
				Brush.line(g, cx, cy - s * 0.1F, cx - s * 0.4F, cy + s * 0.3F, 1.0F, ink);
				Brush.line(g, cx, cy - s * 0.1F, cx + s * 0.4F, cy + s * 0.3F, 1.0F, ink);
				Brush.ring(g, cx, cy - s * 0.42F, s * 0.28F, s * 0.33F, Palette.argb(0xA0, Palette.QI));
			}
			case 3 -> {
				Brush.line(g, cx - s * 0.55F, cy + s * 0.55F, cx + s * 0.55F, cy - s * 0.55F, 1.4F, ink);
				Brush.line(g, cx + s * 0.55F, cy + s * 0.55F, cx - s * 0.55F, cy - s * 0.55F, 1.4F, ink);
				Brush.line(g, cx - s * 0.5F, cy + s * 0.25F, cx - s * 0.25F, cy + s * 0.5F, 1.2F, Palette.GOLD_DARK);
				Brush.line(g, cx + s * 0.5F, cy + s * 0.25F, cx + s * 0.25F, cy + s * 0.5F, 1.2F, Palette.GOLD_DARK);
			}
			// Guard the hall (DESIGN 20.2.5): the hall's roof.
			default -> Glyphs.tab(g, Glyphs.HALL, cx, cy, s * 1.3F, ink);
		}
	}
}
