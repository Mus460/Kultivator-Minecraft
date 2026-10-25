package dev.kultivator.client.alam;

import dev.kultivator.client.dunia.DuniaClient;
import dev.kultivator.client.dunia.reruntuhan.MenaraScreen;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dunia › Alam (DESIGN 25.10, 43.5, 43.12.2): sub <b>Alam Rahasia</b>, the four secret realms as seal cards in one scroll
 * (two columns on a wide box, one on a narrow one) with the realm's seal and name, "Siap" or the lock reason, the key
 * count (and the heavenvoid fragments), the clears or the best Makam layer, the cooldown, a reward line and a "Buka
 * Gerbang" talisman that needs the key; a line above names the lifetime crystals, Luka Alam and Mode Ringan. Sub
 * <b>Menara</b> is the Hundred-Floor Tower card ({@link MenaraScreen}). The page asks for its data while shown (43.0.12):
 * the Alam sync on show, the tower numbers when its sub shows. Text at 1.0 (43.0.5); a long reason is cut with "…" and
 * shown whole in the tooltip.
 */
public class AlamPage extends Page {

	public static final String SUB_ALAM = "alam_rahasia";
	public static final String SUB_MENARA = "menara";
	private static final String[] IDS = {"blood_fire_trial", "heavenvoid_hall", "ancient_god_land", "heavenly_tomb"};
	private static final int CARD_H = 64;
	private static final int CARD_MIN_W = 200;

	private final DaftarGulir realms = new DaftarGulir();
	private final MenaraScreen menara = new MenaraScreen();
	private String sub = SUB_ALAM;
	private boolean shown;

	public AlamPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.dunia.alam");
	}

	@Override
	public int glyph() {
		return Glyphs.JALAN;
	}

	@Override
	public String helpTopic() {
		return SUB_MENARA.equals(sub) ? "menara_seratus" : "alam_rahasia";
	}

	@Override
	public List<Sub> subs() {
		return List.of(new Sub(SUB_ALAM, Component.translatable("kultivator.gui43.sub.alam.alam_rahasia")),
				new Sub(SUB_MENARA, Component.translatable("kultivator.gui43.sub.alam.menara")));
	}

	@Override
	public void showSub(String id) {
		String next = SUB_MENARA.equals(id) ? SUB_MENARA : SUB_ALAM;
		if (!next.equals(sub)) {
			sub = next;
			realms.mulaiUlang(net.minecraft.util.Util.getMillis());
		}
		if (shown && SUB_MENARA.equals(sub)) {
			MenaraScreen.request();
		}
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		shown = true;
		send(Actions.ALAM, 0, "sync");
		if (SUB_MENARA.equals(sub)) {
			MenaraScreen.request();
		}
	}

	@Override
	public void onHide() {
		shown = false;
	}

	@Override
	protected void build() {
		// Immediate page: everything is drawn and hit-tested in lukis().
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		boolean tooBig = DuniaClient.tooBig(Payloads.DuniaSync.CH_ALAM) && SUB_ALAM.equals(sub);
		int bottom = tooBig ? y + h - 11 : y + h;
		if (SUB_MENARA.equals(sub)) {
			menara.gambar(l, x, y, x + w, bottom);
			return;
		}
		l.teks(notes(), x + 2, y + 1, w - 4, l.gaya.teksLemah);
		realms.gambarKisi(l, "alam", x, y + 13, x + w, bottom, IDS.length, CARD_MIN_W, CARD_H,
				(lk, i, x0, y0, x1, y1, sorot, pilih) -> card(lk, i, x0, y0, x1, y1, sorot), null, null);
		if (tooBig) {
			l.teks(Component.translatable("kultivator.sect_fix_feat.too_big"), x + 2, y + h - 9, w - 4, Palette.SEAL_DARK);
		}
	}

	/** Crystals · Luka Alam · Mode Ringan. */
	private static Component notes() {
		CompoundTag data = AlamClient.data();
		Component line = Component.translatable("kultivator.alam.page.crystals", data.getIntOr("crystals", 0), 94);
		long luka = data.getLongOr("luka", 0L);
		if (luka > 0L) {
			line = Component.translatable("kultivator.alam.page.sep", line,
					Component.translatable("kultivator.alam.page.luka", minutes(luka)).withColor(Palette.DANGER & 0xFFFFFF));
		}
		if (data.getBooleanOr("light", false)) {
			line = Component.translatable("kultivator.alam.page.sep", line,
					Component.translatable("kultivator.alam.page.light").withColor(Palette.JADE_DARK & 0xFFFFFF));
		}
		return line;
	}

	private void card(Lukis l, int index, int cx0, int cy0, int cx1, int cy1, boolean sorot) {
		String id = IDS[index];
		CompoundTag entry = realm(id);
		boolean ready = entry.getBooleanOr("ready", false);
		int color = Palette.opaque(RealmScenery.color(index + 1));
		l.kartu(cx0, cy0, cx1, cy1, sorot ? 1.0F : 0.0F, false);
		// The realm's seal: a square in its colour with the ordinal.
		int seal = 18;
		l.g.fill(cx0 + 4, cy0 + 4, cx0 + 4 + seal, cy0 + 4 + seal, Palette.argb(0xE0, color));
		Brush.frame(l.g, cx0 + 5, cy0 + 5, cx0 + 3 + seal, cy0 + 3 + seal, Palette.argb(0xB0, Palette.TALISMAN));
		Brush.centered(l.g, l.font, Component.literal(String.valueOf(index + 1)), cx0 + 4 + seal / 2.0F, cy0 + 9.0F, Palette.TALISMAN, 1.0F, false);
		int tx = cx0 + 8 + seal;
		int room = cx1 - tx - 4;
		l.teks(Brush.bold(Component.translatable("kultivator.alam.realm." + id)), tx, cy0 + 3, room, Palette.darken(color, 0.35F));
		Component status;
		if (entry.isEmpty()) {
			status = Component.translatable("kultivator.alam.page.loading");
		} else if (ready) {
			status = Component.translatable("kultivator.alam.status.ready");
		} else {
			Component why = why(entry);
			status = why == null ? Component.translatable("kultivator.alam.page.locked") : why;
		}
		l.teks(status, tx, cy0 + 14, room, ready ? Palette.JADE_DARK : Palette.SEAL_DARK);
		int lw = cx1 - cx0 - 8;
		Component keys = Component.translatable("kultivator.alam.page.keys", Component.translatable("kultivator.alam.page.key." + id),
				entry.getIntOr("have", 0), entry.getIntOr("need", 1));
		if ("heavenvoid_hall".equals(id)) {
			keys = Component.translatable("kultivator.alam.page.fragments", keys, entry.getIntOr("fragments", 0));
		}
		l.teks(keys, cx0 + 4, cy0 + 25, lw, l.gaya.teks);
		Component record = "heavenly_tomb".equals(id) ? Component.translatable("kultivator.alam.list.best", entry.getIntOr("best", 0))
				: Component.translatable("kultivator.alam.list.clears", entry.getIntOr("clears", 0));
		long cd = entry.getLongOr("cd", 0L);
		if (cd > 0L) {
			record = Component.translatable("kultivator.alam.page.sep", record, Component.translatable("kultivator.alam.page.cooldown", minutes(cd)));
		}
		l.teks(record, cx0 + 4, cy0 + 35, lw, l.gaya.teksLemah);
		Component open = Component.translatable("kultivator.alam.page.open");
		int bw = TalismanButton.lebar(l.font, open, 64, Math.max(64, (cx1 - cx0) / 2));
		int by = cy1 - 16;
		l.teks(Component.translatable("kultivator.alam.realm." + id + ".reward"), cx0 + 4, by + 3, cx1 - cx0 - bw - 14, l.gaya.teksSamar);
		Component why = ready ? null : why(entry);
		TalismanButton.gambar(l, "alam.buka." + index, cx1 - bw - 4, by, bw, 14, open, ready,
				why != null ? why : Component.translatable("kultivator.alam.page.locked"), () -> send(Actions.ALAM, 0, "open:" + id));
		if (ready && l.di(cx1 - bw - 4, by, cx1 - 4, by + 14)) {
			l.tip(Component.translatable("kultivator.alam.page.open_tip"));
		}
	}

	private static CompoundTag realm(String id) {
		for (Tag tag : AlamClient.data().getListOrEmpty("realms")) {
			if (tag instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				return entry;
			}
		}
		return new CompoundTag();
	}

	private static @Nullable Component why(CompoundTag entry) {
		return entry.read("why", ComponentSerialization.CODEC).orElse(null);
	}

	/** Ticks as {@code h:mm} (game minutes are real minutes here: 1200 ticks). */
	private static String minutes(long ticks) {
		long minutes = (ticks + 1199L) / 1200L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60L, minutes % 60L);
	}
}
