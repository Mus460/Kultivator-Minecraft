package dev.kultivator.client.dunia.kota42.ui;

/**
 * Client entry of module 42U "Layar" (DESIGN 42.11): registers the screens of the 38 UI kit for the sheet keys of
 * {@code dunia.kota42.ui.KotaLembar} ({@code UiTempatClient.daftar(layar, Pembuat)}, classes in {@code ui/layar/**}), the
 * eight tablets of the extended {@code dialog} screen, the hub cards ({@code KotaHub}: {@code KotaLembar.HUB_BUKU} in
 * DUNIA, {@code HUB_MILIKKU} in DIRI, {@code HUB_SEKTE} in SEKTE) and the HUDs {@code kejar} and {@code irama}. Called once
 * from {@code KotaHidupClient.init}.
 * <p>
 * Owners: 42U0 created this entry (wave 42-G0, empty on purpose: no screen exists yet, so a sheet of a 42 screen is
 * logged once by {@code UiTempatClient} and not opened); 42U-1 (wave 42-G2) registers warga_kartu, warga_pohon, kota_buku,
 * kota_milikku, rumah, toko_pemain, investasi, gudang, lapak_sewa, the dialog tablets, the hub and both HUDs; 42U-2's
 * screens (jabatan, perkara, pasar_hantu, guanpu, kantor_rekrut, pakta, cabang, kavling, upacara, rombongan) come from
 * one {@code daftar()} call of theirs added here.
 */
public final class KotaUiClient {

	private static boolean initialised;

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
	}

	private KotaUiClient() {
	}
}
