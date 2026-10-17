package com.leafia.contents.machines.heat.rtheater;

import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the RT heater: the base mod's oil-burner OBJ under the addon's {@code radiothermal} texture.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/heat/rtheater/HeaterRTGRender.java} - translate to the
 * block's middle, bind {@code leafia:textures/models/radiothermal.png}, {@code heater_oilburner.renderAll()},
 * no rotation. Here the OBJ and texture are a baked model, {@code leafia:block/heater_rt}, which
 * a generator script writes over the base mod's own {@code oilburner.obj}, so the shape is
 * the base mod's file and only the texture is the addon's; it is drawn as the base mod's
 * {@code RenderOilburner} draws its own. Upstream's nested item renderer is that model's item JSON.
 *
 * <p>{@code isGlobalRenderer} is {@link #shouldRenderOffScreen}; upstream's tile entity's render box and
 * 256-block view distance are {@link #getRenderBoundingBox} and {@link #getViewDistance}, as D-319 moved the
 * oil burner's.
 */
@OnlyIn(Dist.CLIENT)
public class HeaterRTGRender implements BlockEntityRenderer<HeaterRTGTE> {

	public HeaterRTGRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(HeaterRTGTE heater, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int light, int overlay) {

		matrix.pushPose();
		matrix.translate(0.5D, 0.0D, 0.5D);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.HEATER_RT), matrix,
				buffers.getBuffer(Sheets.cutoutBlockSheet()), light, OverlayTexture.NO_OVERLAY);

		matrix.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(HeaterRTGTE heater) {
		return true;
	}

	@Override
	public AABB getRenderBoundingBox(HeaterRTGTE heater) {
		BlockPos pos = heater.getBlockPos();
		return new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
