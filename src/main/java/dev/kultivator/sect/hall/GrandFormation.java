package dev.kultivator.sect.hall;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Formasi Agung Sekte (DESIGN 20.3.2): a Protective Formation of the hall owner with its lodestone within 24 blocks of
 * the active stele. Its radius grows with the hall level, a Foundation+ disciple ordered to stay or meditate within 3
 * blocks of the lodestone becomes its Penjaga Formasi (activity 5, no amethyst drain while it is there), and restriction
 * layers stack with the elders inside the territory and an Inner+ guard. Each Pelindung Sekte inside multiplies its
 * damage. {@code ProtectiveFormations} asks {@link #state} once per pulse; the answer is cached for a second.
 * <p>
 * Sources: <i>A Record of a Mortal's Journey</i> ch. 153 (Yue Lu Hall under its formation), <i>Renegade Immortal</i>
 * ch. 216 (Wang Lin's stacked restrictions), <i>Soul Land</i> (the protectors of the Seven Treasure Glazed Tile Sect).
 */
public final class GrandFormation {

	public static final double GUARD_RANGE = 3.0D;
	public static final int MAX_LAYERS = 4;
	public static final double PROTECTOR_FACTOR = 1.5D;
	public static final double MAX_DAMAGE_FACTOR = 2.0D;
	/** Layer 2: Slowness II on monsters inside; layer 3: Slowness V for 3 s when they cross the edge; layer 4: 2 damage/s. */
	public static final int SLOW_LAYER = 2;
	public static final int EDGE_LAYER = 3;
	public static final int HARD_LAYER = 4;
	private static final int CACHE_TICKS = 20;

	/** What a formation is this second: plain (radius 16, one layer) or grand. */
	public record State(boolean grand, double radius, int layers, double damageFactor, boolean guarded, String guardName) {
		public static final State PLAIN = new State(false, ProtectiveFormations.RADIUS, 1, 1.0D, false, "");
	}

	private static final class Cached {
		State state = State.PLAIN;
		int tick = Integer.MIN_VALUE;
	}

	private static final Map<Formation, Cached> CACHE = new WeakHashMap<>();

	public static void clear() {
		CACHE.clear();
	}

	/** The formation's state this second (computed at most once per second; sets and clears the guard's activity). */
	public static State state(ServerLevel level, Formation formation, int tick) {
		Cached cached = CACHE.computeIfAbsent(formation, key -> new Cached());
		if (tick - cached.tick < CACHE_TICKS && tick >= cached.tick) {
			return cached.state;
		}
		cached.tick = tick;
		cached.state = compute(level, formation);
		return cached.state;
	}

	/** The last radius of a formation (16 when it was never computed); for {@code insideActiveFormation}. */
	public static double radius(Formation formation) {
		Cached cached = CACHE.get(formation);
		return cached == null ? ProtectiveFormations.RADIUS : cached.state.radius();
	}

	/** The last state without computing (the Aula tablet), or null. */
	static @Nullable State peek(Formation formation) {
		Cached cached = CACHE.get(formation);
		return cached == null ? null : cached.state;
	}

	private static State compute(ServerLevel level, Formation formation) {
		UUID owner = formation.owner();
		Halls.HallView hall = Halls.hallOf(owner);
		Vec3 lodestone = Vec3.atCenterOf(formation.pos());
		if (hall == null || !hall.active() || !level.dimension().equals(hall.dim())
				|| lodestone.distanceToSqr(Vec3.atCenterOf(hall.pos())) > HallLevel.FORMATION_RANGE * HallLevel.FORMATION_RANGE) {
			return State.PLAIN;
		}
		DiscipleEntity guard = null;
		int elders = 0;
		int protectors = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.level() != level || disciple.isReleasing()) {
				continue;
			}
			DiscipleData data = disciple.data();
			boolean inside = Halls.inTerritory(owner, level, disciple.position());
			int rank = SectRanks.rank(data);
			if (inside && rank >= SectRanks.TETUA) {
				elders++;
			}
			if (inside && SectRanks.isProtector(data)) {
				protectors++;
			}
			if (onTrial(disciple)) {
				continue;
			}
			boolean qualifies = canGuard(disciple) && disciple.distanceToSqr(lodestone) <= (GUARD_RANGE + 1.0D) * (GUARD_RANGE + 1.0D);
			if (disciple.activity() == DiscipleEntity.ACTIVITY_FORMATION_GUARD) {
				BlockPos spot = disciple.activitySpot();
				boolean spotNear = spot != null && Vec3.atBottomCenterOf(spot).distanceToSqr(lodestone) <= (GUARD_RANGE + 1.5D) * (GUARD_RANGE + 1.5D);
				if (guard == null && canGuard(disciple) && spotNear) {
					guard = disciple;
					// the core keeper sits and channels (the other formation guards stand with the spear)
					disciple.setGuardSits(true);
				} else if (spotNear) {
					disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			} else if (guard == null && qualifies && disciple.activity() == DiscipleEntity.ACTIVITY_NONE) {
				disciple.setActivity(DiscipleEntity.ACTIVITY_FORMATION_GUARD, disciple.blockPosition());
				disciple.setGuardSits(true);
				guard = disciple;
				ServerPlayer master = disciple.owner();
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.hall.formation.guard_start", disciple.getDisplayName()).withStyle(ChatFormatting.AQUA));
				}
			}
		}
		int layers = 1 + elders + (guard != null && SectRanks.rank(guard.data()) >= SectRanks.DALAM ? 1 : 0);
		double damage = Math.min(MAX_DAMAGE_FACTOR, Math.pow(PROTECTOR_FACTOR, protectors));
		return new State(true, HallLevel.formationRadius(hall.level()), Math.min(MAX_LAYERS, layers), damage, guard != null,
				guard == null ? "" : guard.getName().getString());
	}

	/** Foundation+, ordered to stay or meditate, not breaking through. */
	private static boolean canGuard(DiscipleEntity disciple) {
		DiscipleData data = disciple.data();
		DiscipleEntity.Mode mode = disciple.mode();
		return data.realm().atLeast(Realm.FOUNDATION) && (mode == DiscipleEntity.Mode.STAY || mode == DiscipleEntity.Mode.MEDITATE)
				&& !data.breakingThrough() && disciple.role() == DiscipleEntity.Role.DISCIPLE;
	}

	/**
	 * Once a second for a disciple holding activity 5: it keeps the post only while it may guard and a grand formation of
	 * its master is beside its spot (otherwise it is freed at once, so a new order is obeyed).
	 */
	static void checkGuard(DiscipleEntity disciple) {
		if (onTrial(disciple)) {
			return;
		}
		if (disciple.activity() != DiscipleEntity.ACTIVITY_FORMATION_GUARD || !(disciple.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos spot = disciple.activitySpot();
		boolean keep = spot != null && canGuard(disciple);
		if (keep) {
			keep = false;
			Vec3 at = Vec3.atBottomCenterOf(spot);
			for (Formation formation : FormationData.get(level).all()) {
				State state = peek(formation);
				if (formation.owner().equals(disciple.ownerId()) && formation.active() && state != null && state.grand()
						&& Vec3.atCenterOf(formation.pos()).distanceToSqr(at) <= (GUARD_RANGE + 1.5D) * (GUARD_RANGE + 1.5D)) {
					keep = true;
					break;
				}
			}
		}
		if (!keep) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		} else {
			disciple.setGuardSits(true);
		}
	}

	/** A Latihan Khusus holds activity 5 at the stele (PromotionTrial writes {@code trial_spot}); never touch it here. */
	private static boolean onTrial(DiscipleEntity disciple) {
		return disciple.data().extra().getCompoundOrEmpty(Duties.EXTRA).contains("trial_spot");
	}

	/** The Aula tablet's line for the owner's formation near the stele: grand, layers, radius, guard. */
	static @Nullable State forHall(ServerLevel level, BlockPos stele, UUID owner) {
		Vec3 centre = Vec3.atCenterOf(stele);
		for (Formation formation : FormationData.get(level).all()) {
			if (formation.owner().equals(owner) && Vec3.atCenterOf(formation.pos()).distanceToSqr(centre)
					<= HallLevel.FORMATION_RANGE * HallLevel.FORMATION_RANGE) {
				State state = peek(formation);
				return state != null && formation.active() ? state : State.PLAIN;
			}
		}
		return null;
	}

	private GrandFormation() {
	}
}
