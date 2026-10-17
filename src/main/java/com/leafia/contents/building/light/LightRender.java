package com.leafia.contents.building.light;

import com.hbm.blocks.BlockDummyable;
import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.LightTexture;
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
 * The fluorescent light's renderer — <b>the addon's first</b>, and the pattern the other 48
 * follow.
 *
 * <p>Upstream: {@code com/leafia/contents/building/light/LightRender.java}.
 *
 * <h2>What the translation costs</h2>
 *
 * Very little, because the base mod already solved this shape for the filing cabinet:
 *
 * <ul>
 *   <li>{@code pushMatrix}/{@code translate}/{@code rotate}/{@code popMatrix} become
 *       {@link PoseStack} calls, one for one.</li>
 *   <li>{@code bindTexture} is gone. A baked model carries its own atlas UVs, so the texture is
 *       chosen by picking a model rather than by binding one.</li>
 *   <li>{@code renderPart("Base")} becomes drawing a model baked with {@code Bulb} hidden.
 *       Same geometry, decided at bake time — see {@link LeafiaClient}.</li>
 *   <li>{@code LeafiaGls.color(0.35f, 0.35f, 0.35f)} becomes the tint arguments on
 *       {@link ObjUtil#renderModel}, which is what that overload exists for.</li>
 * </ul>
 *
 * <h2>The two numbers that are upstream's and look wrong</h2>
 *
 * The translate is {@code y + 0.99}, not the {@code y + 0.5} a centred model would use: the
 * fixture hangs from the ceiling, so its origin is at the top of the block. And the facing
 * switch handles only the four horizontal directions — a fixture whose {@code DIR} is up or
 * down falls through it and is drawn unrotated. Both are kept.
 *
 * <p>Upstream's four cases are {@code NORTH 180°, SOUTH 0°, WEST 270°, EAST 90°}, which is
 * exactly {@link ObjUtil#rotateForFacing}'s mapping — the base mod's convention and the addon's
 * agree here — and that helper answers 0° for a vertical facing, which is the same
 * fall-through. So one call replaces the switch without changing a single angle.
 *
 * <h2>The bulb is fullbright when lit</h2>
 *
 * Upstream draws it inside {@code disableLighting()} and
 * {@code setLightmapTextureCoords(lightmapTexUnit, 240, 240)}. Both halves of that are one
 * argument in 1.21.1: pass {@link LightTexture#FULL_BRIGHT} as the packed light for that draw.
 * That is what makes a lit fluorescent tube read as lit rather than as a grey tube in a bright
 * room, so it is worth getting right even though nothing here can look at it.
 *
 * <h2>What is not reproduced, and what is not verified</h2>
 *
 * Upstream disables backface culling around the {@code Base} draw. Culling is a property of the
 * chosen {@code RenderType} in 1.21.1, and this uses the block atlas's cutout sheet (D-1062) rather than inventing a no-cull type that could not be checked here.
 * If the fixture's underside turns out to be missing, that is the line to revisit; it is the
 * same class of deferral as D-708's overlay pass.
 *
 * <p>What <em>is</em> checked is that it loads and draws without throwing: `runClient` under
 * Xvfb reports no missing models and no render-thread exception (D-1019). Whether it looks right
 * still needs a human at a screen.
 */
@OnlyIn(Dist.CLIENT)
public class LightRender implements BlockEntityRenderer<LightTE> {

	/** Upstream's tint for the unlit body. The bulb draws untinted. */
	private static final float BASE_TINT = 0.35F;

	public LightRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(LightTE light, float partialTick, PoseStack matrix,
			MultiBufferSource buffers, int packedLight, int overlay) {

		BlockState state = light.getBlockState();
		if(!(state.getBlock() instanceof LightBlock)) return;

		Direction facing = state.getValue(BlockDummyable.DIR);
		boolean lit = state.getValue(LightBlock.LIT);

		var buffer = buffers.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		matrix.translate(0.5D, 0.99D, 0.5D);
		ObjUtil.rotateForFacing(matrix, facing);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.LIGHT_BASE), matrix, buffer,
				packedLight, OverlayTexture.NO_OVERLAY, BASE_TINT, BASE_TINT, BASE_TINT);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.LIGHT_BULB), matrix, buffer,
				lit ? LightTexture.FULL_BRIGHT : packedLight, OverlayTexture.NO_OVERLAY);

		matrix.popPose();
	}

	/**
	 * The fixture is three blocks wide and only its core carries a block entity, so the default
	 * one-block box would cull the whole thing the moment the core left the frustum.
	 */
	@Override
	public AABB getRenderBoundingBox(LightTE light) {
		return new AABB(light.getBlockPos()).inflate(1.0D, 0.0D, 1.0D);
	}
}
