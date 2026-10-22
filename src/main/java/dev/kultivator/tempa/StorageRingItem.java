package dev.kultivator.tempa;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The storage pouch and the four storage rings, {@code kultivator:ring} plus {@code minecraft:container} (DESIGN 25.7,
 * 25.15, owned by P8-tempa). Stacks of one with a unique {@code ring_id}; sneak-use, the "Buka Cincin" key or
 * {@code /kultivasi cincin} opens the {@link RingMenu}. The first opening of a ring (tier 1+) by a cultivator of its realm
 * costs 10 qi and plants their spiritual imprint (Tanam Jejak Jiwa); anyone else is refused. Sumber: RMJ ch. 190, 273, 402
 * (storage pouches); BTTH ch. 267, 1353 (storage rings), ch. 867 (the spiritual imprint). Every number is game design.
 */
public class StorageRingItem extends Item {

	/** Slots per tier: pouch 9, rings 18 / 27 / 36 / 54. */
	public static final int[] SLOTS = {9, 18, 27, 36, 54};
	public static final int SPACE_BONUS = 9;
	public static final int MAX_SLOTS = 54;
	public static final double BOND_QI = 10.0D;
	/** The realm a cultivator needs to imprint a ring of each tier (the pouch takes no imprint). */
	public static final Realm[] BOND_REALM = {Realm.MORTAL, Realm.QI_REFINING, Realm.FOUNDATION, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL};

	private final int tier;

	public StorageRingItem(Properties properties) {
		this(properties, 0);
	}

	public StorageRingItem(Properties properties, int tier) {
		super(properties);
		this.tier = Math.max(0, Math.min(4, tier));
	}

	public int tier() {
		return tier;
	}

	// ================================================================== record

	public static boolean is(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof StorageRingItem;
	}

	public static int tierOf(ItemStack stack) {
		return is(stack) ? ((StorageRingItem) stack.getItem()).tier : -1;
	}

	static CompoundTag record(ItemStack stack) {
		CompoundTag tag = stack.get(TempaItems.RING);
		return tag == null ? new CompoundTag() : tag.copy();
	}

	/** The owner whose spiritual imprint binds this ring, or null (unbound, a pouch, or not a ring). */
	public static @Nullable UUID bondOwner(ItemStack stack) {
		if (!is(stack) || tierOf(stack) == 0) {
			return null;
		}
		CompoundTag tag = stack.get(TempaItems.RING);
		return tag == null ? null : ArtifactItem.parse(tag.getStringOr("owner", ""));
	}

	/** True when the pouch or ring holds no item (no container component, or only empty slots). Read-only. */
	public static boolean isEmpty(ItemStack stack) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		return contents == null || contents.nonEmptyItemCopyStream().findAny().isEmpty();
	}

	/** True for a pouch or ring that must not be sold or consigned: it still holds items or is bound to an owner. */
	public static boolean unsafeToSell(ItemStack stack) {
		return is(stack) && (!isEmpty(stack) || bondOwner(stack) != null);
	}

	static String ownerName(ItemStack stack) {
		CompoundTag tag = stack.get(TempaItems.RING);
		return tag == null ? "" : tag.getStringOr("owner_name", "");
	}

	public static @Nullable UUID ringId(ItemStack stack) {
		CompoundTag tag = is(stack) ? stack.get(TempaItems.RING) : null;
		return tag == null ? null : ArtifactItem.parse(tag.getStringOr("ring_id", ""));
	}

	/** Gives the ring its record and unique id when it has none (a freshly crafted pouch). */
	static UUID ensureId(ItemStack stack) {
		UUID id = ringId(stack);
		if (id != null) {
			return id;
		}
		CompoundTag tag = record(stack);
		id = UUID.randomUUID();
		tag.putString("ring_id", id.toString());
		tag.putInt("tier", tierOf(stack));
		stack.set(TempaItems.RING, tag);
		return id;
	}

	public static int extraSlots(ItemStack stack) {
		CompoundTag tag = stack.get(TempaItems.RING);
		return tag == null ? 0 : Math.max(0, tag.getIntOr("extra_slots", 0));
	}

	/** The ring's slots (a multiple of 9, at most 54). */
	public static int slots(ItemStack stack) {
		int tier = Math.max(0, tierOf(stack));
		return Math.min(MAX_SLOTS, SLOTS[tier] + (tier >= 1 && tier <= 3 ? extraSlots(stack) : 0));
	}

	/** A new ring of {@code tier} (0 pouch … 4 Cincin Ruang Agung), bound to {@code owner} when not null, with a fresh ring id. */
	public static ItemStack create(int tier, @Nullable UUID owner) {
		ItemStack stack = new ItemStack(TempaItems.ringItem(tier));
		CompoundTag tag = new CompoundTag();
		tag.putInt("tier", Math.max(0, Math.min(4, tier)));
		tag.putString("ring_id", UUID.randomUUID().toString());
		if (owner != null && tier > 0) {
			tag.putString("owner", owner.toString());
		}
		stack.set(TempaItems.RING, tag);
		return stack;
	}

	static void setOwner(ItemStack stack, @Nullable ServerPlayer owner) {
		CompoundTag tag = record(stack);
		if (owner == null) {
			tag.remove("owner");
			tag.remove("owner_name");
		} else {
			tag.putString("owner", owner.getUUID().toString());
			tag.putString("owner_name", owner.getName().getString());
		}
		stack.set(TempaItems.RING, tag);
	}

	/** Items a ring refuses: rings, pouches, shulker boxes and every bundle. */
	public static boolean forbidden(ItemStack stack) {
		return is(stack) || stack.is(net.minecraft.tags.ItemTags.SHULKER_BOXES) || stack.is(net.minecraft.tags.ItemTags.BUNDLES)
				|| !stack.getItem().canFitInsideContainerItems();
	}

	// ================================================================== use

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			int slot = hand == InteractionHand.MAIN_HAND ? serverPlayer.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
			open(serverPlayer, slot, false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!stack.has(TempaItems.RING)) {
			ensureId(stack);
		}
	}

	@Override
	public void onCraftedBy(ItemStack stack, Player player) {
		if (!player.level().isClientSide()) {
			ensureId(stack);
		}
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	/** Opens the ring in inventory slot {@code slot}; {@code bypass} (op test) ignores a foreign imprint. True when it opened. */
	public static boolean open(ServerPlayer player, int slot, boolean bypass) {
		if (!KultivatorConfig.get().storageRings) {
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.disabled").withStyle(ChatFormatting.RED));
			return false;
		}
		Inventory inventory = player.getInventory();
		if (slot < 0 || slot >= inventory.getContainerSize()) {
			return false;
		}
		ItemStack stack = inventory.getItem(slot);
		if (!is(stack)) {
			return false;
		}
		UUID ringId = ensureId(stack);
		int tier = tierOf(stack);
		UUID owner = bondOwner(stack);
		if (owner != null && !owner.equals(player.getUUID()) && !bypass) {
			String name = ownerName(stack);
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.refused", name.isEmpty() ? "?" : name).withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHULKER_BOX_CLOSE, SoundSource.PLAYERS, 0.8F, 0.5F);
			return false;
		}
		if (owner == null && tier >= 1 && !bond(player, stack)) {
			return false;
		}
		int rows = Math.max(1, slots(stack) / 9);
		ItemStack ring = stack;
		Component title = stack.getHoverName();
		player.openMenu(new ExtendedMenuProvider<TempaMenus.RingOpen>() {
			@Override
			public TempaMenus.RingOpen getScreenOpeningData(ServerPlayer who) {
				return new TempaMenus.RingOpen(slot, rows, tier);
			}

			@Override
			public Component getDisplayName() {
				return title;
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player who) {
				return new RingMenu(containerId, playerInventory, new TempaMenus.RingOpen(slot, rows, tier), ring, ringId);
			}
		});
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.1F);
		return true;
	}

	/** Tanam Jejak Jiwa: the first opening by a cultivator of the ring's realm writes the imprint (10 qi). */
	private static boolean bond(ServerPlayer player, ItemStack stack) {
		int tier = tierOf(stack);
		CultivationData data = Cultivators.get(player);
		if (!data.realm().atLeast(BOND_REALM[tier])) {
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.realm", BOND_REALM[tier].displayName()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!Qi.trySpend(player, BOND_QI)) {
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.qi", (int) BOND_QI).withStyle(ChatFormatting.RED));
			return false;
		}
		setOwner(stack, player);
		Cultivators.dirty(player);
		Fx.send(player.level(), player.position().add(0.0D, 1.0D, 0.0D), FxType.TEMPA, TempaFxIds.RING_BOND, tier, 0.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.tempa.ring.bonded", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
		Advancements.grant(player, "first_ring");
		if (tier >= 4) {
			Advancements.grant(player, "ring_supreme");
		}
		return true;
	}

	/** "Buka Cincin": opens the highest-tier ring bound to the player (or else the best unbound ring or pouch). */
	public static boolean openBest(ServerPlayer player, boolean bypass) {
		Inventory inventory = player.getInventory();
		int best = -1;
		int bestScore = -1;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!is(stack)) {
				continue;
			}
			UUID owner = bondOwner(stack);
			int score;
			if (owner != null && owner.equals(player.getUUID())) {
				score = 10 + tierOf(stack);
			} else if (owner == null) {
				score = tierOf(stack);
			} else {
				score = bypass ? tierOf(stack) : -1;
			}
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		if (best < 0) {
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.none").withStyle(ChatFormatting.RED));
			return false;
		}
		return open(player, best, bypass);
	}

	// ================================================================== tooltip

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		int tier = tierOf(stack);
		builder.accept(Component.translatable("kultivator.tempa.ring.tip.slots", slots(stack)).withStyle(ChatFormatting.GRAY));
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		long used = contents == null ? 0 : contents.nonEmptyItemCopyStream().count();
		builder.accept(Component.translatable("kultivator.tempa.ring.tip.used", used, slots(stack)).withStyle(ChatFormatting.DARK_AQUA));
		if (tier >= 1) {
			String name = ownerName(stack);
			if (bondOwner(stack) != null) {
				builder.accept(Component.translatable("kultivator.tempa.ring.tip.owner", name.isEmpty() ? "?" : name).withStyle(ChatFormatting.GOLD));
			} else {
				builder.accept(Component.translatable("kultivator.tempa.ring.tip.unbound", BOND_REALM[tier].displayName(), (int) BOND_QI)
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		builder.accept(Component.translatable("kultivator.tempa.ring.tip.open").withStyle(ChatFormatting.DARK_GRAY));
		builder.accept(Component.translatable("item.kultivator." + Forging.BuiltinRingNames.NAMES[Math.max(0, tier)] + ".source")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
