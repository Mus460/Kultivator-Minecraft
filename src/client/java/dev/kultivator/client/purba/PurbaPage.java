package dev.kultivator.client.purba;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The Purba tablet of the Keabadian screen (DESIGN 26.10, owned by P9-purba): three beast seals (Kun-Peng, Zhulong, Taotie)
 * with their kills, the first kill and the unique reward (the Kunpeng bone pieces held, the candle eye), then the natural
 * cooldown, today's repeat rewards (≤ 3), the Gejolak chance, the scroll recipe, a live fight and Luka Purba. Read-only;
 * the data comes from {@link PurbaClient}.
 */
public class PurbaPage extends DuniaPage {

	private static final String[] KINDS = {"kunpeng", "zhulong", "taotie"};
	private static final int[] COLORS = {0x3A7BD5, 0xFF5A2A, 0xC08A3A};
	private static final int TOP = 14;
	private static final int GAP = 4;

	public PurbaPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.purba");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.purba.short");
	}

	@Override
	public int icon() {
		return Glyphs.SEAL;
	}

	@Override
	protected void build() {
	}

	private int cardW() {
		return (w - 2 * GAP) / 3;
	}

	private int cardH() {
		return Math.min(78, Math.max(56, (h - TOP) / 2));
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = PurbaClient.data();
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		float scale = w >= 260 ? 0.66F : 0.6F;
		if (data.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.purba.ui.loading"), x + 4.0F, y + TOP + 2.0F, w - 8.0F, Palette.INK_SOFT, scale, false);
			return;
		}
		if (!data.getBooleanOr("beasts", true)) {
			Brush.wrap(g, font, Component.translatable("kultivator.purba.disabled"), x + 4.0F, y + TOP + 2.0F, w - 8.0F, Palette.INK_SOFT, scale, false);
			return;
		}
		CompoundTag kills = data.getCompoundOrEmpty("kills");
		for (int i = 0; i < 3; i++) {
			drawSeal(g, i, kills.getIntOr(KINDS[i], 0), data, scale);
		}
		float cursor = y + TOP + cardH() + 5.0F;
		float lw = w - 8.0F;
		long cd = data.getLongOr("cd", 0L);
		Component cooldown = cd <= 0L ? Component.translatable("kultivator.purba.ui.cd_ready")
				: Component.translatable("kultivator.purba.ui.cd", minutes(cd));
		Component chance = Component.translatable("kultivator.purba.ui.chance", Math.round(data.getDoubleOr("chance", 0.08D) * 1000.0D) / 10.0D);
		Brush.fit(g, font, Component.translatable("kultivator.purba.ui.sep", cooldown, chance), x + 4.0F, cursor, lw, Palette.INK, scale, scale * 0.7F, false);
		cursor += 10.0F;
		int repeats = data.getIntOr("rep_n", 0);
		int cap = data.getIntOr("rep_cap", 3);
		Brush.fit(g, font, Component.translatable("kultivator.purba.ui.repeats", repeats, cap), x + 4.0F, cursor, lw,
				repeats >= cap ? Palette.SEAL_DARK : Palette.INK_SOFT, scale, scale * 0.7F, false);
		cursor += 10.0F;
		Brush.fit(g, font, Component.translatable("kultivator.purba.ui.recipe"), x + 4.0F, cursor, lw, Palette.INK_SOFT, scale, scale * 0.6F, false);
		cursor += 10.0F;
		if (!data.getBooleanOr("realm", false)) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.not_realm"), x + 4.0F, cursor, lw, Palette.INK_FAINT, scale, scale * 0.6F, false);
			cursor += 10.0F;
		}
		CompoundTag fight = data.getCompoundOrEmpty("fight");
		if (!fight.isEmpty()) {
			int kind = Math.max(0, Math.min(2, fight.getIntOr("kind", 0)));
			Component kindName = Component.translatable("kultivator.purba.kind." + KINDS[kind]);
			Component line = fight.getBooleanOr("active", false)
					? Component.translatable("kultivator.purba.ui.fight", kindName, Component.translatable("kultivator.purba.phase." + KINDS[kind] + "."
					+ Math.max(0, Math.min(2, fight.getIntOr("phase", 0)))))
					: Component.translatable("kultivator.purba.ui.omen", kindName);
			Brush.fit(g, font, line, x + 4.0F, cursor, lw, Palette.DANGER, scale, scale * 0.6F, false);
			cursor += 10.0F;
		}
		long luka = data.getLongOr("luka", 0L);
		if (luka > 0L) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.luka", minutes(luka)), x + 4.0F, cursor, lw, Palette.SEAL_DARK, scale,
					scale * 0.6F, false);
		}
		Brush.fit(g, font, Component.translatable("kultivator.purba.ui.source"), x + 4.0F, y + h - 8.0F, w - 8.0F, Palette.INK_FAINT, 0.5F, 0.35F, false);
	}

	private void drawSeal(GuiGraphicsExtractor g, int index, int kills, CompoundTag data, float scale) {
		int cw = cardW();
		int ch = cardH();
		int cx = x + index * (cw + GAP);
		int cy = y + TOP;
		int color = Palette.opaque(COLORS[index]);
		panel(g, cx, cy, cx + cw, cy + ch);
		float sx = cx + cw / 2.0F;
		float sy = cy + 14.0F;
		Brush.disc(g, sx, sy, 9.0F, Palette.argb(kills > 0 ? 0xE0 : 0x70, color));
		Brush.ring(g, sx, sy, 9.0F, 10.0F, Palette.argb(0xC0, Palette.GOLD));
		Brush.centered(g, font, Component.translatable("kultivator.purba.ui.glyph." + KINDS[index]), sx, sy - 3.5F, Palette.TALISMAN, 0.8F, false);
		float ly = cy + 26.0F;
		float lw = cw - 6.0F;
		Brush.fitCentered(g, font, Brush.bold(Component.translatable("kultivator.purba.kind." + KINDS[index])), sx, ly, lw,
				Palette.darken(color, 0.35F), 0.75F, 0.45F, false);
		ly += 9.0F;
		Brush.fitCentered(g, font, Component.translatable("kultivator.purba.ui.kills", kills), sx, ly, lw, Palette.INK, scale, scale * 0.6F, false);
		ly += 8.0F;
		Brush.fitCentered(g, font, Component.translatable(kills > 0 ? "kultivator.purba.ui.first_done" : "kultivator.purba.ui.first_open"), sx, ly, lw,
				kills > 0 ? Palette.JADE_DARK : Palette.INK_SOFT, scale, scale * 0.6F, false);
		ly += 8.0F;
		Component unique = switch (index) {
			case 0 -> data.getBooleanOr("art", false) ? Component.translatable("kultivator.purba.ui.art_known")
					: Component.translatable("kultivator.purba.ui.pieces", data.getIntOr("pieces", 0));
			case 1 -> Component.translatable(data.getBooleanOr("eye", false) ? "kultivator.purba.ui.eye_owned" : "kultivator.purba.ui.eye_missing");
			default -> Component.translatable("kultivator.purba.ui.no_unique");
		};
		if (ly < cy + ch - 6) {
			Brush.fitCentered(g, font, unique, sx, ly, lw, Palette.INK_SOFT, scale, scale * 0.55F, false);
		}
	}

	/** Ticks as whole minutes. */
	static String minutes(long ticks) {
		long minutes = (ticks + 1199L) / 1200L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60L, minutes % 60L);
	}
}
