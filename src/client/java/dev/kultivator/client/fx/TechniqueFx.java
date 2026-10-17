package dev.kultivator.client.fx;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.data.VisualState;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.SwordColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effects of the universal techniques (Dao technique ordinals are forwarded to {@link DaoTechniqueFx}). One-shot
 * effects come from the server ({@code FxType.TECHNIQUE}, a = technique ordinal, b = sub effect with extra data in the
 * bits above 4, f = scale); some of them play over several ticks here (the falling Buddha Palm, the Divine Sense wave,
 * the opening domain). Continuous ones come from every nearby player's synced look: the flying sword's qi trail, the
 * Dao Domain dome and ground ring, the golden motes of a Dharma Form. Everything is scaled by Effect Quality.
 */
public final class TechniqueFx {

	private static final double RANGE_SQ = 64.0D * 64.0D;
	private static final int QI = 0xCFF6FF;
	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int DEEP_GOLD = 0xE8A82A;
	private static final int PALE_GOLD = 0xFFF1B8;
	private static final int SENSE = 0xC9B8FF;
	private static final int NO_DAO = 0xE8DDB5;
	/** Height the Buddha Palm starts falling from. */
	private static final double PALM_HEIGHT = 12.0D;
	private static final int PALM_TICKS = 20;

	/** An effect playing over several ticks; {@code tick} returns false when it is over. */
	private interface Anim {
		boolean tick(int age, RandomSource random);
	}

	private static final class Running {
		final Anim anim;
		int age;

		Running(Anim anim) {
			this.anim = anim;
		}
	}

	private static final List<Running> RUNNING = new ArrayList<>();
	private static ClientLevel lastLevel;

	public static void play(FxType type, Payloads.Fx fx) {
		Technique[] all = Technique.values();
		if (fx.a() < 0 || fx.a() >= all.length) {
			return;
		}
		Technique technique = all[fx.a()];
		if (technique.dao() != null) {
			DaoTechniqueFx.play(fx);
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int sub = fx.b() & 0xF;
		int extra = fx.b() >>> 4;
		float scale = fx.f();
		switch (technique) {
			case CLOUD_STEP -> cloudPuff(pos, scale);
			case QI_PALM -> {
				if (sub == 0) {
					qiPalm(pos, target);
				} else {
					palmHit(pos);
				}
			}
			case FLYING_SWORD -> swordRing(pos, SwordColors.color(extra), sub == 0);
			case DIVINE_SENSE -> start(senseWave(pos, Math.max(8.0F, scale)));
			case BUDDHA_PALM -> {
				if (sub == 0) {
					start(buddhaPalm(pos, target, Math.max(1.0F, scale)));
				} else {
					buddhaImpact(pos, Math.max(1.0F, scale));
				}
			}
			case DHARMA_FORM -> {
				if (sub == 0) {
					dharmaBurst(pos, target);
				} else {
					dharmaEnd(pos);
				}
			}
			case DAO_DOMAIN -> {
				int color = domainColor(extra - 1);
				if (sub == 0) {
					start(domainOpen(pos, Math.max(2.0F, scale), color));
				} else {
					domainClose(pos, Math.max(2.0F, scale), color);
				}
			}
			default -> {
			}
		}
	}

	/** Divine Sense ore positions to outline for a while. */
	public static void onOrePings(Payloads.OrePings pings) {
		OrePingFx.onOrePings(pings);
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			RUNNING.clear();
			OrePingFx.clear();
		}
		RandomSource random = level.getRandom();
		for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
			Running running = it.next();
			if (!running.anim.tick(running.age++, random)) {
				it.remove();
			}
		}

		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		long time = level.getGameTime();
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isRemoved() || player.isSpectator() || player.isInvisible() && player != minecraft.player) {
				continue;
			}
			if (player.distanceToSqr(camera) > RANGE_SQ) {
				continue;
			}
			VisualState visual = ClientCultivation.visual(player);
			boolean ownView = player == minecraft.player && firstPerson;
			if (visual.has(VisualState.FLYING_SWORD)) {
				swordTrail(player, visual, random, time);
			}
			if (visual.has(VisualState.DOMAIN)) {
				domain(player, visual, random, time);
			}
			if (visual.has(VisualState.DHARMA)) {
				dharma(player, random, time, ownView);
			}
		}
		OrePingFx.tick(minecraft);
	}

	private static void start(Anim anim) {
		if (RUNNING.size() < 64) {
			RUNNING.add(new Running(anim));
		}
	}

	private static int domainColor(int daoOrdinal) {
		Dao dao = Dao.byOrdinal(daoOrdinal);
		return dao == null ? NO_DAO : dao.color();
	}

	// ================================================================ cloud step

	/** A puff of cloud under the feet. */
	private static void cloudPuff(Vec3 feet, float scale) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(Math.round(10 * Math.max(0.5F, scale)));
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double speed = 0.04D + random.nextDouble() * 0.05D;
			Vec3 at = feet.add(Math.cos(angle) * 0.3D, 0.05D + random.nextDouble() * 0.15D, Math.sin(angle) * 0.3D);
			CommonFx.add(ParticleTypes.CLOUD, at, new Vec3(Math.cos(angle) * speed, -0.02D, Math.sin(angle) * speed), false);
		}
		CommonFx.ring(feet, 0xF4FBFF, 0.55F, 14);
		if (EffectQuality.maximal()) {
			CommonFx.ring(feet.add(0.0D, -0.1D, 0.0D), JADE, 0.9F, 18);
			for (int i = 0; i < 4; i++) {
				CommonFx.add(ParticleTypes.POOF, feet, new Vec3(random.nextGaussian() * 0.05D, -0.05D, random.nextGaussian() * 0.05D), false);
			}
		}
	}

	// ================================================================ qi palm

	/** The qi blast: a thin beam with palm prints along it and a burst where it ends. */
	private static void qiPalm(Vec3 hand, Vec3 end) {
		RandomSource random = CommonFx.random();
		Vec3 delta = end.subtract(hand);
		double length = delta.length();
		if (length < 0.1D) {
			return;
		}
		Vec3 dir = delta.scale(1.0D / length);
		Vec3 right = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
		right = right.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
		Vec3 up = right.cross(dir).normalize();
		CommonFx.beam(hand, end, QI, 0.12F);
		for (double t = 1.0D; t < length; t += 2.5D) {
			double size = 0.45D + 0.25D * (t / length);
			TechniqueShapes.palm(hand.add(dir.scale(t)), right, up, size, QI, JADE, 0.7F, 14, random, false);
		}
		TechniqueShapes.palm(end.subtract(dir.scale(0.2D)), right, up, 0.9D, PALE_GOLD, JADE, 0.9F, 22, random, false);
		CommonFx.burst(end, QI, 12, 1.2F);
		if (EffectQuality.maximal()) {
			CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | QI), end, Vec3.ZERO, false);
		}
	}

	/** A monster struck by the palm. */
	private static void palmHit(Vec3 pos) {
		RandomSource random = CommonFx.random();
		CommonFx.burst(pos, PALE_GOLD, 8, 0.8F);
		for (int i = 0; i < EffectQuality.scale(5); i++) {
			CommonFx.add(ParticleTypes.ENCHANTED_HIT, pos, new Vec3(random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D,
					random.nextGaussian() * 0.2D), false);
		}
	}

	// ================================================================ flying sword

	/** Mounting (or leaving) the sword: a ring in the sword's colour and rising qi. */
	private static void swordRing(Vec3 feet, int color, boolean mount) {
		RandomSource random = CommonFx.random();
		if (mount) {
			CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), color, 1.2F, 32);
			CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), TechniqueShapes.lighten(color, 0.5F), 0.6F, 16);
			int n = EffectQuality.scale(14);
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 at = feet.add(Math.cos(angle) * 0.6D, random.nextDouble() * 0.3D, Math.sin(angle) * 0.6D);
				CommonFx.add(CommonFx.dust(color, 0.9F), at, new Vec3(0.0D, 0.12D + random.nextDouble() * 0.1D, 0.0D), false);
			}
			for (int i = 0; i < EffectQuality.scale(4); i++) {
				CommonFx.add(ParticleTypes.ELECTRIC_SPARK, feet, new Vec3(random.nextGaussian() * 0.15D, 0.1D, random.nextGaussian() * 0.15D), false);
			}
			if (EffectQuality.maximal()) {
				for (int i = 0; i < 12; i++) {
					double angle = i * Math.PI / 6.0D;
					CommonFx.add(ParticleTypes.END_ROD, feet.add(Math.cos(angle) * 1.2D, 0.1D, Math.sin(angle) * 1.2D),
							new Vec3(0.0D, 0.05D, 0.0D), false);
				}
			}
		} else {
			CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), color, 0.8F, 20);
			CommonFx.burst(feet.add(0.0D, 0.3D, 0.0D), TechniqueShapes.lighten(color, 0.3F), 8, 0.8F);
		}
	}

	/**
	 * The qi of the sword under a flying cultivator's feet: side sparks and a drifting trail. The sword itself is drawn
	 * by FlyingSwordLayer along the body yaw, so everything here follows the body yaw too.
	 */
	private static void swordTrail(AbstractClientPlayer player, VisualState visual, RandomSource random, long time) {
		int color = SwordColors.color(visual.swordItem());
		double yaw = Math.toRadians(player.yBodyRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		double x = player.getX();
		double y = player.getY() - 0.12D;
		double z = player.getZ();
		if (time % (EffectQuality.maximal() ? 1L : EffectQuality.minimal() ? 6L : 3L) == 0L) {
			for (int side = -1; side <= 1; side += 2) {
				CommonFx.add(CommonFx.dust(TechniqueShapes.lighten(color, 0.2F), 0.55F),
						new Vec3(x - fx * 0.35D + rx * 0.22D * side, y, z - fz * 0.35D + rz * 0.22D * side), Vec3.ZERO, false);
			}
		}
		if (EffectQuality.chance(random, 0.6F)) {
			Vec3 at = new Vec3(x - fx * 0.9D + random.nextGaussian() * 0.08D, y + random.nextDouble() * 0.1D, z - fz * 0.9D + random.nextGaussian() * 0.08D);
			CommonFx.add(CommonFx.dust(TechniqueShapes.lighten(color, 0.45F), 0.45F), at, new Vec3(-fx * 0.02D, -0.01D, -fz * 0.02D), false);
		}
		if (EffectQuality.maximal() && random.nextInt(5) == 0) {
			CommonFx.add(new TrailParticleOption(new Vec3(x + fx * 1.05D, y, z + fz * 1.05D), TechniqueShapes.lighten(color, 0.4F), 3),
					new Vec3(x + fx * 0.95D, y, z + fz * 0.95D), Vec3.ZERO, false);
		}
	}

	// ================================================================ divine sense

	/** A wave of soul sense expanding from the caster to {@code radius} blocks in about 1.2 s. */
	private static Anim senseWave(Vec3 origin, float radius) {
		Vec3 center = origin.add(0.0D, 1.0D, 0.0D);
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | SENSE), center, Vec3.ZERO, true);
		RandomSource first = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(16); i++) {
			Vec3 from = center.add(first.nextGaussian() * 1.2D, first.nextDouble() * 1.5D, first.nextGaussian() * 1.2D);
			CommonFx.add(ParticleTypes.ENCHANT, center, from.subtract(center), false);
		}
		int duration = 24;
		return (age, random) -> {
			double progress = (age + 1) / (double) duration;
			double r = radius * (1.0D - Math.pow(1.0D - progress, 2.0D));
			int n = Math.min(160, EffectQuality.scale(Math.max(16, (int) (r * 1.6D))));
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double height = (random.nextDouble() - 0.4D) * 2.0D * (1.0D - progress * 0.5D);
				Vec3 at = center.add(Math.cos(angle) * r, height, Math.sin(angle) * r);
				Vec3 to = center.add(Math.cos(angle) * (r + 1.6D), height, Math.sin(angle) * (r + 1.6D));
				CommonFx.add(new TrailParticleOption(to, TechniqueShapes.dim(SENSE, 1.0F - (float) progress * 0.4F), 4), at, Vec3.ZERO, true);
			}
			return age + 1 < duration;
		};
	}

	// ================================================================ buddha palm

	/** The giant golden palm descending from 12 blocks over {@code point}, fingers pointing away from the caster. */
	private static Anim buddhaPalm(Vec3 point, Vec3 caster, float radius) {
		Vec3 flat = new Vec3(point.x - caster.x, 0.0D, point.z - caster.z);
		Vec3 forward = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
		double size = radius * 0.95D;
		return (age, random) -> {
			double t = Math.min(1.0D, (age + 1) / (double) PALM_TICKS);
			double height = PALM_HEIGHT * (1.0D - t * t) + 0.6D;
			Vec3 center = point.add(0.0D, height, 0.0D);
			TechniqueShapes.palm(center, right, forward, size, DEEP_GOLD, GOLD, 1.7F, 60, random, true);
			if (age % 2 == 0) {
				int column = EffectQuality.scale(4);
				for (int i = 0; i < column; i++) {
					double y = random.nextDouble() * height;
					CommonFx.add(CommonFx.dust(PALE_GOLD, 1.0F), point.add(random.nextGaussian() * 0.3D, y, random.nextGaussian() * 0.3D),
							Vec3.ZERO, true);
				}
			}
			if (age == 0) {
				TechniqueShapes.looseRing(point, radius, GOLD, 1.2F, 40, random, true);
			}
			return age + 1 < PALM_TICKS;
		};
	}

	/** The palm lands: a flash, a burst and a golden shockwave ring. */
	private static void buddhaImpact(Vec3 point, float radius) {
		RandomSource random = CommonFx.random();
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), point.add(0.0D, 0.5D, 0.0D), Vec3.ZERO, true);
		CommonFx.burst(point.add(0.0D, 0.6D, 0.0D), GOLD, 30, radius * 0.8F);
		for (int i = 0; i < EffectQuality.scale(16); i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.CLOUD, point.add(Math.cos(angle) * radius * 0.4D, 0.1D, Math.sin(angle) * radius * 0.4D),
					new Vec3(Math.cos(angle) * 0.25D, 0.02D, Math.sin(angle) * 0.25D), true);
		}
		start(shockwave(point, radius + 1.5D, GOLD, 12));
	}

	/** A ring on the ground expanding to {@code radius} over {@code duration} ticks. */
	private static Anim shockwave(Vec3 center, double radius, int color, int duration) {
		return (age, random) -> {
			double r = radius * (age + 1) / duration;
			TechniqueShapes.looseRing(center, r, color, 1.4F, Math.max(12, (int) (r * 5.0D)), random, true);
			if (EffectQuality.maximal() && age % 3 == 0) {
				TechniqueShapes.looseRing(center.add(0.0D, 0.3D, 0.0D), r * 0.92D, PALE_GOLD, 0.8F, 10, random, true);
			}
			return age + 1 < duration;
		};
	}

	// ================================================================ dharma form

	/** Becoming the Dharma avatar: a golden pillar, a ring and a flash. */
	private static void dharmaBurst(Vec3 feet, Vec3 head) {
		RandomSource random = CommonFx.random();
		double height = Math.max(2.0D, head.y - feet.y);
		CommonFx.pillar(feet, GOLD, (float) height + 2.0F);
		CommonFx.ring(feet, GOLD, 2.5F, 40);
		CommonFx.ring(feet, PALE_GOLD, 1.5F, 24);
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | GOLD), feet.add(0.0D, height * 0.5D, 0.0D), Vec3.ZERO, true);
		CommonFx.burst(feet.add(0.0D, height * 0.5D, 0.0D), PALE_GOLD, 24, 2.5F);
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 20; i++) {
				CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, feet.add(0.0D, height * 0.5D, 0.0D),
						new Vec3(random.nextGaussian() * 0.3D, random.nextDouble() * 0.4D, random.nextGaussian() * 0.3D), false);
			}
		}
	}

	private static void dharmaEnd(Vec3 feet) {
		CommonFx.burst(feet.add(0.0D, 1.5D, 0.0D), PALE_GOLD, 18, 2.0F);
		CommonFx.ring(feet, DEEP_GOLD, 1.2F, 24);
	}

	/** Golden motes rising around the huge silhouette; a faint halo over the head. */
	private static void dharma(AbstractClientPlayer player, RandomSource random, long time, boolean ownView) {
		double width = player.getBbWidth();
		double height = player.getBbHeight();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		int n = EffectQuality.minimal() ? (random.nextInt(2) == 0 ? 1 : 0) : EffectQuality.scale(3);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = width * 0.6D + random.nextDouble() * 0.35D;
			double py = y + random.nextDouble() * height * (ownView ? 0.45D : 1.0D);
			CommonFx.add(CommonFx.dust(random.nextInt(3) == 0 ? PALE_GOLD : GOLD, 1.1F), new Vec3(x + Math.cos(angle) * r, py, z + Math.sin(angle) * r),
					new Vec3(0.0D, 0.06D, 0.0D), false);
		}
		if (!ownView && time % (EffectQuality.maximal() ? 2L : 4L) == 0L) {
			double spin = time * 0.08D;
			int halo = EffectQuality.scale(6);
			for (int i = 0; i < halo; i++) {
				double angle = spin + i * Math.PI * 2.0D / halo;
				CommonFx.add(CommonFx.dust(PALE_GOLD, 0.8F), new Vec3(x + Math.cos(angle) * width * 0.7D, y + height + 0.25D,
						z + Math.sin(angle) * width * 0.7D), Vec3.ZERO, false);
			}
		}
		if (EffectQuality.maximal() && random.nextInt(4) == 0) {
			CommonFx.add(ParticleTypes.END_ROD, new Vec3(x + random.nextGaussian() * width * 0.5D, y + random.nextDouble() * height * 0.6D,
					z + random.nextGaussian() * width * 0.5D), new Vec3(0.0D, 0.04D, 0.0D), false);
		}
	}

	// ================================================================ dao domain

	/** The domain unfolds: a ring racing out to the edge, then the dome flashes into being. */
	private static Anim domainOpen(Vec3 center, float radius, int color) {
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | color), center.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, true);
		int duration = 16;
		return (age, random) -> {
			double r = radius * (age + 1) / duration;
			TechniqueShapes.looseRing(center, r, color, 1.5F, Math.max(16, (int) (r * 5.0D)), random, true);
			if (age + 1 == duration) {
				TechniqueShapes.hemisphere(center, radius, TechniqueShapes.lighten(color, 0.25F), 1.3F, 90, random, Vec3.ZERO, true);
				TechniqueShapes.looseRing(center, radius, TechniqueShapes.lighten(color, 0.5F), 1.8F, 60, random, true);
			}
			return age + 1 < duration;
		};
	}

	private static void domainClose(Vec3 center, float radius, int color) {
		CommonFx.spiralIn(center.add(0.0D, 1.0D, 0.0D), color, Math.min(radius, 6.0F), 30, 0.0F);
		CommonFx.ring(center, color, radius, 48);
	}

	/** While a domain is up: slow motes on the dome and a ground ring in the Dao's colour. */
	private static void domain(AbstractClientPlayer player, VisualState visual, RandomSource random, long time) {
		int color = domainColor(visual.dao());
		Vec3 center = player.position();
		double radius = 10.0D;
		TechniqueShapes.hemisphere(center, radius, color, 1.0F, 4, random, new Vec3(0.0D, 0.004D, 0.0D), false);
		TechniqueShapes.looseRing(center, radius, color, 1.3F, 6, random, false);
		if (time % 2L == 0L) {
			double spin = time * 0.03D;
			int marks = EffectQuality.maximal() ? 8 : EffectQuality.scale(4);
			for (int i = 0; i < marks; i++) {
				double angle = spin + i * Math.PI * 2.0D / marks;
				CommonFx.add(CommonFx.dust(TechniqueShapes.lighten(color, 0.45F), 1.5F),
						center.add(Math.cos(angle) * radius, 0.1D, Math.sin(angle) * radius), Vec3.ZERO, false);
			}
		}
		if (EffectQuality.maximal() && random.nextInt(6) == 0) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.END_ROD, center.add(Math.cos(angle) * radius * 0.6D, 0.2D, Math.sin(angle) * radius * 0.6D),
					new Vec3(0.0D, 0.03D, 0.0D), false);
		}
	}

	private TechniqueFx() {
	}
}
