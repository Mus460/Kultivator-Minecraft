package dev.kultivator.client.epic.screen;

import dev.kultivator.client.epic.EpicSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The motion warning of Kamera Spektakuler (Phase 30, DESIGN 30.2.1), in the jade style of the photosensitivity warning:
 * shown the first time the player switches the camera to Spektakuler (fast sweeps, crash zooms, rolls, frozen time).
 * "Aktifkan" remembers the choice ({@code epic_spektakuler_ok}) and switches to Spektakuler; "Kembali ke Penuh" and Esc
 * keep Penuh. Returns to the screen it came from.
 */
public final class MotionWarningScreen extends Screen {

	private static final String K = "kultivator.film.warning.";
	private static final int PANEL_W = 300;
	private static final int JADE = 0xFF2E8963;

	private final @Nullable Screen back;
	private int panelX;
	private int panelY;
	private int panelH;

	public MotionWarningScreen(@Nullable Screen back) {
		super(Component.translatable(K + "title"));
		this.back = back;
	}

	@Override
	protected void init() {
		int w = Math.min(PANEL_W, width - 16);
		panelH = Math.min(170, height - 16);
		panelX = (width - w) / 2;
		panelY = (height - panelH) / 2;
		int buttonW = Math.max(60, (w - 30) / 2);
		int buttonY = panelY + panelH - 28;
		addRenderableWidget(Button.builder(Component.translatable(K + "enable"), button -> {
			EpicSettings.confirmSpektakuler();
			onClose();
		}).bounds(panelX + 10, buttonY, buttonW, 20).build());
		addRenderableWidget(Button.builder(Component.translatable(K + "back"), button -> {
			EpicSettings.setCamera(EpicSettings.Camera.PENUH);
			onClose();
		}).bounds(panelX + w - 10 - buttonW, buttonY, buttonW, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xE0081410, 0xF0020604);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int w = Math.min(PANEL_W, width - 16);
		g.fill(panelX, panelY, panelX + w, panelY + panelH, 0xF0102A20);
		g.fill(panelX, panelY, panelX + w, panelY + 1, JADE);
		g.fill(panelX, panelY + panelH - 1, panelX + w, panelY + panelH, JADE);
		g.fill(panelX, panelY, panelX + 1, panelY + panelH, JADE);
		g.fill(panelX + w - 1, panelY, panelX + w, panelY + panelH, JADE);
		g.centeredText(font, title, width / 2, panelY + 10, 0xFFFFD27A);
		g.textWithWordWrap(font, Component.translatable(K + "body"), panelX + 12, panelY + 28, w - 24, 0xFFD8F3E4);
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
