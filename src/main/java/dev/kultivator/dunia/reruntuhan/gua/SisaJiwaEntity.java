package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Sisa Jiwa Leluhur, the ancestor's remnant soul of an Aula Warisan (DESIGN 34.5.5): a translucent elder on the
 * disciple's player model (a fixed elder skin by the ruin's seed, a Dao-coloured robe), standing before its own seated
 * bones. It never moves, never fights and cannot be hurt; it turns to whoever is near and speaks the existing
 * {@code elder.*} voice lines. A click goes to its {@link TekaPartEntity.Listener} (the inheritance of {@link HallRun}).
 * Never saved: it appears again from the anchor when a player comes near.
 */
public class SisaJiwaEntity extends PathfinderMob {

	public static final String TAG = "kultivator.reruntuhan_soul";

	private static final EntityDataAccessor<Integer> DATA_SKIN = SynchedEntityData.defineId(SisaJiwaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ROBE = SynchedEntityData.defineId(SisaJiwaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(SisaJiwaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GLOW = SynchedEntityData.defineId(SisaJiwaEntity.class, EntityDataSerializers.INT);

	private TekaPartEntity.@Nullable Listener listener;
	private int alone;

	public SisaJiwaEntity(EntityType<? extends SisaJiwaEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setPermanentlyInvulnerable(true);
		this.setNoGravity(true);
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SKIN, 0);
		builder.define(DATA_ROBE, 0xC8B8FF);
		builder.define(DATA_TIER, 1);
		builder.define(DATA_GLOW, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F));
	}

	public void setup(int skin, int robe, int tier) {
		entityData.set(DATA_SKIN, skin);
		entityData.set(DATA_ROBE, robe & 0xFFFFFF);
		entityData.set(DATA_TIER, tier);
	}

	public int skin() {
		return entityData.get(DATA_SKIN);
	}

	public int robe() {
		return entityData.get(DATA_ROBE);
	}

	public int tier() {
		return entityData.get(DATA_TIER);
	}

	/** 0 calm, 1 speaking (a soft glow), 2 granting the inheritance (a bright glow). */
	public int glow() {
		return entityData.get(DATA_GLOW);
	}

	public void setGlow(int glow) {
		if (glow() != glow) {
			entityData.set(DATA_GLOW, glow);
		}
	}

	public void setListener(TekaPartEntity.@Nullable Listener listener) {
		this.listener = listener;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 0.0D, 0.0D));
		if (listener == null && ++alone > 40) {
			this.discard();
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && listener != null) {
			try {
				listener.use(serverPlayer, null, false);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: the remnant soul failed to speak", exception);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.kultivator.sisa_jiwa.tier." + Math.max(1, Math.min(3, tier())));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public void setCustomName(@Nullable Component name) {
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}
}
