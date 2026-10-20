package dev.kultivator.pusakalangit;

import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
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

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Segel Titah Langit ({@code kultivator:heaven_decree_seal}, DESIGN 29.2): a square white-jade seal with a gold cord, a
 * coiled-dragon knob and the glyph 敕. It binds to its first user ({@code kultivator:titah_owner}); others are refused.
 * Right-click casts the selected decree at the crosshair ({@link Titah#cast}); sneak + right-click opens the Titah
 * Langit menu. Cooldowns, settings and monument caps live on the player, so a second seal gives nothing extra.
 */
public class HeavenDecreeSealItem extends Item {

	private static final String KEY = "kultivator.pusaka_langit.";

	public HeavenDecreeSealItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		UUID owner = stack.get(TitahItems.OWNER);
		if (owner == null) {
			TitahItems.bind(stack, serverPlayer);
			Msg.actionBar(serverPlayer, Component.translatable(KEY + "seal.bound").withColor(0xFFF4D0));
		} else if (!owner.equals(serverPlayer.getUUID())) {
			Msg.actionBar(serverPlayer, Component.translatable(KEY + "seal.not_yours").withColor(0xFF8A7A));
			return InteractionResult.FAIL;
		}
		if (serverPlayer.isShiftKeyDown()) {
			openMenu(serverPlayer);
			return InteractionResult.SUCCESS_SERVER;
		}
		Titah.Skill skill = TitahSettings.selected(dev.kultivator.data.Cultivators.get(serverPlayer));
		return Titah.cast(serverPlayer, skill, false) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	/** The Titah Langit menu: its data first, then the screen (29.2.2). */
	static void openMenu(ServerPlayer player) {
		TitahSettings.sync(player);
		Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.TITAH, 0));
	}

	/** True when the player carries a seal that is theirs or not yet bound (for {@code /kultivasi titah}). */
	static boolean carries(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(TitahItems.SEAL)) {
				UUID owner = stack.get(TitahItems.OWNER);
				if (owner == null || owner.equals(player.getUUID())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Writes the selected decree into the player's own seals (the tooltip); only where it changed. */
	static void refreshView(ServerPlayer player) {
		Titah.Skill skill = TitahSettings.selected(dev.kultivator.data.Cultivators.get(player));
		int selected = skill.ordinal();
		// Phase 10b Tingkat (29.16.3): the selected decree's Tingkat and whether it would be permanent
		Titah.Settings settings = TitahSettings.read(player, skill);
		int tierView = Math.max(1, Math.min(TitahSettings.T_MAX, settings.tingkat()))
				| (TitahSettings.permanent(settings, dev.kultivator.kehancuran.Kehancuran.selfOn(player)) ? 16 : 0);
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.is(TitahItems.SEAL)) {
				continue;
			}
			UUID owner = stack.get(TitahItems.OWNER);
			if (owner != null && !owner.equals(player.getUUID())) {
				continue;
			}
			Integer view = stack.get(TitahItems.VIEW);
			if (view == null || view != selected) {
				stack.set(TitahItems.VIEW, selected);
			}
			Integer tiers = stack.get(TitahItems.TIER_VIEW);
			if (tiers == null || tiers != tierView) {
				stack.set(TitahItems.TIER_VIEW, tierView);
			}
		}
	}

	/** Fabric's FabricItem hook (when injected): a changed tooltip component never replays the equip animation. */
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.kultivator.heaven_decree_seal.lore").withStyle(ChatFormatting.GOLD));
		String name = stack.get(TitahItems.OWNER_NAME);
		if (stack.get(TitahItems.OWNER) == null) {
			builder.accept(Component.translatable(KEY + "seal.unbound").withStyle(ChatFormatting.GRAY));
		} else {
			builder.accept(Component.translatable(KEY + "seal.owner", name == null ? "?" : name).withStyle(ChatFormatting.GRAY));
		}
		Integer view = stack.get(TitahItems.VIEW);
		Titah.Skill skill = Titah.Skill.byOrdinal(view == null ? 0 : view);
		builder.accept(Component.translatable(KEY + "seal.selected", Component.translatable("kultivator.technique." + skill.specId())
				.withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
		Integer tiers = stack.get(TitahItems.TIER_VIEW);
		int tier = tiers == null ? 1 : Math.max(1, Math.min(9, tiers & 15));
		builder.accept(Component.translatable(KEY + "tier.seal", tier).withStyle(ChatFormatting.GRAY));
		if (tiers != null && (tiers & 16) != 0) {
			builder.accept(Component.translatable(KEY + "tier.seal_permanent").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		builder.accept(Component.translatable(KEY + "seal.cooldown_hint").withStyle(ChatFormatting.DARK_GRAY));
		builder.accept(Component.translatable(KEY + "seal.use").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable(KEY + "seal.menu").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("kultivator.technique." + skill.specId() + ".source").withStyle(ChatFormatting.DARK_GRAY,
				ChatFormatting.ITALIC));
		builder.accept(Component.translatable("item.kultivator.heaven_decree_seal.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
