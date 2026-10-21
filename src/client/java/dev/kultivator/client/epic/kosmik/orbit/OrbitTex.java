package dev.kultivator.client.epic.kosmik.orbit;

import dev.kultivator.Kultivator;
import net.minecraft.resources.Identifier;

/**
 * The textures of the Orbit stage (DESIGN 32.2.2), written by {@code tools/gen_kosmik_orbit.py} under
 * {@code textures/misc/kosmik/orbit/}. Maps follow the stage quality latched at a shot start (Minimal 256, Normal 512,
 * Maximal 1024; "Film kosmik 3D: Ringan" already lowered the quality one step). Identifiers only: nothing is loaded here.
 */
final class OrbitTex {

	static final Identifier[] PLANET_DAY = {tex("planet_day_256"), tex("planet_day_512"), tex("planet_day_1024")};
	static final Identifier[] PLANET_CLASS = {tex("planet_class_256"), tex("planet_class_512"), tex("planet_class_1024")};
	/** Night lights: Normal 256, Maximal 512 (Minimal draws none). */
	static final Identifier[] PLANET_NIGHT = {tex("planet_night_256"), tex("planet_night_256"), tex("planet_night_512")};
	static final Identifier[] CLOUDS = {tex("clouds_256"), tex("clouds_512"), tex("clouds_1024")};
	static final Identifier[] SPACE = {tex("space_256"), tex("space_512"), tex("space_1024")};
	/** The moon: Normal 128, Maximal 256 (Minimal draws none). */
	static final Identifier[] MOON = {tex("moon_128"), tex("moon_128"), tex("moon_256")};
	static final Identifier[] PATCH_LAND = {tex("patch_land_256"), tex("patch_land_256"), tex("patch_land_512")};
	static final Identifier[] PATCH_CLOUD = {tex("patch_cloud_256"), tex("patch_cloud_256"), tex("patch_cloud_512")};

	static final Identifier SUN_CORE = tex("sun_core");
	static final Identifier SUN_CORONA = tex("sun_corona");
	static final Identifier LIMB_GLOW = tex("limb_glow");
	static final Identifier STAR_SPIKE = tex("star_spike");
	static final Identifier ATMO = tex("atmo");
	static final Identifier PLASMA = tex("plasma");
	static final Identifier TRAIL = tex("trail");
	static final Identifier BEAM = tex("beam");
	static final Identifier STAR_LINE = tex("star_line");
	static final Identifier RUNE_RING = tex("rune_ring");
	static final Identifier CELL_TOP = tex("cell_top");
	static final Identifier CELL_SIDE = tex("cell_side");
	static final Identifier CELL_BOTTOM = tex("cell_bottom");

	/** The baked planet (32.2.2, {@link PlanetBake}). */
	static final Identifier BAKED_PLANET = Kultivator.id("dynamic/kosmik_planet");

	private static Identifier tex(String name) {
		return Kultivator.id("textures/misc/kosmik/orbit/" + name + ".png");
	}

	/** The map index of a quality (0 Minimal, 1 Normal, 2 Maximal). */
	static int q(int quality) {
		return Math.max(0, Math.min(2, quality));
	}

	private OrbitTex() {
	}
}
