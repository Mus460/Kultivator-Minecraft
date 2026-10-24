package dev.kultivator.sekte.kebun;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The bridge of Murid Mandiri (DESIGN 39D, 39.4.2 rows KEBUN and TAMBANG) to module W's package-private job slots: a
 * disciple that chooses garden or mine work itself takes a free gardener slot ({@link Pertumbuhan#assign}) or a free
 * miner slot ({@link GiliranTambang#assign}) with every old rule ({@code Kebun.refuse}: in range, not defiant, not
 * resting), works with the old activity ({@code ACT_TEND} 16, {@code ACT_MINE} 15) and the old lazy yield, and leaves the
 * slot when the planner's block ends. A new file in the old package (rule 39.0.7); nothing old changes.
 * <p>
 * A self-chosen job clears W's {@code job} flag right after the assignment: W's day-end streak counts that flag as a day
 * of work and past {@code hariLibur} days reports forced labour ({@code DIPAKSA_KERJA}); a disciple that chose the work
 * itself is never forced (DESIGN 39.10.2). The slot itself (what the yield and the board read) is the old one.
 */
public final class KebunMandiri {

	/** {@code extra.sekte_kebun} flag of a job the disciple chose itself. */
	private static final String SENDIRI = "mandiri";

	/** True while module W runs (its switch). */
	public static boolean aktif() {
		return SekteContent.on(SekteIds.W) && Kebun.server() != null;
	}

	/** The first free gardener slot (1-based), 0 when every slot is taken or the hall has no beds. */
	public static int slotRawatBebas(UUID owner) {
		if (owner == null || !aktif()) {
			return 0;
		}
		int slots = Kebun.tenderSlots(Kebun.plotCount(owner));
		CompoundTag tenders = Kebun.section(owner).getCompoundOrEmpty("rawat");
		for (int slot = 1; slot <= slots; slot++) {
			if (!tenders.contains(String.valueOf(slot))) {
				return slot;
			}
		}
		return 0;
	}

	/** The first free miner slot, 0 when the mine is not built or full. */
	public static int slotTambangBebas(UUID owner) {
		if (owner == null || !aktif()) {
			return 0;
		}
		int level = TambangRoh.level(owner);
		if (level <= 0) {
			return 0;
		}
		CompoundTag miners = TambangRoh.miners(Kebun.section(owner));
		for (int slot = 1; slot <= TabelHasil.slots(level); slot++) {
			if (!miners.contains(String.valueOf(slot))) {
				return slot;
			}
		}
		return 0;
	}

	/** Where gardener slot {@code slot} stands, or null without a working hall. */
	public static @Nullable BlockPos tempatRawat(UUID owner, int slot) {
		Kebun.Aula aula = Kebun.aula(owner);
		return aula == null || slot <= 0 ? null : Kebun.tenderSpot(aula, slot);
	}

	/** Where miner slot {@code slot} stands, or null without a working hall. */
	public static @Nullable BlockPos tempatTambang(UUID owner, int slot) {
		Kebun.Aula aula = Kebun.aula(owner);
		return aula == null || slot <= 0 ? null : Kebun.minerSpot(aula, slot);
	}

	/** True when W would give this disciple a job now (in range, not defiant, not on its day off). */
	public static boolean boleh(UUID owner, DiscipleEntity disciple) {
		Kebun.Aula aula = aktif() ? Kebun.aula(owner) : null;
		return aula != null && Kebun.refuse(aula, disciple) == null;
	}

	/** The disciple takes the first free gardener slot by itself; true when it now tends the beds (activity 16). */
	public static boolean rawat(UUID owner, DiscipleEntity disciple) {
		int slot = slotRawatBebas(owner);
		Kebun.Aula aula = slot <= 0 ? null : Kebun.aula(owner);
		if (aula == null || disciple.activity() != DiscipleEntity.ACTIVITY_NONE || Pertumbuhan.assign(aula, slot, disciple) != null) {
			return false;
		}
		tandaiSendiri(disciple);
		return true;
	}

	/** The disciple takes the first free miner slot by itself; true when it now works the mine (activity 15). */
	public static boolean tambang(UUID owner, DiscipleEntity disciple) {
		int slot = slotTambangBebas(owner);
		Kebun.Aula aula = slot <= 0 ? null : Kebun.aula(owner);
		if (aula == null || disciple.activity() != DiscipleEntity.ACTIVITY_NONE || GiliranTambang.assign(aula, slot, disciple) != null) {
			return false;
		}
		tandaiSendiri(disciple);
		return true;
	}

	/**
	 * The block is over: the yield up to now is worked out by W's own lazy step first, then the disciple leaves the slot it
	 * chose (no day off: it was not forced). A job the master gave is never touched here.
	 */
	public static void lepas(UUID owner, DiscipleEntity disciple) {
		Kebun.Aula aula = Kebun.aula(owner);
		if (aula == null) {
			return;
		}
		CompoundTag kerja = Kebun.kerja(disciple.data());
		if (!kerja.getBooleanOr(SENDIRI, false)) {
			return;
		}
		kerja.remove(SENDIRI);
		// W's own lazy step up to this tick, so the hours the disciple worked are counted before it leaves.
		Kebun.compute(owner);
		UUID soul = disciple.soulId();
		int t = Kebun.slotOf(aula.sec().getCompoundOrEmpty("rawat"), soul);
		if (t > 0) {
			Pertumbuhan.release(aula, t, false);
		}
		int m = Kebun.slotOf(TambangRoh.miners(aula.sec()), soul);
		if (m > 0) {
			GiliranTambang.release(aula, m, false, false);
		}
		Kebun.kerja(disciple.data()).putString("job", "");
	}

	/** True when the disciple holds a W job it chose by itself. */
	public static boolean sendiri(DiscipleEntity disciple) {
		return Kebun.kerja(disciple.data()).getBooleanOr(SENDIRI, false);
	}

	/** Gardener or miner of a slot (either way the master's or the disciple's own). */
	public static boolean bekerja(UUID owner, UUID soul) {
		return aktif() && Kebun.SERVICE.working(owner, soul);
	}

	private static void tandaiSendiri(DiscipleEntity disciple) {
		CompoundTag kerja = Kebun.kerja(disciple.data());
		kerja.putString("job", "");
		kerja.putBoolean(SENDIRI, true);
	}

	private KebunMandiri() {
	}
}
