package dev.kultivator.client.tempa;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.JadeTablet;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.ForgeMenu;
import dev.kultivator.tempa.Forging;
import dev.kultivator.tempa.Heat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Earthfire Forge screen (DESIGN 25.5.10), a jade scroll of 270 x 214 with four vertical tablets: Tempa Baru · Tempa
 * Ulang · Roh · Cincin. The material slots sit on the left, an anvil with the heat gauge and the progress ring in the
 * middle, and the preview card (result grade and what limits it, sub-quality odds, the chance and its parts, qi, time) on
 * the right; the card's lines come from the server's plan. Buttons use vanilla's menu-button packet.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {

	private static final int TABLET_W = 20;
	private static final int CARD_X = 150;
	private static final int CARD_Y = 20;
	private static final int CARD_W = 112;
	private static final int CARD_H = 100;
	private static final int ANVIL_X = 108;
	private static final int ANVIL_Y = 58;
	private static final int[] TAB_ICONS = {Glyphs.BUILD, Glyphs.SEAL, Glyphs.TALENT, Glyphs.RING};
	private static final String[] TAB_KEYS = {"new", "refine", "spirit", "ring"};

	private record Entry(Widget widget, BooleanSupplier visible) {
	}

	private final List<JadeTablet> tablets = new ArrayList<>();
	private final List<Entry> entries = new ArrayList<>();
	private @Nullable Widget pressed;
	private int localTab;
	private long lastFrame;

	public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ForgeMenu.WIDTH, ForgeMenu.HEIGHT);
		this.localTab = menu.tab();
	}

	@Override
	protected void init() {
		super.init();
		this.leftPos = (this.width - this.imageWidth + TABLET_W) / 2;
		this.topPos = (this.height - this.imageHeight) / 2;
		lastFrame = Util.getMillis();
		tablets.clear();
		entries.clear();
		for (int i = 0; i < 4; i++) {
			int index = i;
			JadeTablet tablet = new JadeTablet(leftPos - TABLET_W + 1, topPos + 14 + i * 46, TABLET_W, 42, TAB_ICONS[i],
					() -> Component.translatable("kultivator.tempa.tab." + TAB_KEYS[index] + ".short"), () -> tab() == index);
			tablet.onPress(() -> {
				localTab = index;
				click(index);
			});
			tablet.tooltip(() -> List.of(Component.translatable("kultivator.tempa.tab." + TAB_KEYS[index])));
			tablets.add(tablet);
		}
		int x = leftPos;
		int y = topPos;
		// The start talisman (every tab but Roh) and the cancel talisman while a job runs.
		add(new TalismanButton(x + 74, y + 104, 68, 14, () -> Component.translatable(startKey())).urgentWhen(() -> forge().getBooleanOr("ok", false))
				.enabledWhen(() -> forge().getBooleanOr("ok", false)).onPress(() -> click(ForgeMenu.BTN_START)),
				() -> tab() != ForgeMenu.TAB_SPIRIT && !menu.running());
		add(new TalismanButton(x + 74, y + 104, 68, 14, () -> Component.translatable("kultivator.tempa.btn.cancel"))
				.onPress(() -> click(ForgeMenu.BTN_CANCEL)), menu::running);
		// Tempa Baru: the assistant (or the Kuali's form when a Kuali lies in the slots).
		add(new TalismanButton(x + 8, y + 92, 60, 12, () -> Component.translatable("kultivator.tempa.btn.assist"))
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.btn.assist.tip"))).onPress(() -> click(ForgeMenu.BTN_ASSIST)),
				() -> (tab() == ForgeMenu.TAB_NEW || tab() == ForgeMenu.TAB_REFINE) && !kuali());
		add(new TalismanButton(x + 8, y + 106, 60, 12, () -> Component.translatable("kultivator.tempa.btn.release"))
				.onPress(() -> click(ForgeMenu.BTN_RELEASE)), () -> tab() == ForgeMenu.TAB_NEW && !kuali());
		for (int i = 0; i < 4; i++) {
			int form = i;
			add(new JadeCoin(x + 8 + (i % 2) * 31, y + 92 + (i / 2) * 14, 30, 12,
					() -> Component.translatable("kultivator.tempa.form." + ArtifactItem.FORMS.get(form)), () -> forge().getIntOr("form", 0) == form)
					.onPress(() -> click(ForgeMenu.BTN_KUALI_FORM + form)), () -> tab() == ForgeMenu.TAB_NEW && kuali());
		}
		// Tempa Ulang: refine the natal treasure in place.
		add(new JadeCoin(x + 8, y + 106, 62, 12, () -> Component.translatable("kultivator.tempa.btn.natal"), menu::natalTarget)
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.btn.natal.tip"))).onPress(() -> click(ForgeMenu.BTN_NATAL)),
				() -> tab() == ForgeMenu.TAB_REFINE);
		// Roh: the four spirit actions.
		String[] spirit = {"feed", "seed", "soul", "purify"};
		int[] spiritIds = {ForgeMenu.BTN_FEED, ForgeMenu.BTN_SEED, ForgeMenu.BTN_SOUL, ForgeMenu.BTN_PURIFY};
		for (int i = 0; i < 4; i++) {
			int index = i;
			add(new TalismanButton(x + (i < 2 ? 8 : 74), y + 92 + (i % 2) * 15, i < 2 ? 62 : 68, 13,
					() -> Component.translatable("kultivator.tempa.btn." + spirit[index]))
					.tooltip(() -> List.of(Component.translatable("kultivator.tempa.btn." + spirit[index] + ".tip")))
					.enabledWhen(() -> forge().getBooleanOr("natal_bound", false)).onPress(() -> click(spiritIds[index])),
					() -> tab() == ForgeMenu.TAB_SPIRIT);
		}
		// Cincin: Runtuhkan Ruang.
		add(new TalismanButton(x + 8, y + 100, 62, 14, () -> Component.translatable("kultivator.tempa.btn.collapse"))
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.btn.collapse.tip", Forging.COLLAPSE_STONES)))
				.onPress(() -> click(ForgeMenu.BTN_COLLAPSE)), () -> tab() == ForgeMenu.TAB_RING && !menu.running());
	}

	private void add(Widget widget, BooleanSupplier visible) {
		entries.add(new Entry(widget, visible));
	}

	private int tab() {
		return menu.running() ? menu.tab() : localTab;
	}

	private static CompoundTag forge() {
		return TempaClient.forge();
	}

	private boolean kuali() {
		return forge().getIntOr("kind", 0) == Forging.KIND_KUALI;
	}

	private String startKey() {
		return switch (tab()) {
			case ForgeMenu.TAB_REFINE -> "kultivator.tempa.btn.refine";
			case ForgeMenu.TAB_RING -> "kultivator.tempa.btn.ring";
			default -> kuali() ? "kultivator.tempa.btn.kuali" : "kultivator.tempa.btn.forge";
		};
	}

	private void click(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	// ================================================================== drawing

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
		for (JadeTablet tablet : tablets) {
			tablet.render(g, font, mouseX, mouseY, seconds);
		}
		ScrollFrame.paper(g, x0, x1, y0, y1, y0, y1);
		ScrollFrame.rod(g, x0, x1, y0);
		ScrollFrame.rod(g, x0, x1, y1);
		Component heading = Brush.bold(Component.translatable("kultivator.tempa.forge.title", title,
				Component.translatable("kultivator.tempa.tab." + TAB_KEYS[tab()])));
		Brush.fitCentered(g, font, heading, (x0 + x1) / 2.0F, y0 + 8.0F, imageWidth - 30.0F, Palette.INK, 0.9F, 0.55F, false);
		Brush.text(g, font, Component.translatable("kultivator.tempa.forge.materials"), x0 + ForgeMenu.GRID_X, y0 + ForgeMenu.GRID_Y - 9.0F,
				Palette.SEAL_DARK, 0.65F, false);
		for (Slot slot : menu.slots) {
			int sx = x0 + slot.x - 1;
			int sy = y0 + slot.y - 1;
			g.fill(sx, sy, sx + 18, sy + 18, Palette.argb(0x60, Palette.PAPER_DEEP));
			Brush.frame(g, sx, sy, sx + 18, sy + 18, Palette.argb(0xC0, Palette.GOLD_DARK));
			g.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0x30000000);
			if (slot.index < ForgeMenu.INPUT && menu.running()) {
				g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x30B3281D);
			}
		}
		drawAnvil(g, now);
		drawCard(g);
		for (Entry entry : entries) {
			if (entry.visible().getAsBoolean()) {
				entry.widget().render(g, font, mouseX, mouseY, seconds);
			}
		}
	}

	/** The anvil over its fire, the heat gauge on its right and the progress ring around it. */
	private void drawAnvil(GuiGraphicsExtractor g, long now) {
		float cx = leftPos + ANVIL_X;
		float cy = topPos + ANVIL_Y;
		int heat = menu.heat();
		int fire = Palette.opaque(Heat.COLORS[heat]);
		// Progress ring.
		Brush.ring(g, cx, cy, 24.0F, 26.5F, Palette.argb(0x50, Palette.INK_FAINT));
		if (menu.running()) {
			int color = menu.paused() ? Palette.LOCKED : fire;
			Brush.arc(g, cx, cy, 24.0F, 26.5F, 0.0F, Math.max(0.01F, menu.progress()), color);
		}
		// Flames under the anvil.
		float t = (now % 100000L) / 1000.0F;
		for (int i = 0; i < 5; i++) {
			float fx = cx - 10.0F + i * 5.0F;
			float h = 5.0F + 3.0F * (float) Math.sin(t * 6.0F + i * 1.7F);
			Brush.rect(g, fx - 1.6F, cy + 16.0F - h, fx + 1.6F, cy + 16.0F, Palette.argb(0xC0, Palette.mix(fire, 0xFFFFF0B0, 0.3F)));
		}
		// The anvil: foot, waist, face and horn.
		int iron = 0xFF2B2F36;
		int ironLight = 0xFF4A505A;
		Brush.rect(g, cx - 11.0F, cy + 9.0F, cx + 11.0F, cy + 12.0F, iron);
		Brush.rect(g, cx - 5.0F, cy + 2.0F, cx + 5.0F, cy + 9.0F, iron);
		Brush.rect(g, cx - 13.0F, cy - 4.0F, cx + 9.0F, cy + 2.0F, iron);
		Brush.rect(g, cx - 13.0F, cy - 4.0F, cx + 9.0F, cy - 3.0F, ironLight);
		Brush.rect(g, cx + 9.0F, cy - 3.0F, cx + 15.0F, cy - 1.0F, iron);
		Brush.rect(g, cx + 15.0F, cy - 2.5F, cx + 17.0F, cy - 1.5F, iron);
		if (menu.running() && !menu.paused()) {
			Brush.halo(g, cx - 2.0F, cy - 4.0F, 4.0F, 6.0F, Heat.COLORS[heat], 0.45F);
		}
		// Heat gauge.
		int gx = leftPos + 138;
		int gTop = topPos + 26;
		int gBottom = topPos + 92;
		g.fill(gx, gTop, gx + 5, gBottom, Palette.argb(0x60, Palette.INK));
		int filled = Math.round((gBottom - gTop - 2) * (heat + 1) / 6.0F);
		g.fillGradient(gx + 1, gBottom - 1 - filled, gx + 4, gBottom - 1, fire, Palette.mix(fire, 0xFF401000, 0.5F));
		Brush.frame(g, gx, gTop, gx + 5, gBottom, Palette.GOLD_DARK);
		Component heatLine = Component.translatable("kultivator.tempa.forge.heat", Heat.name(heat), ArtifactItem.gradeName(heat));
		if (tab() != ForgeMenu.TAB_SPIRIT) {
			Brush.fitCentered(g, font, heatLine, cx, topPos + 92.0F, 66.0F, Palette.INK, 0.6F, 0.42F, false);
		}
		if (menu.running()) {
			Component state = Component.translatable(menu.paused() ? "kultivator.tempa.forge.paused" : "kultivator.tempa.forge.working",
					Math.round(menu.progress() * 100.0F));
			Brush.fitCentered(g, font, state, cx, topPos + 20.0F, 66.0F, menu.paused() ? Palette.DANGER : Palette.JADE_DARK, 0.6F, 0.42F, false);
		}
	}

	/** The preview card: the plan's title, lines and problem, as the server computed them. */
	private void drawCard(GuiGraphicsExtractor g) {
		int x0 = leftPos + CARD_X;
		int y0 = topPos + CARD_Y;
		int x1 = x0 + CARD_W;
		int y1 = y0 + CARD_H;
		g.fill(x0, y0, x1, y1, Palette.argb(0x40, Palette.PAPER_DEEP));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xC0, Palette.GOLD));
		CompoundTag card = forge();
		if (card.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.tempa.preview.wait"), x0 + 4.0F, y0 + 4.0F, CARD_W - 8.0F, Palette.INK_SOFT, 0.6F, false);
			return;
		}
		Component cardTitle = Brush.bold(Forging.component(card.getCompoundOrEmpty("title")));
		Brush.rect(g, x0 + 3.0F, y0 + 4.0F, x0 + 6.0F, y0 + 7.0F, Palette.SEAL);
		Brush.fit(g, font, cardTitle, x0 + 8.0F, y0 + 3.0F, CARD_W - 12.0F, Palette.SEAL_DARK, 0.7F, 0.45F, false);
		float y = y0 + 12.0F;
		float scale = 0.55F;
		ListTag lines = card.getListOrEmpty("lines");
		for (Tag entry : lines) {
			if (!(entry instanceof CompoundTag line)) {
				continue;
			}
			if (y > y1 - 14.0F) {
				break;
			}
			int color = Palette.opaque(line.getIntOr("c", 0x1B2521));
			y += Brush.wrap(g, font, Forging.component(line), x0 + 4.0F, y, CARD_W - 8.0F, color, scale, false) + 1.0F;
		}
		CompoundTag problem = card.getCompoundOrEmpty("problem");
		if (!problem.isEmpty()) {
			float h = Brush.wrapHeight(font, Forging.component(problem), CARD_W - 8.0F, scale);
			Brush.wrap(g, font, Forging.component(problem), x0 + 4.0F, y1 - 3.0F - h, CARD_W - 8.0F, Palette.DANGER, scale, false);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		Brush.text(g, font, playerInventoryTitle, ForgeMenu.INV_X, ForgeMenu.INV_Y - 9.0F, Palette.INK_SOFT, 0.65F, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		if (menu.getCarried().isEmpty() && (hoveredSlot == null || !hoveredSlot.hasItem())) {
			List<Component> tip = null;
			for (JadeTablet tablet : tablets) {
				if (tablet.contains(mouseX, mouseY)) {
					tip = tablet.tooltipLines();
				}
			}
			for (Entry entry : entries) {
				if (entry.visible().getAsBoolean() && entry.widget().contains(mouseX, mouseY)) {
					tip = entry.widget().tooltipLines();
				}
			}
			if (tip != null && !tip.isEmpty()) {
				g.nextStratum();
				SlipTooltip.draw(g, font, tip, mouseX, mouseY, width, height);
			}
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (JadeTablet tablet : tablets) {
				if (tablet.contains(event.x(), event.y())) {
					if (!menu.running()) {
						tablet.press();
						pressed = tablet;
					}
					return true;
				}
			}
			for (int i = entries.size() - 1; i >= 0; i--) {
				Entry entry = entries.get(i);
				if (entry.visible().getAsBoolean() && entry.widget().contains(event.x(), event.y())) {
					entry.widget().press();
					pressed = entry.widget();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressed != null) {
			Widget widget = pressed;
			pressed = null;
			if (widget instanceof JadeTablet) {
				UiSound.tab();
			}
			widget.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}
}
