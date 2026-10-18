package dev.kultivator.client;

import dev.kultivator.client.codex.SpecInfo;
import dev.kultivator.client.codex.TeknikSession;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cultivation HUD in the bottom-left corner, in the strip under the chat and left of the offhand slot (DESIGN 5):
 * a dantian orb whose qi rises from the bottom in the root's colour, wrapped in a gold ring that fills clockwise with
 * the cultivation bar, a white-jade Perisai Qi arc, and violet beads while Stored Qi waits; the selected technique spec
 * as a seal tile (glyph, grade ribbon, cooldown sweep, mastery beads, scaled qi cost, charge pips, the red KEHANCURAN
 * tag, DESIGN 22.12.8); countdown arcs of lasting forms, domains and fields above the orb; and the realm, qi and
 * warnings beside it. Every value glides to its new number. Hidden with F1, F3, the HUD setting, an open chat, or
 * before the server sent any data. The quick seals, wheel, charge bar and toasts are {@code codex.CodexHud}.
 */
public final class QiHud implements HudElement {

	private static final float ORB = 11.0F;
	private static final int STORED_BEADS = 12;

	private static long seenVersion = -1L;
	private static int deviationAtSync;
	private static long syncClientTime;

	private final Eased qi = new Eased(5.0F);
	private final Eased bar = new Eased(5.0F);
	private final Eased stored = new Eased(4.0F);
	private final Eased storedAmount = new Eased(4.0F);
	private long lastFrame;
	private int lastStep = -1;

	/** Width the four stage pips need after a realm name. */
	private static final float PIP_ROOM = 17.0F;
	/** Tallest text block: its top then stays at or below the vanilla chat, which starts 40 px above the bottom. */
	private static final float MAX_TEXT_HEIGHT = 37.0F;

	/** One text line beside the orb: a component (shrunk or cut to fit) or a pre-split row, optionally with stage pips. */
	private record Line(@Nullable Component text, @Nullable FormattedCharSequence row, int color, float scale, int pips) {
		static Line of(Component text, int color, float scale) {
			return new Line(text, null, color, scale, -1);
		}
	}

	/** Deviation ticks left right now: the last snapshot's value, counted down on the client between snapshots. */
	public static int deviationTicksNow() {
		Minecraft minecraft = Minecraft.getInstance();
		long now = minecraft.level == null ? syncClientTime : minecraft.level.getGameTime();
		if (ClientCultivation.version() != seenVersion) {
			seenVersion = ClientCultivation.version();
			deviationAtSync = ClientCultivation.data().deviationTicks();
			syncClientTime = now;
		}
		return (int) Math.max(0L, deviationAtSync - Math.max(0L, now - syncClientTime));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long nowMillis = Util.getMillis();
		float seconds = lastFrame == 0L ? 0.0F : Math.max(0.0F, Math.min(0.25F, (nowMillis - lastFrame) / 1000.0F));
		lastFrame = nowMillis;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()
				|| !EffectQuality.hudVisible() || !ClientCultivation.received() || minecraft.gui.screen() instanceof ChatScreen) {
			return;
		}
		CultivationData data = ClientCultivation.data();
		Rank rank = data.rank();
		Font font = minecraft.font;
		int height = g.guiHeight();
		int width = g.guiWidth();
		float cx = 20.0F;
		float cy = height - 20.0F;

		int step = rank.step();
		if (lastStep >= 0 && step != lastStep) {
			bar.set(0.0F);
		}
		lastStep = step;
		double maxQi = Math.max(1.0D, Qi.max(data));
		float qiShown = qi.to((float) Math.min(data.qi(), maxQi), seconds);
		float barShown = bar.to((float) data.barFraction(), seconds);
		double required = Math.max(1.0D, rank.xpRequired());
		float storedShown = stored.to((float) Math.min(1.0D, data.storedQi() / required), seconds);
		float storedNumber = storedAmount.to((float) data.storedQi(), seconds);

		drawOrb(g, data, rank, cx, cy, (float) (qiShown / maxQi), barShown, storedShown, nowMillis);

		// Everything stays left of the offhand slot (on the left for right-handed players), and for left-handed players
		// left of the hotbar attack indicator when it is shown there.
		boolean right = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
		int limit = right ? width / 2 - 91 - 29 - 4
				: minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR ? width / 2 - 91 - 22 - 4 : width / 2 - 91 - 4;
		int textX = 40;
		TechniqueSpec selected = data.selectedId().isEmpty() ? null : TechniqueRegistry.byId(data.selectedId());
		if (selected != null && 40 + 18 <= limit) {
			drawSeal(g, font, data, selected, 40, Math.round(cy) - 13, deltaTracker.getGameTimeDeltaPartialTick(false));
			textX = 62;
		} else if (TeknikSession.breakerOn() && 40 + 18 <= limit) {
			kehancuranTag(g, font, 49.0F, Math.round(cy) - 3.0F);
			textX = 62;
		}
		drawLasting(g, nowMillis, height);
		int room = limit - textX;
		if (room >= 24) {
			drawText(g, font, data, rank, textX, room, cy, height, qiShown, maxQi, barShown, storedNumber, nowMillis);
		}
	}

	/** Root tested and revealed (not still inside its testing ceremony). */
	private static boolean rootKnown(CultivationData data) {
		return data.rootTested() && !SpiritRoots.revealPending(data);
	}

	// ================================================================== orb

	private static void drawOrb(GuiGraphicsExtractor g, CultivationData data, Rank rank, float cx, float cy, float qiFraction, float barFraction,
								float storedFraction, long now) {
		float r = ORB;
		// The root is saved when its testing ceremony starts: keep the orb pale until the reveal.
		boolean known = rootKnown(data);
		int root = Palette.opaque(Element.mainColor(known ? data.rootElements() : 0));
		// A Mortal whose root is not known yet is not ready: the root test (and its reveal) comes first.
		boolean ready = data.barFull() && rank.nextIsBreakthrough() && !(rank.realm().isMortal() && !known);
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 240.0D);

		Brush.disc(g, cx, cy, r + 4.2F, 0x70101824);
		if (ready) {
			Brush.halo(g, cx, cy, r + 3.4F, 3.0F, 0xFFE07A, pulse);
		}
		// Glass and the qi inside, rising from the bottom.
		Brush.disc(g, cx, cy, r, 0xE0141E2C);
		float level = Math.max(0.0F, Math.min(1.0F, qiFraction));
		float surface = r - 2.0F * r * level;
		if (level > 0.0F) {
			Brush.band(g, cx, cy, r - 0.5F, surface, r, Palette.lighten(root, 0.1F), Palette.darken(root, 0.5F));
			// The shimmering surface line.
			if (level < 0.995F) {
				int shine = Palette.lighten(root, 0.65F);
				float halfChord = (float) Math.sqrt(Math.max(0.0F, (r - 0.8F) * (r - 0.8F) - surface * surface));
				for (float sx = -halfChord; sx < halfChord; sx += 1.0F) {
					float wave = (float) Math.sin(sx * 0.8F + now / 180.0D) * 0.45F;
					int alpha = Math.round(150 + 90 * (float) Math.sin(sx * 0.5F - now / 260.0D));
					Brush.rect(g, cx + sx, cy + surface + wave - 0.5F, cx + sx + 1.0F, cy + surface + wave + 0.5F, Palette.argb(alpha, shine));
				}
			}
		}
		// A glint on the glass.
		Brush.disc(g, cx - r * 0.38F, cy - r * 0.42F, r * 0.2F, 0x50FFFFFF);
		Brush.disc(g, cx - r * 0.18F, cy - r * 0.6F, r * 0.08F, 0x40FFFFFF);

		// The gold ring: cultivation, clockwise from the top.
		int gold = ready ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse) : Palette.GOLD;
		Brush.arc(g, cx, cy, r + 0.8F, r + 2.8F, 0.0F, 1.0F, 0xFF3A2D12);
		Brush.arc(g, cx, cy, r + 0.8F, r + 2.8F, 0.0F, barFraction, gold);
		if (barFraction > 0.01F && barFraction < 0.999F) {
			double angle = -Math.PI / 2.0D + barFraction * Math.PI * 2.0D;
			Brush.dot(g, cx + (float) Math.cos(angle) * (r + 1.8F), cy + (float) Math.sin(angle) * (r + 1.8F), 1.3F, Palette.GOLD_LIGHT);
		}

		// Perisai Qi: a white-jade arc between the gold ring and the beads (pool / its maximum, DESIGN 22.12.8).
		double shield = TeknikSession.shield();
		if (shield > 0.01D) {
			double max = TeknikSession.shieldMax();
			if (max <= 0.0D) {
				Minecraft minecraft = Minecraft.getInstance();
				max = minecraft.player == null ? shield : Math.max(shield, minecraft.player.getMaxHealth() * 2.0D);
			}
			float share = (float) Math.max(0.0D, Math.min(1.0D, shield / Math.max(0.01D, max)));
			Brush.arc(g, cx, cy, r + 3.0F, r + 4.1F, 0.0F, share, 0xFFE4F4EC);
		}

		// Violet beads while Stored Qi waits (lit share = stored / one full bar), turning slowly.
		if (data.storedQi() > 0.5D) {
			int lit = Math.max(1, (int) Math.ceil(STORED_BEADS * storedFraction));
			double turn = (now % 12000L) / 12000.0D * Math.PI * 2.0D;
			float beadPulse = 0.6F + 0.4F * (float) Math.sin(now / 300.0D);
			for (int i = 0; i < STORED_BEADS; i++) {
				double angle = -Math.PI / 2.0D + turn + i * Math.PI * 2.0D / STORED_BEADS;
				float bx = cx + (float) Math.cos(angle) * (r + 5.2F);
				float by = cy + (float) Math.sin(angle) * (r + 5.2F);
				int color = i < lit ? Palette.argb(Math.round(255 * beadPulse), Palette.STORED_LIGHT) : Palette.argb(0x55, Palette.STORED);
				Brush.dot(g, bx, by, i < lit ? 1.1F : 0.8F, color);
			}
		}
	}

	// ================================================================== technique seal

	/**
	 * The selected spec as a seal tile (DESIGN 22.12.8): glyph, grade ribbon, cooldown sweep, four mastery beads, the qi
	 * cost after mastery and the realm gap, charge pips above it and the red KEHANCURAN tag under it while the switch is on.
	 */
	private static void drawSeal(GuiGraphicsExtractor g, Font font, CultivationData data, TechniqueSpec spec, int x, int y, float partialTick) {
		int size = 18;
		int qi = SpecInfo.qi(data, spec);
		boolean affordable = data.qi() + 1.0E-6D >= qi;
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xA0101824);
		int paper = affordable ? Palette.TALISMAN : Palette.grey(Palette.TALISMAN, 0.6F);
		g.fill(x, y, x + size, y + size, paper);
		long left = SpecInfo.cooldownLeft(spec);
		int total = SpecInfo.cooldown(data, spec);
		if (left > 0L && total > 0) {
			float fraction = Math.min(1.0F, Math.max(0.0F, (left - partialTick) / total));
			g.enableScissor(x, y, x + size, y + size);
			Brush.pie(g, x + size / 2.0F, y + size / 2.0F, size * 0.75F, 1.0F - fraction, 1.0F, Palette.mix(paper, 0xFF1A2230, 0.6F));
			g.disableScissor();
		}
		int seal = affordable ? Palette.SEAL : 0xFF6A6A6A;
		Brush.frame(g, x, y, x + size, y + size, seal);
		Brush.frame(g, x + 1, y + 1, x + size - 1, y + size - 1, Palette.argb(0x70, seal));
		int ribbon = SpecInfo.ribbon(spec.grade());
		for (int i = 0; i < 5; i++) {
			g.fill(x + size - 5 + i, y + i, x + size, y + i + 1, ribbon);
		}
		int ink = SpecInfo.ink(spec);
		Glyphs.spec(g, spec, x + size / 2.0F, y + size / 2.0F - 1.0F, 10.0F, left > 0L ? Palette.fade(ink, 0.55F) : ink);
		int mastery = data.knows(spec.id()) ? SpecInfo.mastery(data, spec) : 0;
		for (int i = 0; i < 4; i++) {
			Brush.dot(g, x + size / 2.0F + (i - 1.5F) * 2.6F, y + size - 2.2F, 0.7F, i < mastery ? Palette.GOLD : Palette.argb(0x60, 0x3A2D12));
		}
		if (left > 0L) {
			Component secs = Component.literal(String.valueOf((left + 19L) / 20L));
			Brush.centered(g, font, secs, x + size / 2.0F, y + size / 2.0F - 2.5F, 0xFFFFFFFF, 0.6F, true);
		}
		Component cost = Component.literal(SpecInfo.compact(qi));
		Brush.centered(g, font, cost, x + size / 2.0F, y + size + 2.0F, affordable ? Palette.QI_LIGHT : 0xFFFF7A6A, 0.6F, true);
		CompoundTag charges = TeknikSession.charges(spec.id());
		if (charges != null) {
			int max = Math.max(1, Math.min(8, charges.getIntOr("max", 1)));
			int have = Math.max(0, Math.min(max, charges.getIntOr("n", max)));
			for (int i = 0; i < max; i++) {
				Brush.dot(g, x + size / 2.0F + (i - (max - 1) / 2.0F) * 3.0F, y - 2.5F, 0.9F, i < have ? Palette.QI_LIGHT : Palette.argb(0x70, 0x3A4A5A));
			}
		}
		if (TeknikSession.breakerOn()) {
			kehancuranTag(g, font, x + size / 2.0F, y + size + 7.5F);
		}
	}

	/** The red "KEHANCURAN" tag (Mode Kehancuran on and allowed). */
	private static void kehancuranTag(GuiGraphicsExtractor g, Font font, float cx, float y) {
		float pulse = 0.6F + 0.4F * (float) Math.sin(Util.getMillis() / 250.0D);
		Component tag = Component.translatable("kultivator.codex.hud.kehancuran").withStyle(net.minecraft.ChatFormatting.BOLD);
		Brush.centered(g, font, tag, cx, y, Palette.mix(0xFFB3281D, 0xFFFF5B3A, pulse), 0.42F, true);
	}

	/** Countdown arcs for forms, domains, auras, toggles and fields above the orb, in their Dao colour. */
	private static void drawLasting(GuiGraphicsExtractor g, long now, int height) {
		List<TeknikSession.Lasting> lasting = TeknikSession.lasting();
		if (lasting.isEmpty()) {
			return;
		}
		long time = ClientCultivation.serverGameTime();
		int shown = 0;
		for (TeknikSession.Lasting entry : lasting) {
			if (shown >= 5) {
				break;
			}
			TechniqueSpec spec = TechniqueRegistry.byId(entry.id());
			int color = spec == null || spec.dao() == null ? Palette.GOLD : Palette.opaque(spec.dao().color());
			float cx = 6.0F + shown * 8.0F;
			float cy = height - 40.0F;
			Brush.disc(g, cx, cy, 3.4F, 0xA0101824);
			if (entry.end() <= 0L) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
				Brush.arc(g, cx, cy, 2.2F, 3.4F, 0.0F, 1.0F, Palette.mix(color, 0xFFFFFFFF, 0.3F * pulse));
			} else {
				float share = Math.max(0.0F, Math.min(1.0F, (entry.end() - time) / (float) Math.max(1L, entry.end() - entry.start())));
				Brush.arc(g, cx, cy, 2.2F, 3.4F, 0.0F, share, color);
			}
			Brush.dot(g, cx, cy, 0.8F, color);
			shown++;
		}
	}

	// ================================================================== text

	private static void drawText(GuiGraphicsExtractor g, Font font, CultivationData data, Rank rank, int x, int room, float cy, int height,
								 float qiShown, double maxQi, float barShown, float storedNumber, long now) {
		List<Line> lines = new ArrayList<>();
		Line storedLine = null;
		Line toxicityLine = null;
		Line meditatingLine = null;
		addRankLines(lines, font, rank, room);
		boolean untested = rank.realm().isMortal() && !data.rootTested();
		boolean unknown = rank.realm().isMortal() && !rootKnown(data);
		boolean narrow = room < 100;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 200.0D);
		if (untested) {
			Component text = Component.translatable("kultivator.hud.hint_root", Keys.INFO.getTranslatedKeyMessage());
			List<FormattedCharSequence> hint = font.split(text, Math.max(10, (int) (room / 0.75F)));
			int hintColor = Palette.mix(Palette.JADE_PALE, Palette.GOLD_LIGHT, pulse * 0.5F);
			for (int i = 0; i < Math.min(2, hint.size()); i++) {
				lines.add(new Line(null, hint.get(i), hintColor, 0.75F, -1));
			}
		} else {
			MutableComponent qiLine = Component.translatable("kultivator.hud.qi", Msg.number(qiShown), Msg.number(maxQi)).withColor(Palette.QI_LIGHT);
			if (!narrow) {
				qiLine.append(Component.literal("  " + Math.round(barShown * 100.0F) + "%").withColor(Palette.GOLD));
			}
			lines.add(Line.of(qiLine, Palette.QI_LIGHT, 0.75F));
			if (data.storedQi() > 0.5D) {
				storedLine = Line.of(Component.translatable(rank.isMax() ? "kultivator.hud.stored_immortal" : "kultivator.hud.stored",
						Msg.number(storedNumber)), Palette.STORED_LIGHT, 0.75F);
				lines.add(storedLine);
			}
		}
		if (data.breakingThrough()) {
			lines.add(Line.of(Component.translatable("kultivator.hud.breaking_through"), Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse), 0.75F));
		} else if (data.barFull() && rank.nextIsBreakthrough() && !unknown) {
			lines.add(Line.of(Component.translatable("kultivator.hud.ready"), Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse), 0.75F));
		}
		int deviation = deviationTicksNow();
		if (deviation > 0) {
			int seconds = deviation / 20;
			String clock = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
			lines.add(Line.of(Component.translatable("kultivator.hud.deviation", clock), Palette.mix(0xFFFF4A3A, 0xFFFF9A8A, pulse), 0.75F));
		}
		if (data.toxicity() > 80.0D) {
			toxicityLine = Line.of(Component.translatable("kultivator.hud.toxicity", Msg.number(data.toxicity())), Palette.WARN, 0.75F);
			lines.add(toxicityLine);
		}
		if (data.meditating()) {
			MutableComponent line = Component.translatable("kultivator.hud.meditating", multiplier(data.siteMultiplier()))
					.append(Component.literal(" · "))
					.append(Component.translatable("kultivator.hud.density", Math.round(data.veinDensity() * 100.0D)));
			if (data.formationTier() > 0) {
				line.append(Component.literal(" · ")).append(Component.translatable("kultivator.hud.formation." + Math.min(3, data.formationTier())));
			}
			meditatingLine = Line.of(line, Palette.JADE_PALE, 0.75F);
			lines.add(meditatingLine);
		}

		// The vanilla chat starts 40 px above the bottom: keep the block below it by dropping the least important lines
		// (meditation details first, then Stored Qi, then the toxicity warning).
		float total = linesHeight(lines);
		for (Line droppable : new Line[]{meditatingLine, storedLine, toxicityLine}) {
			if (total <= MAX_TEXT_HEIGHT) {
				break;
			}
			if (droppable != null && lines.remove(droppable)) {
				total = linesHeight(lines);
			}
		}
		float y = total <= 34.0F ? cy - total / 2.0F : height - 3.0F - total;
		for (Line line : lines) {
			float drawn;
			if (line.text() != null) {
				drawn = Brush.fit(g, font, line.text(), x, y, room, line.color(), line.scale(), line.scale() * 0.65F, true);
			} else if (line.row() != null) {
				Brush.text(g, font, line.row(), x, y, line.color(), line.scale(), true);
				drawn = font.width(line.row()) * line.scale();
			} else {
				drawn = 0.0F;
			}
			if (line.pips() >= 0 && drawn + PIP_ROOM <= room) {
				pips(g, x + drawn + 3.0F, y + 3.5F * line.scale(), line.pips(), line.color());
			}
			y += 9.0F * line.scale();
		}
	}

	/**
	 * The realm and stage: the full "Golden Core, Late" when it fits, else the realm name with four stage pips, else the
	 * realm name over two small lines (narrow windows, long names).
	 */
	private static void addRankLines(List<Line> lines, Font font, Rank rank, int room) {
		int color = Palette.opaque(rank.realm().color());
		Component full = rank.displayName();
		float fullScale = Brush.fitScale(font, full, room, 1.0F, 0.5F);
		if (fullScale >= 0.7F || rank.realm().isMortal()) {
			lines.add(Line.of(full, color, Math.max(0.6F, fullScale)));
			return;
		}
		Component realm = rank.realm().layers() > 1
				? Component.translatable("kultivator.hud.realm_layer", rank.realm().displayName(), rank.layer() + 1)
				: rank.realm().displayName();
		float realmScale = Brush.fitScale(font, realm, room - PIP_ROOM, 0.85F, 0.5F);
		if (realmScale >= 0.6F) {
			lines.add(new Line(realm, null, color, realmScale, rank.stage()));
			return;
		}
		List<FormattedCharSequence> rows = font.split(realm, Math.max(10, (int) (room / 0.65F)));
		int count = Math.min(2, rows.size());
		for (int i = 0; i < count; i++) {
			lines.add(new Line(null, rows.get(i), color, 0.65F, i == count - 1 ? rank.stage() : -1));
		}
	}

	private static float linesHeight(List<Line> lines) {
		float total = 0.0F;
		for (Line line : lines) {
			total += 9.0F * line.scale();
		}
		return total;
	}

	/** Four small diamonds after the realm name: how many stages of it are reached. */
	private static void pips(GuiGraphicsExtractor g, float x, float cy, int stage, int color) {
		for (int i = 0; i < 4; i++) {
			g.pose().pushMatrix();
			g.pose().translate(x + 1.5F + i * 3.6F, cy);
			g.pose().rotate((float) (Math.PI / 4.0D));
			Brush.rect(g, -1.1F, -1.1F, 1.1F, 1.1F, i <= stage ? color : Palette.argb(0x60, color));
			g.pose().popMatrix();
		}
	}

	/** "×1.5", "×2.25". */
	private static String multiplier(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return "×" + text;
	}
}
