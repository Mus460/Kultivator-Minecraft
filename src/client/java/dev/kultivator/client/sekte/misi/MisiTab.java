package dev.kultivator.client.sekte.misi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The pictures of X's two tabs on the Misi tablet ({@code client.sectevent.MissionPage}, DESIGN 36.2.7): the story board
 * (cards with the template's seal, tier, days and the three stage beads in their kind's colour), the chance preview, the
 * running missions (team chips, three stage beads that fill in as the news arrives, a time cord) and the report history.
 * Pure drawing from {@link MisiClient#state()}; the page owns the buttons and the roster.
 */
public final class MisiTab {

	public static final int CARD_H = 24;
	public static final int MISSION_H = 36;
	public static final int ROW_H = 12;
	public static final String[] STANCES = {"damai", "tegas", "licik"};
	private static final String[] KINDS = {"tempur", "licik", "damai", "jelajah"};
	private static final int[] KIND_COLORS = {0xC0392B, 0x8E44AD, 0x2E86C1, 0x27AE60};
	private static final String[] KIND_GLYPHS = {"戰", "謀", "和", "行"};

	// ================================================================== state access

	public static List<CompoundTag> offers() {
		return compounds(MisiClient.state().getListOrEmpty("papan"));
	}

	public static List<CompoundTag> missions() {
		return compounds(MisiClient.state().getListOrEmpty("aktif"));
	}

	public static List<CompoundTag> history() {
		return compounds(MisiClient.state().getListOrEmpty("riwayat"));
	}

	public static @Nullable CompoundTag offer(int index) {
		List<CompoundTag> offers = offers();
		return index >= 0 && index < offers.size() ? offers.get(index) : null;
	}

	public static boolean full() {
		return missions().size() >= Math.max(1, MisiClient.state().getIntOr("maks", 2));
	}

	/** A text that changes when the buttons of the story tabs must be rebuilt. */
	public static String shape() {
		StringBuilder b = new StringBuilder("m");
		b.append(MisiClient.state().getIntOr("tim", 4));
		for (CompoundTag offer : offers()) {
			b.append(offer.getBooleanOr("taken", false) ? 't' : 'o');
		}
		b.append('|');
		for (CompoundTag mission : missions()) {
			b.append(mission.getIntOr("id", 0)).append(mission.getBooleanOr("finished", false) ? 'f' : 'r')
					.append(mission.getBooleanOr("ditarik", false) ? 'd' : '-').append(hurt(mission) ? 'h' : '-').append(',');
		}
		b.append('|').append(history().size());
		return b.toString();
	}

	public static boolean hurt(CompoundTag mission) {
		for (CompoundTag member : compounds(mission.getListOrEmpty("team"))) {
			if (member.getBooleanOr("hurt", false) && !member.getBooleanOr("left", false)) {
				return true;
			}
		}
		return false;
	}

	static List<CompoundTag> compounds(ListTag list) {
		List<CompoundTag> out = new ArrayList<>();
		for (Tag tag : list) {
			if (tag instanceof CompoundTag compound) {
				out.add(compound);
			}
		}
		return out;
	}

	static Component name(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.empty());
	}

	static int kindIndex(String id) {
		for (int i = 0; i < KINDS.length; i++) {
			if (KINDS[i].equals(id)) {
				return i;
			}
		}
		return 3;
	}

	/** The stage kinds of a template (the same order as {@code sekte.misi.TemplatMisi}). */
	static String[] kindsOf(String tpl) {
		return switch (tpl) {
			case "kawal", "desa" -> new String[]{"jelajah", "tempur", "damai"};
			case "buru" -> new String[]{"jelajah", "tempur", "tempur"};
			case "reruntuhan" -> new String[]{"jelajah", "licik", "tempur"};
			case "utusan", "cari" -> new String[]{"jelajah", "damai", "damai"};
			case "herba" -> new String[]{"jelajah", "jelajah", "tempur"};
			case "pusaka" -> new String[]{"licik", "tempur", "tempur"};
			case "perbatasan" -> new String[]{"tempur", "jelajah", "tempur"};
			case "lelang" -> new String[]{"damai", "licik", "damai"};
			case "kabar" -> new String[]{"jelajah", "licik", "jelajah"};
			case "samar" -> new String[]{"licik", "licik", "tempur"};
			default -> new String[]{"jelajah", "jelajah", "jelajah"};
		};
	}

	static String glyphOf(String tpl) {
		return switch (tpl) {
			case "kawal" -> "護";
			case "buru" -> "獵";
			case "reruntuhan" -> "墟";
			case "utusan" -> "使";
			case "herba" -> "草";
			case "desa" -> "村";
			case "pusaka" -> "寶";
			case "cari" -> "尋";
			case "perbatasan" -> "境";
			case "lelang" -> "市";
			case "kabar" -> "聞";
			case "samar" -> "影";
			default -> "?";
		};
	}

	static int colorOf(String tpl) {
		return switch (tpl) {
			case "kawal" -> 0xC9A227;
			case "buru" -> 0xB0413E;
			case "reruntuhan" -> 0x8E7CC3;
			case "utusan" -> 0x3D85C6;
			case "herba" -> 0x6AA84F;
			case "desa" -> 0xE69138;
			case "pusaka" -> 0xF1C232;
			case "cari" -> 0x76A5AF;
			case "perbatasan" -> 0x999999;
			case "lelang" -> 0xD5A6BD;
			case "kabar" -> 0x93C47D;
			case "samar" -> 0x674EA7;
			default -> 0x888888;
		};
	}

	static String tplKey(String tpl) {
		return "kultivator.sekte.misi.t." + tpl;
	}

	static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}

	static long now() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0L : minecraft.level.getGameTime();
	}

	// ================================================================== small pictures

	/** A round seal in {@code rgb} with a glyph. */
	static void seal(GuiGraphicsExtractor g, Font font, float cx, float cy, float r, int rgb, String glyph, boolean dim) {
		int base = dim ? Palette.argb(0x70, rgb) : Palette.argb(0xFF, rgb);
		Brush.disc(g, cx, cy, r + 0.8F, Palette.argb(0xA0, 0x2A1A10));
		Brush.disc(g, cx, cy, r, base);
		Brush.ring(g, cx, cy, r - 1.4F, r - 0.8F, Palette.argb(0x90, 0xFFF3D9));
		Brush.centered(g, font, Component.literal(glyph), cx, cy - r * 0.55F, 0xFFFFF6E6, Math.max(0.4F, r / 8.0F), false);
	}

	/** One stage bead: {@code state} 0 pending, 1 succeeded, 2 failed. */
	static void bead(GuiGraphicsExtractor g, Font font, float cx, float cy, float r, String kind, int state, boolean ambush) {
		int k = kindIndex(kind);
		int color = KIND_COLORS[k];
		if (state == 0) {
			Brush.disc(g, cx, cy, r, Palette.argb(0x50, color));
			Brush.ring(g, cx, cy, r - 0.7F, r, Palette.argb(0xC0, color));
		} else {
			Brush.disc(g, cx, cy, r, state == 1 ? Palette.argb(0xFF, color) : Palette.argb(0xFF, 0x5A3A30));
			Brush.ring(g, cx, cy, r - 0.8F, r, state == 1 ? Palette.argb(0xFF, Palette.GOLD) : Palette.argb(0xFF, 0xC0392B));
		}
		if (ambush) {
			Brush.ring(g, cx, cy, r + 0.6F, r + 1.4F, Palette.argb(0xE0, 0xE04030));
		}
		Brush.centered(g, font, Component.literal(KIND_GLYPHS[k]), cx, cy - r * 0.55F, state == 0 ? Palette.argb(0xD0, color) : 0xFFFFF6E6,
				Math.max(0.35F, r / 8.5F), false);
		if (state == 2) {
			Brush.line(g, cx - r * 0.7F, cy - r * 0.7F, cx + r * 0.7F, cy + r * 0.7F, 1.0F, Palette.argb(0xC0, 0xFF6050));
		}
	}

	// ================================================================== tab "Misi Cerita": the board

	public static void drawBoard(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1, int mouseX, int mouseY, int selected,
			Consumer<List<Component>> tip) {
		List<CompoundTag> offers = offers();
		if (!MisiClient.state().getBooleanOr("on", true)) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.misi.ui.mati"), x0 + 2.0F, y0 + 2.0F, x1 - x0 - 4.0F, Palette.INK_FAINT, 0.6F, false);
			return;
		}
		if (offers.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.misi.ui.papan_kosong"), x0 + 2.0F, y0 + 2.0F, x1 - x0 - 4.0F,
					Palette.INK_FAINT, 0.6F, false);
			return;
		}
		int cy = y0;
		for (int i = 0; i < offers.size(); i++) {
			if (cy + CARD_H - 2 > y1) {
				break;
			}
			CompoundTag offer = offers.get(i);
			String tpl = offer.getStringOr("tpl", "");
			boolean taken = offer.getBooleanOr("taken", false);
			boolean chosen = i == selected && !taken;
			boolean over = mouseX >= x0 && mouseX < x1 && mouseY >= cy && mouseY < cy + CARD_H - 2;
			int c0 = taken ? Palette.argb(0x40, Palette.LOCKED_PAPER) : chosen ? Palette.argb(0xA0, Palette.JADE_PALE)
					: over ? Palette.argb(0x60, Palette.TALISMAN) : Palette.argb(0x48, Palette.PAPER_DEEP);
			g.fill(x0, cy, x1, cy + CARD_H - 2, c0);
			// a thin band in the template's colour on the left edge, like a book's spine
			g.fill(x0, cy, x0 + 2, cy + CARD_H - 2, Palette.argb(taken ? 0x50 : 0xE0, colorOf(tpl)));
			Brush.frame(g, x0, cy, x1, cy + CARD_H - 2, chosen ? Palette.GOLD : Palette.argb(0xA0, Palette.GOLD_DARK));
			seal(g, font, x0 + 11.0F, cy + 11.0F, 7.0F, colorOf(tpl), glyphOf(tpl), taken);
			int tier = offer.getIntOr("tier", 1);
			Component title = Brush.bold(Component.translatable(tplKey(tpl)));
			float textX = x0 + 21.0F;
			float beadRoom = 34.0F;
			Brush.fit(g, font, title, textX, cy + 2.0F, x1 - textX - beadRoom - 2.0F, taken ? Palette.INK_FAINT : Palette.INK, 0.7F, 0.45F, false);
			MutableComponent detail = Component.translatable("kultivator.sekte.misi.ui.kartu", tier, offer.getIntOr("days", 1));
			Component who = extra(offer);
			if (who != null) {
				detail = detail.append(Component.literal(" · ")).append(who);
			}
			if (offer.getIntOr("price", 0) > 0) {
				detail = detail.append(Component.literal(" · ")).append(Component.translatable("kultivator.sekte.misi.ui.harga", offer.getIntOr("price", 0)));
			}
			Brush.fit(g, font, taken ? Component.translatable("kultivator.sekte.misi.ui.diambil") : detail, textX, cy + 11.0F, x1 - textX - 3.0F,
					Palette.INK_SOFT, 0.55F, 0.4F, false);
			String[] kinds = kindsOf(tpl);
			for (int k = 0; k < 3; k++) {
				bead(g, font, x1 - beadRoom + 5.0F + k * 10.5F, cy + 6.5F, 4.2F, kinds[k], 0, false);
			}
			if (over && !taken) {
				List<Component> lines = new ArrayList<>();
				lines.add(title);
				lines.add(Component.translatable(tplKey(tpl) + ".desc"));
				MutableComponent stages = Component.translatable("kultivator.sekte.misi.ui.tahap");
				for (int k = 0; k < 3; k++) {
					stages = stages.append(Component.literal(k == 0 ? " " : " → ")).append(Component.translatable("kultivator.sekte.misi.jenis." + kinds[k])
							.withColor(KIND_COLORS[kindIndex(kinds[k])]));
				}
				lines.add(stages);
				lines.add(Component.translatable("kultivator.sekte.misi.ui.tip_sikap").withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("kultivator.sekte.misi.sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				tip.accept(lines);
			}
			cy += CARD_H;
		}
	}

	/** The sect, village or missing disciple an offer names (or null). */
	static @Nullable Component extra(CompoundTag offer) {
		if (offer.contains("target_name")) {
			return Component.translatable("kultivator.sekte.misi.ui.cari", name(offer, "target_name"));
		}
		if (offer.contains("village")) {
			return Component.literal(offer.getStringOr("village", ""));
		}
		if (offer.contains("sect_name")) {
			return name(offer, "sect_name");
		}
		return null;
	}

	/** The preview line under the stance buttons: the three stage chances and the chance of a legendary end. */
	public static void drawPreview(GuiGraphicsExtractor g, Font font, float x0, float y0, float width, int offer, String stance, String souls,
			boolean anyChosen, Consumer<List<Component>> tip, int mouseX, int mouseY) {
		Component line;
		CompoundTag preview = MisiClient.state().getCompoundOrEmpty("pratinjau");
		boolean fresh = preview.getIntOr("offer", -1) == offer && stance.equals(preview.getStringOr("sikap", ""))
				&& souls.equals(preview.getStringOr("souls", ""));
		if (full()) {
			line = Component.translatable("kultivator.sekte.misi.ui.penuh", MisiClient.state().getIntOr("maks", 2)).withColor(Palette.DANGER);
		} else if (offer < 0) {
			line = Component.translatable("kultivator.sekte.misi.ui.pilih_misi");
		} else if (!anyChosen) {
			line = Component.translatable("kultivator.sekte.misi.ui.pilih_tim");
		} else if (!fresh) {
			line = Component.translatable("kultivator.sekte.misi.ui.menghitung");
		} else if (!preview.getStringOr("blocker", "").isEmpty()) {
			line = Component.translatable(preview.getStringOr("blocker", ""), name(preview, "blocker_name")).withColor(Palette.DANGER);
		} else if (!preview.getBooleanOr("rank_ok", true)) {
			line = Component.translatable("kultivator.sekte.misi.ui.jenjang_kurang").withColor(Palette.DANGER);
		} else {
			line = Component.translatable("kultivator.sekte.misi.ui.peluang", percent(preview.getCompoundOrEmpty("s0").getDoubleOr("total", 0.0D)),
					percent(preview.getCompoundOrEmpty("s1").getDoubleOr("total", 0.0D)), percent(preview.getCompoundOrEmpty("s2").getDoubleOr("total", 0.0D)),
					percent(preview.getDoubleOr("legend", 0.0D))).withColor(Palette.JADE_DARK);
		}
		Brush.fit(g, font, line, x0, y0, width, Palette.INK_SOFT, 0.6F, 0.42F, false);
		if (fresh && mouseX >= x0 && mouseX < x0 + width && mouseY >= y0 - 1 && mouseY < y0 + 8) {
			List<Component> lines = new ArrayList<>();
			lines.add(Brush.bold(Component.translatable("kultivator.sekte.misi.ui.rincian")));
			for (int s = 0; s < 3; s++) {
				CompoundTag part = preview.getCompoundOrEmpty("s" + s);
				lines.add(Component.translatable("kultivator.sekte.misi.ui.rincian.baris", s + 1, percent(part.getDoubleOr("total", 0.0D)),
						signed(part.getDoubleOr("realm", 0.0D)), signed(part.getDoubleOr("stance", 0.0D)), signed(part.getDoubleOr("traits", 0.0D)),
						signed(part.getDoubleOr("ties", 0.0D)), signed(part.getDoubleOr("team", 0.0D)),
						String.format(Locale.ROOT, "×%.2f", part.getDoubleOr("work", 1.0D) * part.getDoubleOr("office", 1.0D))));
			}
			lines.add(Component.translatable("kultivator.sekte.misi.ui.rincian.ket").withStyle(ChatFormatting.GRAY));
			tip.accept(lines);
		}
	}

	private static String signed(double value) {
		long p = Math.round(value * 100.0D);
		return (p >= 0 ? "+" : "") + p;
	}

	// ================================================================== tab "Kabar Misi"

	/** The running missions from {@code y0}; returns the y below them. */
	public static int drawMissions(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int buttonsW, int mouseX, int mouseY,
			Consumer<List<Component>> tip) {
		List<CompoundTag> missions = missions();
		if (missions.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.misi.ui.tidak_ada"), x0 + 2.0F, y0 + 2.0F, x1 - x0 - 4.0F, Palette.INK_FAINT,
					0.6F, false);
			return y0 + 14;
		}
		long now = now();
		int cy = y0;
		for (CompoundTag mission : missions) {
			String tpl = mission.getStringOr("tpl", "");
			g.fill(x0, cy, x1, cy + MISSION_H - 2, Palette.argb(0x40, Palette.PAPER_DEEP));
			g.fill(x0, cy, x0 + 2, cy + MISSION_H - 2, Palette.argb(0xE0, colorOf(tpl)));
			Brush.frame(g, x0, cy, x1, cy + MISSION_H - 2, Palette.argb(0xC0, Palette.GOLD));
			seal(g, font, x0 + 11.0F, cy + 10.0F, 7.0F, colorOf(tpl), glyphOf(tpl), false);
			float textX = x0 + 21.0F;
			float room = x1 - buttonsW - textX - 4.0F;
			Component title = Brush.bold(Component.translatable("kultivator.sekte.misi.ui.berjalan", Component.translatable(tplKey(tpl)),
					mission.getIntOr("tier", 1), Component.translatable("kultivator.sekte.misi.sikap." + mission.getStringOr("sikap", "damai"))));
			Brush.fit(g, font, title, textX, cy + 2.0F, room, Palette.INK, 0.66F, 0.42F, false);
			// the team: names in their root colour, a red cross for the hurt, grey for the ones left behind
			MutableComponent team = Component.empty();
			boolean first = true;
			for (CompoundTag member : compounds(mission.getListOrEmpty("team"))) {
				if (!first) {
					team = team.append(Component.literal(", ").withColor(Palette.INK_FAINT));
				}
				first = false;
				int color = member.getBooleanOr("left", false) ? Palette.INK_FAINT : Palette.onPaper(member.getIntOr("color", 0x888888));
				team = team.append(name(member, "name").copy().withColor(color));
				if (member.getBooleanOr("hurt", false)) {
					team = team.append(Component.literal(" ✚").withColor(0xFFC0392B));
				}
			}
			Brush.fit(g, font, team, textX, cy + 11.0F, room, Palette.INK_SOFT, 0.55F, 0.4F, false);
			// three stage beads and the time cord
			ListTag stages = mission.getListOrEmpty("stages");
			String[] kinds = kindsOf(tpl);
			for (int k = 0; k < 3; k++) {
				CompoundTag stage = k < stages.size() && stages.get(k) instanceof CompoundTag t ? t : null;
				int state = stage == null || stage.isEmpty() ? 0 : stage.getBooleanOr("ok", false) ? 1 : 2;
				String kind = stage == null || stage.isEmpty() ? kinds[k] : stage.getStringOr("kind", kinds[k]);
				float bx = textX + 4.5F + k * 11.0F;
				bead(g, font, bx, cy + 24.5F, 4.4F, kind, state, stage != null && stage.getBooleanOr("amb", false));
				if (mouseX >= bx - 5 && mouseX < bx + 5 && mouseY >= cy + 19 && mouseY < cy + 30) {
					tip.accept(List.of(Component.translatable("kultivator.sekte.misi.ui.bead", k + 1, Component.translatable("kultivator.sekte.misi.jenis." + kind),
							Component.translatable(state == 0 ? "kultivator.sekte.misi.ui.bead.tunggu" : state == 1
									? "kultivator.sekte.misi.ui.bead.ok" : "kultivator.sekte.misi.ui.bead.gagal"))));
				}
			}
			long start = mission.getLongOr("start", now);
			long end = mission.getLongOr("end", now);
			float cordX0 = textX + 38.0F;
			float cordX1 = x1 - buttonsW - 6.0F;
			float t = end <= start ? 1.0F : Math.max(0.0F, Math.min(1.0F, (now - start) / (float) (end - start)));
			Brush.rect(g, cordX0, cy + 24.0F, cordX1, cy + 25.2F, Palette.argb(0x60, Palette.INK_FAINT));
			Brush.rect(g, cordX0, cy + 24.0F, cordX0 + (cordX1 - cordX0) * t, cy + 25.2F, Palette.argb(0xE0, Palette.SEAL));
			Brush.disc(g, cordX0 + (cordX1 - cordX0) * t, cy + 24.6F, 1.6F, Palette.GOLD);
			Component when;
			if (mission.getBooleanOr("finished", false)) {
				when = Component.translatable("kultivator.sekte.misi.ui.pulang_jalan");
			} else if (mission.getBooleanOr("ditarik", false)) {
				when = Component.translatable("kultivator.sekte.misi.ui.ditarik", clock(end - now));
			} else {
				when = Component.translatable("kultivator.sekte.misi.ui.sisa", String.format(Locale.ROOT, "%.1f", Math.max(0L, end - now) / 24000.0D),
						clock(end - now));
			}
			Brush.fit(g, font, when, cordX0, cy + 27.0F, cordX1 - cordX0, mission.getBooleanOr("amb", false) ? Palette.DANGER : Palette.JADE_DARK,
					0.5F, 0.38F, false);
			if (mission.getBooleanOr("amb", false) && mouseX >= x0 && mouseX < x1 - buttonsW && mouseY >= cy && mouseY < cy + MISSION_H - 2) {
				tip.accept(List.of(Component.translatable("kultivator.sekte.misi.ui.sergap", name(mission, "sect_name")).withStyle(ChatFormatting.RED)));
			}
			cy += MISSION_H;
		}
		return cy;
	}

	/** The report history rows from {@code y0} (the page puts a "Baca" button on each); returns how many fit. */
	public static int drawHistory(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int y1, int buttonW) {
		List<CompoundTag> history = history();
		if (history.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.sekte.misi.ui.riwayat_kosong"), x0 + 2.0F, y0 + 2.0F, x1 - x0 - 4.0F,
					Palette.INK_FAINT, 0.55F, false);
			return 0;
		}
		int shown = 0;
		int cy = y0;
		for (CompoundTag row : history) {
			if (cy + ROW_H - 1 > y1) {
				break;
			}
			String tpl = row.getStringOr("tpl", "");
			int grade = row.getIntOr("grade", 0);
			boolean bright = shown % 2 == 0;
			g.fill(x0, cy, x1, cy + ROW_H - 1, bright ? Palette.BAMBOO : Palette.BAMBOO_ALT);
			g.fill(x0, cy, x0 + 2, cy + ROW_H - 1, Palette.BAMBOO_KNOT);
			int gradeColor = grade == 3 ? 0xD4A017 : grade == 2 ? 0x2E8B57 : grade == 1 ? 0xB8860B : 0xA0352A;
			seal(g, font, x0 + 8.0F, cy + 5.5F, 4.4F, gradeColor, grade == 3 ? "極" : grade == 2 ? "成" : grade == 1 ? "半" : "敗", false);
			Component text = Component.translatable("kultivator.sekte.misi.ui.riwayat", row.getLongOr("day", 0L), Component.translatable(tplKey(tpl)),
					name(row, "leader"), Component.translatable("kultivator.sekte.misi.grade." + grade), row.getIntOr("recruits", 0),
					row.getIntOr("treasures", 0));
			Brush.fit(g, font, text, x0 + 15.0F, cy + 2.0F, x1 - x0 - 19.0F - buttonW, Palette.INK, 0.52F, 0.36F, false);
			cy += ROW_H;
			shown++;
		}
		return shown;
	}

	static String clock(long ticks) {
		long seconds = Math.max(0L, ticks / 20L);
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long rest = seconds % 60L;
		return hours > 0L ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest) : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
	}

	private MisiTab() {
	}
}
