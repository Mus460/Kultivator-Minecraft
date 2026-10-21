package dev.kultivator.client.dunia.dimensi;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.DimensiNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Module D's client effects (DESIGN 34.5.13-14 "Visuals"): every one is particles of the mod's {@code kultivator:qi}
 * family or vanilla particles, scaled by {@code EffectQuality} (Minimal / Normal / Maximal) and thinned for other
 * players' moments ({@code othersScale}); no post chain, no shader, so Sodium and Iris stay happy.
 * <ul>
 * <li>Dunia Bawah ambience: floating paper-money motes (4 / 10 / 20 per s), soul wisps (6 / 16 / 32 per s) within
 * 24 b, the pale Wangchuan glow on water near the player, and a wisp trail behind every walking soul.</li>
 * <li>Laut Tanpa Batas ambience: sea mist on the horizon ring (2 / 5 / 10 per s) and spray around swimmers.</li>
 * <li>Moments: the soul leaving and returning, arrivals (a 2 s pull-back shot in Penuh through the spine's
 * {@link DuniaScenes}, beat 44, with its card; Singkat: the card; Mati: nothing), a court passed, Meng Po's soup, the
 * Book, herbs, the whirl, blessings and the serpent's spout.</li>
 * </ul>
 */
public final class DimensiFx {

	public static final int SOUL = 0xC9D8FF;
	public static final int PAPER = 0xE8D9A8;
	public static final int WANGCHUAN = 0x6FA8C8;
	public static final int SEA = 0x7FE0F0;
	public static final int GOLD = 0xFFD27A;

	private static long ticks;

	static void tick(Minecraft minecraft) {
		ticks++;
		ClientLevel level = minecraft.level;
		Player player = minecraft.player;
		if (level == null || player == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		if (DimensiLevels.isBawah(level)) {
			bawahAmbience(level, player, random);
		} else if (DimensiLevels.isLaut(level)) {
			lautAmbience(level, player, random);
		}
	}

	private static int perSecond(int minimal, int normal, int maximal) {
		return switch (EffectQuality.level()) {
			case MINIMAL -> minimal;
			case MAXIMAL -> maximal;
			default -> normal;
		};
	}

	/** {@code count} per second as a chance per tick (several per tick when above 20). */
	private static int thisTick(RandomSource random, int perSecond) {
		int whole = perSecond / 20;
		return whole + (random.nextInt(20) < perSecond % 20 ? 1 : 0);
	}

	private static void bawahAmbience(ClientLevel level, Player player, RandomSource random) {
		Vec3 at = player.position();
		int paper = thisTick(random, perSecond(4, 10, 20));
		for (int i = 0; i < paper; i++) {
			double x = at.x + (random.nextDouble() - 0.5D) * 36.0D;
			double z = at.z + (random.nextDouble() - 0.5D) * 36.0D;
			double y = at.y + 2.0D + random.nextDouble() * 8.0D;
			CommonFx.add(Qi.petal(random.nextInt(4) == 0 ? 0xD8C080 : PAPER, 0.16F + random.nextFloat() * 0.08F, 140),
					x, y, z, (random.nextDouble() - 0.5D) * 0.02D, -0.012D, (random.nextDouble() - 0.5D) * 0.02D);
		}
		int wisps = thisTick(random, perSecond(6, 16, 32));
		for (int i = 0; i < wisps; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 4.0D + random.nextDouble() * 20.0D;
			double x = at.x + Math.cos(angle) * r;
			double z = at.z + Math.sin(angle) * r;
			double y = at.y + random.nextDouble() * 4.0D;
			CommonFx.add(Qi.mote(random.nextInt(3) == 0 ? 0x9FE8E0 : SOUL, 0.12F + random.nextFloat() * 0.1F, 60 + random.nextInt(40)),
					x, y, z, 0.0D, 0.008D + random.nextDouble() * 0.01D, 0.0D);
		}
		// the Wangchuan's pale glow on still water near the player
		if (ticks % 4L == 0L) {
			for (int i = 0; i < EffectQuality.scale(3); i++) {
				BlockPos probe = BlockPos.containing(at.x + (random.nextDouble() - 0.5D) * 24.0D, at.y - 1.0D - random.nextInt(4),
						at.z + (random.nextDouble() - 0.5D) * 24.0D);
				if (level.getFluidState(probe).isSource() && level.getBlockState(probe.above()).isAir()) {
					CommonFx.add(Qi.mist(WANGCHUAN, 0.7F, 60), probe.getX() + random.nextDouble(), probe.getY() + 1.02D,
							probe.getZ() + random.nextDouble(), 0.0D, 0.002D, 0.0D);
				}
			}
		}
		// every soul (all who walk here are souls) leaves a thin trail of wisps
		if (ticks % 3L == 0L) {
			float others = EffectQuality.othersScale();
			for (Player soul : level.players()) {
				if (soul.isSpectator() || soul.distanceToSqr(player) > 48.0D * 48.0D || soul.getDeltaMovement().horizontalDistanceSqr() < 0.0004D) {
					continue;
				}
				if (soul != player && random.nextFloat() > others) {
					continue;
				}
				CommonFx.add(Qi.mote(SOUL, 0.14F, 30), soul.getX() + (random.nextDouble() - 0.5D) * 0.5D, soul.getY() + 0.2D + random.nextDouble() * 1.4D,
						soul.getZ() + (random.nextDouble() - 0.5D) * 0.5D, 0.0D, 0.01D, 0.0D);
			}
		}
	}

	private static void lautAmbience(ClientLevel level, Player player, RandomSource random) {
		Vec3 at = player.position();
		int mist = thisTick(random, perSecond(2, 5, 10));
		for (int i = 0; i < mist; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 36.0D + random.nextDouble() * 30.0D;
			CommonFx.add(Qi.mist(0xE8FAFF, 3.0F + random.nextFloat() * 2.0F, 160), at.x + Math.cos(angle) * r, DimensiLevels.LAUT_SEA_TOP + 1.6D + random.nextDouble() * 3.0D,
					at.z + Math.sin(angle) * r, 0.01D, 0.0D, 0.0D);
		}
		if (player.isInWater() && player.getDeltaMovement().horizontalDistanceSqr() > 0.01D && random.nextInt(3) == 0) {
			CommonFx.add(ParticleTypes.SPLASH, at.x, at.y + 0.6D, at.z, 0.0D, 0.05D, 0.0D);
		}
		// the spirit islands breathe Qi: jade motes rise from any land above the sea within 48 b (client heightmap probe)
		if (ticks % 5L == 0L) {
			for (int i = 0; i < EffectQuality.scale(2); i++) {
				double x = at.x + (random.nextDouble() - 0.5D) * 96.0D;
				double z = at.z + (random.nextDouble() - 0.5D) * 96.0D;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
				if (top > DimensiLevels.LAUT_SEA_TOP + 2) {
					CommonFx.add(Qi.mote(random.nextInt(3) == 0 ? 0xFFE9A8 : 0x9FF0D0, 0.2F + random.nextFloat() * 0.12F, 70 + random.nextInt(40)),
							x, top + 0.3D + random.nextDouble() * 2.0D, z, 0.0D, 0.025D + random.nextDouble() * 0.02D, 0.0D);
				}
			}
		}
	}

	// ================================================================== moments

	static void onFx(DimensiNet.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Vec3 at = new Vec3(fx.x(), fx.y(), fx.z());
		boolean own = minecraft.player.position().distanceToSqr(at) < 3.0D * 3.0D;
		float scale = own ? 1.0F : EffectQuality.othersScale();
		switch (fx.kind()) {
			case DimensiNet.FX_ARRIVE_BAWAH -> arrive(at, true, own);
			case DimensiNet.FX_ARRIVE_LAUT -> arrive(at, false, own);
			case DimensiNet.FX_SOUL_LEAVE -> {
				int n = Math.round(EffectQuality.scale(10) * scale);
				for (int i = 0; i < n; i++) {
					CommonFx.add(Qi.mote(SOUL, 0.2F, 40), at.x + (random.nextDouble() - 0.5D) * 0.8D, at.y + random.nextDouble() * 1.8D,
							at.z + (random.nextDouble() - 0.5D) * 0.8D, 0.0D, 0.04D + random.nextDouble() * 0.04D, 0.0D);
				}
				if (fx.arg() >= 60) {
					CommonFx.ring(at.add(0.0D, 0.1D, 0.0D), SOUL, 2.5F, EffectQuality.scale(28));
					CommonFx.pillar(at, SOUL, 5.0F);
				}
			}
			case DimensiNet.FX_SOUL_RETURN -> {
				int n = Math.round(EffectQuality.scale(24) * scale);
				for (int i = 0; i < n; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					CommonFx.add(Qi.mote(SOUL, 0.22F, 30), at.x + Math.cos(angle) * 2.0D, at.y + 2.5D, at.z + Math.sin(angle) * 2.0D,
							-Math.cos(angle) * 0.06D, -0.07D, -Math.sin(angle) * 0.06D);
				}
			}
			case DimensiNet.FX_SPOUT -> {
				int n = Math.round(EffectQuality.scale(30) * scale);
				for (int i = 0; i < n; i++) {
					CommonFx.add(ParticleTypes.SPLASH, at.x + (random.nextDouble() - 0.5D) * 1.6D, at.y + random.nextDouble() * 3.0D,
							at.z + (random.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.3D, 0.0D);
					if (i % 3 == 0) {
						CommonFx.add(ParticleTypes.BUBBLE_COLUMN_UP, at.x + (random.nextDouble() - 0.5D), at.y - 1.0D, at.z + (random.nextDouble() - 0.5D),
								0.0D, 0.5D, 0.0D);
					}
				}
			}
			case DimensiNet.FX_JUDGED -> {
				CommonFx.ring(at.add(0.0D, 0.2D, 0.0D), GOLD, 3.0F, EffectQuality.scale(36));
				CommonFx.add(Qi.groundRing(GOLD, 0.5F, 6.0F, 30), at.x, at.y + 0.05D, at.z, 0.0D, 0.0D, 0.0D);
				CommonFx.burst(at.add(0.0D, 1.2D, 0.0D), PAPER, Math.round(EffectQuality.scale(18) * scale), 1.2F);
			}
			case DimensiNet.FX_SOUP -> {
				for (int i = 0; i < EffectQuality.scale(14); i++) {
					CommonFx.add(Qi.mist(0xD8E0F0, 0.5F, 50), at.x + (random.nextDouble() - 0.5D), at.y + 1.6D, at.z + (random.nextDouble() - 0.5D),
							0.0D, 0.03D, 0.0D);
				}
			}
			case DimensiNet.FX_BOOK -> {
				for (int i = 0; i < EffectQuality.scale(fx.arg() == 1 ? 60 : 24); i++) {
					CommonFx.add(ParticleTypes.ENCHANT, at.x + (random.nextDouble() - 0.5D) * 3.0D, at.y + 1.0D + random.nextDouble() * 2.0D,
							at.z + (random.nextDouble() - 0.5D) * 3.0D, 0.0D, -0.4D, 0.0D);
				}
				if (fx.arg() == 1) {
					CommonFx.pillar(at, 0xD04030, 8.0F);
					CommonFx.ring(at.add(0.0D, 0.1D, 0.0D), 0xD04030, 4.0F, EffectQuality.scale(48));
				}
			}
			case DimensiNet.FX_HARVEST -> {
				for (int i = 0; i < EffectQuality.scale(10); i++) {
					CommonFx.add(Qi.petal(0x9FE0A0, 0.18F, 50), at.x + (random.nextDouble() - 0.5D), at.y + 0.5D, at.z + (random.nextDouble() - 0.5D),
							(random.nextDouble() - 0.5D) * 0.08D, 0.08D, (random.nextDouble() - 0.5D) * 0.08D);
				}
			}
			case DimensiNet.FX_WHIRL -> {
				CommonFx.spiralIn(at, SEA, 6.0F, EffectQuality.scale(40), 20.0F);
				CommonFx.ring(at, 0xFFFFFF, 5.0F, EffectQuality.scale(40));
			}
			case DimensiNet.FX_BLESS -> {
				int rgb = fx.arg() == 1 ? SEA : fx.arg() == 2 ? 0xFFE9A8 : GOLD;
				CommonFx.spiralIn(at.add(0.0D, 1.0D, 0.0D), rgb, 1.6F, EffectQuality.scale(fx.arg() == 1 ? 12 : 28), 16.0F);
				if (fx.arg() == 2) {
					CommonFx.pillar(at, rgb, 10.0F);
				}
			}
			default -> {
			}
		}
	}

	/** An arrival: the light of the crossing and, for one's own arrival, the spine's world scene. */
	private static void arrive(Vec3 at, boolean bawah, boolean own) {
		Minecraft minecraft = Minecraft.getInstance();
		int rgb = bawah ? SOUL : SEA;
		CommonFx.ring(at.add(0.0D, 0.1D, 0.0D), rgb, 3.0F, EffectQuality.scale(40));
		CommonFx.pillar(at, rgb, 7.0F);
		CommonFx.burst(at.add(0.0D, 1.0D, 0.0D), bawah ? PAPER : 0xFFFFFF, EffectQuality.scale(20), 2.0F);
		if (!own || minecraft.player == null) {
			return;
		}
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				bawah ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.CONDUIT_ACTIVATE, bawah ? 0.5F : 1.2F, 0.8F));
		EpicSettings.Camera mode = CinematicDirector.mode();
		if (mode == EpicSettings.Camera.MATI) {
			return;
		}
		Scene scene = DuniaScenes.world(bawah ? "tiba/bawah" : "tiba/laut", at, (int) (ticks & 0x7FFFFFFF), 60, true);
		if (scene == null) {
			return;
		}
		if (mode == EpicSettings.Camera.PENUH) {
			DuniaScenes.pullback(scene, 4, 2.5F, 9.0F, 1.6F, 5.0F, 2.0F);
		}
		DuniaScenes.card(scene, 8, Component.translatable(bawah ? "kultivator.dimensi.bawah.card" : "kultivator.dimensi.laut.card"),
				Component.translatable(bawah ? "kultivator.dimensi.bawah.card_sub" : "kultivator.dimensi.laut.card_sub"), bawah ? "冥" : "海",
				bawah ? 0x8A7AC8 : 0x3FB8C8);
	}

	static void clear() {
		ticks = 0L;
	}

	private DimensiFx() {
	}
}
