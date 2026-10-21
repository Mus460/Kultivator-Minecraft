package dev.kultivator.client.hub;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The one-time key move of UI/UX Terpadu (DESIGN 33.4.3). Minecraft saves every mapping to {@code options.txt}, so new
 * defaults alone never reach a player who launched before. Once ({@code key_layout} below 2 in
 * {@code config/kultivator-client.json}), on {@code CLIENT_STARTED}: every moved mapping still on its old default goes to
 * its new default; a key the player changed stays theirs. A fresh install already has the new defaults (nothing moves).
 * After a move, one chat line on the first world join names the new keys (the live ones).
 */
public final class KeyLayout {

	public static final String CONFIG_KEY = "key_layout";
	public static final int LAYOUT = 2;

	private static boolean notice;

	/** Registers the start and join hooks (called once from {@code KultivatorClient}). */
	public static void init() {
		ClientLifecycleEvents.CLIENT_STARTED.register(KeyLayout::migrate);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(KeyLayout::tellOnce));
	}

	private static void migrate(Minecraft minecraft) {
		try {
			JsonElement stored = EffectQuality.get(CONFIG_KEY);
			int layout = stored != null && stored.isJsonPrimitive() && stored.getAsJsonPrimitive().isNumber() ? stored.getAsInt() : 0;
			if (layout >= LAYOUT) {
				return;
			}
			boolean moved = false;
			moved |= move(Keys.MEDITATE, InputConstants.KEY_SEMICOLON);
			moved |= move(Keys.INFO, InputConstants.KEY_APOSTROPHE);
			moved |= move(Keys.PREVIOUS, InputConstants.KEY_COMMA);
			moved |= move(Keys.NEXT, InputConstants.KEY_PERIOD);
			moved |= move(Keys.FLYING_SWORD, InputConstants.KEY_BACKSLASH);
			moved |= move(Keys.CODEX, InputConstants.KEY_MINUS);
			if (moved) {
				KeyMapping.resetMapping();
				minecraft.options.save();
				notice = true;
			}
			EffectQuality.put(CONFIG_KEY, new JsonPrimitive(LAYOUT));
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: moving the saved keys failed", exception);
		}
	}

	/** Moves {@code key} to its new default when it is still on the old default {@code oldDefault}. */
	private static boolean move(KeyMapping key, int oldDefault) {
		String old = InputConstants.Type.KEYBOARD.getOrCreate(oldDefault).getName();
		if (!key.saveString().equals(old) || key.isDefault()) {
			return false;
		}
		key.setKey(key.getDefaultKey());
		return true;
	}

	private static void tellOnce() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!notice || minecraft.player == null) {
			return;
		}
		notice = false;
		minecraft.player.sendSystemMessage(Component.translatable("kultivator.hub.keys_moved", Keys.CAST.getTranslatedKeyMessage(),
				Keys.QUICK.getTranslatedKeyMessage(), Keys.NEXT.getTranslatedKeyMessage(), Keys.MEDITATE.getTranslatedKeyMessage(),
				Keys.FLYING_SWORD.getTranslatedKeyMessage()).withStyle(ChatFormatting.GOLD));
	}

	private KeyLayout() {
	}
}
