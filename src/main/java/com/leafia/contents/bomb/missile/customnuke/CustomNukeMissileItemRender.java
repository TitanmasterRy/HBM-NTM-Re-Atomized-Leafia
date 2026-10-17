package com.leafia.contents.bomb.missile.customnuke;

import com.hbm.render.util.ObjUtil;
import com.leafia.contents.bomb.missile.AddonMissileItemRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The custom nuke missile in the inventory and the hand (D-3022): the neon micro missile, spinning in a GUI slot.
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/customnuke/CustomNukeMissileItemRender.java}, a TEISR that
 * scaled and placed the model per context ({@code guiScale = 4 / 1.4}, {@code guiOffset = 1}) and turned it
 * {@code System.currentTimeMillis() / 15 % 360} degrees about its long axis in the GUI, with culling off. Here the
 * per-context placement is the item model's {@code display} block, which is the base mod's {@code missile_micro}'s -
 * the same form factor, and a mesh of nearly the same size (1.05 x 5.81 against 1.06 x 5.0) - so the addon's missile
 * sits in a slot exactly as the base micro missile does; and the turn is the base mod's
 * {@code ItemRenderMissileGeneric}'s, at upstream's 15 ms a degree about the mesh's own centre (which is its origin).
 */
@OnlyIn(Dist.CLIENT)
public class CustomNukeMissileItemRender extends BlockEntityWithoutLevelRenderer {

	/** Upstream's milliseconds per degree of the GUI spin. */
	public static final long MS_PER_DEGREE = 15L;

	public CustomNukeMissileItemRender() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		if(context == ItemDisplayContext.GUI) pose.mulPose(Axis.YN.rotationDegrees(System.currentTimeMillis() / MS_PER_DEGREE % 360));
		ObjUtil.renderModel(ObjUtil.getModel(AddonMissileItemRender.NEONC), pose,
				buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), light, overlay);
		pose.popPose();
	}
}
