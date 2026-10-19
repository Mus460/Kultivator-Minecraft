package dev.kultivator.purba;

import dev.kultivator.musuh.RogueTargets;
import dev.kultivator.util.NoGrief;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The hit rules every phase-9 foe of this module shares (DESIGN 26.2.2): only targets {@link RogueTargets#mayHit} accepts
 * (survival players, the master's disciples, hunting pets, golems; never villagers, animals, creative or spectator players),
 * never another foe of this module; skills deal a share of the <b>target's</b> max health as a mob attack (armour applies).
 * Entities only, never a block.
 */
final class PurbaHits {

	/** Tag of Taotie's Pelahap (vanilla spiders the fight owns; refused on load). */
	static final String PELAHAP_TAG = "kultivator.purba_pelahap";

	/** True when {@code attacker} may hurt or chase {@code target}. */
	static boolean mayHit(@Nullable Mob attacker, @Nullable LivingEntity target) {
		if (target instanceof HeavenEnvoyEntity || target instanceof PrimordialBeastEntity || target instanceof PrimordialRelicEntity
				|| target != null && target.entityTags().contains(PELAHAP_TAG)) {
			return false;
		}
		return RogueTargets.mayHit(attacker, target);
	}

	/** Deals {@code share} × the victim's max health as a mob attack of {@code attacker}; true when it landed. */
	static boolean share(ServerLevel level, Mob attacker, LivingEntity victim, double share) {
		float amount = (float) (victim.getMaxHealth() * share);
		if (!(amount > 0.0F) || !Float.isFinite(amount)) {
			return false;
		}
		if (!(victim instanceof Player)) {
			victim.addTag(NoGrief.TAG);
		}
		return victim.hurtServer(level, level.damageSources().mobAttack(attacker), amount);
	}

	/** Every living entity {@code attacker} may hit within {@code radius} (horizontal) of {@code centre}, ±{@code height}. */
	static List<LivingEntity> around(ServerLevel level, Mob attacker, Vec3 centre, double radius, double height) {
		return around(level, attacker, centre, radius, height, e -> true);
	}

	static List<LivingEntity> around(ServerLevel level, Mob attacker, Vec3 centre, double radius, double height, Predicate<LivingEntity> extra) {
		AABB box = new AABB(centre, centre).inflate(radius, height, radius);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && horizontalSq(e.position(), centre) <= radius * radius && mayHit(attacker, e) && extra.test(e));
	}

	/** Every target within {@code width} of the segment {@code from}–{@code to}. */
	static List<LivingEntity> alongLine(ServerLevel level, Mob attacker, Vec3 from, Vec3 to, double width) {
		AABB box = new AABB(from, to).inflate(width + 1.0D);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && mayHit(attacker, e) && segmentDistanceSq(e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D), from, to) <= width * width);
	}

	static double horizontalSq(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** Squared distance from {@code p} to the segment {@code a}–{@code b}. */
	static double segmentDistanceSq(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSq = ab.lengthSqr();
		if (lengthSq < 1.0E-6D) {
			return p.distanceToSqr(a);
		}
		double t = Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / lengthSq));
		return p.distanceToSqr(a.add(ab.scale(t)));
	}

	/** A flat unit vector from {@code from} toward {@code to} (+z when they meet). */
	static Vec3 flatDirection(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
		return d.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : d.normalize();
	}

	/** Pushes {@code entity} by {@code velocity} (synced to the client; never a block). */
	static void push(LivingEntity entity, Vec3 velocity) {
		entity.push(velocity.x, velocity.y, velocity.z);
		entity.needsSync = true;
		entity.resetFallDistance();
	}

	private PurbaHits() {
	}
}
