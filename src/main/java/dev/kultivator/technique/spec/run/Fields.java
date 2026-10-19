package dev.kultivator.technique.spec.run;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lasting areas (DESIGN 22.3.1): DOMAIN (follows the caster, one per caster; a new one replaces the old), FIELD (placed,
 * at most 2 per caster; the oldest ends), AURA (a small follow zone in its own slot). Each pulse (≥ 10 ticks apart) is one
 * entity query; DOMAIN and FIELD heights are at most 16 when the radius is above 16. FIELD walls bounce non-bosses back
 * (checked every 5 ticks with one query). Under server load the pulses are thinned ×0.5 and each kept pulse deals ×2.
 */
public final class Fields {

	private static final int WALL_INTERVAL = 5;

	/** One lasting area of a cast. */
	static final class Area {
		final Caster source;
		final Step step;
		final Shape.Kind kind;
		final @Nullable Vec3 placed;
		final double radius;
		final double height;
		final long start;
		final long until;
		final int pulse;
		long nextPulse;
		int pulses;
		final double ramp;
		final int rampMax;
		final Shape.Field.Form form;
		final Shape.Field.Walls walls;
		final List<Vec3> emitters;
		final int repetition;
		boolean ended;

		Area(Caster source, Step step, Shape.Kind kind, @Nullable Vec3 placed, double radius, double height, long start, int duration,
				int pulse, double ramp, int rampMax, Shape.Field.Form form, Shape.Field.Walls walls, List<Vec3> emitters, int repetition) {
			this.source = source;
			this.step = step;
			this.kind = kind;
			this.placed = placed;
			this.radius = radius;
			this.height = height;
			this.start = start;
			this.until = start + Math.max(1, duration);
			this.pulse = Math.max(TechniquePower.PULSE_MIN, pulse);
			this.nextPulse = start;
			this.ramp = ramp;
			this.rampMax = rampMax;
			this.form = form;
			this.walls = walls;
			this.emitters = emitters;
			this.repetition = repetition;
		}

		Vec3 centre() {
			return placed != null ? placed : source.caster().position();
		}

		/** True when {@code pos} lies inside (shape and height). */
		boolean contains(Vec3 pos) {
			Vec3 c = centre();
			double dx = pos.x - c.x;
			double dz = pos.z - c.z;
			if (Math.abs(pos.y - c.y) > height) {
				return false;
			}
			return switch (form) {
				case SQUARE -> Math.abs(dx) <= radius && Math.abs(dz) <= radius;
				case OCTAGON -> Math.abs(dx) <= radius && Math.abs(dz) <= radius && Math.abs(dx) + Math.abs(dz) <= radius * 1.414D;
				case RING -> {
					double d = Math.sqrt(dx * dx + dz * dz);
					yield d <= radius && d >= radius * 0.5D;
				}
				default -> dx * dx + dz * dz <= radius * radius;
			};
		}

		/** The four corners of a square field (otherwise four points on the circle). */
		List<Vec3> corners() {
			Vec3 c = centre();
			List<Vec3> list = new ArrayList<>(4);
			double r = form == Shape.Field.Form.SQUARE ? radius : radius * 0.7071D;
			list.add(c.add(r, 0.0D, r));
			list.add(c.add(-r, 0.0D, r));
			list.add(c.add(-r, 0.0D, -r));
			list.add(c.add(r, 0.0D, -r));
			return list;
		}

		/** Damage factor of the current pulse (RAMP). */
		double rampFactor() {
			if (ramp <= 0.0D) {
				return 1.0D;
			}
			int steps = rampMax > 0 ? Math.min(rampMax, pulses) : pulses;
			return 1.0D + ramp * steps;
		}
	}

	private static final List<Area> AREAS = new ArrayList<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> AREAS.clear());
	}

	/** Places a lasting area, enforcing the one-of slots of 22.15 (the replaced areas end with REPLACED). */
	static Area start(Area area) {
		LivingEntity owner = area.source.caster();
		List<Area> mine = new ArrayList<>();
		for (Area other : AREAS) {
			if (!other.ended && other.source.caster() == owner && other.kind == area.kind) {
				mine.add(other);
			}
		}
		if (area.kind == Shape.Kind.FIELD) {
			while (mine.size() >= TechniquePower.FIELDS_PER_CASTER) {
				end(mine.remove(0), EndReason.REPLACED);
			}
		} else {
			for (Area other : mine) {
				end(other, EndReason.REPLACED);
			}
		}
		AREAS.add(area);
		return area;
	}

	static void end(Area area, EndReason reason) {
		if (area.ended) {
			return;
		}
		area.ended = true;
		AREAS.remove(area);
		if (reason == EndReason.REPLACED || reason == EndReason.CANCELLED) {
			// the area's lasting layers stop on every client at once (S8), not when their ticks run out
			area.source.abortFx();
		}
		area.source.areaEnded(area, reason);
	}

	/** Ends every area of a cast. */
	static void endFrom(Caster source, EndReason reason) {
		for (Area area : List.copyOf(AREAS)) {
			if (area.source == source) {
				end(area, reason);
			}
		}
	}

	static void tick(long serverTick) {
		if (AREAS.isEmpty()) {
			return;
		}
		boolean thin = TechniqueRunner.lowLoad();
		for (Area area : List.copyOf(AREAS)) {
			if (area.ended) {
				continue;
			}
			if (!area.source.casterValid() || area.source.ended() && area.source.aborted()) {
				end(area, EndReason.CANCELLED);
				continue;
			}
			long now = area.source.caster().level().getGameTime();
			if (now >= area.until) {
				end(area, EndReason.DONE);
				continue;
			}
			if (area.walls != Shape.Field.Walls.NONE && serverTick % WALL_INTERVAL == 0) {
				area.source.walls(area);
			}
			if (now >= area.nextPulse) {
				area.nextPulse = now + area.pulse;
				area.pulses++;
				if (thin && area.pulses % 2 == 0) {
					continue;
				}
				area.source.pulse(area, thin ? 2.0D : 1.0D);
			}
		}
	}

	/** True when {@code pos} is inside one of {@code who}'s own fields, domains or auras (or the legacy Dao Domain). */
	public static boolean inOwnArea(LivingEntity who, Vec3 pos) {
		for (Area area : AREAS) {
			if (!area.ended && area.source.caster() == who && area.contains(pos)) {
				return true;
			}
		}
		return who instanceof net.minecraft.server.level.ServerPlayer player && dev.kultivator.technique.Techniques.inLegacyDomain(player, pos);
	}

	/** The centre of {@code who}'s newest area, or {@code fallback}. */
	public static Vec3 ownAreaCentre(LivingEntity who, Vec3 fallback) {
		for (int i = AREAS.size() - 1; i >= 0; i--) {
			Area area = AREAS.get(i);
			if (!area.ended && area.source.caster() == who) {
				return area.centre();
			}
		}
		return fallback;
	}

	/** The radius of {@code who}'s newest area (0 for none). */
	public static double ownAreaRadius(LivingEntity who) {
		for (int i = AREAS.size() - 1; i >= 0; i--) {
			Area area = AREAS.get(i);
			if (!area.ended && area.source.caster() == who) {
				return area.radius;
			}
		}
		return 0.0D;
	}

	/** The newest area of one cast, or null. */
	static @Nullable Area of(Caster source) {
		for (int i = AREAS.size() - 1; i >= 0; i--) {
			Area area = AREAS.get(i);
			if (!area.ended && area.source == source) {
				return area;
			}
		}
		return null;
	}

	/** Lasting areas of {@code who} for the HUD (kind, end tick, radius). */
	static List<Area> of(LivingEntity who) {
		List<Area> list = new ArrayList<>();
		for (Area area : AREAS) {
			if (!area.ended && area.source.caster() == who) {
				list.add(area);
			}
		}
		return list;
	}

	static boolean active(Caster source) {
		for (Iterator<Area> it = AREAS.iterator(); it.hasNext(); ) {
			Area area = it.next();
			if (!area.ended && area.source == source) {
				return true;
			}
		}
		return false;
	}

	static int count() {
		return AREAS.size();
	}

	private Fields() {
	}
}
