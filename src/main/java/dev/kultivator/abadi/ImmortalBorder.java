package dev.kultivator.abadi;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The edges of Alam Abadi (DESIGN 26.3.4, 26.1): a soft circular border of {@code immortalRealmRadius} around 0, 0 that pushes
 * players back (velocity, then a teleport inward when they go far past it; no world-border change), the void catch (below
 * y −8 a survival player is put back on the last solid ground recorded every 10 ticks, else the arrival island, loses 10% of
 * max HP, never lethal, and gets Slow Falling 3 s; {@code out_of_world} would bypass every guard) and the protected arrival
 * zone (48 blocks around the arrival island, refused to Mode Kehancuran). Nothing here changes a block.
 */
final class ImmortalBorder {

	public static final double VOID_Y = -8.0D;
	public static final double VOID_CHECK_Y = 16.0D;
	public static final double VOID_DAMAGE = 0.10D;
	public static final int VOID_SLOW_FALL = 60;
	public static final double PUSH = 0.9D;
	public static final double TELEPORT_PAST = 24.0D;
	public static final double ARRIVAL_ZONE = 48.0D;

	static void init() {
		ProtectedZones.addZoneProvider((level, pos) -> {
			if (!Abadi.isImmortalLevel(level)) {
				return false;
			}
			BlockPos arrival = AlamAbadiData.get(level.getServer()).arrival();
			if (arrival == null) {
				return false;
			}
			double dx = pos.getX() + 0.5D - (arrival.getX() + 0.5D);
			double dz = pos.getZ() + 0.5D - (arrival.getZ() + 0.5D);
			return dx * dx + dz * dz <= ARRIVAL_ZONE * ARRIVAL_ZONE;
		});
	}

	static void tick(MinecraftServer server, long ticks) {
		ServerLevel level = Abadi.immortalLevel(server);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		boolean slow = ticks % 10L == 0L;
		double radius = KultivatorConfig.get().immortalRealmRadius;
		for (ServerPlayer player : java.util.List.copyOf(level.players())) {
			if (player.isSpectator()) {
				continue;
			}
			if (player.getY() < VOID_CHECK_Y && player.getY() < VOID_Y && !player.isCreative()) {
				catchVoid(player);
				continue;
			}
			if (!slow) {
				continue;
			}
			if (player.onGround() && player.getY() >= VOID_CHECK_Y - 16.0D) {
				recordGround(player);
			}
			double distance = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
			if (distance > radius && !player.isCreative()) {
				pushBack(player, distance, radius);
			}
		}
	}

	private static void recordGround(ServerPlayer player) {
		CompoundTag ground = Abadi.extra(player).getCompound("ground").orElse(null);
		if (ground == null) {
			ground = new CompoundTag();
			Abadi.extra(player).put("ground", ground);
		}
		if (Math.abs(ground.getDoubleOr("x", 1.0E9D) - player.getX()) < 0.5D && Math.abs(ground.getDoubleOr("z", 1.0E9D) - player.getZ()) < 0.5D) {
			return;
		}
		ground.putDouble("x", player.getX());
		ground.putDouble("y", player.getY());
		ground.putDouble("z", player.getZ());
	}

	private static void pushBack(ServerPlayer player, double distance, double radius) {
		double nx = -player.getX() / Math.max(1.0D, distance);
		double nz = -player.getZ() / Math.max(1.0D, distance);
		if (distance > radius + TELEPORT_PAST) {
			Vec3 ground = lastGround(player);
			double gd = Math.sqrt(ground.x * ground.x + ground.z * ground.z);
			if (gd <= radius) {
				player.teleportTo(ground.x, ground.y, ground.z);
			} else {
				Vec3 inside = new Vec3(-nx * (radius - 10.0D), player.getY(), -nz * (radius - 10.0D));
				player.teleportTo(inside.x, inside.y, inside.z);
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, VOID_SLOW_FALL, 0, false, false, true));
		} else {
			player.setDeltaMovement(nx * PUSH, Math.max(0.1D, player.getDeltaMovement().y), nz * PUSH);
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
		Vec3 toward = player.position().add(nx * 4.0D, 1.0D, nz * 4.0D);
		Fx.sendTo(player, player.position(), toward, FxType.ABADI, AbadiFxIds.BORDER, 0, 0.0F);
		Msg.actionBar(player, Component.translatable("kultivator.abadi.border").withStyle(ChatFormatting.AQUA));
	}

	/** The last recorded solid ground (inside the border), else the arrival island. */
	private static Vec3 lastGround(ServerPlayer player) {
		CompoundTag ground = Abadi.extra(player).getCompoundOrEmpty("ground");
		if (!ground.isEmpty()) {
			return new Vec3(ground.getDoubleOr("x", 0.0D), ground.getDoubleOr("y", 120.0D), ground.getDoubleOr("z", 0.0D));
		}
		BlockPos island = ArrivalIsland.arrival(player.level().getServer());
		return new Vec3(island.getX() + 0.5D, island.getY() + 0.1D, island.getZ() + 0.5D);
	}

	/** Below y −8: back on solid ground, −10% max HP (never lethal), Slow Falling 3 s. */
	static void catchVoid(ServerPlayer player) {
		Vec3 ground = lastGround(player);
		double radius = KultivatorConfig.get().immortalRealmRadius;
		if (ground.x * ground.x + ground.z * ground.z > radius * radius) {
			BlockPos island = ArrivalIsland.arrival(player.level().getServer());
			ground = new Vec3(island.getX() + 0.5D, island.getY() + 0.1D, island.getZ() + 0.5D);
		}
		player.teleportTo(ground.x, ground.y + 0.1D, ground.z);
		player.setDeltaMovement(Vec3.ZERO);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		player.resetFallDistance();
		if (!player.getAbilities().invulnerable) {
			float loss = (float) (player.getMaxHealth() * VOID_DAMAGE);
			player.setHealth(Math.max(1.0F, player.getHealth() - loss));
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, VOID_SLOW_FALL, 0, false, false, true));
		Fx.send(player.level(), player.position(), player.position(), FxType.ABADI, AbadiFxIds.VOID, 0, 0.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.abadi.void").withStyle(ChatFormatting.AQUA));
		Cultivators.dirty(player);
	}

	private ImmortalBorder() {
	}
}
