package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.menara.PenjagaLantaiEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The "Menara Seratus" card of the hub's Dunia section (DESIGN 34.6 T, at most one card for module R): an ink painting
 * of the nine-tier pagoda's hundred floors as ten bands, lit up to the player's best floor (each band named by its
 * guardian), the open gates, Luka Menara, the floor pattern of a band, the week's ranking and the best of all time, and
 * the module's other records (ancestral ruins cleared, Pahala from the prisons, the seals broken now). The data comes
 * from the server on opening ({@code RNet.Action "menara_info"} → {@code op: "info"}); until then it says it is
 * loading. Esc goes back to the hub.
 */
public class MenaraScreen extends Screen {

	private static @Nullable MenaraScreen open;
	private @Nullable CompoundTag data;
	private final @Nullable Screen back;
	private final long openedAt = Util.getMillis();
	private int x0;
	private int y0;
	private int x1;
	private int y1;

	public MenaraScreen(@Nullable Screen back) {
		super(Component.translatable("kultivator.reruntuhan.menara.ui.title"));
		this.back = back;
	}

	/** Opens the card and asks the server for the numbers. */
	static void request() {
		ClientPlayNetworking.send(new RNet.Action("menara_info", 0, 0));
	}

	/** The server's answer: fills the open card (ignored when none is open). */
	static void onInfo(CompoundTag tag) {
		MenaraScreen screen = open;
		if (screen != null) {
			screen.data = tag.copy();
		}
	}

	@Override
	protected void init() {
		open = this;
		int w = Math.min(400, width - 16);
		int h = Math.min(262, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
	}

	@Override
	public void removed() {
		if (open == this) {
			open = null;
		}
		super.removed();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.NIGHT, Palette.MOUNTAIN);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x60000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		g.fillGradient(x0 - 4, y0 - 3, x1 + 4, y0 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		g.fillGradient(x0 - 4, y1 - 3, x1 + 4, y1 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		Brush.frame(g, x0, y0, x1, y1, Palette.JADE_DARK);
		float cx = (x0 + x1) / 2.0F;
		Brush.fitCentered(g, font, Brush.bold(title), cx, y0 + 8.0F, x1 - x0 - 40.0F, Palette.INK, 1.15F, 0.6F, false);
		Brush.rect(g, x0 + 16.0F, y0 + 20.0F, x1 - 16.0F, y0 + 21.0F, Palette.argb(0x90, Palette.JADE_DARK));
		CompoundTag d = data;
		if (d == null) {
			int dots = (int) ((now - openedAt) / 300L % 4L);
			Brush.fitCentered(g, font, Component.translatable("kultivator.reruntuhan.menara.ui.loading").append(".".repeat(dots)), cx, (y0 + y1) / 2.0F,
					200.0F, Palette.INK_SOFT, 0.9F, 0.6F, false);
			return;
		}
		int best = d.getIntOr("best", 0);
		int unlocked = d.getIntOr("unlocked", 1);
		pagoda(g, best, unlocked, now);
		float tx = x0 + 168.0F;
		float room = x1 - tx - 14.0F;
		float y = y0 + 28.0F;
		float s = 0.72F;
		y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.best", best), tx, y, room, Palette.INK, 0.85F);
		// the progress to the hundredth floor
		Brush.rect(g, tx, y, tx + room, y + 4.0F, Palette.argb(0x50, Palette.INK_FAINT));
		Brush.rect(g, tx, y, tx + room * best / 100.0F, y + 4.0F, Palette.GOLD);
		for (int b = 1; b < 10; b++) {
			Brush.rect(g, tx + room * b / 10.0F, y, tx + room * b / 10.0F + 0.6F, y + 4.0F, Palette.argb(0x80, Palette.PAPER_TOP));
		}
		y += 8.0F;
		y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.gates", unlocked, unlocked * 10 - 9), tx, y, room, Palette.INK_SOFT, s);
		int luka = d.getIntOr("luka", 0);
		if (luka > 0) {
			y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.luka", (luka + 59) / 60), tx, y, room, Palette.DANGER, s);
		}
		if (d.getBooleanOr("penakluk", false)) {
			y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.title_conqueror").withStyle(ChatFormatting.BOLD), tx, y, room, Palette.GOLD_DARK,
					s);
		}
		if (!d.getBooleanOr("tower", true)) {
			y += line(g, Component.translatable("kultivator.reruntuhan.menara.closed"), tx, y, room, Palette.LOCKED, s);
		}
		y += 3.0F;
		// the floor pattern of every band
		y += line(g, Brush.bold(Component.translatable("kultivator.reruntuhan.menara.ui.pattern")), tx, y, room, Palette.JADE_DARK, s);
		y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.pattern.text"), tx, y, room, Palette.INK_SOFT, 0.6F);
		y += 3.0F;
		// the ranking
		y += line(g, Brush.bold(Component.translatable("kultivator.reruntuhan.menara.ui.week")), tx, y, room, Palette.JADE_DARK, s);
		int rank = 0;
		for (Tag t : d.getListOrEmpty("weekly")) {
			if (t instanceof StringTag entry) {
				String value = entry.value();
				int bar = value.lastIndexOf('|');
				String name = bar < 0 ? value : value.substring(0, bar);
				String floor = bar < 0 ? "?" : value.substring(bar + 1);
				rank++;
				int color = rank == 1 ? Palette.GOLD_DARK : Palette.INK;
				y += line(g, Component.translatable("kultivator.reruntuhan.menara.stele.row", rank, name, floor), tx + 4.0F, y, room - 4.0F, color, 0.66F);
			}
		}
		if (rank == 0) {
			y += line(g, Component.translatable("kultivator.reruntuhan.menara.stele.empty"), tx + 4.0F, y, room - 4.0F, Palette.INK_FAINT, 0.66F);
		}
		String bestName = d.getStringOr("best_name", "");
		if (!bestName.isEmpty()) {
			y += line(g, Component.translatable("kultivator.reruntuhan.menara.stele.best", bestName, d.getIntOr("best_floor", 0)), tx, y, room,
					Palette.STORED, 0.66F);
		}
		y += 3.0F;
		// the rest of module R
		y += line(g, Brush.bold(Component.translatable("kultivator.reruntuhan.menara.ui.records")), tx, y, room, Palette.JADE_DARK, s);
		int ruins = d.getIntOr("ruins", 0);
		int ruinBest = Mth.clamp(d.getIntOr("ruin_best", 0), 0, 3);
		y += line(g, ruins == 0 ? Component.translatable("kultivator.reruntuhan.menara.ui.ruins_none")
				: Component.translatable("kultivator.reruntuhan.menara.ui.ruins", ruins, Component.translatable("kultivator.reruntuhan.gua.tier." + Math.max(1, ruinBest))),
				tx + 4.0F, y, room - 4.0F, Palette.INK, 0.66F);
		y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.pahala", d.getIntOr("pahala", 0)), tx + 4.0F, y, room - 4.0F, Palette.INK, 0.66F);
		int prisons = d.getIntOr("prisons", 0);
		int broken = d.getIntOr("prisons_broken", 0);
		y += line(g, Component.translatable("kultivator.reruntuhan.menara.ui.prisons", prisons, broken), tx + 4.0F, y, room - 4.0F,
				broken > 0 ? Palette.DANGER : Palette.INK, 0.66F);
		// the Sumber line at the foot
		Brush.wrap(g, font, Component.translatable("kultivator.reruntuhan.menara.source").withStyle(ChatFormatting.ITALIC), x0 + 14.0F, y1 - 22.0F,
				x1 - x0 - 28.0F, Palette.INK_FAINT, 0.55F, false);
	}

	private float line(GuiGraphicsExtractor g, Component text, float x, float y, float room, int color, float scale) {
		return Brush.wrap(g, font, text, x, y, room, color, scale, false) + 1.5F;
	}

	/** The ten bands as the tiers of a pagoda, the widest at the bottom; lit up to the best floor. */
	private void pagoda(GuiGraphicsExtractor g, int best, int unlocked, long now) {
		float left = x0 + 12.0F;
		float right = x0 + 160.0F;
		float mid = (left + right) / 2.0F;
		float top = y0 + 28.0F;
		float bottom = y1 - 30.0F;
		float tier = (bottom - top) / 10.0F;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 350.0D);
		// the spire
		Brush.rect(g, mid - 0.8F, top - 6.0F, mid + 0.8F, top + 2.0F, Palette.GOLD);
		Brush.disc(g, mid, top - 6.0F, 2.0F, Palette.GOLD_LIGHT);
		for (int b = 10; b >= 1; b--) {
			int i = 10 - b;
			float y = top + i * tier;
			float half = 18.0F + b * 5.2F;
			boolean cleared = best >= b * 10;
			boolean open = b <= unlocked;
			int rgb = LobbyState.bandRgb(b);
			int roof = cleared ? Palette.opaque(rgb) : open ? Palette.mix(Palette.opaque(rgb), Palette.INK_FAINT, 0.55F) : Palette.LOCKED;
			int wall = cleared ? Palette.ROD_LIGHT : open ? Palette.argb(0xC0, Palette.ROD_LIGHT) : Palette.LOCKED_PAPER;
			// the storey's wall, then its curled roof
			Brush.rect(g, mid - half * 0.62F, y + tier * 0.45F, mid + half * 0.62F, y + tier - 1.0F, wall);
			Brush.rect(g, mid - half, y + tier * 0.3F, mid + half, y + tier * 0.45F, roof);
			Brush.line(g, mid - half, y + tier * 0.3F, mid - half - 3.0F, y + tier * 0.12F, 1.2F, roof);
			Brush.line(g, mid + half, y + tier * 0.3F, mid + half + 3.0F, y + tier * 0.12F, 1.2F, roof);
			Brush.rect(g, mid - half * 0.7F, y + tier * 0.18F, mid + half * 0.7F, y + tier * 0.3F, Palette.mix(roof, 0xFF000000, 0.2F));
			if (b == unlocked && !cleared) {
				Brush.halo(g, mid, y + tier * 0.6F, half * 0.5F, 4.0F, rgb, 0.35F + 0.35F * pulse);
			}
			Component label = Component.translatable("kultivator.reruntuhan.menara.ui.band", LobbyState.NUMERALS[b],
					Component.translatable("entity.kultivator.penjaga_lantai." + (b == 10 ? "bayangan_diri" : PenjagaLantaiEntity.IDS[b - 1])));
			Brush.fitCentered(g, font, label, mid, y + tier * 0.5F, half * 1.2F, cleared ? 0xFF1B1008 : open ? Palette.INK : Palette.INK_FAINT, 0.55F,
					0.4F, false);
		}
		Brush.rect(g, left, bottom, right, bottom + 3.0F, Palette.ROD_DARK);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
