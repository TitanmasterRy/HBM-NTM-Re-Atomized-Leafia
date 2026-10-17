package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaBillboardParticle;
import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A drop of the blood rain: a 0.01-block-hitbox droplet that lives three to five ticks and steps
 * through a four-frame strip as it goes.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleRainBlood.java}. Its {@code onUpdate} only counts
 * the age; the motion and ground-splash code that follows it is commented out upstream and is not
 * ported, so the drop never moves — the rain renderer places a fresh one where it wants one. The
 * gravity it sets, 0.06, is read by nothing and kept as upstream has it.
 *
 * <p>{@code bloodrain.png} is 32 by 8: four 8 by 8 frames side by side, and the frame is
 * {@code age × 4 / life} in integers ({@link LeafiaParticleMath#bloodFrameU}). The size is the vanilla
 * one, {@code 0.1 × particleScale}, which is what {@code quadSize} already is.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleRainBlood extends LeafiaBillboardParticle {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/particle/leafia/bloodrain.png");

	private static final ParticleRenderType TYPE = LeafiaParticleRenderTypes.alpha(TEXTURE);

	/** Upstream's {@code setSize(0.01F, 0.01F)}. */
	public static final float HITBOX = 0.01F;
	/** Upstream's {@code particleGravity = 0.06F}, which nothing reads. */
	public static final float GRAVITY = 0.06F;
	/** The shortest life; the longest is this plus two. */
	public static final int MIN_LIFETIME = 3;

	public ParticleRainBlood(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
		this.rCol = 1.0F;
		this.gCol = 1.0F;
		this.bCol = 1.0F;
		this.setSize(HITBOX, HITBOX);
		this.gravity = GRAVITY;
		this.lifetime = MIN_LIFETIME + level.random.nextInt(3);
	}

	@Override
	public void tick() {
		if(++this.age >= this.lifetime)
			this.remove();
	}

	@Override
	protected float getU0() {
		return (float) LeafiaParticleMath.bloodFrameU(this.age, this.lifetime);
	}

	@Override
	protected float getU1() {
		return (float) (LeafiaParticleMath.bloodFrameU(this.age, this.lifetime) + 0.25);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return TYPE;
	}
}
