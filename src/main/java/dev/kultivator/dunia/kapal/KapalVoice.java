package dev.kultivator.dunia.kapal;

import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The ship's spoken moments (DESIGN 34.8, the Japanese lines {@code voice.kapal.*} and {@code voice.peta.site_found}),
 * played through the voice API. Each moment names its own line and a reused fallback: while a line is not registered
 * ({@link VoiceEvents#exists}) the fallback plays instead (or nothing when there is none), so a build without the new
 * clips stays quiet and correct. Every call is rate-limited by {@link Voice} itself.
 */
public final class KapalVoice {

	public static final String LAUNCH = "kapal.launch";
	public static final String CLOUD_LEAP = "kapal.cloud_leap";
	public static final String ARRIVE = "kapal.arrive";
	public static final String SKY_PIRATES = "kapal.sky_pirates";
	public static final String HULL_BROKEN = "kapal.hull_broken";
	public static final String SITE_FOUND = "peta.site_found";

	/** The line to play: {@code key} when it exists, else {@code fallback} when that exists, else null. */
	public static @Nullable String pick(String key, @Nullable String fallback) {
		if (VoiceEvents.exists(key)) {
			return key;
		}
		return fallback != null && VoiceEvents.exists(fallback) ? fallback : null;
	}

	/** The helmsman's voice to one rider (non-positional, captioned "Juru Mudi"). */
	public static void crew(@Nullable ServerPlayer player, String key, @Nullable String fallback) {
		String line = pick(key, fallback);
		if (player != null && line != null) {
			Voice.inner(player, line, Component.translatable("kultivator.kapal.voice.crew"));
		}
	}

	/** The voice of the sky to one rider (a cloud leap, a site written into the map), captioned "Langit". */
	public static void sky(@Nullable ServerPlayer player, String key, @Nullable String fallback) {
		String line = pick(key, fallback);
		if (player != null && line != null) {
			Voice.inner(player, line, Component.translatable("kultivator.voice.speaker.heaven"));
		}
	}

	/** {@code speaker} says the line in the world (positional, heard within 24 blocks). */
	public static void say(@Nullable Entity speaker, String key, @Nullable String fallback) {
		String line = pick(key, fallback);
		if (speaker != null && line != null) {
			Voice.say(speaker, line, null);
		}
	}

	private KapalVoice() {
	}
}
