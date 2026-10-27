package dev.kultivator.client.sekte.langit.ui;

import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.client.sekte.wilayah.WilayahPage;
import dev.kultivator.sekte.langit.main.LangitBajak;
import dev.kultivator.sekte.langit.main.LangitPeristiwa;
import dev.kultivator.sekte.langit.main.LangitUiPayloads;
import dev.kultivator.sekte.langit.main.LangitUtusan;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 37G on the client (DESIGN 37.7.9, 43.5): keeps the last sky-sect board ({@code LangitUi}) for the page
 * Sekte › Wilayah › Langit ({@link LangitScreen}) and for any other page that shows a part of it ({@link #data()},
 * {@link #versi()}, {@link #minta}: the sky chronicle for Sekte › Ikhtisar › Catatan, the NPC sky sects for Sekte › Luar),
 * opens the Gulungan on that page when the server asks ({@code /kultivasi langit}), and registers the seven story beats
 * of the sky events (beats 193-199 of the sekte range, through {@code SekteScenes}: every 7b camera guard applies, Kamera
 * sinematik Penuh/Singkat/Mati, Backspace, real danger). Reads only its own payload. One line in {@code LangitClient} (37V).
 */
public final class LangitUiClient {

	/** The filter id and colour of the sky chronicle in Catatan. */
	private static final String JENIS_KRONIK = "langit";
	private static final int WARNA_KRONIK = 0xFF3E6FB0;

	private static boolean initialised;
	private static @Nullable CompoundTag data;
	private static long versi;
	private static long kronikDiminta = Long.MIN_VALUE / 2;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(LangitUiPayloads.Papan.TYPE, (payload, context) -> {
			data = payload.data().copy();
			versi++;
			if (payload.data().getBooleanOr("buka", false) && !LangitScreen.ditampilkan()) {
				HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_WILAYAH).sub(WilayahPage.SUB_LANGIT));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			data = null;
			versi++;
		}));
		// the sky chronicle lives in the one Kronik of Sekte › Ikhtisar › Catatan (43.11 G37), not in a tab of its own
		HallClient.sumberCatatan(JENIS_KRONIK, Component.translatable("kultivator.sekte_langit.layar.kronik"), LangitUiClient::kronik);
		scenes();
	}

	/**
	 * The sky chronicle as chronicle entries, oldest first (the board keeps it newest first). Called while Catatan is drawn;
	 * asks for a fresh board at most every 30 s then (43.0.12: only while shown).
	 */
	private static List<Kronik.Entri> kronik() {
		long now = Util.getMillis();
		if (now - kronikDiminta >= 30000L) {
			kronikDiminta = now;
			minta(LangitUiPayloads.Minta.ASK, "");
		}
		CompoundTag d = data;
		if (d == null) {
			return List.of();
		}
		ListTag list = d.getListOrEmpty("kronik");
		List<Kronik.Entri> out = new ArrayList<>(list.size());
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			out.add(new Kronik.Entri(entry.read("text", ComponentSerialization.CODEC).orElse(Component.empty()), entry.getLongOr("day", 0L), JENIS_KRONIK,
					WARNA_KRONIK));
		}
		return out;
	}

	/** The last sky-sect board (null before the first answer); read-only. */
	public static @Nullable CompoundTag data() {
		return data;
	}

	/** Grows with every board that arrives. */
	public static long versi() {
		return versi;
	}

	/** Asks the server ({@code LangitUiPayloads.Minta.*}: {@code ASK} for a fresh board, or an action with its argument). */
	public static void minta(int action, String arg) {
		if (ClientPlayNetworking.canSend(LangitUiPayloads.Minta.TYPE)) {
			ClientPlayNetworking.send(new LangitUiPayloads.Minta(action, arg == null ? "" : arg));
		}
	}

	private static void scenes() {
		// 193 the cloud pirates break through the cloud sea: a stamp and a short push, the storm music
		SekteScenes.register(LangitBajak.BEAT_BAJAK, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.bajak", SekteScenes.Arg.NUMBER_A,
				null, "賊", 0xD94A3A, false, 50, SekteScenes.Move.PUSH, 3.0F, MusicCue.Cue.M_STORM, 10, SkyDirector.Grade.SK_ASH, -1, 80));
		// 194 the pirates are driven off: a full card, the camera rising over the island, triumph and gold
		SekteScenes.register(LangitBajak.BEAT_BAJAK_MENANG, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.bajak_menang",
				SekteScenes.Arg.NUMBER_A, "kultivator.sekte_langit.adegan.bajak_menang.sub", "勝", 0xF2C14E, true, 80, SekteScenes.Move.CRANE_UP, 5.0F,
				MusicCue.Cue.M_TRIUMPH, 12, SkyDirector.Grade.SK_GOLD, 0xFFE9A0, 130));
		// 195 Raksasa Longbo climbs out of the clouds: a full card from below, dread music, a red sky
		SekteScenes.register(LangitBajak.BEAT_LONGBO, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.longbo", SekteScenes.Arg.SUBJECT_NAME,
				"kultivator.sekte_langit.adegan.longbo.sub", "巨", 0xB3281D, true, 90, SekteScenes.Move.LOW_HERO, 5.0F, MusicCue.Cue.M_DREAD, 14,
				SkyDirector.Grade.SK_ASH_RED, 0xFF5B3A, 150));
		// 196 the giant falls: the turtle is safe
		SekteScenes.register(LangitBajak.BEAT_LONGBO_KALAH, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.longbo_kalah",
				SekteScenes.Arg.NUMBER_A, "kultivator.sekte_langit.adegan.longbo_kalah.sub", "龜", 0x5ED6A0, true, 90, SekteScenes.Move.PULLBACK, 5.0F,
				MusicCue.Cue.M_GLORY, 14, SkyDirector.Grade.SK_JADE, 0xB6E3CB, 150));
		// 197 the Heaven Court's envoy descends on the Gate of the Nine Heavens at dusk
		SekteScenes.register(LangitUtusan.BEAT_UTUSAN, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.utusan", SekteScenes.Arg.SUBJECT_NAME,
				"kultivator.sekte_langit.adegan.utusan.sub", "天", 0xFFE9A8, true, 90, SekteScenes.Move.CRANE_UP, 6.0F, MusicCue.Cue.M_ASCEND, 16,
				SkyDirector.Grade.SK_GOLD, 0xFFF4C0, 160));
		// 198 guests of an NPC sky sect step off their cloud: a quiet stamp
		SekteScenes.register(LangitPeristiwa.BEAT_TAMU, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.tamu", SekteScenes.Arg.SUBJECT_NAME,
				null, "客", 0x5ED6A0, false, 50, SekteScenes.Move.ORBIT, 3.0F, MusicCue.Cue.M_GENTLE, 8, null, -1, 70));
		// 199 the lantern festival night: a stamp and the night sky
		SekteScenes.register(LangitPeristiwa.BEAT_PESTA, new SekteScenes.Recipe("kultivator.sekte_langit.adegan.pesta", SekteScenes.Arg.NONE,
				null, "燈", 0xFFB347, false, 60, SekteScenes.Move.STILL, 4.0F, MusicCue.Cue.M_GENTLE, 10, SkyDirector.Grade.SK_STARS, -1, 90));
	}

	private LangitUiClient() {
	}
}
