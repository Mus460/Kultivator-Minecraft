package dev.kultivator.sound;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * The film presentation sounds of Phase 30 (DESIGN 30.2.11, contract C30.4): Kenney CC0 sound effects under
 * {@code assets/kultivator/sounds/film/}, one event {@code kultivator:film.<id>} each (see {@code sounds.json}).
 * Registered on both sides from the main initializer; only the client plays them, through
 * {@code client/epic/sound/FilmSound} (never as world sounds from the server).
 */
public final class FilmSounds {

	public static final SoundEvent RISER_LONG = make("riser_long");
	public static final SoundEvent RISER_SHORT = make("riser_short");
	public static final SoundEvent HUM_ENERGY = make("hum_energy");
	public static final SoundEvent HUM_VORTEX = make("hum_vortex");
	public static final SoundEvent WHOOSH = make("whoosh");
	public static final SoundEvent ZAP = make("zap");
	public static final SoundEvent TWINKLE = make("twinkle");
	public static final SoundEvent CRUNCH = make("crunch");
	public static final SoundEvent CLANG = make("clang");
	public static final SoundEvent SUB_BOOM = make("sub_boom");
	public static final SoundEvent BELL = make("bell");
	public static final SoundEvent GLASS = make("glass");
	public static final SoundEvent PUNCH = make("punch");
	public static final SoundEvent GONG = make("gong");
	public static final SoundEvent THUD = make("thud");
	public static final SoundEvent ROCK = make("rock");

	/** Every event, in registration order. */
	public static final List<SoundEvent> ALL = List.of(RISER_LONG, RISER_SHORT, HUM_ENERGY, HUM_VORTEX, WHOOSH, ZAP,
			TWINKLE, CRUNCH, CLANG, SUB_BOOM, BELL, GLASS, PUNCH, GONG, THUD, ROCK);

	private static boolean registered;

	private static SoundEvent make(String id) {
		return SoundEvent.createVariableRangeEvent(Kultivator.id("film." + id));
	}

	/** Registers every event once (called by {@code Kultivator.onInitialize}). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (SoundEvent event : ALL) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event);
		}
	}

	private FilmSounds() {
	}
}
