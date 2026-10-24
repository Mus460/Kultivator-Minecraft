package dev.kultivator.sekte.mandiri;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The lazy off-screen simulation (DESIGN 39.4.7) and the daily pocket money (39.6.4). A body that loads after more than
 * {@link #AMBANG} ticks catches up in closed form, never hour by hour:
 * <ol>
 * <li>{@code D = min(now - terakhir, kejarMaksHari x 24000) / 24000} days;</li>
 * <li>XP {@code = D x 50 x SUM_K(path) x meditationBaseTanpaMode x xpMultiplier(no body, MEDITATION) x seat factor (at most
 * the measured best seat, 1.0-1.5) x skalaLuarLayar x skalaXp} through {@code DiscipleCultivation.distribute}: a full bar
 * at a bottleneck keeps the rest as Stored Qi and no breakthrough ever happens off-screen (the planner queues it);</li>
 * <li>the drives go to the path's resting point;</li>
 * <li>{@code min(10, ceil(D / 2))} small events, each a diary page dated on its past day (the modules' own
 * {@code MandiriApi.PeristiwaKejar} first: a technique from the rack, pills brewed; then the path's own: tea with a
 * friend, a dawn kata, an insight of the Dao...);</li>
 * <li>pocket money for the missed days from the hall pool at once (1 PK = 2 NB, partly when the pool runs dry);</li>
 * <li>{@code terakhir = now}.</li>
 * </ol>
 * At most four disciples are processed per tick (the planner's queue). A Patuh hall does not catch up: behaviour exactly
 * as before 39 (a body that is not loaded does not cultivate).
 */
public final class KejarKetinggalan {

	/** More than two world hours away counts as a catch-up. */
	public static final long AMBANG = 2400L;
	/** At most this many events per catch-up. */
	public static final int PERISTIWA_MAKS = 10;
	/** NB per PK of the sect rate ({@code Harga.NB_PER_PK}). */
	public static final int NB_PER_PK = 2;

	/** The sum of K over a typical day of the path (39.5.1 table), seclusion counted as 1.9 per hour. */
	public static double sigmaK(JalanHidup jalan) {
		if (jalan == null) {
			return 19.5D;
		}
		return switch (jalan) {
			case PERTAPA -> 28.0D;
			case CENDEKIA, PENJAGA, ALKEMIS, FORMASI -> 19.5D;
			case PEDANG, PEMBURU -> 18.0D;
			case PETUALANG, PEDAGANG -> 18.0D;
			case IBLIS -> 20.0D;
		};
	}

	/** Meditation XP per second before multipliers without the x1.5 of an ordered meditation (39.5.1). */
	public static double baseTanpaMode(DiscipleData data) {
		return Math.max(0.4D + 0.2D * data.realm().ordinal(), Meditation.baseRate(data.rank()) * DiscipleCultivation.DISCIPLE_BASE_SHARE);
	}

	/** The seat factor for the catch-up: the best measured seat, between 1.0 and 1.5. */
	public static double faktorTempat(DiscipleData data) {
		double measured = MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_TEMPAT_TERBAIK).getDoubleOr("faktor", 1.0D);
		return Math.max(1.0D, Math.min(1.5D, measured));
	}

	/** XP of one off-screen day (raw x multipliers), for the catch-up and the test projection. */
	public static double xpHarian(DiscipleData data) {
		MandiriConfig config = MandiriConfig.get();
		double sigma = sigmaK(MandiriApi.jalan(data));
		return 50.0D * sigma * baseTanpaMode(data) * DiscipleCultivation.xpMultiplier(null, data, XpSource.MEDITATION)
				* faktorTempat(data) * config.skalaLuarLayar * config.skalaXp;
	}

	/** True when this body has been away long enough to catch up. */
	public static boolean perlu(DiscipleData data, long now) {
		long last = MandiriApi.bagian(data).getLongOr(MandiriIds.K_TERAKHIR, 0L);
		return last > 0L && now - last > AMBANG;
	}

	/**
	 * Catches up {@code ticks} of absence (the {@code lompat} test passes its own); returns the XP given. Writes
	 * {@code terakhir}.
	 */
	public static double jalankan(DiscipleEntity murid, long ticks) {
		DiscipleData data = murid.data();
		ServerLevel level = (ServerLevel) murid.level();
		long now = level.getGameTime();
		CompoundTag section = MandiriApi.bagianTulis(data);
		section.putLong(MandiriIds.K_TERAKHIR, now);
		Kebijakan kebijakan = MandiriApi.kebijakan(murid);
		if (kebijakan.mode() == ModeMandiri.PATUH || ticks <= 0L) {
			return 0.0D;
		}
		MandiriConfig config = MandiriConfig.get();
		double hari = Math.min(ticks, config.kejarMaksHari * 24000L) / 24000.0D;

		// 2. Closed-form XP (no breakthrough: distribute stops at a bottleneck).
		double sigma = sigmaK(MandiriApi.jalan(data));
		long tutupSampai = section.getCompoundOrEmpty(MandiriIds.K_TUTUP).getLongOr("sampai", 0L);
		double tutupHari = Math.max(0.0D, Math.min(hari, (Math.min(tutupSampai, now) - (now - ticks)) / 24000.0D));
		double perHari = 50.0D * baseTanpaMode(data) * DiscipleCultivation.xpMultiplier(null, data, XpSource.MEDITATION)
				* faktorTempat(data) * config.skalaLuarLayar * config.skalaXp;
		double gain = perHari * ((hari - tutupHari) * sigma + tutupHari * 1.9D * 24.0D);
		if (gain > 0.0D && Double.isFinite(gain)) {
			data.addTotalXp(gain);
			int ups = DiscipleCultivation.distribute(data, gain);
			MandiriApi.tambahStatistik(data, MandiriIds.S_JAM_KULTIVASI, Math.round(hari * 14.0D));
			if (ups > 0) {
				murid.refreshSynced();
			}
		} else {
			gain = 0.0D;
		}

		// 3. Drives at rest; habits fade; temptation moves for the missed days.
		long today = Irama.hari(level);
		int hariBulat = (int) Math.floor(hari);
		Dorongan.setimbang(murid, today);
		PilihJalan.pudarkan(data, hariBulat);
		JalanIblis.harian(murid, hariBulat);

		// 4. Small events dated on their past days.
		int n = Math.min(PERISTIWA_MAKS, (int) Math.ceil(hari / 2.0D));
		peristiwa(murid, n, today, Math.max(1, hariBulat));

		// 5. Pocket money of the missed days.
		long missed = Math.max(0L, today - section.getLongOr(MandiriIds.K_SAKU_HARI, today));
		if (missed > 0L) {
			bayarSaku(murid, (int) Math.min(missed, config.kejarMaksHari), true);
		}
		section.putLong(MandiriIds.K_HARI, today);
		return gain;
	}

	// ================================================================== events

	private static void peristiwa(DiscipleEntity murid, int n, long today, int span) {
		if (n <= 0) {
			return;
		}
		RandomSource random = murid.getRandom();
		List<MandiriApi.PeristiwaKejar> modules = new ArrayList<>(MandiriApi.peristiwaKejar());
		for (int i = 0; i < n; i++) {
			long day = today - 1L - random.nextInt(Math.max(1, span));
			boolean done = false;
			if (!modules.isEmpty() && random.nextFloat() < 0.5F) {
				MandiriApi.PeristiwaKejar provider = modules.get(random.nextInt(modules.size()));
				try {
					done = provider.coba(murid, day, random);
				} catch (RuntimeException exception) {
					done = false;
				}
			}
			if (!done) {
				sendiri(murid, day, random);
			}
		}
	}

	/** The path's own small events (39.4.7 step 4 that 39A owns): tea with a friend, a kata, an insight, a quiet day. */
	private static void sendiri(DiscipleEntity murid, long day, RandomSource random) {
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		UUID soul = murid.soulId();
		JalanHidup jalan = MandiriApi.jalan(data);
		String nama = murid.getName().getString();
		int jam = 6 + random.nextInt(14);
		// Tea with a friend first when there is one (affinity +1..+3).
		if (owner != null && random.nextFloat() < 0.35F) {
			List<UUID> friends = SekteApi.hubungan().with(owner, soul, Ikatan.SAHABAT);
			if (friends.isEmpty()) {
				friends = SekteApi.hubungan().with(owner, soul, Ikatan.SAUDARA_ANGKAT);
			}
			if (!friends.isEmpty()) {
				UUID friend = friends.get(random.nextInt(friends.size()));
				SekteApi.hubungan().adjust(owner, soul, friend, 1 + random.nextInt(3), "mandiri.teh");
				DiscipleEntity body = Perencana.cari(owner, friend);
				String other = body != null ? body.getName().getString() : "";
				MandiriApi.catat(owner, soul, new EntriHarian(day, 19 + random.nextInt(3), "kejar",
						other.isEmpty() ? "kultivator.sekte.mandiri.inti.harian.kejar.teh_sendiri" : "kultivator.sekte.mandiri.inti.harian.kejar.teh",
						other.isEmpty() ? List.of(nama) : List.of(nama, other), EntriHarian.Suasana.SENANG, false));
				return;
			}
		}
		String kind = jalan == null ? "tenang" : switch (jalan) {
			case PEDANG -> random.nextBoolean() ? "kata_fajar" : "pedang_asah";
			case ALKEMIS -> random.nextBoolean() ? "herba_catat" : "kebun_embun";
			case FORMASI -> random.nextBoolean() ? "jimat_latih" : "formasi_ukur";
			case PERTAPA -> random.nextBoolean() ? "meditasi_panjang" : "air_terjun";
			case PETUALANG -> random.nextBoolean() ? "peta_baca" : "cerita_jalan";
			case PEMBURU -> random.nextBoolean() ? "jejak_latih" : "busur_rawat";
			case PEDAGANG -> random.nextBoolean() ? "hitung_batu" : "harga_catat";
			case CENDEKIA -> random.nextBoolean() ? "gulungan_salin" : "ajar_adik";
			case PENJAGA -> random.nextBoolean() ? "ronda_malam" : "gerbang_sapu";
			case IBLIS -> random.nextBoolean() ? "bisikan" : "bayangan";
		};
		// A Dao insight on quiet days for those who have one.
		if (data.dao() != null && ("meditasi_panjang".equals(kind) || "air_terjun".equals(kind) || "kata_fajar".equals(kind))) {
			data.addComprehension(0.5D);
		}
		EntriHarian.Suasana mood = jalan == JalanHidup.IBLIS ? EntriHarian.Suasana.TERGODA
				: random.nextInt(4) == 0 ? EntriHarian.Suasana.LELAH : EntriHarian.Suasana.TENANG;
		MandiriApi.catat(owner, soul, new EntriHarian(day, jam, "kejar", "kultivator.sekte.mandiri.inti.harian.kejar." + kind,
				List.of(nama), mood, false));
	}

	// ================================================================== pocket money (39.6.4)

	/**
	 * Pays {@code hari} days of pocket money (06:00 each day while loaded; the missed days at once on a catch-up): the rank
	 * table x the policy's percent, from the hall pool (1 PK = 2 NB, partly when it runs dry; that is only a diary line,
	 * never mistreatment), into the purse up to its limit (the overflow stays in the pool).
	 */
	public static long bayarSaku(DiscipleEntity murid, int hari, boolean ringkas) {
		DiscipleData data = murid.data();
		UUID owner = murid.ownerId();
		ServerLevel level = (ServerLevel) murid.level();
		long today = Irama.hari(level);
		CompoundTag section = MandiriApi.bagianTulis(data);
		section.putLong(MandiriIds.K_SAKU_HARI, today);
		if (owner == null || hari <= 0 || Halls.hallOf(owner) == null) {
			return 0L;
		}
		Kebijakan kebijakan = MandiriApi.kebijakan(murid);
		if (kebijakan.mode() == ModeMandiri.PATUH) {
			return 0L;
		}
		long want = (long) MandiriConfig.get().uangSakuJenjang(SectRanks.rank(data)) * kebijakan.uangSakuPersen() / 100L * hari;
		if (want <= 0L) {
			return 0L;
		}
		long room = Math.max(0L, MandiriApi.dompetMaks(data) - MandiriApi.dompet(data));
		want = Math.min(want, room);
		int pk = (int) Math.min(Integer.MAX_VALUE, (want + NB_PER_PK - 1) / NB_PER_PK);
		int paidPk = Math.min(pk, Halls.pool(owner));
		if (paidPk <= 0 || !Halls.spendPool(owner, paidPk)) {
			if (want > 0L) {
				catat(murid, today, "kultivator.sekte.mandiri.inti.harian.saku_kosong", EntriHarian.Suasana.SEDIH);
			}
			return 0L;
		}
		long nb = (long) paidPk * NB_PER_PK;
		long applied = MandiriApi.ubahDompet(data, nb, "saku");
		long back = nb - applied;
		if (back >= NB_PER_PK) {
			Halls.addPool(owner, (int) (back / NB_PER_PK));
		}
		if (paidPk < pk) {
			catat(murid, today, "kultivator.sekte.mandiri.inti.harian.saku_sebagian", EntriHarian.Suasana.LELAH);
		} else if (ringkas && hari > 1) {
			MandiriApi.catat(owner, murid.soulId(), new EntriHarian(today, 6, "saku", "kultivator.sekte.mandiri.inti.harian.saku_kumpul",
					List.of(murid.getName().getString(), String.valueOf(applied), String.valueOf(hari)), EntriHarian.Suasana.SENANG, false));
		}
		return applied;
	}

	private static void catat(DiscipleEntity murid, long today, String key, EntriHarian.Suasana mood) {
		MandiriApi.catat(murid.ownerId(), murid.soulId(), new EntriHarian(today, 6, "saku", key,
				List.of(murid.getName().getString()), mood, false));
	}

	/** The default money book (39C may replace it with {@code MandiriApi.setKas}): every purse change into {@code kas}. */
	static final MandiriApi.Kas KAS_BAWAAN = (data, delta, saldo, sebab) -> {
		MinecraftServer server = Perencana.server();
		DiscipleEntity body = server == null ? null : Perencana.cariData(data);
		if (server == null || body == null || body.ownerId() == null) {
			return;
		}
		MandiriData.get(server).catatKas(body.ownerId(), body.soulId(), Irama.hari(body.level()), delta, sebab);
	};

	private KejarKetinggalan() {
	}
}
