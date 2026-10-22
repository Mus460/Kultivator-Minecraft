package dev.kultivator.client.dunia.langit;

import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The ambient sounds of module L (detail pass), client only, a few vanilla sounds far and quiet on the ambient channel
 * (its own volume slider), each on a long random timer so they never pile up:
 * <ul>
 * <li>Hujan Meteor Roh: a faint high chime now and then as streaks cross, a far crackle when a fireball bursts;</li>
 * <li>Gerhana Darah: a low cave rumble from somewhere in the dark every 25-45 s, and at totality a slow distant
 * heartbeat;</li>
 * <li>Bulan Kembar: a deep crystal hum every 30-50 s;</li>
 * <li>Kabut Qi Pekat and Fajar Ungu: a soft beacon-like hum of dense Qi every 40-60 s.</li>
 * </ul>
 * Only outdoors (the sky must be seen), only while the event or mist is shown; nothing at all while the game is paused.
 */
final class LangitAmbience {

	private static int eventTimer = 200;
	private static int mistTimer = 300;
	private static int heartTimer = 120;

	static void tick(ClientLevel level, Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		boolean outdoors = level.canSeeSky(minecraft.player.blockPosition().above());
		Vec3 at = minecraft.player.position();
		SkyKind kind = LangitState.shown();
		float weight = LangitState.skyWeight;
		if (kind != null && outdoors && weight > 0.5F && --eventTimer <= 0) {
			switch (kind) {
				case METEOR_SHOWER -> {
					eventTimer = 140 + random.nextInt(220);
					LangitState.playAt(SoundEvents.AMETHYST_BLOCK_CHIME, far(at, random, 24.0D, 12.0D), 0.35F * weight, 1.5F + random.nextFloat() * 0.5F);
				}
				case BLOOD_ECLIPSE -> {
					eventTimer = 500 + random.nextInt(400);
					LangitState.playAt(SoundEvents.AMBIENT_CAVE.value(), far(at, random, 20.0D, -4.0D), 0.45F * weight, 0.6F + random.nextFloat() * 0.2F);
				}
				case TWIN_MOONS -> {
					eventTimer = 600 + random.nextInt(400);
					LangitState.playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, far(at, random, 16.0D, 10.0D), 0.4F * weight, 0.5F);
				}
			}
		}
		if (kind == SkyKind.BLOOD_ECLIPSE && outdoors && LangitState.eclipseCover(level, 0.0F) > 0.95F && --heartTimer <= 0) {
			heartTimer = 70 + random.nextInt(50);
			LangitState.playAt(SoundEvents.WARDEN_HEARTBEAT, far(at, random, 14.0D, 0.0D), 0.25F * weight, 0.55F);
		}
		SpiritWeather weather = LangitState.weather;
		if ((weather == SpiritWeather.DENSE_MIST || weather == SpiritWeather.PURPLE_DAWN) && outdoors && LangitState.mist > 0.6F
				&& --mistTimer <= 0) {
			mistTimer = 800 + random.nextInt(400);
			LangitState.playAt(SoundEvents.BEACON_AMBIENT, far(at, random, 10.0D, 2.0D), 0.25F,
					weather == SpiritWeather.PURPLE_DAWN ? 0.75F : 0.55F);
		}
	}

	/** A fireball burst high up (from {@link LangitSky}): a far crackle in its direction. */
	static void fireball(float dx, float dy, float dz) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Vec3 at = minecraft.player.position().add(dx * 24.0D, Math.max(4.0D, dy * 24.0D), dz * 24.0D);
		LangitState.playAt(SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, at, 0.6F, 0.7F);
	}

	/** A point {@code distance} blocks away in a random horizontal direction, {@code up} blocks higher. */
	private static Vec3 far(Vec3 at, RandomSource random, double distance, double up) {
		float angle = random.nextFloat() * Mth.TWO_PI;
		return at.add(Mth.cos(angle) * distance, up, Mth.sin(angle) * distance);
	}

	static void clear() {
		eventTimer = 200;
		mistTimer = 300;
		heartTimer = 120;
	}

	private LangitAmbience() {
	}
}
