package dev.kultivator.dunia.peta;

import dev.kultivator.Kultivator;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.function.Consumer;

/**
 * The map's items (DESIGN 34.5.16): Peta Dunia (opens the world map; two players holding one who right-click each other
 * merge their known sites) and Gulungan Penunjuk (reveals the nearest site of the chosen kind through
 * {@link DuniaSites#locateFor}; sneak + use picks the kind; used up only when it found a site new to its reader). No
 * ItemStack is made here.
 */
public final class PetaItems {

	/**
	 * What a pointer scroll looks for: the structure tag folder and the published kind (a cave publishes the kind of the
	 * cave actually found, see {@link DuniaSites#locate}). New kinds go at the end: the scroll saves the index.
	 */
	public static final String[][] POINTER_KINDS = {
			{"kota", "kota/kota"}, {"reruntuhan", "reruntuhan/gua_kecil"}, {"penjara", "penjara/penjara"}, {"menara", "menara/menara"},
			{"bawah", "bawah/kuil"}, {"laut", "laut/pusaran"}, {"kota_abadi", "kota/kota_abadi"}};
	/** Kota Abadi floats only in Alam Abadi; the scroll does not search for it anywhere else. */
	private static final int KOTA_ABADI = 6;

	/** {@code kultivator:penunjuk}: the pointer scroll's chosen kind (index into {@link #POINTER_KINDS}). */
	public static final DataComponentType<Integer> POINTER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Kultivator.id("penunjuk"),
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.intRange(0, POINTER_KINDS.length - 1))
					.networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final Item PETA_DUNIA = ModItems.register("peta_dunia", MapItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item GULUNGAN_PENUNJUK = ModItems.register("gulungan_penunjuk", PointerItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	static void init() {
	}

	/** Peta Dunia. */
	public static final class MapItem extends Item {

		public MapItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer serverPlayer) {
				Peta.open(serverPlayer);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.peta_dunia.tooltip").withColor(HartaText.USE_COLOR));
			builder.accept(Component.translatable("item.kultivator.peta_dunia.tooltip.exchange").withStyle(ChatFormatting.GRAY));
			HartaText.sources("kultivator.peta.peta", builder);
		}
	}

	/** Gulungan Penunjuk. */
	public static final class PointerItem extends Item {

		public PointerItem(Item.Properties properties) {
			super(properties);
		}

		static int kind(ItemStack stack) {
			Integer value = stack.get(POINTER);
			return value == null ? 0 : Mth.clamp(value, 0, POINTER_KINDS.length - 1);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			int kind = kind(stack);
			if (player.isShiftKeyDown()) {
				int next = (kind + 1) % POINTER_KINDS.length;
				stack.set(POINTER, next);
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.peta.pointer.kind",
						Component.translatable("kultivator.peta.pointer.kind." + POINTER_KINDS[next][0])).withColor(0xE8E0C0));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (!Peta.on()) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.peta.off").withColor(0xFF8A7A));
				return InteractionResult.FAIL;
			}
			long wait = DuniaSites.locateCooldown(serverPlayer);
			if (wait > 0L) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.peta.pointer.wait", (wait + 19L) / 20L).withColor(0xFFD54A));
				return InteractionResult.FAIL;
			}
			if (kind == KOTA_ABADI && !level.dimension().equals(Abadi.IMMORTAL_LEVEL)) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.sambung.peta.kota_abadi.dimensi").withColor(0xFF8A7A));
				return InteractionResult.FAIL;
			}
			TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, Kultivator.id(POINTER_KINDS[kind][0]));
			DuniaSites.Site site = DuniaSites.locateFor(serverPlayer, tag, DuniaSites.MAX_LOCATE_CHUNKS, POINTER_KINDS[kind][1], 1);
			if (site == null) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.peta.pointer.none",
						Component.translatable("kultivator.peta.pointer.kind." + POINTER_KINDS[kind][0])).withColor(0xFF8A7A));
				return InteractionResult.FAIL;
			}
			double dx = site.pos().getX() + 0.5D - player.getX();
			double dz = site.pos().getZ() + 0.5D - player.getZ();
			int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			Component direction = Component.translatable("kultivator.peta.dir." + direction(dx, dz));
			boolean fresh = DuniaSites.discover(serverPlayer, site.id());
			if (!fresh) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.peta.pointer.known", distance, direction).withColor(0xE8E0C0));
				return InteractionResult.SUCCESS_SERVER;
			}
			Msg.chat(serverPlayer, Component.translatable("kultivator.peta.pointer.found",
					Component.translatable("kultivator.peta.pointer.kind." + POINTER_KINDS[kind][0]), distance, direction).withColor(0x9FE0C0));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.peta.pointer.kind",
					Component.translatable("kultivator.peta.pointer.kind." + POINTER_KINDS[kind(stack)][0])).withColor(0xE8D9A8));
			builder.accept(Component.translatable("item.kultivator.gulungan_penunjuk.tooltip").withColor(HartaText.USE_COLOR));
			HartaText.sources("kultivator.peta.penunjuk", builder);
		}
	}

	/** One of the 8 compass points (n, ne, e, se, s, sw, w, nw) of a direction x/z. */
	public static String direction(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int index = Math.floorMod((int) Math.round(angle / 45.0D), 8);
		return new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[index];
	}

	private PetaItems() {
	}
}
