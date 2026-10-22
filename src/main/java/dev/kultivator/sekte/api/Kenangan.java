package dev.kultivator.sekte.api;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One memory of a disciple (DESIGN 36.3.2, kept by the lead's {@code sekte.Ingatan}, at most
 * {@code Ingatan.MAX_MEMORIES} per disciple, oldest weak ones forgotten first).
 *
 * @param kind   {@code <module>.<what>}, e.g. {@code turnamen.juara}, {@code misi.pulang}, {@code kepribadian.dipukul};
 *               the module prefix is the writer ({@code SekteIds.MODULES} or {@code sekte} for the lead)
 * @param day    the in-game day ({@code Ingatan.today(server)}: the overworld clock / 24000) it happened
 * @param other  the other soul involved (a friend, a rival, the master), or null
 * @param weight -10 (a wound that never heals) .. +10 (a moment it lives for); P sums them into contentment drift
 * @param text   the lang key of the line shown in the disciple's scroll ("Ingatan" tab)
 * @param arg    one plain argument of that line (a name, a place, a number), or ""
 */
public record Kenangan(String kind, long day, @Nullable UUID other, int weight, String text, String arg) {

	public Kenangan {
		kind = kind == null ? "" : kind;
		weight = Math.max(-10, Math.min(10, weight));
		text = text == null ? "" : text;
		arg = arg == null ? "" : arg;
	}

	/** The writer module of {@link #kind()} (the part before the first dot). */
	public String module() {
		int dot = kind.indexOf('.');
		return dot < 0 ? kind : kind.substring(0, dot);
	}
}
