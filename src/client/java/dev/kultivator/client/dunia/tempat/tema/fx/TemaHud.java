package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The HUD part of the themed-town effects (DESIGN 38.T.9.4), plain fills and text (no post chain, unaffected by shader
 * packs), hidden with F1 and while a film plays:
 * <ul>
 * <li>the sandstorm tint {@code #C8A060} with a denser vignette and, outdoors, gusting grain lines blowing across the
 * screen in the wind's sense (on Minimal this overlay is the whole storm); thin under a roof;</li>
 * <li>a faint white veil in the mountain mist;</li>
 * <li>the festival banner (four seconds, gold rules, title and subtitle from {@code kultivator.tempat_tema_fx.acara.*});</li>
 * <li>small captions: the player's own lantern wish (only he sees it), the commissioner of the niche-12 mural he stands
 * before, and the Buddha light around his shadow.</li>
 * </ul>
 */
final class TemaHud implements HudElement {

	private static final String K = "kultivator.tempat_tema_fx.";
	private static final int SPANDUK_TICK = 90;
	private static final int HARAPAN_TICK = 120;

	private static @Nullable String spanduk;
	private static long spandukMulai;
	private static @Nullable String harapan;
	private static long harapanMulai;

	/** Shows the banner of festival {@code acara}. */
	static void spanduk(String acara) {
		Minecraft minecraft = Minecraft.getInstance();
		spanduk = acara;
		spandukMulai = minecraft.level == null ? 0L : minecraft.level.getGameTime();
	}

	/** Shows the player's own lantern wish. */
	static void harapan(String teks) {
		Minecraft minecraft = Minecraft.getInstance();
		harapan = teks;
		harapanMulai = minecraft.level == null ? 0L : minecraft.level.getGameTime();
	}

	static void bersihkan() {
		spanduk = null;
		harapan = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.hud.isHidden() || FilmDirector.playing()) {
			return;
		}
		float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth();
		int h = g.guiHeight();
		long now = minecraft.level.getGameTime();
		badai(g, minecraft, w, h, pt, now);
		kabut(g, w, h, pt);
		teks(g, minecraft, w, h, now, pt);
	}

	private static void badai(GuiGraphicsExtractor g, Minecraft minecraft, int w, int h, float pt, long now) {
		float k = BadaiPasirFx.kuat(pt);
		if (k <= 0.01F) {
			return;
		}
		boolean minimal = EpicBudget.tier() <= 0;
		float roof = BadaiPasirFx.bawahAtap() ? 0.35F : 1.0F;
		float base = (minimal ? 0.42F : 0.22F) * k * roof;
		int rgb = BadaiPasirFx.WARNA;
		g.fill(0, 0, w, h, Palette.argb(Math.round(base * 255.0F), rgb));
		// vignette: the edges choke first
		int band = Math.max(16, Math.min(w, h) / 3);
		int edge = Palette.argb(Math.round(Math.min(0.5F, base * 1.3F) * 255.0F), 0xA87840);
		int clear = Palette.argb(0, 0xA87840);
		g.fillGradient(0, 0, w, band, edge, clear);
		g.fillGradient(0, h - band, w, h, clear, edge);
		for (int i = 0; i < band; i += 3) {
			float f = 1.0F - i / (float) band;
			int c = Palette.argb(Math.round(Math.min(0.5F, base * 1.3F) * 255.0F * f * f), 0xA87840);
			g.fill(i, 0, i + 3, h, c);
			g.fill(w - i - 3, 0, w - i, h, c);
		}
		if (BadaiPasirFx.bawahAtap() || EffectQuality.reduceMotion()) {
			return;
		}
		// gusting grain: the wind's screen sense from the camera yaw (left or right across the view)
		float yaw = minecraft.player.getViewYRot(pt) * Mth.DEG_TO_RAD;
		float wind = BadaiPasirFx.anginX(Mth.cos(yaw), Mth.sin(yaw));
		int lines = minimal ? 22 : 12;
		float t = now + pt;
		for (int i = 0; i < lines; i++) {
			float speed = 9.0F + Bingkai.hash(i, 3) * 10.0F;
			float y = Bingkai.hash(i, 7) * h;
			float len = 18.0F + Bingkai.hash(i, 11) * 46.0F;
			float x = Mth.positiveModulo(Bingkai.hash(i, 13) * (w + len) + t * speed * (wind >= 0.0F ? 1.0F : -1.0F), w + len) - len;
			y += Mth.sin(t * 0.07F + i) * 3.0F;
			int a = Math.round(Mth.clamp((0.18F + 0.25F * Bingkai.hash(i, 17)) * k * Math.abs(wind) * 1.4F + 0.05F * k, 0.0F, 0.5F) * 255.0F);
			g.fill(Math.round(x), Math.round(y), Math.round(x + len), Math.round(y) + 1, Palette.argb(a, 0xF4E0B0));
		}
	}

	private static void kabut(GuiGraphicsExtractor g, int w, int h, float pt) {
		float k = KabutGunungFx.kuat(pt);
		if (k <= 0.01F) {
			return;
		}
		g.fill(0, 0, w, h, Palette.argb(Math.round(0.07F * k * 255.0F), 0xF2F6FA));
		int band = Math.max(16, h / 4);
		int edge = Palette.argb(Math.round(0.16F * k * 255.0F), 0xF2F6FA);
		int clear = Palette.argb(0, 0xF2F6FA);
		g.fillGradient(0, h - band, w, h, clear, edge);
		g.fillGradient(0, 0, w, band / 2, edge, clear);
	}

	private static void teks(GuiGraphicsExtractor g, Minecraft minecraft, int w, int h, long now, float pt) {
		if (spanduk != null) {
			float age = now - spandukMulai + pt;
			if (age > SPANDUK_TICK || age < 0.0F) {
				spanduk = null;
			} else {
				float a = Math.min(1.0F, age / 12.0F) * Math.min(1.0F, (SPANDUK_TICK - age) / 18.0F);
				String id = spanduk;
				String key = K + "acara." + id;
				Component judul = Language.getInstance().has(key) ? Component.translatable(key)
						: Component.translatable(K + "acara.umum", id.replace('_', ' '));
				Component sub = Language.getInstance().has(key + ".sub") ? Component.translatable(key + ".sub") : null;
				float y = h * 0.2F;
				float half = Math.min(w * 0.3F, 150.0F) * (0.6F + 0.4F * a);
				int gold = Palette.argb(Math.round(a * 200.0F), 0xE8C070);
				Brush.rect(g, w / 2.0F - half, y - 6.0F, w / 2.0F + half, y - 5.0F, gold);
				Brush.centered(g, minecraft.font, judul, w / 2.0F, y, Palette.argb(Math.round(a * 255.0F), 0xFFF0D0), 1.3F, true);
				float y2 = y + 16.0F;
				if (sub != null) {
					Brush.centered(g, minecraft.font, sub, w / 2.0F, y2, Palette.argb(Math.round(a * 220.0F), 0xD8C8A8), 0.8F, true);
					y2 += 11.0F;
				}
				Brush.rect(g, w / 2.0F - half, y2, w / 2.0F + half, y2 + 1.0F, gold);
			}
		}
		float cy = h - 86.0F;
		if (harapan != null) {
			float age = now - harapanMulai + pt;
			if (age > HARAPAN_TICK || age < 0.0F) {
				harapan = null;
			} else {
				float a = Math.min(1.0F, age / 10.0F) * Math.min(1.0F, (HARAPAN_TICK - age) / 20.0F);
				Brush.centered(g, minecraft.font, Component.translatable(K + "hud.harapan", harapan), w / 2.0F, cy,
						Palette.argb(Math.round(a * 235.0F), 0xFFD890), 0.85F, true);
				cy -= 11.0F;
			}
		}
		String mural = MuralBillboard.caption();
		if (mural != null) {
			Brush.centered(g, minecraft.font, Component.translatable(K + "hud.mural", mural), w / 2.0F, cy, Palette.argb(0xC8, 0xE8C890), 0.8F, true);
			cy -= 11.0F;
		}
		if (CahayaBudhaFx.dilihat()) {
			Brush.centered(g, minecraft.font, Component.translatable(K + "hud.budha"), w / 2.0F, cy, Palette.argb(0xB4, 0xF0E8FF), 0.8F, true);
		}
	}
}
