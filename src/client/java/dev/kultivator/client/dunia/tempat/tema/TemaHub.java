package dev.kultivator.client.dunia.tempat.tema;

import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;

/**
 * The hub card "Kota Bertema" of the themed towns (DESIGN 38.T.14 "Hub dan peta", module 38TV-F) in section DUNIA,
 * registered with {@link HubCards#register} (no edit of {@code HubCardsPage}). A click asks the server for the read-only
 * hub sheet {@link TemaLayar.Dagang#LAYAR} ({@code tema_dagang}, npcId −1): the themed towns the player knows with
 * distance and next event, the Infami and Gentar meters, and the Buku Dagang (last known prices per town); the answer
 * opens as a child of DUNIA in the 38TV-U screen ({@code LayarBukuDagang}). The same sheet as {@code /kultivasi dagang}.
 * Card texts {@code kultivator.hub.card.kota_tema} and {@code .tip} live in {@code lang-src/tempat_tema_fx.*}.
 */
public final class TemaHub {

	/** The card id (texts {@code kultivator.hub.card.kota_tema[.tip]}). */
	public static final String KARTU = "kota_tema";
	private static boolean ready;

	/** From {@link TemaClient#init()}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		HubCards.register(HubNav.DUNIA, KARTU, Glyphs.JALAN, null, () -> UiTempatClient.lihatDariHub(TemaLayar.Dagang.LAYAR, HubNav.DUNIA));
	}

	private TemaHub() {
	}
}
