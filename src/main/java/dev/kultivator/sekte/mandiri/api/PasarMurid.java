package dev.kultivator.sekte.mandiri.api;

import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Contract C39.K1 (DESIGN 39.6.12): the market a disciple buys from in town. DESIGN 39 defines it; 38EK may register its
 * shared-stock market with {@code MandiriApi.setPasar}. Without one, 39C's {@code PasarBawaan} answers from
 * {@code Harga.BASE}/{@code Harga.PILLS} x a town-day factor (0.9-1.3 seeded by town, day and item) x
 * {@code KotaConfig.priceScale}, with {@code stokMuridHari} units per kind, town and day. A town is a packed
 * {@code BlockPos} ({@code BlockPos.asLong}) of its centre, as {@code Towns} keys them. The player's own shop stock
 * ({@code Toko}, 16 per day) is never reduced by disciples. Server side only.
 */
public interface PasarMurid {

	/** Kinds of goods ({@link Tawaran#jenis}). */
	String PIL = "pil";
	String HERBA = "herba";
	String JIMAT = "jimat";
	String SENJATA = "senjata";
	String JUBAH = "jubah";
	String GULUNGAN = "gulungan";
	String MAKANAN = "makanan";
	String TEH = "teh";
	String HADIAH = "hadiah";
	/** Every kind, in the order the town visit script walks the stalls. */
	List<String> JENIS = List.of(PIL, HERBA, JIMAT, SENJATA, JUBAH, GULUNGAN, MAKANAN, TEH, HADIAH);

	/** One offer: item id, units left for disciples today, price in NB per unit, kind. */
	record Tawaran(String itemId, int n, long harga, String jenis) {
		public Tawaran {
			itemId = itemId == null ? "" : itemId;
			n = Math.max(0, n);
			harga = Math.max(0L, harga);
			jenis = jenis == null ? "" : jenis;
		}
	}

	/** What the town offers disciples of kind {@code jenis} on world day {@code hari}. */
	List<Tawaran> tawaran(ServerLevel level, long town, String jenis, long hari);

	/** The NB price of one {@code itemId} in that town today, or -1 when it is not sold there. */
	long harga(long town, String itemId, long hari);

	/** Reserves {@code n} units (stock); false when not enough are left. Called after the purse paid (39.6.11). */
	boolean pesan(long town, String itemId, int n, long hari);

	/** No market: nothing on offer (39C's default market replaces it at init). */
	PasarMurid NONE = new PasarMurid() {
		@Override
		public List<Tawaran> tawaran(ServerLevel level, long town, String jenis, long hari) {
			return List.of();
		}

		@Override
		public long harga(long town, String itemId, long hari) {
			return -1L;
		}

		@Override
		public boolean pesan(long town, String itemId, int n, long hari) {
			return false;
		}
	};
}
