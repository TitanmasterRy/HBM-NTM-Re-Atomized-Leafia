package com.leafia.contents.network.fluid;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.leafia.contents.network.fluid.gauges.FluidDuctGaugeTE;
import com.leafia.contents.network.fluid.valves.FluidDuctValveTE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * How the in-line duct equipment is drawn <b>at all</b>.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/FluidDuctEquipmentRender.java}. The
 * block's {@code getRenderType} is {@code ENTITYBLOCK_ANIMATED} upstream and
 * {@link net.minecraft.world.level.block.RenderShape#ENTITYBLOCK_ANIMATED} here, so without
 * this class a valve is an invisible hole in a pipe run. Until now a static multipart model
 * stood in for it (D-1016); that stand-in is gone, and the blockstate is a particle-only model
 * of the kind D-1019 established.
 *
 * <p>The pattern is {@code LightRender}'s (D-1020): one baked model per OBJ part, tint through
 * {@link ObjUtil#renderModel}'s colour arguments, no texture binding. What this one adds is the
 * two things a light does not have — a placement orientation and a moving part.
 *
 * <h2>The orientation is upstream's angles, not this port's helper</h2>
 *
 * {@link ObjUtil#rotateForFacing} looks like the right call and is <b>wrong here</b>. Upstream
 * rotates by {@code 180 - direction.getHorizontalIndex() * 90}, which is
 * {@code SOUTH 180, WEST 90, NORTH 0, EAST 270}; the helper's mapping is the same four angles
 * shifted by half a turn. Using it would draw every valve backwards. 1.21.1's
 * {@link net.minecraft.core.Direction#get2DDataValue} is 1.12.2's {@code getHorizontalIndex}
 * unchanged — same order, same numbers — so upstream's expression ports across literally, which
 * is what this does.
 *
 * <p>The other two rotations are literal too: {@code face} tips the body up or down a quarter
 * turn, and {@code vertical} rolls it a quarter turn about Z. Both are read off the block
 * entity rather than the blockstate, which is the thing {@link FluidDuctEquipmentTE} says it
 * would revisit once a renderer existed. It still holds: a baked model rotated by blockstate
 * cannot also spin a handwheel, so the block entity has to carry the orientation for the
 * handwheel's sake regardless.
 *
 * <h2>The handwheel</h2>
 *
 * {@link FluidDuctValveTE#wheelAngle} about Z — upstream's arithmetic, moved onto the block
 * entity so a test can reach it, since a client-only class cannot be loaded by a GameTest
 * server. The redstone valve has no wheel in its model, and upstream skips the branch for it by
 * comparing models; here it is skipped by {@link LeafiaClient#valveWheel} answering null.
 *
 * <h2>The gauge</h2>
 *
 * The same pipe, tinted, the same orientation, and a body of its own; then the needle, turned about
 * Z by {@link FluidDuctGaugeTE#needleAngle} - upstream's {@code -min(fill / max x 360, 350 + rand)} -
 * where the valve turns its handwheel. The needle is a model of its own for the same reason the wheel
 * is: it moves independently of the body. See D-3005.
 */
@OnlyIn(Dist.CLIENT)
public class FluidDuctEquipmentRender implements BlockEntityRenderer<FluidDuctEquipmentTE> {

	/**
	 * Upstream's {@code setColorFromFluid}, including the 240/255 ceiling: the tint is the
	 * fluid's own colour darkened a little so a white fluid does not read as an untinted pipe.
	 */
	private static final float MAX = 240F / 255F;

	public FluidDuctEquipmentRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(FluidDuctEquipmentTE duct, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = duct.getBlockState();
		if(!(state.getBlock() instanceof FluidDuctEquipmentBase)) return;

		boolean gauge = duct instanceof FluidDuctGaugeTE;

		ModelResourceLocation pipe = gauge ? LeafiaClient.GAUGE_PIPE : LeafiaClient.valvePipe(state.getBlock());
		ModelResourceLocation body = gauge ? LeafiaClient.GAUGE_BODY : LeafiaClient.valveBody(state.getBlock());
		if(pipe == null || body == null) return;

		ModelResourceLocation wheel = gauge ? null : LeafiaClient.valveWheel(state.getBlock());

		VertexConsumer buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		matrix.translate(0.5D, 0.5D, 0.5D);

		matrix.mulPose(Axis.YP.rotationDegrees(180F - duct.direction.get2DDataValue() * 90F));
		matrix.mulPose(Axis.XP.rotationDegrees(-90F * duct.face));
		if(duct.vertical) matrix.mulPose(Axis.ZP.rotationDegrees(-90F));

		FluidType type = duct.getFluidType();
		int code = type == null ? 0xFFFFFF : type.getColor();
		float r = (code >>> 16 & 0xFF) / 255F * MAX;
		float g = (code >>> 8 & 0xFF) / 255F * MAX;
		float b = (code & 0xFF) / 255F * MAX;

		ObjUtil.renderModel(ObjUtil.getModel(pipe), matrix, buffer, packedLight, OverlayTexture.NO_OVERLAY, r, g, b);
		ObjUtil.renderModel(ObjUtil.getModel(body), matrix, buffer, packedLight, OverlayTexture.NO_OVERLAY);

		if(wheel != null && duct instanceof FluidDuctValveTE valve) {

			matrix.mulPose(Axis.ZP.rotationDegrees(valve.wheelAngle(partialTick)));

			ObjUtil.renderModel(ObjUtil.getModel(wheel), matrix, buffer, packedLight, OverlayTexture.NO_OVERLAY);
		}

		if(duct instanceof FluidDuctGaugeTE dial) {

			// Upstream's rotate(-min(fill / max x 360, 350 + rand), 0, 0, 1) before the needle.
			matrix.mulPose(Axis.ZP.rotationDegrees(-dial.needleAngle()));

			ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.GAUGE_NEEDLE), matrix, buffer, packedLight, OverlayTexture.NO_OVERLAY);
		}

		matrix.popPose();
	}
}
