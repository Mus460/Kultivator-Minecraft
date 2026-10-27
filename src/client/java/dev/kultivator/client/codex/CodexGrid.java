package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The virtualized seal-card grid of the Kitab Teknik (DESIGN 22.12.2), also used by the Cincin Segel picker and the
 * disciple's teach view: cards of 28-40 px wrap to the width, only the visible rows are drawn, the wheel scrolls with a
 * soft ease, and when sorted by grade the cards are grouped under headers "Tingkat Bumi · 18". It owns no widgets; the
 * page asks it which card is under the mouse, and passes it the arrow keys while the grid has the focus ring
 * ({@link #tombol}: the selection is the keyboard cursor, 43.4.2).
 */
public final class CodexGrid {

	/** What a card looks like and says for the page that owns the grid. */
	public interface Source {
		SealCard.Look look(TechniqueSpec spec);
	}

	private record Cell(@Nullable TechniqueSpec spec, @Nullable TechniqueGrade header, int count, int x, int y) {
	}

	public static final int GAP = 3;
	public static final int HEADER = 12;

	private int x;
	private int y;
	private int w;
	private int h;
	private int min = 28;
	private int max = 40;
	private int size = 32;
	private final List<Cell> cells = new ArrayList<>();
	private List<TechniqueSpec> specs = List.of();
	private boolean grouped;
	private final Eased scrollShown = new Eased(14.0F);
	private float scroll;
	private float contentHeight;
	private @Nullable TechniqueSpec selected;
	private @Nullable TechniqueSpec hovered;

	public void layout(int x, int y, int w, int h, int minCard, int maxCard) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		this.min = minCard;
		this.max = maxCard;
		rebuild();
	}

	public void setSpecs(List<TechniqueSpec> list, boolean byGrade) {
		this.specs = list;
		this.grouped = byGrade;
		rebuild();
	}

	public List<TechniqueSpec> specs() {
		return specs;
	}

	public void select(@Nullable TechniqueSpec spec) {
		this.selected = spec;
	}

	public @Nullable TechniqueSpec selected() {
		return selected;
	}

	public @Nullable TechniqueSpec hovered() {
		return hovered;
	}

	private void rebuild() {
		cells.clear();
		int usable = Math.max(10, w - 5);
		int cols = Math.max(2, (usable + GAP) / (min + GAP));
		size = Math.max(min, Math.min(max, (usable - (cols - 1) * GAP) / cols));
		cols = Math.max(1, (usable + GAP) / (size + GAP));
		int used = cols * size + (cols - 1) * GAP;
		int left = x + Math.max(0, (usable - used) / 2);
		int cy = y + 2;
		int col = 0;
		TechniqueGrade group = null;
		for (int i = 0; i < specs.size(); i++) {
			TechniqueSpec spec = specs.get(i);
			if (grouped && spec.grade() != group) {
				group = spec.grade();
				if (col > 0) {
					cy += size + GAP;
					col = 0;
				}
				int count = 0;
				for (int j = i; j < specs.size() && specs.get(j).grade() == group; j++) {
					count++;
				}
				cells.add(new Cell(null, group, count, x, cy));
				cy += HEADER;
			}
			cells.add(new Cell(spec, null, 0, left + col * (size + GAP), cy));
			col++;
			if (col >= cols) {
				col = 0;
				cy += size + GAP;
			}
		}
		if (col > 0) {
			cy += size + GAP;
		}
		contentHeight = cy - y + 2;
	}

	/** Scrolls so {@code spec} is in view. */
	public void reveal(TechniqueSpec spec) {
		for (Cell cell : cells) {
			if (cell.spec() == spec) {
				float top = cell.y() - y;
				if (top < scroll) {
					scroll = Math.max(0.0F, top - 4.0F);
				} else if (top + size > scroll + h) {
					scroll = top + size - h + 4.0F;
				}
				return;
			}
		}
	}

	public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float seconds, Source source) {
		float maxScroll = Math.max(0.0F, contentHeight - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);
		hovered = null;
		boolean inside = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		g.enableScissor(x, y, x + w, y + h);
		for (Cell cell : cells) {
			int cy = Math.round(cell.y() - offset);
			int height = cell.spec() == null ? HEADER : size;
			if (cy + height + 3 < y || cy > y + h + 3) {
				continue;
			}
			if (cell.spec() == null) {
				TechniqueGrade grade = cell.header();
				int ribbon = SpecInfo.ribbon(grade);
				Brush.rect(g, cell.x() + 1.0F, cy + 2.0F, cell.x() + 5.0F, cy + 6.0F, ribbon);
				Component text = Component.translatable("kultivator.codex.group", grade.displayName(), cell.count());
				Brush.fit(g, font, Brush.bold(text), cell.x() + 7.0F, cy + 1.0F, w - 12.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
				Brush.rect(g, cell.x() + 1.0F, cy + HEADER - 2.0F, cell.x() + w - 6.0F, cy + HEADER - 1.4F, Palette.argb(0x80, Palette.GOLD));
				continue;
			}
			TechniqueSpec spec = cell.spec();
			boolean over = inside && mouseX >= cell.x() && mouseX < cell.x() + size && mouseY >= cy && mouseY < cy + size;
			if (over) {
				hovered = spec;
			}
			SealCard.Look look = source.look(spec);
			if (spec == selected && !look.selected()) {
				look = new SealCard.Look(look.bright(), look.locked(), true, look.cooldownLeft(), look.cooldownTotal(), look.mastery(), look.slot(),
						look.isNew(), look.stamped());
			}
			SealCard.paint(g, font, spec, cell.x(), cy, size, look, over ? 1.0F : 0.0F);
		}
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (offset / maxScroll);
			int rail = x + w - 2;
			g.fill(rail, y, rail + 1, y + h, Palette.CORD);
			Brush.disc(g, rail + 0.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, rail, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	/** The card under the mouse (from the last frame's layout), or null. */
	public @Nullable TechniqueSpec at(double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
			return null;
		}
		float offset = scrollShown.get();
		for (Cell cell : cells) {
			if (cell.spec() == null) {
				continue;
			}
			float cy = cell.y() - offset;
			if (mouseX >= cell.x() && mouseX < cell.x() + size && mouseY >= cy && mouseY < cy + size) {
				return cell.spec();
			}
		}
		return null;
	}

	// ================================================================== keyboard (43.4.2)

	/**
	 * Moves the selection (the keyboard cursor) with {@code key}: ←/→ the previous/next card, ↑/↓ the card above/below
	 * (nearest column, over group headers), PgUp/PgDn a screenful of rows, Home/End the ends. With no selection any of them
	 * selects the first card. Scrolls the new card into view; true when the key was one of these and a card exists.
	 */
	public boolean tombol(int key) {
		List<Cell> cards = new ArrayList<>();
		int at = -1;
		for (Cell cell : cells) {
			if (cell.spec() != null) {
				if (cell.spec() == selected) {
					at = cards.size();
				}
				cards.add(cell);
			}
		}
		int rows = Math.max(1, h / (size + GAP));
		int next = switch (key) {
			case InputConstants.KEY_LEFT -> at - 1;
			case InputConstants.KEY_RIGHT -> at + 1;
			case InputConstants.KEY_UP -> baris(cards, at, -1, 1);
			case InputConstants.KEY_DOWN -> baris(cards, at, 1, 1);
			case InputConstants.KEY_PAGEUP -> baris(cards, at, -1, rows);
			case InputConstants.KEY_PAGEDOWN -> baris(cards, at, 1, rows);
			case InputConstants.KEY_HOME -> 0;
			case InputConstants.KEY_END -> cards.size() - 1;
			default -> Integer.MIN_VALUE;
		};
		if (next == Integer.MIN_VALUE || cards.isEmpty()) {
			return false;
		}
		TechniqueSpec spec = cards.get(at < 0 ? 0 : Math.max(0, Math.min(cards.size() - 1, next))).spec();
		selected = spec;
		reveal(spec);
		return true;
	}

	/** The index {@code steps} rows above ({@code dir} -1) or below (+1) card {@code at}, in the nearest column. */
	private static int baris(List<Cell> cards, int at, int dir, int steps) {
		if (at < 0) {
			return 0;
		}
		int i = at;
		for (int s = 0; s < steps; s++) {
			Cell cur = cards.get(i);
			int rowY = Integer.MIN_VALUE;
			for (Cell c : cards) {
				boolean beyond = dir < 0 ? c.y() < cur.y() : c.y() > cur.y();
				if (beyond && (rowY == Integer.MIN_VALUE || (dir < 0 ? c.y() > rowY : c.y() < rowY))) {
					rowY = c.y();
				}
			}
			if (rowY == Integer.MIN_VALUE) {
				break;
			}
			int best = i;
			int bestDx = Integer.MAX_VALUE;
			for (int j = 0; j < cards.size(); j++) {
				Cell c = cards.get(j);
				int dx = Math.abs(c.x() - cur.x());
				if (c.y() == rowY && dx < bestDx) {
					best = j;
					bestDx = dx;
				}
			}
			i = best;
		}
		return i;
	}

	/** The on-screen box of the selected card {@code {x0, y0, x1, y1}} clipped to the grid, or null when out of view. */
	public int[] kotakTerpilih() {
		for (Cell cell : cells) {
			if (cell.spec() != null && cell.spec() == selected) {
				int cy = Math.round(cell.y() - scrollShown.get());
				int y0 = Math.max(y, cy);
				int y1 = Math.min(y + h, cy + size);
				return y1 - y0 < 4 ? null : new int[] {cell.x(), y0, cell.x() + size, y1};
			}
		}
		return null;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	public boolean scrolled(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		scroll -= (float) amount * (size + GAP) * 0.8F;
		return true;
	}
}
