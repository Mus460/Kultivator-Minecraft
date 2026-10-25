package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormasiItems;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Pattern;
import dev.kultivator.formasi.spec.Rule;
import dev.kultivator.formasi.spec.Slot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mini pattern diagram of the bamboo slip (DESIGN 24.7.1): one ink-grid slice per layer, drawn from the pattern
 * data (3-4 px per cell, no image assets), north up; each cell takes the map colour of its canonical block, the eye is a
 * gold seal and the heart block is ringed in red. Below it the legend: swatch, item icon, label and count, the bill of
 * materials (text 1.0, 43.0.5). Nothing is cached between frames except the layer list of the last spec.
 */
final class BlueprintDiagram {

	private static final int GAP = 6;
	private static final int LABEL_H = 10;
	private static final int LEGEND_ROW = 11;

	private static @Nullable FormationSpec cachedSpec;
	private static int cachedRot = -1;
	private static Map<Integer, List<Slot>> cachedLayers = Map.of();

	/** Cell size in pixels for this pattern inside {@code width}. */
	private static int cell(FormationSpec spec, int width) {
		int span = spec.pattern().radius() * 2 + 1;
		return span * 4 + 2 <= width / 2 ? 4 : span * 3 + 2 <= width ? 3 : 2;
	}

	private static Map<Integer, List<Slot>> layers(FormationSpec spec, int rot) {
		if (spec != cachedSpec || rot != cachedRot) {
			Map<Integer, List<Slot>> map = new TreeMap<>();
			map.put(0, new ArrayList<>());
			for (Slot slot : spec.pattern().slots(rot)) {
				map.computeIfAbsent(slot.dy(), key -> new ArrayList<>()).add(slot);
			}
			cachedSpec = spec;
			cachedRot = rot;
			cachedLayers = map;
		}
		return cachedLayers;
	}

	/** The height {@link #draw} uses for the slices (legend excluded). */
	static float height(FormationSpec spec, int rot, int width) {
		int c = cell(spec, width);
		int side = (spec.pattern().radius() * 2 + 1) * c + 2;
		int perRow = Math.max(1, (width + GAP) / (side + GAP));
		int count = layers(spec, rot).size();
		int rows = (count + perRow - 1) / perRow;
		return rows * (side + LABEL_H + GAP);
	}

	/** Draws the slices at (x, y) within {@code width}; returns the height used. Hovered cells add a tooltip hit. */
	static float draw(FormasiPage page, GuiGraphicsExtractor g, FormationSpec spec, int rot, float x, float y, int width, int mouseX, int mouseY,
			float clipTop, float clipBottom) {
		Font font = page.font();
		Pattern pattern = spec.pattern();
		int radius = pattern.radius();
		int c = cell(spec, width);
		int span = radius * 2 + 1;
		int side = span * c + 2;
		int perRow = Math.max(1, (width + GAP) / (side + GAP));
		int index = 0;
		float used = 0.0F;
		for (Map.Entry<Integer, List<Slot>> layer : layers(spec, rot).entrySet()) {
			int col = index % perRow;
			int row = index / perRow;
			float sx = x + col * (side + GAP);
			float sy = y + row * (side + LABEL_H + GAP);
			used = Math.max(used, (row + 1) * (side + LABEL_H + GAP));
			index++;
			if (sy + side + LABEL_H < clipTop || sy > clipBottom) {
				continue;
			}
			int dy = layer.getKey();
			String label = dy > 0 ? "+" + dy : Integer.toString(dy);
			// The slice may be narrower than "Lapisan +1" at 1.0 (43.0.5): the number is written, the word is the tooltip.
			Brush.fit(g, font, Component.literal(label), sx, sy, Math.max(side, 12), Palette.INK_SOFT, 1.0F, 1.0F, false);
			page.hit(sx, sy, sx + Math.max(side, 12), sy + LABEL_H - 1, null, List.of(Component.translatable("kultivator.formasiui.diagram.layer", label)));
			float gx = sx;
			float gy = sy + LABEL_H;
			// Paper and ink grid.
			Brush.rect(g, gx, gy, gx + side, gy + side, Palette.argb(0xC0, 0xF2E8C8));
			Brush.frame(g, Math.round(gx), Math.round(gy), Math.round(gx + side), Math.round(gy + side), Palette.argb(0xA0, Palette.INK_SOFT));
			if (c >= 3) {
				for (int i = 1; i < span; i++) {
					float line = gx + 1 + i * c;
					Brush.rect(g, line, gy + 1, line + 0.35F, gy + side - 1, Palette.argb(0x22, Palette.INK));
					float lineY = gy + 1 + i * c;
					Brush.rect(g, gx + 1, lineY, gx + side - 1, lineY + 0.35F, Palette.argb(0x22, Palette.INK));
				}
			}
			// North tick.
			Brush.rect(g, gx + side / 2.0F - 0.5F, gy - 1.5F, gx + side / 2.0F + 0.5F, gy, Palette.SEAL);
			if (dy == 0) {
				float ex = gx + 1 + radius * c;
				float ey = gy + 1 + radius * c;
				Brush.rect(g, ex, ey, ex + c, ey + c, Palette.GOLD);
				Brush.frame(g, Math.round(ex) - 1, Math.round(ey) - 1, Math.round(ex + c) + 1, Math.round(ey + c) + 1, Palette.SEAL);
			}
			Slot hovered = null;
			for (Slot slot : layer.getValue()) {
				float cx = gx + 1 + (slot.dx() + radius) * c;
				float cy = gy + 1 + (slot.dz() + radius) * c;
				Brush.rect(g, cx, cy, cx + c, cy + c, color(slot.rule()));
				if (slot.key() == Pattern.HEART) {
					Brush.frame(g, Math.round(cx) - 1, Math.round(cy) - 1, Math.round(cx + c) + 1, Math.round(cy + c) + 1, Palette.SEAL);
				}
				if (mouseX >= cx && mouseX < cx + c && mouseY >= cy && mouseY < cy + c && mouseY >= clipTop && mouseY < clipBottom) {
					hovered = slot;
				}
			}
			if (hovered != null) {
				float cx = gx + 1 + (hovered.dx() + radius) * c;
				float cy = gy + 1 + (hovered.dz() + radius) * c;
				Brush.frame(g, Math.round(cx) - 1, Math.round(cy) - 1, Math.round(cx + c) + 1, Math.round(cy + c) + 1, 0xFFFFFFFF);
				page.hit(cx, cy, cx + c, cy + c, null, List.of(hovered.rule().label(),
						Component.translatable("kultivator.formasiui.diagram.cell", hovered.dx(), hovered.dy(), hovered.dz())));
			}
		}
		return used;
	}

	/** The legend rows (bill of materials); returns the height used. */
	static float legend(FormasiPage page, GuiGraphicsExtractor g, FormationSpec spec, float x, float y, int width, float clipTop, float clipBottom) {
		Font font = page.font();
		Map<Rule, Integer> bill = spec.pattern().bill();
		int columns = width >= 220 ? 2 : 1;
		float colW = (width - (columns - 1) * 4.0F) / columns;
		int index = 0;
		// The eye first.
		List<Map.Entry<Component, Integer>> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		List<ItemStack> icons = new ArrayList<>();
		rows.add(Map.entry(Component.translatable("block.kultivator.formation_eye"), 1));
		colors.add(Palette.GOLD);
		icons.add(new ItemStack(FormasiItems.FORMATION_EYE_ITEM));
		for (Map.Entry<Rule, Integer> entry : bill.entrySet()) {
			rows.add(Map.entry(entry.getKey().label(), entry.getValue()));
			colors.add(color(entry.getKey()));
			icons.add(icon(entry.getKey()));
		}
		for (int i = 0; i < rows.size(); i++) {
			int col = index % columns;
			int row = index / columns;
			float rx = x + col * (colW + 4.0F);
			float ry = y + row * LEGEND_ROW;
			index++;
			if (ry + LEGEND_ROW < clipTop || ry > clipBottom) {
				continue;
			}
			Brush.rect(g, rx, ry + 3.0F, rx + 5.0F, ry + 8.0F, colors.get(i));
			Brush.frame(g, Math.round(rx), Math.round(ry + 3.0F), Math.round(rx + 5.0F), Math.round(ry + 8.0F), Palette.argb(0x80, Palette.INK));
			ItemStack stack = icons.get(i);
			if (!stack.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(rx + 7.0F, ry + 0.5F);
				g.pose().scale(0.5F, 0.5F);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			}
			Component text = Component.translatable("kultivator.formasiui.diagram.count", rows.get(i).getKey(), rows.get(i).getValue());
			page.line(text, rx + 17.0F, ry + 1.0F, colW - 18.0F, Palette.INK);
		}
		return (float) Math.ceil(rows.size() / (double) columns) * LEGEND_ROW;
	}

	/** The item a rule bills: its exact item, else the canonical block's item (barrier-free fallbacks for fluids). */
	static ItemStack icon(Rule rule) {
		Item exact = rule.exactItem();
		if (exact != null) {
			return new ItemStack(exact);
		}
		Item item = rule.canonical(0).getBlock().asItem();
		if (item == Items.AIR) {
			if (!rule.canonical(0).getFluidState().isEmpty()) {
				return new ItemStack(Items.WATER_BUCKET);
			}
			return ItemStack.EMPTY;
		}
		return new ItemStack(item);
	}

	/** The map colour of the rule's canonical block, grey when it has none. */
	static int color(Rule rule) {
		int col = rule.canonical(0).getBlock().defaultMapColor().col;
		return col == 0 ? 0xFF9AA4A0 : 0xFF000000 | col;
	}

	private BlueprintDiagram() {
	}
}
