package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Mutiara Naga Jinak (DESIGN 34.5.11): the pearl a subdued Jiao lives in while it is not out. Use: summon it next to
 * you, use again: call it back. The Jiao itself is kept with its owner ({@link TamedJiao}); the pearl carries a copy of
 * its kind, tier and spirit XP for the tooltip and is bound to its owner (anyone else's use is refused).
 */
public class MutiaraNagaItem extends Item {

	static final String OWNER = "naga_owner";
	static final String OWNER_NAME = "naga_owner_name";
	static final String KIND = "naga_kind";
	static final String TIER = "naga_tier";
	static final String XP = "naga_xp";

	public MutiaraNagaItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		boolean done = TamedJiao.usePearl(serverPlayer, stack);
		if (done) {
			player.getCooldowns().addCooldown(stack, 30);
		}
		return done ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Writes the pearl's copy of the Jiao's record. */
	static void stamp(ItemStack stack, ServerPlayer owner, TamedJiao.Record record) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putString(OWNER, owner.getUUID().toString());
			tag.putString(OWNER_NAME, owner.getName().getString());
			tag.putInt(KIND, record.kind().ordinal());
			tag.putInt(TIER, record.tier());
			tag.putDouble(XP, record.xp());
		});
	}

	/** The UUID string the pearl is bound to ("" for an unbound pearl from the creative tab). */
	static String ownerOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(OWNER, "");
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		if (tag.getStringOr(OWNER, "").isEmpty()) {
			builder.accept(Component.translatable("item.kultivator.mutiara_naga_jinak.empty").withStyle(ChatFormatting.GRAY));
		} else {
			NagaKind kind = NagaKind.byOrdinal(tag.getIntOr(KIND, 0));
			int tier = Math.max(BeastData.MAX_TIER, Math.min(BeastData.TOP_TIER, tag.getIntOr(TIER, BeastData.MAX_TIER)));
			builder.accept(Component.translatable(BeastData.titleKey(BeastData.JIAO, tier)).withColor(kind.glow)
					.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
					.append(kind.displayName().copy().withColor(kind.color)));
			builder.accept(Component.translatable("item.kultivator.mutiara_naga_jinak.tier", tier,
					dev.kultivator.util.Msg.number(tag.getDoubleOr(XP, 0.0D))).withStyle(ChatFormatting.AQUA));
			builder.accept(Component.translatable("item.kultivator.mutiara_naga_jinak.owner", tag.getStringOr(OWNER_NAME, "?"))
					.withStyle(ChatFormatting.DARK_AQUA));
		}
		builder.accept(Component.translatable("item.kultivator.mutiara_naga_jinak.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		NagaItems.sumber(builder, false);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return !ownerOf(stack).isEmpty();
	}
}
