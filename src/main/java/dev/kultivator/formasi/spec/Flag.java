package dev.kultivator.formasi.spec;

/** Spec flags (DESIGN 24.2.2). {@code STONE_ONLY} is set automatically for Bumi and up. */
public enum Flag {
	/** Matched only in true compass orientation (M1, B2, L1, A1). */
	FIXED_ORIENTATION,
	/** Burns spirit stones only. */
	STONE_ONLY,
	/** Pauses while a member's tribulation runs inside. */
	PAUSE_IN_TRIBULATION,
	/** Never built as a 6b pattern component. */
	NO_6B,
	/** May be registered outside every hall territory (personal, K1, K2, K4, M4). */
	PERSONAL_OK,
	/** A read-only descriptor of an old formation (Kitab and hologram only). */
	LEGACY_MIRROR
}
