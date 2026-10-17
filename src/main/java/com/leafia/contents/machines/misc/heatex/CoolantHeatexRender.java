package com.leafia.contents.machines.misc.heatex;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the coolant heat exchanger: the base mod's condenser, recoloured, with the same two counter-rotating fans.
 *
 * <p>Upstream: {@code .../heatex/CoolantHeatexRender.java}, which draws {@code ResourceManager.condenser} (the
 * base mod's OBJ) under {@code leafia:textures/models/leafia/lftr/condenser.png} - three parts, {@code Condenser},
 * {@code Fan1} and {@code Fan2}. Here they are three baked models over a copy of that OBJ,
 * drawn exactly as the base mod's own {@code RenderCondenser}
 * draws its three: the body, then each fan about {@code (0, 1.5, 0)} through {@code lastSpin + (spin - lastSpin) x
 * partialTick} degrees about +X for the first and -X for the second, with culling off for the whole of it.
 *
 * <h2>The rotation</h2>
 *
 * Upstream's switch on {@code metadata - 10} gives north 90, west 180, south 270 and east 0 - the base
 * condenser's table, and not {@link ObjUtil#rotateForFacing}'s (D-332), so it is written out.
 *
 * <p>The item is a baked model, not a renderer: upstream's item draws the same OBJ whole and still, and a model
 * that only sits there is what an item model is (D-346). The box is upstream's, not turned for the facing.
 */
@OnlyIn(Dist.CLIENT)
public class CoolantHeatexRender implements BlockEntityRenderer<CoolantHeatexTE> {

	public CoolantHeatexRender(BlockEntityRendererProvider.Context context) { }

	/** Upstream's four cases, by name rather than by {@code ForgeDirection} ordinal. */
	static float facingDegrees(Direction dir) {
		return switch(dir) {
			case NORTH -> 90F;
			case SOUTH -> 270F;
			case WEST -> 180F;
			case EAST -> 0F;
			default -> 0F;
		};
	}

	@Override
	public void render(CoolantHeatexTE heatex, float partialTick, PoseStack matrix, MultiBufferSource buffers,
			int light, int overlay) {

		if(!(heatex.getBlockState().getBlock() instanceof CoolantHeatexBlock)) return;
		Direction facing = heatex.getBlockState().getValue(BlockDummyable.DIR);
		var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);
		matrix.mulPose(Axis.YP.rotationDegrees(facingDegrees(facing)));

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.COOLANT_HEATEX_CONDENSER), matrix, buffer, light, OverlayTexture.NO_OVERLAY);

		float rot = Mth.lerp(partialTick, heatex.lastSpin, heatex.spin);

		matrix.pushPose();
		ObjUtil.rotateAbout(matrix, rot, 1F, 0F, 0F, 0F, 1.5F, 0F);
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.COOLANT_HEATEX_FAN1), matrix, buffer, light, OverlayTexture.NO_OVERLAY);
		matrix.popPose();

		matrix.pushPose();
		ObjUtil.rotateAbout(matrix, rot, -1F, 0F, 0F, 0F, 1.5F, 0F);
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.COOLANT_HEATEX_FAN2), matrix, buffer, light, OverlayTexture.NO_OVERLAY);
		matrix.popPose();

		matrix.popPose();
	}

	@Override
	public AABB getRenderBoundingBox(CoolantHeatexTE heatex) {
		return heatex.getRenderBoundingBox();
	}
}
