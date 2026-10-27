package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.DaoTechniqueSub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effects of the 16 Dao techniques, all particles, scaled by Effect Quality. One-shot effects come from TECHNIQUE
 * packets (sub effects in {@link DaoTechniqueSub}); moving ones (the sword crescent, falling swords of light, the
 * phoenix, the frost lotus, shockwaves...) are short animations advanced every client tick; lasting ones on an entity
 * (glacier ice shell, space cage, iron shimmer, shadow wisps) are "marks" sent once with their duration and dropped
 * when they expire, end or the entity disappears.
 */
public final class DaoTechniqueFx {

	private static final int MAX_ANIMS = 256;
	private static final int MAX_MARKS = 128;

	private static final int SWORD_WHITE = 0xF4FAFF;
	private static final int SWORD_BLUE = 0x9CCBFF;
	private static final int HILT_GOLD = 0xFFD36A;
	private static final int HILT_DARK = 0xB8862E;
	private static final int FIRE_ORANGE = 0xFF7A1F;
	private static final int FIRE_YELLOW = 0xFFC83A;
	private static final int FIRE_RED = 0xE8331A;
	private static final int ICE_BLUE = 0xA8EEFF;
	private static final int ICE_WHITE = 0xEFFFFF;
	private static final int ICE_DEEP = 0x5FB8E8;
	private static final int THUNDER_VIOLET = 0xB57CFF;
	private static final int THUNDER_CORE = 0xF2E8FF;
	private static final int STORM_DARK = 0x2A2438;
	private static final int STORM_GREY = 0x3A3646;
	private static final int IRON_GREY = 0xA9AFB8;
	private static final int IRON_SHINE = 0xEEF2F6;
	private static final int EARTH_BROWN = 0x8B6B44;
	private static final int EARTH_DARK = 0x5E4A33;
	private static final int LIFE_GREEN = 0x6CFF8A;
	private static final int LIFE_PALE = 0xC8FFD6;
	private static final int BLOOD_RED = 0xB0102A;
	private static final int BLOOD_DARK = 0x5A0012;
	private static final int SHADOW_PURPLE = 0x6A3A9A;
	private static final int SHADOW_DARK = 0x2E1A40;
	private static final int WIND_PALE = 0xE6FFF0;
	private static final int WIND_GREEN = 0xB6FFB0;
	private static final int SPACE_VIOLET = 0x5A3FD0;
	private static final int SPACE_DARK = 0x2A1A6A;
	private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

	private enum Kind { CRESCENT, SWORD_HALO, SWORD_FALL, PHOENIX, LOTUS, STORM, SHOCKWAVE, SPIRAL, DRAIN, GALE, IMPLOSION }

	/** A short animation: drawn every client tick from age 0 to life (inclusive). */
	private static final class Anim {
		final Kind kind;
		final Vec3 pos;
		final Vec3 target;
		final float f;
		final int life;
		final float seed;
		int age;

		Anim(Kind kind, Vec3 pos, Vec3 target, float f, int life, float seed) {
			this.kind = kind;
			this.pos = pos;
			this.target = target;
			this.f = f;
			this.life = Math.max(1, life);
			this.seed = seed;
		}
	}

	/** A lasting effect on one entity for {@code left} more ticks. */
	private static final class Mark {
		final Technique technique;
		final int entityId;
		final Vec3 anchor;
		final float seed;
		int left;
		int age;

		Mark(Technique technique, int entityId, Vec3 anchor, int left, float seed) {
			this.technique = technique;
			this.entityId = entityId;
			this.anchor = anchor;
			this.left = left;
			this.seed = seed;
		}
	}

	private static final List<Anim> ANIMS = new ArrayList<>();
	private static final List<Mark> MARKS = new ArrayList<>();
	/** Animations and marks belong to one world: they are dropped when the client changes or leaves it. */
	private static @Nullable ClientLevel lastLevel;

	public static void play(Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		Technique[] all = Technique.values();
		if (level == null || fx.a() < 0 || fx.a() >= all.length) {
			return;
		}
		Technique technique = all[fx.a()];
		if (technique.dao() == null) {
			return;
		}
		syncLevel(level);
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		float f = fx.f();
		RandomSource random = CommonFx.random();
		switch (fx.b()) {
			case DaoTechniqueSub.HIT -> hit(technique, pos, f);
			case DaoTechniqueSub.SWORD_FALL -> add(new Anim(Kind.SWORD_FALL, pos, pos, Math.max(1.0F, f), Math.max(1, Math.round(f)) + 1,
					random.nextFloat()));
			case DaoTechniqueSub.BOLT -> bolt(pos, target, f);
			case DaoTechniqueSub.MARK -> mark(level, technique, pos, (int) Math.round(target.x), (int) Math.round(target.y));
			case DaoTechniqueSub.END -> end(technique, pos, (int) Math.round(target.x), f);
			case DaoTechniqueSub.DRAIN -> add(new Anim(Kind.DRAIN, pos, target, f, 10, random.nextFloat()));
			case DaoTechniqueSub.HEAL -> heal(technique, pos, f);
			case DaoTechniqueSub.DOMAIN -> domain(technique, pos, f);
			default -> cast(level, technique, pos, target, f);
		}
	}

	/** Every client tick: advances the animations and draws the marks. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		syncLevel(level);
		if (level == null) {
			return;
		}
		if (!ANIMS.isEmpty()) {
			List<Anim> running = new ArrayList<>(ANIMS);
			for (Anim anim : running) {
				draw(level, anim);
				anim.age++;
			}
			ANIMS.removeIf(anim -> anim.age > anim.life);
		}
		if (!MARKS.isEmpty()) {
			Iterator<Mark> iterator = MARKS.iterator();
			while (iterator.hasNext()) {
				Mark mark = iterator.next();
				Entity entity = level.getEntity(mark.entityId);
				if (entity == null || entity.isRemoved() || --mark.left < 0) {
					iterator.remove();
					continue;
				}
				drawMark(minecraft, mark, entity);
				mark.age++;
			}
		}
	}

	private static void syncLevel(@Nullable ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			ANIMS.clear();
			MARKS.clear();
		}
	}

	private static void add(Anim anim) {
		if (ANIMS.size() >= MAX_ANIMS) {
			ANIMS.remove(0);
		}
		ANIMS.add(anim);
	}

	// ================================================================== one-shot effects

	/** The main effect of each technique (sub CAST). */
	private static void cast(ClientLevel level, Technique technique, Vec3 pos, Vec3 target, float f) {
		RandomSource random = CommonFx.random();
		switch (technique) {
			case SWORD_QI -> {
				int life = Mth.clamp((int) Math.ceil(pos.distanceTo(target) / 4.0D), 2, 6);
				add(new Anim(Kind.CRESCENT, pos, target, f, life, random.nextFloat()));
				CommonFx.add(ParticleTypes.SWEEP_ATTACK, pos, Vec3.ZERO, false);
			}
			case THOUSAND_SWORDS -> {
				add(new Anim(Kind.SWORD_HALO, pos, pos, f, 24, random.nextFloat()));
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), SWORD_BLUE, 3.0F, 40);
				if (EffectQuality.maximal()) {
					CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), SWORD_WHITE, Math.min(f, 10.0F), 80);
				}
			}
			case FLAME_PALM -> flameCone(pos, target, f);
			case PHOENIX_FLAME -> {
				add(new Anim(Kind.PHOENIX, pos, target, f, Math.max(1, Math.round(f)), random.nextFloat()));
				CommonFx.burst(pos, FIRE_ORANGE, 14, 1.2F);
			}
			case FROST_LOTUS -> {
				add(new Anim(Kind.LOTUS, pos, pos, f, 14, random.nextFloat() * Mth.TWO_PI));
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), ICE_WHITE, 1.0F, 16);
			}
			case GLACIER_PRISON -> {
				// pos = the sealed monster, target = the caster's palm.
				CommonFx.beam(target, pos, ICE_BLUE, 0.15F);
				snow(pos, f * 0.5F, 24);
			}
			case THUNDER_PALM -> {
				CommonFx.burst(pos, THUNDER_VIOLET, 8, 0.6F);
				spray(ParticleTypes.ELECTRIC_SPARK, pos, 6, 0.25D);
			}
			case HEAVENLY_THUNDER -> add(new Anim(Kind.STORM, pos, pos, f, 44, random.nextFloat()));
			case IRON_BODY -> {
				int n = EffectQuality.scale(30);
				for (int i = 0; i < n; i++) {
					double angle = random.nextDouble() * Mth.TWO_PI;
					double y = random.nextDouble() * Math.max(1.0F, f);
					Vec3 at = pos.add(Math.cos(angle) * 0.6D, y, Math.sin(angle) * 0.6D);
					CommonFx.add(CommonFx.dust(i % 3 == 0 ? IRON_SHINE : IRON_GREY, 1.0F), at, Vec3.ZERO, false);
				}
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), IRON_GREY, 1.2F, 24);
				spray(ParticleTypes.CRIT, pos.add(0.0D, 1.0D, 0.0D), 8, 0.4D);
			}
			case MOUNTAIN_STOMP -> {
				add(new Anim(Kind.SHOCKWAVE, pos, pos, f, 9, random.nextFloat()));
				int poofs = EffectQuality.scale(10);
				for (int i = 0; i < poofs; i++) {
					double angle = Mth.TWO_PI * i / poofs;
					CommonFx.add(ParticleTypes.POOF, pos.add(Math.cos(angle) * 0.6D, 0.2D, Math.sin(angle) * 0.6D),
							new Vec3(Math.cos(angle) * 0.25D, 0.05D, Math.sin(angle) * 0.25D), false);
				}
			}
			case SPRING_OF_LIFE -> {
				add(new Anim(Kind.SPIRAL, pos, pos, f, 24, random.nextFloat() * Mth.TWO_PI));
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), LIFE_GREEN, f, 56);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), LIFE_PALE, f * 0.5F, 32);
			}
			case LIFE_DRAIN -> {
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), BLOOD_RED, f, 56);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), BLOOD_DARK, f * 0.6F, 36);
			}
			case SHADOW_STEP -> {
				// pos = where the caster vanished, target = where they stepped out.
				shadowPuff(pos, f);
				shadowPuff(target, f);
				Vec3 delta = target.subtract(pos);
				int n = EffectQuality.scale(Math.min(60, (int) Math.ceil(delta.length() * 1.5D)));
				DustParticleOptions streak = CommonFx.dust(SHADOW_DARK, 1.2F);
				for (int i = 0; i <= n; i++) {
					Vec3 at = pos.add(delta.scale((double) i / Math.max(1, n))).add(0.0D, 1.0D + random.nextGaussian() * 0.15D, 0.0D);
					CommonFx.add(streak, at, Vec3.ZERO, false);
				}
			}
			case GALE -> {
				add(new Anim(Kind.GALE, pos, pos, f, 12, random.nextFloat()));
				int clouds = EffectQuality.scale(24);
				for (int i = 0; i < clouds; i++) {
					double angle = Mth.TWO_PI * i / clouds + random.nextDouble() * 0.2D;
					double speed = 0.4D + random.nextDouble() * 0.25D;
					CommonFx.add(ParticleTypes.CLOUD, pos.add(Math.cos(angle) * 0.8D, 0.4D + random.nextDouble() * 1.2D, Math.sin(angle) * 0.8D),
							new Vec3(Math.cos(angle) * speed, 0.02D, Math.sin(angle) * speed), false);
				}
				int gusts = EffectQuality.maximal() ? 4 : EffectQuality.minimal() ? 0 : 2;
				for (int i = 0; i < gusts; i++) {
					double angle = random.nextDouble() * Mth.TWO_PI;
					CommonFx.add(ParticleTypes.SMALL_GUST, pos.add(Math.cos(angle) * 1.5D, 0.8D, Math.sin(angle) * 1.5D), Vec3.ZERO, false);
				}
			}
			case SPACE_BLINK -> {
				// Space folds in at the origin and unfolds at the arrival.
				implode(pos.add(0.0D, Math.max(0.5F, f * 0.5F), 0.0D), 1.4D, 30);
				unfold(target.add(0.0D, Math.max(0.5F, f * 0.5F), 0.0D), 1.6D, 30);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), SPACE_DARK, 1.0F, 20);
				CommonFx.ring(target.add(0.0D, 0.1D, 0.0D), SPACE_VIOLET, 1.4F, 28);
			}
			case SPACE_PRISON -> {
				add(new Anim(Kind.IMPLOSION, pos, pos, Math.max(2.0F, f), 12, random.nextFloat()));
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), SPACE_VIOLET, Math.max(2.0F, f), 30, (float) level.getGameTime());
			}
			default -> {
			}
		}
	}

	/** An impact on one monster. */
	private static void hit(Technique technique, Vec3 pos, float size) {
		Dao dao = technique.dao();
		if (dao == null) {
			return;
		}
		float radius = Math.max(0.4F, size * 0.6F);
		switch (dao) {
			case SWORD -> {
				spray(ParticleTypes.CRIT, pos, 6, 0.35D);
				spray(ParticleTypes.ENCHANTED_HIT, pos, 4, 0.3D);
				CommonFx.burst(pos, SWORD_WHITE, 8, radius);
			}
			case FIRE -> {
				spray(ParticleTypes.FLAME, pos, 10, 0.12D);
				CommonFx.burst(pos, FIRE_ORANGE, 8, radius);
				if (EffectQuality.maximal()) {
					spray(ParticleTypes.LAVA, pos, 2, 0.0D);
				}
			}
			case ICE -> snow(pos, radius, 10);
			case LIGHTNING -> spray(ParticleTypes.ELECTRIC_SPARK, pos, 10, 0.3D);
			case BODY -> {
				if (technique == Technique.IRON_BODY) {
					spray(ParticleTypes.CRIT, pos, 8, 0.3D);
					CommonFx.burst(pos, IRON_GREY, 10, radius);
				} else {
					CommonFx.burst(pos, EARTH_BROWN, 12, radius);
					spray(ParticleTypes.POOF, pos.add(0.0D, -size * 0.5D, 0.0D), 3, 0.05D);
				}
			}
			case LIFE -> CommonFx.burst(pos, BLOOD_RED, 10, radius);
			case SHADOW -> {
				spray(ParticleTypes.CRIT, pos, 8, 0.35D);
				spray(ParticleTypes.SQUID_INK, pos, 4, 0.08D);
				CommonFx.add(ParticleTypes.SWEEP_ATTACK, pos, Vec3.ZERO, false);
				CommonFx.burst(pos, SHADOW_PURPLE, 10, radius);
			}
			case SPACE -> {
				implode(pos, 0.8D, 10);
				CommonFx.burst(pos, SPACE_VIOLET, 8, radius);
			}
		}
	}

	/** A particle lightning bolt (never an entity); f >= 1.5 marks a heavenly bolt from the sky. */
	private static void bolt(Vec3 from, Vec3 to, float f) {
		int segments = Mth.clamp((int) Math.ceil(from.distanceTo(to) / 1.4D), 3, 14);
		CommonFx.bolt(from, to, THUNDER_VIOLET, segments);
		if (EffectQuality.maximal() || f >= 1.5F && !EffectQuality.minimal()) {
			CommonFx.bolt(from, to, THUNDER_CORE, segments);
		}
		if (f >= 1.5F) {
			CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | THUNDER_CORE), to.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, true);
			CommonFx.ring(to.add(0.0D, 0.1D, 0.0D), THUNDER_VIOLET, 1.6F, 24);
			CommonFx.burst(to.add(0.0D, 0.5D, 0.0D), THUNDER_CORE, 14, 1.4F);
		} else {
			CommonFx.burst(to, THUNDER_CORE, 6, 0.6F);
		}
	}

	/** A living being healed: hearts above, green motes rising around it (red-green for a drain). */
	private static void heal(Technique technique, Vec3 feet, float height) {
		RandomSource random = CommonFx.random();
		float h = Math.max(0.5F, height);
		int hearts = technique == Technique.LIFE_DRAIN ? 1 : EffectQuality.maximal() ? 4 : 2;
		for (int i = 0; i < hearts; i++) {
			CommonFx.add(ParticleTypes.HEART, feet.add(random.nextGaussian() * 0.3D, h + 0.3D + random.nextDouble() * 0.3D,
					random.nextGaussian() * 0.3D), Vec3.ZERO, false);
		}
		int motes = EffectQuality.scale(14);
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			int color = technique == Technique.LIFE_DRAIN && i % 2 == 0 ? BLOOD_RED : i % 3 == 0 ? LIFE_PALE : LIFE_GREEN;
			CommonFx.add(CommonFx.dust(color, 0.9F), feet.add(Math.cos(angle) * 0.5D, random.nextDouble() * h, Math.sin(angle) * 0.5D),
					Vec3.ZERO, false);
		}
		if (!EffectQuality.minimal()) {
			CommonFx.add(ParticleTypes.HAPPY_VILLAGER, feet.add(random.nextGaussian() * 0.4D, h * 0.6D, random.nextGaussian() * 0.4D),
					Vec3.ZERO, false);
		}
	}

	/** One Dao Domain pulse: a ring at the domain edge plus the flavour of the Dao inside it. */
	private static void domain(Technique technique, Vec3 pos, float radius) {
		Dao dao = technique.dao();
		if (dao == null) {
			return;
		}
		RandomSource random = CommonFx.random();
		float r = Math.max(1.0F, radius);
		CommonFx.ring(pos.add(0.0D, 0.15D, 0.0D), dao.color(), r, 40);
		int n = EffectQuality.scale(10);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = r * Math.sqrt(random.nextDouble());
			Vec3 at = pos.add(Math.cos(angle) * distance, 0.2D, Math.sin(angle) * distance);
			switch (dao) {
				case SWORD -> {
					// A short flash of sword light, tilted.
					Vec3 tilt = new Vec3(Math.cos(angle + 1.2D) * 0.5D, 0.9D, Math.sin(angle + 1.2D) * 0.5D);
					line(at.add(0.0D, 0.4D, 0.0D), at.add(0.0D, 0.4D, 0.0D).add(tilt), CommonFx.dust(SWORD_WHITE, 0.7F), 5);
				}
				case FIRE -> CommonFx.add(ParticleTypes.FLAME, at, new Vec3(0.0D, 0.04D, 0.0D), false);
				case ICE -> CommonFx.add(ParticleTypes.SNOWFLAKE, at.add(0.0D, 1.5D + random.nextDouble() * 2.0D, 0.0D),
						new Vec3(0.0D, -0.05D, 0.0D), false);
				case LIGHTNING -> CommonFx.add(ParticleTypes.ELECTRIC_SPARK, at, new Vec3(random.nextGaussian() * 0.1D, 0.1D,
						random.nextGaussian() * 0.1D), false);
				case BODY -> CommonFx.add(CommonFx.dust(i % 2 == 0 ? EARTH_BROWN : IRON_GREY, 1.3F), at, Vec3.ZERO, false);
				case LIFE -> CommonFx.add(i % 3 == 0 ? ParticleTypes.HAPPY_VILLAGER : CommonFx.dust(LIFE_GREEN, 0.9F),
						at.add(0.0D, random.nextDouble() * 1.5D, 0.0D), Vec3.ZERO, false);
				case SHADOW -> CommonFx.add(i % 3 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.SMOKE, at.add(0.0D, random.nextDouble(), 0.0D),
						new Vec3(0.0D, 0.02D, 0.0D), false);
				case SPACE -> CommonFx.add(ParticleTypes.REVERSE_PORTAL, at.add(0.0D, random.nextDouble() * 1.5D, 0.0D),
						pos.subtract(at).normalize().scale(0.03D), false);
			}
		}
		if (dao == Dao.BODY) {
			CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), IRON_GREY, 1.2F, 16);
		}
	}

	/** A lasting effect starts on an entity. */
	private static void mark(ClientLevel level, Technique technique, Vec3 anchor, int entityId, int duration) {
		if (duration <= 0) {
			return;
		}
		MARKS.removeIf(mark -> mark.entityId == entityId && mark.technique == technique);
		if (MARKS.size() >= MAX_MARKS) {
			MARKS.remove(0);
		}
		MARKS.add(new Mark(technique, entityId, anchor, duration, CommonFx.random().nextFloat() * Mth.TWO_PI));
		Entity entity = level.getEntity(entityId);
		if (entity == null) {
			return;
		}
		AABB box = entity.getBoundingBox();
		switch (technique) {
			case GLACIER_PRISON -> {
				snow(box.getCenter(), (float) Math.max(0.6D, box.getYsize() * 0.5D), 16);
				iceShell(box, 0.0F, true);
			}
			case SPACE_PRISON -> {
				line(box.getCenter(), anchor.add(0.0D, 0.8D, 0.0D), CommonFx.dust(SPACE_VIOLET, 0.8F),
						Math.min(40, (int) Math.ceil(box.getCenter().distanceTo(anchor) * 3.0D)));
				implode(box.getCenter(), 1.0D, 10);
			}
			default -> {
			}
		}
	}

	/** A lasting effect ends (or the empowered shadow strike landed). */
	private static void end(Technique technique, Vec3 pos, int entityId, float size) {
		MARKS.removeIf(mark -> mark.entityId == entityId && mark.technique == technique);
		RandomSource random = CommonFx.random();
		float h = Math.max(0.6F, size);
		switch (technique) {
			case GLACIER_PRISON -> {
				// The ice shell shatters.
				snow(pos, h * 0.6F, 20);
				CommonFx.burst(pos, ICE_WHITE, 18, h);
				spray(ParticleTypes.ITEM_SNOWBALL, pos, 8, 0.15D);
			}
			case SPACE_PRISON -> {
				unfold(pos, 1.2D, 16);
				CommonFx.burst(pos, SPACE_VIOLET, 12, h * 0.7F);
			}
			case IRON_BODY -> {
				int n = EffectQuality.scale(16);
				for (int i = 0; i < n; i++) {
					CommonFx.add(CommonFx.dust(i % 2 == 0 ? IRON_GREY : IRON_SHINE, 0.9F),
							pos.add(random.nextGaussian() * 0.35D, random.nextGaussian() * h * 0.3D, random.nextGaussian() * 0.35D), Vec3.ZERO, false);
				}
			}
			case SHADOW_STEP -> spray(ParticleTypes.SMOKE, pos, 8, 0.04D);
			default -> {
			}
		}
	}

	// ================================================================== animations

	private static void draw(ClientLevel level, Anim anim) {
		switch (anim.kind) {
			case CRESCENT -> drawCrescent(anim);
			case SWORD_HALO -> drawHalo(anim);
			case SWORD_FALL -> drawSwordFall(anim);
			case PHOENIX -> drawPhoenix(anim);
			case LOTUS -> drawLotus(anim);
			case STORM -> drawStorm(anim);
			case SHOCKWAVE -> drawShockwave(level, anim);
			case SPIRAL -> drawSpiral(anim);
			case DRAIN -> drawDrain(anim);
			case GALE -> drawGale(anim);
			case IMPLOSION -> drawImplosion(anim);
		}
	}

	/** Sword Qi: a crescent of white-blue motes flying from pos to target, tips trailing, centre leading. */
	private static void drawCrescent(Anim anim) {
		Vec3 delta = anim.target.subtract(anim.pos);
		double length = delta.length();
		if (length < 1.0E-3D) {
			return;
		}
		RandomSource random = CommonFx.random();
		Vec3 dir = delta.scale(1.0D / length);
		Vec3 right = rightOf(dir);
		Vec3 up = right.cross(dir);
		double half = Math.max(0.6D, anim.f * 0.5D + 0.3D);
		DustParticleOptions white = CommonFx.dust(SWORD_WHITE, 0.9F);
		DustParticleOptions blue = CommonFx.dust(SWORD_BLUE, 0.8F);
		int n = EffectQuality.scale(14);
		for (int step = 0; step < 2; step++) {
			double t = Math.min(1.0D, (anim.age + 0.5D * step) / anim.life);
			Vec3 centre = anim.pos.add(delta.scale(t));
			for (int i = 0; i <= n; i++) {
				double u = (double) i / n * 2.0D - 1.0D;
				double bend = (1.0D - u * u) * 0.55D - 0.3D;
				Vec3 at = centre.add(right.scale(u * half)).add(dir.scale(bend)).add(up.scale(u * 0.18D));
				CommonFx.add(i % 3 == 0 ? blue : white, at, Vec3.ZERO, false);
				if (EffectQuality.maximal() && random.nextInt(6) == 0) {
					CommonFx.add(ParticleTypes.END_ROD, at, dir.scale(0.05D), false);
				}
			}
		}
		if (anim.age == anim.life) {
			CommonFx.burst(anim.target, SWORD_WHITE, 10, 0.8F);
		}
	}

	/** Thousand Swords: a turning ring of swords of light above the caster, points down. */
	private static void drawHalo(Anim anim) {
		if (anim.age % 2 != 0) {
			return;
		}
		int swords = EffectQuality.minimal() ? 6 : 12;
		double radius = 1.8D + anim.age * 0.02D;
		double y = anim.pos.y + 3.0D + anim.age * 0.04D;
		for (int i = 0; i < swords; i++) {
			double angle = anim.seed * Mth.TWO_PI + anim.age * 0.12D + Mth.TWO_PI * i / swords;
			Vec3 tip = new Vec3(anim.pos.x + Math.cos(angle) * radius, y, anim.pos.z + Math.sin(angle) * radius);
			lightSword(tip, angle + Math.PI * 0.5D, 0.55D);
		}
		if (anim.age == 0) {
			int rods = EffectQuality.scale(16);
			RandomSource random = CommonFx.random();
			for (int i = 0; i < rods; i++) {
				CommonFx.add(ParticleTypes.END_ROD, anim.pos.add(random.nextGaussian() * 0.4D, 1.2D, random.nextGaussian() * 0.4D),
						new Vec3(random.nextGaussian() * 0.05D, 0.25D, random.nextGaussian() * 0.05D), false);
			}
		}
	}

	/** One sword of light falling from 7 blocks up onto pos within f ticks, then a bright impact. */
	private static void drawSwordFall(Anim anim) {
		int fall = Math.max(1, Math.round(anim.f));
		if (anim.age <= fall) {
			double height = 7.0D * (1.0D - (double) anim.age / fall);
			Vec3 tip = anim.pos.add(0.0D, height + 0.1D, 0.0D);
			lightSword(tip, anim.seed * Mth.TWO_PI, 1.0D);
			if (anim.age < fall && !EffectQuality.minimal()) {
				// A faint streak of light above it.
				line(tip.add(0.0D, 2.3D, 0.0D), tip.add(0.0D, 3.6D, 0.0D), CommonFx.dust(SWORD_BLUE, 0.5F), 4);
			}
		}
		if (anim.age == fall) {
			RandomSource random = CommonFx.random();
			spray(ParticleTypes.CRIT, anim.pos.add(0.0D, 0.3D, 0.0D), 6, 0.3D);
			spray(ParticleTypes.ENCHANTED_HIT, anim.pos.add(0.0D, 0.6D, 0.0D), 4, 0.25D);
			CommonFx.ring(anim.pos.add(0.0D, 0.1D, 0.0D), SWORD_WHITE, 0.8F, 14);
			if (EffectQuality.maximal()) {
				CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | SWORD_BLUE),
						anim.pos.add(random.nextGaussian() * 0.1D, 0.5D, random.nextGaussian() * 0.1D), Vec3.ZERO, false);
			}
		}
	}

	/** Phoenix Flame: a fiery body flying 1 block per tick, flapping wing arcs, a burning tail; a burst at the end. */
	private static void drawPhoenix(Anim anim) {
		RandomSource random = CommonFx.random();
		Vec3 delta = anim.target.subtract(anim.pos);
		double length = delta.length();
		Vec3 dir = length < 1.0E-3D ? new Vec3(0.0D, 0.0D, 1.0D) : delta.scale(1.0D / length);
		double t = Math.min(1.0D, (double) anim.age / anim.life);
		Vec3 head = anim.pos.add(delta.scale(t));
		Vec3 right = rightOf(dir);
		Vec3 up = right.cross(dir);
		int body = EffectQuality.scale(6);
		for (int i = 0; i < body; i++) {
			Vec3 at = head.add(random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D);
			CommonFx.add(ParticleTypes.FLAME, at, dir.scale(-0.04D), false);
		}
		CommonFx.add(CommonFx.dust(FIRE_YELLOW, 1.6F), head, Vec3.ZERO, false);
		CommonFx.add(CommonFx.dust(FIRE_ORANGE, 1.3F), head.add(dir.scale(0.35D)).add(up.scale(0.15D)), Vec3.ZERO, false);
		double flap = Math.sin(anim.age * 1.1D + anim.seed * Mth.TWO_PI);
		int feathers = EffectQuality.scale(7);
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 1; i <= feathers; i++) {
				double s = (double) i / feathers;
				Vec3 at = head.add(right.scale(side * s * 1.7D)).add(up.scale(flap * s * s * 0.8D + s * 0.15D)).add(dir.scale(-s * 0.6D));
				CommonFx.add(CommonFx.dust(s > 0.7D ? FIRE_RED : FIRE_ORANGE, 1.1F), at, Vec3.ZERO, false);
				if (i == feathers && EffectQuality.maximal()) {
					CommonFx.add(ParticleTypes.FLAME, at, dir.scale(-0.03D), false);
				}
			}
		}
		int tail = EffectQuality.scale(5);
		for (int i = 1; i <= tail; i++) {
			Vec3 at = head.add(dir.scale(-0.4D - i * 0.35D)).add(up.scale(-0.05D * i))
					.add(random.nextGaussian() * 0.08D, random.nextGaussian() * 0.08D, random.nextGaussian() * 0.08D);
			CommonFx.add(i % 2 == 0 ? ParticleTypes.FLAME : CommonFx.dust(FIRE_RED, 1.0F), at, Vec3.ZERO, false);
		}
		if (EffectQuality.chance(random, 0.3F)) {
			CommonFx.add(ParticleTypes.SMOKE, head.add(dir.scale(-1.2D)), Vec3.ZERO, false);
		}
		if (EffectQuality.maximal() && random.nextInt(3) == 0) {
			CommonFx.add(ParticleTypes.LAVA, head, Vec3.ZERO, false);
		}
		if (anim.age >= anim.life) {
			int flames = EffectQuality.scale(30);
			for (int i = 0; i < flames; i++) {
				Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6D, random.nextGaussian()).normalize();
				CommonFx.add(i % 5 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, head, out.scale(0.15D + random.nextDouble() * 0.12D), false);
			}
			CommonFx.ring(head.add(0.0D, -0.3D, 0.0D), FIRE_ORANGE, 2.2F, 28);
			CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | FIRE_YELLOW), head, Vec3.ZERO, false);
		}
	}

	/** Frost Lotus: petals of ice-blue dust opening outwards over the ground, snowflakes drifting. */
	private static void drawLotus(Anim anim) {
		RandomSource random = CommonFx.random();
		double progress = Math.min(1.0D, (anim.age + 1.0D) / anim.life);
		double eased = 1.0D - (1.0D - progress) * (1.0D - progress);
		double radius = anim.f * eased;
		if (anim.age % 2 == 0) {
			petals(anim.pos, anim.seed, radius, 0.6D, 8);
			if (EffectQuality.maximal()) {
				petals(anim.pos.add(0.0D, 0.25D, 0.0D), anim.seed + Math.PI / 8.0D, radius * 0.55D, 0.9D, 8);
			}
		}
		if (anim.age % 3 == 0) {
			int flakes = EffectQuality.scale(6);
			for (int i = 0; i < flakes; i++) {
				double angle = random.nextDouble() * Mth.TWO_PI;
				double distance = radius * Math.sqrt(random.nextDouble());
				CommonFx.add(ParticleTypes.SNOWFLAKE, anim.pos.add(Math.cos(angle) * distance, 0.3D + random.nextDouble() * 1.2D, Math.sin(angle) * distance),
						new Vec3(Math.cos(angle) * 0.03D, -0.01D, Math.sin(angle) * 0.03D), false);
			}
		}
		if (anim.age == anim.life) {
			CommonFx.ring(anim.pos.add(0.0D, 0.15D, 0.0D), ICE_WHITE, anim.f, 64);
			CommonFx.ring(anim.pos.add(0.0D, 0.15D, 0.0D), ICE_DEEP, anim.f * 0.8F, 48);
			int burst = EffectQuality.scale(20);
			for (int i = 0; i < burst; i++) {
				double angle = Mth.TWO_PI * i / burst;
				CommonFx.add(ParticleTypes.SNOWFLAKE, anim.pos.add(Math.cos(angle) * 0.8D, 0.5D, Math.sin(angle) * 0.8D),
						new Vec3(Math.cos(angle) * 0.3D, 0.05D, Math.sin(angle) * 0.3D), false);
			}
		}
	}

	private static void petals(Vec3 centre, double phase, double radius, double lift, int count) {
		if (radius < 0.2D) {
			return;
		}
		DustParticleOptions edge = CommonFx.dust(ICE_BLUE, 1.0F);
		DustParticleOptions rib = CommonFx.dust(ICE_WHITE, 0.8F);
		int points = EffectQuality.scale(6);
		for (int j = 0; j < count; j++) {
			double angle = phase + Mth.TWO_PI * j / count;
			Vec3 out = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
			Vec3 side = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
			for (int i = 1; i <= points; i++) {
				double s = (double) i / points;
				double width = Math.sin(s * Math.PI) * 0.22D * radius;
				Vec3 mid = centre.add(out.scale(s * radius)).add(0.0D, 0.15D + Math.sin(s * Math.PI * 0.9D) * lift, 0.0D);
				CommonFx.add(edge, mid.add(side.scale(width)), Vec3.ZERO, false);
				CommonFx.add(edge, mid.add(side.scale(-width)), Vec3.ZERO, false);
				if (i % 2 == 0) {
					CommonFx.add(rib, mid, Vec3.ZERO, false);
				}
			}
		}
	}

	/** Heavenly Thunder: a dark crackling storm disc gathering high above the caster for the 2 s of bolts. */
	private static void drawStorm(Anim anim) {
		RandomSource random = CommonFx.random();
		Vec3 centre = anim.pos.add(0.0D, 13.0D, 0.0D);
		double radius = 3.0D + Math.min(1.0D, anim.age / 10.0D) * 3.0D;
		int motes = EffectQuality.scale(8);
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = radius * Math.sqrt(random.nextDouble());
			Vec3 at = centre.add(Math.cos(angle) * distance, random.nextGaussian() * 0.3D, Math.sin(angle) * distance);
			CommonFx.add(CommonFx.dust(i % 3 == 0 ? STORM_GREY : STORM_DARK, 3.0F), at,
					new Vec3(-Math.sin(angle) * 0.03D, 0.0D, Math.cos(angle) * 0.03D), true);
		}
		if (EffectQuality.chance(random, 0.35F)) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = radius * 0.8D * random.nextDouble();
			Vec3 at = centre.add(Math.cos(angle) * distance, -0.3D, Math.sin(angle) * distance);
			CommonFx.add(CommonFx.dust(THUNDER_VIOLET, 1.8F), at, Vec3.ZERO, true);
			CommonFx.add(ParticleTypes.ELECTRIC_SPARK, at, new Vec3(random.nextGaussian() * 0.1D, -0.1D, random.nextGaussian() * 0.1D), true);
		}
	}

	/** Mountain Stomp: a ring of earth-coloured dust (and some dust of the ground block) racing outwards. */
	private static void drawShockwave(ClientLevel level, Anim anim) {
		RandomSource random = CommonFx.random();
		double radius = 0.5D + (anim.f - 0.5D) * (anim.age + 1.0D) / (anim.life + 1.0D);
		int n = EffectQuality.scale((int) (16 + radius * 6.0D));
		BlockState ground = level.getBlockState(BlockPos.containing(anim.pos.x, anim.pos.y - 0.5D, anim.pos.z));
		BlockParticleOption crumbs = ground.isAir() ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
		DustParticleOptions brown = CommonFx.dust(EARTH_BROWN, 1.6F);
		DustParticleOptions dark = CommonFx.dust(EARTH_DARK, 1.4F);
		for (int i = 0; i < n; i++) {
			double angle = Mth.TWO_PI * i / n + random.nextDouble() * 0.1D;
			Vec3 at = anim.pos.add(Math.cos(angle) * radius, 0.1D + random.nextDouble() * 0.3D, Math.sin(angle) * radius);
			CommonFx.add(i % 2 == 0 ? brown : dark, at, Vec3.ZERO, false);
			if (crumbs != null && random.nextInt(3) == 0) {
				CommonFx.add(crumbs, at, new Vec3(Math.cos(angle) * 0.1D, 0.25D, Math.sin(angle) * 0.1D), false);
			}
		}
		if (anim.age == 0) {
			CommonFx.burst(anim.pos.add(0.0D, 0.3D, 0.0D), EARTH_BROWN, 20, 1.5F);
		}
	}

	/** Spring of Life: two green helices rising around the caster, sparkles of new growth. */
	private static void drawSpiral(Anim anim) {
		RandomSource random = CommonFx.random();
		double height = 2.6D * anim.age / anim.life;
		int helices = EffectQuality.maximal() ? 3 : 2;
		DustParticleOptions green = CommonFx.dust(LIFE_GREEN, 1.1F);
		DustParticleOptions pale = CommonFx.dust(LIFE_PALE, 0.8F);
		for (int k = 0; k < helices; k++) {
			for (int s = 0; s < 3; s++) {
				double angle = anim.seed + anim.age * 0.55D - s * 0.12D + Mth.TWO_PI * k / helices;
				double y = height - s * 0.04D;
				CommonFx.add(s == 0 ? green : pale, anim.pos.add(Math.cos(angle) * 0.9D, Math.max(0.05D, y), Math.sin(angle) * 0.9D), Vec3.ZERO, false);
			}
		}
		if (EffectQuality.chance(random, 0.4F)) {
			CommonFx.add(ParticleTypes.HAPPY_VILLAGER, anim.pos.add(random.nextGaussian() * 0.8D, random.nextDouble() * 2.0D, random.nextGaussian() * 0.8D),
					Vec3.ZERO, false);
		}
	}

	/** Life Drain: red motes flowing from a monster to the caster. */
	private static void drawDrain(Anim anim) {
		RandomSource random = CommonFx.random();
		Vec3 delta = anim.target.subtract(anim.pos);
		Vec3 dir = delta.lengthSqr() < 1.0E-4D ? UP : delta.normalize();
		Vec3 side = rightOf(dir);
		double t = (anim.age + 1.0D) / anim.life;
		int n = EffectQuality.scale(4);
		DustParticleOptions red = CommonFx.dust(BLOOD_RED, 1.0F);
		DustParticleOptions dark = CommonFx.dust(BLOOD_DARK, 0.8F);
		for (int i = 0; i < n; i++) {
			double tt = Mth.clamp(t - i * 0.05D, 0.0D, 1.0D);
			double wobble = Math.sin(tt * Math.PI * 2.0D + anim.seed * Mth.TWO_PI) * 0.35D * (1.0D - tt);
			Vec3 at = anim.pos.add(delta.scale(tt)).add(side.scale(wobble)).add(0.0D, Math.sin(tt * Math.PI) * 0.4D, 0.0D)
					.add(random.nextGaussian() * 0.06D, random.nextGaussian() * 0.06D, random.nextGaussian() * 0.06D);
			CommonFx.add(i % 2 == 0 ? red : dark, at, Vec3.ZERO, false);
		}
		if (anim.age == 0) {
			CommonFx.burst(anim.pos, BLOOD_DARK, 6, 0.5F);
		}
	}

	/** Gale: three arcs of wind sweeping outwards from the caster. */
	private static void drawGale(Anim anim) {
		double radius = 1.0D + (anim.f - 1.0D) * (anim.age + 1.0D) / anim.life;
		int points = EffectQuality.scale(10);
		for (int k = 0; k < 3; k++) {
			double start = anim.seed * Mth.TWO_PI + k * Mth.TWO_PI / 3.0D + anim.age * 0.35D;
			DustParticleOptions dust = CommonFx.dust(k % 2 == 0 ? WIND_PALE : WIND_GREEN, 1.0F);
			for (int i = 0; i <= points; i++) {
				double angle = start + 1.6D * i / Math.max(1, points);
				double y = 0.4D + k * 0.5D + Math.sin(angle * 2.0D) * 0.2D;
				CommonFx.add(dust, anim.pos.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius), Vec3.ZERO, false);
			}
		}
	}

	/** Space Prison: dark violet rings closing in on the centre, then space folds shut. */
	private static void drawImplosion(Anim anim) {
		double radius = anim.f * (1.0D - (double) anim.age / anim.life) + 0.3D;
		CommonFx.ring(anim.pos.add(0.0D, 0.15D, 0.0D), SPACE_VIOLET, (float) radius, 24);
		CommonFx.ring(anim.pos.add(0.0D, 1.6D, 0.0D), SPACE_DARK, (float) radius, 18);
		if (anim.age % 3 == 0) {
			DustParticleOptions spoke = CommonFx.dust(SPACE_DARK, 0.7F);
			for (int i = 0; i < 6; i++) {
				double angle = anim.seed * Mth.TWO_PI + Mth.TWO_PI * i / 6.0D;
				Vec3 rim = anim.pos.add(Math.cos(angle) * radius, 0.8D, Math.sin(angle) * radius);
				line(rim, anim.pos.add(0.0D, 0.8D, 0.0D), spoke, 4);
			}
		}
		if (anim.age == anim.life) {
			implode(anim.pos.add(0.0D, 1.0D, 0.0D), 1.5D, 30);
		}
	}

	// ================================================================== marks

	private static void drawMark(Minecraft minecraft, Mark mark, Entity entity) {
		AABB box = entity.getBoundingBox();
		RandomSource random = CommonFx.random();
		boolean self = entity == minecraft.player;
		switch (mark.technique) {
			case GLACIER_PRISON -> {
				iceShell(box, mark.seed, mark.age % 5 == 0);
				if (EffectQuality.chance(random, 0.15F)) {
					CommonFx.add(ParticleTypes.SNOWFLAKE, surfacePoint(box, random, 0.25D, 1.0D), new Vec3(0.0D, -0.02D, 0.0D), false);
				}
			}
			case SPACE_PRISON -> {
				if (mark.age % 2 == 0) {
					cage(box, mark.seed + mark.age * 0.05F);
				}
				if (mark.age % 6 == 0) {
					line(box.getCenter(), mark.anchor.add(0.0D, 0.8D, 0.0D), CommonFx.dust(SPACE_DARK, 0.6F),
							Math.min(24, (int) Math.ceil(box.getCenter().distanceTo(mark.anchor) * 2.0D)));
				}
				if (EffectQuality.chance(random, 0.2F)) {
					CommonFx.add(ParticleTypes.PORTAL, surfacePoint(box, random, 0.3D, 1.0D), new Vec3(random.nextGaussian() * 0.2D, 0.1D,
							random.nextGaussian() * 0.2D), false);
				}
			}
			case IRON_BODY -> {
				// A metallic grey shimmer over the skin (kept below the eyes of our own first-person camera).
				double top = self ? 0.7D : 1.0D;
				if (EffectQuality.chance(random, 0.9F)) {
					CommonFx.add(CommonFx.dust(random.nextInt(3) == 0 ? IRON_SHINE : IRON_GREY, 0.7F), surfacePoint(box, random, 0.08D, top),
							Vec3.ZERO, false);
				}
				if (EffectQuality.chance(random, 0.5F)) {
					CommonFx.add(CommonFx.dust(IRON_GREY, 0.6F), surfacePoint(box, random, 0.08D, top), Vec3.ZERO, false);
				}
				if (EffectQuality.chance(random, 0.08F)) {
					CommonFx.add(ParticleTypes.WAX_OFF, surfacePoint(box, random, 0.1D, top), Vec3.ZERO, false);
				}
			}
			case SHADOW_STEP -> {
				// Purple wisps curling around the hands while the shadow strike is ready.
				if (EffectQuality.chance(random, 0.6F)) {
					Vec3 at = surfacePoint(box, random, 0.15D, self ? 0.6D : 0.75D);
					CommonFx.add(ParticleTypes.SMOKE, at, new Vec3(0.0D, 0.01D, 0.0D), false);
					CommonFx.add(CommonFx.dust(SHADOW_PURPLE, 0.8F), at, Vec3.ZERO, false);
				}
				if (EffectQuality.chance(random, 0.06F)) {
					CommonFx.add(ParticleTypes.SQUID_INK, surfacePoint(box, random, 0.15D, 0.5D), new Vec3(0.0D, 0.02D, 0.0D), false);
				}
			}
			default -> {
			}
		}
	}

	/** Six leaning ice crystals around a sealed monster (full crystals when {@code full}, otherwise a few glints). */
	private static void iceShell(AABB box, float seed, boolean full) {
		RandomSource random = CommonFx.random();
		Vec3 centre = box.getCenter();
		double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5D + 0.25D;
		double bottom = box.minY;
		double height = box.getYsize() + 0.25D;
		DustParticleOptions ice = CommonFx.dust(ICE_BLUE, 0.8F);
		DustParticleOptions white = CommonFx.dust(ICE_WHITE, 0.6F);
		for (int j = 0; j < 6; j++) {
			double angle = seed + Mth.TWO_PI * j / 6.0D;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			if (full) {
				int points = EffectQuality.scale(5);
				for (int i = 0; i <= points; i++) {
					double s = (double) i / Math.max(1, points);
					double r = radius * (1.0D - s * 0.35D);
					CommonFx.add(i == points ? white : ice, new Vec3(centre.x + cos * r, bottom + s * height, centre.z + sin * r), Vec3.ZERO, false);
				}
			} else if (EffectQuality.chance(random, 0.25F)) {
				double s = random.nextDouble();
				double r = radius * (1.0D - s * 0.35D);
				CommonFx.add(ice, new Vec3(centre.x + cos * r, bottom + s * height, centre.z + sin * r), Vec3.ZERO, false);
			}
		}
		if (full) {
			CommonFx.add(white, new Vec3(centre.x, bottom + height + 0.1D, centre.z), Vec3.ZERO, false);
		}
	}

	/** Dark violet cage lines around a held monster: six bars with a ring at the top and the bottom. */
	private static void cage(AABB box, float turn) {
		Vec3 centre = box.getCenter();
		double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5D + 0.35D;
		double bottom = box.minY - 0.05D;
		double top = box.maxY + 0.15D;
		DustParticleOptions bar = CommonFx.dust(SPACE_VIOLET, 0.7F);
		int points = EffectQuality.scale(4);
		for (int j = 0; j < 6; j++) {
			double angle = turn + Mth.TWO_PI * j / 6.0D;
			for (int i = 0; i <= points; i++) {
				double y = bottom + (top - bottom) * i / Math.max(1, points);
				CommonFx.add(bar, new Vec3(centre.x + Math.cos(angle) * radius, y, centre.z + Math.sin(angle) * radius), Vec3.ZERO, false);
			}
		}
		CommonFx.ring(new Vec3(centre.x, bottom, centre.z), SPACE_DARK, (float) radius, 12);
		CommonFx.ring(new Vec3(centre.x, top, centre.z), SPACE_DARK, (float) radius, 12);
	}

	// ================================================================== small helpers

	/** Fire cone from the palm towards target (length f): flames rushing forward, orange motes, smoke at the edge. */
	private static void flameCone(Vec3 from, Vec3 target, float length) {
		RandomSource random = CommonFx.random();
		Vec3 delta = target.subtract(from);
		if (delta.lengthSqr() < 1.0E-4D) {
			return;
		}
		Vec3 dir = delta.normalize();
		Vec3 right = rightOf(dir);
		Vec3 up = right.cross(dir);
		double spread = Math.tan(Math.toRadians(30.0D));
		int flames = EffectQuality.scale(45);
		for (int i = 0; i < flames; i++) {
			double s = Math.sqrt(random.nextDouble());
			double distance = s * length;
			double angle = random.nextDouble() * Mth.TWO_PI;
			double r = random.nextDouble() * spread * distance;
			Vec3 at = from.add(dir.scale(distance)).add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
			Vec3 velocity = dir.scale(0.08D + random.nextDouble() * 0.1D);
			ParticleOptions particle = random.nextInt(8) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
			CommonFx.add(particle, at, velocity, false);
			if (i % 3 == 0) {
				CommonFx.add(CommonFx.dust(i % 2 == 0 ? FIRE_ORANGE : FIRE_YELLOW, 1.2F), at, Vec3.ZERO, false);
			}
		}
		int smoke = EffectQuality.scale(6);
		for (int i = 0; i < smoke; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double r = spread * length * 0.9D;
			Vec3 at = from.add(dir.scale(length)).add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
			CommonFx.add(ParticleTypes.SMOKE, at, new Vec3(0.0D, 0.03D, 0.0D), false);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 3; i++) {
				CommonFx.add(ParticleTypes.LAVA, from.add(dir.scale(random.nextDouble() * length)), Vec3.ZERO, false);
			}
		}
	}

	/** Purple smoke and squid ink where a shadow step begins or ends. */
	private static void shadowPuff(Vec3 feet, float height) {
		RandomSource random = CommonFx.random();
		double h = Math.max(1.0F, height);
		int smoke = EffectQuality.scale(14);
		for (int i = 0; i < smoke; i++) {
			Vec3 at = feet.add(random.nextGaussian() * 0.35D, random.nextDouble() * h, random.nextGaussian() * 0.35D);
			CommonFx.add(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at, new Vec3(0.0D, 0.02D, 0.0D), false);
			CommonFx.add(CommonFx.dust(i % 2 == 0 ? SHADOW_PURPLE : SHADOW_DARK, 1.2F), at, Vec3.ZERO, false);
		}
		int ink = EffectQuality.scale(5);
		for (int i = 0; i < ink; i++) {
			CommonFx.add(ParticleTypes.SQUID_INK, feet.add(random.nextGaussian() * 0.3D, 0.3D + random.nextDouble() * h * 0.7D,
					random.nextGaussian() * 0.3D), new Vec3(random.nextGaussian() * 0.03D, 0.03D, random.nextGaussian() * 0.03D), false);
		}
	}

	/** Snowflakes and ice motes bursting from pos. */
	private static void snow(Vec3 pos, float radius, int count) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6D, random.nextGaussian()).normalize();
			CommonFx.add(ParticleTypes.SNOWFLAKE, pos.add(out.scale(radius * 0.3D)), out.scale(0.08D + random.nextDouble() * 0.06D), false);
			if (i % 2 == 0) {
				CommonFx.add(CommonFx.dust(i % 4 == 0 ? ICE_WHITE : ICE_BLUE, 0.9F), pos.add(out.scale(radius * random.nextDouble())), Vec3.ZERO, false);
			}
		}
	}

	/** Space folding in: reverse portal motes rushing into pos from a sphere around it. */
	private static void implode(Vec3 pos, double radius, int count) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			// Reverse portal motes speed up along their velocity: about 30x it over their life.
			CommonFx.add(ParticleTypes.REVERSE_PORTAL, pos.add(out.scale(radius)), out.scale(-radius / 30.0D), false);
		}
	}

	/** Space unfolding: portal motes flying out of pos (they travel from start + offset back to start). */
	private static void unfold(Vec3 pos, double radius, int count) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			Vec3 out = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(radius);
			Vec3 start = pos.add(out);
			CommonFx.add(ParticleTypes.PORTAL, start, out.reverse(), false);
		}
	}

	/** {@code count} particles (before quality) sprayed from pos with random velocity up to {@code speed}. */
	private static void spray(ParticleOptions particle, Vec3 pos, int count, double speed) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			CommonFx.add(particle, pos.add(random.nextGaussian() * 0.15D, random.nextGaussian() * 0.15D, random.nextGaussian() * 0.15D),
					new Vec3(random.nextGaussian() * speed, random.nextGaussian() * speed * 0.5D + speed * 0.3D, random.nextGaussian() * speed), false);
		}
	}

	/** {@code points} motes (before quality) on a straight line. */
	private static void line(Vec3 from, Vec3 to, ParticleOptions particle, int points) {
		int n = EffectQuality.scale(Math.max(1, points));
		Vec3 delta = to.subtract(from);
		for (int i = 0; i <= n; i++) {
			CommonFx.add(particle, from.add(delta.scale((double) i / n)), Vec3.ZERO, false);
		}
	}

	/**
	 * A sword of light standing on its tip: a thin glowing blade going up from {@code tip}, a golden cross guard turned by
	 * {@code angle}, the grip and the pommel. {@code scale} 1 = about 2.1 blocks tall.
	 */
	private static void lightSword(Vec3 tip, double angle, double scale) {
		DustParticleOptions blade = CommonFx.dust(SWORD_WHITE, (float) (0.8D * scale + 0.2D));
		DustParticleOptions edge = CommonFx.dust(SWORD_BLUE, (float) (0.6D * scale + 0.2D));
		DustParticleOptions gold = CommonFx.dust(HILT_GOLD, (float) (0.7D * scale + 0.2D));
		DustParticleOptions grip = CommonFx.dust(HILT_DARK, (float) (0.6D * scale + 0.2D));
		double bladeLength = 1.6D * scale;
		int points = EffectQuality.scale((int) Math.ceil(10 * scale));
		for (int i = 0; i <= points; i++) {
			double y = bladeLength * i / Math.max(1, points);
			CommonFx.add(i == 0 || i % 4 == 2 ? edge : blade, tip.add(0.0D, y, 0.0D), Vec3.ZERO, false);
		}
		Vec3 across = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
		Vec3 guard = tip.add(0.0D, bladeLength, 0.0D);
		int guardPoints = Math.max(2, EffectQuality.scale(3));
		for (int i = -guardPoints; i <= guardPoints; i++) {
			CommonFx.add(gold, guard.add(across.scale(0.35D * scale * i / guardPoints)), Vec3.ZERO, false);
		}
		CommonFx.add(grip, guard.add(0.0D, 0.18D * scale, 0.0D), Vec3.ZERO, false);
		CommonFx.add(grip, guard.add(0.0D, 0.32D * scale, 0.0D), Vec3.ZERO, false);
		CommonFx.add(gold, guard.add(0.0D, 0.45D * scale, 0.0D), Vec3.ZERO, false);
	}

	/** A random point on (or just outside) the side of a box, up to {@code top} (0..1) of its height. */
	private static Vec3 surfacePoint(AABB box, RandomSource random, double outset, double top) {
		Vec3 centre = box.getCenter();
		double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5D + outset;
		double angle = random.nextDouble() * Mth.TWO_PI;
		double y = box.minY + random.nextDouble() * box.getYsize() * Mth.clamp(top, 0.05D, 1.0D);
		return new Vec3(centre.x + Math.cos(angle) * radius, y, centre.z + Math.sin(angle) * radius);
	}

	/** The horizontal "right hand" of a direction (east-west when it points straight up or down). */
	private static Vec3 rightOf(Vec3 dir) {
		Vec3 right = dir.cross(UP);
		return right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
	}

	/** Disconnect: drops the old world (the next world's first tick would only do it after it loads). */
	static void forget() {
		lastLevel = null;
		ANIMS.clear();
		MARKS.clear();
	}

	private DaoTechniqueFx() {
	}
}
