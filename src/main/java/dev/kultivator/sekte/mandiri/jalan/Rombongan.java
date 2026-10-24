package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.api.HubunganService;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.util.SibukMurid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Travelling together (DESIGN 39.6.10): the leader asks one to three hall-mates with affinity 30 or more or a close bond
 * who are free, allowed to make that kind of journey and not hurt; a sweetheart or Dao partner always comes along when
 * free. Shopping trips take at most one companion, adventures up to three. The party shares the plan, the stages and the
 * story; every member writes its own diary line and comes home closer to the others (+3 when it went well).
 */
final class Rombongan {

	/** The companions the leader takes (not the leader itself), nearest bond first. */
	static List<DiscipleEntity> ajak(DiscipleEntity leader, JenisJalan jenis) {
		List<DiscipleEntity> out = new ArrayList<>();
		UUID owner = leader.ownerId();
		if (owner == null) {
			return out;
		}
		int maks = jenis == JenisJalan.BELANJA || jenis == JenisJalan.JUAL || jenis == JenisJalan.ZIARAH ? 1
				: 1 + Math.floorMod(leader.soulId().hashCode() >>> 5, 3);
		HubunganService hub = SekteApi.hubungan();
		List<DiscipleEntity> sweet = new ArrayList<>();
		List<DiscipleEntity> rest = new ArrayList<>();
		for (DiscipleEntity other : Perencana.dimuat(owner)) {
			if (other == leader || other.distanceToSqr(leader) > 48.0D * 48.0D || !bebas(other) || !MandiriApi.kebijakan(other).boleh(jenis.izin())) {
				continue;
			}
			Ikatan bond = hub.kind(owner, leader.soulId(), other.soulId());
			if (bond == Ikatan.KEKASIH || bond == Ikatan.PASANGAN_DAO) {
				sweet.add(other);
			} else if (bond == Ikatan.SAHABAT || bond == Ikatan.SAUDARA_ANGKAT || hub.affinity(owner, leader.soulId(), other.soulId()) >= 30) {
				rest.add(other);
			}
		}
		rest.sort((a, b) -> Integer.compare(hub.affinity(owner, leader.soulId(), b.soulId()), hub.affinity(owner, leader.soulId(), a.soulId())));
		out.addAll(sweet);
		for (DiscipleEntity d : rest) {
			if (out.size() >= maks) {
				break;
			}
			out.add(d);
		}
		while (out.size() > Math.max(maks, sweet.size())) {
			out.remove(out.size() - 1);
		}
		return out;
	}

	/** Free to leave: no order, no fight, no breakthrough, no other module's activity, not hurt badly, not travelling. */
	static boolean bebas(DiscipleEntity e) {
		if (e.role() != DiscipleEntity.Role.DISCIPLE || e.getTarget() != null || e.data().breakingThrough() || e.mode().follows()) {
			return false;
		}
		long now = e.level().getGameTime();
		if (MandiriApi.bebasSampai(e.data()) > now || MandiriApi.luka(e.data(), now) != JenisLuka.TIDAK) {
			return false;
		}
		Kegiatan running = MandiriApi.kegiatan(e);
		if (running == null) {
			return e.activity() == 0 && SibukMurid.why(e) == null;
		}
		return running == Kegiatan.ISTIRAHAT || running == Kegiatan.MAKAN || running == Kegiatan.BERMAIN || running == Kegiatan.SOSIAL;
	}

	private Rombongan() {
	}
}
