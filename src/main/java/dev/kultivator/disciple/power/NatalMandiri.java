package dev.kultivator.disciple.power;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * The bridge of Murid Mandiri (DESIGN 39D, 39.6.7) to the package-private {@link NatalTreasure}: a disciple refines and
 * enhances its own natal treasure with feathers and diamonds from its own bag and its own personal PK, by the old rules
 * and chances (DESIGN 20.3.17). A new file in the old package (rule 39.0.7), so nothing old opens its visibility. The
 * caller ({@code sekte.mandiri.kerja.NatalSendiri}) takes the materials out of the bag first and puts them back when the
 * attempt is refused. Server side only.
 * <p>
 * Sumber: A Will Eternal ch. 5, 29-38 (Bai Xiaochun's folds of spirit enhancement fuelled by Spirit Tail feathers), as
 * cited in {@code NatalTreasure}.
 */
public final class NatalMandiri {

	/** Diamonds and feathers the first refining takes (the old amounts). */
	public static final int REFINE_DIAMONDS = NatalTreasure.REFINE_DIAMONDS;
	public static final int REFINE_FEATHERS = NatalTreasure.REFINE_FEATHERS;
	/** Forms: 0 sword, 1 parasol, 2 wok, 3 bell ({@code PowerIds.NATAL_IDS}). */
	public static final int SWORD = PowerIds.NATAL_SWORD;
	public static final int PARASOL = PowerIds.NATAL_PARASOL;
	public static final int WOK = PowerIds.NATAL_WOK;
	public static final int BELL = PowerIds.NATAL_BELL;
	/** Fold milestones (silver, gold and the form's ability, Eternal Parasol). */
	public static final int SILVER = NatalTreasure.SILVER;
	public static final int GOLD = NatalTreasure.GOLD;
	public static final int ETERNAL = NatalTreasure.ETERNAL;

	/** The form (-1 before refining). */
	public static int form(DiscipleData data) {
		return NatalTreasure.form(data);
	}

	/** Folds of spirit enhancement. */
	public static int folds(DiscipleData data) {
		return NatalTreasure.folds(data);
	}

	public static int maxFolds(DiscipleData data) {
		return NatalTreasure.maxFolds(data);
	}

	/** The old chance of the next fold with the owner's hall level. */
	public static double chanceNext(DiscipleEntity entity) {
		return NatalTreasure.chance(entity.data(), entity.ownerId(), NatalTreasure.folds(entity.data()) + 1);
	}

	/** Personal PK the next fold costs. */
	public static int foldPk(DiscipleData data) {
		return NatalTreasure.foldPkSelf(data);
	}

	/** Bulu Ekor Roh (module B), or a vanilla feather when that item does not exist. */
	public static Item feather() {
		return NatalTreasure.feather();
	}

	/** The form id ({@code sword}, {@code parasol}, {@code wok}, {@code bell}). */
	public static String formId(int form) {
		return PowerIds.NATAL_IDS[Math.max(0, Math.min(PowerIds.NATAL_IDS.length - 1, form))];
	}

	/** The lang key of a form name ({@code kultivator.dpower.natal.form.<id>}). */
	public static String formKey(int form) {
		return DisciplePowers.PREFIX + "natal.form." + formId(form);
	}

	/** Why the disciple cannot refine by itself now (full lang key), or null. */
	public static @Nullable String refineBlocker(DiscipleEntity entity, int diamonds, int feathers) {
		String why = NatalTreasure.refineBlockerSelf(entity, diamonds, feathers);
		return why == null ? null : DisciplePowers.PREFIX + why;
	}

	/** Refines with materials already taken out of the bag; null on success, else the full lang key of the reason. */
	public static @Nullable String refine(DiscipleEntity entity, int form, int diamonds, int feathers) {
		String why = NatalTreasure.refineSelf(entity, form, diamonds, feathers);
		return why == null ? null : DisciplePowers.PREFIX + why;
	}

	/** Why the next fold cannot be tried by the disciple itself (full lang key), or null. */
	public static @Nullable String enhanceBlocker(DiscipleEntity entity, int feathers) {
		String why = NatalTreasure.enhanceBlockerSelf(entity, feathers);
		return why == null ? null : DisciplePowers.PREFIX + why;
	}

	/**
	 * One fold with feathers already taken out of the bag and personal PK: 1 a new fold, 0 failed, -1 failed and lost a
	 * fold, {@link Integer#MIN_VALUE} refused (nothing paid).
	 */
	public static int enhance(DiscipleEntity entity, int feathers) {
		return NatalTreasure.enhanceSelf(entity, feathers);
	}

	private NatalMandiri() {
	}
}
