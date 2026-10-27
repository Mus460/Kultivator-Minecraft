package dev.kultivator.client.tempat.ui;

import dev.kultivator.client.hall.TreasuryPage;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.Set;

/**
 * The Gulungan registration of the 38 money sheets (DESIGN 43.16.2, M08): page Sekte › Kas &amp; Toko ({@link TreasuryPage})
 * holds the ledger {@code buku_kas} (Hari ini / 7 hari / Sumber and Kafilah), the hall treasury, the Toko PK and module A's
 * Rak Kitab and Makam. The old hub cards of this class are gone (G03): "Buku Kas" and "Lapak &amp; Kafilah" became that page
 * (the stall tab lives in Diri › Harta), "Kota Besar" and "Catatan Perjalanan" became Dunia › Peta › kota and Dunia ›
 * Perjalanan (registered by their page owner).
 */
public final class UiHub {

	static void init() {
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_KAS, Urutan.KAS, TreasuryPage::new);
		TreasuryPage.daftarRumah();
		// the stall tab of the ledger lives in Diri › Harta › usaha (43.12.4, G74); same key as HartaPage's mount
		HubPages.daftarRumah(LembarKunci.BukuKas.LAYAR, Set.of(LembarKunci.BukuKas.TAB_LAPAK), HubNav.DIRI, Urutan.ID_HARTA,
				LembarKunci.BukuKas.TAB_LAPAK, null);
	}

	private UiHub() {
	}
}
