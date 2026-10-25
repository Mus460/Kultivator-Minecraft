package dev.kultivator.client.dunia.tempat.tema;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;

import java.util.Set;

/**
 * The home page of the Buku Dagang (DESIGN 38.T.14, 43.5 Dunia › Dagang, 43.12.4; module M15 of 43.16.2): the hub sheet
 * {@link TemaLayar.Dagang#LAYAR} ({@code tema_dagang}) mounted as page {@code dagang} of DUNIA with all its tabs (harga,
 * laba, kontrak; the market tab "pasar" shows only when a theme market NPC or counter opened the book, as a live context
 * on this page). While the page is shown it asks the server for the read-only view (the themed towns the player knows,
 * Infami and Gentar, the last known prices). Replaces the old launcher card "Kota Bertema" (43.11 G03); the sheet is the
 * same one {@code /kultivasi dagang} opens.
 */
public final class TemaHub {

	private static boolean ready;

	/** From {@link TemaClient#init()}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		HubPages.daftarLembar(HubNav.DUNIA, Urutan.ID_DAGANG, Urutan.DAGANG, TemaLayar.Dagang.LAYAR, Set.of());
	}

	private TemaHub() {
	}
}
