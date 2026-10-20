package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Continuous effects of disciples within 32 blocks, all from their synced entity data and scaled by Effect Quality:
 * <ul>
 * <li>an aura per realm, simpler than the player's: pale mist (Qi Refining), rising motes (Foundation and up), golden
 * beads circling the waist (Golden Core and up) and a small halo above the head (Nascent Soul and up);</li>
 * <li>qi motes in the root's colour spiralling into the dantian while it meditates;</li>
 * <li>a qi vortex and rising column in the next realm's colour while it breaks through;</li>
 * <li>black-red wisps during a Qi Deviation and small qi sparks at the fists while it fights.</li>
 * </ul>
 * One-shot disciple effects come from the server as the common BURST / RING / PILLAR effects.
 */
public final class DiscipleFx {

	private static final double RANGE = 32.0D;
	private static final double RANGE_SQ = RANGE * RANGE;
	private static final int GOLD = 0xFFD54A;
	/** How much of the standing height is left while seated. */
	private static final double SEATED_HEIGHT = 0.69D;
	/** Height of a seated disciple's lower dantian above the ground. */
	private static final double DANTIAN = 0.45D;

	private static ClientLevel lastLevel;

	/** Every client tick while in a world and not paused. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			DiscipleLook.clear();
		}
		long time = level.getGameTime();
		if (time % 100L == 0L) {
			DiscipleLook.cleanup(level);
		}
		RandomSource random = level.getRandom();
		Vec3 eye = minecraft.player.position();
		AABB box = minecraft.player.getBoundingBox().inflate(RANGE);
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box)) {
			if (disciple.isRemoved() || !disciple.isAlive() || disciple.isInvisible() || disciple.distanceToSqr(eye) > RANGE_SQ) {
				continue;
			}
			draw(disciple, random, time + disciple.getId() * 31L);
		}
	}

	private static void draw(DiscipleEntity disciple, RandomSource random, long t) {
		Rank rank = DiscipleLook.rank(disciple);
		int flags = DiscipleLook.flags(disciple);
		boolean seated = DiscipleLook.seated(disciple);
		double height = disciple.getBbHeight() * (seated ? SEATED_HEIGHT : 1.0D);
		double width = disciple.getBbWidth();
		Vec3 feet = disciple.position();
		// DESIGN 31.2.6: while the Aura Kultivasi covers this disciple it replaces the aura and the meditation motes
		boolean covered = dev.kultivator.client.aura.AuraEngine.covers(disciple.getId());
		if (!covered) {
			aura(feet, width, height, rank, DiscipleLook.color(rank.realm().ordinal(), DiscipleLook.dao(disciple)), random, t);
		}
		if ((flags & DiscipleLook.MEDITATING) != 0 && !covered) {
			meditation(feet, DiscipleLook.rootColor(disciple), random, t);
		}
		if ((flags & DiscipleLook.BREAKING_THROUGH) != 0) {
			breakthrough(feet, height, rank, random, t);
		}
		if ((flags & DiscipleLook.DEVIATING) != 0) {
			deviation(feet, width, height, random);
		}
		if ((flags & DiscipleLook.FIGHTING) != 0 && !seated) {
			fighting(disciple, rank.realm().color(), random);
		}
	}

	// ================================================================ aura

	private static void aura(Vec3 feet, double width, double height, Rank rank, int tint, RandomSource random, long t) {
		Realm realm = rank.realm();
		if (realm.isMortal()) {
			return;
		}
		int tier = realm.ordinal();
		int stage = rank.stage();
		int color = realm.color();
		if (realm == Realm.QI_REFINING) {
			int n = amount(random, 0.25F + 0.08F * stage);
			for (int i = 0; i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double radius = width * (0.5D + random.nextDouble() * 0.6D);
				Vec3 at = feet.add(Math.cos(angle) * radius, 0.1D + random.nextDouble() * height * 0.75D, Math.sin(angle) * radius);
				CommonFx.add(CommonFx.dust(lighten(color, 0.15F + random.nextFloat() * 0.2F), 0.5F + random.nextFloat() * 0.3F), at,
						new Vec3(Math.cos(angle) * 0.015D, 0.01D, Math.sin(angle) * 0.015D), false);
			}
			return;
		}
		// Rising motes, a little denser with every realm and stage.
		int n = amount(random, 0.18F + 0.06F * tier + 0.05F * stage + 0.02F * rank.layer());
		float size = Math.min(1.4F, 0.45F + 0.05F * tier + 0.04F * stage);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = width * (0.55D + random.nextDouble() * 0.45D);
			Vec3 at = feet.add(Math.cos(angle) * radius, random.nextDouble() * height * 0.6D, Math.sin(angle) * radius);
			int rgb = i % 3 == 2 ? lighten(tint, 0.2F) : lighten(color, 0.05F + random.nextFloat() * 0.2F);
			CommonFx.add(CommonFx.dust(rgb, size * (0.8F + random.nextFloat() * 0.4F)), at,
					new Vec3((random.nextDouble() - 0.5D) * 0.006D, 0.025D + 0.004D * tier + random.nextDouble() * 0.01D,
							(random.nextDouble() - 0.5D) * 0.006D), false);
		}
		// Golden Core and up: two beads circling the waist (three from Deity Transformation).
		if (tier >= Realm.GOLDEN_CORE.ordinal() && !EffectQuality.minimal() && t % 2L == 0L) {
			int beads = tier >= Realm.DEITY_TRANSFORMATION.ordinal() ? 3 : 2;
			double spin = t * 0.07D;
			double orbit = width * 1.2D + 0.03D * stage;
			for (int i = 0; i < beads; i++) {
				double angle = spin + i * Math.PI * 2.0D / beads;
				CommonFx.add(CommonFx.dust(GOLD, 0.75F + 0.05F * stage),
						feet.add(Math.cos(angle) * orbit, height * 0.5D + Math.sin(spin * 1.7D + i) * 0.06D, Math.sin(angle) * orbit),
						Vec3.ZERO, false);
			}
		}
		// Nascent Soul and up: a small halo above the head.
		if (tier >= Realm.NASCENT_SOUL.ordinal() && t % (EffectQuality.maximal() ? 3L : 6L) == 0L && !EffectQuality.minimal()) {
			double spin = t * 0.12D;
			int points = EffectQuality.maximal() ? 8 : 5;
			for (int i = 0; i < points; i++) {
				double angle = spin + i * Math.PI * 2.0D / points;
				CommonFx.add(CommonFx.dust(lighten(color, 0.6F), 0.45F),
						feet.add(Math.cos(angle) * 0.24D, height + 0.25D, Math.sin(angle) * 0.24D), Vec3.ZERO, false);
			}
		}
		if (tier >= Realm.IMMORTAL_ASCENSION.ordinal() && EffectQuality.maximal() && random.nextFloat() < 0.08F) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.END_ROD, feet.add(Math.cos(angle) * width, random.nextDouble() * height, Math.sin(angle) * width),
					new Vec3(0.0D, 0.02D, 0.0D), false);
		}
	}

	// ================================================================ activities

	/** Qi motes spiralling into the lower dantian of the seated disciple, in its root's colour. */
	private static void meditation(Vec3 feet, int rootColor, RandomSource random, long t) {
		Vec3 dantian = feet.add(0.0D, DANTIAN, 0.0D);
		int n = amount(random, 0.6F);
		for (int i = 0; i < n; i++) {
			double angle = t * 0.2D + random.nextDouble() * Math.PI * 2.0D;
			double radius = 1.0D + random.nextDouble() * 0.6D;
			Vec3 at = dantian.add(Math.cos(angle) * radius, 0.2D + random.nextDouble() * 1.1D, Math.sin(angle) * radius);
			// Inwards with a sideways swirl.
			Vec3 in = dantian.subtract(at).scale(0.07D);
			Vec3 swirl = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(0.03D);
			CommonFx.add(CommonFx.dust(lighten(rootColor, random.nextFloat() * 0.25F), 0.6F + random.nextFloat() * 0.3F), at, in.add(swirl), false);
		}
		if (EffectQuality.maximal() && t % 10L == 0L) {
			CommonFx.ring(feet, rootColor, 1.1F, 14);
		}
	}

	/** A strong qi vortex and a rising column in the colour of the realm it is breaking into. */
	private static void breakthrough(Vec3 feet, double height, Rank rank, RandomSource random, long t) {
		int target = rank.isMax() ? rank.realm().color() : rank.next().realm().color();
		Vec3 center = feet.add(0.0D, DANTIAN + 0.2D, 0.0D);
		int n = amount(random, 2.2F);
		for (int i = 0; i < n; i++) {
			double angle = t * 0.3D + i * 2.1D + random.nextDouble() * 0.6D;
			double radius = 1.6D + random.nextDouble() * 1.4D;
			Vec3 at = center.add(Math.cos(angle) * radius, random.nextDouble() * 2.2D - 0.3D, Math.sin(angle) * radius);
			Vec3 in = center.subtract(at).scale(0.09D).add(new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(0.06D));
			CommonFx.add(CommonFx.dust(lighten(target, random.nextFloat() * 0.3F), 0.9F + random.nextFloat() * 0.5F), at, in, true);
		}
		int column = amount(random, 0.8F);
		for (int i = 0; i < column; i++) {
			CommonFx.add(CommonFx.dust(lighten(target, 0.4F), 1.1F), feet.add(random.nextGaussian() * 0.12D, height + random.nextDouble() * 1.5D,
					random.nextGaussian() * 0.12D), new Vec3(0.0D, 0.08D, 0.0D), true);
		}
		if (EffectQuality.maximal() && random.nextFloat() < 0.15F) {
			CommonFx.add(ParticleTypes.ELECTRIC_SPARK, center.add(random.nextGaussian() * 0.5D, random.nextDouble(), random.nextGaussian() * 0.5D),
					new Vec3(random.nextGaussian() * 0.1D, 0.1D, random.nextGaussian() * 0.1D), false);
		}
	}

	/** Black-red wisps leaking out of a deviating disciple. */
	private static void deviation(Vec3 feet, double width, double height, RandomSource random) {
		if (!EffectQuality.chance(random, 0.35F)) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0D;
		Vec3 at = feet.add(Math.cos(angle) * width * 0.6D, random.nextDouble() * height, Math.sin(angle) * width * 0.6D);
		CommonFx.add(CommonFx.dust(random.nextBoolean() ? 0x5A0A0A : 0x1A0505, 0.9F), at, new Vec3(0.0D, 0.02D, 0.0D), false);
		if (random.nextFloat() < 0.3F) {
			CommonFx.add(ParticleTypes.SMOKE, at, new Vec3(0.0D, 0.03D, 0.0D), false);
		}
	}

	/** Small qi sparks at the fists while it fights. */
	private static void fighting(DiscipleEntity disciple, int realmColor, RandomSource random) {
		if (!EffectQuality.chance(random, 0.2F)) {
			return;
		}
		double yaw = Math.toRadians(disciple.yBodyRot);
		double side = random.nextBoolean() ? 0.35D : -0.35D;
		Vec3 fist = disciple.position().add(Math.cos(yaw) * side - Math.sin(yaw) * 0.25D, disciple.getBbHeight() * 0.45D,
				Math.sin(yaw) * side + Math.cos(yaw) * 0.25D);
		CommonFx.add(CommonFx.dust(lighten(realmColor, 0.3F), 0.6F), fist,
				new Vec3(random.nextGaussian() * 0.02D, 0.02D, random.nextGaussian() * 0.02D), false);
	}

	// ================================================================ helpers

	/** Particles this tick for a rate per tick at Normal quality (fractions become a chance). */
	private static int amount(RandomSource random, float rate) {
		float scaled = rate * EffectQuality.level().factor();
		int whole = (int) scaled;
		return whole + (random.nextFloat() < scaled - whole ? 1 : 0);
	}

	private static int lighten(int rgb, float t) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	private DiscipleFx() {
	}
}
