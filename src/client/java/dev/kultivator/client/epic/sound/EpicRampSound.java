package dev.kultivator.client.epic.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A client-local 7b sound with per-tick volume and pitch curves (DESIGN 23.12.1): the camera whoosh (0 → .35 → 0, pitch
 * .6 → 1.2 over a move) and the looping time-stretch layer. Volume follows a triangle-shaped envelope over its life, or a
 * held level that can be released.
 */
public final class EpicRampSound extends AbstractTickableSoundInstance {

	private final int life;
	private final float peak;
	private final float pitchFrom;
	private final float pitchTo;
	private final boolean held;
	private int age;
	private boolean releasing;
	private int releaseLeft;

	/** A one-shot ramp: volume 0 → {@code peak} → 0 and pitch {@code from} → {@code to} over {@code life} ticks (non-positional). */
	public EpicRampSound(SoundEvent event, SoundSource source, int life, float peak, float from, float to, boolean loop) {
		super(event, source, SoundInstance.createUnseededRandom());
		this.life = Math.max(2, life);
		this.peak = peak;
		this.pitchFrom = from;
		this.pitchTo = to;
		this.held = loop;
		this.looping = loop;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.volume = 0.0001F;
		this.pitch = from;
	}

	/** Fades a held sound out over {@code ticks}, then stops it. */
	public void release(int ticks) {
		if (!releasing) {
			releasing = true;
			releaseLeft = Math.max(1, ticks);
		}
	}

	@Override
	public void tick() {
		age++;
		if (releasing) {
			releaseLeft--;
			volume = Math.max(0.0001F, volume * Math.max(0.0F, releaseLeft) / (releaseLeft + 1.0F));
			if (releaseLeft <= 0) {
				stop();
			}
			return;
		}
		if (held) {
			volume = Math.min(peak, peak * age / 6.0F);
			pitch = pitchFrom;
			if (age > 20 * 30) {
				release(10);
			}
			return;
		}
		float t = Math.min(1.0F, age / (float) life);
		float envelope = t < 0.5F ? t * 2.0F : (1.0F - t) * 2.0F;
		volume = Math.max(0.0001F, peak * envelope);
		pitch = Mth.lerp(t, pitchFrom, pitchTo);
		if (age >= life) {
			stop();
		}
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
