package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.client.tempat.ui.layar.LayarKafilah;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Screen part of the themed towns (DESIGN 38.T.13.3, module 38TV-U), called by {@code TemaClient.init()}: registers one
 * page factory per id of {@link TemaLayar#SEMUA} with {@link UiTempatClient#daftar}. Every page draws the sheet keys
 * {@link TemaLayar} documents (C38.T3) with the single kit {@code screen/ui} through {@link TemaHalaman} (DESIGN 43.7) and
 * opens under the Di Sini tablet of the Gulungan (43.8.3; {@code tema_dagang} has its home in Dunia › Dagang). All twenty
 * factories are always registered; whether a sheet opens is the server's decision (builders in
 * {@code dunia/tempat/tema/<tema>/}).
 */
public final class TemaUiClient {

	private static boolean ready;

	/** From {@code TemaClient.init()}. Safe to call twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		Map<String, UiTempatClient.Pembuat> semua = new LinkedHashMap<>();
		semua.put(TemaLayar.Tawar.LAYAR, LayarTawar::new);
		semua.put(TemaLayar.Dagang.LAYAR, LayarBukuDagang::new);
		semua.put(TemaLayar.Kapal.LAYAR, LayarKapal::new);
		semua.put(TemaLayar.Galangan.LAYAR, LayarGalangan::new);
		semua.put(TemaLayar.Kafilah.LAYAR, LayarKafilah::tema);
		semua.put(TemaLayar.Mural.LAYAR, LayarMural::new);
		semua.put(TemaLayar.Sutra.LAYAR, LayarSutra::new);
		semua.put(TemaLayar.Teh.LAYAR, LayarTeh::new);
		semua.put(TemaLayar.Bait.LAYAR, LayarBait::new);
		semua.put(TemaLayar.Herba.LAYAR, LayarHerba::new);
		semua.put(TemaLayar.Ziarah.LAYAR, LayarZiarah::new);
		semua.put(TemaLayar.Lift.LAYAR, LayarLift::new);
		semua.put(TemaLayar.PasarGelap.LAYAR, LayarPasarGelap::new);
		semua.put(TemaLayar.KitabTerlarang.LAYAR, LayarKitabTerlarang::new);
		semua.put(TemaLayar.Kontrak.LAYAR, LayarKontrak::new);
		semua.put(TemaLayar.Taruhan.LAYAR, LayarTaruhan::new);
		semua.put(TemaLayar.Tawanan.LAYAR, LayarTawanan::new);
		semua.put(TemaLayar.Dokumen.LAYAR, LayarDokumen::new);
		semua.put(TemaLayar.Racik.LAYAR, LayarRacik::new);
		semua.put(TemaLayar.Irama.LAYAR, LayarIrama::new);
		for (Map.Entry<String, UiTempatClient.Pembuat> e : semua.entrySet()) {
			UiTempatClient.daftar(e.getKey(), e.getValue());
		}
		for (String layar : TemaLayar.SEMUA) {
			if (!semua.containsKey(layar)) {
				Kultivator.LOGGER.warn("Themed screen {} has no client screen; its sheets will be ignored", layar);
			}
		}
	}

	private TemaUiClient() {
	}
}
