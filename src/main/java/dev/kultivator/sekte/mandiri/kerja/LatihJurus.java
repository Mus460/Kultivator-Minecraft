package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.Mastery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LATIH_JURUS (activity 40, DESIGN 39.4.2, 39.4.8): the disciple practises one of its techniques alone on the training
 * ground. It walks to its slot of the arena (or the courtyard), then runs the four-form kata of the design once every four
 * seconds, a form a second: the thrust (a vanilla stab swing, sparks ahead), the circling cut (a whack swing, the sweep
 * arc, the body turned 30 degrees), the parry (a short clack) and the horse stance (a breath, the body square again). The
 * client adds the sword and the pose (40G's {@code AktivitasKlip} for 40).
 * <p>
 * <b>What it gives.</b> Every full kata cycle at the spot is one practice point; every 5 points are one mastery point of
 * the practised art ({@code Mastery.addDisciple}), at most {@link #POIN_HARI} mastery points per world day (the kata
 * never hits anything, so it follows the spirit of {@code Mastery}'s no-hit cap at the disciple rate 0.5). The block's
 * breath XP uses {@code K = 0.6 + 0.1 x mastery level} (39.5.1). A new mastery level is a diary entry, a news line and a
 * notice. Nothing is hurt and no block changes.
 * <p>
 * <b>Which art.</b> The one furthest from Puncak among the disciple's arts, with the path's own Dao first (Pendekar Pedang:
 * the Sword Dao, Pemburu: the Body Dao) and the disciple's own Dao next.
 */
final class LatihJurus implements Pelaksana {

	/** Mastery points a disciple may earn by practising alone per world day (DAILY_NO_HIT_CAP 20 x DISCIPLE_RATE 0.5). */
	static final int POIN_HARI = 10;
	/** Practice points (kata cycles) per mastery point. */
	static final int SIKLUS_PER_POIN = 5;
	private static final String SPEC = "latihSpec";
	private static final String SIKLUS = "latihSiklus";
	private static final String ARAH = "latihArah";

	@Override
	public boolean bisa(DiscipleEntity murid, Konteks konteks) {
		DiscipleData data = murid.data();
		return konteks.tempat() != null && murid.getHealth() > murid.getMaxHealth() * 0.5F && !data.learnedSpecs().isEmpty()
				&& pilih(data) != null;
	}

	@Override
	public void mulai(DiscipleEntity murid, Konteks konteks) {
		TechniqueSpec spec = pilih(murid.data());
		CompoundTag own = DataKerja.tulis(murid.data());
		own.putString(SPEC, spec == null ? "" : spec.id());
		own.putInt(SIKLUS, 0);
		own.putFloat(ARAH, murid.getRandom().nextFloat() * 360.0F);
		murid.setActivity(Kegiatan.LATIH_JURUS.act(), konteks.tempat());
	}

	@Override
	public boolean jalan(DiscipleEntity murid, Konteks konteks) {
		if (murid.activity() != Kegiatan.LATIH_JURUS.act() || !(murid.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos spot = konteks.tempat();
		if (spot == null || !murid.getNavigation().isDone() || murid.distanceToSqr(Vec3.atBottomCenterOf(spot)) > 4.0D) {
			return true;
		}
		long now = level.getGameTime();
		int form = (int) Math.floorMod((now - konteks.mulai()) / 20L, 4L);
		CompoundTag own = DataKerja.tulis(murid.data());
		float arah = own.getFloatOr(ARAH, 0.0F);
		kata(murid, level, form, arah);
		if (form == 3) {
			own.putInt(SIKLUS, own.getIntOr(SIKLUS, 0) + 1);
		}
		return true;
	}

	/** One form of the kata, in front of the disciple's facing {@code arah} (degrees). */
	private static void kata(DiscipleEntity murid, ServerLevel level, int form, float arah) {
		float yaw = arah + (form == 1 ? 30.0F : 0.0F);
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 eye = murid.getEyePosition();
		Vec3 front = murid.position().add(forward.x * 1.3D, 1.1D, forward.z * 1.3D);
		murid.getLookControl().setLookAt(eye.x + forward.x * 4.0D, eye.y, eye.z + forward.z * 4.0D);
		RandomSource random = murid.getRandom();
		switch (form) {
			case 0 -> {
				// The thrust: a stab swing every tracker sees, sparks at the blade's tip.
				murid.swing(InteractionHand.MAIN_HAND, new SwingAnimation(SwingAnimationType.STAB, 8), false);
				level.sendParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 3, 0.15D, 0.1D, 0.15D, 0.05D);
				level.playSound(null, murid.getX(), murid.getY(), murid.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.4F,
						1.2F + random.nextFloat() * 0.2F);
			}
			case 1 -> {
				// The circling cut: the sweep arc beside the turned body.
				murid.swing(InteractionHand.MAIN_HAND, new SwingAnimation(SwingAnimationType.WHACK, 10), false);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				level.playSound(null, murid.getX(), murid.getY(), murid.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.45F,
						1.1F + random.nextFloat() * 0.2F);
			}
			case 2 -> {
				// The parry: a short clack, a glint where the blades would meet.
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, front.x, front.y + 0.2D, front.z, 2, 0.1D, 0.1D, 0.1D, 0.02D);
				level.playSound(null, murid.getX(), murid.getY(), murid.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.35F,
						1.5F + random.nextFloat() * 0.2F);
			}
			default -> {
				// The horse stance: one slow breath, a faint swirl of qi at the feet.
				level.sendParticles(ParticleTypes.ENCHANT, murid.getX(), murid.getY() + 0.3D, murid.getZ(), 3, 0.35D, 0.1D, 0.35D, 0.3D);
			}
		}
	}

	@Override
	public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		if (murid.activity() == Kegiatan.LATIH_JURUS.act()) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		DiscipleData data = murid.data();
		CompoundTag own = DataKerja.tulis(data);
		String id = own.getStringOr(SPEC, "");
		int siklus = own.getIntOr(SIKLUS, 0);
		own.remove(SPEC);
		own.remove(SIKLUS);
		own.remove(ARAH);
		TechniqueSpec spec = spec(data, id);
		if (spec == null || siklus <= 0) {
			return;
		}
		int poin = beriPoin(murid, spec, siklus / SIKLUS_PER_POIN);
		int levelSebelum = Mastery.level(data, spec.id());
		if (poin > 0) {
			Mastery.addDisciple(data, spec.id(), poin);
		}
		int levelSesudah = Mastery.level(data, spec.id());
		if (levelSesudah > levelSebelum) {
			String tingkat = "kultivator.teknik.mastery." + levelSesudah;
			KabarKerja.catat(murid, "latih", "latih.naik", EntriHarian.Suasana.BANGGA, levelSesudah >= 3, spec.nameKey(), tingkat);
			KabarKerja.kabar(murid, "latih_naik", levelSesudah >= 3, murid.getName().getString(), spec.nameKey(), tingkat);
			KabarKerja.beritahu(murid, Kebijakan.Pemberitahuan.TEKNIK, KabarKerja.baris("pesan.latih_naik", 0x8FE3C8,
					KabarKerja.nama(murid), spec.displayName(), net.minecraft.network.chat.Component.translatable(tingkat)), levelSesudah >= 3);
			KabarKerja.suara(murid, "technique", 2400);
			murid.refreshSynced();
		} else if (!batal && siklus >= 8) {
			KabarKerja.catat(murid, "latih", "latih.selesai", EntriHarian.Suasana.TENANG, false, spec.nameKey(), String.valueOf(siklus));
		}
	}

	@Override
	public double K(DiscipleEntity murid, Konteks konteks) {
		TechniqueSpec spec = spec(murid.data(), DataKerja.baca(murid.data()).getStringOr(SPEC, ""));
		int level = spec == null ? 0 : Mastery.level(murid.data(), spec.id());
		return Math.min(1.0D, 0.6D + 0.1D * level);
	}

	// ================================================================== the art

	/** The art to practise, or null when every art is at Puncak. */
	static @Nullable TechniqueSpec pilih(DiscipleData data) {
		List<TechniqueSpec> learned = data.learnedSpecs();
		JalanHidup jalan = MandiriApi.jalan(data);
		Dao jalanDao = jalan == JalanHidup.PEDANG ? Dao.SWORD : jalan == JalanHidup.PEMBURU ? Dao.BODY : null;
		TechniqueSpec best = null;
		double bestScore = -1.0D;
		for (TechniqueSpec spec : learned) {
			int level = Mastery.level(data, spec.id());
			if (level >= TechniquePower.MAX_MASTERY) {
				continue;
			}
			double score = (TechniquePower.MAX_MASTERY - level) * 1.0D;
			if (jalanDao != null && spec.dao() == jalanDao) {
				score += 3.0D;
			}
			if (spec.dao() != null && spec.dao() == data.dao()) {
				score += 1.5D;
			}
			score += Math.floorMod(spec.id().hashCode(), 7) * 0.01D;
			if (score > bestScore) {
				bestScore = score;
				best = spec;
			}
		}
		return best;
	}

	private static @Nullable TechniqueSpec spec(DiscipleData data, String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (TechniqueSpec spec : data.learnedSpecs()) {
			if (spec.id().equals(id)) {
				return spec;
			}
		}
		return null;
	}

	/** Gives up to {@code wanted} points within today's cap; returns the points allowed. */
	static int beriPoin(DiscipleEntity murid, TechniqueSpec spec, int wanted) {
		if (wanted <= 0) {
			return 0;
		}
		CompoundTag latih = DataKerja.anak(DataKerja.tulis(murid.data()), DataKerja.LATIH);
		long hari = DataKerja.hari(murid.level().getServer());
		if (latih.getLongOr("hari", -1L) != hari) {
			latih.putLong("hari", hari);
			latih.putInt("poin", 0);
		}
		int given = latih.getIntOr("poin", 0);
		int allowed = Math.max(0, Math.min(wanted, POIN_HARI - given));
		latih.putInt("poin", given + allowed);
		return allowed;
	}

	/**
	 * The catch-up event of 39.4.7 step 4 ("practice deepened an art"): three mastery points to the art the disciple would
	 * practise, a diary entry dated {@code hari}. Only for disciples that like the kata (Pendekar Pedang, Pemburu, Penjaga)
	 * or have practised before.
	 */
	static boolean kejar(DiscipleEntity murid, long hari, RandomSource random) {
		DiscipleData data = murid.data();
		JalanHidup jalan = MandiriApi.jalan(data);
		boolean suka = jalan == JalanHidup.PEDANG || jalan == JalanHidup.PEMBURU || jalan == JalanHidup.PENJAGA;
		if (!suka && random.nextInt(3) != 0) {
			return false;
		}
		TechniqueSpec spec = pilih(data);
		if (spec == null || murid.ownerId() == null) {
			return false;
		}
		int before = Mastery.level(data, spec.id());
		Mastery.addDisciple(data, spec.id(), 3);
		int after = Mastery.level(data, spec.id());
		KabarKerja.catatPada(murid.ownerId(), murid.soulId(), hari, 7, "latih", after > before ? "latih.naik" : "latih.kejar",
				after > before ? EntriHarian.Suasana.BANGGA : EntriHarian.Suasana.TENANG, after > before, spec.nameKey(),
				"kultivator.teknik.mastery." + after);
		return true;
	}
}
