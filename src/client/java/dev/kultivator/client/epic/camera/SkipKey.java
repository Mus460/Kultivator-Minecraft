package dev.kultivator.client.epic.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The key "Lewati sinematik" ({@code key.kultivator.skip_scene}, default Backspace, free in vanilla, Iris and Butter Bot;
 * DESIGN 23.2), in the mod's key category (the one {@code Keys} registers). A press ends the current shot (10-tick ease
 * back), clears the card queue and releases the music cue; a second press within 1 s also turns the bars off for the rest
 * of that scene. While a shot runs, any movement, jump, sneak, attack, use, drop, swap-hand, inventory, chat, command or
 * F5 key, a hotbar change and big mouse motion skip it too (23.7.4).
 */
public final class SkipKey {

	/** The category {@code Keys} registers, taken from it (DESIGN 43.10.2: one Kultivator section in the controls). */
	private static final KeyMapping.Category CATEGORY = dev.kultivator.client.Keys.CAST.getCategory();

	public static final KeyMapping SKIP = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.kultivator.skip_scene", InputConstants.Type.KEYBOARD, InputConstants.KEY_BACKSPACE, CATEGORY));

	private static long lastPress = -100L;
	private static long ticks;
	private static int lastSlot = -1;

	/** Loads the class (registers the key) from {@code EpicClient.init}. */
	public static void init() {
	}

	/** The bound key's real name for the skip hint. */
	public static Component keyName() {
		return SKIP.getTranslatedKeyMessage();
	}

	/**
	 * Once per client tick: 1 = a single press, 2 = a double press (bars off for the scene), 0 = nothing. Presses are
	 * consumed even while nothing plays.
	 */
	static int presses() {
		ticks++;
		int result = 0;
		while (SKIP.consumeClick()) {
			result = ticks - lastPress <= 20 ? 2 : Math.max(result, 1);
			lastPress = ticks;
		}
		return result;
	}

	/** True when the player pressed something that ends a running shot. */
	static boolean playerInput() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return false;
		}
		Options o = minecraft.options;
		boolean down = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown() || o.keyJump.isDown()
				|| o.keyShift.isDown() || o.keyAttack.isDown() || o.keyUse.isDown() || o.keyDrop.isDown() || o.keySwapOffhand.isDown()
				|| o.keyInventory.isDown() || o.keyChat.isDown() || o.keyCommand.isDown() || o.keyTogglePerspective.isDown();
		int slot = player.getInventory().getSelectedSlot();
		boolean scrolled = lastSlot >= 0 && slot != lastSlot;
		lastSlot = slot;
		return down || scrolled;
	}

	/** Remembers the hotbar slot when a shot starts (so the first tick never counts as a scroll). */
	static void arm() {
		LocalPlayer player = Minecraft.getInstance().player;
		lastSlot = player == null ? -1 : player.getInventory().getSelectedSlot();
	}

	private SkipKey() {
	}
}
