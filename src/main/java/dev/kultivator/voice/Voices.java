package dev.kultivator.voice;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Module V, the voices of the world: Japanese lines (VOICEVOX, credits in {@code docs/CREDITS.md}) for the voice of
 * Heaven, the heart demon, the ancient beasts, the old hermit, rogue cultivators and every disciple. Called once from
 * {@code Kultivator.onInitialize} (both physical sides): the sound events, the {@link VoicePayload} type and the server
 * listeners. The client half lives in {@code client/voice/}.
 */
public final class Voices {

	private static boolean done;

	public static void init() {
		if (done) {
			return;
		}
		done = true;
		VoiceEvents.register();
		PayloadTypeRegistry.clientboundPlay().register(VoicePayload.TYPE, VoicePayload.CODEC);
		VoiceHooks.init();
	}

	private Voices() {
	}
}
