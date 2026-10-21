package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A puzzle that lives without a structure (DESIGN 34.5.5 {@code uji gua teka <jenis>} and the puzzle floors of the
 * tower, 34.5.7): a code-built layout in a frame at a chosen point, entities only (the test needs a free area of about
 * ten blocks; nothing is ever built). It plays the same rules as a ruin room: the attempt starts when a challenger
 * stands within {@link #radius} of the centre, the hint after {@code hintSeconds}, three minutes, the same backlash.
 * {@link #onEnd} hears {@code true} when solved.
 */
public final class FreeHost implements PuzzleHost {

	private static final List<FreeHost> LIVE = new ArrayList<>();

	private final ServerLevel level;
	private final Frame frame;
	private final CompoundTag layout;
	private final int tier;
	private final long seed;
	private final double radius;
	private final boolean tower;
	private final Consumer<Boolean> onEnd;
	private final List<Entity> owned = new ArrayList<>();
	private final Puzzle puzzle;
	private long attemptStart = -1L;
	private boolean hinted;
	private boolean ended;
	private long lastSeen;

	private FreeHost(ServerLevel level, Frame frame, Puzzle.Kind kind, int tier, long seed, double radius, boolean tower, Consumer<Boolean> onEnd) {
		this.level = level;
		this.frame = frame;
		this.layout = layout(kind, tier, seed);
		this.tier = tier;
		this.seed = seed;
		this.radius = radius;
		this.tower = tower;
		this.onEnd = onEnd;
		this.lastSeen = level.getGameTime();
		this.puzzle = Puzzle.create(kind, this);
	}

	/** Starts a free puzzle of {@code kind} centred on {@code centre} (block). */
	public static FreeHost start(ServerLevel level, Frame frame, Puzzle.Kind kind, int tier, long seed, boolean tower, Consumer<Boolean> onEnd) {
		FreeHost host = new FreeHost(level, frame, kind, tier, seed, tower ? 16.0D : 9.0D, tower, onEnd);
		host.puzzle.build();
		LIVE.add(host);
		return host;
	}

	/** The default layouts (the same offsets the templates write). */
	private static CompoundTag layout(Puzzle.Kind kind, int tier, long seed) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("tier", tier);
		switch (kind) {
			case ILUSI -> {
				// a ring of phantom walls round the exit: one must be revealed and dispelled to reach it
				List<Integer> cells = new ArrayList<>();
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
							cells.add(dx);
							cells.add(dz - 6);
						}
					}
				}
				tag.put("walls", new IntArrayTag(cells.stream().mapToInt(Integer::intValue).toArray()));
				tag.put("exit", new IntArrayTag(new int[]{0, -6}));
				tag.put("dead", new IntArrayTag(new int[]{-5, 2, 5, 2}));
			}
			case CERMIN -> tag.put("mirror", new IntArrayTag(new int[]{0, 0, -7}));
			case TIMBANGAN -> {
				tag.put("pans", new IntArrayTag(new int[]{-4, 0, 4, 0}));
				tag.putInt("weight", 4 + tier + (int) Math.floorMod(seed, 3L));
			}
			case BINTANG -> tag.put("rings", new IntArrayTag(new int[]{2, 4, 6}));
			default -> {
			}
		}
		return tag;
	}

	/** Once per server tick (from {@code Reruntuhan}). */
	public static void tickAll(MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (FreeHost host : List.copyOf(LIVE)) {
			try {
				host.tick();
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: a free puzzle failed; it ends", exception);
				host.end(false);
			}
		}
	}

	private void tick() {
		long now = level.getGameTime();
		List<ServerPlayer> inside = challengers();
		if (!inside.isEmpty() || !DuniaAnchors.playersNear(level, frame.centre(), radius + 24.0D).isEmpty()) {
			lastSeen = now;
		}
		if (!tower && now - lastSeen > 600L) {
			end(false);
			return;
		}
		owned.removeIf(Entity::isRemoved);
		if (puzzle.done()) {
			return;
		}
		if (attemptStart < 0L) {
			if (!inside.isEmpty()) {
				attemptStart = now;
				hinted = false;
				puzzle.started();
			}
			return;
		}
		long age = now - attemptStart;
		if (!hinted && age >= Reruntuhan.config().hintSeconds * 20L) {
			hinted = true;
			puzzle.hint();
			for (ServerPlayer player : inside) {
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.hint_prefix").withStyle(ChatFormatting.AQUA)
						.append(puzzle.hintText().copy().withStyle(ChatFormatting.WHITE)));
			}
		}
		if (age >= Puzzle.TIME_LIMIT) {
			if (tower) {
				end(false);
				return;
			}
			for (ServerPlayer player : inside) {
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.time_up").withStyle(ChatFormatting.RED));
			}
			puzzle.wrongAll();
			attemptStart = -1L;
			return;
		}
		puzzle.tick(now);
	}

	/** Ticks left of the attempt (the tower's bar). */
	public long timeLeft() {
		return attemptStart < 0L ? Puzzle.TIME_LIMIT : Math.max(0L, Puzzle.TIME_LIMIT - (level.getGameTime() - attemptStart));
	}

	public Puzzle puzzle() {
		return puzzle;
	}

	/** Ends the puzzle: every part goes; {@link #onEnd} hears whether it was solved. */
	public void end(boolean solved) {
		if (ended) {
			return;
		}
		ended = true;
		LIVE.remove(this);
		puzzle.dispose();
		for (Entity entity : owned) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}
		owned.clear();
		try {
			onEnd.accept(solved);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.warn("Kultivator: a free puzzle's end hook failed", exception);
		}
	}

	public boolean ended() {
		return ended;
	}

	/** Server stop. */
	public static void clearAll() {
		for (FreeHost host : List.copyOf(LIVE)) {
			host.end(false);
		}
		LIVE.clear();
	}

	/** The nearest live free puzzle within {@code range} (tests). */
	public static @Nullable FreeHost nearest(ServerPlayer player, double range) {
		FreeHost best = null;
		double bestSq = range * range;
		for (FreeHost host : LIVE) {
			if (host.level != player.level()) {
				continue;
			}
			double d = host.frame.centre().distanceToSqr(player.position());
			if (d < bestSq) {
				bestSq = d;
				best = host;
			}
		}
		return best;
	}

	// ================================================================== PuzzleHost

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public Frame frame() {
		return frame;
	}

	@Override
	public CompoundTag layout() {
		return layout;
	}

	@Override
	public int tier() {
		return tier;
	}

	@Override
	public long seed() {
		return seed;
	}

	@Override
	public List<ServerPlayer> challengers() {
		return DuniaAnchors.playersNear(level, frame.centre(), radius);
	}

	@Override
	public <T extends Entity> @Nullable T spawn(EntityType<T> type, Vec3 at, float yaw) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.addTag(NoGrief.TAG);
		if (entity instanceof TekaPartEntity) {
			entity.addTag(TekaPartEntity.TAG);
		}
		if (entity instanceof ColliderEntity) {
			entity.addTag(ColliderEntity.TAG);
		}
		if (!level.addFreshEntity(entity)) {
			return null;
		}
		owned.add(entity);
		return entity;
	}

	@Override
	public void solved(Puzzle solvedPuzzle) {
		RFx.at(level, frame.centre(), RFx.SOLVED, tier, solvedPuzzle.kind().ordinal(), 1.0F);
		for (ServerPlayer player : DuniaAnchors.playersNear(level, frame.centre(), 24.0D)) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.teka.solved",
					Component.translatable("kultivator.reruntuhan.teka." + solvedPuzzle.kind().id())).withStyle(ChatFormatting.GREEN));
		}
		end(true);
	}

	@Override
	public void backlash(ServerPlayer player, Puzzle puzzle) {
		Reruntuhan.backlash(player, Reruntuhan.config().puzzleBacklash);
	}

	@Override
	public boolean hinted() {
		return hinted;
	}

	@Override
	public boolean inTower() {
		return tower;
	}
}
