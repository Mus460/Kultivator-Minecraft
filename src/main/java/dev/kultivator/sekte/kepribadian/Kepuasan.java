package dev.kultivator.sekte.kepribadian;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Kepuasan, contentment 0-100 (DESIGN 36.2.1). Once per in-game day (on the disciple's first {@code tick20} after the
 * day changed; a disciple that was away catches up at most 5 days):
 * <pre>
 * delta = (60 - K) * 0.10 + Ingatan.drift * 0.25 + socialBalance / 20 + care + blessing * 20
 *         (+1 Penyayang with a friend, -1 proud with a rival, -2 for every grudge)
 * </pre>
 * a rise multiplied by the Discipline elder's office factor (module E), the whole step halved for a Tenang heart and
 * kept within +-8. Care of the day: taught +3, a pill +2, meditating within 8 blocks of the master +2, honours
 * ({@code SekteBus.DISCIPLE_HONORED}) +amount, at most +10 a day. Mistreatment takes its severity at once, times the
 * trait factor, and leaves a memory.
 */
public final class Kepuasan {

	/** The day step is kept within this. */
	public static final double DAY_CAP = 8.0D;
	public static final double CARE_TEACH = 3.0D;
	public static final double CARE_PILL = 2.0D;
	public static final double CARE_MEDITATE = 2.0D;
	/** Two blows of a flurry, a whole tournament of honours: memories are written at most once per kind and day anyway. */
	public static final int GRIEF_FRIEND_LEFT = 3;

	/**
	 * One day of the daily update for {@code e} ({@code day} the day that just ended, {@code today} the current day).
	 * Returns the step applied.
	 */
	static double daily(MinecraftServer server, DiscipleEntity e, UUID owner, long day, long today) {
		DiscipleData data = e.data();
		UUID soul = e.soulId();
		double k = Hati.contentmentExact(data);
		double step = (60.0D - k) * 0.10D;
		step += Ingatan.drift(data, today) * 0.25D;
		step += SekteApi.hubungan().socialBalance(owner, soul) / 20.0D;
		step += Hati.careOf(data, day);
		step += Math.max(0.0D, Math.min(0.15D, SekteApi.paviliun().blessing(owner))) * 20.0D;
		if (Hati.has(data, Sifat.PENYAYANG) && (!SekteApi.hubungan().with(owner, soul, Ikatan.SAHABAT).isEmpty()
				|| !SekteApi.hubungan().with(owner, soul, Ikatan.SAUDARA_ANGKAT).isEmpty())) {
			step += 1.0D * KepribadianConfig.get().faktorWatak;
		}
		if (Hati.has(data, Sifat.SOMBONG) && !SekteApi.hubungan().with(owner, soul, Ikatan.SAINGAN).isEmpty()) {
			step -= 1.0D * KepribadianConfig.get().faktorWatak;
		}
		step -= 2.0D * Math.min(3, SekteApi.hubungan().with(owner, soul, Ikatan.DENDAM).size());
		if (step > 0.0D) {
			step *= Math.max(1.0D, Math.min(1.5D, SekteApi.tetua().officeFactor(owner, Jabatan.TETUA_HUKUM)));
		}
		if (Hati.has(data, Sifat.TENANG)) {
			step *= KepribadianConfig.scaled(0.5D);
		}
		step = Math.max(-DAY_CAP, Math.min(DAY_CAP, step));
		Hati.setContentment(data, k + step);
		return step;
	}

	/** The trait factor of a mistreatment (DESIGN 36.2.1 table), scaled by {@code faktorWatak}. */
	static double factor(DiscipleData data, Perlakuan kind) {
		double f = 1.0D;
		switch (kind) {
			case DIPERMALUKAN -> {
				if (Hati.has(data, Sifat.SOMBONG)) {
					f *= 2.0D;
				}
			}
			case DIKORBANKAN -> {
				if (Hati.has(data, Sifat.BERANI)) {
					f *= 0.5D;
				}
			}
			case TIDAK_ADIL -> {
				if (Hati.has(data, Sifat.AMBISIUS)) {
					f *= 2.0D;
				}
			}
			case DIABAIKAN -> {
				if (Hati.has(data, Sifat.MALAS)) {
					f *= 0.5D;
				}
			}
			default -> {
			}
		}
		return KepribadianConfig.scaled(f);
	}

	/**
	 * Applies a mistreatment to the loaded disciple: contentment falls by {@code severity * factor} and a memory of weight
	 * {@code -severity / 2} (one more for a Pendendam heart) is written, kind {@code kepribadian.<perlakuan>}.
	 */
	static void mistreat(@Nullable MinecraftServer server, DiscipleEntity e, Perlakuan kind, int severity, String source,
			@Nullable UUID other) {
		DiscipleData data = e.data();
		double loss = Math.max(0, severity) * factor(data, kind);
		Hati.setContentment(data, Hati.contentmentExact(data) - loss);
		int weight = -Math.max(1, (severity + 1) / 2);
		if (Hati.has(data, Sifat.PENDENDAM)) {
			weight -= 1;
		}
		Ingatan.remember(server, data, SekteIds.P + "." + kind.id(), other, weight, "kultivator.sekte.kepribadian.ingat." + kind.id(),
				source == null ? "" : source);
	}

	/** Care for the daily step (taught, a pill, shared meditation, honours). */
	static void care(DiscipleData data, long today, double amount) {
		Hati.addCare(data, today, amount);
	}

	/** A direct change of contentment (talk, gift, apology, a rite); a rise also counts as care for the neglect timer. */
	static void change(DiscipleData data, long today, double amount) {
		Hati.setContentment(data, Hati.contentmentExact(data) + amount);
		if (amount > 0.0D) {
			Hati.putLong(data, "last", today);
		}
	}

	private Kepuasan() {
	}
}
