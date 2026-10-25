package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiEntities;
import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rented warehouse space and caravans between warehouses (DESIGN 42.6.5). A player rents space in a town, themed city,
 * capital or trading post: 27 / 54 / 108 slots for 10 / 18 / 30 NB a week, one to four weeks ahead (rent to the Kas
 * Kota). Whole stacks are kept in SavedData like the Brankas; a filled container or ring is refused (38.0 rule 16). A
 * lapsed lease seals the space: nothing can be put in, but everything stays and can be taken out, so nothing is ever lost.
 * <p>
 * <b>Shipping</b>: a caravan carries stacks from one of the player's warehouses to another for 3 % of their value and
 * {@code ceil(distance / 1500)} days. Its cart is a {@link GerobakEntity} with a merchant and a porter walking beside, seen
 * only while a player is within 64 blocks of where the caravan is on its road. Every day on the road bandits strike with
 * 10 % (2 % while the player escorts it: he asked with {@code kawal} and came within 96 blocks of the cart that day); they
 * take one stack, which turns up among the stolen goods of the Ghost Market ({@link PasarHantu#tambahCurian}). Contract
 * caravans of {@link KontrakDagang} use the same road.
 * <p>
 * State in {@code KotaEkonomiData.bagian("gudang")}: per place {@code situs(...)["g"][uuid]} {@code u} slots, {@code s}
 * rent paid to (day), {@code i} {slot: stack}; global {@code n} next caravan id and {@code k} caravans ({@code id},
 * {@code p} owner, {@code a}/{@code b} from/to place, {@code ax az bx bz}, {@code t0} start game time, {@code d} length
 * ticks, {@code isi} stacks, {@code kw} escort, {@code kh} last day the escort was near, {@code kon} contract id,
 * {@code o} fee, {@code hl} stacks lost).
 */
public final class GudangKota {

	public static final int[] UKURAN = {27, 54, 108};
	public static final long[] SEWA = {10L, 18L, 30L};
	public static final int MINGGU_MAKS = 4;
	public static final double ONGKOS = 0.03D;
	public static final double BEGAL = 0.10D;
	public static final double BEGAL_KAWAL = 0.02D;
	public static final int JARAK_HARI = 1500;
	public static final double TUBUH_JARAK = 64.0D;
	public static final double KAWAL_JARAK = 96.0D;
	public static final int KIRIM_MAKS = 6;

	/** Visible caravan bodies by caravan id: cart, merchant, porter. */
	private static final Map<Long, Entity[]> TUBUH = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
	}

	static void lupakan() {
		for (Entity[] bodies : TUBUH.values()) {
			hapus(bodies);
		}
		TUBUH.clear();
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().gudang;
	}

	/** True when place {@code situs} has warehouses for rent (towns, themed cities, the capital, trading posts). */
	public static boolean adaGudang(MinecraftServer server, long situs) {
		String kind = PajakKota.jenisTempat(server, situs);
		return !PajakKota.JT_KAMPUNG.equals(kind) && !PajakKota.JT_LAIN.equals(kind);
	}

	// ================================================================== leases

	/** One warehouse of a player: place, slots, rent paid to (day), slots used. */
	public record Gudang(long situs, int ukuran, long sampai, int terpakai) {
	}

	private static CompoundTag semuaPemilik(MinecraftServer server, long situs) {
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.GUDANG, situs);
		CompoundTag g = site.getCompoundOrEmpty("g");
		site.put("g", g);
		return g;
	}

	private static @Nullable CompoundTag milik(MinecraftServer server, long situs, UUID player) {
		CompoundTag g = semuaPemilik(server, situs);
		return g.contains(player.toString()) ? g.getCompoundOrEmpty(player.toString()) : null;
	}

	/** The player's warehouse at {@code situs}, or null. */
	public static @Nullable Gudang gudang(MinecraftServer server, long situs, UUID player) {
		CompoundTag c = milik(server, situs, player);
		return c == null ? null : new Gudang(situs, c.getIntOr("u", UKURAN[0]), c.getLongOr("s", 0L), c.getCompoundOrEmpty("i").size());
	}

	/** Every warehouse of the player. */
	public static List<Gudang> semua(MinecraftServer server, UUID player) {
		List<Gudang> out = new ArrayList<>();
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.GUDANG);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			try {
				Gudang g = gudang(server, Long.parseLong(key.substring(5)), player);
				if (g != null) {
					out.add(g);
				}
			} catch (NumberFormatException ignored) {
				// not a place section
			}
		}
		return out;
	}

	/** True while the lease runs (else the space is sealed: take only). */
	public static boolean berlaku(MinecraftServer server, Gudang g) {
		return g.sampai() >= KotaHidup.hari(server);
	}

	/**
	 * Rents (or extends, or enlarges) space at {@code situs}: size index 0-2, 1-4 weeks paid ahead. Returns null on success
	 * or the refusal.
	 */
	public static @Nullable Component sewa(ServerPlayer player, long situs, int ukuran, int minggu) {
		MinecraftServer server = player.level().getServer();
		if (!aktif()) {
			return Component.translatable("kultivator.kota42.gudang.mati");
		}
		if (!adaGudang(server, situs)) {
			return Component.translatable("kultivator.kota42.gudang.tiada_di_sini");
		}
		int size = Math.max(0, Math.min(UKURAN.length - 1, ukuran));
		int weeks = Math.max(1, Math.min(MINGGU_MAKS, minggu));
		CompoundTag c = milik(server, situs, player.getUUID());
		long today = KotaHidup.hari(server);
		if (c != null && c.getIntOr("u", UKURAN[0]) > UKURAN[size]) {
			return Component.translatable("kultivator.kota42.gudang.tak_mengecil");
		}
		long paidTo = c == null ? today - 1L : Math.max(today - 1L, c.getLongOr("s", 0L));
		if (paidTo - today + 7L * weeks > 7L * MINGGU_MAKS) {
			return Component.translatable("kultivator.kota42.gudang.terlalu_lama", MINGGU_MAKS);
		}
		long price = SEWA[size] * weeks;
		if (!EkonomiApi.bayar(player, price, "kota42.gudang")) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price));
		}
		KasKota.pajak(server, situs, price, "gudang");
		if (c == null) {
			c = new CompoundTag();
			c.put("i", new CompoundTag());
		}
		c.putInt("u", UKURAN[size]);
		c.putLong("s", paidTo + 7L * weeks);
		semuaPemilik(server, situs).put(player.getUUID().toString(), c);
		KotaEkonomiData.get(server).ubah();
		Msg.chat(player, Component.translatable("kultivator.kota42.gudang.sewa_ok", UKURAN[size], PajakKota.nama(server, situs), c.getLongOr("s", 0L))
				.withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** The stacks of the player's warehouse at {@code situs} by slot (a copy). */
	public static Map<Integer, ItemStack> isi(MinecraftServer server, long situs, UUID player) {
		Map<Integer, ItemStack> out = new java.util.TreeMap<>();
		CompoundTag c = milik(server, situs, player);
		if (c == null) {
			return out;
		}
		CompoundTag items = c.getCompoundOrEmpty("i");
		for (String key : items.keySet()) {
			try {
				out.put(Integer.parseInt(key), Barang.muat(items.get(key), server.registryAccess()));
			} catch (NumberFormatException ignored) {
				// not a slot
			}
		}
		return out;
	}

	/** Stores the player's main-hand stack (he must be in the place); null on success or the refusal. */
	public static @Nullable Component simpan(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (hand.isEmpty()) {
			return Component.translatable("kultivator.kota42.gudang.tangan_kosong");
		}
		if (Barang.berisi(hand)) {
			return Component.translatable("kultivator.ekonomi.cincin_berisi");
		}
		Gudang g = gudang(server, situs, player.getUUID());
		if (g == null) {
			return Component.translatable("kultivator.kota42.gudang.belum_sewa");
		}
		if (!berlaku(server, g)) {
			return Component.translatable("kultivator.kota42.gudang.disegel");
		}
		Component name = hand.getHoverName();
		int count = hand.getCount();
		int slot = taruh(server, situs, player.getUUID(), hand.copy(), false);
		if (slot < 0) {
			return Component.translatable("kultivator.kota42.gudang.penuh", g.ukuran());
		}
		hand.setCount(0);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.gudang.simpan_ok", count, name, slot + 1).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Puts a stack into the first free slot; {@code lebih} lets it go past the size (arriving caravans). Returns the slot or -1. */
	static int taruh(MinecraftServer server, long situs, UUID player, ItemStack stack, boolean lebih) {
		CompoundTag c = milik(server, situs, player);
		if (c == null || stack.isEmpty()) {
			return -1;
		}
		CompoundTag items = c.getCompoundOrEmpty("i");
		int cap = lebih ? Integer.MAX_VALUE : c.getIntOr("u", UKURAN[0]);
		for (int slot = 0; slot < cap; slot++) {
			String key = Integer.toString(slot);
			if (!items.contains(key)) {
				items.put(key, Barang.simpan(stack, server.registryAccess()));
				c.put("i", items);
				KotaEkonomiData.get(server).ubah();
				return slot;
			}
		}
		return -1;
	}

	/** Gives slot {@code slot} (0-based) back to the player (also from a sealed space); null on success or the refusal. */
	public static @Nullable Component ambil(ServerPlayer player, long situs, int slot) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = milik(server, situs, player.getUUID());
		if (c == null) {
			return Component.translatable("kultivator.kota42.gudang.belum_sewa");
		}
		CompoundTag items = c.getCompoundOrEmpty("i");
		String key = Integer.toString(slot);
		if (!items.contains(key)) {
			return Component.translatable("kultivator.kota42.gudang.slot_kosong");
		}
		ItemStack stack = Barang.muat(items.get(key), server.registryAccess());
		items.remove(key);
		c.put("i", items);
		KotaEkonomiData.get(server).ubah();
		Component name = stack.getHoverName();
		Barang.beri(player, stack);
		Msg.actionBar(player, Component.translatable("kultivator.kota42.gudang.ambil_ok", name).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Takes {@code n} units of item-like stacks matching {@code sama} out of a warehouse (contracts); returns what was taken. */
	static int keluarkan(MinecraftServer server, long situs, UUID player, java.util.function.Predicate<ItemStack> sama, int n) {
		CompoundTag c = milik(server, situs, player);
		if (c == null || n <= 0) {
			return 0;
		}
		CompoundTag items = c.getCompoundOrEmpty("i");
		int taken = 0;
		for (String key : List.copyOf(items.keySet())) {
			if (taken >= n) {
				break;
			}
			ItemStack stack = Barang.muat(items.get(key), server.registryAccess());
			if (!sama.test(stack)) {
				continue;
			}
			int take = Math.min(n - taken, stack.getCount());
			stack.shrink(take);
			taken += take;
			if (stack.isEmpty()) {
				items.remove(key);
			} else {
				items.put(key, Barang.simpan(stack, server.registryAccess()));
			}
		}
		c.put("i", items);
		KotaEkonomiData.get(server).ubah();
		return taken;
	}

	/** Units in a warehouse matching {@code sama}. */
	static int hitung(MinecraftServer server, long situs, UUID player, java.util.function.Predicate<ItemStack> sama) {
		int n = 0;
		for (ItemStack stack : isi(server, situs, player).values()) {
			if (sama.test(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	// ================================================================== caravans

	/** One caravan on the road. */
	public record Kiriman(long id, UUID pemilik, long asal, long tujuan, List<ItemStack> isi, int sisaHari, int risiko, boolean kawal, long ongkos,
			int kontrak, double kemajuan) {
	}

	private static ListTag kafilah(MinecraftServer server) {
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.GUDANG);
		ListTag list = part.getListOrEmpty("k");
		if (!part.contains("k")) {
			part.put("k", list);
		}
		return list;
	}

	private static @Nullable CompoundTag cari(MinecraftServer server, long id) {
		for (Tag tag : kafilah(server)) {
			if (tag instanceof CompoundTag c && c.getLongOr("id", 0L) == id) {
				return c;
			}
		}
		return null;
	}

	/** Every caravan of the player. */
	public static List<Kiriman> kiriman(MinecraftServer server, UUID player) {
		List<Kiriman> out = new ArrayList<>();
		for (Tag tag : kafilah(server)) {
			if (tag instanceof CompoundTag c && c.getStringOr("p", "").equals(player.toString())) {
				out.add(kirimanDari(server, c));
			}
		}
		return out;
	}

	private static Kiriman kirimanDari(MinecraftServer server, CompoundTag c) {
		List<ItemStack> items = new ArrayList<>();
		for (Tag t : c.getListOrEmpty("isi")) {
			items.add(Barang.muat(t, server.registryAccess()));
		}
		double progress = kemajuan(server, c);
		long remaining = Math.max(0L, c.getLongOr("t0", 0L) + c.getLongOr("d", 0L) - server.overworld().getGameTime());
		UUID owner = Investasi.uuid(c.getStringOr("p", ""));
		boolean escorted = !c.getStringOr("kw", "").isEmpty();
		return new Kiriman(c.getLongOr("id", 0L), owner == null ? new UUID(0L, 0L) : owner, c.getLongOr("a", 0L), c.getLongOr("b", 0L), items,
				(int) Math.ceil(remaining / 24000.0D), (int) Math.round((escorted ? BEGAL_KAWAL : BEGAL) * 100.0D), escorted, c.getLongOr("o", 0L),
				c.getIntOr("kon", 0), progress);
	}

	private static double kemajuan(MinecraftServer server, CompoundTag c) {
		long length = Math.max(1L, c.getLongOr("d", 1L));
		return Math.max(0.0D, Math.min(1.0D, (server.overworld().getGameTime() - c.getLongOr("t0", 0L)) / (double) length));
	}

	private static Vec3 posisi(CompoundTag c, double t) {
		double x = c.getIntOr("ax", 0) + (c.getIntOr("bx", 0) - c.getIntOr("ax", 0)) * t;
		double z = c.getIntOr("az", 0) + (c.getIntOr("bz", 0) - c.getIntOr("az", 0)) * t;
		return new Vec3(x + 0.5D, 0.0D, z + 0.5D);
	}

	/** Days a caravan needs between two places ({@code ceil(distance / 1500)}, at least 1), or -1 when unknown. */
	public static int hari(MinecraftServer server, long asal, long tujuan) {
		BlockPos a = Dagang.posSitus(server, asal);
		BlockPos b = Dagang.posSitus(server, tujuan);
		if (a == null || b == null) {
			return -1;
		}
		double d = Math.sqrt(a.distSqr(new BlockPos(b.getX(), a.getY(), b.getZ())));
		return Math.max(1, (int) Math.ceil(d / JARAK_HARI));
	}

	/**
	 * Sends slots {@code slots} (0-based; empty = everything) of the player's warehouse at {@code asal} to his warehouse at
	 * {@code tujuan}; pays 3 % of the value. Returns null on success or the refusal.
	 */
	public static @Nullable Component kirim(ServerPlayer player, long asal, long tujuan, List<Integer> slots) {
		MinecraftServer server = player.level().getServer();
		if (!aktif()) {
			return Component.translatable("kultivator.kota42.gudang.mati");
		}
		if (asal == tujuan || gudang(server, asal, player.getUUID()) == null || gudang(server, tujuan, player.getUUID()) == null) {
			return Component.translatable("kultivator.kota42.gudang.kirim_tujuan");
		}
		if (kiriman(server, player.getUUID()).size() >= KIRIM_MAKS) {
			return Component.translatable("kultivator.kota42.gudang.kirim_penuh", KIRIM_MAKS);
		}
		CompoundTag c = milik(server, asal, player.getUUID());
		CompoundTag items = c.getCompoundOrEmpty("i");
		List<String> keys = new ArrayList<>();
		for (String key : items.keySet()) {
			try {
				if (slots.isEmpty() || slots.contains(Integer.parseInt(key))) {
					keys.add(key);
				}
			} catch (NumberFormatException ignored) {
				// not a slot
			}
		}
		if (keys.isEmpty()) {
			return Component.translatable("kultivator.kota42.gudang.slot_kosong");
		}
		ListTag cargo = new ListTag();
		long value = 0L;
		for (String key : keys) {
			ItemStack stack = Barang.muat(items.get(key), server.registryAccess());
			value += EkonomiApi.harga(stack) * stack.getCount();
			cargo.add(items.get(key).copy());
		}
		long fee = Math.max(1L, Math.round(value * ONGKOS));
		if (!EkonomiApi.bayar(player, fee, "kota42.kafilah")) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(fee));
		}
		for (String key : keys) {
			items.remove(key);
		}
		c.put("i", items);
		long id = berangkat(server, player.getUUID(), asal, tujuan, cargo, 0, fee);
		if (id == 0L) {
			for (Tag t : cargo) {
				taruh(server, asal, player.getUUID(), Barang.muat(t, server.registryAccess()), true);
			}
			EkonomiApi.beri(player, fee, "kota42.kafilah_batal");
			return Component.translatable("kultivator.kota42.gudang.kirim_tujuan");
		}
		Msg.chat(player, Component.translatable("kultivator.kota42.gudang.kirim_ok", keys.size(), PajakKota.nama(server, tujuan), hari(server, asal, tujuan),
				NilaiBatu.text(fee)).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Starts a caravan (also KontrakDagang); returns its id or 0 when a place's position is unknown. */
	static long berangkat(MinecraftServer server, UUID owner, long asal, long tujuan, ListTag cargo, int kontrak, long fee) {
		BlockPos a = Dagang.posSitus(server, asal);
		BlockPos b = Dagang.posSitus(server, tujuan);
		int days = hari(server, asal, tujuan);
		if (a == null || b == null || days < 0) {
			return 0L;
		}
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.GUDANG);
		long id = part.getLongOr("n", 1L);
		part.putLong("n", id + 1L);
		CompoundTag c = new CompoundTag();
		c.putLong("id", id);
		c.putString("p", owner.toString());
		c.putLong("a", asal);
		c.putLong("b", tujuan);
		c.putInt("ax", a.getX());
		c.putInt("az", a.getZ());
		c.putInt("bx", b.getX());
		c.putInt("bz", b.getZ());
		c.putLong("t0", server.overworld().getGameTime());
		c.putLong("d", days * 24000L);
		c.put("isi", cargo);
		c.putInt("kon", kontrak);
		c.putLong("o", fee);
		kafilah(server).add(c);
		KotaEkonomiData.get(server).ubah();
		return id;
	}

	/** Asks to escort caravan {@code id} (own caravans only); null on success or the refusal. */
	public static @Nullable Component kawal(ServerPlayer player, long id) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = cari(server, id);
		if (c == null || !c.getStringOr("p", "").equals(player.getUUID().toString())) {
			return Component.translatable("kultivator.kota42.gudang.kafilah_tiada");
		}
		c.putString("kw", player.getUUID().toString());
		KotaEkonomiData.get(server).ubah();
		Vec3 at = posisi(c, kemajuan(server, c));
		Msg.chat(player, Component.translatable("kultivator.kota42.gudang.kawal_ok", (int) at.x, (int) at.z).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Every second: caravans arrive, escorts are seen, bodies follow the players around. */
	static void tick(MinecraftServer server, int tick) {
		ListTag list = kafilah(server);
		if (list.isEmpty()) {
			if (!TUBUH.isEmpty()) {
				lupakan();
			}
			return;
		}
		ServerLevel level = server.overworld();
		long today = KotaHidup.hari(server);
		boolean changed = false;
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			long id = c.getLongOr("id", 0L);
			double t = kemajuan(server, c);
			if (t >= 1.0D) {
				hapus(TUBUH.remove(id));
				tiba(server, c);
				list.remove(i);
				changed = true;
				continue;
			}
			Vec3 pos = posisi(c, t);
			UUID escort = Investasi.uuid(c.getStringOr("kw", ""));
			ServerPlayer guard = escort == null ? null : server.getPlayerList().getPlayer(escort);
			if (guard != null && guard.level() == level && horizontal(guard.position(), pos) < KAWAL_JARAK && c.getLongOr("kh", -1L) != today) {
				c.putLong("kh", today);
				changed = true;
			}
			tubuh(level, c, pos);
		}
		if (changed) {
			KotaEkonomiData.get(server).ubah();
		}
	}

	/** Daily: bandits on the road, lapsed leases. */
	static void harian(MinecraftServer server, long hari) {
		for (Tag tag : kafilah(server)) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			boolean escorted = !c.getStringOr("kw", "").isEmpty() && c.getLongOr("kh", -1L) >= hari - 1L;
			RandomSource random = RandomSource.create(c.getLongOr("id", 0L) * 1103515245L + hari);
			if (random.nextDouble() >= (escorted ? BEGAL_KAWAL : BEGAL)) {
				continue;
			}
			ListTag cargo = c.getListOrEmpty("isi");
			if (cargo.isEmpty()) {
				continue;
			}
			int index = random.nextInt(cargo.size());
			ItemStack lost = Barang.muat(cargo.get(index), server.registryAccess());
			cargo.remove(index);
			c.put("isi", cargo);
			c.putInt("hl", c.getIntOr("hl", 0) + 1);
			UUID owner = Investasi.uuid(c.getStringOr("p", ""));
			if (owner != null) {
				PasarHantu.tambahCurian(server, owner, lost, c.getLongOr("b", 0L));
				Investasi.kabarPemain(server, owner, Component.translatable("kultivator.kota42.gudang.begal", lost.getHoverName(),
						PajakKota.nama(server, c.getLongOr("b", 0L))));
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	private static void tiba(MinecraftServer server, CompoundTag c) {
		UUID owner = Investasi.uuid(c.getStringOr("p", ""));
		if (owner == null) {
			return;
		}
		long to = c.getLongOr("b", 0L);
		if (c.getIntOr("kon", 0) > 0) {
			KontrakDagang.tiba(server, owner, c.getIntOr("kon", 0), c.getListOrEmpty("isi"), c.getIntOr("hl", 0) > 0);
			return;
		}
		int n = 0;
		for (Tag t : c.getListOrEmpty("isi")) {
			if (taruh(server, to, owner, Barang.muat(t, server.registryAccess()), true) >= 0) {
				n++;
			}
		}
		Investasi.kabarPemain(server, owner, Component.translatable("kultivator.kota42.gudang.tiba", PajakKota.nama(server, to), n));
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Shows, moves or removes the caravan's cart and its two walkers. */
	private static void tubuh(ServerLevel level, CompoundTag c, Vec3 pos) {
		long id = c.getLongOr("id", 0L);
		boolean near = false;
		for (ServerPlayer player : level.players()) {
			if (horizontal(player.position(), pos) < TUBUH_JARAK) {
				near = true;
				break;
			}
		}
		Entity[] bodies = TUBUH.get(id);
		BlockPos block = BlockPos.containing(pos.x, 64, pos.z);
		if (!near || !level.isPositionEntityTicking(block)) {
			if (bodies != null) {
				hapus(TUBUH.remove(id));
			}
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, block.getX(), block.getZ());
		Vec3 ground = new Vec3(pos.x, y, pos.z);
		Vec3 target = new Vec3(c.getIntOr("bx", 0) + 0.5D, y, c.getIntOr("bz", 0) + 0.5D);
		float yaw = (float) (Math.atan2(target.z - ground.z, target.x - ground.x) * (180.0D / Math.PI)) - 90.0F;
		if (bodies == null || bodies[0] == null || bodies[0].isRemoved()) {
			hapus(bodies);
			bodies = buat(level, c, ground, target, yaw);
			if (bodies == null) {
				return;
			}
			TUBUH.put(id, bodies);
		}
		Entity cart = bodies[0];
		if (horizontal(cart.position(), ground) > 6.0D) {
			cart.snapTo(ground.x, ground.y, ground.z, yaw, 0.0F);
		}
		for (int i = 1; i < bodies.length; i++) {
			if (bodies[i] instanceof KultivatorNpc npc && !npc.isRemoved()) {
				double side = i == 1 ? 1.8D : -1.8D;
				Vec3 at = cart.position().add(Math.cos(Math.toRadians(yaw)) * side, 0.0D, Math.sin(Math.toRadians(yaw)) * side);
				if (npc.distanceToSqr(at) > 9.0D) {
					npc.getNavigation().moveTo(at.x, at.y, at.z, 1.0D);
				}
			}
		}
	}

	private static @Nullable Entity[] buat(ServerLevel level, CompoundTag c, Vec3 ground, Vec3 target, float yaw) {
		if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, ground)) {
			return null;
		}
		GerobakEntity cart = EkonomiEntities.GEROBAK.create(level, EntitySpawnReason.EVENT);
		if (cart == null) {
			return null;
		}
		cart.snapTo(ground.x, ground.y, ground.z, yaw, 0.0F);
		cart.setMuatan(c.getIntOr("kon", 0) > 0 ? 0 : (int) (c.getLongOr("id", 0L) % 3L));
		// unbound (kafilah 0): the 38 caravan check would discard a cart of an id it does not know
		cart.ikat(0L, target);
		if (!level.addFreshEntity(cart)) {
			return null;
		}
		long town = c.getLongOr("a", 0L);
		BlockPos base = BlockPos.containing(ground);
		Entity[] bodies = new Entity[3];
		bodies[0] = cart;
		bodies[1] = HidupApi.tubuh(level, town, NpcRole.PEDAGANG_KELILING, base.offset(2, 0, 0), yaw);
		bodies[2] = HidupApi.tubuh(level, town, NpcRole.KULI, base.offset(-2, 0, 0), yaw);
		for (int i = 1; i < bodies.length; i++) {
			if (bodies[i] instanceof KultivatorNpc npc) {
				npc.setLonely(TUBUH_JARAK, 200);
				npc.memory().putBoolean("kafilah", true);
			}
		}
		return bodies;
	}

	private static void hapus(@Nullable Entity[] bodies) {
		if (bodies == null) {
			return;
		}
		for (Entity body : bodies) {
			if (body instanceof KultivatorNpc npc) {
				npc.vanish();
			} else if (body != null && !body.isRemoved()) {
				body.discard();
			}
		}
	}

	// ================================================================== commands

	/**
	 * {@code /kultivasi kotaku gudang [sewa <1-3> <minggu> | isi | simpan | ambil <slot> | kirim <ke> [slot] | kawal <id>]};
	 * {@code ke} is the number of the destination in the player's warehouse list.
	 */
	static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("gudang").executes(ctx -> daftar(ctx.getSource()))
				.then(Commands.literal("sewa").then(Commands.argument("ukuran", IntegerArgumentType.integer(1, 3))
						.then(Commands.argument("minggu", IntegerArgumentType.integer(1, MINGGU_MAKS)).executes(ctx -> diSini(ctx, (p, s) ->
								sewa(p, s, IntegerArgumentType.getInteger(ctx, "ukuran") - 1, IntegerArgumentType.getInteger(ctx, "minggu")))))))
				.then(Commands.literal("isi").executes(ctx -> lihatIsi(ctx.getSource())))
				.then(Commands.literal("simpan").executes(ctx -> diSini(ctx, GudangKota::simpan)))
				.then(Commands.literal("ambil").then(Commands.argument("slot", IntegerArgumentType.integer(1))
						.executes(ctx -> diSini(ctx, (p, s) -> ambil(p, s, IntegerArgumentType.getInteger(ctx, "slot") - 1)))))
				.then(Commands.literal("kirim").then(Commands.argument("ke", IntegerArgumentType.integer(1))
						.executes(ctx -> kirimCmd(ctx, -1))
						.then(Commands.argument("slot", IntegerArgumentType.integer(1)).executes(ctx -> kirimCmd(ctx, IntegerArgumentType.getInteger(ctx, "slot") - 1)))))
				.then(Commands.literal("kawal").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
					Component why = kawal(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id"));
					if (why != null) {
						ctx.getSource().sendFailure(why);
						return 0;
					}
					return 1;
				})));
	}

	private interface Aksi {
		@Nullable Component jalan(ServerPlayer player, long situs);
	}

	private static int diSini(CommandContext<CommandSourceStack> ctx, Aksi aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		Component why = tempat == null ? Component.translatable("kultivator.kota42.cmd.tanpa_tempat") : aksi.jalan(player, tempat.situsId());
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int kirimCmd(CommandContext<CommandSourceStack> ctx, int slot) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<Gudang> mine = semua(ctx.getSource().getServer(), player.getUUID());
		int to = IntegerArgumentType.getInteger(ctx, "ke") - 1;
		TempatInfo here = PajakKota.tempat(player);
		if (here == null || to < 0 || to >= mine.size()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.gudang.kirim_tujuan"));
			return 0;
		}
		Component why = kirim(player, here.situsId(), mine.get(to).situs(), slot < 0 ? List.of() : List.of(slot));
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int daftar(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		List<Gudang> mine = semua(server, player.getUUID());
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.gudang.judul", mine.size()).withStyle(ChatFormatting.GOLD), false);
		for (int i = 0; i < mine.size(); i++) {
			Gudang g = mine.get(i);
			int no = i + 1;
			source.sendSuccess(() -> Component.translatable(berlaku(server, g) ? "kultivator.kota42.gudang.baris" : "kultivator.kota42.gudang.baris_segel", no,
					PajakKota.nama(server, g.situs()), g.terpakai(), g.ukuran(), g.sampai()), false);
		}
		for (Kiriman k : kiriman(server, player.getUUID())) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.gudang.kafilah_baris", k.id(), PajakKota.nama(server, k.asal()),
					PajakKota.nama(server, k.tujuan()), k.isi().size(), k.sisaHari(), k.risiko())
					.append(k.kawal() ? Component.empty() : Component.translatable("kultivator.kota42.gudang.tombol_kawal").withStyle(style ->
							style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku gudang kawal " + k.id())))), false);
		}
		TempatInfo here = PajakKota.tempat(player);
		if (here != null && adaGudang(server, here.situsId()) && gudang(server, here.situsId(), player.getUUID()) == null) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.gudang.tawaran", PajakKota.nama(server, here.situsId()), UKURAN[0], SEWA[0],
					UKURAN[1], SEWA[1], UKURAN[2], SEWA[2]).withStyle(ChatFormatting.GRAY), false);
		}
		return mine.size();
	}

	private static int lihatIsi(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TempatInfo here = PajakKota.tempat(player);
		Gudang g = here == null ? null : gudang(server, here.situsId(), player.getUUID());
		if (g == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.gudang.belum_sewa"));
			return 0;
		}
		Map<Integer, ItemStack> items = isi(server, g.situs(), player.getUUID());
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.gudang.isi_judul", PajakKota.nama(server, g.situs()), items.size(), g.ukuran())
				.withStyle(ChatFormatting.GOLD), false);
		for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
			int slot = e.getKey() + 1;
			MutableComponent line = Component.translatable("kultivator.kota42.gudang.isi_baris", slot, e.getValue().getCount(), e.getValue().getHoverName())
					.append(Component.translatable("kultivator.kota42.gudang.tombol_ambil").withStyle(style -> style.withColor(ChatFormatting.GREEN)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku gudang ambil " + slot))));
			source.sendSuccess(() -> line, false);
		}
		return items.size();
	}

	/** Tests: the largest space at {@code situs} for 28 days, free. */
	static void sewaUji(MinecraftServer server, long situs, UUID player) {
		CompoundTag c = milik(server, situs, player);
		if (c == null) {
			c = new CompoundTag();
			c.put("i", new CompoundTag());
		}
		c.putInt("u", UKURAN[2]);
		c.putLong("s", KotaHidup.hari(server) + 28L);
		semuaPemilik(server, situs).put(player.toString(), c);
		KotaEkonomiData.get(server).ubah();
	}

	/** {@code uji kota_hidup ekonomi gudang}: rents the largest space here for free and lists caravans. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("gudang").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			TempatInfo here = PajakKota.tempat(player);
			MinecraftServer server = ctx.getSource().getServer();
			if (here == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
				return 0;
			}
			sewaUji(server, here.situsId(), player.getUUID());
			return daftar(ctx.getSource());
		});
	}

	private GudangKota() {
	}
}
