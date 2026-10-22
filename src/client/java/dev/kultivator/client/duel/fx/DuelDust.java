package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Dust kicked up by the duel (DESIGN 35.4.5): particles of the ground's own block ({@code ParticleTypes.BLOCK} with a
 * {@code BlockParticleOption} of the block read below the feet, never changed) spreading flat around the feet, plus
 * {@code DUST_PLUME} puffs for the end of a blade lock, a heavy step and the landing of a Sky Cleave; plain
 * {@code CLOUD} where the ground has no particles (air, barriers). Counts by Effect Quality and the particle budget.
 */
public final class DuelDust {

	/** A ring of dust at {@code feet}: {@code count} block particles at Normal and, with {@code plume}, 4 dust plumes. */
	public static void puff(Vec3 feet, int count, boolean plume) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || minecraft.player.position().distanceToSqr(feet) > 48.0D * 48.0D) {
			return;
		}
		BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2D, feet.z);
		BlockState state = level.getBlockState(below);
		boolean blockDust = !state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE;
		RandomSource random = RandomSource.create(below.asLong() ^ DuelFx.ticks());
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double speed = 0.12D + random.nextDouble() * 0.12D;
			double vx = Math.cos(angle) * speed;
			double vz = Math.sin(angle) * speed;
			double x = feet.x + Math.cos(angle) * 0.3D;
			double z = feet.z + Math.sin(angle) * 0.3D;
			if (blockDust) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, feet.y + 0.1D, z, vx, 0.08D + random.nextDouble() * 0.08D, vz);
			} else {
				level.addParticle(ParticleTypes.CLOUD, x, feet.y + 0.1D, z, vx * 0.4D, 0.02D, vz * 0.4D);
			}
		}
		if (plume) {
			int plumes = EffectQuality.minimal() ? 1 : 4;
			for (int i = 0; i < plumes && FxBudget.take(2, FxBudget.Priority.DETAIL) > 0; i++) {
				double angle = Math.PI * 2.0D * i / plumes + random.nextDouble();
				level.addParticle(ParticleTypes.DUST_PLUME, feet.x + Math.cos(angle) * 0.5D, feet.y + 0.15D, feet.z + Math.sin(angle) * 0.5D,
						Math.cos(angle) * 0.05D, 0.02D, Math.sin(angle) * 0.05D);
			}
		}
	}

	private DuelDust() {
	}
}
