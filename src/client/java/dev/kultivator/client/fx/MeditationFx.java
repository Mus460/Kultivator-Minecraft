package dev.kultivator.client.fx;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.VisualState;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Meditation effects. Continuous ones come from the synced look of every nearby player: qi motes spiralling and
 * streaming into the lower dantian in the colour of their root (plus a faint ground ring at Maximal), and while
 * enlightened a golden halo with falling golden motes, and for ourselves the qi lines of our gathering formation (from
 * the synced tier). One-shot ones come from the server: the enlightenment burst and the activation flash of a
 * gathering formation, qi lines flowing from its pillars to the seat.
 */
public final class MeditationFx {

	private static final double RANGE_SQ = 48.0D * 48.0D;
	private static final int GOLD = 0xFFD54A;
	/** Height of the lower dantian of a seated cultivator above the ground (blocks, before scale). */
	private static final double DANTIAN = 0.45D;

	private static ClientLevel lastLevel;

	public static void play(FxType type, Payloads.Fx fx) {
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		switch (type) {
			case ENLIGHTENMENT -> enlightenmentBurst(pos, fx.a() == 0 ? GOLD : fx.a() & 0xFFFFFF);
			case QI_FORMATION -> formation(pos, fx.a());
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			MeditationPose.clear();
		}
		if (level.getGameTime() % 100L == 0L) {
			MeditationPose.cleanup(level);
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = level.getRandom();
		boolean firstPersonView = minecraft.options.getCameraType().isFirstPerson();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isRemoved() || !player.isAlive() || player.isInvisible() && player != minecraft.player) {
				continue;
			}
			VisualState visual = ClientCultivation.visual(player);
			boolean seated = MeditationPose.posed(visual) && MeditationPose.canSit(player);
			boolean enlightened = visual.has(VisualState.ENLIGHTENED);
			if (!seated && !enlightened || player.position().distanceToSqr(camera) > RANGE_SQ) {
				continue;
			}
			// Our own motes would float right in front of the camera in first person: keep those near the ground.
			boolean ownView = player == minecraft.player && firstPersonView;
			if (seated) {
				// DESIGN 31.2.6: the Aura Kultivasi draws its own inflow in the root colour while it covers this player
				if (!dev.kultivator.client.aura.AuraEngine.covers(player.getId())) {
					meditationMotes(player, visual, random, ownView);
				}
				if (player == minecraft.player) {
					CultivationData data = ClientCultivation.data();
					if (data.meditating() && data.formationTier() > 0) {
						formationFlow(player, data.formationTier(), random);
					}
				}
			}
			if (enlightened) {
				enlightenedGlow(player, random, ownView, seated);
			}
		}
	}

	// ================================================================ continuous

	private static void meditationMotes(AbstractClientPlayer player, VisualState visual, RandomSource random, boolean ownView) {
		int color = visual.rootColor() & 0xFFFFFF;
		float scale = player.getScale();
		float power = visual.power();
		Vec3 feet = player.position();
		Vec3 dantian = feet.add(0.0D, DANTIAN * scale, 0.0D);
		int age = player.tickCount;
		if (!ownView) {
			if (EffectQuality.chance(random, 0.5F)) {
				CommonFx.spiralIn(dantian, color, 1.1F * scale, 2 + Math.round(power * 3.0F), age);
			}
			// Threads of qi drawn in from the surroundings, more of them at higher realms.
			if (EffectQuality.chance(random, 0.15F + power * 0.2F)) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double radius = (1.6D + random.nextDouble() * 0.9D) * scale;
				Vec3 from = feet.add(Math.cos(angle) * radius, (0.2D + random.nextDouble() * 1.6D) * scale, Math.sin(angle) * radius);
				CommonFx.add(new TrailParticleOption(dantian, color, 18 + random.nextInt(14)), from, Vec3.ZERO, false);
			}
		} else if (EffectQuality.chance(random, 0.3F)) {
			// First person: motes rising from the floor around the seat, well below the eyes.
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = (0.9D + random.nextDouble() * 0.5D) * scale;
			CommonFx.add(CommonFx.dust(color, 0.6F), feet.add(Math.cos(angle) * radius, 0.05D, Math.sin(angle) * radius),
					new Vec3(0.0D, 0.015D, 0.0D), false);
		}
		if (EffectQuality.maximal() && age % 10 == 0) {
			groundRing(feet.add(0.0D, 0.03D, 0.0D), color, 1.25F * scale, 14, age * 0.05D);
		}
	}

	/**
	 * The qi lines of our own gathering formation, every tick from the synced tier: a mote now and then from every
	 * pillar towards the dantian, and a faint beam every half second. (The server only sends the activation flash.)
	 */
	private static void formationFlow(AbstractClientPlayer player, int tier, RandomSource random) {
		int clamped = Math.max(1, Math.min(3, tier));
		int color = formationColor(clamped);
		Vec3 seatTop = Vec3.atBottomCenterOf(player.blockPosition());
		Vec3 center = seatTop.add(0.0D, DANTIAN * player.getScale(), 0.0D);
		boolean beams = player.tickCount % 10 == 0;
		for (Vec3 pillar : pillars(seatTop, clamped)) {
			if (EffectQuality.chance(random, 0.35F)) {
				Vec3 from = pillar.add((random.nextFloat() - 0.5F) * 0.3D, random.nextFloat() * 0.3D, (random.nextFloat() - 0.5F) * 0.3D);
				CommonFx.add(new TrailParticleOption(center, color, 14 + random.nextInt(10)), from, Vec3.ZERO, false);
			}
			if (beams) {
				int n = EffectQuality.scale(3);
				Vec3 delta = center.subtract(pillar);
				double offset = random.nextDouble() / n;
				DustParticleOptions dust = CommonFx.dust(color, 0.35F);
				for (int i = 0; i < n; i++) {
					Vec3 at = pillar.add(delta.scale(offset + (double) i / n));
					CommonFx.add(dust, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
				}
			}
		}
	}

	/** Top centres of the pillars of a formation around {@code seatTop} (cardinal; corners from tier 2; End Rods at tier 3). */
	private static List<Vec3> pillars(Vec3 seatTop, int tier) {
		List<Vec3> pillars = new ArrayList<>();
		double rodTop = tier >= 3 ? 1.0D : 0.0D;
		pillars.add(seatTop.add(3.0D, rodTop, 0.0D));
		pillars.add(seatTop.add(-3.0D, rodTop, 0.0D));
		pillars.add(seatTop.add(0.0D, rodTop, 3.0D));
		pillars.add(seatTop.add(0.0D, rodTop, -3.0D));
		if (tier >= 2) {
			pillars.add(seatTop.add(3.0D, 0.0D, 3.0D));
			pillars.add(seatTop.add(3.0D, 0.0D, -3.0D));
			pillars.add(seatTop.add(-3.0D, 0.0D, 3.0D));
			pillars.add(seatTop.add(-3.0D, 0.0D, -3.0D));
		}
		return pillars;
	}

	private static int formationColor(int tier) {
		return switch (tier) {
			case 2 -> 0xFFD54A;
			case 3 -> 0xBFF6FF;
			default -> 0xC69CFF;
		};
	}

	/** A faint ring of small motes on the ground, slowly turning. */
	private static void groundRing(Vec3 center, int color, float radius, int points, double turn) {
		int n = EffectQuality.scale(points);
		DustParticleOptions dust = CommonFx.dust(color, 0.45F);
		for (int i = 0; i < n; i++) {
			double angle = turn + Math.PI * 2.0D * i / n;
			CommonFx.add(dust, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
		}
	}

	private static void enlightenedGlow(AbstractClientPlayer player, RandomSource random, boolean ownView, boolean seated) {
		float scale = player.getScale();
		Vec3 feet = player.position();
		double headTop = feet.y + (seated ? 1.32D : 1.95D) * scale;
		int age = player.tickCount;
		if (!ownView && age % 2 == 0) {
			// The halo: a small golden circle above the head.
			int n = EffectQuality.scale(8);
			DustParticleOptions dust = CommonFx.dust(GOLD, 0.5F);
			double radius = 0.32D * scale;
			for (int i = 0; i < n; i++) {
				double angle = age * 0.12D + Math.PI * 2.0D * i / n;
				CommonFx.add(dust, feet.x + Math.cos(angle) * radius, headTop + 0.18D * scale, feet.z + Math.sin(angle) * radius,
						0.0D, 0.0D, 0.0D);
			}
		}
		if (EffectQuality.chance(random, 0.6F)) {
			// Golden motes drifting down like falling light; around, not onto, our own camera.
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = (ownView ? 1.0D + random.nextDouble() * 0.7D : random.nextDouble() * 1.1D) * scale;
			Vec3 from = new Vec3(feet.x + Math.cos(angle) * radius, headTop + (1.0D + random.nextDouble() * 0.9D) * scale,
					feet.z + Math.sin(angle) * radius);
			CommonFx.add(CommonFx.dust(GOLD, 0.7F), from, new Vec3(0.0D, -0.045D, 0.0D), false);
		}
		if (EffectQuality.maximal() && random.nextInt(5) == 0) {
			Vec3 from = feet.add(random.nextGaussian() * 0.8D * scale, headTop - feet.y + 1.4D * scale, random.nextGaussian() * 0.8D * scale);
			CommonFx.add(ParticleTypes.END_ROD, from, new Vec3(0.0D, -0.03D, 0.0D), false);
		}
	}

	// ================================================================ one-shot

	/** Enlightenment: a golden burst, a pillar of light, rings spreading on the ground and sparks rising. */
	private static void enlightenmentBurst(Vec3 feet, int color) {
		RandomSource random = CommonFx.random();
		CommonFx.burst(feet.add(0.0D, 1.0D, 0.0D), color, 50, 3.0F);
		CommonFx.pillar(feet, color, 6.0F);
		for (int ring = 1; ring <= 3; ring++) {
			CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), color, ring * 1.1F, 20 + ring * 8);
		}
		int sparks = EffectQuality.scale(18);
		for (int i = 0; i < sparks; i++) {
			CommonFx.add(ParticleTypes.END_ROD, feet.add(random.nextGaussian() * 0.3D, 1.2D, random.nextGaussian() * 0.3D),
					new Vec3(random.nextGaussian() * 0.04D, 0.12D + random.nextDouble() * 0.12D, random.nextGaussian() * 0.04D), false);
		}
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 24; i++) {
				CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, feet.add(0.0D, 1.0D, 0.0D),
						new Vec3(random.nextGaussian() * 0.25D, 0.2D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D), false);
			}
		}
	}

	/**
	 * Qi lines of a gathering formation: from every pillar top to the seated cultivator's dantian. {@code seatTop} is the
	 * top centre of the block they sit on; pillars are 3 blocks away (cardinal, plus corners from tier 2; tier 3 has End
	 * Rods on the cardinal pillars, so those lines start one block higher).
	 */
	private static void formation(Vec3 seatTop, int tier) {
		int clamped = Math.max(1, Math.min(3, tier));
		int color = formationColor(clamped);
		RandomSource random = CommonFx.random();
		Vec3 center = seatTop.add(0.0D, DANTIAN, 0.0D);
		int motes = EffectQuality.scale(4 + clamped);
		for (Vec3 pillar : pillars(seatTop, clamped)) {
			CommonFx.beam(pillar, center, color, 0.03F);
			for (int i = 0; i < motes; i++) {
				Vec3 from = pillar.add((random.nextFloat() - 0.5F) * 0.3D, random.nextFloat() * 0.3D, (random.nextFloat() - 0.5F) * 0.3D);
				CommonFx.add(new TrailParticleOption(center, color, 14 + i * 5 + random.nextInt(6)), from, Vec3.ZERO, false);
			}
			CommonFx.add(ParticleTypes.END_ROD, pillar.add(0.0D, 0.15D, 0.0D), new Vec3(0.0D, 0.03D, 0.0D), false);
		}
		if (clamped >= 2) {
			CommonFx.ring(seatTop.add(0.0D, 0.05D, 0.0D), color, 3.0F, 36);
		}
		if (clamped >= 3) {
			// The heavenly star formation also draws starlight down from the sky.
			CommonFx.pillar(seatTop.add(0.0D, 1.5D, 0.0D), 0xFFFFFF, 4.0F);
			int stars = EffectQuality.scale(10);
			for (int i = 0; i < stars; i++) {
				Vec3 from = seatTop.add(random.nextGaussian() * 1.5D, 5.0D + random.nextDouble() * 2.0D, random.nextGaussian() * 1.5D);
				CommonFx.add(new TrailParticleOption(center, 0xFFFFFF, 20 + random.nextInt(15)), from, Vec3.ZERO, false);
			}
		}
	}

	/** Disconnect: drops the old world (the next world's first tick would only do it after it loads). */
	static void forget() {
		lastLevel = null;
		MeditationPose.clear();
	}

	private MeditationFx() {
	}
}
