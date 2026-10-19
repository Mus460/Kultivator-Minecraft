package dev.kultivator.client.tempa;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.tempa.Heat;
import dev.kultivator.tempa.NatalTreasureSelf;
import dev.kultivator.tempa.TempaFxIds;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Forge, natal treasure, weapon spirit and ring visuals (DESIGN 25.2 item 4, owned by P8-tempa); sub-ids in
 * {@code tempa.TempaFxIds}. All particles, scaled by Effect Quality; the only continuous effect is the glow of awakened
 * natal treasures (players within 32 blocks, at most 8, a few motes a second, none at Minimal).
 */
public final class TempaFx {

	private static final int GOLD = 0xFFD54A;
	private static final int SMOKE = 0x6A6A6A;
	private static final int BRONZE = 0xC08A3E;
	private static final int SPIRIT = 0xD8B0FF;
	private static final double GLOW_RANGE = 32.0D;
	private static final int GLOW_MAX = 8;

	/** One {@code FxType} event of this module. */
	public static void play(Payloads.Fx fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		switch (fx.a()) {
			case TempaFxIds.FORGE_DONE -> {
				if (fx.b() == 1) {
					CommonFx.burst(pos, GOLD, 18, 1.2F);
					for (int i = 0; i < EffectQuality.scale(8); i++) {
						CommonFx.add(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
					}
				} else {
					CommonFx.burst(pos, SMOKE, 12, 0.8F);
					for (int i = 0; i < EffectQuality.scale(10); i++) {
						CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.x + random.nextGaussian() * 0.2D, pos.y, pos.z + random.nextGaussian() * 0.2D,
								0.0D, 0.05D, 0.0D);
					}
				}
			}
			case TempaFxIds.SPIRIT_STRIKE -> {
				CommonFx.beam(pos, target, SPIRIT, 0.12F);
				CommonFx.burst(target, 0xF4F8FF, 6, 0.5F);
				for (int i = 0; i < EffectQuality.scale(4); i++) {
					CommonFx.add(ParticleTypes.SWEEP_ATTACK, target.x, target.y, target.z, 0.0D, 0.0D, 0.0D);
				}
			}
			case TempaFxIds.PAYUNG_SHELL -> {
				float radius = Math.max(1.0F, fx.f());
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0x9AE6FF, radius, 24);
				CommonFx.ring(pos.add(0.0D, 1.4D, 0.0D), 0xC8F2FF, radius * 0.7F, 16);
				CommonFx.ring(pos.add(0.0D, 2.4D, 0.0D), 0xFFFFFF, radius * 0.35F, 10);
			}
			case TempaFxIds.WAJAN_BLOCK -> {
				CommonFx.burst(pos, BRONZE, 14, 1.0F);
				CommonFx.ring(pos, GOLD, 0.9F, 14);
			}
			case TempaFxIds.BELL_CHIME -> {
				float radius = Math.max(1.0F, fx.f());
				CommonFx.ring(pos.add(0.0D, 0.2D, 0.0D), GOLD, radius, 32);
				CommonFx.ring(pos.add(0.0D, 0.2D, 0.0D), 0xFFF2B0, radius * 0.5F, 16);
				for (int i = 0; i < EffectQuality.scale(4); i++) {
					CommonFx.add(ParticleTypes.NOTE, pos.x + random.nextGaussian(), pos.y + 1.5D, pos.z + random.nextGaussian(), random.nextFloat(), 0.0D, 0.0D);
				}
			}
			case TempaFxIds.NATAL_BOND -> {
				int rgb = Math.round(fx.f()) & 0xFFFFFF;
				CommonFx.spiralIn(pos, rgb == 0 ? GOLD : rgb, 2.0F, 36, level.getGameTime() * 0.05F);
				CommonFx.pillar(pos.add(0.0D, -1.0D, 0.0D), GOLD, 2.5F);
			}
			case TempaFxIds.REFINE_DONE -> {
				int rgb = Math.round(fx.f()) & 0xFFFFFF;
				if (fx.b() == 1) {
					CommonFx.burst(pos, rgb == 0 ? GOLD : rgb, 16, 1.0F);
				} else {
					CommonFx.burst(pos, SMOKE, 10, 0.6F);
				}
			}
			case TempaFxIds.RING_BOND -> {
				CommonFx.ring(pos, 0xB07CFF, 0.8F, 16);
				CommonFx.spiralIn(pos, 0xD8C8FF, 1.2F, 14, level.getGameTime() * 0.05F);
			}
			case TempaFxIds.FORGE_WORK -> {
				int heat = Math.max(Heat.INNATE, Math.min(Heat.HEAVENLY, fx.b()));
				boolean soul = heat == Heat.HEAVENLY || heat == Heat.INNATE;
				for (int i = 0; i < EffectQuality.scale(3 + heat); i++) {
					CommonFx.add(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, pos.x + (random.nextFloat() - 0.5F) * 0.6D, pos.y,
							pos.z + (random.nextFloat() - 0.5F) * 0.6D, 0.0D, 0.02D + random.nextFloat() * 0.03D, 0.0D);
				}
				if (EffectQuality.maximal()) {
					CommonFx.burst(pos, Heat.COLORS[heat], 3, 0.4F);
				}
			}
			case TempaFxIds.KUALI_OPEN -> {
				CommonFx.pillar(pos, 0x9FE8FF, 4.0F);
				CommonFx.burst(pos, 0xE8FBFF, 24, 1.5F);
				CommonFx.ring(pos, 0x6FD3FF, 1.6F, 24);
			}
			case TempaFxIds.SPIRIT_AWAKEN -> {
				int rgb = Math.round(fx.f()) & 0xFFFFFF;
				CommonFx.pillar(pos.add(0.0D, -1.0D, 0.0D), rgb == 0 ? SPIRIT : rgb, 3.0F + fx.b());
				CommonFx.ring(pos, SPIRIT, 1.2F + 0.4F * fx.b(), 20);
			}
			default -> {
			}
		}
	}

	/** Every client tick: the glow of awakened natal treasures (spirit stage 1+). */
	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || EffectQuality.minimal() || level.getGameTime() % 10L != 0L) {
			return;
		}
		RandomSource random = level.getRandom();
		int shown = 0;
		for (Player player : level.players()) {
			if (shown >= GLOW_MAX) {
				break;
			}
			if (player.isInvisible() || player.distanceToSqr(minecraft.player) > GLOW_RANGE * GLOW_RANGE) {
				continue;
			}
			Integer look = player.getAttached(NatalTreasureSelf.LOOK);
			if (look == null || look == 0 || (look >> 12 & 0xF) < 1) {
				continue;
			}
			shown++;
			int stage = look >> 12 & 0xF;
			Vec3 back = player.position().add(0.0D, 1.3D, 0.0D).subtract(player.getLookAngle().multiply(0.4D, 0.0D, 0.4D));
			for (int i = 0; i < stage; i++) {
				CommonFx.add(CommonFx.dust(SPIRIT, 0.7F), back.add(random.nextGaussian() * 0.25D, random.nextGaussian() * 0.25D, random.nextGaussian() * 0.25D),
						new Vec3(0.0D, 0.01D, 0.0D), false);
			}
		}
	}

	private TempaFx() {
	}
}
