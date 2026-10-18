package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Segel Cepat (DESIGN 22.12.7, 22.12.8): the seal keys 1-4 cast loadout slots 1-4 ({@code CAST} with the id; the server
 * runs every check), and the HUD shows four 10 px tiles with their own cooldown sweeps whenever a seal key is bound or
 * one of the first four slots is filled.
 */
public final class QuickSeals {

	public static final int TILE = 10;

	/** Casts loadout slot {@code index} (0-3); an empty slot only shows a hint. */
	public static void cast(int index) {
		String[] slots = TeknikSession.slots();
		String id = index < slots.length ? slots[index] : "";
		if (id.isEmpty() || TechniqueRegistry.byId(id) == null) {
			CodexHud.hint(Component.translatable("kultivator.codex.quick.empty", index + 1));
			return;
		}
		Keys.send(new Payloads.Action(Actions.CAST, 0, id));
	}

	/** Shown when a seal key is bound or slots 1-4 hold something. */
	public static boolean shown() {
		for (KeyMapping key : new KeyMapping[]{Keys.SEAL_1, Keys.SEAL_2, Keys.SEAL_3, Keys.SEAL_4}) {
			if (!key.isUnbound()) {
				return true;
			}
		}
		String[] slots = TeknikSession.slots();
		for (int i = 0; i < 4 && i < slots.length; i++) {
			if (!slots[i].isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The four tiles in a row starting at {@code x, y}. */
	static void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
		CultivationData data = ClientCultivation.data();
		String[] slots = TeknikSession.slots();
		for (int i = 0; i < 4; i++) {
			int tx = x + i * (TILE + 1);
			TechniqueSpec spec = i < slots.length && !slots[i].isEmpty() ? TechniqueRegistry.byId(slots[i]) : null;
			g.fill(tx - 1, y - 1, tx + TILE + 1, y + TILE + 1, 0xA0101824);
			boolean affordable = spec != null && SpecInfo.affordable(data, spec);
			int paper = spec == null ? 0x60F1D57E : affordable ? Palette.TALISMAN : Palette.grey(Palette.TALISMAN, 0.6F);
			g.fill(tx, y, tx + TILE, y + TILE, paper);
			if (spec != null) {
				long left = SpecInfo.cooldownLeft(spec);
				int total = SpecInfo.cooldown(data, spec);
				if (left > 0L && total > 0) {
					g.enableScissor(tx, y, tx + TILE, y + TILE);
					Brush.pie(g, tx + TILE / 2.0F, y + TILE / 2.0F, TILE * 0.75F, 1.0F - Math.min(1.0F, left / (float) total), 1.0F,
							Palette.mix(paper, 0xFF1A2230, 0.6F));
					g.disableScissor();
				}
				Glyphs.spec(g, spec, tx + TILE / 2.0F, y + TILE / 2.0F, TILE * 0.62F, affordable ? SpecInfo.ink(spec) : 0xFF6A6A6A);
			}
			Brush.frame(g, tx, y, tx + TILE, y + TILE, spec == null ? Palette.argb(0x80, Palette.GOLD_DARK) : Palette.SEAL);
			Brush.text(g, font, Component.literal(String.valueOf(i + 1)), tx + 0.8F, y + 0.6F, Palette.GOLD_DARK, 0.4F, false);
		}
	}

	private QuickSeals() {
	}
}
