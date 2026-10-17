package dev.kultivator.client.disciple.power;

import dev.kultivator.client.disciple.DiscipleScreen;
import dev.kultivator.client.disciple.DiscipleTab;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pusaka, the treasure page of the disciple scroll ({@code DiscipleScreen.TAB_TREASURE}; DESIGN 20.3.16-20.3.17): on the
 * left the natal treasure (four talismans to choose its form once, then its seal with the folds, the next fold's cost
 * and chance and the "Tempa Lipatan" talisman); on the right the Undying Codex ladder with the Undying Blood bar, the
 * Heavenly Flame orbs (and a running absorption) and the Kunpeng Wings with their talisman.
 */
public class TreasureTab extends DiscipleTab {

	private static final int BUTTON_H = 17;
	private static final String P = "kultivator.dpower.";
	private static final int GOLD = 0xFFD4A637;
	private static final int SILVER = 0xFFB8C4D0;

	private int columnW;
	private final List<TalismanButton> forms = new ArrayList<>();
	private TalismanButton enhance;
	private TalismanButton kunpeng;

	public TreasureTab(DiscipleScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.tab.treasure");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.tab.treasure.short");
	}

	private CompoundTag power() {
		return info().getCompoundOrEmpty(DisciplePowers.INFO_KEY);
	}

	private CompoundTag natal() {
		return power().getCompoundOrEmpty("natal");
	}

	@Override
	protected void build() {
		forms.clear();
		columnW = Math.max(110, Math.min(180, Math.round(w * 0.45F)));
		int top = y + 26;
		int gap = Math.max(2, Math.min(4, (h - 60 - 4 * BUTTON_H) / 4));
		for (int i = 0; i < PowerIds.NATAL_IDS.length; i++) {
			int form = i;
			TalismanButton button = add(new TalismanButton(x + 2, top + i * (BUTTON_H + gap), columnW - 4, BUTTON_H,
					() -> Component.translatable(P + "natal.form." + PowerIds.NATAL_IDS[form])));
			button.enabledWhen(() -> natal().getStringOr("refine_reason", "x").isEmpty());
			button.onPress(() -> send(Actions.DISCIPLE_POWER, "natal_form:" + form));
			button.tooltip(() -> {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.translatable(P + "natal.form." + PowerIds.NATAL_IDS[form]).withStyle(ChatFormatting.GOLD));
				tip.add(Component.translatable(P + "natal.form." + PowerIds.NATAL_IDS[form] + ".desc"));
				String why = natal().getStringOr("refine_reason", "");
				if (!why.isEmpty()) {
					tip.add(Component.translatable(why).withStyle(ChatFormatting.RED));
				}
				tip.add(TalentTab.source("natal"));
				return tip;
			});
			forms.add(button);
		}
		enhance = add(new TalismanButton(x + 2, y + h - BUTTON_H, columnW - 4, BUTTON_H, () -> Component.translatable(P + "btn.enhance")));
		enhance.enabledWhen(() -> natal().getStringOr("enhance_reason", "x").isEmpty());
		enhance.urgentWhen(() -> natal().getBooleanOr("loss_risk", false) && natal().getStringOr("enhance_reason", "x").isEmpty());
		enhance.onPress(() -> send(Actions.DISCIPLE_POWER, "natal_enhance"));
		enhance.tooltip(() -> {
			CompoundTag natal = natal();
			List<Component> tip = new ArrayList<>();
			tip.add(Component.translatable(P + "btn.enhance").withStyle(ChatFormatting.GOLD));
			tip.add(Component.translatable(P + "natal.cost", natal.getIntOr("next_feathers", 1), natal.getIntOr("next_pk", 40)));
			tip.add(Component.translatable(P + "natal.chance", Math.round(natal.getDoubleOr("chance", 0.0D) * 100.0D)));
			if (natal.getBooleanOr("loss_risk", false)) {
				tip.add(Component.translatable(P + "natal.loss_risk").withStyle(ChatFormatting.RED));
			}
			String why = natal.getStringOr("enhance_reason", "");
			if (!why.isEmpty()) {
				tip.add(Component.translatable(why).withStyle(ChatFormatting.RED));
			}
			tip.add(TalentTab.source("natal"));
			return tip;
		});
		int rightX = x + columnW + 6;
		kunpeng = add(new TalismanButton(rightX, y + h - BUTTON_H, x + w - rightX, BUTTON_H, () -> Component.translatable(P + "btn.kunpeng")));
		kunpeng.enabledWhen(() -> power().getIntOr("kunpeng_left", 1) <= 0);
		kunpeng.onPress(() -> send(Actions.DISCIPLE_POWER, "kunpeng"));
		kunpeng.tooltip(() -> List.of(Component.translatable(P + "kunpeng.name").withColor(0x8FD0FF),
				Component.translatable(power().getBooleanOr("kunpeng_full", false) ? P + "kunpeng.full" : P + "kunpeng.third"),
				TalentTab.source("kunpeng")));
	}

	@Override
	protected boolean visible(Widget widget) {
		boolean refined = natal().getIntOr("form", -1) >= 0;
		if (widget == enhance) {
			return refined;
		}
		if (widget == kunpeng) {
			return power().getBooleanOr("kunpeng", false);
		}
		return forms.contains(widget) ? !refined : true;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag power = power();
		if (power.isEmpty()) {
			heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
			return;
		}
		drawNatal(g, natal(), mouseX, mouseY);
		drawRight(g, power, mouseX, mouseY);
	}

	private void drawNatal(GuiGraphicsExtractor g, CompoundTag natal, int mouseX, int mouseY) {
		float x0 = x + 2.0F;
		float width = columnW - 4.0F;
		heading(g, Component.translatable(P + "natal.heading"), x0, y, width, 0.8F);
		int form = natal.getIntOr("form", -1);
		if (form < 0) {
			Component cost = Component.translatable(P + "natal.refine_cost", natal.getIntOr("diamonds", 0), natal.getIntOr("feathers", 0));
			Brush.fit(g, font, cost, x0, y + 13.0F, width, Palette.INK_SOFT, 0.6F, 0.4F, false);
			String why = natal.getStringOr("refine_reason", "");
			if (!why.isEmpty()) {
				Brush.wrap(g, font, Component.translatable(why), x0, y + h - 18.0F, width, Palette.DANGER, 0.55F, false);
			}
			return;
		}
		int folds = natal.getIntOr("folds", 0);
		int max = natal.getIntOr("max_folds", 18);
		int tone = folds >= 9 ? GOLD : folds >= 3 ? SILVER : 0xFF9AA49C;
		// The treasure's seal: a round disc in its tier's metal with the folds around it.
		float size = Math.max(26.0F, Math.min(44.0F, h * 0.3F));
		float cx = x0 + size / 2.0F + 2.0F;
		float cy = y + 14.0F + size / 2.0F;
		Brush.disc(g, cx + 1.0F, cy + 1.5F, size / 2.0F, 0x40000000);
		Brush.disc(g, cx, cy, size / 2.0F, Palette.darken(tone, 0.35F));
		Brush.disc(g, cx, cy, size / 2.0F - 2.0F, tone);
		for (int i = 0; i < max; i++) {
			double angle = Math.PI * 2.0D * i / max - Math.PI / 2.0D;
			float bx = cx + (float) Math.cos(angle) * (size / 2.0F + 3.0F);
			float by = cy + (float) Math.sin(angle) * (size / 2.0F + 3.0F);
			Brush.disc(g, bx, by, 1.3F, i < folds ? (i >= 13 ? 0xFFFFF2C0 : GOLD) : 0x60465950);
		}
		drawForm(g, form, cx, cy, size * 0.55F, Palette.darken(tone, 0.6F));
		float tx = cx + size / 2.0F + 6.0F;
		float tw = x0 + width - tx;
		Brush.fit(g, font, Brush.bold(Component.translatable(P + "natal.form." + PowerIds.NATAL_IDS[form])), tx, y + 14.0F, tw, Palette.INK, 0.8F, 0.45F, false);
		Brush.fit(g, font, Component.translatable(P + "natal.folds", folds, max), tx, y + 24.0F, tw, Palette.onPaper(tone), 0.65F, 0.42F, false);
		Brush.fit(g, font, Component.translatable(P + "natal.damage", folds * 5), tx, y + 32.0F, tw, Palette.INK_SOFT, 0.6F, 0.4F, false);
		float ty = cy + size / 2.0F + 8.0F;
		Component ability = folds >= 9 ? Component.translatable(P + "natal.ability." + PowerIds.NATAL_IDS[form] + (folds >= 14 ? ".eternal" : ""))
				: Component.translatable(P + "natal.ability.locked");
		int abilityBottom = y + h - BUTTON_H - 20;
		g.enableScissor(Math.round(x0), Math.round(ty), Math.round(x0 + width), abilityBottom);
		Brush.wrap(g, font, ability, x0, ty, width, folds >= 9 ? Palette.JADE_DARK : Palette.INK_FAINT, 0.58F, false);
		g.disableScissor();
		float costY = y + h - BUTTON_H - 17.0F;
		Component cost = Component.translatable(P + "natal.next", natal.getIntOr("next_feathers", 1), natal.getIntOr("feathers", 0),
				natal.getIntOr("next_pk", 40), Math.round(natal.getDoubleOr("chance", 0.0D) * 100.0D));
		Brush.fit(g, font, cost, x0, costY, width, Palette.INK_SOFT, 0.55F, 0.4F, false);
		String why = natal.getStringOr("enhance_reason", "");
		if (!why.isEmpty()) {
			Brush.fit(g, font, Component.translatable(why), x0, costY + 7.0F, width, Palette.DANGER, 0.52F, 0.38F, false);
		}
		if (mouseX >= cx - size / 2.0F && mouseX < cx + size / 2.0F && mouseY >= cy - size / 2.0F && mouseY < cy + size / 2.0F) {
			screen.showTooltip(List.of(Component.translatable(P + "natal.form." + PowerIds.NATAL_IDS[form]).withStyle(ChatFormatting.GOLD),
					Component.translatable(P + "natal.tiers"), TalentTab.source("natal")));
		}
	}

	/** Small ink marks of the four forms. */
	private static void drawForm(GuiGraphicsExtractor g, int form, float cx, float cy, float size, int ink) {
		float s = size / 2.0F;
		switch (form) {
			case PowerIds.NATAL_PARASOL -> {
				Brush.band(g, cx, cy, s, -s, 0.0F, ink, ink);
				Brush.line(g, cx, cy, cx, cy + s, 1.2F, ink);
				Brush.line(g, cx, cy + s, cx - s * 0.25F, cy + s * 0.85F, 1.0F, ink);
			}
			case PowerIds.NATAL_WOK -> {
				Brush.band(g, cx, cy - s * 0.2F, s, 0.0F, s * 0.8F, ink, ink);
				Brush.line(g, cx - s * 1.0F, cy - s * 0.2F, cx + s * 1.0F, cy - s * 0.2F, 1.2F, ink);
			}
			case PowerIds.NATAL_BELL -> {
				Brush.band(g, cx, cy, s * 0.7F, -s * 0.8F, s * 0.4F, ink, ink);
				Brush.line(g, cx - s, cy + s * 0.45F, cx + s, cy + s * 0.45F, 1.2F, ink);
				Brush.disc(g, cx, cy + s * 0.75F, s * 0.18F, ink);
			}
			default -> {
				Brush.line(g, cx - s * 0.7F, cy + s * 0.7F, cx + s * 0.7F, cy - s * 0.7F, 1.6F, ink);
				Brush.line(g, cx - s * 0.75F, cy + s * 0.2F, cx - s * 0.2F, cy + s * 0.75F, 1.2F, ink);
			}
		}
	}

	private void drawRight(GuiGraphicsExtractor g, CompoundTag power, int mouseX, int mouseY) {
		int x0 = x + columnW + 6;
		int x1 = x + w;
		float width = x1 - x0;
		float cy = y;
		// The Undying Codex.
		heading(g, Component.translatable(P + "codex.heading"), x0, cy, width, 0.8F);
		cy += 13.0F;
		CompoundTag codex = power.getCompoundOrEmpty("codex");
		boolean access = codex.getBooleanOr("access", false);
		int stage = codex.getIntOr("stage", 0);
		float ladderTop = cy;
		if (!access) {
			Brush.wrap(g, font, Component.translatable(P + "codex.locked"), x0, cy, width, Palette.INK_FAINT, 0.58F, false);
			cy += 22.0F;
		} else {
			float step = width / 4.0F;
			for (int i = 0; i < 4; i++) {
				float sx = x0 + step * i;
				boolean done = i < stage;
				int color = done ? (i == 1 ? GOLD : i == 0 ? SILVER : 0xFF8FB8A0) : 0x60465950;
				Brush.rect(g, sx + 1.0F, cy, sx + step - 1.0F, cy + 9.0F, done ? Palette.argb(0xC0, color) : Palette.argb(0x30, Palette.PAPER_DEEP));
				Brush.fitCentered(g, font, Component.translatable(P + "codex.stage." + (i + 1) + ".short"), sx + step / 2.0F, cy + 1.5F, step - 3.0F,
						done ? Palette.INK : Palette.INK_FAINT, 0.55F, 0.35F, false);
			}
			cy += 11.0F;
			int blood = codex.getIntOr("blood", 0);
			Brush.rect(g, x0, cy, x1, cy + 4.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.INK, 0.25F));
			if (blood > 0) {
				Brush.rect(g, x0 + 0.5F, cy + 0.5F, x0 + 0.5F + (width - 1.0F) * blood / 100.0F, cy + 3.5F, 0xFFC0102A);
			}
			cy += 6.0F;
			int next = codex.getIntOr("next_at", 0);
			Component points = next > 0 ? Component.translatable(P + "codex.points", codex.getIntOr("pts", 0), next, blood)
					: Component.translatable(P + "codex.points_full", codex.getIntOr("pts", 0));
			Brush.fit(g, font, points, x0, cy, width, Palette.INK_SOFT, 0.55F, 0.38F, false);
			cy += 7.0F;
			Brush.fit(g, font, Component.translatable(P + "codex.today", codex.getIntOr("day_pts", 0), codex.getIntOr("day_cap", 200)), x0, cy, width,
					Palette.INK_FAINT, 0.52F, 0.36F, false);
			cy += 8.0F;
		}
		if (mouseX >= x0 && mouseX < x1 && mouseY >= ladderTop && mouseY < cy) {
			List<Component> tip = new ArrayList<>();
			tip.add(Component.translatable(P + "codex.heading").withStyle(ChatFormatting.GOLD));
			for (int i = 1; i <= 5; i++) {
				tip.add(Component.translatable(P + "codex.stage." + i + ".desc").withStyle(i <= stage ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			}
			double trained = codex.getDoubleOr("trained_hp", 0.0D);
			if (trained > 0.0D) {
				tip.add(Component.translatable(P + "codex.trained", String.format(Locale.ROOT, "%.0f", trained)).withStyle(ChatFormatting.AQUA));
			}
			tip.add(TalentTab.source("codex"));
			screen.showTooltip(tip);
		}

		// The Heavenly Flames.
		cy += 2.0F;
		heading(g, Component.translatable(P + "flame.heading"), x0, cy, width, 0.8F);
		cy += 13.0F;
		ListTag flames = power.getListOrEmpty("flames");
		int owned = 0;
		for (int i = 0; i < flames.size(); i++) {
			owned |= 1 << flames.getIntOr(i, 0);
		}
		float orb = Math.min(8.0F, width / 12.0F);
		for (int kind = 0; kind < PowerIds.FLAME_IDS.length; kind++) {
			float ox = x0 + orb + kind * (orb * 2.0F + 4.0F);
			float oy = cy + orb;
			boolean has = (owned & 1 << kind) != 0;
			int rgb = PowerIds.FLAME_RGB[kind];
			if (has) {
				Brush.halo(g, ox, oy, orb, 2.0F, rgb, 0.5F);
			}
			Brush.disc(g, ox, oy, orb, has ? Palette.opaque(rgb) : 0x50465950);
			Brush.disc(g, ox, oy - orb * 0.3F, orb * 0.4F, has ? 0xC0FFFFFF : 0x30FFFFFF);
			if (mouseX >= ox - orb && mouseX < ox + orb && mouseY >= oy - orb && mouseY < oy + orb) {
				screen.showTooltip(List.of(Component.translatable("item.kultivator.heavenly_flame_seed." + PowerIds.FLAME_IDS[kind]).withColor(rgb),
						Component.translatable(P + "flame.where." + PowerIds.FLAME_IDS[kind]),
						Component.translatable(has ? P + "flame.owned" : P + "flame.missing").withStyle(ChatFormatting.GRAY),
						TalentTab.source("flame")));
			}
		}
		cy += orb * 2.0F + 3.0F;
		int ritualLeft = power.getIntOr("ritual_left", 0);
		Component flameLine = ritualLeft > 0 ? Component.translatable(P + "flame.ritual_left", (ritualLeft + 19) / 20)
				: !power.getBooleanOr("flame_eligible", false) ? Component.translatable(P + "why.flame_unfit")
				: Component.translatable(P + "flame.count", flames.size(), power.getIntOr("flame_max", 3));
		Brush.fit(g, font, flameLine, x0, cy, width, ritualLeft > 0 ? Palette.WARN : Palette.INK_SOFT, 0.58F, 0.38F, false);
		cy += 9.0F;

		// The Kunpeng Wings.
		if (power.getBooleanOr("kunpeng", false) && cy < y + h - BUTTON_H - 8) {
			Component wings = Component.translatable(power.getBooleanOr("kunpeng_full", false) ? P + "kunpeng.state_full" : P + "kunpeng.state_third");
			Brush.fit(g, font, wings, x0, y + h - BUTTON_H - 9.0F, width, Palette.onPaper(0x8FD0FF), 0.58F, 0.38F, false);
		}
	}
}
