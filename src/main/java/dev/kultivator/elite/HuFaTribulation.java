package dev.kultivator.elite;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hu Fa Tribulasi, players who guard another player's tribulation (DESIGN 19.3.8; BTTH: Yao Lao guards Xiao Yan, 护法;
 * RMJ ch. 1803: a lightning cultivator assists others through their tribulations; NSHBA ch. 6594 supports that strikes
 * hit only the cultivator). Sect mates are invited when the omen begins; anyone within 24 blocks when the first strike
 * lands joins by itself; {@code /kultivasi kawal <player>} joins from 64 blocks. Guardians within 16 blocks soften each
 * strike (×0.95, ×0.90 with the Lightning Dao, all together ≥ 0.85), add +0.04 quality each at the end (≤ +0.08), draw
 * one extra protector each (≤ 3) and are rewarded with 10% of their own bar (half on a failure). The factors are game
 * design. Strikes never touch a guardian; disciples count for protector targeting only.
 */
public final class HuFaTribulation {

	public static final double JOIN_RANGE = 64.0D;
	public static final double AUTO_RANGE = 24.0D;
	/** Guardians within this range of the cultivator count for strikes, quality and threads. */
	public static final double ACTIVE_RANGE = 16.0D;
	public static final double LOSE_RANGE = 40.0D;
	public static final long LOSE_TICKS = 200L;
	public static final double GUARD_FACTOR = 0.95D;
	public static final double LIGHTNING_GUARD_FACTOR = 0.90D;
	public static final double GUARD_FLOOR = 0.85D;
	public static final double QUALITY_PER_GUARD = 0.04D;
	public static final double QUALITY_MAX = 0.08D;
	public static final int MAX_EXTRA_PROTECTORS = 3;
	public static final double REWARD_SHARE = 0.10D;
	public static final double FAILURE_SHARE = 0.5D;
	/** One reward per guardian–cultivator pair every 20 minutes ({@code extra.combat.guard_last}). */
	public static final long REWARD_COOLDOWN = 24000L;
	public static final int GREAT_GUARDS = 5;
	public static final int MAX_FAKE = 3;
	/** Sect mates are invited from this target up. */
	public static final Realm INVITE_FROM = Realm.GOLDEN_CORE;
	private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/** The guardians of one cultivator's tribulation. */
	private static final class Session {
		final Rank target;
		final boolean test;
		final Set<UUID> guards = new LinkedHashSet<>();
		final Map<UUID, Long> farSince = new HashMap<>();
		int fake;

		Session(Rank target, boolean test) {
			this.target = target;
			this.test = test;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** {@code uji kawal <n>}: pretended guardians for the player's next test tribulation. */
	private static final Map<UUID, Integer> PENDING_FAKE = new HashMap<>();

	static void init() {
		Tribulation.addListener(new Tribulation.Listener() {
			@Override
			public void onOmen(ServerPlayer p, Rank t) {
				open(p, t);
			}

			@Override
			public void onFinish(ServerPlayer p, Rank t, @Nullable Tribulation.Result result, int strikesDone, boolean test) {
				close(p, result, test);
			}
		});
		Tribulation.addStrikeFactorProvider((p, target, strikeIndex, last) -> strikeFactor(p));
		Breakthrough.addQualityBonusProvider((p, d, target) -> qualityBonus(p));
		ServerPlayerEvents.LEAVE.register(player -> {
			PENDING_FAKE.remove(player.getUUID());
			drop(player, null);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			PENDING_FAKE.clear();
		});
	}

	// ================================================================== public API

	/** Guardians currently registered for {@code cultivator}'s tribulation (0 without one; pretended ones included). */
	public static int guards(ServerPlayer cultivator) {
		Session session = SESSIONS.get(cultivator.getUUID());
		return session == null ? 0 : session.guards.size() + session.fake;
	}

	/** Extra Dao Protectors per wave: one per guardian, at most {@link #MAX_EXTRA_PROTECTORS}. */
	public static int extraProtectors(ServerPlayer cultivator) {
		return Math.min(MAX_EXTRA_PROTECTORS, guards(cultivator));
	}

	/**
	 * Who a new protector may pick first (DESIGN 19.3.8): the cultivator, the guardians and the cultivator's loaded
	 * disciples within 16 blocks.
	 */
	public static List<LivingEntity> protectorTargets(ServerPlayer cultivator) {
		List<LivingEntity> list = new ArrayList<>();
		list.add(cultivator);
		Session session = SESSIONS.get(cultivator.getUUID());
		if (session != null) {
			list.addAll(present(session, cultivator));
		}
		double rangeSq = ACTIVE_RANGE * ACTIVE_RANGE;
		for (DiscipleEntity disciple : Disciples.loaded(cultivator.getUUID())) {
			if (disciple.level() == cultivator.level() && disciple.isAlive() && disciple.distanceToSqr(cultivator) <= rangeSq) {
				list.add(disciple);
			}
		}
		return list;
	}

	/** The strike factor of the guardians present (pure; 1 without a session). */
	public static double strikeFactor(ServerPlayer cultivator) {
		Session session = SESSIONS.get(cultivator.getUUID());
		if (session == null) {
			return 1.0D;
		}
		double factor = 1.0D;
		for (ServerPlayer guardian : present(session, cultivator)) {
			factor *= Cultivators.get(guardian).dao() == Dao.LIGHTNING ? LIGHTNING_GUARD_FACTOR : GUARD_FACTOR;
		}
		for (int i = 0; i < session.fake; i++) {
			factor *= GUARD_FACTOR;
		}
		return Math.max(GUARD_FLOOR, factor);
	}

	/** The quality bonus {@code count} guardians present at the end give (pure; the Persiapan forecast reads it). */
	public static double qualityFor(int count) {
		return Math.min(QUALITY_MAX, QUALITY_PER_GUARD * Math.max(0, count));
	}

	/** +0.04 per guardian present (max +0.08); pure, 0 without a session. */
	public static double qualityBonus(ServerPlayer cultivator) {
		Session session = SESSIONS.get(cultivator.getUUID());
		if (session == null) {
			return 0.0D;
		}
		return qualityFor(present(session, cultivator).size() + session.fake);
	}

	/** {@code uji kawal <n>}: pretends {@code n} guardians for the running (or next) test tribulation. */
	public static void setFake(ServerPlayer player, int n) {
		int clamped = Math.max(0, Math.min(MAX_FAKE, n));
		Session session = SESSIONS.get(player.getUUID());
		if (session != null && session.test) {
			session.fake = clamped;
			Tribulation.refreshHud(player);
		} else {
			PENDING_FAKE.put(player.getUUID(), clamped);
		}
	}

	/** {@code /kultivasi kawal <player>}: {@code guardian} becomes the cultivator's Hu Fa. True when registered. */
	public static boolean join(ServerPlayer guardian, ServerPlayer cultivator) {
		if (guardian == cultivator) {
			return refuse(guardian, "kultivator.combat.hufa.fail.self", cultivator);
		}
		if (dev.kultivator.alam.Alam.blocked(guardian) || dev.kultivator.alam.Alam.blocked(cultivator)) {
			// Phase 8 (DESIGN 25.2 item 12): no Hu Fa duty in or from a secret realm.
			return refuse(guardian, dev.kultivator.alam.Alam.REFUSE_KEY, cultivator);
		}
		Session session = SESSIONS.get(cultivator.getUUID());
		if (session == null || !Tribulation.isActive(cultivator)) {
			return refuse(guardian, "kultivator.combat.hufa.fail.none", cultivator);
		}
		if (guardian.level() != cultivator.level() || guardian.distanceToSqr(cultivator) > JOIN_RANGE * JOIN_RANGE) {
			return refuse(guardian, "kultivator.combat.hufa.fail.far", cultivator);
		}
		if (!guardian.isAlive() || guardian.isSpectator() || Breakthrough.isBusy(guardian) || Cultivators.get(guardian).breakingThrough()) {
			return refuse(guardian, "kultivator.combat.hufa.fail.busy", cultivator);
		}
		if (session.guards.contains(guardian.getUUID())) {
			Msg.chat(guardian, Component.translatable("kultivator.combat.hufa.fail.already", cultivator.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		register(session, guardian, cultivator);
		return true;
	}

	/** Before strike {@code number}: the auto-join of strike 1 and the gold threads (called by {@code Tribulation}). */
	public static void beforeStrike(ServerPlayer cultivator, int number) {
		Session session = SESSIONS.get(cultivator.getUUID());
		if (session == null) {
			return;
		}
		if (number == 1) {
			double rangeSq = AUTO_RANGE * AUTO_RANGE;
			for (ServerPlayer other : cultivator.level().players()) {
				if (other != cultivator && other.isAlive() && !other.isSpectator() && other.distanceToSqr(cultivator) <= rangeSq
						&& !session.guards.contains(other.getUUID()) && !Breakthrough.isBusy(other)) {
					register(session, other, cultivator);
				}
			}
		}
		Vec3 heart = cultivator.position().add(0.0D, 1.1D, 0.0D);
		for (ServerPlayer guardian : present(session, cultivator)) {
			Fx.send(cultivator.level(), guardian.position().add(0.0D, 1.1D, 0.0D), heart, FxType.ELITE, CombatFxIds.THREAD, 0, 0.0F);
		}
	}

	/** A player died: they stop guarding anyone (DESIGN 19.3.8). */
	static void onPlayerDeath(ServerPlayer player) {
		drop(player, "kultivator.combat.hufa.lost.cultivator");
	}

	/** Every 20 ticks: guardians who left, strayed beyond 40 blocks for 10 s or began their own breakthrough lose the role. */
	static void check(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
			ServerPlayer cultivator = server.getPlayerList().getPlayer(entry.getKey());
			Session session = entry.getValue();
			if (cultivator == null || session.guards.isEmpty()) {
				continue;
			}
			boolean changed = false;
			for (UUID id : List.copyOf(session.guards)) {
				ServerPlayer guardian = server.getPlayerList().getPlayer(id);
				String reason = null;
				if (guardian == null || guardian.isDeadOrDying()) {
					reason = "";
				} else if (Breakthrough.isBusy(guardian)) {
					reason = "kultivator.combat.hufa.lost.busy";
				} else if (guardian.level() != cultivator.level() || guardian.distanceToSqr(cultivator) > LOSE_RANGE * LOSE_RANGE) {
					Long since = session.farSince.putIfAbsent(id, now);
					if (since != null && now - since >= LOSE_TICKS) {
						reason = "kultivator.combat.hufa.lost.far";
					}
				} else {
					session.farSince.remove(id);
				}
				if (reason != null) {
					session.guards.remove(id);
					session.farSince.remove(id);
					changed = true;
					if (guardian != null && !reason.isEmpty()) {
						Msg.chat(guardian, Component.translatable(reason, cultivator.getDisplayName()).withStyle(ChatFormatting.GRAY));
						Msg.chat(cultivator, Component.translatable("kultivator.combat.hufa.lost.cultivator", guardian.getDisplayName())
								.withStyle(ChatFormatting.GRAY));
					}
				}
			}
			if (changed) {
				Tribulation.refreshHud(cultivator);
			}
		}
	}

	// ================================================================== internals

	private static void open(ServerPlayer cultivator, Rank target) {
		boolean test = Tribulation.isTest(cultivator);
		Session session = new Session(target, test);
		Integer fake = PENDING_FAKE.remove(cultivator.getUUID());
		if (test && fake != null) {
			session.fake = fake;
		}
		// The cultivator no longer guards anyone else.
		drop(cultivator, "kultivator.combat.hufa.lost.cultivator");
		SESSIONS.put(cultivator.getUUID(), session);
		if (!test && target.realm().atLeast(INVITE_FROM)) {
			invite(cultivator, target);
		}
	}

	private static void close(ServerPlayer cultivator, @Nullable Tribulation.Result result, boolean test) {
		Session session = SESSIONS.remove(cultivator.getUUID());
		if (session == null || test || session.test || result == null || result == Tribulation.Result.CANCELLED || session.guards.isEmpty()) {
			return;
		}
		boolean success = result == Tribulation.Result.SUCCESS;
		MinecraftServer server = cultivator.level().getServer();
		long now = server.overworld().getGameTime();
		for (UUID id : session.guards) {
			ServerPlayer guardian = server.getPlayerList().getPlayer(id);
			if (guardian == null || !guardian.isAlive()) {
				continue;
			}
			CultivationData data = Cultivators.get(guardian);
			CompoundTag combat = BattleFatigue.section(data);
			CompoundTag last = combat.getCompoundOrEmpty("guard_last");
			String key = cultivator.getUUID().toString();
			long before = last.getLongOr(key, Long.MIN_VALUE);
			if (before != Long.MIN_VALUE && now >= before && now - before < REWARD_COOLDOWN) {
				Msg.chat(guardian, Component.translatable("kultivator.combat.hufa.reward.cooldown", cultivator.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			CompoundTag kept = new CompoundTag();
			for (String other : last.keySet()) {
				long at = last.getLongOr(other, 0L);
				if (now >= at && now - at < REWARD_COOLDOWN) {
					kept.putLong(other, at);
				}
			}
			kept.putLong(key, now);
			combat.put("guard_last", kept);
			double xp = data.rank().xpRequired() * REWARD_SHARE * (success ? 1.0D : FAILURE_SHARE);
			double gained = Progression.grantXp(guardian, xp, XpSource.SECT);
			if (success) {
				int guards = combat.getIntOr("guards", 0) + 1;
				combat.putInt("guards", guards);
				Advancements.grant(guardian, "hu_fa_tribulation");
				if (guards >= GREAT_GUARDS) {
					Advancements.grant(guardian, "great_hu_fa");
				}
			}
			Cultivators.dirty(guardian);
			Msg.chat(guardian, Component.translatable(success ? "kultivator.combat.hufa.reward.success" : "kultivator.combat.hufa.reward.failed",
					cultivator.getDisplayName(), Msg.number(gained)).withStyle(success ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
		}
	}

	private static void invite(ServerPlayer cultivator, Rank target) {
		Sect sect = Sects.sectOf(cultivator);
		if (sect == null) {
			return;
		}
		String command = "/kultivasi kawal " + cultivator.getScoreboardName();
		for (ServerPlayer mate : Sects.onlineMembers(cultivator.level().getServer(), sect)) {
			if (mate == cultivator || mate.level() != cultivator.level()) {
				continue;
			}
			double dx = cultivator.getX() - mate.getX();
			double dz = cultivator.getZ() - mate.getZ();
			int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			int index = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8L);
			Component direction = Component.translatable("kultivator.combat.hufa.dir." + DIRECTIONS[index]);
			MutableComponent button = Component.translatable("kultivator.combat.hufa.invite.button").withStyle(style -> style
					.withColor(ChatFormatting.GOLD).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("kultivator.combat.hufa.invite.hover", (int) JOIN_RANGE))));
			MutableComponent line = Component.translatable("kultivator.combat.hufa.invite", cultivator.getDisplayName(), target.displayName(),
					distance, direction).withStyle(ChatFormatting.AQUA);
			Msg.chat(mate, line.append(Component.literal(" ")).append(button));
		}
	}

	private static void register(Session session, ServerPlayer guardian, ServerPlayer cultivator) {
		session.guards.add(guardian.getUUID());
		session.farSince.remove(guardian.getUUID());
		Msg.chat(guardian, Component.translatable("kultivator.combat.hufa.joined.guard", cultivator.getDisplayName(), (int) ACTIVE_RANGE)
				.withStyle(ChatFormatting.GOLD));
		Msg.chat(cultivator, Component.translatable("kultivator.combat.hufa.joined.cultivator", guardian.getDisplayName()).withStyle(ChatFormatting.GOLD));
		Fx.send(guardian.level(), guardian.position().add(0.0D, 1.0D, 0.0D), FxType.ELITE, CombatFxIds.GUARD_JOIN, 0, 1.0F);
		guardian.level().playSound(null, guardian.getX(), guardian.getY(), guardian.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				0.8F, 1.2F);
		Tribulation.refreshHud(cultivator);
	}

	/** Guardians of {@code session} online, alive, in the cultivator's level and within {@link #ACTIVE_RANGE}. */
	private static List<ServerPlayer> present(Session session, ServerPlayer cultivator) {
		List<ServerPlayer> list = new ArrayList<>();
		if (session.guards.isEmpty()) {
			return list;
		}
		MinecraftServer server = cultivator.level().getServer();
		double rangeSq = ACTIVE_RANGE * ACTIVE_RANGE;
		for (UUID id : session.guards) {
			ServerPlayer guardian = server.getPlayerList().getPlayer(id);
			if (guardian != null && guardian.isAlive() && guardian.level() == cultivator.level() && guardian.distanceToSqr(cultivator) <= rangeSq) {
				list.add(guardian);
			}
		}
		return list;
	}

	/** Removes {@code player} from every session as a guardian ({@code messageKey} tells the cultivator, null = quiet). */
	private static void drop(ServerPlayer player, @Nullable String messageKey) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
			if (entry.getValue().guards.remove(player.getUUID())) {
				entry.getValue().farSince.remove(player.getUUID());
				ServerPlayer cultivator = server.getPlayerList().getPlayer(entry.getKey());
				if (cultivator != null) {
					if (messageKey != null) {
						Msg.chat(cultivator, Component.translatable(messageKey, player.getDisplayName()).withStyle(ChatFormatting.GRAY));
					}
					Tribulation.refreshHud(cultivator);
				}
			}
		}
	}

	private static boolean refuse(ServerPlayer guardian, String key, ServerPlayer cultivator) {
		Msg.chat(guardian, Component.translatable(key, cultivator.getDisplayName()).withStyle(ChatFormatting.RED));
		return false;
	}

	private HuFaTribulation() {
	}
}
