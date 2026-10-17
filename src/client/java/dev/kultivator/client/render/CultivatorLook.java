package dev.kultivator.client.render;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared lookups for the cultivator render layers: which player a render state belongs to (by the entity id the avatar
 * renderer stores in it) and that player's synced look, cached so the six layers of one player share one lookup per
 * frame; the look's colour; the sword item of a synced item id. Render thread only.
 */
final class CultivatorLook {

	private static AvatarRenderState lastState;
	private static int lastId = Integer.MIN_VALUE;
	private static float lastAge = Float.NaN;
	private static Player lastPlayer;
	private static VisualState lastVisual = VisualState.NONE;

	/** Item id -> stack (EMPTY for unknown ids), so no stack is made per frame. */
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	/** The synced look of the player drawn with this state; NONE for other avatars (mannequins...) or unknown ids. */
	static VisualState visual(AvatarRenderState state) {
		resolve(state);
		return lastVisual;
	}

	/** The player drawn with this state, or null. */
	static Player player(AvatarRenderState state) {
		resolve(state);
		return lastPlayer;
	}

	private static void resolve(AvatarRenderState state) {
		if (state == lastState && state.id == lastId && Float.compare(state.ageInTicks, lastAge) == 0) {
			return;
		}
		lastState = state;
		lastId = state.id;
		lastAge = state.ageInTicks;
		lastPlayer = null;
		lastVisual = VisualState.NONE;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Entity entity = level.getEntity(state.id);
		if (entity instanceof Player player && !player.isRemoved()) {
			lastPlayer = player;
			lastVisual = ClientCultivation.visual(player);
		}
	}

	/** Forgets the cached player (world change), so no old entity is kept alive. */
	static void clear() {
		lastState = null;
		lastId = Integer.MIN_VALUE;
		lastAge = Float.NaN;
		lastPlayer = null;
		lastVisual = VisualState.NONE;
	}

	/** True when the look belongs to a cultivator of at least this realm. */
	static boolean atLeast(VisualState visual, Realm realm) {
		return visual.realm() >= realm.ordinal();
	}

	/** RGB of the look: the Dao's colour, or the realm's colour before a Dao is chosen. */
	static int color(VisualState visual) {
		Dao dao = visual.daoEnum();
		return dao != null ? dao.color() : visual.realmEnum().color();
	}

	/** The sword item of a synced id ("minecraft:iron_sword"), EMPTY when unknown or blank. */
	static ItemStack sword(String itemId) {
		if (itemId == null || itemId.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return STACKS.computeIfAbsent(itemId, CultivatorLook::createStack);
	}

	private static ItemStack createStack(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** True when the player holds that item in either hand (then it is not also drawn on the back). */
	static boolean holds(Player player, ItemStack sword) {
		Item item = sword.getItem();
		return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
	}

	/** A colour lightened towards white by {@code amount} (0..1). */
	static int lighten(int rgb, float amount) {
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	private CultivatorLook() {
	}
}
