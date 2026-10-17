package com.leafia.contents.network;

import java.util.Map;

import com.hbm.blocks.BlockContainerBase;
import com.hbm.blocks.PipeTurn;
import com.hbm.lib.Library;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The addon's cable block: a six-armed block that draws an arm at every neighbour it connects to.
 *
 * <p>Upstream: {@code com/leafia/contents/network/BlockCable.java}, a copy of the base mod's
 * power cable ({@code com.hbm.blocks.network.BlockCable}) that exists so the addon can subclass
 * it - its one subclass anywhere is {@code SPKCableBlock}. It is the same translation the base
 * cable went through (D-700): **the six connections are blockstate**, vanilla's
 * {@link PipeBlock} booleans, because a 1.21.1 model is chosen from the state and upstream drew
 * the arms from a {@code TileEntitySpecialRenderer} asking the neighbours every frame.
 * Recomputed in {@code getStateForPlacement}, {@code onPlace}, {@code updateShape} and
 * {@code neighborChanged}, which together cover every way a neighbour can change.
 *
 * <h2>What is different from upstream</h2>
 *
 * <ul>
 * <li><b>Abstract.</b> Upstream's default is {@code new TileEntityCableBaseNT()}, a power cable's
 *     block entity. A 1.21.1 block entity is bound to a registered type that names its blocks, so
 *     this class cannot hand out the base mod's cable block entity for a block that type does not
 *     list. Nothing in the clone instantiates a bare {@code BlockCable}, so the default is
 *     dropped rather than faked: a subclass supplies its block entity and its connection rule.
 * <li><b>{@link #connectsTo}</b> is the one rule. Upstream's default is
 *     {@code Library.canConnect} in {@code BlockCable.getBoundingBox} and
 *     {@code instanceof ISPKConnector} in {@code SPKCableBlock}'s override; here the shape comes
 *     from the state, and the rule sits in one overridable place.
 * <li><b>The shape is one box</b>, exactly upstream's {@code getBB}: the core spans
 *     {@code 5.5/16 .. 10.5/16}, and each connected side stretches the box to the block's edge.
 *     A cable connected east and up has a box that includes the empty corner between them; that
 *     is what a player stands on upstream, and what the base mod's own cable does too.
 * <li>Render type {@code ENTITYBLOCK_ANIMATED}, {@code isFullCube = false} and the rest of the
 *     opacity overrides become the block's {@code noOcclusion()} property and its shape.
 * <li>{@code setCreativeTab(MainRegistry.blockTab)} and {@code AddonBlocks.ALL_BLOCKS.add} are
 *     registration-site arguments now.
 * </ul>
 */
public abstract class BlockCable extends BlockContainerBase {

	public static final BooleanProperty DOWN = PipeBlock.DOWN;
	public static final BooleanProperty UP = PipeBlock.UP;
	public static final BooleanProperty NORTH = PipeBlock.NORTH;
	public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
	public static final BooleanProperty WEST = PipeBlock.WEST;
	public static final BooleanProperty EAST = PipeBlock.EAST;

	public static final Map<Direction, BooleanProperty> BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

	/** Upstream's {@code 11 * p / 2} and its complement, in the sixteenths a VoxelShape takes. */
	private static final double MIN = 5.5D;
	private static final double MAX = 10.5D;

	/** One shape per combination of the six connections, indexed by {@link #mask}. */
	private static final VoxelShape[] SHAPES = makeShapes();

	protected BlockCable(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(DOWN, false).setValue(UP, false)
				.setValue(NORTH, false).setValue(SOUTH, false)
				.setValue(WEST, false).setValue(EAST, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(DOWN, UP, NORTH, SOUTH, WEST, EAST);
	}

	/// SHAPE ///

	private static VoxelShape[] makeShapes() {

		VoxelShape[] shapes = new VoxelShape[64];

		for(int i = 0; i < shapes.length; i++) {
			shapes[i] = getBB(
					has(i, Direction.EAST), has(i, Direction.UP), has(i, Direction.SOUTH),
					has(i, Direction.WEST), has(i, Direction.DOWN), has(i, Direction.NORTH));
		}

		return shapes;
	}

	/** Upstream's {@code getBB(posX, posY, posZ, negX, negY, negZ)}, argument order and all. */
	protected static VoxelShape getBB(boolean posX, boolean posY, boolean posZ, boolean negX, boolean negY, boolean negZ) {
		return Shapes.box(
				(negX ? 0D : MIN) / 16D,
				(negY ? 0D : MIN) / 16D,
				(negZ ? 0D : MIN) / 16D,
				(posX ? 16D : MAX) / 16D,
				(posY ? 16D : MAX) / 16D,
				(posZ ? 16D : MAX) / 16D);
	}

	private static boolean has(int mask, Direction dir) {
		return (mask & (1 << dir.ordinal())) != 0;
	}

	/** The six connections as bits, {@code 1 << Direction.ordinal()}. */
	public static int mask(BlockState state) {
		int mask = 0;
		for(Direction dir : Direction.values()) {
			if(state.getValue(BY_DIRECTION.get(dir))) mask |= 1 << dir.ordinal();
		}
		return mask;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[mask(state)];
	}

	/// CONNECTIONS ///

	/**
	 * Whether this cable draws an arm at the block on the far side of {@code dir}.
	 *
	 * <p>Upstream's {@code Library.canConnect} six times in the base class. A subclass overrides it.
	 *
	 * @param neighborPos the neighbour's position
	 * @param dir         the direction from this cable to the neighbour
	 */
	protected boolean connectsTo(LevelReader level, BlockPos neighborPos, Direction dir) {
		return Library.canConnect(level, neighborPos, dir);
	}

	private BlockState connect(BlockState state, LevelReader level, BlockPos pos) {

		for(Direction dir : Direction.values()) {
			state = state.setValue(BY_DIRECTION.get(dir), connectsTo(level, pos.relative(dir), dir));
		}

		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
			LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		return state.setValue(BY_DIRECTION.get(direction), connectsTo(level, neighborPos, direction));
	}

	/**
	 * The cable itself appearing from worldgen, a structure or {@code /setblock}, where
	 * {@link #getStateForPlacement} never runs and vanilla only pushes shape updates outward.
	 */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {

		super.onPlace(state, level, pos, oldState, movedByPiston);

		if(level.isClientSide) return;

		BlockState updated = connect(state, level, pos);
		if(updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	/** Block updates that are not shape updates: a machine changing what it accepts without moving. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			BlockPos neighborPos, boolean movedByPiston) {

		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

		if(level.isClientSide) return;

		BlockState updated = connect(state, level, pos);
		if(updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	/** Turns the six side booleans with a structure's rotation or mirror (D-677). */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return PipeTurn.rotate(state, rotation);
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return PipeTurn.mirror(state, mirror);
	}
}
