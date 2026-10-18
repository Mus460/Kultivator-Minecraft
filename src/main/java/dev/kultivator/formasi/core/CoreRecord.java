package dev.kultivator.formasi.core;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.formasi.keeper.KeeperOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One registered (or virtual) sect formation core (DESIGN 24.12): the saved fields plus the runtime state the ticker
 * derives (never saved). Only data; the blocks are never touched.
 */
public final class CoreRecord {

	/** A saved keeper assignment. */
	public static final class Keeper {
		public final UUID soul;
		public KeeperOrder order;

		public Keeper(UUID soul, KeeperOrder order) {
			this.soul = soul;
			this.order = order;
		}
	}

	/** A pretend keeper of {@code uji formasi penjaga semu} (session only). */
	public record Pretend(Realm realm, int sectRank, @Nullable Dao dao, KeeperOrder order) {
	}

	// ---- saved
	public final int id;
	public final ResourceKey<Level> dim;
	public String spec;
	public final BlockPos eye;
	public int rot;
	public final UUID owner;
	public String ownerName;
	public int kind;
	public boolean on;
	/** Centi-Daya. */
	public long daya;
	public double carry;
	/** Best stone grade since the core was last empty (-1 amethyst or none). */
	public int best = -1;
	public boolean draw = true;
	public final List<Keeper> keepers = new ArrayList<>();
	public CompoundTag ext = new CompoundTag();
	public long createdDay;
	public CompoundTag stats = new CompoundTag();

	// ---- virtual (never saved)
	public final boolean virtual;
	public long expires;
	public boolean forced;

	// ---- runtime
	public CoreState state = CoreState.DORMANT;
	public @Nullable Component reason;
	public boolean idle;
	public boolean cracked;
	public long crackSince = -1L;
	/** Per slot: 0 unknown, 1 ok, 2 missing, 3 wrong (index into the rotated slot list). */
	public byte[] slotState = new byte[0];
	public int cursor;
	public int missing;
	public int unknown;
	public long lastFullVerify;
	public long nextPulse;
	public long lastPaid = -1L;
	public long lastBrokenNotice = -100_000L;
	public long lastKasPull = -100_000L;
	public long lastUse = -100_000L;
	public double power = 1.0D;
	public double radius;
	public double rate;
	public double vein = 1.0D;
	public @Nullable Component slotReason;
	public @Nullable Component limitReason;
	public int flaggedBy = -1;
	public int flagTarget = -1;
	public boolean flagKeeper;
	// keeper cache (Keepers.refresh, once per second)
	public int keepersAtPost;
	public double keeperUpkeep = 1.0D;
	public double keeperK;
	public boolean keeperAffinity;
	public boolean keeperDalam;
	public boolean keeperInti;
	public boolean zeroCost;
	public final List<Pretend> pretend = new ArrayList<>();
	/** Free scratch values of the handler (M5 cushion count, L1 last activity...). */
	public final CompoundTag scratch = new CompoundTag();

	public CoreRecord(int id, ResourceKey<Level> dim, String spec, BlockPos eye, int rot, UUID owner, String ownerName, int kind,
			boolean virtual) {
		this.id = id;
		this.dim = dim;
		this.spec = spec;
		this.eye = eye.immutable();
		this.rot = Math.floorMod(rot, 4);
		this.owner = owner;
		this.ownerName = ownerName == null ? "" : ownerName;
		this.kind = kind;
		this.virtual = virtual;
	}

	/** True when the pattern is complete (or cracked under a flag). */
	public boolean complete() {
		return missing == 0 || cracked;
	}

	public boolean hasKeeper(UUID soul) {
		for (Keeper keeper : keepers) {
			if (keeper.soul.equals(soul)) {
				return true;
			}
		}
		return false;
	}

	public @Nullable Keeper keeper(UUID soul) {
		for (Keeper keeper : keepers) {
			if (keeper.soul.equals(soul)) {
				return keeper;
			}
		}
		return null;
	}

	/** Adds one to a stats counter. */
	public void stat(String key, int amount) {
		stats.putInt(key, stats.getIntOr(key, 0) + amount);
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("id", id);
		tag.putString("spec", spec);
		tag.putInt("x", eye.getX());
		tag.putInt("y", eye.getY());
		tag.putInt("z", eye.getZ());
		tag.putInt("rot", rot);
		tag.store("owner", UUIDUtil.CODEC, owner);
		tag.putString("owner_name", ownerName);
		tag.putInt("kind", kind);
		tag.putBoolean("on", on);
		tag.putLong("daya", daya);
		tag.putDouble("carry", carry);
		tag.putInt("best", best);
		tag.putBoolean("draw", draw);
		ListTag list = new ListTag();
		for (Keeper keeper : keepers) {
			CompoundTag entry = new CompoundTag();
			entry.store("u", UUIDUtil.CODEC, keeper.soul);
			entry.putString("order", keeper.order.id());
			list.add(entry);
		}
		tag.put("keepers", list);
		tag.put("ext", ext.copy());
		tag.putLong("created_day", createdDay);
		tag.put("stats", stats.copy());
		return tag;
	}

	public static @Nullable CoreRecord load(ResourceKey<Level> dim, CompoundTag tag) {
		UUID owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
		int id = tag.getIntOr("id", -1);
		if (owner == null || id < 0) {
			return null;
		}
		CoreRecord record = new CoreRecord(id, dim, tag.getStringOr("spec", ""),
				new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0)), tag.getIntOr("rot", 0), owner,
				tag.getStringOr("owner_name", ""), tag.getIntOr("kind", 1), false);
		record.on = tag.getBooleanOr("on", false);
		record.daya = Math.max(0L, tag.getLongOr("daya", 0L));
		record.carry = tag.getDoubleOr("carry", 0.0D);
		record.best = tag.getIntOr("best", -1);
		record.draw = tag.getBooleanOr("draw", true);
		for (Tag each : tag.getListOrEmpty("keepers")) {
			if (each instanceof CompoundTag entry) {
				UUID soul = entry.read("u", UUIDUtil.CODEC).orElse(null);
				KeeperOrder order = KeeperOrder.byId(entry.getStringOr("order", "jaga"));
				if (soul != null) {
					record.keepers.add(new Keeper(soul, order == null ? KeeperOrder.JAGA : order));
				}
			}
		}
		record.ext = tag.getCompoundOrEmpty("ext").copy();
		record.createdDay = tag.getLongOr("created_day", 0L);
		record.stats = tag.getCompoundOrEmpty("stats").copy();
		return record;
	}
}
