package dev.kultivator.client.tempat.ui.layar;

import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.ui.LembarKunci;

/**
 * Registers the client screen of every core 38 screen id ({@link LembarKunci#SEMUA_LAYAR}, DESIGN 38.U.3) with
 * {@link UiTempatClient#daftar}. Theme screens ({@code tema_*}) are registered by 38TV-U's {@code TemaUiClient}.
 */
public final class SemuaLayar {

	public static void daftar() {
		UiTempatClient.daftar(LembarKunci.Dialog.LAYAR, LayarDialog::new);
		UiTempatClient.daftar(LembarKunci.TokoSenjata.LAYAR, LayarTokoSenjata::new);
		UiTempatClient.daftar(LembarKunci.Lelang.LAYAR, LayarLelang::new);
		UiTempatClient.daftar(LembarKunci.GildaMisi.LAYAR, LayarGildaMisi::new);
		UiTempatClient.daftar(LembarKunci.Bank.LAYAR, LayarBank::new);
		UiTempatClient.daftar(LembarKunci.BukuKas.LAYAR, LayarBukuKas::new);
		UiTempatClient.daftar(LembarKunci.Tagihan.LAYAR, LayarTagihan::new);
		UiTempatClient.daftar(LembarKunci.PetaKota.LAYAR, LayarPetaKota::new);
		UiTempatClient.daftar(LembarKunci.Teleport.LAYAR, (npc, lembar) -> new LayarTeleport(LembarKunci.Teleport.LAYAR, npc, lembar));
		UiTempatClient.daftar(LembarKunci.Dermaga.LAYAR, (npc, lembar) -> new LayarTeleport(LembarKunci.Dermaga.LAYAR, npc, lembar));
		UiTempatClient.daftar(LembarKunci.PasarBinatang.LAYAR, LayarPasarBinatang::new);
		UiTempatClient.daftar(LembarKunci.JudiBatu.LAYAR, LayarJudiBatu::new);
		for (String layar : new String[] {LembarKunci.Layanan.LAYAR_PENGINAPAN, LembarKunci.Layanan.LAYAR_RUMAH_MAKAN, LembarKunci.Layanan.LAYAR_JASA}) {
			UiTempatClient.daftar(layar, (npc, lembar) -> new LayarLayanan(layar, npc, lembar));
		}
		UiTempatClient.daftar(LembarKunci.Yamen.LAYAR, LayarYamen::new);
		// G64: the Balai Hukum hearing is the court's hearing mode (one court GUI)
		UiTempatClient.daftar(dev.kultivator.sekte.gabung.ui.GabungLembar.Hukum.LAYAR, LayarYamen::hukum);
		UiTempatClient.daftar(LembarKunci.Acara.LAYAR, LayarAcara::new);
		UiTempatClient.daftar(LembarKunci.LapakRekrut.LAYAR, LayarLapakRekrut::new);
		UiTempatClient.daftar(LembarKunci.Jurnal.LAYAR, LayarJurnal::new);
		for (String layar : new String[] {LembarKunci.Gilda.LAYAR_ALKEMIS, LembarKunci.Gilda.LAYAR_TEMPA}) {
			UiTempatClient.daftar(layar, (npc, lembar) -> new LayarGilda(layar, npc, lembar));
		}
		UiTempatClient.daftar(LembarKunci.Kafilah.LAYAR, LayarKafilah::new);
	}

	private SemuaLayar() {
	}
}
