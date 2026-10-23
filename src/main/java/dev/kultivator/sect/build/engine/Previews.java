package dev.kultivator.sect.build.engine;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Costs;
import dev.kultivator.sect.build.blueprint.Emission;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainView;
import dev.kultivator.sect.build.blueprint.Templates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Footprint previews (DESIGN 21.12 "Lihat Tapak", 21.10 snapshot and validation): a ticket per request, the snapshot
 * captured chunk by chunk on the server thread (cached per stele for {@link #SNAPSHOT_KEEP} ticks, so rotating only
 * re-projects it), then compiled off the server thread from a frozen copy (zone fitting, every component emitted for the
 * boxes, ops and bill, terrain checks) and sent to the player as a {@code BuildPreview} with the keys of DESIGN 21.24. A
 * preview lives {@link #LIFETIME} ticks after it is ready.
 */
final class Previews {

	/** A preview stays this long (120 s) after it is compiled. */
	static final int LIFETIME = 2400;
	/** A captured snapshot is reused this long (5 min). */
	static final int SNAPSHOT_KEEP = 6000;
	/** Recompile while the snapshot is incomplete at most this often. */
	static final int RECOMPILE = 200;

	enum Kind {
		FOUND,
		TEST,
		SHOWCASE,
		HOLOGRAM,
		UPGRADE
	}

	static final class Job {
		final long ticket;
		final UUID owner;
		final UUID player;
		final int template;
		final int rotation;
		final Glaze glaze;
		final boolean force;
		final Kind kind;
		final ResourceKey<Level> dim;
		final BlockPos origin;
		final int showTier;
		/** "Kosongkan" was switched on: containers with items in buildings are amber, not red. */
		boolean emptyAuth;
		/**
		 * Sekte Langit (DESIGN 37.3.4, 37.4.8): a sky design. {@code origin} is the throne; {@code groundStele} the hall's
		 * stele on the ground (path A and B), {@code groundTop} the world y of the owner's ground site column top (path A,
		 * the section rule) or {@code Integer.MIN_VALUE}; {@code dx/dz} the shift; {@code minY/maxY} the level's bounds.
		 */
		boolean sky;
		@Nullable BlockPos groundStele;
		int groundTop = Integer.MIN_VALUE;
		@Nullable BoundingBox ownGround;
		int dx;
		int dz;
		int minY;
		int maxY = 319;
		/** A newer preview replaced this one (or it expired): its off-thread compile stops between components. */
		volatile boolean cancelled;
		Snapshot snapshot;
		@Nullable CompletableFuture<Compiled> compiling;
		@Nullable Compiled compiled;
		@Nullable PreviewData data;
		List<Validator.Warning> worldWarnings = List.of();
		long expires = Long.MAX_VALUE;
		long lastCompile = Long.MIN_VALUE / 2;
		int compiledPercent = -1;

		Job(long ticket, UUID owner, UUID player, int template, int rotation, Glaze glaze, boolean force, Kind kind, ResourceKey<Level> dim, BlockPos origin,
				int showTier) {
			this.ticket = ticket;
			this.owner = owner;
			this.player = player;
			this.template = template;
			this.rotation = rotation;
			this.glaze = glaze;
			this.force = force;
			this.kind = kind;
			this.dim = dim;
			this.origin = origin;
			this.showTier = showTier;
		}
	}

	/** The result of an off-thread compile. */
	record Compiled(Snapshot snapshot, Map<String, Integer> levels, CompoundTag tag, long seed) {
	}

	private final Map<Long, Job> jobs = new LinkedHashMap<>();
	private final Map<String, Snapshot> snapshots = new HashMap<>();
	private final Map<String, Long> snapshotTimes = new HashMap<>();
	private long nextTicket = 1L;

	void clear() {
		jobs.clear();
		snapshots.clear();
		snapshotTimes.clear();
	}

	long start(MinecraftServer server, UUID owner, ServerPlayer player, int template, int rotation, Glaze glaze, boolean force, Kind kind,
			ResourceKey<Level> dim, BlockPos origin, int showTier, boolean emptyAuth) {
		long ticket = nextTicket++;
		Job job = new Job(ticket, owner, player.getUUID(), template, rotation, glaze, force, kind, dim, origin.immutable(), showTier);
		job.emptyAuth = emptyAuth;
		TemplateSpec spec = Templates.get(template);
		job.sky = spec.sky();
		ServerLevel startLevel = server.getLevel(dim);
		if (startLevel != null) {
			job.minY = startLevel.getMinY();
			job.maxY = startLevel.getMaxY();
		}
		int half = Math.max(Math.max(Math.abs(spec.x0()), Math.abs(spec.x1())), Math.max(Math.abs(spec.z0()), Math.abs(spec.z1()))) + 1;
		long now = server.overworld().getGameTime();
		if (job.sky && kind != Kind.HOLOGRAM && !(kind == Kind.SHOWCASE && force)) {
			// A sky snapshot looks under islands and through the sect's own cells (never cached with ground ones).
			String key = "sky:" + dim.identifier() + "@" + origin.asLong() + "/" + half;
			Snapshot cached = snapshots.get(key);
			if (cached != null && now - snapshotTimes.getOrDefault(key, 0L) < SNAPSHOT_KEEP) {
				job.snapshot = cached;
			} else {
				job.snapshot = Snapshot.sky(origin, half);
				snapshots.put(key, job.snapshot);
				snapshotTimes.put(key, now);
			}
		} else if (kind == Kind.HOLOGRAM || kind == Kind.SHOWCASE && force) {
			// A layout hologram never waits for terrain: flat land at the origin (never cached).
			job.snapshot = flatSnapshot(origin, half);
		} else {
			String key = dim.identifier() + "@" + origin.asLong() + "/" + half;
			Snapshot cached = snapshots.get(key);
			if (cached != null && now - snapshotTimes.getOrDefault(key, 0L) < SNAPSHOT_KEEP) {
				job.snapshot = cached;
			} else {
				job.snapshot = Snapshot.of(origin, half);
				snapshots.put(key, job.snapshot);
				snapshotTimes.put(key, now);
			}
		}
		// One preview per player at a time: the replaced one stops compiling.
		jobs.values().removeIf(other -> {
			boolean replaced = other.player.equals(job.player) && other.kind != Kind.UPGRADE;
			if (replaced) {
				other.cancelled = true;
			}
			return replaced;
		});
		jobs.put(ticket, job);
		return ticket;
	}

	private static Snapshot flatSnapshot(BlockPos origin, int half) {
		Snapshot snapshot = Snapshot.of(origin, half);
		for (int i = 0; i < snapshot.ground.length; i++) {
			snapshot.ground[i] = -1;
			snapshot.top[i] = -1;
			snapshot.known.set(i);
		}
		return snapshot;
	}

	@Nullable Job job(long ticket) {
		return jobs.get(ticket);
	}

	void cancel(long ticket) {
		Job job = jobs.remove(ticket);
		if (job != null) {
			job.cancelled = true;
		}
	}

	void tick(MinecraftServer server, BuildEngine engine) {
		long now = server.overworld().getGameTime();
		if (now % 200L == 0L) {
			// Old snapshots are dropped (a preview keeps its own reference while it lives).
			snapshotTimes.entrySet().removeIf(entry -> {
				boolean old = now - entry.getValue() >= SNAPSHOT_KEEP;
				if (old) {
					snapshots.remove(entry.getKey());
				}
				return old;
			});
		}
		int budget = Snapshot.CHUNKS_PER_TICK;
		CellIndex index = CellIndex.get(server);
		for (Iterator<Job> it = jobs.values().iterator(); it.hasNext(); ) {
			Job job = it.next();
			if (now > job.expires) {
				job.cancelled = true;
				it.remove();
				continue;
			}
			ServerLevel level = server.getLevel(job.dim);
			if (level == null) {
				job.cancelled = true;
				it.remove();
				continue;
			}
			if (budget > 0 && !job.snapshot.complete()) {
				budget -= job.snapshot.capture(level, budget, index);
			}
			if (job.compiling != null) {
				if (job.compiling.isDone()) {
					try {
						job.compiled = job.compiling.join();
					} catch (RuntimeException exception) {
						Kultivator.LOGGER.error("Sect preview compile failed", exception);
					}
					job.compiling = null;
					if (job.compiled != null) {
						job.data = new PreviewData(job.ticket, job.owner, job.template, job.rotation, job.glaze, job.compiled.tag());
						job.expires = now + LIFETIME;
						// The flow relays ticketed previews with its own keys (time left, confirm state); a hologram has no ticket.
						ServerPlayer player = job.kind == Kind.HOLOGRAM ? server.getPlayerList().getPlayer(job.player) : null;
						if (player != null) {
							Net.send(player, new Payloads.BuildPreview(job.compiled.tag()));
						}
					}
				}
				continue;
			}
			int percent = percent(job);
			boolean first = job.compiledPercent < 0;
			boolean grown = percent > job.compiledPercent && (percent >= 100 || now - job.lastCompile >= RECOMPILE);
			if (first || grown) {
				job.lastCompile = now;
				job.compiledPercent = percent;
				job.worldWarnings = job.sky
						? Validator.skyWorld(level, footprint(job), kakiEstimate(job), job.origin, job.owner, engine.otherSites(job.owner, job.kind == Kind.SHOWCASE),
								job.ownGround)
						: Validator.world(level, footprint(job), engine.otherSites(job.owner, job.kind == Kind.SHOWCASE));
				Snapshot frozen = job.snapshot.freeze();
				List<Validator.Warning> world = job.worldWarnings;
				job.compiling = CompletableFuture.supplyAsync(() -> compile(job, frozen, world, percent), Util.backgroundExecutor());
			}
		}
	}

	private static int percent(Job job) {
		BoundingBox fp = footprint(job);
		return job.snapshot.percent(fp.minX(), fp.minZ(), fp.maxX(), fp.maxZ());
	}

	static BoundingBox footprint(Job job) {
		TemplateSpec spec = Templates.get(job.template);
		SitePlan plan = new SitePlan(spec, job.rotation, job.glaze, 0L, job.origin, TerrainView.flat(-1), Map.of());
		return plan.footprint();
	}

	/**
	 * The stair foot of a sky design for the world checks, before the compile fits it: the foot square around the median
	 * captured ground (6 below to 12 above), or null while none of it is captured.
	 */
	static @Nullable BoundingBox kakiEstimate(Job job) {
		TemplateSpec spec = Templates.get(job.template);
		TemplateSpec.Zone kaki = spec.kaki();
		if (kaki == null) {
			return null;
		}
		TerrainView local = job.snapshot.local(job.origin, job.rotation);
		List<Integer> grounds = new ArrayList<>();
		for (int x = Math.min(kaki.x0(), kaki.x1()); x <= Math.max(kaki.x0(), kaki.x1()); x++) {
			for (int z = Math.min(kaki.z0(), kaki.z1()); z <= Math.max(kaki.z0(), kaki.z1()); z++) {
				if (local.known(x, z)) {
					grounds.add(local.ground(x, z));
				}
			}
		}
		if (grounds.isEmpty()) {
			return null;
		}
		grounds.sort(Integer::compare);
		int ground = grounds.get(grounds.size() / 2);
		SitePlan plan = new SitePlan(spec, job.rotation, job.glaze, 0L, job.origin, TerrainView.flat(-1), Map.of());
		return plan.worldBox(new BoundingBox(Math.min(kaki.x0(), kaki.x1()), ground - 6, Math.min(kaki.z0(), kaki.z1()), Math.max(kaki.x0(), kaki.x1()),
				ground + 12, Math.max(kaki.z0(), kaki.z1())));
	}

	/**
	 * {@code cut}: one signed delta per 4×4 world cell of the footprint, row-major from (minX, minZ) with
	 * {@code (maxX - minX) / 4 + 1} cells per row; the mean of ground minus terrace level (+ cut, - fill), 0 outside zones
	 * or where nothing is known.
	 */
	static int[] cutGrid(TemplateSpec spec, SitePlan plan, TerrainView local, Map<String, Integer> levels, BoundingBox fp) {
		int width = (fp.maxX() - fp.minX()) / 4 + 1;
		int depth = (fp.maxZ() - fp.minZ()) / 4 + 1;
		int[] cells = new int[width * depth];
		BlockPos.MutableBlockPos world = new BlockPos.MutableBlockPos();
		for (int row = 0; row < depth; row++) {
			for (int col = 0; col < width; col++) {
				int sum = 0;
				int count = 0;
				for (int dx = 0; dx < 4; dx++) {
					for (int dz = 0; dz < 4; dz++) {
						world.set(fp.minX() + col * 4 + dx, 0, fp.minZ() + row * 4 + dz);
						if (!fp.isInside(world.getX(), fp.minY(), world.getZ())) {
							continue;
						}
						long at = plan.local(world);
						int x = BlockPos.getX(at);
						int z = BlockPos.getZ(at);
						TemplateSpec.Zone zone = zoneAt(spec, x, z);
						if (zone == null || !local.known(x, z)) {
							continue;
						}
						sum += local.ground(x, z) + 1 - levels.getOrDefault(zone.id(), zone.design());
						count++;
					}
				}
				cells[row * width + col] = count == 0 ? 0 : Math.round((float) sum / count);
			}
		}
		return cells;
	}

	/** The smallest zone holding a template-local column, or null. */
	private static TemplateSpec.@Nullable Zone zoneAt(TemplateSpec spec, int x, int z) {
		TemplateSpec.Zone best = null;
		long bestArea = Long.MAX_VALUE;
		for (TemplateSpec.Zone zone : spec.zones().values()) {
			int x0 = Math.min(zone.x0(), zone.x1());
			int x1 = Math.max(zone.x0(), zone.x1());
			int z0 = Math.min(zone.z0(), zone.z1());
			int z1 = Math.max(zone.z0(), zone.z1());
			if (x < x0 || x > x1 || z < z0 || z > z1) {
				continue;
			}
			long area = (long) (x1 - x0 + 1) * (z1 - z0 + 1);
			if (area < bestArea) {
				best = zone;
				bestArea = area;
			}
		}
		return best;
	}

	/** Off the server thread: fit, emit everything, check the terrain, build the payload tag. */
	private static Compiled compile(Job job, Snapshot frozen, List<Validator.Warning> worldWarnings, int percent) {
		TemplateSpec spec = Templates.get(job.template);
		TerrainView local = frozen.local(job.origin, job.rotation);
		Map<String, Integer> levels = TerraceFitter.fit(spec, local);
		long seed = job.origin.asLong() * 31L + job.template * 7919L + job.rotation;
		SitePlan plan = new SitePlan(spec, job.rotation, job.glaze, seed, job.origin, local, levels);
		CompoundTag tag = new CompoundTag();
		tag.putInt("t", job.template);
		tag.putInt("rot", job.rotation);
		tag.putString("glaze", job.glaze.id());
		tag.putLong("ticket", job.ticket);
		tag.putString("kind", job.kind.name().toLowerCase(java.util.Locale.ROOT));
		tag.putIntArray("stele", new int[]{job.origin.getX(), job.origin.getY(), job.origin.getZ()});
		BoundingBox fp = plan.footprint();
		tag.putIntArray("fp", new int[]{fp.minX(), fp.minY(), fp.minZ(), fp.maxX(), fp.maxY(), fp.maxZ()});
		BlockPos gate = plan.world(0, -1, spec.z1());
		BlockPos back = plan.world(0, -1, spec.z0());
		tag.putIntArray("axis", new int[]{gate.getX(), gate.getY(), gate.getZ(), back.getX(), back.getY(), back.getZ()});
		ListTag comps = new ListTag();
		List<BoundingBox> localBoxes = new ArrayList<>();
		// Every op's box (air included: cuts and clears touch those cells too), for the block-entity check.
		List<BoundingBox> opBoxes = new ArrayList<>();
		Bill.Builder bill = Bill.builder();
		long[] dig = new long[Bahan.COUNT];
		long ops = 0L;
		int maxTier = job.kind == Kind.HOLOGRAM || job.kind == Kind.SHOWCASE ? Math.max(1, job.showTier) : 9;
		for (Component component : spec.components()) {
			if (job.cancelled) {
				return null;
			}
			if (component.tier() > maxTier) {
				continue;
			}
			BoundingBox box = component.bounds(plan.ctx());
			if (box != null) {
				localBoxes.add(box);
				opBoxes.add(box);
			}
			Emission emission = job.kind == Kind.HOLOGRAM ? null : plan.emit(component);
			if (emission != null) {
				ops += emission.size();
				bill.add(Costs.bill(emission));
				predictDig(emission, local, dig);
				BoundingBox all = allOps(emission);
				if (all != null) {
					opBoxes.add(all);
				}
				if (emission.bounds != null) {
					box = emission.bounds;
				}
			}
			if (box == null) {
				continue;
			}
			BoundingBox world = plan.worldBox(box);
			CompoundTag each = new CompoundTag();
			each.putString("id", component.id());
			each.putString("kind", component.kind());
			each.putInt("tier", component.tier());
			each.store("name", ComponentSerialization.CODEC, net.minecraft.network.chat.Component.translatable(component.nameKey()));
			each.putIntArray("box", new int[]{world.minX(), world.minY(), world.minZ(), world.maxX(), world.maxY(), world.maxZ()});
			each.putInt("y", world.minY());
			comps.add(each);
		}
		tag.put("comps", comps);
		ListTag zones = new ListTag();
		for (TemplateSpec.Zone zone : spec.zones().values()) {
			int y = job.origin.getY() + levels.getOrDefault(zone.id(), zone.design()) - 1;
			BlockPos a = plan.world(zone.x0(), 0, zone.z0());
			BlockPos b = plan.world(zone.x1(), 0, zone.z1());
			CompoundTag each = new CompoundTag();
			each.putString("id", zone.id());
			each.putIntArray("box", new int[]{Math.min(a.getX(), b.getX()), y, Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), y,
					Math.max(a.getZ(), b.getZ())});
			each.putInt("y", y);
			zones.add(each);
		}
		tag.put("zones", zones);
		tag.putIntArray("cut", cutGrid(spec, plan, local, levels, fp));
		List<Long> obstacles = new ArrayList<>();
		for (long each : frozen.obstacles) {
			BlockPos pos = BlockPos.of(each);
			if (fp.isInside(pos) && obstacles.size() < Snapshot.MAX_OBSTACLES) {
				obstacles.add(each);
			}
		}
		tag.putLongArray("obst", obstacles.stream().mapToLong(Long::longValue).toArray());
		List<Validator.Warning> warnings = new ArrayList<>(worldWarnings);
		if (job.sky) {
			tag.putBoolean("sky", true);
			tag.putInt("yP", job.origin.getY());
			BoundingBox kakiBox = plan.kakiBox();
			if (kakiBox != null) {
				tag.putIntArray("kaki", new int[]{kakiBox.minX(), kakiBox.minY(), kakiBox.minZ(), kakiBox.maxX(), kakiBox.maxY(), kakiBox.maxZ()});
			}
			if (job.groundStele != null) {
				tag.putIntArray("ground", new int[]{job.groundStele.getX(), job.groundStele.getY(), job.groundStele.getZ()});
			}
		}
		if (job.kind != Kind.HOLOGRAM && job.sky) {
			warnings.addAll(Validator.skyTerrain(spec, local, levels, job.origin.getY(), job.minY, job.maxY, job.groundTop, job.dx, job.dz,
					dev.kultivator.sect.build.flow.LangitPlan.offsetMax(), job.force));
			List<Long> entityPos = new ArrayList<>();
			for (long each : frozen.entities) {
				entityPos.add(plan.local(BlockPos.of(each)));
			}
			warnings.addAll(Validator.blockEntities(opBoxes, entityPos, frozen.entityKinds, job.emptyAuth));
		} else if (job.kind != Kind.HOLOGRAM) {
			warnings.addAll(Validator.terrain(spec, local, levels, job.force, localBoxes));
			// Block entities by height: only those inside a component's volume count (DESIGN 21.10 table).
			List<Long> entityPos = new ArrayList<>();
			for (long each : frozen.entities) {
				entityPos.add(plan.local(BlockPos.of(each)));
			}
			warnings.addAll(Validator.blockEntities(opBoxes, entityPos, frozen.entityKinds, job.emptyAuth));
		}
		if (spec.isPlaceholder()) {
			warnings.add(Validator.warn(Validator.RED, "not_ready"));
		}
		if (!Validator.allowed(job.template)) {
			warnings.add(Validator.warn(Validator.RED, "max_template"));
		}
		if (percent < 100) {
			warnings.add(Validator.warn(Validator.NOTE, "snapshot", percent));
		}
		ListTag warn = new ListTag();
		for (Validator.Warning warning : warnings) {
			warn.add(warning.toTag());
		}
		tag.put("warn", warn);
		tag.putInt("snap", job.kind == Kind.HOLOGRAM ? 100 : percent);
		bill.build().writeTo(tag);
		CompoundTag digTag = new CompoundTag();
		for (Bahan cat : Bahan.values()) {
			if (dig[cat.ordinal()] > 0L) {
				digTag.putLong(cat.id(), dig[cat.ordinal()]);
			}
		}
		tag.put("dig", digTag);
		tag.putLong("ops", ops);
		long[] earth = TerraceFitter.earthwork(spec, local, levels);
		tag.putLongArray("earth", earth);
		return new Compiled(frozen, levels, tag, seed);
	}

	/** The template-local box of every op of an emission (air ops included), or null when it has none. */
	private static @Nullable BoundingBox allOps(Emission emission) {
		if (emission.size() == 0) {
			return null;
		}
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int y1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (long pos : emission.pos) {
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			x0 = Math.min(x0, x);
			y0 = Math.min(y0, y);
			z0 = Math.min(z0, z);
			x1 = Math.max(x1, x);
			y1 = Math.max(y1, y);
			z1 = Math.max(z1, z);
		}
		return new BoundingBox(x0, y0, z0, x1, y1, z1);
	}

	/** Predicted digging credit of an emission: cut cells within 3 of the surface give Tanah, deeper ones Batu (1 unit each). */
	static void predictDig(Emission emission, TerrainView terrain, long[] dig) {
		for (int i = 0; i < emission.size(); i++) {
			if (emission.phase(i) != Phase.CUT || !emission.states[i].isAir()) {
				continue;
			}
			long pos = emission.pos[i];
			int x = BlockPos.getX(pos);
			int y = BlockPos.getY(pos);
			int z = BlockPos.getZ(pos);
			int ground = terrain.ground(x, z);
			if (y > ground) {
				continue;
			}
			dig[ground - y < 3 ? Bahan.TANAH.ordinal() : Bahan.BATU.ordinal()] += Bahan.CENTI;
		}
	}
}
