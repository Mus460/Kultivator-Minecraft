package dev.kultivator.util;

import dev.kultivator.Kultivator;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Grants the mod's advancements (the "Kultivasi" tab). Every advancement file under
 * {@code data/kultivator/advancement/} has one {@code minecraft:impossible} criterion named {@code "done"}.
 */
public final class Advancements {

	/** Told about every advancement that {@link #grant} really completed (DESIGN 19.2.4); the name is without namespace. */
	private static final List<BiConsumer<ServerPlayer, String>> LISTENERS = new CopyOnWriteArrayList<>();

	/** Grants {@code kultivator:<name>}; does nothing if it is unknown or already done. */
	public static void grant(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Kultivator.id(name));
		if (holder == null) {
			return;
		}
		PlayerAdvancements advancements = player.getAdvancements();
		boolean wasDone = advancements.getOrStartProgress(holder).isDone();
		advancements.award(holder, "done");
		if (wasDone || !advancements.getOrStartProgress(holder).isDone()) {
			return;
		}
		for (BiConsumer<ServerPlayer, String> listener : LISTENERS) {
			try {
				listener.accept(player, name);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Advancement listener failed for {}", name, exception);
			}
		}
	}

	/**
	 * Calls {@code listener} with the player and the advancement name (e.g. {@code "first_meditation"}) each time
	 * {@link #grant} completes an advancement that was not done before (DESIGN 19.2.4). Register from a module's
	 * {@code init()}.
	 */
	public static void addListener(BiConsumer<ServerPlayer, String> listener) {
		LISTENERS.add(listener);
	}

	private Advancements() {
	}
}
