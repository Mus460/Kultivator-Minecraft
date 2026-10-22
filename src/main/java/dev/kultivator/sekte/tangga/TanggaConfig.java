package dev.kultivator.sekte.tangga;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Section {@code tangga} of {@code config/kultivator-sekte.json} (DESIGN 36.2.4): module G's switches and numbers. The
 * old recruitment numbers ({@code Candidates}: tiers, verdicts, the amethyst shard per test) are not here and never
 * change; these only shape the climb that replaces the stele test while the stair stands.
 */
public final class TanggaConfig {

	/** Public fields are the defaults (Gson POJO, {@code SekteConfig} rules). */
	public static final class Values {
		/** Off: the recruitment day keeps the old stele test even where the stair stands (data and buildings stay). */
		public boolean aktif = true;
		/**
		 * The illusion's level of detail: {@code otomatis} follows the client's EffectQuality (Minimal 120, Normal 400,
		 * Maksimal 1000 drawn steps); {@code rendah}, {@code sedang}, {@code tinggi} force one. Sent to clients with each
		 * stair state.
		 */
		public String lod = "otomatis";
		/** The Ujian Hati re-climb of a disciple costs this much hall PK. */
		public int ujianHatiPK = 200;
		/** A disciple may re-climb once per this many in-game days. */
		public int ujianHatiHari = 30;
		/** Bond a disciple recruited as "Pendaki Puncak" starts with (added to whatever it gets). */
		public int bondPuncak = 5;
		/** Seconds of the climb itself (the gathering and the announcement come on top). 30-90. */
		public int detikPendakian = 52;
		/** Climbers that cannot be seen from this far (blocks) settle as data at once instead of climbing (64-160). */
		public int jarakTampil = 96;
		/** The "Puncak!" scene (SekteScenes beat 130) for the master near the stair. */
		public boolean adegan = true;
	}

	private static Values values = new Values();

	static void load() {
		values = SekteConfig.section(SekteIds.G, Values.class, Values::new, v -> {
			v.ujianHatiPK = Math.max(0, Math.min(100000, v.ujianHatiPK));
			v.ujianHatiHari = Math.max(1, Math.min(365, v.ujianHatiHari));
			v.bondPuncak = Math.max(0, Math.min(20, v.bondPuncak));
			v.detikPendakian = Math.max(30, Math.min(90, v.detikPendakian));
			v.jarakTampil = Math.max(64, Math.min(160, v.jarakTampil));
			if (v.lod == null || !(v.lod.equals("otomatis") || v.lod.equals("rendah") || v.lod.equals("sedang") || v.lod.equals("tinggi"))) {
				v.lod = "otomatis";
			}
			return v;
		});
	}

	public static Values get() {
		return values;
	}

	/** The LOD sent to clients: -1 automatic, 0 low, 1 medium, 2 high. */
	public static int lodCode() {
		return switch (values.lod) {
			case "rendah" -> 0;
			case "sedang" -> 1;
			case "tinggi" -> 2;
			default -> -1;
		};
	}

	private TanggaConfig() {
	}
}
