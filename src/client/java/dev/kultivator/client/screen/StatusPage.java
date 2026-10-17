package dev.kultivator.client.screen;

import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.client.Keys;
import dev.kultivator.client.QiHud;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.cultivation.Stats;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Status: the big dantian circle (gold ring cultivation, blue ring qi, violet ring stored qi, the realm written in
 * the ink core) with the numbers on a column of bamboo slips beside it, and the meditate / breakthrough / root test
 * talismans under them.
 */
final class StatusPage extends Page {

	private static final int BUTTON_H = 18;

	private final Eased bar = new Eased(5.0F);
	private final Eased qi = new Eased(5.0F);
	private final Eased stored = new Eased(5.0F);
	private final Eased scrollShown = new Eased(14.0F);
	private final List<Slip> slips = new ArrayList<>();
	private float scroll;
	private long slipsBuiltAt;
	private boolean builtMortalUntested;
	private int lastStep = -1;

	private float cx;
	private float cy;
	private float radius;
	private int slipX;
	private int slipY;
	private int slipW;
	private int slipH;

	/** One bamboo slip: a label and its value, or a heading when {@code value} is null. */
	private record Slip(Component label, Component value, int color) {
	}

	StatusPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.status");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.status.short");
	}

	@Override
	protected void build() {
		CultivationData data = data();
		builtMortalUntested = mortalUntested(data);
		int availH = h - BUTTON_H - 6;
		int circleW = Math.round(w * 0.47F);
		float diameter = Math.max(40.0F, Math.min(circleW - 4.0F, availH - 4.0F));
		radius = diameter / 2.0F;
		cx = x + circleW / 2.0F;
		cy = y + availH / 2.0F;
		slipX = x + circleW + 4;
		slipY = y;
		slipW = x + w - slipX;
		slipH = availH;

		List<TalismanButton> row = new ArrayList<>();
		TalismanButton meditate = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable(data().meditating()
				? "kultivator.screen.btn.stop_meditating" : "kultivator.screen.btn.meditate"));
		meditate.stampedWhen(() -> data().meditating());
		meditate.onPress(() -> send(Actions.MEDITATE_TOGGLE));
		meditate.tooltip(() -> List.of(Component.translatable("kultivator.screen.btn.meditate"),
				Component.translatable("kultivator.screen.btn.meditate.tip"),
				Component.translatable("kultivator.screen.key_hint", Keys.MEDITATE.getTranslatedKeyMessage())));
		row.add(meditate);

		TalismanButton breakthrough = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable("kultivator.screen.btn.breakthrough"));
		breakthrough.enabledWhen(() -> canBreakthrough(data()));
		breakthrough.urgentWhen(() -> canBreakthrough(data()));
		breakthrough.onPress(() -> send(Actions.BREAKTHROUGH));
		breakthrough.tooltip(StatusPage::breakthroughTip);
		row.add(breakthrough);

		if (builtMortalUntested) {
			TalismanButton root = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable("kultivator.screen.btn.root_test"));
			root.enabledWhen(() -> mortalUntested(data()));
			root.urgentWhen(() -> mortalUntested(data()));
			root.onPress(() -> {
				// The testing ceremony plays around the player in the world: close the scroll so it can be watched.
				send(Actions.ROOT_TEST);
				screen.onClose();
			});
			root.tooltip(() -> List.of(Component.translatable("kultivator.screen.btn.root_test"),
					Component.translatable("kultivator.screen.btn.root_test.tip")));
			row.add(root);
		}

		int gap = 6;
		int count = row.size();
		int bw = Math.min(118, (w - gap * (count - 1)) / count);
		int total = count * bw + gap * (count - 1);
		int bx = x + (w - total) / 2;
		for (TalismanButton button : row) {
			button.x = bx;
			button.y = y + h - BUTTON_H;
			button.w = bw;
			add(button);
			bx += bw + gap;
		}
	}

	@Override
	boolean needsRebuild() {
		return builtMortalUntested != mortalUntested(data());
	}

	@Override
	void onData() {
		rebuildSlips();
	}

	/** A Mortal whose root was never tested (the test button is offered). */
	static boolean mortalUntested(CultivationData data) {
		return data.realm().isMortal() && !data.rootTested();
	}

	/**
	 * The root is tested and revealed. The root is saved when the 8 s testing ceremony starts; until the reveal the
	 * screens keep the result hidden.
	 */
	static boolean rootKnown(CultivationData data) {
		return data.rootTested() && !SpiritRoots.revealPending(data);
	}

	/** A Mortal whose root is not known yet (untested, or its ceremony still running): no breakthrough yet. */
	static boolean mortalRootUnknown(CultivationData data) {
		return data.realm().isMortal() && !rootKnown(data);
	}

	/**
	 * The breakthrough talisman works when the bar is full at a bottleneck, no breakthrough is running, no Qi Deviation
	 * is raging and (for a Mortal) the root is tested. The tooltip tells which of these is missing.
	 */
	static boolean canBreakthrough(CultivationData data) {
		Rank rank = data.rank();
		return data.barFull() && rank.nextIsBreakthrough() && !data.breakingThrough() && !data.deviating() && !mortalRootUnknown(data);
	}

	/** Full bar at a bottleneck that the player could break through (an untested Mortal must test the root first). */
	private static boolean ready(CultivationData data, Rank rank) {
		return data.barFull() && rank.nextIsBreakthrough() && !mortalRootUnknown(data);
	}

	private static List<Component> breakthroughTip() {
		CultivationData data = data();
		Rank rank = data.rank();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.screen.btn.breakthrough"));
		if (rank.isMax()) {
			lines.add(Component.translatable("kultivator.screen.bt.summit"));
			return lines;
		}
		Realm target = rank.next().realm();
		String kind = rank.realm().isMortal() ? "awakening" : target == Realm.FOUNDATION ? "chance" : "tribulation";
		lines.add(Component.translatable("kultivator.screen.bt.next", rank.next().displayName().withColor(target.color())));
		lines.add(Component.translatable("kultivator.screen.bt." + kind).withStyle(ChatFormatting.GRAY));
		if (data.breakingThrough()) {
			lines.add(Component.translatable("kultivator.screen.bt.running").withStyle(ChatFormatting.GOLD));
		} else if (!rank.isBottleneck()) {
			lines.add(Component.translatable("kultivator.screen.bt.not_peak").withStyle(ChatFormatting.RED));
		} else if (!data.barFull()) {
			lines.add(Component.translatable("kultivator.screen.bt.not_full", Msg.number(data.xp()), Msg.number(rank.xpRequired()))
					.withStyle(ChatFormatting.RED));
		}
		if (rank.realm().isMortal() && !data.rootTested()) {
			lines.add(Component.translatable("kultivator.screen.bt.root_first").withStyle(ChatFormatting.RED));
		} else if (rank.realm().isMortal() && !rootKnown(data)) {
			lines.add(Component.translatable("kultivator.roots.info.reading").withStyle(ChatFormatting.GOLD));
		}
		if (data.deviating()) {
			lines.add(Component.translatable("kultivator.screen.bt.deviation").withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable("kultivator.screen.key_hint_bt", Keys.MEDITATE.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	// ================================================================== bamboo slips

	private void rebuildSlips() {
		slipsBuiltAt = Util.getMillis();
		slips.clear();
		CultivationData data = data();
		Rank rank = data.rank();
		slips.add(new Slip(Component.translatable("kultivator.screen.status.realm"), rank.displayName(), Palette.onPaper(rank.realm().color())));
		boolean ready = ready(data, rank);
		slips.add(new Slip(Component.translatable("kultivator.screen.status.cultivation"),
				Component.literal(Msg.number(data.xp()) + " / " + Msg.number(rank.xpRequired()) + (ready ? " ★" : "")),
				Palette.GOLD_DARK));
		slips.add(new Slip(Component.translatable(rank.isMax() ? "kultivator.screen.status.stored_immortal" : "kultivator.screen.status.stored"),
				Component.literal(Msg.number(data.storedQi())), Palette.onPaper(Palette.STORED)));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.qi"),
				Component.literal(Msg.number(data.qi()) + " / " + Msg.number(Qi.max(data))), Palette.onPaper(Palette.QI)));
		// What the realm gives the body (DESIGN section 2), with one decimal: small bonuses must not round to 0.
		slips.add(new Slip(Component.translatable("kultivator.screen.status.stats"), null, Palette.SEAL_DARK));
		slips.add(statSlip("health", "+" + oneDecimal(Stats.healthBonus(data))));
		slips.add(statSlip("attack", "+" + oneDecimal(Stats.attackBonus(data))));
		slips.add(statSlip("speed", "+" + trimmed(Stats.speedBonus(data) * 100.0D) + "%"));
		slips.add(statSlip("armor", "+" + oneDecimal(Stats.armorBonus(data))));
		slips.add(statSlip("safe_fall", "+" + oneDecimal(Stats.safeFallBonus(data))));
		slips.add(statSlip("mining", "+" + trimmed(Stats.miningBonus(data) * 100.0D) + "%"));
		slips.add(statSlip("quality", "×" + String.format(Locale.ROOT, "%.2f", data.qualityFactor())));
		double toxicity = data.toxicity();
		slips.add(new Slip(Component.translatable("kultivator.screen.status.toxicity"), Component.literal(Msg.number(toxicity) + "%"),
				toxicity > 80.0D ? Palette.DANGER : toxicity > 50.0D ? Palette.WARN : Palette.INK_SOFT));
		int deviation = QiHud.deviationTicksNow();
		slips.add(new Slip(Component.translatable("kultivator.screen.status.deviation"),
				deviation > 0 ? Component.literal(clock(deviation)) : Component.translatable("kultivator.screen.none"),
				deviation > 0 ? Palette.DANGER : Palette.INK_FAINT));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.meditation_time"), duration(data.meditationTicks()), Palette.INK_SOFT));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.enlightenments"),
				Component.literal(String.valueOf(data.enlightenments())), Palette.onPaper(0xFFC060)));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.tribulations"),
				Component.literal(String.valueOf(data.tribulationsSurvived())), Palette.onPaper(0x9A7CFF)));
		if (data.ascended()) {
			slips.add(new Slip(Component.translatable("kultivator.screen.status.ascended"),
					Component.translatable("kultivator.screen.status.ascended.value"), Palette.GOLD_DARK));
		}
		slips.add(new Slip(Component.translatable("kultivator.screen.status.alchemy"),
				Component.translatable("kultivator.screen.status.alchemy.value", Alchemy.level(data), data.pillsRefined()),
				Palette.onPaper(0xFFB060)));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.failed"),
				Component.literal(String.valueOf(data.failedBreakthroughs())), data.failedBreakthroughs() > 0 ? Palette.WARN : Palette.INK_SOFT));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.hardcore"),
				Component.translatable(data.hardcore() ? "kultivator.screen.on" : "kultivator.screen.off"),
				data.hardcore() ? Palette.DANGER : Palette.INK_FAINT));
		slips.add(new Slip(Component.translatable("kultivator.screen.status.qualities"), null, Palette.SEAL_DARK));
		boolean any = false;
		for (Realm realm : Realm.values()) {
			if (realm.isMortal()) {
				continue;
			}
			BreakthroughQuality quality = data.quality(realm);
			if (quality != null) {
				any = true;
				slips.add(new Slip(realm.displayName(), quality.displayName(), Palette.onPaper(quality.color())));
			}
		}
		if (!any) {
			slips.add(new Slip(Component.translatable("kultivator.screen.status.qualities_none"), Component.empty(), Palette.INK_FAINT));
		}
	}

	private static Slip statSlip(String id, String value) {
		return new Slip(Component.translatable("kultivator.screen.status.stat." + id), Component.literal(value), Palette.onPaper(0x4FA36A));
	}

	private static String oneDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** One decimal, without a trailing ".0" ("2.5", "10"). */
	private static String trimmed(double value) {
		String text = oneDecimal(value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	/** Ticks as m:ss. */
	static String clock(int ticks) {
		int seconds = Math.max(0, ticks) / 20;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	static Component duration(long ticks) {
		long minutes = ticks / 1200L;
		if (minutes < 60L) {
			return Component.translatable("kultivator.screen.duration_min", minutes);
		}
		return Component.translatable("kultivator.screen.duration", minutes / 60L, minutes % 60L);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (Util.getMillis() - slipsBuiltAt > 1000L) {
			rebuildSlips();
		}
		CultivationData data = data();
		Rank rank = data.rank();
		int step = rank.step();
		if (lastStep >= 0 && step != lastStep) {
			bar.set(0.0F);
		}
		lastStep = step;
		float barT = bar.to((float) data.barFraction(), seconds);
		double maxQi = Qi.max(data);
		float qiT = qi.to(maxQi <= 0.0D ? 0.0F : (float) Math.min(1.0D, data.qi() / maxQi), seconds);
		float storedT = stored.to((float) Math.min(1.0D, data.storedQi() / Math.max(1.0D, rank.xpRequired())), seconds);
		drawDantian(g, data, rank, barT, qiT, storedT, mouseX, mouseY);
		// A fresh layer keeps the GUI's overlap bookkeeping short after the many small pieces of the circle.
		g.nextStratum();
		drawSlips(g, mouseX, mouseY, seconds);
	}

	private void drawDantian(GuiGraphicsExtractor g, CultivationData data, Rank rank, float barT, float qiT, float storedT, int mouseX, int mouseY) {
		long now = Util.getMillis();
		float r = radius;
		float t = Math.max(2.5F, Math.min(6.0F, r * 0.085F));
		boolean ready = ready(data, rank);
		if (ready) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 260.0D);
			Brush.halo(g, cx, cy, r, 6.0F, 0xFFE27A, 0.5F + 0.5F * pulse);
		}
		// Gold: cultivation.
		Brush.disc(g, cx + 1.0F, cy + 1.5F, r + 0.5F, 0x30000000);
		Brush.arc(g, cx, cy, r - t, r, 0.0F, 1.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.GOLD_DARK, 0.35F));
		Brush.arc(g, cx, cy, r - t, r, 0.0F, barT, Palette.GOLD);
		Brush.arc(g, cx, cy, r - 1.0F, r, 0.0F, barT, Palette.GOLD_LIGHT);
		if (barT > 0.005F) {
			double angle = -Math.PI / 2.0D + barT * Math.PI * 2.0D;
			Brush.dot(g, cx + (float) Math.cos(angle) * (r - t / 2.0F), cy + (float) Math.sin(angle) * (r - t / 2.0F), t * 0.6F, Palette.GOLD_LIGHT);
		}
		// Blue: qi.
		float qOuter = r - t - 2.0F;
		float qInner = qOuter - t;
		Brush.arc(g, cx, cy, qInner, qOuter, 0.0F, 1.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.QI, 0.2F));
		Brush.arc(g, cx, cy, qInner, qOuter, 0.0F, qiT, Palette.QI);
		// Violet: stored qi (relative to one full bar).
		float sOuter = qInner - 2.0F;
		float sInner = sOuter - t * 0.8F;
		Brush.arc(g, cx, cy, sInner, sOuter, 0.0F, 1.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.STORED, 0.16F));
		Brush.arc(g, cx, cy, sInner, sOuter, 0.0F, storedT, Palette.STORED);
		// The ink core with qi circulating inside.
		float core = Math.max(6.0F, sInner - 2.5F);
		Brush.disc(g, cx, cy, core + 1.0F, Palette.GOLD_DARK);
		Brush.band(g, cx, cy, core, -core, core, Palette.NIGHT_MIST, Palette.NIGHT);
		int root = Palette.opaque(Element.mainColor(rootKnown(data) ? data.rootElements() : 0));
		float spin = (now % 6000L) / 6000.0F;
		int swirl = Palette.mix(Palette.NIGHT, root, 0.35F);
		Brush.arc(g, cx, cy, core * 0.72F, core * 0.78F, spin, spin + 0.28F, swirl);
		Brush.arc(g, cx, cy, core * 0.72F, core * 0.78F, spin + 0.5F, spin + 0.78F, swirl);

		Component name = Brush.bold(rank.realm().displayName());
		Component second = rank.realm().isMortal() ? Component.translatable(rootKnown(data) ? "kultivator.screen.status.root_known"
				: "kultivator.screen.status.root_unknown") : rank.realm().layers() > 1
				? Component.translatable("kultivator.screen.status.layer_stage", rank.layer() + 1, rank.stageName())
				: Component.translatable("kultivator.screen.status.stage", rank.stageName());
		Component third = Component.literal(Math.round(barT * 100.0F) + "%");
		float room = core * 1.62F;
		// A long realm name is written over two lines rather than shrunk into dust.
		List<Component> nameLines = List.of(name);
		float s1 = Brush.fitScale(font, name, room, 1.35F, 0.5F);
		String plain = rank.realm().displayName().getString();
		int space = plain.indexOf(' ', plain.length() / 2 - 1);
		if (space < 0) {
			space = plain.lastIndexOf(' ');
		}
		if (s1 < 0.8F && space > 0) {
			Component a = Brush.bold(Component.literal(plain.substring(0, space)));
			Component b = Brush.bold(Component.literal(plain.substring(space + 1)));
			nameLines = List.of(a, b);
			s1 = Math.min(Brush.fitScale(font, a, room * 0.92F, 1.2F, 0.5F), Brush.fitScale(font, b, room * 0.92F, 1.2F, 0.5F));
		}
		float s2 = Math.min(0.85F, Brush.fitScale(font, second, room, 0.85F, 0.45F));
		float s3 = 0.7F;
		float total = 9.0F * s1 * nameLines.size() + 9.0F * s2 + 9.0F * s3 + 3.0F;
		float ty = cy - total / 2.0F;
		for (Component line : nameLines) {
			Brush.fitCentered(g, font, line, cx, ty, room, Palette.opaque(rank.realm().color()), s1, 0.5F, true);
			ty += 9.0F * s1;
		}
		ty += 1.5F;
		Brush.fitCentered(g, font, second, cx, ty, room, Palette.JADE_PALE, s2, 0.45F, false);
		ty += 9.0F * s2 + 1.5F;
		Brush.fitCentered(g, font, third, cx, ty, room, ready ? Palette.GOLD_LIGHT : Palette.GOLD, s3, 0.5F, false);

		// Which ring is under the mouse.
		float dx = mouseX - cx;
		float dy = mouseY - cy;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);
		if (distance <= r + 1.0F) {
			if (distance >= r - t - 1.0F) {
				screen.showTooltip(List.of(Component.translatable("kultivator.screen.status.cultivation"),
						Component.literal(Msg.number(data.xp()) + " / " + Msg.number(rank.xpRequired())).withColor(Palette.GOLD_LIGHT),
						Component.translatable(ready ? "kultivator.screen.status.ready" : "kultivator.screen.status.cultivation.tip")));
			} else if (distance >= qInner - 1.0F) {
				screen.showTooltip(List.of(Component.translatable("kultivator.screen.status.qi"),
						Component.literal(Msg.number(data.qi()) + " / " + Msg.number(Qi.max(data))).withColor(Palette.QI_LIGHT),
						Component.translatable("kultivator.screen.status.qi.tip")));
			} else if (distance >= sInner - 1.0F) {
				screen.showTooltip(List.of(Component.translatable(rank.isMax() ? "kultivator.screen.status.stored_immortal"
								: "kultivator.screen.status.stored"),
						Component.literal(Msg.number(data.storedQi())).withColor(Palette.STORED_LIGHT),
						Component.translatable("kultivator.screen.status.stored.tip")));
			} else {
				screen.showTooltip(List.of(rank.displayName().withColor(rank.realm().color()),
						Component.translatable("kultivator.screen.status.total_xp", Msg.number(data.totalXp()))));
			}
		}
	}

	private void drawSlips(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (slipW < 30 || slips.isEmpty()) {
			return;
		}
		float slatH = Math.max(9.0F, Math.min(12.0F, slipH / (float) slips.size()));
		float scale = slatH >= 11.0F ? 0.8F : 0.68F;
		float total = slips.size() * slatH;
		float maxScroll = Math.max(0.0F, total - slipH);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);

		// Binding cords running down over the slips.
		g.fill(slipX + 4, slipY, slipX + 5, slipY + slipH, Palette.CORD);
		g.fill(slipX + slipW - 5, slipY, slipX + slipW - 4, slipY + slipH, Palette.CORD);
		g.enableScissor(slipX, slipY, slipX + slipW, slipY + slipH);
		for (int i = 0; i < slips.size(); i++) {
			float top = slipY + i * slatH - offset;
			if (top + slatH < slipY || top > slipY + slipH) {
				continue;
			}
			Slip slip = slips.get(i);
			float textY = top + (slatH - 1.0F - 8.0F * scale) / 2.0F;
			if (slip.value() == null) {
				Brush.rect(g, slipX + 8.0F, top + slatH / 2.0F - 1.5F, slipX + 11.0F, top + slatH / 2.0F + 1.5F, Palette.SEAL);
				Brush.fit(g, font, Brush.bold(slip.label()), slipX + 14.0F, textY, slipW - 22.0F, slip.color(), scale, 0.5F, false);
				Brush.rect(g, slipX + 8.0F, top + slatH - 1.0F, slipX + slipW - 8.0F, top + slatH - 0.5F, Palette.argb(0xA0, Palette.GOLD));
				continue;
			}
			int slat = i % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT;
			Brush.rect(g, slipX + 1.0F, top, slipX + slipW - 1.0F, top + slatH - 1.0F, slat);
			Brush.rect(g, slipX + 1.0F, top, slipX + slipW - 1.0F, top + 0.5F, 0x40FFFFFF);
			Brush.rect(g, slipX + 1.0F, top + slatH - 1.5F, slipX + slipW - 1.0F, top + slatH - 1.0F, 0x30000000);
			Brush.rect(g, slipX + 2.0F, top + 1.0F, slipX + 3.0F, top + slatH - 2.0F, Palette.BAMBOO_KNOT);
			Brush.rect(g, slipX + slipW - 3.0F, top + 1.0F, slipX + slipW - 2.0F, top + slatH - 2.0F, Palette.BAMBOO_KNOT);
			g.fill(slipX + 4, Math.round(top), slipX + 5, Math.round(top + slatH - 1.0F), Palette.CORD);
			g.fill(slipX + slipW - 5, Math.round(top), slipX + slipW - 4, Math.round(top + slatH - 1.0F), Palette.CORD);

			float inner = slipW - 16.0F;
			float valueWidth = Math.min(inner * 0.55F, font.width(slip.value()) * scale);
			float labelRoom = inner - valueWidth - 3.0F;
			Brush.fit(g, font, slip.label(), slipX + 8.0F, textY, labelRoom, Palette.INK_SOFT, scale, 0.45F, false);
			float valueScale = Brush.fitScale(font, slip.value(), inner * 0.55F, scale, 0.45F);
			float drawnValue = Math.min(inner * 0.55F, font.width(slip.value()) * valueScale);
			Brush.fit(g, font, slip.value(), slipX + slipW - 8.0F - drawnValue, textY, inner * 0.55F, slip.color(), scale, 0.45F, false);
		}
		g.disableScissor();
		if (maxScroll > 0.0F) {
			// A jade bead sliding on the right cord shows where the list is.
			float bead = slipY + 3.0F + (slipH - 6.0F) * (offset / maxScroll);
			Brush.disc(g, slipX + slipW - 4.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, slipX + slipW - 5.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX >= slipX && mouseX < slipX + slipW && mouseY >= slipY && mouseY < slipY + slipH) {
			scroll -= (float) amount * 12.0F;
			return true;
		}
		return false;
	}
}
