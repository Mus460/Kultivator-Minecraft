package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.Kultivator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The icon atlas of the 42 screens ({@code textures/gui/kota42/ikon.png}, 256 x 112, 16 px slots, drawn by
 * {@code tools/gen_kota_hidup_ui.py}; the slot order is the order of that generator's {@code ICONS} list). One atlas for
 * 42U-1 and 42U-2: faces, hearts, the eight zhuazhou objects, memories, family and bonds, the 14 event kinds, houses and
 * trade, the 12 price categories and the Ghost Market, court and plot marks. A text id that has no slot draws nothing
 * (the caller always also writes the name).
 */
public final class IkonKota {

	public static final Identifier ATLAS = Kultivator.id("textures/gui/kota42/ikon.png");

	// row 0
	public static final int SENANG = 0;
	public static final int NETRAL = 1;
	public static final int SEDIH = 2;
	public static final int TAKUT = 3;
	public static final int MARAH = 4;
	public static final int MABUK = 5;
	public static final int SAKIT = 6;
	public static final int HATI = 7;
	public static final int HATI_PATAH = 8;
	public static final int HATI_KOSONG = 9;
	public static final int AKAR = 10;
	public static final int AKAR_TANYA = 11;
	public static final int SEGEL_JABATAN = 12;
	public static final int NAMA = 13;
	public static final int UTANG = 14;
	public static final int HADIAH = 15;
	// row 1: zhuazhou 16-23, memories 24-31
	public static final int KUAS = 16;
	public static final int KENANGAN_DISELAMATKAN = 24;
	// row 2
	public static final int KENANGAN_UTANG_LUNAS = 32;
	public static final int PASANGAN = 37;
	public static final int MAKAM = 46;
	public static final int PINDAH = 47;
	// row 3
	public static final int ACARA_NIKAH = 48;
	public static final int KALENDER = 62;
	public static final int JAM = 63;
	// row 4
	public static final int RUMAH_KECIL = 64;
	public static final int GUDANG = 69;
	public static final int KONTRAK = 70;
	public static final int KAFILAH = 71;
	public static final int TOKO = 72;
	public static final int LACI = 73;
	public static final int INVESTASI = 74;
	public static final int TARIK = 75;
	public static final int PELUANG = 76;
	public static final int PAJAK = 77;
	public static final int KAS = 78;
	public static final int USAHA = 79;
	// row 5
	public static final int KATEGORI_PANGAN = 80;
	public static final int NAIK = 92;
	public static final int TURUN = 93;
	public static final int DATAR = 94;
	public static final int WASPADA = 95;
	// row 6
	public static final int TOPENG = 96;
	public static final int BULAN_HANTU = 97;
	public static final int DADU = 98;
	public static final int CAWAN = 99;
	public static final int PALU_HAKIM = 100;
	public static final int TIMBANGAN = 101;
	public static final int PERISAI_PAKTA = 102;
	public static final int PANJI = 103;
	public static final int KAVLING = 104;
	public static final int TAHAP = 105;
	public static final int PENJAGA = 106;
	public static final int KANTOR = 107;
	public static final int UJI_AKAR = 108;
	public static final int RAZIA = 109;
	public static final int CURIAN = 110;
	public static final int PENADAH = 111;

	private static final String[] WAJAH = {"senang", "netral", "sedih", "takut", "marah", "mabuk", "sakit"};
	private static final String[] ZHUAZHOU = {"kuas", "sempoa", "pedang_kayu", "cap", "kitab", "palu", "tasbih", "koin"};
	private static final String[] KENANGAN = {"diselamatkan", "dibantu", "hadir_nikah", "hadir_makam", "anak_direkrut", "dipukul", "dicuri",
			"rumah_rusak", "utang_lunas", "utang_mangkir", "cerita_orang_tua"};
	private static final String[] KELUARGA = {"ayah", "ibu", "pasangan", "anak", "saudara", "kakek", "nenek", "cucu", "mertua", "menantu"};
	private static final String[] HUBUNGAN = {"sahabat", "kekasih", "pasangan", "musuh", "saingan", "guru_murid"};
	private static final String[] ACARA = {"nikah", "makam", "lahir", "sebulan", "zhuazhou", "rombongan", "hari_raya", "ronda", "ujian_terbuka",
			"pasar_pagi", "wayang", "serbuan", "peresmian", "sidang"};
	private static final String[] KATEGORI = {"pangan", "obat", "herba", "senjata", "zirah", "jimat", "bahan", "kain", "mewah", "dagang", "jasa",
			"bangunan"};
	private static final String[] RUMAH = {"kecil", "sedang", "besar", "rumah_toko", "paviliun"};

	public static void gambar(GuiGraphicsExtractor g, int slot, int x, int y, int size, int warna) {
		if (slot < 0 || slot > 111) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, (slot % 16) * 16.0F, (slot / 16) * 16.0F, size, size, 16, 16, 256, 112, warna);
	}

	public static void gambar(GuiGraphicsExtractor g, int slot, int x, int y, int size) {
		gambar(g, slot, x, y, size, 0xFFFFFFFF);
	}

	private static int cari(String[] daftar, String id, int dasar, int cadangan) {
		for (int i = 0; i < daftar.length; i++) {
			if (daftar[i].equals(id)) {
				return dasar + i;
			}
		}
		return cadangan;
	}

	public static int wajah(String id) {
		return cari(WAJAH, id, SENANG, NETRAL);
	}

	public static int zhuazhou(String id) {
		return cari(ZHUAZHOU, id, KUAS, -1);
	}

	/** Memory ids of {@code api/Kenangan}: 24-34 (diselamatkan .. cerita_orang_tua). */
	public static int kenangan(String id) {
		return cari(KENANGAN, id, KENANGAN_DISELAMATKAN, HADIAH);
	}

	/** Family bonds ayah..menantu: ayah 35, ibu 36, pasangan 37, anak 38, saudara 39, kakek 40; the rest reuse them. */
	public static int keluarga(String ikatan) {
		return switch (ikatan) {
			case "ayah", "mertua" -> 35;
			case "ibu" -> 36;
			case "pasangan" -> PASANGAN;
			case "anak", "cucu", "menantu" -> 38;
			case "saudara" -> 39;
			case "kakek", "nenek" -> 40;
			default -> NAMA;
		};
	}

	/** Bond kinds sahabat 41, kekasih 42, musuh 43, saingan 44, guru_murid 45 (pasangan = rings). */
	public static int hubungan(String jenis) {
		return switch (jenis) {
			case "sahabat" -> 41;
			case "kekasih" -> 42;
			case "pasangan" -> PASANGAN;
			case "musuh" -> 43;
			case "saingan" -> 44;
			case "guru_murid" -> 45;
			default -> HATI;
		};
	}

	public static int acara(String jenis) {
		return cari(ACARA, jenis, ACARA_NIKAH, KALENDER);
	}

	public static int kategori(String id) {
		return cari(KATEGORI, id, KATEGORI_PANGAN, DAGANG_CADANGAN);
	}

	private static final int DAGANG_CADANGAN = 89;

	public static int rumah(String jenis) {
		return cari(RUMAH, jenis, RUMAH_KECIL, RUMAH_KECIL + 1);
	}

	public static int tren(int t) {
		return t > 0 ? NAIK : t < 0 ? TURUN : DATAR;
	}

	private IkonKota() {
	}
}
