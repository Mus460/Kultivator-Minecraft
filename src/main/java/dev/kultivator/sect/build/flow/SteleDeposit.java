package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.hall.HallItems;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Depositing at the stele (DESIGN 21.13): <b>sneak + right-click with an accepted item</b> puts the held stack into the
 * Gudang of the hall whose stele it is (the owner's own, or a sect mate's on LAN). A plain right-click keeps opening the
 * hall, and sneaking with an empty hand keeps module A's "move hall", so a held diamond is never swallowed by accident:
 * only items of the deposit table are taken, and exact items only as far as the template still needs them.
 * Registered through {@code UseBlockCallback}; it runs on both sides so the client never places the held block.
 */
public final class SteleDeposit {

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isSecondaryUseActive()) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.isEmpty() || Deposits.valueOf(held.getItem()) == null) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!level.getBlockState(pos).is(HallItems.SECT_HALL_STELE)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
				use(server, serverLevel, pos.immutable());
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		UUID owner = ownerAt(player, level, pos);
		if (owner == null) {
			Msg.actionBar(player, Component.translatable("kultivator.sectbuild.deposit.not_your_stele").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Deposits.depositOne(player, owner, player.getInventory().getSelectedSlot());
	}

	/** The player's own active hall at {@code pos}, else a sect mate's, else null. */
	private static @Nullable UUID ownerAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		UUID self = player.getUUID();
		if (standsAt(self, level, pos)) {
			return self;
		}
		Sect sect = SectData.get(level.getServer()).sectOf(self);
		if (sect != null) {
			for (Sect.Member member : sect.members()) {
				if (!member.id().equals(self) && standsAt(member.id(), level, pos)) {
					return member.id();
				}
			}
		}
		return null;
	}

	private static boolean standsAt(UUID owner, ServerLevel level, BlockPos pos) {
		Halls.HallView hall = Halls.hallOf(owner);
		return hall != null && hall.active() && hall.pos().equals(pos) && hall.dim().equals(level.dimension());
	}

	private SteleDeposit() {
	}
}
