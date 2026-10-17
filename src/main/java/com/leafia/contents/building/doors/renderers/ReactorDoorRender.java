package com.leafia.contents.building.doors.renderers;

import java.util.List;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blocks.BlockDummyable;
import com.hbm.render.blockentity.door.IRenderDoors;
import com.hbm.render.util.ObjUtil;
import com.leafia.contents.building.doors.AddonDoorDecl.AddonDefaultSkins;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorTE;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the reactor door: a wall with a round hatch in it, a handle on the hatch, and a brick frame
 * on each face painted with whatever block it was given.
 *
 * <p>Upstream: {@code .../doors/renderers/ReactorDoorRender.java}. Five OBJ parts:
 *
 * <ul>
 * <li>{@code Wall} is the steel around the hatch, on the door's current skin, one of seven;</li>
 * <li>{@code Door} swings out on a hinge 0.625 to the side: it turns {@code -140 x ratio} degrees,
 *     the ratio being the animation's {@code DOOR} channel;</li>
 * <li>{@code Handle} is on the hatch, so it turns with it, and turns itself a further
 *     {@code -90 x ratio} about the door's face, the ratio being the {@code HANDLE} channel;</li>
 * <li>{@code FrameA} and {@code FrameB} are the brick surrounds of the two faces. Each takes the texture of
 *     the block it was painted with: the first quad's sprite on the north face of the block's model, or the
 *     model's particle sprite if that face has no quad. Each is lit from the cell outside its own face -
 *     one in front of the core and two behind it - since the frame fills the cell it is drawn in.</li>
 * </ul>
 *
 * <h2>Why the parts are not drawn from the block atlas</h2>
 *
 * The frame's texture coordinates run to 4.9, because it tiles a block texture across a three-block
 * wall, and an atlas sprite cannot repeat: past its edge are its neighbours. Upstream binds the
 * texture itself, and so does this - every part is drawn with
 * {@link ObjUtil#renderRetextured} on a render type of the texture's own, which wraps. The seven
 * skins are PNGs under {@code textures/block/} that no model references, for the same reason.
 * See D-3005.
 *
 * <p>The upstream code that finds a block's texture catches an {@code IllegalArgumentException} with
 * the comment "FUCK YOUU" and falls back to the base mod's brick concrete; the fallback is kept.
 */
@OnlyIn(Dist.CLIENT)
public class ReactorDoorRender implements IRenderDoors {

	public static final ReactorDoorRender INSTANCE = new ReactorDoorRender();

	public static final ModelResourceLocation FRAME_A = standalone("reactor_door_framea");
	public static final ModelResourceLocation FRAME_B = standalone("reactor_door_frameb");
	public static final ModelResourceLocation WALL = standalone("reactor_door_wall");
	public static final ModelResourceLocation DOOR = standalone("reactor_door_door");
	public static final ModelResourceLocation HANDLE = standalone("reactor_door_handle");

	/** Upstream's {@code test}: what a frame is drawn on when its block's texture cannot be read. */
	public static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath("hbm", "textures/block/brick_concrete.png");

	private static final RandomSource RANDOM = RandomSource.create();

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/" + path));
	}

	/** For {@code LeafiaClient} to register. */
	public static ModelResourceLocation[] models() {
		return new ModelResourceLocation[] {FRAME_A, FRAME_B, WALL, DOOR, HANDLE};
	}

	/**
	 * Upstream's {@code getRsc(block, meta)}: the texture file a painted block draws, from its model.
	 *
	 * <p>The sprite's name is {@code namespace:block/name}, and the file is
	 * {@code namespace:textures/block/name.png}, which is upstream's {@code formatRsc}.
	 */
	public static ResourceLocation textureOf(BlockState state) {
		try {
			BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
			List<net.minecraft.client.renderer.block.model.BakedQuad> quads = baked.getQuads(state, Direction.NORTH, RANDOM);
			TextureAtlasSprite sprite = quads.isEmpty() ? baked.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
			ResourceLocation name = sprite.contents().name();
			return name.withPath(path -> "textures/" + path + ".png");
		} catch(RuntimeException e) {
			return FALLBACK;
		}
	}

	/** The skin file for an index, wrapped into range as the base door's skin count does. */
	public static ResourceLocation skin(int index) {
		return AddonDefaultSkins.ALL[Math.floorMod(index, AddonDefaultSkins.ALL.length)];
	}

	/** One part, on a texture of its own. */
	private static void part(ModelResourceLocation model, ResourceLocation texture, PoseStack matrix,
			MultiBufferSource buffers, int light, int overlay) {
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(texture));
		ObjUtil.renderRetextured(ObjUtil.getModel(model), matrix, buffer, light, overlay);
	}

	/**
	 * The hatch and its handle, at the two animation ratios - shared by the world and the item.
	 * The matrix is the door's own frame.
	 */
	private static void moving(PoseStack matrix, MultiBufferSource buffers, ResourceLocation skin, double doorRatio,
			double handleRatio, int light, int overlay) {

		part(WALL, skin, matrix, buffers, light, overlay);

		matrix.translate(-0.625, 0, 0.5);
		matrix.mulPose(Axis.YP.rotationDegrees((float) (-140 * doorRatio)));
		matrix.translate(0.625, 0, -0.5);
		part(DOOR, skin, matrix, buffers, light, overlay);

		matrix.translate(0, 1.0625, 0);
		matrix.mulPose(Axis.ZP.rotationDegrees((float) (-90 * handleRatio)));
		matrix.translate(0, -1.0625, 0);
		part(HANDLE, skin, matrix, buffers, light, overlay);
	}

	@Override
	public void render(BlockEntityDoorGeneric door, PoseStack matrix, VertexConsumer cutout, int light, int overlay) {

		if(!(door instanceof ReactorDoorTE reac)) return;
		Level level = door.getLevel();
		if(level == null) return;

		// The same source the block entity pass is given: IRenderDoors hands over only the atlas's
		// buffer, and these parts are not on the atlas.
		MultiBufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

		matrix.mulPose(Axis.YP.rotationDegrees(90));
		matrix.translate(0, 0, -0.5);

		Direction face = door.getBlockState().getValue(BlockDummyable.DIR);
		int lightA = LevelRenderer.getLightColor(level, door.getBlockPos().relative(face));
		int lightB = LevelRenderer.getLightColor(level, door.getBlockPos().relative(face, -2));

		part(FRAME_A, textureOf(reac.skinA), matrix, buffers, lightA, overlay);
		part(FRAME_B, textureOf(reac.skinB), matrix, buffers, lightB, overlay);

		double handleRatio = 0;
		double doorRatio = 0;
		if(door.state == BlockEntityDoorGeneric.STATE_OPEN) {
			handleRatio = 1;
			doorRatio = 1;
		}
		if(door.currentAnimation != null) {
			handleRatio = IRenderDoors.getRelevantTransformation("HANDLE", door.currentAnimation)[1];
			doorRatio = IRenderDoors.getRelevantTransformation("DOOR", door.currentAnimation)[1];
		}

		moving(matrix, buffers, skin(door.getSkinIndex()), doorRatio, handleRatio, light, overlay);
	}

	/**
	 * The item: the door standing in the cell, shut, with the default paint and a skin that changes
	 * every two seconds.
	 *
	 * <p>Upstream's {@code ReactorDoorItemRender} draws {@code FrameA} and {@code FrameB} on the base
	 * mod's brick concrete (not on the door's defaults) and everything else on
	 * {@code getCyclingSkins()}; the cycling is NTM-CE's and its period is not in the source we have,
	 * so two seconds is a choice. The door has no handle turn or swing, which is a shut door.
	 */
	public static class ItemRender extends BlockEntityWithoutLevelRenderer {

		public ItemRender() {
			super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		}

		@Override
		public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack matrix,
				MultiBufferSource buffers, int light, int overlay) {

			matrix.pushPose();
			matrix.translate(0.5, 0, 0.5);

			part(FRAME_A, FALLBACK, matrix, buffers, light, OverlayTexture.NO_OVERLAY);
			part(FRAME_B, FALLBACK, matrix, buffers, light, OverlayTexture.NO_OVERLAY);

			int cycle = (int) (System.currentTimeMillis() / 2000L);
			moving(matrix, buffers, skin(cycle), 0, 0, light, OverlayTexture.NO_OVERLAY);

			matrix.popPose();
		}
	}
}
