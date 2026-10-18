package dev.kultivator.formasi.integrate;

import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreStore;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.FormationIndex;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.kehancuran.ProtectedZones;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Mode Kehancuran never carves near a formation (DESIGN 24.6.4): any position within {@code pattern.radius() + 8}
 * horizontally and |dy| ≤ 8 of any registered 7c eye, of any owner. O(1) through the chunk index plus a few box tests.
 */
public final class ZoneBridge {

	public static final int VERTICAL = 8;

	public static void init() {
		ProtectedZones.addZoneProvider(ZoneBridge::protects);
	}

	static boolean protects(ServerLevel level, BlockPos pos) {
		CoreStore store = CoreTicker.store(level);
		if (store.isEmpty()) {
			return false;
		}
		int tests = 0;
		for (int id : store.index().coresNear(pos)) {
			CoreRecord record = store.byId(id);
			if (record == null || record.virtual) {
				continue;
			}
			FormationSpec spec = FormationRegistry.byId(record.spec);
			int reach = (spec == null ? 0 : spec.pattern().radius()) + FormationIndex.MARGIN;
			if (Math.abs(pos.getX() - record.eye.getX()) <= reach && Math.abs(pos.getZ() - record.eye.getZ()) <= reach
					&& Math.abs(pos.getY() - record.eye.getY()) <= VERTICAL) {
				return true;
			}
			if (++tests >= 4) {
				break;
			}
		}
		return false;
	}

	private ZoneBridge() {
	}
}
