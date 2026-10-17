package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallPage;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SectionPicker;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.network.Actions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Base of the Tugas, Misi and Arena tablets (module B): reads {@link SectEventClient#data()}, rebuilds its controls when
 * the shape of the details changes (new disciples, teams, offers), offers a strip of sub-tabs and small drawing helpers
 * (bamboo slips, time left, the "no hall" note).
 */
abstract class SectEventPage extends HallPage {

	protected static final int TAB_H = 13;
	protected static final int ROW_H = 11;

	private long seenVersion = -1L;
	private String seenShape = "";

	protected SectEventPage(HallScreen screen) {
		super(screen);
	}

	/** The sect-activity details (never null). */
	protected CompoundTag data() {
		return SectEventClient.data();
	}

	/** A text that changes when the controls of the page must be rebuilt. */
	protected String shape() {
		return "";
	}

	@Override
	public void onInfo() {
		seenVersion = SectEventClient.version();
		seenShape = shape();
	}

	@Override
	public boolean needsRebuild() {
		return !shape().equals(seenShape);
	}

	/** Called at the top of every frame: rebuilds after an own payload when the shape changed. */
	protected void refresh() {
		long version = SectEventClient.version();
		if (version != seenVersion) {
			seenVersion = version;
			String now = shape();
			if (!now.equals(seenShape)) {
				layout(x, y, w, h);
			}
		}
	}

	/** Sends a sect-event action ({@code Actions.SECT_EVENT}); nothing from a sect mate's read-only scroll. */
	protected void act(int entityId, String text) {
		if (readonly()) {
			UiSound.denied();
			return;
		}
		send(Actions.SECT_EVENT, entityId, text);
	}

	/**
	 * The page's sections as one "‹ section ›" selector at the top-left (DESIGN 5b: no strip of tabs along the top; the
	 * jade tablets on the left stay the only navigation). Returns the y below it.
	 */
	protected int tabs(String[] keys, IntSupplier current, java.util.function.IntConsumer choose) {
		int pickerW = Math.min(w, Math.max(110, Math.round(w * 0.42F)));
		SectionPicker picker = new SectionPicker(x, y, pickerW, TAB_H, keys, current, index -> {
			if (current.getAsInt() != index) {
				choose.accept(index);
				layout(x, y, w, h);
			}
		});
		picker.tooltip(() -> List.of(Component.translatable("kultivator.spine6.ui.section_picker")));
		add(picker);
		return y + TAB_H + 4;
	}

	/** True when this is a sect mate's hall, shown read-only (actions are refused by the server too). */
	protected boolean readonly() {
		return data().getBooleanOr("readonly", false);
	}

	/** True when the details say there is no hall (then only a note is drawn). */
	protected boolean noHall(GuiGraphicsExtractor g) {
		if (data().getBooleanOr("hall", false)) {
			return false;
		}
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		Brush.wrap(g, font, Component.translatable("kultivator.sectevent.ui.no_hall"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 0.75F, false);
		return true;
	}

	/** Current game time of the client level (close to the server's). */
	protected static long now() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0L : minecraft.level.getGameTime();
	}

	/** "1:05:20" or "4:12" for a number of ticks. */
	protected static String clock(long ticks) {
		long seconds = Math.max(0L, ticks / 20L);
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long rest = seconds % 60L;
		return hours > 0L ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
				: String.format(java.util.Locale.ROOT, "%d:%02d", minutes, rest);
	}

	/** "Golden Core, Late" for a roster entry. */
	protected static Component rankName(CompoundTag entry) {
		Rank rank = new Rank(Realm.byOrdinal(entry.getIntOr("realm", 0)), entry.getIntOr("layer", 0), entry.getIntOr("stage", 0));
		return rank.displayName().withColor(Palette.onPaper(rank.realm().color()));
	}

	/** The sect rank's name (0 Tercatat .. 5 Tetua Agung). */
	protected static Component sectRank(int rank) {
		return Component.translatable("kultivator.sectevent.ui.rank." + Math.max(0, Math.min(5, rank)));
	}

	/** A bamboo slip: a pale strip with knots at both ends and a text on it. */
	protected void slip(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, Component text, int color, boolean bright) {
		int base = bright ? Palette.BAMBOO : Palette.BAMBOO_ALT;
		g.fill(x0, y0, x1, y1, base);
		g.fill(x0, y0, x0 + 2, y1, Palette.BAMBOO_KNOT);
		g.fill(x1 - 2, y0, x1, y1, Palette.BAMBOO_KNOT);
		g.fill(x0, y1 - 1, x1, y1, Palette.argb(0x50, 0x000000));
		float scale = Math.min(0.7F, (y1 - y0 - 2) / 9.0F);
		Brush.fit(g, font, text, x0 + 4.0F, y0 + (y1 - y0 - 8.0F * scale) / 2.0F, x1 - x0 - 8.0F, color, scale, 0.45F, false);
	}

	/** Word-wrapped lines from top to bottom inside a box; stops at its bottom. Returns the y below the last line. */
	protected float lines(GuiGraphicsExtractor g, List<Component> list, float x0, float y0, float width, float bottom, int color, float scale) {
		float ty = y0;
		for (Component line : list) {
			float height = Brush.wrapHeight(font, line, width, scale);
			if (ty + height > bottom + 0.5F) {
				break;
			}
			Brush.wrap(g, font, line, x0, ty, width, color, scale, false);
			ty += height + 1.0F;
		}
		return ty;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		refresh();
		if (!noHall(g)) {
			draw(g, mouseX, mouseY, seconds);
		}
	}

	/** The page's own pictures (after the refresh and the hall check). */
	protected abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds);
}
