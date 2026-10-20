package dev.kultivator.client.kehancuran;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.disciple.path.DestroyerArts;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.KehancuranFxIds;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * Effects of {@code FxType.KEHANCURAN} (DESIGN 20.3.22, module D), client particles scaled by Effect Quality: the
 * server's caps for the warning screen, the red sky (with ash drifting around the player), the outline preview of a
 * destructive shape (kept for the confirm window), the dust of removed blocks, the restore shimmer and the impact.
 */
public final class KehancuranFx {

	private static final int RED = 0xE0281C;
	private static final int DARK_RED = 0x6E0F0A;
	/** Nine spheres of Segel Gunung-Laut lie this far apart (DestroyerArts.spec). */
	private static final double NINE_SPACING = 8.0D;

	/** One {@code FxType.KEHANCURAN} packet (routed by {@code FxDispatcher}). */
	public static void play(FxType type, Payloads.Fx fx) {
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		RandomSource random = CommonFx.random();
		switch (fx.a()) {
			case KehancuranFxIds.CONFIG -> KehancuranClient.onConfig(fx);
			case KehancuranFxIds.RED_SKY -> KehancuranClient.sky(pos, fx.b());
			case KehancuranFxIds.PREVIEW -> {
				KehancuranClient.preview(pos, target, fx.b(), fx.f());
				outline(pos, target, Shapes.Kind.byOrdinal(fx.b()), fx.f());
			}
			case KehancuranFxIds.DUST -> {
				int n = Math.max(2, EffectQuality.scale(Math.min(24, 4 + fx.b() / 3)));
				if (DampakWorld.debrisRunning()) {
					// Phase 10 debris displays carry the dust now (DESIGN 28.13.1): no double dust
					n = Math.max(1, n / 2);
				}
				float spread = Math.max(1.0F, fx.f());
				for (int i = 0; i < n; i++) {
					Vec3 at = pos.add(random.nextGaussian() * spread * 0.5D, random.nextGaussian() * spread * 0.3D, random.nextGaussian() * spread * 0.5D);
					CommonFx.add(CommonFx.dust(random.nextBoolean() ? 0x8B7355 : 0x6B6B6B, 1.4F), at, new Vec3(0.0D, 0.04D, 0.0D), false);
					if (i % 3 == 0) {
						CommonFx.add(ParticleTypes.CLOUD, at, new Vec3(random.nextGaussian() * 0.02D, 0.05D, random.nextGaussian() * 0.02D), false);
					}
				}
				if (EffectQuality.chance(random, 0.25F)) {
					CommonFx.add(ParticleTypes.DUST_PLUME, pos, new Vec3(0.0D, 0.1D, 0.0D), false);
				}
			}
			case KehancuranFxIds.SHIMMER -> {
				int n = Math.max(2, EffectQuality.scale(Math.min(20, 3 + fx.b() / 3)));
				float spread = Math.max(1.0F, fx.f());
				for (int i = 0; i < n; i++) {
					Vec3 at = pos.add(random.nextGaussian() * spread * 0.5D, random.nextGaussian() * spread * 0.3D, random.nextGaussian() * spread * 0.5D);
					CommonFx.add(CommonFx.dust(random.nextBoolean() ? 0x9FF3E6 : 0xFFFFFF, 0.9F), at, new Vec3(0.0D, 0.02D, 0.0D), false);
					if (i % 4 == 0) {
						CommonFx.add(ParticleTypes.END_ROD, at, new Vec3(0.0D, 0.03D, 0.0D), false);
					}
				}
			}
			case KehancuranFxIds.IMPACT -> {
				DestroyerArts.Art art = DestroyerArts.Art.byOrdinal(fx.b());
				int color = art == null ? RED : art.color();
				float radius = Math.max(2.0F, fx.f());
				CommonFx.burst(pos, color, 60, radius * 0.6F);
				CommonFx.ring(pos, RED, radius, 72);
				CommonFx.ring(pos, color, radius * 0.6F, 48);
				CommonFx.pillar(pos, DARK_RED, 10.0F);
				CommonFx.add(ParticleTypes.EXPLOSION, pos, Vec3.ZERO, true);
				for (int i = 0; i < EffectQuality.scale(30); i++) {
					Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian()).normalize();
					CommonFx.add(ParticleTypes.LARGE_SMOKE, pos.add(dir.scale(radius * random.nextFloat())), dir.scale(0.08D), false);
				}
				// Phase 10 (DESIGN 28.13.1): every carve gets a shock ring; a self job whose nearest player is the local one
				// counts as the local player's own (the packet names no caster)
				DampakWorld.onKehancuranImpact(pos, radius, art == null && nearestIsLocal(pos, radius + 32.0D));
			}
			default -> {
			}
		}
	}

	/** True when the local player is within {@code range} of {@code pos} and no other player is nearer. */
	private static boolean nearestIsLocal(Vec3 pos, double range) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return false;
		}
		double own = minecraft.player.position().distanceToSqr(pos);
		if (own > range * range) {
			return false;
		}
		for (net.minecraft.world.entity.player.Player other : minecraft.level.players()) {
			if (other != minecraft.player && other.position().distanceToSqr(pos) < own) {
				return false;
			}
		}
		return true;
	}

	/** Every client tick: ash under the red sky, and the preview outline redrawn twice a second while it lasts. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = minecraft.level.getRandom();
		if (KehancuranClient.skyActive()) {
			Vec3 eye = minecraft.player.position();
			int n = EffectQuality.minimal() ? 1 : EffectQuality.maximal() ? 4 : 2;
			for (int i = 0; i < n; i++) {
				Vec3 at = eye.add(random.nextGaussian() * 10.0D, 4.0D + random.nextDouble() * 8.0D, random.nextGaussian() * 10.0D);
				CommonFx.add(random.nextInt(3) == 0 ? ParticleTypes.ASH : ParticleTypes.CRIMSON_SPORE, at, new Vec3(0.0D, -0.02D, 0.0D), false);
			}
			Vec3 centre = KehancuranClient.skyCentre();
			if (minecraft.level.getGameTime() % 10L == 0L && centre.distanceToSqr(eye) < 96.0D * 96.0D) {
				CommonFx.pillar(centre, RED, 24.0F);
			}
		}
		if (Util.getMillis() < KehancuranClient.previewUntil && minecraft.level.getGameTime() % 10L == 0L
				&& KehancuranClient.previewPos.distanceToSqr(minecraft.player.position()) < 128.0D * 128.0D) {
			outline(KehancuranClient.previewPos, KehancuranClient.previewTarget, Shapes.Kind.byOrdinal(KehancuranClient.previewKind),
					KehancuranClient.previewSize);
		}
	}

	/** A dotted red outline of the shape: rings, squares and lines (read-only, nothing touches a block). */
	private static void outline(Vec3 pos, Vec3 target, Shapes.Kind kind, float size) {
		float r = Math.max(1.0F, size);
		int points = Math.max(16, Math.min(96, Math.round(r * 6.0F)));
		switch (kind) {
			case TRENCH -> {
				Vec3 delta = target.subtract(pos);
				Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
				Vec3 dir = flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize();
				Vec3 side = new Vec3(-dir.z, 0.0D, dir.x).scale(r + 0.5D);
				line(pos.add(side), target.add(side), RED);
				line(pos.subtract(side), target.subtract(side), RED);
				line(pos.add(side), pos.subtract(side), RED);
				line(target.add(side), target.subtract(side), RED);
			}
			case SPHERE -> {
				CommonFx.ring(pos, RED, r, points);
				vertical(pos, r, points, false);
				vertical(pos, r, points, true);
			}
			case DISC, COLUMN -> {
				CommonFx.ring(pos, RED, r, points);
				CommonFx.ring(target, DARK_RED, r, points);
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * 2.0D * i / 8.0D;
					Vec3 offset = new Vec3(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
					line(pos.add(offset), target.add(offset), DARK_RED);
				}
			}
			case WITHER -> {
				CommonFx.ring(pos, 0x6CFF8A, r, points);
				CommonFx.ring(pos, RED, r * 0.5F, Math.max(12, points / 2));
			}
			case PIT -> {
				square(pos, r + 0.5D, RED);
				square(target, r + 0.5D, DARK_RED);
				line(pos.add(r + 0.5D, 0.0D, r + 0.5D), target.add(r + 0.5D, 0.0D, r + 0.5D), DARK_RED);
				line(pos.add(-r - 0.5D, 0.0D, r + 0.5D), target.add(-r - 0.5D, 0.0D, r + 0.5D), DARK_RED);
				line(pos.add(r + 0.5D, 0.0D, -r - 0.5D), target.add(r + 0.5D, 0.0D, -r - 0.5D), DARK_RED);
				line(pos.add(-r - 0.5D, 0.0D, -r - 0.5D), target.add(-r - 0.5D, 0.0D, -r - 0.5D), DARK_RED);
			}
			case NINE -> {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						CommonFx.ring(pos.add(dx * NINE_SPACING, 0.0D, dz * NINE_SPACING), dx == 0 && dz == 0 ? RED : 0xFFD54A, r, Math.max(12, points / 2));
					}
				}
			}
		}
	}

	private static void line(Vec3 from, Vec3 to, int color) {
		Vec3 delta = to.subtract(from);
		int n = Math.max(2, Math.min(64, (int) Math.ceil(delta.length() * 1.5D)));
		n = Math.max(2, EffectQuality.scale(n));
		for (int i = 0; i <= n; i++) {
			CommonFx.add(CommonFx.dust(color, 0.9F), from.add(delta.scale((double) i / n)), Vec3.ZERO, false);
		}
	}

	private static void square(Vec3 centre, double half, int color) {
		line(centre.add(-half, 0.0D, -half), centre.add(half, 0.0D, -half), color);
		line(centre.add(half, 0.0D, -half), centre.add(half, 0.0D, half), color);
		line(centre.add(half, 0.0D, half), centre.add(-half, 0.0D, half), color);
		line(centre.add(-half, 0.0D, half), centre.add(-half, 0.0D, -half), color);
	}

	/** A vertical great circle of the sphere (in the x-y plane, or the z-y plane). */
	private static void vertical(Vec3 centre, float r, int points, boolean alongZ) {
		int n = Math.max(8, EffectQuality.scale(points));
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			double a = Math.cos(angle) * r;
			double y = Math.sin(angle) * r;
			Vec3 at = alongZ ? centre.add(0.0D, y, a) : centre.add(a, y, 0.0D);
			CommonFx.add(CommonFx.dust(DARK_RED, 0.8F), at, Vec3.ZERO, false);
		}
	}

	private KehancuranFx() {
	}
}
