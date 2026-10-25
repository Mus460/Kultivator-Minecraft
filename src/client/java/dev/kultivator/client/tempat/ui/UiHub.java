package dev.kultivator.client.tempat.ui;

import dev.kultivator.client.hall.TreasuryPage;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The Gulungan registration of the 38 money sheets (DESIGN 43.16.2, M08): page Sekte › Kas &amp; Toko ({@link TreasuryPage})
 * holds the ledger {@code buku_kas} (Hari ini / 7 hari / Sumber and Kafilah), the hall treasury, the Toko PK and module A's
 * Rak Kitab and Makam. The old hub cards of this class are gone (G03): "Buku Kas" and "Lapak &amp; Kafilah" became that page
 * (the stall tab lives in Diri › Harta), "Kota Besar" and "Catatan Perjalanan" became Dunia › Peta › kota and Dunia ›
 * Perjalanan (registered by their page owner). {@link #mintaTab}/{@link #ambilTab} stay for the sheet base.
 */
public final class UiHub {

	private static final long TAB_TUNGGU_MS = 3000L;
	private static @Nullable String tabLayar;
	private static @Nullable String tabId;
	private static long tabSampai;

	static void init() {
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_KAS, Urutan.KAS, TreasuryPage::new);
		TreasuryPage.daftarRumah();
	}

	/** A caller wants screen {@code layar} to open on tab {@code tab} (used once, within 3 s). */
	public static void mintaTab(String layar, String tab) {
		tabLayar = layar;
		tabId = tab;
		tabSampai = Util.getMillis() + TAB_TUNGGU_MS;
	}

	/** The tab asked for screen {@code layar}, consumed; null when none. */
	public static @Nullable String ambilTab(String layar) {
		if (tabLayar == null || !tabLayar.equals(layar) || Util.getMillis() > tabSampai) {
			return null;
		}
		String t = tabId;
		tabLayar = null;
		tabId = null;
		return t;
	}

	private UiHub() {
	}
}
