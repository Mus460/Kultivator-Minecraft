package dev.kultivator.client.tempa;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Fokus;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.tempa.RingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The open storage ring (DESIGN 25.7.3, 43.8.5), one of the two container screens that stay outside the Gulungan because
 * of their slots, drawn with the same kit: a jade scroll with the ring's rows (up to 6 × 9) over the inventory, the "?"
 * seal (topic "cincin") in the header. The ring's own slot carries a red seal (it is locked while open). Its owner can
 * release the imprint with "Hapus Jejak" (a medium {@link SegelKonfirmasi}: hold 1.5 s). Opened over the Gulungan (Diri ›
 * Pusaka, the ring key, a card), Esc comes back to the page it was opened from (R2, {@link HubNav#kembaliSetelah}).
 */
public class RingScreen extends AbstractContainerScreen<RingMenu> {

	private static final String K = "kultivator.gui43.tempa.";
	private static final int ERASE_W = 80;

	private final Fokus fokus = new Fokus(this::pesan);
	private final TempaGeser geser = new TempaGeser(this::tataUlang);
	private final KeyHints.HelpSeal seal = new KeyHints.HelpSeal(0.0F, 0.0F, 13, () -> "cincin");
	private @Nullable Lukis frame;
	private @Nullable Component pesan;
	private long pesanSampai;

	public RingScreen(RingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, RingMenu.WIDTH, RingMenu.height(menu.rows));
		// MenuScreens builds the screen before it replaces the current one, so the Gulungan is still shown here.
		if (Minecraft.getInstance().gui.screen() instanceof CultivationScreen) {
			HubNav.kembaliSetelah(RingScreen.class);
		}
	}

	@Override
	protected void init() {
		super.init();
		tataUlang();
	}

	/** The panel's place: centred, or (a window shorter than the panel: 5 or more rows at GUI scale 4 on 1366x768) panned by {@link TempaGeser}. */
	private void tataUlang() {
		this.leftPos = (this.width - this.imageWidth) / 2;
		this.topPos = geser.atas(this.height, this.imageHeight);
		seal.moveTo(leftPos + imageWidth - 11.0F, topPos + 12.0F);
	}

	/** Replaced by another screen without closing (the "?" seal opens the Gulungan): the server closes the menu too. */
	@Override
	public void removed() {
		super.removed();
		TempaClient.tutupDiServer(menu);
	}

	/** A refusal: shown in place of the title for 3 s. */
	private void pesan(Component alasan) {
		pesan = alasan;
		pesanSampai = Util.getMillis() + 3000L;
	}

	/** The row between the ring's slots and the inventory (the inventory label on the left, Hapus Jejak on the right). */
	private int labelRow() {
		return RingMenu.TOP + menu.rows * 18;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		SlipTooltip.mulaiBingkai();
		int x0 = leftPos;
		int y0 = topPos;
		int x1 = leftPos + imageWidth;
		int y1 = topPos + imageHeight;
		// the box is the visible part of the panel: the confirm centres in it, the pan seals sit at its edges
		Lukis l = fokus.mulai(g, font, mouseX, mouseY, 1.0F, Palette.Gaya.KERTAS, x0, Math.max(0, y0), x1, Math.min(height, y1));
		frame = l;
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
		float titleW = imageWidth - RingMenu.LEFT - 24.0F;
		if (pesan != null && Util.getMillis() < pesanSampai) {
			Brush.fit(g, font, pesan, x0 + RingMenu.LEFT, y0 + 7.0F, titleW, Palette.DANGER, 1.0F, 1.0F, false);
		} else {
			Brush.fit(g, font, Brush.bold(title), x0 + RingMenu.LEFT, y0 + 7.0F, titleW, Palette.INK, 1.0F, 1.0F, false);
		}
		l.widget("bantuan", seal);
		geser.gambar(l, Math.max(2, leftPos - 20));
		if (menu.tier >= 1) {
			Component label = Component.translatable("kultivator.tempa.ring.erase");
			int ex = x1 - RingMenu.LEFT - ERASE_W;
			if (TalismanButton.gambar(l, "hapus", ex, y0 + labelRow(), ERASE_W, 12, label, true, null,
					() -> fokus.konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "hapus_jejak.tanya", title), this::erase))) {
				l.tip(List.of(label, Component.translatable("kultivator.tempa.ring.erase.tip")));
			}
		}
	}

	private void erase() {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RingMenu.BTN_ERASE);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		Brush.text(g, font, playerInventoryTitle, RingMenu.LEFT, labelRow() + 3.0F, Palette.INK_SOFT, 1.0F, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Lukis l = frame;
		frame = null;
		if (l == null) {
			return;
		}
		// The confirm lies over the slots and items; the kit's tooltip only where no item tooltip shows.
		g.nextStratum();
		fokus.selesai(l, mouseX, mouseY);
		geser.ikuti(fokus.kunci(), fokus.zonaFokus(), height);
		if (fokus.segel().terbuka() || menu.getCarried().isEmpty() && (hoveredSlot == null || !hoveredSlot.hasItem())) {
			g.nextStratum();
			SlipTooltip.gambarAntrean(g, font, mouseX, mouseY, width, height);
		} else {
			SlipTooltip.mulaiBingkai();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (fokus.klik(event.x(), event.y(), event.button())) {
			return true;
		}
		fokus.set(null);
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (fokus.lepas(event.x(), event.y())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The confirm takes every key (Esc cancels it); Tab / Enter reach Hapus Jejak and the seal.
		if (fokus.tombol(event)) {
			return true;
		}
		if (geser.tombol(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		// a hovered bundle takes the wheel first; what is left pans the panel
		return fokus.gulir(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY) || geser.roda(scrollY);
	}
}
