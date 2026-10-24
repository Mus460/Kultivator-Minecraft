package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.alchemy.PillKind;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The plan of one self-made breakthrough (DESIGN 39.5.2 step 3): where (an open spot under the sky), which pills to
 * swallow first, whether the gate asks a Heart Trial, and whether the disciple is "hurried".
 * <ul>
 *   <li>Pills: Foundation Pill for Qi Refining to Foundation; Golden Core Pill before the tribulations into Golden Core
 *   and Nascent Soul; Tribulation Warding Pill from Nascent Soul on; Heart Calming Pill before a Heart Trial.</li>
 *   <li>Hurried: an Ambisius or Sombong disciple whose ambition drive is above 80 skips the pills and the friends in a
 *   quarter of its attempts (never the recovery: the tribulation still needs full health), unless the policy's "careful
 *   breakthrough" forbids it; that attempt's quality is -0.1.</li>
 * </ul>
 *
 * @param tempat      the spot
 * @param pil         the pills to swallow in this order (only those that help this step)
 * @param ujianHati   the step is a great gate with a Heart Trial
 * @param tergesa     hurried
 */
public record Persiapan(BlockPos tempat, List<PillKind> pil, boolean ujianHati, boolean tergesa) {

	/** Chance of a hurried attempt for an eligible disciple. */
	public static final double PELUANG_TERGESA = 0.25D;
	/** The ambition drive above which a disciple may hurry. */
	public static final int AMBISI_TERGESA = 80;
	/** The hurried attempt's quality malus. */
	public static final double MUTU_TERGESA = -0.10D;

	public Persiapan {
		pil = pil == null ? List.of() : List.copyOf(pil);
	}

	/** Plans the attempt of {@code entity} at {@code tempat}. */
	static Persiapan rencana(DiscipleEntity entity, BlockPos tempat, RandomSource random) {
		DiscipleData data = entity.data();
		boolean tergesa = bisaTergesa(entity) && random.nextDouble() < PELUANG_TERGESA;
		boolean hati = UjianHati.gerbang(data.rank());
		List<PillKind> pills = tergesa ? List.of() : pilUntuk(data.rank(), hati);
		return new Persiapan(tempat, pills, hati, tergesa);
	}

	/** The pills that help the step out of {@code current} (39.5.2), in the order they are swallowed. */
	static List<PillKind> pilUntuk(Rank current, boolean ujianHati) {
		List<PillKind> list = new ArrayList<>(3);
		Rank next = current.next();
		DiscipleBreakthrough.Kind kind = DiscipleBreakthrough.kindFor(current);
		if (kind == DiscipleBreakthrough.Kind.GATHERING) {
			list.add(PillKind.FOUNDATION);
		}
		if (kind == DiscipleBreakthrough.Kind.TRIBULATION) {
			if (next.realm() == Realm.GOLDEN_CORE || next.realm() == Realm.NASCENT_SOUL && next.realm() != current.realm()) {
				list.add(PillKind.GOLDEN_CORE);
			}
			if (next.realm().atLeast(Realm.NASCENT_SOUL)) {
				list.add(PillKind.TRIBULATION_WARDING);
			}
		}
		if (ujianHati) {
			list.add(PillKind.HEART_CALMING);
		}
		return list;
	}

	/** The pills a coming step needs and the bag lacks are what {@code Tumbuh.butuh} asks the market for. */
	static boolean pilWajib(PillKind kind) {
		return kind == PillKind.FOUNDATION || kind == PillKind.GOLDEN_CORE;
	}

	/** Ambisius or Sombong with ambition above 80, and the policy allows hurrying. */
	static boolean bisaTergesa(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (MandiriApi.kebijakan(entity).hatiHatiTerobosan()) {
			return false;
		}
		boolean trait = SekteApi.hati().has(data, Sifat.AMBISIUS) || SekteApi.hati().has(data, Sifat.SOMBONG);
		return trait && DataTumbuh.dorong(data, Dorong.AMBISI) > AMBISI_TERGESA;
	}
}
