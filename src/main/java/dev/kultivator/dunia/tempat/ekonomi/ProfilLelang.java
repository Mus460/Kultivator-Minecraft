package dev.kultivator.dunia.tempat.ekonomi;

import java.util.List;

/**
 * The profile of one auction session of the single auction engine {@code dunia/kota/BalaiLelang} (DESIGN 38.M.2.3, public
 * for 38TK's Lelang Gelap): how many lots, how many consignments a player may hand in, the seller commission (a sink into
 * the town treasury, {@link KasKota}), where the NPC bidders come from and which lot table of
 * {@code data/kultivator/tempat/lelang/<tabelLot>.json} fills the lots ({@link LelangAgung#buatLot}).
 * <p>
 * Bidder sources: {@code saingan} (members of the rival groups, 34.5.3), {@code sekte} (masters of the NPC sects of
 * {@code DiplomasiService}), {@code tokoh} (the capital's recurring characters Gu Lianshan and Qian Duoduo), {@code iblis}
 * and {@code iblis_tokoh} (demonic sect masters and the shadow brokers of the demon city). An unknown source adds
 * nobody.
 *
 * @param lot           lots per session (1-12)
 * @param titipMaks     consignments per player and session (0 = none taken)
 * @param komisi        seller commission 0-0.25 (also taken from every lot a player wins)
 * @param sumberPenawar bidder sources, in order
 * @param tabelLot      lot table id ({@code kota} keeps the town's own band pool of 34.5.1)
 */
public record ProfilLelang(int lot, int titipMaks, double komisi, List<String> sumberPenawar, String tabelLot) {

	/** The town Balai Lelang: the numbers of 34.5.1 (5 lots, 2 consignments, no commission, rival groups). */
	public static final ProfilLelang KOTA = new ProfilLelang(5, 2, 0.0D, List.of("saingan"), "kota");
	/**
	 * The capital's Lelang Agung (38.M.2.3): 8 lots (3 low, 3 middle, 2 high band relative to the strongest player present),
	 * 4 consignments, 10 % commission, rival groups + NPC sect masters + the capital's characters.
	 */
	public static final ProfilLelang AGUNG = new ProfilLelang(8, 4, 0.10D, List.of("saingan", "sekte", "tokoh"), "agung");

	public ProfilLelang {
		lot = Math.clamp(lot, 1, 12);
		titipMaks = Math.clamp(titipMaks, 0, 8);
		komisi = Math.clamp(komisi, 0.0D, 0.25D);
		sumberPenawar = List.copyOf(sumberPenawar);
		tabelLot = tabelLot == null || tabelLot.isBlank() ? "kota" : tabelLot;
	}

	/** True for the town profile (consignments go through {@code Titipan}, lots from the 34.5.1 pool). */
	public boolean kota() {
		return "kota".equals(tabelLot);
	}

	/** The commission actually taken: the profile's own, or {@code ekonomi38.komisiLelang} for the Grand Auction. */
	public double komisiBerlaku() {
		return "agung".equals(tabelLot) ? EkonomiConfig.get().komisiLelang : komisi;
	}

	/** Lot class of lot {@code i}: 0 low, 1 middle, 2 high (3/3/2 for 8 lots, scaled for other counts). */
	public int kelas(int i) {
		if (lot <= 2) {
			return Math.min(i, 1) + 1;
		}
		int rendah = Math.round(lot * 3.0F / 8.0F);
		int tinggi = Math.max(1, Math.round(lot * 2.0F / 8.0F));
		if (i < rendah) {
			return 0;
		}
		return i >= lot - tinggi ? 2 : 1;
	}
}
