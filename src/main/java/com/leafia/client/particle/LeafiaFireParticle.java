package com.leafia.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A still flame that jitters at birth, shrinks to half size over its life and does not move.
 *
 * <p>{@code ParticleBalefire} and {@code ParticleFireK} are the same class upstream, character for
 * character, apart from the PNG they bind: a copy-and-paste with one string changed. They share this
 * body so the numbers are written once; each keeps its own class, name and texture, because that is
 * what the rest of the addon names.
 *
 * <p>The constants are upstream's: a 0.05-block jitter on each axis ({@code rand − rand}, so
 * triangular), a draw of {@code particleScale × 0.08} for the size, and a life from
 * {@link LeafiaParticleMath#fireLifetime}. It never collides and, since upstream does not override
 * {@code onUpdate}, ages and falls on the vanilla rule — with no gravity and no motion, so it
 * simply ages.
 */
@OnlyIn(Dist.CLIENT)
public abstract class LeafiaFireParticle extends LeafiaBillboardParticle {

	/** {@code particleScale × 0.08}: the multiplier on the vanilla-sized draw. */
	public static final float SCALE = 0.08F;
	/** The jitter's reach on each axis, in blocks. */
	public static final float JITTER = 0.05F;

	private final float baseSize;
	private final ParticleRenderType renderType;

	protected LeafiaFireParticle(ClientLevel level, double x, double y, double z, ResourceLocation texture) {
		super(level, x, y, z);
		this.renderType = LeafiaParticleRenderTypes.alpha(texture);
		this.setPos(
				this.x + (double) ((this.random.nextFloat() - this.random.nextFloat()) * JITTER),
				this.y + (double) ((this.random.nextFloat() - this.random.nextFloat()) * JITTER),
				this.z + (double) ((this.random.nextFloat() - this.random.nextFloat()) * JITTER));
		this.rCol = 1.0F;
		this.gCol = 1.0F;
		this.bCol = 1.0F;
		this.baseSize = legacyScale() * SCALE;
		this.lifetime = LeafiaParticleMath.fireLifetime(Math.random());
		this.hasPhysics = false;
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
