package dev.kultivator.client.aura;

import dev.kultivator.client.epic.film.FilmParticleProviders;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/**
 * The particles of the Aura Kultivasi (DESIGN 31.8 C31.3): {@code kultivator:qi} motes, glints, mist and petals and the
 * film particles (through {@link FilmParticleProviders#spawnAmbient}, which skips the films' per-shot cap). Each one
 * passes, in order: the aura's own per-tick allowance (1 / 4 / 10 by quality, × share, × 0.25 at reduced LOD, × fade),
 * then {@link FxBudget} ({@code DETAIL} for yourself and your own disciples, {@code EXTRA} for everyone else; cost
 * {@code max(1, life / 20)}), then the vanilla "Particles" setting (inside {@code level.addParticle}). A refused particle
 * is simply not made. Positions come from the drivers' deterministic shapes and {@link AuraRng}.
 */
public final class AuraParticles {

	/** Friction of a qi mote per tick (QiParticle MOTE). */
	public static final double MOTE_FRICTION = 0.97D;
	/** Friction of qi mist (QiParticle SMOKE). */
	public static final double MIST_FRICTION = 0.92D;

	/** A glowing qi mote ({@code Qi.mote}), {@code size} blocks wide, living exactly {@code life} ticks. */
	public static boolean mote(AuraSubject s, Vec3 at, Vec3 vel, int rgb, float size, int life) {
		return qi(s, at, vel, life, () -> Qi.mote(rgb & 0xFFFFFF, size, life));
	}

	/** A star glint ({@code Qi.glint}). */
	public static boolean glint(AuraSubject s, Vec3 at, Vec3 vel, int rgb, float size, int life) {
		return qi(s, at, vel, life, () -> Qi.glint(rgb & 0xFFFFFF, size, life));
	}

	/** A glowing mist puff that swells as it fades ({@code Qi.mist}). */
	public static boolean mist(AuraSubject s, Vec3 at, Vec3 vel, int rgb, float size, int life) {
		return qi(s, at, vel, life, () -> Qi.mist(rgb & 0xFFFFFF, size, life));
	}

	/** A drifting petal ({@code Qi.petal}). */
	public static boolean petal(AuraSubject s, Vec3 at, Vec3 vel, int rgb, float size, int life) {
		return qi(s, at, vel, life, () -> Qi.petal(rgb & 0xFFFFFF, size, life));
	}

	/** A film particle ({@code kultivator:film_*}) tinted {@code rgb}, {@code size} blocks wide. */
	public static boolean film(AuraSubject s, SimpleParticleType type, Vec3 at, Vec3 vel, int rgb, float size, int life) {
		Minecraft minecraft = Minecraft.getInstance();
		// the same distance as vanilla particles (film particles are made directly, without that check)
		if (!FilmParticleProviders.ready() || minecraft.level == null
				|| minecraft.gameRenderer.mainCamera().position().distanceToSqr(at) > 32.0D * 32.0D || !allow(s)) {
			return false;
		}
		if (!FilmParticleProviders.spawnAmbient(type, at, vel, rgb & 0xFFFFFF, size, life, priority(s))) {
			AuraEngine.refundParticle(s.entityId());
			return false;
		}
		return true;
	}

	/**
	 * The start speed that carries a particle from {@code from} to {@code to} in {@code life} ticks under
	 * {@code friction} (the speed shrinks by that factor each tick).
	 */
	public static Vec3 toward(Vec3 from, Vec3 to, int life, double friction) {
		double n = Math.max(1, life);
		double sum = friction >= 0.9999D ? n : (1.0D - Math.pow(friction, n)) / (1.0D - friction);
		return to.subtract(from).scale(1.0D / sum);
	}

	/** True when the aura of {@code s} may make one more particle this tick (takes it from the aura's allowance). */
	public static boolean allow(AuraSubject s) {
		return s.fade() > 0.0F && AuraEngine.takeParticle(s.entityId());
	}

	private static boolean qi(AuraSubject s, Vec3 at, Vec3 vel, int life, java.util.function.Supplier<ParticleOptions> options) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !allow(s)) {
			return false;
		}
		int cost = FxBudget.cost(life);
		int granted = FxBudget.take(cost, priority(s));
		if (granted < cost) {
			FxBudget.refund(granted);
			AuraEngine.refundParticle(s.entityId());
			return false;
		}
		CommonFx.add(options.get(), at, vel, false);
		return true;
	}

	private static FxBudget.Priority priority(AuraSubject s) {
		return s.own() ? FxBudget.Priority.DETAIL : FxBudget.Priority.EXTRA;
	}

	private AuraParticles() {
	}
}
