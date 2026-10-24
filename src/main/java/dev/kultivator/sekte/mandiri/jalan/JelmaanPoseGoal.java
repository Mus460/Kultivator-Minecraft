package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The little play at a stop (DESIGN 39.6.6): once a town body stands at its stall it plays the stop's beats one after
 * another ({@link Jelmaan#ketuk}: looking at the wares, haggling, the handshake with the keeper, tea, a meal, the guild
 * board, the healer's pulse ...), the keeper answering with its own clip and line, then idles in the stop's quiet pose
 * until the plan moves it on. Installed on every disciple body (priority 4, look only, so the walk keeps the feet);
 * only town bodies of a town stage use it.
 */
final class JelmaanPoseGoal extends Goal {

	private final DiscipleEntity body;
	private int tunggu;

	JelmaanPoseGoal(DiscipleEntity body) {
		this.body = body;
		this.setFlags(EnumSet.of(Goal.Flag.LOOK));
	}

	private Jelmaan.Tubuh scene() {
		if (body.level().isClientSide() || body.role() != DiscipleEntity.Role.JELMAAN) {
			return null;
		}
		Jelmaan.Tubuh t = Jelmaan.dari(body);
		return t != null && t.jenis == Jelmaan.Jenis.KOTA ? t : null;
	}

	private boolean diTempat(Jelmaan.Tubuh t) {
		BlockPos p = t.sasaran;
		return p == null || body.getNavigation().isDone() && body.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D) <= 2.2D * 2.2D;
	}

	@Override
	public boolean canUse() {
		Jelmaan.Tubuh t = scene();
		return t != null && diTempat(t);
	}

	@Override
	public boolean canContinueToUse() {
		Jelmaan.Tubuh t = scene();
		return t != null && diTempat(t);
	}

	@Override
	public void start() {
		Jelmaan.Tubuh t = scene();
		if (t != null && !t.tiba) {
			t.tiba = true;
			t.beat = 0;
			t.beatT0 = body.level().getGameTime() + 10L;
		}
		tunggu = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (++tunggu < 10) {
			return;
		}
		tunggu = 0;
		Jelmaan.Tubuh t = scene();
		if (t != null) {
			Jelmaan.mainkan(t);
		}
	}
}
