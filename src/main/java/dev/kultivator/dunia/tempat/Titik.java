package dev.kultivator.dunia.tempat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;

/**
 * A life point in world coordinates (DESIGN 38.B.2.5): a bed, chair, work spot ({@code kerja:<alat>}), door, path node,
 * play spot, guard post, stall, building box ... read from the {@code kultivator_titik} key of a piece's NBT and
 * rotated/offset with the piece ({@link TitikTemplat}). {@code hadap} is the facing an NPC using it looks at.
 *
 * @param jenis point type (tools/tempat_lib.py JENIS_TITIK or {@code kerja:<alat>})
 * @param pos   world block the NPC stands in (box points: the min corner)
 * @param hadap horizontal facing
 * @param data  short string values ({@code urut}, {@code jalur}, {@code dx/dy/dz} of a box, {@code nama} ...)
 */
public record Titik(String jenis, BlockPos pos, Direction hadap, Map<String, String> data) {

	public Titik {
		pos = pos.immutable();
		data = Map.copyOf(data);
	}

	/** The tool of a {@code kerja:<alat>} point, or the empty string. */
	public String alat() {
		return jenis.startsWith("kerja:") ? jenis.substring(6) : "";
	}

	/** An integer data value, or {@code fallback}. */
	public int angka(String key, int fallback) {
		String v = data.get(key);
		if (v == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** The world box of a box point ({@code dx/dy/dz} already rotated by {@link TitikTemplat}); a 1-block box otherwise. */
	public BoundingBox kotak() {
		int dx = angka("dx", 0);
		int dy = angka("dy", 0);
		int dz = angka("dz", 0);
		return new BoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
	}
}
