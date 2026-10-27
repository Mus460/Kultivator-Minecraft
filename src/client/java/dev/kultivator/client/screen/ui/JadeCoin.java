package dev.kultivator.client.screen.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A round jade coin with a square hole, used as an on/off toggle: bright green jade with a gold rim when on, dull
 * grey-green when off. The label sits to its right (1.0, cut with "…"). The single toggle of the kit (DESIGN 43.7);
 * confirming a switch is {@link SegelKonfirmasi}'s job.
 */
public class JadeCoin extends Widget {

	private final Supplier<Component> label;
	private final BooleanSupplier on;
	private final Eased lit = new Eased(10.0F);

	public JadeCoin(int x, int y, int w, int h, Supplier<Component> label, BooleanSupplier on) {
		super(x, y, Math.max(TalismanButton.MIN_W, w), Math.max(TalismanButton.MIN_H, h));
		this.label = label;
		this.on = on;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		boolean state = on.getAsBoolean();
		float t = lit.to(state ? 1.0F : 0.0F, seconds);
		float glow = isEnabled() ? hover.get() : 0.0F;
		float r = h / 2.0F - 0.5F;
		float cx = x + h / 2.0F;
		float cy = y + h / 2.0F + pressOffset(over);

		if (glow > 0.02F) {
			Brush.halo(g, cx, cy, r, 3.0F, 0x9CFFD0, glow);
		}
		Brush.disc(g, cx + 0.5F, cy + 1.0F, r, 0x40000000);
		int rim = Palette.mix(Palette.JADE_DULL, Palette.GOLD, t);
		Brush.disc(g, cx, cy, r, Palette.darken(rim, 0.25F));
		Brush.disc(g, cx, cy, r - 0.7F, rim);
		int faceTop = Palette.mix(0xFFA9B5AE, Palette.JADE_LIGHT, t);
		int faceBottom = Palette.mix(Palette.JADE_DULL, Palette.JADE_DARK, t);
		float face = r - 1.6F;
		Brush.band(g, cx, cy, face, -face, face, Palette.lighten(faceTop, 0.1F * glow), faceBottom);
		// A raised inner rim, like a cast coin.
		Brush.ring(g, cx, cy, face * 0.62F, face * 0.62F + 0.6F, Palette.argb(Math.round(60 + 50 * t), 0xFFFFFF));
		float hole = Math.max(1.0F, face * 0.32F);
		Brush.rect(g, cx - hole, cy - hole, cx + hole, cy + hole, state ? Palette.GOLD_LIGHT : Palette.PAPER_SHADE);
		if (t > 0.05F) {
			Brush.rect(g, cx - hole - 0.6F, cy - hole - 0.6F, cx + hole + 0.6F, cy - hole, Palette.fade(Palette.GOLD, t));
		}

		Component text = label.get();
		float tx = x + h + 4.0F;
		float room = x + w - tx;
		Brush.fit(g, font, text, tx, y + (h - 8.0F) / 2.0F, room, isEnabled() ? Palette.INK : Palette.INK_FAINT, 1.0F, 1.0F, false);
	}

	@Override
	protected void activate() {
		UiSound.toggle(!on.getAsBoolean());
		if (action != null) {
			action.run();
		}
	}
}
