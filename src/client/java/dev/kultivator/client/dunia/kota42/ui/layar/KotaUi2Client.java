package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.client.tempat.ui.tema.LayarPasarGelap;
import dev.kultivator.client.tempat.ui.tema.LayarTaruhan;
import dev.kultivator.dunia.kota42.ui.KotaLembar;

/**
 * Client registration of the 42U-2 screen ids (DESIGN 42.11.9-42.11.14). Jabatan, perkara, kantor_rekrut, pakta, cabang,
 * upacara, rombongan and kavling are pages of their own on the single kit in this package ({@link LayarJabatan},
 * {@link LayarPerkara}, {@link LayarKantor}, {@link LayarPakta}, {@link LayarCabang}, {@link LayarUpacara},
 * {@link LayarKavling}); the court, the recruitment stall, the events board and 41's politics screen no longer carry 42
 * modes. {@code pasar_hantu} and {@code guanpu} keep their themed pages (the black market and the betting table are themed
 * sheets of the Gulungan, {@code LayarPasarGelap} and the dice tab of {@code LayarTaruhan}). Called once from
 * {@code KotaUiClient.init()}.
 */
public final class KotaUi2Client {

	private static boolean siap;

	public static void daftar() {
		if (siap) {
			return;
		}
		siap = true;
		UiTempatClient.daftar(KotaLembar.Jabatan.LAYAR, (npc, lembar) -> new LayarJabatan(npc, lembar));
		UiTempatClient.daftar(KotaLembar.Perkara.LAYAR, (npc, lembar) -> new LayarPerkara(npc, lembar));
		UiTempatClient.daftar(KotaLembar.PasarHantu.LAYAR, (npc, lembar) -> new LayarPasarGelap(KotaLembar.PasarHantu.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Guanpu.LAYAR, (npc, lembar) -> new LayarTaruhan(KotaLembar.Guanpu.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Kantor.LAYAR, (npc, lembar) -> new LayarKantor(npc, lembar));
		UiTempatClient.daftar(KotaLembar.Pakta.LAYAR, (npc, lembar) -> new LayarPakta(npc, lembar));
		UiTempatClient.daftar(KotaLembar.Cabang.LAYAR, (npc, lembar) -> new LayarCabang(npc, lembar));
		UiTempatClient.daftar(KotaLembar.Kavling.LAYAR, LayarKavling::new);
		UiTempatClient.daftar(KotaLembar.Upacara.LAYAR, (npc, lembar) -> new LayarUpacara(KotaLembar.Upacara.LAYAR, npc, lembar));
		UiTempatClient.daftar(KotaLembar.Rombongan.LAYAR, (npc, lembar) -> new LayarUpacara(KotaLembar.Rombongan.LAYAR, npc, lembar));
	}

	private KotaUi2Client() {
	}
}
