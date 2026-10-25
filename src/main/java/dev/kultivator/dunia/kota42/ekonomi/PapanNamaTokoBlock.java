package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * {@code kultivator:papan_nama_toko} (DESIGN 42.12.3, 42.6.2): the hanging shop sign of a player shop, a 14 x 8 x 1 lacquered
 * board on two short chains under the block above. {@link #FACING} is the side the text faces. Its block entity
 * {@link Isi} keeps the shop name (at most {@link Isi#NAMA_MAKS} characters, set by 42C's {@code TokoPemain} when the shop
 * is opened or renamed) and the owner; the 42V block entity renderer writes the name in gold on the board.
 */
public class PapanNamaTokoBlock extends BaseEntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(1.0D, 2.0D, 7.5D, 15.0D, 10.0D, 8.5D),
			Block.box(3.0D, 10.0D, 7.5D, 4.0D, 16.0D, 8.5D),
			Block.box(12.0D, 10.0D, 7.5D, 13.0D, 16.0D, 8.5D)));

	public PapanNamaTokoBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Isi(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/** The placer owns the sign; inside his shop it shows the shop's name, else his own name until a shop renames it. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level instanceof ServerLevel server && by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof Isi isi) {
			isi.setPemilik(player.getUUID());
			TokoPemain.Toko toko = TokoPemain.di(server, pos);
			if (toko == null) {
				toko = TokoPemain.di(server, pos.below());
			}
			isi.setNama(toko != null && player.getUUID().equals(toko.pemilik()) ? toko.nama() : player.getName().getString());
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** The sign's block entity: shop name and owner, synced to clients for the renderer. */
	public static class Isi extends BlockEntity {

		/** Longest shop name (42.12.3). */
		public static final int NAMA_MAKS = 16;
		private static final Logger LOGGER = LogUtils.getLogger();

		private String nama = "";
		private @Nullable UUID pemilik;

		public Isi(BlockPos pos, BlockState state) {
			super(KotaEkonomiBlocks.PAPAN_NAMA_TOKO_BE, pos, state);
		}

		/** The shop name ("" = blank board). */
		public String nama() {
			return nama;
		}

		public @Nullable UUID pemilik() {
			return pemilik;
		}

		/** Sets the name, cut to {@link #NAMA_MAKS} characters and stripped of line breaks. */
		public void setNama(@Nullable String nama) {
			String bersih = nama == null ? "" : nama.replace('\n', ' ').replace('\r', ' ').strip();
			this.nama = bersih.length() > NAMA_MAKS ? bersih.substring(0, NAMA_MAKS) : bersih;
			setChanged();
		}

		public void setPemilik(@Nullable UUID pemilik) {
			this.pemilik = pemilik;
			setChanged();
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			String simpan = input.getStringOr("nama", "");
			nama = simpan.length() > NAMA_MAKS ? simpan.substring(0, NAMA_MAKS) : simpan;
			pemilik = input.read("pemilik", UUIDUtil.CODEC).orElse(null);
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			tulis(output);
		}

		private void tulis(ValueOutput output) {
			output.putString("nama", nama);
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
	}
}
