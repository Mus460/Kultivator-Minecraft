package dev.kultivator.client.tempa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.tempa.RingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The open storage ring (DESIGN 25.7.3): a jade scroll with the ring's rows (up to 6 × 9) over the inventory, fitting
 * 320 × 240. The ring's own slot carries a red seal (it is locked while open). Its owner can release the imprint with
 * "Hapus Jejak" (a second click confirms).
 */
public class RingScreen extends AbstractContainerScreen<RingMenu> {

	private final @Nullable TalismanButton erase;
	private boolean pressedErase;
	private long askedAt;
	private long lastFrame;

	public RingScreen(RingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, RingMenu.WIDTH, RingMenu.height(menu.rows));
		this.erase = menu.tier >= 1 ? new TalismanButton(0, 0, 64, 12, () -> Component.translatable(asking()
				? "kultivator.tempa.ring.erase_confirm" : "kultivator.tempa.ring.erase")) : null;
		if (erase != null) {
			erase.urgentWhen(this::asking);
			erase.tooltip(() -> List.of(Component.translatable("kultivator.tempa.ring.erase.tip")));
		}
	}

	private boolean asking() {
		return Util.getMillis() - askedAt < 3000L;
	}

	@Override
	protected void init() {
		super.init();
		lastFrame = Util.getMillis();
		if (erase != null) {
			erase.x = leftPos + imageWidth - 72;
			erase.y = topPos + 5;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		int x0 = leftPos;
		int y0 = topPos;
		int x1 = leftPos + imageWidth;
		int y1 = topPos + imageHeight;
		ScrollFrame.paper(g, x0, x1, y0, y1, y0, y1);
		ScrollFrame.rod(g, x0, x1, y0);
		ScrollFrame.rod(g, x0, x1, y1);
		int ringRows = menu.rows * 9;
		for (Slot slot : menu.slots) {
			int sx = x0 + slot.x - 1;
			int sy = y0 + slot.y - 1;
			boolean ring = slot.index < ringRows;
			g.fill(sx, sy, sx + 18, sy + 18, Palette.argb(ring ? 0x70 : 0x50, ring ? 0xB6E3CB : Palette.PAPER_DEEP));
			Brush.frame(g, sx, sy, sx + 18, sy + 18, Palette.argb(0xC0, ring ? Palette.JADE_DARK : Palette.GOLD_DARK));
			if (menu.isRingSlot(slot)) {
				g.fill(sx + 1, sy + 1, sx + 17, sy + 17, Palette.argb(0x70, Palette.SEAL));
			}
		}
		if (erase != null) {
			erase.render(g, font, mouseX, mouseY, seconds);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		Brush.fit(g, font, Brush.bold(title), RingMenu.LEFT, 8.0F, imageWidth - (erase != null ? 96 : 30), Palette.INK, 0.85F, 0.5F, false);
		Brush.text(g, font, playerInventoryTitle, RingMenu.LEFT, RingMenu.TOP + menu.rows * 18 + 3.0F, Palette.INK_SOFT, 0.65F, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (erase != null && erase.contains(mouseX, mouseY) && menu.getCarried().isEmpty()) {
			g.nextStratum();
			SlipTooltip.draw(g, font, erase.tooltipLines(), mouseX, mouseY, width, height);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (erase != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT && erase.contains(event.x(), event.y())) {
			erase.press();
			pressedErase = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedErase && erase != null) {
			pressedErase = false;
			if (erase.release(event.x(), event.y())) {
				if (asking()) {
					askedAt = 0L;
					if (minecraft != null && minecraft.gameMode != null) {
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RingMenu.BTN_ERASE);
					}
				} else {
					askedAt = Util.getMillis();
				}
			}
			return true;
		}
		return super.mouseReleased(event);
	}
}
