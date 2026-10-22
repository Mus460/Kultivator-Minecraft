package dev.kultivator.sekte.api;

import java.util.UUID;

/**
 * Module S, Seragam, Lambang &amp; Bendera (DESIGN 36.2.8, contract C36.S1): the hall's emblem and its uniform colours
 * per sect rank. Server side only; S syncs them to clients itself. The default is {@link Lambang#DEFAULT} and the
 * existing robe colours (S returning -1 means "keep the existing tint").
 */
public interface LambangService {

	LambangService NONE = new LambangService() {
	};

	default Lambang emblem(UUID owner) {
		return Lambang.DEFAULT;
	}

	/** The robe body colour (RGB) for sect rank 0-5, or -1 to keep the Dao tint. */
	default int robeColor(UUID owner, int sectRank) {
		return -1;
	}

	/** The robe trim colour (RGB) for sect rank 0-5, or -1 to keep {@code SectRanks.TRIM}. */
	default int trimColor(UUID owner, int sectRank) {
		return -1;
	}

	/** The sect's motto (plain text, at most 32 chars), or "". */
	default String motto(UUID owner) {
		return "";
	}
}
