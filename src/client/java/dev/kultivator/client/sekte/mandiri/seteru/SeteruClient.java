package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.sekte.diplomasi.LuarPage;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import dev.kultivator.sekte.mandiri.seteru.SeteruPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Perseteruan antar-sekte on the client (DESIGN 39.F.9, 43.5): the entry of module 39F's client package, called from
 * {@code MandiriClient.init}. Keeps the last {@code mandiri_seteru_layar} sheet for the page Sekte › Luar › Seteru
 * ({@link SeteruScreen}) and for the named disciples shown in Sekte Dunia ({@link #rosterUntuk}); a sheet the server opens
 * opens the Gulungan there (R22: no hub card in section Murid any more). Also registers {@code mandiri_seteru_judul}
 * ({@link KartuJudul}, a HUD element) and {@link SeteruPoses} (clips 250-299, props 130-149, activities 57-60).
 */
public final class SeteruClient {

	private static final long ROSTER_ULANG_MS = 4000L;

	private static boolean initialised;
	private static @Nullable CompoundTag terakhir;
	private static long versi;
	private static String rosterDiminta = "";
	private static long rosterSejak = Long.MIN_VALUE / 2;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(SeteruPayloads.Layar.TYPE, (payload, context) -> lembar(payload));
		ClientPlayNetworking.registerGlobalReceiver(SeteruPayloads.Judul.TYPE, (payload, context) -> KartuJudul.tampil(payload));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("mandiri_seteru_judul"), new KartuJudul());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			terakhir = null;
			versi++;
			rosterDiminta = "";
			KartuJudul.hapus();
		});
		SeteruPoses.daftar();
	}

	private static void lembar(SeteruPayloads.Layar payload) {
		terakhir = payload.sheet();
		versi++;
		if (Minecraft.getInstance().player == null || !payload.buka()) {
			return;
		}
		HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_LUAR).sub(LuarPage.SUB_SETERU));
	}

	/** The last feud sheet (null before the first answer); read-only. */
	public static @Nullable CompoundTag data() {
		return terakhir;
	}

	/** Grows with every sheet that arrives (and on disconnect). */
	public static long versi() {
		return versi;
	}

	/** True when the server runs the feud module. */
	public static boolean tersedia() {
		return ClientPlayNetworking.canSend(SeteruPayloads.Aksi.TYPE);
	}

	/**
	 * The named disciples of sect {@code sekte} (the sheet's roster when it is about that sect), asking for them when not
	 * (at most every 4 s per sect, only while a page draws them: 43.0.12). Empty while unknown or without the module.
	 */
	public static List<CompoundTag> rosterUntuk(String sekte) {
		if (sekte == null || sekte.isEmpty() || !tersedia()) {
			return List.of();
		}
		CompoundTag d = terakhir;
		if (d != null && sekte.equals(d.getStringOr("fokus", ""))) {
			ListTag l = d.getListOrEmpty("roster");
			List<CompoundTag> out = new ArrayList<>(l.size());
			for (int i = 0; i < l.size(); i++) {
				out.add(l.getCompoundOrEmpty(i));
			}
			return out;
		}
		long now = Util.getMillis();
		if (!sekte.equals(rosterDiminta) || now - rosterSejak > ROSTER_ULANG_MS) {
			rosterDiminta = sekte;
			rosterSejak = now;
			minta(SeteruIds.OP_ROSTER, "", 0, sekte);
		}
		return List.of();
	}

	/** True once the sheet's roster is about {@code sekte} (an empty roster then means "no named disciples known"). */
	public static boolean rosterAda(String sekte) {
		CompoundTag d = terakhir;
		return d != null && sekte != null && sekte.equals(d.getStringOr("fokus", ""));
	}

	/** Sends one request; false (and a line above the hotbar) when the server does not run the module. */
	public static boolean minta(int op, String seteru, int aksi, String sekte) {
		if (!ClientPlayNetworking.canSend(SeteruPayloads.Aksi.TYPE)) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null && op == SeteruIds.OP_AKSI) {
				minecraft.player.sendOverlayMessage(Component.translatable(SeteruIds.k("layar.tanpa_server")));
			}
			return false;
		}
		ClientPlayNetworking.send(new SeteruPayloads.Aksi(op, seteru == null ? "" : seteru, aksi, sekte == null ? "" : sekte));
		return true;
	}

	private SeteruClient() {
	}
}
