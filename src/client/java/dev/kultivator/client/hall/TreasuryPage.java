package dev.kultivator.client.hall;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Perbendaharaan tablet (DESIGN 20.3.7-20.3.8). Left: the Kantor Misi, where every stack of the inventory the elder
 * accepts is a card with its value (one click offers it; sect mates donate the same way), with the daily cap bar, and
 * the lost sect arts with the pages carried and "Pulihkan Seni". Right: the Paviliun Yue Lu, its wares with price and
 * lock reason, and the pickers for the disciple (Ceramah Kitab, Manual Tubuh Abadi) and the technique scroll. Both
 * columns scroll with the wheel.
 */
public class TreasuryPage extends HallPage {

	private static final int TOP = 14;
	private static final int CARD_W = 24;
	private static final int CARD_H = 28;
	private static final int ROW_H = 13;
	private static final int BUTTON_H = 11;

	/** One offerable stack (from the last details). */
	private record Offer(int slot, ItemStack stack, double unit) {
	}

	private final Eased leftShown = new Eased(14.0F);
	private final Eased rightShown = new Eased(14.0F);
	private final Map<Widget, Integer> baseY = new HashMap<>();
	private final Map<Widget, Boolean> inRight = new HashMap<>();
	private final List<Offer> offers = new ArrayList<>();
	private String builtKey = "";
	private float leftScroll;
	private float rightScroll;
	private float leftContentH;
	private float rightContentH;
	private int leftW;
	private int regionTop;
	private int cardsTop;
	private int cardCols;
	private int artsTop;
	private int waresTop;
	private int targetIndex;
	private int bookIndex;

	public TreasuryPage(HallScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.treasury");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.treasury.short");
	}

	@Override
	public int icon() {
		return Glyphs.SETTINGS;
	}

	private boolean readonly() {
		return info().getBooleanOr("readonly", false);
	}

	/** Disciples that can receive a sermon or a codex now (loaded, not away). */
	private List<CompoundTag> targets() {
		List<CompoundTag> list = new ArrayList<>();
		for (Tag tag : info().getListOrEmpty("disciples")) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("away", false) && !entry.getBooleanOr("unloaded", false)) {
				list.add(entry);
			}
		}
		return list;
	}

	private @Nullable CompoundTag target() {
		List<CompoundTag> list = targets();
		return list.isEmpty() ? null : list.get(Math.floorMod(targetIndex, list.size()));
	}

	/** The jade slip picked for the Slip Giok Teknik row: every sellable spec id the hall sends in {@code books} (22.8.4). */
	private @Nullable TechniqueSpec book() {
		ListTag books = info().getListOrEmpty("books");
		if (books.isEmpty()) {
			return null;
		}
		return TechniqueRegistry.byId(books.getStringOr(Math.floorMod(bookIndex, books.size()), ""));
	}

	/** The server's {id, price, need, ok} entry of the picked slip, or null. */
	private @Nullable CompoundTag slip() {
		TechniqueSpec book = book();
		CompoundTag ware = ware("technique");
		if (book == null || ware == null) {
			return null;
		}
		for (Tag tag : ware.getListOrEmpty("slips")) {
			if (tag instanceof CompoundTag entry && entry.getStringOr("id", "").equals(book.id())) {
				return entry;
			}
		}
		return null;
	}

	private @Nullable CompoundTag ware(String id) {
		for (Tag tag : info().getListOrEmpty("shop")) {
			if (tag instanceof CompoundTag entry && entry.getStringOr("id", "").equals(id)) {
				return entry;
			}
		}
		return null;
	}

	private String key() {
		StringBuilder key = new StringBuilder(readonly() ? "r" : "o");
		for (Tag tag : info().getListOrEmpty("shop")) {
			if (tag instanceof CompoundTag entry) {
				key.append(entry.getStringOr("id", "")).append(';');
			}
		}
		return key.toString();
	}

	@Override
	public boolean needsRebuild() {
		return !key().equals(builtKey);
	}

	@Override
	public void onInfo() {
		offers.clear();
		for (Tag tag : info().getListOrEmpty("offers")) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			Identifier id = Identifier.tryParse(entry.getStringOr("item", ""));
			Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
			if (item == Items.AIR) {
				continue;
			}
			ItemStack stack = new ItemStack(item, Math.max(1, entry.getIntOr("count", 1)));
			int quality = entry.getIntOr("quality", -1);
			if (quality >= 0) {
				AlchemyComponents.withQuality(stack, PillQuality.byOrdinal(quality));
			}
			offers.add(new Offer(entry.getIntOr("slot", -1), stack, entry.getDoubleOr("unit", 0.0D)));
		}
	}

	private void track(Widget widget, int base, boolean right) {
		add(widget);
		baseY.put(widget, base);
		inRight.put(widget, right);
	}

	@Override
	protected void build() {
		builtKey = key();
		baseY.clear();
		inRight.clear();
		leftW = Math.max(118, Math.min(160, Math.round(w * 0.47F)));
		regionTop = y + TOP;
		cardsTop = regionTop + 22;
		cardCols = Math.max(1, (leftW - 8) / (CARD_W + 2));
		boolean owner = !readonly();

		// The lost arts (positions follow the card grid: placed in drawBehind through baseY + the grid's height).
		artsTop = 0;
		if (owner) {
			for (int set = 1; set <= 3; set++) {
				int art = set;
				TalismanButton restore = new TalismanButton(x + leftW - 46, 0, 42, BUTTON_H, () -> Component.translatable("kultivator.hall.ui.restore"));
				restore.enabledWhen(() -> canRestore(art));
				restore.urgentWhen(() -> canRestore(art));
				restore.onPress(() -> HallClient.send(Halls.OP_RESTORE, String.valueOf(art)));
				restore.tooltip(() -> artTip(art));
				track(restore, set, false);
			}
		}

		// The Paviliun: the two pickers, then one row per ware.
		int rx = x + leftW + 4;
		int rw = x + w - rx;
		int cursor = regionTop + 12;
		if (owner) {
			TalismanButton pickTarget = new TalismanButton(rx + 2, cursor, rw - 4, BUTTON_H, () -> {
				CompoundTag target = target();
				return Component.translatable("kultivator.hall.ui.for_disciple", target == null ? Component.translatable("kultivator.hall.ui.nobody")
						: HallUi.name(target, "name"));
			});
			pickTarget.enabledWhen(() -> targets().size() > 1);
			pickTarget.onPress(() -> targetIndex++);
			pickTarget.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.for_disciple.tip")));
			track(pickTarget, cursor, true);
			cursor += BUTTON_H + 2;
			TalismanButton pickBook = new TalismanButton(rx + 2, cursor, rw - 4, BUTTON_H, () -> {
				TechniqueSpec book = book();
				return Component.translatable("kultivator.hall.ui.for_book", book == null ? Component.literal("-") : book.displayName());
			});
			pickBook.enabledWhen(() -> info().getListOrEmpty("books").size() > 1);
			pickBook.onPress(() -> bookIndex++);
			pickBook.tooltip(() -> List.of(Component.translatable("kultivator.hall.ui.for_book.tip")));
			track(pickBook, cursor, true);
			cursor += BUTTON_H + 3;
		}
		waresTop = cursor;
		for (Tag tag : info().getListOrEmpty("shop")) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			String id = entry.getStringOr("id", "");
			if (owner) {
				TalismanButton buy = new TalismanButton(x + w - 34, cursor + 1, 32, BUTTON_H, () -> Component.translatable("kultivator.hall.ui.buy"));
				buy.enabledWhen(() -> canBuy(id));
				buy.onPress(() -> HallClient.send(Halls.OP_BUY, id + ":" + param(id)));
				buy.tooltip(() -> wareTip(id));
				track(buy, cursor + 1, true);
			}
			cursor += ROW_H;
		}
		rightContentH = cursor - regionTop + 4;
	}

	private String param(String id) {
		CompoundTag ware = ware(id);
		if (ware != null && ware.getBooleanOr("target", false)) {
			CompoundTag target = target();
			return target == null ? "" : target.getStringOr("soul", "");
		}
		if ("technique".equals(id)) {
			TechniqueSpec book = book();
			return book == null ? "" : book.id();
		}
		return "";
	}

	private boolean canBuy(String id) {
		CompoundTag ware = ware(id);
		if (ware == null || !ware.getBooleanOr("ok", false)) {
			return false;
		}
		if (ware.getBooleanOr("target", false) && target() == null) {
			return false;
		}
		if (!"technique".equals(id)) {
			return true;
		}
		CompoundTag slip = slip();
		return book() != null && (slip == null || slip.getBooleanOr("ok", false));
	}

	private boolean canRestore(int set) {
		CompoundTag info = info();
		int[] pages = info.getIntArray("pages").orElse(new int[3]);
		boolean restored = (info.getIntOr("arts", 0) & 1 << (set - 1)) != 0;
		return !restored && info.getBooleanOr("pavilion", false) && set - 1 < pages.length && pages[set - 1] == 0x1F;
	}

	private List<Component> wareTip(String id) {
		List<Component> lines = new ArrayList<>();
		CompoundTag ware = ware(id);
		lines.add(Component.translatable("kultivator.hall.ware." + id));
		lines.add(Component.translatable("kultivator.hall.ware." + id + ".desc").withStyle(ChatFormatting.GRAY));
		if (ware == null) {
			return lines;
		}
		lines.add(Component.translatable("kultivator.hall.ui.price_pk", ware.getIntOr("price", 0), info().getIntOr("pool", 0)).withStyle(ChatFormatting.GOLD));
		if (ware.getBooleanOr("locked", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.needs_level", ware.getIntOr("need", 1)).withStyle(ChatFormatting.RED));
		} else if (ware.getBooleanOr("used_today", false)) {
			lines.add(Component.translatable("kultivator.hall.ui.used_today").withStyle(ChatFormatting.RED));
		} else if (ware.getIntOr("price", 0) > info().getIntOr("pool", 0)) {
			lines.add(Component.translatable("kultivator.hall.ui.pool_short").withStyle(ChatFormatting.RED));
		}
		if (ware.getBooleanOr("target", false)) {
			CompoundTag target = target();
			lines.add(target == null ? Component.translatable("kultivator.hall.ui.need_target").withStyle(ChatFormatting.RED)
					: Component.translatable("kultivator.hall.ui.for_disciple", HallUi.name(target, "name")).withStyle(ChatFormatting.DARK_AQUA));
		}
		if ("technique".equals(id)) {
			TechniqueSpec book = book();
			if (book != null) {
				lines.add(Component.translatable("kultivator.hall.ui.for_book", book.displayName()).withStyle(ChatFormatting.DARK_AQUA));
				CompoundTag slip = slip();
				if (slip != null) {
					lines.add(Component.translatable("kultivator.hall.ui.price_pk", slip.getIntOr("price", 0), info().getIntOr("pool", 0))
							.withStyle(ChatFormatting.GOLD));
					if (slip.getIntOr("need", 1) > info().getIntOr("level", 99)) {
						lines.add(Component.translatable("kultivator.hall.ui.needs_level", slip.getIntOr("need", 1)).withStyle(ChatFormatting.RED));
					}
				}
			}
		}
		lines.add(Component.translatable("kultivator.hall.ui.paviliun.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	private List<Component> artTip(int set) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.hall.art." + set));
		lines.add(Component.translatable("kultivator.hall.art.effect." + set).withStyle(ChatFormatting.GRAY));
		CompoundTag info = info();
		if ((info.getIntOr("arts", 0) & 1 << (set - 1)) != 0) {
			lines.add(Component.translatable("kultivator.hall.ui.art_restored").withStyle(ChatFormatting.GREEN));
		} else {
			int[] pages = info.getIntArray("pages").orElse(new int[3]);
			int bits = set - 1 < pages.length ? pages[set - 1] : 0;
			lines.add(Component.translatable("kultivator.hall.ui.art_pages", Integer.bitCount(bits), 5).withStyle(bits == 0x1F ? ChatFormatting.GREEN
					: ChatFormatting.YELLOW));
			if (!info.getBooleanOr("pavilion", false)) {
				lines.add(Component.translatable("kultivator.hall.art.need_pavilion").withStyle(ChatFormatting.RED));
			}
			lines.add(Component.translatable("kultivator.hall.ui.art_reward").withStyle(ChatFormatting.GOLD));
		}
		lines.add(Component.translatable("kultivator.hall.art.source." + set).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return lines;
	}

	// ================================================================== scrolling regions

	@Override
	protected boolean visible(Widget widget) {
		Integer base = baseY.get(widget);
		if (base == null) {
			return true;
		}
		return widget.y + widget.h > regionTop && widget.y < y + h;
	}

	@Override
	protected int[] clipFor(Widget widget) {
		Boolean right = inRight.get(widget);
		if (right == null) {
			return null;
		}
		return right ? new int[]{x + leftW + 1, regionTop, x + w, y + h} : new int[]{x, regionTop, x + leftW - 1, y + h};
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseY < regionTop && mouseY >= y) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT && mouseX < x + leftW) {
			int index = cardAt(mouseX, mouseY);
			if (index >= 0) {
				UiSound.press();
				HallClient.send(Halls.OP_OFFER, String.valueOf(offers.get(index).slot()));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** The offer card under the mouse, or -1. */
	private int cardAt(double mouseX, double mouseY) {
		if (mouseY < cardsTop - Math.round(leftShown.get()) || mouseY < regionTop || mouseY >= y + h) {
			return -1;
		}
		int offset = Math.round(leftShown.get());
		for (int i = 0; i < offers.size(); i++) {
			int cx = x + 2 + (i % cardCols) * (CARD_W + 2);
			int cy = cardsTop + (i / cardCols) * (CARD_H + 2) - offset;
			if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!inside(mouseX, mouseY) || mouseY < regionTop) {
			return false;
		}
		if (mouseX < x + leftW) {
			leftScroll -= (float) amount * 16.0F;
		} else {
			rightScroll -= (float) amount * 16.0F;
		}
		return true;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag info = info();
		// Top: the pool and today's offerings against the cap.
		int pool = info.getIntOr("pool", 0);
		// S28: the pool's PK in and out over the last 7 days, beside the balance
		int[] week = info.getIntArray("pool_week").orElse(new int[0]);
		Component poolLine = Brush.bold(Component.translatable("kultivator.hall.ui.pool", pool));
		if (week.length >= 2) {
			poolLine = Component.empty().append(poolLine).append(Component.literal("  "))
					.append(Component.translatable("kultivator.sekte_sambung.kas.minggu", week[0], week[1]).withStyle(ChatFormatting.DARK_GRAY));
		}
		Brush.fit(g, font, poolLine, x + 2.0F, y + 1.0F, leftW - 6.0F, Palette.GOLD_DARK, 0.75F, 0.45F, false);
		int offered = info.getIntOr("offered_today", 0);
		int cap = Math.max(1, info.getIntOr("offer_cap", 300));
		HallUi.bar(g, font, Component.translatable("kultivator.hall.ui.offered"), Component.literal(offered + "/" + cap), offered / (float) cap,
				offered >= cap ? Palette.WARN : Palette.JADE, Palette.JADE_LIGHT, x + leftW + 4.0F, y - 1.0F, x + w - 2.0F, 0.5F);
		Brush.rect(g, x, regionTop - 2.0F, x + w, regionTop - 1.4F, Palette.argb(0x90, Palette.GOLD));

		drawLeft(g, info, mouseX, mouseY, seconds);
		drawRight(g, info, mouseX, mouseY, seconds);
	}

	/** The Kantor Misi cards (their items are drawn above, on their own layer) and the lost arts. */
	private void drawLeft(GuiGraphicsExtractor g, CompoundTag info, int mouseX, int mouseY, float seconds) {
		float maxScroll = Math.max(0.0F, leftContentH - (y + h - regionTop));
		leftScroll = Math.max(0.0F, Math.min(maxScroll, leftScroll));
		int offset = Math.round(leftShown.to(leftScroll, seconds));
		g.enableScissor(x, regionTop, x + leftW - 1, y + h);
		float tx = x + 2.0F;
		float tw = leftW - 8.0F;
		float cursor = regionTop - offset;
		heading(g, Component.translatable("kultivator.hall.ui.kantor"), tx, cursor, tw, 0.72F);
		Brush.fit(g, font, Component.translatable("kultivator.hall.ui.kantor.hint"), tx, cursor + 11.0F, tw, Palette.INK_SOFT, 0.5F, 0.35F, false);
		int hovered = inside(mouseX, mouseY) ? cardAt(mouseX, mouseY) : -1;
		for (int i = 0; i < offers.size(); i++) {
			Offer offer = offers.get(i);
			int cx = x + 2 + (i % cardCols) * (CARD_W + 2);
			int cy = cardsTop + (i / cardCols) * (CARD_H + 2) - offset;
			boolean over = i == hovered;
			Brush.chamferGradient(g, cx, cy, cx + CARD_W, cy + CARD_H, 2, over ? 0xFFFBF2D8 : 0xFFF1E6C6, 0xFFE0D0A6);
			Brush.frame(g, cx, cy, cx + CARD_W, cy + CARD_H, Palette.argb(over ? 0xF0 : 0xA0, Palette.GOLD));
			int value = (int) Math.floor(offer.unit() * offer.stack().getCount() + 1.0E-6D);
			Brush.fitCentered(g, font, Component.literal("+" + value), cx + CARD_W / 2.0F, cy + CARD_H - 8.0F, CARD_W - 2.0F, Palette.JADE_DARK, 0.5F,
					0.35F, false);
		}
		if (offers.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.kantor.empty"), tx + 2.0F, cardsTop - offset + 2.0F, tw - 2.0F,
					Palette.INK_FAINT, 0.55F, false);
		}
		int rows = Math.max(1, (offers.size() + cardCols - 1) / cardCols);
		artsTop = cardsTop + rows * (CARD_H + 2) + 4;
		cursor = artsTop - offset;
		heading(g, Component.translatable("kultivator.hall.ui.arts"), tx, cursor, tw, 0.72F);
		cursor += 12.0F;
		int[] pages = info.getIntArray("pages").orElse(new int[3]);
		int arts = info.getIntOr("arts", 0);
		for (int set = 1; set <= 3; set++) {
			int rowY = Math.round(cursor);
			boolean restored = (arts & 1 << (set - 1)) != 0;
			Brush.fit(g, font, Component.translatable("kultivator.hall.art." + set), tx, rowY, tw - 48.0F, restored ? Palette.JADE_DARK : Palette.INK,
					0.52F, 0.35F, false);
			int bits = set - 1 < pages.length ? pages[set - 1] : 0;
			for (int piece = 0; piece < 5; piece++) {
				float px = tx + piece * 6.0F;
				boolean have = restored || (bits & 1 << piece) != 0;
				Brush.rect(g, px, rowY + 6.0F, px + 4.5F, rowY + 10.0F, have ? Palette.argb(0xFF, 0xC27AE0) : Palette.argb(0x50, Palette.INK_FAINT));
			}
			if (restored) {
				Brush.fit(g, font, Component.translatable("kultivator.hall.ui.art_restored"), x + leftW - 46.0F, rowY + 2.0F, 42.0F, Palette.JADE_DARK,
						0.5F, 0.35F, false);
			}
			if (mouseX >= tx && mouseX < x + leftW - 48 && mouseY >= rowY && mouseY < rowY + ROW_H && mouseY >= regionTop && inside(mouseX, mouseY)) {
				screen.showTooltip(artTip(set));
			}
			cursor += ROW_H;
		}
		if (!info.getBooleanOr("pavilion", false)) {
			cursor += Brush.wrap(g, font, Component.translatable("kultivator.hall.art.need_pavilion"), tx, cursor, tw, Palette.INK_FAINT, 0.5F, false);
		}
		leftContentH = cursor + offset - regionTop + 4.0F;
		g.disableScissor();
		// The restore talismans sit on their art rows.
		for (Map.Entry<Widget, Integer> entry : baseY.entrySet()) {
			if (!inRight.getOrDefault(entry.getKey(), false)) {
				int set = entry.getValue();
				boolean restored = (arts & 1 << (set - 1)) != 0;
				entry.getKey().y = restored ? -1000 : artsTop + 12 + (set - 1) * ROW_H - offset;
			}
		}
		if (maxScroll > 0.0F) {
			float bead = regionTop + 3.0F + (y + h - regionTop - 6.0F) * (offset / maxScroll);
			g.fill(x + leftW - 2, regionTop, x + leftW - 1, y + h, Palette.CORD);
			Brush.disc(g, x + leftW - 1.5F, bead, 2.0F, Palette.JADE);
		}
		if (hovered >= 0) {
			Offer offer = offers.get(hovered);
			int value = (int) Math.floor(offer.unit() * offer.stack().getCount() + 1.0E-6D);
			screen.showTooltip(List.of(offer.stack().getHoverName().copy().append(Component.literal(" ×" + offer.stack().getCount())),
					Component.translatable("kultivator.hall.ui.offer_value", formatUnit(offer.unit()), value).withStyle(ChatFormatting.AQUA),
					Component.translatable(readonly() ? "kultivator.hall.ui.offer_donate" : "kultivator.hall.ui.offer_click").withStyle(ChatFormatting.GRAY),
					Component.translatable("kultivator.hall.ui.kantor.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
	}

	private static String formatUnit(double unit) {
		return unit == Math.floor(unit) ? String.valueOf((int) unit) : String.format(java.util.Locale.ROOT, "%.1f", unit);
	}

	/** The Paviliun Yue Lu: one row per ware with its price, or the hall level it waits for. */
	private void drawRight(GuiGraphicsExtractor g, CompoundTag info, int mouseX, int mouseY, float seconds) {
		int rx = x + leftW + 4;
		int x1 = x + w;
		panel(g, rx - 2, regionTop - 1, x1, y + h);
		float maxScroll = Math.max(0.0F, rightContentH - (y + h - regionTop));
		rightScroll = Math.max(0.0F, Math.min(maxScroll, rightScroll));
		int offset = Math.round(rightShown.to(rightScroll, seconds));
		for (Map.Entry<Widget, Integer> entry : baseY.entrySet()) {
			if (inRight.getOrDefault(entry.getKey(), false)) {
				entry.getKey().y = entry.getValue() - offset;
			}
		}
		g.enableScissor(rx - 1, regionTop, x1 - 1, y + h);
		float tx = rx + 2.0F;
		float tw = x1 - rx - 4.0F;
		heading(g, Component.translatable("kultivator.hall.ui.paviliun"), tx, regionTop + 1.0F - offset, tw, 0.72F);
		int cursor = waresTop;
		boolean owner = !readonly();
		for (Tag tag : info.getListOrEmpty("shop")) {
			if (!(tag instanceof CompoundTag ware)) {
				continue;
			}
			String id = ware.getStringOr("id", "");
			int rowY = cursor - offset;
			boolean locked = ware.getBooleanOr("locked", false);
			float nameW = tw - (owner ? 36.0F : 0.0F) - 30.0F;
			Brush.fit(g, font, Component.translatable("kultivator.hall.ware." + id), tx, rowY + 3.0F, nameW, locked ? Palette.INK_FAINT : Palette.INK, 0.52F,
					0.35F, false);
			Component price = locked ? Component.translatable("kultivator.hall.ui.level_short", ware.getIntOr("need", 1))
					: Component.literal(ware.getIntOr("price", 0) + " PK");
			Brush.fit(g, font, price, tx + nameW + 2.0F, rowY + 3.0F, 28.0F, locked ? Palette.SEAL_DARK : Palette.GOLD_DARK, 0.5F, 0.35F, false);
			Brush.rect(g, tx, rowY + ROW_H - 0.5F, x1 - 3.0F, rowY + ROW_H, Palette.argb(0x40, Palette.GOLD_DARK));
			if (!owner && mouseX >= tx && mouseX < x1 && mouseY >= rowY && mouseY < rowY + ROW_H && mouseY >= regionTop && inside(mouseX, mouseY)) {
				screen.showTooltip(wareTip(id));
			}
			cursor += ROW_H;
		}
		if (!owner) {
			Brush.wrap(g, font, Component.translatable("kultivator.hall.ui.shop_readonly"), tx, cursor - offset + 3.0F, tw, Palette.INK_SOFT, 0.5F, false);
		}
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = regionTop + 3.0F + (y + h - regionTop - 6.0F) * (offset / maxScroll);
			g.fill(x1 - 2, regionTop, x1 - 1, y + h, Palette.CORD);
			Brush.disc(g, x1 - 1.5F, bead, 2.0F, Palette.JADE);
		}
	}

	/** The offered stacks' items, on their own layer above the cards. */
	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (offers.isEmpty()) {
			return;
		}
		g.nextStratum();
		g.enableScissor(x, regionTop, x + leftW - 1, y + h);
		int offset = Math.round(leftShown.get());
		for (int i = 0; i < offers.size(); i++) {
			int cx = x + 2 + (i % cardCols) * (CARD_W + 2);
			int cy = cardsTop + (i / cardCols) * (CARD_H + 2) - offset;
			if (cy + CARD_H < regionTop || cy > y + h) {
				continue;
			}
			ItemStack stack = offers.get(i).stack();
			g.item(stack, cx + (CARD_W - 16) / 2, cy + 2);
			g.itemDecorations(font, stack, cx + (CARD_W - 16) / 2, cy + 2);
		}
		g.disableScissor();
	}
}
