package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Text helpers of 39F: names as components for chat and screens, and as stored arguments for diary entries and news
 * lines (keys only, so the reader's client translates them: 39E's {@code HarianTeks} understands plain lang keys,
 * nested {@code key|arg|arg} lines and {@code @kota_nama:<town>}).
 */
final class Teks {

	/** The NPC sect's name (a generic "a sect in the mountains" when unknown). */
	static Component sekte(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name != null ? name : Component.translatable(SeteruIds.k("sekte_asing"));
	}

	/** The sect's name as a stored argument ({@code key|a|b}). */
	static String argSekte(String sekte) {
		return arg(sekte(sekte));
	}

	/** A town's name as a stored diary argument, "" for none. */
	static String argKota(long town) {
		return town == 0L ? "" : "@kota_nama:" + town;
	}

	static Component kota(long town) {
		return town == 0L ? Component.translatable(SeteruIds.k("tempat.jalan")) : KotaNames.town(town);
	}

	/**
	 * A component as a stored argument: a translatable becomes its key (with {@code |}-joined arguments one level deep),
	 * a literal its text.
	 */
	static String arg(Component c) {
		if (c == null) {
			return "";
		}
		if (c.getContents() instanceof TranslatableContents tc) {
			StringBuilder out = new StringBuilder(tc.getKey());
			for (Object a : tc.getArgs()) {
				out.append('|');
				if (a instanceof Component inner && inner.getContents() instanceof TranslatableContents itc && itc.getArgs().length == 0) {
					out.append(itc.getKey());
				} else if (a instanceof Component inner) {
					out.append(inner.getString().replace('|', '/'));
				} else {
					out.append(String.valueOf(a).replace('|', '/'));
				}
			}
			return out.toString();
		}
		if (c.getContents() instanceof PlainTextContents) {
			return c.getString().replace('|', '/');
		}
		return c.getString().replace('|', '/');
	}

	/**
	 * A stored argument as plain text in the server's language (memories keep one literal argument, so names and nested
	 * sect names are resolved before they are stored; the integrated server shares the player's language).
	 */
	static String polos(String a) {
		return a == null || a.isEmpty() ? "" : komponen(a).getString();
	}

	/** A stored argument as a component ({@code key}, {@code key|a|b}, {@code @kota_nama:<id>}, {@code n:<n>}, literal). */
	static Component komponen(String a) {
		try {
			if (a.startsWith("n:")) {
				return Component.literal(a.substring(2));
			}
			if (a.startsWith("@kota_nama:")) {
				return KotaNames.town(Long.parseLong(a.substring(11)));
			}
			if (a.indexOf('|') > 0 && a.startsWith("kultivator.")) {
				String[] part = a.split("\\|");
				Object[] inner = new Object[part.length - 1];
				for (int i = 1; i < part.length; i++) {
					inner[i - 1] = komponen(part[i]);
				}
				return Component.translatable(part[0], inner);
			}
			if (a.indexOf(' ') < 0 && a.indexOf('.') > 0 && (a.startsWith("kultivator.") || a.startsWith("entity."))) {
				return Component.translatable(a);
			}
		} catch (RuntimeException exception) {
			return Component.literal(a);
		}
		return Component.literal(a);
	}

	/** {@code k(key)} with arguments. */
	static MutableComponent t(String key, Object... args) {
		return Component.translatable(SeteruIds.k(key), args);
	}

	private Teks() {
	}
}
