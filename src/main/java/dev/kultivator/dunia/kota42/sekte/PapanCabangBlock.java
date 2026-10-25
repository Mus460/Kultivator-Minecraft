package dev.kultivator.dunia.kota42.sekte;

import com.mojang.logging.LogUtils;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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
 * {@code kultivator:papan_cabang} (DESIGN 42.12.3, 42.8.4 "Cabang sekte di kota"): the sign board of a player sect's town
 * branch, a dark board between two posts bearing the sect emblem. Registered with the other 42 blocks in
 * {@code ekonomi/KotaEkonomiBlocks}; its block entity {@link Isi} keeps the owning sect (hall owner's soul), the emblem
 * index of the {@code Lambang} atlas (copied from {@code sekte/seragam/SeragamData.lambang()} when the branch opens, so the
 * client draws it without the hall data), the sect name shown under it and the ink colour. 42E's {@code CabangKota} sets
 * them; 42V draws the emblem from the client {@code LambangAtlas}.
 */
public class PapanCabangBlock extends BaseEntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(1.0D, 0.0D, 7.0D, 2.5D, 16.0D, 9.0D),
			Block.box(13.5D, 0.0D, 7.0D, 15.0D, 16.0D, 9.0D),
			Block.box(0.0D, 4.0D, 7.25D, 16.0D, 15.0D, 8.75D)));

	public PapanCabangBlock(BlockBehaviour.Properties properties) {
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

	/** 42E: placing the sign inside an owned building opens the branch ({@link CabangKota#pasang}). */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level instanceof ServerLevel server) {
			CabangKota.pasang(server, pos, by);
		}
	}

	/** 42E: the sign is gone (broken, replaced): its branch closes ({@link CabangKota#papanHilang}). */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		CabangKota.papanHilang(level, pos);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** The branch sign's block entity, synced to clients for the renderer. */
	public static class Isi extends BlockEntity {

		/** Longest sect name kept on the board. */
		public static final int NAMA_MAKS = 32;
		/** Default ink: gold. */
		public static final int WARNA_BAWAAN = 0xE8C860;
		private static final Logger LOGGER = LogUtils.getLogger();

		private @Nullable UUID pemilik;
		private int lambang = -1;
		private String namaSekte = "";
		private int warna = WARNA_BAWAAN;

		public Isi(BlockPos pos, BlockState state) {
			super(KotaEkonomiBlocks.PAPAN_CABANG_BE, pos, state);
		}

		/** The sect owner's soul (the hall owner), or null for a blank board. */
		public @Nullable UUID pemilik() {
			return pemilik;
		}

		/** The emblem index of the {@code Lambang} atlas, -1 = none (a blank board). */
		public int lambang() {
			return lambang;
		}

		public String namaSekte() {
			return namaSekte;
		}

		/** Ink colour 0xRRGGBB of the name and emblem. */
		public int warna() {
			return warna;
		}

		/** Binds the board to a sect: owner, emblem index, name and ink colour. */
		public void ikat(@Nullable UUID pemilik, int lambang, @Nullable String namaSekte, int warna) {
			this.pemilik = pemilik;
			this.lambang = Math.max(-1, lambang);
			String nama = namaSekte == null ? "" : namaSekte.strip();
			this.namaSekte = nama.length() > NAMA_MAKS ? nama.substring(0, NAMA_MAKS) : nama;
			this.warna = warna & 0xFFFFFF;
			setChanged();
		}

		/** Clears the board (the branch closed). */
		public void kosongkan() {
			ikat(null, -1, "", WARNA_BAWAAN);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			pemilik = input.read("pemilik", UUIDUtil.CODEC).orElse(null);
			lambang = Math.max(-1, input.getIntOr("lambang", -1));
			String nama = input.getStringOr("nama", "");
			namaSekte = nama.length() > NAMA_MAKS ? nama.substring(0, NAMA_MAKS) : nama;
			warna = input.getIntOr("warna", WARNA_BAWAAN) & 0xFFFFFF;
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			tulis(output);
		}

		private void tulis(ValueOutput output) {
			output.storeNullable("pemilik", UUIDUtil.CODEC, pemilik);
			output.putInt("lambang", lambang);
			output.putString("nama", namaSekte);
			output.putInt("warna", warna);
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
