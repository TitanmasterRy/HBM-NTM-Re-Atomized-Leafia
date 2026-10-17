package com.leafia.contents.debug.blackhole_test;

import com.leafia.unsorted.BlackholeRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the large black hole at the centre of {@code test_blackhole_render}.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/blackhole_test/DebugBHRender.java}: translate to the block's centre and
 * {@code BlackholeRenderer.draw(true)}. Its disks reach twelve blocks out, so the box is the whole world, as the
 * render test's is upstream.
 */
@OnlyIn(Dist.CLIENT)
public class DebugBHRender implements BlockEntityRenderer<DebugBHTE> {

	public DebugBHRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(DebugBHTE te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		BlackholeRenderer.draw(pose, buffers, true);
		pose.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(DebugBHTE te) {
		return true;
	}

	@Override
	public AABB getRenderBoundingBox(DebugBHTE te) {
		return AABB.INFINITE;
	}
}
