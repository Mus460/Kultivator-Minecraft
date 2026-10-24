package dev.kultivator.dunia.tempat.perbaikan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The blueprint slice of one cared-for place in one chunk (DESIGN 38.R.1, "what should be"): the real blocks recorded
 * once, when the chunk first loaded after the place was known, so random processors (moss, cracks, rot) and terrain
 * adaptation are exactly what the world had. Layout: a 256-bit column mask (the place's piece boxes in this chunk), a
 * height band {@code minY..maxY}, a palette (at most 4096 states) and one palette index per masked column and height,
 * ordered y-major ({@code (y - minY) * columns + column rank}). Saved with run-length coding. Block entities keep only
 * what repairs may put back: the loot table of an unopened chest or barrel, the full tag of a sign or banner.
 */
public final class Cetak {

	/** Highest band a slice keeps (taller pieces keep their lowest 160 blocks). */
	public static final int MAKS_TINGGI = 160;
	public static final int MAKS_PALET = 4096;
	/** Palette index meaning "not recorded" (palette full). */
	public static final short TIDAK = (short) -1;

	public final long kunci;
	public final long chunk;
	public final int minY;
	public final int maxY;
	/** Bit {@code (z & 15) * 16 + (x & 15)} set = column recorded. */
	public final long[] mask;
	final BlockState[] palet;
	final short[] isi;
	/** Column bit to its rank among the set bits (-1 not masked). */
	private final short[] rank;
	/** Rank to its column bit. */
	private final short[] bitRank;
	private final int kolom;
	/** Voxel index to its block-entity tag. */
	final Map<Integer, CompoundTag> be;

	Cetak(long kunci, long chunk, int minY, int maxY, long[] mask, BlockState[] palet, short[] isi, Map<Integer, CompoundTag> be) {
		this.kunci = kunci;
		this.chunk = chunk;
		this.minY = minY;
		this.maxY = maxY;
		this.mask = mask;
		this.palet = palet;
		this.isi = isi;
		this.be = be;
		this.rank = new short[256];
		short[] bits = new short[256];
		int r = 0;
		for (int bit = 0; bit < 256; bit++) {
			if ((mask[bit >> 6] >>> (bit & 63) & 1L) != 0L) {
				bits[r] = (short) bit;
				rank[bit] = (short) r++;
			} else {
				rank[bit] = -1;
			}
		}
		this.kolom = r;
		this.bitRank = java.util.Arrays.copyOf(bits, r);
	}

	/** Recorded columns. */
	public int kolom() {
		return kolom;
	}

	/** Recorded voxels. */
	public int ukuran() {
		return isi.length;
	}

	/** The voxel index of a world position, or -1 when this slice does not record it. */
	public int indeks(int x, int y, int z) {
		if (y < minY || y > maxY) {
			return -1;
		}
		int r = rank[((z & 15) << 4) | (x & 15)];
		if (r < 0) {
			return -1;
		}
		return (y - minY) * kolom + r;
	}

	/** The recorded state at a world position, or null (not recorded). */
	public @Nullable BlockState di(int x, int y, int z) {
		int i = indeks(x, y, z);
		if (i < 0) {
			return null;
		}
		short p = isi[i];
		return p == TIDAK ? null : palet[p & 0xFFFF];
	}

	public @Nullable BlockState di(BlockPos pos) {
		return di(pos.getX(), pos.getY(), pos.getZ());
	}

	/** The recorded block-entity tag at a world position (loot table or sign text), or null. */
	public @Nullable CompoundTag beDi(BlockPos pos) {
		int i = indeks(pos.getX(), pos.getY(), pos.getZ());
		return i < 0 ? null : be.get(i);
	}

	/** The world position of voxel {@code i}. */
	public BlockPos posisi(int i) {
		int bit = bitRank[i % kolom];
		return new BlockPos((net.minecraft.world.level.ChunkPos.getX(chunk) << 4) + (bit & 15), minY + i / kolom,
				(net.minecraft.world.level.ChunkPos.getZ(chunk) << 4) + (bit >> 4));
	}

	/** Writes the world position of voxel {@code i} into {@code out} (no allocation, for the scanner). */
	public void posisi(int i, BlockPos.MutableBlockPos out) {
		int bit = bitRank[i % kolom];
		out.set((net.minecraft.world.level.ChunkPos.getX(chunk) << 4) + (bit & 15), minY + i / kolom,
				(net.minecraft.world.level.ChunkPos.getZ(chunk) << 4) + (bit >> 4));
	}

	/** The recorded state of voxel {@code i}, or null (not recorded). */
	public @Nullable BlockState voxel(int i) {
		short p = isi[i];
		return p == TIDAK ? null : palet[p & 0xFFFF];
	}

	/** The block-entity tag of voxel {@code i}, or null. */
	public @Nullable CompoundTag beVoxel(int i) {
		return be.get(i);
	}

	// ================================================================== building

	/** Collects voxels while recording; {@link #selesai} makes the slice. */
	static final class Pembuat {
		final long kunci;
		final long chunk;
		final int minY;
		final int maxY;
		final long[] mask;
		final int kolom;
		final short[] isi;
		final List<BlockState> palet = new ArrayList<>();
		final Map<BlockState, Short> indeksPalet = new HashMap<>();
		final Map<Integer, CompoundTag> be = new HashMap<>();

		Pembuat(long kunci, long chunk, int minY, int maxY, long[] mask) {
			this.kunci = kunci;
			this.chunk = chunk;
			this.minY = minY;
			this.maxY = maxY;
			this.mask = mask;
			int n = 0;
			for (long m : mask) {
				n += Long.bitCount(m);
			}
			this.kolom = n;
			this.isi = new short[Math.max(0, (maxY - minY + 1) * n)];
		}

		void taruh(int index, BlockState state) {
			Short p = indeksPalet.get(state);
			if (p == null) {
				if (palet.size() >= MAKS_PALET) {
					isi[index] = TIDAK;
					return;
				}
				p = (short) palet.size();
				palet.add(state);
				indeksPalet.put(state, p);
			}
			isi[index] = p;
		}

		Cetak selesai() {
			return new Cetak(kunci, chunk, minY, maxY, mask, palet.toArray(new BlockState[0]), isi, be);
		}
	}

	// ================================================================== saving (run-length coded)

	CompoundTag simpan() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("k", kunci);
		tag.putLong("c", chunk);
		tag.putInt("y0", minY);
		tag.putInt("y1", maxY);
		tag.put("m", new LongArrayTag(mask.clone()));
		ListTag p = new ListTag();
		for (BlockState s : palet) {
			p.add(NbtUtils.writeBlockState(s));
		}
		tag.put("p", p);
		// runs of (value, length): a slice of a street is mostly air above one floor
		List<Integer> runs = new ArrayList<>();
		int i = 0;
		while (i < isi.length) {
			short v = isi[i];
			int j = i + 1;
			while (j < isi.length && isi[j] == v && j - i < 65535) {
				j++;
			}
			runs.add((v & 0xFFFF) | ((j - i) << 16));
			i = j;
		}
		int[] packed = new int[runs.size()];
		for (int n = 0; n < packed.length; n++) {
			packed[n] = runs.get(n);
		}
		tag.put("r", new IntArrayTag(packed));
		if (!be.isEmpty()) {
			ListTag list = new ListTag();
			for (Map.Entry<Integer, CompoundTag> e : be.entrySet()) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("i", e.getKey());
				entry.put("t", e.getValue());
				list.add(entry);
			}
			tag.put("be", list);
		}
		return tag;
	}

	static @Nullable Cetak muat(CompoundTag tag, HolderGetter<Block> blocks) {
		long[] mask = tag.getLongArray("m").orElse(null);
		if (mask == null || mask.length != 4) {
			return null;
		}
		ListTag p = tag.getListOrEmpty("p");
		BlockState[] palet = new BlockState[p.size()];
		for (int n = 0; n < palet.length; n++) {
			palet[n] = p.get(n) instanceof CompoundTag entry ? NbtUtils.readBlockState(blocks, entry) : Blocks.AIR.defaultBlockState();
		}
		int minY = tag.getIntOr("y0", 0);
		int maxY = tag.getIntOr("y1", -1);
		int kolom = 0;
		for (long m : mask) {
			kolom += Long.bitCount(m);
		}
		int size = Math.max(0, (maxY - minY + 1) * kolom);
		short[] isi = new short[size];
		int at = 0;
		for (int run : tag.getIntArray("r").orElse(new int[0])) {
			short v = (short) (run & 0xFFFF);
			int len = run >>> 16;
			for (int n = 0; n < len && at < size; n++) {
				short value = (v != TIDAK && (v & 0xFFFF) >= palet.length) ? TIDAK : v;
				isi[at++] = value;
			}
		}
		if (at != size) {
			return null;
		}
		Map<Integer, CompoundTag> be = new HashMap<>();
		for (Tag t : tag.getListOrEmpty("be")) {
			if (t instanceof CompoundTag entry) {
				be.put(entry.getIntOr("i", -1), entry.getCompoundOrEmpty("t"));
			}
		}
		return new Cetak(tag.getLongOr("k", 0L), tag.getLongOr("c", 0L), minY, maxY, mask, palet, isi, be);
	}
}
