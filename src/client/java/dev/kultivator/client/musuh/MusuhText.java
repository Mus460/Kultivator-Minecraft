package dev.kultivator.client.musuh;

import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Shared lines of the Musuh and Pertahanan tablets: the next raid, results and encounters (DESIGN 25.10). */
final class MusuhText {

	/** Results of the raid log ({@code SectRaids.RES_*}). */
	static Component result(int res) {
		return Component.translatable("kultivator.musuh.res." + Math.max(0, Math.min(4, res)));
	}

	static int resultColor(int res) {
		return res == 0 || res == 3 ? Palette.JADE_DARK : res == 1 ? Palette.GOLD_DARK : Palette.SEAL;
	}

	static Component sect(int sect) {
		return sect < 0 ? Component.translatable("kultivator.musuh.info.unknown_sect") : Component.translatable("kultivator.musuh.sect." + Math.floorMod(sect, 8));
	}

	/** "Serbuan berikutnya: hari 31 · Sekte Bulan Darah", or why there is none. */
	static Component nextRaid(CompoundTag raid) {
		if (!raid.getBooleanOr("enabled", false)) {
			return Component.translatable("kultivator.musuh.info.disabled");
		}
		if (!raid.getBooleanOr("has_hall", false)) {
			return Component.translatable("kultivator.musuh.info.no_hall");
		}
		CompoundTag live = raid.getCompoundOrEmpty("live");
		if (!live.isEmpty()) {
			return Component.translatable("kultivator.musuh.info.live", live.getIntOr("wave", 1), live.getIntOr("waves", 3), live.getIntOr("meter", 0));
		}
		boolean omen = raid.getIntOr("state", 0) == 1;
		if (raid.getIntOr("hall_level", 0) < raid.getIntOr("min_level", 3) && !omen) {
			return Component.translatable("kultivator.musuh.info.low_hall", raid.getIntOr("min_level", 3));
		}
		long next = raid.getLongOr("next_day", -1L);
		if (next < 0L) {
			return Component.translatable("kultivator.musuh.info.counting");
		}
		return Component.translatable("kultivator.musuh.ui.next", next, omen ? sect(raid.getIntOr("sect", -1)) : sect(-1));
	}

	/** Days with one decimal ("0,4" reads fine in both languages as "0.4"). */
	static String days(long ticks) {
		return String.format(Locale.ROOT, "%.1f", ticks / 24000.0D);
	}

	private MusuhText() {
	}
}
