package com.leafia.contents.machines.reactors.pwr.blocks.wreckage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hbm.render.util.ObjUtil;
import com.leafia.lib.LeafiaRef;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.RuntimeRenderType;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.Variation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a wreck: its variation's mesh, on the textures of the block it replaced, darkened by its scorch.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/wreckage/RenderPWRMeshedWreck.java}.
 * The transform is upstream's - centred, then for a wreck pointing down {@code rotate(-180, X)} and for one
 * pointing sideways {@code rotate(180 - yaw, Y)} then {@code rotate(-90, X)}, in parity with the collision
 * boxes - and so is the texturing rule, decided once per variation from the mesh's object names: a mesh
 * with a {@code FRONT} draws its six named faces each on the replaced block's texture for that side
 * ({@link RuntimeRenderType#ALL_SIX}); one with {@code TOP} (and {@code BOTTOM}) draws those on the top
 * (and bottom) texture and {@code SIDE} on the particle texture; any other is drawn whole on the particle
 * texture. Of the fourteen meshes only {@code intact} has named faces. The tint is
 * {@code (1 - s, 1 - s - 0.05 s', 1 - s - 0.06 s')} with {@code s = (scorch / 7)^0.8 x 0.9} and
 * {@code s' = (scorch / 7)^4}.
 *
 * <p>"Binding" a block's texture is upstream's {@code bindByIconName}: the sprite's name turned back into
 * its texture file, {@code ns:block/x} to {@code ns:textures/block/x.png}. Each (mesh, object) pair is a
 * baked model ({@link #part}) drawn through {@link ObjUtil#renderRetextured} onto that file, culling off as
 * upstream turned it off. Upstream passes the <em>wreck's</em> block state where it asks the replaced
 * block's model for a side's quads; a plain block model ignores the state it is asked with, so the replaced
 * block's own state is passed here and nothing a player can see changes.
 */
@OnlyIn(Dist.CLIENT)
public class RenderPWRMeshedWreck implements BlockEntityRenderer<PWRMeshedWreckEntity> {

	private static final RandomSource RANDOM = RandomSource.create();

	/** Every wreck mesh's object names: {@link PWRMeshedWreck#MESH_OBJECTS}. */
	public static final Map<String, String[]> OBJECTS = PWRMeshedWreck.MESH_OBJECTS;

	public RenderPWRMeshedWreck(BlockEntityRendererProvider.Context context) {}

	/** One baked (mesh, object) model: {@code leafia:block/<kind>_<mesh>_<object>}. */
	public static ModelResourceLocation part(String kind, String mesh, String object) {
		return ModelResourceLocation.standalone(LeafiaRef.rl("block/" + kind + "_" + mesh + "_" + object.toLowerCase().replace('.', '_')));
	}

	/** Every model this renderer draws, for {@code ModelEvent.RegisterAdditional}. */
	public static List<ModelResourceLocation> all() {
		List<ModelResourceLocation> out = new ArrayList<>();
		OBJECTS.forEach((mesh, objects) -> {
			for(String object : objects) out.add(part("pwrwreck", mesh, object));
		});
		return out;
	}

	/** Upstream's render-type decision from a mesh's group names. */
	public static RuntimeRenderType renderTypeOf(String[] objects) {
		int mode = 0b000;
		for(String group : objects) {
			switch(group) {
				case "FRONT" -> mode = 0b100;
				case "TOP" -> mode = mode | 0b010;
				case "BOTTOM" -> mode = mode | 0b001;
				default -> {}
			}
		}
		if((mode & 0b100) > 0) return RuntimeRenderType.ALL_SIX;
		else if((mode & 0b001) > 0) return RuntimeRenderType.SIDE_TOP_BOTTOM;
		else if((mode & 0b010) > 0) return RuntimeRenderType.SIDE_TOP;
		return RuntimeRenderType.PARTICLE;
	}

	@Override
	public void render(PWRMeshedWreckEntity entity, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {

		BlockState display = entity.display;
		Variation var = entity.getVariation();
		if(display == null || var == null) return;
		String[] objects = OBJECTS.get(var.meshName);
		if(objects == null) return;

		matrix.pushPose();
		matrix.translate(0.5D, 0.5D, 0.5D);

		Direction face = entity.getBlockState().getValue(PWRMeshedWreck.FACING);
		// rotation algorithm parity to PWRMeshedWreck
		if(face.getStepY() != 0) {
			if(face == Direction.DOWN) matrix.mulPose(Axis.XP.rotationDegrees(-180));
		} else {
			matrix.mulPose(Axis.YP.rotationDegrees(180 - face.toYRot()));
			matrix.mulPose(Axis.XP.rotationDegrees(-90));
		}

		if(var.renderType == null) var.renderType = renderTypeOf(objects);

		double scorch = Math.pow(entity.scorch / (double) 7, 0.8) * 0.9;
		double scorch2 = Math.pow(entity.scorch / (double) 7, 4);
		float r = (float) (1 - scorch);
		float g = (float) (1 - scorch - scorch2 * 0.05);
		float b = (float) (1 - scorch - scorch2 * 0.06);

		BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(display);
		ResourceLocation particle = textureOf(baked.getParticleIcon(ModelData.EMPTY));

		switch(var.renderType) {
			case PARTICLE -> {
				for(String object : objects) draw(matrix, buffers, light, r, g, b, particle, var.meshName, object);
			}
			case ALL_SIX -> {
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.NORTH), var.meshName, "FRONT");
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.EAST), var.meshName, "RIGHT");
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.SOUTH), var.meshName, "BACK");
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.WEST), var.meshName, "LEFT");
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.DOWN), var.meshName, "BOTTOM");
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.UP), var.meshName, "TOP");
			}
			case SIDE_TOP_BOTTOM, SIDE_TOP -> {
				if(var.renderType == RuntimeRenderType.SIDE_TOP_BOTTOM)
					draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.DOWN), var.meshName, "BOTTOM");
				draw(matrix, buffers, light, r, g, b, side(baked, display, Direction.UP), var.meshName, "TOP");
				draw(matrix, buffers, light, r, g, b, particle, var.meshName, "SIDE");
			}
		}

		matrix.popPose();
	}

	private static void draw(PoseStack matrix, MultiBufferSource buffers, int light, float r, float g, float b,
			ResourceLocation texture, String mesh, String object) {
		ObjUtil.renderRetextured(ObjUtil.getModel(part("pwrwreck", mesh, object)), matrix,
				buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, r, g, b, 1F, 0F, 0F);
	}

	/** Upstream's {@code tryBindQuads}: the first quad's sprite on that side, or the particle sprite. */
	public static ResourceLocation side(BakedModel baked, BlockState display, Direction face) {
		List<BakedQuad> quads = baked.getQuads(display, face, RANDOM, ModelData.EMPTY, null);
		return textureOf(quads.isEmpty() ? baked.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite());
	}

	/** Upstream's {@code bindByIconName}: {@code ns:block/x} to {@code ns:textures/block/x.png}. */
	public static ResourceLocation textureOf(TextureAtlasSprite sprite) {
		ResourceLocation name = sprite.contents().name();
		return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
	}

	@Override
	public boolean shouldRenderOffScreen(PWRMeshedWreckEntity entity) {
		return true;
	}
}
