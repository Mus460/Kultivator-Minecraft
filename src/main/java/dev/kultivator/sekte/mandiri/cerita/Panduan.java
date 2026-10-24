package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.util.Advancements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The guide advancements of module 39E (Kitab Panduan bab 21 "Murid Mandiri", steps {@code c21.harian} and
 * {@code c21.cita}; criteria {@code minecraft:impossible}, granted here): {@code sekte/mandiri_harian} when the master reads
 * a diary page with at least one entry ({@code BukuHarian.halaman}), {@code sekte/mandiri_cita} when a disciple reaches one
 * of its own goals ({@code MandiriBus.CITA_SELESAI}, reached). A goal reached while the master is offline is granted at the
 * next goal or page; the step is a side goal, so nothing waits on it.
 */
final class Panduan {

	static void init() {
		MandiriBus.CITA_SELESAI.register((server, owner, soul, jenis, tercapai) -> {
			if (tercapai) {
				grant(server, owner, MandiriIds.ADV_CITA);
			}
		});
	}

	static void grant(MinecraftServer server, UUID owner, String adv) {
		ServerPlayer master = server == null || owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + adv);
		}
	}

	private Panduan() {
	}
}
