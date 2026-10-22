package dev.kultivator.sekte;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceDisciples;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The Sekte yang Hidup voice lines (docs/progres/sekte-voices.md) with a fallback to the older line they replaced: when a
 * new key is not registered (an older resource set), the disciple, elder or boss says the old line instead.
 */
public final class SekteSuara {

	/** An own disciple says {@code line} (falls back to {@code fallback}) through {@link VoiceDisciples#line} and its cooldowns. */
	public static boolean line(@Nullable DiscipleEntity disciple, String line, String fallback, int cooldown) {
		return VoiceDisciples.line(disciple, VoiceEvents.exists("disciple.m1." + line) ? line : fallback, cooldown);
	}

	/** A sect body that is not an own disciple says the line in its own disciple voice, without the master's spacing. */
	public static void body(@Nullable DiscipleEntity body, String line, String fallback) {
		if (body == null || body.isRemoved() || !Voice.free(body)) {
			return;
		}
		String use = VoiceEvents.exists("disciple.m1." + line) ? line : fallback;
		Voice.say(body, "disciple." + VoiceDisciples.voiceOf(body) + "." + use, null);
	}

	/** A full key ({@code elder.banner}, {@code boss.sect_master}) or its fallback, spoken by {@code speaker}. */
	public static String key(String key, String fallback) {
		return VoiceEvents.exists(key) ? key : fallback;
	}

	/** {@code speaker} says {@link #key}. */
	public static void say(@Nullable Entity speaker, String key, String fallback) {
		if (speaker != null) {
			Voice.say(speaker, key(key, fallback), null);
		}
	}

	/** A non-positional elder or boss line to one player ({@link Voice#inner}, queued like the voice of Heaven). */
	public static void inner(@Nullable ServerPlayer player, String key, String fallback) {
		if (player != null) {
			Voice.inner(player, key(key, fallback), (Component) null);
		}
	}

	private SekteSuara() {
	}
}
