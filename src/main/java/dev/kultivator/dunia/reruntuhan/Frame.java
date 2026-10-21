package dev.kultivator.dunia.reruntuhan;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The frame of a structure piece as its anchor saw it (DESIGN 34.4.1): the anchor's block and the quarter turns the
 * piece was placed with. Template offsets (written by {@code tools/gen_dunia_reruntuhan.py} into the anchor's data, in
 * the template frame where the anchor's yaw is 0) are turned the way {@code StructureTemplate} turns blocks:
 * {@code CLOCKWISE_90} maps {@code (x, z)} to {@code (-z, x)} and adds 90 degrees of yaw (checked in mcsrc,
 * {@code StructureTemplate.transform} and {@code Entity.rotate}). Code-built layouts (the tower floors) use the same
 * frame with quarter 0.
 */
public record Frame(BlockPos origin, int quarter) {

	public Frame {
		origin = origin.immutable();
		quarter = Math.floorMod(quarter, 4);
	}

	/** The frame of an anchor (or any entity standing at a piece's reference point). */
	public static Frame of(Entity anchor) {
		return new Frame(anchor.blockPosition(), Math.round(anchor.getYRot() / 90.0F));
	}

	/** Turns a template offset into a world block. */
	public BlockPos block(int dx, int dy, int dz) {
		return switch (quarter) {
			case 1 -> origin.offset(-dz, dy, dx);
			case 2 -> origin.offset(-dx, dy, -dz);
			case 3 -> origin.offset(dz, dy, -dx);
			default -> origin.offset(dx, dy, dz);
		};
	}

	/** The centre of the bottom face of {@link #block}. */
	public Vec3 bottom(int dx, int dy, int dz) {
		return Vec3.atBottomCenterOf(block(dx, dy, dz));
	}

	/** A template offset (doubles) turned into a world point relative to the origin block's bottom centre. */
	public Vec3 point(double dx, double dy, double dz) {
		Vec3 base = Vec3.atBottomCenterOf(origin);
		return switch (quarter) {
			case 1 -> base.add(-dz, dy, dx);
			case 2 -> base.add(-dx, dy, -dz);
			case 3 -> base.add(dz, dy, -dx);
			default -> base.add(dx, dy, dz);
		};
	}

	/** A template direction turned into the world (y unchanged). */
	public Vec3 dir(double dx, double dz) {
		return switch (quarter) {
			case 1 -> new Vec3(-dz, 0.0D, dx);
			case 2 -> new Vec3(-dx, 0.0D, -dz);
			case 3 -> new Vec3(dz, 0.0D, -dx);
			default -> new Vec3(dx, 0.0D, dz);
		};
	}

	/** A template yaw (degrees) turned into the world. */
	public float yaw(float templateYaw) {
		return templateYaw + quarter * 90.0F;
	}

	public Vec3 centre() {
		return Vec3.atBottomCenterOf(origin);
	}

	/** Reads an int-triple offset {@code key} (index {@code i}) of an anchor's data; null when missing. */
	public static int[] triple(CompoundTag data, String key, int i) {
		int[] all = data.getIntArray(key).orElse(new int[0]);
		if (all.length < (i + 1) * 3) {
			return null;
		}
		return new int[]{all[i * 3], all[i * 3 + 1], all[i * 3 + 2]};
	}

	/** How many int triples {@code key} holds. */
	public static int triples(CompoundTag data, String key) {
		return data.getIntArray(key).orElse(new int[0]).length / 3;
	}

	/** Reads an int-pair offset {@code key} (index {@code i}); null when missing. */
	public static int[] pair(CompoundTag data, String key, int i) {
		int[] all = data.getIntArray(key).orElse(new int[0]);
		if (all.length < (i + 1) * 2) {
			return null;
		}
		return new int[]{all[i * 2], all[i * 2 + 1]};
	}

	public static int pairs(CompoundTag data, String key) {
		return data.getIntArray(key).orElse(new int[0]).length / 2;
	}
}
