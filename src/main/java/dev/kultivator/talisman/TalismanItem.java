package dev.kultivator.talisman;

import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A typed talisman (DESIGN 19.3.6): anyone, even a Mortal, can use it with a 10-tick use (the Return Talisman is a 5 s
 * channel); it is consumed or one use is spent, and all talismans share a 1 s cooldown ({@code kultivator:talisman}).
 * The server decides everything in {@link Talismans#activate}.
 */
public class TalismanItem extends Item {

	/** Ticks of the use that activates a talisman. */
	public static final int USE_TICKS = 10;

	private final TalismanKind kind;

	public TalismanItem(Item.Properties properties, TalismanKind kind) {
		super(properties);
		this.kind = kind;
	}

	public TalismanKind kind() {
		return kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			Component why = Talismans.refusal(serverPlayer, kind);
			if (why != null) {
				Msg.actionBar(serverPlayer, why.copy().withColor(0xFF9A7A));
				return InteractionResult.FAIL;
			}
			if (kind == TalismanKind.RETURN) {
				ReturnTalisman.begin(serverPlayer);
			}
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return kind == TalismanKind.RETURN ? ReturnTalisman.CHANNEL_TICKS : USE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (kind == TalismanKind.RETURN && entity instanceof ServerPlayer player
				&& !ReturnTalisman.tick(player, ReturnTalisman.CHANNEL_TICKS - ticksRemaining)) {
			player.stopUsingItem();
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (kind == TalismanKind.RETURN && entity instanceof ServerPlayer player) {
			ReturnTalisman.released(player);
		}
		return false;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && Talismans.activate(player, stack, kind)) {
			return Talismans.spend(player, stack);
		}
		return stack;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return Talismans.data(stack).power() >= 7 || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		TalismanData data = Talismans.data(stack);
		builder.accept(Component.translatable("kultivator.talismans.effect." + kind.testId(), Talismans.effectArgs(kind, data.power()))
				.withColor(kind.color()));
		builder.accept(Component.translatable("kultivator.talismans.tooltip.power", data.power()).withStyle(ChatFormatting.GRAY));
		if (!data.inscriber().isEmpty()) {
			builder.accept(Component.translatable("kultivator.talismans.tooltip.inscriber", data.inscriber()).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (kind.uses() > 1) {
			builder.accept(Component.translatable("kultivator.talismans.tooltip.uses", data.uses(), kind.uses()).withStyle(ChatFormatting.GRAY));
		}
		builder.accept(Component.translatable(kind == TalismanKind.RETURN ? "kultivator.talismans.tooltip.channel"
				: "kultivator.talismans.tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
		builder.accept(Component.translatable("item.kultivator." + kind.itemId() + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
