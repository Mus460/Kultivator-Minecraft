package dev.kultivator.client.epic.fx;

import com.mojang.math.Transformation;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-only Display entities of 7b (DESIGN 23.10.4 F_GLYPH, F_SHARD, F_DEBRIS): glyphs (TextDisplay), tinted-glass
 * shards and debris of the ground's own block state (BlockDisplay). They are created with
 * {@code EntityType.create(clientLevel, LOAD)} under negative ids counting down from −3,000,000, added to the client level
 * only, never sent anywhere, and removed when they end or on a world change. Caps: 2 / 8 / 24 per scene by tier, inside
 * the 22.15.2 alive caps; at most 4 created per tick; {@code setViewRange} and width/height set so culling mods keep them.
 * LOW_LOAD scenes create none. A failure turns 7b displays off for the session (particles stand in).
 */
public final class EpicDisplays {

	public static final int FIRST_ID = -3_000_000;
	public static final int PER_TICK = 4;

	private record Live(Display display, int start, int end, @Nullable Vec3 velocity, double gravity) {
	}

	private static final List<Live> ALIVE = new ArrayList<>();
	private static int nextId = FIRST_ID;
	private static int createdThisTick;
	private static int ticks;
	private static boolean broken;
	private static @Nullable ClientLevel owner;

	/** True while one more display may be created now. */
	public static boolean canCreate() {
		return !broken && createdThisTick < PER_TICK && ALIVE.size() < Math.min(EpicBudget.displayCap() * 2, EffectQuality.displaysAlive());
	}

	/** A glyph {@code size} blocks tall at {@code at} for {@code life} ticks (billboarded). */
	public static @Nullable Display.TextDisplay glyph(Vec3 at, String text, int rgb, float size, int life) {
		Display.TextDisplay display = create(EntityTypes.TEXT_DISPLAY, at, Math.max(1.0F, size / 4.0F));
		if (display == null) {
			return null;
		}
		display.setText(Component.literal(text).withColor(rgb & 0xFFFFFF));
		display.setBackgroundColor(0);
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setLineWidth(400);
		float scale = size / 0.25F;
		display.setTransformation(new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(scale, scale, scale), null));
		display.setWidth(size * 2.0F);
		display.setHeight(size * 1.5F);
		ALIVE.add(new Live(display, ticks, ticks + life, null, 0.0D));
		return display;
	}

	/** A small block display flying with {@code velocity} (blocks per tick) under gravity for {@code life} ticks. */
	public static @Nullable Display.BlockDisplay chunk(Vec3 at, BlockState state, float size, Vec3 velocity, double gravity, int life) {
		Display.BlockDisplay display = create(EntityTypes.BLOCK_DISPLAY, at, 1.0F);
		if (display == null) {
			return null;
		}
		display.setBlockState(state);
		display.setTransformation(new Transformation(new Vector3f(-size / 2.0F, -size / 2.0F, -size / 2.0F), new Quaternionf(),
				new Vector3f(size, size, size), null));
		display.setWidth(size + 0.5F);
		display.setHeight(size + 0.5F);
		ALIVE.add(new Live(display, ticks, ticks + life, velocity, gravity));
		return display;
	}

	/** Up to {@code n} tinted-glass shards bursting from {@code at} (the mirror, the demon's shatter). */
	public static int shards(Vec3 at, int n, float speed) {
		int made = 0;
		for (int i = 0; i < n && canCreate(); i++) {
			double angle = Math.PI * 2.0D * i / Math.max(1, n);
			Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.12D + 0.1D * (i % 3), Math.sin(angle) * speed);
			if (chunk(at, Blocks.TINTED_GLASS.defaultBlockState(), 0.35F + 0.1F * (i % 3), velocity, 0.03D, 24) != null) {
				made++;
			}
		}
		return made;
	}

	/** Once per client tick: moves the flying chunks and removes the finished displays. */
	public static void tick() {
		ticks++;
		createdThisTick = 0;
		ClientLevel level = Minecraft.getInstance().level;
		if (level != owner) {
			ALIVE.clear();
			owner = level;
			return;
		}
		if (ALIVE.isEmpty()) {
			return;
		}
		for (Iterator<Live> it = ALIVE.iterator(); it.hasNext(); ) {
			Live live = it.next();
			Display display = live.display();
			if (display.isRemoved()) {
				it.remove();
				continue;
			}
			if (ticks >= live.end()) {
				remove(display);
				it.remove();
				continue;
			}
			if (live.velocity() != null) {
				int elapsed = ticks - live.start();
				Vec3 velocity = live.velocity().add(0.0D, -live.gravity() * elapsed, 0.0D);
				display.setPos(display.getX() + velocity.x, display.getY() + velocity.y, display.getZ() + velocity.z);
			}
		}
	}

	/** Removes every 7b display (disconnect, world change, stop). */
	public static void clear() {
		for (Live live : List.copyOf(ALIVE)) {
			remove(live.display());
		}
		ALIVE.clear();
	}

	private static <T extends Display> @Nullable T create(EntityType<T> type, Vec3 at, float viewRange) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !canCreate()) {
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
			display.setViewRange(viewRange);
			display.setShadowRadius(0.0F);
			display.setTransformationInterpolationDelay(0);
			level.addEntity(display);
			createdThisTick++;
			return display;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: Momen Epik displays turned off after an error; particles stand in", exception);
			return null;
		}
	}

	private static void remove(Display display) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level == owner && !display.isRemoved()) {
			level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	private EpicDisplays() {
	}
}
