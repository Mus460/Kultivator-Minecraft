package dev.kultivator.client.epic.sound;

import dev.kultivator.client.epic.EpicClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-local presentation sounds of 7b (DESIGN 23.1.7, 23.12.1): UI stings and the card seal for the protagonist,
 * camera whooshes, the time-stretch layer and per-element notes every client in range derives from the same cue. World
 * sounds stay server-side.
 * <p>
 * Ducking (23.11.2): while slow motion runs or a strike is about to land, the world categories AMBIENT, WEATHER, NEUTRAL
 * and BLOCKS go to 0.35 through {@code SoundManager.updateCategoryVolume} (in memory; the gain multiplies the player's
 * slider). HOSTILE, PLAYERS, MUSIC (owned by {@code MusicManager}), MASTER, VOICE, RECORDS and UI are never ducked. Gains
 * go back to 1 exactly at the release tick, and also on a 2 s timeout, skip, screen open, death, dimension change and
 * disconnect.
 */
public final class EpicSound {

	private static final SoundSource[] DUCKED = {SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.NEUTRAL, SoundSource.BLOCKS};
	public static final float DUCK_GAIN = 0.35F;
	public static final int MAX_DUCK = 40;

	private static boolean ducked;
	private static int duckLeft;
	private static int slowTicks;
	private static @Nullable EpicRampSound stretch;

	/** A UI sound for the protagonist. */
	public static void ui(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	/** A positioned client sound (pillar notes, bell rows, Dao cues). */
	public static void at(Vec3 pos, SoundEvent sound, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch,
				SoundInstance.createUnseededRandom(), pos.x, pos.y, pos.z));
	}

	/** The card seal: {@code BELL_BLOCK} p 0.5 + {@code AMETHYST_BLOCK_RESONATE} p 0.7. */
	public static void seal() {
		ui(SoundEvents.BELL_BLOCK, 0.5F, 0.6F);
		ui(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.7F);
	}

	/** The success sting. */
	public static void sting() {
		ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.6F);
	}

	/** The hit-stop sub-bass: {@code WARDEN_SONIC_BOOM} 0.35 p 0.5. */
	public static void subBass() {
		ui(SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 0.35F);
	}

	/** A camera whoosh over {@code ticks}: volume 0 → 0.35 → 0, pitch 0.6 → 1.2. */
	public static void whoosh(int ticks) {
		Minecraft.getInstance().getSoundManager().play(new EpicRampSound(SoundEvents.ELYTRA_FLYING, SoundSource.UI, Math.max(10, ticks), 0.35F, 0.6F,
				1.2F, false));
	}

	/** Ducks the world categories for {@code ticks} (≤ 2 s). */
	public static void duck(int ticks) {
		duckLeft = Math.max(duckLeft, Math.min(MAX_DUCK, ticks));
		apply(true);
	}

	/** Restores every gain and stops the time-stretch layer at once. */
	public static void restore() {
		duckLeft = 0;
		slowTicks = 0;
		apply(false);
		if (stretch != null) {
			stretch.release(4);
			stretch = null;
		}
	}

	public static boolean busy() {
		return ducked || stretch != null;
	}

	/** Once per client tick while a scene runs. */
	public static void tick() {
		boolean slow = EpicClock.slowed();
		if (slow) {
			slowTicks++;
			if (slowTicks <= MAX_DUCK) {
				apply(true);
				if (stretch == null) {
					stretch = new EpicRampSound(SoundEvents.BEACON_AMBIENT, SoundSource.UI, 40, 0.4F, 0.5F, 0.5F, true);
					Minecraft.getInstance().getSoundManager().play(stretch);
				}
			}
		} else {
			slowTicks = 0;
			if (stretch != null) {
				stretch.release(8);
				stretch = null;
			}
		}
		if (duckLeft > 0) {
			duckLeft--;
		}
		if (ducked && duckLeft <= 0 && (!slow || slowTicks > MAX_DUCK)) {
			apply(false);
		}
	}

	private static void apply(boolean on) {
		if (ducked == on) {
			return;
		}
		ducked = on;
		SoundManager manager = Minecraft.getInstance().getSoundManager();
		for (SoundSource source : DUCKED) {
			manager.updateCategoryVolume(source, on ? DUCK_GAIN : 1.0F);
		}
	}

	private EpicSound() {
	}
}
