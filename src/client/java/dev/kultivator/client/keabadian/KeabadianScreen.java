package dev.kultivator.client.keabadian;

import dev.kultivator.client.Keys;
import dev.kultivator.client.dunia.DuniaClient;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * The Keabadian screen (DESIGN 26.10, {@code OpenScreen.ABADI = 8}, {@code /kultivasi abadi [tab]}, the {@code abadi} key): the
 * same jade scroll as the Dunia screen (at most 320 x 240), with six jade tablets on its left edge, Alam · Purba · Bencana ·
 * Batin · Ciptaan · Lelang, each at most {@link #MAX_TABLET_H} tall (the {@code DuniaScreen} formula). The pages belong to
 * P9-langit ({@code AlamAbadiPage}, {@code LelangPage}), P9-purba ({@code PurbaPage}, {@code BencanaPage}) and P9-batin
 * ({@code BatinPage}, {@code CiptaanPage}). On opening it asks the three modules for their data ("sync" of actions 42-44).
 * Behind the scroll: a pale immortal sky with floating islands instead of the Dunia mountains. Hub section 6 (DESIGN 33.3);
 * the header's "?" seal opens Panduan › Ensiklopedia on the tablet's topic ({@link #helpTopic}).
 */
public class KeabadianScreen extends DuniaScreen {

	/** Six tablets: {@code max(24, min(36, (paper height − 20 − gaps) / 6))} (DESIGN 26.1). */
	private static final int MAX_TABLET_H = 36;

	private final Eased backdrop = new Eased(6.0F);
	private long lastBackdrop = Util.getMillis();

	/** Opens on {@code tab}: 0 Alam, 1 Purba, 2 Bencana, 3 Batin, 4 Ciptaan, 5 Lelang ({@code Payloads.OpenScreen.ABADI_*}). */
	public KeabadianScreen(int tab) {
		super(Component.translatable("kultivator.spine9.abadi.title"), tab, screen -> List.of(
				new dev.kultivator.client.abadi.AlamAbadiPage(screen),
				new dev.kultivator.client.purba.PurbaPage(screen),
				new dev.kultivator.client.purba.BencanaPage(screen),
				new dev.kultivator.client.batin.BatinPage(screen),
				new dev.kultivator.client.batin.CiptaanPage(screen),
				new dev.kultivator.client.abadi.LelangPage(screen)));
	}

	@Override
	protected int maxTabletHeight() {
		return MAX_TABLET_H;
	}

	@Override
	protected int channelOf(int index) {
		return switch (index) {
			case 0, 5 -> dev.kultivator.network.Payloads.DuniaSync.CH_ABADI;
			case 1, 2 -> dev.kultivator.network.Payloads.DuniaSync.CH_PURBA;
			case 3, 4 -> dev.kultivator.network.Payloads.DuniaSync.CH_BATIN;
			default -> -1;
		};
	}

	/** The Ensiklopedia topic of each tablet (the header's "?" seal, DESIGN 33 C33.P1). */
	@Override
	protected String helpTopic(int index) {
		return switch (index) {
			case 0 -> "alam_abadi";
			case 1 -> "binatang_purba";
			case 2 -> "bencana_bintang";
			case 3 -> "dunia_batin";
			case 4 -> "jurus_ciptaan";
			case 5 -> "lelang";
			default -> "jalan_abadi";
		};
	}

	@Override
	protected void requestData() {
		DuniaClient.requestAbadi();
	}

	@Override
	protected boolean isCloseKey(KeyEvent event) {
		return Keys.ABADI.matches(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float fade = backdrop.to(1.0F, Math.min(0.1F, (now - lastBackdrop) / 1000.0F), 0.0F);
		lastBackdrop = now;
		// A pale dawn sky over a sea of clouds, with a few islands floating in it: the Immortal Realm above the world.
		g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xB8 * fade), 0x2A3A5C), Palette.argb(Math.round(0xD0 * fade), 0x6A7FA8));
		int step = Math.max(2, width / 160);
		int cloud = Palette.argb(Math.round(0x60 * fade), 0xE8F0FF);
		for (int x = 0; x < width; x += step) {
			double wave = Math.sin(x * 0.021D + 0.7D) * 5.0D + Math.sin(x * 0.063D) * 2.0D;
			int top = (int) Math.round(height * 0.16D + wave);
			g.fill(x, height - top, x + step, height, cloud);
		}
		int island = Palette.argb(Math.round(0x80 * fade), Palette.NIGHT);
		float[][] islands = {{0.12F, 0.30F, 34.0F}, {0.83F, 0.24F, 26.0F}, {0.70F, 0.62F, 18.0F}};
		for (float[] each : islands) {
			int cx = Math.round(width * each[0]);
			int cy = Math.round(height * each[1]);
			int half = Math.round(each[2]);
			for (int dx = -half; dx <= half; dx += step) {
				double t = 1.0D - Math.abs(dx) / (double) half;
				int depth = (int) Math.round(Math.sqrt(Math.max(0.0D, t)) * each[2] * 0.6D);
				g.fill(cx + dx, cy, cx + dx + step, cy + depth, island);
			}
			g.fill(cx - half, cy - 2, cx + half, cy, Palette.argb(Math.round(0x90 * fade), 0x9EE8B8));
		}
		g.nextStratum();
		Brush.halo(g, width * 0.5F, height * 0.12F, 10.0F, 10.0F, 0xFFF4D8, 0.35F * fade);
		Brush.disc(g, width * 0.5F, height * 0.12F, 7.0F, Palette.argb(Math.round(0x90 * fade), 0xFFF4D8));
		minecraft.gui.hud.extractDeferredSubtitles();
	}
}
