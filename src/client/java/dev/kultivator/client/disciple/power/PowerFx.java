package dev.kultivator.client.disciple.power;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effects of {@code FxType.DISCIPLE_POWER} and the continuous disciple power visuals (DESIGN 20.3.22, module C), all made
 * on this client and scaled by Effect Quality: every ultimate's particles, flame orbs, the Poison Body haze, the dragon
 * form, the golden-body glint, the Heretic gate veins, the Purple Mist, the soul-colour motes, the Double Pupils' eye
 * glyphs (only the master gets those packets), the natal treasure's glints and the soul's flight.
 */
public final class PowerFx {

	private static final double RANGE = 32.0D;
	private static final int GOLD = 0xFFD54A;
	private static final int CRIMSON = 0xD8263A;
	private static final int POISON = 0x9D4FD8;
	private static final int[] SEVEN = {0xE8473A, 0xF29B38, 0xF2D84A, 0x5FD066, 0x4FE0E6, 0x5AA8FF, 0x9D4FD8};

	/** An effect that lasts a few ticks (domes, cubes, clouds, charges, eye marks, flights). */
	private static final class Timed {
		final int sub;
		final Vec3 pos;
		final Vec3 target;
		final int b;
		final float f;
		final int life;
		final int entityId;
		int age;

		Timed(int sub, Vec3 pos, Vec3 target, int b, float f, int life, int entityId) {
			this.sub = sub;
			this.pos = pos;
			this.target = target;
			this.b = b;
			this.f = f;
			this.life = Math.max(1, life);
			this.entityId = entityId;
		}
	}

	private static final List<Timed> TIMED = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;

	// ================================================================== one-shot packets

	/** One {@code FxType.DISCIPLE_POWER} packet (routed by {@code FxDispatcher}). */
	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (type != FxType.DISCIPLE_POWER || level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int b = fx.b();
		float f = fx.f();
		RandomSource random = level.getRandom();
		switch (fx.a()) {
			case PowerIds.FX_ULT_SWORD -> {
				TIMED.add(new Timed(fx.a(), pos, target, b, f, (int) target.y, -1));
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0xD8E6FF, f, 64);
				CommonFx.pillar(pos, 0xD8E6FF, f + 2.0F);
			}
			case PowerIds.FX_ULT_BOLT -> {
				if (b == PowerIds.BOLT_STORM) {
					boltStorm(level, pos, f, random);
				} else if (b == 1) {
					CommonFx.bolt(target, pos, GOLD, 4);
				} else {
					CommonFx.bolt(target, pos, GOLD, 8);
					CommonFx.burst(pos.add(0.0D, 0.5D, 0.0D), 0xFFF2B0, 18, 1.2F);
					CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), GOLD, 1.2F, 20);
				}
			}
			case PowerIds.FX_ULT_PRISON -> TIMED.add(new Timed(fx.a(), pos, target, b, f, b, -1));
			case PowerIds.FX_ULT_FIST -> {
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), GOLD, 40, 1.6F);
				TIMED.add(new Timed(fx.a(), pos, target, 0, f, 8, -1));
				for (int i = 0; i < EffectQuality.scale(14); i++) {
					CommonFx.add(ParticleTypes.POOF, pos.x + random.nextGaussian() * 0.8D, pos.y + 0.2D, pos.z + random.nextGaussian() * 0.8D,
							random.nextGaussian() * 0.15D, 0.05D, random.nextGaussian() * 0.15D);
				}
			}
			case PowerIds.FX_LOTUS_CHARGE -> TIMED.add(new Timed(fx.a(), pos, target, 0, f, Math.round(f), b));
			case PowerIds.FX_LOTUS_BLAST -> lotusBlast(pos, target, b, f, random);
			case PowerIds.FX_ULT_LIFE -> {
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), Dao.LIFE.color(), f, 60, 0.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), Dao.LIFE.color(), f, 72);
				CommonFx.pillar(pos, 0x9CFFB0, 5.0F);
			}
			case PowerIds.FX_ULT_CAST -> {
				Dao dao = Dao.byOrdinal(b);
				int color = dao == null ? GOLD : dao.color();
				CommonFx.pillar(pos, color, 6.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, 2.0F, 40);
				CommonFx.burst(pos.add(0.0D, 1.2D, 0.0D), color, 24, 1.0F);
			}
			case PowerIds.FX_KUNPENG -> kunpeng(pos, target, b == 1, random);
			case PowerIds.FX_DASH -> {
				CommonFx.beam(pos.add(0.0D, 1.0D, 0.0D), target.add(0.0D, 1.0D, 0.0D), 0xE0B050, 0.25F);
				CommonFx.add(ParticleTypes.POOF, pos.x, pos.y + 0.2D, pos.z, 0.0D, 0.05D, 0.0D);
			}
			case PowerIds.FX_POISON_CLOUD -> TIMED.add(new Timed(fx.a(), pos, target, b, f, b, -1));
			case PowerIds.FX_DRAGON -> {
				CommonFx.burst(pos.add(0.0D, 1.2D, 0.0D), 0xB05AFF, 40, 1.8F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0xB05AFF, 2.5F, 48);
				for (int i = 0; i < EffectQuality.scale(20); i++) {
					CommonFx.add(ParticleTypes.FLAME, pos.x + random.nextGaussian() * 0.6D, pos.y + random.nextDouble() * 2.0D,
							pos.z + random.nextGaussian() * 0.6D, 0.0D, 0.05D, 0.0D);
				}
			}
			case PowerIds.FX_DRAGON_BREATH -> breath(pos, target, random);
			case PowerIds.FX_GATE_OPEN -> {
				CommonFx.pillar(pos, CRIMSON, 4.0F + b);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), CRIMSON, 1.0F + 0.3F * b, 24 + 6 * b);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0x6A0A12, 20 + 4 * b, 1.2F);
			}
			case PowerIds.FX_GATE_BURST -> {
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), CRIMSON, 50, 2.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), CRIMSON, 3.0F, 60);
				CommonFx.pillar(pos, 0xFF4A4A, 7.0F);
			}
			case PowerIds.FX_REVEAL -> reveal(pos, b, random);
			case PowerIds.FX_EYE_MARK -> TIMED.add(new Timed(fx.a(), pos, target, b, f, Math.round(f * 20.0F), b));
			case PowerIds.FX_DODGE -> {
				CommonFx.add(ParticleTypes.SWEEP_ATTACK, pos.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, false);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), b == 1 ? 0xD8E6FF : 0x8FE3FF, 8, 0.6F);
			}
			case PowerIds.FX_PETALS -> petals(pos, b, f, random);
			case PowerIds.FX_FLAME_RITUAL -> {
				DiscipleEntity near = nearestDisciple(level, pos);
				TIMED.add(new Timed(fx.a(), pos, target, b, f, Math.round(f * 20.0F), near == null ? -1 : near.getId()));
			}
			case PowerIds.FX_FLAME_RESULT -> {
				int color = flameColor(b);
				if (f > 0.5F) {
					CommonFx.pillar(pos, color, 6.0F);
					CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), color, 50, 1.6F);
					CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, 2.0F, 40);
				} else {
					for (int i = 0; i < EffectQuality.scale(24); i++) {
						CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.x + random.nextGaussian() * 0.4D, pos.y + 1.0D + random.nextDouble(),
								pos.z + random.nextGaussian() * 0.4D, 0.0D, 0.04D, 0.0D);
					}
				}
			}
			case PowerIds.FX_NATAL_REFINE -> {
				CommonFx.ring(pos.add(0.0D, 1.0D, 0.0D), GOLD, 1.2F, 36);
				CommonFx.burst(pos.add(0.0D, 1.2D, 0.0D), 0xFFF2B0, 30, 1.0F);
				CommonFx.pillar(pos, GOLD, 4.0F);
			}
			case PowerIds.FX_NATAL_FOLD -> natalFold(pos, b, f > 0.5F, random);
			case PowerIds.FX_PAYUNG -> {
				ringAt(pos.add(0.0D, 2.6D, 0.0D), GOLD, f * 0.5F, 32);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), b == 1 ? 0xFFF2B0 : GOLD, f, 40);
			}
			case PowerIds.FX_LONCENG -> TIMED.add(new Timed(fx.a(), pos, target, b, f, 12, -1));
			case PowerIds.FX_SOUL_FLIGHT -> TIMED.add(new Timed(fx.a(), pos, target, 0, 0.0F, 30, -1));
			case PowerIds.FX_SOUL_RETURN -> {
				CommonFx.pillar(pos.add(0.0D, -1.0D, 0.0D), 0x9FF3E6, 6.0F);
				CommonFx.burst(pos, 0x9FF3E6, 40, 1.4F);
				for (int i = 0; i < EffectQuality.scale(12); i++) {
					CommonFx.add(ParticleTypes.SOUL, pos.x + random.nextGaussian() * 0.5D, pos.y, pos.z + random.nextGaussian() * 0.5D,
							0.0D, 0.06D, 0.0D);
				}
			}
			case PowerIds.FX_UNDYING -> {
				for (int i = 0; i < EffectQuality.scale(40); i++) {
					CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.4D,
							random.nextDouble() * 0.6D, random.nextGaussian() * 0.4D);
				}
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0xC0102A, 30, 1.4F);
			}
			case PowerIds.FX_CODEX_STAGE -> {
				int color = switch (b) {
					case 1 -> 0xDDE6F0;
					case 2 -> GOLD;
					case 3 -> 0x8FE3C8;
					case 4 -> 0xF2F0E0;
					default -> 0xC0102A;
				};
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), color, 36, 1.2F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, 1.6F, 32);
			}
			case PowerIds.FX_DEVOUR -> devour(pos, target, random);
			case PowerIds.FX_COLOUR_UP -> {
				int color = PowerIds.COLOUR_RGB[Mth.clamp(b, 0, 6)];
				CommonFx.pillar(pos.add(0.0D, -1.0D, 0.0D), color, 5.0F);
				CommonFx.ring(pos.add(0.0D, -0.9D, 0.0D), color, 1.8F, 40);
				CommonFx.burst(pos, color, 30, 1.0F);
			}
			case PowerIds.FX_FUSION -> {
				CommonFx.beam(pos, target, 0xFF7A2A, 0.3F);
				CommonFx.burst(target, 0xFFB43A, 30, 1.2F);
			}
			case PowerIds.FX_PHYSIQUE -> physique(pos, b, f, random);
			default -> {
			}
		}
	}

	// ================================================================== continuous

	/** Every client tick (called by {@code FxDispatcher.tick}). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			TIMED.clear();
		}
		RandomSource random = level.getRandom();
		long time = level.getGameTime();
		for (Iterator<Timed> it = TIMED.iterator(); it.hasNext(); ) {
			Timed timed = it.next();
			if (timed.age++ >= timed.life) {
				it.remove();
				continue;
			}
			drawTimed(level, timed, random);
		}
		AABB box = minecraft.player.getBoundingBox().inflate(RANGE);
		Vec3 eye = minecraft.player.position();
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box)) {
			if (disciple.isRemoved() || !disciple.isAlive() || disciple.isInvisible() || disciple.distanceToSqr(eye) > RANGE * RANGE) {
				continue;
			}
			DiscipleEntity.Role role = disciple.syncedRole();
			if (role != DiscipleEntity.Role.DISCIPLE && role != DiscipleEntity.Role.CLONE) {
				continue;
			}
			drawDisciple(disciple, random, time + disciple.getId() * 17L);
		}
	}

	private static void drawDisciple(DiscipleEntity disciple, RandomSource random, long t) {
		int power = disciple.syncedPower();
		int talent = disciple.syncedTalent();
		boolean seated = DiscipleLook.seated(disciple);
		double height = disciple.getBbHeight() * (seated ? 0.69D : 1.0D);
		Vec3 feet = disciple.position();
		int flames = power & PowerIds.BITS_FLAMES;
		if (flames != 0 && t % 2L == 0L) {
			flameOrbs(feet, height, flames, random, t);
		}
		if ((power & PowerIds.BIT_POISON) != 0 && EffectQuality.chance(random, 0.25F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(CommonFx.dust(random.nextBoolean() ? POISON : 0x5FA040, 0.7F),
					feet.add(Math.cos(angle) * 0.7D, random.nextDouble() * 0.6D, Math.sin(angle) * 0.7D), new Vec3(0.0D, 0.01D, 0.0D), false);
		}
		if ((power & PowerIds.BIT_GOLD) != 0 && EffectQuality.chance(random, 0.12F)) {
			CommonFx.add(ParticleTypes.WAX_ON, feet.add(random.nextGaussian() * 0.3D, random.nextDouble() * height, random.nextGaussian() * 0.3D),
					Vec3.ZERO, false);
		}
		if ((power & PowerIds.BIT_RAMPAGE) != 0) {
			boolean heretic = DiscipleEntity.talentPhysique(talent) == PowerIds.PHYSIQUE_HERETIC;
			int n = EffectQuality.scale(2);
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 at = feet.add(Math.cos(angle) * 0.4D, random.nextDouble() * height, Math.sin(angle) * 0.4D);
				CommonFx.add(CommonFx.dust(heretic ? (random.nextBoolean() ? CRIMSON : 0x4A0A0A) : SEVEN[random.nextInt(SEVEN.length)], 1.0F), at,
						new Vec3(0.0D, 0.04D, 0.0D), false);
			}
		}
		if ((power & PowerIds.BIT_DRAGON) != 0 && EffectQuality.chance(random, 0.5F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(CommonFx.dust(0xB05AFF, 1.2F), feet.add(Math.cos(angle) * 0.8D, random.nextDouble() * height * 1.2D, Math.sin(angle) * 0.8D),
					new Vec3(0.0D, 0.03D, 0.0D), false);
			if (random.nextFloat() < 0.3F) {
				CommonFx.add(ParticleTypes.FLAME, feet.add(random.nextGaussian() * 0.3D, height * 1.1D, random.nextGaussian() * 0.3D),
						new Vec3(0.0D, 0.02D, 0.0D), false);
			}
		}
		if ((power & PowerIds.BIT_PURPLE_MIST) != 0 && EffectQuality.chance(random, 0.35F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = 0.8D + random.nextDouble() * 0.6D;
			CommonFx.add(CommonFx.dust(random.nextBoolean() ? 0x9A6AE0 : 0xC6A0FF, 1.3F),
					feet.add(Math.cos(angle) * radius, 0.1D + random.nextDouble() * 0.5D, Math.sin(angle) * radius),
					new Vec3(-Math.sin(angle) * 0.01D, 0.005D, Math.cos(angle) * 0.01D), false);
		}
		if ((power & PowerIds.BIT_NATAL_ORBIT) != 0 && EffectQuality.chance(random, 0.3F)) {
			double angle = t * 0.12D;
			CommonFx.add(CommonFx.dust(GOLD, 0.6F), feet.add(Math.cos(angle) * 1.1D, height * 0.75D, Math.sin(angle) * 1.1D), Vec3.ZERO, false);
		}
		// The soul colour shows in a few motes of its own (TDG's seven soul realms).
		if (DiscipleLook.realm(disciple) > Realm.MORTAL.ordinal() && EffectQuality.chance(random, 0.06F)) {
			int colour = PowerIds.COLOUR_RGB[Mth.clamp(DiscipleEntity.talentSoulColor(talent), 0, 6)];
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(CommonFx.dust(colour, 0.55F), feet.add(Math.cos(angle) * 0.45D, random.nextDouble() * height * 0.8D, Math.sin(angle) * 0.45D),
					new Vec3(0.0D, 0.02D, 0.0D), false);
		}
	}

	/** One coloured flame orb per Heavenly Flame, circling at the chest. */
	private static void flameOrbs(Vec3 feet, double height, int bits, RandomSource random, long t) {
		int count = Integer.bitCount(bits);
		int index = 0;
		double spin = t * 0.09D;
		for (int kind = 0; kind < PowerIds.FLAME_IDS.length; kind++) {
			if ((bits & 1 << kind) == 0) {
				continue;
			}
			double angle = spin + index * Math.PI * 2.0D / count;
			Vec3 at = feet.add(Math.cos(angle) * 0.75D, height * 0.62D + Math.sin(spin * 1.3D + index) * 0.08D, Math.sin(angle) * 0.75D);
			CommonFx.add(CommonFx.dust(PowerIds.FLAME_RGB[kind], 0.9F), at, Vec3.ZERO, false);
			if (EffectQuality.maximal() && random.nextFloat() < 0.2F) {
				CommonFx.add(ParticleTypes.SMALL_FLAME, at, new Vec3(0.0D, 0.01D, 0.0D), false);
			}
			index++;
		}
	}

	// ================================================================== timed effects

	private static void drawTimed(ClientLevel level, Timed timed, RandomSource random) {
		float progress = timed.age / (float) timed.life;
		switch (timed.sub) {
			case PowerIds.FX_ULT_SWORD -> swordDome(timed, random);
			case PowerIds.FX_ULT_PRISON -> {
				if (timed.age % 4 == 0) {
					cube(timed.pos.add(0.0D, timed.f * 0.5D, 0.0D), timed.f, Dao.SPACE.color(), random);
				}
			}
			case PowerIds.FX_ULT_FIST -> {
				float radius = timed.f * (timed.age + 1) / 8.0F;
				CommonFx.ring(timed.pos.add(0.0D, 0.15D, 0.0D), GOLD, radius, 24);
				if (timed.age % 2 == 0) {
					CommonFx.ring(timed.pos.add(0.0D, 0.2D, 0.0D), 0xC08A3E, radius * 0.8F, 16);
				}
			}
			case PowerIds.FX_LOTUS_CHARGE -> {
				Entity entity = level.getEntity(timed.entityId);
				Vec3 at = entity != null ? entity.position() : timed.pos;
				lotusCharge(at.add(0.0D, 2.4D, 0.0D), progress, random, timed.age);
			}
			case PowerIds.FX_POISON_CLOUD -> {
				int n = EffectQuality.scale(10);
				for (int i = 0; i < n; i++) {
					Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D, random.nextGaussian()).normalize()
							.scale(random.nextDouble() * timed.f);
					CommonFx.add(CommonFx.dust(SEVEN[random.nextInt(SEVEN.length)], 1.6F), timed.pos.add(offset.x, 0.6D + Math.abs(offset.y), offset.z),
							new Vec3(0.0D, 0.004D, 0.0D), false);
				}
			}
			case PowerIds.FX_EYE_MARK -> {
				Entity entity = level.getEntity(timed.entityId);
				if (entity == null || !entity.isAlive()) {
					timed.age = timed.life;
					return;
				}
				if (timed.age % 3 == 0) {
					eye(entity.position().add(0.0D, entity.getBbHeight() + 0.7D, 0.0D), timed.age);
				}
			}
			case PowerIds.FX_FLAME_RITUAL -> {
				Entity entity = timed.entityId >= 0 ? level.getEntity(timed.entityId) : null;
				Vec3 at = entity != null ? entity.position() : timed.pos;
				int color = flameColor(timed.b);
				int n = EffectQuality.scale(4);
				for (int i = 0; i < n; i++) {
					double angle = timed.age * 0.25D + i * Math.PI * 2.0D / n;
					double radius = 1.6D * (1.0D - progress * 0.6D);
					Vec3 point = at.add(Math.cos(angle) * radius, 0.3D + (timed.age % 40) / 40.0D * 1.6D, Math.sin(angle) * radius);
					CommonFx.add(CommonFx.dust(color, 1.0F), point, at.add(0.0D, 1.0D, 0.0D).subtract(point).scale(0.04D), false);
				}
				if (random.nextFloat() < 0.3F) {
					CommonFx.add(ParticleTypes.FLAME, at.x + random.nextGaussian() * 0.3D, at.y + 1.0D, at.z + random.nextGaussian() * 0.3D,
							0.0D, 0.02D, 0.0D);
				}
			}
			case PowerIds.FX_LONCENG -> {
				if (timed.age % 4 == 0) {
					CommonFx.ring(timed.pos.add(0.0D, 1.0D, 0.0D), timed.b == 1 ? 0xFFF2B0 : GOLD, timed.f * (timed.age + 4) / 16.0F, 28);
				}
			}
			case PowerIds.FX_SOUL_FLIGHT -> {
				Vec3 head = timed.pos.lerp(timed.target, Math.min(1.0D, progress * 1.2D));
				CommonFx.add(ParticleTypes.END_ROD, head, Vec3.ZERO, true);
				CommonFx.add(CommonFx.dust(0x9FF3E6, 1.4F), head, Vec3.ZERO, true);
				if (EffectQuality.maximal()) {
					CommonFx.add(ParticleTypes.SOUL, head, new Vec3(0.0D, 0.02D, 0.0D), false);
				}
			}
			default -> {
			}
		}
	}

	/** Swords of light circling over a dome, a few per tick in turn. */
	private static void swordDome(Timed timed, RandomSource random) {
		int swords = Math.max(1, EffectQuality.scale(Math.max(1, timed.b)));
		double radius = timed.f;
		double spin = timed.age * 0.08D;
		int per = Math.max(1, swords / 4);
		int start = (timed.age % 4) * per;
		for (int i = start; i < Math.min(swords, start + per); i++) {
			double angle = spin + i * Math.PI * 2.0D / swords * 2.39996D;
			double lift = 0.25D + (i % 5) / 5.0D * 0.7D;
			double r = radius * Math.cos(lift * Math.PI * 0.5D);
			double y = radius * Math.sin(lift * Math.PI * 0.5D);
			Vec3 point = timed.pos.add(Math.cos(angle) * r, y + 0.3D, Math.sin(angle) * r);
			Vec3 tangent = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(0.6D);
			CommonFx.beam(point, point.add(tangent), i % 6 == 0 ? GOLD : 0xD8E6FF, 0.05F);
		}
		if (timed.age % 10 == 0) {
			CommonFx.ring(timed.pos.add(0.0D, 0.1D, 0.0D), 0xD8E6FF, (float) radius, 40);
		}
	}

	/** The edges of a cube of restriction glyphs. */
	private static void cube(Vec3 centre, double half, int color, RandomSource random) {
		int perEdge = Math.max(2, EffectQuality.scale(5));
		for (int edge = 0; edge < 12; edge++) {
			for (int i = 0; i < perEdge; i++) {
				double t = (i + random.nextDouble() * 0.5D) / perEdge * 2.0D - 1.0D;
				double a = (edge & 1) == 0 ? -1.0D : 1.0D;
				double b = (edge & 2) == 0 ? -1.0D : 1.0D;
				Vec3 offset = switch (edge / 4) {
					case 0 -> new Vec3(t, a, b);
					case 1 -> new Vec3(a, t, b);
					default -> new Vec3(a, b, t);
				};
				CommonFx.add(CommonFx.dust(i % 3 == 0 ? 0xC6B0FF : color, 0.9F), centre.add(offset.scale(half)), Vec3.ZERO, false);
			}
		}
	}

	private static void lotusCharge(Vec3 at, float progress, RandomSource random, int age) {
		int petals = 8;
		double radius = 0.25D + 0.55D * progress;
		int n = EffectQuality.scale(petals);
		for (int i = 0; i < n; i++) {
			double angle = age * 0.15D + i * Math.PI * 2.0D / n;
			Vec3 point = at.add(Math.cos(angle) * radius, Math.sin(age * 0.3D + i) * 0.08D, Math.sin(angle) * radius);
			CommonFx.add(CommonFx.dust(i % 2 == 0 ? 0xFF7A2A : 0xFFD54A, 1.0F), point, Vec3.ZERO, false);
		}
		if (random.nextFloat() < 0.4F + progress * 0.5F) {
			CommonFx.add(ParticleTypes.FLAME, at.x + random.nextGaussian() * 0.15D, at.y, at.z + random.nextGaussian() * 0.15D, 0.0D, 0.01D, 0.0D);
		}
	}

	private static void eye(Vec3 at, int age) {
		int n = Math.max(6, EffectQuality.scale(10));
		for (int i = 0; i < n; i++) {
			double angle = i * Math.PI * 2.0D / n;
			CommonFx.add(CommonFx.dust(0x8FE3FF, 0.5F), at.add(Math.cos(angle) * 0.35D, Math.sin(angle) * 0.16D, 0.0D), Vec3.ZERO, false);
		}
		CommonFx.add(CommonFx.dust(0x1A3A6A, 0.9F), at, Vec3.ZERO, false);
		CommonFx.add(CommonFx.dust(0xFFFFFF, 0.4F), at.add(0.05D, 0.04D, 0.0D), Vec3.ZERO, false);
	}

	// ================================================================== bursts

	/**
	 * Petir Pengusir Kejahatan from one packet: golden bolts from the sky onto the nearest monsters around the caster
	 * (at most {@link PowerIds#ULT_BOLTS}) and one chain bolt from each to its nearest other monster. The damage is the
	 * server's; this only draws it.
	 */
	private static void boltStorm(ClientLevel level, Vec3 caster, float radius, RandomSource random) {
		double reach = Math.max(1.0D, radius);
		List<Mob> monsters = level.getEntitiesOfClass(Mob.class, new AABB(caster, caster).inflate(reach),
				mob -> mob.isAlive() && mob instanceof Enemy && mob.distanceToSqr(caster) <= reach * reach);
		monsters.sort(java.util.Comparator.comparingDouble(mob -> mob.distanceToSqr(caster)));
		List<Mob> struck = new ArrayList<>(monsters.subList(0, Math.min(PowerIds.ULT_BOLTS, monsters.size())));
		List<Mob> chained = new ArrayList<>(struck);
		for (Mob victim : struck) {
			Vec3 ground = victim.position();
			Vec3 from = ground.add(0.0D, 1.5D, 0.0D);
			Vec3 sky = ground.add((random.nextDouble() - 0.5D) * 3.0D, 14.0D, (random.nextDouble() - 0.5D) * 3.0D);
			BlockHitResult hit = level.clip(new ClipContext(from, sky, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			Vec3 top = hit.getType() == HitResult.Type.MISS ? sky : hit.getLocation().add(0.0D, -0.2D, 0.0D);
			CommonFx.bolt(top, ground, GOLD, 8);
			CommonFx.burst(ground.add(0.0D, 0.5D, 0.0D), 0xFFF2B0, 18, 1.2F);
			CommonFx.ring(ground.add(0.0D, 0.05D, 0.0D), GOLD, 1.2F, 20);
			Mob next = null;
			double best = PowerIds.ULT_CHAIN_RANGE * PowerIds.ULT_CHAIN_RANGE;
			for (Mob other : monsters) {
				double d = other.distanceToSqr(victim);
				if (!chained.contains(other) && d <= best) {
					best = d;
					next = other;
				}
			}
			if (next != null) {
				chained.add(next);
				CommonFx.bolt(victim.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), GOLD, 4);
			}
		}
	}

	private static void lotusBlast(Vec3 impact, Vec3 from, int b, float radius, RandomSource random) {
		if (b <= 0) {
			for (int i = 0; i < EffectQuality.scale(14); i++) {
				CommonFx.add(ParticleTypes.SMOKE, impact.x + random.nextGaussian() * 0.3D, impact.y, impact.z + random.nextGaussian() * 0.3D, 0.0D, 0.04D, 0.0D);
			}
			return;
		}
		boolean fused = b >= 8;
		int flames = fused ? b - 8 : b;
		CommonFx.beam(from, impact.add(0.0D, 1.0D, 0.0D), 0xFF7A2A, 0.35F);
		CommonFx.burst(impact.add(0.0D, 1.0D, 0.0D), 0xFFB43A, 60 + 20 * flames, radius);
		for (int ring = 0; ring < Math.max(1, flames); ring++) {
			int color = ring < PowerIds.FLAME_RGB.length ? PowerIds.FLAME_RGB[(ring + 3) % PowerIds.FLAME_RGB.length] : 0xFF7A2A;
			CommonFx.ring(impact.add(0.0D, 0.3D + ring * 0.4D, 0.0D), color, radius * (0.6F + 0.15F * ring), 48);
		}
		if (fused) {
			CommonFx.ring(impact.add(0.0D, 0.2D, 0.0D), 0xBFEFFF, radius, 56);
		}
		int n = EffectQuality.scale(30 + 10 * flames);
		for (int i = 0; i < n; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian()).normalize();
			CommonFx.add(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, impact.add(0.0D, 0.5D, 0.0D),
					dir.scale(0.15D + radius * 0.04D), false);
		}
	}

	private static void kunpeng(Vec3 from, Vec3 to, boolean full, RandomSource random) {
		int color = full ? 0xBFE8FF : 0x8FD0FF;
		CommonFx.beam(from.add(0.0D, 1.0D, 0.0D), to.add(0.0D, 1.0D, 0.0D), color, full ? 0.6F : 0.35F);
		Vec3 dir = to.subtract(from);
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		if (side.lengthSqr() > 1.0E-4D) {
			side = side.normalize();
			// Two great wings of wind at the start.
			for (int i = 0; i < EffectQuality.scale(16); i++) {
				double t = i / 16.0D;
				double lift = Math.sin(t * Math.PI) * 0.8D;
				for (int sign = -1; sign <= 1; sign += 2) {
					Vec3 point = from.add(side.scale(sign * (0.4D + t * 2.4D))).add(0.0D, 1.2D + lift, 0.0D);
					CommonFx.add(CommonFx.dust(color, 1.0F), point, Vec3.ZERO, false);
				}
			}
		}
		for (int i = 0; i < EffectQuality.scale(10); i++) {
			Vec3 at = from.lerp(to, random.nextDouble());
			CommonFx.add(ParticleTypes.CLOUD, at.x, at.y + 0.6D, at.z, 0.0D, 0.02D, 0.0D);
		}
	}

	private static void breath(Vec3 mouth, Vec3 end, RandomSource random) {
		Vec3 dir = end.subtract(mouth);
		int n = EffectQuality.scale(16);
		for (int i = 0; i < n; i++) {
			Vec3 spread = new Vec3(random.nextGaussian() * 0.12D, random.nextGaussian() * 0.12D, random.nextGaussian() * 0.12D);
			Vec3 velocity = dir.normalize().add(spread).scale(0.3D + random.nextDouble() * 0.2D);
			CommonFx.add(random.nextInt(3) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, mouth, velocity, false);
		}
	}

	private static void reveal(Vec3 pos, int kind, RandomSource random) {
		switch (kind) {
			case 1 -> {
				petals(pos.add(0.0D, 1.0D, 0.0D), 40, 3.0F, random);
				CommonFx.pillar(pos, 0xF2F6FF, 9.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0xFFB7D5, 3.0F, 60);
			}
			case 2 -> {
				CommonFx.pillar(pos, PowerIds.COLOUR_RGB[6], 10.0F);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), CRIMSON, 70, 2.4F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), CRIMSON, 3.0F, 60);
			}
			default -> {
				CommonFx.pillar(pos, 0xFFB43A, 10.0F);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), GOLD, 70, 2.4F);
				for (int i = 0; i < EffectQuality.scale(30); i++) {
					CommonFx.add(ParticleTypes.FLAME, pos.x + random.nextGaussian() * 0.6D, pos.y + random.nextDouble() * 3.0D,
							pos.z + random.nextGaussian() * 0.6D, 0.0D, 0.06D, 0.0D);
				}
			}
		}
	}

	private static void petals(Vec3 centre, int count, float radius, RandomSource random) {
		int n = EffectQuality.scale(Math.max(4, count));
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = random.nextDouble() * radius;
			Vec3 at = centre.add(Math.cos(angle) * r * 0.3D, random.nextDouble() * 0.8D, Math.sin(angle) * r * 0.3D);
			Vec3 velocity = new Vec3(Math.cos(angle), 0.1D, Math.sin(angle)).scale(0.08D + radius * 0.03D);
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, at, velocity, false);
			if (i % 2 == 0) {
				CommonFx.add(CommonFx.dust(0xFFB7D5, 0.8F), centre.add(Math.cos(angle) * r, 0.2D + random.nextDouble(), Math.sin(angle) * r),
						Vec3.ZERO, false);
			}
		}
	}

	private static void natalFold(Vec3 pos, int folds, boolean success, RandomSource random) {
		if (!success) {
			for (int i = 0; i < EffectQuality.scale(20); i++) {
				CommonFx.add(ParticleTypes.SMOKE, pos.x + random.nextGaussian() * 0.3D, pos.y + 1.2D, pos.z + random.nextGaussian() * 0.3D, 0.0D, 0.05D, 0.0D);
			}
			CommonFx.add(ParticleTypes.POOF, pos.add(0.0D, 1.2D, 0.0D), Vec3.ZERO, false);
			return;
		}
		int color = folds >= 9 ? GOLD : folds >= 3 ? 0xDDE6F0 : 0xC8D8C0;
		CommonFx.burst(pos.add(0.0D, 1.2D, 0.0D), color, 16 + folds * 3, 0.8F + folds * 0.05F);
		ringAt(pos.add(0.0D, 1.2D, 0.0D), color, 0.6F + folds * 0.05F, 20 + folds);
	}

	private static void devour(Vec3 from, Vec3 to, RandomSource random) {
		int n = EffectQuality.scale(20);
		for (int i = 0; i < n; i++) {
			Vec3 at = from.add(random.nextGaussian() * 0.4D, random.nextGaussian() * 0.4D, random.nextGaussian() * 0.4D);
			CommonFx.add(CommonFx.dust(random.nextBoolean() ? 0x7A2E9A : 0x2A0A3A, 1.0F), at, to.subtract(at).scale(0.08D), false);
		}
	}

	private static void physique(Vec3 pos, int physique, float variant, RandomSource random) {
		int color = PowerIds.PHYSIQUE_RGB[Mth.clamp(physique, 0, PowerIds.PHYSIQUE_RGB.length - 1)];
		if (physique == PowerIds.PHYSIQUE_POISON && variant >= 3.0F) {
			for (int i = 0; i < EffectQuality.scale(50); i++) {
				Vec3 offset = new Vec3(random.nextGaussian(), random.nextDouble(), random.nextGaussian()).scale(1.6D);
				CommonFx.add(CommonFx.dust(SEVEN[random.nextInt(SEVEN.length)], 1.4F), pos.add(offset), new Vec3(0.0D, 0.02D, 0.0D), false);
			}
			return;
		}
		CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), color, variant >= 2.0F ? 60 : 30, variant >= 2.0F ? 2.0F : 1.2F);
		CommonFx.pillar(pos, color, variant >= 2.0F ? 8.0F : 4.0F);
		if (variant < 1.0F) {
			CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, 1.8F, 36);
		}
	}

	// ================================================================== helpers

	/** A horizontal ring at {@code pos} (no ground offset). */
	private static void ringAt(Vec3 pos, int color, float radius, int points) {
		int n = EffectQuality.scale(points);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			CommonFx.add(CommonFx.dust(color, 0.8F), pos.x + Math.cos(angle) * radius, pos.y, pos.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
		}
	}

	private static int flameColor(int kind) {
		return PowerIds.FLAME_RGB[Mth.clamp(kind, 0, PowerIds.FLAME_RGB.length - 1)];
	}

	private static @Nullable DiscipleEntity nearestDisciple(ClientLevel level, Vec3 pos) {
		DiscipleEntity best = null;
		double bestSq = 9.0D;
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, new AABB(pos, pos).inflate(3.0D))) {
			double distanceSq = disciple.position().distanceToSqr(pos);
			if (distanceSq < bestSq) {
				bestSq = distanceSq;
				best = disciple;
			}
		}
		return best;
	}

	static void clear() {
		TIMED.clear();
	}

	private PowerFx() {
	}
}
