package dev.kultivator.client.sekte.kebun;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.sekte.kebun.KebunPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Module W's one-shot moments ({@code KebunPayloads.Fx}, DESIGN 36.2.10 "Visual"), all {@code kultivator:qi} particles
 * sized by {@code EffectQuality} (Minimal: a few motes, no rings; Normal; Maximal: twice the motes and a second ring) and a
 * client sound only where the server plays none:
 * <ul>
 * <li>harvest: the herb's colour lifts off the bed in a puff of motes and leaf glints;</li>
 * <li>sowing: a ground ring in the seed's colour;</li>
 * <li>a rare bloom: a rising column of light motes, two rings and a soft chime;</li>
 * <li>the shift's haul: gold dust bursting from the mouth;</li>
 * <li>a cave-in: a cloud of grey dust rolling out of the adit (only dust: no block changes, no one dies);</li>
 * <li>the Inti Roh Gunung: Nüwa's five colours whirling round a gold flash;</li>
 * <li>a deeper level: a gold ground ring at the mouth.</li>
 * </ul>
 */
final class KebunFx {

	private static final int[] FIVE = {0x5AAAFF, 0xE8403A, 0xFFCE48, 0xF4F4EC, 0x2F7A5E};

	static void on(KebunPayloads.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || player.distanceToSqr(fx.x(), fx.y(), fx.z()) > 64.0D * 64.0D) {
			return;
		}
		EffectQuality.Level quality = EffectQuality.level();
		int mul = quality == EffectQuality.Level.MAXIMAL ? 2 : 1;
		boolean minimal = quality == EffectQuality.Level.MINIMAL;
		boolean qi = Qi.on();
		RandomSource r = level.getRandom();
		double x = fx.x();
		double y = fx.y();
		double z = fx.z();
		switch (fx.kind()) {
			case KebunPayloads.FX_HARVEST -> {
				int rgb = fx.arg() == 0 ? 0x8CFFB0 : fx.arg();
				if (qi) {
					for (int i = 0; i < (minimal ? 4 : 10 * mul); i++) {
						level.addParticle(Qi.mote(rgb, 0.12F + r.nextFloat() * 0.08F, 24 + r.nextInt(14)), x + gauss(r, 0.3D), y + 0.3D + r.nextDouble() * 0.4D,
								z + gauss(r, 0.3D), gauss(r, 0.03D), 0.05D + r.nextDouble() * 0.05D, gauss(r, 0.03D));
					}
					if (!minimal) {
						for (int i = 0; i < 4 * mul; i++) {
							level.addParticle(Qi.petal(0x7BD36A, 0.1F, 40), x + gauss(r, 0.25D), y + 0.6D, z + gauss(r, 0.25D), gauss(r, 0.04D), 0.06D,
									gauss(r, 0.04D));
						}
						level.addParticle(Qi.groundRing(rgb, 0.4F, 1.4F, 18), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
					}
				}
				level.playLocalSound(x, y, z, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.4F + r.nextFloat() * 0.3F, false);
			}
			case KebunPayloads.FX_PLANT -> {
				int rgb = fx.arg() == 0 ? 0x8CFFB0 : fx.arg();
				if (qi) {
					if (!minimal) {
						level.addParticle(Qi.groundRing(rgb, 0.3F, 1.1F, 16), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
					}
					for (int i = 0; i < (minimal ? 2 : 5 * mul); i++) {
						level.addParticle(Qi.glint(rgb, 0.08F, 18), x + gauss(r, 0.25D), y + 0.15D, z + gauss(r, 0.25D), 0.0D, 0.03D, 0.0D);
					}
				}
			}
			case KebunPayloads.FX_BLOOM -> {
				int rgb = fx.arg() == 0 ? 0xFFF2B0 : fx.arg();
				if (qi) {
					for (int i = 0; i < (minimal ? 6 : 18 * mul); i++) {
						double h = r.nextDouble();
						level.addParticle(Qi.mote(i % 3 == 0 ? 0xFFFFFF : rgb, 0.1F + r.nextFloat() * 0.1F, 30 + r.nextInt(20)), x + gauss(r, 0.15D),
								y + 0.2D + h * 0.6D, z + gauss(r, 0.15D), gauss(r, 0.01D), 0.08D + h * 0.08D, gauss(r, 0.01D));
					}
					if (!minimal) {
						level.addParticle(Qi.groundRing(rgb, 0.5F, 2.4F, 26), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
						level.addParticle(Qi.ring(0xFFFFFF, 0.3F, 1.6F, 20), x, y + 0.9D, z, 0.0D, 0.0D, 0.0D);
						if (mul > 1) {
							level.addParticle(Qi.groundRing(0xFFFFFF, 0.3F, 3.2F, 34), x, y + 0.06D, z, 0.0D, 0.0D, 0.0D);
						}
					}
				}
				level.playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F, false);
			}
			case KebunPayloads.FX_HAUL -> {
				if (qi) {
					int n = Math.min(minimal ? 4 : 16 * mul, 2 + Math.max(0, fx.arg()) / 2);
					for (int i = 0; i < n; i++) {
						level.addParticle(Qi.glint(i % 4 == 0 ? 0xFFFFFF : 0xFFC447, 0.08F + r.nextFloat() * 0.06F, 26 + r.nextInt(12)), x + gauss(r, 0.4D),
								y + 0.6D + r.nextDouble() * 0.8D, z + gauss(r, 0.4D), gauss(r, 0.05D), 0.03D + r.nextDouble() * 0.05D, gauss(r, 0.05D));
					}
				}
				level.playLocalSound(x, y, z, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F, false);
			}
			case KebunPayloads.FX_CAVE_IN -> {
				if (qi) {
					for (int i = 0; i < (minimal ? 6 : 16 * mul); i++) {
						int grey = i % 3 == 0 ? 0x8A857C : 0x6B675F;
						level.addParticle(Qi.smoke(grey, 0.5F + r.nextFloat() * 0.5F, 50 + r.nextInt(30)), x + gauss(r, 0.6D), y + 0.4D + r.nextDouble(),
								z + gauss(r, 0.6D), gauss(r, 0.06D), 0.02D + r.nextDouble() * 0.03D, gauss(r, 0.06D));
					}
				}
			}
			case KebunPayloads.FX_INTI -> {
				if (qi) {
					int n = minimal ? 5 : 15 * mul;
					for (int i = 0; i < n; i++) {
						double a = Math.PI * 2.0D * i / n;
						int rgb = FIVE[i % FIVE.length];
						level.addParticle(Qi.mote(rgb, 0.14F, 36), x + Math.cos(a) * 0.6D, y + 1.0D, z + Math.sin(a) * 0.6D, -Math.sin(a) * 0.08D, 0.04D,
								Math.cos(a) * 0.08D);
					}
					if (!minimal) {
						level.addParticle(Qi.ring(fx.arg() == 0 ? 0xFFD27A : fx.arg(), 0.4F, 2.6F, 24), x, y + 1.0D, z, 0.0D, 0.0D, 0.0D);
						level.addParticle(Qi.groundRing(0xFFD27A, 0.6F, 3.0F, 30), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
					}
				}
			}
			case KebunPayloads.FX_DEEPEN -> {
				if (qi && !minimal) {
					level.addParticle(Qi.groundRing(0xFFC447, 0.6F, 2.8F, 30), x, y + 0.05D, z, 0.0D, 0.0D, 0.0D);
					for (int i = 0; i < 8 * mul; i++) {
						level.addParticle(Qi.smoke(0x7A746A, 0.4F, 40), x + gauss(r, 0.5D), y + 0.5D, z + gauss(r, 0.5D), 0.0D, 0.02D, 0.0D);
					}
				}
			}
			default -> {
			}
		}
	}

	private static double gauss(RandomSource r, double spread) {
		return r.nextGaussian() * spread;
	}

	private KebunFx() {
	}
}
