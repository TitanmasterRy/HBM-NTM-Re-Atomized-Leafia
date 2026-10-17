package com.leafia.contents.machines.reactors.lftr.processing.separator;

import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the salt separator: one OBJ (the vacuum distillery's, recoloured - the block's own tooltip
 * says so), one texture, no rotation.
 *
 * <p>Upstream: {@code .../separator/SaltSeparatorRender.java}, a {@code TileEntitySpecialRenderer} that
 * translates to the middle of the core's block and draws the whole model, and a nested
 * {@code SaltSeparatorItemRender} (size reference 10, y offset -0.165) that is the item model's
 * {@code display} block here, written by a generator script. The model is a
 * cylinder, so the machine's facing changes nothing about how it is drawn; the upstream renderer takes
 * none either.
 *
 * <p>{@code isGlobalRenderer} (draw regardless of the frustum) becomes {@link #shouldRenderOffScreen},
 * and the tile entity's {@code getRenderBoundingBox} (one block out on each side, six tall) is
 * {@link #getRenderBoundingBox}. The cutout block sheet is the HP boiler's reason (D-1062): the model is
 * baked, so its quads carry atlas coordinates.
 */
@OnlyIn(Dist.CLIENT)
public class SaltSeparatorRender implements BlockEntityRenderer<SaltSeparatorTE> {

	public SaltSeparatorRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(SaltSeparatorTE separator, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		// Upstream's translate(x + 0.5, y, z + 0.5). The block origin is already here.
		matrix.translate(0.5D, 0D, 0.5D);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.SALT_SEPARATOR), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);

		matrix.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(SaltSeparatorTE separator) {
		return true;
	}

	/** Upstream's {@code (x-1, y, z-1)} to {@code (x+2, y+6, z+2)}. */
	@Override
	public AABB getRenderBoundingBox(SaltSeparatorTE separator) {
		return new AABB(separator.getBlockPos()).inflate(1D, 0D, 1D).expandTowards(0D, 5D, 0D);
	}
}
