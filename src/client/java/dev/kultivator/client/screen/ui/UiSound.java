package dev.kultivator.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** The theme's UI sounds: amethyst chimes and bamboo-like note blocks instead of the vanilla button click. */
public final class UiSound {

	/** A talisman pressed: a bright chime. */
	public static void press() {
		play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F, 0.35F);
	}

	/** A jade tablet chosen: the amethyst chime. */
	public static void tab() {
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.25F, 0.9F);
	}

	/** A jade coin switched: a hollow bamboo knock, higher when switched on. */
	public static void toggle(boolean on) {
		play(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), on ? 1.6F : 1.1F, 0.35F);
	}

	/** Something that cannot be done right now: a low wooden knock. */
	public static void denied() {
		play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F, 0.35F);
	}

	/** A choice that asks to be confirmed (second click). */
	public static void ask() {
		play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.3F);
	}

	/** A soft tick when the mouse enters a tablet or trigram. */
	public static void hover() {
		play(SoundEvents.AMETHYST_CLUSTER_STEP, 1.8F, 0.25F);
	}

	/** The scroll unrolls. */
	public static void open() {
		play(SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.8F);
		play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F);
	}

	/** The scroll rolls up. */
	public static void close() {
		play(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.6F);
	}

	private static void play(SoundEvent sound, float pitch, float volume) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private UiSound() {
	}
}
