package dev.kultivator.formasi.integrate;

import dev.kultivator.formasi.Formasi;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.InfoPart;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreStore;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.FormationIndex;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.kehancuran.ProtectedZones;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Mode Kehancuran never carves near a formation (DESIGN 24.6.4): any position within {@code pattern.radius() + 8}
 * horizontally and |dy| ≤ 8 of any registered 7c eye, of any owner. O(1) through the chunk index plus a few box tests.
 */
public final class ZoneBridge {

	public static final int VERTICAL = 8;

	public static void init() {
		ProtectedZones.addZoneProvider(ZoneBridge::protects);
		// audit M4 S6: a powered sect protective (or grand combined) formation shelters a Cave Abode like the old lodestone ring
		ProtectiveFormations.addProtectionProvider(ZoneBridge::shelters);
		// audit M4 T6: the meditation absorption limit travels with the formasi info (formasi.med)
		Formasi.addInfoPart(InfoPart::meditationLimit);
	}

	/** True inside the field of a running sect formation of the Pelindung or Gabungan family (audit M4 S6). */
	static boolean shelters(ServerLevel level, Vec3 pos) {
		CoreStore store = CoreTicker.store(level);
		if (store.isEmpty()) {
			return false;
		}
		for (CoreRecord record : store.all()) {
			if (!record.state.running()) {
				continue;
			}
			FormationSpec spec = FormationRegistry.byId(record.spec);
			if (spec != null && (spec.family() == Family.PELINDUNG || spec.family() == Family.GABUNGAN)
					&& CoreTicker.covers(level, record, spec, pos)) {
				return true;
			}
		}
		return false;
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
