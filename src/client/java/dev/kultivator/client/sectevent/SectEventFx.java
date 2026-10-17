package dev.kultivator.client.sectevent;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.event.SectEventFxIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effects of {@code FxType.SECT_EVENT} (DESIGN 20.3.22, module B): cloud-step departures and returns, the duel ring and
 * hit sparks, the Void portal and the phantom's dissolving, the crimson wind of the forbidden ground, the ghostly ark
 * over the hall, heart-fire motes in the tower, duty puffs, trial waves, rival arts and the champion's fanfare.
 * Continuous effects are kept with an end time (the server refreshes them) and drawn only within 32 blocks (the ark
 * within 64); everything is scaled by Effect Quality. Particles only, nothing else.
 */
public final class SectEventFx {

	private static final double NEAR = 32.0D;
	private static final double ARK_NEAR = 64.0D;
	private static final int GOLD = 0xFFD54A;
	private static final int CRIMSON = 0xB0141E;
	private static final int VOID = 0x5A2E8A;

	/** A continuous effect with its end (client game time). */
	private record Lasting(int kind, Vec3 pos, float radius, long until) {
	}

	private static final List<Lasting> LASTING = new ArrayList<>();

	/** One {@code FxType.SECT_EVENT} packet (routed by {@code FxDispatcher}). */
	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int b = fx.b();
		float f = fx.f();
		switch (fx.a()) {
			case SectEventFxIds.DEPART -> depart(pos, b);
			case SectEventFxIds.RETURN -> arrive(pos, b);
			case SectEventFxIds.DUEL_RING, SectEventFxIds.VOID_PORTAL, SectEventFxIds.CRIMSON_WIND, SectEventFxIds.ARK -> keep(fx.a(), pos, f, b, level);
			case SectEventFxIds.HIT_SPARK -> spark(pos, b);
			case SectEventFxIds.PHANTOM_DISSOLVE -> dissolve(pos);
			case SectEventFxIds.HEART_FIRE -> heartFire(pos, b == 1);
			case SectEventFxIds.DUTY_PUFF -> duty(pos, b);
			case SectEventFxIds.TRIAL_WAVE -> trialWave(pos, f);
			case SectEventFxIds.TRIAL_END -> trialEnd(pos, b == 1);
			case SectEventFxIds.RIVAL_LINE -> {
				CommonFx.beam(pos, target, b, 0.25F);
				CommonFx.add(ParticleTypes.ELECTRIC_SPARK, target, Vec3.ZERO, false);
			}
			case SectEventFxIds.RIVAL_BURST -> CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), b, 30, Math.max(1.0F, f));
			case SectEventFxIds.CHAMPION -> champion(pos, b == 1);
			case SectEventFxIds.ARK_BOARD -> CommonFx.beam(pos.add(0.0D, 1.0D, 0.0D), target, 0xBFF4FF, 0.35F);
			case SectEventFxIds.REFINE -> refine(pos, b);
			default -> {
			}
		}
	}

	private static void keep(int kind, Vec3 pos, float radius, int ticks, ClientLevel level) {
		long until = level.getGameTime() + Math.max(1, ticks);
		for (Iterator<Lasting> it = LASTING.iterator(); it.hasNext(); ) {
			Lasting each = it.next();
			if (each.kind() == kind && each.pos().distanceToSqr(pos) < 4.0D) {
				it.remove();
			}
		}
		LASTING.add(new Lasting(kind, pos, radius, until));
	}

	static void clear() {
		LASTING.clear();
	}

	/** Every client tick: the lasting effects and the tower's heart-fire motes. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		long now = level.getGameTime();
		Vec3 eye = minecraft.player.position();
		RandomSource random = level.getRandom();
		for (Iterator<Lasting> it = LASTING.iterator(); it.hasNext(); ) {
			Lasting each = it.next();
			if (each.until() < now) {
				it.remove();
				continue;
			}
			double range = each.kind() == SectEventFxIds.ARK ? ARK_NEAR : NEAR;
			if (each.pos().distanceToSqr(eye) > range * range) {
				continue;
			}
			switch (each.kind()) {
				case SectEventFxIds.DUEL_RING -> ringTick(each, random, now);
				case SectEventFxIds.VOID_PORTAL -> portalTick(each, random, now);
				case SectEventFxIds.CRIMSON_WIND -> windTick(each, random, now);
				case SectEventFxIds.ARK -> arkTick(each, random, now);
				default -> {
				}
			}
		}
		if (now % 4L == 0L) {
			towerMotes(level, eye, random);
		}
	}

	// ================================================================== lasting effects

	private static void ringTick(Lasting ring, RandomSource random, long now) {
		int n = EffectQuality.scale(4);
		DustParticleOptions gold = CommonFx.dust(GOLD, 1.0F);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(gold, ring.pos().x + Math.cos(angle) * ring.radius(), ring.pos().y + 0.1D, ring.pos().z + Math.sin(angle) * ring.radius(),
					0.0D, 0.01D, 0.0D);
		}
		if (EffectQuality.maximal() && now % 5L == 0L) {
			double angle = now * 0.08D;
			CommonFx.add(ParticleTypes.END_ROD, ring.pos().x + Math.cos(angle) * ring.radius(), ring.pos().y + 0.2D,
					ring.pos().z + Math.sin(angle) * ring.radius(), 0.0D, 0.02D, 0.0D);
		}
	}

	private static void portalTick(Lasting portal, RandomSource random, long now) {
		int n = EffectQuality.scale(3);
		DustParticleOptions dark = CommonFx.dust(VOID, 1.2F);
		for (int i = 0; i < n; i++) {
			double angle = now * 0.25D + i * Math.PI * 2.0D / n;
			double r = portal.radius() * (0.4D + random.nextDouble() * 0.6D);
			Vec3 at = portal.pos().add(Math.cos(angle) * r, 0.2D + random.nextDouble() * 1.8D, Math.sin(angle) * r);
			CommonFx.add(dark, at, portal.pos().add(0.0D, 1.0D, 0.0D).subtract(at).scale(0.06D), false);
		}
		if (EffectQuality.chance(random, 0.5F)) {
			CommonFx.add(ParticleTypes.REVERSE_PORTAL, portal.pos().x + random.nextGaussian() * 0.4D, portal.pos().y + random.nextDouble() * 2.0D,
					portal.pos().z + random.nextGaussian() * 0.4D, 0.0D, 0.02D, 0.0D);
		}
	}

	private static void windTick(Lasting wind, RandomSource random, long now) {
		int n = EffectQuality.scale(3);
		DustParticleOptions red = CommonFx.dust(CRIMSON, 1.3F);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = wind.radius() * (0.5D + random.nextDouble() * 0.6D);
			double x = wind.pos().x + Math.cos(angle) * r;
			double z = wind.pos().z + Math.sin(angle) * r;
			// Tangential drift: the wind circles the stele.
			CommonFx.add(red, x, wind.pos().y + 0.3D + random.nextDouble() * 3.0D, z, -Math.sin(angle) * 0.18D, 0.0D, Math.cos(angle) * 0.18D);
		}
		if (EffectQuality.chance(random, 0.25F)) {
			CommonFx.add(ParticleTypes.CRIMSON_SPORE, wind.pos().x + random.nextGaussian() * 2.0D, wind.pos().y + 1.0D + random.nextDouble() * 2.0D,
					wind.pos().z + random.nextGaussian() * 2.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	/** The ghostly ark: a hull, a mast and a sail outlined by pale motes, drifting slowly. */
	private static void arkTick(Lasting ark, RandomSource random, long now) {
		double sway = Math.sin(now * 0.02D) * 0.6D;
		Vec3 c = ark.pos().add(0.0D, sway, 0.0D);
		int n = EffectQuality.scale(6);
		DustParticleOptions pale = CommonFx.dust(0xBFF4FF, 1.4F);
		DustParticleOptions gold = CommonFx.dust(0xE8D7A8, 1.1F);
		for (int i = 0; i < n; i++) {
			double t = random.nextDouble() * 2.0D - 1.0D;
			int part = random.nextInt(4);
			Vec3 at;
			if (part <= 1) {
				// Hull: a curved keel 14 blocks long.
				at = c.add(t * 7.0D, -1.5D * (1.0D - t * t) + (part == 0 ? 0.0D : 1.0D), (part == 0 ? 0.0D : (random.nextBoolean() ? 1.6D : -1.6D)) * (1.0D - t * t));
			} else if (part == 2) {
				// Mast.
				at = c.add(0.0D, 1.0D + random.nextDouble() * 7.0D, 0.0D);
			} else {
				// Sail.
				double h = random.nextDouble();
				at = c.add(0.3D + random.nextDouble() * 3.5D * (1.0D - h * 0.6D), 2.5D + h * 5.0D, Math.sin(now * 0.03D + h) * 0.3D);
			}
			CommonFx.add(part == 2 ? gold : pale, at, new Vec3(0.0D, -0.01D, 0.0D), true);
		}
		if (EffectQuality.maximal() && now % 10L == 0L) {
			CommonFx.add(ParticleTypes.END_ROD, c.add(-7.0D + random.nextDouble() * 14.0D, -1.0D, 0.0D), new Vec3(0.0D, -0.05D, 0.0D), true);
		}
	}

	/** Soul-flame motes around every disciple training in the tower (synced activity 6) within 32 blocks. */
	private static void towerMotes(ClientLevel level, Vec3 eye, RandomSource random) {
		AABB box = new AABB(eye, eye).inflate(NEAR);
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box,
				each -> each.syncedActivity() == DiscipleEntity.ACTIVITY_TOWER)) {
			int n = EffectQuality.scale(2);
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, disciple.getX() + Math.cos(angle) * 0.8D, disciple.getY() + 0.2D + random.nextDouble() * 1.2D,
						disciple.getZ() + Math.sin(angle) * 0.8D, 0.0D, 0.03D, 0.0D);
			}
		}
	}

	// ================================================================== one-shot effects

	private static void depart(Vec3 pos, int rgb) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(24);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			CommonFx.add(ParticleTypes.CLOUD, pos.x + Math.cos(angle) * 0.8D, pos.y + 0.1D, pos.z + Math.sin(angle) * 0.8D,
					Math.cos(angle) * 0.05D, 0.12D + random.nextDouble() * 0.05D, Math.sin(angle) * 0.05D);
		}
		CommonFx.pillar(pos, rgb, 4.0F);
		CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), rgb, 1.2F, 20, 0.0F);
	}

	private static void arrive(Vec3 pos, int rgb) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(18);
		for (int i = 0; i < n; i++) {
			CommonFx.add(ParticleTypes.CLOUD, pos.x + random.nextGaussian() * 0.5D, pos.y + 1.5D + random.nextDouble(), pos.z + random.nextGaussian() * 0.5D,
					0.0D, -0.08D, 0.0D);
		}
		CommonFx.ring(pos, rgb, 1.2F, 24);
	}

	private static void spark(Vec3 pos, int rgb) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(6);
		for (int i = 0; i < n; i++) {
			CommonFx.add(ParticleTypes.CRIT, pos.x, pos.y, pos.z, random.nextGaussian() * 0.3D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.3D);
		}
		CommonFx.burst(pos, rgb, 6, 0.4F);
	}

	private static void dissolve(Vec3 pos) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(20);
		DustParticleOptions dark = CommonFx.dust(0x2A1838, 1.5F);
		for (int i = 0; i < n; i++) {
			CommonFx.add(dark, pos.x + random.nextGaussian() * 0.3D, pos.y + random.nextDouble() * 1.8D, pos.z + random.nextGaussian() * 0.3D,
					0.0D, 0.05D + random.nextDouble() * 0.05D, 0.0D);
		}
		for (int i = 0; i < EffectQuality.scale(4); i++) {
			CommonFx.add(ParticleTypes.SOUL, pos.x + random.nextGaussian() * 0.3D, pos.y + 1.0D, pos.z + random.nextGaussian() * 0.3D, 0.0D, 0.05D, 0.0D);
		}
		CommonFx.add(ParticleTypes.POOF, pos.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, false);
	}

	private static void heartFire(Vec3 pos, boolean resisted) {
		RandomSource random = CommonFx.random();
		if (resisted) {
			CommonFx.ring(pos.add(0.0D, 0.2D, 0.0D), 0x6FD9FF, 1.4F, 28);
			for (int i = 0; i < EffectQuality.scale(10); i++) {
				CommonFx.add(ParticleTypes.ENCHANT, pos.x + random.nextGaussian() * 0.6D, pos.y + 1.0D + random.nextDouble(), pos.z + random.nextGaussian() * 0.6D,
						0.0D, 0.0D, 0.0D);
			}
			return;
		}
		for (int i = 0; i < EffectQuality.scale(16); i++) {
			CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.15D, random.nextDouble() * 0.2D,
					random.nextGaussian() * 0.15D);
		}
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.05D, 0.05D, random.nextGaussian() * 0.05D);
		}
	}

	private static void duty(Vec3 pos, int duty) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(6);
		for (int i = 0; i < n; i++) {
			double x = pos.x + random.nextGaussian() * 0.4D;
			double y = pos.y + 0.6D + random.nextDouble() * 0.6D;
			double z = pos.z + random.nextGaussian() * 0.4D;
			switch (duty) {
				case DiscipleEntity.ACTIVITY_WATER -> CommonFx.add(ParticleTypes.SPLASH, x, y, z, 0.0D, 0.1D, 0.0D);
				case DiscipleEntity.ACTIVITY_GARDEN -> CommonFx.add(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.0D, 0.02D, 0.0D);
				case DiscipleEntity.ACTIVITY_KITCHEN -> CommonFx.add(i % 2 == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, x, y, z, 0.0D, 0.03D, 0.0D);
				default -> CommonFx.add(CommonFx.dust(0xFFF3D6, 0.8F), x, y, z, random.nextGaussian() * 0.02D, -0.02D, random.nextGaussian() * 0.02D);
			}
		}
	}

	private static void trialWave(Vec3 pos, float radius) {
		CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0xD02A2A, Math.max(2.0F, radius), 40);
		RandomSource random = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(10); i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.SMOKE, pos.x + Math.cos(angle) * radius, pos.y + 0.2D, pos.z + Math.sin(angle) * radius, 0.0D, 0.05D, 0.0D);
		}
	}

	private static void trialEnd(Vec3 pos, boolean passed) {
		if (passed) {
			CommonFx.pillar(pos, GOLD, 6.0F);
			CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 2.5F, 36);
		} else {
			CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0x888888, 20, 1.0F);
		}
	}

	private static void champion(Vec3 pos, boolean elite) {
		RandomSource random = CommonFx.random();
		CommonFx.pillar(pos, GOLD, elite ? 10.0F : 7.0F);
		CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 5.0F, 48);
		for (int i = 0; i < EffectQuality.scale(elite ? 40 : 24); i++) {
			CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 2.0D, pos.z, random.nextGaussian() * 0.4D, random.nextDouble() * 0.6D,
					random.nextGaussian() * 0.4D);
		}
		if (elite && EffectQuality.maximal()) {
			for (int i = 0; i < 12; i++) {
				CommonFx.add(ParticleTypes.FIREWORK, pos.x, pos.y + 3.0D, pos.z, random.nextGaussian() * 0.2D, 0.3D, random.nextGaussian() * 0.2D);
			}
		}
	}

	private static void refine(Vec3 pos, int rgb) {
		CommonFx.spiralIn(pos, rgb, 1.0F, 24, 0.0F);
		RandomSource random = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			CommonFx.add(ParticleTypes.FLAME, pos.x + random.nextGaussian() * 0.2D, pos.y, pos.z + random.nextGaussian() * 0.2D, 0.0D, 0.02D, 0.0D);
		}
	}

	private SectEventFx() {
	}
}
