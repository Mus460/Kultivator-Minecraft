package dev.kultivator.client.fx;

import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;

/** Routes each effect packet to the class that draws it (see {@link FxType}). */
public final class FxDispatcher {

	public static void play(Payloads.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		FxType type = FxType.byOrdinal(fx.kind());
		if (type == null || minecraft.level == null || minecraft.player == null) {
			return;
		}
		switch (type) {
			case BURST, RING, BEAM, PILLAR -> CommonFx.play(type, fx);
			case STAGE_UP, AWAKENING, BREAKTHROUGH_START, BREAKTHROUGH_SUCCESS, DEVIATION -> BreakthroughFx.play(type, fx);
			case TRIBULATION_OMEN, TRIBULATION_STRIKE, TRIBULATION_END, DAO_PROTECTOR -> TribulationFx.play(type, fx);
			case ENLIGHTENMENT, QI_FORMATION -> MeditationFx.play(type, fx);
			case PILL -> PillFx.play(type, fx);
			case ROOT_CEREMONY, MARROW_CLEANSE -> RootFx.play(type, fx);
			case DAO_CHOSEN -> DaoFx.play(type, fx);
			case TECHNIQUE -> TechniqueFx.play(type, fx);
			case HEART_DEMON_APPEAR, HEART_DEMON_DEFEAT -> TrialFx.play(type, fx);
			case BEAST_EVOLVE -> BeastFx.play(type, fx);
			case PROTECTIVE_FORMATION -> FormationFx.play(type, fx);
			case SPARRING -> SectFx.play(type, fx);
			case ALCHEMY -> AlchemyFx.play(type, fx);
			case ASCENSION -> AscensionFx.play(type, fx);
		}
	}

	/** Every client tick: continuous effects driven by synced state. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		AuraFx.tick(minecraft);
		MeditationFx.tick(minecraft);
		TribulationFx.tick(minecraft);
		TechniqueFx.tick(minecraft);
		FormationFx.tick(minecraft);
		BeastFx.tick(minecraft);
		AlchemyFx.tick(minecraft);
		AscensionFx.tick(minecraft);
	}

	private FxDispatcher() {
	}
}
