package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.TempatConfig;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Road shrines of the Earth God (DESIGN 38.C.6.3, {@code pos/kuil_jalan}): using the incense urn, the altar candles or
 * the offering bowl of a shrine burns a stick of incense for {@link #DUPA} NB and grants <b>Restu Jalan</b> (Speed I for
 * three minutes), at most once per ten minutes per player. Only the effect and a puff of smoke; no block changes.
 * Sumber ({@code kultivator.jalan.kuil.inspired}): the small Tudi Gong shrines found along Chinese country roads.
 */
public final class KuilJalan {

	public static final long DUPA = 1L;
	public static final int RESTU_TICKS = 3 * 60 * 20;
	public static final long JEDA = 10 * 60 * 20;

	/** UseBlockCallback: a click on a shrine's urn, candle or bowl. */
	static InteractionResult pakai(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
				|| !TempatConfig.get().aktif) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = server.getBlockState(pos);
		if (!state.is(Blocks.CAULDRON) && !state.is(Blocks.FLOWER_POT) && !state.is(net.minecraft.tags.BlockTags.CANDLES)) {
			return InteractionResult.PASS;
		}
		DuniaAnchorEntity shrine = DuniaAnchors.nearest(server, Vec3.atCenterOf(pos), "pos/kuil_jalan", 6.0D);
		if (shrine == null) {
			return InteractionResult.PASS;
		}
		CultivationData data = Cultivators.get(sp);
		CompoundTag tag = data.extra().getCompoundOrEmpty(Tonggak.TAG).copy();
		long now = server.getGameTime();
		long last = tag.getLongOr("restu", Long.MIN_VALUE / 2);
		if (now - last < JEDA) {
			long left = (JEDA - (now - last)) / 20L;
			Msg.actionBar(sp, Component.translatable("kultivator.jalan.kuil.tunggu", left / 60, left % 60).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!sp.isCreative() && !NilaiBatu.pay(sp, DUPA)) {
			Msg.actionBar(sp, Component.translatable("kultivator.jalan.kuil.miskin", DUPA).withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS_SERVER;
		}
		tag.putLong("restu", now);
		data.extra().put(Tonggak.TAG, tag);
		Cultivators.dirty(sp);
		sp.addEffect(new MobEffectInstance(MobEffects.SPEED, RESTU_TICKS, 0, true, true));
		server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 3, 0.1D, 0.2D, 0.1D, 0.01D);
		server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.4F);
		Msg.chat(sp, Component.translatable("kultivator.jalan.kuil.restu").withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS_SERVER;
	}

	private KuilJalan() {
	}
}
