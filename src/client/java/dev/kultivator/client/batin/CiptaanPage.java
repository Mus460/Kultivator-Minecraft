package dev.kultivator.client.batin;

import dev.kultivator.batin.CiptaanArts;
import dev.kultivator.batin.Creation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.abadi.AbadiClient;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Dao;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Keabadian › Ciptaan (DESIGN 26.10, 26.7.3, 43.5; a page since 43 M16): the three slots (name, the two Dao colours, form,
 * Kesempurnaan) and the creator: the kit name field ({@code InkField}), the Dao A / Dao B / form pickers (cycling
 * talismans: click forward, right click back, ←/→ when focused), the costs and requirements from the real constants with
 * what the player has, the estimated Kesempurnaan and the talismans Ciptakan (overwriting a filled slot asks with the
 * confirm seal) · Sempurnakan · Hapus (confirm seal) · Batal during the 60 s Pencerahan Ciptaan. Opens after the ascent
 * (the old hub card's lock). Text at 1.0 (43.0.5). Reads {@link BatinClient#data()}, asked for while shown.
 */
public class CiptaanPage extends Page {

	private static final String P = "kultivator.batin.ciptaan.page.";
	private static final String K = "kultivator.gui43.m16.ciptaan.";
	private static final int ROW_H = 14;
	private static int slot;
	private static int daoA;
	private static int daoB = 1;
	private static int form = 1;
	private static String name = "";
	private final AbadiClient.Baris baris = new AbadiClient.Baris();
	private int linesTop;
	private @Nullable InkField nameField;

	public CiptaanPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine9.tab.ciptaan");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine9.tab.ciptaan.short");
	}

	@Override
	public int glyph() {
		return Glyphs.CODEX;
	}

	@Override
	public String helpTopic() {
		return "jurus_ciptaan";
	}

	@Override
	public @Nullable Component kunci() {
		return AbadiClient.kunciNaik();
	}

	private static CompoundTag batin() {
		return BatinClient.data();
	}

	private static CompoundTag creator() {
		return batin().getCompoundOrEmpty("creator");
	}

	private static CompoundTag art(int index) {
		ListTag arts = batin().getListOrEmpty("ciptaan");
		return index >= 0 && index < arts.size() ? arts.getCompoundOrEmpty(index) : new CompoundTag();
	}

	private static boolean filled(int index) {
		return !art(index).getStringOr("name", "").isEmpty();
	}

	private static boolean ritual() {
		return creator().getIntOr("ritual", -1) >= 0;
	}

	private int cardH() {
		return Math.max(30, Math.min(36, h / 5));
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		Keys.send(new Payloads.Action(Actions.BATIN, 0, "sync"));
	}

	@Override
	protected void build() {
		int gap = 3;
		int cardW = (w - gap * (CiptaanArts.SLOTS - 1)) / CiptaanArts.SLOTS;
		for (int i = 0; i < CiptaanArts.SLOTS; i++) {
			int index = i;
			SlotCard card = new SlotCard(x + i * (cardW + gap), y, cardW, cardH(), index);
			card.onPress(() -> {
				slot = index;
				CompoundTag art = art(index);
				if (filled(index)) {
					daoA = art.getIntOr("a", daoA);
					daoB = art.getIntOr("b", daoB);
					form = art.getIntOr("f", form);
					name = art.getStringOr("name", name);
					if (nameField != null) {
						nameField.setText(name);
					}
				}
			});
			card.tooltip(() -> cardTip(index));
			add(card);
		}
		// the name field and the three pickers: one row, or two in the narrow layout (no shrinking, 43.0.5)
		int rowY = y + cardH() + gap;
		boolean narrow = tata() == Tata.SEMPIT;
		int fieldW = narrow ? w : Math.round(w * 0.4F);
		InkField field = nameField = new InkField(x, rowY, fieldW, ROW_H, Creation.NAME_MAX, text -> name = text);
		field.hint(Component.translatable(P + "name"));
		field.onChange(text -> name = text);
		field.setText(name);
		field.tooltip(() -> List.of(Component.translatable(P + "name.tip", Creation.NAME_MAX).withStyle(ChatFormatting.GRAY)));
		add(field);
		int pickY = narrow ? rowY + ROW_H + gap : rowY;
		int pickX = narrow ? x : x + fieldW + gap;
		int pickW = (x + w - pickX - 2 * gap) / 3;
		int daos = Dao.values().length;
		int forms = Creation.Form.values().length;
		add(new TalismanButton(pickX, pickY, pickW, ROW_H, () -> daoLabel("a", daoA))
				.siklus(() -> daoA = (daoA + 1) % daos, () -> daoA = (daoA + daos - 1) % daos)
				.tooltip(() -> List.of(daoLabel("a", daoA), Component.translatable(P + "pick.tip").withStyle(ChatFormatting.GRAY))));
		add(new TalismanButton(pickX + pickW + gap, pickY, pickW, ROW_H, () -> daoLabel("b", daoB))
				.siklus(() -> daoB = (daoB + 1) % daos, () -> daoB = (daoB + daos - 1) % daos)
				.tooltip(() -> List.of(daoLabel("b", daoB), Component.translatable(P + "pick.tip").withStyle(ChatFormatting.GRAY))));
		add(new TalismanButton(pickX + 2 * (pickW + gap), pickY, x + w - (pickX + 2 * (pickW + gap)), ROW_H,
				() -> Component.translatable(P + "form", Creation.Form.values()[form].displayName()))
				.siklus(() -> form = (form + 1) % forms, () -> form = (form + forms - 1) % forms)
				.tooltip(() -> List.of(Component.translatable(P + "form", Creation.Form.values()[form].displayName()),
						Component.translatable("kultivator.batin.form." + Creation.Form.values()[form].id() + ".tip").withStyle(ChatFormatting.GRAY))));
		linesTop = pickY + ROW_H + gap + 1;

		int buttonW = (w - 3 * gap) / 4;
		int by = y + h - ROW_H;
		TalismanButton create = new TalismanButton(x, by, buttonW, ROW_H, () -> Component.translatable(P + "create"));
		create.enabledWhen(() -> Keys.connected() && !ritual() && Creation.cleanName(name) != null && ready() == null);
		create.onPress(() -> {
			if (filled(slot)) {
				konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "timpa", art(slot).getStringOr("name", "?"), slot + 1), this::create);
			} else {
				create();
			}
		});
		create.tooltip(() -> {
			Component why = ready();
			return why == null ? List.of(Component.translatable(P + "create.tip", Creation.CHANNEL_SECONDS).withStyle(ChatFormatting.GRAY))
					: List.of(why.copy().withStyle(ChatFormatting.RED));
		});
		add(create);
		TalismanButton refine = new TalismanButton(x + buttonW + gap, by, buttonW, ROW_H, () -> Component.translatable(P + "refine"));
		refine.enabledWhen(() -> Keys.connected() && !ritual() && filled(slot) && art(slot).getIntOr("q", 100) < 100);
		refine.onPress(() -> send(Actions.BATIN, 0, "refine:" + slot));
		refine.tooltip(() -> List.of(Component.translatable(P + "refine.tip", Creation.REFINE_PEARL_COST,
				Msg.number(creator().getDoubleOr("refine_qi", 0.0D))).withStyle(ChatFormatting.GRAY)));
		add(refine);
		TalismanButton delete = new TalismanButton(x + 2 * (buttonW + gap), by, buttonW, ROW_H, () -> Component.translatable(P + "delete"));
		delete.enabledWhen(() -> Keys.connected() && !ritual() && filled(slot));
		delete.onPress(() -> {
			int target = slot;
			konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(K + "hapus", art(target).getStringOr("name", "?"), target + 1),
					() -> send(Actions.BATIN, 0, "delete:" + target));
		});
		delete.tooltip(() -> List.of(Component.translatable(P + "delete.tip").withStyle(ChatFormatting.GRAY)));
		add(delete);
		TalismanButton cancel = new TalismanButton(x + 3 * (buttonW + gap), by, x + w - (x + 3 * (buttonW + gap)), ROW_H,
				() -> Component.translatable(P + "cancel"));
		cancel.enabledWhen(() -> Keys.connected() && ritual());
		cancel.onPress(() -> send(Actions.BATIN, 0, "cancel"));
		add(cancel);
	}

	private void create() {
		String clean = Creation.cleanName(name);
		if (clean != null) {
			send(Actions.BATIN, 0, "create:" + slot + ":" + daoA + ":" + daoB + ":" + form + ":1:" + clean);
		}
	}

	private static Component daoLabel(String which, int ordinal) {
		Dao dao = Dao.byOrdinal(ordinal);
		int[] comp = creator().getIntArray("comp").orElse(new int[0]);
		int value = ordinal >= 0 && ordinal < comp.length ? comp[ordinal] : 0;
		return dao == null ? Component.literal("?") : Component.translatable(P + "dao." + which, dao.displayName().copy().withColor(dao.color()), value);
	}

	/** Why "Ciptakan" is not possible now (from the synced numbers; the server checks again), or null. */
	private static @Nullable Component ready() {
		CompoundTag data = batin();
		CompoundTag creator = creator();
		if (!data.getBooleanOr("born", false)) {
			return Component.translatable("kultivator.batin.ciptaan.need_batin");
		}
		if (!creator.getBooleanOr("peak", false)) {
			return Component.translatable("kultivator.batin.ciptaan.need_peak");
		}
		int[] comp = creator.getIntArray("comp").orElse(new int[0]);
		int a = daoA < comp.length ? comp[daoA] : 0;
		int b = daoB < comp.length ? comp[daoB] : 0;
		Dao dA = Dao.byOrdinal(daoA);
		Dao dB = Dao.byOrdinal(daoB);
		if (dA == null || dB == null) {
			return Component.literal("?");
		}
		if (daoA == daoB) {
			if (a < Creation.PURE_COMPREHENSION) {
				return Component.translatable("kultivator.batin.ciptaan.need_comp", dA.displayName(), (int) Creation.PURE_COMPREHENSION);
			}
		} else if (a < Creation.MIN_COMPREHENSION || b < Creation.MIN_COMPREHENSION) {
			return Component.translatable("kultivator.batin.ciptaan.need_comp", (a < Creation.MIN_COMPREHENSION ? dA : dB).displayName(),
					(int) Creation.MIN_COMPREHENSION);
		}
		if (creator.getIntOr("pearls", 0) < Creation.PEARL_COST) {
			return Component.translatable("kultivator.batin.ciptaan.need_pearls", Creation.PEARL_COST);
		}
		if (creator.getIntOr("material", 0) < Creation.MATERIAL_COST) {
			return Component.translatable("kultivator.batin.ciptaan.need_material");
		}
		if (creator.getDoubleOr("qi", 0.0D) + 1.0E-6D < creator.getDoubleOr("cost_qi", 0.0D)) {
			return Component.translatable("kultivator.batin.ciptaan.need_qi", Msg.number(creator.getDoubleOr("cost_qi", 0.0D)),
					Msg.number(creator.getDoubleOr("qi", 0.0D)));
		}
		return null;
	}

	/** The ceiling of the quality formula with the synced comprehension and kinds slain. */
	private static int ceiling() {
		CompoundTag creator = creator();
		int[] comp = creator.getIntArray("comp").orElse(new int[0]);
		int a = daoA < comp.length ? comp[daoA] : 0;
		int b = daoB < comp.length ? comp[daoB] : 0;
		double q = Creation.Q_MIN + (a + b - 120.0D) / 200.0D + Creation.Q_PER_KIND * creator.getIntOr("kinds", 0);
		return (int) Math.round(Math.max(Creation.Q_MIN, Math.min(Creation.Q_MAX, q)) * 100.0D);
	}

	private List<Component> cardTip(int index) {
		CompoundTag art = art(index);
		if (!filled(index)) {
			return List.of(Component.translatable(P + "slot", index + 1), Component.translatable(P + "slot.empty").withStyle(ChatFormatting.GRAY));
		}
		Dao a = Dao.byOrdinal(art.getIntOr("a", 0));
		Dao b = Dao.byOrdinal(art.getIntOr("b", 0));
		Creation.Form f = Creation.Form.byOrdinal(art.getIntOr("f", 0));
		return List.of(Component.literal(art.getStringOr("name", "?")).withStyle(ChatFormatting.GOLD),
				Component.translatable(P + "slot.line", a == null ? Component.literal("?") : a.displayName(), b == null ? Component.literal("?") : b.displayName(),
						f == null ? Component.literal("?") : f.displayName(), art.getIntOr("q", 0)).withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.technique.own_art_" + (index + 1)).withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	protected boolean visible(Widget widget) {
		return batin().getBooleanOr("on", true);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		CompoundTag data = batin();
		if (!data.getBooleanOr("on", true)) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.GULUNGAN, Component.translatable("kultivator.batin.off"), null, null, x, y, x + w, y + h);
			return;
		}
		int top = linesTop;
		CompoundTag creator = creator();
		int ritual = creator.getIntOr("ritual", -1);
		if (ritual >= 0) {
			Bilah.baris(l, Brush.bold(Component.translatable(P + "ritual", Math.min(ritual, Creation.CHANNEL_SECONDS), Creation.CHANNEL_SECONDS)),
					Math.min(ritual, Creation.CHANNEL_SECONDS), Creation.CHANNEL_SECONDS, x, top, x + w, null, Palette.STORED);
			top += 12;
		}
		baris.mulai(w);
		baris.tambah(Component.translatable(P + "cost", Creation.PEARL_COST, creator.getIntOr("pearls", 0), Creation.MATERIAL_COST,
				creator.getIntOr("material", 0), Msg.number(creator.getDoubleOr("cost_qi", 0.0D)), Msg.number(creator.getDoubleOr("qi", 0.0D))), Palette.INK);
		baris.tambah(daoA == daoB ? Component.translatable(P + "need_pure", (int) Creation.PURE_COMPREHENSION)
				: Component.translatable(P + "need", (int) Creation.MIN_COMPREHENSION), Palette.INK_SOFT);
		Creation.Form chosenForm = Creation.Form.values()[form];
		double coef = chosenForm.coef() * (daoA == daoB ? Creation.PURE_BONUS : 1.0D);
		baris.tambah(Component.translatable(P + "estimate", ceiling(), String.format(java.util.Locale.ROOT, "%.1f", coef), creator.getIntOr("kinds", 0)),
				Palette.GOLD_DARK);
		Component why = ready();
		if (why != null && ritual < 0) {
			baris.tambah(why, Palette.DANGER);
		}
		baris.gambar(l, "ciptaan", x, top, x + w, y + h - ROW_H - 3);
	}

	/** One slot of the three: its name, the two Dao colours, the form and Kesempurnaan; the chosen one is framed in gold. */
	private final class SlotCard extends Widget {

		private final int index;

		SlotCard(int x, int y, int w, int h, int index) {
			super(x, y, w, h);
			this.index = index;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean chosen = slot == index;
			int off = pressOffset(over);
			int x0 = x + off;
			int y0 = y + off;
			g.fill(x0, y0, x0 + w, y0 + h, Palette.argb(chosen ? 0x60 : 0x38, Palette.PAPER_DEEP));
			Brush.frame(g, x0, y0, x0 + w, y0 + h, chosen ? Palette.GOLD : Palette.argb(over ? 0xE0 : 0x90, Palette.GOLD_DARK));
			Brush.text(g, font, Component.literal(String.valueOf(index + 1)), x0 + 3.0F, y0 + 3.0F, Palette.SEAL, 1.0F, false);
			CompoundTag art = art(index);
			if (!filled(index)) {
				Brush.fit(g, font, Component.translatable(P + "slot.empty_short"), x0 + 11.0F, y0 + 3.0F, w - 14.0F, Palette.INK_FAINT, 1.0F, 1.0F, false);
				return;
			}
			Brush.fit(g, font, Brush.bold(Component.literal(art.getStringOr("name", "?"))), x0 + 11.0F, y0 + 3.0F, w - 14.0F, Palette.SEAL_DARK, 1.0F, 1.0F,
					false);
			Dao a = Dao.byOrdinal(art.getIntOr("a", 0));
			Dao b = Dao.byOrdinal(art.getIntOr("b", 0));
			float dotY = y0 + 18.0F;
			if (a != null) {
				Brush.disc(g, x0 + 6.0F, dotY, 2.5F, 0xFF000000 | a.color());
			}
			if (b != null) {
				Brush.disc(g, x0 + 12.0F, dotY, 2.5F, 0xFF000000 | b.color());
			}
			Creation.Form f = Creation.Form.byOrdinal(art.getIntOr("f", 0));
			if (f != null) {
				Brush.fit(g, font, f.displayName(), x0 + 17.0F, dotY - 4.0F, w - 20.0F, Palette.INK, 1.0F, 1.0F, false);
			}
			int q = art.getIntOr("q", 0);
			float barY = y0 + h - 5.0F;
			Brush.rect(g, x0 + 3.0F, barY, x0 + w - 3.0F, barY + 2.0F, Palette.argb(0x70, Palette.BAMBOO_KNOT));
			Brush.rect(g, x0 + 3.0F, barY, x0 + 3.0F + (w - 6.0F) * q / 100.0F, barY + 2.0F, q >= 100 ? Palette.GOLD_LIGHT : Palette.GOLD);
		}
	}
}
