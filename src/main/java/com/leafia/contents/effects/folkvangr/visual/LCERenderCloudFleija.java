package com.leafia.contents.effects.folkvangr.visual;

import com.hbm.entity.effect.EntityCloudFleija;
import com.hbm.render.entity.effect.RenderCloudFleija;
import com.leafia.config.LeafiaFeatures;
import com.leafia.overwrite_contents.LeafiaFleijaClouds;
import com.llib.technical.LeafiaEase;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The F.L.E.I.J.A. cloud as the addon draws it: a teal sphere of the cloud's scale with three glowing
 * shells, which turns black and shrinks to nothing once the cloud is finished.
 *
 * <p>Upstream: {@code com/leafia/contents/effects/folkvangr/visual/LCERenderCloudFleija.java}, which replaced
 * the base mod's renderer for the cloud outright. Here it is a {@link RenderCloudFleija.RenderDelegate}
 * (D-2197) that draws while {@code features.overhauls.folkvangr} is on. The passes, in upstream's order
 * and with its colours ({@code (0.20, 0.92, 0.83)} raised to various powers):
 *
 * <ol>
 * <li>The core, blended, at {@code scale + partialTick x tickrate}: colour {@code c^0.25} while growing;
 *     once finished, {@code c^(1 - 0.75 a^2) x a^2} with {@code a = (1 - t/20)^2}, on a sphere shrunk by
 *     {@code 1 - t / (10 + sqrt(max))}, {@code t} the ticks since it finished.</li>
 * <li>Three shells, additive, at {@code 1.1}, {@code 1.2} and {@code 1.3} times, alpha
 *     {@code (1 - i/3)^1.5 x a} - the third is invisible.</li>
 * <li>While {@code a} is 1, the core again, additive, at {@code c^0.5}. Upstream flips it inside out with a
 *     scale of -1 and then flips it back before drawing, so it is the same sphere.</li>
 * <li>For an antischrabidium cloud, an orange flash four times the size, drawn twice (the second flip does
 *     nothing either), fading over twenty ticks from tick 5.</li>
 * </ol>
 *
 * <p>Two upstream details kept: the "ticks since it finished" clock ({@code lastTicks}) is a field of the
 * renderer, shared by every cloud on screen; and the shrink's ease is {@link LeafiaEase#get(double, double,
 * double, boolean)}, which is linear (D-1004).
 */
@OnlyIn(Dist.CLIENT)
public final class LCERenderCloudFleija {

	private LCERenderCloudFleija() {}

	public static final float R = 0.20F, G = 0.92F, B = 0.83F;

	static final LeafiaEase shrinkEase = new LeafiaEase(LeafiaEase.Ease.EXPO, LeafiaEase.Direction.I);
	static float lastTicks = 0;

	public static final RenderCloudFleija.RenderDelegate DELEGATE = LCERenderCloudFleija::render;

	public static boolean render(Entity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
		if(!LeafiaFeatures.folkvangr || !(entity instanceof EntityCloudFleija cloud)) return false;

		float s = (float) (cloud.scale + partialTicks * LeafiaFleijaClouds.getTickrate(cloud));
		float ticks = cloud.tickCount + partialTicks;

		float alpha = 1;
		if(LeafiaFleijaClouds.isFinished(cloud)) {
			float s3 = (float) shrinkEase.get((ticks - lastTicks) / (10 + Math.pow(cloud.getMaxAge(), 0.5)), 1, 0, true);
			alpha = (float) Math.pow(Mth.clampedLerp(1, 0, (ticks - lastTicks) / 20), 2);
			float a2 = alpha * alpha;
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.BLEND), pose, s * s3,
					(float) (Math.pow(R, 1 - a2 * 0.75) * a2), (float) (Math.pow(G, 1 - a2 * 0.75) * a2), (float) (Math.pow(B, 1 - a2 * 0.75) * a2), 1);
		} else {
			lastTicks = ticks;
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.BLEND), pose, s,
					(float) Math.pow(R, 0.25), (float) Math.pow(G, 0.25), (float) Math.pow(B, 0.25), 1);
		}

		for(int i = 1; i <= 3; i++) {
			float s2 = 1 + (i * 0.1f);
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.ADDITIVE), pose, s * s2, R, G, B, (float) Math.pow(1 - i / 3f, 1.5) * alpha);
		}

		if(alpha >= 1) {
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.ADDITIVE), pose, s,
					(float) Math.pow(R, 0.5), (float) Math.pow(G, 0.5), (float) Math.pow(B, 0.5), 1);
		}

		if(LeafiaFleijaClouds.getIsAntischrab(cloud)) {
			float flash = (float) Math.pow(1 - Mth.clamp(ticks - 5, 0, 20) / 20, 2);
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.ADDITIVE), pose, s * 4, 1, 0.149f, 0, flash);
			FolkvangrSphere.draw(buffers.getBuffer(FolkvangrSphere.ADDITIVE), pose, -s * 4, 1, 0.149f, 0, flash);
		}
		return true;
	}
}
