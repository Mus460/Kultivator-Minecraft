package dev.kultivator.batin;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The items and the entity type of the inner world (DESIGN 26.6, 26.13), loaded by {@link Batin#init()}:
 * {@code immortal_essence_pearl} (Mutiara Esensi Abadi; using one puts it back into the inner world's counter),
 * {@code land_expansion_token} (+1 plot when used) and {@code kultivator:batin_meteor} (never saved, not summonable, no loot).
 */
public final class BatinItems {

	/** Mutiara Esensi Abadi: green, grape-like (RevI ch. 409). */
	public static final Item IMMORTAL_ESSENCE_PEARL = ModItems.register("immortal_essence_pearl", PearlItem::new,
			new Item.Properties().rarity(Rarity.RARE));

	/** Token Perluasan Tanah: one more plot in the inner world (an auction lot and the chapter-14 reward). */
	public static final Item LAND_EXPANSION_TOKEN = ModItems.register("land_expansion_token", TokenItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

	private static final ResourceKey<EntityType<?>> METEOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("batin_meteor"));

	/** A meteor of the Hujan Api Meteor calamity: never saved, cannot be summoned, no loot table, fire immune. */
	public static final EntityType<BatinMeteorEntity> METEOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, METEOR_KEY,
			EntityType.Builder.<BatinMeteorEntity>of(BatinMeteorEntity::new, MobCategory.MISC)
					.sized(1.4F, 1.4F)
					.fireImmune()
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.build(METEOR_KEY));

	/** Loads the class so the entries above are registered. */
	static void init() {
	}

	/** The pearl: right-click puts it back into the inner world's counter (at most 99). */
	public static class PearlItem extends Item {

		public PearlItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			int stored = InnerWorld.depositPearls(serverPlayer, stack.getCount());
			if (stored > 0) {
				stack.shrink(stored);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.FAIL;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.immortal_essence_pearl.lore").withStyle(ChatFormatting.GREEN));
			builder.accept(Component.translatable("item.kultivator.immortal_essence_pearl.use").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.ekonomi.nama.mutiara_esensi").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.immortal_essence_pearl.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The token: +1 plot in a born inner world (up to 16). */
	public static class TokenItem extends Item {

		public TokenItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			if (Batin.addPlot(serverPlayer)) {
				player.getItemInHand(hand).shrink(1);
				return InteractionResult.SUCCESS;
			}
			Msg.actionBar(serverPlayer, Component.translatable(Batin.has(serverPlayer) ? "kultivator.batin.expand.full" : "kultivator.batin.none")
					.withStyle(ChatFormatting.YELLOW));
			return InteractionResult.FAIL;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.land_expansion_token.lore").withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("item.kultivator.land_expansion_token.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private BatinItems() {
	}
}
