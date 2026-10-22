package dev.kultivator.alam;

import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * The items of module A (DESIGN 25.3.9): the four realm keys, the heavenvoid map fragment, the Cold Ice Pearl and the
 * realm materials. Each tooltip shows its use and its Sumber line ({@code item.kultivator.<id>.source} /
 * {@code .inspired}). Loaded by {@code Alam.init()} before {@code ModItems.init()}. Module C finds these by registry id
 * and through the {@code #kultivator:realm_material/*} and {@code #kultivator:spirit_seed} tags.
 */
public final class AlamItems {

	/** Key to realm 1 (consumed on success, kept on an expel). */
	public static final Item TOKEN_DARAH_API = register("token_darah_api", RealmSpecs.BLOOD_FIRE, 16, Rarity.UNCOMMON, false);
	/** Three make a {@link #HEAVENVOID_MAP}. */
	public static final Item HEAVENVOID_FRAGMENT = register("heavenvoid_fragment", null, 64, Rarity.UNCOMMON, false);
	/** Key to realm 2. */
	public static final Item HEAVENVOID_MAP = register("heavenvoid_map", RealmSpecs.HEAVENVOID, 16, Rarity.RARE, false);
	/** In the off hand: Jalan Lava ×0.5 and entity fire duration ×0.5. */
	public static final Item COLD_ICE_PEARL = register("cold_ice_pearl", null, 1, Rarity.RARE, false);
	/** Realm 2 material: weapon-spirit seed, ring tier 4, Soul Nurturing alternative recipe ({@code #kultivator:spirit_seed}). */
	public static final Item SOUL_NURTURING_WOOD = register("soul_nurturing_wood", null, 64, Rarity.RARE, false);
	/** Key to realm 3 (consumed on success, kept on an expel). */
	public static final Item INHERITANCE_CRYSTAL = register("inheritance_crystal", RealmSpecs.ANCIENT_GOD, 16, Rarity.EPIC, false);
	/** Realm 3 material: Abadi forging, refinement +7…+9. */
	public static final Item ANCIENT_GOD_BLOOD = register("ancient_god_blood", null, 64, Rarity.EPIC, false);
	/** Key to realm 4 (one per descent). */
	public static final Item TOMB_SIGIL = register("tomb_sigil", RealmSpecs.HEAVENLY_TOMB, 16, Rarity.EPIC, true);
	/** Realm 4 material: Dewa forging, refinement +10…+12, star condensing discount. */
	public static final Item TOMB_ESSENCE = register("tomb_essence", null, 64, Rarity.EPIC, false);
	/** Realm 2 first clear: the unique Heavenvoid Cauldron (opened by module C, 25.5.9). */
	public static final Item HEAVENVOID_CAULDRON = register("heavenvoid_cauldron", null, 1, Rarity.EPIC, true);

	private static Item register(String id, @Nullable String realm, int stack, Rarity rarity, boolean foil) {
		return ModItems.register(id, properties -> new AlamItem(properties, realm, foil), new Item.Properties().stacksTo(stack).rarity(rarity));
	}

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	/** The key item of a realm (by its id), or null. */
	public static @Nullable Item keyOf(String realm) {
		return switch (realm) {
			case RealmSpecs.BLOOD_FIRE -> TOKEN_DARAH_API;
			case RealmSpecs.HEAVENVOID -> HEAVENVOID_MAP;
			case RealmSpecs.ANCIENT_GOD -> INHERITANCE_CRYSTAL;
			case RealmSpecs.HEAVENLY_TOMB -> TOMB_SIGIL;
			default -> null;
		};
	}

	/** One item of the module: a key ({@code realm} set) opens its realm's gate when used. */
	public static class AlamItem extends Item {

		private final @Nullable String realm;
		private final boolean foil;

		public AlamItem(Item.Properties properties, @Nullable String realm, boolean foil) {
			super(properties);
			this.realm = realm;
			this.foil = foil;
		}

		/** The realm this key opens, or null. */
		public @Nullable String realm() {
			return realm;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (realm == null) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			return Alam.enter(serverPlayer, realm, false) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return foil || super.isFoil(stack);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable(getDescriptionId() + ".tooltip").withColor(HartaText.USE_COLOR));
			if (this == TOMB_ESSENCE) {
				// audit R21: not Immortal Essence and not the Immortal Essence Pearl
				builder.accept(Component.translatable("kultivator.ekonomi.nama.esensi_makam").withStyle(ChatFormatting.GRAY));
			}
			HartaText.sources(getDescriptionId(), builder);
			if (realm != null) {
				builder.accept(Component.translatable("kultivator.alam.item.key_use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	private AlamItems() {
	}
}
