package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.sekte.paviliun.PaviliunMandiri;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Merenung, contemplation (DESIGN 39.4.2; executor MERENUNG 53 of C39.B3): the disciple stands or sits facing a view, the
 * head slightly raised (a cliff, a waterfall, the edge of a sky island, the yard's highest open spot), petals or leaves
 * drifting now and then. While it lasts the comprehension of its Dao grows at twice the meditation rate
 * ({@code MEDITATION_COMPREHENSION x 2} per second, x1.5 with root affinity); at the end of the block the "breathing while
 * working" XP of K 0.4 (given by the planner from {@link Pelaksana#K}), a chance of enlightenment ({@link Pencerahan}), and on a pending Dao choice at night the
 * ceremony of {@link DaoSendiri}. Needs a chosen Dao, the Pertapa path or a Dao ceremony waiting.
 */
public final class Merenung {

	public static final double K = 0.4D;
	/** Twice the meditation comprehension per second. */
	public static final double PEMAHAMAN_PER_DETIK = DiscipleCultivation.MEDITATION_COMPREHENSION * 2.0D;

	/** When each contemplation began (session), for the end-of-block XP. */
	private static final Map<UUID, Long> MULAI = new HashMap<>();

	static void init() {
		MandiriApi.register(Kegiatan.MERENUNG, new Pelaksana() {
			@Override
			public boolean bisa(DiscipleEntity murid, Konteks konteks) {
				DiscipleData data = murid.data();
				return !data.realm().isMortal() && !data.breakingThrough()
						&& (data.dao() != null || MandiriApi.jalan(data) == JalanHidup.PERTAPA || DaoSendiri.menunggu(murid));
			}

			@Override
			public void mulai(DiscipleEntity murid, Konteks konteks) {
				mulaiDi(murid, konteks.tempat());
			}

			@Override
			public boolean jalan(DiscipleEntity murid, Konteks konteks) {
				return murid.activity() == MandiriKegiatan.ACT_MERENUNG && !PaviliunMandiri.sedangBacaTas(murid);
			}

			@Override
			public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
				akhiri(murid, batal);
			}

			@Override
			public double K(DiscipleEntity murid, Konteks konteks) {
				return K;
			}
		});
	}

	/** Goes to a view near {@code saran} and contemplates. */
	static boolean mulaiDi(DiscipleEntity entity, BlockPos saran) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos spot = TempatTumbuh.pandangan(level, entity, saran);
		PaviliunMandiri.lepasDuduk(entity.data());
		entity.setActivity(MandiriKegiatan.ACT_MERENUNG, spot == null ? entity.blockPosition() : spot);
		MULAI.put(entity.soulId(), level.getGameTime());
		return true;
	}

	/** The block ends: XP, enlightenment, the Dao ceremony, the pose cleared. */
	static void akhiri(DiscipleEntity entity, boolean batal) {
		Long start = MULAI.remove(entity.soulId());
		long now = entity.level().getGameTime();
		if (entity.activity() == MandiriKegiatan.ACT_MERENUNG && !PaviliunMandiri.sedangBacaTas(entity)) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		if (start == null || batal || now - start < 100L) {
			return;
		}
		// The breath XP of K 0.4 is the planner's (Perencana.akhiri reads K() for this non-seated activity).
		if (DaoSendiri.menunggu(entity) && DataTumbuh.malam(entity.level())) {
			DaoSendiri.upacara(entity);
			return;
		}
		if (Pencerahan.coba(entity, false) == null && entity.getRandom().nextInt(4) == 0) {
			KabarTumbuh.catat(entity, "renung", "renung.selesai", EntriHarian.Suasana.TENANG, false,
					entity.data().dao() == null ? "" : "d:" + entity.data().dao().id());
		}
	}

	/** Once a second while contemplating (not reading a slip in the same pose): comprehension, a drifting petal. */
	static void tick20(DiscipleEntity entity) {
		if (PaviliunMandiri.sedangBacaTas(entity)) {
			return;
		}
		DiscipleData data = entity.data();
		Dao dao = data.dao();
		BlockPos spot = entity.activitySpot();
		boolean there = spot != null && entity.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) <= 2.25D;
		if (!there) {
			return;
		}
		if (dao != null) {
			data.addComprehension(DiscipleCultivation.comprehensionGain(data, dao, PEMAHAMAN_PER_DETIK));
		}
		if (entity.tickCount % 160 < 20 && entity.level() instanceof ServerLevel level) {
			Fx.burst(level, entity.position().add(0.0D, 2.2D, 0.0D), dao == null ? 0xF7C6D9 : dao.color(), 3, 0.9F);
		}
		if (!MULAI.containsKey(entity.soulId())) {
			MULAI.put(entity.soulId(), entity.level().getGameTime());
		}
	}

	static void clear() {
		MULAI.clear();
	}

	private Merenung() {
	}
}
