package dev.kultivator.formasi.core;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Core states (DESIGN 24.2.5), derived each pulse in this priority order. Labels:
 * {@code kultivator.formasi.state.<name>} (module A's text). Synced by ordinal; never reorder.
 */
public enum CoreState {
	DORMANT,
	BROKEN,
	SUSPENDED,
	ACTIVE,
	POWERED,
	KEPT;

	/** True when the handler pulses (Berdaya or Dijaga). */
	public boolean running() {
		return this == POWERED || this == KEPT;
	}

	public static CoreState byOrdinal(int ordinal) {
		CoreState[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DORMANT;
	}

	public Component label() {
		return Component.translatable("kultivator.formasi.state." + name().toLowerCase(Locale.ROOT));
	}
}
