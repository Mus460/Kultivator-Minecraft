package dev.kultivator.formasi.integrate;

import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.formation.ProtectiveFormations;
import net.minecraft.server.level.ServerLevel;

/**
 * Tribulation warding (DESIGN 24.5, 24.6.1): the B6 strike factor provider. All formations together never go below
 * {@code TRIB_FORMATION_FLOOR = 0.70}, the Formasi Pelindung's own 0.85 included, so the provider returns
 * {@code max(0.70 / (insidePelindung ? 0.85 : 1), Π)}. The spine's overall {@code max(0.5, …)} still applies.
 */
public final class TribulationBridge {

	public static void init() {
		Tribulation.addStrikeFactorProvider((player, target, index, last) -> {
			double product = MeditationBridge.field(player).tribFactor();
			if (product >= 1.0D) {
				return 1.0D;
			}
			boolean pelindung = player.level() instanceof ServerLevel level && ProtectiveFormations.insideActiveFormation(level, player.position());
			return Math.max(Stacking.TRIB_FORMATION_FLOOR / (pelindung ? Stacking.PELINDUNG_TRIB : 1.0D), product);
		});
	}

	private TribulationBridge() {
	}
}
