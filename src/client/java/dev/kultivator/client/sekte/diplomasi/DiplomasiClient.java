package dev.kultivator.client.sekte.diplomasi;

import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.sekte.diplomasi.DiplomasiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Module D on the client (DESIGN 36.2.9): the sheet receiver (opens or refreshes {@link DiplomasiScreen}), the hub card
 * "Diplomasi Sekte" in Gulungan › Sekte, and the three scenes of the module's beat range (180 war declared, 181 alliance,
 * 182 victory), declarative recipes for {@code SekteScenes}: short, skippable and under every 7b guard.
 */
public final class DiplomasiClient {

	public static final int BEAT_PERANG = EpicIds.EXTRA_SEKTE_FIRST + 80;
	public static final int BEAT_SEKUTU = EpicIds.EXTRA_SEKTE_FIRST + 81;
	public static final int BEAT_MENANG = EpicIds.EXTRA_SEKTE_FIRST + 82;
	private static final String KEY = "kultivator.sekte.diplomasi.";

	private static @Nullable CompoundTag last;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DiplomasiPayloads.Sheet.TYPE, (payload, context) -> onSheet(payload));
		HubCards.register(HubNav.SEKTE, "sekte_diplomasi_rival", Glyphs.SEAL, null,
				() -> HubNav.openChild(new DiplomasiScreen(last, null), HubNav.SEKTE));
		scenes();
	}

	private static void scenes() {
		SekteScenes.register(BEAT_PERANG, new SekteScenes.Recipe(KEY + "adegan.perang", SekteScenes.Arg.NUMBER_A, KEY + "adegan.perang.sub",
				"戰", 0xD8322A, true, 70, SekteScenes.Move.LOW_HERO, 4.0F, MusicCue.Cue.M_DREAD, 10, SkyDirector.Grade.SK_ASH_RED, 0xFF3A20, 140));
		SekteScenes.register(BEAT_SEKUTU, new SekteScenes.Recipe(KEY + "adegan.sekutu", SekteScenes.Arg.NUMBER_A, KEY + "adegan.sekutu.sub",
				"盟", 0xE2B33C, true, 70, SekteScenes.Move.CRANE_UP, 5.0F, MusicCue.Cue.M_GLORY, 12, SkyDirector.Grade.SK_GOLD, -1, 150));
		SekteScenes.register(BEAT_MENANG, new SekteScenes.Recipe(KEY + "adegan.menang", SekteScenes.Arg.NUMBER_A, KEY + "adegan.menang.sub",
				"勝", 0xF2D07A, true, 80, SekteScenes.Move.ORBIT, 5.0F, MusicCue.Cue.M_TRIUMPH, 12, SkyDirector.Grade.SK_DAWN, 0xFFE9A8, 160));
	}

	private static void onSheet(DiplomasiPayloads.Sheet payload) {
		last = payload.sheet();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (minecraft.gui.screen() instanceof DiplomasiScreen screen) {
			screen.update(payload.sheet());
		} else if (payload.open()) {
			minecraft.gui.setScreen(new DiplomasiScreen(payload.sheet(), null));
		}
	}

	/** Sends one request (nothing when the server does not know the module). */
	static boolean request(int op, @Nullable String sect, int a, int b) {
		if (!ClientPlayNetworking.canSend(DiplomasiPayloads.Request.TYPE)) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) {
				minecraft.player.sendOverlayMessage(Component.translatable(KEY + "layar.tanpa_server"));
			}
			return false;
		}
		ClientPlayNetworking.send(new DiplomasiPayloads.Request(op, sect == null ? "" : sect, a, b));
		return true;
	}

	private DiplomasiClient() {
	}
}
