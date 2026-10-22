package dev.kultivator.dunia.naga;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The items of module B (DESIGN 34.6 B): the Selendang Pengikat Naga (binds a weakened Jiao), Sisik Jiao and Mutiara Jiao
 * (what a slain Jiao leaves; both feed spirit beasts), and the Mutiara Naga Jinak (the tamed Jiao's pearl: summon and
 * dismiss). Loaded by {@code Naga.init()}, before {@code ModItems.init()} builds the creative tab. Every tooltip ends with
 * the feature's Sumber line (rule C5).
 */
public final class NagaItems {

	/** Selendang Pengikat Naga: crafted (3 Bulu Ekor Roh + 2 High stones + 1 Mutiara Es Dingin) or sold in the black market. */
	public static final Item SELENDANG = ModItems.register("selendang_pengikat_naga", SelendangItem::new,
			new Item.Properties().stacksTo(4).rarity(Rarity.EPIC));

	/** Sisik Jiao: three from every slain Jiao; +60 spirit XP to a spirit beast or a tamed Jiao. */
	public static final Item SISIK_JIAO = ModItems.register("sisik_jiao",
			properties -> new FeedItem(properties, "sisik", FeedItem.SISIK_XP), new Item.Properties().stacksTo(64).rarity(Rarity.RARE));

	/** Mutiara Jiao: one in five slain Jiao; +300 spirit XP. */
	public static final Item MUTIARA_JIAO = ModItems.register("mutiara_jiao",
			properties -> new FeedItem(properties, "mutiara", FeedItem.MUTIARA_XP), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

	/** Mutiara Naga Jinak: the bound pearl of a subdued Jiao. */
	public static final Item MUTIARA_NAGA_JINAK = ModItems.register("mutiara_naga_jinak", MutiaraNagaItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	/** Darah Jiao: the blood of a slain Jiao (half of them, config {@code darahChance}); drunk for Dao comprehension. */
	public static final Item DARAH_JIAO = ModItems.register("darah_jiao", DarahJiaoItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

	/** Loads the class so the items above are registered. */
	static void init() {
	}

	/** An item of another module by id, or null when that module is off or missing (offerings fall back then). */
	static Item byId(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
		return item == Items.AIR ? null : item;
	}

	/** Puts {@code stack} into the player's inventory, the rest at the player's feet. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		player.getInventory().add(stack);
		if (!stack.isEmpty() && player.level() instanceof ServerLevel level) {
			ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 0.5D, player.getZ(), stack.copy());
			drop.setNoPickUpDelay();
			level.addFreshEntity(drop);
			stack.setCount(0);
		}
	}

	/** The Sumber line of the Naga Liar items. */
	static void sumber(Consumer<Component> builder, boolean evolution) {
		builder.accept(Component.translatable(evolution ? "kultivator.naga.evolusi.source" : "kultivator.naga.liar.source")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** The Selendang: its own use is only a hint; binding happens on a weakened Jiao ({@link WillContest}). */
	public static final class SelendangItem extends Item {

		public SelendangItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.selendang_pengikat_naga.desc").withColor(0xFFD27A));
			builder.accept(Component.translatable("item.kultivator.selendang_pengikat_naga.use").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.kultivator.selendang_pengikat_naga.realm").withStyle(ChatFormatting.DARK_AQUA));
			sumber(builder, false);
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}
	}

	/** Sisik and Mutiara Jiao: given to one's own spirit beast or tamed Jiao (the feeding lives in {@link TamedJiao}). */
	public static final class FeedItem extends Item {

		public static final double SISIK_XP = 60.0D;
		public static final double MUTIARA_XP = 300.0D;
		private final String key;
		private final double xp;

		public FeedItem(Item.Properties properties, String key, double xp) {
			super(properties);
			this.key = key;
			this.xp = xp;
		}

		public double xp() {
			return xp;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.naga.item." + key + ".desc").withColor(0x9FE0FF));
			builder.accept(Component.translatable("kultivator.naga.item.feed", Integer.toString((int) xp)).withStyle(ChatFormatting.GRAY));
			sumber(builder, false);
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return xp >= MUTIARA_XP;
		}
	}

	private NagaItems() {
	}

	static String id(String path) {
		return Kultivator.MOD_ID + ":" + path;
	}
}
