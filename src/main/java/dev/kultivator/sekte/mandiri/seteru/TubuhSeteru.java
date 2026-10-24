package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The bodies 39F shows (DESIGN 39.F.1, 39.F.8): members of the other sects from the roster (D's sect body, role RIVAL,
 * never saved, untouchable except by a duel partner) and, when no town body of 39C shows our disciple, a stand-in of our
 * disciple for a scene (also RIVAL, a read-only copy of its away tablet). At most {@code tubuhMaks} (4) on the server,
 * patrols included; AI every 4 ticks (patrol walk, looking at the nearest player); a body leaves in a puff of cloud
 * when its time is up or no player is within 160 blocks. Never a death, never loot.
 */
final class TubuhSeteru {

	/** Why a body is out (lower ones give way first when the cap is full). */
	enum Peran { TEMU, SAKSI, PENGGANTI, PATROLI, DUEL }

	static final double JARAK_HILANG = 160.0D;

	static final class Tubuh {
		final DiscipleEntity body;
		final Peran peran;
		final @Nullable String tokoh;
		final @Nullable UUID owner;
		final @Nullable String seteru;
		long habis;
		@Nullable BlockPos a;
		@Nullable BlockPos b;
		boolean keB;
		long ejekBerikut;

		Tubuh(DiscipleEntity body, Peran peran, @Nullable String tokoh, @Nullable UUID owner, @Nullable String seteru, long habis) {
			this.body = body;
			this.peran = peran;
			this.tokoh = tokoh;
			this.owner = owner;
			this.seteru = seteru;
			this.habis = habis;
		}
	}

	private static final List<Tubuh> AKTIF = new ArrayList<>();

	static int jumlah() {
		AKTIF.removeIf(t -> t.body.isRemoved());
		return AKTIF.size();
	}

	static List<Tubuh> semua() {
		AKTIF.removeIf(t -> t.body.isRemoved());
		return List.copyOf(AKTIF);
	}

	/** The body showing roster member {@code tokohId}, or null. */
	static @Nullable Tubuh dari(String tokohId) {
		for (Tubuh t : AKTIF) {
			if (!t.body.isRemoved() && tokohId.equals(t.tokoh)) {
				return t;
			}
		}
		return null;
	}

	/** Makes room for a body of {@code peran}: false when every live body is more important. */
	private static boolean ruang(Peran peran) {
		int cap = SeteruConfig.get().tubuhMaks;
		if (jumlah() < cap) {
			return true;
		}
		Tubuh weakest = null;
		for (Tubuh t : AKTIF) {
			if (DuelRules.inDuel(t.body)) {
				continue;
			}
			if (t.peran.ordinal() < peran.ordinal() && (weakest == null || t.peran.ordinal() < weakest.peran.ordinal())) {
				weakest = t;
			}
		}
		if (weakest == null) {
			return false;
		}
		hilang(weakest);
		return true;
	}

	/**
	 * Shows roster member {@code tokoh} at {@code pos} for {@code lamaTick} ticks (or reuses its body if it is out).
	 * Null when the cap is full of more important bodies or D does not know the sect.
	 */
	static @Nullable DiscipleEntity munculkan(ServerLevel level, BlockPos pos, TokohSekte tokoh, Peran peran, @Nullable UUID owner,
			@Nullable String seteru, int lamaTick) {
		Tubuh have = dari(tokoh.id);
		long now = level.getGameTime();
		if (have != null) {
			have.habis = Math.max(have.habis, now + lamaTick);
			return have.body;
		}
		if (!ruang(peran)) {
			return null;
		}
		long today = Ingatan.today(level.getServer());
		int step = tokoh.langkah(today, SekteApi.diplomasi().tier(tokoh.sekte));
		BlockPos ground = tanah(level, pos);
		DiscipleEntity body = DiplomasiMandiri.tubuh(level, Vec3.atBottomCenterOf(ground), tokoh.sekte, step, tokoh.slot, tokoh.namaTampil(),
				ground, false);
		if (body == null) {
			return null;
		}
		SosokApi.setSosok(body, new Sosok(tokoh.kelamin(), tokoh.peran >= TokohSekte.PERAN_KAKAK ? Sosok.DEWASA : Sosok.MUDA,
				tokoh.postur, tokoh.benih & Sosok.BENIH_MASK));
		AKTIF.add(new Tubuh(body, peran, tokoh.id, owner, seteru, now + lamaTick));
		return body;
	}

	/**
	 * A stand-in of our disciple for a scene where no town body shows it (a read-only copy of its away tablet, role
	 * RIVAL so a duel may hurt it, never saved). Null when its tablet is not known or the cap is full.
	 */
	static @Nullable DiscipleEntity pengganti(ServerLevel level, BlockPos pos, MuridInfo murid, UUID owner, String seteru, int lamaTick) {
		if (murid.data() == null || !ruang(Peran.PENGGANTI)) {
			return null;
		}
		CompoundTag stored = murid.stored();
		int skin = stored == null ? 0 : stored.getIntOr("skin", 0);
		boolean slim = stored != null && stored.getBooleanOr("slim", false);
		Sosok sosok = stored == null ? null : Sosok.unpack(stored.getIntOr("sosok", Sosok.KOSONG));
		int gaya = stored == null ? 0 : stored.getIntOr("gaya", 0);
		BlockPos ground = tanah(level, pos);
		DiscipleData copy = murid.data().copy();
		DiscipleEntity body = Disciples.spawnUnowned(level, Vec3.atBottomCenterOf(ground), copy, murid.nama(), skin, slim, sosok, gaya,
				DiscipleEntity.Role.RIVAL, ground);
		if (body == null) {
			return null;
		}
		if (sosok != null) {
			SosokApi.setSosok(body, sosok);
		}
		SosokApi.setBusanaKhusus(body, 1);
		AKTIF.add(new Tubuh(body, Peran.PENGGANTI, null, owner, seteru, level.getGameTime() + lamaTick));
		return body;
	}

	/** A patrol body walking between {@code a} and {@code b}. */
	static @Nullable DiscipleEntity patroli(ServerLevel level, BlockPos a, BlockPos b, TokohSekte tokoh, UUID owner, String seteru) {
		DiscipleEntity body = munculkan(level, a, tokoh, Peran.PATROLI, owner, seteru, 20 * 60 * 10);
		if (body == null) {
			return null;
		}
		Tubuh t = dari(tokoh.id);
		if (t != null) {
			t.a = a;
			t.b = b;
		}
		int aliran = Math.max(0, SekteApi.diplomasi().aliran(tokoh.sekte));
		SosokApi.gerak(body, SeteruIds.KLIP_PATROLI, 0, 0, 0, 0, SeteruIds.panji(aliran));
		return body;
	}

	static void hilang(Tubuh t) {
		AKTIF.remove(t);
		DiplomasiMandiri.hilang(t.body);
	}

	static void hilang(@Nullable DiscipleEntity body) {
		if (body == null) {
			return;
		}
		for (Tubuh t : new ArrayList<>(AKTIF)) {
			if (t.body == body) {
				hilang(t);
				return;
			}
		}
		DiplomasiMandiri.hilang(body);
	}

	/** Every body of feud {@code seteru} leaves. */
	static void hilangSeteru(String seteru) {
		for (Tubuh t : new ArrayList<>(AKTIF)) {
			if (seteru.equals(t.seteru) && !DuelRules.inDuel(t.body)) {
				hilang(t);
			}
		}
	}

	static void bersihkan() {
		for (Tubuh t : new ArrayList<>(AKTIF)) {
			hilang(t);
		}
		AKTIF.clear();
	}

	/** Every 4 ticks: timers, distance, the patrol walk, eyes on the nearest player, the patrol's taunt. */
	static void tick(MinecraftServer server) {
		if (AKTIF.isEmpty() || server.getTickCount() % 4 != 1) {
			return;
		}
		for (Tubuh t : new ArrayList<>(AKTIF)) {
			DiscipleEntity body = t.body;
			if (body.isRemoved() || !body.isAlive()) {
				AKTIF.remove(t);
				continue;
			}
			if (!(body.level() instanceof ServerLevel level)) {
				continue;
			}
			boolean duel = DuelRules.inDuel(body);
			long now = level.getGameTime();
			ServerPlayer near = level.getNearestPlayer(body, JARAK_HILANG) instanceof ServerPlayer p ? p : null;
			if (!duel && (now > t.habis || near == null)) {
				hilang(t);
				continue;
			}
			if (duel) {
				continue;
			}
			ServerPlayer close = level.getNearestPlayer(body, 8.0D) instanceof ServerPlayer p ? p : null;
			SosokApi.tatap(body, close);
			if (t.peran == Peran.PATROLI && t.a != null && t.b != null) {
				if (body.getNavigation().isDone()) {
					BlockPos target = t.keB ? t.b : t.a;
					if (body.blockPosition().distSqr(target) < 9.0D) {
						t.keB = !t.keB;
						target = t.keB ? t.b : t.a;
					}
					if (level.isLoaded(target)) {
						BlockPos g = tanah(level, target);
						body.getNavigation().moveTo(g.getX() + 0.5D, g.getY(), g.getZ() + 0.5D, 0.55D);
					}
				}
				if (close != null && t.owner != null && close.getUUID().equals(t.owner) && now >= t.ejekBerikut) {
					t.ejekBerikut = now + 2400L;
					Patroli.ejekGuru(server, t, close);
				}
			}
		}
	}

	/** The top of the ground at {@code pos}'s column (no leaves). */
	static BlockPos tanah(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos) : pos;
	}

	/** A small puff where something happened (cloud, no blocks). */
	static void kepul(ServerLevel level, Vec3 at, int count) {
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.6D, at.z, count, 0.3D, 0.4D, 0.3D, 0.01D);
	}

	/** The body's name for chat. */
	static Component nama(DiscipleEntity body) {
		return body.getName();
	}

	private TubuhSeteru() {
	}
}
