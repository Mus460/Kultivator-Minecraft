package dev.kultivator.dunia.api;

import dev.kultivator.Kultivator;
import org.jetbrains.annotations.Nullable;

/**
 * The cross-module services of DESIGN 34.4.3: one implementation per service, set by its owner from its init, a no-op
 * default while the owner is missing or switched off. Modules call each other only through these and
 * {@code DuniaEvents}, so the six modules never import each other's packages. Setting a service twice logs and replaces.
 */
public final class DuniaApi {

	private static SkyService sky = SkyService.NONE;
	private static TreasureService treasures = TreasureService.NONE;
	private static RivalService rivals = RivalService.NONE;
	private static SealService seals = SealService.NONE;
	private static BeastEvolutionService beasts = BeastEvolutionService.NONE;
	private static RouteService routes = RouteService.NONE;
	private static SoulService souls = SoulService.NONE;

	/** Module L. */
	public static SkyService sky() {
		return sky;
	}

	/** Module L. */
	public static TreasureService treasures() {
		return treasures;
	}

	/** Module K. */
	public static RivalService rivals() {
		return rivals;
	}

	/** Module R. */
	public static SealService seals() {
		return seals;
	}

	/** Module B. */
	public static BeastEvolutionService beasts() {
		return beasts;
	}

	/** Module T. */
	public static RouteService routes() {
		return routes;
	}

	/** Module D. */
	public static SoulService souls() {
		return souls;
	}

	public static void setSky(@Nullable SkyService service) {
		sky = set("sky", sky, service, SkyService.NONE);
	}

	public static void setTreasures(@Nullable TreasureService service) {
		treasures = set("treasures", treasures, service, TreasureService.NONE);
	}

	public static void setRivals(@Nullable RivalService service) {
		rivals = set("rivals", rivals, service, RivalService.NONE);
	}

	public static void setSeals(@Nullable SealService service) {
		seals = set("seals", seals, service, SealService.NONE);
	}

	public static void setBeasts(@Nullable BeastEvolutionService service) {
		beasts = set("beasts", beasts, service, BeastEvolutionService.NONE);
	}

	public static void setRoutes(@Nullable RouteService service) {
		routes = set("routes", routes, service, RouteService.NONE);
	}

	public static void setSouls(@Nullable SoulService service) {
		souls = set("souls", souls, service, SoulService.NONE);
	}

	private static <T> T set(String name, T current, @Nullable T service, T none) {
		if (service == null) {
			return none;
		}
		if (current != none && current != service) {
			Kultivator.LOGGER.warn("Dunia service {} set twice; the newer one wins", name);
		}
		return service;
	}

	private DuniaApi() {
	}
}
