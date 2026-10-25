package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.kota.KotaNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The three screens of module K, all the jade scroll of the Dunia screen ({@link DuniaScreen}, at most 320 × 240):
 * the town screen (Toko · Lelang · Kedai Teh · Pasar Gelap · Papan Misi), the Immortal City screen (Pasar Abadi · Kuliah
 * Dao · Kebun Persik · Titah Utusan) and the wanderer meeting. Their pages read {@link KotaClient#data()} and refresh
 * whenever new data arrived; the backdrops are drawn fills (a lantern-lit street, a sea of clouds, a mountain road).
 * Not pause screens; Escape closes them.
 */
final class KotaScreens {

	/** A screen of this module (the receiver refreshes or retabs it). */
	interface Holder {
		int npcId();

		void showTab(int tab);
	}

	private static List<DuniaPage> pending = List.of();

	static Screen create(int kind, int tab, int npcId) {
		return switch (kind) {
			case KotaNet.SCREEN_ABADI -> new AbadiScreen(Math.max(0, tab - KotaNet.TAB_PASAR_ABADI), npcId);
			case KotaNet.SCREEN_PERTEMUAN -> new MeetingScreen(npcId);
			default -> new TownScreen(Math.max(0, Math.min(4, tab)), npcId);
		};
	}

	/** Shared base: keeps its own page list and refreshes the shown page on new data. */
	abstract static class Base extends DuniaScreen implements Holder {
		protected final List<DuniaPage> own;
		private final int npcId;
		private long seen = -1L;
		protected final Eased fade = new Eased(6.0F);
		protected long lastFrame = Util.getMillis();

		Base(Component title, int tab, int npcId, java.util.function.Function<DuniaScreen, List<DuniaPage>> pages) {
			super(title, tab, screen -> {
				List<DuniaPage> list = pages.apply(screen);
				pending = list;
				return list;
			});
			this.own = new ArrayList<>(pending);
			pending = List.of();
			this.npcId = npcId;
		}

		@Override
		public int npcId() {
			return npcId;
		}

		@Override
		public void showTab(int tab) {
			int index = this instanceof AbadiScreen ? tab - KotaNet.TAB_PASAR_ABADI : tab;
			if (index >= 0 && index < own.size()) {
				super.showTab(index);
			}
		}

		@Override
		protected void requestData() {
			// the server sent the data with the screen
		}

		@Override
		protected int channelOf(int index) {
			return -1;
		}

		@Override
		protected boolean isCloseKey(KeyEvent event) {
			return false;
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			long version = KotaClient.version();
			if (version != seen) {
				seen = version;
				int tab = tab();
				if (tab >= 0 && tab < own.size()) {
					own.get(tab).onData();
				}
			}
			super.extractRenderState(g, mouseX, mouseY, a);
		}

		protected float backdropFade() {
			long now = Util.getMillis();
			float f = fade.to(1.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F), 0.0F);
			lastFrame = now;
			return f;
		}
	}

	// ================================================================== the town

	static final class TownScreen extends Base {

		TownScreen(int tab, int npcId) {
			super(Component.translatable("kultivator.kota.layar.judul"), tab, npcId, screen -> List.of(
					new TownPages.Toko(screen), new TownPages.Lelang(screen), new TownPages.Teh(screen), new TownPages.Gelap(screen),
					new TownPages.Misi(screen)));
		}

		@Override
		protected String helpTopic(int index) {
			return "kota";
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float fade = backdropFade();
			// dusk over a walled town: a warm sky, the roofline with curled eaves and rows of red lanterns
			g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xC8 * fade), 0x1A1430), Palette.argb(Math.round(0xE0 * fade), 0x3A1E20));
			int step = Math.max(2, width / 160);
			int roof = Palette.argb(Math.round(0xB0 * fade), 0x120C10);
			for (int x = 0; x < width; x += step) {
				double block = (x * 0.012D) % 1.0D;
				int peak = (int) Math.round(height * 0.18D + (block < 0.5D ? block : 1.0D - block) * 26.0D + Math.sin(x * 0.05D) * 3.0D);
				g.fill(x, height - peak, x + step, height, roof);
			}
			g.nextStratum();
			long t = Util.getMillis();
			for (int i = 0; i < 9; i++) {
				float lx = width * (0.08F + i * 0.105F);
				float ly = height * 0.74F + (float) Math.sin(t / 900.0D + i) * 1.5F;
				Brush.halo(g, lx, ly, 3.0F, 6.0F, 0xFF6A3A, 0.35F * fade);
				Brush.disc(g, lx, ly, 2.2F, Palette.argb(Math.round(0xE0 * fade), 0xE8402A));
			}
			Brush.halo(g, width * 0.82F, height * 0.16F, 9.0F, 9.0F, 0xFFE0B0, 0.3F * fade);
			Brush.disc(g, width * 0.82F, height * 0.16F, 7.0F, Palette.argb(Math.round(0x90 * fade), 0xFFE8C8));
			minecraft.gui.hud.extractDeferredSubtitles();
		}
	}

	// ================================================================== the Immortal City

	static final class AbadiScreen extends Base {

		AbadiScreen(int tab, int npcId) {
			super(Component.translatable("kultivator.kota.layar.judul_abadi"), Math.max(0, Math.min(3, tab)), npcId, screen -> List.of(
					new AbadiPages.Pasar(screen), new AbadiPages.Kuliah(screen), new AbadiPages.Persik(screen), new AbadiPages.Titah(screen)));
		}

		@Override
		protected String helpTopic(int index) {
			return "kota_abadi";
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float fade = backdropFade();
			g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xB8 * fade), 0x3A3060), Palette.argb(Math.round(0xD0 * fade), 0xC89AA8));
			int step = Math.max(2, width / 160);
			int cloud = Palette.argb(Math.round(0x70 * fade), 0xFFF4F0);
			for (int x = 0; x < width; x += step) {
				double wave = Math.sin(x * 0.019D + 0.4D) * 6.0D + Math.sin(x * 0.07D) * 2.0D;
				int top = (int) Math.round(height * 0.2D + wave);
				g.fill(x, height - top, x + step, height, cloud);
			}
			g.nextStratum();
			// the floating district: a terrace with a pagoda silhouette
			int island = Palette.argb(Math.round(0x90 * fade), 0x2A2238);
			int cx = width / 5;
			int cy = Math.round(height * 0.32F);
			for (int dx = -40; dx <= 40; dx += step) {
				double t = 1.0D - Math.abs(dx) / 40.0D;
				int depth = (int) Math.round(Math.sqrt(Math.max(0.0D, t)) * 22.0D);
				g.fill(cx + dx, cy, cx + dx + step, cy + depth, island);
			}
			for (int tier = 0; tier < 4; tier++) {
				int half = 12 - tier * 3;
				g.fill(cx - half, cy - 8 - tier * 7, cx + half, cy - 6 - tier * 7, island);
				g.fill(cx - half + 3, cy - 6 - tier * 7, cx + half - 3, cy - tier * 7, island);
			}
			Brush.halo(g, width * 0.78F, height * 0.14F, 10.0F, 10.0F, 0xFFF4D8, 0.4F * fade);
			Brush.disc(g, width * 0.78F, height * 0.14F, 7.0F, Palette.argb(Math.round(0x90 * fade), 0xFFF4D8));
			minecraft.gui.hud.extractDeferredSubtitles();
		}
	}

	// ================================================================== the meeting

	static final class MeetingScreen extends Base {

		MeetingScreen(int npcId) {
			super(Component.translatable("kultivator.kota.layar.judul_pertemuan"), 0, npcId, screen -> List.of(new MeetingPage(screen)));
		}

		@Override
		protected String helpTopic(int index) {
			return "pengembara";
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			float fade = backdropFade();
			g.fillGradient(0, 0, width, height, Palette.argb(Math.round(0xC0 * fade), Palette.NIGHT), Palette.argb(Math.round(0xD8 * fade), 0x2A3A44));
			int step = Math.max(2, width / 160);
			int far = Palette.argb(Math.round(0x70 * fade), Palette.MOUNTAIN);
			for (int x = 0; x < width; x += step) {
				double ridge = Math.sin(x * 0.013D + 1.1D) * 18.0D + Math.sin(x * 0.041D) * 5.0D;
				int peak = (int) Math.round(height * 0.24D + ridge);
				g.fill(x, height - peak, x + step, height, far);
			}
			g.nextStratum();
			// a lone road winding into the mountains
			int road = Palette.argb(Math.round(0x50 * fade), 0xC8B890);
			for (int y = 0; y < height / 5; y += 2) {
				float t = y / (height / 5.0F);
				int cx = Math.round(width * 0.5F + (float) Math.sin(t * 5.0F) * width * 0.08F * (1.0F - t));
				int half = Math.round(18.0F * (1.0F - t) + 1.0F);
				g.fill(cx - half, height - y - 2, cx + half, height - y, road);
			}
			minecraft.gui.hud.extractDeferredSubtitles();
		}
	}

	private KotaScreens() {
	}
}
