package dev.kultivator.formasi.integrate;

import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.talisman.Inscribe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Refining support (DESIGN 24.4.4, 24.5): K3 and B7 through the furnace providers (strongest per cauldron position:
 * chance ≤ +0.10, ≤ 1 quality step, time ≥ ×0.7) and the inscription provider (≤ +0.065). The 0.95 caps stay. A query
 * keeps the refining cores out of standby.
 */
public final class AlchemyBridge {

	public static void init() {
		AlchemyFurnace.addChanceProvider((level, pos, owner) -> Stacking.fieldAtBlock(level, pos, owner).refineChance());
		AlchemyFurnace.addQualityStepProvider((level, pos, owner) -> Stacking.fieldAtBlock(level, pos, owner).refineSteps());
		AlchemyFurnace.addDurationFactorProvider((level, pos, owner) -> Stacking.fieldAtBlock(level, pos, owner).refineTime());
		Inscribe.addChanceProvider(AlchemyBridge::inscribe);
	}

	private static double inscribe(ServerPlayer player) {
		FormationField field = MeditationBridge.field(player);
		if (field.inscribe() > 0.0D && player.level() instanceof ServerLevel level) {
			long now = level.getGameTime();
			for (CoreRecord record : CoreTicker.store(level).all()) {
				FormationSpec spec = FormationRegistry.byId(record.spec);
				if (spec != null && spec.family() == Family.PENEMPAAN && record.state.running()
						&& Ownership.isMember(level.getServer(), record.owner, player) && CoreTicker.covers(level, record, spec, player.position())) {
					record.lastUse = now;
				}
			}
		}
		return field.inscribe();
	}

	private AlchemyBridge() {
	}
}
