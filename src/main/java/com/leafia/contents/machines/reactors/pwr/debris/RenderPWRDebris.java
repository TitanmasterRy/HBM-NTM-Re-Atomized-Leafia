package com.leafia.contents.machines.reactors.pwr.debris;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.RenderPWRMeshedWreck;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity.DebrisType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a PWR debris piece in the textures of the block it came from.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/debris/RenderPWRDebris.java}. The transform
 * is upstream's (up an eighth, a yaw of {@code entityId % 360}, then the interpolated tumble about
 * {@code (1, 1, 1)}, as the base mod's RBMK debris). Blank, concrete, shrapnel and graphite pieces are one
 * mesh on the block's particle texture ({@code drawAll}); channels and control rods draw {@code SIDE} on
 * the particle texture and {@code TOP} on the texture of the block's top ({@code draw}). The graphite chip
 * is the base mod's own {@code deb_graphite} mesh, upstream's {@code ResourceManager.deb_graphite}.
 *
 * <p>Each (mesh, object) is a baked model drawn retextured onto the block's texture file, as
 * {@link RenderPWRMeshedWreck} does (the class note there). Culling stays on, as upstream left it.
 * {@link #drawModel} is shared with the items' renderer, {@link PWRDebrisItemRender}.
 */
@OnlyIn(Dist.CLIENT)
public class RenderPWRDebris extends EntityRenderer<PWRDebrisEntity> {

	private static final Axis DIAGONAL = Axis.of(new Vector3f(1F, 1F, 1F).normalize());

	public RenderPWRDebris(EntityRendererProvider.Context context) {
		super(context);
	}

	/** The baked model of one debris object: {@code leafia:block/pwrdebris_<mesh>_<object>}. */
	public static ModelResourceLocation part(String mesh, String object) {
		return RenderPWRMeshedWreck.part("pwrdebris", mesh, object);
	}

	/** The graphite chip, on the base mod's mesh. */
	public static final ModelResourceLocation GRAPHITE = ModelResourceLocation.standalone(com.leafia.lib.LeafiaRef.rl("block/pwrdebris_graphite"));

	/** Every model this renderer draws, for {@code ModelEvent.RegisterAdditional}. */
	public static List<ModelResourceLocation> all() {
		List<ModelResourceLocation> out = new ArrayList<>();
		for(String[] pair : PWRDebrisEntity.MESH_OBJECTS) out.add(part(pair[0], pair[1]));
		out.add(GRAPHITE);
		return out;
	}

	@Override
	public void render(PWRDebrisEntity debris, float yaw, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light) {
		matrix.pushPose();
		matrix.translate(0.0D, 0.125D, 0.0D);
		matrix.mulPose(Axis.YP.rotationDegrees(debris.getId() % 360)); //rotate based on entity ID to add unique randomness
		matrix.mulPose(DIAGONAL.rotationDegrees(debris.lastRot + (debris.rot - debris.lastRot) * partialTick));
		drawModel(debris.getDebrisType(), debris.getBlock(), matrix, buffers, light);
		matrix.popPose();
		super.render(debris, yaw, partialTick, matrix, buffers, light);
	}

	/** Upstream's {@code drawModel}. */
	public static void drawModel(DebrisType type, BlockState display, PoseStack matrix, MultiBufferSource buffers, int light) {
		BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(display);
		switch(type) {
			case BLANK -> drawAll(part("blank_unwrap", "Plane.001"), display, baked, matrix, buffers, light);
			case CONCRETE -> drawAll(part("concrete_unwrap", "Plane"), display, baked, matrix, buffers, light);
			case GRAPHITE -> drawAll(GRAPHITE, display, baked, matrix, buffers, light);
			case SHRAPNEL -> drawAll(part("shrapnel", "Cube"), display, baked, matrix, buffers, light);
			case CHANNEL_1X -> draw("channel1x", display, baked, matrix, buffers, light);
			case CHANNEL_3X -> draw("channel3x", display, baked, matrix, buffers, light);
			case CONTROL_FRAME -> draw("control_frame", display, baked, matrix, buffers, light);
			case CONTROL_ROD -> draw("control_rod", display, baked, matrix, buffers, light);
		}
	}

	private static void draw(String mesh, BlockState display, BakedModel baked, PoseStack matrix, MultiBufferSource buffers, int light) {
		one(part(mesh, "SIDE"), RenderPWRMeshedWreck.textureOf(baked.getParticleIcon(ModelData.EMPTY)), matrix, buffers, light);
		one(part(mesh, "TOP"), RenderPWRMeshedWreck.side(baked, display, Direction.UP), matrix, buffers, light);
	}

	private static void drawAll(ModelResourceLocation model, BlockState display, BakedModel baked, PoseStack matrix, MultiBufferSource buffers, int light) {
		one(model, RenderPWRMeshedWreck.textureOf(baked.getParticleIcon(ModelData.EMPTY)), matrix, buffers, light);
	}

	private static void one(ModelResourceLocation model, ResourceLocation texture, PoseStack matrix, MultiBufferSource buffers, int light) {
		ObjUtil.renderRetextured(ObjUtil.getModel(model), matrix, buffers.getBuffer(RenderType.entityCutout(texture)), light, OverlayTexture.NO_OVERLAY);
	}

	@Override
	public ResourceLocation getTextureLocation(PWRDebrisEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
