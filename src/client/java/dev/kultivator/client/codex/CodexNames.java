package dev.kultivator.client.codex;

import dev.kultivator.data.TechniqueSpec;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Own names of specs on this client (phase 9, DESIGN 26.14 item 12): the self-created arts {@code own_art_1..3} carry the
 * name the player gave them (P9-batin registers a provider fed from its {@code CH_BATIN} sync). The Kitab cards, the
 * detail slip and the cast carousel show {@link #name(TechniqueSpec)}; lists still sort by spec id. Without a provider
 * every spec keeps its translated name.
 */
public final class CodexNames {

	private static final List<Function<String, @Nullable String>> PROVIDERS = new CopyOnWriteArrayList<>();

	/** Adds a provider: spec id → the own name, or null for none. Register once from a client module's {@code init()}. */
	public static void register(Function<String, @Nullable String> provider) {
		if (provider != null) {
			PROVIDERS.add(provider);
		}
	}

	/** The first provider's own name of {@code specId}, or null. */
	public static @Nullable String lookup(String specId) {
		if (specId == null || PROVIDERS.isEmpty()) {
			return null;
		}
		for (Function<String, @Nullable String> provider : PROVIDERS) {
			try {
				String name = provider.apply(specId);
				if (name != null && !name.isBlank()) {
					return name;
				}
			} catch (RuntimeException exception) {
				return null;
			}
		}
		return null;
	}

	/** The name to show for {@code spec}: its own name as plain text, else its translated name. */
	public static Component name(TechniqueSpec spec) {
		String own = lookup(spec.id());
		return own == null ? spec.displayName() : Component.literal(own);
	}

	private CodexNames() {
	}
}
