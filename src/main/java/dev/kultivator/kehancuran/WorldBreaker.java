package dev.kultivator.kehancuran;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The ONLY code of the whole mod that changes blocks (DESIGN 0 and 20.3.21), and only for Mode Kehancuran's ordered casts
 * with all three locks open, and (phase 6c, DESIGN 22.11) for the player's own techniques with the one-click switch on. One
 * running job per server and at most 7 queued, at most 2 destructive jobs per owner. Each server tick changes at most
 * {@code kehancuranBlocksPerTick} positions across all jobs with {@link #FLAGS} ({@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE
 * | UPDATE_SUPPRESS_DROPS | UPDATE_SKIP_ON_PLACE}): no neighbour or shape updates, so no cascades, no popped torches or
 * signs, no flowing fluids and no falling blocks (gravity blocks may float until touched). A position next to a fluid or a
 * block entity is never removed. A job pauses while the average tick time is above {@code kehancuranMsptPause} ms.
 * Protected zones are checked again for every tick's batch (players and villagers move); never-touched blocks are skipped;
 * unloaded chunks are dropped, never loaded. No fire, no explosion, no item entities, except the opt-in drops
 * ({@code kehancuranDropsPct}, at most 64 stacks per cast). Casts with 0% drops keep their removed states in memory for
 * {@code kehancuranUndoMinutes} so the master can restore them (lost on restart). A restore ignores the zones (it only puts
 * back what the cast took, where it is still air), waits for unloaded chunks and for anyone standing in a block, and hands
 * what it could not place back to the restore buffer so Pulihkan can be pressed again.
 * <p>
 * Phase 10b (DESIGN 29.6.3, 29.7.1): a Titah job ({@code Kehancuran.submitTitah}) is a self job with its own block cap,
 * 0 % drops and no merge; after its carve and surface pass it may build a capped block monument of Batu Pusaka and Urat
 * Pusaka into air of its own crater, recorded in the SAME undo entry (a carved position's record is rewritten, never
 * doubled). A clear job ({@link #submitClear}) is a restore-flavoured job that sets air only where the monument block
 * still stands; it waits for unloaded chunks and hands what it could not reach back to {@code TitahMonuments}.
 */
public final class WorldBreaker {

	/** One running job plus seven queued (DESIGN 22.11.7). */
	public static final int MAX_JOBS = 8;
	/** Destructive jobs (self and disciple together) one owner may have running or queued. */
	public static final int MAX_PER_OWNER = 2;
	/** Restore buffers the server keeps at most; the oldest is released first. */
	public static final int MAX_UNDO = 32;
	/** Per-position modes of a self job (one job may merge several shapes). */
	static final byte MODE_REMOVE = 0;
	static final byte MODE_NATURAL = 1;
	static final byte MODE_STRIP = 2;
	static final byte MODE_WITHER = 3;
	public static final int MAX_DROP_STACKS = 64;
	/** Positions looked at per tick at most, as a multiple of the block budget (skipped positions are cheap but bounded). */
	private static final int READ_FACTOR = 8;
	/** The only flags blocks are changed with: clients see it, nothing else reacts (see the class comment). */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_ON_PLACE;

	/** One cast (or restore) being worked through. */
	static final class Job {
		final UUID owner;
		final String ownerName;
		final String disciple;
		final String shape;
		final ResourceKey<Level> dimension;
		final BlockPos centre;
		final long[] positions;
		/** What each position becomes (null = air), or for a restore the state to put back. */
		final BlockState[] targets;
		/** For a restore: the state the cast left (a restore only acts where it is still there or air). */
		final BlockState[] placed;
		final boolean restore;
		final boolean naturalOnly;
		final boolean wither;
		final int dropsPct;
		final boolean undoable;
		int index;
		int changed;
		int stacks;
		boolean pausedTold;
		/** For a restore: the expiry of the buffer it came from, and the indices it could not place yet. */
		long expiresAt;
		final List<Integer> deferred = new ArrayList<>();
		final List<Long> undoPositions = new ArrayList<>();
		final List<BlockState> undoOld = new ArrayList<>();
		final List<BlockState> undoPlaced = new ArrayList<>();
		/** A player's own technique (DESIGN 22.11): label {@code self:<specId>}, the caster's moving safe column. */
		boolean self;
		@Nullable String specId;
		/** Where the caster stood at the cast (its safe column holds there too). */
		@Nullable Vec3 castPos;
		/** Per-position modes for a merged self job (null = the job flags). */
		byte @Nullable [] modes;
		/**
		 * Per column ({@code BlockPos.asLong(x, 0, z)}): the highest solid or fluid block this job had to leave. Nothing below
		 * it in that column is removed, so a skipped top layer (a lake bed, a protected block) never seals a hollow.
		 */
		final Map<Long, Integer> kept = new HashMap<>();

		// ---- Phase 10 (DESIGN 28.6.1, 28.8.1, 28.13.2)
		/** Groups this job's debris batches on the clients (a per-server counter). */
		final int jobId = nextJobId++;
		/** The caster's (player or disciple) entity id, -1 when unknown. */
		int casterEntityId = -1;
		/** The spec's net id of a self job, -1 for Destroyer Arts. */
		int specNet = -1;
		/** The Kawah Lebur palette of the surface pass (NONE = no pass). */
		Dampak.Palette palette = Dampak.Palette.NONE;
		/** {@code DampakDebris} motion flags (LIFT, IMPLODE, SUSPEND, WAKE). */
		int debrisFlags;
		/** The positions this job removed (at most the per-cast cap), kept also when the job is not undoable. */
		long[] carved = new long[0];
		int carvedCount;
		/** True once the carve is done and the surface pass runs. */
		boolean surfacePhase;
		Surface.Plan surface = Surface.EMPTY;
		int surfaceIndex;
		int surfaceChanged;

		// ---- Phase 10b (DESIGN 29.6.3, 29.7.1)
		/** A Titah Langit job: its own block cap, never merged, the monument phase and the DONE callback. */
		boolean titah;
		/** The Titah job's own block cap ({@code noteCarved} uses it). */
		int carveCap;
		/** The {@code TitahMonuments} record of the cast (-1 = none). */
		int recordId = -1;
		/** The planned monument cells as {@code BlockPos.asLong(x, layer, z)} (layer 0 = the crater floor's first air). */
		long @Nullable [] monumentPlan;
		/** 0 Batu Pusaka, 1 Urat Pusaka per planned cell. */
		byte @Nullable [] monumentKinds;
		boolean monumentPhase;
		long[] monumentCells = new long[0];
		byte[] monumentCellKinds = new byte[0];
		int monumentIndex;
		final List<Long> monumentPlaced = new ArrayList<>();
		final List<Byte> monumentPlacedKinds = new ArrayList<>();
		/** Position → index of its record in {@code undoPositions} (the undo rewrite of 29.6.3). */
		@Nullable Map<Long, Integer> undoIndex;
		/** What this job carved (the monument goes only into its own crater). */
		@Nullable Set<Long> carvedSet;
		int carveMinX;
		int carveMinY;
		int carveMinZ;
		int carveMaxX;
		int carveMaxY;
		int carveMaxZ;
		/** A Titah clear job (restore-flavoured): its id in {@code TitahMonuments}, -1 = untracked. */
		boolean clear;
		int clearId = -1;

		Job(UUID owner, String ownerName, String disciple, String shape, ResourceKey<Level> dimension, BlockPos centre, long[] positions,
				BlockState[] targets, BlockState[] placed, boolean restore, boolean naturalOnly, boolean wither, int dropsPct) {
			this.owner = owner;
			this.ownerName = ownerName;
			this.disciple = disciple;
			this.shape = shape;
			this.dimension = dimension;
			this.centre = centre;
			this.positions = positions;
			this.targets = targets;
			this.placed = placed;
			this.restore = restore;
			this.naturalOnly = naturalOnly;
			this.wither = wither;
			this.dropsPct = dropsPct;
			this.undoable = !restore && dropsPct <= 0 && KultivatorConfig.get().kehancuranUndoMinutes > 0;
		}

		/** A self job of the player's own technique (22.11.5): one merged plan with a mode per position. */
		static Job self(ServerPlayer caster, String specId, String shape, BlockPos centre, long[] positions, byte[] modes) {
			Job job = new Job(caster.getUUID(), caster.getName().getString(), "", shape, caster.level().dimension(), centre, positions,
					new BlockState[positions.length], null, false, false, false, KultivatorConfig.get().kehancuranDropsPct);
			job.self = true;
			job.specId = specId;
			job.castPos = caster.position();
			job.modes = modes;
			job.casterEntityId = caster.getId();
			return job;
		}

		/**
		 * A Titah job (Phase 10b, DESIGN 29.7.1): a self job with 0 % drops whatever {@code kehancuranDropsPct} says (so it
		 * is always undoable while the undo window is above 0) and its own block cap.
		 */
		static Job titah(ServerPlayer caster, String specId, String shape, BlockPos centre, long[] positions, byte[] modes, int carveCap) {
			Job job = new Job(caster.getUUID(), caster.getName().getString(), "", shape, caster.level().dimension(), centre, positions,
					new BlockState[positions.length], null, false, false, false, 0);
			job.self = true;
			job.specId = specId;
			job.castPos = caster.position();
			job.modes = modes;
			job.casterEntityId = caster.getId();
			job.titah = true;
			job.carveCap = Math.max(1, carveCap);
			return job;
		}

		/** Remembers a removed position for the surface pass (at most the per-cast cap; a Titah job's own cap). */
		void noteCarved(long pos) {
			int cap = titah ? Math.max(1, carveCap) : Math.max(1, KultivatorConfig.get().kehancuranMaxBlocksPerCast);
			if (carvedCount >= cap) {
				return;
			}
			if (carvedCount == carved.length) {
				carved = java.util.Arrays.copyOf(carved, Math.min(cap, Math.max(64, carved.length * 2)));
			}
			carved[carvedCount++] = pos;
		}

		/** The {@code DampakDebris} flags of this job's batches. */
		int packetFlags(MinecraftServer server) {
			int flags = debrisFlags;
			flags |= self ? Payloads.DampakDebris.FLAG_SELF : Payloads.DampakDebris.FLAG_DISCIPLE;
			if (server.getAverageTickTimeNanos() > LOW_LOAD_NANOS) {
				flags |= Payloads.DampakDebris.FLAG_LOW_LOAD;
			}
			return flags;
		}

		/** The mode of position {@code i}. */
		byte mode(int i) {
			if (modes != null && i < modes.length) {
				return modes[i];
			}
			return wither ? MODE_WITHER : naturalOnly ? MODE_NATURAL : MODE_REMOVE;
		}
	}

	/** What a finished cast removed, kept in memory for the restore window. */
	static final class Undo {
		final int id;
		final UUID owner;
		final String ownerName;
		final String disciple;
		final String shape;
		final ResourceKey<Level> dimension;
		final BlockPos centre;
		final long[] positions;
		final BlockState[] old;
		/** What the cast left at each position; in a bulk undo null means AIR (compact arrays). */
		final BlockState[] placed;
		final long expiresAt;
		final boolean self;
		final @Nullable String specId;
		/** Phase 10b (DESIGN 29.16.9): a compact bulk undo (Tingkat 2-5); Pulihkan runs it as a bulk restore job. */
		final boolean bulk;
		final int tier;

		Undo(int id, Job job, long expiresAt) {
			this(id, job, job.undoPositions.stream().mapToLong(Long::longValue).toArray(), job.undoOld.toArray(new BlockState[0]),
					job.undoPlaced.toArray(new BlockState[0]), expiresAt);
		}

		Undo(int id, Job job, long[] positions, BlockState[] old, BlockState[] placed, long expiresAt) {
			this(id, job.owner, job.ownerName, job.disciple, job.shape, job.dimension, job.centre, positions, old, placed, expiresAt, job.self,
					job.specId, false, 1);
		}

		Undo(int id, UUID owner, String ownerName, String disciple, String shape, ResourceKey<Level> dimension, BlockPos centre, long[] positions,
				BlockState[] old, BlockState[] placed, long expiresAt, boolean self, @Nullable String specId, boolean bulk, int tier) {
			this.id = id;
			this.owner = owner;
			this.ownerName = ownerName;
			this.disciple = disciple;
			this.shape = shape;
			this.dimension = dimension;
			this.centre = centre;
			this.positions = positions;
			this.old = old;
			this.placed = placed;
			this.expiresAt = expiresAt;
			this.self = self;
			this.specId = specId;
			this.bulk = bulk;
			this.tier = tier;
		}
	}

	private static final Deque<Job> JOBS = new ArrayDeque<>();
	private static final List<Undo> UNDO = new ArrayList<>();
	private static int nextUndo = 1;
	/** Phase 10: the next {@code DampakDebris} job id. */
	private static int nextJobId = 1;
	/** Debris packets go to players this close to the batch (28.1.3). */
	private static final double DEBRIS_RANGE = 96.0D;
	/** Above this average tick time the debris batches carry LOW_LOAD (clients draw particles only). */
	private static final long LOW_LOAD_NANOS = 40_000_000L;

	static void init() {
		// Phase 10b (DESIGN 29.16.7): the bulk carve's chunk ticket is registered here, during mod init (class load)
		Kultivator.LOGGER.debug("Kultivator: ticket type {} ready", BuiltInRegistries.TICKET_TYPE.getKey(TITAH_TICKET));
		ServerTickEvents.END_SERVER_TICK.register(WorldBreaker::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// never leave a ticket or a boss bar behind (non-PERSIST tickets are not saved anyway)
			for (BulkJob job : BULK) {
				try {
					releaseAll(server, job);
					job.bar.removeAllPlayers();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.warn("Kultivator: could not release a bulk carve at stop", exception);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			UNDO.clear();
			BULK.clear();
			PERMANENT.clear();
		});
	}

	// ================================================================== queue

	/** True when another job fits in the queue. */
	static boolean hasRoom() {
		return JOBS.size() < MAX_JOBS;
	}

	/** True when another destructive job of {@code owner} fits: the queue has room and the owner has fewer than 2. */
	static boolean hasRoom(UUID owner) {
		return hasRoom() && ownerJobs(owner) < MAX_PER_OWNER;
	}

	/** Destructive (not restore) jobs of {@code owner} running or queued. */
	static int ownerJobs(UUID owner) {
		int count = 0;
		for (Job job : JOBS) {
			if (!job.restore && job.owner.equals(owner)) {
				count++;
			}
		}
		return count;
	}

	/** Phase 10b (29.3.2): a Titah job of {@code owner} is running or queued (one per owner). */
	static boolean hasTitah(UUID owner) {
		for (Job job : JOBS) {
			if (job.titah && !job.restore && job.owner.equals(owner)) {
				return true;
			}
		}
		// Phase 10b (29.16.6): a bulk carve of the owner counts as its Titah job too
		for (BulkJob job : BULK) {
			if (!job.restore && job.owner.equals(owner)) {
				return true;
			}
		}
		return false;
	}

	/** {@link #submitClear(UUID, ResourceKey, long[], BlockState[], int)} without a tracking id. */
	public static boolean submitClear(UUID owner, ResourceKey<Level> dimension, long[] positions, BlockState[] placed) {
		return submitClear(owner, dimension, positions, placed, -1);
	}

	/**
	 * Phase 10b (DESIGN 29.6.3, contract C10b.3): queues the clear of a Titah block monument, a restore-flavoured job that
	 * sets AIR only where the current state is still {@code placed[i]} (our own block), checks no zone (it removes only our
	 * own blocks), never loads a chunk (those positions wait) and is logged. When it ends, what it could not reach goes back
	 * to {@code TitahMonuments.onClearFinished(clearId, ...)}. False when the queue is full (the caller keeps the request).
	 */
	public static boolean submitClear(UUID owner, ResourceKey<Level> dimension, long[] positions, BlockState[] placed, int clearId) {
		if (positions == null || placed == null || positions.length == 0 || positions.length != placed.length || !hasRoom()) {
			return false;
		}
		BlockState[] air = new BlockState[positions.length];
		java.util.Arrays.fill(air, Blocks.AIR.defaultBlockState());
		Job job = new Job(owner, "", "", "titah_clear", dimension, BlockPos.of(positions[0]), positions.clone(), air, placed.clone(), true, false,
				false, 0);
		job.clear = true;
		job.clearId = clearId;
		job.expiresAt = Long.MAX_VALUE;
		JOBS.addLast(job);
		return true;
	}

	/** Queues a cast. False when the queue is full (or, for a destructive job, its owner already has 2). */
	static boolean submit(Job job) {
		if (job.self && !job.titah && !job.restore && ownerJobs(job.owner) >= MAX_PER_OWNER && merge(job)) {
			return true;
		}
		if (!hasRoom() || !job.restore && ownerJobs(job.owner) >= MAX_PER_OWNER) {
			return false;
		}
		JOBS.addLast(job);
		return true;
	}

	/**
	 * A self job of the same owner, spec and dimension still waiting in the queue (not the running one) takes the new
	 * positions, so a cast's later stages or a recast while it carves stay one job and one undo entry (22.11.5). False
	 * when there is none or the merged plan would pass twice the per-cast block cap.
	 */
	private static boolean merge(Job job) {
		Job running = JOBS.peekFirst();
		List<Job> list = new ArrayList<>(JOBS);
		int limit = 2 * Math.max(1, KultivatorConfig.get().kehancuranMaxBlocksPerCast);
		for (int i = 0; i < list.size(); i++) {
			Job queued = list.get(i);
			if (queued == running || !queued.self || queued.titah || queued.restore || !queued.owner.equals(job.owner)
					|| !java.util.Objects.equals(queued.specId, job.specId) || !queued.dimension.equals(job.dimension)) {
				continue;
			}
			int total = queued.positions.length + job.positions.length;
			if (total > limit) {
				return false;
			}
			long[] positions = new long[total];
			byte[] modes = new byte[total];
			for (int n = 0; n < queued.positions.length; n++) {
				positions[n] = queued.positions[n];
				modes[n] = queued.mode(n);
			}
			for (int n = 0; n < job.positions.length; n++) {
				positions[queued.positions.length + n] = job.positions[n];
				modes[queued.positions.length + n] = job.mode(n);
			}
			Job merged = new Job(queued.owner, queued.ownerName, queued.disciple, queued.shape, queued.dimension, queued.centre, positions,
					new BlockState[total], null, false, false, false, queued.dropsPct);
			merged.self = true;
			merged.specId = queued.specId;
			merged.castPos = queued.castPos;
			merged.modes = modes;
			merged.casterEntityId = queued.casterEntityId;
			merged.specNet = queued.specNet;
			merged.palette = queued.palette;
			merged.debrisFlags = queued.debrisFlags;
			list.set(i, merged);
			JOBS.clear();
			JOBS.addAll(list);
			return true;
		}
		return false;
	}

	/**
	 * The player switched the self mode off (22.11.1): queued self jobs are dropped and a running one stops now (after the
	 * batch this tick already did); what was removed stays restorable. Restores keep running. Returns how many stopped.
	 */
	static int cancelSelf(MinecraftServer server, UUID owner) {
		int cancelled = 0;
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (!job.self || job.restore || !job.owner.equals(owner)) {
				continue;
			}
			it.remove();
			cancelled++;
			finish(server, job, true);
		}
		return cancelled + cancelBulk(server, owner);
	}

	/** {running, queued, self jobs} across the server (for {@code debug teknik beban}); bulk jobs included. */
	static int[] counts() {
		int self = 0;
		for (Job job : JOBS) {
			if (job.self) {
				self++;
			}
		}
		for (BulkJob job : BULK) {
			if (!job.restore) {
				self++;
			}
		}
		int running = (JOBS.isEmpty() ? 0 : 1) + (BULK.isEmpty() ? 0 : 1);
		return new int[]{running, queued() + Math.max(0, BULK.size() - 1), self};
	}

	/** {queued self jobs, 1 when a self job of this owner runs} for the Kehancuran page. */
	static int[] selfState(UUID owner) {
		int queued = 0;
		boolean running = false;
		boolean first = true;
		for (Job job : JOBS) {
			if (job.self && !job.restore && job.owner.equals(owner)) {
				if (first) {
					running = true;
				} else {
					queued++;
				}
			}
			first = false;
		}
		first = true;
		for (BulkJob job : BULK) {
			if (!job.restore && job.owner.equals(owner)) {
				if (first) {
					running = true;
				} else {
					queued++;
				}
			}
			first = false;
		}
		return new int[]{queued, running ? 1 : 0};
	}

	/** Jobs waiting behind the running one. */
	static int queued() {
		return Math.max(0, JOBS.size() - 1);
	}

	static @Nullable Job running() {
		return JOBS.peekFirst();
	}

	/** True while any Kehancuran job (cast or restore) is queued or running: sect construction pauses (DESIGN 21.15). */
	public static boolean busy() {
		return !JOBS.isEmpty() || !BULK.isEmpty();
	}

	/**
	 * Stops every destructive job of {@code owner} at once (a lock was closed); what was already removed stays restorable.
	 * Restores keep running: they only put the world back.
	 */
	static int cancelOwner(MinecraftServer server, UUID owner) {
		int cancelled = 0;
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (!job.owner.equals(owner) || job.restore) {
				continue;
			}
			it.remove();
			cancelled++;
			finish(server, job, true);
		}
		return cancelled + cancelBulk(server, owner);
	}

	/** Stops every destructive job (the server switched Mode Kehancuran off); restores keep running. */
	static void cancelAll(MinecraftServer server) {
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (job.restore) {
				continue;
			}
			it.remove();
			finish(server, job, true);
		}
		cancelBulk(server, null);
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (!UNDO.isEmpty() && server.getTickCount() % 20 == 0) {
			long now = server.overworld().getGameTime();
			UNDO.removeIf(undo -> now >= undo.expiresAt);
		}
		// Phase 10b (29.16.7): the bulk carve first, then the classic queue keeps running
		tickBulk(server);
		Job job = JOBS.peekFirst();
		if (job == null) {
			return;
		}
		KultivatorConfig config = KultivatorConfig.get();
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null) {
			JOBS.pollFirst();
			return;
		}
		if (server.getAverageTickTimeNanos() > config.kehancuranMsptPause * 1_000_000L) {
			if (!job.pausedTold) {
				job.pausedTold = true;
				ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
				if (owner != null) {
					Msg.actionBar(owner, Component.translatable("kultivator.kehancuran.paused").withStyle(ChatFormatting.GRAY));
				}
			}
			return;
		}
		if (job.monumentPhase) {
			tickMonument(server, level, job, config);
			return;
		}
		if (job.surfacePhase) {
			tickSurface(server, level, job, config);
			return;
		}
		int budget = config.kehancuranBlocksPerTick;
		int reads = budget * READ_FACTOR;
		ProtectedZones.Context zones = job.restore ? null : zonesFor(server, level, job, batchBox(job, reads));
		Sample carvedSample = new Sample();
		Sample convertSample = new Sample();
		double sx = 0.0D;
		double sy = 0.0D;
		double sz = 0.0D;
		int done = 0;
		while (budget > 0 && reads > 0 && job.index < job.positions.length) {
			int i = job.index++;
			reads--;
			BlockPos pos = BlockPos.of(job.positions[i]);
			if (job.restore) {
				if (!level.isLoaded(pos)) {
					// Never load a chunk: this block waits in the buffer for the next Pulihkan.
					job.deferred.add(i);
					continue;
				}
			}
			ProtectedZones.Zone zone = job.restore ? null : zones.zoneOf(pos);
			if (zone == ProtectedZones.Zone.UNLOADED) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (zone != null) {
				keep(job, i, pos, state);
				continue;
			}
			BlockState to;
			if (job.restore) {
				BlockState left = job.placed == null ? Blocks.AIR.defaultBlockState() : job.placed[i];
				if (!state.isAir() && state != left) {
					continue;
				}
				to = job.targets[i];
				if (!level.isUnobstructed(to, pos, CollisionContext.empty())) {
					// Someone (a pet, a disciple, a player) stands there: never bury it, try again later.
					job.deferred.add(i);
					continue;
				}
			} else {
				byte mode = job.mode(i);
				if (state.isAir()) {
					continue;
				}
				boolean carves = mode == MODE_REMOVE || mode == MODE_NATURAL;
				if (carves && job.kept.getOrDefault(BlockPos.asLong(pos.getX(), 0, pos.getZ()), Integer.MIN_VALUE) > pos.getY()) {
					// A block above in this column had to stay: carving under it would leave a sealed hollow.
					keep(job, i, pos, state);
					continue;
				}
				if (ProtectedZones.neverTouch(level, pos, state) || mode == MODE_NATURAL && !Shapes.natural(state)
						|| mode == MODE_STRIP && !state.is(Shapes.STRIPPABLE) || touchesUnsafe(level, pos)) {
					keep(job, i, pos, state);
					continue;
				}
				to = mode == MODE_WITHER ? Shapes.witherInto(state) : Blocks.AIR.defaultBlockState();
				if (to == null) {
					continue;
				}
				if (to.isAir() && job.dropsPct > 0 && job.stacks < MAX_DROP_STACKS && level.getRandom().nextInt(100) < job.dropsPct) {
					for (ItemStack drop : Block.getDrops(state, level, pos, null)) {
						if (job.stacks >= MAX_DROP_STACKS) {
							break;
						}
						Block.popResource(level, pos, drop);
						job.stacks++;
					}
				}
			}
			if (!level.setBlock(pos, to, FLAGS)) {
				// Nothing changed (the same state, or the level refused): no count and no undo entry.
				continue;
			}
			if (job.undoable) {
				job.undoPositions.add(pos.asLong());
				job.undoOld.add(state);
				job.undoPlaced.add(to);
			}
			job.changed++;
			budget--;
			done++;
			if (!job.restore) {
				// Phase 10: the batch's debris (the OLD state) and the crater the surface pass works on
				if (to.isAir()) {
					job.noteCarved(pos.asLong());
					carvedSample.add(pos.asLong(), Block.getId(state), level.getRandom().nextInt(Integer.MAX_VALUE));
				} else {
					convertSample.add(pos.asLong(), Block.getId(state), level.getRandom().nextInt(Integer.MAX_VALUE));
				}
			}
			if (!job.restore && to.isAir()) {
				budget -= clearLoosePlants(level, job, zones, pos);
			}
			sx += pos.getX() + 0.5D;
			sy += pos.getY() + 0.5D;
			sz += pos.getZ() + 0.5D;
		}
		if (done > 0) {
			Vec3 centroid = new Vec3(sx / done, sy / done, sz / done);
			Fx.send(level, centroid, FxType.KEHANCURAN, job.restore ? KehancuranFxIds.SHIMMER : KehancuranFxIds.DUST, done,
					(float) Math.min(8.0D, 1.0D + Math.sqrt(done)));
			if (server.getTickCount() % 10 == 0) {
				level.playSound(null, centroid.x, centroid.y, centroid.z, job.restore ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.DEEPSLATE_BREAK,
						SoundSource.BLOCKS, 1.2F, job.restore ? 1.4F : 0.6F);
			}
			if (!job.restore) {
				// at most one debris packet per job per tick (28.1.3): the removed blocks, else the withered ones
				boolean carvedAny = carvedSample.count > 0;
				sendDebris(server, level, job, carvedAny ? Payloads.DampakDebris.CARVE : Payloads.DampakDebris.CONVERT,
						carvedAny ? carvedSample : convertSample, centroid);
			}
		}
		if (job.index >= job.positions.length) {
			if (!job.restore && startSurface(level, job, config)) {
				return;
			}
			if (!job.restore && startMonument(level, job, config)) {
				return;
			}
			JOBS.pollFirst();
			finish(server, job, false);
		}
	}

	// ================================================================== Phase 10: debris and the surface pass (DESIGN 28.6, 28.8)

	/** A uniform sample of at most {@code DampakDebris.MAX} changed positions of one batch (reservoir sampling). */
	private static final class Sample {
		final long[] positions = new long[Payloads.DampakDebris.MAX];
		final int[] states = new int[Payloads.DampakDebris.MAX];
		int count;
		int seen;

		void add(long pos, int state, int random) {
			seen++;
			if (count < positions.length) {
				positions[count] = pos;
				states[count] = state;
				count++;
				return;
			}
			int slot = random % seen;
			if (slot < positions.length) {
				positions[slot] = pos;
				states[slot] = state;
			}
		}
	}

	/** Sends one batch to the players within 96 blocks of {@code near} in the job's dimension (never for a restore). */
	private static void sendDebris(MinecraftServer server, ServerLevel level, Job job, byte kind, Sample sample, Vec3 near) {
		if (sample.count <= 0 || job.restore) {
			return;
		}
		BlockPos base = BlockPos.containing(Vec3.atCenterOf(job.centre));
		int[] offsets = new int[sample.count];
		int[] states = new int[sample.count];
		int n = 0;
		for (int i = 0; i < sample.count; i++) {
			BlockPos pos = BlockPos.of(sample.positions[i]);
			int dx = pos.getX() - base.getX();
			int dy = pos.getY() - base.getY();
			int dz = pos.getZ() - base.getZ();
			if (Math.abs(dx) > 511 || Math.abs(dy) > 511 || Math.abs(dz) > 511) {
				continue;
			}
			offsets[n] = Payloads.DampakDebris.pack(dx, dy, dz);
			states[n] = sample.states[i];
			n++;
		}
		if (n == 0) {
			return;
		}
		if (n < offsets.length) {
			offsets = java.util.Arrays.copyOf(offsets, n);
			states = java.util.Arrays.copyOf(states, n);
		}
		Vec3 centre = Vec3.atCenterOf(job.centre);
		broadcast(level, near, new Payloads.DampakDebris(job.jobId, kind, job.casterEntityId, job.specNet, centre.x, centre.y, centre.z,
				(short) Math.min(Short.MAX_VALUE, sample.seen), (short) job.packetFlags(server), offsets, states));
	}

	/** Sends {@code payload} to every player of {@code level} within 96 blocks of {@code near}. */
	static void broadcast(ServerLevel level, Vec3 near, Payloads.DampakDebris payload) {
		double range = DEBRIS_RANGE * DEBRIS_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(near) <= range) {
				Net.send(player, payload);
			}
		}
	}

	/**
	 * The carve is done: plans the Kawah Lebur pass of a job whose spec names a palette (server config
	 * {@code kehancuranSurfacePass}, at most {@code kehancuranSurfaceMax}). True when the pass now runs in this job.
	 */
	private static boolean startSurface(ServerLevel level, Job job, KultivatorConfig config) {
		if (job.surfacePhase || job.palette == Dampak.Palette.NONE || !config.kehancuranSurfacePass || job.carvedCount == 0) {
			return false;
		}
		Vec3 axis = job.castPos == null ? new Vec3(0.0D, 0.0D, 1.0D) : Vec3.atCenterOf(job.centre).subtract(job.castPos);
		int cap = Surface.cap(job.changed, config.kehancuranSurfaceMax);
		Surface.Plan plan = Surface.plan(level, job.carved, job.carvedCount, job.palette, cap, job.centre, job.castPos, axis);
		if (plan.isEmpty()) {
			return false;
		}
		job.surface = plan;
		job.surfaceIndex = 0;
		job.surfacePhase = true;
		return true;
	}

	/**
	 * One tick of the surface pass: the same budget, zones (re-checked per batch), never-touched, unsafe-neighbour and
	 * caster-column rules as the carve; a block is converted only while it is still what was planned; magma never under a
	 * standing player. Recorded in the job's undo entry as (old, placed).
	 */
	private static void tickSurface(MinecraftServer server, ServerLevel level, Job job, KultivatorConfig config) {
		Surface.Plan plan = job.surface;
		int budget = config.kehancuranBlocksPerTick;
		int reads = budget * READ_FACTOR;
		ProtectedZones.Context zones = zonesFor(server, level, job, surfaceBox(job, reads));
		Sample sample = new Sample();
		double sx = 0.0D;
		double sy = 0.0D;
		double sz = 0.0D;
		int done = 0;
		while (budget > 0 && reads > 0 && job.surfaceIndex < plan.positions().length) {
			int i = job.surfaceIndex++;
			reads--;
			BlockPos pos = BlockPos.of(plan.positions()[i]);
			ProtectedZones.Zone zone = zones.zoneOf(pos);
			if (zone != null) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state != plan.from()[i] || ProtectedZones.neverTouch(level, pos, state) || touchesUnsafe(level, pos)) {
				continue;
			}
			BlockState to = plan.to()[i];
			if (Surface.magma(to) && standing(level, pos)) {
				to = Blocks.BLACKSTONE.defaultBlockState();
			}
			if (!level.setBlock(pos, to, FLAGS)) {
				continue;
			}
			if (job.undoable) {
				job.undoPositions.add(pos.asLong());
				job.undoOld.add(state);
				job.undoPlaced.add(to);
			}
			job.surfaceChanged++;
			budget--;
			done++;
			sample.add(pos.asLong(), Block.getId(to), level.getRandom().nextInt(Integer.MAX_VALUE));
			sx += pos.getX() + 0.5D;
			sy += pos.getY() + 0.5D;
			sz += pos.getZ() + 0.5D;
		}
		if (done > 0) {
			Vec3 centroid = new Vec3(sx / done, sy / done, sz / done);
			sendDebris(server, level, job, Payloads.DampakDebris.SURFACE, sample, centroid);
			if (server.getTickCount() % 10 == 0) {
				level.playSound(null, centroid.x, centroid.y, centroid.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.7F);
			}
		}
		if (job.surfaceIndex >= plan.positions().length) {
			if (startMonument(level, job, config)) {
				return;
			}
			JOBS.pollFirst();
			finish(server, job, false);
		}
	}

	// ================================================================== Phase 10b: the Titah block monument (DESIGN 29.6.3)

	/**
	 * The carve (and surface pass) of a Titah job is done: resolves its planned monument cells onto the crater floor (each
	 * planned column's lowest carved cell is layer 0) and starts the monument phase. Only for a Titah job whose record is
	 * still live, with 0 % drops, an undo window above 0 and {@code titahMonumentBlocks} above 0. True when it now runs.
	 */
	private static boolean startMonument(ServerLevel level, Job job, KultivatorConfig config) {
		if (job.monumentPhase || !job.titah || job.restore || job.monumentPlan == null || job.monumentKinds == null
				|| job.monumentPlan.length == 0 || !job.undoable || job.dropsPct > 0 || config.titahMonumentBlocks <= 0
				|| config.kehancuranUndoMinutes <= 0 || job.carvedCount == 0
				|| !dev.kultivator.pusakalangit.TitahMonuments.live(level.getServer(), job.recordId)) {
			return false;
		}
		Map<Long, Integer> lowest = new HashMap<>();
		Set<Long> carved = new HashSet<>(Math.max(16, job.carvedCount * 2));
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < job.carvedCount; i++) {
			long packed = job.carved[i];
			carved.add(packed);
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			lowest.merge(BlockPos.asLong(x, 0, z), y, Math::min);
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		int cap = Math.min(config.titahMonumentBlocks, job.monumentPlan.length);
		List<Long> cells = new ArrayList<>(cap);
		List<Byte> kinds = new ArrayList<>(cap);
		for (int i = 0; i < job.monumentPlan.length && cells.size() < cap; i++) {
			long packed = job.monumentPlan[i];
			int x = BlockPos.getX(packed);
			int layer = Math.max(0, BlockPos.getY(packed));
			int z = BlockPos.getZ(packed);
			Integer floor = lowest.get(BlockPos.asLong(x, 0, z));
			if (floor == null) {
				// not in this job's crater: never built outside its own carve
				continue;
			}
			cells.add(BlockPos.asLong(x, floor + layer, z));
			kinds.add(i < job.monumentKinds.length ? job.monumentKinds[i] : 0);
		}
		if (cells.isEmpty()) {
			return false;
		}
		Map<Long, Integer> index = new HashMap<>(Math.max(16, job.undoPositions.size() * 2));
		for (int i = 0; i < job.undoPositions.size(); i++) {
			// the last record of a position is the one a rewrite must change
			index.put(job.undoPositions.get(i), i);
		}
		job.undoIndex = index;
		job.carvedSet = carved;
		job.carveMinX = minX;
		job.carveMinY = minY;
		job.carveMinZ = minZ;
		job.carveMaxX = maxX;
		job.carveMaxY = maxY;
		job.carveMaxZ = maxZ;
		job.monumentCells = cells.stream().mapToLong(Long::longValue).toArray();
		job.monumentCellKinds = new byte[kinds.size()];
		for (int i = 0; i < kinds.size(); i++) {
			job.monumentCellKinds[i] = kinds.get(i);
		}
		job.monumentIndex = 0;
		job.surfacePhase = false;
		job.monumentPhase = true;
		return true;
	}

	/**
	 * One tick of the monument phase: the same budget, zones (re-checked per batch, the caster's safe column included),
	 * unsafe-neighbour rule and player checks as the carve. A cell is placed only into AIR that this job carved, or into
	 * air right above a solid block inside the carve's box; never where an entity or a standing player is. Every placement
	 * goes into the job's own undo entry: a carved cell's record (old, AIR) is REWRITTEN to (old, monument block), a new
	 * cell gets (AIR, monument block). Two records for one cell would end a Pulihkan on AIR and lose the original stone,
	 * because a restore only puts {@code old} back where the block is air or still {@code placed}.
	 */
	private static void tickMonument(MinecraftServer server, ServerLevel level, Job job, KultivatorConfig config) {
		int budget = config.kehancuranBlocksPerTick;
		int reads = budget * READ_FACTOR;
		ProtectedZones.Context zones = zonesFor(server, level, job, monumentBox(job, reads));
		Map<Long, Integer> index = job.undoIndex == null ? new HashMap<>() : job.undoIndex;
		Set<Long> carved = job.carvedSet == null ? Set.of() : job.carvedSet;
		double sx = 0.0D;
		double sy = 0.0D;
		double sz = 0.0D;
		int done = 0;
		while (budget > 0 && reads > 0 && job.monumentIndex < job.monumentCells.length) {
			int i = job.monumentIndex++;
			reads--;
			long packed = job.monumentCells[i];
			BlockPos pos = BlockPos.of(packed);
			if (zones.zoneOf(pos) != null) {
				// a protected zone, the caster's safe column or an unloaded chunk
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				continue;
			}
			if (!carved.contains(packed)) {
				BlockState below = level.getBlockState(pos.below());
				if (below.isAir() || !below.canOcclude() || !insideCarve(job, pos)) {
					continue;
				}
			}
			if (touchesUnsafe(level, pos) || standing(level, pos) || occupied(level, pos)) {
				continue;
			}
			byte kind = i < job.monumentCellKinds.length ? job.monumentCellKinds[i] : 0;
			BlockState to = dev.kultivator.pusakalangit.TitahItems.monumentState(kind);
			if (!level.setBlock(pos, to, FLAGS)) {
				continue;
			}
			Integer at = index.get(packed);
			if (at != null && at < job.undoPlaced.size()) {
				job.undoPlaced.set(at, to);
			} else {
				job.undoPositions.add(packed);
				job.undoOld.add(state);
				job.undoPlaced.add(to);
				index.put(packed, job.undoPositions.size() - 1);
			}
			job.monumentPlaced.add(packed);
			job.monumentPlacedKinds.add(kind);
			budget--;
			done++;
			sx += pos.getX() + 0.5D;
			sy += pos.getY() + 0.5D;
			sz += pos.getZ() + 0.5D;
		}
		if (done > 0) {
			Vec3 centroid = new Vec3(sx / done, sy / done, sz / done);
			Fx.send(level, centroid, FxType.KEHANCURAN, KehancuranFxIds.SHIMMER, done, (float) Math.min(8.0D, 1.0D + Math.sqrt(done)));
			if (server.getTickCount() % 10 == 0) {
				level.playSound(null, centroid.x, centroid.y, centroid.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.6F);
			}
		}
		if (job.monumentIndex >= job.monumentCells.length) {
			JOBS.pollFirst();
			finish(server, job, false);
		}
	}

	/** Inside the box of what this job carved. */
	private static boolean insideCarve(Job job, BlockPos pos) {
		return pos.getX() >= job.carveMinX && pos.getX() <= job.carveMaxX && pos.getY() >= job.carveMinY && pos.getY() <= job.carveMaxY + 1
				&& pos.getZ() >= job.carveMinZ && pos.getZ() <= job.carveMaxZ;
	}

	/** Some entity (an item, a mob, a player) stands in the cell; the planted weapon itself never counts. */
	private static boolean occupied(ServerLevel level, BlockPos pos) {
		return !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos),
				entity -> !entity.isSpectator() && !(entity instanceof dev.kultivator.pusakalangit.TitahMonumentEntity)).isEmpty();
	}

	/** The box around the monument cells this tick may look at. */
	private static AABB monumentBox(Job job, int reads) {
		long[] cells = job.monumentCells;
		int end = Math.min(cells.length, job.monumentIndex + reads);
		if (job.monumentIndex >= end) {
			return new AABB(job.centre);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = job.monumentIndex; i < end; i++) {
			long packed = cells[i];
			minX = Math.min(minX, BlockPos.getX(packed));
			minY = Math.min(minY, BlockPos.getY(packed));
			minZ = Math.min(minZ, BlockPos.getZ(packed));
			maxX = Math.max(maxX, BlockPos.getX(packed));
			maxY = Math.max(maxY, BlockPos.getY(packed));
			maxZ = Math.max(maxZ, BlockPos.getZ(packed));
		}
		return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	/** A player stands on (or just above) {@code pos}: no magma under their feet. */
	private static boolean standing(ServerLevel level, BlockPos pos) {
		for (ServerPlayer player : level.players()) {
			BlockPos feet = player.blockPosition();
			int above = feet.getY() - pos.getY();
			if (feet.getX() == pos.getX() && feet.getZ() == pos.getZ() && above >= 0 && above <= 2) {
				return true;
			}
		}
		return false;
	}

	/** The box around the surface positions this tick may look at. */
	private static AABB surfaceBox(Job job, int reads) {
		long[] positions = job.surface.positions();
		int end = Math.min(positions.length, job.surfaceIndex + reads);
		if (job.surfaceIndex >= end) {
			return new AABB(job.centre);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = job.surfaceIndex; i < end; i++) {
			long packed = positions[i];
			minX = Math.min(minX, BlockPos.getX(packed));
			minY = Math.min(minY, BlockPos.getY(packed));
			minZ = Math.min(minZ, BlockPos.getZ(packed));
			maxX = Math.max(maxX, BlockPos.getX(packed));
			maxY = Math.max(maxY, BlockPos.getY(packed));
			maxZ = Math.max(maxZ, BlockPos.getZ(packed));
		}
		return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	/** The job ended: the clients near it close the crater with a ring sized by the blocks changed (DONE). */
	private static void sendDone(MinecraftServer server, Job job) {
		if (job.restore || job.changed + job.surfaceChanged <= 0) {
			return;
		}
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null) {
			return;
		}
		Vec3 centre = Vec3.atCenterOf(job.centre);
		broadcast(level, centre, new Payloads.DampakDebris(job.jobId, Payloads.DampakDebris.DONE, job.casterEntityId, job.specNet, centre.x,
				centre.y, centre.z, (short) Math.min(Short.MAX_VALUE, job.changed), (short) job.packetFlags(server), new int[0], new int[0]));
	}

	/**
	 * The zones of one batch: a self job exempts its caster from the player zone and protects the caster's safe column at
	 * the cast position and where the caster stands now (re-read every batch, 22.11.6).
	 */
	private static ProtectedZones.Context zonesFor(MinecraftServer server, ServerLevel level, Job job, AABB box) {
		if (!job.self) {
			return new ProtectedZones.Context(level, box);
		}
		List<Vec3> columns = new ArrayList<>(2);
		if (job.castPos != null) {
			columns.add(job.castPos);
		}
		ServerPlayer caster = server.getPlayerList().getPlayer(job.owner);
		if (caster != null && caster.level() == level) {
			columns.add(caster.position());
		}
		return new ProtectedZones.Context(level, box, job.owner, columns);
	}

	/** How many plants above a carved cell are cleared at most (a sugar cane or bamboo stalk, a tall flower). */
	private static final int LOOSE_PLANT_REACH = 4;

	/**
	 * Clears the grass, flowers and other loose plants that would float above a cell just carved: block updates are off
	 * (no cascades), so they would never pop on their own. Only natural ground cover that can no longer survive there,
	 * never a block entity, a fluid, a protected zone or anything solid; recorded for Pulihkan like the carving itself.
	 *
	 * @return the number of plants cleared (spent from the batch budget)
	 */
	private static int clearLoosePlants(ServerLevel level, Job job, @Nullable ProtectedZones.Context zones, BlockPos carved) {
		int cleared = 0;
		BlockPos.MutableBlockPos cursor = carved.mutable();
		for (int step = 0; step < LOOSE_PLANT_REACH; step++) {
			cursor.move(Direction.UP);
			BlockState plant = level.getBlockState(cursor);
			if (!loosePlant(plant) || plant.canSurvive(level, cursor) || zones != null && zones.zoneOf(cursor) != null) {
				break;
			}
			BlockPos at = cursor.immutable();
			if (!level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS)) {
				break;
			}
			if (job.undoable) {
				job.undoPositions.add(at.asLong());
				job.undoOld.add(plant);
				job.undoPlaced.add(Blocks.AIR.defaultBlockState());
			}
			job.changed++;
			cleared++;
		}
		return cleared;
	}

	/** Natural ground cover that hangs on the block below it: grass, ferns, flowers, saplings, mushrooms, canes, carpets of moss. */
	private static boolean loosePlant(BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		return state.canBeReplaced() && !state.is(Blocks.WATER) && !state.is(Blocks.LAVA)
				|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.SWEET_BERRY_BUSH)
				|| state.is(Blocks.MOSS_CARPET) || state.is(Blocks.PALE_MOSS_CARPET) || state.is(Blocks.CACTUS) || state.is(Blocks.SMALL_DRIPLEAF)
				|| state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA) || state.is(Blocks.LILY_PAD);
	}

	/** Records a solid or fluid block a carving position had to leave (see {@link Job#kept}). */
	private static void keep(Job job, int i, BlockPos pos, BlockState state) {
		byte mode = job.mode(i);
		if ((mode == MODE_REMOVE || mode == MODE_NATURAL) && !state.isAir() && (state.canOcclude() || !state.getFluidState().isEmpty())) {
			job.kept.merge(BlockPos.asLong(pos.getX(), 0, pos.getZ()), pos.getY(), Math::max);
		}
	}

	/**
	 * True when a neighbour holds a fluid or a block entity, or lies in an unloaded chunk: removing this block could later
	 * let water or lava in or pop a sign, and nothing may load a chunk.
	 */
	private static boolean touchesUnsafe(ServerLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.values()) {
			next.setWithOffset(pos, direction);
			if (!level.isLoaded(next)) {
				return true;
			}
			BlockState state = level.getBlockState(next);
			if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
				return true;
			}
		}
		return false;
	}

	/** The box around the positions this tick may look at (for the villager query). */
	private static AABB batchBox(Job job, int reads) {
		int end = Math.min(job.positions.length, job.index + reads);
		if (job.index >= end) {
			return new AABB(job.centre);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = job.index; i < end; i++) {
			long packed = job.positions[i];
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}
		return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	/** A job ends (done or cancelled): the record, the restore buffer and the owner's message. */
	private static void finish(MinecraftServer server, Job job, boolean cancelled) {
		sendDone(server, job);
		if (job.changed > 0) {
			if (job.self) {
				KehancuranLog.recordSelf(server, job.owner, job.ownerName, job.specId == null ? "?" : job.specId, job.shape, job.dimension, job.centre,
						job.changed);
			} else {
				KehancuranLog.record(server, job.owner, job.ownerName, job.disciple, job.shape, job.dimension, job.centre, job.changed, job.restore);
			}
		}
		if (job.clear) {
			// Phase 10b (29.6.3): a monument clear never feeds the restore buffer; what it could not reach goes back to the registry
			List<Long> leftPositions = new ArrayList<>();
			List<BlockState> leftStates = new ArrayList<>();
			for (int i : job.deferred) {
				leftPositions.add(job.positions[i]);
				leftStates.add(job.placed == null ? Blocks.AIR.defaultBlockState() : job.placed[i]);
			}
			for (int i = job.index; i < job.positions.length; i++) {
				leftPositions.add(job.positions[i]);
				leftStates.add(job.placed == null ? Blocks.AIR.defaultBlockState() : job.placed[i]);
			}
			try {
				dev.kultivator.pusakalangit.TitahMonuments.onClearFinished(server, job.clearId, job.owner, job.dimension,
						leftPositions.stream().mapToLong(Long::longValue).toArray(), leftStates.toArray(new BlockState[0]));
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: Titah clear callback failed", exception);
			}
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
		int minutes = KultivatorConfig.get().kehancuranUndoMinutes;
		if (job.undoable && !job.undoPositions.isEmpty()) {
			addUndo(server, new Undo(nextUndo++, job, server.overworld().getGameTime() + minutes * 1200L));
		}
		if (job.titah) {
			// Phase 10b (29.6.3, 29.7.1): the placed monument (kept in the saved record) and the DONE callback (re-seating)
			try {
				if (!job.monumentPlaced.isEmpty()) {
					byte[] kinds = new byte[job.monumentPlacedKinds.size()];
					for (int i = 0; i < kinds.length; i++) {
						kinds[i] = job.monumentPlacedKinds.get(i);
					}
					dev.kultivator.pusakalangit.TitahMonuments.onMonumentPlaced(server, job.recordId, job.owner, job.dimension,
							job.monumentPlaced.stream().mapToLong(Long::longValue).toArray(), kinds);
				}
				dev.kultivator.pusakalangit.TitahMonuments.onJobDone(server, job.recordId, job.jobId);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: Titah job callback failed", exception);
			}
		}
		int left = job.restore ? rebuffer(server, job) : 0;
		if (owner == null) {
			return;
		}
		if (!cancelled && job.changed > 0 && (job.self || job.restore)) {
			dev.kultivator.epic.EpicHooks.kehancuran(server.getLevel(job.dimension), owner, null, Vec3.atCenterOf(job.centre), -1, 0.0F, job.restore ? dev.kultivator.epic.EpicIds.KEH_RESTORE : dev.kultivator.epic.EpicIds.KEH_CRATER, job.changed);
		}
		if (job.self && !job.restore) {
			if (cancelled) {
				Msg.chat(owner, Component.translatable("kultivator.kehancuran_diri.stopped", job.changed).withStyle(ChatFormatting.GOLD));
			} else if (job.changed > 0) {
				Msg.actionBar(owner, Component.translatable(job.undoable ? "kultivator.kehancuran_diri.done_undo" : "kultivator.kehancuran_diri.done",
						job.changed, minutes).withColor(0xFF6A5A));
			}
			return;
		}
		if (job.restore) {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran.restored", job.changed).withColor(0x9FF3E6));
			if (left > 0) {
				Msg.chat(owner, Component.translatable("kultivator.kehancuran.restore_left", left).withStyle(ChatFormatting.YELLOW));
			}
		} else if (cancelled) {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran.stopped", job.changed).withStyle(ChatFormatting.GOLD));
		} else if (job.undoable) {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran.done_undo", job.disciple, job.changed, minutes).withStyle(ChatFormatting.RED));
		} else {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran.done", job.disciple, job.changed).withStyle(ChatFormatting.RED));
		}
	}

	// ================================================================== restore

	/**
	 * A restore ended: the blocks it had to wait for (unloaded chunk, someone standing there) and any it never reached go
	 * back to the buffer with the original expiry, so Pulihkan can be pressed again. Returns how many.
	 */
	private static int rebuffer(MinecraftServer server, Job job) {
		List<Integer> rest = new ArrayList<>(job.deferred);
		for (int i = job.index; i < job.positions.length; i++) {
			rest.add(i);
		}
		if (rest.isEmpty() || server.overworld().getGameTime() >= job.expiresAt) {
			return 0;
		}
		long[] positions = new long[rest.size()];
		BlockState[] old = new BlockState[rest.size()];
		BlockState[] placed = new BlockState[rest.size()];
		for (int n = 0; n < rest.size(); n++) {
			int i = rest.get(n);
			positions[n] = job.positions[i];
			old[n] = job.targets[i];
			placed[n] = job.placed == null ? Blocks.AIR.defaultBlockState() : job.placed[i];
		}
		addUndo(server, new Undo(nextUndo++, job, positions, old, placed, job.expiresAt));
		return rest.size();
	}

	/** Keeps a restore buffer; beyond {@link #MAX_UNDO} the oldest is released with a note to its owner (22.11.7). */
	private static void addUndo(MinecraftServer server, Undo undo) {
		UNDO.add(undo);
		while (UNDO.size() > MAX_UNDO) {
			released(server, UNDO.remove(0));
		}
		// Phase 10b (29.16.9): compact bulk buffers together never hold more than twice titahUndoMaxBlocks (memory on weak
		// machines); the oldest bulk buffer is released first
		long limit = 2L * Math.max(1, KultivatorConfig.get().titahUndoMaxBlocks);
		long held = 0L;
		for (Undo each : UNDO) {
			if (each.bulk) {
				held += each.positions.length;
			}
		}
		for (Iterator<Undo> it = UNDO.iterator(); it.hasNext() && held > limit; ) {
			Undo each = it.next();
			if (each.bulk && each != undo) {
				it.remove();
				held -= each.positions.length;
				released(server, each);
			}
		}
	}

	private static void released(MinecraftServer server, Undo oldest) {
		ServerPlayer owner = server.getPlayerList().getPlayer(oldest.owner);
		if (owner != null) {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran_diri.undo_released", oldest.positions.length).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Queues the restore of every cast of {@code owner} still in its window, newest first. Returns how many. */
	static int restore(UUID owner) {
		int queued = 0;
		for (int i = UNDO.size() - 1; i >= 0; i--) {
			Undo undo = UNDO.get(i);
			if (!undo.owner.equals(owner)) {
				continue;
			}
			if (undo.bulk) {
				// Phase 10b (29.16.7 "Bulk Pulihkan"): a bulk restore job, bottom-up per chunk
				if (!bulkHasRoom()) {
					continue;
				}
				UNDO.remove(i);
				submitBulk(BulkJob.restore(undo));
				queued++;
				continue;
			}
			if (!hasRoom()) {
				continue;
			}
			UNDO.remove(i);
			Job job = new Job(undo.owner, undo.ownerName, undo.disciple, undo.shape, undo.dimension, undo.centre, undo.positions, undo.old,
					undo.placed, true, false, false, 0);
			job.expiresAt = undo.expiresAt;
			submit(job);
			queued++;
		}
		return queued;
	}

	/** The owner's restorable casts: {blocks, seconds left} each, newest first. */
	static List<int[]> undoable(MinecraftServer server, UUID owner) {
		List<int[]> list = new ArrayList<>();
		long now = server.overworld().getGameTime();
		for (int i = UNDO.size() - 1; i >= 0; i--) {
			Undo undo = UNDO.get(i);
			if (undo.owner.equals(owner) && undo.expiresAt > now) {
				list.add(new int[]{undo.positions.length, (int) ((undo.expiresAt - now + 19L) / 20L)});
			}
		}
		return list;
	}

	// ================================================================== Phase 10b: the fast bulk carve (DESIGN 29.16.7)

	/**
	 * The bulk carve's chunk ticket (29.16.7 "Ticket"): FULL level, radius 0, no PERSIST flag (never saved), a 200-tick
	 * timeout as a safety net (refreshed every 100 ticks while held). Only ever added to a chunk that is already FULL-loaded:
	 * it holds chunks, it never loads or generates one.
	 */
	public static final TicketType TITAH_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Kultivator.id("titah_carve"),
			new TicketType(200L, TicketType.FLAG_LOADING));

	static final byte PHASE_PLAN = 0;
	static final byte PHASE_CARVE = 1;
	static final byte PHASE_DRAIN = 2;
	static final byte PHASE_DONE = 3;
	/** One running bulk job plus one queued, server-wide (29.16.8 rule 8). */
	public static final int BULK_MAX = 2;
	private static final int TICKET_REFRESH_TICKS = 100;
	/** At most this many floor and rim conversions per chunk (29.16.7 step C4). */
	private static final int SURFACE_FLOOR_PER_CHUNK = 256;
	private static final int SURFACE_RIM_PER_CHUNK = 64;
	/** A DRAIN that waits longer than this for the light engine finishes anyway. */
	private static final int DRAIN_MAX_TICKS = 400;
	/** Shrink rounds of the plan at most (the undo cap still stops the carve exactly). */
	private static final int MAX_SHRINKS = 14;

	private static final Deque<BulkJob> BULK = new ArrayDeque<>();
	/** Permanent carves of this session (29.16.9): owner, game time, blocks; for the Pulihkan line. */
	private record Permanent(UUID owner, long time, long blocks) {
	}

	private static final List<Permanent> PERMANENT = new ArrayList<>();

	/** A bulk restore's positions, grouped by chunk and bottom-up inside each chunk. */
	static final class Restore {
		final long[] positions;
		final BlockState[] old;
		final BlockState[] placed;
		final long[] chunkKeys;
		/** {@code starts[i]} .. {@code starts[i + 1]} are chunk i's positions. */
		final int[] starts;
		final List<Integer> deferred = new ArrayList<>();
		int cursor;

		Restore(long[] positions, BlockState[] old, BlockState[] placed) {
			int n = positions.length;
			// one sort key per record: chunk x (22 bits), chunk z (22 bits), y + 2048 (12 bits); no allocation per comparison
			long[] keys = new long[n];
			int[] order = new int[n];
			for (int i = 0; i < n; i++) {
				long pos = positions[i];
				long cx = (BlockPos.getX(pos) >> 4) + (1L << 21) & 0x3FFFFFL;
				long cz = (BlockPos.getZ(pos) >> 4) + (1L << 21) & 0x3FFFFFL;
				long y = BlockPos.getY(pos) + 2048L & 0xFFFL;
				keys[i] = cx << 34 | cz << 12 | y;
				order[i] = i;
			}
			it.unimi.dsi.fastutil.ints.IntArrays.quickSort(order, (a, b) -> {
				int c = Long.compare(keys[a], keys[b]);
				return c != 0 ? c : Integer.compare(a, b);
			});
			this.positions = new long[n];
			this.old = new BlockState[n];
			this.placed = new BlockState[n];
			List<Long> chunkList = new ArrayList<>();
			List<Integer> firsts = new ArrayList<>();
			long last = Long.MIN_VALUE;
			for (int k = 0; k < n; k++) {
				int i = order[k];
				this.positions[k] = positions[i];
				this.old[k] = old[i];
				this.placed[k] = placed == null || i >= placed.length ? null : placed[i];
				long key = ChunkPos.pack(BlockPos.of(positions[i]));
				if (k == 0 || key != last) {
					chunkList.add(key);
					firsts.add(k);
					last = key;
				}
			}
			this.chunkKeys = chunkList.stream().mapToLong(Long::longValue).toArray();
			this.starts = new int[firsts.size() + 1];
			for (int i = 0; i < firsts.size(); i++) {
				starts[i] = firsts.get(i);
			}
			starts[firsts.size()] = n;
		}
	}

	/** The per-chunk state of the chunk a bulk job works on now. */
	private static final class ChunkWork {
		final ChunkPos pos;
		final int minX;
		final int minZ;
		final boolean[] has = new boolean[256];
		final int[] lo = new int[256];
		final int[] hi = new int[256];
		final byte[] mode = new byte[256];
		final int[] top = new int[256];
		/** Highest and lowest carved y per column ({@link Integer#MIN_VALUE} / MAX_VALUE = none). */
		final int[] carvedTop = new int[256];
		final int[] carvedLow = new int[256];
		/** Per section index: the cells 6-adjacent to a fluid or a block entity, or on a border to an unloaded chunk. */
		@Nullable BitSet[] unsafe;
		@Nullable ProtectedZones.Context zones;
		final Sample sample = new Sample();
		int column;
		int cursorY = Integer.MAX_VALUE;
		long changedHere;
		double sx;
		double sy;
		double sz;
		int yMin = Integer.MAX_VALUE;
		int yMax = Integer.MIN_VALUE;
		/** A restore: the chunk's entity boxes (read once). */
		@Nullable List<AABB> boxes;

		ChunkWork(ChunkPos pos) {
			this.pos = pos;
			this.minX = pos.getMinBlockX();
			this.minZ = pos.getMinBlockZ();
			java.util.Arrays.fill(carvedTop, Integer.MIN_VALUE);
			java.util.Arrays.fill(carvedLow, Integer.MAX_VALUE);
		}

		boolean unsafe(LevelChunk chunk, int lx, int y, int lz) {
			if (unsafe == null) {
				return false;
			}
			int si = chunk.getSectionIndex(y);
			if (si < 0 || si >= unsafe.length) {
				return true;
			}
			BitSet bits = unsafe[si];
			return bits != null && bits.get((y & 15) << 8 | lz << 4 | lx);
		}

		void mark(LevelChunk chunk, int lx, int y, int lz) {
			if (unsafe == null || lx < 0 || lx > 15 || lz < 0 || lz > 15 || y < chunk.getMinY() || y > chunk.getMaxY()) {
				return;
			}
			int si = chunk.getSectionIndex(y);
			if (si < 0 || si >= unsafe.length) {
				return;
			}
			BitSet bits = unsafe[si];
			if (bits == null) {
				bits = new BitSet(4096);
				unsafe[si] = bits;
			}
			bits.set((y & 15) << 8 | lz << 4 | lx);
		}

		void markAround(LevelChunk chunk, int lx, int y, int lz) {
			mark(chunk, lx, y, lz);
			mark(chunk, lx + 1, y, lz);
			mark(chunk, lx - 1, y, lz);
			mark(chunk, lx, y + 1, lz);
			mark(chunk, lx, y - 1, lz);
			mark(chunk, lx, y, lz + 1);
			mark(chunk, lx, y, lz - 1);
		}
	}

	/** One bulk carve (Tingkat 2-9 or {@code pahat_cepat}) or one bulk restore; the only writer of the bulk path. */
	static final class BulkJob {
		final UUID owner;
		final String ownerName;
		final String specId;
		final ResourceKey<Level> dimension;
		final BlockPos centre;
		final Vec3 castPos;
		final int tier;
		final boolean restore;
		final boolean permanent;
		final boolean undo;
		BulkShape shape;
		ChunkPos[] chunks = new ChunkPos[0];
		int chunkIndex;
		byte phase = PHASE_PLAN;
		/** Chunks carrying our ticket (keys of {@code ChunkPos.pack}). */
		final LongOpenHashSet held = new LongOpenHashSet();
		/** Light waits before the resend of each finished chunk. */
		final ArrayDeque<CompletableFuture<?>> inFlight = new ArrayDeque<>();
		@Nullable ChunkWork work;
		long changed;
		long surfaceChanged;
		long kept;
		long protectedColumns;
		long estimate;
		int chunksDone;
		int chunksSkipped;
		long[] undoPos = new long[0];
		BlockState[] undoOld = new BlockState[0];
		BlockState[] undoPlaced = new BlockState[0];
		int undoCount;
		final ServerBossEvent bar;
		final int jobId = nextJobId++;
		int casterEntityId = -1;
		int specNet = -1;
		Dampak.Palette palette = Dampak.Palette.NONE;
		int debrisFlags;
		int recordId = -1;
		/** The loaded-land clip and the extra shrink factor of the plan (29.16.7 steps P2, P4). */
		double clip = 1.0D;
		double shrink = 1.0D;
		int requestedOuter;
		@Nullable Restore restoreData;
		long restoreExpires = Long.MAX_VALUE;
		boolean cancelled;
		boolean capped;
		boolean pausedTold;
		boolean shrinkTold;
		int shrinks;
		/** PLAN step P4: the chunk the estimate reached and its running sum. */
		int planIndex;
		long planSum;
		boolean planChunksReady;
		long nanos;
		int ticksWorked;
		int drainTicks;
		long lastFxTick = Long.MIN_VALUE;

		BulkJob(UUID owner, String ownerName, String specId, ResourceKey<Level> dimension, BlockPos centre, Vec3 castPos, int tier, boolean restore,
				boolean permanent, BulkShape shape) {
			this.owner = owner;
			this.ownerName = ownerName;
			this.specId = specId;
			this.dimension = dimension;
			this.centre = centre.immutable();
			this.castPos = castPos;
			this.tier = tier;
			this.restore = restore;
			this.permanent = permanent;
			// tiers 2-5 (and a test without "permanen") keep a compact undo; tiers 6-9 never (29.16.9)
			this.undo = !restore && !permanent && KultivatorConfig.get().kehancuranUndoMinutes > 0;
			this.shape = shape;
			this.requestedOuter = shape.outer();
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(restore ? "kultivator.kehancuran.bulk.bar_restore"
					: permanent ? "kultivator.kehancuran.bulk.bar_permanent" : "kultivator.kehancuran.bulk.bar", tier),
					restore ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
			this.bar.setProgress(0.0F);
		}

		/** A carve job of {@code caster}'s own decree (Kehancuran.submitTitahBulk); never throws. */
		static BulkJob carve(ServerPlayer caster, String specId, BulkShape shape, int tier, boolean permanent, Vec3 castPos) {
			BulkJob job = new BulkJob(caster.getUUID(), caster.getName().getString(), specId, caster.level().dimension(), shape.impact(), castPos,
					tier, false, permanent, shape);
			job.casterEntityId = caster.getId();
			return job;
		}

		/** A bulk Pulihkan of {@code undo}: only chunks loaded now are worked, the rest goes back to the buffer. */
		static BulkJob restore(Undo undo) {
			BulkJob job = new BulkJob(undo.owner, undo.ownerName, undo.specId == null ? "?" : undo.specId, undo.dimension, undo.centre,
					Vec3.atCenterOf(undo.centre), undo.tier, true, false, BulkShape.of(undo.centre, Vec3.ZERO, List.of()));
			job.restoreData = new Restore(undo.positions, undo.old, undo.placed);
			job.restoreExpires = undo.expiresAt;
			job.chunks = new ChunkPos[job.restoreData.chunkKeys.length];
			for (int i = 0; i < job.chunks.length; i++) {
				job.chunks[i] = ChunkPos.unpack(job.restoreData.chunkKeys[i]);
			}
			job.phase = PHASE_CARVE;
			return job;
		}

		int packetFlags(MinecraftServer server) {
			int flags = debrisFlags | Payloads.DampakDebris.FLAG_SELF;
			if (server.getAverageTickTimeNanos() > LOW_LOAD_NANOS) {
				flags |= Payloads.DampakDebris.FLAG_LOW_LOAD;
			}
			return flags;
		}

		/** Appends one undo record (null placed = AIR); false when the undo cap is reached (the job stops). */
		boolean record(long pos, BlockState old, @Nullable BlockState placed) {
			if (!undo) {
				return true;
			}
			int cap = Math.max(0, KultivatorConfig.get().titahUndoMaxBlocks);
			if (undoCount >= cap) {
				return false;
			}
			if (undoCount == undoPos.length) {
				int size = (int) Math.min(cap, Math.max(4096L, undoPos.length + (long) (undoPos.length >> 1)));
				undoPos = java.util.Arrays.copyOf(undoPos, size);
				undoOld = java.util.Arrays.copyOf(undoOld, size);
				undoPlaced = java.util.Arrays.copyOf(undoPlaced, size);
			}
			undoPos[undoCount] = pos;
			undoOld[undoCount] = old;
			undoPlaced[undoCount] = placed == null || placed.isAir() ? null : placed;
			undoCount++;
			return true;
		}

		/** True while another undo record fits (always for a job without undo). */
		boolean undoRoom() {
			return !undo || undoCount < Math.max(0, KultivatorConfig.get().titahUndoMaxBlocks);
		}
	}

	/** The running bulk job for {@code pahat_cepat info} (29.16.15), or null. */
	public record BulkInfo(byte phase, int tier, boolean restore, boolean permanent, int chunksDone, int chunksTotal, int chunksSkipped, long changed,
			long surface, long kept, long protectedColumns, long estimate, double msPerTick, int light, int tickets, double clip, double shrink,
			int outer, int requestedOuter, int queued, String ownerName) {
	}

	public static @Nullable BulkInfo bulkInfo() {
		BulkJob job = BULK.peekFirst();
		if (job == null) {
			return null;
		}
		double ms = job.ticksWorked <= 0 ? 0.0D : job.nanos / 1.0E6D / job.ticksWorked;
		return new BulkInfo(job.phase, job.tier, job.restore, job.permanent, job.chunksDone, job.chunks.length, job.chunksSkipped, job.changed,
				job.surfaceChanged, job.kept, job.protectedColumns, job.estimate, ms, job.inFlight.size(), job.held.size(), job.clip, job.shrink,
				job.shape.outer(), job.requestedOuter, Math.max(0, BULK.size() - 1), job.ownerName);
	}

	/** True when another bulk job fits (1 running plus 1 queued). */
	static boolean bulkHasRoom() {
		return BULK.size() < BULK_MAX;
	}

	/** Queues a bulk job (29.16.7); false when the bulk queue is full. */
	static boolean submitBulk(BulkJob job) {
		if (job == null || !bulkHasRoom()) {
			return false;
		}
		BULK.addLast(job);
		return true;
	}

	/**
	 * Cancels the bulk jobs of {@code owner} (null = every destructive bulk job): a running one stops after the current
	 * column (its chunk is still resent, so the clients match the server), queued ones are dropped. Restores keep running.
	 * Returns how many were stopped.
	 */
	public static int cancelBulk(MinecraftServer server, @Nullable UUID owner) {
		int stopped = 0;
		BulkJob running = BULK.peekFirst();
		for (Iterator<BulkJob> it = BULK.iterator(); it.hasNext(); ) {
			BulkJob job = it.next();
			if (job.restore || owner != null && !job.owner.equals(owner)) {
				continue;
			}
			stopped++;
			if (job == running && job.phase != PHASE_PLAN) {
				job.cancelled = true;
				continue;
			}
			it.remove();
			finishBulk(server, job, true);
		}
		return stopped;
	}

	/** Permanent carves of {@code owner} inside the undo window (the Pulihkan line of 29.16.9). */
	static int permanentInWindow(MinecraftServer server, UUID owner) {
		long now = server.overworld().getGameTime();
		long window = Math.max(1, KultivatorConfig.get().kehancuranUndoMinutes) * 1200L;
		PERMANENT.removeIf(entry -> now - entry.time() > window);
		int n = 0;
		for (Permanent entry : PERMANENT) {
			if (entry.owner().equals(owner)) {
				n++;
			}
		}
		return n;
	}

	/** One tick of the running bulk job; any failure ends it safely (tickets released) instead of breaking the server tick. */
	private static void tickBulk(MinecraftServer server) {
		BulkJob job = BULK.peekFirst();
		if (job == null) {
			return;
		}
		try {
			tickBulkJob(server, job);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: bulk carve of {} failed; stopped", job.specId, exception);
			BULK.remove(job);
			try {
				finishBulk(server, job, true);
			} catch (RuntimeException inner) {
				Kultivator.LOGGER.error("Kultivator: bulk carve cleanup failed", inner);
				releaseAll(server, job);
			}
		}
	}

	private static void tickBulkJob(MinecraftServer server, BulkJob job) {
		ServerLevel level = server.getLevel(job.dimension);
		if (level == null) {
			BULK.remove(job);
			finishBulk(server, job, true);
			return;
		}
		KultivatorConfig config = KultivatorConfig.get();
		long tick = server.getTickCount();
		ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
		if (tick % 20 == 0) {
			if (owner != null) {
				job.bar.addPlayer(owner);
			}
			// P1: the owner's own switch went off (a restore only puts the world back and keeps running)
			if (!job.restore && owner != null && !Kehancuran.selfOn(owner)) {
				job.cancelled = true;
			}
		}
		if (tick % TICKET_REFRESH_TICKS == 0) {
			// re-adding an equal ticket resets its timeout (TicketStorage.addTicket)
			for (long key : job.held) {
				level.getChunkSource().addTicketWithRadius(TITAH_TICKET, ChunkPos.unpack(key), 0);
			}
		}
		drainFinished(job);
		if (job.phase == PHASE_DRAIN) {
			job.drainTicks++;
			if (job.inFlight.isEmpty() || job.drainTicks > DRAIN_MAX_TICKS) {
				job.phase = PHASE_DONE;
				BULK.remove(job);
				finishBulk(server, job, job.cancelled);
			}
			return;
		}
		long average = server.getAverageTickTimeNanos();
		// a cancelled job is never held by the pause: it only resends its current chunk and lets its tickets go
		if (!job.cancelled && average > config.kehancuranMsptPause * 1_000_000L) {
			if (!job.pausedTold && owner != null) {
				job.pausedTold = true;
				Msg.actionBar(owner, Component.translatable("kultivator.kehancuran.paused").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		long budget = config.titahBulkMillisPerTick * 1_000_000L;
		if (average > config.titahBulkMsptSlow * 1_000_000L) {
			budget /= 2L;
		}
		long start = System.nanoTime();
		long deadline = start + budget;
		if (job.phase == PHASE_PLAN) {
			if (job.cancelled) {
				BULK.remove(job);
				finishBulk(server, job, true);
				return;
			}
			if (!tickPlan(server, level, job, owner, deadline)) {
				job.nanos += System.nanoTime() - start;
				job.ticksWorked++;
				return;
			}
		}
		if (job.phase == PHASE_CARVE) {
			if (job.restore) {
				tickRestoreChunks(server, level, job, deadline);
			} else {
				tickCarveChunks(server, level, job, owner, deadline);
			}
		}
		job.nanos += System.nanoTime() - start;
		job.ticksWorked++;
		job.bar.setProgress(job.chunks.length == 0 ? 1.0F : Math.min(1.0F, job.chunksDone / (float) job.chunks.length));
	}

	/** Drops the light waits that are done (a failed one is logged and dropped). */
	private static void drainFinished(BulkJob job) {
		for (Iterator<CompletableFuture<?>> it = job.inFlight.iterator(); it.hasNext(); ) {
			CompletableFuture<?> future = it.next();
			if (!future.isDone()) {
				continue;
			}
			if (future.isCompletedExceptionally()) {
				try {
					future.join();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.warn("Kultivator: a bulk carve chunk resend failed", exception);
				}
			}
			it.remove();
		}
	}

	// ---------------------------------------------------------------- PLAN (P1-P4)

	/** One tick of the plan; true when the plan is done and the carve may start this tick. */
	private static boolean tickPlan(MinecraftServer server, ServerLevel level, BulkJob job, @Nullable ServerPlayer owner, long deadline) {
		KultivatorConfig config = KultivatorConfig.get();
		ServerChunkCache cache = level.getChunkSource();
		if (!job.planChunksReady) {
			// P2: clip to the loaded land (nothing is ever loaded or generated)
			if (job.clip >= 1.0D && job.shrinks == 0) {
				double loaded = loadedReach(cache, job.shape);
				if (loaded < 3.0D) {
					job.phase = PHASE_DONE;
					BULK.remove(job);
					finishBulk(server, job, false);
					return false;
				}
				int outer = Math.max(1, job.shape.outer());
				if (loaded < outer) {
					job.clip = loaded / outer;
					job.shape = job.shape.scaled(job.clip);
					if (owner != null) {
						Msg.chat(owner, Component.translatable("kultivator.kehancuran.bulk.clipped", job.shape.outer(), job.requestedOuter)
								.withColor(0xFFB0A0));
					}
				}
			}
			// the chunk cap: shrink until the disc fits titahBulkMaxChunks
			int maxChunks = config.titahBulkMaxChunks;
			while (job.shape.chunkEstimate() > maxChunks && job.shrinks < MAX_SHRINKS * 2) {
				job.shape = job.shape.scaled(0.9D);
				job.shrink *= 0.9D;
				job.shrinks++;
			}
			// P3: only loaded chunks, at most titahBulkMaxChunks, each held by our ticket
			releaseAll(server, job);
			List<ChunkPos> kept = new ArrayList<>();
			for (ChunkPos pos : job.shape.chunksCentreOut()) {
				if (kept.size() >= maxChunks) {
					break;
				}
				if (cache.getChunkNow(pos.x(), pos.z()) != null) {
					kept.add(pos);
				}
			}
			for (ChunkPos pos : kept) {
				cache.addTicketWithRadius(TITAH_TICKET, pos, 0);
				job.held.add(pos.pack());
			}
			job.chunks = kept.toArray(new ChunkPos[0]);
			job.chunkIndex = 0;
			job.planIndex = 0;
			job.planSum = 0L;
			job.planChunksReady = true;
		}
		// P4: the estimate from the heightmaps (one read per sampled column, no block reads), time-budgeted
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		while (job.planIndex < job.chunks.length) {
			ChunkPos pos = job.chunks[job.planIndex++];
			LevelChunk chunk = cache.getChunkNow(pos.x(), pos.z());
			if (chunk == null) {
				continue;
			}
			for (int lx = 0; lx < 16; lx += 2) {
				for (int lz = 0; lz < 16; lz += 2) {
					int x = pos.getMinBlockX() + lx;
					int z = pos.getMinBlockZ() + lz;
					int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
					job.planSum += 4L * job.shape.estimate(x, z, top, minY, maxY);
				}
			}
			if (System.nanoTime() > deadline) {
				return false;
			}
		}
		job.estimate = job.planSum;
		long cap = config.titahBulkMaxBlocks;
		if (job.undo) {
			cap = Math.min(cap, Math.max(0, config.titahUndoMaxBlocks));
		}
		if (job.estimate > cap && job.shrinks < MAX_SHRINKS && cap > 0) {
			// the volume grows with the area (depths are kept): shrink the radius by about sqrt(cap / estimate)
			double f = Math.max(0.3D, Math.min(0.9D, Math.sqrt((double) cap / job.estimate) * 0.97D));
			job.shape = job.shape.scaled(f);
			job.shrink *= f;
			job.shrinks++;
			job.planChunksReady = false;
			if (!job.shrinkTold && owner != null) {
				job.shrinkTold = true;
				Msg.chat(owner, Component.translatable(job.undo ? "kultivator.kehancuran.bulk.shrunk_undo" : "kultivator.kehancuran.bulk.shrunk")
						.withColor(0xFFB0A0));
			}
			return false;
		}
		job.phase = PHASE_CARVE;
		if (owner != null) {
			job.bar.addPlayer(owner);
		}
		return true;
	}

	/**
	 * The distance from the impact to the nearest chunk that is not loaded, minus 2 (29.16.7 step P2): chunk rings around
	 * the impact chunk up to the shape's reach; {@code getChunkNow} only (nothing loads).
	 */
	private static double loadedReach(ServerChunkCache cache, BulkShape shape) {
		BlockPos impact = shape.impact();
		int icx = impact.getX() >> 4;
		int icz = impact.getZ() >> 4;
		double ix = impact.getX() + 0.5D;
		double iz = impact.getZ() + 0.5D;
		int rings = shape.outer() / 16 + 2;
		double nearest = Double.MAX_VALUE;
		int foundAt = -1;
		for (int k = 0; k <= rings; k++) {
			if (foundAt >= 0 && k > foundAt + 1) {
				break;
			}
			for (int dx = -k; dx <= k; dx++) {
				for (int dz = -k; dz <= k; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != k) {
						continue;
					}
					if (cache.getChunkNow(icx + dx, icz + dz) == null) {
						nearest = Math.min(nearest, Math.sqrt(BulkShape.nearestSq(ix, iz, icx + dx, icz + dz)));
						if (foundAt < 0) {
							foundAt = k;
						}
					}
				}
			}
		}
		return nearest == Double.MAX_VALUE ? shape.outer() : nearest - 2.0D;
	}

	// ---------------------------------------------------------------- CARVE (C1-C6)

	private static void tickCarveChunks(MinecraftServer server, ServerLevel level, BulkJob job, @Nullable ServerPlayer owner, long deadline) {
		ServerChunkCache cache = level.getChunkSource();
		KultivatorConfig config = KultivatorConfig.get();
		while (true) {
			if (job.work == null) {
				if (job.cancelled || job.capped || job.chunkIndex >= job.chunks.length) {
					job.phase = PHASE_DRAIN;
					return;
				}
				// C1: the light engine sets the pace
				if (job.inFlight.size() >= config.titahBulkLightChunks) {
					return;
				}
				ChunkPos pos = job.chunks[job.chunkIndex++];
				LevelChunk chunk = cache.getChunkNow(pos.x(), pos.z());
				if (chunk == null) {
					job.chunksSkipped++;
					release(cache, job, pos);
					continue;
				}
				ChunkWork work = startChunk(server, level, job, chunk, owner);
				if (work == null) {
					// nothing of the shape in this chunk
					release(cache, job, pos);
					job.chunksDone++;
					continue;
				}
				job.work = work;
			}
			ChunkWork work = job.work;
			LevelChunk chunk = cache.getChunkNow(work.pos.x(), work.pos.z());
			if (chunk == null) {
				// impossible while held, but checked: nothing more is done there
				job.chunksSkipped++;
				release(cache, job, work.pos);
				job.work = null;
				continue;
			}
			if (!carveColumns(level, job, work, chunk, deadline)) {
				return;
			}
			if (!job.cancelled) {
				surfacePass(level, job, work, chunk);
			}
			endChunk(server, level, job, work);
			job.work = null;
			if (System.nanoTime() > deadline) {
				return;
			}
		}
	}

	/** C2: the chunk's spans, zones and unsafe mask; null when the shape does not touch it. */
	private static @Nullable ChunkWork startChunk(MinecraftServer server, ServerLevel level, BulkJob job, LevelChunk chunk, @Nullable ServerPlayer owner) {
		ChunkWork work = new ChunkWork(chunk.getPos());
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		int[] out = new int[3];
		boolean any = false;
		for (int i = 0; i < 256; i++) {
			int x = work.minX + (i & 15);
			int z = work.minZ + (i >> 4);
			int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			work.top[i] = top;
			if (!job.shape.span(x, z, top, minY, maxY, out)) {
				continue;
			}
			work.has[i] = true;
			work.lo[i] = out[0];
			work.hi[i] = out[1];
			work.mode[i] = (byte) out[2];
			work.yMin = Math.min(work.yMin, out[0]);
			// the walk starts at the column's top (kept blocks above hi stop it), so the mask covers up to there
			work.yMax = Math.max(work.yMax, Math.max(out[1], top));
			any = true;
		}
		if (!any) {
			return null;
		}
		List<Vec3> columns = new ArrayList<>(2);
		columns.add(job.castPos);
		if (owner != null && owner.level() == level) {
			columns.add(owner.position());
		}
		AABB box = new AABB(work.minX, work.yMin, work.minZ, work.minX + 16, work.yMax + 1, work.minZ + 16);
		work.zones = new ProtectedZones.Context(level, box, job.owner, columns);
		buildUnsafe(level, work, chunk);
		return work;
	}

	/**
	 * The unsafe mask of 29.16.7 step C2: every cell 6-adjacent to a fluid (scanned only in sections with
	 * {@code hasFluid()}) or a block entity, in this chunk and across the border of the four neighbours; a border next to a
	 * neighbour that is not loaded is unsafe as a whole. The old {@code touchesUnsafe} rule, made cheap.
	 */
	private static void buildUnsafe(ServerLevel level, ChunkWork work, LevelChunk chunk) {
		work.unsafe = new BitSet[chunk.getSectionsCount()];
		int yFrom = Math.max(chunk.getMinY(), work.yMin - 1);
		int yTo = Math.min(chunk.getMaxY(), work.yMax + 1);
		int sFrom = chunk.getSectionIndex(yFrom);
		int sTo = chunk.getSectionIndex(yTo);
		LevelChunkSection[] sections = chunk.getSections();
		for (int si = Math.max(0, sFrom - 1); si <= Math.min(sections.length - 1, sTo + 1); si++) {
			LevelChunkSection section = sections[si];
			if (section == null || !section.hasFluid()) {
				continue;
			}
			int baseY = chunk.getMinY() + si * 16;
			for (int ly = 0; ly < 16; ly++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						if (!section.getBlockState(lx, ly, lz).getFluidState().isEmpty()) {
							work.markAround(chunk, lx, baseY + ly, lz);
						}
					}
				}
			}
		}
		for (BlockPos pos : chunk.getBlockEntitiesPos()) {
			if (pos.getY() >= yFrom - 1 && pos.getY() <= yTo + 1) {
				work.markAround(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
			}
		}
		ServerChunkCache cache = level.getChunkSource();
		int cx = work.pos.x();
		int cz = work.pos.z();
		// {dx, dz, the neighbour's border coordinate, our border coordinate}; along x for north/south, along z for west/east
		int[][] sides = {{-1, 0, 15, 0}, {1, 0, 0, 15}, {0, -1, 15, 0}, {0, 1, 0, 15}};
		for (int[] side : sides) {
			LevelChunk next = cache.getChunkNow(cx + side[0], cz + side[1]);
			boolean alongZ = side[0] != 0;
			if (next == null) {
				for (int y = yFrom; y <= yTo; y++) {
					for (int a = 0; a < 16; a++) {
						if (alongZ) {
							work.mark(chunk, side[3], y, a);
						} else {
							work.mark(chunk, a, y, side[3]);
						}
					}
				}
				continue;
			}
			LevelChunkSection[] theirs = next.getSections();
			for (int si = Math.max(0, sFrom); si <= Math.min(theirs.length - 1, sTo); si++) {
				LevelChunkSection section = theirs[si];
				if (section == null || !section.hasFluid()) {
					continue;
				}
				int baseY = next.getMinY() + si * 16;
				for (int ly = 0; ly < 16; ly++) {
					for (int a = 0; a < 16; a++) {
						BlockState state = alongZ ? section.getBlockState(side[2], ly, a) : section.getBlockState(a, ly, side[2]);
						if (!state.getFluidState().isEmpty()) {
							if (alongZ) {
								work.mark(chunk, side[3], baseY + ly, a);
							} else {
								work.mark(chunk, a, baseY + ly, side[3]);
							}
						}
					}
				}
			}
			for (BlockPos pos : next.getBlockEntitiesPos()) {
				int lx = pos.getX() & 15;
				int lz = pos.getZ() & 15;
				if (alongZ && lx == side[2]) {
					work.mark(chunk, side[3], pos.getY(), lz);
				} else if (!alongZ && lz == side[2]) {
					work.mark(chunk, lx, pos.getY(), side[3]);
				}
			}
		}
	}

	/** C3: carves the chunk's columns from {@code work.column}; false when the deadline came first (resumed next tick). */
	private static boolean carveColumns(ServerLevel level, BulkJob job, ChunkWork work, LevelChunk chunk, long deadline) {
		KultivatorConfig config = KultivatorConfig.get();
		LevelChunkSection[] sections = chunk.getSections();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		long blockCap = config.titahBulkMaxBlocks;
		ProtectedZones.Context zones = work.zones;
		while (work.column < 256) {
			if (job.cancelled || job.capped) {
				return true;
			}
			int i = work.column;
			if (!work.has[i]) {
				work.column++;
				work.cursorY = Integer.MAX_VALUE;
				continue;
			}
			int lx = i & 15;
			int lz = i >> 4;
			int x = work.minX + lx;
			int z = work.minZ + lz;
			int lo = work.lo[i];
			int hi = work.hi[i];
			byte mode = work.mode[i];
			if (work.cursorY == Integer.MAX_VALUE) {
				// a protected zone keeps the whole column standing: "carve around" (29.16.8 rule 5)
				ProtectedZones.Zone zone = zones == null ? null : zones.columnZone(x, z, lo, Math.max(hi, work.top[i]));
				if (zone != null) {
					job.protectedColumns++;
					work.column++;
					continue;
				}
			}
			int y = Math.min(work.top[i], work.cursorY);
			int reads = 0;
			boolean stop = false;
			while (y >= lo) {
				int si = chunk.getSectionIndex(y);
				LevelChunkSection section = si >= 0 && si < sections.length ? sections[si] : null;
				if (section == null || section.hasOnlyAir()) {
					y = chunk.getMinY() + si * 16 - 1;
					continue;
				}
				BlockState state = section.getBlockState(lx, y & 15, lz);
				if (state.isAir()) {
					y--;
					continue;
				}
				cursor.set(x, y, z);
				boolean keep = y > hi || work.unsafe(chunk, lx, y, lz) || ProtectedZones.neverTouch(level, cursor, state)
						|| mode == MODE_NATURAL && !Shapes.natural(state) && !loosePlant(state)
						|| mode == MODE_STRIP && !state.is(Shapes.STRIPPABLE) && !loosePlant(state);
				if (keep) {
					job.kept++;
					if (Shapes.roofs(state)) {
						// nothing under a kept roof is carved: no sealed hollow, no unsupported fluid
						stop = true;
						break;
					}
					y--;
					continue;
				}
				if (!job.undoRoom() || job.changed + job.surfaceChanged >= blockCap) {
					job.capped = true;
					stop = true;
					break;
				}
				BlockState old = chunk.setBlockState(cursor, air, FLAGS);
				if (old != null) {
					long packed = BlockPos.asLong(x, y, z);
					job.changed++;
					work.changedHere++;
					if (PoiTypes.hasPoi(old)) {
						level.updatePOIOnBlockStateChange(cursor.immutable(), old, air);
					}
					job.record(packed, old, null);
					work.sample.add(packed, Block.getId(old), level.getRandom().nextInt(Integer.MAX_VALUE));
					work.carvedTop[i] = Math.max(work.carvedTop[i], y);
					work.carvedLow[i] = Math.min(work.carvedLow[i], y);
					work.sx += x + 0.5D;
					work.sy += y + 0.5D;
					work.sz += z + 0.5D;
				}
				y--;
				if (++reads >= 64) {
					reads = 0;
					if (System.nanoTime() > deadline) {
						work.cursorY = y;
						return false;
					}
				}
			}
			work.column++;
			work.cursorY = Integer.MAX_VALUE;
			if (stop && job.capped) {
				return true;
			}
			if (System.nanoTime() > deadline) {
				return work.column >= 256;
			}
		}
		return true;
	}

	/**
	 * C4: Kawah Lebur on this chunk's carved columns (only with a palette, i.e. {@code kawahOn} and
	 * {@code kehancuranSurfacePass}): the floor block under each column's lowest carved cell and the wall block of an edge
	 * column (inside this chunk), natural blocks only, never never-touched or unsafe, no magma near the caster or under a
	 * standing player; at most 256 floor and 64 rim blocks; recorded in the undo as (old, new).
	 */
	private static void surfacePass(ServerLevel level, BulkJob job, ChunkWork work, LevelChunk chunk) {
		if (job.palette == Dampak.Palette.NONE || work.changedHere <= 0 || !KultivatorConfig.get().kehancuranSurfacePass) {
			return;
		}
		long seed = job.centre.asLong() * 0x9E3779B97F4A7C15L;
		Vec3 flat = new Vec3(job.centre.getX() + 0.5D - job.castPos.x, 0.0D, job.centre.getZ() + 0.5D - job.castPos.z);
		flat = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(job.owner);
		Vec3 casterNow = caster != null && caster.level() == level ? caster.position() : null;
		int floor = 0;
		int rim = 0;
		int minY = level.getMinY() + BulkShape.FLOOR_MARGIN;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 256 && (floor < SURFACE_FLOOR_PER_CHUNK || rim < SURFACE_RIM_PER_CHUNK); i++) {
			if (work.carvedLow[i] == Integer.MAX_VALUE) {
				continue;
			}
			int lx = i & 15;
			int lz = i >> 4;
			int x = work.minX + lx;
			int z = work.minZ + lz;
			if (floor < SURFACE_FLOOR_PER_CHUNK) {
				int y = work.carvedLow[i] - 1;
				if (y >= minY && convert(level, job, work, chunk, cursor.set(x, y, z), lx, lz, false, seed, flat, casterNow)) {
					floor++;
				}
			}
			if (rim < SURFACE_RIM_PER_CHUNK) {
				int y = work.carvedTop[i];
				int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] way : ways) {
					int nx = lx + way[0];
					int nz = lz + way[1];
					if (nx < 0 || nx > 15 || nz < 0 || nz > 15) {
						continue;
					}
					int j = nz << 4 | nx;
					if (work.carvedLow[j] != Integer.MAX_VALUE && work.carvedLow[j] <= y) {
						continue;
					}
					if (y >= minY && convert(level, job, work, chunk, cursor.set(work.minX + nx, y, work.minZ + nz), nx, nz, true, seed, flat, casterNow)) {
						rim++;
						break;
					}
				}
			}
		}
	}

	/** One Kawah Lebur conversion at {@code pos} (chunk-local lx, lz); true when the block changed. */
	private static boolean convert(ServerLevel level, BulkJob job, ChunkWork work, LevelChunk chunk, BlockPos.MutableBlockPos pos, int lx, int lz,
			boolean rim, long seed, Vec3 axis, @Nullable Vec3 casterNow) {
		BlockState state = chunk.getBlockState(pos);
		if (state.isAir() || !Shapes.natural(state) || ProtectedZones.neverTouch(level, pos, state) || work.unsafe(chunk, lx, pos.getY(), lz)) {
			return false;
		}
		Vec3 at = Vec3.atCenterOf(pos);
		boolean magmaOk = at.distanceTo(job.castPos) >= Surface.MAGMA_SAFE && (casterNow == null || at.distanceTo(casterNow) >= Surface.MAGMA_SAFE);
		BlockState to = Surface.target(job.palette, state, rim, pos, seed, magmaOk, job.centre, axis);
		if (to == null || to.is(state.getBlock())) {
			return false;
		}
		if (Surface.magma(to) && standing(level, pos)) {
			to = Blocks.BLACKSTONE.defaultBlockState();
		}
		if (!job.undoRoom()) {
			job.capped = true;
			return false;
		}
		BlockState old = chunk.setBlockState(pos, to, FLAGS);
		if (old == null) {
			return false;
		}
		if (PoiTypes.hasPoi(old) || PoiTypes.hasPoi(to)) {
			level.updatePOIOnBlockStateChange(pos.immutable(), old, to);
		}
		job.record(pos.asLong(), old, to);
		job.surfaceChanged++;
		return true;
	}

	/** C5: debris, dust and sound, the resend after the light settles, the ticket released, the progress. */
	private static void endChunk(MinecraftServer server, ServerLevel level, BulkJob job, ChunkWork work) {
		long now = server.getTickCount();
		if (work.changedHere > 0) {
			Vec3 centroid = new Vec3(work.sx / work.changedHere, work.sy / work.changedHere, work.sz / work.changedHere);
			if (!job.restore) {
				bulkDebris(server, level, job, work.sample, centroid);
			}
			if (job.lastFxTick != now) {
				job.lastFxTick = now;
				int count = (int) Math.min(Integer.MAX_VALUE, work.changedHere);
				Fx.send(level, centroid, FxType.KEHANCURAN, job.restore ? KehancuranFxIds.SHIMMER : KehancuranFxIds.DUST, count,
						(float) Math.min(8.0D, 1.0D + Math.sqrt(count)));
			}
			if (now % 10 == 0) {
				level.playSound(null, centroid.x, centroid.y, centroid.z, job.restore ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.DEEPSLATE_BREAK,
						SoundSource.BLOCKS, 1.2F, job.restore ? 1.4F : 0.6F);
			}
			int cx = work.pos.x();
			int cz = work.pos.z();
			ResourceKey<Level> dimension = job.dimension;
			job.inFlight.add(level.getChunkSource().getLightEngine().waitForPendingTasks(cx, cz)
					.thenRunAsync(() -> resend(server, dimension, cx, cz), server));
		}
		release(level.getChunkSource(), job, work.pos);
		job.chunksDone++;
	}

	/** Sends the finished chunk again to everyone who has it (the light has settled; the clients match the server). */
	private static void resend(MinecraftServer server, ResourceKey<Level> dimension, int cx, int cz) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return;
		}
		LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null) {
			return;
		}
		List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false);
		if (players.isEmpty()) {
			return;
		}
		ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
		for (ServerPlayer player : players) {
			player.connection.send(packet);
		}
	}

	/** One CARVE debris batch of a finished chunk to the players within 96 blocks of it. */
	private static void bulkDebris(MinecraftServer server, ServerLevel level, BulkJob job, Sample sample, Vec3 near) {
		if (sample.count <= 0) {
			return;
		}
		BlockPos base = job.centre;
		int[] offsets = new int[sample.count];
		int[] states = new int[sample.count];
		int n = 0;
		for (int i = 0; i < sample.count; i++) {
			BlockPos pos = BlockPos.of(sample.positions[i]);
			int dx = pos.getX() - base.getX();
			int dy = pos.getY() - base.getY();
			int dz = pos.getZ() - base.getZ();
			if (Math.abs(dx) > 511 || Math.abs(dy) > 511 || Math.abs(dz) > 511) {
				continue;
			}
			offsets[n] = Payloads.DampakDebris.pack(dx, dy, dz);
			states[n] = sample.states[i];
			n++;
		}
		if (n == 0) {
			return;
		}
		if (n < offsets.length) {
			offsets = java.util.Arrays.copyOf(offsets, n);
			states = java.util.Arrays.copyOf(states, n);
		}
		Vec3 centre = Vec3.atCenterOf(base);
		broadcast(level, near, new Payloads.DampakDebris(job.jobId, Payloads.DampakDebris.CARVE, job.casterEntityId, job.specNet, centre.x, centre.y,
				centre.z, (short) Math.min(Short.MAX_VALUE, sample.seen), (short) job.packetFlags(server), offsets, states));
	}

	private static void release(ServerChunkCache cache, BulkJob job, ChunkPos pos) {
		if (job.held.remove(pos.pack())) {
			cache.removeTicketWithRadius(TITAH_TICKET, pos, 0);
		}
	}

	/** Releases every ticket the job holds. */
	private static void releaseAll(MinecraftServer server, BulkJob job) {
		if (job.held.isEmpty()) {
			return;
		}
		ServerLevel level = server.getLevel(job.dimension);
		if (level != null) {
			for (long key : job.held) {
				level.getChunkSource().removeTicketWithRadius(TITAH_TICKET, ChunkPos.unpack(key), 0);
			}
		}
		job.held.clear();
	}

	// ---------------------------------------------------------------- bulk Pulihkan

	/**
	 * One tick of a bulk restore: chunk by chunk (only loaded ones, held while worked), each bottom-up; a block goes back
	 * only where the cell is AIR or still what the cast left, and no entity box of the chunk (read once) is in it (else
	 * it waits); no zones (it only puts back what the cast took). The rest goes back to the buffer at the end.
	 */
	private static void tickRestoreChunks(MinecraftServer server, ServerLevel level, BulkJob job, long deadline) {
		Restore data = job.restoreData;
		if (data == null) {
			job.phase = PHASE_DRAIN;
			return;
		}
		ServerChunkCache cache = level.getChunkSource();
		KultivatorConfig config = KultivatorConfig.get();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		while (true) {
			if (job.work == null) {
				if (job.chunkIndex >= job.chunks.length) {
					job.phase = PHASE_DRAIN;
					return;
				}
				if (job.inFlight.size() >= config.titahBulkLightChunks) {
					return;
				}
				int index = job.chunkIndex++;
				ChunkPos pos = job.chunks[index];
				LevelChunk chunk = cache.getChunkNow(pos.x(), pos.z());
				if (chunk == null) {
					// never load a chunk: these blocks wait in the buffer for the next Pulihkan
					for (int k = data.starts[index]; k < data.starts[index + 1]; k++) {
						data.deferred.add(k);
					}
					job.chunksSkipped++;
					continue;
				}
				cache.addTicketWithRadius(TITAH_TICKET, pos, 0);
				job.held.add(pos.pack());
				ChunkWork work = new ChunkWork(pos);
				work.boxes = new ArrayList<>();
				AABB box = new AABB(work.minX, level.getMinY(), work.minZ, work.minX + 16, level.getMaxY() + 1, work.minZ + 16);
				for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null, box,
						entity -> !entity.isSpectator() && !(entity instanceof dev.kultivator.pusakalangit.TitahMonumentEntity))) {
					work.boxes.add(entity.getBoundingBox());
				}
				work.column = data.starts[index];
				data.cursor = data.starts[index + 1];
				job.work = work;
			}
			ChunkWork work = job.work;
			LevelChunk chunk = cache.getChunkNow(work.pos.x(), work.pos.z());
			int end = data.cursor;
			if (chunk == null) {
				for (int k = work.column; k < end; k++) {
					data.deferred.add(k);
				}
				release(cache, job, work.pos);
				job.work = null;
				continue;
			}
			int reads = 0;
			while (work.column < end) {
				int k = work.column++;
				cursor.set(BlockPos.getX(data.positions[k]), BlockPos.getY(data.positions[k]), BlockPos.getZ(data.positions[k]));
				BlockState current = chunk.getBlockState(cursor);
				BlockState left = data.placed[k] == null ? air : data.placed[k];
				if (!current.isAir() && current != left) {
					// someone built here meanwhile: never overwritten
					continue;
				}
				BlockState target = data.old[k];
				if (target == null || target == current) {
					continue;
				}
				if (occupiedBy(work.boxes, cursor)) {
					data.deferred.add(k);
					continue;
				}
				BlockState old = chunk.setBlockState(cursor, target, FLAGS);
				if (old != null) {
					job.changed++;
					work.changedHere++;
					if (PoiTypes.hasPoi(old) || PoiTypes.hasPoi(target)) {
						level.updatePOIOnBlockStateChange(cursor.immutable(), old, target);
					}
					work.sx += cursor.getX() + 0.5D;
					work.sy += cursor.getY() + 0.5D;
					work.sz += cursor.getZ() + 0.5D;
				}
				if (++reads >= 256) {
					reads = 0;
					if (System.nanoTime() > deadline) {
						return;
					}
				}
			}
			endChunk(server, level, job, work);
			job.work = null;
			if (System.nanoTime() > deadline) {
				return;
			}
		}
	}

	private static boolean occupiedBy(@Nullable List<AABB> boxes, BlockPos pos) {
		if (boxes == null || boxes.isEmpty()) {
			return false;
		}
		AABB cell = new AABB(pos);
		for (AABB box : boxes) {
			if (box.intersects(cell)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- finish

	/**
	 * A bulk job ends (done, cancelled, the owner's switch off, server stop): every ticket released, the bar's players
	 * removed, the log (and the WARN line of a permanent carve), the compact undo of Tingkat 2-5, the closing ring, the
	 * monument's re-seat and the owner's message.
	 */
	private static void finishBulk(MinecraftServer server, BulkJob job, boolean cancelled) {
		releaseAll(server, job);
		job.bar.removeAllPlayers();
		ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
		ServerLevel level = server.getLevel(job.dimension);
		int minutes = KultivatorConfig.get().kehancuranUndoMinutes;
		if (job.restore) {
			int left = rebufferBulk(server, job);
			if (job.changed > 0) {
				KehancuranLog.record(server, job.owner, job.ownerName, "", Shapes.Kind.BOWL.id(), job.dimension, job.centre,
						(int) Math.min(Integer.MAX_VALUE, job.changed), true);
			}
			if (owner != null) {
				if (job.changed > 0 && level != null) {
					dev.kultivator.epic.EpicHooks.kehancuran(level, owner, null, Vec3.atCenterOf(job.centre), -1, 0.0F, dev.kultivator.epic.EpicIds.KEH_RESTORE,
							(int) Math.min(Integer.MAX_VALUE, job.changed));
				}
				Msg.chat(owner, Component.translatable("kultivator.kehancuran.restored", job.changed).withColor(0x9FF3E6));
				if (left > 0) {
					Msg.chat(owner, Component.translatable("kultivator.kehancuran.restore_left", left).withStyle(ChatFormatting.YELLOW));
				}
			}
			return;
		}
		long total = job.changed + job.surfaceChanged;
		int radius = job.shape.outer();
		if (total > 0) {
			KehancuranLog.recordSelf(server, job.owner, job.ownerName, job.specId, Shapes.Kind.BOWL.id(), job.dimension, job.centre,
					(int) Math.min(Integer.MAX_VALUE, total));
			if (job.permanent) {
				KehancuranLog.recordPermanent(server, job.owner, job.ownerName, job.specId, job.tier, job.dimension, job.centre, radius, total);
				PERMANENT.add(new Permanent(job.owner, server.overworld().getGameTime(), total));
			}
		}
		if (job.undo && job.undoCount > 0) {
			long[] positions = java.util.Arrays.copyOf(job.undoPos, job.undoCount);
			BlockState[] old = java.util.Arrays.copyOf(job.undoOld, job.undoCount);
			BlockState[] placed = java.util.Arrays.copyOf(job.undoPlaced, job.undoCount);
			job.undoPos = new long[0];
			job.undoOld = new BlockState[0];
			job.undoPlaced = new BlockState[0];
			addUndo(server, new Undo(nextUndo++, job.owner, job.ownerName, "", Shapes.Kind.BOWL.id(), job.dimension, job.centre, positions, old, placed,
					server.overworld().getGameTime() + minutes * 1200L, true, job.specId, true, job.tier));
		}
		if (total > 0 && level != null) {
			Vec3 centre = Vec3.atCenterOf(job.centre);
			broadcast(level, centre, new Payloads.DampakDebris(job.jobId, Payloads.DampakDebris.DONE, job.casterEntityId, job.specNet, centre.x,
					centre.y, centre.z, (short) Math.min(Short.MAX_VALUE, total), (short) job.packetFlags(server), new int[0], new int[0]));
		}
		if (job.recordId >= 0) {
			try {
				dev.kultivator.pusakalangit.TitahMonuments.onJobDone(server, job.recordId, job.jobId);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: Titah bulk job callback failed", exception);
			}
		}
		if (owner == null) {
			return;
		}
		if (!cancelled && total > 0 && level != null) {
			dev.kultivator.epic.EpicHooks.kehancuran(level, owner, null, Vec3.atCenterOf(job.centre), -1, 0.0F, dev.kultivator.epic.EpicIds.KEH_CRATER,
					(int) Math.min(Integer.MAX_VALUE, total));
		}
		if (cancelled) {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran.bulk.stopped", total, job.chunksDone, job.chunks.length).withStyle(ChatFormatting.GOLD));
			if (job.undo && job.undoCount > 0) {
				Msg.chat(owner, Component.translatable("kultivator.kehancuran.bulk.undo_hint", minutes).withColor(0x9FF3E6));
			}
			return;
		}
		if (total <= 0) {
			Msg.chat(owner, Component.translatable("kultivator.kehancuran.bulk.nothing", job.protectedColumns).withStyle(ChatFormatting.GRAY));
			return;
		}
		Component tail = job.permanent ? Component.translatable("kultivator.kehancuran.bulk.done_permanent")
				: job.undo && job.undoCount > 0 ? Component.translatable("kultivator.kehancuran.bulk.done_undo", minutes) : Component.empty();
		Msg.chat(owner, Component.translatable("kultivator.kehancuran.bulk.done", total, job.chunksDone, job.protectedColumns, tail).withColor(0xFF6A5A));
		if (job.capped) {
			Msg.chat(owner, Component.translatable(job.undo ? "kultivator.kehancuran.bulk.capped_undo" : "kultivator.kehancuran.bulk.capped")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/** A bulk restore ended: what waited (unloaded chunk, someone standing there) or was never reached goes back to the buffer. */
	private static int rebufferBulk(MinecraftServer server, BulkJob job) {
		Restore data = job.restoreData;
		if (data == null) {
			return 0;
		}
		List<Integer> rest = new ArrayList<>(data.deferred);
		if (job.work != null) {
			for (int k = job.work.column; k < data.cursor; k++) {
				rest.add(k);
			}
		}
		for (int index = job.chunkIndex; index < job.chunks.length; index++) {
			for (int k = data.starts[index]; k < data.starts[index + 1]; k++) {
				rest.add(k);
			}
		}
		if (rest.isEmpty() || server.overworld().getGameTime() >= job.restoreExpires) {
			return 0;
		}
		long[] positions = new long[rest.size()];
		BlockState[] old = new BlockState[rest.size()];
		BlockState[] placed = new BlockState[rest.size()];
		for (int n = 0; n < rest.size(); n++) {
			int k = rest.get(n);
			positions[n] = data.positions[k];
			old[n] = data.old[k];
			placed[n] = data.placed[k];
		}
		addUndo(server, new Undo(nextUndo++, job.owner, job.ownerName, "", Shapes.Kind.BOWL.id(), job.dimension, job.centre, positions, old, placed,
				job.restoreExpires, true, job.specId, true, job.tier));
		return rest.size();
	}

	private WorldBreaker() {
	}
}
