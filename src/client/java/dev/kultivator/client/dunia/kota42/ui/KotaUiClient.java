package dev.kultivator.client.dunia.kota42.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.kota42.ui.layar.KotaHud;
import dev.kultivator.client.dunia.kota42.ui.layar.KotaUi2Client;
import dev.kultivator.client.dunia.kota42.ui.layar.LayarBukuKota;
import dev.kultivator.client.dunia.kota42.ui.layar.LayarGudang;
import dev.kultivator.client.dunia.kota42.ui.layar.LayarInvestasi;
import dev.kultivator.client.dunia.kota42.ui.layar.LayarRumah;
import dev.kultivator.client.dunia.kota42.ui.layar.LayarToko;
import dev.kultivator.client.dunia.kota42.ui.layar.LayarWarga;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Buku;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Gudang;
import dev.kultivator.dunia.kota42.ui.KotaLembar.HudIrama;
import dev.kultivator.dunia.kota42.ui.KotaLembar.HudKejar;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Investasi;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Kartu;
import dev.kultivator.dunia.kota42.ui.KotaLembar.LapakSewa;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Milikku;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Pohon;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Rumah;
import dev.kultivator.dunia.kota42.ui.KotaLembar.Toko;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client entry of module 42U "Layar" (DESIGN 42.11, one main GUI of design 43): registers the page factory of every 42 sheet
 * id of {@link KotaLembar} with {@code UiTempatClient.daftar}, the page "Kota" of Dunia, 42U-2's ten ids
 * ({@link KotaUi2Client#daftar()}) and the two HUDs ({@link KotaHud}). Called once from {@code KotaHidupClient.init}.
 * <p>
 * Where each sheet lives in the Gulungan (43.12.4): {@code kota_buku} is the page Dunia › Kota (its tabs are the page's subs;
 * Acara, Harga and Milikku are server jumps to the calendar, the trade ledger and "Rumah &amp; Usahaku"); a sheet that comes from a
 * counter, a person or a sign (warga_kartu, warga_pohon, rumah, toko_pemain, lapak_sewa, investasi, gudang, the dialog's extra
 * tablets) opens under the Di Sini tablet with the speaker's context, and the same sheet asked for from a hub view is read
 * only there ("datangi tempatnya"). No hub card exists for any of them.
 */
public final class KotaUiClient {

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		UiTempatClient.daftar(Kartu.LAYAR, (npc, lembar) -> new LayarWarga(Kartu.LAYAR, npc, lembar));
		UiTempatClient.daftar(Pohon.LAYAR, (npc, lembar) -> new LayarWarga(Pohon.LAYAR, npc, lembar));
		UiTempatClient.daftar(Buku.LAYAR, (npc, lembar) -> new LayarBukuKota(Buku.LAYAR, npc, lembar));
		UiTempatClient.daftar(Milikku.LAYAR, (npc, lembar) -> new LayarBukuKota(Milikku.LAYAR, npc, lembar));
		UiTempatClient.daftar(Rumah.LAYAR, LayarRumah::new);
		UiTempatClient.daftar(Toko.LAYAR, (npc, lembar) -> new LayarToko(Toko.LAYAR, npc, lembar));
		UiTempatClient.daftar(LapakSewa.LAYAR, (npc, lembar) -> new LayarToko(LapakSewa.LAYAR, npc, lembar));
		UiTempatClient.daftar(Investasi.LAYAR, LayarInvestasi::new);
		UiTempatClient.daftar(Gudang.LAYAR, LayarGudang::new);
		KotaUi2Client.daftar();
		// Dunia › Kota: the town book as a page (its tabs are the subs)
		HubPages.daftarLembar(HubNav.DUNIA, Urutan.ID_KOTA, Urutan.KOTA, Buku.LAYAR, Set.of());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id(HudKejar.ID), new KotaHud.Kejar());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id(HudIrama.ID), new KotaHud.Irama());
	}

	private KotaUiClient() {
	}
}
