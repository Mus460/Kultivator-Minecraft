package dev.kultivator.client.duel;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * The two duel keys (DESIGN 35.3.2), in the mod's own "Kultivator" section of the controls, both <b>unbound</b> by
 * default: a right click with a blade already deflects, so {@link #PARRY} is for players whose right click is taken
 * (Better Combat dual wielding: a mouse side button or C is suggested in the Kitab Panduan), and {@link #CAMERA} cycles
 * the duel camera (Mati, Bahu, Penonton). R (Combat Roll, Iris), G, V, Z, B, H and M are never used. Read by
 * {@link DuelInput} and the visual module's {@code DuelCamera}.
 */
public final class DuelKeys {

	private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

	/** Tap: deflect; hold: guard; during a Blade Lock: the sudden release; while winding up: a feint. */
	public static final KeyMapping PARRY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.kultivator.parry", InputConstants.Type.KEYBOARD, UNBOUND, Keys.CAST.getCategory()));
	/** Cycles the duel camera (Mati → Bahu → Penonton for spectators). */
	public static final KeyMapping CAMERA = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.kultivator.duel_camera", InputConstants.Type.KEYBOARD, UNBOUND, Keys.CAST.getCategory()));

	/** Loads the class (registers both mappings) from {@code DuelClient.init}. */
	static void init() {
	}

	/** True while the parry key is held (an unbound key is never down). */
	public static boolean parryDown() {
		return !PARRY.isUnbound() && PARRY.isDown();
	}

	private DuelKeys() {
	}
}
