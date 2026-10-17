package com.leafia.contents.miscellanous.regex_filter.pneumatic;

import com.hbm.blocks.BlockContainerBase;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code regex_filter}, the pneumatic tube regex filter: an eight-pixel pipe segment that points
 * at the block it was placed against and pushes what it accepts into it.
 *
 * <p>Upstream: {@code .../regex_filter/pneumatic/RegexFilterBlock.java}, a {@code BlockContainer}
 * with six facings. Right click opens the filter screen.
 *
 * <h2>Which way it points</h2>
 *
 * Upstream's {@code getStateForPlacement} stores the <em>opposite of the clicked face</em>, so a
 * filter placed on the side of a chest points into the chest; the {@link #FACING} is the
 * <b>output</b> end, and the item handler is on the opposite face (the open back).
 *
 * <h2>The shape</h2>
 *
 * A square rod the full length of the block along its axis and 8/16 across, which is both its
 * outline and its collision, as upstream's one {@code getBoundingBox} was both. The model is
 * the same rod with a collar at each end.
 *
 * <p>Upstream's six {@code isOpaqueCube}/{@code isFullCube}/{@code isNormalCube} overrides are
 * what {@code noOcclusion} and the three {@code false} predicates on the properties say in 1.21.1,
 * and are set where the block is registered.
 */
public class RegexFilterBlock extends BlockContainerBase {

	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final VoxelShape SHAPE_X = Shapes.box(0, 4d / 16, 4d / 16, 1, 1 - 4d / 16, 1 - 4d / 16);
	private static final VoxelShape SHAPE_Y = Shapes.box(4d / 16, 0, 4d / 16, 1 - 4d / 16, 1, 1 - 4d / 16);
	private static final VoxelShape SHAPE_Z = Shapes.box(4d / 16, 4d / 16, 0, 1 - 4d / 16, 1 - 4d / 16, 1);

	public RegexFilterBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch(state.getValue(FACING).getAxis()) {
			case X -> SHAPE_X;
			case Y -> SHAPE_Y;
			case Z -> SHAPE_Z;
		};
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RegexFilterTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.REGEX_FILTER.get(), RegexFilterTE::serverTick);
	}

	/** Upstream opens the screen on every right click - sneaking included, which vanilla decides first. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return InteractionResult.SUCCESS;

		if(level.getBlockEntity(pos) instanceof RegexFilterTE filter && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(filter, pos);

		return InteractionResult.CONSUME;
	}
}
