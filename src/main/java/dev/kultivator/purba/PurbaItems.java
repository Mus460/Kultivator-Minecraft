package dev.kultivator.purba;

import dev.kultivator.Kultivator;
import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The items of P9-purba (DESIGN 26.4.3, 26.4.6, 26.5.2, 26.13): Gulungan Gunung dan Laut (the summon scroll, component
 * {@code kultivator:purba_kind}), the four primordial materials ({@code #kultivator:primordial_material}), Mata Lilin Zhulong
 * and the heaven seal shard. Each tooltip shows its use and its Sumber line. Other modules find them by registry id and by
 * the tag. Loaded by {@code Purba.init()} before {@code ModItems.init()}.
 */
public final class PurbaItems {

	/** {@code kultivator:purba_kind}: which primordial a scroll calls (0 Kun-Peng, 1 Zhulong, 2 Taotie). */
	public static final DataComponentType<Integer> PURBA_KIND = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("purba_kind"), DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.intRange(0, 2))
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** {@code #kultivator:primordial_material}: kun_scale, peng_feather, zhulong_scale, taotie_fang. */
	public static final TagKey<Item> PRIMORDIAL_MATERIAL = TagKey.create(Registries.ITEM, Kultivator.id("primordial_material"));

	/** Gulungan Gunung dan Laut: calls the primordial of its kind in Alam Abadi (consumed when the beast arrives). */
	public static final Item SHAN_HAI_SCROLL = ModItems.register("shan_hai_scroll", ScrollItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).component(PURBA_KIND, 0));
	public static final Item KUN_SCALE = material("kun_scale");
	public static final Item PENG_FEATHER = material("peng_feather");
	public static final Item ZHULONG_SCALE = material("zhulong_scale");
	public static final Item TAOTIE_FANG = material("taotie_fang");
	/** Mata Lilin Zhulong: in the off hand, Night Vision and no Darkness. */
	public static final Item ZHULONG_CANDLE_EYE = ModItems.register("zhulong_candle_eye", CandleEyeItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	/** Serpihan Segel Langit: three soften every strike of one star calamity run (Segel Penangkal). */
	public static final Item HEAVEN_SEAL_SHARD = ModItems.register("heaven_seal_shard", properties -> new LoreItem(properties, false),
			new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

	private static Item material(String id) {
		return ModItems.register(id, properties -> new LoreItem(properties, true), new Item.Properties().stacksTo(64).rarity(Rarity.EPIC));
	}

	/** Loads the class so the items above are registered. */
	static void init() {
	}

	/** A scroll of primordial {@code kind} (0-2). */
	public static ItemStack scroll(int kind, int count) {
		ItemStack stack = new ItemStack(SHAN_HAI_SCROLL, Math.max(1, count));
		stack.set(PURBA_KIND, Mth.clamp(kind, 0, 2));
		return stack;
	}

	/** The kind of a scroll stack (0-2). */
	public static int kindOf(ItemStack stack) {
		return Mth.clamp(stack.getOrDefault(PURBA_KIND, 0), 0, 2);
	}

	/** The material(s) of a kind: Kun-Peng scale or feather, Zhulong scale, Taotie fang. */
	public static Item material(int kind, boolean second) {
		return switch (Mth.clamp(kind, 0, 2)) {
			case 0 -> second ? PENG_FEATHER : KUN_SCALE;
			case 1 -> ZHULONG_SCALE;
			default -> TAOTIE_FANG;
		};
	}

	/** The beast name of a kind ({@code kultivator.purba.kind.<id>}). */
	public static Component kindName(int kind) {
		return Component.translatable("kultivator.purba.kind." + Purba.KINDS.get(Mth.clamp(kind, 0, 2)));
	}

	// ================================================================== items

	/** A plain item with its use line and Sumber line. */
	public static class LoreItem extends Item {

		private final boolean material;

		public LoreItem(Item.Properties properties, boolean material) {
			super(properties);
			this.material = material;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable(getDescriptionId() + ".tooltip").withColor(HartaText.USE_COLOR));
			if (material) {
				builder.accept(Component.translatable("kultivator.purba.item.material_use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			HartaText.sources(getDescriptionId(), builder);
		}
	}

	/** The summon scroll: used in Alam Abadi it starts a Gejolak Purba of its kind. */
	public static class ScrollItem extends Item {

		public ScrollItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			return PrimordialEvents.useScroll(serverPlayer, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		@Override
		public Component getName(ItemStack stack) {
			return Component.translatable("item.kultivator.shan_hai_scroll.named", kindName(kindOf(stack)));
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.shan_hai_scroll.tooltip", kindName(kindOf(stack))).withColor(HartaText.USE_COLOR));
			builder.accept(Component.translatable("item.kultivator.shan_hai_scroll.rule").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			HartaText.sources("item.kultivator.shan_hai_scroll", builder);
			HartaText.sources("kultivator.purba.kind." + Purba.KINDS.get(kindOf(stack)), builder);
		}
	}

	/** Mata Lilin Zhulong: held in the off hand it keeps Night Vision up and lifts Darkness (DESIGN 26.4.6). */
	public static class CandleEyeItem extends LoreItem {

		public CandleEyeItem(Item.Properties properties) {
			super(properties, false);
		}

		@Override
		public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
			if (slot != EquipmentSlot.OFFHAND || !(owner instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
				return;
			}
			if (player.hasEffect(MobEffects.DARKNESS)) {
				player.removeEffect(MobEffects.DARKNESS);
			}
			MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
			if (player.tickCount % 200 == 0 || vision == null || vision.getDuration() < 260) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
			}
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}
	}

	private PurbaItems() {
	}
}
