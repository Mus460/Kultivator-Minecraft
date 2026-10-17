package dev.kultivator.client.disciple.path;

import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

/**
 * Client side of module D's disciple path (DESIGN 20.4.4): the Jalan tab ({@link PathTab}) and the star visuals
 * ({@link PathFx}, driven by {@code FxDispatcher}). Called once from {@code KultivatorClient}. A disciple whose red seal
 * is released (Mode Kehancuran, DESIGN 20.3.21) shows a red seal in front of its name tag.
 */
public final class PathClient {

	/** The red seal's colour on the name tag. */
	private static final int RED_SEAL = 0xC21F1F;

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PathFx.clear());
		DiscipleLook.addNameDecorator((disciple, tag) -> disciple.syncedRole() == DiscipleEntity.Role.DISCIPLE
				&& (disciple.syncedPower() & DisciplePaths.BIT_RELEASED) != 0
				? Component.translatable("kultivator.dpath.tag.released").withColor(RED_SEAL).append(tag) : tag);
	}

	private PathClient() {
	}
}
