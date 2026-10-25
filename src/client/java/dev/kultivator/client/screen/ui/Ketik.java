package dev.kultivator.client.screen.ui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Text input for the mod's own text fields. Since 26.3 (SDL) the window only delivers typed characters while a text
 * input owner is registered with {@code TextInputManager}; vanilla {@code EditBox} does it in {@code setFocused}, our
 * hand-drawn fields did not, so nothing could be typed. Each field reports its focus every frame it is drawn
 * ({@link #fokus}); an owner that stops being drawn (screen closed, page switched) is released a moment later.
 */
public final class Ketik {

	private static final long LEPAS_MS = 300L;
	private static final Map<Object, Long> AKTIF = new WeakHashMap<>();
	private static boolean terdaftar;

	/** Called from a field's draw with its current focus. */
	public static void fokus(Object owner, boolean focused) {
		if (!terdaftar) {
			terdaftar = true;
			ClientTickEvents.END_CLIENT_TICK.register(mc -> lepasYangHilang());
		}
		boolean was = AKTIF.containsKey(owner);
		if (focused) {
			AKTIF.put(owner, Util.getMillis());
			if (!was) {
				Minecraft.getInstance().textInputManager().onTextInputFocusChange(owner, true);
			}
		} else if (was) {
			AKTIF.remove(owner);
			Minecraft.getInstance().textInputManager().onTextInputFocusChange(owner, false);
		}
	}

	private static void lepasYangHilang() {
		if (AKTIF.isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		for (Iterator<Map.Entry<Object, Long>> it = AKTIF.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Object, Long> e = it.next();
			if (now - e.getValue() > LEPAS_MS) {
				Object owner = e.getKey();
				it.remove();
				Minecraft.getInstance().textInputManager().onTextInputFocusChange(owner, false);
			}
		}
	}

	private Ketik() {
	}
}
