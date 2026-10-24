package dev.kultivator.client.dunia.tempat.hidup;

import dev.kultivator.client.voice.VoiceSettings;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The voices under the residents' gestured talks ("Kota yang Hidup", docs/progres/suara-kota.md): whenever a body near
 * the player starts a new line of a 38.N.4 chat (its synced speech line turns to a {@code kultivator.hidup.obrol.*} or
 * {@code kultivator.kota.obrol.*} key, set by {@code TubuhPenduduk.obrol}), a short quiet Japanese murmur plays from it:
 * {@code voice.gumam.<tipe>.<n>}, the type from its Sosok (man, woman, old man, old woman, child). The clips are an
 * everyday line each, made dull and quiet so they read as people talking nearby, not to the player; the speech bubble
 * keeps the words. Client only, no payload; the scan runs every 5 ticks over bodies within 18 blocks. Volume: "Obrolan
 * warga" ({@link VoiceSettings#obrolan()}) times the vanilla "Voice/Speech" slider, silent with "Suara bicara: Mati".
 */
public final class ObrolanSuara {

	private static final double JANGKAU = 18.0D;
	private static final float VOLUME = 0.6F;
	/** The last chat line key each near body showed (absent or "" = none), so one murmur plays per new line. */
	private static Map<Integer, String> terakhir = new HashMap<>();
	private static final Map<String, Integer> SEBELUM = new HashMap<>();
	private static final RandomSource ACAK = RandomSource.create();
	private static int detak;

	private ObrolanSuara() {
	}

	/** End of every client tick. */
	static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			terakhir.clear();
			return;
		}
		if (minecraft.isPaused() || ++detak < 5) {
			return;
		}
		detak = 0;
		float setelan = VoiceSettings.obrolan();
		Map<Integer, String> kini = new HashMap<>();
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(JANGKAU, 8.0D, JANGKAU),
				KultivatorNpc::isAlive)) {
			String key = npc.line().getContents() instanceof TranslatableContents t ? t.getKey() : "";
			boolean obrol = key.startsWith("kultivator.hidup.obrol.") || key.startsWith("kultivator.kota.obrol.");
			if (!obrol) {
				continue;
			}
			kini.put(npc.getId(), key);
			if (!key.equals(terakhir.get(npc.getId())) && setelan > 0.0F) {
				gumam(minecraft, npc, setelan);
			}
		}
		terakhir = kini;
	}

	static void bersihkan() {
		terakhir.clear();
	}

	private static void gumam(Minecraft minecraft, KultivatorNpc npc, float setelan) {
		String tipe = tipe(npc.syncedSosok());
		int jumlah = VoiceEvents.GUMAM.getOrDefault(tipe, 0);
		if (jumlah <= 0) {
			return;
		}
		// never the same line twice in a row for one voice type
		int n = ACAK.nextInt(jumlah);
		if (jumlah > 1 && n == SEBELUM.getOrDefault(tipe, -1)) {
			n = (n + 1 + ACAK.nextInt(jumlah - 1)) % jumlah;
		}
		SEBELUM.put(tipe, n);
		SoundEvent event = VoiceEvents.get("gumam." + tipe + "." + n);
		if (event == null) {
			return;
		}
		float pitch = 0.94F + ACAK.nextFloat() * 0.12F;
		try {
			minecraft.getSoundManager().play(new EntityBoundSoundInstance(event, SoundSource.VOICE, VOLUME * setelan, pitch, npc, ACAK.nextLong()));
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
}
