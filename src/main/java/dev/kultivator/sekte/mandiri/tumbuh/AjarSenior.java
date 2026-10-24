package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.IzinKegiatan;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.util.SibukMurid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mengajar, a senior disciple teaching a junior (DESIGN 39.5.5, executor MENGAJAR 44 of C39.B3). A Murid Inti or higher
 * stands with its hands behind its back ({@code ACT_MENGAJAR}); the chosen junior is fetched through the planner
 * ({@code MandiriApi.mulaiKegiatan(junior, MEDITASI, ..., seat, senior)}) and sits cross-legged two blocks in front of
 * it, so the old meditation XP runs while it listens (a BELAJAR block started with the senior as companion takes the
 * same seat through {@link BelajarSendiri}, way "listening"). The lesson has three beats:
 * <ol>
 *   <li><b>Gathering</b> (at most 20 s): the junior walks to its seat; without it the lesson is called off.</li>
 *   <li><b>Explaining</b> (15 s): every three seconds a thin thread of qi runs from the senior's raised hand to the
 *   junior's brow, the junior's rings answer in the art's colour.</li>
 *   <li><b>Transmission</b>: 30 % of the senior's qi (the old {@code ELDER_TEACH_QI}), the junior learns one art the senior
 *   knows ({@code DiscipleCultivation.learnSelf}) at the senior's mastery level - 2 ({@code Mastery}); a beam, a burst,
 *   the enchanting chime; both diaries, a memory each, affinity +3; the master hears it ({@code Pemberitahuan.TEKNIK}).
 *   A short practice follows, then both stand up.</li>
 * </ol>
 * Gates: every old one through {@code DiscipleCultivation.teachBlockerSelf} (teachable, realm, Dao, stars, the
 * {@code teachBlocker} hooks asked with no master) and the free technique slots of {@code SectRanks}; never a Dewa art.
 * One lesson per senior per day, one art per junior per three days; never a junior it hates (Dendam) or the master's
 * "Belajar" permission off. The junior is chosen by affinity, the art by the junior's own Dao first, then the highest
 * grade it may take.
 * <p>
 * Sumber: <i>A Record of a Mortal's Journey</i> (senior brothers of the Yellow Maple Valley passing their arts down),
 * <i>Coiling Dragon</i> (Doehring Cowart teaching from beside the junior); the old Tetua lesson of
 * {@code sect/hall/SectRanks} is the master-led version.
 */
public final class AjarSenior {

	/** Murid Inti and up (39.5.5). */
	public static final int JENJANG_MIN = SectRanks.INTI;
	/** The share of the senior's qi one lesson costs (the old {@code SectRanks.ELDER_TEACH_QI}). */
	public static final double BIAYA_QI = SectRanks.ELDER_TEACH_QI;
	/** Juniors are looked for this far from the senior. */
	public static final double JARAK_CARI = 24.0D;
	/** The junior must sit within this distance at the transmission (39.5.5: six blocks). */
	public static final double JARAK_AJAR = 6.0D;
	public static final int KUMPUL_MAKS = 400;
	public static final int JELASKAN = 300;
	public static final int LATIH = 200;
	public static final int JEDA_MURID_HARI = 3;
	private static final String KEY = "ajar";

	enum Tahap { KUMPUL, JELASKAN, LATIH }

	/** A running lesson (session data: a reload ends it, the junior keeps nothing half-learned). */
	private static final class Pelajaran {
		final UUID owner;
		final UUID junior;
		final String spec;
		final BlockPos tempat;
		Tahap tahap = Tahap.KUMPUL;
		long sejak;

		Pelajaran(UUID owner, UUID junior, String spec, BlockPos tempat, long now) {
			this.owner = owner;
			this.junior = junior;
			this.spec = spec;
			this.tempat = tempat;
			this.sejak = now;
		}
	}

	private record Pilihan(DiscipleEntity junior, TechniqueSpec spec) {
	}

	/** senior soul -> its lesson. */
	private static final Map<UUID, Pelajaran> PELAJARAN = new HashMap<>();
	/** junior soul -> the senior teaching it. */
	private static final Map<UUID, UUID> GURU = new HashMap<>();

	static void init() {
		SibukMurid.register("mengajar", entity -> PELAJARAN.containsKey(entity.soulId()));
		MandiriApi.register(Kegiatan.MENGAJAR, new Pelaksana() {
			@Override
			public boolean bisa(DiscipleEntity murid, Konteks konteks) {
				return boleh(murid) && pilih(murid) != null;
			}

			@Override
			public void mulai(DiscipleEntity murid, Konteks konteks) {
				mulaiDi(murid, konteks.tempat());
			}

			@Override
			public boolean jalan(DiscipleEntity murid, Konteks konteks) {
				return PELAJARAN.containsKey(murid.soulId());
			}

			@Override
			public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
				akhiri(murid);
			}
		});
	}

	static void clear() {
		PELAJARAN.clear();
		GURU.clear();
	}

	// ================================================================== for BelajarSendiri (the listening junior)

	/** True when {@code guru} holds a lesson for {@code junior} right now. */
	static boolean adaPelajaran(UUID guru, UUID junior) {
		Pelajaran lesson = PELAJARAN.get(guru);
		return lesson != null && lesson.junior.equals(junior);
	}

	/** The junior takes its seat in front of {@code guru}; false when there is no lesson for it. */
	static boolean duduk(DiscipleEntity junior, UUID guru) {
		Pelajaran lesson = PELAJARAN.get(guru);
		if (lesson == null || !lesson.junior.equals(junior.soulId()) || !(junior.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos seat = TempatTumbuh.depan(level, lesson.tempat, Math.floorMod(junior.soulId().hashCode(), 6));
		junior.setActivity(MandiriKegiatan.ACT_MEDITASI_MANDIRI, seat == null ? lesson.tempat.south(2) : seat);
		GURU.put(junior.soulId(), guru);
		return true;
	}

	/** The junior's block goes on while its teacher's lesson does. */
	static boolean mendengar(DiscipleEntity junior) {
		UUID guru = GURU.get(junior.soulId());
		return guru != null && adaPelajaran(guru, junior.soulId()) && junior.activity() == MandiriKegiatan.ACT_MEDITASI_MANDIRI;
	}

	/** The junior stands up (its BELAJAR block ended). */
	static void bangkit(DiscipleEntity junior) {
		GURU.remove(junior.soulId());
		if (junior.activity() == MandiriKegiatan.ACT_MEDITASI_MANDIRI && MandiriApi.kegiatan(junior) != Kegiatan.MEDITASI) {
			junior.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** The senior teaching {@code soul} now, or null (the info line). */
	static @Nullable UUID guruDari(UUID soul) {
		return GURU.get(soul);
	}

	// ================================================================== choosing

	/** The senior may teach today: Inti or higher, its qi, the day's lesson not given, not busy with a breakthrough. */
	static boolean boleh(DiscipleEntity senior) {
		DiscipleData data = senior.data();
		if (!MandiriApi.aktif() || senior.ownerId() == null || SectRanks.rank(data) < JENJANG_MIN || data.breakingThrough()
				|| data.deviating() || senior.getTarget() != null || PELAJARAN.containsKey(senior.soulId())
				|| TerobosanSendiri.sedang(senior.soulId())) {
			return false;
		}
		long today = DataTumbuh.hari(senior.level().getServer());
		if (DataTumbuh.baca(data).getCompoundOrEmpty(KEY).getLongOr("hari", -1L) == today) {
			return false;
		}
		return data.qi() + 1.0E-6D >= biaya(data);
	}

	static double biaya(DiscipleData data) {
		return DiscipleCultivation.maxQi(data) * BIAYA_QI;
	}

	/** The best junior and art for {@code senior} now, or null. */
	private static @Nullable Pilihan pilih(DiscipleEntity senior) {
		UUID owner = senior.ownerId();
		if (owner == null) {
			return null;
		}
		DiscipleData data = senior.data();
		int rank = SectRanks.rank(data);
		long today = DataTumbuh.hari(senior.level().getServer());
		Pilihan best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (DiscipleEntity junior : Disciples.loaded(owner)) {
			if (junior == senior || junior.role() != DiscipleEntity.Role.DISCIPLE || junior.level() != senior.level()
					|| junior.distanceToSqr(senior) > JARAK_CARI * JARAK_CARI || SectRanks.rank(junior.data()) >= rank
					|| junior.data().breakingThrough() || SectRanks.slotsFull(junior.data()) || GURU.containsKey(junior.soulId())
					|| PELAJARAN.containsKey(junior.soulId()) || !MandiriApi.kebijakan(junior).boleh(IzinKegiatan.BELAJAR)) {
				continue;
			}
			long last = DataTumbuh.baca(junior.data()).getCompoundOrEmpty(KEY).getLongOr("diajar", -100L);
			if (today < last + JEDA_MURID_HARI) {
				continue;
			}
			if (SibukMurid.why(junior) != null) {
				continue;
			}
			Ikatan bond = SekteApi.hubungan().kind(owner, senior.soulId(), junior.soulId());
			if (bond == Ikatan.DENDAM) {
				continue;
			}
			TechniqueSpec spec = seni(senior, junior);
			if (spec == null) {
				continue;
			}
			double score = SekteApi.hubungan().affinity(owner, senior.soulId(), junior.soulId()) / 20.0D
					+ (bond == Ikatan.SAUDARA_ANGKAT || bond == Ikatan.SAHABAT ? 2.0D : 0.0D)
					+ (SekteApi.hati().has(data, Sifat.PENYAYANG) ? 1.0D : 0.0D) * (junior.data().realm().ordinal() < data.realm().ordinal() ? 1.0D : 0.0D)
					+ spec.grade().tier() * 0.5D - Math.sqrt(junior.distanceToSqr(senior)) / 12.0D;
			if (score > bestScore) {
				bestScore = score;
				best = new Pilihan(junior, spec);
			}
		}
		return best;
	}

	/** The art {@code senior} would pass to {@code junior}: the junior's own Dao first, then the highest grade; never Dewa. */
	private static @Nullable TechniqueSpec seni(DiscipleEntity senior, DiscipleEntity junior) {
		Dao dao = junior.data().dao();
		TechniqueSpec best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (TechniqueSpec spec : senior.data().learnedSpecs()) {
			if (spec.grade() == TechniqueGrade.DEWA || spec.dewa() || spec.has(TechniqueSpec.Flag.DISCIPLE_ONLY)
					|| DiscipleCultivation.teachBlockerSelf(junior, spec) != null) {
				continue;
			}
			double score = spec.grade().tier() + (dao != null && spec.dao() == dao ? 3.0D : 0.0D) + (spec.dao() == null ? 0.5D : 0.0D);
			if (score > bestScore) {
				bestScore = score;
				best = spec;
			}
		}
		return best;
	}

	// ================================================================== the lesson

	/** Starts a lesson near {@code saran}; false when no junior came. */
	static boolean mulaiDi(DiscipleEntity senior, @Nullable BlockPos saran) {
		UUID owner = senior.ownerId();
		if (owner == null || !(senior.level() instanceof ServerLevel level) || !boleh(senior)) {
			return false;
		}
		Pilihan choice = pilih(senior);
		if (choice == null) {
			return false;
		}
		BlockPos spot = TempatTumbuh.stand(level, saran != null && saran.distSqr(senior.blockPosition()) < 32 * 32 ? saran : senior.blockPosition());
		if (spot == null) {
			spot = senior.blockPosition();
		}
		long now = level.getGameTime();
		Pelajaran lesson = new Pelajaran(owner, choice.junior().soulId(), choice.spec().id(), spot, now);
		PELAJARAN.put(senior.soulId(), lesson);
		senior.setActivity(MandiriKegiatan.ACT_MENGAJAR, spot);
		int length = KUMPUL_MAKS + JELASKAN + LATIH + 200;
		// The junior listens in the planner's own seated meditation (56) on the seat in front of the teacher: the block's
		// body activity is the catalogue's, so the planner keeps it (BELAJAR's catalogue pose is A's rack pose 13).
		DiscipleEntity junior = choice.junior();
		BlockPos seat = TempatTumbuh.depan(level, spot, Math.floorMod(junior.soulId().hashCode(), 6));
		if (seat == null) {
			seat = spot.south(2);
		}
		GURU.put(junior.soulId(), senior.soulId());
		if (!MandiriApi.mulaiKegiatan(junior, Kegiatan.MEDITASI, length, seat, senior.soulId())) {
			GURU.remove(junior.soulId());
			PELAJARAN.remove(senior.soulId());
			senior.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			return false;
		}
		SekteSuara.line(senior, "ajar_panggil", "greet", 200);
		return true;
	}

	/** Once a second per loaded senior while a lesson runs. */
	static void tick20(DiscipleEntity senior) {
		Pelajaran lesson = PELAJARAN.get(senior.soulId());
		if (lesson == null || !(senior.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		DiscipleEntity junior = cari(lesson.owner, lesson.junior);
		if (junior == null || senior.getTarget() != null || senior.data().breakingThrough()) {
			batal(senior, lesson, junior);
			return;
		}
		TechniqueSpec spec = TechniqueRegistry.byId(lesson.spec);
		if (spec == null) {
			batal(senior, lesson, junior);
			return;
		}
		int color = warna(spec);
		switch (lesson.tahap) {
			case KUMPUL -> {
				boolean seated = mendengar(junior) && junior.activitySpot() != null
						&& junior.distanceToSqr(Vec3.atBottomCenterOf(junior.activitySpot())) <= 2.25D;
				if (seated) {
					lesson.tahap = Tahap.JELASKAN;
					lesson.sejak = now;
					SekteSuara.line(senior, "ajar_mulai", "technique", 0);
				} else if (now - lesson.sejak > KUMPUL_MAKS) {
					batal(senior, lesson, junior);
				}
			}
			case JELASKAN -> {
				if (!mendengar(junior)) {
					batal(senior, lesson, junior);
					return;
				}
				senior.getLookControl().setLookAt(junior);
				if ((now - lesson.sejak) % 60L < 20L) {
					Vec3 hand = senior.position().add(0.0D, 1.5D, 0.0D);
					Vec3 brow = junior.position().add(0.0D, 1.1D, 0.0D);
					Fx.beam(level, hand, brow, color, 0.05F);
					Fx.ring(level, junior.position().add(0.0D, 0.15D, 0.0D), color, 0.8F);
				}
				if (now - lesson.sejak >= JELASKAN) {
					if (junior.distanceToSqr(senior) > JARAK_AJAR * JARAK_AJAR || !wariskan(senior, junior, spec, level)) {
						batal(senior, lesson, junior);
						return;
					}
					lesson.tahap = Tahap.LATIH;
					lesson.sejak = now;
				}
			}
			case LATIH -> {
				if ((now - lesson.sejak) % 80L < 20L) {
					Fx.burst(level, junior.position().add(0.0D, 1.0D, 0.0D), color, 4, 0.6F);
				}
				if (now - lesson.sejak >= LATIH || !mendengar(junior)) {
					PELAJARAN.remove(senior.soulId());
					lepasMurid(junior, false);
					if (senior.activity() == MandiriKegiatan.ACT_MENGAJAR) {
						senior.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
					}
				}
			}
		}
	}

	/** The transmission: qi, the art, the mastery, FX, diaries, memories, affinity, the master. */
	private static boolean wariskan(DiscipleEntity senior, DiscipleEntity junior, TechniqueSpec spec, ServerLevel level) {
		DiscipleData data = senior.data();
		double cost = biaya(data);
		if (data.qi() + 1.0E-6D < cost || DiscipleCultivation.teachBlockerSelf(junior, spec) != null || SectRanks.slotsFull(junior.data())) {
			return false;
		}
		int mastery = Math.max(0, Mastery.level(data, spec.id()) - 2);
		if (!DiscipleCultivation.learnSelf(junior, spec, mastery)) {
			return false;
		}
		data.setQi(Math.max(0.0D, data.qi() - cost));
		senior.refreshSynced();
		MinecraftServer server = level.getServer();
		long today = DataTumbuh.hari(server);
		DataTumbuh.sub(data, KEY).putLong("hari", today);
		CompoundTag mine = DataTumbuh.sub(junior.data(), KEY);
		mine.putLong("diajar", today);
		mine.putString("oleh", senior.getName().getString());
		DataTumbuh.tambahStatistik(data, DataTumbuh.S_AJAR, 1);
		DataTumbuh.tambahStatistik(junior.data(), DataTumbuh.S_TEKNIK, 1);
		int color = warna(spec);
		Vec3 from = senior.position().add(0.0D, 1.5D, 0.0D);
		Vec3 to = junior.position().add(0.0D, 1.1D, 0.0D);
		Fx.beam(level, from, to, color, 0.12F);
		Fx.burst(level, to, color, 18, 0.9F);
		Fx.ring(level, junior.position().add(0.0D, 0.1D, 0.0D), color, 1.6F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.8F, 1.2F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.4F);
		UUID owner = senior.ownerId();
		if (owner != null) {
			SekteApi.hubungan().adjust(owner, senior.soulId(), junior.soulId(), 3, "mandiri.ajar");
		}
		Ingatan.remember(server, junior.data(), "mandiri.diajar", senior.soulId(), 4, KabarTumbuh.P + "ingat.diajar",
				senior.getName().getString());
		Ingatan.remember(server, data, "mandiri.mengajar", junior.soulId(), 3, KabarTumbuh.P + "ingat.mengajar",
				junior.getName().getString());
		KabarTumbuh.catat(junior, "ajar", "ajar.diajar", EntriHarian.Suasana.SENANG, true, senior.getName().getString(), "t:" + spec.id());
		KabarTumbuh.catat(senior, "ajar", "ajar.mengajar", EntriHarian.Suasana.BANGGA, false, junior.getName().getString(), "t:" + spec.id());
		KabarTumbuh.kirim(senior, Kebijakan.Pemberitahuan.TEKNIK, KabarTumbuh.baris("ajar.selesai", 0x9FF5DC, KabarTumbuh.nama(senior),
				KabarTumbuh.nama(junior), spec.displayName().copy().withColor(spec.grade().ribbon())));
		SekteSuara.line(junior, "ajar_terima", "thanks", 0);
		return true;
	}

	private static int warna(TechniqueSpec spec) {
		return spec.dao() == null ? 0xFFD54A : spec.dao().color();
	}

	private static void batal(DiscipleEntity senior, Pelajaran lesson, @Nullable DiscipleEntity junior) {
		PELAJARAN.remove(senior.soulId());
		if (senior.activity() == MandiriKegiatan.ACT_MENGAJAR) {
			senior.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		if (junior != null) {
			lepasMurid(junior, true);
		} else {
			GURU.remove(lesson.junior);
		}
	}

	/** The listening junior stands up: its meditation (or study) block of this lesson ends. */
	private static void lepasMurid(DiscipleEntity junior, boolean batal) {
		if (GURU.remove(junior.soulId()) == null) {
			return;
		}
		Kegiatan running = MandiriApi.kegiatan(junior);
		if (running == Kegiatan.MEDITASI || running == Kegiatan.BELAJAR) {
			MandiriApi.akhiriKegiatan(junior, batal);
		}
	}

	/** The senior's block ended: the lesson (if still running) is called off, the junior stands up. */
	static void akhiri(DiscipleEntity senior) {
		Pelajaran lesson = PELAJARAN.get(senior.soulId());
		if (lesson != null) {
			batal(senior, lesson, cari(lesson.owner, lesson.junior));
		} else if (senior.activity() == MandiriKegiatan.ACT_MENGAJAR) {
			senior.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** {@code uji ... tumbuh ajar <senior>}: the day's rule skipped, a lesson now when a junior and an art exist. */
	static boolean paksa(DiscipleEntity senior) {
		DataTumbuh.sub(senior.data(), KEY).remove("hari");
		if (!boleh(senior) || pilih(senior) == null) {
			return false;
		}
		if (MandiriApi.mulaiKegiatan(senior, Kegiatan.MENGAJAR, KUMPUL_MAKS + JELASKAN + LATIH + 400, senior.blockPosition(), null)) {
			return true;
		}
		return mulaiDi(senior, senior.blockPosition());
	}

	private static @Nullable DiscipleEntity cari(UUID owner, UUID soul) {
		for (DiscipleEntity e : Disciples.loaded(owner)) {
			if (e.soulId().equals(soul)) {
				return e;
			}
		}
		return null;
	}

	private AjarSenior() {
	}
}
