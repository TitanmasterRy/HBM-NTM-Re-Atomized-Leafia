package com.leafia.unsorted;

import com.leafia.client.particle.LeafiaParticleMath;
import com.leafia.client.particle.LeafiaParticleRenderTypes;
import com.leafia.client.particle.LeafiaParticleSprites;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A mote of coloured light: vanilla's redstone dust with the colour and motion chosen by the caller,
 * drawn additively and lit as if by a torch regardless of where it is.
 *
 * <p>Upstream: {@code com/leafia/unsorted/ParticleRedstoneLight.java}, which extends 1.12.2's
 * {@code ParticleRedstone}, passing it a scale and then overwriting its motion and its colour. Its
 * successor, 1.21.1's {@code DustParticleBase}, is carried here rather than inherited, since the
 * dust's constructor takes particle options and a sprite set that nothing has here: the size is
 * {@code quadSize × 0.75 × scale}, the life {@code 8 / (rand × 0.8 + 0.2)} times the scale, the
 * friction 0.96, a tenth of the random motion, and the sideways speed-up of 10% each tick it is
 * blocked vertically. The frame is {@code 7 − age × 8 / life}.
 *
 * <p>What is the addon's own, and exact: a zero red becomes one ({@code red == 0 → 1}); the colour is
 * {@code (red, green, blue)} untouched, with none of the dust's random darkening; and the lightmap
 * is a flat 255, which is block light at full and sky light at none.
 *
 * <p><b>Not independently verified:</b> 1.12.2's {@code ParticleRedstone} is not in the vendored
 * jar's reach, so the dust body is 1.21.1's, which kept vanilla's behaviour across the version.
 */
@OnlyIn(Dist.CLIENT)
public class ParticleRedstoneLight extends TextureSheetParticle {

	/** Upstream's {@code getBrightnessForRender}, {@code 255}: block light 15, sky light 0. */
	public static final int BRIGHTNESS = 255;
	public static final float FRICTION = 0.96F;

	public ParticleRedstoneLight(ClientLevel level, double x, double y, double z, float scale,
			double mx, double my, double mz, float red, float green, float blue) {
		super(level, x, y, z, 0, 0, 0);
		this.friction = FRICTION;
		this.speedUpWhenYMotionIsBlocked = true;
		this.xd *= 0.1F;
		this.yd *= 0.1F;
		this.zd *= 0.1F;
		this.quadSize = this.quadSize * 0.75F * scale;
		int i = (int) (8.0 / (this.random.nextDouble() * 0.8 + 0.2));
		this.lifetime = (int) Math.max((float) i * scale, 1.0F);
		this.setSprite(LeafiaParticleSprites.generic(7));

		this.xd = mx;
		this.yd = my;
		this.zd = mz;
		if(red == 0.0F) {
			red = 1.0F;
		}
		this.rCol = red;
		this.gCol = green;
		this.bCol = blue;
	}

	@Override
	public void tick() {
		super.tick();
		this.setSprite(LeafiaParticleSprites.generic(7 - this.age * 8 / this.lifetime));
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return this.quadSize * LeafiaParticleMath.cloudGrowth(this.age, partialTicks, this.lifetime);
	}

	/** {@code lightmap(0, 255)}: block coordinate clamps to full, sky is dark. */
	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.pack(15, 0);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return LeafiaParticleRenderTypes.ATLAS_ADDITIVE;
	}
}
