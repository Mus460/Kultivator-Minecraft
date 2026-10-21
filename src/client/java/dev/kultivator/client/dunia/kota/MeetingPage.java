package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * The meeting with a wandering cultivator (DESIGN 34.5.2): the wanderer's card (name, Dao, realm, temperament) and the
 * four choices. Bertukar Pengetahuan first takes an offering (a jade slip, a herb, a pill or 5 % of the bar), then the
 * Diskusi Dao: three rounds of Gerak / Diam / Selaras (Gerak beats Diam, Diam beats Selaras, Selaras beats Gerak) with a
 * hint from the wanderer's Dao telling which stance it leans to. The result line closes the discussion.
 */
final class MeetingPage extends KotaPage {

	static final String[] OFFERS = {"giok", "herba", "pil", "pengalaman"};
	static final String[] STANCES = {"gerak", "diam", "selaras"};
	static final String[] TEMPERS = {"ramah", "angkuh", "licik"};

	MeetingPage(DuniaScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.kota.tab.pertemuan");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.kota.tab.pertemuan.short");
	}

	@Override
	public int icon() {
		return Glyphs.PATH;
	}

	private static boolean discussing() {
		return all().getBooleanOr("discussing", false);
	}

	@Override
	protected void build() {
		int top = y + 52;
		int bw = Math.max(52, (w - 16) / 4);
		// row 1: the offerings (before a discussion) or the three stances (during one)
		for (int i = 0; i < OFFERS.length; i++) {
			int index = i;
			TalismanButton offer = add(new TalismanButton(x + 4 + i * (bw + 2), top, bw, 14, () -> Component.translatable("kultivator.kota.diskusi.beri." + OFFERS[index])));
			offer.enabledWhen(() -> !discussing() && !all().getBooleanOr("discussed", false) && all().getIntOr("disc_left", 0) > 0
					&& all().getBooleanOr("has_" + OFFERS[index], false));
			offer.onPress(() -> act("tukar:" + index));
			offer.tooltip(() -> lines(Component.translatable("kultivator.kota.diskusi.tip_beri." + OFFERS[index])));
		}
		int sw = Math.max(60, (w - 16) / 3);
		for (int i = 0; i < STANCES.length; i++) {
			int index = i;
			TalismanButton stance = add(new TalismanButton(x + 4 + i * (sw + 4), top + 46, sw, 16, () -> Component.translatable("kultivator.kota.diskusi.sikap." + STANCES[index])));
			stance.enabledWhen(MeetingPage::discussing);
			stance.urgentWhen(MeetingPage::discussing);
			stance.onPress(() -> act("diskusi:" + index));
			stance.tooltip(() -> lines(Component.translatable("kultivator.kota.diskusi.tip_sikap." + STANCES[index])));
		}
		int cw = Math.max(52, (w - 16) / 4);
		int cy = y + h - 18;
		TalismanButton spar = add(new TalismanButton(x + 4, cy, cw, 14, () -> Component.translatable("kultivator.kota.pertemuan.tanding")));
		spar.enabledWhen(() -> !all().getBooleanOr("sparred", false) && !discussing());
		spar.onPress(() -> act("tanding"));
		spar.tooltip(() -> lines(Component.translatable("kultivator.kota.pertemuan.tip_tanding")));
		TalismanButton dare = add(new TalismanButton(x + 6 + cw, cy, cw, 14, () -> Component.translatable("kultivator.kota.pertemuan.tantang")));
		dare.enabledWhen(() -> !discussing());
		dare.onPress(() -> {
			act("tantang");
			screen.onClose();
		});
		dare.tooltip(() -> lines(Component.translatable("kultivator.kota.pertemuan.tip_tantang")));
		TalismanButton invite = add(new TalismanButton(x + 8 + 2 * cw, cy, cw, 14, () -> Component.translatable("kultivator.kota.pertemuan.ajak")));
		invite.enabledWhen(() -> all().getBooleanOr("can_invite", false) && !discussing());
		invite.onPress(() -> {
			act("ajak");
			screen.onClose();
		});
		invite.tooltip(() -> lines(Component.translatable("kultivator.kota.pertemuan.tip_ajak")));
		TalismanButton bye = add(new TalismanButton(x + 10 + 3 * cw, cy, cw, 14, () -> Component.translatable("kultivator.kota.pertemuan.pamit")));
		bye.onPress(() -> {
			act("pamit");
			screen.onClose();
		});
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = all();
		Dao dao = Dao.byOrdinal(data.getIntOr("dao", -1));
		int daoColor = dao == null ? 0x8090A0 : dao.color();
		heading(g, Component.translatable("kultivator.kota.pertemuan.judul", read(data, "name")), x + 2.0F, y, w - 4.0F, 0.8F);
		// the wanderer's card
		int top = y + 13;
		panel(g, x + 2, top, x + w - 2, top + 34);
		Brush.disc(g, x + 16.0F, top + 17.0F, 12.0F, Palette.argb(0x60, daoColor));
		Brush.halo(g, x + 16.0F, top + 17.0F, 10.0F, 4.0F, daoColor, 0.5F);
		if (dao != null) {
			Glyphs.trigram(g, x + 16.0F, top + 17.0F, 12.0F, 1.6F, 3.0F, Glyphs.trigramOf(dao), Palette.argb(0xF0, Palette.INK));
		}
		Realm realm = Realm.byOrdinal(data.getIntOr("realm", 1));
		Component dline = Component.translatable("kultivator.kota.pertemuan.dao", dao == null ? Component.translatable("kultivator.kota.pertemuan.tanpa_dao")
				: dao.displayName(), realm.displayName(), data.getIntOr("stage", 0) + 1);
		Brush.fit(g, font, dline, x + 32.0F, top + 4.0F, w - 38.0F, Palette.INK, 0.7F, 0.42F, false);
		int temper = Math.max(0, Math.min(2, data.getIntOr("temper", 0)));
		Brush.fit(g, font, Component.translatable("kultivator.kota.pertemuan.watak", Component.translatable("kultivator.kota.watak." + temper),
				Component.translatable("kultivator.kota.watak.isi." + TEMPERS[temper])), x + 32.0F, top + 13.0F, w - 38.0F, Palette.INK_SOFT, 0.6F, 0.4F, false);
		Brush.fit(g, font, Component.translatable("kultivator.kota.pertemuan.diskusi_sisa", data.getIntOr("disc_left", 0)), x + 32.0F, top + 22.0F,
				w - 38.0F, Palette.GOLD_DARK, 0.6F, 0.4F, false);

		// the discussion
		int dy = y + 68;
		if (discussing()) {
			int round = data.getIntOr("round", 0);
			String daoId = dao == null ? "sword" : dao.id();
			int lean = Math.max(0, Math.min(2, data.getIntOr("lean", 0)));
			Component hint = Component.translatable("kultivator.kota.diskusi." + daoId + "." + (lean * 2 + data.getIntOr("hint", 0)));
			Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kota.diskusi.ronde", round + 1, 3, data.getIntOr("wins", 0))), x + 4.0F, dy,
					w - 8.0F, Palette.SEAL_DARK, 0.7F, 0.45F, false);
			Brush.wrap(g, font, Component.literal("“").append(hint).append("”"), x + 8.0F, dy + 9.0F, w - 16.0F, Palette.INK, 0.62F, false);
		} else if (data.getBooleanOr("discussed", false)) {
			String reward = data.getStringOr("reward", "lore");
			Component result = switch (reward) {
				case "paham" -> Component.translatable("kultivator.kota.diskusi.hasil.paham", String.format(java.util.Locale.ROOT, "%.1f",
						data.getDoubleOr("reward_n", 0.0D)), dao == null ? Component.empty() : dao.displayName());
				case "catatan" -> Component.translatable("kultivator.kota.diskusi.hasil.catatan");
				default -> Component.translatable("kultivator.kota.diskusi.hasil.lore", Component.translatable("kultivator.kota.diskusi.lore."
						+ data.getIntOr("lore", 0)));
			};
			panel(g, x + 2, dy - 2, x + w - 2, dy + 34);
			Brush.wrap(g, font, result, x + 8.0F, dy + 2.0F, w - 16.0F, reward.equals("lore") ? Palette.INK_SOFT : Palette.JADE_DARK, 0.64F, false);
		} else {
			Brush.wrap(g, font, Component.translatable("kultivator.kota.diskusi.ajak"), x + 4.0F, y + 52.0F + 17.0F, w - 8.0F, Palette.INK_SOFT, 0.62F, false);
		}
		int last = data.getIntOr("last", 9);
		if (last != 9 && (discussing() || data.getBooleanOr("discussed", false))) {
			Component lastLine = Component.translatable("kultivator.kota.diskusi.terakhir." + (last > 0 ? "menang" : last == 0 ? "seri" : "kalah"),
					Component.translatable("kultivator.kota.diskusi.sikap." + STANCES[Math.max(0, Math.min(2, data.getIntOr("last_theirs", 0)))]));
			Brush.fit(g, font, lastLine, x + 4.0F, y + h - 30.0F, w - 8.0F, last > 0 ? Palette.JADE_DARK : last == 0 ? Palette.INK_SOFT : Palette.SEAL_DARK,
					0.62F, 0.4F, false);
		}
	}

	@Override
	protected boolean visible(dev.kultivator.client.screen.ui.Widget widget) {
		int index = widgets.indexOf(widget);
		boolean disc = discussing();
		if (index >= 0 && index < OFFERS.length) {
			return !disc && !all().getBooleanOr("discussed", false);
		}
		if (index >= OFFERS.length && index < OFFERS.length + STANCES.length) {
			return disc;
		}
		return true;
	}
}
