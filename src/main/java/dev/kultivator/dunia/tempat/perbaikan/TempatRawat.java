package dev.kultivator.dunia.tempat.perbaikan;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One cared-for place (DESIGN 38.R.1): a structure start of tag {@code #kultivator:tempat_dirawat} (or one of the old
 * town tags) seen in a loaded chunk, with the boxes of its generated pieces. Its volume (the union of the piece boxes) is
 * what the blueprint records and the scanner compares. Kept in {@link PerbaikanData}, so the place stays known while its
 * start chunk is unloaded. Server thread only.
 */
public final class TempatRawat {

	/** Pieces kept per place at most (a capital has a few hundred); beyond this the volume is the start's whole box. */
	public static final int MAKS_POTONGAN = 768;

	public final long kunci;
	public final ResourceKey<Level> dimensi;
	public final Identifier struktur;
	public final ChunkPos awal;
	public final BoundingBox kotak;
	public final List<BoundingBox> potongan;
	/** Chunk keys the volume touches. */
	public final long[] chunks;
	/** Site kind ({@code kota/kota_besar}, {@code kampung/desa}, ...). */
	public String jenis;
	/** The published site id (town / reputation id); a stable id of its own until the site index knows the place. */
	public long situsId;
	/** Chunks whose blueprint slice is recorded. */
	public final LongOpenHashSet terekam = new LongOpenHashSet();
	/** Canal boxes of a water city (38.T.11 amendment of rule 6: canal water may come back as a source). */
	public final List<BoundingBox> kanal = new ArrayList<>();
	/** Game time the canal boxes were last asked from the place index (0 = never). */
	public long kanalDitanya;

	public TempatRawat(long kunci, ResourceKey<Level> dimensi, Identifier struktur, ChunkPos awal, BoundingBox kotak, List<BoundingBox> potongan,
			String jenis, long situsId) {
		this.kunci = kunci;
		this.dimensi = dimensi;
		this.struktur = struktur;
		this.awal = awal;
		this.kotak = kotak;
		this.potongan = List.copyOf(potongan.size() > MAKS_POTONGAN || potongan.isEmpty() ? List.of(kotak) : potongan);
		this.jenis = jenis;
		this.situsId = situsId;
		this.chunks = chunksOf(this.potongan);
	}

	/** The key of a start: dimension, structure and start chunk together. */
	public static long kunciDari(ResourceKey<Level> dimensi, Identifier struktur, ChunkPos awal) {
		long h = 0x9E3779B97F4A7C15L;
		h = h * 31L + dimensi.identifier().hashCode();
		h = h * 31L + struktur.hashCode();
		h ^= awal.pack() * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		return h == 0L ? 1L : h;
	}

	private static long[] chunksOf(List<BoundingBox> boxes) {
		LongOpenHashSet set = new LongOpenHashSet();
		for (BoundingBox b : boxes) {
			for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
				for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
					set.add(ChunkPos.pack(cx, cz));
				}
			}
		}
		return set.toLongArray();
	}

	/** The profile of this place's kind. */
	public JenisTempat.Profil profil() {
		return JenisTempat.of(jenis);
	}

	/** True when {@code pos} lies in a piece box of this place. */
	public boolean berisi(BlockPos pos) {
		return berisi(pos.getX(), pos.getY(), pos.getZ());
	}

	public boolean berisi(int x, int y, int z) {
		if (!kotak.isInside(x, y, z)) {
			return false;
		}
		for (BoundingBox b : potongan) {
			if (b.isInside(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	/** True when column {@code (x, z)} lies in a piece box (any height). */
	public boolean kolom(int x, int z) {
		if (x < kotak.minX() || x > kotak.maxX() || z < kotak.minZ() || z > kotak.maxZ()) {
			return false;
		}
		for (BoundingBox b : potongan) {
			if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) {
				return true;
			}
		}
		return false;
	}

	/** The piece boxes that touch chunk {@code (cx, cz)}. */
	public List<BoundingBox> potonganDi(int cx, int cz) {
		int x0 = cx << 4;
		int z0 = cz << 4;
		List<BoundingBox> out = new ArrayList<>();
		for (BoundingBox b : potongan) {
			if (b.maxX() >= x0 && b.minX() <= x0 + 15 && b.maxZ() >= z0 && b.minZ() <= z0 + 15) {
				out.add(b);
			}
		}
		return out;
	}

	/** True when {@code pos} is in a canal box of this place. */
	public boolean diKanal(BlockPos pos) {
		for (BoundingBox b : kanal) {
			if (b.isInside(pos)) {
				return true;
			}
		}
		return false;
	}

	/** The centre of the place's box (for distances). */
	public BlockPos pusat() {
		return kotak.getCenter();
	}

	/** Horizontal distance squared from the place box to {@code pos} (0 inside). */
	public double jarakKotakSqr(BlockPos pos) {
		double dx = Math.max(0, Math.max(kotak.minX() - pos.getX(), pos.getX() - kotak.maxX()));
		double dz = Math.max(0, Math.max(kotak.minZ() - pos.getZ(), pos.getZ() - kotak.maxZ()));
		return dx * dx + dz * dz;
	}

	// ================================================================== saving

	CompoundTag simpan() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("k", kunci);
		tag.putString("dim", dimensi.identifier().toString());
		tag.putString("s", struktur.toString());
		tag.putLong("awal", awal.pack());
		tag.put("kotak", box(kotak));
		ListTag list = new ListTag();
		for (BoundingBox b : potongan) {
			list.add(box(b));
		}
		tag.put("p", list);
		tag.putString("jenis", jenis);
		tag.putLong("situs", situsId);
		tag.put("rek", new LongArrayTag(terekam.toLongArray()));
		return tag;
	}

	static @Nullable TempatRawat muat(CompoundTag tag) {
		Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
		Identifier s = Identifier.tryParse(tag.getStringOr("s", ""));
		BoundingBox kotak = box(tag.get("kotak"));
		if (dim == null || s == null || kotak == null) {
			return null;
		}
		List<BoundingBox> pieces = new ArrayList<>();
		for (Tag t : tag.getListOrEmpty("p")) {
			BoundingBox b = box(t);
			if (b != null) {
				pieces.add(b);
			}
		}
		TempatRawat t = new TempatRawat(tag.getLongOr("k", 0L), ResourceKey.create(Registries.DIMENSION, dim), s,
				ChunkPos.unpack(tag.getLongOr("awal", 0L)), kotak, pieces, tag.getStringOr("jenis", ""), tag.getLongOr("situs", 0L));
		for (long c : tag.getLongArray("rek").orElse(new long[0])) {
			t.terekam.add(c);
		}
		return t;
	}

	static IntArrayTag box(BoundingBox b) {
		return new IntArrayTag(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
	}

	static @Nullable BoundingBox box(@Nullable Tag tag) {
		if (tag instanceof IntArrayTag array) {
			int[] v = array.getAsIntArray();
			if (v.length == 6) {
				return new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5]);
			}
		}
		return null;
	}
}
