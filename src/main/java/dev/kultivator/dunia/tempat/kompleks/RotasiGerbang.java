package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The geometry of an NPC sect's mountain gate as world generation really places it (DESIGN 38.C.3.1), computed from the
 * seed alone so the compound behind it can be planned without reading chunks. Facts (26.3, reference/mcsrc):
 * <ul>
 *   <li>the gate structures {@code kultivator:sekte_gerbang_<band>} are one-piece jigsaws ({@code size 1}) with a constant
 *       {@code start_height}; {@code JigsawStructure.findGenerationPoint} samples the constant height without the random,
 *       so the first draw of the structure random ({@code WorldgenRandom(LegacyRandomSource(0))} with
 *       {@code setLargeFeatureSeed(seed, chunkX, chunkZ)}, {@code Structure.GenerationContext.makeRandom}) is
 *       {@code Rotation.getRandom} in {@code JigsawPlacement.addPieces}: {@code Rotation.values()[nextInt(4)]};</li>
 *   <li>the piece sits at the chunk's MIN corner and rotates about it ({@code StructureTemplate} pivot 0);</li>
 *   <li>its ground is {@code getFirstFreeHeight(box centre, WORLD_SURFACE_WG)} and template {@code y} lands at
 *       {@code ground - 1 + y} (single pool elements have ground level delta 1);</li>
 *   <li>the start is kept only when the biome at the box centre (at the ground) is in
 *       {@code #kultivator:has_structure/sekte_gerbang} ({@code Structure.findValidGenerationPoint}).</li>
 * </ul>
 * In the gate templates (tools/gen_sekte_gerbang.py) the entrance stairs face local south (+z) and the main hall stands at
 * local north: the compound lies <b>behind</b> the gate, toward local -z. {@code uji kompleks rotasi} compares this
 * computation with the gate's real structure start.
 */
public final class RotasiGerbang {

	/** {@code #kultivator:has_structure/sekte_gerbang} (the gate structures' biome list). */
	public static final TagKey<Biome> BIOMA_GERBANG = TagKey.create(Registries.BIOME, Kultivator.id("has_structure/sekte_gerbang"));

	/** Template sizes per band (x, y, z): tools/gen_sekte_gerbang.py build_kecil / build_sedang / build_besar. */
	private static final int[][] UKURAN = {{25, 20, 33}, {37, 26, 45}, {47, 38, 48}};
	/** Template y of the rear courtyard floor per band (kecil/sedang terrace y 4, besar high court y 12). */
	private static final int[] LANTAI_BELAKANG = {4, 4, 12};

	/** The rotation world generation gives the gate start in chunk {@code (chunkX, chunkZ)}. */
	public static Rotation of(long seed, int chunkX, int chunkZ) {
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureSeed(seed, chunkX, chunkZ);
		return Rotation.values()[random.nextInt(Rotation.values().length)];
	}

	/** Template width (x) of a band's gate. */
	public static int lebar(int band) {
		return UKURAN[clampBand(band)][0];
	}

	/** Template depth (z) of a band's gate. */
	public static int dalam(int band) {
		return UKURAN[clampBand(band)][2];
	}

	/** Template height of a band's gate. */
	public static int tinggi(int band) {
		return UKURAN[clampBand(band)][1];
	}

	/** Template y of the rear courtyard floor block of a band's gate. */
	public static int lantaiBelakang(int band) {
		return LANTAI_BELAKANG[clampBand(band)];
	}

	private static int clampBand(int band) {
		return Math.max(0, Math.min(2, band));
	}

	/**
	 * The placed gate of one plan entry: origin (chunk min corner), rotation, XZ box exactly as
	 * {@code PoolElementStructurePiece.getBoundingBox} gives it, and its centre as {@code JigsawPlacement} computes it
	 * (integer division of min + max, as vanilla).
	 */
	public record Gerbang(GerbangPlan.Entry entry, int asalX, int asalZ, Rotation putar, BoundingBox kotak, int pusatX, int pusatZ) {

		/** World x/z of template-local point {@code (x, z)} of this gate. */
		public int[] dunia(int x, int z) {
			int[] r = TataLetakStructure.rotate(putar, x, z);
			return new int[]{asalX + r[0], asalZ + r[1]};
		}

		/** The gate's ground: the first free height at the box centre ({@code WORLD_SURFACE_WG}). */
		public int tanah(PutusanTanah.Sumber sumber) {
			return sumber.surface(pusatX, pusatZ);
		}

		/** World y of the rear courtyard floor block. */
		public int lantaiBelakangY(PutusanTanah.Sumber sumber) {
			return tanah(sumber) - 1 + lantaiBelakang(entry.band());
		}

		/** True when the gate start passes its biome check (so the gate is really born). */
		public boolean biomaSah(PutusanTanah.Sumber sumber) {
			return sumber.biome(pusatX, tanah(sumber), pusatZ).is(BIOMA_GERBANG);
		}
	}

	/** The gate of plan entry {@code e} for world seed {@code seed}. */
	public static Gerbang gerbang(long seed, GerbangPlan.Entry e) {
		int ox = e.chunkX() << 4;
		int oz = e.chunkZ() << 4;
		Rotation r = of(seed, e.chunkX(), e.chunkZ());
		int sx = lebar(e.band());
		int sz = dalam(e.band());
		int[] a = TataLetakStructure.rotate(r, 0, 0);
		int[] b = TataLetakStructure.rotate(r, sx - 1, sz - 1);
		int minX = ox + Math.min(a[0], b[0]);
		int maxX = ox + Math.max(a[0], b[0]);
		int minZ = oz + Math.min(a[1], b[1]);
		int maxZ = oz + Math.max(a[1], b[1]);
		BoundingBox box = new BoundingBox(minX, -64, minZ, maxX, 320, maxZ);
		return new Gerbang(e, ox, oz, r, box, (minX + maxX) / 2, (minZ + maxZ) / 2);
	}

	private RotasiGerbang() {
	}
}
