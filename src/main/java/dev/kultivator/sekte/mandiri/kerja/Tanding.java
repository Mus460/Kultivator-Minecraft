package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.technique.spec.run.Mastery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TANDING (activity 43, DESIGN 39.4.2, 39.4.8): a friendly sparring pair on the training ground. The disciple that chose
 * it ({@code a}) invites the free hall-mate the planner found ({@code Konteks.teman}, best affinity, both above 70 %
 * health, no grudge between them); the partner joins through {@code MandiriApi.mulaiKegiatan} with the same block, so
 * both finish together. Nobody is hurt: it is a bout of touches with wooden swords (the prop is the client's).
 * <p>
 * <b>The bout.</b> Both walk to marks three blocks apart and face each other. A round is the opening fist-and-palm salute
 * (2 s), six scheduled exchanges of 2 s each, attacker and defender taking turns (the attacker's vanilla swing every
 * tracker sees, then the defender either parries, a clack and sparks between them, or is touched, a softer thud), and the
 * closing salute; then a breath and the next round while the block lasts. A touch lands with
 * {@code clamp(0.5 + 0.08 x (P_att - P_def), 0.15, 0.85)}, {@code P = realm step + stars + 0.5 x best mastery level}. The
 * round goes to whoever touched more.
 * <p>
 * <b>The result</b> (rounds won): the unofficial training-ground rating of both ({@code Elo}, K 12, start 1000; never the
 * tournament's), wins/losses/draws, affinity +1 (+2 after a close bout) with the reason {@code mandiri_tanding}, a
 * memory, a diary entry for each with the partner's name and the score, the old leitai voice lines.
 */
final class Tanding implements Pelaksana {

	static final int SALAM = 40;
	static final int TUKAR = 40;
	static final int PERTUKARAN = 6;
	static final int JEDA = 60;
	static final int RONDE = SALAM + PERTUKARAN * TUKAR + SALAM / 2 + JEDA;
	/** The pair waits this long to stand at the marks before the bout is called off. */
	static final int TUNGGU_SIAP = 400;

	/** One bout of two disciples (memory only: activities 40-60 are never saved). */
	static final class Laga {
		final DiscipleEntity a;
		final DiscipleEntity b;
		final BlockPos tempatA;
		final BlockPos tempatB;
		final long dibuat;
		long siap = -1L;
		int ronde;
		int sentuhA;
		int sentuhB;
		int rondeA;
		int rondeB;
		boolean ditutup;
		boolean batal;

		Laga(DiscipleEntity a, DiscipleEntity b, BlockPos tempatA, BlockPos tempatB, long dibuat) {
			this.a = a;
			this.b = b;
			this.tempatA = tempatA;
			this.tempatB = tempatB;
			this.dibuat = dibuat;
		}

		DiscipleEntity lawan(DiscipleEntity e) {
			return e == a ? b : a;
		}
	}

	/** soul (either member) -> bout. */
	private static final Map<UUID, Laga> LAGA = new HashMap<>();

	static void kosongkan() {
		LAGA.clear();
	}

	@Override
	public boolean bisa(DiscipleEntity murid, Konteks konteks) {
		Laga laga = LAGA.get(murid.soulId());
		if (laga != null && laga.b == murid && !laga.ditutup) {
			// The invited partner joining the bout its friend set up.
			return true;
		}
		if (konteks.tempat() == null || konteks.teman() == null || !sehat(murid)) {
			return false;
		}
		DiscipleEntity teman = cari(murid, konteks.teman());
		return teman != null && sehat(teman) && bebasDiajak(teman) && murid.distanceToSqr(teman) < 32.0D * 32.0D
				&& SekteApi.hubungan().kind(murid.ownerId(), murid.soulId(), teman.soulId()) != Ikatan.DENDAM;
	}

	@Override
	public void mulai(DiscipleEntity murid, Konteks konteks) {
		Laga ada = LAGA.get(murid.soulId());
		if (ada != null && ada.b == murid && !ada.ditutup) {
			murid.setActivity(Kegiatan.TANDING.act(), ada.tempatB);
			return;
		}
		DiscipleEntity teman = konteks.teman() == null ? null : cari(murid, konteks.teman());
		BlockPos tempatA = konteks.tempat();
		if (teman == null || tempatA == null || !(murid.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos tempatB = seberang(level, tempatA);
		Laga laga = new Laga(murid, teman, tempatA, tempatB, level.getGameTime());
		LAGA.put(murid.soulId(), laga);
		LAGA.put(teman.soulId(), laga);
		murid.setActivity(Kegiatan.TANDING.act(), tempatA);
		long sisa = konteks.sisa(level.getGameTime());
		int lama = (int) Math.max(200L, Math.min(Kegiatan.TANDING.blokMaks(), sisa == Long.MAX_VALUE ? Kegiatan.TANDING.blokMaks() : sisa));
		if (!MandiriApi.mulaiKegiatan(teman, Kegiatan.TANDING, lama, tempatB, murid.soulId())) {
			laga.batal = true;
			laga.ditutup = true;
			LAGA.remove(teman.soulId());
		}
	}

	@Override
	public boolean jalan(DiscipleEntity murid, Konteks konteks) {
		Laga laga = LAGA.get(murid.soulId());
		if (laga == null || laga.batal || murid.activity() != Kegiatan.TANDING.act() || !(murid.level() instanceof ServerLevel level)) {
			return false;
		}
		DiscipleEntity lawan = laga.lawan(murid);
		if (lawan.isRemoved() || !lawan.isAlive() || lawan.activity() != Kegiatan.TANDING.act() && level.getGameTime() - laga.dibuat > 60L
				|| terganggu(murid) || terganggu(lawan)) {
			laga.batal = true;
			return false;
		}
		murid.getLookControl().setLookAt(lawan, 30.0F, 30.0F);
		if (murid != laga.a) {
			// The partner follows; the inviter runs the timeline.
			return true;
		}
		long now = level.getGameTime();
		if (laga.siap < 0L) {
			boolean diA = dekat(laga.a, laga.tempatA) && laga.a.getNavigation().isDone();
			boolean diB = dekat(laga.b, laga.tempatB) && laga.b.getNavigation().isDone();
			if (diA && diB) {
				laga.siap = now;
				KabarKerja.suara(laga.a, "leitai_salam", 4800);
			} else if (now - laga.dibuat > TUNGGU_SIAP) {
				laga.batal = true;
				return false;
			}
			return true;
		}
		long sisaBlok = konteks.sisa(now);
		long t = now - laga.siap;
		int ronde = (int) (t / RONDE);
		int dalam = (int) (t % RONDE);
		if (ronde > laga.ronde) {
			tutupRonde(laga);
			laga.ronde = ronde;
		}
		// No new round when it could not finish before the block ends: both stand and breathe.
		boolean rondeMuat = sisaBlok == Long.MAX_VALUE || sisaBlok >= RONDE - dalam;
		if (!rondeMuat && dalam < SALAM) {
			return true;
		}
		langkah(laga, level, dalam);
		return true;
	}

	/** One step (every second) of the round's timeline at {@code dalam} ticks into the round. */
	private static void langkah(Laga laga, ServerLevel level, int dalam) {
		if (dalam < SALAM) {
			if (dalam < 20) {
				salam(laga, level);
			}
			return;
		}
		int sesudahSalam = dalam - SALAM;
		if (sesudahSalam < PERTUKARAN * TUKAR) {
			int ke = sesudahSalam / TUKAR;
			boolean serang = sesudahSalam % TUKAR < 20;
			DiscipleEntity penyerang = ke % 2 == 0 ? laga.a : laga.b;
			DiscipleEntity bertahan = laga.lawan(penyerang);
			if (serang) {
				penyerang.swing(InteractionHand.MAIN_HAND, new SwingAnimation(ke % 3 == 2 ? SwingAnimationType.STAB : SwingAnimationType.WHACK, 9), false);
				level.playSound(null, penyerang.getX(), penyerang.getY(), penyerang.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL,
						0.35F, 1.3F + penyerang.getRandom().nextFloat() * 0.2F);
			} else {
				Vec3 mid = penyerang.position().add(bertahan.position()).scale(0.5D).add(0.0D, 1.2D, 0.0D);
				double peluang = Math.max(0.15D, Math.min(0.85D, 0.5D + 0.08D * (kuasa(penyerang.data()) - kuasa(bertahan.data()))));
				if (penyerang.getRandom().nextDouble() < peluang) {
					// A touch: a soft thud on the shoulder, the defender steps back a little.
					if (penyerang == laga.a) {
						laga.sentuhA++;
					} else {
						laga.sentuhB++;
					}
					level.sendParticles(ParticleTypes.CRIT, bertahan.getX(), bertahan.getY() + 1.3D, bertahan.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.1D);
					level.playSound(null, bertahan.getX(), bertahan.getY(), bertahan.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL,
							0.5F, 0.8F);
					Vec3 back = bertahan.position().subtract(penyerang.position()).normalize().scale(0.15D);
					bertahan.push(back.x, 0.05D, back.z);
				} else {
					// A parry: the wooden blades clack, sparks between them.
					bertahan.swing(InteractionHand.MAIN_HAND, new SwingAnimation(SwingAnimationType.WHACK, 6), false);
					level.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 5, 0.15D, 0.15D, 0.15D, 0.15D);
					level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.7F,
							1.2F + bertahan.getRandom().nextFloat() * 0.3F);
				}
			}
			return;
		}
		if (sesudahSalam < PERTUKARAN * TUKAR + 20) {
			salam(laga, level);
		}
	}

	/** The fist-and-palm salute: both look at each other, a soft bell-like tap. */
	private static void salam(Laga laga, ServerLevel level) {
		laga.a.getLookControl().setLookAt(laga.b, 30.0F, 30.0F);
		laga.b.getLookControl().setLookAt(laga.a, 30.0F, 30.0F);
		Vec3 mid = laga.a.position().add(laga.b.position()).scale(0.5D);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.NEUTRAL, 0.4F, 0.7F);
	}

	private static void tutupRonde(Laga laga) {
		if (laga.sentuhA == 0 && laga.sentuhB == 0) {
			return;
		}
		if (laga.sentuhA > laga.sentuhB) {
			laga.rondeA++;
		} else if (laga.sentuhB > laga.sentuhA) {
			laga.rondeB++;
		}
		laga.sentuhA = 0;
		laga.sentuhB = 0;
	}

	@Override
	public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		if (murid.activity() == Kegiatan.TANDING.act()) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		Laga laga = LAGA.remove(murid.soulId());
		if (laga == null) {
			return;
		}
		DiscipleEntity lawan = laga.lawan(murid);
		if (batal) {
			laga.batal = true;
		}
		if (!laga.ditutup) {
			laga.ditutup = true;
			tutupRonde(laga);
			if (!laga.batal && laga.rondeA + laga.rondeB > 0) {
				hasil(laga);
			}
			// The other one ends too (its block ends at the same time; earlier when this one was interrupted).
			if (laga.batal && lawan.isAlive() && MandiriApi.kegiatan(lawan) == Kegiatan.TANDING) {
				MandiriApi.akhiriKegiatan(lawan, true);
			}
		}
	}

	/** Ratings, records, affinity, memories, diaries and the voice lines of a finished bout. */
	private static void hasil(Laga laga) {
		DiscipleEntity a = laga.a;
		DiscipleEntity b = laga.b;
		double skorA = laga.rondeA > laga.rondeB ? 1.0D : laga.rondeA < laga.rondeB ? 0.0D : 0.5D;
		int eloA = DataKerja.elo(a.data());
		int eloB = DataKerja.elo(b.data());
		double expectA = 1.0D / (1.0D + Math.pow(10.0D, (eloB - eloA) / 400.0D));
		int delta = (int) Math.round(DataKerja.ELO_K * (skorA - expectA));
		catatRekor(a, eloA + delta, skorA, b);
		catatRekor(b, eloB - delta, 1.0D - skorA, a);
		boolean ketat = Math.abs(laga.rondeA - laga.rondeB) <= 1;
		UUID owner = a.ownerId();
		if (owner != null) {
			SekteApi.hubungan().adjust(owner, a.soulId(), b.soulId(), ketat ? 2 : 1, "mandiri_tanding");
		}
		String skor = laga.rondeA + "-" + laga.rondeB;
		String skorBalik = laga.rondeB + "-" + laga.rondeA;
		tulisCerita(a, b, skorA, skor);
		tulisCerita(b, a, 1.0D - skorA, skorBalik);
		if (skorA != 0.5D) {
			DiscipleEntity menang = skorA > 0.5D ? a : b;
			DiscipleEntity kalah = menang == a ? b : a;
			KabarKerja.suara(menang, "leitai_juara", 4800);
			KabarKerja.suara(kalah, "leitai_kalah", 4800);
			KabarKerja.kabar(menang, "tanding", false, menang.getName().getString(), kalah.getName().getString(),
					menang == a ? skor : skorBalik);
		}
	}

	private static void catatRekor(DiscipleEntity e, int elo, double skor, DiscipleEntity lawan) {
		CompoundTag own = DataKerja.tulis(e.data());
		own.putInt(DataKerja.ELO, Math.max(100, elo));
		String key = skor > 0.5D ? DataKerja.MENANG : skor < 0.5D ? DataKerja.KALAH : DataKerja.SERI;
		own.putInt(key, own.getIntOr(key, 0) + 1);
		own.putString(DataKerja.LAWAN, lawan.soulId().toString());
	}

	private static void tulisCerita(DiscipleEntity e, DiscipleEntity lawan, double skor, String angka) {
		String inti = skor > 0.5D ? "tanding.menang" : skor < 0.5D ? "tanding.kalah" : "tanding.seri";
		EntriHarian.Suasana suasana = skor > 0.5D ? EntriHarian.Suasana.SENANG : skor < 0.5D ? EntriHarian.Suasana.KESAL
				: EntriHarian.Suasana.TENANG;
		KabarKerja.catat(e, "tanding", inti, suasana, false, lawan.getName().getString(), angka, String.valueOf(DataKerja.elo(e.data())));
		Ingatan.remember(e.level().getServer(), e.data(), "mandiri.tanding", lawan.soulId(), skor > 0.5D ? 2 : 1,
				DataKerja.P + "ingat." + (skor > 0.5D ? "tanding_menang" : skor < 0.5D ? "tanding_kalah" : "tanding_seri"),
				lawan.getName().getString());
	}

	// ================================================================== helpers

	/** The power of a sparring disciple: realm step, stars and half its best mastery level. */
	static double kuasa(DiscipleData data) {
		int best = 0;
		for (var spec : data.learnedSpecs()) {
			best = Math.max(best, Mastery.level(data, spec.id()));
		}
		return DisciplePaths.power(data) + 0.5D * best;
	}

	private static boolean sehat(DiscipleEntity e) {
		return e.getHealth() > e.getMaxHealth() * 0.7F && e.getTarget() == null && !e.data().breakingThrough() && !e.data().deviating()
				&& !DuelRules.inDuel(e);
	}

	/** Idle, or resting, eating, playing or contemplating in the planner (it may be called away for the bout). */
	static boolean bebasDiajak(DiscipleEntity e) {
		if (e.role() != DiscipleEntity.Role.DISCIPLE || e.isReleasing()) {
			return false;
		}
		Kegiatan running = MandiriApi.kegiatan(e);
		if (running == null) {
			return e.activity() == DiscipleEntity.ACTIVITY_NONE && dev.kultivator.util.SibukMurid.why(e) == null;
		}
		return running == Kegiatan.ISTIRAHAT || running == Kegiatan.MAKAN || running == Kegiatan.BERMAIN || running == Kegiatan.MERENUNG
				|| running == Kegiatan.MEDITASI;
	}

	private static boolean terganggu(DiscipleEntity e) {
		return e.getTarget() != null || e.hurtTime > 0 && e.getLastHurtByMob() != null || e.data().breakingThrough();
	}

	private static boolean dekat(DiscipleEntity e, BlockPos spot) {
		return e.distanceToSqr(Vec3.atBottomCenterOf(spot)) <= 2.5D * 2.5D;
	}

	/** The partner's mark three blocks from the inviter's, on the ground of a loaded column (east, west, south, north). */
	private static BlockPos seberang(ServerLevel level, BlockPos a) {
		int[][] offsets = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
		for (int[] o : offsets) {
			BlockPos column = a.offset(o[0], 0, o[1]);
			if (!level.isLoaded(column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			if (Math.abs(y - a.getY()) <= 1) {
				return new BlockPos(column.getX(), y, column.getZ());
			}
		}
		return a.offset(3, 0, 0);
	}

	static @Nullable DiscipleEntity cari(DiscipleEntity murid, UUID soul) {
		UUID owner = murid.ownerId();
		if (owner == null || soul == null) {
			return null;
		}
		for (DiscipleEntity e : Disciples.loaded(owner)) {
			if (e != murid && soul.equals(e.soulId()) && e.level() == murid.level() && e.isAlive()) {
				return e;
			}
		}
		return null;
	}
}
