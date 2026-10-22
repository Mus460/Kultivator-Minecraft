package dev.kultivator.duel.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The soft integrations of the sword duel on the common side (DESIGN 35.3.14, contract C35.S1 calls {@link #init}):
 * Combat Roll on the server. Better Combat and Player Animation Library are client-only and live in
 * {@code client.duel.compat.BetterCombatBridge} and {@code client.duel.anim.PalBridge}. Everything is reflection; a
 * missing or changed mod only turns its integration off.
 */
public final class DuelCompat {

	public static final String BETTER_COMBAT = "bettercombat";
	public static final String PLAYER_ANIMATION_LIBRARY = "player_animation_library";
	public static final String COMBAT_ROLL = "combat_roll";

	private static boolean done;

	/** Once, from {@code Duel.init}. */
	public static void init() {
		if (done) {
			return;
		}
		done = true;
		if (loaded(COMBAT_ROLL)) {
			CombatRollCompat.init();
		}
	}

	/** True when the mod with this id is loaded (both sides). */
	public static boolean loaded(String modId) {
		try {
			return FabricLoader.getInstance().isModLoaded(modId);
		} catch (RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** True when Better Combat is installed (either side; the server uses it to expect {@code BC_SWING} inputs). */
	public static boolean betterCombat() {
		return loaded(BETTER_COMBAT);
	}

	/** True when Combat Roll's roll listener is live. */
	public static boolean combatRoll() {
		return CombatRollCompat.available();
	}

	private DuelCompat() {
	}
}
