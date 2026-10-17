package com.leafia.contents.debug.blackhole_test;

import com.hbm.blocks.BlockContainerBase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code test_blackhole_render}: an invisible block whose renderer draws a black hole over it ({@link DebugBHRender}).
 *
 * <p>Upstream: {@code com/leafia/contents/debug/blackhole_test/DebugBHBlock.java}, an {@code AddonBlockBase} with an
 * {@code INVISIBLE} render type and an empty tile entity.
 */
public class DebugBHBlock extends BlockContainerBase {

	public DebugBHBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DebugBHTE(pos, state);
	}
}
