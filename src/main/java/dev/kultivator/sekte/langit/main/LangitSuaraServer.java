package dev.kultivator.sekte.langit.main;

import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Voices of the sky events (DESIGN 37.6.6, pattern {@code KapalVoice}): a line plays only when its key is a registered
 * voice event ({@code VoiceEvents.exists}); the new Japanese lines of 37G ({@code langit.*}, listed in the module report
 * and added to {@code sounds.json} by the voice pass, never by this module) fall back to an existing line until then.
 */
public final class LangitSuaraServer {

	/** {@code speaker} says {@code key} (or {@code fallback}, or nothing). */
	public static void say(@Nullable Entity speaker, String key, @Nullable String fallback) {
		String line = pick(key, fallback);
		if (speaker != null && line != null) {
			Voice.say(speaker, line, null);
		}
	}

	/** Heaven's voice to the players within {@code radius} of {@code pos} (or the fallback, or nothing). */
	public static void heaven(@Nullable ServerLevel level, Vec3 pos, String key, @Nullable String fallback, double radius) {
		String line = pick(key, fallback);
		if (level != null && line != null) {
			Voice.heavenNear(level, pos, line, radius);
		}
	}

	/** Heaven's voice to one player. */
	public static void heaven(@Nullable ServerPlayer player, String key, @Nullable String fallback) {
		String line = pick(key, fallback);
		if (player != null && line != null) {
			Voice.heaven(player, line);
		}
	}

	static @Nullable String pick(String key, @Nullable String fallback) {
		if (key != null && VoiceEvents.exists(key)) {
			return key;
		}
		return fallback != null && VoiceEvents.exists(fallback) ? fallback : null;
	}

	private LangitSuaraServer() {
	}
}
