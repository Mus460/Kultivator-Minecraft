package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * The sounds of the themed-town effects: one non-positional looping bed per effect (the sandstorm wind, the storm-bay
 * surf) that slews its volume and pitch toward what the effect asks each tick and stops itself after a quiet second,
 * and one-shot positional gusts. Vanilla sound events only (checked in mcsrc {@code SoundEvents}, 26.3:
 * {@code ELYTRA_FLYING}, {@code SAND_IDLE}, {@code WEATHER_RAIN}, {@code AMBIENT_UNDERWATER_LOOP} are {@code SoundEvent}s),
 * source AMBIENT so the player's ambient slider governs them; loudness is capped at {@link #MAX}.
 */
final class TemaSuara {

	/** The loudest bed. */
	static final float MAX = 0.6F;

	/** A looping bed heard everywhere around the listener (relative, no attenuation). */
	static final class Lingkup extends AbstractTickableSoundInstance {

		private float target;
		private float targetPitch;
		private int sepi;

		Lingkup(SoundEvent event, float pitch) {
			super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0001F;
			this.pitch = pitch;
			this.targetPitch = pitch;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0.0D;
			this.y = 0.0D;
			this.z = 0.0D;
		}

		/** What the effect wants this tick: volume 0..{@link #MAX} and pitch. */
		void minta(float volume, float pitch) {
			this.target = Math.max(0.0F, Math.min(MAX, volume));
			this.targetPitch = Math.max(0.5F, Math.min(2.0F, pitch));
		}

		@Override
		public void tick() {
			volume += (target - volume) * 0.12F;
			pitch += (targetPitch - pitch) * 0.08F;
			if (target <= 0.0F && volume < 0.002F) {
				volume = 0.0001F;
				if (++sepi > 20) {
					stop();
				}
			} else {
				sepi = 0;
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		boolean selesai() {
			return isStopped();
		}
	}

	private TemaSuara() {
	}

	/**
	 * Keeps {@code bed} alive and aimed: starts a new one when it is null or stopped and {@code volume > 0}; returns the
	 * bed in use (null when silent).
	 */
	static @Nullable Lingkup jaga(@Nullable Lingkup bed, SoundEvent event, float volume, float pitch) {
		if (bed != null && !bed.selesai()) {
			bed.minta(volume, pitch);
			return bed;
		}
		if (volume <= 0.003F) {
			return null;
		}
		Lingkup fresh = new Lingkup(event, pitch);
		fresh.minta(volume, pitch);
		try {
			Minecraft.getInstance().getSoundManager().play(fresh);
		} catch (RuntimeException exception) {
			return null;
		}
		return fresh;
	}

	/** Fades {@code bed} out (it stops itself once quiet). */
	static void lepas(@Nullable Lingkup bed) {
		if (bed != null) {
			bed.minta(0.0F, 1.0F);
		}
	}

	/** One quiet positional sound. */
	static void sekali(SoundEvent event, float volume, float pitch, double x, double y, double z) {
		float v = Math.max(0.0F, Math.min(MAX, volume));
		if (v <= 0.0F) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, v, pitch,
				SoundInstance.createUnseededRandom(), x, y, z));
	}
}
