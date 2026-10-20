package dev.kultivator.client.dampak.world;

import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Particle helpers of the Phase 10 world effects: every count is granted by the shared {@code FxBudget} through
 * {@link EpicParticles#grant} (DETAIL), velocities follow the presentation σ ({@link EpicParticles#add}).
 */
final class DampakParticles {

	static RandomSource random() {
		return EpicParticles.random();
	}

	/** {@code n} particles bursting from {@code at} at about {@code speed} blocks per tick. */
	static void burst(ClientLevel level, ParticleOptions particle, Vec3 at, int n, float speed) {
		n = EpicParticles.grant(n, false);
		RandomSource random = random();
		for (int i = 0; i < n; i++) {
			double vx = random.nextGaussian() * speed;
			double vy = Math.abs(random.nextGaussian()) * speed + speed * 0.5D;
			double vz = random.nextGaussian() * speed;
			EpicParticles.add(particle, at.x + random.nextGaussian() * 0.3D, at.y + random.nextDouble() * 0.5D, at.z + random.nextGaussian() * 0.3D,
					vx, vy, vz);
		}
	}

	/** One particle (already granted by the caller's own count). */
	static void one(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		EpicParticles.add(particle, x, y, z, vx, vy, vz);
	}

	/** Block-break particles of {@code state}, or dust when the state has none. */
	static ParticleOptions block(BlockState state) {
		if (state == null || state.isAir()) {
			return ParticleTypes.POOF;
		}
		return new BlockParticleOption(ParticleTypes.BLOCK, state);
	}

	/** The rising-dust pillar particle of {@code state} (Normal and up). */
	static ParticleOptions pillar(BlockState state) {
		if (state == null || state.isAir()) {
			return ParticleTypes.CLOUD;
		}
		return new BlockParticleOption(ParticleTypes.DUST_PILLAR, state);
	}

	/** Takes up to {@code wanted} particles from the shared budget. */
	static int grant(int wanted) {
		return EpicParticles.grant(wanted, false);
	}

	private DampakParticles() {
	}
}
