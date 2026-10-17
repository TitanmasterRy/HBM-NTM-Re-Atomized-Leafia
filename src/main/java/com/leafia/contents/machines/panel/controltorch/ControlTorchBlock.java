package com.leafia.contents.machines.panel.controltorch;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.leafia.contents.LeafiaBlocks;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The control panel torch (CCP-INSTRUMENTS-2, D-3072): a redstone torch a panel switches. Adapted from Leafia's
 * {@code ControlTorchBlock}, a {@code BlockTorch} whose lit and unlit forms are two blocks, {@code control_torch} (lit, light 7, in no
 * creative tab) and {@code control_torch_unlit} (in the block tab); either one is picked as, and drops, the unlit one. A panel sets
 * it with the {@code torch_set_state} event ({@link ControlTorchTE}), which swaps the block for the other form <i>keeping its facing</i>.
 *
 * <p><b>One block for the standing and the wall torch.</b> 1.12's {@code BlockTorch} was one block with a {@code facing} of {@code up}
 * or a horizontal direction (the way the torch leans, away from the wall it is on); 1.21.1's is two, so this keeps Leafia's one block
 * and his blockstate: {@code facing} is {@code up} for a standing torch and a horizontal direction for a wall torch, attached to the
 * block behind it. Placement is {@code BlockTorch}'s: the clicked face if it will hold the torch, else the block below, else the first
 * horizontal side that will.
 *
 * <p>The lit torch gives 15 to every side but the one it leans away from (and, as the vanilla torch does, powers a block below it
 * outright), tells its neighbours when it appears or goes, and sprinkles redstone dust. It is instant to break, has no collision box
 * and, with no tool required, is the 1.12 {@code Material.CIRCUITS} of its upstream.
 */
public class ControlTorchBlock extends Block implements EntityBlock {

	public static final DirectionProperty FACING = DirectionProperty.create("facing", d -> d != Direction.DOWN);

	/** Vanilla's standing torch box, which is {@code BlockTorch.STANDING_AABB} (0.4 to 0.6 across, 0 to 0.6 up) rounded to pixels. */
	public static final VoxelShape STANDING = Block.box(6.0, 0.0, 6.0, 10.0, 10.0, 10.0);

	/** Vanilla's wall torch boxes, by the way the torch faces. */
	private static final Map<Direction, VoxelShape> WALL = Map.of(
			Direction.NORTH, Block.box(5.5, 3.0, 11.0, 10.5, 13.0, 16.0),
			Direction.SOUTH, Block.box(5.5, 3.0, 0.0, 10.5, 13.0, 5.0),
			Direction.WEST, Block.box(11.0, 3.0, 5.5, 16.0, 13.0, 10.5),
			Direction.EAST, Block.box(0.0, 3.0, 5.5, 5.0, 13.0, 10.5));

	private final boolean isOn;

	public ControlTorchBlock(Properties properties, boolean isOn) {
		super(properties);
		this.isOn = isOn;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}

	public boolean isOn() {
		return isOn;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Upstream's {@code setTranslationKey("control_torch")} on both: one name, "Control Panel Torch". */
	@Override
	public String getDescriptionId() {
		return "block.leafia.control_torch";
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		return facing == Direction.UP ? STANDING : WALL.get(facing);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	// ------------------------------------------------------------------ placement

	/** Whether a torch with this facing has something to hold it: the top of the block below, or the side of the block behind. */
	public static boolean canHold(LevelReader level, BlockPos pos, Direction facing) {
		if(facing == Direction.UP) {
			BlockPos below = pos.below();
			return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP, net.minecraft.world.level.block.SupportType.CENTER);
		}
		BlockPos behind = pos.relative(facing.getOpposite());
		return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return canHold(level, pos, state.getValue(FACING));
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction clicked = context.getClickedFace();
		if(clicked != Direction.DOWN && canHold(level, pos, clicked))
			return defaultBlockState().setValue(FACING, clicked);
		for(Direction side : Direction.Plane.HORIZONTAL) {
			if(canHold(level, pos, side))
				return defaultBlockState().setValue(FACING, side);
		}
		return canHold(level, pos, Direction.UP) ? defaultBlockState().setValue(FACING, Direction.UP) : null;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		Direction held = state.getValue(FACING) == Direction.UP ? Direction.DOWN : state.getValue(FACING).getOpposite();
		if(direction == held && !canSurvive(state, level, pos))
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		Direction facing = state.getValue(FACING);
		return facing == Direction.UP ? state : state.setValue(FACING, rotation.rotate(facing));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		Direction facing = state.getValue(FACING);
		return facing == Direction.UP ? state : state.rotate(mirror.getRotation(facing));
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return new ItemStack(LeafiaBlocks.control_torch_unlit.get());
	}

	// ------------------------------------------------------------------ redstone

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	/** Upstream's {@code isOn && FACING != side ? 15 : 0}. */
	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return isOn && state.getValue(FACING) != direction ? 15 : 0;
	}

	/** Upstream's {@code side == DOWN ? weak power : 0}. */
	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return direction == Direction.DOWN ? state.getSignal(level, pos, direction) : 0;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if(isOn)
			notifyAround(level, pos);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(isOn && !movedByPiston)
			notifyAround(level, pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	private void notifyAround(Level level, BlockPos pos) {
		for(Direction side : Direction.values())
			level.updateNeighborsAt(pos.relative(side), this);
	}

	/** Upstream's {@code randomDisplayTick}: a speck of redstone dust over the flame, drawn toward the wall on a wall torch. */
	@Override
	@OnlyIn(Dist.CLIENT)
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if(!isOn)
			return;
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
		double y = pos.getY() + 0.7 + (random.nextDouble() - 0.5) * 0.2;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
		Direction facing = state.getValue(FACING);
		if(facing.getAxis().isHorizontal()) {
			Direction toWall = facing.getOpposite();
			x += 0.27 * toWall.getStepX();
			y += 0.22;
			z += 0.27 * toWall.getStepZ();
		}
		level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0, 0);
	}

	// ------------------------------------------------------------------ the block entity

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ControlTorchTE(LeafiaBlockEntities.CONTROL_TORCH.get(), pos, state);
	}
}
