package dev.kultivator.client.codex;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Client entry of the Kitab Teknik, the Cincin Segel, the Roda Segel, the quick seals and the codex HUD (DESIGN 22.12):
 * <ul>
 * <li>the cast key: a tap casts the selected art; held ≥ 250 ms it opens the seal wheel and its release chooses (with
 * "Tahan G untuk roda" on and the wheel key unbound; otherwise it casts on press with no delay);</li>
 * <li>the wheel key (unbound by default): hold to open the wheel, release to choose;</li>
 * <li>seal keys 1-4: cast loadout slots 1-4; while the wheel is open, the hotbar keys 1-4 pin the hovered seal instead;</li>
 * <li>previous / next: cycle within the loadout (all castable arts while it is empty) with the 1.2 s carousel;</li>
 * <li>the codex key opens the Kitab Teknik.</li>
 * </ul>
 * While the wheel is open the use key closes it without choosing, the attack key chooses, and any screen closes it.
 */
public final class CodexClient {

	private static boolean castHeld;
	private static long castPressedAt;

	/** Registers keys, HUD tiles and tick hooks (called once from {@code KultivatorClient}). */
	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(CodexClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(CodexClient::endTick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("codex_hud"), new CodexHud());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SealWheel.close(false);
			castHeld = false;
			CodexHud.reset();
		});
	}

	/** Before vanilla's key handling: the wheel takes the hotbar keys 1-4, use and attack. */
	private static void startTick(Minecraft minecraft) {
		if (!SealWheel.isOpen()) {
			return;
		}
		if (minecraft.gui.screen() != null || minecraft.player == null) {
			SealWheel.close(false);
			return;
		}
		KeyMapping[] hotbar = minecraft.options.keyHotbarSlots;
		for (int i = 0; i < 4 && i < hotbar.length; i++) {
			while (hotbar[i].consumeClick()) {
				SealWheel.pin(i);
			}
		}
		boolean cancel = false;
		while (minecraft.options.keyUse.consumeClick()) {
			cancel = true;
		}
		boolean choose = false;
		while (minecraft.options.keyAttack.consumeClick()) {
			choose = true;
		}
		if (cancel) {
			SealWheel.close(false);
			castHeld = false;
		} else if (choose) {
			SealWheel.close(true);
			castHeld = false;
		}
	}

	private static void endTick(Minecraft minecraft) {
		boolean connected = Keys.connected();
		boolean free = connected && minecraft.gui.screen() == null && minecraft.player != null;
		while (Keys.CODEX.consumeClick()) {
			if (free) {
				Codex.open(-1);
			}
		}
		KeyMapping[] seals = {Keys.SEAL_1, Keys.SEAL_2, Keys.SEAL_3, Keys.SEAL_4};
		for (int i = 0; i < seals.length; i++) {
			while (seals[i].consumeClick()) {
				if (free) {
					QuickSeals.cast(i);
				}
			}
		}
		while (Keys.PREVIOUS.consumeClick()) {
			if (free) {
				cycle(-1);
			}
		}
		while (Keys.NEXT.consumeClick()) {
			if (free) {
				cycle(1);
			}
		}
		boolean wheelBound = !Keys.WHEEL.isUnbound();
		while (Keys.WHEEL.consumeClick()) {
			// presses are read from the held state below
		}
		if (wheelBound) {
			boolean down = Keys.WHEEL.isDown();
			if (down && free && !SealWheel.isOpen()) {
				SealWheel.open(false);
			} else if (!down && SealWheel.isOpen() && !SealWheel.fromCast()) {
				SealWheel.close(true);
			}
		}
		tickCast(free, wheelBound);
		if (SealWheel.isOpen() && (minecraft.gui.screen() != null || minecraft.player == null)) {
			SealWheel.close(false);
		}
		CodexHud.tick();
	}

	private static void tickCast(boolean free, boolean wheelBound) {
		int clicks = 0;
		while (Keys.CAST.consumeClick()) {
			clicks++;
		}
		boolean hold = EffectQuality.wheelHold() && !wheelBound;
		if (!hold) {
			castHeld = false;
			if (free && clicks > 0) {
				cast();
			}
			return;
		}
		long now = Util.getMillis();
		boolean down = Keys.CAST.isDown();
		if (clicks > 0 && !castHeld && free) {
			castHeld = true;
			castPressedAt = now;
		}
		if (!castHeld) {
			return;
		}
		if (!free && !SealWheel.isOpen()) {
			castHeld = false;
			return;
		}
		if (!down) {
			castHeld = false;
			if (SealWheel.isOpen() && SealWheel.fromCast()) {
				SealWheel.close(true);
			} else if (now - castPressedAt < SealWheel.HOLD_MILLIS) {
				cast();
			}
		} else if (!SealWheel.isOpen() && now - castPressedAt >= SealWheel.HOLD_MILLIS) {
			SealWheel.open(true);
		}
	}

	/** Casts the selected art. */
	private static void cast() {
		Keys.send(Payloads.Action.of(Actions.CAST));
	}

	/** Selects the previous or next seal of the loadout (all castable arts while it is empty). */
	private static void cycle(int step) {
		List<TechniqueSpec> ring = TeknikSession.ring();
		if (ring.isEmpty()) {
			return;
		}
		String selected = ClientCultivation.data().selectedId();
		int index = -1;
		for (int i = 0; i < ring.size(); i++) {
			if (ring.get(i).id().equals(selected)) {
				index = i;
			}
		}
		int n = ring.size();
		int next = index < 0 ? (step > 0 ? 0 : n - 1) : Math.floorMod(index + step, n);
		TechniqueSpec spec = ring.get(next);
		Keys.send(new Payloads.Action(Actions.SELECT_TECHNIQUE, 0, spec.id()));
		CodexHud.carousel(n > 1 ? ring.get(Math.floorMod(next - 1, n)) : null, spec, n > 2 ? ring.get(Math.floorMod(next + 1, n)) : null);
	}

	/** The spec with {@code id}, or null (for other client classes). */
	static TechniqueSpec spec(String id) {
		return TechniqueRegistry.byId(id);
	}

	private CodexClient() {
	}
}
