package dev.kultivator.client.epic.screen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.screen.SettingsRows;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * Hides vanilla HUD pieces during shots (DESIGN 23.8.2): no alpha fades ({@code GuiGraphicsExtractor} has no global
 * alpha), the wrapper simply skips drawing. The hotbar, XP level, info bar, held-item tooltip and crosshair hide while the
 * bars are more than half in. Mode Rekam also hides health, armour, food, air, effects, the scoreboard and chat, but only
 * during the safe beats of the player's own scenes. Health, armour, boss bars and the mod's HUDs otherwise always stay.
 * The mod's own HUDs are also gated by their coin in Pengaturan › HUD ({@link SettingsRows#HUD_SAKLAR}, DESIGN 43.9).
 */
public final class HudHider {

	private static final Identifier[] WITH_BARS = {VanillaHudElements.HOTBAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR,
			VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.CROSSHAIR};
	private static final Identifier[] RECORD = {VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.FOOD_BAR,
			VanillaHudElements.AIR_BAR, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.SCOREBOARD, VanillaHudElements.CHAT};
	/** The mod's HUD elements with a switch: element id, switch key of {@link SettingsRows#HUD_SAKLAR} (Kabar draws in mandiri_toast). */
	private static final String[][] SAKLAR = {{"mandiri_toast", "kabar"}, {"qi_hud", "qi"}, {"codex_hud", "codex"}, {"aura_edge", "aura"},
			{"tribulation_hud", "tribulasi"}, {"duel_hud", "duel"}, {"gabung_ujian", "ujian"}, {"sekte_turnamen_lingkar", "lei_tai"},
			{"sekte_tangga_gulungan", "tangga"}, {"kota_besar_hud", "gerbang"}, {"kapal_hud", "kapal"}, {"langit_hud", "langit"},
			{"naga_hud", "naga"}, {"dimensi_jiwa", "dimensi"}, {"tema_fx_hud", "tema"}, {"elite_plate", "elite"}, {"formasi_alarm", "formasi"},
			{"sect_build_hud", "bangun"}};

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
		for (String[] s : SAKLAR) {
			String key = s[1];
			try {
				HudElementRegistry.replaceElement(Kultivator.id(s[0]), original -> (HudElement) (g, deltaTracker) -> {
					if (SettingsRows.hud(key)) {
						original.extractRenderState(g, deltaTracker);
					}
				});
			} catch (RuntimeException exception) {
				// an element registered later than CLIENT_STARTED keeps drawing; its coin then does nothing
				Kultivator.LOGGER.warn("HUD element {} not found for its switch", s[0]);
			}
		}
	}

	private HudHider() {
	}
}
