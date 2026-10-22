package dev.kultivator.sekte.turnamen;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.sect.event.Tournament;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.TurnamenHasil;
import dev.kultivator.sekte.api.TurnamenService;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Module T, Turnamen sekte (DESIGN 36.2.3, butir 39): the sect tournament moves onto the arena's lei tai with a tribune
 * and a crowd, each bout becomes a Sword Duel, every disciple gets an Elo rating and a record across numbered seasons, and
 * NPC sects can challenge the hall to an inter-sect tournament. Built on {@code sect.event.Tournament} (its schedule,
 * bracket, rewards and the Elite every third are unchanged) through {@link Tournament.Extension}, on the Sword Duel
 * through {@code duel.api.DuelEndListeners}, and on the sect build engine through {@link TribunKomponen}.
 * <p>
 * Contracts (DESIGN 36.4): C36.T1 {@code SekteApi.setTurnamen}; C36.T2 fires {@code TOURNAMENT_BOUT},
 * {@code TOURNAMENT_ENDED} and {@code DISCIPLE_HONORED} (champion 8, losing finalist 3), listens to
 * {@code RIVAL_SECT_ACTION(TANTANGAN)}; C36.T3 {@code DuelEndListeners}; C36.T4 component {@code sx_turnamen_tribun};
 * C36.T5 the old numbers stay.
 * <p>
 * Sumber: the sources of {@code Tournament} (Battle Through the Heavens ch. 566-573, the Strong Ranking; A Will Eternal,
 * the Chosen battles; I Shall Seal the Heavens ch. 27-31, the final announced by the Grand Elder); the lei tai stage of
 * Chinese martial contests; the Elo rating of chess (Arpad Elo, 1960) as the public standing of the board.
 */
public final class Turnamen implements TurnamenService, Tournament.Extension {

	/** Scene beats of module T (EXTRA 120-129). */
	public static final int BEAT_FINAL = EpicIds.EXTRA_SEKTE_FIRST + 20;
	public static final int BEAT_JUARA = EpicIds.EXTRA_SEKTE_FIRST + 21;
	public static final int BEAT_TAMU = EpicIds.EXTRA_SEKTE_FIRST + 22;

	/** Players within this distance of the stage see the Lingkar Lei Tai. */
	public static final double SYNC_RANGE = 96.0D;
	public static final int CHAMPION_HONOUR = 8;
	public static final int FINALIST_HONOUR = 3;
	/** The crowd stays this long after the champion is named (the scene and the cheering). */
	public static final int LINGER_TICKS = 140;

	static final Turnamen INSTANCE = new Turnamen();

	/** The live state of one hall's running tournament (session data only). */
	private static final class Live {
		final UUID owner;
		final ServerLevel level;
		final Vec3 venue;
		final boolean stage;
		final String kind;
		final int season;
		final @Nullable String guest;
		final Set<UUID> viewers = new HashSet<>();
		int bout;
		boolean fighting;
		boolean finalBout;
		boolean champion;
		Component nameA = Component.empty();
		Component nameB = Component.empty();
		int linger = -1;

		Live(UUID owner, ServerLevel level, Vec3 venue, boolean stage, String kind, int season, @Nullable String guest) {
			this.owner = owner;
			this.level = level;
			this.venue = venue;
			this.stage = stage;
			this.kind = kind;
			this.season = season;
			this.guest = guest;
		}
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static boolean initialised;
	private static long ticks;

	/** From {@code SekteContent.init} (both physical sides), before {@code ModItems.init()}. */
	public static void init() {
		if (initialised || !SekteContent.on(SekteIds.T)) {
			return;
		}
		initialised = true;
		TurnamenConfig.load();
		TurnamenEntities.init();
		TurnamenPayloads.init();
		TribunKomponen.register();
		BabakDuel.init();
		Tournament.setExtension(INSTANCE);
		SekteApi.setTurnamen(INSTANCE);
		SekteBus.RIVAL_SECT_ACTION.register((server, owner, sect, action, magnitude) -> {
			if (owner != null && action == AksiSekte.TANTANGAN) {
				AntarSekte.challenge(server, owner, sect);
			}
		});
		// A disciple who left the sect leaves the season board too (its record stays on its own data).
		SekteBus.DISCIPLE_LEFT.register((server, owner, soul, name, how, toSect, companion) -> Musim.forget(owner, soul));
		ServerTickEvents.END_SERVER_TICK.register(Turnamen::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		ServerPlayNetworking.registerGlobalReceiver(TurnamenPayloads.Minta.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> onAsk(player, payload.action()));
		});
		TurnamenCommands.init();
	}

	// ================================================================== TurnamenService (C36.T1)

	@Override
	public boolean running(UUID owner) {
		return Tournament.running(owner);
	}

	@Override
	public @Nullable UUID champion(UUID owner) {
		return Musim.reigning(owner);
	}

	@Override
	public boolean hostGuests(ServerPlayer owner, String guestSect) {
		return TurnamenConfig.get().antarSekte && guestSect != null && AntarSekte.host(owner, guestSect, false);
	}

	@Override
	public boolean busy(UUID owner, UUID soul) {
		return Tournament.isEntrant(owner, soul);
	}

	// ================================================================== Tournament.Extension (C36.T5)

	@Override
	public @Nullable Vec3 venue(UUID owner, ServerLevel level, Vec3 stele) {
		return ArenaTempat.venue(owner, level, stele);
	}

	@Override
	public List<BlockPos> seats(UUID owner, ServerLevel level, Vec3 venue) {
		return ArenaTempat.seats(owner, level, venue);
	}

	@Override
	public boolean startBout(UUID owner, ServerLevel level, Vec3 venue, DiscipleEntity a, DiscipleEntity b, boolean finalBout,
			Tournament.BoutDone done) {
		boolean duel = BabakDuel.start(owner, a, b, finalBout, done);
		Live live = LIVE.get(owner);
		if (live != null) {
			live.bout++;
			live.fighting = true;
			live.finalBout = finalBout;
			live.nameA = a.getDisplayName();
			live.nameB = b.getDisplayName();
			sync(live, true);
		}
		Kerumunan.watch(owner, a, b);
		if (finalBout) {
			if (duel) {
				// The duel's own intro film holds the camera: only the "Babak Final" stamp here.
				scene(level, BEAT_FINAL, a, owner, venue, live == null ? 0 : live.season);
			} else {
				EpicHooks.tournament(level, EpicIds.TOUR_INTRO, kindCode(live), a, b, venue);
			}
			level.playSound(null, venue.x, venue.y, venue.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 0.5F, 1.2F);
		}
		return duel;
	}

	@Override
	public void cancelBout(UUID owner) {
		BabakDuel.cancel(owner);
	}

	@Override
	public void started(UUID owner, ServerLevel level, Vec3 venue, String kind, int season, List<UUID> entrants) {
		String guest = Tournament.KIND_GUESTS.equals(kind) ? AntarSekte.hosting(owner) : null;
		boolean stage = ArenaTempat.hasTribune(owner) || venue.distanceToSqr(stele(owner, venue)) > 4.0D;
		Live live = new Live(owner, level, venue, stage, kind, season, guest);
		Live old = LIVE.put(owner, live);
		if (old != null) {
			off(old);
		}
		Musim.setSeason(owner, season);
		List<BlockPos> seats = ArenaTempat.seats(owner, level, venue);
		Kerumunan.gather(owner, level, venue, seats, new HashSet<>(entrants), guest);
		sync(live, true);
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player != null && stage) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.mulai_leitai", season).withStyle(ChatFormatting.GOLD));
		}
		for (UUID soul : entrants) {
			DiscipleEntity body = body(owner, soul);
			if (body != null) {
				Elo.season(body.data(), 0, season);
			}
		}
	}

	@Override
	public void bout(UUID owner, ServerLevel level, @Nullable UUID winner, @Nullable UUID loser, Component winnerName, Component loserName,
			int winnerPower, int loserPower, boolean upset, boolean finalBout, boolean bronze) {
		MinecraftServer server = level.getServer();
		DiscipleEntity w = winner == null ? null : body(owner, winner);
		DiscipleEntity l = loser == null ? null : body(owner, loser);
		int k = TurnamenConfig.get().eloK;
		if (w != null || l != null) {
			int rw = w != null ? Elo.rating(w.data()) : Elo.visitor(Elo.rating(l.data()), loserPower, winnerPower);
			int rl = l != null ? Elo.rating(l.data()) : Elo.visitor(Elo.rating(w.data()), winnerPower, loserPower);
			if (w != null) {
				Elo.bout(w.data(), Elo.delta(rw, rl, 1.0D, k), true);
				Musim.board(owner, winner, w.getName(), Elo.rating(w.data()), Elo.wins(w.data()), Elo.losses(w.data()));
			}
			if (l != null) {
				Elo.bout(l.data(), Elo.delta(rl, rw, 0.0D, k), false);
				Musim.board(owner, loser, l.getName(), Elo.rating(l.data()), Elo.wins(l.data()), Elo.losses(l.data()));
			}
		}
		if (upset && w != null) {
			Ingatan.remember(server, w.data(), "turnamen.kejutan", loser, 4, "kultivator.sekte.turnamen.ingat.kejutan", loserName.getString());
		}
		if (upset && l != null) {
			Ingatan.remember(server, l.data(), "turnamen.dikejutkan", winner, -2, "kultivator.sekte.turnamen.ingat.dikejutkan",
					winnerName.getString());
		}
		if (w != null) {
			VoiceDisciples.line(w, finalBout ? "victory" : "cheer", 400);
		}
		SekteBus.TOURNAMENT_BOUT.invoker().onBout(server, owner, winner, loser, upset);
		Kerumunan.cheer(owner);
		Kerumunan.watch(owner, null, null);
		Live live = LIVE.get(owner);
		if (live != null) {
			live.fighting = false;
			live.nameA = winnerName;
			live.nameB = loserName;
			sync(live, true);
		}
	}

	@Override
	public void finished(UUID owner, ServerLevel level, Vec3 venue, Tournament.Ending ending) {
		MinecraftServer server = level.getServer();
		List<UUID> placings = ending.placings();
		for (UUID soul : placings) {
			DiscipleEntity entrant = body(owner, soul);
			if (entrant != null) {
				// Places 1-3 count for the best placing; the others only entered the season.
				int place = soul.equals(ending.champion()) ? 1 : soul.equals(ending.runnerUp()) ? 2 : soul.equals(ending.third()) ? 3 : 0;
				Elo.season(entrant.data(), place, ending.season());
			}
		}
		UUID champion = ending.champion();
		DiscipleEntity body = ending.championBody();
		if (champion != null && body != null) {
			String title = switch (ending.kind()) {
				case Tournament.KIND_ELITE -> "juara_elit:";
				case Tournament.KIND_GUESTS -> "juara_antar:";
				default -> "juara:";
			};
			Elo.title(body.data(), title + ending.season());
			Ingatan.remember(server, body.data(), "turnamen.juara", null, 8, "kultivator.sekte.turnamen.ingat.juara",
					Integer.toString(ending.season()));
			SekteBus.honored(server, owner, champion, "turnamen.juara", CHAMPION_HONOUR);
			Musim.board(owner, champion, body.getName(), Elo.rating(body.data()), Elo.wins(body.data()), Elo.losses(body.data()));
			VoiceDisciples.line(body, "victory", 200);
		}
		UUID runnerUp = ending.runnerUp();
		if (runnerUp != null) {
			DiscipleEntity second = body(owner, runnerUp);
			if (second != null) {
				Ingatan.remember(server, second.data(), "turnamen.final", champion, 3, "kultivator.sekte.turnamen.ingat.final",
						ending.championName().getString());
			}
			// Losing the final is still an honour (DESIGN 36.2.3: never a humiliation).
			SekteBus.honored(server, owner, runnerUp, "turnamen.final", FINALIST_HONOUR);
		}
		Musim.champion(owner, ending.season(), ending.kind(), ending.championName(), champion, ending.guestSect());
		boolean guestWon = champion == null;
		SekteBus.TOURNAMENT_ENDED.invoker().onTournamentEnded(server, owner, new TurnamenHasil(ending.season(), ending.kind(), champion,
				placings, ending.upsets(), ending.guestSect(), guestWon));
		if (ending.guestSect() != null) {
			AntarSekte.result(server, owner, ending.guestSect(), !guestWon);
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null && champion != null) {
			Advancements.grant(player, "sekte/turnamen_juara");
		}
		SekteLog.log(server, owner, SekteIds.T, Component.translatable("kultivator.sekte.turnamen.log.juara", ending.season(),
				ending.championName()));
		if (TurnamenConfig.get().adegan) {
			if (body != null) {
				scene(level, BEAT_JUARA, body, owner, venue, ending.season());
			} else {
				scene(level, BEAT_TAMU, null, owner, venue, ending.season());
			}
		}
		Kerumunan.cheer(owner);
		Live live = LIVE.get(owner);
		if (live != null) {
			live.fighting = false;
			live.champion = true;
			live.nameA = ending.championName();
			live.nameB = Component.empty();
			live.linger = LINGER_TICKS;
			sync(live, true);
		} else {
			Kerumunan.disperse(owner);
		}
	}

	@Override
	public void cancelled(UUID owner) {
		Live live = LIVE.remove(owner);
		if (live != null) {
			off(live);
		}
		Kerumunan.disperse(owner);
	}

	@Override
	public boolean ownScenes() {
		return TurnamenConfig.get().adegan;
	}

	// ================================================================== tick, sync

	private static void tick(MinecraftServer server) {
		ticks++;
		BabakDuel.tick(ticks);
		if (!LIVE.isEmpty()) {
			for (Live live : List.copyOf(LIVE.values())) {
				if (live.linger > 0 && --live.linger == 0) {
					LIVE.remove(live.owner, live);
					off(live);
					Kerumunan.disperse(live.owner);
					continue;
				}
				if (ticks % 100 == 0) {
					sync(live, false);
				}
			}
		}
		if (ticks % 400 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				AntarSekte.tick(server, player.getUUID());
			}
		}
	}

	/** Sends the Lingkar to players near the stage: to everyone ({@code all}) or only to those who have not seen it yet. */
	private static void sync(Live live, boolean all) {
		TurnamenPayloads.Lingkar payload = lingkar(live);
		for (ServerPlayer player : PlayerLookup.around(live.level, live.venue, SYNC_RANGE)) {
			if (all || live.viewers.add(player.getUUID())) {
				live.viewers.add(player.getUUID());
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void off(Live live) {
		TurnamenPayloads.Lingkar payload = TurnamenPayloads.Lingkar.off(BlockPos.containing(live.venue));
		MinecraftServer server = live.level.getServer();
		for (UUID viewer : live.viewers) {
			ServerPlayer player = server.getPlayerList().getPlayer(viewer);
			if (player != null) {
				ServerPlayNetworking.send(player, payload);
			}
		}
		live.viewers.clear();
	}

	private static TurnamenPayloads.Lingkar lingkar(Live live) {
		int flags = (live.fighting ? TurnamenPayloads.FLAG_FIGHTING : 0) | (live.finalBout ? TurnamenPayloads.FLAG_FINAL : 0)
				| (live.stage ? TurnamenPayloads.FLAG_STAGE : 0) | (live.champion ? TurnamenPayloads.FLAG_CHAMPION : 0);
		int kind = Tournament.KIND_ELITE.equals(live.kind) ? TurnamenPayloads.KIND_ELITE : Tournament.KIND_GUESTS.equals(live.kind)
				? TurnamenPayloads.KIND_GUESTS : TurnamenPayloads.KIND_NORMAL;
		return new TurnamenPayloads.Lingkar(true, BlockPos.containing(live.venue), live.stage ? ArenaTempat.STAGE_RING : ArenaTempat.STELE_RING,
				kind, live.season, live.bout, flags, live.nameA, live.nameB);
	}

	private static int kindCode(@Nullable Live live) {
		return live != null && Tournament.KIND_ELITE.equals(live.kind) ? EpicIds.TOUR_ELITE : EpicIds.TOUR_INTERNAL;
	}

	/** One sekte beat to the master (the scene system's own guards decide whether the camera may move). */
	static void scene(ServerLevel level, int beat, @Nullable Entity subject, UUID owner, Vec3 pos, int number) {
		try {
			ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(owner);
			if (viewer == null || viewer.level() != level) {
				return;
			}
			EpicMoments.send(level, EpicIds.EXTRA, beat, subject, viewer, pos, level.getGameTime(), number, 0, 0.0F, 3,
					beat == BEAT_FINAL ? 0 : EpicIds.FLAG_FINAL);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Sekte tournament scene {} failed", beat, exception);
		}
	}

	// ================================================================== hub card

	private static void onAsk(ServerPlayer player, int action) {
		if (action == TurnamenPayloads.Minta.ACCEPT) {
			AntarSekte.accept(player);
		} else if (action == TurnamenPayloads.Minta.REFUSE) {
			AntarSekte.refuse(player);
		}
		ServerPlayNetworking.send(player, new TurnamenPayloads.Papan(TurnamenCommands.boardData(player)));
	}

	// ================================================================== helpers

	/** The owner's loaded disciple with {@code soul}, or null. */
	static @Nullable DiscipleEntity body(UUID owner, @Nullable UUID soul) {
		if (soul == null) {
			return null;
		}
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (soul.equals(disciple.soulId()) && !disciple.isRemoved()) {
				return disciple;
			}
		}
		return null;
	}

	private static Vec3 stele(UUID owner, Vec3 fallback) {
		dev.kultivator.sect.hall.Halls.HallView hall = dev.kultivator.sect.hall.Halls.hallOf(owner);
		return hall == null ? fallback : Vec3.atBottomCenterOf(hall.pos()).add(0.0D, 1.0D, 0.0D);
	}

	/** True while the hall's tournament has visitors or a running Lingkar (tests). */
	static boolean live(UUID owner) {
		return LIVE.containsKey(owner);
	}

	static int crowd(UUID owner) {
		return Kerumunan.visitors(owner);
	}

	/** Test {@code final}: the Babak Final stamp and the champion card without a tournament. */
	static void testScenes(ServerPlayer player, boolean champion) {
		ServerLevel level = (ServerLevel) player.level();
		int season = Math.max(1, Musim.season(player.getUUID()));
		DiscipleEntity subject = null;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.level() == level) {
				subject = disciple;
				break;
			}
		}
		scene(level, champion ? BEAT_JUARA : BEAT_FINAL, subject, player.getUUID(), player.position(), season);
	}

	/** A disciple of the hall left the sect (its board line goes). */
	static void forget(UUID owner, UUID soul) {
		Musim.forget(owner, soul);
	}

	/** Updates a disciple's board line after a test changed its rating. */
	static void board(UUID owner, DiscipleEntity disciple) {
		DiscipleData data = disciple.data();
		Musim.board(owner, disciple.soulId(), disciple.getName(), Elo.rating(data), Elo.wins(data), Elo.losses(data));
	}

	private static void clear() {
		LIVE.clear();
		BabakDuel.clear();
		Kerumunan.clear();
		ticks = 0L;
	}

	private Turnamen() {
	}
}
