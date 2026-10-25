package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.kompleks.Kartu;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The village market (pasar desa, DESIGN 38.C.2.3, prices 38.M.2.2: {@code Harga.value x kampung.hargaPasar} (0.9)
 * x one {@code HargaKota.faktor} of the ware's category (42.6.3),
 * stock 4-6 wares a day): the four stalls of a {@code desa} or {@code desa_besar} (anchor {@code kampung/pasar}). It sells
 * what mortals grow and make: bread, baked potatoes, pies, honey, the style's own food (salted fish in the snow, mutton
 * on the savanna, dates of the oasis as dried kelp...), young spirit herbs from the edge of the fields (10-40 years), and
 * cheap tools (hoe, shears, rod, lantern, bucket). The day's wares and prices are the same for everyone; pieces sold are
 * memory only. The card shows once per visit within 6 blocks of the stalls; {@code /kultivasi kampung beli <n>} buys.
 */
final class PasarDesa {

	static final double JANGKAU = 8.0D;
	/** A player sees the card again after this long (ticks). */
	static final long ULANG = 20L * 60L * 5L;

	private record Barang(String id, long dasar, Function<RandomSource, ItemStack> buat) {
	}

	record Tawaran(int no, ItemStack barang, long harga, int sisa) {
	}

	private static final List<Barang> UMUM = List.of(
			vanilla("roti", 1L, Items.BREAD, 3),
			vanilla("kentang", 1L, Items.BAKED_POTATO, 3),
			vanilla("pai", 2L, Items.PUMPKIN_PIE, 2),
			vanilla("madu", 2L, Items.HONEY_BOTTLE, 1),
			vanilla("kue", 4L, Items.CAKE, 1),
			new Barang("herba", 0L, r -> SpiritHerbs.create(HerbKind.SPIRIT_GRASS, 10 + r.nextInt(31))),
			new Barang("ginseng", 0L, r -> SpiritHerbs.create(HerbKind.NINECURL_GINSENG, 10 + r.nextInt(21))),
			vanilla("cangkul", 3L, Items.IRON_HOE, 1),
			vanilla("gunting", 2L, Items.SHEARS, 1),
			vanilla("joran", 2L, Items.FISHING_ROD, 1),
			vanilla("lentera", 2L, Items.LANTERN, 1),
			vanilla("ember", 3L, Items.BUCKET, 1));

	private static final Map<String, Barang> KHAS = Map.of(
			"sedang", vanilla("sup", 2L, Items.BEETROOT_SOUP, 1),
			"salju", vanilla("ikan_asin", 1L, Items.COOKED_SALMON, 2),
			"sabana", vanilla("kambing", 2L, Items.COOKED_MUTTON, 2),
			"sakura", vanilla("buah_beri", 1L, Items.SWEET_BERRIES, 8),
			"gurun", vanilla("kurma", 1L, Items.DRIED_KELP, 8),
			"rawa", vanilla("ikan", 1L, Items.COOKED_COD, 2),
			"terasering", vanilla("nasi_sup", 2L, Items.MUSHROOM_STEW, 1));

	/** "site|day|no" -> pieces sold; player -> last card time. */
	private static final Map<String, Integer> TERJUAL = new HashMap<>();
	private static final Map<UUID, Long> KARTU = new HashMap<>();

	private static Barang vanilla(String id, long dasar, Item item, int count) {
		return new Barang(id, dasar, r -> new ItemStack(item, count));
	}

	static void reset() {
		TERJUAL.clear();
		KARTU.clear();
	}

	/** The day's offers of village {@code d}. */
	static List<Tawaran> tawaran(ServerLevel level, KampungData.Desa d) {
		long day = Kota.day(level.getServer());
		RandomSource roll = RandomSource.create(d.situs * 0x9E3779B97F4A7C15L + day * 1_000_003L);
		List<Barang> pool = new ArrayList<>(UMUM);
		Barang khas = KHAS.get(d.gaya);
		List<Barang> pick = new ArrayList<>();
		if (khas != null) {
			pick.add(khas);
		}
		int count = 4 + roll.nextInt(3);
		while (pick.size() < count && !pool.isEmpty()) {
			pick.add(pool.remove(roll.nextInt(pool.size())));
		}
		double factor = KampungConfig.get().hargaPasar;
		List<Tawaran> out = new ArrayList<>();
		for (int i = 0; i < pick.size(); i++) {
			Barang b = pick.get(i);
			ItemStack stack = b.buat().apply(RandomSource.create(d.situs ^ day * 7919L ^ ((long) i << 40)));
			if (stack.isEmpty()) {
				continue;
			}
			// food and tools from the village table; a young herb at its own herb value (Harga.herbValue by age)
			long base = b.dasar() > 0L ? b.dasar() * stack.getCount() : Math.max(1L, EkonomiApi.harga(stack));
			// one HargaKota.faktor per price (42.6.3), by the ware's category
			double town = HargaKota.faktor(level.getServer(), d.situs, Kategori.dari(stack));
			long price = Math.max(1L, Math.round(base * factor * town));
			int stock = 1 + roll.nextInt(4);
			int left = Math.max(0, stock - TERJUAL.getOrDefault(kunci(d.situs, day, i), 0));
			out.add(new Tawaran(i, stack, price, left));
		}
		return out;
	}

	private static String kunci(long site, long day, int no) {
		return site + "|" + day + "|" + no;
	}

	/** Every second near the stalls. */
	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, long site, List<ServerPlayer> within) {
		long now = level.getGameTime();
		KampungData.Desa d = KampungData.get(level.getServer()).cari(site);
		if (d == null) {
			return;
		}
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.distanceToSqr(anchor) > 6.0D * 6.0D) {
				continue;
			}
			Long last = KARTU.get(player.getUUID());
			if (last != null && now - last < ULANG) {
				continue;
			}
			KARTU.put(player.getUUID(), now);
			kartu(level, player, d);
		}
	}

	static void kartu(ServerLevel level, ServerPlayer player, KampungData.Desa d) {
		Component name = KampungNames.nama(d.ukuran, d.situs);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.kampung.pasar.salam." + (Math.floorMod(level.getGameTime() / 20L, 3L))));
		List<Component> chips = new ArrayList<>();
		for (Tawaran t : tawaran(level, d)) {
			Component item = t.barang().getHoverName();
			lines.add(Component.translatable("kultivator.kampung.pasar.baris", t.no() + 1, t.barang().getCount(), item, t.harga(), t.sisa()));
			if (t.sisa() > 0) {
				chips.add(Kartu.chip(Component.translatable("kultivator.kampung.pasar.beli", t.no() + 1), "/kultivasi kampung beli " + t.no(),
						Component.translatable("kultivator.kampung.pasar.beli.hover", item, t.harga()), ChatFormatting.GREEN));
			}
		}
		Kartu.kirim(player, Component.translatable("kultivator.kampung.pasar.judul", name), lines, chips);
	}

	/** The village whose stalls stand within reach of {@code player}, or null. */
	static KampungData.@Nullable Desa dekatPasar(ServerPlayer player) {
		ServerLevel level = player.level();
		DuniaAnchorEntity a = DuniaAnchors.nearest(level, player.position(), "kampung/pasar", JANGKAU);
		return a == null ? null : KampungData.get(level.getServer()).cari(KampungAnchors.situs(a));
	}

	static int lihat(ServerPlayer player) {
		KampungData.Desa d = dekatPasar(player);
		if (d == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.pasar.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		kartu(player.level(), player, d);
		return 1;
	}

	static int beli(ServerPlayer player, int no) {
		ServerLevel level = player.level();
		KampungData.Desa d = dekatPasar(player);
		if (d == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.pasar.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Tawaran pick = null;
		for (Tawaran t : tawaran(level, d)) {
			if (t.no() == no) {
				pick = t;
			}
		}
		if (pick == null || pick.sisa() <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.pasar.habis").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!EkonomiApi.bayar(player, pick.harga(), "pasar_desa")) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.pasar.uang", pick.harga()).withStyle(ChatFormatting.RED));
			return 0;
		}
		TERJUAL.merge(kunci(d.situs, Kota.day(level.getServer()), no), 1, Integer::sum);
		HargaKota.catatBeli(level.getServer(), d.situs, Kategori.dari(pick.barang()), pick.barang().getCount());
		ItemStack stack = dev.kultivator.dunia.tempat.ekonomi.Barang.tandaiAsal(pick.barang().copy(), Kota.day(level.getServer()));
		Component item = stack.getHoverName();
		int count = stack.getCount();
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			player.spawnAtLocation(level, stack);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		Msg.chat(player, Component.translatable("kultivator.kampung.pasar.dibeli", count, item, pick.harga()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private PasarDesa() {
	}
}
