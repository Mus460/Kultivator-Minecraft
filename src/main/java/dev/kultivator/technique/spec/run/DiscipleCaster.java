package dev.kultivator.technique.spec.run;

import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A disciple's cast (DESIGN 22.13.3, 22.13.4): P_disciple (the {@code damageFactor} hooks apply in the choke point), the
 * disciple's own mastery and A/D/N from its rank and stars, look and aim toward the target (or the densest cluster for
 * ZONE, RAIN and FIELD), SELF payloads on the disciple and ALLY payloads including the master. Hostiles only through
 * {@code DiscipleTechniques.mayHit} (never the mobs whose fight changes blocks); never Mode Kehancuran.
 */
public final class DiscipleCaster extends Caster {

	private final DiscipleEntity entity;
	private final @Nullable ServerPlayer owner;

	public DiscipleCaster(DiscipleEntity entity, TechniqueSpec spec, @Nullable LivingEntity target, CastOptions options) {
		this(entity, spec, target, options, null, 1.0D, false);
	}

	private DiscipleCaster(DiscipleEntity entity, TechniqueSpec spec, @Nullable LivingEntity target, CastOptions options, @Nullable Caster parent,
			double childFactor, boolean isEcho) {
		super(spec, entity, (ServerLevel) entity.level(), options, parent);
		this.entity = entity;
		this.owner = entity.owner();
		DiscipleData data = entity.data();
		this.power = TechniquePower.power(data);
		this.mastery = Mastery.level(data, spec.id());
		this.arena = DuelRules.inDuel(entity);
		this.factors = arena ? TechniquePower.Factors.ONE : TechniquePower.factors(data, spec);
		this.visualTier = TechniquePower.casterTier(data);
		this.factor = childFactor;
		this.echo = isEcho;
		this.kehancuran = false;
		this.target = target;
		if (target != null) {
			Vec3 middle = target.getBoundingBox().getCenter();
			Vec3 to = middle.subtract(eye);
			if (to.lengthSqr() > 1.0E-4D) {
				this.look = to.normalize();
			}
			this.aim = clustered(spec) ? densest(target) : target.position();
		} else {
			this.aim = entity.position().add(look.scale(8.0D));
		}
		if (parent != null) {
			this.mastery = parent.mastery;
		}
		readBonuses();
	}

	/** ZONE, RAIN and FIELD aim at the densest cluster around the target. */
	private static boolean clustered(TechniqueSpec spec) {
		for (Step step : spec.timeline()) {
			Shape shape = step.shape();
			if (shape instanceof Shape.Zone || shape instanceof Shape.Rain || shape instanceof Shape.Field) {
				return true;
			}
		}
		return false;
	}

	private Vec3 densest(LivingEntity around) {
		List<LivingEntity> near = hostiles(around.position(), 8.0D, false);
		LivingEntity best = around;
		int bestCount = -1;
		for (LivingEntity a : near) {
			int count = 0;
			for (LivingEntity b : near) {
				if (a.distanceToSqr(b) <= 16.0D) {
					count++;
				}
			}
			if (count > bestCount) {
				bestCount = count;
				best = a;
			}
		}
		return best.position();
	}

	@Override
	public @Nullable ServerPlayer player() {
		return null;
	}

	@Override
	public DiscipleEntity disciple() {
		return entity;
	}

	@Override
	public @Nullable ServerPlayer master() {
		return owner != null && !owner.isRemoved() ? owner : entity.owner();
	}

	@Override
	public boolean mayHit(Entity other, boolean precise) {
		return DiscipleTechniques.mayHit(entity, master(), other, precise);
	}

	@Override
	public List<LivingEntity> hostiles(Vec3 centre, double radius, boolean precise) {
		ServerPlayer master = master();
		// one query, radius at most techniqueAreaCap (Dewa ×1.25, 22.15.1)
		double reach = Math.max(0.5D, Math.min(radius, radiusCap()));
		return DiscipleTechniques.hostilesAround(entity, master, centre, reach, precise);
	}

	@Override
	public List<LivingEntity> allies(Vec3 centre, double radius) {
		ServerPlayer master = master();
		AABB box = new AABB(centre, centre).inflate(Math.min(radius, 48.0D));
		List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, other -> other.isAlive() && !other.isSpectator()
				&& (other == entity || other == master || other instanceof DiscipleEntity disciple && disciple.isOwnedBy(master)
						&& (disciple.role() == DiscipleEntity.Role.DISCIPLE || disciple.role() == DiscipleEntity.Role.CLONE)
						|| other instanceof OwnableEntity ownable && ownable.getOwnerReference() != null && master != null
						&& master.getUUID().equals(ownable.getOwnerReference().getUUID())
						|| other instanceof Player && master != null && !dev.kultivator.util.Targeting.isHostileTo(other, master))
				&& !DuelRules.dueling(entity, other) && (master == null || !DuelRules.dueling(master, other))));
		if (master != null && !list.contains(master) && master.level() == level && master.position().distanceTo(centre) <= radius + 1.0D) {
			list.add(master);
		}
		return list;
	}

	@Override
	boolean chokeHurt(DamageContext context) {
		return DiscipleTechniques.hurt(entity, master(), context);
	}

	@Override
	boolean casterValid() {
		return !entity.isRemoved() && entity.isAlive() && !entity.isDeadOrDying() && entity.level() == level;
	}

	@Override
	void restoreQi(double amount) {
		DiscipleData data = entity.data();
		data.setQi(Math.min(DiscipleCultivation.maxQi(data), data.qi() + Math.max(0.0D, amount)));
	}

	@Override
	double maxQi() {
		return DiscipleCultivation.maxQi(entity.data());
	}

	@Override
	void takeQi(double amount) {
		DiscipleData data = entity.data();
		data.setQi(Math.max(0.0D, data.qi() - amount));
	}

	@Override
	void gainComprehension(double amount) {
		DiscipleData data = entity.data();
		if (data.dao() != null) {
			data.addComprehension(DiscipleCultivation.comprehensionGain(data, data.dao(), amount));
		}
	}

	@Override
	void resetCooldowns(TechniqueGrade maxGrade) {
		DiscipleData data = entity.data();
		for (TechniqueSpec known : data.learnedSpecs()) {
			if (!known.pamungkas() && known.grade().tier() <= maxGrade.tier() && known != spec) {
				data.setCooldown(known.id(), 0);
			}
		}
	}

	@Override
	void finished(EndReason reason) {
		TechniqueRunner.forgetCast(this);
		if (parent != null || options.test()) {
			return;
		}
		if (reason == EndReason.DONE || reason == EndReason.CANCELLED) {
			Mastery.awardDisciple(entity, spec, hitAny, killedAny, hitBoss);
		}
	}

	@Override
	void onKill(LivingEntity victim) {
	}

	@Override
	dev.kultivator.data.Realm casterRealm() {
		return entity.data().realm();
	}

	@Override
	void onFirstHit(LivingEntity victim) {
		DiscipleData data = entity.data();
		if (spec.dao() != null && spec.dao() == data.dao()) {
			data.addComprehension(DiscipleCultivation.comprehensionGain(data, spec.dao(),
					DiscipleCultivation.TECHNIQUE_COMPREHENSION * spec.grade().comprehensionFactor()));
		}
	}

	@Override
	int maxTargets() {
		return TechniquePower.maxTargets(spec.dewa());
	}

	@Override
	Caster child(TechniqueSpec other, double childFactor, boolean isEcho) {
		return new DiscipleCaster(entity, other, target, options, this, childFactor * factor, isEcho);
	}
}
