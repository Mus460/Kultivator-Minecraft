package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One candidate's scroll card on the Calon tablet (DESIGN 20.3.3): face, name and age, the Tekad bar (always visible),
 * the root, soul colour, Duan, talents, physique and special type once tested ("???" before), the verdict seal and the
 * countdown to sunrise. The talismans below it belong to {@link CalonPage}.
 */
final class CandidateCard extends Widget {

	/** Height the talisman row needs under the card's text. */
	static final int BUTTON_ROW = 13;
	private final Supplier<CompoundTag> entry;

	CandidateCard(int x, int y, int w, int h, Supplier<CompoundTag> entry) {
		super(x, y, w, h);
		this.entry = entry;
	}

	/** The candidate's details, or null once it left. */
	@Nullable CompoundTag entry() {
		return entry.get();
	}

	/** True when this candidate carries the "gifted youths avoid an unknown sect" note. */
	boolean gated() {
		CompoundTag card = entry.get();
		return card != null && card.getBooleanOr("gated", false);
	}

	/** True when the mouse is over the verdict seal in the card's corner. */
	boolean overSeal(double mouseX, double mouseY) {
		double dx = mouseX - (x + w - 12.0D);
		double dy = mouseY - (y + 12.0D);
		return dx * dx + dy * dy <= 11.0D * 11.0D;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
		CompoundTag card = entry.get();
		int x1 = x + w;
		int y1 = y + h;
		g.fill(x + 1, y + 2, x1 + 1, y1 + 1, 0x30000000);
		Brush.chamferGradient(g, x, y, x1, y1, 3, 0xFFF4EBCF, 0xFFE6D8B0);
		Brush.frame(g, x + 1, y + 1, x1 - 1, y1 - 1, Palette.argb(over ? 0xE0 : 0xA0, Palette.GOLD));
		if (card == null || card.isEmpty()) {
			return;
		}
		boolean tested = card.getBooleanOr("tested", false);
		boolean testing = card.getBooleanOr("testing", false);
		HallUi.face(g, card.getIntOr("skin", 0), card.getBooleanOr("slim", false), x + 4, y + 4, 16);
		float tx = x + 24.0F;
		float textW = w - 24.0F - 22.0F;
		Brush.fit(g, font, Brush.bold(HallUi.name(card, "name")), tx, y + 4.0F, textW, Palette.INK, 0.75F, 0.5F, false);
		List<Component> flags = new ArrayList<>();
		flags.add(Component.translatable("kultivator.hall.ui.age", card.getIntOr("age", 15)));
		if (card.getBooleanOr("genius", false)) {
			flags.add(Component.translatable("kultivator.hall.ui.flag.genius").withColor(0x8A4CD8));
		}
		if (card.getBooleanOr("token", false)) {
			flags.add(Component.translatable("kultivator.hall.ui.flag.token").withColor(Palette.GOLD_DARK));
		}
		if (card.getBooleanOr("champion", false)) {
			flags.add(Component.translatable("kultivator.hall.ui.flag.champion").withColor(Palette.GOLD_DARK));
		} else if (card.getBooleanOr("war", false)) {
			flags.add(Component.translatable("kultivator.hall.ui.flag.war").withColor(Palette.SEAL_DARK));
		}
		MutableComponent flagLine = Component.empty();
		for (int i = 0; i < flags.size(); i++) {
			if (i > 0) {
				flagLine.append(Component.literal(" · "));
			}
			flagLine.append(flags.get(i));
		}
		Brush.fit(g, font, flagLine, tx, y + 12.0F, textW, Palette.INK_SOFT, 0.55F, 0.4F, false);

		int tekad = card.getIntOr("tekad", 50);
		float cursor = y + 22.0F;
		cursor += HallUi.bar(g, font, Component.translatable("kultivator.hall.ui.tekad"), Component.literal(tekad + "/100"), tekad / 100.0F,
				tekad >= 70 ? Palette.JADE : tekad >= 40 ? Palette.GOLD : Palette.WARN, Palette.GOLD_LIGHT, x + 4.0F, cursor, x1 - 4.0F, 0.55F);
		float lineW = w - 8.0F;
		float scale = 0.52F;
		if (tested) {
			int grade = card.getIntOr("grade", 2);
			RootGrade rootGrade = RootGrade.byOrdinal(grade);
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.root", elements(card.getIntOr("elements", 0)),
					rootGrade.displayName().copy().withColor(rootGrade.color())), x + 4.0F, cursor, lineW, Palette.INK, scale);
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.soul", HallUi.colourName(card.getIntOr("colour", 2)),
					card.getIntOr("duan", 1)), x + 4.0F, cursor, lineW, Palette.INK, scale);
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.talents", talents(card.getIntOr("talents", 0))), x + 4.0F,
					cursor, lineW, Palette.INK_SOFT, scale);
			int physique = card.getIntOr("physique", 0);
			int special = card.getIntOr("special", 0);
			MutableComponent gifts = physique > 0 ? Component.translatable("kultivator.hall.physique." + HallUi.PHYSIQUE_ID[Math.min(8, physique - 1)])
					: Component.translatable("kultivator.hall.physique.none");
			if (special > 0) {
				gifts = gifts.append(Component.literal(" · ")).append(special == 2 && card.getIntOr("realm_r", 0) > 0
						? Component.translatable("kultivator.hall.ui.regressor", Realm.byOrdinal(card.getIntOr("realm_r", 3)).displayName())
						: Component.translatable("kultivator.hall.special." + HallUi.SPECIAL_ID[Math.min(2, special - 1)]));
			}
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.physique", gifts), x + 4.0F, cursor, lineW,
					physique > 0 || special > 0 ? 0xFF7A3FB8 : Palette.INK_SOFT, scale);
		} else {
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.root_hidden"), x + 4.0F, cursor, lineW, Palette.INK_FAINT, scale);
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.soul_hidden"), x + 4.0F, cursor, lineW, Palette.INK_FAINT, scale);
			cursor += HallUi.line(g, font, Component.translatable(testing ? "kultivator.hall.ui.testing" : "kultivator.hall.ui.untested"), x + 4.0F,
					cursor, lineW, testing ? Palette.SEAL_DARK : Palette.INK_FAINT, scale);
		}
		if (card.getBooleanOr("gated", false) && cursor < y1 - BUTTON_ROW - 14) {
			cursor += HallUi.line(g, font, Component.translatable("kultivator.hall.ui.gated"), x + 4.0F, cursor, lineW, Palette.INK_FAINT, 0.45F);
		}
		long left = HallClient.ticksLeft(card.getLongOr("expires", 0L));
		HallUi.line(g, font, Component.translatable("kultivator.hall.ui.leaves", HallUi.clock(left)), x + 4.0F, y1 - BUTTON_ROW - 8.0F, lineW,
				Palette.INK_SOFT, 0.5F);

		// The verdict seal in the corner.
		float sx = x1 - 12.0F;
		float sy = y + 12.0F;
		if (testing) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 150.0D);
			HallUi.stamp(g, font, Component.translatable("kultivator.hall.ui.seal.testing"), sx, sy, 9.0F, Palette.mix(Palette.SEAL, Palette.GOLD, pulse));
		} else if (card.getBooleanOr("token", false) || card.getBooleanOr("champion", false)) {
			HallUi.stamp(g, font, Component.translatable("kultivator.hall.ui.seal.token"), sx, sy, 9.0F, Palette.GOLD_DARK);
		} else if (tested) {
			int verdict = card.getIntOr("verdict", 0);
			int color = verdict == 2 ? 0xFF2E8B57 : verdict == 1 ? 0xFFB07A1C : Palette.SEAL;
			HallUi.stamp(g, font, Component.translatable("kultivator.hall.ui.seal." + verdict), sx, sy, 9.0F, color);
		} else {
			HallUi.stamp(g, font, Component.literal("?"), sx, sy, 9.0F, Palette.INK_FAINT);
		}
	}

	/** "Api + Kayu" with each element in its colour. */
	static MutableComponent elements(int mask) {
		MutableComponent names = Component.empty();
		List<Element> list = Element.fromMask(mask);
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				names.append(Component.literal(" + "));
			}
			names.append(list.get(i).displayName().copy().withColor(list.get(i).color()));
		}
		return names;
	}

	static MutableComponent talents(int bits) {
		if (bits == 0) {
			return Component.translatable("kultivator.hall.talent.none");
		}
		MutableComponent list = Component.empty();
		boolean first = true;
		for (int i = 0; i < HallUi.TALENT_ID.length; i++) {
			if ((bits & 1 << i) != 0) {
				if (!first) {
					list.append(Component.literal(", "));
				}
				list.append(Component.translatable("kultivator.hall.talent." + HallUi.TALENT_ID[i]));
				first = false;
			}
		}
		return list;
	}
}
