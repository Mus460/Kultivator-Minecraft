package dev.kultivator.formasi.core;

import dev.kultivator.formasi.FormasiItems;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.match.ChunkReader;
import dev.kultivator.formasi.match.MatchResult;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Slot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Verification of registered cores (DESIGN 24.2.3): one global round-robin budget of {@code formationReadsPerTick} reads
 * per tick shared by all loaded cores (each core keeps a slot cursor), plus event re-reads of single slots after a block
 * is broken or placed (a separate burst of {@link #BURST} reads per tick). {@code UNKNOWN} (chunk not loaded) never breaks
 * a core. A missing eye removes the record. Never a chunk load, never a block change.
 */
public final class Verifier {

	public static final int BURST = 64;
	/** Reads given to one core before the round-robin moves on. */
	private static final int PER_CORE = 8;

	private record Pending(ResourceKey<Level> dim, long pos) {
	}

	private static final Set<Pending> PENDING = new LinkedHashSet<>();
	private static int cursor;
	private static int readsLast;
	private static int readsWindow;
	private static long windowStart;

	/** Queues a re-read of every slot at {@code pos} (processed on the next tick, within the burst). */
	public static void queue(ServerLevel level, BlockPos pos) {
		PENDING.add(new Pending(level.dimension(), pos.asLong()));
		PENDING.add(new Pending(level.dimension(), pos.above().asLong()));
	}

	/** Re-reads the slots at {@code pos} right now (block broken). */
	public static void reread(ServerLevel level, BlockPos pos) {
		CoreStore store = CoreTicker.store(level);
		if (store.isEmpty()) {
			return;
		}
		ChunkReader reader = new ChunkReader(level);
		for (long ref : store.index().slotsAt(pos)) {
			CoreRecord record = store.byId((int) (ref >>> 16));
			if (record != null) {
				readSlot(level, reader, record, (int) (ref & 0xFFFF));
			}
		}
		readsWindow += reader.reads();
	}

	public static void step(MinecraftServer server, int budget) {
		long now = server.overworld().getGameTime();
		if (now - windowStart >= 20L) {
			readsLast = readsWindow;
			readsWindow = 0;
			windowStart = now;
		}
		// Event re-reads first (their own burst).
		if (!PENDING.isEmpty()) {
			int burst = BURST;
			List<Pending> batch = new ArrayList<>();
			for (Pending pending : PENDING) {
				if (batch.size() >= BURST) {
					break;
				}
				batch.add(pending);
			}
			PENDING.removeAll(batch);
			for (Pending pending : batch) {
				ServerLevel level = server.getLevel(pending.dim());
				if (level != null && burst > 0) {
					reread(level, BlockPos.of(pending.pos()));
					burst--;
				}
			}
		}
		List<CoreRecord> all = new ArrayList<>();
		List<ServerLevel> levels = new ArrayList<>();
		for (CoreStore store : CoreTicker.stores()) {
			ServerLevel level = store.dim() == null ? null : server.getLevel(store.dim());
			if (level == null) {
				continue;
			}
			for (CoreRecord record : store.all()) {
				if (!record.virtual) {
					all.add(record);
					levels.add(level);
				}
			}
		}
		if (all.isEmpty()) {
			return;
		}
		int left = budget;
		int visited = 0;
		while (left > 0 && visited < all.size()) {
			cursor = Math.floorMod(cursor, all.size());
			CoreRecord record = all.get(cursor);
			ServerLevel level = levels.get(cursor);
			cursor++;
			visited++;
			FormationSpec spec = FormationRegistry.byId(record.spec);
			if (spec == null || !level.isLoaded(record.eye)) {
				continue;
			}
			int reads = Math.min(left, PER_CORE);
			left -= stepCore(level, record, spec, reads);
		}
		readsWindow += budget - left;
	}

	/** Reads up to {@code reads} slots of one core from its cursor; returns the reads used. */
	private static int stepCore(ServerLevel level, CoreRecord record, FormationSpec spec, int reads) {
		List<Slot> slots = spec.pattern().slots(record.rot);
		ensure(record, slots.size());
		ChunkReader reader = new ChunkReader(level);
		int used = 0;
		while (used < reads) {
			if (record.cursor >= slots.size()) {
				record.cursor = 0;
				record.lastFullVerify = level.getGameTime();
				used++;
				if (!eyePresent(level, reader, record)) {
					CoreTicker.eyeGone(level, record.eye);
					return used;
				}
				continue;
			}
			readSlot(level, reader, record, record.cursor);
			record.cursor++;
			used++;
		}
		return used;
	}

	private static boolean eyePresent(ServerLevel level, ChunkReader reader, CoreRecord record) {
		BlockState state = reader.state(record.eye);
		return state == null || state.is(FormasiItems.FORMATION_EYE);
	}

	/** Reads one slot and updates the counters; a change between complete and broken re-derives the state at once. */
	private static void readSlot(ServerLevel level, ChunkReader reader, CoreRecord record, int index) {
		FormationSpec spec = FormationRegistry.byId(record.spec);
		if (spec == null) {
			return;
		}
		List<Slot> slots = spec.pattern().slots(record.rot);
		if (index < 0 || index >= slots.size()) {
			return;
		}
		ensure(record, slots.size());
		Slot slot = slots.get(index);
		byte now = reader.read(record.eye.offset(slot.dx(), slot.dy(), slot.dz()), slot.rule(), record.rot);
		byte old = record.slotState[index];
		if (now == old) {
			return;
		}
		record.slotState[index] = now;
		boolean wasBroken = record.missing > 0;
		if (old == ChunkReader.MISSING || old == ChunkReader.WRONG) {
			record.missing--;
		} else if (old == ChunkReader.UNKNOWN) {
			record.unknown--;
		}
		if (now == ChunkReader.MISSING || now == ChunkReader.WRONG) {
			record.missing++;
		} else if (now == ChunkReader.UNKNOWN) {
			record.unknown++;
		}
		if (wasBroken != record.missing > 0) {
			CoreTicker.refreshState(level, record);
		}
	}

	/** Sizes the slot state array (all UNKNOWN) when the pattern changed or was never read. */
	private static void ensure(CoreRecord record, int size) {
		if (record.slotState.length != size) {
			record.slotState = new byte[size];
			record.cursor = 0;
			record.missing = 0;
			record.unknown = size;
		}
	}

	/** Fills the slot states from a full match (registration and {@code debug formasi verify}). */
	public static void apply(CoreRecord record, MatchResult result) {
		record.slotState = result.states().clone();
		record.missing = result.bad();
		record.unknown = result.unknown();
		record.cursor = 0;
	}

	/** Reads (verify plus events) in the last second. */
	public static int readsPerSecond() {
		return readsLast;
	}

	public static void clear() {
		PENDING.clear();
		cursor = 0;
	}

	private Verifier() {
	}
}
