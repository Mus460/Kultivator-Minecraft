package dev.kultivator.client.alam;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gulungan Harta, the treasure scroll of a cleared realm (DESIGN 25.3.4 step 6, 25.10): the items the run gave (already in
 * the inventory; what did not fit waits at the return point or in the Lumbung) and, where the realm says so, three seal
 * cards to choose one from. A choice not made within a minute takes the first card. Jade scroll look, fits 320 × 240.
 */
public class TreasureScreen extends Screen {

	private static final int MAX_W = 280;
	private static final int MAX_H = 210;
	private static final int CELL = 20;
	private static final int CARD = 44;
	private static final long UNROLL_MILLIS = 250L;

	private final String realm;
	private final int layer;
	private final List<ItemStack> items = new ArrayList<>();
	private final List<ItemStack> choices = new ArrayList<>();
	private final Eased glow = new Eased(8.0F);
	private int chosen = -1;
	private long openedAt;
	private long lastFrame;
	private @Nullable TalismanButton close;
	private boolean closePressed;
	private @Nullable List<Component> tooltip;

	private int x0;
	private int x1;
	private int top;
	private int bottom;

	public TreasureScreen(CompoundTag treasure) {
		super(Component.translatable("kultivator.alam.treasure.title"));
		this.realm = treasure.getStringOr("realm", "");
		this.layer = treasure.getIntOr("layer", 0);
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (minecraft.level != null) {
			RegistryOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			for (Tag tag : treasure.getListOrEmpty("items")) {
				if (tag instanceof CompoundTag entry) {
					entry.read("item", ItemStack.CODEC, ops).ifPresent(items::add);
				}
			}
			for (Tag tag : treasure.getListOrEmpty("choices")) {
				if (tag instanceof CompoundTag entry) {
					entry.read("item", ItemStack.CODEC, ops).ifPresent(choices::add);
				}
			}
		}
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		int w = Math.min(MAX_W, width - 16);
		int h = Math.min(MAX_H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		top = (height - h) / 2 + 5;
		bottom = top + h - 10;
		int buttonW = 80;
		close = new TalismanButton((x0 + x1) / 2 - buttonW / 2, bottom - ScrollFrame.END - 22, buttonW, 16,
				() -> Component.translatable("kultivator.alam.treasure.close"));
	}

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		float open = unroll();
		int mid = (top + bottom) / 2;
		int half = Math.round((bottom - top) / 2.0F * open);
		int t = mid - half;
		int b = mid + half;
		ScrollFrame.paper(g, x0, x1, t, b, top, bottom);
		ScrollFrame.rod(g, x0, x1, t);
		ScrollFrame.rod(g, x0, x1, b);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		int inX = x0 + ScrollFrame.SIDE + 8;
		int inW = x1 - x0 - 2 * (ScrollFrame.SIDE + 8);
		float y = top + ScrollFrame.END + 6.0F;
		Component name = Component.translatable("kultivator.alam.realm." + realm).withColor(Palette.opaque(RealmScenery.color(realmOrdinal())) & 0xFFFFFF);
		Component header = layer > 0 ? Component.translatable("kultivator.alam.treasure.header_layer", name, layer)
				: Component.translatable("kultivator.alam.treasure.header", name);
		Brush.fitCentered(g, font, Brush.bold(header), (x0 + x1) / 2.0F, y, inW, Palette.INK, 1.0F, 0.6F, false);
		y += 12.0F;
		Brush.rect(g, inX, y, inX + inW, y + 0.6F, Palette.GOLD);
		y += 4.0F;
		Brush.fit(g, font, Component.translatable("kultivator.alam.treasure.given"), inX, y, inW, Palette.SEAL_DARK, 0.8F, 0.6F, false);
		y += 10.0F;
		int perRow = Math.max(1, inW / CELL);
		int gridTop = Math.round(y);
		for (int i = 0; i < items.size(); i++) {
			int cx = inX + (i % perRow) * CELL;
			int cy = gridTop + (i / perRow) * CELL;
			g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, Palette.argb(0x40, Palette.PAPER_DEEP));
			Brush.frame(g, cx, cy, cx + CELL - 2, cy + CELL - 2, Palette.argb(0x90, Palette.GOLD));
		}
		if (items.isEmpty()) {
			Brush.fit(g, font, Component.translatable("kultivator.alam.treasure.none"), inX, y, inW, Palette.INK_SOFT, 0.8F, 0.6F, false);
		}
		int rows = (items.size() + perRow - 1) / perRow;
		y = gridTop + Math.max(1, rows) * CELL + 4.0F;
		int cardsTop = 0;
		if (!choices.isEmpty()) {
			Brush.fit(g, font, Component.translatable(chosen < 0 ? "kultivator.alam.treasure.choose" : "kultivator.alam.treasure.chosen"),
					inX, y, inW, Palette.SEAL_DARK, 0.8F, 0.6F, false);
			y += 11.0F;
			cardsTop = Math.round(y);
			int gap = 8;
			int totalW = choices.size() * CARD + (choices.size() - 1) * gap;
			int startX = (x0 + x1) / 2 - totalW / 2;
			float pulse = glow.to(1.0F, seconds);
			for (int i = 0; i < choices.size(); i++) {
				int cx = startX + i * (CARD + gap);
				boolean over = mouseX >= cx && mouseY >= cardsTop && mouseX < cx + CARD && mouseY < cardsTop + CARD;
				boolean picked = chosen == i;
				int frame = picked ? Palette.GOLD : over && chosen < 0 ? Palette.GOLD_LIGHT : Palette.GOLD_DARK;
				g.fill(cx, cardsTop, cx + CARD, cardsTop + CARD, Palette.argb(picked ? 0x90 : 0x50, Palette.JADE_PALE));
				Brush.frame(g, cx, cardsTop, cx + CARD, cardsTop + CARD, frame);
				Brush.frame(g, cx + 2, cardsTop + 2, cx + CARD - 2, cardsTop + CARD - 2, Palette.argb(0x80, frame));
				if (picked) {
					Brush.ring(g, cx + CARD - 7.0F, cardsTop + 7.0F, 3.0F, 4.0F, Palette.argb(Math.round(0xD0 * pulse), Palette.SEAL));
				}
				if (chosen >= 0 && !picked) {
					g.fill(cx + 1, cardsTop + 1, cx + CARD - 1, cardsTop + CARD - 1, 0x60A0A0A0);
				}
				if (over) {
					tooltip = Screen.getTooltipFromItem(minecraft, choices.get(i));
				}
			}
		}
		if (close != null) {
			close.render(g, font, mouseX, mouseY, seconds);
		}
		g.nextStratum();
		for (int i = 0; i < items.size(); i++) {
			int cx = inX + (i % perRow) * CELL + 1;
			int cy = gridTop + (i / perRow) * CELL + 1;
			g.item(items.get(i), cx, cy);
			g.itemDecorations(font, items.get(i), cx, cy);
			if (mouseX >= cx - 1 && mouseY >= cy - 1 && mouseX < cx + CELL - 3 && mouseY < cy + CELL - 3) {
				tooltip = Screen.getTooltipFromItem(minecraft, items.get(i));
			}
		}
		if (!choices.isEmpty()) {
			int gap = 8;
			int totalW = choices.size() * CARD + (choices.size() - 1) * gap;
			int startX = (x0 + x1) / 2 - totalW / 2;
			for (int i = 0; i < choices.size(); i++) {
				int cx = startX + i * (CARD + gap) + (CARD - 16) / 2;
				int cy = cardsTop + (CARD - 16) / 2;
				g.item(choices.get(i), cx, cy);
				g.itemDecorations(font, choices.get(i), cx, cy);
			}
		}
		if (tooltip != null && !tooltip.isEmpty()) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	private int realmOrdinal() {
		return switch (realm) {
			case "blood_fire_trial" -> 1;
			case "heavenvoid_hall" -> 2;
			case "ancient_god_land" -> 3;
			default -> 4;
		};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (close != null && close.contains(event.x(), event.y())) {
				close.press();
				closePressed = true;
				return true;
			}
			if (chosen < 0 && !choices.isEmpty()) {
				int index = cardAt(event.x(), event.y());
				if (index >= 0) {
					chosen = index;
					UiSound.press();
					Keys.send(new Payloads.Action(Actions.ALAM, 0, "pick:" + index));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (closePressed && close != null) {
			closePressed = false;
			if (close.release(event.x(), event.y())) {
				onClose();
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	/** The choice card under the mouse, or -1 (the same layout as the drawing). */
	private int cardAt(double mouseX, double mouseY) {
		int inX = x0 + ScrollFrame.SIDE + 8;
		int inW = x1 - x0 - 2 * (ScrollFrame.SIDE + 8);
		int perRow = Math.max(1, inW / CELL);
		int rows = (items.size() + perRow - 1) / perRow;
		float y = top + ScrollFrame.END + 6.0F + 12.0F + 4.0F + 10.0F;
		int gridTop = Math.round(y);
		y = gridTop + Math.max(1, rows) * CELL + 4.0F + 11.0F;
		int cardsTop = Math.round(y);
		int gap = 8;
		int totalW = choices.size() * CARD + (choices.size() - 1) * gap;
		int startX = (x0 + x1) / 2 - totalW / 2;
		for (int i = 0; i < choices.size(); i++) {
			int cx = startX + i * (CARD + gap);
			if (mouseX >= cx && mouseY >= cardsTop && mouseX < cx + CARD && mouseY < cardsTop + CARD) {
				return i;
			}
		}
		return -1;
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
