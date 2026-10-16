package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Divine Sense ore pings on the caster's client. Particles inside opaque blocks are never visible, so the main cue is a
 * set of glowing motes about 1.5 blocks in front of the camera in the direction of each ore (closer ores: brighter and
 * nearer the eye, so they look bigger). Ore faces open to air also shimmer in the ore's colour. Mined ores drop out.
 */
public final class OrePingFx {

	/** Beyond this the direction motes are at their dimmest. */
	private static final double FAR = 24.0D;
	/** Exposed faces only shimmer for ores this close to the camera. */
	private static final double FACE_RANGE_SQ = 20.0D * 20.0D;

	private static final List<BlockPos> POSITIONS = new ArrayList<>();
	private static final List<Integer> COLORS = new ArrayList<>();
	private static long expiresAt;
	private static ClientLevel level;
	private static int cursor;

	public static void onOrePings(Payloads.OrePings pings) {
		clear();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		int count = Math.min(pings.positions().size(), pings.colors().size());
		for (int i = 0; i < count; i++) {
			POSITIONS.add(pings.positions().get(i).immutable());
			COLORS.add(pings.colors().get(i) & 0xFFFFFF);
		}
		level = minecraft.level;
		expiresAt = level.getGameTime() + Math.max(1, pings.ticks());
	}

	public static boolean active() {
		return !POSITIONS.isEmpty();
	}

	public static void clear() {
		POSITIONS.clear();
		COLORS.clear();
		level = null;
		cursor = 0;
	}

	/** Every client tick while pings are active. */
	public static void tick(Minecraft minecraft) {
		if (POSITIONS.isEmpty()) {
			return;
		}
		ClientLevel current = minecraft.level;
		if (current == null || current != level || current.getGameTime() > expiresAt) {
			clear();
			return;
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		RandomSource random = current.getRandom();
		long time = current.getGameTime();
		long left = expiresAt - time;
		float fade = left < 40L ? Math.max(0.2F, left / 40.0F) : 1.0F;
		directionMotes(camera, random, time, fade);
		faceMotes(current, camera, random, time, fade);
	}

	/** Motes in front of the camera pointing at the ores: the nearest few often, the rest in turn. */
	private static void directionMotes(Vec3 camera, RandomSource random, long time, float fade) {
		int size = POSITIONS.size();
		int nearest = Math.min(size, 4);
		if (time % 2L == 0L) {
			for (int i = 0; i < nearest; i++) {
				directionMote(camera, i, random, fade);
			}
		}
		if (size <= nearest) {
			return;
		}
		int budget = Math.min(size - nearest, EffectQuality.scale(8));
		for (int k = 0; k < budget; k++) {
			int index = nearest + (cursor + k) % (size - nearest);
			directionMote(camera, index, random, fade);
		}
		cursor = (cursor + budget) % Math.max(1, size - nearest);
	}

	private static void directionMote(Vec3 camera, int index, RandomSource random, float fade) {
		Vec3 center = Vec3.atCenterOf(POSITIONS.get(index));
		Vec3 delta = center.subtract(camera);
		double distance = delta.length();
		if (distance < 2.0D) {
			return;
		}
		Vec3 dir = delta.scale(1.0D / distance);
		float close = (float) Math.max(0.0D, Math.min(1.0D, 1.0D - distance / FAR));
		double place = 1.5D + (1.0D - close) * 1.0D;
		Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.015D);
		Vec3 start = camera.add(dir.scale(place)).add(jitter);
		Vec3 end = start.add(dir.scale(0.12D + 0.25D * close));
		int color = TechniqueShapes.dim(COLORS.get(index), (0.35F + 0.65F * close) * fade);
		int duration = 4 + Math.round(3.0F * close);
		CommonFx.add(new TrailParticleOption(end, color, duration), start, Vec3.ZERO, true);
	}

	/** Every ore in turn (about every 10 ticks): shimmer on faces open to air; ores that were mined are forgotten. */
	private static void faceMotes(ClientLevel current, Vec3 camera, RandomSource random, long time, float fade) {
		List<Integer> mined = null;
		for (int i = 0; i < POSITIONS.size(); i++) {
			if ((i + time) % 10L != 0L) {
				continue;
			}
			BlockPos pos = POSITIONS.get(i);
			if (current.getBlockState(pos).isAir()) {
				if (mined == null) {
					mined = new ArrayList<>();
				}
				mined.add(i);
				continue;
			}
			Vec3 center = Vec3.atCenterOf(pos);
			if (center.distanceToSqr(camera) > FACE_RANGE_SQ) {
				continue;
			}
			int color = TechniqueShapes.dim(COLORS.get(i), fade);
			for (Direction direction : Direction.values()) {
				if (current.getBlockState(pos.relative(direction)).isSolidRender() || !EffectQuality.chance(random, 0.6F)) {
					continue;
				}
				Vec3 normal = direction.getUnitVec3();
				Vec3 at = center.add(normal.scale(0.53D)).add(inPlane(normal, random));
				CommonFx.add(new TrailParticleOption(at.add(normal.scale(0.12D)), color, 10), at, Vec3.ZERO, false);
			}
		}
		if (mined != null) {
			for (int k = mined.size() - 1; k >= 0; k--) {
				int index = mined.get(k);
				POSITIONS.remove(index);
				COLORS.remove(index);
			}
			if (POSITIONS.isEmpty()) {
				clear();
			}
		}
	}

	/** A random offset within a block face whose normal is {@code normal}. */
	private static Vec3 inPlane(Vec3 normal, RandomSource random) {
		double a = (random.nextDouble() - 0.5D) * 0.8D;
		double b = (random.nextDouble() - 0.5D) * 0.8D;
		if (Math.abs(normal.x) > 0.5D) {
			return new Vec3(0.0D, a, b);
		}
		if (Math.abs(normal.y) > 0.5D) {
			return new Vec3(a, 0.0D, b);
		}
		return new Vec3(a, b, 0.0D);
	}

	private OrePingFx() {
	}
}
