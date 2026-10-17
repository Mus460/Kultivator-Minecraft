package dev.kultivator.alchemy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A refined pill: right-click to swallow (consumed unless creative). The server decides and applies the effect
 * ({@link PillEffects#swallow}); the client only waits for its answer. The name is coloured by the pill's
 * {@link PillQuality}, Perfect pills shimmer, and the tooltip shows the effect for that quality and its toxin.
 */
public class PillItem extends Item {

	private static final int TOXIN_COLOR = 0x7FA650;
	private static final int NOTE_COLOR = 0xC9B7E6;
	private static final int LORE_COLOR = 0x8A8A9A;

	private final PillKind kind;

	public PillItem(Item.Properties properties, PillKind kind) {
		super(properties);
		this.kind = kind;
	}

	public PillKind kind() {
		return kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.CONSUME;
		}
		ItemStack stack = player.getItemInHand(hand);
		return PillEffects.swallow(serverPlayer, stack, kind) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	public Component getName(ItemStack stack) {
		return super.getName(stack).copy().withColor(AlchemyComponents.quality(stack).color());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return kind == PillKind.IMMORTAL || AlchemyComponents.quality(stack) == PillQuality.PERFECT || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		PillQuality quality = AlchemyComponents.quality(stack);
		for (Component line : PillEffects.describe(kind, quality)) {
			builder.accept(line.copy().withColor(kind.color()));
		}
		if (kind == PillKind.SOUL_NURTURING) {
			builder.accept(Component.translatable("kultivator.alchemy.tooltip.requires_nascent").withColor(NOTE_COLOR));
		} else if (kind == PillKind.FOUNDATION) {
			builder.accept(Component.translatable("kultivator.alchemy.tooltip.foundation_hint").withColor(NOTE_COLOR));
		}
		builder.accept(Component.translatable("kultivator.alchemy.tooltip.quality", quality.displayName(),
				PillEffects.multiplierText(quality.multiplier())).withStyle(ChatFormatting.GRAY));
		if (kind == PillKind.IMMORTAL) {
			builder.accept(Component.translatable("kultivator.alchemy.tooltip.no_toxin").withColor(TOXIN_COLOR));
		} else {
			builder.accept(Component.translatable("kultivator.alchemy.tooltip.toxin",
					PillEffects.decimal(kind.toxin(quality))).withColor(TOXIN_COLOR));
		}
		builder.accept(Component.translatable("kultivator.alchemy.lore." + kind.shortId()).withColor(LORE_COLOR)
				.withStyle(ChatFormatting.ITALIC));
		builder.accept(Component.translatable("kultivator.alchemy.tooltip.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
