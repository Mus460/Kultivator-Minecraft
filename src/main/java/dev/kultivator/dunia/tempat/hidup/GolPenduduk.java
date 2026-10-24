package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The goals of a DESIGN 38 resident body (38.N.4, 38.N.7), registered by {@code KultivatorNpc.registerGoals} for every
 * NPC and idle unless the NPC is a resident ({@link KultivatorNpc#penduduk}). They hold no state of their own: the
 * resident's goal, walk and clip live in its memory ({@link TubuhPenduduk}).
 * <ul>
 *   <li>{@link Diam} (priority 1): asleep in its bed, seated, kneeling at a shrine or over a game of chess the body holds
 *       still: no walking, no jumping and no vanilla head turns, and it is not pushed ({@code isPushable}).</li>
 *   <li>{@link Jalan} (priority 4): on the way to the schedule's titik. Within 24 blocks of a player (L0) vanilla
 *       pathfinding, at most {@link Pemanggil#JALUR_PER_TICK} searches started per server tick for all residents; beyond
 *       (L1) straight steps along the place's {@code jalan} nodes with the move control every 10 ticks, no search at all
 *       (38.N.1 "Gerak"). The custom part runs every 4th tick near a player, every 10th when throttled
 *       ({@link DuniaBudget#throttled}).</li>
 *   <li>{@link Fokus} (priority 5): at work (writing, hammering, weaving, cooking...) the eyes stay on the task in front
 *       of it instead of following every passer-by; a look set by the schedule ({@code DATA_LOOK_AT}: a chat partner, a
 *       greeted player) wins.</li>
 * </ul>
 */
public final class GolPenduduk {

	private GolPenduduk() {
	}

	/** Holds a sleeper, a sitter or a kneeler still at its titik. */
	public static final class Diam extends Goal {
		private final KultivatorNpc npc;

		public Diam(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return npc.penduduk() && npc.diamDiTitik() && npc.getTarget() == null;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			npc.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return false;
		}

		@Override
		public void tick() {
			TubuhPenduduk.tahanDiam(npc);
		}
	}

	/** Walks to the schedule's goal: vanilla path near a player, node steps further out. */
	public static final class Jalan extends Goal {
		private final KultivatorNpc npc;

		public Jalan(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return npc.penduduk() && !npc.diamDiTitik() && DuniaBudget.throttled(npc, 4) && TubuhPenduduk.dalamPerjalanan(npc);
		}

		@Override
		public boolean canContinueToUse() {
			return npc.penduduk() && !npc.diamDiTitik() && TubuhPenduduk.dalamPerjalanan(npc);
		}

		@Override
		public void start() {
			TubuhPenduduk.langkah(npc);
		}

		@Override
		public void stop() {
			TubuhPenduduk.hentiLangkah(npc);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			// near a player every 4th tick, otherwise every 10th (38.N.7 "Penyebaran")
			if (DuniaBudget.throttled(npc, 4)) {
				TubuhPenduduk.langkah(npc);
			}
			// L1: the move control needs its aim every tick (it walks one tick per call); no search involved
			Vec3 to = TubuhPenduduk.arah(npc);
			if (to != null && npc.getNavigation().isDone()) {
				npc.getMoveControl().setWantedPosition(to.x, to.y, to.z, TubuhPenduduk.lajuJalan(npc));
			}
		}
	}

	/** Keeps the eyes on the work at hand. */
	public static final class Fokus extends Goal {
		private final KultivatorNpc npc;

		public Fokus(KultivatorNpc npc) {
			this.npc = npc;
			this.setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return npc.penduduk() && npc.getTarget() == null && TubuhPenduduk.fokus(npc);
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			TubuhPenduduk.lihatKerja(npc);
		}
	}
}
