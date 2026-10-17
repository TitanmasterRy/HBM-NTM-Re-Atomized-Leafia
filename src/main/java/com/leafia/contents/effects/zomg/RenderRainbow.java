package com.leafia.contents.effects.zomg;

import com.custom_hbm.render.misc.LCEBeamPronter;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType;
import com.llib.math.LeafiaColor;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws a rainbow beam as a short streak behind it, in a hue drawn afresh every frame at full saturation and half value
 * - the colour rule the AM rifle's rainbow laser uses ({@code GunInitLocal}), since NTM-CE's own renderer is not in the
 * reference (D-3031). The streak is {@link #LENGTH} ticks of its velocity long and {@link #WIDTH} wide, additive and
 * full-bright through {@link LCEBeamPronter}.
 */
@OnlyIn(Dist.CLIENT)
public class RenderRainbow extends EntityRenderer<EntityRainbow> {

	public static final double LENGTH = 3;
	public static final float WIDTH = 0.06F;

	public RenderRainbow(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(EntityRainbow beam, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		Vec3 v = beam.getDeltaMovement();
		if(v.lengthSqr() < 1.0E-8) return;
		LeafiaColor c = LeafiaColor.fromHSV(beam.level().random.nextDouble() * 360, 1, 0.5);
		int rgb = (int) (c.red * 255) << 16 | (int) (c.green * 255) << 8 | (int) (c.blue * 255);
		pose.pushPose();
		LCEBeamPronter.prontBeam(pose, buffers, v.scale(-LENGTH), EnumWaveType.STRAIGHT, EnumBeamType.SOLID, rgb, rgb, 0, 1, 0, 1, WIDTH);
		pose.popPose();
		super.render(beam, yaw, partialTick, pose, buffers, light);
	}

	@SuppressWarnings("deprecation")
	@Override
	public ResourceLocation getTextureLocation(EntityRainbow beam) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
