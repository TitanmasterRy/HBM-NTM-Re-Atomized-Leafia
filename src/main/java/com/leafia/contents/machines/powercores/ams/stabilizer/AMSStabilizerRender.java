package com.leafia.contents.machines.powercores.ams.stabilizer;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseRender;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseTE;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.NTMRenderHelperWrapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the AMC stabilizer: the wall, turned to face the base, and the beam it throws at the ball.
 *
 * <p>Upstream: {@code .../ams/stabilizer/AMSStabilizerRender.java}. The wall is one OBJ turned
 * {@code 180 - 90} degrees and then by the facing: 90 for a stabilizer whose
 * {@code rotateShitfuck - 10} is 2, 180 for 4, 270 for 3 and 0 for 5. Its texture is the burnt-out
 * one when locked, the balefire sheet when the base it faces runs antischrabidium and balefire, and
 * the plain one otherwise.
 *
 * <p>The base is found by the same number: a stabilizer reading 2 looks six blocks to the north
 * (the mirror of the base's own check, which looks south for a 2), 3 six to the south, 4 six west
 * and 5 six east. With no base there, or when locked, there is no beam.
 *
 * <p>The beam is a four-sided prism, 4 long and 0.24 across, that leaves the wall at 2.5 along
 * its own axis and 5.5 up and fades to nothing at the far end (vertex alpha 1 to 0), in a dull
 * teal {@code (0.233, 0.511, 0.511)}, additive. With any efficiency a second, twice as wide prism
 * is added. Upstream draws both with culling off and full-bright; here each face is written from
 * both sides (D-3002).
 *
 * <p>Upstream's {@code maxSize} accumulator, which it fills in from the base's tanks and then
 * never reads, is not ported.
 */
@OnlyIn(Dist.CLIENT)
public class AMSStabilizerRender implements BlockEntityRenderer<AMSStabilizerTE> {

	public static final ModelResourceLocation MODEL = standalone("block/ams_limiter");
	public static final ModelResourceLocation MODEL_BALEFIRE = standalone("block/ams_limiter_balefire");
	public static final ModelResourceLocation MODEL_DESTROYED = standalone("block/ams_limiter_destroyed");

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
	}

	public AMSStabilizerRender(BlockEntityRendererProvider.Context context) { }

	/** Upstream's switch: the extra turn, in degrees, for a stabilizer whose {@code rotateShitfuck - 10} is {@code meta}. */
	public static float turn(int meta) {
		return switch(meta) {
			case 2 -> 90F;
			case 4 -> 180F;
			case 3 -> 270F;
			default -> 0F;
		};
	}

	/** Where the base this stabilizer faces would be, relative to it. */
	public static BlockPos baseOffset(int meta) {
		return switch(meta) {
			case 2 -> new BlockPos(0, 0, -6);
			case 3 -> new BlockPos(0, 0, 6);
			case 4 -> new BlockPos(-6, 0, 0);
			case 5 -> new BlockPos(6, 0, 0);
			default -> null;
		};
	}

	private static AMSBaseTE base(AMSStabilizerTE te, int meta) {
		BlockPos offset = baseOffset(meta);
		if(offset == null || te.getLevel() == null) return null;
		return te.getLevel().getBlockEntity(te.getBlockPos().offset(offset)) instanceof AMSBaseTE b ? b : null;
	}

	@Override
	public void render(AMSStabilizerTE te, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {

		int meta = AMSStabilizerTE.rotateShitfuck(te.facing()) - 10;
		AMSBaseTE base = base(te, meta);

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(180F));
		matrix.mulPose(Axis.YP.rotationDegrees(-90F));
		matrix.mulPose(Axis.YP.rotationDegrees(turn(meta)));

		ModelResourceLocation model;
		if(te.locked) model = MODEL_DESTROYED;
		else model = AMSBaseRender.isBalefire(base) ? MODEL_BALEFIRE : MODEL;

		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
		ObjUtil.renderModel(ObjUtil.getModel(model), matrix, consumer, light, OverlayTexture.NO_OVERLAY);

		matrix.popPose();

		if(base != null && !te.locked) drawBeam(te, matrix, buffers, meta);
	}

	private static void drawBeam(AMSStabilizerTE limiter, PoseStack matrix, MultiBufferSource buffers, int meta) {

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(180F));
		matrix.mulPose(Axis.YP.rotationDegrees(-90F));
		matrix.mulPose(Axis.YP.rotationDegrees(turn(meta)));

		matrix.mulPose(Axis.YP.rotationDegrees(-90F));

		double posX = 0;
		double posY = 0;
		double posZ = 0;
		double length = 4;
		double radius = 0.12;
		matrix.translate(2.5D, 5.5D, 0D);

		VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
		PoseStack.Pose pose = matrix.last();

		prism(buffer, pose, posX, posY, posZ, length, radius);

		if(limiter.efficiency > 0) {
			radius *= 2;
			prism(buffer, pose, posX, posY, posZ, length, radius);
		}

		matrix.popPose();
	}

	/** Upstream's four faces, each fading from alpha 1 at {@code posX} to 0 at {@code posX + length}. */
	private static void prism(VertexConsumer buffer, PoseStack.Pose pose, double posX, double posY, double posZ,
			double length, double radius) {

		float r = 0.408F - 0.175F;
		float g = 0.686F - 0.175F;
		float b = 0.686F - 0.175F;

		face(buffer, pose, r, g, b,
				posX + length, posY - radius, posZ - radius, 0F,
				posX + length, posY - radius, posZ + radius, 0F,
				posX, posY - radius, posZ + radius, 1F,
				posX, posY - radius, posZ - radius, 1F);

		face(buffer, pose, r, g, b,
				posX + length, posY + radius, posZ + radius, 0F,
				posX + length, posY + radius, posZ - radius, 0F,
				posX, posY + radius, posZ - radius, 1F,
				posX, posY + radius, posZ + radius, 1F);

		face(buffer, pose, r, g, b,
				posX + length, posY - radius, posZ - radius, 0F,
				posX + length, posY + radius, posZ - radius, 0F,
				posX, posY + radius, posZ - radius, 1F,
				posX, posY - radius, posZ - radius, 1F);

		face(buffer, pose, r, g, b,
				posX + length, posY - radius, posZ + radius, 0F,
				posX + length, posY + radius, posZ + radius, 0F,
				posX, posY + radius, posZ + radius, 1F,
				posX, posY - radius, posZ + radius, 1F);
	}

	private static void face(VertexConsumer buffer, PoseStack.Pose pose, float r, float g, float b,
			double x1, double y1, double z1, float a1, double x2, double y2, double z2, float a2,
			double x3, double y3, double z3, float a3, double x4, double y4, double z4, float a4) {

		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x1, y1, z1, r, g, b, a1);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x2, y2, z2, r, g, b, a2);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x3, y3, z3, r, g, b, a3);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x4, y4, z4, r, g, b, a4);

		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x4, y4, z4, r, g, b, a4);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x3, y3, z3, r, g, b, a3);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x2, y2, z2, r, g, b, a2);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x1, y1, z1, r, g, b, a1);
	}

	@Override
	public AABB getRenderBoundingBox(AMSStabilizerTE te) {
		return te.getRenderBoundingBox();
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
