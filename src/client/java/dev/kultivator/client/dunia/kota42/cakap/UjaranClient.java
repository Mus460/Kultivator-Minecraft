package dev.kultivator.client.dunia.kota42.cakap;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.voice.VoiceSettings;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Client init of 42K (DESIGN 42.5A.9, K17), called once from {@code KotaHidupClient.init}: the speech bubbles of NPC-NPC
 * chats ({@link GelembungUjaran}, drawn above the speaker from {@code KotaHidupClient.ujaran(entityId)}; the server only
 * sends lines within {@code kotaBicara.obrolanTampilJarak} blocks and at most {@code obrolanMaksTampil} chats per place),
 * and the sound of a new line: the short interjection ({@code kota42.seru.*}, 42.5A.8 table) when its voice clip exists,
 * else the quiet everyday murmur of the speaker's voice type (the 38.N.4 {@code gumam} clips), so a bubble chat still sounds
 * like people talking. Volume follows the "Obrolan warga" voice setting; a missing clip is silence, the words stay.
 */
public final class UjaranClient {

	/** Bodies whose line is checked each tick (beyond the server's 12 blocks nothing arrives anyway). */
	private static final double JANGKAU = 16.0D;
	private static final float VOLUME_SERU = 0.8F;
	private static final float VOLUME_GUMAM = 0.55F;

	/** speaker entity id -> arrival time of the line that already sounded. */
	private static final Map<Integer, Long> BERBUNYI = new HashMap<>();
	private static final Map<String, Integer> SEBELUM = new HashMap<>();
	private static final RandomSource ACAK = RandomSource.create();
	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(GelembungUjaran::render);
		ClientTickEvents.END_CLIENT_TICK.register(UjaranClient::tick);
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			BERBUNYI.clear();
			GelembungUjaran.bersih();
		}));
	}

	private static void tick(Minecraft minecraft) {
		GelembungUjaran.tick();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.isPaused()) {
			return;
		}
		Map<Integer, Long> kini = new HashMap<>();
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(JANGKAU, 8.0D, JANGKAU),
				KultivatorNpc::isAlive)) {
			KotaHidupClient.UjaranMasuk u = KotaHidupClient.ujaran(npc.getId());
			if (u == null) {
				continue;
			}
			kini.put(npc.getId(), u.ms());
			Long done = BERBUNYI.get(npc.getId());
			if (done == null || done != u.ms()) {
				GelembungUjaran.baru(npc.getId());
				bunyi(minecraft, npc, u.ujaran().seru());
			}
		}
		BERBUNYI.clear();
		BERBUNYI.putAll(kini);
	}

	/** The interjection clip when it exists, else a murmur of the speaker's voice type. */
	private static void bunyi(Minecraft minecraft, KultivatorNpc npc, String seru) {
		float setelan = VoiceSettings.obrolan();
		if (setelan <= 0.0F) {
			return;
		}
		SoundEvent event = seru == null || seru.isEmpty() ? null : VoiceEvents.get(seru);
		float volume = VOLUME_SERU;
		if (event == null) {
			String tipe = tipe(npc.syncedSosok());
			int jumlah = VoiceEvents.GUMAM.getOrDefault(tipe, 0);
			if (jumlah <= 0) {
				return;
			}
			int n = ACAK.nextInt(jumlah);
			if (jumlah > 1 && n == SEBELUM.getOrDefault(tipe, -1)) {
				n = (n + 1 + ACAK.nextInt(jumlah - 1)) % jumlah;
			}
			SEBELUM.put(tipe, n);
			event = VoiceEvents.get("gumam." + tipe + "." + n);
			volume = VOLUME_GUMAM;
		}
		if (event == null) {
			return;
		}
		try {
			minecraft.getSoundManager().play(new EntityBoundSoundInstance(event, SoundSource.VOICE, volume * setelan,
					0.95F + ACAK.nextFloat() * 0.1F, npc, ACAK.nextLong()));
		} catch (RuntimeException ignored) {
			// a missing clip falls back to silence: the bubble still carries the words
		}
	}

	/** The murmur voice of a body: child, old man, old woman, woman or man. */
	static String tipe(Sosok sosok) {
		if (sosok.anak()) {
			return "anak";
		}
		if (sosok.tua()) {
			return sosok.perempuan() ? "nenek" : "kakek";
		}
		return sosok.perempuan() ? "wanita" : "pria";
	}

	private UjaranClient() {
	}
}
