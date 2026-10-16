package dev.kultivator.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * STUB (phase 0) — owned by P1-hud-screen. The cultivation info screen in the jade-scroll style (DESIGN 5b, 12).
 * {@code tab}: 0 Status, 1 Root and Dao, 2 Techniques, 3 Settings, 4 Help.
 */
public class CultivationScreen extends Screen {

	public static final int TAB_STATUS = 0;
	public static final int TAB_ROOT_DAO = 1;
	public static final int TAB_TECHNIQUES = 2;
	public static final int TAB_SETTINGS = 3;
	public static final int TAB_HELP = 4;

	protected int tab;

	public CultivationScreen(int tab) {
		super(Component.translatable("kultivator.screen.title"));
		this.tab = tab;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
