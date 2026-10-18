package dev.kultivator.client.sectbuild;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.sect.build.api.SiteView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client mist of a sect (DESIGN 21.16), from the status push, within 64 blocks and off at Minimal quality:
 * <ul>
 * <li>the thin <b>work mist</b> ring around the open component at its working layer, which also softens the half-built
 * look;</li>
 * <li>the <b>cloud sea</b> of templates 6-9 once the late tiers stand: soft white motes hovering at valley level inside the
 * footprint (snow or concrete cloud blocks were rejected because they read as drifts, DESIGN 21.26).</li>
 * </ul>
 * Game design.
 */
public final class MistFx {

	public static final double RANGE = 64.0D;
	/** The cloud sea starts at this template and tier. */
	public static final int CLOUD_TEMPLATE = 6;
	public static final int CLOUD_TIER = 8;
	private static final int MIST = 0xF2F6F8;

	private static int tick;

	static void tick(Minecraft minecraft) {
		if (EffectQuality.minimal() || minecraft.player == null) {
			return;
		}
		CompoundTag status = BuildClient.status();
		if (status.isEmpty()) {
			return;
		}
		tick++;
		RandomSource random = CommonFx.random();
		Vec3 eye = minecraft.player.position();
		workMist(status, eye, random);
		cloudSea(status, eye, random);
	}

	private static void workMist(CompoundTag status, Vec3 eye, RandomSource random) {
		if (status.getIntOr("status", SiteView.STATUS_RUNNING) != SiteView.STATUS_RUNNING || tick % 4 != 0) {
			return;
		}
		int[] box = SiteHologram.box(status.getCompoundOrEmpty("open"), "box");
		if (box == null) {
			return;
		}
		int[] work = status.getIntArray("work").orElse(null);
		double y = work != null && work.length == 3 ? work[1] + 0.3D : box[1] + 1.0D;
		double cx = (box[0] + box[3] + 1) / 2.0D;
		double cz = (box[2] + box[5] + 1) / 2.0D;
		if (eye.distanceToSqr(cx, y, cz) > RANGE * RANGE) {
			return;
		}
		double rx = (box[3] + 1 - box[0]) / 2.0D + 0.6D;
		double rz = (box[5] + 1 - box[2]) / 2.0D + 0.6D;
		DustParticleOptions dust = CommonFx.dust(MIST, 2.2F);
		int n = EffectQuality.scale(6);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(dust, cx + Math.cos(angle) * rx, y + random.nextDouble() * 0.4D, cz + Math.sin(angle) * rz, 0.0D, 0.004D, 0.0D);
		}
		// The builders' gold qi threads to the layer.
		int[] builders = status.getIntArray("builders").orElse(new int[0]);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && tick % 12 == 0) {
			for (int id : builders) {
				net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(id);
				if (entity != null) {
					Vec3 hands = entity.position().add(0.0D, 1.0D, 0.0D);
					Vec3 layer = new Vec3(work != null ? work[0] + 0.5D : cx, y, work != null ? work[2] + 0.5D : cz);
					CommonFx.beam(hands, layer, 0xFFD54A, 0.02F);
				}
			}
		}
	}

	private static void cloudSea(CompoundTag status, Vec3 eye, RandomSource random) {
		if (status.getIntOr("t", 0) < CLOUD_TEMPLATE || status.getIntOr("tier", 0) < CLOUD_TIER || tick % 2 != 0) {
			return;
		}
		int[] fp = SiteHologram.box(status, "fp");
		if (fp == null) {
			return;
		}
		double valley = status.getIntOr("plaza_y", (int) eye.y) - 3.0D;
		int n = EffectQuality.scale(3);
		DustParticleOptions dust = CommonFx.dust(MIST, 3.5F);
		for (int i = 0; i < n; i++) {
			double x = eye.x + (random.nextDouble() - 0.5D) * RANGE * 1.5D;
			double z = eye.z + (random.nextDouble() - 0.5D) * RANGE * 1.5D;
			if (x < fp[0] || x > fp[3] + 1 || z < fp[2] || z > fp[5] + 1) {
				continue;
			}
			double y = valley + random.nextGaussian() * 1.2D;
			CommonFx.add(dust, x, y, z, random.nextGaussian() * 0.002D, 0.0D, random.nextGaussian() * 0.002D);
			if (EffectQuality.maximal() && random.nextInt(8) == 0) {
				CommonFx.add(ParticleTypes.CLOUD, x, y, z, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private MistFx() {
	}
}
