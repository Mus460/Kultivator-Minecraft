package dev.kultivator.client.disciple;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A sub of Murid › Murid (DESIGN 43.5, 43.12.2): the former disciple-scroll tabs (Status, Teknik, Pil &amp; Dao, Bakat,
 * Pusaka, Jalan, Hati, Hubungan, Jalan Hidup, Buku Harian) as {@link Page}s shown inside {@link MuridPage}. A shared
 * helper over {@code Page} (like {@code SectEventPage}), not a second page base: it only hands every sub the disciple
 * the picker shows ({@link #view()}, {@link #info()}, {@link #send}) and the detail panel the tabs drew.
 */
public abstract class MuridSub extends Page {

	protected final MuridPage murid;

	protected MuridSub(MuridPage murid) {
		this.murid = murid;
	}

	/** New details of the disciple arrived (and after every layout). */
	public void onView() {
	}

	@Override
	public void onData() {
		onView();
	}

	/** The height this sub needs when stacked in one scroll with others (0 = the box it is given). */
	public int tinggiMin() {
		return 0;
	}

	protected DiscipleView view() {
		return murid.view();
	}

	/** The raw details of the last {@code DiscipleInfo} (never null). */
	protected CompoundTag info() {
		return murid.info();
	}

	/** Sends {@code action} for this disciple (arg = its entity id) with {@code text}. */
	protected void send(int action, String text) {
		murid.send(action, text);
	}

	/** Headings at 1.0 at least (43.0.5: no text under 1.0). */
	@Override
	protected void heading(GuiGraphicsExtractor g, Component text, float hx, float hy, float width, float scale) {
		super.heading(g, text, hx, hy, width, Math.max(1.0F, scale));
	}

	/** A faint parchment panel with a thin gold frame (detail boxes). */
	protected static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, Palette.argb(0x38, Palette.PAPER_DEEP));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xC0, Palette.GOLD));
		g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x30FFFFFF);
	}
}
