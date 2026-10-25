package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.UUID;

/**
 * The block entity of {@code kultivator:rak_dagang} (DESIGN 42.12.3, 42.6.2 "Toko sendiri"): three display slots, one per
 * shelf (bottom, middle, top), each with an asking price, and the shop owner. The 42V {@code BlockEntityRenderer} draws
 * each slot's item on its shelf with a small price label; selling, restocking and the shop ledger are 42C's
 * ({@code TokoPemain}), which calls the setters below on the server thread.
 * <p>
 * Prices are in the unit of the unified economy's smallest spirit stone (Batu Roh Rendah = 1; 9 / 81 / 729 for the higher
 * stones; DESIGN 38.M), 0 = "not for sale" (shown, not sold). The whole state is synced to clients through
 * {@link #getUpdateTag} so the renderer always shows what the server has; {@link #setChanged()} pushes a block update.
 * Breaking the block drops the displayed stacks (never lost).
 */
public class RakDagangBlockEntity extends BlockEntity {

	public static final int SLOT = 3;
	/** Highest asking price per slot (one stack of the highest stone, 729 x 64). */
	public static final int HARGA_MAKS = 729 * 64;

	private static final Logger LOGGER = LogUtils.getLogger();

	private final NonNullList<ItemStack> barang = NonNullList.withSize(SLOT, ItemStack.EMPTY);
	private final int[] harga = new int[SLOT];
	private @Nullable UUID pemilik;

	public RakDagangBlockEntity(BlockPos pos, BlockState state) {
		super(KotaEkonomiBlocks.RAK_DAGANG_BE, pos, state);
	}

	// ================================================================== state

	/** The stack on shelf {@code slot} (0 bottom .. 2 top); never null. Do not modify the returned stack. */
	public ItemStack barang(int slot) {
		return slot >= 0 && slot < SLOT ? barang.get(slot) : ItemStack.EMPTY;
	}

	/** The asking price of shelf {@code slot} (0 = display only). */
	public int harga(int slot) {
		return slot >= 0 && slot < SLOT ? harga[slot] : 0;
	}

	/** The shop owner (a player soul), or null for a town-owned rack. */
	public @Nullable UUID pemilik() {
		return pemilik;
	}

	/** Puts {@code stack} on shelf {@code slot} (a copy is kept) and returns what was there. */
	public ItemStack taruh(int slot, ItemStack stack) {
		if (slot < 0 || slot >= SLOT) {
			return stack;
		}
		ItemStack lama = barang.get(slot);
		barang.set(slot, stack == null ? ItemStack.EMPTY : stack.copy());
		setChanged();
		return lama;
	}

	/** Takes the whole stack off shelf {@code slot}. */
	public ItemStack ambil(int slot) {
		return taruh(slot, ItemStack.EMPTY);
	}

	public void setHarga(int slot, int nilai) {
		if (slot >= 0 && slot < SLOT) {
			harga[slot] = Math.max(0, Math.min(HARGA_MAKS, nilai));
			setChanged();
		}
	}

	public void setPemilik(@Nullable UUID pemilik) {
		this.pemilik = pemilik;
		setChanged();
	}

	/** True when no shelf holds anything. */
	public boolean kosong() {
		return barang.stream().allMatch(ItemStack::isEmpty);
	}

	// ================================================================== save and sync

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		barang.clear();
		ContainerHelper.loadAllItems(input, barang);
		int[] simpan = input.getIntArray("harga").orElse(new int[0]);
		Arrays.fill(harga, 0);
		for (int i = 0; i < Math.min(SLOT, simpan.length); i++) {
			harga[i] = Math.max(0, Math.min(HARGA_MAKS, simpan[i]));
		}
		pemilik = input.read("pemilik", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		tulis(output);
	}

	private void tulis(ValueOutput output) {
		ContainerHelper.saveAllItems(output, barang, true);
		output.putIntArray("harga", harga.clone());
		output.storeNullable("pemilik", UUIDUtil.CODEC, pemilik);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			tulis(output);
			return output.buildResult();
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			Containers.dropContents(this.level, pos, barang);
		}
	}
}
