package com.leafia.contents.network.pipe_amat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.blocks.ILookOverlay;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.lib.Library;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * `amat_duct`, the antimatter pipe - upstream's "Electromagnetic Fluid Duct".
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/AmatDuctStandard.java}, 455 lines
 * of which most is 1.12.2 rendering. What survives is the geometry and the connection rules;
 * the {@code IDynamicModels} / {@code ItemBlockSpecialAABB} / {@code AmatDuctBakedModel}
 * machinery is replaced by a blockstate and model JSON exactly as D-019 and D-701 replaced the
 * base mod's equivalents, and upstream's own {@code pipe_amat.obj} is loaded through this
 * port's OBJ pipeline rather than through a hand-rolled baked model.
 *
 * <p>**The six connections are vanilla {@link PipeBlock} booleans**, and the shapes are the
 * base mod's {@code FluidDuctStandard} boxes — this is the same pipe system, and D-1000 says our
 * value wins where the two lineages differ. Upstream's own {@code addCollisionBoxToList}
 * branches on the same 0.3125/0.6875 core and the same axis special cases, so nothing is lost
 * by taking them from the version that is already tested here.
 *
 * <p>**The two networks are kept apart at both ends.** {@link AmatDuctTE#canConnect} refuses a
 * plain duct that asks this pipe; {@link #connect} refuses a plain duct this pipe asks, since
 * the base-mod duct would otherwise answer for itself and say yes. Without the second half the
 * pipes would draw as joined while carrying on entirely separate networks, which is the worst
 * of both.
 *
 * <p>**The overlay pass is not reproduced**, for the same reason and with the same consequence
 * as the base mod's ducts: upstream draws {@code pipe_amat.obj} twice, once plain and once in
 * {@code pipe_amat_overlay.png} tinted to the fluid's colour, and a baked model cannot express
 * two textures on one set of quads with one tinted. The pipe renders in its plain texture. See
 * D-708, which settled this for {@code fluid_duct_neo}.
 */
public class AmatDuctStandard extends AmatDuctBase implements ILookOverlay {

	public static final BooleanProperty DOWN = PipeBlock.DOWN;
	public static final BooleanProperty UP = PipeBlock.UP;
	public static final BooleanProperty NORTH = PipeBlock.NORTH;
	public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
	public static final BooleanProperty WEST = PipeBlock.WEST;
	public static final BooleanProperty EAST = PipeBlock.EAST;

	public static final Map<Direction, BooleanProperty> BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

	/** The core cube's bounds, in the sixteenths a VoxelShape takes. The base mod's, and upstream's. */
	private static final double LOWER = 0.3125D;
	private static final double UPPER = 0.6875D;

	private static final VoxelShape[] SHAPES = makeShapes();

	public AmatDuctStandard(Properties properties) {
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

	@Override
	public BlockEntityType<? extends BlockEntityPipeBaseNT> ductType() {
		return LeafiaBlockEntities.AMAT_DUCT.get();
	}

	/// SHAPE ///

	private static VoxelShape[] makeShapes() {
		VoxelShape[] shapes = new VoxelShape[64];
		for(int mask = 0; mask < 64; mask++) shapes[mask] = shapeFor(mask);
		return shapes;
	}

	/** Public so the model generator reads the same boxes rather than re-deriving them. */
	public static VoxelShape shapeFor(int mask) {

		boolean pX = has(mask, 32), nX = has(mask, 16);
		boolean pY = has(mask, 8), nY = has(mask, 4);
		boolean pZ = has(mask, 2), nZ = has(mask, 1);

		if(mask == 0) {
			return Shapes.or(
					aabb(0.6875, 0.3125, 0.3125, 1D, 0.6875, 0.6875),
					aabb(0D, 0.3125, 0.3125, 0.3125, 0.6875, 0.6875),
					aabb(0.3125, 0.6875, 0.3125, 0.6875, 1D, 0.6875),
					aabb(0.3125, 0D, 0.3125, 0.6875, 0.3125, 0.6875),
					aabb(0.3125, 0.3125, 0.6875, 0.6875, 0.6875, 1D),
					aabb(0.3125, 0.3125, 0D, 0.6875, 0.6875, 0.3125));
		} else if((mask & 0b001111) == 0) {
			return aabb(0D, LOWER, LOWER, 1D, UPPER, UPPER);
		} else if((mask & 0b110011) == 0) {
			return aabb(LOWER, 0D, LOWER, UPPER, 1D, UPPER);
		} else if((mask & 0b111100) == 0) {
			return aabb(LOWER, LOWER, 0D, UPPER, UPPER, 1D);
		}

		VoxelShape shape = aabb(LOWER, LOWER, LOWER, UPPER, UPPER, UPPER);
		if(pX) shape = Shapes.or(shape, aabb(UPPER, LOWER, LOWER, 1D, UPPER, UPPER));
		if(nX) shape = Shapes.or(shape, aabb(0D, LOWER, LOWER, LOWER, UPPER, UPPER));
		if(pY) shape = Shapes.or(shape, aabb(LOWER, UPPER, LOWER, UPPER, 1D, UPPER));
		if(nY) shape = Shapes.or(shape, aabb(LOWER, 0D, LOWER, UPPER, LOWER, UPPER));
		if(pZ) shape = Shapes.or(shape, aabb(LOWER, LOWER, UPPER, UPPER, UPPER, 1D));
		if(nZ) shape = Shapes.or(shape, aabb(LOWER, LOWER, 0D, UPPER, UPPER, LOWER));
		return shape;
	}

	private static VoxelShape aabb(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Shapes.box(x1, y1, z1, x2, y2, z2);
	}

	private static boolean has(int mask, int bit) {
		return (mask & bit) != 0;
	}

	public static int mask(BlockState state) {
		int mask = 0;
		if(state.getValue(EAST)) mask |= 32;
		if(state.getValue(WEST)) mask |= 16;
		if(state.getValue(UP)) mask |= 8;
		if(state.getValue(DOWN)) mask |= 4;
		if(state.getValue(SOUTH)) mask |= 2;
		if(state.getValue(NORTH)) mask |= 1;
		return mask;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[mask(state)];
	}

	/// CONNECTIONS ///

	private static FluidType typeAt(LevelReader level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BlockEntityPipeBaseNT pipe ? pipe.getFluidType() : Fluids.NONE;
	}

	/**
	 * Whether this pipe connects to whatever is on the far side of {@code dir}.
	 *
	 * <p>The base mod's test, plus the amat rule: a plain duct is never a valid neighbour, even
	 * when it carries the same fluid and would happily say yes.
	 */
	private static boolean connectsTo(LevelReader level, BlockPos pos, Direction dir, FluidType type) {

		if(level instanceof BlockGetter getter && AmatDuctTE.isPlainDuct(getter, pos)) return false;

		return Library.canConnectFluid(level, pos, dir, type);
	}

	private static BlockState connect(BlockState state, LevelReader level, BlockPos pos) {

		FluidType type = typeAt(level, pos);

		for(Direction dir : Direction.values()) {
			state = state.setValue(BY_DIRECTION.get(dir), connectsTo(level, pos.relative(dir), dir, type));
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

		if(!(level instanceof LevelReader reader)) return state;

		FluidType type = typeAt(reader, pos);
		return state.setValue(BY_DIRECTION.get(direction), connectsTo(reader, neighborPos, direction, type));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {

		super.onPlace(state, level, pos, oldState, movedByPiston);

		if(level.isClientSide) return;

		BlockState updated = connect(state, level, pos);
		if(updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			BlockPos neighborPos, boolean movedByPiston) {

		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

		if(level.isClientSide) return;

		BlockState updated = connect(state, level, pos);
		if(updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	/** This pipe's own fluid type changed; see {@code FluidDuctBase#updateConnections}. */
	@Override
	public void updateConnections(Level level, BlockPos pos) {

		if(level.isClientSide) return;

		BlockState state = level.getBlockState(pos);
		BlockState updated = connect(state, level, pos);
		if(updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
	}

	/// LOOK OVERLAY ///

	/**
	 * Upstream also prints the network's remaining field strength here. That readout waits for
	 * the charger — see the note on {@link AmatDuctTE}.
	 */
	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {

		if(!(world.getBlockEntity(pos) instanceof AmatDuctTE duct)) return;

		List<String> text = new ArrayList<>();
		text.add("&[" + duct.getFluidType().getColor() + "&]" + duct.getFluidType().getLocalizedName());
		ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(((Block) this).getDescriptionId()), 0xffff00, 0x404000, text);
	}
}
