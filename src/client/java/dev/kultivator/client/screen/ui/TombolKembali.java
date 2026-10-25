package dev.kultivator.client.screen.ui;

import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single "back" control of the kit (DESIGN 43.7): "‹ Kembali" written in ink with a brush underline under the mouse.
 * It does exactly what Esc does at that point (close a detail, leave a child view, return to the conversation), so pages
 * give it the same action as their {@code mundur()}. The label can name where it goes ("‹ Kembali ke percakapan").
 */
public final class TombolKembali extends Widget {

	public static final String KUNCI_LANG = "kultivator.gui43.kit.kembali";

	private final Supplier<Component> label;

	public TombolKembali(int x, int y, int h, Supplier<Component> label, Runnable aksi) {
		super(x, y, lebar(label.get()), Math.max(TalismanButton.MIN_H, h));
		this.label = label;
		onPress(aksi);
	}

	/** "‹ Kembali" at (x, y). */
	public TombolKembali(int x, int y, Runnable aksi) {
		this(x, y, TalismanButton.MIN_H, () -> Component.translatable(KUNCI_LANG), aksi);
	}

	/** Width of the control for {@code label}. */
	public static int lebar(Component label) {
		return Math.max(TalismanButton.MIN_W, net.minecraft.client.Minecraft.getInstance().font.width(label) + 12);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		float glow = hover.get();
		int ink = Palette.mix(Palette.INK_SOFT, Palette.SEAL, glow);
		int dy = pressOffset(over);
		float cy = y + h / 2.0F + dy;
		Brush.line(g, x + 5.0F, cy - 3.0F, x + 2.0F, cy, 1.2F, ink);
		Brush.line(g, x + 2.0F, cy, x + 5.0F, cy + 3.0F, 1.2F, ink);
		Brush.fit(g, font, label.get(), x + 9.0F, cy - 4.0F, w - 9.0F, ink, 1.0F, 1.0F, false);
		if (glow > 0.05F) {
			Brush.rect(g, x + 9.0F, cy + 5.0F, x + 9.0F + (w - 10.0F) * glow, cy + 5.8F, Palette.argb(Math.round(200 * glow), Palette.SEAL));
		}
	}

	@Override
	protected void activate() {
		UiSound.close();
		if (action != null) {
			action.run();
		}
	}

	/** Per-key state of an immediate back control. */
	private static final class Imediat {
		Component label = Component.empty();
		@Nullable Runnable aksi;
		final TombolKembali widget = new TombolKembali(0, 0, TalismanButton.MIN_H, () -> label, () -> {
		});

		Imediat() {
			widget.onPress(() -> {
				if (aksi != null) {
					aksi.run();
				}
			});
		}
	}

	/** Draws an immediate back control; {@code label} null = "‹ Kembali". Returns its width. */
	public static int gambar(Lukis l, String kunci, int x, int y, @Nullable Component label, Runnable aksi) {
		Imediat t = l.fokusRing().simpan("kembali:" + kunci, Imediat::new);
		t.label = label != null ? label : Component.translatable(KUNCI_LANG);
		t.aksi = aksi;
		t.widget.x = x;
		t.widget.y = y;
		t.widget.w = lebar(t.label);
		l.widget(kunci, t.widget);
		return t.widget.w;
	}
}
