package com.leafia.contents.machines.research.amsp.receiver;

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
 * Draws the spectrometer's receiver: the AMC base's pedestal (the research directory's copy of it) on the
 * {@code _fus} sheet, standing on the core's own level.
 *
 * <p>Upstream: {@code .../amsp/receiver/AMSPReceiverRender.java}: {@code translate(x + 0.5, y, z + 0.5)} and
 * {@code renderAll}, with no facing.
 */
@OnlyIn(Dist.CLIENT)
public class AMSPReceiverRender implements BlockEntityRenderer<AMSPReceiverTE> {

	public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
			ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/amsp_receiver"));

	public AMSPReceiverRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(AMSPReceiverTE te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		ObjUtil.renderModel(ObjUtil.getModel(MODEL), pose, buffers.getBuffer(Sheets.cutoutBlockSheet()), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	@Override
	public AABB getRenderBoundingBox(AMSPReceiverTE te) {
		return new AABB(te.getBlockPos()).inflate(4, 0, 4).expandTowards(0, 4, 0);
	}
}
