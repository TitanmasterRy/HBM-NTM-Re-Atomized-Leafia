package com.leafia.contents.machines.reactors.apr.blocks.core;

import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.hbm.render.util.ObjUtil;
import com.hbm.render.util.SmallBlockPronter;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock.APRComponentType;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The APR core: its body, twelve funnel blades that tilt open with the control, and - until the reactor is
 * assembled - a ghost of every block the structure still needs.
 *
 * <p>Upstream: {@code .../apr/blocks/core/APRCoreRender.java} over {@code apr_core_better.obj}, whose two groups
 * are two baked models here ({@code block/apr_core_core}, {@code block/apr_core_funnel}, written by
 * a generator script). The blades: twelve, the first at 45 degrees and 30 apart, each pivoting
 * about {@code (0, -0.25, -1.44889)} by {@code 60 x ((c - 10) / 90)^1.25} degrees, {@code c} the funnel's opening
 * interpolated between ticks. The ghosts are the base mod's {@code SmallBlockPronter}, one sprite on all six faces,
 * the sprite upstream mapped for each state at texture stitch (an assembled hull is the hull's, the outlet its own).
 * The item is a composite model, the core and twelve shut blades (upstream's {@code APRCoreItemRender}).
 */
@OnlyIn(Dist.CLIENT)
public class APRCoreRender implements BlockEntityRenderer<APRCoreTE> {

	public static final ModelResourceLocation CORE = standalone("block/apr_core_core");
	public static final ModelResourceLocation FUNNEL = standalone("block/apr_core_funnel");

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
	}

	public APRCoreRender(BlockEntityRendererProvider.Context context) { }

	/** Upstream's {@code ctrl}: the blades' share of their 60 degrees. */
	public static double ctrl(double v) {
		return Math.pow((v - 10) / 90d, 1.25);
	}

	public static float funnelAngle(double lastControl, double control, float partialTicks) {
		return (float) (60 * (ctrl(lastControl) + (ctrl(control) - ctrl(lastControl)) * partialTicks));
	}

	@Override
	public void render(APRCoreTE te, float partialTicks, PoseStack matrix, MultiBufferSource buffers, int light, int overlay) {
		matrix.pushPose();
		matrix.translate(0.5, 0, 0.5);
		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
		ObjUtil.renderModel(ObjUtil.getModel(CORE), matrix, consumer, light, OverlayTexture.NO_OVERLAY);
		float funnelAngle = funnelAngle(te.lastControl, te.control, partialTicks);
		matrix.pushPose();
		matrix.mulPose(Axis.YP.rotationDegrees(15));
		for(int i = 0; i < 12; i++) {
			matrix.mulPose(Axis.YP.rotationDegrees(30));
			matrix.pushPose();
			matrix.translate(0, -0.25, -1.44889);
			matrix.mulPose(Axis.XP.rotationDegrees(-funnelAngle));
			matrix.translate(0, 0.25, 1.44889);
			ObjUtil.renderModel(ObjUtil.getModel(FUNNEL), matrix, consumer, light, OverlayTexture.NO_OVERLAY);
			matrix.popPose();
		}
		matrix.popPose();
		matrix.popPose();

		if(!te.assembled) {
			VertexConsumer ghost = buffers.getBuffer(SmallBlockPronter.ghost());
			BlockPos origin = te.getBlockPos();
			for(Entry<BlockPos, BlockState> entry : te.mbRequirement.entrySet()) {
				if(entry.getValue().isAir()) continue;
				TextureAtlasSprite sprite = previewSprite(entry.getValue());
				if(sprite == null) continue;
				BlockPos p = entry.getKey();
				SmallBlockPronter.drawSmolBlockAt(matrix, ghost, sprite, p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ(),
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			}
		}
	}

	/** Upstream's {@code sprs}: the texture each required state is previewed with, or null for one it never maps. */
	@Nullable
	public static ResourceLocation previewTexture(BlockState state) {
		if(LeafiaBlocks.apr_outlet != null && state.getBlock() == LeafiaBlocks.apr_outlet.get())
			return LeafiaRef.rl("block/apr/apr_outlet");
		if(LeafiaBlocks.apr_component == null || state.getBlock() != LeafiaBlocks.apr_component.get()) return null;
		APRComponentType type = state.getValue(APRComponentBlock.VARIANT);
		return switch(type) {
			case BLADES -> LeafiaRef.rl("block/apr/apr_blades");
			case HULL_ASSEMBLED -> LeafiaRef.rl("block/apr/apr_hull");
			case MECHANICAL -> LeafiaRef.rl("block/apr/apr_parts");
			case PLATING -> LeafiaRef.rl("block/apr/apr_plating");
			case PLATING_DARK -> LeafiaRef.rl("block/apr/apr_plating_dark");
			case SUPPORT -> LeafiaRef.rl("block/apr/apr_support");
			case COIL -> LeafiaRef.rl("block/apr/hadron_coil_gold");
			case HULL -> null;
		};
	}

	@Nullable
	private static TextureAtlasSprite previewSprite(BlockState state) {
		ResourceLocation tex = previewTexture(state);
		if(tex == null) return null;
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
	}

	@Override
	public boolean shouldRenderOffScreen(APRCoreTE te) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}

	@Override
	public AABB getRenderBoundingBox(APRCoreTE te) {
		return te.getRenderBoundingBox();
	}
}
