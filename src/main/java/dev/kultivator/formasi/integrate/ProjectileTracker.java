package dev.kultivator.formasi.integrate;

import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.effect.a.ShieldEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * L1 Lapis Perisai (DESIGN 24.4.1): projectiles whose owner is a hostile mob ({@code Enemy}, not a Dao Protector) are
 * tracked from {@code ENTITY_LOAD} when they spawn within radius + 32 of a running L1. The tracked set is checked every
 * tick; one that crosses the shell inward is discarded with a ripple, so it never explodes or hits a block. A projectile
 * whose current owner is a member is never touched.
 */
public final class ProjectileTracker {

	public static final double TRACK_MARGIN = 32.0D;
	public static final int MAX_AGE = 400;
	public static final int MAX_TRACKED = 128;

	private static final class Tracked {
		final Projectile projectile;
		final ServerLevel level;
		final int core;
		double last;
		int age;

		Tracked(Projectile projectile, ServerLevel level, int core, double last) {
			this.projectile = projectile;
			this.level = level;
			this.core = core;
			this.last = last;
		}
	}

	private static final List<Tracked> TRACKED = new ArrayList<>();

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register(ProjectileTracker::onLoad);
		ServerTickEvents.END_SERVER_TICK.register(ProjectileTracker::tick);
	}

	private static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Projectile projectile) || TRACKED.size() >= MAX_TRACKED) {
			return;
		}
		Entity owner = projectile.getOwner();
		if (!(owner instanceof Mob mob) || !(owner instanceof Enemy) || DaoProtectors.isProtector(mob)) {
			return;
		}
		for (CoreRecord record : CoreTicker.store(level).all()) {
			if (!record.state.running() || !"five_layer_shield".equals(record.spec)) {
				continue;
			}
			double distance = projectile.position().distanceTo(Vec3.atCenterOf(record.eye));
			if (distance <= record.radius + TRACK_MARGIN) {
				TRACKED.add(new Tracked(projectile, level, record.id, distance));
				return;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (TRACKED.isEmpty()) {
			return;
		}
		Iterator<Tracked> iterator = TRACKED.iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next();
			Projectile projectile = tracked.projectile;
			CoreRecord record = CoreTicker.store(tracked.level).byId(tracked.core);
			if (projectile.isRemoved() || record == null || !record.state.running() || ++tracked.age > MAX_AGE) {
				iterator.remove();
				continue;
			}
			Entity owner = projectile.getOwner();
			if (owner != null && Ownership.isMember(server, record.owner, owner)) {
				iterator.remove();
				continue;
			}
			Vec3 centre = Vec3.atCenterOf(record.eye);
			double distance = projectile.position().distanceTo(centre);
			if (distance <= record.radius && tracked.last > record.radius) {
				Fx.send(tracked.level, projectile.position(), FxType.FORMASI, FormasiFxIds.RIPPLE, Family.PELINDUNG.ordinal(), 1.0F);
				projectile.discard();
				record.stat("repels", 1);
				ShieldEffect.activity(record, tracked.level.getGameTime());
				iterator.remove();
				continue;
			}
			tracked.last = distance;
			if (distance > record.radius + TRACK_MARGIN * 2.0D) {
				iterator.remove();
			}
		}
	}

	/** Tracked projectiles now (stats). */
	public static int size() {
		return TRACKED.size();
	}

	private ProjectileTracker() {
	}
}
