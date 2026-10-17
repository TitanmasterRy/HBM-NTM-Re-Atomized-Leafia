package com.leafia.contents.nonmachines.storage.items;

import com.hbm.blockentity.machine.storage.BlockEntityCrateBase;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws what is written on a crate: on each of the four sides a small line at the top, a big one or the icon
 * in the middle and a small one at the bottom, and on the top and bottom faces the vertical lines.
 *
 * <p>Upstream: {@code .../nonmachines/storage/items/CrateLabelRender.java}, a {@code TileEntitySpecialRenderer}
 * whose {@code renderTxt} is a stack of {@code glRotatef}/{@code glTranslated}/{@code glScalef} (its own
 * author tags it "Old code goes brrrrrrrr"). Every number is upstream's: the surface offset 0.501, the text's
 * {@code 1/7/16} of a block per pixel times a scale of 1.25 for the small lines and 3, 4 or 5 for the middle one
 * (by its length: three characters 4, fewer than three 5), the small lines' offset of
 * {@code (0.5 - 1/16 * 0.75 / 2) * 0.8} above and below the centre, the vertical lines on the faces rotated
 * 90 and -90 degrees about the face's axis. A rotation about +Y is the same in {@code PoseStack} as in
 * {@code glRotatef}, so the angles are kept as upstream's.
 *
 * <p>The text is lit full bright ({@code setLightmapTextureCoords(240, 240)}). An icon is drawn through the
 * item renderer's fixed transform at half scale, <b>on the face opposite the one its loop turn writes the
 * text on</b> (upstream translates by minus the offset) - over the four turns that is one icon on every face,
 * and the middle line is skipped wherever an icon is set.
 *
 * <p>Drawn on the base mod's four crates' block entities, which have no renderer of their own.
 */
@OnlyIn(Dist.CLIENT)
public class CrateLabelRender implements BlockEntityRenderer<BlockEntityCrateBase> {

	protected static final float SURFACE_OFFSET = 0.501F;

	private final Font font;

	public CrateLabelRender(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	/** Upstream's {@code renderTxt}. */
	void renderTxt(PoseStack pose, MultiBufferSource buffers, float rx, float ry, float rz, float yoffset, int alignment,
			float scale, String text, boolean vertical) {

		if(text.isEmpty()) return;

		pose.pushPose();
		if(vertical) yoffset = -yoffset;
		pose.translate(0.5, 0.5, 0.5);
		pose.mulPose(Axis.XP.rotationDegrees(rx));
		pose.mulPose(Axis.YP.rotationDegrees(ry));
		pose.mulPose(Axis.ZP.rotationDegrees(rz));
		pose.translate(SURFACE_OFFSET, 0, 0);
		pose.translate(0F, yoffset, 0F);

		int width = font.width(text);
		int height = font.lineHeight - 1;

		float pix = 1F / 7F / 16F;
		float f3 = pix * scale;
		if(!vertical) pose.scale(f3, -f3, f3);
		else pose.scale(-f3, f3, -f3);
		pose.mulPose(Axis.YP.rotationDegrees(90));

		font.drawInBatch(text, -width / 2, -(height / 2) * alignment, 0xFFFFFF, false, pose.last().pose(), buffers,
				Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		pose.popPose();
	}

	@Override
	public void render(BlockEntityCrateBase crate, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {

		CrateLabels labels = CrateLabels.of(crate);
		if(labels.isBlank()) return;

		String labelUpper = labels.upper();
		String labelMiddle = labels.middle();
		String labelLower = labels.lower();
		Item labelIcon = labels.icon();

		float middleScale = 3F;
		if(labelMiddle.length() == 3) middleScale = 4F;
		if(labelMiddle.length() < 3) middleScale = 5F;

		float offs = 0.5F - (1F / 16F * 0.75F) / 2F;
		int orientation = 0;
		for(int rot = 0; rot < 358; rot += 90) {
			renderTxt(pose, buffers, 0F, rot, 0F, offs * 0.8F, 0, 1.25F, labelUpper, false);
			if(labelIcon == null) {
				renderTxt(pose, buffers, 0F, rot, 0F, 0F, 1, middleScale, labelMiddle, false);
			} else {
				ItemStack stack = new ItemStack(labelIcon);
				pose.pushPose();
				pose.translate(0.5, 0.5, 0.5);
				pose.mulPose(Axis.YP.rotationDegrees(rot));
				pose.translate(-SURFACE_OFFSET, 0, 0);
				pose.mulPose(Axis.YP.rotationDegrees(90));
				pose.scale(0.5F, 0.5F, 0.5F);
				Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
						net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, crate.getLevel(), 0);
				pose.popPose();
			}
			renderTxt(pose, buffers, 0F, rot, 0F, -offs * 0.8F, 2, 1.25F, labelLower, false);

			renderTxt(pose, buffers, 0F, rot, 90F, -offs * 0.8F, 2, 1.25F, labels.vertical(orientation), true);
			renderTxt(pose, buffers, 0F, rot, -90F, -offs * 0.8F, 2, 1.25F, labels.vertical(orientation), true);
			orientation++;
		}
	}
}
