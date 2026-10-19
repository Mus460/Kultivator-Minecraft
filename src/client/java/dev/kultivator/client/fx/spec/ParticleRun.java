package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.BeamQuad;
import dev.kultivator.client.fx.quad.BoltQuad;
import dev.kultivator.client.fx.quad.CrescentQuad;
import dev.kultivator.client.fx.quad.GlowQuad;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.client.fx.quad.RingQuad;
import dev.kultivator.client.fx.quad.StreakQuad;
import dev.kultivator.data.Dao;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * The particle templates of DESIGN 22.4.2 (ARC to STARFIELD), on the emissive qi particles since the polish pass (S5):
 * each template builds its seeded shape from the event geometry and spreads its particles over its ticks through
 * {@link #emit}. Every effect uses a three-tone ramp: 20% near-white core motes, 60% body in the layer colour, 20% rim in
 * {@code color2} (or the derived dark hue); the Dao of the spec adds its accent (sword streaks, fire tongues, ice shards,
 * lightning crackle, body dust, life petals, shadow ink, space glints). The layer's particle override replaces the body
 * motes, except END_ROD / TOTEM, which become qi streaks with a 10% real accent (S13). Shapes that need fixed points
 * (bolt paths, crack lines, stars) build them once in the constructor.
 * <p>
 * Geometry (polish): BOLT, BOLT_STORM, BEAM_CORE, CRESCENT_FLY, ARC, TRAIL, SKY_TEAR and the larger rings and impacts
 * also hand a quad effect to {@link QuadFx} (bolts, beams, crescents, streaks, ground ring decals, glow flashes) and keep
 * fewer particles as decoration; when the quad cap refuses, the particles carry the whole shape (Minimal).
 */
final class ParticleRun extends LayerRun {

	private final FxLayer.Template template;
	private final int color;
	private final int color2;
	private final Dao dao;
	/** Body motes (or the layer's particle override). */
	private final ParticleOptions main;
	/** Near-white core motes (the 20% bright share). */
	private final ParticleOptions core;
	/** Rim motes in the second colour. */
	private final ParticleOptions alt;
	/** Motes ramping from the colour to the second colour. */
	private final ParticleOptions blend;
	/** Large soft puffs of the colour (clouds, mist, tears). */
	private final ParticleOptions big;
	/** The Dao accent (streak, tongue, shard, petal, ink, glint). */
	private final ParticleOptions accent;
	/** A streak of the colour (sword sparks, speed lines). */
	private final ParticleOptions spark;
	/** True when the override was END_ROD / TOTEM (10% of the real one as an accent). */
	private final boolean rodAccent;
	/** True for dark colours (ink, cracks): smoke instead of glow. */
	private final boolean dark;
	private final float mote;
	/** Precomputed points (bolt paths, crack lines, stars, tears) as x, y, z triples. */
	private float[] points = new float[0];
	private double baseY;
	private final boolean groundSnap;
	private final BlockState ground;
	private final int spread;
	/** True once the quad effect of this layer was accepted (fewer particles then). */
	private boolean quad;
	private float boltCarry;

	ParticleRun(FxEvent ev, FxLayer layer, int life, int index) {
		this(ev, layer, life, index, 1.0F);
	}

	ParticleRun(FxEvent ev, FxLayer layer, int life, int index, float tierScale) {
		super(ev, layer, life, index, tierScale);
		this.template = layer.template();
		boolean echo = ev.has(Payloads.TechFx.FLAG_ECHO);
		int c1 = layer.color() & 0xFFFFFF;
		int c2 = layer.color2() & 0xFFFFFF;
		if (ev.has(Payloads.TechFx.FLAG_TWO_DAO) && ev.aux != 0) {
			// a two-Dao art: the second Dao colour is the rim
			c2 = ev.aux & 0xFFFFFF;
		}
		this.color = echo ? lighten(c1, 0.35F) : c1;
		int second = echo ? lighten(c2, 0.35F) : c2;
		// FxLayer.of keeps color2 = color: derive the rim instead of a flat single tone (S5)
		this.color2 = second == color ? Qi.rim(color) : second;
		this.dao = ev.spec.dao();
		this.dark = Qi.luma(color) < 0.22F;
		this.mote = moteSize(radius(defaultRadius(template)), 1.0F);
		ParticleOptions override = layer.particle();
		boolean rod = override != null && (override.getType() == ParticleTypes.END_ROD || override.getType() == ParticleTypes.TOTEM_OF_UNDYING);
		this.rodAccent = rod;
		int life0 = particleLife(template);
		if (dark) {
			this.main = override != null && !rod ? override : Qi.smoke(color, mote * 1.2F, life0 + 10);
			this.core = Qi.smoke(Qi.lerp(color, 0x000000, 0.3F), mote, life0);
		} else {
			this.main = override != null && !rod ? override : rod ? Qi.spark(color, mote * 1.4F, life0)
					: Qi.mote(Qi.core(color), color, color2, mote, mote * 0.35F, life0, dev.kultivator.fx.QiParticleOptions.GLOW);
			this.core = Qi.mote(0xFFFFFF, Qi.core(color), color, mote * 0.6F, mote * 0.2F, Math.max(6, life0 - 6), dev.kultivator.fx.QiParticleOptions.GLOW);
		}
		this.alt = Qi.mote(Qi.core(color2), color2, Qi.rim(color2), mote * 0.85F, mote * 0.3F, life0, dev.kultivator.fx.QiParticleOptions.GLOW);
		this.blend = Qi.mote(Qi.core(color), color, color2, mote * 1.1F, mote * 0.4F, life0 + 4, dev.kultivator.fx.QiParticleOptions.GLOW);
		this.big = dark ? Qi.smoke(color, Math.min(2.5F, mote * 3.0F), 40) : Qi.mist(color, Math.min(2.5F, mote * 3.0F), 34);
		this.spark = Qi.spark(dark ? color2 : color, mote * 1.6F, 8);
		this.accent = accentOf(dao, dark ? color2 : color, mote);
		Vec3 anchor = anchor();
		boolean needsGround = template == FxLayer.Template.SHOCK_RING || template == FxLayer.Template.CRACKS
				|| template == FxLayer.Template.RUNE_CIRCLE || template == FxLayer.Template.MIST;
		// above(h) keeps its height: only a layer without an offset snaps to the ground (S14)
		this.groundSnap = needsGround && layer.offsetY() == 0.0F && layer.variant() != FxLayer.Variant.VERTICAL;
		this.baseY = groundSnap ? groundY(anchor) : anchor.y;
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

	/** Particle lifetimes per template (bursts are short and bright, lasting looks linger). */
	private static int particleLife(FxLayer.Template template) {
		return switch (template) {
			case IMPACT, SHARDS, BOLT, ARC -> 10;
			case SHELL, SHOCK_RING, CONE_SPRAY, CRESCENT_FLY, BEAM_CORE, CHAINS, TRAIL -> 14;
			case MIST, CLOUD_DISC, DOME, VORTEX, RUNE_CIRCLE, STARFIELD, SHIELD_SHELL -> 26;
			case HEAL_RISE, COLUMN, PETALS, MOTES_TO -> 22;
			default -> 16;
		};
	}

	private static float defaultRadius(FxLayer.Template template) {
		return switch (template) {
			case DOME, CLOUD_DISC -> 10.0F;
			case SHOCK_RING, MIST -> 6.0F;
			case VORTEX -> 5.0F;
			case RUNE_CIRCLE, CRACKS -> 4.0F;
			case SHELL, SHARDS, ARC, CRESCENT_FLY -> 3.0F;
			default -> 1.5F;
		};
	}

	/** The Dao accent particle (style guide section 4). */
	private static ParticleOptions accentOf(Dao dao, int color, float mote) {
		if (dao == null) {
			return Qi.glint(Qi.lerp(color, 0xFFF1B8, 0.5F), mote * 1.6F, 14);
		}
		return switch (dao) {
			case SWORD -> Qi.spark(Qi.lerp(color, 0xFFFFFF, 0.4F), mote * 1.8F, 9);
			case FIRE -> Qi.flame(color, mote * 1.5F, 16);
			case ICE -> Qi.shard(color, mote * 1.1F, 26);
			case LIGHTNING -> Qi.spark(Qi.lerp(color, 0xFFFFFF, 0.5F), mote * 1.4F, 5);
			case BODY -> Qi.smoke(Qi.lerp(color, 0x6E5A44, 0.6F), mote * 1.8F, 30);
			case LIFE -> Qi.petal(Qi.lerp(color, 0xFFD6E8, 0.35F), mote * 1.2F, 40);
			case SHADOW -> Qi.smoke(Qi.lerp(color, 0x101018, 0.55F), mote * 2.0F, 30);
			case SPACE -> Qi.glint(Qi.lerp(color, 0xE8E0FF, 0.6F), mote * 1.4F, 18);
		};
	}

	/** The 20 / 60 / 20 ramp by index: core, body, rim. */
	private ParticleOptions ramp(int i) {
		int k = i % 5;
		return k == 0 ? core : k == 4 ? alt : main;
	}

	/** True when this layer may hand geometry to {@link QuadFx} (not for faint others' casts). */
	private boolean quads() {
		return ev.viewScale > 0.3F && !EffectQuality.minimal();
	}

	private void build(Vec3 anchor) {
		switch (template) {
			case BOLT -> {
				Vec3 start = ev.hasTarget || attach() == FxLayer.Attach.LINE ? lineStart() : anchor.add(0.0D, 18.0D + rnd.nextInt(6), 0.0D);
				Vec3 end = ev.hasTarget || attach() == FxLayer.Attach.LINE ? lineEnd(anchor) : anchor;
				points = jagged(start, end, 0.07D);
				if (quads()) {
					float w = 0.22F * Math.min(2.0F, ev.sizeF) * Math.max(0.6F, sizeFactor());
					if (layer.variant() == FxLayer.Variant.FANNED) {
						int bolts = 3 + Math.floorMod(ev.seed, 3);
						quad = BoltQuad.fan(start, end.subtract(start).normalize(), (float) start.distanceTo(end), bolts, 50.0F, w, color, Qi.core(color),
								ev.seed, Math.max(6, life + 4), ev.casterId, startDelay()) > 0;
					} else {
						BoltQuad bolt = new BoltQuad(start, end, w, color, Qi.core(color), ev.seed, 3, Math.max(6, life + 4));
						if (ev.targetId >= 0 && ev.hasTarget) {
							bolt.follow(-1, 0.0F, ev.targetId, 1.0F);
						}
						quad = QuadFx.add(bolt.caster(ev.casterId).delay(startDelay()));
					}
				}
			}
			case CRACKS -> {
				float r = radius(4.0F);
				if (attach() == FxLayer.Attach.LINE && ev.hasTarget) {
					// a furrow: one jagged polyline from the position to the target (S14 CRACKS LINE)
					Vec3 a = new Vec3(anchor.x, baseY, anchor.z);
					Vec3 b = new Vec3(ev.target.x, baseY, ev.target.z);
					float[] path = jagged(a, b, 0.04D);
					points = path;
				} else {
					int lines = 6 + rnd.nextInt(5);
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
			}
			case STARFIELD -> {
				int stars = layer.n() > 0 ? Math.max(4, Math.min(96, layer.n())) : 24;
				float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : radius(16.0F);
				boolean cluster = layer.radius() > 0.0F && layer.radius() < 1.0F;
				points = new float[stars * 3];
				Vec3 center = cluster ? ev.casterPos().add(0.0D, 2.1D, 0.0D) : anchor;
				for (int i = 0; i < stars; i++) {
					double elevation = cluster ? Math.toRadians(-30.0D + rnd.nextDouble() * 120.0D) : Math.toRadians(20.0D + rnd.nextDouble() * 60.0D);
					double heading = rnd.nextDouble() * Math.PI * 2.0D;
					double rr = cluster ? Math.max(0.35D, r) : r;
					points[i * 3] = (float) (Math.cos(heading) * Math.cos(elevation) * rr + (cluster ? 0.0D : center.x));
					points[i * 3 + 1] = (float) (Math.sin(elevation) * rr + (cluster ? 0.0D : center.y));
					points[i * 3 + 2] = (float) (Math.sin(heading) * Math.cos(elevation) * rr + (cluster ? 0.0D : center.z));
				}
			}
			case SKY_TEAR -> {
				float half = Math.min(80.0F, length(40.0F)) / 2.0F;
				Vec3 a = anchor.subtract(hfwd.scale(half));
				Vec3 b = anchor.add(hfwd.scale(half));
				points = jagged(a, b, 0.03D);
				if (quads()) {
					quad = QuadFx.add(new StreakQuad(a, b, Math.max(1.0F, half / 10.0F), color, 0xFFFFFF, 6.0F, 0.0F, half / 12.0F, ev.seed, life)
							.caster(ev.casterId).delay(startDelay()));
				}
			}
			case SWORD_SHAPE -> {
				int swords = swordCount();
				points = new float[swords * 4];
				for (int i = 0; i < swords; i++) {
					points[i * 4] = (float) (rnd.nextDouble() * Math.PI * 2.0D);
					points[i * 4 + 1] = (float) Math.sqrt(rnd.nextDouble());
					points[i * 4 + 2] = (float) (rnd.nextDouble() * 0.4D);
					points[i * 4 + 3] = (float) (rnd.nextDouble() * Math.PI);
				}
			}
			case BEAM_CORE -> {
				if (quads()) {
					Vec3 start = lineStart();
					Vec3 end = lineEnd(anchor());
					float w = Math.max(0.12F, width(0.8F) * 0.45F) * Math.max(0.6F, tierScale);
					BeamQuad beam = new BeamQuad(start, end, w, color, Qi.core(color), Math.max(6, life), true);
					quad = QuadFx.add(beam.caster(ev.casterId).delay(startDelay()));
				}
			}
			case CRESCENT_FLY -> {
				if (quads()) {
					boolean vertical = layer.variant() == FxLayer.Variant.VERTICAL;
					float distance = length(16.0F);
					float r = Math.max(1.2F, width(Math.max(1.5F, radius(2.5F) * 0.6F)));
					int count = ev.count > 1 && ev.count <= 9 && layer.variant() != FxLayer.Variant.VERTICAL ? ev.count : 1;
					Vec3 start = anchor();
					quad = CrescentQuad.fan(start, fwd, vertical, count, count > 1 ? 40.0F : 0.0F, r, 1.05F, Math.max(0.25F, r * 0.22F), distance, color,
							Qi.core(color), Math.max(6, life), ev.casterId, startDelay()) > 0;
				}
			}
			case ARC -> {
				if (quads()) {
					float r = radius(3.0F);
					float half = (float) Math.toRadians(layer.width() > 0.0F ? Math.min(110.0F, layer.width()) : 60.0D);
					Vec3 pivot = anchor().subtract(hfwd.scale(r * 0.3D));
					quad = QuadFx.add(new CrescentQuad(pivot, hfwd, false, r, half, Math.max(0.2F, r * 0.18F), 0.6F, color, Qi.core(color), Math.max(6, spread + 4))
							.sweep(spread).caster(ev.casterId).delay(startDelay()));
				}
			}
			case TRAIL -> {
				if (quads() && ev.hasTarget) {
					Vec3 start = lineStart();
					quad = QuadFx.add(new StreakQuad(start, lineEnd(start), 0.35F * Math.min(2.0F, ev.sizeF), color, Qi.core(color), Math.max(2.0F, life * 0.35F),
							Math.max(2.0F, life * 0.25F), 0.0F, ev.seed, life).caster(ev.casterId).delay(startDelay()));
				}
			}
			case SHOCK_RING -> {
				if (quads()) {
					float r = radius(6.0F);
					int rings = layer.n() > 1 ? Math.min(5, layer.n()) : 1;
					for (int i = 0; i < rings; i++) {
						int stagger = rings == 1 ? 0 : i * Math.max(2, spread) / rings;
						RingQuad ring = new RingQuad(new Vec3(anchor.x, baseY, anchor.z), 0.4F, r, color, 0.85F, Math.max(10, spread + 10));
						if (attach() == FxLayer.Attach.CASTER) {
							ring.follow(ev.casterId, 0.05D);
						}
						quad |= QuadFx.add(ring.caster(ev.casterId).delay(stagger + Math.max(0, layer.delay())));
					}
				}
			}
			case IMPACT, SHELL -> {
				float r = radius(template == FxLayer.Template.SHELL ? 3.0F : 1.5F);
				if (quads() && (r >= 1.5F || ev.climax()) && layer.variant() != FxLayer.Variant.INWARD) {
					Vec3 at = anchor().add(0.0D, template == FxLayer.Template.IMPACT ? 0.3D : 0.0D, 0.0D);
					QuadFx.add(new GlowQuad(at, r * 0.5F, r * 1.4F, Qi.lerp(color, 0xFFFFFF, 0.3F), 0.9F, 0.0F, 6).caster(ev.casterId).delay(Math.max(0, layer.delay())));
				}
			}
			case DOME -> {
				float r = radius(10.0F);
				if (quads() && r >= 8.0F) {
					RingQuad ring = new RingQuad(new Vec3(anchor.x, groundY(anchor) , anchor.z), r, r, color2, 0.45F, life);
					if (attach() == FxLayer.Attach.CASTER) {
						ring.follow(ev.casterId, 0.05D);
					}
					QuadFx.add(ring.caster(ev.casterId).delay(Math.max(0, layer.delay())));
				}
			}
			case RUNE_CIRCLE -> {
				float r = radius(4.0F);
				if (quads() && EffectQuality.maximal()) {
					boolean vertical = layer.variant() == FxLayer.Variant.VERTICAL;
					Vec3 center = vertical ? anchor.add(0.0D, layer.offsetY() > 0.0F ? 0.0D : 1.4D, 0.0D).add(hfwd.scale(1.2D))
							: new Vec3(anchor.x, baseY + 0.05D, anchor.z);
					RingQuad ring = vertical ? new RingQuad(center, right, UP, r * 0.9F, r * 0.9F, color, 0.55F, spinRate(), life)
							: new RingQuad(center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), r * 0.9F, r * 0.9F, color, 0.55F, spinRate(), life);
					if (attach() == FxLayer.Attach.CASTER && !vertical) {
						ring.follow(ev.casterId, 0.06D);
					}
					QuadFx.add(ring.caster(ev.casterId).delay(Math.max(0, layer.delay())));
				}
			}
			default -> {
			}
		}
	}

	/** The layer's own delay (its quads start with it). */
	private int startDelay() {
		return Math.max(0, layer.delay());
	}

	/** Rune spin in radians per tick: {@code spin(°/t)} when authored, else a slow default (S14). */
	private float spinRate() {
		return layer.motion() == FxLayer.Motion.SPIN ? (float) Math.toRadians(layer.motionA()) : 0.04F;
	}

	@Override
	boolean update(int age) {
		if (attach() == FxLayer.Attach.CASTER && groundSnap && age % 5 == 0) {
			// a ground template riding the caster keeps to the ground under it (S9)
			baseY = groundY(anchor());
		}
		Vec3 a = anchor();
		switch (template) {
			case ARC -> arc(a, age);
			case CRESCENT_FLY -> crescent(age);
			case BEAM_CORE -> beam(age);
			case CONE_SPRAY -> cone(age);
			case SHELL -> shell(a, age);
			case SHOCK_RING -> shockRing(a, age);
			case DOME -> dome(a, age);
			case COLUMN -> column(a, age);
			case BOLT -> bolt(age);
			case BOLT_STORM -> boltStorm(a, age);
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
			case MIST -> mist(a, age);
			case SHARDS -> shards(a, age);
			case CHAINS -> chains(a);
			case MOTES_TO -> motesTo(a);
			case HEAL_RISE -> healRise(a);
			case SHIELD_SHELL -> shieldShell(age);
			case AFTERIMAGE -> afterimage(a, age);
			case SKY_TEAR -> skyTear();
			case METEOR -> meteor(a, age);
			case STARFIELD -> starfield(age);
			default -> {
				return false;
			}
		}
		if (rodAccent && age % 3 == 0 && rnd.nextFloat() < 0.35F) {
			// the 10% real END_ROD / TOTEM accent of an override that became qi streaks
			emit(layer.particle(), a.add(sphere().scale(radius(1.5F) * 0.6D)), 0.0D, 0.02D, 0.0D);
		}
		return true;
	}

	/** Particles this tick, thinned when a quad effect carries the shape. */
	private int n(int spreadTicks, float geometry) {
		int n = Math.round(quota(spreadTicks) * geometry);
		return quad ? Math.max(n > 0 ? 1 : 0, n / 2) : n;
	}

	// ================================================================== the templates

	/** A crescent band sweeping across the strike arc, with streaks along the cut. */
	private void arc(Vec3 center, int age) {
		float r = radius(3.0F);
		int n = n(spread, geometry(r * 2.0D, 6.0D));
		double half = Math.toRadians(layer.width() > 0.0F ? Math.min(110.0F, layer.width()) : 60.0D);
		double from = -half + 2.0D * half * age / spread;
		double to = -half + 2.0D * half * (age + 1) / spread;
		for (int i = 0; i < n; i++) {
			double angle = from + (to - from) * rnd.nextDouble();
			double rr = r + (rnd.nextDouble() - 0.5D) * 0.6D * sizeFactor();
			Vec3 dir = yaw(hfwd, angle);
			Vec3 tangent = yaw(hfwd, angle + Math.PI / 2.0D);
			Vec3 at = center.add(dir.scale(rr)).add(0.0D, (rnd.nextDouble() - 0.5D) * 0.5D, 0.0D);
			if (i % 4 == 1) {
				emit(spark, at, tangent.x * 0.35D + dir.x * 0.1D, 0.02D, tangent.z * 0.35D + dir.z * 0.1D);
			} else {
				emit(i % 4 == 0 ? core : blend, at, dir.x * 0.04D + tangent.x * 0.06D, 0.0D, dir.z * 0.04D + tangent.z * 0.06D);
			}
		}
		if (n > 0 && age % 2 == 0) {
			Vec3 dir = yaw(hfwd, (from + to) / 2.0D);
			emit(ParticleTypes.SWEEP_ATTACK, center.add(dir.scale(r)), 0.0D, 0.0D, 0.0D);
		}
	}

	/** A crescent wall flying along the direction, shedding streaks behind it. */
	private void crescent(int age) {
		float distance = length(16.0F);
		float halfWidth = width(Math.max(1.5F, radius(2.5F) * 0.6F));
		int n = n(life, geometry(halfWidth * 2.0D, 4.0D));
		Vec3 start = anchor();
		double p = Math.min(1.0D, (age + 1) / (double) life);
		double eased = 1.0D - (1.0D - p) * (1.0D - p);
		Vec3 center = start.add(fwd.scale(distance * eased));
		boolean vertical = layer.variant() == FxLayer.Variant.VERTICAL;
		Vec3 side = vertical ? up : right;
		for (int i = 0; i < n; i++) {
			double s = rnd.nextDouble() * 2.0D - 1.0D;
			Vec3 at = center.add(side.scale(s * halfWidth)).add(fwd.scale(-s * s * halfWidth * 0.4D))
					.add((vertical ? right : up).scale((rnd.nextDouble() - 0.5D) * 0.3D));
			if (i % 3 == 0) {
				emit(spark, at, -fwd.x * 0.25D, -fwd.y * 0.25D, -fwd.z * 0.25D);
			} else {
				emit(Math.abs(s) > 0.8D ? alt : ramp(i), at, fwd.x * 0.05D, fwd.y * 0.05D, fwd.z * 0.05D);
			}
		}
	}

	/** A dense core along the line with a turning spiral sheath (the quad beam carries the core when it plays). */
	private void beam(int age) {
		Vec3 start = lineStart();
		Vec3 end = lineEnd(anchor());
		Vec3 delta = end.subtract(start);
		double len = Math.max(0.5D, delta.length());
		int n = n(spread, geometry(len, 8.0D));
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
				Vec3 out = side.scale(Math.cos(angle)).add(lift.scale(Math.sin(angle)));
				emit(alt, at.add(out.scale(sheath)), out.x * 0.05D, out.y * 0.05D, out.z * 0.05D);
			} else if (i % 3 == 1 && accent != null) {
				emit(accent, at.add(sphere().scale(sheath * 0.6D)), axis.x * 0.3D, axis.y * 0.3D, axis.z * 0.3D);
			} else {
				emit(ramp(i), at.x + (rnd.nextDouble() - 0.5D) * 0.12D, at.y + (rnd.nextDouble() - 0.5D) * 0.12D,
						at.z + (rnd.nextDouble() - 0.5D) * 0.12D, axis.x * 0.15D, axis.y * 0.15D, axis.z * 0.15D);
			}
		}
		if (age % 2 == 0) {
			// sparks and a splash where it hits
			for (int i = 0; i < Math.max(1, n / 6); i++) {
				Vec3 d = sphere();
				emit(spark, end, d.x * 0.3D - axis.x * 0.2D, Math.abs(d.y) * 0.3D, d.z * 0.3D - axis.z * 0.2D);
			}
		}
	}

	/** Particles streaming out through a cone, with the Dao's accent (fire tongues, frost, ink...). */
	private void cone(int age) {
		Vec3 apex = attach() == FxLayer.Attach.CASTER || layer.attach() == FxLayer.Attach.POS ? ev.handStart(anchor()) : anchor();
		float len = length(radius(7.0F));
		int n = n(spread, geometry(len, 7.0D));
		double half = Math.toRadians(layer.width() > 0.0F ? Math.min(60.0F, layer.width()) : 30.0D);
		for (int i = 0; i < n; i++) {
			double spin = rnd.nextDouble() * Math.PI * 2.0D;
			double tilt = half * Math.sqrt(rnd.nextDouble());
			Vec3 dir = fwd.scale(Math.cos(tilt)).add(right.scale(Math.sin(tilt) * Math.cos(spin))).add(up.scale(Math.sin(tilt) * Math.sin(spin)));
			double d = len * Math.sqrt(rnd.nextDouble()) * 0.35D;
			Vec3 at = apex.add(dir.scale(d));
			double speed = len / 14.0D * (0.6D + rnd.nextDouble() * 0.6D);
			ParticleOptions particle = i % 3 == 1 ? accent : i % 5 == 0 ? core : i % 5 == 4 ? alt : main;
			emit(particle, at, dir.x * speed, dir.y * speed, dir.z * speed);
		}
	}

	/** A sphere-shell burst (or an implosion for INWARD) with a core flash and an expanding ring wave. */
	private void shell(Vec3 center, int age) {
		float r = radius(3.0F);
		int n = n(spread, geometry(4.0D * Math.PI * r * r, 40.0D));
		boolean inward = layer.variant() == FxLayer.Variant.INWARD;
		if (age == 0 && !inward && r >= 2.0F) {
			emit(Qi.ring(color, 0.6F, r * 2.4F, 10), center, 0.0D, 0.0D, 0.0D);
		}
		for (int i = 0; i < n; i++) {
			Vec3 dir = sphere();
			if (inward) {
				Vec3 at = center.add(dir.scale(r * (0.9D + rnd.nextDouble() * 0.2D)));
				double speed = -r / 12.0D;
				emit(i % 5 == 0 ? core : i % 4 == 0 ? alt : main, at, dir.x * speed, dir.y * speed, dir.z * speed);
				continue;
			}
			Vec3 at = center.add(dir.scale(r * 0.25D));
			double speed = r / 7.0D * (0.7D + rnd.nextDouble() * 0.5D);
			ParticleOptions particle = i % 4 == 1 ? accent : ramp(i);
			emit(particle, at, dir.x * speed, dir.y * speed, dir.z * speed);
		}
	}

	/** Expanding ground rings (n staggered) with dust of the ground block and a flat decal (quad). */
	private void shockRing(Vec3 center, int age) {
		int rings = layer.n() > 1 ? Math.min(5, layer.n()) : 1;
		float full = radius(6.0F);
		int n = n(spread, geometry(2.0D * Math.PI * full, 12.0D));
		BlockParticleOption crumbs = ground.isAir() ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int ring = 0; ring < rings; ring++) {
			int start = rings == 1 ? 0 : ring * Math.max(2, spread) / rings;
			int local = age - start;
			if (local < 0 || local >= spread) {
				continue;
			}
			float r = full * Math.min(1.0F, (local + 1) / (float) spread) * (1.0F - 0.12F * ring);
			if (!quad && local == 0) {
				emit(Qi.groundRing(color, 0.8F, full * 2.0F, spread + 6), center.x, baseY + 0.06D, center.z, 0.0D, 0.0D, 0.0D);
			}
			int per = Math.max(1, n / rings);
			for (int i = 0; i < per; i++) {
				double angle = rnd.nextDouble() * Math.PI * 2.0D;
				double x = center.x + Math.cos(angle) * r;
				double z = center.z + Math.sin(angle) * r;
				double out = 0.05D + full / 60.0D;
				if (crumbs != null && i % 4 == 0) {
					emit(crumbs, x, baseY + 0.1D, z, Math.cos(angle) * 0.12D, 0.25D, Math.sin(angle) * 0.12D);
				} else if (i % 4 == 1) {
					emit(dao == Dao.BODY || dao == null ? Qi.smoke(0x8A7A66, mote * 2.2F, 26) : accent, x, baseY + 0.15D, z, Math.cos(angle) * out, 0.03D,
							Math.sin(angle) * out);
				} else {
					emit(ramp(i), x, baseY + 0.12D + rnd.nextDouble() * 0.3D, z, Math.cos(angle) * out, 0.02D, Math.sin(angle) * out);
				}
			}
		}
	}

	/** Motes on a hemisphere (SPLIT: the half toward the direction in the colour, the other in the second), with a base ring. */
	private void dome(Vec3 center, int age) {
		float r = radius(10.0F);
		int n = n(life, geometry(2.0D * Math.PI * r * r, 120.0D));
		boolean split = layer.variant() == FxLayer.Variant.SPLIT;
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double ca = Math.cos(angle);
			double sa = Math.sin(angle);
			boolean front = ca * hfwd.x + sa * hfwd.z >= 0.0D;
			if (i % 5 == 0) {
				emit(split && !front ? main : alt, center.x + ca * r, center.y + 0.1D, center.z + sa * r, 0.0D, 0.01D, 0.0D);
				continue;
			}
			double u = rnd.nextDouble();
			double flat = Math.sqrt(1.0D - u * u) * r;
			ParticleOptions particle = split ? (front ? (i % 3 == 0 ? core : main) : (i % 3 == 0 ? accent : alt)) : (i % 7 == 3 ? accent : ramp(i));
			emit(particle, center.x + ca * flat, center.y + u * r, center.z + sa * flat, -sa * 0.02D, 0.01D, ca * 0.02D);
		}
	}

	/** A rising pillar with a splash ring and a glow at its foot. */
	private void column(Vec3 base, int age) {
		float r = layer.radius() > 0.0F ? layer.radius() * ev.sizeF : Math.max(0.6F, Math.min(3.0F, ev.radius > 0.0F ? ev.radius * 0.3F : 1.0F));
		float height = layer.length() > 0.0F ? layer.length() * ev.sizeF : 20.0F * ev.sizeF;
		int n = n(life, geometry(height, 10.0D));
		if (age == 0 && quads()) {
			QuadFx.add(new GlowQuad(base.add(0.0D, 0.4D, 0.0D), r * 1.2F, r * 2.4F, color, 0.8F, 0.0F, 10).caster(ev.casterId));
			QuadFx.add(new RingQuad(new Vec3(base.x, groundY(base), base.z), r, r * 3.0F, color, 0.7F, 14).caster(ev.casterId));
		}
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			double y = base.y + rnd.nextDouble() * height * 0.6D;
			double rise = height / 22.0D * (0.6D + rnd.nextDouble() * 0.6D);
			if (age < 3 && i % 3 == 0) {
				emit(alt, base.x + Math.cos(angle) * r * 2.0D, base.y + 0.1D, base.z + Math.sin(angle) * r * 2.0D, Math.cos(angle) * 0.2D, 0.12D,
						Math.sin(angle) * 0.2D);
			} else if (i % 4 == 1) {
				emit(accent, base.x + Math.cos(angle) * rr, y, base.z + Math.sin(angle) * rr, 0.0D, rise, 0.0D);
			} else {
				emit(ramp(i), base.x + Math.cos(angle) * rr, y, base.z + Math.sin(angle) * rr, -Math.sin(angle) * 0.03D, rise, Math.cos(angle) * 0.03D);
			}
		}
	}

	/** One jagged bolt (quad geometry when allowed), with sparks and a scorch ring where it lands. */
	private void bolt(int age) {
		if (points.length < 6) {
			return;
		}
		int n = n(spread, geometry(points.length / 3.0D, 6.0D));
		int segments = points.length / 3 - 1;
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(segments);
			double t = rnd.nextDouble();
			double x = points[s * 3] + (points[s * 3 + 3] - points[s * 3]) * t;
			double y = points[s * 3 + 1] + (points[s * 3 + 4] - points[s * 3 + 1]) * t;
			double z = points[s * 3 + 2] + (points[s * 3 + 5] - points[s * 3 + 2]) * t;
			if (quad && i % 2 == 0) {
				emit(spark, x, y, z, rnd.nextGaussian() * 0.2D, rnd.nextGaussian() * 0.2D, rnd.nextGaussian() * 0.2D);
			} else {
				emit(i % 4 == 0 ? core : ramp(i), x, y, z, 0.0D, 0.0D, 0.0D);
			}
		}
		if (age == 0) {
			int last = points.length - 3;
			double x = points[last];
			double y = points[last + 1];
			double z = points[last + 2];
			emit(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | Qi.lerp(color, 0xFFFFFF, 0.5F)), x, y + 0.5D, z, 0.0D, 0.0D, 0.0D);
			emit(Qi.groundRing(Qi.lerp(color, 0x2A1A4A, 0.5F), 0.5F, 2.8F * Math.min(2.0F, ev.sizeF), 30), x, groundY(new Vec3(x, y, z)) + 0.04D, z, 0.0D, 0.0D, 0.0D);
			for (int i = 0; i < 8; i++) {
				Vec3 d = sphere();
				emit(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : spark, x, y + 0.2D, z, d.x * 0.35D, Math.abs(d.y) * 0.35D, d.z * 0.35D);
			}
		}
	}

	/** Bolts from a cloud to ground points across the area, a fractional number per tick (quad bolts when allowed). */
	private void boltStorm(Vec3 center, int age) {
		float r = radius(12.0F);
		double cloud = center.y + (layer.offsetY() > 0.0F ? 0.0D : 18.0D);
		// planned / 12 bolts over the life with a fractional carry (S14), at least one in the first ticks
		boltCarry += Math.max(age < 2 ? 0.5F : 0.0F, planned / 12.0F / Math.max(1, life));
		int bolts = (int) boltCarry;
		boltCarry -= bolts;
		for (int b = 0; b < Math.min(6, bolts); b++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			Vec3 ground0 = new Vec3(center.x + Math.cos(angle) * rr, ev.pos.y, center.z + Math.sin(angle) * rr);
			Vec3 end = new Vec3(ground0.x, groundY(ground0.add(0.0D, 2.0D, 0.0D)), ground0.z);
			Vec3 start = new Vec3(end.x + (rnd.nextDouble() - 0.5D) * r * 0.4D, cloud, end.z + (rnd.nextDouble() - 0.5D) * r * 0.4D);
			boolean drawn = quads() && QuadFx.add(new BoltQuad(start, end, 0.25F * Math.min(2.0F, ev.sizeF), color, Qi.core(color), ev.seed + age * 31 + b,
					2, 7).caster(ev.casterId));
			if (!drawn) {
				float[] path = jagged(start, end, 0.06D);
				for (int i = 3; i < path.length; i += 3) {
					for (int k = 0; k < 3; k++) {
						double t = k / 3.0D;
						emit(k == 0 ? core : main, path[i - 3] + (path[i] - path[i - 3]) * t, path[i - 2] + (path[i + 1] - path[i - 2]) * t,
								path[i - 1] + (path[i + 2] - path[i - 1]) * t, 0.0D, 0.0D, 0.0D);
					}
				}
			}
			emit(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.2D, end.z, 0.0D, 0.2D, 0.0D);
			emit(Qi.groundRing(Qi.lerp(color, 0x2A1A4A, 0.5F), 0.4F, 2.2F, 26), end.x, end.y + 0.04D, end.z, 0.0D, 0.0D, 0.0D);
			for (int i = 0; i < 3; i++) {
				Vec3 d = sphere();
				emit(spark, end.x, end.y + 0.3D, end.z, d.x * 0.3D, Math.abs(d.y) * 0.3D, d.z * 0.3D);
			}
		}
	}

	/** A dark disc of billowing cloud turning overhead, lit from inside for lightning arts. */
	private void cloudDisc(Vec3 center, int age) {
		float r = Math.max(radius(10.0F), 6.0F);
		int n = n(life, geometry(Math.PI * r * r, 300.0D));
		double y = center.y + (layer.offsetY() > 0.0F || layer.attach() == FxLayer.Attach.SKY ? 0.0D : 18.0D);
		ParticleOptions cloud = Qi.smoke(color, Math.min(4.0F, 1.6F + r * 0.08F), 50);
		for (int i = 0; i < n; i++) {
			double rr = r * Math.sqrt(rnd.nextDouble());
			double angle = rnd.nextDouble() * Math.PI * 2.0D + age * 0.04D;
			double x = center.x + Math.cos(angle) * rr;
			double z = center.z + Math.sin(angle) * rr;
			double swirl = 0.04D + rr / r * 0.04D;
			if (i % 10 == 0) {
				emit(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
			} else if (i % 12 == 5 && dao == Dao.LIGHTNING) {
				emit(Qi.glint(Qi.lerp(color2, 0xFFFFFF, 0.6F), 2.5F, 4), x, y - 0.5D, z, 0.0D, 0.0D, 0.0D);
			} else {
				emit(i % 4 == 0 ? alt : cloud, x, y + (rnd.nextDouble() - 0.5D) * 1.4D, z, -Math.sin(angle) * swirl, 0.0D, Math.cos(angle) * swirl);
			}
		}
	}

	/** Spiral arms turning in (INWARD) or out, the motes moving along the spiral. */
	private void vortex(Vec3 center, int age) {
		float r = radius(5.0F);
		int n = n(life, geometry(Math.PI * r * r, 80.0D));
		boolean inward = layer.variant() == FxLayer.Variant.INWARD;
		for (int i = 0; i < n; i++) {
			double t = rnd.nextDouble();
			double phase = (age * 0.05D + t) % 1.0D;
			double rr = r * (inward ? 1.0D - phase : phase);
			double angle = (i % 3) * Math.PI * 2.0D / 3.0D + phase * Math.PI * 4.0D * (inward ? 1.0D : -1.0D);
			double y = center.y + t * r * 0.4D;
			double tangent = (inward ? 1.0D : -1.0D) * (0.08D + r * 0.02D);
			double radial = (inward ? -1.0D : 1.0D) * r * 0.012D;
			double vx = -Math.sin(angle) * tangent + Math.cos(angle) * radial;
			double vz = Math.cos(angle) * tangent + Math.sin(angle) * radial;
			emit(i % 5 == 2 ? accent : ramp(i), center.x + Math.cos(angle) * rr, y, center.z + Math.sin(angle) * rr, vx, 0.01D, vz);
		}
	}

	/** Two turning rings with eight rune marks between them (flat, or VERTICAL facing the caster's look). */
	private void runeCircle(Vec3 center, int age) {
		float r = radius(4.0F);
		int n = n(life, geometry(2.0D * Math.PI * r, 25.0D));
		double turn = age * spinRate();
		boolean mirrored = layer.variant() == FxLayer.Variant.MIRRORED;
		boolean vertical = layer.variant() == FxLayer.Variant.VERTICAL;
		Vec3 axisA;
		Vec3 axisB;
		Vec3 c;
		if (vertical) {
			axisA = right;
			axisB = UP;
			c = center.add(0.0D, layer.offsetY() > 0.0F ? 0.0D : 1.4D, 0.0D).add(hfwd.scale(1.2D));
		} else {
			axisA = new Vec3(1.0D, 0.0D, 0.0D);
			axisB = new Vec3(0.0D, 0.0D, 1.0D);
			c = new Vec3(center.x, baseY + 0.08D, center.z);
		}
		for (int i = 0; i < n; i++) {
			int kind = i % 4;
			if (kind < 2) {
				double angle = rnd.nextDouble() * Math.PI * 2.0D;
				emit(i % 8 == 0 ? core : main, c.add(axisA.scale(Math.cos(angle) * r)).add(axisB.scale(Math.sin(angle) * r)), 0.0D, 0.004D, 0.0D);
			} else if (kind == 2) {
				double angle = rnd.nextDouble() * Math.PI * 2.0D;
				emit(alt, c.add(axisA.scale(Math.cos(angle) * r * 0.78D)).add(axisB.scale(Math.sin(angle) * r * 0.78D)), 0.0D, 0.004D, 0.0D);
			} else {
				int mark = rnd.nextInt(8);
				double angle = Math.PI * 2.0D * mark / 8.0D + (mirrored ? -turn : turn);
				double s = (rnd.nextDouble() - 0.5D) * r * 0.16D;
				boolean along = rnd.nextBoolean();
				double ca = Math.cos(angle);
				double sa = Math.sin(angle);
				double du = along ? s * ca : -s * sa;
				double dv = along ? s * sa : s * ca;
				Vec3 at = c.add(axisA.scale(ca * r * 0.89D + du)).add(axisB.scale(sa * r * 0.89D + dv));
				emit(i % 16 == 3 ? Qi.glint(color2, mote * 1.4F, 10) : alt, at, 0.0D, 0.006D, 0.0D);
			}
		}
	}

	/** A lotus or blossom opening, petals drifting off at the end. */
	private void petals(Vec3 center, int age) {
		int count = layer.n() > 0 ? Math.min(16, layer.n()) : 8;
		float length = radius(1.6F);
		int n = n(spread, geometry(length, 1.6D));
		double open = Math.min(1.0D, (age + 1) / 10.0D);
		double tilt = Math.toRadians(80.0D - 45.0D * open);
		ParticleOptions petal = Qi.petal(Qi.lerp(color, 0xFFFFFF, 0.25F), mote * 1.3F, 30);
		for (int i = 0; i < n; i++) {
			int which = rnd.nextInt(count);
			double heading = Math.PI * 2.0D * which / count;
			Vec3 radial = new Vec3(Math.cos(heading), 0.0D, Math.sin(heading));
			Vec3 tangent = new Vec3(-Math.sin(heading), 0.0D, Math.cos(heading));
			double s = rnd.nextDouble();
			double w = length * 0.35D * Math.sin(Math.PI * s) * (rnd.nextBoolean() ? 1.0D : -1.0D) * rnd.nextDouble();
			Vec3 at = center.add(radial.scale(s * length * Math.cos(tilt))).add(0.0D, s * length * Math.sin(tilt), 0.0D).add(tangent.scale(w));
			if (i % 6 == 5) {
				emit(petal, at, radial.x * 0.05D, 0.04D, radial.z * 0.05D);
			} else {
				emit(s > 0.7D ? alt : i % 5 == 0 ? core : blend, at, 0.0D, 0.01D, 0.0D);
			}
		}
	}

	/** A palm or fist of points: sliding along a line, falling from the sky (FALLING) or standing at the anchor. */
	private void palm(Vec3 anchor, int age, boolean fist) {
		float size;
		if (layer.radius() > 0.0F) {
			size = layer.radius() * ev.sizeF;
		} else if (layer.size() != 1.0F) {
			size = 2.0F * layer.size() * ev.sizeF;
		} else if (ev.radius > 0.0F) {
			size = Math.min(30.0F, ev.radius);
		} else {
			size = 2.0F * ev.sizeF;
		}
		size = Math.min(30.0F, size) * tierScale;
		int n = n(life, geometry(size, 2.0D));
		boolean preImpact = ev.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		double progress = Math.min(1.0D, (age + 1) / (double) Math.max(1, preImpact ? life : life * 2 / 3));
		boolean falling = layer.variant() == FxLayer.Variant.FALLING || layer.motion() == FxLayer.Motion.FALL;
		Vec3 center;
		Vec3 axisX;
		Vec3 axisY;
		Vec3[] view = viewPlane();
		if (falling) {
			double height = layer.motionA() > 0.0F ? layer.motionA() : 12.0D;
			Vec3 ground0 = ev.hasTarget ? ev.target : anchor;
			center = ground0.add(0.0D, height * (1.0D - progress * progress) + 0.2D, 0.0D);
			axisX = right;
			axisY = hfwd;
			if (age == 0 && quads() && size >= 3.0F) {
				// the shadow of the palm grows on the ground before it lands
				QuadFx.add(new RingQuad(new Vec3(ground0.x, groundY(ground0.add(0.0D, 1.0D, 0.0D)), ground0.z), size * 0.2F, size * 0.8F, color, 0.6F,
						Math.max(6, (int) Math.round(life * (preImpact ? 1.0D : 0.67D)))).caster(ev.casterId));
			}
		} else if (attach() == FxLayer.Attach.LINE && ev.hasTarget) {
			Vec3 start = lineStart();
			center = start.add(lineEnd(start).subtract(start).scale(progress));
			axisX = view[0];
			axisY = view[1];
		} else {
			// a standing fist sits 1.5 blocks ahead of the striking hand (S9); a palm at the event point (the hand in first person)
			boolean free = layer.attach() == FxLayer.Attach.POS && !ev.follow();
			center = free && fist ? ev.hand().add(fwd.scale(1.5D)) : free ? ev.handStart(anchor) : anchor;
			axisX = view[0];
			axisY = view[1];
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
			emit(edge ? (i % 6 == 0 ? core : alt) : main, at, 0.0D, 0.0D, 0.0D);
		}
		if (falling && age == life - 1) {
			Vec3 ground0 = ev.hasTarget ? ev.target : anchor;
			for (int i = 0; i < 24; i++) {
				Vec3 d = sphere();
				emit(i % 3 == 0 ? accent : main, ground0.add(d.x * size * 0.4D, 0.3D, d.z * size * 0.4D), d.x * 0.3D, Math.abs(d.y) * 0.3D, d.z * 0.3D);
			}
		}
	}

	/** The viewer's right and up axes (point clouds face the camera, S15). */
	private static Vec3[] viewPlane() {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vector3fc left = camera.leftVector();
		Vector3fc upv = camera.upVector();
		return new Vec3[]{new Vec3(-left.x(), -left.y(), -left.z()), new Vec3(upv.x(), upv.y(), upv.z())};
	}

	/** Swords falling around the anchor, flying along the direction or fanned out from the caster. */
	private void swords(Vec3 anchor, int age) {
		int n = n(life, 1.0F);
		int swords = Math.max(1, Math.min(swordCount(), points.length / 4));
		float length = 2.5F * sizeFactor() * ev.sizeF * tierScale;
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
			Vec3 motion = Vec3.ZERO;
			switch (variant) {
				case FALLING -> {
					double angle = points[sword * 4];
					double rr = r * points[sword * 4 + 1];
					double start = points[sword * 4 + 2];
					double t = Math.max(0.0D, Math.min(1.0D, ((age + 1) / (double) life - start) / 0.5D));
					Vec3 base = (ev.hasTarget ? ev.target : anchor).add(Math.cos(angle) * rr, 12.0D * (1.0D - t * t), Math.sin(angle) * rr);
					Vec3 side = yaw(new Vec3(1.0D, 0.0D, 0.0D), points[sword * 4 + 3]);
					at = base.add(side.scale(x * length)).add(0.0D, -(y + 0.6D) * length, 0.0D);
					motion = new Vec3(0.0D, -0.3D, 0.0D);
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
					// FLYING swords lie flat, edge forward (polish S15: face up)
					at = base.add(fwd.scale((y + 0.6D) * length)).add(right.scale(x * length));
					motion = fwd.scale(0.2D);
				}
			}
			boolean tip = y > 0.8D || y < -0.25D;
			emit(tip ? (i % 3 == 0 ? spark : alt) : ramp(i), at, motion.x, motion.y, motion.z);
		}
	}

	/** Two wings behind the caster, flapping slowly, shedding feathers or embers. */
	private void wings(int age) {
		int n = n(life, 1.0F);
		Entity caster = ev.caster();
		Vec3 base = ev.casterPos();
		double yawRad = Math.toRadians(ev.casterYaw());
		Vec3 back = new Vec3(Math.sin(yawRad), 0.0D, -Math.cos(yawRad));
		Vec3 side = new Vec3(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
		Vec3 shoulder = base.add(0.0D, (caster == null ? 1.35D : caster.getBbHeight() * 0.75D) + layer.offsetY(), 0.0D).add(back.scale(0.35D));
		float span = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.2F * sizeFactor() * ev.sizeF) * tierScale;
		double flap = Math.sin(age * 0.3D) * 0.35D;
		float[] wing = PointClouds.wing();
		int pointCount = wing.length / 2;
		ParticleOptions feather = dao == Dao.FIRE ? Qi.flame(color, mote * 1.2F, 14) : Qi.feather(color, mote * 2.0F, 40);
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(pointCount);
			double x = wing[p * 2];
			double y = wing[p * 2 + 1];
			double mirror = i % 2 == 0 ? 1.0D : -1.0D;
			Vec3 at = shoulder.add(side.scale(mirror * x * span)).add(0.0D, y * span, 0.0D).add(back.scale(x * span * (0.3D + flap)));
			if (i % 9 == 4) {
				emit(feather, at, back.x * 0.05D, -0.02D, back.z * 0.05D);
			} else {
				emit(x > 0.8D ? alt : ramp(i), at, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** A point-cloud beast facing the viewer, charging to the target when there is one. */
	private void beast(Vec3 anchor, int age) {
		FxLayer.BeastKind kind = layer.beast() == null ? FxLayer.BeastKind.DRAGON : layer.beast();
		float size = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 3.0F * sizeFactor() * ev.sizeF) * tierScale;
		int n = n(life, geometry(size, 3.0D));
		int copies = layer.n() > 1 ? Math.min(5, layer.n()) : 1;
		float[] shape = PointClouds.beast(kind);
		int pointCount = shape.length / 2;
		Vec3[] view = viewPlane();
		// mirror the silhouette so it faces its direction of travel on screen
		double facing = Math.signum(view[0].x * hfwd.x + view[0].z * hfwd.z);
		if (facing == 0.0D) {
			facing = 1.0D;
		}
		for (int i = 0; i < n; i++) {
			int copy = i % copies;
			Vec3 offset = copies == 1 ? Vec3.ZERO : right.scale((copy - (copies - 1) / 2.0D) * size * 0.9D);
			Vec3 center = ev.hasTarget ? anchor.add(lineEnd(anchor).subtract(anchor).scale(Math.min(1.0D, (age + 1) / (double) life)))
					: anchor.add(0.0D, size * 0.6D, 0.0D);
			int p = rnd.nextInt(pointCount);
			double x = shape[p * 2] * facing;
			double y = shape[p * 2 + 1];
			Vec3 at = center.add(offset).add(view[0].scale(x * size)).add(view[1].scale(y * size));
			emit(i % 6 == 3 ? accent : ramp(i), at, hfwd.x * 0.05D, 0.0D, hfwd.z * 0.05D);
		}
	}

	/** Trail particles running from the start to the end of the line. */
	private void trail(Vec3 start0) {
		Vec3 start = lineStart();
		Vec3 end = lineEnd(start);
		Vec3 delta = end.subtract(start);
		int n = n(life, geometry(delta.length(), 6.0D));
		for (int i = 0; i < n; i++) {
			double t = rnd.nextDouble();
			Vec3 at = start.add(delta.scale(t * 0.3D)).add((rnd.nextDouble() - 0.5D) * 0.3D, (rnd.nextDouble() - 0.5D) * 0.3D,
					(rnd.nextDouble() - 0.5D) * 0.3D);
			if (i % 3 == 0) {
				emit(ramp(i), start.add(delta.scale(t)), 0.0D, 0.0D, 0.0D);
			} else {
				emit(new TrailParticleOption(end, i % 2 == 0 ? color : color2, 10 + rnd.nextInt(10)), at, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** A flash, a spray of streaks and a burst where something lands. */
	private void impact(Vec3 center, int age) {
		float r = radius(1.5F);
		int n = n(spread, geometry(r, 1.5D));
		boolean boss = ev.has(Payloads.TechFx.FLAG_BOSS_HIT);
		if (age == 0) {
			emit(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), center.x, center.y + 0.3D, center.z, 0.0D, 0.0D, 0.0D);
			if (!quad) {
				emit(Qi.ring(color, 0.4F, r * 2.2F, 7), center, 0.0D, 0.0D, 0.0D);
			}
			if (r >= 3.0F || boss) {
				emit(ParticleTypes.EXPLOSION, center.x, center.y + 0.5D, center.z, 0.0D, 0.0D, 0.0D);
			}
		}
		for (int i = 0; i < n; i++) {
			Vec3 dir = sphere();
			double speed = 0.12D + r * 0.05D;
			if (i % 6 == 0) {
				emit(i % 12 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, center.add(dir.scale(0.3D)), dir.x * 0.4D, dir.y * 0.4D,
						dir.z * 0.4D);
			} else if (i % 3 == 1) {
				emit(spark, center.add(dir.scale(0.2D)), dir.x * speed * 2.2D, dir.y * speed * 2.2D, dir.z * speed * 2.2D);
			} else {
				emit(i % 5 == 2 ? accent : ramp(i), center.add(dir.scale(r * 0.3D)), dir.x * speed, Math.abs(dir.y) * speed, dir.z * speed);
			}
		}
	}

	/** Ground cracks (radiating, or one furrow along a LINE) with crumbs and, in Mode Kehancuran, BLOCK_CRUMBLE. */
	private void cracks(int age) {
		int n = n(spread, geometry(points.length / 3.0D, 30.0D));
		if (points.length < 3) {
			return;
		}
		boolean kehancuran = ev.has(Payloads.TechFx.FLAG_KEHANCURAN);
		BlockParticleOption crumbs = ground.isAir() ? null : new BlockParticleOption(kehancuran ? ParticleTypes.BLOCK_CRUMBLE : ParticleTypes.BLOCK, ground);
		BlockParticleOption dust = ground.isAir() || attach() != FxLayer.Attach.LINE ? null : new BlockParticleOption(ParticleTypes.FALLING_DUST, ground);
		ParticleOptions seam = Qi.make(dev.kultivator.fx.QiParticles.QI, 0x3A2010, Qi.lerp(color, 0x1A1008, 0.5F), 0x0A0604, 0.32F, 0.3F, 40, (byte) 0);
		ParticleOptions glow = Qi.mote(Qi.lerp(color2, 0xFFE0A0, 0.5F), 0.18F, 18);
		int segments = points.length / 3;
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(segments);
			double x = points[s * 3] + (rnd.nextDouble() - 0.5D) * 0.3D;
			double z = points[s * 3 + 2] + (rnd.nextDouble() - 0.5D) * 0.3D;
			if (crumbs != null && i % 3 == 0) {
				emit(crumbs, x, baseY + 0.1D, z, 0.0D, 0.15D, 0.0D);
			} else if (dust != null && i % 3 == 1) {
				emit(dust, x, baseY + 0.3D + rnd.nextDouble() * 0.4D, z, 0.0D, 0.0D, 0.0D);
			} else if (i % 5 == 2 && !dark) {
				emit(glow, x, baseY + 0.07D, z, 0.0D, 0.01D, 0.0D);
			} else {
				emit(seam, x, baseY + 0.05D, z, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** Low drifting mist over the area. */
	private void mist(Vec3 center, int age) {
		float r = radius(6.0F);
		int n = n(life, geometry(Math.PI * r * r, 110.0D));
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			double x = center.x + Math.cos(angle) * rr;
			double z = center.z + Math.sin(angle) * rr;
			double drift = 0.015D + rnd.nextDouble() * 0.02D;
			if (i % 10 == 0) {
				emit(ParticleTypes.WHITE_ASH, x, baseY + rnd.nextDouble() * 2.0D, z, 0.0D, 0.0D, 0.0D);
			} else if (i % 7 == 3) {
				emit(accent, x, baseY + 0.3D + rnd.nextDouble(), z, -Math.sin(angle) * drift, 0.01D, Math.cos(angle) * drift);
			} else {
				emit(i % 4 == 0 ? alt : big, x, baseY + rnd.nextDouble() * 1.2D, z, -Math.sin(angle) * drift, 0.0D, Math.cos(angle) * drift);
			}
		}
	}

	/** Shards flying out (or in, for INWARD). */
	private void shards(Vec3 center, int age) {
		float r = radius(3.0F);
		int n = n(spread, geometry(r, 3.0D));
		boolean inward = layer.variant() == FxLayer.Variant.INWARD;
		ParticleOptions shard = dao == Dao.ICE || dao == null ? Qi.shard(color, mote * 1.4F, 30) : accent;
		for (int i = 0; i < n; i++) {
			Vec3 dir = sphere();
			Vec3 at = center.add(dir.scale(inward ? r : 0.4D));
			double speed = (inward ? -0.18D : 0.26D) * Math.max(1.0D, r / 3.0D);
			ParticleOptions particle = i % 8 == 0 ? spark : i % 3 == 0 ? shard : i % 3 == 1 ? ramp(i) : alt;
			emit(particle, at, dir.x * speed, dir.y * speed + (inward ? 0.0D : 0.08D), dir.z * speed);
		}
	}

	/** Chain links along the line, a glint on every fourth link. */
	private void chains(Vec3 start0) {
		Vec3 start = lineStart();
		Vec3 end = lineEnd(start);
		Vec3 delta = end.subtract(start);
		double len = Math.max(0.5D, delta.length());
		int n = n(spread, geometry(len, 8.0D));
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
			emit(link % 4 == 0 && i % 5 == 0 ? Qi.glint(color, mote, 8) : link % 2 == 0 ? main : alt, at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Motes drawn from around the anchor to the caster (or the second point). */
	private void motesTo(Vec3 center) {
		float r = radius(5.0F);
		int n = n(life, geometry(r, 5.0D));
		Vec3 to = ev.hasTarget && layer.attach() != FxLayer.Attach.TARGET ? targetPoint() : ev.casterPos().add(0.0D, 1.0D, 0.0D);
		for (int i = 0; i < n; i++) {
			Vec3 from = center.add(sphere().scale(r * (0.4D + rnd.nextDouble() * 0.6D)));
			if (i % 3 == 1) {
				// a qi mote flying the distance in its own life
				Vec3 v = to.subtract(from).scale(1.0D / 14.0D);
				emit(Qi.mote(Qi.core(color), color, color2, mote * 0.9F, mote * 0.3F, 14, (byte) (dev.kultivator.fx.QiParticleOptions.GLOW
						| dev.kultivator.fx.QiParticleOptions.NO_GRAVITY)), from, v.x, v.y, v.z);
			} else {
				emit(new TrailParticleOption(to, i % 3 == 0 ? color2 : color, 12 + rnd.nextInt(10)), from, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** Healing motes rising in a column, with petals or glints. */
	private void healRise(Vec3 center) {
		int n = n(life, 1.0F);
		float r = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 1.2F) * tierScale;
		for (int i = 0; i < n; i++) {
			double angle = rnd.nextDouble() * Math.PI * 2.0D;
			double rr = r * Math.sqrt(rnd.nextDouble());
			emit(i % 6 == 3 ? accent : ramp(i), center.x + Math.cos(angle) * rr, center.y + rnd.nextDouble() * 1.5D, center.z + Math.sin(angle) * rr,
					-Math.sin(angle) * 0.02D, 0.08D + rnd.nextDouble() * 0.04D, Math.cos(angle) * 0.02D);
		}
	}

	/** A lattice shell of motes around the caster, shimmering. */
	private void shieldShell(int age) {
		int n = n(life, 1.0F);
		Entity caster = ev.caster();
		double h = caster == null ? 1.8D : caster.getBbHeight();
		Vec3 center = ev.casterPos().add(0.0D, h * 0.55D + layer.offsetY(), 0.0D);
		float r = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 1.4F * sizeFactor()) * tierScale;
		for (int i = 0; i < n; i++) {
			int lat = rnd.nextInt(7) - 3;
			int lon = rnd.nextInt(12);
			double phi = lat * Math.PI / 8.0D;
			double theta = lon * Math.PI / 6.0D + (lat % 2 == 0 ? 0.0D : Math.PI / 12.0D) + age * 0.01D;
			Vec3 at = center.add(Math.cos(phi) * Math.cos(theta) * r, Math.sin(phi) * r * 1.2D, Math.cos(phi) * Math.sin(theta) * r);
			emit(i % 9 == 0 ? Qi.glint(color, mote * 1.2F, 10) : ramp(i), at, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A fading figure of the caster where it stood. */
	private void afterimage(Vec3 center, int age) {
		int n = n(life, 1.0F);
		float[] figure = PointClouds.humanoid();
		int pointCount = figure.length / 2;
		Vec3 side = hfwd.cross(UP).normalize();
		double height = 0.95D * sizeFactor();
		for (int i = 0; i < n; i++) {
			int p = rnd.nextInt(pointCount);
			Vec3 at = center.add(side.scale(figure[p * 2] * height)).add(0.0D, (figure[p * 2 + 1] + 1.0D) * height, 0.0D);
			emit(i % 3 == 0 ? alt : main, at, -hfwd.x * 0.01D, 0.006D, -hfwd.z * 0.01D);
		}
	}

	/** A glowing tear high in the sky with light falling from it. */
	private void skyTear() {
		int n = n(life, 1.0F);
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
			if (i % 5 == 4) {
				emit(i % 10 == 9 ? Qi.glint(0xFFFFFF, 1.2F, 20) : spark, x, y - rnd.nextDouble() * 2.0D, z, 0.0D, -0.4D, 0.0D);
			} else {
				emit(i % 2 == 0 ? big : main, x, y, z, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** A flaming sphere falling from high up to the target (landing on time for PRE_IMPACT events), bursting where it lands. */
	private void meteor(Vec3 anchor, int age) {
		int n = n(life, 1.0F);
		Vec3 end = ev.hasTarget ? ev.target : anchor;
		boolean preImpact = ev.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		Vec3 start = end.add(0.0D, 40.0D, 0.0D).subtract(hfwd.scale(20.0D));
		int fall = preImpact ? Math.max(2, life - 1) : Math.max(4, life * 7 / 10);
		float r = (layer.radius() > 0.0F ? layer.radius() * ev.sizeF : 2.0F * sizeFactor() * ev.sizeF) * tierScale;
		ParticleOptions flame = Qi.flame(color, Math.min(2.5F, r * 0.8F), 12);
		ParticleOptions smoke = Qi.smoke(0x3A1A10, Math.min(2.5F, r * 0.9F), 40);
		if (age == 0 && quads()) {
			QuadFx.add(new RingQuad(new Vec3(end.x, groundY(end.add(0.0D, 1.0D, 0.0D)), end.z), r * 0.5F, r * 2.0F, color, 0.5F, fall).caster(ev.casterId));
		}
		if (age < fall) {
			double p = (age + 1) / (double) fall;
			Vec3 head = start.add(end.subtract(start).scale(p * p));
			Vec3 back = start.subtract(end).normalize();
			for (int i = 0; i < n; i++) {
				if (i % 4 == 0) {
					emit(smoke, head.add(back.scale(r + rnd.nextDouble() * r * 2.0D)), back.x * 0.05D, 0.02D, back.z * 0.05D);
				} else if (i % 4 == 1) {
					emit(flame, head.add(sphere().scale(r * rnd.nextDouble())), back.x * 0.2D, back.y * 0.2D, back.z * 0.2D);
				} else {
					emit(i % 2 == 0 ? core : main, head.add(sphere().scale(r)), 0.0D, 0.0D, 0.0D);
				}
			}
		} else if (age == fall) {
			emit(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), end.x, end.y + 1.0D, end.z, 0.0D, 0.0D, 0.0D);
			emit(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y + 0.5D, end.z, 0.0D, 0.0D, 0.0D);
			emit(Qi.groundRing(color, r, r * 5.0F, 18), end.x, groundY(end.add(0.0D, 1.0D, 0.0D)) + 0.05D, end.z, 0.0D, 0.0D, 0.0D);
			for (int i = 0; i < Math.max(12, n * 4); i++) {
				Vec3 dir = sphere();
				emit(i % 2 == 0 ? flame : i % 3 == 0 ? smoke : main, end.add(dir.scale(r)), dir.x * 0.4D, Math.abs(dir.y) * 0.4D, dir.z * 0.4D);
			}
		}
	}

	/** Twinkling stars (n of them; a radius below 1 makes a cluster circling the head) with constellation lines. */
	private void starfield(int age) {
		int n = n(life, 1.0F);
		int stars = points.length / 3;
		if (stars < 2) {
			return;
		}
		boolean cluster = layer.radius() > 0.0F && layer.radius() < 1.0F;
		Vec3 center = cluster ? ev.casterPos().add(0.0D, 2.1D, 0.0D) : Vec3.ZERO;
		double turn = cluster ? age * 0.06D : 0.0D;
		ParticleOptions star = Qi.glint(Qi.lerp(color, 0xFFFFFF, 0.5F), cluster ? 0.35F : 1.0F, 10);
		for (int i = 0; i < n; i++) {
			int s = rnd.nextInt(stars);
			double sx = points[s * 3];
			double sy = points[s * 3 + 1];
			double sz = points[s * 3 + 2];
			if (cluster) {
				double c = Math.cos(turn);
				double sn = Math.sin(turn);
				double x = sx * c - sz * sn;
				double z = sx * sn + sz * c;
				emit(i % 3 == 0 ? alt : star, center.x + x, center.y + sy, center.z + z, 0.0D, 0.0D, 0.0D);
				continue;
			}
			if (i % 3 == 0) {
				int next = (s / 4) * 4 + (s % 4 + 1) % 4;
				if (next >= stars) {
					next = s;
				}
				double t = rnd.nextDouble();
				emit(alt, sx + (points[next * 3] - sx) * t, sy + (points[next * 3 + 1] - sy) * t, sz + (points[next * 3 + 2] - sz) * t, 0.0D, 0.0D, 0.0D);
			} else {
				emit(i % 11 == 0 ? ParticleTypes.END_ROD : i % 2 == 0 ? star : main, sx, sy, sz, 0.0D, 0.0D, 0.0D);
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
