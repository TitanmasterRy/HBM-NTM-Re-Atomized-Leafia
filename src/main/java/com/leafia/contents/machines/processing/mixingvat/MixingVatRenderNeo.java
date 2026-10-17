package com.leafia.contents.machines.processing.mixingvat;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the mixing vat: the body, the frame when something solid sits over it, the turning blade, and the salt as a
 * tinted slab that rises with the fuller tank.
 *
 * <p>Upstream: {@code .../mixingvat/MixingVatRenderNeo.java}, the one it registers for the block entity and
 * (as {@code MixingVatItemRenderNeo}) for the item. The older {@code MixingVatRender}, with a different model, is
 * registered for nothing: only the Cannery's LFTR scene reads its constants, so it is left for the Cannery
 * (D-3016), and this one keeps upstream's {@code Neo} so that name stays free for it. The order, the transforms and the numbers are upstream's:
 *
 * <ol>
 * <li>translate to the block's centre, turn by the facing, then by {@code (0.5, 0, -1)} - the model's origin is not the core's;</li>
 * <li>{@code Body}; {@code Frame} <b>only if the block two above the core is a solid cube</b> (upstream's
 *     {@code isFullCube() && material != AIR});</li>
 * <li>{@code Spinner}, turned {@code -rot} degrees about the vertical through {@code (0, 0, -0.5)}, where
 *     {@code rot} is the interpolated {@code prevRot -> mixerRot} (9 degrees a tick while a batch runs);</li>
 * <li>{@code Fluid}, translucent and tinted with the vat's fluid colour, moved by {@code 1.12171 x (1 - level)} and
 *     scaled to {@code level} in height, where {@code level} is the greater of the two tanks' fill. At zero it is not drawn:
 *     a zero scale has no normal.</li>
 * </ol>
 *
 * <p>The facing table is upstream's switch, north 180, south 0, west 270, east 90, written out rather than taken from
 * {@code ObjUtil.rotateForFacing} (D-1021). The textures are the model's three materials,
 * on the block atlas: the fixed cutout sheet for the solid parts and the fixed translucent sheet for the salt, both held across
 * parts without finishing one another (D-1062).
 *
 * <p>One difference of kind: <b>"full cube" is {@code isSolidRender} here</b>. 1.12.2's {@code isFullCube} is true of the odd
 * block that is not opaque (ice), which a 1.21.1 block no longer answers separately; the common blocks - stone, ores, planks,
 * glass (no), leaves (no) - agree.
 */
@OnlyIn(Dist.CLIENT)
public class MixingVatRenderNeo implements BlockEntityRenderer<MixingVatTE> {

	/** The fluid slab's top at a full tank: upstream's {@code 1.12171}. */
	public static final double FLUID_HEIGHT = 1.12171;

	public MixingVatRenderNeo(BlockEntityRendererProvider.Context context) { }

	/** Upstream's four cases, by name rather than by {@code ForgeDirection} ordinal. */
	static float facingDegrees(Direction dir) {
		return switch(dir) {
			case NORTH -> 180F;
			case SOUTH -> 0F;
			case WEST -> 270F;
			case EAST -> 90F;
			default -> 0F;
		};
	}

	@Override
	public void render(MixingVatTE vat, float partialTick, PoseStack matrix, MultiBufferSource buffers,
			int light, int overlay) {

		BlockState state = vat.getBlockState();
		if(!(state.getBlock() instanceof MixingVatBlock) || vat.getLevel() == null) return;

		VertexConsumer solid = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(facingDegrees(state.getValue(BlockDummyable.DIR))));
		matrix.translate(0.5D, 0D, -1D);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.MIXINGVAT_BODY), matrix, solid, light, OverlayTexture.NO_OVERLAY);

		BlockState above = vat.getLevel().getBlockState(vat.getBlockPos().above(2));
		if(above.isSolidRender(vat.getLevel(), vat.getBlockPos().above(2)) && !above.isAir())
			ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.MIXINGVAT_FRAME), matrix, solid, light, OverlayTexture.NO_OVERLAY);

		float rot = vat.prevRot + (vat.mixerRot - vat.prevRot) * partialTick;
		matrix.pushPose();
		matrix.translate(0D, 0D, -0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(-rot));
		matrix.translate(0D, 0D, 0.5D);
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.MIXINGVAT_SPINNER), matrix, solid, light, OverlayTexture.NO_OVERLAY);
		matrix.popPose();

		float level = vat.fillLevel();
		if(level > 0F) {
			float[] rgb = vat.tint();
			VertexConsumer fluid = buffers.getBuffer(Sheets.translucentCullBlockSheet());
			matrix.pushPose();
			matrix.translate(0D, FLUID_HEIGHT * (1 - level), 0D);
			matrix.scale(1F, level, 1F);
			ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.MIXINGVAT_FLUID), matrix, fluid, light, OverlayTexture.NO_OVERLAY,
					rgb[0], rgb[1], rgb[2]);
			matrix.popPose();
		}

		matrix.popPose();
	}

	@Override
	public AABB getRenderBoundingBox(MixingVatTE vat) {
		return vat.getRenderBoundingBox();
	}
}
