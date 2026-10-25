package dev.kultivator.client.hall;

import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One candidate's card on Sekte › Anggota › Calon (DESIGN 20.3.3, 43.7 kit): face, name and age with the flags, the Tekad
 * bar (always visible), the root, soul colour, Duan, talents, physique and special type once tested ("???" before), the
 * verdict chip in the corner and the time left before the candidate leaves. Drawn immediately ({@link Lukis}, text 1.0);
 * the talismans under it belong to {@link CalonPage}.
 */
final class CandidateCard {

	/** Height the talisman row needs under the card's text. */
	static final int BUTTON_ROW = 17;

	/** The card height: the text block, plus the talisman row when the viewer may act. */
	static int tinggi(boolean readonly) {
		return readonly ? 90 : 90 + BUTTON_ROW;
	}

	/** Draws the card of candidate {@code card} into the box (the talisman row at the bottom stays empty unless read-only). */
	static void gambar(Lukis l, CompoundTag card, int x0, int y0, int x1, int y1, boolean sorot, boolean pilih) {
		l.kartu(x0, y0, x1, y1, sorot ? 1.0F : 0.0F, pilih);
		boolean tested = card.getBooleanOr("tested", false);
		boolean testing = card.getBooleanOr("testing", false);
		HallUi.face(l.g, card.getIntOr("skin", 0), card.getBooleanOr("slim", false), x0 + 4, y0 + 4, 16);
		// the verdict chip in the corner
		Component seal = segel(card);
		int chipW = l.font.width(seal) + 8;
		int cx1 = x1 - 3;
		int cx0 = cx1 - chipW;
		int warna = warnaSegel(card);
		if (testing) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 150.0D);
			warna = Palette.mix(Palette.SEAL, Palette.GOLD, pulse);
		}
		l.g.fill(cx0, y0 + 3, cx1, y0 + 14, Palette.argb(0x30, warna));
		Brush.frame(l.g, cx0, y0 + 3, cx1, y0 + 14, Palette.argb(0xD0, warna));
		l.tengah(seal, (cx0 + cx1) / 2.0F, y0 + 5, chipW - 2, Palette.opaque(warna));
		if (l.di(cx0, y0 + 3, cx1, y0 + 14)) {
			List<Component> tip = sealTip(card);
			l.tip(tip.isEmpty() ? List.of(seal) : tip);
		}
		float tx = x0 + 24.0F;
		l.teks(Brush.bold(HallUi.name(card, "name")), tx, y0 + 4, cx0 - tx - 3, l.gaya.teks);
		l.teks(flags(card), tx, y0 + 14, x1 - tx - 4, l.gaya.teksLemah);

		int lx = x0 + 4;
		int lw = x1 - x0 - 8;
		int y = y0 + 25;
		int tekad = card.getIntOr("tekad", 50);
		y += Bilah.baris(l, Component.translatable("kultivator.hall.ui.tekad"), tekad, 100.0F, lx, y, x1 - 4, Component.literal(tekad + "/100"),
				tekad >= 70 ? Palette.JADE : tekad >= 40 ? Palette.GOLD : Palette.WARN);
		if (tested) {
			RootGrade rootGrade = RootGrade.byOrdinal(card.getIntOr("grade", 2));
			l.teks(Component.translatable("kultivator.hall.ui.root", elements(card.getIntOr("elements", 0)),
					rootGrade.displayName().copy().withColor(rootGrade.color())), lx, y, lw, l.gaya.teks);
			y += 10;
			l.teks(Component.translatable("kultivator.hall.ui.soul", HallUi.colourName(card.getIntOr("colour", 2)), card.getIntOr("duan", 1)), lx, y, lw,
					l.gaya.teks);
			y += 10;
			l.teks(Component.translatable("kultivator.hall.ui.talents", talents(card.getIntOr("talents", 0))), lx, y, lw, l.gaya.teksLemah);
			y += 10;
			int physique = card.getIntOr("physique", 0);
			int special = card.getIntOr("special", 0);
			MutableComponent gifts = physique > 0 ? Component.translatable("kultivator.hall.physique." + HallUi.PHYSIQUE_ID[Math.min(8, physique - 1)])
					: Component.translatable("kultivator.hall.physique.none");
			if (special > 0) {
				gifts = gifts.append(Component.literal(" · ")).append(special == 2 && card.getIntOr("realm_r", 0) > 0
						? Component.translatable("kultivator.hall.ui.regressor", Realm.byOrdinal(card.getIntOr("realm_r", 3)).displayName())
						: Component.translatable("kultivator.hall.special." + HallUi.SPECIAL_ID[Math.min(2, special - 1)]));
			}
			l.teks(Component.translatable("kultivator.hall.ui.physique", gifts), lx, y, lw, physique > 0 || special > 0 ? 0xFF7A3FB8 : l.gaya.teksLemah);
			y += 10;
		} else {
			l.teks(Component.translatable("kultivator.hall.ui.root_hidden"), lx, y, lw, l.gaya.teksSamar);
			y += 10;
			l.teks(Component.translatable("kultivator.hall.ui.soul_hidden"), lx, y, lw, l.gaya.teksSamar);
			y += 10;
			l.teks(Component.translatable(testing ? "kultivator.hall.ui.testing" : "kultivator.hall.ui.untested"), lx, y, lw,
					testing ? Palette.SEAL_DARK : l.gaya.teksSamar);
			y += 10;
			if (card.getBooleanOr("gated", false)) {
				l.teks(Component.translatable("kultivator.hall.ui.gated"), lx, y, lw, l.gaya.teksSamar);
				if (l.di(lx, y, lx + lw, y + 9)) {
					l.tip(gatedTip());
				}
			}
			y += 10;
		}
		long left = HallClient.ticksLeft(card.getLongOr("expires", 0L));
		l.teks(Component.translatable("kultivator.hall.ui.leaves", HallUi.clock(left)), lx, y + 1, lw, l.gaya.teksLemah);
		if (tested && card.getBooleanOr("gated", false) && l.di(x0, y0, x1, y + 10)) {
			// tested gifted youths keep the gate note as the card's tooltip (no room for a fifth line)
			l.tip(gatedTip());
		}
	}

	/** The word on the verdict chip. */
	private static Component segel(CompoundTag card) {
		if (card.getBooleanOr("testing", false)) {
			return Component.translatable("kultivator.hall.ui.seal.testing");
		}
		if (card.getBooleanOr("token", false) || card.getBooleanOr("champion", false)) {
			return Component.translatable("kultivator.hall.ui.seal.token");
		}
		if (card.getBooleanOr("tested", false)) {
			return Component.translatable("kultivator.hall.ui.seal." + card.getIntOr("verdict", 0));
		}
		return Component.literal("?");
	}

	private static int warnaSegel(CompoundTag card) {
		if (card.getBooleanOr("token", false) || card.getBooleanOr("champion", false)) {
			return Palette.GOLD_DARK;
		}
		if (card.getBooleanOr("tested", false)) {
			int verdict = card.getIntOr("verdict", 0);
			return verdict == 2 ? 0xFF2E8B57 : verdict == 1 ? 0xFFB07A1C : Palette.SEAL;
		}
		return Palette.INK_FAINT;
	}

	/** "15 tahun · Jenius · dari Desa …" in one line. */
	private static Component flags(CompoundTag card) {
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
		String origin = card.getStringOr("origin", "");
		if (!origin.isEmpty()) {
			// Phase 8 (DESIGN 25.6.5): "dari Desa <nama>".
			Component from = origin.startsWith("kultivator.") ? Component.translatable(origin) : Component.literal(origin);
			flags.add(Component.translatable("kultivator.spine8.cand_origin", from).withColor(Palette.JADE_DARK));
		}
		MutableComponent line = Component.empty();
		for (int i = 0; i < flags.size(); i++) {
			if (i > 0) {
				line.append(Component.literal(" · "));
			}
			line.append(flags.get(i));
		}
		return line;
	}

	/** The verdict of a tested candidate: what it means and where the test comes from (empty before the test). */
	static List<Component> sealTip(@Nullable CompoundTag e) {
		if (e == null || !e.getBooleanOr("tested", false) || e.getBooleanOr("token", false) || e.getBooleanOr("champion", false)) {
			return List.of();
		}
		int verdict = e.getIntOr("verdict", 0);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.ui.accept." + verdict, 100).withStyle(verdict == 0 ? ChatFormatting.RED
				: verdict == 1 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		lines.add(Component.translatable("kultivator.hall.ui.test.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		lines.add(Component.translatable("kultivator.hall.ui.verdict.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (verdict == 0) {
			lines.add(Component.translatable("kultivator.hall.ui.servant.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
		return lines;
	}

	/** The Return of the Mount Hua Sect gate note and its source. */
	static List<Component> gatedTip() {
		return List.of(Component.translatable("kultivator.hall.ui.gated").withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.hall.ui.gated.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
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

	private CandidateCard() {
	}
}
