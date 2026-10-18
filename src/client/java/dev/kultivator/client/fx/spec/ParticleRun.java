package dev.kultivator.client.fx.spec;

import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The particle templates of DESIGN 22.4.2 (ARC to STARFIELD): each builds its seeded shape from the event geometry and
 * spreads its particles over its ticks through {@link #emit}. Colours: {@code color} for the main motes, {@code color2}
 * for sheaths, tips and sparks; the layer's particle override replaces the main dust. Shapes that need fixed points
 * (bolt paths, crack lines, stars) build them once in the constructor.
 */
final class ParticleRun extends LayerRun {

	private final FxLayer.Template template;
	private final ParticleOptions main;
	private final DustParticleOptions alt;
	private final DustColorTransitionOptions blend;
	/** Large motes of the main colour (clouds, mist, tears). */
	private final DustParticleOptions big;
	private static final DustParticleOptions STAR = new DustParticleOptions(0xFFFFFF, 1.6F);
	private final int color;
	private final int color2;
	/** Precomputed points (bolt paths, crack lines, stars, tears) as x, y, z triples. */
	private float[] points = new float[0];
	private final double baseY;
	private final BlockState ground;
	private final int spread;

	ParticleRun(FxEvent ev, FxLayer layer, int life, int index) {
		super(ev, layer, life, index);
		this.template = layer.template();
		boolean echo = ev.has(Payloads.TechFx.FLAG_ECHO);
		this.color = echo ? lighten(layer.color(), 0.35F) : layer.color() & 0xFFFFFF;
		this.color2 = echo ? lighten(layer.color2(), 0.35F) : layer.color2() & 0xFFFFFF;
		this.alt = new DustParticleOptions(color2, dustSize(0.9F));
		this.main = layer.particle() != null ? layer.particle() : new DustParticleOptions(color, dustSize(1.2F));
		this.blend = new DustColorTransitionOptions(color, color2, dustSize(1.3F));
		this.big = new DustParticleOptions(color, Math.min(4.0F, Math.max(2.5F, dustSize(3.0F))));
		Vec3 anchor = anchor();
		boolean needsGround = template == FxLayer.Template.SHOCK_RING || template == FxLayer.Template.CRACKS
				|| template == FxLayer.Template.RUNE_CIRCLE || template == FxLayer.Template.MIST;
		this.baseY = needsGround ? groundY(anchor) : anchor.y;
		this.ground = needsGround ? groundBlock(anchor) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		this.spread = switch (template) {
			case SHELL, IMPACT, SHARDS -> Math.min(this.life, 3);
			case BOLT -> Math.min(this.life, 3);
			case ARC -> Math.min(this.life, 6);
			case BEAM_CORE, CONE_SPRAY, CRACKS, CHAINS -> Math.min(this.life, 8);
			case SHOCK_RING, PETALS -> Math.min(this.life, 12);
			default -> this.life;
		};
		build(anchor);
	}

	private void build(Vec3 anchor) {
		switch (template) {
			case BOLT -> {
				Vec3 start = ev.hasTarget || layer.attach() == FxLayer.Attach.LINE ? anchor : anchor.add(0.0D, 18.0D + rnd.nextInt(6), 0.0D);
				Vec3 end = ev.hasTarget || layer.attach() == FxLayer.Attach.LINE ? lineEnd(anchor) : anchor;
				points = jagged(start, end, 0.07D);
			}
			case CRACKS -> {
				int lines = 6 + rnd.nextInt(5);
				float r = radius(4.0F);
				float[] all = new float[lines * 7 * 3];
				int index = 0;
				for (int i = 0; i < lines; i++) {
					double angle = Math.PI * 2.0D * i / lines + rnd.nextDouble() * 0.5D;
					double x = anchor.x;
					double z = anchor.z;
					for (int s = 0; s < 7; s++) {
						double step = r / 6.0D * (0.7D + rnd.nextDouble() * 0.6D);
						angle += (rnd.nextDouble() - 0.5D) * 0.7D;
						x += Math.cos(angle) * step;
						z += Math.sin(angle) * step;
						all[index++] = (float) x;
						all[index++] = (float) baseY;
						all[index++] = (float) z;
					}
				}
				points = all;
			}
			case STARFIELD -> {
				int stars = 24;
				float r = radius(16.0F);
				points = new float[stars * 3];
				for (int i = 0; i < stars; i++) {
					double elevation = Math.toRadians(20.0D + rnd.nextDouble() * 60.0D);
					double heading = rnd.nextDouble() * Math.PI * 2.0D;
					points[i * 3] = (float) (anchor.x + Math.cos(heading) * Math.cos(elevation) * r);
					points[i * 3 + 1] = (float) (anchor.y + Math.sin(elevation) * r);
					points[i * 3 + 2] = (float) (anchor.z + Math.sin(heading) * Math.cos(elevation) * r);
				}
			}
			case SKY_TEAR -> {
				float half = Math.min(80.0F, length(40.0F)) / 2.0F;
				Vec3 a = anchor.subtract(hfwd.scale(half));
				Vec3 b = anchor.add(hfwd.scale(half));
				points = jagged(a, b, 0.03D);
			}
			case SWORD_SHAPE -> {
				// Per sword: ring angle, ring distance, start share of the fall, facing.
				int swords = swordCount();
				points = new float[swords * 4];
				for (int i = 0; i < swords; i++) {
					points[i * 4] = (float) (rnd.nextDouble() * Math.PI * 2.0D);
					points[i * 4 + 1] = (float) Math.sqrt(rnd.nextDouble());
					points[i * 4 + 2] = (float) (rnd.nextDouble() * 0.4D);
					points[i * 4 + 3] = (float) (rnd.nextDouble() * Math.PI);
				}
			}
			default -> {
			}
		}
	}

	@Override
	boolean update(int age) {
		Vec3 a = anchor();
		switch (template) {
			case ARC -> arc(a, age);
			case CRESCENT_FLY -> crescent(age);
			case BEAM_CORE -> beam(age);
			case CONE_SPRAY -> cone(age);
			case SHELL -> shell(a);
			case SHOCK_RING -> shockRing(a, age);
			case DOME -> dome(a);
			case COLUMN -> column(a, age);
			case BOLT -> bolt(age);
			case BOLT_STORM -> boltStorm(a);
			case CLOUD_DISC -> cloudDisc(a, age);
			case VORTEX -> vortex(a, age);
			case RUNE_CIRCLE -> runeCircle(a, age);
			case PETALS -> petals(a, age);
			case PALM -> palm(a, age, false);
			case FIST -> palm(a, age, true);
			case SWORD_SHAPE -> swords(a, age);
			case WINGS -> wings(age);
			case BEAST -> beast(a, age);
			case TRAIL -> trail(a);
			case IMPACT -> impact(a, age);
			case CRACKS -> cracks(age);
			case MIST -> mist(a);
			case SHARDS -> shards(a, age);
			case CHAINS -> chains(a);
			case MOTES_TO -> motesTo(a);
			case HEAL_RISE -> healRise(a);
			case SHIELD_SHELL -> shieldShell();
			case AFTERIMAGE -> afterimage(a, age);
			case SKY_TEAR -> skyTear();
			case METEOR -> meteor(a, age);
			case STARFIELD -> starfield();
			default -> {
				return false;
			}
		}
		return true;
	}

	// ================================================================== the templates

	/** A crescent band sweeping across the strike arc. */
	private void arc(Vec3 center, int age) {
		int n = quota(spread);
		float r = radius(3.0F);
		double half = Math.toRadians(layer.width() > 0.0F ? Math.min(110.0F, layer.width()) : 60.0D);
		double from = -half + 2.0D * half * age / spread;
		double to = -half + 2.0D * half * (age + 1) / spread;
		for (int i = 0; i < n; i++) {
			double angle = from + (to - from) * rnd.nextDouble();
			double rr = r + (rnd.nextDouble() - 0.5D) * 0.7D * layer.size();
			Vec3 dir = yaw(hfwd, angle);
			Vec3 at = center.add(dir.scale(rr)).add(0.0D, (rnd.nextDouble() - 0.5D) * 0.6D, 0.0D);
			emit(i % 4 == 0 ? alt : blend, at, dir.x * 0.02D, 0.0D, dir.z * 0.02D);
		}
		if (n > 0 && age % 2 == 0) {
			Vec3 dir = yaw(hfwd, (from + to) / 2.0D);
			emit(ParticleTypes.SWEEP_ATTACK, center.add(dir.scale(r)), 0.0D, 0.0D, 0.0D);
		}
	}

	/** A crescent wall flying along the direction. */
	private void crescent(int age) {
		int n = quota(life);
		float distance = length(16.0F);
		float halfWidth = width(Math.max(1.5F, radius(2.5F) * 0.6F));
		Vec3 start = anchor();
		Vec3 center = start.add(fwd.scale(distance * Math.min(1.0D, (age + 1) / (double) life)));
		boolean vertical = layer.variant() == FxLayer.Variant.VERTICAL;
		Vec3 side = vertical ? up : right;
		for (int i = 0; i < n; i++) {
			double s = rnd.nextDouble() * 2.0D - 1.0D;
			Vec3 at = center.add(side.scale(s * halfWidth)).add(fwd.scale(-s * s * halfWidth * 0.4D))
					.add((vertical ? right : up).scale((rnd.nextDouble() - 0.5D) * 0.3D));
			emit(Math.abs(s) > 0.8D ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A dense core along the line with a turning spiral sheath. */
	private void beam(int age) {
		int n = quota(spread);
		Vec3 start = anchor();
		Vec3 end = lineEnd(start);
		Vec3 delta = end.subtract(start);
		double len = Math.max(0.5D, delta.length());
		Vec3 axis = delta.scale(1.0D / len);
		Vec3 side = axis.cross(UP);
		side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		Vec3 lift = side.cross(axis).normalize();
		double sheath = Math.max(0.25D, width(0.8F) / 2.0D);
		for (int i = 0; i < n; i++) {
			double t = rnd.nextDouble();
			Vec3 at = start.add(delta.scale(t));
			if (i % 3 == 2) {
				double angle = t * len * 1.6D + age * 0.7D;
				at = at.add(side.scale(Math.cos(angle) * sheath)).add(lift.scale(Math.sin(angle) * sheath));
				emit(alt, at, 0.0D, 0.0D, 0.0D);
			} else {
				emit(main, at.x + (rnd.nextDouble() - 0.5D) * 0.12D, at.y + (rnd.nextDouble() - 0.5D) * 0.12D,
						at.z + (rnd.nextDouble() - 0.5D) * 0.12D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** Particles streaming through a cone. */
	private void cone(int age) {
		int n = quota(spread);
		Vec3 apex = anchor();
		float len = length(radius(7.0F));
		double half = Math.toRadians(layer.width() > 0.0F ? Math.min(60.0F, layer.width()) : 30.0D);
		for (int i = 0; i < n; i++) {
			double spin = rnd.nextDouble() * Math.PI * 2.0D;
			double tilt = half * Math.sqrt(rnd.nextDouble());
			Vec3 dir = fwd.scale(Math.cos(tilt)).add(right.scale(Math.sin(tilt) * Math.cos(spin))).add(up.scale(Math.sin(tilt) * Math.sin(spin)));
			double d = len * Math.sqrt(rnd.nextDouble());
			Vec3 at = apex.add(dir.scale(d));
			emit(i % 5 == 0 ? alt : main, at, dir.x * 0.12D, dir.y * 0.12D, dir.z * 0.12D);
		}
	}

	/** A sphere-shell burst (or an implosion for INWARD). */
	private void shell(Vec3 center) {
		int n = quota(spread);
		float r = radius(3.0F);
		boolean inward = layer.variant() == FxLayer.Variant.INWARD;
		for (int i = 0; i < n; i++) {
			Vec3 dir = sphere();
			Vec3 at = center.add(dir.scale(inward ? r : r * 0.85D));
			double speed = (inward ? -0.12D : 0.05D) * Math.max(1.0D, r / 4.0D);
			emit(i % 4 == 0 ? alt : main, at, dir.x * speed, dir.y * speed, dir.z * speed);
		}
	}

	/** An expanding ground ring with dust of the ground block. */
	private void shockRing(Vec3 center, int age) {
		int n = quota(spread);
		float r = radius(6.0F) * Math.min(1.0F, (age + 1) / (float) spread);
		BlockParticleOption crumbs = ground.isAir() ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double x = center.x + Math.cos(angle) * r;
			double z = center.z + Math.sin(angle) * r;
			if (crumbs != null && i % 3 == 0) {
				emit(crumbs, x, baseY + 0.1D, z, Math.cos(angle) * 0.1D, 0.25D, Math.sin(angle) * 0.1D);
			} else {
				emit(i % 2 == 0 ? main : alt, x, baseY + 0.12D + rnd.nextDouble() * 0.3D, z, Math.cos(angle) * 0.05D, 0.02D, Math.sin(angle) * 0.05D);
			}
		}
	}

	/** Motes on a hemisphere, with a base ring. */
	private void dome(Vec3 center) {
		int n = quota(life);
		float r = radius(10.0F);
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			if (i % 5 == 0) {
				emit(alt, center.x + Math.cos(angle) * r, center.y + 0.1D, center.z + Math.sin(angle) * r, 0.0D, 0.0D, 0.0D);
				continue;
			}
			double u = rnd.nextDouble();
			double flat = Math.sqrt(1.0D - u * u) * r;
			emit(main, center.x + Math.cos(angle) * flat, center.y + u * r, center.z + Math.sin(angle) * flat, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A rising pillar with a splash at its foot. */
	private void column(Vec3 base, int age) {
		int n = quota(life);
		float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : Math.max(0.6F, Math.min(3.0F, ev.radius > 0.0F ? ev.radius * 0.3F : 1.0F));
		float height = layer.length() > 0.0F ? layer.length() * ev.sizeF : 20.0F * ev.sizeF;
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			double y = base.y + rnd.nextDouble() * height;
			if (age < 3 && i % 3 == 0) {
				emit(alt, base.x + Math.cos(angle) * r * 2.0D, base.y + 0.1D, base.z + Math.sin(angle) * r * 2.0D, Math.cos(angle) * 0.15D, 0.1D,
						Math.sin(angle) * 0.15D);
			} else if (i % 12 == 0) {
				emit(ParticleTypes.END_ROD, base.x + Math.cos(angle) * rr, y, base.z + Math.sin(angle) * rr, 0.0D, 0.12D, 0.0D);
			} else {
				emit(main, base.x + Math.cos(angle) * rr, y, base.z + Math.sin(angle) * rr, 0.0D, 0.15D, 0.0D);
			}
		}
	}

	/** One jagged bolt (two flickers), with sparks and a flash where it lands. */
	private void bolt(int age) {
		int n = quota(spread);
		if (points.length < 6) {
			return;
		}
		int segments = points.length / 3 - 1;
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(segments);
			double t = rnd.nextDouble();
			double x = points[s * 3] + (points[s * 3 + 3] - points[s * 3]) * t;
			double y = points[s * 3 + 1] + (points[s * 3 + 4] - points[s * 3 + 1]) * t;
			double z = points[s * 3 + 2] + (points[s * 3 + 5] - points[s * 3 + 2]) * t;
			emit(i % 4 == 0 ? alt : main, x, y, z, 0.0D, 0.0D, 0.0D);
		}
		if (age == 0) {
			int last = points.length - 3;
			emit(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color2), points[last], points[last + 1] + 0.5D, points[last + 2], 0.0D,
					0.0D, 0.0D);
			for (int i = 0; i < 5; i++) {
				emit(ParticleTypes.ELECTRIC_SPARK, points[last], points[last + 1] + 0.2D, points[last + 2], rnd.nextGaussian() * 0.25D,
						rnd.nextDouble() * 0.25D, rnd.nextGaussian() * 0.25D);
			}
		}
	}

	/** Many bolts from a cloud disc to random ground points. */
	private void boltStorm(Vec3 center) {
		float r = radius(12.0F);
		double cloud = center.y + (layer.offsetY() > 0.0F ? 0.0D : 18.0D);
		int bolts = quota(life) / 12;
		for (int b = 0; b < bolts; b++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			Vec3 end = new Vec3(center.x + Math.cos(angle) * rr, ev.pos.y, center.z + Math.sin(angle) * rr);
			Vec3 start = new Vec3(end.x + (rnd.nextDouble() - 0.5D) * r * 0.6D, cloud, end.z + (rnd.nextDouble() - 0.5D) * r * 0.6D);
			float[] path = jagged(start, end, 0.06D);
			for (int i = 3; i < path.length; i += 3) {
				for (int k = 0; k < 3; k++) {
					double t = k / 3.0D;
					emit(k == 0 ? alt : main, path[i - 3] + (path[i] - path[i - 3]) * t, path[i - 2] + (path[i + 1] - path[i - 2]) * t,
							path[i - 1] + (path[i + 2] - path[i - 1]) * t, 0.0D, 0.0D, 0.0D);
				}
			}
			emit(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.2D, end.z, 0.0D, 0.2D, 0.0D);
		}
	}

	/** A dark disc turning overhead. */
	private void cloudDisc(Vec3 center, int age) {
		int n = quota(life);
		float r = Math.max(radius(10.0F), 6.0F);
		double y = center.y + (layer.offsetY() > 0.0F || layer.attach() == FxLayer.Attach.SKY ? 0.0D : 18.0D);
		DustParticleOptions cloud = big;
		for (int i = 0; i < n; i++) {
			double rr = r * Math.sqrt(rnd.nextDouble());
			double angle = rnd.nextDouble() * Math.PI * 2.0D + age * 0.04D;
			double x = center.x + Math.cos(angle) * rr;
			double z = center.z + Math.sin(angle) * rr;
			if (i % 10 == 0) {
				emit(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
			} else {
				emit(i % 4 == 0 ? alt : cloud, x, y + (rnd.nextDouble() - 0.5D) * 1.2D, z, -Math.sin(angle) * 0.05D, 0.0D, Math.cos(angle) * 0.05D);
			}
		}
	}

	/** Spiral arms turning in (INWARD) or out. */
	private void vortex(Vec3 center, int age) {
		int n = quota(life);
		float r = radius(5.0F);
		boolean inward = layer.variant() == FxLayer.Variant.INWARD;
		for (int i = 0; i < n; i++) {
			double t = rnd.nextDouble();
			double phase = (age * 0.05D + t) % 1.0D;
			double rr = r * (inward ? 1.0D - phase : phase);
			double angle = (i % 3) * Math.PI * 2.0D / 3.0D + phase * Math.PI * 4.0D * (inward ? 1.0D : -1.0D);
			double y = center.y + t * r * 0.4D;
			emit(i % 2 == 0 ? main : alt, center.x + Math.cos(angle) * rr, y, center.z + Math.sin(angle) * rr, 0.0D, 0.01D, 0.0D);
		}
	}

	/** Two turning rings with eight rune marks between them. */
	private void runeCircle(Vec3 center, int age) {
		int n = quota(life);
		float r = radius(4.0F);
		double turn = age * 0.04D;
		boolean mirrored = layer.variant() == FxLayer.Variant.MIRRORED;
		double y = baseY + 0.08D;
		for (int i = 0; i < n; i++) {
			int kind = i % 4;
			if (kind < 2) {
				double angle = rnd.nextDouble() * Math.PI * 2.0D;
				emit(main, center.x + Math.cos(angle) * r, y, center.z + Math.sin(angle) * r, 0.0D, 0.0D, 0.0D);
			} else if (kind == 2) {
				double angle = rnd.nextDouble() * Math.PI * 2.0D;
				emit(alt, center.x + Math.cos(angle) * r * 0.78D, y, center.z + Math.sin(angle) * r * 0.78D, 0.0D, 0.0D, 0.0D);
			} else {
				int mark = rnd.nextInt(8);
				double angle = Math.PI * 2.0D * mark / 8.0D + (mirrored ? -turn : turn);
				double mx = center.x + Math.cos(angle) * r * 0.89D;
				double mz = center.z + Math.sin(angle) * r * 0.89D;
				double s = (rnd.nextDouble() - 0.5D) * r * 0.16D;
				boolean along = rnd.nextBoolean();
				emit(alt, mx + (along ? s * Math.cos(angle) : -s * Math.sin(angle)), y + 0.02D, mz + (along ? s * Math.sin(angle) : s * Math.cos(angle)),
						0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** A lotus or blossom opening. */
	private void petals(Vec3 center, int age) {
		int n = quota(spread);
		int count = layer.n() > 0 ? Math.min(16, layer.n()) : 8;
		float length = radius(1.6F);
		double open = Math.min(1.0D, (age + 1) / 10.0D);
		double tilt = Math.toRadians(80.0D - 45.0D * open);
		for (int i = 0; i < n; i++) {
			int petal = rnd.nextInt(count);
			double heading = Math.PI * 2.0D * petal / count;
			Vec3 radial = new Vec3(Math.cos(heading), 0.0D, Math.sin(heading));
			Vec3 tangent = new Vec3(-Math.sin(heading), 0.0D, Math.cos(heading));
			double s = rnd.nextDouble();
			double w = length * 0.35D * Math.sin(Math.PI * s) * (rnd.nextBoolean() ? 1.0D : -1.0D) * rnd.nextDouble();
			Vec3 at = center.add(radial.scale(s * length * Math.cos(tilt))).add(0.0D, s * length * Math.sin(tilt), 0.0D).add(tangent.scale(w));
			emit(s > 0.7D ? alt : blend, at, 0.0D, 0.01D, 0.0D);
		}
	}

	/** A palm or fist of points: sliding along a line, falling from the sky (FALLING) or standing at the anchor. */
	private void palm(Vec3 anchor, int age, boolean fist) {
		int n = quota(life);
		float size = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : ev.radius > 0.0F ? Math.min(30.0F, ev.radius) : 2.0F * layer.size() * ev.sizeF;
		size = Math.min(30.0F, size);
		double progress = Math.min(1.0D, (age + 1) / (double) Math.max(1, life * 2 / 3));
		boolean falling = layer.variant() == FxLayer.Variant.FALLING || layer.motion() == FxLayer.Motion.FALL;
		Vec3 center;
		Vec3 axisX;
		Vec3 axisY;
		if (falling) {
			double height = layer.motionA() > 0.0F ? layer.motionA() : 12.0D;
			Vec3 ground = ev.hasTarget ? ev.target : anchor;
			center = ground.add(0.0D, height * (1.0D - progress * progress) + 0.2D, 0.0D);
			axisX = right;
			axisY = hfwd;
		} else if (layer.attach() == FxLayer.Attach.LINE && ev.hasTarget) {
			center = anchor.add(lineEnd(anchor).subtract(anchor).scale(progress));
			axisX = right;
			axisY = up;
		} else {
			center = anchor;
			axisX = right;
			axisY = up;
		}
		float[] outline = fist ? PointClouds.fist() : PointClouds.palm();
		int pointCount = outline.length / 2;
		for (int i = 0; i < n; i++) {
			double x;
			double y;
			boolean edge = fist || i % 5 < 3;
			if (edge) {
				int p = rnd.nextInt(pointCount);
				x = outline[p * 2];
				y = outline[p * 2 + 1];
			} else {
				x = -0.85D + rnd.nextDouble() * 1.35D;
				y = -0.6D + rnd.nextDouble() * 1.55D;
				if (!PointClouds.insidePalm(x, y)) {
					continue;
				}
			}
			Vec3 at = center.add(axisX.scale((x + 0.17D) * size)).add(axisY.scale((y - 0.17D) * size));
			emit(edge ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Swords falling around the anchor, flying along the direction or fanned out from the caster. */
	private void swords(Vec3 anchor, int age) {
		int n = quota(life);
		int swords = Math.max(1, Math.min(swordCount(), points.length / 4));
		float length = 2.5F * layer.size() * ev.sizeF;
		float r = radius(4.0F);
		float[] shape = PointClouds.sword();
		int pointCount = shape.length / 2;
		FxLayer.Variant variant = layer.variant();
		if (variant == FxLayer.Variant.NONE) {
			variant = ev.hasTarget ? FxLayer.Variant.FLYING : FxLayer.Variant.FALLING;
		}
		for (int i = 0; i < n; i++) {
			int sword = rnd.nextInt(swords);
			int p = rnd.nextInt(pointCount);
			double x = shape[p * 2];
			double y = shape[p * 2 + 1];
			Vec3 at;
			switch (variant) {
				case FALLING -> {
					double angle = points[sword * 4];
					double rr = r * points[sword * 4 + 1];
					double start = points[sword * 4 + 2];
					double t = Math.max(0.0D, Math.min(1.0D, ((age + 1) / (double) life - start) / 0.5D));
					Vec3 base = (ev.hasTarget ? ev.target : anchor).add(Math.cos(angle) * rr, 12.0D * (1.0D - t * t), Math.sin(angle) * rr);
					Vec3 side = yaw(new Vec3(1.0D, 0.0D, 0.0D), points[sword * 4 + 3]);
					at = base.add(side.scale(x * length)).add(0.0D, -(y + 0.6D) * length, 0.0D);
				}
				case FANNED -> {
					double spreadAngle = Math.toRadians(-40.0D + 80.0D * (swords == 1 ? 0.5D : sword / (double) (swords - 1)));
					Vec3 dir = yaw(hfwd, spreadAngle);
					Vec3 side = dir.cross(UP).normalize();
					double out = 1.0D + length * 0.5D + Math.min(1.0D, (age + 1) / 8.0D) * 1.5D;
					at = anchor.add(dir.scale(out + (y + 0.6D) * length)).add(side.scale(x * length)).add(0.0D, 1.2D, 0.0D);
				}
				default -> {
					double offset = (sword - (swords - 1) / 2.0D) * 0.8D;
					double travel = length(16.0F) * Math.min(1.0D, (age + 1) / (double) life);
					Vec3 base = anchor.add(right.scale(offset)).add(fwd.scale(travel));
					at = base.add(fwd.scale((y + 0.6D) * length)).add(right.scale(x * length * 0.6D)).add(up.scale(x * length * 0.4D));
				}
			}
			emit(y > 0.8D || y < -0.25D ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Two wings behind the caster, flapping slowly. */
	private void wings(int age) {
		int n = quota(life);
		Entity caster = ev.caster();
		Vec3 base = ev.casterPos();
		double yawRad = caster == null ? Math.atan2(-hfwd.x, hfwd.z) : Math.toRadians(caster.getYRot());
		Vec3 back = new Vec3(Math.sin(yawRad), 0.0D, -Math.cos(yawRad));
		Vec3 side = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
		Vec3 shoulder = base.add(0.0D, 1.35D + layer.offsetY(), 0.0D).add(back.scale(0.35D));
		float span = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.2F * layer.size() * ev.sizeF;
		double flap = Math.sin(age * 0.3D) * 0.35D;
		float[] wing = PointClouds.wing();
		int pointCount = wing.length / 2;
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(pointCount);
			double x = wing[p * 2];
			double y = wing[p * 2 + 1];
			double mirror = i % 2 == 0 ? 1.0D : -1.0D;
			Vec3 at = shoulder.add(side.scale(mirror * x * span)).add(0.0D, y * span, 0.0D).add(back.scale(x * span * (0.3D + flap)));
			emit(x > 0.8D ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A point-cloud beast facing the direction, charging to the target when there is one. */
	private void beast(Vec3 anchor, int age) {
		int n = quota(life);
		FxLayer.BeastKind kind = layer.beast() == null ? FxLayer.BeastKind.DRAGON : layer.beast();
		float size = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 3.0F * layer.size() * ev.sizeF;
		Vec3 center = ev.hasTarget ? anchor.add(lineEnd(anchor).subtract(anchor).scale(Math.min(1.0D, (age + 1) / (double) life)))
				: anchor.add(0.0D, size * 0.6D, 0.0D);
		float[] shape = PointClouds.beast(kind);
		int pointCount = shape.length / 2;
		Vec3 side = hfwd.cross(UP).normalize();
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(pointCount);
			double x = shape[p * 2];
			double y = shape[p * 2 + 1];
			Vec3 at = center.add(hfwd.scale(x * size)).add(0.0D, y * size, 0.0D).add(side.scale((rnd.nextDouble() - 0.5D) * size * 0.12D));
			emit(i % 4 == 0 ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Trail particles running from the start to the end of the line. */
	private void trail(Vec3 start) {
		int n = quota(life);
		Vec3 end = lineEnd(start);
		Vec3 delta = end.subtract(start);
		for (int i = 0; i < n; i++) {
			double t = rnd.nextDouble();
			Vec3 at = start.add(delta.scale(t * 0.3D)).add((rnd.nextDouble() - 0.5D) * 0.3D, (rnd.nextDouble() - 0.5D) * 0.3D,
					(rnd.nextDouble() - 0.5D) * 0.3D);
			if (i % 3 == 0) {
				emit(main, start.add(delta.scale(t)), 0.0D, 0.0D, 0.0D);
			} else {
				emit(new TrailParticleOption(end, i % 2 == 0 ? color : color2, 10 + rnd.nextInt(10)), at, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** A flash and a burst where something lands. */
	private void impact(Vec3 center, int age) {
		int n = quota(spread);
		float r = radius(1.5F);
		boolean boss = ev.has(Payloads.TechFx.FLAG_BOSS_HIT);
		if (age == 0) {
			emit(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), center.x, center.y + 0.3D, center.z, 0.0D, 0.0D, 0.0D);
			if (r >= 3.0F || boss) {
				emit(ParticleTypes.EXPLOSION, center.x, center.y + 0.5D, center.z, 0.0D, 0.0D, 0.0D);
			}
		}
		for (int i = 0; i < n; i++) {
			Vec3 dir = sphere();
			double speed = 0.08D + r * 0.03D;
			if (i % 6 == 0) {
				emit(i % 12 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, center.add(dir.scale(0.3D)), dir.x * 0.4D, dir.y * 0.4D,
						dir.z * 0.4D);
			} else {
				emit(i % 3 == 0 ? alt : main, center.add(dir.scale(r * 0.3D)), dir.x * speed, Math.abs(dir.y) * speed, dir.z * speed);
			}
		}
	}

	/** Radiating ground cracks (dark motes, crumbs of the ground, and BLOCK_CRUMBLE in Mode Kehancuran). */
	private void cracks(int age) {
		int n = quota(spread);
		if (points.length < 3) {
			return;
		}
		boolean kehancuran = ev.has(Payloads.TechFx.FLAG_KEHANCURAN);
		BlockParticleOption crumbs = ground.isAir() ? null : new BlockParticleOption(kehancuran ? ParticleTypes.BLOCK_CRUMBLE : ParticleTypes.BLOCK, ground);
		// a furrow (cracks along a line) also sheds FALLING_DUST of the ground block it runs through (read here, client-side)
		BlockParticleOption dust = ground.isAir() || layer.attach() != FxLayer.Attach.LINE ? null : new BlockParticleOption(ParticleTypes.FALLING_DUST, ground);
		int segments = points.length / 3;
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(segments);
			double x = points[s * 3] + (rnd.nextDouble() - 0.5D) * 0.3D;
			double z = points[s * 3 + 2] + (rnd.nextDouble() - 0.5D) * 0.3D;
			if (crumbs != null && i % 3 == 0) {
				emit(crumbs, x, baseY + 0.1D, z, 0.0D, 0.15D, 0.0D);
			} else if (dust != null && i % 3 == 1) {
				emit(dust, x, baseY + 0.3D + rnd.nextDouble() * 0.4D, z, 0.0D, 0.0D, 0.0D);
			} else {
				emit(main, x, baseY + 0.06D, z, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** Low mist over the area. */
	private void mist(Vec3 center) {
		int n = quota(life);
		float r = radius(6.0F);
		DustParticleOptions fog = big;
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			double x = center.x + Math.cos(angle) * rr;
			double z = center.z + Math.sin(angle) * rr;
			if (i % 10 == 0) {
				emit(ParticleTypes.WHITE_ASH, x, baseY + rnd.nextDouble() * 2.0D, z, 0.0D, 0.0D, 0.0D);
			} else {
				emit(i % 4 == 0 ? alt : fog, x, baseY + rnd.nextDouble() * 1.4D, z, 0.01D, 0.0D, 0.01D);
			}
		}
	}

	/** Shards flying out (or in, for INWARD). */
	private void shards(Vec3 center, int age) {
		int n = quota(spread);
		float r = radius(3.0F);
		boolean inward = layer.variant() == FxLayer.Variant.INWARD;
		for (int i = 0; i < n; i++) {
			Vec3 dir = sphere();
			Vec3 at = center.add(dir.scale(inward ? r : 0.4D));
			double speed = (inward ? -0.18D : 0.22D) * Math.max(1.0D, r / 3.0D);
			ParticleOptions particle = i % 8 == 0 ? ParticleTypes.END_ROD : i % 3 == 0 ? alt : main;
			emit(particle, at, dir.x * speed, dir.y * speed, dir.z * speed);
		}
	}

	/** Chain links along the line. */
	private void chains(Vec3 start) {
		int n = quota(spread);
		Vec3 end = lineEnd(start);
		Vec3 delta = end.subtract(start);
		double len = Math.max(0.5D, delta.length());
		Vec3 axis = delta.scale(1.0D / len);
		Vec3 side = axis.cross(UP);
		side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		Vec3 lift = side.cross(axis).normalize();
		int links = Math.max(1, (int) (len / 0.5D));
		for (int i = 0; i < n; i++) {
			int link = rnd.nextInt(links);
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			Vec3 ring = link % 2 == 0 ? side : lift;
			Vec3 at = start.add(axis.scale((link + 0.5D) * len / links + Math.cos(angle) * 0.25D)).add(ring.scale(Math.sin(angle) * 0.12D));
			emit(link % 2 == 0 ? main : alt, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Motes drawn from around the anchor to the caster (or the second point). */
	private void motesTo(Vec3 center) {
		int n = quota(life);
		float r = radius(5.0F);
		Vec3 to = ev.hasTarget && layer.attach() != FxLayer.Attach.TARGET ? ev.target : ev.casterPos().add(0.0D, 1.0D, 0.0D);
		for (int i = 0; i < n; i++) {
			Vec3 from = center.add(sphere().scale(r * (0.4D + rnd.nextDouble() * 0.6D)));
			emit(new TrailParticleOption(to, i % 3 == 0 ? color2 : color, 12 + rnd.nextInt(10)), from, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Healing motes rising in a column. */
	private void healRise(Vec3 center) {
		int n = quota(life);
		float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 1.2F;
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			emit(i % 3 == 0 ? alt : main, center.x + Math.cos(angle) * rr, center.y + rnd.nextDouble() * 2.5D, center.z + Math.sin(angle) * rr,
					0.0D, 0.06D, 0.0D);
		}
	}

	/** A shell of motes around the caster. */
	private void shieldShell() {
		int n = quota(life);
		Vec3 center = ev.casterPos().add(0.0D, 1.0D + layer.offsetY(), 0.0D);
		float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 1.4F * layer.size();
		for (int i = 0; i < n; i++) {
			int lat = rnd.nextInt(7) - 3;
			int lon = rnd.nextInt(12);
			double phi = lat * Math.PI / 8.0D;
			double theta = lon * Math.PI / 6.0D + (lat % 2 == 0 ? 0.0D : Math.PI / 12.0D);
			Vec3 at = center.add(Math.cos(phi) * Math.cos(theta) * r, Math.sin(phi) * r * 1.2D, Math.cos(phi) * Math.sin(theta) * r);
			emit(i % 3 == 0 ? alt : main, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A fading figure of the caster where it stood. */
	private void afterimage(Vec3 center, int age) {
		int n = quota(life);
		float[] figure = PointClouds.humanoid();
		int pointCount = figure.length / 2;
		Vec3 side = hfwd.cross(UP).normalize();
		double height = 0.95D * layer.size();
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(pointCount);
			Vec3 at = center.add(side.scale(figure[p * 2] * height)).add(0.0D, (figure[p * 2 + 1] + 1.0D) * height, 0.0D);
			emit(i % 3 == 0 ? alt : main, at, 0.0D, 0.004D, 0.0D);
		}
	}

	/** A glowing tear high in the sky with light falling from it. */
	private void skyTear() {
		int n = quota(life);
		if (points.length < 6) {
			return;
		}
		int segments = points.length / 3 - 1;
		DustParticleOptions bright = big;
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(segments);
			double t = rnd.nextDouble();
			double x = points[s * 3] + (points[s * 3 + 3] - points[s * 3]) * t;
			double y = points[s * 3 + 1] + (points[s * 3 + 4] - points[s * 3 + 1]) * t;
			double z = points[s * 3 + 2] + (points[s * 3 + 5] - points[s * 3 + 2]) * t;
			if (i % 5 == 4) {
				emit(i % 10 == 9 ? ParticleTypes.END_ROD : alt, x, y - rnd.nextDouble() * 6.0D, z, 0.0D, -0.25D, 0.0D);
			} else {
				emit(i % 2 == 0 ? bright : main, x, y, z, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** A flaming sphere falling from high up to the target, bursting where it lands. */
	private void meteor(Vec3 anchor, int age) {
		int n = quota(life);
		Vec3 end = ev.hasTarget ? ev.target : anchor;
		Vec3 start = end.add(0.0D, 40.0D, 0.0D).subtract(hfwd.scale(20.0D));
		int fall = Math.max(4, life * 7 / 10);
		float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.0F * layer.size() * ev.sizeF;
		if (age < fall) {
			double p = (age + 1) / (double) fall;
			Vec3 head = start.add(end.subtract(start).scale(p * p));
			Vec3 back = start.subtract(end).normalize();
			for (int i = 0; i < n; i++) {
				if (i % 4 == 0) {
					emit(ParticleTypes.LARGE_SMOKE, head.add(back.scale(r + rnd.nextDouble() * r * 2.0D)), 0.0D, 0.0D, 0.0D);
				} else if (i % 4 == 1) {
					emit(ParticleTypes.FLAME, head.add(sphere().scale(r * rnd.nextDouble())), 0.0D, 0.0D, 0.0D);
				} else {
					emit(i % 2 == 0 ? main : alt, head.add(sphere().scale(r)), 0.0D, 0.0D, 0.0D);
				}
			}
		} else if (age == fall) {
			emit(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), end.x, end.y + 1.0D, end.z, 0.0D, 0.0D, 0.0D);
			emit(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y + 0.5D, end.z, 0.0D, 0.0D, 0.0D);
			for (int i = 0; i < Math.max(8, n * 4); i++) {
				Vec3 dir = sphere();
				emit(i % 2 == 0 ? ParticleTypes.FLAME : main, end.add(dir.scale(r)), dir.x * 0.3D, Math.abs(dir.y) * 0.3D, dir.z * 0.3D);
			}
		}
	}

	/** Twinkling stars on a dome with constellation lines. */
	private void starfield() {
		int n = quota(life);
		int stars = points.length / 3;
		if (stars < 2) {
			return;
		}
		DustParticleOptions star = STAR;
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(stars);
			if (i % 3 == 0) {
				int next = (s / 4) * 4 + (s % 4 + 1) % 4;
				if (next >= stars) {
					next = s;
				}
				double t = rnd.nextDouble();
				emit(alt, points[s * 3] + (points[next * 3] - points[s * 3]) * t, points[s * 3 + 1] + (points[next * 3 + 1] - points[s * 3 + 1]) * t,
						points[s * 3 + 2] + (points[next * 3 + 2] - points[s * 3 + 2]) * t, 0.0D, 0.0D, 0.0D);
			} else {
				emit(i % 11 == 0 ? ParticleTypes.END_ROD : i % 2 == 0 ? star : main, points[s * 3], points[s * 3 + 1], points[s * 3 + 2], 0.0D, 0.0D, 0.0D);
			}
		}
	}

	// ================================================================== helpers

	private int swordCount() {
		return layer.n() > 0 ? Math.min(24, layer.n()) : Math.max(1, Math.min(12, layer.count() / 25));
	}

	/** A jagged polyline from {@code a} to {@code b}, jitter as a share of its length. */
	private float[] jagged(Vec3 a, Vec3 b, double jitter) {
		Vec3 delta = b.subtract(a);
		double len = delta.length();
		int segments = (int) Math.max(4, Math.min(28, len / 2.0D));
		float[] path = new float[(segments + 1) * 3];
		for (int i = 0; i <= segments; i++) {
			double t = i / (double) segments;
			double off = i == 0 || i == segments ? 0.0D : len * jitter;
			path[i * 3] = (float) (a.x + delta.x * t + (rnd.nextDouble() - 0.5D) * 2.0D * off);
			path[i * 3 + 1] = (float) (a.y + delta.y * t + (rnd.nextDouble() - 0.5D) * off);
			path[i * 3 + 2] = (float) (a.z + delta.z * t + (rnd.nextDouble() - 0.5D) * 2.0D * off);
		}
		return path;
	}

	static int lighten(int rgb, float amount) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * amount);
		g = Math.round(g + (255 - g) * amount);
		b = Math.round(b + (255 - b) * amount);
		return (r << 16) | (g << 8) | b;
	}
}
