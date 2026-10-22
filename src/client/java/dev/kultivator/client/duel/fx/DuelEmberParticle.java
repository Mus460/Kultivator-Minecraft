package dev.kultivator.client.duel.fx;

import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.fx.DuelParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code kultivator:duel_ember} (DESIGN 35.4.2): a small glowing ember shed by a hard clash. It pops out a little, then
 * drifts down slowly with a lazy sideways wander, flickers and dims from orange to a dull red. Ages by the presentation
 * clock σ like the sparks (frozen by a hit-stop), no collision (it is too slow to need any), full bright.
 */
public final class DuelEmberParticle extends SingleQuadParticle {

	private final SpriteSet sprites;
	private float ageF;
	private float lifeF = 30.0F;
	private float size = 0.06F;
	private final float phase;
	private final float wander;
	private int rgb = 0xFFA040;
	private int group;
	private float fadeFrom = -1.0F;

	DuelEmberParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		this.hasPhysics = false;
		this.lifetime = 40;
		this.phase = random.nextFloat() * Mth.TWO_PI;
		this.wander = 0.004F + random.nextFloat() * 0.006F;
		setSprite(sprites.get(random));
	}

	void configure(float width, float life, int colour, int predictionGroup) {
		this.size = Math.max(0.02F, width);
		this.lifeF = Math.max(4.0F, life);
		this.lifetime = Math.max(4, Math.round(life * 4.0F));
		this.rgb = colour & 0xFFFFFF;
		this.group = predictionGroup;
		this.quadSize = size * 0.5F;
		tint(0.0F);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		oRoll = roll;
		if (removed) {
			return;
		}
		if (group != 0 && fadeFrom < 0.0F && DuelFx.groupCancelled(group)) {
			fadeFrom = ageF;
			lifeF = Math.min(lifeF, ageF + 2.0F);
		}
		float s = DuelTime.frozen() ? 0.0F : DuelTime.sigma();
		if (s <= 0.0F) {
			return;
		}
		ageF += s;
		if (ageF >= lifeF) {
			remove();
			return;
		}
		// pop, then a slow fall toward a terminal 0.03 b/tick, with a sideways wander
		yd = yd * Math.pow(0.86D, s) - 0.004D * s;
		yd = Math.max(yd, -0.03D);
		xd = xd * Math.pow(0.88D, s) + Math.sin(ageF * 0.35F + phase) * wander * s;
		zd = zd * Math.pow(0.88D, s) + Math.cos(ageF * 0.29F + phase) * wander * s;
		x += xd * s;
		y += yd * s;
		z += zd * s;
		float t = ageF / lifeF;
		tint(t);
		float flicker = 0.7F + 0.3F * Mth.sin(ageF * 1.7F + phase);
		alpha = flicker * (t < 0.75F ? 1.0F : Math.max(0.0F, 1.0F - (t - 0.75F) / 0.25F));
		if (fadeFrom >= 0.0F) {
			alpha *= Math.max(0.0F, 1.0F - (ageF - fadeFrom) / 2.0F);
		}
	}

	private void tint(float t) {
		int colour = DuelGeo.mix(DuelGeo.mix(rgb, 0xFFE0A0, 0.4F), 0x701808, t);
		setColor(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F, (colour & 0xFF) / 255.0F);
	}

	@Override
	public float getQuadSize(float partial) {
		float t = Mth.clamp(ageF / lifeF, 0.0F, 1.0F);
		return size * 0.5F * (1.0F - 0.5F * t);
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partial) {
		return LightCoordsUtil.FULL_BRIGHT;
	}

	// ================================================================ spawning

	private static boolean ready;

	static void markReady() {
		ready = true;
	}

	/** Spawns one ember (the caller applied the caps); false when refused. */
	static boolean spawn(Vec3 pos, Vec3 velocity, float width, float life, int colour, int group) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ready || minecraft.level == null) {
			return false;
		}
		int cost = FxBudget.cost(Math.round(life));
		int granted = FxBudget.take(cost, FxBudget.Priority.DETAIL);
		if (granted < cost) {
			FxBudget.refund(granted);
			return false;
		}
		Particle particle = minecraft.particleEngine.createParticle(DuelParticles.EMBER, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		if (particle instanceof DuelEmberParticle ember) {
			ember.configure(width, life, colour, group);
			return true;
		}
		FxBudget.refund(granted);
		return false;
	}

	static final class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public @Nullable Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux,
				double zAux, RandomSource random) {
			return new DuelEmberParticle(level, x, y, z, xAux, yAux, zAux, sprites, random);
		}
	}
}
