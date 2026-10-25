package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.client.screen.ui.Lukis;
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

	/**
	 * Hands the areas with an action drawn this frame to the page's Tab ring ({@code Page.lukis}): each becomes a focusable
	 * {@link Lukis} zone (Tab reaches it, Enter/Space runs it, a click goes through the ring) with the gold focus ring.
	 */
	void zonakan(Lukis l, String awalan) {
		for (int i = 0; i < drawn.size(); i++) {
			Area a = drawn.get(i);
			if (a.aksi() == null) {
				continue;
			}
			int x0 = (int) Math.floor(a.x0());
			int y0 = (int) Math.floor(a.y0());
			int x1 = (int) Math.ceil(a.x1());
			int y1 = (int) Math.ceil(a.y1());
			String kunci = awalan + i;
			l.zona(kunci, x0, y0, x1, y1, a.aksi(), true, null, true);
			l.fokusCincin(kunci, x0, y0, x1, y1);
		}
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
