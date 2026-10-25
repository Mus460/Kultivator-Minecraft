package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.KontrakDagang;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's building material stock (DESIGN 42.9.4, contract C42.F1) in the five units of the plot plans: {@code kayu}
 * (1 per 4 wood blocks), {@code batu} (1 per 4 stone/brick), {@code genteng} (1 per 2 roof blocks), {@code kaca_kertas}
 * (1 per 2 window blocks), {@code kain} (1 per 4 wool/carpet). Sources, all in {@code KotaTumbuhData.situs("bahan", situs)}:
 * <ul>
 *   <li>daily production on Hari Warga: every porter (KULI) brings 1 unit, every builder (TUKANG) 0.5, split over the
 *       kinds the next stage still needs (wood and stone first; tiles, paper and cloth come by caravan);</li>
 *   <li>Pesanan Kota (42.6.5, {@code KontrakDagang.daftarPesanan}): the missing units of the running stage are posted as
 *       city orders for real blocks; a delivered stack adds its units;</li>
 *   <li>the player sells ({@link #jual}, paid {@code HargaKota x 1.1} out of Kas Kota) or gives ({@link #sumbang}, Nama
 *       Baik +1 per 32 blocks through the plot's donor list) real blocks from his bag;</li>
 *   <li>Kas Kota buys what is still missing from passing caravans ({@link #beliKas}) at the unit price.</li>
 * </ul>
 */
public final class BahanKota {

	/** The five material kinds in index order of {@link #stok}. */
	public static final List<String> JENIS = List.of("kayu", "batu", "genteng", "kaca_kertas", "kain");
	/** Base price of one unit in NB before {@code HargaKota} (Kategori BAHAN): wood, stone, tiles, paper/glass, cloth. */
	static final int[] HARGA_DASAR = {2, 2, 4, 4, 3};
	/** The block a city order asks for per kind (any block of the kind is taken when delivered by hand). */
	static Item barangPesanan(int i) {
		return switch (i) {
			case 0 -> Items.OAK_PLANKS;
			case 1 -> Items.STONE_BRICKS;
			case 2 -> Items.DEEPSLATE_TILES;
			case 3 -> Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.WHITE);
			default -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
		};
	}
	/** Stock cap per kind (a town warehouse). */
	public static final int MAKS = 999;

	/** The stock of place {@code situs}: one count per {@link #JENIS} entry (a new array). */
	public static int[] stok(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server == null ? new int[JENIS.size()] : stok(server, situs);
	}

	public static int[] stok(MinecraftServer server, long situs) {
		int[] v = KotaTumbuhData.get(server).situs(KotaTumbuhData.BAHAN, situs).getIntArray("s").orElse(new int[0]);
		int[] out = new int[JENIS.size()];
		System.arraycopy(v, 0, out, 0, Math.min(v.length, out.length));
		return out;
	}

	static void setStok(MinecraftServer server, long situs, int[] stok) {
		int[] v = new int[JENIS.size()];
		for (int i = 0; i < v.length; i++) {
			v[i] = Math.max(0, Math.min(MAKS, stok[i]));
		}
		KotaTumbuhData.get(server).situs(KotaTumbuhData.BAHAN, situs).putIntArray("s", v);
		KotaTumbuhData.get(server).ubah();
	}

	/** Adds {@code n} units of kind {@code i} (negative takes). */
	public static void tambah(MinecraftServer server, long situs, int i, int n) {
		if (i < 0 || i >= JENIS.size() || n == 0) {
			return;
		}
		int[] s = stok(server, situs);
		s[i] += n;
		setStok(server, situs, s);
	}

	/** True when the stock holds {@code perlu} (by kind). */
	static boolean cukup(int[] stok, int[] perlu) {
		for (int i = 0; i < perlu.length; i++) {
			if (stok[i] < perlu[i]) {
				return false;
			}
		}
		return true;
	}

	/** Takes {@code perlu} from the stock if all of it is there. */
	static boolean ambil(MinecraftServer server, long situs, int[] perlu) {
		int[] s = stok(server, situs);
		if (!cukup(s, perlu)) {
			return false;
		}
		for (int i = 0; i < perlu.length; i++) {
			s[i] -= perlu[i];
		}
		setStok(server, situs, s);
		return true;
	}

	/** Price of one unit of kind {@code i} at a place (NB, at least 1). */
	public static long harga(MinecraftServer server, long situs, int i) {
		double f = HargaKota.aktif() ? HargaKota.faktor(server, situs, Kategori.BAHAN) : 1.0D;
		return Math.max(1L, Math.round(HARGA_DASAR[Math.max(0, Math.min(i, HARGA_DASAR.length - 1))] * f));
	}

	/**
	 * Kas Kota buys the units still missing from {@code perlu} from passing caravans (all or nothing per kind, 42.6.0: never
	 * a partial payment); returns true when the stock now holds all of it.
	 */
	static boolean beliKas(MinecraftServer server, long situs, int[] perlu) {
		int[] s = stok(server, situs);
		for (int i = 0; i < perlu.length; i++) {
			int kurang = perlu[i] - s[i];
			if (kurang > 0 && KasKota.bayar(server, situs, kurang * harga(server, situs, i), "kavling")) {
				s[i] += kurang;
			}
		}
		setStok(server, situs, s);
		return cukup(s, perlu);
	}

	// ================================================================== daily production

	/** Hari Warga: porters and builders bring material toward what the town needs ({@code kebutuhan} by kind, may be 0). */
	static void produksi(MinecraftServer server, WargaData.Situs s, int[] kebutuhan) {
		double units = 0.0D;
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			NpcRole role = NpcRole.byId(w.peran);
			if (role == NpcRole.KULI || role == NpcRole.KULI_GUNUNG) {
				units += 1.0D;
			} else if (role == NpcRole.TUKANG || role == NpcRole.TUKANG_KAPAL) {
				units += 0.5D;
			}
		}
		// a little always comes in by cart, so a town without porters is slow, not stuck
		int n = (int) Math.floor(units) + 1;
		int[] stock = stok(server, s.id);
		for (int k = 0; k < n; k++) {
			// wood and stone are local; the kind furthest below its need gets the unit, wood/stone only
			int pick = kebutuhan[0] - stock[0] >= kebutuhan[1] - stock[1] ? 0 : 1;
			stock[pick]++;
		}
		setStok(server, s.id, stock);
	}

	// ================================================================== player trade (42.11.13 jual / sumbang)

	/** Material kind of an item stack ({@code -1} = not a building block of the plans). */
	public static int jenisBarang(MinecraftServer server, ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) {
			return -1;
		}
		return RencanaTumbuh.bahanBlok(server, BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString());
	}

	/** Units of kind {@code i} the player carries as matching blocks ({@code tas}). */
	public static int tas(ServerPlayer player, int i) {
		MinecraftServer server = player.level().getServer();
		int blocks = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (jenisBarang(server, stack) == i) {
				blocks += stack.getCount();
			}
		}
		return blocks / RencanaTumbuh.satuanBlok(server, i);
	}

	/** Takes up to {@code satuan} units of kind {@code i} as blocks from the bag; returns the units taken. */
	private static int ambilTas(ServerPlayer player, int i, int satuan) {
		MinecraftServer server = player.level().getServer();
		int per = RencanaTumbuh.satuanBlok(server, i);
		int want = Math.min(satuan, tas(player, i)) * per;
		int left = want;
		List<ItemStack> stacks = new ArrayList<>(player.getInventory().getNonEquipmentItems());
		for (ItemStack stack : stacks) {
			if (left <= 0) {
				break;
			}
			if (jenisBarang(server, stack) == i) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		player.getInventory().setChanged();
		return (want - left) / per;
	}

	/**
	 * Sells {@code satuan} units of kind {@code i} from the bag to the town ({@code jual:BH:N}), paid {@code HargaKota x 1.1}
	 * per unit out of Kas Kota. Null on success, else the refusal.
	 */
	public static @Nullable Component jual(ServerPlayer player, long situs, int i, int satuan) {
		MinecraftServer server = player.level().getServer();
		if (i < 0 || i >= JENIS.size() || satuan <= 0) {
			return Component.translatable("kultivator.kota42.tumbuh.bahan_salah");
		}
		int n = Math.min(satuan, tas(player, i));
		if (n <= 0) {
			return Component.translatable("kultivator.kota42.tumbuh.tas_kosong", Component.translatable("kultivator.kota42.tumbuh.bahan." + JENIS.get(i)));
		}
		long per = Math.max(1L, Math.round(harga(server, situs, i) * 1.1D));
		if (!KasKota.bayar(server, situs, per * n, "kavling")) {
			return Component.translatable("kultivator.kota42.tumbuh.kas_kurang");
		}
		int got = ambilTas(player, i, n);
		if (got < n) {
			KasKota.tambah(server, situs, per * (n - got));
		}
		EkonomiApi.beri(player, per * got, "kavling_jual");
		tambah(server, situs, i, got);
		CompoundTag c = Kavling.berjalan(server, situs);
		if (c != null) {
			c.putInt("jual", c.getIntOr("jual", 0) + got);
		}
		return null;
	}

	/**
	 * Gives {@code satuan} units of kind {@code i} from the bag ({@code sumbang:BH:N}): no pay; the value counts as the
	 * player's share of the running plot and Nama Baik rises by 1 per 32 blocks. Null on success, else the refusal.
	 */
	public static @Nullable Component sumbang(ServerPlayer player, long situs, int i, int satuan) {
		MinecraftServer server = player.level().getServer();
		if (i < 0 || i >= JENIS.size() || satuan <= 0) {
			return Component.translatable("kultivator.kota42.tumbuh.bahan_salah");
		}
		int got = ambilTas(player, i, satuan);
		if (got <= 0) {
			return Component.translatable("kultivator.kota42.tumbuh.tas_kosong", Component.translatable("kultivator.kota42.tumbuh.bahan." + JENIS.get(i)));
		}
		tambah(server, situs, i, got);
		CompoundTag c = Kavling.berjalan(server, situs);
		if (c != null) {
			Kavling.catatDana(server, c, player, got * harga(server, situs, i));
		}
		int blocks = got * RencanaTumbuh.satuanBlok(server, i);
		PekerjaanTumbuh.namaBaik(player, situs, blocks / 32);
		return null;
	}

	// ================================================================== city orders (42.6.5)

	/** The city orders of a place: units the running stage still lacks, as blocks of the kind's order item. */
	static List<KontrakDagang.Pesanan> pesanan(MinecraftServer server, long situs) {
		int[] kurang = PekerjaanTumbuh.kurang(server, situs);
		List<KontrakDagang.Pesanan> out = new ArrayList<>();
		CompoundTag site = KotaTumbuhData.get(server).situs(KotaTumbuhData.BAHAN, situs);
		for (int i = 0; i < kurang.length; i++) {
			if (kurang[i] > 0) {
				int per = RencanaTumbuh.satuanBlok(server, i);
				int done = site.getIntOr("p" + i, 0);
				out.add(new KontrakDagang.Pesanan("kavling_" + JENIS.get(i), barangPesanan(i), kurang[i] * per + done, done));
			}
		}
		return out;
	}

	/** {@code n} blocks of order {@code id} were handed in: units added (leftover blocks kept toward the next unit). */
	static void terimaPesanan(MinecraftServer server, long situs, String id, int n) {
		if (id == null || !id.startsWith("kavling_")) {
			return;
		}
		int i = JENIS.indexOf(id.substring(8));
		if (i < 0 || n <= 0) {
			return;
		}
		CompoundTag site = KotaTumbuhData.get(server).situs(KotaTumbuhData.BAHAN, situs);
		int per = RencanaTumbuh.satuanBlok(server, i);
		int blocks = site.getIntOr("sisa" + i, 0) + n;
		tambah(server, situs, i, blocks / per);
		site.putInt("sisa" + i, blocks % per);
		site.putInt("p" + i, site.getIntOr("p" + i, 0) + n);
		KotaTumbuhData.get(server).ubah();
	}

	/** A stage started: the delivered counters of the orders begin again. */
	static void ulangPesanan(MinecraftServer server, long situs) {
		CompoundTag site = KotaTumbuhData.get(server).situs(KotaTumbuhData.BAHAN, situs);
		for (int i = 0; i < JENIS.size(); i++) {
			site.remove("p" + i);
		}
		KotaTumbuhData.get(server).ubah();
	}

	private BahanKota() {
	}
}
