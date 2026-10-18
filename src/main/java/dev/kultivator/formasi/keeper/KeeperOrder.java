package dev.kultivator.formasi.keeper;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Keeper orders (DESIGN 24.2.8). Labels: {@code kultivator.formasi.order.<name>} (module A's text). Saved by name. */
public enum KeeperOrder {
	JAGA,
	PERKUAT,
	WASPADA;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component label() {
		return Component.translatable("kultivator.formasi.order." + id());
	}

	/** The next order of the order coin (Jaga → Perkuat → Waspada → Jaga). */
	public KeeperOrder next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static @Nullable KeeperOrder byId(String id) {
		for (KeeperOrder order : values()) {
			if (order.id().equals(id)) {
				return order;
			}
		}
		return null;
	}
}
