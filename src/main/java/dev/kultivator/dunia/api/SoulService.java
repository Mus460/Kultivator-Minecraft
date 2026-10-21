package dev.kultivator.dunia.api;

import net.minecraft.server.level.ServerPlayer;

/** Module D's soul journey (DESIGN 34.5.13, contract C34.D1). The default: nobody is a soul. */
public interface SoulService {

	SoulService NONE = new SoulService() {
	};

	/** True while {@code player} walks the Dunia Bawah as a soul (their body is a mannequin). */
	default boolean isSoul(ServerPlayer player) {
		return false;
	}
}
