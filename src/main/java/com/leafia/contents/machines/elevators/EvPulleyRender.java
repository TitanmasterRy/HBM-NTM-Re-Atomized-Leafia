package com.leafia.contents.machines.elevators;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.leafia.contents.machines.elevators.floors.EvFloor;
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
 * The winch at the head of a shaft: a motor, its housing, the sheave and the rope over it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvPulleyRender.java}. Six named
 * objects in one OBJ, each bound to its own texture — which is the thing that does not survive.
 *
 * <h2>Six parts, six models</h2>
 *
 * Upstream calls {@code bindTexture(x); mdl.renderPart("Name")} six times over one loaded OBJ.
 * This port has no {@code renderPart}: the {@code neoforge:obj} loader bakes a whole model and
 * selecting one named object is a {@code visibility} map in the model JSON, so every
 * (part, texture) pair becomes its own model. A generator script
 * generates them — twelve for this batch — and {@link LeafiaClient} registers them, because a
 * model nothing references from a blockstate is never baked (see {@code ObjUtil.getModel}).
 * Same pattern as the fluid valve's three (D-1021), with more parts.
 *
 * <h2>The rope is not black because its texture is</h2>
 *
 * Upstream draws {@code "Rope"} with {@code disableTexture2D()} and {@code color(0, 0, 0)} —
 * untextured, flat black. There is no per-draw texture switch here, and none is needed:
 * {@link ObjUtil#renderModel}'s colour arguments multiply the sampled texel, and black times
 * anything is black. So the rope part is a model like the other five and is drawn with
 * {@code (0, 0, 0)}. Identical pixels, and it is the same trick the buffer does not need.
 *
 * <h2>The facing is upstream's table, not the helper's</h2>
 *
 * {@link EvFloor#yawOf} rather than {@link ObjUtil#rotateForFacing}. The two differ by half a
 * turn and using the helper would draw every pulley backwards — D-1021 records the same trap for
 * the fluid valve, and it is worth knowing that this port's helper and this <em>addon's</em>
 * upstream disagree as a rule rather than in one place.
 */
@OnlyIn(Dist.CLIENT)
public class EvPulleyRender implements BlockEntityRenderer<EvPulleyTE> {

	public EvPulleyRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(EvPulleyTE pulley, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = pulley.getBlockState();
		if(!(state.getBlock() instanceof EvPulley)) return;

		VertexConsumer buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(EvFloor.yawOf(state.getValue(BlockDummyable.DIR))));

		for(ModelResourceLocation part : LeafiaClient.EV_PULLEY_PARTS)
			ObjUtil.renderModel(ObjUtil.getModel(part), matrix, buffer, packedLight, OverlayTexture.NO_OVERLAY);

		// Upstream's untextured black rope; see the class javadoc.
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.EV_PULLEY_ROPE), matrix, buffer,
				packedLight, OverlayTexture.NO_OVERLAY, 0F, 0F, 0F);

		matrix.popPose();
	}
}
