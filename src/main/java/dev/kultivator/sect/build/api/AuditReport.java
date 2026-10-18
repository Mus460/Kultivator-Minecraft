package dev.kultivator.sect.build.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The result of {@code uji sekte audit} (DESIGN 21.22): the dry run of the (template, tier) emissions of one template or
 * of all nine ({@code template} null). {@code lines} are the readable findings (at most a screenful, the full list goes
 * to the server log); {@code done} is false while the off-thread run is still going or when no engine is registered.
 */
public record AuditReport(@Nullable Integer template, int emissions, int violations, List<Component> lines, boolean done, long millis) {

	public AuditReport {
		lines = lines == null ? List.of() : List.copyOf(lines);
	}

	/** Finished with zero violations. */
	public boolean ok() {
		return done && violations == 0;
	}

	/** The report when no construction engine is registered. */
	public static AuditReport unavailable(@Nullable Integer template) {
		return new AuditReport(template, 0, 0, List.of(Component.translatable(EngineApi.UNAVAILABLE_KEY)), false, 0L);
	}
}
