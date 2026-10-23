package dev.kultivator.sekte.langit.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The stable site keys of Sekte Langit (DESIGN 37.3.5, 37.3.6): a tethered sky site of path A lives in the engine's
 * {@code skies} map under {@link #skyKey}; after the Ascension the old ground site (Kaki Gunung) lives in the orphans
 * under {@link #footKey}. Both derive from the owner, so they survive restarts and never collide with a player UUID
 * (name-based UUIDs are version 3, player UUIDs version 4).
 */
public final class LangitKeys {

	public static UUID skyKey(UUID owner) {
		return UUID.nameUUIDFromBytes(("kultivator:langit:" + owner).getBytes(StandardCharsets.UTF_8));
	}

	public static UUID footKey(UUID owner) {
		return UUID.nameUUIDFromBytes(("kultivator:kaki:" + owner).getBytes(StandardCharsets.UTF_8));
	}

	private LangitKeys() {
	}
}
