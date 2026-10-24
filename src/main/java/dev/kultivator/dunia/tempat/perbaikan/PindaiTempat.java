package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.KultivatorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The scanner of DESIGN 38.R.2: compares the world with the blueprint, place by place, and hands every real difference
 * to {@link PekerjaanPerbaikan#temukan}. Only places with a player within 64 blocks are scanned, only loaded chunks are
 * read, at most {@link #BACA_PER_TICK} block reads per tick for the whole server, one full pass per place every
 * {@code perbaikan.pindaiTick} ticks at most. Boxes reported by events (a block broken, a cast, TNT, a test) are read
 * first ({@link #prioritas}), so attribution sees the event while it is fresh. Paused while the tick time is high.
 */
public final class PindaiTempat {

	public static final int BACA_PER_TICK = 4096;
	public static final double JARAK_AKTIF = 64.0D;
	/** A priority box is clipped to this many blocks per axis around its centre. */
	private static final int PRIORITAS_MAKS = 25;

	private static final class Kursor {
		int chunk;
		int voxel;
		long mulai;
		boolean selesai;
	}

	private record Prioritas(long kunci, BoundingBox kotak) {
	}

	private record Nanti(long kunci, BoundingBox kotak, long saat) {
	}

	private static final Map<Long, Kursor> KURSOR = new HashMap<>();
	private static final ArrayDeque<Prioritas> PRIORITAS = new ArrayDeque<>();
	private static final List<Nanti> NANTI = new ArrayList<>();
	private static List<TempatRawat> aktif = List.of();
	private static int putar;
	private static long baca;
	private static long diterima;

	static void berhenti() {
		KURSOR.clear();
		PRIORITAS.clear();
		NANTI.clear();
		aktif = List.of();
	}

	/** Places scanned now (a player within 64 blocks). */
	static List<TempatRawat> aktif() {
		return aktif;
	}

	/** Block reads since the server started (for {@code uji tempat cetak info}). */
	static long baca() {
		return baca;
	}

	/** Reads {@code kotak} of place {@code t} before the round robin (an event just happened there). */
	static void prioritas(TempatRawat t, BoundingBox kotak) {
		BlockPos c = kotak.getCenter();
		int h = PRIORITAS_MAKS / 2;
		BoundingBox clipped = new BoundingBox(Math.max(kotak.minX(), c.getX() - h), Math.max(kotak.minY(), c.getY() - h), Math.max(kotak.minZ(), c.getZ() - h),
				Math.min(kotak.maxX(), c.getX() + h), Math.min(kotak.maxY(), c.getY() + h), Math.min(kotak.maxZ(), c.getZ() + h));
		if (PRIORITAS.size() < 512) {
			PRIORITAS.addLast(new Prioritas(t.kunci, clipped));
		}
	}

	/** As {@link #prioritas} but at server tick {@code saat} (TNT after its fuse, fire after it spread). */
	static void prioritasNanti(TempatRawat t, BoundingBox kotak, long saat) {
		if (NANTI.size() < 256) {
			NANTI.add(new Nanti(t.kunci, kotak, saat));
		}
	}

	static void tick(MinecraftServer server) {
		if (!NANTI.isEmpty()) {
			long tick = server.getTickCount();
			PerbaikanData pd = PerbaikanData.get(server);
			for (java.util.Iterator<Nanti> it = NANTI.iterator(); it.hasNext(); ) {
				Nanti n = it.next();
				if (tick >= n.saat()) {
					it.remove();
					TempatRawat t = pd.tempat.get(n.kunci());
					if (t != null) {
						prioritas(t, n.kotak());
					}
				}
			}
		}
		if (server.getAverageTickTimeNanos() > KultivatorConfig.get().kehancuranMsptPause * 1_000_000L) {
			return;
		}
		PerbaikanData data = PerbaikanData.get(server);
		if (server.getTickCount() % 20 == 0) {
			aktif = hitungAktif(server, data);
		}
		int budget = BACA_PER_TICK;
		while (budget > 0 && !PRIORITAS.isEmpty()) {
			Prioritas p = PRIORITAS.pollFirst();
			TempatRawat t = data.tempat.get(p.kunci());
			ServerLevel level = t == null ? null : server.getLevel(t.dimensi);
			if (t != null && level != null) {
				budget -= kotak(level, t, p.kotak());
			}
		}
		if (aktif.isEmpty() || budget <= 0) {
			return;
		}
		long now = server.getTickCount();
		int interval = PerbaikanConfig.get().pindaiTick;
		for (int n = 0; n < aktif.size() && budget > 0; n++) {
			TempatRawat t = aktif.get(putar++ % aktif.size());
			ServerLevel level = server.getLevel(t.dimensi);
			if (level == null) {
				continue;
			}
			Kursor k = KURSOR.computeIfAbsent(t.kunci, key -> new Kursor());
			if (k.selesai) {
				if (now - k.mulai < interval) {
					continue;
				}
				k.selesai = false;
				k.chunk = 0;
				k.voxel = 0;
				k.mulai = now;
				Rekam.situs(level, t, false);
			}
			budget = langkah(level, t, k, budget);
		}
	}

	private static List<TempatRawat> hitungAktif(MinecraftServer server, PerbaikanData data) {
		if (data.tempat.isEmpty()) {
			return List.of();
		}
		List<TempatRawat> out = new ArrayList<>();
		for (TempatRawat t : data.tempat.values()) {
			ServerLevel level = server.getLevel(t.dimensi);
			if (level == null) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (t.jarakKotakSqr(player.blockPosition()) <= JARAK_AKTIF * JARAK_AKTIF) {
					out.add(t);
					break;
				}
			}
		}
		return out;
	}

	/** One budgeted walk of the place's slices from its cursor; returns the budget left. */
	private static int langkah(ServerLevel level, TempatRawat t, Kursor k, int budget) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		while (budget > 0 && k.chunk < t.chunks.length) {
			long key = t.chunks[k.chunk];
			LevelChunk chunk = t.terekam.contains(key) ? level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) : null;
			Cetak cetak = chunk == null ? null : Rekam.cetak(t, key);
			if (chunk == null || cetak == null) {
				if (chunk != null && t.terekam.contains(key)) {
					// the slice file was lost: record again (an honest new baseline)
					t.terekam.remove(key);
					Rekam.antreLagi(t.dimensi, key);
				}
				k.chunk++;
				k.voxel = 0;
				budget--;
				continue;
			}
			int size = cetak.ukuran();
			while (budget > 0 && k.voxel < size) {
				int i = k.voxel++;
				BlockState rec = cetak.voxel(i);
				if (rec == null) {
					budget -= (i & 3) == 0 ? 1 : 0;
					continue;
				}
				budget--;
				baca++;
				cetak.posisi(i, at);
				BlockState now = chunk.getBlockState(at);
				if (now == rec) {
					continue;
				}
				banding(level, t, cetak, at.immutable(), rec, now, cetak.beVoxel(i));
			}
			if (k.voxel >= size) {
				k.chunk++;
				k.voxel = 0;
			}
		}
		if (k.chunk >= t.chunks.length) {
			k.selesai = true;
		}
		return budget;
	}

	/** Reads one event box; returns the reads used. */
	private static int kotak(ServerLevel level, TempatRawat t, BoundingBox box) {
		int reads = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				long key = ChunkPos.pack(cx, cz);
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				Cetak cetak = chunk == null ? null : Rekam.cetak(t, key);
				if (cetak == null) {
					continue;
				}
				for (int x = Math.max(box.minX(), cx << 4); x <= Math.min(box.maxX(), (cx << 4) + 15); x++) {
					for (int z = Math.max(box.minZ(), cz << 4); z <= Math.min(box.maxZ(), (cz << 4) + 15); z++) {
						for (int y = Math.max(box.minY(), cetak.minY); y <= Math.min(box.maxY(), cetak.maxY); y++) {
							BlockState rec = cetak.di(x, y, z);
							if (rec == null) {
								continue;
							}
							reads++;
							at.set(x, y, z);
							BlockState now = chunk.getBlockState(at);
							if (now != rec) {
								BlockPos pos = at.immutable();
								banding(level, t, cetak, pos, rec, now, cetak.beDi(pos));
							}
						}
					}
				}
			}
		}
		baca += reads;
		return Math.max(1, reads);
	}

	private static void banding(ServerLevel level, TempatRawat t, Cetak cetak, BlockPos pos, BlockState rec, BlockState now, CompoundTag be) {
		if (PekerjaanPerbaikan.diketahui(t.kunci, pos.asLong())) {
			return;
		}
		BlockState target = Banding.rusak(rec, now, cetak, pos, t);
		if (target == null) {
			return;
		}
		LogAtribusi.Atribusi why = LogAtribusi.cari(t, pos, rec, now, level.getGameTime());
		if (why == null) {
			// nobody damaged it (worldgen settling, growth, gravity, weathering, another mod): the blueprint takes it
			if (cetak.terima(pos, now)) {
				CetakStore.put(t.dimensi, cetak);
				diterima++;
			}
			return;
		}
		PekerjaanPerbaikan.temukan(level, t, pos, rec, now, target, be, why);
	}

	/** Natural changes the blueprints took since the server started (for {@code uji tempat cetak periksa}). */
	static long diterima() {
		return diterima;
	}

	private PindaiTempat() {
	}
}
