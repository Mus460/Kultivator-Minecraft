package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.jalan.JalanPlacement;
import dev.kultivator.dunia.tempat.jalan.RencanaJalan;
import dev.kultivator.dunia.tempat.worldgen.RencanaPlacement;
import dev.kultivator.dunia.tempat.worldgen.TempatTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.DimensionOriginStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure-free zones around Kultivator places (DESIGN 38.D.4; owner 38W; world-mod compatibility, docs/progres/
 * kompat-mod-dunia.md). The mixin {@code ChunkGeneratorTempatMixin} works on the private
 * {@code ChunkGenerator.tryGenerateStructure}, the one place starts are created:
 * <ol>
 *   <li>before a start ({@link #tolak}): a start whose chunk centre lies in the keep-out box of a planned place
 *       ({@code Rencana.zonaDistrik}: capital districts + 3 chunks, compounds + 2, themed cities + 3) that is really born
 *       (its shared terrain verdict) is refused when it reaches the surface ({@link #permukaan}: every step except
 *       underground structures/decoration and strongholds, so Structory ruins on {@code raw_generation} and Terralith
 *       mage towers on {@code fluid_springs} count too), unless it is part of a plan itself (types {@code tata_letak} /
 *       {@code jalan}) or tagged {@code #kultivator:boleh_di_tempat};</li>
 *   <li>after the start is generated ({@link #saring}): a floating start ({@code #kultivator:melayang}, the sky palaces)
 *       is lifted clear of the terrain (Tectonic peaks); a start of another mod or vanilla is refused when any of its
 *       pieces (plus {@link #MARGIN}) touches a born planned box, or a piece of any Kultivator structure that starts
 *       within 8 chunks ("Kultivator wins"). Those Kultivator starts are predicted exactly as {@code createStructures}
 *       would make them (same placement test, same weighted pick, same generator), cached per seed, so the outcome does
 *       not depend on chunk order and our places never overlap foreign ones either way. Strongholds are never refused
 *       (eyes of ender stay right); other mods' structures are only refused inside our footprints.</li>
 * </ol>
 * Roads yield instead ({@link #jalanTerhalang}: a road piece is skipped where a foreign piece lies). Pure function of the
 * seed: the zone index is built once per seed and shared by all worldgen threads.
 */
public final class TempatKeepOut {

	/** Structures allowed inside a planned place's keep-out box. */
	public static final TagKey<Structure> BOLEH = TagKey.create(Registries.STRUCTURE, Kultivator.id("boleh_di_tempat"));
	/** Floating structures with an absolute start height: lifted above the terrain under them. */
	public static final TagKey<Structure> MELAYANG = TagKey.create(Registries.STRUCTURE, Kultivator.id("melayang"));
	/** Gap kept between a foreign piece and a Kultivator piece (blocks; beard terrain reaches about 12). */
	public static final int MARGIN = 12;
	public static final int MARGIN_Y = 6;
	/** Clear air kept under a floating structure. */
	public static final int JARAK_MELAYANG = 16;
	/** Farthest a start's pieces can lie from its chunk (vanilla references reach 8 chunks). */
	private static final int JANGKAU_CHUNK = 8;
	private static final int CACHE_MAKS = 8192;
	private static final BoundingBox[] KOSONG = new BoundingBox[0];

	private static volatile long seedIndeks = Long.MIN_VALUE;
	private static volatile TempatZones.IndeksRencana indeks = new TempatZones.IndeksRencana();
	private static volatile long seedTempat = Long.MIN_VALUE;
	private static volatile TempatZones.IndeksRencana tempat = new TempatZones.IndeksRencana();

	/** The structure state of the {@code createStructures} call running on this thread (set by the mixin). */
	private static final ThreadLocal<ChunkGeneratorStructureState> STATE = new ThreadLocal<>();
	private record Kunci(ResourceKey<Level> level, StructureSet set, long chunk) {
	}
	private static final Map<Kunci, BoundingBox[]> PREDIKSI = new ConcurrentHashMap<>();
	private static final Map<Kunci, Boolean> CALON = new ConcurrentHashMap<>();
	private static volatile long seedPrediksi = Long.MIN_VALUE;
	private record SetKita(ChunkGeneratorStructureState state, List<Holder<StructureSet>> sets) {
	}
	private static volatile SetKita setKita = new SetKita(null, List.of());

	/** Everything {@code Structure.generate} needs, from the running {@code tryGenerateStructure}. */
	public record Konteks(ResourceKey<Level> level, RegistryAccess registryAccess, ChunkGenerator generator, Climate.Sampler climate,
			RandomState randomState, StructureTemplateManager templates, long seed, LevelHeightAccessor height) {
	}

	/** True when a start of step {@code step} reaches the surface (every step but the underground ones and strongholds). */
	public static boolean permukaan(GenerationStep.Decoration step) {
		return step != GenerationStep.Decoration.UNDERGROUND_STRUCTURES && step != GenerationStep.Decoration.UNDERGROUND_DECORATION
				&& step != GenerationStep.Decoration.STRONGHOLDS;
	}

	/** True when {@code structure} is one of ours (namespace {@code kultivator}). */
	public static boolean kita(Holder<Structure> structure) {
		return structure.is(k -> Kultivator.MOD_ID.equals(k.identifier().getNamespace()));
	}

	/** True when the start of {@code structure} in chunk {@code chunk} must be refused (no verdict source: plan only). */
	public static boolean tolak(long seed, Holder<Structure> structure, ChunkPos chunk) {
		return tolak(seed, structure, chunk, null);
	}

	/**
	 * True when the start of {@code structure} in chunk {@code chunk} must be refused for world seed {@code seed}. With
	 * a verdict source only boxes of places that are really born count; without one every planned box counts.
	 */
	public static boolean tolak(long seed, Holder<Structure> structure, ChunkPos chunk, @Nullable PutusanTanah.Sumber sumber) {
		Structure s = structure.value();
		if (!permukaan(s.step())) {
			return false;
		}
		if (s.type() == TempatTypes.TATA_LETAK || s.type() == TempatTypes.JALAN || structure.is(BOLEH)) {
			return false;
		}
		TempatZones.IndeksRencana zones = zonesFor(seed);
		if (zones.kosong()) {
			return false;
		}
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		if (sumber == null) {
			return !zones.di(x, z).isEmpty();
		}
		return zones.berisiLahir(x, z, sumber);
	}

	/**
	 * The mixin entry: like {@link #tolak(long, Holder, ChunkPos, PutusanTanah.Sumber)}, building the verdict source
	 * from the generator only when the chunk lies in a planned box (most chunks do not).
	 */
	public static boolean tolak(long seed, Holder<Structure> structure, ChunkPos chunk, ChunkGenerator generator, RandomState randomState,
			LevelHeightAccessor height) {
		Structure s = structure.value();
		if (!permukaan(s.step())) {
			return false;
		}
		TempatZones.IndeksRencana zones = zonesFor(seed);
		if (zones.kosong() || zones.di(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()).isEmpty()) {
			return false;
		}
		return tolak(seed, structure, chunk, sumber(generator, randomState, height, seed));
	}

	// ------------------------------------------------------------------------------------------- after generation

	/** From the mixin at the head of {@code createStructures}: the structure state of this thread's chunk. */
	public static void mulai(ChunkGeneratorStructureState state) {
		STATE.set(state);
	}

	/** From the mixin at the return of {@code createStructures}. */
	public static void selesai() {
		STATE.remove();
	}

	/**
	 * The mixin entry after {@code Structure.generate}: the start to keep, lifted when floating, or
	 * {@link StructureStart#INVALID_START} when a foreign start would touch a Kultivator place.
	 */
	public static StructureStart saring(Holder<Structure> structure, StructureStart start, Konteks k) {
		if (!start.isValid()) {
			return start;
		}
		if (structure.is(MELAYANG)) {
			start = angkat(start, k);
		}
		if (kita(structure) || structure.value().step() == GenerationStep.Decoration.STRONGHOLDS || structure.is(BOLEH)) {
			return start;
		}
		List<BoundingBox> boxes = kotakAsing(start);
		if (k.level() == Level.OVERWORLD && permukaan(structure.value().step()) && diZona(boxes, k)) {
			return StructureStart.INVALID_START;
		}
		ChunkGeneratorStructureState state = STATE.get();
		if (state != null && state.getLevelSeed() == k.seed()
				&& tumpangKita(start.getBoundingBox().inflatedBy(MARGIN, MARGIN_Y, MARGIN), boxes, state, k)) {
			return StructureStart.INVALID_START;
		}
		return start;
	}

	/** The foreign start's piece boxes grown by the margin. */
	private static List<BoundingBox> kotakAsing(StructureStart start) {
		List<StructurePiece> pieces = start.getPieces();
		List<BoundingBox> out = new ArrayList<>(pieces.size());
		for (StructurePiece piece : pieces) {
			out.add(piece.getBoundingBox().inflatedBy(MARGIN, MARGIN_Y, MARGIN));
		}
		return out;
	}

	/** True when a box touches the keep-out box of a born planned place. */
	private static boolean diZona(List<BoundingBox> boxes, Konteks k) {
		TempatZones.IndeksRencana zones = zonesFor(k.seed());
		if (zones.kosong()) {
			return false;
		}
		PutusanTanah.Sumber sumber = null;
		for (BoundingBox box : boxes) {
			if (zones.menyentuh(box)) {
				if (sumber == null) {
					sumber = sumber(k.generator(), k.randomState(), k.height(), k.seed());
				}
				if (zones.menyentuhLahir(box, sumber)) {
					return true;
				}
			}
		}
		return false;
	}

	/** True when a foreign piece box touches a piece of a Kultivator start predicted within reach. */
	private static boolean tumpangKita(BoundingBox all, List<BoundingBox> boxes, ChunkGeneratorStructureState state, Konteks k) {
		int cx0 = (all.minX() >> 4) - JANGKAU_CHUNK;
		int cz0 = (all.minZ() >> 4) - JANGKAU_CHUNK;
		int cx1 = (all.maxX() >> 4) + JANGKAU_CHUNK;
		int cz1 = (all.maxZ() >> 4) + JANGKAU_CHUNK;
		if (seedPrediksi != k.seed()) {
			PREDIKSI.clear();
			CALON.clear();
			seedPrediksi = k.seed();
		}
		boolean zona = zonesFor(k.seed()).menyentuh(all);
		for (Holder<StructureSet> set : setKita(state)) {
			if (!zona && set.value().placement() instanceof RencanaPlacement) {
				continue; // district pieces lie inside their plan's keep-out boxes
			}
			for (ChunkPos c : calon(set.value(), state, k.level(), cx0, cz0, cx1, cz1)) {
				for (BoundingBox ours : prediksi(set.value(), c, state, k)) {
					if (!ours.intersects(all)) {
						continue;
					}
					for (BoundingBox box : boxes) {
						if (ours.intersects(box)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** Our structure sets present in {@code state}, without the road itself (roads yield, {@link #jalanTerhalang}). */
	private static List<Holder<StructureSet>> setKita(ChunkGeneratorStructureState state) {
		SetKita known = setKita;
		if (known.state() != state) {
			List<Holder<StructureSet>> out = new ArrayList<>();
			for (Holder<StructureSet> set : state.possibleStructureSets()) {
				if (set.is(k -> Kultivator.MOD_ID.equals(k.identifier().getNamespace()))
						&& !(set.value().placement() instanceof JalanPlacement j && j.titik().isEmpty())) {
					out.add(set);
				}
			}
			known = new SetKita(state, List.copyOf(out));
			setKita = known;
		}
		return known.sets();
	}

	/** The chunks in the range where {@code placement} really has a start (its own placement test). */
	private static List<ChunkPos> calon(StructureSet set, ChunkGeneratorStructureState state, ResourceKey<Level> level, int cx0, int cz0, int cx1,
			int cz1) {
		StructurePlacement placement = set.placement();
		List<ChunkPos> out = new ArrayList<>(2);
		if (placement instanceof JalanPlacement jalan) {
			RencanaJalan.pakai(state);
			// a post belongs to the cell of its edge, which may be a neighbour of the post's own cell (RencanaJalan.posDi)
			for (int sx = Math.floorDiv(cx0 * 16, RencanaJalan.SEL) - 1; sx <= Math.floorDiv(cx1 * 16 + 15, RencanaJalan.SEL) + 1; sx++) {
				for (int sz = Math.floorDiv(cz0 * 16, RencanaJalan.SEL) - 1; sz <= Math.floorDiv(cz1 * 16 + 15, RencanaJalan.SEL) + 1; sz++) {
					for (RencanaJalan.Pos p : RencanaJalan.posSel(state, sx, sz)) {
						if (p.jenis().equals(jalan.titik()) && p.chunkX() >= cx0 && p.chunkX() <= cx1 && p.chunkZ() >= cz0 && p.chunkZ() <= cz1) {
							ChunkPos c = new ChunkPos(p.chunkX(), p.chunkZ());
							if (!out.contains(c)) {
								out.add(c);
							}
						}
					}
				}
			}
		} else if (placement instanceof RandomSpreadStructurePlacement spread) {
			int spacing = spread.spacing();
			for (int gx = Math.floorDiv(cx0, spacing); gx <= Math.floorDiv(cx1, spacing); gx++) {
				for (int gz = Math.floorDiv(cz0, spacing); gz <= Math.floorDiv(cz1, spacing); gz++) {
					ChunkPos c = spread.getPotentialStructureChunk(state.getLevelSeed(), gx * spacing, gz * spacing);
					if (c.x() < cx0 || c.x() > cx1 || c.z() < cz0 || c.z() > cz1) {
						continue;
					}
					Kunci key = new Kunci(level, set, c.pack());
					Boolean ada = CALON.get(key);
					if (ada == null) {
						ada = spread.isStructureChunk(state, c.x(), c.z());
						if (CALON.size() > CACHE_MAKS * 4) {
							CALON.clear();
						}
						CALON.put(key, ada);
					}
					if (ada) {
						out.add(c);
					}
				}
			}
		} else if (placement instanceof DimensionOriginStructurePlacement) {
			ChunkPos c = state.getDimensionOrigin();
			if (c.x() >= cx0 && c.x() <= cx1 && c.z() >= cz0 && c.z() <= cz1) {
				out.add(c);
			}
		}
		return out;
	}

	/**
	 * The piece boxes of the start {@code set} makes in chunk {@code chunk} (empty when none), the way
	 * {@code ChunkGenerator.createStructures} makes it: one entry directly, several by the same seeded weighted pick,
	 * each try refused by {@link #tolak} first, then generated and passed through {@link #saring}.
	 */
	private static BoundingBox[] prediksi(StructureSet set, ChunkPos chunk, ChunkGeneratorStructureState state, Konteks k) {
		Kunci key = new Kunci(k.level(), set, chunk.pack());
		BoundingBox[] known = PREDIKSI.get(key);
		if (known != null) {
			return known;
		}
		List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(set.structures());
		StructureStart found = null;
		if (options.size() == 1) {
			found = coba(options.getFirst(), chunk, k);
		} else {
			WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
			random.setLargeFeatureSeed(k.seed(), chunk.x(), chunk.z());
			int total = 0;
			for (StructureSet.StructureSelectionEntry option : options) {
				total += option.weight();
			}
			while (!options.isEmpty() && found == null) {
				int choice = random.nextInt(total);
				int index = 0;
				for (StructureSet.StructureSelectionEntry option : options) {
					choice -= option.weight();
					if (choice < 0) {
						break;
					}
					index++;
				}
				StructureSet.StructureSelectionEntry selected = options.get(index);
				found = coba(selected, chunk, k);
				options.remove(index);
				total -= selected.weight();
			}
		}
		BoundingBox[] out = KOSONG;
		if (found != null) {
			List<StructurePiece> pieces = found.getPieces();
			out = new BoundingBox[pieces.size()];
			for (int i = 0; i < out.length; i++) {
				out[i] = pieces.get(i).getBoundingBox();
			}
		}
		if (PREDIKSI.size() > CACHE_MAKS) {
			PREDIKSI.clear();
		}
		PREDIKSI.put(key, out);
		return out;
	}

	/** One try of {@code createStructures} for {@code entry} in {@code chunk}: the valid start, or null. */
	private static @Nullable StructureStart coba(StructureSet.StructureSelectionEntry entry, ChunkPos chunk, Konteks k) {
		Holder<Structure> structure = entry.structure();
		if (k.level() == Level.OVERWORLD && tolak(k.seed(), structure, chunk, k.generator(), k.randomState(), k.height())) {
			return null;
		}
		StructureStart start = structure.value().generate(structure, k.level(), k.registryAccess(), k.generator(), k.generator().getBiomeSource(),
				k.climate(), k.randomState(), k.templates(), k.seed(), chunk, 0, k.height(), structure.value().biomes()::contains);
		start = saring(structure, start, k);
		return start.isValid() ? start : null;
	}

	/**
	 * A floating start lifted so its lowest piece clears the highest ground under its pieces by {@link #JARAK_MELAYANG}
	 * (Tectonic and Terralith mountains reach far above the palaces' absolute 204-228), as far as the build limit allows.
	 */
	private static StructureStart angkat(StructureStart start, Konteks k) {
		List<StructurePiece> pieces = start.getPieces();
		int ground = Integer.MIN_VALUE;
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (StructurePiece piece : pieces) {
			BoundingBox b = piece.getBoundingBox();
			low = Math.min(low, b.minY());
			high = Math.max(high, b.maxY());
			int[][] probes = {{b.minX(), b.minZ()}, {b.maxX(), b.minZ()}, {b.minX(), b.maxZ()}, {b.maxX(), b.maxZ()},
					{(b.minX() + b.maxX()) >> 1, (b.minZ() + b.maxZ()) >> 1}};
			for (int[] p : probes) {
				ground = Math.max(ground, k.generator().getBaseHeight(p[0], p[1], Heightmap.Types.WORLD_SURFACE_WG, k.height(), k.randomState()));
			}
		}
		int dy = Math.min(ground + JARAK_MELAYANG - low, k.height().getMaxY() - high);
		if (dy <= 0) {
			return start;
		}
		for (StructurePiece piece : pieces) {
			piece.move(0, dy, 0);
		}
		return new StructureStart(start.getStructure(), start.getChunkPos(), start.getReferences(), new PiecesContainer(pieces));
	}

	// ------------------------------------------------------------------------------------------------------ roads

	/**
	 * True when the part of road piece box {@code piece} inside {@code chunkBB} touches a piece of a structure of another
	 * mod or vanilla in this chunk: the road skips it (roads yield; no road cuts through a foreign structure). The starts
	 * are those referencing the chunk, all known by the features step, so the answer does not depend on chunk order.
	 */
	public static boolean jalanTerhalang(WorldGenLevel level, StructureManager structureManager, ChunkPos chunkPos, BoundingBox chunkBB,
			BoundingBox piece) {
		if (!piece.intersects(chunkBB)) {
			return false;
		}
		BoundingBox part = new BoundingBox(Math.max(piece.minX(), chunkBB.minX()), piece.minY() - 1, Math.max(piece.minZ(), chunkBB.minZ()),
				Math.min(piece.maxX(), chunkBB.maxX()), piece.maxY() + 1, Math.min(piece.maxZ(), chunkBB.maxZ())).inflatedBy(1, 0, 1);
		var registry = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (StructureStart start : structureManager.startsForStructure(chunkPos.x(), chunkPos.z(), s -> {
			var key = registry.getKey(s);
			return key != null && !Kultivator.MOD_ID.equals(key.getNamespace());
		})) {
			if (!start.getBoundingBox().intersects(part)) {
				continue;
			}
			for (StructurePiece other : start.getPieces()) {
				if (other.getBoundingBox().intersects(part)) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------------ zones

	/**
	 * True when column {@code (x, z)} lies inside the box of a planned place that is really born (roads and road-side
	 * posts stop there; the place has its own streets). World generation passes its verdict source.
	 */
	public static boolean dalamTempatLahir(long seed, int x, int z, PutusanTanah.Sumber sumber) {
		if (seedTempat != seed) {
			synchronized (TempatKeepOut.class) {
				if (seedTempat != seed) {
					tempat = TempatZones.IndeksRencana.of(seed, 2);
					seedTempat = seed;
				}
			}
		}
		return !tempat.kosong() && tempat.berisiLahir(x, z, sumber);
	}

	/** Drop the zone indexes and predicted starts (a plan's config changed). */
	public static void lupakan() {
		seedIndeks = Long.MIN_VALUE;
		seedTempat = Long.MIN_VALUE;
		seedPrediksi = Long.MIN_VALUE;
		PREDIKSI.clear();
		CALON.clear();
		setKita = new SetKita(null, List.of());
	}

	private static PutusanTanah.Sumber sumber(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor height, long seed) {
		return new PutusanTanah.Sumber(generator, randomState, height, generator.getBiomeSource().createUncachedResolver(randomState), seed);
	}

	private static TempatZones.IndeksRencana zonesFor(long seed) {
		if (seedIndeks != seed) {
			synchronized (TempatKeepOut.class) {
				if (seedIndeks != seed) {
					indeks = TempatZones.IndeksRencana.of(seed, 0);
					seedIndeks = seed;
				}
			}
		}
		return indeks;
	}

	private TempatKeepOut() {
	}
}
