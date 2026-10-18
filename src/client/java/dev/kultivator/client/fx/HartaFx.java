package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Element;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HartaFxIds;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Effects of {@code FxType.TREASURE} (sub-ids in {@code harta.HartaFxIds}): spirit stones popping from ores, herbs found
 * in plants, cores absorbed, fed or dropped, stones dissolving into qi or a formation, the grey smoke of a rejected
 * pill and the catalyst swirl of a refining (DESIGN 19.3.2-19.3.7). Particles only, scaled by Effect Quality, and only
 * within 48 blocks of the camera.
 */
public final class HartaFx {

	private static final double RANGE_SQ = 48.0D * 48.0D;
	private static final int SMOKE_COLOR = 0x8A8A8A;

	/** One {@code FxType.TREASURE} packet. */
	public static void play(FxType type, Payloads.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		if (camera.distanceToSqr(pos) > RANGE_SQ) {
			return;
		}
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		RandomSource random = CommonFx.random();
		switch (fx.a()) {
			case HartaFxIds.STONE_FOUND -> stoneFound(pos, fx.b(), Math.max(1, Math.round(fx.f())), random);
			case HartaFxIds.HERB_FOUND -> herbFound(pos, fx.b(), Math.round(fx.f()), random);
			case HartaFxIds.CORE_ABSORB -> absorb(pos, elementColor(fx.b()), 10 + 6 * Math.round(fx.f()), random);
			case HartaFxIds.STONE_ABSORB -> absorb(pos, stoneColor(fx.b()), 10 + 6 * fx.b(), random);
			case HartaFxIds.CORE_FEED -> feed(pos, target, elementColor(fx.b()), Math.round(fx.f()), random);
			case HartaFxIds.FORMATION_FUEL -> formationFuel(pos, stoneColor(fx.b()), fx.b(), random);
			case HartaFxIds.CORE_DROP -> coreDrop(pos, elementColor(fx.b()), Math.round(fx.f()), random);
			case HartaFxIds.SMOKE -> smoke(pos, Math.max(0.5F, fx.f()), random);
			case HartaFxIds.CATALYST -> catalyst(pos, fx.b(), Math.round(fx.f()), random);
			default -> {
			}
		}
	}

	/** A spirit stone springs out of a broken ore: a jade-coloured burst and a few rising glints. */
	private static void stoneFound(Vec3 pos, int grade, int count, RandomSource random) {
		int color = stoneColor(grade);
		CommonFx.burst(pos, color, 10 + 4 * count, 1.2F);
		int glints = EffectQuality.scale(4 + 2 * count);
		for (int i = 0; i < glints; i++) {
			CommonFx.add(ParticleTypes.END_ROD, pos.add(random.nextGaussian() * 0.25D, random.nextGaussian() * 0.2D, random.nextGaussian() * 0.25D),
					new Vec3(random.nextGaussian() * 0.02D, 0.06D + random.nextFloat() * 0.05D, random.nextGaussian() * 0.02D), false);
		}
	}

	/** A herb found in a plant: green-gold motes rise; a thousand-year herb adds a gold ring and sparks. */
	private static void herbFound(Vec3 pos, int kind, int age, RandomSource random) {
		HerbKind[] kinds = HerbKind.values();
		int color = kind >= 0 && kind < kinds.length ? kinds[kind].color() : SpiritHerbs.SENSE_COLOR;
		DustParticleOptions dust = CommonFx.dust(color, 1.0F);
		int n = EffectQuality.scale(14);
		for (int i = 0; i < n; i++) {
			Vec3 at = pos.add(random.nextGaussian() * 0.3D, random.nextFloat() * 0.4D - 0.2D, random.nextGaussian() * 0.3D);
			CommonFx.add(dust, at, new Vec3(0.0D, 0.03D + random.nextFloat() * 0.04D, 0.0D), false);
		}
		int happy = EffectQuality.scale(5);
		for (int i = 0; i < happy; i++) {
			CommonFx.add(ParticleTypes.HAPPY_VILLAGER, pos.add(random.nextGaussian() * 0.35D, random.nextFloat() * 0.5D, random.nextGaussian() * 0.35D),
					Vec3.ZERO, false);
		}
		if (age >= SpiritHerbs.MILLENNIUM) {
			CommonFx.ring(pos.add(0.0D, -0.3D, 0.0D), SpiritHerbs.MILLENNIUM_COLOR, 0.9F, 24);
			CommonFx.pillar(pos, SpiritHerbs.MILLENNIUM_COLOR, 2.0F);
			if (EffectQuality.maximal()) {
				for (int i = 0; i < 8; i++) {
					CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, pos, new Vec3(random.nextGaussian() * 0.15D, 0.2D + random.nextFloat() * 0.2D,
							random.nextGaussian() * 0.15D), false);
				}
			}
		}
	}

	/** Qi of a core or stone flowing into the cultivator's dantian (pos = the feet). */
	private static void absorb(Vec3 feet, int color, int count, RandomSource random) {
		Vec3 dantian = feet.add(0.0D, 1.0D, 0.0D);
		CommonFx.spiralIn(dantian, color, 1.6F, count, (float) (CommonFx.level().getGameTime() % 1000L));
		int glow = EffectQuality.scale(4);
		for (int i = 0; i < glow; i++) {
			CommonFx.add(ParticleTypes.ENCHANT, dantian.add(random.nextGaussian() * 0.6D, random.nextGaussian() * 0.5D, random.nextGaussian() * 0.6D),
					new Vec3(0.0D, -0.05D, 0.0D), false);
		}
	}

	/** A core fed to a spirit beast: a thread of its colour from the player to the beast and a soft burst on the beast. */
	private static void feed(Vec3 beast, Vec3 player, int color, int grade, RandomSource random) {
		CommonFx.beam(player.add(0.0D, 1.2D, 0.0D), beast.add(0.0D, 0.5D, 0.0D), color, 0.08F);
		CommonFx.burst(beast.add(0.0D, 0.6D, 0.0D), color, 8 + 4 * grade, 1.0F);
		int hearts = EffectQuality.scale(3);
		for (int i = 0; i < hearts; i++) {
			CommonFx.add(ParticleTypes.HEART, beast.add(random.nextGaussian() * 0.3D, 1.0D + random.nextFloat() * 0.3D, random.nextGaussian() * 0.3D),
					Vec3.ZERO, false);
		}
	}

	/** A stone dissolving into a protective formation: a ring and a short column at the lodestone. */
	private static void formationFuel(Vec3 centre, int color, int grade, RandomSource random) {
		CommonFx.ring(centre.add(0.0D, 0.5D, 0.0D), color, 1.2F + 0.3F * grade, 32);
		CommonFx.pillar(centre.add(0.0D, 0.5D, 0.0D), color, 2.0F + grade);
		CommonFx.burst(centre.add(0.0D, 0.6D, 0.0D), color, 12 + 4 * grade, 1.4F);
	}

	/** A core left by a slain beast: a flash of its colour, taller for higher grades. */
	private static void coreDrop(Vec3 pos, int color, int grade, RandomSource random) {
		CommonFx.burst(pos, color, 8 + 6 * grade, 1.0F + 0.2F * grade);
		CommonFx.pillar(pos, color, 0.8F + 0.5F * grade);
		if (grade >= BeastCores.KING) {
			CommonFx.ring(pos, color, 1.0F, 28);
		}
	}

	/** Grey smoke of a pill the body rejects (Sumsum menolak, Pil Membalik). */
	private static void smoke(Vec3 feet, float strength, RandomSource random) {
		int n = EffectQuality.scale(Math.round(10 * strength));
		DustParticleOptions dust = CommonFx.dust(SMOKE_COLOR, 1.6F);
		for (int i = 0; i < n; i++) {
			Vec3 at = feet.add(random.nextGaussian() * 0.35D, 0.4D + random.nextFloat() * 1.4D, random.nextGaussian() * 0.35D);
			CommonFx.add(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at,
					new Vec3(random.nextGaussian() * 0.01D, 0.03D + random.nextFloat() * 0.03D, random.nextGaussian() * 0.01D), false);
			CommonFx.add(dust, at, new Vec3(0.0D, 0.02D, 0.0D), false);
		}
	}

	/** Catalysts in a refining: jade motes circle the cauldron; a quality floor adds a gold ring of its grade. */
	private static void catalyst(Vec3 top, int stones, int floor, RandomSource random) {
		CommonFx.spiralIn(top.add(0.0D, 0.4D, 0.0D), SpiritStones.COLORS[SpiritStones.LOW], 0.9F, 8 + 4 * Math.max(0, stones),
				(float) (CommonFx.level().getGameTime() % 1000L));
		if (floor > 0) {
			CommonFx.ring(top.add(0.0D, 0.2D, 0.0D), 0xFFC83C, 0.6F + 0.15F * floor, 16 + 4 * floor);
			if (EffectQuality.maximal()) {
				CommonFx.add(ParticleTypes.END_ROD, top.add(0.0D, 0.3D, 0.0D), new Vec3(0.0D, 0.08D, 0.0D), false);
			}
		}
	}

	private static int stoneColor(int grade) {
		return SpiritStones.COLORS[Math.max(0, Math.min(SpiritStones.COLORS.length - 1, grade))];
	}

	private static int elementColor(int ordinal) {
		Element[] elements = Element.values();
		return ordinal >= 0 && ordinal < elements.length ? elements[ordinal].color() : BeastCores.NEUTRAL_COLOR;
	}

	private HartaFx() {
	}
}
