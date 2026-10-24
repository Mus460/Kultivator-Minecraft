package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import org.jetbrains.annotations.Nullable;

/**
 * The pace of growing alone (DESIGN 39.5.1, contract C39.B1). Meditation the planner chose runs through the old
 * meditation XP of {@code DiscipleCultivation.tick} (the seated activities 45, 54, 56 count as meditation); this hook only
 * adds bounded factors on the {@code MEDITATION} source:
 * <ul>
 *   <li>56 self-chosen meditation while the master's order is not MEDITATE: x1.5 (as the order gives), x1.0 under the
 *   order (no double);</li>
 *   <li>45 seclusion: x(1.8 + 0.05 per day of the retreat, at most 2.1) instead of the order's x1.5;</li>
 *   <li>54 absorbing spirit stones: K 3.0 instead of 1.5, x2.0;</li>
 *   <li>times {@code mandiri.skalaXp} (1.5, approved) on all of the above, never on ordered meditation or Master
 *   Guidance;</li>
 *   <li>the Iblis path's new moon x1.3 (39.3.3) and the spring blessing +20 % ({@code extra.mandiri.berkah}, 39.6.9).</li>
 * </ul>
 * Activities that are not seated meditation give their "breathing while working" XP at the end of the block through
 * {@link #akhirBlok} = {@code DiscipleCultivation.addXpKegiatan(50 x meditationBaseTanpaMode x K x skalaXp x hours)}.
 * {@link #kRataRata} is the day's average K of a path (the K̄ of the off-screen catch-up of 39.4.7, the projection).
 * Old numbers (XP_FACTOR 0.6, the rank factors, the formations) are untouched.
 */
public final class LajuMandiri {

	/** Seconds of one world hour (1000 ticks). */
	public static final double DETIK_PER_JAM = 50.0D;
	/** The seclusion factor: 1.8 + 0.05 per day, at most 2.1. */
	public static final double TUTUP_DASAR = 1.8D;
	public static final double TUTUP_PER_HARI = 0.05D;
	public static final double TUTUP_MAKS = 2.1D;
	/** Absorbing stones: K 3.0 against meditation's 1.5. */
	public static final double SERAP_FAKTOR = 2.0D;
	/** The order's factor inside {@code meditationBase}. */
	public static final double MODE_MEDITASI = 1.5D;
	/** The Iblis path meditating at new moon. */
	public static final double IBLIS_BULAN_BARU = 1.3D;
	/** The spring blessing. */
	public static final double BERKAH = 1.2D;

	static void init() {
		DiscipleHooks.addXpFactor(LajuMandiri::faktor);
	}

	/** The C39.B1 provider. */
	static double faktor(@Nullable DiscipleEntity entity, DiscipleData data, XpSource source) {
		if (entity == null || source != XpSource.MEDITATION || entity.role() != DiscipleEntity.Role.DISCIPLE || !MandiriApi.aktif()) {
			return 1.0D;
		}
		int act = entity.activity();
		boolean ordered = entity.mode() == DiscipleEntity.Mode.MEDITATE;
		double skala = MandiriConfig.get().skalaXp;
		double factor;
		switch (act) {
			case MandiriKegiatan.ACT_MEDITASI_MANDIRI -> factor = (ordered ? 1.0D : MODE_MEDITASI) * skala;
			case MandiriKegiatan.ACT_TUTUP_DIRI -> factor = faktorTutup(data, entity.level().getGameTime()) / (ordered ? MODE_MEDITASI : 1.0D) * skala;
			case MandiriKegiatan.ACT_SERAP_BATU -> factor = SERAP_FAKTOR * (ordered ? 1.0D : MODE_MEDITASI) * skala;
			default -> {
				return 1.0D;
			}
		}
		if (MandiriApi.jalan(data) == JalanHidup.IBLIS && DataTumbuh.malam(entity.level())
				&& entity.level().environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, entity.position()) == MoonPhase.NEW_MOON) {
			factor *= IBLIS_BULAN_BARU;
		}
		if (MandiriApi.bagian(data).getLongOr(MandiriIds.K_BERKAH, 0L) > entity.level().getGameTime()) {
			factor *= BERKAH;
		}
		return factor;
	}

	/** The seclusion factor for the retreat's current length (1.8 + 0.05/day, at most 2.1). */
	static double faktorTutup(DiscipleData data, long now) {
		CompoundTag tutup = MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_TUTUP);
		long mulai = tutup.getLongOr("mulai", now);
		double days = Math.max(0.0D, (now - mulai) / 24000.0D);
		return Math.min(TUTUP_MAKS, TUTUP_DASAR + TUTUP_PER_HARI * Math.floor(days));
	}

	/**
	 * The XP at the end of a block of a non-seated self-chosen activity: {@code 50 x meditationBaseTanpaMode x K x skalaXp
	 * x hours} through {@code DiscipleCultivation.addXpKegiatan}. Returns the XP added.
	 */
	public static double akhirBlok(DiscipleEntity entity, double k, long ticks) {
		if (k <= 0.0D || ticks <= 0L || !MandiriApi.aktif()) {
			return 0.0D;
		}
		double hours = ticks / 1000.0D;
		double amount = DETIK_PER_JAM * DiscipleCultivation.meditationBaseTanpaMode(entity.data()) * k * MandiriConfig.get().skalaXp * hours;
		return DiscipleCultivation.addXpKegiatan(entity, amount);
	}

	/**
	 * The average K per hour of a path's typical day without journeys (39.5.1 table, sum / 24): Pertapa 28, the balanced
	 * paths (Cendekia, Penjaga, Alkemis, Formasi, Iblis) 19.5, Pedang and Pemburu 18, Petualang and Pedagang 18 at home.
	 */
	public static double kRataRata(@Nullable JalanHidup jalan) {
		double perDay;
		if (jalan == null) {
			perDay = 19.5D;
		} else {
			perDay = switch (jalan) {
				case PERTAPA -> 28.0D;
				case PEDANG, PEMBURU, PETUALANG, PEDAGANG -> 18.0D;
				default -> 19.5D;
			};
		}
		return perDay / 24.0D;
	}

	/**
	 * The K of seclusion for the off-screen periods (39.5.7: K̄ 1.9) and of a running retreat's catch-up.
	 */
	public static final double K_TUTUP_LUAR_LAYAR = 1.9D;

	/**
	 * XP per world day of a disciple growing alone at its current rank (the projection's and the catch-up's rate):
	 * {@code 50 x 24 x meditationBaseTanpaMode x K̄ x xpMultiplier(null, MEDITATION) x seat x skalaXp}.
	 */
	public static double xpHarian(DiscipleData data, double kBar, double tempat, boolean luarLayar) {
		MandiriConfig config = MandiriConfig.get();
		double scale = config.skalaXp * (luarLayar ? config.skalaLuarLayar : 1.0D);
		return DETIK_PER_JAM * 24.0D * DiscipleCultivation.meditationBaseTanpaMode(data) * kBar
				* DiscipleCultivation.xpMultiplier(null, data, XpSource.MEDITATION) * Math.max(0.1D, tempat) * scale;
	}

	/** The measured best-seat factor ({@code extra.mandiri.tempatTerbaik.faktor}, 1.0 before 39A measured it). */
	public static double faktorTempat(DiscipleData data) {
		double value = MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_TEMPAT_TERBAIK).getDoubleOr("faktor", 1.0D);
		return Double.isFinite(value) && value > 0.0D ? Math.min(3.5D, value) : 1.0D;
	}

	private LajuMandiri() {
	}
}
