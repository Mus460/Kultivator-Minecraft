package dev.kultivator.client.fx.spec;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.Brightness;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only phantom Display entities (DESIGN 22.4.3): created with {@code EntityType.create(clientLevel, LOAD)} under
 * negative ids counting down from −2,000,000, added with {@code ClientLevel.addEntity} and removed with
 * {@code removeEntity(id, DISCARDED)}. The server never spawns a Display for a technique. Limits: 8 / 32 / 96 alive by
 * quality, at most 4 created per tick, only within 64 blocks of the viewer, never a glow outline, at most 2 large
 * translucent layers (lasers; the dome and sky-tear particle templates share the count) at once. Everything is dropped
 * on a world change or disconnect. A failure turns phantom displays off for the session (templates fall back to
 * particles).
 */
public final class PhantomDisplays {

	public static final int FIRST_ID = -2_000_000;
	/** Displays created per tick at Normal (Minimal 4, Maximal 12; polish S13). */
	public static final int CREATED_PER_TICK = 8;
	public static final double VIEW_DISTANCE = 64.0D;
	public static final int LARGE_LAYERS = 2;

	private static int nextId = FIRST_ID;
	private static int createdThisTick;
	private static int largeLayers;
	private static boolean broken;
	private static final List<Display> ALIVE = new ArrayList<>();
	private static @Nullable ClientLevel owner;

	/** True while displays may be created at all (setting, server switch, no earlier failure). */
	public static boolean enabled() {
		return !broken && EffectQuality.phantoms() && SpecFx.serverDisplays();
	}

	/** True when one more display may be created this tick near {@code at}. */
	public static boolean canCreate(Vec3 at) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!enabled() || minecraft.level == null || createdThisTick >= createdPerTick() || alive() >= EffectQuality.displaysAlive()) {
			return false;
		}
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		return camera.distanceToSqr(at) <= VIEW_DISTANCE * VIEW_DISTANCE;
	}

	/** Creates a display of {@code type} at {@code at}, or null (limits, failure). */
	public static <T extends Display> @Nullable T create(EntityType<T> type, Vec3 at, float viewRange) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !canCreate(at)) {
			return null;
		}
		try {
			if (owner != level) {
				clear();
				owner = level;
			}
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
			level.addEntity(display);
			ALIVE.add(display);
			createdThisTick++;
			return display;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: client phantom displays turned off after an error; techniques fall back to particles", exception);
			return null;
		}
	}

	/** Removes one display (safe to call twice). */
	public static void remove(@Nullable Display display) {
		if (display == null) {
			return;
		}
		ALIVE.remove(display);
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level == owner && !display.isRemoved()) {
			level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	public static int alive() {
		ALIVE.removeIf(Entity::isRemoved);
		return ALIVE.size();
	}

	/** Displays one tick may create at the quality in effect: 4 / 8 / 12. */
	public static int createdPerTick() {
		return switch (EffectQuality.level()) {
			case MINIMAL -> 4;
			case NORMAL -> CREATED_PER_TICK;
			case MAXIMAL -> 12;
		};
	}

	/** Displays still allowed alive right now. */
	public static int room() {
		return Math.max(0, EffectQuality.displaysAlive() - alive());
	}

	/** Takes one of the large translucent layer slots (≤ 2 at once); false when both are in use. */
	static boolean takeLarge() {
		if (largeLayers >= LARGE_LAYERS) {
			return false;
		}
		largeLayers++;
		return true;
	}

	static void releaseLarge() {
		largeLayers = Math.max(0, largeLayers - 1);
	}

	/** Once per client tick. */
	static void tick(@Nullable ClientLevel level) {
		createdThisTick = 0;
		if (level != owner && owner != null) {
			ALIVE.clear();
			largeLayers = 0;
			owner = level;
		}
	}

	/** Drops every display (disconnect, world change). */
	static void clear() {
		ClientLevel level = Minecraft.getInstance().level;
		for (Display display : List.copyOf(ALIVE)) {
			if (level != null && level == owner && !display.isRemoved()) {
				level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		ALIVE.clear();
		largeLayers = 0;
		owner = level;
	}

	private PhantomDisplays() {
	}
}
