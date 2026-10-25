package dev.kultivator.client.kehancuran;

import dev.kultivator.client.Keys;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Teknik › Kehancuran › Murid (DESIGN 20.3.21, 43.5, G47; was the Sect Hall's "Segel Merah Murid" tablet): the panel of
 * the disciples' Destroyer arts, drawn by the Kehancuran page as its "murid" sub. Left (scrolls): the three locks
 * (server, the player's oath, the released disciples), the server's caps, the running job, the casts that can still be
 * restored and the last records. Right: the oath (the server answers with the heavy confirm) or revoking it (a medium
 * {@code SegelKonfirmasi}), Pulihkan, cancelling a destructive order on its way, and the warning. Data: the hall details
 * ({@code HallInfo.kehancuran}), refreshed while shown. Text at 1.0 (43.0.5).
 */
public final class RedSealPage {

	/** What the panel needs from the page that shows it. */
	public interface Tuan {
		void tambah(Widget widget);

		void konfirmasi(SegelKonfirmasi.Tingkat tingkat, Component teks, Runnable ya);
	}

	private static final int BUTTON_H = 14;
	private static final int GAP = 4;
	private static final String P = "kultivator.kehancuran.ui.";
	private static final String M = "kultivator.gui43.m02.";

	private final Font font = Minecraft.getInstance().font;
	private int x;
	private int y;
	private int w;
	private int h;
	private int columnW;
	private float scroll;
	private float contentHeight;

	/** Why the sub is locked, or null: only once a hall answer says Mode Kehancuran is off or there is no hall. */
	public static @Nullable Component kunci() {
		if (HallClient.version() <= 0L) {
			return null;
		}
		CompoundTag info = HallClient.info();
		if (!info.getBooleanOr("has", false)) {
			return Component.translatable(M + "kunci.aula");
		}
		return info.getCompoundOrEmpty("cfg").getBooleanOr("kehancuran", false) ? null : Component.translatable(M + "kunci.server");
	}

	/** Asks the server for fresh hall details (no viewer registration). */
	public static void minta() {
		if (Keys.connected()) {
			HallClient.send(Halls.OP_REFRESH, "");
		}
	}

	private static CompoundTag data() {
		return HallClient.info().getCompoundOrEmpty("kehancuran");
	}

	private static void send(String text) {
		Keys.send(new Payloads.Action(Actions.KEHANCURAN, 0, text));
	}

	public void build(Tuan tuan, int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		columnW = Math.max(100, Math.min(140, Math.round(w * 0.4F)));
		int bx = x + w - columnW;
		int top = y;
		TalismanButton oath = new TalismanButton(bx, top, columnW, BUTTON_H, () -> Component.translatable(data().getBooleanOr("sworn", false)
				? P + "revoke" : P + "oath"));
		oath.onPress(() -> {
			if (!data().getBooleanOr("sworn", false)) {
				send("oath");
			} else {
				tuan.konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(P + "revoke.tip"), () -> send("oath_revoke"));
			}
		});
		oath.enabledWhen(() -> data().getBooleanOr("server", false) && !readonly());
		oath.alasanWhen(() -> Component.translatable(readonly() ? M + "baca_saja" : M + "kunci.server"));
		oath.tooltip(() -> List.of(Component.translatable(data().getBooleanOr("sworn", false) ? P + "revoke" : P + "oath"),
				Component.translatable(data().getBooleanOr("sworn", false) ? P + "revoke.tip" : P + "oath.tip")));
		tuan.tambah(oath);

		TalismanButton restore = new TalismanButton(bx, top + BUTTON_H + GAP, columnW, BUTTON_H, () -> Component.translatable(P + "restore_button"));
		restore.enabledWhen(() -> !data().getListOrEmpty("undo").isEmpty() && !readonly());
		restore.urgentWhen(() -> !data().getListOrEmpty("undo").isEmpty());
		restore.alasanWhen(() -> Component.translatable(readonly() ? M + "baca_saja" : M + "pulihkan.kosong"));
		restore.onPress(() -> send("restore"));
		restore.tooltip(() -> List.of(Component.translatable(P + "restore_button"), Component.translatable(P + "restore_button.tip")));
		tuan.tambah(restore);

		TalismanButton cancel = new TalismanButton(bx, top + 2 * (BUTTON_H + GAP), columnW, BUTTON_H, () -> Component.translatable(P + "cancel_order"));
		cancel.onPress(() -> send("cancel"));
		cancel.enabledWhen(() -> !readonly());
		cancel.alasanWhen(() -> Component.translatable(M + "baca_saja"));
		cancel.tooltip(() -> List.of(Component.translatable(P + "cancel_order"), Component.translatable(P + "cancel_order.tip")));
		tuan.tambah(cancel);
	}

	/** A sect mate's hall: shown, never acted on. */
	private static boolean readonly() {
		return data().getBooleanOr("readonly", false);
	}

	public void draw(GuiGraphicsExtractor g) {
		int leftW = w - columnW - 8;
		float room = leftW - 6.0F;
		if (HallClient.version() <= 0L) {
			Brush.wrap(g, font, Component.translatable(M + "memuat"), x + 4.0F, y + 4.0F, room, Palette.INK_SOFT, 1.0F, false);
		} else {
			float maxScroll = Math.max(0.0F, contentHeight - h);
			scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
			g.enableScissor(x, y, x + leftW, y + h);
			float top = y - scroll;
			contentHeight = left(g, top, leftW, room) - top;
			g.disableScissor();
			if (maxScroll > 0.0F) {
				float bead = y + 3.0F + (h - 6.0F) * (scroll / maxScroll);
				g.fill(x + leftW - 1, y, x + leftW, y + h, Palette.CORD);
				Brush.disc(g, x + leftW - 0.5F, bead, 2.0F, Palette.JADE);
			}
		}

		// Right column: the warning under the buttons.
		int bx = x + w - columnW;
		int wy = y + 3 * (BUTTON_H + GAP) + 2;
		if (wy < y + h - 12) {
			g.fill(bx, wy, x + w, y + h, Palette.argb(0x40, Palette.PAPER_DEEP));
			Brush.frame(g, bx, wy, x + w, y + h, Palette.argb(0xA0, Palette.SEAL_DARK));
			g.enableScissor(bx + 1, wy + 1, x + w - 1, y + h - 1);
			Brush.wrap(g, font, Component.translatable(P + "page_warning").withStyle(ChatFormatting.DARK_RED), bx + 4.0F, wy + 4.0F, columnW - 8.0F,
					Palette.SEAL_DARK, 1.0F, false);
			g.disableScissor();
		}
	}

	/** The left column from {@code top}; returns the y after its last line. */
	private float left(GuiGraphicsExtractor g, float top, int leftW, float room) {
		CompoundTag data = data();
		CompoundTag cfg = data.getCompoundOrEmpty("cfg");
		float ty = heading(g, Component.translatable(P + "locks"), top, leftW);
		ListTag released = data.getListOrEmpty("released");
		ty = lock(g, ty, data.getBooleanOr("server", false), Component.translatable(P + "lock_server"), room);
		ty = lock(g, ty, data.getBooleanOr("sworn", false), Component.translatable(P + "lock_oath"), room);
		List<String> names = new ArrayList<>();
		for (Tag tag : released) {
			tag.asString().ifPresent(names::add);
		}
		ty = lock(g, ty, !names.isEmpty(), names.isEmpty() ? Component.translatable(P + "lock_none_released")
				: Component.translatable(P + "lock_released", String.join(", ", names)), room);

		ty += 3.0F;
		ty += Brush.wrap(g, font, Component.translatable(P + "caps", cfg.getIntOr("max_radius", 12), cfg.getIntOr("max_blocks", 4096),
				cfg.getIntOr("per_tick", 48), cfg.getIntOr("hall_safe", 64), cfg.getIntOr("player_safe", 24)), x + 4.0F, ty, room, Palette.INK_SOFT,
				1.0F, false) + 2.0F;
		Component drops = cfg.getIntOr("drops", 0) <= 0 ? Component.translatable(P + "no_drops") : Component.translatable(P + "drops", cfg.getIntOr("drops", 0));
		ty += Brush.wrap(g, font, drops, x + 4.0F, ty, room, Palette.INK_SOFT, 1.0F, false) + 3.0F;

		// The running job.
		int total = data.getIntOr("job_total", 0);
		if (total > 0) {
			float fraction = data.getIntOr("job_done", 0) / (float) Math.max(1, total);
			Component job = Component.translatable(data.getBooleanOr("job_mine", false) ? P + "job_mine" : P + "job_other", data.getIntOr("job_done", 0), total,
					data.getIntOr("queued", 0));
			ty += Brush.wrap(g, font, job, x + 4.0F, ty, room, Palette.SEAL_DARK, 1.0F, false) + 1.0F;
			Brush.rect(g, x + 4.0F, ty, x + 4.0F + room, ty + 3.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
			Brush.rect(g, x + 4.5F, ty + 0.5F, x + 4.5F + (room - 1.0F) * fraction, ty + 2.5F, Palette.DANGER);
			ty += 6.0F;
		}

		// Casts that can still be restored.
		ListTag undo = data.getListOrEmpty("undo");
		if (!undo.isEmpty()) {
			ty = heading(g, Component.translatable(P + "undo_heading"), ty, leftW);
			for (Tag tag : undo) {
				if (!(tag instanceof CompoundTag line)) {
					continue;
				}
				long left = HallClient.ticksLeft(line.getIntOr("seconds", 0) * 20L) / 20L;
				Component text = Component.translatable(P + "undo_line", line.getIntOr("blocks", 0), left / 60, String.format(java.util.Locale.ROOT, "%02d", left % 60));
				ty += Brush.wrap(g, font, text, x + 6.0F, ty, room - 2.0F, Palette.JADE_DARK, 1.0F, false) + 1.0F;
			}
			ty += 2.0F;
		}

		// The last records.
		ListTag log = data.getListOrEmpty("log");
		if (!log.isEmpty()) {
			ty = heading(g, Component.translatable(P + "log_heading"), ty, leftW);
			for (Tag tag : log) {
				if (!(tag instanceof CompoundTag line)) {
					continue;
				}
				Component shape = Component.translatable("kultivator.kehancuran.shape." + line.getStringOr("shape", "sphere"));
				Component text = Component.translatable(line.getBooleanOr("restore", false) ? P + "log_restore" : P + "log_cast", line.getLongOr("day", 0L),
						line.getStringOr("disciple", "?"), shape, line.getIntOr("x", 0), line.getIntOr("y", 0), line.getIntOr("z", 0),
						line.getIntOr("blocks", 0));
				ty += Brush.wrap(g, font, text, x + 6.0F, ty, room - 2.0F, Palette.INK_SOFT, 1.0F, false) + 1.0F;
			}
		}
		return ty + 4.0F;
	}

	/** A heading in seal red with a stamp before it and a gold rule under it; returns the y under it. */
	private float heading(GuiGraphicsExtractor g, Component text, float ty, int leftW) {
		float hx = x + 2.0F;
		float width = leftW - 4.0F;
		Brush.rect(g, hx, ty + 1.0F, hx + 4.0F, ty + 5.0F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(text), hx + 6.0F, ty, width - 6.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		Brush.rect(g, hx, ty + 10.0F, hx + width, ty + 10.6F, Palette.argb(0xB0, Palette.GOLD));
		return ty + 13.0F;
	}

	/** One lock line: a small padlock (red when open, green when closed) and its text. Returns the next line's top. */
	private float lock(GuiGraphicsExtractor g, float ty, boolean open, Component text, float room) {
		int color = open ? Palette.DANGER : Palette.JADE;
		Glyphs.lock(g, x + 8.0F, ty + 4.0F, 7.0F, color);
		Component state = Component.translatable(open ? "kultivator.kehancuran.lock.open" : "kultivator.kehancuran.lock.closed");
		Component line = Component.translatable(P + "lock_line", text, state.copy().withColor(color & 0xFFFFFF));
		return ty + Brush.wrap(g, font, line, x + 15.0F, ty, room - 11.0F, Palette.INK, 1.0F, false) + 2.0F;
	}

	/** Keyboard scroll of the left column (43.4.2): ↑↓ a line, PgUp/PgDn a page, Home/End the ends; true when used. */
	public boolean gulirTombol(int key) {
		float page = Math.max(20.0F, h - 20.0F);
		switch (key) {
			case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> scroll -= 14.0F;
			case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> scroll += 14.0F;
			case com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP -> scroll -= page;
			case com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN -> scroll += page;
			case com.mojang.blaze3d.platform.InputConstants.KEY_HOME -> scroll = 0.0F;
			case com.mojang.blaze3d.platform.InputConstants.KEY_END -> scroll = contentHeight;
			default -> {
				return false;
			}
		}
		// draw() clamps to the column.
		return true;
	}

	/** The wheel over the left column; true when used. */
	public boolean scrolled(double mouseX, double mouseY, double amount) {
		if (mouseX < x || mouseX >= x + w - columnW || mouseY < y || mouseY >= y + h) {
			return false;
		}
		scroll -= (float) amount * 14.0F;
		return true;
	}
}
