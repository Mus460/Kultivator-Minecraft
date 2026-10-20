package dev.kultivator.client.alam;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.RealmRelicEntity;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.spec.DisplayKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The client-only scenery of a secret realm chamber (DESIGN 25.3.3, the 22.4.3 rules): BlockDisplay walls merged into runs
 * from the server's wall grid, magma floor tiles, corner pillars of the stage (or the ring of glyphs of Mode Ringan), and a
 * Display for each relic entity (pad, exits, stone gates, crystals). Negative ids from −5,000,000, at most 4 created per tick,
 * at most 60 per chamber, never glowing, all removed on a new chamber, the run's end, a level change or a disconnect. The
 * realm's theme is the 7b sky grade of {@link SkyDirector}. Never sent anywhere; no block is touched.
 */
public final class RealmScenery {

	public static final int FIRST_ID = -5_000_000;
	public static final int PER_TICK = 4;
	public static final int MAX_DISPLAYS = 60;
	private static final int SKY_PRIORITY = 1;
	private static final double SCAN_RANGE = 48.0D;

	/** One display waiting to be created. */
	private record Pending(Vec3 at, @Nullable BlockState block, @Nullable String glyph, int rgb, Vector3f translation, Vector3f scale) {
	}

	private static final ArrayDeque<Pending> QUEUE = new ArrayDeque<>();
	private static final List<Display> LIVE = new ArrayList<>();
	/** Relic entity id -> its display and the state it was built for. */
	private static final Map<Integer, Display> RELIC_DISPLAYS = new HashMap<>();
	private static final Map<Integer, Integer> RELIC_STATES = new HashMap<>();

	private static @Nullable CompoundTag scene;
	private static @Nullable ClientLevel owner;
	private static int nextId = FIRST_ID;
	private static int createdThisTick;
	private static int ticks;
	private static boolean broken;
	private static boolean skyOn;

	/** A new chamber: rebuild the scenery. */
	public static void setScene(CompoundTag tag) {
		clear();
		scene = tag.copy();
		Minecraft minecraft = Minecraft.getInstance();
		owner = minecraft.level;
		build();
	}

	/** The run ended: remove everything and fade the realm's sky. */
	public static void endScene() {
		clear();
		scene = null;
		if (skyOn) {
			SkyDirector.release(40, SKY_PRIORITY);
			skyOn = false;
		}
	}

	/** The scene shown now, or null. */
	public static @Nullable CompoundTag scene() {
		return scene;
	}

	/** The realm ordinal of the scene (0 when none). */
	public static int realm() {
		return scene == null ? 0 : scene.getIntOr("realm", 0);
	}

	private static Vec3 origin() {
		CompoundTag s = scene;
		return s == null ? Vec3.ZERO : new Vec3(s.getIntOr("ox", 0), s.getIntOr("oy", 0), s.getIntOr("oz", 0));
	}

	/** The centre of the stage (the origin cell's centre on the floor). */
	public static Vec3 centre() {
		return origin().add(0.5D, 0.0D, 0.5D);
	}

	private static void build() {
		CompoundTag s = scene;
		if (s == null) {
			return;
		}
		Vec3 o = origin();
		String chamber = s.getStringOr("chamber", "");
		int realm = s.getIntOr("realm", 1);
		boolean light = s.getBooleanOr("light", false);
		BlockState wall = wallBlock(chamber, realm);
		int[] walls = s.getIntArray("walls").orElse(new int[0]);
		for (int i = 0; i + 3 < walls.length; i += 4) {
			int x0 = Math.min(walls[i], walls[i + 2]);
			int z0 = Math.min(walls[i + 1], walls[i + 3]);
			int x1 = Math.max(walls[i], walls[i + 2]);
			int z1 = Math.max(walls[i + 1], walls[i + 3]);
			QUEUE.add(new Pending(o.add(x0, 0.0D, z0), wall, null, 0, new Vector3f(), new Vector3f(x1 - x0 + 1, 3.0F, z1 - z0 + 1)));
		}
		int[] tiles = s.getIntArray("tiles").orElse(new int[0]);
		for (int i = 0; i + 3 < tiles.length; i += 4) {
			int x0 = Math.min(tiles[i], tiles[i + 2]);
			int z0 = Math.min(tiles[i + 1], tiles[i + 3]);
			int x1 = Math.max(tiles[i], tiles[i + 2]);
			int z1 = Math.max(tiles[i + 1], tiles[i + 3]);
			QUEUE.add(new Pending(o.add(x0, 0.01D, z0), Blocks.MAGMA_BLOCK.defaultBlockState(), null, 0, new Vector3f(),
					new Vector3f(x1 - x0 + 1, 0.06F, z1 - z0 + 1)));
		}
		int rgb = color(realm);
		if (light) {
			String[] glyphs = {"界", "幻", "灵", "封", "界", "幻", "灵", "封"};
			for (int i = 0; i < glyphs.length; i++) {
				double angle = Math.PI * 2.0D * i / glyphs.length;
				Vec3 at = o.add(0.5D + Math.cos(angle) * 20.0D, 2.5D, 0.5D + Math.sin(angle) * 20.0D);
				QUEUE.add(new Pending(at, null, glyphs[i], rgb, new Vector3f(), new Vector3f(6.0F, 6.0F, 6.0F)));
			}
		} else {
			BlockState pillar = realm == 4 ? Blocks.PURPUR_PILLAR.defaultBlockState() : realm == 3 ? Blocks.PACKED_ICE.defaultBlockState()
					: Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
			int[][] corners = {{-23, -23}, {22, -23}, {-23, 22}, {22, 22}};
			for (int[] corner : corners) {
				QUEUE.add(new Pending(o.add(corner[0], 0.0D, corner[1]), pillar, null, 0, new Vector3f(), new Vector3f(1.0F, 7.0F, 1.0F)));
				QUEUE.add(new Pending(o.add(corner[0] + 0.5D, 8.2D, corner[1] + 0.5D), null, realm == 4 ? "墓" : realm == 3 ? "冰" : realm == 2 ? "虚" : "火",
						rgb, new Vector3f(), new Vector3f(5.0F, 5.0F, 5.0F)));
			}
		}
	}

	/** The wall look per chamber: lava lanes in blackstone or nether bricks, the illusion maze in tinted glass. */
	private static BlockState wallBlock(String chamber, int realm) {
		if ("illusion_maze".equals(chamber)) {
			return Blocks.TINTED_GLASS.defaultBlockState();
		}
		if ("lava_road".equals(chamber)) {
			return realm == 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.NETHER_BRICKS.defaultBlockState();
		}
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	/** The realm's colour (same as the server's {@code RealmSpecs}). */
	public static int color(int realm) {
		return switch (realm) {
			case 1 -> 0xE0503A;
			case 2 -> 0x5AA8FF;
			case 3 -> 0x9FF3FF;
			default -> 0xC89BFF;
		};
	}

	private static SkyDirector.Grade grade(int realm) {
		return switch (realm) {
			case 1 -> SkyDirector.Grade.SK_RED;
			case 2 -> SkyDirector.Grade.SK_ASH;
			case 3 -> SkyDirector.Grade.SK_ICE;
			default -> SkyDirector.Grade.SK_STARS;
		};
	}

	/** Once per client tick (from {@code AlamFx.tick}). */
	public static void tick() {
		ticks++;
		createdThisTick = 0;
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level != owner) {
			LIVE.clear();
			RELIC_DISPLAYS.clear();
			RELIC_STATES.clear();
			QUEUE.clear();
			owner = level;
			if (scene != null) {
				// A level change ends what the old level showed; the server sends a new scene for the next chamber.
				scene = null;
				if (skyOn) {
					SkyDirector.release(40, SKY_PRIORITY);
					skyOn = false;
				}
			}
			return;
		}
		if (level == null || minecraft.player == null) {
			return;
		}
		while (!QUEUE.isEmpty() && createdThisTick < PER_TICK && LIVE.size() < MAX_DISPLAYS) {
			Pending pending = QUEUE.poll();
			Display display = make(pending);
			if (display != null) {
				LIVE.add(display);
			}
		}
		if (ticks % 10 == 0) {
			scanRelics(level, minecraft.player.position());
		}
		CompoundTag s = scene;
		if (s != null && ticks % 100 == 1) {
			Vec3 centre = centre();
			if (minecraft.player.position().distanceToSqr(centre) < 80.0D * 80.0D) {
				int realm = s.getIntOr("realm", 1);
				SkyDirector.grade(grade(realm), realm == 3 ? 0.4F : 0.55F, realm == 4 ? 0.3F : 0.1F, 40, SKY_PRIORITY, centre, true, 1.0F, centre.y);
				skyOn = true;
			}
		}
	}

	/** Builds or refreshes a display per relic within range; removes those of gone relics. */
	private static void scanRelics(ClientLevel level, Vec3 at) {
		List<RealmRelicEntity> relics = level.getEntitiesOfClass(RealmRelicEntity.class, new AABB(at, at).inflate(SCAN_RANGE), e -> true);
		Map<Integer, RealmRelicEntity> seen = new HashMap<>();
		for (RealmRelicEntity relic : relics) {
			seen.put(relic.getId(), relic);
		}
		for (Iterator<Map.Entry<Integer, Display>> it = RELIC_DISPLAYS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Display> entry = it.next();
			RealmRelicEntity relic = seen.get(entry.getKey());
			Integer state = RELIC_STATES.get(entry.getKey());
			boolean stale = relic == null || state == null || stateClass(relic) != state;
			if (stale) {
				remove(entry.getValue());
				LIVE.remove(entry.getValue());
				RELIC_STATES.remove(entry.getKey());
				it.remove();
			}
		}
		for (RealmRelicEntity relic : relics) {
			if (RELIC_DISPLAYS.containsKey(relic.getId()) || createdThisTick >= PER_TICK) {
				continue;
			}
			Pending pending = relicLook(relic);
			if (pending == null) {
				RELIC_STATES.put(relic.getId(), stateClass(relic));
				continue;
			}
			Display display = make(pending);
			if (display != null) {
				RELIC_DISPLAYS.put(relic.getId(), display);
				RELIC_STATES.put(relic.getId(), stateClass(relic));
			}
		}
	}

	/** 0 idle, 1 opened or absorbed (crystal progress 2-100 counts as idle). */
	private static int stateClass(RealmRelicEntity relic) {
		return relic.state() == 1 ? 1 : 0;
	}

	private static @Nullable Pending relicLook(RealmRelicEntity relic) {
		Vec3 at = relic.position();
		boolean done = relic.state() == 1;
		return switch (relic.kind()) {
			case RealmRelicEntity.KIND_PAD -> new Pending(at.add(0.0D, 0.02D, 0.0D), Blocks.SEA_LANTERN.defaultBlockState(), null, 0,
					new Vector3f(-1.0F, 0.0F, -1.0F), new Vector3f(2.0F, 0.05F, 2.0F));
			case RealmRelicEntity.KIND_EXIT -> new Pending(at, Blocks.AMETHYST_BLOCK.defaultBlockState(), null, 0,
					new Vector3f(-0.6F, 0.0F, -0.08F), new Vector3f(1.2F, 2.2F, 0.16F));
			case RealmRelicEntity.KIND_GATE -> done
					? new Pending(at, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), null, 0, new Vector3f(-1.2F, 0.0F, -0.3F), new Vector3f(2.4F, 0.35F, 0.6F))
					: new Pending(at, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), null, 0, new Vector3f(-1.2F, 0.0F, -0.3F), new Vector3f(2.4F, 3.2F, 0.6F));
			case RealmRelicEntity.KIND_CRYSTAL -> done ? null
					: new Pending(at, Blocks.BLUE_ICE.defaultBlockState(), null, 0, new Vector3f(-0.45F, 0.0F, -0.45F), new Vector3f(0.9F, 1.9F, 0.9F));
			default -> null;
		};
	}

	/** The relics in range (for the ambient particles of {@code AlamFx}). */
	public static List<RealmRelicEntity> relicsNear(ClientLevel level, Vec3 at, double range) {
		return level.getEntitiesOfClass(RealmRelicEntity.class, new AABB(at, at).inflate(range), e -> true);
	}

	private static @Nullable Display make(Pending pending) {
		if (pending.glyph() != null) {
			Display.TextDisplay text = create(EntityTypes.TEXT_DISPLAY, pending.at());
			if (text == null) {
				return null;
			}
			text.setText(Component.literal(pending.glyph()).withColor(pending.rgb() & 0xFFFFFF));
			text.setBackgroundColor(0);
			text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
			Vector3f scale = pending.scale();
			DisplayKey.place(text, new Vector3f(), new Quaternionf(), scale);
			text.setWidth(scale.x() / 2.0F);
			text.setHeight(scale.y() / 2.0F);
			return text;
		}
		Display.BlockDisplay block = create(EntityTypes.BLOCK_DISPLAY, pending.at());
		if (block == null || pending.block() == null) {
			return block;
		}
		block.setBlockState(pending.block());
		Vector3f scale = pending.scale();
		DisplayKey.place(block, pending.translation(), new Quaternionf(), scale);
		block.setWidth(Math.max(scale.x(), scale.z()) + 1.0F);
		block.setHeight(scale.y() + 0.5F);
		return block;
	}

	private static <T extends Display> @Nullable T create(EntityType<T> type, Vec3 at) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || broken || createdThisTick >= PER_TICK || LIVE.size() + RELIC_DISPLAYS.size() >= MAX_DISPLAYS + 8) {
			return null;
		}
		try {
			owner = level;
			T display = type.create(level, EntitySpawnReason.LOAD);
			if (display == null) {
				return null;
			}
			display.setId(nextId--);
			if (nextId < FIRST_ID - 1_000_000) {
				nextId = FIRST_ID;
			}
			display.setPos(at.x, at.y, at.z);
			display.setBrightnessOverride(Brightness.FULL_BRIGHT);
			display.setViewRange(4.0F);
			display.setShadowRadius(0.0F);
			level.addEntity(display);
			createdThisTick++;
			return display;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: secret realm scenery turned off after an error", exception);
			return null;
		}
	}

	private static void remove(Display display) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level == owner && !display.isRemoved()) {
			level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Removes every display of the module (new chamber, run end, disconnect). */
	public static void clear() {
		for (Display display : List.copyOf(LIVE)) {
			remove(display);
		}
		for (Display display : List.copyOf(RELIC_DISPLAYS.values())) {
			remove(display);
		}
		LIVE.clear();
		RELIC_DISPLAYS.clear();
		RELIC_STATES.clear();
		QUEUE.clear();
	}

	/** Disconnect: everything goes, including the sky grade. */
	public static void reset() {
		clear();
		scene = null;
		skyOn = false;
		owner = null;
	}

	private RealmScenery() {
	}
}
