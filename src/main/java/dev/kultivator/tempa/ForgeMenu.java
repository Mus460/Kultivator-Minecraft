package dev.kultivator.tempa;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Earthfire Forge's menu (DESIGN 25.5.2): like a crafting table there is no block entity, the 9 material slots live in
 * the menu and closing it hands them back, so breaking the forge can never duplicate or lose anything. Materials are
 * consumed only when a job completes; while a job runs the slots are locked. Buttons go through vanilla's menu-button
 * packet ({@link #clickMenuButton}); the synced data holds the tab, the progress, the heat and flags.
 */
public class ForgeMenu extends AbstractContainerMenu {

	public static final int INPUT = 9;
	public static final int WIDTH = 270;
	public static final int HEIGHT = 214;
	public static final int GRID_X = 14;
	public static final int GRID_Y = 32;
	public static final int INV_X = 54;
	public static final int INV_Y = 132;

	public static final int TAB_NEW = 0;
	public static final int TAB_REFINE = 1;
	public static final int TAB_SPIRIT = 2;
	public static final int TAB_RING = 3;

	public static final int BTN_START = 10;
	public static final int BTN_CANCEL = 11;
	public static final int BTN_ASSIST = 12;
	public static final int BTN_RELEASE = 13;
	public static final int BTN_NATAL = 14;
	public static final int BTN_FEED = 20;
	public static final int BTN_SEED = 21;
	public static final int BTN_SOUL = 22;
	public static final int BTN_PURIFY = 23;
	/** 30 + form index: the form the Kuali's embryo takes. */
	public static final int BTN_KUALI_FORM = 30;
	public static final int BTN_COLLAPSE = 41;

	public static final int DATA_TAB = 0;
	public static final int DATA_PROGRESS = 1;
	public static final int DATA_HEAT = 2;
	public static final int DATA_FLAGS = 3;
	public static final int FLAG_RUNNING = 1;
	public static final int FLAG_PAUSED = 2;
	public static final int FLAG_NATAL = 4;

	public final BlockPos pos;
	public final SimpleContainer input;
	private final ContainerData data = new SimpleContainerData(4);

	/** Server only: the running job, the Kuali form choice, the preview to resend. */
	@Nullable Forging.Job job;
	int kualiForm;
	boolean previewDirty = true;

	public ForgeMenu(int containerId, Inventory inventory, BlockPos pos) {
		super(TempaMenus.FORGE, containerId);
		this.pos = pos;
		this.input = new SimpleContainer(INPUT) {
			@Override
			public void setChanged() {
				super.setChanged();
				ForgeMenu.this.slotsChanged(this);
			}
		};
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				this.addSlot(new Slot(input, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return !running();
					}

					@Override
					public boolean mayPickup(Player who) {
						return !running();
					}
				});
			}
		}
		this.addStandardInventorySlots(inventory, INV_X, INV_Y);
		this.addDataSlots(data);
	}

	public int tab() {
		return Math.max(TAB_NEW, Math.min(TAB_RING, data.get(DATA_TAB)));
	}

	/** Progress of the running job, 0..1. */
	public float progress() {
		return Math.max(0, Math.min(1000, data.get(DATA_PROGRESS))) / 1000.0F;
	}

	public int heat() {
		return Math.max(Heat.INNATE, Math.min(Heat.HEAVENLY, data.get(DATA_HEAT)));
	}

	public boolean running() {
		return (data.get(DATA_FLAGS) & FLAG_RUNNING) != 0;
	}

	public boolean paused() {
		return (data.get(DATA_FLAGS) & FLAG_PAUSED) != 0;
	}

	/** Tempa Ulang refines the virtual natal record instead of an artifact in the slots. */
	public boolean natalTarget() {
		return (data.get(DATA_FLAGS) & FLAG_NATAL) != 0;
	}

	void setTab(int tab) {
		data.set(DATA_TAB, tab);
	}

	void setProgress(float fraction) {
		data.set(DATA_PROGRESS, Math.round(Math.max(0.0F, Math.min(1.0F, fraction)) * 1000.0F));
	}

	void setHeat(int heat) {
		data.set(DATA_HEAT, heat);
	}

	void setFlag(int flag, boolean on) {
		int flags = data.get(DATA_FLAGS);
		data.set(DATA_FLAGS, on ? flags | flag : flags & ~flag);
	}

	@Override
	public boolean clickMenuButton(Player who, int buttonId) {
		if (who instanceof ServerPlayer serverPlayer) {
			Forging.button(serverPlayer, this, buttonId);
		}
		return true;
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		previewDirty = true;
	}

	@Override
	public ItemStack quickMoveStack(Player who, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (!slot.hasItem() || running()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (slotIndex < INPUT) {
			if (!this.moveItemStackTo(stack, INPUT, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, INPUT, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(who, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player who) {
		return who.level().getBlockState(pos).is(TempaItems.EARTHFIRE_FORGE)
				&& who.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 25.0D;
	}

	@Override
	public void removed(Player who) {
		super.removed(who);
		if (who instanceof ServerPlayer serverPlayer) {
			Forging.stop(serverPlayer, this, true);
			this.clearContainer(who, input);
		}
	}
}
