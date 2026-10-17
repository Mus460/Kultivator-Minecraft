package dev.kultivator.disciple.path;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ikatan, the bond between master and disciple (DESIGN 20.3.18), 0-100 in {@code extra.path.bond}. Sumber: <i>Tales of
 * Demons and Gods</i>: a new disciple forms a Soul Seal with the master, and ch. 343 is titled "Taking in a disciple".
 * <p>
 * Teaching +5, feeding a pill +2 (at most 10 a day), guarding its breakthrough or calamity +10, meditating within 8
 * blocks of the master +1 per minute (at most 20 a day), each won tournament bout +3 (module B), each Balas Budi rescue
 * +5. The bond of a released disciple goes with it. Bond 50 allows the Soul Seal, bond 80 the heir.
 */
public final class Bond {

	public static final int MAX = 100;
	public static final int TEACH = 5;
	public static final int PILL = 2;
	public static final int PILL_DAY_MAX = 10;
	public static final int GUARD = 10;
	public static final int MEDITATION_PER_MINUTE = 1;
	public static final int MEDITATION_DAY_MAX = 20;
	public static final int BOUT = 3;
	public static final int RESCUE = 5;
	public static final int MASTER_DUEL = 3;
	public static final double MEDITATION_RANGE = 8.0D;
	/** Bond needed for the Soul Seal and for the heir. */
	public static final int SEAL_BOND = 50;
	public static final int HEIR_BOND = 80;

	/** Element-matched teaching (TDG ch. 13: Nie Li matches each friend's technique to the attribute of their soul). */
	public static final double MATCHED_TEACH_COST = 0.5D;
	public static final int MATCHED_CASTS = 10;

	/** Last seen cooldown of each element-matched technique per disciple (to notice its casts; session only). */
	private static final Map<DiscipleEntity, Map<String, Integer>> LAST_COOLDOWN = new WeakHashMap<>();

	static void register() {
		DiscipleHooks.addOnTaught((entity, master, technique) -> {
			int before = DisciplePaths.bond(entity.data());
			add(entity.data(), TEACH);
			tellThreshold(entity, master, before);
			if (matched(entity.data(), technique)) {
				DisciplePaths.child(DisciplePaths.section(entity.data()), "em").putInt(technique.id(), MATCHED_CASTS);
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "bond.matched", DisciplePaths.name(entity),
						technique.displayName(), MATCHED_CASTS).withColor(DisciplePaths.JADE));
			}
		});
		// A technique of a Dao whose element is in the disciple's root costs the master half (10% instead of 20%).
		DiscipleHooks.addTeachCostFactor((entity, master, technique) -> matched(entity.data(), technique) ? MATCHED_TEACH_COST : 1.0D);
		DiscipleHooks.addOnFed((entity, master, stack) -> {
			if (!Disciples.isPill(stack) || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			CompoundTag day = today(entity.data(), level.getServer());
			int given = day.getIntOr("pill", 0);
			if (given < PILL_DAY_MAX) {
				int gain = Math.min(PILL, PILL_DAY_MAX - given);
				day.putInt("pill", given + gain);
				int before = DisciplePaths.bond(entity.data());
				add(entity.data(), gain);
				tellThreshold(entity, master, before);
			}
		});
	}

	/** Adds bond, clamped to 0-100. */
	static void add(DiscipleData data, int amount) {
		DisciplePaths.section(data).putInt("bond", Math.max(0, Math.min(MAX, DisciplePaths.bond(data) + amount)));
	}

	/** Today's counters ({@code bond_day}); reset when the day index changes. */
	static CompoundTag today(DiscipleData data, MinecraftServer server) {
		CompoundTag day = DisciplePaths.child(DisciplePaths.section(data), "bond_day");
		long index = DisciplePaths.day(server);
		if (day.getLongOr("day", -1L) != index) {
			day.putLong("day", index);
			day.putInt("pill", 0);
			day.putInt("med", 0);
			day.putInt("med_ticks", 0);
		}
		return day;
	}

	/** True when the technique's Dao element is in the disciple's root. */
	static boolean matched(DiscipleData data, Technique technique) {
		Dao dao = technique.dao();
		return dao != null && dao.hasAffinity(data.rootElements());
	}

	/**
	 * The first 10 casts of an element-matched technique give double comprehension: a cast is noticed when its cooldown
	 * jumps up between two seconds, and the phase-5 gain is added once more.
	 */
	private static void matchedCasts(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		CompoundTag em = DisciplePaths.tag(data).getCompoundOrEmpty("em");
		if (em.isEmpty()) {
			return;
		}
		Map<String, Integer> last = LAST_COOLDOWN.computeIfAbsent(entity, key -> new HashMap<>());
		for (String id : List.copyOf(em.keySet())) {
			Technique technique = Technique.byId(id);
			int left = em.getIntOr(id, 0);
			if (technique == null || left <= 0 || !data.knows(technique)) {
				DisciplePaths.child(DisciplePaths.section(data), "em").remove(id);
				last.remove(id);
				continue;
			}
			int cooldown = data.cooldownLeft(technique);
			Integer before = last.put(id, cooldown);
			if (before == null || cooldown <= before) {
				continue;
			}
			Dao dao = technique.dao();
			if (dao != null && dao == data.dao()) {
				data.addComprehension(DiscipleCultivation.comprehensionGain(data, dao, DiscipleCultivation.TECHNIQUE_COMPREHENSION));
			}
			CompoundTag write = DisciplePaths.child(DisciplePaths.section(data), "em");
			if (left - 1 <= 0) {
				write.remove(id);
				last.remove(id);
			} else {
				write.putInt(id, left - 1);
			}
		}
	}

	/** Once a second: meditating within 8 blocks of the master grows the bond by 1 per minute (20 a day at most). */
	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		matchedCasts(entity);
		if (!entity.isMeditatingNow() || !DisciplePaths.near(master, entity, MEDITATION_RANGE) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		CompoundTag day = today(entity.data(), level.getServer());
		int gained = day.getIntOr("med", 0);
		if (gained >= MEDITATION_DAY_MAX) {
			return;
		}
		int ticks = day.getIntOr("med_ticks", 0) + 20;
		if (ticks >= 1200) {
			ticks -= 1200;
			day.putInt("med", gained + MEDITATION_PER_MINUTE);
			int before = DisciplePaths.bond(entity.data());
			add(entity.data(), MEDITATION_PER_MINUTE);
			tellThreshold(entity, master, before);
		}
		day.putInt("med_ticks", ticks);
	}

	/** Tells the master when the bond crosses 50 (Soul Seal) or 80 (heir). */
	static void tellThreshold(DiscipleEntity entity, @Nullable ServerPlayer master, int before) {
		int after = DisciplePaths.bond(entity.data());
		if (master == null) {
			return;
		}
		if (before < SEAL_BOND && after >= SEAL_BOND) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "bond.seal_ready", DisciplePaths.name(entity)).withColor(DisciplePaths.JADE));
		} else if (before < HEIR_BOND && after >= HEIR_BOND) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "bond.heir_ready", DisciplePaths.name(entity)).withColor(DisciplePaths.GOLD));
		}
	}

	private Bond() {
	}
}
