package dev.kultivator.client.dampak.world;

import dev.kultivator.Kultivator;
import dev.kultivator.client.fx.spec.DisplayKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Phase 10 display pool (DESIGN 28.6.4): client-only {@code BlockDisplay}s made with
 * {@code EntityType.create(clientLevel, LOAD)} under ids counting down from −6,000,000 (PhantomDisplays −2 M,
 * EpicDisplays −3 M, RealmScenery −5 M), added with {@code ClientLevel.addEntity}, never sent anywhere. Every piece
 * (debris, lift cell, crystal, ice spike) is a {@link Piece} ticked here with the presentation σ; every transform goes
 * through {@link DisplayKey} (snap first, then key every 1-2 ticks by tier). Removed on their end, on a dimension change,
 * on disconnect and on {@code uji dampak berhenti}. A creation failure turns the pool off for the session (particles
 * stand in).
 */
public final class DampakDisplays {

	public static final int FIRST_ID = -6_000_000;

	/** Piece kinds (stats). */
	public static final int DEBRIS = 0;
	public static final int CELL = 1;
	public static final int CRYSTAL = 2;
	public static final int SPIKE = 3;

	private static final List<Piece> PIECES = new ArrayList<>();
	private static int nextId = FIRST_ID;
	private static int createdThisTick;
	private static int ticks;
	private static int farSkip;
	private static boolean broken;
	private static @Nullable ClientLevel owner;

	// ================================================================== pieces

	/** One live display of the pool; subclasses move and pose it each tick. */
	abstract static class Piece {
		final Display.BlockDisplay display;
		final DisplayKey key;
		final int kind;
		/** caster id × spec net of the cast it belongs to (0 = none). */
		final long cast;
		/** Presentation age (advances by σ). */
		float age;
		boolean dead;

		Piece(Display.BlockDisplay display, int kind, long cast) {
			this.display = display;
			this.key = new DisplayKey(display);
			this.kind = kind;
			this.cast = cast;
		}

		/** One tick: {@code keyNow} when the transform should be re-keyed now with {@code duration}. False = remove. */
		abstract boolean tick(ClientLevel level, float sigma, boolean keyNow, int duration);

		/** The cast was aborted (lift cells and suspended chunks end; flying debris keeps going). */
		void abort() {
		}

		/** The cast's climax arrived (suspended chunks drop). */
		void release() {
		}

		/** Poses a cube of size {@code sx, sy, sz} turned by {@code rotation} about its own centre. */
		void pose(Quaternionf rotation, float sx, float sy, float sz, boolean snap, int duration) {
			Vector3f half = new Vector3f(sx * 0.5F, sy * 0.5F, sz * 0.5F);
			rotation.transform(half);
			Vector3f translation = new Vector3f(-half.x, -half.y, -half.z);
			Vector3f scale = new Vector3f(Math.max(0.001F, sx), Math.max(0.001F, sy), Math.max(0.001F, sz));
			if (snap) {
				key.snap(translation, rotation, scale);
			} else {
				key.key(translation, rotation, scale, duration);
			}
		}

		/** Crumble particles of the shown state at the display. */
		void crumble(ClientLevel level, int n) {
			BlockState state = display.getBlockState();
			if (state.isAir()) {
				return;
			}
			BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, state);
			DampakParticles.burst(level, particle, display.position(), n, 0.08F);
		}
	}

	// ================================================================== creation

	/** True while one more piece may be created now near {@code at} (budget, distance, pool state). */
	static boolean canCreate(Vec3 at) {
		Minecraft minecraft = Minecraft.getInstance();
		if (broken || minecraft.level == null) {
			return false;
		}
		int index = DampakWorldBudget.index();
		if (createdThisTick >= DampakWorldBudget.DISPLAYS_PER_TICK[index] || PIECES.size() >= DampakWorldBudget.DISPLAYS_ALIVE[index]) {
			return false;
		}
		double d2 = minecraft.gameRenderer.mainCamera().position().distanceToSqr(at);
		if (d2 > DampakWorldBudget.SPAWN_RANGE * DampakWorldBudget.SPAWN_RANGE) {
			return false;
		}
		if (d2 > DampakWorldBudget.HALF_RANGE * DampakWorldBudget.HALF_RANGE) {
			return (farSkip++ & 1) == 0;
		}
		return true;
	}

	/** Pieces of {@code kind} alive. */
	static int count(int kind) {
		int n = 0;
		for (int i = 0; i < PIECES.size(); i++) {
			if (PIECES.get(i).kind == kind) {
				n++;
			}
		}
		return n;
	}

	/** A block display of {@code state} at {@code at} (not yet posed), or null (limits, failure). */
	static @Nullable Display.BlockDisplay create(BlockState state, Vec3 at, float size, boolean fullBright) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !canCreate(at)) {
			return null;
		}
		try {
			if (owner != level) {
				clear();
				owner = level;
			}
			Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.LOAD);
			if (display == null) {
				return null;
			}
			display.setId(nextId--);
			if (nextId < FIRST_ID - 1_000_000) {
				nextId = FIRST_ID;
			}
			display.setPos(at.x, at.y, at.z);
			display.setBlockState(state);
			if (fullBright) {
				display.setBrightnessOverride(Brightness.FULL_BRIGHT);
			}
			display.setViewRange(Math.max(1.0F, size));
			display.setShadowRadius(0.0F);
			display.setWidth(Math.max(1.0F, size * 1.8F));
			display.setHeight(Math.max(1.0F, size * 1.8F));
			level.addEntity(display);
			createdThisTick++;
			return display;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: Dampak displays turned off after an error; particles stand in", exception);
			return null;
		}
	}

	/** Adds a made piece to the pool (its first frame must already be snapped). */
	static void add(Piece piece) {
		PIECES.add(piece);
	}

	// ================================================================== tick and control

	/** Once per client tick (not while paused). */
	static void tick(ClientLevel level, float sigma) {
		ticks++;
		createdThisTick = 0;
		if (level != owner) {
			if (owner != null) {
				PIECES.clear();
			}
			owner = level;
			return;
		}
		if (PIECES.isEmpty()) {
			return;
		}
		int every = DampakWorldBudget.KEY_EVERY[DampakWorldBudget.index()];
		boolean keyNow = ticks % every == 0;
		for (Iterator<Piece> it = PIECES.iterator(); it.hasNext(); ) {
			Piece piece = it.next();
			boolean keep;
			if (piece.dead || piece.display.isRemoved()) {
				keep = false;
			} else {
				try {
					piece.age += sigma;
					keep = piece.tick(level, sigma, keyNow, every);
				} catch (RuntimeException exception) {
					keep = false;
				}
			}
			if (!keep) {
				remove(piece.display);
				it.remove();
			}
		}
	}

	/** The cast's climax: suspended pieces drop. */
	static void release(long cast) {
		for (int i = 0; i < PIECES.size(); i++) {
			Piece piece = PIECES.get(i);
			if (piece.cast == cast) {
				piece.release();
			}
		}
	}

	/** ABORT of a cast: its cells and suspended chunks end. */
	static void abort(long cast) {
		for (int i = 0; i < PIECES.size(); i++) {
			Piece piece = PIECES.get(i);
			if (piece.cast == cast) {
				piece.abort();
			}
		}
	}

	static int alive() {
		return PIECES.size();
	}

	/** Removes every piece (disconnect, world change, stop). */
	static void clear() {
		for (int i = 0; i < PIECES.size(); i++) {
			remove(PIECES.get(i).display);
		}
		PIECES.clear();
	}

	private static void remove(Display display) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level == owner && !display.isRemoved()) {
			level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	// ================================================================== shared helpers

	/** The client surface height at a column (the first free y above MOTION_BLOCKING_NO_LEAVES), or {@code fallback}. */
	static double ground(ClientLevel level, double x, double z, double fallback) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		if (y <= level.getMinY()) {
			return fallback;
		}
		return y;
	}

	/** The block state forming the surface of a column (air when none is loaded). */
	static BlockState surface(ClientLevel level, double x, double z, double fallbackY) {
		double y = ground(level, x, z, fallbackY);
		return level.getBlockState(BlockPos.containing(x, y - 0.5D, z));
	}

	/** True when a state should fly as a display (a solid model: not air, not a fluid, not a plant). */
	static boolean solid(ClientLevel level, BlockState state, BlockPos pos) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
			return false;
		}
		try {
			return !state.getCollisionShape(level, pos).isEmpty();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private DampakDisplays() {
	}
}
