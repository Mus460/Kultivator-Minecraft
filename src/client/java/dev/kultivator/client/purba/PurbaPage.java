package dev.kultivator.client.purba;

import dev.kultivator.client.Keys;
import dev.kultivator.client.abadi.AbadiClient;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.GulirTeks;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Keabadian › Purba (DESIGN 26.10, 43.5; owned by P9-purba, one page with two subs since 43 M16). Sub {@code binatang}: three
 * beast seals (Kun-Peng, Zhulong, Taotie) with their kills, the first kill and the unique reward (the Kunpeng bone pieces
 * held, the candle eye), then the natural cooldown, today's repeat rewards (≤ 3), the Gejolak chance, the scroll recipe, a
 * live fight and Luka Purba (read-only). Sub {@code bencana}: the star calamities ({@link BencanaPage}). The beasts open
 * after the ascent, the calamities at the True Immortal Peak (the locks of the old hub cards). Text at 1.0 (43.0.5); the
 * data comes from {@link PurbaClient}, asked for while shown (43.0.12).
 */
public class PurbaPage extends Page {

	public static final String SUB_BINATANG = "binatang";
	public static final String SUB_BENCANA = "bencana";
	private static final String[] KINDS = {"kunpeng", "zhulong", "taotie"};
	private static final int[] COLORS = {0x3A7BD5, 0xFF5A2A, 0xC08A3A};
	private static final int GAP = 4;
	private static final int CARD_H = 70;

	private String sub = SUB_BINATANG;
	private final BencanaPage bencana = new BencanaPage(this);
	private final GulirTeks baris = new GulirTeks();

	public PurbaPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.keabadian.purba");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.purba.short");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return SUB_BENCANA.equals(sub) ? "bencana_bintang" : "binatang_purba";
	}

	/** The page opens with the calamities (Peak); the beasts need the ascent. */
	@Override
	public @Nullable Component kunci() {
		return AbadiClient.kunciPuncak();
	}

	@Override
	public List<Sub> subs() {
		return List.of(new Sub(SUB_BINATANG, Component.translatable("kultivator.gui43.sub.purba.binatang"), AbadiClient.kunciNaik()),
				new Sub(SUB_BENCANA, Component.translatable("kultivator.gui43.sub.purba.bencana")));
	}

	@Override
	public void showSub(String id) {
		String next = SUB_BENCANA.equals(id) ? SUB_BENCANA : SUB_BINATANG;
		if (!next.equals(sub)) {
			sub = next;
			if (w > 0) {
				layout(x, y, w, h);
			}
		}
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		Keys.send(new Payloads.Action(Actions.PURBA, 0, "sync"));
	}

	@Override
	protected void build() {
		if (SUB_BENCANA.equals(sub)) {
			bencana.build();
		}
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (SUB_BENCANA.equals(sub)) {
			bencana.drawBehind(g, seconds);
		}
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag data = PurbaClient.data();
		if (data.isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable("kultivator.purba.ui.loading"), null, null, x, y, x + w, y + h);
			return;
		}
		if (SUB_BENCANA.equals(sub)) {
			bencana.lukis(l, data);
			return;
		}
		if (!data.getBooleanOr("beasts", true)) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.purba.disabled"), null, null, x, y, x + w, y + h);
			return;
		}
		int cardH = Math.min(CARD_H, Math.max(46, h / 2));
		CompoundTag kills = data.getCompoundOrEmpty("kills");
		for (int i = 0; i < 3; i++) {
			drawSeal(l, i, kills.getIntOr(KINDS[i], 0), data, cardH);
		}
		AbadiClient.mulai(baris, w, Payloads.DuniaSync.CH_PURBA);
		long cd = data.getLongOr("cd", 0L);
		baris.tambah(cd <= 0L ? Component.translatable("kultivator.purba.ui.cd_ready") : Component.translatable("kultivator.purba.ui.cd", minutes(cd)),
				Palette.INK);
		baris.tambah(Component.translatable("kultivator.purba.ui.chance", Math.round(data.getDoubleOr("chance", 0.08D) * 1000.0D) / 10.0D), Palette.INK);
		int repeats = data.getIntOr("rep_n", 0);
		int cap = data.getIntOr("rep_cap", 3);
		baris.tambah(Component.translatable("kultivator.purba.ui.repeats", repeats, cap), repeats >= cap ? Palette.SEAL_DARK : Palette.INK_SOFT);
		baris.tambah(Component.translatable("kultivator.purba.ui.recipe"), Palette.INK_SOFT);
		if (!data.getBooleanOr("realm", false)) {
			baris.tambah(Component.translatable("kultivator.purba.ui.not_realm"), Palette.INK_FAINT);
		}
		CompoundTag fight = data.getCompoundOrEmpty("fight");
		if (!fight.isEmpty()) {
			int kind = Math.max(0, Math.min(2, fight.getIntOr("kind", 0)));
			Component kindName = Component.translatable("kultivator.purba.kind." + KINDS[kind]);
			baris.tambah(fight.getBooleanOr("active", false)
					? Component.translatable("kultivator.purba.ui.fight", kindName, Component.translatable("kultivator.purba.phase." + KINDS[kind] + "."
					+ Math.max(0, Math.min(2, fight.getIntOr("phase", 0)))))
					: Component.translatable("kultivator.purba.ui.omen", kindName), Palette.DANGER);
		}
		long luka = data.getLongOr("luka", 0L);
		if (luka > 0L) {
			baris.tambah(Component.translatable("kultivator.purba.ui.luka", minutes(luka)), Palette.SEAL_DARK);
		}
		baris.jarak();
		baris.tambah(Component.translatable("kultivator.purba.ui.source"), Palette.INK_FAINT);
		baris.gambar(l, "purba", x, y + cardH + 4, x + w, y + h);
	}

	/** One beast seal card: the coloured seal, the name, kills, first kill and its unique reward (cut lines show in full on hover). */
	private void drawSeal(Lukis l, int index, int kills, CompoundTag data, int ch) {
		GuiGraphicsExtractor g = l.g;
		int cw = (w - 2 * GAP) / 3;
		int cx = x + index * (cw + GAP);
		int color = Palette.opaque(COLORS[index]);
		l.kartu(cx, y, cx + cw, y + ch, 0.0F, false);
		float sx = cx + cw / 2.0F;
		float sy = y + 12.0F;
		Brush.disc(g, sx, sy, 9.0F, Palette.argb(kills > 0 ? 0xE0 : 0x70, color));
		Brush.ring(g, sx, sy, 9.0F, 10.0F, Palette.argb(0xC0, Palette.GOLD));
		Brush.centered(g, font, Component.translatable("kultivator.purba.ui.glyph." + KINDS[index]), sx, sy - 4.0F, Palette.TALISMAN, 1.0F, false);
		float ly = y + 24.0F;
		float lw = cw - 6.0F;
		Brush.fitCentered(g, font, Brush.bold(Component.translatable("kultivator.purba.kind." + KINDS[index])), sx, ly, lw, Palette.darken(color, 0.35F),
				1.0F, 1.0F, false);
		ly += 10.0F;
		Brush.fitCentered(g, font, Component.translatable("kultivator.purba.ui.kills", kills), sx, ly, lw, Palette.INK, 1.0F, 1.0F, false);
		ly += 10.0F;
		if (ly + 9.0F <= y + ch) {
			Brush.fitCentered(g, font, Component.translatable(kills > 0 ? "kultivator.purba.ui.first_done" : "kultivator.purba.ui.first_open"), sx, ly, lw,
					kills > 0 ? Palette.JADE_DARK : Palette.INK_SOFT, 1.0F, 1.0F, false);
			ly += 10.0F;
		}
		Component unique = switch (index) {
			case 0 -> data.getBooleanOr("art", false) ? Component.translatable("kultivator.purba.ui.art_known")
					: Component.translatable("kultivator.purba.ui.pieces", data.getIntOr("pieces", 0));
			case 1 -> Component.translatable(data.getBooleanOr("eye", false) ? "kultivator.purba.ui.eye_owned" : "kultivator.purba.ui.eye_missing");
			default -> Component.translatable("kultivator.purba.ui.no_unique");
		};
		if (ly + 9.0F <= y + ch) {
			Brush.fitCentered(g, font, unique, sx, ly, lw, Palette.INK_SOFT, 1.0F, 1.0F, false);
		}
	}

	/** Ticks as whole minutes. */
	static String minutes(long ticks) {
		long minutes = (ticks + 1199L) / 1200L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60L, minutes % 60L);
	}

	// for the Bencana part (same package)

	int bx() {
		return x;
	}

	int by() {
		return y;
	}

	int bw() {
		return w;
	}

	int bh() {
		return h;
	}

	<T extends dev.kultivator.client.screen.ui.Widget> T tambah(T widget) {
		return add(widget);
	}
}
