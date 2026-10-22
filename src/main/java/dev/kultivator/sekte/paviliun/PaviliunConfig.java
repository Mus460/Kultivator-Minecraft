package dev.kultivator.sekte.paviliun;

/**
 * Section {@code paviliun} of {@code config/kultivator-sekte.json} (DESIGN 36.2.5): the Paviliun Kitab's rack and
 * readers, the study pace, the cemetery and the ancestral rites. Read once from {@link Paviliun#init()};
 * {@code /kultivasi uji perguruan muat} copies new values into the same object.
 */
public final class PaviliunConfig {

	/** Master switch of the module's behaviour (off: no study, no new graves, no rites; the data stays). */
	public boolean aktif = true;
	/** Books the Rak Kitab holds at hall levels 7-9 (levels 1-3 hold a third, 4-6 two thirds); 3-27. */
	public int rakMaks = 27;
	/** Disciples reading in the pavilion at the same time (1-8). */
	public int pembacaMaks = 4;
	/** Study points of a grade-0 (Kuning) technique; a grade-g technique needs {@code poinPerTeknik x (g + 1)}. */
	public int poinPerTeknik = 600;
	/** Study speed multiplier on top of the formula (0.1-10; 1 = DESIGN). */
	public double kecepatanBelajar = 1.0D;
	/** Contribution Points paid to enshrine a grave's spirit tablet on the Altar Leluhur. */
	public int abadikanPK = 50;
	/** Days between two Qingming rites (3-60). */
	public int qingmingHari = 15;
	/** Graves kept when the sect has no built cemetery yet (they move to the Kitab Silsilah beyond it). */
	public int makamTanpaPemakaman = 8;
	/** Records kept in the Kitab Silsilah (the oldest are summarised beyond it, never silently lost: 20-500). */
	public int silsilahMaks = 200;
	/** Friends who mourn at a funeral kneel until the next morning, but at most this many real minutes (1-20). */
	public int berkabungMenitMaks = 10;
	/** Disciples who join the Qingming rows (0-24). */
	public int qingmingBarisan = 16;
	/** Start the Qingming automatically when it is due and the master is home in daylight. */
	public boolean qingmingOtomatis = true;
	/** Show the 7b scenes of the module (Qingming, funerals, enshrinement). The global camera setting still applies. */
	public boolean adegan = true;

	static PaviliunConfig clamp(PaviliunConfig c) {
		c.rakMaks = Math.max(3, Math.min(27, c.rakMaks));
		c.pembacaMaks = Math.max(1, Math.min(8, c.pembacaMaks));
		c.poinPerTeknik = Math.max(30, Math.min(100_000, c.poinPerTeknik));
		c.kecepatanBelajar = Double.isFinite(c.kecepatanBelajar) ? Math.max(0.1D, Math.min(10.0D, c.kecepatanBelajar)) : 1.0D;
		c.abadikanPK = Math.max(0, Math.min(100_000, c.abadikanPK));
		c.qingmingHari = Math.max(3, Math.min(60, c.qingmingHari));
		c.makamTanpaPemakaman = Math.max(1, Math.min(32, c.makamTanpaPemakaman));
		c.silsilahMaks = Math.max(20, Math.min(500, c.silsilahMaks));
		c.berkabungMenitMaks = Math.max(1, Math.min(20, c.berkabungMenitMaks));
		c.qingmingBarisan = Math.max(0, Math.min(24, c.qingmingBarisan));
		return c;
	}
}
