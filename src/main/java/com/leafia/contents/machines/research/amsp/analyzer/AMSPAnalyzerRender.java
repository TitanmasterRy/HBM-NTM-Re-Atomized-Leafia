package com.leafia.contents.machines.research.amsp.analyzer;

import com.hbm.render.util.ObjUtil;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the spectrometer's analyzer: the AMC emitter's tower (the research directory's copy of it) on the
 * {@code _fus} sheet, hung six blocks below the core.
 *
 * <p>Upstream: {@code .../amsp/analyzer/AMSPAnalyzerRender.java}: {@code translate(x + 0.5, y + 1 - 7, z + 0.5)}
 * and {@code renderAll}, with no facing. Baked once against the block atlas (every texture coordinate is
 * inside 0..1, asserted by the generator) as the same model the block's item uses.
 */
@OnlyIn(Dist.CLIENT)
public class AMSPAnalyzerRender implements BlockEntityRenderer<AMSPAnalyzerTE> {

	public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/amsp_analyzer"));

	public AMSPAnalyzerRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(AMSPAnalyzerTE te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.translate(0.5, 1 - 7, 0.5);
		ObjUtil.renderModel(ObjUtil.getModel(MODEL), pose, buffers.getBuffer(Sheets.cutoutBlockSheet()), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** The tower hangs seven blocks below the core, nine wide at its top. */
	@Override
	public AABB getRenderBoundingBox(AMSPAnalyzerTE te) {
		return new AABB(te.getBlockPos()).inflate(4, 0, 4).expandTowards(0, -7, 0);
	}
}
