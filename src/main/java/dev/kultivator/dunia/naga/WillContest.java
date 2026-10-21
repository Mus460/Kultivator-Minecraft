package dev.kultivator.dunia.naga;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pertarungan Kehendak, the contest of wills (DESIGN 34.5.11 "Subdue"). The Selendang Pengikat Naga used on a Lemah Jiao
 * by a player of Deity Transformation+ (Jiao Petir: Dao Union+) pins the Jiao for 8 s: the client shows a ring on which
 * the dragon's will (an arc) wanders, and the player keeps their own arc on it by holding use and turning the view; Qi
 * drains 3 % of its maximum per second. The client reports its overlap every 10 ticks ({@link NagaPayloads.Report}); the
 * server checks the session, the time and the distance, counts missing reports as zero and at the end needs 60 %. Won:
 * the Jiao is the player's ({@link TamedJiao#tame}). Lost: it recovers 30 % of its health and flees. A Selendang is
 * spent on every attempt (never in creative or a test).
 */
final class WillContest {

	public static final int TICKS = 160;
	public static final double WIN_AT = 0.6D;
	public static final double QI_PER_SECOND = 0.03D;
	public static final double MAX_DISTANCE = 32.0D;
	/** Late reports after the end are still taken for this long. */
	private static final int GRACE = 12;

	private static final class Session {
		final int id;
		final UUID player;
		final NagaLiarEntity dragon;
		final int seed;
		final @Nullable Boolean forced;
		int age;
		double covered;
		int samples;
		int lastReportAge = -100;

		Session(int id, UUID player, NagaLiarEntity dragon, int seed, @Nullable Boolean forced) {
			this.id = id;
			this.player = player;
			this.dragon = dragon;
			this.seed = seed;
			this.forced = forced;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static int nextId = 1;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WillContest::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	/** True while {@code player} binds a Jiao. */
	static boolean binding(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** The player binding {@code dragon}, or null. */
	static @Nullable ServerPlayer binderOf(NagaLiarEntity dragon) {
		for (Session session : SESSIONS.values()) {
			if (session.dragon == dragon && dragon.level() instanceof ServerLevel level) {
				return level.getServer().getPlayerList().getPlayer(session.player);
			}
		}
		return null;
	}

	/**
	 * The Selendang on a Jiao: checks everything and starts the contest. {@code forced} (tests) skips the realm, the
	 * Lemah state and the Selendang, and fixes the result. True when the use was handled (also when refused).
	 */
	static boolean begin(ServerPlayer player, NagaLiarEntity dragon, @Nullable ItemStack selendang, @Nullable Boolean forced) {
		NagaConfig config = Naga.config();
		if (dragon.isTamed() || !dragon.isAlive()) {
			return false;
		}
		if (forced == null) {
			if (!config.subdue) {
				Msg.actionBar(player, Component.translatable("kultivator.naga.subdue.off").withStyle(ChatFormatting.GRAY));
				return true;
			}
			if (!dragon.hasFlag(NagaLiarEntity.FLAG_LEMAH)) {
				Msg.actionBar(player, Component.translatable("kultivator.naga.subdue.not_weak", dragon.kind().displayName())
						.withStyle(ChatFormatting.YELLOW));
				return true;
			}
			Realm needed = dragon.kind().subdueRealm;
			if (!Cultivators.get(player).realm().atLeast(needed)) {
				Msg.actionBar(player, Component.translatable("kultivator.naga.subdue.realm", needed.displayName()).withStyle(ChatFormatting.RED));
				return true;
			}
		}
		if (TamedJiao.get(player) != null) {
			Msg.actionBar(player, Component.translatable("kultivator.naga.subdue.have_one").withStyle(ChatFormatting.YELLOW));
			return true;
		}
		if (binding(player) || binderOf(dragon) != null) {
			return true;
		}
		if (forced == null && selendang != null && !player.isCreative()) {
			selendang.consume(1, player);
		}
		dragon.setMode(NagaLiarEntity.Mode.CONTEST);
		int seed = player.getRandom().nextInt();
		Session session = new Session(nextId++, player.getUUID(), dragon, seed, forced);
		SESSIONS.put(player.getUUID(), session);
		ServerLevel level = player.level();
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
		NagaPayloads.send(player, new NagaPayloads.Contest(session.id, dragon.getId(), dragon.kind().ordinal(), seed, TICKS,
				NagaPayloads.CONTEST_START, arcDegrees(dragon.kind())));
		Msg.chat(player, Component.translatable("kultivator.naga.contest.start", dragon.kind().displayName()).withColor(dragon.kind().glow));
		Msg.actionBar(player, Component.translatable("kultivator.naga.contest.hint").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** The width of the dragon's will arc (degrees): wider is easier; the thunder Jiao is the hardest. */
	static int arcDegrees(NagaKind kind) {
		return switch (kind) {
			case AIR -> 56;
			case AWAN -> 50;
			case PETIR -> 42;
		};
	}

	static void onReport(ServerPlayer player, NagaPayloads.Report report) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || report.session() != session.id) {
			return;
		}
		int samples = Math.max(0, Math.min(12, report.samples()));
		if (samples == 0 || session.age - session.lastReportAge < 7 || session.samples + samples > TICKS + 20) {
			return;
		}
		session.lastReportAge = session.age;
		double permille = Math.max(0, Math.min(1000, report.permille())) / 1000.0D;
		session.covered += permille * samples;
		session.samples += samples;
	}

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
		while (iterator.hasNext()) {
			Session session = iterator.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(session.player);
			NagaLiarEntity dragon = session.dragon;
			session.age++;
			String abort = null;
			if (player == null || !player.isAlive()) {
				abort = "gone";
			} else if (dragon.isRemoved() || !dragon.isAlive() || dragon.level() != player.level()) {
				abort = "dragon";
			} else if (player.distanceToSqr(dragon) > MAX_DISTANCE * MAX_DISTANCE) {
				abort = "far";
			}
			if (abort == null && session.age % 20 == 0 && session.age <= TICKS && session.forced == null && !player.isCreative()) {
				CultivationData data = Cultivators.get(player);
				double cost = Qi.max(data) * QI_PER_SECOND;
				if (data.qi() < cost) {
					abort = "qi";
				} else {
					data.setQi(data.qi() - cost);
					Cultivators.dirty(player);
				}
			}
			if (abort != null) {
				iterator.remove();
				fail(server, player, dragon, session, "kultivator.naga.contest.abort." + abort);
				continue;
			}
			ServerLevel level = player.level();
			if (session.age % 4 == 0) {
				// the silk tether of the Selendang between the hand and the dragon (particles)
				Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6D)).add(0.0D, -0.4D, 0.0D);
				Fx.beam(level, hand, dragon.position().add(0.0D, 0.6D, 0.0D), 0xFFD27A, 0.06F);
			}
			if (session.age % 40 == 0 && session.age < TICKS) {
				level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.HOSTILE, 1.5F,
						0.8F + 0.1F * (session.age / 40));
			}
			if (session.age < TICKS + GRACE) {
				continue;
			}
			iterator.remove();
			double overlap = session.forced != null ? (session.forced ? 1.0D : 0.2D)
					: session.covered / Math.max(session.samples, TICKS * 0.85D);
			int permille = (int) Math.round(Math.max(0.0D, Math.min(1.0D, overlap)) * 1000.0D);
			if (overlap >= WIN_AT) {
				NagaPayloads.send(player, new NagaPayloads.Contest(session.id, dragon.getId(), dragon.kind().ordinal(), session.seed, 0,
						NagaPayloads.CONTEST_WON, permille));
				Msg.actionBar(player, Component.translatable("kultivator.naga.contest.won", permille / 10).withStyle(ChatFormatting.GOLD));
				TamedJiao.tame(player, dragon);
			} else {
				NagaPayloads.send(player, new NagaPayloads.Contest(session.id, dragon.getId(), dragon.kind().ordinal(), session.seed, 0,
						NagaPayloads.CONTEST_LOST, permille));
				fail(server, player, dragon, session, null);
				Msg.chat(player, Component.translatable("kultivator.naga.contest.lost", permille / 10, (int) (WIN_AT * 100.0D))
						.withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void fail(MinecraftServer server, @Nullable ServerPlayer player, NagaLiarEntity dragon, Session session, @Nullable String reason) {
		if (player != null && reason != null) {
			NagaPayloads.send(player, new NagaPayloads.Contest(session.id, dragon.getId(), dragon.kind().ordinal(), session.seed, 0,
					NagaPayloads.CONTEST_ABORT, 0));
			Msg.actionBar(player, Component.translatable(reason).withStyle(ChatFormatting.RED));
		}
		if (!dragon.isRemoved() && dragon.isAlive() && dragon.level() instanceof ServerLevel level) {
			dragon.recoverAndFlee(level);
		}
	}

	/** {@code uji naga tunduk menang|kalah}: ends the player's contest now with that result. False without one. */
	static boolean forceEnd(ServerPlayer player, boolean win) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null) {
			return false;
		}
		Session decided = new Session(session.id, session.player, session.dragon, session.seed, win);
		decided.age = TICKS + GRACE - 1;
		SESSIONS.put(player.getUUID(), decided);
		return true;
	}

	/** Drops the player's contest (logout, {@code uji naga hapus}); the pinned Jiao is released and flees, never left stuck. */
	static void clearFor(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session != null && !session.dragon.isRemoved() && session.dragon.isAlive() && session.dragon.level() instanceof ServerLevel level) {
			session.dragon.recoverAndFlee(level);
		}
	}

	static List<String> describe() {
		List<String> lines = new java.util.ArrayList<>();
		for (Session session : SESSIONS.values()) {
			lines.add(session.id + ": " + session.dragon.kind().id() + " age " + session.age + " overlap "
					+ Math.round(session.covered / Math.max(1, session.samples) * 100.0D) + "%");
		}
		return lines;
	}

	private WillContest() {
	}
}
