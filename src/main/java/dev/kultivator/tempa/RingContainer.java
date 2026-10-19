package dev.kultivator.tempa;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The open ring's slots (DESIGN 25.7.3): a plain container that refuses rings, pouches, shulker boxes and bundles, and on
 * the server calls the menu's write-through on every change (no {@code ItemStack} reference to the ring is ever held).
 */
public class RingContainer extends SimpleContainer {

	private @Nullable Runnable onChange;

	public RingContainer(int size) {
		super(size);
	}

	void onChange(@Nullable Runnable listener) {
		this.onChange = listener;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return !StorageRingItem.forbidden(stack);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (onChange != null) {
			onChange.run();
		}
	}
}
