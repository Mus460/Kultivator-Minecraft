package dev.kultivator.sekte.kebun;

import dev.kultivator.sekte.kebun.TabelHasil.Benih;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

/**
 * One spirit-herb bed of the hall's {@code sekte_kebun} section (DESIGN 36.2.10), a view over its live compound in the
 * list {@code petak} (index = bed number - 1):
 * <ul>
 * <li>{@code herb}: the seed id ({@link Benih#id()}), empty for a bare bed;</li>
 * <li>{@code age}: the planted herb's age in years (the harvest's age is worked out from it);</li>
 * <li>{@code prog}: growth 0-1 (1 ripe); four stages: sprout, young, bud, bloom;</li>
 * <li>{@code auto}: replant the same herb from the harvest (default on);</li>
 * <li>{@code tend}: game ticks of care received (the gardener's share of the yield).</li>
 * </ul>
 * Data only: no crop block exists, the client draws the herb at the bed's anchor.
 */
public final class PetakHerba {

	/** The four visible stages (0 bare). */
	public static final int STAGES = 4;
	public static final String LIST = "petak";

	private final CompoundTag tag;
	private final int index;

	private PetakHerba(CompoundTag tag, int index) {
		this.tag = tag;
		this.index = index;
	}

	/** Bed {@code index} (0-based) of the section, created when missing. */
	public static PetakHerba of(CompoundTag section, int index) {
		ListTag list = section.getList(LIST).orElse(null);
		if (list == null) {
			list = new ListTag();
			section.put(LIST, list);
		}
		while (list.size() <= index) {
			CompoundTag fresh = new CompoundTag();
			fresh.putBoolean("auto", true);
			list.add(fresh);
		}
		return new PetakHerba(list.getCompoundOrEmpty(index), index);
	}

	/** How many beds the section holds data for. */
	public static int stored(CompoundTag section) {
		return section.getListOrEmpty(LIST).size();
	}

	public int index() {
		return index;
	}

	public @Nullable Benih herb() {
		return Benih.byId(tag.getStringOr("herb", ""));
	}

	public boolean empty() {
		return herb() == null;
	}

	public int age() {
		return tag.getIntOr("age", 0);
	}

	public double progress() {
		return Math.max(0.0D, Math.min(1.0D, tag.getDoubleOr("prog", 0.0D)));
	}

	public boolean ripe() {
		return !empty() && progress() >= 1.0D;
	}

	/** 0 bare, 1 sprout, 2 young, 3 bud, 4 bloom (ripe). */
	public int stage() {
		if (empty()) {
			return 0;
		}
		if (ripe()) {
			return STAGES;
		}
		return 1 + Math.min(STAGES - 2, (int) Math.floor(progress() * (STAGES - 1)));
	}

	public boolean auto() {
		return tag.getBooleanOr("auto", true);
	}

	public void setAuto(boolean auto) {
		tag.putBoolean("auto", auto);
	}

	public long tended() {
		return tag.getLongOr("tend", 0L);
	}

	/** Game ticks this bed still needs at full care (0 when ripe or bare). */
	public long ticksLeft() {
		Benih herb = herb();
		if (herb == null || ripe()) {
			return 0L;
		}
		return Math.round((1.0D - progress()) * growTicks(herb));
	}

	/** Full growth at full care: {@code 1 + mutu} world days. */
	public static long growTicks(Benih herb) {
		return 24000L * (1 + herb.mutu());
	}

	/** Grows by {@code ticks} of time at {@code rate} (1 tended, 0.5 untended); true when it just ripened. */
	public boolean grow(long ticks, double rate, boolean tended) {
		Benih herb = herb();
		if (herb == null || ripe() || ticks <= 0L) {
			return false;
		}
		double next = Math.min(1.0D, progress() + ticks * rate / growTicks(herb));
		tag.putDouble("prog", next);
		if (tended) {
			tag.putLong("tend", tended() + ticks);
		}
		return next >= 1.0D;
	}

	/** Sows {@code herb} aged {@code age} years. */
	public void plant(Benih herb, int age) {
		tag.putString("herb", herb.id());
		tag.putInt("age", Math.max(1, age));
		tag.putDouble("prog", 0.0D);
		tag.putLong("tend", 0L);
	}

	/** Empties the bed (keeps the replant choice). */
	public void clear() {
		tag.remove("herb");
		tag.remove("age");
		tag.remove("prog");
		tag.remove("tend");
	}

	/** Test helper: sets growth directly (0-1). */
	public void setProgress(double progress) {
		tag.putDouble("prog", Math.max(0.0D, Math.min(1.0D, progress)));
	}
}
