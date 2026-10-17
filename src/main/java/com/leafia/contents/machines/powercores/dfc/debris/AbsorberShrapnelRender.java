package com.leafia.contents.machines.powercores.dfc.debris;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.powercores.dfc.debris.AbsorberShrapnelEntity.DebrisType;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws a piece of a blown-apart absorber on the absorber's own texture.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/debris/AbsorberShrapnelRender.java}: up an
 * eighth, a yaw of {@code entityId % 360}, the interpolated tumble about {@code (1, 1, 1)}, then the kind's
 * mesh from {@code models/leafia/dfc_receiver_shrapnels/} on {@code dfc_receiver_tex}. Each mesh is a baked
 * OBJ model here, registered with
 * {@code ModelEvent.RegisterAdditional}. The core's mesh carries upstream's vertex colours (black and white
 * shading on the one texture), which the generator writes as NeoForge's {@code vc} lines and which this
 * renderer keeps by reading each quad's own colour; the frame pieces have none.
 */
@OnlyIn(Dist.CLIENT)
public class AbsorberShrapnelRender extends EntityRenderer<AbsorberShrapnelEntity> {

	private static final Axis DIAGONAL = Axis.of(new Vector3f(1F, 1F, 1F).normalize());
	private static final RandomSource RANDOM = RandomSource.create();

	public AbsorberShrapnelRender(EntityRendererProvider.Context context) {
		super(context);
	}

	/** The baked model of one kind: {@code leafia:block/dfc_shrapnel_<kind>}. */
	public static ModelResourceLocation model(DebrisType type) {
		return ModelResourceLocation.standalone(LeafiaRef.rl("block/dfc_shrapnel_" + type.name().toLowerCase()));
	}

	/** Every model this renderer draws, for {@code ModelEvent.RegisterAdditional}. */
	public static List<ModelResourceLocation> all() {
		List<ModelResourceLocation> out = new ArrayList<>();
		for(DebrisType type : DebrisType.values()) out.add(model(type));
		return out;
	}

	@Override
	public void render(AbsorberShrapnelEntity debris, float yaw, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light) {
		matrix.pushPose();
		matrix.translate(0.0D, 0.125D, 0.0D);
		matrix.mulPose(Axis.YP.rotationDegrees(debris.getId() % 360)); //rotate based on entity ID to add unique randomness
		matrix.mulPose(DIAGONAL.rotationDegrees(debris.lastRot + (debris.rot - debris.lastRot) * partialTick));
		BakedModel baked = ObjUtil.getModel(model(debris.getDebrisType()));
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
		PoseStack.Pose pose = matrix.last();
		float[] shade = {1F, 1F, 1F, 1F};
		int[] lights = {light, light, light, light};
		for(var quad : baked.getQuads(null, null, RANDOM))
			buffer.putBulkData(pose, quad, shade, 1F, 1F, 1F, 1F, lights, OverlayTexture.NO_OVERLAY, true);
		for(Direction side : Direction.values())
			for(var quad : baked.getQuads(null, side, RANDOM))
				buffer.putBulkData(pose, quad, shade, 1F, 1F, 1F, 1F, lights, OverlayTexture.NO_OVERLAY, true);
		matrix.popPose();
		super.render(debris, yaw, partialTick, matrix, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(AbsorberShrapnelEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
