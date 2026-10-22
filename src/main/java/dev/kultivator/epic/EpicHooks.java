package dev.kultivator.epic;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.Calamity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.disciple.path.FoundationTypes;
import dev.kultivator.disciple.path.SoulLamps;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.sect.build.api.BuildListener;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.KillCredit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server side of the epic moments (DESIGN 23.5.2-23.5.4): the zero-edit listeners, the one-line hooks other files
 * call, the PRE / HUSH timers of the tribulation, the storyboard world sounds (at most 4 per beat with the existing
 * ones) and the saved {@code extra.momen} milestones. Every hook only reads its arguments, never throws and never
 * changes behaviour; with {@code epicMoments = false} nothing is sent and today's FX play alone.
 */
public final class EpicHooks {

	/** DISCIPLE_BT REVEAL {@code b}: the special reveal kinds of E4 (d). */
	public static final int REVEAL_BEGGAR = 1;
	public static final int REVEAL_HERETIC_VEINS = 2;
	public static final int REVEAL_DOU_DI = 3;
	/** PRE and HUSH cues leave this many ticks before they land (the client schedules by {@code at}). */
	public static final int SEND_AHEAD = 10;
	/** Penginjak Langit follows the seventh calamity's PASS after this many ticks. */
	public static final int TRAMPLING_DELAY = 120;
	/** Saved key in {@code extra()}: {@code {firsts: long, kills: int}}. */
	public static final String SAVED_KEY = "momen";
	/** The tag of secret realm guards (owned by P8-alam, DESIGN 25.14); a guardian also carries {@code kultivator.boss}. */
	private static final String REALM_GUARD_TAG = "kultivator.realm_guard";
	/** Phase 9: the tag of the primordial beasts and their relics (relics are never bosses). */
	private static final String PURBA_TAG = "kultivator.purba";

	static final int TAG_TRIB = 1;
	static final int TAG_TEST = 2;
	static final int TAG_MISC = 3;
	private static final UUID NOBODY = new UUID(0L, 0L);

	/** First-milestone bits of {@code extra.momen.firsts}: realms 0-8, Immortal Ascension layers 9-17, disciples 20-26. */
	private static final int FIRST_IA_LAYER = 9;
	private static final int FIRST_DISCIPLE = 20;

	private record Timer(UUID owner, int tag, long when, Runnable action) {
	}

	/** A running tribulation as 7b sees it (positions stay at the start, as the tribulation's clouds do). */
	private record Trib(Vec3 pos, int variant, Rank target, int total, int s, boolean test) {
	}

	private static final List<Timer> TIMERS = new ArrayList<>();
	private static final Map<UUID, Trib> TRIBS = new HashMap<>();
	/** Game time of a Hardcore realm drop (Breakthrough.onFail comes in the same tick before the tribulation's end). */
	private static final Map<UUID, Long> HARDCORE = new HashMap<>();
	private static @Nullable MinecraftServer server;

	/** Registers the listeners (Tribulation, Breakthrough, disciples, build, boss deaths, DeathGuard) and the timer. */
	public static void init() {
		Tribulation.addListener(new Tribulation.Listener() {
			@Override
			public void onOmen(ServerPlayer p, Rank t) {
				guard(() -> tribOmen(p, t));
			}

			@Override
			public void onStrike(ServerPlayer p, Rank t, int index, int total) {
				guard(() -> tribStrike(p, t, index, total));
			}

			@Override
			public void onFinish(ServerPlayer p, Rank t, @Nullable Tribulation.Result result, int strikesDone, boolean test) {
				guard(() -> tribFinish(p, t, result, strikesDone, test));
			}
		});
		Breakthrough.addListener(new Breakthrough.Listener() {
			@Override
			public void onBegin(ServerPlayer p, Rank from, Rank target, int kind, boolean test) {
				guard(() -> send(p.level(), EpicIds.BREAKTHROUGH, EpicIds.BT_BEGIN, p, p, p.position(),
						EpicIds.packBreakthrough(target.realm().ordinal(), 0, kind, target.layer()), 0, 0.0F, EpicScale.of(target), test(test)));
			}

			@Override
			public void onSuccess(ServerPlayer p, Rank before, Rank now, BreakthroughQuality q, int kind, boolean test) {
				guard(() -> breakthroughSuccess(p, now, q, kind, test));
			}

			@Override
			public void onFail(ServerPlayer p, boolean hardcoreDrop, boolean test) {
				guard(() -> {
					if (hardcoreDrop) {
						HARDCORE.put(p.getUUID(), p.level().getGameTime());
					}
					int flags = test(test) | EpicIds.FLAG_FINAL | (hardcoreDrop ? EpicIds.FLAG_HARDCORE : 0);
					send(p.level(), EpicIds.BREAKTHROUGH, EpicIds.BT_FAIL, p, p, p.position(), 0, 0, hardcoreDrop ? 1.0F : 0.0F,
							EpicScale.of(Cultivators.get(p).rank()), flags);
				});
			}
		});
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onStart(DiscipleEntity entity, DiscipleBreakthrough.Kind kind, Rank target) {
				guard(() -> discipleBeat(entity, EpicIds.DBT_START, EpicIds.packBreakthrough(target.realm().ordinal(), 0, kind.ordinal(), 0), 0,
						discipleScale(entity, target), 0));
			}

			@Override
			public void onStrike(DiscipleEntity entity, int index, int count) {
				guard(() -> discipleBeat(entity, EpicIds.DBT_STRIKE, index, count, EpicScale.ofDisciple(entity), index >= count ? EpicIds.FLAG_FINAL : 0));
			}

			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				guard(() -> discipleEnd(entity, success, kind));
			}

			@Override
			public void onCancel(DiscipleEntity entity, DiscipleBreakthrough.Kind kind) {
				guard(() -> discipleBeat(entity, EpicIds.DBT_CANCEL, 0, 0, EpicScale.ofDisciple(entity), EpicIds.FLAG_FINAL));
			}
		});
		BuildListener.add(new BuildListener() {
			@Override
			public void onTierDone(UUID owner, int template, int tier) {
				guard(() -> sectTier(owner, template, tier));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> guard(() -> bossDeath(entity, source)));
		DeathGuard.addSavedListener((p, stage) -> guard(() -> cheatDeath(p, stage)));
		ServerTickEvents.END_SERVER_TICK.register(EpicHooks::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			cancelTimers(player.getUUID(), 0);
			TRIBS.remove(player.getUUID());
			HARDCORE.remove(player.getUUID());
			EpicTests.stopQuietly(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			TIMERS.clear();
			TRIBS.clear();
			HARDCORE.clear();
			EpicMoments.clear();
			EpicTests.clear();
		});
	}

	// ================================================================== one-line hooks (23.5.3)

	/** Pengakuan Langit: beat DARKEN, PILLAR, HEAVENS, VOICE, FINALE or STOP at event tick {@code tick}. */
	public static void ascension(ServerPlayer player, int beat, int tick, boolean real) {
		guard(() -> {
			int flags = (real ? 0 : EpicIds.FLAG_TEST) | (beat == EpicIds.ASC_VOICE ? EpicIds.FLAG_SUPPRESS_TITLE : 0)
					| (beat == EpicIds.ASC_FINALE || beat == EpicIds.ASC_STOP ? EpicIds.FLAG_FINAL : 0);
			send(player.level(), EpicIds.ASCENSION, beat, player, player, player.position(), tick, 0, real ? 1.0F : 0.0F,
					EpicScale.of(new Rank(Realm.TRUE_IMMORTAL, 0, 0)), flags);
		});
	}

	/** Heart demon trial beat; {@code demon} from APPEAR on, {@code kind} the trial kind. */
	public static void heartDemon(ServerPlayer player, @Nullable LivingEntity demon, int beat, int kind) {
		guard(() -> {
			Entity subject = beat != EpicIds.HD_WARNING && demon != null && !demon.isRemoved() ? demon : player;
			int flags = beat == EpicIds.HD_WON ? EpicIds.FLAG_SUPPRESS_TITLE | EpicIds.FLAG_FINAL : beat == EpicIds.HD_LOST ? EpicIds.FLAG_FINAL : 0;
			send(player.level(), EpicIds.HEART_DEMON, beat, subject, player, subject.position(), kind, 0, 0.0F,
					EpicScale.of(Cultivators.get(player).rank()), flags);
		});
	}

	/** Ancient God calamity {@code k} of a disciple; {@code index} of {@code total} strikes. */
	public static void calamity(DiscipleEntity disciple, @Nullable ServerPlayer master, int beat, int k, int index, int total) {
		guard(() -> {
			if (!(disciple.level() instanceof ServerLevel level)) {
				return;
			}
			boolean last = beat == EpicIds.CAL_STRIKE && index >= total;
			int flags = last || beat == EpicIds.CAL_PASS || beat == EpicIds.CAL_FAIL || beat == EpicIds.CAL_ABORT ? EpicIds.FLAG_FINAL : 0;
			int s = EpicScale.ofDisciple(disciple);
			Vec3 pos = disciple.position();
			send(level, EpicIds.CALAMITY, beat, disciple, master, pos, k, EpicIds.packCalamity(index, total), 0.0F, s, flags);
			if (beat == EpicIds.CAL_PASS && k >= SoulLamps.LAMP_BASE_K) {
				send(level, EpicIds.CALAMITY, EpicIds.CAL_LAMP, disciple, master, pos, k, EpicIds.packCalamity(index, total), 0.0F, s, EpicIds.FLAG_FINAL);
			} else if (beat == EpicIds.CAL_PASS && k == Calamity.COUNT) {
				UUID masterId = master == null ? null : master.getUUID();
				schedule(disciple.getUUID(), TAG_MISC, level.getGameTime() + TRAMPLING_DELAY, () -> {
					if (!disciple.isRemoved()) {
						ServerPlayer now = masterId == null ? null : level.getServer().getPlayerList().getPlayer(masterId);
						send(level, EpicIds.CALAMITY, EpicIds.CAL_TRAMPLING, disciple, now, disciple.position(), k, 0, 0.0F, EpicIds.SCALE_MAX,
								EpicIds.FLAG_FINAL);
					}
				});
			}
		});
	}

	/**
	 * The player's own star calamity {@code k} (audit M4 S8): the CALAMITY beats (omen, pre, strike, pass, fail, abort) with
	 * the player as subject and viewer, so the disciples' calamity scene, its sky and its danger-gated 7b shots play for the
	 * player too. {@code index} of {@code total} strikes.
	 */
	public static void playerCalamity(ServerPlayer player, int beat, int k, int index, int total) {
		guard(() -> {
			boolean last = beat == EpicIds.CAL_STRIKE && index >= total;
			int flags = last || beat == EpicIds.CAL_PASS || beat == EpicIds.CAL_FAIL || beat == EpicIds.CAL_ABORT ? EpicIds.FLAG_FINAL : 0;
			send(player.level(), EpicIds.CALAMITY, beat, player, player, player.position(), k, EpicIds.packCalamity(index, total), 0.0F,
					EpicScale.of(Cultivators.get(player).rank()), flags);
		});
	}

	/** The disciple surpasses the master. */
	public static void surpass(DiscipleEntity disciple, ServerPlayer master, boolean byDuel) {
		guard(() -> {
			if (disciple.level() instanceof ServerLevel level) {
				send(level, EpicIds.SURPASS, EpicIds.SURPASS_TRIGGER, disciple, master, disciple.position(), byDuel ? 1 : 0,
						DisciplePaths.stars(disciple.data()), 0.0F, EpicScale.ofDisciple(disciple), EpicIds.FLAG_FINAL);
			}
		});
	}

	/** The master takes the disciple's tribulation strike (Tahan Petir). */
	public static void takeLightning(DiscipleEntity disciple, ServerPlayer master) {
		guard(() -> discipleBeat(disciple, EpicIds.DBT_TAKE_LIGHTNING, 0, master.getId(), EpicScale.ofDisciple(disciple), 0));
	}

	/** A disciple cheat-death; {@code kind} one of {@code EpicIds.SAVE_*}. */
	public static void discipleSave(DiscipleEntity disciple, @Nullable ServerPlayer master, int kind) {
		discipleSave(disciple, master, kind, null);
	}

	/**
	 * Like {@link #discipleSave(DiscipleEntity, ServerPlayer, int)} with a point the save flies to (SAVE_SOUL_FLIGHT: the
	 * Sect Hall). The direction, capped at 40 blocks, travels as b = (dx & 0xFFFF) << 16 | (dz & 0xFFFF) and f = dy.
	 */
	public static void discipleSave(DiscipleEntity disciple, @Nullable ServerPlayer master, int kind, @Nullable Vec3 to) {
		guard(() -> {
			if (disciple.level() instanceof ServerLevel level) {
				int b = 0;
				float f = 0.0F;
				if (to != null) {
					Vec3 d = to.subtract(disciple.position());
					double length = d.length();
					if (length > 40.0D) {
						d = d.scale(40.0D / length);
					}
					int dx = (int) Math.round(d.x);
					int dz = (int) Math.round(d.z);
					b = (dx & 0xFFFF) << 16 | (dz & 0xFFFF);
					f = (float) d.y;
				}
				send(level, EpicIds.CHEAT_DEATH, EpicIds.CHEAT_SAVED, disciple, master, disciple.position(), kind, b, f,
						EpicScale.ofDisciple(disciple), EpicIds.FLAG_FINAL);
			}
		});
	}

	/** The Sect Hall of {@code owner} reached {@code level}. */
	public static void hallLevel(MinecraftServer server, UUID owner, int level) {
		guard(() -> {
			Halls.HallView hall = Halls.hallOf(owner);
			ServerLevel world = hall == null ? null : server.getLevel(hall.dim());
			if (world == null) {
				return;
			}
			Vec3 pos = Vec3.atBottomCenterOf(hall.pos()).add(0.0D, 1.0D, 0.0D);
			send(world, EpicIds.SECT, EpicIds.SECT_HALL_LEVEL, null, server.getPlayerList().getPlayer(owner), pos, level, 0, 0.0F, 1, EpicIds.FLAG_FINAL);
		});
	}

	/** Recruitment day beat (ARRIVAL, TEST, ACCEPT) of the sect of {@code owner}. */
	public static void recruit(ServerLevel level, UUID owner, int beat, Vec3 pos, int a, int b) {
		guard(() -> send(level, EpicIds.RECRUIT, beat, null, level.getServer().getPlayerList().getPlayer(owner), pos, a, b, 0.0F, 1,
				beat == EpicIds.RECRUIT_ARRIVAL ? EpicIds.FLAG_SUPPRESS_TITLE : 0));
	}

	/** Tournament beat (INTRO, WINNER); {@code kind} one of {@code EpicIds.TOUR_*}. */
	public static void tournament(ServerLevel level, int beat, int kind, @Nullable LivingEntity first, @Nullable LivingEntity second, Vec3 pos) {
		guard(() -> {
			ServerPlayer viewer = ownerOf(first);
			if (viewer == null) {
				viewer = ownerOf(second);
			}
			if (viewer == null && level.getNearestPlayer(pos.x, pos.y, pos.z, 48.0D, false) instanceof ServerPlayer near) {
				viewer = near;
			}
			send(level, EpicIds.TOURNAMENT, beat, first, viewer, pos, kind, second == null ? -1 : second.getId(), 0.0F, 3,
					beat == EpicIds.TOUR_WINNER ? EpicIds.FLAG_FINAL : 0);
		});
	}

	/** Mode Kehancuran beat (WARNING, IMPACT, ABORT, RESTORE, CRATER); primitives only. */
	public static void kehancuran(ServerLevel level, @Nullable ServerPlayer owner, @Nullable Entity caster, Vec3 centre, int art, float radius,
								  int beat, int blocks) {
		guard(() -> {
			if (level == null) {
				return;
			}
			int flags = beat == EpicIds.KEH_WARNING ? 0 : EpicIds.FLAG_FINAL;
			int s = owner == null ? 4 : EpicScale.of(Cultivators.get(owner).rank());
			send(level, EpicIds.KEHANCURAN, beat, caster, owner, centre, art, blocks, radius, s, flags);
		});
	}

	/** A disciple's signature ultimate: a short stamp and a sky grade for its master (DESIGN 27.6). */
	public static void discipleUltimate(DiscipleEntity disciple, @Nullable ServerPlayer master, @Nullable dev.kultivator.data.Dao dao, Vec3 centre,
			double radius) {
		guard(() -> {
			if (disciple.level() instanceof ServerLevel level) {
				send(level, EpicIds.EXTRA, EpicIds.EXTRA_DISCIPLE_ULT, disciple, master, centre, dao == null ? -1 : dao.ordinal(),
						(int) Math.max(0L, Math.min(255L, Math.round(radius))), 0.0F, EpicScale.ofDisciple(disciple), EpicIds.FLAG_FINAL);
			}
		});
	}

	/** A disciple's Jurus Pemusnah in its block-safe form: a light impact beat (stamp + sky grade, no warning) for its master. */
	public static void destroyerArt(DiscipleEntity disciple, @Nullable ServerPlayer master, int art, Vec3 centre, double radius) {
		guard(() -> {
			if (disciple.level() instanceof ServerLevel level) {
				send(level, EpicIds.EXTRA, EpicIds.EXTRA_DESTROYER_ART, disciple, master, centre, art,
						(int) Math.max(0L, Math.min(255L, Math.round(radius))), 0.0F, EpicScale.ofDisciple(disciple), EpicIds.FLAG_FINAL);
			}
		});
	}

	/** One of the {@code EpicIds.EXTRA_*} beats. */
	public static void extra(ServerLevel level, int beat, @Nullable ServerPlayer viewer, @Nullable Entity subject, Vec3 pos, int a, int b) {
		guard(() -> {
			int s = viewer == null ? 0 : EpicScale.of(Cultivators.get(viewer).rank());
			send(level, EpicIds.EXTRA, beat, subject, viewer, pos, a, b, 0.0F, s, EpicIds.FLAG_FINAL);
		});
	}

	// ================================================================== tribulation (23.5.2)

	private static void tribOmen(ServerPlayer p, Rank t) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		boolean test = Tribulation.isTest(p);
		int total = Math.max(1, Tribulation.strikeTotal(p));
		int variant = Tribulation.variant(level);
		Trib trib = new Trib(p.position(), variant, t, total, EpicScale.of(t), test);
		cancelTimers(p.getUUID(), TAG_TRIB);
		TRIBS.put(p.getUUID(), trib);
		send(level, EpicIds.TRIBULATION, EpicIds.TRIB_OMEN, p, p, trib.pos(), now, EpicIds.packOmen(variant, t.realm().ordinal(), t.layer()), total,
				0.0F, trib.s(), test(test) | EpicIds.FLAG_SUPPRESS_TITLE);
		schedulePre(p, trib, 1, now + Tribulation.OMEN_TICKS);
		if (total == 1) {
			scheduleHush(p, trib, now + Tribulation.OMEN_TICKS);
		}
	}

	private static void tribStrike(ServerPlayer p, Rank t, int index, int total) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		Trib trib = TRIBS.get(p.getUUID());
		if (trib == null) {
			trib = new Trib(p.position(), Tribulation.variant(level), t, total, EpicScale.of(t), Tribulation.isTest(p));
			TRIBS.put(p.getUUID(), trib);
		}
		boolean last = index >= total;
		send(level, EpicIds.TRIBULATION, EpicIds.TRIB_STRIKE, p, p, trib.pos(), now, index, total, 0.0F, trib.s(),
				test(trib.test()) | (last ? EpicIds.FLAG_FINAL : 0));
		if (!last) {
			long next = now + Tribulation.STRIKE_INTERVAL;
			schedulePre(p, trib, index + 1, next);
			if (index == total - 1) {
				scheduleHush(p, trib, next);
			}
		}
	}

	private static void tribFinish(ServerPlayer p, Rank t, @Nullable Tribulation.Result result, int strikesDone, boolean test) {
		Trib trib = TRIBS.remove(p.getUUID());
		cancelTimers(p.getUUID(), TAG_TRIB);
		ServerLevel level = p.level();
		long now = level.getGameTime();
		Vec3 pos = trib == null ? p.position() : trib.pos();
		int s = trib == null ? EpicScale.of(t) : trib.s();
		int flags = test(test) | EpicIds.FLAG_FINAL;
		if (result == null || result == Tribulation.Result.CANCELLED) {
			send(level, EpicIds.TRIBULATION, EpicIds.TRIB_ABORT, p, p, pos, now, strikesDone, 0, 0.0F, s, flags);
			return;
		}
		if (result == Tribulation.Result.SUCCESS) {
			send(level, EpicIds.TRIBULATION, EpicIds.TRIB_END_OK, p, p, pos, now, strikesDone, 0, 0.0F, s, flags);
			return;
		}
		Long drop = HARDCORE.remove(p.getUUID());
		boolean hardcore = drop != null && drop == now;
		send(level, EpicIds.TRIBULATION, EpicIds.TRIB_END_FAIL, p, p, pos, now, strikesDone, 0, hardcore ? 1.0F : 0.0F, s,
				flags | (hardcore ? EpicIds.FLAG_HARDCORE : 0));
	}

	/** PRE of strike {@code index} that lands {@code strikeAt}: the cue lands {@link EpicIds#PRE_LEAD} ticks before it. */
	private static void schedulePre(ServerPlayer p, Trib trib, int index, long strikeAt) {
		long landAt = strikeAt - EpicIds.PRE_LEAD;
		UUID id = p.getUUID();
		schedule(id, TAG_TRIB, landAt - SEND_AHEAD, () -> {
			ServerPlayer player = playerOf(id);
			if (player != null && TRIBS.get(id) == trib) {
				send(player.level(), EpicIds.TRIBULATION, EpicIds.TRIB_PRE, player, player, trib.pos(), landAt, index, trib.total(), 0.0F, trib.s(),
						test(trib.test()));
			}
		});
	}

	/** HUSH before the final strike that lands {@code strikeAt}. */
	private static void scheduleHush(ServerPlayer p, Trib trib, long strikeAt) {
		long landAt = strikeAt - EpicIds.HUSH_LEAD;
		UUID id = p.getUUID();
		schedule(id, TAG_TRIB, landAt - SEND_AHEAD, () -> {
			ServerPlayer player = playerOf(id);
			if (player != null && TRIBS.get(id) == trib) {
				send(player.level(), EpicIds.TRIBULATION, EpicIds.TRIB_HUSH, player, player, trib.pos(), landAt, trib.total(), trib.total(), 0.0F,
						trib.s(), test(trib.test()));
			}
		});
	}

	// ================================================================== breakthroughs, disciples, sect, bosses

	private static void breakthroughSuccess(ServerPlayer p, Rank now, BreakthroughQuality q, int kind, boolean test) {
		int flags = test(test) | EpicIds.FLAG_SUPPRESS_TITLE | EpicIds.FLAG_FINAL;
		if (!test && markFirst(p, playerMilestone(now))) {
			flags |= EpicIds.FLAG_FIRST;
		}
		send(p.level(), EpicIds.BREAKTHROUGH, EpicIds.BT_SUCCESS, p, p, p.position(),
				EpicIds.packBreakthrough(now.realm().ordinal(), q.ordinal(), kind, now.layer()), 0, 0.0F, EpicScale.of(now), flags);
	}

	private static void discipleEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
		if (!success) {
			discipleBeat(entity, EpicIds.DBT_END_FAIL, 0, 0, EpicScale.ofDisciple(entity), EpicIds.FLAG_FINAL);
			return;
		}
		DiscipleData data = entity.data();
		Rank rank = data.rank();
		BreakthroughQuality quality = data.quality(rank.realm());
		int foundation = rank.realm() == Realm.FOUNDATION ? FoundationTypes.type(data) : 0;
		int flags = EpicIds.FLAG_FINAL;
		ServerPlayer master = entity.owner();
		int milestone = discipleMilestone(rank);
		if (master != null && milestone >= 0 && markFirst(master, FIRST_DISCIPLE + milestone)) {
			flags |= EpicIds.FLAG_FIRST;
		}
		discipleBeat(entity, EpicIds.DBT_END_OK, EpicIds.packBreakthrough(rank.realm().ordinal(), quality == null ? 1 : quality.ordinal(), kind.ordinal(), 0),
				foundation, discipleScale(entity, rank), flags);
	}

	private static void discipleBeat(DiscipleEntity entity, int beat, int a, int b, int s, int flags) {
		if (entity.level() instanceof ServerLevel level) {
			send(level, EpicIds.DISCIPLE_BT, beat, entity, entity.owner(), entity.position(), a, b, 0.0F, s, flags);
		}
	}

	private static int discipleScale(DiscipleEntity entity, Rank target) {
		return DisciplePaths.stars(entity.data()) >= EpicScale.STAR_SCALE_FROM ? EpicIds.SCALE_MAX : EpicScale.of(target);
	}

	private static void sectTier(UUID owner, int template, int tier) {
		MinecraftServer current = server;
		SiteView site = current == null ? null : EngineApi.get().site(owner);
		ServerLevel level = site == null ? null : current.getLevel(site.dim());
		if (level == null) {
			return;
		}
		BlockPos main = EngineApi.get().anchor(owner, "main_hall");
		Vec3 pos = Vec3.atBottomCenterOf(main != null ? main : site.stele()).add(0.0D, 1.0D, 0.0D);
		Vec3 gate = gateEnd(site);
		// f: the yaw (degrees) from the main hall toward the gate, so a flyover can follow the axis.
		float yaw = (float) Math.toDegrees(Math.atan2(-(gate.x - pos.x), gate.z - pos.z));
		ServerPlayer viewer = current.getPlayerList().getPlayer(owner);
		send(level, EpicIds.SECT, EpicIds.SECT_TIER, null, viewer, pos, tier, template, yaw, 1, EpicIds.FLAG_SUPPRESS_TITLE);
		if (tier >= SectTemplates.TIERS) {
			send(level, EpicIds.SECT, EpicIds.SECT_TEMPLATE, null, viewer, pos, tier, template, yaw, template >= 9 ? 9 : 5, EpicIds.FLAG_FINAL);
		}
	}

	/** The gate end of the site's main axis (as {@code Ceremonies} computes it). */
	private static Vec3 gateEnd(SiteView site) {
		BoundingBox fp = site.footprint();
		Direction gate = SectTemplates.gate(site.rotation());
		double cx = site.stele().getX() + 0.5D;
		double cz = site.stele().getZ() + 0.5D;
		double y = site.plazaY() + 1.2D;
		return switch (gate) {
			case NORTH -> new Vec3(cx, y, fp.minZ() + 0.5D);
			case EAST -> new Vec3(fp.maxX() + 0.5D, y, cz);
			case WEST -> new Vec3(fp.minX() + 0.5D, y, cz);
			default -> new Vec3(cx, y, fp.maxZ() + 0.5D);
		};
	}

	private static void bossDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		int kind = bossKind(entity);
		if (kind < 0) {
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(entity, source);
		ServerPlayer killer = credit == null ? null : credit.owner();
		boolean first = killer != null && markKill(killer, kind);
		int flame = first ? flameFor(kind) : EpicIds.FLAME_NONE;
		int s = killer == null ? 4 : EpicScale.of(Cultivators.get(killer).rank());
		send(level, EpicIds.BOSS, EpicIds.BOSS_KILL, entity, killer, entity.position(), kind, flame, 0.0F, s,
				EpicIds.FLAG_FINAL | (first ? EpicIds.FLAG_FIRST : 0));
	}

	/** A boss kind of {@code EpicIds.BOSS_*}, or -1. */
	static int bossKind(LivingEntity entity) {
		if (entity.getType() == EntityTypes.WARDEN) {
			return EpicIds.BOSS_WARDEN;
		}
		if (entity.getType() == EntityTypes.WITHER) {
			return EpicIds.BOSS_WITHER;
		}
		if (entity.getType() == EntityTypes.ENDER_DRAGON) {
			return EpicIds.BOSS_DRAGON;
		}
		if (entity.getType() == EntityTypes.ELDER_GUARDIAN) {
			return EpicIds.BOSS_ELDER_GUARDIAN;
		}
		if (entity.entityTags().contains(TechniquePower.TAG_BOSS) && entity.entityTags().contains(TechniquePower.TAG_TRIAL_GUARD)) {
			return EpicIds.BOSS_INHERITANCE;
		}
		// Phase 9 (DESIGN 26.14 item 4): primordial beasts and the Panglima Langit (before the generic phase-8 checks).
		if (entity.entityTags().contains(TechniquePower.TAG_BOSS) && entity.entityTags().contains(PURBA_TAG)) {
			return EpicIds.BOSS_PRIMORDIAL;
		}
		if (entity.entityTags().contains(TechniquePower.TAG_BOSS) && entity.entityTags().contains(TechniquePower.TAG_ABADI_FOE)) {
			return EpicIds.BOSS_HEAVEN_GENERAL;
		}
		// Phase 8 (DESIGN 25.2 item 15): realm guardians and the Demonic Patriarch.
		if (entity.entityTags().contains(TechniquePower.TAG_BOSS) && entity.entityTags().contains(REALM_GUARD_TAG)) {
			return EpicIds.BOSS_REALM_GUARDIAN;
		}
		if (entity.entityTags().contains(TechniquePower.TAG_BOSS) && entity.entityTags().contains(TechniquePower.TAG_ROGUE)) {
			return EpicIds.BOSS_DEMONIC_ELDER;
		}
		return EliteMonsters.tier(entity) == 3 ? EpicIds.BOSS_DEMON_KING : -1;
	}

	/** The Heavenly Flame seed of a first kill of that boss (F1). */
	static int flameFor(int kind) {
		return switch (kind) {
			case EpicIds.BOSS_ELDER_GUARDIAN -> EpicIds.FLAME_SEA_HEART;
			case EpicIds.BOSS_WITHER -> EpicIds.FLAME_FALLING_HEART;
			case EpicIds.BOSS_DRAGON -> EpicIds.FLAME_DEMON_LOTUS;
			default -> EpicIds.FLAME_NONE;
		};
	}

	private static void cheatDeath(ServerPlayer p, DeathGuard.Stage stage) {
		int kind = switch (stage) {
			case TECHNIQUE -> EpicIds.SAVE_TECHNIQUE;
			case PERK -> EpicIds.SAVE_UNBROKEN_SOUL;
			default -> -1;
		};
		if (kind >= 0) {
			send(p.level(), EpicIds.CHEAT_DEATH, EpicIds.CHEAT_SAVED, p, p, p.position(), kind, 0, 0.0F, EpicScale.of(Cultivators.get(p).rank()),
					EpicIds.FLAG_FINAL);
		}
	}

	// ================================================================== sending and the storyboard world sounds

	/** Sends a beat that lands now and plays its world sounds when the cue went out. */
	static void send(ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer, Vec3 pos, int a, int b,
					 float f, int s, int flags) {
		send(level, moment, beat, subject, viewer, pos, level == null ? 0L : level.getGameTime(), a, b, f, s, flags);
	}

	/** Sends a beat that lands at {@code at} and plays its world sounds when the cue went out. */
	static void send(ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer, Vec3 pos, long at, int a,
					 int b, float f, int s, int flags) {
		dev.kultivator.voice.VoiceHooks.onMoment(level, moment, beat, subject, viewer, pos, a, b, flags);
		if (EpicMoments.sendChecked(level, moment, beat, subject, viewer, pos, at, a, b, f, s, flags)) {
			beatSounds(level, moment, beat, pos, s, a, b, flags);
		}
	}

	/**
	 * The world sounds 7b adds to a beat (DESIGN 23.13), on top of what the existing code already plays; together they
	 * stay within 4 per beat. Tests play them as well.
	 */
	static void beatSounds(ServerLevel level, int moment, int beat, Vec3 pos, int s, int a, int b, int flags) {
		boolean last = (flags & EpicIds.FLAG_FINAL) != 0;
		switch (moment) {
			case EpicIds.TRIBULATION -> {
				switch (beat) {
					case EpicIds.TRIB_OMEN -> {
						sound(level, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.WEATHER, 0.8F, 0.5F);
						sound(level, pos, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.WEATHER, 0.8F, 0.55F);
						if (s >= 6) {
							later(level, 160, () -> sound(level, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 0.4F, 0.5F));
						}
					}
					case EpicIds.TRIB_PRE -> {
						if (a >= b) {
							sound(level, pos, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.WEATHER, 0.6F, 0.5F);
							sound(level, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.WEATHER, 0.8F, 0.5F);
						} else {
							sound(level, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.WEATHER, 0.6F, 0.6F);
						}
					}
					case EpicIds.TRIB_STRIKE -> {
						if (last) {
							if (s >= 5) {
								sound(level, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 0.8F, 1.0F);
							} else {
								sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, 0.6F, 0.5F);
							}
							if (s >= 6) {
								sound(level, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.WEATHER, 0.6F, 0.5F);
							}
						} else if (s >= 6) {
							sound(level, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 0.8F, 0.7F);
						}
					}
					case EpicIds.TRIB_END_FAIL -> {
						if ((flags & EpicIds.FLAG_HARDCORE) != 0) {
							sound(level, pos, SoundEvents.GLASS_BREAK, SoundSource.WEATHER, 0.8F, 0.5F);
						} else {
							sound(level, pos, SoundEvents.ANVIL_LAND, SoundSource.WEATHER, 0.5F, 0.5F);
						}
						sound(level, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.WEATHER, 0.8F, 1.0F);
					}
					default -> {
					}
				}
			}
			case EpicIds.BREAKTHROUGH -> {
				if (beat == EpicIds.BT_SUCCESS && EpicIds.nibble(a, 0) == Realm.FOUNDATION.ordinal()) {
					// B1: the nine pillars converge into the dantian 2 s after they rose.
					later(level, 40, () -> sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F));
				}
			}
			case EpicIds.HEART_DEMON -> {
				if (beat == EpicIds.HD_APPEAR) {
					sound(level, pos, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.6F, 0.5F);
				} else if (beat == EpicIds.HD_WON) {
					sound(level, pos, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
				}
			}
			case EpicIds.CALAMITY -> {
				if (beat == EpicIds.CAL_OMEN) {
					sound(level, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.WEATHER, 1.0F, 0.5F);
				}
			}
			case EpicIds.SURPASS -> {
				sound(level, pos, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
				sound(level, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			case EpicIds.RECRUIT -> {
				if (beat == EpicIds.RECRUIT_ARRIVAL) {
					sound(level, pos, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
					later(level, 12, () -> sound(level, pos, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.5F, 0.6F));
				}
			}
			case EpicIds.TOURNAMENT -> {
				if (beat == EpicIds.TOUR_INTRO) {
					sound(level, pos, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
				} else {
					sound(level, pos, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
			}
			case EpicIds.SECT -> {
				if (beat == EpicIds.SECT_TEMPLATE && b >= 9) {
					sound(level, pos, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
					later(level, 20, () -> sound(level, pos, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.NEUTRAL, 1.0F, 1.0F));
				}
			}
			case EpicIds.KEHANCURAN -> {
				if (beat == EpicIds.KEH_IMPACT) {
					sound(level, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 0.5F);
				}
			}
			case EpicIds.BOSS -> sound(level, pos, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.8F, 0.6F);
			default -> {
			}
		}
	}

	private static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, source, volume, pitch);
	}

	/** A sound (or any small action) a few ticks later; not tied to a player. */
	private static void later(ServerLevel level, int ticks, Runnable action) {
		schedule(NOBODY, TAG_MISC, level.getGameTime() + ticks, action);
	}

	// ================================================================== saved milestones (extra.momen)

	/** Sets bit {@code bit} of {@code extra.momen.firsts}; true when it was not set yet (the FIRST flag). */
	static boolean markFirst(ServerPlayer player, int bit) {
		if (bit < 0 || bit > 63) {
			return false;
		}
		CompoundTag momen = momen(player);
		long firsts = momen.getLongOr("firsts", 0L);
		if ((firsts & 1L << bit) != 0L) {
			return false;
		}
		momen.putLong("firsts", firsts | 1L << bit);
		Cultivators.dirty(player);
		return true;
	}

	/** Sets the boss-kind bit of {@code extra.momen.kills}; true on the first kill of that kind. */
	static boolean markKill(ServerPlayer player, int kind) {
		CompoundTag momen = momen(player);
		int kills = momen.getIntOr("kills", 0);
		if ((kills & 1 << kind) != 0) {
			return false;
		}
		momen.putInt("kills", kills | 1 << kind);
		Cultivators.dirty(player);
		return true;
	}

	private static CompoundTag momen(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag momen = extra.getCompound(SAVED_KEY).orElse(null);
		if (momen == null) {
			momen = new CompoundTag();
			extra.put(SAVED_KEY, momen);
		}
		return momen;
	}

	private static int playerMilestone(Rank rank) {
		return rank.realm() == Realm.IMMORTAL_ASCENSION ? FIRST_IA_LAYER + rank.layer() : rank.realm().ordinal();
	}

	/** Disciple milestones (E1): Golden Core, Nascent Soul, Deity Transformation, Dao Union, IA L1, IA L9, True Immortal. */
	private static int discipleMilestone(Rank rank) {
		return switch (rank.realm()) {
			case GOLDEN_CORE -> 0;
			case NASCENT_SOUL -> 1;
			case DEITY_TRANSFORMATION -> 2;
			case DAO_UNION -> 3;
			case IMMORTAL_ASCENSION -> rank.layer() == 0 ? 4 : rank.layer() == 8 ? 5 : -1;
			case TRUE_IMMORTAL -> 6;
			default -> -1;
		};
	}

	// ================================================================== timers

	static void schedule(UUID owner, int tag, long when, Runnable action) {
		TIMERS.add(new Timer(owner, tag, when, action));
	}

	/** Drops the owner's timers with {@code tag} (0 = every tag). */
	static void cancelTimers(UUID owner, int tag) {
		TIMERS.removeIf(timer -> timer.owner().equals(owner) && (tag == 0 || timer.tag() == tag));
	}

	private static void tick(MinecraftServer current) {
		if (!TIMERS.isEmpty()) {
			long now = current.overworld().getGameTime();
			List<Timer> due = null;
			for (Iterator<Timer> it = TIMERS.iterator(); it.hasNext(); ) {
				Timer timer = it.next();
				if (timer.when() <= now) {
					it.remove();
					if (due == null) {
						due = new ArrayList<>();
					}
					due.add(timer);
				}
			}
			if (due != null) {
				for (Timer timer : due) {
					guard(timer.action());
				}
			}
		}
		EpicTests.tick(current);
	}

	// ================================================================== small helpers

	private static int test(boolean test) {
		return test ? EpicIds.FLAG_TEST : 0;
	}

	private static @Nullable ServerPlayer playerOf(UUID id) {
		MinecraftServer current = server;
		return current == null ? null : current.getPlayerList().getPlayer(id);
	}

	private static @Nullable ServerPlayer ownerOf(@Nullable LivingEntity entity) {
		return entity instanceof DiscipleEntity disciple ? disciple.owner() : null;
	}

	/** Runs a presentation step; anything it throws is logged and swallowed, so a hook never breaks its caller. */
	private static void guard(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Epic moment hook failed", exception);
		}
	}

	private EpicHooks() {
	}
}
