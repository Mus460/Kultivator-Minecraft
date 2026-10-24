package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.Kultivator;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blueprint storage (DESIGN 38.R.1): {@code <world>/kultivator/cetak/<dimension>/r.<rx>.<rz>.dat}, one gzip NBT file per
 * 32 x 32 chunks holding every {@link Cetak} slice of that region. At most {@link #LRU} regions stay in memory (least
 * recently used first out, written when dirty); dirty regions are written every five minutes, on every world save and
 * when the server stops. Reading happens on the server thread only when a slice is needed (a scan, a repair plan), never
 * on chunk load. A file that cannot be read counts as empty (the place is recorded again, an honest new baseline).
 */
public final class CetakStore {

	/** Regions kept in memory. */
	public static final int LRU = 6;
	/** Ticks between two automatic writes of dirty regions. */
	public static final int SIMPAN_TICK = 6000;
	/** Size cap of one region file read (bytes of NBT). */
	private static final long KUOTA = 64L * 1024L * 1024L;

	private record KunciRegion(ResourceKey<Level> dim, int rx, int rz) {
	}

	private static final class Region {
		/** chunk key -> place key -> slice */
		final Map<Long, Map<Long, Cetak>> isi = new HashMap<>();
		boolean kotor;
	}

	private static final LinkedHashMap<KunciRegion, Region> REGION = new LinkedHashMap<>(16, 0.75F, true);
	private static @Nullable MinecraftServer server;

	static void mulai(MinecraftServer s) {
		server = s;
		REGION.clear();
	}

	/** Writes everything dirty and forgets the server (server stop). */
	static void berhenti(MinecraftServer s) {
		simpanSemua(s);
		REGION.clear();
		server = null;
	}

	/** The slice of place {@code kunci} in chunk {@code chunk}, or null when not recorded. */
	static @Nullable Cetak get(ResourceKey<Level> dim, long kunci, long chunk) {
		Region region = region(dim, chunk, false);
		if (region == null) {
			return null;
		}
		Map<Long, Cetak> perChunk = region.isi.get(chunk);
		return perChunk == null ? null : perChunk.get(kunci);
	}

	/** Stores a recorded slice (replacing an older one). */
	static void put(ResourceKey<Level> dim, Cetak cetak) {
		Region region = region(dim, cetak.chunk, true);
		if (region == null) {
			return;
		}
		region.isi.computeIfAbsent(cetak.chunk, c -> new HashMap<>()).put(cetak.kunci, cetak);
		region.kotor = true;
	}

	/** Drops every slice of place {@code kunci} in chunk {@code chunk} (an uji re-record). */
	static void hapus(ResourceKey<Level> dim, long kunci, long chunk) {
		Region region = region(dim, chunk, false);
		if (region == null) {
			return;
		}
		Map<Long, Cetak> perChunk = region.isi.get(chunk);
		if (perChunk != null && perChunk.remove(kunci) != null) {
			region.kotor = true;
		}
	}

	/** Regions in memory and how many are dirty (for {@code uji tempat cetak info}). */
	static int[] statistik() {
		int dirty = 0;
		for (Region r : REGION.values()) {
			if (r.kotor) {
				dirty++;
			}
		}
		return new int[]{REGION.size(), dirty};
	}

	/** Writes every dirty region. */
	static void simpanSemua(MinecraftServer s) {
		for (Map.Entry<KunciRegion, Region> e : REGION.entrySet()) {
			if (e.getValue().kotor) {
				tulis(s, e.getKey(), e.getValue());
			}
		}
	}

	private static @Nullable Region region(ResourceKey<Level> dim, long chunk, boolean buat) {
		MinecraftServer s = server;
		if (s == null) {
			return null;
		}
		KunciRegion key = new KunciRegion(dim, ChunkPos.getRegionX(chunk), ChunkPos.getRegionZ(chunk));
		Region region = REGION.get(key);
		if (region != null) {
			return region;
		}
		region = baca(s, key);
		if (region == null) {
			// "no file" is remembered too (an empty region), so a scan does not hit the disk again; it is only written once
			// a slice is put into it
			region = new Region();
		}
		REGION.put(key, region);
		while (REGION.size() > LRU) {
			Iterator<Map.Entry<KunciRegion, Region>> it = REGION.entrySet().iterator();
			Map.Entry<KunciRegion, Region> eldest = it.next();
			if (eldest.getKey().equals(key)) {
				break;
			}
			if (eldest.getValue().kotor) {
				tulis(s, eldest.getKey(), eldest.getValue());
			}
			it.remove();
		}
		return region;
	}

	private static Path file(MinecraftServer s, KunciRegion key) {
		String dim = key.dim().identifier().getNamespace() + "_" + key.dim().identifier().getPath().replace('/', '_');
		return s.getWorldPath(LevelResource.ROOT).resolve("kultivator").resolve("cetak").resolve(dim)
				.resolve("r." + key.rx() + "." + key.rz() + ".dat");
	}

	private static @Nullable Region baca(MinecraftServer s, KunciRegion key) {
		Path path = file(s, key);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(KUOTA));
			HolderGetter<Block> blocks = s.registryAccess().lookupOrThrow(Registries.BLOCK);
			Region region = new Region();
			for (Tag t : root.getListOrEmpty("cetak")) {
				if (t instanceof CompoundTag entry) {
					Cetak c = Cetak.muat(entry, blocks);
					if (c != null) {
						region.isi.computeIfAbsent(c.chunk, k -> new HashMap<>()).put(c.kunci, c);
					}
				}
			}
			return region;
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: blueprint region {} could not be read; those places will be recorded again", path, exception);
			return null;
		}
	}

	private static void tulis(MinecraftServer s, KunciRegion key, Region region) {
		Path path = file(s, key);
		try {
			ListTag list = new ListTag();
			for (Map<Long, Cetak> perChunk : region.isi.values()) {
				for (Cetak c : perChunk.values()) {
					list.add(c.simpan());
				}
			}
			CompoundTag root = new CompoundTag();
			root.putInt("versi", 1);
			root.put("cetak", list);
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			NbtIo.writeCompressed(root, tmp);
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			region.kotor = false;
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: blueprint region {} could not be written", path, exception);
		}
	}

	private CetakStore() {
	}
}
