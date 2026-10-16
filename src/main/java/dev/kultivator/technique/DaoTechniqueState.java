package dev.kultivator.technique;

import dev.kultivator.data.Technique;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The lasting effects one caster's Dao techniques have running (server only, never saved): Iron Body, the empowered
 * Shadow Step strike, the Thousand Swords rain, delayed hits (falling swords, thunder chains and heavenly bolts), flying
 * phoenixes and imprisoned monsters. Dropped when the caster leaves or dies.
 */
final class DaoTechniqueState {

	/** What a delayed hit does when it is due. */
	enum Kind {
		/** A sword of light starts to fall on {@code target} (the hit follows when it lands). */
		SWORD,
		/** The falling sword lands on {@code target}. */
		SWORD_HIT,
		/** A thunder chain jumps from {@code from} (or {@code fromPos}) to {@code target}. */
		CHAIN,
		/** A heavenly bolt falls on a monster chosen when it is due (prefers ones not hit yet in {@code hitSet}). */
		SKY_BOLT
	}

	static final class Scheduled {
		final Kind kind;
		int delay;
		@Nullable LivingEntity target;
		@Nullable LivingEntity from;
		Vec3 fromPos;
		final double amount;
		final @Nullable Set<Integer> hitSet;

		Scheduled(Kind kind, int delay, @Nullable LivingEntity target, double amount, @Nullable Set<Integer> hitSet) {
			this.kind = kind;
			this.delay = delay;
			this.target = target;
			this.amount = amount;
			this.hitSet = hitSet;
			this.fromPos = Vec3.ZERO;
		}
	}

	/** The virtual phoenix of Phoenix Flame: moves one block per tick and burns what it passes. */
	static final class Phoenix {
		Vec3 pos;
		final Vec3 step;
		int ticksLeft;
		final double damage;
		final Set<Integer> hit = new HashSet<>();

		Phoenix(Vec3 pos, Vec3 step, int ticksLeft, double damage) {
			this.pos = pos;
			this.step = step;
			this.ticksLeft = ticksLeft;
			this.damage = damage;
		}
	}

	/** A monster held by Glacier Prison (frozen in place) or Space Prison (pulled to {@code anchor}). */
	static final class Lock {
		final LivingEntity entity;
		final Technique technique;
		final Vec3 anchor;
		int ticksLeft;
		int age;

		Lock(LivingEntity entity, Technique technique, Vec3 anchor, int ticksLeft) {
			this.entity = entity;
			this.technique = technique;
			this.anchor = anchor;
			this.ticksLeft = ticksLeft;
		}
	}

	/** Game time when Iron Body ends, 0 when it is not active. */
	long ironBodyUntil;
	/** Game time until the next melee hit deals double damage (Shadow Step), 0 when not empowered. */
	long shadowUntil;
	/** Ticks of Thousand Swords left, and the damage of each sword. */
	int swordsLeft;
	double swordsDamage;
	final List<Scheduled> scheduled = new ArrayList<>();
	final List<Phoenix> phoenixes = new ArrayList<>();
	final List<Lock> locks = new ArrayList<>();

	boolean idle() {
		return ironBodyUntil == 0L && shadowUntil == 0L && swordsLeft <= 0 && scheduled.isEmpty() && phoenixes.isEmpty()
				&& locks.isEmpty();
	}

	/** Forgets every reference to an entity that died or was unloaded. */
	void forget(Entity entity) {
		for (Scheduled each : scheduled) {
			if (each.from == entity) {
				each.from = null;
			}
		}
		scheduled.removeIf(each -> each.target == entity);
		locks.removeIf(lock -> lock.entity == entity);
	}

	boolean holds(Entity entity) {
		for (Lock lock : locks) {
			if (lock.entity == entity) {
				return true;
			}
		}
		return false;
	}
}
