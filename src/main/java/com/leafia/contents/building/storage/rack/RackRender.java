package com.leafia.contents.building.storage.rack;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the rack: its frame, the blocks standing on it and a pallet under each shelf that has one.
 *
 * <p>Upstream: {@code .../building/storage/rack/RackRender.java}. The order is upstream's: the frame ({@code rack},
 * {@code poles}, {@code support}) on {@code rack.png}, then each stored block, then the pallets on spruce
 * planks - the lower pallet if either block of the lower shelf is stored, the upper if either of the upper.
 * The blocks are drawn at (-1.125, 0.375, 0.375) and (0.125, 0.375, 0.375) on the lower shelf and at
 * y 1.75 on the upper, each turned half a turn about its own corner (upstream draws the block's baked quads
 * through a matrix of {@code translate(1, 0, 0)} and {@code rotate(180, y)}), after the frame's own
 * {@code translate(0, 0, -0.5)} and the facing.
 *
 * <p>The facing is the table every addon multiblock renderer uses: north 180, south 0, west 270, east 90. The frame
 * is drawn from {@link RackObj}'s geometry on standalone textures, so the pallets' plank can repeat.
 *
 * <p>Its item form is not this class: upstream's nested {@code RackItemRender} draws the frame at half size, and
 * here that is a baked item model (D-1040).
 */
@OnlyIn(Dist.CLIENT)
public class RackRender implements BlockEntityRenderer<RackTE> {

	static final ResourceLocation FRAME = LeafiaRef.rl("textures/block/sellacity_rack.png");
	static final ResourceLocation PALLET = ResourceLocation.withDefaultNamespace("textures/block/spruce_planks.png");

	public RackRender(BlockEntityRendererProvider.Context context) { }

	/** One object on one texture. The buffer is asked for at the draw (D-1062). */
	private static void draw(String object, PoseStack matrix, MultiBufferSource buffers, ResourceLocation texture, int light) {

		Mesh mesh = RackObj.meshes().get(object);
		if(mesh == null) throw new IllegalArgumentException("the rack's OBJ has no object " + object);

		VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutout(texture));
		PoseStack.Pose pose = matrix.last();
		float[] d = mesh.data();

		for(int i = 0; i < d.length; i += 8) {
			buffer.addVertex(pose, d[i], d[i + 1], d[i + 2])
					.setColor(1F, 1F, 1F, 1F)
					.setUv(d[i + 3], d[i + 4])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
		}
	}

	@Override
	public void render(RackTE rack, float partialTick, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {

		BlockState state = rack.getBlockState();
		if(!(state.getBlock() instanceof RackBlock)) return;

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(RackBlock.facingDegrees(state.getValue(BlockDummyable.DIR))));
		matrix.translate(0D, 0D, -0.5D);

		draw("rack", matrix, buffers, FRAME, light);
		draw("poles", matrix, buffers, FRAME, light);
		draw("support", matrix, buffers, FRAME, light);

		boolean palletLower = false;
		boolean palletUpper = false;
		for(int i = 0; i < RackTE.PLACES; i++) {
			if(!rack.has(i)) continue;
			if(i < 2) palletLower = true;
			else palletUpper = true;

			if(!(rack.crates[i].getItem() instanceof BlockItem item)) continue;

			matrix.pushPose();
			matrix.translate(RackTE.PLACES_AT[i][0], RackTE.PLACES_AT[i][1], RackTE.PLACES_AT[i][2]);
			// upstream's translate(1, 0, 0) then rotate(180, y): the block's cell turned half a turn
			matrix.translate(1D, 0D, 0D);
			matrix.mulPose(Axis.YP.rotationDegrees(180F));
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(item.getBlock().defaultBlockState(), matrix, buffers, light, overlay);
			matrix.popPose();
		}

		if(palletLower) draw("palletLower", matrix, buffers, PALLET, light);
		if(palletUpper) draw("palletUpper", matrix, buffers, PALLET, light);

		matrix.popPose();
	}

	/**
	 * Upstream's {@code getRenderBoundingBox} is {@code (x - 1, y, z - 1)} to {@code (x + 2, y + 2, z + 2)}: three
	 * short of the frame's height. It is widened to the whole shelf, the frame being 2.94 high and either facing
	 * reaching two blocks from the core.
	 */
	@Override
	public AABB getRenderBoundingBox(RackTE rack) {
		BlockPos at = rack.getBlockPos();
		return new AABB(at.getX() - 2, at.getY(), at.getZ() - 2, at.getX() + 3, at.getY() + 3, at.getZ() + 3);
	}
}
