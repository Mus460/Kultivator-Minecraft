package dev.kultivator.client.fx.qi;

import dev.kultivator.fx.QiParticleOptions;
import dev.kultivator.fx.QiParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The emissive qi particle (polish pass S5). A translucent {@link SingleQuadParticle} (verified 26.3:
 * {@code SingleQuadParticle.Layer.TRANSLUCENT}, client/particle/SingleQuadParticle.java:181) that keeps the exact velocity
 * and lifetime it was given (no vanilla re-randomising), ramps its colour core → body → rim and its size in (10%), hold,
 * out, and fades its alpha out over the last third. GLOW particles are full bright ({@code getLightCoords} override,
 * client/particle/Particle.java:185). Streaks turn along their screen-space velocity; ground rings lie flat. Each qi type
 * has its own sprite set and physics (shards fall and bounce, smoke rises and swells, snow and petals drift).
 */
public final class QiParticle extends SingleQuadParticle {

	/** Per-type behaviour (the particle type picks it). */
	enum Kind { MOTE, SPARK, SMOKE, RING, FLAME, SHARD, PETAL, SNOW, FEATHER }

	private final Kind kind;
	private final QiParticleOptions options;
	private final SpriteSet sprites;
	private final float startSize;
	private final float endSize;
	private final float rc0, gc0, bc0;
	private final float rc1, gc1, bc1;
	private final float rc2, gc2, bc2;
	private final boolean glow;
	private final boolean fadeIn;
	private final float spinSpeed;
	private final float flatAngle;
	private final float maxAlpha;

	QiParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, QiParticleOptions options, SpriteSet sprites,
			Kind kind, RandomSource random) {
		super(level, x, y, z, sprites.first());
		this.kind = kind;
		this.options = options;
		this.sprites = sprites;
		boolean own = options.vx() != 0.0F || options.vy() != 0.0F || options.vz() != 0.0F;
		// the exact velocity: the options' own when set, else the spawn velocity (Particle's 7-arg constructor would randomise it)
		this.xd = own ? options.vx() : vx;
		this.yd = own ? options.vy() : vy;
		this.zd = own ? options.vz() : vz;
		this.lifetime = Math.max(1, options.life());
		this.startSize = options.size();
		this.endSize = options.endSize();
		this.quadSize = startSize * 0.1F;
		this.glow = options.has(QiParticleOptions.GLOW);
		this.fadeIn = options.has(QiParticleOptions.FADE_IN);
		this.hasPhysics = kind == Kind.SHARD || kind == Kind.SNOW || kind == Kind.PETAL || kind == Kind.FEATHER;
		this.friction = options.has(QiParticleOptions.DRAG) ? 0.84F : switch (kind) {
			case SMOKE -> 0.92F;
			case SPARK -> 0.9F;
			case FLAME -> 0.94F;
			case PETAL, FEATHER, SNOW -> 0.96F;
			default -> 0.97F;
		};
		this.gravity = options.has(QiParticleOptions.NO_GRAVITY) ? 0.0F : switch (kind) {
			case SHARD -> 0.9F;
			case SNOW -> 0.06F;
			case PETAL -> 0.05F;
			case FEATHER -> 0.035F;
			case SMOKE -> -0.035F;
			case FLAME -> -0.05F;
			default -> 0.0F;
		};
		float[] core = rgb(options.core());
		float[] body = rgb(options.body());
		float[] rim = rgb(options.rim());
		rc0 = core[0];
		gc0 = core[1];
		bc0 = core[2];
		rc1 = body[0];
		gc1 = body[1];
		bc1 = body[2];
		rc2 = rim[0];
		gc2 = rim[1];
		bc2 = rim[2];
		this.rCol = rc0;
		this.gCol = gc0;
		this.bCol = bc0;
		this.maxAlpha = kind == Kind.SMOKE && !glow ? 0.72F : options.has(QiParticleOptions.ADDITIVE_LOOK) ? 1.0F : 0.92F;
		this.alpha = fadeIn ? 0.0F : maxAlpha;
		boolean spins = options.has(QiParticleOptions.SPIN) || kind == Kind.SHARD || kind == Kind.PETAL || kind == Kind.FEATHER || kind == Kind.SNOW;
		this.spinSpeed = spins ? (random.nextFloat() - 0.5F) * (kind == Kind.SHARD ? 0.5F : 0.16F) : 0.0F;
		this.roll = spins ? random.nextFloat() * Mth.TWO_PI : 0.0F;
		this.oRoll = roll;
		this.flatAngle = random.nextFloat() * Mth.TWO_PI;
		pickSprite(random);
	}

	private void pickSprite(RandomSource random) {
		switch (kind) {
			case MOTE -> setSprite(options.has(QiParticleOptions.STAR) ? sprites.get(1, 1) : sprites.get(0, 1));
			case FLAME -> setSprite(sprites.get(0, lifetime));
			default -> setSprite(sprites.get(random));
		}
	}

	private static float[] rgb(int rgb) {
		return new float[]{((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F};
	}

	@Override
	public void tick() {
		super.tick();
		if (removed) {
			return;
		}
		float t = Mth.clamp(age / (float) lifetime, 0.0F, 1.0F);
		// colour ramp: core for the first quarter, then body, then rim
		if (t < 0.25F) {
			float k = t / 0.25F;
			setColor(Mth.lerp(k, rc0, rc1), Mth.lerp(k, gc0, gc1), Mth.lerp(k, bc0, bc1));
		} else {
			float k = Mth.clamp((t - 0.25F) / 0.75F, 0.0F, 1.0F);
			k = k * k;
			setColor(Mth.lerp(k, rc1, rc2), Mth.lerp(k, gc1, gc2), Mth.lerp(k, bc1, bc2));
		}
		float in = fadeIn ? 0.33F : 0.1F;
		float a = t < in ? t / in : t > 0.65F ? 1.0F - (t - 0.65F) / 0.35F : 1.0F;
		this.alpha = Mth.clamp(a, 0.0F, 1.0F) * maxAlpha;
		oRoll = roll;
		roll += spinSpeed;
		if (kind == Kind.FLAME) {
			setSpriteFromAge(sprites);
		}
		if ((kind == Kind.PETAL || kind == Kind.FEATHER) && !onGround) {
			// a lazy sideways sway while drifting down
			xd += Mth.sin(age * 0.21F + flatAngle) * 0.004F;
			zd += Mth.cos(age * 0.17F + flatAngle) * 0.004F;
		}
		if (kind == Kind.SHARD && onGround) {
			spinTo0();
		}
	}

	private void spinTo0() {
		xd *= 0.6D;
		zd *= 0.6D;
	}

	@Override
	public float getQuadSize(float partial) {
		float t = Mth.clamp((age + partial) / lifetime, 0.0F, 1.0F);
		float grow = fadeIn ? Math.min(1.0F, t / 0.3F) : Math.min(1.0F, t / 0.1F);
		float size;
		if (t < 0.1F && !fadeIn) {
			// snap in with a slight overshoot: a burst reads as a pop
			size = startSize * (0.4F + 0.6F * grow) * (1.0F + 0.15F * Mth.sin(grow * Mth.PI));
		} else {
			float out = Mth.clamp((t - 0.1F) / 0.9F, 0.0F, 1.0F);
			size = Mth.lerp(out * out * (3.0F - 2.0F * out), startSize, endSize) * (fadeIn ? grow : 1.0F);
		}
		// quadSize is the half-extent in blocks; option sizes are full widths in blocks
		return Math.max(0.005F, size * 0.5F);
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
		if (kind == Kind.RING && options.has(QiParticleOptions.GROUND_HUG)) {
			// flat on the ground, turning slowly (the quad's local XY plane laid into XZ)
			Quaternionf flat = new Quaternionf().rotateY(flatAngle + Mth.lerp(partial, oRoll, roll)).rotateX(-Mth.HALF_PI);
			extractRotatedQuad(state, camera, flat, partial);
			return;
		}
		if (kind == Kind.SPARK) {
			// align the streak (sprite x axis) with the velocity as seen on screen
			Quaternionf rotation = new Quaternionf(camera.rotation());
			Vector3f v = new Vector3f((float) xd, (float) yd, (float) zd);
			if (v.lengthSquared() > 1.0E-8F) {
				Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();
				inverse.transform(v);
				float angle = (float) Math.atan2(v.y, v.x);
				rotation.rotateZ(angle);
			}
			extractRotatedQuad(state, camera, rotation, partial);
			return;
		}
		super.extract(state, camera, partial);
	}

	/** One provider per qi type. */
	static final class Provider implements ParticleProvider<QiParticleOptions> {
		private final SpriteSet sprites;
		private final Kind kind;

		Provider(SpriteSet sprites, Kind kind) {
			this.sprites = sprites;
			this.kind = kind;
		}

		@Override
		public @Nullable Particle createParticle(QiParticleOptions options, ClientLevel level, double x, double y, double z, double xAux, double yAux,
				double zAux, RandomSource random) {
			return new QiParticle(level, x, y, z, xAux, yAux, zAux, options, sprites, kind, random);
		}
	}

	/** The behaviour of a qi type. */
	static Kind kindOf(ParticleType<QiParticleOptions> type) {
		if (type == QiParticles.QI_SPARK) {
			return Kind.SPARK;
		}
		if (type == QiParticles.QI_SMOKE) {
			return Kind.SMOKE;
		}
		if (type == QiParticles.QI_RING) {
			return Kind.RING;
		}
		if (type == QiParticles.QI_FLAME) {
			return Kind.FLAME;
		}
		if (type == QiParticles.QI_SHARD) {
			return Kind.SHARD;
		}
		if (type == QiParticles.QI_PETAL) {
			return Kind.PETAL;
		}
		if (type == QiParticles.QI_SNOW) {
			return Kind.SNOW;
		}
		if (type == QiParticles.QI_FEATHER) {
			return Kind.FEATHER;
		}
		return Kind.MOTE;
	}
}
