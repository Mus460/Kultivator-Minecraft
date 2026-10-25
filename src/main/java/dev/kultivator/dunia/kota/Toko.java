package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.tempa.StorageRingItem;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The shops of the Paviliun Pil and Paviliun Pusaka (DESIGN 34.5.1): stock seeded by (the merchant's anchor, the
 * in-game day, the buyer's realm band), 6-9 offers of existing items in the band ±1, price = base × 0.9-1.3 ×
 * {@code kota.priceScale} × the Nama Baik discount (10 % at 50+). A shop buys its own kind of goods back at 0.35-0.40 ×
 * base (never more than half of any sell price), at most 16 stacks per player per day. Both sides carry one
 * {@code HargaKota.faktor} of the item's category (42.6.3; the buy-back also the player's market flooding). Each offer
 * has 1-4 pieces a day, shared by everyone in the town.
 */
final class Toko {

	static final int SALES_PER_DAY = 16;
	static final String SALES = "sales";

	/** One offer of today. */
	record Offer(int index, Harga.Ware ware, ItemStack stack, long price, int stock) {
	}

	/** The realm band of a player (realm ordinal). */
	static int band(ServerPlayer player) {
		return Mth.clamp(Cultivators.get(player).realm().ordinal(), 0, Harga.BANDS - 1);
	}

	static boolean pillShop(KultivatorNpc npc) {
		return npc.role() == NpcRole.PEDAGANG_PIL;
	}

	/** Today's offers of the merchant for this player's band. */
	static List<Offer> offers(ServerPlayer player, KultivatorNpc npc) {
		long day = Kota.day(player.level().getServer());
		int band = band(player);
		List<Harga.Ware> pool = new ArrayList<>();
		for (Harga.Ware ware : pillShop(npc) ? Harga.PILLS : Harga.TREASURES) {
			if (ware.maxBand() >= band - 1 && ware.minBand() <= band + 1 && (ware.base() == 0L || Harga.exists(ware.id()))) {
				pool.add(ware);
			}
		}
		RandomSource random = RandomSource.create(npc.anchorSite() * 31L + day * 1_000_003L + band);
		int count = Math.min(pool.size(), 6 + random.nextInt(4));
		List<Offer> offers = new ArrayList<>();
		CompoundTag sold = soldTag(player, npc, day);
		double discount = discount(player, npc);
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			Harga.Ware ware = pool.remove(random.nextInt(pool.size()));
			RandomSource itemRandom = RandomSource.create(npc.anchorSite() ^ (day * 7919L) ^ ((long) i << 40) ^ band);
			ItemStack stack = ware.make().make(itemRandom, band);
			if (stack.isEmpty()) {
				continue;
			}
			double factor = 0.9D + random.nextDouble() * 0.4D;
			// one HargaKota.faktor per price (42.6.3)
			double town = HargaKota.faktor(player, npc, Kategori.dari(stack));
			long price = Math.max(1L, Math.round(Harga.priceOf(ware, stack) * factor * Kota.config().priceScale * discount * town));
			int stock = 1 + random.nextInt(4);
			int left = Math.max(0, stock - sold.getIntOr(Integer.toString(i), 0));
			offers.add(new Offer(i, ware, stack, price, left));
		}
		return offers;
	}

	/** 0.9 from Nama Baik 50 on, else 1. */
	static double discount(ServerPlayer player, KultivatorNpc npc) {
		int rep = KotaData.get(player.level().getServer()).reputation(npc.town(), player.getUUID());
		return rep >= Kota.DISCOUNT_REPUTATION ? 0.9D : 1.0D;
	}

	private static CompoundTag soldTag(ServerPlayer player, KultivatorNpc npc, long day) {
		CompoundTag town = KotaData.get(player.level().getServer()).town(npc.town());
		CompoundTag sold = town.getCompoundOrEmpty("sold");
		String key = npc.anchorSite() + "." + day;
		if (!sold.getBooleanOr("_day" + day, false)) {
			// a new day: forget yesterday's counts
			sold = new CompoundTag();
			sold.putBoolean("_day" + day, true);
			town.put("sold", sold);
		}
		CompoundTag mine = sold.getCompoundOrEmpty(key);
		sold.put(key, mine);
		return mine;
	}

	/**
	 * True when the merchant buys this item (its own kind of goods). Never a pouch or ring that still holds items or is
	 * bound (its contents would be lost, audit K2), and never a phase-9 ware of Alam Abadi (audit S24).
	 */
	static boolean buys(KultivatorNpc npc, ItemStack stack) {
		return tradable(stack, pillShop(npc));
	}

	/** The goods of the pill pavilion ({@code pills}) or of the treasure pavilion, under the same rules as {@link #buys}. */
	static boolean tradable(ItemStack stack, boolean pills) {
		if (stack.isEmpty() || SpiritStones.isStone(stack) || Harga.value(stack) <= 0L || StorageRingItem.unsafeToSell(stack)
				|| Harga.phase9(stack)) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (pills) {
			return id.endsWith("_pill") || SpiritHerbs.isHerb(stack) || BeastCores.isCore(stack);
		}
		return "forged_artifact".equals(id) || id.endsWith("_talisman") || id.startsWith("storage_") || "jade_slip".equals(id);
	}

	/** What the merchant pays for one item of {@code stack} (0.35-0.40 × base, seeded by the day). */
	static long buyPrice(ServerPlayer player, KultivatorNpc npc, ItemStack stack) {
		long day = Kota.day(player.level().getServer());
		RandomSource random = RandomSource.create(npc.anchorSite() ^ day * 31L);
		double factor = 0.35D + random.nextDouble() * 0.05D;
		// the town factor with the player's market flooding (42.6.3), never above half of the lowest sell price
		Kategori kategori = Kategori.dari(stack);
		double cap = 0.5D * 0.9D * HargaKota.faktor(player, npc, kategori);
		double town = Math.min(factor * HargaKota.faktorJual(player, npc, kategori), cap);
		return Math.max(0L, (long) Math.floor(Harga.value(stack) * town * Kota.config().priceScale));
	}

	// ================================================================== actions

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		if (npc.role() != NpcRole.PEDAGANG_PIL && npc.role() != NpcRole.PANDAI_PUSAKA) {
			return false;
		}
		if ("beli".equals(verb)) {
			return buy(player, npc, Kota.arg(parts, 1, -1), Kota.arg(parts, 2, -1));
		}
		return sell(player, npc);
	}

	/** Buys offer {@code index} at the price the screen showed ({@code seen}; a changed price is refused). */
	private static boolean buy(ServerPlayer player, KultivatorNpc npc, int index, int seen) {
		List<Offer> offers = offers(player, npc);
		Offer offer = null;
		for (Offer each : offers) {
			if (each.index() == index) {
				offer = each;
			}
		}
		if (offer == null) {
			return true;
		}
		if (seen >= 0 && seen != offer.price()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.toko.harga_berubah").withStyle(ChatFormatting.RED));
			return true;
		}
		if (offer.stock() <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.toko.habis").withStyle(ChatFormatting.RED));
			return true;
		}
		if (!NilaiBatu.pay(player, offer.price())) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(offer.price())).withStyle(ChatFormatting.RED));
			Voice.say(npc, npc.voiceKey("careful"), null);
			return true;
		}
		long day = Kota.day(player.level().getServer());
		CompoundTag sold = soldTag(player, npc, day);
		sold.putInt(Integer.toString(index), sold.getIntOr(Integer.toString(index), 0) + 1);
		HargaKota.catatBeli(player.level().getServer(), HargaKota.situs(player, npc), Kategori.dari(offer.stack()), 1);
		KotaData.get(player.level().getServer()).touch();
		// bought in a town shop: no shop or stall buys it back for 3 days (38.M.5, 42.0 rule)
		ItemStack stack = Barang.tandaiAsal(offer.stack().copy(), Barang.hari(player.level().getServer()));
		Component name = stack.getHoverName();
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			Kota.drop(player, stack);
		}
		player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.5F, 1.4F);
		Voice.say(npc, npc.voiceKey("thanks"), null);
		Msg.actionBar(player, Component.translatable("kultivator.kota.toko.dibeli", name, NilaiBatu.text(offer.price())).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Sells the whole stack in the main hand (one stack counts once toward the daily 16). */
	private static boolean sell(ServerPlayer player, KultivatorNpc npc) {
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (StorageRingItem.unsafeToSell(stack)) {
			Msg.actionBar(player, Component.translatable("kultivator.ekonomi.cincin_berisi").withStyle(ChatFormatting.RED));
			return true;
		}
		if (!buys(npc, stack)) {
			Msg.actionBar(player, Component.translatable(Harga.phase9(stack) ? "kultivator.ekonomi.toko.fase9" : "kultivator.kota.toko.tidak_beli")
					.withStyle(ChatFormatting.RED));
			return true;
		}
		if (Barang.baruDibeli(stack, Barang.hari(player.level().getServer()))) {
			Msg.actionBar(player, Component.translatable("kultivator.tempat_ekonomi.asal_toko", Barang.ASAL_HARI).withStyle(ChatFormatting.RED));
			return true;
		}
		long day = Kota.day(player.level().getServer());
		KotaData data = KotaData.get(player.level().getServer());
		if (data.dailyCount(player.getUUID(), day, SALES) >= SALES_PER_DAY) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.toko.batas_jual", SALES_PER_DAY).withStyle(ChatFormatting.RED));
			return true;
		}
		long each = buyPrice(player, npc, stack);
		if (each <= 0L) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.toko.tidak_beli").withStyle(ChatFormatting.RED));
			return true;
		}
		int count = stack.getCount();
		Component name = stack.getHoverName();
		long total = each * count;
		Kategori kategori = Kategori.dari(stack);
		stack.shrink(count);
		NilaiBatu.give(player, total);
		data.addDaily(player.getUUID(), day, SALES, 1);
		HargaKota.catatJual(player.level().getServer(), HargaKota.situs(player, npc), kategori, count);
		player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.kota.toko.dijual", count, name, NilaiBatu.text(total)).withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", pillShop(npc) ? "pil" : "pusaka");
		ListTag list = new ListTag();
		for (Offer offer : offers(player, npc)) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("i", offer.index());
			entry.putString("item", BuiltInRegistries.ITEM.getKey(offer.stack().getItem()).toString());
			entry.putInt("count", offer.stack().getCount());
			entry.store("name", ComponentSerialization.CODEC, offer.stack().getHoverName());
			entry.putLong("price", offer.price());
			entry.putInt("stock", offer.stock());
			int age = SpiritHerbs.age(offer.stack());
			if (age > 0) {
				entry.putInt("age", age);
			}
			list.add(entry);
		}
		tag.put("offers", list);
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (buys(npc, hand)) {
			tag.store("hand", ComponentSerialization.CODEC, hand.getHoverName());
			tag.putInt("hand_count", hand.getCount());
			tag.putLong("hand_price", buyPrice(player, npc, hand) * hand.getCount());
		}
		long day = Kota.day(player.level().getServer());
		tag.putInt("sales_left", Math.max(0, SALES_PER_DAY - KotaData.get(player.level().getServer()).dailyCount(player.getUUID(), day, SALES)));
		tag.putBoolean("discount", discount(player, npc) < 1.0D);
		return tag;
	}

	private Toko() {
	}
}
