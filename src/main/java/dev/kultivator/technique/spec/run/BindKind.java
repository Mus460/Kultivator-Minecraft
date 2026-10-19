package dev.kultivator.technique.spec.run;

import dev.kultivator.technique.spec.dsl.Payload;

/**
 * How a bound entity looks on the client (DESIGN 27.4, {@code Payloads.BindSync}): vines, ropes, chains, a seal ring, a
 * frost shell, a time stop, a suspension, sleep or a karmic link. This is the visual kind only; the gameplay bind is
 * {@link Payload.BindKind}. The ordinal is sent, so never reorder; new kinds go at the end.
 */
public enum BindKind {
	ROOT_VINE,
	ROPE,
	CHAINS,
	SEAL,
	FREEZE,
	TIME_STOP,
	SUSPEND,
	SLEEP,
	KARMA_LINK;

	private static final BindKind[] VALUES = values();

	public static BindKind byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? CHAINS : VALUES[ordinal];
	}

	/** The default visual kind of a gameplay bind (ROOT vines, STUN chains, SUSPEND suspension, SLEEP sleep). */
	public static BindKind of(Payload.BindKind kind) {
		return switch (kind) {
			case ROOT -> ROOT_VINE;
			case STUN -> CHAINS;
			case SUSPEND -> SUSPEND;
			case SLEEP -> SLEEP;
		};
	}
}
