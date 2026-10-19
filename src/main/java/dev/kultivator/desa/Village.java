package dev.kultivator.desa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One vassal village record of {@code kultivator:vassals} (DESIGN 25.14): the plaque and bell, name, loyalty, counted
 * villagers, the lazy tribute day and its {@code owed} buffer, the open help request, the raid schedule and the Pelindung
 * Desa. Days are overworld day indices ({@code floor(clock / 24000)}). Mutable; the owner of the record calls
 * {@link Vassals#changed()} after a change.
 */
public final class Village {

	/** Request types (DESIGN 25.6.6), also the text key suffixes {@code kultivator.desa.request.<id>}. */
	public static final String REQ_BEAST = "binatang";
	public static final String REQ_CAMP = "sarang";
	public static final String REQ_PLAGUE = "wabah";
	public static final String REQ_HARVEST = "panen";
	public static final String REQ_WATCH = "jaga";
	public static final String[] REQUESTS = {REQ_BEAST, REQ_CAMP, REQ_PLAGUE, REQ_HARVEST, REQ_WATCH};
	/** Raid results kept in {@link #lastRaidResult}. */
	public static final int RAID_NONE = 0;
	public static final int RAID_REPELLED = 1;
	public static final int RAID_SACKED = 2;

	public int id;
	public BlockPos plaque = BlockPos.ZERO;
	public BlockPos bell = BlockPos.ZERO;
	public String name = "";
	public int loyalty;
	public int villagers;
	public boolean pledged;
	public boolean lost;
	/** The day of the last Malam Penjagaan attempt (-1 never). */
	public long trustTried = -1L;
	public long nextTribute;
	/** Encoded stacks waiting for room in the Lumbung (at most {@link Tribute#OWED_MAX}). */
	public ListTag owed = new ListTag();
	/** The next tribute is halved (after a sack). */
	public boolean halfTribute;
	public String reqType = "";
	public BlockPos reqPos = BlockPos.ZERO;
	public long reqExpires;
	public int reqProgress;
	public long nextRequest;
	public long nextRaid;
	public long lastRaid = -1L;
	public int lastRaidResult = RAID_NONE;
	public @Nullable UUID guard;
	/** The departed guard's tag while it is away from the plaque (empty while its body stands at the plaque). */
	public CompoundTag guardTag = new CompoundTag();
	public int guardRealm;
	public String guardName = "";
	public long lastDaily = -1L;
	public long giftDay = -1L;
	public int giftToday;
	public long talentDay = -9999L;

	/** True while a help request is open. */
	public boolean hasRequest() {
		return !reqType.isEmpty();
	}

	/** True while the guard's body stands at the plaque (not stored away). */
	public boolean guardPresent() {
		return guard != null && guardTag.isEmpty();
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("id", id);
		tag.putLong("plaque", plaque.asLong());
		tag.putLong("bell", bell.asLong());
		tag.putString("name", name);
		tag.putInt("loyalty", loyalty);
		tag.putInt("villagers", villagers);
		tag.putBoolean("pledged", pledged);
		tag.putBoolean("lost", lost);
		tag.putLong("trust_tried", trustTried);
		tag.putLong("next_tribute", nextTribute);
		tag.put("owed", owed.copy());
		tag.putBoolean("sacked", halfTribute);
		if (hasRequest()) {
			CompoundTag request = new CompoundTag();
			request.putString("type", reqType);
			request.putLong("pos", reqPos.asLong());
			request.putLong("expires", reqExpires);
			request.putInt("progress", reqProgress);
			tag.put("request", request);
		}
		tag.putLong("next_request", nextRequest);
		tag.putLong("next_raid", nextRaid);
		tag.putLong("last_raid", lastRaid);
		tag.putInt("last_raid_result", lastRaidResult);
		if (guard != null) {
			tag.store("guard", UUIDUtil.CODEC, guard);
			tag.put("guard_tag", guardTag.copy());
			tag.putInt("guard_realm", guardRealm);
			tag.putString("guard_name", guardName);
		}
		tag.putLong("last_daily", lastDaily);
		tag.putLong("gift_day", giftDay);
		tag.putInt("gift_today", giftToday);
		tag.putLong("talent_day", talentDay);
		return tag;
	}

	public static Village load(CompoundTag tag) {
		Village v = new Village();
		v.id = tag.getIntOr("id", 0);
		v.plaque = BlockPos.of(tag.getLongOr("plaque", 0L));
		v.bell = BlockPos.of(tag.getLongOr("bell", 0L));
		v.name = tag.getStringOr("name", "");
		v.loyalty = Math.max(0, Math.min(100, tag.getIntOr("loyalty", 0)));
		v.villagers = Math.max(0, tag.getIntOr("villagers", 0));
		v.pledged = tag.getBooleanOr("pledged", false);
		v.lost = tag.getBooleanOr("lost", false);
		v.trustTried = tag.getLongOr("trust_tried", -1L);
		v.nextTribute = tag.getLongOr("next_tribute", 0L);
		v.owed = tag.getListOrEmpty("owed").copy();
		v.halfTribute = tag.getBooleanOr("sacked", false);
		CompoundTag request = tag.getCompoundOrEmpty("request");
		v.reqType = request.getStringOr("type", "");
		v.reqPos = BlockPos.of(request.getLongOr("pos", 0L));
		v.reqExpires = request.getLongOr("expires", 0L);
		v.reqProgress = request.getIntOr("progress", 0);
		v.nextRequest = tag.getLongOr("next_request", 0L);
		v.nextRaid = tag.getLongOr("next_raid", 0L);
		v.lastRaid = tag.getLongOr("last_raid", -1L);
		v.lastRaidResult = tag.getIntOr("last_raid_result", RAID_NONE);
		v.guard = tag.read("guard", UUIDUtil.CODEC).orElse(null);
		v.guardTag = tag.getCompoundOrEmpty("guard_tag").copy();
		v.guardRealm = tag.getIntOr("guard_realm", 0);
		v.guardName = tag.getStringOr("guard_name", "");
		v.lastDaily = tag.getLongOr("last_daily", -1L);
		v.giftDay = tag.getLongOr("gift_day", -1L);
		v.giftToday = tag.getIntOr("gift_today", 0);
		v.talentDay = tag.getLongOr("talent_day", -9999L);
		return v;
	}
}
