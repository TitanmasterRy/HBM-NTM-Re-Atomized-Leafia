package com.leafia.contents.network.pipe_amat;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.blocks.network.FluidDuctBase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The base every antimatter duct extends.
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/AmatDuctBase.java}, which is ten
 * lines forwarding a constructor to the base mod's {@code FluidDuctBase}. It is slightly more
 * than that here for one reason: {@link FluidDuctBase} builds a plain
 * {@link BlockEntityPipeBaseNT} and ticks it as one, and an amat duct needs its own
 * {@link AmatDuctTE}. Both overrides are that substitution and nothing else — the recursive
 * re-type, the analysis readout and the abstract {@code ductType} hook all come from the
 * parent unchanged.
 */
public abstract class AmatDuctBase extends FluidDuctBase {

	protected AmatDuctBase(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AmatDuctTE(ductType(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ductType(), (l, p, s, be) -> ((AmatDuctTE) be).updateEntity());
	}
}
