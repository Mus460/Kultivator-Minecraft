package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The walk of 39C (DESIGN 39.6.6): a town body ({@code Role.JELMAAN}) strolls to the stall of its stop, walks on along
 * the road segment or stays bound; a real disciple in a 39C scene (the departure to the Lumbung and out of the gate, the
 * arrival with the box on its back, a gift carried to a friend) walks to the scene's next spot. Installed on every
 * disciple body through {@code DiscipleEntity.addGoalInstaller} (priority 3, after floating, holding still and retreat);
 * everything is tested in {@link #canUse}, never at install time. The path is recalculated once a second; a town body
 * that finds no path hops the last stretch (it is only a picture of a journey), a real disciple stops trying and the
 * scene's own timeout moves it on.
 */
final class JelmaanJalanGoal extends Goal {

	/** Stroll speed in town, walking speed on the road and in scenes. */
	private static final double SANTAI = 0.75D;
	private static final double JALAN = 1.0D;

	private final DiscipleEntity body;
	private int recalc;
	private int gagal;

	JelmaanJalanGoal(DiscipleEntity body) {
		this.body = body;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private @Nullable BlockPos target() {
		if (body.level().isClientSide()) {
			return null;
		}
		DiscipleEntity.Role role = body.role();
		if (role == DiscipleEntity.Role.JELMAAN) {
			return Jelmaan.sasaran(body);
		}
		if (role == DiscipleEntity.Role.DISCIPLE && body.getTarget() == null && !body.data().breakingThrough()) {
			return PelaksanaJalan.sasaran(body);
		}
		return null;
	}

	private double jarak2(BlockPos p) {
		return body.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
	}

	@Override
	public boolean canUse() {
		BlockPos t = target();
		return t != null && jarak2(t) > 1.6D * 1.6D;
	}

	@Override
	public boolean canContinueToUse() {
		BlockPos t = target();
		return t != null && body.getTarget() == null && jarak2(t) > 0.8D * 0.8D && gagal < 6;
	}

	@Override
	public void start() {
		recalc = 0;
		gagal = 0;
	}

	@Override
	public void stop() {
		body.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		BlockPos t = target();
		if (t == null || --recalc > 0) {
			return;
		}
		recalc = this.adjustedTickDelay(20);
		boolean jelmaan = body.role() == DiscipleEntity.Role.JELMAAN;
		Jelmaan.Tubuh scene = jelmaan ? Jelmaan.dari(body) : null;
		double speed = scene == null ? JALAN : switch (scene.jenis) {
			case KOTA -> SANTAI;
			case PENTAS -> scene.laju;
			default -> JALAN;
		};
		boolean moving = body.getNavigation().moveTo(t.getX() + 0.5D, t.getY(), t.getZ() + 0.5D, speed);
		if (moving) {
			gagal = 0;
			return;
		}
		gagal++;
		if (jelmaan && (gagal >= 3 || jarak2(t) > 40.0D * 40.0D)) {
			// a picture of a journey: no path (a wall, a river) means it simply appears further on
			body.teleportTo(t.getX() + 0.5D, t.getY(), t.getZ() + 0.5D);
			gagal = 0;
		}
	}
}
