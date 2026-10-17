package com.leafia.contents.building.storage.broof;

import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the canopy.
 *
 * <p>Upstream: {@code .../building/storage/broof/BroofRender.java}. One OBJ, one texture, no
 * animation and no state — the simplest renderer in the addon.
 *
 * <h2>The blend state is the whole of what upstream does by hand</h2>
 *
 * Upstream enables blending, sets {@code SRC_ALPHA}/{@code ONE_MINUS_SRC_ALPHA}, and — this is
 * the part that matters — brackets the draw with
 * {@code glAlphaFunc(GL_ALWAYS, 0)} … {@code glAlphaFunc(GL_GREATER, 0)}, which disables alpha
 * <em>testing</em> so that partly transparent texels blend instead of being discarded. A rug
 * with soft edges needs that; the cutout pass every other model in this addon uses would give
 * it hard ones.
 *
 * <p>{@link Sheets#translucentCullBlockSheet} is that state on the block atlas (and a fixed
 * buffer, D-1062): blended, depth-tested,
 * no alpha cutoff. Named rather than assumed — a rug drawn on {@code cutout()} looks nearly
 * right and loses its edge feathering, which is the kind of difference nobody notices until
 * they compare. It is the atlas pass rather than {@code entityTranslucentCull(texture)},
 * because {@code ObjUtil} draws a <em>baked</em> model whose quads already carry atlas UVs;
 * handing those to a render type bound to a standalone texture samples the wrong pixels.
 *
 * <h2>Its item form does not port</h2>
 *
 * Upstream nests a {@code BroofItemRender} that draws the same OBJ in the hand at
 * {@code sizeReference 5.9} and {@code yOffset 0.03}. In this port that is a generated item
 * model over the same OBJ with a {@code display} block — the rule D-1040 established and
 * D-1043 applied to eight items at once. See a generator script.
 */
@OnlyIn(Dist.CLIENT)
public class BroofRender implements BlockEntityRenderer<BroofTE> {

	public BroofRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(BroofTE broof, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = broof.getBlockState();
		if(!(state.getBlock() instanceof BroofBlock)) return;

		VertexConsumer consumer = buffers.getBuffer(Sheets.translucentCullBlockSheet());

		matrix.pushPose();
		// Upstream's translate(x + 0.5, y, z + 0.5), and nothing else: BroofRender does not
		// rotate. A broof faces the way its OBJ was modelled however it was placed, which is
		// upstream's behaviour and hard to see on a dome that is square in plan.
		matrix.translate(0.5D, 0D, 0.5D);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.BROOF), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);

		matrix.popPose();
	}

	/**
	 * The 3x3 footprint, so the rug is not culled when the core leaves the frustum.
	 *
	 * <p>Upstream puts this on the block entity as {@code getRenderBoundingBox}; NeoForge 21.1
	 * asks the renderer (D-1039), and {@link BroofTE#renderBounds()} keeps the geometry.
	 */
	@Override
	public AABB getRenderBoundingBox(BroofTE broof) {
		return broof.renderBounds();
	}
}
