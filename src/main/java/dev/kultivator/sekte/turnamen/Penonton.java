package dev.kultivator.sekte.turnamen;

import dev.kultivator.util.NoGrief;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A visiting spectator of the sect tournament (DESIGN 36.2.3 "penonton"): an envoy of an allied NPC sect in its sect's
 * colours, or a villager of a vassal village in homespun. A light body: no goals, no walking, never pushed, never hurt
 * (only a creative kill or the void removes it), never saved ({@code noSave}); the tournament spawns it on the tribune
 * and removes it at the end. It only turns its head toward the fighters (every 5 ticks) and cheers every 4 s while a
 * bout is fought (the client raises its arms); the client stops animating it beyond 32 blocks and stops drawing it beyond
 * 64 ({@code clientTrackingRange} 4 chunks).
 */
public class Penonton extends PathfinderMob {

	public static final int KIND_ENVOY = 0;
	public static final int KIND_VILLAGER = 1;
	public static final String TAG = "kultivator.penonton";
	public static final int CHEER_EVERY = 80;
	public static final int LOOK_EVERY = 5;

	/** kind (bits 0-1), skin 0-8 (bits 2-5), slim (bit 6), hat (bit 7). */
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(Penonton.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ROBE = SynchedEntityData.defineId(Penonton.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TRIM = SynchedEntityData.defineId(Penonton.class, EntityDataSerializers.INT);
	/** Counts the cheers; the client starts the cheering pose when it changes. */
	private static final EntityDataAccessor<Integer> DATA_CHEER = SynchedEntityData.defineId(Penonton.class, EntityDataSerializers.INT);

	private @Nullable Vec3 focus;
	private @Nullable Entity focusA;
	private @Nullable Entity focusB;
	private boolean excited;
	private int phase;
	private long life;

	public Penonton(EntityType<? extends Penonton> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setCanPickUpLoot(false);
		this.setPersistenceRequired();
		this.addTag(NoGrief.TAG);
		this.addTag(TAG);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.0D)
				.add(Attributes.FOLLOW_RANGE, 8.0D);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LOOK, 0);
		builder.define(DATA_ROBE, 0x6E4AA8);
		builder.define(DATA_TRIM, 0xE2B33C);
		builder.define(DATA_CHEER, 0);
	}

	@Override
	protected void registerGoals() {
		// None: a spectator only turns its head (tick) and never walks.
	}

	// ================================================================== looks (synced)

	/** Sets the look: {@code kind} {@link #KIND_ENVOY} or {@link #KIND_VILLAGER}, a default skin 0-8, robe and trim colours. */
	public void setLook(int kind, int skin, boolean slim, boolean hat, int robe, int trim) {
		int packed = (kind & 3) | (Math.floorMod(skin, 9) & 15) << 2 | (slim ? 1 << 6 : 0) | (hat ? 1 << 7 : 0);
		this.entityData.set(DATA_LOOK, packed);
		this.entityData.set(DATA_ROBE, robe & 0xFFFFFF);
		this.entityData.set(DATA_TRIM, trim & 0xFFFFFF);
	}

	public int kind() {
		return this.entityData.get(DATA_LOOK) & 3;
	}

	public int skin() {
		return this.entityData.get(DATA_LOOK) >> 2 & 15;
	}

	public boolean slim() {
		return (this.entityData.get(DATA_LOOK) & 1 << 6) != 0;
	}

	public boolean hat() {
		return (this.entityData.get(DATA_LOOK) & 1 << 7) != 0;
	}

	public int robe() {
		return this.entityData.get(DATA_ROBE);
	}

	public int trim() {
		return this.entityData.get(DATA_TRIM);
	}

	public int cheers() {
		return this.entityData.get(DATA_CHEER);
	}

	// ================================================================== behaviour (server)

	/** What to watch: the stage centre and, while a bout is fought, its two fighters (they make the crowd cheer). */
	void watch(Vec3 centre, @Nullable Entity a, @Nullable Entity b) {
		this.focus = centre;
		this.focusA = a;
		this.focusB = b;
		this.excited = a != null && b != null;
	}

	/** One cheer now (a decided bout, a champion). */
	void cheer() {
		this.entityData.set(DATA_CHEER, this.entityData.get(DATA_CHEER) + 1);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel)) {
			return;
		}
		life++;
		if (phase == 0) {
			phase = 1 + Math.floorMod(this.getId(), CHEER_EVERY);
		}
		if ((life + phase) % LOOK_EVERY == 0) {
			Vec3 at = target();
			if (at != null) {
				this.getLookControl().setLookAt(at.x, at.y, at.z, 20.0F, 30.0F);
				if (life < 40) {
					// Face the stage once with the whole body; afterwards only the head follows.
					float yaw = (float) (Math.toDegrees(Math.atan2(at.z - this.getZ(), at.x - this.getX())) - 90.0D);
					this.setYRot(yaw);
					this.setYBodyRot(yaw);
				}
			}
		}
		if (excited && (life + phase) % CHEER_EVERY == 0) {
			cheer();
		}
	}

	private @Nullable Vec3 target() {
		Entity a = focusA != null && focusA.isAlive() ? focusA : null;
		Entity b = focusB != null && focusB.isAlive() ? focusB : null;
		if (a != null && b != null) {
			// The middle of the fight, a little toward whoever is nearer this spectator's side.
			Vec3 mid = a.position().add(b.position()).scale(0.5D);
			return mid.add(0.0D, 1.2D, 0.0D);
		}
		return focus == null ? null : focus.add(0.0D, 1.2D, 0.0D);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
		// Never pushes or is pushed: it stands on its seat.
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && !this.level().isClientSide()) {
			// A short greeting: who this is and where from.
			player.sendOverlayMessage(Component.translatable(kind() == KIND_ENVOY ? "kultivator.sekte.turnamen.penonton.salam_utusan"
					: "kultivator.sekte.turnamen.penonton.salam_warga", this.getDisplayName()));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onBelowWorld() {
		this.discard();
	}
}
