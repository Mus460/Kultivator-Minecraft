package dev.kultivator.dunia.tempat.perbaikan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * What counts as damage when the blueprint and the world differ (DESIGN 38.R.2, 38.R.3 rules 6-9). Only the block
 * matters, not its properties (a door opened, a lantern lit, a cauldron filled are life, not damage). Never damage:
 * flowing fluids, leaves and the logs of trees (trees are not replanted), crops harvested or grown (the farmers' work),
 * soft earth turning into soft earth (grass, dirt, path, farmland), snow and ice coming and going, grass and other
 * replaceable plants. Water comes back as a source only when the record held a source enclosed by at least four solid
 * blocks, or inside a canal box of a water city (38.T.11). Lava never comes back.
 */
public final class Banding {

	/** Repair categories, in work order (38.R.3: support first, then walls, roof, glass, doors, decoration, plants). */
	public static final int K_API = 0;
	public static final int K_TOPANG = 1;
	public static final int K_DINDING = 2;
	public static final int K_ATAP = 3;
	public static final int K_KACA = 4;
	public static final int K_PINTU = 5;
	public static final int K_HIAS = 6;
	public static final int K_TANAMAN = 7;
	public static final int K_BUANG = 8;

	/**
	 * The state to put back at {@code pos}, or null when the difference is not damage. {@code rec} is the recorded state,
	 * {@code now} the world's; {@code cetak} answers neighbour questions of the record; {@code t} the place (canals).
	 */
	public static @Nullable BlockState rusak(BlockState rec, BlockState now, Cetak cetak, BlockPos pos, TempatRawat t) {
		if (rec == now) {
			return null;
		}
		boolean fire = now.getBlock() instanceof BaseFireBlock;
		if (!fire && rec.getBlock() == now.getBlock()) {
			return null;
		}
		if (!rec.getFluidState().isEmpty() && !rec.getFluidState().isSource()) {
			return null;
		}
		if (rec.getFluidState().is(FluidTags.LAVA) || rec.is(Blocks.LAVA)) {
			return null;
		}
		if (air(rec)) {
			// rule 6 (+ 38.T.11 canals): water comes back only as an enclosed source or inside a canal box
			if (air(now) || now.is(Blocks.ICE) || !rec.getFluidState().isSource() || !(t.diKanal(pos) || tertutup(cetak, pos))) {
				return null;
			}
			return rec;
		}
		if (rec.is(BlockTags.LEAVES) || pohon(rec, cetak, pos)) {
			return null;
		}
		if (tanaman(rec)) {
			if (now.isAir() || tanaman(now) || now.is(BlockTags.REPLACEABLE)) {
				return null;
			}
			return muda(rec);
		}
		if (tanahLunak(rec) && tanahLunak(now)) {
			return null;
		}
		if (salju(rec) && (now.isAir() || salju(now) || air(now))) {
			return null;
		}
		if (rec.isAir() && (salju(now) || !fire && now.is(BlockTags.REPLACEABLE))) {
			return null;
		}
		if (rec.is(BlockTags.REPLACEABLE) && (now.isAir() || now.is(BlockTags.REPLACEABLE)) && !fire) {
			return null;
		}
		if (!now.getFluidState().isEmpty() && !now.getFluidState().isSource() && rec.isAir()) {
			// water that ran in from a broken wall goes when the wall comes back; it is not damage by itself
			return null;
		}
		return muda(rec);
	}

	/** The work category of an operation that turns {@code found} into {@code target}. */
	public static int kategori(BlockState target, BlockState found) {
		if (found.getBlock() instanceof BaseFireBlock) {
			return K_API;
		}
		if (target.isAir()) {
			return K_BUANG;
		}
		Block b = target.getBlock();
		if (tanaman(target) || b instanceof VegetationBlock) {
			return K_TANAMAN;
		}
		if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) {
			return K_PINTU;
		}
		if (NilaiBlok.KACA.equals(NilaiBlok.kelompok(target))) {
			return K_KACA;
		}
		if (b instanceof StairBlock || b instanceof SlabBlock) {
			return K_ATAP;
		}
		if (isFull(target)) {
			return K_TOPANG;
		}
		if (target.isSolid()) {
			return K_DINDING;
		}
		return K_HIAS;
	}

	/** The work order key: category, then height (support from below, 38.R.3 rule 7), then position. */
	public static int urut(BlockState target, BlockState found, BlockPos pos, int minY) {
		int cat = kategori(target, found);
		int y = Math.max(0, Math.min(1023, pos.getY() - minY));
		return cat << 20 | y << 10 | (Math.floorMod(pos.getX() * 31 + pos.getZ(), 1024));
	}

	/** A gravity block (sand, gravel, concrete powder) needs its support restored first (rule 7). */
	public static boolean butuhTopang(BlockState target) {
		return target.getBlock() instanceof FallingBlock;
	}

	private static boolean isFull(BlockState state) {
		return state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** Crops come back at age 0 (rule 9); everything else as recorded. */
	static BlockState muda(BlockState rec) {
		if (rec.getBlock() instanceof CropBlock crop) {
			return crop.getStateForAge(0);
		}
		for (Property<?> property : rec.getProperties()) {
			if (property == BlockStateProperties.AGE_1 || property == BlockStateProperties.AGE_2 || property == BlockStateProperties.AGE_3
					|| property == BlockStateProperties.AGE_4 || property == BlockStateProperties.AGE_5 || property == BlockStateProperties.AGE_7
					|| property == BlockStateProperties.AGE_15 || property == BlockStateProperties.AGE_25) {
				return rec.setValue((IntegerProperty) property, 0);
			}
		}
		return rec;
	}

	private static boolean tanaman(BlockState state) {
		return state.is(BlockTags.CROPS) || state.getBlock() instanceof StemBlock || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COCOA)
				|| state.is(Blocks.NETHER_WART);
	}

	private static boolean tanahLunak(BlockState state) {
		return state.is(BlockTags.DIRT) || state.getBlock() instanceof FarmlandBlock || state.is(Blocks.DIRT_PATH);
	}

	private static boolean salju(BlockState state) {
		return state.getBlock() instanceof SnowLayerBlock || state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.ICE) || state.is(Blocks.POWDER_SNOW);
	}

	private static boolean air(BlockState state) {
		return state.is(Blocks.WATER) || state.getFluidState().is(FluidTags.WATER) && state.getBlock() == Blocks.WATER;
	}

	/** A log with at least two recorded leaves around it is part of a tree (trees are not replanted, rule 9). */
	private static boolean pohon(BlockState rec, Cetak cetak, BlockPos pos) {
		if (!rec.is(BlockTags.LOGS)) {
			return false;
		}
		int leaves = 0;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			BlockState s = cetak.di(n);
			if (s != null && s.is(BlockTags.LEAVES) && ++leaves >= 2) {
				return true;
			}
		}
		return false;
	}

	/** Rule 6: a recorded water source with at least four solid recorded neighbours. */
	private static boolean tertutup(Cetak cetak, BlockPos pos) {
		int solid = 0;
		for (Direction d : Direction.values()) {
			BlockState s = cetak.di(pos.relative(d));
			if (s != null && s.isSolid()) {
				solid++;
			}
		}
		return solid >= 4;
	}

	private Banding() {
	}
}
