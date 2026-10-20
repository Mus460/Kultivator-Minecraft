package dev.kultivator.client.dampak.cinema;

import dev.kultivator.client.dampak.DampakClient;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.SoundCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Dampak Sunyi (Phase 10, DESIGN 28.4.3): the strike of a silent row lands without a sound and the boom arrives after
 * the silence plus the distance (40 b/s, the vanilla thunder speed, at most 16 ticks). The server skips the cues of the
 * climax event of such a spec (28.1.1 rule 3, the only server-visible change); every client that receives the event
 * plays the same cues ({@code spec.sounds(phase)}, ≤ 4) itself, at the same position, through
 * {@code SoundManager.playDelayed}. This runs before any VFX gate, so "Efek pemain lain: Mati" still hears the boom.
 * During the silence the world categories duck to 0.35 ({@link EpicSound#duck}: never HOSTILE or PLAYERS), and a
 * sub-bass lands with the boom for a nearby viewer. "Dampak sunyi: Mati" plays the cues at their own delays.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10): the boom of a tier 2-9 decree also travels at about 340 m/s, so a listener far away
 * hears it up to 120 ticks later ({@code max(silence + near travel, min(120, distance / 17))}); tier 1 keeps the timing
 * above.
 */
public final class SilentImpact {

	/** The farthest a viewer gets the duck and the sub-bass (the boom itself plays at any distance it reaches). */
	private static final double NEAR = 48.0D;

	private SilentImpact() {
	}

	/** The climax event of a spec with {@code silence > 0} (every client, own casts and others'). */
	public static void onClimax(Payloads.TechFx p, TechniqueSpec spec, Dampak d, Phase phase, boolean own) {
		List<SoundCue> cues = spec.sounds(phase);
		Vec3 at = new Vec3(p.x(), p.y(), p.z());
		int tier = spec.has(TechniqueSpec.Flag.TITAH) ? Math.max(1, Math.min(9, 1 + (p.aux() >> 14 & 0xF))) : 1;
		play(cues, at, d.silence(), p.seed(), own, tier);
	}

	/** Plays {@code cues} at {@code at} after the silence and the distance; returns the boom's delay in ticks. */
	public static int play(List<SoundCue> cues, Vec3 at, int silence, int seed, boolean own) {
		return play(cues, at, silence, seed, own, 1);
	}

	/** {@link #play} of a Titah decree of Tingkat {@code tier}: from tier 2 the boom travels at about 17 b per tick. */
	public static int play(List<SoundCue> cues, Vec3 at, int silence, int seed, boolean own, int tier) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return 0;
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		double distance = camera.distanceTo(at);
		boolean on = EpicSettings.silentImpact();
		int travel = (int) Math.min(16L, Math.round(distance / 2.0D));
		int extra = on ? Math.max(0, silence) + travel : 0;
		if (on && tier >= 2) {
			// a giant decree: the boom rolls in at the speed of sound (about 17 b per tick), at most 6 s late
			extra = Math.max(extra, (int) Math.min(120L, Math.round(distance / 17.0D)));
		}
		SoundManager sounds = minecraft.getSoundManager();
		int count = Math.min(4, cues.size());
		for (int i = 0; i < count; i++) {
			SoundCue cue = cues.get(i);
			SimpleSoundInstance instance = new SimpleSoundInstance(cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch(),
					RandomSource.create(seed + i), at.x, at.y, at.z);
			int delay = Math.max(0, cue.delay()) + extra;
			if (delay <= 0) {
				sounds.play(instance);
			} else {
				sounds.playDelayed(instance, delay);
			}
		}
		if (on && extra > 0 && (own || distance <= NEAR)) {
			// the held breath: the world goes quiet until the boom, which lands with a sub-bass
			EpicSound.duck(extra + 2);
			if (own || distance <= 32.0D) {
				DampakClient.later(extra, EpicSound::subBass);
			}
		}
		return extra;
	}

	/** {@code /sinematik dampak sunyi <jarak>}: a test boom {@code distance} blocks ahead with a Dewa silence (6 ticks). */
	public static int test(double distance) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return 0;
		}
		Vec3 eye = minecraft.player.getEyePosition();
		Vec3 look = minecraft.player.getViewVector(1.0F);
		Vec3 at = eye.add(new Vec3(look.x, 0.0D, look.z).normalize().scale(Math.max(0.0D, distance)));
		SoundEvent thunder = SoundEvents.TRIDENT_THUNDER.value();
		List<SoundCue> cues = List.of(SoundCue.of(SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F), new SoundCue(thunder, 1.4F, 0.8F, 0));
		return play(cues, at, 6, (int) (distance * 31.0D), true);
	}
}
