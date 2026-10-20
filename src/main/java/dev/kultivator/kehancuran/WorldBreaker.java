package dev.kultivator.kehancuran;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

		/** Remembers a removed position for the surface pass (at most the per-cast cap). */
		void noteCarved(long pos) {
			int cap = Math.max(1, KultivatorConfig.get().kehancuranMaxBlocksPerCast);
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
		final BlockState[] placed;
		final long expiresAt;
		final boolean self;
		final @Nullable String specId;

		Undo(int id, Job job, long expiresAt) {
			this(id, job, job.undoPositions.stream().mapToLong(Long::longValue).toArray(), job.undoOld.toArray(new BlockState[0]),
					job.undoPlaced.toArray(new BlockState[0]), expiresAt);
		}

		Undo(int id, Job job, long[] positions, BlockState[] old, BlockState[] placed, long expiresAt) {
			this.id = id;
			this.owner = job.owner;
			this.ownerName = job.ownerName;
			this.disciple = job.disciple;
			this.shape = job.shape;
			this.dimension = job.dimension;
			this.centre = job.centre;
			this.positions = positions;
			this.old = old;
			this.placed = placed;
			this.expiresAt = expiresAt;
			this.self = job.self;
			this.specId = job.specId;
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
		ServerTickEvents.END_SERVER_TICK.register(WorldBreaker::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			UNDO.clear();
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

	/** Queues a cast. False when the queue is full (or, for a destructive job, its owner already has 2). */
	static boolean submit(Job job) {
		if (job.self && !job.restore && ownerJobs(job.owner) >= MAX_PER_OWNER && merge(job)) {
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
			if (queued == running || !queued.self || queued.restore || !queued.owner.equals(job.owner)
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
		return cancelled;
	}

	/** {running, queued, self jobs} across the server (for {@code debug teknik beban}). */
	static int[] counts() {
		int self = 0;
		for (Job job : JOBS) {
			if (job.self) {
				self++;
			}
		}
		return new int[]{JOBS.isEmpty() ? 0 : 1, queued(), self};
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
		return !JOBS.isEmpty();
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
		return cancelled;
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
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (!UNDO.isEmpty() && server.getTickCount() % 20 == 0) {
			long now = server.overworld().getGameTime();
			UNDO.removeIf(undo -> now >= undo.expiresAt);
		}
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
			JOBS.pollFirst();
			finish(server, job, false);
		}
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
		ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
		int minutes = KultivatorConfig.get().kehancuranUndoMinutes;
		if (job.undoable && !job.undoPositions.isEmpty()) {
			addUndo(server, new Undo(nextUndo++, job, server.overworld().getGameTime() + minutes * 1200L));
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
			Undo oldest = UNDO.remove(0);
			ServerPlayer owner = server.getPlayerList().getPlayer(oldest.owner);
			if (owner != null) {
				Msg.chat(owner, Component.translatable("kultivator.kehancuran_diri.undo_released", oldest.positions.length).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** Queues the restore of every cast of {@code owner} still in its window, newest first. Returns how many. */
	static int restore(UUID owner) {
		int queued = 0;
		for (int i = UNDO.size() - 1; i >= 0 && hasRoom(); i--) {
			Undo undo = UNDO.get(i);
			if (!undo.owner.equals(owner)) {
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

	private WorldBreaker() {
	}
}
