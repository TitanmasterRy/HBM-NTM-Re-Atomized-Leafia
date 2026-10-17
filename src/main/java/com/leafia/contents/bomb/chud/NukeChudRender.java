package com.leafia.contents.bomb.chud;

import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the Fat Chud: the base mod's Fat Man under the addon's {@code fatchud} texture (D-3022).
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/chud/NukeChudRender.java} - translate to the block's middle, turn by
 * the facing (2: 270, 3: 90, 4: 0, 5: 180 degrees), bind {@code leafia:textures/models/fatchud.png} and draw NTM-CE's
 * {@code models/bombs/fatman.obj}. That mesh is the base mod's {@code fat_man.obj} vertex for vertex, so the model is
 * a baked {@code neoforge:obj} model over the base mod's file with the addon's texture,
 * {@code leafia:block/nuke_chud}, written by a generator script. Upstream's nested item renderer
 * is that model's item JSON, which carries the base Fat Man's own item transforms.
 */
@OnlyIn(Dist.CLIENT)
public class NukeChudRender implements BlockEntityRenderer<NukeChudTE> {

	public NukeChudRender(BlockEntityRendererProvider.Context context) { }

	/** Upstream's switch on the metadata facing. */
	public static float rotation(Direction facing) {
		return switch(facing) {
			case SOUTH -> 90F;
			case EAST -> 180F;
			case NORTH -> 270F;
			default -> 0F;
		};
	}

	@Override
	public void render(NukeChudTE te, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {
		matrix.pushPose();
		matrix.translate(0.5D, 0.0D, 0.5D);
		Direction facing = te.getBlockState().hasProperty(NukeChudBlock.FACING) ? te.getBlockState().getValue(NukeChudBlock.FACING) : Direction.WEST;
		matrix.mulPose(Axis.YP.rotationDegrees(rotation(facing)));
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.NUKE_CHUD), matrix, buffers.getBuffer(Sheets.cutoutBlockSheet()), light, OverlayTexture.NO_OVERLAY);
		matrix.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(NukeChudTE te) {
		return true;
	}

	/** Upstream's {@code INFINITE_EXTENT_AABB}. */
	@Override
	public AABB getRenderBoundingBox(NukeChudTE te) {
		return AABB.INFINITE;
	}

	/** Upstream's {@code getMaxRenderDistanceSquared() = 65536}. */
	@Override
	public int getViewDistance() {
		return 256;
	}
}
