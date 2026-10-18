package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Number and bill formatting shared by the flow's messages and the client pages: whole units ("1570"), a half unit as
 * "0.5", ticks as "m:ss", and a bill as a short list of category names and amounts ("32 Batu, 12 Kayu"). Category names
 * come from {@link Bahan#displayName()}, so they read in the viewer's language.
 */
public final class Fmt {

	/** A centi amount as units: whole numbers plain, otherwise one decimal ("0.5"). */
	public static String units(long centi) {
		if (centi % Bahan.CENTI == 0L) {
			return Long.toString(centi / Bahan.CENTI);
		}
		return String.format(Locale.ROOT, "%.1f", centi / (double) Bahan.CENTI);
	}

	/** A centi amount rounded up to whole units (what the player still has to bring). */
	public static long ceilUnits(long centi) {
		return centi <= 0L ? 0L : (centi + Bahan.CENTI - 1L) / Bahan.CENTI;
	}

	/** Ticks as "m:ss" (or "h:mm:ss"). */
	public static String clock(long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		if (seconds >= 3600L) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
		}
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	/** "32 Batu", "1 Lodestone": one line of a bill. */
	public static MutableComponent line(Bahan cat, long centi) {
		return Component.translatable("kultivator.sectbuild.amount", ceilUnits(centi), cat.displayName());
	}

	public static MutableComponent line(Item item, int count) {
		return Component.translatable("kultivator.sectbuild.amount", count, item.getDefaultInstance().getHoverName());
	}

	/** The non-zero lines of a bill (categories first, then exact items), at most {@code max}. */
	public static List<Component> lines(Bill bill, int max) {
		List<Component> lines = new ArrayList<>();
		for (Bahan cat : Bahan.values()) {
			if (bill.centi(cat) > 0L && lines.size() < max) {
				lines.add(line(cat, bill.centi(cat)));
			}
		}
		for (Map.Entry<Item, Integer> entry : bill.exact().entrySet()) {
			if (entry.getValue() > 0 && lines.size() < max) {
				lines.add(line(entry.getKey(), entry.getValue()));
			}
		}
		return lines;
	}

	/** "32 Batu, 12 Kayu, ..." (at most {@code max} lines, then "…"); empty text when the bill is empty. */
	public static MutableComponent joined(Bill bill, int max) {
		List<Component> lines = lines(bill, max + 1);
		MutableComponent text = Component.empty();
		for (int i = 0; i < Math.min(max, lines.size()); i++) {
			if (i > 0) {
				text.append(Component.literal(", "));
			}
			text.append(lines.get(i));
		}
		if (lines.size() > max) {
			text.append(Component.literal(", …"));
		}
		return text;
	}

	/** Percent 0-100 of {@code part / whole}. */
	public static int percent(double part, double whole) {
		return whole <= 0.0D ? 100 : (int) Math.max(0L, Math.min(100L, Math.round(part * 100.0D / whole)));
	}

	private Fmt() {
	}
}
