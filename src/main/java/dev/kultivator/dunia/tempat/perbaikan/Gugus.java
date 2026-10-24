package dev.kultivator.dunia.tempat.perbaikan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A damage cluster of one cared-for place (DESIGN 38.R.2-38.R.3): damage entries within 6 blocks and 2 minutes of each
 * other, with their repair operations, culprits and the job's progress. One repair job per cluster. A cluster has a
 * culprit when at least 60 % of its attributed entries point to one player.
 */
public final class Gugus {

	/** Entries this close (blocks) to a cluster's box join it ... */
	public static final int JARAK_GABUNG = 6;
	/** ... when its last entry is at most this old (ticks: 2 minutes). */
	public static final long WAKTU_GABUNG = 2400L;
	/** Operations per cluster at most (a bigger crater becomes several clusters). */
	public static final int MAKS_OP = 2048;

	/** Causes of 38.R.2 (also lang suffixes {@code kultivator.tempat_perbaikan.sebab.<id>}). */
	public static final String PECAH = "pecah";
	public static final String PASANG = "pasang";
	public static final String TNT = "ledakan_tnt";
	public static final String API = "api";
	public static final String KEHANCURAN = "kehancuran";
	public static final String SERANGAN = "serangan";
	public static final String MONSTER = "monster";
	public static final String UJI = "uji";

	/** Operation states. */
	public static final byte MENUNGGU = 0;
	/** Worked off by a craftsman, waiting for the next block batch (or for its chunk to load). */
	public static final byte SIAP = 1;
	/** In a {@code WorldBreaker} batch now. */
	public static final byte TERKIRIM = 2;
	public static final byte SELESAI = 3;
	/** A filled container stands in the way (the foreman asks the player to move it). */
	public static final byte MENGHALANGI = 4;
	/** Given up (it kept failing). */
	public static final byte BATAL = 5;

	/** One repair operation: put {@code target} at {@code pos} where the world held {@code ditemukan}. */
	public static final class Op {
		public final long pos;
		public final BlockState target;
		public BlockState ditemukan;
		public final @Nullable CompoundTag be;
		public final String sebab;
		public final @Nullable UUID pelaku;
		public final int urut;
		public byte status = MENUNGGU;
		public int gagal;

		Op(long pos, BlockState target, BlockState ditemukan, @Nullable CompoundTag be, String sebab, @Nullable UUID pelaku, int urut) {
			this.pos = pos;
			this.target = target;
			this.ditemukan = ditemukan;
			this.be = be;
			this.sebab = sebab;
			this.pelaku = pelaku;
			this.urut = urut;
		}

		public boolean terbuka() {
			return status == MENUNGGU || status == SIAP || status == TERKIRIM;
		}
	}

	public final long id;
	public final long kunci;
	public long situsId;
	public BoundingBox kotak;
	public final List<Op> ops = new ArrayList<>();
	public final long dibuat;
	public long terakhir;
	/** Work units banked toward the next operation (1.0 = one block). */
	public double kemajuan;
	/** A paid bill hired the crew (x {@code pengaliSewa}, three more craftsmen). */
	public boolean sewa;
	/** The place is afraid of the culprit (x {@code pengaliTakut}). */
	public boolean takut;
	/** The bill made for this cluster (0 = none yet, -1 = decided: no bill). */
	public long tagihanId;
	/** Monster / raid damage: the foreman posted "Mohon bantuan" (the donation sheet is offered). */
	public boolean mohonBantuan;
	/** The event fired once the cluster stopped growing. */
	public boolean dilaporkan;

	Gugus(long id, long kunci, long situsId, BoundingBox kotak, long dibuat) {
		this.id = id;
		this.kunci = kunci;
		this.situsId = situsId;
		this.kotak = kotak;
		this.dibuat = dibuat;
		this.terakhir = dibuat;
	}

	/** Adds an operation, growing the box. False when the cluster is full or already holds that position. */
	boolean tambah(Op op) {
		if (ops.size() >= MAKS_OP) {
			return false;
		}
		for (Op o : ops) {
			if (o.pos == op.pos && o.terbuka()) {
				return false;
			}
		}
		ops.add(op);
		BlockPos p = BlockPos.of(op.pos);
		kotak = new BoundingBox(Math.min(kotak.minX(), p.getX()), Math.min(kotak.minY(), p.getY()), Math.min(kotak.minZ(), p.getZ()),
				Math.max(kotak.maxX(), p.getX()), Math.max(kotak.maxY(), p.getY()), Math.max(kotak.maxZ(), p.getZ()));
		return true;
	}

	/** True when {@code pos} at game time {@code now} may join this cluster. */
	boolean dekat(BlockPos pos, long now) {
		if (now - terakhir > WAKTU_GABUNG || ops.size() >= MAKS_OP) {
			return false;
		}
		return pos.getX() >= kotak.minX() - JARAK_GABUNG && pos.getX() <= kotak.maxX() + JARAK_GABUNG
				&& pos.getY() >= kotak.minY() - JARAK_GABUNG && pos.getY() <= kotak.maxY() + JARAK_GABUNG
				&& pos.getZ() >= kotak.minZ() - JARAK_GABUNG && pos.getZ() <= kotak.maxZ() + JARAK_GABUNG;
	}

	/** Open operations left. */
	public int sisa() {
		int n = 0;
		for (Op op : ops) {
			if (op.terbuka()) {
				n++;
			}
		}
		return n;
	}

	public int selesai() {
		int n = 0;
		for (Op op : ops) {
			if (op.status == SELESAI) {
				n++;
			}
		}
		return n;
	}

	/** Repair progress 0..1 (done or given up over all). */
	public float persen() {
		if (ops.isEmpty()) {
			return 1.0F;
		}
		int closed = 0;
		for (Op op : ops) {
			if (!op.terbuka()) {
				closed++;
			}
		}
		return closed / (float) ops.size();
	}

	/** True when nothing is left to do (a container still in the way keeps the job open until it is moved or given up). */
	public boolean tuntas() {
		for (Op op : ops) {
			if (op.terbuka() || op.status == MENGHALANGI) {
				return false;
			}
		}
		return true;
	}

	/** The culprit (60 % of the attributed entries), or null. */
	public @Nullable UUID pelaku() {
		Map<UUID, Integer> count = new HashMap<>();
		int attributed = 0;
		for (Op op : ops) {
			if (op.pelaku != null) {
				count.merge(op.pelaku, 1, Integer::sum);
				attributed++;
			}
		}
		UUID best = null;
		int bestCount = 0;
		for (Map.Entry<UUID, Integer> e : count.entrySet()) {
			if (e.getValue() > bestCount) {
				best = e.getKey();
				bestCount = e.getValue();
			}
		}
		return best != null && bestCount * 10 >= attributed * 6 ? best : null;
	}

	/** Every player with attributed entries and their entry count (several culprits are billed their share). */
	public Map<UUID, Integer> pelakuSemua() {
		Map<UUID, Integer> count = new HashMap<>();
		for (Op op : ops) {
			if (op.pelaku != null) {
				count.merge(op.pelaku, 1, Integer::sum);
			}
		}
		return count;
	}

	/** The most common cause. */
	public String sebabUtama() {
		Map<String, Integer> count = new HashMap<>();
		for (Op op : ops) {
			count.merge(op.sebab, 1, Integer::sum);
		}
		String best = MONSTER;
		int n = 0;
		for (Map.Entry<String, Integer> e : count.entrySet()) {
			if (e.getValue() > n) {
				best = e.getKey();
				n = e.getValue();
			}
		}
		return best;
	}

	/** True when nobody is to blame (monsters, raids, unknown): the donation variant is offered. */
	public boolean tanpaPelaku() {
		for (Op op : ops) {
			if (op.pelaku != null) {
				return false;
			}
		}
		return true;
	}

	// ================================================================== saving

	CompoundTag simpan() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("id", id);
		tag.putLong("k", kunci);
		tag.putLong("situs", situsId);
		tag.put("kotak", TempatRawat.box(kotak));
		tag.putLong("dibuat", dibuat);
		tag.putLong("akhir", terakhir);
		tag.putDouble("maju", kemajuan);
		tag.putBoolean("sewa", sewa);
		tag.putBoolean("takut", takut);
		tag.putLong("tagihan", tagihanId);
		tag.putBoolean("bantuan", mohonBantuan);
		tag.putBoolean("lapor", dilaporkan);
		ListTag list = new ListTag();
		for (Op op : ops) {
			if (!op.terbuka() && op.status != MENGHALANGI) {
				continue;
			}
			CompoundTag o = new CompoundTag();
			o.putLong("p", op.pos);
			o.put("t", NbtUtils.writeBlockState(op.target));
			o.put("d", NbtUtils.writeBlockState(op.ditemukan));
			if (op.be != null) {
				o.put("be", op.be);
			}
			o.putString("s", op.sebab);
			if (op.pelaku != null) {
				o.store("u", UUIDUtil.CODEC, op.pelaku);
			}
			o.putInt("o", op.urut);
			// a batch in flight when saving is simply worked off again (SIAP)
			o.putByte("st", op.status == TERKIRIM ? SIAP : op.status);
			o.putInt("g", op.gagal);
			list.add(o);
		}
		tag.put("ops", list);
		return tag;
	}

	static @Nullable Gugus muat(CompoundTag tag, HolderGetter<Block> blocks) {
		BoundingBox kotak = TempatRawat.box(tag.get("kotak"));
		if (kotak == null) {
			return null;
		}
		Gugus g = new Gugus(tag.getLongOr("id", 0L), tag.getLongOr("k", 0L), tag.getLongOr("situs", 0L), kotak, tag.getLongOr("dibuat", 0L));
		g.terakhir = tag.getLongOr("akhir", g.dibuat);
		g.kemajuan = tag.getDoubleOr("maju", 0.0D);
		g.sewa = tag.getBooleanOr("sewa", false);
		g.takut = tag.getBooleanOr("takut", false);
		g.tagihanId = tag.getLongOr("tagihan", 0L);
		g.mohonBantuan = tag.getBooleanOr("bantuan", false);
		g.dilaporkan = tag.getBooleanOr("lapor", false);
		for (Tag t : tag.getListOrEmpty("ops")) {
			if (!(t instanceof CompoundTag o)) {
				continue;
			}
			BlockState target = o.get("t") instanceof CompoundTag ts ? NbtUtils.readBlockState(blocks, ts) : Blocks.AIR.defaultBlockState();
			BlockState found = o.get("d") instanceof CompoundTag ds ? NbtUtils.readBlockState(blocks, ds) : Blocks.AIR.defaultBlockState();
			UUID who = o.read("u", UUIDUtil.CODEC).orElse(null);
			Op op = new Op(o.getLongOr("p", 0L), target, found, o.getCompound("be").orElse(null), o.getStringOr("s", MONSTER), who, o.getIntOr("o", 0));
			op.status = o.getByteOr("st", MENUNGGU);
			op.gagal = o.getIntOr("g", 0);
			g.ops.add(op);
		}
		return g;
	}
}
