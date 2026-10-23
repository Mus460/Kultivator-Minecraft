package dev.kultivator.client.sekte.langit.ui;

import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.sekte.langit.main.LangitBajak;
import dev.kultivator.sekte.langit.main.LangitPeristiwa;
import dev.kultivator.sekte.langit.main.LangitUiPayloads;
import dev.kultivator.sekte.langit.main.LangitUtusan;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Module 37G on the client (DESIGN 37.7.9): the "Sekte Langit" card of the hub's Sekte section (its id starts with
 * {@code sekte_}, so it sits in the Perguruan sub-coin with the hall lock, audit 4.6; no 18th top-level card), the
 * {@link LangitScreen} it opens (also from {@code /kultivasi langit}), and the seven story beats of the sky events (beats
 * 193-199 of the sekte range, through {@code SekteScenes}: every 7b camera guard applies, Kamera sinematik
 * Penuh/Singkat/Mati, Backspace, real danger). Reads only its own payload. One line in {@code LangitClient} (37V).
 */
public final class LangitUiClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(LangitUiPayloads.Papan.TYPE, (payload, context) -> {
			if (payload.data().getBooleanOr("buka", false) && !(Minecraft.getInstance().gui.screen() instanceof LangitScreen)) {
				HubNav.openChild(new LangitScreen(), HubNav.SEKTE);
			}
			LangitScreen.onData(payload.data());
		});
		HubCards.register(HubNav.SEKTE, "sekte_langit", Glyphs.BUILD, null, () -> HubNav.openChild(new LangitScreen(), HubNav.SEKTE));
		scenes();
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
