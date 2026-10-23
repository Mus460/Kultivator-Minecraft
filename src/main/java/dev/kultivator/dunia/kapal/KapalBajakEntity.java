package dev.kultivator.dunia.kapal;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * {@code kultivator:kapal_bajak}, the Bajak Langit raider (detail pass of DESIGN 34.5.15, rule W2: an entity, never
 * blocks): a lean black-sailed skiff that comes out of the clouds when sky pirates strike a ship, draws alongside it while
 * the band boards, and flies off when the fight is over. It is scenery with a voice: it cannot be hit, carries nobody,
 * never touches a block, is never saved (an unloaded raider is simply gone) and removes itself when its ship is gone, after
 * {@link #LIFETIME} ticks, or when told to flee. Drawn by the client with the {@code perahu_bajak} hull.
 */
public class KapalBajakEntity extends Entity {

	public static final byte PHASE_APPROACH = 0;
	public static final byte PHASE_HOLD = 1;
	public static final byte PHASE_FLEE = 2;
	/** Ticks it lives at most (5 minutes). */
	public static final int LIFETIME = 6000;
	private static final double APPROACH_SPEED = 0.7D;
	private static final double FLEE_SPEED = 0.9D;
	private static final int FLEE_TICKS = 140;

	private static final EntityDataAccessor<Integer> DATA_SHIP = SynchedEntityData.defineId(KapalBajakEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(KapalBajakEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(KapalBajakEntity.class, EntityDataSerializers.FLOAT);

	private int side = 1;
	private int fleeTicks;
	private boolean announced;
	/**
	 * Sekte Langit (DESIGN 37.7.4/37.7.5, module 37G): a raider that comes for a sky island instead of a ship lies at this
	 * fixed berth beside the island's dock or rim (null: the ship mode above).
	 */
	private @Nullable Vec3 fixedBerth;
	private float fixedYaw;

	public KapalBajakEntity(EntityType<? extends KapalBajakEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SHIP, -1);
		builder.define(DATA_PHASE, PHASE_APPROACH);
		builder.define(DATA_SPEED, 0.0F);
	}

	/** Called once before {@code addFreshEntity}: it comes for {@code ship} from {@code side} (+1 starboard, -1 port). */
	void setup(KapalRohEntity ship, int side) {
		entityData.set(DATA_SHIP, ship.getId());
		this.side = side >= 0 ? 1 : -1;
	}

	/**
	 * Sekte Langit (module 37G): called once before {@code addFreshEntity}, the raider comes for a sky island and lies at
	 * {@code berth} (facing {@code yaw}) until {@link #leave} or its lifetime ends. Scenery only, as in the ship mode.
	 */
	public void berthAt(Vec3 berth, float yaw) {
		this.fixedBerth = berth;
		this.fixedYaw = yaw;
	}

	/** Sekte Langit (module 37G): the island fight is over, the raider turns away and leaves. */
	public void leave() {
		flee();
	}

	public @Nullable KapalRohEntity ship() {
		return level().getEntity(entityData.get(DATA_SHIP)) instanceof KapalRohEntity ship ? ship : null;
	}

	public byte phase() {
		return entityData.get(DATA_PHASE);
	}

	/** Speed in blocks per tick (the sails belly with it). */
	public float speed() {
		return entityData.get(DATA_SPEED);
	}

	/** True once it lies alongside its ship. */
	public boolean alongside() {
		return phase() == PHASE_HOLD;
	}

	/** The fight is over: it turns away and leaves. */
	void flee() {
		if (phase() != PHASE_FLEE) {
			entityData.set(DATA_PHASE, PHASE_FLEE);
			fleeTicks = 0;
		}
	}

	@Override
	public void tick() {
		if (!(level() instanceof ServerLevel)) {
			return;
		}
		if (tickCount > LIFETIME) {
			discard();
			return;
		}
		KapalRohEntity ship = ship();
		if ((ship == null || ship.isRemoved()) && fixedBerth == null) {
			flee();
		}
		Vec3 here = position();
		Vec3 next;
		if (fixedBerth != null && phase() != PHASE_FLEE) {
			next = towardBerth(here);
		} else if (phase() == PHASE_FLEE) {
			fleeTicks++;
			Vec3 away = ship == null ? Vec3.directionFromRotation(0.0F, getYRot()) : here.subtract(ship.position()).multiply(1.0D, 0.0D, 1.0D);
			if (away.lengthSqr() < 1.0E-4D) {
				away = new Vec3(1.0D, 0.0D, 0.0D);
			}
			away = away.normalize();
			double accel = Math.min(1.0D, fleeTicks / 30.0D);
			next = here.add(away.x * FLEE_SPEED * accel, 0.25D * accel, away.z * FLEE_SPEED * accel);
			if (fleeTicks > FLEE_TICKS) {
				discard();
				return;
			}
		} else if (ship != null) {
			Vec3 berth = berth(ship);
			Vec3 delta = berth.subtract(here);
			double dist = delta.length();
			if (phase() == PHASE_APPROACH && dist < 1.5D) {
				entityData.set(DATA_PHASE, PHASE_HOLD);
			}
			if (phase() == PHASE_HOLD) {
				// lie alongside: follow the berth closely (the ship may still be settling)
				next = here.add(delta.scale(Math.min(1.0D, 0.25D)));
				if (!announced && dist < 4.0D) {
					announced = true;
					KapalVoice.say(this, KapalVoice.SKY_PIRATES, "rogue.ambush");
				}
			} else {
				double step = Math.min(dist, APPROACH_SPEED * Math.min(1.0D, 0.35D + dist / 24.0D));
				next = dist < 1.0E-4D ? here : here.add(delta.scale(step / dist));
			}
		} else {
			next = here;
		}
		Vec3 move = next.subtract(here);
		double flat = Math.sqrt(move.x * move.x + move.z * move.z);
		if (flat > 0.02D) {
			float want = (float) (Mth.atan2(move.z, move.x) * Mth.RAD_TO_DEG) - 90.0F;
			setYRot(Mth.wrapDegrees(getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -6.0F, 6.0F)));
		} else if (ship != null && phase() == PHASE_HOLD) {
			// alongside: turn parallel to the ship
			setYRot(Mth.wrapDegrees(getYRot() + Mth.clamp(Mth.wrapDegrees(ship.getYRot() - getYRot()), -3.0F, 3.0F)));
		} else if (fixedBerth != null && phase() == PHASE_HOLD) {
			// beside the island: turn to the berth's heading
			setYRot(Mth.wrapDegrees(getYRot() + Mth.clamp(Mth.wrapDegrees(fixedYaw - getYRot()), -3.0F, 3.0F)));
		}
		float shown = (float) flat;
		if (Math.abs(entityData.get(DATA_SPEED) - shown) > 0.01F) {
			entityData.set(DATA_SPEED, shown);
		}
		if (move.lengthSqr() > 1.0E-6D) {
			setPos(next.x, Mth.clamp(next.y, level().getMinY() + 2.0D, level().getMaxY() - 16.0D), next.z);
		}
	}

	/** Sekte Langit: the step toward the fixed berth beside the island (approach, then hold there). */
	private Vec3 towardBerth(Vec3 here) {
		Vec3 delta = fixedBerth.subtract(here);
		double dist = delta.length();
		if (phase() == PHASE_APPROACH && dist < 1.5D) {
			entityData.set(DATA_PHASE, PHASE_HOLD);
		}
		if (phase() == PHASE_HOLD) {
			if (!announced) {
				announced = true;
				KapalVoice.say(this, KapalVoice.SKY_PIRATES, "rogue.ambush");
			}
			return here.add(delta.scale(0.25D));
		}
		double step = Math.min(dist, APPROACH_SPEED * Math.min(1.0D, 0.35D + dist / 24.0D));
		return dist < 1.0E-4D ? here : here.add(delta.scale(step / dist));
	}

	/** The point beside {@code ship} where the raider lies (its beam, a few blocks off, a little higher). */
	private Vec3 berth(KapalRohEntity ship) {
		double off = ship.kind().width() * 0.5D + KapalClass.PERAHU.width() * 0.5D + 2.5D;
		double[] o = KapalClass.toWorld(side * off, 0.0D, ship.getYRot());
		return new Vec3(ship.getX() + o[0], ship.getY() + 0.8D, ship.getZ() + o[1]);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256.0D * 256.0D;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
