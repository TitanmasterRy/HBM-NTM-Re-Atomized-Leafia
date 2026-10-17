package com.leafia.contents.network.fluid.valves;

import com.leafia.contents.network.fluid.FluidDuctEquipmentBase;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `fluid_duct_valve_mdl_rs`, the redstone fluid valve: the same cut, driven by a signal instead
 * of a click.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/valves/FluidDuctValveRS.java}.
 *
 * <p>It shares {@link FluidDuctValveTE} with the hand valve, and the only two differences are
 * here: it accepts a redstone connection on every side, and it sets {@code open} from the
 * block's power state on every neighbour change rather than from a click. There is deliberately
 * no interaction override — clicking one does nothing, which is upstream's behaviour and not an
 * omission.
 *
 * <p>The renderer upstream ships for it draws no handwheel (its model is
 * {@code valve_rs.obj}, and the animated {@code "valve"} part is skipped for this block); that
 * distinction lands with the renderer.
 */
public class FluidDuctValveRS extends FluidDuctEquipmentBase {

	public FluidDuctValveRS(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluidDuctValveTE(LeafiaBlockEntities.FLUID_DUCT_VALVE_RS.get(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, LeafiaBlockEntities.FLUID_DUCT_VALVE_RS.get(), FluidDuctValveTE::tick);
	}

	@Override
	public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return true;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			BlockPos neighborPos, boolean movedByPiston) {

		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

		if(level.isClientSide) return;

		if(level.getBlockEntity(pos) instanceof FluidDuctValveTE valve) {
			valve.setOpen(level.hasNeighborSignal(pos));
		}
	}
}
