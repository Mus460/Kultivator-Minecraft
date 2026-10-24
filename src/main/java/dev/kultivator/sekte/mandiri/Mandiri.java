package dev.kultivator.sekte.mandiri;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;

/**
 * Murid Mandiri (DESIGN 39 and 39.F): the one entry of the six modules 39A-39F, called from {@code SekteContent.init}
 * on both physical sides ({@link #registerContent} always, {@link #init} while the phase is on).
 * <p>
 * Pattern 36.4: server packages {@code dev.kultivator.sekte.mandiri.<sub>} with {@code <Kelas>.init()}; client packages
 * {@code dev.kultivator.client.sekte.mandiri.<sub>} (entry {@code MandiriClient}, called from {@code SekteContentClient});
 * assets {@code textures/sekte/mandiri/<sub>/}, prop pieces {@code items/fx/sekte_mandiri_<sub>_*.json}, advancements
 * {@code data/kultivator/advancement/sekte/mandiri_*.json} ({@code minecraft:impossible}), lang
 * {@code lang-src/sekte_mandiri_<sub>.*} (prefix {@code kultivator.sekte.mandiri.<sub>.}), own payloads (never
 * {@code network/Payloads} or {@code Net}). A module imports only {@code sekte.mandiri.api.*}, {@code sekte.*},
 * {@code sekte.api.*} and old code, never another 39 package (except through {@link MandiriApi}).
 * <p>
 * Every sub-module line below is active from wave W0 against its stub (schedule matrix row 11); afterwards only 39A edits
 * this file. Runtime blocks never change here; disciples never die through DESIGN 39 while {@code bolehGugur} is false.
 */
public final class Mandiri {

	private static boolean content;
	private static boolean initialised;

	/** K1: registry content of all six modules (payload types, data components, entity types), always registered. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		// 39B Tumbuh Sendiri:
		dev.kultivator.sekte.mandiri.tumbuh.Tumbuh.registerContent();
		// 39C Perjalanan & Kota:
		dev.kultivator.sekte.mandiri.jalan.Perjalanan.registerContent();
		// 39D Kerja, Kerajinan & Pergaulan:
		dev.kultivator.sekte.mandiri.kerja.Kerja.registerContent();
		// 39E Cerita, Layar & Suara:
		dev.kultivator.sekte.mandiri.cerita.Cerita.registerContent();
		// 39F Perseteruan antar-sekte:
		dev.kultivator.sekte.mandiri.seteru.Seteru.registerContent();
	}

	/** From {@code SekteContent.init} (both sides) while {@code umum.aktif}; the module switch is checked here. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		MandiriConfig.load();
		if (!SekteContent.on(MandiriIds.MODUL) || !MandiriConfig.get().aktif) {
			return;
		}
		MandiriCommands.init();
		// 39.7.2: an explicit order of the master (DiscipleEntity.setMode) pauses the planner for that disciple.
		DiscipleEntity.addModeListener(MandiriApi::onPerintah);
		// 39A Inti & Perencana (planner, scoring, rhythm, policy, catch-up, paths, goals, temptation). The defaults come
		// first so a later module may replace them: 39C its own money book (setKas), 39E its diary writer (setPencatat).
		MandiriApi.setSumberKebijakan(KebijakanAula.INSTANCE);
		MandiriApi.setKas(KejarKetinggalan.KAS_BAWAAN);
		MandiriApi.setPencatat((owner, soul, entri) -> MandiriApi.simpanHarian(Perencana.server(), owner, soul, entri));
		PelaksanaDasar.init();
		Perencana.init();
		CitaCita.init();
		JalanIblis.init();
		MandiriUji.init();
		// 39B Tumbuh Sendiri:
		dev.kultivator.sekte.mandiri.tumbuh.Tumbuh.init();
		// 39C Perjalanan & Kota:
		dev.kultivator.sekte.mandiri.jalan.Perjalanan.init();
		// 39D Kerja, Kerajinan & Pergaulan:
		dev.kultivator.sekte.mandiri.kerja.Kerja.init();
		// 39E Cerita, Layar & Suara:
		dev.kultivator.sekte.mandiri.cerita.Cerita.init();
		// 39F Perseteruan antar-sekte:
		dev.kultivator.sekte.mandiri.seteru.Seteru.init();
	}

	private Mandiri() {
	}
}
