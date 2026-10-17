package com.leafia.contents.machines.misc.wind_turbines.medium;

import com.hbm.render.util.ObjUtil;
import com.leafia.client.LeafiaClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

/**
 * Draws the medium wind turbine: a mast that stands still and a rotor that turns.
 *
 * <p>Upstream: {@code .../wind_turbines/medium/WindTurbineMediumRender.java}, and it is the third
 * addon renderer on the pattern D-1020 established with the fluorescent light — upstream's
 * {@code vao.renderPart("Base")} / {@code renderPart("Blades")} become two models baked from one
 * OBJ with the other's group hidden, and the rotation between the two calls happens between the
 * two {@code renderModel}s here.
 *
 * <h2>What this proves about eighty other renderers</h2>
 *
 * The dependency checker lists {@code LeafiaGls}, {@code ResourceInit} and
 * {@code LeafiaItemRenderer} as blockers on essentially every addon renderer, which reads as the
 * addon's rendering being gated on a framework nobody has written. <b>It is not.</b>
 * {@code LeafiaGls} is an immediate-mode GL wrapper whose calls are {@link PoseStack} operations;
 * {@code ResourceInit.getVAO} is {@code ObjUtil.getModel} plus a registration in
 * {@link LeafiaClient}; {@code LeafiaItemRenderer} is an item model JSON. None of the three needs
 * porting and all three will keep appearing as blockers until the tool is told. See D-1028.
 *
 * <h2>One deviation, and it is upstream's own bug</h2>
 *
 * Upstream binds two textures in a row — {@code AddonBase.solid} and then the turbine's — so the
 * first bind is immediately overwritten and does nothing. There is no successor to either call
 * (a baked model carries its own atlas UVs), so both simply vanish rather than one of them being
 * dropped on purpose.
 */
public class WindTurbineMediumRender implements BlockEntityRenderer<WindTurbineMediumTE> {

	public WindTurbineMediumRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(WindTurbineMediumTE turbine, float partialTick, PoseStack matrix,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {

		var consumer = buffer.getBuffer(Sheets.cutoutBlockSheet());

		matrix.pushPose();
		matrix.translate(0.5D, 0D, 0.5D);

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.TURBINE_MEDIUM_BASE), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);

		/*
		 * Upstream rotates about (0, -1, 0): a negative Y axis, so the rotor turns clockwise seen
		 * from above. Axis.YN is that same axis, and getting it the right way round matters —
		 * D-1021 is the entry about a helper that looked right and drew every valve backwards.
		 */
		matrix.mulPose(Axis.YN.rotationDegrees(bladeAngle(turbine, partialTick)));

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaClient.TURBINE_MEDIUM_BLADES), matrix, consumer,
				packedLight, OverlayTexture.NO_OVERLAY);

		matrix.popPose();
	}

	/** Upstream's interpolation, split out so a test can read the number without a screen. */
	public static float bladeAngle(WindTurbineMediumTE turbine, float partialTick) {
		return turbine.bladeAnglePrev + (turbine.bladeAngle - turbine.bladeAnglePrev) * partialTick;
	}

	/**
	 * The rotor reaches well outside its own block, so the box has to say so or the turbine pops
	 * out of view when the mast's block leaves the frustum. This defers to the block entity's own
	 * box, which is upstream's — typo included, see {@link WindTurbineMediumTE#getRenderBoundingBox}.
	 */
	@Override
	public AABB getRenderBoundingBox(WindTurbineMediumTE turbine) {
		return turbine.getRenderBoundingBox();
	}

	/** Half a kilometre, matching what the block entity syncs to. */
	@Override
	public int getViewDistance() {
		return 512;
	}

	@Override
	public boolean shouldRenderOffScreen(WindTurbineMediumTE turbine) {
		return true;
	}
}
