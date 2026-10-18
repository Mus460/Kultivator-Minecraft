package dev.kultivator.technique.spec.dsl;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * One sound of a phase (DESIGN 22.4.6): played on the server with {@code level.playSound(null, ...)}, at most 4 cues per
 * phase; a volume above 1 widens the range. {@code delay} ticks after the phase event.
 */
public record SoundCue(SoundEvent sound, float volume, float pitch, int delay) {

	public static SoundCue of(SoundEvent sound, float volume, float pitch) {
		return new SoundCue(sound, volume, pitch, 0);
	}

	/** For the {@code SoundEvents} fields that are holders. */
	public static SoundCue of(Holder<SoundEvent> sound, float volume, float pitch) {
		return new SoundCue(sound.value(), volume, pitch, 0);
	}

	public SoundCue delay(int ticks) {
		return new SoundCue(sound, volume, pitch, Math.max(0, ticks));
	}
}
