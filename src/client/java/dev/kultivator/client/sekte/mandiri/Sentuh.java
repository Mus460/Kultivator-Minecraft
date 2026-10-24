package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.client.screen.ui.UiSound;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Click areas registered while drawing (chips, bag slots, list rows, sub-page seals, the hour strip), used by the tab,
 * the board and the diary book of 39E: the frame registers what it drew, the next click is tested against the areas of
 * the last frame, newest first. Keeps the screens free of one widget object per tiny control. Client only.
 */
final class Sentuh {

	/** One area; {@code aksi} may be null for a tooltip-only area. */
	record Area(float x0, float y0, float x1, float y1, @Nullable Runnable aksi, @Nullable Supplier<List<Component>> tip) {

		boolean berisi(double mx, double my) {
			return mx >= x0 && mx < x1 && my >= y0 && my < y1;
		}
	}

	private final List<Area> drawn = new ArrayList<>();
	private final List<Area> last = new ArrayList<>();

	/** Starts a frame. */
	void mulai() {
		last.clear();
		last.addAll(drawn);
		drawn.clear();
	}

	void tambah(float x0, float y0, float x1, float y1, @Nullable Runnable aksi, @Nullable Supplier<List<Component>> tip) {
		drawn.add(new Area(x0, y0, x1, y1, aksi, tip));
	}

	/** True when the mouse is over an area drawn this frame. */
	boolean di(double mx, double my, float x0, float y0, float x1, float y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	/** Runs the action under the mouse (areas of the frame just drawn, else the one before). */
	boolean klik(double mx, double my) {
		for (List<Area> list : List.of(drawn, last)) {
			for (int i = list.size() - 1; i >= 0; i--) {
				Area a = list.get(i);
				if (a.aksi() != null && a.berisi(mx, my)) {
					UiSound.press();
					a.aksi().run();
					return true;
				}
			}
		}
		return false;
	}

	/** The tooltip of the area under the mouse (this frame), or null. */
	@Nullable List<Component> tip(double mx, double my) {
		for (int i = drawn.size() - 1; i >= 0; i--) {
			Area a = drawn.get(i);
			if (a.tip() != null && a.berisi(mx, my)) {
				List<Component> lines = a.tip().get();
				if (lines != null && !lines.isEmpty()) {
					return lines;
				}
			}
		}
		return null;
	}

	void bersih() {
		drawn.clear();
		last.clear();
	}
}
