package dev.kultivator.dunia;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.api.TreasureBirth;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The cross-module events of DESIGN 34.4.3 (contract C34.S3). Each event is fired only by the owner named in its comment
 * (through {@code EVENT.invoker()} or the {@code fire*} helper next to it) and may be listened to by anyone. A listener
 * that throws is logged and skipped, so one module never breaks another's moment.
 */
public final class DuniaEvents {

	/** Fired by L when a treasure is born. Listened by K (rivals race), T (live marker), R (a ruin's guardian wakes). */
	@FunctionalInterface
	public interface TreasureBorn {
		void onTreasureBorn(ServerLevel level, TreasureBirth birth);
	}

	/** Fired by L when a birth is claimed or ends: winner null when a rival ({@code rivalId}) took it or it faded. */
	@FunctionalInterface
	public interface TreasureClaimed {
		void onTreasureClaimed(ServerLevel level, TreasureBirth birth, @Nullable ServerPlayer winner, @Nullable String rivalId);
	}

	/** Fired by L when a sky event starts ({@code started} true) or ends in {@code level}. */
	@FunctionalInterface
	public interface SkyEvent {
		void onSkyEvent(ServerLevel level, SkyKind kind, boolean started);
	}

	/** Fired by L when the spirit weather of cell {@code cell} changes. */
	@FunctionalInterface
	public interface WeatherChanged {
		void onWeatherChanged(ServerLevel level, long cell, SpiritWeather weather);
	}

	/** Fired by R when a prison seal changes ({@code siteId} = the prison's {@link DuniaSites.Site#id()}). */
	@FunctionalInterface
	public interface SealChanged {
		void onSealChanged(ServerLevel level, long siteId, int integrity, boolean broken);
	}

	/** Fired by B when an owned spirit beast evolves to {@code tier} on {@code line}. */
	@FunctionalInterface
	public interface BeastEvolved {
		void onBeastEvolved(ServerPlayer owner, LivingEntity beast, int tier, String line);
	}

	/** Fired by the spine when a player discovers a site. */
	@FunctionalInterface
	public interface SiteDiscovered {
		void onSiteDiscovered(ServerPlayer player, DuniaSites.Site site);
	}

	/** Fired by T when a voyage starts ({@code arrived} false) or arrives at {@code destination}. */
	@FunctionalInterface
	public interface Voyage {
		void onVoyage(ServerPlayer captain, String destination, boolean arrived);
	}

	/** Fired by D when a player's soul leaves ({@code entered} true) or returns to the body. */
	@FunctionalInterface
	public interface SoulJourney {
		void onSoulJourney(ServerPlayer player, boolean entered);
	}

	public static final Event<TreasureBorn> TREASURE_BORN = EventFactory.createArrayBacked(TreasureBorn.class, listeners ->
			(level, birth) -> {
				for (TreasureBorn listener : listeners) {
					try {
						listener.onTreasureBorn(level, birth);
					} catch (RuntimeException exception) {
						failed("TREASURE_BORN", exception);
					}
				}
			});

	public static final Event<TreasureClaimed> TREASURE_CLAIMED = EventFactory.createArrayBacked(TreasureClaimed.class, listeners ->
			(level, birth, winner, rivalId) -> {
				for (TreasureClaimed listener : listeners) {
					try {
						listener.onTreasureClaimed(level, birth, winner, rivalId);
					} catch (RuntimeException exception) {
						failed("TREASURE_CLAIMED", exception);
					}
				}
			});

	public static final Event<SkyEvent> SKY_EVENT = EventFactory.createArrayBacked(SkyEvent.class, listeners ->
			(level, kind, started) -> {
				for (SkyEvent listener : listeners) {
					try {
						listener.onSkyEvent(level, kind, started);
					} catch (RuntimeException exception) {
						failed("SKY_EVENT", exception);
					}
				}
			});

	public static final Event<WeatherChanged> WEATHER_CHANGED = EventFactory.createArrayBacked(WeatherChanged.class, listeners ->
			(level, cell, weather) -> {
				for (WeatherChanged listener : listeners) {
					try {
						listener.onWeatherChanged(level, cell, weather);
					} catch (RuntimeException exception) {
						failed("WEATHER_CHANGED", exception);
					}
				}
			});

	public static final Event<SealChanged> SEAL_CHANGED = EventFactory.createArrayBacked(SealChanged.class, listeners ->
			(level, siteId, integrity, broken) -> {
				for (SealChanged listener : listeners) {
					try {
						listener.onSealChanged(level, siteId, integrity, broken);
					} catch (RuntimeException exception) {
						failed("SEAL_CHANGED", exception);
					}
				}
			});

	public static final Event<BeastEvolved> BEAST_EVOLVED = EventFactory.createArrayBacked(BeastEvolved.class, listeners ->
			(owner, beast, tier, line) -> {
				for (BeastEvolved listener : listeners) {
					try {
						listener.onBeastEvolved(owner, beast, tier, line);
					} catch (RuntimeException exception) {
						failed("BEAST_EVOLVED", exception);
					}
				}
			});

	public static final Event<SiteDiscovered> SITE_DISCOVERED = EventFactory.createArrayBacked(SiteDiscovered.class, listeners ->
			(player, site) -> {
				for (SiteDiscovered listener : listeners) {
					try {
						listener.onSiteDiscovered(player, site);
					} catch (RuntimeException exception) {
						failed("SITE_DISCOVERED", exception);
					}
				}
			});

	public static final Event<Voyage> VOYAGE = EventFactory.createArrayBacked(Voyage.class, listeners ->
			(captain, destination, arrived) -> {
				for (Voyage listener : listeners) {
					try {
						listener.onVoyage(captain, destination, arrived);
					} catch (RuntimeException exception) {
						failed("VOYAGE", exception);
					}
				}
			});

	public static final Event<SoulJourney> SOUL_JOURNEY = EventFactory.createArrayBacked(SoulJourney.class, listeners ->
			(player, entered) -> {
				for (SoulJourney listener : listeners) {
					try {
						listener.onSoulJourney(player, entered);
					} catch (RuntimeException exception) {
						failed("SOUL_JOURNEY", exception);
					}
				}
			});

	// ================================================================== fire helpers (owners only)

	/** L. */
	public static void fireTreasureBorn(ServerLevel level, TreasureBirth birth) {
		TREASURE_BORN.invoker().onTreasureBorn(level, birth);
	}

	/** L. */
	public static void fireTreasureClaimed(ServerLevel level, TreasureBirth birth, @Nullable ServerPlayer winner, @Nullable String rivalId) {
		TREASURE_CLAIMED.invoker().onTreasureClaimed(level, birth, winner, rivalId);
	}

	/** L. */
	public static void fireSkyEvent(ServerLevel level, SkyKind kind, boolean started) {
		SKY_EVENT.invoker().onSkyEvent(level, kind, started);
	}

	/** L. */
	public static void fireWeatherChanged(ServerLevel level, long cell, SpiritWeather weather) {
		WEATHER_CHANGED.invoker().onWeatherChanged(level, cell, weather);
	}

	/** R. */
	public static void fireSealChanged(ServerLevel level, long siteId, int integrity, boolean broken) {
		SEAL_CHANGED.invoker().onSealChanged(level, siteId, integrity, broken);
	}

	/** B. */
	public static void fireBeastEvolved(ServerPlayer owner, LivingEntity beast, int tier, String line) {
		BEAST_EVOLVED.invoker().onBeastEvolved(owner, beast, tier, line);
	}

	/** Spine ({@link DuniaSites#discover}). */
	static void fireSiteDiscovered(ServerPlayer player, DuniaSites.Site site) {
		SITE_DISCOVERED.invoker().onSiteDiscovered(player, site);
	}

	/** T. */
	public static void fireVoyage(ServerPlayer captain, String destination, boolean arrived) {
		VOYAGE.invoker().onVoyage(captain, destination, arrived);
	}

	/** D. */
	public static void fireSoulJourney(ServerPlayer player, boolean entered) {
		SOUL_JOURNEY.invoker().onSoulJourney(player, entered);
	}

	private static void failed(String event, RuntimeException exception) {
		Kultivator.LOGGER.error("A Dunia {} listener failed", event, exception);
	}

	private DuniaEvents() {
	}
}
