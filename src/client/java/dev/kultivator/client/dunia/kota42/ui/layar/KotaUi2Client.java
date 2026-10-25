package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.sekte.gabung.ui.layar.LayarPolitik;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.client.tempat.ui.layar.LayarAcara;
import dev.kultivator.client.tempat.ui.layar.LayarLapakRekrut;
import dev.kultivator.client.tempat.ui.layar.LayarYamen;
import dev.kultivator.client.tempat.ui.tema.LayarPasarGelap;
import dev.kultivator.client.tempat.ui.tema.LayarTaruhan;
import dev.kultivator.dunia.kota42.ui.KotaLembar;

/**
 * Client registration of the 42U-2 screen ids (DESIGN 42.11.9-42.11.14). Per jadwal 41/42 0.1 "No GUI twice" each id
 * opens the EXISTING screen of that function in its 42 mode instead of a new screen: {@code jabatan} and {@code perkara}
 * the court ({@code LayarYamen}), {@code pasar_hantu} the black market ({@code LayarPasarGelap}), {@code guanpu} the
 * betting table ({@code LayarTaruhan}, dice tab), {@code kantor_rekrut} the recruitment stall ({@code LayarLapakRekrut}),
 * {@code pakta} and {@code cabang} 41's sect politics ({@code LayarPolitik}, town tabs), {@code upacara} and
 * {@code rombongan} the events board ({@code LayarAcara}); only {@code kavling} is new ({@link LayarKavling}). Called once
 * from {@code KotaUiClient.init()} (42U-1's entry; see {@code docs/progres/m2-42U-2.md}).
 */
public final class KotaUi2Client {

	private static boolean siap;

	public static void daftar() {
		if (siap) {
			return;
		}
		siap = true;
		UiTempatClient.daftar(KotaLembar.Jabatan.LAYAR, (npc, lembar) -> new LayarYamen(KotaLembar.Jabatan.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Perkara.LAYAR, (npc, lembar) -> new LayarYamen(KotaLembar.Perkara.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.PasarHantu.LAYAR, (npc, lembar) -> new LayarPasarGelap(KotaLembar.PasarHantu.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Guanpu.LAYAR, (npc, lembar) -> new LayarTaruhan(KotaLembar.Guanpu.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Kantor.LAYAR, (npc, lembar) -> new LayarLapakRekrut(KotaLembar.Kantor.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Pakta.LAYAR, (npc, lembar) -> new LayarPolitik(KotaLembar.Pakta.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Cabang.LAYAR, (npc, lembar) -> new LayarPolitik(KotaLembar.Cabang.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Kavling.LAYAR, LayarKavling::new);
		UiTempatClient.daftar(KotaLembar.Upacara.LAYAR, (npc, lembar) -> new LayarAcara(KotaLembar.Upacara.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Rombongan.LAYAR, (npc, lembar) -> new LayarAcara(KotaLembar.Rombongan.LAYAR, npc, lembar));
	}

	private KotaUi2Client() {
	}
}
