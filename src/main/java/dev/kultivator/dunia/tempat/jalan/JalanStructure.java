package dev.kultivator.dunia.tempat.jalan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatKeepOut;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import dev.kultivator.dunia.tempat.worldgen.TempatTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code kultivator:jalan} structure type (DESIGN 38.C.6.2, 38.C.6.3). Two modes, chosen by the JSON:
 * <ul>
 *   <li><b>Road</b> (no {@code pos}; structure {@code jalan/jalan}): for every edge of {@link RencanaJalan} crossing its
 *       chunk whose two end places are born, a sample every 3 blocks along the polyline lays a tile ({@code ubin} pool of
 *       the road class, terrain_matching projection, so each column sits on the ground). Water columns
 *       ({@code WORLD_SURFACE_WG > OCEAN_FLOOR_WG}) get no tile: a water run of at most 28 blocks gets a rigid bridge
 *       ({@code jembatan} pool of the class, the piece whose length fits, 8-28) owned by the chunk of its first water
 *       sample, a longer run a ferry dock ({@code penyeberangan}) on each bank; a rise of 3-5 blocks between two
 *       samples gets a rigid stair piece ({@code tangga} pool); imperial roads get lantern posts ({@code lentera}) every
 *       24 blocks on both sides. Samples inside a place that is born are skipped (the place has its own streets).</li>
 *   <li><b>Post</b> ({@code pos} = a post kind; structures {@code pos/<jenis>}): each road-side post of that kind whose
 *       footprint centre lies in the chunk is laid as fixed lots ({@code lot}, like {@code kultivator:tata_letak}),
 *       turned so its front (template south) faces the road, its floor on the ground; never on water or a slope steeper
 *       than 10 blocks across the footprint.</li>
 * </ul>
 * All element choices are hashed from the seed and the position (not the context random), so the server knows which
 * variant stands where. Pools are listed by road class: index 0 kekaisaran, 1 desa, 2 setapak (a missing index uses the
 * last one). World generation of new chunks only.
 */
public final class JalanStructure extends Structure {

	/** Bridge lengths available ({@code jalan/jembatan_<kelas>_<n>} pools hold one element of each, in this order). */
	public static final int[] PANJANG_JEMBATAN = {8, 12, 16, 20, 24, 28};
	/** Deck height inside a bridge template (pillars below it). */
	public static final int DEK_Y = 12;
	/** Deck height inside a ferry dock template (piles below it). */
	public static final int DERMAGA_Y = 6;
	/** Samples a short water run may span before it counts as a wide river (3 blocks each). */
	private static final int RUN_MAKS = 10;

	public static final MapCodec<JalanStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			StructureTemplatePool.CODEC.listOf().optionalFieldOf("ubin", List.of()).forGetter(JalanStructure::ubin),
			StructureTemplatePool.CODEC.listOf().optionalFieldOf("jembatan", List.of()).forGetter(JalanStructure::jembatan),
			StructureTemplatePool.CODEC.listOf().optionalFieldOf("tangga", List.of()).forGetter(JalanStructure::tangga),
			StructureTemplatePool.CODEC.optionalFieldOf("penyeberangan").forGetter(JalanStructure::penyeberangan),
			StructureTemplatePool.CODEC.optionalFieldOf("lentera").forGetter(JalanStructure::lentera),
			Codec.STRING.optionalFieldOf("pos", "").forGetter(JalanStructure::pos),
			TataLetakStructure.Lot.CODEC.listOf().optionalFieldOf("lot", List.of()).forGetter(JalanStructure::lots))
			.apply(i, JalanStructure::new));

	private final List<Holder<StructureTemplatePool>> ubin;
	private final List<Holder<StructureTemplatePool>> jembatan;
	private final List<Holder<StructureTemplatePool>> tangga;
	private final Optional<Holder<StructureTemplatePool>> penyeberangan;
	private final Optional<Holder<StructureTemplatePool>> lentera;
	private final String pos;
	private final List<TataLetakStructure.Lot> lots;

	public JalanStructure(StructureSettings settings, List<Holder<StructureTemplatePool>> ubin,
			List<Holder<StructureTemplatePool>> jembatan, List<Holder<StructureTemplatePool>> tangga,
			Optional<Holder<StructureTemplatePool>> penyeberangan, Optional<Holder<StructureTemplatePool>> lentera, String pos,
			List<TataLetakStructure.Lot> lots) {
		super(settings);
		this.ubin = List.copyOf(ubin);
		this.jembatan = List.copyOf(jembatan);
		this.tangga = List.copyOf(tangga);
		this.penyeberangan = penyeberangan;
		this.lentera = lentera;
		this.pos = pos;
		this.lots = List.copyOf(lots);
	}

	public List<Holder<StructureTemplatePool>> ubin() {
		return ubin;
	}

	public List<Holder<StructureTemplatePool>> jembatan() {
		return jembatan;
	}

	public List<Holder<StructureTemplatePool>> tangga() {
		return tangga;
	}

	public Optional<Holder<StructureTemplatePool>> penyeberangan() {
		return penyeberangan;
	}

	public Optional<Holder<StructureTemplatePool>> lentera() {
		return lentera;
	}

	public String pos() {
		return pos;
	}

	public List<TataLetakStructure.Lot> lots() {
		return lots;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkGeneratorStructureState state = RencanaJalan.state(context.seed());
		if (state == null) {
			return Optional.empty();
		}
		PutusanTanah.Sumber sumber = PutusanTanah.Sumber.of(context);
		List<PoolElementStructurePiece> pieces = new ArrayList<>();
		ChunkPos chunk = context.chunkPos();
		if (pos.isEmpty()) {
			for (RencanaJalan.Sisi e : RencanaJalan.sisiDi(state, chunk.x(), chunk.z())) {
				if (RencanaJalan.sah(sumber, state, e)) {
					jalan(context, sumber, e, pieces);
				}
			}
		} else {
			for (RencanaJalan.Pos p : RencanaJalan.posDi(state, chunk.x(), chunk.z(), pos)) {
				RencanaJalan.Sisi e = RencanaJalan.sisi(state, p.sisiId(), p.x(), p.z());
				if (e != null && RencanaJalan.sah(sumber, state, e)) {
					bangunan(context, sumber, p, pieces);
				}
			}
		}
		if (pieces.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox first = pieces.getFirst().getBoundingBox();
		BlockPos start = new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ());
		return Optional.of(new GenerationStub(start, builder -> pieces.forEach(builder::addPiece)));
	}

	// ------------------------------------------------------------------------------------------------ road mode

	private void jalan(GenerationContext context, PutusanTanah.Sumber sumber, RencanaJalan.Sisi e, List<PoolElementStructurePiece> out) {
		ChunkPos chunk = context.chunkPos();
		int minX = chunk.getMinBlockX();
		int minZ = chunk.getMinBlockZ();
		double len = e.panjang();
		int n = (int) (len / 3.0D);
		if (n < 1) {
			return;
		}
		int[][] pts = new int[n + 1][];
		int k0 = Integer.MAX_VALUE;
		int k1 = Integer.MIN_VALUE;
		for (int k = 0; k <= n; k++) {
			pts[k] = e.titikPada(k * 3.0D);
			if (pts[k][0] >= minX && pts[k][0] < minX + 16 && pts[k][1] >= minZ && pts[k][1] < minZ + 16) {
				k0 = Math.min(k0, k);
				k1 = Math.max(k1, k);
			}
		}
		if (k0 > k1) {
			return;
		}
		Sampel sampel = new Sampel(sumber, pts);
		int kelas = e.kelas();
		int lebar = RencanaJalan.LEBAR[kelas];
		StructureTemplateManager manager = context.structureTemplateManager();
		int sea = context.chunkGenerator().getSeaLevel();
		for (int k = k0; k <= k1; k++) {
			int[] p = pts[k];
			if (!(p[0] >= minX && p[0] < minX + 16 && p[1] >= minZ && p[1] < minZ + 16)) {
				continue;
			}
			if (TempatKeepOut.dalamTempatLahir(context.seed(), p[0], p[1], sumber)) {
				continue;
			}
			if (sampel.air(k)) {
				if (k > 0 && !sampel.air(k - 1)) {
					jembatanAtauDermaga(context, sampel, e, k, sea, out);
				}
				continue;
			}
			if (k > 0 && sampel.air(k - 1)) {
				dermagaSeberang(context, sampel, e, k, sea, out);
			}
			int y = sampel.tanah(k);
			StructurePoolElement tile = pilih(pool(ubin, kelas), context.seed(), p[0], p[1], 0x7111L);
			if (tile != null) {
				tambah(out, manager, tile, new BlockPos(p[0] - lebar / 2, y, p[1] - lebar / 2), Rotation.NONE, 0);
			}
			if (kelas == SimpulJalan.KEKAISARAN && k % 8 == 4 && lentera.isPresent()) {
				StructurePoolElement post = pilih(lentera.get().value(), context.seed(), p[0], p[1], 0x1E47L);
				if (post != null) {
					double nx = -p[3] / 1000.0D;
					double nz = p[2] / 1000.0D;
					for (int side = -1; side <= 1; side += 2) {
						int lx = (int) Math.round(p[0] + nx * side * (lebar / 2.0D + 1.5D));
						int lz = (int) Math.round(p[1] + nz * side * (lebar / 2.0D + 1.5D));
						if (!sampel.airDi(lx, lz)) {
							tambah(out, manager, post, new BlockPos(lx, y, lz), Rotation.NONE, 0);
						}
					}
				}
			}
			if (k < n && !sampel.air(k + 1)) {
				int dh = sampel.tanah(k + 1) - y;
				if (Math.abs(dh) >= 3 && Math.abs(dh) <= 5) {
					tangga(context, sampel, e, dh > 0 ? k : k + 1, dh > 0 ? k + 1 : k, out);
				}
			}
		}
	}

	/** A short water run from sample {@code k} on: a bridge from bank {@code k - 1} to the far bank; else a ferry dock. */
	private void jembatanAtauDermaga(GenerationContext context, Sampel sampel, RencanaJalan.Sisi e, int k, int sea,
			List<PoolElementStructurePiece> out) {
		int j = -1;
		for (int t = k + 1; t <= Math.min(sampel.n(), k + RUN_MAKS); t++) {
			if (!sampel.air(t)) {
				j = t;
				break;
			}
		}
		int[] a = sampel.pts[k - 1];
		int panjang = j < 0 ? -1 : panjangJembatan(a, sampel.pts[j]);
		if (panjang > 0) {
			int[] b = sampel.pts[j];
			int deck = Math.clamp(Math.min(sampel.tanah(k - 1), sampel.tanah(j)), sea, sea + 6);
			jembatan(context, e, a, b, panjang, deck, out);
			return;
		}
		dermaga(context, e, a, sampel.pts[k], sea, out);
	}

	/** The far bank of a water run (sample {@code k} is land, {@code k - 1} water): a ferry dock when the run was too wide. */
	private void dermagaSeberang(GenerationContext context, Sampel sampel, RencanaJalan.Sisi e, int k, int sea,
			List<PoolElementStructurePiece> out) {
		int i = -1;
		for (int t = k - 1; t >= Math.max(0, k - 1 - RUN_MAKS); t--) {
			if (!sampel.air(t)) {
				i = t + 1;
				break;
			}
		}
		if (i > 0 && panjangJembatan(sampel.pts[i - 1], sampel.pts[k]) > 0) {
			return; // the near bank's chunk lays the bridge
		}
		dermaga(context, e, sampel.pts[k], sampel.pts[k - 1], sea, out);
	}

	/** The bridge length (one of {@link #PANJANG_JEMBATAN}) that spans bank {@code a} to bank {@code b}, or -1. */
	static int panjangJembatan(int[] a, int[] b) {
		int major = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
		for (int len : PANJANG_JEMBATAN) {
			if (len >= major + 2) {
				return len;
			}
		}
		return -1;
	}

	private void jembatan(GenerationContext context, RencanaJalan.Sisi e, int[] a, int[] b, int panjang, int deck,
			List<PoolElementStructurePiece> out) {
		if (jembatan.isEmpty()) {
			return;
		}
		Holder<StructureTemplatePool> base = jembatan.get(Math.min(e.kelas(), jembatan.size() - 1));
		StructurePoolElement element = elementPanjang(context, base, panjang, context.seed(), a[0], a[1]);
		if (element == null) {
			return;
		}
		StructureTemplateManager manager = context.structureTemplateManager();
		int dx = b[0] - a[0];
		int dz = b[1] - a[1];
		Rotation r = Rotation.values()[Math.abs(dz) >= Math.abs(dx) ? (dz >= 0 ? 0 : 2) : (dx < 0 ? 1 : 3)];
		Vec3i size = element.getSize(manager, Rotation.NONE);
		int[] centre = TataLetakStructure.rotate(r, size.getX() / 2, -1);
		BlockPos origin = new BlockPos(a[0] - centre[0], deck - DEK_Y, a[1] - centre[1]);
		tambah(out, manager, element, origin, r, 0);
	}

	private void dermaga(GenerationContext context, RencanaJalan.Sisi e, int[] bank, int[] water, int sea, List<PoolElementStructurePiece> out) {
		if (penyeberangan.isEmpty() || e.kelas() == SimpulJalan.SETAPAK) {
			return;
		}
		StructurePoolElement element = pilih(penyeberangan.get().value(), context.seed(), bank[0], bank[1], 0xFE77L);
		if (element == null) {
			return;
		}
		StructureTemplateManager manager = context.structureTemplateManager();
		int dx = water[0] - bank[0];
		int dz = water[1] - bank[1];
		Rotation r = Rotation.values()[RencanaJalan.putarKe(dx, dz)];
		Vec3i size = element.getSize(manager, Rotation.NONE);
		int[] centre = TataLetakStructure.rotate(r, size.getX() / 2, 2);
		BlockPos origin = new BlockPos(bank[0] - centre[0], sea - DERMAGA_Y, bank[1] - centre[1]);
		tambah(out, manager, element, origin, r, 0);
	}

	private void tangga(GenerationContext context, Sampel sampel, RencanaJalan.Sisi e, int low, int high, List<PoolElementStructurePiece> out) {
		StructureTemplatePool pool = pool(tangga, e.kelas());
		if (pool == null) {
			return;
		}
		int[] a = sampel.pts[low];
		int[] b = sampel.pts[high];
		StructurePoolElement element = pilih(pool, context.seed(), a[0], a[1], 0x7A66L);
		if (element == null) {
			return;
		}
		StructureTemplateManager manager = context.structureTemplateManager();
		Rotation r = Rotation.values()[RencanaJalan.putarKe(b[0] - a[0], b[1] - a[1])];
		Vec3i size = element.getSize(manager, Rotation.NONE);
		int[] centre = TataLetakStructure.rotate(r, size.getX() / 2, 0);
		BlockPos origin = new BlockPos(a[0] - centre[0], sampel.tanah(low), a[1] - centre[1]);
		tambah(out, manager, element, origin, r, 0);
	}

	// ------------------------------------------------------------------------------------------------ post mode

	private void bangunan(GenerationContext context, PutusanTanah.Sumber sumber, RencanaJalan.Pos p, List<PoolElementStructurePiece> out) {
		if (TempatKeepOut.dalamTempatLahir(context.seed(), p.x(), p.z(), sumber)) {
			return;
		}
		int radius = RencanaJalan.UKURAN_POS.getOrDefault(p.jenis(), 4);
		int r = Math.max(2, (int) Math.round(radius * 0.7D));
		int[][] probes = {{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}};
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		int[] heights = new int[probes.length];
		for (int i = 0; i < probes.length; i++) {
			int x = p.x() + probes[i][0];
			int z = p.z() + probes[i][1];
			int surface = sumber.surface(x, z);
			int floor = sumber.floor(x, z);
			if (surface > floor) {
				return;
			}
			heights[i] = floor - 1;
			lo = Math.min(lo, heights[i]);
			hi = Math.max(hi, heights[i]);
		}
		if (hi - lo > 10) {
			return;
		}
		java.util.Arrays.sort(heights);
		int ground = heights[heights.length / 2];
		StructureTemplateManager manager = context.structureTemplateManager();
		Rotation place = Rotation.values()[p.putar()];
		for (int index = 0; index < lots.size(); index++) {
			TataLetakStructure.Lot lot = lots.get(index);
			StructurePoolElement element = pilih(lot.pool().value(), context.seed(), p.x() + index, p.z(), 0xB0A7L);
			if (element == null) {
				continue;
			}
			Rotation own = Rotation.values()[lot.putar()];
			Vec3i size = element.getSize(manager, Rotation.NONE);
			int[] corner = TataLetakStructure.cornerOffset(own, size.getX(), size.getZ());
			int[] world = TataLetakStructure.rotate(place, lot.dx() + corner[0], lot.dz() + corner[1]);
			BlockPos at = new BlockPos(p.x() + world[0], ground - TataLetakStructure.LANTAI_Y + lot.dy(), p.z() + world[1]);
			tambah(out, manager, element, at, place.getRotated(own), TataLetakStructure.LANTAI_Y + 1);
		}
	}

	// ------------------------------------------------------------------------------------------------ helpers

	/** Lazy noise samples along one edge: ground top and water per sample. */
	private static final class Sampel {

		private final PutusanTanah.Sumber sumber;
		private final int[][] pts;
		private final int[] tanah;
		private final byte[] air;

		Sampel(PutusanTanah.Sumber sumber, int[][] pts) {
			this.sumber = sumber;
			this.pts = pts;
			this.tanah = new int[pts.length];
			this.air = new byte[pts.length];
		}

		int n() {
			return pts.length - 1;
		}

		private void ukur(int k) {
			if (air[k] != 0) {
				return;
			}
			int surface = sumber.surface(pts[k][0], pts[k][1]);
			int floor = sumber.floor(pts[k][0], pts[k][1]);
			tanah[k] = floor - 1;
			air[k] = (byte) (surface > floor ? 2 : 1);
		}

		boolean air(int k) {
			ukur(k);
			return air[k] == 2;
		}

		int tanah(int k) {
			ukur(k);
			return tanah[k];
		}

		boolean airDi(int x, int z) {
			return sumber.surface(x, z) > sumber.floor(x, z);
		}
	}

	private static StructureTemplatePool pool(List<Holder<StructureTemplatePool>> byClass, int kelas) {
		if (byClass.isEmpty()) {
			return null;
		}
		return byClass.get(Math.min(kelas, byClass.size() - 1)).value();
	}

	private static StructurePoolElement pilih(StructureTemplatePool pool, long seed, int x, int z, long salt) {
		if (pool == null) {
			return null;
		}
		long h = seed ^ (x * 341873128712L) ^ (z * 132897987541L) ^ (salt * 0x9E3779B97F4A7C15L);
		StructurePoolElement element = pool.getRandomTemplate(RandomSource.create(RencanaJalan.mix(h)));
		return element == EmptyPoolElement.INSTANCE ? null : element;
	}

	/**
	 * The bridge element of {@code panjang}: the pool {@code <class pool>_<panjang>} (e.g.
	 * {@code kultivator:jalan/jembatan_desa_16}) when registered, else the class pool itself.
	 */
	private static StructurePoolElement elementPanjang(GenerationContext context, Holder<StructureTemplatePool> base, int panjang, long seed,
			int x, int z) {
		StructureTemplatePool pool = base.value();
		Optional<ResourceKey<StructureTemplatePool>> key = base.unwrapKey();
		if (key.isPresent()) {
			Registry<StructureTemplatePool> pools = context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
			ResourceKey<StructureTemplatePool> sized = ResourceKey.create(Registries.TEMPLATE_POOL, key.get().identifier().withSuffix("_" + panjang));
			Optional<Holder.Reference<StructureTemplatePool>> found = pools.get(sized);
			if (found.isPresent()) {
				pool = found.get().value();
			}
		}
		return pilih(pool, seed, x, z, 0x7B1DL);
	}

	private static void tambah(List<PoolElementStructurePiece> out, StructureTemplateManager manager, StructurePoolElement element,
			BlockPos at, Rotation rotation, int groundDelta) {
		BoundingBox box = element.getBoundingBox(manager, at, rotation);
		out.add(new PoolElementStructurePiece(manager, element, at, groundDelta, rotation, box, LiquidSettings.IGNORE_WATERLOGGING));
	}

	@Override
	public StructureType<?> type() {
		return TempatTypes.JALAN;
	}
}
