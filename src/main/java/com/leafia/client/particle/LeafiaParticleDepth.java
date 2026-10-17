package com.leafia.client.particle;

import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.systems.RenderSystem;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Gives the depth test back after the particles.
 *
 * <p>{@link LeafiaParticleRenderTypes#alphaNoDepth} switches the depth test off in its {@code begin}
 * and a {@code ParticleRenderType} has no {@code end}: 1.12.2's {@code shouldDisableDepth()} was a
 * bracket around one particle, and the nearest 1.21.1 thing is a state change that stays until
 * something changes it back. The particle engine turns it on at the start of each of its two passes,
 * and every addon render type sets it explicitly, so the only thing left to cover is whatever the
 * level renderer draws after the particles. This runs at the end of each particle pass and does that.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT)
public final class LeafiaParticleDepth {

	private LeafiaParticleDepth() {}

	@SubscribeEvent
	public static void afterParticles(RenderLevelStageEvent event) {
		if(event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES)
			RenderSystem.enableDepthTest();
	}
}
