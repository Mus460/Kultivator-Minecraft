package dev.kultivator.client.desa;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Shared drawing of the vassal cards (DESIGN 25.10) for the Dunia Desa tablet and the hall Desa page: the loyalty bar and
 * the status lines read from the synced {@code desa} tag ({@code desa.Desa.syncTag}). Pure client code.
 */
final class DesaCards {

	static final String P = "kultivator.desa.ui.";
	static final int LOYAL = 0xFF2E8963;
	static final int WARN = 0xFFB3281D;

	/** A bamboo loyalty bar 0-100 with a gold fill (red below 25, jade at 100). */
	static void loyaltyBar(GuiGraphicsExtractor g, float x, float y, float width, int loyalty) {
		float share = Math.max(0.0F, Math.min(1.0F, loyalty / 100.0F));
		int fill = loyalty >= 100 ? Palette.JADE : loyalty < 25 ? Palette.DANGER : Palette.GOLD;
		Brush.rect(g, x, y, x + width, y + 3.0F, Palette.argb(0x70, Palette.BAMBOO_KNOT));
		Brush.rect(g, x, y, x + width * share, y + 3.0F, fill);
		for (int mark = 1; mark < 4; mark++) {
			float mx = x + width * mark / 4.0F;
			Brush.rect(g, mx, y, mx + 0.5F, y + 3.0F, 0x40000000);
		}
		Brush.rect(g, x, y, x + width, y + 0.5F, 0x30FFFFFF);
	}

	/** "Setia 64 · 12 penduduk" or the waiting / lost state. */
	static Component headline(CompoundTag v) {
		if (!v.getBooleanOr("pledged", false)) {
			return Component.translatable(P + "waiting").withStyle(ChatFormatting.ITALIC);
		}
		if (v.getBooleanOr("lost", false)) {
			return Component.translatable(P + "lost");
		}
		return Component.translatable(P + "headline", v.getIntOr("loyalty", 0), v.getIntOr("villagers", 0));
	}

	/** The open request with its days left and progress, or null. */
	static MutableComponent request(CompoundTag v) {
		String type = v.getStringOr("req", "");
		if (type.isEmpty()) {
			return null;
		}
		int need = v.getIntOr("req_need", 0);
		MutableComponent line = Component.translatable(P + "request", Component.translatable("kultivator.desa.request." + type), v.getLongOr("req_in", 0L));
		if (need > 0) {
			line.append(Component.literal(" · " + v.getIntOr("req_progress", 0) + "/" + need));
		}
		return line;
	}

	/** The Pelindung Desa line. */
	static Component guard(CompoundTag v) {
		String name = v.getStringOr("guard", "");
		if (name.isEmpty()) {
			return Component.translatable(P + "no_guard");
		}
		return Component.translatable(v.getBooleanOr("guard_here", false) ? P + "guard_here" : P + "guard_away", name);
	}

	/** The last raid line, or null when none happened yet. */
	static Component lastRaid(CompoundTag v, long today) {
		long day = v.getLongOr("last_raid", -1L);
		int result = v.getIntOr("last_result", 0);
		if (day < 0L || result == 0) {
			return null;
		}
		return Component.translatable(result == 1 ? P + "raid_repelled" : P + "raid_sacked", Math.max(0L, today - day));
	}

	/** One Penjarahan log entry: "Hari 31 · Qingshan · dipukul mundur (hadir)". */
	static Component logLine(CompoundTag entry) {
		boolean repelled = entry.getIntOr("result", 0) == 1;
		return Component.translatable(P + "log_line", entry.getLongOr("day", 0L), entry.getStringOr("village", "?"),
				Component.translatable(repelled ? P + "log_repelled" : P + "log_sacked"),
				Component.translatable(entry.getBooleanOr("live", false) ? P + "log_live" : P + "log_away"));
	}

	/** A small status seal at the right of a row: a red flame for danger, a gold dot for a request, nothing otherwise. */
	static void seal(GuiGraphicsExtractor g, Font font, CompoundTag v, float x, float y) {
		if (v.getBooleanOr("siege", false) || v.getBooleanOr("threat", false)) {
			Brush.disc(g, x, y, 2.6F, WARN);
			Brush.disc(g, x, y - 0.6F, 1.0F, Palette.SEAL_GLOW);
		} else if (!v.getStringOr("req", "").isEmpty()) {
			Brush.disc(g, x, y, 2.2F, Palette.GOLD);
		} else if (v.getBooleanOr("pledged", false)) {
			Brush.disc(g, x, y, 1.6F, LOYAL);
		}
	}

	private DesaCards() {
	}
}
