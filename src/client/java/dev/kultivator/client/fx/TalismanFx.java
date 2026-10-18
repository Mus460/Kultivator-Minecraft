package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.talisman.Talismans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Effects of {@code FxType.TALISMAN} (sub-ids in {@link TalismanFxIds}, DESIGN 19.3.5, 19.3.6, 19.3.11): paper burning
 * into glyphs, the eight talismans, the inscribing brush and its ash, the Unbroken Soul shatter, player stars, opening
 * meridians, the strengthened foundation, the vein compass ping, stone fuel and the cave abode. Particles only, within
 * {@link #RANGE} blocks, scaled by Effect Quality and paid from {@link FxBudget}.
 */
public final class TalismanFx {

	/** Effects further than this from the camera are skipped. */
	public static final double RANGE = 32.0D;
	private static final int GOLD = 0xFFD54A;
	private static final int PAPER = 0xF2E2B0;
	private static final int JADE = 0x8FE3C8;
	private static final int[] STONE_COLORS = {0xBFE8FF, 0x7CFFB2, 0xC69CFF, 0xFFD54A};

	/** One {@code FxType.TALISMAN} packet. */
	public static void play(FxType type, Payloads.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		if (pos.distanceToSqr(camera) > RANGE * RANGE && target.distanceToSqr(camera) > RANGE * RANGE) {
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		float height = fx.f() > 0.0F ? fx.f() : 1.8F;
		switch (fx.a()) {
			case TalismanFxIds.ACTIVATE -> activate(pos, kindColor(fx.b()), random);
			case TalismanFxIds.THUNDER -> thunder(pos, target, fx.b() == 1, random);
			case TalismanFxIds.FIRE_CLOUD -> fireCloud(pos, target, random);
			case TalismanFxIds.SHIELD -> shell(pos, height, GOLD, random);
			case TalismanFxIds.BINDING -> binding(pos, height, random);
			case TalismanFxIds.RETURN_CHANNEL -> returnChannel(pos, fx.b(), random);
			case TalismanFxIds.RETURN_FLASH -> returnFlash(pos, random);
			case TalismanFxIds.WARD -> ward(pos, height, random);
			case TalismanFxIds.THOUSAND_MILE -> wind(pos, height, random);
			case TalismanFxIds.LIMPIDITY -> shimmer(pos, height, random);
			case TalismanFxIds.INSCRIBE -> brush(pos, kindColor(fx.b()), random);
			case TalismanFxIds.INSCRIBED -> inscribed(pos, kindColor(fx.b()), random);
			case TalismanFxIds.ASH -> ash(pos, random);
			case TalismanFxIds.SOUL -> soul(pos, height, random);
			case TalismanFxIds.STAR -> star(pos, height, random);
			case TalismanFxIds.BOON -> boon(pos, height, random);
			case TalismanFxIds.REFORGE -> reforge(pos, fx.b(), random);
			case TalismanFxIds.COMPASS -> compass(pos, target, random);
			case TalismanFxIds.FUEL -> fuel(pos, STONE_COLORS[Math.max(0, Math.min(3, fx.b()))], random);
			case TalismanFxIds.ABODE -> abode(pos, fx.f() > 0.0F ? fx.f() : 16.0F, random);
			default -> {
			}
		}
	}

	/** Every client tick (from {@code FxDispatcher.tick}): the brush motes of players inscribing nearby. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused() || (level.getGameTime() & 1L) != 0L) {
			return;
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = level.getRandom();
		for (AbstractClientPlayer player : level.players()) {
			if (!player.isUsingItem() || !player.getUseItem().is(Talismans.BLANK) || player.position().distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			if (!EffectQuality.chance(random, 0.8F)) {
				continue;
			}
			Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.55D)).subtract(0.0D, 0.4D, 0.0D);
			Vec3 at = hand.add((random.nextFloat() - 0.5F) * 0.25D, (random.nextFloat() - 0.5F) * 0.1D, (random.nextFloat() - 0.5F) * 0.25D);
			CommonFx.add(CommonFx.dust(0xC0201C, 0.45F), at, Vec3.ZERO, false);
			if (EffectQuality.maximal() && random.nextInt(3) == 0) {
				CommonFx.add(ParticleTypes.ENCHANT, at.add(0.0D, 0.3D, 0.0D), new Vec3(0.0D, -0.2D, 0.0D), false);
			}
		}
	}

	// ================================================================== the effects

	private static void activate(Vec3 pos, int color, RandomSource random) {
		int n = budget(14);
		DustParticleOptions glyph = CommonFx.dust(color, 0.9F);
		DustParticleOptions paper = CommonFx.dust(PAPER, 0.7F);
		for (int i = 0; i < n; i++) {
			Vec3 at = jitter(pos, 0.25D, random);
			CommonFx.add(i % 3 == 0 ? paper : glyph, at, new Vec3((random.nextFloat() - 0.5F) * 0.04D, 0.04D + random.nextFloat() * 0.05D,
					(random.nextFloat() - 0.5F) * 0.04D), false);
		}
		for (int i = 0; i < budget(4); i++) {
			CommonFx.add(ParticleTypes.SMALL_FLAME, jitter(pos, 0.15D, random), new Vec3(0.0D, 0.02D, 0.0D), false);
		}
		if (EffectQuality.maximal()) {
			CommonFx.add(ParticleTypes.WHITE_ASH, pos, new Vec3(0.0D, 0.03D, 0.0D), false);
		}
	}

	private static void thunder(Vec3 from, Vec3 to, boolean chained, RandomSource random) {
		CommonFx.bolt(from, to, chained ? 0xD6B8FF : 0xB57CFF, chained ? 4 : 7);
		for (int i = 0; i < budget(chained ? 4 : 8); i++) {
			CommonFx.add(ParticleTypes.ELECTRIC_SPARK, jitter(to, 0.4D, random), new Vec3(random.nextGaussian() * 0.15D,
					random.nextFloat() * 0.2D, random.nextGaussian() * 0.15D), false);
		}
	}

	private static void fireCloud(Vec3 from, Vec3 to, RandomSource random) {
		Vec3 axis = to.subtract(from);
		double length = Math.max(0.5D, axis.length());
		Vec3 dir = axis.normalize();
		int n = budget(40);
		for (int i = 0; i < n; i++) {
			Vec3 spread = dir.add(random.nextGaussian() * 0.22D, random.nextGaussian() * 0.12D, random.nextGaussian() * 0.22D).normalize();
			double speed = 0.18D + random.nextFloat() * 0.22D * length / 6.0D;
			ParticleOptions particle = i % 4 == 0 ? CommonFx.dust(0xFF8A3A, 1.4F) : ParticleTypes.FLAME;
			CommonFx.add(particle, from.add(spread.scale(0.4D)), spread.scale(speed), false);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 6; i++) {
				CommonFx.add(ParticleTypes.LARGE_SMOKE, from.add(dir.scale(length * random.nextFloat())), new Vec3(0.0D, 0.02D, 0.0D), false);
			}
		}
	}

	private static void shell(Vec3 feet, float height, int color, RandomSource random) {
		int n = budget(36);
		DustParticleOptions dust = CommonFx.dust(color, 0.8F);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double y = random.nextDouble() * height;
			double r = 0.75D * Math.sin(Math.PI * Math.min(1.0D, (y + 0.2D) / (height + 0.4D))) + 0.25D;
			CommonFx.add(dust, feet.add(Math.cos(angle) * r, y, Math.sin(angle) * r), Vec3.ZERO, false);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 6; i++) {
				CommonFx.add(ParticleTypes.END_ROD, feet.add(0.0D, height * random.nextFloat(), 0.0D),
						new Vec3(random.nextGaussian() * 0.05D, 0.02D, random.nextGaussian() * 0.05D), false);
			}
		}
	}

	private static void binding(Vec3 feet, float height, RandomSource random) {
		int rings = 3;
		DustParticleOptions dust = CommonFx.dust(0x9FF3FF, 0.9F);
		for (int ring = 0; ring < rings; ring++) {
			double y = height * (0.2D + 0.3D * ring);
			int points = budget(12);
			double twist = random.nextDouble() * Math.PI;
			for (int i = 0; i < points; i++) {
				double angle = twist + Math.PI * 2.0D * i / points;
				CommonFx.add(dust, feet.add(Math.cos(angle) * 0.6D, y + Math.sin(angle * 2.0D) * 0.08D, Math.sin(angle) * 0.6D), Vec3.ZERO, false);
			}
		}
		for (int i = 0; i < budget(4); i++) {
			CommonFx.add(ParticleTypes.SNOWFLAKE, jitter(feet.add(0.0D, height * 0.5D, 0.0D), 0.5D, random), Vec3.ZERO, false);
		}
	}

	private static void returnChannel(Vec3 feet, int done, RandomSource random) {
		float progress = Math.max(0.1F, Math.min(1.0F, done / 100.0F));
		int n = budget(Math.round(8 + 16 * progress));
		DustParticleOptions dust = CommonFx.dust(0xFFC94A, 1.0F);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 0.9D - 0.4D * progress;
			Vec3 at = feet.add(Math.cos(angle) * r, random.nextDouble() * 0.3D, Math.sin(angle) * r);
			CommonFx.add(i % 3 == 0 ? ParticleTypes.FLAME : dust, at, new Vec3(0.0D, 0.06D + 0.08D * progress, 0.0D), false);
		}
	}

	private static void returnFlash(Vec3 feet, RandomSource random) {
		CommonFx.pillar(feet, 0xFFC94A, 3.0F);
		int n = budget(30);
		for (int i = 0; i < n; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextFloat() * 1.5D, random.nextGaussian()).normalize();
			CommonFx.add(i % 2 == 0 ? ParticleTypes.FLAME : CommonFx.dust(GOLD, 1.2F), feet.add(0.0D, 1.0D, 0.0D), dir.scale(0.2D), false);
		}
	}

	private static void ward(Vec3 feet, float height, RandomSource random) {
		Vec3 top = feet.add(0.0D, height + 0.5D, 0.0D);
		DustParticleOptions dust = CommonFx.dust(0x6CB8FF, 1.0F);
		int n = budget(16);
		for (int i = 0; i < n; i++) {
			// A small upright slip of light above the head.
			Vec3 at = top.add((random.nextFloat() - 0.5F) * 0.3D, (random.nextFloat() - 0.5F) * 0.7D, 0.0D);
			CommonFx.add(dust, at, Vec3.ZERO, false);
		}
		CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), 0x6CB8FF, 1.2F, 24);
		if (EffectQuality.maximal()) {
			CommonFx.add(ParticleTypes.END_ROD, top, new Vec3(0.0D, 0.05D, 0.0D), false);
		}
	}

	private static void wind(Vec3 feet, float height, RandomSource random) {
		int n = budget(18);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 at = feet.add(Math.cos(angle) * 0.8D, random.nextDouble() * height * 0.7D, Math.sin(angle) * 0.8D);
			Vec3 tangent = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)).scale(0.2D);
			CommonFx.add(i % 2 == 0 ? ParticleTypes.CLOUD : CommonFx.dust(0xB6FFB0, 0.7F), at, tangent, false);
		}
	}

	private static void shimmer(Vec3 feet, float height, RandomSource random) {
		int n = budget(24);
		DustParticleOptions dust = CommonFx.dust(0xC8B8FF, 0.6F);
		for (int i = 0; i < n; i++) {
			Vec3 at = feet.add((random.nextFloat() - 0.5F) * 0.8D, random.nextDouble() * height, (random.nextFloat() - 0.5F) * 0.8D);
			CommonFx.add(i % 4 == 0 ? ParticleTypes.REVERSE_PORTAL : dust, at, new Vec3(0.0D, 0.01D, 0.0D), false);
		}
	}

	private static void brush(Vec3 hand, int color, RandomSource random) {
		int n = budget(6);
		DustParticleOptions ink = CommonFx.dust(color, 0.5F);
		for (int i = 0; i < n; i++) {
			CommonFx.add(i % 2 == 0 ? ink : CommonFx.dust(0xC0201C, 0.4F), jitter(hand, 0.18D, random), Vec3.ZERO, false);
		}
		CommonFx.add(ParticleTypes.ENCHANT, hand.add(0.0D, 0.4D, 0.0D), new Vec3(0.0D, -0.3D, 0.0D), false);
	}

	private static void inscribed(Vec3 hand, int color, RandomSource random) {
		CommonFx.burst(hand, color, 16, 1.2F);
		for (int i = 0; i < budget(8); i++) {
			CommonFx.add(ParticleTypes.ENCHANT, jitter(hand, 0.6D, random), new Vec3(0.0D, -0.4D, 0.0D), false);
		}
	}

	private static void ash(Vec3 hand, RandomSource random) {
		for (int i = 0; i < budget(12); i++) {
			CommonFx.add(i % 3 == 0 ? ParticleTypes.SMOKE : ParticleTypes.ASH, jitter(hand, 0.25D, random),
					new Vec3((random.nextFloat() - 0.5F) * 0.03D, 0.03D, (random.nextFloat() - 0.5F) * 0.03D), false);
		}
		CommonFx.add(ParticleTypes.SMALL_FLAME, hand, Vec3.ZERO, false);
	}

	private static void soul(Vec3 feet, float height, RandomSource random) {
		Vec3 center = feet.add(0.0D, height * 0.55D, 0.0D);
		int n = budget(50);
		DustParticleOptions gold = CommonFx.dust(GOLD, 1.3F);
		for (int i = 0; i < n; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			CommonFx.add(gold, center.add(dir.scale(0.2D)), dir.scale(0.25D + random.nextFloat() * 0.2D), true);
		}
		CommonFx.spiralIn(center, GOLD, 1.6F, 24, 0.0F);
		for (int i = 0; i < budget(12); i++) {
			CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, center, new Vec3(random.nextGaussian() * 0.3D, random.nextFloat() * 0.4D,
					random.nextGaussian() * 0.3D), false);
		}
	}

	private static void star(Vec3 feet, float height, RandomSource random) {
		Vec3 top = feet.add(0.0D, height + 0.8D, 0.0D);
		CommonFx.spiralIn(top, GOLD, 1.4F, 30, random.nextFloat() * 10.0F);
		int points = budget(10);
		DustParticleOptions dust = CommonFx.dust(0xFFF2B0, 1.0F);
		for (int i = 0; i < points; i++) {
			// A five-pointed star outline.
			double angle = Math.PI * 2.0D * i / points - Math.PI / 2.0D;
			double r = i % 2 == 0 ? 0.5D : 0.22D;
			CommonFx.add(dust, top.add(Math.cos(angle) * r, Math.sin(angle) * r, 0.0D), Vec3.ZERO, true);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 8; i++) {
				CommonFx.add(ParticleTypes.END_ROD, top, new Vec3(random.nextGaussian() * 0.08D, random.nextGaussian() * 0.08D,
						random.nextGaussian() * 0.08D), false);
			}
		}
	}

	private static void boon(Vec3 feet, float height, RandomSource random) {
		int lines = Math.max(4, EffectQuality.scale(8));
		for (int i = 0; i < lines; i++) {
			double angle = Math.PI * 2.0D * i / lines + random.nextDouble() * 0.2D;
			Vec3 low = feet.add(Math.cos(angle) * 0.35D, 0.1D, Math.sin(angle) * 0.35D);
			Vec3 high = feet.add(Math.cos(angle + 0.6D) * 0.25D, height, Math.sin(angle + 0.6D) * 0.25D);
			CommonFx.beam(low, high, i % 2 == 0 ? GOLD : 0xFFF2B0, 0.04F);
		}
		CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), GOLD, 1.4F, 32);
	}

	private static void reforge(Vec3 feet, int quality, RandomSource random) {
		if (quality < 0) {
			for (int i = 0; i < budget(16); i++) {
				CommonFx.add(ParticleTypes.LARGE_SMOKE, jitter(feet.add(0.0D, 1.0D, 0.0D), 0.5D, random), new Vec3(0.0D, 0.04D, 0.0D), false);
			}
			return;
		}
		BreakthroughQuality value = BreakthroughQuality.byOrdinal(quality);
		int color = value == null ? GOLD : value.color();
		CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), color, 1.6F, 40);
		CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), color, 0.9F, 24);
		CommonFx.pillar(feet, color, 3.5F);
		CommonFx.burst(feet.add(0.0D, 1.0D, 0.0D), color, 24, 1.6F);
	}

	private static void compass(Vec3 from, Vec3 to, RandomSource random) {
		CommonFx.beam(from, to, JADE, 0.05F);
		for (int i = 0; i < budget(6); i++) {
			CommonFx.add(ParticleTypes.GLOW, jitter(to, 0.3D, random), Vec3.ZERO, false);
		}
	}

	private static void fuel(Vec3 center, int color, RandomSource random) {
		CommonFx.spiralIn(center, color, 0.8F, 10, random.nextFloat() * 10.0F);
		if (EffectQuality.maximal()) {
			CommonFx.add(ParticleTypes.END_ROD, center, new Vec3(0.0D, 0.03D, 0.0D), false);
		}
	}

	private static void abode(Vec3 seat, float radius, RandomSource random) {
		CommonFx.ring(seat.add(0.0D, 0.05D, 0.0D), JADE, 1.5F, 32);
		CommonFx.ring(seat.add(0.0D, 0.05D, 0.0D), JADE, Math.min(radius, 16.0F), 96);
		CommonFx.pillar(seat, JADE, 2.5F);
	}

	// ================================================================== helpers

	/** Particles at Normal quality, scaled by Effect Quality and paid from the client particle budget. */
	private static int budget(int count) {
		return FxBudget.take(EffectQuality.scale(count), FxBudget.Priority.DETAIL);
	}

	private static Vec3 jitter(Vec3 pos, double spread, RandomSource random) {
		return pos.add((random.nextFloat() - 0.5F) * 2.0F * spread, (random.nextFloat() - 0.5F) * 2.0F * spread,
				(random.nextFloat() - 0.5F) * 2.0F * spread);
	}

	private static int kindColor(int ordinal) {
		TalismanKind[] kinds = TalismanKind.values();
		return ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal].color() : PAPER;
	}

	private TalismanFx() {
	}
}
