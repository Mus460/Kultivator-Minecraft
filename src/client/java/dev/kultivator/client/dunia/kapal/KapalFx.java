package dev.kultivator.client.dunia.kapal;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.dunia.kapal.KapalBajakEntity;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-tick side of the ships' looks (DESIGN 34.5.15, budgets {@code Minimal / Normal / Maximal}): the stern's qi
 * engine motes (4 / 10 / 20 per second for a ship under way, a few for a docked one), a cloud wake trail behind a moving
 * ship (puffs every 3 ticks, 60-tick life, drawn by {@link KapalRenderer}), sparks and smoke on a broken hull, and from the
 * detail pass a thin smoke from a hull below half, the qi dust shed by the formation under a ship under way, and the sky
 * pirates' raider ({@link KapalBajakEntity}: a grey wake, red engine embers, soot from its torn sails). Only hulls within
 * 96 blocks of the camera spawn anything; another player's ship uses {@code othersScale}. Client thread only.
 */
public final class KapalFx {

	public static final int WAKE_LIFE = 60;
	private static final int TRAIL_SIZE = 48;
	private static final double RANGE = 96.0D;
	private static final int[] ENGINE_PER_SECOND = {4, 10, 20};

	/** A ring buffer of wake puffs: x, y, z and the entity tick it was laid. */
	public static final class Trail {
		private final double[] data = new double[TRAIL_SIZE * 4];
		private int head;
		private int size;

		void add(double x, double y, double z, double tick) {
			int o = head * 4;
			data[o] = x;
			data[o + 1] = y;
			data[o + 2] = z;
			data[o + 3] = tick;
			head = (head + 1) % TRAIL_SIZE;
			size = Math.min(TRAIL_SIZE, size + 1);
		}

		public int count() {
			return size;
		}

		/** Puff {@code i} (0 oldest): {x, y, z, tick}. */
		public double[] at(int i) {
			int index = Math.floorMod(head - size + i, TRAIL_SIZE);
			int o = index * 4;
			return new double[]{data[o], data[o + 1], data[o + 2], data[o + 3]};
		}
	}

	private static final Int2ObjectMap<Trail> TRAILS = new Int2ObjectOpenHashMap<>();
	private static final List<KapalRohEntity> SHIPS = new ArrayList<>();
	private static final List<KapalBajakEntity> RAIDERS = new ArrayList<>();

	static void loaded(Entity entity) {
		if (entity instanceof KapalRohEntity ship && !SHIPS.contains(ship)) {
			SHIPS.add(ship);
		} else if (entity instanceof KapalBajakEntity raider && !RAIDERS.contains(raider)) {
			RAIDERS.add(raider);
		}
	}

	static void unloaded(Entity entity) {
		if (entity instanceof KapalRohEntity ship) {
			SHIPS.remove(ship);
			TRAILS.remove(ship.getId());
		} else if (entity instanceof KapalBajakEntity raider) {
			RAIDERS.remove(raider);
			TRAILS.remove(raider.getId());
		}
	}

	static void clear() {
		SHIPS.clear();
		RAIDERS.clear();
		TRAILS.clear();
	}

	public static @Nullable Trail trail(int id) {
		return TRAILS.get(id);
	}

	/** The ships of the client level (loaded ones). */
	static List<KapalRohEntity> ships() {
		return SHIPS;
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || (SHIPS.isEmpty() && RAIDERS.isEmpty()) || minecraft.isPaused()) {
			return;
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		int quality = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 2 : 1;
		RAIDERS.removeIf(Entity::isRemoved);
		for (KapalBajakEntity raider : RAIDERS) {
			if (raider.level() == level && raider.distanceToSqr(camera) <= RANGE * RANGE) {
				tickRaider(level, raider, quality, EffectQuality.othersScale());
			}
		}
		SHIPS.removeIf(Entity::isRemoved);
		for (KapalRohEntity ship : SHIPS) {
			if (ship.level() != level || ship.distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			boolean own = minecraft.player != null && (ship.getPassengers().contains(minecraft.player)
					|| minecraft.player.getName().getString().equals(ship.ownerName()));
			float scale = own ? 1.0F : EffectQuality.othersScale();
			tickShip(level, ship, quality, scale);
		}
	}

	private static void tickShip(ClientLevel level, KapalRohEntity ship, int quality, float scale) {
		KapalClass kind = ship.kind();
		KapalModel.Hull hull = KapalModel.of(kind);
		float speed = Math.abs(ship.speed());
		boolean broken = ship.broken();
		boolean running = !ship.docked() && !broken;
		var random = level.getRandom();
		// the engine: motes streaming aft from the stern lanterns
		float perSecond = ENGINE_PER_SECOND[quality] * scale * (running ? 1.0F : 0.25F);
		if (!hull.engines.isEmpty() && random.nextFloat() < perSecond / 20.0F) {
			float[] engine = hull.engines.get(random.nextInt(hull.engines.size()));
			Vec3 at = local(ship, engine[0] + (random.nextFloat() - 0.5F) * 0.6F, engine[1] + 0.5F, engine[2] - 0.7F);
			Vec3 back = local(ship, 0.0F, 0.0F, -1.0F).subtract(local(ship, 0.0F, 0.0F, 0.0F)).scale(0.04D + speed * 0.4D);
			int rgb = KapalRenderer.mixToWhite(ship.color(), 0.55F);
			level.addParticle(Qi.mote(rgb, 0.18F + random.nextFloat() * 0.12F, 18 + random.nextInt(12)), at.x, at.y, at.z, back.x,
					0.01D + random.nextFloat() * 0.02D, back.z);
		}
		// the wake
		if (running && speed > 0.04F && (ship.tickCount % 3 == 0)) {
			Trail trail = TRAILS.computeIfAbsent(ship.getId(), id -> new Trail());
			float half = kind.width() * 0.35F;
			float side = (random.nextFloat() - 0.5F) * 2.0F * half;
			Vec3 at = local(ship, side, 0.2F + random.nextFloat() * 0.8F, -kind.length() * 0.5F - 0.5F);
			trail.add(at.x, at.y, at.z, ship.tickCount);
		}
		// a broken hull smokes and sparks; a hull below half trails a thin smoke
		float hullLeft = kind.hull() <= 0 ? 1.0F : ship.hull() / (float) kind.hull();
		float smoke = broken ? 0.25F : hullLeft < 0.5F ? 0.08F * (1.0F - hullLeft * 2.0F) + 0.02F : 0.0F;
		if (smoke > 0.0F && random.nextFloat() < smoke * scale * (quality + 1) / 2.0F) {
			Vec3 at = local(ship, (random.nextFloat() - 0.5F) * kind.width(), kind.deck() + 0.3F, (random.nextFloat() - 0.5F) * kind.length() * 0.8F);
			level.addParticle(Qi.smoke(0x4A4440, 0.6F + random.nextFloat() * 0.4F, 50), at.x, at.y, at.z, 0.0D, 0.04D, 0.0D);
		}
		// the formation sheds qi dust below a ship under way (Normal and Maximal)
		if (running && quality > 0 && hull.formation[1] > 0.0F && random.nextFloat() < (quality == 2 ? 0.5F : 0.2F) * scale) {
			float a = random.nextFloat() * Mth.TWO_PI;
			float r = hull.formation[1] * (0.6F + random.nextFloat() * 0.4F);
			Vec3 at = local(ship, Mth.cos(a) * r, hull.formation[0] - 0.2F, Mth.sin(a) * r);
			int rgb = KapalRenderer.mixToWhite(ship.color(), 0.5F);
			level.addParticle(Qi.mote(rgb, 0.12F + random.nextFloat() * 0.08F, 24 + random.nextInt(16)), at.x, at.y, at.z, 0.0D,
					-0.02D - random.nextFloat() * 0.03D, 0.0D);
		}
	}

	/** The raider: a grey wake, red embers from its engine, soot shaken from the torn sails. */
	private static void tickRaider(ClientLevel level, KapalBajakEntity raider, int quality, float scale) {
		var random = level.getRandom();
		float speed = raider.speed();
		if (speed > 0.04F && raider.tickCount % 3 == 0) {
			Trail trail = TRAILS.computeIfAbsent(raider.getId(), id -> new Trail());
			Vec3 at = localRaider(raider, (random.nextFloat() - 0.5F) * 3.0F, 0.3F + random.nextFloat() * 0.6F, -8.0F);
			trail.add(at.x, at.y, at.z, raider.tickCount);
		}
		if (random.nextFloat() < ENGINE_PER_SECOND[quality] * scale / 20.0F) {
			Vec3 at = localRaider(raider, (random.nextFloat() - 0.5F) * 0.5F, 1.5F, -7.8F);
			level.addParticle(Qi.mote(0xFF5A3A, 0.16F + random.nextFloat() * 0.1F, 16 + random.nextInt(10)), at.x, at.y, at.z, 0.0D, 0.02D, 0.0D);
		}
		if (quality > 0 && random.nextFloat() < 0.12F * scale) {
			Vec3 at = localRaider(raider, (random.nextFloat() - 0.5F) * 4.0F, 5.0F + random.nextFloat() * 4.0F, (random.nextFloat() - 0.5F) * 6.0F);
			level.addParticle(Qi.smoke(0x2A2422, 0.4F + random.nextFloat() * 0.3F, 40), at.x, at.y, at.z, 0.0D, 0.02D, 0.0D);
		}
	}

	private static Vec3 localRaider(KapalBajakEntity raider, float x, float y, float z) {
		double[] o = KapalClass.toWorld(x, z, raider.getYRot());
		return new Vec3(raider.getX() + o[0], raider.getY() + y, raider.getZ() + o[1]);
	}

	/** A local hull point (x across, y up, z toward the bow) in the world, with the ship's current yaw. */
	static Vec3 local(KapalRohEntity ship, float x, float y, float z) {
		double[] o = KapalClass.toWorld(x, z, ship.getYRot());
		return new Vec3(ship.getX() + o[0], ship.getY() + y, ship.getZ() + o[1]);
	}

	private KapalFx() {
	}
}
