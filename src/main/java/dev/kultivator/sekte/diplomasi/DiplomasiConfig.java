package dev.kultivator.sekte.diplomasi;

/**
 * Section {@code diplomasi} of {@code config/kultivator-sekte.json} (DESIGN 36.2.9). The first five fields are the
 * design's; the rest are this module's own tuning (all clamped). {@code jumlahSekte} and {@code jarakMin} also decide where
 * the mountain gates are planned (world generation, new chunks only), so change them before a world is created.
 */
public final class DiplomasiConfig {

	/** NPC sects of the world (4-12). */
	public int jumlahSekte = 8;
	/** Least distance of a sect's mountain gate from the world centre in blocks (600-6000). */
	public int jarakMin = 1500;
	/** Most NPC sect events a hall gets per world day (0-4). */
	public int peristiwaPerHari = 2;
	/** False: no sect ever declares war (raids by hostile Iblis sects stay, they use the existing raid schedule). */
	public boolean perangAktif = true;
	/** False: no new mountain gate is generated (chunks already generated keep theirs). */
	public boolean strukturAktif = true;

	/** Members spawned at one loaded gate while a player is near (0-6). */
	public int anggotaMaks = 6;
	/** Seconds between two sect simulation turns (one sect per turn, 10-300). */
	public int simulasiDetik = 30;
	/** Allied guards that come to a live raid on the hall (0-2). */
	public int penjagaSekutu = 2;
	/** Envoys walk to the hall as bodies (false: offers arrive as letters only). */
	public boolean utusanBerwujud = true;
	/** Minutes (real time) an envoy body waits at the hall before it leaves (1-20). */
	public int utusanMenitTunggu = 4;

	static DiplomasiConfig clamp(DiplomasiConfig c) {
		c.jumlahSekte = Math.max(4, Math.min(12, c.jumlahSekte));
		c.jarakMin = Math.max(600, Math.min(6000, c.jarakMin));
		c.peristiwaPerHari = Math.max(0, Math.min(4, c.peristiwaPerHari));
		c.anggotaMaks = Math.max(0, Math.min(6, c.anggotaMaks));
		c.simulasiDetik = Math.max(10, Math.min(300, c.simulasiDetik));
		c.penjagaSekutu = Math.max(0, Math.min(2, c.penjagaSekutu));
		c.utusanMenitTunggu = Math.max(1, Math.min(20, c.utusanMenitTunggu));
		return c;
	}
}
