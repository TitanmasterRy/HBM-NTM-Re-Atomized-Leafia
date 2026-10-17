package com.leafia.contents.machines.powercores.ams.base;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.particle.LeafiaParticleSphere;
import com.leafia.client.particle.SphereMesh;
import com.leafia.lib.LeafiaRef;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the AMC base: the pedestal, and above it the ball of light that grows with the fuel.
 *
 * <p>Upstream: {@code .../ams/base/AMSBaseRender.java}.
 *
 * <ul>
 * <li><b>The pedestal</b> is one OBJ turned half a turn, on {@code ams_base.png} - or on the
 *     balefire sheet when one fuel tank holds antischrabidium and the other balefire. Both
 *     textures are model JSONs here ({@link #MODEL}, {@link #MODEL_BALEFIRE}), registered in
 *     {@code LeafiaClient}, because a baked quad draws from the block atlas.</li>
 * <li><b>The ball</b> is drawn when the core has a colour, which is when it is generating. Centred
 *     5.5 blocks above the core, at {@code 0.5 + (fuelA / capacity + fuelB / capacity) x 2.25}
 *     blocks across, it is a black sphere at 0.45 times that size and then six shells of the base
 *     mod's icosphere at 0.5, 0.6, 0.7, 0.8, 0.9 and 1.0 times it, in the core's colour, added
 *     together and full-bright, spinning in alternate directions at three degrees every tenth of
 *     a second.</li>
 * </ul>
 *
 * <p><b>The black sphere is a core inside the shells (D-3076).</b> Upstream's comment says it used to
 * be {@code sphere_iuv} - the same icosphere with its faces turned inward - and that its author did
 * not know what that was: with the faces turned in, the sphere is a dark backdrop behind the glowing
 * shells. Turned out, as upstream drew it, it is the largest thing drawn and the depth test hides
 * every shell behind it, so the core read as a plain black ball (D-3002). It is drawn here at
 * {@link #BACKDROP} of the ball, inside the smallest shell, which is the backdrop's effect from every angle
 * without a render type that culls.
 *
 * <p>The spin is upstream's {@code (int) ((nanoTime / 100000000) % 360) * 3}: whole degrees,
 * ten times a second, and so a visible step rather than a smooth turn.
 */
@OnlyIn(Dist.CLIENT)
public class AMSBaseRender implements BlockEntityRenderer<AMSBaseTE> {

	/** The black sphere's size against the ball: under the smallest shell's 0.5, so every shell is drawn in front of it. */
	public static final float BACKDROP = 0.45F;

	public static final ModelResourceLocation MODEL = standalone("block/ams_base");
	public static final ModelResourceLocation MODEL_BALEFIRE = standalone("block/ams_base_balefire");

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
	}

	public AMSBaseRender(BlockEntityRendererProvider.Context context) { }

	/** Antischrabidium and balefire, in either order: upstream's test for the green-white texture, shared by all three. */
	public static boolean isBalefire(AMSBaseTE base) {
		if(base == null) return false;
		return (base.tanks[2].getTankType() == Fluids.ASCHRAB && base.tanks[3].getTankType() == Fluids.BALEFIRE)
				|| (base.tanks[2].getTankType() == Fluids.BALEFIRE && base.tanks[3].getTankType() == Fluids.ASCHRAB);
	}

	/** {@code 0.5 + (fillA / capA + fillB / capB) x 2.25}: the ball's size, before the black sphere's 1.1. */
	public static double ballScale(AMSBaseTE base) {
		double maxSize = 5;
		double minSize = 0.5;
		double scale = minSize;
		scale += ((((double) base.tanks[2].getFill()) / ((double) base.tanks[2].getMaxFill()))
				+ (((double) base.tanks[3].getFill()) / ((double) base.tanks[3].getMaxFill()))) * ((maxSize - minSize) / 2);
		return scale;
	}

	@Override
	public void render(AMSBaseTE base, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(180F));

		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
		ObjUtil.renderModel(ObjUtil.getModel(isBalefire(base) ? MODEL_BALEFIRE : MODEL), matrix, consumer,
				light, OverlayTexture.NO_OVERLAY);

		matrix.popPose();

		if(base.color > -1) drawBall(base, matrix, buffers);
	}

	private static void drawBall(AMSBaseTE base, PoseStack matrix, MultiBufferSource buffers) {

		int rot = (int) ((System.nanoTime() / 100000000) % 360) * 3;
		float scale = (float) ballScale(base);
		int full = LightTexture.FULL_BRIGHT;

		// The black sphere: opaque, so drawn first.
		matrix.pushPose();
		matrix.translate(0.5D, 5.5D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(180F));
		matrix.scale(scale, scale, scale);
		matrix.mulPose(Axis.YP.rotationDegrees(rot));
		matrix.scale(BACKDROP, BACKDROP, BACKDROP);
		sphere(buffers.getBuffer(RenderType.leash()), matrix.last(), 0F, 0F, 0F, 1F, full);
		matrix.popPose();

		float r = ((base.color & 0xFF0000) >> 16) / 255F;
		float g = ((base.color & 0x00FF00) >> 8) / 255F;
		float b = ((base.color & 0x0000FF) >> 0) / 255F;

		// The shells: additive, so one consumer for all six.
		VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

		matrix.pushPose();
		matrix.translate(0.5D, 5.5D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(180F));
		matrix.scale(scale, scale, scale);

		matrix.mulPose(Axis.YP.rotationDegrees(rot));
		shell(glow, matrix, 0.5F, r, g, b, full);

		matrix.mulPose(Axis.YP.rotationDegrees(-rot * 2));
		shell(glow, matrix, 0.6F, r, g, b, full);

		matrix.mulPose(Axis.YP.rotationDegrees(rot * 2));
		shell(glow, matrix, 0.7F, r, g, b, full);

		matrix.mulPose(Axis.YP.rotationDegrees(-rot * 2));
		shell(glow, matrix, 0.8F, r, g, b, full);

		matrix.mulPose(Axis.YP.rotationDegrees(rot * 2));
		shell(glow, matrix, 0.9F, r, g, b, full);

		matrix.mulPose(Axis.YP.rotationDegrees(-rot * 2));
		shell(glow, matrix, 1F, r, g, b, full);

		matrix.popPose();
	}

	private static void shell(VertexConsumer buffer, PoseStack matrix, float size, float r, float g, float b, int light) {
		matrix.pushPose();
		matrix.scale(size, size, size);
		sphere(buffer, matrix.last(), r, g, b, 1F, light);
		matrix.popPose();
	}

	/** The icosphere, each triangle as a quad with its last corner repeated, through the pose. */
	private static void sphere(VertexConsumer buffer, PoseStack.Pose pose, float r, float g, float b, float a, int light) {

		SphereMesh mesh = LeafiaParticleSphere.mesh();
		float[] p = mesh.positions;
		int[] t = mesh.triangles;

		for(int i = 0; i < t.length; i += 3) {
			vertex(buffer, pose, p, t[i], r, g, b, a, light);
			vertex(buffer, pose, p, t[i + 1], r, g, b, a, light);
			vertex(buffer, pose, p, t[i + 2], r, g, b, a, light);
			vertex(buffer, pose, p, t[i + 2], r, g, b, a, light);
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] p, int index,
			float r, float g, float b, float a, int light) {
		buffer.addVertex(pose, p[index * 3], p[index * 3 + 1], p[index * 3 + 2])
				.setColor(r, g, b, a)
				.setLight(light);
	}

	@Override
	public AABB getRenderBoundingBox(AMSBaseTE base) {
		return base.getRenderBoundingBox();
	}

	/** Upstream's {@code getMaxRenderDistanceSquared} of 65,536 is 256 blocks. */
	@Override
	public int getViewDistance() {
		return 256;
	}
}
