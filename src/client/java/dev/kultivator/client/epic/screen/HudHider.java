package dev.kultivator.client.epic.screen;

import dev.kultivator.client.epic.EpicDirector;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * Hides vanilla HUD pieces during shots (DESIGN 23.8.2): no alpha fades ({@code GuiGraphicsExtractor} has no global
 * alpha), the wrapper simply skips drawing. The hotbar, XP level, info bar, held-item tooltip and crosshair hide while the
 * bars are more than half in. Mode Rekam also hides health, armour, food, air, effects, the scoreboard and chat, but only
 * during the safe beats of the player's own scenes. Health, armour, boss bars and the mod's HUDs otherwise always stay.
 */
public final class HudHider {

	private static final Identifier[] WITH_BARS = {VanillaHudElements.HOTBAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR,
			VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.CROSSHAIR};
	private static final Identifier[] RECORD = {VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.FOOD_BAR,
			VanillaHudElements.AIR_BAR, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.SCOREBOARD, VanillaHudElements.CHAT};

	/** Wraps the vanilla elements (from {@code CLIENT_STARTED}). */
	public static void register() {
		for (Identifier id : WITH_BARS) {
			HudElementRegistry.replaceElement(id, original -> (HudElement) (g, deltaTracker) -> {
				if (Letterbox.amount() > 0.5F || EpicDirector.recordHide()) {
					return;
				}
				original.extractRenderState(g, deltaTracker);
			});
		}
		for (Identifier id : RECORD) {
			HudElementRegistry.replaceElement(id, original -> (HudElement) (g, deltaTracker) -> {
				if (EpicDirector.recordHide()) {
					return;
				}
				original.extractRenderState(g, deltaTracker);
			});
		}
	}

	private HudHider() {
	}
}
