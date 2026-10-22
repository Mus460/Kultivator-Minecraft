package dev.kultivator.client.duel.fx;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.DisplayKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only Display entities of the duel visuals (ring posts, hanging talismans, the companion sword, the 危 / 決
 * marks): created with {@code EntityType.create(clientLevel, LOAD)} under negative ids counting down from −9,000,000 (a
 * range no other module uses), added to the client level only, never sent anywhere, never saved in a chunk, removed when
 * they end or on a world change. At most 4 are created per tick and 40 live at once (fewer at Minimal). Every pose goes
 * through {@link DisplayKey}. A failure turns them off for the session (the visuals carry on without them).
 */
final class DuelDisplays {

	static final int FIRST_ID = -9_000_000;
	static final int PER_TICK = 4;

	private static final List<Display> LIVE = new ArrayList<>();
	private static int nextId = FIRST_ID;
	private static int createdThisTick;
	private static boolean broken;
	private static @Nullable ClientLevel owner;

	static void tick() {
		createdThisTick = 0;
		ClientLevel level = Minecraft.getInstance().level;
		if (level != owner) {
			LIVE.clear();
			owner = level;
		}
		LIVE.removeIf(Entity::isRemoved);
	}

	static int cap() {
		return EffectQuality.minimal() ? 12 : 40;
	}

	static boolean canCreate() {
		return !broken && createdThisTick < PER_TICK && LIVE.size() < cap();
	}

	/** An item display of {@code stack} at {@code at} (FIXED transform), full bright when {@code glow}. */
	static Display.@Nullable ItemDisplay item(Vec3 at, ItemStack stack, boolean glow, float viewRange) {
		Display.ItemDisplay display = create(EntityTypes.ITEM_DISPLAY, at, viewRange);
		if (display == null) {
			return null;
		}
		display.setItemStack(stack);
		display.setItemTransform(ItemDisplayContext.FIXED);
		if (glow) {
			display.setBrightnessOverride(Brightness.FULL_BRIGHT);
		}
		display.setWidth(2.0F);
		display.setHeight(2.5F);
		return display;
	}

	/** A billboarded glyph {@code size} blocks tall (the 危 / 決 marks). */
	static Display.@Nullable TextDisplay glyph(Vec3 at, String text, int rgb, float size) {
		Display.TextDisplay display = create(EntityTypes.TEXT_DISPLAY, at, 1.0F);
		if (display == null) {
			return null;
		}
		display.setText(Component.literal(text).withColor(rgb & 0xFFFFFF));
		display.setBackgroundColor(0);
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setLineWidth(200);
		display.setBrightnessOverride(Brightness.FULL_BRIGHT);
		float scale = size / 0.25F;
		DisplayKey.place(display, new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(scale, scale, scale));
		display.setWidth(size * 2.0F);
		display.setHeight(size * 1.5F);
		return display;
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
			if (nextId < FIRST_ID - 900_000) {
				nextId = FIRST_ID;
			}
			display.setPos(at.x, at.y, at.z);
			display.setViewRange(viewRange);
			display.setShadowRadius(0.0F);
			level.addEntity(display);
			createdThisTick++;
			LIVE.add(display);
			return display;
		} catch (RuntimeException exception) {
			broken = true;
			Kultivator.LOGGER.warn("Kultivator: duel displays turned off after an error", exception);
			return null;
		}
	}

	/** Removes one display (null-safe). */
	static void remove(@Nullable Display display) {
		if (display == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level == owner && !display.isRemoved()) {
			level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
		}
		LIVE.remove(display);
	}

	static void clear() {
		for (Display display : List.copyOf(LIVE)) {
			remove(display);
		}
		LIVE.clear();
	}

	private DuelDisplays() {
	}
}
