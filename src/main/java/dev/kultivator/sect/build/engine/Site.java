package dev.kultivator.sect.build.engine;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Emission;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.Templates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One construction site (DESIGN 21.20 {@code build_site}): what the engine saves (template, rotation, glaze, seed,
 * plaza level, the frozen snapshot and zone levels, state, tier, done and skipped components, the priority, the open
 * component's cursor, reservation, pending refund, deferred and blocked ops, the parked components with their unspent
 * reservations, anchors, the bank, the component bills and stats) plus the live state of the tick (the compiled plan, the
 * open emission, the pending async emission, the rate accumulator and the status). Real sites live in the owner's
 * {@code HallData} section; showcases and relocation orphans in {@link Showcase}'s data.
 */
final class Site {

	static final int VERSION = 2;
	/** The open emission: a component (from its first op or its saved cursor), a retry of its parked cells, a repair list. */
	static final int MODE_FULL = 0;
	static final int MODE_RETRY = 1;
	static final int MODE_REPAIR = 2;

	/**
	 * A component set aside (DESIGN 21.10, 21.15): waiting for blocked cells, or suspended at an unloaded chunk. It keeps
	 * where it stopped ({@code cursor}, in component op indices), the op indices it could not place before that
	 * ({@code ops}), the unused digging credit ({@code absorb}) and the unspent part of its reservation ({@code held} =
	 * cost of {@code ops} and of every op from {@code cursor}, minus {@code absorb}). Nothing it already placed is ever
	 * replayed, so nothing is refunded twice.
	 */
	static final class Parked {
		int cursor;
		int[] ops = new int[0];
		long[] absorb = new long[Bahan.COUNT];
		Bill held = Bill.EMPTY;

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("cursor", cursor);
			tag.putIntArray("ops", ops.clone());
			tag.putLongArray("absorb", absorb.clone());
			tag.put("held", held.toTag());
			return tag;
		}

		static Parked load(CompoundTag tag) {
			Parked parked = new Parked();
			parked.cursor = tag.getIntOr("cursor", 0);
			parked.ops = tag.getIntArray("ops").orElse(new int[0]);
			long[] absorb = tag.getLongArray("absorb").orElse(new long[0]);
			System.arraycopy(absorb, 0, parked.absorb, 0, Math.min(absorb.length, Bahan.COUNT));
			parked.held = Bill.readFrom(tag.getCompoundOrEmpty("held"));
			return parked;
		}
	}

	/** The site key: the owner, a showcase id, or a relocation orphan's own id. */
	UUID owner;
	/** A relocation orphan credits its refunds to the player who moved away (DESIGN 21.18); null otherwise. */
	@Nullable UUID refundTo;
	ResourceKey<Level> dim;
	BlockPos stele;
	int template;
	int rotation;
	Glaze glaze = Glaze.TINTA;
	long seed;
	int plazaY;
	Snapshot snapshot;
	Map<String, Integer> levels = new LinkedHashMap<>();
	int planVersion;
	int state = SiteView.STATE_ACTIVE;
	int tier;
	boolean test;
	boolean showcase;
	/** The test build target tier (test sites; -1 = normal funded building). */
	int testTarget = -1;
	/** 0 normal, 1 instan, 2 cepat (test builds). */
	int speedMode;
	boolean deconstructTotal;
	final Set<String> done = new LinkedHashSet<>();
	final Set<String> skipped = new LinkedHashSet<>();
	@Nullable String prio;
	// The open component.
	@Nullable String openId;
	int openMode = MODE_FULL;
	int cursor;
	int openPhase;
	int openLayer;
	Bill reserve = Bill.EMPTY;
	final long[] absorb = new long[Bahan.COUNT];
	int placed;
	int total;
	long openSince;
	/** A retry's op index of each op of the open (filtered) emission in its component, else null. */
	int @Nullable [] origIdx;
	/** Op indices (of the open emission) that were blocked, and those deferred for an entity in the way. */
	final List<Integer> blockedOps = new ArrayList<>();
	final List<Integer> deferred = new ArrayList<>();
	/** Skips not yet given back (released when the component closes or parks). */
	Bill refund = Bill.EMPTY;
	/** A repair list cannot be re-emitted after a restart: its unspent bill is released instead. */
	Bill openRest = Bill.EMPTY;
	/** Components waiting because protected or player-made cells block them: id -> sample positions (world). */
	final Map<String, List<Long>> waiting = new LinkedHashMap<>();
	final Set<String> suspended = new LinkedHashSet<>();
	/** Where each waiting or suspended component stopped, with its unspent reservation. */
	final Map<String, Parked> parked = new LinkedHashMap<>();
	final Map<String, BlockPos> anchors = new LinkedHashMap<>();
	long bank;
	boolean removePlayerBlocks;
	boolean emptyContainers;
	/** The two permissions were switched on by an op (they stay valid on LAN, DESIGN 21.19). */
	boolean authOp;
	/** Test reset or showcase removal in progress: the undo log is put back in budgeted slices, then the site goes. */
	boolean restoring;
	long statPlaced;
	long statRemoved;
	long statCredited;

	// ---- live, not saved
	@Nullable SitePlan plan;
	@Nullable Emission emission;
	@Nullable CompletableFuture<Emission> pending;
	@Nullable String pendingId;
	double acc;
	double rateMultiplier = 1.0D;
	double rate;
	int status = SiteView.STATUS_PREPARING;
	int blockedNow;
	long deferUntil;
	/** World positions of the blocked cells of the last waiting component (pings and the UI sample). */
	final List<Long> blockedCells = new ArrayList<>();
	long nextFundTry;
	long nextRecheck;
	long lastPing;
	int pingsLeft;
	/** Extra block changes of the current op (fluid seals, the other half of a door), counted against the burst budget. */
	int extraOps;
	/** TIDY of the finished open component, done in budgeted bursts before it counts as DONE. */
	long @Nullable [] tidyQueue;
	int tidyCursor;
	/** Legacy cells (an upgrade's old sect) to take down inside the next component's columns before it builds. */
	long @Nullable [] legacyQueue;
	int legacyCursor;
	/** Periksa &amp; Perbaiki: the planned cells of the finished components (emitted off the server thread), checked in slices. */
	@Nullable CompletableFuture<RepairScan> repairScan;
	@Nullable RepairScan repairList;
	long lastSave;
	boolean dirty;
	BlockPos workSpot;
	long workSpotUntil;
	boolean ceremonyPending;
	int tiersBuiltThisTest;
	/** A component emitted and waiting for its bill to be reserved. */
	@Nullable Emission fundEmission;
	@Nullable String fundId;
	Bill fundBill = Bill.EMPTY;
	long[] fundDig = new long[Bahan.COUNT];
	boolean fundTold;
	/** Dismantling (deconstruction or a lower test tier): components above this tier are taken down in reverse. */
	boolean dismantling;
	int dismantleTarget;
	/** The pending emission resumes a parked (waiting or suspended) component. */
	boolean reopened;
	/** {@code debug ... komponen selesai}: this open component runs at the "instan" pace (with every normal check). */
	@Nullable String fastId;
	/** Cached per-component bills and op counts (a background survey; saved, so a restart does not re-survey). */
	final Map<String, Bill> bills = new HashMap<>();
	final Map<String, Integer> opCounts = new HashMap<>();
	@Nullable CompletableFuture<Survey> survey;
	int deferTries;
	long lastMoveAway;

	/** The bills and op counts of every component, computed off the server thread (emissions are dropped at once). */
	record Survey(Map<String, Bill> bills, Map<String, Integer> ops) {
	}

	/**
	 * Periksa &amp; Perbaiki (DESIGN 21.18): the final planned non-air cell of every finished component (later components
	 * win), emitted off the server thread; the server thread compares them with the world in slices
	 * ({@code cursor}) and collects the missing ones.
	 */
	static final class RepairScan {
		final long[] pos;
		final BlockState[] states;
		final byte[] phases;
		int cursor;
		final List<Integer> missing = new ArrayList<>();

		RepairScan(long[] pos, BlockState[] states, byte[] phases) {
			this.pos = pos;
			this.states = states;
			this.phases = phases;
		}
	}

	Site(UUID owner) {
		this.owner = owner;
	}

	/** Who the ledger credits and charges: the owner, or the player a relocation orphan belongs to. */
	UUID ledgerOwner() {
		return refundTo != null ? refundTo : owner;
	}

	/** True when this site's refunds and credits are real (not a test build or a showcase). */
	boolean paid() {
		return !test && !showcase;
	}

	/**
	 * "Bongkar &amp; simpan" / "Kosongkan" as valid right now (DESIGN 21.19): singleplayer without LAN, a dedicated server
	 * (only an op can switch them on there), or switched on by an op. A flag set before the world was opened to LAN stops.
	 */
	boolean mayRemovePlayerBlocks(net.minecraft.server.MinecraftServer server) {
		return removePlayerBlocks && authorizedNow(server);
	}

	boolean mayEmptyContainers(net.minecraft.server.MinecraftServer server) {
		return emptyContainers && authorizedNow(server);
	}

	private boolean authorizedNow(net.minecraft.server.MinecraftServer server) {
		return authOp || !server.isSingleplayer() || !server.isPublished();
	}

	TemplateSpec spec() {
		return Templates.get(template);
	}

	/** The compiled plan (built lazily from the saved snapshot and levels). */
	SitePlan plan() {
		if (plan == null) {
			plan = new SitePlan(spec(), rotation, glaze, seed, stele, snapshot.local(stele, rotation), levels);
		}
		return plan;
	}

	BoundingBox footprint() {
		return plan().footprint();
	}

	/** Done or skipped: settled for the queue only; the completed tier counts DONE components alone (Scheduler). */
	boolean finished(String componentId) {
		return done.contains(componentId) || skipped.contains(componentId);
	}

	SiteView view() {
		return new SiteView(owner, dim, stele, template, rotation, glaze, plazaY, footprint(), state, status, tier, blockedNow, openId, rate, bank,
				test || showcase);
	}

	// ================================================================== persistence

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("v", VERSION);
		tag.putInt("plan_v", planVersion);
		tag.putString("dim", dim.identifier().toString());
		tag.putIntArray("stele", new int[]{stele.getX(), stele.getY(), stele.getZ()});
		tag.putInt("template", template);
		tag.putInt("rot", rotation);
		tag.putString("glaze", glaze.id());
		tag.putLong("seed", seed);
		tag.putInt("plaza_y", plazaY);
		tag.put("snap", snapshot.toTag());
		CompoundTag levelTag = new CompoundTag();
		levels.forEach(levelTag::putInt);
		tag.put("levels", levelTag);
		tag.putInt("state", state);
		tag.putInt("tier", tier);
		tag.putBoolean("test", test);
		tag.putBoolean("showcase", showcase);
		tag.putInt("test_target", testTarget);
		tag.putInt("speed", speedMode);
		tag.putBoolean("total", deconstructTotal);
		tag.putBoolean("dismantling", dismantling);
		tag.putInt("dismantle_to", dismantleTarget);
		tag.put("done", strings(done));
		tag.put("skipped", strings(skipped));
		if (prio != null) {
			tag.putString("prio", prio);
		}
		if (openId != null) {
			CompoundTag open = new CompoundTag();
			open.putString("id", openId);
			open.putInt("mode", openMode);
			open.putInt("phase", openPhase);
			open.putInt("layer", openLayer);
			open.putInt("cursor", cursor);
			open.putInt("placed", placed);
			open.putInt("total", total);
			open.putLong("since", openSince);
			reserve.writeTo(open);
			open.putLongArray("absorb", absorb.clone());
			open.put("refund", refund.toTag());
			open.putIntArray("deferred", ints(deferred));
			open.putIntArray("blocked", ints(blockedOps));
			if (origIdx != null) {
				open.putIntArray("orig", origIdx.clone());
			}
			open.put("rest", openRest.toTag());
			tag.put("open", open);
		}
		CompoundTag wait = new CompoundTag();
		waiting.forEach((id, sample) -> {
			CompoundTag each = new CompoundTag();
			each.putLongArray("sample", sample.stream().mapToLong(Long::longValue).toArray());
			wait.put(id, each);
		});
		tag.put("wait", wait);
		CompoundTag parkedTag = new CompoundTag();
		parked.forEach((id, each) -> parkedTag.put(id, each.save()));
		tag.put("parked", parkedTag);
		tag.put("suspended", strings(suspended));
		CompoundTag anchorTag = new CompoundTag();
		anchors.forEach((id, pos) -> anchorTag.putIntArray(id, new int[]{pos.getX(), pos.getY(), pos.getZ()}));
		tag.put("anchors", anchorTag);
		tag.putLong("bank", bank);
		tag.putBoolean("remove_player", removePlayerBlocks);
		tag.putBoolean("empty_containers", emptyContainers);
		tag.putBoolean("auth_op", authOp);
		tag.putBoolean("restoring", restoring);
		if (refundTo != null) {
			tag.putString("refund_to", refundTo.toString());
		}
		if (!bills.isEmpty()) {
			CompoundTag billTag = new CompoundTag();
			bills.forEach((id, bill) -> {
				CompoundTag each = bill.toTag();
				each.putInt("ops", opCounts.getOrDefault(id, 0));
				billTag.put(id, each);
			});
			tag.put("bills", billTag);
		}
		CompoundTag stats = new CompoundTag();
		stats.putLong("placed", statPlaced);
		stats.putLong("removed", statRemoved);
		stats.putLong("credited", statCredited);
		tag.put("stats", stats);
		return tag;
	}

	static @Nullable Site load(UUID owner, CompoundTag tag) {
		int template = tag.getIntOr("template", 0);
		int[] stele = tag.getIntArray("stele").orElse(new int[0]);
		Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
		if (template <= 0 || stele.length != 3 || dim == null) {
			return null;
		}
		Site site = new Site(owner);
		site.planVersion = tag.getIntOr("plan_v", 1);
		site.dim = ResourceKey.create(Registries.DIMENSION, dim);
		site.stele = new BlockPos(stele[0], stele[1], stele[2]);
		site.template = template;
		site.rotation = tag.getIntOr("rot", 0);
		Glaze glaze = Glaze.byId(tag.getStringOr("glaze", "tinta"));
		site.glaze = glaze == null ? Glaze.TINTA : glaze;
		site.seed = tag.getLongOr("seed", 0L);
		site.plazaY = tag.getIntOr("plaza_y", site.stele.getY() - 1);
		site.snapshot = Snapshot.fromTag(tag.getCompoundOrEmpty("snap"));
		CompoundTag levelTag = tag.getCompoundOrEmpty("levels");
		for (String key : levelTag.keySet()) {
			site.levels.put(key, levelTag.getIntOr(key, 0));
		}
		site.state = tag.getIntOr("state", SiteView.STATE_ACTIVE);
		site.tier = tag.getIntOr("tier", 0);
		site.test = tag.getBooleanOr("test", false);
		site.showcase = tag.getBooleanOr("showcase", false);
		site.testTarget = tag.getIntOr("test_target", -1);
		site.speedMode = tag.getIntOr("speed", 0);
		site.deconstructTotal = tag.getBooleanOr("total", false);
		site.dismantling = tag.getBooleanOr("dismantling", false);
		site.dismantleTarget = tag.getIntOr("dismantle_to", 0);
		readStrings(tag.getListOrEmpty("done"), site.done);
		readStrings(tag.getListOrEmpty("skipped"), site.skipped);
		String prio = tag.getStringOr("prio", "");
		site.prio = prio.isEmpty() ? null : prio;
		CompoundTag open = tag.getCompoundOrEmpty("open");
		if (!open.getStringOr("id", "").isEmpty()) {
			site.openId = open.getStringOr("id", "");
			site.openPhase = open.getIntOr("phase", 0);
			site.openLayer = open.getIntOr("layer", Integer.MIN_VALUE);
			site.cursor = open.getIntOr("cursor", 0);
			site.placed = open.getIntOr("placed", 0);
			site.total = open.getIntOr("total", 0);
			site.openSince = open.getLongOr("since", 0L);
			site.reserve = Bill.readFrom(open);
			long[] absorb = open.getLongArray("absorb").orElse(new long[0]);
			System.arraycopy(absorb, 0, site.absorb, 0, Math.min(absorb.length, Bahan.COUNT));
			site.openMode = open.getIntOr("mode", MODE_FULL);
			site.refund = Bill.readFrom(open.getCompoundOrEmpty("refund"));
			for (int each : open.getIntArray("deferred").orElse(new int[0])) {
				site.deferred.add(each);
			}
			for (int each : open.getIntArray("blocked").orElse(new int[0])) {
				site.blockedOps.add(each);
			}
			int[] orig = open.getIntArray("orig").orElse(null);
			site.origIdx = orig == null || orig.length == 0 ? null : orig;
			site.openRest = Bill.readFrom(open.getCompoundOrEmpty("rest"));
		}
		CompoundTag wait = tag.getCompoundOrEmpty("wait");
		for (String id : wait.keySet()) {
			long[] sample = wait.getCompoundOrEmpty(id).getLongArray("sample").orElse(new long[0]);
			List<Long> list = new ArrayList<>();
			for (long each : sample) {
				list.add(each);
			}
			site.waiting.put(id, list);
		}
		CompoundTag parkedTag = tag.getCompoundOrEmpty("parked");
		for (String id : parkedTag.keySet()) {
			site.parked.put(id, Parked.load(parkedTag.getCompoundOrEmpty(id)));
		}
		readStrings(tag.getListOrEmpty("suspended"), site.suspended);
		CompoundTag billTag = tag.getCompoundOrEmpty("bills");
		for (String id : billTag.keySet()) {
			CompoundTag each = billTag.getCompoundOrEmpty(id);
			site.bills.put(id, Bill.readFrom(each));
			site.opCounts.put(id, each.getIntOr("ops", 0));
		}
		site.authOp = tag.getBooleanOr("auth_op", false);
		site.restoring = tag.getBooleanOr("restoring", false);
		String refundTo = tag.getStringOr("refund_to", "");
		if (!refundTo.isEmpty()) {
			try {
				site.refundTo = UUID.fromString(refundTo);
			} catch (IllegalArgumentException ignored) {
				// A broken id: refunds go to the site key.
			}
		}
		CompoundTag anchorTag = tag.getCompoundOrEmpty("anchors");
		for (String id : anchorTag.keySet()) {
			int[] pos = anchorTag.getIntArray(id).orElse(new int[0]);
			if (pos.length == 3) {
				site.anchors.put(id, new BlockPos(pos[0], pos[1], pos[2]));
			}
		}
		site.bank = tag.getLongOr("bank", 0L);
		site.removePlayerBlocks = tag.getBooleanOr("remove_player", false);
		site.emptyContainers = tag.getBooleanOr("empty_containers", false);
		CompoundTag stats = tag.getCompoundOrEmpty("stats");
		site.statPlaced = stats.getLongOr("placed", 0L);
		site.statRemoved = stats.getLongOr("removed", 0L);
		site.statCredited = stats.getLongOr("credited", 0L);
		return site;
	}

	private static int[] ints(List<Integer> values) {
		int[] array = new int[values.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = values.get(i);
		}
		return array;
	}

	private static ListTag strings(Set<String> values) {
		ListTag list = new ListTag();
		for (String value : values) {
			list.add(StringTag.valueOf(value));
		}
		return list;
	}

	private static void readStrings(ListTag list, Set<String> into) {
		for (Tag each : list) {
			each.asString().ifPresent(into::add);
		}
	}
}
