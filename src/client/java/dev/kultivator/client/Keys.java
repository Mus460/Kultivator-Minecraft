package dev.kultivator.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Technique;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The mod's keys, in their own "Kultivator" section of the controls. Defaults avoid vanilla keys, Butter Bot's keys
 * and Iris (K, O, R): G cast, ; meditate, ' info screen, , and . choose the technique, \ flying sword, - the Kitab
 * Teknik; the seal wheel key and the four quick seal keys are unbound (DESIGN 22.12.7). Jumping in mid-air asks for a
 * Cloud Step. The cast, wheel, quick seal, cycle and codex keys are read by {@code client.codex.CodexClient} (tap or
 * hold, the loadout carousel); this class handles the rest.
 */
public final class Keys {

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Kultivator.id("main"));

	public static final KeyMapping CAST = key("cast", InputConstants.KEY_G);
	public static final KeyMapping MEDITATE = key("meditate", InputConstants.KEY_SEMICOLON);
	public static final KeyMapping INFO = key("info", InputConstants.KEY_APOSTROPHE);
	public static final KeyMapping PREVIOUS = key("previous", InputConstants.KEY_COMMA);
	public static final KeyMapping NEXT = key("next", InputConstants.KEY_PERIOD);
	public static final KeyMapping FLYING_SWORD = key("flying_sword", InputConstants.KEY_BACKSLASH);
	// Phase 6c (DESIGN 22.12.1, 22.12.7).
	public static final KeyMapping CODEX = key("codex", InputConstants.KEY_MINUS);
	public static final KeyMapping WHEEL = key("wheel", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping SEAL_1 = key("seal_1", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping SEAL_2 = key("seal_2", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping SEAL_3 = key("seal_3", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping SEAL_4 = key("seal_4", InputConstants.UNKNOWN.getValue());
	// Phase 8 (DESIGN 25.2 item 18): unbound by default.
	/** Opens or closes the Dunia screen. */
	public static final KeyMapping DUNIA = key("dunia", InputConstants.UNKNOWN.getValue());
	/** Buka Cincin: opens the highest-tier bound ring in the inventory ({@code TEMPA "ring:open"}). */
	public static final KeyMapping CINCIN = key("cincin", InputConstants.UNKNOWN.getValue());
	/** Opens the scroll of the disciple you look at (else the nearest); pressed again inside it, the next disciple. */
	public static final KeyMapping MURID = key("murid", InputConstants.KEY_M);
	// Phase 9 (DESIGN 26.14 item 18, 26.10): unbound by default.
	/** Opens or closes the Keabadian screen. */
	public static final KeyMapping ABADI = key("abadi", InputConstants.UNKNOWN.getValue());
	// Kitab Panduan (the in-game guide): unbound by default.
	/** Opens or closes the Kitab Panduan; read by {@code client.guide.GuideClient} and the guide screen. */
	public static final KeyMapping GUIDE = key("guide", InputConstants.UNKNOWN.getValue());

	private static boolean jumpWasDown;
	private static int airTicks;

	private static KeyMapping key(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.kultivator." + name, InputConstants.Type.KEYBOARD, defaultKey, CATEGORY));
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
	}

	/** True when the server has the mod and we are in a world. */
	public static boolean connected() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.getConnection() != null && ClientPlayNetworking.canSend(Payloads.Action.TYPE);
	}

	public static void send(Payloads.Action action) {
		if (connected()) {
			ClientPlayNetworking.send(action);
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean connected = connected();
		boolean free = connected && minecraft.gui.screen() == null;
		while (INFO.consumeClick()) {
			if (free) {
				minecraft.gui.setScreen(new CultivationScreen(0));
			}
		}
		press(MEDITATE, free, Actions.MEDITATE_TOGGLE);
		while (DUNIA.consumeClick()) {
			if (free) {
				dev.kultivator.client.dunia.DuniaClient.open(Payloads.OpenScreen.DUNIA_ALAM);
			}
		}
		while (ABADI.consumeClick()) {
			if (free) {
				dev.kultivator.client.dunia.DuniaClient.openAbadi(Payloads.OpenScreen.ABADI_ALAM);
			}
		}
		while (MURID.consumeClick()) {
			if (free) {
				send(new Payloads.Action(Actions.DISCIPLE_OPEN, -1, ""));
			}
		}
		while (CINCIN.consumeClick()) {
			if (free) {
				send(new Payloads.Action(Actions.TEMPA, 0, "ring:open"));
			}
		}
		press(FLYING_SWORD, free, Actions.FLYING_SWORD_TOGGLE);
		tickCloudStep(minecraft, player, free);
	}

	private static void press(KeyMapping key, boolean free, int action) {
		while (key.consumeClick()) {
			if (free) {
				send(Payloads.Action.of(action));
			}
		}
	}

	/** A fresh jump press while airborne (not flying, not in water) is a Cloud Step request. */
	private static void tickCloudStep(Minecraft minecraft, LocalPlayer player, boolean free) {
		if (player == null) {
			jumpWasDown = false;
			return;
		}
		boolean jumpDown = minecraft.options.keyJump.isDown();
		airTicks = player.onGround() ? 0 : airTicks + 1;
		CultivationData data = ClientCultivation.data();
		boolean canStep = free && data.knows(Technique.CLOUD_STEP) && !player.getAbilities().flying && !player.isInWater()
				&& !player.isPassenger() && !player.onClimbable() && !player.isFallFlying();
		if (jumpDown && !jumpWasDown && canStep && airTicks > 2) {
			send(Payloads.Action.of(Actions.CLOUD_STEP));
		}
		jumpWasDown = jumpDown;
	}

	private Keys() {
	}
}
