package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The warning card of a seal talisman of the Kuil Segel (DESIGN 34.5.6, {@code op: "warn"}): soot-black parchment with
 * the talisman itself painted down the middle, the seal's strength, the five talismans (torn ones grey), the demon king
 * sealed below with its Sumber line, what a pull costs and gives, and the temptation of Marshal Hong Xin. Only a seal held
 * for 1.5 s tears the talisman ({@code RNet.Action "cabut"}); Esc or "Biarkan" leaves it. The server checks everything
 * again (distance, state); the card only asks.
 */
public class JimatScreen extends Screen {

	private static final long HOLD_MILLIS = 1500L;
	private static final int PAPER_TOP = 0xFF2A1210;
	private static final int PAPER_BOTTOM = 0xFF140806;
	private static final int TEXT = 0xFFF0DCC8;
	private static final int TEXT_SOFT = 0xFFC8A890;
	private static final int RED = 0xFFD8261A;

	private final int n;
	private final int integrity;
	private final int pulled;
	private final int king;
	private final long openedAt = Util.getMillis();
	private boolean holding;
	private long holdStart;
	private boolean done;
	private long lastBeat;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private float sealX;
	private float sealY;
	private float sealR;
	private int leaveX0;
	private int leaveY0;
	private int leaveX1;
	private int leaveY1;

	public JimatScreen(CompoundTag tag) {
		super(Component.translatable("kultivator.reruntuhan.jimat.ui.title"));
		this.n = Mth.clamp(tag.getIntOr("n", 0), 0, 4);
		this.integrity = Mth.clamp(tag.getIntOr("integrity", 0), 0, 100);
		this.pulled = Mth.clamp(tag.getIntOr("pulled", 0), 0, 5);
		this.king = Mth.clamp(tag.getIntOr("king", 0), 0, 2);
	}

	@Override
	protected void init() {
		int w = Math.min(360, width - 16);
		int h = Math.min(250, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		sealR = Math.max(11.0F, Math.min(16.0F, h * 0.06F));
		sealX = x1 - 44.0F;
		sealY = y1 - sealR - 16.0F;
		leaveX0 = x0 + 18;
		leaveX1 = x0 + 98;
		leaveY1 = Math.round(sealY + 8.0F);
		leaveY0 = leaveY1 - 16;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xC0100404, 0xE0040000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		heartbeat(now);
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x70000000);
		g.fillGradient(x0, y0, x1, y1, PAPER_TOP, PAPER_BOTTOM);
		Brush.frame(g, x0, y0, x1, y1, 0xFF0A0202);
		Brush.frame(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, Palette.argb(0x90, 0x7A1A10));

		// the talisman painted down the left: yellow paper, red border, the seal script and 封
		int tw = 34;
		int tx0 = x0 + 16;
		int ty0 = y0 + 16;
		int ty1 = y1 - 52;
		float flutter = (float) Math.sin(now / 420.0D) * 1.5F;
		g.fill(tx0, ty0, tx0 + tw, ty1, Palette.TALISMAN);
		g.fillGradient(tx0, ty0 + (ty1 - ty0) / 2, tx0 + tw, ty1, Palette.TALISMAN, Palette.TALISMAN_LOW);
		Brush.frame(g, tx0, ty0, tx0 + tw, ty1, Palette.SEAL);
		Brush.frame(g, tx0 + 2, ty0 + 2, tx0 + tw - 2, ty1 - 2, Palette.argb(0x90, Palette.SEAL));
		Brush.fitCentered(g, font, Component.literal("封"), tx0 + tw / 2.0F, ty0 + 6.0F, tw - 6.0F, Palette.SEAL, 1.6F, 1.0F, false);
		for (int i = 0; i < 6; i++) {
			float yy = ty0 + 28.0F + i * ((ty1 - ty0 - 40.0F) / 6.0F);
			Brush.line(g, tx0 + tw / 2.0F - 5.0F + flutter * 0.2F, yy, tx0 + tw / 2.0F + 5.0F, yy + 4.0F, 1.4F, Palette.SEAL);
			Brush.line(g, tx0 + tw / 2.0F, yy - 2.0F, tx0 + tw / 2.0F, yy + 7.0F, 1.2F, Palette.SEAL);
		}

		// the text column
		float cx0 = tx0 + tw + 14.0F;
		float room = x1 - cx0 - 16.0F;
		float y = y0 + 12.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.reruntuhan.jimat.ui.title_n", n + 1)), cx0, y, room, 0xFFFFD27A, 1.1F, 0.6F, false);
		y += 14.0F;
		Brush.rect(g, cx0, y, x1 - 16.0F, y + 1.0F, Palette.argb(0xA0, 0x7A1A10));
		y += 5.0F;
		// the seal's strength and the five talismans
		Brush.fit(g, font, Component.translatable("kultivator.reruntuhan.jimat.ui.strength", integrity), cx0, y, room * 0.6F, TEXT, 0.8F, 0.55F, false);
		float bx0 = cx0;
		float bx1 = cx0 + room * 0.58F;
		float by = y + 11.0F;
		Brush.rect(g, bx0, by, bx1, by + 4.0F, 0xFF3A0A08);
		int barColor = Palette.mix(0xFFD8261A, 0xFFE8C050, integrity / 100.0F);
		Brush.rect(g, bx0, by, bx0 + (bx1 - bx0) * integrity / 100.0F, by + 4.0F, barColor);
		for (int i = 0; i < 5; i++) {
			float px = bx1 + 10.0F + i * 11.0F;
			boolean torn = i < pulled;
			boolean mine = i == n;
			int paper = torn ? 0xFF6A6058 : mine ? 0xFFFFE07A : Palette.TALISMAN;
			Brush.rect(g, px, y - 1.0F, px + 7.0F, y + 14.0F, paper);
			Brush.rect(g, px + 3.0F, y + 2.0F, px + 4.0F, y + 11.0F, torn ? 0xFF3A3430 : Palette.SEAL);
			if (mine) {
				Brush.frame(g, Math.round(px - 1.0F), Math.round(y - 2.0F), Math.round(px + 8.0F), Math.round(y + 15.0F), 0xFFFFFFFF);
			}
		}
		y += 22.0F;
		List<Component> lines = new ArrayList<>();
		Component kingName = Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[king]).withStyle(ChatFormatting.RED);
		lines.add(Component.translatable("kultivator.reruntuhan.jimat.ui.below", kingName));
		lines.add(Component.translatable("kultivator.reruntuhan.penjara." + RajaIblisEntity.IDS[king] + ".source").withStyle(ChatFormatting.ITALIC));
		lines.add(Component.translatable("kultivator.reruntuhan.jimat.ui.cost"));
		lines.add(Component.translatable("kultivator.reruntuhan.jimat.ui.break"));
		lines.add(Component.translatable("kultivator.reruntuhan.jimat.ui.tempt").withStyle(ChatFormatting.ITALIC));
		float bottom = leaveY0 - 6.0F;
		float scale = 0.72F;
		while (scale > 0.5F && height(lines, room, scale) > bottom - y) {
			scale -= 0.04F;
		}
		g.enableScissor(Math.round(cx0) - 2, Math.round(y), x1 - 6, Math.round(bottom));
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 1 ? TEXT_SOFT : i == 4 ? 0xFFFF8A6A : TEXT;
			y += Brush.wrap(g, font, lines.get(i), cx0, y, room, color, scale, false) + 3.0F;
		}
		g.disableScissor();

		// "Biarkan" (leave it) and the seal to hold
		boolean overLeave = mouseX >= leaveX0 && mouseX < leaveX1 && mouseY >= leaveY0 && mouseY < leaveY1;
		g.fillGradient(leaveX0, leaveY0, leaveX1, leaveY1, overLeave ? Palette.JADE_LIGHT : Palette.JADE, Palette.JADE_DARK);
		Brush.frame(g, leaveX0, leaveY0, leaveX1, leaveY1, Palette.JADE_DARK);
		Brush.fitCentered(g, font, Component.translatable("kultivator.reruntuhan.jimat.ui.leave"), (leaveX0 + leaveX1) / 2.0F, leaveY0 + 4.0F,
				leaveX1 - leaveX0 - 6, 0xFFFFFFFF, 0.85F, 0.55F, false);
		float progress = progress(now, mouseX, mouseY);
		boolean overSeal = overSeal(mouseX, mouseY);
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 260.0D);
		Brush.halo(g, sealX, sealY, sealR, 6.0F, 0xFF3A2A, (overSeal ? 0.85F : 0.35F) * (0.6F + 0.4F * pulse));
		Brush.disc(g, sealX, sealY, sealR + 2.0F, 0xFF0A0202);
		Brush.disc(g, sealX, sealY, sealR, RED);
		if (progress > 0.0F) {
			Brush.arc(g, sealX, sealY, sealR + 2.5F, sealR + 5.0F, 0.0F, progress, 0xFFFFD27A);
		}
		Brush.fitCentered(g, font, Component.literal("拔"), sealX, sealY - 5.0F, sealR * 1.6F, 0xFFF3D9C8, 1.2F, 0.8F, false);
		Brush.fitCentered(g, font, Component.translatable("kultivator.reruntuhan.jimat.ui.hold"), sealX, sealY + sealR + 4.0F, 90.0F, TEXT_SOFT, 0.7F,
				0.5F, false);
		if (progress >= 1.0F && !done) {
			confirm();
		}
	}

	private float height(List<Component> lines, float room, float scale) {
		float total = 0.0F;
		for (Component line : lines) {
			total += Brush.wrapHeight(font, line, room, scale) + 3.0F;
		}
		return total;
	}

	private void heartbeat(long now) {
		long interval = holding ? 600L : 1400L;
		if (now - lastBeat >= interval && now - openedAt > 150L) {
			lastBeat = now;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, holding ? 1.1F : 0.8F, 0.5F));
		}
	}

	private boolean overSeal(double mouseX, double mouseY) {
		double dx = mouseX - sealX;
		double dy = mouseY - sealY;
		return dx * dx + dy * dy <= (sealR + 3.0F) * (sealR + 3.0F);
	}

	private float progress(long now, int mouseX, int mouseY) {
		if (!holding) {
			return 0.0F;
		}
		if (!overSeal(mouseX, mouseY)) {
			holding = false;
			return 0.0F;
		}
		return Math.min(1.0F, (now - holdStart) / (float) HOLD_MILLIS);
	}

	private void confirm() {
		done = true;
		holding = false;
		ClientPlayNetworking.send(new RNet.Action("cabut", n, 0));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.0F));
		onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			if (mx >= leaveX0 && mx < leaveX1 && my >= leaveY0 && my < leaveY1) {
				UiSound.close();
				onClose();
				return true;
			}
			if (overSeal(mx, my)) {
				holding = true;
				holdStart = Util.getMillis();
				UiSound.ask();
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
		minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
