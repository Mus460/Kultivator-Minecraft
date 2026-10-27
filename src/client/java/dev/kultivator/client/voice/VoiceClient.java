package dev.kultivator.client.voice;

import dev.kultivator.Kultivator;
import dev.kultivator.voice.VoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * The client half of the voices (module V): plays each {@link VoicePayload} on {@link SoundSource#VOICE} (the vanilla
 * "Voice/Speech" slider) unless "Suara bicara" mutes it, and hands the caption to {@link VoiceCaptions}. Positional lines
 * follow their speaker; the voice of Heaven, the heart demon and the ancient beasts play non-positional, with the echo
 * already in the clip.
 */
public final class VoiceClient {

	private static final String[] KIND_IDS = {"heaven", "demon", "boss", "elder", "rogue", "disciple", "grunt", "village"};
	private static final int[] KIND_COLORS = {0xFFE9A8, 0xE0485A, 0xFF9A3C, 0x9FE0B0, 0xD09A70, 0x9AD8FF, 0xC0C0C0, 0xB8E0A0};
	private static int previewIndex;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VoicePayload.TYPE, (payload, context) -> onPayload(payload));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("voice_captions"), new VoiceCaptions());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(VoiceCaptions::clear));
	}

	static void onPayload(VoicePayload p) {
		try {
			play(p);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Kultivator voice: could not play {}", p.event(), e);
		}
	}

	private static void play(VoicePayload p) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || !p.event().startsWith("voice.")) {
			return;
		}
		VoiceSettings.Mode mode = VoiceSettings.mode();
		boolean heaven = p.kind() == VoicePayload.KIND_HEAVEN;
		if (mode == VoiceSettings.Mode.MATI || (mode == VoiceSettings.Mode.LANGIT && !heaven)) {
			return;
		}
		Identifier id = Kultivator.id(p.event());
		SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
		if (event == null) {
			event = SoundEvent.createVariableRangeEvent(id);
		}
		float volume = clamp(p.volume(), 0.05F, 4.0F);
		float pitch = clamp(p.pitch(), 0.5F, 2.0F);
		Entity speaker = p.entityId() >= 0 ? minecraft.level.getEntity(p.entityId()) : null;
		SoundInstance sound;
		if (p.positional()) {
			sound = speaker != null
					? new EntityBoundSoundInstance(event, SoundSource.VOICE, volume, pitch, speaker, minecraft.level.getRandom().nextLong())
					: new SimpleSoundInstance(event, SoundSource.VOICE, volume, pitch, SoundInstance.createUnseededRandom(), p.x(), p.y(), p.z());
		} else {
			sound = new SimpleSoundInstance(id, SoundSource.VOICE, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
					SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
		}
		minecraft.getSoundManager().play(sound);
		if (!p.caption().isEmpty() && VoiceSettings.text() && Language.getInstance().has(p.caption())) {
			int kind = Math.max(0, Math.min(KIND_IDS.length - 1, p.kind()));
			Component name = heaven || p.speaker().isEmpty() ? Component.translatable("kultivator.voice.speaker." + KIND_IDS[kind]) : p.speaker().get();
			String prefix = "kultivator.voice.";
			String jaKey = p.caption().startsWith(prefix) ? "kultivator.voice.ja." + p.caption().substring(prefix.length()) : "";
			Component ja = !jaKey.isEmpty() && Language.getInstance().has(jaKey) ? Component.translatable(jaKey) : null;
			VoiceCaptions.push(p.entityId(), name, KIND_COLORS[kind], Component.translatable(p.caption()), ja, kind);
		}
	}

	/** The credit row of the Settings page: plays one sample line of each kind in turn, locally. */
	static void preview() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		String[][] samples = {{"voice.heaven.witness", "kultivator.voice.heaven.witness"}, {"voice.disciple.f1.greet", "kultivator.voice.disciple.greet"},
				{"voice.elder.advice", "kultivator.voice.elder.advice"}, {"voice.disciple.m2.cheer", "kultivator.voice.disciple.cheer"},
				{"voice.demon.whisper", "kultivator.voice.demon.whisper"}};
		String[] sample = samples[Math.floorMod(previewIndex++, samples.length)];
		int kind = sample[0].startsWith("voice.heaven") ? VoicePayload.KIND_HEAVEN : sample[0].startsWith("voice.demon") ? VoicePayload.KIND_DEMON
				: sample[0].startsWith("voice.elder") ? VoicePayload.KIND_ELDER : VoicePayload.KIND_DISCIPLE;
		onPayload(new VoicePayload(sample[0], sample[1], java.util.Optional.empty(), kind, false, -1, 0.0D, 0.0D, 0.0D, 1.0F, 1.0F));
	}

	private static float clamp(float v, float lo, float hi) {
		return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 1.0F;
	}

	private VoiceClient() {
	}
}
