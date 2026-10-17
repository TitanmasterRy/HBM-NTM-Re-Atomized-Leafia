package com.leafia.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A spark of molten fire that arcs up, falls under gravity and leaves a puff behind it.
 *
 * <p>{@code ParticleBalefireLava} and {@code ParticleFireLavaK} are one class upstream with two
 * differences, the PNG and the trail: the balefire one drops vanilla smoke
 * ({@code SMOKE_NORMAL}) and the other drops a cloud puff ({@code CLOUD}). Both are a constructor
 * argument here.
 *
 * <p>Motion is drawn once, {@code (rand × 0.4 − 0.2) / 2} sideways and {@code (rand × 0.4 + 0.05) / 2}
 * up, then each tick it falls by 0.03, moves, loses 0.1% of its speed and, on the ground, 30% of its
 * sideways speed. Each tick it spawns a trail particle with probability {@code 1 − age / life}, so
 * the trail thins as the spark dies. These collide, unlike the still flame, and the size draw
 * carries a second random factor, {@code (particleScale × rand × 2 + 0.2) × 0.08}.
 */
@OnlyIn(Dist.CLIENT)
public abstract class LeafiaLavaParticle extends LeafiaBillboardParticle {

	public static final float SCALE = 0.08F;
	public static final float JITTER = 0.05F;
	/** Downward acceleration per tick. */
	public static final double GRAVITY = 0.03D;
	/** Upstream's {@code 0.9990000128746033D}, which is the float {@code 0.999F} widened. */
	public static final double DRAG = 0.9990000128746033D;
	/** Upstream's {@code 0.699999988079071D}, the float {@code 0.7F} widened. */
	public static final double GROUND_DRAG = 0.699999988079071D;

	private final float baseSize;
	private final ParticleRenderType renderType;
	private final ParticleOptions trail;

	protected LeafiaLavaParticle(ClientLevel level, double x, double y, double z, ResourceLocation texture, ParticleOptions trail) {
		super(level, x, y, z);
		this.renderType = LeafiaParticleRenderTypes.alpha(texture);
		this.trail = trail;
		this.setPos(
				this.x + (double) ((this.random.nextFloat() - this.random.nextFloat()) * JITTER),
				this.y + (double) ((this.random.nextFloat() - this.random.nextFloat()) * JITTER),
				this.z + (double) ((this.random.nextFloat() - this.random.nextFloat()) * JITTER));
		this.xd = (double) (this.random.nextFloat() * 0.4F - 0.2F) / 2D;
		this.yd = (double) (this.random.nextFloat() * 0.4F + 0.05F) / 2D;
		this.zd = (double) (this.random.nextFloat() * 0.4F - 0.2F) / 2D;
		this.rCol = 1.0F;
		this.gCol = 1.0F;
		this.bCol = 1.0F;
		this.baseSize = (legacyScale() * this.random.nextFloat() * 2.0F + 0.2F) * SCALE;
		this.lifetime = LeafiaParticleMath.lavaLifetime(Math.random());
		this.hasPhysics = true;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;

		if(this.age++ >= this.lifetime) {
			this.remove();
		}

		float f = (float) this.age / (float) this.lifetime;

		if(this.random.nextFloat() > f) {
			this.level.addParticle(this.trail, this.x, this.y, this.z, this.xd, this.yd, this.zd);
		}

		this.yd -= GRAVITY;
		this.move(this.xd, this.yd, this.zd);
		this.xd *= DRAG;
		this.yd *= DRAG;
		this.zd *= DRAG;

		if(this.onGround) {
			this.xd *= GROUND_DRAG;
			this.zd *= GROUND_DRAG;
		}
	}

	@Override
	public float getQuadSize(float partialTicks) {
		float f = ((float) this.age + partialTicks) / (float) this.lifetime;
		return this.baseSize * LeafiaParticleMath.fireShrink(f);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return this.renderType;
	}
}
