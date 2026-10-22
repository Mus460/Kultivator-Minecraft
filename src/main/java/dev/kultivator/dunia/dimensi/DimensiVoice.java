package dev.kultivator.dunia.dimensi;

import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Module D's spoken lines through the existing voice API ({@code voice/Voice}, DESIGN 34.8). The phase's own Japanese
 * lines ({@code voice.bawah.*}, {@code voice.laut.*}, captions {@code kultivator.voice.<key>}) are preferred; when a key
 * is not registered in this build ({@link VoiceEvents#exists}), the reused line of 34.8 plays instead with module D's
 * own caption, so nothing ever goes silent and nothing throws. Every call is rate-limited by the voice API itself.
 */
public final class DimensiVoice {

	// the phase-34 lines of module D (DESIGN 34.8 table, generated into sounds.json by the voice module)
	public static final String MENG_PO_OFFER = "bawah.meng_po_offer";
	public static final String MENG_PO_REFUSE = "bawah.meng_po_refuse";
	public static final String YAMA_JUDGEMENT = "bawah.yama_judgement";
	public static final String YAMA_BOOK = "bawah.yama_book";
	public static final String OX_HEAD = "bawah.ox_head";
	public static final String GHOST_PLEA = "bawah.ghost_plea";
	public static final String DRAGON_KING = "laut.dragon_king";
	public static final String GATE_GUARD = "laut.gate_guard";
	public static final String PENGLAI_TRIAL = "laut.penglai_trial";

	/**
	 * {@code speaker} says {@code key} when that line exists, else {@code fallback} captioned with {@code fallbackCaption}
	 * (a lang key of this module; null: the fallback line's own caption).
	 */
	public static void say(@Nullable Entity speaker, String key, String fallback, @Nullable String fallbackCaption) {
		if (speaker == null) {
			return;
		}
		if (VoiceEvents.exists(key)) {
			Voice.say(speaker, key, null);
		} else {
			Voice.say(speaker, fallback, fallbackCaption);
		}
	}

	/** A line that only exists as a reused one (no phase-34 key): spoken with module D's caption. */
	public static void sayReused(@Nullable Entity speaker, String reused, @Nullable String caption) {
		if (speaker != null) {
			Voice.say(speaker, reused, caption);
		}
	}

	/** The voice of Heaven to one player: {@code key} when it exists (full key, e.g. {@code heaven.secret_realm}), else {@code fallback}. */
	public static void heaven(@Nullable ServerPlayer player, String key, String fallback) {
		if (player == null) {
			return;
		}
		Voice.heaven(player, VoiceEvents.exists(key) ? key : fallback);
	}

	/** True when this build has the phase-34 line {@code key}. */
	public static boolean has(String key) {
		return VoiceEvents.exists(key);
	}

	private DimensiVoice() {
	}
}
