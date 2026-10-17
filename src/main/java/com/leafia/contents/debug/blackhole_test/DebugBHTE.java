package com.leafia.contents.debug.blackhole_test;

import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The black hole test's block entity: it holds nothing and exists so {@link DebugBHRender} has something to draw at.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/blackhole_test/DebugBHTE.java}, an empty {@code TileEntity}.
 */
public class DebugBHTE extends BlockEntity {

	public DebugBHTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.DEBUG_BLACKHOLE.get(), pos, state);
	}
}
