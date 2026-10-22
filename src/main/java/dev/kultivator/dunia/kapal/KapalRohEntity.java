package dev.kultivator.dunia.kapal;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code kultivator:kapal_roh}, the flying ship (DESIGN 34.5.15, rule W2: an entity, never blocks). The server moves it;
 * passengers ride it directly in synced seats (seat 0 is the helm: its rider is the captain when they own the ship or are
 * the owner's sect mate). It never collides with or changes a block: the terrain is only read (heightmaps of loaded
 * chunks) to keep it above the ground. Docked (hovering at most 3 blocks above ground or water and still for 2 s) it turns
 * to the nearest quarter and puts out deck colliders ({@link KapalDekEntity}) so players walk on its deck; in flight every
 * player aboard is seated.
 * <p>
 * Long voyages fly by autopilot at cruise height (terrain + 40, at least y 180) and, beyond 1 500 blocks, make a cloud
 * leap ({@code leapDistance}) every 30 s behind a client cloud wipe, after a short {@link DuniaTickets} ticket loaded the
 * landing chunk. Fuel burns from the 9-slot hold ({@link KapalFuel}); the hull ({@code 200 / 400 / 800}) takes damage from
 * explosions, projectiles and boarding sky pirates; at 0 the ship sets down as Rusak until repaired. FOLLOW disciples of
 * the captain fly along: they depart from the world when it lifts off ({@link Disciples#depart}) and arrive again when it
 * docks, so a voyage never strands them. Nothing here references a client class.
 * <p>
 * Detail pass: a hull livery ({@link KapalStyle}, chosen at the helm, some earned) and a given name (the nameplate) ride
 * with the ship and its keel; module L's sky touches it ({@link KapalWeather}: spirit weather under the hull changes fuel
 * and speed, a blood eclipse emboldens the sky pirates, twin moons cheapen a cloud leap, a meteor shower may drop a Batu
 * Meteor Roh into the cloud net); the sky pirates come in a raider ({@link KapalBajakEntity}) that draws alongside while
 * the ship sets down, board once it is low, and leave spoils when driven off; the helmsman speaks the ship's moments
 * ({@link KapalVoice}).
 */
public class KapalRohEntity extends Entity implements HasCustomInventoryScreen {

	public static final int DEFAULT_COLOR = 0x3FAE84;
	/** The banner colours of the helm (jade, sect red, gold, azure, violet, white, ink, peach). */
	public static final int[] COLORS = {0x3FAE84, 0xB3281D, 0xD4A637, 0x3C7FD6, 0x8E5BD8, 0xE8E6DA, 0x2A2F35, 0xE88E7A};

	public static final byte STATE_IDLE = 0;
	public static final byte STATE_FLY = 1;
	public static final byte STATE_AUTO = 2;
	public static final byte STATE_LEAP = 3;
	public static final byte STATE_BROKEN = 4;
	public static final byte STATE_HALT = 5;

	public static final int OP_INFO = 0;
	public static final int OP_STOW = 1;
	public static final int OP_REPAIR = 2;
	public static final int OP_HOLD = 3;
	public static final int OP_COLOR = 4;
	public static final int OP_SAIL = 5;
	public static final int OP_STOP = 6;
	public static final int OP_ROUTE = 7;
	public static final int OP_HALL = 8;
	public static final int OP_STYLE = 9;
	public static final int OP_RENAME = 10;
	/** Longest ship name. */
	public static final int NAME_MAX = 24;

	/** Docked: at most this high above the ground, still for {@link #DOCK_TICKS}. */
	public static final double DOCK_HEIGHT = 3.2D;
	public static final int DOCK_TICKS = 40;
	/** The hover of a docked ship above its ground. */
	public static final double HOVER = 1.2D;
	/** Lowest clearance in flight. */
	public static final double CLEARANCE = 2.5D;
	public static final int CRUISE_ABOVE = 40;
	public static final int CRUISE_MIN_Y = 180;
	public static final double LEAP_FROM = 1500.0D;
	public static final int LEAP_EVERY = 600;
	public static final int LEAP_WAIT = 40;
	public static final double ARRIVE_RANGE = 24.0D;
	public static final double PIRATE_VOYAGE = 2000.0D;
	public static final double VOYAGE_ADVANCEMENT = 3000.0D;
	/** The planks and Mid stones a quarter of the hull costs to repair. */
	public static final int REPAIR_PLANKS = 8;
	private static final int INPUT_TIMEOUT = 20;
	private static final double ACCEL = 0.012D;
	private static final double BRAKE = 0.03D;
	private static final double CLIMB = 0.25D;

	private static final EntityDataAccessor<Byte> DATA_CLASS = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_DOCKED = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_HULL = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_SEATS = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> DATA_OWNER_NAME = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_DEST = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> DATA_ABOARD = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_STYLE = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<String> DATA_NAME = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Byte> DATA_WEATHER = SynchedEntityData.defineId(KapalRohEntity.class, EntityDataSerializers.BYTE);
	/** Batu Meteor Roh, module L's item (looked up by id, nothing of L is imported). */
	private static final net.minecraft.resources.Identifier METEOR_STONE = Kultivator.id("batu_meteor_roh");

	// ---- server (saved)
	private @Nullable UUID owner;
	private final HoldContainer hold = new HoldContainer(this);
	private long fuelTicks;
	private double fuelDebt;
	private ListTag aboard = new ListTag();
	private boolean test;
	// ---- server (session)
	private double speed;
	private int inForward;
	private int inTurn;
	private int inVertical;
	private long inputAt = Long.MIN_VALUE / 2;
	private int still;
	private final List<KapalDekEntity> decks = new ArrayList<>();
	private @Nullable Vec3 dest;
	private String destId = "";
	private String destName = "";
	private double voyageLength;
	private double pirateAt = -1.0D;
	private int leapTimer = LEAP_EVERY;
	private int leapWait = -1;
	private @Nullable Vec3 leapTarget;
	private int haltTicks;
	private int calm;
	private int fleetTick;
	private int[] seatIds = new int[0];
	private String seatsSeen = "";
	private boolean lowFuelTold;
	// ---- server (session): the sky and the sky pirates
	private SpiritWeather weather = SpiritWeather.CLEAR;
	private @Nullable KapalBajakEntity raider;
	private @Nullable UUID pirateTarget;
	private boolean bandOut;
	private boolean foesSeen;
	private int pirateTier = 1;
	private int pirateRealm;

	public KapalRohEntity(EntityType<? extends KapalRohEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_CLASS, (byte) 0);
		builder.define(DATA_STATE, STATE_IDLE);
		builder.define(DATA_DOCKED, true);
		builder.define(DATA_SPEED, 0.0F);
		builder.define(DATA_HULL, KapalClass.PERAHU.hull());
		builder.define(DATA_COLOR, DEFAULT_COLOR);
		builder.define(DATA_SEATS, "");
		builder.define(DATA_FUEL, 0);
		builder.define(DATA_OWNER_NAME, "");
		builder.define(DATA_DEST, "");
		builder.define(DATA_ABOARD, 0);
		builder.define(DATA_STYLE, (byte) 0);
		builder.define(DATA_NAME, "");
		builder.define(DATA_WEATHER, (byte) 0);
	}

	// ================================================================== synced readers (client and server)

	public KapalClass kind() {
		return KapalClass.byOrdinal(entityData.get(DATA_CLASS));
	}

	public byte state() {
		return entityData.get(DATA_STATE);
	}

	public boolean docked() {
		return entityData.get(DATA_DOCKED);
	}

	public boolean broken() {
		return state() == STATE_BROKEN;
	}

	/** Signed speed in blocks per tick. */
	public float speed() {
		return entityData.get(DATA_SPEED);
	}

	public int hull() {
		return entityData.get(DATA_HULL);
	}

	public int color() {
		return entityData.get(DATA_COLOR);
	}

	/** Seconds of fuel left (burning item plus the hold). */
	public int fuelSeconds() {
		return entityData.get(DATA_FUEL);
	}

	public String ownerName() {
		return entityData.get(DATA_OWNER_NAME);
	}

	/** "x z name" of the voyage, or "". */
	public String destination() {
		return entityData.get(DATA_DEST);
	}

	/** Disciples flying along. */
	public int disciplesAboard() {
		return entityData.get(DATA_ABOARD);
	}

	/** The hull livery. */
	public KapalStyle style() {
		return KapalStyle.byOrdinal(entityData.get(DATA_STYLE));
	}

	/** The name its owner gave it ("" when unnamed). */
	public String shipName() {
		return entityData.get(DATA_NAME);
	}

	/** The spirit weather under the hull (module L; CLEAR when L is off), refreshed every 2 s. */
	public SpiritWeather weather() {
		return SpiritWeather.values()[Mth.clamp(entityData.get(DATA_WEATHER), 0, SpiritWeather.values().length - 1)];
	}

	public @Nullable UUID owner() {
		return owner;
	}

	/** The seat of {@code passenger} (synced order, else its passenger index), or -1. */
	public int seatOf(Entity passenger) {
		refreshSeats();
		for (int i = 0; i < seatIds.length; i++) {
			if (seatIds[i] == passenger.getId()) {
				return i;
			}
		}
		int index = getPassengers().indexOf(passenger);
		return index < 0 ? -1 : Math.min(index, kind().seats() - 1);
	}

	private void refreshSeats() {
		String raw = entityData.get(DATA_SEATS);
		if (raw.equals(seatsSeen)) {
			return;
		}
		seatsSeen = raw;
		if (raw.isEmpty()) {
			seatIds = new int[0];
			return;
		}
		String[] parts = raw.split(",");
		int[] ids = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				ids[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException exception) {
				ids[i] = -1;
			}
		}
		seatIds = ids;
	}

	/** The rider of seat 0, or null. */
	public @Nullable Entity helmRider() {
		refreshSeats();
		if (seatIds.length > 0 && seatIds[0] >= 0) {
			for (Entity passenger : getPassengers()) {
				if (passenger.getId() == seatIds[0]) {
					return passenger;
				}
			}
		}
		return null;
	}

	// ================================================================== setup

	/** Called once before {@code addFreshEntity}. */
	void setup(KapalClass kind, ServerPlayer player, @Nullable KapalItems.State state, ItemContainerContents contents, boolean isTest) {
		entityData.set(DATA_CLASS, (byte) kind.ordinal());
		this.owner = player.getUUID();
		this.test = isTest;
		entityData.set(DATA_OWNER_NAME, player.getName().getString());
		int max = kind.hull();
		int color = defaultColor(player);
		if (state != null) {
			entityData.set(DATA_HULL, Mth.clamp(state.hull(), 0, max));
			fuelTicks = Math.max(0L, state.fuel());
			color = state.color();
			entityData.set(DATA_NAME, cleanName(state.name()));
			entityData.set(DATA_STYLE, (byte) KapalStyle.byOrdinal(state.style()).ordinal());
			if (state.broken() || state.hull() <= 0) {
				setState(STATE_BROKEN);
			}
		} else {
			entityData.set(DATA_HULL, max);
		}
		entityData.set(DATA_COLOR, color);
		NonNullList<ItemStack> items = NonNullList.withSize(HoldContainer.SIZE, ItemStack.EMPTY);
		contents.copyInto(items);
		for (int i = 0; i < HoldContainer.SIZE; i++) {
			hold.setItem(i, items.get(i));
		}
		updateFuelShown();
	}

	static int defaultColor(ServerPlayer player) {
		Sect sect = Sects.sectOf(player);
		return sect == null ? DEFAULT_COLOR : (0xFFFFFF & sect.color());
	}

	private void setState(byte state) {
		if (entityData.get(DATA_STATE) != state) {
			entityData.set(DATA_STATE, state);
		}
	}

	private void setDocked(boolean value) {
		if (entityData.get(DATA_DOCKED) != value) {
			entityData.set(DATA_DOCKED, value);
		}
	}

	// ================================================================== tick (server)

	@Override
	public void tick() {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		KapalConfig cfg = Kapal.config();
		KapalClass kind = kind();
		long now = level.getGameTime();
		ServerPlayer captain = captain();
		boolean on = cfg.ships && DuniaSpine.enabled();
		if (captain != null && now - inputAt > INPUT_TIMEOUT) {
			inForward = 0;
			inTurn = 0;
			inVertical = 0;
		}
		if (captain == null || !on || state() == STATE_BROKEN || state() == STATE_HALT || state() == STATE_LEAP) {
			inForward = 0;
			inTurn = 0;
			inVertical = 0;
		}
		boolean fueled = fuelTicks > 0L || hasFuel(level);
		if (!fueled && captain != null && (inForward != 0 || inVertical != 0)) {
			if (tickCount % 40 == 0) {
				Msg.actionBar(captain, Component.translatable("kultivator.kapal.no_fuel").withColor(0xFF8A7A));
			}
			inForward = 0;
			inVertical = 0;
		}
		double ground = ground(level, kind, getX(), getZ(), getYRot());
		byte state = state();
		double vertical = 0.0D;
		double max = maxSpeed(level, cfg, kind, weather);

		if (state == STATE_BROKEN || !on || (!fueled && !docked())) {
			// set down gently (a broken ship, ships switched off, an empty hold): no input, no voyage
			if (state == STATE_AUTO || state == STATE_LEAP || state == STATE_FLY) {
				endVoyage(level, false);
				setState(state == STATE_BROKEN ? STATE_BROKEN : STATE_IDLE);
			}
			speed *= 0.9D;
			vertical = settle(ground, 0.12D);
		} else if (state == STATE_HALT) {
			speed *= 0.85D;
			vertical = settle(ground, 0.18D);
			tickPirates(level, ground);
		} else if (state == STATE_LEAP) {
			speed *= 0.95D;
			tickLeap(level, captain, cfg);
		} else if (state == STATE_AUTO && dest != null && captain != null) {
			vertical = tickAuto(level, cfg, kind, max, ground, captain);
		} else {
			if (state == STATE_AUTO) {
				// the captain left the helm: the voyage pauses where it is
				endVoyage(level, false);
			}
			// manual: W/S throttle (it keeps its speed), A/D rudder, jump up, sprint down
			if (inForward > 0) {
				speed = Math.min(max, speed + ACCEL);
			} else if (inForward < 0) {
				speed = Math.max(-max * 0.25D, speed - BRAKE);
			} else if (captain == null) {
				speed *= 0.92D;
			}
			if (Math.abs(speed) < 0.002D && inForward == 0) {
				speed = 0.0D;
			}
			float turn = inTurn * kind.turnRate() * (0.45F + 0.55F * (float) Math.min(1.0D, Math.abs(speed) / Math.max(0.01D, max)));
			if (turn != 0.0F) {
				setYRot(Mth.wrapDegrees(getYRot() + turn));
			}
			if (inVertical != 0) {
				vertical = inVertical * CLIMB;
			} else if (captain == null && !docked()) {
				vertical = settle(ground, 0.08D);
			}
			boolean moving = Math.abs(speed) > 0.002D || inVertical != 0 || inTurn != 0;
			setState(moving && captain != null ? STATE_FLY : STATE_IDLE);
		}

		move(level, kind, ground, vertical);
		dockCheck(level, kind, ground, vertical);
		burn(level, cfg);
		if (docked() && !aboard.isEmpty() && (tickCount + getId()) % 40 == 0) {
			landDisciples(level, false);
		}
		if (++fleetTick >= 100) {
			fleetTick = 0;
			noteFleet(level);
		}
		if ((tickCount + getId()) % 40 == 0) {
			senseSky(level);
		}
		float shown = (float) speed;
		if (Math.abs(entityData.get(DATA_SPEED) - shown) > 0.004F || (shown == 0.0F && entityData.get(DATA_SPEED) != 0.0F)) {
			entityData.set(DATA_SPEED, shown);
		}
	}

	private static double maxSpeed(ServerLevel level, KapalConfig cfg, KapalClass kind, SpiritWeather weather) {
		double perSecond = Math.min(kind.topSpeed() * KapalWeather.speed(weather), cfg.maxSpeed);
		if (DuniaBudget.overloaded(level.getServer())) {
			perSecond *= 0.5D;
		}
		return perSecond / 20.0D;
	}

	/** Vertical step toward the docked hover over {@code ground} (NaN: hold still). */
	private double settle(double ground, double rate) {
		if (Double.isNaN(ground)) {
			return 0.0D;
		}
		double target = ground + HOVER;
		double dy = target - getY();
		return Mth.clamp(dy, -rate, rate * 0.6D);
	}

	/**
	 * The highest ground (heightmap MOTION_BLOCKING, water counts) under the hull's middle, bow, stern and both beams, or
	 * NaN when one of those columns is in an unloaded chunk (never loads one).
	 */
	static double ground(ServerLevel level, KapalClass kind, double x, double z, float yaw) {
		double best = Double.NEGATIVE_INFINITY;
		double halfL = kind.length() * 0.5D - 1.0D;
		double halfW = kind.width() * 0.5D - 1.0D;
		double[][] spots = {{0.0D, 0.0D}, {0.0D, halfL}, {0.0D, -halfL}, {halfW, 0.0D}, {-halfW, 0.0D}};
		for (double[] spot : spots) {
			double[] o = KapalClass.toWorld(spot[0], spot[1], yaw);
			int bx = Mth.floor(x + o[0]);
			int bz = Mth.floor(z + o[1]);
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
			if (top <= level.getMinY()) {
				return Double.NaN;
			}
			best = Math.max(best, top);
		}
		return best;
	}

	private void move(ServerLevel level, KapalClass kind, double ground, double vertical) {
		double yaw = Math.toRadians(getYRot());
		double dx = -Math.sin(yaw) * speed;
		double dz = Math.cos(yaw) * speed;
		if (Math.abs(speed) > 0.001D) {
			// look ahead: an unloaded column or a cliff higher than the ship stops it (it climbs over what it can)
			double ahead = ground(level, kind, getX() + dx * 8.0D, getZ() + dz * 8.0D, getYRot());
			if (Double.isNaN(ahead)) {
				speed *= 0.5D;
				dx *= 0.0D;
				dz *= 0.0D;
			} else if (ahead + CLEARANCE > getY() + vertical) {
				vertical = Math.max(vertical, Math.min(0.5D, ahead + CLEARANCE - getY()));
				if (ahead + CLEARANCE > getY() + 3.0D) {
					speed *= 0.7D;
					dx *= 0.3D;
					dz *= 0.3D;
				}
			}
		}
		double y = getY() + vertical;
		if (!Double.isNaN(ground)) {
			double floor = ground + (docked() || state() == STATE_BROKEN ? HOVER * 0.5D : Math.min(CLEARANCE, HOVER + 0.4D));
			y = Math.max(y, floor);
		}
		y = Mth.clamp(y, level.getMinY() + 2.0D, level.getMaxY() - kind.height() - 2.0D);
		if (dx != 0.0D || dz != 0.0D || Math.abs(y - getY()) > 1.0E-4D) {
			setPos(getX() + dx, y, getZ() + dz);
		}
	}

	// ================================================================== docking and the deck

	private void dockCheck(ServerLevel level, KapalClass kind, double ground, double vertical) {
		boolean low = !Double.isNaN(ground) && getY() - ground <= DOCK_HEIGHT;
		boolean idle = Math.abs(speed) < 0.01D && inForward == 0 && inTurn == 0 && inVertical == 0
				&& (docked() || Math.abs(vertical) < 0.005D);
		byte state = state();
		if (docked() && Double.isNaN(ground) && idle) {
			// a corner of the hull lies in a chunk that is not loaded: it stays as it is
			return;
		}
		if (docked()) {
			if (!idle || !low || state == STATE_AUTO || state == STATE_LEAP) {
				undock(level);
			} else {
				align();
				if (decks.isEmpty() && Kapal.config().deckWalk && Math.abs(Mth.wrapDegrees(getYRot() - snapped(getYRot()))) < 0.01F) {
					spawnDecks(level, kind);
				}
			}
			return;
		}
		if (low && idle && state != STATE_AUTO && state != STATE_LEAP) {
			if (++still >= DOCK_TICKS) {
				still = 0;
				setDocked(true);
				onDocked(level);
			}
		} else {
			still = 0;
		}
	}

	/** Turns a docked ship to the nearest quarter (the deck colliders are axis aligned). */
	private void align() {
		float target = snapped(getYRot());
		float diff = Mth.wrapDegrees(target - getYRot());
		if (Math.abs(diff) > 0.01F) {
			setYRot(Mth.abs(diff) <= 3.0F ? target : getYRot() + Math.signum(diff) * 3.0F);
		}
	}

	static float snapped(float yaw) {
		return Mth.wrapDegrees(Math.round(yaw / 90.0F) * 90.0F);
	}

	private void onDocked(ServerLevel level) {
		if (!aboard.isEmpty()) {
			landDisciples(level, false);
		}
		removeDecks();
	}

	private void undock(ServerLevel level) {
		setDocked(false);
		still = 0;
		// in flight everyone aboard sits: players standing on the deck take the free seats
		if (!decks.isEmpty()) {
			AABB box = footprint().inflate(0.5D, 2.0D, 0.5D);
			for (Player player : level.getEntitiesOfClass(Player.class, box, p -> !p.isPassenger() && p.isAlive() && !p.isSpectator())) {
				if (getPassengers().size() < kind().seats()) {
					player.startRiding(this, true, true);
				}
			}
		}
		removeDecks();
		boardDisciples(level);
	}

	/** The axis-aligned box of the hull now (yaw rounded to a quarter for the swap of width and length). */
	AABB footprint() {
		KapalClass kind = kind();
		boolean across = Math.abs(Mth.wrapDegrees(snapped(getYRot()))) == 90.0F;
		double hx = (across ? kind.length() : kind.width()) * 0.5D;
		double hz = (across ? kind.width() : kind.length()) * 0.5D;
		return new AABB(getX() - hx, getY(), getZ() - hz, getX() + hx, getY() + kind.deck() + 1.0D, getZ() + hz);
	}

	private void spawnDecks(ServerLevel level, KapalClass kind) {
		float yaw = snapped(getYRot());
		boolean across = Math.abs(yaw) == 90.0F;
		for (float[] tile : kind.deckTiles()) {
			if (!DuniaBudget.mayAdd(level, DuniaBudget.PART)) {
				break;
			}
			KapalDekEntity deck = KapalEntities.DEK.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (deck == null) {
				break;
			}
			double[] o = KapalClass.toWorld(tile[0], tile[1], yaw);
			float sx = across ? tile[3] : tile[2];
			float sz = across ? tile[2] : tile[3];
			deck.setup(this, sx, sz);
			deck.snapTo(getX() + o[0], getY() + kind.deck() - KapalDekEntity.THICKNESS, getZ() + o[1], 0.0F, 0.0F);
			if (level.addFreshEntity(deck)) {
				DuniaBudget.track(DuniaBudget.PART, deck);
				decks.add(deck);
			}
		}
	}

	private void removeDecks() {
		for (KapalDekEntity deck : decks) {
			if (!deck.isRemoved()) {
				deck.discard();
			}
		}
		decks.clear();
	}

	// ================================================================== seats and passengers

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < kind().seats() && !broken();
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!level().isClientSide()) {
			assignSeats();
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!level().isClientSide()) {
			assignSeats();
			if (passenger instanceof LivingEntity living && !docked() && level() instanceof ServerLevel level && !isRemoved()) {
				double ground = ground(level, kind(), getX(), getZ(), getYRot());
				if (Double.isNaN(ground) || getY() - ground > 4.0D) {
					// jumping off a ship in flight: a soft fall instead of a death
					living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0, false, true));
					if (living instanceof ServerPlayer player) {
						Msg.actionBar(player, Component.translatable("kultivator.kapal.jump_off").withColor(0xB4E2FF));
					}
				}
			}
		}
	}

	/** Keeps every rider on a seat; seat 0 (the helm) goes to the owner or a sect mate when one is aboard. */
	private void assignSeats() {
		int seats = kind().seats();
		int[] next = new int[seats];
		java.util.Arrays.fill(next, -1);
		List<Entity> riders = new ArrayList<>(getPassengers());
		refreshSeats();
		// riders keep the seats they already have
		for (int i = 0; i < Math.min(seats, seatIds.length); i++) {
			if (seatIds[i] >= 0 && byId(riders, seatIds[i]) != null) {
				next[i] = seatIds[i];
			}
		}
		// the helm belongs to someone who may steer: such a rider swaps in
		boolean helmOk = byId(riders, next[0]) instanceof ServerPlayer helm && mayCaptain(helm);
		if (!helmOk) {
			for (Entity rider : riders) {
				if (rider instanceof ServerPlayer player && mayCaptain(player)) {
					int at = indexOf(next, rider.getId());
					int old = next[0];
					next[0] = rider.getId();
					if (at >= 0) {
						next[at] = old;
					} else if (old >= 0) {
						int free = indexOf(next, -1, 1);
						if (free >= 0) {
							next[free] = old;
						}
					}
					break;
				}
			}
		}
		// everyone else takes a free seat (seat 0 only when nothing else is free)
		for (Entity rider : riders) {
			if (indexOf(next, rider.getId()) >= 0) {
				continue;
			}
			int free = indexOf(next, -1, 1);
			if (free < 0 && next[0] < 0) {
				free = 0;
			}
			if (free >= 0) {
				next[free] = rider.getId();
			}
		}
		StringBuilder raw = new StringBuilder();
		for (int i = 0; i < seats; i++) {
			if (i > 0) {
				raw.append(',');
			}
			raw.append(next[i]);
		}
		entityData.set(DATA_SEATS, raw.toString());
	}

	private static @Nullable Entity byId(List<Entity> riders, int id) {
		for (Entity rider : riders) {
			if (rider.getId() == id) {
				return rider;
			}
		}
		return null;
	}

	private static int indexOf(int[] seats, int id) {
		return indexOf(seats, id, 0);
	}

	private static int indexOf(int[] seats, int id, int from) {
		for (int i = Math.max(0, from); i < seats.length; i++) {
			if (seats[i] == id) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		int seat = seatOf(passenger);
		if (seat < 0) {
			super.positionRider(passenger, moveFunction);
			return;
		}
		float[] spot = kind().seat(seat);
		double[] o = KapalClass.toWorld(spot[0], spot[2], getYRot());
		Vec3 offset = passenger.getVehicleAttachmentPoint(this);
		moveFunction.accept(passenger, getX() + o[0] - offset.x, getY() + spot[1] - offset.y, getZ() + o[1] - offset.z);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		int seat = seatOf(passenger);
		if (seat < 0 || !docked()) {
			return passenger.position();
		}
		float[] spot = kind().seat(seat);
		double[] o = KapalClass.toWorld(spot[0], spot[2] + 1.0D, getYRot());
		return new Vec3(getX() + o[0], getY() + kind().deck() + 0.05D, getZ() + o[1]);
	}

	/** The captain: the rider of seat 0 when they may steer (the owner or a sect mate, not a wandering soul). */
	public @Nullable ServerPlayer captain() {
		return helmRider() instanceof ServerPlayer player && mayCaptain(player) ? player : null;
	}

	boolean mayCaptain(ServerPlayer player) {
		if (player == null || player.isSpectator()) {
			return false;
		}
		if (DuniaApi.souls().isSoul(player)) {
			return false;
		}
		if (owner == null || owner.equals(player.getUUID())) {
			return true;
		}
		Sect sect = Sects.sectOf(player);
		return sect != null && sect.isMember(owner);
	}

	boolean isOwner(ServerPlayer player) {
		return owner != null && owner.equals(player.getUUID());
	}

	// ================================================================== interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level() instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		return board(serverPlayer);
	}

	/** Right-click on the hull or a deck collider: sneak opens the helm for whoever may steer, else boards. */
	InteractionResult board(ServerPlayer player) {
		if (player.getVehicle() == this) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown() && mayCaptain(player)) {
			sendInfo(player, true);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (broken()) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.broken_board").withColor(0xFF8A7A));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (getPassengers().size() >= kind().seats()) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.full").withColor(0xFFD54A));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (player.startRiding(this, false, true)) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_STEP, SoundSource.PLAYERS, 0.8F, 0.9F);
			Msg.actionBar(player, Component.translatable(captain() == player ? "kultivator.kapal.helm_taken" : "kultivator.kapal.boarded")
					.withColor(0xE8E0C0));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** The rider presses the inventory key: the captain gets the helm, the owner's people the hold, others a line. */
	@Override
	public void openCustomInventoryScreen(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (mayCaptain(serverPlayer)) {
			sendInfo(serverPlayer, true);
		} else {
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.kapal.only_captain").withColor(0xFFD54A));
		}
	}

	// ================================================================== input and helm (server, from KapalNet)

	static void onInput(ServerPlayer player, KapalNet.Input input) {
		if (!(player.level().getEntity(input.ship()) instanceof KapalRohEntity ship) || ship.captain() != player) {
			return;
		}
		ship.inForward = Mth.clamp(input.forward(), -1, 1);
		ship.inTurn = Mth.clamp(input.turn(), -1, 1);
		ship.inVertical = Mth.clamp(input.vertical(), -1, 1);
		ship.inputAt = player.level().getGameTime();
		if ((ship.inForward != 0 || ship.inVertical != 0) && (ship.state() == STATE_AUTO)) {
			// the captain takes the rudder: the autopilot lets go
			ship.endVoyage((ServerLevel) player.level(), false);
			ship.setState(STATE_FLY);
			Msg.actionBar(player, Component.translatable("kultivator.kapal.autopilot_off").withColor(0xE8E0C0));
		}
	}

	static void onHelm(ServerPlayer player, KapalNet.Helm helm) {
		if (!(player.level().getEntity(helm.ship()) instanceof KapalRohEntity ship) || !ship.mayCaptain(player)
				|| player.distanceToSqr(ship) > 48.0D * 48.0D) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		switch (helm.op()) {
			case OP_STOW -> ship.stow(player);
			case OP_REPAIR -> ship.repair(player);
			case OP_HOLD -> ship.openHold(player);
			case OP_COLOR -> {
				ship.entityData.set(DATA_COLOR, COLORS[Math.floorMod(helm.arg(), COLORS.length)]);
				ship.sendInfo(player, false);
			}
			case OP_SAIL -> ship.sailTo(player, helm.text());
			case OP_ROUTE -> ship.sailRoute(player, helm.text());
			case OP_HALL -> ship.sailHall(player);
			case OP_STYLE -> ship.chooseStyle(player, KapalStyle.byOrdinal(helm.arg()));
			case OP_RENAME -> ship.rename(player, helm.text());
			case OP_STOP -> {
				ship.endVoyage(level, false);
				ship.setState(STATE_IDLE);
				ship.sendInfo(player, false);
			}
			default -> ship.sendInfo(player, false);
		}
	}

	/** The helm screen's data for {@code player} ({@code open}: the screen opens). */
	void sendInfo(ServerPlayer player, boolean open) {
		CompoundTag info = new CompoundTag();
		KapalClass kind = kind();
		info.putInt("class", kind.ordinal());
		info.putInt("hull", hull());
		info.putInt("hull_max", kind.hull());
		info.putInt("state", state());
		info.putBoolean("docked", docked());
		info.putInt("color", color());
		info.putString("owner", ownerName());
		info.putBoolean("is_owner", isOwner(player));
		info.putBoolean("captain", captain() == player);
		info.putLong("fuel", KapalFuel.total(hold, level().dimension(), fuelTicks));
		info.putInt("seats", kind.seats());
		info.putInt("riders", getPassengers().size());
		info.putInt("disciples", aboard.size());
		info.putString("dest", destination());
		info.putInt("repair", repairCost());
		info.putInt("speed_max", Math.min(Math.round(kind.topSpeed()), Kapal.config().maxSpeed));
		info.putInt("style", style().ordinal());
		int styles = 0;
		for (KapalStyle each : KapalStyle.values()) {
			if (styleOpen(player, each)) {
				styles |= 1 << each.ordinal();
			}
		}
		info.putInt("styles_open", styles);
		info.putString("name", shipName());
		info.putInt("weather", weather().ordinal());
		ListTag routes = new ListTag();
		for (Voyages.Route route : Voyages.routes()) {
			CompoundTag r = new CompoundTag();
			r.putString("id", route.id());
			r.store("name", ComponentSerialization.CODEC, level().registryAccess().createSerializationContext(NbtOps.INSTANCE), route.name());
			boolean allowed;
			try {
				allowed = route.allowed().test(player, (ServerLevel) level());
			} catch (RuntimeException exception) {
				allowed = false;
			}
			r.putBoolean("allowed", allowed);
			routes.add(r);
		}
		info.put("routes", routes);
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		if (hall != null && hall.dim().equals(level().dimension())) {
			info.putInt("hall_x", hall.pos().getX());
			info.putInt("hall_z", hall.pos().getZ());
			info.putString("hall_name", hall.name());
		}
		ListTag waypoints = new ListTag();
		for (CompoundTag wp : dev.kultivator.dunia.peta.Peta.waypointsFor(player, level().dimension())) {
			waypoints.add(wp);
		}
		info.put("waypoints", waypoints);
		KapalNet.send(player, new KapalNet.Info(getId(), open, info));
	}

	// ================================================================== the world map (module T's own peta package)

	/** "Berlayar ke sini" from the map: true when {@code player} is the captain of the ship they ride. */
	public static boolean sailFromMap(ServerPlayer player, int x, int z, String name) {
		if (!(player.getVehicle() instanceof KapalRohEntity ship) || ship.captain() != player) {
			return false;
		}
		ship.startVoyage(player, new Vec3(x + 0.5D, 0.0D, z + 0.5D), "peta", name == null ? "" : name);
		return true;
	}

	/** The map's entry for the player's ship in their dimension ({@code x}, {@code z}, class, state, voyage), or null. */
	public static @Nullable CompoundTag mapEntry(ServerPlayer player) {
		KapalRohEntity ship = Kapal.shipOf(player);
		CompoundTag tag = new CompoundTag();
		if (ship != null) {
			if (ship.level() != player.level()) {
				return null;
			}
			tag.putInt("x", ship.getBlockX());
			tag.putInt("z", ship.getBlockZ());
			tag.putInt("class", ship.kind().ordinal());
			tag.putInt("state", ship.state());
			tag.putBoolean("docked", ship.docked());
			tag.putFloat("yaw", ship.getYRot());
			if (!ship.shipName().isEmpty()) {
				tag.putString("name", ship.shipName());
			}
			String dest = ship.destination();
			if (!dest.isEmpty()) {
				tag.putString("dest", dest);
			}
			return tag;
		}
		KapalFleet.Entry entry = KapalFleet.ship(player.level().getServer(), player.getUUID());
		if (entry == null || !entry.dim().equals(player.level().dimension())) {
			return null;
		}
		tag.putInt("x", entry.pos().getX());
		tag.putInt("z", entry.pos().getZ());
		tag.putInt("class", entry.kind());
		tag.putInt("state", STATE_IDLE);
		tag.putBoolean("docked", true);
		tag.putBoolean("far", true);
		return tag;
	}

	// ================================================================== hold

	private void openHold(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, hold, 1),
				Component.translatable("kultivator.kapal.hold.title", Component.translatable("kultivator.kapal.class." + kind().model()))));
	}

	boolean hasFuel(ServerLevel level) {
		for (int i = 0; i < hold.getContainerSize(); i++) {
			if (KapalFuel.ticks(hold.getItem(i), level.dimension()) > 0L) {
				return true;
			}
		}
		return false;
	}

	/** Burns fuel while it flies (docked and broken ships burn nothing). */
	private void burn(ServerLevel level, KapalConfig cfg) {
		if (docked() || broken() || (state() == STATE_IDLE && Math.abs(speed) < 0.002D && captain() == null)) {
			if ((tickCount + getId()) % 20 == 0) {
				updateFuelShown();
			}
			return;
		}
		fuelDebt += cfg.fuelScale * KapalWeather.fuel(weather);
		long whole = (long) fuelDebt;
		fuelDebt -= whole;
		fuelTicks -= whole;
		if (fuelTicks <= 0L) {
			long more = KapalFuel.take(hold, level.dimension());
			if (more > 0L) {
				fuelTicks += more;
			} else {
				fuelTicks = 0L;
				ServerPlayer captain = captain();
				if (captain != null && (tickCount % 100 == 0)) {
					Msg.actionBar(captain, Component.translatable("kultivator.kapal.no_fuel").withColor(0xFF8A7A));
				}
			}
		}
		if ((tickCount + getId()) % 20 == 0) {
			updateFuelShown();
			long total = KapalFuel.total(hold, level.dimension(), fuelTicks);
			ServerPlayer captain = captain();
			if (captain != null && total > 0L && total < 30L * 20L && !lowFuelTold) {
				lowFuelTold = true;
				Msg.actionBar(captain, Component.translatable("kultivator.kapal.low_fuel").withColor(0xFFD54A));
			} else if (total >= 30L * 20L) {
				lowFuelTold = false;
			}
		}
	}

	/** Spends {@code ticks} of fuel at once (a cloud leap); false when the hold cannot pay it. */
	private boolean spend(ServerLevel level, long ticks) {
		long need = Math.max(0L, ticks);
		if (KapalFuel.total(hold, level.dimension(), fuelTicks) < need) {
			return false;
		}
		while (fuelTicks < need) {
			long more = KapalFuel.take(hold, level.dimension());
			if (more <= 0L) {
				return false;
			}
			fuelTicks += more;
		}
		fuelTicks -= need;
		updateFuelShown();
		return true;
	}

	private void updateFuelShown() {
		long total = KapalFuel.total(hold, level().dimension(), fuelTicks);
		entityData.set(DATA_FUEL, (int) Math.min(Integer.MAX_VALUE, total / 20L));
	}

	/** Adds {@code ticks} of fuel directly ({@code uji kapal bahan_bakar}). */
	void addFuel(long ticks) {
		fuelTicks = Math.max(0L, fuelTicks + ticks);
		updateFuelShown();
	}

	// ================================================================== hull

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isInvulnerableToBase(source) || damage <= 0.0F) {
			return false;
		}
		if (source.getDirectEntity() instanceof Player) {
			// a fist or a sword on the hull does nothing (the ship is not a target)
			return false;
		}
		damageHull(level, Math.max(1, Math.round(damage)));
		return true;
	}

	void damageHull(ServerLevel level, int amount) {
		if (broken()) {
			return;
		}
		int left = Math.max(0, hull() - Math.max(0, amount));
		entityData.set(DATA_HULL, left);
		if (left <= 0) {
			breakDown(level);
		}
	}

	private void breakDown(ServerLevel level) {
		endVoyage(level, false);
		setState(STATE_BROKEN);
		speed = 0.0D;
		endPirates();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.6F, 0.6F);
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				Msg.title(player, Component.translatable("kultivator.kapal.broken.title").withColor(0xFF6A5A),
						Component.translatable("kultivator.kapal.broken.sub").withColor(0xE8E0C0), 5, 50, 15);
				KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_BROKEN, getX(), getY(), getZ(), 0));
				KapalVoice.crew(player, KapalVoice.HULL_BROKEN, "disciple.m1.careful");
			}
		}
	}

	/** The repair price in quarters of the hull (each {@link #REPAIR_PLANKS} planks and a Mid stone). */
	int repairCost() {
		int max = kind().hull();
		int missing = max - hull();
		return missing <= 0 ? 0 : Mth.ceil(missing * 4.0D / max);
	}

	private void repair(ServerPlayer player) {
		int quarters = repairCost();
		if (quarters <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.repair.full").withColor(0xB4E2FF));
			return;
		}
		Inventory inv = player.getInventory();
		int planks = 0;
		int stones = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(ItemTags.PLANKS)) {
				planks += stack.getCount();
			} else if (SpiritStones.grade(stack) == SpiritStones.MID) {
				stones += stack.getCount();
			}
		}
		int afford = player.isCreative() ? quarters : Math.min(quarters, Math.min(planks / REPAIR_PLANKS, stones));
		if (afford <= 0) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.repair.need", REPAIR_PLANKS, SpiritStones.name(SpiritStones.MID))
					.withColor(0xFF8A7A));
			return;
		}
		if (!player.isCreative()) {
			take(inv, afford * REPAIR_PLANKS, true);
			take(inv, afford, false);
		}
		int max = kind().hull();
		int restored = Math.min(max, hull() + afford * Mth.ceil(max / 4.0D));
		entityData.set(DATA_HULL, restored);
		if (broken() && restored >= max / 4) {
			setState(STATE_IDLE);
		}
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.3F);
		Msg.actionBar(player, Component.translatable("kultivator.kapal.repair.done", restored, max).withColor(0x9FE0C0));
		sendInfo(player, false);
	}

	private static void take(Inventory inv, int count, boolean planks) {
		int left = count;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			boolean match = planks ? stack.is(ItemTags.PLANKS) : SpiritStones.grade(stack) == SpiritStones.MID;
			if (match) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
		inv.setChanged();
	}

	/** {@code uji kapal rusak|perbaiki}. */
	void testHull(ServerLevel level, boolean broken) {
		if (broken) {
			entityData.set(DATA_HULL, 0);
			breakDown(level);
		} else {
			entityData.set(DATA_HULL, kind().hull());
			if (broken()) {
				setState(STATE_IDLE);
			}
		}
	}

	// ================================================================== voyages

	private void sailTo(ServerPlayer player, String text) {
		String[] parts = text == null ? new String[0] : text.trim().split(" ", 3);
		if (parts.length < 2) {
			return;
		}
		try {
			double x = Double.parseDouble(parts[0]);
			double z = Double.parseDouble(parts[1]);
			String name = parts.length > 2 ? parts[2] : "";
			startVoyage(player, new Vec3(x, 0.0D, z), "titik", name);
		} catch (NumberFormatException ignored) {
			// a broken request from a client
		}
	}

	private void sailHall(ServerPlayer player) {
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		if (hall == null || !hall.dim().equals(level().dimension())) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.no_hall").withColor(0xFF8A7A));
			return;
		}
		startVoyage(player, Vec3.atCenterOf(hall.pos()), "aula", hall.name());
	}

	private void sailRoute(ServerPlayer player, String id) {
		Voyages.Route route = Voyages.route(id);
		if (route == null) {
			return;
		}
		boolean allowed;
		try {
			allowed = route.allowed().test(player, (ServerLevel) level());
		} catch (RuntimeException exception) {
			allowed = false;
		}
		if (!allowed) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.route_closed", route.name()).withColor(0xFF8A7A));
			return;
		}
		// a route is a crossing: 2 000 blocks straight on, then the route's own arrival takes the ship's people there
		double yaw = Math.toRadians(getYRot());
		Vec3 far = new Vec3(getX() - Math.sin(yaw) * Voyages.ROUTE_CROSSING, 0.0D, getZ() + Math.cos(yaw) * Voyages.ROUTE_CROSSING);
		startVoyage(player, far, "rute:" + route.id(), route.name().getString());
	}

	/** Starts the autopilot toward {@code target} (x/z) for {@code captain}. */
	void startVoyage(ServerPlayer captain, Vec3 target, String id, String name) {
		if (captain() != captain) {
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.sit_helm").withColor(0xFFD54A));
			return;
		}
		if (broken()) {
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.broken_board").withColor(0xFF8A7A));
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (fuelTicks <= 0L && !hasFuel(level)) {
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.no_fuel").withColor(0xFF8A7A));
			return;
		}
		double dx = target.x - getX();
		double dz = target.z - getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < ARRIVE_RANGE) {
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.already_there").withColor(0xE8E0C0));
			return;
		}
		dest = target;
		destId = id;
		destName = name == null ? "" : name;
		voyageLength = length;
		leapTimer = LEAP_EVERY / 3;
		leapWait = -1;
		pirateAt = -1.0D;
		KapalConfig cfg = Kapal.config();
		if (cfg.pirates && length > PIRATE_VOYAGE && random.nextDouble() < KapalWeather.pirateChance(cfg.pirateChance, sky(level))) {
			// somewhere between a third and two thirds of the way
			pirateAt = length * (0.33D + 0.34D * random.nextDouble());
		}
		entityData.set(DATA_DEST, Math.round(target.x) + " " + Math.round(target.z) + " " + destName);
		setState(STATE_AUTO);
		DuniaEvents.fireVoyage(captain, id, false);
		Msg.actionBar(captain, Component.translatable("kultivator.kapal.voyage_start", destName.isEmpty() ? Component.translatable(
				"kultivator.kapal.dest.point", Math.round(target.x), Math.round(target.z)) : Component.literal(destName), Math.round(length))
				.withColor(0x9FE0C0));
		sendInfo(captain, false);
	}

	private void endVoyage(ServerLevel level, boolean arrived) {
		if (dest == null && state() != STATE_AUTO && state() != STATE_LEAP) {
			return;
		}
		dest = null;
		leapWait = -1;
		leapTarget = null;
		pirateAt = -1.0D;
		entityData.set(DATA_DEST, "");
		if (!arrived) {
			destId = "";
			destName = "";
		}
	}

	private double tickAuto(ServerLevel level, KapalConfig cfg, KapalClass kind, double max, double ground, ServerPlayer captain) {
		Vec3 target = dest;
		if (target == null) {
			return 0.0D;
		}
		double dx = target.x - getX();
		double dz = target.z - getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float err = Mth.wrapDegrees(want - getYRot());
		float step = Mth.clamp(err, -kind.turnRate(), kind.turnRate());
		setYRot(Mth.wrapDegrees(getYRot() + step));
		// pirates
		if (pirateAt > 0.0D && voyageLength - dist >= pirateAt) {
			pirateAt = -1.0D;
			startPirates(level, captain);
			return 0.0D;
		}
		if (dist <= ARRIVE_RANGE) {
			speed *= 0.85D;
			double v = settle(ground, 0.3D);
			if (Math.abs(speed) < 0.01D && !Double.isNaN(ground) && getY() - ground <= DOCK_HEIGHT) {
				arrive(level, captain);
			}
			return v;
		}
		// leaps on long voyages
		if (cfg.cloudLeap && dist > LEAP_FROM && --leapTimer <= 0) {
			leapTimer = LEAP_EVERY;
			if (beginLeap(level, cfg, dist)) {
				return 0.0D;
			}
		}
		double slow = dist < 160.0D ? Math.max(0.25D, dist / 160.0D) : 1.0D;
		double heading = Math.abs(err) < 30.0F ? 1.0D : 0.3D;
		double targetSpeed = max * slow * heading;
		speed += Mth.clamp(targetSpeed - speed, -BRAKE, ACCEL);
		double cruise;
		if (dist < 160.0D) {
			cruise = Double.isNaN(ground) ? getY() : ground + CLEARANCE + dist / 160.0D * CRUISE_ABOVE;
		} else {
			cruise = Math.max(Double.isNaN(ground) ? getY() : ground + CRUISE_ABOVE, CRUISE_MIN_Y);
		}
		cruise = Math.min(cruise, level.getMaxY() - kind.height() - 4.0D);
		return Mth.clamp(cruise - getY(), -0.3D, 0.4D);
	}

	/** {@code uji kapal lompat <x> <z>}: a voyage toward x/z with a cloud leap at once (fuel paid as usual). */
	void testLeap(ServerPlayer captain, Vec3 target) {
		if (dest == null || dest.distanceToSqr(target) > 1.0D) {
			startVoyage(captain, target, "titik", "");
		}
		if (dest == null) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		float want = (float) (Mth.atan2(target.z - getZ(), target.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
		setYRot(Mth.wrapDegrees(want));
		double dist = Math.sqrt(Mth.square(target.x - getX()) + Mth.square(target.z - getZ()));
		if (!beginLeap(level, Kapal.config(), dist, true)) {
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.cmd.leap_refused").withColor(0xFFD54A));
		}
	}

	/** The ship leaves without its "destroyed" handling (a test removal already gave the keel back). */
	void markStowed() {
		stowed = true;
	}

	private boolean beginLeap(ServerLevel level, KapalConfig cfg, double dist) {
		return beginLeap(level, cfg, dist, false);
	}

	private boolean beginLeap(ServerLevel level, KapalConfig cfg, double dist, boolean forced) {
		double yaw = Math.toRadians(getYRot());
		double length = forced ? Math.min(cfg.leapDistance, dist - ARRIVE_RANGE * 2.0D) : Math.min(cfg.leapDistance, dist - 600.0D);
		if (length < (forced ? 32.0D : 128.0D)) {
			return false;
		}
		double perTick = Math.max(0.05D, maxSpeed(level, cfg, kind(), weather));
		long cost = (long) Math.ceil(length / perTick * 1.5D * cfg.fuelScale * KapalWeather.fuel(weather) * KapalWeather.leap(sky(level)));
		if (!spend(level, cost)) {
			return false;
		}
		Vec3 landing = new Vec3(getX() - Math.sin(yaw) * length, getY(), getZ() + Math.cos(yaw) * length);
		DuniaTickets.touch(level, ChunkPos.containing(BlockPos.containing(landing)), 1);
		leapTarget = landing;
		leapWait = 0;
		setState(STATE_LEAP);
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_LEAP_IN, landing.x, landing.y, landing.z, LEAP_WAIT));
				KapalVoice.sky(player, KapalVoice.CLOUD_LEAP, null);
				if (player == captain()) {
					Advancements.grant(player, "dunia/ship_leap");
				}
			}
		}
		return true;
	}

	private void tickLeap(ServerLevel level, @Nullable ServerPlayer captain, KapalConfig cfg) {
		Vec3 landing = leapTarget;
		if (landing == null) {
			setState(dest != null ? STATE_AUTO : STATE_IDLE);
			return;
		}
		leapWait++;
		BlockPos at = BlockPos.containing(landing);
		boolean ready = level.hasChunkAt(at.getX(), at.getZ()) && leapWait >= 12;
		if (!ready && leapWait < LEAP_WAIT) {
			return;
		}
		if (!ready) {
			// the far chunk did not come in time: no jump this time, the voyage flies on
			leapTarget = null;
			setState(dest != null ? STATE_AUTO : STATE_IDLE);
			sendLeapOut();
			return;
		}
		double ground = ground(level, kind(), landing.x, landing.z, getYRot());
		double y = Double.isNaN(ground) ? getY() : Math.max(ground + CRUISE_ABOVE, CRUISE_MIN_Y);
		y = Math.min(y, level.getMaxY() - kind().height() - 4.0D);
		leapTarget = null;
		teleport(new TeleportTransition(level, new Vec3(landing.x, y, landing.z), Vec3.ZERO, getYRot(), 0.0F, Set.of(),
				TeleportTransition.DO_NOTHING));
		setState(dest != null ? STATE_AUTO : STATE_IDLE);
		sendLeapOut();
	}

	private void sendLeapOut() {
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_LEAP_OUT, getX(), getY(), getZ(), 0));
			}
		}
	}

	private void arrive(ServerLevel level, ServerPlayer captain) {
		String id = destId;
		String name = destName;
		double length = voyageLength;
		endVoyage(level, true);
		setState(STATE_IDLE);
		speed = 0.0D;
		destId = "";
		destName = "";
		DuniaEvents.fireVoyage(captain, id, true);
		if (length >= VOYAGE_ADVANCEMENT) {
			Advancements.grant(captain, "dunia/ship_voyage");
		}
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_ARRIVE, getX(), getY(), getZ(), player == captain ? 1 : 0));
				KapalVoice.crew(player, KapalVoice.ARRIVE, "disciple.m1.greet");
				Msg.actionBar(player, Component.translatable("kultivator.kapal.arrived", name.isEmpty() ? Component.translatable(
						"kultivator.kapal.dest.here") : Component.literal(name)).withColor(0x9FE0C0));
			}
		}
		if (id.startsWith("rute:")) {
			Voyages.Route route = Voyages.route(id.substring("rute:".length()));
			if (route != null) {
				Voyages.arriveRoute(this, captain, route);
			}
		}
	}

	// ================================================================== sky pirates

	/**
	 * Bajak Langit: the ship halts and sets down, a raider comes out of the clouds and draws alongside, and once the hull is
	 * low (or after 12 s) the band boards: module K's rivals when K is on, else rogue cultivators beside the ship. Driven off
	 * (no foe near for 10 s after the fight), they leave spoils for the captain and the voyage goes on.
	 */
	void startPirates(ServerLevel level, @Nullable ServerPlayer captain) {
		setState(STATE_HALT);
		haltTicks = 0;
		calm = 0;
		speed *= 0.5D;
		bandOut = false;
		foesSeen = false;
		ServerPlayer target = captain != null ? captain : nearestRider();
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				Msg.title(player, Component.translatable("kultivator.kapal.pirates.title").withColor(0xFF6A5A),
						Component.translatable("kultivator.kapal.pirates.sub").withColor(0xE8E0C0), 5, 50, 15);
				KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_PIRATES, getX(), getY(), getZ(), 0));
			}
		}
		if (target == null) {
			pirateTarget = null;
			return;
		}
		pirateTarget = target.getUUID();
		pirateRealm = Cultivators.get(target).realm().ordinal();
		pirateTier = Mth.clamp(1 + pirateRealm / 2, 1, 4);
		spawnRaider(level);
	}

	/** The raider appears some way off the beam, a little higher, and flies in. */
	private void spawnRaider(ServerLevel level) {
		if (raider != null && !raider.isRemoved()) {
			return;
		}
		if (!DuniaBudget.mayAdd(level, DuniaBudget.PART)) {
			return;
		}
		int side = random.nextBoolean() ? 1 : -1;
		Vec3 from = null;
		for (double off : new double[]{44.0D, 30.0D, 18.0D}) {
			double[] o = KapalClass.toWorld(side * off, -off * 0.4D, getYRot());
			Vec3 at = new Vec3(getX() + o[0], getY() + 6.0D + off * 0.2D, getZ() + o[1]);
			if (level.isPositionEntityTicking(BlockPos.containing(at))) {
				from = at;
				break;
			}
		}
		if (from == null) {
			return;
		}
		KapalBajakEntity skiff = KapalEntities.BAJAK.create(level, EntitySpawnReason.EVENT);
		if (skiff == null) {
			return;
		}
		skiff.setup(this, side);
		skiff.snapTo(from.x, from.y, from.z, getYRot(), 0.0F);
		if (level.addFreshEntity(skiff)) {
			DuniaBudget.track(DuniaBudget.PART, skiff);
			raider = skiff;
		}
	}

	private @Nullable ServerPlayer nearestRider() {
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				return player;
			}
		}
		return null;
	}

	private void tickPirates(ServerLevel level, double ground) {
		haltTicks++;
		boolean low = !Double.isNaN(ground) && getY() - ground <= DOCK_HEIGHT + 1.0D;
		if (!bandOut && pirateTarget != null && (low || haltTicks >= 240)) {
			bandOut = true;
			ServerPlayer target = level.getServer().getPlayerList().getPlayer(pirateTarget);
			if (target != null && target.level() == level) {
				Voyages.pirates(level, this, target, pirateTier, pirateRealm);
			}
		}
		if (haltTicks % 20 != 0) {
			return;
		}
		AABB box = getBoundingBox().inflate(32.0D, 16.0D, 32.0D);
		List<Mob> foes = level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && (mob instanceof Enemy || RogueCultivators.isRogue(mob)
				|| (mob.getTarget() instanceof Player hunted && (hunted.getVehicle() == this || hunted.distanceToSqr(this) < 40.0D * 40.0D))));
		if (!foes.isEmpty()) {
			calm = 0;
			foesSeen = true;
			// their techniques scorch the hull: a few points per nearby foe every 2 s
			if (haltTicks % 40 == 0) {
				int near = 0;
				for (Mob mob : foes) {
					if (mob.distanceToSqr(this) < 24.0D * 24.0D) {
						near++;
					}
				}
				if (near > 0) {
					damageHull(level, near * (2 + random.nextInt(4)));
				}
			}
		} else {
			calm += 20;
		}
		// the band is gone for 10 s (or 3 minutes passed): the voyage goes on
		boolean waited = bandOut || pirateTarget == null;
		if ((waited && calm >= 200) || haltTicks >= 3600) {
			if (broken()) {
				return;
			}
			boolean won = foesSeen && haltTicks < 3600;
			setState(dest != null ? STATE_AUTO : STATE_IDLE);
			ServerPlayer receiver = pirateTarget == null ? null : level.getServer().getPlayerList().getPlayer(pirateTarget);
			endPirates();
			for (Entity passenger : getPassengers()) {
				if (passenger instanceof ServerPlayer player) {
					Msg.actionBar(player, Component.translatable(won ? "kultivator.kapal.pirates.repelled" : "kultivator.kapal.pirates.gone")
							.withColor(0x9FE0C0));
					if (won) {
						Advancements.grant(player, "dunia/ship_pirates");
					}
				}
			}
			if (won && receiver != null && receiver.level() == level && receiver.distanceToSqr(this) < 64.0D * 64.0D) {
				Advancements.grant(receiver, "dunia/ship_pirates");
				Voyages.spoils(level, this, receiver, pirateTier);
			}
		}
	}

	/** The pirates are done (driven off, the ship broke, it was stowed): the raider turns away. */
	private void endPirates() {
		bandOut = false;
		pirateTarget = null;
		if (raider != null) {
			if (!raider.isRemoved()) {
				raider.flee();
			}
			raider = null;
		}
	}

	/** The raider of this ship's sky pirates, or null. */
	@Nullable KapalBajakEntity raider() {
		return raider == null || raider.isRemoved() ? null : raider;
	}

	// ================================================================== the sky (module L)

	private static @Nullable SkyKind sky(ServerLevel level) {
		try {
			return DuniaApi.sky().active(level);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/** Every 2 s: the spirit weather under the hull, and a meteor shower's chance to net a meteor stone. */
	private void senseSky(ServerLevel level) {
		SpiritWeather now;
		try {
			now = DuniaApi.sky().weatherAt(level, blockPosition());
		} catch (RuntimeException exception) {
			now = SpiritWeather.CLEAR;
		}
		if (now == null) {
			now = SpiritWeather.CLEAR;
		}
		if (now != weather) {
			weather = now;
			entityData.set(DATA_WEATHER, (byte) now.ordinal());
			ServerPlayer captain = captain();
			if (captain != null && now != SpiritWeather.CLEAR && !docked()) {
				Msg.actionBar(captain, Component.translatable("kultivator.kapal.weather." + now.id()).withColor(0xC9B6FF));
			}
		}
		boolean underWay = !docked() && !broken() && (state() == STATE_FLY || state() == STATE_AUTO);
		if (underWay && (tickCount + getId()) % KapalWeather.METEOR_EVERY < 40 && sky(level) == SkyKind.METEOR_SHOWER
				&& random.nextDouble() < KapalWeather.METEOR_CHANCE) {
			netMeteor(level);
		}
	}

	/** {@code uji kapal meteor}: the cloud net catches a meteor stone now. */
	void testMeteor() {
		netMeteor((ServerLevel) level());
	}

	private void netMeteor(ServerLevel level) {
		Item meteor = BuiltInRegistries.ITEM.getOptional(METEOR_STONE).orElse(null);
		if (meteor == null) {
			return;
		}
		ItemStack left = hold.addItem(new ItemStack(meteor));
		if (!left.isEmpty()) {
			return;
		}
		level.playSound(null, getX(), getY() + kind().deck(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F);
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				Msg.actionBar(player, Component.translatable("kultivator.kapal.meteor_net").withColor(0xFFC66A));
				KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_METEOR, getX(), getY() + kind().deck(), getZ(), 0));
			}
		}
		updateFuelShown();
	}

	// ================================================================== livery and name

	/** True when {@code player} may give the ship {@code style} (creative players may give any). */
	static boolean styleOpen(ServerPlayer player, KapalStyle style) {
		if (player.isCreative()) {
			return true;
		}
		return switch (style.unlock()) {
			case NONE -> true;
			case HALL_3 -> Halls.level(player.getUUID()) >= 3;
			case PIRATES -> advancementDone(player, "dunia/ship_pirates");
			case VOYAGE -> advancementDone(player, "dunia/ship_voyage");
		};
	}

	private static boolean advancementDone(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Kultivator.id(name));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	private void chooseStyle(ServerPlayer player, KapalStyle style) {
		if (!isOwner(player) && !player.isCreative()) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.stow.owner_only").withColor(0xFF8A7A));
			return;
		}
		if (!styleOpen(player, style)) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.style.locked." + style.unlock().name().toLowerCase(java.util.Locale.ROOT))
					.withColor(0xFF8A7A));
			sendInfo(player, false);
			return;
		}
		setStyle(style);
		level().playSound(null, getX(), getY() + kind().deck(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.1F);
		Msg.actionBar(player, Component.translatable("kultivator.kapal.style.set", Component.translatable("kultivator.kapal.style." + style.id()))
				.withColor(0x9FE0C0));
		sendInfo(player, false);
	}

	/** Sets the livery ({@code uji kapal gaya} too). */
	void setStyle(KapalStyle style) {
		entityData.set(DATA_STYLE, (byte) style.ordinal());
	}

	private void rename(ServerPlayer player, String text) {
		if (!isOwner(player) && !player.isCreative()) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.stow.owner_only").withColor(0xFF8A7A));
			return;
		}
		setShipName(text);
		Msg.actionBar(player, shipName().isEmpty() ? Component.translatable("kultivator.kapal.name.cleared").withColor(0xE8E0C0)
				: Component.translatable("kultivator.kapal.name.set", shipName()).withColor(0x9FE0C0));
		sendInfo(player, false);
	}

	/** Sets the ship's name ({@code uji kapal nama} too); control characters are dropped, at most {@link #NAME_MAX}. */
	void setShipName(String text) {
		entityData.set(DATA_NAME, cleanName(text));
	}

	static String cleanName(String text) {
		String clean = text == null ? "" : StringUtil.filterText(text).strip();
		return clean.length() > NAME_MAX ? clean.substring(0, NAME_MAX).strip() : clean;
	}

	// ================================================================== disciples aboard

	/** FOLLOW disciples of the captain within 24 blocks take the free seats as the ship lifts off. */
	private void boardDisciples(ServerLevel level) {
		ServerPlayer captain = captain();
		if (captain == null) {
			return;
		}
		int free = kind().seats() - getPassengers().size() - aboard.size();
		if (free <= 0) {
			return;
		}
		int boarded = 0;
		for (DiscipleEntity disciple : Disciples.loaded(captain.getUUID())) {
			if (free <= 0) {
				break;
			}
			if (disciple.level() != level || disciple.distanceToSqr(this) > 24.0D * 24.0D || !disciple.mode().follows()
					|| disciple.isReleasing() || disciple.data().breakingThrough() || DuelRules.inDuel(disciple)
					|| disciple.role() != DiscipleEntity.Role.DISCIPLE) {
				continue;
			}
			CompoundTag tag = Disciples.depart(disciple, "kapal");
			if (!tag.isEmpty()) {
				aboard.add(tag);
				free--;
				boarded++;
			}
		}
		entityData.set(DATA_ABOARD, aboard.size());
		if (boarded > 0) {
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.disciples_aboard", boarded).withColor(0x9FE0C0));
		}
	}

	/**
	 * Brings the disciples aboard back onto the deck (or next to the owner when {@code toOwner}); true when none is left.
	 * Those that cannot land yet stay aboard (retried every 2 s while docked).
	 */
	boolean landDisciples(ServerLevel level, boolean toOwner) {
		if (aboard.isEmpty()) {
			return true;
		}
		ServerPlayer ownerPlayer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		ListTag left = new ListTag();
		int i = 0;
		for (Tag tag : aboard) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			Vec3 at;
			if (toOwner && ownerPlayer != null && ownerPlayer.level() == level) {
				at = ownerPlayer.position();
			} else {
				float[] spot = kind().seat(1 + (i % Math.max(1, kind().seats() - 1)));
				double[] o = KapalClass.toWorld(spot[0], spot[2], getYRot());
				at = new Vec3(getX() + o[0], getY() + kind().deck() + 0.1D, getZ() + o[1]);
			}
			i++;
			DiscipleEntity back = owner == null ? null : Disciples.arrive(level, at, entry, owner);
			if (back == null) {
				left.add(entry);
			}
		}
		aboard = left;
		entityData.set(DATA_ABOARD, aboard.size());
		return aboard.isEmpty();
	}

	// ================================================================== stow ("Simpan")

	private void stow(ServerPlayer player) {
		if (!isOwner(player) && !player.isCreative()) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.stow.owner_only").withColor(0xFF8A7A));
			return;
		}
		ServerLevel level = (ServerLevel) level();
		double ground = ground(level, kind(), getX(), getZ(), getYRot());
		if (state() == STATE_HALT || state() == STATE_AUTO || state() == STATE_LEAP || Double.isNaN(ground)
				|| getY() - ground > DOCK_HEIGHT + 0.5D) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.stow.land_first").withColor(0xFFD54A));
			return;
		}
		if (!landDisciples(level, true)) {
			Msg.actionBar(player, Component.translatable("kultivator.kapal.stow.disciples").withColor(0xFFD54A));
			return;
		}
		ItemStack keel = toItem();
		closeHoldMenus(level);
		hold.clearContent();
		ejectPassengers();
		removeDecks();
		Kapal.give(player, keel);
		KapalFleet.remove(level.getServer(), owner, getUUID());
		KapalNet.send(player, new KapalNet.Event(getId(), KapalNet.Event.EV_STOWED, getX(), getY(), getZ(), 0));
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.4F);
		Msg.actionBar(player, Component.translatable("kultivator.kapal.stowed").withColor(0x9FE0C0));
		stowed = true;
		discard();
	}

	private boolean stowed;

	/** A route's arrival: the ship goes back into its keel in the captain's inventory (riders already off). */
	void stowForRoute(ServerPlayer captain) {
		ServerLevel level = (ServerLevel) level();
		ItemStack keel = toItem();
		closeHoldMenus(level);
		hold.clearContent();
		ejectPassengers();
		removeDecks();
		Kapal.give(captain, keel);
		if (owner != null) {
			KapalFleet.remove(level.getServer(), owner, getUUID());
		}
		KapalNet.send(captain, new KapalNet.Event(getId(), KapalNet.Event.EV_STOWED, getX(), getY(), getZ(), 1));
		stowed = true;
		discard();
	}

	/** The keel item with this ship's state and hold. */
	ItemStack toItem() {
		ItemStack keel = new ItemStack(KapalItems.keel(kind()));
		keel.set(KapalItems.STATE, new KapalItems.State(hull(), fuelTicks, color(), broken(), shipName(), style().ordinal()));
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < hold.getContainerSize(); i++) {
			items.add(hold.getItem(i).copy());
		}
		keel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		return keel;
	}

	private void closeHoldMenus(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.containerMenu instanceof ChestMenu chest && chest.getContainer() == hold) {
				player.closeContainer();
			}
		}
	}

	boolean test() {
		return test;
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		if (level() instanceof ServerLevel) {
			endPirates();
		}
		if (level() instanceof ServerLevel level && reason.shouldDestroy()) {
			removeDecks();
			closeHoldMenus(level);
			if (!stowed && owner != null) {
				// destroyed without "Simpan" (/kill, uji hapus): disciples land, and a real ship drops its keel with the hold
				if (!landDisciples(level, false) && !aboard.isEmpty()) {
					KapalFleet.addPending(level.getServer(), owner, aboard);
					aboard = new ListTag();
				}
				if (!test) {
					ItemEntity drop = new ItemEntity(level, getX(), getY() + 1.0D, getZ(), toItem());
					drop.setDefaultPickUpDelay();
					drop.addTag(NoGrief.TAG);
					level.addFreshEntity(drop);
				}
				hold.clearContent();
			}
			if (owner != null) {
				KapalFleet.remove(level.getServer(), owner, getUUID());
			}
		} else if (level() instanceof ServerLevel) {
			// unloaded: the colliders are not saved; they come back when it docks again
			removeDecks();
		}
		super.onRemoval(reason);
	}

	private void noteFleet(ServerLevel level) {
		if (owner != null && !isRemoved()) {
			KapalFleet.put(level.getServer(), owner, new KapalFleet.Entry(getUUID(), level.dimension(), blockPosition(), kind().ordinal(),
					ownerName()));
		}
	}

	/** {@code uji kapal panggil} and the summon of a far ship: moves it next to {@code player} (passengers stay seated). */
	void summonTo(ServerPlayer player) {
		ServerLevel target = (ServerLevel) player.level();
		endVoyage(target, false);
		endPirates();
		setState(broken() ? STATE_BROKEN : STATE_IDLE);
		removeDecks();
		setDocked(false);
		Vec3 at = player.position().add(0.0D, 2.0D, 0.0D);
		double yaw = Math.toRadians(player.getYRot());
		at = at.add(-Math.sin(yaw) * (kind().width() + 3.0D), 0.0D, Math.cos(yaw) * (kind().width() + 3.0D));
		if (level() != target) {
			ejectPassengers();
		}
		teleport(new TeleportTransition(target, at, Vec3.ZERO, player.getYRot(), 0.0F, Set.of(), TeleportTransition.DO_NOTHING));
	}

	// ================================================================== inert entity details

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeHitByProjectile() {
		return true;
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
	public boolean skipAttackInteraction(Entity source) {
		return source instanceof Player;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double range = 256.0D;
		return distance < range * range;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		// server-driven: no client simulates it
		return null;
	}

	// ================================================================== save

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		entityData.set(DATA_CLASS, (byte) Mth.clamp(input.getIntOr("class", 0), 0, KapalClass.values().length - 1));
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		entityData.set(DATA_OWNER_NAME, input.getStringOr("owner_name", ""));
		entityData.set(DATA_HULL, Mth.clamp(input.getIntOr("hull", kind().hull()), 0, kind().hull()));
		entityData.set(DATA_COLOR, input.getIntOr("color", DEFAULT_COLOR));
		fuelTicks = Math.max(0L, input.getLongOr("fuel", 0L));
		entityData.set(DATA_NAME, cleanName(input.getStringOr("name", "")));
		entityData.set(DATA_STYLE, (byte) KapalStyle.byOrdinal(input.getIntOr("style", 0)).ordinal());
		test = input.getBooleanOr("test", false);
		setState(input.getBooleanOr("broken", false) ? STATE_BROKEN : STATE_IDLE);
		setDocked(false);
		NonNullList<ItemStack> items = NonNullList.withSize(HoldContainer.SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		for (int i = 0; i < HoldContainer.SIZE; i++) {
			hold.setItem(i, items.get(i));
		}
		aboard = input.read("aboard", CompoundTag.CODEC).map(tag -> tag.getListOrEmpty("list").copy()).orElse(new ListTag());
		entityData.set(DATA_ABOARD, aboard.size());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("class", kind().ordinal());
		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
		output.putString("owner_name", ownerName());
		output.putInt("hull", hull());
		output.putInt("color", color());
		output.putLong("fuel", fuelTicks);
		output.putString("name", shipName());
		output.putInt("style", style().ordinal());
		output.putBoolean("test", test);
		output.putBoolean("broken", broken());
		NonNullList<ItemStack> items = NonNullList.withSize(HoldContainer.SIZE, ItemStack.EMPTY);
		for (int i = 0; i < HoldContainer.SIZE; i++) {
			items.set(i, hold.getItem(i));
		}
		ContainerHelper.saveAllItems(output, items);
		if (!aboard.isEmpty()) {
			CompoundTag holder = new CompoundTag();
			holder.put("list", aboard.copy());
			output.store("aboard", CompoundTag.CODEC, holder);
		}
	}

	/** The 9-slot hold: valid only while the ship is there and the player is near (closing on stow prevents a dupe). */
	static final class HoldContainer extends SimpleContainer {

		static final int SIZE = 9;
		private final KapalRohEntity ship;

		HoldContainer(KapalRohEntity ship) {
			super(SIZE);
			this.ship = ship;
		}

		@Override
		public boolean stillValid(Player player) {
			return !ship.isRemoved() && player.level() == ship.level() && player.distanceToSqr(ship) < 40.0D * 40.0D;
		}
	}
}
