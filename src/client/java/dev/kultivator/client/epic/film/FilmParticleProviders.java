package dev.kultivator.client.epic.film;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.fx.FilmParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The client side of the Kenney film particles (DESIGN 30.2.11, contract C30.4): one sprite-set provider per
 * {@link FilmParticles} type and the one place that spawns them ({@link #spawn}). The sprites are white (alpha = Kenney
 * alpha × luminance), so a particle takes any colour; glowing types are full bright and translucent like {@code
 * kultivator:qi}. They age by the film clock σ (bullet time and hit-stop hold them), never by vanilla's tick-rate. Every
 * spawn takes {@link FxBudget} tokens (as {@code kultivator:qi}) and the per-shot caps of 30.2.13 (40 / 160 / 400).
 */
public final class FilmParticleProviders {

	/** Per-type behaviour. */
	enum Kind { SPARK, FLARE, STAR, SMOKE, FLAME, MAGIC, SLASH, TRACE, TWIRL, CIRCLE, MUZZLE, DIRT, SYMBOL }

	public static final int[] PER_SHOT = {40, 160, 400};

	private static boolean ready;
	private static int shotSpent;

	/** Registers the providers (once, from {@code EpicClient.init}). */
	public static void init() {
		if (ready) {
			return;
		}
		if (!FilmParticles.registered()) {
			Kultivator.LOGGER.warn("Kultivator: film particle types are not registered; films draw without Kenney particles");
			return;
		}
		Kind[] kinds = Kind.values();
		for (int i = 0; i < FilmParticles.ALL.size(); i++) {
			Kind kind = kinds[i];
			SimpleParticleType type = FilmParticles.ALL.get(i).type();
			ParticleProviderRegistry.getInstance().register(type,
					(ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType>) sprites -> new Provider(sprites, kind));
		}
		ready = true;
	}

	public static boolean ready() {
		return ready;
	}

	/** A new shot starts: its particle allowance resets. */
	public static void newShot() {
		shotSpent = 0;
	}

	/**
	 * Spawns one film particle: {@code size} is its full width in blocks, {@code life} exact ticks. Returns false when the
	 * budget, the per-shot cap or the vanilla "Particles" setting refused it.
	 */
	public static boolean spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity, int rgb, float size, int life) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ready || minecraft.level == null) {
			return false;
		}
		int cap = PER_SHOT[EffectQuality.level().ordinal()];
		if (shotSpent >= cap) {
			return false;
		}
		int option = minecraft.options.particles().get().ordinal();
		if (option == 2 || option == 1 && (shotSpent & 1) == 1) {
			// "Particles: Minimal" draws none of them, "Decreased" every other one
			shotSpent++;
			return false;
		}
		int cost = FxBudget.cost(life);
		if (FxBudget.take(cost, FxBudget.Priority.DETAIL) < cost) {
			return false;
		}
		shotSpent++;
		Particle particle = minecraft.particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		if (particle instanceof FilmParticle film) {
			film.configure(rgb, size, life);
			return true;
		}
		return particle != null;
	}

	/** A ring of {@code n} particles around {@code centre} (radius r, flat), moving outward at {@code speed} b/tick. */
	public static void ring(SimpleParticleType type, Vec3 centre, double r, int n, double speed, int rgb, float size, int life) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0D * i / n;
			double cx = Math.cos(a);
			double cz = Math.sin(a);
			if (!spawn(type, centre.add(cx * r, 0.1D, cz * r), new Vec3(cx * speed, 0.0D, cz * speed), rgb, size, life)) {
				return;
			}
		}
	}

	/** A burst of {@code n} particles from {@code centre} on seeded directions (deterministic per seed). */
	public static void burst(SimpleParticleType type, Vec3 centre, int n, double speed, int rgb, float size, int life, long seed) {
		long mix = seed * 0x9E3779B97F4A7C15L + 0x1234567L;
		for (int i = 0; i < n; i++) {
			mix = mix * 6364136223846793005L + 1442695040888963407L;
			double yaw = ((mix >>> 33) & 0xFFFF) / 65535.0D * Math.PI * 2.0D;
			mix = mix * 6364136223846793005L + 1442695040888963407L;
			double up = ((mix >>> 33) & 0xFFFF) / 65535.0D * 0.9D + 0.1D;
			double flat = Math.sqrt(1.0D - up * up);
			Vec3 dir = new Vec3(Math.cos(yaw) * flat, up, Math.sin(yaw) * flat);
			if (!spawn(type, centre, dir.scale(speed), rgb, size, life)) {
				return;
			}
		}
	}

	// ================================================================ the particle

	static final class FilmParticle extends SingleQuadParticle {
		private final Kind kind;
		private final SpriteSet sprites;
		private float size = 0.5F;
		private float maxAlpha = 0.95F;
		private float spin;
		private float clock;
		private final float phase;
		private final boolean glow;

		FilmParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Kind kind,
					 RandomSource random) {
			super(level, x, y, z, sprites.first());
			this.kind = kind;
			this.sprites = sprites;
			this.xd = vx;
			this.yd = vy;
			this.zd = vz;
			this.lifetime = 20;
			this.glow = kind != Kind.SMOKE && kind != Kind.DIRT;
			this.hasPhysics = kind == Kind.DIRT;
			this.gravity = switch (kind) {
				case DIRT -> 0.9F;
				case SMOKE -> -0.03F;
				case FLAME -> -0.05F;
				case SYMBOL -> -0.01F;
				default -> 0.0F;
			};
			this.friction = switch (kind) {
				case SPARK, TRACE -> 0.9F;
				case SMOKE -> 0.92F;
				case DIRT -> 0.96F;
				default -> 0.95F;
			};
			this.spin = switch (kind) {
				case TWIRL -> 0.25F;
				case MAGIC, STAR -> 0.06F;
				case SMOKE -> 0.02F;
				default -> 0.0F;
			};
			this.roll = kind == Kind.SLASH || kind == Kind.MAGIC || kind == Kind.TWIRL || kind == Kind.SMOKE ? random.nextFloat() * Mth.TWO_PI : 0.0F;
			this.oRoll = roll;
			this.phase = random.nextFloat() * Mth.TWO_PI;
			if (kind == Kind.SMOKE) {
				maxAlpha = 0.6F;
			}
			setSprite(sprites.get(random));
		}

		void configure(int rgb, float width, int life) {
			setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
			this.size = Math.max(0.02F, width);
			this.lifetime = Math.max(2, life);
			this.quadSize = size * 0.5F;
			if (kind == Kind.FLAME) {
				setSpriteFromAge(sprites);
			}
		}

		@Override
		public void tick() {
			// the film clock: bullet time and hit-stop hold the particle (position and age)
			float sigma = EpicClock.frozen() ? 0.0F : EpicClock.sigma();
			clock += sigma;
			if (clock < 1.0F) {
				xo = x;
				yo = y;
				zo = z;
				oRoll = roll;
				return;
			}
			clock -= 1.0F;
			super.tick();
			if (removed) {
				return;
			}
			oRoll = roll;
			roll += spin;
			float t = Mth.clamp(age / (float) lifetime, 0.0F, 1.0F);
			float a;
			switch (kind) {
				case STAR -> a = (0.55F + 0.45F * Mth.sin(age * 0.6F + phase)) * (1.0F - t * t);
				case MUZZLE, SLASH -> a = 1.0F - t;
				case CIRCLE -> a = (1.0F - t) * (1.0F - t);
				default -> a = t < 0.1F ? t / 0.1F : t > 0.6F ? 1.0F - (t - 0.6F) / 0.4F : 1.0F;
			}
			alpha = Mth.clamp(a, 0.0F, 1.0F) * maxAlpha;
			if (kind == Kind.FLAME) {
				setSpriteFromAge(sprites);
			}
		}

		@Override
		public float getQuadSize(float partial) {
			float t = Mth.clamp((age + partial) / lifetime, 0.0F, 1.0F);
			float k = switch (kind) {
				case CIRCLE -> 0.3F + 0.7F * Mth.sqrt(t) * 1.4F;
				case SMOKE -> 0.7F + 0.6F * t;
				case FLARE, MUZZLE -> t < 0.15F ? 0.5F + t / 0.15F * 0.5F : 1.0F - (t - 0.15F) * 0.4F;
				case SPARK, TRACE, SLASH -> 1.0F - t * 0.5F;
				case FLAME -> 1.0F - t * 0.6F;
				default -> 1.0F;
			};
			return Math.max(0.01F, size * 0.5F * k);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partial) {
			return glow ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partial);
		}

		@Override
		public void extract(QuadParticleRenderState state, Camera camera, float partial) {
			if (kind == Kind.SPARK || kind == Kind.TRACE) {
				// the streak along its screen-space velocity
				Quaternionf rotation = new Quaternionf(camera.rotation());
				Vector3f v = new Vector3f((float) xd, (float) yd, (float) zd);
				if (v.lengthSquared() > 1.0E-8F) {
					Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();
					inverse.transform(v);
					rotation.rotateZ((float) Math.atan2(v.y, v.x) + (kind == Kind.TRACE ? Mth.HALF_PI : 0.0F));
				}
				extractRotatedQuad(state, camera, rotation, partial);
				return;
			}
			super.extract(state, camera, partial);
		}
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;
		private final Kind kind;

		Provider(SpriteSet sprites, Kind kind) {
			this.sprites = sprites;
			this.kind = kind;
		}

		@Override
		public @Nullable Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux,
				double zAux, RandomSource random) {
			return new FilmParticle(level, x, y, z, xAux, yAux, zAux, sprites, kind, random);
		}
	}

	private FilmParticleProviders() {
	}
}
