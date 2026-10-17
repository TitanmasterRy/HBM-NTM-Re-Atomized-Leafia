package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaLavaParticle;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A spark of balefire lava, arcing up and trailing smoke. Spawned by balefire ash, the PWR element block and the bale corium fluid.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleBalefireLava.java}. See {@link LeafiaLavaParticle}, which holds
 * the behaviour this and its twin share; they differ in the PNG and in the puff they drop
 * (vanilla's {@code SMOKE} here, 1.12.2's {@code SMOKE_NORMAL}).
 */
@OnlyIn(Dist.CLIENT)
public class ParticleBalefireLava extends LeafiaLavaParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/leafia/particle_balelava.png");

	public ParticleBalefireLava(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z, TEXTURE, ParticleTypes.SMOKE);
	}
}
