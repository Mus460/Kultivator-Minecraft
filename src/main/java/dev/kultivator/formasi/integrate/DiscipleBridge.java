package dev.kultivator.formasi.integrate;

import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.keeper.KeeperOrder;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Stacking;
import net.minecraft.server.level.ServerLevel;

/**
 * The disciple side of 7c (DESIGN 24.5, 24.6.1): their own meditation XP × G (the master share is never multiplied again),
 * healing through the regen factor, tribulation strikes through the strike factor, the Waspada keeper's watch radius,
 * Perkuat keepers' own XP ×0.5, and a keeper post freed on death.
 */
public final class DiscipleBridge {

	public static void init() {
		DiscipleHooks.addXpFactor((entity, data, source) -> {
			if (source != XpSource.MEDITATION || entity == null) {
				return 1.0D;
			}
			double factor = Math.min(Stacking.DISCIPLE_GATHER_CAP, field(entity).sectGathering());
			if (Keepers.orderOf(entity) == KeeperOrder.PERKUAT) {
				factor *= Keepers.PERKUAT_OWN_XP;
			}
			return factor;
		});
		DiscipleHooks.addRegenFactor(entity -> {
			int amp = field(entity).healAmp();
			return amp >= 1 ? Stacking.DISCIPLE_REGEN_CAP : amp == 0 ? 1.5D : 1.0D;
		});
		DiscipleHooks.addStrikeFactor((entity, target, index) -> {
			double factor = field(entity).tribFactor();
			return factor >= 1.0D ? 1.0D : Math.max(Stacking.TRIB_FORMATION_FLOOR, factor);
		});
		DiscipleHooks.addKeeperWatchRadius(entity -> {
			if (Keepers.orderOf(entity) != KeeperOrder.WASPADA) {
				return 0.0D;
			}
			CoreRecord record = Keepers.coreOf(entity);
			FormationSpec spec = record == null ? null : FormationRegistry.byId(record.spec);
			return spec == null ? 0.0D : record.radius;
		});
		DiscipleHooks.addOnDeath((entity, source) -> {
			if (entity.activity() == DiscipleEntity.ACTIVITY_FORMATION_KEEPER) {
				Keepers.release(entity);
			}
			return false;
		});
	}

	static FormationField field(DiscipleEntity entity) {
		return entity.level() instanceof ServerLevel level ? Stacking.fieldAt(level, entity.position(), entity) : FormationField.NONE;
	}

	private DiscipleBridge() {
	}
}
