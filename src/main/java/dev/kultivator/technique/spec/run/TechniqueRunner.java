package dev.kultivator.technique.spec.run;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drives running casts (DESIGN 22.2.6): the cast pipeline with every refusal of step 1 in order, the snapshot, the target
 * precheck, spending, the start, the per-tick step scheduling, END and the abort on death, logout or a dimension change.
 * Also the session state of 22.18.4: global cooldown, ultimate lockout, charges, recast stacks, recent casts (echo wheel)
 * and the {@code uji teknik bebas} bypass. Zero cost while idle.
 */
public final class TechniqueRunner {

	private static final List<Caster> ACTIVE = new ArrayList<>();
	private static final Map<UUID, LivingEntity> KNOWN = new HashMap<>();
	private static final Map<UUID, Long> GLOBAL = new HashMap<>();
	private static final Map<UUID, Long> LOCKOUT = new HashMap<>();
	private static final Map<UUID, Map<String, List<Long>>> CHARGES = new HashMap<>();
	private static final Map<UUID, Map<String, long[]>> STACKS = new HashMap<>();
	private static final Map<UUID, Deque<long[]>> RECENT = new HashMap<>();
	private static final Map<UUID, String> FORBIDDEN_CONFIRM = new HashMap<>();
	private static final Set<UUID> BYPASS = new HashSet<>();
	private static boolean lowLoad;
	private static long serverTick;
	private static final int RECENT_MAX = 16;
	/** A FORBIDDEN spec asks once per session: cast again within this many ticks to confirm. */
	private static final int CONFIRM_TICKS = 200;

	/** Registers the runner and every subsystem (called from {@code Techniques.init}; the registry is read lazily). */
	public static void init() {
		StatusTracker.init();
		Boons.init();
		QiShield.init();
		Forms.init();
		Fields.init();
		Phantoms.init();
		VirtualProjectiles.init();
		Flows.init();
		Dummies.init();
		Homes.init();
		FeatTracker.init();
		TechniqueTests.init();
		ServerTickEvents.END_SERVER_TICK.register(TechniqueRunner::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer || entity instanceof DiscipleEntity) {
				abort(entity, EndReason.DEATH);
				Boons.forget(entity);
				QiShield.forget(entity);
				StatusTracker.forgetSource(entity);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			abort(player, EndReason.LOGOUT);
			Boons.forget(player);
			QiShield.forget(player);
			StatusTracker.forgetSource(player);
			UUID id = player.getUUID();
			GLOBAL.remove(id);
			LOCKOUT.remove(id);
			CHARGES.remove(id);
			STACKS.remove(id);
			RECENT.remove(id);
			FORBIDDEN_CONFIRM.remove(id);
			BYPASS.remove(id);
			KNOWN.remove(id);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> abort(player, EndReason.DIMENSION));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			KNOWN.clear();
			GLOBAL.clear();
			LOCKOUT.clear();
			CHARGES.clear();
			STACKS.clear();
			RECENT.clear();
			FORBIDDEN_CONFIRM.clear();
			BYPASS.clear();
			StatusTracker.clearAll();
			Destruction.clearAll();
		});
	}

	// ================================================================== contract (22.18.3)

	/** Starts a cast whose costs were already paid. */
	public static void start(CastContext ctx) {
		if (!(ctx instanceof Caster cast)) {
			return;
		}
		register(cast);
		if (cast.spec.special() != null && !cast.spec.isLegacy() && !cast.castFxSent && cast.spec.charge() == null) {
			// a special's handler already ran: its CAST goes out now (at the caster), its timeline steps then send their own phases
			cast.castFx(cast.caster.position(), cast.aim, 0.0F, 0.0F, 1, 0);
		}
		if (cast.spec.isLegacy()) {
			Flows.beginFlags(cast);
		} else {
			Flows.begin(cast);
		}
		if (!cast.castFxSent && !cast.charging && cast.spec.timeline().isEmpty() && !cast.spec.isLegacy()) {
			cast.castFx(cast.caster.position(), cast.aim, 0.0F, 0.0F, 1, 0);
		}
		if (cast.parent == null && !cast.spec.isLegacy()) {
			if (cast.echoBonus != null && cast.spec.echoAllowed()) {
				Caster echo = cast.child(cast.spec, cast.echoBonus.value(), true);
				cast.schedule(Math.max(1, cast.echoBonus.ticks()), () -> startChild(echo));
				cast.activity(1);
				cast.schedule(Math.max(1, cast.echoBonus.ticks()) + 1, () -> cast.activity(-1));
			}
			if (cast.extraCast != null) {
				for (int i = 1; i <= cast.extraCast.count(); i++) {
					Caster copy = cast.child(cast.spec, cast.extraCast.value(), false);
					int delay = Math.max(1, cast.extraCast.ticks()) * i;
					cast.activity(1);
					cast.schedule(delay, () -> {
						startChild(copy);
						cast.activity(-1);
					});
				}
			}
		}
	}

	/** Ends every cast of {@code caster} at once: fields, phantoms, projectiles and forms. */
	public static void abort(Entity caster, EndReason reason) {
		if (ACTIVE.isEmpty()) {
			if (caster instanceof LivingEntity living) {
				Forms.end(living, reason);
			}
			return;
		}
		for (Caster cast : List.copyOf(ACTIVE)) {
			if (cast.caster == caster) {
				cast.end(reason);
			}
		}
		if (caster instanceof LivingEntity living) {
			Forms.end(living, reason);
		}
	}

	/** Active casts of {@code caster} (children excluded): ≤ 6 per player, ≤ 3 per disciple. */
	public static int active(Entity caster) {
		int count = 0;
		for (Caster cast : ACTIVE) {
			if (cast.caster == caster && cast.parent == null && !cast.ended) {
				count++;
			}
		}
		return count;
	}

	// ================================================================== registry of running casts

	static void register(Caster cast) {
		if (!ACTIVE.contains(cast)) {
			ACTIVE.add(cast);
		}
		KNOWN.put(cast.caster.getUUID(), cast.caster);
		int max = KultivatorConfig.get().techniqueMaxActiveCasts;
		if (ACTIVE.size() > max) {
			for (Caster old : List.copyOf(ACTIVE)) {
				if (ACTIVE.size() <= max) {
					break;
				}
				if (old != cast) {
					old.end(EndReason.OVERLOAD);
				}
			}
		}
	}

	/** Starts an echo, extra cast or replay (shares the parent's ledger; no costs, no mastery, no Kehancuran). */
	static void startChild(Caster child) {
		if (child.parent != null && (child.parent.aborted || !child.parent.casterValid())) {
			return;
		}
		child.kehancuran = false;
		register(child);
		if (child.spec.isLegacy()) {
			LegacyArt.cast(child);
		} else if (child.spec.special() == null) {
			Flows.begin(child);
		}
	}

	static void forgetCast(Caster cast) {
		ACTIVE.remove(cast);
	}

	/**
	 * True while {@code entity} charges or channels a technique (DESIGN 27.6): disciples stand still and face their foe
	 * meanwhile, so the cast pose reads.
	 */
	public static boolean holding(Entity entity) {
		for (Caster cast : ACTIVE) {
			if (cast.caster == entity && !cast.ended && (cast.charging || cast.channelUntil > cast.level.getGameTime())) {
				return true;
			}
		}
		return false;
	}

	static boolean hasActive(Entity entity) {
		for (Caster cast : ACTIVE) {
			if (cast.caster == entity) {
				return true;
			}
		}
		return false;
	}

	static List<Caster> activeOf(Entity entity) {
		List<Caster> list = new ArrayList<>();
		for (Caster cast : ACTIVE) {
			if (cast.caster == entity && !cast.ended) {
				list.add(cast);
			}
		}
		return list;
	}

	/** The running (non-child) cast of {@code spec} by {@code caster}, or null. */
	static @Nullable Caster find(Entity caster, String specId) {
		for (Caster cast : ACTIVE) {
			if (cast.caster == caster && cast.parent == null && !cast.ended && cast.spec.id().equals(specId)) {
				return cast;
			}
		}
		return null;
	}

	/** A live caster entity by UUID (online players, disciples that cast this session). */
	static @Nullable LivingEntity living(@Nullable UUID id) {
		if (id == null) {
			return null;
		}
		LivingEntity known = KNOWN.get(id);
		if (known instanceof ServerPlayer player) {
			MinecraftServer server = player.level().getServer();
			ServerPlayer now = server.getPlayerList().getPlayer(id);
			if (now != null) {
				if (now != known) {
					KNOWN.put(id, now);
				}
				return now;
			}
			KNOWN.remove(id);
			return null;
		}
		if (known != null && (known.isRemoved() || !known.isAlive())) {
			KNOWN.remove(id);
			return null;
		}
		return known;
	}

	/**
	 * The live entity of a boon or shield holder (casters and allies alike): an online player's current entity (a respawn
	 * or the End exit makes a new one), or the entity itself while it is alive and not removed; null once it is gone.
	 */
	static @Nullable LivingEntity current(@Nullable LivingEntity holder) {
		if (holder == null) {
			return null;
		}
		if (holder instanceof ServerPlayer player) {
			ServerPlayer now = player.level().getServer().getPlayerList().getPlayer(player.getUUID());
			return now != null && now.isAlive() ? now : null;
		}
		return holder.isRemoved() || !holder.isAlive() ? null : holder;
	}

	/** Remembers a caster entity (boons and shields find it by UUID). */
	static void know(LivingEntity entity) {
		KNOWN.put(entity.getUUID(), entity);
	}

	static boolean lowLoad() {
		return lowLoad;
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		serverTick++;
		lowLoad = server.getAverageTickTimeNanos() > KultivatorConfig.get().kehancuranMsptPause * 1_000_000L;
		if (!ACTIVE.isEmpty()) {
			for (Caster cast : List.copyOf(ACTIVE)) {
				boolean alive;
				try {
					alive = cast.tick();
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("Kultivator: technique {} failed and was ended", cast.spec.id(), exception);
					cast.end(EndReason.CANCELLED);
					alive = false;
				}
				if (!alive) {
					ACTIVE.remove(cast);
				}
			}
		}
		long now = serverTick;
		guarded("trackers", () -> StatusTracker.tick(now));
		guarded("boons", () -> Boons.tick(now));
		guarded("shields", QiShield::tick);
		guarded("forms", () -> Forms.tick(now));
		guarded("fields", () -> Fields.tick(now));
		guarded("phantoms", Phantoms::tick);
		guarded("projectiles", VirtualProjectiles::tick);
		guarded("kehancuran", Destruction::flushAll);
		if (serverTick % 200 == 0) {
			KNOWN.values().removeIf(entity -> entity.isRemoved() && !(entity instanceof ServerPlayer));
		}
	}

	/** Runs one subsystem's tick; a payload that throws is logged instead of escaping END_SERVER_TICK. */
	private static void guarded(String what, Runnable tick) {
		try {
			tick.run();
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: technique {} tick failed", what, exception);
		}
	}

	// ================================================================== the cast pipeline (22.2.6)

	/** {@code uji teknik bebas}: gates, qi and cooldowns are skipped for normal casting this session. */
	public static boolean bypass(ServerPlayer player) {
		return BYPASS.contains(player.getUUID());
	}

	public static void setBypass(ServerPlayer player, boolean on) {
		if (on) {
			BYPASS.add(player.getUUID());
		} else {
			BYPASS.remove(player.getUUID());
		}
	}

	/** Casts {@code spec} for {@code player} if every rule allows it; true when it happened. */
	public static boolean cast(ServerPlayer player, TechniqueSpec spec, CastOptions options) {
		CultivationData data = Cultivators.get(player);
		long now = player.level().getGameTime();
		boolean gatesOff = options.bypassGates() || bypass(player);
		boolean free = options.free() || bypass(player);
		// 1. Refusals, in order; each spends nothing.
		if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.now"));
		}
		if (spec.type() == TechniqueType.PASIF) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.passive",
					Component.keybind("key.jump").withStyle(ChatFormatting.YELLOW)));
		}
		if (spec.legacy() == Technique.FLYING_SWORD && data.flyingSword()) {
			Techniques.dismountSword(player);
			return true;
		}
		if (spec.has(TechniqueSpec.Flag.DISCIPLE_ONLY)) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.unknown", spec.id()));
		}
		Component blocked = Techniques.castBlocker(player);
		if (blocked != null) {
			return Techniques.refuse(player, blocked);
		}
		Component busy = busyRefusal(player, data, spec);
		Caster running = find(player, spec.id());
		if (running != null) {
			// Ending a charge, channel or toggle is always allowed; advancing a stage obeys the same refusals as a cast.
			boolean ending = running.charging || running.channelUntil > now || running.toggled;
			if (busy != null && !ending) {
				return Techniques.refuse(player, busy);
			}
			if (Flows.recast(running, player)) {
				return true;
			}
		}
		if (spec.isLegacy() && LegacyArt.recastable(player, spec)) {
			if (busy != null) {
				return Techniques.refuse(player, busy);
			}
			if (active(player) >= TechniquePower.ACTIVE_CASTS_PLAYER) {
				return Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.too_many"));
			}
			if (LegacyArt.recast(player, spec, options)) {
				return true;
			}
		}
		if (!gatesOff) {
			Component why = whyNotCastable(data, spec);
			if (why != null) {
				return Techniques.refuse(player, why);
			}
		}
		if (busy != null) {
			return Techniques.refuse(player, busy);
		}
		if (!free) {
			long global = GLOBAL.getOrDefault(player.getUUID(), 0L);
			if (now < global) {
				return false;
			}
			long lockout = LOCKOUT.getOrDefault(player.getUUID(), 0L);
			if (spec.pamungkas() && now < lockout) {
				return Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.lockout", seconds(lockout - now)));
			}
			if (spec.charges() != null) {
				if (readyCharges(player, spec, data) <= 0) {
					return Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.charges", spec.displayName(),
							seconds(nextCharge(player, spec) - now)));
				}
			} else {
				long left = data.cooldownLeft(spec.id(), now);
				if (left > 0L) {
					return Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.cooldown",
							spec.displayName().copy().withColor(0xFFD54A), seconds(left)));
				}
			}
		}
		int mastery = Mastery.level(data, spec.id());
		int cost = cost(player, data, spec, mastery);
		if (!free && data.qi() + 1.0E-6D < cost) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.qi", spec.displayName().copy().withColor(0xFFD54A),
					Msg.number(data.qi()), String.valueOf(cost)));
		}
		if (spec.has(TechniqueSpec.Flag.FORBIDDEN)) {
			if (player.getHealth() < 2.0F) {
				return Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.forbidden_health", spec.displayName()));
			}
			if (!options.test() && !forbiddenConfirmed(player, spec, now)) {
				return false;
			}
		}
		if (active(player) >= TechniquePower.ACTIVE_CASTS_PLAYER) {
			return Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.too_many"));
		}
		// 2-3. Meditation stops; the snapshot.
		Meditation.stop(player);
		PlayerCaster ctx = new PlayerCaster(player, spec, options);
		ctx.recastStacks = recastStacks(player, spec, now, false);
		SpecialTechnique handler = spec.special() == null ? null : TechniqueRegistry.special(spec.special());
		if (handler != null) {
			Component why = handler.blocker(ctx);
			if (why != null) {
				return Techniques.refuse(player, why);
			}
		}
		// 4. Target precheck (legacy and special code checks its own targets and refuses itself). A dry run: nothing carves.
		boolean attack = spec.type() == TechniqueType.SERANG || spec.type() == TechniqueType.AREA || spec.type() == TechniqueType.KENDALI
				|| spec.type() == TechniqueType.PAMUNGKAS;
		if (!spec.isLegacy() && handler == null && attack && !options.forceVisual() && !spec.has(TechniqueSpec.Flag.CAST_ALWAYS)
				&& Steps.attacks(spec) && !precheck(ctx)) {
			return Techniques.refuse(player, Component.translatable(spec.has(TechniqueSpec.Flag.REQUIRES_TARGET)
					? "kultivator.teknik.refuse.no_lock" : "kultivator.teknik.refuse.no_target", spec.displayName()));
		}
		// Legacy and special casts happen first and spend only when they really happened.
		if (spec.isLegacy() || handler != null) {
			register(ctx);
			boolean done;
			try {
				done = spec.isLegacy() ? LegacyArt.cast(ctx) : handler.cast(ctx);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: technique {} failed", spec.id(), exception);
				done = false;
			}
			if (!done) {
				ctx.ended = true;
				ACTIVE.remove(ctx);
				return false;
			}
			spend(player, data, spec, ctx, cost, free, now);
			start(ctx);
			return true;
		}
		// 5-6. Spend, start.
		spend(player, data, spec, ctx, cost, free, now);
		start(ctx);
		return true;
	}

	/** Refusals 7-8 of 22.2.6 (Qi Deviation, a breakthrough in progress), or null. Checked before any recast too. */
	private static @Nullable Component busyRefusal(ServerPlayer player, CultivationData data, TechniqueSpec spec) {
		if (data.deviating()) {
			return Component.translatable("kultivator.techniques.refuse.deviation", String.valueOf(Math.max(1, data.deviationTicks() / 20)));
		}
		boolean demonTrial = HeartDemons.inBreakthroughTrial(player) && spec.legacy() != Technique.FLYING_SWORD;
		if ((data.breakingThrough() || Breakthrough.isBusy(player)) && !demonTrial) {
			return Component.translatable("kultivator.techniques.refuse.breakthrough");
		}
		return null;
	}

	/** The target precheck as a dry run: Kehancuran anchors and impact points are ignored while it evaluates. */
	static boolean precheck(Caster ctx) {
		ctx.precheck = true;
		try {
			return Steps.anyTarget(ctx);
		} finally {
			ctx.precheck = false;
		}
	}

	/** Drops a registered cast that never really happened (no END, no mastery, nothing spent). */
	static void discard(Caster ctx) {
		ctx.ended = true;
		ACTIVE.remove(ctx);
	}

	/** Qi of one cast after mastery, the realm gap and COST_MOD (toggles: their start qi). */
	static int cost(ServerPlayer player, CultivationData data, TechniqueSpec spec, int mastery) {
		if (spec.toggle() != null && spec.toggle().startQi() > 0) {
			return spec.toggle().startQi();
		}
		if (spec.stages() != null) {
			int first = spec.stages().qiFor(1, -1);
			if (first >= 0) {
				return (int) Math.ceil(first * TechniquePower.masteryQi(mastery));
			}
		}
		return TechniquePower.qi(spec, data, mastery, Boons.qiFactor(player, spec));
	}

	/** Cooldown ticks of one cast after mastery, the realm gap, COST_MOD / CD_RATE and the level-3 COOLDOWN bonus. */
	static int cooldown(ServerPlayer player, CultivationData data, TechniqueSpec spec, int mastery, double bonus) {
		double extra = Boons.cooldownFactor(player, spec) * Math.max(0.0D, 1.0D - bonus);
		return TechniquePower.cooldownTicks(spec, data, mastery, extra);
	}

	private static void spend(ServerPlayer player, CultivationData data, TechniqueSpec spec, Caster ctx, int cost, boolean free, long now) {
		ctx.cooldownSet = cooldown(player, data, spec, ctx.mastery, ctx.cooldownBonus);
		if (!free) {
			if (cost > 0) {
				Qi.trySpend(player, cost);
			}
			ctx.qiPaid = cost;
			if (spec.charges() != null) {
				useCharge(player, spec, ctx.cooldownSet, now);
			} else if (spec.toggle() == null && spec.stages() == null) {
				data.setCooldown(spec.id(), now, ctx.cooldownSet);
			}
			GLOBAL.put(player.getUUID(), now + TechniquePower.GLOBAL_COOLDOWN);
			if (spec.pamungkas()) {
				LOCKOUT.put(player.getUUID(), now + TechniquePower.ULTIMATE_LOCKOUT);
			}
		}
		// Casting anything else ends a channel.
		for (Caster other : activeOf(player)) {
			if (other != ctx && other.channelUntil > now) {
				other.channelUntil = 0L;
				other.end(EndReason.CANCELLED);
			}
		}
		recastStacks(player, spec, now, true);
		remember(player, spec, now);
		if (!ctx.options.test()) {
			FeatTracker.onCast(player, spec);
		}
		Advancements.grant(player, "first_technique");
		Cultivators.dirty(player);
	}

	/** Why the player may not select or cast {@code spec} at all (known, rank, Dao, gate), or null. Pure. */
	public static @Nullable Component whyNotCastablePublic(CultivationData data, TechniqueSpec spec) {
		return whyNotCastable(data, spec);
	}

	/** The refusals 3-6 of 22.2.6: known, rank, Dao, Dewa gate and prerequisites (null = castable). */
	static @Nullable Component whyNotCastable(CultivationData data, TechniqueSpec spec) {
		Component name = spec.displayName().copy().withColor(0xFFD54A);
		// Phase 10b (DESIGN 29.3.1): a Titah decree is never learned; its own gates live in pusakalangit.Titah.whyNot
		if (!data.knows(spec.id()) && !spec.titah()) {
			return Component.translatable("kultivator.techniques.refuse.not_known", name);
		}
		Rank need = spec.minRank();
		if (!data.realm().atLeast(spec.minRealm())) {
			return Component.translatable("kultivator.techniques.refuse.realm", name,
					spec.minRealm().displayName().copy().withColor(spec.minRealm().color()));
		}
		if (data.realm() == spec.minRealm() && (data.rank().layer() < need.layer() || data.rank().layer() == need.layer() && data.rank().stage() < need.stage())) {
			return Component.translatable("kultivator.teknik.refuse.rank", name, need.displayName());
		}
		if (spec.dao() != null && spec.dao() != data.dao()) {
			return Component.translatable("kultivator.techniques.refuse.dao", name, spec.dao().displayName().copy().withColor(spec.dao().color()));
		}
		return Unlocks.gateBlocker(data, spec);
	}

	private static boolean forbiddenConfirmed(ServerPlayer player, TechniqueSpec spec, long now) {
		CultivationData data = Cultivators.get(player);
		if (data.extra().getCompoundOrEmpty(Mastery.EXTRA).getListOrEmpty("forbidden").stream()
				.anyMatch(tag -> tag.asString().orElse("").equals(spec.id()))) {
			return true;
		}
		String pending = FORBIDDEN_CONFIRM.get(player.getUUID());
		if (pending != null && pending.startsWith(spec.id() + "@")) {
			long until = Long.parseLong(pending.substring(pending.indexOf('@') + 1));
			if (now <= until) {
				net.minecraft.nbt.CompoundTag teknik = Mastery.section(data.extra(), Mastery.EXTRA);
				net.minecraft.nbt.ListTag list = teknik.getListOrEmpty("forbidden");
				list.add(net.minecraft.nbt.StringTag.valueOf(spec.id()));
				teknik.put("forbidden", list);
				FORBIDDEN_CONFIRM.remove(player.getUUID());
				return true;
			}
		}
		FORBIDDEN_CONFIRM.put(player.getUUID(), spec.id() + "@" + (now + CONFIRM_TICKS));
		Msg.chat(player, Component.translatable("kultivator.teknik.forbidden_confirm", spec.displayName()).withColor(0xFF6A5A));
		return false;
	}

	// ================================================================== charges, stacks, recent casts

	static int readyCharges(ServerPlayer player, TechniqueSpec spec, CultivationData data) {
		if (spec.charges() == null) {
			return 0;
		}
		long now = player.level().getGameTime();
		int max = maxCharges(spec, data);
		List<Long> list = CHARGES.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).computeIfAbsent(spec.id(), key -> new ArrayList<>());
		list.removeIf(time -> time <= now);
		return Math.max(0, max - list.size());
	}

	static int maxCharges(TechniqueSpec spec, CultivationData data) {
		return spec.charges() == null ? 0 : Math.max(1, TechniquePower.count(spec.charges().count(), TechniquePower.factors(data, spec), 0.0D));
	}

	private static long nextCharge(ServerPlayer player, TechniqueSpec spec) {
		List<Long> list = CHARGES.getOrDefault(player.getUUID(), Map.of()).getOrDefault(spec.id(), List.of());
		long next = Long.MAX_VALUE;
		for (long time : list) {
			next = Math.min(next, time);
		}
		return next == Long.MAX_VALUE ? player.level().getGameTime() : next;
	}

	private static void useCharge(ServerPlayer player, TechniqueSpec spec, int cooldown, long now) {
		int recharge = Math.max(TechniquePower.MIN_COOLDOWN, (int) Math.round(spec.charges().recharge() * (cooldown / (double) Math.max(1, spec.cooldownTicks()))));
		CHARGES.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).computeIfAbsent(spec.id(), key -> new ArrayList<>()).add(now + recharge);
	}

	/** RECAST stacks within the spec's window (recorded when {@code record}). */
	private static int recastStacks(ServerPlayer player, TechniqueSpec spec, long now, boolean record) {
		if (spec.stack() == null || spec.stack().source() != dev.kultivator.technique.spec.dsl.Flow.Stack.Source.RECAST) {
			return 0;
		}
		long[] entry = STACKS.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).computeIfAbsent(spec.id(), key -> new long[]{0L, 0L});
		int stacks = now - entry[1] <= spec.stack().window() ? (int) entry[0] : 0;
		if (record) {
			entry[0] = Math.min(spec.stack().max(), stacks + 1);
			entry[1] = now;
		}
		return stacks;
	}

	private static void remember(LivingEntity caster, TechniqueSpec spec, long now) {
		Deque<long[]> deque = RECENT.computeIfAbsent(caster.getUUID(), key -> new ArrayDeque<>());
		deque.addFirst(new long[]{spec.netId(), now});
		while (deque.size() > RECENT_MAX) {
			deque.removeLast();
		}
	}

	/** Distinct spec ids cast in the last {@code seconds} seconds, newest first. */
	static List<String> recentCasts(LivingEntity caster, int seconds) {
		Deque<long[]> deque = RECENT.get(caster.getUUID());
		if (deque == null) {
			return List.of();
		}
		long now = caster.level().getGameTime();
		Set<String> ids = new LinkedHashSet<>();
		for (long[] entry : deque) {
			if (now - entry[1] > seconds * 20L) {
				break;
			}
			TechniqueSpec spec = TechniqueRegistry.byNet((int) entry[0]);
			if (spec != null) {
				ids.add(spec.id());
			}
		}
		return new ArrayList<>(ids);
	}

	/** Session cooldown state for the HUD: global cooldown end, lockout end. */
	static long[] sessionTimes(ServerPlayer player) {
		return new long[]{GLOBAL.getOrDefault(player.getUUID(), 0L), LOCKOUT.getOrDefault(player.getUUID(), 0L)};
	}

	/** Charge state for the HUD: {id: [ready, max, next recharge tick]}. */
	static Map<String, long[]> chargeState(ServerPlayer player) {
		Map<String, long[]> map = new HashMap<>();
		Map<String, List<Long>> charges = CHARGES.get(player.getUUID());
		if (charges == null) {
			return map;
		}
		CultivationData data = Cultivators.get(player);
		for (Map.Entry<String, List<Long>> entry : charges.entrySet()) {
			TechniqueSpec spec = TechniqueRegistry.byId(entry.getKey());
			if (spec != null) {
				map.put(entry.getKey(), new long[]{readyCharges(player, spec, data), maxCharges(spec, data), nextCharge(player, spec)});
			}
		}
		return map;
	}

	/** Resets the player's session cooldowns (debug, uji). */
	static void resetSession(ServerPlayer player) {
		GLOBAL.remove(player.getUUID());
		LOCKOUT.remove(player.getUUID());
		CHARGES.remove(player.getUUID());
	}

	/** Casts a disciple can have running at once (22.13.4). */
	public static final int DISCIPLE_ACTIVE_MAX = 3;

	/**
	 * Runs one disciple spec cast (DESIGN 22.13.3) after {@code DiscipleTechniques.trySpec} checked the gates and the qi:
	 * the disciple's global cooldown, at most 3 active casts, the special blocker and the target precheck; legacy and
	 * special casts happen first. True when the cast really happened (the caller then spends qi and sets the cooldown).
	 */
	public static boolean castDisciple(DiscipleEntity entity, TechniqueSpec spec, @Nullable LivingEntity target) {
		if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level) || entity.owner() == null) {
			return false;
		}
		long now = level.getGameTime();
		if (active(entity) >= DISCIPLE_ACTIVE_MAX || find(entity, spec.id()) != null) {
			// one running instance per spec: a stance or stage window still open is never stacked
			return false;
		}
		long global = GLOBAL.getOrDefault(entity.getUUID(), 0L);
		if (now < global) {
			return false;
		}
		DiscipleCaster ctx = new DiscipleCaster(entity, spec, target, CastOptions.DEFAULT);
		SpecialTechnique handler = spec.special() == null ? null : TechniqueRegistry.special(spec.special());
		if (handler != null && (!handler.supportsDisciples() || handler.blocker(ctx) != null)) {
			return false;
		}
		boolean attack = spec.type() == TechniqueType.SERANG || spec.type() == TechniqueType.AREA || spec.type() == TechniqueType.KENDALI
				|| spec.type() == TechniqueType.PAMUNGKAS;
		if (!spec.isLegacy() && handler == null && attack && !spec.has(TechniqueSpec.Flag.CAST_ALWAYS) && Steps.attacks(spec) && !precheck(ctx)) {
			return false;
		}
		if (spec.isLegacy() || handler != null) {
			register(ctx);
			boolean done;
			try {
				done = spec.isLegacy() ? LegacyArt.cast(ctx) : handler.cast(ctx);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: disciple technique {} failed", spec.id(), exception);
				done = false;
			}
			if (!done) {
				ctx.ended = true;
				ACTIVE.remove(ctx);
				return false;
			}
		}
		GLOBAL.put(entity.getUUID(), now + TechniquePower.GLOBAL_COOLDOWN);
		rememberDisciple(entity, spec, now);
		start(ctx);
		if (!spec.isLegacy() && handler == null && spec.stages() != null && spec.discipleStages() > 1) {
			scheduleDiscipleStages(ctx, spec);
		}
		return true;
	}

	/** Ticks between the stages a disciple runs on its own (its "recasts"). */
	private static final int DISCIPLE_STAGE_GAP = 20;

	/**
	 * A disciple's STAGES cast with {@code discipleStages(n)}: stages 2..n follow one gap apart inside the window, each
	 * behind its realm gate and paid with the disciple's qi (×0.6); a missing gate or qi stops the sequence there.
	 */
	private static void scheduleDiscipleStages(DiscipleCaster ctx, TechniqueSpec spec) {
		dev.kultivator.technique.spec.dsl.Flow.Stages stages = spec.stages();
		int last = Math.min(stages.count(), spec.discipleStages());
		int gap = Math.max(DISCIPLE_STAGE_GAP, Math.min(stages.window() - 1, Math.max(stages.gap(), DISCIPLE_STAGE_GAP)));
		for (int stage = 2; stage <= last; stage++) {
			int next = stage;
			ctx.schedule(gap * (stage - 1), () -> advanceDisciple(ctx, stages, next, last));
		}
	}

	private static void advanceDisciple(DiscipleCaster ctx, dev.kultivator.technique.spec.dsl.Flow.Stages stages, int next, int last) {
		if (ctx.ended || ctx.stage != next - 1 || !ctx.casterValid()) {
			return;
		}
		DiscipleEntity entity = ctx.disciple();
		dev.kultivator.data.Realm gate = stages.realmFor(next);
		if (gate != dev.kultivator.data.Realm.MORTAL && !entity.data().realm().atLeast(gate)) {
			return;
		}
		double qi = stages.qiFor(next, 0) * TechniquePower.DISCIPLE_QI_FACTOR;
		if (qi > 0.0D) {
			if (entity.data().qi() + 1.0E-6D < qi) {
				return;
			}
			entity.data().setQi(entity.data().qi() - qi);
		}
		long now = ctx.level.getGameTime();
		ctx.stage = next;
		ctx.stageWindowUntil = next >= last ? 0L : now + Math.max(1, stages.window());
		Steps.startStage(ctx, next);
		if (next >= last) {
			Steps.windowEnded(ctx);
		}
	}

	/** Disciples: one cast per 10 ticks at most (their own global cooldown). */
	static boolean discipleReady(DiscipleEntity entity, long now) {
		long global = GLOBAL.getOrDefault(entity.getUUID(), 0L);
		if (now < global) {
			return false;
		}
		GLOBAL.put(entity.getUUID(), now + TechniquePower.GLOBAL_COOLDOWN);
		return true;
	}

	static void rememberDisciple(DiscipleEntity entity, TechniqueSpec spec, long now) {
		know(entity);
		remember(entity, spec, now);
	}

	/** Counts for {@code debug teknik beban}. */
	static int[] counts() {
		int children = 0;
		for (Caster cast : ACTIVE) {
			if (cast.parent != null) {
				children++;
			}
		}
		return new int[]{ACTIVE.size(), children};
	}

	static List<Caster> all() {
		return List.copyOf(ACTIVE);
	}

	static String seconds(long ticks) {
		return String.format(Locale.ROOT, "%.1f", Math.max(0L, ticks) / 20.0D);
	}

	/** Ends the player's lasting casts (the showcase ends the previous technique's lasting parts before the next). */
	static void endAll(ServerPlayer player, EndReason reason) {
		for (Caster cast : activeOf(player)) {
			cast.end(reason);
		}
	}

	private TechniqueRunner() {
	}
}
