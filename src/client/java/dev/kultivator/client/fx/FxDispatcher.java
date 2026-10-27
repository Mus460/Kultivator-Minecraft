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
			// Phase 6 (DESIGN 20.2.3).
			case HALL -> dev.kultivator.client.hall.HallFx.play(type, fx);
			case SECT_EVENT -> dev.kultivator.client.sectevent.SectEventFx.play(type, fx);
			case DISCIPLE_POWER -> dev.kultivator.client.disciple.power.PowerFx.play(type, fx);
			case STAR -> dev.kultivator.client.disciple.path.PathFx.play(type, fx);
			case KEHANCURAN -> dev.kultivator.client.kehancuran.KehancuranFx.play(type, fx);
			// Phase 6b (DESIGN 21.24).
			case SECT_BUILD -> dev.kultivator.client.sectbuild.BuildFx.play(type, fx);
			// Phase 7 (DESIGN 19.2.11).
			case ELITE -> EliteFx.play(type, fx);
			case TREASURE -> HartaFx.play(type, fx);
			case TALISMAN -> TalismanFx.play(type, fx);
			// Phase 7c (DESIGN 24.13 item 3).
			case FORMASI -> dev.kultivator.client.formasi.FormasiFx.play(fx);
			// Phase 8 (DESIGN 25.2 item 4).
			case ALAM -> dev.kultivator.client.alam.AlamFx.play(fx);
			case MUSUH -> dev.kultivator.client.musuh.MusuhFx.play(fx);
			case TEMPA -> dev.kultivator.client.tempa.TempaFx.play(fx);
			case DESA -> dev.kultivator.client.desa.DesaFx.play(fx);
			// Phase 9 (DESIGN 26.14 item 3).
			case ABADI -> dev.kultivator.client.abadi.AbadiFx.play(fx);
			case PURBA -> dev.kultivator.client.purba.PurbaFx.play(fx);
			case BATIN -> dev.kultivator.client.batin.BatinFx.play(fx);
		}
	}

	/** Disconnect: the per-world modules let go of the old ClientLevel (their own level check only runs once a world is loaded). */
	public static void forgetLevel() {
		EliteFx.forget();
		MeditationFx.forget();
		DiscipleFx.forget();
		BreakthroughFx.forget();
		TribulationFx.forget();
		TechniqueFx.forget();
		DaoTechniqueFx.forget();
		FormationFx.forget();
		AlchemyFx.forget();
		AscensionFx.forget();
		TrialFx.forget();
	}

	/** Every client tick: continuous effects driven by synced state. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		AuraFx.tick(minecraft);
		MeditationFx.tick(minecraft);
		// Both ignore a second call in the same game tick (TribulationFx and PillFx's own hook also call them).
		BreakthroughFx.tick(minecraft);
		PillFx.tick(minecraft);
		TribulationFx.tick(minecraft);
		TechniqueFx.tick(minecraft);
		DaoTechniqueFx.tick(minecraft);
		FormationFx.tick(minecraft);
		BeastFx.tick(minecraft);
		AlchemyFx.tick(minecraft);
		AscensionFx.tick(minecraft);
		// Phase 6: continuous disciple power and star visuals.
		dev.kultivator.client.disciple.power.PowerFx.tick(minecraft);
		dev.kultivator.client.disciple.path.PathFx.tick(minecraft);
		// Phase 6b: construction sparks, work mist and the cloud sea.
		dev.kultivator.client.sectbuild.BuildFx.tick(minecraft);
		// Phase 7: elite auras and telegraphs, talisman glyphs.
		EliteFx.tick(minecraft);
		TalismanFx.tick(minecraft);
		// Phase 7c: formation domes, rings, marks and holograms.
		dev.kultivator.client.formasi.FormasiFx.tick();
		dev.kultivator.client.alam.AlamFx.tick();
		dev.kultivator.client.musuh.MusuhFx.tick();
		dev.kultivator.client.tempa.TempaFx.tick();
		dev.kultivator.client.desa.DesaFx.tick();
		dev.kultivator.client.abadi.AbadiFx.tick();
		dev.kultivator.client.purba.PurbaFx.tick();
		dev.kultivator.client.batin.BatinFx.tick();
	}

	private FxDispatcher() {
	}
}
