package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.Kultivator;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.fx.spec.DisplayKey;
import dev.kultivator.client.fx.spec.PhantomDisplays;
import dev.kultivator.pusakalangit.TitahMonumentEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Giant decree weapons this client cannot see as an entity (Tingkat Titah, DESIGN 29.16.10, contract C10b.9): the planted
 * monuments of the server's {@code TitahFar} list whose entity is not in the client level or stands beyond render
 * distance, and the falling weapons of tier 2-9 decrees (their props are too large and too high for the 64 b phantom
 * displays). Everything here is client-only and visual; nothing is read from or sent to the server besides that list.
 * <p>
 * Three LOD steps, nearest first, at most 3 / 6 / 12 relics (Minimal / Normal / Maximal):
 * <ol>
 * <li><b>within render distance</b> (base ≤ render distance − 16 b): a client {@code ItemDisplay} of the weapon's
 * {@code kultivator:fx/<model>} at its true scale (model only, no parts, no glow quads), posed through
 * {@link DisplayKey}; view range, width and height follow the scaled model so Sodium and culling mods keep it;</li>
 * <li><b>beyond it</b> (no shader pack, Normal or Maximal): the same display as a perspective impostor, standing at
 * {@code C + (A − C) × Dm / D} (camera C, anchor A, {@code Dm} = render distance − 24 b) at {@code scale × Dm / D},
 * re-keyed every client tick, so its angular size is exact and terrain nearer than {@code Dm} still hides it;</li>
 * <li><b>Minimal, a shader pack, or the 7b frame guard</b>: two additive silhouette quads ({@code sword_far.png} /
 * {@code pillar_far.png} and a soft glow strip) through the Phase 10 quad path, which never draws in Iris's shadow pass,
 * with the same projection.</li>
 * </ol>
 * Item stacks are made lazily ({@link ModelFx#stack}). Every method never throws into the game; client thread only.
 */
public final class FarRelic {

	/** Displays of this class count down from here (−8,000,000; the other client display pools use −2 to −7 million). */
	public static final int FIRST_ID = -8_000_000;
	private static final int CREATE_PER_TICK = 2;
	private static final int MAX_MONUMENTS = 24;
	private static final int MAX_FALLS = 8;
	private static final int ENTITY_CHECK = 10;

	/** The model's pose constants at scale 1 (blocks; the renderer's {@code SWORD_TIP} / {@code PILLAR_HALF}). */
	private static final float SWORD_TIP = 1.47F;
	private static final float SWORD_TOP = 0.52F;
	private static final float PILLAR_HALF = 1.5F;
	private static final float SEAL_TOP = 1.1F;

	private static final class Relic {
		final boolean monument;
		final int record;
		final long cast;
		int kind;
		String model;
		double x;
		double y;
		double z;
		float scale;
		float yaw;
		float tilt;
		int state;
		int tier;
		// falling
		double start;
		float height;
		int duration = 1;
		boolean hold;
		boolean landed;
		double endAt = Double.NaN;
		double liftStart = Double.NaN;
		float liftHeight;
		int liftTicks = 1;
		// runtime
		@Nullable Display.ItemDisplay display;
		@Nullable DisplayKey key;
		int lod;
		double distance;
		boolean entityHere;
		int checkIn;
		boolean placed;

		Relic(boolean monument, int record, long cast) {
			this.monument = monument;
			this.record = record;
			this.cast = cast;
		}

		boolean sword() {
			return kind == RelicFall.SWORD;
		}

		/** The ground point under the weapon at {@code now} (a falling one: raised by its remaining fall and lift). */
		Vec3 base(double now) {
			if (monument) {
				return new Vec3(x, y, z);
			}
			float p = (float) Math.max(0.0D, Math.min(1.0D, (now - start) / Math.max(1, duration)));
			double up = height * (1.0F - p * p);
			if (!Double.isNaN(liftStart)) {
				float q = (float) Math.max(0.0D, Math.min(1.0D, (now - liftStart) / Math.max(1, liftTicks)));
				up += liftHeight * q * q;
			}
			return new Vec3(x, y + up, z);
		}

		/** The anchor (the model's pivot) above the base in the planted pose. */
		float anchorUp() {
			return switch (kind) {
				case RelicFall.SWORD -> SWORD_TIP * scale * 0.75F;
				case RelicFall.PILLAR -> PILLAR_HALF * scale * 0.5F;
				default -> 0.02F;
			};
		}

		/** How deep the model reaches below the base (a quarter of the blade, a quarter of the pillar). */
		float sink() {
			return switch (kind) {
				case RelicFall.SWORD -> SWORD_TIP * scale * 0.25F;
				case RelicFall.PILLAR -> PILLAR_HALF * scale * 0.5F;
				default -> 0.0F;
			};
		}

		/** The model's height above the anchor. */
		float top() {
			return switch (kind) {
				case RelicFall.SWORD -> SWORD_TOP * scale;
				case RelicFall.PILLAR -> PILLAR_HALF * scale;
				default -> SEAL_TOP * scale;
			};
		}

		float width() {
			return switch (kind) {
				case RelicFall.SWORD -> 0.55F * scale;
				case RelicFall.PILLAR -> 0.45F * scale;
				default -> 0.9F * scale;
			};
		}
	}

	private static final Map<Integer, Relic> MONUMENTS = new HashMap<>();
	private static final List<Relic> FALLS = new ArrayList<>();
	private static final List<Relic> SHOWN = new ArrayList<>();
	private static @Nullable ClientLevel dataLevel;
	private static @Nullable ClientLevel displayLevel;
	private static int nextId = FIRST_ID;
	private static boolean broken;
	private static final int[] LODS = new int[4];

	private FarRelic() {
	}

	// ================================================================== the TitahFar list (C10b.9)

	/**
	 * One {@code TitahFar}: {@code m} = the live monuments of this client's dimension ({@code id, x, y, z, model, scale,
	 * yaw, tilt, state, born, tier}); it replaces the list.
	 */
	public static void onFar(CompoundTag data) {
		try {
			ClientLevel level = Minecraft.getInstance().level;
			if (level != dataLevel) {
				dropMonuments();
				dataLevel = level;
			}
			ListTag list = data.getListOrEmpty("m");
			Map<Integer, Relic> fresh = new HashMap<>();
			for (int i = 0; i < list.size() && fresh.size() < MAX_MONUMENTS; i++) {
				CompoundTag tag = list.getCompoundOrEmpty(i);
				int id = tag.getIntOr("id", -1);
				double x = tag.getDoubleOr("x", Double.NaN);
				double y = tag.getDoubleOr("y", Double.NaN);
				double z = tag.getDoubleOr("z", Double.NaN);
				if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
					continue;
				}
				Relic relic = MONUMENTS.remove(id);
				if (relic == null) {
					relic = new Relic(true, id, 0L);
				}
				int model = Math.max(0, Math.min(TitahMonumentEntity.MODELS.length - 1, tag.getByteOr("model", (byte) 0)));
				String modelId = TitahMonumentEntity.MODELS[model];
				if (relic.model != null && !relic.model.equals(modelId)) {
					remove(relic);
				}
				relic.model = modelId;
				relic.kind = modelId.endsWith("_sword") ? RelicFall.SWORD : RelicFall.PILLAR;
				relic.x = x;
				relic.y = y;
				relic.z = z;
				float scale = tag.getFloatOr("scale", 12.0F);
				relic.scale = Float.isFinite(scale) ? Math.max(0.5F, Math.min(TitahMonumentRenderer.MAX_SCALE, scale)) : 12.0F;
				float yaw = tag.getFloatOr("yaw", 0.0F);
				relic.yaw = Float.isFinite(yaw) ? yaw : 0.0F;
				float tilt = tag.getFloatOr("tilt", 0.0F);
				relic.tilt = Float.isFinite(tilt) ? Math.max(-30.0F, Math.min(30.0F, tilt)) : 0.0F;
				relic.state = tag.getByteOr("state", (byte) 1);
				relic.tier = Math.max(1, Math.min(9, tag.getByteOr("tier", (byte) 1)));
				relic.checkIn = 0;
				fresh.put(id, relic);
			}
			dropMonuments();
			MONUMENTS.putAll(fresh);
			if (!MONUMENTS.isEmpty()) {
				TitahWorld.touch();
			}
		} catch (RuntimeException exception) {
			TitahWorld.warn("far list", exception);
		}
	}

	// ================================================================== falling weapons (RelicFall, tier 2-9)

	/**
	 * A tier 2-9 weapon falls onto {@code ground} from {@code height} b over {@code duration} ticks at {@code scale}
	 * ({@code kind} one of {@link RelicFall#SWORD}, {@link RelicFall#PILLAR}, {@link RelicFall#SEAL}); {@code hold} keeps it
	 * on the ground until {@link #liftAway}. False when not taken (the caller then shows its usual prop).
	 */
	static boolean falling(long cast, String model, int kind, Vec3 ground, float height, int duration, float scale, float yaw, boolean hold, int tier) {
		if (!TitahWorld.finite(ground) || model == null || kind == RelicFall.STAR) {
			return false;
		}
		if (FALLS.size() >= MAX_FALLS) {
			Relic oldest = FALLS.remove(0);
			remove(oldest);
		}
		Relic relic = new Relic(false, -1, cast);
		relic.kind = kind;
		relic.model = ModelFx.known(model) ? model : "heaven_splitting_sword";
		relic.x = ground.x;
		relic.y = ground.y;
		relic.z = ground.z;
		relic.scale = Math.max(0.5F, Math.min(TitahMonumentRenderer.MAX_SCALE, scale));
		relic.yaw = yaw;
		relic.height = Math.max(4.0F, height);
		relic.duration = Math.max(1, duration);
		relic.hold = hold;
		relic.tier = tier;
		relic.state = 1;
		relic.start = TitahWorld.clock();
		FALLS.add(relic);
		TitahWorld.touch();
		return true;
	}

	/** The cast's climax: its falling weapon landed; the monument (entity or far list) takes over, a held seal stays. */
	static void land(long cast) {
		double now = TitahWorld.clock();
		for (Relic relic : FALLS) {
			if (relic.cast == cast && !relic.landed) {
				relic.landed = true;
				relic.start = Math.min(relic.start, now - relic.duration);
				if (!relic.hold) {
					relic.endAt = now + 4.0D;
				} else {
					relic.endAt = now + 400.0D;
				}
			}
		}
	}

	/** A held weapon (the seal) rises {@code up} b over {@code ticks} and is gone. */
	static void liftAway(long cast, float up, int ticks) {
		double now = TitahWorld.clock();
		for (Relic relic : FALLS) {
			if (relic.cast == cast && relic.hold && Double.isNaN(relic.liftStart)) {
				relic.liftStart = now;
				relic.liftHeight = up;
				relic.liftTicks = Math.max(4, ticks);
				relic.endAt = now + relic.liftTicks;
			}
		}
	}

	static void abort(long cast) {
		for (Iterator<Relic> it = FALLS.iterator(); it.hasNext(); ) {
			Relic relic = it.next();
			if (relic.cast == cast) {
				remove(relic);
				it.remove();
			}
		}
	}

	// ================================================================== queries

	/** Relics known now (monuments of the far list plus falling weapons). */
	static int count() {
		return MONUMENTS.size() + FALLS.size();
	}

	/** True when a far monument at about {@code x, z} is drawn here now (the entity renderer then steps aside). */
	static boolean drawsAt(double x, double z) {
		for (Relic relic : SHOWN) {
			if (relic.monument && Math.abs(relic.x - x) < 3.0D && Math.abs(relic.z - z) < 3.0D) {
				return true;
			}
		}
		return false;
	}

	/** One line for {@code /sinematik pusaka info}: relics known, shown and per LOD step. */
	public static String stats() {
		return String.format(Locale.ROOT, "jauh %d (monumen %d, jatuh %d), tampil %d, LOD1/2/3 %d/%d/%d", count(), MONUMENTS.size(), FALLS.size(), SHOWN.size(),
				LODS[1], LODS[2], LODS[3]);
	}

	/** {@code /sinematik pusaka jauh}: every relic with its distance, LOD step and scale (shown first). */
	public static List<String> describe() {
		List<String> lines = new ArrayList<>();
		List<Relic> all = new ArrayList<>(MONUMENTS.values());
		all.addAll(FALLS);
		all.sort((a, b) -> Double.compare(a.distance, b.distance));
		for (Relic relic : all) {
			String what = relic.monument ? "monumen #" + relic.record : "jatuh";
			String lod = SHOWN.contains(relic) ? "LOD " + relic.lod : relic.entityHere ? "entitas" : "-";
			lines.add(String.format(Locale.ROOT, "%s %s · %.0f b · %s · skala %.0f · tingkat %d", what, relic.model, relic.distance, lod, relic.scale, relic.tier));
		}
		return lines;
	}

	/** Removes every display and forgets the falls (stop, disconnect, dimension change); the far list stays per level. */
	static void clear() {
		for (Relic relic : FALLS) {
			remove(relic);
		}
		FALLS.clear();
		for (Relic relic : MONUMENTS.values()) {
			remove(relic);
		}
		SHOWN.clear();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != dataLevel) {
			MONUMENTS.clear();
		}
	}

	private static void dropMonuments() {
		for (Relic relic : MONUMENTS.values()) {
			remove(relic);
		}
		MONUMENTS.clear();
	}

	// ================================================================== tick: LOD choice and the displays

	static void tick(ClientLevel level, double now) {
		if (level != dataLevel && !MONUMENTS.isEmpty()) {
			dropMonuments();
		}
		if (level != displayLevel) {
			// the old level's displays went with it
			for (Relic relic : FALLS) {
				relic.display = null;
				relic.key = null;
			}
			for (Relic relic : MONUMENTS.values()) {
				relic.display = null;
				relic.key = null;
			}
			displayLevel = level;
		}
		for (Iterator<Relic> it = FALLS.iterator(); it.hasNext(); ) {
			Relic relic = it.next();
			if (!Double.isNaN(relic.endAt) && now >= relic.endAt) {
				remove(relic);
				it.remove();
			}
		}
		SHOWN.clear();
		java.util.Arrays.fill(LODS, 0);
		if (MONUMENTS.isEmpty() && FALLS.isEmpty()) {
			return;
		}
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double near = TitahWorld.viewRange() - 16.0D;
		List<Relic> candidates = new ArrayList<>(MONUMENTS.size() + FALLS.size());
		for (Relic relic : MONUMENTS.values()) {
			if (relic.state >= TitahMonumentEntity.STATE_LEAVING) {
				remove(relic);
				continue;
			}
			if (--relic.checkIn <= 0) {
				relic.checkIn = ENTITY_CHECK;
				relic.entityHere = entityHere(level, relic);
			}
			relic.distance = camera.distanceTo(relic.base(now));
			if (relic.entityHere && relic.distance <= near) {
				// the real entity draws it
				remove(relic);
				continue;
			}
			candidates.add(relic);
		}
		for (Relic relic : FALLS) {
			relic.distance = camera.distanceTo(relic.base(now));
			candidates.add(relic);
		}
		candidates.sort((a, b) -> Double.compare(a.distance, b.distance));
		int index = DampakWorldBudget.index();
		int cap = DampakWorldBudget.FAR_RELICS[index];
		boolean quadsOnly = index <= 0 || broken || !PhantomDisplays.enabled() || !DampakWorldBudget.worldFx();
		boolean farQuads = quadsOnly || ShaderCompat.packOn() || DampakWorldBudget.guardDropped();
		int created = 0;
		for (int i = 0; i < candidates.size(); i++) {
			Relic relic = candidates.get(i);
			if (i >= cap) {
				remove(relic);
				continue;
			}
			int lod = quadsOnly ? 3 : relic.distance <= near ? 1 : farQuads ? 3 : 2;
			if (lod != 3 && relic.display == null) {
				if (created < CREATE_PER_TICK && create(level, relic)) {
					created++;
				} else {
					// no display (yet): the quads stand in this tick
					lod = 3;
				}
			}
			if (lod == 3) {
				remove(relic);
			} else {
				pose(relic, camera, now, lod, near);
			}
			relic.lod = lod;
			LODS[lod]++;
			SHOWN.add(relic);
		}
		if (!SHOWN.isEmpty()) {
			TitahWorld.touch();
		}
	}

	/** True when the monument's entity is in the client level (its renderer draws it while it is in range). */
	private static boolean entityHere(ClientLevel level, Relic relic) {
		try {
			AABB box = new AABB(relic.x - 3.0D, relic.y - 64.0D, relic.z - 3.0D, relic.x + 3.0D, relic.y + 64.0D, relic.z + 3.0D);
			return !level.getEntitiesOfClass(TitahMonumentEntity.class, box, Entity::isAlive).isEmpty();
		} catch (RuntimeException exception) {
			return false;
		}
	}

	private static boolean create(ClientLevel level, Relic relic) {
		try {
			Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.LOAD);
			if (display == null) {
				return false;
			}
			display.setId(nextId--);
			if (nextId <= FIRST_ID - 1_000_000) {
				nextId = FIRST_ID;
			}
			Vec3 base = relic.base(TitahWorld.clock());
			display.setPos(base.x, base.y, base.z);
			// made on first use, never in a static initializer
			display.setItemStack(ModelFx.stack(relic.model));
			display.setItemTransform(ItemDisplayContext.FIXED);
			display.setBrightnessOverride(Brightness.FULL_BRIGHT);
			display.setShadowRadius(0.0F);
			display.setViewRange(4.0F);
			level.addEntity(display);
			relic.display = display;
			relic.key = new DisplayKey(display);
			relic.placed = false;
			displayLevel = level;
			return true;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: Titah far relic displays turned off after an error; silhouettes stand in", exception);
			return false;
		}
	}

	/**
	 * Poses a relic's display for LOD 1 (true size) or 2 (the impostor at {@code Dm = near − 8} b): the entity at the
	 * model's lowest point, the transformation lifting the model's pivot, turning it as the monument renderer does (the
	 * item display adds its own half turn about y, undone here) and scaling it.
	 */
	private static void pose(Relic relic, Vec3 camera, double now, int lod, double near) {
		Display.ItemDisplay display = relic.display;
		DisplayKey key = relic.key;
		if (display == null || key == null || display.isRemoved()) {
			relic.display = null;
			relic.key = null;
			return;
		}
		Vec3 base = relic.base(now);
		double k = 1.0D;
		if (lod == 2) {
			double dm = Math.max(32.0D, near - 8.0D);
			k = relic.distance > dm ? dm / relic.distance : 1.0D;
		}
		Vec3 projected = k >= 1.0D ? base : camera.add(base.subtract(camera).scale(k));
		float kf = (float) k;
		float sink = relic.sink() * kf;
		Vec3 at = projected.subtract(0.0D, sink, 0.0D);
		Quaternionf lean = new Quaternionf().rotateY((float) Math.toRadians(-relic.yaw));
		if (relic.sword()) {
			lean.rotateX((float) Math.toRadians(-relic.tilt));
		}
		Vector3f pivot = lean.transform(new Vector3f(0.0F, relic.anchorUp() * kf, 0.0F)).add(0.0F, sink, 0.0F);
		Quaternionf rotation = new Quaternionf(lean);
		if (relic.sword()) {
			rotation.rotateZ((float) Math.PI);
		}
		rotation.rotateY((float) -Math.PI);
		float s = relic.scale * kf;
		Vector3f scale = new Vector3f(s, s, s);
		float height = (relic.sink() + relic.anchorUp() + relic.top()) * kf + 1.0F;
		float width = Math.max(2.0F, relic.width() * kf * 1.6F);
		if (Math.abs(display.getWidth() - width) > 0.5F || Math.abs(display.getHeight() - height) > 0.5F) {
			display.setWidth(width);
			display.setHeight(height);
		}
		float range = (float) ((relic.distance * k + height) / 64.0D + 1.0D);
		if (Math.abs(display.getViewRange() - range) > 0.25F) {
			display.setViewRange(range);
		}
		if (!relic.placed) {
			relic.placed = true;
			display.setPos(at.x, at.y, at.z);
			key.snap(pivot, rotation, scale);
			return;
		}
		key.move(at);
		key.key(pivot, rotation, scale, 1);
	}

	private static void remove(Relic relic) {
		Display.ItemDisplay display = relic.display;
		relic.display = null;
		relic.key = null;
		relic.placed = false;
		if (display == null) {
			return;
		}
		try {
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null && level == displayLevel && !display.isRemoved()) {
				level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
			}
		} catch (RuntimeException exception) {
			TitahWorld.warn("far remove", exception);
		}
	}

	// ================================================================== render: LOD 3 silhouettes

	static void render(SigilRenderer.Frame frame) {
		if (SHOWN.isEmpty()) {
			return;
		}
		double near = TitahWorld.viewRange() - 16.0D;
		double dm = Math.max(32.0D, near - 8.0D);
		for (int i = 0; i < SHOWN.size(); i++) {
			Relic relic = SHOWN.get(i);
			if (relic.lod != 3 || !TitahWorld.quads(2)) {
				continue;
			}
			Vec3 base = relic.base(frame.clock);
			if (relic.monument && relic.distance <= near) {
				// the client heightmap where it is loaded (the entity renderer does the same)
				base = new Vec3(base.x, DampakWorld.titahGround(frame.level, base.x, base.z, base.y), base.z);
			}
			double dx = frame.rx(base.x);
			double dy = frame.ry(base.y);
			double dz = frame.rz(base.z);
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double k = d > dm ? dm / d : 1.0D;
			float kf = (float) k;
			float bx = (float) (dx * k);
			float by = (float) (dy * k);
			float bz = (float) (dz * k);
			float sink = relic.sink() * kf;
			float length = (relic.sink() + relic.anchorUp() + relic.top()) * kf;
			float width = Math.max(0.6F, relic.width() * kf);
			if (relic.kind == RelicFall.SEAL) {
				// the seal from afar: a soft jade-gold glow over its face
				frame.billboard(frame.ground, bx, by + relic.top() * kf * 0.5F, bz, Math.max(1.0F, relic.scale * 0.5F * kf), TitahWorld.argb(0.7F, 0xFFE8A0));
				continue;
			}
			// the axis from the lowest point (the sword's point) up, leaning like the monument
			Quaternionf lean = new Quaternionf().rotateY((float) Math.toRadians(-relic.yaw));
			if (relic.sword()) {
				lean.rotateX((float) Math.toRadians(-relic.tilt));
			}
			Vector3f up = lean.transform(new Vector3f(0.0F, 1.0F, 0.0F));
			float ax = bx - up.x * sink;
			float ay = by - up.y * sink;
			float az = bz - up.z * sink;
			float tx = ax + up.x * length;
			float ty = ay + up.y * length;
			float tz = az + up.z * length;
			boolean warm = !relic.sword() || "zhuxian_sword".equals(relic.model);
			int rgb = warm ? TitahWorld.EMBER : TitahWorld.WHITE_GOLD;
			float breathe = 0.85F + 0.15F * (float) Math.sin(frame.clock * 0.05D + relic.record);
			// the soft glow behind, then the silhouette itself
			frame.beam(frame.glow, ax, ay, az, tx, ty, tz, width * 2.6F, TitahWorld.argb(0.35F * breathe, rgb), TitahWorld.argb(0.2F * breathe, rgb));
			frame.beam(relic.sword() ? frame.farSword : frame.farPillar, ax, ay, az, tx, ty, tz, width, TitahWorld.argb(0.9F, 0xFFFFFF),
					TitahWorld.argb(0.9F, 0xFFFFFF));
		}
	}
}
