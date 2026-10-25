package dev.kultivator.dunia.kota42.ekonomi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * {@code kultivator:rak_dagang} (DESIGN 42.12.3): a three-tier wooden shop rack, 16 x 16 x 8 pixels against the back half of
 * its block, open to the front ({@link #FACING} points at the customer). Holds {@link RakDagangBlockEntity} (3 slots +
 * prices + owner). Placed by players in their own shops (42.6.2) and by 42W in the shop interiors of new chunks; the
 * block itself never changes the world. Using it (buy, restock) is 42C's ({@code TokoPemain}); the item and price
 * display is 42V's block entity renderer. The placer becomes the rack's owner; the aimed shelf is picked by the height of
 * the hit ({@link #slotDari}): the owner stocks it (item in hand), takes it back (empty hand) or cycles its price (sneak,
 * empty hand); anyone else buys one item.
 */
public class RakDagangBlock extends BaseEntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Pixel heights of the three shelf boards (the items stand on them). */
	public static final int[] RAK_Y = {1, 6, 11};
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(0.0D, 0.0D, 8.0D, 1.0D, 16.0D, 16.0D),
			Block.box(15.0D, 0.0D, 8.0D, 16.0D, 16.0D, 16.0D),
			Block.box(1.0D, 0.0D, 15.0D, 15.0D, 16.0D, 16.0D),
			Block.box(1.0D, 0.0D, 8.0D, 15.0D, 1.0D, 15.0D),
			Block.box(1.0D, 5.0D, 8.0D, 15.0D, 6.0D, 15.0D),
			Block.box(1.0D, 10.0D, 8.0D, 15.0D, 11.0D, 15.0D),
			Block.box(1.0D, 15.0D, 8.0D, 15.0D, 16.0D, 15.0D)));

	public RakDagangBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RakDagangBlockEntity(pos, state);
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

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (!level.isClientSide() && by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof RakDagangBlockEntity rak) {
			rak.setPemilik(player.getUUID());
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (itemStack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof RakDagangBlockEntity rak) {
			TokoPemain.rakDipakai(sp, rak, slotDari(pos, hitResult), itemStack);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof RakDagangBlockEntity rak) {
			TokoPemain.rakDipakai(sp, rak, slotDari(pos, hitResult), ItemStack.EMPTY);
		}
		return InteractionResult.SUCCESS;
	}

	/** The shelf at the hit height: 0 below the middle board, 1 below the top board, else 2. */
	public static int slotDari(BlockPos pos, BlockHitResult hit) {
		double y = (hit.getLocation().y - pos.getY()) * 16.0D;
		return y < RAK_Y[1] ? 0 : y < RAK_Y[2] ? 1 : 2;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
