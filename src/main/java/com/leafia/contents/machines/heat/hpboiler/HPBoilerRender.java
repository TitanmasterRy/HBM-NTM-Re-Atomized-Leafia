package com.leafia.contents.machines.heat.hpboiler;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the high-pressure boiler.
 *
 * <p>Upstream: {@code .../hpboiler/HPBoilerRender.java} — one OBJ, one texture, one rotation, and
 * a nested item renderer that does not port (it is the generated item model
 * a generator script writes, per D-1040/D-1043).
 *
 * <h2>The rotation is written out, not derived</h2>
 *
 * Upstream switches on {@code getBlockMetadata() - 10} — the {@code ForgeDirection} ordinal —
 * and gives NORTH 180°, SOUTH 0°, WEST 270°, EAST 90°. {@code ObjUtil.rotateForFacing} is the
 * base mod's helper for exactly this and it is <b>not</b> used, because D-1021 found the addon
 * does not share the base mod's convention everywhere: the fluid valves' angles were the same
 * four shifted half a turn, and using the helper drew every one of them backwards with nothing
 * to show for it. So the four numbers are transcribed from upstream's switch and the block that
 * follows says which is which.
 *
 * <p>{@link Sheets#cutoutBlockSheet()} rather than a standalone-texture type — the block atlas's
 * cutout pass, and one of the level's <em>fixed</em> buffers, so no other render type asked for
 * mid-frame can finish it under us (D-1062): {@link ObjUtil} draws a
 * <em>baked</em> model whose quads already carry atlas coordinates, which is the same reasoning
 * {@code BroofRender} records (D-1049), and the boiler has no translucency to want.
 */
@OnlyIn(Dist.CLIENT)
public class HPBoilerRender implements BlockEntityRenderer<HPBoilerTE> {

	public HPBoilerRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(HPBoilerTE boiler, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = boiler.getBlockState();
		if(!(state.getBlock() instanceof HPBoilerBlock)) return;

		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		// Upstream's translate(x + 0.5, y, z + 0.5). The block origin is already here.
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(facingDegrees(state.getValue(BlockDummyable.DIR))));

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.HP_BOILER), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);

		matrix.popPose();
	}

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

	/**
	 * The machine's own footprint, so it is not culled when the core leaves the frustum.
	 *
	 * <p>{@link HPBoilerBlock#getDimensions} is {@code {3, 0, 1, 1, 1, 1}} with an offset of one,
	 * so the machine is three up and one out on every side. The box below is <b>one block more
	 * generous than that in each direction</b>, deliberately: the offset moves the core off the
	 * block that was clicked, and a box that exactly fit the footprint would clip the far edge
	 * for some facings. It is not rotated for facing either, which is the simplification
	 * {@code RenderSolarBoiler} and {@code RenderCatalyticCracker} both carry and which costs
	 * nothing on a box that is square in plan.
	 */
	@Override
	public AABB getRenderBoundingBox(HPBoilerTE boiler) {
		return new AABB(boiler.getBlockPos()).inflate(2D, 0D, 2D).expandTowards(0D, 4D, 0D);
	}
}
