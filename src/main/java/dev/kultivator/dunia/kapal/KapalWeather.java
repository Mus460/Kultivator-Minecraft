package dev.kultivator.dunia.kapal;

import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;

/**
 * How the sky of module L touches a flying ship (detail pass of DESIGN 34.5.15; module L is only asked through
 * {@code DuniaApi.sky()}, so with L off every factor is 1). Spirit weather under the hull: a thin Qi mist feeds the
 * formation (fuel ×0.85), a dense mist feeds it more but blinds the helmsman (fuel ×0.7, top speed ×0.85), the purple dawn
 * is a spirit wind (fuel ×0.6, top speed ×1.1, still under {@code maxSpeed}). Sky events: a blood eclipse doubles the sky
 * pirates' chance (at most 60 %), twin moons pull the clouds (a cloud leap costs ×0.75), and a meteor shower may drop a
 * Batu Meteor Roh into the cloud net of a ship under way. Pure numbers, shared by both sides (the HUD shows them).
 */
public final class KapalWeather {

	/** A meteor shower: one roll per ship under way every {@link #METEOR_EVERY} ticks. */
	public static final int METEOR_EVERY = 1200;
	public static final double METEOR_CHANCE = 0.12D;
	public static final double ECLIPSE_PIRATES = 2.0D;
	public static final double ECLIPSE_PIRATES_CAP = 0.6D;
	public static final double TWIN_MOONS_LEAP = 0.75D;

	/** Fuel burnt per tick in {@code weather} (1 = as usual). */
	public static double fuel(SpiritWeather weather) {
		return switch (weather) {
			case THIN_MIST -> 0.85D;
			case DENSE_MIST -> 0.7D;
			case PURPLE_DAWN -> 0.6D;
			default -> 1.0D;
		};
	}

	/** The top speed factor in {@code weather}. */
	public static double speed(SpiritWeather weather) {
		return switch (weather) {
			case DENSE_MIST -> 0.85D;
			case PURPLE_DAWN -> 1.1D;
			default -> 1.0D;
		};
	}

	/** The sky pirates' chance during {@code sky} (null: no event). */
	public static double pirateChance(double base, SkyKind sky) {
		return sky == SkyKind.BLOOD_ECLIPSE ? Math.min(ECLIPSE_PIRATES_CAP, base * ECLIPSE_PIRATES) : base;
	}

	/** The fuel factor of a cloud leap during {@code sky}. */
	public static double leap(SkyKind sky) {
		return sky == SkyKind.TWIN_MOONS ? TWIN_MOONS_LEAP : 1.0D;
	}

	private KapalWeather() {
	}
}
