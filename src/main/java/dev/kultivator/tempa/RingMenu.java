package dev.kultivator.tempa;

import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The open storage ring (DESIGN 25.7.3): its rows over a {@link RingContainer} plus the player's inventory. Write-through:
 * every change on the server looks the ring up in the player's inventory <em>by {@code ring_id}</em> and writes its
 * {@code minecraft:container}; when it is gone the menu closes and nothing is written. The ring's own slot is locked (no
 * pickup, so no THROW; no placing), and number-key swaps into that hotbar index or the offhand (button 40) are refused.
 */
public class RingMenu extends AbstractContainerMenu {

	public static final int LEFT = 15;
	public static final int TOP = 20;
	public static final int WIDTH = 190;
	/** Gap between the ring rows and the inventory. */
	public static final int GAP = 14;
	public static final int BTN_ERASE = 1;

	public final int rows;
	public final int tier;
	public final int ringSlot;
	private final RingContainer container;
	private final Player player;
	private final @Nullable UUID ringId;
	private boolean closing;

	/** Client side (from the opening packet). */
	public RingMenu(int containerId, Inventory inventory, TempaMenus.RingOpen open) {
		this(containerId, inventory, open, ItemStack.EMPTY, null);
	}

	/** Server side: the ring's contents are copied in once; afterwards the ring is only found again by its id. */
	public RingMenu(int containerId, Inventory inventory, TempaMenus.RingOpen open, ItemStack ring, @Nullable UUID ringId) {
		super(TempaMenus.RING, containerId);
		this.rows = Math.max(1, Math.min(6, open.rows()));
		this.tier = open.tier();
		this.ringSlot = open.slot();
		this.player = inventory.player;
		this.ringId = ringId;
		this.container = new RingContainer(rows * 9);
		if (!ring.isEmpty()) {
			ItemContainerContents contents = ring.get(DataComponents.CONTAINER);
			if (contents != null) {
				contents.copyInto(container.items);
			}
			container.onChange(this::writeBack);
		}
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(container, col + row * 9, LEFT + col * 18, TOP + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return !StorageRingItem.forbidden(stack);
					}
				});
			}
		}
		int invTop = TOP + rows * 18 + GAP;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addInventorySlot(inventory, col + (row + 1) * 9, LEFT + col * 18, invTop + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			addInventorySlot(inventory, col, LEFT + col * 18, invTop + 58);
		}
	}

	/** The GUI height for {@code rows} ring rows. */
	public static int height(int rows) {
		return TOP + rows * 18 + GAP + 58 + 18 + 8;
	}

	private void addInventorySlot(Inventory inventory, int index, int x, int y) {
		boolean locked = index == ringSlot;
		this.addSlot(new Slot(inventory, index, x, y) {
			@Override
			public boolean mayPickup(Player who) {
				return !locked;
			}

			@Override
			public boolean mayPlace(ItemStack stack) {
				return !locked;
			}
		});
	}

	/** True for the menu slot showing the ring itself. */
	public boolean isRingSlot(Slot slot) {
		return slot.container instanceof Inventory && slot.getContainerSlot() == ringSlot;
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player who) {
		if (input == ContainerInput.SWAP && (buttonNum == Inventory.SLOT_OFFHAND || buttonNum == ringSlot)) {
			return;
		}
		if (slotIndex >= 0 && slotIndex < this.slots.size() && isRingSlot(this.slots.get(slotIndex))) {
			return;
		}
		if (who instanceof ServerPlayer serverPlayer && slotIndex >= 0 && slotIndex < this.slots.size()) {
			boolean intoRing = slotIndex < rows * 9 && input == ContainerInput.PICKUP && StorageRingItem.forbidden(getCarried());
			boolean shifted = slotIndex >= rows * 9 && input == ContainerInput.QUICK_MOVE && StorageRingItem.forbidden(this.slots.get(slotIndex).getItem());
			if (intoRing || shifted) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.tempa.ring.refuse_item").withStyle(ChatFormatting.RED));
			}
		}
		super.clicked(slotIndex, buttonNum, input, who);
	}

	@Override
	public boolean clickMenuButton(Player who, int buttonId) {
		if (buttonId == BTN_ERASE && who instanceof ServerPlayer serverPlayer) {
			int index = find(serverPlayer);
			if (index >= 0) {
				ItemStack ring = serverPlayer.getInventory().getItem(index);
				UUID owner = StorageRingItem.bondOwner(ring);
				if (owner != null && owner.equals(serverPlayer.getUUID())) {
					StorageRingItem.setOwner(ring, null);
					Msg.chat(serverPlayer, Component.translatable("kultivator.tempa.ring.erased").withStyle(ChatFormatting.YELLOW));
					serverPlayer.closeContainer();
				}
			}
		}
		return true;
	}

	/** The ring's inventory index found by its id, or -1. */
	private int find(Player who) {
		if (ringId == null) {
			return -1;
		}
		Inventory inventory = who.getInventory();
		if (ringSlot >= 0 && ringSlot < inventory.getContainerSize() && ringId.equals(StorageRingItem.ringId(inventory.getItem(ringSlot)))) {
			return ringSlot;
		}
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (ringId.equals(StorageRingItem.ringId(inventory.getItem(i)))) {
				return i;
			}
		}
		return -1;
	}

	/** Server: writes the slots into the ring found by id; closes the menu without writing when it is gone. */
	private void writeBack() {
		if (closing || ringId == null || player.level().isClientSide()) {
			return;
		}
		int index = find(player);
		if (index < 0) {
			closing = true;
			if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu == this) {
				serverPlayer.closeContainer();
			}
			return;
		}
		player.getInventory().getItem(index).set(DataComponents.CONTAINER, ItemContainerContents.fromItems(container.getItems()));
	}

	@Override
	public ItemStack quickMoveStack(Player who, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem() || !slot.mayPickup(who)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int ringSlots = rows * 9;
		if (slotIndex < ringSlots) {
			if (!this.moveItemStackTo(stack, ringSlots, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			if (StorageRingItem.forbidden(stack) || !this.moveItemStackTo(stack, 0, ringSlots, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player who) {
		return who.isAlive() && (who.level().isClientSide() || find(who) >= 0);
	}

	@Override
	public void removed(Player who) {
		super.removed(who);
		if (!closing) {
			writeBack();
		}
		closing = true;
		container.onChange(null);
	}
}
