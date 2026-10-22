package dev.kultivator.dunia.kota;

import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The spoken lines of module K through the existing {@code voice/**} API (DESIGN 34.8). Phase 34 has its own Japanese
 * lines for the towns ({@code kota.*}); every call names the reused line of earlier phases as its fallback (with the
 * caption it had), so a build whose {@code sounds.json} / {@code VoiceEvents} do not carry the new key yet plays the old
 * line instead of nothing. The {@code Voice} rate limits stay (one line per speaker at a time).
 */
final class KotaVoice {

	static final String AUCTION_OPEN = "kota.auction_open";
	static final String AUCTION_SOLD = "kota.auction_sold";
	static final String TEA_WELCOME = "kota.tea_welcome";
	static final String TEA_NEWS = "kota.tea_news";
	static final String BLACK_WHISPER = "kota.black_whisper";
	static final String GUARD_HALT = "kota.guard_halt";
	static final String WANDERER_DAO = "kota.wanderer_dao";
	static final String RIVAL_CLAIM = "kota.rival_claim";
	static final String IMMORTAL_SCORN = "kota.immortal_scorn";
	static final String LECTURE = "kota.lecture";
	static final String ENVOY_PLEASED = "kota.envoy_pleased";
	static final String PEACH_KEEPER = "kota.peach_keeper";

	/** {@code wanted} when it is a registered voice event, else {@code fallback}. */
	static String pick(String wanted, String fallback) {
		return VoiceEvents.exists(wanted) ? wanted : fallback;
	}

	/**
	 * {@code speaker} says the new line {@code wanted} with its own caption, or, when that key is not registered, the
	 * reused line {@code fallback} with {@code fallbackCaption} (null = the line's own, "" = none).
	 */
	static void say(@Nullable Entity speaker, String wanted, String fallback, @Nullable String fallbackCaption) {
		if (speaker == null) {
			return;
		}
		if (VoiceEvents.exists(wanted)) {
			Voice.say(speaker, wanted, null);
		} else {
			Voice.say(speaker, fallback, fallbackCaption);
		}
	}

	/** {@link #say} for a speaker about to vanish (the sound stays where it stood). */
	static void sayHere(@Nullable Entity speaker, String wanted, String fallback, @Nullable String fallbackCaption) {
		if (speaker == null) {
			return;
		}
		if (VoiceEvents.exists(wanted)) {
			Voice.sayHere(speaker, wanted, null);
		} else {
			Voice.sayHere(speaker, fallback, fallbackCaption);
		}
	}

	private KotaVoice() {
	}
}
