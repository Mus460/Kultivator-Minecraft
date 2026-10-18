package dev.kultivator.technique.spec.dsl;

/**
 * The shapes of DESIGN 22.3.1: who a step reaches. Pure data; the runner (P6c-mesin) evaluates them with one entity
 * query per evaluation and the hard caps of 22.15. Sizes are {@link Val}s: one number is fixed, two are base → spec cap.
 * Every record has a {@code DEFAULT} with the defaults of the table and fluent copies for each parameter, so the
 * catalogue reads like its notation: {@code nova().radius(6, 18)}, {@code beam().length(14, 35).halfWidth(0.8)}.
 */
public sealed interface Shape permits Shape.Self, Shape.Strike, Shape.Cone, Shape.Beam, Shape.Front, Shape.Nova,
		Shape.Wave, Shape.Zone, Shape.Pillar, Shape.Rain, Shape.Chain, Shape.Target, Shape.All, Shape.Recall,
		Shape.Volley, Shape.Domain, Shape.Field, Shape.Aura, Shape.Path {

	enum Kind {
		SELF, STRIKE, CONE, BEAM, FRONT, NOVA, WAVE, ZONE, PILLAR, RAIN, CHAIN, TARGET, ALL, RECALL, VOLLEY, DOMAIN,
		FIELD, AURA, PATH;

		/** PRECISE shapes may hit calm neutral mobs (22.3.5 rule 2): STRIKE, BEAM, TARGET (and the first CHAIN hop). */
		public boolean precise() {
			return this == STRIKE || this == BEAM || this == TARGET;
		}

		/** Lasting areas that pulse (they force NO_ECHO, 22.3.4). */
		public boolean lasting() {
			return this == DOMAIN || this == FIELD || this == AURA;
		}
	}

	Kind kind();

	// ================================================================== SELF

	/** No hostiles; self and ally payloads only. */
	record Self() implements Shape {
		public static final Self INSTANCE = new Self();

		@Override
		public Kind kind() {
			return Kind.SELF;
		}
	}

	// ================================================================== STRIKE

	/** Targets within {@code range} of the eye (horizontal), inside the {@code arc} around the look, LOS; at most {@code max} (0 = no limit). */
	record Strike(Val range, Val arc, double height, int max, Anchor anchor) implements Shape {
		public static final Strike DEFAULT = new Strike(Val.of(4.0D), Val.of(120.0D), 2.5D, 0, Anchor.CASTER);

		@Override
		public Kind kind() {
			return Kind.STRIKE;
		}

		public Strike range(double value) { return new Strike(Val.of(value), arc, height, max, anchor); }
		public Strike range(double base, double cap) { return new Strike(Val.of(base, cap), arc, height, max, anchor); }
		public Strike arc(double degrees) { return new Strike(range, Val.of(degrees), height, max, anchor); }
		public Strike arc(double base, double cap) { return new Strike(range, Val.of(base, cap), height, max, anchor); }
		public Strike height(double value) { return new Strike(range, arc, value, max, anchor); }
		public Strike max(int value) { return new Strike(range, arc, height, value, anchor); }
		public Strike at(Anchor value) { return new Strike(range, arc, height, max, value); }
	}

	// ================================================================== CONE

	/**
	 * Apex at the eye; {@code count} cones fanned {@code fan}° apart (one by default), turned by {@code yaw}°; targets within
	 * 1.5 blocks only need dot > 0; LOS; at most {@code max} targets (0 = no limit).
	 */
	record Cone(Val length, Val halfAngle, Val count, double fan, double yaw, int max) implements Shape {
		public static final Cone DEFAULT = new Cone(Val.of(7.0D), Val.of(30.0D), Val.of(1.0D), 0.0D, 0.0D, 0);

		@Override
		public Kind kind() {
			return Kind.CONE;
		}

		public Cone length(double value) { return new Cone(Val.of(value), halfAngle, count, fan, yaw, max); }
		public Cone length(double base, double cap) { return new Cone(Val.of(base, cap), halfAngle, count, fan, yaw, max); }
		public Cone halfAngle(double degrees) { return new Cone(length, Val.of(degrees), count, fan, yaw, max); }
		public Cone halfAngle(double base, double cap) { return new Cone(length, Val.of(base, cap), count, fan, yaw, max); }
		public Cone count(Val value) { return new Cone(length, halfAngle, value, fan, yaw, max); }
		public Cone count(double value) { return count(Val.of(value)); }
		public Cone fan(double degrees) { return new Cone(length, halfAngle, count, degrees, yaw, max); }
		public Cone yaw(double degrees) { return new Cone(length, halfAngle, count, fan, degrees, max); }
		public Cone max(int value) { return new Cone(length, halfAngle, count, fan, yaw, value); }
	}

	// ================================================================== BEAM

	/**
	 * A ray from {@code from} (the eye by default) that stops at the first block (read-only clip) unless
	 * {@code passBlocks} (SPACE or DEWA specs only). {@code pierceCount}: 0 = the first target, n = up to n targets,
	 * {@link #PIERCE_ALL} = every target on the line. {@code travelling}: past 64 blocks a travelling hit in ≤ 16-block
	 * segments (Dewa PAMUNGKAS up to 128). {@code count} beams fanned {@code fan}° apart, turned by {@code yaw}°.
	 */
	record Beam(Val length, Val halfWidth, int pierceCount, boolean passBlocks, boolean travelling, Val count, double fan,
			double yaw, Anchor from) implements Shape {
		public static final int PIERCE_ALL = -1;
		public static final Beam DEFAULT = new Beam(Val.of(16.0D), Val.of(1.0D), 0, false, false, Val.of(1.0D), 0.0D, 0.0D, Anchor.CASTER);

		@Override
		public Kind kind() {
			return Kind.BEAM;
		}

		public Beam length(double value) { return new Beam(Val.of(value), halfWidth, pierceCount, passBlocks, travelling, count, fan, yaw, from); }
		public Beam length(double base, double cap) { return new Beam(Val.of(base, cap), halfWidth, pierceCount, passBlocks, travelling, count, fan, yaw, from); }
		public Beam halfWidth(double value) { return new Beam(length, Val.of(value), pierceCount, passBlocks, travelling, count, fan, yaw, from); }
		public Beam halfWidth(double base, double cap) { return new Beam(length, Val.of(base, cap), pierceCount, passBlocks, travelling, count, fan, yaw, from); }
		/** Hits every target on the line. */
		public Beam pierce() { return new Beam(length, halfWidth, PIERCE_ALL, passBlocks, travelling, count, fan, yaw, from); }
		/** Hits up to {@code targets} targets. */
		public Beam pierce(int targets) { return new Beam(length, halfWidth, targets, passBlocks, travelling, count, fan, yaw, from); }
		public Beam ignoreBlocks() { return new Beam(length, halfWidth, pierceCount, true, travelling, count, fan, yaw, from); }
		public Beam travel() { return new Beam(length, halfWidth, pierceCount, passBlocks, true, count, fan, yaw, from); }
		public Beam count(Val value) { return new Beam(length, halfWidth, pierceCount, passBlocks, travelling, value, fan, yaw, from); }
		public Beam count(double value) { return count(Val.of(value)); }
		public Beam fan(double degrees) { return new Beam(length, halfWidth, pierceCount, passBlocks, travelling, count, degrees, yaw, from); }
		public Beam yaw(double degrees) { return new Beam(length, halfWidth, pierceCount, passBlocks, travelling, count, fan, degrees, from); }
		public Beam from(Anchor value) { return new Beam(length, halfWidth, pierceCount, passBlocks, travelling, count, fan, yaw, value); }
	}

	// ================================================================== FRONT

	/**
	 * A travelling wall (crescents, waves, falling staffs): each target is hit once when the front passes (at most
	 * {@code perTarget} fronts of one cast per target). {@code mode}: LOOK = horizontal along the look, AIM = along the
	 * line to the aim point (dives), SKY = from {@code skyHeight} above, falling or pressed down along the aim line.
	 * {@code ahead}: starts this many blocks in front of the caster. Stops at a solid wall unless {@code passBlocks}
	 * (SPACE or DEWA). {@code travelling}: Dewa fronts may travel 128.
	 */
	record Front(Val length, Val width, Val height, double speed, boolean passBlocks, boolean travelling, Mode mode,
			double skyHeight, double ahead, Val count, double fan, int perTarget, Anchor from) implements Shape {
		public enum Mode { LOOK, AIM, SKY }

		public static final Front DEFAULT = new Front(Val.of(16.0D), Val.of(1.0D), Val.of(3.0D), 2.0D, false, false, Mode.LOOK,
				0.0D, 0.0D, Val.of(1.0D), 0.0D, 1, Anchor.CASTER);

		@Override
		public Kind kind() {
			return Kind.FRONT;
		}

		public Front length(double value) { return new Front(Val.of(value), width, height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front length(double base, double cap) { return new Front(Val.of(base, cap), width, height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front width(double value) { return new Front(length, Val.of(value), height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front width(double base, double cap) { return new Front(length, Val.of(base, cap), height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front height(double value) { return new Front(length, width, Val.of(value), speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front height(double base, double cap) { return new Front(length, width, Val.of(base, cap), speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		/** Blocks per tick (1-8). */
		public Front speed(double value) { return new Front(length, width, height, value, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front ignoreBlocks() { return new Front(length, width, height, speed, true, travelling, mode, skyHeight, ahead, count, fan, perTarget, from); }
		public Front travel() { return new Front(length, width, height, speed, passBlocks, true, mode, skyHeight, ahead, count, fan, perTarget, from); }
		/** Along the line to the aim point (a dive). */
		public Front toAim() { return new Front(length, width, height, speed, passBlocks, travelling, Mode.AIM, skyHeight, ahead, count, fan, perTarget, from); }
		/** Falls or is pressed down from {@code height} blocks up, along the aim line. */
		public Front fromSky(double blocks) { return new Front(length, width, height, speed, passBlocks, travelling, Mode.SKY, blocks, ahead, count, fan, perTarget, from); }
		public Front ahead(double blocks) { return new Front(length, width, height, speed, passBlocks, travelling, mode, skyHeight, blocks, count, fan, perTarget, from); }
		public Front count(Val value) { return new Front(length, width, height, speed, passBlocks, travelling, mode, skyHeight, ahead, value, fan, perTarget, from); }
		public Front count(double value) { return count(Val.of(value)); }
		public Front fan(double degrees) { return new Front(length, width, height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, degrees, perTarget, from); }
		public Front perTarget(int fronts) { return new Front(length, width, height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, fronts, from); }
		public Front from(Anchor value) { return new Front(length, width, height, speed, passBlocks, travelling, mode, skyHeight, ahead, count, fan, perTarget, value); }
	}

	// ================================================================== NOVA

	/** A sphere (or a shell from {@code inner}) at the anchor (the caster by default). */
	record Nova(Val radius, double inner, Anchor anchor) implements Shape {
		public static final Nova DEFAULT = new Nova(Val.of(6.0D), 0.0D, Anchor.CASTER);

		@Override
		public Kind kind() {
			return Kind.NOVA;
		}

		public Nova radius(double value) { return new Nova(Val.of(value), inner, anchor); }
		public Nova radius(double base, double cap) { return new Nova(Val.of(base, cap), inner, anchor); }
		/** The radius of the cast's own field or domain ("NOVA(field)"). */
		public Nova ofArea() { return new Nova(Val.NONE, inner, Anchor.AREA); }
		public Nova inner(double value) { return new Nova(radius, value, anchor); }
		public Nova at(Anchor value) { return new Nova(radius, inner, value); }
	}

	// ================================================================== WAVE

	/**
	 * An expanding ring front from {@code r0} to {@code r1} at {@code speed} b/t: a target is hit once when
	 * |dist − front| ≤ thickness/2. One query of r1 every 4 ticks. {@code perRepeat}: r1 grows by this much with each
	 * repetition of the step ("x3/10 WAVE(r1 6 / 10 / 14)").
	 */
	record Wave(double r0, Val r1, double speed, double thickness, double height, Anchor origin, double perRepeat) implements Shape {
		public static final Wave DEFAULT = new Wave(1.0D, Val.of(12.0D), 1.0D, 2.0D, 3.0D, Anchor.CASTER, 0.0D);

		@Override
		public Kind kind() {
			return Kind.WAVE;
		}

		public Wave r0(double value) { return new Wave(value, r1, speed, thickness, height, origin, perRepeat); }
		public Wave r1(double value) { return new Wave(r0, Val.of(value), speed, thickness, height, origin, perRepeat); }
		public Wave r1(double base, double cap) { return new Wave(r0, Val.of(base, cap), speed, thickness, height, origin, perRepeat); }
		/** r1 = the radius of the step's previous NOVA/ZONE plus {@code extra} ("WAVE(r1 = the nova radius)"). */
		public Wave r1Previous(double extra) { return new Wave(r0, new Val(Double.NaN, extra, 0.0D, false), speed, thickness, height, origin, perRepeat); }
		public Wave speed(double value) { return new Wave(r0, r1, value, thickness, height, origin, perRepeat); }
		public Wave thickness(double value) { return new Wave(r0, r1, speed, value, height, origin, perRepeat); }
		public Wave height(double value) { return new Wave(r0, r1, speed, thickness, value, origin, perRepeat); }
		public Wave from(Anchor value) { return new Wave(r0, r1, speed, thickness, height, value, perRepeat); }
		public Wave perRepeat(double value) { return new Wave(r0, r1, speed, thickness, height, origin, value); }
	}

	// ================================================================== ZONE

	/**
	 * A cylinder at the anchor (aim within {@code range}) after {@code delay} ticks; {@code skyHeight} > 0 = it falls from
	 * that height (the client animates the fall). {@code advance}: each repetition lands this many blocks further along
	 * the look (footprints).
	 */
	record Zone(Anchor anchor, Val range, Val radius, Val skyHeight, int delay, double height, double advance) implements Shape {
		public static final Zone DEFAULT = new Zone(Anchor.AIM, Val.of(24.0D), Val.of(5.0D), Val.of(0.0D), 0, 6.0D, 0.0D);

		@Override
		public Kind kind() {
			return Kind.ZONE;
		}

		public Zone at(Anchor value) { return new Zone(value, range, radius, skyHeight, delay, height, advance); }
		public Zone range(double value) { return new Zone(anchor, Val.of(value), radius, skyHeight, delay, height, advance); }
		public Zone range(double base, double cap) { return new Zone(anchor, Val.of(base, cap), radius, skyHeight, delay, height, advance); }
		public Zone radius(double value) { return new Zone(anchor, range, Val.of(value), skyHeight, delay, height, advance); }
		public Zone radius(double base, double cap) { return new Zone(anchor, range, Val.of(base, cap), skyHeight, delay, height, advance); }
		/** The radius of the cast's own field ("ZONE(field)"). */
		public Zone ofArea() { return new Zone(Anchor.AREA, range, Val.NONE, skyHeight, delay, height, advance); }
		public Zone fromSky(double blocks) { return new Zone(anchor, range, radius, Val.of(blocks), delay, height, advance); }
		public Zone fromSky(double base, double cap) { return new Zone(anchor, range, radius, Val.of(base, cap), delay, height, advance); }
		public Zone delay(int ticks) { return new Zone(anchor, range, radius, skyHeight, ticks, height, advance); }
		public Zone height(double value) { return new Zone(anchor, range, radius, skyHeight, delay, value, advance); }
		public Zone advance(double blocks) { return new Zone(anchor, range, radius, skyHeight, delay, height, blocks); }
	}

	// ================================================================== PILLAR

	/**
	 * Vertical cylinders: one at the anchor, one on each of {@code onTargets} hostiles within {@code search}, or
	 * {@code ring} pillars on a ring of {@code ringRadius} around the anchor (corners, emitters).
	 */
	record Pillar(Anchor anchor, Val radius, Val height, int delay, Val onTargets, Val search, int ring, double ringRadius) implements Shape {
		public static final Pillar DEFAULT = new Pillar(Anchor.AIM, Val.of(2.0D), Val.of(20.0D), 10, Val.of(0.0D), Val.of(16.0D), 0, 0.0D);

		@Override
		public Kind kind() {
			return Kind.PILLAR;
		}

		public Pillar at(Anchor value) { return new Pillar(value, radius, height, delay, onTargets, search, ring, ringRadius); }
		public Pillar radius(double value) { return new Pillar(anchor, Val.of(value), height, delay, onTargets, search, ring, ringRadius); }
		public Pillar radius(double base, double cap) { return new Pillar(anchor, Val.of(base, cap), height, delay, onTargets, search, ring, ringRadius); }
		public Pillar height(double value) { return new Pillar(anchor, radius, Val.of(value), delay, onTargets, search, ring, ringRadius); }
		public Pillar height(double base, double cap) { return new Pillar(anchor, radius, Val.of(base, cap), delay, onTargets, search, ring, ringRadius); }
		public Pillar delay(int ticks) { return new Pillar(anchor, radius, height, ticks, onTargets, search, ring, ringRadius); }
		public Pillar onTargets(double count) { return new Pillar(anchor, radius, height, delay, Val.of(count), search, ring, ringRadius); }
		public Pillar onTargets(double base, double cap) { return new Pillar(anchor, radius, height, delay, Val.of(base, cap), search, ring, ringRadius); }
		public Pillar search(double value) { return new Pillar(anchor, radius, height, delay, onTargets, Val.of(value), ring, ringRadius); }
		public Pillar search(double base, double cap) { return new Pillar(anchor, radius, height, delay, onTargets, Val.of(base, cap), ring, ringRadius); }
		public Pillar ring(int count, double radiusAround) { return new Pillar(anchor, radius, height, delay, onTargets, search, count, radiusAround); }
	}

	// ================================================================== RAIN

	/**
	 * Impacts spread over {@code over} ticks within {@code radius} of the anchor (aim within {@code range}); each
	 * targets an unhit hostile with probability {@code targeted}; {@code fall} ticks of client fall animation.
	 */
	record Rain(Anchor anchor, Val range, Val radius, Val impacts, int over, Val impactRadius, int fall, double targeted) implements Shape {
		public static final Rain DEFAULT = new Rain(Anchor.AIM, Val.of(24.0D), Val.of(12.0D), Val.of(12.0D), 40, Val.of(2.0D), 6, 0.6D);

		@Override
		public Kind kind() {
			return Kind.RAIN;
		}

		public Rain at(Anchor value) { return new Rain(value, range, radius, impacts, over, impactRadius, fall, targeted); }
		public Rain range(double value) { return new Rain(anchor, Val.of(value), radius, impacts, over, impactRadius, fall, targeted); }
		public Rain range(double base, double cap) { return new Rain(anchor, Val.of(base, cap), radius, impacts, over, impactRadius, fall, targeted); }
		public Rain radius(double value) { return new Rain(anchor, range, Val.of(value), impacts, over, impactRadius, fall, targeted); }
		public Rain radius(double base, double cap) { return new Rain(anchor, range, Val.of(base, cap), impacts, over, impactRadius, fall, targeted); }
		/** The radius of the cast's own field. */
		public Rain ofArea() { return new Rain(Anchor.AREA, range, Val.NONE, impacts, over, impactRadius, fall, targeted); }
		public Rain impacts(double value) { return new Rain(anchor, range, radius, Val.of(value), over, impactRadius, fall, targeted); }
		public Rain impacts(double base, double cap) { return new Rain(anchor, range, radius, Val.of(base, cap), over, impactRadius, fall, targeted); }
		public Rain impacts(Val value) { return new Rain(anchor, range, radius, value, over, impactRadius, fall, targeted); }
		public Rain over(int ticks) { return new Rain(anchor, range, radius, impacts, ticks, impactRadius, fall, targeted); }
		public Rain impactRadius(double value) { return new Rain(anchor, range, radius, impacts, over, Val.of(value), fall, targeted); }
		public Rain impactRadius(double base, double cap) { return new Rain(anchor, range, radius, impacts, over, Val.of(base, cap), fall, targeted); }
		public Rain fall(int ticks) { return new Rain(anchor, range, radius, impacts, over, impactRadius, ticks, targeted); }
		public Rain targeted(double chance) { return new Rain(anchor, range, radius, impacts, over, impactRadius, fall, chance); }
	}

	// ================================================================== CHAIN

	/** First hop = the locked target (or {@code from}), then the nearest unhit hostile within {@code jump}. */
	record Chain(Anchor from, Val range, Val jump, Val jumps, int delay, double falloff) implements Shape {
		public static final Chain DEFAULT = new Chain(Anchor.TARGET, Val.of(24.0D), Val.of(6.0D), Val.of(4.0D), 3, 0.85D);

		@Override
		public Kind kind() {
			return Kind.CHAIN;
		}

		public Chain from(Anchor value) { return new Chain(value, range, jump, jumps, delay, falloff); }
		public Chain range(double value) { return new Chain(from, Val.of(value), jump, jumps, delay, falloff); }
		public Chain range(double base, double cap) { return new Chain(from, Val.of(base, cap), jump, jumps, delay, falloff); }
		public Chain jump(double value) { return new Chain(from, range, Val.of(value), jumps, delay, falloff); }
		public Chain jump(double base, double cap) { return new Chain(from, range, Val.of(base, cap), jumps, delay, falloff); }
		public Chain jumps(double value) { return new Chain(from, range, jump, Val.of(value), delay, falloff); }
		public Chain jumps(double base, double cap) { return new Chain(from, range, jump, Val.of(base, cap), delay, falloff); }
		public Chain jumps(Val value) { return new Chain(from, range, jump, value, delay, falloff); }
		public Chain delay(int ticks) { return new Chain(from, range, jump, jumps, ticks, falloff); }
		public Chain falloff(double factor) { return new Chain(from, range, jump, jumps, delay, factor); }
	}

	// ================================================================== TARGET

	/** The locked hostile (or the strongest / weakest in range); {@code splash} radius around it at {@code splashFactor}. */
	record Target(Val range, Val splash, double splashFactor, Pick pick, boolean los) implements Shape {
		public enum Pick { LOCKED, STRONGEST, WEAKEST, HIT }

		public static final Target DEFAULT = new Target(Val.of(24.0D), Val.of(0.0D), 0.5D, Pick.LOCKED, true);

		@Override
		public Kind kind() {
			return Kind.TARGET;
		}

		public Target range(double value) { return new Target(Val.of(value), splash, splashFactor, pick, los); }
		public Target range(double base, double cap) { return new Target(Val.of(base, cap), splash, splashFactor, pick, los); }
		public Target splash(double value) { return new Target(range, Val.of(value), splashFactor, pick, los); }
		public Target splash(double base, double cap) { return new Target(range, Val.of(base, cap), splashFactor, pick, los); }
		public Target splashFactor(double factor) { return new Target(range, splash, factor, pick, los); }
		public Target pick(Pick value) { return new Target(range, splash, splashFactor, value, los); }
		/** No line of sight needed (a Divine Sense glow, the gourd calling the name). */
		public Target noLos() { return new Target(range, splash, splashFactor, pick, false); }
	}

	// ================================================================== ALL

	/**
	 * Every hostile within {@code radius} of the anchor (aim within {@code range} when anchored at AIM), by
	 * {@code order}, up to {@code max}; {@code needsLos} needs line of sight from the caster; {@code isPrecise} = may hit calm
	 * neutrals (22.3.5). Anchor AREA with radius NONE = the cast's own field or domain.
	 */
	record All(Anchor anchor, Val range, Val radius, Val max, Order order, boolean needsLos, boolean isPrecise) implements Shape {
		public enum Order { NEAREST, STRONGEST, WEAKEST, RANDOM }

		public static final All DEFAULT = new All(Anchor.CASTER, Val.of(0.0D), Val.of(16.0D), Val.of(12.0D), Order.NEAREST, false, false);

		@Override
		public Kind kind() {
			return Kind.ALL;
		}

		public All at(Anchor value) { return new All(value, range, radius, max, order, needsLos, isPrecise); }
		public All range(double value) { return new All(anchor, Val.of(value), radius, max, order, needsLos, isPrecise); }
		public All range(double base, double cap) { return new All(anchor, Val.of(base, cap), radius, max, order, needsLos, isPrecise); }
		public All radius(double value) { return new All(anchor, range, Val.of(value), max, order, needsLos, isPrecise); }
		public All radius(double base, double cap) { return new All(anchor, range, Val.of(base, cap), max, order, needsLos, isPrecise); }
		/** Every hostile inside the cast's own field or domain. */
		public All ofArea() { return new All(Anchor.AREA, range, Val.NONE, max, order, needsLos, isPrecise); }
		public All max(double value) { return new All(anchor, range, radius, Val.of(value), order, needsLos, isPrecise); }
		public All max(double base, double cap) { return new All(anchor, range, radius, Val.of(base, cap), order, needsLos, isPrecise); }
		public All max(Val value) { return new All(anchor, range, radius, value, order, needsLos, isPrecise); }
		public All order(Order value) { return new All(anchor, range, radius, max, value, needsLos, isPrecise); }
		public All los() { return new All(anchor, range, radius, max, order, true, isPrecise); }
		public All precise() { return new All(anchor, range, radius, max, order, needsLos, true); }
	}

	// ================================================================== RECALL

	/** The still-living targets hit earlier in this cast, within {@code range} (convergence strikes, shatters). */
	record Recall(int max, double range) implements Shape {
		public static final Recall DEFAULT = new Recall(32, 48.0D);

		@Override
		public Kind kind() {
			return Kind.RECALL;
		}

		public Recall max(int value) { return new Recall(value, range); }
	}

	// ================================================================== VOLLEY

	/**
	 * Simulated projectiles, never entities. SIM: one merged query per cast per tick, one clip per projectile per tick;
	 * SEEK: targets picked at launch, hits land at the computed arrival tick. {@code over}: launches spread over this many
	 * ticks (else {@code stagger} ticks apart); at most {@code perTarget} hits per target (0 = no limit) and
	 * {@code maxTargets} targets (0 = no limit); {@code perTargetGc} replaces {@code perTarget} from Golden Core (0 = the same).
	 * {@code pierce}: 0 = stops at the first target.
	 */
	record Volley(Val count, Val range, Val hitRadius, int pierce, Spread spread, double angle, int stagger, Mode mode,
			int perTarget, int over, Val maxTargets, Anchor from, int perTargetGc) implements Shape {
		/** {@code pierce(PIERCE_ALL)}: passes through every target. */
		public static final int PIERCE_ALL = -1;

		public enum Spread { FAN, RING, SKY, ORBIT }

		public enum Mode { SIM, SEEK }

		public static final Volley DEFAULT = new Volley(Val.of(1.0D), Val.of(32.0D), Val.of(1.0D), 0, Spread.FAN, 30.0D, 2,
				Mode.SIM, 0, 0, Val.of(0.0D), Anchor.CASTER, 0);

		@Override
		public Kind kind() {
			return Kind.VOLLEY;
		}

		public Volley count(double value) { return new Volley(Val.of(value), range, hitRadius, pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley count(double base, double cap) { return new Volley(Val.of(base, cap), range, hitRadius, pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley range(double value) { return new Volley(count, Val.of(value), hitRadius, pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley range(double base, double cap) { return new Volley(count, Val.of(base, cap), hitRadius, pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley hitRadius(double value) { return new Volley(count, range, Val.of(value), pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley hitRadius(double base, double cap) { return new Volley(count, range, Val.of(base, cap), pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley pierce(int targets) { return new Volley(count, range, hitRadius, targets, spread, angle, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley spread(Spread value, double degrees) { return new Volley(count, range, hitRadius, pierce, value, degrees, stagger, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley stagger(int ticks) { return new Volley(count, range, hitRadius, pierce, spread, angle, ticks, mode, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley seek() { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, Mode.SEEK, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley sim() { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, Mode.SIM, perTarget, over, maxTargets, from, perTargetGc); }
		public Volley perTarget(int hits) { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, mode, hits, over, maxTargets, from, perTargetGc); }
		public Volley over(int ticks) { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, mode, perTarget, ticks, maxTargets, from, perTargetGc); }
		public Volley maxTargets(double value) { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, mode, perTarget, over, Val.of(value), from, perTargetGc); }
		public Volley from(Anchor value) { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, mode, perTarget, over, maxTargets, value, perTargetGc); }
		/** At most {@code fe} hits per target below Golden Core and {@code gc} from Golden Core on ("≤ 4 per target at FE, ≤ 8 at GC"). */
		public Volley perTarget(int fe, int gc) { return new Volley(count, range, hitRadius, pierce, spread, angle, stagger, mode, fe, over, maxTargets, from, Math.max(0, gc)); }
	}

	// ================================================================== DOMAIN

	/**
	 * Follows the caster; one per caster (a new one replaces the old). Height ≤ 16 when r > 16 (0 = automatic).
	 * {@code ramp}: damage +ramp per pulse, at most {@code rampMax} pulses (RAMP, 22.3.4).
	 */
	record Domain(Val radius, double height, Val duration, int pulse, double ramp, int rampMax) implements Shape {
		public static final Domain DEFAULT = new Domain(Val.of(10.0D), 0.0D, Val.of(200.0D), 20, 0.0D, 0);

		@Override
		public Kind kind() {
			return Kind.DOMAIN;
		}

		public Domain radius(double value) { return new Domain(Val.of(value), height, duration, pulse, ramp, rampMax); }
		public Domain radius(double base, double cap) { return new Domain(Val.of(base, cap), height, duration, pulse, ramp, rampMax); }
		public Domain radius(Val value) { return new Domain(value, height, duration, pulse, ramp, rampMax); }
		public Domain height(double value) { return new Domain(radius, value, duration, pulse, ramp, rampMax); }
		public Domain duration(double ticks) { return new Domain(radius, height, Val.of(ticks), pulse, ramp, rampMax); }
		public Domain duration(double base, double cap) { return new Domain(radius, height, Val.of(base, cap), pulse, ramp, rampMax); }
		public Domain duration(Val value) { return new Domain(radius, height, value, pulse, ramp, rampMax); }
		/** Ticks between pulses (at least 10). */
		public Domain pulse(int ticks) { return new Domain(radius, height, duration, Math.max(10, ticks), ramp, rampMax); }
		public Domain ramp(double perPulse, int maxPulses) { return new Domain(radius, height, duration, pulse, perPulse, maxPulses); }
	}

	// ================================================================== FIELD

	/**
	 * A placed timed zone, at most 2 per caster (the oldest ends). {@code form}: circle, square (radius = half side),
	 * octagon or ring; {@code wallMode}: IN bounces non-bosses back inside, OUT keeps them out; {@code emitters}: visual
	 * emitters (and EMITTERS anchors). Anchor AIM (within {@code range}) or CASTER (cast spot).
	 */
	record Field(Anchor anchor, Val range, Val radius, double height, Val duration, int pulse, Form form, Walls wallMode,
			Val emitters, double ramp, int rampMax) implements Shape {
		public enum Form { CIRCLE, SQUARE, OCTAGON, RING }

		public enum Walls { NONE, IN, OUT }

		public static final Field DEFAULT = new Field(Anchor.AIM, Val.of(24.0D), Val.of(10.0D), 0.0D, Val.of(200.0D), 20,
				Form.CIRCLE, Walls.NONE, Val.of(0.0D), 0.0D, 0);

		@Override
		public Kind kind() {
			return Kind.FIELD;
		}

		public Field at(Anchor value) { return new Field(value, range, radius, height, duration, pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field range(double value) { return new Field(anchor, Val.of(value), radius, height, duration, pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field range(double base, double cap) { return new Field(anchor, Val.of(base, cap), radius, height, duration, pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field radius(double value) { return new Field(anchor, range, Val.of(value), height, duration, pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field radius(double base, double cap) { return new Field(anchor, range, Val.of(base, cap), height, duration, pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field height(double value) { return new Field(anchor, range, radius, value, duration, pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field duration(double ticks) { return new Field(anchor, range, radius, height, Val.of(ticks), pulse, form, wallMode, emitters, ramp, rampMax); }
		public Field duration(double base, double cap) { return new Field(anchor, range, radius, height, Val.of(base, cap), pulse, form, wallMode, emitters, ramp, rampMax); }
		/** Ticks between pulses (at least 10). */
		public Field pulse(int ticks) { return new Field(anchor, range, radius, height, duration, Math.max(10, ticks), form, wallMode, emitters, ramp, rampMax); }
		public Field form(Form value) { return new Field(anchor, range, radius, height, duration, pulse, value, wallMode, emitters, ramp, rampMax); }
		public Field square() { return form(Form.SQUARE); }
		public Field walls(Walls value) { return new Field(anchor, range, radius, height, duration, pulse, form, value, emitters, ramp, rampMax); }
		public Field walls() { return walls(Walls.IN); }
		public Field emitters(double count) { return new Field(anchor, range, radius, height, duration, pulse, form, wallMode, Val.of(count), ramp, rampMax); }
		public Field emitters(double base, double cap) { return new Field(anchor, range, radius, height, duration, pulse, form, wallMode, Val.of(base, cap), ramp, rampMax); }
		public Field ramp(double perPulse, int maxPulses) { return new Field(anchor, range, radius, height, duration, pulse, form, wallMode, emitters, perPulse, maxPulses); }
	}

	// ================================================================== AURA

	/** A small follow zone in its own slot (one per caster). */
	record Aura(Val radius, Val duration, int pulse) implements Shape {
		public static final Aura DEFAULT = new Aura(Val.of(3.0D), Val.of(200.0D), 20);

		@Override
		public Kind kind() {
			return Kind.AURA;
		}

		public Aura radius(double value) { return new Aura(Val.of(value), duration, pulse); }
		public Aura radius(double base, double cap) { return new Aura(Val.of(base, cap), duration, pulse); }
		public Aura duration(double ticks) { return new Aura(radius, Val.of(ticks), pulse); }
		public Aura duration(double base, double cap) { return new Aura(radius, Val.of(base, cap), pulse); }
		/** Ticks between pulses (at least 10). */
		public Aura pulse(int ticks) { return new Aura(radius, duration, Math.max(10, ticks)); }
	}

	// ================================================================== PATH

	/** The segment a DASH or BLINK just travelled. */
	record Path(Val halfWidth) implements Shape {
		public static final Path DEFAULT = new Path(Val.of(1.2D));

		@Override
		public Kind kind() {
			return Kind.PATH;
		}

		public Path halfWidth(double value) { return new Path(Val.of(value)); }
		public Path halfWidth(double base, double cap) { return new Path(Val.of(base, cap)); }
	}
}
