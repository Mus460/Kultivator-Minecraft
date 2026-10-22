package dev.kultivator.client.sekte.turnamen;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.turnamen.TurnamenPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Turnamen" card of the hub's Sekte section (DESIGN 36.2.3 "UI"): a paper scroll with the season board (every
 * disciple's Elo rating, wins and losses, the leader in gold), the live bracket while a tournament runs (rounds as
 * columns, the bout now fought pulsing, winners in jade) or else the next date and the all-time top of the old tournament,
 * the roll of champions, the inter-sect record and a waiting NPC sect's challenge with its two seals (Terima / Tolak).
 * The data comes from the server on opening ({@code Minta}) and again every 3 s while the card is open.
 */
public class TurnamenScreen extends Screen {

	private static @Nullable TurnamenScreen open;
	private @Nullable CompoundTag data;
	private final long openedAt = Util.getMillis();
	private long askedAt;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private float acceptX0;
	private float acceptX1;
	private float buttonY0;
	private float buttonY1;
	private float refuseX0;
	private float refuseX1;

	public TurnamenScreen() {
		super(Component.translatable("kultivator.sekte.turnamen.ui.judul"));
	}

	static void ask(int action) {
		ClientPlayNetworking.send(new TurnamenPayloads.Minta(action));
	}

	static void onData(CompoundTag tag) {
		TurnamenScreen screen = open;
		if (screen != null) {
			screen.data = tag.copy();
		}
	}

	@Override
	protected void init() {
		open = this;
		int w = Math.min(420, width - 16);
		int h = Math.min(266, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		askedAt = Util.getMillis();
		ask(TurnamenPayloads.Minta.ASK);
	}

	@Override
	public void removed() {
		if (open == this) {
			open = null;
		}
		super.removed();
	}

	@Override
	public void tick() {
		if (Util.getMillis() - askedAt > 3000L) {
			askedAt = Util.getMillis();
			ask(TurnamenPayloads.Minta.ASK);
		}
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
		CompoundTag d = data;
		int season = d == null ? 0 : d.getIntOr("season", 0);
		Component title = season > 0 ? Component.translatable("kultivator.sekte.turnamen.ui.judul_musim", season) : this.title;
		Brush.fitCentered(g, font, Brush.bold(title), cx, y0 + 8.0F, x1 - x0 - 40.0F, Palette.INK, 1.15F, 0.6F, false);
		// the lei tai seal in the corner
		Brush.disc(g, x1 - 22.0F, y0 + 14.0F, 9.0F, Palette.SEAL);
		Brush.centered(g, font, Component.literal("擂"), x1 - 22.0F, y0 + 10.0F, 0xFFFFF4E0, 1.0F, false);
		Brush.rect(g, x0 + 16.0F, y0 + 22.0F, x1 - 16.0F, y0 + 23.0F, Palette.argb(0x90, Palette.JADE_DARK));
		if (d == null) {
			int dots = (int) ((now - openedAt) / 300L % 4L);
			Brush.fitCentered(g, font, Component.translatable("kultivator.sekte.turnamen.ui.memuat").append(".".repeat(dots)), cx, (y0 + y1) / 2.0F,
					200.0F, Palette.INK_SOFT, 0.9F, 0.6F, false);
			return;
		}
		float split = x0 + (x1 - x0) * 0.40F;
		board(g, d, x0 + 12.0F, y0 + 28.0F, split - 6.0F);
		Brush.rect(g, split, y0 + 28.0F, split + 0.8F, y1 - 34.0F, Palette.argb(0x60, Palette.JADE_DARK));
		CompoundTag live = d.getCompoundOrEmpty("live");
		float y = y0 + 28.0F;
		if (live.getBooleanOr("running", false)) {
			y = bracket(g, live, split + 8.0F, y, x1 - 12.0F, now);
		} else {
			y = schedule(g, live, split + 8.0F, y, x1 - 12.0F);
		}
		y = roll(g, d, split + 8.0F, y + 4.0F, x1 - 12.0F);
		footer(g, d, mouseX, mouseY, now);
	}

	// ================================================================== parts

	private void board(GuiGraphicsExtractor g, CompoundTag d, float left, float top, float right) {
		float y = top;
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.turnamen.ui.papan")), left, y, right - left, Palette.JADE_DARK, 0.78F);
		ListTag board = d.getListOrEmpty("board");
		if (board.isEmpty()) {
			line(g, Component.translatable("kultivator.sekte.turnamen.papan.kosong"), left, y, right - left, Palette.INK_FAINT, 0.62F);
			return;
		}
		int best = 1;
		for (Tag tag : board) {
			if (tag instanceof CompoundTag entry) {
				best = Math.max(best, entry.getIntOr("e", 1000));
			}
		}
		int rank = 0;
		for (Tag tag : board) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			rank++;
			Component name = entry.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			int elo = entry.getIntOr("e", 1000);
			int color = rank == 1 ? Palette.GOLD_DARK : rank <= 3 ? Palette.INK : Palette.INK_SOFT;
			// a bar under the name, as long as the rating against the leader's
			float bar = (right - left - 18.0F) * Math.max(0.08F, Math.min(1.0F, (elo - 600) / (float) Math.max(1, best - 600)));
			Brush.rect(g, left + 14.0F, y + 7.5F, left + 14.0F + bar, y + 9.0F, Palette.argb(rank == 1 ? 0xB0 : 0x70, rank == 1 ? Palette.GOLD
					: Palette.JADE));
			Brush.text(g, font, Component.literal(rank + "."), left, y, color, 0.62F, false);
			Brush.fit(g, font, name, left + 14.0F, y, right - left - 64.0F, color, 0.62F, 0.45F, false);
			Brush.text(g, font, Component.translatable("kultivator.sekte.turnamen.ui.elo_baris", elo, entry.getIntOr("w", 0), entry.getIntOr("l", 0)),
					right - 50.0F, y, Palette.INK_SOFT, 0.55F, false);
			y += 11.0F;
		}
	}

	/** The live bracket: rounds as columns, the bout now pulsing, winners in jade. Returns the y below it. */
	private float bracket(GuiGraphicsExtractor g, CompoundTag live, float left, float top, float right, long now) {
		float y = top;
		String kind = live.getStringOr("kind", "biasa");
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.turnamen.ui.berjalan", Component.translatable("kultivator.sekte.turnamen.jenis." + kind))),
				left, y, right - left, Palette.SEAL_DARK, 0.75F);
		if (live.contains("now_a")) {
			Component a = live.read("now_a", ComponentSerialization.CODEC).orElse(Component.empty());
			Component b = live.read("now_b", ComponentSerialization.CODEC).orElse(Component.empty());
			y += line(g, Component.translatable("kultivator.sekte.turnamen.ui.sekarang", a, b), left, y, right - left, Palette.INK, 0.62F);
		}
		CompoundTag bracket = live.getCompoundOrEmpty("bracket");
		ListTag rounds = bracket.getListOrEmpty("rounds");
		int columns = Math.max(1, rounds.size());
		float colW = (right - left) / columns;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 220.0D);
		float tallest = y;
		for (int r = 0; r < rounds.size(); r++) {
			if (!(rounds.get(r) instanceof CompoundTag round)) {
				continue;
			}
			ListTag matches = round.getListOrEmpty("m");
			float cy = y + 2.0F;
			float step = Math.max(17.0F, 17.0F * (1 << r));
			float colX = left + r * colW;
			for (int m = 0; m < matches.size(); m++) {
				if (!(matches.get(m) instanceof CompoundTag match)) {
					continue;
				}
				float my = cy + m * step + (step - 17.0F) / 2.0F;
				boolean nowFought = match.getBooleanOr("now", false);
				int w = match.getIntOr("w", -1);
				if (nowFought) {
					Brush.rect(g, colX - 1.0F, my - 1.0F, colX + colW - 4.0F, my + 15.0F, Palette.argb((int) (0x30 + 0x50 * pulse), Palette.SEAL_GLOW));
				}
				Brush.rect(g, colX, my + 7.0F, colX + colW - 5.0F, my + 7.6F, Palette.argb(0x60, Palette.INK_FAINT));
				name(g, match, "a", "ak", w == 0, w == 1, colX, my, colW - 6.0F);
				name(g, match, "b", "bk", w == 1, w == 0, colX, my + 8.0F, colW - 6.0F);
				tallest = Math.max(tallest, my + 17.0F);
			}
		}
		if (bracket.contains("bronze") && bracket.get("bronze") instanceof CompoundTag bronze) {
			float by = tallest + 1.0F;
			Brush.text(g, font, Component.translatable("kultivator.sekte.turnamen.ui.perunggu"), left, by, Palette.ROD_LIGHT, 0.55F, false);
			int w = bronze.getIntOr("w", -1);
			name(g, bronze, "a", "ak", w == 0, w == 1, left + 46.0F, by, (right - left - 50.0F) / 2.0F);
			name(g, bronze, "b", "bk", w == 1, w == 0, left + 50.0F + (right - left - 50.0F) / 2.0F, by, (right - left - 50.0F) / 2.0F);
			tallest = by + 9.0F;
		}
		return tallest + 2.0F;
	}

	private void name(GuiGraphicsExtractor g, CompoundTag match, String key, String kindKey, boolean won, boolean lost, float x, float y, float room) {
		Component name = match.read(key, ComponentSerialization.CODEC).orElse(Component.literal("-"));
		boolean visitor = match.getIntOr(kindKey, 0) != 0;
		int color = won ? Palette.JADE_DARK : lost ? Palette.INK_FAINT : visitor ? Palette.ROD_LIGHT : Palette.INK;
		Brush.fit(g, font, won ? Brush.bold(name) : name, x, y, room, color, 0.55F, 0.38F, false);
	}

	/** No tournament running: the next date, Elite or not, and the all-time top of the old tournament. */
	private float schedule(GuiGraphicsExtractor g, CompoundTag live, float left, float top, float right) {
		float y = top;
		long next = live.getLongOr("next", 0L);
		boolean elite = live.getBooleanOr("elite_next", false);
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.turnamen.ui.berikutnya")), left, y, right - left, Palette.JADE_DARK, 0.75F);
		y += line(g, Component.translatable(elite ? "kultivator.sekte.turnamen.ui.hari_elit" : "kultivator.sekte.turnamen.ui.hari", next), left, y,
				right - left, elite ? Palette.GOLD_DARK : Palette.INK, 0.64F);
		ListTag ranking = live.getListOrEmpty("ranking");
		if (!ranking.isEmpty()) {
			y += 2.0F;
			y += line(g, Brush.bold(Component.translatable("kultivator.sekte.turnamen.ui.sepanjang_masa")), left, y, right - left, Palette.JADE_DARK, 0.7F);
			int rank = 0;
			for (Tag tag : ranking) {
				if (!(tag instanceof CompoundTag entry) || rank >= 5) {
					continue;
				}
				rank++;
				Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
				y += line(g, Component.translatable("kultivator.sekte.turnamen.ui.sepanjang_baris", rank, name, entry.getIntOr("points", 0),
						entry.getIntOr("firsts", 0)), left + 4.0F, y, right - left - 4.0F, rank == 1 ? Palette.GOLD_DARK : Palette.INK_SOFT, 0.58F);
			}
		}
		return y;
	}

	private float roll(GuiGraphicsExtractor g, CompoundTag d, float left, float top, float right) {
		ListTag roll = d.getListOrEmpty("roll");
		if (roll.isEmpty()) {
			return top;
		}
		float y = top;
		y += line(g, Brush.bold(Component.translatable("kultivator.sekte.turnamen.papan.juara")), left, y, right - left, Palette.JADE_DARK, 0.7F);
		List<CompoundTag> entries = new ArrayList<>();
		for (Tag tag : roll) {
			if (tag instanceof CompoundTag entry) {
				entries.add(entry);
			}
		}
		for (int i = 0; i < Math.min(5, entries.size()); i++) {
			CompoundTag entry = entries.get(i);
			if (y > y1 - 44.0F) {
				break;
			}
			Component name = entry.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			String kind = entry.getStringOr("k", "biasa");
			y += line(g, Component.translatable("kultivator.sekte.turnamen.papan.juara_baris", entry.getIntOr("s", 0),
					Component.translatable("kultivator.sekte.turnamen.jenis." + kind), name), left + 4.0F, y, right - left - 4.0F,
					i == 0 ? Palette.GOLD_DARK : Palette.INK_SOFT, 0.58F);
		}
		return y;
	}

	private void footer(GuiGraphicsExtractor g, CompoundTag d, int mouseX, int mouseY, long now) {
		float left = x0 + 12.0F;
		float y = y1 - 32.0F;
		Brush.rect(g, x0 + 16.0F, y - 2.0F, x1 - 16.0F, y - 1.0F, Palette.argb(0x60, Palette.JADE_DARK));
		Component tribune = Component.translatable(d.getBooleanOr("tribun", false) ? "kultivator.sekte.turnamen.ui.tribun_ada"
				: "kultivator.sekte.turnamen.ui.tribun_belum");
		Component record = Component.translatable("kultivator.sekte.turnamen.papan.antar", d.getIntOr("antar_w", 0), d.getIntOr("antar_l", 0));
		Brush.fit(g, font, tribune, left, y, 170.0F, Palette.INK_SOFT, 0.58F, 0.42F, false);
		Brush.fit(g, font, record, left + 176.0F, y, 110.0F, Palette.INK_SOFT, 0.58F, 0.42F, false);
		buttonY0 = buttonY1 = acceptX0 = acceptX1 = refuseX0 = refuseX1 = -1.0F;
		if (d.contains("pending")) {
			Component sect = d.read("pending", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			float py = y + 9.0F;
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
			Brush.fit(g, font, Component.translatable("kultivator.sekte.turnamen.ui.tantangan", sect).withStyle(ChatFormatting.BOLD), left, py,
					x1 - x0 - 140.0F, Palette.mix(Palette.SEAL_DARK, Palette.SEAL, pulse), 0.62F, 0.42F, false);
			acceptX0 = x1 - 120.0F;
			acceptX1 = acceptX0 + 50.0F;
			refuseX0 = acceptX1 + 6.0F;
			refuseX1 = refuseX0 + 50.0F;
			buttonY0 = py - 2.0F;
			buttonY1 = py + 10.0F;
			seal(g, acceptX0, acceptX1, Component.translatable("kultivator.sekte.turnamen.antar.terima"), Palette.JADE, mouseX, mouseY);
			seal(g, refuseX0, refuseX1, Component.translatable("kultivator.sekte.turnamen.antar.tolak"), Palette.SEAL, mouseX, mouseY);
		}
		Brush.wrap(g, font, Component.translatable("kultivator.sekte.turnamen.sumber").withStyle(ChatFormatting.ITALIC), x0 + 14.0F, y1 - 12.0F,
				x1 - x0 - 28.0F, Palette.INK_FAINT, 0.5F, false);
	}

	private void seal(GuiGraphicsExtractor g, float ax, float bx, Component label, int color, int mouseX, int mouseY) {
		boolean hover = mouseX >= ax && mouseX <= bx && mouseY >= buttonY0 && mouseY <= buttonY1;
		Brush.chamfer(g, (int) ax, (int) buttonY0, (int) bx, (int) buttonY1, 2, hover ? Palette.mix(color, 0xFFFFFFFF, 0.25F) : color);
		Brush.fitCentered(g, font, Brush.bold(label), (ax + bx) / 2.0F, buttonY0 + 2.5F, bx - ax - 4.0F, 0xFFFFF8E8, 0.62F, 0.4F, false);
	}

	private float line(GuiGraphicsExtractor g, Component text, float x, float y, float room, int color, float scale) {
		return Brush.wrap(g, font, text, x, y, room, color, scale, false) + 1.5F;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (buttonY0 >= 0.0F && my >= buttonY0 && my <= buttonY1) {
			if (mx >= acceptX0 && mx <= acceptX1) {
				ask(TurnamenPayloads.Minta.ACCEPT);
				onClose();
				return true;
			}
			if (mx >= refuseX0 && mx <= refuseX1) {
				ask(TurnamenPayloads.Minta.REFUSE);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
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
	public boolean isPauseScreen() {
		return false;
	}
}
