package com.leafia.contents.effects.folkvangr.visual;

import com.hbm.entity.effect.EntityCloudFleijaRainbow;
import com.hbm.render.entity.effect.RenderCloudFleija;
import com.leafia.config.LeafiaFeatures;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The rainbow cloud (the custom nuke's and the DFC's) as the addon draws it: a core of half the cloud's
 * scale in a random colour every frame, under four additive shells, each its own random colour.
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/visual/LCERenderCloudRainbow.java}, a
 * {@link RenderCloudFleija.RenderDelegate} on {@code RenderCloudRainbow} here (D-2197), drawing while
 * {@code features.overhauls.folkvangr} is on. The scale is {@code scale + partialTick x tickrate}; the core is
 * opaque at half of it; the shells are at {@code 0.6, 0.7, 0.8} and {@code 0.9} of it - upstream's loop runs
 * {@code i += 0.1F} from 0.6 while {@code i <= 1F}, and four float additions make {@code 1.0000001}, so the
 * fifth shell is never drawn. Every colour is three random bytes ({@code glColor3ub}), so the cloud strobes.
 * Cull is on for every pass.
 */
@OnlyIn(Dist.CLIENT)
public final class LCERenderCloudRainbow {

	private LCERenderCloudRainbow() {}

	public static final RenderCloudFleija.RenderDelegate DELEGATE = LCERenderCloudRainbow::render;

	public static boolean render(Entity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
		if(!LeafiaFeatures.folkvangr || !(entity instanceof EntityCloudFleijaRainbow cloud)) return false;

		RandomSource rand = cloud.level().random;
		float s = (float) (cloud.scale + partialTicks * LeafiaFleijaClouds.getTickrate(cloud));

		FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.OPAQUE_CULLED), pose, s * 0.5F,
				rand.nextInt(0x100) / 255F, rand.nextInt(0x100) / 255F, rand.nextInt(0x100) / 255F, 1);

		for(float i = 0.6F; i <= 1F; i += 0.1F) {
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.ADDITIVE_CULLED), pose, s * i,
					rand.nextInt(0x100) / 255F, rand.nextInt(0x100) / 255F, rand.nextInt(0x100) / 255F, 1);
		}
		return true;
	}

	/** The shells upstream's float loop draws: 0.6 to 0.9, four of them. */
	public static int shellCount() {
		int n = 0;
		for(float i = 0.6F; i <= 1F; i += 0.1F) n++;
		return n;
	}
}
