package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.PasarMurid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The default disciple market (contract C39.K1, DESIGN 39.6.5): what a town sells to disciples of every hall, without
 * touching the player's shop ({@code Toko}). Prices are {@code Harga.BASE} (pills, talismans, teas), the herb curve
 * ({@code Harga.herbValue}), the dish prices of 38.M.2.2 and the sword/robe rows of {@link Perlengkapan}, times a
 * town-day factor 0.90-1.30 seeded by (town, day, item) and {@code KotaConfig.priceScale}. A small town sells wares up to
 * band 5; a capital, a themed city and the Immortal City everything. Each kind of goods has {@code stokMuridHari} units
 * per town and day for all disciples together ({@link JalanData#terbeli}). 38EK may replace it with
 * {@code MandiriApi.setPasar}.
 */
public final class PasarBawaan implements PasarMurid {

	public static final PasarBawaan INSTANCE = new PasarBawaan();

	/** Herb ages a town pavilion sells (never above {@code Harga.SHOP_HERB_MAX_AGE}). */
	static final int[] UMUR_HERBA = {60, 100, 200, 300};
	/** Dishes of 38.M.2.2: id, NB. */
	static final String[][] HIDANGAN = {{"kultivator:hidangan/bakpao_roh", "2"}, {"kultivator:hidangan/sup_ginseng_roh", "6"},
			{"kultivator:hidangan/mi_panjang_umur", "12"}, {"kultivator:hidangan/arak_bunga_persik", "25"}};
	/** Teas of module K. */
	static final String[] DAFTAR_TEH = {"teh_embun_pagi", "teh_awan_ungu", "teh_penenang_hati"};
	/** Gifts: two teas, the peach wine, a steamed bun and a flower from the flower girl (1 NB). */
	static final String[] DAFTAR_HADIAH = {"kultivator:teh_awan_ungu", "kultivator:hidangan/arak_bunga_persik", "kultivator:hidangan/bakpao_roh",
			"minecraft:peony", "kultivator:teh_penenang_hati"};

	private static @Nullable MinecraftServer server;

	static void server(@Nullable MinecraftServer value) {
		server = value;
	}

	private PasarBawaan() {
	}

	@Override
	public List<Tawaran> tawaran(ServerLevel level, long town, String jenis, long hari) {
		List<Tawaran> out = new ArrayList<>();
		int maxBand = bandMaks(level.getServer(), town);
		switch (jenis) {
			case PIL -> {
				for (Harga.Ware ware : Harga.PILLS) {
					if (ware.base() > 0L && ware.minBand() <= maxBand && Harga.exists(ware.id())) {
						add(out, town, Kultivator.MOD_ID + ":" + ware.id(), hari, jenis);
					}
				}
			}
			case HERBA -> {
				for (int age : UMUR_HERBA) {
					if (age <= Harga.SHOP_HERB_MAX_AGE) {
						add(out, town, Kultivator.MOD_ID + ":herb@" + age, hari, jenis);
					}
				}
			}
			case JIMAT -> {
				for (Harga.Ware ware : Harga.TREASURES) {
					if (ware.base() > 0L && ware.id().endsWith("talisman") && ware.minBand() <= maxBand) {
						add(out, town, Kultivator.MOD_ID + ":" + ware.id(), hari, jenis);
					}
				}
				add(out, town, Kultivator.MOD_ID + ":storage_pouch", hari, jenis);
			}
			case SENJATA -> {
				for (int tier = 1; tier <= Math.min(Perlengkapan.PEDANG_TOKO_MAKS, 1 + maxBand / 2); tier++) {
					add(out, town, "kultivator:pedang_tier_" + tier, hari, jenis);
				}
			}
			case JUBAH -> {
				for (int tier = 1; tier <= Perlengkapan.JUBAH_MAKS; tier++) {
					add(out, town, "kultivator:jubah_tier_" + tier, hari, jenis);
				}
			}
			case MAKANAN -> {
				for (String[] dish : HIDANGAN) {
					add(out, town, dish[0], hari, jenis);
				}
			}
			case TEH -> {
				for (String tea : DAFTAR_TEH) {
					if (Harga.exists(tea)) {
						add(out, town, Kultivator.MOD_ID + ":" + tea, hari, jenis);
					}
				}
			}
			case HADIAH -> {
				for (String gift : DAFTAR_HADIAH) {
					add(out, town, gift, hari, jenis);
				}
			}
			default -> {
				// gulungan: no town sells technique slips without 38's scroll rack (the Paviliun Yue Lu sells them for PK)
			}
		}
		return out;
	}

	private static void add(List<Tawaran> out, long town, String itemId, long hari, String jenis) {
		long price = INSTANCE.harga(town, itemId, hari);
		if (price > 0L) {
			int left = Math.max(0, MandiriConfig.get().stokMuridHari - terbeli(town, itemId, hari));
			out.add(new Tawaran(itemId, left, price, jenis));
		}
	}

	@Override
	public long harga(long town, String itemId, long hari) {
		long base = dasar(itemId, benih(town, hari, itemId));
		if (base <= 0L) {
			return -1L;
		}
		double scale = Kota.config().priceScale;
		return Math.max(1L, Math.round(base * faktor(town, hari, itemId) * scale));
	}

	@Override
	public boolean pesan(long town, String itemId, int n, long hari) {
		MinecraftServer srv = server;
		if (srv == null || n <= 0) {
			return false;
		}
		JalanData data = JalanData.get(srv);
		if (data.terbeli(town, itemId, hari) + n > MandiriConfig.get().stokMuridHari) {
			return false;
		}
		data.tambahTerbeli(town, itemId, n, hari);
		return true;
	}

	/** The base NB of an item id before the town-day factor (-1 when towns do not sell it to disciples). */
	static long dasar(String itemId, long benih) {
		if (itemId == null) {
			return -1L;
		}
		if (Perlengkapan.semu(itemId)) {
			return Perlengkapan.harga(itemId, benih);
		}
		int at = itemId.indexOf('@');
		if (at >= 0) {
			try {
				return Harga.herbValue(Integer.parseInt(itemId.substring(at + 1)));
			} catch (NumberFormatException e) {
				return -1L;
			}
		}
		for (String[] dish : HIDANGAN) {
			if (dish[0].equals(itemId)) {
				return Long.parseLong(dish[1]);
			}
		}
		if ("minecraft:peony".equals(itemId)) {
			return 1L;
		}
		String path = itemId.startsWith(Kultivator.MOD_ID + ":") ? itemId.substring(Kultivator.MOD_ID.length() + 1) : null;
		if (path == null) {
			return -1L;
		}
		long base = Harga.base(path);
		return base > 0L ? base : -1L;
	}

	/** The town-day factor 0.90-1.30 (the same nature as {@code Toko}'s daily prices). */
	static double faktor(long town, long hari, String itemId) {
		long z = benih(town, hari, itemId);
		return 0.90D + Math.floorMod(z, 41L) / 100.0D;
	}

	static long benih(long town, long hari, String itemId) {
		long z = town * 0x9E3779B97F4A7C15L ^ hari * 0xBF58476D1CE4E5B9L ^ (itemId == null ? 0 : itemId.hashCode()) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		z *= 0xD6E8FEB86659FD93L;
		return z ^ (z >>> 32);
	}

	private static int terbeli(long town, String itemId, long hari) {
		MinecraftServer srv = server;
		return srv == null ? 0 : JalanData.get(srv).terbeli(town, itemId, hari);
	}

	/** The highest ware band a town sells to disciples: small towns 5, the capital, themed and immortal cities 8. */
	static int bandMaks(@Nullable MinecraftServer srv, long town) {
		if (srv == null) {
			return 5;
		}
		String jenis = Towns.jenis(srv, town);
		return Towns.JENIS_KOTA.equals(jenis) ? 5 : Harga.BANDS - 1;
	}
}
