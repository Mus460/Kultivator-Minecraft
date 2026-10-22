package dev.kultivator.sekte.api;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The event bus of Sekte yang Hidup (DESIGN 36.3.1, contract C36.L2). Each event is fired only by the owner named in its
 * comment, through its {@code fire*} helper (or {@code EVENT.invoker()}), on the server thread, and may be listened to by
 * anyone. A listener that throws is logged and skipped, so one module never breaks another's moment. Listeners must be
 * cheap: anything heavier than a few map lookups is queued and done on the module's own spread tick.
 */
public final class SekteBus {

	// ================================================================== disciples (P, R)

	/** Fired by any module that sees a master mistreat a disciple ({@code source} = {@code <module>.<what>}); P listens. */
	@FunctionalInterface
	public interface Mistreated {
		void onMistreated(MinecraftServer server, UUID owner, UUID soul, Perlakuan kind, int severity, String source);
	}

	/** Fired by any module that honours a disciple (a won bout, a mission success, praise); amount 1-10. P listens. */
	@FunctionalInterface
	public interface Honored {
		void onHonored(MinecraftServer server, UUID owner, UUID soul, String reason, int amount);
	}

	/** Fired by P when a disciple's loyalty state changes. */
	@FunctionalInterface
	public interface LoyaltyChanged {
		void onLoyaltyChanged(MinecraftServer server, UUID owner, UUID soul, HatiService.Keadaan before, HatiService.Keadaan now);
	}

	/**
	 * Fired by P after a disciple left the sect (its body already departed). {@code toSect} is the NPC sect it defected to
	 * (D takes it), {@code companion} a sworn sibling that went with it, or null.
	 */
	@FunctionalInterface
	public interface Left {
		void onLeft(MinecraftServer server, UUID owner, UUID soul, Component name, Kepergian how, @Nullable String toSect,
				@Nullable UUID companion);
	}

	/** Fired by R when the kind of a pair's tie changes. */
	@FunctionalInterface
	public interface RelationChanged {
		void onRelationChanged(MinecraftServer server, UUID owner, UUID a, UUID b, Ikatan before, Ikatan now);
	}

	// ================================================================== tournament, trial (T, G)

	/** Fired by T after each bout of the hall's own disciples; a visitor's side is null. */
	@FunctionalInterface
	public interface Bout {
		void onBout(MinecraftServer server, UUID owner, @Nullable UUID winner, @Nullable UUID loser, boolean upset);
	}

	/** Fired by T when a tournament ends. */
	@FunctionalInterface
	public interface TournamentEnded {
		void onTournamentEnded(MinecraftServer server, UUID owner, TurnamenHasil result);
	}

	/** Fired by G when a stair trial ends ({@code bestSteps} 0-1000). */
	@FunctionalInterface
	public interface TrialFinished {
		void onTrialFinished(MinecraftServer server, UUID owner, int candidates, int passed, int bestSteps);
	}

	// ================================================================== pavilion, cemetery (A)

	/** Fired by A when a pavilion study ends ({@code learned} true when the technique was learned). */
	@FunctionalInterface
	public interface Studied {
		void onStudied(MinecraftServer server, UUID owner, UUID soul, String techniqueId, boolean learned);
	}

	/** Fired by A when a fallen disciple gets its grave. */
	@FunctionalInterface
	public interface Buried {
		void onBuried(MinecraftServer server, UUID owner, UUID soul, Component name);
	}

	/** Fired by A after an ancestral rite ({@code kind} 0 daily incense, 1 Qingming, 2 a funeral); attendees count. */
	@FunctionalInterface
	public interface AncestorRite {
		void onAncestorRite(MinecraftServer server, UUID owner, int kind, List<UUID> attendees);
	}

	// ================================================================== elders, heir (E)

	/** Fired by E when an office is filled ({@code office} non-null) or vacated (null). */
	@FunctionalInterface
	public interface ElderAppointed {
		void onElderAppointed(MinecraftServer server, UUID owner, UUID soul, @Nullable Jabatan office);
	}

	/** Fired by E when an heir is named (the existing Heir ritual finished) or replaced. */
	@FunctionalInterface
	public interface HeirNamed {
		void onHeirNamed(MinecraftServer server, UUID owner, UUID soul, @Nullable UUID previous);
	}

	/** Fired by E when the heir becomes the second-generation Sect Master (the master becomes the Founding Patriarch). */
	@FunctionalInterface
	public interface Succession {
		void onSuccession(MinecraftServer server, UUID owner, UUID newMaster);
	}

	// ================================================================== missions (X)

	@FunctionalInterface
	public interface MissionSent {
		void onMissionSent(MinecraftServer server, UUID owner, String missionId, List<UUID> team);
	}

	/** Fired by X once per finished mission, after the team arrived (or was settled as data). */
	@FunctionalInterface
	public interface MissionReturned {
		void onMissionReturned(MinecraftServer server, UUID owner, MisiHasil result);
	}

	// ================================================================== emblem, rival sects, work (S, D, W)

	@FunctionalInterface
	public interface EmblemChanged {
		void onEmblemChanged(MinecraftServer server, UUID owner, Lambang emblem);
	}

	/**
	 * Fired by D for each NPC sect action. {@code owner} is the player hall it concerns, or null for world news
	 * ({@link AksiSekte#PERANG_NPC}, {@link AksiSekte#TUMBUH}); {@code magnitude} 1-9 (the sect's tier or the raid size).
	 */
	@FunctionalInterface
	public interface RivalSectAction {
		void onRivalSectAction(MinecraftServer server, @Nullable UUID owner, String sectId, AksiSekte action, int magnitude);
	}

	/** Fired by W when a garden or mine shift banks its output ({@code kind} {@code kebun} or {@code tambang}). */
	@FunctionalInterface
	public interface WorkDone {
		void onWorkDone(MinecraftServer server, UUID owner, UUID soul, String kind, int amount);
	}

	/** Fired by the lead's {@code SekteLog} for every line a module writes to the hall log (E's elders collect them). */
	@FunctionalInterface
	public interface SectLog {
		void onSectLog(MinecraftServer server, UUID owner, String module, Component line);
	}

	// ================================================================== events

	public static final Event<Mistreated> DISCIPLE_MISTREATED = EventFactory.createArrayBacked(Mistreated.class, listeners ->
			(server, owner, soul, kind, severity, source) -> {
				for (Mistreated listener : listeners) {
					try {
						listener.onMistreated(server, owner, soul, kind, severity, source);
					} catch (RuntimeException exception) {
						failed("DISCIPLE_MISTREATED", exception);
					}
				}
			});

	public static final Event<Honored> DISCIPLE_HONORED = EventFactory.createArrayBacked(Honored.class, listeners ->
			(server, owner, soul, reason, amount) -> {
				for (Honored listener : listeners) {
					try {
						listener.onHonored(server, owner, soul, reason, amount);
					} catch (RuntimeException exception) {
						failed("DISCIPLE_HONORED", exception);
					}
				}
			});

	public static final Event<LoyaltyChanged> LOYALTY_CHANGED = EventFactory.createArrayBacked(LoyaltyChanged.class, listeners ->
			(server, owner, soul, before, now) -> {
				for (LoyaltyChanged listener : listeners) {
					try {
						listener.onLoyaltyChanged(server, owner, soul, before, now);
					} catch (RuntimeException exception) {
						failed("LOYALTY_CHANGED", exception);
					}
				}
			});

	public static final Event<Left> DISCIPLE_LEFT = EventFactory.createArrayBacked(Left.class, listeners ->
			(server, owner, soul, name, how, toSect, companion) -> {
				for (Left listener : listeners) {
					try {
						listener.onLeft(server, owner, soul, name, how, toSect, companion);
					} catch (RuntimeException exception) {
						failed("DISCIPLE_LEFT", exception);
					}
				}
			});

	public static final Event<RelationChanged> RELATION_CHANGED = EventFactory.createArrayBacked(RelationChanged.class, listeners ->
			(server, owner, a, b, before, now) -> {
				for (RelationChanged listener : listeners) {
					try {
						listener.onRelationChanged(server, owner, a, b, before, now);
					} catch (RuntimeException exception) {
						failed("RELATION_CHANGED", exception);
					}
				}
			});

	public static final Event<Bout> TOURNAMENT_BOUT = EventFactory.createArrayBacked(Bout.class, listeners ->
			(server, owner, winner, loser, upset) -> {
				for (Bout listener : listeners) {
					try {
						listener.onBout(server, owner, winner, loser, upset);
					} catch (RuntimeException exception) {
						failed("TOURNAMENT_BOUT", exception);
					}
				}
			});

	public static final Event<TournamentEnded> TOURNAMENT_ENDED = EventFactory.createArrayBacked(TournamentEnded.class, listeners ->
			(server, owner, result) -> {
				for (TournamentEnded listener : listeners) {
					try {
						listener.onTournamentEnded(server, owner, result);
					} catch (RuntimeException exception) {
						failed("TOURNAMENT_ENDED", exception);
					}
				}
			});

	public static final Event<TrialFinished> TRIAL_FINISHED = EventFactory.createArrayBacked(TrialFinished.class, listeners ->
			(server, owner, candidates, passed, bestSteps) -> {
				for (TrialFinished listener : listeners) {
					try {
						listener.onTrialFinished(server, owner, candidates, passed, bestSteps);
					} catch (RuntimeException exception) {
						failed("TRIAL_FINISHED", exception);
					}
				}
			});

	public static final Event<Studied> DISCIPLE_STUDIED = EventFactory.createArrayBacked(Studied.class, listeners ->
			(server, owner, soul, techniqueId, learned) -> {
				for (Studied listener : listeners) {
					try {
						listener.onStudied(server, owner, soul, techniqueId, learned);
					} catch (RuntimeException exception) {
						failed("DISCIPLE_STUDIED", exception);
					}
				}
			});

	public static final Event<Buried> DISCIPLE_BURIED = EventFactory.createArrayBacked(Buried.class, listeners ->
			(server, owner, soul, name) -> {
				for (Buried listener : listeners) {
					try {
						listener.onBuried(server, owner, soul, name);
					} catch (RuntimeException exception) {
						failed("DISCIPLE_BURIED", exception);
					}
				}
			});

	public static final Event<AncestorRite> ANCESTOR_RITE = EventFactory.createArrayBacked(AncestorRite.class, listeners ->
			(server, owner, kind, attendees) -> {
				for (AncestorRite listener : listeners) {
					try {
						listener.onAncestorRite(server, owner, kind, attendees);
					} catch (RuntimeException exception) {
						failed("ANCESTOR_RITE", exception);
					}
				}
			});

	public static final Event<ElderAppointed> ELDER_APPOINTED = EventFactory.createArrayBacked(ElderAppointed.class, listeners ->
			(server, owner, soul, office) -> {
				for (ElderAppointed listener : listeners) {
					try {
						listener.onElderAppointed(server, owner, soul, office);
					} catch (RuntimeException exception) {
						failed("ELDER_APPOINTED", exception);
					}
				}
			});

	public static final Event<HeirNamed> HEIR_NAMED = EventFactory.createArrayBacked(HeirNamed.class, listeners ->
			(server, owner, soul, previous) -> {
				for (HeirNamed listener : listeners) {
					try {
						listener.onHeirNamed(server, owner, soul, previous);
					} catch (RuntimeException exception) {
						failed("HEIR_NAMED", exception);
					}
				}
			});

	public static final Event<Succession> SUCCESSION = EventFactory.createArrayBacked(Succession.class, listeners ->
			(server, owner, newMaster) -> {
				for (Succession listener : listeners) {
					try {
						listener.onSuccession(server, owner, newMaster);
					} catch (RuntimeException exception) {
						failed("SUCCESSION", exception);
					}
				}
			});

	public static final Event<MissionSent> MISSION_SENT = EventFactory.createArrayBacked(MissionSent.class, listeners ->
			(server, owner, missionId, team) -> {
				for (MissionSent listener : listeners) {
					try {
						listener.onMissionSent(server, owner, missionId, team);
					} catch (RuntimeException exception) {
						failed("MISSION_SENT", exception);
					}
				}
			});

	public static final Event<MissionReturned> MISSION_RETURNED = EventFactory.createArrayBacked(MissionReturned.class, listeners ->
			(server, owner, result) -> {
				for (MissionReturned listener : listeners) {
					try {
						listener.onMissionReturned(server, owner, result);
					} catch (RuntimeException exception) {
						failed("MISSION_RETURNED", exception);
					}
				}
			});

	public static final Event<EmblemChanged> EMBLEM_CHANGED = EventFactory.createArrayBacked(EmblemChanged.class, listeners ->
			(server, owner, emblem) -> {
				for (EmblemChanged listener : listeners) {
					try {
						listener.onEmblemChanged(server, owner, emblem);
					} catch (RuntimeException exception) {
						failed("EMBLEM_CHANGED", exception);
					}
				}
			});

	public static final Event<RivalSectAction> RIVAL_SECT_ACTION = EventFactory.createArrayBacked(RivalSectAction.class, listeners ->
			(server, owner, sectId, action, magnitude) -> {
				for (RivalSectAction listener : listeners) {
					try {
						listener.onRivalSectAction(server, owner, sectId, action, magnitude);
					} catch (RuntimeException exception) {
						failed("RIVAL_SECT_ACTION", exception);
					}
				}
			});

	public static final Event<WorkDone> WORK_DONE = EventFactory.createArrayBacked(WorkDone.class, listeners ->
			(server, owner, soul, kind, amount) -> {
				for (WorkDone listener : listeners) {
					try {
						listener.onWorkDone(server, owner, soul, kind, amount);
					} catch (RuntimeException exception) {
						failed("WORK_DONE", exception);
					}
				}
			});

	public static final Event<SectLog> SECT_LOG = EventFactory.createArrayBacked(SectLog.class, listeners ->
			(server, owner, module, line) -> {
				for (SectLog listener : listeners) {
					try {
						listener.onSectLog(server, owner, module, line);
					} catch (RuntimeException exception) {
						failed("SECT_LOG", exception);
					}
				}
			});

	// ================================================================== fire helpers (null-safe)

	public static void mistreated(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, Perlakuan kind,
			String source) {
		if (server != null && owner != null && soul != null && kind != null) {
			DISCIPLE_MISTREATED.invoker().onMistreated(server, owner, soul, kind, kind.severity(), source == null ? "" : source);
		}
	}

	public static void honored(@Nullable MinecraftServer server, @Nullable UUID owner, @Nullable UUID soul, String reason, int amount) {
		if (server != null && owner != null && soul != null) {
			DISCIPLE_HONORED.invoker().onHonored(server, owner, soul, reason == null ? "" : reason, Math.max(1, Math.min(10, amount)));
		}
	}

	public static void rivalAction(@Nullable MinecraftServer server, @Nullable UUID owner, String sectId, AksiSekte action, int magnitude) {
		if (server != null && sectId != null && action != null) {
			RIVAL_SECT_ACTION.invoker().onRivalSectAction(server, owner, sectId, action, Math.max(1, Math.min(9, magnitude)));
		}
	}

	private static void failed(String event, RuntimeException exception) {
		Kultivator.LOGGER.error("A Sekte {} listener failed", event, exception);
	}

	private SekteBus() {
	}
}
