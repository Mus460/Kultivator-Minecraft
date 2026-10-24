package dev.kultivator.dunia.tempat.perbaikan;

import java.util.UUID;

/**
 * A damage bill (DESIGN 38.R.4, owner 38PR): who owes how much to which place, since and until which world day, and
 * the billing style ({@code biasa}, {@code sekte}, {@code iblis} for the Blood Price of Kota Iblis, 38.T.14.3).
 */
public record Tagihan(long id, long situsId, UUID pembayar, long jumlah, long hariDibuat, long hariJatuhTempo, String gaya) {
}
