package dev.kultivator.sekte.misi;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Section {@code misi} of {@code config/kultivator-sekte.json} (DESIGN 36.2.7, module X). Every number here is X's own;
 * the existing expedition board ({@code sect.event.Expeditions}) keeps all of its numbers.
 */
public final class MisiConfig {

	/** Story missions on/off (the board stays, nothing new is offered or sent while off; running teams still come home). */
	public boolean aktif = true;
	/** Story missions one hall may run at the same time (1-4). */
	public int misiMaksAktif = 2;
	/** Chance that a successful mission (2+ stages) brings youths home as candidates (0-1). */
	public double peluangCalon = 0.20D;
	/** The longest mission in in-game days (1-3); longer templates are shortened to it. */
	public int durasiMaksHari = 3;
	/** Disciples per team (1-4). */
	public int timMaks = 4;
	/** The stage chance at mission tier 1 before every modifier (0.3-0.9). */
	public double peluangDasar = 0.70D;
	/** How much the base chance drops per mission tier above 1 (0-0.05). */
	public double turunPerTingkat = 0.02D;
	/** A returning team appears outside the gate and walks in (false: they appear at the stele like an expedition). */
	public boolean kembaliLewatGerbang = true;
	/** Blocks beyond the {@code arrival} anchor where the team appears (6-24). */
	public int jarakGerbang = 12;
	/** The short 7b scene when the team is home (the client's Kamera sinematik setting still decides). */
	public boolean adegan = true;
	/** Offers on the story board per day by hall level 1-3 / 4-6 / 7-9 (each 1-6). */
	public int[] papanPerHari = {3, 4, 5};
	/** Show the stage news in the master's chat while the team is away. */
	public boolean kabarTahap = true;

	private static MisiConfig current = new MisiConfig();

	static MisiConfig load() {
		current = SekteConfig.section(SekteIds.X, MisiConfig.class, MisiConfig::new, MisiConfig::clamp);
		return current;
	}

	public static MisiConfig get() {
		return current;
	}

	private static MisiConfig clamp(MisiConfig c) {
		c.misiMaksAktif = Math.max(1, Math.min(4, c.misiMaksAktif));
		c.peluangCalon = Math.max(0.0D, Math.min(1.0D, c.peluangCalon));
		c.durasiMaksHari = Math.max(1, Math.min(3, c.durasiMaksHari));
		c.timMaks = Math.max(1, Math.min(4, c.timMaks));
		c.peluangDasar = Math.max(0.30D, Math.min(0.90D, c.peluangDasar));
		c.turunPerTingkat = Math.max(0.0D, Math.min(0.05D, c.turunPerTingkat));
		c.jarakGerbang = Math.max(6, Math.min(24, c.jarakGerbang));
		if (c.papanPerHari == null || c.papanPerHari.length != 3) {
			c.papanPerHari = new int[]{3, 4, 5};
		}
		for (int i = 0; i < 3; i++) {
			c.papanPerHari[i] = Math.max(1, Math.min(6, c.papanPerHari[i]));
		}
		return c;
	}
}
