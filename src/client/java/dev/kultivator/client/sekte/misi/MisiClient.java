package dev.kultivator.client.sekte.misi;

import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.sectevent.MissionPage;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.misi.Misi;
import dev.kultivator.sekte.misi.MisiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Module X on the client (DESIGN 36.2.7): the cache of the story tabs ({@code MisiPayloads.Keadaan}), the report screen
 * ({@code MisiPayloads.Laporan} opens {@link MisiLaporanScreen}), the line-up pose of activity {@code ACT_MUSTER} with the
 * travel bundle, the two homecoming scenes (beats 160 and 161) and the hub card "Misi Cerita" (SEKTE section: opens the
 * Misi tablet of the hall on the story tab). Reads only X's own payloads and the shared constants.
 */
public final class MisiClient {

	/** The ask for fresh state is sent at most this often while a story tab is on screen (ms). */
	private static final long REQUEST_EVERY = 4000L;

	private static CompoundTag state = new CompoundTag();
	private static long version;
	private static long lastRequest;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(MisiPayloads.Keadaan.TYPE, (payload, context) -> {
			CompoundTag fresh = payload.state();
			// The periodic refresh carries no preview: keep the last one (it is keyed by offer, stance and team).
			if (!fresh.contains("pratinjau") && state.contains("pratinjau")) {
				fresh.put("pratinjau", state.getCompoundOrEmpty("pratinjau").copy());
			}
			state = fresh;
			version++;
		});
		ClientPlayNetworking.registerGlobalReceiver(MisiPayloads.Laporan.TYPE, (payload, context) -> {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.gui.setScreen(new MisiLaporanScreen(payload.report(), minecraft.gui.screen()));
		});
		SektePoses.register(SekteIds.ACT_MUSTER, MisiPose::apply, List.of(new SektePoses.Prop("sekte_misi_bundle", true, 0.75F, 0.0F)));
		SekteScenes.register(Misi.BEAT_HOME, new SekteScenes.Recipe("kultivator.sekte.misi.adegan.pulang", SekteScenes.Arg.SUBJECT_NAME,
				null, "帰", 0x7FD8C0, false, 60, SekteScenes.Move.PUSH, 4.0F, MusicCue.Cue.M_GENTLE, 8, null, -1, 100));
		SekteScenes.register(Misi.BEAT_LEGEND, new SekteScenes.Recipe("kultivator.sekte.misi.adegan.gemilang", SekteScenes.Arg.SUBJECT_NAME,
				"kultivator.sekte.misi.adegan.gemilang.sub", "凱", 0xFFD76A, true, 80, SekteScenes.Move.LOW_HERO, 5.0F,
				MusicCue.Cue.M_TRIUMPH, 10, SkyDirector.Grade.SK_DAWN, 0xFFE9A8, 160));
		HubCards.register(HubNav.SEKTE, "sekte_misi_cerita", Glyphs.PATH, null, () -> {
			MissionPage.selectStory();
			HubNav.openHall(MissionPage.PAGE);
		});
	}

	/** The last state from the server (never null; empty before the first answer). */
	public static CompoundTag state() {
		return state;
	}

	public static long version() {
		return version;
	}

	/** Sends an action of the story tabs. */
	public static void send(String op, int a, String text) {
		if (ClientPlayNetworking.canSend(MisiPayloads.Aksi.TYPE)) {
			ClientPlayNetworking.send(new MisiPayloads.Aksi(op, a, text == null ? "" : text));
			lastRequest = Util.getMillis();
		}
	}

	/** Asks for fresh state now and then while a story tab is shown ({@code force} asks at once). */
	public static void request(boolean force) {
		long now = Util.getMillis();
		if (force || now - lastRequest >= REQUEST_EVERY) {
			send("buka", 0, "");
		}
	}

	private MisiClient() {
	}
}
