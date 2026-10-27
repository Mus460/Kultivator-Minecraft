package dev.kultivator.client.purba;

import dev.kultivator.client.Keys;
import dev.kultivator.client.abadi.AbadiClient;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.GulirTeks;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * Keabadian › Purba › Bencana (DESIGN 26.10, 43.5; owned by P9-purba): the sub of {@link PurbaPage} that draws the star
 * calamities (was the Bencana tablet). The seven calamity seals (Lulus, Terlewati, the next one, still locked) with their
 * gate stars, the T factor, the next calamity's cost against the Stored Qi, a running calamity, [Mulai] and the Segel
 * Penangkal switch, the next wrath day and the last five results. The buttons send {@code Actions.PURBA} "start" and
 * "segel:0|1"; the data comes from {@link PurbaClient}. Text at 1.0 (43.0.5); the lines scroll in the kit list.
 */
final class BencanaPage {

	private static final int COUNT = 7;
	private static final int BUTTON_H = 14;

	private final PurbaPage page;
	private final GulirTeks baris = new GulirTeks();

	BencanaPage(PurbaPage page) {
		this.page = page;
	}

	private static CompoundTag calamity() {
		return PurbaClient.data().getCompoundOrEmpty("calamity");
	}

	private static void send(String op) {
		Keys.send(new Payloads.Action(Actions.PURBA, 0, op));
	}

	/** The two talismans at the foot of the sub. */
	void build() {
		int x = page.bx();
		int w = page.bw();
		int bw = Math.max(TalismanButton.MIN_W, Math.min(130, (w - 4) / 2));
		int by = page.by() + page.bh() - BUTTON_H;
		TalismanButton start = new TalismanButton(x, by, bw, BUTTON_H, () -> {
			CompoundTag c = calamity();
			int pending = c.getIntOr("pending", 0);
			return pending > 0 ? Component.translatable("kultivator.purba.ui.start", pending)
					: Component.translatable("kultivator.purba.ui.start_t", c.getIntOr("skip_first", 0));
		});
		start.enabledWhen(() -> Keys.connected() && calamity().getBooleanOr("can_start", false));
		start.onPress(() -> send("start"));
		start.tooltip(() -> {
			CompoundTag c = calamity();
			if (c.getBooleanOr("can_start", false)) {
				return List.of(Component.translatable("kultivator.purba.ui.start_tip"));
			}
			return List.of(c.read("why", ComponentSerialization.CODEC).orElse(Component.empty()));
		});
		page.tambah(start);
		TalismanButton seal = new TalismanButton(x + w - bw, by, bw, BUTTON_H, () -> Component.translatable(
				calamity().getBooleanOr("segel", false) ? "kultivator.purba.ui.seal_on" : "kultivator.purba.ui.seal_off"));
		seal.enabledWhen(Keys::connected);
		seal.onPress(() -> send(calamity().getBooleanOr("segel", false) ? "segel:0" : "segel:1"));
		seal.tooltip(() -> List.of(Component.translatable("kultivator.purba.ui.seal_tip", calamity().getIntOr("shards", 0))));
		page.tambah(seal);
	}

	/** The radius of the seals for the current width. */
	private float radius() {
		return Math.min(10.0F, (page.bw() / (float) COUNT) * 0.38F);
	}

	/** The seven seals in a row, the next one breathing. */
	void drawBehind(GuiGraphicsExtractor g, float seconds) {
		CompoundTag data = PurbaClient.data();
		if (data.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		CompoundTag c = data.getCompoundOrEmpty("calamity");
		int passed = c.getIntOr("passed", 0);
		int skipped = c.getIntOr("skipped", 0);
		int pending = c.getIntOr("pending", 0);
		int open = c.getIntOr("open", 0);
		int[] gates = c.getIntArray("gates").orElse(new int[COUNT]);
		int x = page.bx();
		float slot = page.bw() / (float) COUNT;
		float r = radius();
		float sy = page.by() + r + 1.0F;
		float time = (net.minecraft.util.Util.getMillis() % 100000L) / 1000.0F;
		for (int k = 1; k <= COUNT; k++) {
			float sx = x + slot * (k - 0.5F);
			int color;
			if ((passed & (1 << k)) != 0) {
				color = Palette.JADE;
			} else if ((skipped & (1 << k)) != 0) {
				color = Palette.LOCKED;
			} else if (k == pending) {
				color = Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, 0.5F + 0.5F * Mth.sin(time * 4.0F));
			} else if (k == open) {
				color = Palette.WARN;
			} else {
				color = Palette.argb(0x60, Palette.INK_FAINT);
			}
			Brush.disc(g, sx, sy, r, color);
			Brush.ring(g, sx, sy, r, r + 1.0F, Palette.argb(0xC0, Palette.GOLD));
			Brush.centered(g, font, Component.literal(String.valueOf(k)), sx, sy - 4.0F, Palette.TALISMAN, 1.0F, false);
			int gate = k - 1 < gates.length ? gates[k - 1] : 0;
			Brush.centered(g, font, Component.translatable("kultivator.purba.ui.gate_star", gate), sx, sy + r + 2.0F, Palette.INK_SOFT, 1.0F, false);
			if (k == 2 || k == 4) {
				float bx = x + slot * k;
				Brush.rect(g, bx - 0.5F, sy - r, bx + 0.5F, sy + r, Palette.argb(0x90, Palette.GOLD));
			}
		}
	}

	/** The lines under the seals, in one scroll above the buttons. */
	void lukis(Lukis l, CompoundTag data) {
		CompoundTag c = data.getCompoundOrEmpty("calamity");
		int pending = c.getIntOr("pending", 0);
		int open = c.getIntOr("open", 0);
		int[] gates = c.getIntArray("gates").orElse(new int[COUNT]);
		int x = page.bx();
		int w = page.bw();
		AbadiClient.mulai(baris, w, Payloads.DuniaSync.CH_PURBA);
		if (!c.getBooleanOr("enabled", true)) {
			baris.tambah(Component.translatable("kultivator.purba.calamity.disabled"), Palette.INK_FAINT);
		}
		baris.tambah(Component.translatable("kultivator.purba.ui.legend"), Palette.INK_FAINT);
		String t = String.format(Locale.ROOT, "%.3f", c.getDoubleOr("t", 1.0D));
		baris.tambah(Component.translatable("kultivator.purba.ui.t", t, c.getIntOr("stars", 0)), Palette.INK);
		if (c.contains("run_k")) {
			baris.tambah(Component.translatable("kultivator.purba.ui.running", c.getIntOr("run_k", 1), c.getIntOr("run_strikes", 0),
					c.getIntOr("run_total", 7)), Palette.DANGER);
		} else if (pending > 0) {
			baris.tambah(Component.translatable("kultivator.purba.ui.next", pending, gates.length >= pending ? gates[pending - 1] : 0,
					Math.round(c.getDoubleOr("cost", 0.0D)), Math.round(c.getDoubleOr("stored", 0.0D))), Palette.SEAL_DARK);
		} else if (open > 0) {
			baris.tambah(Component.translatable("kultivator.purba.ui.later", open, gates.length >= open ? gates[open - 1] : 0), Palette.INK_SOFT);
		} else {
			baris.tambah(Component.translatable("kultivator.purba.ui.all_done"), Palette.JADE_DARK);
		}
		if (c.getBooleanOr("title", false)) {
			baris.tambah(Brush.bold(Component.translatable("kultivator.purba.title.trampler")), Palette.GOLD_DARK);
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
		baris.tambah(wrathLine, Palette.GOLD_DARK);
		long langit = data.getLongOr("langit", 0L);
		if (langit > 0L) {
			baris.tambah(Component.translatable("kultivator.purba.ui.langit", PurbaPage.minutes(langit)), Palette.SEAL_DARK);
		}
		// The last five results, newest first.
		ListTag results = data.getListOrEmpty("results");
		if (!results.isEmpty()) {
			baris.jarak();
		}
		for (int i = results.size() - 1; i >= 0; i--) {
			CompoundTag entry = results.getCompoundOrEmpty(i);
			boolean pass = entry.getBooleanOr("pass", false);
			baris.tambah(Component.translatable(pass ? "kultivator.purba.ui.result_pass" : "kultivator.purba.ui.result_fail", entry.getIntOr("k", 1),
					entry.getLongOr("day", 0L)), pass ? Palette.INK : Palette.SEAL_DARK, pass ? Palette.JADE : Palette.SEAL);
		}
		baris.jarak();
		baris.tambah(Component.translatable("kultivator.purba.calamity.source"), Palette.INK_FAINT);
		int top = Math.round(page.by() + radius() * 2.0F + 14.0F);
		baris.gambar(l, "bencana", x, top, x + w, page.by() + page.bh() - BUTTON_H - 4);
	}
}
