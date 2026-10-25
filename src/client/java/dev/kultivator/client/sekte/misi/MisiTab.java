package dev.kultivator.client.sekte.misi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KartuHasil;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pictures of module X on Sekte › Kegiatan › Misi ({@code client.sectevent.MissionPage}, DESIGN 36.2.7, 43.12.2): the
 * story board rows (the template's seal, tier, days and the three stage beads in their kind's colour), the chance
 * preview, the running missions (team, three stage beads that fill in as the news arrives, a time cord), the report
 * history rows, and the report itself as the kit's {@link KartuHasil} parts (was {@code MisiLaporanScreen}). Pure drawing
 * from {@link MisiClient#state()} at text 1.0 (43.0.5); the page owns the lists, buttons and the roster.
 */
public final class MisiTab {

	public static final int CARD_H = 24;
	public static final int MISSION_H = 32;
	public static final int ROW_H = 16;
	public static final String[] STANCES = {"damai", "tegas", "licik"};
	private static final String[] KINDS = {"tempur", "licik", "damai", "jelajah"};
	private static final int[] KIND_COLORS = {0xC0392B, 0x8E44AD, 0x2E86C1, 0x27AE60};
	private static final String[] KIND_GLYPHS = {"戰", "謀", "和", "行"};
	private static final String[] GRADE_GLYPH = {"敗", "半", "成", "極"};
	private static final int[] GRADE_COLOR = {0xA0352A, 0xB8860B, 0x2E8B57, 0xD4A017};

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

	/** False while module X is off on the server. */
	public static boolean on() {
		return MisiClient.state().getBooleanOr("on", true);
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

	static String clock(long ticks) {
		long seconds = Math.max(0L, ticks / 20L);
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long rest = seconds % 60L;
		return hours > 0L ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest) : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
	}

	// ================================================================== small pictures

	/** A round seal in {@code rgb} with a glyph at 1.0 ({@code r} ≥ 7 holds the glyph; smaller seals are plain). */
	static void seal(Lukis l, float cx, float cy, float r, int rgb, String glyph, boolean dim) {
		Brush.disc(l.g, cx, cy, r + 0.8F, Palette.argb(0xA0, 0x2A1A10));
		Brush.disc(l.g, cx, cy, r, dim ? Palette.argb(0x70, rgb) : Palette.argb(0xFF, rgb));
		Brush.ring(l.g, cx, cy, r - 1.4F, r - 0.8F, Palette.argb(0x90, 0xFFF3D9));
		if (r >= 7.0F) {
			Brush.centered(l.g, l.font, Component.literal(glyph), cx, cy - 4.0F, 0xFFFFF6E6, 1.0F, false);
		}
	}

	/** One stage bead in its kind's colour: {@code state} 0 pending, 1 succeeded, 2 failed; the kind is its tooltip. */
	static void bead(Lukis l, float cx, float cy, float r, String kind, int state, boolean ambush) {
		int color = KIND_COLORS[kindIndex(kind)];
		if (state == 0) {
			Brush.disc(l.g, cx, cy, r, Palette.argb(0x50, color));
			Brush.ring(l.g, cx, cy, r - 0.7F, r, Palette.argb(0xC0, color));
		} else {
			Brush.disc(l.g, cx, cy, r, state == 1 ? Palette.argb(0xFF, color) : Palette.argb(0xFF, 0x5A3A30));
			Brush.ring(l.g, cx, cy, r - 0.8F, r, state == 1 ? Palette.argb(0xFF, Palette.GOLD) : Palette.argb(0xFF, 0xC0392B));
		}
		if (ambush) {
			Brush.ring(l.g, cx, cy, r + 0.6F, r + 1.4F, Palette.argb(0xE0, 0xE04030));
		}
		if (r >= 6.0F) {
			Brush.centered(l.g, l.font, Component.literal(KIND_GLYPHS[kindIndex(kind)]), cx, cy - 4.0F, state == 0 ? Palette.argb(0xD0, color) : 0xFFFFF6E6,
					1.0F, false);
		}
		if (state == 2) {
			Brush.line(l.g, cx - r * 0.7F, cy - r * 0.7F, cx + r * 0.7F, cy + r * 0.7F, 1.0F, Palette.argb(0xC0, 0xFF6050));
		}
	}

	private static Component kindName(String kind) {
		return Component.translatable("kultivator.sekte.misi.jenis." + kind).withColor(KIND_COLORS[kindIndex(kind)]);
	}

	// ================================================================== the story board

	/** One offer of the story board as a list row ({@link #CARD_H} high); its details are the row's tooltip. */
	public static void offerRow(Lukis l, CompoundTag offer, int x0, int y0, int x1, int y1, boolean over, boolean chosen) {
		String tpl = offer.getStringOr("tpl", "");
		boolean taken = offer.getBooleanOr("taken", false);
		l.kartu(x0, y0, x1, y1, over && !taken ? 1.0F : 0.0F, chosen && !taken);
		l.g.fill(x0, y0, x0 + 2, y1, Palette.argb(taken ? 0x50 : 0xE0, colorOf(tpl)));
		seal(l, x0 + 11.0F, (y0 + y1) / 2.0F, 8.0F, colorOf(tpl), glyphOf(tpl), taken);
		String[] kinds = kindsOf(tpl);
		int beads = x1 - 3 * 10 - 2;
		for (int k = 0; k < 3; k++) {
			bead(l, beads + 5.0F + k * 10.0F, y0 + 6.0F, 4.0F, kinds[k], 0, false);
		}
		Component title = Brush.bold(Component.translatable(tplKey(tpl)));
		int tx = x0 + 22;
		l.teks(title, tx, y0 + 2, beads - tx - 2, taken ? Palette.INK_FAINT : l.gaya.teks);
		MutableComponent detail = Component.translatable("kultivator.sekte.misi.ui.kartu", offer.getIntOr("tier", 1), offer.getIntOr("days", 1));
		Component who = extra(offer);
		if (who != null) {
			detail = detail.append(Component.literal(" · ")).append(who);
		}
		if (offer.getIntOr("price", 0) > 0) {
			detail = detail.append(Component.literal(" · ")).append(Component.translatable("kultivator.sekte.misi.ui.harga", offer.getIntOr("price", 0)));
		}
		l.teks(taken ? Component.translatable("kultivator.sekte.misi.ui.diambil") : detail, tx, y0 + 12, x1 - tx - 3, l.gaya.teksLemah);
		if (over && !taken) {
			List<Component> lines = new ArrayList<>();
			lines.add(title);
			lines.add(Component.translatable(tplKey(tpl) + ".desc"));
			MutableComponent stages = Component.translatable("kultivator.sekte.misi.ui.tahap");
			for (int k = 0; k < 3; k++) {
				stages = stages.append(Component.literal(k == 0 ? " " : " → ")).append(kindName(kinds[k]));
			}
			lines.add(stages);
			lines.add(Component.translatable("kultivator.sekte.misi.ui.tip_sikap").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("kultivator.sekte.misi.sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			l.tip(lines);
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

	/** The preview line under the board: the three stage chances and the chance of a legendary end (breakdown as tooltip). */
	public static void preview(Lukis l, int x0, int y0, int width, int offer, String stance, String souls, boolean anyChosen) {
		Component line;
		CompoundTag preview = MisiClient.state().getCompoundOrEmpty("pratinjau");
		boolean fresh = preview.getIntOr("offer", -1) == offer && stance.equals(preview.getStringOr("sikap", ""))
				&& souls.equals(preview.getStringOr("souls", ""));
		int color = l.gaya.teksLemah;
		if (full()) {
			line = Component.translatable("kultivator.sekte.misi.ui.penuh", MisiClient.state().getIntOr("maks", 2));
			color = Palette.DANGER;
		} else if (offer < 0) {
			line = Component.translatable("kultivator.sekte.misi.ui.pilih_misi");
		} else if (!anyChosen) {
			line = Component.translatable("kultivator.sekte.misi.ui.pilih_tim");
		} else if (!fresh) {
			line = Component.translatable("kultivator.sekte.misi.ui.menghitung");
		} else if (!preview.getStringOr("blocker", "").isEmpty()) {
			line = Component.translatable(preview.getStringOr("blocker", ""), name(preview, "blocker_name"));
			color = Palette.DANGER;
		} else if (!preview.getBooleanOr("rank_ok", true)) {
			line = Component.translatable("kultivator.sekte.misi.ui.jenjang_kurang");
			color = Palette.DANGER;
		} else {
			line = Component.translatable("kultivator.sekte.misi.ui.peluang", percent(preview.getCompoundOrEmpty("s0").getDoubleOr("total", 0.0D)),
					percent(preview.getCompoundOrEmpty("s1").getDoubleOr("total", 0.0D)), percent(preview.getCompoundOrEmpty("s2").getDoubleOr("total", 0.0D)),
					percent(preview.getDoubleOr("legend", 0.0D)));
			color = Palette.JADE_DARK;
		}
		l.teks(line, x0, y0, width, color);
		if (fresh && l.di(x0, y0 - 1, x0 + width, y0 + 9)) {
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
			l.tip(lines);
		}
	}

	private static String signed(double value) {
		long p = Math.round(value * 100.0D);
		return (p >= 0 ? "+" : "") + p;
	}

	// ================================================================== the running missions

	/** One running mission as a list row ({@link #MISSION_H} high), text up to {@code textX1} (the buttons sit right of it). */
	public static void missionRow(Lukis l, CompoundTag mission, int x0, int y0, int x1, int textX1) {
		long now = now();
		String tpl = mission.getStringOr("tpl", "");
		l.kartu(x0, y0, x1, y1(y0), 0.0F, false);
		l.g.fill(x0, y0, x0 + 2, y1(y0), Palette.argb(0xE0, colorOf(tpl)));
		seal(l, x0 + 11.0F, y0 + 10.0F, 8.0F, colorOf(tpl), glyphOf(tpl), false);
		int tx = x0 + 22;
		Component title = Brush.bold(Component.translatable("kultivator.sekte.misi.ui.berjalan", Component.translatable(tplKey(tpl)),
				mission.getIntOr("tier", 1), Component.translatable("kultivator.sekte.misi.sikap." + mission.getStringOr("sikap", "damai"))));
		l.teks(title, tx, y0 + 2, textX1 - tx, l.gaya.teks);
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
		l.teks(team, tx, y0 + 12, textX1 - tx, l.gaya.teksLemah);
		// three stage beads, the time cord and the time left
		ListTag stages = mission.getListOrEmpty("stages");
		String[] kinds = kindsOf(tpl);
		int by = y0 + 26;
		for (int k = 0; k < 3; k++) {
			CompoundTag stage = k < stages.size() && stages.get(k) instanceof CompoundTag t ? t : null;
			int state = stage == null || stage.isEmpty() ? 0 : stage.getBooleanOr("ok", false) ? 1 : 2;
			String kind = stage == null || stage.isEmpty() ? kinds[k] : stage.getStringOr("kind", kinds[k]);
			float bx = x0 + 6.0F + k * 10.0F;
			bead(l, bx, by, 4.0F, kind, state, stage != null && stage.getBooleanOr("amb", false));
			if (l.di(Math.round(bx - 5), by - 5, Math.round(bx + 5), by + 5)) {
				l.tip(Component.translatable("kultivator.sekte.misi.ui.bead", k + 1, kindName(kind), Component.translatable(state == 0
						? "kultivator.sekte.misi.ui.bead.tunggu" : state == 1 ? "kultivator.sekte.misi.ui.bead.ok" : "kultivator.sekte.misi.ui.bead.gagal")));
			}
		}
		long start = mission.getLongOr("start", now);
		long end = mission.getLongOr("end", now);
		Component when;
		if (mission.getBooleanOr("finished", false)) {
			when = Component.translatable("kultivator.sekte.misi.ui.pulang_jalan");
		} else if (mission.getBooleanOr("ditarik", false)) {
			when = Component.translatable("kultivator.sekte.misi.ui.ditarik", clock(end - now));
		} else {
			when = Component.translatable("kultivator.sekte.misi.ui.sisa", String.format(Locale.ROOT, "%.1f", Math.max(0L, end - now) / 24000.0D),
					clock(end - now));
		}
		boolean amb = mission.getBooleanOr("amb", false);
		int cordX0 = x0 + 40;
		int whenW = Math.min(textX1 - cordX0 - 30, l.font.width(when));
		int cordX1 = textX1 - whenW - 6;
		if (cordX1 > cordX0 + 10) {
			float t = end <= start ? 1.0F : Mth.clamp((now - start) / (float) (end - start), 0.0F, 1.0F);
			Brush.rect(l.g, cordX0, by - 0.6F, cordX1, by + 0.6F, Palette.argb(0x60, Palette.INK_FAINT));
			Brush.rect(l.g, cordX0, by - 0.6F, cordX0 + (cordX1 - cordX0) * t, by + 0.6F, Palette.argb(0xE0, Palette.SEAL));
			Brush.disc(l.g, cordX0 + (cordX1 - cordX0) * t, by, 1.6F, Palette.GOLD);
		}
		l.teks(when, textX1 - whenW, by - 4, whenW, amb ? Palette.DANGER : Palette.JADE_DARK);
		if (amb && l.di(x0, y0, textX1, y1(y0))) {
			l.tip(Component.translatable("kultivator.sekte.misi.ui.sergap", name(mission, "sect_name")).withStyle(ChatFormatting.RED));
		}
	}

	private static int y1(int y0) {
		return y0 + MISSION_H - 2;
	}

	/** One finished report as a bamboo row ({@link #ROW_H} high), text up to {@code textX1} (the "Baca" button sits right). */
	public static void historyRow(Lukis l, CompoundTag row, int index, int x0, int y0, int x1, int y1, int textX1) {
		String tpl = row.getStringOr("tpl", "");
		int grade = Mth.clamp(row.getIntOr("grade", 0), 0, 3);
		l.g.fill(x0, y0, x1, y1, index % 2 == 0 ? Palette.BAMBOO : Palette.BAMBOO_ALT);
		l.g.fill(x0, y0, x0 + 2, y1, Palette.BAMBOO_KNOT);
		seal(l, x0 + 9.0F, (y0 + y1) / 2.0F, 7.0F, GRADE_COLOR[grade], GRADE_GLYPH[grade], false);
		Component text = Component.translatable("kultivator.sekte.misi.ui.riwayat", row.getLongOr("day", 0L), Component.translatable(tplKey(tpl)),
				name(row, "leader"), Component.translatable("kultivator.sekte.misi.grade." + grade), row.getIntOr("recruits", 0), row.getIntOr("treasures", 0));
		l.teks(text, x0 + 19, (y0 + y1) / 2.0F - 4.0F, textX1 - x0 - 21, Palette.INK);
	}

	// ================================================================== the report (was MisiLaporanScreen)

	/** The report's title line ("Kawal Kafilah · Tingkat 3"). */
	public static Component reportTitle(CompoundTag report) {
		return Component.translatable("kultivator.sekte.misi.lapor.judul", Component.translatable(tplKey(report.getStringOr("tpl", ""))),
				report.getIntOr("tier", 1));
	}

	/** Day and stance. */
	public static Component reportSub(CompoundTag report) {
		return Component.translatable("kultivator.sekte.misi.lapor.sub", report.getLongOr("day", 0L),
				Component.translatable("kultivator.sekte.misi.sikap." + report.getStringOr("sikap", "damai")));
	}

	/** The grade stamp (極 legendary, 成 success, 半 partial, 敗 failure) in the report's own words. */
	public static KartuHasil.Nilai reportGrade(CompoundTag report) {
		int grade = Mth.clamp(report.getIntOr("grade", 0), 0, 3);
		return new KartuHasil.Nilai(GRADE_GLYPH[grade], Component.translatable("kultivator.sekte.misi.grade." + grade), GRADE_COLOR[grade]);
	}

	/** The team line, the leader first. */
	public static Component reportTeam(CompoundTag report) {
		List<CompoundTag> team = compounds(report.getListOrEmpty("team"));
		MutableComponent names = Component.translatable("kultivator.sekte.misi.lapor.tim");
		for (int i = 0; i < team.size(); i++) {
			CompoundTag member = team.get(i);
			names = names.append(Component.literal(i == 0 ? " " : ", "));
			MutableComponent name = name(member, "name").copy().withColor(Palette.onPaper(member.getIntOr("color", 0x888888)));
			if (i == 0) {
				name = name.append(Component.translatable("kultivator.sekte.misi.lapor.pemimpin").withColor(Palette.INK_FAINT));
			}
			if (member.getBooleanOr("left", false)) {
				name = name.append(Component.translatable("kultivator.sekte.misi.lapor.ditinggal_tanda").withColor(Palette.DANGER));
			} else if (member.getBooleanOr("hurt", false)) {
				name = name.append(Component.literal(" ✚").withColor(0xFFC0392B));
			}
			names = names.append(name);
		}
		return names;
	}

	/**
	 * The report's body: the departure in the chosen stance, the three stages each in its {@code ok} or {@code gagal}
	 * branch with a bead in the stage kind's colour (an ambush or a wound where it happened), the found disciple, the
	 * recall, the ending; then what came home and the quoted memories. Every text is a lang key resolved here.
	 */
	public static List<KartuHasil.Bagian> reportBody(CompoundTag report) {
		List<KartuHasil.Bagian> out = new ArrayList<>();
		String tpl = report.getStringOr("tpl", "");
		int grade = Mth.clamp(report.getIntOr("grade", 0), 0, 3);
		List<CompoundTag> team = compounds(report.getListOrEmpty("team"));
		Component leader = team.isEmpty() ? Component.literal("?") : name(team.get(0), "name");
		Component companion = team.size() > 1 ? name(team.get(1), "name") : leader;
		Component place = Component.translatable("kultivator.sekte.misi.tempat." + report.getIntOr("place", 0));
		Component foe = report.contains("sect_name") ? name(report, "sect_name")
				: "samar".equals(tpl) ? Component.translatable("kultivator.sekte.misi.sekte_umum")
				: Component.translatable("kultivator.sekte.misi.lawan." + report.getIntOr("foe", 0));
		Component extra = extra(report, foe);
		out.add(KartuHasil.Bagian.of(Component.translatable("kultivator.sekte.misi.buka." + report.getStringOr("sikap", "damai"), leader, place,
				team.size())));
		ListTag stages = report.getListOrEmpty("stages");
		String[] kinds = kindsOf(tpl);
		for (int i = 0; i < 3; i++) {
			CompoundTag stage = i < stages.size() && stages.get(i) instanceof CompoundTag t && !t.isEmpty() ? t : null;
			if (stage == null) {
				out.add(new KartuHasil.Bagian(null, Component.translatable("kultivator.sekte.misi.lapor.batal", i + 1).withStyle(ChatFormatting.ITALIC),
						KartuHasil.BIASA, KIND_COLORS[kindIndex(kinds[i])]));
				continue;
			}
			boolean ok = stage.getBooleanOr("ok", false);
			MutableComponent text = Component.empty();
			if (stage.getBooleanOr("amb", false)) {
				text.append(Component.translatable("kultivator.sekte.misi.lapor.sergap", foe).withColor(Palette.DANGER)).append(Component.literal(" "));
			}
			text.append(Component.translatable(tplKey(tpl) + ".s" + (i + 1) + (ok ? ".ok" : ".gagal"), leader, companion, place, foe, extra));
			String hurt = stage.getStringOr("hurt", "");
			if (!hurt.isEmpty()) {
				for (CompoundTag member : team) {
					if (hurt.equals(member.getStringOr("soul", ""))) {
						text.append(Component.literal(" ")).append(Component.translatable("kultivator.sekte.misi.lapor.luka", name(member, "name"))
								.withColor(Palette.DANGER));
					}
				}
			}
			String kind = stage.getStringOr("kind", kinds[i]);
			out.add(new KartuHasil.Bagian(null, text, ok ? KartuHasil.BERHASIL : KartuHasil.GAGAL, ok ? KIND_COLORS[kindIndex(kind)] : 0));
		}
		if ("cari".equals(tpl)) {
			Component target = report.contains("target_name") ? name(report, "target_name") : Component.literal("?");
			out.add(KartuHasil.Bagian.of(Component.translatable(report.getBooleanOr("found", false) ? "kultivator.sekte.misi.temu.ok"
					: "kultivator.sekte.misi.temu.gagal", target, leader)));
		}
		if (report.getBooleanOr("ditarik", false)) {
			out.add(KartuHasil.Bagian.of(Component.translatable("kultivator.sekte.misi.lapor.ditarik").withStyle(ChatFormatting.ITALIC)));
		}
		out.add(KartuHasil.Bagian.of(Component.translatable("kultivator.sekte.misi.tutup." + grade, leader)));
		// what came home
		List<Component> results = results(report, team);
		for (int i = 0; i < results.size(); i++) {
			Component line = Component.literal("• ").append(results.get(i));
			out.add(i == 0 ? KartuHasil.Bagian.judul(Component.translatable("kultivator.sekte.misi.lapor.hasil"), line) : KartuHasil.Bagian.of(line));
		}
		// quoted memories
		for (CompoundTag quote : compounds(report.getListOrEmpty("quotes"))) {
			Component said = Component.translatable(quote.getStringOr("text", ""), quote.getStringOr("arg", ""));
			out.add(new KartuHasil.Bagian(null, Component.translatable("kultivator.sekte.misi.lapor.kutip", name(quote, "name"), said)
					.withStyle(ChatFormatting.ITALIC), KartuHasil.BIASA, Palette.JADE_DARK));
		}
		out.add(KartuHasil.Bagian.of(Component.translatable("kultivator.sekte.misi.sumber").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY)));
		return out;
	}

	private static Component extra(CompoundTag report, Component foe) {
		if (report.contains("target_name")) {
			return name(report, "target_name");
		}
		String village = report.getStringOr("village", "");
		if (!village.isEmpty()) {
			return Component.literal(village);
		}
		if ("desa".equals(report.getStringOr("tpl", ""))) {
			return Component.translatable("kultivator.sekte.misi.desa_umum");
		}
		return foe;
	}

	private static List<Component> results(CompoundTag report, List<CompoundTag> team) {
		List<Component> lines = new ArrayList<>();
		int recruits = report.getIntOr("recruits", 0);
		if (recruits > 0) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.calon", recruits).withColor(Palette.JADE_DARK));
		}
		for (CompoundTag item : compounds(report.getListOrEmpty("items"))) {
			lines.add(name(item, "text"));
		}
		if (report.getIntOr("more", 0) > 0) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.lagi", report.getIntOr("more", 0)));
		}
		if (report.contains("map")) {
			CompoundTag map = report.getCompoundOrEmpty("map");
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.peta", map.getIntOr("x", 0), map.getIntOr("z", 0)).withColor(0xFF8E44AD));
		}
		if (report.getBooleanOr("found", false)) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.ditemukan",
					report.contains("target_name") ? name(report, "target_name") : Component.literal("?")).withColor(Palette.JADE_DARK));
		}
		int standing = report.getIntOr("standing", 0);
		if (standing != 0 && report.contains("sect_name")) {
			lines.add(Component.translatable(standing > 0 ? "kultivator.sekte.misi.standing.naik" : "kultivator.sekte.misi.standing.turun",
					name(report, "sect_name"), Math.abs(standing)));
		}
		if (report.getIntOr("refund", 0) > 0) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.refund", report.getIntOr("refund", 0)));
		}
		for (CompoundTag member : team) {
			if (member.getBooleanOr("left", false)) {
				lines.add(Component.translatable("kultivator.sekte.misi.lapor.ditinggal", name(member, "name")).withColor(Palette.DANGER));
			} else if (member.getBooleanOr("hurt", false)) {
				lines.add(Component.translatable("kultivator.sekte.misi.lapor.terluka", name(member, "name")).withColor(Palette.DANGER));
			}
		}
		if (lines.isEmpty()) {
			lines.add(Component.translatable("kultivator.sekte.misi.lapor.kosong"));
		}
		return lines;
	}

	private MisiTab() {
	}
}
