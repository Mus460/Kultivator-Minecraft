package dev.kultivator.technique.spec.run;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Step;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cincin Segel, the 12-slot loadout (DESIGN 22.12.5), saved in {@code extra.teknik.slots} (12 ids, "" = empty). Slots
 * 1-4 are also the quick seals. The read is pure (the client wheel and HUD use it on the synced data); edits check that
 * the id is known and castable. "Isi otomatis" fills the strongest castable spec per role: single target 2, area 3,
 * control 2, movement 1, defence 1, heal 1, ultimate 2 (strongest = grade, then mastery, then coefficient).
 */
public final class Loadout {

	public static final int SLOTS = 12;
	public static final String KEY = "slots";

	/** The 12 slot ids ("" for an empty slot); never null entries. */
	public static String[] slots(CultivationData data) {
		String[] slots = new String[SLOTS];
		java.util.Arrays.fill(slots, "");
		ListTag list = data.extra().getCompoundOrEmpty(Mastery.EXTRA).getListOrEmpty(KEY);
		for (int i = 0; i < Math.min(SLOTS, list.size()); i++) {
			Tag entry = list.get(i);
			slots[i] = entry.asString().orElse("");
		}
		return slots;
	}

	/** True when every slot is empty. */
	public static boolean empty(CultivationData data) {
		for (String id : slots(data)) {
			if (!id.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Known, not passive, not disciple-only, rank and Dao allow it (gates included). Pure. */
	public static boolean castable(CultivationData data, TechniqueSpec spec) {
		return spec.type() != TechniqueType.PASIF && spec.playerVisible() && TechniqueRunner.whyNotCastable(data, spec) == null;
	}

	/** Puts {@code id} ("" or "-" empties) into slot {@code index} (0-11). False when refused. */
	public static boolean set(ServerPlayer player, int index, String id) {
		if (index < 0 || index >= SLOTS) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		String value = id == null || id.equals("-") ? "" : id.trim();
		if (!value.isEmpty()) {
			TechniqueSpec spec = TechniqueRegistry.byId(value);
			if (spec == null || !castable(data, spec)) {
				return false;
			}
		}
		String[] slots = slots(data);
		for (int i = 0; i < SLOTS; i++) {
			if (!value.isEmpty() && i != index && slots[i].equals(value)) {
				slots[i] = "";
			}
		}
		slots[index] = value;
		write(data, slots);
		Cultivators.dirty(player);
		return true;
	}

	public static void clear(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		String[] slots = new String[SLOTS];
		java.util.Arrays.fill(slots, "");
		write(data, slots);
		Cultivators.dirty(player);
	}

	/** "Isi otomatis": the strongest castable spec per role. Returns how many slots were filled. */
	public static int autofill(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		List<TechniqueSpec> castable = new ArrayList<>();
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (castable(data, spec)) {
				castable.add(spec);
			}
		}
		castable.sort(Comparator.comparingInt((TechniqueSpec spec) -> -spec.grade().tier())
				.thenComparingInt(spec -> -Mastery.level(data, spec.id()))
				.thenComparingDouble(spec -> -coefficient(spec)));
		String[] slots = new String[SLOTS];
		java.util.Arrays.fill(slots, "");
		int filled = 0;
		Object[][] roles = {{TechniqueType.SERANG, 2}, {TechniqueType.AREA, 3}, {TechniqueType.KENDALI, 2}, {TechniqueType.GERAK, 1},
				{TechniqueType.LINDUNG, 1}, {TechniqueType.PULIH, 1}, {TechniqueType.PAMUNGKAS, 2}};
		List<TechniqueSpec> used = new ArrayList<>();
		for (Object[] role : roles) {
			TechniqueType type = (TechniqueType) role[0];
			int want = (Integer) role[1];
			for (TechniqueSpec spec : castable) {
				if (want <= 0 || filled >= SLOTS) {
					break;
				}
				if (spec.type() == type && !used.contains(spec)) {
					slots[filled++] = spec.id();
					used.add(spec);
					want--;
				}
			}
		}
		for (TechniqueSpec spec : castable) {
			if (filled >= SLOTS) {
				break;
			}
			if (!used.contains(spec)) {
				slots[filled++] = spec.id();
				used.add(spec);
			}
		}
		write(data, slots);
		Cultivators.dirty(player);
		return filled;
	}

	/** The highest damage coefficient of a spec's timeline (autofill and disciple roles). */
	public static double coefficient(TechniqueSpec spec) {
		double best = 0.0D;
		for (Step step : spec.timeline()) {
			for (Payload.Hit hit : step.hit()) {
				if (hit instanceof Payload.Dmg dmg) {
					best = Math.max(best, dmg.coef() * Math.max(1, step.repeat()));
				}
			}
		}
		return best;
	}

	/** The castable specs the wheel and cycling use: the loadout, or every castable spec while it is empty. */
	public static List<TechniqueSpec> cycle(CultivationData data) {
		List<TechniqueSpec> list = new ArrayList<>();
		if (!empty(data)) {
			for (String id : slots(data)) {
				TechniqueSpec spec = TechniqueRegistry.byId(id);
				if (spec != null && castable(data, spec)) {
					list.add(spec);
				}
			}
			if (!list.isEmpty()) {
				return list;
			}
		}
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (castable(data, spec)) {
				list.add(spec);
			}
		}
		return list;
	}

	private static void write(CultivationData data, String[] slots) {
		CompoundTag teknik = Mastery.section(data.extra(), Mastery.EXTRA);
		ListTag list = new ListTag();
		for (String id : slots) {
			list.add(StringTag.valueOf(id == null ? "" : id));
		}
		teknik.put(KEY, list);
	}

	private Loadout() {
	}
}
