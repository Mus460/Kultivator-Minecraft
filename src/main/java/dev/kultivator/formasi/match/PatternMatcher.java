package dev.kultivator.formasi.match;

import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Rule;
import dev.kultivator.formasi.spec.Slot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads patterns around an eye (DESIGN 24.2.3). Pure and read-only: only {@code getBlockState}/{@code getFluidState}
 * through {@link ChunkReader}.
 */
public final class PatternMatcher {

	/** Reads every slot of {@code spec} in rotation {@code rot}. */
	public static MatchResult full(ChunkReader reader, BlockPos eye, FormationSpec spec, int rot) {
		List<Slot> slots = spec.pattern().slots(rot);
		byte[] states = new byte[slots.size()];
		int ok = 0;
		int missing = 0;
		int wrong = 0;
		int unknown = 0;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			byte state = reader.read(eye.offset(slot.dx(), slot.dy(), slot.dz()), slot.rule(), rot);
			states[i] = state;
			switch (state) {
				case ChunkReader.OK -> ok++;
				case ChunkReader.MISSING -> missing++;
				case ChunkReader.WRONG -> wrong++;
				default -> unknown++;
			}
		}
		return new MatchResult(spec, rot, slots.size(), ok, missing, wrong, unknown, states);
	}

	/**
	 * Reads slots nearest first until the first one that is not OK or the budget runs out. Returns the number of OK slots
	 * read; equal to the slot count when the pattern is complete. {@code budget[0]} is decreased by the reads.
	 */
	public static int quick(ChunkReader reader, BlockPos eye, FormationSpec spec, int rot, int[] budget) {
		List<Slot> slots = spec.pattern().slots(rot);
		int ok = 0;
		for (Slot slot : slots) {
			if (budget[0] <= 0) {
				return -1;
			}
			budget[0]--;
			if (reader.read(eye.offset(slot.dx(), slot.dy(), slot.dz()), slot.rule(), rot) != ChunkReader.OK) {
				return ok;
			}
			ok++;
		}
		return ok;
	}

	/** The rotations to try: true compass only for FIXED specs, else the distinct ones. */
	public static int rotations(FormationSpec spec) {
		return spec.has(dev.kultivator.formasi.spec.Flag.FIXED_ORIENTATION) ? 1 : spec.pattern().distinctRotations();
	}

	/** Missing or wrong slots grouped by rule label: "Balok Kecubung ×3, Tongkat End ×2 +N lagi". */
	public static Component missingSummary(MatchResult result, int maxGroups) {
		Map<Rule, Integer> counts = new LinkedHashMap<>();
		List<Slot> slots = result.spec().pattern().slots(result.rot());
		for (int i = 0; i < slots.size(); i++) {
			byte state = result.states()[i];
			if (state == ChunkReader.MISSING || state == ChunkReader.WRONG) {
				counts.merge(slots.get(i).rule(), 1, Integer::sum);
			}
		}
		MutableComponent out = Component.empty();
		int shown = 0;
		int more = 0;
		for (Map.Entry<Rule, Integer> entry : counts.entrySet()) {
			if (shown >= maxGroups) {
				more++;
				continue;
			}
			if (shown > 0) {
				out.append(", ");
			}
			out.append(entry.getKey().label().copy()).append(" ×" + entry.getValue());
			shown++;
		}
		if (more > 0) {
			out.append(" ").append(Component.translatable("kultivator.formasi.more", more));
		}
		return out;
	}

	/** Positions of bad slots (wrong first, then missing), at most {@code max}. */
	public static List<BlockPos> badPositions(BlockPos eye, MatchResult result, int max, List<Byte> kinds) {
		List<BlockPos> out = new ArrayList<>();
		List<Slot> slots = result.spec().pattern().slots(result.rot());
		for (byte want : new byte[]{ChunkReader.WRONG, ChunkReader.MISSING}) {
			for (int i = 0; i < slots.size() && out.size() < max; i++) {
				if (result.states()[i] == want) {
					Slot slot = slots.get(i);
					out.add(eye.offset(slot.dx(), slot.dy(), slot.dz()));
					kinds.add(want);
				}
			}
		}
		return out;
	}

	private PatternMatcher() {
	}
}
