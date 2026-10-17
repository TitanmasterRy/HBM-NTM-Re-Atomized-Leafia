package com.leafia.contents.machines.reactors.pwr.debris;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The debris items in the hand and in a slot: the piece itself, in the textures of the block the stack names.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/debris/PWRDebrisItemRender.java}, a
 * {@code TEISRBase}. Its transform is upstream's: centred, {@code rotate(225, Y)}, {@code rotate(30)} about
 * {@code (1, 0, 1)} (normalised, as {@code glRotate} normalises), and scale 1.5 in a slot or a frame, 0.75
 * otherwise. A stack that names no block draws nothing, as upstream's.
 */
@OnlyIn(Dist.CLIENT)
public class PWRDebrisItemRender extends BlockEntityWithoutLevelRenderer {

	private static final Axis XZ = Axis.of(new Vector3f(1F, 0F, 1F).normalize());

	public PWRDebrisItemRender() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {
		if(!(stack.getItem() instanceof PWRDebrisItem item)) return;
		var level = Minecraft.getInstance().level;
		BlockState display = PWRDebrisItem.stateOf(stack, level == null ? null : level.registryAccess());
		if(display == null) return;
		matrix.pushPose();
		matrix.translate(0.5, 0.5, 0.5);
		matrix.mulPose(Axis.YP.rotationDegrees(225));
		matrix.mulPose(XZ.rotationDegrees(30));
		float scale = context == ItemDisplayContext.GUI || context == ItemDisplayContext.FIXED ? 1.5F : 0.75F;
		matrix.scale(scale, scale, scale);
		RenderPWRDebris.drawModel(item.type, display, matrix, buffers, light);
		matrix.popPose();
	}
}
