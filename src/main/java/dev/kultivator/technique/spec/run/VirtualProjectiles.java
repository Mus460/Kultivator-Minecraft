package dev.kultivator.technique.spec.run;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.util.LoadedPath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * VOLLEY (DESIGN 22.3.1): simulated projectiles, never entities. SIM: one merged entity query per volley per tick and one
 * read-only clip per projectile per tick; SEEK: targets picked at launch, the hit lands at the computed arrival tick after
 * one small validation (the client animates the homing). At most 48 per cast and 256 on the server; beyond that a volley
 * falls back to SEEK.
 */
public final class VirtualProjectiles {

	public static final double SPEED = 2.0D;
	/** Volleys up to this size send one TRAVEL per projectile; bigger ones send one event for all. */
	static final int VOLLEY_EVENTS = 16;
	private static final double SKY_HEIGHT = 16.0D;

	private static final class Projectile {
		Vec3 pos;
		final Vec3 velocity;
		double travelled;
		boolean alive = true;
		int pierceLeft;
		final long launch;
		boolean launched;
		final @Nullable LivingEntity target;
		final long arrival;
		/** This tick's clipped segment end and whether a block stopped it (one clip per projectile per tick). */
		@Nullable Vec3 stepEnd;
		boolean stepBlocked;

		Projectile(Vec3 pos, Vec3 velocity, int pierce, long launch, @Nullable LivingEntity target, long arrival) {
			this.pos = pos;
			this.velocity = velocity;
			this.pierceLeft = pierce;
			this.launch = launch;
			this.target = target;
			this.arrival = arrival;
		}
	}

	/** One volley of a cast step. */
	static final class Volley {
		final Caster source;
		final Caster.Run run;
		final Shape.Volley shape;
		final boolean seek;
		final double range;
		final double hitRadius;
		final int perTarget;
		final int maxTargets;
		final List<Projectile> projectiles = new ArrayList<>();
		final Map<LivingEntity, Integer> hits = new HashMap<>();
		final Set<LivingEntity> targeted = new HashSet<>();
		boolean done;

		Volley(Caster source, Caster.Run run, Shape.Volley shape, boolean seek, double range, double hitRadius, int perTarget, int maxTargets) {
			this.source = source;
			this.run = run;
			this.shape = shape;
			this.seek = seek;
			this.range = range;
			this.hitRadius = hitRadius;
			this.perTarget = perTarget;
			this.maxTargets = maxTargets;
		}
	}

	private static final List<Volley> VOLLEYS = new ArrayList<>();
	private static int live;

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VOLLEYS.clear();
			live = 0;
		});
	}

	/**
	 * Launches a volley of {@code count} from {@code from} toward {@code aim}: FAN across {@code angle}°, RING all around,
	 * SKY falling on the aim area, ORBIT circling the caster first. True when anything was launched.
	 */
	static boolean launch(Caster source, Caster.Run run, Shape.Volley shape, int count, double range, double hitRadius, Vec3 from, Vec3 aim) {
		int n = Math.max(0, Math.min(count, TechniquePower.PROJECTILES_PER_CAST));
		if (n == 0) {
			return false;
		}
		boolean seek = shape.mode() == Shape.Volley.Mode.SEEK || live + n > TechniquePower.PROJECTILES_SERVER;
		int maxTargets = source.count(shape.maxTargets(), source.maxTargets());
		int perTarget = shape.perTargetGc() > 0 && source.casterRealm().atLeast(dev.kultivator.data.Realm.GOLDEN_CORE) ? shape.perTargetGc() : shape.perTarget();
		Volley volley = new Volley(source, run, shape, seek, range, hitRadius, perTarget, maxTargets <= 0 ? source.maxTargets() : maxTargets);
		long now = source.caster().level().getGameTime();
		int spread = shape.over() > 0 ? shape.over() : Math.max(0, shape.stagger()) * (n - 1);
		Vec3 look = aim.subtract(from);
		look = look.lengthSqr() < 1.0E-6D ? source.look() : look.normalize();
		List<LivingEntity> seekTargets = seek ? source.hostiles(from, range, false) : List.of();
		if (seek && seekTargets.isEmpty()) {
			return false;
		}
		for (int i = 0; i < n; i++) {
			long delay = n <= 1 ? 0L : Math.round(spread * (i / (double) (n - 1)));
			Vec3 start = from;
			Vec3 dir;
			switch (shape.spread()) {
				case RING -> {
					double angle = Math.PI * 2.0D * i / n;
					dir = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
				}
				case SKY -> {
					double angle = Math.PI * 2.0D * i / Math.max(1, n) + i * 0.7D;
					double r = hitRadius + (i % 4) * 1.5D;
					start = aim.add(Math.cos(angle) * r, SKY_HEIGHT, Math.sin(angle) * r);
					dir = new Vec3(0.0D, -1.0D, 0.0D);
				}
				case ORBIT -> {
					double angle = Math.PI * 2.0D * i / n;
					start = from.add(Math.cos(angle) * 1.5D, 0.0D, Math.sin(angle) * 1.5D);
					dir = look;
				}
				default -> {
					double offset = n <= 1 ? 0.0D : (i / (double) (n - 1) - 0.5D) * shape.angle();
					dir = Caster.rotateYaw(look, offset);
				}
			}
			LivingEntity target = null;
			long arrival = now + delay;
			if (seek) {
				target = seekTargets.get(i % seekTargets.size());
				double distance = start.distanceTo(target.getBoundingBox().getCenter());
				arrival = now + delay + Math.max(1L, Math.round(distance / SPEED));
			}
			volley.projectiles.add(new Projectile(start, dir.normalize().scale(SPEED), shape.pierce(), now + delay, target, arrival));
		}
		live += n;
		VOLLEYS.add(volley);
		source.activity(1);
		int flight = (int) Math.ceil(range / SPEED);
		int mode = shape.spread().ordinal();
		if (n <= VOLLEY_EVENTS) {
			// one TRAVEL per projectile when it really leaves (FLAG_VOLLEY, DESIGN 27.4): the client flies each one its own way
			for (int i = 0; i < n; i++) {
				Projectile projectile = volley.projectiles.get(i);
				int index = i;
				Vec3 start = projectile.pos;
				Vec3 end = projectile.target != null ? projectile.target.getBoundingBox().getCenter() : start.add(projectile.velocity.normalize().scale(range));
				int ticks = projectile.target != null ? (int) Math.max(1L, projectile.arrival - projectile.launch) : flight;
				int target = projectile.target != null ? projectile.target.getId() : -1;
				source.schedule((int) Math.max(0L, projectile.launch - now), () -> source.fx(run.travelPhase(), start, end, (float) hitRadius,
						(float) start.distanceTo(end), n, ticks, target, index, n, mode, dev.kultivator.network.Payloads.TechFx.FLAG_VOLLEY));
			}
		} else {
			// a big volley is one event: the client re-simulates the spread from the seed (index -1, of = n)
			source.fx(run.travelPhase(), from, seek ? seekTargets.get(0).getBoundingBox().getCenter() : from.add(look.scale(range)),
					(float) hitRadius, (float) range, n, Math.max(1, spread + flight), -1, -1, n, mode, dev.kultivator.network.Payloads.TechFx.FLAG_VOLLEY);
		}
		return true;
	}

	static void tick() {
		if (VOLLEYS.isEmpty()) {
			return;
		}
		for (Volley volley : List.copyOf(VOLLEYS)) {
			if (!volley.source.casterValid() || volley.source.aborted()) {
				finish(volley);
				continue;
			}
			try {
				if (volley.seek) {
					tickSeek(volley);
				} else {
					tickSim(volley);
				}
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: volley of {} failed and was ended", volley.source.spec().id(), exception);
				finish(volley);
				continue;
			}
			boolean any = false;
			for (Projectile projectile : volley.projectiles) {
				if (projectile.alive) {
					any = true;
					break;
				}
			}
			if (!any) {
				finish(volley);
			}
		}
	}

	private static void tickSeek(Volley volley) {
		long now = volley.source.caster().level().getGameTime();
		for (Projectile projectile : volley.projectiles) {
			if (!projectile.alive || now < projectile.arrival) {
				continue;
			}
			projectile.alive = false;
			LivingEntity target = projectile.target;
			if (target == null || !target.isAlive() || target.isRemoved() || target.position().distanceTo(volley.source.caster().position()) > volley.range + 8.0D
					|| !volley.source.mayHit(target, false) || !allowed(volley, target)) {
				continue;
			}
			volley.source.projectileHit(volley.run, target, target.getBoundingBox().getCenter());
		}
	}

	private static void tickSim(Volley volley) {
		long now = volley.source.caster().level().getGameTime();
		AABB box = null;
		for (Projectile projectile : volley.projectiles) {
			if (!projectile.alive || now < projectile.launch) {
				continue;
			}
			projectile.launched = true;
			Vec3 next = projectile.pos.add(projectile.velocity);
			BlockHitResult hit = LoadedPath.ray(volley.source.level(), projectile.pos, next, null);
			projectile.stepBlocked = hit.getType() != HitResult.Type.MISS;
			Vec3 end = projectile.stepBlocked ? hit.getLocation() : next;
			projectile.stepEnd = end;
			AABB sweep = new AABB(projectile.pos, end).inflate(volley.hitRadius + 1.0D);
			box = box == null ? sweep : box.minmax(sweep);
		}
		if (box == null) {
			return;
		}
		Vec3 centre = box.getCenter();
		double radius = Math.min(TechniquePower.radiusCap(true) * 2.0D,
				Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5D + 1.0D);
		List<LivingEntity> near = volley.source.hostiles(centre, radius, false);
		for (Projectile projectile : volley.projectiles) {
			if (!projectile.alive || !projectile.launched || projectile.stepEnd == null) {
				continue;
			}
			Vec3 from = projectile.pos;
			// the clip of the first loop, reused: one read-only clip per projectile per tick (22.15.1)
			boolean blocked = projectile.stepBlocked;
			Vec3 end = projectile.stepEnd;
			projectile.stepEnd = null;
			for (LivingEntity target : near) {
				if (!projectile.alive) {
					break;
				}
				AABB body = target.getBoundingBox().inflate(volley.hitRadius);
				if (!body.contains(from) && body.clip(from, end).isEmpty()) {
					continue;
				}
				if (!allowed(volley, target)) {
					continue;
				}
				volley.source.projectileHit(volley.run, target, target.getBoundingBox().getCenter());
				if (projectile.pierceLeft == 0) {
					projectile.alive = false;
				} else if (projectile.pierceLeft > 0) {
					projectile.pierceLeft--;
				}
			}
			projectile.travelled += from.distanceTo(end);
			projectile.pos = end;
			if (blocked || projectile.travelled >= volley.range) {
				if (projectile.alive) {
					volley.source.projectileLanded(volley.run, end);
				}
				projectile.alive = false;
			}
		}
	}

	/** Per-target and max-target limits of the volley; records the hit. */
	private static boolean allowed(Volley volley, LivingEntity target) {
		int already = volley.hits.getOrDefault(target, 0);
		if (volley.perTarget > 0 && already >= volley.perTarget) {
			return false;
		}
		if (already == 0 && volley.maxTargets > 0 && volley.targeted.size() >= volley.maxTargets) {
			return false;
		}
		volley.hits.put(target, already + 1);
		volley.targeted.add(target);
		return true;
	}

	private static void finish(Volley volley) {
		if (volley.done) {
			return;
		}
		volley.done = true;
		VOLLEYS.remove(volley);
		live = Math.max(0, live - volley.projectiles.size());
		volley.source.activity(-1);
		volley.source.runEnded(volley.run);
	}

	static void endFrom(Caster source) {
		for (Volley volley : List.copyOf(VOLLEYS)) {
			if (volley.source == source) {
				finish(volley);
			}
		}
	}

	static int count() {
		return live;
	}

	private VirtualProjectiles() {
	}
}
