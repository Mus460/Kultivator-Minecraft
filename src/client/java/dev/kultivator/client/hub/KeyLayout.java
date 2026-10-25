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
 * The one-time key moves (DESIGN 33.4.3, 43.10.3). Minecraft saves every mapping to {@code options.txt}, so new defaults
 * alone never reach a player who launched before. On {@code CLIENT_STARTED}, by the {@code key_layout} step stored in
 * {@code config/kultivator-client.json}:
 * <ul>
 * <li>below 2 (UI/UX Terpadu): every moved mapping still on its old default goes to its new default; a key the player
 * changed stays theirs;</li>
 * <li>below 43 (Gulungan Utama): the section keys K Kitab Teknik, J Panduan and N Peta are bound only where the mapping is
 * still unbound and no other mapping uses that key.</li>
 * </ul>
 * After a move, one chat line on the first world join names the new keys (the live ones).
 */
public final class KeyLayout {

	public static final String CONFIG_KEY = "key_layout";
	public static final int LAYOUT = 43;
	private static final int LAYOUT_33 = 2;

	private static boolean notice33;
	private static boolean notice43;

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
			if (layout < LAYOUT_33) {
				boolean moved33 = move(Keys.MEDITATE, InputConstants.KEY_SEMICOLON);
				moved33 |= move(Keys.INFO, InputConstants.KEY_APOSTROPHE);
				moved33 |= move(Keys.PREVIOUS, InputConstants.KEY_COMMA);
				moved33 |= move(Keys.NEXT, InputConstants.KEY_PERIOD);
				moved33 |= move(Keys.FLYING_SWORD, InputConstants.KEY_BACKSLASH);
				notice33 = moved33;
				moved = moved33;
			}
			boolean bound43 = bindIfFree(minecraft, Keys.CODEX, InputConstants.KEY_K);
			bound43 |= bindIfFree(minecraft, Keys.GUIDE, InputConstants.KEY_J);
			bound43 |= bindIfFree(minecraft, dev.kultivator.client.dunia.peta.PetaClient.PETA, InputConstants.KEY_N);
			notice43 = bound43;
			if (moved || bound43) {
				KeyMapping.resetMapping();
				minecraft.options.save();
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

	/** Binds {@code key} to {@code code} when it is unbound and no other mapping (vanilla or mod) uses that key. */
	private static boolean bindIfFree(Minecraft minecraft, KeyMapping key, int code) {
		if (!key.isUnbound()) {
			return false;
		}
		String name = InputConstants.Type.KEYBOARD.getOrCreate(code).getName();
		for (KeyMapping other : minecraft.options.keyMappings) {
			if (other != key && name.equals(other.saveString())) {
				return false;
			}
		}
		key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(code));
		return true;
	}

	private static void tellOnce() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (notice33) {
			notice33 = false;
			minecraft.player.sendSystemMessage(Component.translatable("kultivator.hub.keys_moved", Keys.CAST.getTranslatedKeyMessage(),
					Keys.QUICK.getTranslatedKeyMessage(), Keys.NEXT.getTranslatedKeyMessage(), Keys.MEDITATE.getTranslatedKeyMessage(),
					Keys.FLYING_SWORD.getTranslatedKeyMessage()).withStyle(ChatFormatting.GOLD));
		}
		if (notice43) {
			notice43 = false;
			minecraft.player.sendSystemMessage(Component.translatable("kultivator.gui43.host.tombol_baru", Keys.CODEX.getTranslatedKeyMessage(),
					Keys.GUIDE.getTranslatedKeyMessage(), dev.kultivator.client.dunia.peta.PetaClient.PETA.getTranslatedKeyMessage())
					.withStyle(ChatFormatting.GOLD));
		}
	}

	private KeyLayout() {
	}
}
