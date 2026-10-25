package dev.kultivator.client.sekte.langit;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Pemandangan Langit", the sky-sect view settings (module 37V, the client switches of {@link LangitClientConfig}): an
 * unrolling scroll with a small living painting at its head (a floating island over a sea of clouds, its mist fall, a
 * pair of cranes crossing, the sun or the moon by the real time of day), then the nine jade-coin switches in two columns
 * with their explanations as slip tooltips, and a foot line with the Effect Quality in use and the quads the sky drew
 * last frame. Opened from the sky-sect screen of 37G ({@link LangitClient#openSettings}); Esc returns to the parent.
 */
public final class LangitVisualScreen extends Screen {

	private static final String K = "kultivator.sekte_langit.visual.";
	private static final int MAX_W = 340;
	private static final int MAX_H = 250;
	private static final int ROW_H = 16;
	private static final long UNROLL_MILLIS = 260L;

	private final @Nullable Screen parent;
	private final List<Widget> widgets = new ArrayList<>();
	private long openedAt;
	private long lastFrame;
	private int x0;
	private int x1;
	private int top;
	private int bottom;
	private @Nullable Widget held;

	public LangitVisualScreen(@Nullable Screen parent) {
		super(Component.translatable(K + "title"));
		this.parent = parent;
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
		top = (height - h) / 2;
		bottom = top + h;
		widgets.clear();
		int cx0 = x0 + ScrollFrame.SIDE + 10;
		int cx1 = x1 - ScrollFrame.SIDE - 10;
		int colW = (cx1 - cx0 - 8) / 2;
		int y = paintingBottom() + 8;
		List<LangitClientConfig.Toggle> toggles = LangitClientConfig.TOGGLES;
		int rows = (toggles.size() + 1) / 2;
		for (int i = 0; i < toggles.size(); i++) {
			LangitClientConfig.Toggle toggle = toggles.get(i);
			int col = i / rows;
			int row = i % rows;
			JadeCoin coin = new JadeCoin(cx0 + col * (colW + 8), y + row * ROW_H, colW, 12, toggle::label, () -> LangitClientConfig.on(toggle));
			coin.onPress(() -> LangitClientConfig.flip(toggle));
			coin.tooltip(() -> List.of(Brush.bold(toggle.label()), toggle.tip()));
			widgets.add(coin);
		}
	}

	private int paintingTop() {
		return top + ScrollFrame.END + 20;
	}

	private int paintingBottom() {
		return paintingTop() + Math.max(48, Math.min(78, (bottom - top) / 3));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		float open = Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
		int mid = (top + bottom) / 2;
		int half = Math.round((bottom - top) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, top, bottom);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		int cx0 = x0 + ScrollFrame.SIDE + 10;
		int cx1 = x1 - ScrollFrame.SIDE - 10;
		int cy = top + ScrollFrame.END + 5;
		Brush.fitCentered(g, font, Brush.bold(title), (cx0 + cx1) / 2.0F, cy, cx1 - cx0, Palette.SEAL_DARK, 1.0F, 0.55F, false);
		Brush.rect(g, cx0, cy + 11, cx1, cy + 11.6F, Palette.argb(0xB0, Palette.GOLD));
		float seconds = (Util.getMillis() % 3_600_000L) / 1000.0F;
		painting(g, cx0, paintingTop(), cx1, paintingBottom(), seconds);
		long now = Util.getMillis();
		float frame = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		List<Component> tooltip = List.of();
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, frame);
			if (widget.contains(mouseX, mouseY)) {
				tooltip = widget.tooltipLines();
			}
		}
		int foot = bottom - ScrollFrame.END - 13;
		Brush.rect(g, cx0, foot - 3, cx1, foot - 2.4F, Palette.argb(0x70, Palette.GOLD));
		Component quality = Component.translatable("kultivator.quality_setting." + EffectQuality.level().name().toLowerCase(java.util.Locale.ROOT));
		Brush.fit(g, font, Component.translatable(K + "foot", quality, LangitVisuals.quadsLastFrame(), LangitSkyState.halls().size()), cx0, foot,
				cx1 - cx0, Palette.INK_SOFT, 0.65F, 0.45F, false);
		if (!tooltip.isEmpty()) {
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/**
	 * The living painting: dusk-to-noon sky by the real clock of the world, the sun or the moon, a floating island with a
	 * pagoda, its mist fall pouring off the rim, a sea of clouds drifting under it and two cranes crossing.
	 */
	private void painting(GuiGraphicsExtractor g, int px0, int py0, int px1, int py1, float t) {
		Minecraft minecraft = Minecraft.getInstance();
		long day = minecraft.level != null ? Math.floorMod(minecraft.level.getOverworldClockTime(), 24000L) : 6000L;
		float light = Mth.clamp(1.0F - Math.abs(day - 6000L) / 7000.0F, 0.0F, 1.0F);
		boolean nightTime = day > 13000L && day < 23000L;
		int skyTop = nightTime ? 0xFF0E1A33 : Palette.mix(0xFF2A3E78, 0xFF6FA8E8, light);
		int skyBottom = nightTime ? 0xFF33406A : Palette.mix(0xFFF0A868, 0xFFD8ECFF, light);
		g.fillGradient(px0, py0, px1, py1, skyTop, skyBottom);
		float w = px1 - px0;
		float h = py1 - py0;
		// the sun or the moon
		float bodyX = px0 + w * (0.15F + 0.7F * ((day + 18000L) % 24000L) / 24000.0F);
		float bodyY = py0 + h * 0.25F;
		if (nightTime) {
			Brush.halo(g, bodyX, bodyY, 4.0F, 3.0F, 0xE8F0FF, 0.5F);
			Brush.disc(g, bodyX, bodyY, 4.0F, 0xFFF2F4FF);
			for (int i = 0; i < 14; i++) {
				float sx = px0 + (i * 37 % 100) / 100.0F * w;
				float sy = py0 + (i * 53 % 45) / 100.0F * h;
				float tw = 0.5F + 0.5F * Mth.sin(t * 2.0F + i);
				Brush.rect(g, sx, sy, sx + 1.0F, sy + 1.0F, Palette.argb(Math.round(120 + 120 * tw), 0xFFFFFF));
			}
		} else {
			Brush.halo(g, bodyX, bodyY, 5.0F, 4.0F, 0xFFE8A0, 0.6F);
			Brush.disc(g, bodyX, bodyY, 5.0F, 0xFFFFF0C0);
		}
		// the sea of clouds: three drifting rows of puffs
		float seaY = py0 + h * 0.78F;
		for (int row = 0; row < 3; row++) {
			int puffColor = nightTime ? Palette.argb(0xC0 - row * 30, 0x9AA8D0) : Palette.argb(0xE0 - row * 30, 0xFFFFFF);
			float drift = (t * (4.0F + row * 2.0F)) % 24.0F;
			for (float x = px0 - 24.0F + drift; x < px1 + 12.0F; x += 12.0F) {
				float r = 6.0F + 2.0F * Mth.sin(x * 0.31F + row);
				float cx = Mth.clamp(x, px0 + r, px1 - r);
				Brush.disc(g, cx, seaY + row * 5.0F, r, puffColor);
			}
		}
		g.fillGradient(px0, Math.round(seaY + 4.0F), px1, py1, nightTime ? 0xFF6A78A0 : 0xFFF4F8FF, nightTime ? 0xFF4A5680 : 0xFFE4ECF8);
		// the island: a grassy top and a stepped stone cone hanging under it, bobbing gently
		float ix = px0 + w * 0.42F;
		float iy = py0 + h * 0.42F + Mth.sin(t * 0.8F) * 1.5F;
		float iw = w * 0.26F;
		int stone = nightTime ? 0xFF4A4E62 : 0xFF8A8478;
		int stoneDark = nightTime ? 0xFF33364A : 0xFF6A6458;
		int steps = 9;
		for (int i = 0; i < steps; i++) {
			float k = i / (float) steps;
			float half = iw * 0.5F * (1.0F - k * k) + 1.0F;
			float off = Mth.sin(i * 1.7F) * 1.5F;
			Brush.rect(g, ix - half + off, iy + 3.0F + i * 2.0F, ix + half + off, iy + 5.0F + i * 2.0F, i % 2 == 0 ? stone : stoneDark);
		}
		Brush.rect(g, ix - iw * 0.5F - 1.0F, iy, ix + iw * 0.5F + 1.0F, iy + 3.0F, nightTime ? 0xFF2E5A44 : 0xFF4FA35A);
		// a hanging root and a glowing crystal under the cone
		Brush.line(g, ix + 3.0F, iy + 21.0F, ix + 4.0F, iy + 27.0F, 1.0F, 0xFF5A4030);
		Brush.disc(g, ix - 3.0F, iy + 22.0F, 1.6F, 0xFFC8A0FF);
		// the hall and a five-roof pagoda on the island
		int roof = nightTime ? 0xFF6A2A28 : 0xFFB03A2E;
		Brush.rect(g, ix - 8.0F, iy - 5.0F, ix + 2.0F, iy, 0xFFE8E0D0);
		Brush.rect(g, ix - 10.0F, iy - 7.0F, ix + 4.0F, iy - 5.0F, roof);
		for (int i = 0; i < 5; i++) {
			float pw = 5.0F - i * 0.7F;
			float py = iy - 3.0F - i * 4.0F;
			Brush.rect(g, ix + 8.0F - pw * 0.5F, py - 1.0F, ix + 8.0F + pw * 0.5F, py + 2.0F, 0xFFE8E0D0);
			Brush.rect(g, ix + 8.0F - pw, py - 2.5F, ix + 8.0F + pw, py - 1.0F, roof);
		}
		if (nightTime) {
			Brush.halo(g, ix + 8.0F, iy - 23.0F, 1.0F, 2.0F, 0xFFE0A0, 0.8F);
		}
		// the mist fall off the right rim, thinning into the cloud sea
		float fx = ix + iw * 0.5F;
		for (int i = 0; i < 12; i++) {
			float k = i / 12.0F;
			float yy = iy + 1.0F + k * (seaY - iy - 2.0F);
			float alpha = 1.0F - k;
			float flow = 0.5F + 0.5F * Mth.sin(t * 6.0F - i * 0.9F);
			Brush.rect(g, fx + k * 3.0F, yy, fx + k * 3.0F + 2.0F, yy + (seaY - iy) / 12.0F + 0.5F,
					Palette.argb(Math.round((120 + 100 * flow) * alpha), 0xE8F6FF));
		}
		// two cranes crossing the sky (by day) or two lanterns rising (by night)
		for (int i = 0; i < 2; i++) {
			float k = ((t * 0.05F + i * 0.17F) % 1.0F);
			float bx = px0 + w * k;
			float by = py0 + h * (0.3F - i * 0.08F) + Mth.sin(t + i) * 2.0F;
			if (nightTime) {
				float ly = py1 - (t * 4.0F + i * 30.0F) % h;
				float lx = ix - 20.0F + i * 30.0F + Mth.sin(t + i) * 3.0F;
				Brush.halo(g, lx, ly, 1.2F, 2.0F, 0xFFB060, 0.7F);
				Brush.rect(g, lx - 1.0F, ly - 1.5F, lx + 1.0F, ly + 1.5F, 0xFFFFB86A);
			} else {
				float flap = Mth.sin(t * 8.0F + i) * 1.5F;
				Brush.line(g, bx - 3.0F, by - flap, bx, by, 1.0F, 0xFF2A2A30);
				Brush.line(g, bx, by, bx + 3.0F, by - flap, 1.0F, 0xFF2A2A30);
				Brush.rect(g, bx + 2.5F, by - 0.5F, bx + 3.5F, by + 0.5F, 0xFFD02020);
			}
		}
		Brush.frame(g, px0, py0, px1, py1, Palette.argb(0xC0, Palette.GOLD_DARK));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (Widget widget : widgets) {
				if (widget.contains(event.x(), event.y())) {
					widget.press();
					held = widget;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (held != null) {
			Widget widget = held;
			held = null;
			widget.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		UiSound.close();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
