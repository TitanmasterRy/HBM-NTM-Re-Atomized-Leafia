package com.leafia.contents.building.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a letter sign: the letter itself, in the sign's dye colour, on the face of an otherwise
 * invisible block.
 *
 * <p>Upstream: {@code com/leafia/contents/building/sign/SignRender.java}.
 *
 * <h2>The addon's first renderer that draws text rather than a model</h2>
 *
 * The three before it — the light, the valves, the wind turbine — all bake an OBJ and hand it to
 * {@code ObjUtil}. This one has no geometry at all: {@code font.drawString} becomes
 * {@link Font#drawInBatch}, which takes the pose and the buffer source rather than relying on the
 * GL matrix stack, and needs a packed light because text is a quad like anything else.
 *
 * <h2>Upstream draws the letter twice, and that is not a mistake</h2>
 *
 * Each call is followed by a half-pixel translate and a second identical draw. It is a poor man's
 * bold, from before {@code drawInBatch} could be told to embolden, and it is kept: dropping it
 * makes every sign visibly thinner. What is <b>not</b> kept is {@code setUnicodeFlag(false)} —
 * forcing the legacy glyph set is not something 1.21.1 exposes to a renderer at all, it is a
 * resource-pack decision now. See D-1029.
 */
public class SignRender implements BlockEntityRenderer<SignTE> {

	/** Upstream's scale: the font is 7 units tall in the space a block wide. */
	private static final float SCALE = 1F / 7F;

	public SignRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(SignTE sign, float partialTick, PoseStack matrix, MultiBufferSource buffer,
			int packedLight, int packedOverlay) {

		BlockState state = sign.getBlockState();
		if(!(state.getBlock() instanceof SignBlock block)) return;

		matrix.pushPose();
		matrix.translate(0.5D, 0.5D, 0.5D);

		/*
		 * Upstream: rotate(-facing.getHorizontalAngle() + 180). Vanilla's toYRot() is that same
		 * horizontal angle, and the sign then has to face out of the wall rather than into it,
		 * which is the 180.
		 */
		matrix.mulPose(Axis.YP.rotationDegrees(-state.getValue(SignBlock.FACING).toYRot() + 180F));
		matrix.translate(0D, 0.5D, 0.495D);
		matrix.scale(SCALE, SCALE, SCALE);

		// Text is drawn y-down and from the far side, so both axes flip. Upstream's scale(-1,-1,1).
		matrix.scale(-1F, -1F, 1F);

		drawDoubled(matrix, buffer, block.letter, sign.color.getTextColor(), packedLight);

		matrix.popPose();
	}

	/**
	 * Upstream's centred, doubled draw, shared by the block and the item so the letter is the same
	 * shape in the world and in the hand.
	 *
	 * <p>The {@code -0.25} after centring is upstream's, and it is not a rounding fudge — the font
	 * measures a glyph's advance, which includes the one-pixel gap after it, so centring on the
	 * advance leaves the glyph half a pixel right of centre.
	 */
	private static void drawDoubled(PoseStack matrix, MultiBufferSource buffer, String letter,
			int colour, int packedLight) {

		Font font = Minecraft.getInstance().font;
		float width = font.width(letter);

		matrix.translate(-width / 2F, 0D, 0D);
		matrix.translate(-0.25D, 0D, 0D);

		font.drawInBatch(letter, 0F, 0F, colour, false, matrix.last().pose(), buffer,
				Font.DisplayMode.NORMAL, 0, packedLight);

		matrix.translate(0.5D, 0D, 0D);

		font.drawInBatch(letter, 0F, 0F, colour, false, matrix.last().pose(), buffer,
				Font.DisplayMode.NORMAL, 0, packedLight);
	}

	/**
	 * The item in a player's hand, in a chest, or on the ground.
	 *
	 * <p>Upstream's nested {@code SignItemRender}, which extends its own {@code LeafiaItemRenderer}
	 * — one of the three "blockers" D-1028 found to be nothing. 1.21.1's counterpart is a
	 * {@link BlockEntityWithoutLevelRenderer} hung on the item through
	 * {@code IClientItemExtensions}, and this is <b>the first one in the whole project</b>, base
	 * mod included.
	 *
	 * <p>Upstream has two branches — a GUI one with its own transform stack and a common one for
	 * everywhere else — because 1.12.2's item renderer gave it no display transforms. Here the
	 * model JSON carries those, so there is one branch: the letter, white, centred.
	 */
	public static class SignItemRender extends BlockEntityWithoutLevelRenderer {

		public SignItemRender() {
			super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
					Minecraft.getInstance().getEntityModels());
		}

		@Override
		public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack matrix,
				MultiBufferSource buffer, int packedLight, int packedOverlay) {

			if(!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return;
			if(!(item.getBlock() instanceof SignBlock sign)) return;

			// The text's origin is the top of the glyph, which then hangs down seven rows - one
			// block, at this scale. The block puts that origin at the top of its own cell; here it
			// went to the middle of the cell, so the letter hung out of the bottom of every slot,
			// frame and hand (D-1081). Top centre, and it fills the cell exactly.
			//
			// Twice, back to back: Font text is a culled render type and the letter is flat, so it
			// has one visible side. The block is only ever seen from its front, but an item turns -
			// it spins on the ground, hangs at an angle in a hand, and an inventory slot looks at
			// it from the side the draw faces away from, which is why every sign was blank in the
			// hotbar. The second pass is the same letter turned half way round about the cell's
			// vertical axis, so it reads the right way round from behind as well.
			for(int side = 0; side < 2; side++) {
				matrix.pushPose();
				matrix.translate(0.5D, 1.0D, 0.5D);
				if(side == 1) matrix.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
				matrix.scale(SCALE, SCALE, SCALE);
				matrix.scale(-1F, -1F, 1F);

				drawDoubled(matrix, buffer, sign.letter, 0xFFFFFF, packedLight);
				matrix.popPose();
			}
		}
	}
}
