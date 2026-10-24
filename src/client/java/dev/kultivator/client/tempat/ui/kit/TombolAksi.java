package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.TalismanButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * {@code TombolAksi} (DESIGN 38.U.2): the action button of the kit, a {@link TalismanButton} (paper strip with a red seal,
 * hover glow, 1 px press, urgent pulse) driven immediately: each frame the screen calls {@link #gambar} with the current
 * label, state and action; the talisman instance is kept per key so its hover easing survives. A disabled button keeps its
 * reason as tooltip and, when clicked or activated with Enter, shows the reason as the screen message. Focusable (gold
 * focus ring), Enter activates.
 */
public final class TombolAksi {

	private static final class Tombol {
		final TalismanButton widget;
		Component label = Component.empty();
		boolean aktif = true;
		boolean mendesak;
		boolean terpilih;
		@Nullable Runnable aksi;

		Tombol() {
			widget = new TalismanButton(0, 0, 10, 10, () -> label);
			widget.enabledWhen(() -> aktif);
			widget.urgentWhen(() -> mendesak);
			widget.stampedWhen(() -> terpilih);
			widget.onPress(() -> {
				if (aksi != null) {
					aksi.run();
				}
			});
		}
	}

	private final Map<String, Tombol> tombol = new HashMap<>();

	/** Draws button {@code kunci}; returns true when the mouse is over it. */
	public boolean gambar(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			@Nullable Runnable aksi) {
		return gambar(l, kunci, x, y, w, h, label, aktif, alasan, aksi, false, false);
	}

	public boolean gambar(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			@Nullable Runnable aksi, boolean mendesak, boolean terpilih) {
		Tombol t = tombol.computeIfAbsent(kunci, k -> new Tombol());
		t.label = label;
		t.aktif = aktif;
		t.mendesak = mendesak;
		t.terpilih = terpilih;
		t.aksi = aksi;
		t.widget.x = x;
		t.widget.y = y;
		t.widget.w = w;
		t.widget.h = h;
		Lukis.Zona z = l.zona(kunci, x, y, x + w, y + h, aksi, aktif, alasan, true);
		if (z != null) {
			z.widget = t.widget;
		}
		t.widget.render(l.g, l.font, l.mx, l.my, l.detik);
		boolean over = l.di(x, y, x + w, y + h);
		if (over) {
			List<Component> lines = new ArrayList<>(2);
			if (!aktif && alasan != null) {
				lines.add(alasan);
			}
			if (!lines.isEmpty()) {
				l.tip(lines);
			}
		}
		l.fokusCincin(kunci, x, y, x + w, y + h);
		return over;
	}

	/** Width that fits {@code label} at body size plus the seal and margins (clamped). */
	public static int lebar(Lukis l, Component label, int min, int max) {
		return Math.max(min, Math.min(max, l.font.width(label) + 24));
	}
}
