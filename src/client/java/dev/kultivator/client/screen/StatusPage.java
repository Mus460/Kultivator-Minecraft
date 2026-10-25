package dev.kultivator.client.screen;

import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.QiHud;
import dev.kultivator.client.goals.PrepView;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.QiFormations;
import dev.kultivator.cultivation.Reforge;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diri › Status (DESIGN 43.5): the big dantian circle (gold ring cultivation, blue ring qi, violet ring stored qi, the
 * realm written in the ink core) with the numbers on a column of bamboo slips beside it ({@code DaftarGulir}: wheel, rail,
 * ↑↓ after Tab), and the meditate / breakthrough / root test talismans under them. The "Persiapan" coin above the slips
 * folds out the breakthrough forecast (DESIGN 19.3.10). A "Perkokoh Fondasi" talisman joins the row while a recorded
 * quality can still rise ({@code reforge}), and at True Immortal Peak the breakthrough talisman becomes "Padatkan Bintang"
 * ({@code star}, with Shift {@code star:all}); the player's Immortal Stars circle the dantian (DESIGN 19.3.11). The Cave
 * Abode point moved to Diri › Harta › Kediaman (G66, {@link HartaPage}). All text is drawn at 1.0 (43.0.5).
 */
final class StatusPage extends Page {

	private static final int BUTTON_H = 18;
	private static final int COIN_H = 12;
	private static final int SLAT_H = 12;
	private static final int PREP_LINE_H = 10;
	private static final String S = "kultivator.goals.status.";
	/** The qi formation tier Perkokoh Fondasi needs (DESIGN 19.3.11; the server checks it again). */
	private static final int REFORGE_FORMATION = 2;
	/** At most this many Immortal Stars orbit the dantian (the count is written beside it). */
	private static final int ORBIT_STARS = 18;

	private final Eased bar = new Eased(5.0F);
	private final Eased qi = new Eased(5.0F);
	private final Eased stored = new Eased(5.0F);
	private final List<Slip> slips = new ArrayList<>();
	private final DaftarGulir slipList = new DaftarGulir().judul(i -> i < slips.size() && slips.get(i).value() == null, SLAT_H);
	private long slipsBuiltAt;
	private boolean builtMortalUntested;
	private int lastStep = -1;
	/** The Persiapan fold-out (DESIGN 19.3.10) replaces the bamboo slips while open. */
	private static boolean prepOpen;
	/** The forecast pre-wrapped into lines of the slip column's width (rebuilt each frame from {@code PrepView}). */
	private final List<PrepLine> prepLines = new ArrayList<>();
	private final DaftarGulir prepList = new DaftarGulir().judul(i -> i < prepLines.size() && prepLines.get(i).heading() != null, SLAT_H);
	/** Phase-7 controls that exist only in some states (DESIGN 19.3.5, 19.3.11). */
	private boolean builtSummit;
	private boolean builtReforge;

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

	/** One line of the Persiapan fold-out: a heading, or one wrapped line of a forecast row. */
	private record PrepLine(@Nullable Component heading, @Nullable FormattedCharSequence text, int color) {
	}

	StatusPage(CultivationScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.screen.tab.status");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.screen.tab.status.short");
	}

	@Override
	public String helpTopic() {
		return "meditation";
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
		slipW = x + w - slipX;
		// The Persiapan coin (DESIGN 19.3.10) on top of the slip column.
		JadeCoin prep = new JadeCoin(slipX + 2, y, Math.max(TalismanButton.MIN_W, slipW - 4), COIN_H,
				() -> Component.translatable("kultivator.goals.prep.coin"), () -> prepOpen);
		prep.onPress(() -> {
			prepOpen = !prepOpen;
			prepList.mulaiUlang(Util.getMillis());
			slipList.mulaiUlang(Util.getMillis());
		});
		prep.tooltip(() -> List.of(Component.translatable("kultivator.goals.prep.coin"), Component.translatable("kultivator.goals.prep.coin.tip")
				.withStyle(ChatFormatting.GRAY)));
		add(prep);
		slipY = y + COIN_H + 2;
		slipH = availH - COIN_H - 2;

		List<TalismanButton> row = new ArrayList<>();
		TalismanButton meditate = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable(data().meditating()
				? "kultivator.screen.btn.stop_meditating" : "kultivator.screen.btn.meditate"));
		meditate.stampedWhen(() -> data().meditating());
		meditate.onPress(() -> send(Actions.MEDITATE_TOGGLE));
		meditate.tooltip(() -> List.of(Component.translatable("kultivator.screen.btn.meditate"),
				Component.translatable("kultivator.screen.btn.meditate.tip"),
				Component.translatable("kultivator.screen.key_hint", KeyHints.name(Keys.MEDITATE))));
		row.add(meditate);

		builtSummit = data.rank().isMax();
		if (builtSummit) {
			// At True Immortal Peak no breakthrough is left: the talisman condenses Immortal Stars instead (DESIGN 19.3.11).
			TalismanButton star = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable(S + "star", ImmortalPerks.stars(data()) + 1));
			star.enabledWhen(() -> Keys.connected() && canCondense(data()));
			star.alasanWhen(() -> Keys.connected() ? Component.translatable(S + "star.cost", Msg.number(ImmortalPerks.nextStarCost(data())),
					Msg.number(data().storedQi())) : luring());
			star.urgentWhen(() -> canCondense(data()));
			star.onPress(() -> send(Actions.KULTIVASI, 0, Minecraft.getInstance().hasShiftDown() ? "star:all" : "star"));
			star.tooltip(StatusPage::starTip);
			row.add(star);
		} else {
			TalismanButton breakthrough = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable("kultivator.screen.btn.breakthrough"));
			breakthrough.enabledWhen(() -> canBreakthrough(data()));
			breakthrough.alasanWhen(StatusPage::breakthroughWhy);
			breakthrough.urgentWhen(() -> canBreakthrough(data()));
			breakthrough.onPress(() -> send(Actions.BREAKTHROUGH));
			breakthrough.tooltip(StatusPage::breakthroughTip);
			row.add(breakthrough);
		}

		builtReforge = reforgeOffered(data);
		if (builtReforge) {
			// Perkokoh Fondasi (DESIGN 19.3.11): raises the lowest recorded foundation quality.
			TalismanButton reforge = new TalismanButton(0, 0, 0, BUTTON_H, () -> reforging(data())
					? Component.translatable(S + "reforge.running", clock((int) reforgeLeft(data())))
					: Component.translatable(S + "reforge"));
			reforge.stampedWhen(() -> reforging(data()));
			reforge.enabledWhen(() -> Keys.connected() && canReforge(data()));
			reforge.alasanWhen(() -> Keys.connected() ? reforgeWhy(data()) : luring());
			reforge.urgentWhen(() -> canReforge(data()));
			reforge.onPress(() -> send(Actions.KULTIVASI, 0, "reforge"));
			reforge.tooltip(StatusPage::reforgeTip);
			row.add(reforge);
		}

		if (builtMortalUntested) {
			TalismanButton root = new TalismanButton(0, 0, 0, BUTTON_H, () -> Component.translatable("kultivator.screen.btn.root_test"));
			root.enabledWhen(() -> mortalUntested(data()));
			root.urgentWhen(() -> mortalUntested(data()));
			root.onPress(() -> {
				// The testing ceremony plays around the player in the world: close the scroll so it can be watched.
				send(Actions.ROOT_TEST);
				HubNav.tutupSemua();
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
	public boolean needsRebuild() {
		CultivationData data = data();
		return builtMortalUntested != mortalUntested(data) || builtSummit != data.rank().isMax() || builtReforge != reforgeOffered(data);
	}

	// ================================================================== phase 7: Perkokoh Fondasi, stars

	/** True at True Immortal Peak with enough Qi Abadi for the next star. */
	static boolean canCondense(CultivationData data) {
		double cost = ImmortalPerks.nextStarCost(data);
		return data.rank().isMax() && cost > 0.0D && data.storedQi() + 1.0E-6D >= cost;
	}

	private static List<Component> starTip() {
		CultivationData data = data();
		int stars = ImmortalPerks.stars(data);
		double cost = ImmortalPerks.nextStarCost(data);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(S + "star", stars + 1));
		lines.add(Component.translatable(S + "star.count", stars).withColor(Palette.GOLD_LIGHT));
		lines.add(Component.translatable(S + "star.cost", Msg.number(cost), Msg.number(data.storedQi()))
				.withStyle(canCondense(data) ? ChatFormatting.GREEN : ChatFormatting.RED));
		lines.add(Component.translatable(S + "star.tip").withStyle(ChatFormatting.GRAY));
		return lines;
	}

	/** Perkokoh Fondasi is offered from Qi Refining on while some recorded quality is below Heavenly. */
	static boolean reforgeOffered(CultivationData data) {
		return !data.realm().isMortal() && Reforge.target(data) != null;
	}

	/** Ticks left of the running Perkokoh Fondasi ritual ({@code sessionExtra.reforge_end}), 0 when none runs. */
	static long reforgeLeft(CultivationData data) {
		return Math.max(0L, data.sessionExtra().getLongOr("reforge_end", 0L) - ClientCultivation.serverGameTime());
	}

	static boolean reforging(CultivationData data) {
		return reforgeLeft(data) > 0L;
	}

	/** Everything the server checks before the ritual, so the talisman lights up only when it would start. */
	static boolean canReforge(CultivationData data) {
		return Reforge.target(data) != null && !reforging(data) && data.meditating() && data.formationTier() >= REFORGE_FORMATION
				&& !data.deviating() && !data.breakingThrough() && data.storedQi() + 1.0E-6D >= Reforge.cost(data);
	}

	/** Why Perkokoh Fondasi is dim: the first unmet line of its tooltip (footer reason of the disabled talisman). */
	private static Component reforgeWhy(CultivationData data) {
		if (reforging(data)) {
			return Component.translatable(S + "reforge.running", clock((int) reforgeLeft(data)));
		}
		if (data.deviating()) {
			return Component.translatable("kultivator.screen.bt.deviation");
		}
		if (!data.meditating() || data.formationTier() < REFORGE_FORMATION) {
			return Component.translatable(S + "reforge.need", QiFormations.name(REFORGE_FORMATION));
		}
		return Component.translatable(S + "reforge.cost", Msg.number(Reforge.cost(data)), Msg.number(data.storedQi()));
	}

	private static Component luring() {
		return Component.translatable("kultivator.gui43.m02.luring");
	}

	private static List<Component> reforgeTip() {
		CultivationData data = data();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(S + "reforge"));
		Realm target = Reforge.target(data);
		if (target == null) {
			lines.add(Component.translatable(S + "reforge.none").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		BreakthroughQuality quality = data.quality(target);
		lines.add(Component.translatable(S + "reforge.target", target.displayName().copy().withColor(target.color()),
				quality == null ? Component.empty() : quality.displayName().copy().withColor(quality.color())));
		double cost = Reforge.cost(data);
		lines.add(Component.translatable(S + "reforge.cost", Msg.number(cost), Msg.number(data.storedQi()))
				.withStyle(data.storedQi() + 1.0E-6D >= cost ? ChatFormatting.GREEN : ChatFormatting.RED));
		boolean seat = data.meditating() && data.formationTier() >= REFORGE_FORMATION;
		lines.add(Component.translatable(S + "reforge.need", QiFormations.name(REFORGE_FORMATION))
				.withStyle(seat ? ChatFormatting.GREEN : ChatFormatting.RED));
		if (data.deviating()) {
			lines.add(Component.translatable("kultivator.screen.bt.deviation").withStyle(ChatFormatting.RED));
		}
		if (reforging(data)) {
			lines.add(Component.translatable(S + "reforge.running", clock((int) reforgeLeft(data))).withStyle(ChatFormatting.GOLD));
		}
		lines.add(Component.translatable(S + "reforge.tip").withStyle(ChatFormatting.GRAY));
		return lines;
	}

	/** Immortal Stars circling the dantian (at most {@link #ORBIT_STARS}) and the count beside the circle. */
	private void drawStars(GuiGraphicsExtractor g, int stars, float r, long now) {
		int shown = Math.min(ORBIT_STARS, stars);
		double spin = (now % 24000L) / 24000.0D * Math.PI * 2.0D;
		float orbit = r + 2.5F;
		for (int i = 0; i < shown; i++) {
			double angle = spin + i * Math.PI * 2.0D / shown;
			float sx = cx + (float) Math.cos(angle) * orbit;
			float sy = cy + (float) Math.sin(angle) * orbit;
			float twinkle = 0.6F + 0.4F * (float) Math.sin(now / 300.0D + i * 1.7D);
			int color = Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, twinkle);
			Brush.rect(g, sx - 1.6F, sy - 0.35F, sx + 1.6F, sy + 0.35F, color);
			Brush.rect(g, sx - 0.35F, sy - 1.6F, sx + 0.35F, sy + 1.6F, color);
			Brush.dot(g, sx, sy, 0.6F, 0xFFFFFFFF);
		}
		Brush.text(g, font, Component.literal("★" + stars), cx + r * 0.72F, cy - r - 4.0F, Palette.GOLD_DARK, 1.0F, false);
	}

	@Override
	public void onData() {
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

	/** Why the breakthrough talisman is dim: the first red line of its tooltip (footer reason of the disabled talisman). */
	private static @Nullable Component breakthroughWhy() {
		CultivationData data = data();
		Rank rank = data.rank();
		if (data.breakingThrough()) {
			return Component.translatable("kultivator.screen.bt.running");
		}
		if (data.deviating()) {
			return Component.translatable("kultivator.screen.bt.deviation");
		}
		if (rank.realm().isMortal() && !data.rootTested()) {
			return Component.translatable("kultivator.screen.bt.root_first");
		}
		if (!rank.isBottleneck() || !rank.nextIsBreakthrough()) {
			return Component.translatable("kultivator.screen.bt.not_peak");
		}
		if (!data.barFull()) {
			return Component.translatable("kultivator.screen.bt.not_full", Msg.number(data.xp()), Msg.number(rank.xpRequired()));
		}
		return mortalRootUnknown(data) ? Component.translatable("kultivator.roots.info.reading") : null;
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
		// The Persiapan summary (DESIGN 19.3.10): strikes, quality bonus, healing needed.
		lines.addAll(PrepView.tooltip(data));
		lines.add(Component.translatable("kultivator.screen.key_hint_bt", KeyHints.name(Keys.MEDITATE)).withStyle(ChatFormatting.DARK_GRAY));
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
		float room = core * 1.8F;
		// A long realm name is written over two lines (never shrunk below 1.0, 43.0.5); a word still too wide loses its
		// bold weight before it is cut with "…".
		List<Component> nameLines = List.of(name);
		float s1 = Brush.fitScale(font, name, room, 1.35F, 1.0F);
		String plain = rank.realm().displayName().getString();
		int space = plain.indexOf(' ', plain.length() / 2 - 1);
		if (space < 0) {
			space = plain.lastIndexOf(' ');
		}
		if (font.width(name) * s1 > room && space > 0) {
			nameLines = List.of(fitWeight(plain.substring(0, space), room), fitWeight(plain.substring(space + 1), room));
			s1 = 1.0F;
		} else if (font.width(name) > room) {
			nameLines = List.of(fitWeight(plain, room));
			s1 = 1.0F;
		}
		float total = 9.0F * s1 * nameLines.size() + 9.0F + 9.0F + 3.0F;
		float ty = cy - total / 2.0F;
		for (Component line : nameLines) {
			Brush.fitCentered(g, font, line, cx, ty, room, Palette.opaque(rank.realm().color()), s1, 1.0F, true);
			ty += 9.0F * s1;
		}
		ty += 1.5F;
		Brush.fitCentered(g, font, second, cx, ty, room, Palette.JADE_PALE, 1.0F, 1.0F, false);
		ty += 9.0F + 1.5F;
		Brush.fitCentered(g, font, third, cx, ty, room, ready ? Palette.GOLD_LIGHT : Palette.GOLD, 1.0F, 1.0F, false);
		int stars = ImmortalPerks.stars(data);
		if (stars > 0) {
			drawStars(g, stars, r, now);
		}

		// Which ring is under the mouse.
		float dx = mouseX - cx;
		float dy = mouseY - cy;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);
		if (distance <= r + 1.0F) {
			if (distance >= r - t - 1.0F) {
				tooltip(List.of(Component.translatable("kultivator.screen.status.cultivation"),
						Component.literal(Msg.number(data.xp()) + " / " + Msg.number(rank.xpRequired())).withColor(Palette.GOLD_LIGHT),
						Component.translatable(ready ? "kultivator.screen.status.ready" : "kultivator.screen.status.cultivation.tip")));
			} else if (distance >= qInner - 1.0F) {
				tooltip(List.of(Component.translatable("kultivator.screen.status.qi"),
						Component.literal(Msg.number(data.qi()) + " / " + Msg.number(Qi.max(data))).withColor(Palette.QI_LIGHT),
						Component.translatable("kultivator.screen.status.qi.tip")));
			} else if (distance >= sInner - 1.0F) {
				tooltip(List.of(Component.translatable(rank.isMax() ? "kultivator.screen.status.stored_immortal"
								: "kultivator.screen.status.stored"),
						Component.literal(Msg.number(data.storedQi())).withColor(Palette.STORED_LIGHT),
						Component.translatable("kultivator.screen.status.stored.tip")));
			} else {
				List<Component> tip = new ArrayList<>(List.of(rank.displayName().withColor(rank.realm().color()),
						Component.translatable("kultivator.screen.status.total_xp", Msg.number(data.totalXp()))));
				if (stars > 0) {
					tip.add(Component.translatable(S + "star.count", stars).withColor(Palette.GOLD_LIGHT));
				}
				tooltip(tip);
			}
		}
	}

	/** A bold word, or the plain word when bold does not fit {@code room} (it is cut with "…" only when even that is too wide). */
	private Component fitWeight(String word, float room) {
		Component bold = Brush.bold(Component.literal(word));
		return font.width(bold) <= room ? bold : Component.literal(word);
	}

	// ================================================================== the slip column (DaftarGulir)

	@Override
	protected void lukis(Lukis l) {
		if (slipW < 30) {
			return;
		}
		if (prepOpen) {
			lukisPrep(l);
			return;
		}
		if (slips.isEmpty()) {
			return;
		}
		// Binding cords running down behind the slips.
		l.g.fill(slipX + 4, slipY, slipX + 5, slipY + slipH, Palette.CORD);
		l.g.fill(slipX + slipW - 5, slipY, slipX + slipW - 4, slipY + slipH, Palette.CORD);
		slipList.gambar(l, "slips", slipX, slipY, slipX + slipW, slipY + slipH, slips.size(), SLAT_H, this::drawSlip, null, null);
	}

	/** One bamboo slip (label left, value right, both at 1.0; a value too long is cut and shown whole in the tooltip). */
	private void drawSlip(Lukis l, int i, int x0, int y0, int x1, int y1, boolean over, boolean chosen) {
		Slip slip = slips.get(i);
		GuiGraphicsExtractor g = l.g;
		float textY = y0 + (y1 - y0 - 8) / 2.0F;
		if (slip.value() == null) {
			Brush.rect(g, x0 + 4.0F, (y0 + y1) / 2.0F - 1.5F, x0 + 7.0F, (y0 + y1) / 2.0F + 1.5F, Palette.SEAL);
			Brush.fit(g, font, Brush.bold(slip.label()), x0 + 10.0F, textY, x1 - x0 - 14.0F, slip.color(), 1.0F, 1.0F, false);
			Brush.rect(g, x0 + 4.0F, y1 - 0.5F, x1 - 4.0F, y1, Palette.argb(0xA0, Palette.GOLD));
			return;
		}
		int slat = i % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT;
		Brush.rect(g, x0 + 1.0F, y0, x1 - 1.0F, y1, over || chosen && l.difokus("slips") ? Palette.mix(slat, 0xFFFFFFFF, 0.25F) : slat);
		Brush.rect(g, x0 + 1.0F, y0, x1 - 1.0F, y0 + 0.5F, 0x40FFFFFF);
		Brush.rect(g, x0 + 1.0F, y1 - 0.5F, x1 - 1.0F, y1, 0x30000000);
		Brush.rect(g, x0 + 2.0F, y0 + 1.0F, x0 + 3.0F, y1 - 1.0F, Palette.BAMBOO_KNOT);
		Brush.rect(g, x1 - 3.0F, y0 + 1.0F, x1 - 2.0F, y1 - 1.0F, Palette.BAMBOO_KNOT);
		float inner = x1 - x0 - 12.0F;
		float valueRoom = inner * 0.55F;
		float valueWidth = Math.min(valueRoom, font.width(slip.value()));
		Brush.fit(g, font, slip.label(), x0 + 6.0F, textY, inner - valueWidth - 4.0F, Palette.INK_SOFT, 1.0F, 1.0F, false);
		Brush.fit(g, font, slip.value(), x1 - 6.0F - valueWidth, textY, valueRoom, slip.color(), 1.0F, 1.0F, false);
	}

	/** The Persiapan fold-out: the forecast of {@code PrepView} on a pale slip, wrapped to lines, in a {@code DaftarGulir}. */
	private void lukisPrep(Lukis l) {
		GuiGraphicsExtractor g = l.g;
		g.fill(slipX + 1, slipY, slipX + slipW - 1, slipY + slipH, Palette.argb(0x70, 0xFFF4E6B8));
		Brush.frame(g, slipX, slipY, slipX + slipW, slipY + slipH, Palette.argb(0xB0, Palette.GOLD_DARK));
		prepLines.clear();
		int room = slipW - 18;
		for (PrepView.Row row : PrepView.rows(data())) {
			if (row.heading()) {
				prepLines.add(new PrepLine(row.text(), null, row.color()));
				continue;
			}
			for (FormattedCharSequence line : font.split(row.text(), Math.max(10, room))) {
				prepLines.add(new PrepLine(null, line, row.color()));
			}
		}
		prepList.gambar(l, "prep", slipX + 2, slipY + 2, slipX + slipW - 2, slipY + slipH - 2, prepLines.size(), PREP_LINE_H,
				(lk, i, x0, y0, x1, y1, over, chosen) -> {
					PrepLine line = prepLines.get(i);
					if (line.heading() != null) {
						Brush.rect(lk.g, x0 + 2.0F, y0 + 3.5F, x0 + 5.0F, y0 + 6.5F, Palette.SEAL);
						Brush.fit(lk.g, font, Brush.bold(line.heading()), x0 + 7.0F, y0 + 2.0F, x1 - x0 - 9.0F, line.color(), 1.0F, 1.0F, false);
						Brush.rect(lk.g, x0 + 2.0F, y1 - 0.6F, x1 - 2.0F, y1, Palette.argb(0xA0, Palette.GOLD));
					} else if (line.text() != null) {
						Brush.text(lk.g, font, line.text(), x0 + 3.0F, y0 + 1.0F, line.color(), 1.0F, false);
					}
				}, null, null);
	}
}
