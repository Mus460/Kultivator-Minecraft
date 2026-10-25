package dev.kultivator.client.sectbuild;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.build.api.BuildFxIds;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.flow.Ceremonies;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Effects of {@code FxType.SECT_BUILD} (DESIGN 21.16), every particle made here and scaled by Effect Quality:
 * <ul>
 * <li>placement crumbs of the placed block and one jade spark per batch; dismantling dust;</li>
 * <li>a component opening (a jade outline on its ground) and finishing (a cherry-petal burst along its roofline);</li>
 * <li>a tier finishing (a gold light pillar from the main hall, a gold ribbon along the axis, lantern orbs rising, the
 * owner's toast) and the grand finale of a complete template;</li>
 * <li>Batu Pertama (the footprint outline flashes once), blocked pings (a red X shimmer), builders' dust and gold qi
 * threads, deposits flowing into the stele, refund puffs.</li>
 * </ul>
 * The same ceremony packet arriving twice within a second is drawn once. Continuous effects (the work mist, the cloud
 * sea of {@link MistFx}) run in {@link #tick}. Source: game design; the petals echo the plum motif of <i>Return of the
 * Mount Hua Sect</i>.
 */
public final class BuildFx {

	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xFF3A2A;
	private static final int PETAL = 0xF6B7CF;
	private static final int MAX_ANIMATIONS = 32;
	private static final Map<String, Long> RECENT = new HashMap<>();
	private static final List<Animation> ANIMATIONS = new ArrayList<>();

	private static final class Animation {
		final int kind;
		final Vec3 pos;
		final Vec3 target;
		final int b;
		final int life;
		int age;

		Animation(int kind, Vec3 pos, Vec3 target, int b, int life) {
			this.kind = kind;
			this.pos = pos;
			this.target = target;
			this.b = b;
			this.life = life;
		}
	}

	/** One {@code FxType.SECT_BUILD} packet (routed by {@code FxDispatcher}). */
	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.SECT_BUILD || CommonFx.level() == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int a = fx.a();
		int b = fx.b();
		float f = fx.f();
		if (a != BuildFxIds.PLACE && a != BuildFxIds.DECONSTRUCT && a != Ceremonies.FX_WORK && a != Ceremonies.FX_DEPOSIT && duplicate(a, pos)) {
			return;
		}
		switch (a) {
			case BuildFxIds.PLACE -> crumbs(pos, target, b, Math.max(1, Math.round(f)), true);
			case BuildFxIds.DECONSTRUCT -> crumbs(pos, target, b, Math.max(1, Math.round(f)), false);
			case BuildFxIds.COMPONENT_OPEN -> outline(pos, target, pos.y + 0.1D, SiteHologram.TIER_RGB[Math.max(0, Math.min(8, b - 1))], 2);
			case BuildFxIds.COMPONENT_DONE -> {
				petals(pos, target, f);
				CommonFx.burst(new Vec3((pos.x + target.x) / 2.0D, f + 0.5D, (pos.z + target.z) / 2.0D), GOLD, 24, 2.0F);
			}
			case BuildFxIds.TIER_DONE -> animate(new Animation(BuildFxIds.TIER_DONE, pos, target, b, 200));
			case BuildFxIds.FIRST_STONE -> {
				outline(pos, target, pos.y + 0.2D, JADE, 3);
				animate(new Animation(BuildFxIds.FIRST_STONE, pos, target, b, 30));
			}
			case BuildFxIds.BLOCKED -> cross(pos, Math.max(1.0F, f));
			case Ceremonies.FX_WORK -> work(pos, target, b);
			case Ceremonies.FX_DEPOSIT -> deposit(pos, target, b, f);
			case Ceremonies.FX_ORB -> animate(new Animation(Ceremonies.FX_ORB, pos, pos, b, 50));
			// the single notice slot (DESIGN 43.9), a little longer than a plain notice
			case Ceremonies.FX_TOAST -> Kabar.kirim(Kabar.kartu(Component.translatable("kultivator.sectbuild.toast.title", b),
					Component.translatable("kultivator.sectbuild.toast.text", SectTemplates.tierName(b), SectTemplates.name(Math.round(f))))
					.ikon(Kabar.glyph(Glyphs.BUILD)).lama(7000L));
			case Ceremonies.FX_GRAND -> animate(new Animation(Ceremonies.FX_GRAND, pos, target, b, 120));
			case Ceremonies.FX_PUFF -> CommonFx.burst(pos, b == 0 ? JADE : b, 10, 0.6F);
			default -> {
			}
		}
	}

	/** True when the same effect at the same place came within the last second (both modules may send a ceremony). */
	private static boolean duplicate(int a, Vec3 pos) {
		String key = a + ":" + Math.round(pos.x) + ":" + Math.round(pos.y) + ":" + Math.round(pos.z);
		long now = Util.getMillis();
		if (RECENT.size() > 64) {
			RECENT.values().removeIf(time -> now - time > 2000L);
		}
		Long last = RECENT.put(key, now);
		return last != null && now - last < 1000L;
	}

	// ================================================================== single effects

	/** 3-6 crumbs of the block per placement (batched, at most 8 placements) and one jade spark. */
	private static void crumbs(Vec3 from, Vec3 to, int stateId, int count, boolean spark) {
		RandomSource random = CommonFx.random();
		BlockState state = Block.stateById(stateId);
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
		int batches = Math.min(8, count);
		int each = EffectQuality.minimal() ? 1 : 2 + random.nextInt(EffectQuality.maximal() ? 4 : 2);
		for (int i = 0; i < batches; i++) {
			double t = batches <= 1 ? 0.0D : i / (double) (batches - 1);
			Vec3 at = from.lerp(to, t).add(0.5D, 0.5D, 0.5D);
			for (int k = 0; k < each; k++) {
				CommonFx.add(particle, at.x + random.nextGaussian() * 0.3D, at.y + random.nextGaussian() * 0.3D, at.z + random.nextGaussian() * 0.3D,
						random.nextGaussian() * 0.05D, 0.05D + random.nextFloat() * 0.05D, random.nextGaussian() * 0.05D);
			}
		}
		if (spark) {
			Vec3 at = to.add(0.5D, 1.0D, 0.5D);
			CommonFx.add(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0D, 0.04D, 0.0D);
			CommonFx.add(CommonFx.dust(JADE, 1.0F), at.x, at.y + 0.2D, at.z, 0.0D, 0.02D, 0.0D);
		}
	}

	/** Dust along a box's ground outline. */
	private static void outline(Vec3 min, Vec3 max, double y, int rgb, int density) {
		DustParticleOptions dust = CommonFx.dust(rgb, 1.0F);
		double width = max.x - min.x;
		double depth = max.z - min.z;
		double perimeter = 2.0D * (width + depth);
		int n = Math.min(600, EffectQuality.scale((int) Math.ceil(perimeter * density / 2.0D)));
		for (int i = 0; i < n; i++) {
			double s = perimeter * i / Math.max(1, n);
			double x;
			double z;
			if (s < width) {
				x = min.x + s;
				z = min.z;
			} else if (s < width + depth) {
				x = max.x;
				z = min.z + (s - width);
			} else if (s < 2.0D * width + depth) {
				x = max.x - (s - width - depth);
				z = max.z;
			} else {
				x = min.x;
				z = max.z - (s - 2.0D * width - depth);
			}
			CommonFx.add(dust, x, y, z, 0.0D, 0.01D, 0.0D);
		}
	}

	/** Cherry petals drifting off the roofline (the top outline of the box at {@code roofY}). */
	private static void petals(Vec3 min, Vec3 max, float roofY) {
		RandomSource random = CommonFx.random();
		double width = max.x - min.x;
		double depth = max.z - min.z;
		int n = Math.min(160, EffectQuality.scale((int) Math.ceil((width + depth) * 1.5D)));
		for (int i = 0; i < n; i++) {
			boolean alongX = random.nextBoolean();
			double x = alongX ? min.x + random.nextDouble() * width : random.nextBoolean() ? min.x : max.x;
			double z = alongX ? (random.nextBoolean() ? min.z : max.z) : min.z + random.nextDouble() * depth;
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, x, roofY + random.nextDouble(), z, random.nextGaussian() * 0.02D, -0.01D, random.nextGaussian() * 0.02D);
			if (i % 4 == 0) {
				CommonFx.add(CommonFx.dust(PETAL, 0.8F), x, roofY + 0.3D, z, 0.0D, 0.02D, 0.0D);
			}
		}
	}

	/** The red X shimmer over a blocked cell. */
	private static void cross(Vec3 center, float seconds) {
		DustParticleOptions dust = CommonFx.dust(RED, 0.9F);
		int n = EffectQuality.scale(8);
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n - 0.5D;
			CommonFx.add(dust, center.x + t, center.y + 0.55D, center.z + t, 0.0D, 0.0D, 0.0D);
			CommonFx.add(dust, center.x + t, center.y + 0.55D, center.z - t, 0.0D, 0.0D, 0.0D);
		}
	}

	/** A builder's dust at its hands and a thin gold qi thread to the work layer. */
	private static void work(Vec3 hand, Vec3 layer, int color) {
		RandomSource random = CommonFx.random();
		for (int i = 0; i < EffectQuality.scale(3); i++) {
			CommonFx.add(CommonFx.dust(0xC8B89A, 0.7F), hand.x + random.nextGaussian() * 0.2D, hand.y + random.nextGaussian() * 0.1D,
					hand.z + random.nextGaussian() * 0.2D, 0.0D, 0.02D, 0.0D);
		}
		if (!EffectQuality.minimal() && hand.distanceToSqr(layer) > 1.0D) {
			CommonFx.beam(hand, layer, color == 0 ? GOLD : color, 0.03F);
		}
	}

	/** Coloured motes flowing from the depositor into the stele. */
	private static void deposit(Vec3 from, Vec3 to, int rgb, float units) {
		RandomSource random = CommonFx.random();
		int n = Math.min(40, EffectQuality.scale(6 + (int) Math.min(24.0F, units / 8.0F)));
		DustParticleOptions dust = CommonFx.dust(rgb, 1.0F);
		Vec3 delta = to.subtract(from);
		for (int i = 0; i < n; i++) {
			double t = random.nextDouble();
			double swirl = Math.sin(t * Math.PI) * 0.6D;
			Vec3 at = from.add(delta.scale(t)).add(Math.cos(t * 12.0D) * swirl, Math.sin(t * Math.PI) * 0.8D, Math.sin(t * 12.0D) * swirl);
			Vec3 velocity = delta.normalize().scale(0.08D);
			CommonFx.add(dust, at, velocity, false);
		}
		CommonFx.ring(to.add(0.0D, -0.6D, 0.0D), JADE, 0.8F, 12);
	}

	// ================================================================== animations

	private static void animate(Animation animation) {
		if (ANIMATIONS.size() < MAX_ANIMATIONS) {
			ANIMATIONS.add(animation);
		}
	}

	/** Every client tick while a level is shown (routed by {@code FxDispatcher.tick}). */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			ANIMATIONS.clear();
			return;
		}
		RandomSource random = CommonFx.random();
		for (Iterator<Animation> it = ANIMATIONS.iterator(); it.hasNext(); ) {
			Animation animation = it.next();
			if (++animation.age > animation.life) {
				it.remove();
				continue;
			}
			switch (animation.kind) {
				case BuildFxIds.TIER_DONE -> tierStep(animation, random);
				case BuildFxIds.FIRST_STONE -> {
					if (animation.age == 15) {
						outline(animation.pos, animation.target, animation.pos.y + 0.2D, GOLD, 2);
					}
				}
				case Ceremonies.FX_ORB -> {
					double rise = animation.age * 0.08D;
					Vec3 at = animation.pos.add(Math.sin(animation.age * 0.3D) * 0.2D, rise, Math.cos(animation.age * 0.3D) * 0.2D);
					CommonFx.add(CommonFx.dust(0xFFC46A, 1.6F), at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
					if (EffectQuality.chance(random, 0.4F)) {
						CommonFx.add(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0D, 0.01D, 0.0D);
					}
				}
				case Ceremonies.FX_GRAND -> grandStep(animation, random);
				default -> {
				}
			}
		}
		MistFx.tick(minecraft);
	}

	/** The tier ceremony: a gold light pillar from the main hall, a ribbon along the axis, gold rain. */
	private static void tierStep(Animation animation, RandomSource random) {
		if (animation.age % 6 == 0) {
			CommonFx.pillar(animation.pos, GOLD, 24.0F);
		}
		if (animation.age % 10 == 0 && animation.age <= 120) {
			double t = Math.min(1.0D, animation.age / 120.0D);
			Vec3 tip = animation.pos.lerp(animation.target, t);
			CommonFx.beam(animation.pos, tip, GOLD, 0.12F);
		}
		if (animation.age == 1) {
			CommonFx.ring(animation.pos.add(0.0D, 0.1D, 0.0D), GOLD, 6.0F, 96);
			CommonFx.burst(animation.pos.add(0.0D, 3.0D, 0.0D), GOLD, 60, 3.0F);
		}
		if (EffectQuality.chance(random, 0.5F)) {
			Vec3 at = animation.pos.add(random.nextGaussian() * 6.0D, 10.0D + random.nextDouble() * 6.0D, random.nextGaussian() * 6.0D);
			CommonFx.add(CommonFx.dust(GOLD, 1.0F), at.x, at.y, at.z, 0.0D, -0.05D, 0.0D);
		}
	}

	/** The grand finale: blossoms of gold and petals along the whole axis. */
	private static void grandStep(Animation animation, RandomSource random) {
		if (animation.age % 15 == 0) {
			double t = random.nextDouble();
			Vec3 at = animation.pos.lerp(animation.target, t).add(0.0D, 8.0D + random.nextDouble() * 8.0D, 0.0D);
			CommonFx.burst(at, random.nextBoolean() ? GOLD : PETAL, 40, 3.0F);
			if (EffectQuality.maximal()) {
				for (int i = 0; i < 12; i++) {
					CommonFx.add(ParticleTypes.FIREWORK, at.x, at.y, at.z, random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D,
							random.nextGaussian() * 0.2D);
				}
			}
		}
		if (EffectQuality.chance(random, 0.7F)) {
			double t = random.nextDouble();
			Vec3 at = animation.pos.lerp(animation.target, t).add(random.nextGaussian() * 4.0D, 6.0D, random.nextGaussian() * 4.0D);
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, 0.0D, -0.02D, 0.0D);
		}
	}

	private BuildFx() {
	}
}
