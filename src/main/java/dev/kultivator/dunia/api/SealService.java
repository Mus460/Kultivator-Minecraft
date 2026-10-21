package dev.kultivator.dunia.api;

/** Module R's prison seals (DESIGN 34.5.6, contract C34.R1). The default: every seal whole. */
public interface SealService {

	SealService NONE = new SealService() {
	};

	/** The seal integrity 0-100 of the prison site {@code siteId} (100 when unknown). */
	default int integrity(long siteId) {
		return 100;
	}

	default boolean broken(long siteId) {
		return false;
	}
}
