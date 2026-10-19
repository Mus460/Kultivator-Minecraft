package dev.kultivator.client.desa;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * The Desa tablet of the Dunia screen (DESIGN 25.10): a compact list of the vassals (name, loyalty bar, villagers, the
 * open request, the guard), the vassal count against the hall's limit, and how to pledge a village when there is none.
 * The full cards with their actions live on the hall's Desa tablet. Reads {@link DesaClient#data()}.
 */
public class DesaPage extends DuniaPage {

	public DesaPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.tab.desa");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.tab.desa.short");
	}

	@Override
	public int icon() {
		return Glyphs.HALL;
	}

	@Override
	protected void build() {
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = DesaClient.data();
		float scale = w >= 260 ? 0.68F : 0.6F;
		heading(g, Component.translatable(DesaCards.P + "dunia_title", data.getIntOr("count", 0), data.getIntOr("max", 1)), x + 2.0F, y, w - 4.0F, 0.8F);
		float cursor = y + 14.0F;
		if (!data.getBooleanOr("on", true)) {
			Brush.wrap(g, font, Component.translatable(DesaCards.P + "off"), x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale, false);
			return;
		}
		ListTag villages = data.getListOrEmpty("villages");
		long today = data.getLongOr("day", 0L);
		if (villages.isEmpty()) {
			cursor += Brush.wrap(g, font, Component.translatable(DesaCards.P + "none", data.getIntOr("range", 256)), x + 4.0F, cursor, w - 8.0F,
					Palette.INK_SOFT, scale, false) + 4.0F;
		}
		float rowH = 9.0F * scale * 3.0F + 7.0F;
		for (int i = 0; i < villages.size() && cursor + rowH < y + h - 14.0F; i++) {
			CompoundTag v = villages.getCompoundOrEmpty(i);
			panel(g, x + 2, Math.round(cursor), x + w - 2, Math.round(cursor + rowH - 2.0F));
			float tx = x + 6.0F;
			float ty = cursor + 2.5F;
			Brush.fit(g, font, Brush.bold(Component.literal(v.getStringOr("name", "?"))), tx, ty, w * 0.38F, Palette.SEAL_DARK, scale + 0.05F, 0.45F, false);
			Brush.fit(g, font, DesaCards.headline(v), x + w * 0.42F, ty, w * 0.5F, Palette.INK, scale, 0.45F, false);
			DesaCards.seal(g, font, v, x + w - 8.0F, ty + 3.0F);
			ty += 9.0F * scale + 1.5F;
			if (v.getBooleanOr("pledged", false)) {
				DesaCards.loyaltyBar(g, tx, ty, w - 16.0F, v.getIntOr("loyalty", 0));
			}
			ty += 5.0F;
			Component detail = DesaCards.request(v);
			if (detail == null) {
				Component raid = DesaCards.lastRaid(v, today);
				detail = raid != null ? raid : Component.translatable(DesaCards.P + "tribute", v.getLongOr("tribute_in", 0L));
			}
			Brush.fit(g, font, detail, tx, ty, w * 0.55F, Palette.INK_SOFT, scale - 0.04F, 0.42F, false);
			Brush.fit(g, font, DesaCards.guard(v), x + w * 0.6F, ty, w * 0.38F, Palette.INK_SOFT, scale - 0.04F, 0.42F, false);
			cursor += rowH;
		}
		Component hint = Component.translatable(DesaCards.P + "dunia_hint").withStyle(ChatFormatting.ITALIC);
		float hintH = Brush.wrapHeight(font, hint, w - 8.0F, 0.52F);
		Brush.wrap(g, font, hint, x + 4.0F, y + h - hintH - 1.0F, w - 8.0F, Palette.INK_FAINT, 0.52F, false);
	}
}
