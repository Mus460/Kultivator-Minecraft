package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.HuFa;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.util.SibukMurid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hu Fa sahabat, a friend guarding a disciple's breakthrough (DESIGN 39.5.3, contract C39.B2; executor JAGA_TEROBOSAN
 * 55 of C39.B3). The disciple breaking through alone invites at most two friends (a bond of Sahabat, Saudara Angkat,
 * Kekasih or Pasangan Dao, or affinity 40+; at most one realm below; free by {@code SibukMurid}); a free Tetua may stand in
 * when friends are lacking. They walk to 4-6 blocks, stand with crossed arms facing outward ({@code ACT_JAGA_TEROBOSAN})
 * and, while alive and within 8 blocks:
 * <ul>
 *   <li>every strike x0.93 per friend (a Tetua x0.90), at most two (x0.865); with the master's own Hu Fa present the
 *   friends together never go below x0.93 ("the master stays the best", the master's x0.8 is {@code HuFa}'s);</li>
 *   <li>qi gathering (Qi Refining to Foundation) +0.04 per friend; breakthrough quality +0.03 per friend.</li>
 * </ul>
 * A thin shield ring flashes on every guard when a strike falls. On success both sides gain affinity +8
 * ({@code HubunganService.adjust}, reason {@code mandiri.hufa}) and a +5 memory. Guards fight the Dao Protector with the
 * old combat behaviour (hit, they hit back). The old Hu Fa of the master ({@code disciple/path/HuFa}) is untouched.
 * <p>
 * Sumber: <i>Battle Through the Heavens</i> (Yao Lao guards Xiao Yan's breakthrough), cited in {@code disciple/path/HuFa};
 * a friend's guard is the weaker version.
 */
public final class HuFaSahabat {

	public static final double FAKTOR_SAHABAT = 0.93D;
	public static final double FAKTOR_TETUA = 0.90D;
	public static final double BATAS_DENGAN_GURU = 0.93D;
	public static final double PELUANG_PER_PENJAGA = 0.04D;
	public static final double MUTU_PER_PENJAGA = 0.03D;
	public static final int PENJAGA_MAKS = 2;
	public static final double JARAK = 8.0D;
	public static final int AFINITAS_MIN = 40;
	public static final double JARAK_UNDANG = 48.0D;

	/** The breaking disciple's soul -> its guards' souls. */
	private static final Map<UUID, List<UUID>> PENJAGA = new HashMap<>();
	/** The breaking disciple's soul -> its master (to find the guards' bodies). */
	private static final Map<UUID, UUID> PEMILIK = new HashMap<>();
	/** A guard's soul -> the friend it guards. */
	private static final Map<UUID, UUID> MENJAGA = new HashMap<>();
	/** The friend's spot, for guards that come later through the planner. */
	private static final Map<UUID, BlockPos> TITIK = new HashMap<>();

	static void init() {
		SibukMurid.register("hufa", entity -> MENJAGA.containsKey(entity.soulId()));
		DiscipleHooks.addStrikeFactor((entity, target, index) -> faktorSambaran(entity));
		DiscipleHooks.addFoundationChance(entity -> PELUANG_PER_PENJAGA * berdiri(entity).size());
		DiscipleHooks.addQualityBonus((entity, target) -> MUTU_PER_PENJAGA * berdiri(entity).size());
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onStrike(DiscipleEntity entity, int index, int count) {
				perisai(entity);
			}

			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				if (success) {
					terimaKasih(entity);
				}
				lepasSemua(entity.soulId());
			}

			@Override
			public void onCancel(DiscipleEntity entity, DiscipleBreakthrough.Kind kind) {
				lepasSemua(entity.soulId());
			}
		});
		MandiriApi.register(Kegiatan.JAGA_TEROBOSAN, new Jaga());
	}

	static void clear() {
		PENJAGA.clear();
		PEMILIK.clear();
		MENJAGA.clear();
		TITIK.clear();
	}

	// ================================================================== invitation

	/**
	 * Invites up to two guards for {@code murid}'s breakthrough at {@code spot}: friends first (strongest affinity), then a
	 * free elder. The free ones walk to their post now; returns the invited bodies.
	 */
	static List<DiscipleEntity> undang(DiscipleEntity murid, BlockPos spot) {
		UUID owner = murid.ownerId();
		List<DiscipleEntity> invited = new ArrayList<>();
		if (owner == null || !(murid.level() instanceof ServerLevel level)) {
			return invited;
		}
		lepasSemua(murid.soulId());
		DiscipleData data = murid.data();
		List<DiscipleEntity> friends = new ArrayList<>();
		List<DiscipleEntity> elders = new ArrayList<>();
		Map<UUID, Integer> affinity = new HashMap<>();
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other == murid || other.role() != DiscipleEntity.Role.DISCIPLE || other.level() != level
					|| other.distanceToSqr(murid) > JARAK_UNDANG * JARAK_UNDANG || MENJAGA.containsKey(other.soulId())
					|| PENJAGA.containsKey(other.soulId()) || other.data().breakingThrough()
					|| other.data().realm().ordinal() < data.realm().ordinal() - 1 || SibukMurid.why(other) != null) {
				continue;
			}
			Ikatan bond = SekteApi.hubungan().kind(owner, murid.soulId(), other.soulId());
			int aff = SekteApi.hubungan().affinity(owner, murid.soulId(), other.soulId());
			boolean close = bond == Ikatan.SAHABAT || bond == Ikatan.SAUDARA_ANGKAT || bond == Ikatan.KEKASIH || bond == Ikatan.PASANGAN_DAO;
			if (close || aff >= AFINITAS_MIN) {
				affinity.put(other.soulId(), aff + (close ? 100 : 0));
				friends.add(other);
			} else if (SectRanks.rank(other.data()) >= SectRanks.TETUA && bond != Ikatan.DENDAM && bond != Ikatan.SAINGAN) {
				elders.add(other);
			}
		}
		friends.sort((a, b) -> Integer.compare(affinity.getOrDefault(b.soulId(), 0), affinity.getOrDefault(a.soulId(), 0)));
		List<DiscipleEntity> chosen = new ArrayList<>(friends.subList(0, Math.min(PENJAGA_MAKS, friends.size())));
		for (int i = 0; chosen.size() < PENJAGA_MAKS && i < elders.size(); i++) {
			chosen.add(elders.get(i));
		}
		List<UUID> souls = new ArrayList<>();
		TITIK.put(murid.soulId(), spot);
		for (int i = 0; i < chosen.size(); i++) {
			DiscipleEntity guard = chosen.get(i);
			BlockPos post = TempatTumbuh.jaga(level, spot, guard, i);
			if (post == null) {
				continue;
			}
			souls.add(guard.soulId());
			MENJAGA.put(guard.soulId(), murid.soulId());
			guard.setActivity(MandiriKegiatan.ACT_JAGA_TEROBOSAN, post);
			invited.add(guard);
		}
		if (!souls.isEmpty()) {
			PENJAGA.put(murid.soulId(), souls);
			PEMILIK.put(murid.soulId(), owner);
			SekteSuara.line(murid, "hufa_minta", "greet", 200);
			SekteSuara.line(invited.get(0), "hufa_jaga", "protect", 200);
		}
		return invited;
	}

	/** The guards of {@code murid} now standing at their post (alive, within 8 blocks, in pose). */
	static List<DiscipleEntity> berdiri(DiscipleEntity murid) {
		List<UUID> souls = PENJAGA.get(murid.soulId());
		UUID owner = murid.ownerId();
		if (souls == null || souls.isEmpty() || owner == null) {
			return List.of();
		}
		List<DiscipleEntity> list = new ArrayList<>(souls.size());
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (souls.contains(other.soulId()) && other.isAlive() && other.level() == murid.level()
					&& other.activity() == MandiriKegiatan.ACT_JAGA_TEROBOSAN && other.distanceToSqr(murid) <= JARAK * JARAK) {
				list.add(other);
			}
		}
		return list;
	}

	/** How many guards were invited (standing or still walking). */
	static int diundang(UUID murid) {
		List<UUID> souls = PENJAGA.get(murid);
		return souls == null ? 0 : souls.size();
	}

	/**
	 * The strike factor the invited guards will bring that is not counted yet (the ones still walking), for the "doomed"
	 * test before the start ({@code DiscipleCultivation.breakthroughBlockerSelf}).
	 */
	static double faktorJanji(DiscipleEntity murid) {
		List<UUID> souls = PENJAGA.get(murid.soulId());
		if (souls == null) {
			return 1.0D;
		}
		List<DiscipleEntity> standing = berdiri(murid);
		double factor = 1.0D;
		int counted = standing.size();
		for (UUID soul : souls) {
			boolean stands = false;
			for (DiscipleEntity s : standing) {
				stands |= s.soulId().equals(soul);
			}
			if (!stands && counted < PENJAGA_MAKS) {
				factor *= FAKTOR_SAHABAT;
				counted++;
			}
		}
		return factor;
	}

	/** The C39.B2 strike factor of the breaking disciple. */
	private static double faktorSambaran(DiscipleEntity entity) {
		List<DiscipleEntity> standing = berdiri(entity);
		if (standing.isEmpty()) {
			return 1.0D;
		}
		double factor = 1.0D;
		for (int i = 0; i < standing.size() && i < PENJAGA_MAKS; i++) {
			factor *= SectRanks.rank(standing.get(i).data()) >= SectRanks.TETUA ? FAKTOR_TETUA : FAKTOR_SAHABAT;
		}
		return HuFa.active(entity) ? Math.max(BATAS_DENGAN_GURU, factor) : factor;
	}

	/** A thin shield flashes on every guard as the strike falls. */
	private static void perisai(DiscipleEntity murid) {
		if (!(murid.level() instanceof ServerLevel level)) {
			return;
		}
		for (DiscipleEntity guard : berdiri(murid)) {
			Vec3 at = guard.position().add(0.0D, 1.0D, 0.0D);
			Fx.ring(level, at, 0x9FE8FF, 1.1F);
			Fx.burst(level, at, 0xCFF6FF, 6, 0.6F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.5F, 1.6F);
		}
	}

	/** Success: affinity +8 both ways, a +5 memory each, the guard's diary line. */
	private static void terimaKasih(DiscipleEntity murid) {
		UUID owner = murid.ownerId();
		MinecraftServer server = murid.level().getServer();
		if (owner == null || server == null) {
			return;
		}
		Rank rank = murid.data().rank();
		for (DiscipleEntity guard : berdiri(murid)) {
			SekteApi.hubungan().adjust(owner, murid.soulId(), guard.soulId(), 8, "mandiri.hufa");
			Ingatan.remember(server, murid.data(), "mandiri.hufa_dijaga", guard.soulId(), 5, KabarTumbuh.P + "ingat.hufa_dijaga",
					guard.getName().getString());
			Ingatan.remember(server, guard.data(), "mandiri.hufa_menjaga", murid.soulId(), 5, KabarTumbuh.P + "ingat.hufa_menjaga",
					murid.getName().getString());
			KabarTumbuh.catat(guard, "hufa", "hufa.jaga_berhasil", EntriHarian.Suasana.BANGGA, false, murid.getName().getString(),
					"r:" + rank.realm().id());
		}
	}

	/** Every guard of {@code murid} goes back to what it did (the pose is cleared). */
	static void lepasSemua(UUID murid) {
		List<UUID> souls = PENJAGA.remove(murid);
		UUID owner = PEMILIK.remove(murid);
		TITIK.remove(murid);
		if (souls == null) {
			return;
		}
		for (UUID soul : souls) {
			MENJAGA.remove(soul);
		}
		if (owner == null) {
			return;
		}
		for (DiscipleEntity guard : Disciples.loaded(owner)) {
			if (souls.contains(guard.soulId()) && guard.activity() == MandiriKegiatan.ACT_JAGA_TEROBOSAN) {
				guard.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
	}

	/** Once a second per loaded guard: a guard whose friend is gone, unloaded or no longer breaking through leaves. */
	static void tick20(DiscipleEntity guard) {
		UUID friend = MENJAGA.get(guard.soulId());
		if (friend == null) {
			if (guard.activity() == MandiriKegiatan.ACT_JAGA_TEROBOSAN && MandiriApi.kegiatan(guard) != Kegiatan.JAGA_TEROBOSAN) {
				guard.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			return;
		}
		if (!TerobosanSendiri.sedang(friend)) {
			lepasSemua(friend);
		}
	}

	/** The friend a guard was asked to watch, or null. */
	static @Nullable UUID menjaga(UUID guard) {
		return MENJAGA.get(guard);
	}

	// ================================================================== executor (C39.B3)

	/** JAGA_TEROBOSAN for the planner: an invited friend that was busy at the invitation comes when it is free. */
	private static final class Jaga implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			UUID friend = MENJAGA.get(murid.soulId());
			return friend != null && TITIK.containsKey(friend) && murid.activity() != MandiriKegiatan.ACT_JAGA_TEROBOSAN;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			UUID friend = MENJAGA.get(murid.soulId());
			BlockPos spot = friend == null ? null : TITIK.get(friend);
			if (spot != null && murid.level() instanceof ServerLevel level) {
				BlockPos post = TempatTumbuh.jaga(level, spot, murid, 1);
				murid.setActivity(MandiriKegiatan.ACT_JAGA_TEROBOSAN, post == null ? spot : post);
				SekteSuara.line(murid, "hufa_jaga", "protect", 200);
			}
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return MENJAGA.containsKey(murid.soulId()) && murid.activity() == MandiriKegiatan.ACT_JAGA_TEROBOSAN;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			if (murid.activity() == MandiriKegiatan.ACT_JAGA_TEROBOSAN) {
				murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			if (batal) {
				UUID friend = MENJAGA.remove(murid.soulId());
				List<UUID> souls = friend == null ? null : PENJAGA.get(friend);
				if (souls != null) {
					souls.remove(murid.soulId());
				}
			}
		}
	}

	private HuFaSahabat() {
	}
}
