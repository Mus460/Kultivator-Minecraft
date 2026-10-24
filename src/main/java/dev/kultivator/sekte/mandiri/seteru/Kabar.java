package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Town rumours about feuds (DESIGN 39.F.5, contract C39.K8): "Murid <hall> dan <sect> bertarung di Arena Timur". The
 * templates live in {@code data/kultivator/tempat/kabar/mandiri_seteru.json} (text keys of this module); the sect is
 * stored as {@code sekte:<id>} so every listener's client names it. A feud born in a town spreads its rumours in that
 * town first (its town id is the rumour place, the same id the town's NPCs ask for); a feud without a town spreads them
 * world-wide. Without 38's story layer nothing happens.
 */
final class Kabar {

	static void sebar(MinecraftServer server, Perseteruan f, String templat) {
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}
		var hall = Halls.hallOf(f.owner);
		Component hallName = hall == null ? Component.translatable(SeteruIds.k("aula_pemain")) : Component.literal(hall.name());
		try {
			CeritaApi.kabar(level, f.situs == 0L ? null : f.situs, templat, f.namaUtama().getString(), hallName.getString(), "sekte:" + f.sekte);
		} catch (RuntimeException exception) {
			// the story layer is off or not loaded: no rumour
		}
	}

	private Kabar() {
	}
}
