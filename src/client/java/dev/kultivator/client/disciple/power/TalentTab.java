package dev.kultivator.client.disciple.power;

import dev.kultivator.client.disciple.MuridPage;
import dev.kultivator.client.disciple.MuridSub;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bakat, the talent page of the disciple scroll (Murid › Murid › Kekuatan, top; DESIGN 20.3.4-20.3.6, 20.3.16): on
 * the left the soul colour (an orb in the colour of its soul realm with its XP factor), the sect rank tile, the talent
 * seals and the special candidate's slip; on the right the rare physique's card (with its live meter: toxin, golden
 * blood, the seven gates, the bone technique...) and the signature ultimate with its talismans: "Jurus Pamungkas", the
 * master's "Fusi" during the lotus charge and "Ledakan Gerbang" for the Heretic God Veins. Every line names its source
 * story in the tooltip.
 */
public class TalentTab extends MuridSub {

	private static final int BUTTON_H = 18;
	private static final String P = "kultivator.dpower.";
	/** The lost sect arts by bit (1 Plum Blossom, 2 Purple Mist, 4 Northern Heaven; {@code Halls.arts}). */
	private static final String[] ART_IDS = {"plum", "purple_mist", "northern"};

	private int columnW;
	private TalismanButton ultimate;
	private TalismanButton fusion;
	private TalismanButton burst;

	public TalentTab(MuridPage murid) {
		super(murid);
	}

	@Override
	public String helpTopic() {
		return "bakat";
	}

	/** Stacked under Kekuatan: tall enough for its column of seals and lines at 1.0 (43.0.5). */
	@Override
	public int tinggiMin() {
		return 186;
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.tab.talent");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.tab.talent.short");
	}

	private CompoundTag power() {
		return info().getCompoundOrEmpty(DisciplePowers.INFO_KEY);
	}

	@Override
	protected void build() {
		columnW = Math.max(110, Math.min(190, Math.round(w * 0.46F)));
		int rightX = x + columnW + 6;
		int rightW = x + w - rightX;
		int buttonsTop = y + h - BUTTON_H;
		int half = (rightW - 4) / 2;
		ultimate = add(new TalismanButton(rightX, buttonsTop, half, BUTTON_H, () -> Component.translatable(P + "btn.ultimate")));
		ultimate.urgentWhen(() -> power().getBooleanOr("ult_has", false) && power().getStringOr("ult_reason", "x").isEmpty());
		ultimate.enabledWhen(() -> power().getStringOr("ult_reason", "x").isEmpty());
		ultimate.onPress(() -> send(Actions.DISCIPLE_POWER, "ultimate"));
		ultimate.tooltip(this::ultimateTip);
		fusion = add(new TalismanButton(rightX + half + 4, buttonsTop, rightW - half - 4, BUTTON_H, () -> Component.translatable(P + "btn.fusion")));
		fusion.urgentWhen(() -> power().getBooleanOr("lotus", false) && !power().getBooleanOr("lotus_fused", false));
		fusion.enabledWhen(() -> power().getBooleanOr("lotus", false) && !power().getBooleanOr("lotus_fused", false)
				&& power().getBooleanOr("master_fire", false));
		fusion.onPress(() -> send(Actions.DISCIPLE_POWER, "fusion"));
		fusion.tooltip(() -> List.of(Component.translatable(P + "btn.fusion"), Component.translatable(P + "btn.fusion.desc"),
				source("fusion")));
		burst = add(new TalismanButton(rightX + half + 4, buttonsTop, rightW - half - 4, BUTTON_H, () -> Component.translatable(P + "btn.burst")));
		burst.enabledWhen(() -> power().getIntOr("burst_left", 1) <= 0 && power().getBooleanOr("heretic_revealed", false));
		burst.urgentWhen(() -> power().getBooleanOr("bursting", false));
		burst.onPress(() -> send(Actions.DISCIPLE_POWER, "burst"));
		burst.tooltip(() -> List.of(Component.translatable(P + "btn.burst"), Component.translatable(P + "btn.burst.desc"),
				cooldownLine(power().getIntOr("burst_left", 0)), source("physique.heretic")));
	}

	@Override
	protected boolean visible(Widget widget) {
		CompoundTag power = power();
		if (widget == burst) {
			// A Fire-Dao Heretic shows Fusi in this slot while its lotus charges.
			boolean charging = power.getIntOr("ult_dao", -1) == Dao.FIRE.ordinal() && power.getBooleanOr("lotus", false);
			return power.getIntOr("physique", 0) == PowerIds.PHYSIQUE_HERETIC && power.getBooleanOr("heretic_revealed", false) && !charging;
		}
		if (widget == fusion) {
			return power.getIntOr("ult_dao", -1) == Dao.FIRE.ordinal() && !visible(burst);
		}
		return true;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag power = power();
		if (power.isEmpty()) {
			heading(g, title(), x + 2.0F, y, w - 4.0F, 1.0F);
			Brush.wrap(g, font, Component.translatable(P + "tab.loading"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 1.0F, false);
			return;
		}
		g.enableScissor(x, y, x + columnW + 2, y + h);
		drawLeft(g, power, mouseX, mouseY);
		g.disableScissor();
		drawRight(g, power, mouseX, mouseY);
	}

	private void drawLeft(GuiGraphicsExtractor g, CompoundTag power, int mouseX, int mouseY) {
		float x0 = x + 2.0F;
		float width = columnW - 4.0F;
		heading(g, Component.translatable(P + "tab.soul"), x0, y, width, 1.0F);
		float cy = y + 14.0F;
		// The soul realm: an orb in its colour.
		int colour = Math.max(0, Math.min(6, power.getIntOr("colour", 2)));
		int rgb = PowerIds.COLOUR_RGB[colour];
		float r = 9.0F;
		float ox = x0 + r + 2.0F;
		float oy = cy + r;
		Brush.halo(g, ox, oy, r, 3.0F, rgb, 0.4F);
		Brush.disc(g, ox, oy, r, Palette.darken(Palette.opaque(rgb), 0.35F));
		Brush.disc(g, ox, oy, r - 1.5F, Palette.opaque(rgb));
		Brush.disc(g, ox - 2.5F, oy - 2.5F, 2.0F, 0x90FFFFFF);
		float tx = ox + r + 4.0F;
		float tw = x0 + width - tx;
		Component colourName = Brush.bold(Component.translatable(P + "colour." + PowerIds.COLOUR_IDS[colour]));
		Brush.fit(g, font, colourName, tx, cy, tw, Palette.onPaper(rgb), 1.0F, 1.0F, false);
		Component factor = Component.translatable(P + "colour.factor", String.format(Locale.ROOT, "%.2f", power.getDoubleOr("colour_factor", 1.0D)));
		Brush.fit(g, font, factor, tx, cy + 10.0F, tw, Palette.INK_SOFT, 1.0F, 1.0F, false);
		boolean capped = power.getBooleanOr("red_capped", false);
		if (capped) {
			Brush.fit(g, font, Component.translatable(P + "colour.red_capped"), tx, cy + 20.0F, tw, Palette.DANGER, 1.0F, 1.0F, false);
		}
		if (mouseX >= x0 && mouseX < x0 + width && mouseY >= cy && mouseY < cy + 2.0F * r) {
			tooltip(List.of(Component.translatable(P + "colour." + PowerIds.COLOUR_IDS[colour]).withColor(rgb),
					Component.translatable(P + "colour.tip"), source("colour")));
		}
		cy += (capped ? 30.0F : 2.0F * r + 2.0F) + 4.0F;

		// The sect rank tile.
		int rank = Math.max(0, Math.min(PhysiqueVisuals.RANK_TRIM.length - 1, power.getIntOr("sect_rank", 1)));
		int trim = PhysiqueVisuals.RANK_TRIM[rank];
		Brush.rect(g, x0, cy, x0 + width, cy + 12.0F, Palette.argb(0x50, Palette.PAPER_DEEP));
		Brush.rect(g, x0, cy, x0 + 3.0F, cy + 12.0F, Palette.opaque(trim));
		Brush.fit(g, font, Component.translatable(P + "tab.rank", Component.translatable(P + "rank." + rank)), x0 + 6.0F, cy + 2.0F, width - 8.0F,
				Palette.onPaper(trim), 1.0F, 1.0F, false);
		cy += 16.0F;

		// The restored sect arts (DESIGN 20.3.8) and the Seven Fairies formation (20.3.16), when any is in effect.
		int arts = power.getIntOr("arts", 0);
		boolean fairies = power.getBooleanOr("fairies", false);
		if (arts != 0 || fairies) {
			MutableComponent line = Component.empty();
			List<Component> tip = new ArrayList<>();
			for (int i = 0; i < ART_IDS.length; i++) {
				if ((arts & 1 << i) == 0) {
					continue;
				}
				if (!line.getSiblings().isEmpty()) {
					line.append(" · ");
				}
				line.append(Component.translatable(P + "art." + ART_IDS[i]));
				tip.add(Component.translatable(P + "art." + ART_IDS[i]).withStyle(ChatFormatting.GOLD));
				tip.add(Component.translatable(P + "art." + ART_IDS[i] + ".desc"));
			}
			if (arts != 0) {
				tip.add(source("art"));
			}
			if (fairies) {
				if (!line.getSiblings().isEmpty()) {
					line.append(" · ");
				}
				line.append(Component.translatable(P + "fairies"));
				tip.add(Component.translatable(P + "fairies").withStyle(ChatFormatting.AQUA));
				tip.add(Component.translatable(P + "fairies.desc"));
				tip.add(source("fairies"));
			}
			Brush.fit(g, font, line, x0, cy, width, Palette.JADE_DARK, 1.0F, 1.0F, false);
			if (mouseX >= x0 && mouseX < x0 + width && mouseY >= cy && mouseY < cy + 9.0F) {
				tooltip(tip);
			}
			cy += 11.0F;
		}

		// Talent seals.
		Brush.fit(g, font, Brush.bold(Component.translatable(P + "tab.talents")), x0, cy, width, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		cy += 11.0F;
		int talents = power.getIntOr("talents", 0);
		float seal = 17.0F;
		float sx = x0;
		boolean any = false;
		for (int i = 0; i < PowerIds.TALENT_IDS.length; i++) {
			if ((talents & 1 << i) == 0) {
				continue;
			}
			any = true;
			if (sx + seal > x0 + width) {
				sx = x0;
				cy += seal + 3.0F;
			}
			drawSeal(g, sx, cy, seal, i);
			if (mouseX >= sx && mouseX < sx + seal && mouseY >= cy && mouseY < cy + seal) {
				String id = PowerIds.TALENT_IDS[i];
				List<Component> tip = new ArrayList<>();
				tip.add(Component.translatable(P + "talent." + id).withStyle(ChatFormatting.GOLD));
				tip.add(Component.translatable(P + "talent." + id + ".desc"));
				if (i == 5) {
					tip.add(Component.translatable(P + "talent.sword_sense.kills", power.getIntOr("sword_kills", 0)).withStyle(ChatFormatting.GRAY));
				}
				tip.add(source("talent." + id));
				tooltip(tip);
			}
			sx += seal + 3.0F;
		}
		if (!any) {
			Brush.fit(g, font, Component.translatable(P + "tab.no_talent"), x0, cy + 4.0F, width, Palette.INK_FAINT, 1.0F, 1.0F, false);
		}
		cy += seal + 5.0F;

		// The special candidate.
		int special = power.getIntOr("special", 0);
		if (special > 0 && special < PowerIds.SPECIAL_IDS.length && cy < y + h - 10.0F) {
			String id = PowerIds.SPECIAL_IDS[special];
			boolean hidden = power.getBooleanOr("hidden", false) && special == PowerIds.SPECIAL_BEGGAR;
			Component name = hidden ? Component.translatable(P + "special.hidden") : Component.translatable(P + "special." + id);
			Brush.fit(g, font, Brush.bold(name), x0, cy, width, Palette.SEAL_DARK, 1.0F, 1.0F, false);
			MutableComponent line = hidden ? Component.translatable(P + "special.hidden.desc")
					: special == PowerIds.SPECIAL_REGRESSOR ? Component.translatable(P + "special.regressor.line",
					power.getBooleanOr("regressor_known", true)
							? Realm.byOrdinal(power.getIntOr("regressor_realm", Realm.GOLDEN_CORE.ordinal())).displayName()
							: Component.literal("???"),
					Component.translatable(power.getBooleanOr("regressing", false) ? P + "special.regressor.active" : P + "special.regressor.done"))
					: Component.translatable(P + "special." + id + ".short");
			float lineH = Brush.wrap(g, font, line, x0, cy + 10.0F, width, Palette.INK_SOFT, 1.0F, false);
			if (mouseX >= x0 && mouseX < x0 + width && mouseY >= cy && mouseY < cy + 10.0F + lineH) {
				tooltip(hidden ? List.of(name, Component.translatable(P + "special.hidden.desc"))
						: List.of(Component.translatable(P + "special." + id).withStyle(ChatFormatting.GOLD),
						Component.translatable(P + "special." + id + ".desc"), source("special." + id)));
			}
		}
	}

	private void drawRight(GuiGraphicsExtractor g, CompoundTag power, int mouseX, int mouseY) {
		int x0 = x + columnW + 6;
		int x1 = x + w;
		float width = x1 - x0;
		float cy = y;
		heading(g, Component.translatable(P + "tab.physique"), x0, cy, width, 1.0F);
		cy += 14.0F;
		int physique = power.getIntOr("physique", 0);
		boolean hidden = power.getBooleanOr("hidden", false) && physique == PowerIds.PHYSIQUE_HERETIC;
		int bottom = y + h - BUTTON_H - 4;
		int ultTop = bottom - 31;
		panel(g, x0, Math.round(cy), x1, ultTop - 3);
		float px = x0 + 4.0F;
		float pw = width - 8.0F;
		float py = cy + 3.0F;
		if (physique <= 0 || physique >= PowerIds.PHYSIQUE_IDS.length || hidden) {
			g.enableScissor(x0 + 1, Math.round(py), x1 - 1, ultTop - 4);
			Brush.wrap(g, font, Component.translatable(hidden ? P + "physique.hidden" : P + "physique.none"), px, py, pw, Palette.INK_FAINT, 1.0F, false);
			g.disableScissor();
		} else {
			String id = PowerIds.PHYSIQUE_IDS[physique];
			int rgb = PowerIds.PHYSIQUE_RGB[physique];
			Brush.fit(g, font, Brush.bold(Component.translatable(P + "physique." + id)), px, py, pw, Palette.onPaper(rgb), 1.0F, 1.0F, false);
			py += 11.0F;
			Component state = physiqueState(physique, power);
			if (state != null) {
				Brush.fit(g, font, state, px, py, pw, Palette.JADE_DARK, 1.0F, 1.0F, false);
				py += 10.0F;
			}
			if (physique == PowerIds.PHYSIQUE_HERETIC) {
				drawGates(g, px, py, pw, power.getIntOr("gates", 0), mouseX, mouseY);
				py += 10.0F;
			}
			g.enableScissor(x0 + 1, Math.round(py), x1 - 1, ultTop - 4);
			Brush.wrap(g, font, Component.translatable(P + "physique." + id + ".short"), px, py, pw, Palette.INK_SOFT, 1.0F, false);
			g.disableScissor();
			if (mouseX >= x0 && mouseX < x1 && mouseY >= cy && mouseY < ultTop - 3) {
				tooltip(List.of(Component.translatable(P + "physique." + id).withColor(rgb),
						Component.translatable(P + "physique." + id + ".desc"), source("physique." + id)));
			}
		}

		// The signature ultimate.
		int dao = power.getIntOr("ult_dao", -1);
		Dao chosen = Dao.byOrdinal(dao);
		Component ultName = chosen == null ? Component.translatable(P + "ult.none") : Component.translatable(P + "ult." + chosen.id());
		Brush.fit(g, font, Brush.bold(Component.translatable(P + "tab.ultimate")), x0, ultTop, width, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		Brush.fit(g, font, ultName, x0, ultTop + 10.0F, width, chosen == null ? Palette.INK_FAINT : Palette.onPaper(chosen.color()), 1.0F, 1.0F, false);
		Component state;
		if (!power.getBooleanOr("ult_has", false)) {
			state = Component.translatable(chosen == null ? P + "why.ult_no_dao" : P + "why.ult_realm");
		} else if (power.getBooleanOr("lotus", false)) {
			state = Component.translatable(power.getBooleanOr("lotus_fused", false) ? P + "lotus.state_fused" : P + "lotus.state_charging");
		} else {
			int left = power.getIntOr("ult_left", 0);
			state = left > 0 ? cooldownLine(left) : Component.translatable(P + "ult.ready");
		}
		Brush.fit(g, font, state, x0, ultTop + 20.0F, width, Palette.INK_SOFT, 1.0F, 1.0F, false);
		if (mouseX >= x0 && mouseX < x1 && mouseY >= ultTop && mouseY < bottom) {
			tooltip(ultimateTip());
		}
	}

	private List<Component> ultimateTip() {
		CompoundTag power = power();
		Dao chosen = Dao.byOrdinal(power.getIntOr("ult_dao", -1));
		List<Component> tip = new ArrayList<>();
		if (chosen == null) {
			tip.add(Component.translatable(P + "ult.none"));
			tip.add(Component.translatable(P + "ult.none.desc"));
			return tip;
		}
		tip.add(Component.translatable(P + "ult." + chosen.id()).withColor(chosen.color()));
		tip.add(Component.translatable(P + "ult." + chosen.id() + ".desc"));
		String reason = power.getStringOr("ult_reason", "");
		if (!reason.isEmpty()) {
			tip.add(Component.translatable(reason).withStyle(ChatFormatting.RED));
		}
		tip.add(Component.translatable(P + "ult.auto").withStyle(ChatFormatting.GRAY));
		tip.add(source("ult." + chosen.id()));
		return tip;
	}

	/** The live line of a physique (meters and cooldowns). */
	private static Component physiqueState(int physique, CompoundTag power) {
		return switch (physique) {
			case PowerIds.PHYSIQUE_POISON -> power.getBooleanOr("rampage", false) ? Component.translatable(P + "physique.poison.rampage")
					: Component.translatable(P + "physique.poison.meter", power.getIntOr("toxic", 0));
			case PowerIds.PHYSIQUE_TAIXU -> power.getBooleanOr("dragon", false) ? Component.translatable(P + "physique.taixu.active")
					: power.getIntOr("dragon_left", 0) > 0 ? cooldownLine(power.getIntOr("dragon_left", 0))
					: Component.translatable(P + "physique.taixu.ready");
			case PowerIds.PHYSIQUE_DOU_DI -> Component.translatable(power.getBooleanOr("dou_di", false) ? P + "physique.dou_di.awake" : P + "physique.dou_di.sleeping");
			case PowerIds.PHYSIQUE_GOLDEN_BODY -> Component.translatable(P + "physique.golden_body.meter", power.getIntOr("gold_stacks", 0),
					String.format(Locale.ROOT, "%.1f", power.getDoubleOr("gold_hp", 0.0D)));
			case PowerIds.PHYSIQUE_SUPREME_BONE -> {
				Technique bone = Technique.byId(power.getStringOr("bone", ""));
				yield Component.translatable(power.getBooleanOr("bone_shed", false) ? P + "physique.supreme_bone.shed" : P + "physique.supreme_bone.art",
						bone == null ? Component.literal("?") : bone.displayName());
			}
			case PowerIds.PHYSIQUE_HERETIC -> power.getBooleanOr("bursting", false) ? Component.translatable(P + "physique.heretic.bursting")
					: power.getBooleanOr("exhausted", false) ? Component.translatable(P + "physique.heretic.exhausted")
					: Component.translatable(P + "physique.heretic.gates", power.getIntOr("gates", 0));
			default -> null;
		};
	}

	/** Seven beads for the seven Heretic God gates. */
	private void drawGates(GuiGraphicsExtractor g, float x0, float y0, float width, int gates, int mouseX, int mouseY) {
		float step = Math.min(12.0F, width / 7.0F);
		for (int i = 0; i < 7; i++) {
			float cx = x0 + step * i + step / 2.0F;
			float cy = y0 + 4.0F;
			boolean open = i < gates;
			Brush.disc(g, cx, cy, 3.4F, Palette.argb(0xC0, Palette.INK_SOFT));
			Brush.disc(g, cx, cy, 2.6F, open ? 0xFFD8263A : 0xFF8A8A80);
			if (open) {
				Brush.disc(g, cx - 0.8F, cy - 0.8F, 0.8F, 0xA0FFFFFF);
			}
			if (mouseX >= cx - 4.0F && mouseX < cx + 4.0F && mouseY >= cy - 4.0F && mouseY < cy + 4.0F) {
				tooltip(List.of(Component.translatable(P + "gate." + (i + 1)).withColor(0xD8263A),
						Component.translatable(P + "gate.when." + (i + 1)),
						Component.translatable(open ? P + "gate.open" : P + "gate.closed").withStyle(ChatFormatting.GRAY)));
			}
		}
	}

	/** A talent's seal: a red square with the talent's mark. */
	private void drawSeal(GuiGraphicsExtractor g, float sx, float sy, float size, int index) {
		int x0 = Math.round(sx);
		int y0 = Math.round(sy);
		int x1 = Math.round(sx + size);
		int y1 = Math.round(sy + size);
		g.fill(x0 + 1, y0 + 2, x1 + 1, y1 + 1, 0x40000000);
		g.fill(x0, y0, x1, y1, Palette.SEAL);
		Brush.frame(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Palette.argb(0xB0, Palette.TALISMAN));
		Component mark = Component.translatable(P + "talent." + PowerIds.TALENT_IDS[index] + ".mark");
		Brush.fitCentered(g, font, mark, sx + size / 2.0F, sy + size / 2.0F - 4.0F, size - 2.0F, Palette.TALISMAN, 1.0F, 1.0F, false);
	}

	private static Component cooldownLine(int ticks) {
		int seconds = (Math.max(0, ticks) + 19) / 20;
		return Component.translatable(P + "cooldown", String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
	}

	/** "Sumber: ..." in grey italics. */
	static Component source(String key) {
		return Component.translatable(P + key + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
	}
}
