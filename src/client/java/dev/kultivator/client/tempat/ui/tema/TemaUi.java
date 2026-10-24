package dev.kultivator.client.tempat.ui.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.PanelTempat;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Shared drawing of the themed-town screens (DESIGN 38.T.13.3, 38TV-U): the textures of {@code tools/gen_tempat_tema_ui.py}
 * ({@code textures/gui/tempat/tema/*}), the icon slots of {@code ikon_tema.png}, the theme colour tokens, the lang
 * helper {@link #k} ({@code kultivator.tempat_tema_ui.*}) and a few small blocks (meter, chip, mural, robe, tower) that
 * several screens use. Pure blits and fills, cheap enough for weak laptops; no shader, nothing in the world.
 */
public final class TemaUi {

	public static final String K = "kultivator.tempat_tema_ui.";

	public static final Identifier IKON = tex("ikon_tema");
	public static final Identifier MURAL = tex("mural");
	public static final Identifier IRAMA = tex("irama");
	public static final Identifier JUBAH = tex("jubah");
	public static final Identifier MENARA = tex("menara");
	public static final Identifier LATAR_GUNUNG = tex("latar_gunung");
	public static final Identifier KARTU = tex("kartu");
	public static final Identifier TENDA = tex("tenda");
	public static final Identifier SURAT = tex("surat");
	public static final Identifier KANDANG = tex("kandang");

	// ikon_tema.png slots (row * 16 + column), in the generator's ICONS + EXTRA order
	public static final int I_PELABUHAN = 0;
	public static final int I_OASIS = 1;
	public static final int I_AIR = 2;
	public static final int I_GUNUNG = 3;
	public static final int I_IBLIS = 4;
	public static final int I_IBU_KOTA = 5;
	public static final int I_INFAMI = 6;
	public static final int I_GENTAR = 7;
	public static final int I_SUASANA = 8;
	public static final int I_BADAI = 9;
	public static final int I_KONTRAK = 10;
	public static final int I_ASAL = 11;
	public static final int I_TERBAIK = 12;
	public static final int I_JENUH = 13;
	public static final int I_KAPAL = 14;
	public static final int I_UNTA = 15;
	/** First trade good (row 1 = DagangItems order: ikan_asin_roh .. racun_ular_hitam; minyak_bayangan at 64). */
	public static final int I_BARANG_PERTAMA = 16;
	public static final int I_CAWAN = 32;
	public static final int I_LENSA = 33;
	public static final int I_SURAT = 34;
	public static final int I_CAP = 35;
	public static final int I_TUNGKU = 36;
	public static final int I_TENUN = 37;
	public static final int I_GENDERANG = 38;
	public static final int I_PENARI = 39;
	public static final int I_KABIN = 40;
	public static final int I_BORDES = 41;
	public static final int I_KUIL = 42;
	public static final int I_TOPENG = 43;
	public static final int I_PETA_GELAP = 44;
	public static final int I_PIL_HITAM = 45;
	public static final int I_CAKAR = 46;
	public static final int I_KARUNG = 47;
	public static final int I_KOIN_DARAH = 48;
	public static final int I_ARENA = 49;
	public static final int I_TANGGA = 50;
	public static final int I_RANTAI = 51;
	public static final int I_SEL = 52;
	public static final int I_GULUNGAN = 53;
	public static final int I_LANTAI = 54;
	public static final int I_JUBAH = 55;
	public static final int I_KUAS = 56;
	public static final int I_BAIT = 57;
	public static final int I_TALI = 58;
	public static final int I_KATROL = 59;
	public static final int I_GALANGAN = 60;
	public static final int I_LAYAR = 61;
	public static final int I_SEWA = 62;
	public static final int I_ORANG = 63;
	public static final int I_MINYAK = 64;
	public static final int I_RESTU = 65;
	public static final int I_AIR_SUCI = 66;
	public static final int I_BINGKAI_MURAL = 67;
	public static final int I_SUAR = 68;
	public static final int I_OMBAK = 69;
	public static final int I_MACET = 70;
	public static final int I_MALAM = 71;

	/** Trade good ids in {@code DagangItems} registration order (slot = I_BARANG_PERTAMA + index, the 17th at 64). */
	private static final String[] BARANG = {"ikan_asin_roh", "mutiara_laut", "rempah_seberang", "porselen_hijau", "kurma_emas",
			"permadani_gurun", "giok_barat", "kaca_warna", "sutra_awan", "teh_bi_luo", "arak_beras", "herba_tebing_kering", "dupa_gunung",
			"teh_awan_gunung", "lingzhi_gunung", "racun_ular_hitam", "minyak_bayangan"};

	// Theme colours (Tema.warna) and a few inks of the demonic city
	public static final int PELABUHAN = 0xFF5FA8C8;
	public static final int OASIS = 0xFFE0B060;
	public static final int AIR = 0xFF7FC8B0;
	public static final int GUNUNG = 0xFFA0A8B8;
	public static final int IBLIS = 0xFF8A1020;
	public static final int IBU_KOTA = TempatWarna.ISTANA;
	public static final int DARAH = 0xFFB0202A;
	public static final int DARAH_TERANG = 0xFFFF6A6A;
	public static final int PASIR = 0xFFC8A060;

	private static Identifier tex(String name) {
		return Kultivator.id("textures/gui/tempat/tema/" + name + ".png");
	}

	/** Text of this module's fragment ({@code lang-src/tempat_tema_ui.*}). */
	public static MutableComponent k(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	/** Theme emblem slot for a theme id ({@code pelabuhan|oasis|air|gunung|iblis|ibu_kota}); -1 unknown. */
	public static int ikonTema(String tema) {
		return switch (tema) {
			case "pelabuhan" -> I_PELABUHAN;
			case "oasis" -> I_OASIS;
			case "air" -> I_AIR;
			case "gunung" -> I_GUNUNG;
			case "iblis" -> I_IBLIS;
			case "ibu_kota" -> I_IBU_KOTA;
			default -> -1;
		};
	}

	/** Theme colour for a theme id. */
	public static int warnaTema(String tema) {
		return switch (tema) {
			case "pelabuhan" -> PELABUHAN;
			case "oasis" -> OASIS;
			case "air" -> AIR;
			case "gunung" -> GUNUNG;
			case "iblis" -> IBLIS;
			default -> IBU_KOTA;
		};
	}

	/** Icon slot of a trade good id ({@code dagang/<id>} or the bare id); -1 when unknown. */
	public static int ikonBarang(String id) {
		String bare = id.startsWith("dagang/") ? id.substring(7) : id;
		int colon = bare.indexOf(':');
		if (colon >= 0) {
			bare = bare.substring(colon + 1);
			if (bare.startsWith("dagang/")) {
				bare = bare.substring(7);
			}
		}
		for (int i = 0; i < BARANG.length; i++) {
			if (BARANG[i].equals(bare)) {
				return i < 16 ? I_BARANG_PERTAMA + i : I_MINYAK;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------------------------------------ blits

	private static void blit(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, float u, float v, int sw, int sh, int tw, int th,
			int warna) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, sw, sh, tw, th, warna);
	}

	/** Icon {@code slot} of {@code ikon_tema.png} at {@code size} px; slot -1 draws nothing. */
	public static void ikon(GuiGraphicsExtractor g, int slot, int x, int y, int size, int warna) {
		if (slot < 0 || slot >= 80) {
			return;
		}
		blit(g, IKON, x, y, size, size, (slot % 16) * 16.0F, (slot / 16) * 16.0F, 16, 16, 256, 80, warna);
	}

	public static void ikon(GuiGraphicsExtractor g, int slot, int x, int y, int size) {
		ikon(g, slot, x, y, size, 0xFFFFFFFF);
	}

	/** Mural panel {@code gambar} 0-15 of {@code mural.png} scaled into the box. */
	public static void mural(GuiGraphicsExtractor g, int gambar, int x, int y, int w, int h, int warna) {
		int s = Math.floorMod(gambar, 16);
		blit(g, MURAL, x, y, w, h, (s % 4) * 64.0F, (s / 4) * 64.0F, 64, 64, 256, 256, warna);
	}

	/** Arrow note of the rhythm atlas: {@code arah} 0 up, 1 right, 2 down, 3 left; lit when hit. */
	public static void nada(GuiGraphicsExtractor g, int arah, boolean lit, int x, int y, int size, int warna) {
		int cell = (lit ? 4 : 0) + Math.floorMod(arah, 4);
		blit(g, IRAMA, x, y, size, size, cell * 16.0F, 0.0F, 16, 16, 128, 96, warna);
	}

	/** Rhythm atlas pieces: 0 hit ring, 1 drum skin, 2 loom (32 x 32 each). */
	public static void iramaBesar(GuiGraphicsExtractor g, int bagian, int x, int y, int size, int warna) {
		blit(g, IRAMA, x, y, size, size, bagian * 32.0F, 16.0F, 32, 32, 128, 96, warna);
	}

	/** Shuttle (0) or drumstick (1), 16 x 16. */
	public static void iramaKecil(GuiGraphicsExtractor g, int bagian, int x, int y, int size, int warna) {
		blit(g, IRAMA, x, y, size, size, 96.0F, 16.0F + bagian * 16.0F, 16, 16, 128, 96, warna);
	}

	/** Whirling dancer frame 0-3 (32 x 48). */
	public static void penari(GuiGraphicsExtractor g, int frame, int x, int y, int w, int h, int warna) {
		blit(g, IRAMA, x, y, w, h, Math.floorMod(frame, 4) * 32.0F, 48.0F, 32, 48, 128, 96, warna);
	}

	/**
	 * The robe on its stand: body tinted {@code dye} (ARGB), folds and collar over it, then the motif ({@code awan|bangau|
	 * teratai|naga}, empty = none) tinted gold. Drawn at 32 x 64 scaled by {@code skala}.
	 */
	public static void jubah(GuiGraphicsExtractor g, int x, int y, float skala, int dye, String motif) {
		int w = Math.round(32 * skala);
		int h = Math.round(64 * skala);
		blit(g, JUBAH, x, y, w, h, 0.0F, 0.0F, 32, 64, 256, 64, 0xFFFFFFFF);
		blit(g, JUBAH, x, y, w, h, 32.0F, 0.0F, 32, 64, 256, 64, dye | 0xFF000000);
		blit(g, JUBAH, x, y, w, h, 64.0F, 0.0F, 32, 64, 256, 64, 0xFFFFFFFF);
		int m = switch (motif) {
			case "awan" -> 0;
			case "bangau" -> 1;
			case "teratai" -> 2;
			case "naga" -> 3;
			default -> -1;
		};
		if (m >= 0) {
			blit(g, JUBAH, x, y, w, h, 96.0F + m * 32.0F, 0.0F, 32, 64, 256, 64, 0xFFF7DC7E);
		}
	}

	/** The hanger tag beside the robe (32 x 64 cell). */
	public static void labelJubah(GuiGraphicsExtractor g, int x, int y, float skala) {
		blit(g, JUBAH, x, y, Math.round(32 * skala), Math.round(64 * skala), 224.0F, 0.0F, 32, 64, 256, 64, 0xFFFFFFFF);
	}

	/** The forbidden tower (64 x 160) scaled to height {@code h}; returns the drawn width. */
	public static int menara(GuiGraphicsExtractor g, int x, int y, int h) {
		int w = Math.round(h * 64.0F / 160.0F);
		blit(g, MENARA, x, y, w, h, 0.0F, 0.0F, 64, 160, 64, 160, 0xFFFFFFFF);
		return w;
	}

	/** The mountain backdrop stretched over the box. */
	public static void latarGunung(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int warna) {
		blit(g, LATAR_GUNUNG, x0, y0, x1 - x0, y1 - y0, 0.0F, 0.0F, 160, 80, 160, 80, warna);
	}

	/** Arena fighter card frame (64 x 96) at the given size. */
	public static void kartuPetarung(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		blit(g, KARTU, x, y, w, h, 0.0F, 0.0F, 64, 96, 128, 96, 0xFFFFFFFF);
	}

	/** The "VS" blood seal (32 x 32). */
	public static void segelVs(GuiGraphicsExtractor g, int x, int y, int size, int warna) {
		blit(g, KARTU, x, y, size, size, 64.0F, 0.0F, 32, 32, 128, 96, warna);
	}

	/** Odds chip (32 x 16), bet plate (64 x 16) and ladder rung (64 x 16). */
	public static void chipOdds(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		blit(g, KARTU, x, y, w, h, 96.0F, 0.0F, 32, 16, 128, 96, 0xFFFFFFFF);
	}

	public static void pelatTaruhan(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		blit(g, KARTU, x, y, w, h, 64.0F, 32.0F, 64, 16, 128, 96, 0xFFFFFFFF);
	}

	public static void anakTangga(GuiGraphicsExtractor g, int x, int y, int w, int h, int warna) {
		blit(g, KARTU, x, y, w, h, 64.0F, 48.0F, 64, 16, 128, 96, warna);
	}

	/** The striped bazaar awning tiled across {@code x0..x1} (16 px high). */
	public static void tenda(GuiGraphicsExtractor g, int x0, int x1, int y) {
		for (int x = x0; x < x1; x += 64) {
			int w = Math.min(64, x1 - x);
			blit(g, TENDA, x, y, w, 16, 0.0F, 0.0F, w, 16, 64, 16, 0xFFFFFFFF);
		}
	}

	/** Cargo manifest paper (nine-slice of {@code surat.png}). */
	public static void surat(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		PanelTempat.bingkai(g, SURAT, x0, y0, x1, y1);
	}

	/** Iron cell bars (nine-slice of {@code kandang.png}). */
	public static void kandang(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		PanelTempat.bingkai(g, KANDANG, x0, y0, x1, y1);
	}

	// ------------------------------------------------------------------------------------------------ small blocks

	/**
	 * A horizontal meter: track, fill {@code frac} 0..1 in {@code warna}, a 1 px shine, an optional tick at {@code tanda}
	 * (0..1, -1 none) and the icon at the left. Returns the height (8).
	 */
	public static int meter(Lukis l, int ikon, int x0, int y, int x1, float frac, int warna, float tanda) {
		int bx0 = x0;
		if (ikon >= 0) {
			ikon(l.g, ikon, x0, y - 1, 10);
			bx0 = x0 + 12;
		}
		float f = Math.max(0.0F, Math.min(1.0F, frac));
		l.g.fill(bx0, y + 1, x1, y + 7, Palette.argb(0x60, 0x000000));
		int fx = bx0 + Math.round((x1 - bx0) * f);
		if (fx > bx0) {
			l.g.fillGradient(bx0, y + 1, fx, y + 7, Palette.lighten(warna, 0.25F), Palette.darken(warna, 0.2F));
			l.g.fill(bx0, y + 1, fx, y + 2, Palette.argb(0x80, 0xFFFFFF));
		}
		Brush.frame(l.g, bx0 - 1, y, x1 + 1, y + 8, Palette.argb(0x90, TempatWarna.EMAS_GELAP & 0xFFFFFF));
		if (tanda >= 0.0F) {
			int tx = bx0 + Math.round((x1 - bx0) * Math.min(1.0F, tanda));
			l.g.fill(tx, y - 1, tx + 1, y + 9, TempatWarna.EMAS_TERANG);
		}
		return 8;
	}

	/** A small rounded chip with an icon and a caption (status chips: badai, macet, malam ...). Returns its width. */
	public static int chip(Lukis l, int ikon, Component teks, int x, int y, int warna) {
		int w = Math.round(l.font.width(teks) * 0.75F) + (ikon >= 0 ? 15 : 6);
		Brush.chamfer(l.g, x, y, x + w, y + 11, 2, Palette.argb(0xD8, warna & 0xFFFFFF));
		Brush.frame(l.g, x, y, x + w, y + 11, Palette.argb(0x70, 0x000000));
		int tx = x + 3;
		if (ikon >= 0) {
			ikon(l.g, ikon, x + 2, y + 1, 9);
			tx = x + 12;
		}
		Brush.text(l.g, l.font, teks, tx, y + 2.5F, Palette.luminance(warna) > 0.55F ? 0xFF201410 : 0xFFFFF6E8, 0.75F, false);
		return w;
	}

	/** Theme emblem in a coloured roundel (city columns, contract targets). */
	public static void lambang(Lukis l, String tema, int cx, int cy, int r) {
		int warna = warnaTema(tema);
		Brush.disc(l.g, cx, cy, r, Palette.argb(0xC0, Palette.darken(warna, 0.35F) & 0xFFFFFF));
		Brush.ring(l.g, cx, cy, r - 1.0F, r, Palette.argb(0xE0, warna & 0xFFFFFF));
		int s = Math.max(8, r * 2 - 4);
		ikon(l.g, ikonTema(tema), cx - s / 2, cy - s / 2, s);
	}

	/** Stars for a count {@code isi} of {@code total} using the kit icons. Returns the width. */
	public static int bintang(Lukis l, int isi, int total, int x, int y, int size) {
		for (int i = 0; i < total; i++) {
			IkonTempat.gambar(l.g, i < isi ? IkonTempat.BINTANG_PENUH : IkonTempat.BINTANG_KOSONG, x + i * (size - 1), y, size, 0xFFFFFFFF);
		}
		return total * (size - 1) + 1;
	}

	/** A soft glow behind something precious (kit lantern glow). */
	public static void cahaya(Lukis l, int cx, int cy, int r, int warna) {
		IkonTempat.cahaya(l.g, cx - r, cy - r, r * 2, r * 2, warna);
	}

	/** Text colour that reads on the style's well (wells are light on every style). */
	public static int tintaSumur(TempatWarna.Gaya gaya) {
		return gaya.teksSumur();
	}

	private TemaUi() {
	}
}
