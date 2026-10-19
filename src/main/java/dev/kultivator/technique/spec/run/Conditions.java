package dev.kultivator.technique.spec.run;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only evaluation of {@link Cond} (DESIGN 22.3.4 conditions and the feat conditions of 22.8.9). Nothing here
 * changes the world; block reads are single lookups (the ground under a receiver, the sky above the caster).
 */
final class Conditions {

	/** Dawn: the last 1,500 ticks of the night and the first 1,000 of the day. */
	private static final long DAWN_FROM = 22_500L;
	private static final long DAWN_TO = 1_000L;

	/** True when the condition depends on the target (checked per target; otherwise once per evaluation). */
	static boolean perTarget(Cond cond) {
		return switch (cond.kind()) {
			case TARGET_BURNING, TARGET_FROZEN, TARGET_LOW_HP, TARGET_BOSS, TARGET_UNDEAD, HIT_BEFORE, TARGET_TOUGH -> true;
			case ALL, ANY, NOT -> {
				for (Cond part : cond.parts()) {
					if (perTarget(part)) {
						yield true;
					}
				}
				yield false;
			}
			default -> false;
		};
	}

	/**
	 * The multiplier a {@code ? cond ×m} gives (1 when it does not hold). {@code TRIB_STRIKES(per, cap)} always holds and
	 * gives {@code m × (1 + min(cap, per × strikes))}.
	 */
	static double multiplier(Step.When when, @Nullable Caster cast, LivingEntity subject, @Nullable LivingEntity target) {
		Cond cond = when.cond();
		if (cond.kind() == Cond.Kind.TRIB_STRIKES) {
			return Math.max(0.0D, when.mult()) * (1.0D + Math.min(cond.b(), cond.a() * strikes(subject)));
		}
		return test(cond, cast, subject, target) ? Math.max(0.0D, when.mult()) : 1.0D;
	}

	/** Evaluates {@code cond} for {@code subject} (the caster) and the optional {@code target}. */
	static boolean test(Cond cond, @Nullable Caster cast, LivingEntity subject, @Nullable LivingEntity target) {
		if (!(subject.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 at = subject.position();
		BlockPos head = BlockPos.containing(subject.getEyePosition());
		return switch (cond.kind()) {
			case NONE -> true;
			case NIGHT -> level.isDarkOutside();
			case DAY -> level.isBrightOutside();
			case DAWN -> {
				long time = Math.floorMod(level.getOverworldClockTime(), 24_000L);
				yield !level.dimensionType().hasFixedTime() && (time >= DAWN_FROM || time < DAWN_TO);
			}
			case SKY_OPEN -> level.canSeeSky(head);
			case FULL_MOON -> fullMoon(level, head);
			case FULL_MOON_NIGHT -> level.isDarkOutside() && !level.isRaining() && fullMoon(level, head);
			case RAINING -> level.isRaining();
			case THUNDERING -> level.isThundering();
			case IN_WATER -> subject.isInWater();
			case IN_NETHER -> level.dimension() == Level.NETHER;
			case IN_END -> level.dimension() == Level.END;
			case TARGET_BURNING -> target != null && target.isOnFire();
			case TARGET_FROZEN -> target != null && (target.isFullyFrozen() || target.getTicksFrozen() > 0);
			case TARGET_LOW_HP -> target != null && target.getHealth() < target.getMaxHealth() * cond.a();
			case TARGET_BOSS -> target != null && TechniquePower.isBoss(target);
			case TARGET_UNDEAD -> target != null && target.is(EntityTypeTags.UNDEAD);
			case CASTER_LOW_HP -> subject.getHealth() < subject.getMaxHealth() * cond.a();
			case FULL_QI -> fullQi(subject);
			case HOLDING_SWORD -> subject.getMainHandItem().is(ItemTags.SWORDS);
			case PLAYER_CASTER -> (cast != null ? cast.caster : subject) instanceof ServerPlayer;
			case RANK_AT_LEAST -> rankAtLeast(cast != null ? cast.caster : subject, (int) cond.a(), (int) cond.b());
			case NEAR_ANCHOR -> cast != null && subject.position().distanceTo(cast.anchorForConditions()) <= cond.a();
			case TRIB_STRIKES -> true;
			case DIVINE_SENSE -> Boons.divineSense(subject);
			case IN_AREA -> cast != null && Fields.inOwnArea(subject, target == null ? subject.position() : target.position());
			case HIT_BEFORE -> cast != null && target != null && cast.hitsOn(target) >= cond.a();
			case TARGET_TOUGH -> target != null && cast != null && target.getMaxHealth() >= cond.a() * cast.power();
			case NATURAL_GROUND -> naturalGround(level, target == null ? subject : target);
			case BIOME -> biome(level, BlockPos.containing(at), cond.key());
			case DIMENSION -> level.dimension().identifier().toString().equals(cond.key());
			case ALTITUDE -> at.y >= cond.a();
			case SITE -> subject instanceof ServerPlayer player && site(player, cond.key());
			case ALL -> {
				for (Cond part : cond.parts()) {
					if (!test(part, cast, subject, target)) {
						yield false;
					}
				}
				yield true;
			}
			case ANY -> {
				for (Cond part : cond.parts()) {
					if (test(part, cast, subject, target)) {
						yield true;
					}
				}
				yield false;
			}
			case NOT -> cond.parts().isEmpty() || !test(cond.parts().get(0), cast, subject, target);
		};
	}

	/** The caster reached realm ordinal {@code realm} (and inside it layer index {@code layer}). */
	private static boolean rankAtLeast(LivingEntity who, int realm, int layer) {
		dev.kultivator.data.Rank rank;
		if (who instanceof ServerPlayer player) {
			rank = Cultivators.get(player).rank();
		} else if (who instanceof dev.kultivator.disciple.DiscipleEntity disciple) {
			rank = disciple.data().rank();
		} else {
			return false;
		}
		if (rank.realm().ordinal() != realm) {
			return rank.realm().ordinal() > realm;
		}
		return rank.layer() >= layer;
	}

	private static boolean fullMoon(ServerLevel level, BlockPos pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos) == MoonPhase.FULL_MOON;
	}

	private static boolean fullQi(LivingEntity subject) {
		if (subject instanceof ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			return data.qi() + 1.0E-3D >= Qi.max(data);
		}
		if (subject instanceof dev.kultivator.disciple.DiscipleEntity disciple) {
			return disciple.data().qi() + 1.0E-3D >= dev.kultivator.disciple.DiscipleCultivation.maxQi(disciple.data());
		}
		return false;
	}

	/** Lifetime survived tribulation strikes of a player (disciples: 0). */
	static int strikes(LivingEntity subject) {
		return subject instanceof ServerPlayer player ? Tribulation.strikesSurvived(Cultivators.get(player)) : 0;
	}

	/** One read-only block check under the receiver's feet. */
	private static boolean naturalGround(ServerLevel level, LivingEntity who) {
		BlockPos below = who.blockPosition().below();
		return level.isLoaded(below) && Shapes.natural(level.getBlockState(below));
	}

	/** A biome id or {@code #tag}. */
	static boolean biome(ServerLevel level, BlockPos pos, String key) {
		if (key == null || key.isEmpty() || !level.isLoaded(pos)) {
			return false;
		}
		try {
			if (key.startsWith("#")) {
				TagKey<Biome> tag = TagKey.create(Registries.BIOME, Identifier.parse(key.substring(1)));
				return level.getBiome(pos).is(tag);
			}
			return level.getBiome(pos).is(Identifier.parse(key));
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static boolean site(ServerPlayer player, String kind) {
		for (SpiritSites.Site site : SpiritSites.current(player).sites()) {
			if (site.id().equals(kind)) {
				return true;
			}
		}
		return false;
	}

	private Conditions() {
	}
}
