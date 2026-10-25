package dev.kultivator.client.sekte.seragam;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.seragam.SeragamData;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/**
 * The emblem and uniform designer of module S (DESIGN 36.2.8) as stacked sections of Sekte › Ikhtisar › Identitas (43.5,
 * 43.11 G44; was {@code LambangDesignerScreen}): the live preview (banner cloth, bare emblem, generation, motto and a paper
 * doll of the chosen rank's robe) with Acak / Kembalikan / Simpan and the cost line, then "Lambang" (eight shapes,
 * thirty-two symbols, the three colours from the 32-colour palette, the motto) and "Seragam" (per rank the robe and trim
 * colour or "Asli", the cut, "Samakan semua jenjang") as headings in one scroll (43.0.4: no fourth tab bar). Immediate
 * mode in the page's {@link Lukis}; every swatch and cell is at least 16 × 12 (43.0.6), text at 1.0. Only the hall master
 * edits; anyone else sees it read-only. The design comes from the last {@code LambangOpen} ({@link SeragamClient#terakhir()}).
 */
public final class LambangPanel {

	private static final Identifier ATLAS = Kultivator.id("textures/sekte/seragam/lambang_atlas.png");
	private static final String U = "kultivator.sekte.seragam.ui.";
	private static final String[] RANK_IDS = {"tercatat", "luar", "dalam", "inti", "tetua", "tetua_agung"};
	/** A neutral swatch that stands for "the old look" (the Dao tint is per disciple). */
	private static final int ASLI_ROBE = 0xC9D3DC;
	private static final int SW_W = 16;
	private static final int SW_H = 12;
	private static final int BARIS = 10;

	private final InkField motto = new InkField(0, 0, 80, 14, SeragamData.MOTTO_MAX, null);
	private String key = "";
	private SeragamData original = SeragamData.DEFAULT;
	private SeragamData working = SeragamData.DEFAULT;
	private int freeLeft;
	private int cost;
	private int pool;
	private boolean canEdit;
	private boolean hasHall;
	private long seenOpen = -1L;
	private int colourSlot;
	private int rank;
	private long savedAt = Long.MIN_VALUE / 2;

	public LambangPanel() {
		motto.hint(Component.translatable(U + "semboyan.hint"));
		motto.onChange(text -> {
			if (canEdit) {
				working = working.withMotto(text);
			}
		});
	}

	/** True when a design has arrived (else the section shows "memuat…" and asks again). */
	public boolean siap() {
		return !key.isEmpty();
	}

	/** True while the motto field writes (the page then gets every key first). */
	public boolean menulis() {
		return motto.focused();
	}

	/** Picks up a newer {@code LambangOpen} (after a save, or the first answer). */
	private void segarkan() {
		SeragamPayloads.LambangOpen open = SeragamClient.terakhir();
		if (open == null || SeragamClient.versiBuka() == seenOpen) {
			return;
		}
		seenOpen = SeragamClient.versiBuka();
		boolean sameHall = open.key().equals(key);
		key = open.key();
		freeLeft = open.freeLeft();
		cost = open.cost();
		pool = open.pool();
		canEdit = (open.flags() & SeragamPayloads.LambangOpen.OPEN_EDIT) != 0;
		hasHall = (open.flags() & SeragamPayloads.LambangOpen.OPEN_HALL) != 0;
		LambangCache.Entry entry = LambangCache.get(key);
		SeragamData saved = entry == null ? SeragamData.DEFAULT : entry.data;
		boolean pristine = !sameHall || working.sameDesign(original);
		original = saved;
		if (pristine) {
			working = saved;
			motto.setText(working.motto());
		}
	}

	/**
	 * Draws the preview with its buttons, "Lambang" and "Seragam" from {@code y0} down between {@code x0} and {@code x1};
	 * returns the y below.
	 */
	public int gambar(Lukis l, int x0, int y0, int x1) {
		segarkan();
		int cy = pratinjau(l, x0, y0, x1);
		cy = lambang(l, x0, cy + 4, x1);
		cy = seragam(l, x0, cy + 4, x1);
		return cy;
	}

	// ================================================================== preview, buttons, cost

	private int pratinjau(Lukis l, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable(U + "title"), x0, y0, x1);
		if (l.di(x0, y0, x1, y0 + 11)) {
			l.tip(List.of(Component.translatable(U + "title"), Component.translatable("kultivator.sekte.seragam.sumber")));
		}
		Identifier preview = LambangAtlas.preview(working);
		int px = x0 + 2;
		int py = cy + 1;
		// a lacquer arm and the banner cloth below it, the bare emblem beside it
		l.g.fill(px, py, px + 44, py + 3, 0xFF6E160F);
		l.g.fill(px, py, px + 2, py + 3, 0xFFD4A637);
		l.g.fill(px + 42, py, px + 44, py + 3, 0xFFD4A637);
		if (preview != null) {
			l.g.blit(RenderPipelines.GUI_TEXTURED, preview, px + 2, py + 3, 0.0F, 0.0F, 40, 80, 64, 128, 128, 128);
			l.g.blit(RenderPipelines.GUI_TEXTURED, preview, px + 48, py + 3, 64.0F, 0.0F, 44, 44, 64, 64, 128, 128);
		}
		drawDoll(l, px + 48 + 44 + 26, py + 4, preview);
		int tx = px + 48;
		int ty = py + 50;
		int tw = Math.max(40, x1 - tx);
		l.teks(Component.translatable(U + "generasi", working.generation()), tx, ty, tw, l.gaya.teksLemah);
		ty += BARIS;
		Component mottoText = working.motto().isEmpty() ? Component.translatable("kultivator.sekte.seragam.no_motto")
				: Component.literal("「" + working.motto() + "」");
		ty += Math.round(l.bungkus(mottoText, tx, ty, tw, l.gaya.teks, 2 * 9));
		cy = Math.max(py + 86, ty) + 2;
		// the doll's caption: the chosen rank
		l.samar(Component.translatable(U + "jubah_prev").copy().append(" · ").append(Component.translatable("kultivator.hall.rank." + RANK_IDS[rank])),
				x0 + 2, cy, x1 - x0 - 2);
		cy += BARIS + 2;
		// Acak, Kembalikan, Simpan
		int bw = Math.max(TalismanButton.MIN_W, (x1 - x0 - 8) / 3);
		boolean bisaSimpan = canEdit && Util.getMillis() - savedAt > 800L;
		TalismanButton.gambar(l, "lambang_acak", x0, cy, bw, 14, Component.translatable(U + "acak"), canEdit, alasanBaca(), this::randomise);
		TalismanButton.gambar(l, "lambang_batal", x0 + bw + 4, cy, bw, 14, Component.translatable(U + "batal"),
				canEdit && !working.sameDesign(original), canEdit ? null : alasanBaca(), this::revert);
		boolean over = TalismanButton.gambar(l, "lambang_simpan", x0 + 2 * (bw + 4), cy, x1 - x0 - 2 * (bw + 4), 14,
				Component.translatable(U + "simpan"), bisaSimpan, canEdit ? null : alasanBaca(), this::save,
				canEdit && (!working.sameDesign(original) || !original.designed()), false);
		if (over) {
			l.tip(List.of(costLine(), Component.translatable(U + "simpan.tip")));
		}
		cy += 17;
		cy += Math.round(l.bungkus(costLine(), x0 + 2, cy, x1 - x0 - 2, canEdit ? Palette.JADE_DARK : Palette.SEAL_DARK));
		return cy + 2;
	}

	private @Nullable Component alasanBaca() {
		return canEdit ? null : Component.translatable(hasHall ? U + "bukan_ketua" : U + "tanpa_aula");
	}

	private Component costLine() {
		if (!hasHall) {
			return Component.translatable(U + "tanpa_aula");
		}
		if (!canEdit) {
			return Component.translatable(U + "bukan_ketua");
		}
		return freeLeft > 0 ? Component.translatable(U + "biaya.gratis", freeLeft) : Component.translatable(U + "biaya.pk", cost, pool);
	}

	// ================================================================== Lambang

	private int lambang(Lukis l, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable(U + "tab.lambang"), x0, y0, x1);
		if (l.di(x0, y0, x1, y0 + 11)) {
			l.tip(Component.translatable(U + "tab.lambang.tip"));
		}
		Lambang e = working.emblem();
		int w = x1 - x0;
		// shapes
		l.samar(Component.translatable(U + "bentuk"), x0, cy, w);
		cy += BARIS;
		int cell = 18;
		int perRow = Math.max(1, (w + 2) / (cell + 2));
		for (int i = 0; i < Lambang.SHAPES; i++) {
			int shape = i;
			int cx = x0 + (i % perRow) * (cell + 2);
			int ry = cy + (i / perRow) * (cell + 2);
			maskCell(l, "bentuk" + i, cx, ry, cell, true, shape, e.shape() == shape,
					() -> edit(old -> new Lambang(shape, old.symbol(), old.field(), old.charge(), old.border())),
					Component.translatable("kultivator.sekte.seragam.bentuk." + shape));
		}
		cy += ((Lambang.SHAPES + perRow - 1) / perRow) * (cell + 2) + 3;
		// symbols
		l.samar(Component.translatable(U + "simbol"), x0, cy, w);
		cy += BARIS;
		int sc = 16;
		int perRowS = Math.max(1, Math.min(8, (w + 2) / (sc + 2)));
		for (int i = 0; i < Lambang.SYMBOLS; i++) {
			int symbol = i;
			int cx = x0 + (i % perRowS) * (sc + 2);
			int ry = cy + (i / perRowS) * (sc + 2);
			maskCell(l, "simbol" + i, cx, ry, sc, false, symbol, e.symbol() == symbol,
					() -> edit(old -> new Lambang(old.shape(), symbol, old.field(), old.charge(), old.border())),
					Component.translatable("kultivator.sekte.seragam.simbol." + symbol));
		}
		cy += ((Lambang.SYMBOLS + perRowS - 1) / perRowS) * (sc + 2) + 3;
		// the three colour slots, then the palette
		int slotW = (w - 2 * 3) / 3;
		for (int s = 0; s < 3; s++) {
			int slot = s;
			Component label = Component.translatable(U + "warna." + slot);
			if (TalismanButton.gambar(l, "slot" + slot, x0 + s * (slotW + 3), cy, slotW, 14, label, true, null, () -> colourSlot = slot, false,
					colourSlot == slot)) {
				l.tip(List.of(label, Component.translatable(U + "warna." + slot + ".tip")));
			}
		}
		cy += 17;
		cy = palette(l, "warna", x0, cy, x1, false, index -> edit(old -> switch (colourSlot) {
			case 0 -> new Lambang(old.shape(), old.symbol(), index, old.charge(), old.border());
			case 1 -> new Lambang(old.shape(), old.symbol(), old.field(), index, old.border());
			default -> new Lambang(old.shape(), old.symbol(), old.field(), old.charge(), index);
		}), () -> switch (colourSlot) {
			case 0 -> working.emblem().field();
			case 1 -> working.emblem().charge();
			default -> working.emblem().border();
		});
		cy += 3;
		// the motto
		l.samar(Component.translatable(U + "semboyan"), x0, cy, w);
		cy += BARIS;
		if (canEdit) {
			motto.x = x0;
			motto.y = cy;
			motto.w = Math.max(TalismanButton.MIN_W, w);
			motto.h = 14;
			l.widget("semboyan", motto);
			cy += 16;
		} else {
			Component m = working.motto().isEmpty() ? Component.translatable("kultivator.sekte.seragam.no_motto") : Component.literal(working.motto());
			l.teks(m, x0 + 2, cy, w - 2, l.gaya.teksLemah);
			cy += BARIS + 2;
		}
		return cy;
	}

	/** A shape or symbol cell: the mask from the atlas tinted with the working colours, a gold ring when chosen. */
	private void maskCell(Lukis l, String kunci, int bx, int by, int size, boolean shape, int index, boolean chosen, Runnable aksi, Component tip) {
		Lambang e = working.emblem();
		boolean over = l.di(bx, by, bx + size, by + size);
		l.g.fill(bx, by, bx + size, by + size, chosen ? Palette.argb(0x60, Palette.GOLD_LIGHT) : Palette.argb(0x28, Palette.INK));
		if (shape) {
			int u = index * 64;
			l.g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, u, 0.0F, size - 2, size - 2, 64, 64, 512, 384, 0xFF000000 | e.fieldRgb());
			l.g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, u, 64.0F, size - 2, size - 2, 64, 64, 512, 384, 0xFF000000 | e.borderRgb());
		} else {
			int u = (index % 8) * 64;
			int v = 128 + (index / 8) * 64;
			l.g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, 0.0F, 0.0F, size - 2, size - 2, 64, 64, 512, 384,
					Palette.argb(0x70, e.fieldRgb()));
			l.g.blit(RenderPipelines.GUI_TEXTURED, ATLAS, bx + 1, by + 1, u, v, size - 2, size - 2, 64, 64, 512, 384, 0xFF000000 | e.chargeRgb());
		}
		if (chosen) {
			Brush.frame(l.g, bx - 1, by - 1, bx + size + 1, by + size + 1, Palette.GOLD);
		} else if (over && canEdit) {
			Brush.frame(l.g, bx, by, bx + size, by + size, Palette.argb(0xA0, Palette.SEAL_GLOW));
		}
		l.zona(kunci, bx, by, bx + size, by + size, aksi, canEdit, alasanBaca(), true);
		l.fokusCincin(kunci, bx, by, bx + size, by + size);
		if (over) {
			l.tip(tip);
		}
	}

	/** The 32 palette swatches (plus "Asli" first when {@code withAsli}) in rows of 16 × 12 swatches; returns the y below. */
	private int palette(Lukis l, String kunci, int x0, int y0, int x1, boolean withAsli, IntConsumer pick, IntSupplier current) {
		int n = Lambang.PALETTE.length + (withAsli ? 1 : 0);
		int perRow = Math.max(1, (x1 - x0 + 1) / (SW_W + 1));
		for (int k = 0; k < n; k++) {
			int index = withAsli ? k - 1 : k;
			int sx = x0 + (k % perRow) * (SW_W + 1);
			int sy = y0 + (k / perRow) * (SW_H + 1);
			boolean chosen = current.getAsInt() == index;
			if (index < 0) {
				l.g.fill(sx, sy, sx + SW_W, sy + SW_H, 0xFFE6EDDB);
				Brush.line(l.g, sx + 1, sy + SW_H - 1, sx + SW_W - 1, sy + 1, 1.0F, Palette.SEAL);
			} else {
				l.g.fill(sx, sy, sx + SW_W, sy + SW_H, 0xFF000000 | Lambang.PALETTE[index]);
			}
			Brush.frame(l.g, sx, sy, sx + SW_W, sy + SW_H, chosen ? Palette.GOLD : Palette.argb(0x60, Palette.INK));
			if (chosen) {
				Brush.frame(l.g, sx - 1, sy - 1, sx + SW_W + 1, sy + SW_H + 1, Palette.SEAL);
			}
			String z = kunci + k;
			l.zona(z, sx, sy, sx + SW_W, sy + SW_H, () -> pick.accept(index), canEdit, alasanBaca(), true);
			l.fokusCincin(z, sx, sy, sx + SW_W, sy + SW_H);
			if (l.di(sx, sy, sx + SW_W, sy + SW_H)) {
				l.tip(index < 0 ? List.of(Component.translatable(U + "asli"), Component.translatable(U + "asli.tip"))
						: List.of(Component.translatable("kultivator.sekte.seragam.warna." + index)));
			}
		}
		return y0 + ((n + perRow - 1) / perRow) * (SW_H + 1);
	}

	// ================================================================== Seragam

	private int seragam(Lukis l, int x0, int y0, int x1) {
		int cy = y0 + l.kepala(Component.translatable(U + "tab.seragam"), x0, y0, x1);
		if (l.di(x0, y0, x1, y0 + 11)) {
			l.tip(Component.translatable(U + "tab.seragam.tip"));
		}
		int w = x1 - x0;
		// ranks: three per row so every name fits at 1.0
		int rw = (w - 2 * 3) / 3;
		for (int r = 0; r < SeragamData.RANKS; r++) {
			int which = r;
			Component label = Component.translatable(U + "jenjang." + which);
			if (TalismanButton.gambar(l, "jenjang" + r, x0 + (r % 3) * (rw + 3), cy + (r / 3) * 16, rw, 14, label, true, null, () -> rank = which, false,
					rank == which)) {
				l.tip(Component.translatable("kultivator.hall.rank." + RANK_IDS[which]));
			}
		}
		cy += ((SeragamData.RANKS + 2) / 3) * 16 + 2;
		l.samar(Component.translatable(U + "jubah"), x0, cy, w);
		cy += BARIS;
		cy = palette(l, "jubah", x0, cy, x1, true, index -> working = working.withRank(rank, index, working.trimIndex(rank)),
				() -> working.robeIndex(rank)) + 3;
		l.samar(Component.translatable(U + "trim"), x0, cy, w);
		cy += BARIS;
		cy = palette(l, "trim", x0, cy, x1, true, index -> working = working.withRank(rank, working.robeIndex(rank), index),
				() -> working.trimIndex(rank)) + 3;
		l.samar(Component.translatable(U + "potongan"), x0, cy, w);
		cy += BARIS;
		int cw = (w - 2 * 3) / 3;
		for (int c = 0; c < SeragamData.CUTS; c++) {
			int cut = c;
			Component label = Component.translatable(U + "potongan." + cut);
			if (TalismanButton.gambar(l, "potongan" + c, x0 + c * (cw + 3), cy, cw, 14, label, canEdit, alasanBaca(),
					() -> working = working.withCut(rank, cut), false, working.cutOf(rank) == cut)) {
				l.tip(List.of(label, Component.translatable(U + "potongan." + cut + ".tip")));
			}
		}
		cy += 17;
		if (TalismanButton.gambar(l, "salin", x0, cy, w, 14, Component.translatable(U + "salin"), canEdit, alasanBaca(), this::copyToAllRanks)) {
			l.tip(Component.translatable(U + "salin.tip"));
		}
		return cy + 17;
	}

	// ================================================================== edits

	private void edit(UnaryOperator<Lambang> change) {
		if (canEdit) {
			working = working.withEmblem(change.apply(working.emblem()));
		}
	}

	private void copyToAllRanks() {
		SeragamData data = working;
		for (int r = 0; r < SeragamData.RANKS; r++) {
			data = data.withRank(r, working.robeIndex(rank), working.trimIndex(rank)).withCut(r, working.cutOf(rank));
		}
		working = data;
	}

	private void randomise() {
		working = working.withEmblem(Lambang.seeded(ThreadLocalRandom.current().nextLong()));
	}

	private void revert() {
		working = original;
		motto.setText(working.motto());
	}

	private void save() {
		savedAt = Util.getMillis();
		if (ClientPlayNetworking.canSend(SeragamPayloads.LambangSet.TYPE)) {
			SeragamClient.tandaiDiminta();
			ClientPlayNetworking.send(new SeragamPayloads.LambangSet(working));
		}
	}

	// ================================================================== the robe doll

	/** A paper doll of the robe: body, sleeves, sash, skirt of the cut's length, trims, the emblem on the chest. */
	private void drawDoll(Lukis l, int cx, int y, @Nullable Identifier preview) {
		var g = l.g;
		int robe = working.robeRgb(rank) >= 0 ? working.robeRgb(rank) : ASLI_ROBE;
		int trim = working.trimRgb(rank) >= 0 ? working.trimRgb(rank) : PhysiqueVisuals.RANK_TRIM[rank];
		int cut = working.cutOf(rank);
		int body = 0xFF000000 | robe;
		int dark = 0xFF000000 | LambangAtlas.mix(robe, 0x000000, 0.25F);
		int edge = 0xFF000000 | trim;
		g.fill(cx - 4, y, cx + 4, y + 8, 0xFFE8C9A8);
		g.fill(cx - 4, y, cx + 4, y + 2, 0xFF2B2018);
		g.fill(cx - 8, y + 9, cx + 8, y + 25, body);
		int sleeve = cut == SeragamData.CUT_TEMPUR ? 3 : 5;
		g.fill(cx - 8 - sleeve, y + 9, cx - 8, y + 23, dark);
		g.fill(cx + 8, y + 9, cx + 8 + sleeve, y + 23, dark);
		if (cut == SeragamData.CUT_TEMPUR) {
			g.fill(cx - 8 - sleeve, y + 17, cx - 8, y + 23, 0xFF5A3A22);
			g.fill(cx + 8, y + 17, cx + 8 + sleeve, y + 23, 0xFF5A3A22);
			g.fill(cx - 9, y + 9, cx + 9, y + 11, 0xFF5A3A22);
		} else {
			g.fill(cx - 8 - sleeve, y + 21, cx - 8, y + 23, edge);
			g.fill(cx + 8, y + 21, cx + 8 + sleeve, y + 23, edge);
		}
		g.fill(cx - 4 - sleeve - 3, y + 23, cx - 8 - sleeve + 4, y + 26, 0xFFE8C9A8);
		g.fill(cx + 8 + sleeve - 4, y + 23, cx + 4 + sleeve + 7, y + 26, 0xFFE8C9A8);
		for (int i = 0; i < 6; i++) {
			g.fill(cx - 3 + i, y + 9 + i, cx - 1 + i, y + 10 + i, edge);
		}
		g.fill(cx - 8, y + 21, cx + 8, y + 24, cut == SeragamData.CUT_TEMPUR ? 0xFF5A3A22 : 0xFF2C2C34);
		int skirt = cut == SeragamData.CUT_PANJANG ? 22 : (cut == SeragamData.CUT_PENDEK ? 11 : 15);
		g.fill(cx - 9, y + 24, cx + 9, y + 24 + skirt, body);
		g.fill(cx - 9, y + 24 + skirt - 2, cx + 9, y + 24 + skirt, edge);
		if (cut == SeragamData.CUT_TEMPUR) {
			g.fill(cx - 1, y + 28, cx + 1, y + 24 + skirt, 0xFF2C2C34);
		}
		if (cut != SeragamData.CUT_PANJANG) {
			g.fill(cx - 6, y + 24 + skirt, cx - 1, y + 46, cut == SeragamData.CUT_TEMPUR ? 0xFF5A3A22 : dark);
			g.fill(cx + 1, y + 24 + skirt, cx + 6, y + 46, cut == SeragamData.CUT_TEMPUR ? 0xFF5A3A22 : dark);
		}
		if (preview != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, preview, cx + 1, y + 12, 64.0F, 0.0F, 6, 6, 64, 64, 128, 128);
		}
	}
}
