package dev.kultivator.client.fx;

import dev.kultivator.alchemy.AlchemyRecipes;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Alchemy furnace effects ({@code ALCHEMY}, pos = top centre of the cauldron):
 * <ul>
 * <li>a = 0 an ingredient drops into the pot: crumbs of the item (b = its raw item id) fall in with sparkles in its
 * colour, glyphs flow into the pot and a ripple runs over the surface (f = how full the pot is);</li>
 * <li>a = 1 refining (sent every second, b = ticks left, f = steam colour as RGB): keeps a cauldron emitter alive that
 * bubbles, breathes coloured steam and swirls two strands of qi upwards; the last seconds grow more intense;</li>
 * <li>a = 2 success (b = pill quality, f = pill colour): a burst and flash in the quality colour, then a glowing two-tone
 * pill rises from the pot, turning, and pops into sparkles;</li>
 * <li>a = 3 failure: a puff of black smoke and ink (b = 1: the smaller puff of a pot spilling out).</li>
 * </ul>
 * Everything is particles only and scaled by {@link EffectQuality}.
 */
public final class AlchemyFx {

	private static final int MAX_EMITTERS = 24;
	/** A refining emitter without a refresh for this many ticks fades out. */
	private static final int REFRESH_TICKS = 30;
	private static final int GLOW_TICKS = 26;
	private static final int SMOKE_DARK = 0x1A1A1A;
	private static final int SMOKE_GREY = 0x3A3A3A;
	private static final double SAME_POT_SQ = 0.01D;

	private static final List<Refining> REFINING = new ArrayList<>();
	private static final List<PillGlow> GLOWS = new ArrayList<>();
	private static ClientLevel lastLevel;

	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.ALCHEMY || CommonFx.level() == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		switch (fx.a()) {
			case 0 -> ingredient(pos, fx.b(), fx.f());
			case 1 -> refining(pos, fx.b(), rgb(fx.f()));
			case 2 -> success(pos, PillQuality.byOrdinal(fx.b()), rgb(fx.f()));
			case 3 -> failure(pos, fx.b() == 1);
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			REFINING.clear();
			GLOWS.clear();
		}
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Iterator<Refining> refining = REFINING.iterator();
		while (refining.hasNext()) {
			Refining pot = refining.next();
			refiningTick(pot, random);
			pot.age++;
			pot.life--;
			pot.ticksLeft--;
			if (pot.life <= 0) {
				refining.remove();
			}
		}
		Iterator<PillGlow> glows = GLOWS.iterator();
		while (glows.hasNext()) {
			PillGlow glow = glows.next();
			glowTick(glow, random);
			glow.age++;
			if (glow.age >= GLOW_TICKS) {
				pop(glow, random);
				glows.remove();
			}
		}
	}

	// ================================================================ a = 0: an ingredient goes in

	private static void ingredient(Vec3 pos, int itemId, float fill) {
		RandomSource random = CommonFx.random();
		Item item = BuiltInRegistries.ITEM.byId(itemId);
		int rgb = AlchemyRecipes.color(item);
		Vec3 surface = surface(pos);
		if (item != null && item != Items.AIR) {
			ItemParticleOption crumbs = new ItemParticleOption(ParticleTypes.ITEM, item);
			for (int i = 0; i < EffectQuality.scale(8); i++) {
				CommonFx.add(crumbs, surface.x + (random.nextFloat() - 0.5F) * 0.3D, surface.y + 0.6D + random.nextFloat() * 0.3D,
						surface.z + (random.nextFloat() - 0.5F) * 0.3D, (random.nextFloat() - 0.5F) * 0.04D, -0.05D, (random.nextFloat() - 0.5F) * 0.04D);
			}
		}
		DustParticleOptions dust = CommonFx.dust(rgb, 0.9F);
		for (int i = 0; i < EffectQuality.scale(6); i++) {
			CommonFx.add(ParticleTypes.END_ROD, surface.x + (random.nextFloat() - 0.5F) * 0.5D, surface.y + 0.7D + random.nextFloat() * 0.4D,
					surface.z + (random.nextFloat() - 0.5F) * 0.5D, 0.0D, -0.06D - random.nextFloat() * 0.03D, 0.0D);
			CommonFx.add(dust, surface.x + (random.nextFloat() - 0.5F) * 0.6D, surface.y + 0.2D + random.nextFloat() * 0.6D,
					surface.z + (random.nextFloat() - 0.5F) * 0.6D, 0.0D, 0.0D, 0.0D);
		}
		// Glyphs drawn from the air into the pot (enchant particles fly from pos + velocity to pos).
		for (int i = 0; i < EffectQuality.scale(5); i++) {
			CommonFx.add(ParticleTypes.ENCHANT, surface.x, surface.y + 0.1D, surface.z, (random.nextFloat() - 0.5F) * 1.6D,
					0.4D + random.nextFloat() * 0.8D, (random.nextFloat() - 0.5F) * 1.6D);
		}
		CommonFx.ring(surface, rgb, 0.33F, 12);
		for (int i = 0; i < EffectQuality.scale(3); i++) {
			CommonFx.add(ParticleTypes.BUBBLE_POP, surface.x + (random.nextFloat() - 0.5F) * 0.5D, surface.y,
					surface.z + (random.nextFloat() - 0.5F) * 0.5D, 0.0D, 0.03D, 0.0D);
		}
		if (EffectQuality.maximal() && fill >= 0.99F) {
			// A full pot hums: a faint golden ring at the rim.
			CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), 0xFFE08A, 0.55F, 20);
		}
	}

	// ================================================================ a = 1: refining

	private static void refining(Vec3 pos, int ticksLeft, int rgb) {
		for (Refining pot : REFINING) {
			if (pot.pos.distanceToSqr(pos) < SAME_POT_SQ) {
				pot.rgb = rgb;
				pot.ticksLeft = ticksLeft;
				pot.life = Math.min(REFRESH_TICKS, Math.max(1, ticksLeft + 5));
				return;
			}
		}
		Refining pot = new Refining(pos, rgb, ticksLeft, Math.min(REFRESH_TICKS, Math.max(1, ticksLeft + 5)));
		REFINING.add(pot);
		while (REFINING.size() > MAX_EMITTERS) {
			REFINING.remove(0);
		}
		// The fire roars up: a quick ring of flame around the cauldron.
		RandomSource random = CommonFx.random();
		ParticleOptions flame = soulFire(pos) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
		int n = EffectQuality.scale(14);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			CommonFx.add(flame, pos.x + Math.cos(angle) * 0.62D, pos.y - 0.9D, pos.z + Math.sin(angle) * 0.62D,
					Math.cos(angle) * 0.02D, 0.06D + random.nextFloat() * 0.04D, Math.sin(angle) * 0.02D);
		}
	}

	private static void refiningTick(Refining pot, RandomSource random) {
		Vec3 surface = surface(pot.pos);
		boolean finale = pot.ticksLeft < 60;
		float intensity = finale ? 1.8F : 1.0F;
		int rgb = pot.rgb;

		// Bubbling.
		if (EffectQuality.chance(random, 0.6F * intensity)) {
			CommonFx.add(ParticleTypes.BUBBLE_POP, surface.x + (random.nextFloat() - 0.5F) * 0.55D, surface.y,
					surface.z + (random.nextFloat() - 0.5F) * 0.55D, 0.0D, 0.04D, 0.0D);
		}
		// Coloured steam rising (spell particles drift upwards on their own).
		ColorParticleOption steam = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | rgb);
		int puffs = random.nextFloat() < 0.5F * intensity ? 2 : 1;
		for (int i = 0; i < puffs; i++) {
			if (EffectQuality.chance(random, 0.8F)) {
				CommonFx.add(steam, surface.x + (random.nextFloat() - 0.5F) * 0.5D, surface.y + 0.05D,
						surface.z + (random.nextFloat() - 0.5F) * 0.5D, 0.0D, 0.06D, 0.0D);
			}
		}
		if (pot.age % 6 == 0 && EffectQuality.chance(random, 0.7F)) {
			CommonFx.add(ParticleTypes.WHITE_SMOKE, surface.x + (random.nextFloat() - 0.5F) * 0.4D, surface.y + 0.1D,
					surface.z + (random.nextFloat() - 0.5F) * 0.4D, 0.0D, 0.03D, 0.0D);
		}
		// Two strands of qi spiralling up out of the pot.
		DustParticleOptions qi = CommonFx.dust(lighten(rgb), 0.7F);
		double rise = (pot.age % 24) / 24.0D;
		for (int k = 0; k < 2; k++) {
			if (!EffectQuality.chance(random, 0.95F)) {
				continue;
			}
			double angle = pot.age * 0.38D + Math.PI * k;
			double radius = 0.42D - 0.22D * rise;
			CommonFx.add(qi, surface.x + Math.cos(angle) * radius, surface.y + 0.1D + rise * 1.3D,
					surface.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
			if (EffectQuality.maximal()) {
				double back = angle - 0.35D;
				CommonFx.add(qi, surface.x + Math.cos(back) * radius, surface.y + 0.05D + rise * 1.3D,
						surface.z + Math.sin(back) * radius, 0.0D, 0.0D, 0.0D);
			}
		}
		// Flames licking up the cauldron's sides.
		if (EffectQuality.chance(random, EffectQuality.maximal() ? 0.4F : 0.15F)) {
			ParticleOptions flame = soulFire(pot.pos) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
			double angle = random.nextFloat() * Math.PI * 2.0D;
			CommonFx.add(flame, pot.pos.x + Math.cos(angle) * 0.58D, pot.pos.y - 0.95D, pot.pos.z + Math.sin(angle) * 0.58D,
					0.0D, 0.025D, 0.0D);
		}
		// The last seconds: qi from all around is drawn into the pot.
		if (finale) {
			if (EffectQuality.chance(random, 0.8F)) {
				CommonFx.add(ParticleTypes.ENCHANT, surface.x, surface.y + 0.2D, surface.z, (random.nextFloat() - 0.5F) * 2.4D,
						0.3D + random.nextFloat() * 1.2D, (random.nextFloat() - 0.5F) * 2.4D);
			}
			if (pot.age % 4 == 0 && EffectQuality.chance(random, 0.6F)) {
				CommonFx.add(ParticleTypes.END_ROD, surface.x + (random.nextFloat() - 0.5F) * 0.3D, surface.y + 0.2D,
						surface.z + (random.nextFloat() - 0.5F) * 0.3D, 0.0D, 0.04D, 0.0D);
			}
		}
	}

	// ================================================================ a = 2: success

	private static void success(Vec3 pos, PillQuality quality, int pillRgb) {
		RandomSource random = CommonFx.random();
		int q = quality.ordinal();
		int rgb = quality.color();
		Vec3 surface = surface(pos);
		CommonFx.burst(surface.add(0.0D, 0.5D, 0.0D), rgb, 20 + 10 * q, 1.0F + 0.3F * q);
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | rgb), surface.add(0.0D, 0.6D, 0.0D), Vec3.ZERO, true);
		CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), rgb, 0.8F + 0.25F * q, 32);
		ColorParticleOption steam = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | pillRgb);
		for (int i = 0; i < EffectQuality.scale(12); i++) {
			CommonFx.add(steam, surface.x + (random.nextFloat() - 0.5F) * 0.6D, surface.y + random.nextFloat() * 0.3D,
					surface.z + (random.nextFloat() - 0.5F) * 0.6D, 0.0D, 0.1D, 0.0D);
		}
		if (q >= PillQuality.HIGH.ordinal()) {
			int n = EffectQuality.scale(16);
			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2.0D * i / n;
				CommonFx.add(ParticleTypes.END_ROD, surface.x, surface.y + 0.4D, surface.z, Math.cos(angle) * 0.12D, 0.02D,
						Math.sin(angle) * 0.12D);
			}
		}
		if (quality == PillQuality.PERFECT) {
			for (int i = 0; i < EffectQuality.scale(14); i++) {
				CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, surface.x, surface.y + 0.5D, surface.z, random.nextGaussian() * 0.25D,
						0.25D + random.nextFloat() * 0.3D, random.nextGaussian() * 0.25D);
			}
			if (EffectQuality.maximal()) {
				for (int i = 0; i < 10; i++) {
					CommonFx.add(ParticleTypes.FIREWORK, surface.x, surface.y + 1.2D, surface.z, random.nextGaussian() * 0.12D,
							random.nextFloat() * 0.15D, random.nextGaussian() * 0.12D);
				}
			}
		}
		GLOWS.add(new PillGlow(surface, rgb, pillRgb, random.nextFloat() * (float) (Math.PI * 2.0D), q));
		while (GLOWS.size() > MAX_EMITTERS) {
			GLOWS.remove(0);
		}
	}

	/** The rising pill: a turning two-tone capsule of light (half pill colour, half quality colour). */
	private static void glowTick(PillGlow glow, RandomSource random) {
		double t = (double) glow.age / GLOW_TICKS;
		double ease = 1.0D - (1.0D - t) * (1.0D - t);
		Vec3 centre = glow.origin.add(0.0D, 0.25D + ease * 1.35D, 0.0D);
		double yaw = glow.phase + glow.age * 0.3D;
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);
		double half = 0.16D;
		double radius = 0.09D;
		int points = EffectQuality.scale(10 + 2 * glow.quality);
		DustParticleOptions pillDust = CommonFx.dust(glow.pillRgb, 0.55F);
		DustParticleOptions qualityDust = CommonFx.dust(glow.qualityRgb, 0.55F);
		for (int i = 0; i < points; i++) {
			// A point on the capsule outline in its own plane: along the axis u in [-half - r, half + r], v the edge.
			double s = (random.nextDouble() * 2.0D - 1.0D) * (half + radius);
			double v;
			if (Math.abs(s) <= half) {
				v = random.nextBoolean() ? radius : -radius;
			} else {
				double d = Math.abs(s) - half;
				v = Math.sqrt(Math.max(0.0D, radius * radius - d * d)) * (random.nextBoolean() ? 1.0D : -1.0D);
			}
			double x = centre.x + s * cos;
			double y = centre.y + v;
			double z = centre.z + s * sin;
			CommonFx.add(s < 0.0D ? pillDust : qualityDust, x, y, z, 0.0D, 0.0D, 0.0D);
		}
		if (glow.age % 3 == 0 && EffectQuality.chance(random, 0.9F)) {
			CommonFx.add(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, random.nextGaussian() * 0.01D, -0.01D, random.nextGaussian() * 0.01D);
		}
		if (EffectQuality.maximal() && glow.age % 2 == 0) {
			CommonFx.add(ParticleTypes.GLOW, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
		}
	}

	private static void pop(PillGlow glow, RandomSource random) {
		Vec3 top = glow.origin.add(0.0D, 1.6D, 0.0D);
		CommonFx.burst(top, glow.qualityRgb, 10 + 4 * glow.quality, 0.8F);
		for (int i = 0; i < EffectQuality.scale(4 + 2 * glow.quality); i++) {
			CommonFx.add(ParticleTypes.END_ROD, top.x, top.y, top.z, random.nextGaussian() * 0.06D, random.nextGaussian() * 0.06D,
					random.nextGaussian() * 0.06D);
		}
	}

	// ================================================================ a = 3: failure

	private static void failure(Vec3 pos, boolean spill) {
		RandomSource random = CommonFx.random();
		Vec3 surface = surface(pos);
		int big = EffectQuality.scale(spill ? 6 : 16);
		for (int i = 0; i < big; i++) {
			CommonFx.add(ParticleTypes.LARGE_SMOKE, surface.x + (random.nextFloat() - 0.5F) * 0.5D, surface.y + random.nextFloat() * 0.3D,
					surface.z + (random.nextFloat() - 0.5F) * 0.5D, (random.nextFloat() - 0.5F) * 0.06D, 0.06D + random.nextFloat() * 0.08D,
					(random.nextFloat() - 0.5F) * 0.06D);
		}
		for (int i = 0; i < EffectQuality.scale(spill ? 4 : 10); i++) {
			CommonFx.add(ParticleTypes.SMOKE, surface.x + (random.nextFloat() - 0.5F) * 0.6D, surface.y + 0.1D,
					surface.z + (random.nextFloat() - 0.5F) * 0.6D, 0.0D, 0.08D, 0.0D);
		}
		if (!spill) {
			// Black ink-like soot bursting out of the pot.
			for (int i = 0; i < EffectQuality.scale(8); i++) {
				CommonFx.add(ParticleTypes.SQUID_INK, surface.x, surface.y + 0.2D, surface.z, (random.nextFloat() - 0.5F) * 0.25D,
						0.08D + random.nextFloat() * 0.12D, (random.nextFloat() - 0.5F) * 0.25D);
			}
			CommonFx.burst(surface.add(0.0D, 0.4D, 0.0D), SMOKE_DARK, 14, 0.8F);
			CommonFx.add(ParticleTypes.POOF, surface.x, surface.y + 0.3D, surface.z, 0.0D, 0.05D, 0.0D);
		} else {
			CommonFx.burst(surface.add(0.0D, 0.2D, 0.0D), SMOKE_GREY, 6, 0.5F);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 8; i++) {
				CommonFx.add(ParticleTypes.ASH, surface.x + (random.nextFloat() - 0.5F) * 1.2D, surface.y + 0.8D + random.nextFloat() * 0.6D,
						surface.z + (random.nextFloat() - 0.5F) * 1.2D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	// ================================================================ helpers

	/** Where the liquid would be: just below the rim. */
	private static Vec3 surface(Vec3 top) {
		return top.add(0.0D, -0.12D, 0.0D);
	}

	/** True when the cauldron under {@code top} stands on a soul campfire (blue flames). */
	private static boolean soulFire(Vec3 top) {
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return false;
		}
		BlockState below = level.getBlockState(BlockPos.containing(top.x, top.y - 1.5D, top.z));
		return below.is(Blocks.SOUL_CAMPFIRE);
	}

	private static int rgb(float encoded) {
		return ((int) encoded) & 0xFFFFFF;
	}

	/** Halfway towards white. */
	private static int lighten(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
	}

	/** A cauldron being refined in, kept alive by the server's once-a-second packets. */
	private static final class Refining {
		final Vec3 pos;
		int rgb;
		int ticksLeft;
		int life;
		int age;

		Refining(Vec3 pos, int rgb, int ticksLeft, int life) {
			this.pos = pos;
			this.rgb = rgb;
			this.ticksLeft = ticksLeft;
			this.life = life;
		}
	}

	/** A refined pill rising from the pot. */
	private static final class PillGlow {
		final Vec3 origin;
		final int qualityRgb;
		final int pillRgb;
		final float phase;
		final int quality;
		int age;

		PillGlow(Vec3 origin, int qualityRgb, int pillRgb, float phase, int quality) {
			this.origin = origin;
			this.qualityRgb = qualityRgb;
			this.pillRgb = pillRgb;
			this.phase = phase;
			this.quality = quality;
		}
	}

	private AlchemyFx() {
	}
}
