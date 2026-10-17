package com.leafia.contents.debug.render_test;

import com.hbm.blocks.BlockContainerBase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code test_render}: an invisible, non-occluding block whose renderer draws a mesh over it ({@link DebugRenderTestRender}).
 *
 * <p>Upstream: {@code com/leafia/contents/debug/render_test/DebugRenderTestBlock.java}: {@code INVISIBLE}, and not a
 * full, normal or opaque cube - here {@code noOcclusion()} on its properties and the render shape below.
 */
public class DebugRenderTestBlock extends BlockContainerBase {

	public DebugRenderTestBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DebugRenderTestTE(pos, state);
	}
}
