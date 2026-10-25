package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Catatan sub of Sekte › Formasi (DESIGN 24.7.1, 43.7 G37): the last log lines {@code {d, k, args}} of the owner store in
 * the kit's single chronicle ({@link Kronik}: newest first under day headings, wrapped at 1.0, the wheel and ↑↓ scroll):
 * alarms (also those queued while the owner was offline), deposits, keeper changes and broken patterns. Arguments that are
 * spec ids show the formation name; alarm kinds show their translated word.
 */
final class LogPanel {

	private static final Kronik KRONIK = new Kronik(null);

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, Lukis l) {
		page.title(g, Component.translatable("kultivator.formasiui.log.title"), x + 2.0F, y + 1.0F, w - 6.0F, 1.0F);
		// The day stamps are the store's own day numbers: every heading reads "Hari N" (no "today" guess on the client).
		KRONIK.gambar(l, "formasi:log", entri(page.f()), Long.MIN_VALUE, x, y + 15, x + w, y + h);
	}

	/**
	 * The log of the {@code formasi} part as chronicle entries (oldest first), kind {@code formasi}; also the source of the
	 * formation chronicle in Sekte › Ikhtisar › Catatan (43.11 G37, {@code HallClient.sumberCatatan}).
	 */
	static List<Kronik.Entri> entri(CompoundTag formasi) {
		List<Kronik.Entri> entri = new ArrayList<>();
		for (Tag tag : formasi.getListOrEmpty("log")) {
			if (tag instanceof CompoundTag line) {
				String key = line.getStringOr("k", "");
				int color = key.contains("alert") || key.contains("alarm") ? Palette.SEAL : key.contains("broken") ? Palette.WARN
						: key.contains("donation") || key.contains("deposit") ? Palette.JADE : Palette.INK_SOFT;
				entri.add(new Kronik.Entri(text(line), line.getLongOr("d", 0L), "formasi", color & 0xFFFFFF));
			}
		}
		return entri;
	}

	/** The translated line: spec ids become names, alarm kinds their word, everything else stays as written. */
	static Component text(CompoundTag line) {
		List<Object> args = new ArrayList<>();
		for (Tag tag : line.getListOrEmpty("args")) {
			String arg = tag.asString().orElse("");
			FormationSpec spec = FormationRegistry.byId(arg);
			if (spec != null) {
				args.add(spec.name());
			} else if (arg.matches("approach|breach|stranger|elite|boss")) {
				args.add(Component.translatable("kultivator.formasiui.alarm_kind." + arg.toLowerCase(Locale.ROOT)));
			} else {
				args.add(arg);
			}
		}
		return Component.translatable(line.getStringOr("k", "?"), args.toArray());
	}

	private LogPanel() {
	}
}
