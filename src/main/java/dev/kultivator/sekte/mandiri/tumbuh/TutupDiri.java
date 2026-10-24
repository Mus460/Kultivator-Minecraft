package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Tutup Diri, seclusion (DESIGN 39.5.7; executor TUTUP_DIRI 45 of C39.B3). Chosen when the cultivation drive is high and
 * the bar stands at half or more (or the realm's peak is reached); the Pertapa path often. The disciple walks to the
 * quietest seat (the measured best seat, the library, the main hall), pastes a seal paper before it and sits
 * {@code ACT_TUTUP_DIRI} for 1-5 days (Seimbang at most 2, Penuh 5; a Pertapa picks the long ones). It does not eat and is
 * not invited to tea ({@code SibukMurid} "tutup_diri" from {@code extra.mandiri.tutup}). Meditation XP runs the old way
 * (it sits), x(1.8 + 0.05/day, at most 2.1) through {@link LajuMandiri}. A faint qi ring circles it now and then.
 * Stopped by the master's order, a fight, or the planner. When the bar fills at a peak during the retreat, it breaks
 * through in place ({@link TerobosanSendiri#diPengasingan}). Leaving: the seal burns, a ring of the Dao's colour, "the
 * retreat is over... my qi is clearer", a diary line and a chance of enlightenment ({@link Pencerahan}).
 */
public final class TutupDiri {

	public static final int HARI_SEIMBANG = 2;
	public static final int HARI_PENUH = 5;
	private static final int KERTAS = 0xF2E3B5;

	static void init() {
		MandiriApi.register(Kegiatan.TUTUP_DIRI, new Pelaksana() {
			@Override
			public boolean bisa(DiscipleEntity murid, Konteks konteks) {
				return boleh(murid);
			}

			@Override
			public void mulai(DiscipleEntity murid, Konteks konteks) {
				int days = konteks.sampai() > konteks.mulai() ? (int) ((konteks.sampai() - konteks.mulai()) / 24000L) : 0;
				masuk(murid, konteks.tempat(), days);
			}

			@Override
			public boolean jalan(DiscipleEntity murid, Konteks konteks) {
				return murid.activity() == MandiriKegiatan.ACT_TUTUP_DIRI && MandiriApi.tutupDiri(murid.data(), murid.level().getGameTime());
			}

			@Override
			public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
				keluar(murid, batal);
			}

			@Override
			public double K(DiscipleEntity murid, Konteks konteks) {
				return LajuMandiri.faktorTutup(murid.data(), murid.level().getGameTime());
			}
		});
	}

	/** The retreat is possible now: a cultivator, unhurt, not breaking through, bar at half or at the peak. */
	static boolean boleh(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		long now = entity.level().getGameTime();
		if (data.realm().isMortal() || data.breakingThrough() || data.deviating() || MandiriApi.luka(data, now) != JenisLuka.TIDAK
				|| MandiriApi.tutupDiri(data, now) || TerobosanSendiri.sedang(entity.soulId())) {
			return false;
		}
		return data.barFraction() >= 0.5D || data.rank().isBottleneck();
	}

	/** Enters the retreat for {@code days} (0 = choose by path and policy) at the quietest seat near {@code saran}. */
	static boolean masuk(DiscipleEntity entity, BlockPos saran, int days) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		DiscipleData data = entity.data();
		int max = MandiriApi.kebijakan(entity).mode() == ModeMandiri.PENUH ? HARI_PENUH : HARI_SEIMBANG;
		if (days <= 0) {
			boolean hermit = MandiriApi.jalan(data) == JalanHidup.PERTAPA;
			days = hermit ? 1 + entity.getRandom().nextInt(max) : 1 + entity.getRandom().nextInt(Math.min(2, max));
		}
		days = Math.max(1, Math.min(max, days));
		BlockPos seat = TempatTumbuh.tenang(level, entity, saran);
		if (seat == null) {
			seat = entity.blockPosition();
		}
		long now = level.getGameTime();
		CompoundTag tutup = new CompoundTag();
		tutup.putLong("mulai", now);
		tutup.putLong("sampai", now + days * 24000L);
		tutup.putInt("tumpuk", days);
		MandiriApi.bagianTulis(data).put(MandiriIds.K_TUTUP, tutup);
		entity.setActivity(MandiriKegiatan.ACT_TUTUP_DIRI, seat);
		SekteSuara.line(entity, "tutup_masuk", "cultivate", 200);
		Vec3 at = Vec3.atBottomCenterOf(seat).add(0.0D, 1.2D, 0.0D);
		Fx.burst(level, at, KERTAS, 10, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PAINTING_PLACE, SoundSource.NEUTRAL, 0.6F, 1.2F);
		KabarTumbuh.catat(entity, "tutup", "tutup.masuk", EntriHarian.Suasana.TENANG, false, "n:" + days);
		return true;
	}

	/** Leaves the retreat ({@code batal}: cut short): the seal burns, the ring, the diary, enlightenment's chance. */
	static void keluar(DiscipleEntity entity, boolean batal) {
		DiscipleData data = entity.data();
		long now = entity.level().getGameTime();
		CompoundTag tutup = MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_TUTUP);
		if (tutup.isEmpty() || tutup.getBooleanOr("selesai", false)) {
			if (entity.activity() == MandiriKegiatan.ACT_TUTUP_DIRI) {
				entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			return;
		}
		long start = tutup.getLongOr("mulai", now);
		CompoundTag live = MandiriApi.bagianTulis(data).getCompoundOrEmpty(MandiriIds.K_TUTUP);
		live.putBoolean("selesai", true);
		if (tutup.getLongOr("sampai", 0L) > now) {
			live.putLong("sampai", now);
		}
		if (entity.activity() == MandiriKegiatan.ACT_TUTUP_DIRI) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		int days = (int) Math.max(0L, (now - start) / 24000L);
		DataTumbuh.tambahStatistik(data, DataTumbuh.S_TUTUP, days);
		if (entity.level() instanceof ServerLevel level) {
			Dao dao = data.dao();
			int color = dao == null ? 0x9FD7A8 : dao.color();
			Vec3 at = entity.position();
			Fx.burst(level, at.add(0.0D, 1.2D, 0.0D), 0xFF8A3A, 14, 0.6F);
			Fx.ring(level, at.add(0.0D, 0.1D, 0.0D), color, 2.6F);
			level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
		}
		if (batal) {
			KabarTumbuh.catat(entity, "tutup", "tutup.terganggu", EntriHarian.Suasana.KESAL, false, "n:" + days);
			return;
		}
		SekteSuara.line(entity, "tutup_keluar", "cultivate", 0);
		KabarTumbuh.catat(entity, "tutup", "tutup.keluar", EntriHarian.Suasana.TENANG, false, "n:" + Math.max(1, days));
		Pencerahan.coba(entity, false);
	}

	/** Once a second while it sits in the retreat: a faint ring now and then, the in-place breakthrough, the end. */
	static void tick20(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		long now = entity.level().getGameTime();
		if (!MandiriApi.tutupDiri(data, now)) {
			if (MandiriApi.kegiatan(entity) != Kegiatan.TUTUP_DIRI) {
				keluar(entity, false);
			}
			return;
		}
		if (entity.tickCount % 200 < 20 && entity.isMeditatingNow() && entity.level() instanceof ServerLevel level) {
			Dao dao = data.dao();
			Fx.ring(level, entity.position().add(0.0D, 0.6D, 0.0D), dao == null ? 0x6FD3FF : dao.color(), 1.1F);
		}
		if (entity.tickCount % 1200 < 20 && TerobosanSendiri.dibottleneck(data)) {
			TerobosanSendiri.diPengasingan(entity);
		}
	}

	private TutupDiri() {
	}
}
