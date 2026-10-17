package com.leafia.contents.machines.misc.modular_turbine.flywheel;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The flywheels' shared base: their block entity, and a round collision shape.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/flywheel/MTFlywheelBase.java}.
 *
 * <h2>Round, as far as walking into it goes</h2>
 *
 * A flywheel's multiblock is a square of blocks, and upstream gives collision only to the ones
 * whose centre lies within {@code size / 2 - 0.333} of the shaft (measured in the plane of the
 * disc), so the corners can be walked through. Upstream's {@code addCollisionBoxToList} adds the
 * box or not; 1.21.1 asks for a shape, so {@link #getCollisionShape} answers the full block or
 * nothing. The outline when you look at one is still square — upstream only changed collision.
 *
 * <p>Upstream's {@code isFullCube() = false} is {@code noOcclusion()} on the registration.
 */
public abstract class MTFlywheelBase extends ModularTurbineBlockBase {

	public MTFlywheelBase(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new MTFlywheelTE(LeafiaBlockEntities.MODULAR_TURBINE_FLYWHEEL.get(), pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.MODULAR_TURBINE_PROXY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}

	/** Both sides, as upstream's {@code ITickable}: the client moves its own player, the server everything. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.MODULAR_TURBINE_FLYWHEEL.get(), MTFlywheelTE::tick);
	}

	/** Whether the block at {@code pos} is inside the disc. Upstream's distance test, "scary" comment and all. */
	public boolean isInsideDisc(BlockGetter world, BlockPos pos) {
		BlockPos corePos = findCore(world, pos);
		if(corePos == null) return false;
		Direction facing = world.getBlockState(corePos).getValue(BlockDummyable.DIR).getOpposite();
		Vec3 shaft = Vec3.atCenterOf(corePos.above(shaftHeight()));
		Vec3 myPos = Vec3.atCenterOf(pos);
		if(facing.getAxis() == Axis.X)
			myPos = new Vec3(shaft.x, myPos.y, myPos.z);
		else if(facing.getAxis() == Axis.Z)
			myPos = new Vec3(myPos.x, myPos.y, shaft.z);
		return shaft.distanceTo(myPos) <= size() / 2d - 0.333; // scary
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return isInsideDisc(world, pos) ? super.getCollisionShape(state, world, pos, context) : Shapes.empty();
	}
}
