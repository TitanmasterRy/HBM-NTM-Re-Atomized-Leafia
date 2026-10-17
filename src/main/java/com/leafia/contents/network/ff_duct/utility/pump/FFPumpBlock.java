package com.leafia.contents.network.ff_duct.utility.pump;

import javax.annotation.Nullable;

import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code ff_pump}, the Special Pipe Pump. Upstream: {@code .../utility/pump/FFPumpBlock.java}, whose whole
 * body is {@code createNewTileEntity}. See {@link FFPumpTE} for what it does and {@link FFDuctUtilityBase} for
 * what it shares.
 */
public class FFPumpBlock extends FFDuctUtilityBase {

	public FFPumpBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FFPumpTE(LeafiaBlockEntities.FF_PUMP.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return serverTicker(level, type, LeafiaBlockEntities.FF_PUMP.get());
	}
}
