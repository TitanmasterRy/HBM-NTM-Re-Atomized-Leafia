package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaFireParticle;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A flame of alkali fire: the same still, shrinking flame as {@link ParticleBalefire} under a different PNG. Spawned by {@code LeafiaParticlePacket}'s alkali fire at its third period.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleFireK.java}. See {@link LeafiaFireParticle}, which holds
 * the behaviour this and its twin share; the only thing that differs between them is the PNG.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFireK extends LeafiaFireParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/leafia/particle_fire_k.png");

	public ParticleFireK(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z, TEXTURE);
	}
}
