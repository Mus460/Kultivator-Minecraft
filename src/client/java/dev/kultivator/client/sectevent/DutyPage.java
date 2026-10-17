package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.event.Storehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Tugas tablet (DESIGN 20.3.9): left, the disciples with their duties and the duty talismans (Pikul Air, Kebun
 * Seratus Obat, Dapur Api, Peternakan Ayam Ekor Roh, release); right, the 27 slots of the Lumbung Sekte (click a slot to
 * take it), the herb refining talisman and what the chosen duty yields.
 */
public class DutyPage extends SectEventPage {

	private static final int BUTTON_H = 13;
	private static final Set<String> SELECTED = new HashSet<>();

	private final RosterView roster;
	private int colW;
	private int gridX;
	private int gridY;
	private int slot;
	private int infoY;

	public DutyPage(HallScreen screen) {
		super(screen);
		this.roster = new RosterView(font, 1, entry -> true, entry -> entry.getBooleanOr("near", false),
				entry -> {
					String duty = entry.getStringOr("duty", "");
					return duty.isEmpty() ? Component.translatable("kultivator.sectevent.ui.idle")
							: Component.translatable("kultivator.sectevent.duty." + duty + ".short");
				}, SELECTED);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.duty");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.duty.short");
	}

	@Override
	public int icon() {
		return Glyphs.TALENT;
	}

	@Override
	protected void build() {
		colW = Math.max(110, Math.round(w * 0.46F));
		int bottom = y + h;
		roster.layout(x + 2, y + 12, colW - 4, Math.max(30, h - 12 - 3 * (BUTTON_H + 1) - 4));
		if (!data().getBooleanOr("hall", false)) {
			return;
		}
		int half = (colW - 6) / 2;
		int rowA = bottom - 3 * (BUTTON_H + 1);
		String[] duties = Duties.DUTIES;
		for (int i = 0; i < duties.length; i++) {
			String duty = duties[i];
			int bx = x + 2 + (i % 2) * (half + 2);
			int by = rowA + (i / 2) * (BUTTON_H + 1);
			TalismanButton button = new TalismanButton(bx, by, half, BUTTON_H, () -> Component.translatable("kultivator.sectevent.duty." + duty));
			button.stampedWhen(() -> {
				CompoundTag chosen = roster.first();
				return chosen != null && duty.equals(chosen.getStringOr("duty", ""));
			});
			button.enabledWhen(() -> roster.first() != null);
			button.onPress(() -> {
				CompoundTag chosen = roster.first();
				if (chosen != null) {
					act(chosen.getIntOr("id", 0), "duty:" + duty);
				}
			});
			button.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.duty." + duty).withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.sectevent.duty." + duty + ".desc"),
					Component.translatable("kultivator.sectevent.duty." + duty + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			add(button);
		}
		TalismanButton release = new TalismanButton(x + 2, rowA + 2 * (BUTTON_H + 1), colW - 4, BUTTON_H,
				() -> Component.translatable("kultivator.sectevent.ui.release_duty"));
		release.enabledWhen(() -> {
			CompoundTag chosen = roster.first();
			return chosen != null && !chosen.getStringOr("duty", "").isEmpty();
		});
		release.onPress(() -> {
			CompoundTag chosen = roster.first();
			if (chosen != null) {
				act(chosen.getIntOr("id", 0), "duty_off:" + chosen.getStringOr("soul", ""));
			}
		});
		add(release);

		int rightX = x + colW + 4;
		int rightW = x + w - rightX;
		slot = Math.max(12, Math.min(18, rightW / 9));
		gridX = rightX + (rightW - slot * 9) / 2;
		gridY = y + 12;
		int refineY = gridY + slot * 3 + 4;
		TalismanButton refine = new TalismanButton(rightX + 2, refineY, rightW - 4, BUTTON_H,
				() -> Component.translatable("kultivator.sectevent.ui.refine", data().getIntOr("herbs", 0), Duties.HERBS_PER_PILL));
		refine.enabledWhen(() -> data().getIntOr("herbs", 0) >= Duties.HERBS_PER_PILL);
		refine.onPress(() -> act(0, "refine"));
		refine.tooltip(() -> List.of(Component.translatable("kultivator.sectevent.ui.refine.tip"),
				Component.translatable("kultivator.sectevent.ui.refine.odds").withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.sectevent.item.foundation_herb.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		add(refine);
		infoY = refineY + BUTTON_H + 4;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = data();
		int used = data.getCompoundOrEmpty("post_list").size();
		heading(g, Component.translatable("kultivator.sectevent.ui.posts", used, data.getIntOr("posts", 1)), x + 2.0F, y, colW - 4.0F, 0.8F);
		roster.draw(g, mouseX, mouseY, screen);

		int rightX = x + colW + 4;
		int rightW = x + w - rightX;
		heading(g, Component.translatable("kultivator.sectevent.ui.store"), rightX + 2.0F, y, rightW - 4.0F, 0.8F);
		ItemStack[] store = SectEventClient.store();
		int hovered = -1;
		for (int i = 0; i < Storehouse.SLOTS; i++) {
			int sx = gridX + i % 9 * slot;
			int sy = gridY + i / 9 * slot;
			boolean over = mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot;
			g.fill(sx, sy, sx + slot - 1, sy + slot - 1, over ? Palette.argb(0x70, Palette.JADE_PALE) : Palette.argb(0x48, Palette.PAPER_DEEP));
			Brush.frame(g, sx, sy, sx + slot - 1, sy + slot - 1, Palette.argb(0x90, Palette.GOLD_DARK));
			if (over) {
				hovered = i;
			}
		}
		drawInfo(g, rightX, rightW);
		// Items on their own layer so they sit on top of the slots.
		g.nextStratum();
		float itemScale = (slot - 2) / 16.0F;
		for (int i = 0; i < Storehouse.SLOTS; i++) {
			ItemStack stack = store[i];
			if (stack.isEmpty()) {
				continue;
			}
			int sx = gridX + i % 9 * slot;
			int sy = gridY + i / 9 * slot;
			g.pose().pushMatrix();
			g.pose().translate(sx + 0.5F, sy + 0.5F);
			g.pose().scale(itemScale, itemScale);
			g.item(stack, 0, 0);
			g.itemDecorations(font, stack, 0, 0);
			g.pose().popMatrix();
		}
		if (hovered >= 0 && !store[hovered].isEmpty()) {
			ItemStack stack = store[hovered];
			screen.showTooltip(List.of(stack.getHoverName(), Component.translatable("kultivator.sectevent.ui.store.count", stack.getCount())
					.withStyle(ChatFormatting.GRAY), Component.translatable("kultivator.sectevent.ui.store.take").withStyle(ChatFormatting.DARK_AQUA)));
		}
	}

	/** What the chosen disciple's duty yields, the chickens for the farm, the Pikul Air body and the meal. */
	private void drawInfo(GuiGraphicsExtractor g, int rightX, int rightW) {
		CompoundTag data = data();
		List<Component> list = new ArrayList<>();
		CompoundTag chosen = roster.first();
		if (chosen == null) {
			list.add(Component.translatable("kultivator.sectevent.ui.duty_hint"));
		} else {
			String duty = chosen.getStringOr("duty", "");
			if (duty.isEmpty()) {
				list.add(Component.translatable("kultivator.sectevent.ui.duty_pick", SectEventClient.name(chosen, "name")));
			} else {
				list.add(Component.translatable("kultivator.sectevent.ui.duty_now", SectEventClient.name(chosen, "name"),
						Component.translatable("kultivator.sectevent.duty." + duty)).withColor(Palette.JADE_DARK));
				list.add(Component.translatable("kultivator.sectevent.duty." + duty + ".desc"));
			}
			list.add(Component.translatable("kultivator.sectevent.ui.trained", chosen.getIntOr("trained_hp", 0), Duties.WATER_HP_MAX));
			long meal = chosen.getLongOr("meal", 0L) - now();
			if (meal > 0L) {
				list.add(Component.translatable("kultivator.sectevent.ui.meal", clock(meal)).withColor(Palette.onPaper(0xE5821C)));
			}
		}
		int chickens = data.getIntOr("chickens", 0);
		list.add(Component.translatable("kultivator.sectevent.ui.chickens", chickens, Duties.FARM_CHICKENS)
				.withColor(chickens >= Duties.FARM_CHICKENS ? Palette.JADE_DARK : Palette.DANGER));
		panel(g, rightX, infoY, x + w, y + h);
		lines(g, list, rightX + 4.0F, infoY + 3.0F, rightW - 8.0F, y + h - 3.0F, Palette.INK_SOFT, 0.6F);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0 || !data().getBooleanOr("hall", false)) {
			return false;
		}
		if (roster.click(mouseX, mouseY)) {
			return true;
		}
		int index = slotAt(mouseX, mouseY);
		if (index >= 0 && !SectEventClient.store()[index].isEmpty()) {
			act(0, "take:" + index);
			dev.kultivator.client.screen.ui.UiSound.press();
			return true;
		}
		return false;
	}

	private int slotAt(double mouseX, double mouseY) {
		if (mouseX < gridX || mouseY < gridY || mouseX >= gridX + slot * 9 || mouseY >= gridY + slot * 3) {
			return -1;
		}
		return (int) ((mouseY - gridY) / slot) * 9 + (int) ((mouseX - gridX) / slot);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return roster.scroll(mouseX, mouseY, amount);
	}

	@Override
	protected String shape() {
		return data().getBooleanOr("hall", false) ? "hall" : "none";
	}
}
