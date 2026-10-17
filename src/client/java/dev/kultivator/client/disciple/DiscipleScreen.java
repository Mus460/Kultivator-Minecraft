package dev.kultivator.client.disciple;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeTablet;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A disciple's scroll in the "Gulungan Giok" style (DESIGN 5b, 18): the same unrolling jade scroll as the cultivation
 * screen, with four jade tablets on its left edge: Status, Techniques (teach the master's arts), Orders (follow, stay,
 * meditate, assist) and Pills &amp; Breakthrough (feed the pill in hand, start the breakthrough, choose the Dao,
 * release). Every button sends a {@code DISCIPLE_*} action with the disciple's entity id; the server answers with fresh
 * details, which refresh the scroll. It closes when the disciple is gone or more than 16 blocks away. Not a pause screen.
 */
public class DiscipleScreen extends Screen {

	static final int TAB_STATUS = 0;
	static final int TAB_TECHNIQUES = 1;
	static final int TAB_ORDERS = 2;
	static final int TAB_PILLS = 3;

	private static final long UNROLL_MILLIS = 250L;
	private static final int TABLET_W = 20;
	private static final int MAX_W = 420;
	private static final int MAX_H = 270;
	/** Same reach as the server's {@code Disciples.ACTION_RANGE}, so a refresh never meets a "too far" refusal. */
	private static final double MAX_DISTANCE_SQ = Disciples.ACTION_RANGE * Disciples.ACTION_RANGE;
	/** Live values (qi, xp, health) are asked for again this often while the scroll is open, in ticks. */
	private static final int REFRESH_TICKS = 100;
	private static final int[] ICONS = {Glyphs.STATUS, Glyphs.TECHNIQUES, Glyphs.HELP, Glyphs.ROOT_DAO};

	private final int entityId;
	private DiscipleView view;
	private long seenVersion;
	private int tab;
	private final DiscipleTab[] tabs;
	private final List<JadeTablet> tablets = new ArrayList<>();
	private final Eased backdrop = new Eased(6.0F);
	private long openedAt;
	private long lastFrame;
	private boolean opened;
	private int ticksOpen;
	private @Nullable JadeTablet pressedTablet;
	private @Nullable List<Component> tooltip;

	private int paperX0;
	private int paperX1;
	private int paperTop;
	private int paperBottom;
	private int contentX;
	private int contentY;
	private int contentW;
	private int contentH;

	public DiscipleScreen(int entityId, CompoundTag info, long version) {
		super(Component.translatable("kultivator.discipleui.title"));
		this.entityId = entityId;
		this.view = new DiscipleView(info);
		this.seenVersion = version;
		this.tabs = new DiscipleTab[]{new StatusTab(this), new TechniqueTab(this), new OrderTab(this), new PillTab(this)};
	}

	public int entityId() {
		return entityId;
	}

	DiscipleView view() {
		return view;
	}

	/** The disciple on this client, or null when it is not loaded. */
	@Nullable DiscipleEntity entity() {
		if (minecraft.level == null) {
			return null;
		}
		Entity entity = minecraft.level.getEntity(entityId);
		return entity instanceof DiscipleEntity disciple ? disciple : null;
	}

	/** The disciple's name: from the details, else its entity name. */
	Component discipleName() {
		if (!view.name.isEmpty()) {
			return Component.literal(view.name);
		}
		DiscipleEntity disciple = entity();
		return disciple != null ? DiscipleLook.name(disciple) : Component.translatable("kultivator.discipleui.title");
	}

	/** Sends a disciple action for this disciple. */
	void send(int action, String text) {
		Keys.send(new Payloads.Action(action, entityId, text));
	}

	/** Shows {@code lines} as this frame's tooltip (drawn last, over everything). */
	void showTooltip(List<Component> lines) {
		if (tooltip == null && !lines.isEmpty()) {
			tooltip = lines;
		}
	}

	@Override
	protected void init() {
		if (!opened) {
			opened = true;
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		int scrollW = Math.min(MAX_W, width - TABLET_W - 26);
		int scrollH = Math.min(MAX_H, height - 12);
		paperX0 = (width - scrollW + TABLET_W) / 2;
		paperX1 = paperX0 + scrollW;
		paperTop = (height - scrollH) / 2 + 5;
		paperBottom = paperTop + scrollH - 10;
		contentX = paperX0 + ScrollFrame.SIDE + 7;
		contentW = paperX1 - ScrollFrame.SIDE - 7 - contentX;
		contentY = paperTop + ScrollFrame.END + 17;
		contentH = paperBottom - ScrollFrame.END - 6 - contentY;

		tablets.clear();
		int count = tabs.length;
		int top = paperTop + 10;
		int gap = height < 250 ? 2 : 3;
		int tabletH = Math.max(24, Math.min(58, (paperBottom - paperTop - 20 - gap * (count - 1)) / count));
		for (int i = 0; i < count; i++) {
			int index = i;
			JadeTablet tablet = new JadeTablet(paperX0 - TABLET_W + 1, top + i * (tabletH + gap), TABLET_W, tabletH, ICONS[i],
					() -> tabs[index].shortName(), () -> tab == index);
			tablet.onPress(() -> switchTab(index));
			tablet.tooltip(() -> List.of(tabs[index].title(), Component.translatable("kultivator.discipleui.tab.key", index + 1)));
			tablets.add(tablet);
		}
		for (DiscipleTab each : tabs) {
			each.layout(contentX, contentY, contentW, contentH);
		}
	}

	private void switchTab(int index) {
		if (index == tab || index < 0 || index >= tabs.length) {
			return;
		}
		tab = index;
		tabs[tab].layout(contentX, contentY, contentW, contentH);
	}

	@Override
	public void tick() {
		DiscipleEntity disciple = entity();
		if (disciple == null || disciple.isRemoved() || !disciple.isAlive() || minecraft.player == null
				|| disciple.distanceToSqr(minecraft.player) > MAX_DISTANCE_SQ) {
			onClose();
			return;
		}
		ticksOpen++;
		if (ticksOpen % REFRESH_TICKS == 0) {
			// Only fresh details: the server never reopens the screen for a refresh (it may be closed by the time it answers).
			send(Actions.DISCIPLE_OPEN, "refresh");
		}
	}

	/** Picks up details that arrived since the last frame. */
	private void refresh() {
		long version = DiscipleClient.version(entityId);
		if (version == seenVersion) {
			return;
		}
		seenVersion = version;
		CompoundTag info = DiscipleClient.info(entityId);
		if (info == null) {
			return;
		}
		view = new DiscipleView(info);
		DiscipleTab current = tabs[tab];
		if (current.needsRebuild()) {
			current.layout(contentX, contentY, contentW, contentH);
		} else {
			current.onView();
		}
	}

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float fade = backdrop.to(1.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F), 0.0F);
		g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xC0 * fade), Palette.NIGHT), Palette.argb(Math.round(0xD8 * fade), Palette.NIGHT_MIST));
		// A low range of ink-wash hills along the bottom.
		int step = Math.max(2, width / 160);
		int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
		int close = Palette.argb(Math.round(0x90 * fade), Palette.NIGHT);
		for (int x = 0; x < width; x += step) {
			double ridge = Math.sin(x * 0.017D + 0.8D) * 12.0D + Math.sin(x * 0.047D + 2.3D) * 6.0D;
			int peak = (int) Math.round(height * 0.17D + ridge);
			int low = Math.max(0, (int) Math.round(height * 0.08D + Math.sin(x * 0.031D + 1.1D) * 7.0D));
			if (peak > low) {
				g.fill(x, height - peak, x + step, height - low, far);
			}
			g.fill(x, height - low, x + step, height, close);
		}
		g.nextStratum();
		Brush.halo(g, width * 0.16F, height * 0.17F, 7.0F, 7.0F, 0xE8EEF5, 0.3F * fade);
		Brush.disc(g, width * 0.16F, height * 0.17F, 7.0F, Palette.argb(Math.round(0x88 * fade), 0xE8EEF5));
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		refresh();

		float open = unroll();
		int mid = (paperTop + paperBottom) / 2;
		int half = Math.round((paperBottom - paperTop) / 2.0F * open);
		int top = mid - half;
		int bottom = mid + half;

		float tabletsOut = Eased.out(Math.min(1.0F, Math.max(0.0F, (now - openedAt - 150L) / 220.0F)));
		g.pose().pushMatrix();
		g.pose().translate((1.0F - tabletsOut) * (TABLET_W - 2), 0.0F);
		for (JadeTablet tablet : tablets) {
			if (tablet.y + tablet.h <= bottom && tablet.y >= top) {
				tablet.render(g, font, mouseX, mouseY, seconds);
			}
		}
		g.pose().popMatrix();
		g.nextStratum();

		ScrollFrame.paper(g, paperX0, paperX1, top, bottom, paperTop, paperBottom);
		ScrollFrame.rod(g, paperX0, paperX1, top);
		ScrollFrame.rod(g, paperX0, paperX1, bottom);
		g.nextStratum();

		if (bottom - top > 24) {
			g.enableScissor(paperX0 + ScrollFrame.SIDE + 1, top + ScrollFrame.END + 1, paperX1 - ScrollFrame.SIDE - 1, bottom - ScrollFrame.END - 1);
			drawHeader(g);
			tabs[tab].render(g, mouseX, mouseY, seconds);
			g.disableScissor();
		}

		for (JadeTablet tablet : tablets) {
			if (tabletsOut > 0.9F && tablet.contains(mouseX, mouseY)) {
				showTooltip(tablet.tooltipLines());
			}
		}
		if (tooltip != null && open >= 1.0F) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** "&lt;name&gt; · Status" between two gold rules, a seal at each end and the tab's mark in the corners. */
	private void drawHeader(GuiGraphicsExtractor g) {
		Component text = Brush.bold(Component.translatable("kultivator.discipleui.header", discipleName(), tabs[tab].title()));
		float y = paperTop + ScrollFrame.END + 5.0F;
		float cx = (paperX0 + paperX1) / 2.0F;
		float room = contentW - 40.0F;
		float scale = Brush.fitScale(font, text, room, 1.0F, 0.6F);
		float textW = Math.min(room, font.width(text) * scale);
		Brush.fitCentered(g, font, text, cx, y, room, Palette.INK, 1.0F, 0.6F, false);
		float lineY = y + 4.0F * scale;
		float left = cx - textW / 2.0F - 6.0F;
		float right = cx + textW / 2.0F + 6.0F;
		Brush.rect(g, contentX + 8.0F, lineY - 0.5F, left - 4.0F, lineY + 0.5F, Palette.GOLD);
		Brush.rect(g, right + 4.0F, lineY - 0.5F, contentX + contentW - 8.0F, lineY + 0.5F, Palette.GOLD);
		Brush.rect(g, left - 3.0F, lineY - 2.0F, left + 1.0F, lineY + 2.0F, Palette.SEAL);
		Brush.rect(g, right - 1.0F, lineY - 2.0F, right + 3.0F, lineY + 2.0F, Palette.SEAL);
		Glyphs.tab(g, ICONS[tab], contentX + 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
		Glyphs.tab(g, ICONS[tab], contentX + contentW - 4.0F, lineY, 6.0F, Palette.GOLD_DARK);
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			for (JadeTablet tablet : tablets) {
				if (tablet.contains(event.x(), event.y())) {
					tablet.press();
					pressedTablet = tablet;
					return true;
				}
			}
		}
		if (tabs[tab].mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pressedTablet != null) {
			JadeTablet tablet = pressedTablet;
			pressedTablet = null;
			tablet.release(event.x(), event.y());
			return true;
		}
		if (tabs[tab].mouseReleased(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (tabs[tab].mouseScrolled(x, y, scrollY)) {
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int number = event.key() - InputConstants.KEY_1;
		if (number >= 0 && number < tabs.length) {
			if (number != tab) {
				UiSound.tab();
				switchTab(number);
			}
			return true;
		}
		return super.keyPressed(event);
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
