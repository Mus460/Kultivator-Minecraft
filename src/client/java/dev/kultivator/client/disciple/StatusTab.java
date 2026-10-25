package dev.kultivator.client.disciple;

import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.StarTitles;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Status: on the left the disciple's name over its rank seal (a square seal in the realm colour whose gold frame fills
 * with cultivation, the realm written inside) and three bars for cultivation, qi and stored qi; on the right a column
 * of bamboo slips with master, rank, root, Dao, health, kills, order and what it is doing.
 */
final class StatusTab extends MuridSub {

	private static final int ORDER_H = 14;
	/** One labelled bar: the label at 1.0, then the bar. */
	private static final int BAR_STEP = 17;

	private final Eased bar = new Eased(5.0F);
	private final Eased qi = new Eased(5.0F);
	private final Eased stored = new Eased(5.0F);
	private final Eased scrollShown = new Eased(14.0F);
	private final List<Slip> slips = new ArrayList<>();
	private float scroll;
	private long slipsBuiltAt;
	private int lastStep = -1;

	private int leftW;
	private int slipX;
	private int slipW;
	/** The height above the orders row. */
	private int bodyH;

	/** One bamboo slip: a label and its value. */
	private record Slip(Component label, Component value, int color) {
	}

	StatusTab(MuridPage murid) {
		super(murid);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.discipleui.tab.status");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.discipleui.tab.status.short");
	}

	@Override
	public String helpTopic() {
		return "murid";
	}

	@Override
	protected void build() {
		leftW = Math.max(84, Math.min(150, Math.round(w * 0.4F)));
		slipX = x + leftW + 6;
		slipW = x + w - slipX;
		bodyH = h - ORDER_H - 4;
		buildOrders();
	}

	/**
	 * The orders (was the Perintah tab): one row of talismans along the bottom (Follow, Stay &amp; guard, Meditate, Assist,
	 * Guard the Hall), the active one stamped; its meaning and the chat commands in the tooltip.
	 */
	private void buildOrders() {
		int n = DiscipleView.MODES.length;
		int gap = 3;
		int bw = (w - gap * (n - 1)) / n;
		int top = y + h - ORDER_H;
		for (int i = 0; i < n; i++) {
			int index = i;
			String mode = DiscipleView.MODES[i];
			TalismanButton button = add(new TalismanButton(x + i * (bw + gap), top, i == n - 1 ? x + w - (x + i * (bw + gap)) : bw, ORDER_H,
					() -> Component.translatable(MuridPage.K + "perintah." + mode)));
			button.stampedWhen(() -> view().modeIndex() == index);
			button.onPress(() -> {
				if (view().modeIndex() != index) {
					send(Actions.DISCIPLE_MODE, mode);
				}
			});
			button.tooltip(() -> List.of(Component.translatable("kultivator.discipleui.mode." + mode).withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.discipleui.mode." + mode + ".desc"),
					Component.translatable("kultivator.discipleui.orders.current",
							Component.translatable("kultivator.discipleui.mode." + DiscipleView.MODES[view().modeIndex()])).withStyle(ChatFormatting.DARK_GREEN),
					Component.translatable("kultivator.discipleui.orders.hint").withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	@Override
	public void onView() {
		rebuildSlips();
	}

	// ================================================================== slips

	private void rebuildSlips() {
		slipsBuiltAt = Util.getMillis();
		slips.clear();
		DiscipleView view = view();
		DiscipleEntity disciple = murid.entity();
		Rank rank = view.rank;
		// The synced master name; only the master can open the scroll, so the viewer is the fallback.
		Minecraft minecraft = Minecraft.getInstance();
		String masterName = !view.master.isEmpty() ? view.master : disciple != null ? disciple.ownerName() : "";
		Component master = masterName != null && !masterName.isEmpty() ? Component.literal(masterName)
				: minecraft.player != null ? minecraft.player.getName() : Component.empty();
		slips.add(new Slip(label("master"), master, Palette.INK_SOFT));
		slips.add(new Slip(label("realm"), rank.displayName(), Palette.onPaper(rank.realm().color())));
		slips.add(new Slip(label("cultivation"), Component.literal(Msg.number(view.xp) + " / " + Msg.number(view.xpRequired)
				+ (view.barFull() && !rank.isMax() ? " ★" : "")), Palette.GOLD_DARK));
		slips.add(new Slip(label("qi"), Component.literal(Msg.number(view.qi) + " / " + Msg.number(view.maxQi)), Palette.onPaper(Palette.QI)));
		slips.add(new Slip(label("stored"), Component.literal(Msg.number(view.storedQi)), Palette.onPaper(Palette.STORED)));
		slips.add(new Slip(label("root"), rootText(view), Palette.onPaper(Element.mainColor(view.rootMask))));
		slips.add(new Slip(label("grade"), view.rootKnown ? view.grade.displayName() : Component.translatable("kultivator.discipleui.status.root_none"),
				view.rootKnown ? Palette.onPaper(view.grade.color()) : Palette.INK_FAINT));
		Dao dao = view.dao;
		slips.add(new Slip(label("dao"), dao != null ? dao.displayName() : Component.translatable("kultivator.discipleui.status.dao_none"),
				dao != null ? Palette.onPaper(dao.color()) : Palette.INK_FAINT));
		if (dao != null) {
			slips.add(new Slip(label("comprehension"), Component.translatable("kultivator.discipleui.status.comprehension.value",
					String.format(Locale.ROOT, "%.1f", view.comprehension), Dao.comprehensionName(view.comprehension)), Palette.onPaper(dao.color())));
		}
		addPhase6Slips();
		double health = disciple != null ? disciple.getHealth() : view.health;
		double maxHealth = disciple != null ? disciple.getMaxHealth() : view.maxHealth;
		if (health >= 0.0D && maxHealth > 0.0D) {
			float fraction = (float) (health / maxHealth);
			slips.add(new Slip(label("health"), Component.literal(Msg.number(Math.ceil(health)) + " / " + Msg.number(maxHealth)),
					fraction < 0.25F ? Palette.DANGER : fraction < 0.5F ? Palette.WARN : Palette.onPaper(0x4FA36A)));
		}
		slips.add(new Slip(label("kills"), Component.literal(String.valueOf(view.kills)), Palette.INK_SOFT));
		slips.add(new Slip(label("techniques"), Component.literal(String.valueOf(view.learned.size())), Palette.JADE_DARK));
		slips.add(new Slip(label("mode"), Component.translatable("kultivator.discipleui.mode." + DiscipleView.MODES[view.modeIndex()]),
				Palette.SEAL_DARK));
		slips.add(new Slip(label("state"), activity(view, disciple), activityColor(view, disciple)));
		if (view.deviationTicks > 0) {
			slips.add(new Slip(label("deviation"), Component.literal(clock(view.deviationTicks)), Palette.DANGER));
		}
	}

	private static Component label(String id) {
		return Component.translatable("kultivator.discipleui.status." + id);
	}

	/** Phase 6 (module C): sect rank, soul colour, rare physique and stars, from the {@code dpower} details. */
	private void addPhase6Slips() {
		CompoundTag power = info().getCompoundOrEmpty(DisciplePowers.INFO_KEY);
		if (power.isEmpty()) {
			return;
		}
		int rank = Math.max(0, Math.min(PhysiqueVisuals.RANK_TRIM.length - 1, power.getIntOr("sect_rank", 1)));
		slips.add(new Slip(Component.translatable("kultivator.dpower.status.sect_rank"), Component.translatable("kultivator.dpower.rank." + rank),
				Palette.onPaper(PhysiqueVisuals.RANK_TRIM[rank])));
		int colour = Math.max(0, Math.min(6, power.getIntOr("colour", 2)));
		slips.add(new Slip(Component.translatable("kultivator.dpower.status.colour"),
				Component.translatable("kultivator.dpower.status.colour.value", Component.translatable("kultivator.dpower.colour." + PowerIds.COLOUR_IDS[colour]),
						String.format(Locale.ROOT, "%.2f", power.getDoubleOr("colour_factor", 1.0D))), Palette.onPaper(PowerIds.COLOUR_RGB[colour])));
		int physique = power.getIntOr("physique", 0);
		if (physique > 0 && physique < PowerIds.PHYSIQUE_IDS.length && !power.getBooleanOr("hidden", false)) {
			slips.add(new Slip(Component.translatable("kultivator.dpower.status.physique"),
					Component.translatable("kultivator.dpower.physique." + PowerIds.PHYSIQUE_IDS[physique]), Palette.onPaper(PowerIds.PHYSIQUE_RGB[physique])));
		}
		int stars = power.getIntOr("stars", 0);
		if (stars > 0) {
			slips.add(new Slip(Component.translatable("kultivator.dpower.status.stars"), StarTitles.title(stars), Palette.GOLD_DARK));
		}
	}

	/** The root's elements, each in its own colour ("not revealed" when unknown). */
	private static Component rootText(DiscipleView view) {
		if (!view.rootKnown || view.rootMask == 0) {
			return Component.translatable("kultivator.discipleui.status.root_none");
		}
		MutableComponent text = Component.empty();
		boolean first = true;
		for (Element element : Element.fromMask(view.rootMask)) {
			if (!first) {
				text.append(Component.literal(" "));
			}
			first = false;
			text.append(element.displayName().copy().withColor(Palette.onPaper(element.color()) & 0xFFFFFF));
		}
		return text;
	}

	private static Component activity(DiscipleView view, DiscipleEntity disciple) {
		int flags = disciple == null ? 0 : DiscipleLook.flags(disciple);
		String id;
		if (view.breakingThrough || (flags & DiscipleLook.BREAKING_THROUGH) != 0) {
			id = "breaking";
		} else if ((flags & DiscipleLook.DEVIATING) != 0 || view.deviating) {
			id = "deviating";
		} else if ((flags & DiscipleLook.FIGHTING) != 0) {
			id = "fighting";
		} else if ((flags & DiscipleLook.MEDITATING) != 0) {
			id = "meditating";
		} else {
			id = "idle";
		}
		return Component.translatable("kultivator.discipleui.state." + id);
	}

	private static int activityColor(DiscipleView view, DiscipleEntity disciple) {
		int flags = disciple == null ? 0 : DiscipleLook.flags(disciple);
		if (view.breakingThrough || (flags & DiscipleLook.BREAKING_THROUGH) != 0) {
			return Palette.GOLD_DARK;
		}
		if ((flags & (DiscipleLook.DEVIATING | DiscipleLook.FIGHTING)) != 0 || view.deviating) {
			return Palette.DANGER;
		}
		return (flags & DiscipleLook.MEDITATING) != 0 ? Palette.onPaper(Palette.QI) : Palette.INK_SOFT;
	}

	/** Ticks as m:ss. */
	private static String clock(int ticks) {
		int seconds = Math.max(0, ticks) / 20;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (Util.getMillis() - slipsBuiltAt > 1000L) {
			rebuildSlips();
		}
		DiscipleView view = view();
		Rank rank = view.rank;
		int step = rank.step();
		if (lastStep >= 0 && step != lastStep) {
			bar.set(0.0F);
		}
		lastStep = step;
		float barT = bar.to((float) view.barFraction(), seconds);
		float qiT = qi.to((float) Math.min(1.0D, view.qi / view.maxQi), seconds);
		float storedT = stored.to((float) Math.min(1.0D, view.storedQi / view.xpRequired), seconds);

		// The seal, then the bars (the name is in the picker above).
		float cx = x + leftW / 2.0F;
		int barsH = 3 * BAR_STEP;
		float size = Math.max(36.0F, Math.min(leftW - 18.0F, bodyH - 2.0F - barsH - 8.0F));
		float sealTop = y + 1.0F;
		drawSeal(g, view, cx, sealTop, size, barT, mouseX, mouseY);
		g.nextStratum();
		float barsTop = sealTop + size + 8.0F;
		// A bar that would reach the orders row is left out (very short windows); its numbers stay on the slips.
		float limit = y + bodyH - 14.0F;
		float bx0 = x + 4.0F;
		float bx1 = x + leftW - 4.0F;
		if (barsTop <= limit) drawBar(g, label("cultivation"), Msg.number(view.xp) + " / " + Msg.number(view.xpRequired), barT, Palette.GOLD, Palette.GOLD_LIGHT,
				bx0, barsTop, bx1, mouseX, mouseY, List.of(label("cultivation"),
						Component.literal(Msg.number(view.xp) + " / " + Msg.number(view.xpRequired)).withColor(Palette.GOLD_LIGHT),
						Component.translatable(view.barFull() && !rank.isMax() ? "kultivator.discipleui.status.ready"
								: "kultivator.discipleui.status.cultivation.tip"), guidanceLine(rank)));
		if (barsTop + BAR_STEP <= limit) drawBar(g, label("qi"), Msg.number(view.qi) + " / " + Msg.number(view.maxQi), qiT, Palette.QI, Palette.QI_LIGHT, bx0, barsTop + BAR_STEP, bx1,
				mouseX, mouseY, List.of(label("qi"), Component.literal(Msg.number(view.qi) + " / " + Msg.number(view.maxQi)).withColor(Palette.QI_LIGHT),
						Component.translatable("kultivator.discipleui.status.qi.tip")));
		if (barsTop + 2 * BAR_STEP <= limit) drawBar(g, label("stored"), Msg.number(view.storedQi), storedT, Palette.STORED, Palette.STORED_LIGHT, bx0, barsTop + 2 * BAR_STEP, bx1, mouseX, mouseY,
				List.of(label("stored"), Component.literal(Msg.number(view.storedQi)).withColor(Palette.STORED_LIGHT),
						Component.translatable("kultivator.discipleui.status.stored.tip")));
		g.nextStratum();
		drawSlips(g, seconds);
	}

	/** Bimbingan Guru: what the master's meditation nearby gives this disciple now (the server's own pure formulas). */
	private static Component guidanceLine(Rank rank) {
		dev.kultivator.data.Realm master = dev.kultivator.client.ClientCultivation.data().realm();
		return Component.translatable("kultivator.disciplecult.guidance.status",
				Math.round(dev.kultivator.disciple.DiscipleCultivation.guidanceShare(master, rank.realm()) * 100.0D),
				Msg.number(dev.kultivator.disciple.DiscipleCultivation.guidanceRange(master)),
				Msg.number(dev.kultivator.disciple.DiscipleCultivation.guidanceSelfFactor(master))).withColor(0x7CFFB2);
	}

	/**
	 * The rank seal: a chamfered square in the realm colour with a gold frame that fills clockwise with cultivation, the
	 * realm and stage written inside and a bead of the root's colour at the bottom. It glows when the bar is full.
	 */
	private void drawSeal(GuiGraphicsExtractor g, DiscipleView view, float cx, float top, float size, float barT, int mouseX, int mouseY) {
		Rank rank = view.rank;
		int x0 = Math.round(cx - size / 2.0F);
		int y0 = Math.round(top);
		int x1 = Math.round(cx + size / 2.0F);
		int y1 = Math.round(top + size);
		long now = Util.getMillis();
		boolean ready = view.barFull() && !rank.isMax();
		if (ready) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 260.0D);
			Brush.chamfer(g, x0 - 3, y0 - 3, x1 + 3, y1 + 3, 5, Palette.argb(Math.round(50 + 70 * pulse), 0xFFE27A));
		}
		g.fill(x0 + 2, y0 + 3, x1 + 2, y1 + 3, 0x50000000);
		int realmColor = Palette.opaque(rank.realm().color());
		// Frame track and the gold that fills it.
		Brush.chamfer(g, x0, y0, x1, y1, 4, Palette.mix(Palette.PAPER_BOTTOM, Palette.GOLD_DARK, 0.45F));
		perimeter(g, x0, y0, x1, y1, 3, barT, Palette.GOLD);
		// The seal face: deep realm colour, lighter at the top.
		int faceTop = Palette.mix(Palette.darken(realmColor, 0.35F), Palette.SEAL, 0.25F);
		int faceBottom = Palette.mix(Palette.darken(realmColor, 0.7F), Palette.SEAL_DARK, 0.3F);
		Brush.chamferGradient(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, 3, faceTop, faceBottom);
		Brush.frame(g, x0 + 5, y0 + 5, x1 - 5, y1 - 5, Palette.argb(0xB0, Palette.TALISMAN));
		Brush.frame(g, x0 + 7, y0 + 7, x1 - 7, y1 - 7, Palette.argb(0x50, Palette.TALISMAN));

		// Realm (two lines when long), stage, and the root bead.
		float room = size - 16.0F;
		String plain = rank.realm().displayName().getString();
		List<Component> lines = new ArrayList<>();
		Component whole = Brush.bold(rank.realm().displayName());
		float scale = Brush.fitScale(font, whole, room, 1.2F, 0.5F);
		int space = plain.indexOf(' ', plain.length() / 2 - 1);
		if (space < 0) {
			space = plain.lastIndexOf(' ');
		}
		if (font.width(whole) > room && space > 0) {
			Component first = Brush.bold(Component.literal(plain.substring(0, space)));
			Component second = Brush.bold(Component.literal(plain.substring(space + 1)));
			lines.add(first);
			lines.add(second);
			scale = Math.min(Brush.fitScale(font, first, room, 1.1F, 0.5F), Brush.fitScale(font, second, room, 1.1F, 0.5F));
		} else {
			lines.add(whole);
		}
		Component stage = rank.realm().isMortal() ? Component.translatable("kultivator.discipleui.status.mortal_hint")
				: rank.realm().layers() > 1 ? Component.translatable("kultivator.discipleui.status.layer_stage", rank.layer() + 1, rank.stageName())
				: Component.translatable("kultivator.discipleui.status.stage", rank.stageName());
		float stageScale = 1.0F;
		float textH = lines.size() * 9.0F * scale + 2.0F + 9.0F * stageScale;
		float ty = y0 + (size - textH) / 2.0F - 2.0F;
		for (Component line : lines) {
			Brush.fitCentered(g, font, line, cx, ty, room, Palette.lighten(realmColor, 0.35F), Math.max(1.0F, scale), 1.0F, true);
			ty += 9.0F * scale;
		}
		ty += 2.0F;
		Brush.fitCentered(g, font, stage, cx, ty, room, Palette.TALISMAN, 1.0F, 1.0F, false);
		float beadY = y1 - 9.0F;
		int root = Palette.opaque(Element.mainColor(view.rootKnown ? view.rootMask : 0));
		Brush.disc(g, cx, beadY, 2.6F, Palette.GOLD_DARK);
		Brush.disc(g, cx, beadY, 2.0F, root);
		Brush.disc(g, cx - 0.6F, beadY - 0.6F, 0.6F, 0x90FFFFFF);

		if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
			List<Component> tip = new ArrayList<>();
			tip.add(rank.displayName().withColor(rank.realm().color()));
			tip.add(Component.translatable("kultivator.discipleui.status.bar", Math.round(barT * 100.0F)).withStyle(ChatFormatting.GOLD));
			if (!rank.isMax()) {
				Realm next = view.nextRank.realm();
				tip.add(Component.translatable("kultivator.discipleui.bt.next", view.nextRank.displayName().withColor(next.color())));
			}
			tooltip(tip);
		}
	}

	/** The frame around a square filled clockwise from the top-left corner for {@code fraction} of its length. */
	private static void perimeter(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int t, float fraction, int color) {
		int wSide = x1 - x0;
		int hSide = y1 - y0;
		float left = Math.max(0.0F, Math.min(1.0F, fraction)) * 2.0F * (wSide + hSide);
		float top = Math.min(left, wSide);
		if (top > 0.0F) {
			g.fill(x0, y0, x0 + Math.round(top), y0 + t, color);
		}
		left -= wSide;
		float right = Math.min(Math.max(0.0F, left), hSide);
		if (right > 0.0F) {
			g.fill(x1 - t, y0, x1, y0 + Math.round(right), color);
		}
		left -= hSide;
		float bottom = Math.min(Math.max(0.0F, left), wSide);
		if (bottom > 0.0F) {
			g.fill(x1 - Math.round(bottom), y1 - t, x1, y1, color);
		}
		left -= wSide;
		float leftSide = Math.min(Math.max(0.0F, left), hSide);
		if (leftSide > 0.0F) {
			g.fill(x0, y1 - Math.round(leftSide), x0 + t, y1, color);
		}
	}

	/** A labelled thin bar with its numbers on the right. */
	private void drawBar(GuiGraphicsExtractor g, Component name, String numbers, float fraction, int color, int light, float x0, float top, float x1,
						 int mouseX, int mouseY, List<Component> tip) {
		float scale = 1.0F;
		Component value = Component.literal(numbers);
		float valueW = Math.min((x1 - x0) * 0.55F, font.width(value) * scale);
		Brush.fit(g, font, name, x0, top, x1 - x0 - valueW - 3.0F, Palette.INK_SOFT, scale, 0.45F, false);
		Brush.fit(g, font, value, x1 - valueW, top, valueW, Palette.onPaper(color), scale, 0.45F, false);
		float by = top + 10.0F;
		Brush.rect(g, x0, by, x1, by + 4.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
		Brush.rect(g, x0 + 0.5F, by + 0.5F, x1 - 0.5F, by + 3.5F, Palette.mix(Palette.PAPER_BOTTOM, color, 0.15F));
		float fill = x0 + 0.5F + (x1 - x0 - 1.0F) * Math.max(0.0F, Math.min(1.0F, fraction));
		if (fill > x0 + 0.6F) {
			Brush.rect(g, x0 + 0.5F, by + 0.5F, fill, by + 3.5F, color);
			Brush.rect(g, x0 + 0.5F, by + 0.5F, fill, by + 1.3F, light);
		}
		if (mouseX >= x0 && mouseX < x1 && mouseY >= top && mouseY < by + 5.0F) {
			tooltip(tip);
		}
	}

	private void drawSlips(GuiGraphicsExtractor g, float seconds) {
		if (slipW < 30 || slips.isEmpty()) {
			return;
		}
		int slipY = y;
		int slipH = bodyH;
		float slatH = 12.0F;
		float scale = 1.0F;
		float total = slips.size() * slatH;
		float maxScroll = Math.max(0.0F, total - slipH);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		float offset = scrollShown.to(scroll, seconds);

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
			int slat = i % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT;
			Brush.rect(g, slipX + 1.0F, top, slipX + slipW - 1.0F, top + slatH - 1.0F, slat);
			Brush.rect(g, slipX + 1.0F, top, slipX + slipW - 1.0F, top + 0.5F, 0x40FFFFFF);
			Brush.rect(g, slipX + 1.0F, top + slatH - 1.5F, slipX + slipW - 1.0F, top + slatH - 1.0F, 0x30000000);
			Brush.rect(g, slipX + 2.0F, top + 1.0F, slipX + 3.0F, top + slatH - 2.0F, Palette.BAMBOO_KNOT);
			Brush.rect(g, slipX + slipW - 3.0F, top + 1.0F, slipX + slipW - 2.0F, top + slatH - 2.0F, Palette.BAMBOO_KNOT);
			g.fill(slipX + 4, Math.round(top), slipX + 5, Math.round(top + slatH - 1.0F), Palette.CORD);
			g.fill(slipX + slipW - 5, Math.round(top), slipX + slipW - 4, Math.round(top + slatH - 1.0F), Palette.CORD);

			float inner = slipW - 16.0F;
			float valueRoom = inner * 0.58F;
			float valueScale = Brush.fitScale(font, slip.value(), valueRoom, scale, 0.45F);
			float drawnValue = Math.min(valueRoom, font.width(slip.value()) * valueScale);
			Brush.fit(g, font, slip.label(), slipX + 8.0F, textY, inner - drawnValue - 3.0F, Palette.INK_SOFT, scale, 0.45F, false);
			Brush.fit(g, font, slip.value(), slipX + slipW - 8.0F - drawnValue, textY, valueRoom, slip.color(), scale, 0.45F, false);
		}
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = slipY + 3.0F + (slipH - 6.0F) * (offset / maxScroll);
			Brush.disc(g, slipX + slipW - 4.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, slipX + slipW - 5.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double amount) {
		if (mouseX >= slipX && mouseX < slipX + slipW && mouseY >= y && mouseY < y + bodyH) {
			scroll -= (float) amount * 12.0F;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, amount);
	}
}
