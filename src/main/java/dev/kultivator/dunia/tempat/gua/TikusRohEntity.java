package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code kultivator:tikus_roh} (DESIGN 38.O.2, 38.S.4 quest {@code tikus_dapur}): a spirit rat the size of a puppy with
 * glowing eyes. Harmless and cosmetic: it never attacks, never breaks or eats a block (no block goals at all, unlike a
 * silverfish), never breeds. It flees players in zig-zags (a new sideways jink every {@link #JINK_TICKS} ticks) and
 * squeaks; two hits end it, leaving only a puff. Never saved: whoever releases rats ({@link #lepas}) re-makes them;
 * a rat alone for {@link #ALONE_TICKS} ticks without a player within 32 blocks scurries off (discarded).
 */
public class TikusRohEntity extends PathfinderMob {

	public static final int JINK_TICKS = 10;
	public static final int ALONE_TICKS = 600;

	private int alone;

	public TikusRohEntity(EntityType<? extends TikusRohEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0D).add(Attributes.MOVEMENT_SPEED, 0.35D);
	}

	/** Releases {@code n} rats (1-6) around {@code at}; returns those that entered the level. */
	public static List<TikusRohEntity> lepas(ServerLevel level, Vec3 at, int n) {
		List<TikusRohEntity> out = new ArrayList<>();
		int count = Mth.clamp(n, 1, 6);
		for (int i = 0; i < count; i++) {
			TikusRohEntity rat = GuaEntities.TIKUS_ROH.create(level, EntitySpawnReason.EVENT);
			if (rat == null) {
				continue;
			}
			double a = level.getRandom().nextDouble() * Math.PI * 2.0D;
			rat.snapTo(at.x + Math.cos(a) * 1.5D, at.y, at.z + Math.sin(a) * 1.5D, (float) Math.toDegrees(a), 0.0F);
			rat.addTag(NoGrief.TAG);
			if (level.addFreshEntity(rat)) {
				out.add(rat);
			}
		}
		return out;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6D));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.2D, 1.6D));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// the zig-zag: while fleeing, a sideways jink now and then
		if (this.tickCount % JINK_TICKS == 0 && this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
			Vec3 v = this.getDeltaMovement();
			double side = this.random.nextBoolean() ? 0.25D : -0.25D;
			this.push(-v.z * side * 4.0D, 0.05D, v.x * side * 4.0D);
		}
		if (this.tickCount % 40 == 0) {
			if (level.getNearestPlayer(this, 32.0D) == null) {
				alone += 40;
				if (alone >= ALONE_TICKS) {
					this.discard();
				}
			} else {
				alone = 0;
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2D, getZ(), 6, 0.2D, 0.1D, 0.2D, 0.02D);
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.6F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.4F);
	}
}
