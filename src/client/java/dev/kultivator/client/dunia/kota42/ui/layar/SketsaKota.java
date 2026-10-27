package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The texts of the 42U-2 pages ({@code lang-src/kota_hidup_ui2}, keys {@code kultivator.kota42.ui2.*}) and two small shared
 * pieces: the Ghost Market's green and the "age · job" line of a townsperson row. The 42U-2 pages (jabatan, perkara, kantor,
 * pakta, cabang, upacara, rombongan, kavling) and the themed black market / betting table draw with the single kit and
 * {@code KotaGambar}; nothing here draws any more.
 */
public final class SketsaKota {

	/** Lang prefix of every 42U-2 text ({@code lang-src/kota_hidup_ui2}). */
	public static final String K = "kultivator.kota42.ui2.";
	/** The Ghost Market's green accent ({@code KotaLembar.PasarHantu.WARNA_HIJAU}). */
	public static final int HIJAU = KotaLembar.PasarHantu.WARNA_HIJAU;

	public static MutableComponent t(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	/** "34 · Koki" (age and job of a warga row). */
	public static Component umurKerja(Lembar w) {
		Component kerja = w.teksAtau(KotaLembar.KERJA);
		MutableComponent out = t("umur", w.i(KotaLembar.UMUR, 0));
		if (kerja != null) {
			out.append(" · ").append(kerja);
		}
		return out;
	}

	private SketsaKota() {
	}
}
