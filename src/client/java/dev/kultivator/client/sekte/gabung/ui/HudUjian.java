package dev.kultivator.client.sekte.gabung.ui;

import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.hud.SlotSisi;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.api.Corak;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import static dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.J_CORAK;
import static dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.J_HATI;
import static dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.J_TAMU;
import static dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.J_TANGGA;
import static dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.J_WAWANCARA;
import static dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.baca;

/**
 * The entrance test HUD {@code gabung_ujian} (DESIGN 41.10.9, 41.5.4), the first content of the right-side slot
 * ({@link SlotSisi}, priority {@link SlotSisi#UJIAN}; DESIGN 43.9): a jade slip with the stage seal (一 二 三) and name,
 * the stage score and the time ring, then, on a lacquer inlay, the stage's own gauge read from the shared byte layout
 * {@code GabungLembar.HudUjian}: the Mountain Pressure bar with its Slowness marks (40, 70) and the climb, the Seratus
 * Tebasan counter and the six dummies, the bronze-men waves and the vow, the beast's health with the 20 % line, the
 * formation eyes and the bearing to the next, the Lima Pilar dial with its band and needle, the furnace, the carried
 * stone, the blood pool, the three cups, the times seen in the silent trial, the interview's questions. Every gauge has
 * its line of text under it at 1.0. Hidden with F1, in Mode Rekam and while no test runs ({@code GabungClient.ujian()}
 * turns stale after 3 s).
 */
public final class HudUjian implements HudElement {

	private static final String[] ANGKA = {"一", "二", "三"};
	private static final int PLAT_W = 196;
	private static final int TINTA = 0xFFF4E8D0;
	private static final int SAMAR = 0xFFB8A888;
	/** Line height of text at 1.0. */
	private static final int BARIS = 10;

	private float tekananTampil;

	public HudUjian() {
		SlotSisi.daftar(SlotSisi.UJIAN, () -> {
			GabungPayloads.Ujian u = GabungClient.ujian();
			return u != null && u.tahap() > 0;
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || EpicDirector.recordHide()) {
			return;
		}
		GabungPayloads.Ujian u = GabungClient.ujian();
		if (u == null || u.tahap() <= 0) {
			tekananTampil = 0.0F;
			return;
		}
		try {
			gambar(g, minecraft.font, u, g.guiWidth(), g.guiHeight());
		} catch (RuntimeException ignored) {
			// a malformed packet must never break the HUD
		}
	}

	private void gambar(GuiGraphicsExtractor g, Font font, GabungPayloads.Ujian u, int lebar, int tinggiLayar) {
		byte[] d = dariServer(u.data());
		int jenis = baca(d, 0);
		int isi = tinggiIsi(jenis);
		int tinggi = 30 + (isi > 0 ? isi + 6 : 0);
		int x1 = SlotSisi.kanan(lebar);
		int x0 = x1 - Math.min(PLAT_W, lebar - 12);
		int w = x1 - x0;
		int y0 = SlotSisi.atas(tinggiLayar, tinggi);
		// the slip, and the lacquer inlay under the gauge
		Kabar.slip(g, x0, y0, x1, y0 + tinggi, Palette.SEAL);
		if (isi > 0) {
			g.fill(x0 + 4, y0 + 29, x1 - 4, y0 + tinggi - 4, 0xE0120E0C);
			Brush.frame(g, x0 + 4, y0 + 29, x1 - 4, y0 + tinggi - 4, Palette.argb(0xC0, Palette.GOLD_DARK));
		}
		// stage seal and name
		int tahap = Math.max(1, Math.min(3, u.tahap()));
		IkonGabung.hud(g, x0 + 4, y0 + 3, 24, 24, 96, 24, 32, 32, 0xFFFFFFFF);
		Component angka = Component.literal(ANGKA[tahap - 1]);
		Brush.centered(g, font, angka, x0 + 16, y0 + 11, 0xFFFFF0E0, 1.0F, true);
		Brush.fit(g, font, Brush.bold(namaTahap(jenis, tahap)), x0 + 31, y0 + 5, w - 62, Palette.INK, 1.0F, 1.0F, false);
		int skor = Math.round(Math.max(0.0F, Math.min(1.0F, u.skor())) * 100.0F);
		Brush.fit(g, font, t("hud.skor", skor), x0 + 31, y0 + 16, w - 62, Palette.INK_SOFT, 1.0F, 1.0F, false);
		// the time ring
		float detik = Math.max(0, u.waktu()) / 20.0F;
		int rx = x1 - 16;
		int ry = y0 + 15;
		IkonGabung.hud(g, rx - 12, ry - 12, 24, 24, 64, 24, 32, 32, 0xFFFFFFFF);
		int warnaWaktu = detik <= 10.0F ? 0xFFE04A30 : detik <= 30.0F ? 0xFFD0A040 : 0xFF7FD6A0;
		Component waktu = Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", (int) detik / 60, (int) detik % 60));
		Brush.centered(g, font, waktu, rx, ry - 4, warnaWaktu, 1.0F, true);
		if (detik <= 10.0F) {
			Brush.halo(g, rx, ry, 12.5F, 2.0F, 0xE04A30, 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 150.0D));
		}
		if (isi <= 0) {
			return;
		}
		int iy = y0 + 33;
		int ix0 = x0 + 8;
		int ix1 = x1 - 8;
		if (jenis == J_TANGGA) {
			tangga(g, font, u, d, ix0, iy, ix1);
		} else if (jenis >= J_CORAK && jenis < J_CORAK + Corak.values().length) {
			corak(g, font, Corak.byOrdinal(jenis - J_CORAK), d, ix0, iy + 2, ix1);
		} else if (jenis == J_HATI) {
			int soal = Math.max(1, baca(d, 1));
			int kesan = baca(d, 2) - 128;
			pips(g, ix0, iy, 3, soal - 1, 0xFFE0B040);
			IkonGabung.gambar(g, IkonGabung.HATI, ix0 + 30, iy - 2, 12);
			Brush.fit(g, font, t("hud.hati", soal, kesan >= 0 ? "+" + kesan : Integer.toString(kesan)), ix0 + 45, iy, ix1 - ix0 - 45,
					kesan >= 0 ? 0xFF9AE0B0 : 0xFFFF9A8A, 1.0F, 1.0F, false);
		} else if (jenis == J_WAWANCARA) {
			IkonGabung.gambar(g, IkonGabung.HATI, ix0, iy - 2, 12);
			Brush.fit(g, font, t(baca(d, 1) > 0 ? "hud.wawancara.selesai" : "hud.wawancara"), ix0 + 15, iy, ix1 - ix0 - 15, TINTA, 1.0F, 1.0F, false);
		} else if (jenis == J_TAMU) {
			pips(g, ix0, iy, 3, baca(d, 1) - 1, 0xFFE0B040);
			Brush.fit(g, font, t("hud.tamu", baca(d, 1), baca(d, 2)), ix0 + 30, iy, ix1 - ix0 - 30, TINTA, 1.0F, 1.0F, false);
		}
	}

	/**
	 * F41-1: the senders use their own layout (TanggaUjian {@code [0xFF, illusions, slowness]}, UjianCorak byte 0 = Corak
	 * ordinal with the per-style bytes of its javadoc, HariUjian {@code [0xFE, question, impression + 64]}; 41V-P's
	 * UjianFx reads that layout too). This maps it onto the {@code J_*} layout the panels below draw.
	 */
	static byte[] dariServer(byte[] s) {
		int k = baca(s, 0);
		if (k == 0xFF) {
			return dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(J_TANGGA, 0, 0, baca(s, 2), 0, baca(s, 1));
		}
		if (k == 0xFE) {
			return dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(J_HATI, baca(s, 1), baca(s, 2) + 64);
		}
		if (k >= Corak.values().length) {
			return new byte[0];
		}
		int j = J_CORAK + k;
		return switch (Corak.byOrdinal(k)) {
			case PEDANG -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), baca(s, 2), baca(s, 4) == 0 ? 255 : baca(s, 4) - 1, 0);
			case BUDDHA -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), baca(s, 2), baca(s, 3), pct(s, 4), baca(s, 5));
			case BINATANG -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, pct(s, 2), pct(s, 4), baca(s, 1));
			case FORMASI -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), baca(s, 3), 255);
			case UNSUR -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), baca(s, 2), baca(s, 3), baca(s, 4) / 2, baca(s, 6),
					pct(s, 7), pct(s, 8), pct(s, 9), pct(s, 10), pct(s, 11), Math.max(0, 15 - baca(s, 12)));
			case ALKIMIA -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), baca(s, 3) == 0 ? 255 : baca(s, 3) - 1);
			case TUBUH -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, pct(s, 1), baca(s, 2));
			case DARAH -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), Math.max(0, baca(s, 1) - 1), pct(s, 3), baca(s, 4));
			case RACUN -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1) + 1, baca(s, 3), baca(s, 4),
					baca(s, 5) == 2 || baca(s, 6) == 2 || baca(s, 7) == 2 ? 1 : 0);
			case BAYANGAN -> dev.kultivator.sekte.gabung.ui.GabungLembar.HudUjian.data(j, baca(s, 1), baca(s, 2), baca(s, 5), pct(s, 3));
		};
	}

	/** A 0-255 fraction byte as 0-100. */
	private static int pct(byte[] s, int i) {
		return Math.round(baca(s, i) * 100.0F / 255.0F);
	}

	/** Height of the gauge inlay's content (0: no gauge). */
	private static int tinggiIsi(int jenis) {
		if (jenis == J_TANGGA) {
			return 36;
		}
		if (jenis == J_CORAK + Corak.UNSUR.ordinal()) {
			return 68;
		}
		if (jenis >= J_CORAK && jenis < J_CORAK + Corak.values().length) {
			return 24;
		}
		return jenis == 0 ? 0 : 12;
	}

	private static Component namaTahap(int jenis, int tahap) {
		if (jenis >= J_CORAK && jenis < J_CORAK + Corak.values().length) {
			return Corak.byOrdinal(jenis - J_CORAK).namaUjian();
		}
		return switch (jenis) {
			case J_TANGGA -> t("hud.nama.tangga");
			case J_HATI -> t("hud.nama.hati");
			case J_WAWANCARA -> t("hud.nama.wawancara");
			case J_TAMU -> t("hud.nama.tamu");
			default -> t("hud.nama.tahap", tahap);
		};
	}

	/** One line of text at 1.0 across the inlay, cut with "…". */
	private static void baris(GuiGraphicsExtractor g, Font font, Component text, int x0, int y, int x1, int warna) {
		Brush.fit(g, font, text, x0, y, x1 - x0, warna, 1.0F, 1.0F, false);
	}

	// ================================================================== stage 1

	private void tangga(GuiGraphicsExtractor g, Font font, GabungPayloads.Ujian u, byte[] d, int x0, int y, int x1) {
		float tekanan = Math.max(0.0F, Math.min(100.0F, u.tekanan()));
		tekananTampil += (tekanan - tekananTampil) * 0.25F;
		int w = x1 - x0;
		IkonGabung.hud(g, x0, y, w, 14, 0, 0, 128, 14, 0xFFFFFFFF);
		IkonGabung.gambar(g, IkonGabung.TEKANAN, x0 + 1, y + 1, 12);
		int fx0 = x0 + 12;
		int fw = w - 15;
		float frac = tekananTampil / 100.0F;
		int warna = frac >= 0.7F ? 0xFFE0302A : frac >= 0.4F ? 0xFFE08A2A : 0xFF67C99B;
		IkonGabung.hud(g, fx0, y + 4, Math.round(fw * frac), 6, 0, 16, Math.max(1, Math.round(120 * frac)), 6, warna);
		for (float tanda : new float[] {0.4F, 0.7F}) {
			int tx = fx0 + Math.round(fw * tanda);
			g.fill(tx, y + 2, tx + 1, y + 12, 0xFFF7DC7E);
		}
		int puncak = baca(d, 2);
		if (puncak > 0) {
			int px = fx0 + Math.round(fw * puncak / 100.0F);
			g.fill(px, y + 3, px + 1, y + 11, 0xA0FFFFFF);
		}
		boolean meditasi = baca(d, 4) > 0;
		baris(g, font, t("hud.tekanan", Math.round(tekanan)), x0 + 2, y + 16, meditasi ? x1 - 14 : x1, frac >= 0.7F ? 0xFFFF8A7A : TINTA);
		if (meditasi) {
			IkonGabung.gambar(g, IkonGabung.MEDITASI, x1 - 12, y + 15, 10);
		}
		MutableComponent info = t("hud.tinggi", baca(d, 1));
		if (baca(d, 3) > 0) {
			info.append("  ").append(t("hud.lambat", baca(d, 3)));
		}
		info.append("  ").append(t("hud.ilusi", baca(d, 5)));
		baris(g, font, info, x0 + 2, y + 16 + BARIS, x1, SAMAR);
	}

	// ================================================================== stage 2

	/** Row 1 (around {@code y}): the style's icon and its gauge; row 2 ({@code y + 11}): its line of text. */
	private void corak(GuiGraphicsExtractor g, Font font, Corak corak, byte[] d, int x0, int y, int x1) {
		int ikon = IkonGabung.ujianCorak(corak.id());
		IkonGabung.gambar(g, ikon, x0, y - 2, 12);
		int tx = x0 + 15;
		int ty = y + 11;
		switch (corak) {
			case PEDANG -> {
				int lit = baca(d, 3);
				for (int i = 0; i < 6; i++) {
					int bx = tx + i * 13;
					IkonGabung.gambar(g, IkonGabung.BONEKA, bx, y - 2, 10, i == lit ? 0xFFFFFFFF : 0x70FFFFFF);
					if (i == lit) {
						Brush.halo(g, bx + 5, y + 3, 6.0F, 1.5F, 0xFFD27A, 0.8F);
					}
				}
				baris(g, font, t("hud.pedang", baca(d, 1), baca(d, 2)), x0, ty, x1, TINTA);
			}
			case BUDDHA -> {
				pips(g, tx, y + 1, 3, baca(d, 2) - 1, 0xFFE0B040);
				boolean langgar = baca(d, 5) > 0;
				baris(g, font, t(langgar ? "hud.buddha.langgar" : "hud.buddha", baca(d, 1), baca(d, 3), baca(d, 4)), x0, ty, x1,
						langgar ? 0xFFFF8A7A : TINTA);
			}
			case BINATANG -> {
				int hp = baca(d, 1);
				int target = Math.max(1, baca(d, 2) == 0 ? 20 : baca(d, 2));
				int w = x1 - tx;
				g.fill(tx, y + 1, tx + w, y + 6, 0x80000000);
				g.fill(tx, y + 1, tx + Math.round(w * hp / 100.0F), y + 6, hp <= target ? 0xFF67C99B : 0xFFE0602A);
				int lx = tx + Math.round(w * target / 100.0F);
				g.fill(lx, y - 1, lx + 1, y + 8, 0xFFF7DC7E);
				baris(g, font, t("hud.binatang." + Math.min(2, baca(d, 3)), hp), x0, ty, x1, TINTA);
			}
			case FORMASI -> {
				pips(g, tx, y + 1, 3, baca(d, 1) - 1, 0xFF6FE0FF);
				int trigram = baca(d, 2);
				if (trigram < 8) {
					for (int bar = 0; bar < 3; bar++) {
						boolean patah = (trigram >> bar & 1) == 0;
						int by = y - 1 + bar * 3;
						int bx = tx + 30;
						if (patah) {
							g.fill(bx, by, bx + 4, by + 2, TINTA);
							g.fill(bx + 6, by, bx + 10, by + 2, TINTA);
						} else {
							g.fill(bx, by, bx + 10, by + 2, TINTA);
						}
					}
				}
				int arah = baca(d, 3);
				if (arah < 8) {
					float ang = (float) (arah * Math.PI / 4.0D - Math.PI / 2.0D);
					float cx = x1 - 8;
					float cy = y + 3;
					Brush.disc(g, cx, cy, 6.0F, 0x80000000);
					Brush.line(g, cx, cy, cx + (float) Math.cos(ang) * 5.0F, cy + (float) Math.sin(ang) * 5.0F, 1.5F, 0xFFE0302A);
				}
				baris(g, font, t("hud.formasi", baca(d, 1)), x0, ty, x1, TINTA);
			}
			case UNSUR -> limaPilar(g, font, d, x0, y, x1);
			case ALKIMIA -> {
				int fase = Math.min(2, baca(d, 1));
				int mutu = baca(d, 2);
				Component s = mutu < 4 ? t("hud.alkimia.mutu", t("hud.mutu." + mutu)) : t("hud.alkimia." + fase);
				baris(g, font, s, x0, ty, x1, TINTA);
			}
			case TUBUH -> {
				int tinggi = baca(d, 1);
				int w = x1 - tx;
				g.fill(tx, y + 1, tx + w, y + 6, 0x80000000);
				g.fill(tx, y + 1, tx + Math.round(w * tinggi / 100.0F), y + 6, 0xFFC77B3B);
				baris(g, font, t(baca(d, 2) > 0 ? "hud.tubuh" : "hud.tubuh.lepas", tinggi), x0, ty, x1, baca(d, 2) > 0 ? TINTA : 0xFFFF8A7A);
			}
			case DARAH -> {
				pips(g, tx, y + 1, 3, baca(d, 2) - 1, 0xFFD0303A);
				baris(g, font, t(baca(d, 4) > 0 ? "hud.darah.korban" : "hud.darah", baca(d, 1), baca(d, 3)), x0, ty, x1, TINTA);
			}
			case RACUN -> {
				int cawan = Math.max(1, baca(d, 1));
				for (int i = 0; i < 3; i++) {
					IkonGabung.gambar(g, IkonGabung.CAWAN, tx + i * 12, y - 2, 10, i < cawan ? 0xFFFFFFFF : 0x60FFFFFF);
				}
				baris(g, font, t(baca(d, 4) > 0 ? "hud.racun.keracunan" : "hud.racun", baca(d, 2), baca(d, 3)), x0, ty, x1,
						baca(d, 4) > 0 ? 0xFFD7B4FF : TINTA);
			}
			case BAYANGAN -> {
				int dilihat = baca(d, 1);
				for (int i = 0; i < 3; i++) {
					IkonGabung.gambar(g, IkonGabung.PENJAGA, tx + i * 12, y - 2, 10, i < dilihat ? 0xFFFF6A5A : 0x60FFFFFF);
				}
				int waspada = Math.min(100, baca(d, 4));
				int bw = x1 - tx - 40;
				g.fill(tx + 40, y + 1, tx + 40 + bw, y + 5, 0x80000000);
				g.fill(tx + 40, y + 1, tx + 40 + Math.round(bw * waspada / 100.0F), y + 5, waspada >= 70 ? 0xFFE0302A : 0xFFD0A040);
				baris(g, font, t(baca(d, 2) > 0 ? "hud.bayangan.giok" : "hud.bayangan", baca(d, 3)), x0, ty, x1, TINTA);
			}
			default -> {
			}
		}
	}

	/** The Lima Pilar dial: the band to keep the needle in, the needle, five pillars with their accuracy, then its line. */
	private void limaPilar(GuiGraphicsExtractor g, Font font, byte[] d, int x0, int y, int x1) {
		int cx = (x0 + x1) / 2;
		int dx0 = cx - 32;
		IkonGabung.hud(g, dx0, y, 64, 36, 0, 24, 64, 36, 0xFFFFFFFF);
		float pusat = baca(d, 3) / 255.0F;
		float lebar = Math.max(0.02F, baca(d, 4) / 255.0F);
		float jarum = baca(d, 2) / 255.0F;
		float oy = y + 33;
		boolean di = Math.abs(jarum - pusat) <= lebar;
		Brush.arc(g, cx, oy, 19.0F, 26.0F, (float) (-Math.PI + Math.PI * Math.max(0.0F, pusat - lebar)),
				(float) (-Math.PI + Math.PI * Math.min(1.0F, pusat + lebar)), di ? 0xC067C99B : 0xA0D0A040);
		float ang = (float) (-Math.PI + Math.PI * jarum);
		Brush.line(g, cx, oy, cx + (float) Math.cos(ang) * 25.0F, oy + (float) Math.sin(ang) * 25.0F, 1.5F, di ? 0xFFFFFFFF : 0xFFFF6A5A);
		int pilar = Math.min(4, baca(d, 1));
		int[] unsur = {0xFF9FD8FF, 0xFFFF6A2A, 0xFF7CD67C, 0xFFE2B33C, 0xFFD8E0E6};
		for (int i = 0; i < 5; i++) {
			int px = cx - 40 + i * 20;
			int py = y + 41;
			int acc = baca(d, 6 + i);
			Brush.disc(g, px, py, 4.0F, i == pilar ? unsur[i] : Palette.argb(0x70, unsur[i] & 0xFFFFFF));
			if (acc > 0) {
				Brush.centered(g, font, Component.literal(Integer.toString(acc)), px, py + 6, SAMAR, 1.0F, false);
			}
		}
		baris(g, font, t(baca(d, 5) > 0 ? "hud.unsur.tahan" : "hud.unsur", pilar + 1, baca(d, 11)), x0, y + 56, x1, TINTA);
	}

	private static void pips(GuiGraphicsExtractor g, int x, int y, int n, int isi, int warna) {
		for (int i = 0; i < n; i++) {
			Brush.disc(g, x + 4 + i * 8, y + 2.5F, 3.0F, i <= isi ? warna : 0x60FFFFFF);
		}
	}

	private static MutableComponent t(String kunci, Object... args) {
		return Component.translatable("kultivator.gabung.ui." + kunci, args);
	}
}
