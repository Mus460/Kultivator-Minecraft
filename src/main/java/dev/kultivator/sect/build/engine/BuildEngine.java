package dev.kultivator.sect.build.engine;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.WorldBreaker;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.build.api.AuditReport;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.BuildFxIds;
import dev.kultivator.sect.build.api.BuildListener;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.MaterialLedger;
import dev.kultivator.sect.build.api.PlanStats;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Costs;
import dev.kultivator.sect.build.blueprint.Emission;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.Templates;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.HallItems;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The construction engine (DESIGN 21.9-21.15, module P6b-cetak), registered as the {@link EngineApi}: previews and
 * confirmation, the sites (saved in each owner's {@code HallData} section {@code build_site}; showcases and relocation
 * orphans in {@link Showcase}), the scheduler, the budgeted placement through {@link BuildPlacer}, refunds,
 * deconstruction, test builds, showcases, the undo log, dry runs and the audit.
 * <p>
 * <b>Pacing (DESIGN 21.15).</b> {@code rate = (6 + 3 × (S − 1)) × sectBuildSpeed × flow factor × (bank > 0 ? 2 : 1) ×
 * msptFactor} blocks per second, msptFactor 1 below 30 ms, 0.5 up to {@code sectBuildMsptPause}, then 0 (pause). Every
 * {@link #BURST} ticks one global budget of {@code 4 × sectBuildMaxPerTick} ops is shared round-robin by the running sites;
 * a skip costs 0.1 op, every extra block change (a fluid seal, a door's other half, a TIDY shape update, an old legacy
 * block taken down) costs a whole op, and at most {@link #MAX_CHECKS} cells are looked at per burst. It runs only while the
 * hall is active, the stele stands (loaded) on its anchor cell, the owner (or a sect mate) is within
 * {@code sectBuildRange}, no Kehancuran job runs and the chunk is loaded; while a funded component waits only for the
 * owner, banked ticks grow (up to {@code sectBuildBankTicks}) and later double the rate. Emission (also the repair
 * inspection and the survey) runs off the server thread from immutable data; the world is only touched on the server
 * thread.
 * <p>
 * <b>Reservations.</b> A component opens only with its whole bill reserved (minus the predicted digging credit, which
 * real digging pays back first). Every op is settled once: placed (spent), skipped (its share given back), or parked
 * (blocked, deferred, or after an unloaded chunk). A parked component keeps exactly its unspent share and resumes where it
 * stopped; nothing already placed is ever replayed, so nothing is refunded twice, and every way out (skip, finish without
 * the blocked cells, deconstruct, upgrade, release, relocation) gives the unspent share back.
 */
public final class BuildEngine implements EngineApi {

	public static final BuildEngine INSTANCE = new BuildEngine();

	/** The HallData section of a site. */
	public static final String SECTION = "build_site";
	/** Placement bursts every 4 ticks (fewer section rebuilds and light packets). */
	public static final int BURST = 4;
	/** Cells looked at per burst at most. */
	public static final int MAX_CHECKS = 1024;
	/** {@code uji sekte tingkat ... instan}: ops per tick, paused above this MSPT. */
	public static final int INSTANT_OPS = 512;
	public static final int INSTANT_MSPT = 50;
	/** {@code cepat}: rate multiplier. */
	public static final int FAST_FACTOR = 20;
	/** A skip, a block check without a change. */
	public static final double SKIP_COST = 0.1D;
	/** Waiting (blocked) components are re-tried this often (30 s), suspended ones every 10 s. */
	public static final int RECHECK_TICKS = 600;
	public static final int SUSPEND_TICKS = 200;
	/** A deferred op (an entity in the way) is retried after 40 ticks; after 10 tries nearby players are asked to move. */
	public static final int DEFER_TICKS = 40;
	public static final int DEFER_TRIES = 10;
	/** After this many tries every op still deferred counts as blocked (the component waits with the usual buttons). */
	public static final int DEFER_GIVE_UP = 30;
	/** Deferred ops retried per call at most. */
	public static final int DEFER_RETRY_MAX = 256;
	/** Builders' work spot changes every 20 s. */
	public static final int WORK_SPOT_TICKS = 400;
	/** The blocked ping: every 5 s for 30 s. */
	public static final int PING_TICKS = 100;
	public static final int PINGS = 6;
	/** Sites are written back at most this often while building (10 s), and always before a world save. */
	public static final int SAVE_TICKS = 200;
	/** At most this many cells are remembered per blocked component. */
	public static final int BLOCKED_SAMPLE = 32;
	/** Test reset and showcase removal: undo-logged cells put back per tick at most (paused above {@link #INSTANT_MSPT}). */
	public static final int RESTORE_OPS = 512;
	/** A restore pass that left cells (unloaded, someone in the way) tries again after this long. */
	public static final int RESTORE_RETRY_TICKS = 100;
	/** Periksa &amp; Perbaiki: planned cells compared with the world per tick at most. */
	public static final int REPAIR_CHECKS = 1024;
	/** An upgrade's old (legacy) cells checked per burst at most before a new component builds. */
	public static final int LEGACY_CHECKS = 4096;

	private @Nullable MinecraftServer server;
	private final Map<UUID, Site> sites = new LinkedHashMap<>();
	private final Map<UUID, Site> showcases = new LinkedHashMap<>();
	private final Map<UUID, Site> orphans = new LinkedHashMap<>();
	private final Map<UUID, UUID> showcaseOwners = new HashMap<>();
	private final Previews previews = new Previews();
	private final Map<Long, Boolean> autoConfirm = new HashMap<>();
	private final Map<Long, Integer> autoTier = new HashMap<>();
	/** The two permissions per owner (1 remove player blocks, 2 empty containers, 4 switched on by an op), also before founding. */
	private final Map<UUID, Integer> auth = new HashMap<>();
	/** Restore order per restoring site (reverse first-change order of its undo log). */
	private final Map<UUID, long[]> restoreOrder = new HashMap<>();
	private final Map<UUID, Integer> restoreCursor = new HashMap<>();
	private final Map<UUID, Long> restoreRetry = new HashMap<>();
	/** finishWithoutBlocked: the anchors of a finished component arrive from an async emission. */
	private final List<AnchorJob> anchorJobs = new ArrayList<>();
	private long ticks;
	private int roundRobin;

	private record AnchorJob(Site site, CompletableFuture<Emission> emission) {
	}

	/** Called once from {@code Kultivator.onInitialize}. */
	public static void init() {
		EngineApi.register(INSTANCE);
		ServerLifecycleEvents.SERVER_STARTED.register(INSTANCE::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(INSTANCE::saveAll);
		ServerLifecycleEvents.BEFORE_SAVE.register((saving, flush, force) -> INSTANCE.saveAll(saving));
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> INSTANCE.stop());
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::tick);
		BreakRefund.init();
	}

	private BuildEngine() {
	}

	// ================================================================== lifecycle

	private void start(MinecraftServer started) {
		this.server = started;
		sites.clear();
		showcases.clear();
		orphans.clear();
		HallData data = HallData.get(started);
		for (UUID owner : data.owners()) {
			CompoundTag section = Halls.section(owner, SECTION);
			if (section.getIntOr("template", 0) <= 0) {
				continue;
			}
			try {
				Site site = Site.load(owner, section);
				if (site != null) {
					sites.put(owner, site);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Skipping a broken sect construction site of {}", owner, exception);
			}
		}
		Showcase showcase = Showcase.get(started);
		for (Map.Entry<UUID, CompoundTag> entry : showcase.sites.entrySet()) {
			Site site = Site.load(entry.getKey(), entry.getValue());
			if (site != null) {
				site.showcase = true;
				showcases.put(entry.getKey(), site);
				String by = entry.getValue().getStringOr("by", "");
				try {
					showcaseOwners.put(entry.getKey(), UUID.fromString(by));
				} catch (IllegalArgumentException ignored) {
					// No placer recorded.
				}
			}
		}
		for (Map.Entry<UUID, CompoundTag> entry : showcase.orphans.entrySet()) {
			try {
				Site site = Site.load(entry.getKey(), entry.getValue());
				if (site != null) {
					orphans.put(entry.getKey(), site);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Skipping a broken relocated sect site {}", entry.getKey(), exception);
			}
		}
	}

	private void stop() {
		sites.clear();
		showcases.clear();
		orphans.clear();
		showcaseOwners.clear();
		previews.clear();
		autoConfirm.clear();
		autoTier.clear();
		auth.clear();
		restoreOrder.clear();
		restoreCursor.clear();
		restoreRetry.clear();
		anchorJobs.clear();
		PlanAudit.clear();
		CellIndex.forget();
		server = null;
	}

	private void saveAll(MinecraftServer saving) {
		for (Site site : allSites()) {
			save(site);
		}
	}

	private List<Site> allSites() {
		List<Site> list = new ArrayList<>(sites.size() + showcases.size() + orphans.size());
		list.addAll(sites.values());
		list.addAll(showcases.values());
		list.addAll(orphans.values());
		return list;
	}

	/** The site (real, showcase or orphan) whose cells belong to {@code key}, or null. */
	@Nullable Site siteOf(@Nullable UUID key) {
		if (key == null) {
			return null;
		}
		Site site = sites.get(key);
		if (site != null) {
			return site;
		}
		site = showcases.get(key);
		return site != null ? site : orphans.get(key);
	}

	private boolean orphan(Site site) {
		return orphans.get(site.owner) == site;
	}

	private void save(Site site) {
		if (server == null) {
			return;
		}
		site.dirty = false;
		site.lastSave = ticks;
		if (site.openId != null && site.emission != null) {
			site.openRest = unspent(site);
		}
		if (site.showcase) {
			CompoundTag tag = site.save();
			UUID by = showcaseOwners.get(site.owner);
			if (by != null) {
				tag.putString("by", by.toString());
			}
			Showcase.get(server).put(site.owner, tag);
			return;
		}
		if (orphan(site)) {
			Showcase.get(server).putOrphan(site.owner, site.save());
			return;
		}
		if (sites.get(site.owner) != site) {
			return;
		}
		CompoundTag section = Halls.section(site.owner, SECTION);
		for (String key : new ArrayList<>(section.keySet())) {
			section.remove(key);
		}
		section.merge(site.save());
		Halls.setDirty();
	}

	private void clearSection(UUID owner) {
		CompoundTag section = Halls.section(owner, SECTION);
		for (String key : new ArrayList<>(section.keySet())) {
			section.remove(key);
		}
		Halls.setDirty();
	}

	/** Gives back a bill to the site's ledger owner (never for test builds and showcases). */
	private static void release(Site site, Bill bill) {
		if (site.paid() && bill != null && !bill.isEmpty()) {
			MaterialLedger.get().release(site.ledgerOwner(), bill);
		}
	}

	// ================================================================== tick

	private void tick(MinecraftServer current) {
		if (server == null) {
			return;
		}
		ticks++;
		previews.tick(current, this);
		handleAutoConfirm(current);
		KultivatorConfig config = KultivatorConfig.get();
		long mspt = current.getAverageTickTimeNanos() / 1_000_000L;
		List<Site> list = allSites();
		if (ticks % 20 == 0) {
			for (Site site : list) {
				try {
					refresh(current, site, config, mspt);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Sect construction site of {} failed to refresh", site.owner, exception);
				}
			}
			anchorJobs();
		}
		for (Site site : list) {
			if (site.restoring && mspt < INSTANT_MSPT) {
				restoreSlice(current, site);
			}
			if (site.repairList != null && mspt < config.sectBuildMsptPause) {
				repairSlice(current, site);
			}
		}
		if (!config.sectConstruction || list.isEmpty()) {
			return;
		}
		// Test builds "instan" and the debug "komponen selesai": up to 512 ops every tick, paused above 50 ms.
		for (Site site : list) {
			if (site.restoring || site.status != SiteView.STATUS_RUNNING || mspt >= INSTANT_MSPT || placementPaused(site.owner)) {
				continue;
			}
			if (site.speedMode == 1 || site.fastId != null && site.fastId.equals(site.openId)) {
				run(current, site, INSTANT_OPS);
			} else if (site.fastId != null) {
				site.fastId = null;
			}
		}
		if (ticks % BURST != 0) {
			return;
		}
		double factor = msptFactor(mspt, config);
		int global = BURST * config.sectBuildMaxPerTick;
		int n = list.size();
		for (int k = 0; k < n && global > 0; k++) {
			Site site = list.get(Math.floorMod(roundRobin + k, n));
			if (site.restoring || site.status != SiteView.STATUS_RUNNING || site.speedMode == 1 || site.fastId != null
					|| placementPaused(site.owner)) {
				continue;
			}
			site.rate = rate(site, config, factor);
			site.acc = Math.min(site.acc + site.rate * BURST / 20.0D, 64.0D * (site.speedMode == 2 ? FAST_FACTOR : 1));
			if (site.bank > 0L) {
				site.bank = Math.max(0L, site.bank - BURST);
			}
			int cap = site.speedMode == 2 ? global * FAST_FACTOR : global;
			int allowed = (int) Math.min(Math.floor(site.acc), cap);
			if (allowed <= 0) {
				continue;
			}
			int used = run(current, site, allowed);
			site.acc = Math.max(0.0D, site.acc - used);
			if (site.speedMode != 2) {
				global -= used;
			}
		}
		roundRobin++;
	}

	private static final java.util.List<java.util.function.Predicate<UUID>> PAUSE_PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Phase 8 (DESIGN 25.4.5): while any provider returns true for the owner (a live sect raid), the owner's construction
	 * places nothing; it resumes by itself afterwards. Repairs and restores are not paused.
	 */
	public static void addPauseProvider(java.util.function.Predicate<UUID> provider) {
		if (provider != null) {
			PAUSE_PROVIDERS.add(provider);
		}
	}

	/** True while a pause provider holds the owner's construction. */
	public static boolean placementPaused(UUID owner) {
		for (java.util.function.Predicate<UUID> provider : PAUSE_PROVIDERS) {
			try {
				if (provider.test(owner)) {
					return true;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A sect construction pause provider failed", exception);
			}
		}
		return false;
	}

	/** The base rate of a template in blocks per second (DESIGN 21.15 table). */
	public static double baseRate(int template) {
		return 6.0D + 3.0D * (Math.max(1, template) - 1);
	}

	private static double msptFactor(long mspt, KultivatorConfig config) {
		if (mspt < 30L) {
			return 1.0D;
		}
		return mspt < config.sectBuildMsptPause ? 0.5D : 0.0D;
	}

	private double rate(Site site, KultivatorConfig config, double factor) {
		double rate = baseRate(site.template) * config.sectBuildSpeed * site.rateMultiplier * factor;
		if (site.bank > 0L) {
			rate *= 2.0D;
		}
		if (site.speedMode == 2) {
			rate *= FAST_FACTOR;
		}
		return rate;
	}

	// ================================================================== the once-a-second refresh

	private void refresh(MinecraftServer current, Site site, KultivatorConfig config, long mspt) {
		ServerLevel level = current.getLevel(site.dim);
		long now = ticks;
		if (site.restoring) {
			site.status = SiteView.STATUS_PAUSED;
			return;
		}
		int status = computeStatus(current, site, level, config, mspt);
		site.status = status;
		boolean active = status == SiteView.STATUS_RUNNING || status == SiteView.STATUS_PREPARING || status == SiteView.STATUS_WAITING_FUNDS
				|| status == SiteView.STATUS_BLOCKED || status == SiteView.STATUS_UNLOADED;
		// Banked time: only while a funded, open component waits for the owner (never while waiting for materials).
		if (status == SiteView.STATUS_OWNER_AWAY && site.openId != null && site.openMode != Site.MODE_REPAIR) {
			site.bank = Math.min(config.sectBuildBankTicks, site.bank + 20L);
		}
		if (level == null) {
			return;
		}
		// Async results.
		if (site.survey == null && site.bills.isEmpty() && !site.spec().isPlaceholder()) {
			survey(site);
		}
		if (site.survey != null && site.survey.isDone()) {
			try {
				Site.Survey survey = site.survey.join();
				site.bills.putAll(survey.bills());
				site.opCounts.putAll(survey.ops());
				site.dirty = true;
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect site survey failed", exception);
			}
			site.survey = null;
		}
		if (site.repairScan != null && site.repairScan.isDone()) {
			try {
				site.repairList = site.repairScan.join();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect repair inspection failed", exception);
			}
			site.repairScan = null;
		}
		if (site.pending != null && site.pending.isDone()) {
			Emission emission;
			try {
				emission = site.pending.join();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect component {} failed to emit", site.pendingId, exception);
				emission = Emission.empty(site.pendingId == null ? "?" : site.pendingId);
			}
			String id = site.pendingId;
			site.pending = null;
			site.pendingId = null;
			received(level, site, id, emission);
		}
		if (!active) {
			return;
		}
		if (site.dismantling) {
			if (site.emission == null && site.pending == null) {
				nextDismantle(site);
			}
			return;
		}
		// Funding: the emitted next component waits for its whole bill.
		if (site.fundEmission != null && site.openId == null && now >= site.nextFundTry) {
			site.nextFundTry = now + 20L;
			tryFund(level, site);
		}
		boolean inspecting = site.repairScan != null || site.repairList != null;
		if (site.openId == null && site.pending == null && site.fundEmission == null && !inspecting) {
			openNext(site, now);
		} else if (site.openId != null && site.emission == null && site.pending == null) {
			resumeOpen(site);
		}
		// Retry deferred ops after their wait.
		if (site.emission != null && !site.deferred.isEmpty() && site.cursor >= site.emission.size() && now >= site.deferUntil) {
			site.status = SiteView.STATUS_RUNNING;
		}
		// Blocked pings.
		if (site.pingsLeft > 0 && now - site.lastPing >= PING_TICKS) {
			site.lastPing = now;
			site.pingsLeft--;
			for (int i = 0; i < Math.min(8, site.blockedCells.size()); i++) {
				BlockPos pos = BlockPos.of(site.blockedCells.get(i));
				Vec3 at = Vec3.atCenterOf(pos);
				Fx.send(level, at, at, FxType.SECT_BUILD, BuildFxIds.BLOCKED, site.blockedCells.size(), 5.0F);
			}
		}
		if (site.dirty && now - site.lastSave >= SAVE_TICKS) {
			save(site);
		}
	}

	/** A saved open component after a restart: re-emitted and resumed at its saved cursor (emission is deterministic). */
	private void resumeOpen(Site site) {
		Component component = site.openMode == Site.MODE_REPAIR ? null : site.spec().component(site.openId);
		if (component != null) {
			site.pendingId = site.openId;
			site.pending = site.plan().emitAsync(component);
			return;
		}
		// A repair list (never re-emitted) or a component the plan no longer has: its unspent part goes back.
		release(site, site.openRest);
		clearOpen(site);
	}

	private int computeStatus(MinecraftServer current, Site site, @Nullable ServerLevel level, KultivatorConfig config, long mspt) {
		if (level == null) {
			return SiteView.STATUS_STELE;
		}
		if (!config.sectConstruction || site.state == SiteView.STATE_PAUSED) {
			return SiteView.STATUS_PAUSED;
		}
		if (site.spec().isPlaceholder()) {
			return SiteView.STATUS_COMPLETE;
		}
		if (WorldBreaker.busy()) {
			return SiteView.STATUS_KEHANCURAN;
		}
		if (!site.showcase && !orphan(site) && !steleOk(site, level)) {
			return SiteView.STATUS_STELE;
		}
		if (!site.showcase && !config.sectBuildWhileAway && !present(current, site, level, config)) {
			return SiteView.STATUS_OWNER_AWAY;
		}
		if (mspt >= config.sectBuildMsptPause) {
			return SiteView.STATUS_SERVER_BUSY;
		}
		if (site.dismantling) {
			return SiteView.STATUS_RUNNING;
		}
		if (site.openId != null) {
			if (site.emission == null) {
				return SiteView.STATUS_PREPARING;
			}
			if (site.cursor >= site.emission.size() && !site.deferred.isEmpty() && ticks < site.deferUntil) {
				return SiteView.STATUS_PREPARING;
			}
			return SiteView.STATUS_RUNNING;
		}
		if (site.pending != null || site.repairScan != null || site.repairList != null) {
			return SiteView.STATUS_PREPARING;
		}
		if (site.fundEmission != null) {
			return SiteView.STATUS_WAITING_FUNDS;
		}
		if (Scheduler.complete(site) && site.waiting.isEmpty() && site.suspended.isEmpty()) {
			return SiteView.STATUS_COMPLETE;
		}
		if (site.testTarget >= 0 && site.tier >= site.testTarget && Scheduler.next(site) == null) {
			return SiteView.STATUS_COMPLETE;
		}
		if (Scheduler.next(site) == null) {
			if (!site.waiting.isEmpty()) {
				return SiteView.STATUS_BLOCKED;
			}
			if (!site.suspended.isEmpty()) {
				return SiteView.STATUS_UNLOADED;
			}
			return SiteView.STATUS_COMPLETE;
		}
		return SiteView.STATUS_PREPARING;
	}

	/**
	 * The hall is active and its stele stands on the site's anchor cell (DESIGN 21.15, 21.17). An unloaded stele chunk
	 * counts as "not standing": nothing builds without seeing it.
	 */
	private boolean steleOk(Site site, ServerLevel level) {
		Halls.HallView hall = Halls.hallOf(site.owner);
		if (hall == null || !hall.active() || !hall.pos().equals(site.stele) || !hall.dim().equals(site.dim)) {
			return false;
		}
		return level.isLoaded(site.stele) && level.getBlockState(site.stele).is(HallItems.SECT_HALL_STELE);
	}

	/** The site's player, or a sect mate, is online in the site's dimension within {@code sectBuildRange} of the stele. */
	private boolean present(MinecraftServer current, Site site, ServerLevel level, KultivatorConfig config) {
		double range = config.sectBuildRange;
		UUID owner = site.ledgerOwner();
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - site.stele.getX();
			double dz = player.getZ() - site.stele.getZ();
			if (dx * dx + dz * dz > range * range) {
				continue;
			}
			if (player.getUUID().equals(owner)) {
				return true;
			}
			if (site.test) {
				continue;
			}
			Sect sect = Sects.sectOf(player);
			if (sect != null && sect.isMember(owner)) {
				return true;
			}
		}
		return false;
	}

	/** Emits every component once in the background for the bills and op counts the UI shows (only those are kept). */
	private void survey(Site site) {
		SitePlan plan = site.plan();
		List<Component> components = new ArrayList<>(plan.spec().components());
		site.survey = CompletableFuture.supplyAsync(() -> {
			Map<String, Bill> bills = new HashMap<>();
			Map<String, Integer> ops = new HashMap<>();
			for (Component component : components) {
				Emission emission = plan.emit(component);
				bills.put(component.id(), Costs.bill(emission));
				ops.put(component.id(), emission.size());
			}
			return new Site.Survey(bills, ops);
		}, net.minecraft.util.Util.backgroundExecutor());
	}

	private void openNext(Site site, long now) {
		Component next = Scheduler.next(site);
		if (next == null) {
			if (!site.waiting.isEmpty() && now >= site.nextRecheck) {
				site.nextRecheck = now + RECHECK_TICKS;
				reopen(site, site.waiting.keySet().iterator().next());
			} else if (!site.suspended.isEmpty() && now >= site.nextRecheck) {
				site.nextRecheck = now + SUSPEND_TICKS;
				reopen(site, site.suspended.iterator().next());
			}
			return;
		}
		site.pendingId = next.id();
		site.pending = site.plan().emitAsync(next);
	}

	/** Reopens a parked (waiting or suspended) component: only its parked cells and the ops after its cursor run again. */
	private void reopen(Site site, String id) {
		Component component = site.spec().component(id);
		if (component == null) {
			Site.Parked parked = site.parked.remove(id);
			if (parked != null) {
				release(site, parked.held);
			}
			site.waiting.remove(id);
			site.suspended.remove(id);
			return;
		}
		site.reopened = true;
		site.pendingId = id;
		site.pending = site.plan().emitAsync(component);
	}

	/** An async emission arrived. */
	private void received(ServerLevel level, Site site, @Nullable String id, Emission emission) {
		if (id == null) {
			return;
		}
		if (site.dismantling) {
			site.emission = emission;
			site.cursor = emission.size() - 1;
			site.openId = id;
			site.deferred.clear();
			site.deferTries = 0;
			return;
		}
		if (id.equals(site.openId)) {
			// A resumed open component: continue at the saved cursor (the emission is deterministic).
			Emission run = site.openMode == Site.MODE_RETRY && site.origIdx != null ? Emission.subset(emission, valid(site.origIdx, emission.size()))
					: emission;
			if (site.openMode == Site.MODE_RETRY && site.origIdx != null) {
				site.origIdx = valid(site.origIdx, emission.size());
			}
			site.emission = run;
			site.total = run.size();
			site.cursor = Math.max(0, Math.min(site.cursor, run.size()));
			site.deferred.removeIf(i -> i < 0 || i >= run.size());
			site.blockedOps.removeIf(i -> i < 0 || i >= run.size());
			return;
		}
		if (site.reopened) {
			site.reopened = false;
			Site.Parked parked = site.parked.remove(id);
			site.waiting.remove(id);
			site.suspended.remove(id);
			resumeParked(level, site, id, emission, parked == null ? new Site.Parked() : parked);
			return;
		}
		Bill bill = Costs.bill(emission);
		long[] dig = new long[Bahan.COUNT];
		Previews.predictDig(emission, site.plan().ctx().terrain(), dig);
		// The digging credit absorbed by this component never exceeds what it costs in that category.
		for (Bahan cat : Bahan.values()) {
			dig[cat.ordinal()] = Math.min(dig[cat.ordinal()], bill.centi(cat));
		}
		site.fundEmission = emission;
		site.fundId = id;
		site.fundBill = bill.minus(new Bill(dig, Map.of()));
		site.fundDig = dig;
		site.fundTold = false;
		site.nextFundTry = 0L;
		tryFund(level, site);
	}

	private static int[] valid(int[] idx, int size) {
		int n = 0;
		for (int i : idx) {
			if (i >= 0 && i < size) {
				n++;
			}
		}
		if (n == idx.length) {
			return idx;
		}
		int[] out = new int[n];
		int k = 0;
		for (int i : idx) {
			if (i >= 0 && i < size) {
				out[k++] = i;
			}
		}
		return out;
	}

	/** A parked component resumes: its parked cells first, then every op from its cursor, with its unspent share. */
	private void resumeParked(ServerLevel level, Site site, String id, Emission emission, Site.Parked parked) {
		int size = emission.size();
		int cursor = Math.max(0, Math.min(parked.cursor, size));
		if (parked.ops.length == 0 && cursor == 0) {
			open(level, site, id, emission, parked.held, parked.absorb, Site.MODE_FULL, null, false);
			return;
		}
		List<Integer> order = new ArrayList<>();
		for (int i : parked.ops) {
			if (i >= 0 && i < cursor) {
				order.add(i);
			}
		}
		for (int i = cursor; i < size; i++) {
			order.add(i);
		}
		int[] idx = new int[order.size()];
		for (int i = 0; i < idx.length; i++) {
			idx[i] = order.get(i);
		}
		open(level, site, id, Emission.subset(emission, idx), parked.held, parked.absorb, Site.MODE_RETRY, idx, false);
	}

	private void tryFund(ServerLevel level, Site site) {
		if (site.fundEmission == null || site.fundId == null) {
			return;
		}
		boolean free = !site.paid();
		if (free || MaterialLedger.get().reserve(site.ledgerOwner(), site.fundBill)) {
			Emission emission = site.fundEmission;
			String id = site.fundId;
			Bill reserved = free ? Bill.EMPTY : site.fundBill;
			long[] dig = free ? new long[Bahan.COUNT] : site.fundDig;
			site.fundEmission = null;
			site.fundId = null;
			open(level, site, id, emission, reserved, dig, Site.MODE_FULL, null, true);
			return;
		}
		if (!site.fundTold) {
			site.fundTold = true;
			BuildListener.fire().onWaitingFunds(site.ledgerOwner(), site.fundBill);
		}
	}

	private void open(ServerLevel level, Site site, String id, Emission emission, Bill reserved, long[] absorb, int mode, int @Nullable [] origIdx,
			boolean fresh) {
		site.openId = id;
		site.openMode = mode;
		site.origIdx = origIdx;
		site.emission = emission;
		site.cursor = 0;
		site.placed = 0;
		site.total = emission.size();
		site.reserve = reserved;
		System.arraycopy(absorb, 0, site.absorb, 0, Bahan.COUNT);
		site.openSince = ticks;
		site.openPhase = 0;
		site.openLayer = Integer.MIN_VALUE;
		site.deferred.clear();
		site.blockedOps.clear();
		site.refund = Bill.EMPTY;
		site.openRest = Bill.EMPTY;
		site.deferTries = 0;
		site.tidyQueue = null;
		site.legacyQueue = null;
		site.dirty = true;
		if (mode != Site.MODE_REPAIR && server != null && CellIndex.get(server).hasLegacy(site.owner)) {
			site.legacyQueue = legacyColumns(site, emission);
			site.legacyCursor = 0;
		}
		Component component = site.spec().component(id);
		if (component != null && mode == Site.MODE_FULL && fresh && !instant(site)) {
			BoundingBox box = site.plan().worldBox(emission.bounds);
			sendBox(level, box, box.minY(), BuildFxIds.COMPONENT_OPEN, component.tier(), 0.0F);
			BuildListener.fire().onComponentStarted(site.ledgerOwner(), view(site, component, ComponentView.STATE_OPEN));
		}
		save(site);
	}

	/**
	 * The legacy work of a component on an upgraded site (DESIGN 21.18 "old sect cells in its volume are dismantled"):
	 * the world columns its ops touch and their height range, as {@code [top, bottom, column...]}; the old cells there come
	 * down top-down (roofs first, plinths last) with a full refund before the component builds.
	 */
	private static long @Nullable [] legacyColumns(Site site, Emission emission) {
		if (emission.size() == 0) {
			return null;
		}
		SitePlan plan = site.plan();
		LongLinkedOpenHashSet columns = new LongLinkedOpenHashSet();
		int top = Integer.MIN_VALUE;
		int bottom = Integer.MAX_VALUE;
		for (int i = 0; i < emission.size(); i++) {
			BlockPos pos = plan.world(emission.pos[i]);
			columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
			top = Math.max(top, pos.getY());
			bottom = Math.min(bottom, pos.getY());
		}
		long[] queue = new long[columns.size() + 2];
		queue[0] = top;
		queue[1] = bottom;
		int k = 2;
		for (long column : columns) {
			queue[k++] = column;
		}
		return queue;
	}

	private boolean instant(Site site) {
		return site.speedMode == 1;
	}

	/**
	 * What the open component still holds of its reservation: the cost of every op not reached yet and of every op it
	 * could not place (blocked, deferred), minus the digging credit it will not absorb any more (clamped at zero), plus
	 * the skips not given back yet.
	 */
	private static Bill unspent(Site site) {
		Emission emission = site.emission;
		if (emission == null) {
			return site.openRest;
		}
		Bill.Builder rest = Bill.builder();
		rest.add(Costs.bill(emission, site.cursor, emission.size()));
		for (int i : site.deferred) {
			if (i >= 0 && i < site.cursor && i < emission.size()) {
				Costs.add(rest, emission.states[i]);
			}
		}
		for (int i : site.blockedOps) {
			if (i >= 0 && i < site.cursor && i < emission.size()) {
				Costs.add(rest, emission.states[i]);
			}
		}
		return rest.build().minus(new Bill(site.absorb, Map.of())).plus(site.refund);
	}

	/** Closes the open component; {@code refundRest} gives back exactly its unspent part ({@link #unspent}). */
	private void closeOpen(Site site, boolean refundRest) {
		if (refundRest) {
			release(site, unspent(site));
		}
		clearOpen(site);
	}

	private static void clearOpen(Site site) {
		site.openId = null;
		site.openMode = Site.MODE_FULL;
		site.origIdx = null;
		site.emission = null;
		site.cursor = 0;
		site.reserve = Bill.EMPTY;
		site.refund = Bill.EMPTY;
		site.openRest = Bill.EMPTY;
		Arrays.fill(site.absorb, 0L);
		site.deferred.clear();
		site.blockedOps.clear();
		site.tidyQueue = null;
		site.legacyQueue = null;
		site.fastId = null;
		site.dirty = true;
	}

	/** Gives back every parked reservation (and the open component's unspent part): nothing reserved is ever lost. */
	private void releaseHeld(Site site) {
		if (site.openId != null && !site.dismantling) {
			closeOpen(site, true);
		}
		for (Site.Parked parked : site.parked.values()) {
			release(site, parked.held);
		}
		site.parked.clear();
		site.waiting.clear();
		site.suspended.clear();
		site.blockedCells.clear();
		site.blockedNow = 0;
		site.fundEmission = null;
		site.fundId = null;
		site.dirty = true;
	}

	/** The op index in its component of op {@code i} of the open emission. */
	private static int orig(Site site, int i) {
		return site.origIdx != null && i >= 0 && i < site.origIdx.length ? site.origIdx[i] : i;
	}

	// ================================================================== placement

	/** Runs up to {@code budget} ops of the site's open work; returns the ops spent (skips count 0.1). */
	private int run(MinecraftServer current, Site site, int budget) {
		ServerLevel level = current.getLevel(site.dim);
		Emission emission = site.emission;
		if (level == null || emission == null) {
			return 0;
		}
		if (site.dismantling) {
			return dismantleRun(level, site, budget);
		}
		CellIndex index = CellIndex.get(current);
		double spent = 0.0D;
		if (site.legacyQueue != null) {
			spent += legacyRun(level, site, budget, index);
			if (site.legacyQueue != null) {
				return (int) Math.ceil(spent);
			}
		}
		if (site.tidyQueue != null) {
			spent += tidyRun(level, site, budget - spent, index);
			if (site.tidyQueue != null && site.tidyCursor < site.tidyQueue.length) {
				return (int) Math.ceil(spent);
			}
			complete(level, site);
			return (int) Math.ceil(spent);
		}
		SitePlan plan = site.plan();
		int checks = Math.min(MAX_CHECKS, Math.max(16, budget * 10));
		EntityGuard guard = EntityGuard.gather(level, burstBox(plan, emission, site.cursor, Math.min(emission.size(), site.cursor + checks)));
		BlockPos first = null;
		BlockPos last = null;
		BlockState sample = null;
		int placedNow = 0;
		Bill.Builder refund = null;
		while (spent < budget && checks-- > 0 && site.cursor < emission.size()) {
			int i = site.cursor++;
			BlockPos pos = plan.world(emission.pos[i]);
			BlockState target = plan.world(emission.states[i]);
			Phase phase = emission.phase(i);
			site.extraOps = 0;
			BuildPlacer.Result result = BuildPlacer.place(level, site, pos, target, phase, guard, index);
			spent += site.extraOps;
			site.openPhase = emission.phases[i];
			site.openLayer = emission.layers[i];
			switch (result) {
				case PLACED -> {
					spent += 1.0D;
					placedNow++;
					site.placed++;
					if (first == null) {
						first = pos;
					}
					last = pos;
					if (!target.isAir()) {
						sample = target;
					}
				}
				case SKIPPED -> {
					// Settled once and never replayed: its reserved share goes back.
					spent += SKIP_COST;
					site.placed++;
					if (site.paid() && !target.isAir()) {
						if (refund == null) {
							refund = Bill.builder();
						}
						Costs.add(refund, target);
					}
				}
				case SKIPPED_OWN -> {
					spent += SKIP_COST;
					site.placed++;
				}
				case DEFERRED -> {
					spent += SKIP_COST;
					site.deferred.add(i);
				}
				case BLOCKED -> {
					spent += SKIP_COST;
					site.blockedOps.add(i);
				}
				case UNLOADED -> {
					site.cursor = i;
					if (refund != null) {
						site.refund = site.refund.plus(refund.build());
					}
					suspend(site);
					return (int) Math.ceil(spent);
				}
				case OUTSIDE -> spent += SKIP_COST;
			}
		}
		if (refund != null) {
			site.refund = site.refund.plus(refund.build());
		}
		if (placedNow > 0 && first != null) {
			BlockState rep = sample == null ? level.getBlockState(last) : sample;
			Fx.send(level, Vec3.atCenterOf(first), Vec3.atCenterOf(last), FxType.SECT_BUILD, BuildFxIds.PLACE, Block.getId(rep), placedNow);
			if (!rep.isAir()) {
				level.playSound(null, last, rep.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.35F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
			site.dirty = true;
		}
		if (site.cursor >= emission.size()) {
			if (!site.deferred.isEmpty()) {
				if (ticks >= site.deferUntil) {
					retryDeferred(level, site, index);
				}
			} else {
				complete(level, site);
			}
		}
		return (int) Math.ceil(spent);
	}

	/** Takes an upgrade's old cells down inside the open component's columns, layer by layer from the top (refunded). */
	private double legacyRun(ServerLevel level, Site site, double budget, CellIndex index) {
		long[] queue = site.legacyQueue;
		if (queue == null || queue.length <= 2) {
			site.legacyQueue = null;
			return 0.0D;
		}
		int top = (int) queue[0];
		int bottom = (int) queue[1];
		int columns = queue.length - 2;
		long total = (long) columns * (top - bottom + 1);
		double spent = 0.0D;
		int checks = LEGACY_CHECKS;
		EntityGuard guard = null;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		while (spent < budget && checks-- > 0 && site.legacyCursor < total) {
			long k = site.legacyCursor++;
			int y = top - (int) (k / columns);
			long column = queue[2 + (int) (k % columns)];
			cursor.set(BlockPos.getX(column), y, BlockPos.getZ(column));
			if (!level.isLoaded(cursor) || !index.isLegacy(level, cursor)) {
				continue;
			}
			BlockPos pos = cursor.immutable();
			if (guard == null) {
				guard = EntityGuard.gather(level, new AABB(pos).inflate(8.0D));
			}
			if (BuildPlacer.dismantle(level, site, pos, null, null, index, guard) == BuildPlacer.Result.PLACED) {
				spent += 1.0D;
			} else {
				spent += SKIP_COST;
			}
		}
		if (site.legacyCursor >= total) {
			site.legacyQueue = null;
			site.dirty = true;
		}
		return spent;
	}

	/** TIDY in bursts: each check costs a skip, each shape update a whole op. */
	private double tidyRun(ServerLevel level, Site site, double budget, CellIndex index) {
		long[] queue = site.tidyQueue;
		if (queue == null) {
			return 0.0D;
		}
		double spent = 0.0D;
		int checks = MAX_CHECKS;
		while (spent < budget && checks-- > 0 && site.tidyCursor < queue.length) {
			BlockPos pos = BlockPos.of(queue[site.tidyCursor++]);
			spent += BuildPlacer.tidy(level, pos, index) ? 1.0D : SKIP_COST;
		}
		return spent;
	}

	private void retryDeferred(ServerLevel level, Site site, CellIndex index) {
		Emission emission = site.emission;
		if (emission == null) {
			return;
		}
		SitePlan plan = site.plan();
		List<Integer> again = new ArrayList<>();
		List<Integer> todo = new ArrayList<>(site.deferred);
		BoundingBox box = null;
		for (int k = 0; k < Math.min(todo.size(), DEFER_RETRY_MAX); k++) {
			BlockPos pos = plan.world(emission.pos[todo.get(k)]);
			box = box == null ? new BoundingBox(pos) : BoundingBox.encapsulating(box, new BoundingBox(pos));
		}
		EntityGuard guard = EntityGuard.gather(level, box == null ? new AABB(site.stele) : AABB.of(box));
		site.deferTries++;
		for (int k = 0; k < todo.size(); k++) {
			int i = todo.get(k);
			if (k >= DEFER_RETRY_MAX) {
				again.add(i);
				continue;
			}
			BlockPos pos = plan.world(emission.pos[i]);
			BlockState target = plan.world(emission.states[i]);
			site.extraOps = 0;
			BuildPlacer.Result result = BuildPlacer.place(level, site, pos, target, emission.phase(i), guard, index);
			switch (result) {
				case DEFERRED -> {
					// A boat, cart, armor stand or frame never walks away: after a while it blocks like a player block.
					if (site.deferTries > DEFER_GIVE_UP || site.deferTries > DEFER_TRIES && guard.stuckAt(pos)) {
						site.blockedOps.add(i);
					} else {
						again.add(i);
					}
				}
				case BLOCKED, UNLOADED -> site.blockedOps.add(i);
				case SKIPPED -> {
					if (site.paid() && !target.isAir()) {
						Bill.Builder refund = Bill.builder();
						Costs.add(refund, target);
						site.refund = site.refund.plus(refund.build());
					}
				}
				default -> {
				}
			}
		}
		site.deferred.clear();
		site.deferred.addAll(again);
		site.deferUntil = ticks + DEFER_TICKS;
		site.dirty = true;
		if (!again.isEmpty()) {
			if (site.deferTries > DEFER_TRIES && ticks - site.lastMoveAway > 200L) {
				site.lastMoveAway = ticks;
				BlockPos pos = plan.world(emission.pos[again.get(0)]);
				for (ServerPlayer player : guard.playersNear(pos)) {
					Msg.actionBar(player, net.minecraft.network.chat.Component.translatable("kultivator.blueprint.blocked.move_away").withStyle(ChatFormatting.YELLOW));
				}
			}
		} else {
			complete(level, site);
		}
	}

	/** The box around every op the burst may visit (the guard gathers more on demand). */
	private static AABB burstBox(SitePlan plan, Emission emission, int from, int to) {
		if (from >= to) {
			return new AABB(plan.stele());
		}
		BoundingBox box = new BoundingBox(plan.world(emission.pos[from]));
		for (int i = from + 1; i < to; i++) {
			BlockPos pos = plan.world(emission.pos[i]);
			if (!box.isInside(pos)) {
				box = BoundingBox.encapsulating(box, new BoundingBox(pos));
			}
		}
		return AABB.of(box);
	}

	/** An unloaded chunk: the open component parks where it stopped, holding exactly its unspent share. */
	private void suspend(Site site) {
		if (site.openId == null) {
			return;
		}
		String id = site.openId;
		if (site.openMode == Site.MODE_REPAIR) {
			// A repair list is simply dropped (Perbaiki can run again): its unspent part goes back.
			closeOpen(site, true);
			site.nextRecheck = ticks + SUSPEND_TICKS;
			return;
		}
		Site.Parked parked = park(site);
		release(site, site.refund);
		site.refund = Bill.EMPTY;
		site.suspended.add(id);
		site.parked.put(id, parked);
		clearOpen(site);
		site.nextRecheck = ticks + SUSPEND_TICKS;
	}

	/** What the open component still has to do, in component op indices, with its unspent share (refund excluded). */
	private static Site.Parked park(Site site) {
		Emission emission = site.emission;
		Site.Parked parked = new Site.Parked();
		System.arraycopy(site.absorb, 0, parked.absorb, 0, Bahan.COUNT);
		if (emission == null) {
			parked.held = site.openRest.minus(site.refund);
			return parked;
		}
		Bill.Builder held = Bill.builder();
		held.add(Costs.bill(emission, site.cursor, emission.size()));
		BitSet rest = new BitSet();
		int max = 0;
		for (int i = site.cursor; i < emission.size(); i++) {
			int o = orig(site, i);
			rest.set(o);
			max = Math.max(max, o + 1);
		}
		for (List<Integer> list : List.of(site.deferred, site.blockedOps)) {
			for (int i : list) {
				if (i >= 0 && i < emission.size()) {
					int o = orig(site, i);
					if (!rest.get(o)) {
						rest.set(o);
						Costs.add(held, emission.states[i]);
					}
					max = Math.max(max, o + 1);
				}
			}
		}
		// The contiguous tail of unprocessed ops becomes the cursor; the rest below it are the parked ops.
		int cursor = site.cursor < emission.size() ? max : Integer.MAX_VALUE;
		if (cursor != Integer.MAX_VALUE) {
			while (cursor > 0 && rest.get(cursor - 1)) {
				cursor--;
			}
		}
		List<Integer> ops = new ArrayList<>();
		for (int o = rest.nextSetBit(0); o >= 0 && o < (cursor == Integer.MAX_VALUE ? max : cursor); o = rest.nextSetBit(o + 1)) {
			ops.add(o);
		}
		parked.cursor = cursor;
		parked.ops = ops.stream().mapToInt(Integer::intValue).toArray();
		parked.held = held.build().minus(new Bill(site.absorb, Map.of()));
		return parked;
	}

	/** The open component reached its end: TIDY in bursts, then DONE; or waiting when cells blocked it. */
	private void complete(ServerLevel level, Site site) {
		String id = site.openId;
		Emission emission = site.emission;
		if (id == null || emission == null) {
			return;
		}
		release(site, site.refund);
		site.refund = Bill.EMPTY;
		Component component = site.spec().component(id);
		if (site.openMode == Site.MODE_REPAIR) {
			closeOpen(site, true);
			return;
		}
		SitePlan plan = site.plan();
		if (!site.blockedOps.isEmpty()) {
			Site.Parked parked = park(site);
			List<Long> sample = new ArrayList<>();
			site.blockedCells.clear();
			for (int i : site.blockedOps) {
				long at = plan.world(emission.pos[i]).asLong();
				site.blockedCells.add(at);
				if (sample.size() < BLOCKED_SAMPLE) {
					sample.add(at);
				}
			}
			int count = site.blockedOps.size();
			site.waiting.put(id, sample);
			site.parked.put(id, parked);
			site.blockedNow = count;
			site.pingsLeft = PINGS;
			site.lastPing = 0L;
			site.nextRecheck = ticks + RECHECK_TICKS;
			if (component != null) {
				List<BlockPos> positions = new ArrayList<>();
				for (long each : sample) {
					positions.add(BlockPos.of(each));
				}
				BuildListener.fire().onBlocked(site.ledgerOwner(), view(site, component, ComponentView.STATE_BLOCKED), count, positions);
			}
			clearOpen(site);
			save(site);
			return;
		}
		// TIDY: fences and walls of this component (and their neighbours) reconnect, in budgeted bursts first.
		if (site.tidyQueue == null) {
			LongLinkedOpenHashSet tidy = new LongLinkedOpenHashSet();
			for (int i = 0; i < emission.size(); i++) {
				if (BuildPlacer.tidyable(emission.states[i])) {
					BlockPos pos = plan.world(emission.pos[i]);
					tidy.add(pos.asLong());
					tidy.add(pos.north().asLong());
					tidy.add(pos.south().asLong());
					tidy.add(pos.east().asLong());
					tidy.add(pos.west().asLong());
				}
			}
			if (!tidy.isEmpty()) {
				site.tidyQueue = tidy.toLongArray();
				site.tidyCursor = 0;
				return;
			}
		} else if (site.tidyCursor < site.tidyQueue.length) {
			return;
		}
		for (Map.Entry<String, BlockPos> anchor : emission.anchors.entrySet()) {
			BlockPos world = plan.world(anchor.getValue().getX(), anchor.getValue().getY(), anchor.getValue().getZ());
			site.anchors.put(anchor.getKey(), world);
			if (anchor.getKey().startsWith("waterfall")) {
				BuildPlacer.startWaterfall(level, world);
			}
		}
		site.done.add(id);
		site.waiting.remove(id);
		site.parked.remove(id);
		site.blockedNow = 0;
		clearOpen(site);
		if (component != null) {
			BoundingBox box = plan.worldBox(emission.bounds);
			if (!instant(site)) {
				sendBox(level, box, box.minY(), BuildFxIds.COMPONENT_DONE, component.tier(), box.maxY());
				BuildListener.fire().onComponentDone(site.ledgerOwner(), view(site, component, ComponentView.STATE_DONE));
			}
		}
		advanceTier(level, site);
		save(site);
	}

	private void advanceTier(ServerLevel level, Site site) {
		int reached = Scheduler.completedTier(site);
		boolean finishedTest = false;
		while (site.tier < reached) {
			site.tier++;
			if (!instant(site)) {
				tierFx(level, site);
				BuildListener.fire().onTierDone(site.ledgerOwner(), site.template, site.tier);
			}
			finishedTest = site.testTarget >= 0 && site.tier >= site.testTarget;
		}
		if (instant(site) && (finishedTest || Scheduler.next(site) == null && site.openId == null)) {
			// "instan": one ceremony at the end.
			tierFx(level, site);
			BuildListener.fire().onTierDone(site.ledgerOwner(), site.template, site.tier);
			site.speedMode = 0;
		}
		if (site.testTarget >= 0 && site.tier >= site.testTarget) {
			site.speedMode = 0;
		}
	}

	private void tierFx(ServerLevel level, Site site) {
		BlockPos hall = site.anchors.getOrDefault("main_hall", site.stele);
		BlockPos gate = site.plan().world(0, -1, site.spec().z1());
		Fx.send(level, Vec3.atCenterOf(hall), Vec3.atCenterOf(gate), FxType.SECT_BUILD, BuildFxIds.TIER_DONE, site.tier, site.template);
	}

	/**
	 * Sends a box effect (pos = box min, target = box max + 1, the client's outline contract) to every player near the box
	 * itself, not only to those within {@link Fx#RANGE} of its corner (templates 8 and 9 are far wider than that).
	 */
	public static void sendBox(ServerLevel level, BoundingBox box, double y, int id, int b, float f) {
		Vec3 min = new Vec3(box.minX(), y, box.minZ());
		Vec3 max = new Vec3(box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
		double range = Fx.RANGE;
		for (ServerPlayer player : level.players()) {
			double dx = Math.max(0.0D, Math.max(box.minX() - player.getX(), player.getX() - box.maxX() - 1));
			double dz = Math.max(0.0D, Math.max(box.minZ() - player.getZ(), player.getZ() - box.maxZ() - 1));
			if (dx * dx + dz * dz <= range * range) {
				Fx.sendTo(player, min, max, FxType.SECT_BUILD, id, b, f);
			}
		}
	}

	// ================================================================== dismantling (deconstruction, lower test tiers)

	private void nextDismantle(Site site) {
		List<Component> components = new ArrayList<>(site.spec().components());
		java.util.Collections.reverse(components);
		for (Component component : components) {
			if (component.tier() > site.dismantleTarget && site.done.contains(component.id())) {
				site.pendingId = component.id();
				site.pending = site.plan().emitAsync(component);
				return;
			}
		}
		finishDismantle(site);
	}

	private int dismantleRun(ServerLevel level, Site site, int budget) {
		Emission emission = site.emission;
		if (emission == null) {
			return 0;
		}
		CellIndex index = CellIndex.get(level.getServer());
		SitePlan plan = site.plan();
		int spent = 0;
		int checks = MAX_CHECKS;
		Undo undo = site.paid() ? null : Undo.get(level.getServer(), site.owner);
		BlockPos first = null;
		BlockPos last = null;
		EntityGuard guard = null;
		while (spent < budget && checks-- > 0 && site.cursor >= 0) {
			int i = site.cursor--;
			BlockState target = emission.states[i];
			Phase phase = emission.phase(i);
			if (target.isAir() || phase == Phase.CLEAR || phase == Phase.CUT || phase == Phase.FILL
					|| !site.deconstructTotal && site.dismantleTarget == 0 && (phase == Phase.PLINTH || phase == Phase.SHELL)) {
				continue;
			}
			BlockPos pos = plan.world(emission.pos[i]);
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (guard == null) {
				guard = EntityGuard.gather(level, new AABB(pos).inflate(8.0D));
			}
			BlockState replacement = undo == null ? null : undo.original(pos.asLong());
			BuildPlacer.Result result = BuildPlacer.dismantle(level, site, pos, plan.world(target), replacement, index, guard);
			if (result == BuildPlacer.Result.PLACED) {
				spent++;
				if (first == null) {
					first = pos;
				}
				last = pos;
			} else if (result == BuildPlacer.Result.DEFERRED) {
				site.deferred.add(i);
			}
		}
		if (first != null) {
			Fx.send(level, Vec3.atCenterOf(first), Vec3.atCenterOf(last), FxType.SECT_BUILD, BuildFxIds.DECONSTRUCT, 0, spent);
		}
		if (site.cursor < 0) {
			if (!site.deferred.isEmpty() && site.deferTries < DEFER_TRIES) {
				// Someone stood on a deck or floor: those cells are tried again a little later.
				if (ticks >= site.deferUntil) {
					site.deferTries++;
					site.deferUntil = ticks + DEFER_TICKS;
					List<Integer> again = new ArrayList<>(site.deferred);
					site.deferred.clear();
					EntityGuard retry = EntityGuard.gather(level, AABB.of(plan.worldBox(emission.bounds)));
					for (int i : again) {
						BlockPos pos = plan.world(emission.pos[i]);
						BlockState replacement = undo == null ? null : undo.original(pos.asLong());
						if (BuildPlacer.dismantle(level, site, pos, plan.world(emission.states[i]), replacement, index, retry) == BuildPlacer.Result.DEFERRED) {
							site.deferred.add(i);
						}
					}
				}
				return Math.max(1, spent);
			}
			site.done.remove(site.openId);
			site.anchors.entrySet().removeIf(entry -> emission.anchors.containsKey(entry.getKey()));
			site.openId = null;
			site.emission = null;
			site.deferred.clear();
			site.deferTries = 0;
			// The tier falls as tiers are removed (DESIGN 21.18).
			int built = Scheduler.builtTier(site);
			if (built < site.tier) {
				site.tier = built;
				if (site.paid() && sites.get(site.owner) == site) {
					Halls.refreshBuildings(site.owner);
				}
			}
			site.dirty = true;
		}
		return Math.max(1, spent);
	}

	private void finishDismantle(Site site) {
		site.dismantling = false;
		site.openId = null;
		site.emission = null;
		if (site.dismantleTarget <= 0) {
			// The whole template came down: the hall returns to its scan.
			removeSite(site, false);
			return;
		}
		// A lower test tier: replay the kept tiers instantly so shared cells are whole again.
		site.testTarget = site.dismantleTarget;
		site.done.removeIf(id -> {
			Component component = site.spec().component(id);
			return component != null && component.tier() <= site.dismantleTarget;
		});
		site.tier = 0;
		site.speedMode = 1;
		site.state = SiteView.STATE_ACTIVE;
		site.dirty = true;
		save(site);
	}

	private void removeSite(Site site, boolean keepCells) {
		releaseHeld(site);
		if (server != null && !keepCells) {
			CellIndex.get(server).clearOwner(site.owner);
		}
		if (site.showcase) {
			showcases.remove(site.owner);
			showcaseOwners.remove(site.owner);
			if (server != null) {
				Showcase.get(server).remove(site.owner);
			}
			return;
		}
		if (orphan(site)) {
			orphans.remove(site.owner);
			if (server != null) {
				Showcase.get(server).removeOrphan(site.owner);
			}
			return;
		}
		if (sites.get(site.owner) == site) {
			sites.remove(site.owner);
			clearSection(site.owner);
		}
	}

	// ================================================================== views

	ComponentView view(Site site, Component component, int state) {
		BoundingBox local = component.bounds(site.plan().ctx());
		BoundingBox box = site.plan().worldBox(local);
		boolean open = component.id().equals(site.openId);
		int placed = open ? site.placed : state == ComponentView.STATE_DONE ? site.opCounts.getOrDefault(component.id(), 0) : 0;
		int total = open ? site.total : site.opCounts.getOrDefault(component.id(), 0);
		int blocked = site.waiting.containsKey(component.id()) ? site.waiting.get(component.id()).size() : open ? site.blockedOps.size() : 0;
		Site.Parked parked = site.parked.get(component.id());
		Bill bill = open ? site.reserve : parked != null ? parked.held : site.bills.getOrDefault(component.id(), Bill.EMPTY);
		return new ComponentView(component.id(), component.kind(), component.tier(), component.name(), box, box.minY(), state, placed, total, blocked, bill);
	}

	private int stateOf(Site site, Component component, int nextTier) {
		String id = component.id();
		if (site.done.contains(id)) {
			return ComponentView.STATE_DONE;
		}
		if (site.skipped.contains(id)) {
			return ComponentView.STATE_SKIPPED;
		}
		if (id.equals(site.openId)) {
			return ComponentView.STATE_OPEN;
		}
		if (site.waiting.containsKey(id)) {
			return ComponentView.STATE_BLOCKED;
		}
		if (site.suspended.contains(id)) {
			return ComponentView.STATE_SUSPENDED;
		}
		return component.tier() <= nextTier ? ComponentView.STATE_QUEUED : ComponentView.STATE_FUTURE;
	}

	/** The sect name for the plaque signs (the hall's name, else the template's). */
	net.minecraft.network.chat.Component plaqueName(Site site) {
		if (!site.showcase) {
			Halls.HallView hall = Halls.hallOf(site.ledgerOwner());
			if (hall != null && hall.name() != null && !hall.name().isBlank()) {
				return net.minecraft.network.chat.Component.literal(hall.name());
			}
		}
		return SectTemplates.name(site.template);
	}

	/** Footprints of every other site, showcase and relocation orphan (overlap checks). */
	List<BoundingBox> otherSites(UUID owner, boolean showcase) {
		List<BoundingBox> list = new ArrayList<>();
		for (Site site : allSites()) {
			if (!showcase && site.owner.equals(owner) && !site.showcase && !site.restoring) {
				continue;
			}
			list.add(site.footprint());
		}
		return list;
	}

	/** True when "Kosongkan" is switched on for {@code owner} (the preview marks containers amber instead of red). */
	boolean emptyAuthorized(UUID owner) {
		return (auth.getOrDefault(owner, 0) & 2) != 0;
	}

	// ================================================================== EngineApi: founding

	@Override
	public long preview(ServerPlayer player, int template, int rot, Glaze glaze) {
		return startPreview(player, template, rot, glaze, false, Previews.Kind.FOUND, null, 9);
	}

	private long startPreview(ServerPlayer player, int template, int rot, Glaze glaze, boolean force, Previews.Kind kind, @Nullable BlockPos origin,
			int showTier) {
		if (server == null) {
			return 0L;
		}
		if (!KultivatorConfig.get().sectConstruction) {
			error(player, "disabled");
			return 0L;
		}
		if (!SectTemplates.valid(template)) {
			error(player, "bad_template");
			return 0L;
		}
		if (kind == Previews.Kind.FOUND && !Validator.allowed(template)) {
			error(player, "max_template");
			return 0L;
		}
		BlockPos at = origin;
		net.minecraft.resources.ResourceKey<Level> dim = player.level().dimension();
		if (kind == Previews.Kind.FOUND || kind == Previews.Kind.TEST || kind == Previews.Kind.UPGRADE) {
			Halls.HallView hall = Halls.hallOf(player.getUUID());
			if (hall == null) {
				if (kind == Previews.Kind.TEST) {
					player.getInventory().add(new ItemStack(HallItems.SECT_HALL_STELE_ITEM));
					error(player, "test_no_hall");
				} else {
					error(player, "no_hall");
				}
				return 0L;
			}
			if (!hall.active()) {
				error(player, "hall_inactive");
				return 0L;
			}
			at = hall.pos();
			dim = hall.dim();
		}
		if (at == null) {
			at = player.blockPosition();
		}
		return previews.start(server, player.getUUID(), player, template, Math.floorMod(rot, 4), glaze == null ? Glaze.TINTA : glaze, force, kind, dim, at,
				showTier, emptyAuthorized(player.getUUID()));
	}

	@Override
	public @Nullable PreviewData previewData(long ticket) {
		Previews.Job job = previews.job(ticket);
		return job == null ? null : job.data;
	}

	@Override
	public void cancel(long ticket) {
		previews.cancel(ticket);
		autoConfirm.remove(ticket);
		autoTier.remove(ticket);
	}

	@Override
	public @Nullable net.minecraft.network.chat.Component confirm(ServerPlayer player, long ticket) {
		Previews.Job job = previews.job(ticket);
		if (job == null) {
			return err("no_preview");
		}
		if (job.data == null || job.compiled == null) {
			return err("compiling");
		}
		if (!job.player.equals(player.getUUID())) {
			return err("not_owner");
		}
		if (!job.data.confirmable()) {
			return err("not_confirmable");
		}
		if (!Templates.ready(job.template)) {
			return err("not_ready");
		}
		return switch (job.kind) {
			case FOUND, TEST -> found(player, job);
			case SHOWCASE -> showcaseConfirm(player, job);
			case UPGRADE -> beginUpgrade(job.owner, job.template, ticket);
			case HOLOGRAM -> err("hologram_only");
		};
	}

	private @Nullable net.minecraft.network.chat.Component found(ServerPlayer player, Previews.Job job) {
		Site existing = sites.get(job.owner);
		if (existing != null) {
			if (existing.restoring) {
				return err("restoring");
			}
			boolean untouched = existing.statPlaced == 0L && existing.done.isEmpty() && existing.openId == null;
			if (!untouched && !(job.kind == Previews.Kind.TEST && existing.test)) {
				return err("has_site");
			}
			if (existing.test && !untouched) {
				// The old test site is put back first (in slices); confirming again founds the new one.
				reset(existing.owner);
				return err("restoring");
			}
			// An untouched site: whatever it reserved goes back before it is replaced.
			releaseHeld(existing);
			sites.remove(job.owner);
		}
		Site site = newSite(job.owner, job, job.kind == Previews.Kind.TEST);
		sites.put(job.owner, site);
		save(site);
		previews.cancel(job.ticket);
		long blocks = job.compiled.tag().getLongOr("ops", 0L);
		Kultivator.LOGGER.info("[Kultivator] Bangun: {} {} T{} -> {} blocks", player.getName().getString(), SectTemplates.id(site.template), site.tier + 1, blocks);
		// The Batu Pertama outline is drawn once, by the flow's ceremony (onConfirmed).
		BuildListener.fire().onConfirmed(site.owner, site.template);
		return null;
	}

	private Site newSite(UUID owner, Previews.Job job, boolean test) {
		Site site = new Site(owner);
		site.dim = job.dim;
		site.stele = job.origin;
		site.template = job.template;
		site.rotation = job.rotation;
		site.glaze = job.glaze;
		site.seed = job.compiled.seed();
		site.plazaY = job.origin.getY() - 1;
		site.snapshot = job.compiled.snapshot();
		site.levels.putAll(job.compiled.levels());
		site.planVersion = Templates.get(job.template).planVersion();
		site.state = SiteView.STATE_ACTIVE;
		site.test = test;
		int flags = auth.getOrDefault(owner, 0);
		site.removePlayerBlocks = (flags & 1) != 0;
		site.emptyContainers = (flags & 2) != 0;
		site.authOp = (flags & 4) != 0;
		site.dirty = true;
		return site;
	}

	private @Nullable net.minecraft.network.chat.Component showcaseConfirm(ServerPlayer player, Previews.Job job) {
		if (showcases.size() >= Showcase.MAX_SHOWCASES) {
			return err("showcase_limit");
		}
		UUID id = UUID.randomUUID();
		Site site = newSite(id, job, true);
		site.showcase = true;
		site.testTarget = Math.max(1, job.showTier);
		site.speedMode = 1;
		site.removePlayerBlocks = false;
		site.emptyContainers = false;
		showcases.put(id, site);
		showcaseOwners.put(id, player.getUUID());
		save(site);
		previews.cancel(job.ticket);
		return null;
	}

	private void handleAutoConfirm(MinecraftServer current) {
		if (autoConfirm.isEmpty()) {
			return;
		}
		for (Long ticket : new ArrayList<>(autoConfirm.keySet())) {
			Previews.Job job = previews.job(ticket);
			if (job == null) {
				autoConfirm.remove(ticket);
				autoTier.remove(ticket);
				continue;
			}
			if (job.data == null || job.compiledPercent < 100) {
				continue;
			}
			autoConfirm.remove(ticket);
			Integer keep = autoTier.remove(ticket);
			ServerPlayer player = current.getPlayerList().getPlayer(job.player);
			net.minecraft.network.chat.Component error = beginUpgrade(job.owner, job.template, ticket);
			if (player != null && error != null) {
				Msg.chat(player, error.copy().withStyle(ChatFormatting.YELLOW));
			}
			Site site = sites.get(job.owner);
			if (error == null && site != null && keep != null) {
				site.test = true;
				site.testTarget = keep;
				site.speedMode = 1;
			}
		}
	}

	// ================================================================== EngineApi: reading

	@Override
	public int template(UUID owner) {
		Site site = sites.get(owner);
		return site == null ? 0 : site.template;
	}

	@Override
	public int tier(UUID owner) {
		Site site = sites.get(owner);
		if (site == null || server == null) {
			return 0;
		}
		ServerLevel level = server.getLevel(site.dim);
		return level != null && steleOk(site, level) ? site.tier : 0;
	}

	@Override
	public @Nullable SiteView site(UUID owner) {
		Site site = sites.get(owner);
		return site == null ? null : site.view();
	}

	@Override
	public List<ComponentView> components(UUID owner) {
		Site site = sites.get(owner);
		if (site == null) {
			return List.of();
		}
		Component next = Scheduler.next(site);
		int nextTier = next == null ? site.tier + 1 : Math.max(site.tier + 1, next.tier());
		List<ComponentView> list = new ArrayList<>();
		for (Component component : site.spec().components()) {
			list.add(view(site, component, stateOf(site, component, nextTier)));
		}
		return list;
	}

	@Override
	public Bill remainingBill(UUID owner, int tierOr0) {
		Site site = sites.get(owner);
		if (site == null) {
			return Bill.EMPTY;
		}
		Bill.Builder bill = Bill.builder();
		for (Component component : site.spec().components()) {
			String id = component.id();
			if (site.finished(id) || id.equals(site.openId) || site.waiting.containsKey(id) || site.suspended.contains(id)) {
				continue;
			}
			if (tierOr0 > 0 && component.tier() != tierOr0) {
				continue;
			}
			Bill each = site.bills.get(id);
			if (each != null) {
				bill.add(each);
			}
		}
		return bill.build();
	}

	@Override
	public Bill tierBill(int template, int tier, Glaze glaze) {
		return PlanAudit.tierBill(template, tier, glaze == null ? Glaze.TINTA : glaze);
	}

	@Override
	public PlanStats stats(int template) {
		return PlanAudit.stats(template);
	}

	@Override
	public AuditReport audit(@Nullable Integer template) {
		return PlanAudit.audit(template);
	}

	@Override
	public boolean isSectCell(ServerLevel level, BlockPos pos) {
		return server != null && CellIndex.get(server).isPlaced(level, pos);
	}

	@Override
	public boolean insideAnySite(Level level, BlockPos pos, int margin) {
		for (Site site : allSites()) {
			if (!site.dim.equals(level.dimension())) {
				continue;
			}
			BoundingBox fp = site.footprint();
			if (pos.getX() >= fp.minX() - margin && pos.getX() <= fp.maxX() + margin && pos.getZ() >= fp.minZ() - margin && pos.getZ() <= fp.maxZ() + margin) {
				return true;
			}
		}
		return false;
	}

	@Override
	public @Nullable BlockPos anchor(UUID owner, String id) {
		Site site = sites.get(owner);
		return site == null ? null : site.anchors.get(id);
	}

	@Override
	public @Nullable BlockPos workSpot(UUID owner) {
		Site site = sites.get(owner);
		if (site == null || site.emission == null || site.emission.bounds == null) {
			return null;
		}
		if (site.workSpot == null || ticks >= site.workSpotUntil) {
			BoundingBox box = site.plan().worldBox(site.emission.bounds);
			int y = site.stele.getY();
			int midX = (box.minX() + box.maxX()) / 2;
			int midZ = (box.minZ() + box.maxZ()) / 2;
			BlockPos[] spots = {new BlockPos(box.minX() - 1, y, midZ), new BlockPos(box.maxX() + 1, y, midZ), new BlockPos(midX, y, box.minZ() - 1),
					new BlockPos(midX, y, box.maxZ() + 1)};
			site.workSpot = spots[(int) ((ticks / WORK_SPOT_TICKS) % spots.length)];
			site.workSpotUntil = ticks + WORK_SPOT_TICKS;
		}
		return site.workSpot;
	}

	// ================================================================== EngineApi: control

	@Override
	public void setRateMultiplier(UUID owner, double factor) {
		Site site = sites.get(owner);
		if (site != null) {
			site.rateMultiplier = Double.isNaN(factor) ? 1.0D : Math.max(0.0D, Math.min(100.0D, factor));
		}
	}

	@Override
	public void setBank(UUID owner, long ticksBanked) {
		Site site = sites.get(owner);
		if (site != null) {
			site.bank = Math.max(0L, Math.min(KultivatorConfig.get().sectBuildBankTicks, ticksBanked));
			site.dirty = true;
		}
	}

	@Override
	public boolean pause(UUID owner, boolean paused) {
		Site site = sites.get(owner);
		if (site == null) {
			return false;
		}
		site.state = paused ? SiteView.STATE_PAUSED : site.dismantling ? SiteView.STATE_DECONSTRUCTING : SiteView.STATE_ACTIVE;
		site.dirty = true;
		save(site);
		return true;
	}

	/**
	 * Puts a component first in its tier's queue. A skipped component is queued again (it is built like any other and
	 * only then counts for the tier). When another component is only waiting for its materials (nothing reserved yet,
	 * so nothing to give back), that wait is dropped so the next refresh opens the prioritized one.
	 */
	@Override
	public boolean prioritize(UUID owner, String componentId) {
		Site site = sites.get(owner);
		if (site == null || site.spec().component(componentId) == null || site.done.contains(componentId)) {
			return false;
		}
		boolean requeued = site.skipped.remove(componentId);
		site.prio = componentId;
		if (site.fundId != null && site.openId == null && !site.fundId.equals(componentId)) {
			Component next = Scheduler.next(site);
			if (next != null && next.id().equals(componentId)) {
				site.fundEmission = null;
				site.fundId = null;
				site.fundTold = false;
			}
		}
		site.dirty = true;
		if (requeued) {
			save(site);
		}
		return true;
	}

	/**
	 * Skips a component that cannot go on now: one waiting on blocked cells, or the one waiting for its materials. A
	 * skipped component never counts toward the completed tier (the hall's building cap and bits), and "Prioritaskan"
	 * queues it again. Test and showcase sites may skip anything.
	 */
	@Override
	public boolean skip(UUID owner, String componentId) {
		Site site = sites.get(owner);
		if (site == null || site.spec().component(componentId) == null || site.finished(componentId)) {
			return false;
		}
		if (!skippable(site, componentId)) {
			return false;
		}
		if (componentId.equals(site.openId)) {
			closeOpen(site, true);
		}
		Site.Parked parked = site.parked.remove(componentId);
		if (parked != null) {
			release(site, parked.held);
		}
		site.waiting.remove(componentId);
		site.suspended.remove(componentId);
		if (componentId.equals(site.fundId)) {
			site.fundEmission = null;
			site.fundId = null;
		}
		site.skipped.add(componentId);
		site.dirty = true;
		if (server != null) {
			ServerLevel level = server.getLevel(site.dim);
			if (level != null) {
				advanceTier(level, site);
			}
		}
		save(site);
		return true;
	}

	/** True when the owner may skip the component now (blocked, or waiting for materials; anything on a test site). */
	static boolean skippable(Site site, String componentId) {
		if (site.test || site.showcase) {
			return true;
		}
		return site.waiting.containsKey(componentId) || componentId.equals(site.fundId) && site.openId == null;
	}

	@Override
	public boolean finishWithoutBlocked(UUID owner, String componentId) {
		Site site = sites.get(owner);
		if (site == null || !site.waiting.containsKey(componentId)) {
			return false;
		}
		site.waiting.remove(componentId);
		Site.Parked parked = site.parked.remove(componentId);
		if (parked != null) {
			// The blocked cells are never placed: their reserved share goes back.
			release(site, parked.held);
		}
		site.done.add(componentId);
		site.blockedNow = 0;
		site.blockedCells.clear();
		site.dirty = true;
		if (server != null) {
			ServerLevel level = server.getLevel(site.dim);
			Component component = site.spec().component(componentId);
			if (level != null && component != null) {
				// Its anchors still count: emitted off the server thread, applied when they arrive.
				anchorJobs.add(new AnchorJob(site, site.plan().emitAsync(component)));
				BuildListener.fire().onComponentDone(site.ledgerOwner(), view(site, component, ComponentView.STATE_DONE));
				advanceTier(level, site);
			}
		}
		save(site);
		return true;
	}

	private void anchorJobs() {
		for (java.util.Iterator<AnchorJob> it = anchorJobs.iterator(); it.hasNext(); ) {
			AnchorJob job = it.next();
			if (!job.emission().isDone()) {
				continue;
			}
			it.remove();
			try {
				Emission emission = job.emission().join();
				SitePlan plan = job.site().plan();
				for (Map.Entry<String, BlockPos> anchor : emission.anchors.entrySet()) {
					job.site().anchors.put(anchor.getKey(), plan.world(anchor.getValue().getX(), anchor.getValue().getY(), anchor.getValue().getZ()));
				}
				job.site().dirty = true;
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect component anchors failed to emit", exception);
			}
		}
	}

	@Override
	public void recheck(UUID owner) {
		Site site = sites.get(owner);
		if (site != null) {
			site.nextRecheck = 0L;
		}
	}

	@Override
	public void authorize(UUID owner, boolean removePlayerBlocks, boolean emptyContainers) {
		authorize(owner, removePlayerBlocks, emptyContainers, (auth.getOrDefault(owner, 0) & 4) != 0);
	}

	@Override
	public void authorize(UUID owner, boolean removePlayerBlocks, boolean emptyContainers, boolean byOp) {
		auth.put(owner, (removePlayerBlocks ? 1 : 0) | (emptyContainers ? 2 : 0) | (byOp ? 4 : 0));
		Site site = sites.get(owner);
		if (site != null) {
			site.removePlayerBlocks = removePlayerBlocks;
			site.emptyContainers = emptyContainers;
			site.authOp = byOp;
			site.dirty = true;
		}
	}

	@Override
	public int repair(UUID owner) {
		Site site = sites.get(owner);
		if (site == null || server == null) {
			return -1;
		}
		if (site.openId != null || site.pending != null || site.fundEmission != null || site.dismantling || site.restoring || site.repairScan != null
				|| site.repairList != null) {
			return -2;
		}
		SitePlan plan = site.plan();
		List<Component> done = new ArrayList<>();
		for (Component component : site.spec().components()) {
			if (site.done.contains(component.id())) {
				done.add(component);
			}
		}
		if (done.isEmpty()) {
			return 0;
		}
		// The final planned state of every cell of the finished components (later ones win), emitted off the server thread.
		site.repairScan = CompletableFuture.supplyAsync(() -> {
			Long2ObjectLinkedOpenHashMap<BlockState> wanted = new Long2ObjectLinkedOpenHashMap<>();
			Map<Long, Byte> phases = new HashMap<>();
			for (Component component : done) {
				Emission emission = plan.emit(component);
				for (int i = 0; i < emission.size(); i++) {
					wanted.put(emission.pos[i], emission.states[i]);
					phases.put(emission.pos[i], emission.phases[i]);
				}
			}
			List<Long> pos = new ArrayList<>();
			for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<BlockState> entry : wanted.long2ObjectEntrySet()) {
				if (!entry.getValue().isAir()) {
					pos.add(entry.getLongKey());
				}
			}
			long[] positions = new long[pos.size()];
			BlockState[] states = new BlockState[pos.size()];
			byte[] ph = new byte[pos.size()];
			for (int i = 0; i < positions.length; i++) {
				positions[i] = pos.get(i);
				states[i] = wanted.get(positions[i]);
				ph[i] = phases.getOrDefault(positions[i], (byte) 0);
			}
			return new Site.RepairScan(positions, states, ph);
		}, net.minecraft.util.Util.backgroundExecutor());
		site.dirty = true;
		return done.size();
	}

	/** Compares the planned cells with the world in slices; at the end the air cells become a paid repair emission. */
	private void repairSlice(MinecraftServer current, Site site) {
		Site.RepairScan scan = site.repairList;
		ServerLevel level = current.getLevel(site.dim);
		if (scan == null || level == null) {
			return;
		}
		CellIndex index = CellIndex.get(current);
		SitePlan plan = site.plan();
		int checks = REPAIR_CHECKS;
		while (checks-- > 0 && scan.cursor < scan.pos.length) {
			int i = scan.cursor++;
			BlockPos pos = plan.world(scan.pos[i]);
			if (!level.isLoaded(pos) || index.isRemoved(level, pos)) {
				continue;
			}
			if (level.getBlockState(pos).isAir()) {
				scan.missing.add(i);
			}
		}
		if (scan.cursor < scan.pos.length) {
			return;
		}
		site.repairList = null;
		ServerPlayer owner = current.getPlayerList().getPlayer(site.ledgerOwner());
		if (scan.missing.isEmpty()) {
			if (owner != null) {
				Msg.chat(owner, net.minecraft.network.chat.Component.translatable("kultivator.blueprint.repair.none").withStyle(ChatFormatting.AQUA));
			}
			return;
		}
		// Supports first: ordered by phase, then by layer (y).
		List<Integer> order = new ArrayList<>(scan.missing);
		order.sort((a, b) -> scan.phases[a] != scan.phases[b] ? Byte.compare(scan.phases[a], scan.phases[b])
				: Integer.compare(BlockPos.getY(scan.pos[a]), BlockPos.getY(scan.pos[b])));
		long[] pos = new long[order.size()];
		BlockState[] st = new BlockState[order.size()];
		byte[] ph = new byte[order.size()];
		int[] layers = new int[order.size()];
		for (int k = 0; k < pos.length; k++) {
			int i = order.get(k);
			pos[k] = scan.pos[i];
			st[k] = scan.states[i];
			ph[k] = scan.phases[i];
			layers[k] = BlockPos.getY(scan.pos[i]);
		}
		Emission repair = Emission.of("repair", pos, st, ph, layers);
		Bill bill = Costs.bill(repair);
		if (site.openId != null || site.pending != null || site.fundEmission != null || site.dismantling) {
			return;
		}
		if (site.paid() && !MaterialLedger.get().reserve(site.ledgerOwner(), bill)) {
			BuildListener.fire().onWaitingFunds(site.ledgerOwner(), bill);
			if (owner != null) {
				Msg.chat(owner, net.minecraft.network.chat.Component.translatable("kultivator.blueprint.repair.funds", pos.length).withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		if (owner != null) {
			Msg.chat(owner, net.minecraft.network.chat.Component.translatable("kultivator.blueprint.repair.found", pos.length).withStyle(ChatFormatting.AQUA));
		}
		open(level, site, "repair", repair, site.paid() ? bill : Bill.EMPTY, new long[Bahan.COUNT], Site.MODE_REPAIR, null, false);
	}

	@Override
	public boolean deconstruct(UUID owner, boolean total) {
		Site site = sites.get(owner);
		if (site == null) {
			return false;
		}
		startDismantle(site, 0, total);
		return true;
	}

	private void startDismantle(Site site, int target, boolean total) {
		if (!site.dismantling) {
			// Whatever was reserved and not placed goes back first.
			releaseHeld(site);
		}
		site.fundEmission = null;
		site.fundId = null;
		site.pending = null;
		site.pendingId = null;
		site.reopened = false;
		site.repairScan = null;
		site.repairList = null;
		site.dismantling = true;
		site.dismantleTarget = Math.max(0, target);
		site.deconstructTotal = total;
		site.state = SiteView.STATE_DECONSTRUCTING;
		site.openId = null;
		site.emission = null;
		site.deferred.clear();
		site.deferTries = 0;
		site.dirty = true;
		save(site);
	}

	@Override
	public @Nullable net.minecraft.network.chat.Component beginUpgrade(UUID owner, int newTemplate, long ticket) {
		Site site = sites.get(owner);
		if (site == null) {
			return err("no_site");
		}
		Previews.Job job = previews.job(ticket);
		if (job == null || job.compiled == null || job.data == null) {
			return err("no_preview");
		}
		if (newTemplate <= site.template || job.template != newTemplate) {
			return err("upgrade_smaller");
		}
		if (!job.data.confirmable()) {
			return err("not_confirmable");
		}
		if (!Templates.ready(newTemplate)) {
			return err("not_ready");
		}
		// Every reservation of the old plan goes back; its blocks become legacy cells the new plan takes down where it builds.
		releaseHeld(site);
		site.pending = null;
		site.pendingId = null;
		site.reopened = false;
		site.repairScan = null;
		site.repairList = null;
		if (server != null) {
			CellIndex.get(server).toLegacy(site.owner);
		}
		site.template = newTemplate;
		site.snapshot = job.compiled.snapshot();
		site.levels.clear();
		site.levels.putAll(job.compiled.levels());
		site.seed = job.compiled.seed();
		site.plan = null;
		site.done.clear();
		site.skipped.clear();
		site.bills.clear();
		site.opCounts.clear();
		site.survey = null;
		site.tier = 0;
		site.prio = null;
		site.planVersion = Templates.get(newTemplate).planVersion();
		site.state = SiteView.STATE_ACTIVE;
		site.dirty = true;
		previews.cancel(ticket);
		save(site);
		BuildListener.fire().onConfirmed(owner, newTemplate);
		return null;
	}

	@Override
	public boolean release(UUID owner, boolean keepBlocks) {
		Site site = sites.get(owner);
		if (site == null) {
			return false;
		}
		if (!keepBlocks) {
			startDismantle(site, 0, true);
			return true;
		}
		removeSite(site, false);
		return true;
	}

	@Override
	public @Nullable net.minecraft.network.chat.Component relocate(UUID owner, BlockPos newStele, boolean deconstructOld) {
		Site site = sites.get(owner);
		if (site == null) {
			return err("no_site");
		}
		if (!deconstructOld) {
			release(owner, true);
			return null;
		}
		// The old site keeps coming down (with refunds to the player) whenever the player is near it, also after a restart;
		// it gets its own key, so its cells never mix with the new site's and the slot is free for a new founding.
		releaseHeld(site);
		UUID key = UUID.randomUUID();
		sites.remove(owner);
		clearSection(owner);
		if (server != null) {
			CellIndex.get(server).reassign(owner, key);
		}
		site.refundTo = owner;
		site.owner = key;
		orphans.put(key, site);
		startDismantle(site, 0, false);
		return null;
	}

	// ================================================================== EngineApi: debug and test

	@Override
	public void setTierCounter(UUID owner, int tier) {
		Site site = sites.get(owner);
		if (site != null) {
			site.tier = Math.max(0, Math.min(SectTemplates.TIERS, tier));
			site.dirty = true;
			save(site);
		}
	}

	@Override
	public boolean finishOpen(UUID owner) {
		Site site = sites.get(owner);
		if (site == null || site.openId == null || site.emission == null || server == null) {
			return false;
		}
		ServerLevel level = server.getLevel(site.dim);
		if (level == null || computeStatus(server, site, level, KultivatorConfig.get(), server.getAverageTickTimeNanos() / 1_000_000L) != SiteView.STATUS_RUNNING) {
			return false;
		}
		// Finished like "instan": 512 ops a tick, with every pause and safety check of normal building.
		site.fastId = site.openId;
		site.deferUntil = 0L;
		return true;
	}

	@Override
	public void replan(UUID owner) {
		Site site = sites.get(owner);
		if (site == null) {
			return;
		}
		if (site.openId != null && site.emission != null) {
			site.openRest = unspent(site);
		}
		site.plan = null;
		site.planVersion = site.spec().planVersion();
		if (site.openId != null) {
			site.emission = null;
		}
		site.bills.clear();
		site.opCounts.clear();
		site.survey = null;
		site.dirty = true;
		save(site);
	}

	@Override
	public long previewTest(ServerPlayer player, int template, int rot, Glaze glaze, boolean force) {
		return startPreview(player, template, rot, glaze, force, Previews.Kind.TEST, null, 9);
	}

	@Override
	public @Nullable net.minecraft.network.chat.Component testBuild(ServerPlayer player, int tier, int speedMode) {
		Site site = sites.get(player.getUUID());
		if (site == null || site.restoring) {
			return err("no_test_site");
		}
		int target = Math.max(0, Math.min(SectTemplates.TIERS, tier));
		if (!site.test) {
			// A real site becomes a test site: its reservations go back first, since test builds are free.
			releaseHeld(site);
		}
		site.test = true;
		if (site.fundEmission != null) {
			site.fundEmission = null;
			site.fundId = null;
		}
		if (target < site.tier) {
			site.speedMode = 1;
			startDismantle(site, target, true);
			return null;
		}
		site.testTarget = target;
		site.speedMode = Math.max(0, Math.min(2, speedMode));
		site.state = SiteView.STATE_ACTIVE;
		site.dirty = true;
		save(site);
		return null;
	}

	@Override
	public @Nullable net.minecraft.network.chat.Component testUpgrade(ServerPlayer player, int newTemplate) {
		Site site = sites.get(player.getUUID());
		if (site == null || site.restoring) {
			return err("no_test_site");
		}
		if (newTemplate <= site.template || !Templates.ready(newTemplate)) {
			return err("upgrade_smaller");
		}
		long ticket = startPreview(player, newTemplate, site.rotation, site.glaze, true, Previews.Kind.UPGRADE, null, 9);
		if (ticket == 0L) {
			return err("no_preview");
		}
		autoConfirm.put(ticket, Boolean.TRUE);
		autoTier.put(ticket, Math.max(site.tier, 1));
		return null;
	}

	@Override
	public @Nullable net.minecraft.network.chat.Component testDeconstruct(ServerPlayer player) {
		Site site = sites.get(player.getUUID());
		if (site == null || site.restoring) {
			return err("no_test_site");
		}
		if (!site.test) {
			releaseHeld(site);
		}
		site.test = true;
		site.speedMode = 1;
		startDismantle(site, 0, true);
		return null;
	}

	@Override
	public long showcase(ServerPlayer player, int template, int tier, int rot, BlockPos origin) {
		if (showcases.size() >= Showcase.MAX_SHOWCASES) {
			error(player, "showcase_limit");
			return 0L;
		}
		return startPreview(player, template, rot, Glaze.TINTA, false, Previews.Kind.SHOWCASE, origin, Math.max(1, Math.min(9, tier)));
	}

	@Override
	public int showcaseRemove(ServerPlayer player, boolean all) {
		if (server == null) {
			return 0;
		}
		List<Site> targets = new ArrayList<>();
		if (all) {
			targets.addAll(showcases.values());
		} else {
			Site nearest = null;
			double best = Double.MAX_VALUE;
			for (Site site : showcases.values()) {
				double distance = site.stele.distSqr(player.blockPosition());
				if (distance < best && !site.restoring) {
					best = distance;
					nearest = site;
				}
			}
			if (nearest != null) {
				targets.add(nearest);
			}
		}
		int removed = 0;
		for (Site site : targets) {
			if (!site.restoring) {
				beginRestore(site);
				removed++;
			}
		}
		return removed;
	}

	/** Starts putting a test site or showcase back from its undo log, in budgeted slices over ticks. */
	private void beginRestore(Site site) {
		closeOpen(site, false);
		site.pending = null;
		site.pendingId = null;
		site.fundEmission = null;
		site.fundId = null;
		site.dismantling = false;
		site.repairScan = null;
		site.repairList = null;
		site.restoring = true;
		site.status = SiteView.STATUS_PAUSED;
		restoreOrder.remove(site.owner);
		restoreCursor.remove(site.owner);
		restoreRetry.remove(site.owner);
		site.dirty = true;
		save(site);
	}

	/**
	 * One restore slice (DESIGN 21.22 {@code reset}, {@code pamer hapus}): undo-logged cells are put back newest first; a
	 * cell that cannot be restored now (unloaded, someone in the way) keeps its record for the next pass; a cell a player
	 * changed keeps the player's block. Only when the log is empty are the index entries and the site removed.
	 */
	private void restoreSlice(MinecraftServer current, Site site) {
		ServerLevel level = current.getLevel(site.dim);
		if (level == null) {
			return;
		}
		Undo undo = Undo.get(current, site.owner);
		if (undo.size() == 0) {
			CellIndex.get(current).clearOwner(site.owner);
			restoreOrder.remove(site.owner);
			restoreCursor.remove(site.owner);
			restoreRetry.remove(site.owner);
			site.restoring = false;
			removeSite(site, true);
			return;
		}
		long[] order = restoreOrder.get(site.owner);
		int cursor = restoreCursor.getOrDefault(site.owner, 0);
		if (order == null || cursor >= order.length) {
			if (order != null && ticks < restoreRetry.getOrDefault(site.owner, 0L)) {
				return;
			}
			List<Long> positions = undo.positions();
			order = new long[positions.size()];
			for (int i = 0; i < order.length; i++) {
				order[i] = positions.get(order.length - 1 - i);
			}
			restoreOrder.put(site.owner, order);
			cursor = 0;
		}
		CellIndex index = CellIndex.get(current);
		EntityGuard guard = EntityGuard.gather(level, new AABB(BlockPos.of(order[cursor])).inflate(8.0D));
		int done = 0;
		while (done < RESTORE_OPS && cursor < order.length) {
			long each = order[cursor++];
			BlockState original = undo.original(each);
			if (original == null) {
				continue;
			}
			BuildPlacer.Result result = BuildPlacer.restore(level, BlockPos.of(each), original, index, guard);
			if (result != BuildPlacer.Result.DEFERRED) {
				undo.forget(each);
				done++;
			}
		}
		restoreCursor.put(site.owner, cursor);
		if (cursor >= order.length) {
			restoreRetry.put(site.owner, ticks + RESTORE_RETRY_TICKS);
		}
	}

	@Override
	public List<SiteView> showcases() {
		List<SiteView> list = new ArrayList<>();
		for (Site site : showcases.values()) {
			list.add(site.view());
		}
		return list;
	}

	@Override
	public void hologram(ServerPlayer player, int template, int tier) {
		startPreview(player, template, SectTemplates.rotFor(player.getDirection()), Glaze.TINTA, false, Previews.Kind.HOLOGRAM, player.blockPosition(),
				Math.max(1, Math.min(9, tier)));
	}

	@Override
	public void compareAll(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", "compare");
		ListTag list = new ListTag();
		BlockPos at = player.blockPosition();
		for (int t = 1; t <= SectTemplates.COUNT; t++) {
			TemplateSpec spec = Templates.get(t);
			CompoundTag each = new CompoundTag();
			each.putInt("t", t);
			int[] box = {at.getX() + spec.x0(), at.getY() - 1 - spec.below(), at.getZ() + spec.z0(), at.getX() + spec.x1(),
					at.getY() - 1 + spec.above(), at.getZ() + spec.z1()};
			each.putIntArray("box", box);
			list.add(each);
			if (t == SectTemplates.COUNT) {
				tag.putIntArray("fp", box);
			}
		}
		tag.put("fps", list);
		tag.putIntArray("stele", new int[]{at.getX(), at.getY(), at.getZ()});
		tag.putInt("snap", 100);
		Net.send(player, new Payloads.BuildPreview(tag));
	}

	@Override
	public void camera(ServerPlayer player, int view) {
		Site site = sites.get(player.getUUID());
		if (site == null) {
			Site nearest = null;
			double best = Double.MAX_VALUE;
			for (Site each : showcases.values()) {
				double distance = each.stele.distSqr(player.blockPosition());
				if (distance < best) {
					best = distance;
					nearest = each;
				}
			}
			site = nearest;
		}
		if (site == null || server == null) {
			error(player, "no_site");
			return;
		}
		ServerLevel level = server.getLevel(site.dim);
		if (level == null) {
			return;
		}
		TemplateSpec spec = site.spec();
		SitePlan plan = site.plan();
		int which = Math.max(1, Math.min(3, view));
		// Each template names its own viewpoints (DESIGN 21.8): "cam_<n>" is the eye, "cam_<n>_look" what it looks at.
		BlockPos eye = site.anchors.get("cam_" + which);
		BlockPos look = site.anchors.get("cam_" + which + "_look");
		if (eye == null || look == null) {
			switch (which) {
				case 1 -> {
					int z = spec.z1() - 1;
					eye = plan.world(0, plan.ctx().terrain().ground(0, z) + 1, z);
					look = plan.world(0, 4, spec.z0() / 2);
				}
				case 2 -> {
					eye = plan.world(spec.x0() + 3, 3, (spec.z0() + spec.z1()) / 2);
					look = plan.world(spec.x0() / 2, 8, spec.z0() * 2 / 3);
				}
				default -> {
					eye = plan.world(spec.x1() + 12, spec.width() / 2 + 10, spec.z1() + 12);
					look = plan.world(0, 0, (spec.z0() + spec.z1()) / 2);
				}
			}
		}
		double dx = look.getX() - eye.getX();
		double dy = look.getY() - eye.getY();
		double dz = look.getZ() - eye.getZ();
		float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
		player.teleportTo(level, eye.getX() + 0.5D, eye.getY(), eye.getZ() + 0.5D, Set.<Relative>of(), yaw, pitch, false);
	}

	@Override
	public boolean reset(UUID owner) {
		Site site = sites.get(owner);
		if (site == null || server == null || !site.test) {
			return false;
		}
		if (!site.restoring) {
			beginRestore(site);
		}
		return true;
	}

	// ================================================================== helpers

	private static net.minecraft.network.chat.Component err(String key) {
		return net.minecraft.network.chat.Component.translatable("kultivator.blueprint.error." + key);
	}

	private static void error(ServerPlayer player, String key) {
		Msg.chat(player, err(key).copy().withStyle(ChatFormatting.YELLOW));
	}

	/** All live sites (the break refund, mixins and the audit read them). */
	Collection<Site> liveSites() {
		return allSites();
	}
}
