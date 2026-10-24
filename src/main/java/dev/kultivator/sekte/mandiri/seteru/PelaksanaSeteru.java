package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The executors of the four feud activities of the planner (contract C39.A5, activities 57-60 of 39.F.5). They are never
 * chosen by the planner's own scoring: {@link #bisa} answers yes only while 39F itself starts one ({@link #mulai}).
 * <ul>
 * <li>{@code CEKCOK} (57): stands its ground facing the other, finger pointed (the client plays 39F's clip 250/251).</li>
 * <li>{@code TANTANG} (58): the fist-and-palm salute before the honour duel.</li>
 * <li>{@code TERLUKA} (59): badly beaten, it limps home bandaged to the stele (the limp of {@code Gaya.LUKA_PINCANG},
 * Slowness I) and rests there until the wound heals; open-ended, ended by the day count it was given.</li>
 * <li>{@code SUMPAH} (60): kneels, fist on the ground, and swears revenge.</li>
 * </ul>
 */
final class PelaksanaSeteru implements Pelaksana {

	private static final Set<UUID> IZIN = new HashSet<>();

	private final int act;

	private PelaksanaSeteru(int act) {
		this.act = act;
	}

	static void daftar() {
		MandiriApi.register(Kegiatan.CEKCOK, new PelaksanaSeteru(MandiriKegiatan.ACT_CEKCOK));
		MandiriApi.register(Kegiatan.TANTANG, new PelaksanaSeteru(MandiriKegiatan.ACT_TANTANG));
		MandiriApi.register(Kegiatan.TERLUKA, new PelaksanaSeteru(MandiriKegiatan.ACT_TERLUKA));
		MandiriApi.register(Kegiatan.SUMPAH, new PelaksanaSeteru(MandiriKegiatan.ACT_SUMPAH));
	}

	/** Starts a feud activity on a loaded disciple (the only way they start). */
	static boolean mulai(DiscipleEntity murid, Kegiatan kegiatan, int ticks, @Nullable BlockPos tempat, @Nullable UUID teman) {
		if (murid == null) {
			return false;
		}
		IZIN.add(murid.getUUID());
		try {
			return MandiriApi.mulaiKegiatan(murid, kegiatan, ticks, tempat, teman);
		} finally {
			IZIN.remove(murid.getUUID());
		}
	}

	@Override
	public boolean bisa(DiscipleEntity murid, Konteks konteks) {
		return IZIN.contains(murid.getUUID());
	}

	@Override
	public void mulai(DiscipleEntity murid, Konteks konteks) {
		BlockPos spot = konteks.tempat() != null ? konteks.tempat() : murid.blockPosition();
		if (act == MandiriKegiatan.ACT_TERLUKA) {
			var hall = Halls.hallOf(konteks.owner());
			if (hall != null && hall.dim() == murid.level().dimension()) {
				spot = hall.pos().offset(Math.floorMod(murid.getUUID().hashCode(), 7) - 3, 0, 3 + Math.floorMod(murid.getUUID().hashCode() >> 4, 3));
			}
			SosokApi.setLuka(murid, Gaya.LUKA_PINCANG);
			SosokApi.setSuasana(murid, Gaya.SUASANA_SEDIH, 0);
			murid.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 0, false, false, true));
		} else if (act == MandiriKegiatan.ACT_SUMPAH) {
			SosokApi.setSuasana(murid, Gaya.SUASANA_MARAH, 400);
		} else {
			SosokApi.setSuasana(murid, Gaya.SUASANA_MARAH, 300);
		}
		murid.setActivity(act, spot);
		Adegan.tandaiSibuk(murid, (int) Math.min(Integer.MAX_VALUE, Math.max(200L, konteks.sisa(konteks.mulai()))));
	}

	@Override
	public boolean jalan(DiscipleEntity murid, Konteks konteks) {
		long now = murid.level().getGameTime();
		if (act == MandiriKegiatan.ACT_TERLUKA && now % 200L < 20L && !murid.hasEffect(MobEffects.SLOWNESS)) {
			murid.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 0, false, false, true));
		}
		if (murid.activity() != act) {
			return false;
		}
		return konteks.sisa(now) > 0L;
	}

	@Override
	public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		if (act == MandiriKegiatan.ACT_TERLUKA) {
			SosokApi.setLuka(murid, Gaya.LUKA_SEHAT);
			murid.removeEffect(MobEffects.SLOWNESS);
		}
		SosokApi.setSuasana(murid, Gaya.SUASANA_NETRAL, 0);
		if (murid.activity() == act) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	@Override
	public double K(DiscipleEntity murid, Konteks konteks) {
		return 0.0D;
	}
}
