package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One layer of one event while it plays (DESIGN 22.4): it waits {@code delay} ticks, then {@link #update} runs once per
 * client tick until its lifetime is over. Every particle goes through {@link #emit}, which charges the {@link FxBudget}
 * (by particle lifetime; a refused partial grant is given back), the event's particle cap and the END_ROD / TOTEM share
 * of accents. The random source is seeded from the event, so every client draws the same shape.
 * <p>
 * Polish S3: a lasting layer (life above 40 ticks or a lasting template) counts its {@code count} per 20 ticks for its
 * whole life, with a minimum density per template; a burst template authored longer than its burst becomes burst +
 * sustain (60% in the burst, then a steady trickle). S6: a layer above the caster's tier draws smaller and thinner instead
 * of not at all. S9: a FOLLOW event's POS layers ride the caster. S14: {@code size ≥ 3} on ring, shell, dome and mist
 * templates scales their geometry (radius × size / 4) and particle sizes stay ≤ 2 blocks.
 */
abstract class LayerRun {

	static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

	final FxEvent ev;
	final FxLayer layer;
	final RandomSource rnd;
	final FxBudget.Priority priority;
	final int life;
	/** Seconds-free age in ticks; negative while delayed. */
	int age;
	/** Particles this layer plans to draw in all. */
	final float planned;
	/** Lasting layers (domains, fields, forms) count per second and cap the per-event share per second. */
	final boolean lasting;
	/** S6: 1 at or above the caster's tier, 0.6 / 0.45 one or two tiers short. */
	final float tierScale;
	/** The cast spec and caster this run belongs to (ABORT and followUntil end them). */
	final int casterId;
	final int specNet;
	/** The phase that ends this run when it arrives (followUntil), or null. */
	final Phase until;
	/** Burst ticks of a burst template ({@code life} for others). */
	private final int burst;
	private final float sustainPerTick;
	private final float minPerTick;
	private int rodSpent;
	private int secondSpent;
	private float carry;
	/** Set by {@code SpecFx} to fade a run out early (ABORT, followUntil): the remaining ticks. */
	int endIn = -1;
	/** Overflow (too many runs this tick): age without emitting. */
	boolean starved;

	/** Unit vectors around the event direction. */
	final Vec3 fwd;
	final Vec3 right;
	final Vec3 up;
	/** Horizontal forward (never zero). */
	final Vec3 hfwd;

	LayerRun(FxEvent ev, FxLayer layer, int life, int index) {
		this(ev, layer, life, index, 1.0F);
	}

	LayerRun(FxEvent ev, FxLayer layer, int life, int index, float tierScale) {
		this.ev = ev;
		this.layer = layer;
		this.tierScale = tierScale;
		this.rnd = RandomSource.create(ev.seed * 31L + index * 7919L + layer.template().ordinal() + ev.index * 104729L);
		this.priority = switch (layer.priority()) {
			case CORE -> FxBudget.Priority.CORE;
			case DETAIL -> FxBudget.Priority.DETAIL;
			case EXTRA -> FxBudget.Priority.EXTRA;
		};
		this.life = Math.max(1, life);
		this.age = -Math.max(0, layer.delay());
		this.casterId = ev.casterId;
		this.specNet = ev.spec.netId();
		this.until = layer.followUntil();
		this.lasting = this.life > 40 || lastingTemplate(layer.template());
		float base = layer.count() * ev.countF * ev.viewScale * (tierScale < 1.0F ? 0.5F : 1.0F);
		int cap = burstCap(layer.template());
		if (lasting) {
			// count per 20 ticks for the whole life
			this.planned = Math.max(1.0F, base * this.life / 20.0F);
			this.burst = this.life;
			this.sustainPerTick = 0.0F;
		} else if (cap > 0 && this.life > cap + 2) {
			// burst + sustain: 60% over the burst, then a steady 20% per 20 ticks
			this.planned = Math.max(1.0F, base * 0.6F);
			this.burst = cap;
			this.sustainPerTick = base * 0.2F / 20.0F;
		} else {
			this.planned = Math.max(1.0F, base);
			this.burst = cap > 0 ? Math.min(this.life, cap) : this.life;
			this.sustainPerTick = 0.0F;
		}
		this.minPerTick = lasting ? minDensity(layer.template()) * EffectQuality.level().factor() * Math.min(1.0F, ev.viewScale * 1.5F) : 0.0F;
		this.fwd = ev.dir;
		Vec3 side = fwd.cross(UP);
		this.right = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		this.up = right.cross(fwd).normalize();
		Vec3 flat = new Vec3(fwd.x, 0.0D, fwd.z);
		this.hfwd = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
	}

	/** Templates that are lasting by nature (their count is per second). */
	static boolean lastingTemplate(FxLayer.Template template) {
		return switch (template) {
			case DOME, MIST, CLOUD_DISC, RUNE_CIRCLE, STARFIELD, WINGS, SHIELD_SHELL, VORTEX, SWARM, HEAL_RISE -> true;
			default -> false;
		};
	}

	/** The burst length of a burst template (0 = not a burst template). */
	static int burstCap(FxLayer.Template template) {
		return switch (template) {
			case SHELL, IMPACT, SHARDS, BOLT -> 3;
			case ARC -> 6;
			case BEAM_CORE, CONE_SPRAY, CRACKS, CHAINS -> 8;
			case SHOCK_RING, PETALS -> 12;
			default -> 0;
		};
	}

	/** Minimum particles per tick at Normal for a lasting template (S3). */
	static float minDensity(FxLayer.Template template) {
		return switch (template) {
			case MIST -> 3.0F;
			case DOME, VORTEX -> 4.0F;
			case SHIELD_SHELL, RUNE_CIRCLE -> 3.0F;
			case HEAL_RISE -> 2.0F;
			case STARFIELD -> 1.0F;
			default -> 0.0F;
		};
	}

	/** One client tick; false when the layer is over. */
	final boolean tick() {
		if (age < 0) {
			age++;
			return true;
		}
		if (age % 20 == 0) {
			secondSpent = 0;
		}
		boolean more = true;
		if (!starved) {
			more = update(age);
		}
		starved = false;
		age++;
		if (endIn >= 0 && endIn-- == 0) {
			return false;
		}
		return more && age < life;
	}

	/** Draws tick {@code age} (0-based); false to end early. */
	abstract boolean update(int age);

	/** Runs this layer at {@code age} for a wrapper layer that owns the timing (fallbacks, large-layer slots). */
	final boolean drive(int age) {
		this.age = age;
		if (age % 20 == 0) {
			secondSpent = 0;
		}
		return update(age);
	}

	/** Called once when the layer ends or is cut (displays remove themselves). */
	void end() {
	}

	/** Fades the layer out over {@code ticks} (ABORT, followUntil); displays override to shrink their props. */
	void finish(int ticks) {
		// a second cut may shorten the fade but never restarts it (a display would grow back and shrink again)
		int wanted = Math.max(0, ticks);
		endIn = endIn >= 0 ? Math.min(endIn, wanted) : wanted;
	}

	boolean extra() {
		return priority == FxBudget.Priority.EXTRA;
	}

	/**
	 * Particles to draw this tick: {@link #planned} spread evenly over {@code spreadTicks} (the burst), then the sustain
	 * trickle, never below the template's minimum density while lasting.
	 */
	int quota(int spreadTicks) {
		int spread = Math.max(1, Math.min(spreadTicks, burst));
		float want;
		if (age < spread) {
			want = planned / spread;
		} else if (sustainPerTick > 0.0F) {
			want = sustainPerTick;
		} else if (lasting && age < life) {
			want = planned / Math.max(1, life);
		} else {
			return 0;
		}
		if (lasting) {
			want = Math.max(want, minPerTick);
		}
		if (endIn >= 0) {
			want *= 0.5F;
		}
		carry += want;
		int n = (int) carry;
		carry -= n;
		return n;
	}

	/** Adds one particle when the budget, the event cap and the accent share allow; false when refused. */
	boolean emit(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		if (lasting ? secondSpent >= FxBudget.perEvent() : ev.particlesLeft <= 0) {
			return false;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return false;
		}
		int own = Qi.lifeOf(particle);
		int cost = FxBudget.cost(own > 0 ? own : lifetime(particle.getType()));
		boolean rod = particle.getType() == ParticleTypes.END_ROD || particle.getType() == ParticleTypes.TOTEM_OF_UNDYING;
		if (rod && rodSpent + cost > Math.max(2.0F, 0.15F * planned * 2.0F)) {
			return false;
		}
		int granted = FxBudget.take(cost, priority);
		if (granted < cost) {
			FxBudget.refund(granted);
			return false;
		}
		if (rod) {
			rodSpent += cost;
		}
		if (lasting) {
			secondSpent++;
		} else {
			ev.particlesLeft--;
		}
		level.addParticle(particle, true, false, x, y, z, vx, vy, vz);
		return true;
	}

	boolean emit(ParticleOptions particle, Vec3 at, double vx, double vy, double vz) {
		return emit(particle, at.x, at.y, at.z, vx, vy, vz);
	}

	/** Rough lifetimes (ticks) of the verified vanilla particles, for the token cost. */
	static int lifetime(ParticleType<?> type) {
		if (type == ParticleTypes.END_ROD || type == ParticleTypes.GLOW || type == ParticleTypes.TOTEM_OF_UNDYING
				|| type == ParticleTypes.WHITE_ASH || type == ParticleTypes.ASH || type == ParticleTypes.CHERRY_LEAVES
				|| type == ParticleTypes.FALLING_DUST) {
			return 60;
		}
		if (type == ParticleTypes.SNOWFLAKE || type == ParticleTypes.ENCHANT || type == ParticleTypes.PORTAL
				|| type == ParticleTypes.REVERSE_PORTAL || type == ParticleTypes.SCULK_SOUL || type == ParticleTypes.LARGE_SMOKE
				|| type == ParticleTypes.TRIAL_OMEN || type == ParticleTypes.FIREWORK) {
			return 40;
		}
		return 20;
	}

	// ================================================================== geometry helpers

	/** Where this layer is attached: FOLLOW events turn POS layers into CASTER ones (S9). */
	FxLayer.Attach attach() {
		FxLayer.Attach attach = layer.attach();
		if (attach == FxLayer.Attach.POS && ev.follow()) {
			return FxLayer.Attach.CASTER;
		}
		if (layer.atHand()) {
			return FxLayer.Attach.CASTER;
		}
		return attach;
	}

	/** The layer's anchor point (attach POS, TARGET, CASTER or SKY), raised by its offset. */
	Vec3 anchor() {
		double lift = layer.offsetY();
		if (layer.atHand()) {
			return ev.hand().add(0.0D, lift, 0.0D);
		}
		return switch (attach()) {
			case TARGET -> targetPoint().add(0.0D, lift, 0.0D);
			case CASTER -> ev.casterPos().add(0.0D, lift, 0.0D);
			case SKY -> ev.pos.add(0.0D, lift > 0.0F ? lift : 30.0D, 0.0D);
			default -> ev.pos.add(0.0D, lift, 0.0D);
		};
	}

	/** The second point: the target entity's middle while it lives (v2 target id), else the event's point. */
	Vec3 targetPoint() {
		net.minecraft.world.entity.Entity entity = ev.targetEntity();
		if (entity != null && entity.isAlive()) {
			return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
		}
		// v2 HIT: pos is the victim's middle and the second point the caster's hand
		if (ev.phase == Phase.HIT || ev.phase == Phase.MASS_HIT) {
			return ev.pos;
		}
		return ev.target;
	}

	/** The start of a line layer for this viewer (the hand in one's own first person view, S9). */
	Vec3 lineStart() {
		Vec3 a = anchor();
		return attach() == FxLayer.Attach.LINE || layer.attach() == FxLayer.Attach.POS ? ev.handStart(a) : a;
	}

	/** The far end of a line layer: the target, or {@code length} blocks along the direction. */
	Vec3 lineEnd(Vec3 start) {
		if (ev.hasTarget) {
			return targetPoint().add(0.0D, layer.offsetY(), 0.0D);
		}
		return start.add(fwd.scale(length(8.0F)));
	}

	/** True for templates whose {@code size ≥ 3} means a bigger shape, not bigger motes (S14). */
	private boolean sizeIsGeometry() {
		if (layer.size() < 3.0F) {
			return false;
		}
		return switch (layer.template()) {
			case SHOCK_RING, SHELL, RUNE_CIRCLE, MIST, CRESCENT_FLY, CONE_SPRAY, DOME, CLOUD_DISC, VORTEX, IMPACT -> true;
			default -> false;
		};
	}

	/** The layer's size factor for motes (≤ 2 when the size scales the geometry). */
	float sizeFactor() {
		return sizeIsGeometry() ? 1.0F : Math.min(4.0F, layer.size());
	}

	/** The layer's radius: its own (× size factor), else the event's, else {@code fallback}; × the geometry size (S14). */
	float radius(float fallback) {
		float r;
		if (layer.radius() > 0.0F) {
			r = layer.radius() * ev.sizeF;
		} else {
			r = ev.radius > 0.0F ? ev.radius : fallback * ev.sizeF;
		}
		if (sizeIsGeometry()) {
			r *= layer.size() / 4.0F;
		}
		return r * tierScale;
	}

	/** The layer's length: its own (× size factor), else the event's, else the span between the points. */
	float length(float fallback) {
		if (layer.length() > 0.0F) {
			return layer.length() * ev.sizeF;
		}
		if (ev.length > 0.0F) {
			return ev.length;
		}
		return ev.hasTarget ? (float) ev.span() : fallback * ev.sizeF;
	}

	/** The layer's width (× size factor), else {@code fallback}. */
	float width(float fallback) {
		return layer.width() > 0.0F ? layer.width() * ev.sizeF : fallback;
	}

	/**
	 * A mote width in blocks for a shape of radius {@code r} (style guide: 0.25 + 0.035 × radius, clamped 0.25-2.5),
	 * × the layer size and {@code k}.
	 */
	float moteSize(float r, float k) {
		float base = Math.max(0.25F, Math.min(2.5F, 0.25F + 0.035F * r));
		return Math.max(0.06F, Math.min(2.5F, base * k * sizeFactor() * Math.min(1.4F, ev.sizeF) * (0.75F + 0.25F * tierScale)));
	}

	/** The old dust scale (kept for the Minimal stand-ins of some templates). */
	float dustSize(float base) {
		return Math.max(0.15F, Math.min(4.0F, base * sizeFactor() * Math.min(1.6F, ev.sizeF)));
	}

	/** S4: a count factor from the shape's size against a reference (1-6). */
	static float geometry(double measure, double reference) {
		return (float) Math.max(1.0D, Math.min(6.0D, measure / Math.max(0.01D, reference)));
	}

	/** A point on the unit sphere. */
	Vec3 sphere() {
		double u = rnd.nextDouble() * 2.0D - 1.0D;
		double a = rnd.nextDouble() * Math.PI * 2.0D;
		double s = Math.sqrt(1.0D - u * u);
		return new Vec3(Math.cos(a) * s, u, Math.sin(a) * s);
	}

	/** {@code v} turned around the vertical axis by {@code radians}. */
	static Vec3 yaw(Vec3 v, double radians) {
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	/** The first solid block top at or below {@code at} (≤ 8 blocks, read only), else {@code at.y}. */
	static double groundY(Vec3 at) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return at.y;
		}
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos(at.x, at.y + 0.5D, at.z);
		for (int i = 0; i < 9; i++) {
			if (!level.isLoaded(pos)) {
				return at.y;
			}
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos.getY() + 1.0D;
			}
			pos.move(0, -1, 0);
		}
		return at.y;
	}

	/** The block under {@code at} (for dust and crumbs), or air. */
	static net.minecraft.world.level.block.state.BlockState groundBlock(Vec3 at) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		}
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at.x, groundY(at) - 0.5D, at.z);
		return level.isLoaded(pos) ? level.getBlockState(pos) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
	}
}
