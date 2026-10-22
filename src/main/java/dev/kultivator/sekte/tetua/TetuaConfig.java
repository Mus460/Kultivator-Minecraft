package dev.kultivator.sekte.tetua;

/**
 * Section {@code tetua} of {@code config/kultivator-sekte.json} (DESIGN 36.2.6): office hours, the pace of the daily
 * affairs, the Grand Elder acting for an absent master, and the days a sect must stand before the throne can pass.
 * Read once from {@link Tetua#init()}; {@code /kultivasi uji perguruan muat} copies new values into the same object.
 */
public final class TetuaConfig {

	/** World hour (0-23) the office holders sit down at their desks (06:00 is the overworld's tick 0). */
	public int jamMulai = 6;
	/** World hour (0-23) the offices close. */
	public int jamSelesai = 18;
	/** Real seconds between two daily affairs of one hall (one affair per hall at a time, DESIGN 36.3.9). */
	public int urusanDetik = 120;
	/** The Grand Elder accepts the best passed candidate of a recruitment day while the master is away from the hall. */
	public boolean terimaOtomatis = false;
	/** Days the sect must stand (since the hall was founded) before the Succession Ceremony. */
	public int takhtaHari = 30;
	/** The Grand Elder walks up with the morning report (off: only the card and the hub). */
	public boolean laporanAdegan = true;
	/** Disciples who join the succession ceremony's two rows (0-24). */
	public int barisanMaks = 12;

	static TetuaConfig clamp(TetuaConfig c) {
		c.jamMulai = Math.max(0, Math.min(23, c.jamMulai));
		c.jamSelesai = Math.max(0, Math.min(23, c.jamSelesai));
		if (c.jamSelesai == c.jamMulai) {
			c.jamSelesai = (c.jamMulai + 12) % 24;
		}
		c.urusanDetik = Math.max(20, Math.min(3600, c.urusanDetik));
		c.takhtaHari = Math.max(0, Math.min(3650, c.takhtaHari));
		c.barisanMaks = Math.max(0, Math.min(24, c.barisanMaks));
		return c;
	}
}
