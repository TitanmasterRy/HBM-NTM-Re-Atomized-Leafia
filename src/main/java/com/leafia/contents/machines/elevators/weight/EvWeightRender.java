package com.leafia.contents.machines.elevators.weight;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.leafia.contents.machines.elevators.EvRope;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The counterweight on the far end of the rope.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/weight/EvWeightRender.java}. One
 * OBJ object drawn whole, plus the rope up to its pulley — which is why {@link EvRope} was
 * hoisted out of the cabin's renderer rather than this file importing all 412 lines of it.
 *
 * <h2>Where the rope attaches at the pulley end</h2>
 *
 * {@code pulleyPos} is the pulley's block position, centred, then offset <b>1.4062 blocks along
 * the pulley's own clockwise direction</b> — because the sheave the counterweight hangs from is
 * not the one the cabin hangs from, and they are just over a block and a quarter apart. That
 * offset and {@link EvPulleyRender}'s facing table have to agree or the rope leaves the machine
 * at a visibly wrong point; the shared {@code yawOf} is what keeps them together.
 *
 * <h2>One upstream oddity, preserved</h2>
 *
 * The rope's direction is computed in <b>world</b> space and then drawn inside a matrix already
 * rotated by the entity's yaw, so the rope is skewed by that yaw rather than pointing straight at
 * the pulley. It is barely visible in play — a counterweight hangs almost directly below its
 * sheave, so the horizontal component being rotated is small — and it is what upstream draws.
 * Kept under D-021; correcting it would change what a player sees.
 */
@OnlyIn(Dist.CLIENT)
public class EvWeightRender extends EntityRenderer<EvWeightEntity> {

	/** Upstream's attachment height on the weight itself. */
	private static final double ROPE_ANCHOR = 3.0625;

	/** Upstream's distance from the pulley's centre to the counterweight's sheave. */
	private static final double SHEAVE_OFFSET = 1.4062;

	public EvWeightRender(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(EvWeightEntity weight, float entityYaw, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight) {

		matrix.pushPose();
		// Upstream's rotate(entityYaw, 0, -1, 0): a turn about the NEGATIVE Y axis.
		matrix.mulPose(Axis.YP.rotationDegrees(-entityYaw));

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.EV_WEIGHT), matrix,
				buffers.getBuffer(Sheets.cutoutBlockSheet()), packedLight, OverlayTexture.NO_OVERLAY);

		if(weight.pulley != null) {

			BlockPos pos = weight.pulley.getBlockPos();
			Direction face = weight.pulley.getLevel().getBlockState(pos).getValue(BlockDummyable.DIR);
			Vec3 sheave = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
					.add(Vec3.atLowerCornerOf(face.getClockWise().getNormal()).scale(SHEAVE_OFFSET));

			Vec3 anchor = new Vec3(
					weight.xOld + (weight.getX() - weight.xOld) * partialTick,
					weight.yOld + (weight.getY() - weight.yOld) * partialTick + ROPE_ANCHOR,
					weight.zOld + (weight.getZ() - weight.zOld) * partialTick);

			matrix.pushPose();
			matrix.translate(0D, ROPE_ANCHOR, 0D);
			EvRope.render(buffers, matrix, Vec3.ZERO, sheave.subtract(anchor), packedLight);
			matrix.popPose();
		}

		matrix.popPose();
		super.render(weight, entityYaw, partialTick, matrix, buffers, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(EvWeightEntity weight) {
		return EvRope.TEXTURE;
	}
}
