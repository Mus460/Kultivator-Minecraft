package dev.kultivator.technique.spec;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Damage one cast dealt to each target (DESIGN 22.5.5, 22.14.2): the cumulative boss cap, the own heart demon's 12% per
 * cast and the arena's 15% per cast read it. ECHO, LINK, REFLECT, COUNTER, DOT, phantoms, pulses and replays share the
 * ledger of the cast that started them. Session only; one per cast.
 */
public final class CastLedger {

	private final Map<UUID, Double> dealt = new HashMap<>();

	/** Damage already dealt to {@code target} by this cast (0 when none). */
	public double dealt(UUID target) {
		Double value = dealt.get(target);
		return value == null ? 0.0D : value;
	}

	public void add(UUID target, double amount) {
		if (amount > 0.0D && Double.isFinite(amount)) {
			dealt.merge(target, amount, Double::sum);
		}
	}

	public int targets() {
		return dealt.size();
	}

	public void clear() {
		dealt.clear();
	}
}
