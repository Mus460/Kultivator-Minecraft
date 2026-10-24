package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * {@code TabLentera} (DESIGN 38.U.2): the vertical tablet column on the left, one paper lantern per tab. The active lantern
 * is lit, glows and juts out 3 px; hovered lanterns warm up; a locked tab shows a grey lantern with a padlock and its reason
 * as tooltip (and as the screen message when clicked). Number keys 1-9 pick the visible tabs in order. In the narrow
 * layout (column 28 px) the label is a tooltip and the active label is written under the panel title; in the wide layout
 * the label is written beside the lantern.
 */
public final class TabLentera {

	/** One tab: id (sheet tab id), label, icon slot inside the lantern, lock reason (null = open). */
	public record Tab(String id, Component label, int ikon, @Nullable Component kunci) {
	}

	public static final int LEBAR = 28;
	public static final int LEBAR_LABEL = 84;
	private static final int TINGGI = 30;

	private final Map<String, Eased> nyala = new HashMap<>();
	private List<Tab> tabs = new ArrayList<>();

	public void setTabs(List<Tab> tabs) {
		this.tabs = tabs;
	}

	public List<Tab> tabs() {
		return tabs;
	}

	public @Nullable Tab tab(int index) {
		return index >= 0 && index < tabs.size() ? tabs.get(index) : null;
	}

	public @Nullable Tab cari(String id) {
		for (Tab tab : tabs) {
			if (tab.id().equals(id)) {
				return tab;
			}
		}
		return null;
	}

	/** Smallest lantern: nine tabs still fit the 126 px content of a 1280 x 720 window at GUI scale 4. */
	private static final int TINGGI_MIN = 14;

	/** Height of the column for the current tabs (lanterns squeeze below 30 px when the panel is short). */
	public static int tinggiPer(int tersedia, int jumlah) {
		return jumlah <= 0 ? TINGGI : Math.max(TINGGI_MIN, Math.min(TINGGI, tersedia / jumlah));
	}

	/**
	 * Draws the column at (x, y) down to {@code y1}; {@code label} draws the names beside the lanterns. {@code pilih}
	 * receives a click on an open tab; a locked tab reports through {@code ditolak}.
	 */
	public void gambar(Lukis l, int x, int y, int y1, boolean label, String aktif, Consumer<String> pilih, Consumer<Component> ditolak) {
		int per = tinggiPer(y1 - y, tabs.size());
		int w = label ? LEBAR + LEBAR_LABEL : LEBAR;
		// the tablet rail: a lacquered rod the lanterns hang from
		l.g.fillGradient(x + 13, y - 2, x + 15, Math.min(y1, y + per * tabs.size()), Palette.ROD_LIGHT, Palette.ROD_DARK);
		for (int i = 0; i < tabs.size(); i++) {
			Tab tab = tabs.get(i);
			int ty = y + i * per;
			boolean on = tab.id().equals(aktif);
			boolean locked = tab.kunci() != null;
			boolean over = l.di(x, ty, x + w, ty + per);
			Eased e = nyala.computeIfAbsent(tab.id(), k -> new Eased(10.0F));
			float glow = e.to(on ? 1.0F : over ? 0.45F : 0.0F, l.detik);
			int jut = on ? 3 : over ? 1 : 0;
			int lw = Math.round((per - 2) * 24.0F / 28.0F);
			int lx = x + 2 + jut + (24 - lw) / 2;
			int ly = ty + 1;
			if (glow > 0.05F && !locked) {
				int gs = per + 10;
				IkonTempat.cahaya(l.g, lx + lw / 2 - gs / 2, ly + (per - 2) / 2 - gs / 2, gs, gs, Palette.argb(Math.round(220 * glow), 0xFFFFFF));
			}
			int state = locked ? 3 : on ? 2 : over ? 1 : 0;
			IkonTempat.lentera(l.g, state, lx, ly, lw, per - 2, 0xFFFFFFFF);
			int is = Math.max(8, Math.round(lw * 0.5F));
			int ix = lx + (lw - is) / 2;
			int iy = ly + Math.round((per - 2) * 0.47F) - is / 2;
			IkonTempat.gambar(l.g, tab.ikon(), ix, iy, is, locked ? 0xA0B0B0B0 : on ? 0xFFFFFFFF : 0xD0FFFFFF);
			if (locked) {
				int gs = per < 20 ? 6 : 8;
				IkonTempat.gambar(l.g, dev.kultivator.dunia.tempat.ui.LembarKunci.Ikon.GEMBOK, lx + lw - gs + 1, ly + per - gs - 3, gs, 0xFFFFFFFF);
			}
			if (i < 9) {
				Brush.text(l.g, l.font, Component.literal(Integer.toString(i + 1)), x + 1, ty + 1, on ? TempatWarna.EMAS_TERANG
						: Palette.argb(0xB0, l.gaya.teksSamar & 0xFFFFFF), 0.6F, true);
			}
			if (label) {
				int tc = locked ? TempatWarna.terkunci(l.gaya.teks) : on ? l.gaya.aksen : l.gaya.teksLemah;
				Component name = on ? Brush.bold(tab.label()) : tab.label();
				Brush.fit(l.g, l.font, name, x + LEBAR + 3 + jut, ty + (per - 8) / 2.0F, LEBAR_LABEL - 6, tc, 1.0F, 0.6F, false);
			}
			final String id = tab.id();
			String kunci = "tab:" + id;
			l.zona(kunci, x, ty, x + w, ty + per, () -> {
				if (tab.kunci() != null) {
					ditolak.accept(tab.kunci());
				} else if (!id.equals(aktif)) {
					pilih.accept(id);
				}
			}, !locked, tab.kunci(), true);
			if (over) {
				List<Component> tip = new ArrayList<>(2);
				if (!label) {
					tip.add(tab.label());
				}
				if (locked) {
					tip.add(tab.kunci().copy().withColor(0xFFFF8A7A));
				}
				l.tip(tip);
			}
			l.fokusCincin(kunci, x + 1, ty, x + w - 1, ty + per - 1);
		}
	}
}
