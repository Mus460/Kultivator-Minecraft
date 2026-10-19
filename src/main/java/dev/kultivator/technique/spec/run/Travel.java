package dev.kultivator.technique.spec.run;

import dev.kultivator.data.Dao;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.util.LoadedPath;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The travelling shapes of DESIGN 22.3.1: WAVE and FRONT (one entity query per 4 ticks covering the next 4 ticks of
 * travel), travelling BEAMs past 64 blocks (≤ 16-block segments, 2 per tick, loaded chunks only), RAIN (impacts landing in
 * the same tick share one query) and CHAIN (one query per hop). Fronts and beams stop at the first solid block unless
 * SPACE or DEWA; every ray is a read-only clip along a loaded path.
 */
final class Travel {

	private static final int CHUNK = 4;
	private static final double SEGMENT = 16.0D;

	// ================================================================== WAVE

	/** {@code preview} ≥ 0: only the CAST of the build-up (that many ticks before the release); −1: the wave itself. */
	static void wave(Caster cast, Caster.Run run, Shape.Wave wave, int preview) {
		Vec3 origin = Steps.anchorPoint(cast, wave.origin(), run);
		Val v = wave.r1();
		double r1;
		if (!v.set()) {
			double previous = Double.isNaN(cast.lastAreaRadius) ? 6.0D : cast.lastAreaRadius;
			r1 = previous + (Double.isNaN(v.cap()) ? 0.0D : v.cap());
		} else {
			r1 = cast.area(v, TechniquePower.WAVE_RADIUS_CAP);
		}
		r1 = Math.min(TechniquePower.WAVE_RADIUS_CAP, r1 + wave.perRepeat() * run.rep);
		double r0 = Math.max(0.0D, wave.r0());
		double speed = Math.max(0.5D, wave.speed());
		double thickness = Math.max(0.5D, wave.thickness());
		double height = Math.max(1.0D, wave.height());
		int ticks = (int) Math.ceil(Math.max(0.0D, r1 - r0) / speed);
		if (preview >= 0) {
			cast.castFx(origin, origin, (float) r1, (float) thickness, 1, preview);
			return;
		}
		cast.castFx(origin, origin, (float) r1, (float) thickness, 1, ticks);
		cast.fx(run.travelPhase(), origin, origin, (float) r1, (float) thickness, 1, ticks);
		cast.lastAreaRadius = r1;
		Set<LivingEntity> done = new HashSet<>();
		cast.activity(1);
		double outer = r1;
		new Runnable() {
			int k;

			@Override
			public void run() {
				if (cast.ended) {
					return;
				}
				double f0 = r0 + speed * CHUNK * k;
				double f1 = Math.min(outer, f0 + speed * CHUNK);
				Steps.Hits hits = new Steps.Hits(origin, f1, false, cast.look);
				// a flattened query (the ring's height), never a cube of the full radius (22.15.1)
				for (LivingEntity target : cast.hostilesFlat(origin, f1 + thickness + 1.0D, height + 3.0D, false)) {
					if (done.contains(target)) {
						continue;
					}
					Vec3 rel = target.position().subtract(origin);
					double distance = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
					double slack = thickness * 0.5D + target.getBbWidth() * 0.5D;
					if (Math.abs(rel.y) <= height + target.getBbHeight() && distance >= f0 - slack && distance <= f1 + slack) {
						hits.add(target, 1.0D);
						done.add(target);
					}
				}
				Steps.deliver(cast, run, hits);
				k++;
				if (f1 >= outer) {
					cast.activity(-1);
					Steps.runEnded(cast, run);
				} else {
					cast.schedule(CHUNK, this);
				}
			}
		}.run();
	}

	// ================================================================== FRONT

	/** Height changes a forward wave tries when a segment is blocked: up 1, up 2, down 1. */
	private static final double[] WAVE_STEPS = {1.0D, 2.0D, -1.0D};

	private static final class Line {
		final Vec3 start;
		final Vec3 dir;
		final double factor;
		double stop;
		boolean done;
		/** A forward wave climbs (or drops) over small steps of terrain: its height offset now. */
		double rise;

		Line(Vec3 start, Vec3 dir, double factor, double stop) {
			this.start = start;
			this.dir = dir;
			this.factor = factor;
			this.stop = stop;
		}
	}

	/** {@code preview} ≥ 0: only the CAST of the build-up; −1: the front itself. */
	static void front(Caster cast, Caster.Run run, Shape.Front front, int preview) {
		boolean dewa = cast.spec.dewa();
		double lengthCap = front.travelling() && dewa ? TechniquePower.TRAVEL_CAP : TechniquePower.FRONT_LENGTH_CAP;
		double length = cast.area(front.length(), lengthCap);
		double width = cast.area(front.width(), TechniquePower.FRONT_WIDTH_CAP);
		double height = cast.area(front.height(), TechniquePower.FRONT_HEIGHT_CAP);
		double speed = Math.max(1.0D, Math.min(TechniquePower.FRONT_SPEED_MAX, front.speed()));
		int base = Math.max(1, cast.count(front.count(), 12.0D));
		int count = Math.min(12, base + cast.bonusCount);
		boolean pass = front.passBlocks() && (cast.spec.dao() == Dao.SPACE || dewa);
		Vec3 flat = Steps.horizontalLook(cast);
		Vec3 origin;
		Vec3 baseDir;
		switch (front.mode()) {
			case AIM -> {
				Vec3 eye = front.from().kind() == Anchor.Kind.CASTER ? cast.caster.getEyePosition() : Steps.anchorPoint(cast, front.from(), run);
				Vec3 to = cast.aim.subtract(eye);
				baseDir = to.lengthSqr() < 1.0E-4D ? cast.look : to.normalize();
				origin = eye.add(baseDir.scale(front.ahead())).add(0.0D, -1.0D, 0.0D);
			}
			case SKY -> {
				double sky = Math.max(2.0D, front.skyHeight());
				// pressed down onto its anchor (a dense cluster, a hit), or the aim
				Vec3 ground = front.from().kind() == Anchor.Kind.CASTER ? cast.aim : Steps.anchorPoint(cast, front.from(), run);
				origin = ground.add(0.0D, sky, 0.0D);
				baseDir = new Vec3(0.0D, -1.0D, 0.0D);
				length = Math.min(lengthCap, sky + 1.0D);
			}
			default -> {
				// LOOK, and SKY_BAND (S10): the damage line runs along the ground ahead; only the visual comes from the sky
				Vec3 from = front.from().kind() == Anchor.Kind.CASTER ? cast.caster.position() : Steps.anchorPoint(cast, front.from(), run);
				baseDir = flat;
				origin = from.add(baseDir.scale(front.ahead()));
			}
		}
		boolean band = front.mode() == Shape.Front.Mode.SKY_BAND;
		double fan = front.fan() > 0.0D ? front.fan() : 20.0D;
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double offset = count <= 1 ? 0.0D : (i - (count - 1) / 2.0D) * fan;
			Vec3 dir = front.mode() == Shape.Front.Mode.SKY ? baseDir : Caster.rotateYaw(baseDir, offset);
			lines.add(new Line(origin, dir, i >= base ? cast.bonusCountFactor : 1.0D, length));
		}
		int ticks = (int) Math.ceil(length / speed);
		Vec3 far = origin.add(baseDir.scale(length));
		if (preview >= 0) {
			Vec3 at = front.mode() == Shape.Front.Mode.AIM || band ? cast.hand() : origin;
			cast.castFx(at, far, (float) width, (float) length, count, preview);
			return;
		}
		cast.castFx(origin, far, (float) width, (float) length, count, ticks);
		if (band) {
			// the staff, diagram or phoenix comes down from a band high over the line and sweeps along it
			double sky = Math.max(4.0D, front.skyHeight());
			cast.fx(run.travelPhase(), origin.add(0.0D, sky, 0.0D), far, (float) width, (float) length, count, ticks, -1, 0, 1, (int) Math.round(sky * 100.0D),
					Payloads.TechFx.FLAG_PRE_IMPACT);
		} else if (count > 1 && count <= 8 && front.mode() != Shape.Front.Mode.SKY) {
			// a fan of fronts: one event per line (S9), so each crescent flies its own way
			for (Line line : lines) {
				int i = lines.indexOf(line);
				cast.fx(run.travelPhase(), origin, origin.add(line.dir.scale(length)), (float) width, (float) length, 1, ticks, -1, i, count, 0, 0);
			}
		} else {
			cast.fx(run.travelPhase(), origin, far, (float) width, (float) length, count, ticks);
		}
		Map<LivingEntity, Integer> perTarget = new HashMap<>();
		int maxPer = Math.max(1, front.perTarget());
		double reach = length;
		double finalLength = length;
		cast.activity(1);
		new Runnable() {
			int k;

			@Override
			public void run() {
				if (cast.ended) {
					return;
				}
				double s0 = speed * CHUNK * k;
				double s1 = Math.min(finalLength, s0 + speed * CHUNK);
				for (Line line : lines) {
					if (line.done || pass) {
						continue;
					}
					boolean look = front.mode() == Shape.Front.Mode.LOOK || front.mode() == Shape.Front.Mode.SKY_BAND;
					Vec3 lift = look ? new Vec3(0.0D, 0.6D + line.rise, 0.0D) : Vec3.ZERO;
					Vec3 a = line.start.add(line.dir.scale(s0)).add(lift);
					Vec3 b = line.start.add(line.dir.scale(s1)).add(lift);
					if (!LoadedPath.loaded(cast.level, a, b)) {
						line.stop = Math.min(line.stop, s0);
						continue;
					}
					BlockHitResult hit = LoadedPath.ray(cast.level, a, b, cast.caster);
					if (hit.getType() != HitResult.Type.MISS && look) {
						// A forward wave rolls over a 1-2 block rise (or down a step) instead of dying on it: one read-only ray per try.
						for (double step : WAVE_STEPS) {
							Vec3 up = new Vec3(0.0D, step, 0.0D);
							if (LoadedPath.ray(cast.level, a.add(up), b.add(up), cast.caster).getType() == HitResult.Type.MISS) {
								line.rise += step;
								hit = null;
								break;
							}
						}
					}
					if (hit != null && hit.getType() != HitResult.Type.MISS) {
						line.stop = Math.min(line.stop, s0 + a.distanceTo(hit.getLocation()));
					}
				}
				Vec3 centre = origin.add(baseDir.scale((s0 + s1) * 0.5D));
				// one query per 4-tick segment, centred on the segment: a fan of lines covers the band between the arcs
				// s0 and s1 within ± half the fan, measured from the segment's centre (22.15.1)
				double radius = (s1 - s0) * 0.5D + width * 0.5D + height + 1.0D;
				if (lines.size() > 1 && front.mode() != Shape.Front.Mode.SKY) {
					double half = Math.toRadians(Math.min(180.0D, fan * (lines.size() - 1) / 2.0D));
					double mid = (s0 + s1) * 0.5D;
					double spread = 0.0D;
					for (double s : new double[]{s0, s1}) {
						for (double angle : new double[]{0.0D, half}) {
							double dx = s * Math.cos(angle) - mid;
							double dy = s * Math.sin(angle);
							spread = Math.max(spread, Math.sqrt(dx * dx + dy * dy));
						}
					}
					radius = spread + width * 0.5D + height + 1.0D;
				}
				List<LivingEntity> candidates = cast.hostiles(centre, radius, false);
				Steps.Hits hits = new Steps.Hits(centre, width * 0.5D, false, baseDir);
				for (Line line : lines) {
					if (line.done) {
						continue;
					}
					double top = Math.min(s1, line.stop);
					for (LivingEntity target : candidates) {
						if (perTarget.getOrDefault(target, 0) >= maxPer) {
							continue;
						}
						Vec3 rel = target.position().subtract(line.start);
						double along;
						double lateral;
						double vertical;
						if (front.mode() == Shape.Front.Mode.SKY) {
							along = -rel.y;
							lateral = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
							vertical = 0.0D;
						} else if (front.mode() == Shape.Front.Mode.AIM) {
							along = rel.dot(line.dir);
							lateral = rel.subtract(line.dir.scale(along)).length();
							vertical = 0.0D;
						} else {
							along = rel.x * line.dir.x + rel.z * line.dir.z;
							double px = rel.x - line.dir.x * along;
							double pz = rel.z - line.dir.z * along;
							lateral = Math.sqrt(px * px + pz * pz);
							vertical = rel.y - line.rise;
						}
						double slack = target.getBbWidth() * 0.5D;
						if (along < s0 - slack - 0.5D || along > top + slack + 0.5D || lateral > width * 0.5D + slack
								|| vertical < -2.0D || vertical > height) {
							continue;
						}
						perTarget.merge(target, 1, Integer::sum);
						hits.add(target, line.factor);
					}
					if (s1 >= line.stop || s1 >= finalLength) {
						line.done = true;
						Vec3 end = line.start.add(line.dir.scale(Math.min(line.stop, finalLength))).add(0.0D, line.rise, 0.0D);
						cast.markEnd(end);
						if (!run.byProduct && !cast.echo) {
							cast.destruction.impact(end, line.dir, true);
						}
					}
				}
				Steps.deliver(cast, run, hits);
				k++;
				boolean all = true;
				for (Line line : lines) {
					all &= line.done;
				}
				if (all || s1 >= reach) {
					cast.activity(-1);
					Steps.runEnded(cast, run);
				} else {
					cast.schedule(CHUNK, this);
				}
			}
		}.run();
	}

	// ================================================================== travelling BEAM

	static void beam(Caster cast, Caster.Run run, Shape.Beam beam) {
		double length = Steps.beamLength(cast, beam);
		double halfWidth = cast.area(beam.halfWidth(), TechniquePower.BEAM_WIDTH_CAP);
		Vec3 from = beam.from().kind() == Anchor.Kind.CASTER ? cast.caster.getEyePosition() : Steps.anchorPoint(cast, beam.from(), run);
		Vec3 dir = cast.look;
		boolean pass = beam.passBlocks() && (cast.spec.dao() == Dao.SPACE || cast.spec.dewa());
		int pierce = beam.pierceCount();
		int limit = pierce == Shape.Beam.PIERCE_ALL ? Integer.MAX_VALUE : Math.max(1, pierce == 0 ? 1 : pierce);
		int segments = (int) Math.ceil(length / SEGMENT);
		Vec3 far = from.add(dir.scale(length));
		Vec3 hand = cast.caster.getEyePosition().add(dir.scale(2.0D)).add(0.0D, -0.3D, 0.0D);
		cast.castFx(hand, far, (float) halfWidth, (float) length, 1, segments / 2 + 1);
		cast.fx(run.travelPhase(), from, far, (float) halfWidth, (float) length, 1, segments / 2 + 1);
		Set<LivingEntity> done = new HashSet<>();
		cast.activity(1);
		new Runnable() {
			int segment;
			boolean stopped;

			@Override
			public void run() {
				if (cast.ended) {
					return;
				}
				for (int n = 0; n < 2 && !stopped && segment < segments; n++, segment++) {
					Vec3 a = from.add(dir.scale(segment * SEGMENT));
					Vec3 b = from.add(dir.scale(Math.min(length, (segment + 1) * SEGMENT)));
					if (!LoadedPath.loaded(cast.level, a, b)) {
						b = LoadedPath.clip(cast.level, a, b);
						stopped = true;
					}
					if (!pass) {
						BlockHitResult hit = LoadedPath.ray(cast.level, a, b, cast.caster);
						if (hit.getType() != HitResult.Type.MISS) {
							b = hit.getLocation();
							stopped = true;
							cast.destruction.beam(from, dir, b);
						}
					}
					Steps.Hits hits = new Steps.Hits(a, halfWidth, true, dir);
					List<LivingEntity> line = new ArrayList<>();
					for (LivingEntity target : cast.hostiles(a.add(b).scale(0.5D), a.distanceTo(b) * 0.5D + halfWidth + 2.0D, true)) {
						AABB body = target.getBoundingBox().inflate(halfWidth);
						if (!done.contains(target) && (body.contains(a) || body.clip(a, b).isPresent())) {
							line.add(target);
						}
					}
					line.sort(java.util.Comparator.comparingDouble(target -> target.distanceToSqr(from)));
					for (LivingEntity target : line) {
						if (done.size() >= limit) {
							stopped = true;
							break;
						}
						done.add(target);
						hits.add(target, 1.0D);
					}
					Steps.deliver(cast, run, hits);
					cast.markEnd(b);
				}
				if (stopped || segment >= segments) {
					cast.activity(-1);
					Steps.runEnded(cast, run);
				} else {
					cast.schedule(1, this);
				}
			}
		}.run();
	}

	// ================================================================== RAIN

	/**
	 * {@code preview} ≥ 0: only the CAST of the build-up; −1: the rain itself. Each drop with TRAVEL layers is announced
	 * {@code fall} ticks before it lands (a pre-impact TRAVEL from 14 blocks up, S10); the IMPACT lands at the ground point
	 * (pos = ground, to = the sky point, S9); a spec with PULSE layers gets one PULSE per impact tick (S17).
	 */
	static void rain(Caster cast, Caster.Run run, Shape.Rain rain, int preview) {
		Vec3 centre;
		double radius;
		if (rain.anchor().kind() == Anchor.Kind.AREA && !rain.radius().set()) {
			centre = cast.areaCentre();
			radius = Math.max(2.0D, cast.areaRadius());
		} else {
			Vec3 point = Steps.anchorPoint(cast, rain.anchor(), run);
			double range = cast.area(rain.range(), TechniquePower.ZONE_RANGE_CAP);
			centre = rain.anchor().kind() == Anchor.Kind.AIM ? Steps.clampToRange(cast, point, range) : point;
			radius = cast.area(rain.radius(), TechniquePower.RAIN_RADIUS_CAP);
		}
		int impacts = cast.countWithBonus(rain.impacts(), TechniquePower.RAIN_IMPACTS_CAP);
		double coef = 1.0D;
		if (TechniqueRunner.lowLoad() && impacts > 1) {
			impacts = (impacts + 1) / 2;
			coef = 2.0D;
		}
		int over = Math.max(1, rain.over());
		double impactRadius = cast.area(rain.impactRadius(), TechniquePower.RAIN_IMPACT_RADIUS_CAP);
		int fall = Math.max(0, rain.fall());
		cast.lastAreaRadius = radius;
		if (preview >= 0) {
			cast.castFx(centre, centre, (float) radius, (float) impactRadius, impacts, preview + over + fall);
			return;
		}
		cast.castFx(centre, centre, (float) radius, (float) impactRadius, impacts, over + fall);
		if (run.step.extraPhase() >= 0) {
			// a rain of its own phase (a stage's storm) announces itself once
			cast.fx(run.travelPhase(), centre, centre, (float) radius, (float) impactRadius, impacts, over + fall);
		}
		int total = impacts;
		boolean announce = fall > 0 && !cast.spec.fx(Phase.TRAVEL).isEmpty();
		boolean pulses = !cast.spec.fx(Phase.PULSE).isEmpty();
		Map<Integer, Integer> perTick = new HashMap<>();
		for (int i = 0; i < impacts; i++) {
			int tick = impacts <= 1 ? 0 : (int) Math.round(over * (i / (double) (impacts - 1)));
			perTick.merge(tick + fall, 1, Integer::sum);
		}
		Set<LivingEntity> struck = new HashSet<>();
		RandomSource random = RandomSource.create(cast.seed ^ run.step.at());
		double coefFactor = coef;
		int last = 0;
		cast.activity(1);
		Phase impactPhase = run.step.extraPhase() >= 0 ? Phase.extra(run.step.extraPhase()) : Phase.IMPACT;
		int index = 0;
		for (Map.Entry<Integer, Integer> entry : perTick.entrySet()) {
			int count = entry.getValue();
			int landing = entry.getKey();
			int firstIndex = index;
			index += count;
			last = Math.max(last, landing);
			int pick = announce ? Math.max(0, landing - fall) : landing;
			cast.schedule(pick, () -> {
				if (cast.ended) {
					return;
				}
				// impacts of this tick share one flattened query (22.15.1); the points are chosen when the drops start falling
				List<LivingEntity> around = cast.hostilesFlat(centre, radius + impactRadius, 6.0D, false);
				List<Vec3> points = new java.util.ArrayList<>(count);
				for (int n = 0; n < count; n++) {
					Vec3 point;
					if (!around.isEmpty() && random.nextDouble() < rain.targeted()) {
						LivingEntity chosen = null;
						for (LivingEntity candidate : around) {
							if (!struck.contains(candidate)) {
								chosen = candidate;
								break;
							}
						}
						if (chosen == null) {
							chosen = around.get(random.nextInt(around.size()));
						}
						struck.add(chosen);
						point = chosen.position();
					} else {
						double angle = random.nextDouble() * Math.PI * 2.0D;
						double r = Math.sqrt(random.nextDouble()) * radius;
						point = centre.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
					}
					points.add(point);
					if (announce) {
						cast.fx(Phase.TRAVEL, point.add(0.0D, 14.0D, 0.0D), point, (float) impactRadius, 14.0F, total, landing - pick, -1, firstIndex + n,
								total, 0, Payloads.TechFx.FLAG_PRE_IMPACT);
					}
				}
				Runnable land = () -> {
					if (cast.ended) {
						return;
					}
					List<LivingEntity> now = announce ? cast.hostilesFlat(centre, radius + impactRadius, 6.0D, false) : around;
					for (int n = 0; n < points.size(); n++) {
						Vec3 point = points.get(n);
						Steps.Hits hits = new Steps.Hits(point, impactRadius, false, cast.look);
						hits.coefFactor = coefFactor;
						for (LivingEntity target : now) {
							Vec3 rel = target.position().subtract(point);
							double reach = impactRadius + target.getBbWidth() * 0.5D;
							if (rel.x * rel.x + rel.z * rel.z <= reach * reach && Math.abs(rel.y) <= 3.0D + target.getBbHeight()) {
								hits.add(target, 1.0D);
							}
						}
						cast.fx(impactPhase, point, point.add(0.0D, 14.0D, 0.0D), (float) impactRadius, 14.0F, 1, 0, -1, firstIndex + n, total, 0, 0);
						Steps.deliver(cast, run, hits);
						if (!run.byProduct && !cast.echo) {
							cast.destruction.impact(point, cast.look, true);
						}
					}
					if (pulses) {
						cast.fx(Phase.PULSE, centre, centre, (float) radius, (float) impactRadius, points.size(), 0);
					}
				};
				if (announce && landing > pick) {
					cast.schedule(landing - pick, land);
				} else {
					land.run();
				}
			});
		}
		cast.schedule(last + 1, () -> {
			cast.activity(-1);
			Steps.runEnded(cast, run);
		});
	}

	// ================================================================== CHAIN

	static void chain(Caster cast, Caster.Run run, Shape.Chain chain) {
		double range = cast.area(chain.range(), TechniquePower.CHAIN_RANGE_CAP);
		double jump = cast.area(chain.jump(), TechniquePower.CHAIN_JUMP_CAP);
		int jumps = (int) Math.min(TechniquePower.CHAIN_JUMPS_CAP, cast.count(chain.jumps(), TechniquePower.CHAIN_JUMPS_CAP) + cast.bonusChain);
		int delay = Math.max(1, chain.delay());
		double falloff = chain.falloff() <= 0.0D ? 0.85D : chain.falloff();
		LivingEntity first = switch (chain.from().kind()) {
			case HIT -> run.hit != null && run.hit.isAlive() ? run.hit : Steps.lockedOrFront(cast, range, true, run);
			case TARGET -> Steps.lockedOrFront(cast, range, true, run);
			default -> {
				Vec3 point = Steps.anchorPoint(cast, chain.from(), run);
				LivingEntity nearest = null;
				for (LivingEntity candidate : cast.hostiles(point, Math.min(range, cast.radiusCap()), true)) {
					nearest = candidate;
					break;
				}
				yield nearest;
			}
		};
		Vec3 hand = cast.caster.getEyePosition().add(cast.look.scale(0.6D)).add(0.0D, -0.35D, 0.0D);
		if (first == null || !cast.mayHit(first, true)) {
			cast.castFx(hand, cast.aim, 0.0F, 0.0F, 0, 0);
			Steps.runEnded(cast, run);
			return;
		}
		Vec3 firstMiddle = first.getBoundingBox().getCenter();
		cast.castFx(hand, firstMiddle, (float) jump, (float) hand.distanceTo(firstMiddle), jumps + 1, jumps * delay, first.getId());
		cast.fx(run.travelPhase(), hand, firstMiddle, 0.5F, (float) hand.distanceTo(firstMiddle), 1, delay, first.getId(), 0, jumps + 1, 0, 0);
		Steps.Hits hits = new Steps.Hits(first.position(), 0.0D, true, cast.look);
		hits.add(first, 1.0D);
		Steps.deliver(cast, run, hits);
		cast.markEnd(first.position());
		if (!run.byProduct && !cast.echo) {
			cast.destruction.impact(first.position(), cast.look, true);
		}
		Set<LivingEntity> visited = new HashSet<>();
		visited.add(first);
		LivingEntity[] previous = {first};
		Vec3[] previousPos = {firstMiddle};
		cast.activity(1);
		for (int h = 1; h <= jumps; h++) {
			int hop = h;
			cast.schedule(delay * h, () -> {
				if (cast.ended || previous[0] == null) {
					return;
				}
				Vec3 from = previous[0].isAlive() && !previous[0].isRemoved() ? previous[0].getBoundingBox().getCenter() : previousPos[0];
				LivingEntity next = null;
				for (LivingEntity candidate : cast.hostiles(from, jump, false)) {
					if (!visited.contains(candidate)) {
						next = candidate;
						break;
					}
				}
				if (next == null) {
					previous[0] = null;
					return;
				}
				visited.add(next);
				Vec3 middle = next.getBoundingBox().getCenter();
				// every hop travels from the last foe to the next (S9); the HIT on it comes from the delivery
				cast.fx(run.travelPhase(), from, middle, 0.8F, (float) from.distanceTo(middle), 1, delay, next.getId(), hop, jumps + 1, 0, 0);
				Steps.Hits hopHits = new Steps.Hits(next.position(), 0.0D, false, cast.look);
				hopHits.add(next, Math.pow(falloff, hop));
				Steps.deliver(cast, run, hopHits);
				cast.markEnd(next.position());
				if (!run.byProduct && !cast.echo) {
					cast.destruction.impact(next.position(), cast.look, true);
				}
				previous[0] = next;
				previousPos[0] = middle;
			});
		}
		cast.schedule(delay * jumps + 1, () -> {
			cast.activity(-1);
			Steps.runEnded(cast, run);
		});
	}

	private Travel() {
	}
}
