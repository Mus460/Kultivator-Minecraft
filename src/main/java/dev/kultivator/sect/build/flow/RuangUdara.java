package dev.kultivator.sect.build.flow;

import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The warded air of a sky hall (DESIGN 37.4.10): its volume (the island column + 8, from 8 under the lowest block to 8
 * over the envelope, plus the stair foot) is reserved from the moment the design is confirmed.
 * <ul>
 * <li>Someone who is not the owner, a sect mate, an invited guest or a creative op may not place or break blocks there,
 * nor use buckets, flint and steel or fire charges into it ({@code kultivator.sekte_langit.udara.terlindung}). No block is
 * changed by this rule.</li>
 * <li>Mode Kehancuran never touches the volume (a protected zone of {@code ProtectedZones}).</li>
 * <li>Lightning that appears in it is visual only ({@code LightningBolt.setVisualOnly}): no fire on the wooden roofs, no
 * hurt. The sect stands "above the storm"; the mod itself never makes lightning.</li>
 * <li>With the gate closed (and the Gate of the Nine Heavens or the Grand Formation standing), strangers are pushed gently
 * outward (0.6 a tick) and given slow falling; never teleported away in the air. "A name in the register" (an invitation,
 * one world day) lets them in, after <i>Journey to the West</i> ch. 4.</li>
 * </ul>
 * Switch {@code sekteLangit.lindungiUdara}.
 */
public final class RuangUdara {

	/** The push speed of a closed gate (blocks a tick) and how often it runs. */
	public static final double PUSH = 0.6D;
	public static final int PUSH_EVERY = 5;
	/** One warning line per player at most this often. */
	private static final int WARN_TICKS = 40;

	private static final Map<UUID, Long> WARNED = new HashMap<>();
	private static long now;

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!placing(stack)) {
				return InteractionResult.PASS;
			}
			BlockPos at = hit.getBlockPos();
			if (warded(server, level, at) || warded(server, level, at.relative(hit.getDirection()))) {
				tell(server, level, at);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server) || !placing(player.getItemInHand(hand))
					|| player.getItemInHand(hand).getItem() instanceof BlockItem) {
				return InteractionResult.PASS;
			}
			Vec3 eye = player.getEyePosition();
			Vec3 reach = eye.add(player.getLookAngle().scale(5.0D));
			BlockPos target = BlockPos.containing(reach);
			if (warded(server, level, target) || warded(server, level, player.blockPosition())) {
				tell(server, level, target);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
				return true;
			}
			if (warded(server, level, pos)) {
				tell(server, level, pos);
				return false;
			}
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LightningBolt bolt && LangitConfig.get().lindungiUdara && LangitApi.get().at(level, bolt.blockPosition()) != null) {
				// Above the storm: a bolt in the warded air is only light and sound.
				bolt.setVisualOnly(true);
			}
		});
		ProtectedZones.addZoneProvider((level, pos) -> LangitApi.get().at(level, pos) != null);
	}

	/** Items that put a block, a fluid or fire into the world. */
	private static boolean placing(ItemStack stack) {
		return stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem || stack.getItem() instanceof SolidBucketItem
				|| stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
	}

	/** True when {@code pos} lies in a sky hall's warded air and {@code player} may not change it. */
	static boolean warded(ServerPlayer player, Level level, BlockPos pos) {
		if (!LangitConfig.get().lindungiUdara) {
			return false;
		}
		SkyHallView view = LangitApi.get().at(level, pos);
		return view != null && !allowed(view.owner(), player);
	}

	/** The owner, a sect mate, an invited guest, or a creative operator. */
	static boolean allowed(UUID owner, ServerPlayer player) {
		if (LangitApiImpl.isMember(owner, player) || LangitApi.get().invited(owner, player.getUUID())) {
			return true;
		}
		return player.isCreative() && player.canUseGameMasterBlocks();
	}

	private static void tell(ServerPlayer player, Level level, BlockPos pos) {
		Long last = WARNED.get(player.getUUID());
		if (last != null && now - last < WARN_TICKS) {
			return;
		}
		WARNED.put(player.getUUID(), now);
		SkyHallView view = LangitApi.get().at(level, pos);
		Component name = view == null ? Component.literal("?") : hallName(view.owner());
		Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.udara.terlindung", name).withStyle(ChatFormatting.AQUA));
	}

	private static Component hallName(UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		return hall == null || hall.name() == null || hall.name().isBlank() ? Component.translatable("kultivator.sekte_langit.udara.tanpa_nama")
				: Component.literal(hall.name());
	}

	/** The closed gate: strangers inside the volume drift out (server tick). */
	static void tick(MinecraftServer server, long ticks) {
		now = ticks;
		if (ticks % PUSH_EVERY != 0 || !LangitConfig.on() || !LangitConfig.get().lindungiUdara) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			for (SkyHallView view : LangitApi.get().all(level)) {
				if (!view.gateClosed() || !gateStands(view)) {
					continue;
				}
				for (ServerPlayer player : level.players()) {
					if (player.isSpectator() || !view.inVolume(player.position()) || allowed(view.owner(), player)) {
						continue;
					}
					push(player, view);
				}
			}
		}
	}

	/** The gate holds when the Gate of the Nine Heavens stands or the Grand Formation is active. */
	private static boolean gateStands(SkyHallView view) {
		return view.grandFormation() || LangitApi.get().anchor(view.owner(), "gerbang_langit") != null;
	}

	private static void push(ServerPlayer player, SkyHallView view) {
		double dx = player.getX() - (view.origin().getX() + 0.5D);
		double dz = player.getZ() - (view.origin().getZ() + 0.5D);
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-3D) {
			dx = 1.0D;
			length = 1.0D;
		}
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(dx / length * PUSH, Math.max(motion.y, -0.1D), dz / length * PUSH);
		player.needsSync = true;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
		tell(player, player.level(), player.blockPosition());
	}

	static void clear() {
		WARNED.clear();
	}

	private RuangUdara() {
	}
}
