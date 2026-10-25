package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rumah Dagang (DESIGN 42.6.5): the trading house of every town and bigger place (clerk {@code PEDAGANG_SEBERANG},
 * {@code KAFILAH_BASHI} or {@code BANKIR}; also read by the Papan Kontrak Muatan of 38.T.10.3) and its weekly board of
 * repeating contracts: "Send 64 Teh Awan from Kota Air to the capital every 7 days, 4 times, 3.2 NB a unit, 15 % fine on
 * a failed run". The board is the same for everyone in a world week (seeded by place and week, nothing stored): four to
 * six offers whose source is another known place 300-6000 blocks away; themed cities send their own trade goods, other
 * places staples. Higher trade tiers (Nama Dagang I-V, {@link KontrakDagang#tingkat}) unlock bigger loads and better
 * prices: {@code price = base value x (1.15 + 0.05 x tier) x the destination's trade-price factor}.
 */
public final class RumahDagang {

	public static final int TAWARAN_MIN = 4;
	public static final int TAWARAN_MAKS = 6;
	public static final int JARAK_MIN = 300;
	public static final int JARAK_MAKS = 6000;
	public static final int DENDA_PERSEN = 15;
	public static final int TIAP_HARI = 7;

	/** Staples sent from places without trade goods of their own. */
	private static final List<String> POKOK = List.of("minecraft:wheat", "minecraft:oak_log", "minecraft:iron_ingot", "minecraft:leather",
			"minecraft:paper", "minecraft:white_wool", "minecraft:copper_ingot", "minecraft:bread", "minecraft:honey_bottle", "minecraft:clay_ball");

	/**
	 * One offer: id (unique per place and week), item id, units per run, source place, destination place, days between
	 * runs, runs, NB per unit, fine %, tier needed.
	 */
	public record Tawaran(int id, String barang, int jumlah, long asal, long tujuan, int tiap, int kali, double harga, int denda, int tingkat) {

		public @Nullable Item item() {
			Identifier id = Identifier.tryParse(barang);
			return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		}

		/** "Kontrak II: 64 Teh Awan → Ibu Kota". */
		public Component nama(MinecraftServer server) {
			Item item = item();
			return Component.translatable("kultivator.kota42.kontrak.nama", Component.translatable("kultivator.kota42.kontrak.tingkat." + tingkat), jumlah,
					item == null ? Component.literal(barang) : new ItemStack(item).getHoverName(), PajakKota.nama(server, tujuan));
		}
	}

	static void init() {
	}

	/** Daily hook (the board itself is seeded per week). */
	static void harian(MinecraftServer server, long hari) {
	}

	/** True for places with a trading house (towns, themed cities, the capital, trading posts). */
	public static boolean ada(MinecraftServer server, long situs) {
		return GudangKota.adaGudang(server, situs);
	}

	/** This week's contract board of place {@code situs}. */
	public static List<Tawaran> tawaran(MinecraftServer server, long situs) {
		List<Tawaran> out = new ArrayList<>();
		if (!ada(server, situs)) {
			return out;
		}
		BlockPos here = Dagang.posSitus(server, situs);
		if (here == null) {
			return out;
		}
		List<Long> sources = new ArrayList<>();
		for (long other : tempatDagang(server)) {
			BlockPos at = Dagang.posSitus(server, other);
			if (other == situs || at == null) {
				continue;
			}
			double d = Math.sqrt(at.distSqr(new BlockPos(here.getX(), at.getY(), here.getZ())));
			if (d >= JARAK_MIN && d <= JARAK_MAKS) {
				sources.add(other);
			}
		}
		if (sources.isEmpty()) {
			return out;
		}
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		RandomSource random = RandomSource.create(situs * 2862933555777941757L + week * 3037000493L);
		int n = TAWARAN_MIN + random.nextInt(TAWARAN_MAKS - TAWARAN_MIN + 1);
		double factor = HargaKota.faktorDagang(server, situs);
		for (int i = 0; i < n; i++) {
			long from = sources.get(random.nextInt(sources.size()));
			int tier = 1 + Math.min(4, random.nextInt(5));
			String item = barangDari(server, from, random);
			double base = nilai(item);
			if (base <= 0.0D) {
				continue;
			}
			int units = Math.max(8, (int) Math.round((16 + random.nextInt(17)) * (1 + tier * 0.5D) / Math.max(1.0D, base / 4.0D)));
			units = Math.min(256, units - units % 8);
			int runs = 2 + random.nextInt(4);
			double price = Math.round(base * (1.15D + 0.05D * tier) * factor * 10.0D) / 10.0D;
			out.add(new Tawaran((int) (week % 1000L) * 100 + i + 1, item, units, from, situs, TIAP_HARI, runs, price, DENDA_PERSEN, tier));
		}
		return out;
	}

	/** The offer {@code id} of place {@code situs} this week, or null. */
	public static @Nullable Tawaran tawaran(MinecraftServer server, long situs, int id) {
		for (Tawaran t : tawaran(server, situs)) {
			if (t.id() == id) {
				return t;
			}
		}
		return null;
	}

	/** Known places that trade: towns, themed cities, trading posts. */
	static List<Long> tempatDagang(MinecraftServer server) {
		Set<Long> out = new LinkedHashSet<>(Towns.known(server));
		for (DuniaSites.Site site : DuniaSites.all(server)) {
			if (site.kind().startsWith("kota_tema/") || site.kind().startsWith("pos/") || site.kind().startsWith("kota/")) {
				out.add(site.id());
			}
		}
		out.removeIf(id -> !ada(server, id));
		return new ArrayList<>(out);
	}

	private static String barangDari(MinecraftServer server, long from, RandomSource random) {
		TemaKota.Kota kota = TemaKota.dari(server, from);
		if (kota != null) {
			List<DagangItems.Barang> own = new ArrayList<>();
			for (DagangItems.Barang b : DagangItems.barang()) {
				if (b.asal() == kota.tema() && !b.selundupan()) {
					own.add(b);
				}
			}
			if (!own.isEmpty()) {
				DagangItems.Barang b = own.get(random.nextInt(own.size()));
				Item item = DagangItems.item(b.id());
				if (item != null) {
					return BuiltInRegistries.ITEM.getKey(item).toString();
				}
			}
		}
		return POKOK.get(random.nextInt(POKOK.size()));
	}

	/** Base value of one unit of {@code itemId}: a trade good's table price, else the shop value (at least 0.2 NB). */
	static double nilai(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		if (item == null) {
			return 0.0D;
		}
		DagangItems.Barang good = DagangItems.barang(item);
		if (good != null) {
			return good.dasar();
		}
		return Math.max(0.2D, Harga.value(new ItemStack(item)));
	}

	private RumahDagang() {
	}
}
