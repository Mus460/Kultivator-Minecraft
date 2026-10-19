package dev.kultivator.batin;

import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A falling meteor of the Hujan Api Meteor earthly calamity (DESIGN 26.6.4; RevI ch. 409, the 2nd calamity was a meteor fire
 * rain). A plain hostile living entity so techniques, disciples and formations can strike it in the air: no AI, no gravity,
 * no physics (it slides along a straight line from 40 blocks up to its marked landing point over {@link #FALL_TICKS}), tag
 * {@code kultivator.abadi_foe}, never saved. Landing is handled by {@link InnerCalamity} (a 3-block burst through
 * {@code mobAttack} plus entity fire): no block fire, no explosion, no block change.
 */
public class BatinMeteorEntity extends Monster {

	public static final int FALL_TICKS = 120;
	public static final double FALL_HEIGHT = 40.0D;
	public static final double BASE_HEALTH = 20.0D;

	private @Nullable Vec3 start;
	private @Nullable Vec3 target;
	private int fall;
	private boolean landed;
	private @Nullable UUID owner;

	public BatinMeteorEntity(EntityType<? extends BatinMeteorEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HEALTH).add(Attributes.MOVEMENT_SPEED, 0.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	/** Sets the line it falls along and its health ({@code 20 × (1 + 0.35 R_eff)}). */
	public void setup(Vec3 landing, double health, UUID owner) {
		this.target = landing;
		this.start = landing.add(0.0D, FALL_HEIGHT, 0.0D);
		this.owner = owner;
		this.fall = 0;
		var attribute = getAttribute(Attributes.MAX_HEALTH);
		if (attribute != null) {
			attribute.setBaseValue(Math.max(1.0D, health));
		}
		setHealth(getMaxHealth());
		addTag(TechniquePower.TAG_ABADI_FOE);
		addTag(InnerCalamity.TAG);
		setPos(start.x, start.y, start.z);
	}

	public @Nullable Vec3 landing() {
		return target;
	}

	public @Nullable UUID owner() {
		return owner;
	}

	/** True once it reached the ground (set before it is removed). */
	public boolean landed() {
		return landed;
	}

	/** Ticks left until it lands. */
	public int ticksLeft() {
		return Math.max(0, FALL_TICKS - fall);
	}

	@Override
	public void aiStep() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (start == null || target == null) {
			discard();
			return;
		}
		if (isDeadOrDying()) {
			return;
		}
		fall++;
		double t = Math.min(1.0D, fall / (double) FALL_TICKS);
		Vec3 now = start.lerp(target, t);
		setPos(now.x, now.y, now.z);
		setDeltaMovement(Vec3.ZERO);
		if (fall >= FALL_TICKS) {
			landed = true;
			InnerCalamity.meteorLanded(serverLevel, this);
			discard();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && isAlive()) {
			// A fire tail (client particles, from common classes only).
			for (int i = 0; i < 3; i++) {
				level().addParticle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5D) * 0.8D,
						getY() + 0.7D + random.nextDouble() * 0.8D, getZ() + (random.nextDouble() - 0.5D) * 0.8D, 0.0D, 0.12D, 0.0D);
			}
			level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6D, getZ(), 0.0D, 0.08D, 0.0D);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof BatinMeteorEntity) {
			return false;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}
}
