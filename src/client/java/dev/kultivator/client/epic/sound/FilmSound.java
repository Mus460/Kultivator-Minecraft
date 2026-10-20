package dev.kultivator.client.epic.sound;

import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.sound.FilmSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Kenney CC0 presentation cues of the film (DESIGN 30.2.11, 30.2.12), client side only. Stereo files never play
 * positioned (Minecraft does not attenuate stereo): they are protagonist-only cues, relative with no attenuation. Mono
 * files may be positioned (every client in range derives them from the same TechFx). At most three film cues start per
 * client tick; the silence of a silent impact is never filled (the caller times {@code SUB_BOOM} on the delayed boom).
 * Own cues play under {@link SoundSource#MASTER} (never ducked by the cosmic shots, ruled by the master slider), the
 * positioned world cues under {@link SoundSource#PLAYERS}.
 */
public final class FilmSound {

	/** One film cue: its event and whether its files are stereo (own client only, never positioned). */
	public enum Cue {
		RISER_LONG(false), RISER_SHORT(false), HUM_ENERGY(false), HUM_VORTEX(false), WHOOSH(false), ZAP(false), TWINKLE(false),
		CRUNCH(false), CLANG(false), SUB_BOOM(true), BELL(true), GLASS(true), PUNCH(true), GONG(true), THUD(true), ROCK(true);

		public final boolean stereo;

		Cue(boolean stereo) {
			this.stereo = stereo;
		}

		public SoundEvent event() {
			return switch (this) {
				case RISER_LONG -> FilmSounds.RISER_LONG;
				case RISER_SHORT -> FilmSounds.RISER_SHORT;
				case HUM_ENERGY -> FilmSounds.HUM_ENERGY;
				case HUM_VORTEX -> FilmSounds.HUM_VORTEX;
				case WHOOSH -> FilmSounds.WHOOSH;
				case ZAP -> FilmSounds.ZAP;
				case TWINKLE -> FilmSounds.TWINKLE;
				case CRUNCH -> FilmSounds.CRUNCH;
				case CLANG -> FilmSounds.CLANG;
				case SUB_BOOM -> FilmSounds.SUB_BOOM;
				case BELL -> FilmSounds.BELL;
				case GLASS -> FilmSounds.GLASS;
				case PUNCH -> FilmSounds.PUNCH;
				case GONG -> FilmSounds.GONG;
				case THUD -> FilmSounds.THUD;
				case ROCK -> FilmSounds.ROCK;
			};
		}
	}

	public static final int MAX_PER_TICK = 3;

	private static int startedThisTick;
	private static @Nullable Ramp hum;
	private static @Nullable Ramp riser;

	/** Once per client tick. */
	public static void tick() {
		startedThisTick = 0;
	}

	private static boolean take() {
		if (startedThisTick >= MAX_PER_TICK) {
			return false;
		}
		startedThisTick++;
		return true;
	}

	/** A cue for the protagonist (non-positional). */
	public static void own(Cue cue, float volume, float pitch) {
		if (!take() || Minecraft.getInstance().player == null) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(cue.event().location(), SoundSource.MASTER, volume * vfxVolume(),
				pitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true));
	}

	/** A cue at a world point: mono cues are positioned, stereo cues fall back to {@link #own}. */
	public static void at(Cue cue, Vec3 pos, float volume, float pitch) {
		if (cue.stereo) {
			own(cue, volume, pitch);
			return;
		}
		if (!take()) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(cue.event(), SoundSource.PLAYERS, volume * vfxVolume(), pitch,
				SoundInstance.createUnseededRandom(), pos.x, pos.y, pos.z));
	}

	/** A riser for {@code ticks}: volume swells in, pitch ramps {@code from} → {@code to}, a short fade at the end. */
	public static void riser(Cue cue, int ticks, float volume, float from, float to) {
		if (!take()) {
			return;
		}
		if (riser != null) {
			riser.release(4);
		}
		riser = new Ramp(cue.event(), Math.max(6, ticks), volume * vfxVolume(), from, to, false);
		Minecraft.getInstance().getSoundManager().play(riser);
	}

	/** A held hum (cosmic shots, fields) at {@code pitch}; {@link #releaseHum} fades it. */
	public static void hum(Cue cue, int ticks, float volume, float pitch) {
		if (!take()) {
			return;
		}
		if (hum != null) {
			hum.release(6);
		}
		hum = new Ramp(cue.event(), Math.max(10, ticks), volume * vfxVolume(), pitch, pitch, true);
		Minecraft.getInstance().getSoundManager().play(hum);
	}

	public static void releaseHum(int ticks) {
		if (hum != null) {
			hum.release(ticks);
			hum = null;
		}
	}

	/** Stops every held cue (film end, skip, disconnect). */
	public static void stopAll() {
		releaseHum(6);
		if (riser != null) {
			riser.release(4);
			riser = null;
		}
	}

	private static float vfxVolume() {
		return Mth.clamp(0.6F + 0.4F * EpicSettings.vfx(), 0.6F, 1.2F);
	}

	/** A non-positional cue with a volume envelope and a pitch ramp; looping while held. */
	private static final class Ramp extends AbstractTickableSoundInstance {
		private final int life;
		private final float peak;
		private final float from;
		private final float to;
		private final boolean held;
		private int age;
		private int releaseLeft = -1;
		private int releaseTotal = 1;

		Ramp(SoundEvent event, int life, float peak, float from, float to, boolean held) {
			super(event, SoundSource.MASTER, SoundInstance.createUnseededRandom());
			this.life = life;
			this.peak = Math.max(0.01F, peak);
			this.from = from;
			this.to = to;
			this.held = held;
			this.looping = held;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.0001F;
			this.pitch = from;
		}

		void release(int ticks) {
			if (releaseLeft < 0) {
				releaseTotal = Math.max(1, ticks);
				releaseLeft = releaseTotal;
			}
		}

		@Override
		public void tick() {
			age++;
			if (releaseLeft >= 0) {
				releaseLeft--;
				volume = Math.max(0.0001F, peak * Math.max(0, releaseLeft) / (float) releaseTotal);
				if (releaseLeft <= 0) {
					stop();
				}
				return;
			}
			float t = Math.min(1.0F, age / (float) life);
			pitch = Mth.clamp(Mth.lerp(t, from, to), 0.5F, 2.0F);
			if (held) {
				volume = Math.min(peak, peak * age / 8.0F);
				if (age >= life) {
					release(10);
				}
				return;
			}
			float swell = Math.min(1.0F, t / 0.7F);
			volume = Math.max(0.0001F, peak * swell * swell);
			if (age >= life) {
				release(4);
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

	private FilmSound() {
	}
}
