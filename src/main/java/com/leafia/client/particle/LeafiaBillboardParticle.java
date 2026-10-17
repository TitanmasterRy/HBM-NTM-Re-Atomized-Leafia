package com.leafia.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A camera-facing quad that draws its own whole texture: the shape of every addon particle that
 * bound a PNG by hand in 1.12.2 and wrote four vertices from {@code rotationX/Z/YZ/XY/XZ}.
 *
 * <p>Those five numbers are the camera's right and up vectors, and 1.21.1's
 * {@link SingleQuadParticle} builds the same four corners from the camera's rotation, so the
 * subclass supplies only what upstream varied: the half-size ({@link #getQuadSize}), the colour and
 * alpha, and the texture window. The texture is the whole of a PNG, so the window is 0..1 here and a
 * subclass narrows it for a strip.
 *
 * <h2>Two sizes, and which is which</h2>
 *
 * 1.12.2's {@code Particle} drew a quad of half-size {@code 0.1 × particleScale}. Upstream's custom
 * renderers mostly drew {@code particleScale} itself, ten times larger, because they did not go
 * through the vanilla renderer. 1.21.1 draws {@code quadSize}, which its constructor sets to
 * {@code 0.1 × (random × 0.5 + 0.5) × 2} — that is, the vanilla draw already carries the 0.1. So a
 * subclass that wants upstream's {@code particleScale} reads {@link #legacyScale()}, which undoes it,
 * and one that wants vanilla's size just leaves {@code quadSize} alone.
 *
 * <h2>The lightmap</h2>
 *
 * The default is the world's light at the particle, as {@code getBrightnessForRender} was. A subclass
 * that upstream forced to {@code lightmap(240, 240)} overrides {@link #getLightColor}.
 */
@OnlyIn(Dist.CLIENT)
public abstract class LeafiaBillboardParticle extends SingleQuadParticle {

	protected LeafiaBillboardParticle(ClientLevel level, double x, double y, double z) {
		super(level, x, y, z);
	}

	/**
	 * The {@code particleScale} 1.12.2's {@code Particle} constructor drew, {@code (rand × 0.5 + 0.5) × 2},
	 * recovered from {@code quadSize}. {@code quadSize} was set from the same draw as one tenth of it.
	 */
	protected final float legacyScale() {
		return this.quadSize * 10.0F;
	}

	@Override
	protected float getU0() { return 0.0F; }

	@Override
	protected float getU1() { return 1.0F; }

	@Override
	protected float getV0() { return 0.0F; }

	@Override
	protected float getV1() { return 1.0F; }
}
