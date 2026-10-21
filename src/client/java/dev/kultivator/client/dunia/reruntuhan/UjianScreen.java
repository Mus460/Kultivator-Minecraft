package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.reruntuhan.RNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Ujian Hati, the trial of the heart on the seventh floor of a band of the Menara Seratus Lantai (DESIGN 34.5.7,
 * {@code op: "riddle"}): a jade scroll with one Dao riddle, its source, and three answers on talisman tablets in the order
 * the server shuffled them; a click (or the keys 1-3) answers. Two right answers pass, two wrong ones fail the floor; a
 * wrong answer costs the formation backlash. Esc hides the scroll; it comes back by itself while the question waits
 * ({@link #pending}).
 */
public class UjianScreen extends Screen {

	/** The question that waits for an answer (null when none); kept when the scroll is hidden. */
	static @Nullable CompoundTag pending;
	static long hiddenAt;

	private final int question;
	private final int[] order;
	private final int number;
	private final int right;
	private final long openedAt = Util.getMillis();
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private final int[] optY0 = new int[3];
	private final int[] optY1 = new int[3];
	private boolean answered;

	public UjianScreen(CompoundTag tag) {
		super(Component.translatable("kultivator.reruntuhan.ujian.ui.title"));
		this.question = Mth.clamp(tag.getIntOr("q", 0), 0, 11);
		int[] opts = tag.getIntArray("opts").orElse(new int[]{0, 1, 2});
		this.order = opts.length == 3 ? opts : new int[]{0, 1, 2};
		this.number = Mth.clamp(tag.getIntOr("n", 1), 1, 3);
		this.right = Mth.clamp(tag.getIntOr("right", 0), 0, 3);
	}

	@Override
	protected void init() {
		int w = Math.min(340, width - 16);
		int h = Math.min(240, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		int bottom = y1 - 14;
		int optH = 26;
		for (int i = 2; i >= 0; i--) {
			optY1[i] = bottom - (2 - i) * (optH + 5);
			optY0[i] = optY1[i] - optH;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xB0081410, 0xD0020806);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float open = Math.min(1.0F, (now - openedAt) / 220.0F);
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x60000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		// the scroll's rods
		g.fillGradient(x0 - 4, y0 - 3, x1 + 4, y0 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		g.fillGradient(x0 - 4, y1 - 3, x1 + 4, y1 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		Brush.frame(g, x0, y0, x1, y1, Palette.JADE_DARK);
		float cx = (x0 + x1) / 2.0F;
		float room = x1 - x0 - 28.0F;
		Brush.fitCentered(g, font, Brush.bold(Component.translatable("kultivator.reruntuhan.ujian.ui.title_n", number)), cx, y0 + 9.0F, room, Palette.INK,
				1.1F, 0.6F, false);
		// three beads: right answers so far
		for (int i = 0; i < 3; i++) {
			float bx = x1 - 22.0F - i * 9.0F;
			Brush.disc(g, bx, y0 + 13.0F, 3.0F, i < right ? Palette.JADE : Palette.argb(0x60, Palette.INK_FAINT));
		}
		Brush.rect(g, x0 + 18.0F, y0 + 22.0F, x1 - 18.0F, y0 + 23.0F, Palette.argb(0x90, Palette.JADE_DARK));
		float y = y0 + 30.0F;
		String key = "kultivator.reruntuhan.menara.ujian.q" + question;
		y += Brush.wrap(g, font, Component.translatable(key), x0 + 18.0F, y, room - 4.0F, Palette.INK, 0.85F, false) + 4.0F;
		Brush.wrap(g, font, Component.translatable(key + ".src").withStyle(ChatFormatting.ITALIC), x0 + 18.0F, y, room - 4.0F, Palette.INK_SOFT, 0.62F,
				false);
		for (int i = 0; i < 3; i++) {
			boolean over = !answered && mouseX >= x0 + 18 && mouseX < x1 - 18 && mouseY >= optY0[i] && mouseY < optY1[i];
			int top = over ? 0xFFF7DFA0 : Palette.TALISMAN;
			int bx0 = x0 + 18;
			int bx1 = Math.round(x0 + 18 + (x1 - x0 - 36) * open);
			g.fillGradient(bx0, optY0[i], bx1, optY1[i], top, Palette.TALISMAN_LOW);
			Brush.frame(g, bx0, optY0[i], bx1, optY1[i], over ? Palette.SEAL : Palette.TALISMAN_EDGE);
			Brush.disc(g, bx0 + 11.0F, (optY0[i] + optY1[i]) / 2.0F, 6.0F, Palette.SEAL);
			Brush.fitCentered(g, font, Component.literal(String.valueOf(i + 1)), bx0 + 11.0F, (optY0[i] + optY1[i]) / 2.0F - 3.5F, 10.0F, 0xFFF3D9C8, 0.8F,
					0.6F, false);
			Brush.wrap(g, font, Component.translatable(key + ".a" + order[i]), bx0 + 22.0F, optY0[i] + 4.0F, bx1 - bx0 - 28.0F, Palette.INK, 0.72F, false);
		}
	}

	private void answer(int shown) {
		if (answered || shown < 0 || shown > 2) {
			return;
		}
		answered = true;
		pending = null;
		ClientPlayNetworking.send(new RNet.Action("jawab", shown, 0));
		UiSound.press();
		minecraft.gui.setScreen(null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (int i = 0; i < 3; i++) {
				if (event.x() >= x0 + 18 && event.x() < x1 - 18 && event.y() >= optY0[i] && event.y() < optY1[i]) {
					answer(i);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_3) {
			answer(key - InputConstants.KEY_1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		hiddenAt = Util.getMillis();
		minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
