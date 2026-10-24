package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.elite.EliteData;
import dev.kultivator.util.KillCredit;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Init of 38ST stories (DESIGN 38.S): data-driven dialogs and quests, recurring characters, rumours, the capital story arc.
 * Called once from {@code Tempat.init()}. Registers always (K1): the config section {@code cerita}, the scene payload,
 * the data loader, the built-in conditions / effects / goal kinds, the dialog and journal screens, {@code uji cerita}
 * and {@code /kultivasi jurnal}; {@code cerita.aktif} only gates behaviour.
 *
 * <p>One server tick hook, spread: every 40 ticks one pass over the online players (condition goals, deadlines,
 * arrivals for {@code pergi}, escorts), every 100 ticks the capital arcs, every 200 ticks per player (offset by index)
 * the place-lore look-up of {@link LoreTempat}, once per world day the rumour cleanup. No
 * per-tick world scans; nothing here changes a block.
 */
public final class Cerita {

	private static boolean initialised;
	private static long lastCleanDay = Long.MIN_VALUE;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		CeritaConfig.get();
		AdeganPayload.register();
		CeritaReload.register();
		SyaratDasar.daftar();
		AkibatDasar.daftar();
		QuestTracker.daftarDasar();
		KabarTempat.init();
		TempatDialog.init();
		JurnalCerita.init();
		CeritaCommands.init();
		LoreTempat.init();
		ServerLivingEntityEvents.AFTER_DEATH.register(Cerita::afterDeath);
		DuelEndListeners.add((session, winner, loser) -> {
			if (winner instanceof ServerPlayer player && loser instanceof KultivatorNpc npc && CeritaConfig.jalan()) {
				String tokoh = Tokoh.dari(npc);
				if (!tokoh.isEmpty()) {
					QuestTracker.peristiwa(player, "duel", "tokoh:" + tokoh);
				}
				QuestTracker.peristiwa(player, "duel", "peran:" + npc.role().id());
			}
		});
		// goal "racik <item id|*>": a successful refining at any furnace by an online owner (38ST is the only module that
		// fires "racik" from the furnace, so a pill is never counted twice)
		AlchemyFurnace.addFinishListener((level, pos, owner, result, quality, success, alchemyLevel) -> {
			if (!success || owner == null || result.isEmpty() || !CeritaConfig.jalan()) {
				return;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				QuestTracker.peristiwa(player, "racik", BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
			}
		});
		DuniaEvents.SITE_DISCOVERED.register((player, site) -> {
			if (CeritaConfig.jalan()) {
				QuestTracker.peristiwa(player, "pergi", site.kind());
				KabarTempat.ditemukan(player, site);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Cerita::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Tokoh.clear();
			AkibatDasar.KAWAL.clear();
			lastCleanDay = Long.MIN_VALUE;
		});
	}

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!CeritaConfig.jalan() || !(victim.level() instanceof ServerLevel)) {
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null) {
			return;
		}
		ServerPlayer player = credit.owner();
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
		QuestTracker.peristiwa(player, "kalahkan", type.toString());
		// broad kinds for quests that do not name an entity: "kalahkan elit" (an elite beast), "kalahkan monster"
		if (EliteData.of(victim) != null) {
			QuestTracker.peristiwa(player, "kalahkan", "elit");
		}
		if (victim instanceof Enemy) {
			QuestTracker.peristiwa(player, "kalahkan", "monster");
		}
		if (victim instanceof KultivatorNpc npc) {
			QuestTracker.peristiwa(player, "kalahkan", "peran:" + npc.role().id());
			String tokoh = Tokoh.dari(npc);
			if (!tokoh.isEmpty()) {
				QuestTracker.peristiwa(player, "kalahkan", "tokoh:" + tokoh);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (!CeritaConfig.jalan()) {
			return;
		}
		long tick = server.getTickCount();
		if (tick % 100L == 50L) {
			AlurTakhta.tick(server);
		}
		if (tick % 40L == 20L) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.isRemoved() && player.isAlive()) {
					pergi(player);
					QuestTracker.periksa(player);
				}
			}
			kawal(server);
		}
		// place lore: each online player is looked up once per 200 ticks, spread over the ticks by their index
		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		for (int i = 0; i < online.size(); i++) {
			ServerPlayer player = online.get(i);
			if ((tick + i * 7L) % LoreTempat.SELA_TICK == 0L && !player.isRemoved() && player.isAlive()) {
				LoreTempat.periksa(player);
			}
		}
		if (tick % 1200L == 600L) {
			long day = Kota.day(server);
			if (day != lastCleanDay) {
				lastCleanDay = day;
				KabarTempat.bersihkan(server);
			}
		}
	}

	/** Arrival goals: a player inside a 38 place, or within 48 blocks of a site, fires {@code pergi <kind>} for running goals. */
	private static void pergi(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		List<String> wanted = new ArrayList<>();
		CeritaData data = CeritaData.get(player.level().getServer());
		for (String questId : data.questIds(player.getUUID())) {
			Quest quest = CeritaReload.quest(questId);
			if (quest == null || !QuestTracker.aktif(player, questId)) {
				continue;
			}
			Quest.Tahap stage = quest.tahap(QuestTracker.simpul(player, questId));
			if (stage == null) {
				continue;
			}
			for (Quest.Tujuan goal : stage.tujuan()) {
				if ("pergi".equals(goal.jenis()) && !goal.args().isEmpty() && !wanted.contains(goal.args().get(0))) {
					wanted.add(goal.args().get(0));
				}
			}
		}
		if (wanted.isEmpty()) {
			return;
		}
		TempatInfo here = TempatApi.tempatDi(level, player.blockPosition());
		for (String kind : wanted) {
			if (here != null && here.jenis().startsWith(kind)) {
				QuestTracker.peristiwa(player, "pergi", here.jenis());
				continue;
			}
			List<DuniaSites.Site> near = DuniaSites.near(level, player.blockPosition(), 48, kind);
			if (!near.isEmpty()) {
				QuestTracker.peristiwa(player, "pergi", near.get(0).kind());
			}
		}
	}

	/** Escorts ({@code kawal_mulai}): when the escorted body stands within 24 blocks of a site, goal {@code kawal} fires. */
	private static void kawal(MinecraftServer server) {
		Iterator<Map.Entry<UUID, AkibatDasar.Kawal>> it = AkibatDasar.KAWAL.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, AkibatDasar.Kawal> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			AkibatDasar.Kawal k = entry.getValue();
			if (player == null || !(player.level() instanceof ServerLevel level) || level.getGameTime() > k.sampai()) {
				it.remove();
				continue;
			}
			Entity body = level.getEntity(k.npc());
			if (!(body instanceof KultivatorNpc npc) || !npc.isAlive()) {
				it.remove();
				continue;
			}
			TempatInfo here = TempatApi.tempatDi(level, npc.blockPosition());
			String kind = here != null ? here.jenis() : null;
			if (kind == null) {
				List<DuniaSites.Site> near = DuniaSites.near(level, npc.blockPosition(), 24, "");
				kind = near.isEmpty() ? null : near.get(0).kind();
			}
			if (kind != null) {
				QuestTracker.peristiwa(player, "kawal", k.ref() + "@" + kind);
			}
		}
	}

	private Cerita() {
	}
}
