package dev.kultivator.dao;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import net.minecraft.server.level.ServerPlayer;

/** STUB — owned by P2-dao. Dao comprehension gains (DESIGN 7, 15). */
public final class Comprehension {

	public static void init() {
	}

	/** Adds comprehension to {@code dao} (x1.5 with a matching root element), with level-up messages. */
	public static void add(ServerPlayer player, Dao dao, double amount) {
	}

	/** After a successful cast. */
	public static void onTechniqueUsed(ServerPlayer player, Technique technique) {
	}

	private Comprehension() {
	}
}
