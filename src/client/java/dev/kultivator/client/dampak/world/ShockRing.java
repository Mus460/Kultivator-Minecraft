package dev.kultivator.client.dampak.world;

import dev.kultivator.client.epic.EpicBudget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gelombang Kejut (DESIGN 28.5): an expanding ground ripple following the terrain: a soft annulus
 * ({@code ring_soft.png}, {@code entityTranslucentEmissive}) of 32 / 48 / 64 segments whose every vertex sits on the
 * client heightmap + 0.04, growing 0 → R over 12 ticks (easeOutCubic) and fading, with a dust wall at the front (the ground
 * block's {@code BLOCK} particles, {@code DUST_PILLAR} at Normal and up, 8 / 24 / 48 per tick through the budget) and a
 * second, fainter ring 8 ticks later to 1.4 R. A ring wider than 16 b takes a large layer. Variants: DUST, EMBER (flame
 * front), FROST (snow front and packed-ice spikes rising along it, staying 10 s), VOID (contracts to 0.3 R first, then
 * bursts out), GOLD (glow motes). Minimal: a particle ring.
 */
final class ShockRing {

	static final int GROW = 12;
	static final int SECOND_DELAY = 8;
	private static final int VOID_IN = 6;
	private static final int FADE = 10;
	private static final int SPIKE_LIFE = 200;

	private static final List<Ring> RINGS = new ArrayList<>();

	private static final class Ring {
		final Vec3 centre;
		final float radius;
		final String variant;
		final int rgb;
		final float alpha;
		final boolean own;
		final double start;
		final boolean second;
		final boolean dust;
		boolean spikesDone;
		int lastParticleTick = -1;

		Ring(Vec3 centre, float radius, String variant, int rgb, float alpha, boolean own, double start, boolean second, boolean dust) {
			this.centre = centre;
			this.radius = radius;
			this.variant = variant;
			this.rgb = rgb;
			this.alpha = alpha;
			this.own = own;
			this.start = start;
			this.second = second;
			this.dust = dust;
		}

		int growTicks() {
			return "VOID".equals(variant) ? GROW + VOID_IN : GROW;
		}

		int life() {
			return growTicks() + FADE;
		}

		/** The radius at presentation age {@code age}. */
		float radiusAt(float age) {
			if ("VOID".equals(variant)) {
				if (age < VOID_IN) {
					float t = age / VOID_IN;
					return radius * (0.8F - 0.5F * t * t);
				}
				float t = Math.min(1.0F, (age - VOID_IN) / GROW);
				return radius * (0.3F + 0.7F * cubic(t));
			}
			return radius * cubic(Math.min(1.0F, age / GROW));
		}

		float alphaAt(float age) {
			if (age < 0.0F) {
				return 0.0F;
			}
			float in = Math.min(1.0F, age / 2.0F);
			float out = age <= growTicks() ? 1.0F : 1.0F - Math.min(1.0F, (age - growTicks()) / FADE);
			return alpha * in * out;
		}
	}

	// ================================================================== API

	/**
	 * Starts a ring (and its second ring 8 ticks later) of radius {@code r} at {@code centre}. {@code variant} is a
	 * {@code Dampak.Ring} name (null = DUST); {@code rgb} tints the band.
	 */
	static void start(Vec3 centre, float r, String variant, int rgb, boolean own, int delay, boolean twin) {
		if (centre == null || !Double.isFinite(centre.x) || !Double.isFinite(centre.y) || !Double.isFinite(centre.z) || !(r > 0.5F)) {
			return;
		}
		float othersAlpha = own ? 1.0F : Math.max(0.35F, DampakWorldBudget.othersFactor(false));
		if (DampakWorldBudget.othersFactor(own) <= 0.0F) {
			return;
		}
		String kind = variant == null ? "DUST" : variant;
		float radius = Math.min(48.0F, r * DampakWorldBudget.ringScale());
		double now = DampakWorld.clock(0.0F) + Math.max(0, delay);
		add(new Ring(centre, radius, kind, rgb, 0.9F * othersAlpha, own, now, false, true));
		if (twin) {
			add(new Ring(centre, Math.min(64.0F, radius * 1.4F), kind, rgb, 0.45F * othersAlpha, own, now + SECOND_DELAY, true, false));
		}
		DampakWorld.touchGeometry();
	}

	private static void add(Ring ring) {
		if (RINGS.size() >= DampakWorldBudget.RINGS_ALIVE) {
			RINGS.remove(0);
		}
		RINGS.add(ring);
	}

	static boolean active() {
		return !RINGS.isEmpty();
	}

	static int count() {
		return RINGS.size();
	}

	static void clear() {
		RINGS.clear();
	}

	/** The default band colour of a variant. */
	static int variantColor(String variant, int fallback) {
		if (variant == null) {
			return fallback;
		}
		return switch (variant) {
			case "EMBER" -> 0xFF7A2A;
			case "FROST" -> 0xCFF4FF;
			case "VOID" -> 0x8A5CFF;
			case "GOLD" -> 0xFFD878;
			default -> fallback;
		};
	}

	// ================================================================== tick: fronts, particles, spikes

	static void tick(ClientLevel level, double clock) {
		boolean minimal = DampakWorldBudget.tier() <= 0;
		for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
			Ring ring = it.next();
			float age = (float) (clock - ring.start);
			if (age > ring.life()) {
				it.remove();
				continue;
			}
			if (age < 0.0F) {
				continue;
			}
			int tick = (int) age;
			if (tick == ring.lastParticleTick) {
				continue;
			}
			ring.lastParticleTick = tick;
			float r = ring.radiusAt(age);
			if (minimal && tick % 2 == 0) {
				particleRing(level, ring, r);
			}
			if (age <= ring.growTicks() + 2) {
				front(level, ring, r);
			}
			if ("FROST".equals(ring.variant) && !ring.second && !ring.spikesDone && age >= ring.growTicks() * 0.6F) {
				ring.spikesDone = true;
				spikes(level, ring);
			}
		}
	}

	/** Minimal (and the geometry fallback): a dust particle ring. */
	private static void particleRing(ClientLevel level, Ring ring, float r) {
		int n = DampakParticles.grant(Math.min(48, 12 + Math.round(r * 2.0F)));
		ParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(ring.rgb & 0xFFFFFF, 1.4F);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n);
			double x = ring.centre.x + Math.cos(angle) * r;
			double z = ring.centre.z + Math.sin(angle) * r;
			double y = DampakDisplays.ground(level, x, z, ring.centre.y) + 0.1D;
			DampakParticles.one(dust, x, y, z, Math.cos(angle) * 0.05D, 0.0D, Math.sin(angle) * 0.05D);
		}
	}

	/** The front's dust wall (or the variant's front) this tick. */
	private static void front(ClientLevel level, Ring ring, float r) {
		if (!ring.dust || r < 0.5F) {
			return;
		}
		int index = DampakWorldBudget.index();
		int wanted = DampakWorldBudget.scaled(DampakWorldBudget.RING_DUST[index], ring.own, DampakWorldBudget.RING_DUST[index]);
		int n = DampakParticles.grant(wanted);
		RandomSource random = DampakParticles.random();
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double x = ring.centre.x + Math.cos(angle) * r;
			double z = ring.centre.z + Math.sin(angle) * r;
			double y = DampakDisplays.ground(level, x, z, ring.centre.y);
			double ox = Math.cos(angle) * 0.12D;
			double oz = Math.sin(angle) * 0.12D;
			ParticleOptions particle = switch (ring.variant) {
				case "EMBER" -> i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
				case "FROST" -> i % 2 == 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.ITEM_SNOWBALL;
				case "VOID" -> i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL;
				case "GOLD" -> i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.GLOW;
				default -> {
					BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5D, z));
					yield index >= 1 && i % 3 == 0 ? DampakParticles.pillar(ground) : DampakParticles.block(ground);
				}
			};
			DampakParticles.one(particle, x, y + 0.1D, z, ox, 0.12D + random.nextDouble() * 0.2D, oz);
		}
	}

	/** FROST: packed-ice spikes rising 1-3 b along the front, staying 10 s, then shattering. */
	private static void spikes(ClientLevel level, Ring ring) {
		int index = DampakWorldBudget.index();
		if (!DampakWorldBudget.displays(ring.own, false)) {
			return;
		}
		int n = DampakWorldBudget.scaled(DampakWorldBudget.ICE_SPIKES[index], ring.own, DampakWorldBudget.ICE_SPIKES[index]);
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		RandomSource random = DampakParticles.random();
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n) + random.nextDouble() * 0.2D;
			double r = ring.radius * (0.75D + random.nextDouble() * 0.25D);
			double x = ring.centre.x + Math.cos(angle) * r;
			double z = ring.centre.z + Math.sin(angle) * r;
			double y = DampakDisplays.ground(level, x, z, ring.centre.y);
			Vec3 at = new Vec3(x, y, z);
			float height = 1.0F + random.nextFloat() * 2.0F;
			float width = 0.35F + random.nextFloat() * 0.3F;
			Display.BlockDisplay display = DampakDisplays.create(ice, at, height, false);
			if (display == null) {
				break;
			}
			// leaning outward a little (about the horizontal axis across the radius)
			Quaternionf tilt = new Quaternionf().rotateAxis((float) Math.toRadians(8.0D + random.nextDouble() * 14.0D), (float) Math.sin(angle), 0.0F,
					(float) -Math.cos(angle));
			Spike spike = new Spike(display, tilt, width, height);
			spike.poseBase(0.05F, true, 0);
			DampakDisplays.add(spike);
		}
	}

	/** One ice spike of a FROST ring: rises over 4 ticks, stays, then shatters into its own particles. */
	private static final class Spike extends DampakDisplays.Piece {
		private final Quaternionf tilt;
		private final float width;
		private final float height;
		private boolean shattered;

		Spike(Display.BlockDisplay display, Quaternionf tilt, float width, float height) {
			super(display, DampakDisplays.SPIKE, 0L);
			this.tilt = tilt;
			this.width = width;
			this.height = height;
		}

		@Override
		boolean tick(ClientLevel level, float sigma, boolean keyNow, int duration) {
			if (age >= SPIKE_LIFE) {
				if (!shattered) {
					shattered = true;
					crumble(level, 10);
					DampakParticles.burst(level, DampakParticles.block(display.getBlockState()), display.position().add(0.0D, height * 0.5D, 0.0D), 8, 0.12F);
				}
				return false;
			}
			if (age <= 6.0F && keyNow) {
				float t = Math.min(1.0F, (age + duration) / 4.0F);
				poseBase(cubic(t) * height, false, duration);
			}
			return true;
		}

		/** Poses the spike with its bottom centre on the entity position (sunk 0.15 b), tilted, {@code h} tall. */
		void poseBase(float h, boolean snap, int duration) {
			Quaternionf rotation = new Quaternionf(tilt);
			org.joml.Vector3f bottom = new org.joml.Vector3f(width * 0.5F, 0.0F, width * 0.5F);
			rotation.transform(bottom);
			org.joml.Vector3f translation = new org.joml.Vector3f(-bottom.x, -bottom.y - 0.15F, -bottom.z);
			org.joml.Vector3f scale = new org.joml.Vector3f(width, Math.max(0.05F, h), width);
			if (snap) {
				key.snap(translation, rotation, scale);
			} else {
				key.key(translation, rotation, scale, duration);
			}
		}
	}

	// ================================================================== render

	static void render(SigilRenderer.Frame frame) {
		int index = DampakWorldBudget.index();
		if (DampakWorldBudget.tier() <= 0) {
			return;
		}
		int segments = DampakWorldBudget.RING_SEGMENTS[index];
		for (int k = 0; k < RINGS.size(); k++) {
			Ring ring = RINGS.get(k);
			float age = (float) (frame.clock - ring.start);
			float a = ring.alphaAt(age);
			if (a <= 0.01F) {
				continue;
			}
			float r = ring.radiusAt(age);
			if (r < 0.3F || frame.dist2(ring.centre.x, ring.centre.y, ring.centre.z) > (r + 96.0D) * (r + 96.0D)) {
				continue;
			}
			if (r > 16.0F && !EpicBudget.takeLarge()) {
				continue;
			}
			int n = r < 6.0F ? Math.max(16, segments / 2) : segments;
			if (!EpicBudget.takeQuads(n)) {
				continue;
			}
			float width = Math.max(0.8F, Math.min(4.0F, r * 0.22F));
			float inner = Math.max(0.0F, r - width);
			int outerColor = ARGB.color(Math.round(a * 255.0F), ring.rgb);
			int innerColor = ARGB.color(Math.round(a * 90.0F), ring.rgb);
			annulus(frame, ring, inner, r, n, innerColor, outerColor);
		}
	}

	/** The terrain-following annulus: every vertex on the client heightmap + 0.04. */
	private static void annulus(SigilRenderer.Frame frame, Ring ring, float inner, float outer, int segments, int innerColor, int outerColor) {
		double cx = ring.centre.x;
		double cz = ring.centre.z;
		double fallback = ring.centre.y;
		float step = (float) (Math.PI * 2.0D / segments);
		float uScale = Math.max(1.0F, Math.round(outer / 4.0F));
		float uOffset = (float) (frame.clock * 0.01D);
		double c0 = 1.0D;
		double s0 = 0.0D;
		float yi0 = frame.ry(frame.groundY(cx + c0 * inner, cz + s0 * inner, fallback));
		float yo0 = frame.ry(frame.groundY(cx + c0 * outer, cz + s0 * outer, fallback));
		for (int i = 0; i < segments; i++) {
			double angle = (i + 1) * step;
			double c1 = Math.cos(angle);
			double s1 = Math.sin(angle);
			float yi1 = frame.ry(frame.groundY(cx + c1 * inner, cz + s1 * inner, fallback));
			float yo1 = frame.ry(frame.groundY(cx + c1 * outer, cz + s1 * outer, fallback));
			float u0 = i / (float) segments * uScale + uOffset;
			float u1 = (i + 1) / (float) segments * uScale + uOffset;
			SigilRenderer.Sink sink = frame.ring;
			sink.vertex(frame.rx(cx + c0 * inner), yi0, frame.rz(cz + s0 * inner), u0, 0.0F, innerColor, 0.0F, 1.0F, 0.0F);
			sink.vertex(frame.rx(cx + c0 * outer), yo0, frame.rz(cz + s0 * outer), u0, 1.0F, outerColor, 0.0F, 1.0F, 0.0F);
			sink.vertex(frame.rx(cx + c1 * outer), yo1, frame.rz(cz + s1 * outer), u1, 1.0F, outerColor, 0.0F, 1.0F, 0.0F);
			sink.vertex(frame.rx(cx + c1 * inner), yi1, frame.rz(cz + s1 * inner), u1, 0.0F, innerColor, 0.0F, 1.0F, 0.0F);
			c0 = c1;
			s0 = s1;
			yi0 = yi1;
			yo0 = yo1;
		}
	}

	static float cubic(float t) {
		float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	private ShockRing() {
	}
}
