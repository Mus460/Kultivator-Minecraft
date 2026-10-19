package dev.kultivator;

import dev.kultivator.command.CultivateCommand;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Enlightenment;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.PillToxicity;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.QiFormations;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.cultivation.SpiritVeins;
import dev.kultivator.cultivation.Stats;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.cultivation.VanillaPills;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.ModAttachments;
import dev.kultivator.network.Net;
import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.registry.ModItems;
import dev.kultivator.sect.Sects;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.technique.Techniques;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Kultivator: the player cultivates like in a xianxia novel. This class wires the modules together; the rules are in
 * docs/DESIGN.md. Every module has a static {@code init()} (its own events) and may have a per-player {@code tick}.
 */
public class Kultivator implements ModInitializer {
	public static final String MOD_ID = "kultivator";
	public static final Logger LOGGER = LoggerFactory.getLogger("Kultivator");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		KultivatorConfig.load();
		ModAttachments.init();
		Net.init();
		// Phase 6c spine (DESIGN 22.14.6): DeathGuard is the only player ALLOW_DEATH listener; modules register their guards.
		dev.kultivator.util.DeathGuard.init();

		// Phase 1
		Progression.init();
		Qi.init();
		Stats.init();
		XpSources.init();
		Breakthrough.init();
		Tribulation.init();
		DaoProtectors.init();
		Deviation.init();
		Meditation.init();
		SpiritSites.init();
		SpiritVeins.init();
		QiFormations.init();
		Enlightenment.init();
		VanillaPills.init();
		PillToxicity.init();
		// Phase 2
		SpiritRoots.init();
		DaoPaths.init();
		Comprehension.init();
		TechniqueBooks.init();
		Techniques.init();
		DaoTechniques.init();
		// Phase 3
		HeartDemons.init();
		SpiritBeasts.init();
		ProtectiveFormations.init();
		Sects.init();
		// Phase 4
		Alchemy.init();
		Ascension.init();
		// Phase 5
		dev.kultivator.disciple.Disciples.init();
		// Phase 6: Sekte Abadi & Murid OP (DESIGN 20.2.12)
		dev.kultivator.sect.hall.Halls.init();
		dev.kultivator.sect.event.SectEvents.init();
		dev.kultivator.disciple.power.DisciplePowers.init();
		dev.kultivator.disciple.path.DisciplePaths.init();
		dev.kultivator.kehancuran.Kehancuran.init();
		// Phase 6b: Pembangunan Sekte (DESIGN 21.24); the engine registers first, then the flow and its hall providers.
		dev.kultivator.sect.build.engine.BuildEngine.init();
		dev.kultivator.sect.build.SectBuild.init();
		// Phase 7: Dunia yang Hidup (DESIGN 19.2.3). None of them reads TechniqueRegistry eagerly; ImmortalPerks also
		// initialises Reforge and CaveAbode.
		dev.kultivator.harta.Harta.init();
		dev.kultivator.elite.EliteMonsters.init();
		dev.kultivator.talisman.Talismans.init();
		dev.kultivator.cultivation.ImmortalPerks.init();
		dev.kultivator.goals.Goals.init();
		// Phase 7b: Momen Epik (DESIGN 23.5.1 item 5); listeners only, no items.
		dev.kultivator.epic.EpicHooks.init();
		// Phase 7c: Formasi Sekte (DESIGN 24.13 item 4). Registers the eye block, builds and freezes the formation registry
		// (it calls FormasiB.init itself) on both physical sides.
		dev.kultivator.formasi.Formasi.init();
		// Phase 8: Jalan Dunia Kultivasi (DESIGN 25.2 item 18). Each registers its own items, entity, menus and providers.
		dev.kultivator.alam.Alam.init();
		dev.kultivator.musuh.Musuh.init();
		dev.kultivator.tempa.Tempa.init();
		dev.kultivator.desa.Desa.init();
		// After every module registered its items.
		ModItems.init();
		// Phase 6c (DESIGN 22.2.3, 22.18.1): the technique registry is built here only, after every module registered its
		// items (FX layers may name them). It runs on both physical sides, so the client knows every spec without a sync;
		// the client initializer never builds it again. Module inits must read the registry lazily.
		dev.kultivator.data.TechniqueRegistry.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				CultivateCommand.register(dispatcher, buildContext));

		ServerTickEvents.END_SERVER_TICK.register(Kultivator::tickPlayers);
		ServerPlayerEvents.JOIN.register(Kultivator::onJoin);
		ServerPlayerEvents.LEAVE.register(Kultivator::onLeave);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onRespawn(newPlayer, alive));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			Cultivators.dirty(player);
			Cultivators.refreshVisual(player);
		});

		LOGGER.info("Kultivator ready. The Dao is long; walk it step by step.");
	}

	private static void tickPlayers(MinecraftServer server) {
		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		for (ServerPlayer player : players) {
			if (player.isRemoved()) {
				continue;
			}
			CultivationData data = Cultivators.get(player);
			Qi.tick(player, data);
			Deviation.tick(player, data);
			PillToxicity.tick(player, data);
			Meditation.tick(player, data);
			Breakthrough.tick(player, data);
			Tribulation.tick(player, data);
			SpiritRoots.tick(player, data);
			Techniques.tick(player, data);
			DaoTechniques.tick(player, data);
			HeartDemons.tick(player, data);
			SpiritBeasts.tick(player, data);
			Ascension.tick(player, data);
			// Phase 7 (DESIGN 19.2.3): each one throttles itself.
			dev.kultivator.cultivation.ImmortalPerks.tick(player, data);
			dev.kultivator.talisman.Talismans.tick(player, data);
			dev.kultivator.goals.Goals.tick(player, data);
			if (player.tickCount % 40 == 0) {
				// Picks up a new sword in the inventory, cosmetic changes and so on.
				Cultivators.refreshVisual(player);
			}
		}
		Cultivators.flush(server);
	}

	private static void onJoin(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Techniques.onJoin(player, data);
		data.resetSession();
		writeSessionConfig(data);
		Stats.apply(player);
		Cultivators.syncNow(player);
	}

	private static void onLeave(ServerPlayer player) {
		Meditation.onLeave(player);
		Breakthrough.onLeave(player);
		Techniques.onLeave(player);
		Cultivators.forget(player);
	}

	private static void onRespawn(ServerPlayer player, boolean alive) {
		CultivationData data = Cultivators.get(player);
		data.resetSession();
		writeSessionConfig(data);
		Stats.apply(player);
		if (!alive) {
			player.setHealth(player.getMaxHealth());
			Breakthrough.onRespawn(player, data);
		}
		Cultivators.syncNow(player);
	}

	/**
	 * {@code sessionExtra.cfg} (DESIGN 19.2.3, 19.9): the server switches the client screens and HUD must know, written
	 * after every session reset (join and respawn): hd heart demon trials, dp Dao Protector event, guide guide rewards,
	 * daily daily chores, tele tribulation telegraph. Call it again after any other {@code resetSession()}.
	 */
	public static void writeSessionConfig(CultivationData data) {
		KultivatorConfig config = KultivatorConfig.get();
		CompoundTag cfg = new CompoundTag();
		cfg.putBoolean("hd", config.heartDemonTrials);
		cfg.putBoolean("dp", config.daoProtectorEvent);
		cfg.putBoolean("guide", config.guideRewards);
		cfg.putBoolean("daily", config.dailyTasks);
		cfg.putBoolean("tele", config.tribulationTelegraph);
		data.sessionExtra().put("cfg", cfg);
	}
}
