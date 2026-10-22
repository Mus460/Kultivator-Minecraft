package dev.kultivator.sekte.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of one outside mission (DESIGN 36.2.7, module X, carried by {@code SekteBus.MISSION_RETURNED}). Item
 * rewards are never carried here (ItemStacks are created by X when it hands them out); only counts and lang keys.
 *
 * @param missionId  X's mission template id ({@code misi.<id>})
 * @param team       the souls that went (the leader first)
 * @param success    0 failed, 1 partial, 2 success, 3 legendary
 * @param recruits   youths brought home as hall candidates ({@code Halls.addCandidates})
 * @param treasures  treasures brought home (rolled by X)
 * @param storyKey   the lang key of the report's story (X's story table)
 * @param storyArgs  plain arguments of the story
 * @param rivalSect  the NPC sect met on the road (module D id), or null
 * @param injured    souls that came back hurt
 */
public record MisiHasil(String missionId, List<UUID> team, int success, int recruits, int treasures, String storyKey,
		List<String> storyArgs, @Nullable String rivalSect, List<UUID> injured) {

	public MisiHasil {
		team = team == null ? List.of() : List.copyOf(team);
		storyArgs = storyArgs == null ? List.of() : List.copyOf(storyArgs);
		injured = injured == null ? List.of() : List.copyOf(injured);
	}
}
