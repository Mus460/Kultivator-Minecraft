package dev.kultivator.dunia.tempat;

import net.minecraft.world.level.block.Rotation;

/**
 * One district of a planned place (DESIGN 38.B.2.2): the start chunk of one {@code kultivator:tata_letak} structure.
 * {@code (pusatX, pusatZ)} is the district centre after the place rotation; its chunk is the start chunk. {@code dy} is
 * the extra floor height of the district over the place's ground ({@link Putusan#dasarY()}), used by terraced places
 * (sect compound rows 0/+10/+18, mountain city rows; 0 for the capital).
 *
 * @param kandidat the place candidate
 * @param id       district id inside the plan ({@code pasar}, {@code halaman}, ...)
 * @param pusatX   block x of the district centre (rotated)
 * @param pusatZ   block z of the district centre (rotated)
 * @param dy       floor offset over the place ground
 */
public record Distrik(Kandidat kandidat, String id, int pusatX, int pusatZ, int dy) {

	public Distrik(Kandidat kandidat, String id, int pusatX, int pusatZ) {
		this(kandidat, id, pusatX, pusatZ, 0);
	}

	public int chunkX() {
		return pusatX >> 4;
	}

	public int chunkZ() {
		return pusatZ >> 4;
	}

	/** The whole-place rotation of this district's lots. */
	public Rotation putar() {
		return Rotation.values()[kandidat.putar()];
	}
}
