package dev.kultivator.client.abadi;

import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Keabadian › Alam Abadi (DESIGN 26.10, 43.5; owned by P9-langit, a page since 43 M16). Before the ascension: the gate
 * checklist (Dewa Abadi Puncak, ★9, the DESIGN 17 ascension, 9 Esensi Energi Makam, 1 Supreme spirit stone, no cooldown),
 * the reason it is locked and a "Mulai Kenaikan" talisman. After it: the Esensi Abadi bar, Batu carried, mined today,
 * conversions left, the star discount, the crossing cooldown, a [Lintas Alam] talisman and a [Konversi] talisman. Lines
 * scroll in the kit list at 1.0 (43.0.5). Reads {@code AbadiClient.alam()}; asks for it while shown (43.0.12).
 */
public class AlamAbadiPage extends Page {

	private static final int BUTTON_H = 14;

	private final AbadiClient.Baris baris = new AbadiClient.Baris();
	private @Nullable TalismanButton ascend;
	private @Nullable TalismanButton cross;
	private @Nullable TalismanButton convert;

	public AlamAbadiPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.alam");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.alam.short");
	}

	@Override
	public int glyph() {
		return Glyphs.PATH;
	}

	@Override
	public String helpTopic() {
		return "alam_abadi";
	}

	private static CompoundTag page() {
		return AbadiClient.alam();
	}

	private static boolean ascended() {
		return page().getBooleanOr("ascended", false);
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		Keys.send(new Payloads.Action(Actions.ABADI, 0, "sync"));
	}

	@Override
	protected void build() {
		int by = y + h - BUTTON_H;
		int bw = Math.max(TalismanButton.MIN_W, Math.min(130, (w - 4) / 2));
		ascend = add(new TalismanButton(x + w - bw, by, bw, BUTTON_H, () -> Component.translatable("kultivator.abadi.page.ascend")));
		ascend.enabledWhen(() -> Keys.connected() && page().getBooleanOr("ready", false));
		ascend.onPress(() -> send(Actions.ABADI, 0, "naik"));
		ascend.tooltip(() -> {
			Component why = page().read("why", ComponentSerialization.CODEC).orElse(null);
			return why == null ? List.of(Component.translatable("kultivator.abadi.page.ascend_tip")) : List.of(why);
		});
		cross = add(new TalismanButton(x + w - bw, by, bw, BUTTON_H, () -> Component.translatable("kultivator.abadi.page.cross")));
		cross.enabledWhen(() -> Keys.connected() && page().getBooleanOr("talisman", false) && page().getLongOr("cross_cd", 0L) <= 0L);
		cross.onPress(() -> send(Actions.ABADI, 0, "lintas"));
		cross.tooltip(() -> List.of(Component.translatable(page().getBooleanOr("talisman", false) ? "kultivator.abadi.page.cross_tip"
				: "kultivator.abadi.cross.no_talisman")));
		convert = add(new TalismanButton(x, by, bw, BUTTON_H, () -> Component.translatable("kultivator.abadi.page.convert")));
		convert.enabledWhen(() -> Keys.connected() && page().getIntOr("conv", 0) < 100 && page().getIntOr("conv_left", 0) > 0
				&& page().getIntOr("stones", 0) > 0);
		convert.onPress(() -> send(Actions.ABADI, 0, "konversi:1"));
		convert.tooltip(() -> List.of(Component.translatable("kultivator.abadi.page.convert_tip", page().getIntOr("conv_left", 0))));
	}

	@Override
	protected boolean visible(Widget widget) {
		if (page().isEmpty()) {
			return false;
		}
		if (widget == ascend) {
			return !ascended();
		}
		if (widget == cross || widget == convert) {
			return ascended();
		}
		return true;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag page = page();
		if (page.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable("kultivator.abadi.page.loading"), null, null, x, y, x + w, y + h);
			return;
		}
		int bottom = y + h - BUTTON_H - 4;
		baris.mulai(w);
		int top = y;
		if (!page.getBooleanOr("ascended", false)) {
			gate(page);
		} else {
			int conv = Math.max(0, Math.min(100, page.getIntOr("conv", 0)));
			Bilah.baris(l, Component.translatable("kultivator.gui43.m16.alam.esensi"), conv, 100.0F, x, y, x + w,
					Component.literal(conv + " %"), 0xFFFFE9A8);
			top += 12;
			status(page);
		}
		baris.gambar(l, "alam_abadi", x, top, x + w, bottom);
	}

	private void gate(CompoundTag page) {
		baris.tambah(Brush.bold(Component.translatable("kultivator.abadi.page.gate_title")), Palette.SEAL_DARK);
		baris.tambah(Component.translatable("kultivator.abadi.page.gate_intro"), Palette.INK_SOFT);
		baris.jarak();
		for (Tag tag : page.getListOrEmpty("gate")) {
			if (tag instanceof CompoundTag line) {
				boolean ok = line.getBooleanOr("ok", false);
				Component text = Component.translatable(line.getStringOr("key", ""), line.getIntOr("have", 0), line.getIntOr("need", 0));
				baris.tambah(text, ok ? Palette.JADE_DARK : Palette.SEAL_DARK, ok ? Palette.JADE : Palette.DANGER);
			}
		}
		Component why = page.read("why", ComponentSerialization.CODEC).orElse(null);
		if (why != null) {
			baris.jarak();
			baris.tambah(why, Palette.SEAL_DARK);
		}
		baris.jarak();
		baris.tambah(Component.translatable("kultivator.abadi.page.gate_source"), Palette.INK_FAINT);
	}

	private void status(CompoundTag page) {
		baris.tambah(Component.translatable(page.getBooleanOr("in_realm", false) ? "kultivator.abadi.page.here" : "kultivator.abadi.page.away"),
				Palette.INK_SOFT);
		baris.jarak();
		baris.tambah(Component.translatable("kultivator.abadi.page.stones", page.getIntOr("stones", 0)), Palette.INK);
		baris.tambah(Component.translatable("kultivator.abadi.page.mined", page.getIntOr("mined", 0), page.getIntOr("mined_cap", 16)), Palette.INK);
		baris.tambah(Component.translatable("kultivator.abadi.page.conv_left", page.getIntOr("conv_left", 0)), Palette.INK);
		baris.tambah(Component.translatable("kultivator.abadi.page.discount", page.getIntOr("discount", 0)), Palette.INK);
		long cd = page.getLongOr("cross_cd", 0L);
		baris.tambah(cd > 0L ? Component.translatable("kultivator.abadi.page.cross_cd", Brush.waktu(cd - AbadiClient.age()))
				: Component.translatable(page.getBooleanOr("talisman", false) ? "kultivator.abadi.page.cross_ready" : "kultivator.abadi.page.no_talisman"),
				cd > 0L ? Palette.WARN : Palette.INK);
		if (page.getBooleanOr("fallback", false)) {
			baris.jarak();
			baris.tambah(Component.translatable("kultivator.abadi.page.fallback"), Palette.WARN);
		}
	}
}
