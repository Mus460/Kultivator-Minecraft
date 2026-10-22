package dev.kultivator.sekte.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The end of one sect tournament (DESIGN 36.2.3, module T, carried by {@code SekteBus.TOURNAMENT_ENDED}).
 *
 * @param season   the hall's tournament count (1-based)
 * @param kind     {@code biasa}, {@code elit} (the existing Turnamen Elit) or {@code antar_sekte} (hosted for module D)
 * @param champion the champion's soul, or null when a visitor won
 * @param placings the souls of the hall's own entrants, best first
 * @param upsets   pairs {@code [winner, loser]} where the lower realm won (R and P read them)
 * @param guestSect the NPC sect that sent the visitors ({@code antar_sekte}), or null
 * @param guestWon  true when a visitor took the title
 */
public record TurnamenHasil(int season, String kind, @Nullable UUID champion, List<UUID> placings, List<UUID[]> upsets,
		@Nullable String guestSect, boolean guestWon) {

	public TurnamenHasil {
		kind = kind == null ? "biasa" : kind;
		placings = placings == null ? List.of() : List.copyOf(placings);
		upsets = upsets == null ? List.of() : List.copyOf(upsets);
	}
}
