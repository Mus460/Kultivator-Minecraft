package dev.kultivator.formasi.core;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Slot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-memory index of one dimension's real cores (DESIGN 24.2.3), rebuilt when the store loads: chunk → cores (pattern
 * box plus 8, for zone and field lookups), block → slot refs (event re-reads) and eye → core. At most 160 slots × 64
 * cores. Virtual cores have no blocks and are never indexed.
 */
public final class FormationIndex {

	/** Extra blocks around the pattern box that count as "near" an eye (Kehancuran zone, 24.6.4). */
	public static final int MARGIN = 8;

	private final Map<Long, List<Integer>> chunks = new HashMap<>();
	private final Map<Long, List<Long>> slots = new HashMap<>();
	private final Map<Long, Integer> eyes = new HashMap<>();

	/** Empties and refills the index from {@code records}. */
	public void rebuild(Iterable<CoreRecord> records) {
		chunks.clear();
		slots.clear();
		eyes.clear();
		for (CoreRecord record : records) {
			add(record);
		}
	}

	public void add(CoreRecord record) {
		if (record.virtual) {
			return;
		}
		eyes.put(record.eye.asLong(), record.id);
		FormationSpec spec = FormationRegistry.byId(record.spec);
		int reach = (spec == null ? 0 : spec.pattern().radius()) + MARGIN;
		int minCx = (record.eye.getX() - reach) >> 4;
		int maxCx = (record.eye.getX() + reach) >> 4;
		int minCz = (record.eye.getZ() - reach) >> 4;
		int maxCz = (record.eye.getZ() + reach) >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				chunks.computeIfAbsent(ChunkPos.pack(cx, cz), key -> new ArrayList<>(2)).add(record.id);
			}
		}
		if (spec != null) {
			List<Slot> list = spec.pattern().slots(record.rot);
			for (int i = 0; i < list.size(); i++) {
				Slot slot = list.get(i);
				long pos = BlockPos.asLong(record.eye.getX() + slot.dx(), record.eye.getY() + slot.dy(), record.eye.getZ() + slot.dz());
				slots.computeIfAbsent(pos, key -> new ArrayList<>(1)).add(((long) record.id << 16) | i);
			}
		}
	}

	/** Cores whose pattern box (plus {@link #MARGIN}) touches the chunk of {@code pos}. */
	public List<Integer> coresNear(BlockPos pos) {
		List<Integer> list = chunks.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
		return list == null ? List.of() : list;
	}

	/** Slot refs ({@code coreId << 16 | slotIndex}) at a block. */
	public List<Long> slotsAt(BlockPos pos) {
		List<Long> list = slots.get(pos.asLong());
		return list == null ? List.of() : list;
	}

	/** The core whose eye is at {@code pos}, or -1. */
	public int eyeAt(BlockPos pos) {
		Integer id = eyes.get(pos.asLong());
		return id == null ? -1 : id;
	}

	/** Entries in the index (the memory budget of 24.9). */
	public int size() {
		int total = eyes.size();
		for (List<Long> list : slots.values()) {
			total += list.size();
		}
		return total;
	}
}
