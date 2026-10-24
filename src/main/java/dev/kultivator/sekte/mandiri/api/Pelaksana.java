package dev.kultivator.sekte.mandiri.api;

import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The executor of one planner activity (contract C39.A5). The planner (39A) asks {@link #bisa} while scoring (cheap: no
 * world scans, the hall's point cache is in {@link Konteks}), calls {@link #mulai} once when the activity is chosen,
 * {@link #jalan} on its spread tick while the block lasts (return false to finish early), and {@link #selesai} exactly
 * once at the end, interrupted ({@code batal} true: an order of the master, a raid, a wound) or not. Executors set the
 * body activity themselves ({@code DiscipleEntity.setActivity}) and clear it in {@link #selesai}. Server side only.
 */
public interface Pelaksana {

	/** What the planner knows when it asks: the level, the hall owner, the activity, its block, the chosen spot. */
	record Konteks(ServerLevel level, UUID owner, Kegiatan kegiatan, long mulai, long sampai, @Nullable BlockPos tempat,
				   @Nullable UUID teman, Kebijakan kebijakan) {

		/** Ticks left in the block (0 when over, Long.MAX_VALUE for open-ended blocks {@code sampai < 0}). */
		public long sisa(long now) {
			return sampai < 0 ? Long.MAX_VALUE : Math.max(0L, sampai - now);
		}
	}

	/** True when this disciple can do the activity now (place exists, materials, partner free...). */
	boolean bisa(DiscipleEntity murid, Konteks konteks);

	/** The activity was chosen: walk to the spot, set the body activity, start sounds and bubbles. */
	void mulai(DiscipleEntity murid, Konteks konteks);

	/** One spread step while the block lasts; false finishes it now. */
	boolean jalan(DiscipleEntity murid, Konteks konteks);

	/** Ends the activity exactly once ({@code batal} = interrupted); clears the body activity it set. */
	void selesai(DiscipleEntity murid, Konteks konteks, boolean batal);

	/** The cultivation factor K for this disciple (39.5.1); the default is the catalogue's {@link Kegiatan#k()}. */
	default double K(DiscipleEntity murid, Konteks konteks) {
		return konteks.kegiatan().k();
	}

	/** The executor of an activity no module registered: never possible, does nothing. */
	Pelaksana KOSONG = new Pelaksana() {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return false;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return false;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			return 0.0D;
		}
	};
}
