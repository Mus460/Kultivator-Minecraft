package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.DisplayKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The scenery of a floor of the Menara Seratus Lantai (DESIGN 34.5.7, {@code op: "scene"}): the band's own hall drawn
 * with client-only BlockDisplays merged into runs (concentric inlays on the floor, eight pillars with their capitals and
 * lamps round the arena, four wall screens, the band's accents), posed once through {@link DisplayKey}; at most 40 /
 * 120 / 240 displays by quality, 4-6 made per tick, negative ids from −7,000,000, never glowing, all removed when the
 * floor ends, on a level change or a disconnect. The band colours the sky ({@link SkyDirector}, its own priority 1, the
 * tower is a dark dimension), a soft wall of light marks the arena's edge, the meditation circle shines on floor 8 of a
 * band, and motes of the band's colour drift about. Nothing is ever a block.
 */
public final class TowerScenery {

	public static final int FIRST_ID = -7_000_000;
	private static final int[] CAP = {40, 120, 240};
	private static final int[] PER_TICK = {4, 4, 6};
	private static final int[] MOTES = {3, 8, 16};
	private static final int SKY_PRIORITY = 1;

	/** One run: a block stretched over a box (relative to the floor centre at standing level). */
	private record Run(BlockState block, float x, float y, float z, float sx, float sy, float sz, boolean light) {
	}

	/** The band's materials: floor inlay, pillar, capital, wall screen, lamp. */
	private record Theme(BlockState inlay, BlockState pillar, BlockState cap, BlockState wall, BlockState lamp) {
	}

	private static final ArrayDeque<Run> QUEUE = new ArrayDeque<>();
	private static final List<Display> LIVE = new ArrayList<>();
	private static @Nullable CompoundTag scene;
	private static @Nullable ClientLevel owner;
	private static Vec3 centre = Vec3.ZERO;
	private static int band;
	private static float arena = 18.0F;
	private static float circle;
	private static int nextId = FIRST_ID;
	private static int createdThisTick;
	private static int ticks;
	private static boolean broken;
	private static boolean skyOn;
	private static final RSink EDGE = new RSink(RTex.STRIP, 256);
	private static final RSink FLOOR = new RSink(RTex.SIGIL, 16);
	private static final RSink RINGQ = new RSink(RTex.RING, 16);

	private static Theme theme(int band) {
		return switch (band) {
			case 1 -> new Theme(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(),
					Blocks.EMERALD_BLOCK.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState());
			case 2 -> new Theme(Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState(),
					Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState());
			case 3 -> new Theme(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.TUFF_BRICKS.defaultBlockState(),
					Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
			case 4 -> new Theme(Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(),
					Blocks.CUT_COPPER.waxed().oxidized().defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState(),
					Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
			case 5 -> new Theme(Blocks.DYED_TERRACOTTA.pink().defaultBlockState(), Blocks.CHERRY_LOG.defaultBlockState(), Blocks.WOOL.pink().defaultBlockState(),
					Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
			case 6 -> new Theme(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.QUARTZ_PILLAR.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState(),
					Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState());
			case 7 -> new Theme(Blocks.CONCRETE.white().defaultBlockState(), Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(),
					Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState());
			case 8 -> new Theme(Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
					Blocks.CRYING_OBSIDIAN.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.VERDANT_FROGLIGHT.defaultBlockState());
			case 9 -> new Theme(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
					Blocks.OBSIDIAN.defaultBlockState(), Blocks.END_ROD.defaultBlockState());
			default -> new Theme(Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
					Blocks.IRON_BLOCK.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.LANTERN.defaultBlockState());
		};
	}

	// ================================================================== scene

	static void setScene(CompoundTag tag) {
		clear();
		scene = tag.copy();
		owner = Minecraft.getInstance().level;
		centre = new Vec3(tag.getIntOr("ox", 0) + 0.5D, tag.getIntOr("oy", 0), tag.getIntOr("oz", 0) + 0.5D);
		band = Mth.clamp(tag.getIntOr("band", 0), 0, 9);
		arena = tag.getFloatOr("arena", 18.0F);
		circle = tag.getFloatOr("circle", 0.0F);
		build();
		sky();
	}

	static void endScene() {
		clear();
		scene = null;
		if (skyOn) {
			SkyDirector.release(40, SKY_PRIORITY);
			skyOn = false;
		}
	}

	/** The floor's band (0-9) while a floor is shown, -1 otherwise. */
	static int band() {
		return scene == null ? -1 : band;
	}

	private static void build() {
		Theme t = theme(band);
		List<Run> runs = new ArrayList<>();
		// 1. the floor: a cross and two square frames inlaid (thin, just above the floor)
		float f = -0.99F;
		float h = 1.0F;
		runs.add(new Run(t.inlay(), -13.0F, f, -0.5F, 27.0F, h, 1.0F, false));
		runs.add(new Run(t.inlay(), -0.5F, f, -13.0F, 1.0F, h, 27.0F, false));
		frame(runs, t.inlay(), 13.0F, f, h);
		frame(runs, t.inlay(), 5.0F, f, h);
		// 2. eight pillars round the arena with capitals and lamps
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0D + Math.PI / 8.0D;
			float x = (float) Math.cos(a) * (arena + 2.0F);
			float z = (float) Math.sin(a) * (arena + 2.0F);
			runs.add(new Run(t.pillar(), x - 0.5F, 0.0F, z - 0.5F, 1.0F, 7.0F, 1.0F, false));
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0D + Math.PI / 8.0D;
			float x = (float) Math.cos(a) * (arena + 2.0F);
			float z = (float) Math.sin(a) * (arena + 2.0F);
			runs.add(new Run(t.cap(), x - 0.75F, 7.0F, z - 0.75F, 1.5F, 0.6F, 1.5F, false));
			runs.add(new Run(t.lamp(), x - 0.3F, 7.6F, z - 0.3F, 0.6F, 0.6F, 0.6F, true));
		}
		// 3. four wall screens beyond the pillars (the way in stays open to the south)
		float w = arena + 5.0F;
		runs.add(new Run(t.wall(), -4.5F, 0.0F, -w - 0.5F, 9.0F, 5.0F, 1.0F, false));
		runs.add(new Run(t.wall(), -w - 0.5F, 0.0F, -4.5F, 1.0F, 5.0F, 9.0F, false));
		runs.add(new Run(t.wall(), w - 0.5F, 0.0F, -4.5F, 1.0F, 5.0F, 9.0F, false));
		runs.add(new Run(t.cap(), -4.5F, 5.0F, -w - 0.5F, 9.0F, 0.5F, 1.0F, false));
		runs.add(new Run(t.cap(), -w - 0.5F, 5.0F, -4.5F, 1.0F, 0.5F, 9.0F, false));
		runs.add(new Run(t.cap(), w - 0.5F, 5.0F, -4.5F, 1.0F, 0.5F, 9.0F, false));
		// 4. the band's accents
		accents(runs, t);
		// 5. the meditation circle (floor 8 of a band)
		if (circle > 0.0F) {
			float r = circle;
			runs.add(new Run(Blocks.GOLD_BLOCK.defaultBlockState(), -r, f + 0.01F, -r, 2.0F * r, h, 0.25F, false));
			runs.add(new Run(Blocks.GOLD_BLOCK.defaultBlockState(), -r, f + 0.01F, r - 0.25F, 2.0F * r, h, 0.25F, false));
			runs.add(new Run(Blocks.GOLD_BLOCK.defaultBlockState(), -r, f + 0.01F, -r, 0.25F, h, 2.0F * r, false));
			runs.add(new Run(Blocks.GOLD_BLOCK.defaultBlockState(), r - 0.25F, f + 0.01F, -r, 0.25F, h, 2.0F * r, false));
		}
		int cap = CAP[EffectQuality.level().ordinal()];
		for (int i = 0; i < runs.size() && i < cap; i++) {
			QUEUE.add(runs.get(i));
		}
	}

	private static void frame(List<Run> runs, BlockState block, float r, float y, float h) {
		runs.add(new Run(block, -r - 0.5F, y, -r - 0.5F, 2.0F * r + 1.0F, h, 1.0F, false));
		runs.add(new Run(block, -r - 0.5F, y, r - 0.5F, 2.0F * r + 1.0F, h, 1.0F, false));
		runs.add(new Run(block, -r - 0.5F, y, -r + 0.5F, 1.0F, h, 2.0F * r - 1.0F, false));
		runs.add(new Run(block, r - 0.5F, y, -r + 0.5F, 1.0F, h, 2.0F * r - 1.0F, false));
	}

	/** What makes each band's hall its own (beyond the materials). */
	private static void accents(List<Run> runs, Theme t) {
		switch (band) {
			case 0 -> {
				// iron: chains of iron bars hanging between the pillars
				for (int i = 0; i < 4; i++) {
					double a = i * Math.PI / 2.0D;
					float x = (float) Math.cos(a) * (arena + 1.0F);
					float z = (float) Math.sin(a) * (arena + 1.0F);
					runs.add(new Run(Blocks.IRON_CHAIN.defaultBlockState(), x - 0.5F, 3.0F, z - 0.5F, 1.0F, 4.0F, 1.0F, false));
				}
			}
			case 2 -> {
				// fire: magma tiles in the corners of the inner frame
				for (int sx = -1; sx <= 1; sx += 2) {
					for (int sz = -1; sz <= 1; sz += 2) {
						runs.add(new Run(Blocks.MAGMA_BLOCK.defaultBlockState(), sx * 9.0F - 1.0F, -0.98F, sz * 9.0F - 1.0F, 2.0F, 1.0F, 2.0F, true));
					}
				}
			}
			case 3 -> {
				// stone turtle: four boulders of moss round the centre
				for (int i = 0; i < 4; i++) {
					double a = i * Math.PI / 2.0D + Math.PI / 4.0D;
					float x = (float) Math.cos(a) * 10.0F;
					float z = (float) Math.sin(a) * 10.0F;
					runs.add(new Run(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), x - 1.0F, 0.0F, z - 1.0F, 2.0F, 1.2F, 2.0F, false));
				}
			}
			case 4 -> {
				// thunder: lightning rods on the capitals
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4.0D + Math.PI / 8.0D;
					float x = (float) Math.cos(a) * (arena + 2.0F);
					float z = (float) Math.sin(a) * (arena + 2.0F);
					runs.add(new Run(Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState(), x - 0.5F, 8.2F, z - 0.5F, 1.0F, 1.0F, 1.0F, false));
				}
			}
			case 6 -> runs.add(new Run(Blocks.GOLD_BLOCK.defaultBlockState(), -1.5F, -0.97F, -1.5F, 3.0F, 1.0F, 3.0F, false));
			case 8, 9 -> {
				// shadow and the true immortal floors: a dark mirror pool at the centre
				runs.add(new Run(Blocks.OBSIDIAN.defaultBlockState(), -2.5F, -0.97F, -2.5F, 5.0F, 1.0F, 5.0F, false));
				runs.add(new Run(t.cap(), -0.5F, -0.96F, -0.5F, 1.0F, 1.0F, 1.0F, true));
			}
			default -> {
			}
		}
	}

	private static void sky() {
		if (scene == null) {
			return;
		}
		int rgb = LobbyState.bandRgb(band + 1);
		SkyDirector.dao(rgb, 0.35F, 40, SKY_PRIORITY, centre, true, 1.0F);
		skyOn = true;
	}

	// ================================================================== tick

	static void tick(Minecraft minecraft) {
		ticks++;
		createdThisTick = 0;
		ClientLevel level = minecraft.level;
		if (level != owner) {
			LIVE.clear();
			QUEUE.clear();
			owner = level;
			if (scene != null) {
				scene = null;
				if (skyOn) {
					SkyDirector.release(40, SKY_PRIORITY);
					skyOn = false;
				}
			}
			return;
		}
		if (level == null || minecraft.player == null || scene == null) {
			return;
		}
		int perTick = PER_TICK[EffectQuality.level().ordinal()];
		while (!QUEUE.isEmpty() && createdThisTick < perTick) {
			Run run = QUEUE.poll();
			Display display = make(run);
			if (display != null) {
				LIVE.add(display);
			}
		}
		if (ticks % 100 == 1 && minecraft.player.position().distanceToSqr(centre) < 64.0D * 64.0D) {
			sky();
		}
		if (Qi.on()) {
			RandomSource random = level.getRandom();
			int perSecond = MOTES[EffectQuality.level().ordinal()];
			if (random.nextInt(20) < perSecond) {
				Vec3 p = minecraft.player.position();
				int rgb = LobbyState.bandRgb(band + 1);
				level.addParticle(Qi.mote(rgb, 0.06F + random.nextFloat() * 0.05F, 60), p.x + random.nextGaussian() * 9.0D, centre.y + 0.5D + random.nextDouble() * 5.0D,
						p.z + random.nextGaussian() * 9.0D, 0.0D, 0.01D + random.nextDouble() * 0.015D, 0.0D);
			}
		}
	}

	private static @Nullable Display make(Run run) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || broken) {
			return null;
		}
		try {
			Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.LOAD);
			if (display == null) {
				return null;
			}
			display.setId(nextId--);
			if (nextId < FIRST_ID - 1_000_000) {
				nextId = FIRST_ID;
			}
			display.setPos(centre.x + run.x(), centre.y + run.y(), centre.z + run.z());
			display.setBlockState(run.block());
			display.setBrightnessOverride(run.light() ? Brightness.FULL_BRIGHT : new Brightness(11, 11));
			display.setViewRange(2.0F);
			display.setShadowRadius(0.0F);
			Vector3f scale = new Vector3f(run.sx(), run.sy(), run.sz());
			DisplayKey.place(display, new Vector3f(), new Quaternionf(), scale);
			display.setWidth(Math.max(run.sx(), run.sz()) + 1.0F);
			display.setHeight(run.sy() + 0.5F);
			level.addEntity(display);
			createdThisTick++;
			return display;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: tower scenery turned off after an error", exception);
			return null;
		}
	}

	static void clear() {
		ClientLevel level = Minecraft.getInstance().level;
		for (Display display : LIVE) {
			if (level != null && level == owner && !display.isRemoved()) {
				level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		LIVE.clear();
		QUEUE.clear();
	}

	static void reset() {
		clear();
		scene = null;
		skyOn = false;
		owner = null;
	}

	// ================================================================== render: the arena's edge and the floor sigil

	static void render(LevelRenderContext context) {
		if (scene == null || ShaderCompat.shadowPass()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		if (cam.distanceToSqr(centre) > 80.0D * 80.0D) {
			return;
		}
		float time = ticks + context.levelState().worldPartialTicks;
		float x = (float) (centre.x - cam.x);
		float y = (float) (centre.y - cam.y);
		float z = (float) (centre.z - cam.z);
		int rgb = LobbyState.bandRgb(band + 1);
		EDGE.reset();
		FLOOR.reset();
		RINGQ.reset();
		int segments = EffectQuality.minimal() ? 24 : EffectQuality.maximal() ? 64 : 40;
		float breath = 0.2F + 0.08F * Mth.sin(time * 0.05F);
		EDGE.band(x, y, y + 1.6F, z, arena + 0.5F, segments, RSink.argb(breath, rgb), RSink.argb(0.0F, rgb));
		FLOOR.flat(x, y + 0.03F, z, 4.5F, time * 0.004F, RSink.argb(0.35F, rgb));
		if (circle > 0.0F) {
			RINGQ.flat(x, y + 0.05F, z, circle + 0.3F, time * 0.01F, RSink.argb(0.55F + 0.2F * Mth.sin(time * 0.1F), 0xFFE6A0));
		}
		PoseStack pose = context.poseStack();
		EDGE.submit(context.submitNodeCollector(), pose);
		FLOOR.submit(context.submitNodeCollector(), pose);
		RINGQ.submit(context.submitNodeCollector(), pose);
	}

	private TowerScenery() {
	}
}
