package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Mata Air Roh (DESIGN 38.C.5, anchor {@code gua/mata_air} by the jade-stone basin of a spring under a cherry tree, with
 * a little shrine): a meditation site ×1.3, and drinking from the basin (an empty-handed use on a block of the pool
 * within {@code r} of the anchor; {@code UseBlockCallback} in {@link Gua}) restores 20 % of the maximum Qi once per
 * world day. Nothing in the world changes: the water stays where it is.
 */
public final class MataAirRoh extends GuaRun {

	public static final double QI_SHARE = 0.20D;

	public MataAirRoh(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return 8.0D;
	}

	/** True when {@code pos} is a block of this spring's basin. */
	public boolean kolam(BlockPos pos) {
		int r = Math.max(2, Math.min(6, data().getIntOr("r", 3)));
		return pos.distSqr(anchor.blockPosition()) <= (r + 1.0D) * (r + 1.0D);
	}

	@Override
	protected void spawn() {
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
	}

	@Override
	protected void tick(long now) {
		if (now % 50L == 0L) {
			Vec3 c = centre();
			level.sendParticles(ParticleTypes.GLOW, c.x, c.y + 0.2D, c.z, 2, 1.2D, 0.1D, 1.2D, 0.0D);
		}
	}

	/** Drinks from the spring: 20 % of the maximum Qi once per world day. True when the click was the spring's. */
	public boolean minum(ServerPlayer player, BlockPos clicked) {
		if (!kolam(clicked)) {
			return false;
		}
		GuaData data = GuaData.get(level.getServer());
		long today = today();
		if (data.playerLong(player.getUUID(), "air") == today) {
			Msg.actionBar(player, Component.translatable("kultivator.gua.mata_air.sudah").withStyle(ChatFormatting.GRAY));
			return true;
		}
		CultivationData c = Cultivators.get(player);
		double max = Qi.max(c);
		double before = c.qi();
		c.setQi(Math.min(max, before + max * QI_SHARE));
		Cultivators.dirty(player);
		data.setPlayerLong(player.getUUID(), "air", today);
		Vec3 at = Vec3.atCenterOf(clicked);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.6D, at.z, 12, 0.3D, 0.1D, 0.3D, 0.05D);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0D, player.getZ(), 6, 0.3D, 0.4D, 0.3D, 0.0D);
		Msg.chat(player, Component.translatable("kultivator.gua.mata_air.minum", Msg.number(c.qi() - before)).withStyle(ChatFormatting.AQUA));
		return true;
	}
}
