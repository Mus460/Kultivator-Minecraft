package dev.kultivator.client.batin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.batin.CiptaanArts;
import dev.kultivator.batin.Creation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Dao;
import dev.kultivator.network.Actions;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * The Ciptaan tablet of the Keabadian screen (DESIGN 26.10, 26.7.3): the three slots (name, the two Dao colours, form,
 * Kesempurnaan) and the creator: a name field, the Dao A / Dao B / form pickers, the costs and requirements from the real
 * constants with what the player has, the estimated Kesempurnaan and the talismans Ciptakan (a second click confirms
 * overwriting a slot) · Sempurnakan · Hapus (two clicks) · Batal during the 60 s Pencerahan Ciptaan. Reads
 * {@link BatinClient#data()}.
 */
public class CiptaanPage extends DuniaPage {

	private static final String P = "kultivator.batin.ciptaan.page.";
	private static int slot;
	private static int daoA;
	private static int daoB = 1;
	private static int form = 1;
	private static String name = "";
	private boolean typing;
	private long confirmCreate;
	private long confirmDelete;

	public CiptaanPage(DuniaScreen screen) {
		super(screen);
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
	public int icon() {
		return Glyphs.CODEX;
	}

	private static CompoundTag data() {
		return BatinClient.data();
	}

	private static CompoundTag creator() {
		return data().getCompoundOrEmpty("creator");
	}

	private static CompoundTag art(int index) {
		ListTag arts = data().getListOrEmpty("ciptaan");
		return index >= 0 && index < arts.size() ? arts.getCompoundOrEmpty(index) : new CompoundTag();
	}

	private static boolean filled(int index) {
		return !art(index).getStringOr("name", "").isEmpty();
	}

	private static boolean ritual() {
		return creator().getIntOr("ritual", -1) >= 0;
	}

	private int cardH() {
		return Math.max(24, Math.min(34, h / 5));
	}

	@Override
	protected void build() {
		int cardW = (w - 8) / CiptaanArts.SLOTS;
		int top = y + 14;
		for (int i = 0; i < CiptaanArts.SLOTS; i++) {
			int index = i;
			SlotCard card = new SlotCard(x + 2 + i * (cardW + 2), top, cardW, cardH(), index);
			card.onPress(() -> {
				slot = index;
				CompoundTag art = art(index);
				if (filled(index)) {
					daoA = art.getIntOr("a", daoA);
					daoB = art.getIntOr("b", daoB);
					form = art.getIntOr("f", form);
					name = art.getStringOr("name", name);
				}
			});
			card.tooltip(() -> cardTip(index));
			add(card);
		}
		int rowY = top + cardH() + 4;
		int fieldW = Math.round((w - 6) * 0.4F);
		NameField field = new NameField(x + 2, rowY, fieldW, 13);
		field.onPress(() -> typing = true);
		field.tooltip(() -> List.of(Component.translatable(P + "name.tip", Creation.NAME_MAX).withStyle(ChatFormatting.GRAY)));
		add(field);
		int pickW = (w - 6 - fieldW - 6) / 3;
		int pickX = x + 4 + fieldW + 2;
		TalismanButton pickA = new TalismanButton(pickX, rowY, pickW, 13, () -> daoLabel("a", daoA));
		pickA.onPress(() -> daoA = (daoA + 1) % Dao.values().length);
		pickA.tooltip(() -> List.of(Component.translatable(P + "pick.tip").withStyle(ChatFormatting.GRAY)));
		add(pickA);
		TalismanButton pickB = new TalismanButton(pickX + pickW + 2, rowY, pickW, 13, () -> daoLabel("b", daoB));
		pickB.onPress(() -> daoB = (daoB + 1) % Dao.values().length);
		pickB.tooltip(() -> List.of(Component.translatable(P + "pick.tip").withStyle(ChatFormatting.GRAY)));
		add(pickB);
		TalismanButton pickForm = new TalismanButton(pickX + 2 * (pickW + 2), rowY, pickW, 13,
				() -> Component.translatable(P + "form", Creation.Form.values()[form].displayName()));
		pickForm.onPress(() -> form = (form + 1) % Creation.Form.values().length);
		pickForm.tooltip(() -> List.of(Component.translatable("kultivator.batin.form." + Creation.Form.values()[form].id() + ".tip")
				.withStyle(ChatFormatting.GRAY)));
		add(pickForm);

		int buttonW = (w - 10) / 4;
		int by = y + h - 14;
		TalismanButton create = new TalismanButton(x + 2, by, buttonW, 13, () -> Component.translatable(
				confirming(confirmCreate) ? P + "create.confirm" : P + "create"));
		create.enabledWhen(() -> Keys.connected() && !ritual() && Creation.cleanName(name) != null && ready() == null);
		create.urgentWhen(() -> confirming(confirmCreate));
		create.onPress(() -> {
			if (filled(slot) && !confirming(confirmCreate)) {
				confirmCreate = Util.getMillis();
				return;
			}
			confirmCreate = 0L;
			String clean = Creation.cleanName(name);
			if (clean != null) {
				send(Actions.BATIN, 0, "create:" + slot + ":" + daoA + ":" + daoB + ":" + form + ":1:" + clean);
			}
		});
		create.tooltip(() -> {
			Component why = ready();
			return why == null ? List.of(Component.translatable(P + "create.tip", Creation.CHANNEL_SECONDS).withStyle(ChatFormatting.GRAY))
					: List.of(why.copy().withStyle(ChatFormatting.RED));
		});
		add(create);
		TalismanButton refine = new TalismanButton(x + 4 + buttonW, by, buttonW, 13, () -> Component.translatable(P + "refine"));
		refine.enabledWhen(() -> Keys.connected() && !ritual() && filled(slot) && art(slot).getIntOr("q", 100) < 100);
		refine.onPress(() -> send(Actions.BATIN, 0, "refine:" + slot));
		refine.tooltip(() -> List.of(Component.translatable(P + "refine.tip", Creation.REFINE_PEARL_COST,
				Msg.number(creator().getDoubleOr("refine_qi", 0.0D))).withStyle(ChatFormatting.GRAY)));
		add(refine);
		TalismanButton delete = new TalismanButton(x + 6 + 2 * buttonW, by, buttonW, 13, () -> Component.translatable(
				confirming(confirmDelete) ? P + "delete.confirm" : P + "delete"));
		delete.enabledWhen(() -> Keys.connected() && !ritual() && filled(slot));
		delete.urgentWhen(() -> confirming(confirmDelete));
		delete.onPress(() -> {
			if (!confirming(confirmDelete)) {
				confirmDelete = Util.getMillis();
				return;
			}
			confirmDelete = 0L;
			send(Actions.BATIN, 0, "delete:" + slot);
		});
		delete.tooltip(() -> List.of(Component.translatable(P + "delete.tip").withStyle(ChatFormatting.GRAY)));
		add(delete);
		TalismanButton cancel = new TalismanButton(x + 8 + 3 * buttonW, by, buttonW, 13, () -> Component.translatable(P + "cancel"));
		cancel.enabledWhen(() -> Keys.connected() && ritual());
		cancel.onPress(() -> send(Actions.BATIN, 0, "cancel"));
		add(cancel);
	}

	private static boolean confirming(long since) {
		return since > 0L && Util.getMillis() - since < 4000L;
	}

	private static Component daoLabel(String which, int ordinal) {
		Dao dao = Dao.byOrdinal(ordinal);
		int[] comp = creator().getIntArray("comp").orElse(new int[0]);
		int value = ordinal >= 0 && ordinal < comp.length ? comp[ordinal] : 0;
		return dao == null ? Component.literal("?") : Component.translatable(P + "dao." + which, dao.displayName().copy().withColor(dao.color()), value);
	}

	/** Why "Ciptakan" is not possible now (from the synced numbers; the server checks again), or null. */
	private static Component ready() {
		CompoundTag data = data();
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
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag data = data();
		float scale = w >= 260 ? 0.64F : 0.56F;
		heading(g, Component.translatable(P + "title"), x + 2.0F, y, w - 4.0F, 0.8F);
		if (!data.getBooleanOr("on", true)) {
			Brush.wrap(g, font, Component.translatable("kultivator.batin.off"), x + 4.0F, y + 16.0F + cardH(), w - 8.0F, Palette.INK_SOFT, scale, false);
			return;
		}
		float cursor = y + 14.0F + cardH() + 4.0F + 15.0F;
		CompoundTag creator = creator();
		int ritual = creator.getIntOr("ritual", -1);
		if (ritual >= 0) {
			cursor += Brush.wrap(g, font, Brush.bold(Component.translatable(P + "ritual", Math.min(ritual, Creation.CHANNEL_SECONDS), Creation.CHANNEL_SECONDS)),
					x + 4.0F, cursor, w - 8.0F, Palette.SEAL_DARK, scale + 0.06F, false) + 1.0F;
			float share = Math.min(1.0F, ritual / (float) Creation.CHANNEL_SECONDS);
			Brush.rect(g, x + 4.0F, cursor, x + w - 4.0F, cursor + 3.0F, Palette.argb(0x70, Palette.BAMBOO_KNOT));
			Brush.rect(g, x + 4.0F, cursor, x + 4.0F + (w - 8.0F) * share, cursor + 3.0F, Palette.STORED);
			cursor += 6.0F;
		}
		Component cost = Component.translatable(P + "cost", Creation.PEARL_COST, creator.getIntOr("pearls", 0), Creation.MATERIAL_COST,
				creator.getIntOr("material", 0), Msg.number(creator.getDoubleOr("cost_qi", 0.0D)), Msg.number(creator.getDoubleOr("qi", 0.0D)));
		cursor += Brush.wrap(g, font, cost, x + 4.0F, cursor, w - 8.0F, Palette.INK, scale, false) + 1.0F;
		Component need = daoA == daoB ? Component.translatable(P + "need_pure", (int) Creation.PURE_COMPREHENSION)
				: Component.translatable(P + "need", (int) Creation.MIN_COMPREHENSION);
		cursor += Brush.wrap(g, font, need, x + 4.0F, cursor, w - 8.0F, Palette.INK_SOFT, scale - 0.04F, false) + 1.0F;
		Creation.Form chosenForm = Creation.Form.values()[form];
		double coef = chosenForm.coef() * (daoA == daoB ? Creation.PURE_BONUS : 1.0D);
		cursor += Brush.wrap(g, font, Component.translatable(P + "estimate", ceiling(), String.format(java.util.Locale.ROOT, "%.1f", coef),
				creator.getIntOr("kinds", 0)), x + 4.0F, cursor, w - 8.0F, Palette.GOLD_DARK, scale, false) + 1.0F;
		Component why = ready();
		if (why != null && ritual < 0 && cursor < y + h - 24.0F) {
			Brush.wrap(g, font, why, x + 4.0F, cursor, w - 8.0F, Palette.DANGER, scale - 0.04F, false);
		}
	}

	// ================================================================== the name field

	@Override
	public boolean capturesKeys() {
		return typing;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!typing) {
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			typing = false;
		} else if (key == InputConstants.KEY_BACKSPACE && !name.isEmpty()) {
			name = name.substring(0, name.length() - 1);
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!typing) {
			return false;
		}
		int c = event.codepoint();
		if (name.length() < Creation.NAME_MAX && Character.isBmpCodePoint(c) && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'')) {
			name = name + (char) c;
		}
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean used = super.mouseClicked(mouseX, mouseY, button);
		if (typing && !(widgets.stream().anyMatch(widget -> widget instanceof NameField && widget.contains(mouseX, mouseY)))) {
			typing = false;
		}
		return used;
	}

	/** A strip of paper where the name is written (click to write; Enter or Escape ends). */
	private final class NameField extends Widget {

		NameField(int x, int y, int w, int h) {
			super(x, y, w, h);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			g.fill(x, y, x + w, y + h, typing ? 0xFFF8EED0 : Palette.lighten(0xFFE9DDB6, 0.1F * hover.get()));
			Brush.frame(g, x, y, x + w, y + h, typing ? Palette.SEAL : Palette.GOLD_DARK);
			boolean caret = typing && (Util.getMillis() / 400L) % 2L == 0L;
			Component text = name.isEmpty() && !typing ? Component.translatable(P + "name").withStyle(ChatFormatting.ITALIC) : Component.literal(name + (caret ? "_" : ""));
			Brush.fit(g, font, text, x + 3.0F, y + 3.0F, w - 6.0F, name.isEmpty() && !typing ? Palette.INK_FAINT : Palette.INK, 0.7F, 0.45F, false);
		}

		@Override
		protected void activate() {
			UiSound.press();
			if (action != null) {
				action.run();
			}
		}
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
			Brush.text(g, font, Component.literal(String.valueOf(index + 1)), x0 + 3.0F, y0 + 2.5F, Palette.SEAL, 0.7F, false);
			CompoundTag art = art(index);
			if (!filled(index)) {
				Brush.fit(g, font, Component.translatable(P + "slot.empty_short"), x0 + 10.0F, y0 + 3.0F, w - 13.0F, Palette.INK_FAINT, 0.6F, 0.4F, false);
				return;
			}
			Brush.fit(g, font, Brush.bold(Component.literal(art.getStringOr("name", "?"))), x0 + 10.0F, y0 + 2.5F, w - 13.0F, Palette.SEAL_DARK, 0.66F, 0.4F, false);
			Dao a = Dao.byOrdinal(art.getIntOr("a", 0));
			Dao b = Dao.byOrdinal(art.getIntOr("b", 0));
			float dotY = y0 + h * 0.55F;
			if (a != null) {
				Brush.disc(g, x0 + 6.0F, dotY, 2.5F, 0xFF000000 | a.color());
			}
			if (b != null) {
				Brush.disc(g, x0 + 12.0F, dotY, 2.5F, 0xFF000000 | b.color());
			}
			Creation.Form f = Creation.Form.byOrdinal(art.getIntOr("f", 0));
			if (f != null) {
				Brush.fit(g, font, f.displayName(), x0 + 17.0F, dotY - 2.5F, w - 20.0F, Palette.INK, 0.56F, 0.38F, false);
			}
			int q = art.getIntOr("q", 0);
			float barY = y0 + h - 5.0F;
			Brush.rect(g, x0 + 3.0F, barY, x0 + w - 3.0F, barY + 2.0F, Palette.argb(0x70, Palette.BAMBOO_KNOT));
			Brush.rect(g, x0 + 3.0F, barY, x0 + 3.0F + (w - 6.0F) * q / 100.0F, barY + 2.0F, q >= 100 ? Palette.GOLD_LIGHT : Palette.GOLD);
		}
	}
}
