package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.keeper.KeeperOrder;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.kebun.KebunMandiri;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The sect work a disciple takes up by itself (DESIGN 39.4.2 rows KEBUN, TAMBANG, POS_TUGAS, JAGA_FORMASI): every one goes
 * through the old module's own job with its old rules, output and look, only the master's order is replaced by the
 * planner's block, and the job is left when the block ends. None of them is ever "forced work" (39.10.2): the old work
 * streak of W does not count a job the disciple chose.
 * <ul>
 *   <li><b>KEBUN</b> (16, W): a free gardener slot through {@code KebunMandiri.rawat}; W's beds grow faster while tended.</li>
 *   <li><b>TAMBANG</b> (15, W): a free miner slot through {@code KebunMandiri.tambang}; W's lazy yield table.</li>
 *   <li><b>POS_TUGAS</b> (1-4, Duties): a free duty post through {@code Duties.assignSelf}; the duty follows the path and the
 *   root (an alchemist goes to the Ovens, a hermit carries water, Wood roots tend the garden...); the old lazy output.</li>
 *   <li><b>JAGA_FORMASI</b> (11, Keepers): the nearest running core of the hall with a free keeper slot through
 *   {@code Keepers.assignSelf}; Perkuat when the disciple's realm is above the grade's keeper realm, else Jaga.</li>
 * </ul>
 */
final class KerjaSekte {

	private static final String POS = "pos";
	private static final String ORDER = "jagaOrder";

	static void daftar() {
		MandiriApi.register(Kegiatan.KEBUN, new Kebun());
		MandiriApi.register(Kegiatan.TAMBANG, new Tambang());
		MandiriApi.register(Kegiatan.POS_TUGAS, new Pos());
		MandiriApi.register(Kegiatan.JAGA_FORMASI, new JagaFormasi());
	}

	// ================================================================== KEBUN 16

	static final class Kebun implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			UUID owner = konteks.owner();
			return konteks.tempat() != null && owner != null && KebunMandiri.slotRawatBebas(owner) > 0 && KebunMandiri.boleh(owner, murid);
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			KebunMandiri.rawat(konteks.owner(), murid);
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			if (murid.activity() != SekteIds.ACT_TEND || !(murid.level() instanceof ServerLevel level)) {
				return false;
			}
			if (murid.getNavigation().isDone() && murid.tickCount % 60 < 20) {
				level.sendParticles(ParticleTypes.COMPOSTER, murid.getX(), murid.getY() + 0.3D, murid.getZ(), 2, 0.4D, 0.1D, 0.4D, 0.0D);
				level.playSound(null, murid.getX(), murid.getY(), murid.getZ(), SoundEvents.BONE_MEAL_USE, SoundSource.NEUTRAL, 0.3F, 1.2F);
			}
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			boolean sendiri = KebunMandiri.sendiri(murid);
			KebunMandiri.lepas(konteks.owner(), murid);
			if (sendiri && !batal) {
				KabarKerja.catat(murid, "kebun", "kebun.selesai", EntriHarian.Suasana.TENANG, false);
				KabarKerja.suara(murid, "harvest", 4800);
			}
		}
	}

	// ================================================================== TAMBANG 15

	static final class Tambang implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			UUID owner = konteks.owner();
			return konteks.tempat() != null && owner != null && murid.data().realm().atLeast(Realm.QI_REFINING)
					&& KebunMandiri.slotTambangBebas(owner) > 0 && KebunMandiri.boleh(owner, murid);
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			KebunMandiri.tambang(konteks.owner(), murid);
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return murid.activity() == SekteIds.ACT_MINE;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			boolean sendiri = KebunMandiri.sendiri(murid);
			KebunMandiri.lepas(konteks.owner(), murid);
			if (sendiri && !batal) {
				KabarKerja.catat(murid, "tambang", "tambang.selesai", EntriHarian.Suasana.LELAH, false);
			}
		}
	}

	// ================================================================== POS_TUGAS 1-4

	static final class Pos implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			UUID owner = konteks.owner();
			// The planner ends the running activity before it starts this one, so the body's activity is not checked here.
			return konteks.tempat() != null && owner != null && Duties.selfPostOpen(owner, tugasPilihan(murid.data())) != null;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			UUID owner = konteks.owner();
			String duty = Duties.selfPostOpen(owner, tugasPilihan(murid.data()));
			if (duty != null && Duties.assignSelf(owner, murid, duty)) {
				DataKerja.tulis(murid.data()).putString(POS, duty);
			}
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			String duty = DataKerja.baca(murid.data()).getStringOr(POS, "");
			return !duty.isEmpty() && murid.activity() == Duties.activityOf(duty) && duty.equals(Duties.selfDutyOf(murid.data()));
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			CompoundTag own = DataKerja.tulis(murid.data());
			String duty = own.getStringOr(POS, "");
			own.remove(POS);
			boolean held = Duties.releaseSelf(konteks.owner(), murid.soulId());
			if (held && !batal && !duty.isEmpty()) {
				KabarKerja.catat(murid, "pos", "pos." + duty, duty.equals(Duties.WATER) ? EntriHarian.Suasana.LELAH : EntriHarian.Suasana.TENANG,
						false);
			}
		}
	}

	/** The post a disciple likes: by its path first, then its root (the old factors favour Wood in the garden, Fire at the Ovens). */
	static String tugasPilihan(DiscipleData data) {
		JalanHidup jalan = MandiriApi.jalan(data);
		if (jalan != null) {
			switch (jalan) {
				case ALKEMIS -> {
					return Duties.KITCHEN;
				}
				case PERTAPA, PENJAGA, PEDANG -> {
					return Duties.WATER;
				}
				case PEDAGANG -> {
					return Duties.FARM;
				}
				default -> {
				}
			}
		}
		int roots = data.rootElements();
		if ((roots & Element.WOOD.bit()) != 0) {
			return Duties.GARDEN;
		}
		if ((roots & Element.FIRE.bit()) != 0) {
			return Duties.KITCHEN;
		}
		return Duties.WATER;
	}

	// ================================================================== JAGA_FORMASI 11

	static final class JagaFormasi implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			UUID owner = konteks.owner();
			return konteks.tempat() != null && owner != null && Keepers.selfCore(owner, murid) != null;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			UUID owner = konteks.owner();
			CoreRecord core = Keepers.selfCore(owner, murid);
			if (core == null) {
				return;
			}
			KeeperOrder order = perintah(murid.data(), core);
			if (Keepers.assignSelf(owner, konteks.level(), core.id, murid, order) == null) {
				DataKerja.tulis(murid.data()).putString(ORDER, order.id());
				KabarKerja.suara(murid, "formation", 6000);
			}
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return Keepers.selfKept(murid);
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			CompoundTag own = DataKerja.tulis(murid.data());
			String order = own.getStringOr(ORDER, "");
			own.remove(ORDER);
			if (Keepers.selfKept(murid)) {
				Keepers.release(murid);
				if (!batal) {
					KabarKerja.catat(murid, "formasi", "perkuat".equals(order) ? "formasi.perkuat" : "formasi.jaga", EntriHarian.Suasana.TENANG, false);
				}
			}
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			return "perkuat".equals(DataKerja.baca(murid.data()).getStringOr(ORDER, "")) ? 0.25D : 0.5D;
		}
	}

	/** Perkuat when the disciple stands a realm or more above the grade's keeper realm (it adds to the core), else Jaga. */
	static KeeperOrder perintah(DiscipleData data, @Nullable CoreRecord core) {
		FormationSpec spec = core == null ? null : FormationRegistry.byId(core.spec);
		if (spec == null) {
			return KeeperOrder.JAGA;
		}
		Realm need = spec.grade().keeperRealm().realm();
		return data.realm().ordinal() > need.ordinal() ? KeeperOrder.PERKUAT : KeeperOrder.JAGA;
	}

	private KerjaSekte() {
	}
}
