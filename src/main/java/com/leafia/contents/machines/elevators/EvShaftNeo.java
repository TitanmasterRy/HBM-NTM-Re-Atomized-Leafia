package com.leafia.contents.machines.elevators;

import java.util.EnumMap;
import java.util.Map;

import com.hbm.blocks.BlockBase;
import com.leafia.dev.math.FiaBB;
import com.leafia.dev.math.FiaMatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * `elevator_shaft`: the rail an elevator car runs up. One thin strip against a wall, stacked as
 * high as the shaft goes.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvShaftNeo.java}. <b>"Neo" is
 * upstream's own word for "the new one"</b> and has nothing to do with NeoForge — the name is kept
 * because every commit here names the upstream path (standing rule 8), and renaming it would make
 * the diff unreadable for the sake of a pun.
 *
 * <h2>The first piece of the elevator campaign</h2>
 *
 * The elevators are 39 upstream files with a 63-file closure (D-1030 corrected that from 189), so
 * they are a campaign rather than a batch. This is the piece everything else attaches to and the
 * only one that stands up alone: a placeable, visible block with no dependency on the car, the
 * pulley or the floors.
 *
 * <h2>Three files it replaces, which upstream never deleted</h2>
 *
 * There is an older {@code EvShaft} — a {@code BlockDummyable} with an {@code EvShaftTE} and an
 * {@code EvShaftRender} drawing the model through a TESR. <b>{@code new EvShaft(} appears nowhere
 * in the addon</b>: only this class is registered, so that block never exists, its block entity is
 * never created and its renderer never runs. {@code WeightSpawnItem} still imports
 * {@code EvShaft} and then tests {@code instanceof EvShaftNeo}. All three are recorded as dead
 * upstream code rather than ported — see D-1031.
 *
 * <p>The reason the older one lost is visible in the two implementations: this one is a
 * <b>baked model chosen by a blockstate</b>, where the dummyable drew through a block entity every
 * frame. That is the same conclusion D-700 reached for the base mod's cables.
 */
public class EvShaftNeo extends BlockBase {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	/**
	 * Upstream's box, evaluated once per facing.
	 *
	 * <p>It is a thin strip — one sixteenth deep, a block tall, a block wide — pushed two pixels
	 * out from the block's centre and then turned to face the way the shaft was placed. Upstream
	 * builds it per call with {@link FiaMatrix}; 1.21.1 wants a {@link VoxelShape}, which cannot be
	 * rotated after the fact, so the four are built here from the same arithmetic and cached. The
	 * matrix maths is unchanged — {@code FiaBB} and {@code FiaMatrix} are ported (D-1005) and are
	 * what produce the numbers.
	 */
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

	static {
		for(Direction facing : Direction.Plane.HORIZONTAL) {

			int ang = facing.get2DDataValue() * 90;

			AABB box = new FiaBB(new FiaMatrix(new Vec3(0.5, 0, 0.5))
					.rotateY(-ang)
					.translate(0, 0, -0.5 + 2 / 16d)
					.rotateY(180), -0.5, 0, 0.5, 1, 1 / 16d).toAABB();

			SHAPES.put(facing, Shapes.create(box));
		}
	}

	public EvShaftNeo(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/**
	 * Upstream stores the facing in metadata as {@code max(getHorizontalIndex(), 0)} — the
	 * {@code max} is a guard, because it uses the six-way {@code BlockDirectional.FACING} and
	 * {@code UP}/{@code DOWN} answer −1 to a horizontal question. The property here is horizontal
	 * to begin with, so there is no such state to guard against and no metadata to store.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	/**
	 * Upstream overrides {@code addCollisionBoxToList} to add nothing at all: you walk through a
	 * shaft rail, which is what makes it possible to stand in the shaft the car runs in.
	 */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
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
