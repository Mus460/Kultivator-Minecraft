package dev.kultivator.sekte.api;

import java.util.UUID;

/**
 * Module W, Kebun Obat &amp; Tambang Roh (DESIGN 36.2.10, contract C36.W1): the spirit-herb plots and the spirit mine,
 * worked by disciples as data (no crop or ore block is ever changed). The default has neither.
 */
public interface KebunService {

	KebunService NONE = new KebunService() {
	};

	/** Spirit-herb plots built (0 none). */
	default int plots(UUID owner) {
		return 0;
	}

	/** The spirit mine's depth level 0-9 (0 not built). */
	default int mineLevel(UUID owner) {
		return 0;
	}

	/** True while the soul works a garden or mine shift. */
	default boolean working(UUID owner, UUID soul) {
		return false;
	}
}
