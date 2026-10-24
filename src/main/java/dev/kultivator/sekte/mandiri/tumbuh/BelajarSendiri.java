package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.KontribusiMurid;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.sekte.paviliun.PaviliunMandiri;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Belajar, studying without the master (DESIGN 39.5.5; executor BELAJAR of C39.B3, borrowing A's {@code ACT_STUDY} 13).
 * Four ways, tried in this order when the planner chooses to study:
 * <ol>
 *   <li><b>Listening</b>: the block was started by a senior's lesson ({@link AjarSenior}, {@code teman} = the senior):
 *   the junior sits cross-legged two blocks in front of the teacher ({@code ACT_MEDITASI_MANDIRI}, so the old meditation
 *   XP runs while it listens) until the lesson ends.</li>
 *   <li><b>The rack</b> of the Paviliun Kitab ({@code PaviliunMandiri.mintaBaca}): a seat at the {@code library}, A's
 *   machine does the reading, one technique a day (rule A). Seated: the old meditation tick gives the XP.</li>
 *   <li><b>A slip in the bag</b> ({@code PaviliunMandiri.pilihTas}, {@code bacaTas}): read at the quietest seat, the slip
 *   pressed to the forehead; the "breathing while working" XP of K 0.4 at the end of the block.</li>
 *   <li><b>Buying a slip</b> with its own Contribution Points at the Paviliun Yue Lu ({@code KontribusiMurid.beli}, the old
 *   grade prices, at most one a day), then reading it as above. An Ambisius disciple reaches for the dearest slip it can
 *   afford, everyone else for the cheapest of its own Dao.</li>
 * </ol>
 * Every gate is the old one ({@code specQualifies} through {@code RakKitab.blocker} or
 * {@code DiscipleCultivation.teachBlockerSelf}, the {@code teachBlocker} hooks asked without the master, the free
 * technique slots of {@code SectRanks}). The diary keeps what was read and bought.
 * <p>
 * Sumber: <i>A Record of a Mortal's Journey</i> ch. 153 (the Yue Lu Hall sells manuals for contribution), cited in
 * {@code sect/hall/Contribution}; <i>Demi-Gods and Semi-Devils</i> (the Shaolin Sutra Library), cited in
 * {@code sekte/paviliun/RakKitab}.
 */
public final class BelajarSendiri {

	/** The breath XP of reading from the bag (39.4.2: BELAJAR K 0.4). */
	public static final double K = 0.4D;

	enum Cara { DENGAR, RAK, TAS }

	private record Sesi(Cara cara, long mulai, @Nullable String spec) {
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();

	static void init() {
		MandiriApi.register(Kegiatan.BELAJAR, new Pelaksana() {
			@Override
			public boolean bisa(DiscipleEntity murid, Konteks konteks) {
				return boleh(murid, konteks.teman());
			}

			@Override
			public void mulai(DiscipleEntity murid, Konteks konteks) {
				mulaiDi(murid, konteks.tempat(), konteks.teman());
			}

			@Override
			public boolean jalan(DiscipleEntity murid, Konteks konteks) {
				Sesi sesi = SESI.get(murid.soulId());
				if (sesi == null) {
					return false;
				}
				return switch (sesi.cara()) {
					case DENGAR -> AjarSenior.mendengar(murid);
					case RAK -> PaviliunMandiri.sedangBaca(murid);
					case TAS -> PaviliunMandiri.sedangBacaTas(murid);
				};
			}

			@Override
			public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
				akhiri(murid, batal);
			}

			@Override
			public double K(DiscipleEntity murid, Konteks konteks) {
				// Seated at the rack or listening: the old meditation tick; the bag's breath XP is given in akhiri.
				return 0.0D;
			}
		});
	}

	static void clear() {
		SESI.clear();
	}

	/** The way the soul studies right now ({@code dengar}, {@code rak}, {@code tas}) or "" (the info line). */
	static String cara(UUID soul) {
		Sesi sesi = SESI.get(soul);
		return sesi == null ? "" : sesi.cara().name().toLowerCase(java.util.Locale.ROOT);
	}

	// ================================================================== choosing

	/** Something to study exists now: a lesson, the rack, a slip in the bag, or a slip it can buy and learn today. */
	static boolean boleh(DiscipleEntity entity, @Nullable UUID guru) {
		DiscipleData data = entity.data();
		if (data.breakingThrough() || data.deviating() || entity.ownerId() == null || TerobosanSendiri.sedang(entity.soulId())) {
			return false;
		}
		if (guru != null && AjarSenior.adaPelajaran(guru, entity.soulId())) {
			return true;
		}
		if (PaviliunMandiri.doneToday(entity) || SectRanks.slotsFull(data)) {
			return false;
		}
		return PaviliunMandiri.bisaBaca(entity) || PaviliunMandiri.pilihTas(entity) != null || slipDibeli(entity) != null;
	}

	/** The slip the disciple would buy today, or null (none affordable, the day's slip bought, no room in the bag). */
	static @Nullable TechniqueSpec slipDibeli(DiscipleEntity entity) {
		List<TechniqueSpec> list = KontribusiMurid.slipTerjangkau(entity, 6);
		if (list.isEmpty()) {
			return null;
		}
		boolean ambitious = dev.kultivator.sekte.api.SekteApi.hati().has(entity.data(), dev.kultivator.sekte.api.Sifat.AMBISIUS);
		TechniqueSpec spec = ambitious ? list.get(list.size() - 1) : list.get(0);
		return KontribusiMurid.blocker(entity, KontribusiMurid.Barang.TECHNIQUE, spec.id()) == null ? spec : null;
	}

	/** Starts the best way of studying near {@code saran}; false when nothing could begin. */
	static boolean mulaiDi(DiscipleEntity entity, @Nullable BlockPos saran, @Nullable UUID guru) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (guru != null && AjarSenior.duduk(entity, guru)) {
			SESI.put(entity.soulId(), new Sesi(Cara.DENGAR, now, null));
			return true;
		}
		if (PaviliunMandiri.mintaBaca(entity)) {
			SESI.put(entity.soulId(), new Sesi(Cara.RAK, now, PaviliunMandiri.bacaanRak(entity.data())));
			return true;
		}
		TechniqueSpec spec = PaviliunMandiri.pilihTas(entity);
		if (spec == null) {
			TechniqueSpec buy = slipDibeli(entity);
			if (buy != null) {
				int price = KontribusiMurid.hargaSlip(entity.ownerId(), buy);
				if (!KontribusiMurid.beli(entity, KontribusiMurid.Barang.TECHNIQUE, buy.id()).isEmpty()) {
					KabarTumbuh.catat(entity, "beli", "beli.slip", EntriHarian.Suasana.SENANG, false, "t:" + buy.id(), "n:" + price);
					SekteSuara.line(entity, "beli_slip", "thanks", 600);
					spec = PaviliunMandiri.pilihTas(entity);
				}
			}
		}
		if (spec == null) {
			return false;
		}
		BlockPos seat = TempatTumbuh.tenang(level, entity, saran);
		if (!PaviliunMandiri.bacaTas(entity, spec, seat)) {
			return false;
		}
		SESI.put(entity.soulId(), new Sesi(Cara.TAS, now, spec.id()));
		KabarTumbuh.catat(entity, "belajar", "belajar.tas_mulai", EntriHarian.Suasana.TENANG, false, "t:" + spec.id());
		return true;
	}

	// ================================================================== reading from the bag (once a second)

	/** Once a second while the disciple reads from its bag: A's points; the stamp, the diary and the master's line at the end. */
	static void tick20(DiscipleEntity entity) {
		if (!PaviliunMandiri.sedangBacaTas(entity)) {
			return;
		}
		String id = PaviliunMandiri.bacaanTas(entity.data());
		TechniqueSpec spec = id.isEmpty() ? null : dev.kultivator.data.TechniqueRegistry.byId(id);
		int step = PaviliunMandiri.langkahTas(entity);
		if (step <= 0 || spec == null) {
			return;
		}
		DataTumbuh.tambahStatistik(entity.data(), DataTumbuh.S_TEKNIK, 1);
		KabarTumbuh.catat(entity, "belajar", "belajar.tas_selesai", EntriHarian.Suasana.BANGGA, true, "t:" + spec.id());
		KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEKNIK, KabarTumbuh.baris("belajar.tas_selesai", 0x9FF5DC, KabarTumbuh.nama(entity),
				spec.displayName().copy().withColor(spec.grade().ribbon())));
		SekteSuara.line(entity, "belajar_sendiri", "cheer", 0);
	}

	// ================================================================== the end

	/** The block ends: the reading closes (points kept for another day), the bag's breath XP. */
	static void akhiri(DiscipleEntity entity, boolean batal) {
		Sesi sesi = SESI.remove(entity.soulId());
		if (sesi == null) {
			PaviliunMandiri.berhenti(entity);
			return;
		}
		long lama = Math.max(0L, entity.level().getGameTime() - sesi.mulai());
		switch (sesi.cara()) {
			case DENGAR -> AjarSenior.bangkit(entity);
			case RAK -> PaviliunMandiri.berhenti(entity);
			case TAS -> {
				PaviliunMandiri.berhenti(entity);
				if (!batal) {
					LajuMandiri.akhirBlok(entity, K, lama);
				}
			}
		}
	}

	private BelajarSendiri() {
	}
}
