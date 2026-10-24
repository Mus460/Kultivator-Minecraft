package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ujian Hati, the heart-demon trial at the great gates (DESIGN 39.5.2 step 4): entering Nascent Soul, Dao Union, the
 * fifth layer of Immortal Ascension and True Immortal. Rolled as data just before the start:
 * <pre>gagal = clamp(0.05 + iblis/200 + (60 - Kepuasan)/300 - drift/200 - 0.10 guruDekat - 0.04 sahabat - 0.10 heart_calming
 *          (+0.20 on the Iblis path), 0.02, 0.50)</pre>
 * Seen: three seconds of dark mist around the disciple (it trembles in its seat), a heartbeat. Lost: no breakthrough, Qi
 * Deviation 3600 ticks (the old number), the bar -10 %, temptation +15, a -5 memory "the heart demon won today", another
 * try after two days. Won: a +3 memory and the line "Iblis hati... tidak bisa menggoyahkanku."
 * <p>
 * Sumber: the 心魔 of the Ladder trial (36 G) and {@code trial/HeartDemons} for the player; the disciple's is lighter and
 * resolved as data.
 */
public final class UjianHati {

	/** Ticks of the visible trial. */
	public static final int DURASI = 60;
	public static final double DASAR = 0.05D;
	public static final double GURU_DEKAT = 0.10D;
	public static final double PER_SAHABAT = 0.04D;
	public static final double PIL_TENANG = 0.10D;
	public static final double JALAN_IBLIS = 0.20D;
	public static final double MIN = 0.02D;
	public static final double MAX = 0.50D;
	public static final double BAR_HILANG = 0.10D;
	public static final int IBLIS_KALAH = 15;
	public static final int ULANG_HARI = 2;
	private static final int KABUT = 0x2B1A3D;

	/** Test override of the next result per soul ({@code uji perguruan mandiri tumbuh hati}). */
	private static final Map<UUID, Boolean> PAKSA = new HashMap<>();

	/** True when the step out of {@code current} is a great gate. */
	public static boolean gerbang(Rank current) {
		if (current.isMax() || !current.isBottleneck()) {
			return false;
		}
		Rank next = current.next();
		if (next.realm() != current.realm()) {
			return next.realm() == Realm.NASCENT_SOUL || next.realm() == Realm.DAO_UNION || next.realm() == Realm.TRUE_IMMORTAL;
		}
		return next.realm() == Realm.IMMORTAL_ASCENSION && next.layer() == 4 && current.layer() == 3;
	}

	/** The failure chance for {@code entity} now (pure apart from the reads). */
	public static double peluangGagal(DiscipleEntity entity, int sahabat, boolean pilTenang) {
		DiscipleData data = entity.data();
		MinecraftServer server = entity.level().getServer();
		int iblis = MandiriApi.iblis(data);
		int kepuasan = SekteApi.hati().contentment(data);
		double drift = Ingatan.drift(data, DataTumbuh.hari(server));
		ServerPlayer master = entity.owner();
		boolean guruDekat = master != null && master.level() == entity.level() && master.distanceToSqr(entity) <= 16.0D * 16.0D;
		double chance = DASAR + iblis / 200.0D + (60 - kepuasan) / 300.0D - drift / 200.0D - (guruDekat ? GURU_DEKAT : 0.0D)
				- PER_SAHABAT * Math.max(0, sahabat) - (pilTenang ? PIL_TENANG : 0.0D);
		if (MandiriApi.jalan(data) == JalanHidup.IBLIS) {
			chance += JALAN_IBLIS;
		}
		return Math.max(MIN, Math.min(MAX, chance));
	}

	/** One second of the visible trial ({@code detik} 0..2): mist, a heartbeat, the trembling. */
	static void kabut(DiscipleEntity entity, int detik) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = entity.position().add(0.0D, 1.0D, 0.0D);
		Fx.burst(level, at, KABUT, 14 + 6 * detik, 1.4F + 0.3F * detik);
		Fx.ring(level, entity.position().add(0.0D, 0.1D, 0.0D), 0x5A2A6E, 1.6F + 0.4F * detik);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 0.7F + 0.1F * detik, 0.8F + 0.1F * detik);
		if (detik == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.NEUTRAL, 0.4F, 0.5F);
		}
	}

	/**
	 * Rolls the trial ({@code sahabat}: guards standing, {@code pilTenang}: a Heart Calming Pill taken in the preparation)
	 * and applies the result. True when the disciple won.
	 */
	static boolean uji(DiscipleEntity entity, int sahabat, boolean pilTenang) {
		Boolean forced = PAKSA.remove(entity.soulId());
		double fail = peluangGagal(entity, sahabat, pilTenang);
		boolean won = forced != null ? forced : entity.getRandom().nextDouble() >= fail;
		MinecraftServer server = entity.level().getServer();
		DiscipleData data = entity.data();
		Rank next = data.rank().next();
		if (won) {
			Ingatan.remember(server, data, "mandiri.hati_menang", null, 3, KabarTumbuh.P + "ingat.hati_menang", "");
			if (entity.level() instanceof ServerLevel level) {
				Fx.ring(level, entity.position().add(0.0D, 0.1D, 0.0D), 0xFFE9A8, 2.4F);
				Fx.burst(level, entity.position().add(0.0D, 1.2D, 0.0D), 0xFFF6D8, 24, 1.2F);
				level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.7F, 1.4F);
			}
			SekteSuara.line(entity, "hati_lulus", "cheer", 0);
			KabarTumbuh.catat(entity, "hati", "hati.lulus", EntriHarian.Suasana.BANGGA, true, "r:" + next.realm().id());
			return true;
		}
		data.setDeviationTicks(DiscipleBreakthrough.DEVIATION_TICKS);
		data.setXp(data.xp() * (1.0D - BAR_HILANG));
		DataTumbuh.ubahIblis(server, entity, IBLIS_KALAH);
		Ingatan.remember(server, data, "mandiri.hati_kalah", null, -5, KabarTumbuh.P + "ingat.hati_kalah", "");
		DataTumbuh.sub(data, "tembus").putLong("hatiGagal", DataTumbuh.hari(server));
		entity.refreshSynced();
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.DEVIATION, 0, 0, 1.0F);
			level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.NEUTRAL, 0.4F, 1.5F);
		}
		KabarTumbuh.catat(entity, "hati", "hati.gagal", EntriHarian.Suasana.SEDIH, true, "r:" + next.realm().id());
		KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEROBOSAN, KabarTumbuh.baris("hati.kalah", 0xC9A0FF, KabarTumbuh.nama(entity),
				next.displayName().withColor(next.realm().color()), ULANG_HARI));
		return false;
	}

	/** True while the last lost trial still bars a new attempt (two days). */
	static boolean masihTerluka(DiscipleEntity entity) {
		long lost = DataTumbuh.baca(entity.data()).getCompoundOrEmpty("tembus").getLongOr("hatiGagal", -100L);
		return DataTumbuh.hari(entity.level().getServer()) < lost + ULANG_HARI;
	}

	/** Forces the next result (test command). */
	public static void paksa(UUID soul, boolean menang) {
		PAKSA.put(soul, menang);
	}

	private UjianHati() {
	}
}
