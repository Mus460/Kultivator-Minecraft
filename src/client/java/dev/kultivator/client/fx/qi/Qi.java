package dev.kultivator.client.fx.qi;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.QiParticleOptions;
import dev.kultivator.fx.QiParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/**
 * Client entry of the qi particles (polish pass S5) and the one place every effect builds them: a colour becomes a
 * three-tone ramp (core = the colour lifted 55% toward white, body = the colour, rim = the colour darkened 35% and turned
 * 10° in hue), sizes are full widths in blocks, lifetimes are exact ticks. At Minimal quality, or when the types are not
 * registered (an older server jar), every builder returns plain dust instead, which is cheaper and needs no sorting.
 */
public final class Qi {

	private static boolean ready;

	/** Registers the providers of every qi type (once, from {@code KultivatorClient}). */
	public static void init() {
		if (!QiParticles.registered()) {
			Kultivator.LOGGER.warn("Kultivator: qi particle types are not registered; technique effects use dust particles");
			return;
		}
		register(QiParticles.QI);
		register(QiParticles.QI_SPARK);
		register(QiParticles.QI_SMOKE);
		register(QiParticles.QI_RING);
		register(QiParticles.QI_FLAME);
		register(QiParticles.QI_SHARD);
		register(QiParticles.QI_PETAL);
		register(QiParticles.QI_SNOW);
		register(QiParticles.QI_FEATHER);
		ready = true;
	}

	private static void register(ParticleType<QiParticleOptions> type) {
		QiParticle.Kind kind = QiParticle.kindOf(type);
		// fabric-particles-v1 5.0.25: register(type, PendingParticleProvider) receives the sprite set of particles/<id>.json
		ParticleProviderRegistry.getInstance().register(type,
				(ParticleProviderRegistry.PendingParticleProvider<QiParticleOptions>) sprites -> new QiParticle.Provider(sprites, kind));
	}

	/** True when qi particles are drawn (registered and not Minimal quality). */
	public static boolean on() {
		return ready && !EffectQuality.minimal();
	}

	// ================================================================== colours

	/** The bright core of {@code rgb} (55% toward white). */
	public static int core(int rgb) {
		return lerp(rgb, 0xFFFFFF, 0.55F);
	}

	/** The dark, hue-turned rim of {@code rgb}. */
	public static int rim(int rgb) {
		return hueShift(darken(rgb, 0.35F), 10.0F);
	}

	/** The rim toward a second colour (two-Dao arts, authored {@code color2}). */
	public static int rim(int rgb, int toward) {
		return lerp(darken(rgb, 0.3F), toward, 0.45F);
	}

	public static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
		int g = Math.round(((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
		int bl = Math.round((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
		return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
	}

	public static int darken(int rgb, float amount) {
		float k = 1.0F - amount;
		return (clamp(Math.round(((rgb >> 16) & 0xFF) * k)) << 16) | (clamp(Math.round(((rgb >> 8) & 0xFF) * k)) << 8)
				| clamp(Math.round((rgb & 0xFF) * k));
	}

	/** {@code rgb} turned by {@code degrees} on the hue circle (same saturation and value). */
	public static int hueShift(int rgb, float degrees) {
		float r = ((rgb >> 16) & 0xFF) / 255.0F;
		float g = ((rgb >> 8) & 0xFF) / 255.0F;
		float b = (rgb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		if (delta < 1.0E-4F) {
			return rgb & 0xFFFFFF;
		}
		float hue;
		if (max == r) {
			hue = 60.0F * (((g - b) / delta) % 6.0F);
		} else if (max == g) {
			hue = 60.0F * ((b - r) / delta + 2.0F);
		} else {
			hue = 60.0F * ((r - g) / delta + 4.0F);
		}
		hue = ((hue + degrees) % 360.0F + 360.0F) % 360.0F;
		float s = delta / max;
		float c = max * s;
		float x = c * (1.0F - Math.abs((hue / 60.0F) % 2.0F - 1.0F));
		float m = max - c;
		float rr;
		float gg;
		float bb;
		if (hue < 60.0F) {
			rr = c; gg = x; bb = 0.0F;
		} else if (hue < 120.0F) {
			rr = x; gg = c; bb = 0.0F;
		} else if (hue < 180.0F) {
			rr = 0.0F; gg = c; bb = x;
		} else if (hue < 240.0F) {
			rr = 0.0F; gg = x; bb = c;
		} else if (hue < 300.0F) {
			rr = x; gg = 0.0F; bb = c;
		} else {
			rr = c; gg = 0.0F; bb = x;
		}
		return (clamp(Math.round((rr + m) * 255.0F)) << 16) | (clamp(Math.round((gg + m) * 255.0F)) << 8) | clamp(Math.round((bb + m) * 255.0F));
	}

	private static int clamp(int c) {
		return Math.max(0, Math.min(255, c));
	}

	/** Perceived brightness 0..1 (dark colours become smoke, not glow). */
	public static float luma(int rgb) {
		return (0.299F * ((rgb >> 16) & 0xFF) + 0.587F * ((rgb >> 8) & 0xFF) + 0.114F * (rgb & 0xFF)) / 255.0F;
	}

	// ================================================================== builders

	/** A glowing mote of {@code rgb} with the derived ramp, {@code size} blocks wide, shrinking to 40%. */
	public static ParticleOptions mote(int rgb, float size, int life) {
		return make(QiParticles.QI, core(rgb), rgb, rim(rgb), size, size * 0.4F, life, QiParticleOptions.GLOW);
	}

	/** A mote ramping core → body → rim with explicit colours. */
	public static ParticleOptions mote(int core, int body, int rim, float size, float endSize, int life, byte flags) {
		return make(QiParticles.QI, core, body, rim, size, endSize, life, flags);
	}

	/** A star glint. */
	public static ParticleOptions glint(int rgb, float size, int life) {
		return make(QiParticles.QI, 0xFFFFFF, lerp(rgb, 0xFFFFFF, 0.3F), rgb, size, size * 0.2F, life, (byte) (QiParticleOptions.GLOW | QiParticleOptions.STAR));
	}

	/** A streak along its velocity. */
	public static ParticleOptions spark(int rgb, float size, int life) {
		return make(QiParticles.QI_SPARK, 0xFFFFFF, lerp(rgb, 0xFFFFFF, 0.25F), rgb, size, size * 0.5F, life, (byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY));
	}

	/** A smoke puff that swells (not emissive). */
	public static ParticleOptions smoke(int rgb, float size, int life) {
		return make(QiParticles.QI_SMOKE, lerp(rgb, 0xFFFFFF, 0.15F), rgb, darken(rgb, 0.4F), size, size * 2.2F, life, (byte) 0);
	}

	/** A glowing mist puff (qi clouds, mist and fog). */
	public static ParticleOptions mist(int rgb, float size, int life) {
		return make(QiParticles.QI_SMOKE, core(rgb), rgb, rim(rgb), size, size * 1.6F, life, (byte) (QiParticleOptions.GLOW | QiParticleOptions.FADE_IN));
	}

	/** A camera-facing ring that expands from {@code size} to {@code endSize}. */
	public static ParticleOptions ring(int rgb, float size, float endSize, int life) {
		return make(QiParticles.QI_RING, 0xFFFFFF, rgb, rim(rgb), size, endSize, life, (byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY));
	}

	/** A flat ground ring / decal expanding from {@code size} to {@code endSize}. */
	public static ParticleOptions groundRing(int rgb, float size, float endSize, int life) {
		return make(QiParticles.QI_RING, core(rgb), rgb, rim(rgb), size, endSize, life,
				(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.GROUND_HUG));
	}

	public static ParticleOptions flame(int rgb, float size, int life) {
		return make(QiParticles.QI_FLAME, lerp(rgb, 0xFFF6C0, 0.6F), rgb, darken(rgb, 0.45F), size, size * 0.3F, life, QiParticleOptions.GLOW);
	}

	public static ParticleOptions shard(int rgb, float size, int life) {
		return make(QiParticles.QI_SHARD, 0xFFFFFF, rgb, rim(rgb), size, size * 0.7F, life, QiParticleOptions.GLOW);
	}

	public static ParticleOptions petal(int rgb, float size, int life) {
		return make(QiParticles.QI_PETAL, lerp(rgb, 0xFFFFFF, 0.4F), rgb, darken(rgb, 0.2F), size, size * 0.9F, life, QiParticleOptions.GLOW);
	}

	public static ParticleOptions snow(int rgb, float size, int life) {
		return make(QiParticles.QI_SNOW, 0xFFFFFF, rgb, lerp(rgb, 0xFFFFFF, 0.3F), size, size * 0.8F, life, QiParticleOptions.GLOW);
	}

	public static ParticleOptions feather(int rgb, float size, int life) {
		return make(QiParticles.QI_FEATHER, lerp(rgb, 0xFFFFFF, 0.5F), rgb, darken(rgb, 0.4F), size, size * 0.9F, life, QiParticleOptions.GLOW);
	}

	/** Any qi type with explicit everything (dust at Minimal). */
	public static ParticleOptions make(ParticleType<QiParticleOptions> type, int core, int body, int rim, float size, float endSize, int life,
			byte flags) {
		if (!on()) {
			return dust(body, size);
		}
		return new QiParticleOptions(type, core, body, rim, size, endSize, life, 0.0F, 0.0F, 0.0F, flags);
	}

	/** The Minimal stand-in: dust about the same width (dust scale 1 is about 0.2 blocks). */
	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(rgb & 0xFFFFFF, Math.max(0.15F, Math.min(4.0F, size * 4.0F)));
	}

	/** Lifetime of a qi option for the budget (dust and others: −1). */
	public static int lifeOf(ParticleOptions options) {
		return options instanceof QiParticleOptions qi ? qi.life() : -1;
	}

	private Qi() {
	}
}
