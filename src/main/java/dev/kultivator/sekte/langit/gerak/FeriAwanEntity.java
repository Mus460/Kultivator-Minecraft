package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code kultivator:feri_awan}, the Cloud Ferry of a sky sect (DESIGN 37.5.5, T7): a small jade-and-cinnabar skiff on a
 * bank of cloud with a tiled canopy and two lanterns, four seats. It is an entity only ({@code noPhysics}, no gravity,
 * moved by {@code setPos} along a {@link FeriRute} computed once per call) and is never saved; at most one per hall.
 * <ul>
 * <li><b>Call</b> ({@link #call}): a member rings the bell at a stop ({@link FeriPanggil}) or uses
 * {@code /kultivasi langit feri} (37G). It appears at that stop (or, idle and empty at the other stop, flies over to
 * fetch the caller) and waits 30 s.</li>
 * <li><b>Board</b>: only members of the hall and their disciples (right-click). FOLLOW disciples of a riding player
 * within 6 blocks board with them when it leaves; it leaves 5 s after the first rider sat down.</li>
 * <li><b>Fly</b>: 0.6 blocks a tick (A9 about 17 s), paused while the server is over {@code sectBuildMsptPause}; a
 * destination out of view is loaded by a short {@code DuniaTickets} ticket. A player who jumps off (shift) gets Slow
 * Falling for 12 s like a ship, then the Cloud Net; a disciple cannot get off midway.</li>
 * <li><b>Arrive</b>: riders step off onto the stop, a cloud puff ({@code FX_FERI}), a chime; it waits 20 s empty, then
 * dissolves.</li>
 * </ul>
 * Busy players ({@code Sibuk.why}) are refused at boarding. No block is touched.
 */
public class FeriAwanEntity extends Entity {

	public static final float WIDTH = 3.0F;
	public static final float HEIGHT = 1.2F;
	public static final int SEATS = 4;
	public static final double SPEED = 0.6D;
	public static final int WAIT_TICKS = 600;
	public static final int EMPTY_TICKS = 400;
	public static final int DEPART_AFTER_BOARD = 100;
	public static final double DISCIPLE_RANGE = 6.0D;
	/** Deck height of a seat over the entity origin. */
	public static final double SEAT_Y = 0.62D;
	/** Seat offsets (x, z) in the ferry's frame, +z forward. */
	private static final double[][] SEAT = {{-0.55D, 0.75D}, {0.55D, 0.75D}, {-0.55D, -0.55D}, {0.55D, -0.55D}};
	/** Degrees the hull may turn per tick. */
	private static final float TURN = 6.0F;

	public static final byte PHASE_WAIT = 0;
	public static final byte PHASE_TRAVEL = 1;
	public static final byte PHASE_FADE = 2;

	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(FeriAwanEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(FeriAwanEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(FeriAwanEntity.class, EntityDataSerializers.INT);

	/** The live ferry of each hall (server thread). */
	private static final Map<UUID, FeriAwanEntity> LIVE = new ConcurrentHashMap<>();

	// ---- server (session; never saved)
	private @Nullable UUID owner;
	private @Nullable FeriRute route;
	/** True while the ferry stands at the sky stop, or flies down from it. */
	private boolean top;
	private double travelled;
	private int waitLeft = WAIT_TICKS;
	private long boardedAt = -1L;
	private int fade;
	private final Set<UUID> riders = new HashSet<>();

	public FeriAwanEntity(EntityType<? extends FeriAwanEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_PHASE, PHASE_WAIT);
		builder.define(DATA_SPEED, 0.0F);
		builder.define(DATA_COLOR, 0x3FAE84);
	}

	// ================================================================== synced readers

	public byte phase() {
		return entityData.get(DATA_PHASE);
	}

	/** Blocks per tick along the route (0 while waiting): sway and cloud trail on the client. */
	public float speed() {
		return entityData.get(DATA_SPEED);
	}

	/** The jade lacquer tint (0xRRGGBB). */
	public int color() {
		return entityData.get(DATA_COLOR);
	}

	// ================================================================== calling

	/**
	 * Calls {@code owner}'s ferry to the ground stop ({@code up}: the caller wants to go up) or the sky stop. Null when
	 * the ferry is coming or waiting, else why not.
	 */
	public static @Nullable Component call(ServerLevel level, UUID owner, boolean up) {
		LangitConfig cfg = LangitConfig.get();
		if (!LangitConfig.on() || !cfg.feri) {
			return Component.translatable("kultivator.sekte_langit.feri.mati");
		}
		SkyHallView hall = LangitGerak.hallIn(level, owner);
		if (hall == null || hall.tethered()) {
			return Component.translatable("kultivator.sekte_langit.feri.bukan_langit");
		}
		if (hall.tier() < FeriPanggil.MIN_TIER) {
			return Component.translatable("kultivator.sekte_langit.feri.tingkat", FeriPanggil.MIN_TIER);
		}
		BlockPos topStop = LangitGerak.anchor(owner, FeriPanggil.TOP);
		BlockPos bottomStop = FeriPanggil.bottomStop(owner);
		if (topStop == null || bottomStop == null) {
			return Component.translatable("kultivator.sekte_langit.feri.halte");
		}
		if (LangitGerak.busy(level.getServer())) {
			return Component.translatable("kultivator.sekte_langit.feri.server_sibuk");
		}
		FeriRute rute = FeriRute.plan(hall, bottomStop, topStop, level.getMaxY());
		if (rute == null) {
			return Component.translatable("kultivator.sekte_langit.feri.rute");
		}
		boolean wantTop = !up;
		FeriAwanEntity live = LIVE.get(owner);
		if (live != null && (live.isRemoved() || live.level() != level)) {
			LIVE.remove(owner);
			live = null;
		}
		if (live != null) {
			return live.recall(rute, wantTop);
		}
		Vec3 stop = wantTop ? rute.end() : rute.start();
		BlockPos at = BlockPos.containing(stop);
		if (!level.isLoaded(at)) {
			DuniaTickets.touch(level, ChunkPos.containing(at), 1);
			return Component.translatable("kultivator.sekte_langit.feri.muat");
		}
		FeriAwanEntity ferry = new FeriAwanEntity(LangitGerakEntities.FERI, level);
		ferry.owner = owner;
		ferry.route = rute;
		ferry.top = wantTop;
		ferry.travelled = 0.0D;
		ferry.waitLeft = WAIT_TICKS;
		ferry.setPos(stop.x, stop.y, stop.z);
		ferry.setYRot(wantTop ? rute.yawAt(rute.length()) : 0.0F);
		if (Float.isNaN(ferry.getYRot())) {
			ferry.setYRot(0.0F);
		}
		if (!level.addFreshEntity(ferry)) {
			return Component.translatable("kultivator.sekte_langit.feri.rute");
		}
		LIVE.put(owner, ferry);
		LangitGerak.fx(level, LangitIds.FX_FERI, at, at);
		level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.9F);
		return null;
	}

	/** The hall's ferry was called again: wait longer where it is, or come over empty. */
	private @Nullable Component recall(FeriRute rute, boolean wantTop) {
		this.route = rute;
		if (phase() == PHASE_TRAVEL) {
			return Component.translatable("kultivator.sekte_langit.feri.jalan");
		}
		if (phase() == PHASE_FADE) {
			setPhase(PHASE_WAIT);
			fade = 0;
		}
		if (top == wantTop) {
			waitLeft = WAIT_TICKS;
			return null;
		}
		if (!getPassengers().isEmpty()) {
			return Component.translatable("kultivator.sekte_langit.feri.terisi");
		}
		depart();
		return null;
	}

	/** True while one of {@code master}'s disciples sits on a ferry (follow veto). */
	public static boolean carriesDiscipleOf(UUID master) {
		for (FeriAwanEntity ferry : LIVE.values()) {
			for (Entity rider : ferry.getPassengers()) {
				if (rider instanceof DiscipleEntity disciple && master.equals(disciple.ownerId())) {
					return true;
				}
			}
		}
		return false;
	}

	/** {@code owner}'s live ferry, or null. */
	public static @Nullable FeriAwanEntity of(UUID owner) {
		FeriAwanEntity ferry = LIVE.get(owner);
		return ferry == null || ferry.isRemoved() ? null : ferry;
	}

	static void clearAll() {
		LIVE.clear();
	}

	// ================================================================== boarding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level() instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isPassenger() && player.getVehicle() == this) {
			return InteractionResult.PASS;
		}
		Component why = boardRefusal(serverPlayer);
		if (why != null) {
			Msg.actionBar(serverPlayer, why.copy().withColor(0xFF9A7A));
			return InteractionResult.SUCCESS;
		}
		if (serverPlayer.startRiding(this)) {
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.sekte_langit.feri.naik", Math.max(1, (DEPART_AFTER_BOARD + 19) / 20))
					.withColor(0xBFE6FF));
		}
		return InteractionResult.SUCCESS;
	}

	private @Nullable Component boardRefusal(ServerPlayer player) {
		if (owner == null || !LangitGerak.member(owner, player)) {
			return Component.translatable("kultivator.sekte_langit.feri.bukan_anggota");
		}
		if (phase() != PHASE_WAIT && phase() != PHASE_FADE) {
			return Component.translatable("kultivator.sekte_langit.feri.jalan");
		}
		if (getPassengers().size() >= SEATS) {
			return Component.translatable("kultivator.sekte_langit.feri.penuh", SEATS);
		}
		String busy = Sibuk.why(player);
		if (busy != null) {
			return Component.translatable("kultivator.sekte_langit.feri.sibuk", Sibuk.text(busy));
		}
		return null;
	}

	/**
	 * Seats {@code rider} (37G: a disciple leaving on a mission rides along for the look). False when the ferry is full,
	 * flying, or the rider is not of the hall.
	 */
	public boolean board(Entity rider) {
		if (owner == null || phase() == PHASE_TRAVEL || getPassengers().size() >= SEATS || rider.isPassenger()
				|| !LangitGerak.member(owner, rider)) {
			return false;
		}
		return rider.startRiding(this, true, true);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < SEATS;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!level().isClientSide()) {
			if (passenger instanceof DiscipleEntity) {
				riders.add(passenger.getUUID());
			}
			if (boardedAt < 0L) {
				boardedAt = level().getGameTime();
			}
			if (phase() == PHASE_FADE) {
				setPhase(PHASE_WAIT);
				fade = 0;
			}
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (level().isClientSide() || phase() != PHASE_TRAVEL || isRemoved()) {
			return;
		}
		if (passenger instanceof ServerPlayer player) {
			// jumping off the ferry in flight: a soft fall, then the Cloud Net
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0, false, true));
			Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.feri.lompat").withColor(0xB4E2FF));
		}
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		int index = getPassengers().indexOf(passenger);
		if (index < 0 || index >= SEAT.length) {
			super.positionRider(passenger, moveFunction);
			return;
		}
		double yaw = Math.toRadians(getYRot());
		double sx = SEAT[index][0];
		double sz = SEAT[index][1];
		double x = sx * Math.cos(yaw) - sz * Math.sin(yaw);
		double z = sx * Math.sin(yaw) + sz * Math.cos(yaw);
		Vec3 offset = passenger.getVehicleAttachmentPoint(this);
		moveFunction.accept(passenger, getX() + x - offset.x, getY() + SEAT_Y - offset.y, getZ() + z - offset.z);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		if (phase() == PHASE_TRAVEL) {
			return passenger.position();
		}
		// at a stop: step down onto the stop's floor where the seat is
		return new Vec3(passenger.getX(), getY() - FeriRute.HOVER + 0.02D, passenger.getZ());
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	// ================================================================== ticking

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (owner == null || route == null || LIVE.get(owner) != this && LIVE.containsKey(owner)) {
			// removePassenger skips its soft fall once removed: riders mid-air get it here.
			for (Entity passenger : getPassengers()) {
				if (passenger instanceof LivingEntity living) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0, false, true));
				}
			}
			discard();
			return;
		}
		LIVE.putIfAbsent(owner, this);
		switch (phase()) {
			case PHASE_TRAVEL -> tickTravel(level);
			case PHASE_FADE -> tickFade(level);
			default -> tickWait(level);
		}
	}

	private void tickWait(ServerLevel level) {
		FeriRute rute = route;
		Vec3 stop = top ? rute.end() : rute.start();
		if (position().distanceToSqr(stop) > 1.0E-4D) {
			setPos(stop.x, stop.y, stop.z);
		}
		setSpeed(0.0F);
		if (!getPassengers().isEmpty()) {
			if (boardedAt >= 0L && level.getGameTime() - boardedAt >= DEPART_AFTER_BOARD && !LangitGerak.busy(level.getServer())) {
				boardFollowers(level);
				depart();
			}
			return;
		}
		boardedAt = -1L;
		if (--waitLeft <= 0) {
			setPhase(PHASE_FADE);
			fade = 0;
		}
	}

	/** FOLLOW disciples of the riding players come aboard as it leaves (free seats only). */
	private void boardFollowers(ServerLevel level) {
		List<ServerPlayer> players = new ArrayList<>();
		for (Entity rider : getPassengers()) {
			if (rider instanceof ServerPlayer player) {
				players.add(player);
			}
		}
		for (ServerPlayer player : players) {
			for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
				if (getPassengers().size() >= SEATS) {
					return;
				}
				if (disciple.isAlive() && disciple.level() == level && !disciple.isPassenger() && disciple.mode().follows()
						&& disciple.distanceToSqr(this) <= DISCIPLE_RANGE * DISCIPLE_RANGE) {
					disciple.getNavigation().stop();
					disciple.startRiding(this, true, true);
				}
			}
		}
	}

	private void depart() {
		travelled = 0.0D;
		boardedAt = -1L;
		setPhase(PHASE_TRAVEL);
		if (level() instanceof ServerLevel level) {
			BlockPos at = blockPosition();
			LangitGerak.fx(level, LangitIds.FX_FERI, at, at);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.3F);
		}
	}

	private void tickTravel(ServerLevel level) {
		FeriRute rute = route;
		if (LangitGerak.busy(level.getServer())) {
			setSpeed(0.0F);
			return;
		}
		double length = rute.length();
		double next = Math.min(length, travelled + SPEED);
		// upward runs the route forwards (ground stop to sky stop), downward backwards
		double s = top ? length - next : next;
		Vec3 target = rute.at(s);
		BlockPos ahead = BlockPos.containing(target);
		if (!level.isLoaded(ahead)) {
			DuniaTickets.touch(level, ChunkPos.containing(ahead), 1);
			setSpeed(0.0F);
			return;
		}
		float heading = rute.yawAt(s);
		if (!Float.isNaN(heading)) {
			if (top) {
				heading = Mth.wrapDegrees(heading + 180.0F);
			}
			float err = Mth.wrapDegrees(heading - getYRot());
			setYRot(Mth.wrapDegrees(getYRot() + Mth.clamp(err, -TURN, TURN)));
		}
		travelled = next;
		setPos(target.x, target.y, target.z);
		setSpeed((float) SPEED);
		keepRiders(level);
		if (travelled >= length - 1.0E-6D) {
			arrive(level);
		}
	}

	/** A disciple that slipped off midway (a follow teleport, a push) is seated again when still close. */
	private void keepRiders(ServerLevel level) {
		if (riders.isEmpty() || (tickCount & 7) != 0) {
			return;
		}
		for (UUID id : new ArrayList<>(riders)) {
			if (!(level.getEntity(id) instanceof DiscipleEntity disciple) || !disciple.isAlive()) {
				riders.remove(id);
				continue;
			}
			if (disciple.getVehicle() != this && !disciple.isPassenger() && disciple.distanceToSqr(this) <= 64.0D
					&& getPassengers().size() < SEATS) {
				disciple.startRiding(this, true, true);
			}
		}
	}

	private void arrive(ServerLevel level) {
		top = !top;
		setSpeed(0.0F);
		setPhase(PHASE_WAIT);
		waitLeft = EMPTY_TICKS;
		boardedAt = -1L;
		riders.clear();
		BlockPos at = blockPosition();
		LangitGerak.fx(level, LangitIds.FX_FERI, at, at);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.9F);
		for (Entity rider : new ArrayList<>(getPassengers())) {
			if (rider instanceof ServerPlayer player) {
				Advancements.grant(player, LangitIds.ADV + "feri");
				Msg.actionBar(player, Component.translatable(top ? "kultivator.sekte_langit.feri.tiba_atas" : "kultivator.sekte_langit.feri.tiba_bawah")
						.withColor(0xBFE6FF));
			}
			rider.stopRiding();
			rider.resetFallDistance();
		}
	}

	private void tickFade(ServerLevel level) {
		setSpeed(0.0F);
		if (++fade >= 20) {
			BlockPos at = blockPosition();
			LangitGerak.fx(level, LangitIds.FX_FERI, at, at);
			discard();
		}
	}

	private void setPhase(byte phase) {
		if (entityData.get(DATA_PHASE) != phase) {
			entityData.set(DATA_PHASE, phase);
		}
	}

	private void setSpeed(float speed) {
		if (Math.abs(entityData.get(DATA_SPEED) - speed) > 0.01F) {
			entityData.set(DATA_SPEED, speed);
		}
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		if (owner != null && LIVE.get(owner) == this) {
			LIVE.remove(owner);
		}
		super.onRemoval(reason);
	}

	// ================================================================== entity plumbing

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
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
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean skipAttackInteraction(Entity source) {
		return true;
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
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 192.0D * 192.0D;
	}

	/** Never saved: a ferry left in an unloaded chunk is simply gone (it is called again with the bell). */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
