package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.kota.KultivatorNpc;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Init of 38DS mortal villages and clan villages (DESIGN 38.C.2). Source of the villages
 * ({@code kultivator.kampung.desa.inspired}): Han Li's mountain village at the start of A Record of a Mortal's Journey
 * and the little shrine of the Earth God (Tudi Gong) found in nearly every Chinese village; of the clans
 * ({@code kultivator.kampung.klan.inspired}): the Xiao family of Wu Tan City (Battle Through the Heavens).
 * <p>
 * The villages themselves come only from world generation in new chunks (pack {@code kultivator:dunia_kampung}, sets
 * {@code kampung/dusun|desa|desa_besar|klan}, salt 380101-380104; seven styles by biome, four for clans); their bells
 * and vanilla villagers make them vassal villages of the Desa system (25.6) like any vanilla village. This module adds
 * their life on top ({@link KampungAnchors}): the site and safe zone, the Harvest Festival ({@link FestivalPanen}),
 * talented children of pledged villages ({@link AnakBerbakat}), the village market ({@link PasarDesa}), the clan's young
 * master, patriarch, pledge gate and tribute bonus ({@link Klan}), and the commands ({@link KampungCommands}).
 * Registry entries exist always; {@code kampung.worldgen} only decides the pack, {@code kampung.aktif} the behaviour.
 */
public final class Kampung {

	private static boolean initialised;
	/** Site -> bodies this module made there (young master, patriarch), capped by {@code kampung.tubuhMaks}. */
	private static final Map<Long, List<KultivatorNpc>> TUBUH = new HashMap<>();

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KampungConfig config = KampungConfig.get();
		DuniaPacks.worldgen("kampung", config.worldgen);
		DuniaAnchors.register("kampung", new KampungAnchors());
		Klan.init();
		KampungCommands.init();
		ServerLifecycleEvents.SERVER_STARTING.register(KampungApi::server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KampungApi.server(null);
			FestivalPanen.reset();
			PasarDesa.reset();
			Klan.reset();
			TUBUH.clear();
		});
	}

	/** Living bodies this module made at village {@code site}. */
	static int tubuhKu(long site) {
		List<KultivatorNpc> list = TUBUH.get(site);
		if (list == null) {
			return 0;
		}
		list.removeIf(npc -> !npc.isAlive() || npc.isRemoved());
		return list.size();
	}

	static void catatTubuh(long site, KultivatorNpc npc) {
		TUBUH.computeIfAbsent(site, s -> new ArrayList<>()).add(npc);
	}

	private Kampung() {
	}
}
