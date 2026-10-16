package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.Dao;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dao effects ({@code FxType.DAO_CHOSEN}, a = Dao ordinal):
 * <ul>
 *   <li>b = 0, Dao chosen: the eight trigrams of the Bagua unfold into a ring around the player and spin in the Dao's
 *   colour (the chosen Dao's trigram larger), a pillar of qi rises, Dao-flavoured motes drift up; Maximal adds two
 *   counter-rotating rings and sparkles. Ends with the ring collapsing into a burst and a shockwave.</li>
 *   <li>b = 1..4, a comprehension level reached: a smaller, quicker version (higher levels a little grander).</li>
 *   <li>b = 5, technique learned from a book (a = -1 for universal): glyphs stream into the reader, a burst and a ring.</li>
 *   <li>b = 6, insight from a book: a few glyphs and a small burst.</li>
 * </ul>
 * The ring follows the player it was played for, so walking does not leave it behind.
 */
public final class DaoFx {

	private static final int MAX_ACTIVE = 16;
	private static final double FOLLOW_RANGE_SQ = 2.5D * 2.5D;
	private static final int CEREMONY_TICKS = 70;
	private static final int LEVEL_TICKS = 40;

	private static final List<Ring> ACTIVE = new ArrayList<>();
	private static long lastTick = Long.MIN_VALUE;
	private static boolean hooked;

	/** Hooks the ring animation into the client tick once (a second tick in the same game tick is ignored). */
	public static void init() {
		if (!hooked) {
			hooked = true;
			ClientTickEvents.END_CLIENT_TICK.register(DaoFx::tick);
		}
	}

	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.DAO_CHOSEN) {
			return;
		}
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Dao dao = Dao.byOrdinal(fx.a());
		int rgb = dao == null ? TechniqueBooks.UNIVERSAL_COLOR : dao.color();
		int kind = fx.b();
		RandomSource random = CommonFx.random();
		if (kind == TechniqueBooks.FX_LEARNED || kind == TechniqueBooks.FX_INSIGHT) {
			book(pos, rgb, dao, kind == TechniqueBooks.FX_LEARNED, random);
			return;
		}
		int stage = Mth.clamp(kind, 0, 4);
		boolean ceremony = stage == 0;
		Ring ring = new Ring(followId(level, pos), pos, rgb, dao, stage, ceremony ? CEREMONY_TICKS : LEVEL_TICKS,
				random.nextFloat() * (float) (Math.PI * 2.0D));
		ACTIVE.add(ring);
		while (ACTIVE.size() > MAX_ACTIVE) {
			ACTIVE.remove(0);
		}
		// The first breath: a ring on the ground and glyphs drawn in from the air.
		CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), rgb, ceremony ? 1.0F : 0.7F, ceremony ? 28 : 16);
		int glyphs = EffectQuality.scale(ceremony ? 30 : 10 + 4 * stage);
		for (int i = 0; i < glyphs; i++) {
			CommonFx.add(ParticleTypes.ENCHANT, pos.x, pos.y + 1.2D, pos.z, random.nextGaussian() * 1.6D, random.nextFloat() * 1.4D,
					random.nextGaussian() * 1.6D);
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		if (minecraft.player == null || minecraft.isPaused() || ACTIVE.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		if (now == lastTick) {
			return;
		}
		lastTick = now;
		RandomSource random = level.getRandom();
		Iterator<Ring> iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			Ring ring = iterator.next();
			Vec3 center = ring.center(level);
			ringTick(ring, center, random);
			ring.age++;
			if (ring.age >= ring.duration) {
				finale(ring, center, random);
				iterator.remove();
			}
		}
	}

	// ================================================================== the trigram ring

	private static void ringTick(Ring ring, Vec3 center, RandomSource random) {
		boolean ceremony = ring.stage == 0;
		float size = ceremony ? 1.0F : 0.55F + 0.07F * ring.stage;
		float t = (float) ring.age / ring.duration;
		// Unfold quickly, hold, then pull in during the last ticks.
		float unfold = Mth.clamp(ring.age / (ceremony ? 14.0F : 8.0F), 0.0F, 1.0F);
		unfold = 1.0F - (1.0F - unfold) * (1.0F - unfold);
		float collapse = Mth.clamp((ring.duration - ring.age) / 10.0F, 0.0F, 1.0F);
		double radius = (0.4D + 1.9D * size * unfold) * (0.3D + 0.7D * collapse);
		double height = center.y + 0.15D + (ceremony ? 1.1D : 0.6D) * t;
		double spin = ring.phase + ring.age * (ceremony ? 0.07D : 0.11D) * (1.0D + 1.5D * (1.0D - collapse));

		int stride = EffectQuality.minimal() ? 4 : EffectQuality.maximal() ? 1 : 2;
		if (ring.age % stride == 0) {
			drawTrigrams(ring, center, radius, height, spin, size);
		}

		// Maximal: two thin rings turning against the trigrams.
		if (EffectQuality.maximal() && ring.age % 2 == 0) {
			arc(center, ring.rgb, radius * 0.55D, height + 0.02D, -spin * 1.6D, 14, 0.6F);
			arc(center, ring.rgb, radius * 1.45D, height - 0.02D, -spin * 0.8D, 22, 0.6F);
		}

		// The pillar of qi rising through the ring.
		if (ring.age >= 6 && ring.age < ring.duration - 6 && ring.age % 3 == 0) {
			float pillar = (ceremony ? 7.0F : 2.0F + 0.5F * ring.stage) * Mth.clamp((ring.age - 6) / 20.0F, 0.15F, 1.0F);
			CommonFx.pillar(center, ring.rgb, pillar);
			if (EffectQuality.maximal()) {
				CommonFx.add(ParticleTypes.END_ROD, center.x + random.nextGaussian() * 0.2D, center.y + random.nextFloat() * pillar,
						center.z + random.nextGaussian() * 0.2D, 0.0D, 0.06D, 0.0D);
			}
		}

		// Motes of the Dao's own nature drifting up from the ring.
		ParticleOptions flavour = flavour(ring.dao);
		if (EffectQuality.chance(random, ceremony ? 0.6F : 0.3F)) {
			double angle = random.nextFloat() * Math.PI * 2.0D;
			CommonFx.add(flavour, center.x + Math.cos(angle) * radius, height, center.z + Math.sin(angle) * radius,
					0.0D, 0.03D + random.nextFloat() * 0.03D, 0.0D);
		}
		// Glyphs flowing into the cultivator during the ceremony.
		if (ceremony && ring.age % 4 == 0) {
			int glyphs = EffectQuality.scale(3);
			for (int i = 0; i < glyphs; i++) {
				CommonFx.add(ParticleTypes.ENCHANT, center.x, center.y + 1.1D, center.z, random.nextGaussian() * 1.2D,
						random.nextFloat() * 0.8D, random.nextGaussian() * 1.2D);
			}
		}
	}

	/**
	 * The eight trigrams around the ring in Bagua order, each as three short bars stacked outwards (bottom line
	 * innermost): solid yang lines and broken yin lines. The ring's own Dao is drawn larger and brighter.
	 */
	private static void drawTrigrams(Ring ring, Vec3 center, double radius, double height, double spin, float size) {
		double pitch = 0.17D * size;
		for (int k = 0; k < Glyphs.BAGUA.length; k++) {
			Dao groupDao = Glyphs.BAGUA[k];
			boolean own = groupDao == ring.dao;
			int bits = Glyphs.trigramOf(groupDao);
			double angle = spin + Math.PI * 2.0D * k / Glyphs.BAGUA.length;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			double half = (own ? 0.42D : 0.32D) * size;
			int rgb = own ? brighten(ring.rgb) : ring.rgb;
			DustParticleOptions dust = CommonFx.dust(rgb, (own ? 0.9F : 0.7F) * Math.max(0.7F, size));
			for (int line = 0; line < 3; line++) {
				double r = radius + (line - 1) * pitch;
				boolean solid = ((bits >> line) & 1) == 1;
				double[] offsets = solid ? new double[]{-1.0D, -0.5D, 0.0D, 0.5D, 1.0D} : new double[]{-1.0D, -0.6D, 0.6D, 1.0D};
				for (double offset : offsets) {
					double along = offset * half;
					// Radial position plus a tangent offset (perpendicular to the radius).
					double x = center.x + cos * r - sin * along;
					double z = center.z + sin * r + cos * along;
					CommonFx.add(dust, x, height, z, 0.0D, 0.0D, 0.0D);
				}
			}
		}
	}

	/** {@code points} motes on a circle (a thin turning ring). */
	private static void arc(Vec3 center, int rgb, double radius, double y, double spin, int points, float scale) {
		DustParticleOptions dust = CommonFx.dust(rgb, scale);
		for (int i = 0; i < points; i++) {
			double angle = spin + Math.PI * 2.0D * i / points;
			CommonFx.add(dust, center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
		}
	}

	/** The ring folds into the cultivator: a burst at the dantian and a shockwave on the ground. */
	private static void finale(Ring ring, Vec3 center, RandomSource random) {
		boolean ceremony = ring.stage == 0;
		CommonFx.burst(center.add(0.0D, 1.1D, 0.0D), ring.rgb, ceremony ? 40 : 14 + 4 * ring.stage, ceremony ? 2.2F : 1.2F);
		CommonFx.ring(center.add(0.0D, 0.05D, 0.0D), ring.rgb, ceremony ? 3.2F : 1.6F + 0.2F * ring.stage, ceremony ? 56 : 28);
		if (ceremony || ring.stage >= 3) {
			CommonFx.ring(center.add(0.0D, 0.05D, 0.0D), brighten(ring.rgb), ceremony ? 2.2F : 1.1F, ceremony ? 40 : 20);
		}
		int sparks = EffectQuality.scale(ceremony ? 16 : 4 + 2 * ring.stage);
		ParticleOptions flavour = flavour(ring.dao);
		for (int i = 0; i < sparks; i++) {
			CommonFx.add(flavour, center.x, center.y + 1.0D, center.z, random.nextGaussian() * 0.15D, 0.05D + random.nextFloat() * 0.15D,
					random.nextGaussian() * 0.15D);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < (ceremony ? 24 : 8); i++) {
				CommonFx.add(ParticleTypes.END_ROD, center.x, center.y + 1.0D, center.z, random.nextGaussian() * 0.12D,
						random.nextFloat() * 0.2D, random.nextGaussian() * 0.12D);
			}
		}
	}

	// ================================================================== books

	/** Glyphs stream from all around into the reader, then a burst (and a ring for a newly learned technique). */
	private static void book(Vec3 pos, int rgb, @Nullable Dao dao, boolean learned, RandomSource random) {
		Vec3 chest = pos.add(0.0D, 1.2D, 0.0D);
		int glyphs = EffectQuality.scale(learned ? 40 : 16);
		for (int i = 0; i < glyphs; i++) {
			CommonFx.add(ParticleTypes.ENCHANT, chest.x, chest.y, chest.z, random.nextGaussian() * 1.8D, random.nextFloat() * 1.5D,
					random.nextGaussian() * 1.8D);
		}
		CommonFx.spiralIn(chest, rgb, learned ? 1.6F : 1.0F, learned ? 24 : 10, random.nextFloat() * 20.0F);
		CommonFx.burst(chest, rgb, learned ? 20 : 8, learned ? 1.4F : 0.8F);
		if (learned) {
			CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), rgb, 1.4F, 32);
			ParticleOptions flavour = flavour(dao);
			int sparks = EffectQuality.scale(8);
			for (int i = 0; i < sparks; i++) {
				CommonFx.add(flavour, chest.x + random.nextGaussian() * 0.4D, chest.y + random.nextFloat() * 0.6D,
						chest.z + random.nextGaussian() * 0.4D, 0.0D, 0.04D, 0.0D);
			}
			if (EffectQuality.maximal()) {
				CommonFx.pillar(pos, rgb, 3.0F);
			}
		}
	}

	// ================================================================== helpers

	/** A particle that feels like the Dao (golden sparkles for universal techniques). */
	private static ParticleOptions flavour(@Nullable Dao dao) {
		if (dao == null) {
			return ParticleTypes.END_ROD;
		}
		return switch (dao) {
			case SWORD -> ParticleTypes.CRIT;
			case FIRE -> ParticleTypes.FLAME;
			case ICE -> ParticleTypes.SNOWFLAKE;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case BODY -> ParticleTypes.WHITE_ASH;
			case LIFE -> ParticleTypes.HAPPY_VILLAGER;
			case SHADOW -> ParticleTypes.SMOKE;
			case SPACE -> ParticleTypes.REVERSE_PORTAL;
		};
	}

	/** Halfway to white. */
	private static int brighten(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
	}

	/** The nearest player at the effect position (the one it was played for), -1 when none. */
	private static int followId(ClientLevel level, Vec3 pos) {
		int best = -1;
		double bestSq = FOLLOW_RANGE_SQ;
		for (Entity player : level.players()) {
			double distSq = player.distanceToSqr(pos);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = player.getId();
			}
		}
		return best;
	}

	/** One running trigram ring. */
	private static final class Ring {
		private final int entityId;
		private final Vec3 origin;
		private final int rgb;
		private final @Nullable Dao dao;
		/** 0 = Dao chosen, 1..4 = comprehension level reached. */
		private final int stage;
		private final int duration;
		private final float phase;
		private int age;

		private Ring(int entityId, Vec3 origin, int rgb, @Nullable Dao dao, int stage, int duration, float phase) {
			this.entityId = entityId;
			this.origin = origin;
			this.rgb = rgb;
			this.dao = dao;
			this.stage = stage;
			this.duration = duration;
			this.phase = phase;
		}

		/** The followed player's feet, or the original position when they are gone. */
		private Vec3 center(ClientLevel level) {
			Entity entity = entityId < 0 ? null : level.getEntity(entityId);
			return entity == null || entity.isRemoved() ? origin : entity.position();
		}
	}

	private DaoFx() {
	}
}
