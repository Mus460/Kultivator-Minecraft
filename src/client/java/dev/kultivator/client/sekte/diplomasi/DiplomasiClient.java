package dev.kultivator.client.sekte.diplomasi;

import dev.kultivator.client.epic.SekteScenes;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.sekte.diplomasi.DiplomasiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Module D on the client (DESIGN 36.2.9, 43.5 Sekte › Luar): keeps the last diplomacy sheet for the pages of Sekte › Luar
 * ({@link LuarPage}: the sect list of Sekte Dunia, Perang, Utusan), opens the Gulungan there when the server opens the sheet
 * (a command, a Ketua Sekte, an envoy: 43.4.4), feeds the world news to the one chronicle of Sekte › Ikhtisar › Catatan
 * (G37), and registers the three scenes of the module's beat range (180 war declared, 181 alliance, 182 victory).
 */
public final class DiplomasiClient {

	public static final int BEAT_PERANG = EpicIds.EXTRA_SEKTE_FIRST + 80;
	public static final int BEAT_SEKUTU = EpicIds.EXTRA_SEKTE_FIRST + 81;
	public static final int BEAT_MENANG = EpicIds.EXTRA_SEKTE_FIRST + 82;
	private static final String KEY = "kultivator.sekte.diplomasi.";
	private static final String JENIS_KABAR = "diplomasi";
	private static final int WARNA_KABAR = 0x8B1A1A;

	private static @Nullable CompoundTag last;
	private static long versi;
	private static long kabarDiminta = Long.MIN_VALUE / 2;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DiplomasiPayloads.Sheet.TYPE, (payload, context) -> onSheet(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			last = null;
			versi++;
		});
		LuarPage.daftar();
		HallClient.sumberCatatan(JENIS_KABAR, Component.translatable(KEY + "layar.kabar"), DiplomasiClient::kabar);
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
		versi++;
		if (Minecraft.getInstance().player == null || !payload.open()) {
			return;
		}
		// an envoy's offer opens Utusan; a command or a sect master opens the sect in Sekte Dunia (43.4.4)
		CompoundTag sheet = payload.sheet();
		String focus = sheet.getStringOr("focus", "");
		Tujuan t = Tujuan.of(HubNav.SEKTE, Urutan.ID_LUAR).sub(sheet.getIntOr("offer", 0) != 0 ? LuarPage.SUB_UTUSAN : LuarPage.SUB_SEKTE_DUNIA);
		if (!focus.isEmpty()) {
			CompoundTag f = new CompoundTag();
			f.putString("sekte", focus);
			t = t.fokus(f);
		}
		HubNav.buka(t);
	}

	/** The last diplomacy sheet (null before the first answer); read-only. */
	public static @Nullable CompoundTag data() {
		return last;
	}

	/** Grows with every sheet that arrives (and on disconnect). */
	public static long versi() {
		return versi;
	}

	/** Asks for a fresh sheet focused on {@code sect} (null = none); nothing when the server does not run the module. */
	public static void minta(@Nullable String sect) {
		if (ClientPlayNetworking.canSend(DiplomasiPayloads.Request.TYPE)) {
			ClientPlayNetworking.send(new DiplomasiPayloads.Request(DiplomasiPayloads.OP_REFRESH, sect == null ? "" : sect, 0, 0));
		}
	}

	/** True when the server runs the diplomacy module. */
	public static boolean tersedia() {
		return ClientPlayNetworking.canSend(DiplomasiPayloads.Request.TYPE);
	}

	/** Sends one request (a line above the hotbar when the server does not know the module). */
	public static boolean request(int op, @Nullable String sect, int a, int b) {
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

	/**
	 * The world news as chronicle entries, oldest first (as the server keeps them). Called while Catatan is drawn; asks for a
	 * fresh sheet at most every 30 s then (43.0.12).
	 */
	private static List<Kronik.Entri> kabar() {
		long now = Util.getMillis();
		if (now - kabarDiminta >= 30000L) {
			kabarDiminta = now;
			minta(null);
		}
		CompoundTag d = last;
		if (d == null) {
			return List.of();
		}
		ListTag news = d.getListOrEmpty("news");
		List<Kronik.Entri> out = new ArrayList<>(news.size());
		for (int i = 0; i < news.size(); i++) {
			CompoundTag line = news.getCompoundOrEmpty(i);
			out.add(new Kronik.Entri(line.read("text", ComponentSerialization.CODEC).orElse(Component.empty()), line.getLongOr("day", 0L), JENIS_KABAR,
					WARNA_KABAR));
		}
		return out;
	}

	private DiplomasiClient() {
	}
}
