package dev.kultivator.technique;

import dev.kultivator.technique.spec.run.CastContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The lasting parts one caster's legacy Dao techniques have running (server only, never saved): the empowered Shadow
 * Step strike and its stage-2 window, and flying phoenixes. Prisons, swords, chains and bolts are scheduled on the cast
 * itself ({@code CastContext.schedule}). Dropped when the caster leaves or dies.
 */
final class DaoTechniqueState {

	/** The virtual phoenix of Phoenix Flame: moves one block per tick and burns what it passes (one query per 4 ticks). */
	static final class Phoenix {
		final CastContext ctx;
		Vec3 pos;
		final Vec3 step;
		int ticksLeft;
		final double coef;
		final double reach;
		int age;
		final Set<Integer> hit = new HashSet<>();

		Phoenix(CastContext ctx, Vec3 pos, Vec3 step, int ticksLeft, double coef, double reach) {
			this.ctx = ctx;
			this.pos = pos;
			this.step = step;
			this.ticksLeft = ticksLeft;
			this.coef = coef;
			this.reach = reach;
		}
	}

	/** Game time until the next melee hit is empowered (Shadow Step), 0 when not empowered. */
	long shadowUntil;
	/** The empowered hit's damage factor (×2, Kenaikan Abadi ×3) and the cast that granted it. */
	double shadowFactor = 2.0D;
	@Nullable CastContext shadowCast;
	/** Game time the Shadow Step stage-2 window closes (0 = closed) and the targets stepped behind so far. */
	long shadowWindowUntil;
	final Set<Integer> shadowVisited = new HashSet<>();
	final List<Phoenix> phoenixes = new ArrayList<>();

	boolean idle(long now) {
		return now >= shadowUntil && now >= shadowWindowUntil && phoenixes.isEmpty();
	}

	/** Forgets every reference to an entity that died or was unloaded. */
	void forget(LivingEntity entity) {
		shadowVisited.remove(entity.getId());
	}
}
