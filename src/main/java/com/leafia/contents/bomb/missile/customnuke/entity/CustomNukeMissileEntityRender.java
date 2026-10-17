package com.leafia.contents.bomb.missile.customnuke.entity;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.bomb.missile.AddonMissileItemRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the custom nuke missile in flight (D-3022): the neon micro missile, turned to its heading.
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/customnuke/entity/CustomNukeMissileEntityRender.java} -
 * {@code glRotatef(yaw - 90, 0, 1, 0)} then {@code glRotatef(pitch, 0, 0, 1)}, both interpolated, with culling off.
 * Unlike the base mod's own missile renderers it does not turn the yaw back afterwards, and it ignores the launch
 * pad's rotation byte; both kept. {@code NTMRenderHelper.getRenderPosFromMissile} was NTM-CE's interpolation of the
 * position, which 1.21.1's entity pass already does.
 */
@OnlyIn(Dist.CLIENT)
public class CustomNukeMissileEntityRender extends EntityRenderer<CustomNukeMissileEntity> {

	public CustomNukeMissileEntityRender(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(CustomNukeMissileEntity missile, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, missile.yRotO, missile.getYRot()) - 90.0F));
		pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, missile.xRotO, missile.getXRot())));
		ObjUtil.renderModel(ObjUtil.getModel(AddonMissileItemRender.NEONC), pose,
				buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(CustomNukeMissileEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
