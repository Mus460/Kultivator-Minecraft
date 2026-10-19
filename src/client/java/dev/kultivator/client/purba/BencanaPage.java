package dev.kultivator.client.purba;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * The Bencana tablet of the Keabadian screen (DESIGN 26.10, owned by P9-purba): the seven calamity seals (Lulus, Terlewati,
 * the next one, still locked) with their gate stars, the T factor, the next calamity's cost against the Stored Qi, a running
 * calamity, [Mulai] and the Segel Penangkal switch, the next wrath day and the last five results. The buttons send
 * {@code Actions.PURBA} "start" and "segel:0|1"; the data comes from {@link PurbaClient}.
 */
public class BencanaPage extends DuniaPage {

	private static final int TOP = 14;
	private static final int COUNT = 7;

	public BencanaPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.bencana");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.bencana.short");
	}

	@Override
	public int icon() {
		return Glyphs.STATUS;
	}

	private static CompoundTag calamity() {
		return PurbaClient.data().getCompoundOrEmpty("calamity");
	}

	@Override
	protected void build() {
		int bw = Math.min(96, (w - 12) / 2);
		int by = y + h - 30;
		TalismanButton start = new TalismanButton(x + 4, by, bw, 14, () -> {
			CompoundTag c = calamity();
			int pending = c.getIntOr("pending", 0);
			return pending > 0 ? Component.translatable("kultivator.purba.ui.start", pending)
					: Component.translatable("kultivator.purba.ui.start_t", c.getIntOr("skip_first", 0));
		});
		start.enabledWhen(() -> calamity().getBooleanOr("can_start", false));
		start.onPress(() -> send(Actions.PURBA, 0, "start"));
		start.tooltip(() -> {
			CompoundTag c = calamity();
			if (c.getBooleanOr("can_start", false)) {
				return List.of(Component.translatable("kultivator.purba.ui.start_tip"));
			}
			return List.of(c.read("why", ComponentSerialization.CODEC).orElse(Component.empty()));
		});
		add(start);
		TalismanButton seal = new TalismanButton(x + w - bw - 4, by, bw, 14, () -> Component.translatable(
				calamity().getBooleanOr("segel", false) ? "kultivator.purba.ui.seal_on" : "kultivator.purba.ui.seal_off"));
		seal.onPress(() -> send(Actions.PURBA, 0, calamity().getBooleanOr("segel", false) ? "segel:0" : "segel:1"));
		seal.tooltip(() -> List.of(Component.translatable("kultivator.purba.ui.seal_tip", calamity().getIntOr("shards", 0))));
		add(seal);
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
		CompoundTag c = data.getCompoundOrEmpty("calamity");
		int passed = c.getIntOr("passed", 0);
		int skipped = c.getIntOr("skipped", 0);
		int pending = c.getIntOr("pending", 0);
		int open = c.getIntOr("open", 0);
		int[] gates = c.getIntArray("gates").orElse(new int[COUNT]);
		// The seven seals in a row.
		float slot = (w - 8.0F) / COUNT;
		float sy = y + TOP + 12.0F;
		float radius = Math.min(10.0F, slot * 0.38F);
		for (int k = 1; k <= COUNT; k++) {
			float sx = x + 4.0F + slot * (k - 0.5F);
			boolean isPassed = (passed & (1 << k)) != 0;
			boolean isSkipped = (skipped & (1 << k)) != 0;
			int color;
			if (isPassed) {
				color = Palette.JADE;
			} else if (isSkipped) {
				color = Palette.LOCKED;
			} else if (k == pending) {
				color = Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.5F + 0.5F * Mth.sin(seconds * 4.0F));
			} else if (k == open) {
				color = Palette.WARN;
			} else {
				color = Palette.argb(0x60, Palette.INK_FAINT);
			}
			Brush.disc(g, sx, sy, radius, color);
			Brush.ring(g, sx, sy, radius, radius + 1.0F, Palette.argb(0xC0, Palette.GOLD));
			Brush.centered(g, font, Component.literal(String.valueOf(k)), sx, sy - 3.0F, Palette.TALISMAN, 0.75F, false);
			int gate = k - 1 < gates.length ? gates[k - 1] : 0;
			Brush.centered(g, font, Component.translatable("kultivator.purba.ui.gate_star", gate), sx, sy + radius + 2.0F, Palette.INK_SOFT, 0.55F, false);
			if (k == 2 || k == 4) {
				float bx = x + 4.0F + slot * k;
				Brush.rect(g, bx - 0.5F, sy - radius, bx + 0.5F, sy + radius, Palette.argb(0x90, Palette.GOLD));
			}
		}
		float cursor = sy + radius + 12.0F;
		float lw = w - 8.0F;
		if (!c.getBooleanOr("enabled", true)) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.calamity.disabled"), x + 4.0F, cursor, lw, Palette.INK_FAINT, scale, scale * 0.6F, false);
			cursor += 10.0F;
		}
		Component legend = Component.translatable("kultivator.purba.ui.legend");
		Brush.fit(g, font, legend, x + 4.0F, cursor, lw, Palette.INK_FAINT, scale * 0.9F, scale * 0.55F, false);
		cursor += 9.0F;
		String t = String.format(Locale.ROOT, "%.3f", c.getDoubleOr("t", 1.0D));
		Brush.fit(g, font, Component.translatable("kultivator.purba.ui.t", t, c.getIntOr("stars", 0)), x + 4.0F, cursor, lw, Palette.INK, scale,
				scale * 0.6F, false);
		cursor += 10.0F;
		if (c.contains("run_k")) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.running", c.getIntOr("run_k", 1), c.getIntOr("run_strikes", 0),
					c.getIntOr("run_total", 7)), x + 4.0F, cursor, lw, Palette.DANGER, scale, scale * 0.6F, false);
		} else if (pending > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.next", pending, gates.length >= pending ? gates[pending - 1] : 0,
					Math.round(c.getDoubleOr("cost", 0.0D)), Math.round(c.getDoubleOr("stored", 0.0D))), x + 4.0F, cursor, lw, Palette.SEAL_DARK, scale,
					scale * 0.55F, false);
		} else if (open > 0) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.later", open, gates.length >= open ? gates[open - 1] : 0), x + 4.0F, cursor,
					lw, Palette.INK_SOFT, scale, scale * 0.6F, false);
		} else {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.all_done"), x + 4.0F, cursor, lw, Palette.JADE_DARK, scale, scale * 0.6F, false);
		}
		cursor += 10.0F;
		if (c.getBooleanOr("title", false)) {
			Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.purba.title.trampler")), x + 4.0F, cursor, lw, Palette.GOLD_DARK, scale,
					scale * 0.6F, false);
			cursor += 10.0F;
		}
		CompoundTag wrath = data.getCompoundOrEmpty("wrath");
		Component wrathLine;
		if (!wrath.getBooleanOr("enabled", true)) {
			wrathLine = Component.translatable("kultivator.purba.ui.wrath_off");
		} else if (!wrath.getBooleanOr("eligible", false)) {
			wrathLine = Component.translatable("kultivator.purba.ui.wrath_none");
		} else if (wrath.getBooleanOr("live", false)) {
			wrathLine = Component.translatable("kultivator.purba.ui.wrath_live");
		} else {
			wrathLine = Component.translatable("kultivator.purba.ui.wrath_next", wrath.getLongOr("next", -1L), wrath.getLongOr("today", 0L));
		}
		Brush.fit(g, font, wrathLine, x + 4.0F, cursor, lw, Palette.GOLD_DARK, scale, scale * 0.6F, false);
		cursor += 10.0F;
		long langit = data.getLongOr("langit", 0L);
		if (langit > 0L) {
			Brush.fit(g, font, Component.translatable("kultivator.purba.ui.langit", PurbaPage.minutes(langit)), x + 4.0F, cursor, lw, Palette.SEAL_DARK,
					scale, scale * 0.6F, false);
			cursor += 10.0F;
		}
		// The last five results, newest first, above the buttons.
		ListTag results = data.getListOrEmpty("results");
		float bottom = y + h - 33.0F;
		for (int i = results.size() - 1; i >= 0 && cursor + 8.0F <= bottom; i--) {
			CompoundTag entry = results.getCompoundOrEmpty(i);
			boolean pass = entry.getBooleanOr("pass", false);
			Component line = Component.translatable(pass ? "kultivator.purba.ui.result_pass" : "kultivator.purba.ui.result_fail", entry.getIntOr("k", 1),
					entry.getLongOr("day", 0L));
			int top = Math.round(cursor);
			g.fill(x + 4, top, x + 6, top + 8, pass ? Palette.JADE : Palette.SEAL);
			Brush.fit(g, font, line, x + 9.0F, cursor, lw - 6.0F, pass ? Palette.INK : Palette.SEAL_DARK, scale * 0.9F, scale * 0.55F, false);
			cursor += 9.0F;
		}
		Brush.fit(g, font, Component.translatable("kultivator.purba.calamity.source"), x + 4.0F, y + h - 8.0F, w - 8.0F, Palette.INK_FAINT, 0.5F, 0.35F,
				false);
	}
}
