package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.sect.build.SectBuild;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Player land the repairs never touch (DESIGN 38.R.3 rule 2): {@link ProtectedZones#claimed} (sect halls and steles, cave
 * abode seats, the patron plaque of a vassal village, any other claim provider) and the confirmed sites of the player's
 * own sect buildings ({@code SectBuild.insideAnySite}). The place zones of 34/38 are not claims. A damage entry on a
 * claim is never recorded, so a player building inside his own claim is never billed.
 */
public final class KlaimPemain {

	public static boolean diklaim(ServerLevel level, BlockPos pos) {
		try {
			return ProtectedZones.claimed(level, pos) || SectBuild.insideAnySite(level, pos, 0);
		} catch (RuntimeException exception) {
			// never repair over somebody's land by mistake
			return true;
		}
	}

	private KlaimPemain() {
	}
}
