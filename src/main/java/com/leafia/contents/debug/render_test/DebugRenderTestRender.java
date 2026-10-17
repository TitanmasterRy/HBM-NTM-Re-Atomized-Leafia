package com.leafia.contents.debug.render_test;

import java.io.IOException;
import java.io.InputStream;

import com.leafia.contents.machines.powercores.dfc.render.DFCObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the small crimson door's whole mesh on the large crimson door's texture, standing on {@code test_render}.
 *
 * <p>Upstream: {@code com/leafia/contents/debug/render_test/DebugRenderTestRender.java}: translate to the block's
 * bottom centre, culling on, smooth shading, bind {@code crimdoorlarge/texture.png} and {@code renderAll()} of
 * {@code crimdoorsmall.obj} - a renderer test bench, not a door. Both files came over with the doors (D-3005):
 * {@code models/blocks/crimdoorsmall.obj} and {@code textures/block/crimdoor.png}, which is the large door's texture
 * byte for byte. Drawn as {@code entityCutout} (culled) at the block's light.
 */
@OnlyIn(Dist.CLIENT)
public class DebugRenderTestRender implements BlockEntityRenderer<DebugRenderTestTE> {

	public static final String OBJ = "/assets/leafia/models/blocks/crimdoorsmall.obj";
	public static final ResourceLocation TEX = LeafiaRef.rl("textures/block/crimdoor.png");

	private static Mesh vao;

	public DebugRenderTestRender(BlockEntityRendererProvider.Context context) { }

	private static synchronized Mesh vao() {
		if(vao == null) {
			try(InputStream in = DebugRenderTestRender.class.getResourceAsStream(OBJ)) {
				if(in == null) throw new IllegalStateException("missing " + OBJ);
				vao = DFCObj.whole(in);
			} catch(IOException e) {
				throw new IllegalStateException("could not read " + OBJ, e);
			}
		}
		return vao;
	}

	@Override
	public void render(DebugRenderTestTE te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEX));
		PoseStack.Pose last = pose.last();
		float[] d = vao().data();
		for(int i = 0; i < d.length; i += 8) {
			consumer.addVertex(last, d[i], d[i + 1], d[i + 2])
					.setColor(1F, 1F, 1F, 1F)
					.setUv(d[i + 3], d[i + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(last, d[i + 5], d[i + 6], d[i + 7]);
		}
		pose.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(DebugRenderTestTE te) {
		return true;
	}

	/** Upstream's {@code INFINITE_EXTENT_AABB}. */
	@Override
	public AABB getRenderBoundingBox(DebugRenderTestTE te) {
		return AABB.INFINITE;
	}
}
