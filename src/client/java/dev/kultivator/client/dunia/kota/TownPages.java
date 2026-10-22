package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.NpcRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The five tablets of the town screen (DESIGN 34.5.1): Toko (the pill or treasure pavilion's wares), Lelang (the dusk
 * auction), Kedai Teh (teas, Kabar Angin, the token), Pasar Gelap (the broker, the raid and its bribe) and Papan Misi
 * (the day's bounties). Each reads its part of {@link KotaClient#data()}; a service that belongs to another NPC shows
 * where to find it.
 */
final class TownPages {

	static NpcRole role() {
		return NpcRole.byOrdinal(KotaClient.data().getIntOr("role", 0));
	}

	// ================================================================== Toko

	static final class Toko extends KotaPage {
		private static final int TOP = 24;
		private static final int CARD_H = 20;

		Toko(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.toko");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.toko.short");
		}

		@Override
		public int icon() {
			return Glyphs.ALKIMIA;
		}

		private long built = -1L;

		@Override
		public void onData() {
			if (built != KotaClient.version()) {
				layout(x, y, w, h);
			}
		}

		@Override
		protected void build() {
			built = KotaClient.version();
			CompoundTag toko = tab("toko");
			ListTag offers = toko.getListOrEmpty("offers");
			int columns = w >= 240 ? 2 : 1;
			int cw = (w - 4 - (columns - 1) * 4) / columns;
			for (int i = 0; i < offers.size() && i < 10; i++) {
				if (!(offers.get(i) instanceof CompoundTag offer)) {
					continue;
				}
				int col = i % columns;
				int row = i / columns;
				int cx = x + 2 + col * (cw + 4);
				int cy = y + TOP + row * (CARD_H + 3);
				if (cy + CARD_H > y + h - 22) {
					break;
				}
				int index = offer.getIntOr("i", -1);
				long price = offer.getLongOr("price", 0L);
				int stock = offer.getIntOr("stock", 0);
				Card card = add(new Card(cx, cy, cw, CARD_H, () -> icon(offer.getStringOr("item", "")), () -> {
					Component name = read(offer, "name");
					int count = offer.getIntOr("count", 1);
					return count > 1 ? Component.translatable("kultivator.kota.layar.kali", name, count) : name;
				}, () -> Component.translatable("kultivator.kota.toko.baris", price, stock), () -> false));
				card.enabledWhen(() -> stock > 0 && all().getLongOr("nb", 0L) >= price);
				card.onPress(() -> act("beli:" + index + ":" + price));
				card.tooltip(() -> lines(read(offer, "name"), Component.translatable("kultivator.kota.toko.tip_beli", price, stock)));
			}
			int bw = Math.min(110, w / 2);
			TalismanButton sell = add(new TalismanButton(x + w - bw - 2, y + h - 18, bw, 14, () -> Component.translatable("kultivator.kota.toko.jual")));
			sell.enabledWhen(() -> tab("toko").contains("hand") && tab("toko").getIntOr("sales_left", 0) > 0);
			sell.onPress(() -> act("jual"));
			sell.tooltip(() -> lines(Component.translatable("kultivator.kota.toko.tip_jual", tab("toko").getIntOr("sales_left", 0))));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag toko = tab("toko");
			Component heading = toko.isEmpty() ? title() : Component.translatable("kultivator.kota.toko.judul." + toko.getStringOr("kind", "pil"),
					read(all(), "town"));
			heading(g, heading, x + 2.0F, y, w - 4.0F, 0.8F);
			purse(g, y + 13.0F);
			if (toko.isEmpty()) {
				elsewhere(g, Component.translatable("kultivator.kota.toko.kunjungi"), y + TOP);
				return;
			}
			if (toko.getListOrEmpty("offers").isEmpty()) {
				elsewhere(g, Component.translatable("kultivator.kota.toko.kosong"), y + TOP);
			}
			Component hand = toko.contains("hand")
					? Component.translatable("kultivator.kota.toko.tangan", read(toko, "hand"), toko.getIntOr("hand_count", 1), toko.getLongOr("hand_price", 0L))
					: Component.translatable("kultivator.kota.toko.tangan_kosong");
			Brush.fit(g, font, hand, x + 4.0F, y + h - 15.0F, w - Math.min(110, w / 2) - 10.0F, Palette.INK_SOFT, 0.62F, 0.4F, false);
		}
	}

	// ================================================================== Lelang

	static final class Lelang extends KotaPage {
		private static final int[] STEPS = {5, 10, 25};
		private static final int ROW = 15;
		private static final int TOP = 24;

		Lelang(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.lelang");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.lelang.short");
		}

		@Override
		public int icon() {
			return Glyphs.TREASURE;
		}

		private static CompoundTag lot() {
			CompoundTag lelang = tab("lelang");
			ListTag lots = lelang.getListOrEmpty("lots");
			int current = lelang.getIntOr("current", -1);
			return current >= 0 && current < lots.size() && lots.get(current) instanceof CompoundTag lot ? lot : new CompoundTag();
		}

		private static boolean canBid(int step) {
			CompoundTag lelang = tab("lelang");
			CompoundTag lot = lot();
			return role() == NpcRole.JURU_LELANG && lelang.getBooleanOr("active", false) && lot.getIntOr("state", 0) == 1
					&& lot.getLongOr("present", 0L) <= 0L && !lot.getBooleanOr("mine", false)
					&& all().getLongOr("nb", 0L) >= lot.getLongOr("offer" + step, Long.MAX_VALUE);
		}

		private int buttonsY() {
			return y + TOP + ROW * 5 + 3;
		}

		@Override
		protected void build() {
			int bw = Math.max(36, Math.min(64, (w - 16) / 4));
			for (int i = 0; i < STEPS.length; i++) {
				int step = STEPS[i];
				TalismanButton button = add(new TalismanButton(x + 4 + i * (bw + 4), buttonsY(), bw, 14,
						() -> Component.translatable("kultivator.kota.lelang.langkah", step, lot().getLongOr("offer" + step, 0L))));
				button.enabledWhen(() -> canBid(step));
				button.onPress(() -> act("tawar:" + tab("lelang").getIntOr("current", -1) + ":" + lot().getLongOr("price", 0L) + ":" + step));
				button.tooltip(() -> lines(Component.translatable("kultivator.kota.lelang.tip_tawar", lot().getLongOr("offer" + step, 0L),
						all().getLongOr("nb", 0L))));
			}
			int cw = Math.min(120, w - 8);
			TalismanButton consign = add(new TalismanButton(x + w - cw - 4, y + h - 18, cw, 14, () -> Component.translatable("kultivator.kota.lelang.titip")));
			consign.enabledWhen(() -> role() == NpcRole.JURU_LELANG && tab("lelang").getBooleanOr("active", false));
			consign.onPress(() -> act("titip"));
			consign.tooltip(() -> lines(Component.translatable("kultivator.kota.lelang.tip_titip", tab("lelang").getIntOr("consign_max", 2))));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag lelang = tab("lelang");
			heading(g, Component.translatable("kultivator.kota.lelang.judul", read(all(), "town")), x + 2.0F, y, w - 4.0F, 0.8F);
			Component status;
			if (!lelang.getBooleanOr("enabled", true)) {
				status = Component.translatable("kultivator.kota.lelang.mati");
			} else if (lelang.getBooleanOr("active", false)) {
				status = Component.translatable("kultivator.kota.lelang.berjalan");
			} else if (lelang.getBooleanOr("done_today", false)) {
				status = Component.translatable("kultivator.kota.lelang.sudah_hari_ini");
			} else {
				status = Component.translatable("kultivator.kota.lelang.berikut", clock(lelang.getLongOr("next", 0L)));
			}
			if (role() != NpcRole.JURU_LELANG) {
				status = Component.translatable("kultivator.kota.layar.sep", status, Component.translatable("kultivator.kota.lelang.di_panggung"));
			}
			Brush.fit(g, font, status, x + 4.0F, y + 13.0F, w - 8.0F, Palette.INK_SOFT, 0.68F, 0.45F, false);
			ListTag lots = lelang.getListOrEmpty("lots");
			int current = lelang.getIntOr("current", -1);
			if (lots.isEmpty()) {
				panel(g, x + 2, y + TOP, x + w - 2, y + TOP + ROW * 5);
				Brush.wrap(g, font, Component.translatable("kultivator.kota.lelang.kosong"), x + 8.0F, y + TOP + 4.0F, w - 16.0F, Palette.INK_FAINT, 0.7F, false);
			}
			for (int i = 0; i < lots.size() && i < 5; i++) {
				if (lots.get(i) instanceof CompoundTag lot) {
					drawLot(g, i, lot, i == current);
				}
			}
			drawBidders(g, lelang);
		}

		private void drawLot(GuiGraphicsExtractor g, int index, CompoundTag lot, boolean onBlock) {
			int ry = y + TOP + index * ROW;
			int state = lot.getIntOr("state", 0);
			if (onBlock && state == 1) {
				g.fill(x + 2, ry, x + w - 2, ry + ROW - 1, Palette.argb(0x50, Palette.GOLD_LIGHT));
				Brush.frame(g, x + 2, ry, x + w - 2, ry + ROW - 1, Palette.argb(0xE0, Palette.GOLD));
			} else {
				g.fill(x + 2, ry, x + w - 2, ry + ROW - 1, Palette.argb(0x30, Palette.PAPER_DEEP));
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 4.0F, ry + 1.0F);
			g.pose().scale(0.75F, 0.75F);
			g.item(icon(lot.getStringOr("item", "")), 0, 0);
			g.pose().popMatrix();
			Component name = read(lot, "name");
			Brush.fit(g, font, Component.literal((index + 1) + ". ").append(name), x + 18.0F, ry + 1.0F, w * 0.5F, state == 3 ? Palette.INK_FAINT : Palette.INK,
					0.7F, 0.42F, false);
			Brush.fit(g, font, Component.translatable("kultivator.kota.lelang.harga", lot.getLongOr("price", 0L), lot.getLongOr("base", 0L)),
					x + 18.0F + w * 0.52F, ry + 1.0F, w * 0.44F - 18.0F, Palette.GOLD_DARK, 0.66F, 0.42F, false);
			Component leader = read(lot, "leader");
			Component line = switch (state) {
				case 2 -> Component.translatable("kultivator.kota.lelang.terjual", leader);
				case 3 -> Component.translatable("kultivator.kota.lelang.tak_laku");
				case 1 -> {
					long present = lot.getLongOr("present", 0L);
					long sell = lot.getLongOr("sell", -1L);
					if (present > 0L) {
						yield Component.translatable("kultivator.kota.lelang.dipamerkan", clock(present));
					} else if (sell >= 0L) {
						yield Component.translatable("kultivator.kota.lelang.memimpin", leader, clock(sell));
					}
					yield Component.translatable("kultivator.kota.lelang.dibuka");
				}
				default -> Component.translatable("kultivator.kota.lelang.menunggu");
			};
			int color = lot.getBooleanOr("mine", false) ? Palette.JADE_DARK : state == 2 ? Palette.SEAL_DARK : Palette.INK_SOFT;
			Brush.fit(g, font, line, x + 18.0F, ry + 8.0F, w - 24.0F, color, 0.58F, 0.4F, false);
		}

		private void drawBidders(GuiGraphicsExtractor g, CompoundTag lelang) {
			float ly = buttonsY() + 18.0F;
			float bottom = y + h - 20.0F;
			ListTag bidders = lelang.getListOrEmpty("bidders");
			if (bidders.isEmpty() || ly > bottom - 8.0F) {
				return;
			}
			Brush.fit(g, font, Component.translatable("kultivator.kota.lelang.penawar"), x + 4.0F, ly, w - 8.0F, Palette.SEAL_DARK, 0.66F, 0.42F, false);
			ly += 8.0F;
			for (int i = 0; i < bidders.size() && ly <= bottom - 6.0F; i++) {
				if (bidders.get(i) instanceof CompoundTag bidder) {
					Brush.rect(g, x + 6.0F, ly + 1.5F, x + 9.0F, ly + 4.5F, Palette.opaque(bidder.getIntOr("color", 0x808080)));
					Brush.fit(g, font, read(bidder, "name"), x + 12.0F, ly, w - 16.0F, Palette.INK, 0.6F, 0.4F, false);
					ly += 7.0F;
				}
			}
		}
	}

	// ================================================================== Kedai Teh

	static final class Teh extends KotaPage {
		private static final String[] TEAS = {"embun", "awan", "penenang"};
		private static final String[] KINDS = {"kota", "reruntuhan", "penjara", "menara", "bawah", "laut"};
		private int kind;

		Teh(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.teh");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.teh.short");
		}

		@Override
		public int icon() {
			return Glyphs.PATH;
		}

		private static boolean master() {
			return role() == NpcRole.PEMILIK_TEH;
		}

		private static CompoundTag tea(int i) {
			ListTag teas = tab("teh").getListOrEmpty("teas");
			return i < teas.size() && teas.get(i) instanceof CompoundTag tag ? tag : new CompoundTag();
		}

		@Override
		protected void build() {
			int rowY = y + 24;
			int bw = Math.max(40, Math.min(58, (w - 120) / 2));
			for (int i = 0; i < 3; i++) {
				int index = i;
				int ry = rowY + i * 17;
				TalismanButton drink = add(new TalismanButton(x + w - 2 * bw - 8, ry, bw, 14, () -> Component.translatable("kultivator.kota.teh.minum_tombol")));
				drink.enabledWhen(() -> master() && tea(index).getBooleanOr("open", false) && all().getLongOr("nb", 0L) >= tea(index).getLongOr("price", 1L));
				drink.onPress(() -> act("teh:" + index));
				drink.tooltip(() -> lines(Component.translatable("kultivator.kota.teh.efek." + TEAS[index], Math.round(tab("teh").getDoubleOr("bonus", 0.1D) * 100.0D)),
						Component.translatable("kultivator.kota.teh.tip_harga", tea(index).getLongOr("price", 0L))));
				TalismanButton wrap = add(new TalismanButton(x + w - bw - 4, ry, bw, 14, () -> Component.translatable("kultivator.kota.teh.bungkus")));
				wrap.enabledWhen(() -> master() && tea(index).getBooleanOr("open", false) && all().getLongOr("nb", 0L) >= tea(index).getLongOr("price", 1L));
				wrap.onPress(() -> act("beliteh:" + index));
				wrap.tooltip(() -> lines(Component.translatable("kultivator.kota.teh.tip_bungkus")));
			}
			int coinY = rowY + 3 * 17 + 14;
			int coinW = Math.max(40, (w - 8) / 3);
			for (int i = 0; i < KINDS.length; i++) {
				int index = i;
				int col = i % 3;
				int row = i / 3;
				JadeCoin coin = add(new JadeCoin(x + 4 + col * coinW, coinY + row * 13, coinW - 4, 11,
						() -> Component.translatable("kultivator.kota.kabar.jenis." + KINDS[index]), () -> kind == index));
				coin.onPress(() -> kind = index);
			}
			int by = coinY + 28;
			int rw = Math.max(50, (w - 16) / 3);
			for (int b = 0; b < 3; b++) {
				int band = b;
				TalismanButton rumour = add(new TalismanButton(x + 4 + b * (rw + 4), by, rw, 14, () -> {
					ListTag rumours = tab("teh").getListOrEmpty("rumours");
					CompoundTag entry = band < rumours.size() && rumours.get(band) instanceof CompoundTag t ? t : new CompoundTag();
					return Component.translatable("kultivator.kota.kabar.tombol", entry.getIntOr("radius", 0), entry.getLongOr("price", 0L));
				}));
				rumour.enabledWhen(() -> master() && tab("teh").getLongOr("cooldown", 0L) <= 0L);
				rumour.onPress(() -> act("kabar:" + kind + ":" + band));
				rumour.tooltip(() -> lines(Component.translatable("kultivator.kota.kabar.tip")));
			}
			int tw = Math.min(140, w - 8);
			TalismanButton token = add(new TalismanButton(x + w - tw - 4, y + h - 18, tw, 14,
					() -> Component.translatable("kultivator.kota.teh.token", tab("teh").getLongOr("token_price", 60L))));
			token.enabledWhen(() -> master() && tab("teh").getBooleanOr("token", false));
			token.tooltip(() -> lines(Component.translatable("kultivator.kota.teh.tip_token", tab("teh").getIntOr("heard", 0))));
			token.onPress(() -> act("token"));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag teh = tab("teh");
			heading(g, Component.translatable("kultivator.kota.teh.judul"), x + 2.0F, y, w - 4.0F, 0.8F);
			purse(g, y + 13.0F);
			int rowY = y + 24;
			int active = teh.getIntOr("active", -1);
			for (int i = 0; i < 3; i++) {
				int ry = rowY + i * 17;
				CompoundTag tea = tea(i);
				boolean on = active == i;
				g.fill(x + 2, ry - 1, x + w - 2, ry + 15, Palette.argb(on ? 0x60 : 0x28, on ? Palette.JADE_LIGHT : Palette.PAPER_DEEP));
				g.pose().pushMatrix();
				g.pose().translate(x + 4.0F, ry);
				g.pose().scale(0.8F, 0.8F);
				g.item(icon("kultivator:teh_" + (i == 0 ? "embun_pagi" : i == 1 ? "awan_ungu" : "penenang_hati")), 0, 0);
				g.pose().popMatrix();
				Component name = Component.translatable("item.kultivator.teh_" + (i == 0 ? "embun_pagi" : i == 1 ? "awan_ungu" : "penenang_hati"));
				Brush.fit(g, font, name, x + 20.0F, ry, w * 0.4F, tea.getBooleanOr("open", false) ? Palette.INK : Palette.INK_FAINT, 0.7F, 0.42F, false);
				Component sub = tea.getBooleanOr("open", false)
						? Component.translatable("kultivator.kota.teh.harga", tea.getLongOr("price", 0L))
						: Component.translatable("kultivator.kota.teh.terkunci", Realm.byOrdinal(tea.getIntOr("gate", 0)).displayName());
				Brush.fit(g, font, sub, x + 20.0F, ry + 7.5F, w * 0.4F, Palette.GOLD_DARK, 0.58F, 0.4F, false);
			}
			int coinY = rowY + 3 * 17 + 3;
			Component status = active >= 0
					? Component.translatable("kultivator.kota.teh.aktif", Component.translatable("kultivator.kota.teh.nama." + TEAS[active]), clock(teh.getLongOr("left", 0L)))
					: Component.translatable("kultivator.kota.teh.tidak_aktif");
			Brush.fit(g, font, Component.translatable("kultivator.kota.layar.sep", status, Component.translatable("kultivator.kota.teh.pengembara",
					teh.getIntOr("wanderers", 0))), x + 4.0F, coinY, w - 8.0F, Palette.JADE_DARK, 0.62F, 0.4F, false);
			long cooldown = teh.getLongOr("cooldown", 0L);
			Component kabar = cooldown > 0L ? Component.translatable("kultivator.kota.kabar.judul_tunggu", clock(cooldown))
					: Component.translatable("kultivator.kota.kabar.judul");
			Brush.fit(g, font, kabar, x + 4.0F, coinY + 54.0F + 2.0F, w - 8.0F, Palette.SEAL_DARK, 0.62F, 0.4F, false);
			if (!master()) {
				Brush.fit(g, font, Component.translatable("kultivator.kota.teh.di_kedai"), x + 4.0F, y + h - 15.0F, w - 150.0F, Palette.INK_FAINT, 0.6F, 0.4F, false);
			}
		}
	}

	// ================================================================== Pasar Gelap

	static final class Gelap extends KotaPage {
		private static final int TOP = 24;
		private static final int CARD_H = 20;

		Gelap(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.gelap");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.gelap.short");
		}

		@Override
		public int icon() {
			return Glyphs.SEAL;
		}

		private long built = -1L;

		@Override
		public void onData() {
			if (built != KotaClient.version()) {
				layout(x, y, w, h);
			}
		}

		@Override
		protected void build() {
			built = KotaClient.version();
			CompoundTag gelap = tab("gelap");
			ListTag offers = gelap.getListOrEmpty("offers");
			int columns = w >= 240 ? 2 : 1;
			int cw = (w - 4 - (columns - 1) * 4) / columns;
			for (int i = 0; i < offers.size() && i < 8; i++) {
				if (!(offers.get(i) instanceof CompoundTag offer)) {
					continue;
				}
				int cx = x + 2 + (i % columns) * (cw + 4);
				int cy = y + TOP + (i / columns) * (CARD_H + 3);
				if (cy + CARD_H > y + h - 38) {
					break;
				}
				int index = offer.getIntOr("i", -1);
				long price = offer.getLongOr("price", 0L);
				Card card = add(new Card(cx, cy, cw, CARD_H, () -> icon(offer.getStringOr("item", "")), () -> read(offer, "name"),
						() -> Component.translatable("kultivator.kota.gelap.baris", price), () -> false));
				card.enabledWhen(() -> all().getLongOr("nb", 0L) >= price && tab("gelap").getIntOr("rep", 0) > -50);
				card.onPress(() -> act("gbeli:" + index + ":" + price));
				card.tooltip(() -> lines(read(offer, "name"), Component.translatable("kultivator.kota.gelap.tip_beli",
						Math.round(tab("gelap").getDoubleOr("raid", 0.08D) * 100.0D))));
			}
			int bw = Math.min(100, (w - 12) / 2);
			TalismanButton sell = add(new TalismanButton(x + 4, y + h - 18, bw, 14, () -> Component.translatable("kultivator.kota.gelap.jual")));
			sell.enabledWhen(() -> tab("gelap").contains("hand"));
			sell.onPress(() -> act("gjual"));
			TalismanButton bribe = add(new TalismanButton(x + w - bw - 4, y + h - 18, bw, 14,
					() -> Component.translatable("kultivator.kota.gelap.suap", tab("gelap").getLongOr("bribe", 0L))));
			bribe.urgentWhen(() -> tab("gelap").contains("raid_left"));
			bribe.enabledWhen(() -> tab("gelap").contains("raid_left"));
			bribe.onPress(() -> act("suap"));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag gelap = tab("gelap");
			heading(g, Component.translatable("kultivator.kota.gelap.judul"), x + 2.0F, y, w - 4.0F, 0.8F);
			purse(g, y + 13.0F);
			if (gelap.isEmpty()) {
				elsewhere(g, Component.translatable("kultivator.kota.gelap.bisikan"), y + TOP);
				return;
			}
			int infoY = y + h - 34;
			int rep = gelap.getIntOr("rep", 0);
			Component line = Component.translatable("kultivator.kota.gelap.reputasi", rep, Math.round(gelap.getDoubleOr("raid", 0.08D) * 100.0D));
			Brush.fit(g, font, line, x + 4.0F, infoY, w - 8.0F, rep < 0 ? Palette.SEAL_DARK : Palette.INK_SOFT, 0.62F, 0.4F, false);
			bar(g, x + 4, infoY + 8, x + w - 4, infoY + 11, (rep + 100) / 200.0F, rep < 0 ? Palette.SEAL : 0xFF6A4A9A);
			if (gelap.contains("eclipse")) {
				// Gerhana Darah: the smugglers lie low and every price carries the surcharge (a blood-red band under the heading)
				float glow = 0.5F + 0.5F * (float) Math.sin(net.minecraft.util.Util.getMillis() / 420.0D);
				g.fill(x + 2, y + 22, x + w - 2, y + 23, Palette.argb(Math.round(0x70 + 0x50 * glow), 0xB01020));
				Brush.fit(g, font, Component.translatable("kultivator.kota.gelap.gerhana", gelap.getIntOr("eclipse", 25)), x + 4.0F, infoY - 9.0F,
						w - 8.0F, 0xFF9A1020, 0.6F, 0.4F, false);
			}
			if (gelap.contains("raid_left")) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(net.minecraft.util.Util.getMillis() / 160.0D);
				g.fill(x + 2, y + TOP - 2, x + w - 2, y + TOP - 1, Palette.argb(Math.round(0x80 + 0x60 * pulse), Palette.SEAL));
				Brush.fit(g, font, Component.translatable("kultivator.kota.gelap.razia_sisa", clock(gelap.getLongOr("raid_left", 0L))), x + 4.0F, y + h - 26.0F,
						w - 8.0F, Palette.SEAL, 0.62F, 0.4F, false);
			} else if (gelap.contains("hand")) {
				Brush.fit(g, font, Component.translatable("kultivator.kota.gelap.tangan", read(gelap, "hand"), gelap.getLongOr("hand_price", 0L)),
						x + 4.0F, y + h - 26.0F, w - 8.0F, Palette.INK_SOFT, 0.6F, 0.4F, false);
			}
		}
	}

	// ================================================================== Papan Misi

	static final class Misi extends KotaPage {
		private static final int TOP = 24;
		private static final int CARD_H = 28;

		Misi(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.misi");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.misi.short");
		}

		@Override
		public int icon() {
			return Glyphs.CODEX;
		}

		private static boolean keeper() {
			return role() == NpcRole.PENJAGA;
		}

		private static CompoundTag entry(int i) {
			ListTag board = tab("misi").getListOrEmpty("board");
			return i < board.size() && board.get(i) instanceof CompoundTag t ? t : new CompoundTag();
		}

		@Override
		protected void build() {
			int bw = 54;
			for (int i = 0; i < 3; i++) {
				int index = i;
				int cy = y + TOP + i * (CARD_H + 3);
				TalismanButton take = add(new TalismanButton(x + w - bw - 6, cy + (CARD_H - 14) / 2, bw, 14, () -> Component.translatable("kultivator.kota.misi.ambil")));
				take.enabledWhen(() -> keeper() && !entry(index).isEmpty() && !entry(index).getBooleanOr("taken", false)
						&& !tab("misi").contains("active") && tab("misi").getIntOr("left", 0) > 0);
				take.onPress(() -> act("ambil:" + index));
			}
			int aw = Math.min(90, (w - 12) / 2);
			TalismanButton turnIn = add(new TalismanButton(x + 4, y + h - 18, aw, 14, () -> Component.translatable("kultivator.kota.misi.serah")));
			turnIn.enabledWhen(() -> keeper() && tab("misi").contains("active"));
			turnIn.urgentWhen(() -> tab("misi").getCompoundOrEmpty("active").getBooleanOr("done", false));
			turnIn.onPress(() -> act("serah"));
			TalismanButton cancel = add(new TalismanButton(x + w - aw - 4, y + h - 18, aw, 14, () -> Component.translatable("kultivator.kota.misi.batal")));
			cancel.enabledWhen(() -> keeper() && tab("misi").contains("active"));
			cancel.onPress(() -> act("batal"));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag misi = tab("misi");
			heading(g, Component.translatable("kultivator.kota.misi.judul"), x + 2.0F, y, w - 4.0F, 0.8F);
			Brush.fit(g, font, Component.translatable("kultivator.kota.misi.sisa", misi.getIntOr("left", 0), misi.getIntOr("rep", 0)), x + 4.0F, y + 13.0F,
					w - 8.0F, Palette.INK_SOFT, 0.66F, 0.42F, false);
			if (!misi.getBooleanOr("enabled", true)) {
				elsewhere(g, Component.translatable("kultivator.kota.misi.mati"), y + TOP);
				return;
			}
			for (int i = 0; i < 3; i++) {
				CompoundTag entry = entry(i);
				if (entry.isEmpty()) {
					continue;
				}
				int cy = y + TOP + i * (CARD_H + 3);
				boolean taken = entry.getBooleanOr("taken", false);
				// a paper notice pinned to the board
				g.fillGradient(x + 2, cy, x + w - 2, cy + CARD_H, Palette.argb(0xC8, taken ? Palette.LOCKED_PAPER : Palette.TALISMAN),
						Palette.argb(0xC8, taken ? Palette.LOCKED_PAPER : Palette.TALISMAN_LOW));
				Brush.frame(g, x + 2, cy, x + w - 2, cy + CARD_H, Palette.argb(0xB0, Palette.TALISMAN_EDGE));
				Brush.disc(g, x + 7.0F, cy + 4.0F, 1.6F, Palette.SEAL);
				int type = entry.getIntOr("type", 0);
				String id = new String[]{"buru", "kemah", "herba", "kawal"}[Math.max(0, Math.min(3, type))];
				Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kota.misi.jenis." + id)), x + 12.0F, cy + 3.0F, w - 80.0F,
						taken ? Palette.INK_FAINT : Palette.INK, 0.7F, 0.45F, false);
				Brush.wrap(g, font, Component.translatable("kultivator.kota.misi.isi." + id), x + 12.0F, cy + 11.0F, w - 80.0F, Palette.INK_SOFT, 0.55F, false);
				Brush.fit(g, font, Component.translatable("kultivator.kota.misi.hadiah_baris", entry.getLongOr("reward", 0L)), x + 12.0F, cy + CARD_H - 7.0F,
						w - 80.0F, Palette.GOLD_DARK, 0.55F, 0.4F, false);
			}
			CompoundTag active = misi.getCompoundOrEmpty("active");
			if (!active.isEmpty()) {
				int type = Math.max(0, Math.min(3, active.getIntOr("type", 0)));
				String id = new String[]{"buru", "kemah", "herba", "kawal"}[type];
				Component state = Component.translatable(active.getBooleanOr("done", false) ? "kultivator.kota.misi.aktif_selesai" : "kultivator.kota.misi.aktif",
						Component.translatable("kultivator.kota.misi.jenis." + id));
				Brush.fit(g, font, state, x + 4.0F, y + h - 28.0F, w - 8.0F, active.getBooleanOr("done", false) ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.64F,
						0.4F, false);
			}
			if (!keeper()) {
				Brush.fit(g, font, Component.translatable("kultivator.kota.misi.di_papan"), x + 4.0F, y + h - 36.0F, w - 8.0F, Palette.INK_FAINT, 0.58F, 0.4F, false);
			}
		}
	}

	private TownPages() {
	}

	@SuppressWarnings("unused")
	private static List<Component> none() {
		return List.of();
	}
}
