package dev.kultivator.client.aura;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The quiet realm hum of an Aura Kultivasi (DESIGN 31.2.9): a vanilla looping sound bound to the cultivator
 * ({@link SoundSource#AMBIENT}, so the vanilla "Ambient/Environment" slider applies), at most 0.08 loud and × the aura's
 * fade, for the nearest one or two active auras within 12 blocks; plus a rare one-shot chime (≤ 0.12, at most one per 60
 * ticks per aura). Film sounds are never used. "Dengung aura: Mati" silences all of it.
 * <p>
 * Vanilla sounds checked in mcsrc {@code SoundEvents} (26.3): {@code BEACON_AMBIENT}, {@code CONDUIT_AMBIENT},
 * {@code RESPAWN_ANCHOR_AMBIENT}, {@code AMETHYST_BLOCK_CHIME}, {@code BELL_RESONATE}, {@code ENCHANTMENT_TABLE_USE} are
 * {@code SoundEvent}s; {@code NOTE_BLOCK_CHIME} is a {@code Holder.Reference} (use {@code .value()}).
 */
public final class AuraHum {

	/** The loudest hum. */
	public static final float MAX_VOLUME = 0.08F;
	/** The loudest chime. */
	public static final float MAX_CHIME = 0.12F;
	/** The usual chime volume. */
	public static final float CHIME_VOLUME = 0.08F;
	/** Hums are heard from this far (blocks). */
	public static final double RANGE = 12.0D;
	/** Chimes of one aura come at most this often (ticks). */
	public static final int MIN_CHIME_EVERY = 60;

	/**
	 * What a realm sounds like: {@code loop} at {@code volume} and {@code pitch} (null: no hum), and {@code chime} every
	 * {@code chimeEvery} ticks at {@code chimePitch} (null or 0: none).
	 */
	public record Spec(@Nullable SoundEvent loop, float volume, float pitch, @Nullable SoundEvent chime, float chimePitch, int chimeEvery) {

		public static final Spec NONE = new Spec(null, 0.0F, 1.0F, null, 1.0F, 0);

		public Spec {
			volume = Math.max(0.0F, Math.min(MAX_VOLUME, volume));
		}

		/** A hum without a chime. */
		public static Spec loop(SoundEvent loop, float volume, float pitch) {
			return new Spec(loop, volume, pitch, null, 1.0F, 0);
		}

		public boolean silent() {
			return loop == null && chime == null;
		}
	}

	/** The looping hum of one aura, following its cultivator. Volume and position are set by the engine each tick. */
	static final class Loop extends AbstractTickableSoundInstance {

		private final SoundEvent event;
		private float target;
		private boolean releasing;
		private int silentTicks;

		Loop(SoundEvent event, float pitch, Vec3 at) {
			super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.event = event;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0001F;
			this.pitch = pitch;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
		}

		SoundEvent event() {
			return event;
		}

		/** The engine's wish for this tick: the volume (already × fade) and where the cultivator is. */
		void aim(float volume, Vec3 at) {
			this.target = Math.max(0.0F, Math.min(MAX_VOLUME, volume));
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
		}

		/** Fades out over a few ticks, then stops. */
		void release() {
			releasing = true;
		}

		boolean released() {
			return releasing || isStopped();
		}

		@Override
		public void tick() {
			float goal = releasing ? 0.0F : target;
			// a gentle slew so the hum never clicks in or out
			volume += (goal - volume) * 0.25F;
			if (volume < 0.0015F && (releasing || goal <= 0.0F)) {
				volume = 0.0001F;
				if (releasing || ++silentTicks > 40) {
					stop();
				}
			} else {
				silentTicks = 0;
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

	/** Starts a looping hum; null when it could not be played. */
	static @Nullable Loop start(SoundEvent event, float pitch, Vec3 at) {
		Minecraft minecraft = Minecraft.getInstance();
		Loop loop = new Loop(event, pitch, at);
		try {
			minecraft.getSoundManager().play(loop);
		} catch (RuntimeException exception) {
			return null;
		}
		return loop;
	}

	/** One quiet positional chime. */
	static void chime(SoundEvent event, float volume, float pitch, Vec3 at) {
		Minecraft minecraft = Minecraft.getInstance();
		float v = Math.max(0.0F, Math.min(MAX_CHIME, volume));
		if (v <= 0.0F) {
			return;
		}
		minecraft.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, v, pitch, SoundInstance.createUnseededRandom(),
				at.x, at.y, at.z));
	}

	/** The breakthrough-ready bell (31.3): {@code BELL_RESONATE}, pitch 1.6, volume 0.06. */
	static void bottleneckBell(Vec3 at) {
		chime(SoundEvents.BELL_RESONATE, 0.06F, 1.6F, at);
	}

	private AuraHum() {
	}
}
