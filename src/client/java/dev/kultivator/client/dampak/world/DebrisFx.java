package dev.kultivator.client.dampak.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Puing (DESIGN 28.6): flying chunks of real or surface blocks as client-only block displays from the
 * {@link DampakDisplays} pool. Each chunk is launched up 0.35-0.9 b/t and outward 0.15-0.6 b/t (× "Daya dampak"),
 * spins 6-20°/t about a seeded axis under gravity 0.06 b/t² and drag 0.98, bounces once on the client heightmap
 * ({@code vy × −0.3}, the spin halves), rests, then leaves by one shrink over 8 ticks with {@code BLOCK_CRUMBLE}. Modes:
 * BURST, WAKE (spawn delayed by the distance along a line / 4 ticks), IMPLODE (pulled toward a core and up, shrinking),
 * SUSPEND (rises 1-3 b and hangs until its cast's climax, then drops) and CONVERT (ash and leaves, no displays).
 * Everything runs on presentation time (σ), so a hit-stop freezes the chunks mid-air.
 */
final class DebrisFx {

	static final int BURST = 0;
	static final int WAKE = 1;
	static final int IMPLODE = 2;
	static final int SUSPEND = 3;

	private static final double GRAVITY = 0.06D;
	private static final double DRAG = 0.98D;
	private static final int SHRINK = 8;
	private static final int SUSPEND_MAX = 200;
	private static final int MAX_PENDING = 256;

	/** A chunk waiting for its WAKE delay. */
	private record Pending(Vec3 at, BlockState state, Vec3 velocity, int mode, @Nullable Vec3 core, long cast, double due) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static int spawnCounter;

	// ================================================================== launching

	/**
	 * Launches chunks of the given states at the given block positions (the changed blocks of a real batch, or a TEST
	 * batch). Positions beyond the display budget become block particles.
	 */
	static void batch(ClientLevel level, BlockPos[] positions, BlockState[] states, Vec3 centre, int mode, @Nullable Vec3 core,
					  @Nullable Vec3 lineFrom, long cast, boolean own, boolean displays) {
		int index = DampakWorldBudget.index();
		int particles = DampakParticles.grant(DampakWorldBudget.scaled(DampakWorldBudget.BATCH_PARTICLES[index], own,
				DampakWorldBudget.BATCH_PARTICLES[index]));
		float othersFactor = DampakWorldBudget.othersFactor(own);
		double minAlong = Double.MAX_VALUE;
		if (mode == WAKE && lineFrom != null) {
			for (BlockPos pos : positions) {
				minAlong = Math.min(minAlong, Vec3.atCenterOf(pos).distanceTo(lineFrom));
			}
		}
		RandomSource random = DampakParticles.random();
		for (int i = 0; i < positions.length; i++) {
			BlockPos pos = positions[i];
			BlockState state = states[i];
			Vec3 at = Vec3.atCenterOf(pos);
			boolean solid = DampakDisplays.solid(level, state, pos);
			boolean asDisplay = displays && solid && (othersFactor >= 1.0F || random.nextFloat() < othersFactor);
			if (asDisplay) {
				double due = 0.0D;
				if (mode == WAKE && lineFrom != null) {
					due = Math.min(30.0D, (at.distanceTo(lineFrom) - minAlong) / 4.0D);
				}
				if (!launch(level, at, state, launchVelocity(at, centre, random), mode, core, cast, due)) {
					asDisplay = false;
				}
			}
			if (!asDisplay && particles > 0) {
				particles--;
				DampakParticles.one(DampakParticles.block(state), at.x, at.y, at.z, random.nextGaussian() * 0.1D, 0.2D + random.nextDouble() * 0.2D,
						random.nextGaussian() * 0.1D);
			}
		}
		// the dust of the batch
		for (int i = 0; i < particles && positions.length > 0; i++) {
			BlockPos pos = positions[random.nextInt(positions.length)];
			BlockState state = states[random.nextInt(states.length)];
			Vec3 at = Vec3.atCenterOf(pos);
			DampakParticles.one(i % 3 == 0 ? ParticleTypes.CLOUD : DampakParticles.block(state), at.x, at.y, at.z, random.nextGaussian() * 0.06D,
					0.08D + random.nextDouble() * 0.12D, random.nextGaussian() * 0.06D);
		}
	}

	/** CONVERT batches (wither, strip): ash and leaves, no displays. */
	static void convert(BlockPos[] positions, BlockState[] states, boolean own) {
		int index = DampakWorldBudget.index();
		int n = DampakParticles.grant(DampakWorldBudget.scaled(DampakWorldBudget.BATCH_PARTICLES[index], own, DampakWorldBudget.BATCH_PARTICLES[index]));
		RandomSource random = DampakParticles.random();
		for (int i = 0; i < n && positions.length > 0; i++) {
			int k = random.nextInt(positions.length);
			Vec3 at = Vec3.atCenterOf(positions[k]);
			DampakParticles.one(i % 3 == 0 ? ParticleTypes.PALE_OAK_LEAVES : i % 3 == 1 ? ParticleTypes.ASH : DampakParticles.block(states[k]),
					at.x + random.nextGaussian() * 0.3D, at.y + 0.5D, at.z + random.nextGaussian() * 0.3D, 0.0D, 0.03D, 0.0D);
		}
	}

	/**
	 * Visual debris (Mode Kehancuran off, 28.6.2): chunks of the client's own surface around {@code centre} (or along
	 * {@code lineFrom → centre} for WAKE), {@code n} of them. Nothing is read from or sent to the server.
	 */
	static void fake(ClientLevel level, Vec3 centre, float radius, int n, int mode, @Nullable Vec3 core, @Nullable Vec3 lineFrom, long cast,
					 boolean own, boolean displays) {
		RandomSource random = DampakParticles.random();
		BlockState centreState = DampakDisplays.surface(level, centre.x, centre.z, centre.y);
		boolean line = mode == WAKE && lineFrom != null && lineFrom.distanceToSqr(centre) > 4.0D;
		Vec3 dir = line ? centre.subtract(lineFrom) : Vec3.ZERO;
		double length = line ? dir.length() : 0.0D;
		Vec3 unit = line ? dir.scale(1.0D / length) : Vec3.ZERO;
		Vec3 side = line ? new Vec3(-unit.z, 0.0D, unit.x) : Vec3.ZERO;
		int particles = DampakParticles.grant(Math.max(8, n));
		for (int i = 0; i < n; i++) {
			double x;
			double z;
			double due = 0.0D;
			if (line) {
				double along = random.nextDouble() * length;
				double off = (random.nextDouble() - 0.5D) * 3.0D;
				x = lineFrom.x + unit.x * along + side.x * off;
				z = lineFrom.z + unit.z * along + side.z * off;
				due = Math.min(30.0D, along / 4.0D);
			} else {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double r = radius * Math.sqrt(random.nextDouble());
				x = centre.x + Math.cos(angle) * r;
				z = centre.z + Math.sin(angle) * r;
			}
			double ground = DampakDisplays.ground(level, x, z, centre.y);
			BlockPos pos = BlockPos.containing(x, ground - 0.5D, z);
			BlockState state = level.getBlockState(pos);
			if (!DampakDisplays.solid(level, state, pos)) {
				state = centreState;
			}
			Vec3 at = new Vec3(x, ground + 0.3D, z);
			Vec3 origin = line ? new Vec3(x - side.x, at.y, z - side.z) : centre;
			boolean made = displays && DampakDisplays.solid(level, state, pos)
					&& launch(level, at, state, launchVelocity(at, origin, random), mode, core, cast, due);
			if (!made && particles > 0) {
				particles--;
				DampakParticles.one(DampakParticles.block(state), at.x, at.y, at.z, random.nextGaussian() * 0.12D, 0.25D + random.nextDouble() * 0.2D,
						random.nextGaussian() * 0.12D);
			}
		}
	}

	private static Vec3 launchVelocity(Vec3 at, Vec3 origin, RandomSource random) {
		double speed = DampakWorldBudget.speedScale();
		double dx = at.x - origin.x;
		double dz = at.z - origin.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 0.05D) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			dx = Math.cos(angle);
			dz = Math.sin(angle);
			d = 1.0D;
		}
		double out = (0.15D + random.nextDouble() * 0.45D) * speed;
		double up = (0.35D + random.nextDouble() * 0.55D) * speed;
		return new Vec3(dx / d * out, up, dz / d * out);
	}

	/** Spawns one chunk now, or queues it for {@code due} ticks (WAKE). False when the pool refused. */
	private static boolean launch(ClientLevel level, Vec3 at, BlockState state, Vec3 velocity, int mode, @Nullable Vec3 core, long cast,
								  double due) {
		if (due >= 1.0D) {
			if (PENDING.size() >= MAX_PENDING) {
				return false;
			}
			PENDING.add(new Pending(at, state, velocity, mode, core, cast, DampakWorld.clock(0.0F) + due));
			return true;
		}
		return spawn(level, at, state, velocity, mode, core, cast);
	}

	private static boolean spawn(ClientLevel level, Vec3 at, BlockState state, Vec3 velocity, int mode, @Nullable Vec3 core, long cast) {
		RandomSource random = DampakParticles.random();
		boolean maximal = DampakWorldBudget.index() >= 2;
		float size = maximal && (spawnCounter++ & 7) == 0 ? 1.0F : 0.35F + random.nextFloat() * 0.55F;
		Display.BlockDisplay display = DampakDisplays.create(state, at, size, false);
		if (display == null) {
			return false;
		}
		int life = DampakWorldBudget.index() == 0 ? 30 : 40 + random.nextInt(31);
		Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F);
		if (axis.lengthSquared() < 1.0E-4F) {
			axis.set(0.0F, 1.0F, 0.0F);
		}
		axis.normalize();
		float spin = (6.0F + random.nextFloat() * 14.0F) * (random.nextBoolean() ? 1.0F : -1.0F);
		Chunk chunk = new Chunk(display, cast, at, velocity, axis, spin, size, life, mode, core);
		chunk.pose(chunk.rotation(0.0F), size, size, size, true, 0);
		DampakDisplays.add(chunk);
		return true;
	}

	// ================================================================== tick

	static void tick(ClientLevel level, double clock) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending pending = it.next();
			if (clock >= pending.due()) {
				it.remove();
				if (!spawn(level, pending.at(), pending.state(), pending.velocity(), pending.mode(), pending.core(), pending.cast())) {
					DampakParticles.one(DampakParticles.block(pending.state()), pending.at().x, pending.at().y, pending.at().z, 0.0D, 0.25D, 0.0D);
				}
			}
		}
	}

	static boolean running() {
		return !PENDING.isEmpty() || DampakDisplays.count(DampakDisplays.DEBRIS) > 0;
	}

	static int pending() {
		return PENDING.size();
	}

	static void clear() {
		PENDING.clear();
	}

	// ================================================================== the chunk

	private static final class Chunk extends DampakDisplays.Piece {
		private Vec3 pos;
		private Vec3 velocity;
		private final Vector3f axis;
		private float spin;
		private final float size;
		private final int life;
		private final int mode;
		private final @Nullable Vec3 core;
		private float angle;
		private boolean bounced;
		private boolean resting;
		private boolean released;
		private double hoverY;
		private float endAge = -1.0F;

		Chunk(Display.BlockDisplay display, long cast, Vec3 pos, Vec3 velocity, Vector3f axis, float spin, float size, int life, int mode,
			  @Nullable Vec3 core) {
			super(display, DampakDisplays.DEBRIS, cast);
			this.pos = pos;
			this.velocity = velocity;
			this.axis = axis;
			this.spin = spin;
			this.size = size;
			this.life = life;
			this.mode = mode;
			this.core = core;
			this.hoverY = pos.y + 1.0D + DampakParticles.random().nextDouble() * 2.0D;
		}

		Quaternionf rotation(float degrees) {
			return new Quaternionf().rotateAxis((float) Math.toRadians(degrees), axis.x, axis.y, axis.z);
		}

		@Override
		void release() {
			if (mode == SUSPEND && !released) {
				released = true;
				velocity = new Vec3(velocity.x * 0.3D, -0.1D, velocity.z * 0.3D);
			}
		}

		@Override
		void abort() {
			if (mode == SUSPEND && endAge < 0.0F) {
				endAge = age;
			}
		}

		@Override
		boolean tick(ClientLevel level, float sigma, boolean keyNow, int duration) {
			if (endAge >= 0.0F) {
				if (age - endAge >= SHRINK) {
					return false;
				}
				return true;
			}
			if (sigma > 0.0F) {
				move(level, sigma);
			}
			key.move(pos);
			boolean suspendedTooLong = mode == SUSPEND && !released && age >= SUSPEND_MAX;
			if (suspendedTooLong) {
				release();
			}
			int limit = mode == SUSPEND ? life + (released ? 0 : SUSPEND_MAX) : life;
			boolean imploded = mode == IMPLODE && core != null && pos.distanceToSqr(core) < 0.8D;
			if (age >= limit || imploded) {
				endAge = age;
				crumble(level, imploded ? 3 : 6);
				pose(rotation(angle), 0.01F, 0.01F, 0.01F, false, imploded ? 3 : SHRINK);
				return true;
			}
			if (keyNow) {
				float scale = size;
				if (mode == IMPLODE && core != null) {
					scale = (float) (size * Math.max(0.2D, Math.min(1.0D, pos.distanceTo(core) / 6.0D)));
				}
				pose(rotation(angle + spin * sigma * duration), scale, scale, scale, false, duration);
			}
			return true;
		}

		private void move(ClientLevel level, float sigma) {
			angle += spin * sigma;
			if (mode == SUSPEND && !released) {
				// rise to the hover height and hang, turning slowly
				double dy = hoverY - pos.y;
				pos = pos.add(velocity.x * 0.1D * sigma, dy * 0.15D * sigma, velocity.z * 0.1D * sigma);
				spin *= (float) Math.pow(0.97D, sigma);
				return;
			}
			if (mode == IMPLODE && core != null) {
				Vec3 toCore = core.subtract(pos);
				double d = toCore.length();
				if (d > 1.0E-3D) {
					Vec3 pull = toCore.scale(0.09D / d).add(-toCore.z / d * 0.05D, 0.02D, toCore.x / d * 0.05D);
					velocity = velocity.add(pull.scale(sigma)).scale(Math.pow(0.9D, sigma));
				}
				pos = pos.add(velocity.scale(sigma));
				return;
			}
			if (resting) {
				return;
			}
			velocity = new Vec3(velocity.x, velocity.y - GRAVITY * sigma, velocity.z).scale(Math.pow(DRAG, sigma));
			Vec3 next = pos.add(velocity.scale(sigma));
			if (velocity.y < 0.0D) {
				double ground = DampakDisplays.ground(level, next.x, next.z, Double.NaN);
				double floor = Double.isNaN(ground) ? Double.NEGATIVE_INFINITY : ground + size * 0.5D;
				if (next.y <= floor && pos.y >= floor - 1.0D) {
					next = new Vec3(next.x, floor, next.z);
					if (!bounced) {
						bounced = true;
						velocity = new Vec3(velocity.x * 0.6D, -velocity.y * 0.3D, velocity.z * 0.6D);
						spin *= 0.5F;
					} else {
						resting = true;
						velocity = Vec3.ZERO;
						spin = 0.0F;
					}
				}
			}
			pos = next;
		}
	}

	private DebrisFx() {
	}
}
