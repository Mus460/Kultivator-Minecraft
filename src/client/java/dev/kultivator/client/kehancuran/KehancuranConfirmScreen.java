package dev.kultivator.client.kehancuran;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "SEGEL MERAH — MODE KEHANCURAN" (DESIGN 20.3.21): the deliberate blood-red variant of the jade scroll, crimson
 * parchment with black ink and red seal stamps, and a slow heartbeat. It lists exactly what will happen (the shape and
 * its maximum size, the blocks per cast, the protected zones, the drops, the restore window), then asks for the
 * checkbox talisman "I understand blocks will be destroyed" and a seal that must be held for 5 s (the player's oath) or
 * 3 s (a disciple's red seal). Only then the confirming action goes to the server; Esc cancels.
 */
public class KehancuranConfirmScreen extends Screen {

	private static final int MAX_W = 360;
	private static final int MAX_H = 270;
	private static final int PAPER_TOP = 0xFF7E1812;
	private static final int PAPER_BOTTOM = 0xFF4E0C08;
	private static final int INK = 0xFF120404;
	private static final int INK_SOFT = 0xFF2E0B08;
	private static final int PARCHMENT_TEXT = 0xFFF3D9C8;
	private static final int STAMP = 0xFFD8261A;
	private static final long HEARTBEAT_MILLIS = 1500L;

	private final int target;
	private final Component name;
	private final CompoundTag path;
	private final @Nullable Screen back;
	private final long holdMillis;
	private final long openedAt;

	private boolean understood;
	private boolean holding;
	private long holdStart;
	private long lastBeat;
	private boolean done;

	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int boxX0;
	private int boxY0;
	private int boxX1;
	private int boxY1;
	private float sealX;
	private float sealY;
	private float sealR;

	/** {@code target} 0 = the player's oath, otherwise the disciple's entity id. */
	public KehancuranConfirmScreen(int target, Component name, CompoundTag path, @Nullable Screen back) {
		super(Component.translatable("kultivator.kehancuran.ui.title"));
		this.target = target;
		this.name = name;
		this.path = path;
		this.back = back;
		this.holdMillis = (target == 0 ? Kehancuran.OATH_HOLD_TICKS : Kehancuran.RELEASE_HOLD_TICKS) * 50L;
		this.openedAt = Util.getMillis();
	}

	@Override
	protected void init() {
		int w = Math.min(MAX_W, width - 16);
		int h = Math.min(MAX_H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		sealR = Math.max(12.0F, Math.min(18.0F, h * 0.07F));
		sealX = (x0 + x1) / 2.0F;
		sealY = y1 - sealR - 14.0F;
		boxY1 = Math.round(sealY - sealR - 8.0F);
		boxY0 = boxY1 - 16;
		boxX0 = x0 + 24;
		boxX1 = x1 - 24;
	}

	// ================================================================== text

	private List<Component> lines() {
		List<Component> lines = new ArrayList<>();
		String p = "kultivator.kehancuran.ui.";
		if (target == 0) {
			lines.add(Component.translatable(p + "oath_sub"));
			lines.add(Component.translatable(p + "shape_any", KehancuranClient.maxRadius));
		} else {
			lines.add(Component.translatable(p + "release_sub", name));
			String kind = path.getStringOr("art_kind", "");
			String art = path.getStringOr("art", "");
			Component artName = art.isEmpty() ? Component.literal("?") : Component.translatable("kultivator.dpath.art." + art);
			if ("trench".equals(kind)) {
				lines.add(Component.translatable(p + "shape_trench", artName, path.getIntOr("art_length", 0), 3, path.getIntOr("art_depth", 0)));
			} else if (!kind.isEmpty()) {
				lines.add(Component.translatable(p + "shape", artName, Component.translatable("kultivator.kehancuran.shape." + kind),
						path.getIntOr("art_radius", 0)));
			} else {
				lines.add(Component.translatable(p + "shape_any", KehancuranClient.maxRadius));
			}
		}
		lines.add(Component.translatable(p + "blocks", KehancuranClient.maxBlocks, KehancuranClient.perTick));
		lines.add(Component.translatable(p + "zones", KehancuranClient.hallSafe, KehancuranClient.playerSafe));
		lines.add(Component.translatable(p + "never"));
		lines.add(KehancuranClient.dropsPct <= 0 ? Component.translatable(p + "no_drops") : Component.translatable(p + "drops", KehancuranClient.dropsPct));
		lines.add(KehancuranClient.dropsPct <= 0 && KehancuranClient.undoMinutes > 0
				? Component.translatable(p + "restore", KehancuranClient.undoMinutes) : Component.translatable(p + "no_restore"));
		lines.add(Component.translatable(p + "order", KehancuranClient.warningSeconds));
		lines.add(Component.translatable(p + "close"));
		return lines;
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xD0140202, 0xE0050000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		heartbeat(now);

		// Crimson parchment with a black frame and a seal stamp in each corner.
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x70000000);
		g.fillGradient(x0, y0, x1, y1, PAPER_TOP, PAPER_BOTTOM);
		Brush.frame(g, x0, y0, x1, y1, INK);
		Brush.frame(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, Palette.argb(0xA0, 0x1A0000));
		for (int i = 0; i < 4; i++) {
			int sx = i % 2 == 0 ? x0 + 6 : x1 - 16;
			int sy = i < 2 ? y0 + 6 : y1 - 16;
			stamp(g, sx, sy, 10);
		}

		// Title and the list.
		float cx = (x0 + x1) / 2.0F;
		float room = x1 - x0 - 44.0F;
		Brush.fitCentered(g, font, Brush.bold(title), cx, y0 + 9.0F, room, INK, 1.1F, 0.6F, false);
		Brush.rect(g, x0 + 22.0F, y0 + 21.0F, x1 - 22.0F, y0 + 22.0F, Palette.argb(0xC0, INK));
		List<Component> lines = lines();
		float textTop = y0 + 27.0F;
		float textBottom = boxY0 - 4.0F;
		float scale = 0.72F;
		while (scale > 0.5F && height(lines, room, scale) > textBottom - textTop) {
			scale -= 0.04F;
		}
		g.enableScissor(x0 + 4, Math.round(textTop), x1 - 4, Math.round(textBottom));
		float ty = textTop;
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? INK : INK_SOFT;
			Brush.rect(g, x0 + 16.0F, ty + 2.5F * scale, x0 + 19.0F, ty + 5.5F * scale, STAMP);
			ty += Brush.wrap(g, font, lines.get(i), x0 + 22.0F, ty, room, color, scale, false) + 2.0F;
		}
		g.disableScissor();

		// The checkbox talisman.
		boolean overBox = mouseX >= boxX0 && mouseX < boxX1 && mouseY >= boxY0 && mouseY < boxY1;
		g.fillGradient(boxX0, boxY0, boxX1, boxY1, overBox ? 0xFFF7DFA0 : Palette.TALISMAN, Palette.TALISMAN_LOW);
		Brush.frame(g, boxX0, boxY0, boxX1, boxY1, understood ? STAMP : Palette.TALISMAN_EDGE);
		int check = boxY1 - boxY0 - 6;
		g.fill(boxX0 + 3, boxY0 + 3, boxX0 + 3 + check, boxY0 + 3 + check, 0xFFFFF4D6);
		Brush.frame(g, boxX0 + 3, boxY0 + 3, boxX0 + 3 + check, boxY0 + 3 + check, INK);
		if (understood) {
			float bx = boxX0 + 3.0F;
			float by = boxY0 + 3.0F;
			Brush.line(g, bx + 2.0F, by + check * 0.55F, bx + check * 0.42F, by + check - 2.0F, 1.6F, STAMP);
			Brush.line(g, bx + check * 0.42F, by + check - 2.0F, bx + check - 1.5F, by + 1.5F, 1.6F, STAMP);
		}
		Brush.fit(g, font, Component.translatable("kultivator.kehancuran.ui.understand"), boxX0 + check + 8.0F, boxY0 + 4.0F,
				boxX1 - boxX0 - check - 12.0F, INK, 0.85F, 0.55F, false);

		// The seal to hold.
		float progress = progress(now, mouseX, mouseY);
		boolean overSeal = overSeal(mouseX, mouseY);
		if (understood) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
			Brush.halo(g, sealX, sealY, sealR, 6.0F, 0xFF3A2A, (overSeal ? 0.8F : 0.4F) * (0.6F + 0.4F * pulse));
		}
		Brush.disc(g, sealX, sealY, sealR + 2.0F, INK);
		Brush.disc(g, sealX, sealY, sealR, understood ? STAMP : Palette.grey(STAMP, 0.7F));
		if (progress > 0.0F) {
			Brush.arc(g, sealX, sealY, sealR + 2.5F, sealR + 5.0F, 0.0F, progress, 0xFFFFD27A);
		}
		Brush.ring(g, sealX, sealY, sealR * 0.62F, sealR * 0.7F, Palette.argb(0xC0, PARCHMENT_TEXT));
		Brush.rect(g, sealX - sealR * 0.42F, sealY - 0.6F, sealX + sealR * 0.42F, sealY + 0.6F, Palette.argb(0xD0, PARCHMENT_TEXT));
		Brush.rect(g, sealX - 0.6F, sealY - sealR * 0.42F, sealX + 0.6F, sealY + sealR * 0.42F, Palette.argb(0xD0, PARCHMENT_TEXT));
		long seconds = (holdMillis + 999L) / 1000L;
		Component hint = understood ? Component.translatable("kultivator.kehancuran.ui.hold", seconds)
				: Component.translatable("kultivator.kehancuran.ui.check_first");
		Brush.fitCentered(g, font, hint, sealX, sealY + sealR + 4.0F, x1 - x0 - 30.0F, PARCHMENT_TEXT, 0.75F, 0.5F, false);
		Brush.fit(g, font, Component.translatable("kultivator.kehancuran.ui.esc"), x0 + 8.0F, y1 - 10.0F, 80.0F, Palette.argb(0xC0, PARCHMENT_TEXT), 0.6F,
				0.45F, false);

		if (progress >= 1.0F && !done) {
			confirm();
		}
	}

	private float height(List<Component> lines, float room, float scale) {
		float total = 0.0F;
		for (Component line : lines) {
			total += Brush.wrapHeight(font, line, room, scale) + 2.0F;
		}
		return total;
	}

	/** A small red seal stamp with a carved knot. */
	private static void stamp(GuiGraphicsExtractor g, int x, int y, int size) {
		g.fill(x, y, x + size, y + size, STAMP);
		Brush.frame(g, x + 1, y + 1, x + size - 1, y + size - 1, Palette.argb(0xB0, 0xF3D9C8));
		float mid = size / 2.0F;
		Brush.rect(g, x + 2.5F, y + mid - 0.5F, x + size - 2.5F, y + mid + 0.5F, Palette.argb(0xC8, 0xF3D9C8));
		Brush.rect(g, x + mid - 0.5F, y + 2.5F, x + mid + 0.5F, y + mid, Palette.argb(0xC8, 0xF3D9C8));
	}

	/** The slow heartbeat while the warning is open (faster while the seal is held). */
	private void heartbeat(long now) {
		long interval = holding ? HEARTBEAT_MILLIS / 2 : HEARTBEAT_MILLIS;
		if (now - lastBeat >= interval && now - openedAt > 150L) {
			lastBeat = now;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, holding ? 1.1F : 0.8F, 0.6F));
		}
	}

	// ================================================================== the hold

	private boolean overSeal(double mouseX, double mouseY) {
		double dx = mouseX - sealX;
		double dy = mouseY - sealY;
		return dx * dx + dy * dy <= (sealR + 3.0F) * (sealR + 3.0F);
	}

	/** 0..1 of the hold; it resets when the mouse leaves the seal or the button is let go. */
	private float progress(long now, int mouseX, int mouseY) {
		if (!holding) {
			return 0.0F;
		}
		if (!understood || !overSeal(mouseX, mouseY)) {
			holding = false;
			return 0.0F;
		}
		return Math.min(1.0F, (now - holdStart) / (float) holdMillis);
	}

	private void confirm() {
		done = true;
		holding = false;
		Keys.send(new Payloads.Action(Actions.KEHANCURAN, target, target == 0 ? "oath_confirm" : "release_confirm"));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.7F, 0.8F));
		onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			if (mx >= boxX0 && mx < boxX1 && my >= boxY0 && my < boxY1) {
				understood = !understood;
				UiSound.toggle(understood);
				return true;
			}
			if (overSeal(mx, my)) {
				if (understood) {
					holding = true;
					holdStart = Util.getMillis();
					UiSound.ask();
				} else {
					UiSound.denied();
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding) {
			holding = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
