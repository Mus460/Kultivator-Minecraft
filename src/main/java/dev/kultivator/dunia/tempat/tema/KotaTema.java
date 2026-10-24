package dev.kultivator.dunia.tempat.tema;

/**
 * Kota Bertema (DESIGN 38.T, modules 38TK + 38TV): the server-side entry of the five themed towns, called once by
 * {@code Tempat.init()} (38W) on both physical sides, before {@code ModItems.init()} (items are registered here).
 * <p>
 * Wave W0 (38TK-0) lands the skeleton every later module compiles against: {@link Tema} (districts, salts, ring data),
 * {@link TemaConfig} (all six config sections of 38.T.13.1), {@link TemaApi} (C38.T1, safe defaults until 38TK inti
 * installs its provider), {@link TemaEntities} (C38.T2, all types), {@link TemaNet} (C38.T4 payload
 * {@code kultivator:tema_fx}), {@link TemaItems} + {@code bersama.DagangItems} (all items, K1), {@link KotaTemaData}
 * ({@code bagian(tema)} per theme), {@link TemaCommands#daftar}, {@link TemaKalender#daftar} and
 * {@code bersama.TemaLayar} (C38.T3 sheet keys). Wave W2: 38TK inti adds the plan ({@code RencanaKotaTema},
 * {@code PutusanTema}), anchors, trade, contracts, the calendar runner, {@code TemaSitus}, {@code TemaSibuk} and
 * {@code TemaApi.pasang(...)} here; each theme agent fills only its own package init ({@code pelabuhan.Pelabuhan},
 * {@code oasis.Oasis}, {@code air.Air}, {@code gunung.Gunung}, {@code iblis.Iblis}), already called below.
 * <p>
 * Rules: runtime never changes a block (towns come only from worldgen in new chunks; every moving part is a
 * {@code noSave} entity); never the weather, time or tick rate; every registry entry always registered, switches only
 * gate behaviour.
 */
public final class KotaTema {

	private static boolean initialised;

	/** From {@code Tempat.init()} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaConfig.muat();
		TemaItems.init();
		TemaEntities.init();
		TemaNet.init();
		TemaCommands.init();
		// 38TK inti (W2): plans, anchors, live bodies, busy states, calendar, meditation sites, the C38.T1 provider, story
		// words, trade and cargo contracts, the shared rhythm game, guild mission kinds and the test words
		RencanaKotaTema.daftar();
		RencanaPosPemburu.daftar();
		// built-in pack kultivator:dunia_kotatema holds every themed structure_set (46 districts + 2 satellites)
		dev.kultivator.dunia.DuniaPacks.worldgen("kotatema", TemaConfig.inti().aktif && TemaConfig.inti().worldgen);
		TemaAnchors.init();
		TemaMekanisme.init();
		TemaSibuk.init();
		TemaKalender.init();
		TemaSitus.init();
		TemaInti.init();
		dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.init();
		dev.kultivator.dunia.tempat.tema.bersama.DagangLayar.init();
		dev.kultivator.dunia.tempat.tema.bersama.KontrakMuatan.init();
		dev.kultivator.dunia.tempat.tema.bersama.TemaIrama.init();
		TemaMisi.init();
		TemaUji.init();
		// one init per theme package; each theme agent fills its own class in W2
		dev.kultivator.dunia.tempat.tema.pelabuhan.Pelabuhan.init();
		dev.kultivator.dunia.tempat.tema.oasis.Oasis.init();
		dev.kultivator.dunia.tempat.tema.air.Air.init();
		dev.kultivator.dunia.tempat.tema.gunung.Gunung.init();
		dev.kultivator.dunia.tempat.tema.iblis.Iblis.init();
	}

	private KotaTema() {
	}
}
