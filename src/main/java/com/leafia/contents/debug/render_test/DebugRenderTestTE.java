package com.leafia.contents.debug.render_test;

import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The render test's block entity: nothing but a place for {@link DebugRenderTestRender} to draw.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/render_test/DebugRenderTestTE.java}, whose one override is an infinite
 * render box - which is the renderer's to answer in 1.21.1 ({@code DebugRenderTestRender.getRenderBoundingBox}).
 */
public class DebugRenderTestTE extends BlockEntity {

	public DebugRenderTestTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.DEBUG_RENDER_TEST.get(), pos, state);
	}
}
