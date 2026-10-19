package dev.kultivator.tempa;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tungku Api Bumi, the Earthfire Forge (DESIGN 25.5.2): a plain block with no block entity, no state property and no random
 * tick; using it opens the {@link ForgeMenu}, whose slots live in the menu like a crafting table's. Its heat is read from
 * the block below; its glow is client FX. Sumber: RMJ ch. 520, 928 (refining rooms and the Earthfire Hall).
 */
public class EarthfireForgeBlock extends Block {

	public EarthfireForgeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer serverPlayer) {
			open(serverPlayer, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** Opens the forge menu at {@code pos} (refused while forging is switched off or inside a secret realm). */
	static void open(ServerPlayer player, BlockPos pos) {
		if (!KultivatorConfig.get().artifactForging) {
			Msg.chat(player, Component.translatable("kultivator.tempa.forge.disabled").withStyle(ChatFormatting.RED));
			return;
		}
		if (dev.kultivator.alam.Alam.blocked(player)) {
			Msg.chat(player, dev.kultivator.alam.Alam.refusal());
			return;
		}
		BlockPos at = pos.immutable();
		player.openMenu(new ExtendedMenuProvider<BlockPos>() {
			@Override
			public BlockPos getScreenOpeningData(ServerPlayer who) {
				return at;
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("block.kultivator.earthfire_forge");
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player who) {
				return new ForgeMenu(containerId, inventory, at);
			}
		});
	}
}
