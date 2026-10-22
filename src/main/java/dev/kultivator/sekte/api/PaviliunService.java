package dev.kultivator.sekte.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Module A, Paviliun Kitab &amp; Pemakaman / Altar Leluhur (DESIGN 36.2.5, contract C36.A1). The default has no
 * pavilion study and no graves.
 */
public interface PaviliunService {

	/** One fallen disciple honoured at the cemetery (kept in A's hall section, at most {@code maxGraves}). */
	record Makam(UUID soul, Component name, String realm, long day, String causeKey, int deeds, boolean enshrined) {
	}

	PaviliunService NONE = new PaviliunService() {
	};

	/** The technique id the disciple is studying in the pavilion, or null. */
	default @Nullable String studying(UUID owner, UUID soul) {
		return null;
	}

	/** The owner's graves, newest first. */
	default List<Makam> graves(UUID owner) {
		return List.of();
	}

	/**
	 * The ancestral blessing 0-0.15 from the altar (tablets enshrined and the last rite's freshness); E's heir ritual and
	 * P's contentment read it.
	 */
	default double blessing(UUID owner) {
		return 0.0D;
	}
}
