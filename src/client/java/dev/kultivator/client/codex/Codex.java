package dev.kultivator.client.codex;

import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.client.Minecraft;

/**
 * The client codex contract (DESIGN 22.18.3): opening the Kitab Teknik and feeding the Roda Segel with mouse movement
 * and the mouse wheel ({@code MouseHandlerMixin}).
 */
public final class Codex {

	/** Opens the Kitab Teknik, focused on the spec with net id {@code focusNetId} (−1 = none). */
	public static void open(int focusNetId) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		TechniqueSpec spec = focusNetId < 0 ? null : TechniqueRegistry.byNet(focusNetId);
		SealWheel.close(false);
		if (minecraft.gui.screen() instanceof CodexScreen codex) {
			if (spec != null) {
				codex.focus(spec);
			}
			return;
		}
		minecraft.gui.setScreen(new CodexScreen(spec, -1));
	}

	/** Opens the Kitab Teknik on a tablet ({@link CodexScreen#TAB_KODEKS} ...). */
	public static void openTab(int tab) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			SealWheel.close(false);
			minecraft.gui.setScreen(new CodexScreen(null, tab));
		}
	}

	/** Mouse movement while the seal wheel is open; true when consumed (the camera does not turn). */
	public static boolean wheelInput(double dx, double dy) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!SealWheel.isOpen() || minecraft.gui.screen() != null) {
			return false;
		}
		return SealWheel.input(dx, dy);
	}

	/** The mouse wheel while the seal wheel is open; true when consumed (the hotbar slot does not change). */
	public static boolean wheelScroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!SealWheel.isOpen() || minecraft.gui.screen() != null) {
			return false;
		}
		return SealWheel.scroll(amount);
	}

	private Codex() {
	}
}
