package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.ui.KotaLembar.HudIrama;
import dev.kultivator.dunia.kota42.ui.KotaLembar.HudKejar;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The two HUDs of 42U-1 (DESIGN 42.11.14; not screens), one painter each, on the HUD style of 43.9 (jade slip, text 1.0):
 * <ul>
 * <li>{@link Kejar} {@code kota_kejar}: the pickpocket chase. An arrow on an ellipse around the crosshair turns toward the
 * thief's entity (from the player's view yaw), a ring around it burns down the 30 s, the distance in blocks sits beside it,
 * and within {@code HudKejar.DEKAT_BLOK} the arrow becomes a "Pukul!" prompt. The thief out of loading range leaves the
 * ring and a "?" so the player still sees how much time is left;</li>
 * <li>{@link Irama} {@code kota_irama}: the 5-beat ring of the night watch, teaching and the shadow-theatre gong. Beats sit
 * on a ring, those the pattern wants are gold, the sweeping hand is placed from the game time against the round's start
 * tick, beats already struck on time are ticked green, and a misses counter runs below. The right mouse button (use) strikes:
 * the client sends the nearest beat and how many ticks early or late ({@code HudIrama} C2S layout).</li>
 * </ul>
 * Both skip drawing while a screen is open, in Mode Rekam, with F1 and with the HUD off. Their Pengaturan › HUD switch ids are
 * {@code kota_kejar} and {@code kota_irama} (element ids of the same names).
 */
public final class KotaHud {

	private static boolean kunciTerakhir;

	private static boolean sembunyi(Minecraft mc) {
		return mc.player == null || mc.level == null || mc.gui.hud.isHidden() || !EffectQuality.hudVisible() || EpicDirector.recordHide();
	}

	/** The chase HUD. */
	public static final class Kejar implements HudElement {

		private @org.jetbrains.annotations.Nullable KotaPayloads.Kejar terakhir;
		private long terakhirMs;

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
			Minecraft mc = Minecraft.getInstance();
			KotaPayloads.Kejar k = KotaHidupClient.kejar();
			if (k == null || sembunyi(mc)) {
				return;
			}
			try {
				gambar(g, mc, k);
			} catch (RuntimeException ignored) {
				// a malformed packet must never break the HUD
			}
		}

		private void gambar(GuiGraphicsExtractor g, Minecraft mc, KotaPayloads.Kejar k) {
			int w = g.guiWidth();
			int h = g.guiHeight();
			Entity copet = mc.level.getEntity(k.copet());
			if (k != terakhir) {
				terakhir = k;
				terakhirMs = System.currentTimeMillis();
			}
			float sisaTick = Math.max(0.0F, k.sisa() - (System.currentTimeMillis() - terakhirMs) / 50.0F);
			float frac = Mth.clamp(sisaTick / HudKejar.DURASI_TICK, 0.0F, 1.0F);
			float cx = w / 2.0F;
			float cy = h / 2.0F;
			float rx = Math.min(w * 0.30F, 150.0F);
			float ry = Math.min(h * 0.28F, 90.0F);
			double jarak = copet == null ? -1.0D : copet.distanceTo(mc.player);
			float sudut = 0.0F;
			boolean dekat = copet != null && jarak <= HudKejar.DEKAT_BLOK;
			if (copet != null) {
				double dx = copet.getX() - mc.player.getX();
				double dz = copet.getZ() - mc.player.getZ();
				float arah = (float) Math.toDegrees(Math.atan2(-dx, dz));
				sudut = (float) Math.toRadians(Mth.wrapDegrees(arah - mc.player.getYRot()));
			}
			float px = cx + (float) Math.sin(sudut) * rx;
			float py = cy - (float) Math.cos(sudut) * ry;
			int warna = frac > 0.33F ? 0xFFF0C040 : 0xFFE0503A;
			// the countdown ring (full at the start, clockwise from the top), then the slip with the arrow
			Brush.disc(g, px, py, 17.0F, Palette.argb(0x70, 0x000000));
			Brush.ring(g, px, py, 14.0F, 17.0F, Palette.argb(0x60, 0xFFFFFF));
			Brush.arc(g, px, py, 14.0F, 17.0F, -(float) Math.PI / 2.0F, -(float) Math.PI / 2.0F + frac * (float) (Math.PI * 2.0D), warna);
			if (copet != null && !dekat) {
				// an arrow pointing away from the screen centre
				float ax = (float) Math.sin(sudut);
				float ay = -(float) Math.cos(sudut);
				Brush.line(g, px - ax * 7.0F, py - ay * 7.0F, px + ax * 8.0F, py + ay * 8.0F, 2.4F, 0xFFFFF0C0);
				Brush.line(g, px + ax * 8.0F, py + ay * 8.0F, px + ax * 2.0F - ay * 5.0F, py + ay * 2.0F + ax * 5.0F, 2.0F, 0xFFFFF0C0);
				Brush.line(g, px + ax * 8.0F, py + ay * 8.0F, px + ax * 2.0F + ay * 5.0F, py + ay * 2.0F - ax * 5.0F, 2.0F, 0xFFFFF0C0);
			} else {
				Brush.centered(g, mc.font, Component.literal(dekat ? "!" : "?"), px, py - 4.0F, dekat ? 0xFFFFF0C0 : 0xFFD0D0D0, 1.0F, true);
			}
			// the text: "Kejar!  12 m  18 s"
			Component baris = dekat ? KotaGambar.t("hud.kejar_pukul") : jarak < 0 ? KotaGambar.t("hud.kejar_jauh", Math.round(sisaTick / 20.0F))
					: KotaGambar.t("hud.kejar", Math.round(jarak), Math.round(sisaTick / 20.0F));
			int tw = mc.font.width(baris);
			int tx = Math.round(Mth.clamp(px - tw / 2.0F, 6.0F, w - tw - 6.0F));
			int ty = Math.round(Mth.clamp(py + 21.0F, 6.0F, h - 50.0F));
			Kabar.slip(g, tx - 4, ty - 3, tx + tw + 4, ty + 11, dekat ? Palette.SEAL : 0);
			Brush.text(g, mc.font, baris, tx, ty, Palette.INK, 1.0F, false);
		}
	}

	/** The rhythm ring. */
	public static final class Irama implements HudElement {

		private int ketukanTerakhirTick = -100;

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
			Minecraft mc = Minecraft.getInstance();
			KotaPayloads.Irama irama = KotaHidupClient.irama();
			if (irama == null || sembunyi(mc) || HudIrama.baca(irama.data(), 0) == HudIrama.J_KOSONG) {
				kunciTerakhir = false;
				return;
			}
			try {
				gambar(g, mc, irama.data(), deltaTracker.getGameTimeDeltaPartialTick(false));
			} catch (RuntimeException ignored) {
				// a malformed packet must never break the HUD
			}
		}

		private void gambar(GuiGraphicsExtractor g, Minecraft mc, byte[] d, float sebagian) {
			int jenis = HudIrama.baca(d, 0);
			int pola = HudIrama.baca(d, 2);
			int tpb = Math.max(4, HudIrama.baca(d, 3));
			int putaran = HudIrama.baca(d, 4);
			int total = HudIrama.baca(d, 5);
			int kena = HudIrama.baca(d, 6);
			int salah = HudIrama.baca(d, 7);
			long sekarang = mc.level.getGameTime();
			long mulai = sekarang - ((sekarang - HudIrama.baca(d, 8)) & 0xFF);
			float t = (sekarang - mulai) + sebagian;
			float lingkar = tpb * HudIrama.KETUKAN;
			float fase = Math.min(1.0F, t / lingkar);
			int w = g.guiWidth();
			int h = g.guiHeight();
			float cx = w / 2.0F;
			float cy = h * 0.70F;
			float r = 28.0F;
			Brush.disc(g, cx, cy, r + 14.0F, Palette.argb(0x78, 0x0A1322));
			Brush.ring(g, cx, cy, r + 12.0F, r + 14.0F, Palette.argb(0xC0, Palette.GOLD_DARK));
			Brush.ring(g, cx, cy, r - 1.0F, r + 1.0F, Palette.argb(0x60, 0xFFFFFF));
			int kini = Math.min(HudIrama.KETUKAN - 1, (int) (t / tpb));
			for (int i = 0; i < HudIrama.KETUKAN; i++) {
				float a = -(float) Math.PI / 2.0F + i * (float) (Math.PI * 2.0D) / HudIrama.KETUKAN;
				float bx = cx + (float) Math.cos(a) * r;
				float by = cy + (float) Math.sin(a) * r;
				boolean mau = (pola & (1 << i)) != 0;
				boolean sudah = (kena & (1 << i)) != 0;
				if (mau) {
					Brush.disc(g, bx, by, i == kini ? 8.0F : 6.0F, sudah ? 0xFF3FA86A : 0xFFF0C040);
					Brush.ring(g, bx, by, 6.0F, i == kini ? 9.0F : 7.0F, Palette.argb(0xC0, 0xFFF0C0));
				} else {
					Brush.disc(g, bx, by, 2.5F, Palette.argb(0x90, 0xB0B0B0));
				}
			}
			// the sweeping hand
			float ha = -(float) Math.PI / 2.0F + fase * (float) (Math.PI * 2.0D);
			Brush.line(g, cx, cy, cx + (float) Math.cos(ha) * (r - 4.0F), cy + (float) Math.sin(ha) * (r - 4.0F), 1.6F, 0xFFFF5B3A);
			Brush.disc(g, cx, cy, 2.5F, 0xFFFF5B3A);
			Component nama = KotaGambar.t("hud.irama." + jenis);
			Brush.centered(g, mc.font, nama, cx, cy + r + 18.0F, Palette.argb(0xFF, 0xF4E8D0), 1.0F, true);
			Component info = KotaGambar.t("hud.irama_info", putaran, Math.max(1, total), salah);
			Brush.centered(g, mc.font, info, cx, cy + r + 29.0F, salah > 0 ? 0xFFFFA090 : 0xFFD8D8C8, 1.0F, true);
			// strike: the rising edge of the use key (the key is not consumed: striking a gong block still works)
			boolean tekan = mc.options.keyUse.isDown();
			if (tekan && !kunciTerakhir && sekarang - ketukanTerakhirTick >= 3 && mc.gui.screen() == null) {
				ketukanTerakhirTick = (int) sekarang;
				int terdekat = 0;
				float terbaik = Float.MAX_VALUE;
				for (int i = 0; i < HudIrama.KETUKAN; i++) {
					float jarak = Math.abs(t - i * tpb);
					if (((pola & (1 << i)) != 0 || terbaik == Float.MAX_VALUE) && jarak < terbaik) {
						terbaik = jarak;
						terdekat = i;
					}
				}
				int offset = Math.round(t - terdekat * tpb);
				KotaHidupClient.kirimIrama(new KotaPayloads.Irama(HudIrama.data(jenis, terdekat, Mth.clamp(offset, -127, 127) + 128)));
			}
			kunciTerakhir = tekan;
		}
	}

	private KotaHud() {
	}

}
