package dev.kultivator.technique.spec.run;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.sect.Sparring;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.LoadedPath;
import dev.kultivator.util.Targeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's cast (DESIGN 22.2.6 step 3): P_player, the spec's mastery level, A/D/N (1 in an arena), the aim point (a
 * read-only clip along a loaded path), the locked target under the crosshair, the visual tier and the Kehancuran flag of
 * 22.11.2. Hostiles come from {@code Targeting.isHostileTo}, damage goes through {@code Techniques.hurt}.
 */
public final class PlayerCaster extends Caster {

	/** How far the aim point reaches. */
	public static final double AIM_RANGE = 64.0D;

	private final ServerPlayer player;

	public PlayerCaster(ServerPlayer player, TechniqueSpec spec, CastOptions options) {
		this(player, spec, options, null, 1.0D, false);
	}

	private PlayerCaster(ServerPlayer player, TechniqueSpec spec, CastOptions options, @Nullable Caster parent, double childFactor, boolean isEcho) {
		super(spec, player, player.level(), options, parent);
		this.player = player;
		CultivationData data = Cultivators.get(player);
		this.power = TechniquePower.power(data, spec);
		this.mastery = Mastery.level(data, spec.id());
		this.arena = DuelRules.inDuel(player) || Sparring.inDuel(player);
		this.factors = arena ? TechniquePower.Factors.ONE : TechniquePower.factors(data, spec);
		this.visualTier = TechniquePower.casterTier(data);
		this.factor = childFactor;
		this.echo = isEcho;
		if (parent != null) {
			this.aim = parent.aim;
			this.target = parent.target;
			this.end = parent.end;
			this.mastery = parent.mastery;
		} else {
			this.aim = aimPoint(player);
			this.target = Targeting.lookedAtHostile(player, AIM_RANGE);
		}
		readBonuses();
		this.kehancuran = parent == null && kehancuranFlag(player, data, spec, options);
	}

	/** The aim point: where the view meets a block within 64 (read-only, along loaded chunks), or its end. */
	static Vec3 aimPoint(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = LoadedPath.ray(player.level(), eye, eye.add(player.getViewVector(1.0F).scale(AIM_RANGE)), player);
		return hit.getType() == HitResult.Type.MISS ? hit.getLocation() : hit.getLocation();
	}

	/** 22.11.2: the switch on and allowed, a player at Golden Core or higher, KH shapes, not sneaking, no test, no duel. */
	private static boolean kehancuranFlag(ServerPlayer player, CultivationData data, TechniqueSpec spec, CastOptions options) {
		if (!spec.hasDestruction() || options.noKehancuran() || player.isShiftKeyDown()) {
			return false;
		}
		if ((options.showcase() || options.forceVisual()) && !TechniqueTests.kehancuranTests(player)) {
			return false;
		}
		if (!Kehancuran.selfOn(player) || TechniquePower.kScale(data) <= 0.0D || !data.realm().atLeast(Realm.GOLDEN_CORE)) {
			return false;
		}
		if (DuelRules.inDuel(player) || Sparring.inDuel(player) || data.breakingThrough() || Breakthrough.isBusy(player)
				|| HeartDemons.inTrial(player)) {
			return false;
		}
		return true;
	}

	@Override
	public @Nullable ServerPlayer player() {
		return player;
	}

	@Override
	public @Nullable DiscipleEntity disciple() {
		return null;
	}

	@Override
	public @Nullable ServerPlayer master() {
		return player;
	}

	@Override
	public boolean mayHit(Entity entity, boolean precise) {
		return entity.level() == level && entity != player && Targeting.isHostileTo(entity, player, precise);
	}

	/** The legacy Divine Sense keeps its ALL r48→96 sense (22.10 row 3), as a flattened query. */
	private static final double DIVINE_SENSE_REACH = 96.0D;

	/** Hostiles within {@code radius} of {@code centre}: one query, radius at most {@code techniqueAreaCap} (Dewa ×1.25, 22.15.1). */
	@Override
	public List<LivingEntity> hostiles(Vec3 centre, double radius, boolean precise) {
		double cap = radiusCap();
		if (radius > cap && spec.legacy() == dev.kultivator.data.Technique.DIVINE_SENSE) {
			return flat(centre, Math.min(radius, DIVINE_SENSE_REACH), TechniquePower.AREA_HEIGHT_CAP, precise);
		}
		double reach = Math.max(0.5D, Math.min(radius, cap));
		return Targeting.hostilesAround(player, centre, reach, precise);
	}

	@Override
	public List<LivingEntity> allies(Vec3 centre, double radius) {
		AABB box = new AABB(centre, centre).inflate(Math.min(radius, 48.0D));
		List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive()
				&& !entity.isSpectator()
				&& (entity instanceof Player || entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null
						|| dev.kultivator.beast.SpiritBeasts.isSpiritBeast(entity))
				&& !Targeting.isHostileTo(entity, player)
				&& !(entity instanceof DiscipleEntity disciple && disciple.role() != DiscipleEntity.Role.DISCIPLE && disciple.role() != DiscipleEntity.Role.CLONE)));
		if (!list.contains(player) && player.position().distanceTo(centre) <= radius + 1.0D) {
			list.add(player);
		}
		return list;
	}

	@Override
	boolean chokeHurt(DamageContext context) {
		return Techniques.hurt(player, context);
	}

	@Override
	boolean casterValid() {
		return !player.isRemoved() && player.isAlive() && !player.isDeadOrDying() && player.level() == level;
	}

	@Override
	void restoreQi(double amount) {
		Qi.restore(player, amount);
	}

	@Override
	double maxQi() {
		return Qi.max(Cultivators.get(player));
	}

	@Override
	void takeQi(double amount) {
		CultivationData data = Cultivators.get(player);
		data.setQi(Math.max(0.0D, data.qi() - amount));
		Cultivators.dirty(player);
	}

	@Override
	void gainComprehension(double amount) {
		CultivationData data = Cultivators.get(player);
		dev.kultivator.data.Dao dao = spec.dao() != null ? spec.dao() : data.dao();
		if (dao != null) {
			Comprehension.grantCapped(player, dao, amount);
		}
	}

	@Override
	void resetCooldowns(TechniqueGrade maxGrade) {
		CultivationData data = Cultivators.get(player);
		long now = level.getGameTime();
		for (TechniqueSpec known : data.learnedSpecs()) {
			if (!known.pamungkas() && known.grade().tier() <= maxGrade.tier() && known != spec) {
				data.setCooldown(known.id(), now, 0);
			}
		}
		Cultivators.dirty(player);
	}

	@Override
	void finished(EndReason reason) {
		TechniqueRunner.forgetCast(this);
		if (parent != null || options.test() || TechniqueRunner.bypass(player) || spec.titah()) {
			// Phase 10b (DESIGN 29.3.1): Titah decrees have no mastery (level fixed at 0)
			return;
		}
		if (reason == EndReason.DONE || reason == EndReason.CANCELLED || reason == EndReason.REPLACED) {
			Mastery.award(player, spec, hitAny, killedAny, hitBoss);
		}
	}

	@Override
	void onKill(LivingEntity victim) {
		FeatTracker.onTechniqueKill(player, spec, victim);
	}

	@Override
	Realm casterRealm() {
		return Cultivators.get(player).realm();
	}

	@Override
	void onFirstHit(LivingEntity victim) {
		Comprehension.onTechniqueHit(player, spec);
	}

	@Override
	Caster child(TechniqueSpec other, double childFactor, boolean isEcho) {
		return new PlayerCaster(player, other, options, this, childFactor * factor, isEcho);
	}

	/** Known specs the codex counts (player-visible). */
	static int known(CultivationData data) {
		int count = 0;
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.playerVisible() && data.knows(spec.id())) {
				count++;
			}
		}
		return count;
	}
}
