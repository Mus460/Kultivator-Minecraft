package dev.kultivator.sound;

import dev.kultivator.Kultivator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * The sword-duel sound events of DESIGN 35.6.1 (contract C35.U5): {@code kultivator:duel.<id>}, each defined in
 * {@code sounds.json} with the subtitle {@code subtitles.kultivator.duel.<id>}. No new audio files: every event wraps
 * the Kenney CC0 oggs already shipped under {@code sounds/film/} at its own pitch and volume, or points at a vanilla
 * event ({@code "type": "event"}); the extra vanilla layers of 35.6.1 are added by the client's
 * {@code client.duel.sound.DuelSoundPlayer}. Registered on both sides once from {@code Duel.init} (module S); only the
 * client plays them, from the same {@code DuelClash}/{@code DuelSwing} every client receives, so a sound lands on the
 * frame of its sparks. The server never plays a clash sound itself (C35.S4).
 * <p>
 * Positional events use only the mono Kenney files (clang, crunch, whoosh, zap, riser, hum); the stereo ones (gong,
 * bell, sub_boom, thud) are only ever played to the listener himself, without a position.
 */
public final class DuelSounds {

	/** Light blade on blade: a bright clang (pitch 0.95-1.15 chosen by the player). */
	public static final SoundEvent CLASH = make("clash");
	/** Heavy blade on blade, the start of a Blade Lock: a low ringing clang. */
	public static final SoundEvent CLASH_HEAVY = make("clash_heavy");
	/** Steel grinding on steel during a Blade Lock (the grindstone, pitched up; repeated every 3 ticks). */
	public static final SoundEvent SCRAPE = make("scrape");
	/** A Deflect: a short high clang. */
	public static final SoundEvent PARRY = make("parry");
	/** A Perfect Deflect: the long, singing ring. */
	public static final SoundEvent PARRY_PERFECT = make("parry_perfect");
	/** A held guard takes the blow (shield thump, pitched up). */
	public static final SoundEvent BLOCK = make("block");
	/** The guard is broken open. */
	public static final SoundEvent GUARD_BREAK = make("guard_break");
	/** Posture broken: the crunch of a stance giving way. */
	public static final SoundEvent POSTURE_BREAK = make("posture_break");
	/** The Deathblow (after 3 ticks of silence). */
	public static final SoundEvent DEATHBLOW = make("deathblow");
	/** A feint: the blade's whoosh, cut short. */
	public static final SoundEvent FEINT = make("feint");
	/** A Sword Qi Wave leaves the blade. */
	public static final SoundEvent QI_WAVE = make("qi_wave");
	/** Qi Reflection: the wave is sent back. */
	public static final SoundEvent QI_REFLECT = make("qi_reflect");
	/** The blade reaches the body. */
	public static final SoundEvent HIT = make("hit");
	/** The intro wind over the ring. */
	public static final SoundEvent INTRO_WIND = make("intro_wind");
	/** The gong that opens the bout (stereo: only for the duellists themselves). */
	public static final SoundEvent GONG = make("gong");
	/** The winner sheathes the blade. */
	public static final SoundEvent SHEATHE = make("sheathe");

	/** Every event, in registration order. */
	public static final List<SoundEvent> ALL = List.of(CLASH, CLASH_HEAVY, SCRAPE, PARRY, PARRY_PERFECT, BLOCK, GUARD_BREAK, POSTURE_BREAK,
			DEATHBLOW, FEINT, QI_WAVE, QI_REFLECT, HIT, INTRO_WIND, GONG, SHEATHE);

	private static boolean registered;

	private static SoundEvent make(String id) {
		return SoundEvent.createVariableRangeEvent(Kultivator.id("duel." + id));
	}

	/** Registers every event once (called by {@code Duel.init}, module S, contract C35.S1). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (SoundEvent event : ALL) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event);
		}
	}

	private DuelSounds() {
	}
}
