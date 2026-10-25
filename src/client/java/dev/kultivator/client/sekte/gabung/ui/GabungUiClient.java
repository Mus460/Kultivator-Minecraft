package dev.kultivator.client.sekte.gabung.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarDirektori;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarGabung2;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarKartu;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarLamaran;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarPangkat;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client entry of module 41U "Layar" (DESIGN 41.10): registers the screens of the 38 UI kit for the lembar keys of
 * {@code sekte.gabung.ui.GabungLembar} ({@code UiTempatClient.daftar}) and the entrance test HUD ({@link HudUjian}, reads
 * {@code GabungClient.ujian()}). Called once from {@code GabungClient.init}.
 * <p>
 * The two hub cards of 41 ({@code GabungHub}) are gone (DESIGN 43.16.2): the Kartu is Sekte › Ikhtisar (kartu, hukum,
 * keluar; {@code hall.IkhtisarPage}), the Direktori Sekte › Luar › Sekte Dunia (M11).
 * <p>
 * Owners: 41A0 created this entry (wave 41-G0); 41U-1 registers Direktori, Lamaran, Kartu, Pangkat and the HUD;
 * 41U-2's screens (Paviliun, Papan, Kediaman, Politik, Shifu, Putusan, Hukum) come from LayarGabung2.daftar().
 */
public final class GabungUiClient {

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		UiTempatClient.daftar(GabungLembar.Direktori.LAYAR, LayarDirektori::new);
		UiTempatClient.daftar(GabungLembar.Lamaran.LAYAR, LayarLamaran::new);
		UiTempatClient.daftar(GabungLembar.Kartu.LAYAR, LayarKartu::new);
		UiTempatClient.daftar(GabungLembar.PohonPangkat.LAYAR, LayarPangkat::new);
		LayarGabung2.daftar(); // 41U-2: Paviliun, Papan, Kediaman, Politik, Shifu, Putusan, Hukum
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("gabung_ujian"), new HudUjian());
	}

	private GabungUiClient() {
	}
}
