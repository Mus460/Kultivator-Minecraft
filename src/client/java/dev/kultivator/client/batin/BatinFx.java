package dev.kultivator.client.batin;

import dev.kultivator.batin.BatinFxIds;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.network.Payloads;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The BATIN effects (DESIGN 26.14 item 3; sub-ids in {@code batin.BatinFxIds}), routed by {@code FxDispatcher}: the inner
 * world's birth, the earthly calamities (meteor rings and bursts, the Gunung Dang Hun glyph ring, lightning visuals that are
 * never a LightningBolt, the Tanggul Qi circle), the Pencerahan Ciptaan and the self-created arts ({@link CiptaanFx}).
 * Particles only, every count through Effect Quality, at most {@link #MAX} running animations.
 */
public final class BatinFx {

	private static final int MAX = 24;
	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int FIRE = 0xFF6A2A;
	private static final int WATER = 0x5FB8FF;
	private static final int BOLT = 0x9FD0FF;
	private static final int GLYPH = 0xE8F4FF;
	private static final List<Anim> ANIMS = new ArrayList<>();

	private static final class Anim {
		final int kind;
		final Vec3 pos;
		final int life;
		final float size;
		final int colorA;
		final int colorB;
		int age;

		Anim(int kind, Vec3 pos, int life, float size, int colorA, int colorB) {
			this.kind = kind;
			this.pos = pos;
			this.life = life;
			this.size = size;
			this.colorA = colorA;
			this.colorB = colorB;
		}
	}

	/** One {@code FxType.BATIN} packet; {@code a} = the sub-id. */
	public static void play(Payloads.Fx fx) {
		if (CommonFx.level() == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		RandomSource random = CommonFx.random();
		switch (fx.a()) {
			case BatinFxIds.BORN -> {
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), JADE, 2.5F, 48);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 4.0F, 64);
				CommonFx.pillar(pos, JADE, 6.0F);
				add(new Anim(BatinFxIds.BORN, pos, 50, 3.0F, JADE, GOLD));
			}
			case BatinFxIds.METEOR_MARK -> {
				CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), FIRE, 1.8F, 28);
				add(new Anim(BatinFxIds.METEOR_MARK, pos, Math.max(10, Math.round(fx.f() * 20.0F)), 1.8F, FIRE, 0xFFD27A));
			}
			case BatinFxIds.METEOR_BURST -> {
				CommonFx.burst(pos.add(0.0D, 0.5D, 0.0D), FIRE, 40, fx.f());
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), 0xFFB040, fx.f(), 36);
				for (int i = 0; i < EffectQuality.scale(24); i++) {
					CommonFx.add(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, pos.x, pos.y + 0.3D, pos.z, random.nextGaussian() * 0.25D,
							0.2D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
				}
				for (int i = 0; i < EffectQuality.scale(8); i++) {
					CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.x + random.nextGaussian(), pos.y + 0.5D, pos.z + random.nextGaussian(), 0.0D, 0.06D, 0.0D);
				}
			}
			case BatinFxIds.DANG_HUN -> {
				float radius = Math.max(1.0F, fx.f());
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GLYPH, radius, 56);
				CommonFx.ring(pos.add(0.0D, 0.15D, 0.0D), 0x6A8CFF, radius - 0.6F, 40);
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * 2.0D * i / 8 + (CommonFx.level().getGameTime() % 80L) / 80.0D * Math.PI * 0.25D;
					CommonFx.pillar(pos.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius), GLYPH, 1.6F);
				}
			}
			case BatinFxIds.BOLT -> {
				Vec3 from = pos.add(random.nextGaussian() * 3.0D, 14.0D + random.nextDouble() * 4.0D, random.nextGaussian() * 3.0D);
				Vec3 to = pos.add(random.nextGaussian() * 2.0D, 0.2D, random.nextGaussian() * 2.0D);
				CommonFx.bolt(from, to, BOLT, 9);
			}
			case BatinFxIds.TANGGUL -> {
				float radius = Math.max(1.0F, fx.f());
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), WATER, radius, 48);
				int filled = Math.max(0, Math.min(100, fx.b()));
				int points = Math.max(1, EffectQuality.scale(40) * filled / 100);
				for (int i = 0; i < points; i++) {
					double angle = Math.PI * 2.0D * i / Math.max(1, EffectQuality.scale(40));
					CommonFx.add(CommonFx.dust(GOLD, 1.2F), pos.x + Math.cos(angle) * (radius - 0.4D), pos.y + 0.4D, pos.z + Math.sin(angle) * (radius - 0.4D),
							0.0D, 0.02D, 0.0D);
				}
				for (int i = 0; i < EffectQuality.scale(10); i++) {
					CommonFx.add(ParticleTypes.SPLASH, pos.x + random.nextGaussian() * radius * 0.6D, pos.y + 0.2D, pos.z + random.nextGaussian() * radius * 0.6D,
							0.0D, 0.1D, 0.0D);
				}
			}
			case BatinFxIds.CALAMITY_WIN -> {
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 3.0F, 56);
				for (int i = 0; i < EffectQuality.scale(20); i++) {
					CommonFx.add(ParticleTypes.HAPPY_VILLAGER, pos.x + random.nextGaussian() * 1.2D, pos.y + random.nextDouble() * 2.0D,
							pos.z + random.nextGaussian() * 1.2D, 0.0D, 0.04D, 0.0D);
				}
			}
			case BatinFxIds.RITUAL -> {
				int progress = fx.b() & 0xFF;
				int colorA = CiptaanFx.daoColor((fx.b() >> 8) & 0xF);
				int colorB = CiptaanFx.daoColor((fx.b() >> 12) & 0xF);
				Vec3 center = pos.add(0.0D, 1.0D, 0.0D);
				CommonFx.spiralIn(center, colorA, 2.4F, 10, progress);
				CommonFx.spiralIn(center, colorB, 1.8F, 8, progress + 3);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), progress % 2 == 0 ? colorA : colorB, 1.0F + progress / 40.0F, 24);
			}
			case BatinFxIds.ART_BORN -> {
				int colorA = CiptaanFx.daoColor((fx.b() >> 4) & 0xF);
				int colorB = CiptaanFx.daoColor((fx.b() >> 8) & 0xF);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), colorA, 3.0F, 56);
				CommonFx.ring(pos.add(0.0D, 0.3D, 0.0D), colorB, 2.2F, 44);
				CommonFx.pillar(pos, GOLD, 8.0F);
				add(new Anim(BatinFxIds.BORN, pos, 40, 2.5F, colorA, colorB));
			}
			case BatinFxIds.OWN_ART -> CiptaanFx.play(fx);
			case BatinFxIds.PLOT -> {
				for (int i = 0; i < EffectQuality.scale(10); i++) {
					CommonFx.add(ParticleTypes.HAPPY_VILLAGER, pos.x + random.nextGaussian() * 0.5D, pos.y + 0.8D + random.nextDouble(),
							pos.z + random.nextGaussian() * 0.5D, 0.0D, 0.03D, 0.0D);
				}
			}
			default -> {
			}
		}
	}

	private static void add(Anim anim) {
		if (ANIMS.size() < MAX) {
			ANIMS.add(anim);
		}
	}

	/** Every client tick (continuous visuals). */
	public static void tick() {
		CiptaanFx.tick();
		if (ANIMS.isEmpty()) {
			return;
		}
		if (CommonFx.level() == null) {
			ANIMS.clear();
			return;
		}
		for (Iterator<Anim> it = ANIMS.iterator(); it.hasNext(); ) {
			Anim anim = it.next();
			anim.age++;
			if (anim.kind == BatinFxIds.BORN && anim.age % 5 == 0) {
				float t = anim.age / (float) anim.life;
				CommonFx.ring(anim.pos.add(0.0D, 0.2D + t * 4.0D, 0.0D), anim.age % 10 == 0 ? anim.colorA : anim.colorB, anim.size * (1.0F - t * 0.5F), 28);
			} else if (anim.kind == BatinFxIds.METEOR_MARK && anim.age % 8 == 0) {
				float t = anim.age / (float) anim.life;
				CommonFx.ring(anim.pos.add(0.0D, 0.05D, 0.0D), t > 0.75F ? anim.colorB : anim.colorA, anim.size * (1.0F + t), 24);
			}
			if (anim.age >= anim.life) {
				it.remove();
			}
		}
	}

	private BatinFx() {
	}
}
