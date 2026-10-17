package dev.kultivator.client.kehancuran;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hall.HallPage;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The Segel Merah tablet of the Sect Hall scroll (DESIGN 20.3.21): only shown while the server allows Mode Kehancuran.
 * Left: the three locks (server, the player's oath, the released disciples), the server's caps, the running job, the
 * casts that can still be restored and the last records. Right: the oath (opens the blood-red warning) or revoking it,
 * Pulihkan (restore) and cancelling a destructive order on its way.
 */
public class RedSealPage extends HallPage {

	private static final int BUTTON_H = 18;
	private static final long REVOKE_MILLIS = 4000L;
	private static final String P = "kultivator.kehancuran.ui.";

	private int columnW;
	private long revokeArmedUntil;

	public RedSealPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.red_seal");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.red_seal.short");
	}

	@Override
	public int icon() {
		return Glyphs.SEAL;
	}

	/** Only while the server allows Mode Kehancuran ({@code HallInfo.cfg.kehancuran}, DESIGN 20.3.21). */
	@Override
	public boolean shown() {
		return info().getCompoundOrEmpty("cfg").getBooleanOr("kehancuran", false);
	}

	private CompoundTag data() {
		return info().getCompoundOrEmpty("kehancuran");
	}

	@Override
	protected void build() {
		columnW = Math.max(100, Math.min(140, Math.round(w * 0.4F)));
		int bx = x + w - columnW;
		int top = y + 16;
		int gap = 5;
		TalismanButton oath = new TalismanButton(bx, top, columnW, BUTTON_H, () -> Component.translatable(data().getBooleanOr("sworn", false)
				? revokeArmed() ? P + "revoke_confirm" : P + "revoke" : P + "oath"));
		oath.urgentWhen(this::revokeArmed);
		oath.onPress(() -> {
			if (!data().getBooleanOr("sworn", false)) {
				send(Actions.KEHANCURAN, 0, "oath");
			} else if (revokeArmed()) {
				revokeArmedUntil = 0L;
				send(Actions.KEHANCURAN, 0, "oath_revoke");
			} else {
				revokeArmedUntil = Util.getMillis() + REVOKE_MILLIS;
				UiSound.ask();
			}
		});
		oath.enabledWhen(() -> data().getBooleanOr("server", false) && !readonly());
		oath.tooltip(() -> List.of(Component.translatable(data().getBooleanOr("sworn", false) ? P + "revoke" : P + "oath"),
				Component.translatable(data().getBooleanOr("sworn", false) ? P + "revoke.tip" : P + "oath.tip")));
		add(oath);

		TalismanButton restore = new TalismanButton(bx, top + BUTTON_H + gap, columnW, BUTTON_H, () -> Component.translatable(P + "restore_button"));
		restore.enabledWhen(() -> !data().getListOrEmpty("undo").isEmpty() && !readonly());
		restore.urgentWhen(() -> !data().getListOrEmpty("undo").isEmpty());
		restore.onPress(() -> send(Actions.KEHANCURAN, 0, "restore"));
		restore.tooltip(() -> List.of(Component.translatable(P + "restore_button"), Component.translatable(P + "restore_button.tip")));
		add(restore);

		TalismanButton cancel = new TalismanButton(bx, top + 2 * (BUTTON_H + gap), columnW, BUTTON_H, () -> Component.translatable(P + "cancel_order"));
		cancel.onPress(() -> send(Actions.KEHANCURAN, 0, "cancel"));
		cancel.enabledWhen(() -> !readonly());
		cancel.tooltip(() -> List.of(Component.translatable(P + "cancel_order"), Component.translatable(P + "cancel_order.tip")));
		add(cancel);
	}

	/** A sect mate's hall: shown, never acted on. */
	private boolean readonly() {
		return data().getBooleanOr("readonly", false);
	}

	private boolean revokeArmed() {
		return Util.getMillis() <= revokeArmedUntil;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = data();
		CompoundTag cfg = data.getCompoundOrEmpty("cfg");
		int leftW = w - columnW - 8;
		float room = leftW - 6.0F;
		heading(g, Component.translatable(P + "locks"), x + 2.0F, y, leftW - 2.0F, 0.8F);
		float ty = y + 14.0F;
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
				0.6F, false) + 2.0F;
		Component drops = cfg.getIntOr("drops", 0) <= 0 ? Component.translatable(P + "no_drops") : Component.translatable(P + "drops", cfg.getIntOr("drops", 0));
		ty += Brush.wrap(g, font, drops, x + 4.0F, ty, room, Palette.INK_SOFT, 0.6F, false) + 3.0F;

		// The running job.
		int total = data.getIntOr("job_total", 0);
		if (total > 0) {
			float fraction = data.getIntOr("job_done", 0) / (float) Math.max(1, total);
			Component job = Component.translatable(data.getBooleanOr("job_mine", false) ? P + "job_mine" : P + "job_other", data.getIntOr("job_done", 0), total,
					data.getIntOr("queued", 0));
			Brush.fit(g, font, job, x + 4.0F, ty, room, Palette.SEAL_DARK, 0.6F, 0.45F, false);
			ty += 7.0F;
			Brush.rect(g, x + 4.0F, ty, x + 4.0F + room, ty + 3.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
			Brush.rect(g, x + 4.5F, ty + 0.5F, x + 4.5F + (room - 1.0F) * fraction, ty + 2.5F, Palette.DANGER);
			ty += 6.0F;
		}

		// Casts that can still be restored.
		ListTag undo = data.getListOrEmpty("undo");
		if (!undo.isEmpty()) {
			heading(g, Component.translatable(P + "undo_heading"), x + 2.0F, ty, leftW - 2.0F, 0.7F);
			ty += 11.0F;
			for (Tag tag : undo) {
				if (!(tag instanceof CompoundTag line) || ty > y + h - 8.0F) {
					continue;
				}
				long left = HallClient.ticksLeft(line.getIntOr("seconds", 0) * 20L) / 20L;
				Component text = Component.translatable(P + "undo_line", line.getIntOr("blocks", 0), left / 60, String.format(java.util.Locale.ROOT, "%02d", left % 60));
				Brush.fit(g, font, text, x + 6.0F, ty, room - 2.0F, Palette.JADE_DARK, 0.6F, 0.45F, false);
				ty += 7.0F;
			}
			ty += 2.0F;
		}

		// The last records.
		ListTag log = data.getListOrEmpty("log");
		if (!log.isEmpty() && ty < y + h - 18.0F) {
			heading(g, Component.translatable(P + "log_heading"), x + 2.0F, ty, leftW - 2.0F, 0.7F);
			ty += 11.0F;
			g.enableScissor(x, Math.round(ty), x + leftW, y + h);
			for (Tag tag : log) {
				if (!(tag instanceof CompoundTag line)) {
					continue;
				}
				Component shape = Component.translatable("kultivator.kehancuran.shape." + line.getStringOr("shape", "sphere"));
				Component text = Component.translatable(line.getBooleanOr("restore", false) ? P + "log_restore" : P + "log_cast", line.getLongOr("day", 0L),
						line.getStringOr("disciple", "?"), shape, line.getIntOr("x", 0), line.getIntOr("y", 0), line.getIntOr("z", 0),
						line.getIntOr("blocks", 0));
				ty += Brush.wrap(g, font, text, x + 6.0F, ty, room - 2.0F, Palette.INK_SOFT, 0.55F, false) + 1.0F;
			}
			g.disableScissor();
		}

		// Right column: the warning under the buttons.
		int bx = x + w - columnW;
		float wy = y + 16 + 3 * (BUTTON_H + 5) + 4.0F;
		panel(g, bx, Math.round(wy), x + w, y + h);
		g.enableScissor(bx + 1, Math.round(wy) + 1, x + w - 1, y + h - 1);
		Brush.wrap(g, font, Component.translatable(P + "page_warning").withStyle(ChatFormatting.DARK_RED), bx + 4.0F, wy + 4.0F, columnW - 8.0F,
				Palette.SEAL_DARK, 0.6F, false);
		g.disableScissor();
	}

	/** One lock line: a small padlock (red when open, green when closed) and its text. Returns the next line's top. */
	private float lock(GuiGraphicsExtractor g, float ty, boolean open, Component text, float room) {
		int color = open ? Palette.DANGER : Palette.JADE;
		Glyphs.lock(g, x + 8.0F, ty + 3.5F, 7.0F, color);
		Component state = Component.translatable(open ? "kultivator.kehancuran.lock.open" : "kultivator.kehancuran.lock.closed");
		Component line = Component.translatable(P + "lock_line", text, state.copy().withColor(color & 0xFFFFFF));
		return ty + Brush.wrap(g, font, line, x + 15.0F, ty, room - 11.0F, Palette.INK, 0.65F, false) + 2.0F;
	}
}
