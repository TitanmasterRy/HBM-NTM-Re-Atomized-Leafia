package com.leafia.contents.network.spk_cable;

import com.leafia.contents.network.BlockCable;
import com.leafia.contents.network.spk_cable.uninos.ISPKConnector;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * `spk_cable`, the DFC Redirection Cable.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/SPKCableBlock.java}. It draws an arm
 * at any neighbour whose block entity is an {@link ISPKConnector} - the cable itself, a provider
 * or a receiver - and nothing else, so it never visibly joins a power cable or a pipe.
 *
 * <p>Upstream's {@code addInformation} only calls {@code super}, so there is no tooltip to port.
 * Its block entity is ticked on both sides: the server half keeps the node in the nodespace, and
 * the client half works out where to draw the sparks between corners (see {@link SPKCableTE}).
 */
public class SPKCableBlock extends BlockCable {

	public SPKCableBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean connectsTo(LevelReader level, BlockPos neighborPos, Direction dir) {
		return level.getBlockEntity(neighborPos) instanceof ISPKConnector;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SPKCableTE(LeafiaBlockEntities.SPK_CABLE.get(), pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, LeafiaBlockEntities.SPK_CABLE.get(), SPKCableTE::tick);
	}
}
