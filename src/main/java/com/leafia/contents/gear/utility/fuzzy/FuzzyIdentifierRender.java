package com.leafia.contents.gear.utility.fuzzy;

import java.util.function.Function;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.render.util.EnumSymbol;
import com.leafia.client.LeafiaClient;
import com.leafia.dev.render.LeafiaBrush;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.HALF_A_PIXEL;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.PATCHES;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.PATCH_OFFSETS;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.PIPS;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.PIX;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.getDisplayAlpha;
import static com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay.shearedUv;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the fuzzy identifier: the item, and over it a flickering readout of the fluid it is set
 * to.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/utility/fuzzy/FuzzyIdentifierRender.java}, a
 * {@code TEISRBase}. 1.21.1's counterpart is a {@link BlockEntityWithoutLevelRenderer} hung on
 * the item through {@code IClientItemExtensions}, the second in the addon after
 * {@code SignRender.SignItemRender}.
 *
 * <h2>The sprite has holes, and the overlay fills them</h2>
 *
 * Every one of the eight rectangles drawn below lands on a <b>fully transparent</b> part of
 * {@code fuzzy_identifier.png} — four in the empty rows above the magnifying glass, four in the
 * gaps inside its handle. That is not luck: upstream draws the overlay <em>behind</em> the item
 * sprite and it is only visible through those gaps. It also means the coordinates are load
 * bearing in a way nothing else would catch, so
 * {@code LeafiaFuzzyTests.overlayRectanglesLandOnTransparentTexels} reads the PNG and asserts it
 * — mistype a 13 as a 12 and the test fails rather than the readout quietly half-vanishing.
 *
 * <h2>Standalone textures, so this is not D-1049's mistake</h2>
 *
 * Upstream pulls the fluid's sprite off the block atlas and walks it with
 * {@code getInterpolatedU/V}. This port's fluids are <b>standalone 16x16 textures</b> —
 * {@code FluidType.getTexture()} is a plain {@link ResourceLocation} that
 * {@code GUIElements.drawFluidTank} blits directly — so the UV arithmetic collapses into
 * {@code u / 16} and the texture goes straight into a {@link RenderType}. An entity render type
 * over a standalone texture is right here for exactly the reason it was wrong for the broof
 * (D-1049): these quads are not atlas-baked.
 *
 * <p>One consequence kept rather than corrected: upstream's second patch starts at
 * {@code getInterpolatedU(5 * 1.5 - 8)}, which is <b>-0.5</b> — a negative texel. On an atlas
 * that samples half a pixel into whatever sprite is stitched to the left of this one. Here it
 * samples at {@code u = -0.03125} on a standalone texture, and what that gives depends on the
 * sampler's wrap mode, which nothing in this file sets. <b>Not verified either way</b>: it is
 * half a pixel at the very left of one of four patches, and the alternative is inventing an
 * atlas to reproduce a bleed.
 *
 * <h2>Additive, and why the winding matters</h2>
 *
 * Upstream draws the fluid patches under {@code glBlendFunc(GL_SRC_ALPHA, GL_ONE)} — additive,
 * which is what makes the readout glow rather than tint. {@link #GLOW} is vanilla's
 * {@code ENTITY_TRANSLUCENT} state with {@code LIGHTNING_TRANSPARENCY} (exactly that blend func)
 * swapped in, the same substitution {@code RenderSolarBoiler.BEAM} makes in the base mod.
 *
 * <p><b>Culling is left on</b>, where vanilla's own {@code entityTranslucent} turns it off. Every
 * patch here is emitted twice, once per winding, which under {@code NO_CULL} would draw both and
 * double an additive brightness. Upstream emits both windings to be seen from both sides, not to
 * be seen twice from one — so cull on, and each side gets one.
 */
@OnlyIn(Dist.CLIENT)
public class FuzzyIdentifierRender extends BlockEntityWithoutLevelRenderer {

	/** Additive, textured, lit, cull on. See the class doc. */
	private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(texture ->
			RenderType.create("leafia_fuzzy_glow",
					DefaultVertexFormat.NEW_ENTITY,
					VertexFormat.Mode.QUADS,
					256,
					RenderType.CompositeState.builder()
							.setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
							.setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
							.setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
							.setLightmapState(RenderStateShard.LIGHTMAP)
							.setOverlayState(RenderStateShard.OVERLAY)
							.createCompositeState(false)));

	/**
	 * The hazard pips: untextured flat colour, normal alpha blending.
	 *
	 * <p>Upstream's {@code disableTexture2D()} plus a restored
	 * {@code glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)}. The state is
	 * {@code RenderType.leash()}'s — untextured {@code POSITION_COLOR_LIGHTMAP} — which is the
	 * same reuse {@code RenderPumpjack.CABLE} and {@code RenderSolarBoiler.BEAM} make.
	 */
	private static final RenderType PIP_QUADS = RenderType.create("leafia_fuzzy_pips",
			DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
			VertexFormat.Mode.QUADS,
			256,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
					.setTextureState(RenderStateShard.NO_TEXTURE)
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.createCompositeState(false));

	private final LeafiaBrush brush = new LeafiaBrush();

	public FuzzyIdentifierRender() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
				Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack matrix,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {

		if(!(stack.getItem() instanceof FuzzyIdentifierItem)) return;

		FluidType fluid = FuzzyIdentifierItem.getType(stack);

		matrix.pushPose();

		// Upstream: glTranslated(0, 0, 0.5 + HALF_A_PIXEL), with the patch vertices then sitting
		// at z = -HALF_A_PIXEL and the pips at 0 and -PIX. The whole overlay stays inside the
		// sprite's own 1/16 of depth.
		matrix.translate(0D, 0D, 0.5D + HALF_A_PIXEL);

		renderFluidPatches(fluid, matrix, buffer);
		renderHazardPips(fluid, matrix, buffer);

		matrix.popPose();

		// Upstream draws the item last, over the overlay. Here the order is only bookkeeping -
		// MultiBufferSource batches by render type - but the depth relationship is the same, and
		// the sprite's transparent gaps are what the overlay shows through either way.
		renderBaseModel(stack, matrix, buffer, packedLight, packedOverlay);
	}

	/** The four flickering rectangles of the fluid's own texture, tinted by its colour. */
	private void renderFluidPatches(FluidType fluid, PoseStack matrix, MultiBufferSource buffer) {

		int hex = fluid.getColor();
		float r = ((hex >>> 16) & 0xFF) / 255F;
		float g = ((hex >>> 8) & 0xFF) / 255F;
		float b = (hex & 0xFF) / 255F;

		// Upstream disables lighting for the whole overlay; LeafiaBrush.FULLBRIGHT is what that
		// means on a lightmapped vertex format.
		brush.begin(buffer, matrix, GLOW.apply(fluid.getTexture()), LeafiaBrush.FULLBRIGHT);
		brush.normal(0F, 0F, 1F);

		for(int i = 0; i < PATCHES.length; i++) {

			int[] p = PATCHES[i];
			float alpha = getDisplayAlpha(PATCH_OFFSETS[i]);

			// Upstream's UVs are the pixel coordinates run through `n * 1.5 - 8`, which is its
			// own fuzzing of the sprite - the patch samples a stretched, shifted part of the
			// fluid texture rather than the part it covers. Kept exactly; that shear is what
			// makes the readout look like a smear of the fluid rather than a swatch of it.
			float minU = shearedUv(p[0]);
			float minV = shearedUv(p[1]);
			float maxU = shearedUv(p[2]);
			float maxV = shearedUv(p[3]);

			double x0 = p[0] * PIX, y0 = p[1] * PIX, x1 = p[2] * PIX, y1 = p[3] * PIX;
			double z = -HALF_A_PIXEL;

			brush.addVertexColorWithUV(x0, y0, z, minU, minV, r, g, b, alpha);
			brush.addVertexColorWithUV(x1, y0, z, maxU, minV, r, g, b, alpha);
			brush.addVertexColorWithUV(x1, y1, z, maxU, maxV, r, g, b, alpha);
			brush.addVertexColorWithUV(x0, y1, z, minU, maxV, r, g, b, alpha);

			// The same rectangle wound the other way, so it is visible from behind. See the
			// class doc on why culling stays on.
			brush.addVertexColorWithUV(x1, y0, z, maxU, minV, r, g, b, alpha);
			brush.addVertexColorWithUV(x0, y0, z, minU, minV, r, g, b, alpha);
			brush.addVertexColorWithUV(x0, y1, z, minU, maxV, r, g, b, alpha);
			brush.addVertexColorWithUV(x1, y1, z, maxU, maxV, r, g, b, alpha);
		}

		brush.end();
	}

	/**
	 * Four one-pixel plates reading the fluid's hazard numbers.
	 *
	 * <p>Red is flammability, blue poison, yellow reactivity, and white means the fluid carries a
	 * hazard symbol at all. Each is its {@code /4} share of full brightness raised to the power
	 * {@code 0.65}, which is upstream's own gamma — a 1-of-4 hazard reads as a third of the
	 * colour rather than a quarter, so a low rating is still visible.
	 */
	private void renderHazardPips(FluidType fluid, PoseStack matrix, MultiBufferSource buffer) {

		brush.begin(buffer, matrix, PIP_QUADS, LeafiaBrush.FULLBRIGHT);
		brush.normal(0F, 0F, 1F);

		for(int type = 0; type < PIPS.length; type++) {

			float r = 0, g = 0, b = 0;

			switch(type) {
				case 0 -> r = fluid.flammability / 4F;
				case 1 -> b = fluid.poison / 4F;
				case 2 -> {
					r = fluid.reactivity / 4F;
					g = fluid.reactivity / 4F;
				}
				case 3 -> {
					if(fluid.symbol != EnumSymbol.NONE) {
						r = 1;
						g = 1;
						b = 1;
					}
				}
				default -> { }
			}

			r = (float) Math.pow(r, 0.65D);
			g = (float) Math.pow(g, 0.65D);
			b = (float) Math.pow(b, 0.65D);

			int x = PIPS[type][0];
			int y = PIPS[type][1];

			brush.addVertexColor(x * PIX, y * PIX, 0, r, g, b, 1);
			brush.addVertexColor((x + 1) * PIX, y * PIX, 0, r, g, b, 1);
			brush.addVertexColor((x + 1) * PIX, (y + 1) * PIX, 0, r, g, b, 1);
			brush.addVertexColor(x * PIX, (y + 1) * PIX, 0, r, g, b, 1);

			brush.addVertexColor((x + 1) * PIX, y * PIX, -PIX, r, g, b, 1);
			brush.addVertexColor(x * PIX, y * PIX, -PIX, r, g, b, 1);
			brush.addVertexColor(x * PIX, (y + 1) * PIX, -PIX, r, g, b, 1);
			brush.addVertexColor((x + 1) * PIX, (y + 1) * PIX, -PIX, r, g, b, 1);
		}

		brush.end();
	}

	/**
	 * The item sprite itself, upstream's {@code renderItem(itemStackIn, itemModel)}.
	 *
	 * <p>{@code ItemRenderer.render} cannot be called from here: it applies the display transform
	 * and its own {@code translate(-0.5, -0.5, -0.5)} <em>before</em> handing over to a custom
	 * renderer, so calling it again would apply both twice. The model's quads go straight onto a
	 * buffer instead, which is what the matrix this renderer was handed is already set up for.
	 */
	private void renderBaseModel(ItemStack stack, PoseStack matrix, MultiBufferSource buffer,
			int packedLight, int packedOverlay) {

		BakedModel model = Minecraft.getInstance().getModelManager().getModel(LeafiaClient.FUZZY_BASE);
		RenderType type = ItemBlockRenderTypes.getRenderType(stack, true);
		VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffer, type, true, stack.hasFoil());

		Minecraft.getInstance().getItemRenderer()
				.renderModelLists(model, stack, packedLight, packedOverlay, matrix, consumer);
	}
}
