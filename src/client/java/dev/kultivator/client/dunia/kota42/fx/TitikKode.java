package dev.kultivator.client.dunia.kota42.fx;

import java.util.List;

/**
 * The 16 bits of {@code KotaPayloads.EntriTitik.data} for the visual titik of Kota yang Hidup (DESIGN 42.13.2, contract between
 * the server sender of {@code kota_titik} and the fx of 42V-F; the titik NBT of {@code tools/kota_hidup_kit.py} carries the
 * same facts as strings, the sender packs them):
 * <pre>
 *  bits  0-1  hadap      0 north, 1 east, 2 south, 3 west (the titik's facing: where the visual looks / faces out)
 *  bits  2-4  sub        lentera: warna 0 merah, 1 putih, 2 hijau, 3 biru, 4 sekte (WARNA_LENTERA of the kit)
 *                        panji / spanduk: kain 0 panji, 1 papan, 2 bendera_arak, 3 spanduk, 4 kertas_pintu (KAIN of the kit)
 *                        cerobong: 0 rumah, 1 usaha makanan (baker, tea house, inn), 2 pandai besi, 3 rumah duka
 *  bits  5-7  ekstra     lentera: jenis 0 bulat, 1 panjang, 2 labu, 3 kelinci, 4 heksa; spanduk: 1 = the 'b' pole, 0 = 'a'
 *  bits  8-15 ref        255 = none.  lentera / cerobong / kertas_pintu: the house slot (the id of
 *                        {@code KotaPayloads.EntriRumah.rumah}, 0-254);  panji / papan / bendera_arak / spanduk: index into
 *                        {@code TitikVisual.teks} of the comma list of glyph ids ({@code fu,chun}; ids of the kit's GLIF)
 * pajangan: bits 2-7 = index into the kit's PAJANGAN list (mangkuk, mi, teko, ... see {@link #PAJANGAN}), ref unused.
 * kucing_tidur, nisan, nama_jalan: hadap only; nisan / nama_jalan: ref = index into {@code teks} (the engraved / street name).
 * </pre>
 * Client only reads; {@link #pak} is for the sender and for tests.
 */
public final class TitikKode {

	public static final int TIDAK_ADA = 255;

	public static final int WARNA_MERAH = 0;
	public static final int WARNA_PUTIH = 1;
	public static final int WARNA_HIJAU = 2;
	public static final int WARNA_BIRU = 3;
	public static final int WARNA_SEKTE = 4;

	public static final int KAIN_PANJI = 0;
	public static final int KAIN_PAPAN = 1;
	public static final int KAIN_ARAK = 2;
	public static final int KAIN_SPANDUK = 3;
	public static final int KAIN_PINTU = 4;

	public static final int ASAP_RUMAH = 0;
	public static final int ASAP_USAHA = 1;
	public static final int ASAP_PANDAI = 2;
	public static final int ASAP_DUKA = 3;

	/** The kit's PAJANGAN list in order (the index is the {@code sub|ekstra} value of a pajangan). */
	public static final List<String> PAJANGAN = List.of("mangkuk", "mi", "teko", "botol_pil", "herba", "kain", "pedang", "papan", "ikan",
			"bakpao", "lentera_kertas", "gulungan", "jimat", "keranjang", "jala", "guci_arak", "dadu_cawan", "bunga", "potret_almarhum",
			"uang_kertas", "dacing");

	public static int hadap(short data) {
		return data & 3;
	}

	public static int sub(short data) {
		return data >> 2 & 7;
	}

	public static int ekstra(short data) {
		return data >> 5 & 7;
	}

	public static int ref(short data) {
		return data >> 8 & 255;
	}

	/** The pajangan index of a pajangan titik, or -1. */
	public static int pajangan(short data) {
		int i = data >> 2 & 63;
		return i < PAJANGAN.size() ? i : -1;
	}

	public static short pak(int hadap, int sub, int ekstra, int ref) {
		return (short) (hadap & 3 | (sub & 7) << 2 | (ekstra & 7) << 5 | (ref & 255) << 8);
	}

	/** Unit vector x of a hadap (0 north, 1 east, 2 south, 3 west). */
	public static int dx(int hadap) {
		return switch (hadap & 3) {
			case 1 -> 1;
			case 3 -> -1;
			default -> 0;
		};
	}

	/** Unit vector z of a hadap. */
	public static int dz(int hadap) {
		return switch (hadap & 3) {
			case 0 -> -1;
			case 2 -> 1;
			default -> 0;
		};
	}

	private TitikKode() {
	}
}
