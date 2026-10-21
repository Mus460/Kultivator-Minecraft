package dev.kultivator.dunia.reruntuhan;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * One moving or clickable part of a puzzle, seal or tower hall (DESIGN 34.0 rule W2): a plate of the Delapan Trigram, a
 * lamp of the Lima Unsur, a ring handle of the Peta Bintang, a pan of the Timbangan Qi, a hint stele, a seal talisman,
 * the Jantung Segel, a seal pillar, a band gate, a tower pad. A plain invisible entity (no gravity, no physics, never
 * hurt, never saved: {@code noSave} plus the {@code ALLOW_LOAD} refusal of {@link RGuard}); the client draws it with
 * Display entities ({@code client.dunia.reruntuhan.RScenery}) posed through {@code DisplayKey}. It never touches a block.
 * <p>
 * Synced: role, state (0 idle, 1 lit / done, 2 hint, 3 wrong flash, more by role), {@code arg} and {@code arg2} (glyph,
 * element, rotation, weight), the colour and the puzzle centre (rings, pans and glyph orientation). The server-only
 * {@link Listener} receives clicks.
 */
public class TekaPartEntity extends Entity {

	public static final int PLATE = 0;
	public static final int LAMP = 1;
	public static final int RING = 2;
	public static final int PAN = 3;
	public static final int STELE = 4;
	public static final int TALISMAN = 5;
	public static final int CORE = 6;
	public static final int PILLAR = 7;
	public static final int GATE = 8;
	public static final int EXIT = 9;
	public static final int PAD = 10;
	public static final int MIRROR = 11;
	public static final int ROLES = 12;

	public static final int IDLE = 0;
	public static final int LIT = 1;
	public static final int HINT = 2;
	public static final int WRONG = 3;
	public static final int LOCKED = 4;

	/** Tag every part carries (cleanup and the load refusal). */
	public static final String TAG = "kultivator.reruntuhan_part";

	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(TekaPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(TekaPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ARG = SynchedEntityData.defineId(TekaPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ARG2 = SynchedEntityData.defineId(TekaPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(TekaPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> DATA_CENTER = SynchedEntityData.defineId(TekaPartEntity.class, EntityDataSerializers.VECTOR3);

	/** Told when a player clicks (or hits) the part; server only, never saved. */
	@FunctionalInterface
	public interface Listener {
		void use(ServerPlayer player, TekaPartEntity part, boolean attack);
	}

	private @Nullable Listener listener;
	private final int defaultRole;
	private int age;

	public TekaPartEntity(EntityType<? extends TekaPartEntity> type, Level level) {
		this(type, level, PLATE);
	}

	protected TekaPartEntity(EntityType<? extends TekaPartEntity> type, Level level, int defaultRole) {
		super(type, level);
		this.defaultRole = defaultRole;
		this.noPhysics = true;
		this.setNoGravity(true);
		this.entityData.set(DATA_ROLE, defaultRole);
		this.refreshDimensions();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_ROLE, PLATE);
		builder.define(DATA_STATE, IDLE);
		builder.define(DATA_ARG, 0);
		builder.define(DATA_ARG2, 0);
		builder.define(DATA_COLOR, 0xFFFFFF);
		builder.define(DATA_CENTER, new Vector3f());
	}

	// ================================================================== synced values

	public int role() {
		return Mth.clamp(entityData.get(DATA_ROLE), 0, ROLES - 1);
	}

	public void setRole(int role) {
		entityData.set(DATA_ROLE, Mth.clamp(role, 0, ROLES - 1));
		refreshDimensions();
	}

	public int state() {
		return entityData.get(DATA_STATE);
	}

	public void setState(int state) {
		if (state() != state) {
			entityData.set(DATA_STATE, state);
		}
	}

	public int arg() {
		return entityData.get(DATA_ARG);
	}

	public void setArg(int arg) {
		if (arg() != arg) {
			entityData.set(DATA_ARG, arg);
		}
	}

	public int arg2() {
		return entityData.get(DATA_ARG2);
	}

	public void setArg2(int arg) {
		if (arg2() != arg) {
			entityData.set(DATA_ARG2, arg);
		}
	}

	public int color() {
		return entityData.get(DATA_COLOR);
	}

	public void setColor(int rgb) {
		if (color() != (rgb & 0xFFFFFF)) {
			entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
		}
	}

	/** The centre of the puzzle this part belongs to (world coordinates). */
	public Vec3 center() {
		Vector3fc c = entityData.get(DATA_CENTER);
		return new Vec3(c.x(), c.y(), c.z());
	}

	public void setCenter(Vec3 at) {
		entityData.set(DATA_CENTER, new Vector3f((float) at.x, (float) at.y, (float) at.z));
	}

	public void setListener(@Nullable Listener listener) {
		this.listener = listener;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ROLE.equals(accessor)) {
			refreshDimensions();
		}
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return switch (role()) {
			case PLATE -> EntityDimensions.scalable(1.0F, 0.2F);
			case LAMP -> EntityDimensions.scalable(0.7F, 0.9F);
			case RING -> EntityDimensions.scalable(0.9F, 0.9F);
			case PAN -> EntityDimensions.scalable(3.0F, 0.3F);
			case STELE -> EntityDimensions.scalable(1.1F, 2.5F);
			case TALISMAN -> EntityDimensions.scalable(0.8F, 1.4F);
			case CORE -> EntityDimensions.scalable(1.4F, 3.0F);
			case PILLAR -> EntityDimensions.scalable(1.3F, 4.0F);
			case GATE -> EntityDimensions.scalable(1.8F, 3.2F);
			case EXIT, PAD -> EntityDimensions.scalable(1.8F, 0.25F);
			case MIRROR -> EntityDimensions.scalable(0.4F, 4.0F);
			default -> EntityDimensions.scalable(0.8F, 0.8F);
		};
	}

	// ================================================================== behaviour

	@Override
	public void tick() {
		// Parts are moved and changed by their puzzle only; a stray part (its puzzle gone) leaves.
		age++;
		if (!this.level().isClientSide() && listener == null && age > 40) {
			this.discard();
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setRole(input.getIntOr("role", defaultRole));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("role", role());
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	/** Clickable roles (plates are stepped on, pans weighed, mirrors are scenery). */
	public boolean clickable() {
		int role = role();
		return role == LAMP || role == RING || role == STELE || role == TALISMAN || role == CORE || role == PILLAR || role == GATE
				|| role == EXIT || role == PAD;
	}

	@Override
	public boolean isPickable() {
		return clickable() && state() != LOCKED;
	}

	@Override
	public boolean canBeHitByProjectile() {
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
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		if (source instanceof ServerPlayer player && isPickable()) {
			fire(player, true);
		}
		return true;
	}

	@Override
	public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!isPickable()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
			fire(serverPlayer, false);
		}
		return InteractionResult.SUCCESS;
	}

	private void fire(ServerPlayer player, boolean attack) {
		Listener current = listener;
		if (current == null || player.isSpectator()) {
			return;
		}
		try {
			current.use(player, this, attack);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: a ruin part failed on use", exception);
		}
	}
}
