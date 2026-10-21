package dev.kultivator.client.codex;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Realm;
import dev.kultivator.network.Actions;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Kehancuran tablet (DESIGN 22.11.1, 22.12.6), on the blood-red scroll: one big red seal is the "Saklar gampang"
 * switch (one click, no oath, hold or checkbox) sending {@code Actions.KEHANCURAN self_on / self_off}; the server answers
 * with its one red chat line. Below it the status from {@code sessionExtra.teknik.kh}: availability or why not, this
 * rank's K, radius cap and blocks per cast, the safe zones, "Shift saat merapal = tanpa kehancuran", the queue, the
 * Pulihkan talisman with the undo windows (blocks, mm:ss) and the last six self casts.
 * <p>
 * DESIGN 33.6 R9: shown as "Kehancuran Diri" (the player's own techniques); one talisman above Pulihkan leads to the
 * hall's "Segel Merah Murid" (the disciples' Destroyer arts), so the two pages are never mistaken for each other.
 */
final class KehancuranPage extends CodexPage {

	private static final int PAPER_TOP = 0xFF7E1812;
	private static final int PAPER_BOTTOM = 0xFF4E0C08;
	private static final int TEXT = 0xFFF3D9C8;
	private static final int TEXT_SOFT = 0xFFD9A898;
	private static final int STAMP = 0xFFD8261A;
	private static final String P = "kultivator.codex.kh.";
	/** The hall's Segel Merah Murid page ({@code HallScreen} page id 9, after Catatan). */
	private static final int RED_SEAL_PAGE = 9;

	private int sealX;
	private int sealY;
	private int sealR;
	private int columnX;
	private long pendingUntil;

	KehancuranPage(CodexScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.codex.tab.kehancuran");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.codex.tab.kehancuran.short");
	}

	@Override
	int icon() {
		return Glyphs.SEAL;
	}

	@Override
	boolean red() {
		return true;
	}

	@Override
	String helpTopic() {
		return "kehancuran_diri";
	}

	@Override
	protected void build() {
		int leftW = Math.max(96, Math.min(150, Math.round(w * 0.42F)));
		sealR = Math.max(16, Math.min(28, Math.min(leftW, h - 70) / 3));
		sealX = x + leftW / 2;
		sealY = y + 14 + sealR;
		columnX = x + leftW + 6;
		add(new RedSeal(sealX - sealR, sealY - sealR, sealR * 2));
		int bw = leftW - 8;
		TalismanButton restore = add(new TalismanButton(x + 4, y + h - 16, bw, 15, () -> Component.translatable(P + "restore", undoBlocks())));
		restore.enabledWhen(() -> Keys.connected() && undoBlocks() > 0);
		restore.onPress(() -> send(Actions.KEHANCURAN, 0, "self_undo"));
		restore.tooltip(this::restoreTip);
		TalismanButton disciples = add(new TalismanButton(x + 4, y + h - 31, bw, 13, () -> Component.translatable("kultivator.uxpages.kh.to_disciples")));
		disciples.enabledWhen(() -> Keys.connected() && allowed());
		disciples.onPress(() -> HubNav.openHall(RED_SEAL_PAGE));
		disciples.tooltip(() -> List.of(Component.translatable("kultivator.uxpages.kh.to_disciples.title"),
				Component.translatable("kultivator.uxpages.kh.to_disciples.tip").withStyle(ChatFormatting.GRAY)));
	}

	private boolean on() {
		return TeknikSession.breakerStored();
	}

	private boolean allowed() {
		CompoundTag kh = TeknikSession.kh();
		if (kh.get("allowed") != null) {
			return kh.getBooleanOr("allowed", false);
		}
		return Minecraft.getInstance().hasSingleplayerServer();
	}

	private int undoBlocks() {
		int total = 0;
		for (Tag entry : TeknikSession.kh().getListOrEmpty("undo")) {
			if (entry instanceof ListTag pair && !pair.isEmpty()) {
				total += pair.get(0).asInt().orElse(0);
			}
		}
		return total;
	}

	private List<Component> restoreTip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(P + "restore.title"));
		for (Tag entry : TeknikSession.kh().getListOrEmpty("undo")) {
			if (entry instanceof ListTag pair && pair.size() >= 2) {
				int blocks = pair.get(0).asInt().orElse(0);
				int seconds = pair.get(1).asInt().orElse(0);
				lines.add(Component.translatable(P + "restore.entry", SpecInfo.number(blocks), clock(seconds)).withStyle(ChatFormatting.GRAY));
			}
		}
		if (lines.size() == 1) {
			lines.add(Component.translatable(P + "restore.none").withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable(P + "restore.restart").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static String clock(int seconds) {
		return String.format(Locale.ROOT, "%d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		g.fillGradient(x - 2, y - 2, x + w + 2, y + h + 2, PAPER_TOP, PAPER_BOTTOM);
		Brush.frame(g, x - 2, y - 2, x + w + 2, y + h + 2, Palette.argb(0xC0, Palette.GOLD_DARK));
		CultivationData data = data();
		boolean on = on();
		Component state = Component.translatable(on ? P + "state_on" : P + "state_off");
		Brush.fitCentered(g, font, Brush.bold(state), sealX, y + 2.0F, sealR * 2.0F + 50.0F, on ? 0xFFFF8A70 : TEXT, 0.75F, 0.5F, false);
		Brush.wrap(g, font, Component.translatable(P + "one_click"), x + 4.0F, sealY + sealR + 6.0F, columnX - x - 10.0F, TEXT_SOFT, 0.55F, false);

		float tx = columnX;
		float room = x + w - columnX - 2.0F;
		float cursor = y + 1.0F;
		CompoundTag kh = TeknikSession.kh();
		List<Component> lines = new ArrayList<>();
		if (!allowed()) {
			Component why = kh.get("why") != null ? Component.translatable(kh.getStringOr("why", "")) : Component.translatable(P + "why_unknown");
			lines.add(Component.translatable(P + "unavailable", why).withColor(0xFFFFB0A0));
		} else if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
			lines.add(Component.translatable(P + "below_gc").withColor(0xFFFFD0A0));
		} else {
			lines.add(Component.translatable(P + "available").withColor(0xFFB0FFC0));
		}
		double k = kh.get("k") != null ? kh.getDoubleOr("k", 0.0D) : TechniquePower.kScale(data);
		int radius = kh.get("r") != null ? kh.getIntOr("r", 0) : KultivatorConfig.get().kehancuranMaxRadius;
		int cap = kh.get("cap") != null ? kh.getIntOr("cap", 0) : TechniquePower.blockCap(data);
		lines.add(Component.translatable(P + "size", SpecInfo.decimal(k, 2), radius, SpecInfo.number(cap)));
		int safe = kh.get("safe") != null ? kh.getIntOr("safe", 4) : KultivatorConfig.get().kehancuranCasterSafeRadius;
		int home = kh.get("home") != null ? kh.getIntOr("home", 24) : KultivatorConfig.get().kehancuranHomeSafeRadius;
		lines.add(Component.translatable(P + "zone.self", safe));
		lines.add(Component.translatable(P + "zone.home", home));
		lines.add(Component.translatable(P + "zone.others"));
		lines.add(Component.translatable(P + "shift").withStyle(ChatFormatting.BOLD).withColor(0xFFFFE08A));
		int queued = kh.getIntOr("queued", 0);
		boolean running = kh.getBooleanOr("running", false);
		lines.add(Component.translatable(running ? P + "queue.running" : queued > 0 ? P + "queue.waiting" : P + "queue.idle", queued));
		for (Component line : lines) {
			cursor += Brush.wrap(g, font, line, tx, cursor, room, TEXT, 0.58F, false) + 1.5F;
		}
		cursor += 2.0F;
		Brush.rect(g, tx, cursor, tx + room, cursor + 0.6F, Palette.argb(0xA0, Palette.GOLD));
		cursor += 3.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(P + "log")), tx, cursor, room, 0xFFFFC8B0, 0.6F, 0.45F, false);
		cursor += 8.0F;
		int shown = 0;
		for (Tag entry : kh.getListOrEmpty("log")) {
			if (shown >= 6 || cursor > y + h - 6) {
				break;
			}
			Component line = logLine(entry);
			cursor += Brush.wrap(g, font, line, tx + 2.0F, cursor, room - 2.0F, TEXT_SOFT, 0.52F, false) + 0.5F;
			shown++;
		}
		if (shown == 0) {
			Brush.wrap(g, font, Component.translatable(P + "log.none"), tx + 2.0F, cursor, room - 2.0F, TEXT_SOFT, 0.52F, false);
		}
	}

	private static Component logLine(Tag entry) {
		if (entry instanceof CompoundTag tag) {
			return Component.translatable("kultivator.codex.kh.log.entry", spec(tag.getStringOr("spec", "")), SpecInfo.number(tag.getIntOr("blocks", 0)),
					tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
		}
		return Component.literal(entry.asString().orElse(""));
	}

	private static Component spec(String id) {
		String bare = id.startsWith("self:") ? id.substring(5) : id;
		dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(bare);
		return spec == null ? Component.literal(bare) : spec.displayName();
	}

	/** The big red seal: the one-click switch. */
	private final class RedSeal extends Widget {
		RedSeal(int x, int y, int size) {
			super(x, y, size, size);
			tooltip(() -> List.of(Component.translatable(on() ? P + "seal.off" : P + "seal.on"),
					Component.translatable(P + "seal.tip").withStyle(ChatFormatting.GRAY),
					Component.translatable(P + "seal.undo").withStyle(ChatFormatting.DARK_GRAY)));
		}

		@Override
		public boolean contains(double mouseX, double mouseY) {
			double dx = mouseX - (x + w / 2.0D);
			double dy = mouseY - (y + h / 2.0D);
			return dx * dx + dy * dy <= (w / 2.0D) * (w / 2.0D);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = on();
			float r = w / 2.0F;
			float cx = x + r;
			float cy = y + r + pressOffset(over);
			float beat = on ? 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 300.0D) : 0.0F;
			if (on || over) {
				Brush.halo(g, cx, cy, r, 5.0F, on ? 0xFF4030 : 0xFFB070, on ? 0.6F + 0.4F * beat : hover.get());
			}
			Brush.disc(g, cx + 1.0F, cy + 2.0F, r, 0x60000000);
			Brush.disc(g, cx, cy, r, on ? 0xFFFF5B3A : 0xFF8A1A12);
			Brush.disc(g, cx, cy, r - 2.0F, on ? STAMP : 0xFF5A0E0A);
			Brush.ring(g, cx, cy, r * 0.72F, r * 0.72F + 1.0F, Palette.argb(0xC0, 0xFFE0C0));
			// The carved square of the seal with a cross.
			float s = r * 0.45F;
			Brush.rect(g, cx - s, cy - s, cx + s, cy + s, Palette.argb(on ? 0xF0 : 0xA0, 0xFFE0C0));
			Brush.rect(g, cx - s + 2.0F, cy - s + 2.0F, cx + s - 2.0F, cy + s - 2.0F, on ? STAMP : 0xFF5A0E0A);
			Brush.line(g, cx - s + 3.0F, cy - s + 3.0F, cx + s - 3.0F, cy + s - 3.0F, 1.3F, Palette.argb(on ? 0xF0 : 0xA0, 0xFFE0C0));
			Brush.line(g, cx - s + 3.0F, cy + s - 3.0F, cx + s - 3.0F, cy - s + 3.0F, 1.3F, Palette.argb(on ? 0xF0 : 0xA0, 0xFFE0C0));
			if (Util.getMillis() < pendingUntil) {
				Brush.arc(g, cx, cy, r + 1.0F, r + 2.5F, (Util.getMillis() % 1000L) / 1000.0F, (Util.getMillis() % 1000L) / 1000.0F + 0.25F, 0xFFFFE08A);
			}
		}

		@Override
		protected void activate() {
			if (!Keys.connected()) {
				UiSound.denied();
				return;
			}
			boolean turnOn = !on();
			// The server answers with its red line and one low heartbeat (22.11.1); here only the seal's knock.
			UiSound.toggle(turnOn);
			pendingUntil = Util.getMillis() + 1500L;
			send(Actions.KEHANCURAN, 0, turnOn ? "self_on" : "self_off");
		}
	}
}
