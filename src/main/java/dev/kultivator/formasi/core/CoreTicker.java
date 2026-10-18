package dev.kultivator.formasi.core;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.fuel.Kas;
import dev.kultivator.formasi.fuel.Upkeep;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.match.ChunkReader;
import dev.kultivator.formasi.own.Limits;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.own.Slots;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.formasi.spec.Scope;
import dev.kultivator.formasi.spec.Slot;
import dev.kultivator.formasi.stack.KillLedger;
import dev.kultivator.formasi.stack.Power;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The 7c server runtime (DESIGN 24.2.12): on {@code END_SERVER_TICK} it steps the {@link Verifier}, runs up to
 * {@link #MAX_PULSES_PER_TICK} due cores (state derivation, upkeep, the handler's pulse), refreshes keepers, slots, limits
 * and flags once per second, and sends {@code FormasiSync}. It measures its own time and throttles above
 * {@code formationBudgetMs} (24.9). Nothing here ever changes a block.
 */
public final class CoreTicker {

	public static final int MAX_PULSES_PER_TICK = 4;
	/** Cores whose eye chunk is not entity-ticking sleep this long before the next look. */
	public static final int SLEEP_TICKS = 100;
	/** One owner notice per broken core per this many ticks. */
	public static final int BROKEN_NOTICE_TICKS = 1200;
	/** Kas pulls and auto-draws happen at most this often per core. */
	public static final int KAS_PULL_TICKS = 1200;
	/** Killing arrays pulse at least this slowly while the server is slow. */
	public static final int SLOW_KILL_PULSE = 40;
	/** L6 "Retak": a cracked target keeps working this long (twice with a keeper at the flag). */
	public static final int CRACK_TICKS = 12_000;
	/** Slot markers per check. */
	public static final int MAX_MARKS = 24;
	/** The average of the last this many ticks drives the throttle. */
	public static final int BUDGET_WINDOW = 200;

	private static @Nullable MinecraftServer server;
	private static final Map<ResourceKey<Level>, CoreStore> STORES = new HashMap<>();
	private static final Map<UUID, Integer> PRETEND_HALL = new HashMap<>();
	private static long tick;
	private static int rotate;
	private static final long[] TIMES = new long[BUDGET_WINDOW];
	private static int timeIndex;
	private static int throttle = 1;
	private static boolean slow;
	private static int pulsesLast;
	private static int pulsesWindow;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> clearSession());
		ServerTickEvents.END_SERVER_TICK.register(CoreTicker::tick);
	}

	private static void clearSession() {
		server = null;
		STORES.clear();
		PRETEND_HALL.clear();
		Stacking.invalidate();
		KillLedger.clear();
		IntruderSnapshot.clear();
		CoreContextImpl.clear();
		SyncSender.clear();
		Verifier.clear();
		tick = 0L;
	}

	// ================================================================== access

	public static @Nullable MinecraftServer server() {
		return server;
	}

	/** Sets the server when a hook runs before {@code SERVER_STARTED} finished (integrated server start-up). */
	public static void ensureServer(MinecraftServer running) {
		if (server == null) {
			server = running;
		}
	}

	public static CoreStore store(ServerLevel level) {
		ensureServer(level.getServer());
		CoreStore store = STORES.get(level.dimension());
		if (store == null) {
			store = CoreStore.get(level);
			STORES.put(level.dimension(), store);
		}
		return store;
	}

	/** The stores of every loaded dimension. */
	public static List<CoreStore> stores() {
		List<CoreStore> list = new ArrayList<>();
		if (server != null) {
			for (ServerLevel level : server.getAllLevels()) {
				list.add(store(level));
			}
		}
		return list;
	}

	public static @Nullable ServerLevel level(ResourceKey<Level> dim) {
		return server == null || dim == null ? null : server.getLevel(dim);
	}

	public static @Nullable CoreRecord record(ResourceKey<Level> dim, int id) {
		ServerLevel level = level(dim);
		return level == null ? null : store(level).byId(id);
	}

	public static void markDirty(ServerLevel level) {
		store(level).setDirty();
	}

	public static long now() {
		return server == null ? 0L : server.overworld().getGameTime();
	}

	/** The owner's hall level, or the level pretended by {@code uji formasi aula}. */
	public static int hallLevel(UUID owner) {
		Integer pretend = PRETEND_HALL.get(owner);
		return pretend != null ? pretend : Halls.level(owner);
	}

	public static void pretendHall(UUID owner, int level) {
		if (level <= 0) {
			PRETEND_HALL.remove(owner);
		} else {
			PRETEND_HALL.put(owner, Math.min(9, level));
		}
	}

	/** The hall level a record runs at: personal cores count as level 3. */
	public static int levelFor(CoreRecord record) {
		if (record.kind == Ownership.KIND_PERSONAL) {
			return Limits.PERSONAL_HALL_LEVEL;
		}
		int level = hallLevel(record.owner);
		return record.virtual && record.forced ? Math.max(9, level) : level;
	}

	/** The effective grade number of the Daya Tembus rule: the grade, +1 with a Murid Dalam+ keeper at its post. */
	public static int effectiveGrade(CoreView view) {
		FormationSpec spec = FormationRegistry.byId(view.spec());
		CoreRecord record = record(view.dim(), view.id());
		int grade = spec == null ? 1 : spec.grade().number();
		return grade + (record != null && record.keeperDalam && record.keepersAtPost > 0 ? 1 : 0);
	}

	public static CoreView view(CoreRecord record) {
		return new CoreView(record.id, record.spec, record.dim, record.eye, record.rot, record.owner, record.kind, record.state, record.reason,
				record.idle, record.cracked, record.daya, record.keepersAtPost, record.power, record.virtual, record.ext.copy());
	}

	/** The vertical reach of a core field: the spec's {@code dy}, else half the radius. */
	public static double dyLimit(FormationSpec spec, CoreRecord record) {
		double dy = spec.params().get("dy");
		return dy > 0.0D ? dy : Math.max(2.0D, record.radius / 2.0D);
	}

	/** True when {@code pos} lies inside the record's field. */
	public static boolean covers(ServerLevel level, CoreRecord record, FormationSpec spec, Vec3 pos) {
		if (!level.dimension().equals(record.dim)) {
			return false;
		}
		Vec3 centre = Vec3.atCenterOf(record.eye);
		if (spec.scope() == Scope.TERRITORY && !record.virtual) {
			return Halls.inTerritory(record.owner, level, pos);
		}
		double dx = pos.x - centre.x;
		double dz = pos.z - centre.z;
		if (dx * dx + dz * dz > record.radius * record.radius) {
			return false;
		}
		double dy = spec.scope() == Scope.TERRITORY ? Halls.TERRITORY_HEIGHT : dyLimit(spec, record);
		return Math.abs(pos.y - centre.y) <= dy;
	}

	// ================================================================== the tick

	private static void tick(MinecraftServer running) {
		server = running;
		long start = System.nanoTime();
		tick++;
		long now = running.overworld().getGameTime();
		try {
			Verifier.step(running, KultivatorConfig.get().formationReadsPerTick);
			if (tick % 20L == 0L) {
				Keepers.refresh(running);
				allocate(running);
				expireVirtual(running, now);
				CoreContextImpl.housekeeping(now);
				Stacking.prune(now);
				KillLedger.prune(now);
				IntruderSnapshot.prune(now);
				updateThrottle(running);
			}
			pulses(running, now);
			SyncSender.tick(running, tick);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Formasi Sekte tick failed", exception);
		}
		TIMES[timeIndex] = System.nanoTime() - start;
		timeIndex = (timeIndex + 1) % BUDGET_WINDOW;
	}

	/** Average added server time per tick over the window, in milliseconds. */
	public static double averageMs() {
		long sum = 0L;
		for (long time : TIMES) {
			sum += time;
		}
		return sum / (double) BUDGET_WINDOW / 1_000_000.0D;
	}

	public static int throttle() {
		return throttle;
	}

	public static boolean slow() {
		return slow;
	}

	/** Pulses run in the last second (stats). */
	public static int pulsesPerSecond() {
		return pulsesLast;
	}

	private static void updateThrottle(MinecraftServer running) {
		double budget = KultivatorConfig.get().formationBudgetMs;
		double average = averageMs();
		throttle = average > budget ? 2 : 1;
		double mspt = running.getAverageTickTimeNanos() / 1_000_000.0D;
		slow = average > 2.0D * budget || mspt > KultivatorConfig.get().formationMsptSlow;
		pulsesLast = pulsesWindow;
		pulsesWindow = 0;
	}

	private static void pulses(MinecraftServer running, long now) {
		List<CoreStore> stores = stores();
		int done = 0;
		int offset = rotate++;
		for (CoreStore store : stores) {
			if (store.isEmpty() || store.dim() == null) {
				continue;
			}
			ServerLevel level = running.getLevel(store.dim());
			if (level == null) {
				continue;
			}
			List<CoreRecord> records = store.list();
			int size = records.size();
			for (int i = 0; i < size && done < MAX_PULSES_PER_TICK; i++) {
				CoreRecord record = records.get(Math.floorMod(i + offset, size));
				FormationSpec spec = FormationRegistry.byId(record.spec);
				if (record.nextPulse == 0L) {
					int period = spec == null ? 20 : spec.pulseTicks();
					record.nextPulse = now + Math.floorMod(record.id * 7, period);
					record.lastPaid = now;
				}
				if (now < record.nextPulse) {
					continue;
				}
				done++;
				pulsesWindow++;
				process(level, store, record, spec, now);
			}
		}
	}

	private static void process(ServerLevel level, CoreStore store, CoreRecord record, @Nullable FormationSpec spec, long now) {
		if (!record.virtual && !level.isPositionEntityTicking(record.eye)) {
			record.nextPulse = now + SLEEP_TICKS;
			record.lastPaid = now;
			return;
		}
		refreshState(level, record);
		if (spec == null) {
			record.nextPulse = now + SLEEP_TICKS;
			return;
		}
		pay(level, store, record, spec, now);
		FormationEffect effect = FormationRegistry.effectOf(spec);
		if (record.state.running() && effect != null) {
			CoreContextImpl ctx = new CoreContextImpl(level, record, spec, null);
			try {
				effect.pulse(ctx);
				record.idle = alwaysDrains(spec) ? false : effect.idle(ctx);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Formation {} pulse failed", spec.id(), exception);
			}
		} else {
			record.idle = false;
		}
		if (!record.virtual && record.draw && (record.state == CoreState.ACTIVE || record.state.running())
				&& now - record.lastKasPull >= KAS_PULL_TICKS) {
			double hours = record.daya / Math.max(1.0E-6D, Upkeep.hoursCenti(spec, 1.0D));
			if (hours < Upkeep.DRAW_BELOW_HOURS) {
				record.lastKasPull = now;
				if (draw(record, spec, Upkeep.DRAW_HOURS) > 0L) {
					store.setDirty();
					refreshState(level, record);
				}
			}
		}
		int period = spec.pulseTicks() * throttle;
		if (spec.family() == Family.PEMBUNUH && slow) {
			period = Math.max(period, SLOW_KILL_PULSE);
		}
		record.nextPulse = now + period;
	}

	/** Alarm and concealment families always drain fully (24.2.7). */
	private static boolean alwaysDrains(FormationSpec spec) {
		return spec.family() == Family.PERINGATAN || spec.family() == Family.PENYEMBUNYI;
	}

	private static void pay(ServerLevel level, CoreStore store, CoreRecord record, FormationSpec spec, long now) {
		if (record.lastPaid < 0L) {
			record.lastPaid = now;
		}
		long elapsed = Math.max(0L, now - record.lastPaid);
		record.lastPaid = now;
		record.vein = Upkeep.vein(level, record.eye);
		record.rate = Upkeep.centiPerTick(spec, record.vein, record.keeperUpkeep, record.idle, record.flaggedBy >= 0);
		if (!record.state.running() || record.virtual || elapsed == 0L || record.rate <= 0.0D) {
			return;
		}
		double cost = record.rate * elapsed + record.carry;
		long whole = (long) Math.floor(cost);
		record.carry = cost - whole;
		record.daya = Math.max(0L, record.daya - whole);
		if (record.daya == 0L) {
			record.best = -1;
			record.carry = 0.0D;
		}
		store.setDirty();
	}

	/**
	 * Draws up to {@code hours} of upkeep from the Kas into the core (STONE_ONLY cores only from the stone pool; others the
	 * cheaper amethyst pool first). Returns the centi-Daya moved.
	 */
	public static long draw(CoreRecord record, FormationSpec spec, double hours) {
		long room = Upkeep.capCenti(spec) - record.daya;
		long want = Math.min(room, Upkeep.hoursCenti(spec, hours));
		if (want <= 0L) {
			return 0L;
		}
		long got = 0L;
		if (!spec.has(Flag.STONE_ONLY)) {
			got += Kas.take(record.owner, want, false);
		}
		if (got < want) {
			int best = Kas.bestGrade(record.owner);
			long stone = Kas.take(record.owner, want - got, true);
			if (stone > 0L) {
				record.best = Math.max(record.best, best);
			}
			got += stone;
		}
		record.daya += got;
		return got;
	}

	// ================================================================== state

	/** Derives the record's state now and handles a change (event re-reads and toggles call this directly). */
	public static void refreshState(ServerLevel level, CoreRecord record) {
		CoreState old = record.state;
		FormationSpec spec = FormationRegistry.byId(record.spec);
		derive(level, record, spec);
		if (old != record.state) {
			onStateChanged(level, record, spec, old);
		}
	}

	private static void derive(ServerLevel level, CoreRecord record, @Nullable FormationSpec spec) {
		long now = level.getGameTime();
		record.cracked = false;
		if (spec == null) {
			record.state = record.on ? CoreState.SUSPENDED : CoreState.DORMANT;
			record.reason = Component.translatable("kultivator.formasi.reason.unknown_spec", record.spec);
			return;
		}
		int hallLevel = levelFor(record);
		Halls.HallView hall = Halls.hallOf(record.owner);
		double hallRadius = hall == null ? 0.0D : hall.radius();
		if (spec.scope() == Scope.TERRITORY) {
			record.radius = record.virtual || hall == null ? Math.max(32.0D, spec.radiusAt(Math.max(1, hallLevel))) : hallRadius;
		} else {
			record.radius = Power.radius(spec, record, Math.max(1, hallLevel), hallRadius);
		}
		if (!record.on) {
			record.state = CoreState.DORMANT;
			record.reason = null;
			record.crackSince = -1L;
			return;
		}
		if (record.missing > 0 && !record.virtual) {
			if (record.missing == 1 && record.flaggedBy >= 0) {
				if (record.crackSince < 0L) {
					record.crackSince = now;
				}
				record.cracked = now - record.crackSince <= (long) CRACK_TICKS * (record.flagKeeper ? 2 : 1);
			}
			if (!record.cracked) {
				record.state = CoreState.BROKEN;
				record.reason = Component.translatable("kultivator.formasi.reason.broken", record.missing);
				return;
			}
		} else {
			record.crackSince = -1L;
		}
		Component blocked = blocker(level, record, spec, hallLevel, hall);
		record.power = Power.of(spec, record, view(record));
		if (blocked != null) {
			record.state = CoreState.SUSPENDED;
			record.reason = blocked;
			return;
		}
		if (record.daya <= 0L && !record.zeroCost && !record.virtual) {
			record.state = CoreState.ACTIVE;
			record.reason = Component.translatable("kultivator.formasi.reason.unpowered");
			return;
		}
		record.state = record.keepersAtPost > 0 ? CoreState.KEPT : CoreState.POWERED;
		record.reason = record.cracked ? Component.translatable("kultivator.formasi.reason.cracked") : null;
	}

	private static @Nullable Component blocker(ServerLevel level, CoreRecord record, FormationSpec spec, int hallLevel,
			@Nullable Halls.HallView hall) {
		if (!KultivatorConfig.get().sectFormations) {
			return Component.translatable("kultivator.formasi.reason.config");
		}
		if (!record.virtual) {
			if (record.kind == Ownership.KIND_SECT) {
				if (hall == null || !hall.active()) {
					return Component.translatable("kultivator.formasi.reason.hall_dormant");
				}
				if (!Halls.inTerritory(record.owner, level, Vec3.atCenterOf(record.eye))) {
					return Component.translatable("kultivator.formasi.reason.outside");
				}
			}
			if (record.limitReason != null) {
				return record.limitReason;
			}
			if (record.slotReason != null) {
				return record.slotReason;
			}
		}
		if (!(record.virtual && record.forced) && hallLevel < spec.minHallLevel()) {
			return Component.translatable("kultivator.formasi.reason.hall_level", spec.minHallLevel());
		}
		for (String requirement : spec.requires()) {
			if (!requirementMet(level, record, requirement)) {
				FormationSpec needed = FormationRegistry.byId(requirement);
				return Component.translatable("kultivator.formasi.reason.requires", needed == null ? Component.literal(requirement) : needed.name());
			}
		}
		FormationEffect effect = FormationRegistry.effectOf(spec);
		if (effect != null) {
			try {
				return effect.blocker(new CoreContextImpl(level, record, spec, null));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Formation {} blocker failed", spec.id(), exception);
			}
		}
		return null;
	}

	private static boolean requirementMet(ServerLevel level, CoreRecord record, String requirement) {
		boolean family = requirement.startsWith("family:");
		String name = family ? requirement.substring(7) : requirement;
		for (CoreRecord other : store(level).all()) {
			if (other == record || !other.owner.equals(record.owner) || !other.state.running()) {
				continue;
			}
			FormationSpec spec = FormationRegistry.byId(other.spec);
			if (spec != null && (family ? spec.family().id().equals(name) : spec.id().equals(name))) {
				return true;
			}
		}
		return false;
	}

	private static void onStateChanged(ServerLevel level, CoreRecord record, @Nullable FormationSpec spec, CoreState old) {
		Stacking.invalidate();
		Fx.send(level, Vec3.atCenterOf(record.eye), FxType.FORMASI, FormasiFxIds.STATE, record.state.ordinal(), (float) record.radius);
		if (spec == null) {
			return;
		}
		FormationEffect effect = FormationRegistry.effectOf(spec);
		if (effect != null) {
			try {
				effect.onState(new CoreContextImpl(level, record, spec, null), old, record.state);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Formation {} state hook failed", spec.id(), exception);
			}
		}
		if (record.virtual) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(record.owner);
		long now = level.getGameTime();
		if (record.state == CoreState.BROKEN && now - record.lastBrokenNotice >= BROKEN_NOTICE_TICKS) {
			record.lastBrokenNotice = now;
			OwnerStore.get(level.getServer()).log(record.owner, now / 24000L, "kultivator.formasi.log.broken", spec.id(),
					Integer.toString(record.missing));
			if (owner != null) {
				Msg.chat(owner, Component.translatable("kultivator.formasi.broken", spec.name(), record.missing).withStyle(ChatFormatting.RED));
				if (owner.level() == level) {
					markBad(owner, record, spec);
				}
			}
		}
		if (owner != null && record.state.running()) {
			if (spec.grade().ordinal() >= Grade.BUMI.ordinal()) {
				Advancements.grant(owner, "stone_formation");
			}
			noteFamilies(owner);
		}
	}

	/** Marks the bad slots of a registered core for one player (red cross wrong, gold cube missing). */
	public static void markBad(ServerPlayer player, CoreRecord record, FormationSpec spec) {
		List<Slot> slots = spec.pattern().slots(record.rot);
		int sent = 0;
		for (int i = 0; i < slots.size() && i < record.slotState.length && sent < MAX_MARKS; i++) {
			byte state = record.slotState[i];
			if (state == ChunkReader.MISSING || state == ChunkReader.WRONG) {
				Slot slot = slots.get(i);
				Vec3 at = Vec3.atCenterOf(record.eye.offset(slot.dx(), slot.dy(), slot.dz()));
				Fx.sendTo(player, at, at, FxType.FORMASI, FormasiFxIds.SLOT_MARK, state == ChunkReader.WRONG ? 0 : 1, 30.0F);
				sent++;
			}
		}
	}

	/** Five families running at once, and the families seen, for the advancements (24.11). */
	private static void noteFamilies(ServerPlayer owner) {
		Set<Family> running = new HashSet<>();
		for (CoreView view : Cores.of(owner.getUUID())) {
			FormationSpec spec = FormationRegistry.byId(view.spec());
			if (spec != null && view.state().running() && !view.virtual()) {
				running.add(spec.family());
			}
		}
		CultivationData data = Cultivators.get(owner);
		CompoundTag section = data.extra().getCompound("formasi").orElse(null);
		if (section == null) {
			section = new CompoundTag();
			data.extra().put("formasi", section);
		}
		int bits = section.getIntOr("fam_seen", 0);
		for (Family family : running) {
			bits |= 1 << family.ordinal();
		}
		section.putInt("fam_seen", bits);
		Cultivators.dirty(owner);
		if (running.size() >= 5) {
			Advancements.grant(owner, "five_families");
		}
	}

	// ================================================================== once per second

	/** Limits, active slots and Formasi Panji Larangan targets of every owner (24.2.4, 24.4.6). */
	private static void allocate(MinecraftServer running) {
		Map<UUID, List<CoreRecord>> byOwner = new LinkedHashMap<>();
		for (CoreStore store : stores()) {
			for (CoreRecord record : store.all()) {
				if (!record.virtual) {
					byOwner.computeIfAbsent(record.owner, key -> new ArrayList<>()).add(record);
				}
			}
		}
		for (Map.Entry<UUID, List<CoreRecord>> entry : byOwner.entrySet()) {
			UUID owner = entry.getKey();
			List<CoreRecord> records = entry.getValue();
			records.sort(Comparator.comparingInt(record -> record.id));
			int hallLevel = hallLevel(owner);
			int registered = 0;
			int personal = 0;
			Map<String, Integer> groups = new HashMap<>();
			for (CoreRecord record : records) {
				FormationSpec spec = FormationRegistry.byId(record.spec);
				record.limitReason = null;
				if (spec == null) {
					continue;
				}
				if (record.kind == Ownership.KIND_PERSONAL) {
					personal++;
					if (personal > Limits.PERSONAL_MAX) {
						record.limitReason = Component.translatable("kultivator.formasi.reason.limit_personal", Limits.PERSONAL_MAX);
					}
					continue;
				}
				if (!Limits.transport(spec)) {
					registered++;
					if (registered > Limits.registered(hallLevel)) {
						record.limitReason = Component.translatable("kultivator.formasi.reason.limit_total", Limits.registered(hallLevel));
						continue;
					}
				}
				int count = groups.merge(Limits.group(spec), 1, Integer::sum);
				if (count > Limits.groupMax(spec, hallLevel)) {
					record.limitReason = Component.translatable("kultivator.formasi.reason.limit_group", Limits.groupMax(spec, hallLevel));
				}
			}
			int max = Slots.active(hallLevel);
			Set<String> units = new HashSet<>();
			List<CoreRecord> waiting = new ArrayList<>();
			for (CoreRecord record : records) {
				FormationSpec spec = FormationRegistry.byId(record.spec);
				record.slotReason = null;
				if (spec == null || record.kind != Ownership.KIND_SECT || !record.on || !record.complete() || record.limitReason != null
						|| hallLevel < spec.minHallLevel()) {
					continue;
				}
				ServerLevel level = running.getLevel(record.dim);
				if (level == null || !Halls.inTerritory(owner, level, Vec3.atCenterOf(record.eye))) {
					continue;
				}
				String unit = Limits.transport(spec) ? "t:" + spec.grade().name() : "c:" + record.id;
				if (units.contains(unit) || units.size() < max) {
					units.add(unit);
				} else {
					waiting.add(record);
				}
			}
			for (CoreRecord record : waiting) {
				record.slotReason = Component.translatable("kultivator.formasi.reason.slots", units.size(), max);
			}
			assignFlags(records);
		}
	}

	private static void assignFlags(List<CoreRecord> records) {
		for (CoreRecord record : records) {
			record.flaggedBy = -1;
			record.flagTarget = -1;
			record.flagKeeper = false;
		}
		for (CoreRecord flag : records) {
			if (!"restriction_flag".equals(flag.spec) || !flag.state.running()) {
				continue;
			}
			CoreRecord best = null;
			double bestDistance = Double.MAX_VALUE;
			for (CoreRecord target : records) {
				FormationSpec spec = FormationRegistry.byId(target.spec);
				if (target == flag || spec == null || !target.dim.equals(flag.dim) || target.flaggedBy >= 0 || spec.grade() == Grade.ABADI
						|| "restriction_flag".equals(target.spec)) {
					continue;
				}
				double distance = target.eye.distSqr(flag.eye);
				if (distance <= 64.0D && distance < bestDistance) {
					best = target;
					bestDistance = distance;
				}
			}
			if (best != null) {
				best.flaggedBy = flag.id;
				best.flagKeeper = flag.keepersAtPost > 0;
				flag.flagTarget = best.id;
			}
		}
	}

	private static void expireVirtual(MinecraftServer running, long now) {
		for (CoreStore store : stores()) {
			for (CoreRecord record : store.list()) {
				if (record.virtual && now >= record.expires) {
					store.remove(record.id);
				}
			}
		}
	}

	// ================================================================== removal

	/**
	 * The eye at {@code pos} is gone: the record is removed, its Daya goes to the owner's Kas, its keepers walk free, and
	 * the owner is told. The eye block itself drops through its vanilla loot table.
	 */
	public static void eyeGone(ServerLevel level, BlockPos pos) {
		CoreStore store = store(level);
		CoreRecord record = store.atEye(pos);
		if (record == null) {
			store.setTarget(pos, null);
			return;
		}
		forget(level, store, record, true);
	}

	/** Forgets a real record (blocks untouched, Daya to the Kas, keepers freed). */
	public static void forget(ServerLevel level, CoreStore store, CoreRecord record, boolean tell) {
		FormationSpec spec = FormationRegistry.byId(record.spec);
		if (!record.virtual && record.daya > 0L) {
			Kas.add(record.owner, record.daya, record.best >= 0 ? record.best : -1);
		}
		for (DiscipleEntity disciple : Disciples.loaded(record.owner)) {
			if (record.hasKeeper(disciple.soulId())) {
				disciple.data().extra().remove(Keepers.EXTRA);
				if (disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
					disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			}
		}
		store.remove(record.id);
		Stacking.invalidate();
		if (tell && !record.virtual) {
			Component name = spec == null ? Component.literal(record.spec) : spec.name();
			OwnerStore.get(level.getServer()).log(record.owner, level.getGameTime() / 24000L, "kultivator.formasi.log.removed", record.spec,
					Long.toString(record.daya / 100L));
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(record.owner);
			if (owner != null) {
				Msg.chat(owner, Component.translatable("kultivator.formasi.removed", name, record.daya / 100L).withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	private CoreTicker() {
	}
}
