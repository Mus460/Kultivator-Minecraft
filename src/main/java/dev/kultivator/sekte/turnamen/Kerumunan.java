package dev.kultivator.sekte.turnamen;

import dev.kultivator.desa.Vassals;
import dev.kultivator.desa.VillageView;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.duel.session.DuelRing;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The crowd of a sect tournament (DESIGN 36.2.3 "penonton", budget 36.3.9): idle disciples who are not entrants take
 * the free tribune seats (the existing spectator activity 8), then visitors come as light {@link Penonton} bodies: envoys
 * of allied and friendly NPC sects (module D's service; at most {@code utusanMaks}, two of them from the visiting sect of
 * an inter-sect tournament) and villagers of the hall's vassal villages (at most {@code wargaMaks}, more from the more
 * loyal villages), together never more than {@code penontonMaks} (12). They stand on the remaining seats, else on a ring
 * 11 blocks around the stage; everything leaves when the tournament ends. Spawns read only the ground (no block is
 * changed).
 */
final class Kerumunan {

	/** One hall's crowd. */
	private static final class Crowd {
		final ServerLevel level;
		final Vec3 venue;
		final List<Penonton> visitors = new ArrayList<>();
		final Set<UUID> seated = new HashSet<>();

		Crowd(ServerLevel level, Vec3 venue) {
			this.level = level;
			this.venue = venue;
		}
	}

	private static final Map<UUID, Crowd> CROWDS = new HashMap<>();
	private static final double RING = 11.0D;
	private static final double DISCIPLE_RANGE = 64.0D;

	/** The tournament started: seats the idle disciples, then the visitors. {@code entrants} already hold the first seats. */
	static void gather(UUID owner, ServerLevel level, Vec3 venue, List<BlockPos> seats, Set<UUID> entrants, @Nullable String guestSect) {
		disperse(owner);
		Crowd crowd = new Crowd(level, venue);
		CROWDS.put(owner, crowd);
		TurnamenConfig config = TurnamenConfig.get();
		int next = Math.min(seats.size(), entrants.size());
		// 1. idle disciples (not entrants, not on duty) on the tribune.
		int disciples = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciples >= config.muridPenontonMaks || next >= seats.size()) {
				break;
			}
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.departed() || disciple.level() != level
					|| disciple.activity() != DiscipleEntity.ACTIVITY_NONE || entrants.contains(disciple.soulId())
					|| disciple.distanceToSqr(venue) > DISCIPLE_RANGE * DISCIPLE_RANGE || disciple.getTarget() != null) {
				continue;
			}
			disciple.setActivity(DiscipleEntity.ACTIVITY_SPECTATOR, seats.get(next++));
			disciple.setActivityFocus(BlockPos.containing(venue));
			crowd.seated.add(disciple.soulId());
			disciples++;
		}
		// 2. visitors.
		int budget = config.penontonMaks;
		RandomSource random = level.getRandom();
		List<Penonton> made = new ArrayList<>();
		DiplomasiService diplomacy = SekteApi.diplomasi();
		int envoys = 0;
		if (guestSect != null && budget > 0) {
			for (int i = 0; i < 2 && envoys < config.utusanMaks && made.size() < budget; i++) {
				Penonton body = envoy(level, random, diplomacy, guestSect);
				if (body != null) {
					made.add(body);
					envoys++;
				}
			}
		}
		for (String sect : diplomacy.sects()) {
			if (envoys >= config.utusanMaks || made.size() >= budget) {
				break;
			}
			if (sect.equals(guestSect)) {
				continue;
			}
			Sikap stance = diplomacy.stance(owner, sect);
			if (stance != Sikap.SEKUTU && stance != Sikap.RAMAH) {
				continue;
			}
			Penonton body = envoy(level, random, diplomacy, sect);
			if (body != null) {
				made.add(body);
				envoys++;
			}
		}
		int villagers = 0;
		for (VillageView village : Vassals.views(owner)) {
			if (villagers >= config.wargaMaks || made.size() >= budget) {
				break;
			}
			if (village.threatened() || village.loyalty() < 30) {
				continue;
			}
			int count = village.loyalty() >= 80 ? 3 : village.loyalty() >= 55 ? 2 : 1;
			for (int i = 0; i < count && villagers < config.wargaMaks && made.size() < budget; i++) {
				Penonton body = villager(level, random, village.name());
				if (body != null) {
					made.add(body);
					villagers++;
				}
			}
		}
		// 3. places: the free seats, then a ring around the stage.
		int ringIndex = 0;
		for (Penonton body : made) {
			Vec3 at = null;
			while (at == null && next < seats.size()) {
				at = Vec3.atBottomCenterOf(seats.get(next++));
			}
			if (at == null) {
				at = ringSpot(level, venue, ringIndex++, made.size());
			}
			if (at == null) {
				continue;
			}
			body.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
			body.watch(venue, null, null);
			if (level.addFreshEntity(body)) {
				crowd.visitors.add(body);
			}
		}
		if (!crowd.visitors.isEmpty() || !crowd.seated.isEmpty()) {
			Halls.log(owner, Component.translatable("kultivator.sekte.turnamen.log.penonton", crowd.seated.size(), envoys, villagers));
		}
	}

	/** A bout began ({@code a}, {@code b}) or ended (both null): the crowd watches it. */
	static void watch(UUID owner, @Nullable Entity a, @Nullable Entity b) {
		Crowd crowd = CROWDS.get(owner);
		if (crowd == null) {
			return;
		}
		for (Penonton body : crowd.visitors) {
			if (body.isAlive()) {
				body.watch(crowd.venue, a, b);
			}
		}
	}

	/** Everyone cheers once (a decided bout, the champion). */
	static void cheer(UUID owner) {
		Crowd crowd = CROWDS.get(owner);
		if (crowd == null) {
			return;
		}
		for (Penonton body : crowd.visitors) {
			if (body.isAlive()) {
				body.cheer();
			}
		}
		// the seated disciples cheer too (a body clip over the seat pose)
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (crowd.seated.contains(disciple.soulId()) && disciple.activity() == DiscipleEntity.ACTIVITY_SPECTATOR) {
				SosokApi.gerak(disciple, GerakId.SORAK_ARENA, 0, 80);
			}
		}
	}

	/** The visitors' count (tests, hub card). */
	static int visitors(UUID owner) {
		Crowd crowd = CROWDS.get(owner);
		return crowd == null ? 0 : crowd.visitors.size();
	}

	/** The tournament ended or was cancelled: visitors leave, seated disciples go back to their own day. */
	static void disperse(UUID owner) {
		Crowd crowd = CROWDS.remove(owner);
		if (crowd == null) {
			return;
		}
		for (Penonton body : crowd.visitors) {
			if (!body.isRemoved()) {
				body.discard();
			}
		}
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (crowd.seated.contains(disciple.soulId()) && disciple.activity() == DiscipleEntity.ACTIVITY_SPECTATOR) {
				disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
	}

	/** Test {@code penonton <n>}: {@code n} more villagers around the stage of the running tournament (or none). */
	static int spawnTest(UUID owner, int n) {
		Crowd crowd = CROWDS.get(owner);
		if (crowd == null) {
			return 0;
		}
		RandomSource random = crowd.level.getRandom();
		int made = 0;
		for (int i = 0; i < n && crowd.visitors.size() < TurnamenConfig.get().penontonMaks; i++) {
			Penonton body = random.nextBoolean() ? villager(crowd.level, random, "Uji") : envoy(crowd.level, random, SekteApi.diplomasi(), "uji");
			Vec3 at = body == null ? null : ringSpot(crowd.level, crowd.venue, crowd.visitors.size(), TurnamenConfig.get().penontonMaks);
			if (at == null) {
				continue;
			}
			body.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
			body.watch(crowd.venue, null, null);
			if (crowd.level.addFreshEntity(body)) {
				crowd.visitors.add(body);
				made++;
			}
		}
		return made;
	}

	private static @Nullable Penonton envoy(ServerLevel level, RandomSource random, DiplomasiService diplomacy, String sect) {
		Penonton body = TurnamenEntities.PENONTON.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			return null;
		}
		Lambang emblem = diplomacy.emblem(sect);
		if (emblem == Lambang.DEFAULT) {
			emblem = Lambang.seeded(sect.hashCode());
		}
		body.setLook(Penonton.KIND_ENVOY, random.nextInt(9), random.nextBoolean(), true, Lambang.PALETTE[emblem.field()],
				Lambang.PALETTE[emblem.border()]);
		Component sectName = diplomacy.name(sect);
		body.setCustomName(Component.translatable("kultivator.sekte.turnamen.penonton.utusan",
				sectName != null ? sectName : Component.translatable("kultivator.sekte.turnamen.penonton.sekte_jauh")));
		body.setCustomNameVisible(false);
		return body;
	}

	private static final int[] HOMESPUN = {0x8A6A48, 0x5E6E7A, 0x6F7F52, 0x9A8466, 0x4F5A72, 0x7A5A44};

	private static @Nullable Penonton villager(ServerLevel level, RandomSource random, String village) {
		Penonton body = TurnamenEntities.PENONTON.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			return null;
		}
		int robe = HOMESPUN[random.nextInt(HOMESPUN.length)];
		body.setLook(Penonton.KIND_VILLAGER, random.nextInt(9), random.nextBoolean(), random.nextInt(3) != 0, robe, 0xC9B58A);
		body.setCustomName(Component.translatable("kultivator.sekte.turnamen.penonton.warga", village));
		body.setCustomNameVisible(false);
		return body;
	}

	/** A standing spot on a ring around the stage (read-only ground rays), or null when it is blocked. */
	private static @Nullable Vec3 ringSpot(ServerLevel level, Vec3 venue, int index, int count) {
		for (int attempt = 0; attempt < 3; attempt++) {
			double angle = Math.PI * 2.0D * (index + attempt * 0.37D) / Math.max(1, count) + 0.3D;
			double radius = RING + attempt;
			Vec3 near = venue.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
			BlockPos cell = BlockPos.containing(near);
			if (!level.isLoaded(cell)) {
				continue;
			}
			Vec3 ground = DuelRing.ground(level, near, venue.y);
			BlockPos feet = BlockPos.containing(ground.x, ground.y + 0.01D, ground.z);
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
					&& level.getFluidState(feet).isEmpty()) {
				return ground;
			}
		}
		return null;
	}

	static void clear() {
		for (UUID owner : List.copyOf(CROWDS.keySet())) {
			disperse(owner);
		}
		CROWDS.clear();
	}

	private Kerumunan() {
	}
}
