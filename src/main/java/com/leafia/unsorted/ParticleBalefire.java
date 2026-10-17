package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaFireParticle;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A flame of balefire: still and shrinking. Spawned by balefire ash and by the PWR element block.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleBalefire.java}. See {@link LeafiaFireParticle}, which holds
 * the behaviour this and its twin share; the only thing that differs between them is the PNG.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleBalefire extends LeafiaFireParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/leafia/particle_balefire.png");

	public ParticleBalefire(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z, TEXTURE);
	}
}
