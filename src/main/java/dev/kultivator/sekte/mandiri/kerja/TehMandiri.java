package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.hubungan.HubunganMandiri;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SOSIAL / TEH (activity 18, DESIGN 39.4.2): the disciple invites a friend for tea instead of waiting for R's tea turn.
 * The companion is the planner's ({@code Konteks.teman}: the free hall-mate with the best affinity within 24 blocks);
 * sworn enemies never sit together and a pair meets at most twice a day (R's own rules, through
 * {@code HubunganMandiri.cocok}). The tea itself is R's session ({@code HubunganMandiri.minum}): the walk to two facing
 * seats, the cups, the tea bubbles and the greeting line, and at the end R's own affinity change from their compatibility
 * and mood, with memories and the meeting count.
 * <p>
 * After R's tea is drunk the two linger at the table until the planner's hour of tea is over (the inviter keeps the tea
 * pose; a little steam rises now and then), so the block ends as a finished one: the liked-activity honour of 39A and,
 * after 19:00 with a sworn friend, a night toward redemption (39.3.3). Both write the evening into their diaries.
 */
final class TehMandiri implements Pelaksana {

	/** A running tea of an inviter: its friend, when the two sat down (estimated), where it sits. */
	private static final class Teh {
		final DiscipleEntity teman;
		long duduk = -1L;
		boolean diminum;
		BlockPos kursi;

		Teh(DiscipleEntity teman) {
			this.teman = teman;
		}
	}

	private static final Map<UUID, Teh> TEH = new HashMap<>();

	static void kosongkan() {
		TEH.clear();
	}

	@Override
	public boolean bisa(DiscipleEntity murid, Konteks konteks) {
		if (!HubunganMandiri.aktif() || konteks.tempat() == null || konteks.teman() == null || !HubunganMandiri.bebas(murid)) {
			return false;
		}
		DiscipleEntity teman = Tanding.cari(murid, konteks.teman());
		return teman != null && HubunganMandiri.bebas(teman) && Tanding.bebasDiajak(teman) && HubunganMandiri.cocok(konteks.owner(), murid, teman);
	}

	@Override
	public void mulai(DiscipleEntity murid, Konteks konteks) {
		DiscipleEntity teman = konteks.teman() == null ? null : Tanding.cari(murid, konteks.teman());
		if (teman == null) {
			return;
		}
		if (MandiriApi.kegiatan(teman) != null) {
			// The friend leaves its rest (finished, not interrupted) and comes to the table.
			MandiriApi.akhiriKegiatan(teman, false);
		}
		if (HubunganMandiri.minum(konteks.owner(), murid, teman) == null) {
			TEH.put(murid.soulId(), new Teh(teman));
		}
	}

	@Override
	public boolean jalan(DiscipleEntity murid, Konteks konteks) {
		Teh teh = TEH.get(murid.soulId());
		if (teh == null || murid.activity() != SekteIds.ACT_SOCIAL || !(murid.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (!teh.diminum && teh.duduk < 0L && murid.getNavigation().isDone() && teh.teman.getNavigation().isDone()
				&& murid.distanceToSqr(teh.teman) <= 3.5D * 3.5D) {
			teh.duduk = now;
			teh.kursi = murid.blockPosition();
		}
		if (teh.diminum) {
			// Lingering at the table: the inviter still faces the friend's seat, a thread of steam from the cup.
			if (teh.teman.isAlive() && teh.teman.distanceToSqr(murid) < 64.0D) {
				murid.getLookControl().setLookAt(teh.teman, 20.0F, 20.0F);
			}
			RandomSource random = murid.getRandom();
			if (random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.WHITE_SMOKE, murid.getX(), murid.getY() + 1.1D, murid.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.005D);
			}
		}
		return true;
	}

	/**
	 * Every server tick after R's own (Hubungan registers its tick first): when R's tea of an inviter is over and R gave
	 * the body its idle activity back, the inviter keeps sitting in the tea pose until its planner block ends.
	 */
	static void tick(MinecraftServer server) {
		if (TEH.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Teh>> it = TEH.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Teh> entry = it.next();
			Teh teh = entry.getValue();
			DiscipleEntity murid = Tanding.cari(teh.teman, entry.getKey());
			if (murid == null) {
				it.remove();
				continue;
			}
			if (teh.diminum || HubunganMandiri.diMeja(murid) || MandiriApi.kegiatan(murid) != Kegiatan.SOSIAL) {
				continue;
			}
			if (murid.activity() == DiscipleEntity.ACTIVITY_NONE && teh.duduk >= 0L) {
				teh.diminum = true;
				murid.setActivity(SekteIds.ACT_SOCIAL, teh.kursi != null ? teh.kursi : murid.blockPosition());
			}
		}
	}

	@Override
	public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		Teh teh = TEH.remove(murid.soulId());
		if (murid.activity() == SekteIds.ACT_SOCIAL && (teh == null || teh.diminum || !HubunganMandiri.diMeja(murid))) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		if (teh == null || !teh.diminum) {
			return;
		}
		DiscipleEntity teman = teh.teman;
		UUID owner = murid.ownerId();
		Ikatan ikatan = owner == null ? Ikatan.NONE : SekteApi.hubungan().kind(owner, murid.soulId(), teman.soulId());
		String jenis = ikatan == null || ikatan == Ikatan.NONE ? DataKerja.P + "teman" : ikatan.key();
		KabarKerja.catat(murid, "teh", "teh.selesai", EntriHarian.Suasana.SENANG, false, teman.getName().getString(), jenis);
		KabarKerja.catat(teman, "teh", "teh.selesai", EntriHarian.Suasana.SENANG, false, murid.getName().getString(), jenis);
	}

	/**
	 * The catch-up event of 39.4.7 step 4 ("tea with a sworn friend"): the best friend in the hall's loaded list gets
	 * {@code HubunganService.adjust} +1..+3 with the reason {@code mandiri_teh} and a diary entry dated {@code hari}.
	 */
	static boolean kejar(DiscipleEntity murid, long hari, RandomSource random) {
		UUID owner = murid.ownerId();
		if (owner == null) {
			return false;
		}
		List<UUID> friends = SekteApi.hubungan().with(owner, murid.soulId(), Ikatan.SAHABAT);
		if (friends.isEmpty()) {
			return false;
		}
		UUID friend = friends.get(random.nextInt(friends.size()));
		int delta = 1 + random.nextInt(3);
		SekteApi.hubungan().adjust(owner, murid.soulId(), friend, delta, "mandiri_teh");
		DiscipleEntity body = Tanding.cari(murid, friend);
		String name = body != null ? body.getName().getString() : DataKerja.P + "sahabat";
		KabarKerja.catatPada(owner, murid.soulId(), hari, 20, "teh", "teh.kejar", EntriHarian.Suasana.SENANG, false, name);
		return true;
	}
}
