package dev.kultivator.client.disciple.path;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DestroyerArts;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.disciple.path.FoundationTypes;
import dev.kultivator.disciple.path.PathFxIds;
import dev.kultivator.disciple.path.StarTitles;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Effects of {@code FxType.STAR} (DESIGN 20.3.22, module D) and the continuous star visuals of disciples within 32
 * blocks, all client particles scaled by Effect Quality: the star orbit (9 dots per ring; 1 ring at Minimal, 3 at
 * Maximal) and the Stellar stages (Meteor granules, the star core, the sun-like aura, dark motes, a new hue per Tak
 * Terbatas tier), Penginjak Langit's white-gold aura, the released red seal's sparks, the heir's seal ring, the Hu Fa
 * and calamity glyphs, the Heaven-Dao golden pillar and the descended master's aura. One-shot effects: condensing,
 * calamity omen and passing, seal glyphs, the heir's dantian link, the ring's old sage, the surpass pillar, the Tahan
 * Petir arc, Divine Clones, hexes, soul lamps and the block-safe Jurus Pemusnah.
 */
public final class PathFx {

	private static final double RANGE = 32.0D;
	private static final double RANGE_SQ = RANGE * RANGE;
	private static final int GOLD = 0xFFD54A;
	private static final int JADE = 0x8FE3C8;
	private static final int SILVER = 0xDCE6F5;
	private static final int CRIMSON = 0xC21F1F;
	/** Entity id -> {colour, client tick until} of a running Jiwa Guru Turun. */
	private static final Map<Integer, long[]> DESCENTS = new HashMap<>();

	// ================================================================== one-shot effects

	/** One {@code FxType.STAR} packet (routed by {@code FxDispatcher}). */
	public static void play(FxType type, Payloads.Fx fx) {
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int b = fx.b();
		float f = fx.f();
		RandomSource random = CommonFx.random();
		switch (fx.a()) {
			case PathFxIds.CONDENSE -> {
				int color = StarTitles.color(b);
				CommonFx.spiralIn(pos, SILVER, 1.6F, 26, random.nextFloat() * 20.0F);
				CommonFx.burst(pos, color, 18, 0.8F);
				for (int i = 0; i < EffectQuality.scale(6); i++) {
					CommonFx.add(ParticleTypes.END_ROD, pos, new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.06D), false);
				}
				CommonFx.ring(pos.add(0.0D, -1.0D, 0.0D), color, 1.2F, 24);
			}
			case PathFxIds.CALAMITY_OMEN -> {
				glyphRing(pos.add(0.0D, 0.05D, 0.0D), 2.6F, 0x9B4DFF, GOLD, 18 + b * 2);
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), 0x9B4DFF, 2.4F, 12, f);
			}
			case PathFxIds.CALAMITY_PASS -> {
				CommonFx.pillar(pos, GOLD, 8.0F);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0xFFFFFF, 40, 2.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 3.5F, 60);
				for (int i = 0; i < EffectQuality.scale(8); i++) {
					CommonFx.add(ParticleTypes.FIREWORK, pos.add(0.0D, 1.5D, 0.0D), new Vec3(random.nextGaussian(), random.nextFloat(), random.nextGaussian()).scale(0.15D), false);
				}
			}
			case PathFxIds.SEAL_GLYPH -> {
				glyphRing(pos.add(0.0D, 0.05D, 0.0D), 1.2F + f, JADE, 0xFFFFFF, 12);
				CommonFx.beam(target, pos.add(0.0D, 1.0D, 0.0D), JADE, 0.05F);
			}
			case PathFxIds.SEALED -> {
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), JADE, 36, 1.4F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), JADE, 2.0F, 40);
				CommonFx.pillar(pos, 0xFFFFFF, 3.0F);
			}
			case PathFxIds.HEIR_LINK -> {
				CommonFx.beam(pos, target, JADE, 0.08F + 0.1F * f);
				CommonFx.burst(pos, JADE, 4, 0.3F);
				CommonFx.burst(target, GOLD, 4, 0.3F);
			}
			case PathFxIds.HEIR_NAMED -> {
				CommonFx.pillar(pos, JADE, 7.0F);
				CommonFx.pillar(pos, GOLD, 5.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 3.0F, 54);
				glyphRing(pos.add(0.0D, 0.06D, 0.0D), 1.6F, GOLD, JADE, 9);
			}
			case PathFxIds.RING_GHOST -> sage(pos, b);
			case PathFxIds.SURPASS -> {
				CommonFx.pillar(pos, GOLD, Math.max(6.0F, f));
				CommonFx.pillar(pos, 0xFFF4C0, Math.max(4.0F, f * 0.7F));
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 4.0F, 72);
				for (int i = 0; i < EffectQuality.scale(12); i++) {
					CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, pos.add(0.0D, 1.0D, 0.0D),
							new Vec3(random.nextGaussian() * 0.3D, 0.4D + random.nextFloat() * 0.4D, random.nextGaussian() * 0.3D), false);
				}
			}
			case PathFxIds.TAHAN_ARC -> {
				CommonFx.bolt(target, pos, 0xFFE27A, 8);
				CommonFx.burst(pos, 0xFFE27A, 12, 0.6F);
			}
			case PathFxIds.HUFA_SEAL -> {
				glyphRing(pos, 1.3F, JADE, 0xFFFFFF, 10);
				if (EffectQuality.maximal()) {
					CommonFx.beam(pos.add(0.0D, 0.2D, 0.0D), target, 0x6CC9A0, 0.03F);
				}
			}
			case PathFxIds.FOUNDATION -> foundation(pos, b, Math.round(f));
			case PathFxIds.CLONE -> {
				if (b == 0) {
					CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0xBFE8FF, 24, 1.0F);
					CommonFx.pillar(pos, 0xE8F6FF, 2.5F);
				} else {
					for (int i = 0; i < EffectQuality.scale(10); i++) {
						CommonFx.add(ParticleTypes.POOF, pos.add(random.nextGaussian() * 0.3D, 0.2D + random.nextFloat() * 1.6D, random.nextGaussian() * 0.3D),
								Vec3.ZERO, false);
					}
				}
			}
			case PathFxIds.DESCEND -> {
				Minecraft minecraft = Minecraft.getInstance();
				Entity near = nearestDisciple(pos);
				if (near != null && minecraft.level != null) {
					DESCENTS.put(near.getId(), new long[]{b & 0xFFFFFF, minecraft.level.getGameTime() + Math.round(f * 20.0F) + 20L});
				}
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), b, 30, 1.6F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), b, 2.5F, 40);
			}
			case PathFxIds.HEX -> {
				glyphRing(pos.add(0.0D, 0.06D, 0.0D), 1.4F, GOLD, 0xFFF4C0, Math.max(3, b));
				CommonFx.burst(pos.add(0.0D, 1.2D, 0.0D), GOLD, 16, 0.8F);
			}
			case PathFxIds.ART -> art(DestroyerArts.Art.byOrdinal(b), pos, target, f, random);
			case PathFxIds.ART_CHARGE -> hammer(pos, b);
			case PathFxIds.LAMP -> {
				for (int i = 0; i < EffectQuality.scale(14); i++) {
					CommonFx.add(ParticleTypes.SOUL_FIRE_FLAME, pos.add(random.nextGaussian() * 0.4D, 1.0D + random.nextFloat(), random.nextGaussian() * 0.4D),
							new Vec3(0.0D, 0.05D, 0.0D), false);
					CommonFx.add(ParticleTypes.SMOKE, pos.add(random.nextGaussian() * 0.3D, 2.0D + random.nextFloat(), random.nextGaussian() * 0.3D),
							new Vec3(0.0D, 0.03D, 0.0D), false);
				}
				CommonFx.pillar(pos, 0x9FF3E6, 4.0F);
			}
			case PathFxIds.PIN -> {
				glyphRing(pos.add(0.0D, 0.05D, 0.0D), f, GOLD, 0xFFFFFF, 9);
				for (int i = 0; i < EffectQuality.scale(16); i++) {
					CommonFx.add(ParticleTypes.ENCHANT, pos.add(random.nextGaussian() * f * 0.5D, 0.5D + random.nextFloat(), random.nextGaussian() * f * 0.5D),
							Vec3.ZERO, false);
				}
			}
			case PathFxIds.REPAY -> {
				CommonFx.beam(target, pos, GOLD, 0.12F);
				for (int i = 0; i < EffectQuality.scale(14); i++) {
					CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, pos, new Vec3(random.nextGaussian(), random.nextFloat(), random.nextGaussian()).scale(0.25D), false);
				}
			}
			case PathFxIds.DUEL -> CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0xFF8A3A, f, 64);
			default -> {
			}
		}
	}

	/** A ring of glyph dots: alternating colours, every third one a small cross. */
	private static void glyphRing(Vec3 centre, float radius, int color, int accent, int count) {
		int n = Math.max(3, EffectQuality.scale(count));
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			Vec3 at = centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
			CommonFx.add(CommonFx.dust(i % 3 == 0 ? accent : color, 0.9F), at, Vec3.ZERO, false);
			if (i % 3 == 0 && !EffectQuality.minimal()) {
				CommonFx.add(CommonFx.dust(accent, 0.5F), at.add(0.0D, 0.18D, 0.0D), Vec3.ZERO, false);
			}
		}
	}

	/** The old sage in the ring: a pale robed figure of motes (0 given, 1 bonded, 2 a protecting hand). */
	private static void sage(Vec3 feet, int stage) {
		RandomSource random = CommonFx.random();
		int pale = stage == 2 ? 0xFFF4C0 : 0xD6EEF5;
		Vec3 base = feet.add(-0.6D, 0.0D, 0.0D);
		int n = EffectQuality.scale(stage == 0 ? 10 : 26);
		for (int i = 0; i < n; i++) {
			double y = random.nextDouble() * 1.7D;
			double spread = 0.25D + (1.7D - y) * 0.12D;
			CommonFx.add(CommonFx.dust(pale, 0.8F), base.add(random.nextGaussian() * spread * 0.4D, y, random.nextGaussian() * spread * 0.4D),
					new Vec3(0.0D, 0.01D, 0.0D), false);
		}
		CommonFx.burst(base.add(0.0D, 1.85D, 0.0D), pale, EffectQuality.scale(6), 0.2F);
		for (int i = 0; i < EffectQuality.scale(stage == 0 ? 2 : 6); i++) {
			CommonFx.add(ParticleTypes.SOUL, base.add(random.nextGaussian() * 0.3D, random.nextFloat() * 1.8D, random.nextGaussian() * 0.3D),
					new Vec3(0.0D, 0.04D, 0.0D), false);
		}
		if (stage == 2) {
			CommonFx.ring(feet.add(0.0D, 0.1D, 0.0D), 0xFFF4C0, 1.6F, 32);
		}
	}

	/** Dao foundation pillars rising around the disciple: fractured (Fana), cracked (Bumi), flawless (Langit), ten (Sempurna). */
	private static void foundation(Vec3 feet, int type, int pillars) {
		RandomSource random = CommonFx.random();
		int color = FoundationTypes.color(type);
		int count = Math.max(1, pillars);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			Vec3 base = feet.add(Math.cos(angle) * 1.6D, 0.0D, Math.sin(angle) * 1.6D);
			int motes = EffectQuality.scale(type >= FoundationTypes.LANGIT ? 10 : 6);
			for (int m = 0; m < motes; m++) {
				double y = random.nextDouble() * 2.2D;
				// Cracks: Fana leaves wide gaps, Bumi small ones, Langit and Sempurna none.
				boolean gap = type == FoundationTypes.FANA && (int) (y * 4.0D) % 2 == 1 || type == FoundationTypes.BUMI && (int) (y * 6.0D) % 4 == 3;
				if (!gap) {
					CommonFx.add(CommonFx.dust(color, 1.0F), base.add(0.0D, y, 0.0D), new Vec3(0.0D, 0.01D, 0.0D), false);
				}
			}
		}
		if (type == FoundationTypes.SEMPURNA) {
			CommonFx.pillar(feet, 0xFFFFFF, 6.0F);
		}
	}

	/** The growing hammer glyph of the Great Sumeru Hammer above the disciple (blow 1-9). */
	private static void hammer(Vec3 feet, int blow) {
		float scale = 0.4F + 0.12F * blow;
		Vec3 top = feet.add(0.0D, 2.8D + 0.1D * blow, 0.0D);
		int color = 0xB57CFF;
		int n = EffectQuality.scale(8);
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n - 0.5D;
			CommonFx.add(CommonFx.dust(color, 1.0F), top.add(t * 1.6D * scale, 0.0D, 0.0D), Vec3.ZERO, false);
			CommonFx.add(CommonFx.dust(color, 1.0F), top.add(t * 1.6D * scale, 0.5D * scale, 0.0D), Vec3.ZERO, false);
			CommonFx.add(CommonFx.dust(0xE8D7A8, 0.8F), top.add(0.0D, -t * 1.6D * scale - 0.8D * scale, 0.0D), Vec3.ZERO, false);
		}
		if (blow >= 9) {
			CommonFx.burst(top, color, 20, 1.2F);
		}
	}

	/** The block-safe forms of the Jurus Pemusnah: big particles only. */
	private static void art(DestroyerArts.Art art, Vec3 pos, Vec3 target, float size, RandomSource random) {
		if (art == null) {
			return;
		}
		int color = art.color();
		switch (art) {
			case XUANTIAN -> {
				CommonFx.beam(pos, target, color, 0.5F);
				CommonFx.beam(pos, target, 0xFFFFFF, 0.15F);
				Vec3 delta = target.subtract(pos);
				int n = EffectQuality.scale((int) Math.ceil(delta.length() / 3.0D));
				for (int i = 0; i <= n; i++) {
					CommonFx.add(ParticleTypes.SWEEP_ATTACK, pos.add(delta.scale((double) i / Math.max(1, n))), Vec3.ZERO, true);
				}
			}
			case LOTUS -> {
				sphereBurst(pos, color, size, 60);
				for (int i = 0; i < EffectQuality.scale(30); i++) {
					Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
					CommonFx.add(ParticleTypes.FLAME, pos.add(dir.scale(size * random.nextFloat())), dir.scale(0.12D), false);
				}
				CommonFx.add(ParticleTypes.EXPLOSION, pos, Vec3.ZERO, true);
			}
			case QUAKE -> {
				for (int ring = 1; ring <= 4; ring++) {
					CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, size * ring / 4.0F, 40 + ring * 8);
				}
				for (int i = 0; i < EffectQuality.scale(30); i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = random.nextDouble() * size;
					CommonFx.add(ParticleTypes.DUST_PLUME, pos.add(Math.cos(angle) * r, 0.2D, Math.sin(angle) * r), new Vec3(0.0D, 0.15D, 0.0D), false);
				}
			}
			case DESOLATION -> {
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), color, size, 50, random.nextFloat() * 10.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, size, 64);
				CommonFx.pillar(pos, color, 4.0F);
			}
			case PRISON_HAND -> {
				squareColumn(pos, size, color, 6.0F);
				for (int i = 0; i < EffectQuality.scale(20); i++) {
					CommonFx.add(ParticleTypes.REVERSE_PORTAL, pos.add(random.nextGaussian() * size * 0.4D, random.nextFloat() * 4.0D, random.nextGaussian() * size * 0.4D),
							Vec3.ZERO, false);
				}
			}
			case SUMERU -> {
				sphereBurst(pos, color, size, 70);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, size, 72);
				CommonFx.add(ParticleTypes.EXPLOSION, pos, Vec3.ZERO, true);
				CommonFx.add(ParticleTypes.SONIC_BOOM, pos.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, true);
			}
			case HELL_MONARCH -> {
				for (int ring = 1; ring <= 3; ring++) {
					CommonFx.ring(pos.add(0.0D, 0.2D, 0.0D), CRIMSON, size * ring / 3.0F, 60 + ring * 20);
				}
				for (int i = 0; i < EffectQuality.scale(50); i++) {
					CommonFx.add(ParticleTypes.CRIMSON_SPORE, pos.add(random.nextGaussian() * size * 0.5D, random.nextFloat() * 8.0D, random.nextGaussian() * size * 0.5D),
							Vec3.ZERO, false);
				}
				CommonFx.pillar(pos, CRIMSON, 10.0F);
			}
			case DEVOURING -> {
				for (int i = 0; i < 3; i++) {
					CommonFx.spiralIn(pos.add(0.0D, 0.5D + i, 0.0D), color, size, 30, random.nextFloat() * 20.0F);
				}
				for (int i = 0; i < EffectQuality.scale(20); i++) {
					CommonFx.add(ParticleTypes.PORTAL, pos.add(random.nextGaussian() * size * 0.5D, random.nextFloat() * 3.0D, random.nextGaussian() * size * 0.5D),
							Vec3.ZERO, false);
				}
			}
			case MOUNTAIN_SEA -> {
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), color, size, 40);
				squareColumn(pos, size * 0.7F, color, 2.0F);
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0xFFF4C0, 12, size * 0.5F);
			}
		}
	}

	private static void sphereBurst(Vec3 centre, int color, float radius, int count) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			CommonFx.add(CommonFx.dust(color, 1.6F), centre.add(dir.scale(radius * (0.3D + random.nextDouble() * 0.7D))), dir.scale(0.05D), false);
		}
	}

	private static void squareColumn(Vec3 centre, float half, int color, float height) {
		int n = Math.max(4, EffectQuality.scale(10));
		for (int i = 0; i <= n; i++) {
			double t = -half + 2.0D * half * i / n;
			for (double y = 0.1D; y <= height; y += height / 3.0D) {
				CommonFx.add(CommonFx.dust(color, 1.0F), centre.add(t, y, -half), Vec3.ZERO, false);
				CommonFx.add(CommonFx.dust(color, 1.0F), centre.add(t, y, half), Vec3.ZERO, false);
				CommonFx.add(CommonFx.dust(color, 1.0F), centre.add(-half, y, t), Vec3.ZERO, false);
				CommonFx.add(CommonFx.dust(color, 1.0F), centre.add(half, y, t), Vec3.ZERO, false);
			}
		}
	}

	private static Entity nearestDisciple(Vec3 pos) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		DiscipleEntity best = null;
		double bestSq = 4.0D;
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, new AABB(pos, pos).inflate(2.0D))) {
			double distanceSq = disciple.position().distanceToSqr(pos);
			if (distanceSq < bestSq) {
				bestSq = distanceSq;
				best = disciple;
			}
		}
		return best;
	}

	// ================================================================== continuous effects

	/** Every client tick (called by {@code FxDispatcher.tick}). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		long time = level.getGameTime();
		if (time % 100L == 0L && !DESCENTS.isEmpty()) {
			DESCENTS.values().removeIf(entry -> entry[1] < time);
		}
		RandomSource random = level.getRandom();
		Vec3 eye = minecraft.player.position();
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, minecraft.player.getBoundingBox().inflate(RANGE))) {
			if (disciple.isRemoved() || !disciple.isAlive() || disciple.isInvisible() || disciple.distanceToSqr(eye) > RANGE_SQ) {
				continue;
			}
			int stars = disciple.syncedStars();
			int power = disciple.syncedPower() & DiscipleEntity.POWER_MASK_D;
			if (stars <= 0 && power == 0) {
				continue;
			}
			draw(disciple, stars, power, time + disciple.getId() * 13L, random, level.getGameTime());
		}
	}

	private static void draw(DiscipleEntity disciple, int stars, int power, long t, RandomSource random, long now) {
		boolean seated = DiscipleLook.seated(disciple);
		double height = disciple.getBbHeight() * (seated ? 0.69D : 1.0D);
		Vec3 feet = disciple.position();
		Vec3 chest = feet.add(0.0D, height * 0.62D, 0.0D);
		boolean clone = disciple.syncedRole() == DiscipleEntity.Role.CLONE;
		if (stars > 0) {
			orbit(feet, chest, height, stars, t, random, clone);
		}
		if ((power & DisciplePaths.BIT_TRAMPLING) != 0 && t % 3L == 0L) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 at = feet.add(Math.cos(angle) * 0.6D, random.nextDouble() * height, Math.sin(angle) * 0.6D);
			CommonFx.add(CommonFx.dust(random.nextBoolean() ? 0xFFFFFF : 0xFFE9A8, 0.9F), at, new Vec3(0.0D, 0.04D, 0.0D), false);
		}
		if ((power & DisciplePaths.BIT_RELEASED) != 0 && EffectQuality.chance(random, 0.3F)) {
			Vec3 at = chest.add(random.nextGaussian() * 0.35D, random.nextGaussian() * 0.4D, random.nextGaussian() * 0.35D);
			CommonFx.add(CommonFx.dust(CRIMSON, 0.7F), at, new Vec3(0.0D, 0.03D, 0.0D), false);
			if (random.nextInt(6) == 0) {
				CommonFx.add(ParticleTypes.SMALL_FLAME, at, new Vec3(0.0D, 0.02D, 0.0D), false);
			}
		}
		if ((power & DisciplePaths.BIT_HEIR) != 0 && t % 20L == 0L) {
			boolean nine = (power & DisciplePaths.BIT_NINE_HEXES) != 0;
			glyphRing(feet.add(0.0D, 0.06D, 0.0D), 0.8F, nine ? 0xFFF4C0 : GOLD, nine ? 0xFFFFFF : JADE, 9);
		}
		if ((power & DisciplePaths.BIT_RING) != 0 && EffectQuality.chance(random, 0.06F)) {
			CommonFx.add(CommonFx.dust(0xD6EEF5, 0.6F), feet.add(0.45D, height * 0.45D, 0.0D), new Vec3(0.0D, 0.02D, 0.0D), false);
		}
		long[] descent = DESCENTS.get(disciple.getId());
		if ((power & DisciplePaths.BIT_DESCEND) != 0) {
			int color = descent != null && descent[1] >= now ? (int) descent[0] : GOLD;
			int n = EffectQuality.minimal() ? (t % 2L == 0L ? 1 : 0) : EffectQuality.maximal() ? 3 : 2;
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 at = feet.add(Math.cos(angle) * 0.7D, random.nextDouble() * height, Math.sin(angle) * 0.7D);
				CommonFx.add(CommonFx.dust(color, 1.1F), at, new Vec3(0.0D, 0.05D, 0.0D), false);
			}
		}
		if ((power & DisciplePaths.BIT_GUARDED) != 0 && t % 10L == 0L) {
			glyphRing(feet.add(0.0D, 0.05D, 0.0D), 1.1F, JADE, 0xFFFFFF, 10);
		}
		if ((power & DisciplePaths.BIT_CALAMITY) != 0 && t % 8L == 0L) {
			glyphRing(feet.add(0.0D, 0.05D, 0.0D), 1.8F, 0x9B4DFF, GOLD, 14);
		}
		int foundation = power >> DisciplePaths.FOUNDATION_SHIFT & 0x7;
		if (foundation >= FoundationTypes.LANGIT && t % 6L == 0L && !EffectQuality.minimal()) {
			Vec3 back = feet.add(-Math.sin(Math.toRadians(-disciple.getYRot())) * -0.4D, 0.0D, -Math.cos(Math.toRadians(-disciple.getYRot())) * -0.4D);
			CommonFx.add(CommonFx.dust(foundation == FoundationTypes.SEMPURNA ? 0xFFFFFF : GOLD, 0.9F), back.add(0.0D, random.nextDouble() * height * 1.2D, 0.0D),
					Vec3.ZERO, false);
		}
	}

	/** The star orbit and the Stellar stage visuals. */
	private static void orbit(Vec3 feet, Vec3 chest, double height, int stars, long t, RandomSource random, boolean clone) {
		int tier = StarTitles.tier(stars);
		int color = StarTitles.color(stars);
		int qualityRings = EffectQuality.minimal() ? 1 : EffectQuality.maximal() ? 3 : 2;
		int rings = Math.max(1, Math.min(qualityRings, 1 + Math.max(0, tier) / 3));
		long period = EffectQuality.minimal() ? 4L : 2L;
		if (t % period == 0L) {
			for (int ring = 0; ring < rings; ring++) {
				double direction = ring % 2 == 0 ? 1.0D : -1.0D;
				double radius = 0.7D + 0.28D * ring;
				double tilt = 0.18D * (ring + 1);
				for (int i = 0; i < 9; i++) {
					double angle = t * 0.07D * direction + Math.PI * 2.0D * i / 9.0D + ring * 0.7D;
					Vec3 at = chest.add(Math.cos(angle) * radius, Math.sin(angle + ring) * tilt + 0.1D * ring, Math.sin(angle) * radius);
					CommonFx.add(CommonFx.dust(i == 0 ? 0xFFFFFF : color, clone ? 0.3F : 0.45F), at, Vec3.ZERO, false);
				}
			}
		}
		if (clone) {
			return;
		}
		int granules = StarTitles.granules(stars);
		if (granules > 0 && t % 2L == 0L) {
			for (int i = 0; i < granules; i++) {
				double angle = t * 0.16D + Math.PI * 2.0D * i / granules;
				CommonFx.add(CommonFx.dust(GOLD, 0.55F), chest.add(Math.cos(angle) * 0.45D, Math.sin(angle * 2.0D) * 0.1D, Math.sin(angle) * 0.45D), Vec3.ZERO, false);
			}
		}
		if (tier >= 2 && t % 6L == 0L) {
			CommonFx.add(CommonFx.dust(0xFFE27A, 1.2F), chest, Vec3.ZERO, false);
		}
		if (tier == 4 && EffectQuality.chance(random, 0.08F)) {
			CommonFx.add(ParticleTypes.ELECTRIC_SPARK, chest.add(random.nextGaussian() * 0.5D, random.nextGaussian() * 0.5D, random.nextGaussian() * 0.5D),
					Vec3.ZERO, false);
		}
		if (tier >= 5 && t % 3L == 0L) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			int sun = tier >= StarTitles.NAMED_TIERS ? color : 0xFFF1A8;
			CommonFx.add(CommonFx.dust(sun, 1.0F), feet.add(Math.cos(angle) * 0.75D, random.nextDouble() * height, Math.sin(angle) * 0.75D),
					new Vec3(0.0D, 0.035D, 0.0D), false);
		}
		if ((tier == 6 || tier == 7) && t % 3L == 0L) {
			for (int i = 0; i < 2; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				Vec3 at = chest.add(Math.cos(angle) * 1.4D, random.nextGaussian() * 0.4D, Math.sin(angle) * 1.4D);
				CommonFx.add(CommonFx.dust(tier == 7 ? 0x1A0E2E : 0x4A2E8A, 0.9F), at, chest.subtract(at).scale(0.09D), false);
			}
		}
	}

	static void clear() {
		DESCENTS.clear();
	}

	private PathFx() {
	}
}
