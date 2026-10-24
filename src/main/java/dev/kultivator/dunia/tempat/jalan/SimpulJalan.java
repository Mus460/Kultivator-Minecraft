package dev.kultivator.dunia.tempat.jalan;

/**
 * A road node of DESIGN 38.C.6.1 / 38.T.2.1: a point a road ends at (a capital gate, a town gate, a village centre, a
 * sect gate, a cave mouth, a themed-city land gate). Pure seed data; {@link RencanaJalan} joins nodes into edges.
 *
 * @param x     block x where the road stops (12 blocks outside the place box)
 * @param z     block z
 * @param jenis node kind ({@code kota_besar}, {@code kota}, {@code kampung}, {@code klan}, {@code sekte},
 *              {@code gua}, {@code kota_tema}, {@code pos})
 * @param kelas road class reaching it: {@link #KEKAISARAN}, {@link #DESA} or {@link #SETAPAK}
 * @param kunci stable id of the place (plan index or chunk hash), so an edge's two ends are known
 */
public record SimpulJalan(int x, int z, String jenis, int kelas, long kunci) {

	/** Jalan Kekaisaran: 5 wide, stone bricks (from capital gates). */
	public static final int KEKAISARAN = 0;
	/** Jalan Desa: 3 wide, dirt path and gravel. */
	public static final int DESA = 1;
	/** Jalan Setapak: 2 wide (cave branches). */
	public static final int SETAPAK = 2;
}
