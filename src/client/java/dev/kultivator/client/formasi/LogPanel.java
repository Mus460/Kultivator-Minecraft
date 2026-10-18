package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Catatan panel (DESIGN 24.7.1): the last 20 log lines {@code {d, k, args}} of the owner store, newest first: alarms
 * (also those queued while the owner was offline), deposits, keeper changes and broken patterns. Arguments that are spec
 * ids show the formation name; alarm kinds show their translated word.
 */
final class LogPanel {

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		Font font = page.font();
		page.title(g, Component.translatable("kultivator.formasiui.log.title"), x + 2.0F, y + 1.0F, w - 6.0F, 0.7F);
		float top = y + 13.0F;
		float view = h - 13.0F;
		ListTag log = page.f().getListOrEmpty("log");
		List<CompoundTag> lines = new ArrayList<>();
		for (int i = log.size() - 1; i >= 0; i--) {
			if (log.get(i) instanceof CompoundTag line) {
				lines.add(line);
			}
		}
		float textW = w - 40.0F;
		float content = 4.0F;
		List<Component> texts = new ArrayList<>();
		for (CompoundTag line : lines) {
			Component text = text(line);
			texts.add(text);
			content += Math.max(9.0F, Brush.wrapHeight(font, text, textW, 0.55F)) + 3.0F;
		}
		float offset = page.clampScroll(4, content, view);
		float cursor = top - offset;
		g.enableScissor(x, Math.round(top), x + w, Math.round(top + view));
		if (lines.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.formasiui.log.empty"), x + 4.0F, cursor + 2.0F, w - 10.0F, Palette.INK_SOFT, 0.6F, false);
		}
		for (int i = 0; i < lines.size(); i++) {
			Component text = texts.get(i);
			float height = Math.max(9.0F, Brush.wrapHeight(font, text, textW, 0.55F));
			if (cursor + height >= top && cursor <= top + view) {
				CompoundTag line = lines.get(i);
				String key = line.getStringOr("k", "");
				int color = key.contains("alert") || key.contains("alarm") ? Palette.SEAL : key.contains("broken") ? Palette.WARN
						: key.contains("donation") || key.contains("deposit") ? Palette.JADE : Palette.INK_SOFT;
				Brush.rect(g, x + 2.0F, cursor + 2.0F, x + 4.0F, cursor + height - 1.0F, color);
				Brush.fit(g, font, Component.translatable("kultivator.formasiui.log.day", line.getLongOr("d", 0L)), x + 6.0F, cursor + 1.0F, 30.0F,
						Palette.INK_FAINT, 0.5F, 0.35F, false);
				Brush.wrap(g, font, text, x + 36.0F, cursor + 1.0F, textW, Palette.INK, 0.55F, false);
			}
			cursor += height + 3.0F;
		}
		g.disableScissor();
		FormasiPage.bead(g, x + w, Math.round(top), Math.round(top + view), offset, Math.max(0.0F, content - view));
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
