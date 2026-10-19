package dev.kultivator.client.alam;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.List;

/**
 * The Alam tablet of the Dunia screen (DESIGN 25.10, owned by P8-alam): four realm seal cards in a 2 × 2 grid. Each shows the
 * realm's seal and name, "Siap" or the lock reason, the key count (and the heavenvoid fragments), the clears or the best Makam
 * layer, the cooldown, a reward preview and a "Buka Gerbang" talisman that needs the key. A line above names the lifetime
 * crystals, Luka Alam and Mode Ringan when it is on.
 */
public class AlamPage extends DuniaPage {

	private static final String[] IDS = {"blood_fire_trial", "heavenvoid_hall", "ancient_god_land", "heavenly_tomb"};
	private static final int TOP = 14;
	private static final int GAP = 4;

	private final List<TalismanButton> buttons = new ArrayList<>();

	public AlamPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.tab.alam");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.tab.alam.short");
	}

	@Override
	public int icon() {
		return Glyphs.JALAN;
	}

	private int cardW() {
		return (w - GAP) / 2;
	}

	private int cardH() {
		return (h - TOP - GAP) / 2;
	}

	private int cardX(int index) {
		return x + (index % 2) * (cardW() + GAP);
	}

	private int cardY(int index) {
		return y + TOP + (index / 2) * (cardH() + GAP);
	}

	@Override
	protected void build() {
		buttons.clear();
		for (int i = 0; i < IDS.length; i++) {
			String id = IDS[i];
			int bw = Math.min(90, cardW() - 10);
			TalismanButton button = new TalismanButton(cardX(i) + cardW() - bw - 4, cardY(i) + cardH() - 17, bw, 14,
					() -> Component.translatable("kultivator.alam.page.open"));
			button.enabledWhen(() -> realm(id).getBooleanOr("ready", false));
			button.onPress(() -> send(Actions.ALAM, 0, "open:" + id));
			button.tooltip(() -> {
				Component why = why(realm(id));
				return why == null ? List.of(Component.translatable("kultivator.alam.page.open_tip")) : List.of(why);
			});
			buttons.add(add(button));
		}
	}

	private static CompoundTag realm(String id) {
		for (Tag tag : AlamClient.data().getListOrEmpty("realms")) {
			if (tag instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				return entry;
			}
		}
		return new CompoundTag();
	}

	private static Component why(CompoundTag entry) {
		return entry.read("why", ComponentSerialization.CODEC).orElse(null);
	}

	/** Ticks as {@code h:mm} (game minutes are real minutes here: 1200 ticks). */
	private static String minutes(long ticks) {
		long minutes = (ticks + 1199L) / 1200L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60L, minutes % 60L);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = AlamClient.data();
		List<Component> notes = new ArrayList<>();
		notes.add(Component.translatable("kultivator.alam.page.crystals", data.getIntOr("crystals", 0), 94));
		long luka = data.getLongOr("luka", 0L);
		if (luka > 0L) {
			notes.add(Component.translatable("kultivator.alam.page.luka", minutes(luka)).withColor(Palette.DANGER & 0xFFFFFF));
		}
		if (data.getBooleanOr("light", false)) {
			notes.add(Component.translatable("kultivator.alam.page.light").withColor(Palette.JADE_DARK & 0xFFFFFF));
		}
		Component line = notes.get(0);
		for (int i = 1; i < notes.size(); i++) {
			line = Component.translatable("kultivator.alam.page.sep", line, notes.get(i));
		}
		Brush.fit(g, font, line, x + 2.0F, y + 2.0F, w - 4.0F, Palette.INK_SOFT, 0.8F, 0.5F, false);
		for (int i = 0; i < IDS.length; i++) {
			drawCard(g, i, IDS[i], realm(IDS[i]));
		}
	}

	private void drawCard(GuiGraphicsExtractor g, int index, String id, CompoundTag entry) {
		int cx = cardX(index);
		int cy = cardY(index);
		int cw = cardW();
		int ch = cardH();
		int color = Palette.opaque(RealmScenery.color(index + 1));
		boolean ready = entry.getBooleanOr("ready", false);
		panel(g, cx, cy, cx + cw, cy + ch);
		// The realm's seal: a square in its colour with the ordinal.
		int seal = 14;
		g.fill(cx + 4, cy + 4, cx + 4 + seal, cy + 4 + seal, Palette.argb(0xE0, color));
		Brush.frame(g, cx + 5, cy + 5, cx + 3 + seal, cy + 3 + seal, Palette.argb(0xB0, Palette.TALISMAN));
		Brush.centered(g, font, Component.literal(String.valueOf(index + 1)), cx + 4 + seal / 2.0F, cy + 7.0F, Palette.TALISMAN, 0.9F, false);
		float tx = cx + 8.0F + seal;
		float room = cw - seal - 12.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.alam.realm." + id)), tx, cy + 4.0F, room, Palette.darken(color, 0.35F), 0.85F, 0.5F, false);
		Component status;
		if (entry.isEmpty()) {
			status = Component.translatable("kultivator.alam.page.loading");
		} else if (ready) {
			status = Component.translatable("kultivator.alam.status.ready");
		} else {
			Component why = why(entry);
			status = why == null ? Component.translatable("kultivator.alam.page.locked") : why;
		}
		Brush.fit(g, font, status, tx, cy + 13.0F, room, ready ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.7F, 0.45F, false);
		float ly = cy + 23.0F;
		float lw = cw - 8.0F;
		Component keys = Component.translatable("kultivator.alam.page.keys", Component.translatable("kultivator.alam.page.key." + id),
				entry.getIntOr("have", 0), entry.getIntOr("need", 1));
		if ("heavenvoid_hall".equals(id)) {
			keys = Component.translatable("kultivator.alam.page.fragments", keys, entry.getIntOr("fragments", 0));
		}
		Brush.fit(g, font, keys, cx + 4.0F, ly, lw, Palette.INK, 0.7F, 0.45F, false);
		ly += 8.0F;
		Component record = "heavenly_tomb".equals(id) ? Component.translatable("kultivator.alam.list.best", entry.getIntOr("best", 0))
				: Component.translatable("kultivator.alam.list.clears", entry.getIntOr("clears", 0));
		long cd = entry.getLongOr("cd", 0L);
		if (cd > 0L) {
			record = Component.translatable("kultivator.alam.page.sep", record, Component.translatable("kultivator.alam.page.cooldown", minutes(cd)));
		}
		Brush.fit(g, font, record, cx + 4.0F, ly, lw, Palette.INK_SOFT, 0.7F, 0.45F, false);
		ly += 8.0F;
		float bottom = cy + ch - 19.0F;
		if (ly < bottom) {
			Brush.wrap(g, font, Component.translatable("kultivator.alam.realm." + id + ".reward"), cx + 4.0F, ly, lw, Palette.INK_FAINT, 0.6F, false);
		}
	}
}
