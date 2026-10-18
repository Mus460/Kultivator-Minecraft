package dev.kultivator.harta;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/** Small text helpers shared by the P7-harta items and messages (DESIGN 19.1.4). */
public final class HartaText {

	public static final int SOURCE_COLOR = 0x8A9A92;
	public static final int USE_COLOR = 0xC9D7C0;
	public static final int VALUE_COLOR = 0xE8D9A8;

	/**
	 * The source lines of {@code key}: {@code <key>.source} ("Sumber: …") and, when the language has it,
	 * {@code <key>.inspired} written in italics ("Terinspirasi dari …"). A key with only an inspiration shows just the
	 * italic line.
	 */
	public static void sources(String key, Consumer<Component> builder) {
		String inspired = key + ".inspired";
		boolean hasInspired = Language.getInstance().has(inspired);
		if (!hasInspired || Language.getInstance().has(key + ".source")) {
			builder.accept(Component.translatable(key + ".source").withColor(SOURCE_COLOR));
		}
		if (hasInspired) {
			builder.accept(Component.translatable(inspired).withColor(SOURCE_COLOR).withStyle(ChatFormatting.ITALIC));
		}
	}

	/** At most two decimals without trailing zeros ("0.5", "4.5", "300"). */
	public static String decimal(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	/** A fraction as a whole percentage ("15"). */
	public static String percent(double fraction) {
		return String.valueOf(Math.round(fraction * 100.0D));
	}

	private HartaText() {
	}
}
