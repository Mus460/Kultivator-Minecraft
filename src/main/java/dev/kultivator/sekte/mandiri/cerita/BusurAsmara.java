package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Tokoh;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;

/**
 * The sweetheart arc of DESIGN 39.8.3 (KENALAN_HATI): met in town ({@code KOTA_PERISTIWA "asmara"} or
 * {@code "asmara:<look hex><p|l>"}, the look of the person 39C's story named, so the diary and the travel scroll name the
 * same face). Two more meetings, then "a promise at the festival"; the next meeting decides: half of the time the
 * sweetheart is young and gifted enough to come to the hall as a candidate ({@code Halls.addCandidates}, a lasting tie
 * may follow through R), otherwise a bittersweet farewell the disciple remembers (memory weight 6, written while its
 * body is loaded). Romance between two disciples stays module R's (39 only adds occasions).
 */
final class BusurAsmara {

	static final int JANJI = 3;

	static boolean kota(MinecraftServer server, UUID owner, UUID soul, long town, String jenis) {
		if (!jenis.startsWith("asmara")) {
			return false;
		}
		long hari = Cerita.hari(server);
		String situs = "kota:" + town;
		Tokoh hati = DaftarTokoh.dikenal(server, owner, Tokoh.Peran.KENALAN_HATI, soul);
		if (hati == null) {
			hati = DaftarTokoh.baru(server, owner, Tokoh.Peran.KENALAN_HATI, situs, "lembut", soul, hari, 40, town ^ soul.getMostSignificantBits());
			String look = jenis.length() > 7 ? jenis.substring(7) : "";
			if (look.length() >= 2) {
				try {
					int seed = (int) Long.parseLong(look.substring(0, look.length() - 1), 16);
					boolean p = look.endsWith("p");
					hati = new Tokoh(hati.id(), "@kota:" + seed + ":" + (p ? "p" : "l"), hati.peran(), hati.situs(), hati.watak(), hati.sikap(),
							hati.jumpa(), hari);
					DaftarTokoh.simpan(server, owner, hati);
				} catch (NumberFormatException ignored) {
					// keep the generated face
				}
			}
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.hati.kenal", EntriHarian.Suasana.SENANG, false, hati.nama(), DaftarTokoh.argKota(town));
			return true;
		}
		hati = hati.dijumpai(hari, situs).ubahSikap(soul, 12);
		if (hati.jumpa() < JANJI) {
			DaftarTokoh.simpan(server, owner, hati);
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.hati.jumpa", EntriHarian.Suasana.SENANG, false, hati.nama(), DaftarTokoh.argKota(town));
			return true;
		}
		if (hati.jumpa() == JANJI) {
			DaftarTokoh.simpan(server, owner, hati);
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.hati.janji", EntriHarian.Suasana.SENANG, true, hati.nama(), DaftarTokoh.argKota(town));
			return true;
		}
		RandomSource random = server.overworld().getRandom();
		DaftarTokoh.lupakan(server, owner, hati.id());
		if (random.nextBoolean() && Halls.hallOf(owner) != null) {
			DiscipleData calon = DiscipleData.fresh(random);
			Halls.addCandidates(owner, List.of(new Halls.CandidateSpec(calon, 55 + random.nextInt(41), -1.0D, -1.0D, false, "mandiri",
					"kultivator.sekte.mandiri.cerita.calon.kenalan_hati", 10)));
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.hati.datang", EntriHarian.Suasana.BANGGA, true, hati.nama(), DaftarTokoh.argKota(town));
		} else {
			DiscipleEntity body = Perencana.cari(owner, soul);
			if (body != null) {
				Ingatan.remember(server, body.data(), "mandiri.asmara_pisah", null, 6, "kultivator.sekte.mandiri.cerita.ingatan.asmara_pisah",
						"");
			}
			Cerita.tulis(server, owner, soul, "tokoh", "tokoh.hati.pisah", EntriHarian.Suasana.SEDIH, true, hati.nama(), DaftarTokoh.argKota(town));
		}
		return true;
	}

	private BusurAsmara() {
	}
}
