package dev.kultivator.client.abadi;

import dev.kultivator.abadi.AbadiFxIds;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The ABADI effects (DESIGN 26.14 item 3, owned by P9-langit; sub-ids in {@code abadi.AbadiFxIds}), routed by
 * {@code FxDispatcher}: the ascension omen (a dawn sky grade through {@code SkyDirector} and a five-coloured cloud disc), the
 * client bolts of the nine strikes (never a {@code LightningBolt}), the black hole ({@link VortexRenderer}), the passing burst,
 * the crossing talisman's channel and arrival, the soft border and the void catch. Particles and the sky grade only, scaled by
 * Effect Quality.
 */
public final class AbadiFx {

	private static final int SKY_PRIORITY = 3;
	private static final int[] FIVE = {0xFF5A5A, 0xFFD54A, 0x5CCB4A, 0x47B2FF, 0xB98CFF};
	private static final int GOLD = 0xFFE9A8;
	private static final int JADE = 0x7FE0B0;

	private static long ticks;
	private static long omenUntil;

	/** One {@code FxType.ABADI} packet; {@code a} = the sub-id. */
	public static void play(Payloads.Fx fx) {
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		RandomSource random = CommonFx.random();
		Minecraft minecraft = Minecraft.getInstance();
		boolean own = minecraft.player != null && minecraft.player.position().distanceToSqr(pos) < 9.0D;
		switch (fx.a()) {
			case AbadiFxIds.OMEN -> {
				SkyDirector.grade(SkyDirector.Grade.SK_DAWN, 0.75F, 0.15F, 30, SKY_PRIORITY, pos, own, 0.6F, pos.y);
				omenUntil = ticks + 20L * 120L;
				int points = EffectQuality.scale(30);
				for (int i = 0; i < points; i++) {
					double angle = Math.PI * 2.0D * i / points + ticks * 0.02D;
					double r = 3.0D + random.nextDouble() * 6.0D;
					CommonFx.add(CommonFx.dust(FIVE[i % FIVE.length], 2.0F), target.x + Math.cos(angle) * r, target.y + random.nextGaussian() * 0.3D,
							target.z + Math.sin(angle) * r, 0.0D, 0.0D, 0.0D);
				}
				for (int i = 0; i < EffectQuality.scale(8); i++) {
					CommonFx.add(ParticleTypes.CLOUD, target.x + random.nextGaussian() * 4.0D, target.y, target.z + random.nextGaussian() * 4.0D, 0.0D, 0.0D, 0.0D);
				}
			}
			case AbadiFxIds.STRIKE -> {
				int rgb = FIVE[Math.floorMod(fx.b(), FIVE.length)];
				CommonFx.bolt(target, pos, rgb, 12);
				CommonFx.bolt(target.add(random.nextGaussian(), 0.0D, random.nextGaussian()), pos, 0xFFFFFF, 8);
				CommonFx.burst(pos.add(0.0D, 0.5D, 0.0D), rgb, 24, 1.5F);
				if (own) {
					ScreenFlash.add(0xFFFFFF, Math.min(ScreenFlash.MAX_ALPHA, 0.15F * fx.f()), 4);
				}
			}
			case AbadiFxIds.VORTEX -> VortexRenderer.start(target, pos, fx.f(), fx.b() + 40);
			case AbadiFxIds.STOP -> {
				VortexRenderer.stop();
				omenUntil = 0L;
				SkyDirector.release(fx.b() == 1 ? 60 : 30, SKY_PRIORITY);
				if (fx.b() == 0) {
					CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0x6A2AA8, 30, 2.0F);
				}
			}
			case AbadiFxIds.PASS -> {
				CommonFx.pillar(pos, GOLD, 24.0F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 4.0F, 48);
				for (int i = 0; i < EffectQuality.scale(30); i++) {
					CommonFx.add(ParticleTypes.END_ROD, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.2D, 0.2D + random.nextDouble() * 0.3D,
							random.nextGaussian() * 0.2D);
				}
				if (own) {
					ScreenFlash.add(GOLD, ScreenFlash.MAX_ALPHA, ScreenFlash.MAX_TICKS);
				}
			}
			case AbadiFxIds.CHANNEL -> {
				CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), JADE, 2.5F, 14, ticks);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), JADE, 1.2F, 20);
			}
			case AbadiFxIds.CROSS -> {
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), JADE, 30, 1.8F);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 2.0F, 32);
				if (own) {
					ScreenFlash.add(JADE, 0.25F, 6);
				}
			}
			case AbadiFxIds.BORDER -> {
				Vec3 dir = target.subtract(pos);
				for (int i = 0; i < EffectQuality.scale(16); i++) {
					double side = (random.nextDouble() - 0.5D) * 6.0D;
					Vec3 at = pos.add(-dir.z * side * 0.25D, random.nextDouble() * 3.0D, dir.x * side * 0.25D).subtract(dir.scale(0.4D));
					CommonFx.add(CommonFx.dust(0xBFE8FF, 1.0F), at.x, at.y, at.z, dir.x * 0.05D, 0.0D, dir.z * 0.05D);
				}
			}
			case AbadiFxIds.VOID -> {
				for (int i = 0; i < EffectQuality.scale(20); i++) {
					CommonFx.add(ParticleTypes.CLOUD, pos.x + random.nextGaussian() * 0.6D, pos.y + random.nextDouble(), pos.z + random.nextGaussian() * 0.6D,
							0.0D, 0.05D, 0.0D);
				}
				if (own) {
					ScreenFlash.add(0xE8F0FF, 0.3F, 6);
				}
			}
			default -> {
			}
		}
	}

	/** Every client tick (the vortex; the omen grade's safety release). */
	public static void tick() {
		ticks++;
		VortexRenderer.tick();
		if (omenUntil > 0L && ticks > omenUntil) {
			omenUntil = 0L;
			SkyDirector.release(40, SKY_PRIORITY);
		}
	}

	/** Disconnect: forget everything. */
	static void clear() {
		VortexRenderer.stop();
		omenUntil = 0L;
	}

	private AbadiFx() {
	}
}
