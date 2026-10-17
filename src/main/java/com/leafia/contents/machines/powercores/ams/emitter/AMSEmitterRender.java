package com.leafia.contents.machines.powercores.ams.emitter;

import java.util.Random;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseRender;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseTE;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.NTMRenderHelperWrapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 * Draws the AMC emitter: the tower, and while a base stands nine blocks below it, the beam it fires.
 *
 * <p>Upstream: {@code .../ams/emitter/AMSEmitterRender.java}. The tower is one OBJ turned half a
 * turn and on one of three textures: the burnt-out model once it is locked, the balefire sheet
 * when the base below is running antischrabidium against balefire, and the plain one otherwise.
 *
 * <p>The beam runs from the tower's foot down seven blocks, centred on the column, and is only
 * drawn when there is a base nine below and the tower is not locked. It is always two thin yellow
 * rods ({@code 0.04} and {@code 0.08} across, full length); when the emitter has any efficiency
 * there is also an orange bolt, seven one-block steps whose ends jump to a random place within half
 * a block each frame, three layers thick ({@code 0.04} x layer). It is additive and the faces are
 * drawn from both sides, since upstream switched culling off (D-3002).
 */
@OnlyIn(Dist.CLIENT)
public class AMSEmitterRender implements BlockEntityRenderer<AMSEmitterTE> {

	public static final ModelResourceLocation MODEL = standalone("block/ams_emitter");
	public static final ModelResourceLocation MODEL_BALEFIRE = standalone("block/ams_emitter_balefire");
	public static final ModelResourceLocation MODEL_DESTROYED = standalone("block/ams_emitter_destroyed");

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
	}

	private final Random rand = new Random();

	public AMSEmitterRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(AMSEmitterTE te, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {

		AMSBaseTE base = base(te);

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(180F));

		ModelResourceLocation model;
		if(te.locked) model = MODEL_DESTROYED;
		else model = AMSBaseRender.isBalefire(base) ? MODEL_BALEFIRE : MODEL;

		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
		ObjUtil.renderModel(ObjUtil.getModel(model), matrix, consumer, light, OverlayTexture.NO_OVERLAY);

		matrix.popPose();

		if(base != null && !te.locked) drawBeam(te, matrix, buffers);
	}

	/** The base nine blocks below, if there is one. */
	private static AMSBaseTE base(AMSEmitterTE te) {
		return te.getLevel() != null && te.getLevel().getBlockEntity(te.getBlockPos().offset(0, -9, 0)) instanceof AMSBaseTE b ? b : null;
	}

	private void drawBeam(AMSEmitterTE emitter, PoseStack matrix, MultiBufferSource buffers) {

		float radius = 0.04F;
		int distance = 1;
		int layers = 3;

		matrix.pushPose();
		matrix.translate(0.5D, -7D, 0.5D);

		VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
		PoseStack.Pose pose = matrix.last();

		if(emitter.efficiency > 0) {

			double lastPosX = 0;
			double lastPosZ = 0;

			for(int i = 7; i > 0; i -= distance) {

				double posX = rand.nextDouble() - 0.5;
				double posZ = rand.nextDouble() - 0.5;

				for(int j = 1; j <= layers; j++) {

					quad(buffer, pose, 1, 0.5F, 0,
							lastPosX + (radius * j), i, lastPosZ + (radius * j),
							lastPosX + (radius * j), i, lastPosZ - (radius * j),
							posX + (radius * j), i - distance, posZ - (radius * j),
							posX + (radius * j), i - distance, posZ + (radius * j));

					quad(buffer, pose, 1, 0.5F, 0,
							lastPosX - (radius * j), i, lastPosZ + (radius * j),
							lastPosX - (radius * j), i, lastPosZ - (radius * j),
							posX - (radius * j), i - distance, posZ - (radius * j),
							posX - (radius * j), i - distance, posZ + (radius * j));

					quad(buffer, pose, 1, 0.5F, 0,
							lastPosX + (radius * j), i, lastPosZ + (radius * j),
							lastPosX - (radius * j), i, lastPosZ + (radius * j),
							posX - (radius * j), i - distance, posZ + (radius * j),
							posX + (radius * j), i - distance, posZ + (radius * j));

					quad(buffer, pose, 1, 0.5F, 0,
							lastPosX + (radius * j), i, lastPosZ - (radius * j),
							lastPosX - (radius * j), i, lastPosZ - (radius * j),
							posX - (radius * j), i - distance, posZ - (radius * j),
							posX + (radius * j), i - distance, posZ - (radius * j));
				}

				lastPosX = posX;
				lastPosZ = posZ;
			}
		}

		for(int j = 1; j <= 2; j++) {

			quad(buffer, pose, 1, 1, 0,
					0 + (radius * j), 7, 0 + (radius * j),
					0 + (radius * j), 7, 0 - (radius * j),
					0 + (radius * j), 0, 0 - (radius * j),
					0 + (radius * j), 0, 0 + (radius * j));

			quad(buffer, pose, 1, 1, 0,
					0 - (radius * j), 7, 0 + (radius * j),
					0 - (radius * j), 7, 0 - (radius * j),
					0 - (radius * j), 0, 0 - (radius * j),
					0 - (radius * j), 0, 0 + (radius * j));

			quad(buffer, pose, 1, 1, 0,
					0 + (radius * j), 7, 0 + (radius * j),
					0 - (radius * j), 7, 0 + (radius * j),
					0 - (radius * j), 0, 0 + (radius * j),
					0 + (radius * j), 0, 0 + (radius * j));

			quad(buffer, pose, 1, 1, 0,
					0 + (radius * j), 7, 0 - (radius * j),
					0 - (radius * j), 7, 0 - (radius * j),
					0 - (radius * j), 0, 0 - (radius * j),
					0 + (radius * j), 0, 0 - (radius * j));
		}

		matrix.popPose();
	}

	/**
	 * One quad in one colour, written twice, once from each side: upstream drew with culling off, and
	 * the render type here has it on.
	 */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float r, float g, float b,
			double x1, double y1, double z1, double x2, double y2, double z2,
			double x3, double y3, double z3, double x4, double y4, double z4) {

		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x1, y1, z1, r, g, b, 1F);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x2, y2, z2, r, g, b, 1F);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x3, y3, z3, r, g, b, 1F);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x4, y4, z4, r, g, b, 1F);

		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x4, y4, z4, r, g, b, 1F);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x3, y3, z3, r, g, b, 1F);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x2, y2, z2, r, g, b, 1F);
		NTMRenderHelperWrapper.addVertexColor(buffer, pose, x1, y1, z1, r, g, b, 1F);
	}

	@Override
	public AABB getRenderBoundingBox(AMSEmitterTE te) {
		return te.getRenderBoundingBox();
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
