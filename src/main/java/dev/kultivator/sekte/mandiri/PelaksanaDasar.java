package dev.kultivator.sekte.mandiri;

import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The five executors 39A owns (DESIGN 39.12: MEDITASI 56, ISTIRAHAT 51, MAKAN 48, JAGA_GERBANG and BERMAIN) plus the
 * planner's own two events that borrow a pose: the morning salute at 05:00 (the free disciples line up in the courtyard
 * facing the hall, contemplation pose, ten minutes of morning breath) and the master's redemption ritual (39.3.3).
 * <p>
 * Each executor sets the synced body activity with its place ({@code DiscipleEntity.setActivity}); the old
 * {@code ReturnToPostGoal} walks the disciple there and holds it, and seated ids (56) count as meditation for the old XP,
 * regeneration, Master Guidance and formation factor. The gate watch is a temporary {@code GUARD_HALL} patrol set
 * through {@code MandiriApi.setModeSendiri} (not counted as the master's order) and the old mode is restored after.
 */
public final class PelaksanaDasar {

	/** The morning salute (acara {@code salam}). */
	public static final String ACARA_SALAM = "salam";
	/** The master's redemption ritual (acara {@code tebus}). */
	public static final String ACARA_TEBUS = "tebus";
	/** The salute lasts ten world minutes. */
	public static final int SALAM_TICK = 170;

	static void init() {
		MandiriApi.register(Kegiatan.MEDITASI, new Meditasi());
		MandiriApi.register(Kegiatan.ISTIRAHAT, new Duduk(Kegiatan.ISTIRAHAT));
		MandiriApi.register(Kegiatan.MAKAN, new Duduk(Kegiatan.MAKAN));
		MandiriApi.register(Kegiatan.JAGA_GERBANG, new JagaGerbang());
		MandiriApi.register(Kegiatan.BERMAIN, new Bermain());
	}

	/** The executor of a planner event, or null. */
	static @Nullable Pelaksana acara(String id) {
		return switch (id) {
			case ACARA_SALAM -> SALAM;
			case ACARA_TEBUS -> TEBUS;
			default -> null;
		};
	}

	// ================================================================== MEDITASI 56

	/** Self-chosen meditation at the best seat; measures the seat's factor for the catch-up (39.4.7). */
	static final class Meditasi implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			// Also with a full bar at a bottleneck: the qi then goes into Stored Qi (the old rule).
			return konteks.tempat() != null;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			murid.setActivity(Kegiatan.MEDITASI.act(), konteks.tempat());
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			if (murid.activity() != Kegiatan.MEDITASI.act()) {
				return false;
			}
			if (murid.isMeditatingNow() && murid.tickCount % 200 == 0) {
				ukurTempat(murid);
			}
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			lepasAktivitas(murid, Kegiatan.MEDITASI.act());
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			// Seated: the old meditation tick gives the XP (39B's factor x1.5); no extra breath XP at the end.
			return 0.0D;
		}
	}

	/**
	 * The seat's own factor = the XP multiplier with the body (formation, site, sect hooks) over the one without it,
	 * kept as {@code tempatTerbaik} when it beats the stored seat (39.4.7: the catch-up uses at most this factor).
	 */
	static void ukurTempat(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		double tanpa = DiscipleHooks.xpFactor(null, data, XpSource.MEDITATION);
		if (!(tanpa > 0.0D)) {
			return;
		}
		double faktor = Math.max(0.5D, Math.min(2.0D, DiscipleHooks.xpFactor(murid, data, XpSource.MEDITATION) / tanpa));
		CompoundTag section = MandiriApi.bagianTulis(data);
		CompoundTag best = section.getCompoundOrEmpty(MandiriIds.K_TEMPAT_TERBAIK);
		BlockPos here = murid.blockPosition();
		BlockPos old = best.contains("pos") ? BlockPos.of(best.getLongOr("pos", 0L)) : null;
		if (best.isEmpty() || faktor >= best.getDoubleOr("faktor", 0.0D) || here.equals(old)) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("pos", here.asLong());
			tag.putDouble("faktor", Math.round(faktor * 1000.0D) / 1000.0D);
			section.put(MandiriIds.K_TEMPAT_TERBAIK, tag);
		}
	}

	// ================================================================== ISTIRAHAT 51, MAKAN 48

	/** Resting (lying, leaning on a tree or bench) and eating (bowl and chopsticks): hold the place, nothing else. */
	static final class Duduk implements Pelaksana {
		private final Kegiatan kegiatan;

		Duduk(Kegiatan kegiatan) {
			this.kegiatan = kegiatan;
		}

		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			if (konteks.tempat() == null) {
				return false;
			}
			if (kegiatan == Kegiatan.MAKAN) {
				// Meals at the meal hours or when tired; never twice in a row.
				int jam = Irama.jam(murid.level());
				boolean waktuMakan = jam == 6 || jam == 7 || jam == 12 || jam == 18 || jam == 19;
				return waktuMakan || Dorongan.baca(murid.data(), dev.kultivator.sekte.mandiri.api.Dorong.LELAH) >= 60;
			}
			return true;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			murid.setActivity(kegiatan.act(), konteks.tempat());
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return murid.activity() == kegiatan.act();
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			lepasAktivitas(murid, kegiatan.act());
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			if (kegiatan == Kegiatan.ISTIRAHAT) {
				// "A cultivator's sleep" from Golden Core.
				return murid.data().realm().ordinal() >= Realm.GOLDEN_CORE.ordinal() ? 0.8D : 0.5D;
			}
			return kegiatan.k();
		}
	}

	// ================================================================== JAGA_GERBANG

	/** A temporary hall patrol (GUARD_HALL) for guardians and the brave; the old mode comes back after. */
	static final class JagaGerbang implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return konteks.tempat() != null && murid.mode() != DiscipleEntity.Mode.GUARD_HALL
					&& murid.data().realm().ordinal() >= Realm.QI_REFINING.ordinal() && murid.getHealth() > murid.getMaxHealth() * 0.6F;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			CompoundTag kegiatan = MandiriApi.bagianTulis(murid.data()).getCompoundOrEmpty(MandiriIds.K_KEGIATAN);
			kegiatan.putString(MandiriIds.KG_MODE_ASAL, murid.mode().id());
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			MandiriApi.setModeSendiri(murid, DiscipleEntity.Mode.GUARD_HALL);
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return murid.mode() == DiscipleEntity.Mode.GUARD_HALL;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			String asal = MandiriApi.bagian(murid.data()).getCompoundOrEmpty(MandiriIds.K_KEGIATAN).getStringOr(MandiriIds.KG_MODE_ASAL, "");
			DiscipleEntity.Mode mode = DiscipleEntity.Mode.byId(asal);
			// A running order pause means the master set GUARD_HALL himself during the watch: keep his order.
			if (murid.mode() == DiscipleEntity.Mode.GUARD_HALL && mode != null && mode != DiscipleEntity.Mode.GUARD_HALL
					&& MandiriApi.bebasSampai(murid.data()) <= murid.level().getGameTime()) {
				MandiriApi.setModeSendiri(murid, mode);
			}
		}
	}

	// ================================================================== BERMAIN

	/**
	 * Snow, harvest or festival play in the courtyard: no body activity (the tea pose of 18 would swallow a loop clip), the
	 * two play with a real clip, a chase (MAIN_KEJAR) and in dry weather kicking the cuju ball (MAIN_CUJU, ball prop), turned
	 * towards each other, with snowflakes in snow and happy sparks otherwise.
	 */
	static final class Bermain implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return konteks.tempat() != null && konteks.teman() != null;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			// no body activity: the walk to the place is the executor's own (jalan)
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			if (murid.activity() != DiscipleEntity.ACTIVITY_NONE) {
				return false;
			}
			BlockPos tempat = konteks.tempat();
			if (tempat != null && !murid.mode().guardsPost() && murid.tickCount % 20 == 0
					&& murid.distanceToSqr(tempat.getX() + 0.5D, tempat.getY(), tempat.getZ() + 0.5D) > 9.0D) {
				murid.getNavigation().moveTo(tempat.getX() + 0.5D, tempat.getY(), tempat.getZ() + 0.5D, 1.0D);
			}
			if (murid.tickCount % 40 == 0 && murid.level() instanceof ServerLevel level && murid.getNavigation().isDone()) {
				boolean snow = Irama.salju(level, murid.blockPosition());
				SimpleParticleType type = snow ? ParticleTypes.SNOWFLAKE : ParticleTypes.HAPPY_VILLAGER;
				level.sendParticles(type, murid.getX(), murid.getY() + 1.4D, murid.getZ(), snow ? 4 : 2, 0.4D, 0.3D, 0.4D, 0.02D);
				DiscipleEntity teman = konteks.teman() == null ? null : Perencana.cari(konteks.owner(), konteks.teman());
				if (teman != null && !teman.isRemoved() && teman.distanceToSqr(murid) < 100.0D) {
					murid.getLookControl().setLookAt(teman, 30.0F, 30.0F);
				}
				// 80-tick turns of chasing and (dry) kicking; a base clip, so it only restarts when it changes
				if (!snow && murid.tickCount / 80 % 2 == 0) {
					SosokApi.gerak(murid, KotaGerakId.MAIN_CUJU, 0, 0, KotaGerakId.PROP_BOLA_CUJU, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
				} else {
					SosokApi.gerak(murid, GerakId.MAIN_KEJAR, 0, 0);
				}
			}
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			SosokApi.henti(murid);
		}
	}

	// ================================================================== events: the morning salute, the redemption

	/** The 05:00 salute: lined up facing the hall, contemplation pose, then everyone goes on with the day. */
	static final Pelaksana SALAM = new Pelaksana() {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return konteks.tempat() != null;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			murid.setActivity(Kegiatan.MERENUNG.act(), konteks.tempat());
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			if (murid.activity() != Kegiatan.MERENUNG.act()) {
				return false;
			}
			BlockPos face = Irama.pusat(konteks.owner());
			if (face != null && murid.getNavigation().isDone()) {
				murid.getLookControl().setLookAt(face.getX() + 0.5D, face.getY() + 1.5D, face.getZ() + 0.5D);
			}
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			lepasAktivitas(murid, Kegiatan.MERENUNG.act());
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			return 0.4D;
		}
	};

	/** The master's redemption ritual: kneeling before the stele for 60 s while the master stays within 6 blocks. */
	static final Pelaksana TEBUS = new Pelaksana() {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return true;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			murid.setActivity(Kegiatan.MERENUNG.act(), konteks.tempat());
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return murid.activity() == Kegiatan.MERENUNG.act()
					&& JalanIblis.langkahTebus(murid, konteks.mulai(), murid.level().getGameTime());
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			lepasAktivitas(murid, Kegiatan.MERENUNG.act());
			long now = murid.level().getGameTime();
			JalanIblis.selesaiTebus(murid, !batal && konteks.sampai() >= 0L && now >= konteks.sampai() - 20L);
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			return 0.0D;
		}
	};

	// ================================================================== helpers

	/** Clears the body activity only when it is still the one this executor set (an old module may have taken over). */
	static void lepasAktivitas(DiscipleEntity murid, int act) {
		if (murid.activity() == act) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	static @Nullable UUID uuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private PelaksanaDasar() {
	}
}
