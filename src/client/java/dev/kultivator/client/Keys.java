package dev.kultivator.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.hub.HubNav;
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
 * The mod's keys, in their own "Kultivator" section of the controls. Since UI/UX Terpadu (DESIGN 33.4) every frequent key
 * sits beside WASD: G cast (hold: Roda Segel), V Akses Cepat (tap: the jade-scroll hub, hold: the quick wheel), Z next
 * technique, B meditate, H flying sword, M Murid. The far keys of before ({@code ; ' , . \ -}) are gone as defaults:
 * {@code info} and {@code previous} are unbound (everything they did is reachable through V and the Gulungan), and
 * {@code client.hub.KeyLayout} moves the saved keys of players who never rebound them (33.4.3). Since the Gulungan Utama
 * (DESIGN 43.10.3) the section keys K (Kodeks), J (Panduan) and N (Peta, registered by {@code PetaClient}) open the
 * Gulungan there; {@code KeyLayout} step 43 binds them for saved options only where the key is still free. The checked
 * clashes (33.1): vanilla Q E F T Tab X C O P L, Combat Roll R, Iris R K I (K: the Iris shader toggle; a player with
 * Iris keeps whichever they bind); G is also vanilla's quick actions (a server dialog only). Jumping in mid-air asks for
 * a Cloud Step. The cast, wheel, quick seal and cycle keys are read by {@code client.codex.CodexClient}, the quick key by
 * {@code client.hub.QuickWheel}; this class handles the rest, the section keys included.
 */
public final class Keys {

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Kultivator.id("main"));
	private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

	public static final KeyMapping CAST = key("cast", InputConstants.KEY_G);
	/** Akses Cepat (DESIGN 33.3.1): a tap opens the hub on its last section, holding opens the quick wheel. */
	public static final KeyMapping QUICK = key("quick", InputConstants.KEY_V);
	public static final KeyMapping MEDITATE = key("meditate", InputConstants.KEY_B);
	/** The hub without the wheel, for players who want a plain toggle (unbound since 33.4; was {@code '}). */
	public static final KeyMapping INFO = key("info", UNBOUND);
	/** Unbound since 33.4 (was {@code ,}): the Roda Segel picks directly; a mouse side button is a good choice. */
	public static final KeyMapping PREVIOUS = key("previous", UNBOUND);
	public static final KeyMapping NEXT = key("next", InputConstants.KEY_Z);
	public static final KeyMapping FLYING_SWORD = key("flying_sword", InputConstants.KEY_H);
	/** Teknik › Kodeks (DESIGN 43.10.3: K since 43; unbound 33.4-42, was -). */
	public static final KeyMapping CODEX = key("codex", InputConstants.KEY_K);
	public static final KeyMapping WHEEL = key("wheel", UNBOUND);
	public static final KeyMapping SEAL_1 = key("seal_1", UNBOUND);
	public static final KeyMapping SEAL_2 = key("seal_2", UNBOUND);
	public static final KeyMapping SEAL_3 = key("seal_3", UNBOUND);
	public static final KeyMapping SEAL_4 = key("seal_4", UNBOUND);
	// Phase 8 (DESIGN 25.2 item 18): unbound by default (the hub's Dunia section and wheel slice 6 reach it).
	/** Opens or closes the Dunia section of the Gulungan. */
	public static final KeyMapping DUNIA = key("dunia", UNBOUND);
	/** Buka Cincin: opens the highest-tier bound ring in the inventory ({@code TEMPA "ring:open"}). */
	public static final KeyMapping CINCIN = key("cincin", UNBOUND);
	/** Murid › Murid on the disciple you look at (else the nearest); pressed again while it shows, it closes the Gulungan (R24). */
	public static final KeyMapping MURID = key("murid", InputConstants.KEY_M);
	// Phase 9 (DESIGN 26.14 item 18, 26.10): unbound by default (hub section 6, wheel slice 7).
	/** Opens or closes the Keabadian section of the Gulungan. */
	public static final KeyMapping ABADI = key("abadi", UNBOUND);
	/** Panduan › Kitab (DESIGN 43.10.3: J since 43, unbound before); pressed again while shown it closes the Gulungan. */
	public static final KeyMapping GUIDE = key("guide", InputConstants.KEY_J);

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
				HubNav.buka(dev.kultivator.client.hub.Tujuan.bagian(HubNav.lastSection()));
			}
		}
		press(MEDITATE, free, Actions.MEDITATE_TOGGLE);
		// The section keys (DESIGN 43.4.2, 43.10.3): K Kodeks, M Murid, J Panduan, N Peta, dunia, abadi open the Gulungan
		// there. This tick runs before the module ticks (Keys.init is first), so the old per-module handlers see no click.
		for (KeyMapping key : new KeyMapping[]{CODEX, MURID, GUIDE, dev.kultivator.client.dunia.peta.PetaClient.PETA, DUNIA, ABADI}) {
			while (key.consumeClick()) {
				if (free) {
					HubNav.bukaKunci(key);
				}
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
