package dev.kultivator.client.screen.ui;

import dev.kultivator.client.hub.HubNav;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live key names for texts (DESIGN 33.6 R11, C33.P2) and the small "?" help seal of the jade screens (C33.P1).
 * <p>
 * Lang texts write a key as the token {@code [[key:<id>]]} (for example {@code [[key:meditate]]}); {@link #fill} replaces
 * each token with the key the player bound to {@code key.kultivator.<id>} right now, so a rebound key never makes the
 * Kitab Panduan, the Ensiklopedia or the tracker wrong. An unbound key names the way to it instead: "tidak diikat ·
 * Akses Cepat (V)" for what the Akses Cepat wheel or the hub reaches, "tidak diikat · Roda Segel (tahan G)" for the seal
 * keys, and "tidak diikat" for the rest.
 */
public final class KeyHints {

	private static final String P = "kultivator.uxpages.";
	private static final String TOKEN_START = "[[key:";
	private static final Pattern TOKEN = Pattern.compile("\\[\\[key:([a-z0-9_]+)]]");
	/** Keys whose action the Akses Cepat wheel or the hub reaches when the key itself is unbound (33.3.1). */
	private static final Set<String> QUICK_REACH = Set.of("info", "meditate", "flying_sword", "codex", "murid", "cincin", "dunia", "abadi", "guide");
	/** Keys whose action the Roda Segel (hold the cast key) reaches when the key itself is unbound. */
	private static final Set<String> WHEEL_REACH = Set.of("previous", "next", "wheel", "seal_1", "seal_2", "seal_3", "seal_4");

	/** {@code text} with every {@code [[key:<id>]]} replaced by the live key name (the same component when it has none). */
	public static Component fill(Component text) {
		if (!text.getString().contains(TOKEN_START)) {
			return text;
		}
		MutableComponent out = Component.empty();
		text.visit((style, content) -> {
			appendFilled(out, style, content);
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** The translated text of {@code key} with its key tokens filled. */
	public static Component fill(String key) {
		return fill(Component.translatable(key));
	}

	/** True when the lang value of {@code key} exists and holds a key token (cheap check before {@link #fill}). */
	public static boolean hasToken(String key) {
		Language language = Language.getInstance();
		return language.has(key) && language.getOrDefault(key).contains(TOKEN_START);
	}

	private static void appendFilled(MutableComponent out, Style style, String content) {
		Matcher matcher = TOKEN.matcher(content);
		int last = 0;
		while (matcher.find()) {
			if (matcher.start() > last) {
				out.append(Component.literal(content.substring(last, matcher.start())).setStyle(style));
			}
			out.append(name(matcher.group(1)).copy().setStyle(style.withBold(true)));
			last = matcher.end();
		}
		if (last < content.length()) {
			out.append(Component.literal(content.substring(last)).setStyle(style));
		}
	}

	/** The live name of the Kultivator key {@code id} ({@code key.kultivator.<id>}), or how to reach it when unbound. */
	public static Component name(String id) {
		KeyMapping key = KeyMapping.get("key.kultivator." + id);
		if (key == null) {
			return Component.literal(id);
		}
		return name(key);
	}

	/** The live name of {@code key}, or how to reach its action when it is unbound. */
	public static Component name(KeyMapping key) {
		if (!key.isUnbound()) {
			return key.getTranslatedKeyMessage();
		}
		String id = key.getName().startsWith("key.kultivator.") ? key.getName().substring("key.kultivator.".length()) : key.getName();
		KeyMapping quick = KeyMapping.get("key.kultivator.quick");
		if (QUICK_REACH.contains(id) && quick != null && !quick.isUnbound()) {
			return Component.translatable(P + "key.unbound_quick", quick.getTranslatedKeyMessage());
		}
		KeyMapping cast = KeyMapping.get("key.kultivator.cast");
		if (WHEEL_REACH.contains(id) && cast != null && !cast.isUnbound()) {
			return Component.translatable(P + "key.unbound_wheel", cast.getTranslatedKeyMessage());
		}
		return Component.translatable(P + "key.unbound");
	}

	/**
	 * The key that opens the jade scroll (the hub): the Akses Cepat key when bound (a tap opens the scroll), else the
	 * plain scroll key when bound, else "tidak diikat".
	 */
	public static Component hubKey() {
		KeyMapping quick = KeyMapping.get("key.kultivator.quick");
		if (quick != null && !quick.isUnbound()) {
			return quick.getTranslatedKeyMessage();
		}
		KeyMapping info = KeyMapping.get("key.kultivator.info");
		if (info != null && !info.isUnbound()) {
			return info.getTranslatedKeyMessage();
		}
		return Component.translatable(P + "key.unbound");
	}

	/** True while the key that peeks the guide tracker is held: Akses Cepat, or the plain scroll key if bound (R12). */
	public static boolean peekHeld() {
		KeyMapping quick = KeyMapping.get("key.kultivator.quick");
		KeyMapping info = KeyMapping.get("key.kultivator.info");
		return quick != null && !quick.isUnbound() && quick.isDown() || info != null && !info.isUnbound() && info.isDown();
	}

	/** The title of help topic {@code topic} when the lang files have it. */
	public static @Nullable Component topicTitle(String topic) {
		String key = "kultivator.screen.help." + topic + ".title";
		return Language.getInstance().has(key) ? Component.translatable(key) : null;
	}

	/**
	 * The "?" help seal at the right end of a jade screen's header (C33.P1): a small round seal with a question mark; a
	 * click opens Panduan › Ensiklopedia on the page's topic ({@link HubNav#help}). The tooltip names the topic.
	 */
	public static final class HelpSeal extends Widget {

		private final Supplier<String> topic;

		/** A seal of {@code size} px centred on ({@code cx}, {@code cy}); {@code topic} is read when it is clicked. */
		public HelpSeal(float cx, float cy, int size, Supplier<String> topic) {
			super(Math.round(cx - size / 2.0F), Math.round(cy - size / 2.0F), size, size);
			this.topic = topic;
			tooltip(this::lines);
		}

		/** Moves the seal so it is centred on ({@code cx}, {@code cy}). */
		public void moveTo(float cx, float cy) {
			x = Math.round(cx - w / 2.0F);
			y = Math.round(cy - h / 2.0F);
		}

		private List<Component> lines() {
			Component title = topicTitle(topic.get());
			return title == null
					? List.of(Component.translatable(P + "help.title"), Component.translatable(P + "help.tip").withStyle(ChatFormatting.GRAY))
					: List.of(Component.translatable(P + "help.title"), Component.translatable(P + "help.topic", title).withStyle(ChatFormatting.GRAY),
					Component.translatable(P + "help.tip").withStyle(ChatFormatting.DARK_GRAY));
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			float r = w / 2.0F;
			float cx = x + r;
			float cy = y + r + pressOffset(over);
			float glow = hover.get();
			if (glow > 0.02F) {
				Brush.halo(g, cx, cy, r, 2.5F, 0xFFE07A, glow * 0.8F);
			}
			Brush.disc(g, cx + 0.5F, cy + 0.8F, r, 0x40000000);
			Brush.disc(g, cx, cy, r, Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, glow * 0.6F));
			Brush.ring(g, cx, cy, r - 1.4F, r - 0.8F, Palette.argb(0xB0, Palette.TALISMAN));
			float scale = Math.max(0.6F, Math.min(0.9F, (w - 3.0F) / 7.0F));
			Brush.centered(g, font, Brush.bold(Component.literal("?")), cx + 0.4F, cy - 3.6F * scale, Palette.TALISMAN, scale, false);
		}

		@Override
		protected void activate() {
			UiSound.press();
			HubNav.help(topic.get());
		}
	}

	private KeyHints() {
	}
}
