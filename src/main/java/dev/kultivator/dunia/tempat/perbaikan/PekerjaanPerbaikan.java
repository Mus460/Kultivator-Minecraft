package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.kehancuran.WorldBreaker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Damage clusters and their repair jobs (DESIGN 38.R.2-38.R.3, 38.R.6).
 * <ul>
 *   <li>{@link #temukan}: a difference the scanner found becomes an operation of a cluster (claims left out, cause from
 *       {@link LogAtribusi}, loot tables kept only for monster damage, rule 5).</li>
 *   <li>Once a cluster has not grown for two minutes it is reported ({@code TempatEvents.TEMPAT_RUSAK}), billed or
 *       declared "help wanted" ({@link Penagihan#putuskan}), and the crew starts.</li>
 *   <li>Every second the crew works off operations at {@code craftsmen / detikPerBlok} blocks per second times the
 *       multipliers (hired x4, afraid x0.33, donations x2, a player helping x1.5), at most one block per second per place
 *       (60 a minute). Worked-off operations whose chunk is loaded go to {@link WorldBreaker#submitRestore} as one batch;
 *       those of unloaded chunks wait, so a place left alone is still repaired "on paper" and put back at once when the
 *       chunk loads again (within the repair budget).</li>
 * </ul>
 * Failures are planned again with the block the world holds now; a filled container in the way blocks its operation
 * until it is emptied; an operation that keeps failing is given up. Server thread only.
 */
public final class PekerjaanPerbaikan {

	/** Blocks per second one place may get back at most (60 a minute, 38.R.3). */
	public static final double MAKS_PER_DETIK = 1.0D;
	/** Operations per block batch. */
	public static final int BATCH = 64;
	/** Failures before an operation is given up. */
	public static final int MAKS_GAGAL = 12;

	/** Place key -> positions with open operations (the scanner skips them). */
	private static final Map<Long, LongOpenHashSet> DIKETAHUI = new HashMap<>();
	/** Place id -> repair rate already granted this second. */
	private static final Map<Long, Double> JATAH = new HashMap<>();
	/** Cluster id -> game time the foreman last asked to move a container. */
	private static final Map<Long, Long> MINTA_PINDAH = new HashMap<>();
	private static int dipulihkan;

	static void mulai(MinecraftServer server) {
		DIKETAHUI.clear();
		JATAH.clear();
		MINTA_PINDAH.clear();
		for (Gugus g : PerbaikanData.get(server).gugus.values()) {
			for (Gugus.Op op : g.ops) {
				if (op.terbuka() || op.status == Gugus.MENGHALANGI) {
					DIKETAHUI.computeIfAbsent(g.kunci, k -> new LongOpenHashSet()).add(op.pos);
				}
			}
		}
	}

	static void berhenti() {
		DIKETAHUI.clear();
		JATAH.clear();
		MINTA_PINDAH.clear();
	}

	static boolean diketahui(long kunci, long pos) {
		LongOpenHashSet set = DIKETAHUI.get(kunci);
		return set != null && set.contains(pos);
	}

	/** Blocks put back by repairs this session (for {@code uji tempat gugus}). */
	static int dipulihkan() {
		return dipulihkan;
	}

	// ================================================================== finding damage

	/**
	 * Records the difference at {@code pos}: the world holds {@code now} where the blueprint has {@code rec}; the repair
	 * target is {@code target} (crop age 0, air for a foreign block). {@code be} is the slice's block-entity tag.
	 */
	static void temukan(ServerLevel level, TempatRawat t, BlockPos pos, BlockState rec, BlockState now, BlockState target, @Nullable CompoundTag be) {
		long key = pos.asLong();
		if (diketahui(t.kunci, key) || KlaimPemain.diklaim(level, pos)) {
			return;
		}
		long time = level.getGameTime();
		LogAtribusi.Atribusi why = LogAtribusi.cari(t, pos, rec, now, time);
		CompoundTag tag = be;
		if (tag != null && tag.getBooleanOr("kultivator_jarahan", false)) {
			// rule 5: a looted chest comes back empty; only monsters and raids leave the loot table in place
			tag = Gugus.MONSTER.equals(why.sebab()) || Gugus.SERANGAN.equals(why.sebab()) ? stripMarker(tag) : null;
		}
		PerbaikanData data = PerbaikanData.get(level.getServer());
		Gugus.Op op = new Gugus.Op(key, target, now, tag, why.sebab(), why.pelaku(), Banding.urut(target, now, pos, t.kotak.minY()));
		if (wadahBerisi(level, pos)) {
			op.status = Gugus.MENGHALANGI;
		}
		Gugus home = null;
		for (Gugus g : data.gugus.values()) {
			if (g.kunci == t.kunci && !g.dilaporkan && g.dekat(pos, time)) {
				home = g;
				break;
			}
		}
		if (home == null) {
			home = new Gugus(data.id(), t.kunci, t.situsId, new BoundingBox(pos), time);
			data.gugus.put(home.id, home);
		}
		if (home.tambah(op)) {
			home.terakhir = time;
			DIKETAHUI.computeIfAbsent(t.kunci, k -> new LongOpenHashSet()).add(key);
			data.kotor();
		}
	}

	private static CompoundTag stripMarker(CompoundTag tag) {
		CompoundTag copy = tag.copy();
		copy.remove("kultivator_jarahan");
		return copy;
	}

	private static boolean wadahBerisi(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).hasBlockEntity() && level.getBlockEntity(pos) instanceof Container c && !c.isEmpty();
	}

	// ================================================================== the crews' second

	/** Once a second: reports, bills and the work of every cluster. */
	static void tick(MinecraftServer server) {
		PerbaikanData data = PerbaikanData.get(server);
		if (data.gugus.isEmpty()) {
			return;
		}
		JATAH.clear();
		PerbaikanConfig config = PerbaikanConfig.get();
		long now = server.overworld().getGameTime();
		List<Gugus> list = new ArrayList<>(data.gugus.values());
		for (Gugus g : list) {
			TempatRawat t = data.tempat.get(g.kunci);
			ServerLevel level = t == null ? null : server.getLevel(t.dimensi);
			if (t == null || level == null) {
				hapus(server, g);
				continue;
			}
			try {
				if (!g.dilaporkan) {
					if (now - g.terakhir < Gugus.WAKTU_GABUNG) {
						continue;
					}
					lapor(level, t, g);
				}
				kerja(level, t, g, config, now);
				if (g.tuntas()) {
					tuntas(level, t, g);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: a repair job of {} failed; it is dropped", t.struktur, exception);
				hapus(server, g);
			}
		}
	}

	private static void lapor(ServerLevel level, TempatRawat t, Gugus g) {
		g.dilaporkan = true;
		PerbaikanData.get(level.getServer()).kotor();
		TempatEvents.fireTempatRusak(level, t.situsId, g.kotak, g.ops.size(), g.pelaku());
		Penagihan.putuskan(level, t, g);
		if (g.ops.size() >= 12) {
			CeritaApi.kabar(level, t.situsId, g.tanpaPelaku() ? "perbaikan.rusak_monster" : "perbaikan.rusak_pemain", g.ops.size());
		}
	}

	private static void kerja(ServerLevel level, TempatRawat t, Gugus g, PerbaikanConfig config, long now) {
		JenisTempat.Profil profil = t.profil();
		int bodies = Tukang.bekerja(g);
		int crew = bodies > 0 ? bodies : profil.tukangDasar() + (g.sewa ? 3 : 0);
		double mult = 1.0D;
		if (g.sewa) {
			mult *= config.pengaliSewa;
		}
		if (g.takut) {
			mult *= config.pengaliTakut;
		}
		Long donation = PerbaikanData.get(level.getServer()).donasiSampai.get(t.situsId);
		if (donation != null && donation > now) {
			mult *= 2.0D;
		}
		if (Bantu.membantu(g)) {
			mult *= 1.5D;
		}
		double rate = crew / (double) Math.max(1, config.detikPerBlok) * mult;
		double used = JATAH.getOrDefault(t.situsId, 0.0D);
		rate = Math.max(0.0D, Math.min(rate, MAKS_PER_DETIK - used));
		JATAH.put(t.situsId, used + rate);
		g.kemajuan = Math.min(g.kemajuan + rate, 8.0D);
		List<Gugus.Op> waiting = new ArrayList<>();
		for (Gugus.Op op : g.ops) {
			if (op.status == Gugus.MENUNGGU) {
				waiting.add(op);
			}
		}
		waiting.sort(Comparator.comparingInt(op -> op.urut));
		for (Gugus.Op op : waiting) {
			if (g.kemajuan < 1.0D) {
				break;
			}
			op.status = Gugus.SIAP;
			g.kemajuan -= 1.0D;
		}
		if (now % 600L < 20L) {
			cekHalangan(level, t, g, now);
		}
		kirim(level, t, g);
	}

	/** Re-opens operations whose container was emptied; the foreman asks nearby players to move filled ones (rule 3). */
	private static void cekHalangan(ServerLevel level, TempatRawat t, Gugus g, long now) {
		boolean blocked = false;
		for (Gugus.Op op : g.ops) {
			if (op.status != Gugus.MENGHALANGI) {
				continue;
			}
			BlockPos pos = BlockPos.of(op.pos);
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (wadahBerisi(level, pos)) {
				if (now - g.dibuat > 72000L) {
					// three world days in the way: the crew works around it
					op.status = Gugus.BATAL;
					LongOpenHashSet known = DIKETAHUI.get(t.kunci);
					if (known != null) {
						known.remove(op.pos);
					}
				} else {
					blocked = true;
				}
			} else {
				op.ditemukan = level.getBlockState(pos);
				op.status = Gugus.MENUNGGU;
				op.gagal = 0;
			}
		}
		Long asked = MINTA_PINDAH.get(g.id);
		if (blocked && (asked == null || now - asked > 2400L)) {
			MINTA_PINDAH.put(g.id, now);
			Tukang.mintaPindah(level, t, g);
		}
	}

	/** Sends the worked-off operations of loaded chunks as one restore batch. */
	private static void kirim(ServerLevel level, TempatRawat t, Gugus g) {
		List<Gugus.Op> batch = new ArrayList<>();
		LongOpenHashSet pending = new LongOpenHashSet();
		for (Gugus.Op op : g.ops) {
			if (op.terbuka()) {
				pending.add(op.pos);
			}
		}
		List<Gugus.Op> ready = new ArrayList<>();
		for (Gugus.Op op : g.ops) {
			if (op.status == Gugus.SIAP) {
				ready.add(op);
			}
		}
		ready.sort(Comparator.comparingInt(op -> op.urut));
		for (Gugus.Op op : ready) {
			if (batch.size() >= BATCH) {
				break;
			}
			BlockPos pos = BlockPos.of(op.pos);
			if (!level.isLoaded(pos)) {
				continue;
			}
			// rule 7: a gravity block waits for its support (an open operation below it)
			if (Banding.butuhTopang(op.target) && pending.contains(pos.below().asLong()) && level.getBlockState(pos.below()).isAir()) {
				continue;
			}
			batch.add(op);
		}
		if (batch.isEmpty()) {
			return;
		}
		int n = batch.size();
		long[] positions = new long[n];
		BlockState[] targets = new BlockState[n];
		BlockState[] expected = new BlockState[n];
		CompoundTag[] tags = new CompoundTag[n];
		boolean anyTag = false;
		for (int i = 0; i < n; i++) {
			Gugus.Op op = batch.get(i);
			positions[i] = op.pos;
			targets[i] = op.target;
			// fire is put out first (rule 8): it is "expected" so the restore may replace it
			expected[i] = op.ditemukan;
			tags[i] = op.be;
			anyTag |= op.be != null;
			op.status = Gugus.TERKIRIM;
		}
		final List<Gugus.Op> sent = List.copyOf(batch);
		boolean queued = WorldBreaker.submitRestore(new WorldBreaker.RestoreRequest(level.dimension(), positions, targets, expected, anyTag ? tags : null,
				"perbaikan:" + t.struktur, result -> selesaiBatch(level, t, g, sent, result)));
		if (!queued) {
			for (Gugus.Op op : batch) {
				op.status = Gugus.SIAP;
			}
		}
	}

	private static void selesaiBatch(ServerLevel level, TempatRawat t, Gugus g, List<Gugus.Op> sent, boolean[] result) {
		PerbaikanData data = PerbaikanData.get(level.getServer());
		LongOpenHashSet known = DIKETAHUI.get(t.kunci);
		for (int i = 0; i < sent.size(); i++) {
			Gugus.Op op = sent.get(i);
			if (op.status != Gugus.TERKIRIM) {
				continue;
			}
			BlockPos pos = BlockPos.of(op.pos);
			if (i < result.length && result[i]) {
				op.status = Gugus.SELESAI;
				dipulihkan++;
				if (known != null) {
					known.remove(op.pos);
				}
				seized(level, t, op);
				continue;
			}
			if (!level.isLoaded(pos)) {
				op.status = Gugus.SIAP;
				continue;
			}
			BlockState now = level.getBlockState(pos);
			if (now == op.target) {
				op.status = Gugus.SELESAI;
				if (known != null) {
					known.remove(op.pos);
				}
				continue;
			}
			if (wadahBerisi(level, pos)) {
				op.status = Gugus.MENGHALANGI;
				continue;
			}
			op.ditemukan = now;
			op.gagal++;
			op.status = op.gagal > MAKS_GAGAL ? Gugus.BATAL : Gugus.SIAP;
			if (op.status == Gugus.BATAL && known != null) {
				known.remove(op.pos);
			}
		}
		data.kotor();
	}

	/** Rule 4: a foreign block taken away goes to the place's seized goods in its placer's name. */
	private static void seized(ServerLevel level, TempatRawat t, Gugus.Op op) {
		BlockState found = op.ditemukan;
		if (found.isAir() || found.getBlock() instanceof BaseFireBlock || !found.getFluidState().isEmpty() && found.getFluidState().isSource()
				&& found.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
			return;
		}
		if (found.getBlock() == op.target.getBlock()) {
			return;
		}
		UUID owner = Gugus.PASANG.equals(op.sebab) ? op.pelaku : null;
		Sitaan.tambah(level, t, found, owner);
	}

	private static void tuntas(ServerLevel level, TempatRawat t, Gugus g) {
		Tukang.bubar(level, g, true);
		if (g.ops.size() >= 12) {
			CeritaApi.kabar(level, t.situsId, "perbaikan.selesai", g.selesai());
		}
		hapus(level.getServer(), g);
	}

	static void hapus(MinecraftServer server, Gugus g) {
		PerbaikanData data = PerbaikanData.get(server);
		data.gugus.remove(g.id);
		LongOpenHashSet known = DIKETAHUI.get(g.kunci);
		if (known != null) {
			for (Gugus.Op op : g.ops) {
				known.remove(op.pos);
			}
		}
		MINTA_PINDAH.remove(g.id);
		Tukang.lupakan(g);
		data.kotor();
	}

	// ================================================================== test helpers (uji tempat perbaiki)

	/** Works off every open operation of the place now ({@code perbaiki cepat}): the batches still obey the budget. */
	static int cepat(MinecraftServer server, TempatRawat t) {
		int n = 0;
		for (Gugus g : PerbaikanData.get(server).gugus.values()) {
			if (g.kunci != t.kunci) {
				continue;
			}
			g.dilaporkan = true;
			for (Gugus.Op op : g.ops) {
				if (op.status == Gugus.MENUNGGU) {
					op.status = Gugus.SIAP;
					n++;
				}
			}
		}
		return n;
	}

	/** Clusters of place {@code kunci} grown since game time {@code sejak} report now ({@code uji tempat rusak}). */
	static void segerakan(MinecraftServer server, long kunci, long sejak) {
		for (Gugus g : PerbaikanData.get(server).gugus.values()) {
			if (g.kunci == kunci && !g.dilaporkan && g.terakhir >= sejak) {
				g.terakhir = Math.min(g.terakhir, server.overworld().getGameTime() - Gugus.WAKTU_GABUNG - 1L);
			}
		}
	}

	/** Drops every cluster of the place ({@code perbaiki batal}); the damage is found again by the next scan. */
	static int batal(MinecraftServer server, TempatRawat t) {
		List<Gugus> drop = new ArrayList<>();
		for (Gugus g : PerbaikanData.get(server).gugus.values()) {
			if (g.kunci == t.kunci) {
				drop.add(g);
			}
		}
		for (Gugus g : drop) {
			ServerLevel level = server.getLevel(t.dimensi);
			if (level != null) {
				Tukang.bubar(level, g, false);
			}
			hapus(server, g);
		}
		return drop.size();
	}

	private PekerjaanPerbaikan() {
	}
}
