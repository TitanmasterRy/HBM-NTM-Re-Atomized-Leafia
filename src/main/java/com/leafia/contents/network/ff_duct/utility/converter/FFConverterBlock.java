package com.leafia.contents.network.ff_duct.utility.converter;

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
 * {@code ff_converter}, the Special Pipe Adapter. Upstream: {@code .../utility/converter/FFConverterBlock.java},
 * whose whole body is {@code createNewTileEntity}. See {@link FFConverterTE}.
 */
public class FFConverterBlock extends FFDuctUtilityBase {

	public FFConverterBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FFConverterTE(LeafiaBlockEntities.FF_CONVERTER.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return serverTicker(level, type, LeafiaBlockEntities.FF_CONVERTER.get());
	}
}
