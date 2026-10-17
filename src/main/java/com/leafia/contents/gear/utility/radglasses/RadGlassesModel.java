package com.leafia.contents.gear.utility.radglasses;

import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The goggles on a wearer's face (D-3026).
 *
 * <p>Upstream: {@code com/hbm/render/model/RadGlassesModel.java}, a {@code ModelArmorBase} subclass (in the base mod's package, so it
 * could reach its protected members) whose {@code head} was {@code new ModelRendererObj(ResourceManager.armor_goggles)} - the base
 * mod's {@code models/armor/goggles.obj} - and whose {@code renderArmor} bound {@code leafia:textures/armor/goggle_rad.png} and drew
 * {@code head.render(par7 * 1.001F)}: the model, at a thousandth over the scale, on the biped's head. The other six parts it builds
 * ({@code body}, arms, legs, feet from {@code armor_bj}) are never drawn, its {@code renderArmor} draws only when {@code type == 0}.
 *
 * <p>1.21.1 asks an item for an armour <em>model</em> only through a client extension that answers with a {@code HumanoidModel} drawn
 * with the layer's texture; this one needs neither (the OBJ has its own texture, stitched into the block atlas as
 * {@code leafia:block/goggle_rad}), so it is a {@link RenderLayer} on every humanoid renderer: wearers, zombies, skeletons. The head
 * part has already turned, nodded and shrunk with the head when the layer is called; the OBJ is authored in the biped's model space
 * (pixels, y down, the face at -z - its eyes are at z = -5.25), so the layer scales by a sixteenth and draws it.
 */
@OnlyIn(Dist.CLIENT)
public class RadGlassesModel<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

	/** Upstream's `par7 * 1.001F`: the pixel scale, a thousandth over, so the goggles clear the face they sit on. */
	public static final float SCALE = 0.0625F * 1.001F;

	public RadGlassesModel(RenderLayerParent<T, M> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if(!(entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof RadGlassesItem)) return;
		draw(pose, buffers, light, getParentModel().head);
	}

	/** The goggles on `head`, which has the entity's pose applied. */
	public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ModelPart head) {
		pose.pushPose();
		head.translateAndRotate(pose);
		pose.scale(SCALE, SCALE, SCALE);
		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.RAD_GLASSES), pose,
				buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
