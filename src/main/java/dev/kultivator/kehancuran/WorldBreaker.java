package dev.kultivator.kehancuran;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The ONLY code of the whole mod that changes blocks (DESIGN 0 and 20.3.21), and only for Mode Kehancuran's ordered casts
 * with all three locks open. One running job per server and at most 2 queued. Each server tick changes at most
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

	/** One running job plus two queued. */
	public static final int MAX_JOBS = 3;
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
		}
	}

	private static final Deque<Job> JOBS = new ArrayDeque<>();
	private static final List<Undo> UNDO = new ArrayList<>();
	private static int nextUndo = 1;

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

	/** Queues a cast. False when the queue is full. */
	static boolean submit(Job job) {
		if (!hasRoom()) {
			return false;
		}
		JOBS.addLast(job);
		return true;
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
		int budget = config.kehancuranBlocksPerTick;
		int reads = budget * READ_FACTOR;
		ProtectedZones.Context zones = job.restore ? null : new ProtectedZones.Context(level, batchBox(job, reads));
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
			} else if (zones.zoneOf(pos) != null) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
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
				if (ProtectedZones.neverTouch(level, pos, state) || job.naturalOnly && !Shapes.natural(state) || touchesUnsafe(level, pos)) {
					continue;
				}
				to = job.wither ? Shapes.witherInto(state) : Blocks.AIR.defaultBlockState();
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
			level.setBlock(pos, to, FLAGS);
			if (job.undoable) {
				job.undoPositions.add(pos.asLong());
				job.undoOld.add(state);
				job.undoPlaced.add(to);
			}
			job.changed++;
			budget--;
			done++;
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
		}
		if (job.index >= job.positions.length) {
			JOBS.pollFirst();
			finish(server, job, false);
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
		if (job.changed > 0) {
			KehancuranLog.record(server, job.owner, job.ownerName, job.disciple, job.shape, job.dimension, job.centre, job.changed, job.restore);
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
		int minutes = KultivatorConfig.get().kehancuranUndoMinutes;
		if (job.undoable && !job.undoPositions.isEmpty()) {
			UNDO.add(new Undo(nextUndo++, job, server.overworld().getGameTime() + minutes * 1200L));
		}
		int left = job.restore ? rebuffer(server, job) : 0;
		if (owner == null) {
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
		UNDO.add(new Undo(nextUndo++, job, positions, old, placed, job.expiresAt));
		return rest.size();
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
