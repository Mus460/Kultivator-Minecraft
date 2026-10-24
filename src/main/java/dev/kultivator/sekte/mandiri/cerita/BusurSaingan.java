package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Tokoh;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * The rival arc of DESIGN 39.8.3: a young cultivator who provoked the disciple in town ({@code KOTA_PERISTIWA
 * "provokasi:kalah"} or {@code "provokasi:menang"}) becomes a named SAINGAN. A loss makes a DENDAM goal aimed at that
 * character ({@code MandiriApi.dendam}); every further meeting is one diary line; from the {@link #PENENTU}th meeting the
 * next clash is the deciding duel: won, the grudge goal is reached and the rival turns respectful (+40); lost, the
 * disciple's temptation rises by 5 and training takes the front seat (the planner reads the goal), and the rival waits
 * for a rematch.
 */
final class BusurSaingan {

	/** Meetings before the deciding duel. */
	static final int PENENTU = 3;

	static boolean kota(MinecraftServer server, UUID owner, UUID soul, long town, String jenis) {
		if (!jenis.startsWith("provokasi")) {
			return false;
		}
		boolean menang = jenis.endsWith("menang");
		long hari = Cerita.hari(server);
		String situs = "kota:" + town;
		Tokoh rival = DaftarTokoh.dikenal(server, owner, Tokoh.Peran.SAINGAN, soul);
		if (rival == null) {
			rival = DaftarTokoh.baru(server, owner, Tokoh.Peran.SAINGAN, situs, "sombong", soul, hari, menang ? -20 : -40,
					soul.getMostSignificantBits() ^ town ^ hari);
			Cerita.tulis(server, owner, soul, "tokoh", menang ? "tokoh.saingan.kenal_menang" : "tokoh.saingan.kenal_kalah",
					menang ? EntriHarian.Suasana.BANGGA : EntriHarian.Suasana.KESAL, false, rival.nama(), DaftarTokoh.argKota(town));
			if (!menang) {
				MandiriApi.dendam(server, owner, soul, rival.id());
			}
			return true;
		}
		boolean hormat = "hormat".equals(rival.watak());
		rival = rival.dijumpai(hari, situs);
		if (hormat) {
			DaftarTokoh.simpan(server, owner, rival.ubahSikap(soul, 4));
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.saingan.hormat", EntriHarian.Suasana.SENANG, false, rival.nama(),
					DaftarTokoh.argKota(town));
			return true;
		}
		if (rival.jumpa() < PENENTU) {
			DaftarTokoh.simpan(server, owner, rival.ubahSikap(soul, menang ? -5 : -8));
			Cerita.tulis(server, owner, soul, "tokoh", menang ? "tokoh.saingan.jumpa_menang" : "tokoh.saingan.jumpa_kalah",
					menang ? EntriHarian.Suasana.BANGGA : EntriHarian.Suasana.KESAL, false, rival.nama(), DaftarTokoh.argKota(town),
					"n:" + rival.jumpa());
			if (rival.jumpa() == PENENTU - 1) {
				Cerita.tulis(server, owner, soul, "tokoh", "tokoh.saingan.janji", EntriHarian.Suasana.KESAL, false, rival.nama(),
						DaftarTokoh.argKota(town));
			}
			return true;
		}
		DiscipleEntity body = Perencana.cari(owner, soul);
		if (menang) {
			Tokoh respected = new Tokoh(rival.id(), rival.nama(), rival.peran(), rival.situs(), "hormat", rival.sikap(), rival.jumpa(), hari)
					.ubahSikap(soul, 40);
			DaftarTokoh.simpan(server, owner, respected);
			if (body != null) {
				MandiriApi.selesaikanCita(body, JenisCita.DENDAM, rival.id());
			}
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.saingan.duel_menang", EntriHarian.Suasana.BANGGA, true, rival.nama(),
					DaftarTokoh.argKota(town));
		} else {
			DaftarTokoh.simpan(server, owner, new Tokoh(rival.id(), rival.nama(), rival.peran(), rival.situs(), rival.watak(), rival.sikap(), 1,
					hari));
			if (body != null) {
				MandiriApi.ubahIblis(body, 5, "saingan");
			}
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.saingan.duel_kalah", EntriHarian.Suasana.SEDIH, true, rival.nama(),
					DaftarTokoh.argKota(town));
		}
		return true;
	}

	private BusurSaingan() {
	}
}
