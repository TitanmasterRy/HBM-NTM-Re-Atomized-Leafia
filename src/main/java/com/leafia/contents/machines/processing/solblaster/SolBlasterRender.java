package com.leafia.contents.machines.processing.solblaster;

import com.hbm.blocks.BlockDummyable;
import com.hbm.items.ModItems;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the Neutron Blaster, and its door.
 *
 * <p>Upstream: {@code .../solblaster/SolBlasterRender.java}. The order, the textures, the
 * transforms and the two colours are upstream's:
 *
 * <ol>
 * <li>the base and frame, on the chemical plant's own texture;</li>
 * <li>the <b>door</b>, which swings on a hinge at (-0.625, 1.125, 1) through
 *     {@code -135 x r} degrees about the vertical, where {@code r} is the door's position
 *     (interpolated between its last two ticks) over {@link SolBlasterTE#doorDuration}; and
 *     inside that swing the <b>handle</b> turns {@code 1080 x r} degrees about the horizontal axis
 *     through (0, 1.688), which is three full turns on the way open;</li>
 * <li>the containment on brick concrete and its inside on ducrete brick, and the black plane that
 *     is the chamber's lid;</li>
 * <li>the hemisphere and tower in {@link #HULL}, the grey upstream computes as the square root of
 *     three measured linear values; a cover over each quarter that has a lens in it, and under it
 *     the lens's charge;</li>
 * <li>and last, the stain, blended, because it is translucent.</li>
 * </ol>
 *
 * <h2>The facing</h2>
 *
 * Upstream switches on the metadata's direction and gives north 180, south 0, west 270, east 90.
 * The four numbers are transcribed rather than taken from {@code ObjUtil.rotateForFacing}, which
 * D-1021 found is not the addon's convention in every renderer, and the boiler's is the same table.
 *
 * <h2>Why it does not use baked models</h2>
 *
 * Every other addon OBJ is baked by NeoForge against the block atlas, which cannot wrap: this one's
 * brick texture tiles ten times across the containment and its hemisphere sits on the corner of a
 * one-pixel texture. The parts are drawn from {@link SolBlasterObj}'s own geometry on standalone
 * textures (an entity render type each), which repeat as upstream's did.
 *
 * <h2>In the hand</h2>
 *
 * Upstream's nested {@code SolBlasterItemRender} draws the same parts at half size with the door
 * shut and no covers, and all four charges. {@link ItemRender} does that fitted to the item's unit
 * cell: the model is 5.06 blocks tall, so it is drawn at one 5.25th, and the item model's
 * {@code display} block does the rest.
 */
@OnlyIn(Dist.CLIENT)
public class SolBlasterRender implements BlockEntityRenderer<SolBlasterTE> {

	/**
	 * Upstream's {@code (float)Math.pow(x, 0.5)} of 0.104514, 0.110955f and 0.108075f - the
	 * hemisphere, tower and covers. The {@code f} suffixes are upstream's and are kept, because the
	 * float literals are not the doubles they print as.
	 */
	public static final float[] HULL = {
			(float) Math.pow(0.104514, 0.5),
			(float) Math.pow(0.110955f, 0.5),
			(float) Math.pow(0.108075f, 0.5) };

	public SolBlasterRender() { }

	public SolBlasterRender(BlockEntityRendererProvider.Context context) { }

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

	private static ResourceLocation own(String name) {
		return LeafiaRef.rl("textures/entity/sol_blaster/" + name + ".png");
	}

	/** What upstream binds before each {@code renderPart}: the base mod's own plant and bricks, and four of ours. */
	static final ResourceLocation CHEM = ResourceLocation.fromNamespaceAndPath("hbm", "textures/models/machines/chemical_plant.png");
	static final ResourceLocation CONCRETE = ResourceLocation.fromNamespaceAndPath("hbm", "textures/block/brick_concrete.png");
	static final ResourceLocation DUCRETE = ResourceLocation.fromNamespaceAndPath("hbm", "textures/block/brick_ducrete.png");
	static final ResourceLocation DOOR = own("door");
	static final ResourceLocation REACTOR_DOOR = own("reactordoor");
	static final ResourceLocation CHARGE = own("charge");
	static final ResourceLocation STAIN = own("stain");
	/** Upstream's {@code AddonBase.solid}: one white pixel, tinted. */
	static final ResourceLocation SOLID = own("solid");

	/**
	 * One object of the OBJ on one texture, tinted. The buffer is asked for here, at the draw, and
	 * every vertex is written before the next call asks again: the shared builder of a non-fixed
	 * render type is finished when another is requested, so a consumer held across two parts would
	 * throw "Not building!" (D-1062).
	 */
	private static void draw(String object, PoseStack matrix, MultiBufferSource buffers, ResourceLocation texture,
			boolean translucent, int light, float r, float g, float b) {

		SolBlasterObj.Mesh mesh = SolBlasterObj.meshes().get(object);
		if(mesh == null) throw new IllegalArgumentException("the Neutron Blaster's OBJ has no object " + object);

		VertexConsumer buffer = buffers.getBuffer(translucent ? RenderType.entityTranslucent(texture) : RenderType.entityCutout(texture));
		PoseStack.Pose pose = matrix.last();
		float[] d = mesh.data();

		for(int i = 0; i < d.length; i += 8) {
			buffer.addVertex(pose, d[i], d[i + 1], d[i + 2])
					.setColor(r, g, b, 1F)
					.setUv(d[i + 3], d[i + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
		}
	}

	private static void draw(String object, PoseStack matrix, MultiBufferSource buffers, ResourceLocation texture, int light) {
		draw(object, matrix, buffers, texture, false, light, 1F, 1F, 1F);
	}

	private static void tinted(String object, PoseStack matrix, MultiBufferSource buffers, int light, float r, float g, float b) {
		draw(object, matrix, buffers, SOLID, false, light, r, g, b);
	}

	private static boolean isLens(ItemStack stack) {
		return stack.is(ModItems.early_explosive_lenses.get()) || stack.is(ModItems.explosive_lenses.get());
	}

	@Override
	public void render(SolBlasterTE te, float partialTick, PoseStack matrix, MultiBufferSource buffers,
			int light, int overlay) {

		BlockState state = te.getBlockState();
		if(!(state.getBlock() instanceof SolBlasterBlock)) return;

		matrix.pushPose();
		// Upstream's translate(x + 0.5, y, z + 0.5). The block origin is already here.
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(facingDegrees(state.getValue(BlockDummyable.DIR))));

		float r = (te.prevDoorPos + (te.doorPos - te.prevDoorPos) * partialTick) / (float) SolBlasterTE.doorDuration;

		boolean[] lens = new boolean[5];
		for(int i = 1; i <= 4; i++) lens[i] = isLens(te.slots[i]);

		drawBody(matrix, buffers, light, r, lens);

		matrix.popPose();
	}

	/**
	 * Everything, for a door at {@code r} (0 shut, 1 open) and the lenses in each quarter. Shared by
	 * the block and the item, which differ only in what they pass.
	 */
	private static void drawBody(PoseStack matrix, MultiBufferSource buffers, int light, float r, boolean[] lens) {

		draw("Base", matrix, buffers, CHEM, light);
		draw("Frame", matrix, buffers, CHEM, light);

		matrix.pushPose();
		matrix.translate(-0.625D, 1.125D, 1D);
		matrix.mulPose(Axis.YP.rotationDegrees(-135F * r));
		matrix.translate(0.625D, -1.125D, -1D);
		draw("Door", matrix, buffers, DOOR, light);
		draw("DoorAlt", matrix, buffers, REACTOR_DOOR, light);
		matrix.translate(0D, 1.688D, 0D);
		matrix.mulPose(Axis.ZP.rotationDegrees(1080F * r));
		matrix.translate(0D, -1.688D, 0D);
		draw("Handle", matrix, buffers, REACTOR_DOOR, light);
		matrix.popPose();

		draw("Containment", matrix, buffers, CONCRETE, light);
		draw("ContainmentInside", matrix, buffers, DUCRETE, light);

		tinted("SolidBlack", matrix, buffers, light, 0F, 0F, 0F);
		tinted("Hemisphere", matrix, buffers, light, HULL[0], HULL[1], HULL[2]);
		tinted("Tower", matrix, buffers, light, HULL[0], HULL[1], HULL[2]);

		for(int i = 1; i <= 4; i++)
			if(lens[i]) tinted("Cover" + i, matrix, buffers, light, HULL[0], HULL[1], HULL[2]);

		for(int i = 1; i <= 4; i++)
			if(lens[i]) draw("Charge" + i, matrix, buffers, CHARGE, light);

		// Keep this last to be rendered: upstream's own comment.
		draw("Stain", matrix, buffers, STAIN, true, light, 1F, 1F, 1F);
	}

	/** Upstream's {@code new AxisAlignedBB(x - 3, y, z - 3, x + 4, y + 6, z + 4)}. */
	@Override
	public AABB getRenderBoundingBox(SolBlasterTE te) {
		return te.getRenderBoundingBox();
	}

	/**
	 * In the hand: the door shut, no covers, and all four charges showing.
	 *
	 * <p>Upstream's {@code renderCommon} has no covers and every charge, and draws them in the same
	 * order as the block does with the covers left out.
	 */
	@OnlyIn(Dist.CLIENT)
	public static class ItemRender extends BlockEntityWithoutLevelRenderer {

		/** The model is 5.06 blocks tall and 3 wide; this is the cell it is fitted to. */
		static final float SIZE = 5.25F;

		public ItemRender() {
			super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
		}

		@Override
		public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack matrix,
				MultiBufferSource buffers, int light, int overlay) {

			matrix.pushPose();
			matrix.translate(0.5D, 0.5D, 0.5D);
			matrix.scale(1F / SIZE, 1F / SIZE, 1F / SIZE);
			matrix.translate(0D, -SIZE / 2D, 0D);

			drawBody(matrix, buffers, light, 0F, new boolean[5]);
			// The charges upstream shows in the hand, which the block shows only where a lens is.
			for(int i = 1; i <= 4; i++) draw("Charge" + i, matrix, buffers, CHARGE, light);

			matrix.popPose();
		}
	}
}
