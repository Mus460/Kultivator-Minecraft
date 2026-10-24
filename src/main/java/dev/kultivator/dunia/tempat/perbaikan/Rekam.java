package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding cared-for places and recording their blueprints (DESIGN 38.R.1).
 * <ul>
 *   <li>Every loaded chunk ({@code ServerChunkEvents.CHUNK_LOAD}) is asked for the starts it holds and the starts it
 *       references; a start of {@link #DIRAWAT} (or of the old town tags) becomes a {@link TempatRawat} with the boxes of
 *       its pieces. A reference to a start in an unloaded chunk waits until that chunk loads: nothing is ever loaded.</li>
 *   <li>A loaded chunk that a known place touches and that has no slice yet goes into the record queue; at most
 *       {@code perbaikan.rekamPerTick} chunks are recorded per tick, from the live chunk (only while it is still loaded).</li>
 * </ul>
 * The index (chunk to places) is rebuilt from {@link PerbaikanData} when the server starts. Server thread only.
 */
public final class Rekam {

	/** Structures whose places are cared for (owner of the tag file: 38W; the old town tags count too). */
	public static final TagKey<Structure> DIRAWAT = TagKey.create(Registries.STRUCTURE, Kultivator.id("tempat_dirawat"));
	private static final TagKey<Structure> KOTA = TagKey.create(Registries.STRUCTURE, Kultivator.id("kota"));
	private static final TagKey<Structure> KOTA_ABADI = TagKey.create(Registries.STRUCTURE, Kultivator.id("kota_abadi"));

	private record Antre(ResourceKey<Level> dim, long chunk) {
	}

	private static final Map<ResourceKey<Level>, Map<Long, long[]>> INDEKS = new HashMap<>();
	private static final ArrayDeque<Antre> ANTRE = new ArrayDeque<>();
	private static final Map<ResourceKey<Level>, LongOpenHashSet> DIANTRE = new HashMap<>();
	/** Start chunks referenced by a loaded chunk but not loaded themselves. */
	private static final Map<ResourceKey<Level>, LongOpenHashSet> TERTUNDA = new HashMap<>();
	private static final Map<Structure, Boolean> DIRAWAT_CACHE = new IdentityHashMap<>();
	/** Game time a place's site id was last looked up (places whose id is still made up are asked again each minute). */
	private static final Map<Long, Long> SITUS_DITANYA = new HashMap<>();
	private static @Nullable MinecraftServer server;
	private static int rekamTotal;

	static void init() {
		ServerChunkEvents.CHUNK_LOAD.register(Rekam::onLoad);
	}

	static void mulai(MinecraftServer s) {
		server = s;
		INDEKS.clear();
		ANTRE.clear();
		DIANTRE.clear();
		TERTUNDA.clear();
		DIRAWAT_CACHE.clear();
		SITUS_DITANYA.clear();
		for (TempatRawat t : PerbaikanData.get(s).tempat.values()) {
			indeks(t);
		}
	}

	static void berhenti() {
		server = null;
		INDEKS.clear();
		ANTRE.clear();
		DIANTRE.clear();
		TERTUNDA.clear();
		DIRAWAT_CACHE.clear();
		SITUS_DITANYA.clear();
	}

	// ================================================================== queries

	/** The cared-for places touching chunk {@code chunk} of {@code dim}. */
	public static List<TempatRawat> diChunk(ResourceKey<Level> dim, long chunk) {
		MinecraftServer s = server;
		Map<Long, long[]> map = INDEKS.get(dim);
		if (s == null || map == null) {
			return List.of();
		}
		long[] keys = map.get(chunk);
		if (keys == null) {
			return List.of();
		}
		PerbaikanData data = PerbaikanData.get(s);
		List<TempatRawat> out = new ArrayList<>(keys.length);
		for (long k : keys) {
			TempatRawat t = data.tempat.get(k);
			if (t != null) {
				out.add(t);
			}
		}
		return out;
	}

	/** The cared-for place whose piece holds {@code pos}, or null. */
	public static @Nullable TempatRawat tempatDi(ServerLevel level, BlockPos pos) {
		for (TempatRawat t : diChunk(level.dimension(), ChunkPos.pack(pos))) {
			if (t.berisi(pos)) {
				return t;
			}
		}
		return null;
	}

	/** The nearest cared-for place within {@code jarak} blocks (horizontal distance to its box), or null. */
	public static @Nullable TempatRawat terdekat(ServerLevel level, BlockPos pos, double jarak) {
		MinecraftServer s = server;
		if (s == null) {
			return null;
		}
		TempatRawat best = null;
		double bestD = jarak * jarak;
		for (TempatRawat t : PerbaikanData.get(s).tempat.values()) {
			if (!t.dimensi.equals(level.dimension())) {
				continue;
			}
			double d = t.jarakKotakSqr(pos);
			if (d <= bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	/** The place with site id {@code situsId} (first found), or null. */
	public static @Nullable TempatRawat bySitus(long situsId) {
		MinecraftServer s = server;
		if (s == null) {
			return null;
		}
		for (TempatRawat t : PerbaikanData.get(s).tempat.values()) {
			if (t.situsId == situsId) {
				return t;
			}
		}
		return null;
	}

	/** The blueprint slice of place {@code t} in chunk {@code chunk}, or null when not recorded. */
	public static @Nullable Cetak cetak(TempatRawat t, long chunk) {
		if (!t.terekam.contains(chunk)) {
			return null;
		}
		return CetakStore.get(t.dimensi, t.kunci, chunk);
	}

	/** {places, recorded slices this session, queued chunks}. */
	static int[] statistik() {
		MinecraftServer s = server;
		return new int[]{s == null ? 0 : PerbaikanData.get(s).tempat.size(), rekamTotal, ANTRE.size()};
	}

	// ================================================================== discovery

	private static void onLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (server == null || !PerbaikanConfig.get().aktif) {
			return;
		}
		try {
			discover(level, chunk);
			long key = chunk.getPos().pack();
			for (TempatRawat t : diChunk(level.dimension(), key)) {
				if (!t.terekam.contains(key)) {
					antre(level.dimension(), key);
					break;
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: place repair could not read chunk {}", chunk.getPos(), exception);
		}
	}

	private static void discover(ServerLevel level, LevelChunk chunk) {
		for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
			if (e.getValue() != null && e.getValue().isValid() && dirawat(level, e.getKey())) {
				daftar(level, e.getKey(), e.getValue());
			}
		}
		long here = chunk.getPos().pack();
		LongOpenHashSet waiting = TERTUNDA.get(level.dimension());
		if (waiting != null) {
			waiting.remove(here);
		}
		for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
			if (e.getValue() == null || e.getValue().isEmpty() || !dirawat(level, e.getKey())) {
				continue;
			}
			Identifier id = structureId(level, e.getKey());
			if (id == null) {
				continue;
			}
			for (long startChunk : e.getValue()) {
				ChunkPos sp = ChunkPos.unpack(startChunk);
				long kunci = TempatRawat.kunciDari(level.dimension(), id, sp);
				if (PerbaikanData.get(level.getServer()).tempat.containsKey(kunci)) {
					continue;
				}
				LevelChunk startHolder = level.getChunkSource().getChunkNow(sp.x(), sp.z());
				if (startHolder == null) {
					TERTUNDA.computeIfAbsent(level.dimension(), d -> new LongOpenHashSet()).add(startChunk);
					continue;
				}
				StructureStart start = startHolder.getStartForStructure(e.getKey());
				if (start != null && start.isValid()) {
					daftar(level, e.getKey(), start);
				}
			}
		}
	}

	private static boolean dirawat(ServerLevel level, Structure structure) {
		Boolean known = DIRAWAT_CACHE.get(structure);
		if (known != null) {
			return known;
		}
		boolean yes = false;
		try {
			Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			Holder<Structure> holder = registry.wrapAsHolder(structure);
			yes = holder.is(DIRAWAT) || holder.is(KOTA) || holder.is(KOTA_ABADI);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: could not read the tags of a structure for place repair", exception);
		}
		DIRAWAT_CACHE.put(structure, yes);
		return yes;
	}

	private static @Nullable Identifier structureId(ServerLevel level, Structure structure) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
	}

	/** Registers the place of {@code start} (once) and queues every loaded chunk it touches. */
	private static void daftar(ServerLevel level, Structure structure, StructureStart start) {
		Identifier id = structureId(level, structure);
		if (id == null) {
			return;
		}
		PerbaikanData data = PerbaikanData.get(level.getServer());
		long kunci = TempatRawat.kunciDari(level.dimension(), id, start.getChunkPos());
		if (data.tempat.containsKey(kunci)) {
			return;
		}
		List<BoundingBox> boxes = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			boxes.add(piece.getBoundingBox());
		}
		String jenis = JenisTempat.dariStruktur(id);
		TempatRawat t = new TempatRawat(kunci, level.dimension(), id, start.getChunkPos(), start.getBoundingBox(), boxes, jenis, 0L);
		situs(level, t, true);
		data.tempat.put(kunci, t);
		data.kotor();
		indeks(t);
		for (long c : t.chunks) {
			if (level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c)) != null) {
				antre(level.dimension(), c);
			}
		}
		Kultivator.LOGGER.debug("Kultivator: cared-for place {} at {} ({} pieces, {} chunks)", id, start.getChunkPos(), t.potongan.size(), t.chunks.length);
	}

	/**
	 * The site id and kind of {@code t}: the place index of 38W when it knows the place, else a published site of the
	 * same family inside the box, else a stable made-up id. Asked again at most once a minute while made up; a new
	 * real id moves the place's clusters, bills and fear along.
	 */
	static void situs(ServerLevel level, TempatRawat t, boolean paksa) {
		long now = level.getGameTime();
		Long asked = SITUS_DITANYA.get(t.kunci);
		if (!paksa && asked != null && now - asked < 1200L) {
			return;
		}
		SITUS_DITANYA.put(t.kunci, now);
		long old = t.situsId;
		long found = 0L;
		BlockPos centre = t.pusat();
		TempatInfo info = TempatApi.tempatDi(level, new BlockPos(centre.getX(), t.kotak.minY() + 2, centre.getZ()));
		if (info != null && info.situsId() != 0L) {
			found = info.situsId();
			if (info.jenis() != null && !info.jenis().isEmpty()) {
				t.jenis = info.jenis();
			}
		} else {
			String family = t.jenis.contains("/") ? t.jenis.substring(0, t.jenis.indexOf('/') + 1) : t.jenis;
			int radius = Math.max(48, Math.max(t.kotak.getXSpan(), t.kotak.getZSpan()) / 2 + 16);
			for (DuniaSites.Site site : DuniaSites.near(level, centre, radius, family)) {
				if (t.kotak.inflatedBy(16).isInside(site.pos())) {
					found = site.id();
					break;
				}
			}
		}
		if (found == 0L) {
			if (old != 0L) {
				return;
			}
			found = DuniaSites.idOf(level.dimension(), centre, "rawat/" + t.jenis);
		}
		if (found != old) {
			t.situsId = found;
			if (old != 0L) {
				PerbaikanData data = PerbaikanData.get(level.getServer());
				for (Gugus g : data.gugus.values()) {
					if (g.kunci == t.kunci) {
						g.situsId = found;
					}
				}
				PerbaikanData.TakutData fear = data.takut.remove(old);
				if (fear != null) {
					data.takut.put(found, fear);
				}
			}
			PerbaikanData.get(level.getServer()).kotor();
		}
		if (t.kanalDitanya == 0L || now - t.kanalDitanya > 6000L) {
			t.kanalDitanya = now;
			t.kanal.clear();
			for (Titik k : TempatApi.titik(level, t.situsId, "kanal")) {
				t.kanal.add(k.kotak());
			}
		}
	}

	private static void indeks(TempatRawat t) {
		Map<Long, long[]> map = INDEKS.computeIfAbsent(t.dimensi, d -> new HashMap<>());
		for (long c : t.chunks) {
			long[] old = map.get(c);
			if (old == null) {
				map.put(c, new long[]{t.kunci});
			} else {
				boolean has = false;
				for (long k : old) {
					has |= k == t.kunci;
				}
				if (!has) {
					long[] more = java.util.Arrays.copyOf(old, old.length + 1);
					more[old.length] = t.kunci;
					map.put(c, more);
				}
			}
		}
	}

	/** Queues a loaded chunk for recording again (its slice file was lost). */
	static void antreLagi(ResourceKey<Level> dim, long chunk) {
		antre(dim, chunk);
	}

	private static void antre(ResourceKey<Level> dim, long chunk) {
		if (DIANTRE.computeIfAbsent(dim, d -> new LongOpenHashSet()).add(chunk)) {
			ANTRE.addLast(new Antre(dim, chunk));
		}
	}

	// ================================================================== recording

	static void tick(MinecraftServer s) {
		int budget = PerbaikanConfig.get().rekamPerTick;
		while (budget > 0 && !ANTRE.isEmpty()) {
			Antre a = ANTRE.pollFirst();
			LongOpenHashSet set = DIANTRE.get(a.dim());
			if (set != null) {
				set.remove(a.chunk());
			}
			ServerLevel level = s.getLevel(a.dim());
			if (level == null) {
				continue;
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(a.chunk()), ChunkPos.getZ(a.chunk()));
			if (chunk == null) {
				// unloaded again before its turn: it is queued anew on its next load
				continue;
			}
			boolean any = false;
			for (TempatRawat t : diChunk(a.dim(), a.chunk())) {
				if (!t.terekam.contains(a.chunk())) {
					rekam(level, chunk, t);
					any = true;
				}
			}
			if (any) {
				budget--;
			}
		}
	}

	/** Records (or re-records) the slice of {@code t} in {@code chunk} from the live blocks. */
	static void rekam(ServerLevel level, LevelChunk chunk, TempatRawat t) {
		int cx = chunk.getPos().x();
		int cz = chunk.getPos().z();
		long key = chunk.getPos().pack();
		List<BoundingBox> boxes = t.potonganDi(cx, cz);
		PerbaikanData data = PerbaikanData.get(level.getServer());
		if (boxes.isEmpty()) {
			t.terekam.add(key);
			data.kotor();
			return;
		}
		long[] mask = new long[4];
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int x0 = cx << 4;
		int z0 = cz << 4;
		for (BoundingBox b : boxes) {
			for (int x = Math.max(b.minX(), x0); x <= Math.min(b.maxX(), x0 + 15); x++) {
				for (int z = Math.max(b.minZ(), z0); z <= Math.min(b.maxZ(), z0 + 15); z++) {
					int bit = ((z & 15) << 4) | (x & 15);
					mask[bit >> 6] |= 1L << (bit & 63);
				}
			}
			minY = Math.min(minY, b.minY());
			maxY = Math.max(maxY, b.maxY());
		}
		minY = Math.max(minY, level.getMinY());
		maxY = Math.min(Math.min(maxY, level.getMaxY()), minY + Cetak.MAKS_TINGGI - 1);
		Cetak.Pembuat p = new Cetak.Pembuat(t.kunci, key, minY, maxY, mask);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int index = 0;
		for (int y = minY; y <= maxY; y++) {
			for (int bit = 0; bit < 256; bit++) {
				if ((mask[bit >> 6] >>> (bit & 63) & 1L) == 0L) {
					continue;
				}
				int x = x0 + (bit & 15);
				int z = z0 + (bit >> 4);
				boolean inside = false;
				for (BoundingBox b : boxes) {
					if (b.isInside(x, y, z)) {
						inside = true;
						break;
					}
				}
				if (!inside) {
					p.isi[index++] = Cetak.TIDAK;
					continue;
				}
				at.set(x, y, z);
				BlockState state = chunk.getBlockState(at);
				if (state.getBlock() instanceof BaseFireBlock) {
					state = Blocks.AIR.defaultBlockState();
				}
				int here = index++;
				p.taruh(here, state);
				if (state.hasBlockEntity()) {
					CompoundTag tag = beTag(level, chunk.getBlockEntity(at, LevelChunk.EntityCreationType.CHECK));
					if (tag != null) {
						p.be.put(here, tag);
					}
				}
			}
		}
		CetakStore.put(t.dimensi, p.selesai());
		t.terekam.add(key);
		data.kotor();
		rekamTotal++;
	}

	/** What a repair may put back of a block entity: an unopened chest's loot table, a sign's or banner's full tag. */
	private static @Nullable CompoundTag beTag(ServerLevel level, @Nullable BlockEntity entity) {
		if (entity == null) {
			return null;
		}
		try {
			if (entity instanceof RandomizableContainer container && container.getLootTable() != null) {
				CompoundTag tag = new CompoundTag();
				tag.putString(RandomizableContainer.LOOT_TABLE_TAG, container.getLootTable().identifier().toString());
				tag.putBoolean("kultivator_jarahan", true);
				return tag;
			}
			if (entity instanceof SignBlockEntity || entity instanceof BannerBlockEntity) {
				return entity.saveCustomOnly(level.registryAccess());
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.debug("Kultivator: a block entity of a cared-for place could not be recorded", exception);
		}
		return null;
	}

	/** {@code uji tempat cetak rekam}: records every loaded chunk of {@code t} again (a new honest baseline). */
	static int rekamUlang(ServerLevel level, TempatRawat t) {
		int n = 0;
		LongArrayList loaded = new LongArrayList();
		for (long c : t.chunks) {
			if (level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c)) != null) {
				loaded.add(c);
			}
		}
		for (long c : loaded) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
			if (chunk != null) {
				CetakStore.hapus(t.dimensi, t.kunci, c);
				t.terekam.remove(c);
				rekam(level, chunk, t);
				n++;
			}
		}
		return n;
	}

	private Rekam() {
	}
}
