package dev.kultivator.duel.ai;

import dev.kultivator.duel.clash.ClashTiming;
import dev.kultivator.duel.clash.SwingDir;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a duelist has seen of its opponent (DESIGN 35.7.3), only what a player would see too: the start of each windup,
 * its kind, direction and weapon, the perilous mark, the heavy charge pose, distances and the posture bars. Never the plan
 * of a feint (that is learned only when the feint is cancelled), never inputs the server has not received, never future
 * randomness. From the last 12 inputs it keeps four habits:
 * <ul>
 * <li>deflect timing: how early the opponent presses before the AI's impacts (too early → the AI holds its heavy cuts
 * 2-4 ticks longer);</li>
 * <li>favourite direction (the AI prepares its guard for it);</li>
 * <li>"always answers a deflect with a light cut" (the AI feints after its own deflect);</li>
 * <li>deflect spam (the AI waits for the spam lock and then strikes).</li>
 * </ul>
 * Only {@code SANTO_PEDANG} uses all four, {@code PENIPU} the direction and the spam; the others read nothing.
 */
public final class AttackReader {

	private static final int MEMORY = 12;

	/** Opponent inputs seen (kind codes of {@code DuelIds.IN_*}) with their tick. */
	private final ArrayDeque<long[]> inputs = new ArrayDeque<>();
	/** {@code impact − press} of the opponent's presses against the AI's swings. */
	private final ArrayDeque<Integer> pressLeads = new ArrayDeque<>();
	private final Map<SwingDir, Integer> directions = new EnumMap<>(SwingDir.class);
	private final ArrayDeque<SwingDir> lastDirs = new ArrayDeque<>();
	/** The last presses: true when one met nothing (spam). */
	private final ArrayDeque<Boolean> pressEmpty = new ArrayDeque<>();
	/** After the opponent's deflect: did it swing within 10 ticks? */
	private int ripostes;
	private int deflects;
	private long lastOpponentDeflect = Long.MIN_VALUE / 4;

	/** The opponent began a swing in {@code dir}. */
	void swing(SwingDir dir, long now) {
		note(0, now);
		directions.merge(dir, 1, Integer::sum);
		lastDirs.addLast(dir);
		while (lastDirs.size() > MEMORY) {
			SwingDir old = lastDirs.removeFirst();
			directions.computeIfPresent(old, (key, count) -> count > 1 ? count - 1 : null);
		}
		if (now - lastOpponentDeflect <= 10L) {
			ripostes++;
			lastOpponentDeflect = Long.MIN_VALUE / 4;
		}
	}

	/** The opponent pressed deflect; {@code lead} = the AI's live impact − now, or −1 when nothing was coming. */
	void press(int lead, long now) {
		note(3, now);
		boolean empty = lead < 0 || lead > ClashTiming.PARRY_WINDOW + 4;
		pressEmpty.addLast(empty);
		while (pressEmpty.size() > MEMORY) {
			pressEmpty.removeFirst();
		}
		if (!empty) {
			pressLeads.addLast(lead);
			while (pressLeads.size() > MEMORY) {
				pressLeads.removeFirst();
			}
		}
	}

	/** The opponent deflected one of the AI's swings. */
	void opponentDeflected(long now) {
		deflects++;
		lastOpponentDeflect = now;
		if (deflects > MEMORY) {
			deflects = MEMORY;
			ripostes = Math.max(0, ripostes - 1);
		}
	}

	private void note(int kind, long now) {
		inputs.addLast(new long[] {kind, now});
		while (inputs.size() > MEMORY) {
			inputs.removeFirst();
		}
	}

	/** True when the opponent presses deflect clearly too early (average lead above 3 ticks over 4+ presses). */
	public boolean pressesEarly() {
		if (pressLeads.size() < 4) {
			return false;
		}
		int sum = 0;
		for (int lead : pressLeads) {
			sum += lead;
		}
		return sum / (float) pressLeads.size() > 3.0F;
	}

	/** The opponent's most frequent direction of the last 12 swings (null with fewer than 4 or no clear favourite). */
	public SwingDir favouriteDir() {
		if (lastDirs.size() < 4) {
			return null;
		}
		SwingDir best = null;
		int bestCount = 0;
		for (Map.Entry<SwingDir, Integer> entry : directions.entrySet()) {
			if (entry.getValue() > bestCount) {
				bestCount = entry.getValue();
				best = entry.getKey();
			}
		}
		return bestCount * 2 >= lastDirs.size() ? best : null;
	}

	/** True when the opponent nearly always answers its own deflect with a cut (3 of the last 4 or more). */
	public boolean ripostesAfterDeflect() {
		return deflects >= 4 && ripostes * 4 >= deflects * 3;
	}

	/** True when at least a third of the opponent's last presses met nothing (deflect spam). */
	public boolean spams() {
		if (pressEmpty.size() < 4) {
			return false;
		}
		int empty = 0;
		for (boolean value : pressEmpty) {
			if (value) {
				empty++;
			}
		}
		return empty * 3 >= pressEmpty.size();
	}

	/** For {@code uji duel info}. */
	public String summary() {
		return "early=" + pressesEarly() + " fav=" + favouriteDir() + " riposte=" + ripostesAfterDeflect() + " spam=" + spams();
	}
}
