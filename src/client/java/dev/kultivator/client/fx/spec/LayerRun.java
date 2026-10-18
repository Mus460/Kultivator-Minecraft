package dev.kultivator.client.fx.spec;

import dev.kultivator.technique.spec.dsl.FxLayer;
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
 * (by particle lifetime), the event's particle cap and the 15% share END_ROD and TOTEM_OF_UNDYING may use. The random
 * source is seeded from the event, so every client draws the same shape.
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
	/** Particles this layer plans to draw in all (for the END_ROD share). */
	final float planned;
	/** Lasting layers (domains, fields, forms) count the per-event cap per second instead of in all. */
	final boolean lasting;
	private int spent;
	private int rodSpent;
	private int secondSpent;
	private float carry;

	/** Unit vectors around the event direction. */
	final Vec3 fwd;
	final Vec3 right;
	final Vec3 up;
	/** Horizontal forward (never zero). */
	final Vec3 hfwd;

	LayerRun(FxEvent ev, FxLayer layer, int life, int index) {
		this.ev = ev;
		this.layer = layer;
		this.rnd = RandomSource.create(ev.seed * 31L + index * 7919L + layer.template().ordinal());
		this.priority = switch (layer.priority()) {
			case CORE -> FxBudget.Priority.CORE;
			case DETAIL -> FxBudget.Priority.DETAIL;
			case EXTRA -> FxBudget.Priority.EXTRA;
		};
		this.life = Math.max(1, life);
		this.age = -Math.max(0, layer.delay());
		// A lasting look stretched to the event's duration repeats its count once per its own tick window.
		this.lasting = this.life > 40;
		float repeats = Math.max(1.0F, this.life / (float) Math.max(1, layer.ticks()));
		this.planned = Math.max(1.0F, layer.count() * ev.countF * ev.viewScale * repeats);
		this.fwd = ev.dir;
		Vec3 side = fwd.cross(UP);
		this.right = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		this.up = right.cross(fwd).normalize();
		Vec3 flat = new Vec3(fwd.x, 0.0D, fwd.z);
		this.hfwd = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
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
		boolean more = update(age);
		age++;
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

	boolean extra() {
		return priority == FxBudget.Priority.EXTRA;
	}

	/** Particles to draw this tick when {@link #planned} are spread evenly over {@code spreadTicks}. */
	int quota(int spreadTicks) {
		if (age >= spreadTicks) {
			return 0;
		}
		carry += planned / Math.max(1, spreadTicks);
		int n = (int) carry;
		carry -= n;
		return n;
	}

	/** Adds one particle when the budget, the event cap and the END_ROD share allow; false when refused. */
	boolean emit(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		if (lasting ? secondSpent >= FxBudget.perEvent() : ev.particlesLeft <= 0) {
			return false;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		int cost = FxBudget.cost(lifetime(particle.getType()));
		boolean rod = particle.getType() == ParticleTypes.END_ROD || particle.getType() == ParticleTypes.TOTEM_OF_UNDYING;
		if (rod && rodSpent + cost > Math.max(2.0F, 0.15F * planned * 2.0F)) {
			return false;
		}
		if (FxBudget.take(cost, priority) < cost) {
			return false;
		}
		spent += cost;
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

	/** Rough lifetimes (ticks) of the verified particles, for the token cost. */
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

	/** The layer's anchor point (attach POS, TARGET, CASTER or SKY), raised by its offset. */
	Vec3 anchor() {
		double lift = layer.offsetY();
		return switch (layer.attach()) {
			case TARGET -> ev.target.add(0.0D, lift, 0.0D);
			case CASTER -> ev.casterPos().add(0.0D, lift, 0.0D);
			case SKY -> ev.pos.add(0.0D, lift > 0.0F ? lift : 30.0D, 0.0D);
			default -> ev.pos.add(0.0D, lift, 0.0D);
		};
	}

	/** The far end of a line layer: the target, or {@code length} blocks along the direction. */
	Vec3 lineEnd(Vec3 start) {
		if (ev.hasTarget) {
			return ev.target.add(0.0D, layer.offsetY(), 0.0D);
		}
		return start.add(fwd.scale(length(8.0F)));
	}

	/** The layer's radius: its own (× size factor), else the event's, else {@code fallback}. */
	float radius(float fallback) {
		if (layer.radius() > 0.0F) {
			return layer.radius() * ev.sizeF;
		}
		return ev.radius > 0.0F ? ev.radius : fallback * ev.sizeF;
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

	/** A particle size (dust scale) from the layer size, kept within the dust limits. */
	float dustSize(float base) {
		return Math.max(0.15F, Math.min(4.0F, base * layer.size() * Math.min(1.6F, ev.sizeF)));
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
