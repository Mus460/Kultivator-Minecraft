package dev.kultivator.client.epic.sound;

import dev.kultivator.client.epic.EpicSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.attribute.BackgroundMusic;
import org.jetbrains.annotations.Nullable;

/**
 * The non-diegetic music cues of the protagonist (DESIGN 23.12.2, setting "Musik sinematik"). A cue starts through the
 * camera's environment probe: {@code SkyDirector} returns {@code new BackgroundMusic(new Music(cue, 0, 0, true))} for
 * {@code BACKGROUND_MUSIC}, and {@code MusicManager.tick} replaces the current track at once for replace-current music;
 * the now-playing toast is hidden right after. Ducking goes through {@code MUSIC_VOLUME} (0.25 during omens). The release
 * ramps {@code MUSIC_VOLUME} 1 → 0.05 over 40 ticks, then {@code stopPlaying(cue)}; vanilla music resumes by itself. A cue
 * is only returned for its length, so it never loops. The moods are Presentasi.
 */
public final class MusicCue {

	/** The cue table (vanilla events; lengths in seconds). */
	public enum Cue {
		M_OMEN(SoundEvents.MUSIC_DISC_13, 12),
		M_STORM(SoundEvents.MUSIC_DRAGON, 12),
		M_NETHER(SoundEvents.MUSIC_BIOME_SOUL_SAND_VALLEY, 12),
		M_END(SoundEvents.MUSIC_END, 12),
		M_HEART(SoundEvents.MUSIC_DISC_11, 10),
		M_DREAD(SoundEvents.MUSIC_DISC_5, 30),
		M_TRIUMPH(SoundEvents.MUSIC_DISC_RELIC, 14),
		M_GLORY(SoundEvents.MUSIC_DISC_CREATOR, 18),
		M_GENTLE(SoundEvents.MUSIC_DISC_CREATOR_MUSIC_BOX, 12),
		M_ASCEND(SoundEvents.MUSIC_CREDITS, 35),
		M_BOUT(SoundEvents.MUSIC_DISC_PRECIPICE, 60),
		M_SORROW(SoundEvents.MUSIC_DISC_TEARS, 10);

		private final Music music;
		private final BackgroundMusic background;
		private final int seconds;

		Cue(Holder<SoundEvent> sound, int seconds) {
			this.music = new Music(sound, 0, 0, true);
			this.background = new BackgroundMusic(music);
			this.seconds = seconds;
		}

		public int seconds() {
			return seconds;
		}
	}

	public static final float DUCK = 0.25F;
	public static final int RELEASE_TICKS = 40;

	private static @Nullable Cue current;
	private static int priority = -1;
	private static int left;
	private static int releaseLeft;
	private static boolean toastHidden;
	private static float duck = 1.0F;
	private static int duckLeft;
	private static boolean active;

	/** Starts a cue for {@code seconds} (≤ its table length; ≤ 0 = the table length); a lower priority is dropped. */
	public static void play(Cue cue, int seconds, int prio) {
		if (!EpicSettings.music()) {
			return;
		}
		if (current != null && releaseLeft == 0 && prio < priority) {
			return;
		}
		if (current != null && current != cue) {
			stopNow();
		}
		current = cue;
		priority = prio;
		left = (seconds <= 0 ? cue.seconds() : Math.min(seconds, cue.seconds())) * 20;
		releaseLeft = 0;
		toastHidden = false;
		duck = 1.0F;
		duckLeft = 0;
		active = true;
	}

	/** Ducks the music to 0.25 for {@code ticks} (omens before a cue). */
	public static void duck(int ticks) {
		if (!EpicSettings.music()) {
			return;
		}
		duck = DUCK;
		duckLeft = Math.max(duckLeft, ticks);
		active = true;
	}

	/** Releases the running cue (40-tick ramp, then stop). */
	public static void release() {
		if (current != null && releaseLeft == 0) {
			releaseLeft = RELEASE_TICKS;
		}
	}

	/** Releases only when {@code cue} is the one playing. */
	public static void release(Cue cue) {
		if (current == cue) {
			release();
		}
	}

	/** Stops everything at once (skip, stop, death, dimension change, disconnect). */
	public static void clear() {
		stopNow();
		duck = 1.0F;
		duckLeft = 0;
		active = false;
	}

	public static boolean active() {
		return active;
	}

	public static @Nullable Cue current() {
		return current;
	}

	/** Once per client tick while active. */
	public static void tick() {
		if (!active) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (current != null) {
			if (!toastHidden && minecraft.getMusicManager().isPlayingMusic(current.music)) {
				toastHidden = true;
				minecraft.gui.toastManager().hideNowPlayingToast();
			}
			if (releaseLeft > 0) {
				if (--releaseLeft <= 0) {
					stopNow();
				}
			} else if (--left <= 0) {
				releaseLeft = RELEASE_TICKS;
			}
		}
		if (duckLeft > 0 && --duckLeft <= 0) {
			duck = 1.0F;
		}
		if (current == null && duckLeft <= 0) {
			active = false;
		}
	}

	/** The {@code BACKGROUND_MUSIC} override, or null for vanilla. */
	public static @Nullable BackgroundMusic background() {
		return current != null && releaseLeft == 0 ? current.background : null;
	}

	/** The {@code MUSIC_VOLUME} multiplier (1 = vanilla). */
	public static float volume() {
		if (current != null && releaseLeft > 0) {
			float t = releaseLeft / (float) RELEASE_TICKS;
			return 0.05F + 0.95F * t;
		}
		return current != null ? 1.0F : duck;
	}

	private static void stopNow() {
		if (current != null) {
			Minecraft.getInstance().getMusicManager().stopPlaying(current.music);
		}
		current = null;
		priority = -1;
		left = 0;
		releaseLeft = 0;
	}

	private MusicCue() {
	}
}
