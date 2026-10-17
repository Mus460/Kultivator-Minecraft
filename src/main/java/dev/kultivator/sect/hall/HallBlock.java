package dev.kultivator.sect.hall;

import dev.kultivator.kehancuran.ProtectedZones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
 * Prasasti Aula Sekte (DESIGN 20.3.1): a jade tablet with a gold cap on a stone foot, the testing stone of the Xiao clan
 * (<i>Battle Through the Heavens</i>). Players place it and mine it the vanilla way; the block itself never changes the
 * world. Placing founds (or wakes) its placer's hall, using it opens the hall scroll, an Immortal Ascension Token used
 * on it calls a candidate, and its removal makes the hall dormant.
 */
public class HallBlock extends Block {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
			Block.box(2.0D, 0.0D, 3.0D, 14.0D, 3.0D, 13.0D),
			Block.box(4.0D, 3.0D, 6.0D, 12.0D, 14.0D, 10.0D),
			Block.box(3.0D, 14.0D, 5.0D, 13.0D, 16.0D, 11.0D)));

	public HallBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level instanceof ServerLevel serverLevel) {
			// Mode Kehancuran's hall zone covers every stele, active or inert (DESIGN 20.3.21).
			ProtectedZones.noteStele(serverLevel, pos);
		}
		if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player) {
			Halls.onStelePlaced(player, serverLevel, pos.immutable());
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			Halls.onSteleUsed(serverPlayer, serverLevel, pos.immutable(), player.isSecondaryUseActive());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!itemStack.is(HallItems.IMMORTAL_ASCENSION_TOKEN)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			Candidates.useToken(serverPlayer, serverLevel, pos.immutable(), itemStack);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		ProtectedZones.forgetStele(level, pos);
		Halls.onSteleRemoved(level, pos.immutable());
	}
}
