package com.leafia.contents.cannery;

import com.hbm.blocks.ModBlocks;
import com.hbm.render.util.ObjUtil;
import com.hbm.wiaj.WorldInAJar;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.machines.processing.mixingvat.MixingVatRender;
import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryRender;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlRender;
import com.llib.math.LeafiaColor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The drawing half of the machines the PWR and LFTR topics put in a jar as actors: a PWR control rod column, the older mixing vat and
 * the LFTR's arbitrary block (D-3029). The same split the base mod makes for its own topics ({@code JarActorRenders}): the scripts
 * and their actors are common code a test can build, and these are the client calls.
 *
 * <p>Upstream drew each in a {@code renderActor}: translate to the cell, bind a texture, {@code renderPart} a named part of a model.
 * Here a part is a standalone baked model (the texture is baked into it), drawn on the block atlas through the entity cutout render
 * type with culling off, lit with the jar's packed light. Upstream's jar had the fixed pipeline's default state under it, in which
 * alpha-tested cutout is what a translucent texel gets, so the vat's glass and liquid are drawn cutout too.
 */
@OnlyIn(Dist.CLIENT)
public class CanneryActorRenders {

	/** The indices of {@link com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock#PARTS}; a test pins them to the names. */
	public static final int FRAME_SIDE = 0, FRAME_END = 1, FRAME_END_TOP = 2, FRAME_END_BTM = 3, RODS_SIDE = 4, RODS_END = 5, RODS_END_TOP = 6, RODS_END_BTM = 7;

	/** Upstream's {@code BlockDummyable.offset}: the metadata of a machine's core is its rotation plus this. */
	public static final int DUMMYABLE_OFFSET = 10;

	private static VertexConsumer cutout(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
	}

	private static void part(ModelResourceLocation model, PoseStack pose, VertexConsumer buffer, int light) {
		ObjUtil.renderModel(ObjUtil.getModel(model), pose, buffer, light, OverlayTexture.NO_OVERLAY);
	}

	/**
	 * Upstream's {@code CanneryPWR.ActorControlRods.renderActor}: one segment of a control rod column at the cell, the frame and the
	 * rods inside it, of the classic mesh when the tag's {@code variant} is 1. Upstream binds the top texture for the four end parts and
	 * the side texture for the other four; the part models carry theirs.
	 */
	public static void controlRods(WorldInAJar world, CompoundTag data, PoseStack pose, MultiBufferSource buffers) {

		ModelResourceLocation[] mesh = data.getInt("variant") == 1 ? PWRControlRender.CLASSIC : PWRControlRender.FINAL;
		VertexConsumer buffer = cutout(buffers);
		int light = world.lightlevel;

		pose.pushPose();
		pose.translate(data.getDouble("x") + 0.5D, data.getDouble("y"), data.getDouble("z") + 0.5D);

		part(mesh[FRAME_END_TOP], pose, buffer, light);
		part(mesh[FRAME_END_BTM], pose, buffer, light);
		part(mesh[RODS_END_TOP], pose, buffer, light);
		part(mesh[RODS_END_BTM], pose, buffer, light);
		part(mesh[FRAME_SIDE], pose, buffer, light);
		part(mesh[FRAME_END], pose, buffer, light);
		part(mesh[RODS_SIDE], pose, buffer, light);
		part(mesh[RODS_END], pose, buffer, light);

		pose.popPose();
	}

	/**
	 * Upstream's {@code CanneryLFTR.ActorMixingVat.renderActor}: the older vat, turned by the tag's {@code meta} (a core's rotation plus
	 * {@link #DUMMYABLE_OFFSET}), moved by the model's own offset, with the blade turning at {@code prevRot -> mixerRot} and the fluid
	 * at a full tank in the colour of the salt.
	 */
	public static void mixingVat(WorldInAJar world, float interp, CompoundTag data, float prevRot, float mixerRot, PoseStack pose, MultiBufferSource buffers) {

		VertexConsumer buffer = cutout(buffers);
		int light = world.lightlevel;

		pose.pushPose();
		pose.translate(data.getDouble("x") + 0.5D, data.getDouble("y"), data.getDouble("z") + 0.5D);
		switch(data.getInt("meta") - DUMMYABLE_OFFSET) {
		case 2: pose.mulPose(Axis.YP.rotationDegrees(180F)); break;
		case 4: pose.mulPose(Axis.YP.rotationDegrees(270F)); break;
		case 3: pose.mulPose(Axis.YP.rotationDegrees(0F)); break;
		case 5: pose.mulPose(Axis.YP.rotationDegrees(90F)); break;
		}
		pose.translate(0.5D, 0D, -1D);
		part(MixingVatRender.part("Base"), pose, buffer, light);

		pose.pushPose();
		float rot = prevRot + (mixerRot - prevRot) * interp;
		pose.translate(0D, 0D, -0.5D);
		pose.mulPose(Axis.YP.rotationDegrees(-rot));
		pose.translate(0D, 0D, 0.5D);
		part(MixingVatRender.part("MixingBlade"), pose, buffer, light);
		pose.popPose();

		part(MixingVatRender.part("VatGlass"), pose, buffer, light);
		part(MixingVatRender.part("Vat"), pose, buffer, light);
		part(MixingVatRender.part("Tanks"), pose, buffer, light);

		pose.pushPose();
		float level = 1;
		LeafiaColor color = new LeafiaColor(LeafiaFluids.FLUORIDE.getColor());
		pose.translate(0D, -0.65D + 0.65D * level, 0D);
		ObjUtil.renderModel(ObjUtil.getModel(MixingVatRender.part("VatLiquid")), pose, buffer, light, OverlayTexture.NO_OVERLAY,
				color.getRed(), color.getGreen(), color.getBlue());
		pose.popPose();

		pose.popPose();
	}

	/**
	 * Upstream's {@code CanneryLFTR.ActorArbitrary.renderActor}: a square of the graphite block's texture on each face of the cell,
	 * the way the arbitrary block's own renderer draws the block it holds, lit with the jar's light.
	 */
	public static void arbitrary(WorldInAJar world, CompoundTag data, PoseStack pose, MultiBufferSource buffers) {

		BlockState display = ModBlocks.block_graphite.get().defaultBlockState();
		BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(display);
		TextureAtlasSprite sprite = MSRArbitraryRender.spriteOf(baked, display);
		if(sprite == null) return;

		pose.pushPose();
		pose.translate(data.getDouble("x"), data.getDouble("y"), data.getDouble("z"));
		MSRArbitraryRender.drawSquares(pose, cutout(buffers), sprite, facing -> world.lightlevel);
		pose.popPose();
	}
}
