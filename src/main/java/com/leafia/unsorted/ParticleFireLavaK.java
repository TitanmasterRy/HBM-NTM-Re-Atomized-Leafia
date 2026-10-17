package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaLavaParticle;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A spark of alkali lava, arcing up and trailing a cloud puff in place of smoke. Spawned by {@code LeafiaParticlePacket}'s alkali fire.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleFireLavaK.java}. See {@link LeafiaLavaParticle}, which holds
 * the behaviour this and its twin share; they differ in the PNG and in the puff they drop
 * ({@code CLOUD} in both versions).
 */
@OnlyIn(Dist.CLIENT)
public class ParticleFireLavaK extends LeafiaLavaParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/leafia/particle_lava_k.png");

	public ParticleFireLavaK(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z, TEXTURE, ParticleTypes.CLOUD);
	}
}
